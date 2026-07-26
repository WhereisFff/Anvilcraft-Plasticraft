package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltCauldronBlock;
import dev.anvilcraft.plasticraft.api.FluidPipeNetworkExtension;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.UniversalPlasticMeltBlockEntity;
import dev.anvilcraft.plasticraft.entity.CatalyticPressLidEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.init.ModRecipeTypes;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.dubhe.anvilcraft.api.fluid.network.FluidContainerLookup;
import dev.dubhe.anvilcraft.api.fluid.network.FluidNetworkScanner;
import dev.dubhe.anvilcraft.api.fluid.network.FluidPipeNetwork;
import dev.dubhe.anvilcraft.block.FishTankBlock;
import dev.dubhe.anvilcraft.block.entity.FishTankBlockEntity;
import dev.dubhe.anvilcraft.entity.CauldronOutletEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 催化压盖与各种锅、输出口和管网之间的服务端加工桥。 */
public final class CatalyticPressProcess {
    private CatalyticPressProcess() {
    }

    public static void tick(CatalyticPressLidEntity lid) {
        if (!(lid.level() instanceof ServerLevel level) || lid.isReady()) return;
        SealedContainer container = findSealedContainer(lid);
        if (container == null) return;

        FluidStack stored = firstFluid(container.handler());
        if (stored.isEmpty()) return;
        List<ItemStack> items = collectItems(level, container);
        RecipeHolder<CatalyticPressingRecipe> holder = findRecipe(level, items, stored);
        if (holder == null) return;

        String recipeId = holder.id().toString();
        CatalyticPressingRecipe recipe = holder.value();
        int progress = recipeId.equals(lid.activeRecipe()) ? lid.catalyticProgress() : 0;
        int heat = CatalyticPressHeat.power(level.getBlockState(container.pos().below()));
        lid.setCatalyticProgress(recipeId, progress, recipe.processingTime());
        if (heat <= 0) return;

        progress = Math.min(recipe.processingTime(), progress + heat);
        lid.setCatalyticProgress(recipeId, progress, recipe.processingTime());
        if (progress < recipe.processingTime()) return;
        ItemConsumptionPlan itemPlan = planItems(level, container, recipe.itemIngredients());
        FluidTransformation fluidChange = planTransformation(container.handler(), recipe, stored);
        if (itemPlan == null || fluidChange == null) {
            lid.resetCatalysis();
            return;
        }
        if (fluidChange.commit(container.handler()) && itemPlan.commit()) {
            setContainerColor(level, container, DyeColor.WHITE);
            lid.completeCatalysis();
        } else {
            fluidChange.rollback(container.handler());
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
        if (!melt.is(ModFluids.UNIVERSAL_PLASTIC_MELT.get())) {
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

    private static @Nullable RecipeHolder<CatalyticPressingRecipe> findRecipe(
        ServerLevel level,
        List<ItemStack> items,
        FluidStack fluid
    ) {
        CatalyticPressingRecipe.Input input = new CatalyticPressingRecipe.Input(items, fluid);
        for (RecipeHolder<CatalyticPressingRecipe> holder :
            level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.CATALYTIC_PRESSING_TYPE.get())) {
            if (holder.value().matches(input, level)) return holder;
        }
        return null;
    }

    private static @Nullable FluidTransformation planTransformation(
        IFluidHandler handler,
        CatalyticPressingRecipe recipe,
        FluidStack stored
    ) {
        int batches = recipe.batches(stored);
        FluidStack output = recipe.resultFor(stored);
        if (batches <= 0 || output.isEmpty()) return null;
        int consume = Math.multiplyExact(recipe.fluidIngredient().amount(), batches);
        FluidStack simulated = handler.drain(consume, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.getAmount() != consume || !recipe.fluidIngredient().test(simulated)) return null;
        return new FluidTransformation(simulated.copy(), output.copy());
    }

    private static FluidStack firstFluid(IFluidHandler handler) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stack = handler.getFluidInTank(tank);
            if (!stack.isEmpty()) return stack.copy();
        }
        return FluidStack.EMPTY;
    }

