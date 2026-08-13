package dev.anvilcraft.plasticraft.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/**
 * 世界级结构快照库:规范结构 NBT 以内容哈希为文件名保存在
 * {@code <world>/anvilcraftplasticraft/structures/<sha256>.nbt},内容寻址天然去重。
 * 导入时另写一份本体可读的 {@code anvilcraft/structures} 文件,供预览和智能方块放置器使用。
 */
public final class ConstructionStructureLibrary {
    /** 单个库文件解压后允许的最大 NBT 字节数。 */
    public static final long MAX_FILE_NBT_BYTES = 64L * 1024L * 1024L;
    private static final String LIBRARY_DIRECTORY = "structures";
    private static final Pattern HASH_FILE = Pattern.compile("[a-f0-9]{64}");

    private ConstructionStructureLibrary() {
    }

    public static boolean isValidHash(String hash) {
        return HASH_FILE.matcher(hash).matches();
    }

    public static boolean exists(MinecraftServer server, String hash) {
        try {
            return Files.isRegularFile(resolveFile(server, hash), LinkOption.NOFOLLOW_LINKS);
        } catch (ConstructionBlueprintException exception) {
            return false;
        }
    }

    /** 原子写入一份规范结构 NBT;同哈希文件已存在时视为内容一致直接复用。 */
    public static void store(MinecraftServer server, String hash, CompoundTag canonicalTag)
        throws ConstructionBlueprintException {
        Path target = resolveFile(server, hash);
        if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) return;
        Path temporary = null;
        try {
            temporary = Files.createTempFile(target.getParent(), ".structure-", ".tmp");
            NbtIo.writeCompressed(canonicalTag, temporary);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            temporary = null;
        } catch (AtomicMoveNotSupportedException exception) {
            throw new ConstructionBlueprintException(
                "atomic_write_unsupported",
                "Filesystem does not support atomic structure writes",
                exception
            );
        } catch (IOException exception) {
            throw new ConstructionBlueprintException("structure_write_failed", exception.getMessage(), exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static CompoundTag load(MinecraftServer server, String hash) throws ConstructionBlueprintException {
        Path file = resolveFile(server, hash);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new ConstructionBlueprintException("structure_missing", hash);
        }
        try {
            return NbtIo.readCompressed(file, NbtAccounter.create(MAX_FILE_NBT_BYTES));
        } catch (IOException exception) {
            throw new ConstructionBlueprintException("structure_read_failed", exception.getMessage(), exception);
        }
    }

    private static Path resolveFile(MinecraftServer server, String hash) throws ConstructionBlueprintException {
        if (!isValidHash(hash)) {
            throw new ConstructionBlueprintException("unsafe_filename", "Invalid structure hash: " + hash);
        }
        Path root = libraryRoot(server);
        Path file = root.resolve(hash + ".nbt").toAbsolutePath().normalize();
        if (!file.getParent().equals(root.toAbsolutePath().normalize()) || Files.isSymbolicLink(file)) {
            throw new ConstructionBlueprintException("unsafe_path", "Structure path escapes its library");
        }
        return file;
    }

    public static Path libraryRoot(MinecraftServer server) throws ConstructionBlueprintException {
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path base = world.resolve("anvilcraftplasticraft");
        Path directory = base.resolve(LIBRARY_DIRECTORY);
        try {
            createCheckedDirectory(base);
            createCheckedDirectory(directory);
            Path realWorld = world.toRealPath();
            Path realDirectory = directory.toRealPath();
            if (!realDirectory.startsWith(realWorld)) {
                throw new ConstructionBlueprintException(
                    "unsafe_symlink",
                    "Structure directory escapes the world directory"
                );
            }
            return directory;
        } catch (IOException exception) {
            throw new ConstructionBlueprintException("library_directory_failed", exception.getMessage(), exception);
        }
    }

    private static void createCheckedDirectory(Path directory)
        throws IOException, ConstructionBlueprintException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new ConstructionBlueprintException(
                    "unsafe_symlink",
                    "Structure directory is not a real directory"
                );
            }
            return;
        }
        Files.createDirectory(directory);
        if (Files.isSymbolicLink(directory)) {
            throw new ConstructionBlueprintException("unsafe_symlink", "Structure directory is a symbolic link");
        }
    }
}
