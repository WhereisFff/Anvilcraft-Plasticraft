package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.allay.AllayClearanceStrategy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 一份施工任务的进度、材料台账与租约;与任务索引分离,避免把大进度塞进客户端同步的摘要 record。 */
public final class ConstructionJobProgress {
    private final UUID jobId;
    private boolean planned;
    private boolean incomplete;
    private ConstructionWaitReason waitReason = ConstructionWaitReason.NONE;
    private ConstructionWaitReason lastReported = ConstructionWaitReason.NONE;
    private ItemStack missingMaterial = ItemStack.EMPTY;
    private int missingOperationId = -1;
    private final List<ConstructionBuildOp> operations = new ArrayList<>();
    private final Map<Integer, ConstructionBuildOp> operationsById = new HashMap<>();
    private final Map<Long, List<ConstructionBuildOp>> operationsByPosition = new HashMap<>();
    private final List<ConstructionLedgerEntry> ledger = new ArrayList<>();
    private final Map<Integer, List<ConstructionLedgerEntry>> carriedLedgerByOperation = new HashMap<>();
    private final Map<UUID, List<ConstructionLedgerEntry>> carriedLedgerByAllay = new HashMap<>();
    private final List<ConstructionDebrisAccount> debris = new ArrayList<>();
    private final Map<UUID, UUID> debrisLeases = new HashMap<>();
    private final ConstructionCommitLog commitLog = new ConstructionCommitLog();
    @Nullable
    private BlockPos coordinatorLounge;
    private final Map<Long, BlockState> declaredTargets = new HashMap<>();
    private AllayClearanceStrategy clearanceStrategy = AllayClearanceStrategy.CLEAR_AREA;
    private boolean clearanceStrategyRecorded;
    private int nextOpId;
    private int nextLedgerId;
    private long enclosureRevision;
    private long operationOrderRevision;
    private long layoutRevision;
    private long statusRevision;
    private long topologyRevision;
    private long overlayCacheRevision = Long.MIN_VALUE;
    private Map<Long, BlockState> overlayCache = Map.of();
    private long deliveredCommitCacheRevision = Long.MIN_VALUE;
    private List<ConstructionBuildOp> deliveredCommitCache = List.of();
    private long deliveredEntityCacheRevision = Long.MIN_VALUE;
    private List<ConstructionBuildOp> deliveredEntityCache = List.of();
    private long deliveredCommitPositionCacheRevision = Long.MIN_VALUE;
    private Set<Long> deliveredCommitPositionCache = Set.of();
    private long childrenCacheRevision = Long.MIN_VALUE;
    private Map<Integer, List<ConstructionBuildOp>> childrenCache = Map.of();
    private long materialCacheRevision = Long.MIN_VALUE;
    private Map<Item, List<ConstructionBuildOp>> materialOperationCache = Map.of();
    private final Map<Item, Integer> materialFirstUnresolved = new HashMap<>();
    private boolean projectionIndexReady;

    /** 建造材料操作种类:{@link ConstructionBuildOp#isBuildMaterial()} 的种类清单。 */
    static final ConstructionBuildOp.Kind[] BUILD_MATERIAL_KINDS = {
        ConstructionBuildOp.Kind.PLACE,
        ConstructionBuildOp.Kind.ATTACHED,
        ConstructionBuildOp.Kind.CONTENT,
        ConstructionBuildOp.Kind.FLUID,
        ConstructionBuildOp.Kind.ENTITY,
        ConstructionBuildOp.Kind.DECORATE
    };
    /** 需要真实取料并按位置派发的建造种类,不含随核心交付的 ATTACHED。 */
    static final ConstructionBuildOp.Kind[] PLACE_MATERIAL_KINDS = {
        ConstructionBuildOp.Kind.PLACE,
        ConstructionBuildOp.Kind.CONTENT,
        ConstructionBuildOp.Kind.FLUID,
        ConstructionBuildOp.Kind.ENTITY,
        ConstructionBuildOp.Kind.DECORATE
    };
    /** 会占用出库额度的取料种类:建造材料加临时封堵填充。 */
    static final ConstructionBuildOp.Kind[] LAUNCH_MATERIAL_KINDS = {
        ConstructionBuildOp.Kind.PLACE,
        ConstructionBuildOp.Kind.CONTENT,
        ConstructionBuildOp.Kind.FLUID,
        ConstructionBuildOp.Kind.ENTITY,
        ConstructionBuildOp.Kind.DECORATE,
        ConstructionBuildOp.Kind.SEAL
    };

    private static final int BIT_OPEN = 1;
    private static final int BIT_SHELL = 1 << 1;
    private static final int BIT_LEASE_ALLAY = 1 << 2;
    private static final int BIT_WAITING_WORLD = 1 << 3;
    private static final int BIT_WAITING_OCCUPIED = 1 << 4;
    private static final int BIT_DELIVERED = 1 << 5;
    private static final int BIT_SKIPPED_PLACE = 1 << 6;
    private static final int BIT_UNLEASED = 1 << 7;
    /** 世界满足标记不进任何桶,但它决定提交表的成员,必须让贡献位变化以顶起摘要 revision。 */
    private static final int BIT_WORLD_SATISFIED = 1 << 8;

