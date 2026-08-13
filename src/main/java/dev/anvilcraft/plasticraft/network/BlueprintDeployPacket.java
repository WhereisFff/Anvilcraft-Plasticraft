package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintException;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * 部署会话确认:携带手持槽位与最终放置参数。锚点必须在玩家附近,
 * 其余校验(磁盘内容、所有者、结构存在)由服务层完成并回报结果。
 */
public record BlueprintDeployPacket(
    InteractionHand hand,
    BlockPos anchor,
    Rotation rotation,
    Mirror mirror
) implements IServerboundPacket {
    /** 锚点到玩家的最大距离(格),限制远程部署滥用。 */
    private static final double MAX_ANCHOR_DISTANCE = 64.0D;

    public static final Type<BlueprintDeployPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("blueprint_deploy")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintDeployPacket> STREAM_CODEC = StreamCodec.of(
        BlueprintDeployPacket::write,
        BlueprintDeployPacket::read
    );

    private static void write(RegistryFriendlyByteBuf buffer, BlueprintDeployPacket packet) {
        buffer.writeBoolean(packet.hand == InteractionHand.MAIN_HAND);
        buffer.writeBlockPos(packet.anchor);
        buffer.writeVarInt(packet.rotation.ordinal());
        buffer.writeVarInt(packet.mirror.ordinal());
    }

    private static BlueprintDeployPacket read(RegistryFriendlyByteBuf buffer) {
        return new BlueprintDeployPacket(
            buffer.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
            buffer.readBlockPos(),
            Rotation.values()[buffer.readVarInt() % Rotation.values().length],
            Mirror.values()[buffer.readVarInt() % Mirror.values().length]
        );
    }

    @Override
    public Type<BlueprintDeployPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!this.anchor.closerToCenterThan(serverPlayer.position(), MAX_ANCHOR_DISTANCE)) return;
        try {
            ConstructionBlueprintService.deploy(serverPlayer, this.hand, this.anchor, this.rotation, this.mirror);
            BlueprintImportResultPacket.sendSuccess(serverPlayer, "deployed", "");
        } catch (ConstructionBlueprintException exception) {
            BlueprintImportResultPacket.sendFailure(serverPlayer, exception);
        }
    }
}
