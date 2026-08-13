package dev.anvilcraft.plasticraft.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.util.Locale;
import java.util.UUID;

/**
 * 一份已部署蓝图对应的服务端权威任务。TODO 04 只区分未启动与已启动;
 * 后续任务系统在 state 字节上追加新状态值,既有值保持不变。
 */
public record ConstructionJob(
    UUID jobId,
    UUID owner,
    byte state,
    String hash,
    ResourceKey<Level> dimension,
    BlockPos anchor,
    Rotation rotation,
    Mirror mirror,
    String name,
    Vec3i size,
    BlueprintSource source,
    boolean hasBlockEntities,
    boolean hasEntities
) {
    /** 投影已放置但未启动。 */
    public static final byte STATE_INACTIVE = 0;
    /** 已启动;每名玩家同一时间最多一份。 */
    public static final byte STATE_ACTIVE = 1;

    private static final Codec<Rotation> ROTATION_CODEC = Codec.STRING.xmap(
        name -> Rotation.valueOf(name.toUpperCase(Locale.ROOT)),
        rotation -> rotation.name().toLowerCase(Locale.ROOT)
    );
    private static final Codec<Mirror> MIRROR_CODEC = Codec.STRING.xmap(
        name -> Mirror.valueOf(name.toUpperCase(Locale.ROOT)),
        mirror -> mirror.name().toLowerCase(Locale.ROOT)
    );

    public static final Codec<ConstructionJob> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("job_id").forGetter(ConstructionJob::jobId),
        UUIDUtil.CODEC.fieldOf("owner").forGetter(ConstructionJob::owner),
        Codec.BYTE.fieldOf("state").forGetter(ConstructionJob::state),
        Codec.STRING.fieldOf("hash").forGetter(ConstructionJob::hash),
        ResourceLocation.CODEC.xmap(
            id -> ResourceKey.create(Registries.DIMENSION, id),
            ResourceKey::location
        ).fieldOf("dimension").forGetter(ConstructionJob::dimension),
        BlockPos.CODEC.fieldOf("anchor").forGetter(ConstructionJob::anchor),
        ROTATION_CODEC.fieldOf("rotation").forGetter(ConstructionJob::rotation),
        MIRROR_CODEC.fieldOf("mirror").forGetter(ConstructionJob::mirror),
        Codec.STRING.fieldOf("name").forGetter(ConstructionJob::name),
        Vec3i.CODEC.fieldOf("size").forGetter(ConstructionJob::size),
        BlueprintSource.CODEC.fieldOf("source").forGetter(ConstructionJob::source),
        Codec.BOOL.fieldOf("has_block_entities").forGetter(ConstructionJob::hasBlockEntities),
        Codec.BOOL.fieldOf("has_entities").forGetter(ConstructionJob::hasEntities)
    ).apply(instance, ConstructionJob::new));

    public boolean isActive() {
        return this.state == STATE_ACTIVE;
    }

    public ConstructionJob withState(byte newState) {
        return new ConstructionJob(
            this.jobId,
            this.owner,
            newState,
            this.hash,
            this.dimension,
            this.anchor,
            this.rotation,
            this.mirror,
            this.name,
            this.size,
            this.source,
            this.hasBlockEntities,
            this.hasEntities
        );
    }

    public ConstructionJob withPlacement(BlockPos newAnchor, Rotation newRotation, Mirror newMirror) {
        return new ConstructionJob(
            this.jobId,
            this.owner,
            this.state,
            this.hash,
            this.dimension,
            newAnchor.immutable(),
            newRotation,
            newMirror,
            this.name,
            this.size,
            this.source,
            this.hasBlockEntities,
            this.hasEntities
        );
    }
}
