package dev.anvilcraft.plasticraft.allay;

import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import dev.anvilcraft.plasticraft.blueprint.ConstructionWaitReason;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** 施工飞行与空闲交还给原版大脑的共用动作。 */
public final class AllayWorkMotions {
    private AllayWorkMotions() {
    }

    public static void releaseToVanilla(WorkingAllayEntity worker) {
        worker.clearActionHold();
        worker.navigator().clear();
        worker.setWaitReason(ConstructionWaitReason.NONE);
        if (worker.flightState() != AllayFlightState.DOCKING) {
            worker.setFlightState(AllayFlightState.HOVERING);
        }
    }

    public static void holdStation(WorkingAllayEntity worker) {
        worker.navigator().clear();
        worker.setDeltaMovement(Vec3.ZERO);
        worker.setFlightState(AllayFlightState.HOVERING);
    }

    public static void flyTo(WorkingAllayEntity worker, Vec3 goal) {
        worker.clearActionHold();
        if (worker.position().distanceTo(goal) <= 3.0D) {
            worker.navigator().setPath(List.of(goal));
            worker.setFlightState(AllayFlightState.FLYING);
            return;
        }
        if (worker.horizontalCollision) {
            worker.navigator().clear();
        }
        if (worker.flightState() != AllayFlightState.FLYING
            || !worker.navigator().hasPath()
            || !worker.navigator().endsNear(goal)) {
            List<Vec3> path = AllayFlightPlanner.plan(worker, goal);
            if (path.isEmpty()) {
                boolean blocked = worker.horizontalCollision
                    || !worker.level().noCollision(worker, worker.getBoundingBox());
                worker.setDeltaMovement(blocked ? new Vec3(0.0D, 0.12D, 0.0D) : Vec3.ZERO);
                worker.setFlightState(AllayFlightState.FLYING);
                return;
            }
            worker.navigator().setPath(path);
        }
        worker.setFlightState(AllayFlightState.FLYING);
    }

    public static boolean arrived(WorkingAllayEntity worker, Vec3 goal) {
        return worker.position().distanceTo(goal) <= ConstructionJobController.reach(worker) + 0.5D;
    }
}
