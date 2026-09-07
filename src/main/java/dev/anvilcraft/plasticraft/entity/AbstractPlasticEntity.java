package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.api.entity.CarrierMovableEntity;
import dev.anvilcraft.plasticraft.api.entity.PlasticGravityTypeProvider;
import dev.anvilcraft.plasticraft.api.entity.ShapedCollisionEntity;
import dev.anvilcraft.plasticraft.api.item.EntityFacePlaceableItem;
import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.block.piston.PlasticPistonOccupancy;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveFaces;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondState;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityCollisionBox;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityContactResolver;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import dev.anvilcraft.plasticraft.entity.collision.PlasticCarrierPrediction;
import dev.anvilcraft.plasticraft.entity.collision.PlasticPushChain;
import dev.anvilcraft.plasticraft.entity.physics.PlasticBlockEpoch;
import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityGridSnapping;
import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityPhysics;
import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityRestState;
import dev.anvilcraft.plasticraft.entity.physics.PlasticFallingBlockSupport;
import dev.anvilcraft.plasticraft.entity.physics.PlasticFluidPhysics;
import dev.anvilcraft.plasticraft.entity.physics.PlasticMagnetism;
import dev.anvilcraft.plasticraft.entity.physics.PlasticRestIndex;
import dev.anvilcraft.plasticraft.entity.physics.PlasticSlidingRailPhysics;
import dev.anvilcraft.plasticraft.entity.physics.PlasticWallSnapping;
import dev.anvilcraft.plasticraft.entity.redstone.PlasticObserverContact;
import dev.anvilcraft.plasticraft.event.CatalyticPressAnvilEvents;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.block.item.PipeBlockItem;
import dev.dubhe.anvilcraft.init.ModSoundEvents;
import dev.dubhe.anvilcraft.item.AnvilHammerItem;
import dev.dubhe.anvilcraft.item.MagnetItem;
import dev.dubhe.anvilcraft.item.MultitoolItem;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 可移动塑料制品共用的持久化落方块实现。
 *
 * <p>此处不能使用原版落方块的刻逻辑，因为它最终会变回方块，
 * 而这些制品必须保持为可碰撞、可推动的实体。此刻逻辑保留 AnvilCraft 的重力和加速入口点，
 * 同时明确管理这一不同的生命周期。</p>
 */
