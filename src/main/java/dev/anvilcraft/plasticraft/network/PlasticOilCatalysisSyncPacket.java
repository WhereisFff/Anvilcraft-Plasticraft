package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.renderer.PlasticOilCatalysisRenderer;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/** 将指定反应位置的催化进度同步给客户端渲染器。 */
public record PlasticOilCatalysisSyncPacket(
    BlockPos pos,
    float progress,
    float progressPerTick,
    long serverGameTime,
    int mode
) implements IClientboundPacket {
    public static final int UPDATE = 0;
    public static final int COMPLETE = 1;
    public static final int CLEAR = 2;

    public static final Type<PlasticOilCatalysisSyncPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("plastic_oil_catalysis_sync")
    );
    public static final StreamCodec<ByteBuf, PlasticOilCatalysisSyncPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        PlasticOilCatalysisSyncPacket::pos,
        ByteBufCodecs.FLOAT,
        PlasticOilCatalysisSyncPacket::progress,
        ByteBufCodecs.FLOAT,
        PlasticOilCatalysisSyncPacket::progressPerTick,
        ByteBufCodecs.VAR_LONG,
        PlasticOilCatalysisSyncPacket::serverGameTime,
        ByteBufCodecs.VAR_INT,
        PlasticOilCatalysisSyncPacket::mode,
        PlasticOilCatalysisSyncPacket::new
    );

    @Override
    public Type<PlasticOilCatalysisSyncPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnClient(Player player) {
        PlasticOilCatalysisRenderer.handleSync(
            player.level(),
            this.pos,
            this.progress,
            this.progressPerTick,
            this.serverGameTime,
            this.mode
        );
    }
}
