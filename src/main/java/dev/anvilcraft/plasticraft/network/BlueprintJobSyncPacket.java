package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.BlueprintSource;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.client.blueprint.ClientBlueprintJobCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 施工任务索引的客户端同步包。字段顺序一经发布保持稳定,只能追加;
 * fullReplace 为真时客户端先清空缓存再应用条目。
 */
public record BlueprintJobSyncPacket(
    boolean fullReplace,
    List<ConstructionJob> updates,
    List<UUID> removals
) implements IClientboundPacket {
    public static final Type<BlueprintJobSyncPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("blueprint_job_sync")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintJobSyncPacket> STREAM_CODEC = StreamCodec.of(
        BlueprintJobSyncPacket::write,
        BlueprintJobSyncPacket::read
    );

    public static BlueprintJobSyncPacket fullReplace(List<ConstructionJob> jobs) {
        return new BlueprintJobSyncPacket(true, jobs, List.of());
    }

    public static BlueprintJobSyncPacket update(List<ConstructionJob> jobs) {
        return new BlueprintJobSyncPacket(false, jobs, List.of());
    }

    public static BlueprintJobSyncPacket remove(List<UUID> jobIds) {
        return new BlueprintJobSyncPacket(false, List.of(), jobIds);
    }

    private static void write(RegistryFriendlyByteBuf buffer, BlueprintJobSyncPacket packet) {
        buffer.writeBoolean(packet.fullReplace);
        buffer.writeVarInt(packet.updates.size());
        for (ConstructionJob job : packet.updates) {
            writeJob(buffer, job);
        }
        buffer.writeVarInt(packet.removals.size());
        for (UUID jobId : packet.removals) {
            UUIDUtil.STREAM_CODEC.encode(buffer, jobId);
        }
    }

    private static void writeJob(RegistryFriendlyByteBuf buffer, ConstructionJob job) {
        UUIDUtil.STREAM_CODEC.encode(buffer, job.jobId());
        UUIDUtil.STREAM_CODEC.encode(buffer, job.owner());
        buffer.writeByte(job.state());
        buffer.writeUtf(job.hash());
        buffer.writeResourceLocation(job.dimension().location());
        buffer.writeBlockPos(job.anchor());
        buffer.writeVarInt(job.rotation().ordinal());
        buffer.writeVarInt(job.mirror().ordinal());
        buffer.writeUtf(job.name());
        buffer.writeVarInt(job.size().getX());
        buffer.writeVarInt(job.size().getY());
        buffer.writeVarInt(job.size().getZ());
        BlueprintSource.STREAM_CODEC.encode(buffer, job.source());
        buffer.writeBoolean(job.hasBlockEntities());
        buffer.writeBoolean(job.hasEntities());
    }

    private static BlueprintJobSyncPacket read(RegistryFriendlyByteBuf buffer) {
        boolean fullReplace = buffer.readBoolean();
        int updateCount = buffer.readVarInt();
        List<ConstructionJob> updates = new ArrayList<>(updateCount);
        for (int index = 0; index < updateCount; index++) {
            updates.add(readJob(buffer));
        }
        int removalCount = buffer.readVarInt();
        List<UUID> removals = new ArrayList<>(removalCount);
        for (int index = 0; index < removalCount; index++) {
            removals.add(UUIDUtil.STREAM_CODEC.decode(buffer));
        }
        return new BlueprintJobSyncPacket(fullReplace, updates, removals);
    }

    private static ConstructionJob readJob(RegistryFriendlyByteBuf buffer) {
        UUID jobId = UUIDUtil.STREAM_CODEC.decode(buffer);
        UUID owner = UUIDUtil.STREAM_CODEC.decode(buffer);
        byte state = buffer.readByte();
        String hash = buffer.readUtf();
        ResourceLocation dimension = buffer.readResourceLocation();
        BlockPos anchor = buffer.readBlockPos();
        Rotation rotation = Rotation.values()[buffer.readVarInt() % Rotation.values().length];
        Mirror mirror = Mirror.values()[buffer.readVarInt() % Mirror.values().length];
        String name = buffer.readUtf();
        Vec3i size = new Vec3i(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        BlueprintSource source = BlueprintSource.STREAM_CODEC.decode(buffer);
        boolean hasBlockEntities = buffer.readBoolean();
        boolean hasEntities = buffer.readBoolean();
        return new ConstructionJob(
            jobId,
            owner,
            state,
            hash,
            ResourceKey.create(Registries.DIMENSION, dimension),
            anchor,
            rotation,
            mirror,
            name,
            size,
            source,
            hasBlockEntities,
            hasEntities
        );
    }

    @Override
    public Type<BlueprintJobSyncPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnClient(Player player) {
        ClientBlueprintJobCache.apply(this);
    }
}
