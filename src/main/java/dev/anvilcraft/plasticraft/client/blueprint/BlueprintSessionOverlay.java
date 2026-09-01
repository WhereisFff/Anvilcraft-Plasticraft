package dev.anvilcraft.plasticraft.client.blueprint;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * 部署会话的屏幕工具条:七个蓝图按钮图标横排在快捷栏上方,
 * Ctrl+滚轮切换选中,Alt+滚轮调整,右击执行;上方文字显示蓝图名与当前状态。
 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class BlueprintSessionOverlay {
    private static final int ICON_SIZE = 16;
    private static final int ICON_SPACING = 20;
    private static final int ATLAS_HEIGHT = 64;
    /** 四帧竖排按钮图集里的"按下(选中)"帧起始 V。 */
    private static final int SELECTED_FRAME_V = 32;

    private static final Map<BlueprintDeploySession.Tool, ResourceLocation> ICONS =
        new EnumMap<>(BlueprintDeploySession.Tool.class);

    static {
        for (BlueprintDeploySession.Tool tool : BlueprintDeploySession.Tool.values()) {
            ICONS.put(tool, AnvilcraftPlasticraft.of("textures/gui/button/blueprint/" + tool.id() + ".png"));
        }
    }

    private BlueprintSessionOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!BlueprintDeploySession.isActive()) return;
        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        BlueprintDeploySession.Tool selected = BlueprintDeploySession.selectedTool();

        BlueprintDeploySession.Tool[] tools = BlueprintDeploySession.Tool.values();
        int totalWidth = tools.length * ICON_SPACING - (ICON_SPACING - ICON_SIZE);
        int left = (graphics.guiWidth() - totalWidth) / 2;
        int top = graphics.guiHeight() - 64;
        for (int index = 0; index < tools.length; index++) {
            BlueprintDeploySession.Tool tool = tools[index];
            int frameV = tool == selected ? SELECTED_FRAME_V : 0;
            graphics.blit(
                ICONS.get(tool),
                left + index * ICON_SPACING,
                top,
                0,
                frameV,
                ICON_SIZE,
                ICON_SIZE,
                ICON_SIZE,
                ATLAS_HEIGHT
            );
        }

        graphics.drawCenteredString(
            minecraft.font,
            statusLine(selected),
            graphics.guiWidth() / 2,
            top - 12,
            0xFFFFFF
        );
        graphics.drawCenteredString(
            minecraft.font,
            Component.translatable("screen.anvilcraftplasticraft.blueprint_session.hint"),
            graphics.guiWidth() / 2,
            top - 24,
            0xA0FFFFFF
        );
        if (selected == BlueprintDeploySession.Tool.CANCEL) {
            String cancelName = BlueprintDeploySession.pendingCancelName();
            if (cancelName != null) {
                graphics.drawCenteredString(
                    minecraft.font,
                    Component.translatable("screen.anvilcraftplasticraft.blueprint_session.cancel_target", cancelName),
                    graphics.guiWidth() / 2,
                    top - 36,
                    0xFFFFC14D
                );
            }
        }
    }

    private static Component statusLine(BlueprintDeploySession.Tool selected) {
        String name = BlueprintDeploySession.activeName();
        Rotation rotation = BlueprintDeploySession.activeRotation();
        Mirror mirror = BlueprintDeploySession.activeMirror();
        int layer = BlueprintDeploySession.layerView();
        Component toolName = Component.translatable(
            "screen.anvilcraftplasticraft.blueprint_session.tool."
                + (selected == null ? BlueprintDeploySession.Tool.MOVE.id() : selected.id())
        );
        Component layerText = layer == BlueprintDeploySession.LAYERS_ALL
            ? Component.translatable("screen.anvilcraftplasticraft.blueprint_session.layer_all")
            : Component.literal(String.valueOf(layer + 1));
        Component lockText = Component.translatable(
            BlueprintDeploySession.isAnchorLocked()
                ? "screen.anvilcraftplasticraft.blueprint_session.anchor_locked"
                : "screen.anvilcraftplasticraft.blueprint_session.anchor_following"
        );
        return Component.translatable(
            "screen.anvilcraftplasticraft.blueprint_session.status",
            name == null ? "" : name,
            toolName,
            rotation.name().toLowerCase(Locale.ROOT),
            mirror.name().toLowerCase(Locale.ROOT),
            layerText,
            lockText
        );
    }
}
