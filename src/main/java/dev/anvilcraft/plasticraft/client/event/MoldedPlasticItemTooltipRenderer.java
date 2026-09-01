package dev.anvilcraft.plasticraft.client.event;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.gui.tooltip.MoldedPlasticContentTooltip;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContentSummary;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

import java.util.List;

@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class MoldedPlasticItemTooltipRenderer {
    private static final int TOOLTIP_Z = 400;
    private static final int TOOLTIP_GAP = 5;
    private static final int MAIN_TOOLTIP_OFFSET = 13;

    private MoldedPlasticItemTooltipRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderTooltipEvent.Pre event) {
        MoldedPlasticData data = MoldedPlasticData.get(event.getItemStack()).orElse(null);
        if (data == null) return;
        MoldedPlasticContentSummary summary = data.summary().isVacant()
            ? MoldedPlasticContentSummary.create(data.finalType(), data.capacity(), List.of(), List.of())
            : data.summary();
        List<Component> lines = MoldedPlasticContentTooltip.create(summary);
        if (lines.isEmpty()) return;

        int mouseX = event.getX();
        int mouseY = event.getY();
        event.setY(mouseY + MAIN_TOOLTIP_OFFSET);
        renderAbovePointer(event.getGraphics(), lines, mouseX, mouseY);
    }

    private static void renderAbovePointer(
        GuiGraphics graphics,
        List<Component> lines,
        int mouseX,
        int mouseY
    ) {
        Font font = Minecraft.getInstance().font;
        List<ClientTooltipComponent> components = lines.stream()
            .map(Component::getVisualOrderText)
            .map(ClientTooltipComponent::create)
            .toList();
        int width = components.stream().mapToInt(component -> component.getWidth(font)).max().orElse(0);
        int height = components.stream().mapToInt(ClientTooltipComponent::getHeight).sum();
        int textX = Math.clamp(
            mouseX - width / 2,
            4,
            Math.max(4, graphics.guiWidth() - width - 4)
        );
        int textY = Math.max(4, mouseY - height - TOOLTIP_GAP - TooltipRenderUtil.PADDING_BOTTOM);

        graphics.pose().pushPose();
        graphics.drawManaged(() -> TooltipRenderUtil.renderTooltipBackground(
            graphics,
            textX,
            textY,
            width,
            height,
            TOOLTIP_Z
        ));
        graphics.pose().translate(0.0F, 0.0F, TOOLTIP_Z);
        int lineY = textY;
        for (ClientTooltipComponent component : components) {
            component.renderText(font, textX, lineY, graphics.pose().last().pose(), graphics.bufferSource());
            lineY += component.getHeight();
        }
        lineY = textY;
        for (ClientTooltipComponent component : components) {
            component.renderImage(font, textX, lineY, graphics);
            lineY += component.getHeight();
        }
        graphics.pose().popPose();
    }
}
