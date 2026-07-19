package dev.anvilcraft.plasticraft.data;

import dev.anvilcraft.lib.v2.registrum.providers.ProviderType;
import dev.anvilcraft.lib.v2.registrum.util.DataIngredient;
import dev.anvilcraft.plasticraft.init.PlasticBlocks;
import dev.anvilcraft.plasticraft.init.PlasticItemGroups;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.world.item.Items;

import static dev.anvilcraft.plasticraft.AnvilcraftPlasticraft.REGISTRUM;

/** Data generation registrations kept in one small, version-isolated class. */
public final class PlasticDatagen {
    private PlasticDatagen() {
    }

    public static void init() {
        REGISTRUM.addDataGenerator(ProviderType.LANG, provider -> {
            provider.add(PlasticItemGroups.TITLE_KEY, "Anvilcraft: Plasticraft");
            provider.add("item.anvilcraftplasticraft.plastic_anvil", "Plastic Anvil");
            provider.add("item.anvilcraftplasticraft.plastic_pot", "Plastic Pot");
            provider.add("tooltip.anvilcraftplasticraft.magnetized", "Magnetized");
            provider.add(
                "jei.anvilcraftplasticraft.plastic_anvil.info",
                "A solid, pushable anvil entity with the vanilla anvil workflow."
            );
            provider.add("tooltip.anvilcraftplasticraft.jade.color", "Plastic color: %s");
            provider.add("tooltip.anvilcraftplasticraft.jade.pushable", "Can be pushed by entities");
            provider.add(
                "config.jade.plugin_anvilcraftplasticraft.plastic_anvil",
                "Plastic Anvil"
            );
            // AnvilCraft 1.6 exposes this Jade key but omits it from its
            // language generator. Keep local runs usable without patching the
            // required upstream artifact.
            provider.add("config.jade.plugin_anvilcraft.fluid_tank", "Fluid Tank");
        });
        REGISTRUM.addDataGenerator(ProviderType.RECIPE, provider ->
            provider.singleItem(
                DataIngredient.items(Items.SNOWBALL),
                RecipeCategory.BUILDING_BLOCKS,
                PlasticBlocks.PLASTIC_ANVIL::asItem,
                1,
                1
            )
        );
    }
}
