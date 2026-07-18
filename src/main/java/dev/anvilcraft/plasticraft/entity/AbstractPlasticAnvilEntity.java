package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.api.entity.CarrierMovableEntity;
import dev.anvilcraft.plasticraft.api.entity.PlasticGravityTypeProvider;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
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

    private static final double FALLING_BLOCK_DRAG = 0.98D;
    private static final EntityDataAccessor<Byte> ORIENTATION = SynchedEntityData.defineId(
        AbstractPlasticAnvilEntity.class,
        EntityDataSerializers.BYTE
    );
    private static final EntityDataAccessor<BlockState> DISPLAY_STATE = SynchedEntityData.defineId(
        AbstractPlasticAnvilEntity.class,
        EntityDataSerializers.BLOCK_STATE
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
            .define(DISPLAY_STATE, Blocks.SAND.defaultBlockState());
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
    }

    public final PlasticAnvilOrientation getOrientation() {
        return PlasticAnvilOrientation.unpack(Byte.toUnsignedInt(this.entityData.get(ORIENTATION)));
    }

    public final void setOrientation(PlasticAnvilOrientation orientation) {
        this.entityData.set(ORIENTATION, (byte) Objects.requireNonNull(orientation, "orientation").pack());
    }

    public final ItemStack getDropStack() {
        ItemStack stack = this.dropStack.isEmpty() ? this.createDefaultDropStack() : this.dropStack;
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    public final void setDropStack(ItemStack stack) {
        this.dropStack = stack == null ? ItemStack.EMPTY : stack.copy();
        if (this.dropStack.getCount() > 1) {
            this.dropStack.setCount(1);
        }
    }

    protected abstract ItemStack createDefaultDropStack();

    protected abstract void openAnvilMenu(ServerPlayer player);

    @Override
    public GravityType plasticraft$getGravityType() {
        return GravityType.LOW_GRAVITY;
    }

    protected boolean isBuoyantInFluids() {
        return true;
    }

    protected double getFluidBuoyancyAcceleration() {
        double gravityScalar = Math.abs(this.plasticraft$getGravityType().getScalar());
        return 0.08D * gravityScalar * GravityManager.getDimensionGravity(this.level());
    }

    /**
     * AnvilCraft's landing recipe contract is world-down oriented: consumers
     * inspect {@code event.pos.below()}. Other impact directions remain valid
     * physics contacts, but cannot be posted as that event without targeting
     * the wrong block.
     */
    protected boolean triggersAnvilCraftLandingEvents(Direction impactDirection) {
        return impactDirection == Direction.DOWN;
    }

    /** Future plastic types can replace destruction with their own damaged state or durability model. */
    protected void handleAnvilCraftLandingDamage(AnvilEvent.OnLand event) {
        if (!this.isSilent()) {
            this.level().levelEvent(1029, event.getPos(), 0);
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
        double buoyancy = this.isBuoyantInFluids() && this.isInFluidType()
            ? this.getFluidBuoyancyAcceleration()
            : 0.0D;
        if (!this.isNoGravity() && !controlledByRing) {
            this.applyGravity();
            if (buoyancy != 0.0D) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, buoyancy, 0.0D));
            }
        }

        Vec3 effectiveGravity = this.isNoGravity()
            ? Vec3.ZERO
            : GravityManager.getNetGravityVectorForFallingBlock(this).add(0.0D, buoyancy, 0.0D);
        Direction gravityDirection = controlledByRing
            ? null
            : PlasticAnvilPhysics.directionOrNull(effectiveGravity);
        this.updateEffectiveGravityDirection(gravityDirection);
        if (accelerationRequestedMovement.lengthSqr()
            > PlasticAnvilPhysics.FACE_EPSILON * PlasticAnvilPhysics.FACE_EPSILON) {
            boolean hadBlockSupportBeforeAcceleration = gravityDirection != null
                && PlasticAnvilPhysics.hasBlockSupport(this, boxBeforeAcceleration, gravityDirection);
            Entity accelerationSupport = gravityDirection == null
                ? null
                : PlasticAnvilPhysics.findSupport(this, gravityDirection);
            this.updateLandingState(
                gravityDirection,
                accelerationRequestedMovement,
                accelerationActualMovement,
                accelerationSupport != null,
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
        Vec3 velocityAfterMove = this.getDeltaMovement();
        boolean entitySupportCollision = gravityDirection != null
            && support != null
            && PlasticAnvilPhysics.collidedAlongGravity(requestedMovement, actualMovement, gravityDirection)
            && !PlasticAnvilPhysics.hasBlockSupport(this, gravityDirection);
        if (entitySupportCollision) {
            velocityAfterMove = PlasticAnvilPhysics.removeIntoSupportVelocity(velocityAfterMove, gravityDirection);
        }
        this.setDeltaMovement(velocityAfterMove.scale(FALLING_BLOCK_DRAG));

        Direction postMoveGravityDirection = this.isNoGravity() || AccelerateManager.isControlledByRing(this)
            ? null
            : PlasticAnvilPhysics.directionOrNull(
                GravityManager.getNetGravityVectorForFallingBlock(this).add(0.0D, buoyancy, 0.0D)
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
            postMoveSupport != null,
            hadBlockSupportBeforeMove
        );
        if (this.isRemoved()) {
            return;
        }
        this.supportObservation = postMoveSupport == null
            ? null
            : PlasticAnvilPhysics.SupportObservation.capture(postMoveSupport);
        this.supportDirection = postMoveSupport == null ? null : gravityDirection;

        // FallingBlockEntityMixin normally adds these two components at tick tail.
        if (!this.isNoGravity() && !AccelerateManager.isControlledByRing(this)) {
            Vec3 localGravity = GravityManager.getGravityVector(this);
            this.setDeltaMovement(this.getDeltaMovement().add(localGravity.x, 0.0D, localGravity.z));
        }

        if (accelerationChangedMovement) {
            this.hasImpulse = true;
            this.hurtMarked = true;
        }

        PlasticAnvilPhysics.pushSideEntities(
            this,
            gravityDirection,
            this.supportObservation == null ? null : this.supportObservation.entityId()
        );
        this.checkBelowWorld();
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
        return !this.carrierMoveInProgress
            && this.supportObservation != null
            && this.supportDirection != null
            && this.supportObservation.entityId().equals(carrier.getUUID())
            && PlasticAnvilPhysics.canMoveWithCarrier(this, carrier, this.supportDirection, requestedMovement);
    }

    @Override
    public void plasticraft$moveWithCarrier(Entity carrier, Vec3 actualMovement) {
        if (this.supportObservation == null
            || this.supportDirection == null
            || !this.supportObservation.entityId().equals(carrier.getUUID())
            || actualMovement.lengthSqr() <= PlasticAnvilPhysics.FACE_EPSILON * PlasticAnvilPhysics.FACE_EPSILON
            || !PlasticAnvilPhysics.isWithinCarryDistance(actualMovement)
            || actualMovement.dot(Vec3.atLowerCornerOf(this.supportDirection.getNormal()))
                > PlasticAnvilPhysics.FACE_EPSILON) {
            return;
        }
        this.moveWithCarrierDisplacement(actualMovement);
        this.supportObservation = PlasticAnvilPhysics.SupportObservation.capture(carrier);
    }

    @Override
    public boolean plasticraft$canCompleteCarrierMovement(Entity carrier, Vec3 requestedMovement) {
        if (!this.plasticraft$canMoveWithCarrier(carrier, requestedMovement)) return false;
        List<VoxelShape> entityCollisions = this.level().getEntities(
            this,
            this.getBoundingBox().expandTowards(requestedMovement).inflate(PlasticAnvilPhysics.FACE_EPSILON),
            other -> !other.isRemoved()
                && !other.isSpectator()
                && other != carrier
                && !other.isPassengerOfSameVehicle(carrier)
                && this.canCollideWith(other)
        ).stream().map(other -> Shapes.create(other.getBoundingBox())).toList();
        Vec3 allowedMovement = Entity.collideBoundingBox(
            this,
            requestedMovement,
            this.getBoundingBox(),
            this.level(),
            entityCollisions
        );
        return allowedMovement.distanceToSqr(requestedMovement)
            <= PlasticAnvilPhysics.FACE_EPSILON * PlasticAnvilPhysics.FACE_EPSILON;
    }

    private void moveWithCarrierDisplacement(Vec3 movement) {
        if (movement.lengthSqr() <= PlasticAnvilPhysics.FACE_EPSILON * PlasticAnvilPhysics.FACE_EPSILON) return;
        Vec3 velocity = this.getDeltaMovement();
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
    }

    @Override
    public void push(Entity entity) {
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
        double buoyancy = this.isBuoyantInFluids() && this.isInFluidType()
            ? this.getFluidBuoyancyAcceleration()
            : 0.0D;
        Direction direction = PlasticAnvilPhysics.directionOrNull(
            GravityManager.getNetGravityVectorForFallingBlock(this).add(0.0D, buoyancy, 0.0D)
        );
        return direction == null ? Direction.DOWN : direction;
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
    public InteractionResult interact(Player player, InteractionHand hand) {
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
        this.effectiveGravityDirection = Direction.byName(tag.getString("EffectiveGravityDirection"));
        if (this.effectiveGravityDirection == null) {
            this.directionalFallDistance = 0.0F;
            this.blockContactMask = 0;
            this.impactTrackingArmed = false;
        }
    }
}
