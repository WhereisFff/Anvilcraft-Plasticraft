package dev.anvilcraft.plasticraft.integration.jei;

import com.mojang.blaze3d.platform.Lighting;
import dev.dubhe.anvilcraft.client.support.RenderSupport;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/** 保留预览的成分交互，由配方分类统一控制模型的绘制顺序。 */
record CatalysisBlockRenderer<T>(Function<T, List<Component>> tooltip) implements IIngredientRenderer<T> {
    private static final int SCALE = 18;
    // 与 RenderSupport 的 30 度俯视投影一致，让上下方块的接触面贴合。
    static final float STACK_Y_OFFSET = SCALE * (float) Math.cos(Math.PI / 6);

    @Override
    public void render(GuiGraphics graphics, T ingredient) {
        // JEI 会按成分类型批量绘制，模型留给分类先画环境、再画流体，避免遮挡顺序被打乱。
    }

    static void drawBlock(GuiGraphics graphics, BlockState state, float x, float y, float depth) {
        graphics.flush();
        RenderSupport.renderBlock(graphics, state, x + 16, y + 10, depth, SCALE, RenderSupport.SINGLE_BLOCK);
        graphics.flush();
        Lighting.setupFor3DItems();
    }

    @Override
    @SuppressWarnings("removal")
    public List<Component> getTooltip(T ingredient, TooltipFlag tooltipFlag) {
        return this.tooltip.apply(ingredient);
    }

    @Override
    public List<Component> getTooltip(
        T ingredient,
        Item.TooltipContext context,
        @Nullable Player player,
        TooltipFlag tooltipFlag
    ) {
        return this.tooltip.apply(ingredient);
    }

    @Override
    public int getWidth() {
        return 32;
    }

    @Override
    public int getHeight() {
        return 32;
    }
}
