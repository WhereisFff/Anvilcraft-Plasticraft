package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltCauldronBlock;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.UniversalPlasticMeltBlockEntity;
import dev.anvilcraft.plasticraft.entity.CatalyticPressLidEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.dubhe.anvilcraft.api.fluid.network.FluidContainerLookup;
import dev.dubhe.anvilcraft.api.fluid.network.FluidNetworkScanner;
import dev.dubhe.anvilcraft.api.fluid.network.FluidPipeNetwork;
import dev.dubhe.anvilcraft.block.FishTankBlock;
import dev.dubhe.anvilcraft.entity.CauldronOutletEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/** 催化压盖与各种锅、输出口和管网之间的服务端加工桥。 */
public final class CatalyticPressProcess {
    private static final String PLASTIC_OIL_PROCESS = "plastic_oil_catalysis";

    private CatalyticPressProcess() {
    }

    public static void tick(CatalyticPressLidEntity lid) {
        if (!(lid.level() instanceof ServerLevel level) || lid.isReady()) return;
        SealedContainer container = findSealedContainer(lid);
        if (container == null) {
            if (lid.catalyticProgress() > 0) {
                PlasticOilCatalysisVisualSync.clear(level, occupiedPos(lid).below());
                lid.resetCatalysis();
            }
            return;
        }

        FluidStack stored = firstFluid(container.handler());
        if (!stored.is(PlasticraftFluids.PLASTIC_OIL.get())) {
            if (lid.catalyticProgress() > 0) {
                PlasticOilCatalysisVisualSync.clear(level, container.pos());
                lid.resetCatalysis();
            }
            return;
        }

        int progress = PLASTIC_OIL_PROCESS.equals(lid.activeRecipe()) ? lid.catalyticProgress() : 0;
        int heat = CatalyticPressHeat.power(level.getBlockState(container.pos().below()));
        lid.setCatalyticProgress(PLASTIC_OIL_PROCESS, progress, PlasticOilCatalysis.BASE_WORK);
        if (heat <= 0) {
            PlasticOilCatalysisVisualSync.update(level, container.pos(), progress, 0.0D);
            return;
        }

        progress = Math.min(PlasticOilCatalysis.BASE_WORK, progress + heat);
        lid.setCatalyticProgress(PLASTIC_OIL_PROCESS, progress, PlasticOilCatalysis.BASE_WORK);
        PlasticOilCatalysisVisualSync.update(level, container.pos(), progress, heat);
        if (progress < PlasticOilCatalysis.BASE_WORK) return;
        FluidTransformation fluidChange = planTransformation(container.handler(), stored);
        if (fluidChange == null) {
            PlasticOilCatalysisVisualSync.clear(level, container.pos());
            lid.resetCatalysis();
            return;
        }
        if (fluidChange.commit(container.handler())) {
            setContainerColor(level, container, DyeColor.WHITE);
            PlasticOilCatalysisVisualSync.complete(level, container.pos());
            lid.completeCatalysis();
        } else {
            fluidChange.rollback(container.handler());
            PlasticOilCatalysisVisualSync.clear(level, container.pos());
            lid.resetCatalysis();
        }
    }

    public static void press(CatalyticPressLidEntity lid) {
        if (!(lid.level() instanceof ServerLevel level) || !lid.isReady()) return;
        SealedContainer container = findSealedContainer(lid);
        if (container == null) {
            lid.resetCatalysis();
            return;
        }
        FluidStack melt = firstFluid(container.handler());
        if (!melt.is(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get())) {
            lid.resetCatalysis();
            return;
        }

        boolean output = false;
        Direction outlet = findOutletDirection(level, container);
        if (outlet != null) {
            output = outputThroughOutlet(level, container, outlet, melt.copy());
        } else {
            output = outputThroughPipe(level, container, melt);
        }

        if (output) {
            lid.resetCatalysis();
        } else {
            burstContainer(level, container, melt.copy());
            launchLid(level, lid);
        }
    }

    public static @Nullable SealedContainer findSealedContainer(CatalyticPressLidEntity lid) {
        if (!(lid.level() instanceof ServerLevel level)
            || lid.getOrientation().attachmentFace() != Direction.UP) {
            return null;
        }
        BlockPos lidPos = occupiedPos(lid);
        BlockPos containerPos = lidPos.below();
        FluidContainerLookup.Result result = FluidContainerLookup.find(level, containerPos, Direction.UP);
        if (result == null) return null;

        BlockState state = level.getBlockState(containerPos);
        HardenedResinCauldronEntity plasticCauldron = plasticCauldron(level, containerPos, result);
        boolean supportedBlock = state.is(BlockTags.CAULDRONS) || state.getBlock() instanceof FishTankBlock;
        if (!supportedBlock && plasticCauldron == null) return null;
        if (plasticCauldron != null && plasticCauldron.getOrientation().attachmentFace() != Direction.UP) return null;
        return new SealedContainer(containerPos, result.handler(), result, plasticCauldron);
    }

