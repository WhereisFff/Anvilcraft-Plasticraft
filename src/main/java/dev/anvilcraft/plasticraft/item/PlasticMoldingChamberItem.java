package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberStructure;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** 在消耗物品前一次性提交控制器和全部 27 个区域部件。 */
public class PlasticMoldingChamberItem extends BlockItem {
    public PlasticMoldingChamberItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return PlasticMoldingChamberStructure.placeAtomically(
            context.getLevel(),
            context.getClickedPos(),
            state
        );
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        Item.TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.anvilcraftplasticraft.plastic_molding_chamber"));
    }
}
