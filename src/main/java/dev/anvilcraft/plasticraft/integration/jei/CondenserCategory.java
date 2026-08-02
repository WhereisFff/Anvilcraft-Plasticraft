package dev.anvilcraft.plasticraft.integration.jei;

import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.recipe.CondenserRecipe;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.client.support.RenderSupport;
import dev.dubhe.anvilcraft.integration.jei.util.JeiRenderHelper;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/** 冷凝收集的 JEI 页面：虚拟气体从左侧进入，冷凝塔从顶部收集并输出液体。 */
public final class CondenserCategory implements IRecipeCategory<RecipeHolder<CondenserRecipe>> {
    public static final int WIDTH = 162;
    public static final int HEIGHT = 64;

    private final IDrawable icon;
    private final IDrawable slot;
    private final VaporDrawableSet vaporDrawables;
    private final IDrawable arrowIn;
    private final IDrawable arrowOut;
    private final BlockState tower;

    public CondenserCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemStack(
            new ItemStack(PlasticraftBlocks.CONDENSER_TOWER)
        );
        this.slot = JeiRenderHelper.getSlotDefault(helper);
        this.vaporDrawables = new VaporDrawableSet(helper);
        this.arrowIn = JeiRenderHelper.getArrowInput(helper);
        this.arrowOut = JeiRenderHelper.getArrowOutput(helper);
        this.tower = PlasticraftBlocks.CONDENSER_TOWER.getDefaultState()
            .setValue(CondenserTowerBlock.HALF, Cube3x3PartHalf.MID_CENTER);
    }

    @Override
    public RecipeType<RecipeHolder<CondenserRecipe>> getRecipeType() {
        return PlasticraftJeiPlugin.CONDENSER;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.anvilcraftplasticraft.category.condenser");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public @Nullable IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void setRecipe(
        IRecipeLayoutBuilder builder,
        RecipeHolder<CondenserRecipe> holder,
        IFocusGroup focuses
    ) {
        CondenserRecipe recipe = holder.value();
        Fluid fluid = BuiltInRegistries.FLUID.get(recipe.fluid());
        if (fluid == null || fluid == Fluids.EMPTY) return;
        builder.addSlot(RecipeIngredientRole.OUTPUT, 120, 24)
            .addFluidStack(fluid, recipe.produce());
    }

    @Override
    public void createRecipeExtras(
        IRecipeExtrasBuilder builder,
        RecipeHolder<CondenserRecipe> holder,
        IFocusGroup focuses
    ) {
    }

    @Override
    public void draw(
        RecipeHolder<CondenserRecipe> holder,
        IRecipeSlotsView recipeSlotsView,
        GuiGraphics graphics,
        double mouseX,
        double mouseY
    ) {
        CondenserRecipe recipe = holder.value();
        this.slot.draw(graphics, 119, 23);
        this.vaporDrawables.draw(graphics, recipe.gas(), 16, 24);
        this.arrowIn.draw(graphics, 48, 30);
        this.arrowOut.draw(graphics, 96, 29);
        RenderSupport.renderBlock(graphics, this.tower, 81, 33, 10, 7.0F, RenderSupport.SINGLE_BLOCK);

        Component output = Component.literal(recipe.produce() + " mB");
        graphics.drawCenteredString(Minecraft.getInstance().font, output, 128, 45, 0xFFFFFFFF);
    }

    @Override
    public void getTooltip(
        ITooltipBuilder tooltip,
        RecipeHolder<CondenserRecipe> holder,
        IRecipeSlotsView recipeSlotsView,
        double mouseX,
        double mouseY
    ) {
        if (mouseX >= 15 && mouseX < 33 && mouseY >= 23 && mouseY < 41) {
            tooltip.add(Component.translatable("jei.anvilcraftplasticraft.gas." + holder.value().gas().getPath()));
            tooltip.add(Component.literal(holder.value().consume() + " mB"));
        }
    }
}
