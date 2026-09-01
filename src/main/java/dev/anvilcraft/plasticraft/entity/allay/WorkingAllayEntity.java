package dev.anvilcraft.plasticraft.entity.allay;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.allay.AllayFlightNavigator;
import dev.anvilcraft.plasticraft.allay.AllayFlightPlanner;
import dev.anvilcraft.plasticraft.allay.AllayFlightState;
import dev.anvilcraft.plasticraft.allay.AllayWorkMotions;
import dev.anvilcraft.plasticraft.allay.AllayHardHatTraits;
import dev.anvilcraft.plasticraft.allay.AllayHardHats;
import dev.anvilcraft.plasticraft.allay.AllayShortageStrategy;
import dev.anvilcraft.plasticraft.allay.AllayWorkRecord;
import dev.anvilcraft.plasticraft.allay.observation.ObservationChunkLoader;
import dev.anvilcraft.plasticraft.allay.transfer.ConstructionTransferService;
import dev.anvilcraft.plasticraft.allay.path.AllayPathPriority;
import dev.anvilcraft.plasticraft.allay.tool.AllayToolDefinition;
import dev.anvilcraft.plasticraft.allay.tool.AllayToolDefinitions;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.blueprint.ConstructionDebris;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import dev.anvilcraft.plasticraft.blueprint.ConstructionLeaseService;
import dev.anvilcraft.plasticraft.blueprint.ConstructionMaterialAccess;
import dev.anvilcraft.plasticraft.blueprint.ConstructionPermission;
import dev.anvilcraft.plasticraft.blueprint.ConstructionTraffic;
import dev.anvilcraft.plasticraft.blueprint.ConstructionWaitReason;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 戴上悦灵安全帽后的施工悦灵。空闲时复用原版游荡大脑与飞行动画;
 * 正在执行任务时改由工种调度器与三维寻路驱动。
 */
