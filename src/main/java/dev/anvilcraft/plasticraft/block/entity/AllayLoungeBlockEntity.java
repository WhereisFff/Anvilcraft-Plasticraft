package dev.anvilcraft.plasticraft.block.entity;

import com.mojang.serialization.Codec;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.allay.tool.AllayToolSupply;
import dev.anvilcraft.plasticraft.allay.AllayClearanceStrategy;
import dev.anvilcraft.plasticraft.allay.AllayLoungeAnimation;
import dev.anvilcraft.plasticraft.allay.AllayLoungePickupQueue;
import dev.anvilcraft.plasticraft.allay.AllayLoungeStatus;
import dev.anvilcraft.plasticraft.allay.AllayShortageStrategy;
import dev.anvilcraft.plasticraft.allay.AllayWorkRecord;
import dev.anvilcraft.plasticraft.allay.observation.ObservationChunkLoader;
import dev.anvilcraft.plasticraft.allay.transfer.AllayLoungeNetwork;
import dev.anvilcraft.plasticraft.allay.transfer.ConstructionTransferService;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintData;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobProgress;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobStore;
import dev.anvilcraft.plasticraft.blueprint.ConstructionPermission;
import dev.anvilcraft.plasticraft.blueprint.ConstructionTraffic;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.anvilcraft.plasticraft.init.PlasticraftMenuTypes;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockEntities;
import dev.anvilcraft.plasticraft.inventory.AllayLoungeMenu;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * 悦灵休息室。最多托管 16 只戴帽悦灵,另有 1 个结构磁盘槽。
 * 不接入电网,有无供电都能召回、出库与入库。破坏时把托管悦灵随休息室物品保存。
 */
