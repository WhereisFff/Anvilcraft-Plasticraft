package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.PlasticOilCauldronBlock;
import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltCauldronBlock;
import dev.anvilcraft.plasticraft.block.entity.UniversalPlasticMeltBlockEntity;
import dev.anvilcraft.plasticraft.entity.PlasticCauldron;
import dev.anvilcraft.plasticraft.entity.PlasticCauldronWorkBlockFinder;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItemTags;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.dubhe.anvilcraft.block.entity.FishTankBlockEntity;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** 树脂、金属和玻璃在对应温度条件下催化塑料油的通用服务端过程。 */
public final class PlasticOilCatalysis {
    public static final int BASE_WORK = 800;
    public static final double MAX_OPEN_MULTIPLIER = 0.55D;
    public static final double FROST_METAL_EFFICIENCY = 0.5D;
    public static final double ENGINEERING_REACTION_POWER = 8.0D;
    public static final double ELASTIC_REACTION_POWER = 8.0D;

    private PlasticOilCatalysis() {
    }

    /**
     * 开放反应按物品种类数作对数增长：一种为四分之一，八种为二分之一，最终封顶于 0.55。
     */
    public static double catalystMultiplier(int distinctItems) {
        if (distinctItems <= 0) return 0.0D;
        double fitted = 0.25D + Math.log(distinctItems) / Math.log(2.0D) / 12.0D;
        return Math.min(MAX_OPEN_MULTIPLIER, fitted);
    }

    /** 浮霜金属保留皇家钢魔力，但低温与外部热源相抵，只提供皇家钢新增种类一半的速度。 */
    public static double catalystMultiplier(int royalSteelItems, int frostMetalItems) {
        int royalSteel = Math.max(0, royalSteelItems);
        int frostMetal = Math.max(0, frostMetalItems);
        double royalMultiplier = catalystMultiplier(royalSteel);
        if (frostMetal == 0) return royalMultiplier;
        int combined = (int) Math.min(Integer.MAX_VALUE, (long) royalSteel + frostMetal);
        return royalMultiplier
            + (catalystMultiplier(combined) - royalMultiplier) * FROST_METAL_EFFICIENCY;
    }

