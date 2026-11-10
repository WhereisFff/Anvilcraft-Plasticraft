package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.molding.product.storage.MoldedPlasticCrateAccess;
import dev.dubhe.anvilcraft.block.container.storage.CrateBlock;
import dev.dubhe.anvilcraft.block.entity.storage.CrateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = CrateBlock.class, remap = false)
abstract class PlasticCrateSearchMixin {
    @Inject(method = "getNearbyCrates", at = @At("RETURN"), cancellable = true)
    private static void plasticraft$includeCrates(Level level, BlockPos pos, CallbackInfoReturnable<List<CrateBlockEntity>> callback) {
        callback.setReturnValue(MoldedPlasticCrateAccess.includeNearby(level, pos, callback.getReturnValue()));
    }
}
