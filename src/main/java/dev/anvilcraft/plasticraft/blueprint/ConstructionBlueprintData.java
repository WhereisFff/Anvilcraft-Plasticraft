package dev.anvilcraft.plasticraft.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.plasticraft.init.PlasticraftDataComponents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.Vec3i;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * 结构磁盘上的蓝图引用组件。磁盘只携带内容哈希与摘要,结构内容保存在世界结构库,
 * 任务状态保存在世界任务索引;jobId 存在但任务索引查不到时一律按"未部署"处理。
 * 字段顺序冻结:hash, name, size, source, has_block_entities, has_entities, job_id,只能追加。
 */
public record ConstructionBlueprintData(
    String hash,
    String name,
    Vec3i size,
    BlueprintSource source,
    boolean hasBlockEntities,
    boolean hasEntities,
    Optional<UUID> jobId
) {
    public static final Codec<ConstructionBlueprintData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("hash").forGetter(ConstructionBlueprintData::hash),
        Codec.STRING.fieldOf("name").forGetter(ConstructionBlueprintData::name),
        Vec3i.CODEC.fieldOf("size").forGetter(ConstructionBlueprintData::size),
        BlueprintSource.CODEC.fieldOf("source").forGetter(ConstructionBlueprintData::source),
        Codec.BOOL.fieldOf("has_block_entities").forGetter(ConstructionBlueprintData::hasBlockEntities),
        Codec.BOOL.fieldOf("has_entities").forGetter(ConstructionBlueprintData::hasEntities),
        UUIDUtil.CODEC.optionalFieldOf("job_id").forGetter(ConstructionBlueprintData::jobId)
    ).apply(instance, ConstructionBlueprintData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructionBlueprintData> STREAM_CODEC =
        StreamCodec.of(ConstructionBlueprintData::write, ConstructionBlueprintData::read);

    private static void write(RegistryFriendlyByteBuf buffer, ConstructionBlueprintData data) {
        buffer.writeUtf(data.hash);
        buffer.writeUtf(data.name);
        buffer.writeVarInt(data.size.getX());
        buffer.writeVarInt(data.size.getY());
        buffer.writeVarInt(data.size.getZ());
        BlueprintSource.STREAM_CODEC.encode(buffer, data.source);
        buffer.writeBoolean(data.hasBlockEntities);
        buffer.writeBoolean(data.hasEntities);
        ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).encode(buffer, data.jobId);
    }

    private static ConstructionBlueprintData read(RegistryFriendlyByteBuf buffer) {
        return new ConstructionBlueprintData(
            buffer.readUtf(),
            buffer.readUtf(),
            new Vec3i(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()),
            BlueprintSource.STREAM_CODEC.decode(buffer),
            buffer.readBoolean(),
            buffer.readBoolean(),
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buffer)
        );
    }

    /** 部署后写回 jobId 的副本。 */
    public ConstructionBlueprintData withJobId(UUID newJobId) {
        return new ConstructionBlueprintData(
            this.hash,
            this.name,
            this.size,
            this.source,
            this.hasBlockEntities,
            this.hasEntities,
            Optional.of(newJobId)
        );
    }

    /** 取消部署后清除 jobId 的副本。 */
    public ConstructionBlueprintData withoutJobId() {
        return new ConstructionBlueprintData(
            this.hash,
            this.name,
            this.size,
            this.source,
            this.hasBlockEntities,
            this.hasEntities,
            Optional.empty()
        );
    }

    public static Optional<ConstructionBlueprintData> get(ItemStack stack) {
        return Optional.ofNullable(stack.get(PlasticraftDataComponents.BLUEPRINT_TASK.get()));
    }

    public static void set(ItemStack stack, ConstructionBlueprintData data) {
        stack.set(PlasticraftDataComponents.BLUEPRINT_TASK.get(), data);
    }
}
