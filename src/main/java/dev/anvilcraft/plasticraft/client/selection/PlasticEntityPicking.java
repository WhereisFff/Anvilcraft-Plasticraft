package dev.anvilcraft.plasticraft.client.selection;

import dev.anvilcraft.lib.v2.cube.client.SelectionPart;
import dev.anvilcraft.lib.v2.cube.geometry.SelectionGeometry;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 保留原版的最近命中与同载具规则，仅将塑料候选的 AABB 判定替换为砧库 BVH。 */
public final class PlasticEntityPicking {
    private PlasticEntityPicking() {
    }

    public static @Nullable EntityHitResult pick(
        Entity camera, List<Entity> candidates, Vec3 start, Vec3 end, double maximumDistanceSquared, float partialTick
    ) {
        double nearest = maximumDistanceSquared;
        Entity selected = null;
        Vec3 position = null;
        for (Entity candidate : candidates) {
            Vec3 hit;
            boolean inside;
            if (candidate instanceof AbstractPlasticEntity plastic) {
                SelectionPart part = PlasticSelectionGeometry.get(plastic.plasticraft$getGeometry()).selection(plastic.getOrientation());
                float entityPartialTick = plastic.level().tickRateManager().isEntityFrozen(plastic) ? 1.0F : partialTick;
                Vec3 origin = plastic.getPosition(entityPartialTick);
                SelectionGeometry.RayHit result = part.clip(start.subtract(origin), end.subtract(origin));
                hit = result == null ? null : start.lerp(end, result.fraction());
                inside = result != null && result.inside();
            } else {
                AABB bounds = candidate.getBoundingBox().inflate(candidate.getPickRadius());
                hit = bounds.clip(start, end).orElse(null);
                inside = bounds.contains(start);
            }
            if (inside) {
                if (nearest >= 0) {
                    selected = candidate;
                    position = hit == null ? start : hit;
                    nearest = 0;
                }
            } else if (hit != null) {
                double distance = start.distanceToSqr(hit);
                if (distance >= nearest && nearest != 0) continue;
                if (candidate.getRootVehicle() == camera.getRootVehicle() && !candidate.canRiderInteract()) {
                    if (nearest == 0) {
                        selected = candidate;
                        position = hit;
                    }
                } else {
                    selected = candidate;
                    position = hit;
                    nearest = distance;
                }
            }
        }
        return selected == null ? null : new EntityHitResult(selected, position);
    }
}
