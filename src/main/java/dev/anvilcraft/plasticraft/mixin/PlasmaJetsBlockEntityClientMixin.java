package dev.anvilcraft.plasticraft.mixin;

import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.entity.PlasmaJetsBlockEntity;
import dev.dubhe.anvilcraft.init.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 仅补齐大型炼药锅底部占位导致缺失的最后一格喷流粒子。
 * 移植时应复核本体 {@code summonParticles} 的粒子弹道参数。
 */
@Mixin(PlasmaJetsBlockEntity.class)
abstract class PlasmaJetsBlockEntityClientMixin {
    private static final int TOP_CELL_PARTICLES_PER_TICK = 3;
    private static final double TOP_CELL_VERTICAL_SPEED = 0.2D;

    @Inject(method = "summonParticles", at = @At("TAIL"))
    private void plasticraft$fillTopCellBelowLargeCauldron(ClientLevel level, CallbackInfo ci) {
        PlasmaJetsBlockEntity self = (PlasmaJetsBlockEntity) (Object) this;
        BlockPos jetPos = self.getBlockPos();
        BlockState stateAbove = level.getBlockState(jetPos.above());
        if (!(stateAbove.getBlock() instanceof LargeCauldronBlock)
            || !stateAbove.hasProperty(LargeCauldronBlock.HALF)
            || stateAbove.getValue(LargeCauldronBlock.HALF).getOffsetY() != 0) {
            return;
        }

        Vec3 start = jetPos.getBottomCenter().add(0.0D, 0.05D, 0.0D);
        RandomSource random = level.getRandom();
        for (int i = 0; i < TOP_CELL_PARTICLES_PER_TICK; i++) {
            level.addParticle(
                ModParticles.PLASMA_JETS.get(),
                true,
                start.x,
                start.y,
                start.z,
                (random.nextIntBetweenInclusive(0, 20) - 10) / 100.0D,
                TOP_CELL_VERTICAL_SPEED,
                (random.nextIntBetweenInclusive(0, 20) - 10) / 100.0D
            );
        }
    }
}
