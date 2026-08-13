package dev.anvilcraft.plasticraft.client.blueprint;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintData;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.client.renderer.blueprint.BlueprintProjectionRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** 部署会话的客户端输入与生命周期:Ctrl+滚轮切换工具、Alt+滚轮调整参数、断线清理缓存。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class BlueprintClientEvents {
    private BlueprintClientEvents() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ConstructionBlueprintData data = ConstructionBlueprintData.get(event.getItemStack()).orElse(null);
        if (data == null) return;
        ConstructionJob job = data.jobId().map(ClientBlueprintJobCache::job).orElse(null);
        String stateKey = job == null
            ? "tooltip.anvilcraftplasticraft.blueprint.state_imported"
            : job.isActive()
                ? "tooltip.anvilcraftplasticraft.blueprint.state_active"
                : "tooltip.anvilcraftplasticraft.blueprint.state_placed";
        event.getToolTip().add(Component.translatable(stateKey).withStyle(ChatFormatting.GRAY));
        if (!Screen.hasShiftDown()) return;
        event.getToolTip().add(Component
            .translatable("tooltip.anvilcraftplasticraft.blueprint.source." + data.source().getSerializedName())
            .withStyle(ChatFormatting.DARK_GRAY));
        event.getToolTip().add(Component
            .translatable("tooltip.anvilcraftplasticraft.blueprint.controls")
            .withStyle(ChatFormatting.DARK_GRAY));
        if (data.hasBlockEntities()) {
            event.getToolTip().add(Component
                .translatable("tooltip.anvilcraftplasticraft.blueprint.has_block_entities")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (data.hasEntities()) {
            event.getToolTip().add(Component
                .translatable("tooltip.anvilcraftplasticraft.blueprint.has_entities")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        BlueprintDeploySession.clientTick(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!BlueprintDeploySession.isActive()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) return;
        boolean ctrl = Screen.hasControlDown();
        boolean alt = Screen.hasAltDown();
        if (!ctrl && !alt) return;
        int delta = event.getScrollDeltaY() > 0 ? 1 : event.getScrollDeltaY() < 0 ? -1 : 0;
        if (delta == 0) return;
        if (ctrl) {
            BlueprintDeploySession.cycleTool(-delta);
        } else {
            BlueprintDeploySession.adjustSelectedTool(delta);
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BlueprintDeploySession.exit();
        ClientBlueprintJobCache.clear();
        ClientBlueprintSnapshotCache.clear();
        BlueprintProjectionRenderer.clearCache();
    }
}
