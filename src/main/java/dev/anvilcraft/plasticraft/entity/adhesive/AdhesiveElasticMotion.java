package dev.anvilcraft.plasticraft.entity.adhesive;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/** 记录胶着实体受击后围绕原粘合位置振荡所需的同步状态。 */
public record AdhesiveElasticMotion(
    Vec3 anchor,
    long startedGameTime,
    int durationTicks,
    boolean originalNoGravity
) {
    public static final Codec<AdhesiveElasticMotion> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Vec3.CODEC.fieldOf("anchor").forGetter(AdhesiveElasticMotion::anchor),
        Codec.LONG.fieldOf("started_game_time").forGetter(AdhesiveElasticMotion::startedGameTime),
        Codec.INT.optionalFieldOf("duration_ticks", 10).forGetter(AdhesiveElasticMotion::durationTicks),
        Codec.BOOL.optionalFieldOf("original_no_gravity", false)
            .forGetter(AdhesiveElasticMotion::originalNoGravity)
    ).apply(instance, AdhesiveElasticMotion::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdhesiveElasticMotion> STREAM_CODEC = StreamCodec.of(
        (buffer, motion) -> {
            buffer.writeDouble(motion.anchor.x);
            buffer.writeDouble(motion.anchor.y);
            buffer.writeDouble(motion.anchor.z);
            buffer.writeLong(motion.startedGameTime);
            buffer.writeVarInt(motion.durationTicks);
            buffer.writeBoolean(motion.originalNoGravity);
        },
        buffer -> new AdhesiveElasticMotion(
            new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
            buffer.readLong(),
            buffer.readVarInt(),
            buffer.readBoolean()
        )
    );

    public AdhesiveElasticMotion {
        Objects.requireNonNull(anchor, "anchor");
        if (!Double.isFinite(anchor.x) || !Double.isFinite(anchor.y) || !Double.isFinite(anchor.z)) {
            throw new IllegalArgumentException("Elastic anchor must be finite");
        }
        durationTicks = Math.clamp(durationTicks, 4, 40);
    }

    public AdhesiveElasticMotion restarted(long gameTime, int duration) {
        return new AdhesiveElasticMotion(this.anchor, gameTime, duration, this.originalNoGravity);
    }
}
