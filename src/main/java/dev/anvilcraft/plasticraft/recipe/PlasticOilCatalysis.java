package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.PlasticOilCauldronBlock;
import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltCauldronBlock;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.CatalyticPressLidEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItemTags;
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

/** 皇家钢和浮霜金属在受热流体中催化塑料油的通用服务端过程。 */
public final class PlasticOilCatalysis {
    public static final int BASE_WORK = 800;
    public static final double MAX_OPEN_MULTIPLIER = 0.55D;
    public static final double FROST_METAL_EFFICIENCY = 0.5D;

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
            || !isCatalyst(catalyst.getItem())) {
            return;
        }
        BlockPos pos = catalyst.blockPosition();
        BlockState state = level.getBlockState(pos);
        if (state.is(PlasticraftBlocks.PLASTIC_OIL_CAULDRON.get())) {
            PlasticOilCauldronBlock cauldron = (PlasticOilCauldronBlock) state.getBlock();
            if (!cauldron.containsEntity(state, pos, catalyst) || hasSealedLid(level, pos)) return;
            List<ItemEntity> catalysts = looseCatalysts(
                level,
                pos,
                item -> cauldron.containsEntity(state, pos, item)
            );
            advance(level, pos, CatalyticPressHeat.power(level.getBlockState(pos.below())), catalysts, () -> {
                int levelValue = state.getValue(PlasticOilCauldronBlock.LEVEL);
                BlockState result = PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT_CAULDRON.get()
                    .defaultBlockState()
                    .setValue(UniversalPlasticMeltCauldronBlock.LEVEL, levelValue)
                    .setValue(UniversalPlasticMeltCauldronBlock.COLOR, DyeColor.WHITE);
                return level.setBlock(pos, result, Block.UPDATE_ALL);
            });
            return;
        }
        if (!level.getFluidState(pos).isSourceOfType(PlasticraftFluids.PLASTIC_OIL.get())) {
            clearProgress(level, pos);
            return;
        }
        List<ItemEntity> catalysts = looseCatalysts(level, pos, item -> item.blockPosition().equals(pos));
        advance(level, pos, CatalyticPressHeat.power(level.getBlockState(pos.below())), catalysts, () -> {
            CatalyticPressProcess.placeMelt(
                level,
                pos,
                new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), 1_000)
            );
            return true;
        });
    }

    /** 鱼缸每刻从自己的流体与完整物品库存中检查开放催化。 */
    public static void tickFishTank(ServerLevel level, FishTankBlockEntity tank) {
        tickFluidContainer(
            level,
            tank.getBlockPos(),
            tank.getFluidHandler(),
            countCatalysts(tank.getItemHandler()),
            CatalyticPressHeat.power(level.getBlockState(tank.getBlockPos().below())),
            true
        );
    }

    /** 硬化树脂锅只在开口向上时接受正下方热源。 */
    public static void tickResinCauldron(ServerLevel level, HardenedResinCauldronEntity cauldron) {
        if (cauldron.getOrientation().attachmentFace() != Direction.UP) return;
        BlockPos pos = CatalyticPressProcess.occupiedPos(cauldron);
        tickFluidContainer(
            level,
            pos,
            cauldron.getFluidHandler(),
            countCatalysts(cauldron.getItemHandler()),
            CatalyticPressHeat.power(level.getBlockState(pos.below())),
            true
        );
    }

    /** 大型炼药锅按底部九格的实际总热功率取平均值。 */
    public static void tickLargeCauldron(ServerLevel level, LargeCauldronBlockEntity cauldron) {
        if (!cauldron.isMainPart()) return;
        Set<Item> royalSteelCatalysts = new HashSet<>();
        Set<Item> frostMetalCatalysts = new HashSet<>();
        collectCatalysts(cauldron.getInputHandler(), royalSteelCatalysts, frostMetalCatalysts);
        collectCatalysts(cauldron.getOutputHandler(), royalSteelCatalysts, frostMetalCatalysts);
        tickFluidContainer(
            level,
            cauldron.getBlockPos(),
            cauldron.getFluids(),
            new CatalystCounts(royalSteelCatalysts.size(), frostMetalCatalysts.size()),
            largeCauldronHeat(level, cauldron.getBlockPos()),
            false
        );
    }

    public static double largeCauldronHeat(ServerLevel level, BlockPos mainPos) {
        BlockPos heatCenter = mainPos.below(2);
        int total = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                total += CatalyticPressHeat.power(level.getBlockState(heatCenter.offset(x, 0, z)));
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
        boolean checkLid
    ) {
        FluidStack plasticOil = findPlasticOil(fluids);
        if (plasticOil.isEmpty()) {
            clearProgress(level, pos);
            return;
        }
        if (checkLid && hasSealedLid(level, pos)) return;

        ProgressData data = ProgressData.get(level);
        double workPerTick = heat * catalystMultiplier(catalysts.royalSteelItems(), catalysts.frostMetalItems());
        if (workPerTick <= 0.0D) {
            syncPausedProgress(level, pos, data);
            return;
        }
        AdvanceResult result = data.advance(level, pos, level.getGameTime(), workPerTick);
        if (!result.advanced()) return;
        PlasticOilCatalysisVisualSync.update(level, pos, result.work(), workPerTick);
        if (!result.complete()) return;
        if (!transformFluid(fluids, plasticOil)) return;
        data.clear(pos);
        CatalyticPressProcess.syncMeltContainerColor(
            level,
            pos,
            new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), plasticOil.getAmount())
        );
        PlasticOilCatalysisVisualSync.complete(level, pos);
        playCompletionSound(level, pos);
    }

    private static void advance(
        ServerLevel level,
        BlockPos pos,
        double heat,
        List<ItemEntity> catalysts,
        Transformation transformation
    ) {
        CatalystCounts counts = countCatalysts(catalysts);
        ProgressData data = ProgressData.get(level);
        double workPerTick = heat * catalystMultiplier(counts.royalSteelItems(), counts.frostMetalItems());
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
                && isCatalyst(item.getItem())
                && inside.test(item)
        );
    }

    private static boolean isCatalyst(ItemStack stack) {
        return stack.is(PlasticraftItemTags.ROYAL_STEEL_ITEMS) || stack.is(PlasticraftItemTags.FROST_METAL_ITEMS);
    }

    private static CatalystCounts countCatalysts(IItemHandler handler) {
        Set<Item> royalSteel = new HashSet<>();
        Set<Item> frostMetal = new HashSet<>();
        collectCatalysts(handler, royalSteel, frostMetal);
        return new CatalystCounts(royalSteel.size(), frostMetal.size());
    }

    private static CatalystCounts countCatalysts(List<ItemEntity> catalysts) {
        Set<Item> royalSteel = new HashSet<>();
        Set<Item> frostMetal = new HashSet<>();
        for (ItemEntity catalyst : catalysts) {
            collectCatalyst(catalyst.getItem(), royalSteel, frostMetal);
        }
        return new CatalystCounts(royalSteel.size(), frostMetal.size());
    }

    private static void collectCatalysts(IItemHandler handler, Set<Item> royalSteel, Set<Item> frostMetal) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            collectCatalyst(stack, royalSteel, frostMetal);
        }
    }

    private static void collectCatalyst(ItemStack stack, Set<Item> royalSteel, Set<Item> frostMetal) {
        if (stack.is(PlasticraftItemTags.ROYAL_STEEL_ITEMS)) {
            royalSteel.add(stack.getItem());
        } else if (stack.is(PlasticraftItemTags.FROST_METAL_ITEMS)) {
            frostMetal.add(stack.getItem());
        }
    }

    private static FluidStack findPlasticOil(IFluidHandler handler) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stored = handler.getFluidInTank(tank);
            if (stored.is(PlasticraftFluids.PLASTIC_OIL.get())) return stored.copy();
        }
        return FluidStack.EMPTY;
    }

    private static boolean transformFluid(IFluidHandler handler, FluidStack input) {
        FluidStack simulated = handler.drain(input, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.getAmount() != input.getAmount()
            || !FluidStack.isSameFluidSameComponents(simulated, input)) return false;

        FluidStack drained = handler.drain(input, IFluidHandler.FluidAction.EXECUTE);
        if (drained.getAmount() != input.getAmount()
            || !FluidStack.isSameFluidSameComponents(drained, input)) {
            if (!drained.isEmpty()) handler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            return false;
        }
        FluidStack output = new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), drained.getAmount());
        int filled = handler.fill(output, IFluidHandler.FluidAction.EXECUTE);
        if (filled == output.getAmount()) return true;
        if (filled > 0) {
            handler.drain(output.copyWithAmount(filled), IFluidHandler.FluidAction.EXECUTE);
        }
        handler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        return false;
    }

    private static boolean hasSealedLid(ServerLevel level, BlockPos containerPos) {
        BlockPos lidPos = containerPos.above();
        if (level.getBlockEntity(lidPos) instanceof BondedEntityBlockEntity bonded
            && bonded.getOrCreateRenderEntity() instanceof CatalyticPressLidEntity lid
            && lid.getOrientation().attachmentFace() == Direction.UP) {
            return true;
        }
        return !level.getEntitiesOfClass(
            CatalyticPressLidEntity.class,
            new AABB(lidPos),
            lid -> !lid.isRemoved()
                && lid.getOrientation().attachmentFace() == Direction.UP
                && CatalyticPressProcess.occupiedPos(lid).equals(lidPos)
        ).isEmpty();
    }

    private static void playCompletionSound(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 0.85F);
    }

    @FunctionalInterface
    private interface Transformation {
        boolean apply();
    }

    private record CatalystCounts(int royalSteelItems, int frostMetalItems) {
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
