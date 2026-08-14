package dev.anvilcraft.plasticraft.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 一份施工任务的进度、材料台账与租约;与任务索引分离,避免把大进度塞进客户端同步的摘要 record。 */
public final class ConstructionJobProgress {
    private final UUID jobId;
    private boolean planned;
    private boolean incomplete;
    private ConstructionWaitReason waitReason = ConstructionWaitReason.NONE;
    private ConstructionWaitReason lastReported = ConstructionWaitReason.NONE;
    private ItemStack missingMaterial = ItemStack.EMPTY;
    private final List<ConstructionBuildOp> operations = new ArrayList<>();
    private final List<ConstructionLedgerEntry> ledger = new ArrayList<>();
    private final List<ConstructionDebrisAccount> debris = new ArrayList<>();
    private final Map<UUID, UUID> debrisLeases = new HashMap<>();
    private final ConstructionCommitLog commitLog = new ConstructionCommitLog();
    private int nextOpId;
    private int nextLedgerId;

    public ConstructionJobProgress(UUID jobId) {
        this.jobId = jobId;
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
    }

    public List<ConstructionBuildOp> operations() {
        return this.operations;
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
        this.operations.add(op);
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
        return entry;
    }

    @Nullable
    public ConstructionBuildOp operation(int id) {
        for (ConstructionBuildOp op : this.operations) {
            if (op.id() == id) return op;
        }
        return null;
    }

    @Nullable
    public ConstructionBuildOp leasedBy(UUID droneId) {
        for (ConstructionBuildOp op : this.operations) {
            if (op.status() == ConstructionBuildOp.Status.LEASED
                && op.leaseDrone().filter(droneId::equals).isPresent()) {
                return op;
            }
        }
        return null;
    }

    public ConstructionCommitLog commitLog() {
        return this.commitLog;
    }

    @Nullable
    public ConstructionBuildOp parentOf(ConstructionBuildOp child) {
        if (child.parentId() < 0) {
            return null;
        }
        return this.operation(child.parentId());
    }

    public Map<Long, BlockState> overlayStates() {
        Map<Long, BlockState> overlay = new HashMap<>();
        for (ConstructionBuildOp op : this.operations) {
            if (!op.writesProjection() || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                continue;
            }
            overlay.put(op.pos().asLong(), op.target());
        }
        return overlay;
    }

    public boolean hasNonEmptyProgress() {
        if (!this.planned) return false;
        for (ConstructionBuildOp op : this.operations) {
            if (op.status() == ConstructionBuildOp.Status.DELIVERED
                || op.status() == ConstructionBuildOp.Status.LEASED) {
                return true;
            }
        }
        for (ConstructionLedgerEntry entry : this.ledger) {
            if (entry.state() == ConstructionLedgerEntry.State.CARRIED) return true;
        }
        return false;
    }

    public boolean allSealResolved() {
        return this.allResolved(ConstructionBuildOp.Kind.SEAL, true);
    }

    public boolean allDemolishResolved() {
        return this.allResolved(ConstructionBuildOp.Kind.DEMOLISH, false);
    }

    public boolean hasOpenSeal() {
        return this.hasOpen(ConstructionBuildOp.Kind.SEAL, true);
    }

    public boolean hasOpenDemolish() {
        return this.hasOpen(ConstructionBuildOp.Kind.DEMOLISH, false);
    }

    public boolean hasLeasedDemolish() {
        for (ConstructionBuildOp op : this.operations) {
            if (op.kind() != ConstructionBuildOp.Kind.DEMOLISH || op.shell()) continue;
            if (op.status() == ConstructionBuildOp.Status.LEASED) return true;
        }
        return false;
    }

    private boolean allResolved(ConstructionBuildOp.Kind kind, boolean includeShell) {
        for (ConstructionBuildOp op : this.operations) {
            if (op.kind() != kind) continue;
            if (op.shell() && !includeShell) continue;
            if (op.status() == ConstructionBuildOp.Status.PENDING
                || op.status() == ConstructionBuildOp.Status.WAITING_WORLD
                || op.status() == ConstructionBuildOp.Status.WAITING_OCCUPIED
                || op.status() == ConstructionBuildOp.Status.LEASED) {
                return false;
            }
        }
        return true;
    }

    private boolean hasOpen(ConstructionBuildOp.Kind kind, boolean includeShell) {
        for (ConstructionBuildOp op : this.operations) {
            if (op.kind() != kind) continue;
            if (op.shell() && !includeShell) continue;
            if (op.isOpen()) return true;
        }
        return false;
    }

    public boolean allPlaceResolved() {
        for (ConstructionBuildOp op : this.operations) {
            if (!op.isBuildMaterial()) {
                continue;
            }
            if (op.status() == ConstructionBuildOp.Status.PENDING
                || op.status() == ConstructionBuildOp.Status.WAITING_WORLD
                || op.status() == ConstructionBuildOp.Status.WAITING_OCCUPIED
                || op.status() == ConstructionBuildOp.Status.LEASED) {
                return false;
            }
        }
        return true;
    }

    public boolean hasDelivered() {
        for (ConstructionBuildOp op : this.operations) {
            if (op.status() == ConstructionBuildOp.Status.DELIVERED) return true;
        }
        return false;
    }

    public boolean hasOpenPlace() {
        for (ConstructionBuildOp op : this.operations) {
            if ((op.kind() == ConstructionBuildOp.Kind.PLACE
                || op.kind() == ConstructionBuildOp.Kind.CONTENT
                || op.kind() == ConstructionBuildOp.Kind.FLUID
                || op.kind() == ConstructionBuildOp.Kind.ENTITY)
                && op.isOpen()) {
                return true;
            }
        }
        return false;
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
        progress.nextOpId = tag.getInt("NextOpId");
        progress.nextLedgerId = tag.getInt("NextLedgerId");
        ListTag opsTag = tag.getList("Operations", Tag.TAG_COMPOUND);
        for (int index = 0; index < opsTag.size(); index++) {
            progress.operations.add(ConstructionBuildOp.load(opsTag.getCompound(index), registries));
        }
        ListTag ledgerTag = tag.getList("Ledger", Tag.TAG_COMPOUND);
        for (int index = 0; index < ledgerTag.size(); index++) {
            progress.ledger.add(ConstructionLedgerEntry.load(ledgerTag.getCompound(index), registries));
        }
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
        return progress;
    }
}
