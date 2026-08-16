package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** 施工悦灵 0.35×0.6 占位与可飞判定,规划器和封闭洪泛共用。 */
public final class ConstructionWorkerSpace {
    public static final double WIDTH = 0.35D;
    public static final double HEIGHT = 0.6D;
    public static final double HALF_WIDTH = WIDTH * 0.5D;
    private static final double NAVIGATION_CLEARANCE = 0.01D;

    private ConstructionWorkerSpace() {
    }

    public static AABB boxAt(BlockPos pos) {
        return boxAt(navigationPoint(pos));
    }

    public static Vec3 navigationPoint(BlockPos pos) {
        return navigationPoint(pos.getX(), pos.getY(), pos.getZ());
    }

    public static Vec3 navigationPoint(int x, int y, int z) {
        return new Vec3(x + 0.5D, y + NAVIGATION_CLEARANCE, z + 0.5D);
    }

    public static AABB boxAt(Vec3 bottomCenter) {
        return new AABB(
            bottomCenter.x - HALF_WIDTH,
            bottomCenter.y,
            bottomCenter.z - HALF_WIDTH,
            bottomCenter.x + HALF_WIDTH,
            bottomCenter.y + HEIGHT,
            bottomCenter.z + HALF_WIDTH
        );
    }

    public static boolean ignore(Entity entity) {
        return !entity.isAlive()
            || entity.isSpectator()
            || entity instanceof WorkingAllayEntity
            || entity instanceof ItemEntity
            || entity instanceof ExperienceOrb;
    }

    public static boolean fitsGeometry(ServerLevel level, BlockPos pos) {
        AABB box = boxAt(pos);
        if (!level.noBlockCollision(null, box)) return false;
        for (Entity entity : level.getEntities(null, box)) {
            if (ignore(entity)) continue;
            return false;
        }
        return true;
    }

    public static boolean fitsWorker(ServerLevel level, BlockPos pos) {
        return fitsWorker(level, pos, null);
    }

    public static boolean fitsWorker(ServerLevel level, BlockPos pos, @Nullable UUID exceptAllay) {
        if (!fitsGeometry(level, pos)) return false;
        return !ConstructionTraffic.isReserved(level, pos, exceptAllay);
    }
}
