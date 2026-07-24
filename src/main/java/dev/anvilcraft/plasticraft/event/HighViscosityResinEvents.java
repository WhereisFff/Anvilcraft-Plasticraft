package dev.anvilcraft.plasticraft.event;

import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import dev.anvilcraft.plasticraft.item.HighViscosityResinBlockItem;
import dev.dubhe.anvilcraft.block.Layered4LevelCauldronBlock;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.RegisterCauldronFluidContentEvent;

/** 高粘性树脂与玩家、发射器之外的全局交互入口。 */
public final class HighViscosityResinEvents {
    private HighViscosityResinEvents() {
    }

    public static void useEntity(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())) return;
        InteractionResult result = HighViscosityResinBlockItem.useEntity(
            event.getEntity(),
            event.getTarget(),
            stack
        );
        if (result == InteractionResult.PASS) return;
        event.setCancellationResult(result);
        event.setCanceled(true);
    }

    public static void registerCauldronFluidContent(RegisterCauldronFluidContentEvent event) {
        event.register(
            ModBlocks.LIQUID_HIGH_VISCOSITY_RESIN_CAULDRON.get(),
            ModFluids.LIQUID_HIGH_VISCOSITY_RESIN.get(),
            1000,
            Layered4LevelCauldronBlock.LEVEL
        );
        event.register(
            ModBlocks.HIGH_HEAT_FUEL_CAULDRON.get(),
            ModFluids.HIGH_HEAT_FUEL.get(),
            1000,
            Layered4LevelCauldronBlock.LEVEL
        );
    }
}