public class AllayLoungeBlockEntity extends BlockEntity {
    public static final int HOST_CAPACITY = 16;
    public static final int DISK_SLOT = 0;
    public static final int DOCKING_DURATION_TICKS = 20;
    public static final double RECALL_RANGE = 16.0D;
    public static final int FORMATION_GRID_SIZE = 4;
    public static final double FORMATION_BASE_OFFSET_Y = 3.0D;
    private static final int FORMATION_LAYER_CAPACITY = FORMATION_GRID_SIZE * FORMATION_GRID_SIZE;
    private static final double FORMATION_SPACING = 1.2D;
    private static final double FORMATION_RING_RADIUS = 3.0D;
    private static final double FORMATION_LAYER_SPACING = 1.0D;
    private static final Codec<List<AllayWorkRecord>> HOSTS_CODEC = AllayWorkRecord.CODEC.listOf();

    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return AllayLoungeBlockEntity.isValidDisk(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            AllayLoungeBlockEntity.this.onDiskChanged();
            AllayLoungeBlockEntity.this.setChanged();
        }
    };
    private final List<AllayWorkRecord> hosted = new ArrayList<>();
    private final List<Player> viewers = new ArrayList<>();
    private final List<UUID> dockingQueue = new ArrayList<>();
    private final Map<UUID, Integer> dockingSlots = new HashMap<>();
    private final Map<UUID, Double> dockingDistances = new HashMap<>();
    private final AllayLoungePickupQueue pickups = new AllayLoungePickupQueue();
    private AllayLoungeStatus indicatorStatus = AllayLoungeStatus.IDLE;
    @Nullable
    private UUID indicatorJobId;
    private boolean indicatorJobStarted;
    private final AllayLoungeAnimation hatchAnimation = new AllayLoungeAnimation();
    private boolean hatchOpen;
    private long hatchOpenUntil;
    @Nullable
    private AllayWorkRecord dockingRecord;
    private int dockingProgress;
    private boolean dockingRunning;
    private long dockingSyncGameTime;
    private long dockingQueuePruneTime = Long.MIN_VALUE;
    private AllayShortageStrategy shortageStrategy = AllayShortageStrategy.PAUSE;
    private AllayClearanceStrategy clearanceStrategy = AllayClearanceStrategy.CLEAR_AREA;
    private boolean loading;
    /** 生存模式移除方块后才生成掉落物,先缓存破坏前的数据以跨过 onRemove 回调。 */
    @Nullable
    private CompoundTag removalData;
    @Nullable
    private UUID lastDiskJobId;
    /** 休息室放置者；无所有者时保持拒绝访问，不进行隐式认领。 */
    @Nullable
    private UUID owner;

    public AllayLoungeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public AllayLoungeBlockEntity(BlockPos pos, BlockState blockState) {
        this(PlasticraftBlockEntities.ALLAY_LOUNGE.get(), pos, blockState);
    }

    public static boolean isValidDisk(ItemStack stack) {
        return stack.is(ModItems.STRUCTURE_DISK.get()) && ConstructionBlueprintData.get(stack).isPresent();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AllayLoungeBlockEntity lounge) {
        lounge.tickHatch();
        lounge.tickDocking();
        lounge.retryDiskClaim();
        if (level instanceof ServerLevel serverLevel) {
            lounge.updateIndicatorStatus(serverLevel);
            ConstructionJob toolJob = lounge.diskJobId() == null ? null
                : ConstructionJobIndex.get(serverLevel).job(lounge.diskJobId());
            if (toolJob == null || !toolJob.isActive()) AllayToolSupply.returnHostedTools(lounge);
            if (lounge.pickups.promote(serverLevel.getGameTime())) lounge.sendDockingUpdate();
            // 运行时转运图不落盘;每次实体刻重新登记,覆盖热加载、区块重载及早于 onLoad 建立的节点
            AllayLoungeNetwork.register(lounge);
            ConstructionTransferService.tickLoungeTransit(serverLevel, lounge);
            ObservationChunkLoader.syncLoungeThrottled(lounge);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, AllayLoungeBlockEntity lounge) {
        lounge.hatchAnimation.tick(lounge.hatchOpen);
    }

    public float hatchOpenness(float partialTick) {
        return this.hatchAnimation.openness(partialTick);
    }

    private void tickHatch() {
        if (this.isBayBusy()) this.markHatchActivity();
        if (this.hatchOpen && this.level != null && this.level.getGameTime() >= this.hatchOpenUntil) {
            this.hatchOpen = false;
            this.level.playSound(null, this.worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE,
                SoundSource.BLOCKS, 0.6F, 1.2F);
            this.sendDockingUpdate();
        }
    }

    private void markHatchActivity() {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        this.hatchOpenUntil = serverLevel.getGameTime() + AllayLoungeAnimation.IDLE_TICKS;
        if (this.hatchOpen) return;
        this.hatchOpen = true;
        serverLevel.playSound(null, this.worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN,
            SoundSource.BLOCKS, 0.6F, 1.0F);
        this.sendDockingUpdate();
    }

    private void tickDocking() {
        if (this.dockingRecord == null && !this.dockingRunning) return;
        if (!this.dockingRunning) {
            this.dockingRunning = true;
            this.sendDockingUpdate();
        }
        this.dockingProgress++;
        this.setChanged();
        if (this.dockingProgress < DOCKING_DURATION_TICKS) return;
        if (this.dockingRecord == null) {
            this.dockingProgress = 0;
            this.dockingRunning = false;
            this.sendDockingUpdate();
            this.setChanged();
            return;
        }
        this.finishDocking();
    }

    private void finishDocking() {
        if (this.dockingRecord == null) return;
        if (this.level instanceof ServerLevel serverLevel) {
            AllayWorkRecord record = this.dockingRecord;
            if (!this.canHostRecord(record) || !this.storeHosted(record)) {
                this.spawnBound(serverLevel, this.dockApproachPoint().add(0.0D, 0.2D, 0.0D), record);
            }
        }
        this.dockingRecord = null;
        this.dockingProgress = 0;
        this.dockingRunning = false;
        this.sendDockingUpdate();
        this.setChanged();
        ObservationChunkLoader.syncLounge(this);
    }

    public record DockAssignment(boolean head, Vec3 target) {
    }

    public DockAssignment assignDockTarget(WorkingAllayEntity worker) {
        if (!worker.isAlive() || worker.isDeadOrDying() || !canHost(worker)) {
            return new DockAssignment(false, worker.position());
        }
        this.registerDocking(worker);
        UUID id = worker.getUUID();
        boolean head = !this.dockingQueue.isEmpty() && this.dockingQueue.getFirst().equals(id);
        if (head && this.canAcceptDocking()
            && worker.position().distanceToSqr(this.dockApproachPoint()) <= 2.25D) {
            this.markHatchActivity();
        }
        return head
            ? new DockAssignment(true, this.dockApproachPoint())
            : new DockAssignment(false, this.formationSlotPosition(this.dockingSlots.get(id)));
    }

    public void registerDocking(WorkingAllayEntity worker) {
        if (!worker.isAlive() || worker.isDeadOrDying() || !canHost(worker)) return;
        this.pruneDockingQueue();
        UUID id = worker.getUUID();
        if (!this.dockingQueue.contains(id)) {
            if (this.hosted.removeIf(record -> record.entityId().equals(id))) {
                this.setChanged();
            }
            this.dockingDistances.put(id, worker.position().distanceToSqr(this.dockApproachPoint()));
            int insertAt = 0;
            while (insertAt < this.dockingQueue.size()
                && this.compareDockingOrder(this.dockingQueue.get(insertAt), id) <= 0) {
                insertAt++;
            }
            this.dockingQueue.add(insertAt, id);
        }
        this.dockingSlots.computeIfAbsent(id, ignored -> this.allocateDockingSlot());
    }

    /** 死亡或永久移除中的悦灵不能继续占用入库队列。 */
    public void cancelDocking(WorkingAllayEntity worker) {
        UUID id = worker.getUUID();
        if (!this.dockingQueue.contains(id)) return;
        this.removeDockingWorker(id);
        if (this.level != null) ConstructionTraffic.release(this.level, id);
        this.setChanged();
    }

    private int compareDockingOrder(UUID first, UUID second) {
        int distance = Double.compare(
            this.dockingDistances.getOrDefault(first, Double.POSITIVE_INFINITY),
            this.dockingDistances.getOrDefault(second, Double.POSITIVE_INFINITY)
        );
        return distance != 0 ? distance : first.compareTo(second);
    }

    private void pruneDockingQueue() {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        long gameTime = serverLevel.getGameTime();
        if (this.dockingQueuePruneTime == gameTime) return;
        this.dockingQueuePruneTime = gameTime;
        this.dockingQueue.removeIf(id -> {
            Entity entity = serverLevel.getEntity(id);
            boolean remove = !(entity instanceof WorkingAllayEntity worker)
                || worker.isRemoved()
                || !worker.isAlive()
                || worker.isDeadOrDying()
                || !worker.isDockingTo(this.worldPosition);
            if (remove) {
                this.dockingSlots.remove(id);
                this.dockingDistances.remove(id);
                ConstructionTraffic.release(serverLevel, id);
            }
            return remove;
        });
    }

    private int allocateDockingSlot() {
        int slot = 0;
        while (this.dockingSlots.containsValue(slot)) {
            slot++;
        }
        return slot;
    }

    private Vec3 formationSlotPosition(int slot) {
        int layer = slot / FORMATION_LAYER_CAPACITY;
        int cell = slot % FORMATION_LAYER_CAPACITY;
        int side = cell / FORMATION_GRID_SIZE;
        int sideIndex = cell % FORMATION_GRID_SIZE;
        double along = (sideIndex - (FORMATION_GRID_SIZE - 1) / 2.0D) * FORMATION_SPACING;
        double dx = switch (side) {
            case 0 -> along;
            case 1 -> FORMATION_RING_RADIUS;
            case 2 -> -along;
            default -> -FORMATION_RING_RADIUS;
        };
        double dz = switch (side) {
            case 0 -> -FORMATION_RING_RADIUS;
            case 1 -> along;
            case 2 -> FORMATION_RING_RADIUS;
            default -> -along;
        };
        return new Vec3(
            this.worldPosition.getX() + 0.5D + dx,
            this.worldPosition.getY() + FORMATION_BASE_OFFSET_Y + layer * FORMATION_LAYER_SPACING,
            this.worldPosition.getZ() + 0.5D + dz
        );
    }

    public boolean tryDock(WorkingAllayEntity worker) {
        if (this.level == null || this.level.isClientSide) return false;
        if (!worker.isAlive() || worker.isDeadOrDying()) {
            this.cancelDocking(worker);
            return false;
        }
        if (!canHost(worker)) return false;
        if (this.isBayBusy()) return false;
        if (this.hosted.size() >= HOST_CAPACITY) return false;
        this.removeDockingWorker(worker.getUUID());
        worker.clearAssignment(false);
        AllayToolSupply.returnOnDock(worker, this);
        this.dockingRecord = worker.toWorkRecord();
        this.dockingProgress = 0;
        this.dockingRunning = true;
        this.markHatchActivity();
        this.sendDockingUpdate();
        this.setChanged();
        // 调用方随后就会销毁实体,休息室必须在此刻先接过观察覆盖,否则入库瞬间会断刻
        ObservationChunkLoader.syncLounge(this);
        return true;
    }

    private void removeDockingWorker(UUID id) {
        this.dockingQueue.remove(id);
        this.dockingSlots.remove(id);
        this.dockingDistances.remove(id);
    }

    public boolean canAcceptDocking() {
        return !this.isBayBusy() && this.hosted.size() < HOST_CAPACITY;
    }

    public boolean isBayBusy() {
        return this.dockingRecord != null || this.dockingRunning;
    }

    /** 网络入口：只召回当前休息室所有者或同队成员的悦灵。 */
    public int recallNearbyWorkers(ServerPlayer actor) {
        if (!ConstructionPermission.canUseLounge(actor, this)) return 0;
        UUID loungeOwner = this.owner;
        return loungeOwner == null ? 0 : recallNearbyWorkers(actor.server, loungeOwner);
    }

    /** 兼容内部调度与旧 GameTest 的可信入口。 */
    public int recallNearbyWorkers(UUID ownerId) {
        if (this.level instanceof ServerLevel level) {
            return recallNearbyWorkers(level.getServer(), ownerId);
        }
        return 0;
    }

    private int recallNearbyWorkers(MinecraftServer server, UUID ownerId) {
        if (this.level == null || this.level.isClientSide) return 0;
        this.pruneDockingQueue();
        int available = HOST_CAPACITY
            - this.hosted.size()
            - (this.dockingRecord == null ? 0 : 1)
            - this.dockingQueue.size();
        if (available <= 0) return 0;
        AABB range = new AABB(this.worldPosition).inflate(RECALL_RANGE);
        List<WorkingAllayEntity> workers = this.level.getEntitiesOfClass(WorkingAllayEntity.class, range);
        Vec3 approach = this.dockApproachPoint();
        workers.sort(Comparator
            .comparingDouble((WorkingAllayEntity worker) -> worker.position().distanceToSqr(approach))
            .thenComparing(WorkingAllayEntity::getUUID));
        int recalled = 0;
        for (WorkingAllayEntity worker : workers) {
            if (!worker.isAlive() || worker.isDeadOrDying()) continue;
            UUID workerOwner = worker.getOwner().orElse(null);
            if (workerOwner == null || !ConstructionPermission.areCollaborators(server, ownerId, workerOwner)) {
                continue;
            }
            if (worker.isDockingTo(this.worldPosition)) continue;
            if (worker.startDockingTo(this.worldPosition)) {
                recalled++;
                if (recalled >= available) break;
            }
        }
        return recalled;
    }

    public boolean releaseHosted(int index) {
        if (!(this.level instanceof ServerLevel serverLevel)) return false;
        if (index < 0 || index >= this.hosted.size()) return false;
        AllayWorkRecord record = this.hosted.remove(index);
        WorkingAllayEntity worker = this.spawnBound(serverLevel, this.releasePoint(), record);
        // GUI 放出是玩家明确解除托管，不能保留 home/origin/transit 触发自动回库或转运。
        worker.setHomeLounge(null);
        ConstructionTransferService.detachManualRelease(worker);
        // 手动解除托管不占通道，也不能覆盖正在入库的记录或重置自动出入库进度。
        this.markHatchActivity();
        this.setChanged();
        this.sendDockingUpdate();
        return true;
    }

    /** 网络入口：托管卡片只能由休息室所有者或同队成员操作。 */
    public boolean releaseHosted(ServerPlayer actor, int index, UUID entityId) {
        // 连续点选期间列表可能已移位，按点击时的身份重新定位，防止放出相邻卡片。
        if (index < 0 || index >= this.hosted.size() || !this.hosted.get(index).entityId().equals(entityId)) {
            index = this.hostedIndex(entityId);
        }
        if (!ConstructionPermission.canUseLounge(actor, this)
            || index < 0
            || index >= this.hosted.size()) {
            return false;
        }
        UUID recordOwner = this.hosted.get(index).owner().orElse(null);
        return recordOwner != null
            && this.owner != null
            && ConstructionPermission.areCollaborators(actor.server, recordOwner, this.owner)
            && releaseHosted(index);
    }

    public boolean tryLaunch(Predicate<AllayWorkRecord> match) {
        return this.tryLaunchFor(match) != null;
    }

    /** 出库并返回生成的悦灵实体(供转运转发继续编排),占 20 gt 出库通道。无匹配记录时返回 null。 */
    @Nullable
    public WorkingAllayEntity tryLaunchFor(Predicate<AllayWorkRecord> match) {
        if (!(this.level instanceof ServerLevel serverLevel)) return null;
        if (this.isBayBusy()) return null;
        for (int index = 0; index < this.hosted.size(); index++) {
            AllayWorkRecord record = this.hosted.get(index);
            if (!match.test(record)) continue;
            this.hosted.remove(index);
            WorkingAllayEntity worker = this.spawnBound(serverLevel, this.releasePoint(), record);
            this.occupyOutboundBay();
            return worker;
        }
        return null;
    }

    public boolean hasHosted(Predicate<AllayWorkRecord> match) {
        for (AllayWorkRecord record : this.hosted) {
            if (match.test(record)) return true;
        }
        return false;
    }

    /** 替换一条托管记录(转运字段清除等),找不到返回 false。 */
    public boolean updateHostedRecord(UUID entityId, UnaryOperator<AllayWorkRecord> update) {
        int index = this.hostedIndex(entityId);
        if (index < 0) return false;
        this.hosted.set(index, update.apply(this.hosted.get(index)));
        this.setChanged();
        ObservationChunkLoader.syncLounge(this);
        this.sendDockingUpdate();
        return true;
    }

    private void occupyOutboundBay() {
        this.dockingRecord = null;
        this.dockingProgress = 0;
        this.dockingRunning = true;
        this.markHatchActivity();
        this.setChanged();
        this.sendDockingUpdate();
    }

    public void releaseAllToWorld() {
        if (this.level instanceof ServerLevel serverLevel) {
            ConstructionJobController.unclaimLounge(serverLevel, this.worldPosition, this.diskJobId());
        }
        this.lastDiskJobId = null;
        this.clearDockingQueue();
        if (!(this.level instanceof ServerLevel serverLevel)) {
            this.dropDisk();
            return;
        }
        Vec3 spawn = this.releasePoint();
        if (this.dockingRecord != null) {
            this.spawnBound(serverLevel, spawn, this.dockingRecord);
            this.dockingRecord = null;
        }
        for (AllayWorkRecord record : this.hosted) {
            this.spawnBound(serverLevel, spawn, record);
        }
        this.hosted.clear();
        this.dropDisk();
        // 托管记录都已放回世界并各自持票,休息室这份覆盖才可以退掉
        ObservationChunkLoader.revokeLounge(serverLevel, this.worldPosition);
    }

    /** 破坏前保留可搬运数据,只解除世界级运行时引用,不把托管悦灵生成到世界。 */
    public void prepareForRemoval() {
        if (this.level == null || this.removalData != null) return;
        this.removalData = this.savePortableData(this.level.registryAccess());
        if (this.level instanceof ServerLevel serverLevel) {
            ConstructionJobController.unclaimLounge(serverLevel, this.worldPosition, this.diskJobId());
            this.lastDiskJobId = null;
            this.clearDockingQueue();
            ObservationChunkLoader.revokeLounge(serverLevel, this.worldPosition);
        }
    }

    public boolean hasPortableData() {
        return !this.hosted.isEmpty()
            || this.dockingRecord != null
            || !this.items.getStackInSlot(DISK_SLOT).isEmpty()
            || this.owner != null
            || this.shortageStrategy != AllayShortageStrategy.PAUSE
            || this.clearanceStrategy != AllayClearanceStrategy.CLEAR_AREA;
    }

    private void clearDockingQueue() {
        if (this.level != null) {
            for (UUID id : this.dockingQueue) {
                ConstructionTraffic.release(this.level, id);
            }
        }
        this.dockingQueue.clear();
        this.dockingSlots.clear();
        this.dockingDistances.clear();
    }

    private void dropDisk() {
        ItemStack disk = this.items.getStackInSlot(DISK_SLOT);
        if (disk.isEmpty() || this.level == null) return;
        Vec3 pos = Vec3.atCenterOf(this.worldPosition);
        this.level.addFreshEntity(new ItemEntity(this.level, pos.x, pos.y, pos.z, disk.copy()));
        this.items.setStackInSlot(DISK_SLOT, ItemStack.EMPTY);
    }

    private WorkingAllayEntity spawnBound(ServerLevel level, Vec3 pos, AllayWorkRecord record) {
        WorkingAllayEntity worker = WorkingAllayEntity.spawnFromRecord(level, pos, record);
        worker.setHomeLounge(this.worldPosition);
        if (!this.canHost(worker)) {
            worker.setHomeLounge(null);
        }
        // 先让出库的观察悦灵自己持票,再重算休息室覆盖,顺序反了就会在出库瞬间空窗
        ObservationChunkLoader.syncObserver(worker);
        ObservationChunkLoader.syncLounge(this);
        return worker;
    }

    private boolean canHostRecord(AllayWorkRecord record) {
        if (!(this.level instanceof ServerLevel level) || this.owner == null) return false;
        UUID recordOwner = record.owner().orElse(null);
        return recordOwner != null
            && ConstructionPermission.areCollaborators(level.getServer(), recordOwner, this.owner)
            && ConstructionPermission.canModify(level, this.worldPosition, this.owner);
    }

    public AllayShortageStrategy shortageStrategy() {
        return this.shortageStrategy;
    }

    public void setShortageStrategy(AllayShortageStrategy strategy) {
        this.shortageStrategy = strategy == null ? AllayShortageStrategy.PAUSE : strategy;
        this.setChanged();
        this.sendDockingUpdate();
        if (this.level instanceof ServerLevel serverLevel) {
            ConstructionJobController.onLoungeShortageStrategyChanged(
                serverLevel,
                this.worldPosition,
                this.shortageStrategy
            );
        }
    }

    /** 网络入口：缺料策略属于休息室设置，不能由陌生玩家修改。 */
    public boolean setShortageStrategy(ServerPlayer actor, AllayShortageStrategy strategy) {
        if (!ConstructionPermission.canUseLounge(actor, this)) return false;
        setShortageStrategy(strategy);
        return true;
    }

    public AllayClearanceStrategy clearanceStrategy() {
        return this.clearanceStrategy;
    }

    public void setClearanceStrategy(AllayClearanceStrategy strategy) {
        this.clearanceStrategy = strategy == null ? AllayClearanceStrategy.CLEAR_AREA : strategy;
        this.setChanged();
        this.sendDockingUpdate();
        if (this.level instanceof ServerLevel serverLevel) {
            ConstructionJobController.onLoungeClearanceStrategyChanged(
                serverLevel,
                this.worldPosition,
                this.clearanceStrategy
            );
        }
    }

    /** 网络入口：清场策略属于休息室设置，不能由陌生玩家修改。 */
    public boolean setClearanceStrategy(ServerPlayer actor, AllayClearanceStrategy strategy) {
        if (!ConstructionPermission.canUseLounge(actor, this)) return false;
        setClearanceStrategy(strategy);
        return true;
    }

    public void setPickupDisplay(ItemStack stack, UUID allayId) {
        long gameTime = this.level == null ? 0L : this.level.getGameTime();
        if (this.pickups.offer(allayId, stack, gameTime)) this.sendDockingUpdate();
        this.setChanged();
    }

    public void clearPickupDisplay(UUID allayId) {
        long gameTime = this.level == null ? 0L : this.level.getGameTime();
        if (this.pickups.remove(allayId, gameTime)) this.sendDockingUpdate();
        this.setChanged();
    }

    public void clearPickupDisplays() {
        if (this.pickups.clear()) this.sendDockingUpdate();
        this.setChanged();
    }

    public ItemStack pickupDisplay(Direction side) {
        return this.pickups.display(side);
    }

    public float pickupSlideProgress(Direction side, float partialTick) {
        long now = this.level == null ? 0L : this.level.getGameTime();
        long started = this.pickups.startedAt(side, now);
        return AllayLoungeAnimation.smoothStep(
            ((float) (now - started) + partialTick) / AllayLoungeAnimation.ITEM_SLIDE_TICKS
        );
    }

    public Map<Direction, ItemStack> pickupDisplays() {
        return this.pickups.displays();
    }

    public AllayLoungeStatus indicatorStatus() {
        return this.indicatorStatus;
    }

    private void updateIndicatorStatus(ServerLevel level) {
        UUID jobId = this.diskJobId();
        if (!Objects.equals(jobId, this.indicatorJobId)) {
            this.indicatorJobId = jobId;
            this.indicatorJobStarted = false;
            this.setChanged();
        }
        ConstructionJob job = jobId == null ? null : ConstructionJobIndex.get(level).job(jobId);
        if (job != null && !job.dimension().equals(level.dimension())) job = null;
        ConstructionJobProgress progress = job == null ? null : ConstructionJobStore.get(level).get(jobId);
        // INACTIVE 同时表示未启动和手动暂停，保留启动历史才能区分蓝灯与红灯。
        if (!this.indicatorJobStarted
            && (job != null && job.isActive() || progress != null && progress.planned())) {
            this.indicatorJobStarted = true;
            this.setChanged();
        }
        AllayLoungeStatus status = AllayLoungeStatus.forJob(job, this.indicatorJobStarted);
        if (status != this.indicatorStatus) {
            this.indicatorStatus = status;
            this.setChanged();
            this.sendDockingUpdate();
        }
    }

    @Nullable
    public UUID diskJobId() {
        return ConstructionBlueprintData.get(this.items.getStackInSlot(DISK_SLOT))
            .flatMap(ConstructionBlueprintData::jobId)
            .orElse(null);
    }

    public void clearDiskJobId(UUID jobId) {
        ItemStack disk = this.items.getStackInSlot(DISK_SLOT);
        ConstructionBlueprintData data = ConstructionBlueprintData.get(disk).orElse(null);
        if (data == null || data.jobId().filter(jobId::equals).isEmpty()) return;
        this.lastDiskJobId = null;
        ConstructionBlueprintData.set(disk, data.withoutJobId());
        this.setChanged();
        this.sendDockingUpdate();
    }

    private void onDiskChanged() {
        if (this.loading || this.level == null || this.level.isClientSide) return;
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        UUID now = this.diskJobId();
        if (Objects.equals(now, this.lastDiskJobId)) return;
        UUID previous = this.lastDiskJobId;
        if (previous != null && !previous.equals(now)) {
            ConstructionJobController.unclaimLounge(serverLevel, this.worldPosition, previous);
        }
        this.lastDiskJobId = null;
        this.retryDiskClaim();
    }

    /** 权限或团队关系暂时失败时，保留磁盘并在后续 tick 重新尝试认领。 */
    private void retryDiskClaim() {
        if (!(this.level instanceof ServerLevel serverLevel) || this.level.isClientSide) return;
        UUID now = this.diskJobId();
        if (now == null) {
            this.lastDiskJobId = null;
            return;
        }
        if (Objects.equals(now, this.lastDiskJobId)) return;
        if (ConstructionJobController.claimLounge(serverLevel, this.worldPosition, now)) {
            this.lastDiskJobId = now;
        }
    }

    public Vec3 dockApproachPoint() {
        return new Vec3(
            this.worldPosition.getX() + 0.5D,
            this.worldPosition.getY() + 1.05D,
            this.worldPosition.getZ() + 0.5D
        );
    }

    public Vec3 releasePoint() {
        return new Vec3(
            this.worldPosition.getX() + 0.5D,
            this.worldPosition.getY() + 1.2D,
            this.worldPosition.getZ() + 0.5D
        );
    }

    public boolean openMenu(ServerPlayer player) {
        if (!ConstructionPermission.canUseLounge(player, this)) return false;
        player.openMenu(
            new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new AllayLoungeMenu(
                    PlasticraftMenuTypes.ALLAY_LOUNGE.get(),
                    containerId,
                    inventory,
                    this
                ),
                Component.translatable("container.anvilcraftplasticraft.allay_lounge")
            ),
            buffer -> buffer.writeBlockPos(this.worldPosition)
        );
        return true;
    }

    @Nullable
    public UUID owner() {
        return this.owner;
    }

    public void setOwner(@Nullable UUID owner) {
        if (Objects.equals(this.owner, owner)) return;
        this.owner = owner;
        this.setChanged();
        this.sendDockingUpdate();
    }

    public boolean canHost(WorkingAllayEntity worker) {
        if (!(this.level instanceof ServerLevel level)) return false;
        UUID workerOwner = worker.getOwner().orElse(null);
        if (workerOwner == null) return false;
        return this.owner != null
            && ConstructionPermission.areCollaborators(level.getServer(), workerOwner, this.owner)
            && ConstructionPermission.canModify(level, this.worldPosition, this.owner);
    }

    public void addViewer(Player player) {
        if (!this.viewers.contains(player)) this.viewers.add(player);
    }

    public void removeViewer(Player player) {
        this.viewers.remove(player);
    }

    public ItemStackHandler items() {
        return this.items;
    }

    public List<AllayWorkRecord> hosted() {
        return List.copyOf(this.hosted);
    }

    public boolean addHosted(AllayWorkRecord record) {
        if (this.hosted.size() >= HOST_CAPACITY || this.hostedIndex(record.entityId()) >= 0) return false;
        this.hosted.add(record);
        this.setChanged();
        return true;
    }

    private boolean storeHosted(AllayWorkRecord record) {
        int existing = this.hostedIndex(record.entityId());
        if (existing >= 0) {
            this.hosted.set(existing, record);
            return true;
        }
        if (this.hosted.size() >= HOST_CAPACITY) return false;
        this.hosted.add(record);
        return true;
    }

    private int hostedIndex(UUID entityId) {
        for (int index = 0; index < this.hosted.size(); index++) {
            if (this.hosted.get(index).entityId().equals(entityId)) return index;
        }
        return -1;
    }

    @Nullable
    public AllayWorkRecord dockingRecord() {
        return this.dockingRecord;
    }

    public int dockingProgress() {
        return this.dockingProgress;
    }

    public boolean isDockingRunning() {
        return this.dockingRunning;
    }

    public float clientDockingProgress(float partialTick) {
        if (!this.dockingRunning || this.level == null) return this.dockingProgress;
        long elapsed = this.level.getGameTime() - this.dockingSyncGameTime;
        return this.dockingProgress + elapsed + partialTick;
    }

    private void sendDockingUpdate() {
        this.dockingSyncGameTime = this.level == null ? 0L : this.level.getGameTime();
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", this.items.serializeNBT(registries));
        tag.putString("ShortageStrategy", this.shortageStrategy.getSerializedName());
        tag.putString("ClearanceStrategy", this.clearanceStrategy.getSerializedName());
        HOSTS_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this.hosted)
            .resultOrPartial(error -> AnvilcraftPlasticraft.LOGGER.error("Failed to save lounge hosts: {}", error))
            .ifPresent(encoded -> tag.put("Hosted", encoded));
        this.saveDockingState(tag, registries);
        this.pickups.save(tag, registries, true);
        tag.putByte("LoungeStatus", this.indicatorStatus.id());
        tag.putBoolean("IndicatorJobStarted", this.indicatorJobStarted);
        if (this.indicatorJobId != null) tag.putUUID("IndicatorJobId", this.indicatorJobId);
        if (this.owner != null) {
            tag.putUUID("Owner", this.owner);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        CompoundTag tag = this.removalData == null
            ? this.level == null ? null : this.savePortableData(this.level.registryAccess())
            : this.removalData.copy();
        if (tag == null) return;
        BlockEntity.addEntityType(tag, this.getType());
        components.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
    }

    private CompoundTag savePortableData(HolderLookup.Provider registries) {
        CompoundTag tag = this.saveCustomOnly(registries);
        tag.remove("PickupDisplays");
        tag.remove("PickupDisplayStarts");
        tag.remove("PickupDisplayOwners");
        tag.remove("PickupDisplayQueue");
        tag.remove("PickupNextSide");
        tag.remove("LoungeStatus");
        tag.remove("IndicatorJobStarted");
        tag.remove("IndicatorJobId");
        if (!tag.contains("DockingAllay", Tag.TAG_COMPOUND)) {
            tag.remove("DockingProgress");
            tag.remove("DockingRunning");
        }
        return tag;
    }

    private void saveDockingState(CompoundTag tag, HolderLookup.Provider registries) {
        if (this.dockingRecord != null) {
            AllayWorkRecord.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this.dockingRecord)
                .resultOrPartial(error -> AnvilcraftPlasticraft.LOGGER.error("Failed to save docking allay: {}", error))
                .ifPresent(encoded -> tag.put("DockingAllay", encoded));
        }
        if (this.dockingRecord != null || this.dockingRunning) {
            tag.putInt("DockingProgress", this.dockingProgress);
            tag.putBoolean("DockingRunning", this.dockingRunning);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.loading = true;
        if (tag.contains("Items")) this.items.deserializeNBT(registries, tag.getCompound("Items"));
        this.lastDiskJobId = null;
        this.pickups.load(tag, registries);
        this.indicatorStatus = AllayLoungeStatus.fromId(tag.getByte("LoungeStatus"));
        this.indicatorJobStarted = tag.getBoolean("IndicatorJobStarted");
        this.indicatorJobId = tag.hasUUID("IndicatorJobId") ? tag.getUUID("IndicatorJobId") : null;
        this.hatchOpen = tag.getBoolean("HatchOpen");
        this.hatchOpenUntil = 0L;
        this.loading = false;
        this.shortageStrategy = AllayShortageStrategy.SKIP.getSerializedName().equals(tag.getString("ShortageStrategy"))
            ? AllayShortageStrategy.SKIP
            : AllayShortageStrategy.PAUSE;
        this.clearanceStrategy =
            AllayClearanceStrategy.KEEP_BLANK.getSerializedName().equals(tag.getString("ClearanceStrategy"))
                ? AllayClearanceStrategy.KEEP_BLANK
                : AllayClearanceStrategy.CLEAR_AREA;
        this.hosted.clear();
        if (tag.contains("Hosted")) {
            HOSTS_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("Hosted"))
                .resultOrPartial(error -> AnvilcraftPlasticraft.LOGGER.error("Failed to load lounge hosts: {}", error))
                .ifPresent(records -> {
                    Map<UUID, AllayWorkRecord> unique = new LinkedHashMap<>();
                    for (AllayWorkRecord record : records) {
                        unique.put(record.entityId(), record);
                    }
                    unique.values().stream().limit(HOST_CAPACITY).forEach(this.hosted::add);
                });
        }
        this.dockingRecord = null;
        this.dockingProgress = 0;
        this.dockingRunning = false;
        this.dockingQueue.clear();
        this.dockingSlots.clear();
        this.dockingDistances.clear();
        this.dockingQueuePruneTime = Long.MIN_VALUE;
        if (tag.contains("DockingAllay")) {
            AllayWorkRecord.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("DockingAllay"))
                .resultOrPartial(error -> AnvilcraftPlasticraft.LOGGER.error("Failed to load docking allay: {}", error))
                .ifPresent(record -> this.dockingRecord = record);
        }
        if (tag.contains("DockingProgress") || tag.contains("DockingRunning")) {
            this.dockingProgress = tag.getInt("DockingProgress");
            this.dockingRunning = tag.getBoolean("DockingRunning");
        }
        this.dockingSyncGameTime = this.level == null ? 0L : this.level.getGameTime();
        this.owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("HatchOpen", this.hatchOpen);
        tag.putByte("LoungeStatus", this.indicatorStatus.id());
        this.saveDockingState(tag, registries);
        HOSTS_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this.hosted)
            .resultOrPartial(error -> AnvilcraftPlasticraft.LOGGER.error("Failed to sync lounge hosts: {}", error))
            .ifPresent(encoded -> tag.put("Hosted", encoded));
        this.pickups.save(tag, registries, false);
        if (this.owner != null) {
            tag.putUUID("Owner", this.owner);
        }
        return tag;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel) {
            AllayLoungeNetwork.register(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        if (this.level instanceof ServerLevel serverLevel) {
            AllayLoungeNetwork.unregister(serverLevel, this.worldPosition);
        }
        super.onChunkUnloaded();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        this.removalData = null;
        if (this.level instanceof ServerLevel) {
            AllayLoungeNetwork.register(this);
        }
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel serverLevel) {
            AllayLoungeNetwork.unregister(serverLevel, this.worldPosition);
        }
        super.setRemoved();
    }

    @Override
    @Nullable
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
