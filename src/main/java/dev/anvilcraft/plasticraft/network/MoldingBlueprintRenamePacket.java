package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.molding.blueprint.BlueprintException;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintLibrary;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintSummary;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** 按显示名重命名共享蓝图文件,不改动既有库操作载荷。 */
public record MoldingBlueprintRenamePacket(
    BlockPos chamberPos,
    UUID sessionId,
    long baseRevision,
    String fileId,
    long fileRevision,
    String newName
) implements IServerboundPacket {
    public static final Type<MoldingBlueprintRenamePacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("molding_blueprint_rename")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldingBlueprintRenamePacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.chamberPos);
            buffer.writeUUID(packet.sessionId);
            buffer.writeVarLong(packet.baseRevision);
            buffer.writeUtf(packet.fileId, MoldingBlueprintSummary.MAX_FILE_ID_LENGTH);
            buffer.writeVarLong(packet.fileRevision);
            buffer.writeUtf(packet.newName, MoldingElement.MAX_NAME_LENGTH);
        },
        buffer -> new MoldingBlueprintRenamePacket(
            buffer.readBlockPos(),
            buffer.readUUID(),
            buffer.readVarLong(),
            buffer.readUtf(MoldingBlueprintSummary.MAX_FILE_ID_LENGTH),
            buffer.readVarLong(),
            buffer.readUtf(MoldingElement.MAX_NAME_LENGTH)
        )
    );

    @Override
    public Type<MoldingBlueprintRenamePacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)
            || MoldingPacketAccess.chamber(serverPlayer, this.chamberPos, this.sessionId) == null) {
            return;
        }
        try {
            MoldingBlueprintLibrary.StoredBlueprint renamed = MoldingBlueprintLibrary.rename(
                serverPlayer,
                this.fileId,
                this.fileRevision,
                this.newName
            );
            MoldingBlueprintResultPacket.send(serverPlayer, this.chamberPos, "blueprint_renamed", "");
            MoldingBlueprintListPacket.send(serverPlayer, this.chamberPos, renamed.fileId());
        } catch (BlueprintException exception) {
            MoldingBlueprintResultPacket.send(
                serverPlayer,
                this.chamberPos,
                exception.reason(),
                exception.getMessage()
            );
        }
    }
}
