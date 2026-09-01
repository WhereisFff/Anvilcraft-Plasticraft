package dev.anvilcraft.plasticraft.entity.adhesive;

import dev.anvilcraft.plasticraft.block.BlockAdhesionState;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 同步滑动方块实体内部需要随动画移动的胶面。 */
public record SlidingAdhesionData(List<Part> parts) {
    public static final StreamCodec<RegistryFriendlyByteBuf, SlidingAdhesionData> STREAM_CODEC = StreamCodec.of(
        (buffer, data) -> {
            buffer.writeVarInt(data.parts.size());
            for (Part part : data.parts) {
                BlockPos.STREAM_CODEC.encode(buffer, part.relativePos);
                ResourceLocation.STREAM_CODEC.encode(buffer, part.state.blockId());
                buffer.writeByte(part.state.patchMask());
                buffer.writeByte(part.state.blockBondMask());
                buffer.writeByte(part.state.entityBondMask());
                buffer.writeByte(part.state.invisibleMask());
            }
        },
        buffer -> {
            int size = Math.clamp(buffer.readVarInt(), 0, 512);
            List<Part> parts = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                BlockPos relativePos = BlockPos.STREAM_CODEC.decode(buffer);
                BlockAdhesionState state = new BlockAdhesionState(
                    ResourceLocation.STREAM_CODEC.decode(buffer),
                    buffer.readUnsignedByte(),
                    buffer.readUnsignedByte(),
                    buffer.readUnsignedByte(),
                    buffer.readUnsignedByte()
                );
                parts.add(new Part(relativePos, state));
            }
            return new SlidingAdhesionData(parts);
        }
    );

    public SlidingAdhesionData {
        parts = List.copyOf(parts);
    }

    public static SlidingAdhesionData empty() {
        return new SlidingAdhesionData(List.of());
    }

    public static SlidingAdhesionData from(
        BondedFallingBlocks.MovementSnapshot snapshot,
        BlockPos origin
    ) {
        List<Part> parts = new ArrayList<>(snapshot.adhesions().size());
        for (Map.Entry<BlockPos, BlockAdhesionState> entry : snapshot.adhesions().entrySet()) {
            parts.add(new Part(entry.getKey().subtract(origin), entry.getValue()));
        }
        return new SlidingAdhesionData(parts);
    }

    public record Part(BlockPos relativePos, BlockAdhesionState state) {
        public Part {
            relativePos = relativePos.immutable();
        }
    }
}
