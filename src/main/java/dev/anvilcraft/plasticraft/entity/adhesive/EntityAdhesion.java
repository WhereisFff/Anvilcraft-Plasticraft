package dev.anvilcraft.plasticraft.entity.adhesive;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/** 记录普通实体被高粘性树脂固定后所依赖的支撑面和精确位置。 */
public record EntityAdhesion(
    BlockPos supportPos,
    Direction attachmentFace,
    ResourceLocation supportBlockId,
    Vec3 fixedPosition,
    boolean originalNoGravity,
    boolean invisible
) {
    private static final ResourceLocation DEFAULT_SUPPORT_BLOCK = ResourceLocation.fromNamespaceAndPath(
        "minecraft",
        "air"
    );

    public static final Codec<EntityAdhesion> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BlockPos.CODEC.fieldOf("support_pos").forGetter(EntityAdhesion::supportPos),
        Direction.CODEC.fieldOf("attachment_face").forGetter(EntityAdhesion::attachmentFace),
        ResourceLocation.CODEC.optionalFieldOf("support_block", DEFAULT_SUPPORT_BLOCK)
            .forGetter(EntityAdhesion::supportBlockId),
        Vec3.CODEC.fieldOf("fixed_position").forGetter(EntityAdhesion::fixedPosition),
        Codec.BOOL.optionalFieldOf("original_no_gravity", false).forGetter(EntityAdhesion::originalNoGravity),
        Codec.BOOL.optionalFieldOf("invisible", false).forGetter(EntityAdhesion::invisible)
    ).apply(instance, EntityAdhesion::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EntityAdhesion> STREAM_CODEC = StreamCodec.of(
        (buffer, adhesion) -> {
            BlockPos.STREAM_CODEC.encode(buffer, adhesion.supportPos);
            buffer.writeByte(adhesion.attachmentFace.get3DDataValue());
            ResourceLocation.STREAM_CODEC.encode(buffer, adhesion.supportBlockId);
            buffer.writeDouble(adhesion.fixedPosition.x);
            buffer.writeDouble(adhesion.fixedPosition.y);
            buffer.writeDouble(adhesion.fixedPosition.z);
            buffer.writeBoolean(adhesion.originalNoGravity);
            buffer.writeBoolean(adhesion.invisible);
        },
        buffer -> new EntityAdhesion(
            BlockPos.STREAM_CODEC.decode(buffer),
            Direction.from3DDataValue(buffer.readUnsignedByte()),
            ResourceLocation.STREAM_CODEC.decode(buffer),
            new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
            buffer.readBoolean(),
            buffer.readBoolean()
        )
    );

    public EntityAdhesion {
        supportPos = Objects.requireNonNull(supportPos, "supportPos").immutable();
        Objects.requireNonNull(attachmentFace, "attachmentFace");
        Objects.requireNonNull(supportBlockId, "supportBlockId");
        Objects.requireNonNull(fixedPosition, "fixedPosition");
        if (!Double.isFinite(fixedPosition.x)
            || !Double.isFinite(fixedPosition.y)
            || !Double.isFinite(fixedPosition.z)) {
            throw new IllegalArgumentException("Fixed position must be finite");
        }
    }

    public EntityAdhesion(
        BlockPos supportPos,
        Direction attachmentFace,
        ResourceLocation supportBlockId,
        Vec3 fixedPosition,
        boolean originalNoGravity
    ) {
        this(supportPos, attachmentFace, supportBlockId, fixedPosition, originalNoGravity, false);
    }

    public EntityAdhesion moved(Direction direction) {
        return new EntityAdhesion(
            this.supportPos.relative(direction),
            this.attachmentFace,
            this.supportBlockId,
            this.fixedPosition.add(direction.getStepX(), direction.getStepY(), direction.getStepZ()),
            this.originalNoGravity,
            this.invisible
        );
    }

    public EntityAdhesion withInvisible() {
        if (this.invisible) return this;
        return new EntityAdhesion(
            this.supportPos,
            this.attachmentFace,
            this.supportBlockId,
            this.fixedPosition,
            this.originalNoGravity,
            true
        );
    }
}
