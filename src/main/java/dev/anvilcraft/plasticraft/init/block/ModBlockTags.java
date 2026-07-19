package dev.anvilcraft.plasticraft.init.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** 供树脂兼容冲击系统使用的跨模组方块标签。 */
public final class ModBlockTags {
    public static final TagKey<Block> RESIN_SHOCK_COMPATIBLE = TagKey.create(
        Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath("anvilcraft", "resin_shock_compatible")
    );

    private ModBlockTags() {
    }
}
