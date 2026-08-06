package dev.anvilcraft.plasticraft.entity.adhesive;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;
import java.util.UUID;

/** 实体一个碰撞箱面连接到另一实体面的持久化端点。 */
public record EntityBondLink(
    Direction face,
    UUID otherEntityUuid,
    int otherEntityId,
    Direction otherFace,
    boolean invisible
) {
    public static final Codec<EntityBondLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Direction.CODEC.fieldOf("face").forGetter(EntityBondLink::face),
        UUIDUtil.CODEC.fieldOf("other_entity").forGetter(EntityBondLink::otherEntityUuid),
        Direction.CODEC.fieldOf("other_face").forGetter(EntityBondLink::otherFace),
        Codec.BOOL.optionalFieldOf("invisible", false).forGetter(EntityBondLink::invisible)
    ).apply(instance, (face, otherEntityUuid, otherFace, invisible) -> new EntityBondLink(
        face,
        otherEntityUuid,
        -1,
        otherFace,
        invisible
    )));

    public static final StreamCodec<RegistryFriendlyByteBuf, EntityBondLink> STREAM_CODEC = StreamCodec.of(
        (buffer, link) -> {
            buffer.writeByte(link.face.get3DDataValue());
            buffer.writeUUID(link.otherEntityUuid);
            buffer.writeVarInt(link.otherEntityId);
            buffer.writeByte(link.otherFace.get3DDataValue());
            buffer.writeBoolean(link.invisible);
        },
        buffer -> new EntityBondLink(
            Direction.from3DDataValue(buffer.readUnsignedByte()),
            buffer.readUUID(),
            buffer.readVarInt(),
            Direction.from3DDataValue(buffer.readUnsignedByte()),
            buffer.readBoolean()
        )
    );

    public EntityBondLink {
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(otherEntityUuid, "otherEntityUuid");
        Objects.requireNonNull(otherFace, "otherFace");
    }

    public EntityBondLink(
        Direction face,
        UUID otherEntityUuid,
        int otherEntityId,
        Direction otherFace
    ) {
        this(face, otherEntityUuid, otherEntityId, otherFace, false);
    }

    public EntityBondLink withOtherEntityId(int entityId) {
        return new EntityBondLink(this.face, this.otherEntityUuid, entityId, this.otherFace, this.invisible);
    }

    public EntityBondLink withInvisible() {
        if (this.invisible) return this;
        return new EntityBondLink(this.face, this.otherEntityUuid, this.otherEntityId, this.otherFace, true);
    }
}