    /** 普通炼药锅和世界流体源依靠其中的掉落物推进反应。 */
    public static void tickLooseCatalyst(ItemEntity catalyst) {
        if (!(catalyst.level() instanceof ServerLevel level)
            || catalyst.isRemoved()
            || !isCatalyst(level, catalyst.getItem())) {
            return;
        }
        BlockPos pos = catalyst.blockPosition();
        BlockState state = level.getBlockState(pos);
        if (state.is(PlasticraftBlocks.PLASTIC_OIL_CAULDRON.get())) {
            PlasticOilCauldronBlock cauldron = (PlasticOilCauldronBlock) state.getBlock();
            if (!cauldron.containsEntity(state, pos, catalyst)) return;
            List<ItemEntity> catalysts = looseCatalysts(
                level,
                pos,
                item -> cauldron.containsEntity(state, pos, item)
            );
            CatalystCounts counts = countCatalysts(level, catalysts);
            boolean cold = PlasticCatalysisCold.isCold(level.getBlockState(pos.below()));
            PlasticMaterial productMaterial = oilProduct(counts, cold);
            advance(level, pos, oilWorkPerTick(
                PlasticCatalysisHeat.power(level.getBlockState(pos.below())),
                cold,
                counts
            ), () -> {
                int levelValue = state.getValue(PlasticOilCauldronBlock.LEVEL);
                BlockState result = productMaterial.meltCauldron().defaultBlockState()
                    .setValue(UniversalPlasticMeltCauldronBlock.LEVEL, levelValue);
                if (result.hasProperty(UniversalPlasticMeltCauldronBlock.COLOR)) {
                    result = result.setValue(UniversalPlasticMeltCauldronBlock.COLOR, DyeColor.WHITE);
                }
                return level.setBlock(pos, result, Block.UPDATE_ALL);
            });
            return;
        }
        if (state.is(PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT_CAULDRON.get())) {
            UniversalPlasticMeltCauldronBlock cauldron = (UniversalPlasticMeltCauldronBlock) state.getBlock();
            if (!cauldron.containsEntity(state, pos, catalyst)) return;
            List<ItemEntity> catalysts = looseCatalysts(
                level,
                pos,
                item -> cauldron.containsEntity(state, pos, item)
            );
            CatalystCounts counts = countCatalysts(level, catalysts);
            double heat = PlasticCatalysisHeat.power(level.getBlockState(pos.below()));
            boolean cold = PlasticCatalysisCold.isCold(level.getBlockState(pos.below()));
            PlasticMaterial productMaterial = universalProduct(counts, heat, cold);
            advance(level, pos, universalWorkPerTick(counts, heat, cold), () -> {
                int levelValue = state.getValue(UniversalPlasticMeltCauldronBlock.LEVEL);
                DyeColor color = state.getValue(UniversalPlasticMeltCauldronBlock.COLOR);
                BlockState result = productMaterial.meltCauldron().defaultBlockState()
                    .setValue(UniversalPlasticMeltCauldronBlock.LEVEL, levelValue)
                    .setValue(UniversalPlasticMeltCauldronBlock.COLOR, color);
                return level.setBlock(pos, result, Block.UPDATE_ALL);
            });
            return;
        }
        if (!level.getFluidState(pos).isSourceOfType(PlasticraftFluids.PLASTIC_OIL.get())) {
            if (!level.getFluidState(pos).isSourceOfType(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get())) {
                clearProgress(level, pos);
                return;
            }
            List<ItemEntity> catalysts = looseCatalysts(level, pos, item -> item.blockPosition().equals(pos));
            CatalystCounts counts = countCatalysts(level, catalysts);
            double heat = PlasticCatalysisHeat.power(level.getBlockState(pos.below()));
            boolean cold = PlasticCatalysisCold.isCold(level.getBlockState(pos.below()));
            PlasticMaterial productMaterial = universalProduct(counts, heat, cold);
            advance(level, pos, universalWorkPerTick(counts, heat, cold), () -> {
                DyeColor color = level.getBlockEntity(pos) instanceof UniversalPlasticMeltBlockEntity melt
                    ? melt.getColor()
                    : DyeColor.WHITE;
                FluidStack output = new FluidStack(productMaterial.melt(), 1_000);
                PlasticMeltColor.set(output, color);
                placeMelt(level, pos, output);
                return true;
            });
            return;
        }
        List<ItemEntity> catalysts = looseCatalysts(level, pos, item -> item.blockPosition().equals(pos));
        CatalystCounts counts = countCatalysts(level, catalysts);
        boolean cold = PlasticCatalysisCold.isCold(level.getBlockState(pos.below()));
        advance(level, pos, oilWorkPerTick(
            PlasticCatalysisHeat.power(level.getBlockState(pos.below())),
            cold,
            counts
        ), () -> {
            PlasticMaterial material = oilProduct(counts, cold);
            placeMelt(level, pos, new FluidStack(material.melt(), 1_000));
            return true;
        });
    }

    /** 鱼缸每刻从自己的流体与完整物品库存中检查开放催化。 */
    public static void tickFishTank(ServerLevel level, FishTankBlockEntity tank) {
        tickFluidContainer(
            level,
            tank.getBlockPos(),
            tank.getFluidHandler(),
            countCatalysts(level, tank.getItemHandler(), ItemStack.EMPTY),
            PlasticCatalysisHeat.power(level.getBlockState(tank.getBlockPos().below())),
            PlasticCatalysisCold.isCold(level.getBlockState(tank.getBlockPos().below()))
        );
    }

    /** 实体炼药锅只在开口向上时接受重力面一格内的工作方块。 */
    public static void tickResinCauldron(ServerLevel level, PlasticCauldron cauldron) {
        if (cauldron.getOrientation().attachmentFace() != Direction.UP) return;
        BlockPos pos = BlockPos.containing(cauldron.plasticraft$cauldronEntity().getBoundingBox().getCenter());
        @Nullable CauldronWorkConditions work = PlasticCauldronWorkBlockFinder.find(
            cauldron,
            PlasticOilCatalysis::cauldronWorkConditions
        );
        if (work == null) work = CauldronWorkConditions.NONE;
        tickFluidContainer(
            level,
            pos,
            cauldron.getFluidHandler(),
            countCatalysts(
                level,
                cauldron.getItemHandler(),
                new ItemStack(cauldron.plasticraft$cauldronEntity().getBlockState().getBlock())
            ),
            work.heat(),
            work.cold()
        );
    }

