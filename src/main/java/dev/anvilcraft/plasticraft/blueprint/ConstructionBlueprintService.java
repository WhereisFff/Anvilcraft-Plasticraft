package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintDisk;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.item.property.component.StructureDiskData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 结构蓝图的服务端操作入口:导入写盘、部署、锚点移动、取消与启动。
 * 导入时同时写入本体结构磁盘数据和 {@code anvilcraft/structures} 原版 NBT,
 * 使 Tooltip、旋转预览和智能方块放置器走 AnvilCraft 原逻辑;任务索引仍是施工投影的权威状态。
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
        removeDiskJob(server, disk);
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
        bindVanillaStructureDisk(server, disk, name, snapshot.size(), hash, canonical);
        return new ImportResult(data, parsed.warnings());
    }

    /**
     * 施工蓝图与本体结构是同一份原版 NBT:覆盖写入 {@code anvilcraft/structures} 和
     * {@code StructureDiskData},Tooltip「结构：」、尺寸、5×5×5 判定和旋转预览都走 AnvilCraft 原逻辑。
     */
    private static void bindVanillaStructureDisk(
        MinecraftServer server,
        ItemStack disk,
        String name,
        Vec3i size,
        String hash,
        CompoundTag canonical
    ) throws ConstructionBlueprintException {
        UUID uuid = UUID.nameUUIDFromBytes(("anvilcraftplasticraft:" + hash).getBytes(StandardCharsets.US_ASCII));
        String fileName = "blueprint_" + uuid + ".nbt";
        Path baseDir = server.getWorldPath(LevelResource.ROOT)
            .toAbsolutePath()
            .normalize()
            .resolve("anvilcraft")
            .resolve("structures");
        Path target = baseDir.resolve(fileName).toAbsolutePath().normalize();
        if (!target.startsWith(baseDir.toAbsolutePath().normalize())) {
            throw new ConstructionBlueprintException("unsafe_path", fileName);
        }
        try {
            Files.createDirectories(baseDir);
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                Path temporary = Files.createTempFile(baseDir, ".blueprint-", ".tmp");
                try {
                    NbtIo.writeCompressed(canonical, temporary);
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                    temporary = null;
                } catch (AtomicMoveNotSupportedException exception) {
                    throw new ConstructionBlueprintException(
                        "atomic_write_unsupported",
                        "Filesystem does not support atomic structure writes",
                        exception
                    );
                } finally {
                    if (temporary != null) {
                        Files.deleteIfExists(temporary);
                    }
                }
            }
        } catch (IOException exception) {
            throw new ConstructionBlueprintException("structure_write_failed", exception.getMessage(), exception);
        }
        disk.set(
            ModComponents.STRUCTURE_DISK_DATA,
            new StructureDiskData(
                fileName,
                name,
                uuid,
                Direction.NORTH,
                size.getX(),
                size.getY(),
                size.getZ(),
                false
            )
        );
    }

    /** 导入目标必须是结构磁盘、未被成型舱蓝图占用。覆盖导入会清掉该磁盘先前的部署。 */
    private static void requireImportableDisk(ItemStack disk) throws ConstructionBlueprintException {
        if (!isStructureDisk(disk)) {
            throw new ConstructionBlueprintException("not_structure_disk", "");
        }
        if (MoldingBlueprintDisk.hasBlueprintData(disk)) {
            throw new ConstructionBlueprintException("disk_in_molding_use", "");
        }
    }

    /** 磁盘改写前移除它引用的已放置蓝图,避免投影残留在世界里无法清除。 */
    private static void removeDiskJob(MinecraftServer server, ItemStack disk) {
        UUID jobId = ConstructionBlueprintData.get(disk).flatMap(ConstructionBlueprintData::jobId).orElse(null);
        if (jobId == null) return;
        ConstructionJobIndex index = ConstructionJobIndex.get(server);
        if (index.job(jobId) == null) return;
        index.remove(jobId);
        BlueprintJobSync.syncRemove(server, jobId);
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
        clearJobId(player.getInventory().getSelected(), jobId);
        clearJobId(player.getOffhandItem(), jobId);
        for (ItemStack stack : player.getInventory().items) {
            clearJobId(stack, jobId);
        }
        clearJobId(player.containerMenu.getCarried(), jobId);
    }

    private static void clearJobId(ItemStack stack, UUID jobId) {
        ConstructionBlueprintData data = ConstructionBlueprintData.get(stack).orElse(null);
        if (data != null && data.jobId().map(jobId::equals).orElse(false)) {
            ConstructionBlueprintData.set(stack, data.withoutJobId());
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

    /**
     * 客户端投影可读的结构哈希:已部署任务引用的内容,或该玩家物品栏/打开菜单里磁盘上的内容。
     * 部署确认前会话必须能拿到快照,否则世界里不会出现投影。
     */
    public static boolean canReadSnapshot(ServerPlayer player, String hash) {
        if (!ConstructionStructureLibrary.isValidHash(hash)) return false;
        for (ConstructionJob job : ConstructionJobIndex.get(player.server).jobs()) {
            if (job.hash().equals(hash)) {
                return true;
            }
        }
        if (diskHasHash(player.containerMenu.getCarried(), hash)) {
            return true;
        }
        for (Slot slot : player.containerMenu.slots) {
            if (diskHasHash(slot.getItem(), hash)) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().items) {
            if (diskHasHash(stack, hash)) {
                return true;
            }
        }
        return diskHasHash(player.getOffhandItem(), hash);
    }

    private static boolean diskHasHash(ItemStack stack, String hash) {
        return ConstructionBlueprintData.get(stack).map(data -> data.hash().equals(hash)).orElse(false);
    }

    private static void requireOwner(ServerPlayer player, ConstructionJob job)
        throws ConstructionBlueprintException {
        if (!job.owner().equals(player.getUUID())) {
            throw new ConstructionBlueprintException("not_owner", "");
        }
    }
}
