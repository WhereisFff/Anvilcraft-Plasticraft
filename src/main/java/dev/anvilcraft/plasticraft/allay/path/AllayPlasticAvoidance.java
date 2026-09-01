package dev.anvilcraft.plasticraft.allay.path;

import dev.anvilcraft.plasticraft.api.entity.ShapedCollisionEntity;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * 悦灵寻路的塑料实体软障碍来源。塑料实体的逐部件与凸体碰撞求解代价很高,
 * 这里只取整体包围盒当作绕行代价,绝不调用组合形状或凸体求解。
 * 所有查询都读实体分段,必须在主线程调用;超大范围直接放弃避让以保住帧时间。
 */
public final class AllayPlasticAvoidance {
    /** 单次查询最多参与避让的塑料实体数量,超出部分按可穿过处理。 */
    private static final int MAX_BOXES = 256;
    /** 查询体积上限,约 64³;更大的范围宁可不避让也不做全域实体扫描。 */
    private static final double MAX_QUERY_VOLUME = 262144.0D;

    private AllayPlasticAvoidance() {
    }

    /** 收集范围内塑料实体的整体包围盒,供快照编译成 A* 的绕行代价。 */
    public static List<AABB> gather(Level level, AABB area) {
        if (oversized(area)) return List.of();
        List<AABB> boxes = new ArrayList<>();
        for (Entity entity : level.getEntities((Entity) null, area, AllayPlasticAvoidance::isObstacle)) {
            boxes.add(ShapedCollisionEntity.collisionBounds(entity));
            if (boxes.size() >= MAX_BOXES) break;
        }
        return boxes;
    }

    /** 判断扫掠体是否压到塑料实体的整体包围盒,用于否决直飞捷径。 */
    public static boolean blocked(Level level, AABB swept) {
        if (oversized(swept)) return false;
        for (Entity entity : level.getEntities((Entity) null, swept, AllayPlasticAvoidance::isObstacle)) {
            if (ShapedCollisionEntity.collisionBounds(entity).intersects(swept)) return true;
        }
        return false;
    }

    private static boolean isObstacle(Entity entity) {
        return entity instanceof AbstractPlasticEntity && entity.isAlive();
    }

    private static boolean oversized(AABB area) {
        return area.getXsize() * area.getYsize() * area.getZsize() > MAX_QUERY_VOLUME;
    }
}
