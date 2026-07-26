package dev.anvilcraft.plasticraft.integration.jei;

import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.recipe.CatalyticPressHeat;
import dev.anvilcraft.plasticraft.recipe.CatalyticPressingRecipe;
import dev.dubhe.anvilcraft.client.support.RenderSupport;
import dev.dubhe.anvilcraft.integration.jei.util.JeiRenderHelper;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/** 展示密封锅、催化压盖和可用热源的 JEI 配方页。 */
public final class CatalyticPressingCategory
    implements IRecipeCategory<RecipeHolder<CatalyticPressingRecipe>> {
    private static final int WIDTH = 162;
    private static final int HEIGHT = 84;
    private static final int HEAT_X = 72;
    private static final int HEAT_Y = 63;
    private static final int HEAT_SIZE = 18;

    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable arrowIn;
    private final IDrawable arrowOut;
    private List<BlockState> heatSources;

    public CatalyticPressingCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemStack(ModBlocks.CATALYTIC_PRESS_LID.asStack());
        this.slot = JeiRenderHelper.getSlotDefault(helper);
        this.arrowIn = JeiRenderHelper.getArrowInput(helper);
        this.arrowOut = JeiRenderHelper.getArrowOutput(helper);
    }

    @Override
    public RecipeType<RecipeHolder<CatalyticPressingRecipe>> getRecipeType() {
        return PlasticraftJeiPlugin.CATALYTIC_PRESSING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.anvilcraftplasticraft.category.catalytic_pressing");
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
        RecipeHolder<CatalyticPressingRecipe> holder,
        IFocusGroup focuses
    ) {
        CatalyticPressingRecipe recipe = holder.value();
        int amount = recipe.fluidIngredient().amount();
        for (FluidStack fluid : recipe.fluidIngredient().getFluids()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 10, 27)
                .setFluidRenderer(amount, true, 16, 16)
                .addFluidStack(fluid.getFluid(), amount, fluid.getComponentsPatch());
        }
        for (int index = 0; index < recipe.itemIngredients().size(); index++) {
            Ingredient ingredient = recipe.itemIngredients().get(index);
            builder.addSlot(RecipeIngredientRole.INPUT, 31, 18 + index * 19)
                .addIngredients(ingredient);
        }
        FluidStack output = recipe.fluidResult();
        builder.addSlot(RecipeIngredientRole.OUTPUT, 135, 27)
            .setFluidRenderer(output.getAmount(), true, 16, 16)
            .addFluidStack(output.getFluid(), output.getAmount(), output.getComponentsPatch());
    }

    @Override
    public void draw(
        RecipeHolder<CatalyticPressingRecipe> holder,
        IRecipeSlotsView recipeSlotsView,
        GuiGraphics graphics,
        double mouseX,
        double mouseY
    ) {
        CatalyticPressingRecipe recipe = holder.value();
        this.slot.draw(graphics, 9, 26);
        for (int index = 0; index < recipe.itemIngredients().size(); index++) {
            this.slot.draw(graphics, 30, 17 + index * 19);
        }
        this.slot.draw(graphics, 134, 26);
        this.arrowIn.draw(graphics, 52, 33);
        this.arrowOut.draw(graphics, 105, 32);

        RenderSupport.renderBlock(
            graphics,
            ModBlocks.CATALYTIC_PRESS_LID.getDefaultState(),
            81,
            28,
            20,
            6.0F,
            RenderSupport.SINGLE_BLOCK
        );
        RenderSupport.renderBlock(
            graphics,
            Blocks.CAULDRON.defaultBlockState(),
            81,
            50,
            10,
            6.0F,
            RenderSupport.SINGLE_BLOCK
        );
        BlockState heat = this.displayedHeatSource();
        if (heat != null) {
            RenderSupport.renderBlock(
                graphics,
                heat,
                81,
                73,
                10,
                5.5F,
                RenderSupport.SINGLE_BLOCK
            );
        }
    }

    @Override
    public void getTooltip(
        ITooltipBuilder tooltip,
        RecipeHolder<CatalyticPressingRecipe> holder,
        IRecipeSlotsView recipeSlotsView,
        double mouseX,
        double mouseY
    ) {
        if (mouseX < HEAT_X || mouseX >= HEAT_X + HEAT_SIZE
            || mouseY < HEAT_Y || mouseY >= HEAT_Y + HEAT_SIZE) return;
        BlockState heat = this.displayedHeatSource();
        if (heat == null) return;
        int power = CatalyticPressHeat.power(heat);
        tooltip.add(heat.getBlock().getName());
        tooltip.add(Component.translatable("jei.anvilcraftplasticraft.heat_power", power));
        tooltip.add(Component.translatable(
            "jei.anvilcraftplasticraft.reaction_time",
            CatalyticPressHeat.processingTime(holder.value().processingTime(), heat)
        ));
    }

    private BlockState displayedHeatSource() {
        List<BlockState> sources = this.heatSources();
        if (sources.isEmpty()) return null;
        int index = Math.floorMod((int) (System.currentTimeMillis() / 1000L), sources.size());
        return sources.get(index);
    }

    private List<BlockState> heatSources() {
        if (this.heatSources == null) {
            this.heatSources = BuiltInRegistries.BLOCK.stream()
                .flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
                .filter(state -> CatalyticPressHeat.power(state) > 0)
                .sorted(Comparator
                    .comparingInt(CatalyticPressHeat::power)
                    .thenComparing(state -> BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()))
                .toList();
        }
        return this.heatSources;
    }
}
