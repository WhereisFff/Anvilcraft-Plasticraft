package dev.anvilcraft.plasticraft.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** 临时流体封堵块:稳定实心、无方块实体、不会下落或立即自毁。 */
public final class FluidSealFill {
    private FluidSealFill() {
    }

    public static boolean isValid(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        if (block instanceof FallingBlock
            || block instanceof EntityBlock
            || block instanceof LiquidBlock
            || block instanceof LeavesBlock
            || block instanceof TntBlock
            || block instanceof FireBlock
            || block instanceof ScaffoldingBlock) {
            return false;
        }
        if (block.defaultDestroyTime() < 0.0F) return false;
        BlockState state = block.defaultBlockState();
        return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    @Nullable
    public static BlockState stateOf(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return null;
        return blockItem.getBlock().defaultBlockState();
    }

    /** 先为蓝图 PLACE 预留背包数量,再从剩余物品里选数量最多的合法填充块。 */
    public static ItemStack choose(Player player, ConstructionJobProgress progress) {
        return chooseFromCounts(countOwned(player), progress);
    }

    /** 认领后从休息室下方容器选填充块,预留规则与玩家背包相同。 */
    public static ItemStack choose(IItemHandler items, ConstructionJobProgress progress) {
        return chooseFromCounts(countOwned(items), progress);
    }

    public static ItemStack choose(ConstructionMaterialAccess access, ConstructionJobProgress progress) {
        if (access.isInfinite()) return new ItemStack(Items.COBBLESTONE);
        return chooseFromCounts(access.countItems(), progress);
    }

    private static ItemStack chooseFromCounts(Map<Item, Integer> owned, ConstructionJobProgress progress) {
        Map<Item, Integer> reserved = new HashMap<>();
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.PLACE || !op.needsMaterial()) continue;
            if (op.status() == ConstructionBuildOp.Status.DELIVERED
                || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                continue;
            }
            reserved.merge(op.material().getItem(), 1, Integer::sum);
        }
        Item best = null;
        int bestLeft = 0;
        for (Map.Entry<Item, Integer> entry : owned.entrySet()) {
            ItemStack sample = new ItemStack(entry.getKey());
            if (!isValid(sample)) continue;
            int left = entry.getValue() - reserved.getOrDefault(entry.getKey(), 0);
            if (left > bestLeft) {
                bestLeft = left;
                best = entry.getKey();
            }
        }
        return best == null ? ItemStack.EMPTY : new ItemStack(best);
    }

    private static Map<Item, Integer> countOwned(Player player) {
        Map<Item, Integer> owned = new HashMap<>();
        Inventory inventory = player.getInventory();
        for (ItemStack stack : inventory.items) {
            addCount(owned, stack);
        }
        addCount(owned, inventory.offhand.getFirst());
        return owned;
    }

    private static Map<Item, Integer> countOwned(IItemHandler items) {
        Map<Item, Integer> owned = new HashMap<>();
        for (int slot = 0; slot < items.getSlots(); slot++) {
            addCount(owned, items.getStackInSlot(slot));
        }
        return owned;
    }

    private static void addCount(Map<Item, Integer> owned, ItemStack stack) {
        if (stack.isEmpty()) return;
        owned.merge(stack.getItem(), stack.getCount(), Integer::sum);
    }
}
