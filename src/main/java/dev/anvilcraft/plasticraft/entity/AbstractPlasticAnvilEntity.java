package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.api.entity.CarrierMovableEntity;
import dev.anvilcraft.plasticraft.api.entity.PlasticGravityTypeProvider;
import dev.anvilcraft.plasticraft.api.item.EntityFacePlaceableItem;
import dev.anvilcraft.plasticraft.entity.collision.PlasticPushChain;
import dev.anvilcraft.plasticraft.block.PlasticAnvilBlock;
import dev.anvilcraft.plasticraft.block.PlasticPotBlock;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.util.AccelerateManager;
import dev.dubhe.anvilcraft.util.GravityManager;
import dev.dubhe.anvilcraft.util.GravityType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.Objects;

/**
 * Shared persistent falling-block implementation for entity-backed plastic anvils.
 *
 * <p>The vanilla falling-block tick cannot be used here: it settles into a block,
 * while a plastic anvil must remain a collidable, pushable entity. This tick keeps
 * the AnvilCraft gravity and acceleration entry points while owning that different
 * lifecycle explicitly.</p>
 */
public abstract class AbstractPlasticAnvilEntity extends FallingBlockEntity
    implements PlasticGravityTypeProvider, CarrierMovableEntity {
    public static final float COLLISION_SIZE = 0.98F;

    private static final double AIR_DRAG = 0.98D;
    private static final double FLUID_VERTICAL_DRAG = 0.82D;
    private static final double FLUID_HORIZONTAL_DRAG = 0.86D;
    private static final EntityDataAccessor<Byte> ORIENTATION = SynchedEntityData.defineId(
        AbstractPlasticAnvilEntity.class,
        EntityDataSerializers.BYTE
    );
    private static final EntityDataAccessor<BlockState> DISPLAY_STATE = SynchedEntityData.defineId(
        AbstractPlasticAnvilEntity.class,
        EntityDataSerializers.BLOCK_STATE
    );
    private static final EntityDataAccessor<Boolean> MAGNETIZED = SynchedEntityData.defineId(
        AbstractPlasticAnvilEntity.class,
        EntityDataSerializers.BOOLEAN
    );

    private ItemStack dropStack = ItemStack.EMPTY;
    private PlasticAnvilPhysics.SupportObservation supportObservation;
    private Direction supportDirection;
    private Direction effectiveGravityDirection;
    private float directionalFallDistance;
    private int blockContactMask;
    private boolean carrierMoveInProgress;
    private boolean impactTrackingArmed;
    private boolean accelerationPositionCapture;
    private boolean accelerationMoveInProgress;
    private Vec3 accelerationRequestedMovement = Vec3.ZERO;
    private Vec3 accelerationActualMovement = Vec3.ZERO;
    private int impactContactMask;
    private int previousImpactContactMask;
    private int impactSoundTick = Integer.MIN_VALUE;
    private int impactSoundMask;
    private long lastSidePushGameTime = Long.MIN_VALUE;
    private Vec3 sidePushVelocity = Vec3.ZERO;
    private Direction sidePushGravityDirection = Direction.DOWN;
    private boolean clientSnapshotPending;
    private double clientSnapshotX;
    private double clientSnapshotY;
    private double clientSnapshotZ;
    private float clientSnapshotYRot;
    private float clientSnapshotXRot;
    private long clientCarrierMoveGameTime = Long.MIN_VALUE;
    private double clientCarrierMoveStartX;
    private double clientCarrierMoveStartY;
    private double clientCarrierMoveStartZ;

    protected AbstractPlasticAnvilEntity(
        EntityType<? extends AbstractPlasticAnvilEntity> entityType,
        Level level
    ) {
        super(entityType, level);
        this.blocksBuilding = true;
        this.setNoGravity(false);
        this.refreshDimensions();
    }

    protected AbstractPlasticAnvilEntity(
        EntityType<? extends AbstractPlasticAnvilEntity> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticAnvilOrientation orientation
    ) {
        this(entityType, level);
        this.setDisplayState(displayState);
        this.setOrientation(orientation);
        this.setDropStack(dropStack);
        this.setMagnetized(PlasticItemData.isMagnetized(dropStack == null ? ItemStack.EMPTY : dropStack));
        this.setPos(position);
        this.xo = position.x;
        this.yo = position.y;
        this.zo = position.z;
        this.setStartPos(this.blockPosition());
        this.impactTrackingArmed = false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ORIENTATION, (byte) PlasticAnvilOrientation.DEFAULT.pack())
            .define(DISPLAY_STATE, Blocks.SAND.defaultBlockState())
            .define(MAGNETIZED, false);
    }

    public final BlockState getDisplayState() {
        return this.entityData.get(DISPLAY_STATE);
    }

    /** Keeps rendering, AnvilCraft recipes, spawn packets, and later runtime variant changes in lockstep. */
    public final void setDisplayState(BlockState state) {
        BlockState displayState = Objects.requireNonNull(state, "state");
        this.blockState = displayState;
        this.entityData.set(DISPLAY_STATE, displayState);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DISPLAY_STATE.equals(key)) {
            this.blockState = this.entityData.get(DISPLAY_STATE);
        }
    }

    @Override
    public final BlockState getBlockState() {
        return this.getDisplayState();
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.setDisplayState(this.blockState);
        this.clientSnapshotX = this.getX();
        this.clientSnapshotY = this.getY();
        this.clientSnapshotZ = this.getZ();
        this.clientSnapshotYRot = this.getYRot();
        this.clientSnapshotXRot = this.getXRot();
        this.clientSnapshotPending = false;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (!this.level().isClientSide) {
            super.lerpTo(x, y, z, yRot, xRot, steps);
            return;
        }
        this.clientSnapshotX = x;
        this.clientSnapshotY = y;
        this.clientSnapshotZ = z;
        this.clientSnapshotYRot = yRot;
        this.clientSnapshotXRot = xRot;
        this.clientSnapshotPending = true;
    }

    @Override
    public double lerpTargetX() {
        return this.clientSnapshotPending ? this.clientSnapshotX : this.getX();
    }

    @Override
    public double lerpTargetY() {
        return this.clientSnapshotPending ? this.clientSnapshotY : this.getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.clientSnapshotPending ? this.clientSnapshotZ : this.getZ();
    }

    @Override
    public float lerpTargetYRot() {
        return this.clientSnapshotPending ? this.clientSnapshotYRot : this.getYRot();
    }

    @Override
    public float lerpTargetXRot() {
        return this.clientSnapshotPending ? this.clientSnapshotXRot : this.getXRot();
    }

    public final PlasticAnvilOrientation getOrientation() {
        return PlasticAnvilOrientation.unpack(Byte.toUnsignedInt(this.entityData.get(ORIENTATION)));
    }

    public final void setOrientation(PlasticAnvilOrientation orientation) {
        this.entityData.set(ORIENTATION, (byte) Objects.requireNonNull(orientation, "orientation").pack());
    }

    public final boolean isMagnetized() {
        return this.entityData.get(MAGNETIZED);
    }

    public final int getDisplayTint() {
        BlockState state = this.getDisplayState();
        if (state.getBlock() instanceof PlasticAnvilBlock && state.hasProperty(PlasticAnvilBlock.COLOR)) {
            return PlasticAnvilBlock.tint(state.getValue(PlasticAnvilBlock.COLOR));
        }
        if (state.getBlock() instanceof PlasticPotBlock && state.hasProperty(PlasticPotBlock.COLOR)) {
            return PlasticPotBlock.tint(state.getValue(PlasticPotBlock.COLOR));
        }
        return 0xFFFFFF;
    }

    public final void setMagnetized(boolean magnetized) {
        this.entityData.set(MAGNETIZED, magnetized);
    }

    @Override
    public final boolean anvilcraft$isMagnetized() {
        return this.isMagnetized();
    }

    @Override
    public final boolean anvilcraft$canCollisionCraft() {
        // Horizontal collision crafting is an iron-anvil behavior. Plastic
        // entities use the directional landing hook below instead.
        return false;
    }

    @Override
    public final boolean anvilcraft$acceptMagnetization(Player player, ItemStack stack) {
        if (!PlasticMagnetism.isMagnetTool(stack) || !player.isShiftKeyDown()) return false;
        if (this.level().isClientSide) return true;
        this.setMagnetized(!this.isMagnetized());
        this.level().playSound(
            null,
            this.blockPosition(),
            this.isMagnetized() ? SoundEvents.ITEM_FRAME_ADD_ITEM : SoundEvents.ITEM_FRAME_REMOVE_ITEM,
            SoundSource.BLOCKS,
            0.8F,
            1.0F
        );
        return true;
    }

    public final ItemStack getDropStack() {
        ItemStack stack = this.dropStack.isEmpty() ? this.createDefaultDropStack() : this.dropStack;
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        PlasticItemData.setMagnetized(copy, this.isMagnetized());
        return copy;
    }

    public final void setDropStack(ItemStack stack) {
        this.dropStack = stack == null ? ItemStack.EMPTY : stack.copy();
        if (this.dropStack.getCount() > 1) {
            this.dropStack.setCount(1);
        }
    }

    protected abstract ItemStack createDefaultDropStack();

    protected abstract void openAnvilMenu(ServerPlayer player);

    protected final InteractionResult tryMagnetization(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!PlasticMagnetism.isMagnetTool(stack) || !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!this.level().isClientSide) {
            this.anvilcraft$acceptMagnetization(player, stack);
            player.getCooldowns().addCooldown(stack.getItem(), 5);
        }
        return this.level().isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    @Override
    public GravityType plasticraft$getGravityType() {
        return GravityType.NORMAL;
    }

    @Override
    protected Component getTypeName() {
        return this.getDisplayState().getBlock().getName();
    }

    @Override
    public Vec3 anvilcraft$getAdditionalGravity(double baseGravity) {
        return PlasticMagnetism.calculatePointGravity(this, baseGravity);
    }

    protected boolean isBuoyantInFluids() {
        return true;
    }

    protected double getFluidBuoyancyAcceleration(PlasticFluidPhysics.FluidContact contact, Vec3 gravity) {
        if (!contact.isPresent()) return 0.0D;
        // A plastic body is neutrally buoyant at half immersion.  This keeps
        // the centre of mass at the fluid surface instead of launching it out
        // of the water after a few ticks.
        return Math.abs(gravity.y) * 2.0D * contact.submergedFraction();
    }

    /**
     * AnvilCraft's landing recipe contract is world-down oriented: consumers
     * inspect {@code event.pos.below()}. Other impact directions remain valid
     * physics contacts, but cannot be posted as that event without targeting
     * the wrong block.
     */
    protected boolean triggersAnvilCraftLandingEvents(Direction impactDirection) {
        // AnvilCraft's public landing context is world-down based (recipes use
        // "below" offsets), so require both that direction and the physical bottom face.
        return impactDirection == Direction.DOWN
            && impactDirection == this.getOrientation().attachmentFace().getOpposite();
    }

    /** Future plastic types can replace destruction with their own damaged state or durability model. */
    protected void handleAnvilCraftLandingDamage(AnvilEvent.OnLand event) {
        this.handleAnvilCraftRecipeDamage(event.getPos());
    }

    /** Applies a DamageAnvil recipe outcome without fabricating a landing event. */
    protected void handleAnvilCraftRecipeDamage(BlockPos pos) {
        if (!this.isSilent()) {
            this.level().levelEvent(1029, pos, 0);
        }
        this.discard();
    }

    /** Future variants can customize the acceleration source while retaining collision-aware position correction. */
    protected void applyAnvilCraftAcceleration() {
        AccelerateManager.handleAcceleration(this);
    }

    @Override
    public void setPos(double x, double y, double z) {
        if (!this.accelerationPositionCapture || this.accelerationMoveInProgress) {
            super.setPos(x, y, z);
            return;
        }

        Vec3 requestedMovement = new Vec3(x - this.getX(), y - this.getY(), z - this.getZ());
        if (!Double.isFinite(requestedMovement.lengthSqr())
            || requestedMovement.lengthSqr()
                <= PlasticAnvilPhysics.FACE_EPSILON * PlasticAnvilPhysics.FACE_EPSILON) {
            super.setPos(x, y, z);
            return;
        }

        Vec3 startPosition = this.position();
        this.accelerationRequestedMovement = this.accelerationRequestedMovement.add(requestedMovement);
        this.accelerationMoveInProgress = true;
        try {
            // AccelerateManager recenters fast anvils with setPos. Replay only that scoped correction
            // through Entity.move so ordinary teleports retain their normal semantics.
            this.move(MoverType.SELF, requestedMovement);
        } finally {
            this.accelerationActualMovement = this.accelerationActualMovement.add(
                this.position().subtract(startPosition)
            );
            this.accelerationMoveInProgress = false;
        }
    }

    @Override
    public void tick() {
        if (this.level().isClientSide) {
            // ClientLevel snapshots old coordinates before ticking each entity. A local player can
            // push this body earlier in the same entity pass, so retain the pre-push position and
            // let vanilla's partial-tick renderer draw that displacement instead of hiding it.
            if (this.clientCarrierMoveGameTime == this.level().getGameTime()) {
                this.xOld = this.xo = this.clientCarrierMoveStartX;
                this.yOld = this.yo = this.clientCarrierMoveStartY;
                this.zOld = this.zo = this.clientCarrierMoveStartZ;
            }
            // Consume one already-confirmed server snapshot per client tick. Rendering then runs
            // linearly from xOld to this position, giving a fixed one-tick visual delay without
            // integrating gravity, buoyancy, magnetism, or any other prediction on the client.
            if (this.clientSnapshotPending) {
                this.setPos(this.clientSnapshotX, this.clientSnapshotY, this.clientSnapshotZ);
                this.setRot(this.clientSnapshotYRot, this.clientSnapshotXRot);
                this.clientSnapshotPending = false;
            }
            this.firstTick = false;
            return;
        }

        // FallingBlockEntity.tick intentionally skips Entity#baseTick. Keep the
        // interpolation and edge-trigger state that baseTick normally advances.
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
        this.previousImpactContactMask = this.impactContactMask;
        this.impactContactMask = 0;
        // AnvilCraft portal conversion writes FallingBlockEntity#blockState directly.
        // Fold those external mutations back into the tracked display state on the server.
        if (!this.level().isClientSide && !this.blockState.equals(this.getDisplayState())) {
            this.setDisplayState(this.blockState);
        }

        // FallingBlockEntity owns a compact lifecycle and does not call Entity#baseTick.
        // Refresh fluid occupancy explicitly because buoyancy depends on NeoForge's fluid map.
        this.updateInWaterStateAndDoFluidPushing();
        // Keep the persistent entity's fluid/lava lifecycle consistent after its first tick;
        // vanilla FallingBlockEntity normally disappears before this distinction matters.
        this.firstTick = false;

        if (this.time < Integer.MAX_VALUE) {
            this.time++;
        }

        Vec3 velocityBeforeAcceleration = this.getDeltaMovement();
        AABB boxBeforeAcceleration = this.getBoundingBox();
        this.accelerationRequestedMovement = Vec3.ZERO;
        this.accelerationActualMovement = Vec3.ZERO;
        this.accelerationPositionCapture = true;
        try {
            this.applyAnvilCraftAcceleration();
        } finally {
            this.accelerationPositionCapture = false;
        }
        Vec3 accelerationRequestedMovement = this.accelerationRequestedMovement;
        Vec3 accelerationActualMovement = this.accelerationActualMovement;
        boolean controlledByRing = AccelerateManager.isControlledByRing(this);
        boolean accelerationChangedMovement = !this.getDeltaMovement().equals(velocityBeforeAcceleration)
            || accelerationActualMovement.lengthSqr()
                > PlasticAnvilPhysics.FACE_EPSILON * PlasticAnvilPhysics.FACE_EPSILON;
        PlasticFluidPhysics.FluidContact fluidContact = PlasticFluidPhysics.sample(this);
        Vec3 gravity = this.isNoGravity()
            ? Vec3.ZERO
            : GravityManager.getNetGravityVectorForFallingBlock(this);
        double buoyancy = !controlledByRing && this.isBuoyantInFluids()
            ? this.getFluidBuoyancyAcceleration(fluidContact, gravity)
            : 0.0D;
        Vec3 effectiveGravity = controlledByRing
            ? Vec3.ZERO
            : gravity.add(0.0D, buoyancy, 0.0D);
        if (!this.isNoGravity() && !controlledByRing) {
            this.setDeltaMovement(this.getDeltaMovement().add(effectiveGravity));
        }
        Direction gravityDirection = controlledByRing
            ? null
            : PlasticAnvilPhysics.directionOrNull(effectiveGravity);
        this.updateEffectiveGravityDirection(gravityDirection);
        long sidePushAge = this.lastSidePushGameTime == Long.MIN_VALUE
            ? Long.MAX_VALUE
            : this.level().getGameTime() - this.lastSidePushGameTime;
        boolean deferSidePushVelocity = sidePushAge >= 0L && sidePushAge <= 1L;
        Vec3 deferredSidePush = deferSidePushVelocity ? this.sidePushVelocity : Vec3.ZERO;
        if (accelerationRequestedMovement.lengthSqr()
            > PlasticAnvilPhysics.FACE_EPSILON * PlasticAnvilPhysics.FACE_EPSILON) {
            boolean hadBlockSupportBeforeAcceleration = gravityDirection != null
                && PlasticAnvilPhysics.hasBlockSupport(this, boxBeforeAcceleration, gravityDirection);
            Entity accelerationSupport = gravityDirection == null
                ? null
                : PlasticAnvilPhysics.findSupport(this, boxBeforeAcceleration, gravityDirection);
            Entity accelerationPostSupport = gravityDirection == null
                ? null
                : PlasticAnvilPhysics.findSupport(this, gravityDirection);
            this.handleEntityImpact(
                gravityDirection,
                accelerationPostSupport,
                accelerationRequestedMovement,
                accelerationActualMovement,
                hadBlockSupportBeforeAcceleration,
                accelerationSupport != null
                    && PlasticAnvilPhysics.hasImmediateEntityContact(
                        this,
                        boxBeforeAcceleration,
                        accelerationSupport,
                        gravityDirection
                    )
            );
            this.handleNewCollisionContacts(accelerationRequestedMovement, accelerationActualMovement);
            this.updateLandingState(
                gravityDirection,
                accelerationRequestedMovement,
                accelerationActualMovement,
                accelerationPostSupport != null
                    && PlasticAnvilPhysics.hasImmediateEntityContact(
                        this,
                        accelerationPostSupport,
                        gravityDirection
                    ),
                hadBlockSupportBeforeAcceleration
            );
            if (this.isRemoved()) return;
        } else {
            this.initializeExistingBlockContact(gravityDirection);
        }
        Entity support = gravityDirection == null
            ? null
            : PlasticAnvilPhysics.findSupport(this, gravityDirection);
        PlasticAnvilPhysics.SupportObservation preMoveObservation = support == null
            ? null
            : PlasticAnvilPhysics.SupportObservation.capture(support);

        Vec3 observedCarrierMovement = preMoveObservation == null
            ? Vec3.ZERO
            : PlasticAnvilPhysics.carriedMovement(
                this.supportObservation,
                preMoveObservation,
                gravityDirection
        );
        this.moveWithCarrierDisplacement(observedCarrierMovement);
        if (this.isRemoved()) return;

        support = gravityDirection == null
            ? null
            : PlasticAnvilPhysics.findSupport(this, gravityDirection);
        boolean hadBlockSupportBeforeMove = gravityDirection != null
            && PlasticAnvilPhysics.hasBlockSupport(this, gravityDirection);
        boolean hadEntitySupportBeforeMove = support != null
            && PlasticAnvilPhysics.hasImmediateEntityContact(this, support, gravityDirection);

        if (deferSidePushVelocity) {
            this.setDeltaMovement(
                this.getDeltaMovement().subtract(
                    PlasticAnvilPhysics.tangentialMovement(
                        this.getDeltaMovement(),
                        this.sidePushGravityDirection
                    )
                )
            );
        }
        Vec3 requestedMovement = this.getDeltaMovement();
        Vec3 positionBeforeMove = this.position();
        this.move(MoverType.SELF, requestedMovement);
        if (this.isRemoved()) return;
        this.handlePortal();
        if (this.isRemoved()) return;
        Vec3 actualMovement = this.position().subtract(positionBeforeMove);
        // Entity.move and AnvilCraft's EntityMixin own collision-axis clearing,
        // swept-gravity velocity impulses, celestial-body clipping, and ring
        // deflection. Reading the post-move velocity preserves all four.
        Vec3 velocityAfterMove = PlasticAnvilPhysics.removeClippedVelocity(
            this.getDeltaMovement(),
            requestedMovement,
            actualMovement
        );
        Entity entitySupportAfterMove = gravityDirection == null
            ? null
            : PlasticAnvilPhysics.findSupport(this, gravityDirection);
        boolean entitySupportCollision = gravityDirection != null
            && entitySupportAfterMove != null
            && PlasticAnvilPhysics.collidedAlongGravity(requestedMovement, actualMovement, gravityDirection)
            && !PlasticAnvilPhysics.hasBlockSupport(this, gravityDirection);
        if (entitySupportCollision) {
            velocityAfterMove = PlasticAnvilPhysics.removeIntoSupportVelocity(velocityAfterMove, gravityDirection);
            this.handleEntityImpact(
                gravityDirection,
                entitySupportAfterMove,
                requestedMovement,
                actualMovement,
                hadBlockSupportBeforeMove,
                hadEntitySupportBeforeMove
            );
        }
        this.handleNewCollisionContacts(requestedMovement, actualMovement);
        PlasticFluidPhysics.FluidContact postMoveFluid = PlasticFluidPhysics.sample(this);
        double horizontalDrag = postMoveFluid.isPresent() ? FLUID_HORIZONTAL_DRAG : AIR_DRAG;
        double verticalDrag = postMoveFluid.isPresent() ? FLUID_VERTICAL_DRAG : AIR_DRAG;
        this.setDeltaMovement(new Vec3(
            velocityAfterMove.x * horizontalDrag,
            velocityAfterMove.y * verticalDrag,
            velocityAfterMove.z * horizontalDrag
        ));
        if (deferSidePushVelocity) {
            this.setDeltaMovement(this.getDeltaMovement().add(deferredSidePush.scale(horizontalDrag)));
        }
        this.applySurfaceFriction(gravityDirection);

        Direction postMoveGravityDirection = this.isNoGravity() || AccelerateManager.isControlledByRing(this)
            ? null
            : PlasticAnvilPhysics.directionOrNull(
                GravityManager.getNetGravityVectorForFallingBlock(this)
                    .add(0.0D, this.getFluidBuoyancyAcceleration(postMoveFluid,
                        GravityManager.getNetGravityVectorForFallingBlock(this)), 0.0D)
            );
        if (postMoveGravityDirection != gravityDirection) {
            gravityDirection = postMoveGravityDirection;
            this.updateEffectiveGravityDirection(gravityDirection);
            // A direction ignored during movement cannot have an existing tracked support edge.
            hadBlockSupportBeforeMove = false;
        }
        Entity postMoveSupport = gravityDirection == null
            ? null
            : PlasticAnvilPhysics.findSupport(this, gravityDirection);
        this.updateLandingState(
            gravityDirection,
            requestedMovement,
            actualMovement,
            postMoveSupport != null
                && PlasticAnvilPhysics.hasImmediateEntityContact(this, postMoveSupport, gravityDirection),
            hadBlockSupportBeforeMove
        );
        if (this.isRemoved()) {
            return;
        }
        this.supportObservation = postMoveSupport == null
            ? null
            : PlasticAnvilPhysics.SupportObservation.capture(postMoveSupport);
        this.supportDirection = postMoveSupport == null ? null : gravityDirection;

        if (accelerationChangedMovement) {
            this.hasImpulse = true;
            this.hurtMarked = true;
        }

        PlasticAnvilPhysics.pushSideEntities(
            this,
            gravityDirection,
            this.supportObservation == null ? null : this.supportObservation.entityId()
        );
        if (postMoveFluid.isPresent() || !actualMovement.equals(requestedMovement)) {
            this.hasImpulse = true;
            this.hurtMarked = true;
        }
        this.checkBelowWorld();
    }

    private void applySurfaceFriction(Direction gravityDirection) {
        if (gravityDirection == null || !PlasticAnvilPhysics.hasBlockSupport(this, gravityDirection)) return;
        BlockPos supportPos = PlasticAnvilPhysics.landingPosition(this, gravityDirection).relative(gravityDirection);
        BlockState supportState = this.level().getBlockState(supportPos);
        float friction = supportState.getFriction(this.level(), supportPos, this);
        double retention;
        if (friction >= 0.9F) {
            retention = 0.96D;
        } else if (friction >= 0.75F) {
            retention = 0.45D;
        } else {
            retention = 0.08D;
        }

        Vec3 velocity = this.getDeltaMovement();
        Vec3 normal = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        double normalVelocity = velocity.dot(normal);
        Vec3 tangential = velocity.subtract(normal.scale(normalVelocity)).scale(retention);
        if (tangential.lengthSqr() < 1.0E-5D) tangential = Vec3.ZERO;
        this.setDeltaMovement(tangential.add(normal.scale(normalVelocity)));
    }

    private void updateLandingState(
        Direction gravityDirection,
        Vec3 requestedMovement,
        Vec3 actualMovement,
        boolean hasEntitySupport,
        boolean hadBlockSupportBeforeMove
    ) {
        if (gravityDirection == null) {
            this.directionalFallDistance = 0.0F;
            this.blockContactMask = 0;
            return;
        }

        Vec3 normal = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        double fallStep = actualMovement.dot(normal);
        if (fallStep > 0.0D) {
            this.directionalFallDistance += (float) fallStep;
            if (!hadBlockSupportBeforeMove) {
                this.impactTrackingArmed = true;
            }
        }

        int directionMask = PlasticAnvilPhysics.directionMask(gravityDirection);
        boolean blockContact = PlasticAnvilPhysics.hasBlockSupport(this, gravityDirection);
        boolean newImpact = blockContact
            && (this.blockContactMask & directionMask) == 0
            && this.impactTrackingArmed
            && PlasticAnvilPhysics.collidedAlongGravity(requestedMovement, actualMovement, gravityDirection)
            && this.directionalFallDistance > PlasticAnvilPhysics.FACE_EPSILON;
        if (newImpact) {
            this.playImpactSound(gravityDirection, requestedMovement.length());
            this.postLandingEvent(gravityDirection);
            this.directionalFallDistance = 0.0F;
            this.impactTrackingArmed = false;
        } else if (hasEntitySupport) {
            this.directionalFallDistance = 0.0F;
            this.impactTrackingArmed = false;
        } else if (!blockContact && fallStep < -PlasticAnvilPhysics.FACE_EPSILON) {
            this.directionalFallDistance = 0.0F;
            this.impactTrackingArmed = false;
        }
        this.blockContactMask = blockContact ? directionMask : 0;
    }

    /** Handles a newly encountered entity support before the generic landing state clears its fall edge. */
    private void handleEntityImpact(
        Direction gravityDirection,
        Entity support,
        Vec3 requestedMovement,
        Vec3 actualMovement,
        boolean hadBlockSupportBeforeMove,
        boolean hadEntitySupportBeforeMove
    ) {
        if (gravityDirection == null
            || support == null
            || hadBlockSupportBeforeMove
            || hadEntitySupportBeforeMove
            || PlasticAnvilPhysics.hasBlockSupport(this, gravityDirection)
            || !PlasticAnvilPhysics.collidedAlongGravity(requestedMovement, actualMovement, gravityDirection)) {
            return;
        }

        Vec3 normal = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        double fallStep = actualMovement.dot(normal);
        float impactDistance = this.directionalFallDistance + (float) Math.max(0.0D, fallStep);
        if (impactDistance <= PlasticAnvilPhysics.FACE_EPSILON
            || (!this.impactTrackingArmed && fallStep <= PlasticAnvilPhysics.FACE_EPSILON)) {
            return;
        }

        this.directionalFallDistance = 0.0F;
        this.impactTrackingArmed = false;
    }

    /** Called once when the effective-gravity face first collides with another entity. */
    protected void onEntityImpact(Entity support, Direction impactDirection, float fallDistance) {
        if (this instanceof PlasticAnvilEntity anvil && support instanceof PlasticPotEntity pot) {
            pot.processAnvilImpact(anvil, impactDirection);
        }
    }

    private void handleNewCollisionContacts(Vec3 requested, Vec3 actual) {
        int contacts = PlasticAnvilPhysics.clippedDirectionMask(requested, actual, 0.04D);
        int newContacts = contacts & ~this.previousImpactContactMask & ~this.impactContactMask;
        this.impactContactMask |= contacts;
        if (newContacts == 0) return;
        for (Direction direction : Direction.values()) {
            if ((newContacts & PlasticAnvilPhysics.directionMask(direction)) == 0) continue;
            this.playImpactSound(direction, Math.abs(requested.get(direction.getAxis())));
            if (PlasticAnvilPhysics.hasBlockSupport(this, direction)) continue;
            Entity contact = PlasticAnvilPhysics.findSupport(this, direction);
            if (contact != null) {
                this.onEntityImpact(contact, direction, (float) requested.length());
            }
        }
    }

    protected void playImpactSound(Direction direction, double speed) {
        if (this.level().isClientSide || this.isSilent()) return;
        if (this.impactSoundTick != this.tickCount) {
            this.impactSoundTick = this.tickCount;
            this.impactSoundMask = 0;
        }
        int directionMask = PlasticAnvilPhysics.directionMask(direction);
        if ((this.impactSoundMask & directionMask) != 0) return;
        this.impactSoundMask |= directionMask;
        float volume = (float) Math.min(0.9D, 0.48D + speed * 0.12D);
        float pitch = 0.96F + this.random.nextFloat() * 0.12F;
        this.level().playSound(
            null,
            this.blockPosition(),
            SoundEvents.BONE_BLOCK_PLACE,
            SoundSource.BLOCKS,
            volume,
            pitch
        );
    }

    private void updateEffectiveGravityDirection(Direction gravityDirection) {
        if (this.effectiveGravityDirection != gravityDirection) {
            this.directionalFallDistance = 0.0F;
            this.blockContactMask = 0;
            this.impactTrackingArmed = false;
        }
        this.effectiveGravityDirection = gravityDirection;
    }

    private void initializeExistingBlockContact(Direction gravityDirection) {
        if (gravityDirection != null
            && this.blockContactMask == 0
            && this.directionalFallDistance <= PlasticAnvilPhysics.FACE_EPSILON
            && PlasticAnvilPhysics.hasImmediateBlockContact(this, gravityDirection)) {
            this.blockContactMask = PlasticAnvilPhysics.directionMask(gravityDirection);
        }
    }

    private void postLandingEvent(Direction gravityDirection) {
        if (this.level().isClientSide || !this.triggersAnvilCraftLandingEvents(gravityDirection)) return;
        BlockPos landingPos = PlasticAnvilPhysics.landingPosition(this, gravityDirection);
        AnvilEvent.OnLand event = new AnvilEvent.OnLand(
            this.level(),
            landingPos,
            this,
            this.directionalFallDistance
        );
        NeoForge.EVENT_BUS.post(event);
        if (event.isAnvilDamage()) {
            this.handleAnvilCraftLandingDamage(event);
        }
    }

    @Override
    public float anvilcraft$getFallDistance() {
        return this.directionalFallDistance;
    }

    @Override
    public boolean plasticraft$canMoveWithCarrier(Entity carrier, Vec3 requestedMovement) {
        // Plastic-to-plastic transfer is planned atomically by PlasticPushChain.
        // Letting the generic carrier hook participate as well both duplicates
        // chain movement and turns genuine anvil/pot impacts into pass-through pushes.
        return !(carrier instanceof AbstractPlasticAnvilEntity)
            && !this.carrierMoveInProgress
            && this.carrierMovement(carrier, carrier.getBoundingBox(), requestedMovement) != null;
    }

    @Override
    public void plasticraft$moveWithCarrier(Entity carrier, Vec3 actualMovement) {
        if (actualMovement.lengthSqr() <= PlasticAnvilPhysics.FACE_EPSILON * PlasticAnvilPhysics.FACE_EPSILON
            || !PlasticAnvilPhysics.isWithinCarryDistance(actualMovement)) return;

        boolean supportedCarrier = this.isCurrentSupport(carrier);
        Vec3 transferredMovement;
        if (supportedCarrier) {
            Vec3 normal = Vec3.atLowerCornerOf(this.supportDirection.getNormal());
            if (actualMovement.dot(normal) > PlasticAnvilPhysics.FACE_EPSILON) return;
            transferredMovement = actualMovement;
        } else {
            AABB previousCarrierBox = carrier.getBoundingBox().move(actualMovement.scale(-1.0D));
            transferredMovement = this.carrierMovement(carrier, previousCarrierBox, actualMovement);
            if (transferredMovement == null) return;
        }

        PlasticPushChain.Plan pushPlan = null;
        if (!supportedCarrier) {
            pushPlan = PlasticPushChain.create(this, carrier, transferredMovement);
            if (pushPlan == null) return;
            pushPlan.move(this, carrier);
        }
        Vec3 moved = this.moveWithCarrierDisplacement(transferredMovement);
        if (supportedCarrier) {
            this.supportObservation = PlasticAnvilPhysics.SupportObservation.capture(carrier);
            return;
        }

        Direction gravityDirection = this.currentPushGravityDirection();
        this.lastSidePushGameTime = this.level().getGameTime();
        this.sidePushGravityDirection = gravityDirection;
        this.sidePushVelocity = PlasticAnvilPhysics.tangentialMovement(moved, gravityDirection);
        Vec3 normal = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        double normalVelocity = this.getDeltaMovement().dot(normal);
        this.setDeltaMovement(this.sidePushVelocity.add(normal.scale(normalVelocity)));
        this.hasImpulse = true;
        this.hurtMarked = true;
    }

    @Override
    public boolean plasticraft$canCompleteCarrierMovement(Entity carrier, Vec3 requestedMovement) {
        if (carrier instanceof AbstractPlasticAnvilEntity) return false;
        Vec3 targetMovement = this.carrierMovement(carrier, carrier.getBoundingBox(), requestedMovement);
        if (this.carrierMoveInProgress || targetMovement == null) return false;
        if (!this.isCurrentSupport(carrier)) {
            return PlasticPushChain.create(this, carrier, targetMovement) != null;
        }
        List<VoxelShape> entityCollisions = this.level().getEntities(
            this,
            this.getBoundingBox().expandTowards(targetMovement).inflate(PlasticAnvilPhysics.FACE_EPSILON),
            other -> !other.isRemoved()
                && !other.isSpectator()
                && other != carrier
                && !other.isPassengerOfSameVehicle(carrier)
                && this.canCollideWith(other)
        ).stream().map(other -> Shapes.create(other.getBoundingBox())).toList();
        Vec3 allowedMovement = Entity.collideBoundingBox(
            this,
            targetMovement,
            this.getBoundingBox(),
            this.level(),
            entityCollisions
        );
        return allowedMovement.distanceToSqr(targetMovement)
            <= PlasticAnvilPhysics.FACE_EPSILON * PlasticAnvilPhysics.FACE_EPSILON;
    }

    private Vec3 carrierMovement(Entity carrier, AABB carrierBox, Vec3 requestedMovement) {
        if (!PlasticAnvilPhysics.isWithinCarryDistance(requestedMovement)) return null;
        if (this.isCurrentSupport(carrier)
            && PlasticAnvilPhysics.canMoveWithCarrier(this, carrier, this.supportDirection, requestedMovement)) {
            return requestedMovement;
        }
        Direction gravityDirection = this.currentPushGravityDirection();
        return PlasticAnvilPhysics.sidePushMovement(
            this,
            carrier,
            carrierBox,
            gravityDirection,
            requestedMovement
        );
    }

    private boolean isCurrentSupport(Entity carrier) {
        return this.supportObservation != null
            && this.supportDirection != null
            && this.supportObservation.entityId().equals(carrier.getUUID());
    }

    private Vec3 moveWithCarrierDisplacement(Vec3 movement) {
        if (movement.lengthSqr() <= PlasticAnvilPhysics.FACE_EPSILON * PlasticAnvilPhysics.FACE_EPSILON) {
            return Vec3.ZERO;
        }
        Vec3 velocity = this.getDeltaMovement();
        Vec3 start = this.position();
        this.carrierMoveInProgress = true;
        try {
            // AnvilCraft's collision event reads delta movement inside Entity.move.
            // Expose this displacement temporarily so its speed and hit position stay finite.
            this.setDeltaMovement(movement);
            this.move(MoverType.SELF, movement);
        } finally {
            this.setDeltaMovement(velocity);
            this.carrierMoveInProgress = false;
        }
        Vec3 actualMovement = this.position().subtract(start);
        if (this.level().isClientSide
            && actualMovement.lengthSqr()
                > PlasticAnvilPhysics.FACE_EPSILON * PlasticAnvilPhysics.FACE_EPSILON) {
            long gameTime = this.level().getGameTime();
            if (this.clientCarrierMoveGameTime != gameTime) {
                this.clientCarrierMoveGameTime = gameTime;
                this.clientCarrierMoveStartX = start.x;
                this.clientCarrierMoveStartY = start.y;
                this.clientCarrierMoveStartZ = start.z;
            }
            // The packet target acknowledges an earlier server tick. Carry the locally
            // collision-clipped player displacement forward so consuming that snapshot does
            // not pull the body back into the player; the next server packet remains authoritative.
            if (this.clientSnapshotPending) {
                this.clientSnapshotX += actualMovement.x;
                this.clientSnapshotY += actualMovement.y;
                this.clientSnapshotZ += actualMovement.z;
            }
        }
        return actualMovement;
    }

    @Override
    public void push(Entity entity) {
        // Player collision movement is transferred directly by the carrier
        // preflight. Vanilla mutual impulses would slow the player and add a
        // second, much smaller push on top of that displacement.
        if (entity instanceof Player) return;
        // Touching plastic bodies are stable at rest. Their intentional motion
        // is propagated once by PlasticPushChain; vanilla mutual impulses would
        // open a gap before the player starts pushing.
        if (entity instanceof AbstractPlasticAnvilEntity) return;
        if (this.supportObservation != null && this.supportObservation.entityId().equals(entity.getUUID())) {
            return;
        }
        Direction gravityDirection = this.currentPushGravityDirection();
        if (!PlasticAnvilPhysics.isSupportCandidate(this, entity, gravityDirection)
            && PlasticAnvilPhysics.isSideContact(this, entity, gravityDirection)) {
            super.push(entity);
        }
    }

    private Direction currentPushGravityDirection() {
        if (this.effectiveGravityDirection != null) {
            return this.effectiveGravityDirection;
        }
        if (this.isNoGravity() || AccelerateManager.isControlledByRing(this)) {
            return Direction.DOWN;
        }
        Vec3 gravity = GravityManager.getNetGravityVectorForFallingBlock(this);
        double buoyancy = this.isBuoyantInFluids()
            ? this.getFluidBuoyancyAcceleration(PlasticFluidPhysics.sample(this), gravity)
            : 0.0D;
        Direction direction = PlasticAnvilPhysics.directionOrNull(
            gravity.add(0.0D, buoyancy, 0.0D)
        );
        return direction == null ? Direction.DOWN : direction;
    }

    public final Direction plasticraft$currentPushGravityDirection() {
        return this.currentPushGravityDirection();
    }

    public final void plasticraft$applyTransferredPush(Entity pusher, Vec3 movement) {
        Vec3 moved = this.moveWithCarrierDisplacement(movement);
        Direction gravityDirection = this.currentPushGravityDirection();
        this.lastSidePushGameTime = this.level().getGameTime();
        this.sidePushGravityDirection = gravityDirection;
        this.sidePushVelocity = PlasticAnvilPhysics.tangentialMovement(moved, gravityDirection);
        Vec3 normal = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        double normalVelocity = this.getDeltaMovement().dot(normal);
        this.setDeltaMovement(this.sidePushVelocity.add(normal.scale(normalVelocity)));
        this.hasImpulse = true;
        this.hurtMarked = true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return Boat.canVehicleCollide(this, entity);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(COLLISION_SIZE, COLLISION_SIZE);
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.NORMAL;
    }

    @Override
    public ItemStack getPickResult() {
        return this.getDropStack();
    }

    @Override
    public final InteractionResult interact(Player player, InteractionHand hand) {
        InteractionResult magnetResult = this.tryMagnetization(player, hand);
        if (magnetResult != InteractionResult.PASS) return magnetResult;
        if (player.isShiftKeyDown() && player.getItemInHand(hand).getItem() instanceof BlockItem) {
            return InteractionResult.PASS;
        }
        return this.interactNormally(player, hand);
    }

    protected InteractionResult interactNormally(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            this.openAnvilMenu(serverPlayer);
            player.awardStat(Stats.INTERACT_WITH_ANVIL);
            this.gameEvent(GameEvent.ENTITY_INTERACT, player);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 location, InteractionHand hand) {
        InteractionResult magnetResult = this.tryMagnetization(player, hand);
        if (magnetResult != InteractionResult.PASS) return magnetResult;
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;

        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return InteractionResult.PASS;
        Direction face = this.nearestInteractionFace(location);
        BlockPos occupiedPos = BlockPos.containing(this.getBoundingBox().getCenter());
        BlockPos targetPos = occupiedPos.relative(face);
        if (!this.level().getBlockState(targetPos).canBeReplaced()) return InteractionResult.FAIL;
        Vec3 faceLocation = this.faceLocation(face);
        BlockHitResult hit = new BlockHitResult(faceLocation, face, targetPos, false);
        if (this.level().isClientSide) return InteractionResult.SUCCESS;
        if (stack.getItem() instanceof EntityFacePlaceableItem entityItem) {
            return entityItem.plasticraft$placeOnEntityFace(this.level(), player, hand, stack, hit);
        }
        if (stack.getItem() instanceof BlockItem) {
            return stack.useOn(new UseOnContext(this.level(), player, hand, stack, hit));
        }
        return InteractionResult.PASS;
    }

    private Direction nearestInteractionFace(Vec3 relativeLocation) {
        AABB box = this.getBoundingBox();
        Vec3 absolute = this.position().add(relativeLocation);
        Direction best = Direction.UP;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Direction direction : Direction.values()) {
            double distance = Math.abs(switch (direction) {
                case DOWN -> absolute.y - box.minY;
                case UP -> box.maxY - absolute.y;
                case WEST -> absolute.x - box.minX;
                case EAST -> box.maxX - absolute.x;
                case NORTH -> absolute.z - box.minZ;
                case SOUTH -> box.maxZ - absolute.z;
            });
            if (distance < bestDistance) {
                bestDistance = distance;
                best = direction;
            }
        }
        return best;
    }

    private Vec3 faceLocation(Direction face) {
        AABB box = this.getBoundingBox();
        Vec3 center = box.getCenter();
        return switch (face) {
            case DOWN -> new Vec3(center.x, box.minY, center.z);
            case UP -> new Vec3(center.x, box.maxY, center.z);
            case WEST -> new Vec3(box.minX, center.y, center.z);
            case EAST -> new Vec3(box.maxX, center.y, center.z);
            case NORTH -> new Vec3(center.x, center.y, box.minZ);
            case SOUTH -> new Vec3(center.x, center.y, box.maxZ);
        };
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }
        if (!this.level().isClientSide) {
            if (this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                this.spawnAtLocation(this.getDropStack());
            }
            this.discard();
        } else {
            this.markHurt();
        }
        return true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ItemStack stack = this.getDropStack();
        if (!stack.isEmpty()) {
            tag.put("DropStack", stack.save(this.registryAccess()));
        }
        PlasticAnvilOrientation orientation = this.getOrientation();
        tag.putString("AttachmentFace", orientation.attachmentFace().getName());
        tag.putInt("InPlaneRotation", orientation.quarterTurn());
        tag.putFloat("DirectionalFallDistance", this.directionalFallDistance);
        tag.putInt("BlockContactMask", this.blockContactMask);
        tag.putBoolean("ImpactTrackingArmed", this.impactTrackingArmed);
        tag.putBoolean("Magnetized", this.isMagnetized());
        if (this.effectiveGravityDirection != null) {
            tag.putString("EffectiveGravityDirection", this.effectiveGravityDirection.getName());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // The pre-falling-block entity did not save this flag. Keep those entities light and non-damaging.
        CompoundTag fallingBlockData = tag;
        if (!tag.contains("HurtEntities", Tag.TAG_ANY_NUMERIC)) {
            fallingBlockData = tag.copy();
            fallingBlockData.putBoolean("HurtEntities", false);
        }
        super.readAdditionalSaveData(fallingBlockData);
        this.setDisplayState(this.blockState);

        if (tag.contains("DropStack", Tag.TAG_COMPOUND)) {
            this.setDropStack(ItemStack.parseOptional(this.registryAccess(), tag.getCompound("DropStack")));
        } else if (tag.contains("DropItem", Tag.TAG_STRING)) {
            ResourceLocation key = ResourceLocation.parse(tag.getString("DropItem"));
            this.setDropStack(new ItemStack(BuiltInRegistries.ITEM.get(key)));
        }

        Direction attachmentFace = Direction.byName(tag.getString("AttachmentFace"));
        if (attachmentFace != null && tag.contains("InPlaneRotation", Tag.TAG_ANY_NUMERIC)) {
            this.setOrientation(new PlasticAnvilOrientation(attachmentFace, tag.getInt("InPlaneRotation")));
        } else {
            this.setOrientation(PlasticAnvilOrientation.fromLegacyState(this.getBlockState()));
        }

        this.directionalFallDistance = Math.max(0.0F, tag.getFloat("DirectionalFallDistance"));
        this.blockContactMask = Math.max(0, tag.getInt("BlockContactMask"));
        this.impactTrackingArmed = tag.getBoolean("ImpactTrackingArmed");
        this.setMagnetized(tag.getBoolean("Magnetized"));
        this.effectiveGravityDirection = Direction.byName(tag.getString("EffectiveGravityDirection"));
        if (this.effectiveGravityDirection == null) {
            this.directionalFallDistance = 0.0F;
            this.blockContactMask = 0;
            this.impactTrackingArmed = false;
        }
    }
}
