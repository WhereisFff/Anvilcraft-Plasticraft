package dev.anvilcraft.plasticraft.client.hud;

import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.dubhe.anvilcraft.api.tooltip.providers.ITooltipProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** 用铁砧工艺原生浮窗样式显示真实方块上的树脂粘合状态。 */
public final class BondedBlockTooltipProvider extends ITooltipProvider.BlockTooltipProvider {
    @Override
    public boolean accepts(Level level, BlockPos pos, BlockState state) {
        return BondedFallingBlocks.isBonded(level, pos);
    }

    @Override
    public List<Component> tooltip(Level level, BlockPos pos, BlockState state) {
        return List.of(Component.translatable("tooltip.anvilcraftplasticraft.bonded"));
    }

    @Override
    public ItemStack icon(Level level, BlockPos pos, BlockState state) {
        return state.getBlock().asItem().getDefaultInstance();
    }

    @Override
    public int priority() {
        return 0;
    }
}
