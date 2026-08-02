package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.molding.blueprint.BlueprintException;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprint;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintCodec;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintLibrary;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintLibraryAction;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintSummary;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/** 对选中共享文件执行置顶、复制、导出或删除。 */
public record MoldingBlueprintLibraryPacket(
    BlockPos chamberPos,
    UUID sessionId,
    long baseRevision,
    MoldingBlueprintLibraryAction action,
    String fileId,
    long fileRevision
) implements IServerboundPacket {
    public static final Type<MoldingBlueprintLibraryPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("molding_blueprint_library")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldingBlueprintLibraryPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.chamberPos);
            buffer.writeUUID(packet.sessionId);
            buffer.writeVarLong(packet.baseRevision);
            buffer.writeByte(packet.action.protocolId());
            buffer.writeUtf(packet.fileId, MoldingBlueprintSummary.MAX_FILE_ID_LENGTH);
            buffer.writeVarLong(packet.fileRevision);
        },
        buffer -> new MoldingBlueprintLibraryPacket(
            buffer.readBlockPos(),
            buffer.readUUID(),
            buffer.readVarLong(),
            MoldingBlueprintLibraryAction.fromProtocolId(buffer.readUnsignedByte()),
            buffer.readUtf(MoldingBlueprintSummary.MAX_FILE_ID_LENGTH),
            buffer.readVarLong()
        )
    );

    @Override
    public Type<MoldingBlueprintLibraryPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)
            || MoldingPacketAccess.chamber(serverPlayer, this.chamberPos, this.sessionId) == null) {
            return;
        }
        try {
            switch (this.action) {
                case PIN -> {
                    boolean pinned = MoldingBlueprintLibrary.togglePin(
                        serverPlayer,
                        this.fileId,
                        this.fileRevision
                    );
                    MoldingBlueprintResultPacket.send(
                        serverPlayer,
                        this.chamberPos,
                        pinned ? "blueprint_pinned" : "blueprint_unpinned",
                        ""
                    );
                    MoldingBlueprintListPacket.send(serverPlayer, this.chamberPos, this.fileId);
                }
                case COPY -> {
                    MoldingBlueprintLibrary.StoredBlueprint copy = MoldingBlueprintLibrary.copy(
                        serverPlayer,
                        this.fileId,
                        this.fileRevision
                    );
                    MoldingBlueprintResultPacket.send(serverPlayer, this.chamberPos, "blueprint_copied", "");
                    MoldingBlueprintListPacket.send(serverPlayer, this.chamberPos, copy.fileId());
                }
                case OPEN -> sendOpen(serverPlayer, true);
                case REFRESH -> {
                    sendOpen(serverPlayer, false);
                    MoldingBlueprintListPacket.send(serverPlayer, this.chamberPos, "");
                }
                case DELETE -> {
                    MoldingBlueprintLibrary.delete(serverPlayer, this.fileId, this.fileRevision);
                    MoldingBlueprintResultPacket.send(serverPlayer, this.chamberPos, "blueprint_deleted", "");
                    MoldingBlueprintListPacket.send(serverPlayer, this.chamberPos, "");
                }
            }
        } catch (BlueprintException exception) {
            MoldingBlueprintResultPacket.send(
                serverPlayer,
                this.chamberPos,
                exception.reason(),
                exception.getMessage()
            );
        }
    }

    private void sendOpen(ServerPlayer player, boolean openFolder) throws BlueprintException {
        MinecraftServer server = player.getServer();
        if (server == null) throw new BlueprintException("server_unavailable", "Server is unavailable");
        String text = "";
        if (!this.fileId.isEmpty()) {
            MoldingBlueprint blueprint = MoldingBlueprintLibrary.read(server, this.fileId, this.fileRevision);
            text = MoldingBlueprintCodec.encode(blueprint);
        }
        PacketDistributor.sendToPlayer(
            player,
            new MoldingBlueprintOpenPacket(
                this.chamberPos,
                this.sessionId,
                this.fileId,
                text,
                server.isSingleplayer(),
                openFolder
            )
        );
    }
}
