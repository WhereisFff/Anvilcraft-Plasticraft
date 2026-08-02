package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** 催化压盖输出口把一桶熔体投入满水炼药锅时使用的原子结算。 */
public final class PlasticGranuleCauldronOutput {
    public static final int MELT_AMOUNT = 1_000;
    public static final int GRANULE_COUNT = 16;

    private static final int FULL_WATER_LEVEL = 3;

    private PlasticGranuleCauldronOutput() {
    }

    /**
     * 尝试消耗一满锅水和恰好一桶同组件熔体，并在锅的位置生成同色塑料粒。
     *
     * <p>物品实体和水锅先提交但都可直接撤销，真正的源流体扣除放在最后；这样预检失败、
     * 实体生成失败或方块替换失败都不会接触源熔体。若异常流体处理器在模拟后拒绝实际排出，
     * 已产生的物品、水锅和处理器返回的流体也会在同一调用内回滚。</p>
     */
    public static boolean tryProcess(
        ServerLevel level,
        BlockPos targetPos,
        IFluidHandler source,
        FluidStack availableMelt
    ) {
        BlockState waterState = level.getBlockState(targetPos);
        if (!isFullWaterCauldron(waterState)
            || !availableMelt.is(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get())
            || availableMelt.getAmount() < MELT_AMOUNT) {
            return false;
        }

        FluidStack requested = availableMelt.copyWithAmount(MELT_AMOUNT);
        FluidStack simulated = source.drain(requested, IFluidHandler.FluidAction.SIMULATE);
        if (!isExactMelt(simulated, requested)) return false;

        ItemStack granules = new ItemStack(PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE.get(), GRANULE_COUNT);
        PlasticMeltColor.set(granules, PlasticMeltColor.get(requested));
        ItemEntity output = new ItemEntity(
            level,
            targetPos.getX() + 0.5D,
            targetPos.getY() + 0.7D,
            targetPos.getZ() + 0.5D,
            granules
        );
        output.setDefaultPickUpDelay();
        if (!level.addFreshEntity(output)) return false;

        if (!level.setBlock(targetPos, Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_ALL)) {
            output.discard();
            return false;
        }

        FluidStack drained = source.drain(requested, IFluidHandler.FluidAction.EXECUTE);
        if (!isExactMelt(drained, requested)) {
            rollback(level, targetPos, waterState, source, drained, output);
            return false;
        }

        level.playSound(
            null,
            targetPos,
            SoundEvents.FIRE_EXTINGUISH,
            SoundSource.BLOCKS,
            0.8F,
            1.1F
        );
        return true;
    }

    private static boolean isFullWaterCauldron(BlockState state) {
        return state.is(Blocks.WATER_CAULDRON)
            && state.getValue(LayeredCauldronBlock.LEVEL) == FULL_WATER_LEVEL;
    }

    private static boolean isExactMelt(FluidStack actual, FluidStack expected) {
        return actual.getAmount() == expected.getAmount()
            && FluidStack.isSameFluidSameComponents(actual, expected);
    }

    private static void rollback(
        ServerLevel level,
        BlockPos targetPos,
        BlockState waterState,
        IFluidHandler source,
        FluidStack drained,
        ItemEntity output
    ) {
        output.discard();
        boolean waterRestored = level.setBlock(targetPos, waterState, Block.UPDATE_ALL);
        int fluidRestored = drained.isEmpty()
            ? 0
            : source.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        if (!waterRestored || fluidRestored != drained.getAmount()) {
            // 非守约能力无法做到真正事务化；记录完整上下文，避免静默吞资源。
            AnvilcraftPlasticraft.LOGGER.error(
                "Unable to roll back plastic-granule cauldron output at {}: waterRestored={}, fluid={}/{} mB",
                targetPos,
                waterRestored,
                fluidRestored,
                drained.getAmount()
            );
        }
    }
}
