package dev.anvilcraft.plasticraft.event;

import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.item.HighViscosityResinBlockItem;
import dev.dubhe.anvilcraft.block.Layered4LevelCauldronBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.RegisterCauldronFluidContentEvent;

/** 高粘性树脂与玩家、发射器之外的全局交互入口。 */
public final class HighViscosityResinEvents {
    private HighViscosityResinEvents() {
    }

    public static void projectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof WitherSkull skull)
            || !(event.getRayTraceResult() instanceof BlockHitResult hit)) return;
        Level level = skull.level();
        BlockPos pos = hit.getBlockPos();
        if (!level.getBlockState(pos).is(PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.get())) return;
        // 必须在投射物命中逻辑前取消，否则凋零之首仍会爆炸并摧毁捕获产物。
        event.setCanceled(true);
        if (level.isClientSide()) return;
        ItemStack captured = HighViscosityResinBlockItem.captureSkull(
            skull, PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asStack()
        );
        if (captured.isEmpty()) return;
        level.removeBlock(pos, false);
        Block.popResource(level, pos, captured);
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
