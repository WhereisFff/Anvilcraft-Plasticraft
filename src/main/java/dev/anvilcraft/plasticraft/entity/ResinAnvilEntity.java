package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.api.entity.ElasticCollisionEntity;
import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityPhysics;
import dev.anvilcraft.plasticraft.entity.physics.ResinShockDropBehavior;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.dubhe.anvilcraft.api.giantanvil.IShockEntity;
import dev.dubhe.anvilcraft.api.giantanvil.ShockAnvilBehavior;
import dev.dubhe.anvilcraft.block.item.HasMobBlockItem;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.util.BlockMiningEffect;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 初期弹性树脂砧。它有意不提供菜单：空手使用会释放可选的已保存生物组件，
 * 常规放置和贴实体面放置仍由共用基类负责。
 */
public class ResinAnvilEntity extends AbstractPlasticEntity implements ElasticCollisionEntity, IShockEntity {
    private static final double BLOCK_RESTITUTION = 0.80D;
    private static final double ENTITY_RESTITUTION = 0.72D;
    private static final double TANGENTIAL_RETENTION = 0.68D;
    private static final double MIN_BOUNCE_SPEED = 0.06D;
    private static final double MAX_ENTITY_IMPULSE = 0.45D;
    private static final int CONTACT_COOLDOWN_TICKS = 3;
    private static final ShockAnvilBehavior SHOCK_ANVIL_BEHAVIOR = new ShockAnvilBehavior(
        BlockMiningEffect.NORMAL,
        ResinShockDropBehavior.INSTANCE
    );

    private static Supplier<ItemStack> defaultDropSupplier = () -> ItemStack.EMPTY;
    private final int[] contactCooldownUntil = new int[Direction.values().length];

    public static void configureDefaultDrop(Supplier<ItemStack> supplier) {
        defaultDropSupplier = Objects.requireNonNull(supplier, "supplier");
    }

    public ResinAnvilEntity(EntityType<? extends ResinAnvilEntity> entityType, Level level) {
        super(entityType, level);
        this.setDisplayState(ModBlocks.RESIN_ANVIL.get().defaultBlockState());
    }

    public ResinAnvilEntity(
        EntityType<? extends ResinAnvilEntity> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        super(entityType, level, position, displayState, dropStack, orientation);
    }

    @Override
    protected String materialKey() {
        return "resin";
    }

    @Override
    protected ItemStack createDefaultDropStack() {
        ItemStack stack = defaultDropSupplier.get();
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        stack = stack.copyWithCount(1);
        PlasticItemData.setMaterial(stack, "resin");
        return stack;
    }

    @Override
    protected boolean supportsHammerRotation() {
        return true;
    }

    @Override
    protected void openAnvilMenu(ServerPlayer player) {
        // 树脂是硬化前的形态，永远不会打开铁砧界面。
    }

    @Override
    public double anvilcraft$getShockBounceHeightMultiplier() {
        return 2.0D;
    }

    @Override
    public Optional<ShockAnvilBehavior> anvilcraft$getShockAnvilBehavior() {
        return Optional.of(SHOCK_ANVIL_BEHAVIOR);
    }

    @Override
    public Optional<BlockState> anvilcraft$getShockBaseState() {
        return Optional.of(dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK.getDefaultState());
    }

    public boolean hasCapturedMob() {
        return this.getDropStack().has(ModComponents.SAVED_ENTITY);
    }

