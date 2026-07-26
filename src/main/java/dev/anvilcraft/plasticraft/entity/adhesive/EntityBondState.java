package dev.anvilcraft.plasticraft.entity.adhesive;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 实体粘接组的基准实体、相对位置和六面连接。 */
public record EntityBondState(
    UUID leaderUuid,
    int leaderEntityId,
    Vec3 offsetFromLeader,
    boolean originalNoGravity,
    List<EntityBondLink> links
) {
    public static final Codec<EntityBondState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("leader").forGetter(EntityBondState::leaderUuid),
        Codec.INT.optionalFieldOf("leader_entity_id", -1).forGetter(EntityBondState::leaderEntityId),
        Vec3.CODEC.fieldOf("offset_from_leader").forGetter(EntityBondState::offsetFromLeader),
        Codec.BOOL.optionalFieldOf("original_no_gravity", false).forGetter(EntityBondState::originalNoGravity),
        EntityBondLink.CODEC.listOf(0, 6).fieldOf("links").forGetter(EntityBondState::links)
    ).apply(instance, EntityBondState::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EntityBondState> STREAM_CODEC = StreamCodec.of(
        (buffer, state) -> {
            buffer.writeUUID(state.leaderUuid);
            buffer.writeVarInt(state.leaderEntityId);
            buffer.writeDouble(state.offsetFromLeader.x);
            buffer.writeDouble(state.offsetFromLeader.y);
            buffer.writeDouble(state.offsetFromLeader.z);
            buffer.writeBoolean(state.originalNoGravity);
            buffer.writeVarInt(state.links.size());
            for (EntityBondLink link : state.links) EntityBondLink.STREAM_CODEC.encode(buffer, link);
        },
        buffer -> {
            UUID leaderUuid = buffer.readUUID();
            int leaderEntityId = buffer.readVarInt();
            Vec3 offset = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
            boolean originalNoGravity = buffer.readBoolean();
            int linkCount = Math.clamp(buffer.readVarInt(), 0, 6);
            List<EntityBondLink> links = new ArrayList<>(linkCount);
            for (int index = 0; index < linkCount; index++) {
                links.add(EntityBondLink.STREAM_CODEC.decode(buffer));
            }
            return new EntityBondState(leaderUuid, leaderEntityId, offset, originalNoGravity, links);
        }
    );

    public EntityBondState {
        Objects.requireNonNull(leaderUuid, "leaderUuid");
        Objects.requireNonNull(offsetFromLeader, "offsetFromLeader");
        if (!Double.isFinite(offsetFromLeader.x)
            || !Double.isFinite(offsetFromLeader.y)
            || !Double.isFinite(offsetFromLeader.z)) {
            throw new IllegalArgumentException("Entity bond offset must be finite");
        }
        Map<Direction, EntityBondLink> byFace = new LinkedHashMap<>();
        for (EntityBondLink link : links) {
            EntityBondLink previous = byFace.put(link.face(), Objects.requireNonNull(link, "link"));
            if (previous != null) throw new IllegalArgumentException("Each entity face can contain only one bond");
        }
        if (byFace.size() > 6) {
            throw new IllegalArgumentException("Entity bond state cannot contain more than six links");
        }
        links = List.copyOf(byFace.values());
    }

    public @Nullable EntityBondLink linkAt(Direction face) {
        for (EntityBondLink link : this.links) {
            if (link.face() == face) return link;
        }
        return null;
    }

    public EntityBondState withLink(EntityBondLink link) {
        List<EntityBondLink> changed = new ArrayList<>(this.links.size() + 1);
        for (EntityBondLink current : this.links) {
            if (current.face() != link.face()) changed.add(current);
        }
        changed.add(link);
        return new EntityBondState(
            this.leaderUuid,
            this.leaderEntityId,
            this.offsetFromLeader,
            this.originalNoGravity,
            changed
        );
    }

    public EntityBondState withoutLinksTo(UUID otherEntityUuid) {
        List<EntityBondLink> changed = new ArrayList<>(this.links.size());
        for (EntityBondLink current : this.links) {
            if (!current.otherEntityUuid().equals(otherEntityUuid)) changed.add(current);
        }
        return new EntityBondState(
            this.leaderUuid,
            this.leaderEntityId,
            this.offsetFromLeader,
            this.originalNoGravity,
            changed
        );
    }

    public EntityBondState withoutLinkAt(Direction face) {
        List<EntityBondLink> changed = new ArrayList<>(this.links.size());
        for (EntityBondLink current : this.links) {
            if (current.face() != face) changed.add(current);
        }
        return new EntityBondState(
            this.leaderUuid,
            this.leaderEntityId,
            this.offsetFromLeader,
            this.originalNoGravity,
            changed
        );
    }

    public EntityBondState withLeader(Entity leader, Vec3 offset) {
        return new EntityBondState(
            leader.getUUID(),
            leader.getId(),
            offset,
            this.originalNoGravity,
            this.links
        );
    }

}
