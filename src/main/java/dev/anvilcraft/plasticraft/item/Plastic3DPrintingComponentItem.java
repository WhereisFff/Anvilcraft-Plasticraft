package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;

public class Plastic3DPrintingComponentItem extends BlockItem {
    public Plastic3DPrintingComponentItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos pos = context.getClickedPos();
        if (context.getLevel().getBlockState(pos).is(PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.get())) {
            // 只重定向安装位置，保留原版的占位检查、组件数据、音效与物品消耗。
            context = new UseOnContext(context.getLevel(), context.getPlayer(), context.getHand(), context.getItemInHand(),
                new BlockHitResult(pos.getCenter().add(0, 0.5, 0), Direction.UP, pos, false));
        }
        return super.useOn(context);
    }
}
