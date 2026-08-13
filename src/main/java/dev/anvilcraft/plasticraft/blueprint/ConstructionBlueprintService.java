package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintDisk;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 结构蓝图的服务端操作入口:导入写盘、部署、锚点移动、取消与启动。
 * 所有操作只信任服务端状态;磁盘组件是任务索引的引用,不承载权威数据。
 */
public final class ConstructionBlueprintService {
    private ConstructionBlueprintService() {
    }

    /** 导入结果:写入磁盘的组件与解析警告。 */
    public record ImportResult(ConstructionBlueprintData data, List<StructureSnapshotCodec.BlueprintWarning> warnings) {
    }

    public static boolean isStructureDisk(ItemStack stack) {
        return stack.is(ModItems.STRUCTURE_DISK.get());
    }

    /**
     * 把一份原版结构语义的 NBT 导入世界结构库并写到磁盘上。
     * 输入先经数据修复边界升级到当前版本,再做规范化解析与校验。
     */
    public static ImportResult importIntoDisk(
        MinecraftServer server,
        ItemStack disk,
        CompoundTag structureTag,
        String name,
        BlueprintSource source
    ) throws ConstructionBlueprintException {
        requireImportableDisk(disk);
        int dataVersion = NbtUtils.getDataVersion(structureTag, 500);
        CompoundTag updated = DataFixTypes.STRUCTURE.updateToCurrentVersion(
            server.getFixerUpper(),
            structureTag,
            dataVersion
        );
        StructureSnapshotCodec.ParsedSnapshot parsed = StructureSnapshotCodec.parse(
            updated,
            server.registryAccess()
        );
        return importSnapshot(server, disk, parsed, name, source);
    }

    /** 把已解析的快照入库并写盘;供扫描器磁盘等已归一化的来源复用。 */
    public static ImportResult importSnapshot(
        MinecraftServer server,
        ItemStack disk,
        StructureSnapshotCodec.ParsedSnapshot parsed,
        String name,
        BlueprintSource source
    ) throws ConstructionBlueprintException {
        requireImportableDisk(disk);
        StructureSnapshot snapshot = StructureSnapshotCodec.canonicalize(parsed.snapshot());
        CompoundTag canonical = StructureSnapshotCodec.write(snapshot);
        String hash = StructureSnapshotCodec.hash(canonical);
        ConstructionStructureLibrary.store(server, hash, canonical);
        ConstructionBlueprintData data = new ConstructionBlueprintData(
            hash,
            name,
            snapshot.size(),
            source,
            snapshot.hasBlockEntities(),
            snapshot.hasEntities(),
            Optional.empty()
        );
        ConstructionBlueprintData.set(disk, data);
        return new ImportResult(data, parsed.warnings());
    }

    /** 导入目标必须是结构磁盘、未被成型舱蓝图占用,且没有仍然有效的部署任务。 */
    private static void requireImportableDisk(ItemStack disk) throws ConstructionBlueprintException {
        if (!isStructureDisk(disk)) {
            throw new ConstructionBlueprintException("not_structure_disk", "");
        }
        if (MoldingBlueprintDisk.hasBlueprintData(disk)) {
            throw new ConstructionBlueprintException("disk_in_molding_use", "");
        }
    }

    /**
     * 部署或重摆一张已导入磁盘的蓝图。首次部署创建 INACTIVE 任务并把 jobId 写回磁盘;
     * 磁盘已有有效任务时按锚点移动处理,只更新放置参数。
     */
    public static ConstructionJob deploy(
        ServerPlayer player,
        InteractionHand hand,
        BlockPos anchor,
        Rotation rotation,
        Mirror mirror
    ) throws ConstructionBlueprintException {
        MinecraftServer server = player.server;
        ItemStack disk = player.getItemInHand(hand);
        ConstructionBlueprintData data = ConstructionBlueprintData.get(disk)
            .orElseThrow(() -> new ConstructionBlueprintException("disk_not_imported", ""));
        if (!ConstructionStructureLibrary.exists(server, data.hash())) {
            throw new ConstructionBlueprintException("structure_missing", data.hash());
        }
        ConstructionJobIndex index = ConstructionJobIndex.get(server);
        ConstructionJob existing = data.jobId().map(index::job).orElse(null);
        if (existing != null) {
            requireOwner(player, existing);
            ConstructionJob moved = existing.withPlacement(anchor, rotation, mirror);
            index.put(moved);
            BlueprintJobSync.syncPut(server, moved);
            return moved;
        }
        ConstructionJob job = new ConstructionJob(
            UUID.randomUUID(),
            player.getUUID(),
            ConstructionJob.STATE_INACTIVE,
            data.hash(),
            player.level().dimension(),
            anchor.immutable(),
            rotation,
            mirror,
            data.name(),
            data.size(),
            data.source(),
            data.hasBlockEntities(),
            data.hasEntities()
        );
        index.put(job);
        ConstructionBlueprintData.set(disk, data.withJobId(job.jobId()));
        BlueprintJobSync.syncPut(server, job);
        return job;
    }

    /** 取消一份已放置蓝图:删除任务条目,并清除玩家手中引用它的磁盘组件 jobId。 */
    public static void cancel(ServerPlayer player, UUID jobId) throws ConstructionBlueprintException {
        MinecraftServer server = player.server;
        ConstructionJobIndex index = ConstructionJobIndex.get(server);
        ConstructionJob job = index.job(jobId);
        if (job == null) {
            throw new ConstructionBlueprintException("job_missing", "");
        }
        requireOwner(player, job);
        index.remove(jobId);
        BlueprintJobSync.syncRemove(server, jobId);
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            ConstructionBlueprintData data = ConstructionBlueprintData.get(held).orElse(null);
            if (data != null && data.jobId().map(jobId::equals).orElse(false)) {
                ConstructionBlueprintData.set(held, data.withoutJobId());
            }
        }
    }

    /** 启动一份任务并暂停该玩家的其他活动任务;TODO 04 只切换状态,不派发无人机。 */
    public static void start(ServerPlayer player, UUID jobId) throws ConstructionBlueprintException {
        MinecraftServer server = player.server;
        ConstructionJobIndex index = ConstructionJobIndex.get(server);
        ConstructionJob job = index.job(jobId);
        if (job == null) {
            throw new ConstructionBlueprintException("job_missing", "");
        }
        requireOwner(player, job);
        List<ConstructionJob> paused = index.activate(jobId);
        for (ConstructionJob pausedJob : paused) {
            BlueprintJobSync.syncPut(server, pausedJob);
        }
        ConstructionJob started = index.job(jobId);
        if (started != null) {
            BlueprintJobSync.syncPut(server, started);
        }
    }

    /** 菜单内右击磁盘的启动/停止切换;返回切换后是否处于启动状态。 */
    public static boolean toggleActive(ServerPlayer player, UUID jobId) throws ConstructionBlueprintException {
        MinecraftServer server = player.server;
        ConstructionJobIndex index = ConstructionJobIndex.get(server);
        ConstructionJob job = index.job(jobId);
        if (job == null) {
            throw new ConstructionBlueprintException("job_missing", "");
        }
        requireOwner(player, job);
        if (job.isActive()) {
            ConstructionJob stopped = job.withState(ConstructionJob.STATE_INACTIVE);
            index.put(stopped);
            BlueprintJobSync.syncPut(server, stopped);
            return false;
        }
        start(player, jobId);
        return true;
    }

    private static void requireOwner(ServerPlayer player, ConstructionJob job)
        throws ConstructionBlueprintException {
        if (!job.owner().equals(player.getUUID())) {
            throw new ConstructionBlueprintException("not_owner", "");
        }
    }
}
