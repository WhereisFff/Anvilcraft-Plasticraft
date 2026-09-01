package dev.anvilcraft.plasticraft.event;

import dev.anvilcraft.plasticraft.allay.AllayHardHats;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** 原版悦灵右击戴上安全帽后转换成施工悦灵。 */
public final class AllayHardHatEvents {
    private AllayHardHatEvents() {
    }

    public static void entityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Allay allay) || event.getTarget() instanceof WorkingAllayEntity) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!AllayHardHats.isHardHat(stack)) return;
        Player player = event.getEntity();
        if (!(allay.level() instanceof ServerLevel)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        ItemStack hat = stack.copyWithCount(1);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        WorkingAllayEntity.convertFrom(allay, hat, player.getUUID());
        allay.level().playSound(null, allay.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 0.8F, 1.2F);
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
    }
}
