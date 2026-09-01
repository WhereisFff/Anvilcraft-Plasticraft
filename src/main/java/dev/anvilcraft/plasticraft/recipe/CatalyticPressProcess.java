package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltCauldronBlock;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.UniversalPlasticMeltBlockEntity;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.CatalyticPressLidEntity;
import dev.anvilcraft.plasticraft.entity.PlasticCauldron;
import dev.anvilcraft.plasticraft.entity.PlasticCauldrons;
import dev.anvilcraft.plasticraft.entity.PlasticCauldronWorkBlockFinder;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItemTags;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/** 催化压盖与各种锅、输出口和管网之间的服务端加工桥。 */
public final class CatalyticPressProcess {
    private static final String PLASTIC_OIL_PROCESS = "plastic_oil_catalysis";
    private static final String CLEAR_PLASTIC_PROCESS = "clear_plastic_catalysis";
    private static final String ENGINEERING_PLASTIC_PROCESS = "engineering_plastic_catalysis";
    private static final String HEAT_RESISTANT_PLASTIC_PROCESS = "heat_resistant_plastic_catalysis";

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
        WorkConditions work = workConditions(level, container);
        String process;
        double workPerTick;
        Fluid outputFluid;
        @Nullable DyeColor outputColor;
        if (stored.is(PlasticraftFluids.PLASTIC_OIL.get())) {
            double heat = work.heat();
            GlassCatalysts glass = glassCatalysts(level, container);
            if (glass.hasAny()) {
                process = CLEAR_PLASTIC_PROCESS;
                workPerTick = heat * PlasticOilCatalysis.catalystMultiplier(
                    glass.royalItems(),
                    glass.frostItems()
                );
                outputFluid = PlasticraftFluids.CLEAR_PLASTIC_MELT.get();
                outputColor = null;
            } else {
                process = PLASTIC_OIL_PROCESS;
                workPerTick = heat;
                outputFluid = PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get();
                outputColor = DyeColor.WHITE;
            }
        } else if (stored.is(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get())) {
            double heat = work.heat();
            int emberMetalItems = emberMetalItems(level, container);
            if (emberMetalItems > 0 && heat > 0.0D) {
                process = HEAT_RESISTANT_PLASTIC_PROCESS;
                workPerTick = heat * PlasticOilCatalysis.catalystMultiplier(emberMetalItems);
                outputFluid = PlasticraftFluids.HEAT_RESISTANT_PLASTIC_MELT.get();
                outputColor = PlasticMeltColor.get(stored);
            } else if (work.cold()) {
                process = ENGINEERING_PLASTIC_PROCESS;
                workPerTick = PlasticOilCatalysis.ENGINEERING_REACTION_POWER;
                outputFluid = PlasticraftFluids.ENGINEERING_PLASTIC_MELT.get();
                outputColor = PlasticMeltColor.get(stored);
            } else {
                if (lid.catalyticProgress() > 0) {
                    PlasticOilCatalysisVisualSync.clear(level, container.pos());
                    lid.resetCatalysis();
                }
                return;
            }
        } else {
            if (lid.catalyticProgress() > 0) {
                PlasticOilCatalysisVisualSync.clear(level, container.pos());
                lid.resetCatalysis();
            }
            return;
        }

        int progress = process.equals(lid.activeRecipe()) ? lid.catalyticProgress() : 0;
        lid.setCatalyticProgress(process, progress, PlasticOilCatalysis.BASE_WORK);
        if (workPerTick <= 0.0D) {
            PlasticOilCatalysisVisualSync.update(level, container.pos(), progress, 0.0D);
            return;
        }