    private static List<ItemStack> collectItems(ServerLevel level, SealedContainer container) {
        List<ItemStack> result = new ArrayList<>();
        IItemHandler handler = itemHandler(level, container);
        if (handler != null) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) result.add(stack.copy());
            }
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(container.pos()).inflate(0.1D))) {
            if (!item.getItem().isEmpty()) result.add(item.getItem().copy());
        }
        return result;
    }

    private static @Nullable ItemConsumptionPlan planItems(
        ServerLevel level,
        SealedContainer container,
        List<Ingredient> ingredients
    ) {
        IItemHandler handler = itemHandler(level, container);
        List<ItemEntity> looseItems = level.getEntitiesOfClass(
            ItemEntity.class,
            new AABB(container.pos()).inflate(0.1D)
        );
        Map<Integer, Integer> handlerCounts = new LinkedHashMap<>();
        Map<ItemEntity, Integer> entityCounts = new IdentityHashMap<>();
        for (Ingredient ingredient : ingredients) {
            boolean reserved = false;
            if (handler != null) {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    int count = handlerCounts.getOrDefault(slot, 0);
                    if (stack.getCount() <= count || !ingredient.test(stack)) continue;
                    if (handler.extractItem(slot, count + 1, true).getCount() < count + 1) continue;
                    handlerCounts.put(slot, count + 1);
                    reserved = true;
                    break;
                }
            }
            if (!reserved) {
                for (ItemEntity entity : looseItems) {
                    ItemStack stack = entity.getItem();
                    int count = entityCounts.getOrDefault(entity, 0);
                    if (stack.getCount() <= count || !ingredient.test(stack)) continue;
                    entityCounts.put(entity, count + 1);
                    reserved = true;
                    break;
                }
            }
            if (!reserved) return null;
        }
        return new ItemConsumptionPlan(handler, handlerCounts, entityCounts);
    }

    private static @Nullable IItemHandler itemHandler(ServerLevel level, SealedContainer container) {
        if (container.plasticCauldron() != null) return container.plasticCauldron().getItemHandler();
        if (level.getBlockEntity(container.pos()) instanceof BondedEntityBlockEntity bonded) {
            return bonded.getItemHandler();
        }
        if (level.getBlockEntity(container.pos()) instanceof FishTankBlockEntity fishTank) {
            return fishTank.getItemHandler();
        }
        return null;
    }

    private static void setContainerColor(ServerLevel level, SealedContainer container, DyeColor color) {
        FluidStack stored = firstFluid(container.handler());
        if (!stored.isEmpty()) {
            PlasticMeltColor.set(stored, color);
            replaceFluid(container.handler(), stored);
        }
        BlockState state = level.getBlockState(container.pos());
        if (state.is(ModBlocks.UNIVERSAL_PLASTIC_MELT_CAULDRON.get())) {
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
        if (state.is(ModBlocks.UNIVERSAL_PLASTIC_MELT_CAULDRON.get())
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
            if (((FluidPipeNetworkExtension) network).plasticraft$pushAll(
                source.handler(),
                source.pos(),
                pipePos,
                source.pos().getY() + 10,
                melt
            )) return true;
        }
        return false;
    }

    private static void burstContainer(ServerLevel level, SealedContainer container, FluidStack melt) {
        DyeColor color = PlasticMeltColor.get(melt);
        if (container.plasticCauldron() != null && !container.plasticCauldron().isRemoved()) {
            EntityBondManager.disconnectEntity(level, container.plasticCauldron());
            container.plasticCauldron().discard();
        }
        level.setBlock(container.pos(), ModBlocks.UNIVERSAL_PLASTIC_MELT.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(container.pos()) instanceof UniversalPlasticMeltBlockEntity blockEntity) {
            blockEntity.setColor(color);
        }
        level.levelEvent(2001, container.pos(), Block.getId(ModBlocks.CATALYTIC_PRESS_LID.get().defaultBlockState()));
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
        level.setBlock(pos, ModBlocks.UNIVERSAL_PLASTIC_MELT.get().defaultBlockState(), Block.UPDATE_ALL);
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

    private record ItemConsumptionPlan(
        @Nullable IItemHandler handler,
        Map<Integer, Integer> handlerCounts,
        Map<ItemEntity, Integer> entityCounts
    ) {
        private boolean commit() {
            for (Map.Entry<ItemEntity, Integer> entry : this.entityCounts.entrySet()) {
                if (entry.getKey().isRemoved() || entry.getKey().getItem().getCount() < entry.getValue()) return false;
            }
            if (this.handler != null) {
                for (Map.Entry<Integer, Integer> entry : this.handlerCounts.entrySet()) {
                    if (this.handler.extractItem(entry.getKey(), entry.getValue(), true).getCount()
                        != entry.getValue()) return false;
                }
                List<SlotExtraction> extracted = new ArrayList<>();
                for (Map.Entry<Integer, Integer> entry : this.handlerCounts.entrySet()) {
                    ItemStack stack = this.handler.extractItem(entry.getKey(), entry.getValue(), false);
                    if (stack.getCount() != entry.getValue()) {
                        if (!stack.isEmpty()) extracted.add(new SlotExtraction(entry.getKey(), stack));
                        for (int i = extracted.size() - 1; i >= 0; i--) {
                            SlotExtraction restore = extracted.get(i);
                            this.handler.insertItem(restore.slot(), restore.stack(), false);
                        }
                        return false;
                    }
                    extracted.add(new SlotExtraction(entry.getKey(), stack));
                }
            }
            for (Map.Entry<ItemEntity, Integer> entry : this.entityCounts.entrySet()) {
                ItemStack stack = entry.getKey().getItem();
                stack.shrink(entry.getValue());
                if (stack.isEmpty()) entry.getKey().discard();
            }
            return true;
        }
    }

    private record SlotExtraction(int slot, ItemStack stack) {
    }

    public record SealedContainer(
        BlockPos pos,
        IFluidHandler handler,
        FluidContainerLookup.Result lookup,
        @Nullable HardenedResinCauldronEntity plasticCauldron
    ) {
    }
}
