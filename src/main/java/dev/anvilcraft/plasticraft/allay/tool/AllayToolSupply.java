package dev.anvilcraft.plasticraft.allay.tool;

import dev.anvilcraft.plasticraft.allay.AllayFlightState;
import dev.anvilcraft.plasticraft.allay.AllayWorkMotions;
import dev.anvilcraft.plasticraft.allay.AllayWorkRecord;
import dev.anvilcraft.plasticraft.allay.observation.ObservationCoverageService;
import dev.anvilcraft.plasticraft.allay.observation.ObservationLoadingIndex;
import dev.anvilcraft.plasticraft.allay.path.AllayPathPriority;
import dev.anvilcraft.plasticraft.allay.transfer.ConstructionTransferService;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobProgress;
import dev.anvilcraft.plasticraft.blueprint.ConstructionMaterialAccess;
import dev.anvilcraft.plasticraft.blueprint.ConstructionPermission;
import dev.anvilcraft.plasticraft.blueprint.IgnitionBuildAdapter;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 仓库工具按任务缺口借用，现场工人先回取放点再换装，玩家配装始终固定。 */
public final class AllayToolSupply {
    private AllayToolSupply() {
    }

    public static void plan(ServerLevel level, ConstructionJob job, ConstructionJobProgress progress,
                            AllayLoungeBlockEntity lounge) {
        if (!job.isActive() || !ObservationCoverageService.isCoordinatorTicking(level, progress)
            || !canAccess(level, lounge.getBlockPos(), job.owner())) return;
        List<Member> members = new ArrayList<>();
        for (AllayWorkRecord record : lounge.hosted()) {
            if (record.owner().filter(owner -> ConstructionPermission.areCollaborators(
                level.getServer(), owner, job.owner())).isEmpty()
                || record.assignedJobId().filter(id -> !id.equals(job.jobId())).isPresent()
                || record.transitJob().isPresent()) continue;
            members.add(new Member(null, record));
        }
        for (WorkingAllayEntity worker : ConstructionJobController.loadedWorkers(level, job.owner())) {
            if (!ConstructionJobController.isWorkerAvailableForJob(level, worker, job, progress)
                || worker.transitJobId().isPresent() || worker.flightState() == AllayFlightState.DOCKING) continue;
            members.add(new Member(worker, worker.toWorkRecord()));
        }
        ConstructionMaterialAccess access = ConstructionMaterialAccess.below(level, lounge.getBlockPos());
        Map<Item, Integer> stock = new HashMap<>(access.countItems());
        if (access.isInfinite()) {
            for (AllayToolDefinition tool : AllayToolDefinitions.values()) {
                stock.put(tool.toolItem().get(), Integer.MAX_VALUE);
            }
        }
        Set<UUID> used = new HashSet<>();
        Set<UUID> localIds = new HashSet<>();
        for (Member member : members) localIds.add(member.id());
        int retainedObservers = (int) ObservationLoadingIndex.get(level).leases(job.jobId()).stream()
            .filter(lease -> localIds.contains(lease.observer())).count();
        int missingObservers = ObservationCoverageService.wantedObservers(level.getServer(), job)
            - ConstructionTransferService.inTransitCount(level.getServer(), job.jobId(), AllayCapability.CHUNK_LOADING);
        assign(level, lounge, members, used, stock, AllayToolDefinitions.OBSERVATION,
            retainedObservers + Math.max(0, missingObservers));
        byte phase = job.state();
        boolean demolition = phase == ConstructionJob.STATE_DEMOLISHING
            || phase == ConstructionJob.STATE_WAITING_DEMOLITION;
        boolean collection = phase == ConstructionJob.STATE_COLLECTING_DEBRIS || demolition;
        int pendingDemolition = demolition ? progress.openDemolitionCores().size() : 0;
        // 至少留一名拆除工，避免唯一工人改收集后触发缺拆除暂停，自己也再领不到收集任务。
        if (pendingDemolition > 0) {
            assign(level, lounge, members, used, stock, AllayToolDefinitions.DEMOLITION, 1);
        }
        if (collection && ConstructionJobController.nextAssignableDebris(level, job, progress,
            Vec3.atCenterOf(lounge.getBlockPos())) != null) {
            assign(level, lounge, members, used, stock, AllayToolDefinitions.COLLECTION, 1);
        }
        if (pendingDemolition > 1) {
            assign(level, lounge, members, used, stock, AllayToolDefinitions.DEMOLITION, pendingDemolition - 1);
        }
        if (phase == ConstructionJob.STATE_BUILDING || phase == ConstructionJob.STATE_SEALING_FLUID) {
            boolean ignition = phase == ConstructionJob.STATE_BUILDING && IgnitionBuildAdapter.hasWork(progress);
            if (ignition) assign(level, lounge, members, used, stock, AllayToolDefinitions.IGNITION, 1);
            assign(level, lounge, members, used, stock, AllayToolDefinitions.CONSTRUCTION, members.size());
        }
        for (Member member : members) {
            if (used.contains(member.id()) || !member.flexible()) continue;
            // 暂停与观察等待不让全队拆装；只保留已有工具，下一阶段再统一分配。
            if (member.worker != null) member.worker.requestTool(null);
        }
    }

