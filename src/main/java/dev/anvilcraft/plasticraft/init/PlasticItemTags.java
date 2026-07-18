package dev.anvilcraft.plasticraft.init;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class PlasticItemTags {
    public static final TagKey<Item> PLASTIC_ANVILS = TagKey.create(
        Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath(AnvilcraftPlasticraft.MOD_ID, "plastic_anvils")
    );

    private PlasticItemTags() {
    }
}
