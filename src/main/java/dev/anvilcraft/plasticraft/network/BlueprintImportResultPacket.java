package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintException;
import dev.anvilcraft.plasticraft.blueprint.StructureSnapshotCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 蓝图操作(导入、部署、取消、启动)的结果回报:reason 是稳定翻译键后缀,
 * detail 为原样附加说明,warnings 为不阻断导入的警告;客户端拼装为聊天消息。
 */
public record BlueprintImportResultPacket(
    boolean success,
    String reason,
    String detail,
    List<Warning> warnings
) implements IClientboundPacket {
    /** 单条警告;与解析层的警告一一对应。 */
    public record Warning(String reason, String detail) {
    }

    public static final Type<BlueprintImportResultPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("blueprint_import_result")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintImportResultPacket> STREAM_CODEC =
        StreamCodec.of(BlueprintImportResultPacket::write, BlueprintImportResultPacket::read);

    private static void write(RegistryFriendlyByteBuf buffer, BlueprintImportResultPacket packet) {
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.reason);
        buffer.writeUtf(packet.detail);
        buffer.writeVarInt(packet.warnings.size());
        for (Warning warning : packet.warnings) {
            buffer.writeUtf(warning.reason());
            buffer.writeUtf(warning.detail());
        }
    }

    private static BlueprintImportResultPacket read(RegistryFriendlyByteBuf buffer) {
        boolean success = buffer.readBoolean();
        String reason = buffer.readUtf();
        String detail = buffer.readUtf();
        int warningCount = buffer.readVarInt();
        List<Warning> warnings = new ArrayList<>(warningCount);
        for (int index = 0; index < warningCount; index++) {
            warnings.add(new Warning(buffer.readUtf(), buffer.readUtf()));
        }
        return new BlueprintImportResultPacket(success, reason, detail, warnings);
    }

    public static void sendSuccess(ServerPlayer player, String reason, String detail) {
        PacketDistributor.sendToPlayer(player, new BlueprintImportResultPacket(true, reason, detail, List.of()));
    }

    public static void sendSuccess(
        ServerPlayer player,
        String reason,
        String detail,
        List<StructureSnapshotCodec.BlueprintWarning> warnings
    ) {
        List<Warning> converted = warnings.stream()
            .map(warning -> new Warning(warning.reason(), warning.detail()))
            .toList();
        PacketDistributor.sendToPlayer(player, new BlueprintImportResultPacket(true, reason, detail, converted));
    }

    public static void sendFailure(ServerPlayer player, ConstructionBlueprintException exception) {
        PacketDistributor.sendToPlayer(player, new BlueprintImportResultPacket(
            false,
            exception.reason(),
            exception.detail(),
            List.of()
        ));
    }

    @Override
    public Type<BlueprintImportResultPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnClient(Player player) {
        MutableComponent message = Component.translatable(
            "message.anvilcraftplasticraft.blueprint." + this.reason
        );
        if (!this.detail.isBlank()) {
            message = message.append(Component.literal(": " + this.detail));
        }
        message = message.withStyle(this.success ? ChatFormatting.GREEN : ChatFormatting.RED);
        player.displayClientMessage(message, false);
        for (Warning warning : this.warnings) {
            MutableComponent warningMessage = Component
                .translatable("message.anvilcraftplasticraft.blueprint.warning."
                    + warning.reason())
                .withStyle(ChatFormatting.YELLOW);
            if (!warning.detail().isBlank()) {
                warningMessage = warningMessage.append(Component.literal(": " + warning.detail()));
            }
            player.displayClientMessage(warningMessage, false);
        }
    }
}
