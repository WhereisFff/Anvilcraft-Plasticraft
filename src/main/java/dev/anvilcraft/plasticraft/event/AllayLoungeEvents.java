package dev.anvilcraft.plasticraft.event;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.blueprint.ConstructionPermission;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** 休息室破坏前的所有者/团队复核。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID)
public final class AllayLoungeEvents {
    private AllayLoungeEvents() {
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
            || !(level.getBlockEntity(event.getPos()) instanceof AllayLoungeBlockEntity lounge)
            || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.getAbilities().mayBuild || !ConstructionPermission.canUseLounge(player, lounge)) {
            event.setCanceled(true);
        }
    }
}
