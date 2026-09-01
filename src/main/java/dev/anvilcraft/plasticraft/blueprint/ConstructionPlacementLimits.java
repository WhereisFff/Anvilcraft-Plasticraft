package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.allay.tool.AllayToolDefinitions;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * 空手悦灵手臂短,只能放普通单格与门/床这类两格件;
 * 巨型铁砧等多方块和超过 1 格宽或 2 格高的塑料实体必须蟹钳。
 */
public final class ConstructionPlacementLimits {
    private static final double WIDE = 1.0D + 1.0E-3D;
    private static final double TALL = 2.0D + 1.0E-3D;

    private ConstructionPlacementLimits() {
    }

    public static boolean requiresLongReach(ConstructionBuildOp op) {
        if (op.kind() == ConstructionBuildOp.Kind.PLACE && MultiblockBuildAdapter.isMultiPart(op.target())) {
            return true;
        }
        return op.kind() == ConstructionBuildOp.Kind.ENTITY && op.longReach();
    }

    public static boolean canDeliver(WorkingAllayEntity worker, ConstructionBuildOp op) {
        return !requiresLongReach(op)
            || worker.toolDefinition().reachDistance() >= AllayToolDefinitions.CONSTRUCTION.reachDistance();
    }

    public static boolean isLarge(Entity entity) {
        if (!(entity instanceof AbstractPlasticEntity)) {
            return false;
        }
        AABB box = entity.getBoundingBox();
        return box.getXsize() > WIDE || box.getZsize() > WIDE || box.getYsize() > TALL;
    }
}