    private static void assign(ServerLevel level, AllayLoungeBlockEntity lounge, List<Member> members,
                               Set<UUID> used, Map<Item, Integer> stock, AllayToolDefinition tool, int wanted) {
        List<Member> ordered = new ArrayList<>(members);
        ordered.sort(Comparator.comparingInt((Member member) -> member.priority(tool))
            .thenComparing(Member::id));
        for (Member member : ordered) {
            if (wanted <= 0) break;
            if (used.contains(member.id())) continue;
            boolean already = member.definition() == tool;
            if (!already && !member.flexible()) continue;
            if (!already && stock.getOrDefault(tool.toolItem().get(), 0) <= 0) continue;
            if (member.worker == null && !already) {
                if (!equipHosted(level, lounge, member.record, tool)) continue;
            } else if (member.worker != null && member.flexible()) {
                member.worker.requestTool(tool);
            }
            if (!already) stock.computeIfPresent(tool.toolItem().get(), (item, count) -> count - 1);
            used.add(member.id());
            wanted--;
        }
    }

    private static boolean equipHosted(ServerLevel level, AllayLoungeBlockEntity lounge,
                                       AllayWorkRecord record, AllayToolDefinition tool) {
        if (!record.hostedCarry().isEmpty() || record.collectionInventory().stream().anyMatch(stack -> !stack.isEmpty())) {
            return false;
        }
        if (record.borrowedToolLounge().filter(pos -> pos != lounge.getBlockPos().asLong()).isPresent()) return false;
        ItemStack next = exchange(level, lounge.getBlockPos(), record.heldTool(), tool);
        if (next == null) return false;
        return lounge.updateHostedRecord(record.entityId(), current -> current.withBorrowedTool(
            next, next.isEmpty() ? Optional.empty() : Optional.of(lounge.getBlockPos().asLong())));
    }

    public static boolean tickWorker(WorkingAllayEntity worker) {
        if (worker.borrowedToolLounge() == null && worker.requestedTool() == null) return false;
        if (!(worker.level() instanceof ServerLevel level) || !worker.canBorrowTool()
            || worker.flightState() == AllayFlightState.DOCKING || worker.isEvacuating()) return false;
        ConstructionJob job = ConstructionJobController.jobForWorker(level, worker);
        AllayToolDefinition requested = worker.requestedTool();
        if (job == null || !job.isActive() || job.state() == ConstructionJob.STATE_COMMITTING) {
            requested = worker.borrowedToolLounge() == null ? null : AllayToolDefinitions.NONE;
        }
        if (requested == null || requested == worker.toolDefinition()) return false;
        if (!worker.hostedCarry().isEmpty() || worker.hasCollectionItems()) return false;
        BlockPos source = worker.borrowedToolLounge() == null ? worker.homeLoungePos() : worker.borrowedToolLounge();
        UUID owner = worker.getOwner().orElse(null);
        if (source == null || owner == null || !canAccess(level, source, owner)) return false;
        if (!(level.getBlockEntity(source) instanceof AllayLoungeBlockEntity lounge)) return false;
        if (worker.assignedJobId().isPresent()) worker.clearAssignment(false);
        Vec3 target = lounge.dockApproachPoint();
        if (worker.position().distanceTo(target) > 1.5D) {
            AllayWorkMotions.flyTo(worker, target, AllayPathPriority.PICKUP);
            return true;
        }
        if (!worker.prepareAction(target)) return true;
        ItemStack next = exchange(level, source, worker.getMainHandItem(), requested);
        if (next == null) {
            worker.requestTool(null);
            return false;
        }
        worker.equipBorrowedTool(next, source);
        return false;
    }