public abstract class AbstractPlasticEntity extends FallingBlockEntity
    implements PlasticGravityTypeProvider, CarrierMovableEntity, ShapedCollisionEntity {
    public static final float COLLISION_SIZE = 1.0F;

    private static final double AIR_DRAG = 0.98D;
    private static final double FLUID_VERTICAL_DRAG = 0.82D;
    private static final double FLUID_HORIZONTAL_DRAG = 0.86D;
    private static final EntityDataAccessor<Byte> ORIENTATION = SynchedEntityData.defineId(
        AbstractPlasticEntity.class,
        EntityDataSerializers.BYTE
    );
    private static final EntityDataAccessor<BlockState> DISPLAY_STATE = SynchedEntityData.defineId(
        AbstractPlasticEntity.class,
        EntityDataSerializers.BLOCK_STATE
    );
    private static final EntityDataAccessor<Boolean> MAGNETIZED = SynchedEntityData.defineId(
        AbstractPlasticEntity.class,
        EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Byte> HAMMER_STABLE_ORIENTATION = SynchedEntityData.defineId(
        AbstractPlasticEntity.class,
        EntityDataSerializers.BYTE
    );
    private static final EntityDataAccessor<Byte> HAMMER_RETURN_FROM = SynchedEntityData.defineId(
        AbstractPlasticEntity.class,
        EntityDataSerializers.BYTE
    );
    private static final EntityDataAccessor<Long> HAMMER_RETURN_STARTED = SynchedEntityData.defineId(
        AbstractPlasticEntity.class,
        EntityDataSerializers.LONG
    );
    /** 客户端 Ctrl 中键需要完整物品状态，不能依赖仅用于显示的方块状态。 */
    private static final EntityDataAccessor<ItemStack> PICK_STACK = SynchedEntityData.defineId(
        AbstractPlasticEntity.class,
        EntityDataSerializers.ITEM_STACK
    );
    private static final String TAG_HAMMER_STABLE_ORIENTATION = "HammerStableOrientation";
    private static final String TAG_HAMMER_RETURN_AT = "HammerReturnAt";
    private static final String TAG_HAMMER_RETURN_FROM = "HammerReturnFrom";
    private static final String TAG_HAMMER_RETURN_STARTED = "HammerReturnStarted";
    private static final byte NO_HAMMER_ORIENTATION = -1;
    private static final int HAMMER_DEFLECTION_HOLD_TICKS = 2;
    private static final double HAMMER_ROTATION_COLLISION_EPSILON = 1.0E-6D;
    private static final double CLIENT_HARD_CORRECTION_DISTANCE = 16.0D;
    private static final double CLIENT_MAX_PREDICTION_DISTANCE_SQR = 4.0D;
    private static final double CLIENT_MAX_PREDICTION_TRAVEL = 4.0D;
    private static final long CLIENT_PREDICTION_TIMEOUT_TICKS = 10L;
    public static final int HAMMER_RETURN_ANIMATION_TICKS = 5;

    private ItemStack dropStack = ItemStack.EMPTY;
    private PlasticEntityPhysics.SupportObservation supportObservation;
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
    private UUID lastSidePushCarrierId;
    private long lastSidePushCarrierGameTime = Long.MIN_VALUE;
    private boolean sideEntityPushInProgress;
    private final PlasticSlidingRailPhysics.State slidingRailState = new PlasticSlidingRailPhysics.State();
    private Set<BlockPos> supportedFallingBlocks = Set.of();
    private boolean clientSnapshotPending;
    private int clientSnapshotSteps;
    private float clientSnapshotYRot;
    private float clientSnapshotXRot;
    private boolean clientServerPositionInitialized;
    private double clientServerX;
    private double clientServerY;
    private double clientServerZ;
    private Vec3 clientPendingServerMovement = Vec3.ZERO;
    private final PlasticCarrierPrediction clientCarrierPrediction = new PlasticCarrierPrediction();
    private boolean clientSnapshotHardCorrection;
    private long clientLastPredictionConfirmationGameTime = Long.MIN_VALUE;
    private long clientCarrierMoveGameTime = Long.MIN_VALUE;
    private double clientCarrierMoveStartX;
    private double clientCarrierMoveStartY;
    private double clientCarrierMoveStartZ;
    private long hammerReturnAt = -1L;
    private boolean plasticGeometryReady;
    private PlasticEntityGeometry cachedGeometry;
    private PlasticEntityOrientation cachedGeometryOrientation;
    private PlasticEntityGeometry.Oriented cachedOrientedGeometry;
    private Vec3 cachedCollisionBoxPosition;
    private PlasticEntityCollisionBox cachedCollisionBox;
    private Vec3 cachedInteractionShapePosition;
    private VoxelShape cachedInteractionShape = Shapes.empty();
    private Set<BlockPos> observerContacts = Set.of();
    private boolean observerContactsDirty = true;
    private boolean silentObserverContactHydration;
    private long supportCacheGameTime = Long.MIN_VALUE;
    private long supportCacheBlockEpoch = Long.MIN_VALUE;
    private Vec3 supportCachePosition;
    private byte supportCacheOrientation;
    private final Entity[] cachedFindSupport = new Entity[6];
    private final boolean[] cachedFindSupportReady = new boolean[6];
    private final byte[] cachedHasBlockSupport = new byte[6];
    private Vec3 lastBroadcastDeltaMovement = Vec3.ZERO;
    private boolean lastFluidContact;
    private final PlasticEntityRestState restState = new PlasticEntityRestState();

    protected AbstractPlasticEntity(
        EntityType<? extends AbstractPlasticEntity> entityType,
        Level level
    ) {
        super(entityType, level);
        this.blocksBuilding = true;
        this.setNoGravity(false);
        this.plasticGeometryReady = true;
        this.refreshDimensions();
    }

    protected AbstractPlasticEntity(
        EntityType<? extends AbstractPlasticEntity> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
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
        builder.define(ORIENTATION, (byte) PlasticEntityOrientation.DEFAULT.pack())
            .define(DISPLAY_STATE, Blocks.SAND.defaultBlockState())
            .define(MAGNETIZED, false)
            .define(HAMMER_STABLE_ORIENTATION, NO_HAMMER_ORIENTATION)
            .define(HAMMER_RETURN_FROM, (byte) PlasticEntityOrientation.DEFAULT.pack())
            .define(HAMMER_RETURN_STARTED, -1L)
            .define(PICK_STACK, ItemStack.EMPTY);
    }

    public final BlockState getDisplayState() {
        return this.entityData.get(DISPLAY_STATE);
    }

    /** 保持渲染、AnvilCraft 配方、生成数据包和后续运行时变体变化同步。 */
    /**
     * 撤销 AnvilCraft 对 {@link #blockState} 的传送门改写。
     * 必须在 {@code changeDimension} 保存实体前调用，否则新维度会读到末地尘。
     */
    public final void rejectPortalConversion() {
        if (this.level().isClientSide) return;
        BlockState display = this.getDisplayState();
        if (this.blockState.equals(display)) return;
        this.blockState = display;
        this.blockData = null;
    }

    public final void setDisplayState(BlockState state) {
        BlockState displayState = Objects.requireNonNull(state, "state");
        this.blockState = displayState;
        this.entityData.set(DISPLAY_STATE, displayState);
        if (displayState.hasProperty(AbstractPlasticEntityBlock.MAGNETIZED)) {
            this.entityData.set(
                MAGNETIZED,
                displayState.getValue(AbstractPlasticEntityBlock.MAGNETIZED)
            );
        }
        this.invalidatePlasticGeometry();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DISPLAY_STATE.equals(key)) {
            this.blockState = this.entityData.get(DISPLAY_STATE);
        }
        if (ORIENTATION.equals(key) || DISPLAY_STATE.equals(key)) {
            this.invalidatePlasticGeometry();
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
        this.clientSnapshotYRot = this.getYRot();
        this.clientSnapshotXRot = this.getXRot();
        this.clientSnapshotPending = false;
        this.clientSnapshotSteps = 0;
        this.clientServerPositionInitialized = true;
        this.clientServerX = this.getX();
        this.clientServerY = this.getY();
        this.clientServerZ = this.getZ();
        this.clientPendingServerMovement = Vec3.ZERO;
        this.clientCarrierPrediction.clear();
        this.clientSnapshotHardCorrection = false;
        this.clientLastPredictionConfirmationGameTime = Long.MIN_VALUE;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (!this.level().isClientSide) {
            super.lerpTo(x, y, z, yRot, xRot, steps);
            return;
        }
        Vec3 serverMovement = this.clientServerPositionInitialized
            ? new Vec3(x - this.clientServerX, y - this.clientServerY, z - this.clientServerZ)
            : Vec3.ZERO;
        if (this.clientCarrierPrediction.resetIfDiscontinuous(
            serverMovement,
            CLIENT_HARD_CORRECTION_DISTANCE
        )) {
            this.clientPendingServerMovement = Vec3.ZERO;
            this.clientLastPredictionConfirmationGameTime = Long.MIN_VALUE;
            this.clientSnapshotHardCorrection = true;
        } else {
            // 零位移快照可能只是服务端尚未处理本地推动；交给有序路径和超时逻辑决定是否回正。
            this.clientPendingServerMovement = this.clientPendingServerMovement.add(serverMovement);
        }
        this.clientServerPositionInitialized = true;
        this.clientServerX = x;
        this.clientServerY = y;
        this.clientServerZ = z;
        this.clientSnapshotYRot = yRot;
        this.clientSnapshotXRot = xRot;
        this.clientSnapshotSteps = Math.max(1, steps);
        this.clientSnapshotPending = true;
    }

    @Override
    public double lerpTargetX() {
        return this.clientServerPositionInitialized ? this.clientServerX : this.getX();
    }

    @Override
    public double lerpTargetY() {
        return this.clientServerPositionInitialized ? this.clientServerY : this.getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.clientServerPositionInitialized ? this.clientServerZ : this.getZ();
    }

    @Override
    public float lerpTargetYRot() {
        return this.clientSnapshotPending || this.clientSnapshotSteps > 0
            ? this.clientSnapshotYRot
            : this.getYRot();
    }

    @Override
    public float lerpTargetXRot() {
        return this.clientSnapshotPending || this.clientSnapshotSteps > 0
            ? this.clientSnapshotXRot
            : this.getXRot();
    }

    private boolean clearExpiredClientPrediction() {
        if (this.clientCarrierPrediction.isEmpty()
            || !this.clientServerPositionInitialized
            || this.level().getGameTime() - this.clientLastPredictionConfirmationGameTime
                <= CLIENT_PREDICTION_TIMEOUT_TICKS) {
            return false;
        }
        this.clientCarrierPrediction.clear();
        this.clientLastPredictionConfirmationGameTime = Long.MIN_VALUE;
        return true;
    }

    private boolean shouldHardCorrectClientSnapshot(Vec3 snapshotPosition) {
        return !Double.isFinite(snapshotPosition.x)
            || !Double.isFinite(snapshotPosition.y)
            || !Double.isFinite(snapshotPosition.z)
            || this.position().distanceToSqr(snapshotPosition)
                > CLIENT_HARD_CORRECTION_DISTANCE * CLIENT_HARD_CORRECTION_DISTANCE;
    }

    public final PlasticEntityOrientation getOrientation() {
        return PlasticEntityOrientation.unpack(Byte.toUnsignedInt(this.entityData.get(ORIENTATION)));
    }

    public final void setOrientation(PlasticEntityOrientation orientation) {
        this.entityData.set(ORIENTATION, (byte) Objects.requireNonNull(orientation, "orientation").pack());
        this.invalidatePlasticGeometry();
    }

    public final boolean isHammerDeflected() {
        return this.entityData.get(HAMMER_STABLE_ORIENTATION) != NO_HAMMER_ORIENTATION
            || this.entityData.get(HAMMER_RETURN_STARTED) >= 0L;
    }

    public final HammerRotationAnimation getHammerRotationAnimation(float partialTick) {
        long returnStarted = this.entityData.get(HAMMER_RETURN_STARTED);
        if (returnStarted < 0L) return null;
        float progress = Math.clamp(
            (this.level().getGameTime() + partialTick - returnStarted) / HAMMER_RETURN_ANIMATION_TICKS,
            0.0F,
            1.0F
        );
        return new HammerRotationAnimation(
            PlasticEntityOrientation.unpack(Byte.toUnsignedInt(this.entityData.get(HAMMER_RETURN_FROM))),
            this.getOrientation(),
            progress
        );
    }

    public final boolean startHammerDeflection(PlasticEntityOrientation targetOrientation) {
        PlasticEntityOrientation target = Objects.requireNonNull(targetOrientation, "targetOrientation");
        if (!this.canHammerRotateTo(target)) return false;
        byte pendingStable = this.entityData.get(HAMMER_STABLE_ORIENTATION);
        byte stableOrientation = pendingStable == NO_HAMMER_ORIENTATION
            ? this.entityData.get(ORIENTATION)
            : pendingStable;
        byte packedTarget = target.pack();
        if (packedTarget == stableOrientation) return false;

        this.entityData.set(HAMMER_STABLE_ORIENTATION, stableOrientation);
        this.setOrientation(target);
        this.entityData.set(HAMMER_RETURN_FROM, packedTarget);
        this.entityData.set(HAMMER_RETURN_STARTED, -1L);
        this.hammerReturnAt = this.level().getGameTime() + HAMMER_DEFLECTION_HOLD_TICKS;
        this.hasImpulse = true;
        this.hurtMarked = true;
        return true;
    }

    public final boolean isMagnetized() {
        return this.entityData.get(MAGNETIZED);
    }

    public final int getDisplayTint() {
        if (!this.supportsDyeing()) return 0xFFFFFF;
        return DyeableMaterial.tint(this.getDisplayState());
    }

    public final void setMagnetized(boolean magnetized) {
        this.entityData.set(MAGNETIZED, magnetized);
        BlockState state = this.getDisplayState();
        if (state.hasProperty(AbstractPlasticEntityBlock.MAGNETIZED)
            && state.getValue(AbstractPlasticEntityBlock.MAGNETIZED) != magnetized) {
            this.blockState = state.setValue(AbstractPlasticEntityBlock.MAGNETIZED, magnetized);
            this.entityData.set(DISPLAY_STATE, this.blockState);
        }
    }

    @Override
    public final boolean anvilcraft$isMagnetized() {
        return this.isMagnetized();
    }

    @Override
    public final boolean anvilcraft$canCollisionCraft() {
        // 水平碰撞合成属于铁砧行为；塑料实体改用下方带方向的落地钩子。
        return false;
    }

    public final ItemStack getDropStack() {
        ItemStack stack = this.dropStack.isEmpty() ? this.createDefaultDropStack() : this.dropStack;
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        PlasticItemData.setMaterial(copy, this.materialKey());
        if (!this.supportsDyeing()) PlasticItemData.clearColor(copy);
        PlasticItemData.setMagnetized(copy, this.isMagnetized());
        return this.prepareDropStack(copy);
    }

    public final void setDropStack(ItemStack stack) {
        this.dropStack = stack == null ? ItemStack.EMPTY : stack.copy();
        if (this.dropStack.getCount() > 1) {
            this.dropStack.setCount(1);
        }
        if (!this.dropStack.isEmpty()) {
            PlasticItemData.setMaterial(this.dropStack, this.materialKey());
            if (!this.supportsDyeing()) PlasticItemData.clearColor(this.dropStack);
        }
        this.entityData.set(PICK_STACK, this.dropStack.copy());
        this.onDropStackChanged(this.dropStack.copy());
    }

    /** 子类可在回收前把当前实体状态写回物品组件。 */
    protected ItemStack prepareDropStack(ItemStack stack) {
        return stack;
    }

    /** 子类可把物品携带的动态数据同步给客户端，但不得依赖渲染状态。 */
    protected void onDropStackChanged(ItemStack stack) {
    }

    protected abstract ItemStack createDefaultDropStack();

    /** 用于迁移仅含旧颜色数据物品堆的材料标识。 */
    protected String materialKey() {
        return "hardened_resin";
    }

    /** 固定颜色树脂制品有意不提供调色板能力。 */
    protected boolean supportsDyeing() {
        return DyeableMaterial.supportsDyeing(this.materialKey());
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
        // 塑料实体半浸没时达到中性浮力，使质心停留在流体表面，
        // 而不会在数刻后被弹出水面。
        return Math.abs(gravity.y) * 2.0D * contact.submergedFraction();
    }

    /** 渲染器读取当前实体的有效重力，不把液面逻辑写入物理或渲染状态。 */
    public final Vec3 plasticraft$getEffectiveGravityVector() {
        if (this.isNoGravity() || AccelerateManager.isControlledByRing(this)) return Vec3.ZERO;
        Vec3 gravity = GravityManager.getNetGravityVectorForFallingBlock(this);
        if (!this.isBuoyantInFluids()) return gravity;
        PlasticFluidPhysics.FluidContact contact = PlasticFluidPhysics.sample(this);
        return gravity.add(0.0D, this.getFluidBuoyancyAcceleration(contact, gravity), 0.0D);
    }

    /**
     * AnvilCraft 的落地配方约定以世界向下为方向：使用方会检查
     * {@code event.pos.below()}。其他冲击方向仍是有效的物理接触，
     * 但若作为该事件发布就会指向错误的方块。
     */
    protected boolean triggersAnvilCraftLandingEvents(Direction impactDirection) {
        // AnvilCraft 的公开落地上下文以世界向下为基准，配方会使用 "below" 偏移，
        // 因此必须同时满足该方向和实际底面条件。
        return impactDirection == Direction.DOWN
            && impactDirection == this.getOrientation().attachmentFace().getOpposite();
    }

    /** 后续塑料类型可用自身的损坏状态或耐久模型替代销毁行为。 */
    protected void handleAnvilCraftLandingDamage(BlockPos centerPos) {
        this.applyAnvilCraftRecipeDamage(centerPos);
    }

    /** 功能类型可在逐格落砧事件之前执行只按整次落地生效的流程。 */
    protected void handleLandingOnce(BlockPos centerPos, float fallDistance) {
    }

    /** 功能类型可在普通落砧配方发布前执行并消费单格落地流程。 */
    protected boolean handleAdditionalAnvilCraftLanding(AnvilEvent.OnLand event) {
        return false;
    }

    /** 不伪造落地事件，直接应用 DamageAnvil 配方结果。 */
    public void applyAnvilCraftRecipeDamage(BlockPos pos) {
        if (!this.isSilent()) {
            this.level().levelEvent(1029, pos, 0);
        }
        this.discard();
    }

    /** 后续变体可自定义加速来源，同时保留感知碰撞的位置修正。 */
    protected void applyAnvilCraftAcceleration() {
        AccelerateManager.handleAcceleration(this);
    }

    @Override
    public void setPos(double x, double y, double z) {
        Vec3 previousPosition = this.position();
        if (!this.accelerationPositionCapture || this.accelerationMoveInProgress) {
            super.setPos(x, y, z);
            this.markObserverContactsDirty(previousPosition);
            return;
        }

        Vec3 requestedMovement = new Vec3(x - this.getX(), y - this.getY(), z - this.getZ());
        if (!Double.isFinite(requestedMovement.lengthSqr())
            || requestedMovement.lengthSqr()
                <= PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
            super.setPos(x, y, z);
            this.markObserverContactsDirty(previousPosition);
            return;
        }

        Vec3 startPosition = this.position();
        this.accelerationRequestedMovement = this.accelerationRequestedMovement.add(requestedMovement);
        this.accelerationMoveInProgress = true;
        try {
            // AccelerateManager 使用 setPos 将高速砧重新居中。
            // 仅通过 Entity.move 重放这次局部修正，使普通传送仍保持原有语义。
            this.move(MoverType.SELF, requestedMovement);
        } finally {
            this.accelerationActualMovement = this.accelerationActualMovement.add(
                this.position().subtract(startPosition)
            );
            this.accelerationMoveInProgress = false;
        }
        this.markObserverContactsDirty(previousPosition);
    }

    private void markObserverContactsDirty(Vec3 previousPosition) {
        if (this.position().equals(previousPosition)) return;
        this.observerContactsDirty = true;
        // 自身移动会改变周围实体的支撑与侧向接触关系。索引登记时已外扩一格，
        // 因此用移动后的包围盒直接查询即可命中所有一格内的休眠实体（含自身）。
        if (!this.level().isClientSide) {
            PlasticRestIndex.wakeInBounds(this.level(), this.getBoundingBox());
        }
    }

    /**
     * 支撑查询在单次 {@code tick} 内会被重复问到五六次，这里按刻记忆化。
     *
     * <p>缓存键取 {@code (gameTime, 方块纪元, position() 引用, 朝向)}：{@code Entity#setPosRaw} 只在坐标真正
     * 变化时才新建 {@link Vec3}，因此引用比较就是一次零成本的「本实体是否移动过」判定。实体逐个 tick，
     * 本实体不动时其它实体也不可能被本实体的推挤链移动，所以引用未变即代表可见的实体布局未变；
     * 方块纪元额外覆盖刻内的方块写入。几何体（含显示状态）变化经 {@link #invalidatePlasticGeometry()} 失效。</p>
     */
    private void ensureSupportCacheValid() {
        long gameTime = this.level().getGameTime();
        long blockEpoch = PlasticBlockEpoch.current();
        Vec3 position = this.position();
        byte orientation = (byte) this.getOrientation().pack();
        if (this.supportCacheGameTime == gameTime
            && this.supportCacheBlockEpoch == blockEpoch
            && this.supportCachePosition == position
            && this.supportCacheOrientation == orientation) {
            return;
        }
        this.supportCacheGameTime = gameTime;
        this.supportCacheBlockEpoch = blockEpoch;
        this.supportCachePosition = position;
        this.supportCacheOrientation = orientation;
        Arrays.fill(this.cachedFindSupport, null);
        Arrays.fill(this.cachedFindSupportReady, false);
        Arrays.fill(this.cachedHasBlockSupport, (byte) 0);
    }

    /**
     * {@code null} 是合法结果，因此用单独的就绪标记区分「未算过」和「算过且没有支撑」。
     *
     * <p>撞击回调（锅体配方、铁砧砸伤）可能在同一刻内移除支撑实体，而本实体位置并未改变。
     * {@code findSupport} 本身会过滤已移除实体，这里对缓存值补一次同样的判定以保持行为一致。</p>
     */
    private Entity cachedFindSupport(Direction gravityDirection) {
        this.ensureSupportCacheValid();
        int index = gravityDirection.ordinal();
        Entity cached = this.cachedFindSupport[index];
        if (!this.cachedFindSupportReady[index] || (cached != null && cached.isRemoved())) {
            cached = PlasticEntityPhysics.findSupport(this, gravityDirection);
            this.cachedFindSupport[index] = cached;
            this.cachedFindSupportReady[index] = true;
        }
        return cached;
    }

    /** 0 未算过、1 无支撑、2 有支撑。 */
    private boolean cachedHasBlockSupport(Direction gravityDirection) {
        this.ensureSupportCacheValid();
        int index = gravityDirection.ordinal();
        byte cached = this.cachedHasBlockSupport[index];
        if (cached != 0) return cached == 2;
        boolean supported = PlasticEntityPhysics.hasBlockSupport(this, gravityDirection);
        this.cachedHasBlockSupport[index] = (byte) (supported ? 2 : 1);
        return supported;
    }

    @Override
    public void tick() {
        PlasticPistonOccupancy.MotionTarget pistonTarget = PlasticPistonOccupancy.movementTarget(this);
        if (pistonTarget != null) {
            // 活塞分支在休眠检查之前返回，若不在此唤醒，被推动的休眠实体会留下过期的索引登记。
            this.plasticraft$wakeFromRest();
            this.tickPistonMovement(pistonTarget);
            this.refreshObserverContacts();
            return;
        }
        if (this.level().isClientSide) {
            // ClientLevel 会在每个实体执行刻逻辑前记录旧坐标。本地玩家可能在同一轮实体处理中更早推动该实体，
            // 因此保留推动前位置，让原版局部刻渲染器绘制这段位移，而不是将其隐藏。
            if (this.clientCarrierMoveGameTime == this.level().getGameTime()) {
                this.xOld = this.xo = this.clientCarrierMoveStartX;
                this.yOld = this.yo = this.clientCarrierMoveStartY;
                this.zOld = this.zo = this.clientCarrierMoveStartZ;
            }
            boolean receivedSnapshot = this.clientSnapshotPending;
            if (receivedSnapshot) {
                if (this.clientCarrierPrediction.reconcile(this.clientPendingServerMovement)) {
                    this.clientLastPredictionConfirmationGameTime = this.level().getGameTime();
                }
                this.clientPendingServerMovement = Vec3.ZERO;
                this.clientSnapshotPending = false;
            }
            boolean predictionCleared = this.clearExpiredClientPrediction();
            if (predictionCleared && this.clientSnapshotSteps == 0) {
                this.clientSnapshotSteps = 1;
            }
            // 服务端确认量与本刻本地推动在同一处归并，避免包处理顺序让链尾实体重复前进。
            if (this.clientServerPositionInitialized
                && (receivedSnapshot || predictionCleared || this.clientSnapshotSteps > 0)) {
                Vec3 snapshotPosition = new Vec3(
                    this.clientServerX,
                    this.clientServerY,
                    this.clientServerZ
                ).add(this.clientCarrierPrediction.pendingMovement());
                if (this.clientSnapshotHardCorrection || this.shouldHardCorrectClientSnapshot(snapshotPosition)) {
                    this.setPos(snapshotPosition);
                    this.setRot(this.clientSnapshotYRot, this.clientSnapshotXRot);
                    this.clientSnapshotSteps = 0;
                    this.xOld = this.xo = this.getX();
                    this.yOld = this.yo = this.getY();
                    this.zOld = this.zo = this.getZ();
                } else {
                    int interpolationSteps = Math.max(1, this.clientSnapshotSteps);
                    this.setPos(this.position().lerp(snapshotPosition, 1.0D / interpolationSteps));
                    this.setRot(
                        this.getYRot()
                            + Mth.wrapDegrees(this.clientSnapshotYRot - this.getYRot()) / interpolationSteps,
                        this.getXRot()
                            + Mth.wrapDegrees(this.clientSnapshotXRot - this.getXRot()) / interpolationSteps
                    );
                    this.clientSnapshotSteps = interpolationSteps - 1;
                }
                this.clientSnapshotHardCorrection = false;
            }
            // 仅重建本地玩家碰撞预测所需的支撑关系，实体运动仍由服务端快照决定。
            this.refreshClientSupportObservation();
            this.firstTick = false;
            return;
        }

        if (this.restState.isResting() && !this.tickWhileResting()) return;

        this.supportedFallingBlocks = PlasticFallingBlockSupport.updateSupportChecks(
            this,
            this.supportedFallingBlocks
        );
        // FallingBlockEntity.tick 有意跳过 Entity#baseTick。
        // 此处保留通常由 baseTick 推进的插值和边沿触发状态。
        CatalyticPressAnvilEvents.beforeFallingAnvilTick(this);
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
        this.previousImpactContactMask = this.impactContactMask;
        this.impactContactMask = 0;
        this.tickHammerDeflection();
        // AnvilCraft 会把下落方块的 blockState 改成末地尘等传送门产物。
        // 塑料实体必须保持自身展示方块并正常传送，因此这里写回被改掉的字段。
        this.rejectPortalConversion();

        // FallingBlockEntity 使用精简的生命周期，不调用 Entity#baseTick。
        // 浮力依赖 NeoForge 的流体映射，因此需要显式刷新流体占用情况。
        this.updateInWaterStateAndDoFluidPushing();
        // 首刻之后仍保持持久化实体的流体和熔岩生命周期一致；
        // 原版 FallingBlockEntity 通常会在此差异产生影响前消失。
        this.firstTick = false;

        if (this.time < Integer.MAX_VALUE) {
            this.time++;
        }

        Vec3 velocityBeforeAcceleration = this.getDeltaMovement();
        Vec3 positionBeforeAcceleration = this.position();
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
        boolean onSlidingRail = this.slidingRailState.tick(this);
        boolean controlledByRing = AccelerateManager.isControlledByRing(this);
        boolean accelerationChangedMovement = !this.getDeltaMovement().equals(velocityBeforeAcceleration)
            || accelerationActualMovement.lengthSqr()
                > PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON;
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
            : PlasticEntityPhysics.directionOrNull(effectiveGravity);
        this.updateEffectiveGravityDirection(gravityDirection);
        if (accelerationRequestedMovement.lengthSqr()
            > PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
            boolean hadBlockSupportBeforeAcceleration = gravityDirection != null
                && PlasticEntityPhysics.hasBlockSupport(this, boxBeforeAcceleration, gravityDirection);
            Entity accelerationSupport = gravityDirection == null
                ? null
                : PlasticEntityPhysics.findSupport(this, boxBeforeAcceleration, gravityDirection);
            Entity accelerationPostSupport = gravityDirection == null
                ? null
                : this.cachedFindSupport(gravityDirection);
            this.handleEntityImpact(
                gravityDirection,
                accelerationPostSupport,
                accelerationRequestedMovement,
                accelerationActualMovement,
                hadBlockSupportBeforeAcceleration,
                accelerationSupport != null
                && PlasticEntityPhysics.hasImmediateEntityContact(
                        this,
                        boxBeforeAcceleration,
                        accelerationSupport,
                        gravityDirection
                    )
            );
            this.setDeltaMovement(this.resolveCollisionVelocity(
                accelerationRequestedMovement,
                accelerationActualMovement,
                this.getDeltaMovement(),
                gravityDirection
            ));
            this.handleNewCollisionContacts(accelerationRequestedMovement, accelerationActualMovement);
            PlasticEntityGridSnapping.applyAfterMove(
                this,
                accelerationRequestedMovement,
                accelerationActualMovement
            );
            this.updateLandingState(
                gravityDirection,
                accelerationRequestedMovement,
                accelerationActualMovement,
                accelerationPostSupport != null
                    && PlasticEntityPhysics.hasImmediateEntityContact(
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
        this.handleSweptCauldronImpact(positionBeforeAcceleration);
        if (this.isRemoved()) return;
        Entity support = gravityDirection == null
            ? null
            : this.cachedFindSupport(gravityDirection);
        PlasticEntityPhysics.SupportObservation preMoveObservation = support == null
            ? null
            : PlasticEntityPhysics.SupportObservation.capture(support);

        Vec3 observedCarrierMovement = preMoveObservation == null
            ? Vec3.ZERO
            : PlasticEntityPhysics.carriedMovement(
                this.supportObservation,
                preMoveObservation,
                gravityDirection
        );
        this.moveWithCarrierDisplacement(support, observedCarrierMovement);
        if (this.isRemoved()) return;

        support = gravityDirection == null
            ? null
            : this.cachedFindSupport(gravityDirection);
        boolean hadBlockSupportBeforeMove = gravityDirection != null
            && this.cachedHasBlockSupport(gravityDirection);
        boolean hadEntitySupportBeforeMove = support != null
            && PlasticEntityPhysics.hasImmediateEntityContact(this, support, gravityDirection);

        Vec3 requestedMovement = this.getDeltaMovement();
        Vec3 positionBeforeMove = this.position();
        this.move(MoverType.SELF, requestedMovement);
        if (this.isRemoved()) return;
        this.handlePortal();
        if (this.isRemoved()) return;
        Vec3 actualMovement = this.position().subtract(positionBeforeMove);
        PlasticEntityGridSnapping.applyAfterMove(this, requestedMovement, actualMovement);
        // Entity.move 和 AnvilCraft 的 EntityMixin 负责碰撞轴清零、扫掠重力速度冲量、
        // 天体裁剪和圆环偏转。读取移动后的速度可保留这四种处理结果。
        Vec3 velocityAfterMove = PlasticEntityPhysics.removeClippedVelocity(
            this.getDeltaMovement(),
            requestedMovement,
            actualMovement
        );
        velocityAfterMove = this.resolveCollisionVelocity(
            requestedMovement,
            actualMovement,
            velocityAfterMove,
            gravityDirection
        );
        Entity entitySupportAfterMove = gravityDirection == null
            ? null
            : this.cachedFindSupport(gravityDirection);
        boolean entitySupportCollision = gravityDirection != null
            && entitySupportAfterMove != null
            && PlasticEntityPhysics.collidedAlongGravity(requestedMovement, actualMovement, gravityDirection)
            && !this.cachedHasBlockSupport(gravityDirection);
        if (entitySupportCollision) {
            velocityAfterMove = PlasticEntityPhysics.removeIntoSupportVelocity(velocityAfterMove, gravityDirection);
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
        this.handleSweptCauldronImpact(positionBeforeMove);
        if (this.isRemoved()) return;
        PlasticFluidPhysics.FluidContact postMoveFluid = PlasticFluidPhysics.sample(this);
        boolean onSlidingRailAfterMove = this.slidingRailState.isOnSlidingRail(this);
        double horizontalDrag = postMoveFluid.isPresent()
            ? FLUID_HORIZONTAL_DRAG
            : onSlidingRail || onSlidingRailAfterMove ? 1.0D : AIR_DRAG;
        double verticalDrag = postMoveFluid.isPresent() ? FLUID_VERTICAL_DRAG : AIR_DRAG;
        this.setDeltaMovement(new Vec3(
            velocityAfterMove.x * horizontalDrag,
            velocityAfterMove.y * verticalDrag,
            velocityAfterMove.z * horizontalDrag
        ));
        if (!onSlidingRailAfterMove) {
            this.applySurfaceFriction(gravityDirection);
        }

        Direction postMoveGravityDirection = this.isNoGravity() || AccelerateManager.isControlledByRing(this)
            ? null
            : PlasticEntityPhysics.directionOrNull(
                GravityManager.getNetGravityVectorForFallingBlock(this)
                    .add(0.0D, this.getFluidBuoyancyAcceleration(postMoveFluid,
                        GravityManager.getNetGravityVectorForFallingBlock(this)), 0.0D)
            );
        if (postMoveGravityDirection != gravityDirection) {
            gravityDirection = postMoveGravityDirection;
            this.updateEffectiveGravityDirection(gravityDirection);
            // 移动期间被忽略的方向不可能存在已追踪的支撑边沿。
            hadBlockSupportBeforeMove = false;
        }
        Entity postMoveSupport = gravityDirection == null
            ? null
            : this.cachedFindSupport(gravityDirection);
        this.updateLandingState(
            gravityDirection,
            requestedMovement,
            actualMovement,
            postMoveSupport != null
                && PlasticEntityPhysics.hasImmediateEntityContact(this, postMoveSupport, gravityDirection),
            hadBlockSupportBeforeMove
        );
        if (this.isRemoved()) {
            return;
        }
        this.supportObservation = postMoveSupport == null
            ? null
            : PlasticEntityPhysics.SupportObservation.capture(postMoveSupport);
        this.supportDirection = postMoveSupport == null ? null : gravityDirection;

        if (accelerationChangedMovement) {
            this.hasImpulse = true;
            this.hurtMarked = true;
        }

        Direction pushGravityDirection = gravityDirection == null ? Direction.DOWN : gravityDirection;
        if (PlasticEntityPhysics.tangentialMovement(requestedMovement, pushGravityDirection).lengthSqr()
            > PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
            this.sideEntityPushInProgress = true;
            try {
                PlasticEntityPhysics.pushSideEntities(
                    this,
                    gravityDirection,
                    this.supportObservation == null ? null : this.supportObservation.entityId()
                );
            } finally {
                this.sideEntityPushInProgress = false;
            }
        }
        // 原版 ServerEntity 对 hurtMarked 无条件每刻广播一次运动包。重力每刻给 y 加 -0.04 又被
        // move() 裁回 0，令 actualMovement 与 requestedMovement 对任何静止实体永远不等，若照此
        // 置位会持续每刻广播。改为仅当广播用速度实质变化、或流体接触状态翻转时才广播。
        boolean fluidContactChanged = postMoveFluid.isPresent() != this.lastFluidContact;
        this.lastFluidContact = postMoveFluid.isPresent();
        Vec3 broadcastMovement = this.getDeltaMovement();
        if (fluidContactChanged
            || broadcastMovement.distanceToSqr(this.lastBroadcastDeltaMovement)
                > PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
            this.lastBroadcastDeltaMovement = broadcastMovement;
            this.hasImpulse = true;
            this.hurtMarked = true;
        }
        this.refreshObserverContacts();
        this.checkBelowWorld();
        this.evaluateRestEntry(
            gravityDirection,
            postMoveFluid,
            onSlidingRail || onSlidingRailAfterMove,
            accelerationRequestedMovement
        );
    }

    /**
     * 休眠期间跳过整条支撑、移动与碰撞流水线，只推进插值与边沿状态记账。
     *
     * <p>返回 {@code true} 表示错峰复核判定必须唤醒，本刻继续按完整流水线执行。</p>
     */
    private boolean tickWhileResting() {
        if (this.restState.shouldAudit(this.level().getGameTime(), this.getId())
            && this.restState.auditRequiresWake(this)) {
            this.plasticraft$wakeFromRest();
            return true;
        }
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
        this.previousImpactContactMask = this.impactContactMask;
        this.impactContactMask = 0;
        if (this.time < Integer.MAX_VALUE) {
            this.time++;
        }
        return false;
    }

    /**
     * 只有「完全静止 + 纯方块支撑 + 无任何外力来源」的实体才允许休眠。
     *
     * <p>实体支撑链（{@link #supportObservation} 非空）与胶合组件保持清醒，因为 {@code carriedMovement}
     * 和组件领导者位移都依赖逐刻观察，休眠会破坏其语义。加速请求需连续为零若干刻，避免加速环边界
     * 抖动时反复进出休眠。</p>
     */
    private void evaluateRestEntry(
        Direction gravityDirection,
        PlasticFluidPhysics.FluidContact fluidContact,
        boolean onSlidingRail,
        Vec3 accelerationRequestedMovement
    ) {
        boolean calm = accelerationRequestedMovement.lengthSqr()
            <= PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON;
        if (!this.restState.updateCalmStreak(calm)
            || this.isRemoved()
            || gravityDirection == null
            || this.supportObservation != null
            || this.observerContactsDirty
            || fluidContact.isPresent()
            || onSlidingRail
            || this.isHammerDeflected()
            || this.hammerReturnAt >= 0L
            || EntityBondManager.hasBonds(this)
            || PlasticPistonOccupancy.isMoving(this)) {
            return;
        }
        Vec3 velocity = this.getDeltaMovement();
        if (Math.abs(velocity.x) >= PlasticEntityPhysics.FACE_EPSILON
            || Math.abs(velocity.y) >= PlasticEntityPhysics.FACE_EPSILON
            || Math.abs(velocity.z) >= PlasticEntityPhysics.FACE_EPSILON
            || !this.cachedHasBlockSupport(gravityDirection)) {
            return;
        }
        // 休眠期间速度不再被重力与阻力刷新，这里归零以免残留量在唤醒后被当成真实冲量。
        this.setDeltaMovement(Vec3.ZERO);
        this.restState.enter(this, gravityDirection);
    }

    /** 休眠中的实体跳过物理流水线，托盘光源等只依赖位置与朝向的派生状态可以一并省掉刷新。 */
    public final boolean plasticraft$isResting() {
        return this.restState.isResting();
    }

    /**
     * 退出休眠并恢复到「与从未休眠完全一致」的起点：清空支撑观察、强制重算观察者接触、
     * 作废按刻记忆化的支撑缓存（唤醒原因往往正是世界发生了变化）。
     */
    public final void plasticraft$wakeFromRest() {
        if (!this.restState.isResting()) return;
        this.restState.exit(this);
        this.supportObservation = null;
        this.supportDirection = null;
        this.observerContactsDirty = true;
        this.supportCacheGameTime = Long.MIN_VALUE;
    }

    private void refreshObserverContacts() {
        if (this.level().isClientSide || this.isRemoved() || !this.observerContactsDirty) return;
        PlasticObserverContact.ScanResult scan = PlasticObserverContact.scan(this);
        if (this.silentObserverContactHydration) {
            this.observerContacts = scan.contacts();
            if (!scan.complete()) return;
            this.silentObserverContactHydration = false;
            this.observerContactsDirty = false;
            return;
        }
        if (!scan.complete()) return;
        this.observerContacts = PlasticObserverContact.emitTransitions(
            this.level(),
            this.observerContacts,
            scan.contacts()
        );
        this.observerContactsDirty = false;
    }

    private void refreshClientSupportObservation() {
        Direction gravityDirection = null;
        if (EntityBondManager.isFollower(this)) {
            gravityDirection = this.currentPushGravityDirection();
        } else if (!this.isNoGravity() && !AccelerateManager.isControlledByRing(this)) {
            Vec3 gravity = GravityManager.getNetGravityVectorForFallingBlock(this);
            PlasticFluidPhysics.FluidContact fluidContact = PlasticFluidPhysics.sample(this);
            double buoyancy = this.isBuoyantInFluids()
                ? this.getFluidBuoyancyAcceleration(fluidContact, gravity)
                : 0.0D;
            gravityDirection = PlasticEntityPhysics.directionOrNull(gravity.add(0.0D, buoyancy, 0.0D));
        }
        Entity support = gravityDirection == null
            ? null
            : this.cachedFindSupport(gravityDirection);
        this.supportObservation = support == null
            ? null
            : PlasticEntityPhysics.SupportObservation.capture(support);
        this.supportDirection = support == null ? null : gravityDirection;
    }

    private void applySurfaceFriction(Direction gravityDirection) {
        if (gravityDirection == null) return;
        AbstractPlasticEntity frictionMember = this.findSurfaceFrictionMember(gravityDirection);
        if (frictionMember == null) return;
        BlockPos supportPos = PlasticEntityPhysics.landingPosition(
            frictionMember,
            gravityDirection
        ).relative(gravityDirection);
        BlockState supportState = this.level().getBlockState(supportPos);
        float friction = supportState.getFriction(this.level(), supportPos, frictionMember);
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

    private AbstractPlasticEntity findSurfaceFrictionMember(Direction gravityDirection) {
        if (this.cachedHasBlockSupport(gravityDirection)) return this;
        if (!EntityBondManager.hasBonds(this) || EntityBondManager.isFollower(this)) return null;
        for (Entity member : EntityBondManager.component(this.level(), this)) {
            if (!(member instanceof AbstractPlasticEntity plastic) || plastic == this) continue;
            EntityBondState bonds = EntityBondManager.get(plastic);
            if (bonds == null || !bonds.leaderUuid().equals(this.getUUID())) continue;
            Direction memberGravityDirection = plastic.plasticraft$currentPushGravityDirection();
            if (memberGravityDirection != gravityDirection) continue;
            Vec3 expectedPosition = this.position().add(bonds.offsetFromLeader());
            AABB expectedBounds = plastic.getBoundingBox().move(expectedPosition.subtract(plastic.position()));
            if (PlasticEntityPhysics.hasBlockSupport(plastic, expectedBounds, gravityDirection)) return plastic;
        }
        return null;
    }

    protected final boolean isOnSlidingRail() {
        return this.slidingRailState.isOnSlidingRail(this);
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

        int directionMask = PlasticEntityPhysics.directionMask(gravityDirection);
        boolean blockContact = this.cachedHasBlockSupport(gravityDirection);
        // 支撑探针有厚度，落体可能先落进探针深度、下一刻才真正被裁剪。若只认裁剪，接触那一刻会抢先
        // 占掉落地边沿，随后真正停下的一刻反被当成本来就站在地面上，整次落地一个事件都发不出来。
        boolean gainedBlockContact = blockContact && !hadBlockSupportBeforeMove;
        boolean newImpact = blockContact
            && (this.blockContactMask & directionMask) == 0
            && this.impactTrackingArmed
            && (gainedBlockContact
                || PlasticEntityPhysics.collidedAlongGravity(requestedMovement, actualMovement, gravityDirection))
            && this.directionalFallDistance > PlasticEntityPhysics.FACE_EPSILON;
        if (newImpact) {
            this.playImpactSound(gravityDirection, requestedMovement.length());
            this.postLandingEvent(gravityDirection);
            this.directionalFallDistance = 0.0F;
            this.impactTrackingArmed = false;
        } else if (hasEntitySupport) {
            this.directionalFallDistance = 0.0F;
            this.impactTrackingArmed = false;
        } else if (!blockContact && fallStep < -PlasticEntityPhysics.FACE_EPSILON) {
            this.directionalFallDistance = 0.0F;
            this.impactTrackingArmed = false;
        }
        this.blockContactMask = blockContact ? directionMask : 0;
    }

    /** 在通用落地状态清除下落边沿前处理新遇到的实体支撑。 */
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
            || this.cachedHasBlockSupport(gravityDirection)
            || !PlasticEntityPhysics.collidedAlongGravity(requestedMovement, actualMovement, gravityDirection)) {
            return;
        }

        Vec3 normal = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        double fallStep = actualMovement.dot(normal);
        float impactDistance = this.directionalFallDistance + (float) Math.max(0.0D, fallStep);
        if (impactDistance <= PlasticEntityPhysics.FACE_EPSILON
            || (!this.impactTrackingArmed && fallStep <= PlasticEntityPhysics.FACE_EPSILON)) {
            return;
        }

        this.directionalFallDistance = 0.0F;
        this.impactTrackingArmed = false;
    }

    /** 有效重力面首次与另一实体碰撞时调用一次。 */
    protected void onEntityImpact(Entity support, Direction impactDirection, float fallDistance) {
        PlasticCauldron pot = PlasticCauldrons.of(support);
        if (pot != null) {
            pot.processAnvilImpact(this, impactDirection);
        }
    }

    private void tickPistonMovement(PlasticPistonOccupancy.MotionTarget target) {
        Vec3 targetPosition = target.position();
        Vec3 start = this.position();
        AABB startBox = this.getBoundingBox();
        Vec3 movement = targetPosition.subtract(start);
        this.xOld = this.xo = start.x;
        this.yOld = this.yo = start.y;
        this.zOld = this.zo = start.z;
        this.setPos(targetPosition);
        PlasticEntityGridSnapping.applyAtRest(this);
        if (movement.lengthSqr() > PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
            this.displaceEntitiesFromPistonOccupancy(startBox, movement);
        }
        this.setDeltaMovement(target.velocity());
        this.supportObservation = null;
        this.supportDirection = null;
        this.blockContactMask = 0;
        this.previousImpactContactMask = 0;
        this.impactContactMask = 0;
        this.hasImpulse = true;
        this.hurtMarked = true;
        this.firstTick = false;
        if (this.time < Integer.MAX_VALUE) this.time++;
    }

    /** 占位跟随用 setPos 传送，站在顶上或被叠进碰撞里的实体不会走 Entity.move，需要按同一位移推开。 */
    private void displaceEntitiesFromPistonOccupancy(AABB startBox, Vec3 movement) {
        AABB query = startBox.minmax(this.getBoundingBox()).inflate(PlasticEntityPhysics.SUPPORT_PROBE_DEPTH);
        for (Entity entity : this.level().getEntities(this, query, this::canDisplaceFromPistonOccupancy)) {
            AABB box = entity.getBoundingBox();
            boolean standingOn = box.minY >= startBox.maxY - PlasticEntityPhysics.SUPPORT_PROBE_DEPTH
                && box.minY <= startBox.maxY + PlasticEntityPhysics.SUPPORT_PROBE_DEPTH
                && box.intersects(startBox.inflate(PlasticEntityPhysics.SUPPORT_PROBE_DEPTH));
            if (standingOn || box.intersects(this.getBoundingBox())) {
                entity.move(MoverType.PISTON, movement);
            }
        }
    }

    private boolean canDisplaceFromPistonOccupancy(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator() || entity.isPassenger()) return false;
        if (entity.getPistonPushReaction() == PushReaction.IGNORE) return false;
        if (entity instanceof AbstractPlasticEntity plastic && PlasticPistonOccupancy.isMoving(plastic)) {
            return false;
        }
        return EntitySelector.NO_SPECTATORS.test(entity);
    }

    private void handleNewCollisionContacts(Vec3 requested, Vec3 actual) {
        int contacts = PlasticEntityPhysics.clippedDirectionMask(requested, actual, 0.04D);
        int newContacts = contacts & ~this.previousImpactContactMask & ~this.impactContactMask;
        this.impactContactMask |= contacts;
        if (newContacts == 0) return;
        for (Direction direction : Direction.values()) {
            if ((newContacts & PlasticEntityPhysics.directionMask(direction)) == 0) continue;
            this.playImpactSound(direction, Math.abs(requested.get(direction.getAxis())));
            if (this.cachedHasBlockSupport(direction)) {
                this.onBondedBlockImpact(direction);
                continue;
            }
            Entity contact = this.cachedFindSupport(direction);
            if (contact != null) {
                this.onEntityImpact(contact, direction, (float) Math.abs(requested.get(direction.getAxis())));
            }
        }
    }

    /** 真实凹形碰撞允许砧底进入锅口，因此配方碰撞单独按两个功能平面的穿越判断。 */
    private void handleSweptCauldronImpact(Vec3 startPosition) {
        if (this.level().isClientSide || startPosition.equals(this.position())) return;
        Vec3 movement = this.position().subtract(startPosition);
        AABB sweptBounds = this.getBoundingBox()
            .move(-movement.x, -movement.y, -movement.z)
            .expandTowards(movement)
            .inflate(PlasticEntityPhysics.FACE_EPSILON);
        PlasticCauldron self = PlasticCauldrons.of(this);
        if (self != null) {
            for (AbstractPlasticEntity anvil : this.level().getEntitiesOfClass(
                AbstractPlasticEntity.class,
                sweptBounds,
                candidate -> candidate != this && candidate.isAlive()
            )) {
                self.processSweptAnvilImpact(
                    anvil,
                    anvil.position(),
                    anvil.position(),
                    startPosition,
                    this.position()
                );
            }
            return;
        }
        for (PlasticCauldron pot : PlasticCauldrons.findIn(this.level(), sweptBounds)) {
            pot.processSweptAnvilImpact(
                this,
                startPosition,
                this.position(),
                pot.position(),
                pot.position()
            );
        }
    }

    private void onBondedBlockImpact(Direction impactDirection) {
        BlockPos contactPos = PlasticEntityPhysics.landingPosition(this, impactDirection);
        if (this.level().getBlockEntity(contactPos) instanceof BondedEntityBlockEntity bonded
            && bonded.isInitialized()) {
            bonded.processAnvilImpact(this, impactDirection);
        }
    }

    protected void playImpactSound(Direction direction, double speed) {
        if (this.level().isClientSide || this.isSilent()) return;
        if (this.impactSoundTick != this.tickCount) {
            this.impactSoundTick = this.tickCount;
            this.impactSoundMask = 0;
        }
        int directionMask = PlasticEntityPhysics.directionMask(direction);
        if ((this.impactSoundMask & directionMask) != 0) return;
        this.impactSoundMask |= directionMask;
        float volume = (float) Math.min(0.9D, 0.48D + speed * 0.12D);
        float pitch = 0.96F + this.random.nextFloat() * 0.12F;
        this.level().playSound(
            null,
            this.blockPosition(),
            this.impactSound(),
            SoundSource.BLOCKS,
            volume,
            pitch
        );
    }

    protected SoundEvent impactSound() {
        return SoundEvents.BONE_BLOCK_PLACE;
    }

    /**
     * 在不改变共用移动和落地生命周期的前提下，为材料变体提供碰撞后速度钩子。
     * 默认实现有意与原始可移动砧行为完全一致。
     */
    protected Vec3 resolveCollisionVelocity(
        Vec3 requestedMovement,
        Vec3 actualMovement,
        Vec3 velocityAfterMove,
        Direction gravityDirection
    ) {
        return velocityAfterMove;
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
            && this.directionalFallDistance <= PlasticEntityPhysics.FACE_EPSILON
            && PlasticEntityPhysics.hasImmediateBlockContact(this, gravityDirection)) {
            this.blockContactMask = PlasticEntityPhysics.directionMask(gravityDirection);
        }
    }

    private void postLandingEvent(Direction gravityDirection) {
        if (this.level().isClientSide || !this.triggersAnvilCraftLandingEvents(gravityDirection)) return;
        List<BlockPos> landingPositions = PlasticEntityPhysics.landingPositions(this, gravityDirection);
        BlockPos centerPos = landingPositions.getFirst();
        float fallDistance = this.directionalFallDistance;
        this.handleLandingOnce(centerPos, fallDistance);
        boolean anvilDamage = false;
        for (BlockPos landingPos : landingPositions) {
            // 逐格配方可能直接销毁制品，剩余格不能再拿已移除的实体当落砧来源。
            if (this.isRemoved()) break;
            AnvilEvent.OnLand event = new AnvilEvent.OnLand(
                this.level(),
                landingPos,
                this,
                fallDistance
            );
            if (!this.handleAdditionalAnvilCraftLanding(event)) {
                NeoForge.EVENT_BUS.post(event);
            }
            anvilDamage |= event.isAnvilDamage();
        }
        if (anvilDamage) {
            this.handleAnvilCraftLandingDamage(centerPos);
        }
    }

    @Override
    public float anvilcraft$getFallDistance() {
        return this.directionalFallDistance;
    }

    @Override
    public boolean plasticraft$canMoveWithCarrier(Entity carrier, Vec3 requestedMovement) {
        if (this.carrierMoveInProgress) return false;
        return this.carrierTransfer(
            carrier,
            carrier.getBoundingBox(),
            requestedMovement
        ) != null;
    }

    @Override
    public void plasticraft$moveWithCarrier(Entity carrier, Vec3 actualMovement) {
        if (actualMovement.lengthSqr() <= PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON
            || !PlasticEntityPhysics.isWithinCarryDistance(actualMovement)) return;

        AABB previousCarrierBox = carrier.getBoundingBox().move(actualMovement.scale(-1.0D));
        CarrierTransfer transfer = this.carrierTransfer(carrier, previousCarrierBox, actualMovement);
        if (transfer == null) return;
        Vec3 transferredMovement = transfer.targetMovement();
        if (!transfer.isCarried()) {
            transferredMovement = PlasticWallSnapping.extendToNearbyBlock(this, transferredMovement);
        }

        PlasticPushChain.ClippedPlan clipped = PlasticPushChain.clip(this, carrier, transferredMovement);
        transferredMovement = clipped.movement();
        if (transferredMovement.lengthSqr()
            <= PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) return;
        clipped.plan().move(this, carrier);

        if (EntityBondManager.hasBonds(this)) {
            Vec3 moved = this.moveBondedComponentWithCarrier(carrier, transferredMovement);
            if (transfer.isCarried()) {
                this.supportObservation = PlasticEntityPhysics.SupportObservation.capture(carrier);
                this.supportDirection = transfer.supportDirection();
            } else {
                Entity leader = EntityBondManager.resolveLeader(this.level(), this);
                if (leader instanceof AbstractPlasticEntity plasticLeader) {
                    plasticLeader.finishTransferredSidePush(carrier, moved);
                }
            }
            return;
        }

        Vec3 moved = this.moveWithCarrierDisplacement(carrier, transferredMovement);
        if (transfer.isCarried()) {
            this.supportObservation = PlasticEntityPhysics.SupportObservation.capture(carrier);
            this.supportDirection = transfer.supportDirection();
            return;
        }
        this.finishTransferredSidePush(carrier, moved);
    }

    @Override
    public Vec3 plasticraft$clampCarrierMovement(Entity carrier, Vec3 requestedMovement) {
        CarrierTransfer transfer = this.carrierTransfer(
            carrier,
            carrier.getBoundingBox(),
            requestedMovement
        );
        if (this.carrierMoveInProgress || transfer == null) return requestedMovement;
        Vec3 allowedTargetMovement = PlasticPushChain.clip(
            this,
            carrier,
            transfer.targetMovement()
        ).movement();
        if (transfer.isCarried() && !(carrier instanceof AbstractPlasticEntity)) {
            // 玩家或生物头顶承载被世界挡住时，只限制朝制品的法向位移，切向仍可走出制品下方。
            return clampLivingHeadCarry(
                requestedMovement,
                transfer.targetMovement(),
                allowedTargetMovement,
                transfer.supportDirection()
            );
        }
        return transfer.clampCarrierMovement(requestedMovement, allowedTargetMovement);
    }

    private static Vec3 clampLivingHeadCarry(
        Vec3 carrierMovement,
        Vec3 requestedTargetMovement,
        Vec3 allowedTargetMovement,
        Direction supportDirection
    ) {
        Vec3 limited = PlasticEntityPhysics.clampCarrierMovement(
            carrierMovement,
            requestedTargetMovement,
            allowedTargetMovement
        );
        return PlasticEntityPhysics.tangentialMovement(carrierMovement, supportDirection)
            .add(limited.subtract(PlasticEntityPhysics.tangentialMovement(limited, supportDirection)));
    }

    public boolean plasticraft$isSupportedBy(Entity support) {
        return support != null && this.isCurrentSupport(support, support.getBoundingBox());
    }

    private Vec3 moveBondedComponentWithCarrier(Entity ignored, Vec3 movement) {
        Entity leader = EntityBondManager.resolveLeader(this.level(), this);
        if (leader == null || !leader.isAlive()) return Vec3.ZERO;
        Vec3 start = leader.position();
        EntityBondManager.runPreclippedComponentMovement(this, ignored, () -> {
            if (leader instanceof AbstractPlasticEntity plasticLeader) {
                plasticLeader.moveWithCarrierDisplacement(null, movement);
            } else {
                leader.move(MoverType.SELF, movement);
            }
        });
        Vec3 moved = leader.position().subtract(start);
        EntityBondManager.synchronizeComponent(leader);
        return moved;
    }

    private void finishTransferredSidePush(Entity carrier, Vec3 moved) {
        if (moved.lengthSqr() <= PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
            return;
        }
        this.lastSidePushCarrierId = carrier.getUUID();
        this.lastSidePushCarrierGameTime = this.level().getGameTime();
        this.slidingRailState.reapplyPoweredDrive(this);
        this.hasImpulse = true;
        this.hurtMarked = true;
    }

    private CarrierTransfer carrierTransfer(Entity carrier, AABB carrierBox, Vec3 requestedMovement) {
        if (!PlasticEntityPhysics.isWithinCarryDistance(requestedMovement)) return null;
        if (this.carrierStandsOnBondedComponent(carrier, carrierBox)) {
            return null;
        }
        Direction carrierSupportDirection = this.carrierSupportDirection(carrier, carrierBox);
        if (carrierSupportDirection != null
            && PlasticEntityPhysics.canMoveWithCarrier(
                this,
                carrier,
                carrierBox,
                carrierSupportDirection,
                requestedMovement
            )) {
            return CarrierTransfer.carried(
                PlasticEntityPhysics.carriedMovement(requestedMovement, carrierSupportDirection),
                carrierSupportDirection
            );
        }
        if (!this.transfersSidePushWithCarrier(carrier)) return null;
        Direction gravityDirection = this.currentPushGravityDirection();
        PlasticEntityContactResolver.PushContact sidePush = PlasticEntityPhysics.sidePushContact(
            this,
            carrier,
            carrierBox,
            gravityDirection,
            requestedMovement
        );
        return sidePush == null ? null : CarrierTransfer.pushed(sidePush);
    }

    private boolean carrierStandsOnBondedComponent(Entity carrier, AABB carrierBox) {
        if (!EntityBondManager.hasBonds(this)) return false;
        Entity leader = EntityBondManager.resolveLeader(this.level(), this);
        if (leader == null || !leader.isAlive()) return false;
        Direction gravityDirection = PlasticEntityPhysics.gravityDirection(carrier);
        for (Entity member : EntityBondManager.component(this.level(), leader)) {
            if (PlasticEntityPhysics.hasSurfaceSupport(
                carrier,
                carrierBox,
                member,
                gravityDirection
            )) {
                return true;
            }
        }
        return false;
    }

    private record CarrierTransfer(
        Vec3 targetMovement,
        Direction supportDirection,
        PlasticEntityContactResolver.PushContact sidePush
    ) {
        private static CarrierTransfer carried(Vec3 movement, Direction supportDirection) {
            return new CarrierTransfer(movement, supportDirection, null);
        }

        private static CarrierTransfer pushed(PlasticEntityContactResolver.PushContact sidePush) {
            return new CarrierTransfer(sidePush.targetMovement(), null, sidePush);
        }

        private boolean isCarried() {
            return this.supportDirection != null;
        }

        private Vec3 clampCarrierMovement(Vec3 carrierMovement, Vec3 allowedTargetMovement) {
            if (this.sidePush != null) {
                return this.sidePush.clampCarrierMovement(carrierMovement, allowedTargetMovement);
            }
            return PlasticEntityPhysics.clampCarrierMovement(
                carrierMovement,
                this.targetMovement,
                allowedTargetMovement
            );
        }
    }

    private boolean isCurrentSupport(Entity carrier, AABB carrierBox) {
        Direction observedDirection = this.supportDirection;
        return this.supportObservation != null
            && observedDirection != null
            && this.supportObservation.entityId().equals(carrier.getUUID())
            && PlasticEntityPhysics.hasImmediateEntityContact(
                this,
                this.getBoundingBox(),
                carrier,
                carrierBox,
                observedDirection
            );
    }

    private Direction carrierSupportDirection(Entity carrier, AABB carrierBox) {
        if (this.isCurrentSupport(carrier, carrierBox)) return this.supportDirection;
        if (carrier instanceof AbstractPlasticEntity) {
            Direction gravityDirection = this.currentPushGravityDirection();
            return PlasticEntityPhysics.hasImmediateEntityContact(
                this,
                this.getBoundingBox(),
                carrier,
                carrierBox,
                gravityDirection
            ) ? gravityDirection : null;
        }
        if (!EntityBondManager.isFollower(this)) return null;
        Direction gravityDirection = this.currentPushGravityDirection();
        return PlasticEntityPhysics.hasImmediateEntityContact(
            this,
            this.getBoundingBox(),
            carrier,
            carrierBox,
            gravityDirection
        )
            ? gravityDirection
            : null;
    }

    private Vec3 moveWithCarrierDisplacement(Entity ignoredCollision, Vec3 movement) {
        if (movement.lengthSqr() <= PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
            return Vec3.ZERO;
        }
        Vec3 velocity = this.getDeltaMovement();
        Vec3 start = this.position();
        this.carrierMoveInProgress = true;
        try {
            // AnvilCraft 的碰撞事件会在 Entity.move 内读取增量位移。
            // 临时公开此位移，使其速度和命中位置保持为有限值。
            this.setDeltaMovement(movement);
            if (ignoredCollision == null) {
                this.move(MoverType.SELF, movement);
            } else {
                EntityBondManager.runPreclippedComponentMovement(
                    this,
                    ignoredCollision,
                    () -> this.move(MoverType.SELF, movement)
                );
            }
        } finally {
            this.setDeltaMovement(velocity);
            this.carrierMoveInProgress = false;
        }
        Vec3 actualMovement = this.position().subtract(start);
        PlasticEntityGridSnapping.applyAfterMove(this, movement, actualMovement);
        actualMovement = this.position().subtract(start);
        if (!this.level().isClientSide
            && actualMovement.lengthSqr()
                > PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
            this.observerContactsDirty = true;
            this.refreshObserverContacts();
        }
        if (this.level().isClientSide
            && actualMovement.lengthSqr()
                > PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
            long gameTime = this.level().getGameTime();
            if (this.clientCarrierMoveGameTime != gameTime) {
                this.clientCarrierMoveGameTime = gameTime;
                this.clientCarrierMoveStartX = start.x;
                this.clientCarrierMoveStartY = start.y;
                this.clientCarrierMoveStartZ = start.z;
            }
            boolean predictionWasEmpty = this.clientCarrierPrediction.isEmpty();
            this.clientCarrierPrediction.add(actualMovement);
            Vec3 predictedMovement = this.clientCarrierPrediction.pendingMovement();
            if (!Double.isFinite(predictedMovement.lengthSqr())
                || predictedMovement.lengthSqr() > CLIENT_MAX_PREDICTION_DISTANCE_SQR
                || this.clientCarrierPrediction.remainingTravel() > CLIENT_MAX_PREDICTION_TRAVEL) {
                this.clientCarrierPrediction.clear();
                this.clientLastPredictionConfirmationGameTime = Long.MIN_VALUE;
                if (this.clientServerPositionInitialized) {
                    this.clientSnapshotSteps = 1;
                    this.clientSnapshotPending = true;
                    this.clientSnapshotHardCorrection = true;
                }
                return actualMovement;
            }
            if (predictionWasEmpty) {
                this.clientLastPredictionConfirmationGameTime = this.level().getGameTime();
            }
        }
        return actualMovement;
    }

    @Override
    public void push(Entity entity) {
        // 相接的塑料实体静止时保持稳定。其主动位移由 PlasticPushChain 传播一次；
        // 原版相互冲量会在下一次连续推动前制造间隙。
        if (entity instanceof AbstractPlasticEntity) return;
        this.plasticraft$wakeFromRest();
        if (this.wasRecentlyMovedBySidePushCarrier(entity)) return;
        if (this.isCurrentSupport(entity, entity.getBoundingBox())) {
            return;
        }
        Direction gravityDirection = this.currentPushGravityDirection();
        if (PlasticEntityPhysics.isSupportCandidate(this, entity, gravityDirection)
            || !PlasticEntityPhysics.isSideContact(this, entity, gravityDirection)) return;
        // 玩家与其他受支撑实体的主动移动均由同一连续侧推接管，避免静止接触产生额外冲量。
        if (!this.sideEntityPushInProgress
            && this.transfersSidePushWithCarrier(entity)
            && PlasticEntityPhysics.isContinuousSidePushCarrier(this, entity, entity.getBoundingBox())) return;
        super.push(entity);
    }

    @Override
    public void setDeltaMovement(Vec3 deltaMovement) {
        // 爆炸击退、命令与外部模组都直接写速度，既不经过 push(Entity) 也不改变重力场、加速环、
        // 流体或方块支撑，因此错峰复核发现不了。休眠分支不读速度，这里是唯一的唤醒出口。
        if (deltaMovement.lengthSqr()
            > PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
            this.plasticraft$wakeFromRest();
        }
        super.setDeltaMovement(deltaMovement);
    }

    private boolean wasRecentlyMovedBySidePushCarrier(Entity entity) {
        if (this.lastSidePushCarrierId == null || !this.lastSidePushCarrierId.equals(entity.getUUID())) {
            return false;
        }
        long elapsed = this.level().getGameTime() - this.lastSidePushCarrierGameTime;
        // 承载目标通常在推动者移动后的同刻或下一刻处理侧面互推。
        return elapsed >= 0L && elapsed <= 1L;
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
        Direction direction = PlasticEntityPhysics.directionOrNull(
            gravity.add(0.0D, buoyancy, 0.0D)
        );
        return direction == null ? Direction.DOWN : direction;
    }

    public final Direction plasticraft$currentPushGravityDirection() {
        return this.currentPushGravityDirection();
    }

    public final void plasticraft$recordSupportedFallingBlock(BlockPos pos) {
        Set<BlockPos> positions = new HashSet<>(this.supportedFallingBlocks);
        positions.add(pos.immutable());
        this.supportedFallingBlocks = positions;
    }

    public final void plasticraft$applyTransferredPush(Entity pusher, Vec3 movement) {
        if (!EntityBondManager.hasBonds(this)) {
            this.finishTransferredSidePush(pusher, this.moveWithCarrierDisplacement(pusher, movement));
            return;
        }
        Vec3 moved = this.moveBondedComponentWithCarrier(pusher, movement);
        Entity leader = EntityBondManager.resolveLeader(this.level(), this);
        if (leader instanceof AbstractPlasticEntity plasticLeader) {
            plasticLeader.finishTransferredSidePush(pusher, moved);
        }
    }

    /** 侧面接触是否参与共用的连续承载移动。 */
    protected boolean transfersSidePushWithCarrier(Entity carrier) {
        return true;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {
            // 索引持有实体强引用，注销必须先于移除；同时唤醒周围实体重算失去本实体后的接触关系。
            this.plasticraft$wakeFromRest();
            PlasticRestIndex.wakeInBounds(this.level(), this.getBoundingBox());
            if (reason.shouldDestroy() && !this.observerContacts.isEmpty()) {
                PlasticObserverContact.clear(this.level(), this.observerContacts);
                this.observerContacts = Set.of();
            }
        }
        super.remove(reason);
    }

    /**
     * 区块卸载走 {@code setRemoved(UNLOADED_TO_CHUNK)}，不经过 {@link #remove}，因此注销必须挂在这里：
     * 本方法是所有移除路径的共同出口，能确保索引不会残留实体强引用。
     */
    @Override
    public void onRemovedFromLevel() {
        this.plasticraft$wakeFromRest();
        super.onRemovedFromLevel();
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
        return !EntityBondManager.areInSameComponent(this, entity)
            && Boat.canVehicleCollide(this, entity);
    }

    @Override
    public final EntityDimensions getDimensions(Pose pose) {
        // EntityDimensions 仅保留给原版眼高等 API；真实宽阶段范围由 makeBoundingBox 按几何生成。
        return EntityDimensions.scalable(COLLISION_SIZE, COLLISION_SIZE);
    }

    @Override
    protected final AABB makeBoundingBox() {
        if (!this.plasticGeometryReady) return super.makeBoundingBox();
        return this.currentGeometry().boundingBoxAt(this.position(), this.getOrientation());
    }

    /** 子类返回不可变几何；动态模型数据变化后必须调用 {@link #invalidatePlasticGeometry()}。 */
    protected abstract PlasticEntityGeometry getLocalGeometry();

    public final PlasticEntityGeometry plasticraft$getGeometry() {
        return this.currentGeometry();
    }

    /** 供后续由同步模型数据派生几何的实体主动刷新碰撞和宽阶段范围。 */
    protected final void invalidatePlasticGeometry() {
        // 几何体变化会改写碰撞子盒，已登记的休眠格随之失效；这里统一覆盖锤击旋转、
        // 展示方块替换、托盘内容变化等所有几何来源，无需在各调用点单独挂唤醒钩子。
        this.plasticraft$wakeFromRest();
        this.cachedGeometry = null;
        this.cachedGeometryOrientation = null;
        this.cachedOrientedGeometry = null;
        this.cachedCollisionBoxPosition = null;
        this.cachedCollisionBox = null;
        this.cachedInteractionShapePosition = null;
        this.cachedInteractionShape = Shapes.empty();
        this.observerContactsDirty = true;
        // 几何体变化会改变碰撞子盒，位置与朝向却可能都没动，必须显式作废支撑缓存。
        this.supportCacheGameTime = Long.MIN_VALUE;
        if (this.plasticGeometryReady) {
            this.setBoundingBox(this.makeBoundingBox());
        }
    }

    @Override
    public final PlasticEntityCollisionBox plasticraft$getCollisionBox() {
        PlasticEntityGeometry geometry = this.currentGeometry();
        PlasticEntityOrientation orientation = this.getOrientation();
        this.currentOrientedGeometry(geometry, orientation);
        Vec3 position = this.position();
        if (!position.equals(this.cachedCollisionBoxPosition)) {
            this.cachedCollisionBoxPosition = position;
            this.cachedCollisionBox = geometry.collisionBoxAt(position, orientation);
        }
        return Objects.requireNonNull(this.cachedCollisionBox, "collision box");
    }

    @Override
    public final VoxelShape plasticraft$getInteractionShape() {
        PlasticEntityGeometry geometry = this.currentGeometry();
        PlasticEntityOrientation orientation = this.getOrientation();
        this.currentOrientedGeometry(geometry, orientation);
        Vec3 position = this.position();
        if (!position.equals(this.cachedInteractionShapePosition)) {
            this.cachedInteractionShapePosition = position;
            this.cachedInteractionShape = geometry.interactionShapeAt(position, orientation);
        }
        return this.cachedInteractionShape;
    }

    /** 返回该朝向贴住指定首格时的实体位置。 */
    public final Vec3 plasticraft$placementPosition(
        BlockPos occupiedPos,
        PlasticEntityOrientation orientation
    ) {
        return this.currentGeometry().placementPosition(occupiedPos, orientation);
    }

    /** 返回让指定局部面贴住方块单元对应面时的实体位置。 */
    public final Vec3 plasticraft$placementPosition(
        BlockPos occupiedPos,
        PlasticEntityOrientation orientation,
        Direction localFace
    ) {
        return this.currentGeometry().placementPosition(occupiedPos, orientation, localFace);
    }

    /** 返回不会因碰撞外包围盒偏心而漂移的模型旋转中心。 */
    public final Vec3 plasticraft$getRotationCenter() {
        return this.currentGeometry().rotationCenterAt(this.position());
    }

    /** 返回权限、回收和单点玩法使用的稳定模型锚点。 */
    public final BlockPos plasticraft$getAnchorBlockPos() {
        return BlockPos.containing(this.plasticraft$getRotationCenter());
    }

    private PlasticEntityGeometry currentGeometry() {
        return Objects.requireNonNull(this.getLocalGeometry(), "local geometry");
    }

    private PlasticEntityGeometry.Oriented currentOrientedGeometry(
        PlasticEntityGeometry geometry,
        PlasticEntityOrientation orientation
    ) {
        if (geometry != this.cachedGeometry || !orientation.equals(this.cachedGeometryOrientation)) {
            this.cachedGeometry = geometry;
            this.cachedGeometryOrientation = orientation;
            this.cachedOrientedGeometry = geometry.oriented(orientation);
            this.cachedCollisionBoxPosition = null;
            this.cachedCollisionBox = null;
            this.cachedInteractionShapePosition = null;
            this.cachedInteractionShape = Shapes.empty();
        }
        return Objects.requireNonNull(this.cachedOrientedGeometry, "oriented geometry");
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.NORMAL;
    }

    @Override
    public ItemStack getPickResult() {
        return this.prepareInitialPickResult(this.getCompletePickResult());
    }

    /** 返回 Ctrl 中键使用的完整物品状态。 */
    public ItemStack getCompletePickResult() {
        if (!this.level().isClientSide) return this.getDropStack();
        ItemStack synchronizedStack = this.entityData.get(PICK_STACK);
        if (synchronizedStack.isEmpty()) return this.getDropStack();
        ItemStack result = synchronizedStack.copyWithCount(1);
        PlasticItemData.setMaterial(result, this.materialKey());
        if (!this.supportsDyeing()) PlasticItemData.clearColor(result);
        PlasticItemData.setMagnetized(result, this.isMagnetized());
        return this.prepareDropStack(result);
    }

    /** 普通中键从完整结果中去除可变内容，材料、颜色和磁化状态仍由完整物品保留。 */
    protected ItemStack prepareInitialPickResult(ItemStack stack) {
        return stack;
    }

    @Override
    public final InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (stack.getItem() instanceof AnvilHammerItem) {
                return this.pickUpWithAnvilHammer(player);
            }
            if (this.anvilcraft$acceptMagnetization(player, stack)) {
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            return InteractionResult.PASS;
        }
        return this.interactNormally(player, hand);
    }

    private InteractionResult pickUpWithAnvilHammer(Player player) {
        BlockPos occupiedPos = this.plasticraft$getAnchorBlockPos();
        if (!player.getAbilities().mayBuild || !this.level().mayInteract(player, occupiedPos)) {
            return InteractionResult.PASS;
        }

        if (this.getDropStack().isEmpty()) return InteractionResult.FAIL;
        if (this.level().isClientSide) return InteractionResult.SUCCESS;

        this.prepareAnvilHammerPickup(player);
        ItemStack drop = this.getDropStack();
        if (drop.isEmpty()) return InteractionResult.FAIL;
        player.getInventory().placeItemBackInInventory(drop);
        this.level().playSound(
            null,
            occupiedPos,
            this.getDisplayState().getSoundType().getBreakSound(),
            SoundSource.BLOCKS,
            0.8F,
            1.0F
        );
        this.gameEvent(GameEvent.ENTITY_INTERACT, player);
        this.discard();
        return InteractionResult.CONSUME;
    }

    /** 在铁砧锤回收前让容器实体转移自身额外保存的物品。 */
    protected void prepareAnvilHammerPickup(Player player) {
    }

    @Override
    public final boolean anvilcraft$acceptMagnetization(Player player, ItemStack stack) {
        if (!this.supportsCreativeMagnetization()
            || !player.isCreative()
            || !player.isShiftKeyDown()
            || !isMagnetizationTool(stack)) {
            return false;
        }
        if (!this.level().isClientSide) {
            boolean magnetized = !this.isMagnetized();
            this.setMagnetized(magnetized);
            this.hasImpulse = true;
            this.hurtMarked = true;
            player.getCooldowns().addCooldown(stack.getItem(), 5);
            this.level().playSound(
                null,
                this.blockPosition(),
                SoundEvents.LODESTONE_COMPASS_LOCK,
                SoundSource.PLAYERS,
                0.8F,
                magnetized ? 1.15F : 0.85F
            );
            this.gameEvent(GameEvent.ENTITY_INTERACT, player);
        }
        return true;
    }

    protected boolean supportsCreativeMagnetization() {
        return true;
    }

    protected boolean supportsHammerRotation() {
        return true;
    }

    public final boolean supportsAnvilHammerOrientationMenu() {
        return this.supportsHammerRotation();
    }

    public final boolean canHammerRotateTo(PlasticEntityOrientation targetOrientation) {
        return this.canHammerRotateTo(targetOrientation, this.position(), Set.of());
    }

    /** 检查当前几何能否在指定姿态和位置占据世界，用于放置等非旋转入口。 */
    public final boolean plasticraft$canOccupy(
        PlasticEntityOrientation targetOrientation,
        Vec3 targetPosition
    ) {
        return this.isGeometryUnobstructed(targetOrientation, targetPosition, Set.of());
    }

    /** 检查目标姿态是否会穿入方块或越过世界边界，不把尚未同步的粘连成员视为障碍。 */
    public final boolean plasticraft$canOccupyBlocks(
        PlasticEntityOrientation targetOrientation,
        Vec3 targetPosition
    ) {
        return this.plasticraft$canOccupyBlocks(targetOrientation, targetPosition, Set.of());
    }

    /** 检查目标姿态能否占据世界，并允许制造流程忽略自身的结构占位方块。 */
    public final boolean plasticraft$canOccupyBlocks(
        PlasticEntityOrientation targetOrientation,
        Vec3 targetPosition,
        Set<BlockPos> ignoredBlocks
    ) {
        Objects.requireNonNull(ignoredBlocks, "ignoredBlocks");
        PlasticEntityCollisionBox targetBox = this.currentGeometry().collisionBoxAt(targetPosition, targetOrientation);
        return this.hasUnobstructedBlocks(targetBox, ignoredBlocks);
    }

    /** 固定制品校验目标格时忽略承载自身数据的方块。 */
    public final boolean canHammerRotateTo(
        PlasticEntityOrientation targetOrientation,
        Vec3 targetPosition,
        BlockPos ignoredBlock
    ) {
        return this.canHammerRotateTo(targetOrientation, targetPosition, Set.of(ignoredBlock.immutable()));
    }

    private boolean canHammerRotateTo(
        PlasticEntityOrientation targetOrientation,
        Vec3 targetPosition,
        Set<BlockPos> ignoredBlocks
    ) {
        PlasticEntityOrientation target = Objects.requireNonNull(targetOrientation, "targetOrientation");
        Vec3 position = Objects.requireNonNull(targetPosition, "targetPosition");
        Objects.requireNonNull(ignoredBlocks, "ignoredBlocks");
        if (!this.supportsHammerRotation()) return false;
        if (target.equals(this.getOrientation()) && position.equals(this.position())) return true;

        return this.isGeometryUnobstructed(target, position, ignoredBlocks);
    }

    private boolean isGeometryUnobstructed(
        PlasticEntityOrientation target,
        Vec3 position,
        Set<BlockPos> ignoredBlocks
    ) {
        PlasticEntityCollisionBox targetBox = this.currentGeometry().collisionBoxAt(position, target);
        if (!this.hasUnobstructedBlocks(targetBox, ignoredBlocks)) return false;
        List<AABB> probes = targetBox.components().stream()
            .map(AbstractPlasticEntity::hammerRotationProbe)
            .toList();

        if (probes.isEmpty()) return true;
        List<Entity> candidates = this.level().getEntities(
            this,
            targetBox.bounds().inflate(HAMMER_ROTATION_COLLISION_EPSILON),
            other -> EntitySelector.NO_SPECTATORS.test(other) && this.canCollideWith(other)
        );
        for (Entity candidate : candidates) {
            List<AABB> candidateComponents = candidate instanceof ShapedCollisionEntity shaped
                ? shaped.plasticraft$getCollisionBox().components()
                : List.of(candidate.getBoundingBox());
            if (intersectsAny(probes, candidateComponents)) return false;
        }
        return true;
    }

    private boolean hasUnobstructedBlocks(PlasticEntityCollisionBox targetBox, Set<BlockPos> ignoredBlocks) {
        List<AABB> probes = targetBox.components().stream()
            .map(AbstractPlasticEntity::hammerRotationProbe)
            .toList();
        if (!this.level().getWorldBorder().isWithinBounds(targetBox.bounds())) return false;

        CollisionContext context = CollisionContext.of(this);
        Map<Long, List<AABB>> blockCollisions = new HashMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (AABB probe : probes) {
            int minX = Mth.floor(probe.minX);
            int minY = Mth.floor(probe.minY);
            int minZ = Mth.floor(probe.minZ);
            int maxX = Mth.floor(probe.maxX);
            int maxY = Mth.floor(probe.maxY);
            int maxZ = Mth.floor(probe.maxZ);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        cursor.set(x, y, z);
                        if (ignoredBlocks.contains(cursor)) continue;
                        if (!this.level().hasChunkAt(cursor)) return false;
                        long key = cursor.asLong();
                        List<AABB> collision = blockCollisions.get(key);
                        if (collision == null) {
                            collision = this.level().getBlockState(cursor)
                                .getCollisionShape(this.level(), cursor, context)
                                .move(x, y, z)
                                .toAabbs();
                            blockCollisions.put(key, collision);
                        }
                        for (AABB box : collision) {
                            if (probe.intersects(box)) return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static boolean intersectsAny(List<AABB> first, List<AABB> second) {
        for (AABB firstBox : first) {
            for (AABB secondBox : second) {
                if (firstBox.intersects(secondBox)) return true;
            }
        }
        return false;
    }

    private static AABB hammerRotationProbe(AABB component) {
        double maxInset = Math.min(
            component.getXsize(),
            Math.min(component.getYsize(), component.getZsize())
        ) * 0.25D;
        return component.deflate(Math.min(HAMMER_ROTATION_COLLISION_EPSILON, maxInset));
    }

    /** 执行铁砧锤快速释放时的默认实体交互。 */
    public final InteractionResult plasticraft$useAnvilHammer(
        Player player,
        InteractionHand hand,
        Direction interactionFace
    ) {
        if (player.isShiftKeyDown()
            || !(player.getItemInHand(hand).getItem() instanceof AnvilHammerItem)) {
            return InteractionResult.PASS;
        }
        return this.interactWithAnvilHammer(player, hand, interactionFace);
    }

    /** 子类可在此实现铁砧锤快速释放行为，例如切换釜的输出口。 */
    protected InteractionResult interactWithAnvilHammer(
        Player player,
        InteractionHand hand,
        Direction interactionFace
    ) {
        return this.interactNormally(player, hand);
    }

    /** 应用铁砧锤环形菜单选中的附着面。 */
    public final boolean plasticraft$changeAttachmentFace(
        Player player,
        InteractionHand hand,
        Direction attachmentFace
    ) {
        ItemStack stack = player.getItemInHand(hand);
        BlockPos occupiedPos = this.plasticraft$getAnchorBlockPos();
        if (player.isShiftKeyDown()
            || !(stack.getItem() instanceof AnvilHammerItem)
            || !this.supportsHammerRotation()
            || !player.getAbilities().mayBuild
            || !this.level().mayInteract(player, occupiedPos)) {
            return false;
        }

        PlasticEntityOrientation current = this.getOrientation();
        if (current.attachmentFace() == attachmentFace) return true;
        PlasticEntityOrientation changed = new PlasticEntityOrientation(attachmentFace, current.quarterTurn());
        if (!this.canHammerRotateTo(changed)) return false;
        if (!EntityBondManager.hasBonds(this) || !this.startHammerDeflection(changed)) {
            this.setOrientation(changed);
        }
        this.hasImpulse = true;
        this.hurtMarked = true;
        this.level().playSound(
            null,
            this.blockPosition(),
            ModSoundEvents.ANVIL_HAMMER_ROTATE_BLOCK.get(),
            SoundSource.BLOCKS,
            2.0F,
            1.0F
        );
        this.gameEvent(GameEvent.ENTITY_INTERACT, player);
        return true;
    }

    private static boolean isMagnetizationTool(ItemStack stack) {
        return stack.getItem() instanceof MagnetItem
            || stack.getItem() instanceof MultitoolItem
                && MultitoolItem.getMode(stack) == MultitoolItem.MAGNET_MODE;
    }

    protected InteractionResult interactNormally(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 location, InteractionHand hand) {
        this.plasticraft$wakeFromRest();
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() && stack.getItem() instanceof AnvilHammerItem) {
            return this.plasticraft$useAnvilHammer(player, hand, this.nearestInteractionFace(location));
        }
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;

        if (this.anvilcraft$acceptMagnetization(player, stack)) {
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (stack.isEmpty()) return InteractionResult.PASS;
        Vec3 absoluteHit = this.position().add(location);
        Direction face = this.nearestInteractionFace(location);
        Vec3 faceLocation = AdhesiveFaces.faceLocation(this, absoluteHit, face);
        BlockPos targetPos = outsideFaceBlockPos(faceLocation, face);
        if (!this.level().getBlockState(targetPos).canBeReplaced()) return InteractionResult.FAIL;
        BlockHitResult hit = new BlockHitResult(faceLocation, face, targetPos, false);
        if (stack.getItem() instanceof EntityFacePlaceableItem entityItem) {
            if (this.level().isClientSide) return InteractionResult.SUCCESS;
            return entityItem.plasticraft$placeOnEntityFace(this.level(), player, hand, stack, hit);
        }
        if (stack.getItem() instanceof BlockItem || stack.getItem() instanceof PipeBlockItem) {
            return stack.useOn(new UseOnContext(this.level(), player, hand, stack, hit));
        }
        return InteractionResult.PASS;
    }

    public final Direction nearestInteractionFace(Vec3 relativeLocation) {
        Vec3 absolute = this.position().add(relativeLocation);
        return AdhesiveFaces.hitFace(this, absolute);
    }

    private static BlockPos outsideFaceBlockPos(Vec3 faceLocation, Direction face) {
        int x = Mth.floor(faceLocation.x);
        int y = Mth.floor(faceLocation.y);
        int z = Mth.floor(faceLocation.z);
        int outside = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
            ? Mth.floor(faceLocation.get(face.getAxis()) - PlasticEntityPhysics.FACE_EPSILON) + 1
            : Mth.floor(faceLocation.get(face.getAxis()) + PlasticEntityPhysics.FACE_EPSILON) - 1;
        return switch (face.getAxis()) {
            case X -> new BlockPos(outside, y, z);
            case Y -> new BlockPos(x, outside, z);
            case Z -> new BlockPos(x, y, outside);
        };
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }
        if (!this.level().isClientSide) {
            this.playBlockBreakEffect();
            if (!isCreativePlayerDamage(source)
                && this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                this.spawnDropAtGeometry(this.getDropStack());
            }
            this.discard();
        } else {
            this.markHurt();
        }
        return true;
    }

    /** 在真实几何中心播放当前显示方块状态的原版破坏效果。 */
    private void playBlockBreakEffect() {
        if (this.isRemoved()) return;
        BlockPos effectPos = BlockPos.containing(this.getBoundingBox().getCenter());
        this.level().levelEvent(2001, effectPos, Block.getId(this.getDisplayState()));
    }

    private static boolean isCreativePlayerDamage(DamageSource source) {
        return source.getEntity() instanceof Player causingPlayer && causingPlayer.isCreative()
            || source.getDirectEntity() instanceof Player directPlayer && directPlayer.isCreative();
    }

    /** 偏置模型的实体原点可能位于真实几何之外，破坏掉落必须改用几何中心并避开支撑面。 */
    private void spawnDropAtGeometry(ItemStack stack) {
        if (stack.isEmpty()) return;
        AABB geometryBounds = this.getBoundingBox();
        Vec3 itemCenter = geometryBounds.getCenter();
        ItemEntity item = new ItemEntity(
            this.level(),
            itemCenter.x,
            itemCenter.y,
            itemCenter.z,
            stack
        );
        Direction awayFromSupport = this.getOrientation().attachmentFace();
        Vec3 outward = Vec3.atLowerCornerOf(awayFromSupport.getNormal());
        double halfExtent = awayFromSupport.getAxis() == Direction.Axis.Y
            ? item.getBbHeight() * 0.5D
            : item.getBbWidth() * 0.5D;
        double clearance = switch (awayFromSupport.getAxis()) {
            case X -> geometryBounds.getXsize() * 0.5D;
            case Y -> geometryBounds.getYsize() * 0.5D;
            case Z -> geometryBounds.getZsize() * 0.5D;
        };
        double correction = halfExtent + PlasticEntityPhysics.FACE_EPSILON - clearance;
        if (correction > 0.0D) itemCenter = itemCenter.add(outward.scale(correction));
        item.setPos(itemCenter.x, itemCenter.y - item.getBbHeight() * 0.5D, itemCenter.z);
        item.setDefaultPickUpDelay();
        Collection<ItemEntity> capturedDrops = this.captureDrops();
        if (capturedDrops == null) {
            this.level().addFreshEntity(item);
        } else {
            capturedDrops.add(item);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ItemStack stack = this.getDropStack();
        if (!stack.isEmpty()) {
            tag.put("DropStack", stack.save(this.registryAccess()));
        }
        PlasticEntityOrientation orientation = this.getOrientation();
        tag.putString("AttachmentFace", orientation.attachmentFace().getName());
        tag.putInt("InPlaneRotation", orientation.quarterTurn());
        tag.putFloat("DirectionalFallDistance", this.directionalFallDistance);
        tag.putInt("BlockContactMask", this.blockContactMask);
        tag.putBoolean("ImpactTrackingArmed", this.impactTrackingArmed);
        tag.putBoolean("Magnetized", this.isMagnetized());
        byte stableOrientation = this.entityData.get(HAMMER_STABLE_ORIENTATION);
        if (stableOrientation != NO_HAMMER_ORIENTATION) {
            tag.putByte(TAG_HAMMER_STABLE_ORIENTATION, stableOrientation);
            tag.putLong(TAG_HAMMER_RETURN_AT, this.hammerReturnAt);
        }
        long hammerReturnStarted = this.entityData.get(HAMMER_RETURN_STARTED);
        if (hammerReturnStarted >= 0L) {
            tag.putByte(TAG_HAMMER_RETURN_FROM, this.entityData.get(HAMMER_RETURN_FROM));
            tag.putLong(TAG_HAMMER_RETURN_STARTED, hammerReturnStarted);
        }
        if (this.effectiveGravityDirection != null) {
            tag.putString("EffectiveGravityDirection", this.effectiveGravityDirection.getName());
        }
        this.slidingRailState.save(tag);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // 存档与区块加载共用这条 NBT 路径；已有接触只能恢复边沿，不能当成新接触发脉冲。
        this.silentObserverContactHydration = true;
        // 改用落方块基类前的实体不会保存此标志，因此让这些实体保持轻量且不造成伤害。
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
            this.setOrientation(new PlasticEntityOrientation(attachmentFace, tag.getInt("InPlaneRotation")));
        } else {
            this.setOrientation(PlasticEntityOrientation.fromLegacyState(this.getBlockState()));
        }

        this.directionalFallDistance = Math.max(0.0F, tag.getFloat("DirectionalFallDistance"));
        this.blockContactMask = Math.max(0, tag.getInt("BlockContactMask"));
        this.impactTrackingArmed = tag.getBoolean("ImpactTrackingArmed");
        this.setMagnetized(tag.getBoolean("Magnetized"));
        this.entityData.set(
            HAMMER_STABLE_ORIENTATION,
            tag.contains(TAG_HAMMER_STABLE_ORIENTATION, Tag.TAG_ANY_NUMERIC)
                ? tag.getByte(TAG_HAMMER_STABLE_ORIENTATION)
                : NO_HAMMER_ORIENTATION
        );
        this.hammerReturnAt = tag.contains(TAG_HAMMER_RETURN_AT, Tag.TAG_ANY_NUMERIC)
            ? tag.getLong(TAG_HAMMER_RETURN_AT)
            : -1L;
        this.entityData.set(
            HAMMER_RETURN_FROM,
            tag.contains(TAG_HAMMER_RETURN_FROM, Tag.TAG_ANY_NUMERIC)
                ? tag.getByte(TAG_HAMMER_RETURN_FROM)
                : this.entityData.get(ORIENTATION)
        );
        this.entityData.set(
            HAMMER_RETURN_STARTED,
            tag.contains(TAG_HAMMER_RETURN_STARTED, Tag.TAG_ANY_NUMERIC)
                ? tag.getLong(TAG_HAMMER_RETURN_STARTED)
                : -1L
        );
        this.slidingRailState.load(tag);
        this.effectiveGravityDirection = Direction.byName(tag.getString("EffectiveGravityDirection"));
        if (this.effectiveGravityDirection == null) {
            this.directionalFallDistance = 0.0F;
            this.blockContactMask = 0;
            this.impactTrackingArmed = false;
        }
    }

    private void tickHammerDeflection() {
        long gameTime = this.level().getGameTime();
        byte stableOrientation = this.entityData.get(HAMMER_STABLE_ORIENTATION);
        if (stableOrientation != NO_HAMMER_ORIENTATION && gameTime >= this.hammerReturnAt) {
            this.entityData.set(HAMMER_RETURN_FROM, this.entityData.get(ORIENTATION));
            this.setOrientation(PlasticEntityOrientation.unpack(Byte.toUnsignedInt(stableOrientation)));
            this.entityData.set(HAMMER_STABLE_ORIENTATION, NO_HAMMER_ORIENTATION);
            this.entityData.set(HAMMER_RETURN_STARTED, gameTime);
            this.hammerReturnAt = -1L;
            this.hasImpulse = true;
            this.hurtMarked = true;
            return;
        }
        long returnStarted = this.entityData.get(HAMMER_RETURN_STARTED);
        if (returnStarted >= 0L && gameTime - returnStarted >= HAMMER_RETURN_ANIMATION_TICKS) {
            this.entityData.set(HAMMER_RETURN_STARTED, -1L);
        }
    }

    public record HammerRotationAnimation(
        PlasticEntityOrientation from,
        PlasticEntityOrientation to,
        float progress
    ) {
    }
}
