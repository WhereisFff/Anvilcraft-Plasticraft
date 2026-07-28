package dev.anvilcraft.plasticraft.integration.jei;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.anvilcraft.lib.v2.util.predicate.ChanceItemStack;
import dev.anvilcraft.lib.v2.util.predicate.ItemIngredientPredicate;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.recipe.CondenserGas;
import dev.anvilcraft.plasticraft.recipe.CondenserTowerProcess;
import dev.anvilcraft.plasticraft.recipe.PlasmaJetBlastingRecipe;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.client.support.RenderSupport;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.integration.jei.util.JeiFluidUtil;
import dev.dubhe.anvilcraft.integration.jei.util.JeiRecipeUtil;
import dev.dubhe.anvilcraft.integration.jei.util.JeiRenderHelper;
import dev.dubhe.anvilcraft.recipe.anvil.predicate.block.HasCauldron;
import dev.dubhe.anvilcraft.recipe.component.HasCauldronSimple;
import mezz.jei.api.gui.ITickTimer;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
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
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import org.jetbrains.annotations.Nullable;

/** 喷流灼烧配方的 JEI 页面。 */
public final class PlasmaJetBlastingCategory implements IRecipeCategory<RecipeHolder<PlasmaJetBlastingRecipe>> {
    public static final int WIDTH = 162;
    public static final int HEIGHT = 64;
    private static final String INPUT_FLUID = "input_fluid";
    private static final String OUTPUT_FLUID = "output_fluid";
    private static final int PLASMA_PARTICLE_COUNT = 32;
    private static final int PLASMA_PARTICLE_CENTER_X = 78;
    private static final ResourceLocation[] PLASMA_PARTICLE_TEXTURES = {
        ResourceLocation.withDefaultNamespace("textures/particle/generic_0.png"),
        ResourceLocation.withDefaultNamespace("textures/particle/generic_1.png"),
        ResourceLocation.withDefaultNamespace("textures/particle/generic_2.png"),
        ResourceLocation.withDefaultNamespace("textures/particle/generic_3.png"),
        ResourceLocation.withDefaultNamespace("textures/particle/generic_4.png"),
        ResourceLocation.withDefaultNamespace("textures/particle/generic_5.png"),
        ResourceLocation.withDefaultNamespace("textures/particle/generic_6.png"),
        ResourceLocation.withDefaultNamespace("textures/particle/generic_7.png")
    };

    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable arrowIn;
    private final IDrawable arrowOut;
    private final IDrawable steam;
    private final ITickTimer timer;
    private final BlockState largeCauldron;

