package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.api.entity.CarrierMovableEntity;
import dev.anvilcraft.plasticraft.api.entity.PlasticGravityTypeProvider;
import dev.anvilcraft.plasticraft.api.item.EntityFacePlaceableItem;
import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.entity.collision.PlasticPushChain;
import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityPhysics;
import dev.anvilcraft.plasticraft.entity.physics.PlasticFluidPhysics;
import dev.anvilcraft.plasticraft.entity.physics.PlasticMagnetism;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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
 * 可移动塑料制品共用的持久化落方块实现。
 *
 * <p>此处不能使用原版落方块的刻逻辑，因为它最终会变回方块，
 * 而这些制品必须保持为可碰撞、可推动的实体。此刻逻辑保留 AnvilCraft 的重力和加速入口点，
 * 同时明确管理这一不同的生命周期。</p>
 */
public abstract class AbstractPlasticEntity extends FallingBlockEntity
    implements PlasticGravityTypeProvider, CarrierMovableEntity {
    public static final float COLLISION_SIZE = 0.98F;

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

    protected AbstractPlasticEntity(
        EntityType<? extends AbstractPlasticEntity> entityType,
        Level level
    ) {
        super(entityType, level);
        this.blocksBuilding = true;
        this.setNoGravity(false);
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
            .define(MAGNETIZED, false);
    }

    public final BlockState getDisplayState() {
        return this.entityData.get(DISPLAY_STATE);
    }

    /** 保持渲染、AnvilCraft 配方、生成数据包和后续运行时变体变化同步。 */
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

    public final PlasticEntityOrientation getOrientation() {
        return PlasticEntityOrientation.unpack(Byte.toUnsignedInt(this.entityData.get(ORIENTATION)));
    }

    public final void setOrientation(PlasticEntityOrientation orientation) {
        this.entityData.set(ORIENTATION, (byte) Objects.requireNonNull(orientation, "orientation").pack());
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
        return copy;
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
    }

    protected abstract ItemStack createDefaultDropStack();

    protected abstract void openAnvilMenu(ServerPlayer player);

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
    protected void handleAnvilCraftLandingDamage(AnvilEvent.OnLand event) {
        this.applyAnvilCraftRecipeDamage(event.getPos());
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
        if (!this.accelerationPositionCapture || this.accelerationMoveInProgress) {
            super.setPos(x, y, z);
            return;
        }

        Vec3 requestedMovement = new Vec3(x - this.getX(), y - this.getY(), z - this.getZ());
        if (!Double.isFinite(requestedMovement.lengthSqr())
            || requestedMovement.lengthSqr()
                <= PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
            super.setPos(x, y, z);
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
    }

    @Override
    public void tick() {
        if (this.level().isClientSide) {
            // ClientLevel 会在每个实体执行刻逻辑前记录旧坐标。本地玩家可能在同一轮实体处理中更早推动该实体，
            // 因此保留推动前位置，让原版局部刻渲染器绘制这段位移，而不是将其隐藏。
            if (this.clientCarrierMoveGameTime == this.level().getGameTime()) {
                this.xOld = this.xo = this.clientCarrierMoveStartX;
                this.yOld = this.yo = this.clientCarrierMoveStartY;
                this.zOld = this.zo = this.clientCarrierMoveStartZ;
            }
            // 每个客户端刻消费一个已确认的服务端快照。随后渲染从 xOld 线性移动到当前位置，
            // 形成固定一刻的视觉延迟，且客户端无需积分重力、浮力、磁力或进行其他预测。
            if (this.clientSnapshotPending) {
                this.setPos(this.clientSnapshotX, this.clientSnapshotY, this.clientSnapshotZ);
                this.setRot(this.clientSnapshotYRot, this.clientSnapshotXRot);
                this.clientSnapshotPending = false;
            }
            // 仅重建本地玩家碰撞预测所需的支撑关系，实体运动仍由服务端快照决定。
            this.refreshClientSupportObservation();
            this.firstTick = false;
            return;
        }

        // FallingBlockEntity.tick 有意跳过 Entity#baseTick。
        // 此处保留通常由 baseTick 推进的插值和边沿触发状态。
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
        this.previousImpactContactMask = this.impactContactMask;
        this.impactContactMask = 0;
        // AnvilCraft 的传送门转换会直接写入 FallingBlockEntity#blockState。
        // 在服务端将这些外部变化同步回受追踪的展示状态。
        if (!this.level().isClientSide && !this.blockState.equals(this.getDisplayState())) {
            this.setDisplayState(this.blockState);
        }

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
        long sidePushAge = this.lastSidePushGameTime == Long.MIN_VALUE
            ? Long.MAX_VALUE
            : this.level().getGameTime() - this.lastSidePushGameTime;
        boolean deferSidePushVelocity = this.defersTransferredSidePushVelocity()
            && sidePushAge >= 0L
            && sidePushAge <= 1L;
        Vec3 deferredSidePush = deferSidePushVelocity ? this.sidePushVelocity : Vec3.ZERO;
        if (accelerationRequestedMovement.lengthSqr()
            > PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
            boolean hadBlockSupportBeforeAcceleration = gravityDirection != null
                && PlasticEntityPhysics.hasBlockSupport(this, boxBeforeAcceleration, gravityDirection);
            Entity accelerationSupport = gravityDirection == null
                ? null
                : PlasticEntityPhysics.findSupport(this, boxBeforeAcceleration, gravityDirection);
            Entity accelerationPostSupport = gravityDirection == null
                ? null
                : PlasticEntityPhysics.findSupport(this, gravityDirection);
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
        Entity support = gravityDirection == null
            ? null
            : PlasticEntityPhysics.findSupport(this, gravityDirection);
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
        this.moveWithCarrierDisplacement(observedCarrierMovement);
        if (this.isRemoved()) return;

        support = gravityDirection == null
            ? null
            : PlasticEntityPhysics.findSupport(this, gravityDirection);
        boolean hadBlockSupportBeforeMove = gravityDirection != null
            && PlasticEntityPhysics.hasBlockSupport(this, gravityDirection);
        boolean hadEntitySupportBeforeMove = support != null
            && PlasticEntityPhysics.hasImmediateEntityContact(this, support, gravityDirection);

        if (deferSidePushVelocity) {
            this.setDeltaMovement(
                this.getDeltaMovement().subtract(
                    PlasticEntityPhysics.tangentialMovement(
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
            : PlasticEntityPhysics.findSupport(this, gravityDirection);
        boolean entitySupportCollision = gravityDirection != null
            && entitySupportAfterMove != null
            && PlasticEntityPhysics.collidedAlongGravity(requestedMovement, actualMovement, gravityDirection)
            && !PlasticEntityPhysics.hasBlockSupport(this, gravityDirection);
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
            : PlasticEntityPhysics.findSupport(this, gravityDirection);
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

        PlasticEntityPhysics.pushSideEntities(
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

    private void refreshClientSupportObservation() {
        Direction gravityDirection = null;
        if (!this.isNoGravity() && !AccelerateManager.isControlledByRing(this)) {
            Vec3 gravity = GravityManager.getNetGravityVectorForFallingBlock(this);
            PlasticFluidPhysics.FluidContact fluidContact = PlasticFluidPhysics.sample(this);
            double buoyancy = this.isBuoyantInFluids()
                ? this.getFluidBuoyancyAcceleration(fluidContact, gravity)
                : 0.0D;
            gravityDirection = PlasticEntityPhysics.directionOrNull(gravity.add(0.0D, buoyancy, 0.0D));
        }
        Entity support = gravityDirection == null
            ? null
            : PlasticEntityPhysics.findSupport(this, gravityDirection);
        this.supportObservation = support == null
            ? null
            : PlasticEntityPhysics.SupportObservation.capture(support);
        this.supportDirection = support == null ? null : gravityDirection;
    }

    private void applySurfaceFriction(Direction gravityDirection) {
        if (gravityDirection == null || !PlasticEntityPhysics.hasBlockSupport(this, gravityDirection)) return;
        BlockPos supportPos = PlasticEntityPhysics.landingPosition(this, gravityDirection).relative(gravityDirection);
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

        int directionMask = PlasticEntityPhysics.directionMask(gravityDirection);
        boolean blockContact = PlasticEntityPhysics.hasBlockSupport(this, gravityDirection);
        boolean newImpact = blockContact
            && (this.blockContactMask & directionMask) == 0
            && this.impactTrackingArmed
            && PlasticEntityPhysics.collidedAlongGravity(requestedMovement, actualMovement, gravityDirection)
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
            || PlasticEntityPhysics.hasBlockSupport(this, gravityDirection)
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
        if (support instanceof HardenedResinCauldronEntity pot) {
            pot.processAnvilImpact(this, impactDirection);
        }
    }

    private void handleNewCollisionContacts(Vec3 requested, Vec3 actual) {
        int contacts = PlasticEntityPhysics.clippedDirectionMask(requested, actual, 0.04D);
        int newContacts = contacts & ~this.previousImpactContactMask & ~this.impactContactMask;
        this.impactContactMask |= contacts;
        if (newContacts == 0) return;
        for (Direction direction : Direction.values()) {
            if ((newContacts & PlasticEntityPhysics.directionMask(direction)) == 0) continue;
            this.playImpactSound(direction, Math.abs(requested.get(direction.getAxis())));
            if (PlasticEntityPhysics.hasBlockSupport(this, direction)) continue;
            Entity contact = PlasticEntityPhysics.findSupport(this, direction);
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
        BlockPos landingPos = PlasticEntityPhysics.landingPosition(this, gravityDirection);
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
        // 塑料实体之间的位移传递由 PlasticPushChain 以原子方式规划。
        // 若同时使用通用承载钩子，不但会重复链式移动，还会将真正的砧和釜冲击变成穿透推动。
        if (carrier instanceof AbstractPlasticEntity || this.carrierMoveInProgress) return false;
        Vec3 transferredMovement = this.carrierMovement(
            carrier,
            carrier.getBoundingBox(),
            requestedMovement
        );
        return transferredMovement != null
            && (this.isCurrentSupport(carrier) || this.transfersSidePushWithCarrier(carrier));
    }

    @Override
    public void plasticraft$moveWithCarrier(Entity carrier, Vec3 actualMovement) {
        if (actualMovement.lengthSqr() <= PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON
            || !PlasticEntityPhysics.isWithinCarryDistance(actualMovement)) return;

        boolean supportedCarrier = this.isCurrentSupport(carrier);
        Vec3 transferredMovement;
        if (supportedCarrier) {
            Vec3 normal = Vec3.atLowerCornerOf(this.supportDirection.getNormal());
            if (actualMovement.dot(normal) > PlasticEntityPhysics.FACE_EPSILON) return;
            transferredMovement = actualMovement;
        } else {
            AABB previousCarrierBox = carrier.getBoundingBox().move(actualMovement.scale(-1.0D));
            transferredMovement = this.carrierMovement(carrier, previousCarrierBox, actualMovement);
            if (transferredMovement == null) return;
        }

        if (!supportedCarrier) {
            PlasticPushChain.ClippedPlan clipped = PlasticPushChain.clip(this, carrier, transferredMovement);
            transferredMovement = clipped.movement();
            if (transferredMovement.lengthSqr()
                <= PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) return;
            clipped.plan().move(this, carrier);
        }
        Vec3 moved = this.moveWithCarrierDisplacement(transferredMovement);
        if (supportedCarrier) {
            this.supportObservation = PlasticEntityPhysics.SupportObservation.capture(carrier);
            return;
        }

        Direction gravityDirection = this.currentPushGravityDirection();
        this.lastSidePushGameTime = this.level().getGameTime();
        this.sidePushGravityDirection = gravityDirection;
        this.sidePushVelocity = this.adjustTransferredSidePushVelocity(
            carrier,
            PlasticEntityPhysics.tangentialMovement(moved, gravityDirection)
        );
        Vec3 normal = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        double normalVelocity = this.getDeltaMovement().dot(normal);
        this.setDeltaMovement(this.sidePushVelocity.add(normal.scale(normalVelocity)));
        this.hasImpulse = true;
        this.hurtMarked = true;
    }

    @Override
    public Vec3 plasticraft$clampCarrierMovement(Entity carrier, Vec3 requestedMovement) {
        if (carrier instanceof AbstractPlasticEntity) return requestedMovement;
        Vec3 targetMovement = this.carrierMovement(carrier, carrier.getBoundingBox(), requestedMovement);
        if (this.carrierMoveInProgress || targetMovement == null) return requestedMovement;
        Vec3 allowedTargetMovement;
        if (this.isCurrentSupport(carrier)) {
            List<VoxelShape> entityCollisions = this.level().getEntities(
                this,
                this.getBoundingBox().expandTowards(targetMovement).inflate(PlasticEntityPhysics.FACE_EPSILON),
                other -> !other.isRemoved()
                    && !other.isSpectator()
                    && other != carrier
                    && !other.isPassengerOfSameVehicle(carrier)
                    && this.canCollideWith(other)
            ).stream().map(other -> Shapes.create(other.getBoundingBox())).toList();
            allowedTargetMovement = Entity.collideBoundingBox(
                this,
                targetMovement,
                this.getBoundingBox(),
                this.level(),
                entityCollisions
            );
        } else {
            allowedTargetMovement = PlasticPushChain.clip(this, carrier, targetMovement).movement();
        }
        return PlasticEntityPhysics.clampCarrierMovement(
            requestedMovement,
            targetMovement,
            allowedTargetMovement
        );
    }

    private Vec3 carrierMovement(Entity carrier, AABB carrierBox, Vec3 requestedMovement) {
        if (!PlasticEntityPhysics.isWithinCarryDistance(requestedMovement)) return null;
        if (this.isCurrentSupport(carrier)
            && PlasticEntityPhysics.canMoveWithCarrier(this, carrier, this.supportDirection, requestedMovement)) {
            return requestedMovement;
        }
        Direction gravityDirection = this.currentPushGravityDirection();
        return PlasticEntityPhysics.sidePushMovement(
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
            this.move(MoverType.SELF, movement);
        } finally {
            this.setDeltaMovement(velocity);
            this.carrierMoveInProgress = false;
        }
        Vec3 actualMovement = this.position().subtract(start);
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
            // 数据包目标确认的是较早的服务端刻。将本地经过碰撞裁剪的玩家位移带到新位置，
            // 避免消费该快照时把实体拉回玩家体内；下一份服务端数据包仍为权威状态。
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
        // 玩家碰撞位移由承载预检直接传递。原版相互冲量会降低玩家速度，
        // 并在该位移之上再叠加一次小得多的推动。
        if (entity instanceof Player) return;
        // 相接的塑料实体静止时保持稳定。其主动位移由 PlasticPushChain 传播一次；
        // 原版相互冲量会在玩家开始推动前制造间隙。
        if (entity instanceof AbstractPlasticEntity) return;
        if (this.supportObservation != null && this.supportObservation.entityId().equals(entity.getUUID())) {
            return;
        }
        Direction gravityDirection = this.currentPushGravityDirection();
        if (!PlasticEntityPhysics.isSupportCandidate(this, entity, gravityDirection)
            && PlasticEntityPhysics.isSideContact(this, entity, gravityDirection)) {
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
        Direction direction = PlasticEntityPhysics.directionOrNull(
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
        this.sidePushVelocity = this.adjustTransferredSidePushVelocity(
            pusher,
            PlasticEntityPhysics.tangentialMovement(moved, gravityDirection)
        );
        Vec3 normal = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        double normalVelocity = this.getDeltaMovement().dot(normal);
        this.setDeltaMovement(this.sidePushVelocity.add(normal.scale(normalVelocity)));
        this.hasImpulse = true;
        this.hurtMarked = true;
    }

    /** 材料推动响应钩子，同时保留共用的防穿模移动。 */
    protected Vec3 adjustTransferredSidePushVelocity(Entity pusher, Vec3 transferredVelocity) {
        return transferredVelocity;
    }

    /** 硬化制品延迟应用承载位移，避免重复积分。 */
    protected boolean defersTransferredSidePushVelocity() {
        return true;
    }

    /** 侧面接触是否参与共用的连续承载移动。 */
    protected boolean transfersSidePushWithCarrier(Entity carrier) {
        return true;
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
        BlockPos occupiedPos = BlockPos.containing(this.getBoundingBox().getCenter());
        if (!player.getAbilities().mayBuild || !this.level().mayInteract(player, occupiedPos)) {
            return InteractionResult.PASS;
        }

        ItemStack drop = this.getDropStack();
        if (drop.isEmpty()) return InteractionResult.FAIL;
        if (this.level().isClientSide) return InteractionResult.SUCCESS;

        this.prepareAnvilHammerPickup(player);
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
        return false;
    }

    public final boolean supportsAnvilHammerOrientationMenu() {
        return this.supportsHammerRotation();
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
        BlockPos occupiedPos = BlockPos.containing(this.getBoundingBox().getCenter());
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
        this.setOrientation(changed);
        this.setPos(changed.entityPosition(occupiedPos, this.getBbWidth(), this.getBbHeight()));
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
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() && stack.getItem() instanceof AnvilHammerItem) {
            return this.plasticraft$useAnvilHammer(player, hand, this.nearestInteractionFace(location));
        }
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;

        if (this.anvilcraft$acceptMagnetization(player, stack)) {
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
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
        if (stack.getItem() instanceof BlockItem || stack.getItem() instanceof PipeBlockItem) {
            return stack.useOn(new UseOnContext(this.level(), player, hand, stack, hit));
        }
        return InteractionResult.PASS;
    }

    public final Direction nearestInteractionFace(Vec3 relativeLocation) {
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
        PlasticEntityOrientation orientation = this.getOrientation();
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
        this.effectiveGravityDirection = Direction.byName(tag.getString("EffectiveGravityDirection"));
        if (this.effectiveGravityDirection == null) {
            this.directionalFallDistance = 0.0F;
            this.blockContactMask = 0;
            this.impactTrackingArmed = false;
        }
    }
}
