package dev.anvilcraft.plasticraft.allay;

import dev.anvilcraft.plasticraft.allay.path.AllayPathPriority;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import dev.anvilcraft.plasticraft.blueprint.ConstructionTraffic;
import dev.anvilcraft.plasticraft.blueprint.ConstructionWaitReason;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** 施工飞行与空闲交还给原版大脑的共用动作。 */
public final class AllayWorkMotions {
    private AllayWorkMotions() {
    }

    public static void releaseToVanilla(WorkingAllayEntity worker) {
        worker.clearActionHold();
        worker.cancelFlightTask();
        worker.navigator().clear();
        ConstructionTraffic.release(worker.level(), worker.getUUID());
        worker.setWaitReason(ConstructionWaitReason.NONE);
        if (worker.flightState() != AllayFlightState.DOCKING) {
            worker.setFlightState(AllayFlightState.HOVERING);
        }
    }

    public static void holdStation(WorkingAllayEntity worker) {
        worker.cancelFlightTask();
        worker.navigator().clear();
        worker.setDeltaMovement(Vec3.ZERO);
        if (worker.flightState() != AllayFlightState.DOCKING) {
            worker.setFlightState(AllayFlightState.HOVERING);
        }
    }

    public static void flyTo(WorkingAllayEntity worker, Vec3 goal) {
        flyTo(worker, goal, AllayPathPriority.DELIVER);
    }

    public static void flyTo(WorkingAllayEntity worker, Vec3 goal, AllayPathPriority priority) {
        worker.clearActionHold();
        boolean recovering = worker.navigator().consumeReplanRequest();
        if (recovering) {
            worker.cancelFlightTask();
        }
        boolean docking = worker.flightState() == AllayFlightState.DOCKING;
        boolean continuingTask = worker.hasFlightTaskFor(goal);
        if (!continuingTask && worker.navigator().hasPath() && !worker.navigator().endsNear(goal)) {
            worker.navigator().clear();
        }
        AllayFlightPlanner.FlightTask task = worker.ensureFlightTask(
            goal,
            recovering ? AllayPathPriority.STUCK : priority
        );
        if (worker.navigator().hasPath()) {
            if (!docking) {
                worker.setFlightState(AllayFlightState.FLYING);
            }
            return;
        }
        if (task.advance(worker)) {
            List<Vec3> path = task.path();
            if (path.isEmpty()) {
                worker.setDeltaMovement(Vec3.ZERO);
            } else {
                worker.navigator().setPath(path);
                reserveCorridor(worker, path);
            }
        }
        if (!docking) {
            worker.setFlightState(AllayFlightState.FLYING);
        }
    }

    public static boolean arrived(WorkingAllayEntity worker, Vec3 goal) {
        return worker.position().distanceTo(goal) <= ConstructionJobController.reach(worker) + 0.5D;
    }

    private static void reserveCorridor(WorkingAllayEntity worker, List<Vec3> path) {
        if (!(worker.level() instanceof ServerLevel level)) return;
        List<BlockPos> cells = new ArrayList<>(path.size());
        for (Vec3 point : path) {
            cells.add(BlockPos.containing(point));
        }
        List<BlockPos> narrow = ConstructionTraffic.narrowCells(level, cells);
        ConstructionTraffic.reserveCorridor(level, worker.getUUID(), narrow);
    }
}
