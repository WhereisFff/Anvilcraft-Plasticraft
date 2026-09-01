package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.dubhe.anvilcraft.integration.jei.category.anvil.liquid.AbstractLiquidCategory;
import dev.dubhe.anvilcraft.integration.jei.util.JeiSlotUtil;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** 为染色固液配方补充带组件的熔体输出，避免 JEI 只显示输入而误导玩家。 */
@Mixin(AbstractLiquidCategory.class)
abstract class AbstractLiquidCategoryMixin {
    @Shadow
    @Final
    protected IDrawable slotDefault;

    private static final String DYE_RECIPE_PREFIX = "solid_liquid/dye_";
    private static final String DYE_OUTPUT_SLOT = "anvilcraft:preview/fluid/output_fluid";

    @Inject(method = "setRecipe", at = @At("TAIL"))
    private void plasticraft$addDyedMeltOutput(
        IRecipeLayoutBuilder builder,
        RecipeHolder<?> recipeHolder,
        IFocusGroup focuses,
        CallbackInfo ci
    ) {
        DyeRecipe dyeRecipe = plasticraft$getDyeRecipe(recipeHolder);
        if (dyeRecipe == null) return;
        long displayAmount = FluidType.BUCKET_VOLUME;
        FluidStack fluid = new FluidStack(dyeRecipe.material().melt(), (int) displayAmount);
        PlasticMeltColor.set(fluid, dyeRecipe.color());
        IRecipeSlotBuilder slot = builder
            .addSlot(RecipeIngredientRole.OUTPUT, JeiSlotUtil.OUTPUT_X, JeiSlotUtil.DEFAULT_Y)
            .setSlotName(DYE_OUTPUT_SLOT)
            .setFluidRenderer(displayAmount, false, 16, 16);
        slot.addFluidStack(fluid.getFluid(), displayAmount, fluid.getComponentsPatch());

        ItemStack bucket = new ItemStack(dyeRecipe.material().bucket());
        PlasticMeltColor.set(bucket, dyeRecipe.color());
        builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemStacks(List.of(bucket));
    }

    @Inject(method = "draw", at = @At("TAIL"))
    private void plasticraft$drawDyedMeltSlot(
        RecipeHolder<?> recipeHolder,
        IRecipeSlotsView recipeSlotsView,
        GuiGraphics guiGraphics,
        double mouseX,
        double mouseY,
        CallbackInfo ci
    ) {
        if (plasticraft$getDyeRecipe(recipeHolder) == null) return;
        JeiSlotUtil.drawDefaultOutputSlots(guiGraphics, this.slotDefault, 1);
    }

    private static DyeRecipe plasticraft$getDyeRecipe(RecipeHolder<?> recipeHolder) {
        if (!recipeHolder.id().getNamespace().equals(AnvilcraftPlasticraft.MOD_ID)) return null;
        String path = recipeHolder.id().getPath();
        for (PlasticMaterial material : PlasticMaterial.values()) {
            String prefix = DYE_RECIPE_PREFIX + material.key() + "_melt_";
            if (!path.startsWith(prefix)) continue;
            String colorName = path.substring(prefix.length());
            for (DyeColor color : DyeColor.values()) {
                if (color.getName().equals(colorName)) return new DyeRecipe(material, color);
            }
        }
        return null;
    }

    private record DyeRecipe(PlasticMaterial material, DyeColor color) {
    }
}