    /** 大型炼药锅按底部九格的实际总热功率取平均值。 */
    public static void tickLargeCauldron(ServerLevel level, LargeCauldronBlockEntity cauldron) {
        if (!cauldron.isMainPart()) return;
        Set<Item> royalSteelCatalysts = new HashSet<>();
        Set<Item> frostMetalCatalysts = new HashSet<>();
        Set<Item> emberMetalCatalysts = new HashSet<>();
        Set<Item> royalGlassCatalysts = new HashSet<>();
        Set<Item> frostGlassCatalysts = new HashSet<>();
        Set<Item> resinCatalysts = new HashSet<>();
        collectCatalysts(
            level,
            cauldron.getInputHandler(),
            royalSteelCatalysts,
            frostMetalCatalysts,
            emberMetalCatalysts,
            royalGlassCatalysts,
            frostGlassCatalysts,
            resinCatalysts
        );
        collectCatalysts(
            level,
            cauldron.getOutputHandler(),
            royalSteelCatalysts,
            frostMetalCatalysts,
            emberMetalCatalysts,
            royalGlassCatalysts,
            frostGlassCatalysts,
            resinCatalysts
        );
        tickFluidContainer(
            level,
            cauldron.getBlockPos(),
            cauldron.getFluids(),
            new CatalystCounts(
                royalSteelCatalysts.size(),
                frostMetalCatalysts.size(),
                emberMetalCatalysts.size(),
                royalGlassCatalysts.size(),
                frostGlassCatalysts.size(),
                resinCatalysts.size()
            ),
            largeCauldronHeat(level, cauldron.getBlockPos()),
            PlasticCatalysisCold.isCold(level.getBlockState(cauldron.getBlockPos().below(2)))
        );
    }

