package dev.anvilcraft.plasticraft.client.hud;

import dev.anvilcraft.plasticraft.init.PlasticraftAttachments;
import dev.dubhe.anvilcraft.api.tooltip.TooltipRenderHelper;
import dev.dubhe.anvilcraft.item.AnvilHammerItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

import java.util.List;

/** 手持铁砧锤指向普通粘附实体时，使用铁砧工艺原生样式显示状态浮窗。 */
public final class AdhesiveBondHud {
    private static final int BACKGROUND_COLOR = 0xCC100010;
    private static final int BORDER_COLOR_TOP = 0x505000FF;
    private static final int BORDER_COLOR_BOTTOM = 0x5028007F;

    private AdhesiveBondHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker ignored) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null || minecraft.options.hideGui) return;
        if (!AnvilHammerItem.shouldRenderEffect(player)) return;
        if (!(minecraft.hitResult instanceof EntityHitResult hit)) return;

        Entity entity = hit.getEntity();
        if (!entity.hasData(PlasticraftAttachments.ENTITY_ADHESION)
            && !entity.hasData(PlasticraftAttachments.ENTITY_BONDS)) {
            return;
        }

        ItemStack icon = entity instanceof FallingBlockEntity fallingBlock
            ? fallingBlock.getBlockState().getBlock().asItem().getDefaultInstance()
            : entity.getPickResult();
        TooltipRenderHelper.renderTooltipWithItemIcon(
            graphics,
            minecraft.font,
            icon == null ? ItemStack.EMPTY : icon,
            List.of(Component.translatable("tooltip.anvilcraftplasticraft.bonded")),
            graphics.guiWidth() / 2 + 10,
            graphics.guiHeight() / 2 + 10,
            BACKGROUND_COLOR,
            BORDER_COLOR_TOP,
            BORDER_COLOR_BOTTOM
        );
    }
}