    /**
     * 未完成操作按种类分桶的增量索引。派发、出库判据和观察规划每刻都要问"还剩什么活",
     * 若每次都线性扫描整份蓝图,64 只悦灵下就是每刻数百万次访问;这里随状态变化就地增删。
     * DEMOLISH 另按核心与区外壳分开:壳在拆除阶段不派发,却可能占多数条目。
     */
    private final Map<ConstructionBuildOp.Kind, Map<Integer, ConstructionBuildOp>> openByKind =
        new EnumMap<>(ConstructionBuildOp.Kind.class);
    private final Map<Integer, ConstructionBuildOp> openDemolishCores = new LinkedHashMap<>();
    private final Map<Integer, ConstructionBuildOp> openDemolishShells = new LinkedHashMap<>();
    /** 当前持有租约的操作;上限是任务参与悦灵数,因此可以直接遍历。 */
    private final Map<Integer, ConstructionBuildOp> leasedOps = new LinkedHashMap<>();
    private final Map<Integer, ConstructionBuildOp> waitingWorldPlaces = new LinkedHashMap<>();
    private int waitingWorldCount;
    private int waitingOccupiedCount;
    private int deliveredCount;
    private int skippedPlaceCount;
    private int unleasedDemolishCount;
    private int unleasedMaterialCount;

    public ConstructionJobProgress(UUID jobId) {
        this.jobId = jobId;
        for (ConstructionBuildOp.Kind kind : ConstructionBuildOp.Kind.values()) {
            this.openByKind.put(kind, new LinkedHashMap<>());
        }
    }

    /**
     * 重新登记一项操作在各增量索引中的位置。状态、外壳标记、租约归属、世界满足标记以及该操作
     * 是否还有在途台账都会改变它属于哪些桶,统一由这里按贡献位差量维护,避免与集合分叉。
     */
    private void reindex(ConstructionBuildOp op) {
        int bits = this.indexBitsOf(op);
        int previous = op.indexBits();
        if (bits == previous) return;
        op.setIndexBits(bits);
        this.statusRevision++;
        ConstructionBuildOp.Kind kind = op.kind();
        boolean demolish = kind == ConstructionBuildOp.Kind.DEMOLISH;
        boolean open = (bits & BIT_OPEN) != 0;
        boolean shell = (bits & BIT_SHELL) != 0;
        index(this.openByKind.get(kind), op, open && !demolish);
        index(this.openDemolishCores, op, open && demolish && !shell);
        index(this.openDemolishShells, op, open && demolish && shell);
        index(this.leasedOps, op, (bits & BIT_LEASE_ALLAY) != 0);
        index(
            this.waitingWorldPlaces,
            op,
            (bits & BIT_WAITING_WORLD) != 0 && kind == ConstructionBuildOp.Kind.PLACE
        );
        this.waitingWorldCount += delta(previous, bits, BIT_WAITING_WORLD);
        this.waitingOccupiedCount += delta(previous, bits, BIT_WAITING_OCCUPIED);
        this.deliveredCount += delta(previous, bits, BIT_DELIVERED);
        this.skippedPlaceCount += delta(previous, bits, BIT_SKIPPED_PLACE);
        // 外壳标记会在登记之后才被规划器改写,因此"未认领的拆除核心"必须按新旧两组位一起判定,
        // 只看未认领位的差量会让一份先登记后标壳的操作永远多算一个待办
        this.unleasedDemolishCount += count(demolish && has(bits, BIT_UNLEASED) && !has(bits, BIT_SHELL))
            - count(demolish && has(previous, BIT_UNLEASED) && !has(previous, BIT_SHELL));
        if (isLaunchMaterial(op)) {
            this.unleasedMaterialCount += delta(previous, bits, BIT_UNLEASED);
        }
    }

    private int indexBitsOf(ConstructionBuildOp op) {
        ConstructionBuildOp.Status status = op.status();
        boolean open = op.isOpen();
        int bits = 0;
        if (open) bits |= BIT_OPEN;
        if (op.shell()) bits |= BIT_SHELL;
        if (op.leaseAllay().isPresent()) bits |= BIT_LEASE_ALLAY;
        if (status == ConstructionBuildOp.Status.WAITING_WORLD) bits |= BIT_WAITING_WORLD;
        if (status == ConstructionBuildOp.Status.WAITING_OCCUPIED) bits |= BIT_WAITING_OCCUPIED;
        if (status == ConstructionBuildOp.Status.DELIVERED) bits |= BIT_DELIVERED;
        if (status == ConstructionBuildOp.Status.SKIPPED && isPlaceMaterial(op)) bits |= BIT_SKIPPED_PLACE;
        if (op.worldSatisfied()) bits |= BIT_WORLD_SATISFIED;
        if (open
            && status != ConstructionBuildOp.Status.LEASED
            && op.leaseAllay().isEmpty()
            && !this.hasCarriedMaterial(op.id())) {
            bits |= BIT_UNLEASED;
        }
        return bits;
    }

    private static int delta(int previous, int bits, int mask) {
        return count(has(bits, mask)) - count(has(previous, mask));
    }

    private static boolean has(int bits, int mask) {
        return (bits & mask) != 0;
    }

    private static int count(boolean member) {
        return member ? 1 : 0;
    }

    private static void index(Map<Integer, ConstructionBuildOp> bucket, ConstructionBuildOp op, boolean member) {
        if (member) {
            bucket.put(op.id(), op);
        } else {
            bucket.remove(op.id());
        }
    }

    private void reindexOperation(int operationId) {
        ConstructionBuildOp op = this.operationsById.get(operationId);
        if (op != null) this.reindex(op);
    }

