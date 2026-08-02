package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.block.entity.UniversalPlasticMeltBlockEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** 世界中单格通用塑料熔体的唯一固化结算服务。 */
public final class UniversalPlasticSolidification {
    private UniversalPlasticSolidification() {
    }

    /**
     * 捕获熔体颜色并在原位置原子替换为通用塑料制品。
     *
     * <p>调用前后的方块身份检查保证计划刻、随机刻和相邻方块更新即使在同一刻触发，
     * 也只有第一个调用能够提交；声音只在替换成功后播放，且本路径永不生成塑料粒。</p>
     *
     * @return 当前位置确实由熔体成功固化时返回 {@code true}
     */
    public static boolean solidify(ServerLevel level, BlockPos pos) {
        BlockState meltState = level.getBlockState(pos);
        if (!meltState.is(PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT.get())) return false;

        DyeColor color = level.getBlockEntity(pos) instanceof UniversalPlasticMeltBlockEntity melt
            ? melt.getColor()
            : DyeColor.WHITE;
        BlockState product = PlasticraftBlocks.UNIVERSAL_PLASTIC.get()
            .defaultBlockState()
            .setValue(DyeableMaterial.COLOR, color);
        if (!level.setBlock(pos, product, Block.UPDATE_ALL)) return false;

        level.playSound(
            null,
            pos,
            SoundEvents.FIRE_EXTINGUISH,
            SoundSource.BLOCKS,
            0.8F,
            1.15F
        );
        return true;
    }
}
