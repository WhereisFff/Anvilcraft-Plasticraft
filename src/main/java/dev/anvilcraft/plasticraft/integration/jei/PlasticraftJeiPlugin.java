package dev.anvilcraft.plasticraft.integration.jei;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.PlasticBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Optional JEI integration. The class is discovered by JEI only when that
 * mod is present, so the main mod remains loadable without the compile-only
 * JEI API at runtime.
 */
@JeiPlugin
public final class PlasticraftJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = AnvilcraftPlasticraft.of("jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
            new ItemStack(PlasticBlocks.PLASTIC_ANVIL.get()),
            RecipeTypes.ANVIL
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addItemStackInfo(
            new ItemStack(PlasticBlocks.PLASTIC_ANVIL.get()),
            Component.translatable("jei.anvilcraftplasticraft.plastic_anvil.info")
        );
    }
}
