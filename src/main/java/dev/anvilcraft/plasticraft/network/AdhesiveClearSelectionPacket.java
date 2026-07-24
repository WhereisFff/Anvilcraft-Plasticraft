package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveSelectionManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/** 请求服务端清除当前树脂桶选中的实体。 */
public record AdhesiveClearSelectionPacket() implements IServerboundPacket {
    public static final Type<AdhesiveClearSelectionPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("adhesive_clear_selection")
    );
    public static final StreamCodec<ByteBuf, AdhesiveClearSelectionPacket> STREAM_CODEC = StreamCodec.unit(
        new AdhesiveClearSelectionPacket()
    );

    @Override
    public Type<AdhesiveClearSelectionPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        AdhesiveSelectionManager.clear(player);
    }
}
