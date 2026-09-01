package dev.anvilcraft.plasticraft.allay;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.function.IntFunction;

/** 蓝图范围内空白格的清场策略;流体封堵留下的填充块属于施工自身产物,不受该配置影响。 */
public enum AllayClearanceStrategy implements StringRepresentable {
    CLEAR_AREA("clear_area"),
    KEEP_BLANK("keep_blank");

    public static final Codec<AllayClearanceStrategy> CODEC = StringRepresentable.fromEnum(AllayClearanceStrategy::values);
    private static final IntFunction<AllayClearanceStrategy> BY_ID =
        id -> id >= 0 && id < values().length ? values()[id] : CLEAR_AREA;
    public static final StreamCodec<ByteBuf, AllayClearanceStrategy> STREAM_CODEC =
        ByteBufCodecs.idMapper(BY_ID, AllayClearanceStrategy::ordinal);

    private final String name;

    AllayClearanceStrategy(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
