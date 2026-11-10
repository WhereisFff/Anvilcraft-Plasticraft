package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.dubhe.anvilcraft.client.gui.screen.StorageScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/** 使用服务端授予的仓储来源打开本体板条箱界面。 */
public record MoldedPlasticCrateOpenPacket(BlockPos source, Component title) implements IClientboundPacket {
    public static final Type<MoldedPlasticCrateOpenPacket> TYPE = IPacket.type(AnvilcraftPlasticraft.of("molded_crate_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldedPlasticCrateOpenPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, MoldedPlasticCrateOpenPacket::source,
        ComponentSerialization.STREAM_CODEC, MoldedPlasticCrateOpenPacket::title,
        MoldedPlasticCrateOpenPacket::new
    );

    @Override
    public Type<MoldedPlasticCrateOpenPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnClient(Player player) {
        StorageScreen.openScreen(this.source, this.title);
    }
}
