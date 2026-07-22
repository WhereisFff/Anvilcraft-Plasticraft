package dev.anvilcraft.yukkuri.mixin;

import dev.anvilcraft.yukkuri.api.vapor.VaporizationManager;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs the shared large-cauldron process once from AnvilCraft's server tick. */
@Mixin(LargeCauldronBlockEntity.class)
abstract class LargeCauldronBlockEntityMixin {
    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void yukkuri$processVaporization(
        Level level,
        BlockPos pos,
        BlockState state,
        LargeCauldronBlockEntity entity,
        CallbackInfo ci
    ) {
        if (level instanceof ServerLevel serverLevel) {
            VaporizationManager.tick(serverLevel, entity);
        }
    }
}
