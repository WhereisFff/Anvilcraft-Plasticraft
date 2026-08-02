package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.dubhe.anvilcraft.integration.jei.category.anvil.liquid.AbstractLiquidCategory;
import dev.dubhe.anvilcraft.integration.jei.util.JeiFluidUtil;
import dev.dubhe.anvilcraft.integration.jei.util.JeiSlotUtil;
import dev.dubhe.anvilcraft.recipe.component.HasCauldronSimple;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/** 让固液染色配方的 JEI 输出流体保留调色组件。 */
@Mixin(AbstractLiquidCategory.class)
abstract class AbstractLiquidCategoryMixin {
    private static final String DYE_RECIPE_PREFIX = "solid_liquid/dye_universal_plastic_melt_";
    private static final String OUTPUT_SLOT_PREFIX = "anvilcraft:preview/fluid/";

    @Redirect(
        method = "setRecipe",
        at = @At(
            value = "INVOKE",
            target = """
                Ldev/dubhe/anvilcraft/integration/jei/util/JeiFluidUtil;addFluidOutputSlot(\
                Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;Ljava/lang/String;II\
                Ldev/dubhe/anvilcraft/recipe/component/HasCauldronSimple;)V"""
        )
    )
    private void plasticraft$addColoredFluidOutput(
        IRecipeLayoutBuilder builder,
        String name,
        int width,
        int height,
        HasCauldronSimple cauldron,
        IRecipeLayoutBuilder originalBuilder,
        RecipeHolder<?> recipeHolder,
        IFocusGroup focuses
    ) {
        DyeColor color = plasticraft$getDyeColor(recipeHolder, cauldron);
        if (color == null) {
            JeiFluidUtil.addFluidOutputSlot(builder, name, width, height, cauldron);
            return;
        }
        plasticraft$addColoredOutput(
            builder,
            name,
            JeiSlotUtil.OUTPUT_X,
            JeiSlotUtil.FLUID_Y,
            width,
            height,
            cauldron,
            color
        );
    }

    @Redirect(
        method = "setRecipe",
        at = @At(
            value = "INVOKE",
            target = """
                Ldev/dubhe/anvilcraft/integration/jei/util/JeiFluidUtil;addDefaultOutputSlot(\
                Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;Ljava/lang/String;II\
                Ldev/dubhe/anvilcraft/recipe/component/HasCauldronSimple;)V"""
        )
    )
    private void plasticraft$addColoredDefaultOutput(
        IRecipeLayoutBuilder builder,
        String name,
        int width,
        int height,
        HasCauldronSimple cauldron,
        IRecipeLayoutBuilder originalBuilder,
        RecipeHolder<?> recipeHolder,
        IFocusGroup focuses
    ) {
        DyeColor color = plasticraft$getDyeColor(recipeHolder, cauldron);
        if (color == null) {
            JeiFluidUtil.addDefaultOutputSlot(builder, name, width, height, cauldron);
            return;
        }
        plasticraft$addColoredOutput(
            builder,
            name,
            JeiSlotUtil.OUTPUT_X,
            JeiSlotUtil.DEFAULT_Y,
            width,
            height,
            cauldron,
            color
        );
    }

    private static DyeColor plasticraft$getDyeColor(
        RecipeHolder<?> recipeHolder,
        HasCauldronSimple cauldron
    ) {
        if (!recipeHolder.id().getNamespace().equals(AnvilcraftPlasticraft.MOD_ID)
            || !cauldron.transform().equals(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.getId())) {
            return null;
        }
        String path = recipeHolder.id().getPath();
        if (!path.startsWith(DYE_RECIPE_PREFIX)) return null;
        String colorName = path.substring(DYE_RECIPE_PREFIX.length());
        for (DyeColor color : DyeColor.values()) {
            if (color.getName().equals(colorName)) return color;
        }
        return null;
    }

    private static void plasticraft$addColoredOutput(
        IRecipeLayoutBuilder builder,
        String name,
        int x,
        int y,
        int width,
        int height,
        HasCauldronSimple cauldron,
        DyeColor color
    ) {
        long displayAmount = cauldron.produce() > 0 ? cauldron.produce() : FluidType.BUCKET_VOLUME;
        FluidStack fluid = new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), (int) displayAmount);
        PlasticMeltColor.set(fluid, color);
        IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
            .setSlotName(OUTPUT_SLOT_PREFIX + name)
            .setFluidRenderer(displayAmount, false, width, height);
        slot.addFluidStack(fluid.getFluid(), displayAmount, fluid.getComponentsPatch());

        ItemStack bucket = new ItemStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get().getBucket());
        PlasticMeltColor.set(bucket, color);
        builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemStacks(List.of(bucket));
    }
}