    /** 加载或整表重建后按当前状态重新登记全部操作。 */
    private void reindexAll() {
        this.clearIndexes();
        for (ConstructionBuildOp op : this.operations) {
            op.setIndexBits(0);
            this.reindex(op);
        }
    }

    private void clearIndexes() {
        for (Map<Integer, ConstructionBuildOp> bucket : this.openByKind.values()) {
            bucket.clear();
        }
        this.openDemolishCores.clear();
        this.openDemolishShells.clear();
        this.leasedOps.clear();
        this.waitingWorldPlaces.clear();
        this.waitingWorldCount = 0;
        this.waitingOccupiedCount = 0;
        this.deliveredCount = 0;
        this.skippedPlaceCount = 0;
        this.unleasedDemolishCount = 0;
        this.unleasedMaterialCount = 0;
    }

    public UUID jobId() {
        return this.jobId;
    }

    public boolean planned() {
        return this.planned;
    }

    public void setPlanned(boolean planned) {
        this.planned = planned;
    }

    public boolean incomplete() {
        return this.incomplete;
    }

    public void setIncomplete(boolean incomplete) {
        this.incomplete = incomplete;
    }

    public ConstructionWaitReason waitReason() {
        return this.waitReason;
    }

    public void setWaitReason(ConstructionWaitReason reason) {
        this.waitReason = reason;
    }

    public ConstructionWaitReason lastReported() {
        return this.lastReported;
    }

    public void setLastReported(ConstructionWaitReason reason) {
        this.lastReported = reason;
    }

    public ItemStack missingMaterial() {
        return this.missingMaterial;
    }

    public void setMissingMaterial(ItemStack missing) {
        this.missingMaterial = missing.isEmpty() ? ItemStack.EMPTY : missing.copy();
        if (missing.isEmpty()) {
            this.missingOperationId = -1;
        }
    }

    public int missingOperationId() {
        return this.missingOperationId;
    }

    public void setMissingOperationId(int operationId) {
        this.missingOperationId = operationId;
    }

    public List<ConstructionBuildOp> operations() {
        return this.operations;
    }

    public void clearOperations() {
        if (this.operations.isEmpty()) return;
        this.operations.clear();
        this.operationsById.clear();
        this.operationsByPosition.clear();
        this.clearIndexes();
        this.enclosureRevision++;
        this.operationOrderRevision++;
        this.invalidateLayout();
        this.invalidateStatus();
        this.invalidateTopology();
        this.projectionIndexReady = false;
    }

    /** 锚点改变时丢弃尚未产生实际施工进度的坐标计划，保留休息室认领关系。 */
    void resetPlan() {
        this.clearOperations();
        this.ledger.clear();
        this.carriedLedgerByOperation.clear();
        this.carriedLedgerByAllay.clear();
        this.debris.clear();
        this.debrisLeases.clear();
        this.commitLog.reset();
        this.planned = false;
        this.incomplete = false;
        this.waitReason = ConstructionWaitReason.NONE;
        this.lastReported = ConstructionWaitReason.NONE;
        this.missingMaterial = ItemStack.EMPTY;
        this.missingOperationId = -1;
        this.nextOpId = 0;
        this.nextLedgerId = 0;
        this.declaredTargets.clear();
        this.clearanceStrategy = AllayClearanceStrategy.CLEAR_AREA;
        this.clearanceStrategyRecorded = false;
        this.projectionIndexReady = false;
    }

    long enclosureRevision() {
        return this.enclosureRevision;
    }

    long operationOrderRevision() {
        return this.operationOrderRevision;
    }

    long layoutRevision() {
        return this.layoutRevision;
    }

    boolean projectionIndexReady() {
        return this.projectionIndexReady;
    }

    void setProjectionIndexReady(boolean ready) {
        this.projectionIndexReady = ready;
    }

    public List<ConstructionLedgerEntry> ledger() {
        return this.ledger;
    }

    public List<ConstructionDebrisAccount> debris() {
        return this.debris;
    }

    public ConstructionDebrisAccount debrisAccount(int operationId) {
        for (ConstructionDebrisAccount account : this.debris) {
            if (account.operationId() == operationId) return account;
        }
        ConstructionDebrisAccount created = new ConstructionDebrisAccount(operationId);
        this.debris.add(created);
        return created;
    }

    public void addDebrisSpawned(int operationId, int count) {
        this.debrisAccount(operationId).addSpawned(count);
    }

    public void addDebrisCollected(int operationId, int count) {
        this.debrisAccount(operationId).addCollected(count);
    }

    public void addDebrisExternal(int operationId, int count) {
        this.debrisAccount(operationId).addExternal(count);
    }

    public int debrisSpawned() {
        int total = 0;
        for (ConstructionDebrisAccount account : this.debris) {
            total += account.spawned();
        }
        return total;
    }

    public int debrisSpawned(int operationId) {
        for (ConstructionDebrisAccount account : this.debris) {
            if (account.operationId() == operationId) return account.spawned();
        }
        return 0;
    }

    public int debrisSettled() {
        int total = 0;
        for (ConstructionDebrisAccount account : this.debris) {
            total += account.collected() + account.external();
        }
        return total;
    }

    public int debrisSettled(int operationId) {
        for (ConstructionDebrisAccount account : this.debris) {
            if (account.operationId() == operationId) return account.collected() + account.external();
        }
        return 0;
    }

    @Nullable
    public UUID leasedDebrisEntity(UUID droneId) {
        for (Map.Entry<UUID, UUID> entry : this.debrisLeases.entrySet()) {
            if (droneId.equals(entry.getValue())) return entry.getKey();
        }
        return null;
    }

