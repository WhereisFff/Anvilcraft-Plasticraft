package dev.anvilcraft.plasticraft.entity.physics;

import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.dubhe.anvilcraft.block.entity.ActivatorSlidingRailBlockEntity;
import dev.dubhe.anvilcraft.block.sliding.ActivatorSlidingRailBlock;
import dev.dubhe.anvilcraft.block.sliding.BaseSlidingRailBlock;
import dev.dubhe.anvilcraft.block.sliding.DetectorSlidingRailBlock;
import dev.dubhe.anvilcraft.block.sliding.PoweredSlidingRailBlock;
import dev.dubhe.anvilcraft.init.block.ModBlockEntities;
import dev.dubhe.anvilcraft.init.block.ModBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

/** 让持久化树脂制品以单方块载荷的语义经过 AnvilCraft 滑轨。 */
public final class PlasticSlidingRailPhysics {
    private static final double RAIL_CONTACT_TOLERANCE = 0.125D;
    private static final double POWERED_SPEED = 0.35D;
    private static final double POWERED_CENTER_RETENTION = 0.5D;
    private static final double POWERED_CENTER_PULL = 0.45D / 0.98D;
    private static final double BRAKE_RETENTION = 0.8D;
    private static final double BRAKE_CENTER_PULL = 0.15D / 0.98D;

    private PlasticSlidingRailPhysics() {
    }

    /** 每个实体各自保存激活滑轨的短暂充能阶段，避免同一条轨道反复触发。 */
    public static final class State {
        @Nullable
        private BlockPos chargingRail;
        @Nullable
        private Direction chargingDirection;
        @Nullable
        private BlockPos passedActivatorRail;
        private boolean poweredDriven;

        public boolean tick(AbstractPlasticEntity entity) {
            this.poweredDriven = false;
            RailContact contact = findRailOrLaunchContact(entity);
            if (this.passedActivatorRail != null
                && (contact == null || !this.passedActivatorRail.equals(contact.pos()))) {
                this.passedActivatorRail = null;
            }

            if (this.chargingRail != null) {
                if (contact != null
                    && this.chargingRail.equals(contact.pos())
                    && contact.state().getBlock() instanceof ActivatorSlidingRailBlock) {
                    this.tickActivatorCharge(entity, contact);
                    return true;
                }
                this.clearCharge();
            }
            if (contact == null) return false;

            BlockState state = contact.state();
            if (state.getBlock() instanceof PoweredSlidingRailBlock) {
                if (state.getValue(PoweredSlidingRailBlock.POWERED)) {
                    this.poweredDriven = true;
                    accelerateAndCenter(entity, contact.pos(), state.getValue(PoweredSlidingRailBlock.FACING));
                } else {
                    brakeAndCenter(entity, contact.pos());
                }
                return true;
            }
            if (state.getBlock() instanceof DetectorSlidingRailBlock detector) {
                detector.onItemEntitySlidingAbove(entity.level(), contact.pos(), state);
                return true;
            }
            if (state.getBlock() instanceof ActivatorSlidingRailBlock activator) {
                Direction direction = horizontalDirection(entity.getDeltaMovement(), state.getValue(ActivatorSlidingRailBlock.FACING));
                if (state.getValue(ActivatorSlidingRailBlock.FACING) != direction) {
                    state = state.setValue(ActivatorSlidingRailBlock.FACING, direction);
                    entity.level().setBlockAndUpdate(contact.pos(), state);
                    contact = new RailContact(contact.pos(), state);
                }
                if (state.getValue(ActivatorSlidingRailBlock.POWERED)
                    && !contact.pos().equals(this.passedActivatorRail)) {
                    this.beginActivatorCharge(entity, contact, activator, direction);
                }
            }
            return true;
        }

        public boolean isPoweredDriven() {
            return this.poweredDriven;
        }

        public boolean isOnSlidingRail(AbstractPlasticEntity entity) {
            return findRailOrLaunchContact(entity) != null;
        }

