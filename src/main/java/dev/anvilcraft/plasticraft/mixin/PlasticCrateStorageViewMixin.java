package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.molding.product.storage.MoldedPlasticCrateAccess;
import dev.dubhe.anvilcraft.block.container.storage.CrateBlock;
import dev.dubhe.anvilcraft.block.entity.storage.CrateBlockEntity;
import dev.dubhe.anvilcraft.rpc.StorageServerStub;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.UUID;

@Mixin(value = StorageServerStub.class, remap = false)
abstract class PlasticCrateStorageViewMixin {
    @Redirect(
        method = "getView(Lnet/minecraft/core/HolderLookup$Provider;Ljava/util/UUID;J)"
            + "Ldev/dubhe/anvilcraft/rpc/StorageServerStub$StorageView;",
        at = @At(value = "INVOKE",
            target = "Ldev/dubhe/anvilcraft/block/container/storage/CrateBlock;getNearbyCrates"
                + "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Ljava/util/List;")
    )
    private static List<CrateBlockEntity> plasticraft$nearbyCrates(
        Level level, BlockPos pos, HolderLookup.Provider registries, UUID playerId, long source
    ) {
        if (MoldedPlasticCrateAccess.isSource(source) && level instanceof ServerLevel serverLevel) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
            return player == null ? List.of() : MoldedPlasticCrateAccess.nearby(player, source);
        }
        return CrateBlock.getNearbyCrates(level, pos);
    }

    @Redirect(
        method = "getView(Lnet/minecraft/core/HolderLookup$Provider;Ljava/util/UUID;J)"
            + "Ldev/dubhe/anvilcraft/rpc/StorageServerStub$StorageView;",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)"
                + "Lnet/minecraft/world/level/block/entity/BlockEntity;", remap = true)
    )
    private static @Nullable BlockEntity plasticraft$crateSource(
        Level level, BlockPos pos, HolderLookup.Provider registries, UUID playerId, long source
    ) {
        if (MoldedPlasticCrateAccess.isSource(source) && level instanceof ServerLevel serverLevel) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
            return player == null ? null : MoldedPlasticCrateAccess.resolve(player, source);
        }
        return level.getBlockEntity(pos);
    }
}
