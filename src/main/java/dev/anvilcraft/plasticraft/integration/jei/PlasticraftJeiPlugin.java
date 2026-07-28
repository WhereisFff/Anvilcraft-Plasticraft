package dev.anvilcraft.plasticraft.integration.jei;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.ModRecipeTypes;
import dev.anvilcraft.plasticraft.recipe.PlasmaJetBlastingRecipe;
import dev.anvilcraft.plasticraft.recipe.CondenserRecipe;
import dev.dubhe.anvilcraft.integration.jei.AnvilCraftJeiPlugin;
import dev.dubhe.anvilcraft.integration.jei.util.JeiRecipeUtil;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
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
    private static final String ENHANCED_RECIPE_PREFIX = "jei/enhanced/";
    public static final mezz.jei.api.recipe.RecipeType<RecipeHolder<PlasmaJetBlastingRecipe>> PLASMA_JET_BLASTING =
        mezz.jei.api.recipe.RecipeType.createRecipeHolderType(
            AnvilcraftPlasticraft.of("plasma_jet_blasting")
        );
    public static final mezz.jei.api.recipe.RecipeType<RecipeHolder<CondenserRecipe>> CONDENSER =
        mezz.jei.api.recipe.RecipeType.createRecipeHolderType(
            AnvilcraftPlasticraft.of("condenser")
        );
    private static final List<mezz.jei.api.recipe.RecipeType<?>> ANVIL_PROCESSING_TYPES = List.of(
        AnvilCraftJeiPlugin.MESH,
        AnvilCraftJeiPlugin.BLOCK_COMPRESS,
        AnvilCraftJeiPlugin.BLOCK_CRUSH,
        AnvilCraftJeiPlugin.BLOCK_SMEAR,
        AnvilCraftJeiPlugin.ITEM_CRUSH,
        AnvilCraftJeiPlugin.SQUEEZING,
        AnvilCraftJeiPlugin.ITEM_INJECT,
        AnvilCraftJeiPlugin.MASS_INJECT,
        AnvilCraftJeiPlugin.ITEM_COMPRESS,
        AnvilCraftJeiPlugin.UNPACK,
        AnvilCraftJeiPlugin.FAST_COOKING,
        AnvilCraftJeiPlugin.STAMPING,
        AnvilCraftJeiPlugin.SUPER_HEATING,
        AnvilCraftJeiPlugin.SOLID_LIQUID,
        AnvilCraftJeiPlugin.TIME_WARP,
        AnvilCraftJeiPlugin.NEUTRON_IRRADIATION,
        AnvilCraftJeiPlugin.PROCEDURAL_PROCESS
    );
    private static final List<mezz.jei.api.recipe.RecipeType<?>> CAULDRON_PROCESSING_TYPES = List.of(
        AnvilCraftJeiPlugin.FAST_COOKING,
        AnvilCraftJeiPlugin.ITEM_COMPRESS,
        AnvilCraftJeiPlugin.NEUTRON_IRRADIATION,
        AnvilCraftJeiPlugin.SOLID_LIQUID,
        AnvilCraftJeiPlugin.SQUEEZING,
        AnvilCraftJeiPlugin.SUPER_HEATING,
        AnvilCraftJeiPlugin.TIME_WARP
    );

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new PlasmaJetBlastingCategory(
            registration.getJeiHelpers().getGuiHelper()
        ));
        registration.addRecipeCategories(new CondenserCategory(
            registration.getJeiHelpers().getGuiHelper()
        ));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<RecipeHolder<PlasmaJetBlastingRecipe>> plasmaRecipes =
            JeiRecipeUtil.getRecipeHoldersFromType(ModRecipeTypes.PLASMA_JET_BLASTING_TYPE.get());
        List<RecipeHolder<PlasmaJetBlastingRecipe>> displayRecipes = new ArrayList<>(plasmaRecipes.size() * 2);
        for (RecipeHolder<PlasmaJetBlastingRecipe> holder : plasmaRecipes) {
            displayRecipes.add(holder);
            displayRecipes.add(new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath(
                    holder.id().getNamespace(),
                    ENHANCED_RECIPE_PREFIX + holder.id().getPath()
                ),
                holder.value()
            ));
        }
        registration.addRecipes(
            PLASMA_JET_BLASTING,
            displayRecipes
        );
        registration.addRecipes(
            CONDENSER,
            JeiRecipeUtil.getRecipeHoldersFromType(ModRecipeTypes.CONDENSER_TYPE.get())
        );
    }

    static boolean isEnhancedRecipe(RecipeHolder<PlasmaJetBlastingRecipe> holder) {
        return holder.id().getPath().startsWith(ENHANCED_RECIPE_PREFIX);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
            dev.dubhe.anvilcraft.init.block.ModBlocks.LARGE_CAULDRON.asStack(),
            PLASMA_JET_BLASTING
        );
        registration.addRecipeCatalyst(ModBlocks.CONDENSER_TOWER.asStack(), PLASMA_JET_BLASTING);
        registration.addRecipeCatalyst(ModBlocks.CONDENSER_TOWER.asStack(), CONDENSER);
        for (mezz.jei.api.recipe.RecipeType<?> recipeType : ANVIL_PROCESSING_TYPES) {
            registration.addRecipeCatalyst(ModBlocks.RESIN_ANVIL.asStack(), recipeType);
            registration.addRecipeCatalyst(ModBlocks.HARDEND_RESIN_ANVIL.asStack(), recipeType);
        }
        // 液体混合不在此列表中，继续只接受大型炼药锅和巨型铁砧。
        for (mezz.jei.api.recipe.RecipeType<?> recipeType : CAULDRON_PROCESSING_TYPES) {
            registration.addRecipeCatalyst(ModBlocks.HARDEND_RESIN_CAULDRON.asStack(), recipeType);
        }
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
