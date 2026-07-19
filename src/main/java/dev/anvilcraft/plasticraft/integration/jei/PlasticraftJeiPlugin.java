package dev.anvilcraft.plasticraft.integration.jei;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 可选的 JEI 集成。仅当 JEI 存在时才会发现此类，
 * 因此主模组运行时即使没有仅编译期依赖的 JEI API 也能正常加载。
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
            new ItemStack(ModBlocks.HARDEND_RESIN_ANVIL.get()),
            RecipeTypes.ANVIL
        );
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        // JEI 通常会自行收集这些配方，此处仅补充缺失项，
        // 既避免其他集成将其隐藏，也避免常规环境出现重复项。
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        Set<ResourceLocation> registeredIds = jeiRuntime.getRecipeManager()
            .createRecipeLookup(RecipeTypes.CRAFTING)
            .includeHidden()
            .get()
            .map(RecipeHolder::id)
            .collect(Collectors.toSet());
        List<RecipeHolder<CraftingRecipe>> missingRecipes = minecraft.level.getRecipeManager()
            .getAllRecipesFor(RecipeType.CRAFTING)
            .stream()
            .filter(holder -> holder.id().getNamespace().equals(AnvilcraftPlasticraft.MOD_ID))
            .filter(holder -> !registeredIds.contains(holder.id()))
            .toList();
        if (!missingRecipes.isEmpty()) {
            jeiRuntime.getRecipeManager().addRecipes(RecipeTypes.CRAFTING, missingRecipes);
        }
    }
}
