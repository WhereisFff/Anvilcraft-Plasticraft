package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.molding.blueprint.MoldingBlueprintClientFiles;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintCodec;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintSummary;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** 本地世界只定位权威文件，远程服务器则下发只读规范副本。 */
public record MoldingBlueprintOpenPacket(
    BlockPos chamberPos,
    UUID sessionId,
    String fileId,
    String text,
    boolean localAuthority,
    boolean openFolder
) implements IClientboundPacket {
    public static final Type<MoldingBlueprintOpenPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("molding_blueprint_open")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldingBlueprintOpenPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.chamberPos);
            buffer.writeUUID(packet.sessionId);
            buffer.writeUtf(packet.fileId, MoldingBlueprintSummary.MAX_FILE_ID_LENGTH);
            buffer.writeUtf(packet.text, MoldingBlueprintCodec.MAX_TEXT_BYTES);
            buffer.writeBoolean(packet.localAuthority);
            buffer.writeBoolean(packet.openFolder);
        },
        buffer -> new MoldingBlueprintOpenPacket(
            buffer.readBlockPos(),
            buffer.readUUID(),
            buffer.readUtf(MoldingBlueprintSummary.MAX_FILE_ID_LENGTH),
            buffer.readUtf(MoldingBlueprintCodec.MAX_TEXT_BYTES),
            buffer.readBoolean(),
            buffer.readBoolean()
        )
    );

    @Override
    public Type<MoldingBlueprintOpenPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnClient(Player player) {
        MoldingBlueprintClientFiles.open(
            player,
            this.chamberPos,
            this.sessionId,
            this.fileId,
            this.text,
            this.localAuthority,
            this.openFolder
        );
    }
}
