package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.lib.v2.recipe.cache.BlockCache;
import dev.anvilcraft.plasticraft.recipe.CondenserTowerProcess;
import dev.anvilcraft.plasticraft.recipe.EnhancedPlasmaJetFuel;
import dev.anvilcraft.plasticraft.recipe.HardenedResinCauldronSupport;
import dev.dubhe.anvilcraft.api.block.IIgnitableCauldron;
import dev.dubhe.anvilcraft.block.HeaterBlock;
import dev.dubhe.anvilcraft.block.PlasmaJetsBlock;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让喷流穿过大型炼药锅和冷凝塔的占位空间，并在锅上方停止抬升。 */
@Mixin(PlasmaJetsBlock.class)
abstract class PlasmaJetsBlockMixin {
    @Inject(method = "trySpawn", at = @At("HEAD"), cancellable = true)
    private static void plasticraft$spawnThroughLargeContainers(
        BlockPos pos,
        Level level,
        CallbackInfoReturnable<Boolean> cir
    ) {
        Integer entityAmount = HardenedResinCauldronSupport.fluidAmount(level, pos.below());
        if (entityAmount != null && entityAmount < 250) {
            cir.setReturnValue(false);
            return;
        }

        boolean hasPassThrough = false;
        for (int i = 0; i < 8; i++) {
            if (CondenserTowerProcess.isPlasmaPassThrough(level.getBlockState(pos.above(i)))) {
                hasPassThrough = true;
                break;
            }
        }
        if (!hasPassThrough) return;

        BlockState heater = level.getBlockState(pos.below().below());
        if (!PlasmaJetsBlock.isIgnitedOilCauldron(level, pos.below())
            || !heater.is(ModBlocks.HEATER)
            || heater.getValue(HeaterBlock.OVERLOAD)) {
            cir.setReturnValue(false);
            return;
        }
        BlockPos cauldronPos = pos.below();
        int fluidAmount = entityAmount == null ? blockFluidAmount(level, cauldronPos) : entityAmount;
        if (fluidAmount < 250) {
            cir.setReturnValue(false);
            return;
        }
        for (int i = 0; i < 8; i++) {
            BlockState above = level.getBlockState(pos.above(i));
            if (!above.isAir() && (i == 0 || !CondenserTowerProcess.isPlasmaPassThrough(above))) {
                cir.setReturnValue(false);
                return;
            }
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            if (!level.getBlockState(side).isFaceSturdy(level, side, direction.getOpposite())) {
                cir.setReturnValue(false);
                return;
            }
        }
        level.setBlock(pos, ModBlocks.PLASMA_JETS.getDefaultState(), 3);
        cir.setReturnValue(true);
    }

    private static int blockFluidAmount(Level level, BlockPos pos) {
        BlockCache cache = new BlockCache(level);
        if (cache.getBlockState(pos).getBlock() instanceof IIgnitableCauldron cauldron) {
            return cauldron.getFluidAmount(cache, pos);
        }
        return 0;
    }

    @Inject(method = "isIgnitedOilCauldron", at = @At("HEAD"), cancellable = true)
    private static void plasticraft$entityOilBase(
        Level level,
        BlockPos pos,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (HardenedResinCauldronSupport.isIgnitedOil(level, pos)) {
            cir.setReturnValue(true);
        } else if (EnhancedPlasmaJetFuel.isIgnitedHighHeatFuel(level, pos)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isValidBaseCauldron", at = @At("HEAD"), cancellable = true)
    private static void plasticraft$entityValidBase(
        Level level,
        BlockPos pos,
        CallbackInfoReturnable<Boolean> cir
    ) {
        Boolean valid = HardenedResinCauldronSupport.validBase(level, pos);
        if (valid != null) {
            cir.setReturnValue(valid);
        } else if (EnhancedPlasmaJetFuel.isValidBase(level, pos)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "tryConsumeOnce", at = @At("HEAD"), cancellable = true)
    private static void plasticraft$consumeEntityOil(
        Level level,
        BlockPos pos,
        CallbackInfoReturnable<Boolean> cir
    ) {
        Boolean consumed = HardenedResinCauldronSupport.consumeOnce(level, pos);
        if (consumed != null) cir.setReturnValue(consumed);
    }

    @Inject(method = "usesContinuousFuel", at = @At("HEAD"), cancellable = true)
    private static void plasticraft$useEntityContinuousFuel(
        Level level,
        BlockPos pos,
        CallbackInfoReturnable<Boolean> cir
    ) {
        Boolean continuous = HardenedResinCauldronSupport.usesContinuousFuel(level, pos);
        if (continuous != null) cir.setReturnValue(continuous);
    }

    @Inject(method = "tryConsumeContinuousFuel", at = @At("HEAD"), cancellable = true)
    private static void plasticraft$consumeEntityContinuousFuel(
        Level level,
        BlockPos pos,
        int amount,
        CallbackInfoReturnable<Boolean> cir
    ) {
        Boolean consumed = HardenedResinCauldronSupport.consumeContinuousFuel(level, pos, amount);
        if (consumed != null) cir.setReturnValue(consumed);
    }
}