    public static double largeCauldronHeat(ServerLevel level, BlockPos mainPos) {
        BlockPos heatCenter = mainPos.below(2);
        int total = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                total += PlasticCatalysisHeat.power(level.getBlockState(heatCenter.offset(x, 0, z)));
            }
        }
        return total / 9.0D;
    }

    /** 玩家开始追踪区块后补发其中暂停或尚未完成的开放反应。 */
    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        ProgressData.get(event.getLevel()).syncChunk(event.getLevel(), event.getPlayer(), event.getPos());
    }

    private static void tickFluidContainer(
        ServerLevel level,
        BlockPos pos,
        IFluidHandler fluids,
        CatalystCounts catalysts,
        double heat,
        boolean cold
    ) {
        FluidStack input = findReactiveFluid(fluids);
        if (input.isEmpty()) {
            clearProgress(level, pos);
            return;
        }

        ProgressData data = ProgressData.get(level);
        boolean universal = input.is(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get());
        PlasticMaterial outputMaterial = universal
            ? universalProduct(catalysts, heat, cold)
            : oilProduct(catalysts, cold);
        double workPerTick = universal
            ? universalWorkPerTick(catalysts, heat, cold)
            : oilWorkPerTick(heat, cold, catalysts);
        if (workPerTick <= 0.0D) {
            syncPausedProgress(level, pos, data);
            return;
        }
        AdvanceResult result = data.advance(level, pos, level.getGameTime(), workPerTick);
        if (!result.advanced()) return;
        PlasticOilCatalysisVisualSync.update(level, pos, result.work(), workPerTick);
        if (!result.complete()) return;
        FluidStack output = new FluidStack(
            outputMaterial.melt(),
            input.getAmount()
        );
        if (outputMaterial.supportsDyeing()) {
            PlasticMeltColor.set(output, universal ? PlasticMeltColor.get(input) : DyeColor.WHITE);
        }
        if (!transformFluid(fluids, input, output)) return;
        data.clear(pos);
        syncMeltContainerColor(level, pos, output);
        PlasticOilCatalysisVisualSync.complete(level, pos);
        playCompletionSound(level, pos);
    }

    private static void syncMeltContainerColor(ServerLevel level, BlockPos pos, FluidStack melt) {
        PlasticMaterial material = PlasticMaterial.fromMelt(melt).orElse(null);
        if (material == null || !material.supportsDyeing()) {
            return;
        }
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

    private static void placeMelt(ServerLevel level, BlockPos pos, FluidStack melt) {
        PlasticMaterial material = PlasticMaterial.fromMelt(melt).orElse(PlasticMaterial.UNIVERSAL);
        level.setBlock(pos, material.meltBlock().defaultBlockState(), Block.UPDATE_ALL);
        if (material.supportsDyeing()
            && level.getBlockEntity(pos) instanceof UniversalPlasticMeltBlockEntity blockEntity) {
            blockEntity.setColor(PlasticMeltColor.get(melt));
        }
    }

    private static void advance(
        ServerLevel level,
        BlockPos pos,
        double workPerTick,
        Transformation transformation
    ) {
        ProgressData data = ProgressData.get(level);
        if (workPerTick <= 0.0D) {
            syncPausedProgress(level, pos, data);
            return;
        }
        AdvanceResult result = data.advance(level, pos, level.getGameTime(), workPerTick);
        if (!result.advanced()) return;
        PlasticOilCatalysisVisualSync.update(level, pos, result.work(), workPerTick);
        if (!result.complete()) return;
        if (!transformation.apply()) return;
        data.clear(pos);
        PlasticOilCatalysisVisualSync.complete(level, pos);
        playCompletionSound(level, pos);
    }

    private static void syncPausedProgress(ServerLevel level, BlockPos pos, ProgressData data) {
        double work = data.work(pos);
        if (work > 0.0D) PlasticOilCatalysisVisualSync.update(level, pos, work, 0.0D);
    }

    private static void clearProgress(ServerLevel level, BlockPos pos) {
        boolean removed = ProgressData.get(level).clear(pos);
        PlasticOilCatalysisVisualSync.clear(level, pos, removed);
    }

    private static List<ItemEntity> looseCatalysts(
        ServerLevel level,
        BlockPos pos,
        Predicate<ItemEntity> inside
    ) {
        return level.getEntitiesOfClass(
            ItemEntity.class,
            new AABB(pos),
            item -> !item.isRemoved()
                && isCatalyst(level, item.getItem())
                && inside.test(item)
        );
    }

    private static boolean isCatalyst(ServerLevel level, ItemStack stack) {
        return stack.is(PlasticraftItemTags.ROYAL_STEEL_ITEMS)
            || stack.is(PlasticraftItemTags.FROST_METAL_ITEMS)
            || stack.is(PlasticraftItemTags.EMBER_METAL_ITEMS)
            || stack.is(PlasticraftItemTags.ROYAL_GLASS_ITEMS)
            || stack.is(PlasticraftItemTags.FROST_GLASS_ITEMS)
            || ResinCatalysts.contains(level, stack);
    }

    public static boolean delaysSolidification(ServerLevel level, BlockPos pos) {
        if (!level.getFluidState(pos).isSourceOfType(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get())) return false;
        boolean coldBelow = PlasticCatalysisCold.isCold(level.getBlockState(pos.below()));
        return !level.getEntitiesOfClass(
            ItemEntity.class,
            new AABB(pos),
            item -> !item.isRemoved()
                && item.blockPosition().equals(pos)
                && (item.getItem().is(PlasticraftItemTags.FROST_METAL_ITEMS)
                    || coldBelow && item.getItem().is(PlasticraftItemTags.ROYAL_STEEL_ITEMS)
                    || PlasticCatalysisHeat.power(level.getBlockState(pos.below())) > 0.0D
                        && item.getItem().is(PlasticraftItemTags.EMBER_METAL_ITEMS))
        ).isEmpty();
    }

    private static CatalystCounts countCatalysts(ServerLevel level, IItemHandler handler, ItemStack contactCatalyst) {
        Set<Item> royalSteel = new HashSet<>();
        Set<Item> frostMetal = new HashSet<>();
        Set<Item> emberMetal = new HashSet<>();
        Set<Item> royalGlass = new HashSet<>();
        Set<Item> frostGlass = new HashSet<>();
        Set<Item> resin = new HashSet<>();
        collectCatalysts(level, handler, royalSteel, frostMetal, emberMetal, royalGlass, frostGlass, resin);
        if (ResinCatalysts.contains(level, contactCatalyst)) resin.add(contactCatalyst.getItem());
        return new CatalystCounts(
            royalSteel.size(),
            frostMetal.size(),
            emberMetal.size(),
            royalGlass.size(),
            frostGlass.size(),
            resin.size()
        );
    }

    private static CatalystCounts countCatalysts(ServerLevel level, List<ItemEntity> catalysts) {
        Set<Item> royalSteel = new HashSet<>();
        Set<Item> frostMetal = new HashSet<>();
        Set<Item> emberMetal = new HashSet<>();
        Set<Item> royalGlass = new HashSet<>();
        Set<Item> frostGlass = new HashSet<>();
        Set<Item> resin = new HashSet<>();
        for (ItemEntity catalyst : catalysts) {
            collectCatalyst(level, catalyst.getItem(), royalSteel, frostMetal, emberMetal, royalGlass, frostGlass, resin);
        }
        return new CatalystCounts(
            royalSteel.size(),
            frostMetal.size(),
            emberMetal.size(),
            royalGlass.size(),
            frostGlass.size(),
            resin.size()
        );
    }

    private static void collectCatalysts(
        ServerLevel level,
        IItemHandler handler,
        Set<Item> royalSteel,
        Set<Item> frostMetal,
        Set<Item> emberMetal,
        Set<Item> royalGlass,
        Set<Item> frostGlass,
        Set<Item> resin
    ) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            collectCatalyst(level, stack, royalSteel, frostMetal, emberMetal, royalGlass, frostGlass, resin);
        }
    }

    private static void collectCatalyst(
        ServerLevel level,
        ItemStack stack,
        Set<Item> royalSteel,
        Set<Item> frostMetal,
        Set<Item> emberMetal,
        Set<Item> royalGlass,
        Set<Item> frostGlass,
        Set<Item> resin
    ) {
        if (ResinCatalysts.contains(level, stack)) resin.add(stack.getItem());
        if (stack.is(PlasticraftItemTags.ROYAL_GLASS_ITEMS)) {
            royalGlass.add(stack.getItem());
        } else if (stack.is(PlasticraftItemTags.FROST_GLASS_ITEMS)) {
            frostGlass.add(stack.getItem());
        } else if (stack.is(PlasticraftItemTags.ROYAL_STEEL_ITEMS)) {
            royalSteel.add(stack.getItem());
        } else if (stack.is(PlasticraftItemTags.FROST_METAL_ITEMS)) {
            frostMetal.add(stack.getItem());
        } else if (stack.is(PlasticraftItemTags.EMBER_METAL_ITEMS)) {
            emberMetal.add(stack.getItem());
        }
    }

    private static FluidStack findReactiveFluid(IFluidHandler handler) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stored = handler.getFluidInTank(tank);
            if (stored.is(PlasticraftFluids.PLASTIC_OIL.get())
                || stored.is(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get())) {
                return stored.copy();
            }
        }
        return FluidStack.EMPTY;
    }

    private static boolean transformFluid(IFluidHandler handler, FluidStack input, FluidStack output) {
        FluidStack simulated = handler.drain(input, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.getAmount() != input.getAmount()
            || !FluidStack.isSameFluidSameComponents(simulated, input)) return false;

        FluidStack drained = handler.drain(input, IFluidHandler.FluidAction.EXECUTE);
        if (drained.getAmount() != input.getAmount()
            || !FluidStack.isSameFluidSameComponents(drained, input)) {
            if (!drained.isEmpty()) handler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            return false;
        }
        int filled = handler.fill(output, IFluidHandler.FluidAction.EXECUTE);
        if (filled == output.getAmount()) return true;
        if (filled > 0) {
            handler.drain(output.copyWithAmount(filled), IFluidHandler.FluidAction.EXECUTE);
        }
        handler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        return false;
    }

    private static double oilWorkPerTick(double heat, boolean cold, CatalystCounts catalysts) {
        if (cold && catalysts.resinItems() > 0) {
            return ELASTIC_REACTION_POWER * catalystMultiplier(catalysts.resinItems());
        }
        return heat * (catalysts.hasGlass()
            ? catalystMultiplier(catalysts.royalGlassItems(), catalysts.frostGlassItems())
            : catalystMultiplier(catalysts.royalSteelItems(), catalysts.frostMetalItems()));
    }

    private static double engineeringWorkPerTick(CatalystCounts catalysts, boolean cold) {
        int royalSteel = cold ? catalysts.royalSteelItems() : 0;
        return ENGINEERING_REACTION_POWER * catalystMultiplier(royalSteel, catalysts.frostMetalItems());
    }

    private static double heatResistantWorkPerTick(double heat, CatalystCounts catalysts) {
        return heat * catalystMultiplier(catalysts.emberMetalItems());
    }

    private static double universalWorkPerTick(CatalystCounts catalysts, double heat, boolean cold) {
        return catalysts.emberMetalItems() > 0 && heat > 0.0D
            ? heatResistantWorkPerTick(heat, catalysts)
            : engineeringWorkPerTick(catalysts, cold);
    }

    private static PlasticMaterial universalProduct(CatalystCounts catalysts, double heat, boolean cold) {
        return catalysts.emberMetalItems() > 0 && heat > 0.0D
            ? PlasticMaterial.HEAT_RESISTANT
            : PlasticMaterial.ENGINEERING;
    }

    private static PlasticMaterial oilProduct(CatalystCounts catalysts, boolean cold) {
        if (cold && catalysts.resinItems() > 0) return PlasticMaterial.ELASTIC;
        return catalysts.hasGlass() ? PlasticMaterial.CLEAR : PlasticMaterial.UNIVERSAL;
    }

    private static void playCompletionSound(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 0.85F);
    }

    private static @Nullable CauldronWorkConditions cauldronWorkConditions(BlockState state) {
        int heat = PlasticCatalysisHeat.power(state);
        boolean cold = PlasticCatalysisCold.isCold(state);
        return heat > 0 || cold ? new CauldronWorkConditions(heat, cold) : null;
    }

    @FunctionalInterface
    private interface Transformation {
        boolean apply();
    }

    private record CatalystCounts(
        int royalSteelItems,
        int frostMetalItems,
        int emberMetalItems,
        int royalGlassItems,
        int frostGlassItems,
        int resinItems
    ) {
        private boolean hasGlass() {
            return this.royalGlassItems > 0 || this.frostGlassItems > 0;
        }
    }

    private record CauldronWorkConditions(double heat, boolean cold) {
        private static final CauldronWorkConditions NONE = new CauldronWorkConditions(0.0D, false);
    }

    /** 没有方块实体的反应位置也通过维度数据持久化进度。 */
    private static final class ProgressData extends SavedData {
        private static final String NAME = AnvilcraftPlasticraft.MOD_ID + "_plastic_oil_catalysis";
        private static final SavedData.Factory<ProgressData> FACTORY = new SavedData.Factory<>(
            ProgressData::new,
            ProgressData::load,
            null
        );
        private final Map<Long, Progress> progress = new HashMap<>();

        private static ProgressData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
        }

        private static ProgressData load(CompoundTag tag, HolderLookup.Provider registries) {
            ProgressData data = new ProgressData();
            ListTag entries = tag.getList("Entries", Tag.TAG_COMPOUND);
            for (int index = 0; index < entries.size(); index++) {
                CompoundTag entry = entries.getCompound(index);
                data.progress.put(
                    entry.getLong("Pos"),
                    new Progress(entry.getDouble("Work"), entry.getLong("LastTick"))
                );
            }
            return data;
        }

        private AdvanceResult advance(ServerLevel level, BlockPos pos, long gameTime, double work) {
            long key = pos.asLong();
            Progress previous = this.progress.getOrDefault(key, new Progress(0.0D, Long.MIN_VALUE));
            if (previous.lastTick() == gameTime) {
                return new AdvanceResult(previous.work(), previous.work() >= BASE_WORK, false);
            }
            double next = Math.min(BASE_WORK, previous.work() + Math.max(0.0D, work));
            this.progress.put(key, new Progress(next, gameTime));
            this.removeStale(level, gameTime);
            this.setDirty();
            return new AdvanceResult(next, next >= BASE_WORK, true);
        }

        private double work(BlockPos pos) {
            Progress value = this.progress.get(pos.asLong());
            return value == null ? 0.0D : value.work();
        }

        private boolean clear(BlockPos pos) {
            if (this.progress.remove(pos.asLong()) == null) return false;
            this.setDirty();
            return true;
        }

        private void syncChunk(ServerLevel level, ServerPlayer player, ChunkPos chunk) {
            for (Map.Entry<Long, Progress> entry : this.progress.entrySet()) {
                BlockPos pos = BlockPos.of(entry.getKey());
                if (pos.getX() >> 4 != chunk.x || pos.getZ() >> 4 != chunk.z) continue;
                PlasticOilCatalysisVisualSync.sendToPlayer(
                    level,
                    player,
                    pos,
                    entry.getValue().work()
                );
            }
        }

        private void removeStale(ServerLevel level, long gameTime) {
            if (gameTime % 1_200L != 0L) return;
            var iterator = this.progress.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, Progress> entry = iterator.next();
                if (gameTime - entry.getValue().lastTick() <= 12_000L) continue;
                iterator.remove();
                PlasticOilCatalysisVisualSync.clear(level, BlockPos.of(entry.getKey()), true);
            }
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag entries = new ListTag();
            for (Map.Entry<Long, Progress> value : this.progress.entrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putLong("Pos", value.getKey());
                entry.putDouble("Work", value.getValue().work());
                entry.putLong("LastTick", value.getValue().lastTick());
                entries.add(entry);
            }
            tag.put("Entries", entries);
            return tag;
        }
    }

    private record Progress(double work, long lastTick) {
    }

    private record AdvanceResult(double work, boolean complete, boolean advanced) {
    }
}