        public boolean reapplyPoweredDrive(AbstractPlasticEntity entity) {
            RailContact contact = findRailOrLaunchContact(entity);
            if (contact == null
                || !(contact.state().getBlock() instanceof PoweredSlidingRailBlock)
                || !contact.state().getValue(PoweredSlidingRailBlock.POWERED)) {
                return false;
            }
            accelerateAndCenter(
                entity,
                contact.pos(),
                contact.state().getValue(PoweredSlidingRailBlock.FACING)
            );
            return true;
        }

        public void save(CompoundTag ownerTag) {
            if (this.chargingRail == null && this.passedActivatorRail == null) return;
            CompoundTag tag = new CompoundTag();
            if (this.chargingRail != null && this.chargingDirection != null) {
                tag.putLong("ChargingRail", this.chargingRail.asLong());
                tag.putString("ChargingDirection", this.chargingDirection.getName());
            }
            if (this.passedActivatorRail != null) {
                tag.putLong("PassedActivatorRail", this.passedActivatorRail.asLong());
            }
            ownerTag.put("SlidingRailState", tag);
        }

        public void load(CompoundTag ownerTag) {
            this.clearCharge();
            this.passedActivatorRail = null;
            if (!ownerTag.contains("SlidingRailState", Tag.TAG_COMPOUND)) return;
            CompoundTag tag = ownerTag.getCompound("SlidingRailState");
            Direction direction = Direction.byName(tag.getString("ChargingDirection"));
            if (direction != null
                && direction.getAxis().isHorizontal()
                && tag.contains("ChargingRail", Tag.TAG_ANY_NUMERIC)) {
                this.chargingRail = BlockPos.of(tag.getLong("ChargingRail"));
                this.chargingDirection = direction;
            }
            if (tag.contains("PassedActivatorRail", Tag.TAG_ANY_NUMERIC)) {
                this.passedActivatorRail = BlockPos.of(tag.getLong("PassedActivatorRail"));
            }
        }

        private void beginActivatorCharge(
            AbstractPlasticEntity entity,
            RailContact contact,
            ActivatorSlidingRailBlock activator,
            Direction direction
        ) {
            this.chargingRail = contact.pos().immutable();
            this.chargingDirection = direction;
            Level level = entity.level();
            level.getBlockEntity(contact.pos(), ModBlockEntities.ACTIVATOR_SLIDING_RAIL.get())
                .ifPresent(ActivatorSlidingRailBlockEntity::startPulse);
            if (!level.getBlockTicks().hasScheduledTick(contact.pos(), activator)) {
                level.scheduleTick(contact.pos(), activator, 3);
            }
            holdAtCenter(entity, contact.pos());
        }

        private void tickActivatorCharge(AbstractPlasticEntity entity, RailContact contact) {
            TriState phase = entity.level()
                .getBlockEntity(contact.pos(), ModBlockEntities.ACTIVATOR_SLIDING_RAIL.get())
                .map(ActivatorSlidingRailBlockEntity::getShouldPower)
                .orElse(TriState.DEFAULT);
            if (phase != TriState.DEFAULT) {
                holdAtCenter(entity, contact.pos());
                return;
            }

            Direction direction = this.chargingDirection;
            this.passedActivatorRail = contact.pos().immutable();
            this.clearCharge();
            if (direction != null) {
                moveHorizontally(entity, Vec3.ZERO.relative(direction, POWERED_SPEED));
            }
        }

        private void clearCharge() {
            this.chargingRail = null;
            this.chargingDirection = null;
        }
    }

    @Nullable
    private static RailContact findRailOrLaunchContact(AbstractPlasticEntity entity) {
        RailContact contact = findRailContact(entity);
        return contact == null ? findPoweredLaunchContact(entity) : contact;
    }