        progress = Math.min(PlasticOilCatalysis.BASE_WORK, progress + (int) Math.ceil(workPerTick));
        lid.setCatalyticProgress(process, progress, PlasticOilCatalysis.BASE_WORK);
        PlasticOilCatalysisVisualSync.update(level, container.pos(), progress, workPerTick);
        if (progress < PlasticOilCatalysis.BASE_WORK) return;
        FluidTransformation fluidChange = planTransformation(container.handler(), stored, outputFluid);
        if (fluidChange == null) {
            PlasticOilCatalysisVisualSync.clear(level, container.pos());
            lid.resetCatalysis();
            return;
        }
        if (fluidChange.commit(container.handler())) {
            if (outputColor != null) setContainerColor(level, container, outputColor);
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
        if (!PlasticMaterial.isMelt(melt)) {
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
        PlasticCauldron plasticCauldron = plasticCauldron(level, containerPos, result);
        boolean supportedBlock = state.is(BlockTags.CAULDRONS) || state.getBlock() instanceof FishTankBlock;
        if (!supportedBlock && plasticCauldron == null) return null;
        if (plasticCauldron != null && plasticCauldron.getOrientation().attachmentFace() != Direction.UP) return null;
        return new SealedContainer(containerPos, result.handler(), result, plasticCauldron);
    }

    public static BlockPos occupiedPos(Entity entity) {
        return BlockPos.containing(entity.getBoundingBox().getCenter());
    }

    private static WorkConditions workConditions(ServerLevel level, SealedContainer container) {
        PlasticCauldron cauldron = container.plasticCauldron();
        if (cauldron == null) return workConditions(level.getBlockState(container.pos().below()));
        @Nullable WorkConditions work = PlasticCauldronWorkBlockFinder.find(
            cauldron,
            CatalyticPressProcess::cauldronWorkConditions
        );
        return work == null ? WorkConditions.NONE : work;
    }

    private static WorkConditions workConditions(BlockState state) {
        return new WorkConditions(CatalyticPressHeat.power(state), PlasticCatalysisCold.isCold(state));
    }

    private static @Nullable WorkConditions cauldronWorkConditions(BlockState state) {
        WorkConditions work = workConditions(state);
        return work.isWorking() ? work : null;
    }

    private static @Nullable FluidTransformation planTransformation(
        IFluidHandler handler,
        FluidStack stored,
        Fluid outputFluid
    ) {
        if (stored.getAmount() <= 0) return null;
        FluidStack simulated = handler.drain(stored, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.getAmount() != stored.getAmount()
            || !FluidStack.isSameFluidSameComponents(simulated, stored)) return null;
        return new FluidTransformation(
            simulated.copy(),
            new FluidStack(outputFluid, simulated.getAmount())
        );
    }

    private static FluidStack firstFluid(IFluidHandler handler) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stack = handler.getFluidInTank(tank);
            if (!stack.isEmpty()) return stack.copy();
        }
        return FluidStack.EMPTY;
    }

    private static int emberMetalItems(ServerLevel level, SealedContainer container) {
        Set<Item> items = new HashSet<>();
        IItemHandler handler = itemHandler(level, container);
        if (handler != null) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.is(PlasticraftItemTags.EMBER_METAL_ITEMS)) items.add(stack.getItem());
            }
        }
        for (ItemEntity item : level.getEntitiesOfClass(
            ItemEntity.class,
            new AABB(container.pos()),
            candidate -> !candidate.isRemoved()
                && candidate.blockPosition().equals(container.pos())
                && candidate.getItem().is(PlasticraftItemTags.EMBER_METAL_ITEMS)
        )) {
            items.add(item.getItem().getItem());
        }
        return items.size();
    }

    private static GlassCatalysts glassCatalysts(ServerLevel level, SealedContainer container) {
        Set<Item> royal = new HashSet<>();
        Set<Item> frost = new HashSet<>();
        IItemHandler handler = itemHandler(level, container);
        if (handler != null) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                collectGlassCatalyst(handler.getStackInSlot(slot), royal, frost);
            }
        }
        for (ItemEntity item : level.getEntitiesOfClass(
            ItemEntity.class,
            new AABB(container.pos()),
            candidate -> !candidate.isRemoved()
                && candidate.blockPosition().equals(container.pos())
                && isGlassCatalyst(candidate.getItem())
        )) {
            collectGlassCatalyst(item.getItem(), royal, frost);
        }
        return new GlassCatalysts(royal.size(), frost.size());
    }

    private static void collectGlassCatalyst(ItemStack stack, Set<Item> royal, Set<Item> frost) {
        if (stack.is(PlasticraftItemTags.ROYAL_GLASS_ITEMS)) {
            royal.add(stack.getItem());
        } else if (stack.is(PlasticraftItemTags.FROST_GLASS_ITEMS)) {
            frost.add(stack.getItem());
        }
    }

    private static boolean isGlassCatalyst(ItemStack stack) {
        return stack.is(PlasticraftItemTags.ROYAL_GLASS_ITEMS)
            || stack.is(PlasticraftItemTags.FROST_GLASS_ITEMS);
    }

    private static @Nullable IItemHandler itemHandler(ServerLevel level, SealedContainer container) {
        if (container.plasticCauldron() != null) return container.plasticCauldron().getItemHandler();
        BlockState state = level.getBlockState(container.pos());
        BlockEntity blockEntity = level.getBlockEntity(container.pos());
        IItemHandler blockHandler = level.getCapability(
            Capabilities.ItemHandler.BLOCK,
            container.pos(),
            state,
            blockEntity,
            Direction.UP
        );
        if (blockHandler != null) return blockHandler;
        Entity entity = container.lookup().entity();
        return entity == null ? null : entity.getCapability(Capabilities.ItemHandler.ENTITY);
    }

    private static void setContainerColor(ServerLevel level, SealedContainer container, DyeColor color) {
        FluidStack stored = firstFluid(container.handler());
        if (!stored.isEmpty()) {
            PlasticMeltColor.set(stored, color);
            replaceFluid(container.handler(), stored);
        }
        BlockState state = level.getBlockState(container.pos());
        if (PlasticMaterial.fromMeltCauldron(state).isPresent()
            && state.hasProperty(UniversalPlasticMeltCauldronBlock.COLOR)) {
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
        PlasticMaterial material = PlasticMaterial.fromMelt(melt).orElse(null);
        if (material == null || !material.supportsDyeing()) return;
        DyeColor color = PlasticMeltColor.get(melt);
        BlockState state = level.getBlockState(pos);
        if (PlasticMaterial.fromMeltCauldron(state).isPresent()
            && state.hasProperty(UniversalPlasticMeltCauldronBlock.COLOR)
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
        // 方块化容器的胶接关系保存在方块附加数据中，替换锅之前必须同步清掉双方的胶面。
        BondedFallingBlocks.removeAll(level, container.pos());
        if (container.plasticCauldron() != null && !container.plasticCauldron().isRemoved()) {
            AbstractPlasticEntity cauldronEntity = container.plasticCauldron().plasticraft$cauldronEntity();
            EntityBondManager.disconnectEntity(level, cauldronEntity);
            cauldronEntity.discard();
        }
        placeMelt(level, container.pos(), melt);
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
        PlasticMaterial material = PlasticMaterial.fromMelt(melt).orElse(PlasticMaterial.UNIVERSAL);
        level.setBlock(pos, material.meltBlock().defaultBlockState(), Block.UPDATE_ALL);
        if (material.supportsDyeing()
            && level.getBlockEntity(pos) instanceof UniversalPlasticMeltBlockEntity blockEntity) {
            blockEntity.setColor(PlasticMeltColor.get(melt));
        }
    }

    private static @Nullable PlasticCauldron plasticCauldron(
        ServerLevel level,
        BlockPos pos,
        FluidContainerLookup.Result result
    ) {
        PlasticCauldron entityCauldron = PlasticCauldrons.of(result.entity());
        if (entityCauldron != null) return entityCauldron;
        if (level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
            && PlasticCauldrons.of(bonded.getOrCreateRenderEntity()) instanceof PlasticCauldron cauldron) {
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

    private record GlassCatalysts(int royalItems, int frostItems) {
        private boolean hasAny() {
            return this.royalItems > 0 || this.frostItems > 0;
        }
    }

    private record WorkConditions(double heat, boolean cold) {
        private static final WorkConditions NONE = new WorkConditions(0.0D, false);

        private boolean isWorking() {
            return this.heat > 0.0D || this.cold;
        }
    }

    public record SealedContainer(
        BlockPos pos,
        IFluidHandler handler,
        FluidContainerLookup.Result lookup,
        @Nullable PlasticCauldron plasticCauldron
    ) {
    }
}
