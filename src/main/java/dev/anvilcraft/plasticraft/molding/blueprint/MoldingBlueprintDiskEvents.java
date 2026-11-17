package dev.anvilcraft.plasticraft.molding.blueprint;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID)
public final class MoldingBlueprintDiskEvents {
    private MoldingBlueprintDiskEvents() {
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getEntity().isShiftKeyDown() || !MoldingBlueprintDisk.hasBlueprintData(event.getItemStack())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player && MoldingBlueprintDisk.clear(event.getItemStack())) {
            player.sendOverlayMessage(Component.translatable("message.anvilcraft.disk.data_cleared"));
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }
}
