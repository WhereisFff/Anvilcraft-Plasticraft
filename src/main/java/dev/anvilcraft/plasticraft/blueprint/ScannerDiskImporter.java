package dev.anvilcraft.plasticraft.blueprint;

import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.item.property.component.StructureDiskData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 读取 AnvilCraft 结构扫描器写出的磁盘世界文件并归一化为规范快照。
 * 扫描器文件的坐标处于随扫描器朝向旋转的预览空间而方块状态保持世界朝向,
 * 这里按磁盘记录的朝向与上下翻转标志把坐标还原成世界对齐,使部署结果与被扫描的原结构一致。
 */
public final class ScannerDiskImporter {
    private static final Pattern VALID_STRUCTURE_FILE = Pattern.compile("^[a-zA-Z0-9_\\-]+_[a-f0-9\\-]+\\.nbt$");
    private static final int MAX_STRUCTURE_FILE_LENGTH = 128;

    private ScannerDiskImporter() {
    }

    public static boolean hasScannerData(ItemStack stack) {
        return stack.get(ModComponents.STRUCTURE_DISK_DATA) != null;
    }

    /** 磁盘上的扫描器结构显示名,用作蓝图名称。 */
    public static Optional<String> scannerName(ItemStack stack) {
        StructureDiskData data = stack.get(ModComponents.STRUCTURE_DISK_DATA);
        return data == null ? Optional.empty() : Optional.of(data.name());
    }

    /** 读取扫描器世界文件、经数据修复升级后解析,并归一化到世界对齐坐标。 */
    public static StructureSnapshotCodec.ParsedSnapshot read(MinecraftServer server, ItemStack disk)
        throws ConstructionBlueprintException {
        StructureDiskData diskData = disk.get(ModComponents.STRUCTURE_DISK_DATA);
        if (diskData == null) {
            throw new ConstructionBlueprintException("scanner_data_missing", "");
        }
        CompoundTag tag = readScannerFile(server, diskData.file());
        int dataVersion = NbtUtils.getDataVersion(tag, 500);
        CompoundTag updated = DataFixTypes.STRUCTURE.updateToCurrentVersion(
            server.getFixerUpper(),
            tag,
            dataVersion
        );
        StructureSnapshotCodec.ParsedSnapshot parsed = StructureSnapshotCodec.parse(updated, server.registryAccess());
        StructureSnapshot normalized = normalize(parsed.snapshot(), diskData.direction(), diskData.upsideDown());
        return new StructureSnapshotCodec.ParsedSnapshot(
            StructureSnapshotCodec.canonicalize(normalized),
            parsed.warnings()
        );
    }

    private static CompoundTag readScannerFile(MinecraftServer server, String fileName)
        throws ConstructionBlueprintException {
        if (fileName.isEmpty()
            || fileName.length() > MAX_STRUCTURE_FILE_LENGTH
            || !VALID_STRUCTURE_FILE.matcher(fileName).matches()
            || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new ConstructionBlueprintException("unsafe_filename", fileName);
        }
        Path baseDir = server.getWorldPath(LevelResource.ROOT)
            .toAbsolutePath()
            .normalize()
            .resolve("anvilcraft")
            .resolve("structures");
        Path file = baseDir.resolve(fileName).toAbsolutePath().normalize();
        if (!file.startsWith(baseDir) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new ConstructionBlueprintException("scanner_file_missing", fileName);
        }
        try {
            return NbtIo.readCompressed(file, NbtAccounter.create(ConstructionStructureLibrary.MAX_FILE_NBT_BYTES));
        } catch (IOException exception) {
            throw new ConstructionBlueprintException("structure_read_failed", exception.getMessage(), exception);
        }
    }

    /**
     * 把预览空间坐标还原为世界对齐坐标。方块状态不旋转(扫描器保存的就是世界朝向状态),
     * 只变换方块与实体坐标;东西朝向会交换 X/Z 轴,尺寸随之交换。
     */
    public static StructureSnapshot normalize(StructureSnapshot snapshot, Direction facing, boolean upsideDown) {
        Vec3i size = snapshot.size();
        int sx = size.getX();
        int sy = size.getY();
        int sz = size.getZ();
        Vec3i normalizedSize = facing.getAxis() == Direction.Axis.X
            ? new Vec3i(sz, sy, sx)
            : size;

        List<StructureSnapshot.BlockEntry> blocks = new ArrayList<>(snapshot.blocks().size());
        for (StructureSnapshot.BlockEntry entry : snapshot.blocks()) {
            BlockPos pos = entry.pos();
            int y = upsideDown ? sy - 1 - pos.getY() : pos.getY();
            BlockPos normalized = switch (facing) {
                case SOUTH -> new BlockPos(sx - 1 - pos.getX(), y, sz - 1 - pos.getZ());
                case WEST -> new BlockPos(pos.getZ(), y, sx - 1 - pos.getX());
                case EAST -> new BlockPos(sz - 1 - pos.getZ(), y, pos.getX());
                default -> new BlockPos(pos.getX(), y, pos.getZ());
            };
            blocks.add(new StructureSnapshot.BlockEntry(normalized, entry.stateIndex(), entry.nbt()));
        }

        List<StructureSnapshot.EntityEntry> entities = new ArrayList<>(snapshot.entities().size());
        for (StructureSnapshot.EntityEntry entry : snapshot.entities()) {
            Vec3 pos = entry.pos();
            double y = upsideDown ? sy - pos.y : pos.y;
            Vec3 normalized = switch (facing) {
                case SOUTH -> new Vec3(sx - pos.x, y, sz - pos.z);
                case WEST -> new Vec3(pos.z, y, sx - pos.x);
                case EAST -> new Vec3(sz - pos.z, y, pos.x);
                default -> new Vec3(pos.x, y, pos.z);
            };
            BlockPos blockPos = entry.blockPos();
            int blockY = upsideDown ? sy - 1 - blockPos.getY() : blockPos.getY();
            BlockPos normalizedBlockPos = switch (facing) {
                case SOUTH -> new BlockPos(sx - 1 - blockPos.getX(), blockY, sz - 1 - blockPos.getZ());
                case WEST -> new BlockPos(blockPos.getZ(), blockY, sx - 1 - blockPos.getX());
                case EAST -> new BlockPos(sz - 1 - blockPos.getZ(), blockY, blockPos.getX());
                default -> new BlockPos(blockPos.getX(), blockY, blockPos.getZ());
            };
            entities.add(new StructureSnapshot.EntityEntry(normalized, normalizedBlockPos, entry.nbt()));
        }

        return new StructureSnapshot(normalizedSize, snapshot.palette(), blocks, entities);
    }
}