    public static void returnOnDock(WorkingAllayEntity worker, AllayLoungeBlockEntity lounge) {
        if (!(lounge.getLevel() instanceof ServerLevel level)
            || !lounge.getBlockPos().equals(worker.borrowedToolLounge())
            || worker.getOwner().filter(owner -> canAccess(level, lounge.getBlockPos(), owner)).isEmpty()) return;
        if (worker.toolDefinition() == AllayToolDefinitions.OBSERVATION
            && (ObservationLoadingIndex.get(level).leaseOf(worker.getUUID()) != null
                || ObservationCoverageService.awaitsWindow(level, worker))) return;
        ItemStack next = exchange(level, lounge.getBlockPos(), worker.getMainHandItem(), AllayToolDefinitions.NONE);
        if (next != null) worker.equipBorrowedTool(next, lounge.getBlockPos());
    }

    public static void returnHostedTools(AllayLoungeBlockEntity lounge) {
        if (!(lounge.getLevel() instanceof ServerLevel level)) return;
        for (AllayWorkRecord record : lounge.hosted()) {
            if (record.borrowedToolLounge().isEmpty()
                || record.borrowedToolLounge().get() != lounge.getBlockPos().asLong()
                || record.owner().filter(owner -> canAccess(level, lounge.getBlockPos(), owner)).isEmpty()) continue;
            equipHosted(level, lounge, record, AllayToolDefinitions.NONE);
        }
    }

    private static boolean canAccess(ServerLevel level, BlockPos source, UUID owner) {
        return level.hasChunkAt(source) && level.hasChunkAt(source.below())
            && level.getBlockEntity(source) instanceof AllayLoungeBlockEntity lounge
            && ConstructionPermission.areCollaborators(level.getServer(), owner, lounge.owner())
            && ConstructionPermission.canModify(level, source, owner)
            && ConstructionPermission.canModify(level, source.below(), owner);
    }

    @Nullable
    private static ItemStack exchange(ServerLevel level, BlockPos source, ItemStack previous, AllayToolDefinition tool) {
        ConstructionMaterialAccess access = ConstructionMaterialAccess.below(level, source);
        ItemStack next = ItemStack.EMPTY;
        if (tool != AllayToolDefinitions.NONE) {
            next = access.extractTool(tool.toolItem().get());
            if (next.isEmpty()) return null;
        }
        if (!previous.isEmpty() && !access.insert(previous).isEmpty()) {
            access.insertOrDrop(next);
            return null;
        }
        return next;
    }

    private record Member(@Nullable WorkingAllayEntity worker, AllayWorkRecord record) {
        UUID id() {
            return this.record.entityId();
        }

        AllayToolDefinition definition() {
            return AllayToolDefinitions.fromHeldItem(this.record.heldTool());
        }

        boolean flexible() {
            return this.record.heldTool().isEmpty() || this.record.borrowedToolLounge().isPresent();
        }

        int priority(AllayToolDefinition desired) {
            if (desired == AllayToolDefinitions.OBSERVATION && this.worker != null
                && ObservationLoadingIndex.get(this.worker.level()).leaseOf(this.id()) != null) return -1;
            if (this.definition() == desired) return this.flexible() ? 1 : 0;
            if (this.worker != null && this.worker.requestedTool() == desired) return 2;
            if (this.worker == null) return 3;
            return this.worker.hostedCarry().isEmpty() && !this.worker.hasCollectionItems() ? 4 : 5;
        }
    }
}
