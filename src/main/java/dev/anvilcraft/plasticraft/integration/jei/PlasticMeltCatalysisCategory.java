package dev.anvilcraft.plasticraft.integration.jei;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.recipe.CatalyticPressHeat;
import dev.dubhe.anvilcraft.integration.jei.util.JeiFluidUtil;
import dev.dubhe.anvilcraft.integration.jei.util.JeiRenderHelper;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.List;

public final class PlasticMeltCatalysisCategory implements IRecipeCategory<PlasticMeltCatalysisRecipe> {
    private static final String TEXT_PREFIX = "jei.anvilcraftplasticraft.plastic_melt_catalysis.";
    private static final String INPUT_FLUID = "input_fluid";
    private static final String OUTPUT_FLUID = "output_fluid";
    private static final String ENVIRONMENT = "environment";
    private static final int FLUID_Y = 14;
    private static final float ENVIRONMENT_Y = FLUID_Y + CatalysisBlockRenderer.STACK_Y_OFFSET;
    private static final int DETAILS_Y = 82;
    private static final CatalysisBlockRenderer<FluidStack> FLUID_RENDERER = new CatalysisBlockRenderer<>(
        fluid -> List.of(fluid.getHoverName(), Component.literal(fluid.getAmount() + " mB"))
    );
    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable arrow;

    public PlasticMeltCatalysisCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemStack(new ItemStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get().getBucket()));
        this.slot = JeiRenderHelper.getSlotDefault(helper);
        this.arrow = JeiRenderHelper.getArrowOutput(helper);
    }

    @Override
    public RecipeType<PlasticMeltCatalysisRecipe> getRecipeType() {
        return PlasticraftJeiPlugin.PLASTIC_MELT_CATALYSIS;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.anvilcraftplasticraft.category.plastic_melt_catalysis");
    }

    @Override
    public int getWidth() {
        return 176;
    }

    @Override
    public int getHeight() {
        return 94;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public ResourceLocation getRegistryName(PlasticMeltCatalysisRecipe recipe) {
        return AnvilcraftPlasticraft.of("jei/plastic_melt_catalysis/"
            + BuiltInRegistries.FLUID.getKey(recipe.output()).getPath() + "/" + recipe.catalyst().location().getPath());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, PlasticMeltCatalysisRecipe recipe, IFocusGroup focuses) {
        JeiFluidUtil.addFluidSlot(
            builder, RecipeIngredientRole.INPUT, 24, FLUID_Y, 32, 32, FluidType.BUCKET_VOLUME, true,
            List.of(new FluidStack(recipe.input(), FluidType.BUCKET_VOLUME))
        ).setCustomRenderer(NeoForgeTypes.FLUID_STACK, FLUID_RENDERER).setSlotName(INPUT_FLUID);
        JeiFluidUtil.addFluidSlot(
            builder, RecipeIngredientRole.OUTPUT, 120, FLUID_Y, 32, 32, FluidType.BUCKET_VOLUME, true,
            List.of(new FluidStack(recipe.output(), FluidType.BUCKET_VOLUME))
        ).setCustomRenderer(NeoForgeTypes.FLUID_STACK, FLUID_RENDERER).setSlotName(OUTPUT_FLUID);
        if (!recipe.environmentBlocks().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.CATALYST, 24, Math.round(ENVIRONMENT_Y))
                .setSlotName(ENVIRONMENT)
                .setCustomRenderer(VanillaTypes.ITEM_STACK, new CatalysisBlockRenderer<>(
                    stack -> environmentTooltip(recipe, stack)
                ))
                .addItemStacks(recipe.environmentStacks());
        }
        builder.addSlot(RecipeIngredientRole.CATALYST, 80, 48)
            .addIngredients(Ingredient.of(recipe.catalyst()))
            .addRichTooltipCallback((slotView, tooltip) -> {
                tooltip.add(Component.translatable(TEXT_PREFIX + "not_consumed"));
                tooltip.add(Component.translatable(TEXT_PREFIX + "variety"));
            });
    }

    @Override
    public void draw(
        PlasticMeltCatalysisRecipe recipe,
        IRecipeSlotsView recipeSlotsView,
        GuiGraphics graphics,
        double mouseX,
        double mouseY
    ) {
        Font font = Minecraft.getInstance().font;
        this.slot.draw(graphics, 79, 47);
        this.arrow.draw(graphics, 80, 27);
        recipeSlotsView.findSlotByName(ENVIRONMENT)
            .flatMap(IRecipeSlotView::getDisplayedItemStack)
            .ifPresent(stack -> CatalysisBlockRenderer.drawBlock(
                graphics, recipe.environmentState(stack), 24, ENVIRONMENT_Y, 10
            ));
        drawFluid(recipeSlotsView, INPUT_FLUID, graphics, 24);
        drawFluid(recipeSlotsView, OUTPUT_FLUID, graphics, 120);
        graphics.drawWordWrap(font, Component.translatable(TEXT_PREFIX + "catalyst", recipe.frost() ? 50 : 100),
            104, 46, 68, 0xFF404040);
        graphics.drawCenteredString(font, "1000 mB", 40, 4, 0xFFFFFFFF);
        graphics.drawCenteredString(font, "1000 mB", 136, 4, 0xFFFFFFFF);
        if (recipe.environmentBlocks().isEmpty()) {
            graphics.drawWordWrap(font, Component.translatable(TEXT_PREFIX + recipe.environment()), 4, 48, 70, 0xFF404040);
        }
        graphics.drawCenteredString(font, Component.translatable(TEXT_PREFIX + "details"), 88, DETAILS_Y, 0xFF808080);
    }

    private static void drawFluid(IRecipeSlotsView slots, String name, GuiGraphics graphics, int x) {
        slots.findSlotByName(name)
            .flatMap(slot -> slot.getDisplayedIngredient(NeoForgeTypes.FLUID_STACK))
            .ifPresent(fluid -> CatalysisBlockRenderer.drawBlock(
                graphics, fluid.getFluid().defaultFluidState().createLegacyBlock(), x, FLUID_Y, 20
            ));
    }

    @Override
    public void getTooltip(
        ITooltipBuilder tooltip,
        PlasticMeltCatalysisRecipe recipe,
        IRecipeSlotsView recipeSlotsView,
        double mouseX,
        double mouseY
    ) {
        if (mouseX < 0 || mouseX >= getWidth() || mouseY < DETAILS_Y - 3 || mouseY >= getHeight()) return;
        tooltip.add(Component.translatable(TEXT_PREFIX + recipe.environment()));
        tooltip.add(Component.translatable(TEXT_PREFIX + "containers"));
        tooltip.add(Component.translatable(TEXT_PREFIX + "preserved"));
        tooltip.add(Component.translatable(TEXT_PREFIX + "variety"));
        tooltip.add(Component.translatable(TEXT_PREFIX + recipe.priority()));
    }

    private static List<Component> environmentTooltip(PlasticMeltCatalysisRecipe recipe, ItemStack stack) {
        BlockState state = recipe.environmentState(stack);
        Component condition = Component.translatable(TEXT_PREFIX + recipe.environment());
        if (recipe.environment().equals("heat")) {
            return List.of(
                state.getBlock().getName(),
                condition,
                Component.translatable(TEXT_PREFIX + "heat_power", CatalyticPressHeat.power(state))
            );
        }
        return List.of(state.getBlock().getName(), condition);
    }
}