    public static BlockPos occupiedPos(Entity entity) {
        return BlockPos.containing(entity.getBoundingBox().getCenter());
    }

    private static @Nullable FluidTransformation planTransformation(
        IFluidHandler handler,
        FluidStack stored
    ) {
        if (!stored.is(PlasticraftFluids.PLASTIC_OIL.get()) || stored.getAmount() <= 0) return null;
        FluidStack simulated = handler.drain(stored, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.getAmount() != stored.getAmount()
            || !FluidStack.isSameFluidSameComponents(simulated, stored)) return null;
        return new FluidTransformation(
            simulated.copy(),
            new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), simulated.getAmount())
        );
    }

    private static FluidStack firstFluid(IFluidHandler handler) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stack = handler.getFluidInTank(tank);
            if (!stack.isEmpty()) return stack.copy();
        }
        return FluidStack.EMPTY;
    }

    private static void setContainerColor(ServerLevel level, SealedContainer container, DyeColor color) {
        FluidStack stored = firstFluid(container.handler());
        if (!stored.isEmpty()) {
            PlasticMeltColor.set(stored, color);
            replaceFluid(container.handler(), stored);
        }
        BlockState state = level.getBlockState(container.pos());
        if (state.is(PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT_CAULDRON.get())) {
            level.setBlock(container.pos(), state.setValue(UniversalPlasticMeltCauldronBlock.COLOR, color), Block.UPDATE_CLIENTS);
        }
    }

    private static void replaceFluid(IFluidHandler handler, FluidStack replacement) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stored = handler.getFluidInTank(tank);
            if (stored.isEmpty()) continue;
            handler.drain(stored.getAmount(), IFluidHandler.FluidAction.EXECUTE);
            handler.fill(replacement, IFluidHandler.FluidAction.EXECUTE);
            return;
        }
    }

    private static @Nullable Direction findOutletDirection(ServerLevel level, SealedContainer container) {
        if (container.plasticCauldron() != null) return container.plasticCauldron().getOutletDirection();
        BlockState state = level.getBlockState(container.pos());
        if (state.getBlock() instanceof FishTankBlock && state.getValue(FishTankBlock.OUTLET)) {
            return state.getValue(FishTankBlock.FACING);
        }
        for (CauldronOutletEntity outlet : level.getEntitiesOfClass(
            CauldronOutletEntity.class,
            new AABB(container.pos()).inflate(1.5D),
            candidate -> candidate.getCauldronPos().equals(container.pos())
        )) {
            return outlet.getAttachedDirection();
        }
        return null;
    }

    private static boolean outputThroughOutlet(
        ServerLevel level,
        SealedContainer source,
        Direction direction,
        FluidStack melt
    ) {
        BlockPos outputPos = source.pos().relative(direction);
        if (PlasticGranuleCauldronOutput.tryProcess(level, outputPos, source.handler(), melt)) {
            return true;
        }
        FluidContainerLookup.Result direct = FluidContainerLookup.find(level, outputPos, direction.getOpposite());
        if (direct != null) {
            return transferAll(level, outputPos, source.handler(), direct.handler(), melt);
        }
        if (!level.getBlockState(outputPos).isAir() || !level.getFluidState(outputPos).isEmpty()) return false;

        BlockPos lowerPos = outputPos.below();
        FluidContainerLookup.Result lower = FluidContainerLookup.find(level, lowerPos, Direction.UP);
        if (lower != null && transferAll(level, lowerPos, source.handler(), lower.handler(), melt)) return true;
        if (melt.getAmount() < 1000) return false;

        source.handler().drain(melt.getAmount(), IFluidHandler.FluidAction.EXECUTE);
        placeMelt(level, outputPos, melt);
        return true;
    }

    private static boolean transferAll(
        ServerLevel level,
        BlockPos targetPos,
        IFluidHandler source,
        IFluidHandler target,
        FluidStack melt
    ) {
        if (target.fill(melt, IFluidHandler.FluidAction.SIMULATE) != melt.getAmount()) return false;
        FluidStack drained = source.drain(melt.getAmount(), IFluidHandler.FluidAction.SIMULATE);
        if (!FluidStack.isSameFluidSameComponents(drained, melt) || drained.getAmount() != melt.getAmount()) return false;
        source.drain(melt.getAmount(), IFluidHandler.FluidAction.EXECUTE);
        target.fill(melt, IFluidHandler.FluidAction.EXECUTE);
        syncMeltContainerColor(level, targetPos, melt);
        return true;
    }

    /** 将带颜色的熔体写入方块化炼药锅和单格熔体方块的可见状态。 */
    public static void syncMeltContainerColor(ServerLevel level, BlockPos pos, FluidStack melt) {
        DyeColor color = PlasticMeltColor.get(melt);
        BlockState state = level.getBlockState(pos);
        if (state.is(PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT_CAULDRON.get())
            && state.getValue(UniversalPlasticMeltCauldronBlock.COLOR) != color) {
            level.setBlock(
                pos,
                state.setValue(UniversalPlasticMeltCauldronBlock.COLOR, color),
                Block.UPDATE_CLIENTS
            );
        }
        if (level.getBlockEntity(pos) instanceof UniversalPlasticMeltBlockEntity blockEntity) {
            blockEntity.setColor(color);
        }
    }

    private static boolean outputThroughPipe(ServerLevel level, SealedContainer source, FluidStack melt) {
        for (Direction direction : Direction.values()) {
            BlockPos pipePos = source.pos().relative(direction);
            if (!FluidNetworkScanner.isPipePart(level.getBlockState(pipePos))) continue;
            FluidPipeNetwork network = FluidNetworkScanner.scan(level, pipePos);
            if (network == null) continue;
            BlockPos target = network.pushExact(
                source.handler(),
                source.pos(),
                pipePos,
                source.pos().getY() + 10,
                melt
            );
            if (target != null) {
                CatalyticPressProcess.syncMeltContainerColor(level, target, melt);
                return true;
            }
        }
        return false;
    }

    private static void burstContainer(ServerLevel level, SealedContainer container, FluidStack melt) {
        DyeColor color = PlasticMeltColor.get(melt);
        // 方块化容器的胶接关系保存在方块附加数据中，替换锅之前必须同步清掉双方的胶面。
        BondedFallingBlocks.removeAll(level, container.pos());
        if (container.plasticCauldron() != null && !container.plasticCauldron().isRemoved()) {
            EntityBondManager.disconnectEntity(level, container.plasticCauldron());
            container.plasticCauldron().discard();
        }
        level.setBlock(container.pos(), PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(container.pos()) instanceof UniversalPlasticMeltBlockEntity blockEntity) {
            blockEntity.setColor(color);
        }
        level.levelEvent(2001, container.pos(), Block.getId(PlasticraftBlocks.CATALYTIC_PRESS_LID.get().defaultBlockState()));
    }

    private static void launchLid(ServerLevel level, CatalyticPressLidEntity lid) {
        BlockPos lidPos = occupiedPos(lid);
        if (level.getBlockEntity(lidPos) instanceof BondedEntityBlockEntity bonded
            && bonded.getOrCreateRenderEntity() instanceof CatalyticPressLidEntity) {
            Entity released = bonded.releaseEntity(null);
            if (released instanceof CatalyticPressLidEntity releasedLid) {
                releasedLid.resetCatalysis();
                releasedLid.setDeltaMovement(0.0D, 0.75D, 0.0D);
                releasedLid.hasImpulse = true;
            }
            return;
        }
        EntityBondManager.disconnectEntity(level, lid);
        lid.resetCatalysis();
        lid.setDeltaMovement(new Vec3(0.0D, 0.75D, 0.0D));
        lid.hasImpulse = true;
    }

    public static void placeMelt(ServerLevel level, BlockPos pos, FluidStack melt) {
        level.setBlock(pos, PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof UniversalPlasticMeltBlockEntity blockEntity) {
            blockEntity.setColor(PlasticMeltColor.get(melt));
        }
    }

    private static @Nullable HardenedResinCauldronEntity plasticCauldron(
        ServerLevel level,
        BlockPos pos,
        FluidContainerLookup.Result result
    ) {
        if (result.entity() instanceof HardenedResinCauldronEntity cauldron) return cauldron;
        if (level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
            && bonded.getOrCreateRenderEntity() instanceof HardenedResinCauldronEntity cauldron) {
            return cauldron;
        }
        return null;
    }

    private static final class FluidTransformation {
        private final FluidStack input;
        private final FluidStack output;
        private boolean committed;

        private FluidTransformation(FluidStack input, FluidStack output) {
            this.input = input;
            this.output = output;
        }

        private boolean commit(IFluidHandler handler) {
            FluidStack drained = handler.drain(this.input, IFluidHandler.FluidAction.EXECUTE);
            if (drained.getAmount() != this.input.getAmount()
                || !FluidStack.isSameFluidSameComponents(drained, this.input)) {
                if (!drained.isEmpty()) handler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                return false;
            }
            int filled = handler.fill(this.output, IFluidHandler.FluidAction.EXECUTE);
            if (filled == this.output.getAmount()) {
                this.committed = true;
                return true;
            }
            if (filled > 0) {
                handler.drain(this.output.copyWithAmount(filled), IFluidHandler.FluidAction.EXECUTE);
            }
            handler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            return false;
        }

        private void rollback(IFluidHandler handler) {
            if (!this.committed) return;
            FluidStack drained = handler.drain(this.output, IFluidHandler.FluidAction.EXECUTE);
            if (drained.getAmount() == this.output.getAmount()
                && FluidStack.isSameFluidSameComponents(drained, this.output)) {
                handler.fill(this.input, IFluidHandler.FluidAction.EXECUTE);
            } else if (!drained.isEmpty()) {
                handler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            }
            this.committed = false;
        }
    }

    public record SealedContainer(
        BlockPos pos,
        IFluidHandler handler,
        FluidContainerLookup.Result lookup,
        @Nullable HardenedResinCauldronEntity plasticCauldron
    ) {
    }
}
