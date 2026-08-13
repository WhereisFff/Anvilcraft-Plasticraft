package dev.anvilcraft.plasticraft.drone;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.function.IntFunction;

/** 缺料与缺拆除能力时的处理策略;权限拒绝不读取该配置,始终强制暂停。 */
public enum DroneShortageStrategy implements StringRepresentable {
    PAUSE("pause"),
    SKIP("skip");

    public static final Codec<DroneShortageStrategy> CODEC = StringRepresentable.fromEnum(DroneShortageStrategy::values);
    private static final IntFunction<DroneShortageStrategy> BY_ID =
        id -> id >= 0 && id < values().length ? values()[id] : PAUSE;
    public static final StreamCodec<ByteBuf, DroneShortageStrategy> STREAM_CODEC =
        ByteBufCodecs.idMapper(BY_ID, DroneShortageStrategy::ordinal);

    private final String name;

    DroneShortageStrategy(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