public class WorkingAllayEntity extends Allay {
    private static final EntityDataAccessor<ItemStack> DATA_HARD_HAT =
        SynchedEntityData.defineId(WorkingAllayEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Byte> DATA_ACTION_STATE =
        SynchedEntityData.defineId(WorkingAllayEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_FLIGHT_STATE =
        SynchedEntityData.defineId(WorkingAllayEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<ItemStack> DATA_CARRIED_ITEM =
        SynchedEntityData.defineId(WorkingAllayEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final byte CARRY_ORIGIN_UNKNOWN = 0;
    private static final byte CARRY_ORIGIN_ESCROW = 1;
    private static final byte CARRY_ORIGIN_FREE = 2;
    /** 放弃入库而丢出的物品要短暂禁止拾取,否则收集悦灵会立刻把它吸回来,原地反复丢捡同一堆物品。 */
    private static final int GIVE_UP_PICKUP_DELAY_TICKS = 100;

    private UUID owner;
    private AllayShortageStrategy shortageStrategy = AllayShortageStrategy.PAUSE;
    private List<ItemStack> collectionInventory = new ArrayList<>();
    /** 托管携带物的归属:施工余料要按台账返还,自由收集物要走收集卸货,只在携带物变化时重新判定以避免每刻扫描台账。 */
    private byte carryOrigin = CARRY_ORIGIN_UNKNOWN;
    @Nullable
    private BlockPos dockLoungePos;
    @Nullable
    private BlockPos homeLoungePos;
    /** 转运状态只记“真正的家”与正在前往的任务,与当前宿主(homeLounge)区分;沿链经每座宿主室时 homeLounge 被改写,origin/transit 不变,到达或返程时由转发规则清除。 */
    @Nullable
    private BlockPos originLoungePos;
    private Optional<UUID> transitJobId = Optional.empty();
    /** 世界内转运兜底的重试冷却(游戏刻)。 */
    private long nextTransitRetryTick = Long.MIN_VALUE;
    private Optional<UUID> assignedJobId = Optional.empty();
    private int taskOpId = -1;
    private ConstructionWaitReason waitReason = ConstructionWaitReason.NONE;
    private final AllayFlightNavigator navigator = new AllayFlightNavigator();
    /** 取放拆收等动手间隔,够到后先转向再执行。 */
    public static final int ACTION_INTERVAL_TICKS = 4;
    /** 与原版悦灵一致的飞行转向上限,保证交还控制权后的游荡手感不变。 */
    private static final int VANILLA_FLIGHT_TURN = 20;
    private static final float ACTION_TURN_STEP = 22.0F;
    private static final float ACTION_PITCH_STEP = 16.0F;
    private static final double ACTION_FACE_DOT = 0.85D;
    private static final int PROGRESS_SAMPLE_TICKS = 20;
    private static final double PROGRESS_DISTANCE_SQR = 0.25D;
    /** 被方块包住多久后强制脱困;取一秒,既让正常的擦碰自行恢复,也把窒息伤害压到一次以内。 */
    private static final int EMBEDDED_RESCUE_TICKS = 20;
    /** 脱困搜索的最大半径;整层墙体都压过来时也够跳到墙外。 */
    private static final int EMBEDDED_RESCUE_RADIUS = 6;
    private int lastActionTick;
    private boolean holdingForAction;
    @Nullable
    private Vec3 actionLookTarget;
    @Nullable
    private AllayFlightPlanner.FlightTask flightTask;
    private int stuckTicks;
    private int progressSampleTicks;
    /** 连续被方块包住的刻数,用于触发脱困。 */
    private int embeddedTicks;
    private Vec3 lastProgressPos = Vec3.ZERO;
    private boolean evacuating;
    @Nullable
    private Vec3 evacuationTarget;
    private boolean deathLootDropped;

    public WorkingAllayEntity(EntityType<? extends Allay> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setNoGravity(true);
        this.moveControl = new CommandAwareMoveControl(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HARD_HAT, ItemStack.EMPTY);
        builder.define(DATA_ACTION_STATE, (byte) 0);
        builder.define(DATA_FLIGHT_STATE, (byte) AllayFlightState.HOVERING.ordinal());
        builder.define(DATA_CARRIED_ITEM, ItemStack.EMPTY);
    }

    @Override
    public void aiStep() {
        this.setNoGravity(true);
        if (!this.level().isClientSide && this.isAlive() && !this.isDeadOrDying()) {
            // 自救必须先于工具行为:入库飞行等状态会直接 return,一旦被方块包住就没人来救
            this.serverEmbeddedTick();
            this.toolDefinition().behavior().serverTick(this);
            this.serverFlightTick();
            // 接管飞行时作废原版航线:否则原版寻路会在整个任务期间空跑重算,
            // 交还控制权后还会先沿一条早已过期的旧路径飞一段
            if (this.isCommanded()) {
                this.getNavigation().stop();
            }
        }
        super.aiStep();
        if (!this.level().isClientSide && this.holdingForAction && this.actionLookTarget != null) {
            this.turnToward(this.actionLookTarget);
        }
    }

    /**
     * 提交阶段把投影换成真实方块、或世界方块压在悦灵身上时的自救。
     * 碰撞钳制会把指令位移全部吃掉,寻路却认为自己一直在走,于是悦灵原地窒息到死;
     * 这里连续确认若干刻仍被包住就直接挪到最近的空位,并作废在途航线重新规划。
     */
    private void serverEmbeddedTick() {
        if (this.level().noBlockCollision(this, this.getBoundingBox())) {
            this.embeddedTicks = 0;
            return;
        }
        if (++this.embeddedTicks < EMBEDDED_RESCUE_TICKS) return;
        this.embeddedTicks = 0;
        Vec3 free = AllayFlightPlanner.escapeSolid(this, EMBEDDED_RESCUE_RADIUS);
        if (free == null) return;
        this.cancelFlightTask();
        this.navigator.clear();
        this.moveTo(free.x, free.y, free.z, this.getYRot(), this.getXRot());
        this.setDeltaMovement(Vec3.ZERO);
        this.resetStuck();
    }

    @Override
    protected void customServerAiStep() {
        if (this.isCommanded()) return;
        this.getBrain().eraseMemory(MemoryModuleType.LIKED_PLAYER);
        this.getBrain().eraseMemory(MemoryModuleType.LIKED_NOTEBLOCK_POSITION);
        super.customServerAiStep();
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isCommanded()) {
            this.move(MoverType.SELF, this.getDeltaMovement());
            if (this.flightState() == AllayFlightState.HOVERING && !this.isLeashed()) {
                this.setDeltaMovement(this.getDeltaMovement().scale(0.8D));
            }
            // 原版 Allay.travel 在移动后才会更新 walkAnimation;任务飞行也要走同一条。
            this.calculateEntityAnimation(false);
            return;
        }
        super.travel(travelVector);
    }

    @Override
    public boolean hasItemInHand() {
        return super.hasItemInHand() || !this.hostedCarry().isEmpty();
    }

    public boolean isCommanded() {
        return this.flightState() == AllayFlightState.DOCKING
            || this.flightState() == AllayFlightState.FLYING
            || this.assignedJobId.isPresent()
            || !this.hostedCarry().isEmpty()
            || this.hasCollectionItems()
            || this.waitReason != ConstructionWaitReason.NONE
            || this.holdingForAction
            || this.navigator.hasPath();
    }

    /**
     * 悬停并转向目标。间隔未满或还没对准时返回 false,调用方本 tick 不得取放拆收。
     */
    public boolean prepareAction(Vec3 lookTarget) {
        this.holdingForAction = true;
        this.actionLookTarget = lookTarget;
        AllayWorkMotions.holdStation(this);
        this.turnToward(lookTarget);
        if (this.tickCount - this.lastActionTick < ACTION_INTERVAL_TICKS || !this.isFacing(lookTarget)) {
            return false;
        }
        this.holdingForAction = false;
        this.actionLookTarget = null;
        this.lastActionTick = this.tickCount;
        return true;
    }

    public void clearActionHold() {
        this.holdingForAction = false;
        this.actionLookTarget = null;
    }

    private void turnToward(Vec3 target) {
        Vec3 delta = target.subtract(this.getEyePosition());
        if (delta.lengthSqr() < 1.0E-8D) return;
        float yaw = (float) (Mth.atan2(-delta.x, delta.z) * (180.0D / Math.PI));
        float pitch = (float) (
            Mth.atan2(-delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * (180.0D / Math.PI)
        );
        this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, ACTION_TURN_STEP));
        this.setYHeadRot(this.getYRot());
        this.yBodyRot = this.getYRot();
        this.setXRot(Mth.approachDegrees(this.getXRot(), pitch, ACTION_PITCH_STEP));
        this.getLookControl().setLookAt(target.x, target.y, target.z);
    }

    private boolean isFacing(Vec3 target) {
        Vec3 delta = target.subtract(this.getEyePosition());
        if (delta.lengthSqr() < 1.0E-6D) return true;
        return this.getLookAngle().dot(delta.normalize()) >= ACTION_FACE_DOT;
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return super.fireImmune() || AllayHardHatTraits.isFireResistant(this.getHardHat());
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean canBeLeashed() {
        return true;
    }

    private void serverFlightTick() {
        switch (this.flightState()) {
            case HOVERING -> {
            }
            case FLYING -> {
                if (!this.navigator.follow(this)) {
                    this.setDeltaMovement(Vec3.ZERO);
                    if (!this.hasPendingFlightTask()) {
                        this.setFlightState(AllayFlightState.HOVERING);
                    }
                }
            }
            case DOCKING -> this.serverDockingTick();
        }
    }

    private void serverDockingTick() {
        if (!this.isAlive() || this.isDeadOrDying()) {
            if (this.dockLoungePos != null
                && this.level().getBlockEntity(this.dockLoungePos) instanceof AllayLoungeBlockEntity lounge) {
                lounge.cancelDocking(this);
            }
            ConstructionTraffic.release(this.level(), this.getUUID());
            this.dockLoungePos = null;
            this.setHomeLounge(null);
            this.cancelFlightTask();
            this.setFlightState(AllayFlightState.HOVERING);
            return;
        }
        BlockPos loungePos = this.dockLoungePos;
        AllayLoungeBlockEntity lounge = loungePos != null
            && this.level().getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity found
            ? found
            : null;
        if (lounge == null || !lounge.canHost(this)) {
            ConstructionTraffic.release(this.level(), this.getUUID());
            this.dockLoungePos = null;
            this.setHomeLounge(null);
            this.cancelFlightTask();
            this.setFlightState(AllayFlightState.HOVERING);
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        AllayLoungeBlockEntity.DockAssignment assignment = lounge.assignDockTarget(this);
        Vec3 delta = assignment.target().subtract(this.position());
        if (delta.length() < 0.35D) {
            this.cancelFlightTask();
            this.navigator.clear();
            ConstructionTraffic.release(this.level(), this.getUUID());
            ConstructionTraffic.reserveLounge(
                this.level(),
                this.getUUID(),
                BlockPos.containing(assignment.target())
            );
            if (assignment.head() && lounge.canAcceptDocking() && lounge.tryDock(this)) {
                this.dockLoungePos = null;
                ConstructionTraffic.release(this.level(), this.getUUID());
                this.discard();
                return;
            }
            this.setDeltaMovement(delta.scale(0.2D));
            return;
        }
        ConstructionTraffic.reserveLounge(this.level(), this.getUUID(), BlockPos.containing(assignment.target()));
        AllayWorkMotions.flyTo(
            this,
            assignment.target(),
            assignment.head() ? AllayPathPriority.DOCK_HEAD : AllayPathPriority.LEAVE
        );
        if (this.flightTask != null && this.flightTask.repeatedlyUnreachable()) {
            Vec3 recall = AllayFlightPlanner.snapToFree(this, assignment.target());
            this.cancelFlightTask();
            this.navigator.clear();
            this.moveTo(recall.x, recall.y, recall.z, this.getYRot(), this.getXRot());
            this.setDeltaMovement(Vec3.ZERO);
            this.resetStuck();
            return;
        }
        if (!this.navigator.follow(this)) {
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    public boolean startDockingTo(BlockPos loungePos) {
        if (this.level().isClientSide || this.isRemoved() || !this.isAlive() || this.isDeadOrDying()) return false;
        if (this.flightState() == AllayFlightState.DOCKING) return false;
        if (!(this.level().getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity lounge)
            || !lounge.canHost(this)) {
            return false;
        }
        this.clearAssignment(false);
        this.dockLoungePos = loungePos.immutable();
        this.setHomeLounge(loungePos);
        this.setFlightState(AllayFlightState.DOCKING);
        lounge.registerDocking(this);
        return true;
    }

    public boolean isDockingTo(BlockPos loungePos) {
        return this.flightState() == AllayFlightState.DOCKING && loungePos.equals(this.dockLoungePos);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide
            && (!(player instanceof ServerPlayer serverPlayer) || !this.canBeManagedBy(serverPlayer))) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.LEAD) && this.canBeLeashed()) {
            return super.mobInteract(player, hand);
        }
        if (AllayHardHats.isHardHat(stack)) {
            if (!this.level().isClientSide) {
                ItemStack previous = this.getHardHat();
                this.setHardHat(stack.copyWithCount(1));
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                if (!previous.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(previous);
                }
                this.level().playSound(null, this.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 0.8F, 1.2F);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (player.isShiftKeyDown()) {
            if (!this.level().isClientSide) {
                this.unequipHat(player);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    public boolean unequipHat(Player player) {
        if (this.level().isClientSide) return false;
        if (!(player instanceof ServerPlayer serverPlayer) || !this.canBeManagedBy(serverPlayer)) return false;
        if (!this.hostedCarry().isEmpty() || this.hasCollectionItems()) return false;
        ItemStack hat = this.getHardHat();
        Allay vanilla = EntityType.ALLAY.create(this.level());
        if (vanilla == null) return false;
        UUID id = this.getUUID();
        vanilla.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
        copyPose(this, vanilla);
        vanilla.setUUID(id);
        vanilla.setCustomName(this.getCustomName());
        vanilla.setItemSlot(EquipmentSlot.MAINHAND, this.getMainHandItem().copy());
        vanilla.setPersistenceRequired();
        this.discard();
        this.level().addFreshEntity(vanilla);
        if (!hat.isEmpty()) {
            player.getInventory().placeItemBackInInventory(hat);
        }
        return true;
    }

    private boolean canBeManagedBy(ServerPlayer player) {
        return this.owner != null
            && ConstructionPermission.areCollaborators(
                player.server,
                this.owner,
                player.getUUID()
            )
            && ConstructionPermission.canPlayerModify(player, this.blockPosition());
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource damageSource) {
        if (this.deathLootDropped) return;
        this.deathLootDropped = true;
        super.dropAllDeathLoot(level, damageSource);
        this.spawnAtLocation(this.getHardHat());
        this.spawnAtLocation(this.hostedCarry());
        for (ItemStack stack : this.collectionInventory) {
            this.spawnAtLocation(stack);
        }
    }

    public static WorkingAllayEntity convertFrom(Allay source, ItemStack hat, @Nullable UUID owner) {
        Level level = source.level();
        WorkingAllayEntity worker = PlasticraftEntities.WORKING_ALLAY.get().create(level);
        if (worker == null) {
            throw new IllegalStateException("Failed to create working allay");
        }
        UUID id = source.getUUID();
        worker.moveTo(source.getX(), source.getY(), source.getZ(), source.getYRot(), source.getXRot());
        copyPose(source, worker);
        worker.setUUID(id);
        worker.setCustomName(source.getCustomName());
        worker.setItemSlot(EquipmentSlot.MAINHAND, source.getMainHandItem().copy());
        worker.setHardHat(hat.copyWithCount(1));
        if (owner != null) {
            worker.setOwner(owner);
        }
        worker.setPersistenceRequired();
        worker.setFlightState(AllayFlightState.HOVERING);
        source.discard();
        level.addFreshEntity(worker);
        return worker;
    }

    /**
     * 戴帽与脱帽都是"换一个实体",必须把姿态整体搬过去。
     * {@code moveTo} 只搬 yRot/xRot,渲染用的头部与身体朝向仍留在初始的 0 度(正南),
     * 生成包又是按头部朝向同时给客户端的身体与头部赋值,于是换装瞬间会硬生生转向;
     * 动量与落地状态一并继承,免得原地顿一下才恢复飞行。
     */
    private static void copyPose(Allay source, Allay target) {
        target.setYHeadRot(source.getYHeadRot());
        target.setYBodyRot(source.yBodyRot);
        target.yHeadRotO = source.yHeadRotO;
        target.yBodyRotO = source.yBodyRotO;
        target.setDeltaMovement(source.getDeltaMovement());
        target.setOnGround(source.onGround());
    }

    public AllayToolDefinition toolDefinition() {
        return AllayToolDefinitions.fromHeldItem(this.getMainHandItem());
    }

    public ItemStack getHardHat() {
        return this.entityData.get(DATA_HARD_HAT);
    }

    public void setHardHat(ItemStack stack) {
        this.entityData.set(DATA_HARD_HAT, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    public byte getActionState() {
        return this.entityData.get(DATA_ACTION_STATE);
    }

    public void setActionState(byte state) {
        this.entityData.set(DATA_ACTION_STATE, state);
    }

    public AllayFlightState flightState() {
        return AllayFlightState.byId(this.entityData.get(DATA_FLIGHT_STATE));
    }

    public void setFlightState(AllayFlightState state) {
        this.entityData.set(DATA_FLIGHT_STATE, (byte) state.ordinal());
    }

    public Optional<UUID> getOwner() {
        return Optional.ofNullable(this.owner);
    }

    public void setOwner(UUID ownerId) {
        this.owner = ownerId;
    }

    public AllayShortageStrategy shortageStrategy() {
        if (this.homeLoungePos != null
            && this.level().getBlockEntity(this.homeLoungePos) instanceof AllayLoungeBlockEntity lounge) {
            return lounge.shortageStrategy();
        }
        return AllayShortageStrategy.PAUSE;
    }

    public void setShortageStrategy(AllayShortageStrategy strategy) {
        this.shortageStrategy = strategy;
    }

    public void setHomeLounge(@Nullable BlockPos loungePos) {
        this.homeLoungePos = loungePos == null ? null : loungePos.immutable();
    }

    @Nullable
    public BlockPos homeLoungePos() {
        return this.homeLoungePos;
    }

    public void setOriginLounge(@Nullable BlockPos loungePos) {
        this.originLoungePos = loungePos == null ? null : loungePos.immutable();
    }

    @Nullable
    public BlockPos originLoungePos() {
        return this.originLoungePos;
    }

    public void setTransitJob(@Nullable UUID jobId) {
        this.transitJobId = jobId == null ? Optional.empty() : Optional.of(jobId);
    }

    public Optional<UUID> transitJobId() {
        return this.transitJobId;
    }

    /** 世界内转运兜底的重试冷却;由转运服务在重试前推进。 */
    public void setNextTransitRetryTick(long tick) {
        this.nextTransitRetryTick = tick;
    }

    /** 世界内转运兜底是否仍在冷却中。 */
    public boolean transitRetryCooldownActive(long gameTime) {
        return gameTime < this.nextTransitRetryTick;
    }

    public Optional<UUID> assignedJobId() {
        return this.assignedJobId;
    }

    public int taskOpId() {
        return this.taskOpId;
    }

    public void assign(UUID jobId, int opId) {
        if (this.assignedJobId.isPresent()
            && (!this.assignedJobId.get().equals(jobId) || this.taskOpId != opId)) {
            this.clearAssignment(false);
        }
        this.assignedJobId = Optional.of(jobId);
        this.taskOpId = opId;
    }

    public void clearAssignment(boolean clearCarry) {
        if (!this.level().isClientSide) {
            ConstructionLeaseService.release(this);
            ConstructionTraffic.release(this.level(), this.getUUID());
        }
        this.cancelFlightTask();
        this.assignedJobId = Optional.empty();
        this.taskOpId = -1;
        this.holdingForAction = false;
        this.actionLookTarget = null;
        this.navigator.clear();
        if (clearCarry) {
            this.setHostedCarry(ItemStack.EMPTY);
        }
    }

    public List<ItemStack> collectionInventory() {
        this.ensureCollectionSlots();
        return this.collectionInventory;
    }

    public boolean hasCollectionItems() {
        if (this.toolDefinition().inventorySize() <= 0) {
            return !this.hostedCarry().isEmpty() && !this.hasEscrowCarry();
        }
        for (ItemStack stack : this.collectionInventory) {
            if (!stack.isEmpty()) return true;
        }
        return false;
    }

    public boolean isCollectionFull() {
        if (this.toolDefinition().inventorySize() <= 0) {
            return !this.hostedCarry().isEmpty();
        }
        this.ensureCollectionSlots();
        for (ItemStack stack : this.collectionInventory) {
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) return false;
        }
        return true;
    }

    public boolean canAcceptCollection(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (this.toolDefinition().inventorySize() <= 0) {
            return this.hostedCarry().isEmpty();
        }
        this.ensureCollectionSlots();
        ItemStack probe = stack.copy();
        ConstructionDebris.clear(probe);
        for (ItemStack slot : this.collectionInventory) {
            if (slot.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(slot, probe) && slot.getCount() < slot.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    public int tryInsertCollection(ItemStack incoming) {
        if (incoming.isEmpty()) return 0;
        if (this.toolDefinition().inventorySize() <= 0) {
            if (!this.hostedCarry().isEmpty()) return 0;
            ItemStack take = incoming.copy();
            ConstructionDebris.clear(take);
            this.setHostedCarry(take.copyWithCount(1));
            this.carryOrigin = CARRY_ORIGIN_FREE;
            return 1;
        }
        this.ensureCollectionSlots();
        ItemStack moving = incoming.copy();
        ConstructionDebris.clear(moving);
        int before = moving.getCount();
        for (int index = 0; index < this.collectionInventory.size() && !moving.isEmpty(); index++) {
            ItemStack slot = this.collectionInventory.get(index);
            if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, moving)) continue;
            int space = slot.getMaxStackSize() - slot.getCount();
            if (space <= 0) continue;
            int take = Math.min(space, moving.getCount());
            slot.grow(take);
            moving.shrink(take);
        }
        for (int index = 0; index < this.collectionInventory.size() && !moving.isEmpty(); index++) {
            if (!this.collectionInventory.get(index).isEmpty()) continue;
            int take = Math.min(moving.getCount(), moving.getMaxStackSize());
            this.collectionInventory.set(index, moving.copyWithCount(take));
            moving.shrink(take);
        }
        return before - moving.getCount();
    }

    /** 返回是否已全部收进目标;塞不下的部分会落地,调用方据此给自己加冷却避免立刻吸回来。 */
    public boolean unloadCollectionTo(Player player) {
        if (this.toolDefinition().inventorySize() <= 0) {
            if (this.hostedCarry().isEmpty() || this.hasEscrowCarry()) return true;
            // 携带物是同步数据,必须先取副本再交给背包,否则 add 的就地缩减不会同步到客户端
            ItemStack carry = this.hostedCarry().copy();
            this.setHostedCarry(ItemStack.EMPTY);
            player.getInventory().add(carry);
            if (carry.isEmpty()) return true;
            dropBeside(player.level(), player.position(), carry);
            return false;
        }
        this.ensureCollectionSlots();
        boolean stored = true;
        for (int index = 0; index < this.collectionInventory.size(); index++) {
            ItemStack slot = this.collectionInventory.get(index);
            if (slot.isEmpty()) continue;
            player.getInventory().add(slot);
            if (!slot.isEmpty()) {
                dropBeside(player.level(), player.position(), slot.copy());
                stored = false;
            }
            this.collectionInventory.set(index, ItemStack.EMPTY);
        }
        return stored;
    }

    /** 返回是否已全部收进容器;溢出的部分会落在休息室旁,调用方据此给自己加冷却。 */
    public boolean unloadCollectionTo(ConstructionMaterialAccess access) {
        if (this.toolDefinition().inventorySize() <= 0) {
            if (this.hostedCarry().isEmpty() || this.hasEscrowCarry()) return true;
            ItemStack carry = this.hostedCarry().copy();
            this.setHostedCarry(ItemStack.EMPTY);
            return access.insertOrDrop(carry).isEmpty();
        }
        this.ensureCollectionSlots();
        boolean stored = true;
        for (int index = 0; index < this.collectionInventory.size(); index++) {
            ItemStack slot = this.collectionInventory.get(index);
            if (slot.isEmpty()) continue;
            stored &= access.insertOrDrop(slot.copy()).isEmpty();
            this.collectionInventory.set(index, ItemStack.EMPTY);
        }
        return stored;
    }

    /** 权限失效或来源不可用时，把临时收集库存安全实体化到指定位置。 */
    public void dropCollectionAt(Vec3 position) {
        if (this.toolDefinition().inventorySize() <= 0) {
            ItemStack carry = this.hostedCarry();
            if (!carry.isEmpty()) {
                dropBeside(this.level(), position, carry.copy());
                this.setHostedCarry(ItemStack.EMPTY);
            }
            return;
        }
        this.ensureCollectionSlots();
        for (int index = 0; index < this.collectionInventory.size(); index++) {
            ItemStack stack = this.collectionInventory.get(index);
            if (stack.isEmpty()) continue;
            dropBeside(this.level(), position, stack.copy());
            this.collectionInventory.set(index, ItemStack.EMPTY);
        }
    }

    private static void dropBeside(Level level, Vec3 pos, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity dropped = new ItemEntity(level, pos.x, pos.y, pos.z, stack);
        dropped.setPickUpDelay(GIVE_UP_PICKUP_DELAY_TICKS);
        level.addFreshEntity(dropped);
    }

    private void ensureCollectionSlots() {
        int size = this.toolDefinition().inventorySize();
        if (size <= 0) {
            this.collectionInventory.clear();
            return;
        }
        while (this.collectionInventory.size() < size) {
            this.collectionInventory.add(ItemStack.EMPTY);
        }
        if (this.collectionInventory.size() > size) {
            this.collectionInventory = new ArrayList<>(this.collectionInventory.subList(0, size));
        }
    }

    public ItemStack hostedCarry() {
        return this.entityData.get(DATA_CARRIED_ITEM);
    }

    public void setHostedCarry(ItemStack stack) {
        this.entityData.set(DATA_CARRIED_ITEM, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        this.carryOrigin = CARRY_ORIGIN_UNKNOWN;
    }

    /**
     * 携带物是否是施工台账里的材料或返还物。自由收集来的物品没有台账,
     * 必须交给收集卸货路径,否则既塞不进容器又会被反复丢在地上。
     */
    public boolean hasEscrowCarry() {
        if (this.hostedCarry().isEmpty()) return false;
        if (this.carryOrigin == CARRY_ORIGIN_UNKNOWN) {
            // 客户端没有台账,按施工余料显示即可;服务端只在携带物变化后判定一次
            if (this.level().isClientSide) return true;
            this.carryOrigin = ConstructionJobController.hasEscrowCarry(this)
                ? CARRY_ORIGIN_ESCROW
                : CARRY_ORIGIN_FREE;
        }
        return this.carryOrigin == CARRY_ORIGIN_ESCROW;
    }

    public ConstructionWaitReason waitReason() {
        return this.waitReason;
    }

    public void setWaitReason(ConstructionWaitReason reason) {
        this.waitReason = reason;
    }

    public AllayFlightPlanner.FlightTask ensureFlightTask(Vec3 goal, AllayPathPriority priority) {
        if (this.flightTask != null && this.flightTask.sameGoal(goal)) {
            this.flightTask.setPriority(priority);
            return this.flightTask;
        }
        this.cancelFlightTask();
        this.flightTask = new AllayFlightPlanner.FlightTask(this.getUUID(), goal, priority);
        return this.flightTask;
    }

    public boolean hasFlightTaskFor(Vec3 goal) {
        return this.flightTask != null && this.flightTask.sameGoal(goal);
    }

    public boolean hasPendingFlightTask() {
        return this.flightTask != null && this.flightTask.isPending();
    }

    public void cancelFlightTask() {
        if (this.flightTask != null) {
            this.flightTask.cancel();
            this.flightTask = null;
        }
    }

    public void noteProgress() {
        this.progressSampleTicks++;
        if (this.progressSampleTicks < PROGRESS_SAMPLE_TICKS) return;
        if (this.position().distanceToSqr(this.lastProgressPos) < PROGRESS_DISTANCE_SQR) {
            this.stuckTicks += this.progressSampleTicks;
        } else {
            this.stuckTicks = 0;
        }
        this.progressSampleTicks = 0;
        this.lastProgressPos = this.position();
    }

    public boolean isMotionStuck() {
        return this.stuckTicks >= 80;
    }

    public void resetStuck() {
        this.stuckTicks = 0;
        this.progressSampleTicks = 0;
        this.lastProgressPos = this.position();
    }

    public void beginEvacuation(Vec3 target) {
        this.evacuating = true;
        this.evacuationTarget = target;
    }

    public boolean isEvacuating() {
        return this.evacuating;
    }

    public @Nullable Vec3 evacuationTarget() {
        return this.evacuationTarget;
    }

    public void endEvacuation() {
        this.evacuating = false;
        this.evacuationTarget = null;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide) {
            boolean storedByLounge = reason == Entity.RemovalReason.DISCARDED
                && this.flightState() == AllayFlightState.DOCKING
                && this.dockLoungePos == null;
            if (reason.shouldDestroy()) {
                if (this.dockLoungePos != null
                    && this.level().getBlockEntity(this.dockLoungePos) instanceof AllayLoungeBlockEntity lounge) {
                    lounge.cancelDocking(this);
                }
                this.clearAssignment(false);
                if (!storedByLounge && !this.hostedCarry().isEmpty()) {
                    ConstructionLeaseService.markCarriesUntracked(this);
                }
                // 入栈托管只是转运途中的一站,不能当作离开世界去清在途台账
                if (!storedByLounge && this.level() instanceof ServerLevel serverLevel) {
                    ConstructionTransferService.onEntityRemove(serverLevel.getServer(), this.getUUID());
                }
                this.dockLoungePos = null;
                this.setHomeLounge(null);
            }
            // 换维度会以同一 UUID 在目标维度重建实体,旧维度这份九柱票没有持票人,必须在这里退掉
            if ((reason.shouldDestroy() || reason == Entity.RemovalReason.CHANGED_DIMENSION)
                && this.level() instanceof ServerLevel observerLevel) {
                ObservationChunkLoader.revokeObserver(observerLevel, this.getUUID());
            }
            this.cancelFlightTask();
            ConstructionTraffic.release(this.level(), this.getUUID());
        }
        super.remove(reason);
    }

    public AllayFlightNavigator navigator() {
        return this.navigator;
    }

    public AllayWorkRecord toWorkRecord() {
        return new AllayWorkRecord(
            this.getUUID(),
            this.getHardHat().copy(),
            this.getMainHandItem().copy(),
            Optional.ofNullable(this.owner),
            this.shortageStrategy,
            List.copyOf(this.collectionInventory),
            this.assignedJobId,
            this.hostedCarry().copy(),
            Optional.ofNullable(this.getCustomName()),
            this.originLoungePos == null ? Optional.empty() : Optional.of(this.originLoungePos.asLong()),
            this.transitJobId
        );
    }

    public void applyWorkRecord(AllayWorkRecord record) {
        this.setUUID(record.entityId());
        this.setHardHat(record.hardHat());
        this.setItemSlot(EquipmentSlot.MAINHAND, record.heldTool().copy());
        this.owner = record.owner().orElse(null);
        this.shortageStrategy = record.shortageStrategy();
        this.collectionInventory = new ArrayList<>(record.collectionInventory());
        this.assignedJobId = record.assignedJobId();
        this.setHostedCarry(record.hostedCarry());
        this.setCustomName(record.customName().orElse(null));
        this.originLoungePos = record.originLounge().map(BlockPos::of).orElse(null);
        this.transitJobId = record.transitJob();
        this.setPersistenceRequired();
        this.setFlightState(AllayFlightState.HOVERING);
    }

    public static WorkingAllayEntity spawnFromRecord(ServerLevel level, Vec3 pos, AllayWorkRecord record) {
        WorkingAllayEntity worker = PlasticraftEntities.WORKING_ALLAY.get().create(level);
        if (worker == null) {
            throw new IllegalStateException("Failed to create working allay");
        }
        worker.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        worker.applyWorkRecord(record);
        worker.clearAssignment(false);
        if (level.getEntity(record.entityId()) != null) {
            worker.setUUID(UUID.randomUUID());
        }
        level.addFreshEntity(worker);
        return worker;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        AllayWorkRecord.CODEC
            .encodeStart(this.registryAccess().createSerializationContext(NbtOps.INSTANCE), this.toWorkRecord())
            .resultOrPartial(error -> AnvilcraftPlasticraft.LOGGER.error("Failed to save working allay: {}", error))
            .ifPresent(encoded -> tag.put("AllayWork", encoded));
        tag.putByte("FlightState", (byte) this.flightState().ordinal());
        if (this.dockLoungePos != null) {
            tag.putLong("DockLounge", this.dockLoungePos.asLong());
        }
        if (this.homeLoungePos != null) {
            tag.putLong("HomeLounge", this.homeLoungePos.asLong());
        }
        tag.putInt("TaskOpId", this.taskOpId);
        tag.putByte("WaitReason", (byte) this.waitReason.ordinal());
        tag.put("Navigator", this.navigator.save());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("AllayWork")) {
            AllayWorkRecord.CODEC
                .parse(this.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag.get("AllayWork"))
                .resultOrPartial(error -> AnvilcraftPlasticraft.LOGGER.error("Failed to load working allay: {}", error))
                .ifPresent(this::applyWorkRecord);
        }
        if (tag.contains("FlightState")) {
            this.setFlightState(AllayFlightState.byId(tag.getByte("FlightState")));
        }
        this.dockLoungePos = tag.contains("DockLounge")
            ? BlockPos.of(tag.getLong("DockLounge"))
            : null;
        this.homeLoungePos = tag.contains("HomeLounge")
            ? BlockPos.of(tag.getLong("HomeLounge"))
            : null;
        this.taskOpId = tag.contains("TaskOpId") ? tag.getInt("TaskOpId") : -1;
        if (tag.contains("WaitReason")) {
            this.waitReason = ConstructionWaitReason.byId(tag.getByte("WaitReason"));
        }
        if (tag.contains("Navigator")) {
            this.navigator.load(tag.getCompound("Navigator"));
        }
    }

    /**
     * 执行任务时关掉原版飞行控制器,空闲时再交给它游荡。
     * 必须自己继承 {@link FlyingMoveControl} 而不是包装另一个实例:
     * 寻路与大脑把目标点写在悦灵当前挂载的移动控制器上,委派给另一个对象后那份目标点永远读不到,
     * 原版控制器的状态一直停在等待,空闲悦灵就再也不会飞。
     */
    private static final class CommandAwareMoveControl extends FlyingMoveControl {
        private final WorkingAllayEntity worker;

        private CommandAwareMoveControl(WorkingAllayEntity worker) {
            super(worker, VANILLA_FLIGHT_TURN, true);
            this.worker = worker;
        }

        @Override
        public void tick() {
            if (this.worker.isCommanded()) {
                // 丢掉指挥期间堆积的目标点与残留移动输入,交还控制权后不会先朝一个过期目标猛冲
                this.operation = MoveControl.Operation.WAIT;
                this.worker.setXxa(0.0F);
                this.worker.setYya(0.0F);
                this.worker.setZza(0.0F);
                return;
            }
            super.tick();
        }
    }
}