    public boolean leaseDebris(UUID entityId, UUID droneId) {
        UUID current = this.debrisLeases.get(entityId);
        if (current != null && !current.equals(droneId)) return false;
        this.debrisLeases.put(entityId, droneId);
        return true;
    }

    public void releaseDebrisLease(UUID entityId) {
        this.debrisLeases.remove(entityId);
    }

    public void releaseDebrisLeasesOf(UUID droneId) {
        this.debrisLeases.entrySet().removeIf(entry -> droneId.equals(entry.getValue()));
    }

    public void clearDebrisLeases() {
        this.debrisLeases.clear();
    }

    @Nullable
    public UUID debrisLease(UUID entityId) {
        return this.debrisLeases.get(entityId);
    }

    public ConstructionBuildOp addOperation(
        BlockPos pos,
        BlockState target,
        ItemStack material,
        ConstructionBuildOp.Kind kind,
        ConstructionBuildOp.Status status
    ) {
        ConstructionBuildOp op = new ConstructionBuildOp(
            this.nextOpId++,
            pos,
            target,
            material,
            kind,
            status,
            0
        );
        op.bindInvalidators(
            this::invalidateEnclosure,
            this::invalidateOperationOrder,
            this::invalidateLayout,
            () -> this.reindex(op),
            this::invalidateTopology
        );
        this.operations.add(op);
        this.operationsById.put(op.id(), op);
        this.operationsByPosition.computeIfAbsent(op.pos().asLong(), ignored -> new ArrayList<>()).add(op);
        this.reindex(op);
        this.invalidateEnclosure();
        this.invalidateOperationOrder();
        this.invalidateLayout();
        this.invalidateTopology();
        this.projectionIndexReady = false;
        return op;
    }

    public ConstructionBuildOp addOperation(
        BlockPos pos,
        BlockState target,
        ItemStack material,
        ConstructionBuildOp.Kind kind,
        ConstructionBuildOp.Status status,
        boolean shell
    ) {
        ConstructionBuildOp op = this.addOperation(pos, target, material, kind, status);
        op.setShell(shell);
        return op;
    }

    public ConstructionLedgerEntry addLedger(int operationId, ItemStack stack, @Nullable UUID droneId) {
        ConstructionLedgerEntry entry = new ConstructionLedgerEntry(
            this.nextLedgerId++,
            operationId,
            stack,
            droneId,
            ConstructionLedgerEntry.State.CARRIED
        );
        this.ledger.add(entry);
        this.indexCarriedEntry(entry);
        this.reindexOperation(operationId);
        return entry;
    }

    @Nullable
    public ConstructionBuildOp operation(int id) {
        return this.operationsById.get(id);
    }

    @Nullable
    public ConstructionBuildOp leasedBy(UUID droneId) {
        for (ConstructionBuildOp op : this.leasedOps.values()) {
            if (op.status() == ConstructionBuildOp.Status.LEASED
                && op.leaseAllay().filter(droneId::equals).isPresent()) {
                return op;
            }
        }
        return null;
    }

    public ConstructionCommitLog commitLog() {
        return this.commitLog;
    }

    @Nullable
    public BlockPos coordinatorLounge() {
        return this.coordinatorLounge;
    }

    public boolean hasCoordinator() {
        return this.coordinatorLounge != null;
    }

    public void setCoordinatorLounge(@Nullable BlockPos loungePos) {
        this.coordinatorLounge = loungePos == null ? null : loungePos.immutable();
    }

    /** 保存蓝图显式声明的格及目标状态，空状态也必须保留以区分稀疏空隙。 */
    public void setDeclaredTargets(Map<Long, BlockState> targets) {
        this.declaredTargets.clear();
        this.declaredTargets.putAll(targets);
    }

    public boolean hasDeclaredTarget(BlockPos pos) {
        return this.declaredTargets.containsKey(pos.asLong());
    }

    @Nullable
    public BlockState declaredTarget(BlockPos pos) {
        return this.declaredTargets.get(pos.asLong());
    }

    public Map<Long, BlockState> declaredTargets() {
        return Map.copyOf(this.declaredTargets);
    }

    public AllayClearanceStrategy clearanceStrategy() {
        return this.clearanceStrategy;
    }

    public boolean clearanceStrategyRecorded() {
        return this.clearanceStrategyRecorded;
    }

    public void setClearanceStrategy(AllayClearanceStrategy strategy) {
        this.clearanceStrategy = strategy == null
            ? AllayClearanceStrategy.CLEAR_AREA
            : strategy;
        this.clearanceStrategyRecorded = true;
    }

    @Nullable
    public ItemStack carriedBy(UUID allayId, int operationId) {
        for (ConstructionLedgerEntry entry : this.carriedLedgerByOperation.getOrDefault(operationId, List.of())) {
            if (allayId.equals(entry.allayId())) {
                return entry.stack();
            }
        }
        return null;
    }

    public boolean hasCarriedMaterial(int operationId) {
        return this.carriedLedgerByOperation.containsKey(operationId);
    }

    public boolean isCarriedBy(UUID allayId, int operationId) {
        return this.carriedLedgerByOperation.getOrDefault(operationId, List.of()).stream()
            .anyMatch(entry -> allayId.equals(entry.allayId()));
    }