    @Override
    protected Vec3 resolveCollisionVelocity(
        Vec3 requestedMovement,
        Vec3 actualMovement,
        Vec3 velocityAfterMove,
        Direction gravityDirection
    ) {
        int blockOrEntityContacts = PlasticEntityPhysics.clippedDirectionMask(
            requestedMovement,
            actualMovement,
            PlasticEntityPhysics.FACE_EPSILON
        );
        AABB currentBox = this.getBoundingBox();
        AABB startBox = currentBox.move(actualMovement.scale(-1.0D));
        AABB sweptBox = startBox.expandTowards(requestedMovement).inflate(0.08D);
        List<Entity> collisionEntities = this.level().getEntities(
            this,
            sweptBox,
            other -> other.isAlive()
                && !other.isSpectator()
                // 塑料砧会形成相互推动的链，但树脂釜同样是有效的弹性冲击面和配方目标。
                && (!(other instanceof AbstractPlasticEntity)
                    || other instanceof HardenedResinCauldronEntity)
                && !this.isPassengerOfSameVehicle(other)
                // 此处有意不复用船的碰撞检查：树脂会从可推动的生物实体，
                // 以及明确提供碰撞箱的实体上反弹。
                && (other.canBeCollidedWith() || other.isPushable())
        );

        int entityContacts = 0;
        for (Direction direction : Direction.values()) {
            for (Entity other : collisionEntities) {
                if (contactsFace(startBox, other.getBoundingBox(), requestedMovement, direction)) {
                    entityContacts |= PlasticEntityPhysics.directionMask(direction);
                    break;
                }
            }
        }

        int contacts = blockOrEntityContacts | entityContacts;
        if (contacts == 0) return velocityAfterMove;

        Vec3 result = velocityAfterMove;
        for (Direction direction : Direction.values()) {
            int directionMask = PlasticEntityPhysics.directionMask(direction);
            if ((contacts & directionMask) == 0) continue;
            Vec3 normal = Vec3.atLowerCornerOf(direction.getNormal());
            double incidentSpeed = requestedMovement.dot(normal);
            if (incidentSpeed <= PlasticEntityPhysics.FACE_EPSILON) continue;

            boolean hasEntityContact = (entityContacts & directionMask) != 0;
            boolean coolingDown = this.tickCount < this.contactCooldownUntil[direction.get3DDataValue()];
            double bounceSpeed = incidentSpeed < MIN_BOUNCE_SPEED || coolingDown
                ? 0.0D
                : incidentSpeed * (hasEntityContact ? ENTITY_RESTITUTION : BLOCK_RESTITUTION);

            double currentNormal = result.dot(normal);
            Vec3 tangential = result.subtract(normal.scale(currentNormal)).scale(TANGENTIAL_RETENTION);
            result = tangential.add(normal.scale(-bounceSpeed));
            if (bounceSpeed > 0.0D) {
                this.contactCooldownUntil[direction.get3DDataValue()] = this.tickCount + CONTACT_COOLDOWN_TICKS;
                if (hasEntityContact) {
                    this.applyBoundedEntityImpulse(collisionEntities, startBox, requestedMovement, direction, incidentSpeed);
                }
            }
        }

        if (!result.equals(velocityAfterMove)) {
            this.hasImpulse = true;
            this.hurtMarked = true;
        }
        return result;
    }

    @Override
    public void plasticraft$onEntityCollision(
        Entity collider,
        AABB colliderStartBox,
        Vec3 requestedMovement,
        Vec3 actualMovement
    ) {
        if (collider instanceof AbstractPlasticEntity
            || collider.isSuppressingBounce()
            || requestedMovement.lengthSqr()
                <= PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
            return;
        }

        int clippedContacts = PlasticEntityPhysics.clippedDirectionMask(
            requestedMovement,
            actualMovement,
            PlasticEntityPhysics.FACE_EPSILON
        );
        if (clippedContacts == 0) return;

        Vec3 colliderVelocity = collider.getDeltaMovement();
        boolean bouncedCollider = false;
        Direction gravityDirection = this.plasticraft$currentPushGravityDirection();
        double restitution = collider instanceof LivingEntity ? 1.0D : BLOCK_RESTITUTION;
        for (Direction direction : Direction.values()) {
            if ((clippedContacts & PlasticEntityPhysics.directionMask(direction)) == 0
                || !contactsFace(colliderStartBox, this.getBoundingBox(), requestedMovement, direction)) {
                continue;
            }
            Vec3 normal = Vec3.atLowerCornerOf(direction.getNormal());
            double incidentSpeed = requestedMovement.dot(normal);
            if (incidentSpeed <= PlasticEntityPhysics.FACE_EPSILON) continue;
            if (direction.getAxis() != gravityDirection.getAxis()) continue;
            double currentNormal = colliderVelocity.dot(normal);
            colliderVelocity = colliderVelocity.subtract(normal.scale(currentNormal))
                .add(normal.scale(-incidentSpeed * restitution));
            bouncedCollider = true;
        }
        if (bouncedCollider) {
            collider.setDeltaMovement(colliderVelocity);
            collider.resetFallDistance();
            collider.hasImpulse = true;
            collider.hurtMarked = true;
        }
    }

    @Override
    protected SoundEvent impactSound() {
        return dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK.getDefaultState().getSoundType().getHitSound();
    }

