package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.dubhe.anvilcraft.block.sliding.DetectorSlidingRailBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 仅扩展 {@link DetectorSlidingRailBlock#tick} 原有的二十刻占用复查。
 * 迁移到 26.1 时若滑轨提供公开载荷查询，应改用该 API 并移除此 Mixin。
 */
@Mixin(DetectorSlidingRailBlock.class)
abstract class DetectorSlidingRailBlockMixin {
    @ModifyExpressionValue(
        method = "tick",
        at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z", ordinal = 0)
    )
    private boolean plasticraft$keepPoweredForPlasticEntity(
        boolean original,
        BlockState state,
        ServerLevel level,
        BlockPos pos,
        RandomSource random
    ) {
        return original
            && level.getEntitiesOfClass(AbstractPlasticEntity.class, new AABB(pos.above())).isEmpty();
    }
}
