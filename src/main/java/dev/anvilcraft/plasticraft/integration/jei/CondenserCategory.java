package dev.anvilcraft.plasticraft.integration.jei;

import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.recipe.CondenserRecipe;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.client.support.RenderSupport;
import dev.dubhe.anvilcraft.integration.jei.util.JeiRenderHelper;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
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
import org.jetbrains.annotations.Nullable;

/** 冷凝收集的 JEI 页面：虚拟气体从左侧进入，冷凝塔从顶部收集并输出液体。 */
public final class CondenserCategory implements IRecipeCategory<RecipeHolder<CondenserRecipe>> {
    public static final int WIDTH = 162;
    public static final int HEIGHT = 64;

    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable arrowIn;
    private final IDrawable arrowOutFromBelow;
    private final BlockState tower;

    public CondenserCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemStack(
            new ItemStack(dev.anvilcraft.plasticraft.init.block.ModBlocks.CONDENSER_TOWER)
        );
        this.slot = JeiRenderHelper.getSlotDefault(helper);
        this.arrowIn = JeiRenderHelper.getArrowInput(helper);
        this.arrowOutFromBelow = JeiRenderHelper.getArrowOutputFromBelow(helper);
        this.tower = dev.anvilcraft.plasticraft.init.block.ModBlocks.CONDENSER_TOWER.getDefaultState()
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
        if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) return;
        builder.addSlot(RecipeIngredientRole.OUTPUT, 126, 24)
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
        this.slot.draw(graphics, 125, 23);
        this.arrowIn.draw(graphics, 50, 36);
        graphics.pose().pushPose();
        graphics.pose().translate(98.0F, 40.0F, 0.0F);
        graphics.pose().scale(1.0F, -1.0F, 1.0F);
        this.arrowOutFromBelow.draw(graphics, 0, 0);
        graphics.pose().popPose();
        RenderSupport.renderBlock(graphics, this.tower, 81, 40, 10, 7.0F, RenderSupport.SINGLE_BLOCK);

        Component gasName = Component.translatable(
            "jei.anvilcraftplasticraft.gas." + recipe.gas().getPath()
        );
        Minecraft minecraft = Minecraft.getInstance();
        drawFittedText(graphics, gasName, 24.0F, 5.0F, 46.0F);
        drawFittedText(
            graphics,
            Component.literal(recipe.consume() + " mB"),
            24.0F,
            16.0F,
            46.0F
        );
        Component output = Component.literal(recipe.produce() + " mB");
        graphics.drawString(
            minecraft.font,
            output,
            WIDTH - minecraft.font.width(output),
            45,
            0xFF202020,
            false
        );
    }

    @Override
    public void getTooltip(
        mezz.jei.api.gui.builder.ITooltipBuilder tooltip,
        RecipeHolder<CondenserRecipe> holder,
        IRecipeSlotsView recipeSlotsView,
        double mouseX,
        double mouseY
    ) {
        if (mouseX >= 0 && mouseX < 48 && mouseY >= 4 && mouseY < 22) {
            tooltip.add(Component.translatable("jei.anvilcraftplasticraft.gas." + holder.value().gas().getPath()));
        }
    }

    private static void drawFittedText(
        GuiGraphics graphics,
        Component text,
        float centerX,
        float y,
        float maxWidth
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        float scale = Math.min(0.75F, maxWidth / Math.max(1, minecraft.font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(minecraft.font, text, 0, 0, 0xFF202020);
        graphics.pose().popPose();
    }
}
