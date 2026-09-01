package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPrinterMotion;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPrintingMotionPhase;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/** 只同步打印头的短时运动段，避免为逐像素动画反复发送完整方块实体 NBT。 */
public record MoldingPrinterMotionPacket(
    BlockPos chamberPos,
    MoldingPrintingMotionPhase phase,
    MoldingPrinterMotion motion,
    int completedProgress
) implements IClientboundPacket {
    public static final Type<MoldingPrinterMotionPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("molding_printer_motion")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldingPrinterMotionPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.chamberPos);
            buffer.writeEnum(packet.phase);
            writeVector(buffer, packet.motion.from());
            writeVector(buffer, packet.motion.to());
            buffer.writeVarLong(packet.motion.startGameTime());
            buffer.writeVarInt(packet.motion.durationTicks());
            buffer.writeVarInt(packet.completedProgress);
        },
        buffer -> new MoldingPrinterMotionPacket(
            buffer.readBlockPos(),
            buffer.readEnum(MoldingPrintingMotionPhase.class),
            new MoldingPrinterMotion(
                readVector(buffer),
                readVector(buffer),
                buffer.readVarLong(),
                buffer.readVarInt()
            ),
            buffer.readVarInt()
        )
    );

    @Override
    public Type<MoldingPrinterMotionPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnClient(Player player) {
        if (player.level().getBlockEntity(this.chamberPos) instanceof PlasticMoldingChamberBlockEntity chamber) {
            chamber.applyClientPrinterMotion(this.phase, this.motion, this.completedProgress);
        }
    }

    private static void writeVector(RegistryFriendlyByteBuf buffer, MoldingVec3 vector) {
        buffer.writeDouble(vector.x());
        buffer.writeDouble(vector.y());
        buffer.writeDouble(vector.z());
    }

    private static MoldingVec3 readVector(RegistryFriendlyByteBuf buffer) {
        return new MoldingVec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
}
