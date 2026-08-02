package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.molding.blueprint.BlueprintException;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintDiskAction;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintService;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintSummary;
import dev.anvilcraft.plasticraft.molding.session.MoldingSessionSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/** 加载磁盘、存入磁盘和共享行写磁盘的统一有界载荷。 */
public record MoldingBlueprintDiskPacket(
    BlockPos chamberPos,
    UUID sessionId,
    long baseRevision,
    MoldingBlueprintDiskAction action,
    String fileId,
    long fileRevision,
    String expectedDiskToken,
    boolean overwriteConfirmed
) implements IServerboundPacket {
    public static final int MAX_DISK_TOKEN_LENGTH = 72;
    public static final Type<MoldingBlueprintDiskPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("molding_blueprint_disk")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldingBlueprintDiskPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.chamberPos);
            buffer.writeUUID(packet.sessionId);
            buffer.writeVarLong(packet.baseRevision);
            buffer.writeByte(packet.action.protocolId());
            buffer.writeUtf(packet.fileId, MoldingBlueprintSummary.MAX_FILE_ID_LENGTH);
            buffer.writeVarLong(packet.fileRevision);
            buffer.writeUtf(packet.expectedDiskToken, MAX_DISK_TOKEN_LENGTH);
            buffer.writeBoolean(packet.overwriteConfirmed);
        },
        buffer -> new MoldingBlueprintDiskPacket(
            buffer.readBlockPos(),
            buffer.readUUID(),
            buffer.readVarLong(),
            MoldingBlueprintDiskAction.fromProtocolId(buffer.readUnsignedByte()),
            buffer.readUtf(MoldingBlueprintSummary.MAX_FILE_ID_LENGTH),
            buffer.readVarLong(),
            buffer.readUtf(MAX_DISK_TOKEN_LENGTH),
            buffer.readBoolean()
        )
    );

    @Override
    public Type<MoldingBlueprintDiskPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        PlasticMoldingChamberBlockEntity chamber = MoldingPacketAccess.chamber(
            serverPlayer,
            this.chamberPos,
            this.sessionId
        );
        if (chamber == null) return;
        try {
            MoldingBlueprintService.OperationResult result = switch (this.action) {
                case LOAD -> MoldingBlueprintService.loadDiskModel(
                    serverPlayer,
                    chamber,
                    this.sessionId,
                    this.baseRevision,
                    this.expectedDiskToken,
                    this.overwriteConfirmed
                );
                case STORE -> MoldingBlueprintService.storeModel(
                    serverPlayer,
                    chamber,
                    this.sessionId,
                    this.baseRevision,
                    this.expectedDiskToken,
                    this.overwriteConfirmed
                );
                case WRITE_SHARED -> MoldingBlueprintService.writeSharedModelToDisk(
                    serverPlayer,
                    chamber,
                    this.sessionId,
                    this.baseRevision,
                    this.fileId,
                    this.fileRevision,
                    this.expectedDiskToken,
                    this.overwriteConfirmed
                );
            };
            String success = switch (this.action) {
                case LOAD -> "blueprint_loaded";
                case STORE -> "blueprint_saved";
                case WRITE_SHARED -> "blueprint_disk_written";
            };
            MoldingBlueprintResultPacket.send(serverPlayer, this.chamberPos, success, "");
            if (this.action != MoldingBlueprintDiskAction.LOAD) {
                MoldingBlueprintListPacket.send(serverPlayer, this.chamberPos, result.selectedFileId());
            }
        } catch (BlueprintException exception) {
            MoldingBlueprintResultPacket.send(
                serverPlayer,
                this.chamberPos,
                exception.reason(),
                exception.getMessage()
            );
        }
        MoldingSessionSnapshot snapshot = chamber.sessionSnapshot(serverPlayer, this.sessionId);
        PacketDistributor.sendToPlayer(
            serverPlayer,
            MoldingSessionSnapshotPacket.of(this.chamberPos, snapshot, "")
        );
        serverPlayer.containerMenu.broadcastChanges();
    }
}
