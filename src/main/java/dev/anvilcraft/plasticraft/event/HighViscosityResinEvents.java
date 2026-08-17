package dev.anvilcraft.plasticraft.event;

import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
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
        if (!stack.is(PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())) return;
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
            PlasticraftBlocks.LIQUID_HIGH_VISCOSITY_RESIN_CAULDRON.get(),
            PlasticraftFluids.LIQUID_HIGH_VISCOSITY_RESIN.get(),
            1000,
            Layered4LevelCauldronBlock.LEVEL
        );
        event.register(
            PlasticraftBlocks.HIGH_HEAT_FUEL_CAULDRON.get(),
            PlasticraftFluids.HIGH_HEAT_FUEL.get(),
            1000,
            Layered4LevelCauldronBlock.LEVEL
        );
        event.register(
            PlasticraftBlocks.PLASTIC_OIL_CAULDRON.get(),
            PlasticraftFluids.PLASTIC_OIL.get(),
            1000,
            Layered4LevelCauldronBlock.LEVEL
        );
        event.register(
            PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT_CAULDRON.get(),
            PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(),
            1000,
            Layered4LevelCauldronBlock.LEVEL
        );
        event.register(
            PlasticraftBlocks.ENGINEERING_PLASTIC_MELT_CAULDRON.get(),
            PlasticraftFluids.ENGINEERING_PLASTIC_MELT.get(),
            1000,
            Layered4LevelCauldronBlock.LEVEL
        );
        event.register(
            PlasticraftBlocks.HEAT_RESISTANT_PLASTIC_MELT_CAULDRON.get(),
            PlasticraftFluids.HEAT_RESISTANT_PLASTIC_MELT.get(),
            1000,
            Layered4LevelCauldronBlock.LEVEL
        );
        event.register(
            PlasticraftBlocks.CLEAR_PLASTIC_MELT_CAULDRON.get(),
            PlasticraftFluids.CLEAR_PLASTIC_MELT.get(),
            1000,
            Layered4LevelCauldronBlock.LEVEL
        );
    }
}