    @Nullable
    private static RailContact findRailContact(AbstractPlasticEntity entity) {
        AABB box = entity.getBoundingBox();
        BlockPos pos = BlockPos.containing(entity.getX(), box.minY - 0.02D, entity.getZ());
        BlockState state = entity.level().getBlockState(pos);
        if (!(state.getBlock() instanceof BaseSlidingRailBlock)
            || state.is(ModBlockTags.SLIDING_RAIL_STOP_LIKE)) {
            return null;
        }
        if (Math.abs(box.minY - (pos.getY() + 1.0D)) > RAIL_CONTACT_TOLERANCE) return null;
        return new RailContact(pos, state);
    }

    @Nullable
    private static RailContact findPoweredLaunchContact(AbstractPlasticEntity entity) {
        AABB box = entity.getBoundingBox();
        BlockPos supportPos = BlockPos.containing(entity.getX(), box.minY - 0.02D, entity.getZ());
        if (Math.abs(box.minY - (supportPos.getY() + 1.0D)) > RAIL_CONTACT_TOLERANCE
            || !entity.level().getBlockState(supportPos).is(ModBlockTags.SLIDING_RAIL_STOP_LIKE)) {
            return null;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos railPos = supportPos.relative(direction);
            BlockState railState = entity.level().getBlockState(railPos);
            if (railState.getBlock() instanceof PoweredSlidingRailBlock
                && railState.getValue(PoweredSlidingRailBlock.POWERED)
                && railState.getValue(PoweredSlidingRailBlock.FACING) == direction) {
                return new RailContact(railPos, railState);
            }
        }
        return null;
    }

    private static void accelerateAndCenter(AbstractPlasticEntity entity, BlockPos railPos, Direction facing) {
        Direction.Axis centerAxis = facing.getClockWise().getAxis();
        double center = coordinate(railPos.getCenter(), centerAxis);
        double offset = center - coordinate(entity.position(), centerAxis);
        double centeredVelocity = coordinate(entity.getDeltaMovement(), centerAxis) * POWERED_CENTER_RETENTION
            + offset * POWERED_CENTER_PULL;
        Vec3 horizontal = centerAxis == Direction.Axis.X
            ? new Vec3(centeredVelocity, 0.0D, facing.getStepZ() * POWERED_SPEED)
            : new Vec3(facing.getStepX() * POWERED_SPEED, 0.0D, centeredVelocity);
        moveHorizontally(entity, horizontal);
    }

    private static void brakeAndCenter(AbstractPlasticEntity entity, BlockPos railPos) {
        Vec3 center = railPos.getCenter();
        Vec3 position = entity.position();
        Vec3 velocity = entity.getDeltaMovement();
        moveHorizontally(entity, new Vec3(
            velocity.x * BRAKE_RETENTION + (center.x - position.x) * BRAKE_CENTER_PULL,
            0.0D,
            velocity.z * BRAKE_RETENTION + (center.z - position.z) * BRAKE_CENTER_PULL
        ));
    }

    private static void holdAtCenter(AbstractPlasticEntity entity, BlockPos railPos) {
        Vec3 center = railPos.getCenter();
        entity.setPos(center.x, entity.getY(), center.z);
        moveHorizontally(entity, Vec3.ZERO);
    }

    private static void moveHorizontally(AbstractPlasticEntity entity, Vec3 horizontal) {
        Vec3 current = entity.getDeltaMovement();
        Vec3 next = new Vec3(horizontal.x, current.y, horizontal.z);
        if (next.equals(current)) return;
        entity.setDeltaMovement(next);
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }

    private static Direction horizontalDirection(Vec3 movement, Direction fallback) {
        if (Math.abs(movement.x) >= Math.abs(movement.z)
            && Math.abs(movement.x) > PlasticEntityPhysics.FACE_EPSILON) {
            return movement.x > 0.0D ? Direction.EAST : Direction.WEST;
        }
        if (Math.abs(movement.z) > PlasticEntityPhysics.FACE_EPSILON) {
            return movement.z > 0.0D ? Direction.SOUTH : Direction.NORTH;
        }
        return fallback;
    }

    private static double coordinate(Vec3 vector, Direction.Axis axis) {
        return vector.get(axis);
    }

    private record RailContact(BlockPos pos, BlockState state) {
    }
}
