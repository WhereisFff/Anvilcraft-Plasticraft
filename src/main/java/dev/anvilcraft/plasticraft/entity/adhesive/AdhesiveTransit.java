package dev.anvilcraft.plasticraft.entity.adhesive;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 服务端和客户端共用的树脂牵引路径及计时信息。 */
public record AdhesiveTransit(
    BlockPos supportPos,
    Direction attachmentFace,
    ResourceLocation supportBlockId,
    Direction sourceFace,
    Optional<UUID> supportEntityUuid,
    int supportEntityId,
    Vec3 supportEntityStartPosition,
    List<Vec3> path,
    long startGameTime,
    int durationTicks,
    boolean originalNoGravity,
    boolean plastic,
    byte startOrientation,
    byte targetOrientation
) {
    private static final int MAX_PATH_POINTS = 256;

    public static final Codec<AdhesiveTransit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BlockPos.CODEC.fieldOf("support_pos").forGetter(AdhesiveTransit::supportPos),
        Direction.CODEC.fieldOf("attachment_face").forGetter(AdhesiveTransit::attachmentFace),
        ResourceLocation.CODEC.fieldOf("support_block").forGetter(AdhesiveTransit::supportBlockId),
        Direction.CODEC.optionalFieldOf("source_face", Direction.DOWN).forGetter(AdhesiveTransit::sourceFace),
        UUIDUtil.CODEC.optionalFieldOf("support_entity").forGetter(AdhesiveTransit::supportEntityUuid),
        Codec.INT.optionalFieldOf("support_entity_id", -1).forGetter(AdhesiveTransit::supportEntityId),
        Vec3.CODEC.optionalFieldOf("support_entity_start", Vec3.ZERO)
            .forGetter(AdhesiveTransit::supportEntityStartPosition),
        Vec3.CODEC.listOf(2, MAX_PATH_POINTS).fieldOf("path").forGetter(AdhesiveTransit::path),
        Codec.LONG.fieldOf("start_game_time").forGetter(AdhesiveTransit::startGameTime),
        Codec.intRange(1, 40).fieldOf("duration_ticks").forGetter(AdhesiveTransit::durationTicks),
        Codec.BOOL.fieldOf("original_no_gravity").forGetter(AdhesiveTransit::originalNoGravity),
        Codec.BOOL.fieldOf("plastic").forGetter(AdhesiveTransit::plastic),
        Codec.BYTE.fieldOf("start_orientation").forGetter(AdhesiveTransit::startOrientation),
        Codec.BYTE.fieldOf("target_orientation").forGetter(AdhesiveTransit::targetOrientation)
    ).apply(instance, AdhesiveTransit::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdhesiveTransit> STREAM_CODEC = StreamCodec.of(
        (buffer, transit) -> {
            BlockPos.STREAM_CODEC.encode(buffer, transit.supportPos);
            buffer.writeByte(transit.attachmentFace.get3DDataValue());
            ResourceLocation.STREAM_CODEC.encode(buffer, transit.supportBlockId);
            buffer.writeByte(transit.sourceFace.get3DDataValue());
            buffer.writeBoolean(transit.supportEntityUuid.isPresent());
            transit.supportEntityUuid.ifPresent(buffer::writeUUID);
            buffer.writeVarInt(transit.supportEntityId);
            buffer.writeDouble(transit.supportEntityStartPosition.x);
            buffer.writeDouble(transit.supportEntityStartPosition.y);
            buffer.writeDouble(transit.supportEntityStartPosition.z);
            buffer.writeVarInt(transit.path.size());
            for (Vec3 point : transit.path) {
                buffer.writeDouble(point.x);
                buffer.writeDouble(point.y);
                buffer.writeDouble(point.z);
            }
            buffer.writeVarLong(transit.startGameTime);
            buffer.writeVarInt(transit.durationTicks);
            buffer.writeBoolean(transit.originalNoGravity);
            buffer.writeBoolean(transit.plastic);
            buffer.writeByte(transit.startOrientation);
            buffer.writeByte(transit.targetOrientation);
        },
        buffer -> {
            BlockPos supportPos = BlockPos.STREAM_CODEC.decode(buffer);
            Direction attachmentFace = Direction.from3DDataValue(buffer.readUnsignedByte());
            ResourceLocation supportBlockId = ResourceLocation.STREAM_CODEC.decode(buffer);
            Direction sourceFace = Direction.from3DDataValue(buffer.readUnsignedByte());
            Optional<UUID> supportEntityUuid = buffer.readBoolean()
                ? Optional.of(buffer.readUUID())
                : Optional.empty();
            int supportEntityId = buffer.readVarInt();
            Vec3 supportEntityStartPosition = new Vec3(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
            );
            int pointCount = Math.clamp(buffer.readVarInt(), 2, MAX_PATH_POINTS);
            List<Vec3> path = new java.util.ArrayList<>(pointCount);
            for (int i = 0; i < pointCount; i++) {
                path.add(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
            }
            return new AdhesiveTransit(
                supportPos,
                attachmentFace,
                supportBlockId,
                sourceFace,
                supportEntityUuid,
                supportEntityId,
                supportEntityStartPosition,
                path,
                buffer.readVarLong(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readByte(),
                buffer.readByte()
            );
        }
    );

    public AdhesiveTransit {
        supportPos = Objects.requireNonNull(supportPos, "supportPos").immutable();
        Objects.requireNonNull(attachmentFace, "attachmentFace");
        Objects.requireNonNull(supportBlockId, "supportBlockId");
        Objects.requireNonNull(sourceFace, "sourceFace");
        supportEntityUuid = Objects.requireNonNull(supportEntityUuid, "supportEntityUuid");
        Objects.requireNonNull(supportEntityStartPosition, "supportEntityStartPosition");
        if (!Double.isFinite(supportEntityStartPosition.x)
            || !Double.isFinite(supportEntityStartPosition.y)
            || !Double.isFinite(supportEntityStartPosition.z)) {
            throw new IllegalArgumentException("Support entity start position must be finite");
        }
        path = List.copyOf(path);
        if (path.size() < 2 || path.size() > MAX_PATH_POINTS) {
            throw new IllegalArgumentException("Adhesive transit path size is invalid");
        }
        for (Vec3 point : path) {
            if (point == null || !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) {
                throw new IllegalArgumentException("Adhesive transit path contains an invalid point");
            }
        }
        if (durationTicks < 1 || durationTicks > 40) {
            throw new IllegalArgumentException("Adhesive transit duration must be between 1 and 40 ticks");
        }
    }

    public Vec3 targetPosition() {
        return this.path.getLast();
    }

    public boolean hasEntityTarget() {
        return this.supportEntityUuid.isPresent();
    }

    public Vec3 supportMovement(@javax.annotation.Nullable Entity supportEntity) {
        return supportEntity == null || !this.hasEntityTarget()
            ? Vec3.ZERO
            : supportEntity.position().subtract(this.supportEntityStartPosition);
    }

    public Vec3 targetPosition(@javax.annotation.Nullable Entity supportEntity) {
        return this.targetPosition().add(this.supportMovement(supportEntity));
    }

    public Vec3 positionAt(double progress, @javax.annotation.Nullable Entity supportEntity) {
        return this.positionAt(progress).add(this.supportMovement(supportEntity));
    }

    public double rawProgress(long gameTime, float partialTick) {
        // Keep the elapsed-time subtraction in double precision.  Promoting the
        // absolute game time to float first causes visible interpolation jitter
        // once a world has been running for a large number of ticks.
        double elapsed = (double) (gameTime - this.startGameTime) + partialTick;
        return Math.clamp(elapsed / this.durationTicks, 0.0D, 1.0D);
    }

    public double easedProgress(long gameTime, float partialTick) {
        double progress = this.rawProgress(gameTime, partialTick);
        return progress * progress * (3.0D - 2.0D * progress);
    }

    public double rotationProgress(long gameTime, float partialTick) {
        if (!this.plastic) return 0.0D;
        double progress = Math.clamp((this.rawProgress(gameTime, partialTick) - 0.72D) / 0.28D, 0.0D, 1.0D);
        return progress * progress * (3.0D - 2.0D * progress);
    }

    public Vec3 positionAt(double progress) {
        if (progress <= 0.0D) return this.path.getFirst();
        if (progress >= 1.0D) return this.path.getLast();

        double totalLength = 0.0D;
        for (int i = 1; i < this.path.size(); i++) {
            totalLength += this.path.get(i - 1).distanceTo(this.path.get(i));
        }
        if (totalLength <= 1.0E-6D) return this.path.getLast();

        double targetLength = totalLength * progress;
        double traversed = 0.0D;
        for (int i = 1; i < this.path.size(); i++) {
            Vec3 from = this.path.get(i - 1);
            Vec3 to = this.path.get(i);
            double segmentLength = from.distanceTo(to);
            if (traversed + segmentLength >= targetLength) {
                double local = segmentLength <= 1.0E-6D ? 1.0D : (targetLength - traversed) / segmentLength;
                return from.lerp(to, local);
            }
            traversed += segmentLength;
        }
        return this.path.getLast();
    }
}
