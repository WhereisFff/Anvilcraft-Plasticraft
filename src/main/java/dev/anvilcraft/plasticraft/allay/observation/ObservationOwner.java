package dev.anvilcraft.plasticraft.allay.observation;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 一份观察覆盖的持票人。区块票按持票人分组,因此不同持票人在同一区块上的票互不影响,
 * 撤掉其中一张不会让另一张失效——这正是重叠覆盖的引用计数来源。
 */
public sealed interface ObservationOwner {
    ResourceKey<Level> dimension();

    /** 世界中的观察悦灵实体,按 UUID 持票。 */
    record Observer(ResourceKey<Level> dimension, UUID entityId) implements ObservationOwner {
    }

    /** 托管着有效观察悦灵的休息室,按方块坐标持票。 */
    record Lounge(ResourceKey<Level> dimension, BlockPos pos) implements ObservationOwner {
    }

    static Observer observer(ResourceKey<Level> dimension, UUID entityId) {
        return new Observer(dimension, entityId);
    }

    static Lounge lounge(ResourceKey<Level> dimension, BlockPos pos) {
        return new Lounge(dimension, pos.immutable());
    }
}