    public PlasmaJetBlastingCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemStack(ModBlocks.LARGE_CAULDRON.asStack());
        this.slot = JeiRenderHelper.getSlotDefault(helper);
        this.arrowIn = JeiRenderHelper.getArrowInput(helper);
        this.arrowOut = JeiRenderHelper.getArrowOutput(helper);
        this.steam = helper.drawableBuilder(
            AnvilcraftPlasticraft.of("textures/gui/jei/steam.png"),
            0,
            0,
            16,
            16
        ).setTextureSize(16, 16).build();
        this.timer = helper.createTickTimer(20, 20, false);
        this.largeCauldron = ModBlocks.LARGE_CAULDRON.getDefaultState()
            .setValue(LargeCauldronBlock.HALF, Cube3x3PartHalf.MID_CENTER);
    }

    @Override
    public RecipeType<RecipeHolder<PlasmaJetBlastingRecipe>> getRecipeType() {
        return PlasticraftJeiPlugin.PLASMA_JET_BLASTING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.anvilcraftplasticraft.category.plasma_jet_blasting");
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
        RecipeHolder<PlasmaJetBlastingRecipe> holder,
        IFocusGroup focuses
    ) {
        PlasmaJetBlastingRecipe recipe = holder.value();
        boolean splitInputColumns = !recipe.getInputItems().isEmpty() && recipe.hasFluidInput();
        for (int index = 0; index < recipe.getInputItems().size(); index++) {
            ItemIngredientPredicate ingredient = recipe.getInputItems().get(index);
            Position position = itemPosition(
                true,
                recipe.getInputItems().size(),
                index,
                splitInputColumns
            );
            builder.addSlot(RecipeIngredientRole.INPUT, position.x() + 1, position.y() + 1)
                .addIngredients(Ingredient.of(ingredient.getItems()));
        }
        if (recipe.hasFluidInput()) {
            Position position = fluidPosition(true, 1, 0, splitInputColumns);
            if (hasRenderableInput(recipe.getHasCauldron())) {
                JeiFluidUtil.addInputSlot(
                    builder,
                    INPUT_FLUID,
                    position.x() + 1,
                    position.y() + 1,
                    16,
                    16,
                    recipe.getHasCauldron()
                );
            }
        }

        boolean splitOutputColumns = !recipe.getResultItems().isEmpty() && recipe.hasFluidOutput();
        for (int index = 0; index < recipe.getResultItems().size(); index++) {
            ChanceItemStack result = recipe.getResultItems().get(index);
            Position position = itemPosition(
                false,
                recipe.getResultItems().size(),
                index,
                splitOutputColumns
            );
            ItemStack stack = result.stack().copy();
            if (result.count() instanceof ConstantValue) stack.setCount(result.getMaxCount());
            IRecipeSlotBuilder resultSlot = builder.addSlot(
                RecipeIngredientRole.OUTPUT,
                position.x() + 1,
                position.y() + 1
            ).addItemStack(stack);
            JeiRecipeUtil.addTooltips(resultSlot, result.getMaxCount(), result.count());
        }
        if (recipe.hasFluidOutput()) {
            Position position = fluidPosition(false, 1, 0, splitOutputColumns);
            if (hasRegisteredFluid(recipe.getHasCauldron().transform())) {
                JeiFluidUtil.addOutputSlot(
                    builder,
                    OUTPUT_FLUID,
                    position.x() + 1,
                    position.y() + 1,
                    16,
                    16,
                    recipe.getHasCauldron()
                );
            }
        }
    }

    @Override
    public void createRecipeExtras(
        IRecipeExtrasBuilder builder,
        RecipeHolder<PlasmaJetBlastingRecipe> holder,
        IFocusGroup focuses
    ) {
        JeiFluidUtil.suppressHoverOverlays(builder);
    }

    @Override
    public void draw(
        RecipeHolder<PlasmaJetBlastingRecipe> holder,
        IRecipeSlotsView recipeSlotsView,
        GuiGraphics graphics,
        double mouseX,
        double mouseY
    ) {
        PlasmaJetBlastingRecipe recipe = holder.value();
        boolean splitInputColumns = !recipe.getInputItems().isEmpty() && recipe.hasFluidInput();
        for (int index = 0; index < recipe.getInputItems().size(); index++) {
            Position position = itemPosition(
                true,
                recipe.getInputItems().size(),
                index,
                splitInputColumns
            );
            this.slot.draw(graphics, position.x(), position.y());
        }
        if (recipe.hasFluidInput() && hasRenderableInput(recipe.getHasCauldron())) {
            Position position = fluidPosition(true, 1, 0, splitInputColumns);
            this.slot.draw(graphics, position.x(), position.y());
        }

        boolean splitOutputColumns = !recipe.getResultItems().isEmpty() && recipe.hasFluidOutput();
        for (int index = 0; index < recipe.getResultItems().size(); index++) {
            Position position = itemPosition(
                false,
                recipe.getResultItems().size(),
                index,
                splitOutputColumns
            );
            this.slot.draw(graphics, position.x(), position.y());
        }
        if (recipe.hasFluidOutput() && hasRegisteredFluid(recipe.getHasCauldron().transform())) {
            Position position = fluidPosition(false, 1, 0, splitOutputColumns);
            this.slot.draw(graphics, position.x(), position.y());
        }

        this.arrowIn.draw(graphics, 46, 30);
        this.arrowOut.draw(graphics, 100, 29);
        drawPlasmaParticles(graphics, PlasticraftJeiPlugin.isEnhancedRecipe(holder));
        RenderSupport.renderBlock(
            graphics,
            this.largeCauldron,
            81,
            39,
            10,
            6.5F,
            RenderSupport.SINGLE_BLOCK
        );

        if (recipe.hasFluidInput() && isVirtualFluidInput(recipe.getHasCauldron())) {
            drawVirtualFluid(
                graphics,
                fluidPosition(true, 1, 0, splitInputColumns),
                recipe.getHasCauldron().fluid()
            );
        }
        if (recipe.hasFluidOutput() && isVirtualFluid(recipe.getHasCauldron().transform())) {
            drawVirtualFluid(
                graphics,
                fluidPosition(false, 1, 0, splitOutputColumns),
                recipe.getHasCauldron().transform()
            );
        }
    }

    @Override
    public void getTooltip(
        ITooltipBuilder tooltip,
        RecipeHolder<PlasmaJetBlastingRecipe> holder,
        IRecipeSlotsView recipeSlotsView,
        double mouseX,
        double mouseY
    ) {
        PlasmaJetBlastingRecipe recipe = holder.value();
        boolean splitInputColumns = !recipe.getInputItems().isEmpty() && recipe.hasFluidInput();
        boolean splitOutputColumns = !recipe.getResultItems().isEmpty() && recipe.hasFluidOutput();
        if (recipe.hasFluidInput() && isVirtualFluidInput(recipe.getHasCauldron())) {
            Position position = fluidPosition(true, 1, 0, splitInputColumns);
            if (inside(position, mouseX, mouseY)) {
                addVirtualFluidTooltip(tooltip, recipe.getHasCauldron().fluid(), recipe.getHasCauldron().consume());
            }
        } else if (recipe.hasFluidInput()) {
            Position position = fluidPosition(true, 1, 0, splitInputColumns);
            if (inside(position, mouseX, mouseY)) {
                tooltip.add(Component.literal(recipe.getHasCauldron().consume() + " mB"));
            }
        }
        if (recipe.hasFluidOutput() && isVirtualFluid(recipe.getHasCauldron().transform())) {
            Position position = fluidPosition(false, 1, 0, splitOutputColumns);
            if (inside(position, mouseX, mouseY)) {
                ResourceLocation output = recipe.getHasCauldron().transform();
                tooltip.add(virtualFluidName(output));
                if (CondenserGas.isGas(output)) {
                    int rate = PlasticraftJeiPlugin.isEnhancedRecipe(holder)
                        ? CondenserTowerProcess.ENHANCED_VAPORIZATION_PER_JET
                        : CondenserTowerProcess.VAPORIZATION_PER_JET;
                    tooltip.add(Component.translatable(
                        "jei.anvilcraftplasticraft.vaporization_rate",
                        rate
                    ));
                } else {
                    tooltip.add(virtualAmount(output, recipe.getHasCauldron().produce()));
                }
            }
        } else if (recipe.hasFluidOutput()) {
            Position position = fluidPosition(false, 1, 0, splitOutputColumns);
            if (inside(position, mouseX, mouseY)) {
                tooltip.add(Component.literal(recipe.getHasCauldron().produce() + " mB"));
            }
        }
    }

    private static boolean hasRenderableInput(HasCauldronSimple cauldron) {
        return cauldron.fluidTag() != null || hasRegisteredFluid(cauldron.fluid());
    }

    private static boolean isVirtualFluidInput(HasCauldronSimple cauldron) {
        return cauldron.fluidTag() == null && isVirtualFluid(cauldron.fluid());
    }

    private static boolean isVirtualFluid(ResourceLocation id) {
        return HasCauldron.isNotEmpty(id) && !hasRegisteredFluid(id);
    }

    private static boolean hasRegisteredFluid(ResourceLocation id) {
        if (!HasCauldron.isNotEmpty(id)) return false;
        Fluid fluid = BuiltInRegistries.FLUID.get(id);
        return fluid != null && fluid != Fluids.EMPTY;
    }

    private static Component virtualFluidName(ResourceLocation id) {
        return Component.translatable("jei.anvilcraftplasticraft.gas." + id.getPath());
    }

    private void drawVirtualFluid(
        GuiGraphics graphics,
        Position position,
        ResourceLocation id
    ) {
        if (CondenserGas.GASEOUS_EXPERIENCE.equals(CondenserGas.canonicalize(id))) {
            this.drawExperienceOrb(graphics, position);
        } else {
            this.steam.draw(graphics, position.x() + 1, position.y() + 1);
        }
    }

    private static void addVirtualFluidTooltip(ITooltipBuilder tooltip, ResourceLocation id, int amount) {
        tooltip.add(virtualFluidName(id));
        tooltip.add(virtualAmount(id, amount));
    }

    private static Component virtualAmount(ResourceLocation id, int amount) {
        return Component.literal(amount + " mB");
    }

    private void drawPlasmaParticles(GuiGraphics graphics, boolean enhanced) {
        int time = this.timer.getValue();
        try {
            for (int i = 0; i < PLASMA_PARTICLE_COUNT; i++) {
                int phase = Math.floorMod(i * 17 + i * i * 3, 24);
                int elapsed = enhanced ? time * 12 / 5 : time * 2;
                int age = Math.floorMod(elapsed + phase, 24);
                float progress = age / 24.0F;
                float red = enhanced ? 0.24F + progress * 0.58F : 1.0F;
                float green = enhanced ? 0.82F + progress * 0.12F : 1.0F;
                float blue = 1.0F;
                if (!enhanced && progress < 1.0F / 3.0F) {
                    blue -= progress * 3.0F;
                } else if (!enhanced && progress < 2.0F / 3.0F) {
                    green -= (progress - 1.0F / 3.0F) * 3.0F;
                    blue = 0.0F;
                } else if (!enhanced) {
                    red -= (progress - 2.0F / 3.0F) * 3.0F;
                    green = 0.0F;
                    blue = 0.0F;
                }
                ResourceLocation texture = PLASMA_PARTICLE_TEXTURES[Math.min(7, age / 3)];
                int size = 3 + i % 3;
                int halfWidth = progress >= 0.75F ? 5 : 4;
                int offsetX = Math.floorMod(i * 11 + age / 4 * 3, halfWidth * 2 + 1) - halfWidth;
                int x = PLASMA_PARTICLE_CENTER_X + offsetX - size / 2;
                int y = 61 - age * 19 / 24;
                RenderSystem.setShaderColor(red, green, blue, 0.95F);
                graphics.blit(texture, x, y, 0, 0, size, size, 8, 8);
            }
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void drawExperienceOrb(GuiGraphics graphics, Position position) {
        drawExperienceOrb(graphics, position.x(), position.y(), this.timer.getValue());
    }

    static void drawExperienceOrb(GuiGraphics graphics, int x, int y, int tick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        ExperienceOrb orb = new ExperienceOrb(minecraft.level, 0.0D, 0.0D, 0.0D, 3);
        orb.tickCount = tick;
        EntityRenderer<? super ExperienceOrb> renderer = minecraft.getEntityRenderDispatcher().getRenderer(orb);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 9.0F, y + 15.0F, 120.0F);
        graphics.pose().scale(30.0F, -30.0F, 30.0F);
        renderer.render(
            orb,
            0.0F,
            0.0F,
            graphics.pose(),
            minecraft.renderBuffers().bufferSource(),
            LightTexture.FULL_BRIGHT
        );
        graphics.pose().popPose();
        graphics.flush();
    }

    private static boolean inside(Position position, double mouseX, double mouseY) {
        return mouseX >= position.x() && mouseX < position.x() + 18
            && mouseY >= position.y() && mouseY < position.y() + 18;
    }

    private static Position itemPosition(
        boolean input,
        int count,
        int index,
        boolean splitColumns
    ) {
        int x = input ? splitColumns ? 6 : 15 : splitColumns ? 119 : 129;
        return new Position(x, slotRow(count, index));
    }

    private static Position fluidPosition(
        boolean input,
        int count,
        int index,
        boolean splitColumns
    ) {
        int x = input ? splitColumns ? 25 : 15 : splitColumns ? 138 : 129;
        return new Position(x, slotRow(count, index));
    }

    private static int slotRow(int count, int index) {
        if (count <= 1) return 24;
        if (count == 2) return 14 + index * 19;
        return 5 + index * 19;
    }

    private record Position(int x, int y) {
    }
}