    private void applyBoundedEntityImpulse(
        List<Entity> candidates,
        AABB startBox,
        Vec3 requestedMovement,
        Direction direction,
        double incidentSpeed
    ) {
        Vec3 normal = Vec3.atLowerCornerOf(direction.getNormal());
        for (Entity other : candidates) {
            if (!contactsFace(startBox, other.getBoundingBox(), requestedMovement, direction)) continue;
            double relativeSpeed = incidentSpeed - other.getDeltaMovement().dot(normal);
            if (relativeSpeed <= PlasticEntityPhysics.FACE_EPSILON) continue;
            double impulse = Math.min(MAX_ENTITY_IMPULSE, relativeSpeed * 0.45D);
            other.setDeltaMovement(other.getDeltaMovement().add(normal.scale(impulse)));
            other.hasImpulse = true;
            other.hurtMarked = true;
        }
    }

    private static boolean contactsFace(
        AABB movingBox,
        AABB otherBox,
        Vec3 requestedMovement,
        Direction direction
    ) {
        double directedMovement = requestedMovement.get(direction.getAxis())
            * direction.getAxisDirection().getStep();
        if (directedMovement <= PlasticEntityPhysics.FACE_EPSILON) return false;

        double gap = switch (direction) {
            case DOWN -> movingBox.minY - otherBox.maxY;
            case UP -> otherBox.minY - movingBox.maxY;
            case WEST -> movingBox.minX - otherBox.maxX;
            case EAST -> otherBox.minX - movingBox.maxX;
            case NORTH -> movingBox.minZ - otherBox.maxZ;
            case SOUTH -> otherBox.minZ - movingBox.maxZ;
        };
        if (gap < -0.08D || gap > directedMovement + 0.08D) return false;

        AABB swept = movingBox.expandTowards(requestedMovement).inflate(PlasticEntityPhysics.FACE_EPSILON);
        double xOverlap = overlap(swept.minX, swept.maxX, otherBox.minX, otherBox.maxX);
        double yOverlap = overlap(swept.minY, swept.maxY, otherBox.minY, otherBox.maxY);
        double zOverlap = overlap(swept.minZ, swept.maxZ, otherBox.minZ, otherBox.maxZ);
        return switch (direction.getAxis()) {
            case X -> yOverlap * zOverlap > PlasticEntityPhysics.FACE_EPSILON;
            case Y -> xOverlap * zOverlap > PlasticEntityPhysics.FACE_EPSILON;
            case Z -> xOverlap * yOverlap > PlasticEntityPhysics.FACE_EPSILON;
        };
    }

    private static double overlap(double firstMin, double firstMax, double secondMin, double secondMax) {
        return Math.max(0.0D, Math.min(firstMax, secondMax) - Math.max(firstMin, secondMin));
    }

    @Override
    protected InteractionResult interactNormally(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty() || !this.hasCapturedMob()) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide) return InteractionResult.SUCCESS;
        return this.releaseCapturedMob(player, this.getOrientation().attachmentFace())
            ? InteractionResult.CONSUME
            : InteractionResult.FAIL;
    }

    /** 在树脂开口上方释放已保存实体，并保留砧物品。 */
    private boolean releaseCapturedMob(Player player, Direction releaseFace) {
        ItemStack stored = this.getDropStack();
        Entity entity = HasMobBlockItem.getMobFromItem(this.level(), stored);
        if (!(entity instanceof Mob mob)) return false;

        Vec3 center = this.getBoundingBox().getCenter();
        double offset = 0.55D + Math.max(mob.getBbWidth(), mob.getBbHeight()) * 0.5D;
        Vec3 target = center.add(Vec3.atLowerCornerOf(releaseFace.getNormal()).scale(offset));
        mob.moveTo(target.x, target.y, target.z, mob.getYRot(), mob.getXRot());
        mob.setDeltaMovement(Vec3.ZERO);
        if (!this.level().noCollision(mob, mob.getBoundingBox().inflate(0.02D))) return false;
        if (!this.level().addFreshEntity(mob)) return false;

        stored.remove(ModComponents.SAVED_ENTITY);
        this.setDropStack(stored);
        this.level().playSound(
            null,
            this.blockPosition(),
            dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK.getDefaultState().getSoundType().getPlaceSound(),
            SoundSource.BLOCKS,
            0.8F,
            0.9F + this.random.nextFloat() * 0.2F
        );
        this.gameEvent(net.minecraft.world.level.gameevent.GameEvent.ENTITY_PLACE, player);
        return true;
    }
}
