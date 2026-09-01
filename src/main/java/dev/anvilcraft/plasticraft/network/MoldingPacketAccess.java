package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.inventory.PlasticMoldingChamberMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** 集中执行成型舱载荷共用的菜单、距离、位置和区块检查。 */
final class MoldingPacketAccess {
    private MoldingPacketAccess() {
    }

    static @Nullable PlasticMoldingChamberBlockEntity chamber(
        ServerPlayer player,
        BlockPos pos,
        UUID sessionId
    ) {
        if (!(player.containerMenu instanceof PlasticMoldingChamberMenu menu)
            || !menu.chamberPos().equals(pos)
            || !menu.sessionId().equals(sessionId)
            || player.distanceToSqr(pos.getCenter()) > 64.0D
            || !player.level().isLoaded(pos)) {
            return null;
        }
        return player.level().getBlockEntity(pos) instanceof PlasticMoldingChamberBlockEntity chamber
            ? chamber
            : null;
    }
}
