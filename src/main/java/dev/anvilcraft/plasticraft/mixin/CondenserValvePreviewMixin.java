package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.lib.v2.multiblock.dynamic.definition.MultiblockDefinition;
import dev.anvilcraft.plasticraft.client.AnvilCraftClientApiBootstrap;
import dev.dubhe.anvilcraft.client.support.LevelLikeDisplaySupport;
import dev.dubhe.anvilcraft.util.LevelLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelLikeDisplaySupport.class)
abstract class CondenserValvePreviewMixin {
    @Inject(
        method = "asLevelLike(Ldev/anvilcraft/lib/v2/multiblock/dynamic/definition/MultiblockDefinition;)Ldev/dubhe/anvilcraft/util/LevelLike;",
        at = @At("RETURN")
    )
    private static void plasticraft$seedValves(MultiblockDefinition definition, CallbackInfoReturnable<LevelLike> cir) {
        AnvilCraftClientApiBootstrap.seedCondenserValves(definition, cir.getReturnValue());
    }
}
