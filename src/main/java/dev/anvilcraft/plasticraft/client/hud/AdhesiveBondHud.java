package dev.anvilcraft.plasticraft.client.hud;

import dev.anvilcraft.plasticraft.client.gui.tooltip.MoldedPlasticContentTooltip;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.init.PlasticraftAttachments;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContentSummary;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
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

import java.util.ArrayList;
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
        boolean bonded = entity.hasData(PlasticraftAttachments.ENTITY_ADHESION)
            || entity.hasData(PlasticraftAttachments.ENTITY_BONDS);
        MoldedPlasticContentSummary summary = entity instanceof UniversalPlasticEntity plastic
            ? plastic.getMoldedContentSummary()
            : MoldedPlasticContentSummary.EMPTY;
        boolean functional = MoldingProductTypes.isChest(summary.type())
            || MoldingProductTypes.isTank(summary.type())
            || MoldingProductTypes.isCauldron(summary.type());
        if (!bonded && !functional) {
            return;
        }

        ItemStack icon = entity instanceof FallingBlockEntity fallingBlock
            ? fallingBlock.getBlockState().getBlock().asItem().getDefaultInstance()
            : entity.getPickResult();
        List<Component> lines = new ArrayList<>();
        if (bonded) lines.add(Component.translatable("tooltip.anvilcraftplasticraft.bonded"));
        lines.addAll(MoldedPlasticContentTooltip.create(summary));
        TooltipRenderHelper.renderTooltipWithItemIcon(
            graphics,
            minecraft.font,
            icon == null ? ItemStack.EMPTY : icon,
            lines,
            graphics.guiWidth() / 2 + 10,
            graphics.guiHeight() / 2 + 10,
            BACKGROUND_COLOR,
            BORDER_COLOR_TOP,
            BORDER_COLOR_BOTTOM
        );
    }
}
