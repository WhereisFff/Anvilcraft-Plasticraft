package dev.anvilcraft.plasticraft.block;

import dev.dubhe.anvilcraft.block.ResinBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** 保留本体树脂弹性并提供增强活塞粘连的高粘性树脂块。 */
public class HighViscosityResinBlock extends ResinBlock {
    public HighViscosityResinBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isStickyBlock(BlockState state) {
        return true;
    }

    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        return true;
    }
}