    public List<ConstructionBuildOp> carriedOperations(UUID allayId) {
        List<ConstructionBuildOp> result = new ArrayList<>();
        for (ConstructionLedgerEntry entry : this.carriedLedgerByAllay.getOrDefault(allayId, List.of())) {
            ConstructionBuildOp op = this.operation(entry.operationId());
            if (op != null) result.add(op);
        }
        result.sort((left, right) -> {
            int order = Integer.compare(left.order(), right.order());
            return order != 0 ? order : Integer.compare(left.id(), right.id());
        });
        return List.copyOf(result);
    }

    public List<ConstructionLedgerEntry> carriedEntries(UUID allayId) {
        return List.copyOf(this.carriedLedgerByAllay.getOrDefault(allayId, List.of()));
    }

    public List<ConstructionLedgerEntry> carriedEntries(UUID allayId, int operationId) {
        List<ConstructionLedgerEntry> result = new ArrayList<>();
        for (ConstructionLedgerEntry entry : this.carriedLedgerByOperation.getOrDefault(operationId, List.of())) {
            if (allayId.equals(entry.allayId())) result.add(entry);
        }
        return List.copyOf(result);
    }

    /** 返回某项操作尚未结算的全部台账，供取消和权限撤销按操作逐项返还。 */
    public List<ConstructionLedgerEntry> unsettledEntries(int operationId) {
        List<ConstructionLedgerEntry> result = new ArrayList<>();
        for (ConstructionLedgerEntry entry : this.ledger) {
            if (entry.operationId() != operationId
                || entry.state() == ConstructionLedgerEntry.State.RETURNED) {
                continue;
            }
            result.add(entry);
        }
        return List.copyOf(result);
    }

    public void markOperationDelivered(int operationId) {
        for (ConstructionLedgerEntry entry : List.copyOf(
            this.carriedLedgerByOperation.getOrDefault(operationId, List.of())
        )) {
            this.transitionCarry(entry, ConstructionLedgerEntry.State.DELIVERED);
        }
    }

    public boolean markCarryReturned(ConstructionLedgerEntry entry) {
        return this.transitionCarry(entry, ConstructionLedgerEntry.State.RETURNED);
    }

    /** 将已发布但随后撤销的操作台账标记为已返还，不影响仍处于在途索引的 CARRIED 条目。 */
    public boolean markDeliveredReturned(int operationId) {
        boolean changed = false;
        for (ConstructionLedgerEntry entry : this.ledger) {
            if (entry.operationId() != operationId
                || entry.state() != ConstructionLedgerEntry.State.DELIVERED) {
                continue;
            }
            entry.setState(ConstructionLedgerEntry.State.RETURNED);
            changed = true;
        }
        if (changed) this.invalidateStatus();
        return changed;
    }

    public boolean markCarriesReturned(UUID allayId) {
        boolean changed = false;
        for (ConstructionLedgerEntry entry : List.copyOf(
            this.carriedLedgerByAllay.getOrDefault(allayId, List.of())
        )) {
            changed |= this.transitionCarry(entry, ConstructionLedgerEntry.State.RETURNED);
        }
        return changed;
    }

    List<ConstructionBuildOp> operationsForMaterial(ItemStack material) {
        this.ensureMaterialOperationCache();
        List<ConstructionBuildOp> operations = this.materialOperationCache.getOrDefault(material.getItem(), List.of());
        int first = this.materialFirstUnresolved.getOrDefault(material.getItem(), 0);
        while (first < operations.size()) {
            ConstructionBuildOp op = operations.get(first);
            if (op.status() != ConstructionBuildOp.Status.DELIVERED
                && op.status() != ConstructionBuildOp.Status.SKIPPED) {
                break;
            }
            first++;
        }
        this.materialFirstUnresolved.put(material.getItem(), first);
        return first == 0 ? operations : operations.subList(first, operations.size());
    }

    @Nullable
    public ConstructionBuildOp parentOf(ConstructionBuildOp child) {
        if (child.parentId() < 0) {
            return null;
        }
        return this.operation(child.parentId());
    }

    public List<ConstructionBuildOp> childrenOf(ConstructionBuildOp parent) {
        this.ensureChildrenCache();
        return this.childrenCache.getOrDefault(parent.id(), List.of());
    }

    List<ConstructionBuildOp> operationsAt(BlockPos pos) {
        return this.operationsByPosition.getOrDefault(pos.asLong(), List.of());
    }

    public Map<Long, BlockState> overlayStates() {
        if (this.overlayCacheRevision == this.layoutRevision) {
            return this.overlayCache;
        }
        Map<Long, BlockState> overlay = new HashMap<>();
        for (ConstructionBuildOp op : this.operations) {
            if (!op.writesProjection() || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                continue;
            }
            overlay.put(op.pos().asLong(), op.target());
        }
        this.overlayCache = Map.copyOf(overlay);
        this.overlayCacheRevision = this.layoutRevision;
        return this.overlayCache;
    }

    private void invalidateEnclosure() {
        this.enclosureRevision++;
    }

    private void invalidateOperationOrder() {
        this.operationOrderRevision++;
    }

    private void invalidateLayout() {
        this.layoutRevision++;
    }

    private void invalidateStatus() {
        this.statusRevision++;
    }

    private void invalidateTopology() {
        this.topologyRevision++;
    }

    private void ensureChildrenCache() {
        if (this.childrenCacheRevision == this.topologyRevision) return;
        Map<Integer, List<ConstructionBuildOp>> children = new HashMap<>();
        for (ConstructionBuildOp op : this.operations) {
            if (op.parentId() < 0) continue;
            children.computeIfAbsent(op.parentId(), ignored -> new ArrayList<>()).add(op);
        }
        children.replaceAll((ignored, value) -> List.copyOf(value));
        this.childrenCache = Map.copyOf(children);
        this.childrenCacheRevision = this.topologyRevision;
    }

