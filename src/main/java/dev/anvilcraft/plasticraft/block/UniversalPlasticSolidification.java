package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.block.entity.UniversalPlasticMeltBlockEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.item.CooledPlasticItemStacks;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

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
        PlasticMaterial material = PlasticMaterial.fromMeltBlock(meltState).orElse(null);
        if (material == null) return false;

        DyeColor color = level.getBlockEntity(pos) instanceof UniversalPlasticMeltBlockEntity melt
            ? melt.getColor()
            : DyeColor.WHITE;
        BlockState displayState = material.productBlock().defaultBlockState();
        if (material.supportsDyeing() && displayState.hasProperty(DyeableMaterial.COLOR)) {
            displayState = displayState.setValue(DyeableMaterial.COLOR, color);
        }
        ItemStack dropStack = CooledPlasticItemStacks.create(material, color);
        PlasticEntityOrientation orientation = PlasticEntityOrientation.DEFAULT;
        UniversalPlasticEntity entity = material.createEntity(
            level,
            Vec3.atBottomCenterOf(pos),
            displayState,
            dropStack,
            orientation
        );
        entity.setPos(entity.plasticraft$placementPosition(pos, orientation));
        entity.setStartPos(entity.blockPosition());

        if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) return false;
        if (!level.addFreshEntity(entity)) {
            level.setBlock(pos, meltState, Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof UniversalPlasticMeltBlockEntity restoredMelt) {
                restoredMelt.setColor(color);
            }
            return false;
        }

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
