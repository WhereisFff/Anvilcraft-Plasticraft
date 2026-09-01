package dev.anvilcraft.plasticraft.allay.tool;

import dev.anvilcraft.plasticraft.allay.AllayFlightState;
import dev.anvilcraft.plasticraft.allay.AllayWorkMotions;
import dev.anvilcraft.plasticraft.allay.observation.ObservationChunkLoader;
import dev.anvilcraft.plasticraft.allay.observation.ObservationCoverage;
import dev.anvilcraft.plasticraft.allay.observation.ObservationCoverageService;
import dev.anvilcraft.plasticraft.allay.observation.ObservationLease;
import dev.anvilcraft.plasticraft.allay.observation.ObservationLoadingIndex;
import dev.anvilcraft.plasticraft.allay.path.AllayPathPriority;
import dev.anvilcraft.plasticraft.allay.transfer.ConstructionTransferService;
import dev.anvilcraft.plasticraft.blueprint.ConstructionTraffic;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 观察悦灵调度器:望远镜不参与建设、拆除或收集,只负责把自己所在的九区块柱保持在实体刻。
 *
 * <p>覆盖同步每刻先做,与是否有任务无关——资格只看戴帽、主手望远镜和实体是否真在这个维度。
 * 有观察租约时飞去目标柱的安全观察位待着,窗口需求还在但租约尚未派到手时原地待命,
 * 两者都没有时才按绑定关系回库或交还原版游荡。
 */
public final class ObservationAllayToolBehavior implements AllayToolBehavior {
    public static final ObservationAllayToolBehavior INSTANCE = new ObservationAllayToolBehavior();

    /** 观察位在地表之上的候选抬升高度,按顺序取第一个不碰撞的,保证目标点逐刻稳定。 */
    private static final int[] POST_HEIGHTS = {3, 6, 1, 10};
    /** 观察位在目标柱内的候选水平偏移,先取柱中心。 */
    private static final int[] POST_OFFSETS = {8, 4, 12};

    private ObservationAllayToolBehavior() {
    }

    @Override
    public void serverTick(WorkingAllayEntity worker) {
        if (!(worker.level() instanceof ServerLevel level)) return;
        ObservationChunkLoader.syncObserver(worker);
        if (worker.flightState() == AllayFlightState.DOCKING) return;
        if (worker.getOwner().isEmpty()) {
            AllayWorkMotions.releaseToVanilla(worker);
            return;
        }
        if (ConstructionTransferService.isIdleTransitWorker(level, worker)) {
            // 借调/返程途中的观察悦灵只沿转运链走,租约由抵达后的调度重新指派
            ConstructionTransferService.routeWorldWorkerHome(level, worker);
            return;
        }
        ObservationLease lease = ObservationLoadingIndex.get(level).leaseOf(worker.getUUID());
        if (lease != null && lease.dimension().equals(level.dimension())) {
            holdObservationPost(worker, level, lease.center());
            return;
        }
        if (ObservationCoverageService.awaitsWindow(level, worker)) {
            // 窗口需求还在、只是规划这一刻还没派到手:原地待命等指派,当刻返库只会白占出库通道
            worker.setActionState((byte) 0);
            AllayWorkMotions.holdStation(worker);
            return;
        }
        if (dockHome(worker, level)) return;
        AllayWorkMotions.releaseToVanilla(worker);
    }

    private static void holdObservationPost(WorkingAllayEntity worker, ServerLevel level, ChunkPos center) {
        worker.setActionState((byte) 0);
        if (ObservationCoverage.centerOf(worker).equals(center)) {
            // 已经在目标柱内,任何不与方块碰撞的位置都是合格观察位,不再继续折腾飞行
            AllayWorkMotions.holdStation(worker);
            return;
        }
        Vec3 post = observationPost(worker, level, center);
        if (AllayWorkMotions.arrived(worker, post)) {
            AllayWorkMotions.holdStation(worker);
            return;
        }
        AllayWorkMotions.flyTo(worker, post, AllayPathPriority.DELIVER);
    }

    /**
     * 目标柱内的观察位。候选顺序只由目标柱和世界地形决定,不掺入悦灵当前位置,
     * 否则目标点会随飞行逐刻漂移,飞行任务永远无法沿用。
     */
    private static Vec3 observationPost(WorkingAllayEntity worker, ServerLevel level, ChunkPos center) {
        Vec3 fallback = null;
        for (int offsetX : POST_OFFSETS) {
            for (int offsetZ : POST_OFFSETS) {
                int x = center.getMinBlockX() + offsetX;
                int z = center.getMinBlockZ() + offsetZ;
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                for (int lift : POST_HEIGHTS) {
                    int y = Math.min(surface + lift, level.getMaxBuildHeight() - 2);
                    Vec3 candidate = new Vec3(x + 0.5D, Math.max(y, level.getMinBuildHeight() + 1), z + 0.5D);
                    if (fallback == null) fallback = candidate;
                    if (isSafePost(worker, candidate)) return candidate;
                }
            }
        }
        return fallback == null ? Vec3.atCenterOf(center.getMiddleBlockPosition(level.getSeaLevel())) : fallback;
    }

    private static boolean isSafePost(WorkingAllayEntity worker, Vec3 candidate) {
        return worker.level().noCollision(worker, worker.getBoundingBox().move(candidate.subtract(worker.position())));
    }

    /** 无租约时的返航。没有休息室的观察悦灵不返航,交还原版游荡,不强制离地悬停。 */
    private static boolean dockHome(WorkingAllayEntity worker, ServerLevel level) {
        if (worker.homeLoungePos() == null || worker.isEvacuating()) return false;
        worker.setActionState((byte) 0);
        AllayWorkMotions.releaseToVanilla(worker);
        ConstructionTraffic.release(level, worker.getUUID());
        worker.resetStuck();
        if (ConstructionTransferService.sendGuestHome(level, worker)) return true;
        @Nullable BlockPos home = worker.homeLoungePos();
        if (home != null && worker.startDockingTo(home)) return true;
        worker.setHomeLounge(null);
        return false;
    }
}
