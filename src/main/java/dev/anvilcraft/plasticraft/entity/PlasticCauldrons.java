package dev.anvilcraft.plasticraft.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * 实体炼药锅的查找工具。{@code getEntitiesOfClass} 要求 {@code Class<? extends Entity>}，
 * 接口无法直接作为查找类型，因此统一按 {@link AbstractPlasticEntity} 检索再收敛到 {@link PlasticCauldron}。
 */
public final class PlasticCauldrons {
    private PlasticCauldrons() {
    }

    /** 把任意引用收敛为当前确实作为炼药锅工作的实体，否则返回 {@code null}。 */
    public static @Nullable PlasticCauldron of(@Nullable Object candidate) {
        return candidate instanceof PlasticCauldron cauldron && cauldron.plasticraft$isCauldron()
            ? cauldron
            : null;
    }

    public static boolean isCauldron(@Nullable Object candidate) {
        return of(candidate) != null;
    }

    public static List<PlasticCauldron> findIn(Level level, AABB bounds) {
        return findIn(level, bounds, Entity::isAlive);
    }

    /** 按实体 ID 升序返回，保证同一格内多口锅的处理顺序稳定。 */
    public static List<PlasticCauldron> findIn(Level level, AABB bounds, Predicate<Entity> filter) {
        List<PlasticCauldron> found = new ArrayList<>();
        for (AbstractPlasticEntity entity : level.getEntitiesOfClass(
            AbstractPlasticEntity.class,
            bounds,
            candidate -> filter.test(candidate) && isCauldron(candidate)
        )) {
            found.add((PlasticCauldron) entity);
        }
        found.sort(Comparator.comparingInt(PlasticCauldron::getId));
        return found;
    }
}
