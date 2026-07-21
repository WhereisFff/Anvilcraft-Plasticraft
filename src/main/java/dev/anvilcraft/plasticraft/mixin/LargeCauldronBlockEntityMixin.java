package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.recipe.CondenserTowerProcess;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将大型炼药锅的顶层原油接入等离子喷流气化。 */
@Mixin(LargeCauldronBlockEntity.class)
abstract class LargeCauldronBlockEntityMixin {
    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void plasticraft$vaporizeTopOil(
        Level level,
        BlockPos pos,
        BlockState state,
        LargeCauldronBlockEntity entity,
        CallbackInfo ci
    ) {
        if (level instanceof ServerLevel serverLevel) {
            CondenserTowerProcess.tickLargeCauldron(serverLevel, entity);
        }
    }
}
