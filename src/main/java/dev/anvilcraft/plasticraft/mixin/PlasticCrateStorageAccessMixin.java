package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.molding.product.storage.MoldedPlasticCrateAccess;
import dev.dubhe.anvilcraft.rpc.StorageServerStub;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = StorageServerStub.StorageAccessValidator.class, remap = false)
abstract class PlasticCrateStorageAccessMixin {
    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
    private void plasticraft$validateCrate(IPayloadContext context, Object[] args, CallbackInfoReturnable<Boolean> callback) {
        if (context.player() instanceof ServerPlayer player && args.length >= 2
            && args[0] instanceof UUID id && args[1] instanceof Long source
            && MoldedPlasticCrateAccess.isSource(source)) {
            callback.setReturnValue(id.equals(player.getUUID()) && MoldedPlasticCrateAccess.resolve(player, source) != null);
        }
    }
}
