package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** 将没有方块占位的透明塑料实体并入信标光柱扫描。 */
public final class ClearPlasticBeaconInteraction {
    // 同一游戏刻内每个光柱列只查询一次实体，后续按高度直接读取颜色缓存。
    private static final Map<Level, LevelColumns> CACHED_COLUMNS = new WeakHashMap<>();

    private ClearPlasticBeaconInteraction() {
    }

    public static int scanHeight(Level level, int x, int z, int originalHeight) {
        return Math.max(originalHeight, columnSnapshot(level, x, z).maximumHeight());
    }

    public static Integer beamColorAt(Level level, BlockPos position) {
        return columnSnapshot(level, position.getX(), position.getZ()).colors().get(position.getY());
    }

    private static ColumnSnapshot columnSnapshot(Level level, int x, int z) {
        long gameTime = level.getGameTime();
        long columnKey = BlockPos.asLong(x, 0, z);
        synchronized (CACHED_COLUMNS) {
            LevelColumns columns = CACHED_COLUMNS.get(level);
            if (columns == null || columns.gameTime() != gameTime) {
                columns = new LevelColumns(gameTime, new HashMap<>());
                CACHED_COLUMNS.put(level, columns);
            }
            return columns.snapshots().computeIfAbsent(columnKey, ignored -> ColumnSnapshot.create(level, x, z));
        }
    }

    private record LevelColumns(long gameTime, Map<Long, ColumnSnapshot> snapshots) {
    }

    private record ColumnSnapshot(int maximumHeight, Map<Integer, Integer> colors) {
        private static ColumnSnapshot create(Level level, int x, int z) {
            AABB column = new AABB(
                x,
                level.getMinBuildHeight(),
                z,
                x + 1.0D,
                level.getMaxBuildHeight(),
                z + 1.0D
            );
            int maximumHeight = level.getMinBuildHeight();
            Map<Integer, Integer> colors = new HashMap<>();
            for (ClearPlasticEntity entity : level.getEntitiesOfClass(
                ClearPlasticEntity.class,
                column,
                candidate -> {
                    AABB bounds = candidate.getBoundingBox();
                    return candidate.isAlive()
                        && bounds.intersects(column)
                        && coversBeamCenter(bounds, x, z);
                }
            )) {
                AABB bounds = entity.getBoundingBox();
                maximumHeight = Math.max(maximumHeight, (int) Math.ceil(bounds.maxY));
                int minimumY = Math.max(level.getMinBuildHeight(), (int) Math.floor(bounds.minY));
                int maximumY = Math.min(level.getMaxBuildHeight(), (int) Math.ceil(bounds.maxY));
                int color = entity.getDisplayState().getValue(DyeableMaterial.COLOR).getTextureDiffuseColor();
                for (int y = minimumY; y < maximumY; y++) {
                    colors.putIfAbsent(y, color);
                }
            }
            return new ColumnSnapshot(maximumHeight, Map.copyOf(colors));
        }
    }

    private static boolean coversBeamCenter(AABB bounds, int x, int z) {
        double centerX = x + 0.5D;
        double centerZ = z + 0.5D;
        return bounds.minX <= centerX && centerX < bounds.maxX
            && bounds.minZ <= centerZ && centerZ < bounds.maxZ;
    }
}
