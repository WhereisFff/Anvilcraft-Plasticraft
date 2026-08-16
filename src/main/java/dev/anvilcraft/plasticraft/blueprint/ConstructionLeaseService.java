package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class ConstructionLeaseService {
    private ConstructionLeaseService() {
    }

    public static void release(WorkingAllayEntity worker) {
        if (!(worker.level() instanceof ServerLevel level)) return;
        UUID jobId = worker.assignedJobId().orElse(null);
        if (jobId == null) return;
        ConstructionJobStore store = ConstructionJobStore.get(level);
        ConstructionJobProgress progress = store.get(jobId);
        if (progress == null) return;
        ConstructionBuildOp op = worker.taskOpId() < 0 ? null : progress.operation(worker.taskOpId());
        if (!isOwnedAssignment(op, worker.getUUID())) {
            op = findOwnedAssignment(progress, worker.getUUID());
        }
        if (op == null) return;
        if (worker.hostedCarry().isEmpty()) {
            returnReservedMaterial(level, worker, progress, op);
        }
        op.setStatus(ConstructionBuildOp.Status.PENDING);
        op.setLeaseAllay(null);
        op.setApproach(null);
        store.markDirty();
    }

    public static void markCarriesUntracked(WorkingAllayEntity worker) {
        if (!(worker.level() instanceof ServerLevel level)) return;
        ConstructionJobStore store = ConstructionJobStore.get(level);
        boolean changed = false;
        for (ConstructionJobProgress progress : store.progresses()) {
            changed |= progress.markCarriesReturned(worker.getUUID());
        }
        if (changed) {
            store.markDirty();
        }
    }

    private static boolean isOwnedAssignment(ConstructionBuildOp op, UUID workerId) {
        return op != null
            && op.isOpen()
            && op.leaseAllay().filter(workerId::equals).isPresent();
    }

    private static ConstructionBuildOp findOwnedAssignment(ConstructionJobProgress progress, UUID workerId) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (isOwnedAssignment(op, workerId)) return op;
        }
        return null;
    }

    private static void returnReservedMaterial(
        ServerLevel level,
        WorkingAllayEntity worker,
        ConstructionJobProgress progress,
        ConstructionBuildOp op
    ) {
        BlockPos loungePos = progress.coordinatorLounge();
        ConstructionMaterialAccess access = loungePos == null
            ? null
            : ConstructionMaterialAccess.below(level, loungePos);
        ConstructionJob job = ConstructionJobIndex.get(level).job(progress.jobId());
        ServerPlayer owner = job == null ? null : level.getServer().getPlayerList().getPlayer(job.owner());
        boolean changed = false;
        for (ConstructionLedgerEntry entry : progress.carriedEntries(worker.getUUID(), op.id())) {
            ItemStack stack = entry.stack().copy();
            if (access != null) {
                if (!access.isInfinite()) {
                    access.insertOrDrop(stack);
                }
            } else if (owner != null) {
                owner.getInventory().placeItemBackInInventory(stack);
            } else if (!stack.isEmpty()) {
                Vec3 pos = worker.position();
                level.addFreshEntity(new ItemEntity(level, pos.x, pos.y, pos.z, stack));
            }
            progress.markCarryReturned(entry);
            changed = true;
        }
        op.setStatus(ConstructionBuildOp.Status.PENDING);
        op.setLeaseAllay(null);
        op.setApproach(null);
        if (changed && loungePos != null
            && level.getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity lounge) {
            lounge.clearPickupDisplays();
        }
    }
}
