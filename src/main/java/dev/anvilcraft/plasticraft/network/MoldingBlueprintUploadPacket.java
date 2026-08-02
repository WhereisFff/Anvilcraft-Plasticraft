package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.molding.blueprint.BlueprintException;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprint;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintCodec;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintImporter;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintLibrary;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;

/** 仅接收白名单文本格式的有界上传载荷，不接收客户端路径。 */
public record MoldingBlueprintUploadPacket(
    BlockPos chamberPos,
    UUID sessionId,
    String filename,
    String text,
    boolean translationConfirmed,
    double translationX,
    double translationY,
    double translationZ,
    long sourceSize,
    long sourceModifiedAt
) implements IServerboundPacket {
    public static final Type<MoldingBlueprintUploadPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("molding_blueprint_upload")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldingBlueprintUploadPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.chamberPos);
            buffer.writeUUID(packet.sessionId);
            buffer.writeUtf(packet.filename, MoldingBlueprintImporter.MAX_UPLOAD_NAME_LENGTH);
            buffer.writeUtf(packet.text, MoldingBlueprintCodec.MAX_TEXT_BYTES);
            buffer.writeBoolean(packet.translationConfirmed);
            buffer.writeDouble(packet.translationX);
            buffer.writeDouble(packet.translationY);
            buffer.writeDouble(packet.translationZ);
            buffer.writeVarLong(packet.sourceSize);
            buffer.writeVarLong(packet.sourceModifiedAt);
        },
        buffer -> new MoldingBlueprintUploadPacket(
            buffer.readBlockPos(),
            buffer.readUUID(),
            buffer.readUtf(MoldingBlueprintImporter.MAX_UPLOAD_NAME_LENGTH),
            buffer.readUtf(MoldingBlueprintCodec.MAX_TEXT_BYTES),
            buffer.readBoolean(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readVarLong(),
            buffer.readVarLong()
        )
    );

    @Override
    public Type<MoldingBlueprintUploadPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)
            || MoldingPacketAccess.chamber(serverPlayer, this.chamberPos, this.sessionId) == null) {
            return;
        }
        try {
            MoldingBlueprintImporter.ImportPreview preview = MoldingBlueprintImporter.inspect(this.filename, this.text);
            EditableMoldingModel model = MoldingBlueprintImporter.finish(
                preview,
                this.translationConfirmed,
                new MoldingVec3(this.translationX, this.translationY, this.translationZ)
            );
            MinecraftServer server = player.getServer();
            if (server == null) throw new BlueprintException("server_unavailable", "Server is unavailable");
            MoldingBlueprint blueprint = MoldingBlueprint.create(
                model,
                player.getUUID(),
                player.getGameProfile().getName(),
                System.currentTimeMillis()
            );
            Optional<MoldingBlueprintLibrary.StoredBlueprint> existing = MoldingBlueprintLibrary.findOwnedModel(
                server,
                player.getUUID(),
                blueprint.name(),
                blueprint.modelHash()
            );
            MoldingBlueprintLibrary.StoredBlueprint stored = existing.isPresent()
                ? existing.orElseThrow()
                : MoldingBlueprintLibrary.saveNew(server, blueprint);
            MoldingBlueprintResultPacket.send(serverPlayer, this.chamberPos, "blueprint_uploaded", "");
            MoldingBlueprintListPacket.send(serverPlayer, this.chamberPos, stored.fileId());
            MoldingBlueprintUploadResultPacket.send(
                serverPlayer,
                this.chamberPos,
                this.sessionId,
                this.filename,
                this.sourceSize,
                this.sourceModifiedAt,
                true
            );
        } catch (BlueprintException | IllegalArgumentException exception) {
            String reason = exception instanceof BlueprintException blueprintException
                ? blueprintException.reason()
                : "invalid_import";
            MoldingBlueprintResultPacket.send(
                serverPlayer,
                this.chamberPos,
                reason,
                exception.getMessage()
            );
            MoldingBlueprintUploadResultPacket.send(
                serverPlayer,
                this.chamberPos,
                this.sessionId,
                this.filename,
                this.sourceSize,
                this.sourceModifiedAt,
                false
            );
        }
    }
}