    private void ensureMaterialOperationCache() {
        if (this.materialCacheRevision == this.operationOrderRevision) return;
        Map<Item, List<ConstructionBuildOp>> byItem = new HashMap<>();
        for (ConstructionBuildOp op : this.operations) {
            if (op.material().isEmpty()) continue;
            byItem.computeIfAbsent(op.material().getItem(), ignored -> new ArrayList<>()).add(op);
        }
        for (List<ConstructionBuildOp> operations : byItem.values()) {
            operations.sort((left, right) -> {
                int order = Integer.compare(left.order(), right.order());
                return order != 0 ? order : Integer.compare(left.id(), right.id());
            });
        }
        byItem.replaceAll((ignored, operations) -> List.copyOf(operations));
        this.materialOperationCache = Map.copyOf(byItem);
        this.materialFirstUnresolved.clear();
        this.materialCacheRevision = this.operationOrderRevision;
    }

    private void indexCarriedEntry(ConstructionLedgerEntry entry) {
        if (entry.state() != ConstructionLedgerEntry.State.CARRIED) return;
        this.carriedLedgerByOperation.computeIfAbsent(entry.operationId(), ignored -> new ArrayList<>()).add(entry);
        if (entry.allayId() != null) {
            this.carriedLedgerByAllay.computeIfAbsent(entry.allayId(), ignored -> new ArrayList<>()).add(entry);
        }
    }

    private boolean transitionCarry(ConstructionLedgerEntry entry, ConstructionLedgerEntry.State state) {
        if (entry.state() != ConstructionLedgerEntry.State.CARRIED) return false;
        entry.setState(state);
        removeIndexedEntry(this.carriedLedgerByOperation, entry.operationId(), entry);
        if (entry.allayId() != null) {
            removeIndexedEntry(this.carriedLedgerByAllay, entry.allayId(), entry);
        }
        this.reindexOperation(entry.operationId());
        this.invalidateStatus();
        return true;
    }

    private static <K> void removeIndexedEntry(
        Map<K, List<ConstructionLedgerEntry>> index,
        K key,
        ConstructionLedgerEntry entry
    ) {
        List<ConstructionLedgerEntry> entries = index.get(key);
        if (entries == null) return;
        entries.remove(entry);
        if (entries.isEmpty()) index.remove(key);
    }

    public boolean hasNonEmptyProgress() {
        if (!this.planned) return false;
        for (ConstructionBuildOp op : this.operations) {
            if (op.status() == ConstructionBuildOp.Status.DELIVERED
                || op.status() == ConstructionBuildOp.Status.LEASED
                || op.leaseAllay().isPresent()) {
                return true;
            }
        }
        for (ConstructionLedgerEntry entry : this.ledger) {
            if (entry.state() == ConstructionLedgerEntry.State.CARRIED) return true;
        }
        return false;
    }

    public boolean allSealResolved() {
        return !this.hasOpenSeal();
    }

    public boolean allDemolishResolved() {
        return this.openDemolishCores.isEmpty();
    }

    public boolean hasOpenSeal() {
        return !this.openByKind.get(ConstructionBuildOp.Kind.SEAL).isEmpty();
    }

    public boolean hasOpenDemolish() {
        return !this.openDemolishCores.isEmpty();
    }

    public boolean hasLeasedDemolish() {
        for (ConstructionBuildOp op : this.leasedOps.values()) {
            if (op.kind() == ConstructionBuildOp.Kind.DEMOLISH && !op.shell()) return true;
        }
        return false;
    }

    public boolean allPlaceResolved() {
        for (ConstructionBuildOp.Kind kind : BUILD_MATERIAL_KINDS) {
            if (!this.openByKind.get(kind).isEmpty()) return false;
        }
        return true;
    }

    public boolean hasDelivered() {
        return this.deliveredCount > 0;
    }

    public boolean hasOpenPlace() {
        for (ConstructionBuildOp.Kind kind : PLACE_MATERIAL_KINDS) {
            if (!this.openByKind.get(kind).isEmpty()) return true;
        }
        return false;
    }

    /** 是否有需要真实取料的建造操作被记为跳过,用于区分完成与残缺完成。 */
    public boolean hasSkippedPlaceMaterial() {
        return this.skippedPlaceCount > 0;
    }

    /**
     * 某一种类当前未完成(既非已交付也非已跳过)的操作。返回的是索引视图,遍历中若要改状态先自行复制。
     * DEMOLISH 不走这里,拆除核心与区外壳分别用 {@link #openDemolitionCores()} 与
     * {@link #openDemolitionShells()}。
     */
    public Collection<ConstructionBuildOp> openOperations(ConstructionBuildOp.Kind kind) {
        if (kind == ConstructionBuildOp.Kind.DEMOLISH) {
            throw new IllegalArgumentException("Use openDemolitionCores/openDemolitionShells for DEMOLISH");
        }
        return Collections.unmodifiableCollection(this.openByKind.get(kind).values());
    }

    /** 未完成的拆除操作,不含留到提交后再砸的区外封堵壳。 */
    public Collection<ConstructionBuildOp> openDemolitionCores() {
        return Collections.unmodifiableCollection(this.openDemolishCores.values());
    }

