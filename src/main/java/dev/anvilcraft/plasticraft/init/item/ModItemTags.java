package dev.anvilcraft.plasticraft.init.item;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModItemTags {
    public static final TagKey<Item> PLASTIC_ANVILS = TagKey.create(
        Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath(AnvilcraftPlasticraft.MOD_ID, "plastic_anvils")
    );
    public static final TagKey<Item> BUOYANT_PLASTIC_ITEMS = TagKey.create(
        Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath(AnvilcraftPlasticraft.MOD_ID, "buoyant_plastic_items")
    );
    public static final TagKey<Item> PLASTIC_CAULDRONS = TagKey.create(
        Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath(AnvilcraftPlasticraft.MOD_ID, "plastic_cauldrons")
    );

    private ModItemTags() {
    }
}
