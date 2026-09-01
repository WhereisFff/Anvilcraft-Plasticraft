package dev.anvilcraft.plasticraft.molding.product;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;

/** 支架红石元件及其建模格位置。 */
public record MoldedTrayComponentPlacement(
    MoldedTrayCell cell,
    MoldedTrayComponent component
) implements Comparable<MoldedTrayComponentPlacement> {
    public static final Codec<MoldedTrayComponentPlacement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        MoldedTrayCell.CODEC.fieldOf("cell").forGetter(MoldedTrayComponentPlacement::cell),
        MoldedTrayComponent.CODEC.fieldOf("component").forGetter(MoldedTrayComponentPlacement::component)
    ).apply(instance, MoldedTrayComponentPlacement::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldedTrayComponentPlacement> STREAM_CODEC = StreamCodec.of(
        MoldedTrayComponentPlacement::encode,
        MoldedTrayComponentPlacement::decode
    );

    public MoldedTrayComponentPlacement {
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(component, "component");
    }

    @Override
    public int compareTo(MoldedTrayComponentPlacement other) {
        return this.cell.compareTo(other.cell);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MoldedTrayComponentPlacement placement) {
        buffer.writeByte(placement.cell.index());
        MoldedTrayComponent.STREAM_CODEC.encode(buffer, placement.component);
    }

    private static MoldedTrayComponentPlacement decode(RegistryFriendlyByteBuf buffer) {
        MoldedTrayCell cell = MoldedTrayCell.fromIndex(buffer.readUnsignedByte());
        return new MoldedTrayComponentPlacement(cell, MoldedTrayComponent.STREAM_CODEC.decode(buffer));
    }
}