    /** 未完成的区外封堵壳拆除,拆除阶段不派发。 */
    public Collection<ConstructionBuildOp> openDemolitionShells() {
        return Collections.unmodifiableCollection(this.openDemolishShells.values());
    }

    /** 当前持有租约的操作,上限是任务参与悦灵数。 */
    public Collection<ConstructionBuildOp> leasedOperations() {
        return Collections.unmodifiableCollection(this.leasedOps.values());
    }

    /**
     * 剩余的建造位置是否全都压在不可达退避里。退避到期时间随游戏刻变化,不能进增量索引,
     * 因此这里实扫未完成桶;只有整个任务确实停下来了才对外报不可达,免得别处照常施工时误报。
     */
    public boolean stalledByUnreachable(long gameTime) {
        boolean anyDeferred = false;
        for (ConstructionBuildOp.Kind kind : PLACE_MATERIAL_KINDS) {
            for (ConstructionBuildOp op : this.openByKind.get(kind).values()) {
                if (!op.isDeferred(gameTime)) return false;
                anyDeferred = true;
            }
        }
        return anyDeferred;
    }

    int unleasedDemolishCount() {
        return this.unleasedDemolishCount;
    }

    int unleasedMaterialCount() {
        return this.unleasedMaterialCount;
    }

    boolean hasWaitingWorld() {
        return this.waitingWorldCount > 0;
    }

    boolean hasWaitingOccupied() {
        return this.waitingOccupiedCount > 0;
    }

    /** 等待真实世界让位的 PLACE 操作;每刻复查只需要这一小撮,不必扫整份蓝图。 */
    List<ConstructionBuildOp> waitingWorldPlaces() {
        return List.copyOf(this.waitingWorldPlaces.values());
    }

    /** 已租出的墙体与封堵操作,供封口串行判定;上限是任务参与悦灵数。 */
    List<ConstructionBuildOp> leasedWallOperations() {
        List<ConstructionBuildOp> walls = new ArrayList<>();
        for (ConstructionBuildOp op : this.leasedOps.values()) {
            if (op.kind() == ConstructionBuildOp.Kind.PLACE || op.kind() == ConstructionBuildOp.Kind.SEAL) {
                walls.add(op);
            }
        }
        return walls;
    }

    /** 已交付投影加上由真实世界方块满足、仍需提交子内容的方块锚点。 */
    List<ConstructionBuildOp> deliveredCommitOperations() {
        if (this.deliveredCommitCacheRevision == this.statusRevision) {
            return this.deliveredCommitCache;
        }
        List<ConstructionBuildOp> result = new ArrayList<>();
        for (ConstructionBuildOp op : this.operations) {
            if (op.status() != ConstructionBuildOp.Status.DELIVERED
                || (!op.writesProjection()
                    && (!op.worldSatisfied()
                        || (op.kind() != ConstructionBuildOp.Kind.PLACE
                            && op.kind() != ConstructionBuildOp.Kind.ATTACHED)))) {
                continue;
            }
            result.add(op);
        }
        this.deliveredCommitCache = List.copyOf(result);
        this.deliveredCommitCacheRevision = this.statusRevision;
        return this.deliveredCommitCache;
    }

    Set<Long> deliveredCommitPositions() {
        if (this.deliveredCommitPositionCacheRevision == this.statusRevision) {
            return this.deliveredCommitPositionCache;
        }
        Set<Long> positions = new HashSet<>();
        for (ConstructionBuildOp op : this.deliveredCommitOperations()) {
            positions.add(op.pos().asLong());
        }
        this.deliveredCommitPositionCache = Set.copyOf(positions);
        this.deliveredCommitPositionCacheRevision = this.statusRevision;
        return this.deliveredCommitPositionCache;
    }

    List<ConstructionBuildOp> deliveredEntityOperations() {
        if (this.deliveredEntityCacheRevision == this.statusRevision) {
            return this.deliveredEntityCache;
        }
        List<ConstructionBuildOp> result = new ArrayList<>();
        for (ConstructionBuildOp op : this.operations) {
            if (op.status() == ConstructionBuildOp.Status.DELIVERED
                && op.kind() == ConstructionBuildOp.Kind.ENTITY) {
                result.add(op);
            }
        }
        this.deliveredEntityCache = List.copyOf(result);
        this.deliveredEntityCacheRevision = this.statusRevision;
        return this.deliveredEntityCache;
    }

    private static boolean isPlaceMaterial(ConstructionBuildOp op) {
        return op.kind() == ConstructionBuildOp.Kind.PLACE
            || op.kind() == ConstructionBuildOp.Kind.CONTENT
            || op.kind() == ConstructionBuildOp.Kind.FLUID
            || op.kind() == ConstructionBuildOp.Kind.ENTITY
            || op.kind() == ConstructionBuildOp.Kind.DECORATE;
    }

