package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import dev.dubhe.anvilcraft.block.entity.ItemDetectorBlockEntity;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/** 支架上 AnvilCraft 物品探测器的范围、筛选、容器统计与模拟输出。 */
final class MoldedTrayItemDetectorBehavior implements MoldedTrayRedstoneBehavior {
    @Override
    public boolean supports(BlockState state) {
        return state.is(ModBlocks.ITEM_DETECTOR.get());
    }

    @Override
    public boolean tick(MoldedTrayRedstoneRuntime runtime, BlockState state) {
        if (!(runtime.cachedBlockEntity() instanceof ItemDetectorBlockEntity detector)
            || !(runtime.host().level() instanceof ServerLevel level)) return false;
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        AABB range = MoldedTrayRedstoneNetwork.forwardRange(
            runtime.host(),
            runtime.cell(),
            facing,
            detector.getRange()
        );
        int output = detectorOutput(level, detector, range);
        boolean changed = output != runtime.detectorOutput();
        runtime.detectorOutput(output);
        boolean powered = output > 0;
        if (state.getValue(BlockStateProperties.POWERED) != powered) {
            runtime.setState(runtime.state().setValue(BlockStateProperties.POWERED, powered));
            changed = true;
        }
        return changed;
    }

    @Override
    public InteractionResult interact(
        MoldedTrayRedstoneRuntime runtime,
        Player player,
        InteractionHand hand,
        BlockState state
    ) {
        return runtime.openMenu(player);
    }

    @Override
    public int weakSignal(MoldedTrayComponent component, Direction queryDirection) {
        BlockState state = component.state();
        if (state.getValue(BlockStateProperties.HORIZONTAL_FACING) != queryDirection) return 0;
        return Math.clamp(component.blockEntityData().getInt("OutputSignal"), 0, 15);
    }

    @Override
    public boolean supportsStructureDisk() {
        return true;
    }

    @Override
    public void loadRuntime(
        MoldedTrayRedstoneRuntime runtime,
        MoldedTrayComponent component
    ) {
        runtime.detectorOutput(component.blockEntityData().getInt("OutputSignal"));
    }

    @Override
    public void writeSnapshotData(
        MoldedTrayRedstoneRuntime runtime,
        CompoundTag data
    ) {
        data.putInt("OutputSignal", runtime.detectorOutput());
    }

    private static int detectorOutput(
        ServerLevel level,
        ItemDetectorBlockEntity detector,
        AABB range
    ) {
        List<ItemEntity> entities = level.getEntitiesOfClass(
            ItemEntity.class,
            range,
            entity -> !entity.getItem().isEmpty()
        );
        int minimum = 16;
        boolean canOutput = true;
        boolean hasFilter = false;
        for (ItemStack filter : detector.getFilteredItems()) {
            if (filter.isEmpty()) continue;
            hasFilter = true;
            int count = 0;
            for (ItemEntity entity : entities) {
                if (entity.getItem().is(filter.getItem())) count += entity.getItem().getCount();
            }
            for (BlockPos position : containedPositions(range)) {
                count += countContainer(level, position, filter.getItem());
            }
            int signal = lerpOutput(count, filter.getCount());
            if (signal > 0) minimum = Math.min(minimum, signal);
            else if (detector.getFilterMode() == ItemDetectorBlockEntity.Mode.ALL) canOutput = false;
        }
        if (!hasFilter) {
            int count = entities.stream().mapToInt(entity -> entity.getItem().getCount()).sum();
            for (BlockPos position : containedPositions(range)) {
                count += countContainer(level, position, null);
            }
            return lerpOutput(count, 1);
        }
        return canOutput && minimum <= 15 ? minimum : 0;
    }

    private static Iterable<BlockPos> containedPositions(AABB range) {
        return BlockPos.betweenClosed(
            (int) Math.floor(range.minX),
            (int) Math.floor(range.minY),
            (int) Math.floor(range.minZ),
            (int) Math.ceil(range.maxX) - 1,
            (int) Math.ceil(range.maxY) - 1,
            (int) Math.ceil(range.maxZ) - 1
        );
    }

    private static int countContainer(ServerLevel level, BlockPos position, Item item) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, position, null);
        if (handler == null) return 0;
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && (item == null || stack.is(item))) count += stack.getCount();
        }
        return count;
    }

    private static int lerpOutput(int count, int target) {
        if (count < target) return 0;
        return Math.min(15, 1 + (count - target) * 14 / (63 * Math.max(1, target)));
    }
}
