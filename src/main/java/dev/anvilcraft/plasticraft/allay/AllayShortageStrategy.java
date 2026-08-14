package dev.anvilcraft.plasticraft.allay;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.function.IntFunction;

/** 缺料与缺拆除能力时的处理策略;权限拒绝不读取该配置,始终强制暂停。 */
public enum AllayShortageStrategy implements StringRepresentable {
    PAUSE("pause"),
    SKIP("skip");

    public static final Codec<AllayShortageStrategy> CODEC = StringRepresentable.fromEnum(AllayShortageStrategy::values);
    private static final IntFunction<AllayShortageStrategy> BY_ID =
        id -> id >= 0 && id < values().length ? values()[id] : PAUSE;
    public static final StreamCodec<ByteBuf, AllayShortageStrategy> STREAM_CODEC =
        ByteBufCodecs.idMapper(BY_ID, AllayShortageStrategy::ordinal);

    private final String name;

    AllayShortageStrategy(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
