package dev.anvilcraft.plasticraft.allay.observation;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 一份任务对某个九区块覆盖窗口的独占租约。
 *
 * <p>只有需要观察悦灵顶上去的窗口才建租约:玩家或其它加载源天然保持的区块直接复用,不建租约、
 * 不占观察悦灵,天然覆盖消失时会在下一轮规划里重新升级为需要指派。
 *
 * @param observer 被指派保持该窗口的观察悦灵
 */
public record ObservationLease(ResourceKey<Level> dimension, ChunkPos center, UUID observer) {
}