    private static boolean isLaunchMaterial(ConstructionBuildOp op) {
        return isPlaceMaterial(op) || op.kind() == ConstructionBuildOp.Kind.SEAL;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("JobId", this.jobId);
        tag.putBoolean("Planned", this.planned);
        tag.putBoolean("Incomplete", this.incomplete);
        tag.putString("WaitReason", this.waitReason.name());
        tag.putString("LastReported", this.lastReported.name());
        if (!this.missingMaterial.isEmpty()) {
            tag.put("MissingMaterial", this.missingMaterial.save(registries));
        }
        if (this.missingOperationId >= 0) {
            tag.putInt("MissingOperationId", this.missingOperationId);
        }
        tag.putInt("NextOpId", this.nextOpId);
        tag.putInt("NextLedgerId", this.nextLedgerId);
        ListTag opsTag = new ListTag();
        for (ConstructionBuildOp op : this.operations) {
            opsTag.add(op.save(registries));
        }
        tag.put("Operations", opsTag);
        ListTag ledgerTag = new ListTag();
        for (ConstructionLedgerEntry entry : this.ledger) {
            ledgerTag.add(entry.save(registries));
        }
        tag.put("Ledger", ledgerTag);
        ListTag debrisTag = new ListTag();
        for (ConstructionDebrisAccount account : this.debris) {
            debrisTag.add(account.save());
        }
        tag.put("Debris", debrisTag);
        tag.put("CommitLog", this.commitLog.save());
        if (this.coordinatorLounge != null) {
            tag.putLong("CoordinatorLounge", this.coordinatorLounge.asLong());
        }
        ListTag declaredTag = new ListTag();
        this.declaredTargets.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                CompoundTag declared = new CompoundTag();
                declared.putLong("Pos", entry.getKey());
                declared.put("State", NbtUtils.writeBlockState(entry.getValue()));
                declaredTag.add(declared);
            });
        tag.put("DeclaredTargets", declaredTag);
        if (this.clearanceStrategyRecorded) {
            tag.putString("ClearanceStrategy", this.clearanceStrategy.name());
        }
        return tag;
    }

    public static ConstructionJobProgress load(CompoundTag tag, HolderLookup.Provider registries) {
        ConstructionJobProgress progress = new ConstructionJobProgress(tag.getUUID("JobId"));
        progress.planned = tag.getBoolean("Planned");
        progress.incomplete = tag.getBoolean("Incomplete");
        progress.waitReason = ConstructionWaitReason.valueOf(tag.getString("WaitReason"));
        progress.lastReported = ConstructionWaitReason.valueOf(tag.getString("LastReported"));
        if (tag.contains("MissingMaterial")) {
            progress.missingMaterial = ItemStack.parse(registries, tag.getCompound("MissingMaterial"))
                .orElse(ItemStack.EMPTY);
        }
        if (tag.contains("MissingOperationId")) {
            progress.missingOperationId = tag.getInt("MissingOperationId");
        }
        progress.nextOpId = tag.getInt("NextOpId");
        progress.nextLedgerId = tag.getInt("NextLedgerId");
        ListTag opsTag = tag.getList("Operations", Tag.TAG_COMPOUND);
        for (int index = 0; index < opsTag.size(); index++) {
            ConstructionBuildOp op = ConstructionBuildOp.load(opsTag.getCompound(index), registries);
            op.bindInvalidators(
                progress::invalidateEnclosure,
                progress::invalidateOperationOrder,
                progress::invalidateLayout,
                () -> progress.reindex(op),
                progress::invalidateTopology
            );
            progress.operations.add(op);
            progress.operationsById.put(op.id(), op);
            progress.operationsByPosition.computeIfAbsent(op.pos().asLong(), ignored -> new ArrayList<>()).add(op);
        }
        progress.invalidateEnclosure();
        progress.invalidateOperationOrder();
        progress.invalidateLayout();
        progress.invalidateStatus();
        progress.invalidateTopology();
        ListTag ledgerTag = tag.getList("Ledger", Tag.TAG_COMPOUND);
        for (int index = 0; index < ledgerTag.size(); index++) {
            ConstructionLedgerEntry entry = ConstructionLedgerEntry.load(ledgerTag.getCompound(index), registries);
            progress.ledger.add(entry);
            progress.indexCarriedEntry(entry);
        }
        // 在途台账参与"未认领数"判定,因此增量索引必须等台账装完再整表重建一次
        progress.reindexAll();
        ListTag debrisTag = tag.getList("Debris", Tag.TAG_COMPOUND);
        for (int index = 0; index < debrisTag.size(); index++) {
            progress.debris.add(ConstructionDebrisAccount.load(debrisTag.getCompound(index)));
        }
        if (tag.contains("CommitLog", Tag.TAG_COMPOUND)) {
            ConstructionCommitLog loaded = ConstructionCommitLog.load(tag.getCompound("CommitLog"));
            progress.commitLog.setPhase(loaded.phase());
            progress.commitLog.setNextIndex(loaded.nextIndex());
            progress.commitLog.written().addAll(loaded.written());
        }
        if (tag.contains("CoordinatorLounge")) {
            progress.coordinatorLounge = BlockPos.of(tag.getLong("CoordinatorLounge"));
        }
        ListTag declaredTag = tag.getList("DeclaredTargets", Tag.TAG_COMPOUND);
        for (int index = 0; index < declaredTag.size(); index++) {
            CompoundTag declared = declaredTag.getCompound(index);
            if (!declared.contains("State", Tag.TAG_COMPOUND)) continue;
            BlockState state = NbtUtils.readBlockState(
                registries.lookupOrThrow(Registries.BLOCK),
                declared.getCompound("State")
            );
            progress.declaredTargets.put(declared.getLong("Pos"), state);
        }
        if (tag.contains("ClearanceStrategy")) {
            try {
                progress.setClearanceStrategy(
                    AllayClearanceStrategy.valueOf(tag.getString("ClearanceStrategy"))
                );
            } catch (IllegalArgumentException ignored) {
                progress.setClearanceStrategy(AllayClearanceStrategy.CLEAR_AREA);
            }
        }
        return progress;
    }
}
