package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.inventory.PlasticMoldingChamberMenu;
import dev.anvilcraft.plasticraft.molding.blueprint.BlueprintException;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintLibrary;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintSummary;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** 同步至多 512 条共享蓝图摘要，不传服务器路径。 */
public record MoldingBlueprintListPacket(
    BlockPos chamberPos,
    List<MoldingBlueprintSummary> blueprints,
    String selectedFileId
) implements IClientboundPacket {
    public static final Type<MoldingBlueprintListPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("molding_blueprint_list")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldingBlueprintListPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.chamberPos);
            buffer.writeVarInt(packet.blueprints.size());
            for (MoldingBlueprintSummary summary : packet.blueprints) summary.write(buffer);
            buffer.writeUtf(packet.selectedFileId, MoldingBlueprintSummary.MAX_FILE_ID_LENGTH);
        },
        buffer -> {
            BlockPos pos = buffer.readBlockPos();
            int count = buffer.readVarInt();
            if (count < 0 || count > MoldingBlueprintLibrary.MAX_LIBRARY_FILES) {
                throw new IllegalArgumentException("Invalid number of shared blueprints");
            }
            List<MoldingBlueprintSummary> summaries = new ArrayList<>(count);
            for (int index = 0; index < count; index++) summaries.add(MoldingBlueprintSummary.read(buffer));
            return new MoldingBlueprintListPacket(
                pos,
                summaries,
                buffer.readUtf(MoldingBlueprintSummary.MAX_FILE_ID_LENGTH)
            );
        }
    );

    public MoldingBlueprintListPacket {
        blueprints = List.copyOf(blueprints);
    }

    public static void send(ServerPlayer player, BlockPos chamberPos, String selectedFileId) {
        try {
            PacketDistributor.sendToPlayer(
                player,
                new MoldingBlueprintListPacket(chamberPos, MoldingBlueprintLibrary.list(player), selectedFileId)
            );
        } catch (BlueprintException exception) {
            MoldingBlueprintResultPacket.send(player, chamberPos, exception.reason(), exception.getMessage());
        }
    }

    @Override
    public Type<MoldingBlueprintListPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnClient(Player player) {
        if (player.containerMenu instanceof PlasticMoldingChamberMenu menu
            && menu.chamberPos().equals(this.chamberPos)) {
            menu.applyBlueprintList(this.blueprints, this.selectedFileId);
        }
    }
}
