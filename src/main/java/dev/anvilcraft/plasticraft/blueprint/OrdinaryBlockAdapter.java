package dev.anvilcraft.plasticraft.blueprint;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.StructureVoidBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * TODO 06 普通方块材料映射:有放置物品的方块消耗一份对应物品,朝向等属性随蓝图恢复;
 * 门上半、床头和活塞头是依附格,不重复扣料。流体与无物品状态记为不支持,留给后续 TODO。
 */
public final class OrdinaryBlockAdapter {
    public enum Mapping {
        AIR,
        PLACE,
        ATTACHED,
        UNSUPPORTED
    }

    private OrdinaryBlockAdapter() {
    }

    public static Mapping mapping(BlockState state) {
        if (state.isAir() || state.getBlock() instanceof StructureVoidBlock) {
            return Mapping.AIR;
        }
        if (state.getBlock() instanceof LiquidBlock) {
            return Mapping.UNSUPPORTED;
        }
        if (isAttachedHalf(state)) {
            return Mapping.ATTACHED;
        }
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) {
            return Mapping.UNSUPPORTED;
        }
        return Mapping.PLACE;
    }

    public static ItemStack material(BlockState state) {
        if (mapping(state) != Mapping.PLACE) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(state.getBlock().asItem());
    }

    public static boolean isAttachedHalf(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
            && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return true;
        }
        if (state.hasProperty(BlockStateProperties.BED_PART)
            && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
            return true;
        }
        return state.getBlock() instanceof PistonHeadBlock;
    }
}
