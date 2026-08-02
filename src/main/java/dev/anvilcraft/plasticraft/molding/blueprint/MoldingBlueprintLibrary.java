package dev.anvilcraft.plasticraft.molding.blueprint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** 世界级共享蓝图库及按玩家保存的置顶偏好。 */
public final class MoldingBlueprintLibrary {
    public static final int MAX_LIBRARY_FILES = 512;
    private static final int MAX_PREFERENCE_BYTES = 64 * 1024;
    private static final Pattern SAFE_FILE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,95}\\.json");
    private static final String LIBRARY_DIRECTORY = "blueprints";
    private static final String PREFERENCE_DIRECTORY = "blueprint_preferences";

    private MoldingBlueprintLibrary() {
    }

    public static List<MoldingBlueprintSummary> list(ServerPlayer player) throws BlueprintException {
        MinecraftServer server = player.getServer();
        if (server == null) throw new BlueprintException("server_unavailable", "Server is unavailable");
        List<String> pins = readPins(server, player.getUUID());
        Set<String> pinned = Set.copyOf(pins);
        List<MoldingBlueprintSummary> summaries = new ArrayList<>();
        Path root = libraryRoot(server);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "*.json")) {
            int visited = 0;
            for (Path path : stream) {
                String fileId = path.getFileName().toString();
                if (!isSafeFileId(fileId)
                    || Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (++visited > MAX_LIBRARY_FILES) break;
                try {
                    MoldingBlueprint blueprint = readPath(path);
                    summaries.add(MoldingBlueprintSummary.of(fileId, blueprint, pinned.contains(fileId)));
                } catch (BlueprintException exception) {
                    AnvilcraftPlasticraft.LOGGER.warn("Skipping invalid shared blueprint {}: {}", path, exception.getMessage());
                }
            }
        } catch (IOException exception) {
            throw ioFailure("library_read_failed", root, exception);
        }

        Map<String, Integer> pinOrder = new HashMap<>();
        for (int index = 0; index < pins.size(); index++) pinOrder.putIfAbsent(pins.get(index), index);
        summaries.sort(Comparator
            .comparingInt((MoldingBlueprintSummary summary) -> pinOrder.getOrDefault(summary.fileId(), Integer.MAX_VALUE))
            .thenComparing(summary -> summary.name().toLowerCase(Locale.ROOT))
            .thenComparing(MoldingBlueprintSummary::fileId));
        return List.copyOf(summaries);
    }

    public static StoredBlueprint saveNew(MinecraftServer server, MoldingBlueprint blueprint)
        throws BlueprintException {
        Path root = libraryRoot(server);
        requireCapacity(root);
        String fileId = uniqueFileId(root, blueprint.name());
        Path path = resolveFile(root, fileId);
        writeAtomic(path, MoldingBlueprintCodec.encode(blueprint), false);
        return new StoredBlueprint(fileId, blueprint);
    }

    public static MoldingBlueprint read(MinecraftServer server, String fileId, long expectedRevision)
        throws BlueprintException {
        Path path = resolveFile(libraryRoot(server), fileId);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new BlueprintException("blueprint_missing", "Shared blueprint no longer exists");
        }
        MoldingBlueprint blueprint = readPath(path);
        if (expectedRevision > 0L && blueprint.fileRevision() != expectedRevision) {
            throw new BlueprintException("stale_file_revision", "Shared blueprint was changed by another player");
        }
        return blueprint;
    }

    public static Optional<StoredBlueprint> findOwnedModel(
        MinecraftServer server,
        UUID ownerId,
        String name,
        String modelHash
    ) throws BlueprintException {
        Path root = libraryRoot(server);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "*.json")) {
            int visited = 0;
            for (Path path : stream) {
                String fileId = path.getFileName().toString();
                if (!isSafeFileId(fileId)
                    || Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (++visited > MAX_LIBRARY_FILES) break;
                try {
                    MoldingBlueprint blueprint = readPath(path);
                    if (blueprint.ownerId().equals(ownerId)
                        && blueprint.name().equals(name)
                        && blueprint.modelHash().equals(modelHash)) {
                        return Optional.of(new StoredBlueprint(fileId, blueprint));
                    }
                } catch (BlueprintException ignored) {
                }
            }
            return Optional.empty();
        } catch (IOException exception) {
            throw ioFailure("library_read_failed", root, exception);
        }
    }

    public static StoredBlueprint copy(
        ServerPlayer player,
        String fileId,
        long expectedRevision
    ) throws BlueprintException {
        MinecraftServer server = requireServer(player);
        MoldingBlueprint source = read(server, fileId, expectedRevision);
        String copyName = uniqueDisplayName(server, source.name() + " Copy");
        MoldingBlueprint copy = source.copyFor(
            copyName,
            player.getUUID(),
            player.getGameProfile().getName(),
            System.currentTimeMillis()
        );
        return saveNew(server, copy);
    }

    public static MoldingBlueprint overwrite(
        ServerPlayer player,
        String fileId,
        long expectedRevision,
        EditableBlueprint replacement
    ) throws BlueprintException {
        MinecraftServer server = requireServer(player);
        MoldingBlueprint current = read(server, fileId, expectedRevision);
        requireManagePermission(player, current);
        MoldingBlueprint revised = current.revise(
            replacement.name(),
            replacement.model(),
            System.currentTimeMillis()
        );
        writeAtomic(resolveFile(libraryRoot(server), fileId), MoldingBlueprintCodec.encode(revised), true);
        return revised;
    }

    public static MoldingBlueprint rename(
        ServerPlayer player,
        String fileId,
        long expectedRevision,
        String newName
    ) throws BlueprintException {
        MoldingBlueprint current = read(requireServer(player), fileId, expectedRevision);
        return overwrite(player, fileId, expectedRevision, new EditableBlueprint(newName, current.model()));
    }

    public static void delete(ServerPlayer player, String fileId, long expectedRevision)
        throws BlueprintException {
        MinecraftServer server = requireServer(player);
        MoldingBlueprint current = read(server, fileId, expectedRevision);
        requireManagePermission(player, current);
        Path path = resolveFile(libraryRoot(server), fileId);
        try {
            if (!Files.deleteIfExists(path)) {
                throw new BlueprintException("blueprint_missing", "Shared blueprint no longer exists");
            }
        } catch (IOException exception) {
            throw ioFailure("delete_failed", path, exception);
        }
    }

    static void rollbackNew(MinecraftServer server, StoredBlueprint stored) {
        try {
            Path path = resolveFile(libraryRoot(server), stored.fileId());
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return;
            MoldingBlueprint current = readPath(path);
            if (current.fileRevision() == stored.blueprint().fileRevision()
                && current.modelHash().equals(stored.blueprint().modelHash())
                && current.ownerId().equals(stored.blueprint().ownerId())) {
                Files.deleteIfExists(path);
            }
        } catch (BlueprintException | IOException exception) {
            AnvilcraftPlasticraft.LOGGER.error(
                "Unable to roll back shared blueprint {} after a disk update failure",
                stored.fileId(),
                exception
            );
        }
    }

    public static boolean togglePin(
        ServerPlayer player,
        String fileId,
        long expectedRevision
    ) throws BlueprintException {
        MinecraftServer server = requireServer(player);
        read(server, fileId, expectedRevision);
        List<String> pins = new ArrayList<>(readPins(server, player.getUUID()));
        boolean pinned;
        if (pins.remove(fileId)) {
            pinned = false;
        } else {
            pins.add(fileId);
            pinned = true;
        }
        writePins(server, player.getUUID(), pins);
        return pinned;
    }

    public static Path libraryRoot(MinecraftServer server) throws BlueprintException {
        return ensureOwnedDirectory(server, LIBRARY_DIRECTORY);
    }

    public static boolean isSafeFileId(String fileId) {
        return fileId != null && SAFE_FILE_ID.matcher(fileId).matches();
    }

    public static String safeStem(String name) {
        StringBuilder result = new StringBuilder();
        boolean separator = false;
        for (int index = 0; index < name.length() && result.length() < 64; index++) {
            char character = Character.toLowerCase(name.charAt(index));
            if (character >= 'a' && character <= 'z' || character >= '0' && character <= '9') {
                result.append(character);
                separator = false;
            } else if (!separator && !result.isEmpty()) {
                result.append('-');
                separator = true;
            }
        }
        while (!result.isEmpty() && result.charAt(result.length() - 1) == '-') {
            result.deleteCharAt(result.length() - 1);
        }
        return result.isEmpty() ? "blueprint" : result.toString();
    }

    private static MoldingBlueprint readPath(Path path) throws BlueprintException {
        try {
            long size = Files.size(path);
            if (size < 1L || size > MoldingBlueprintCodec.MAX_TEXT_BYTES) {
                throw new BlueprintException("file_too_large", "Shared blueprint has an invalid size");
            }
            return MoldingBlueprintCodec.decode(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw ioFailure("blueprint_read_failed", path, exception);
        }
    }

    private static String uniqueFileId(Path root, String name) throws BlueprintException {
        String stem = safeStem(name);
        for (int suffix = 1; suffix <= MAX_LIBRARY_FILES + 1; suffix++) {
            String candidate = suffix == 1 ? stem + ".json" : stem + '-' + suffix + ".json";
            Path path = resolveFile(root, candidate);
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return candidate;
        }
        throw new BlueprintException("library_full", "No non-conflicting blueprint filename is available");
    }

    private static void requireCapacity(Path root) throws BlueprintException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "*.json")) {
            int count = 0;
            for (Path path : stream) {
                if (!isSafeFileId(path.getFileName().toString())
                    || Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (++count >= MAX_LIBRARY_FILES) {
                    throw new BlueprintException("library_full", "Shared blueprint library contains 512 files");
                }
            }
        } catch (IOException exception) {
            throw ioFailure("library_read_failed", root, exception);
        }
    }

    private static String uniqueDisplayName(MinecraftServer server, String preferred) throws BlueprintException {
        Set<String> names = new HashSet<>();
        Path root = libraryRoot(server);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "*.json")) {
            int visited = 0;
            for (Path path : stream) {
                if (!isSafeFileId(path.getFileName().toString())
                    || Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (++visited > MAX_LIBRARY_FILES) break;
                try {
                    names.add(readPath(path).name().toLowerCase(Locale.ROOT));
                } catch (BlueprintException ignored) {
                }
            }
        } catch (IOException exception) {
            throw ioFailure("library_read_failed", root, exception);
        }
        String base = preferred.length() > 58 ? preferred.substring(0, 58) : preferred;
        for (int suffix = 1; suffix <= MAX_LIBRARY_FILES + 1; suffix++) {
            String candidate = suffix == 1 ? base : trimForSuffix(base, suffix);
            if (!names.contains(candidate.toLowerCase(Locale.ROOT))) return candidate;
        }
        throw new BlueprintException("library_full", "No non-conflicting blueprint name is available");
    }

    private static String trimForSuffix(String base, int suffix) {
        String tail = " " + suffix;
        return base.substring(0, Math.min(base.length(), 64 - tail.length())) + tail;
    }

    private static Path resolveFile(Path root, String fileId) throws BlueprintException {
        if (!isSafeFileId(fileId)) throw new BlueprintException("unsafe_filename", "Invalid blueprint filename");
        Path path = root.resolve(fileId).toAbsolutePath().normalize();
        if (!path.getParent().equals(root.toAbsolutePath().normalize())) {
            throw new BlueprintException("unsafe_path", "Blueprint path escapes its library");
        }
        if (Files.isSymbolicLink(path)) {
            throw new BlueprintException("unsafe_symlink", "Blueprint path is a symbolic link");
        }
        return path;
    }

    private static Path ensureOwnedDirectory(MinecraftServer server, String child) throws BlueprintException {
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path base = world.resolve(AnvilcraftPlasticraft.MOD_ID);
        Path directory = base.resolve(child);
        try {
            createCheckedDirectory(base);
            createCheckedDirectory(directory);
            Path realWorld = world.toRealPath();
            Path realDirectory = directory.toRealPath();
            if (!realDirectory.startsWith(realWorld)) {
                throw new BlueprintException("unsafe_symlink", "Blueprint directory escapes the world directory");
            }
            return directory;
        } catch (IOException exception) {
            throw ioFailure("library_directory_failed", directory, exception);
        }
    }

    private static void createCheckedDirectory(Path directory) throws IOException, BlueprintException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new BlueprintException("unsafe_symlink", "Blueprint directory is not a real directory");
            }
            return;
        }
        Files.createDirectory(directory);
        if (Files.isSymbolicLink(directory)) {
            throw new BlueprintException("unsafe_symlink", "Blueprint directory is a symbolic link");
        }
    }

    private static void writeAtomic(Path target, String text, boolean replace) throws BlueprintException {
        Path root = target.getParent();
        Path temporary = null;
        try {
            temporary = Files.createTempFile(root, ".blueprint-", ".tmp");
            if (Files.isSymbolicLink(temporary)) {
                throw new BlueprintException("unsafe_symlink", "Temporary blueprint path is a symbolic link");
            }
            Files.writeString(
                temporary,
                text,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
            if (replace) {
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } else {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            }
            temporary = null;
        } catch (AtomicMoveNotSupportedException exception) {
            throw new BlueprintException("atomic_write_unsupported", "Filesystem does not support atomic blueprint writes", exception);
        } catch (IOException exception) {
            throw ioFailure("blueprint_write_failed", target, exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static List<String> readPins(MinecraftServer server, UUID playerId) throws BlueprintException {
        Path path = preferencePath(server, playerId);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new BlueprintException("unsafe_symlink", "Blueprint preference path is not a regular file");
        }
        try {
            if (Files.size(path) > MAX_PREFERENCE_BYTES) {
                throw new BlueprintException("preferences_too_large", "Blueprint preferences exceed 64 KiB");
            }
            JsonObject root = MoldingBlueprintCodec.parseObject(Files.readString(path, StandardCharsets.UTF_8));
            if (!root.has("version") || root.get("version").getAsInt() != 1) return List.of();
            JsonArray values = root.has("pins") ? root.getAsJsonArray("pins") : new JsonArray();
            List<String> pins = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (JsonElement value : values) {
                if (pins.size() >= MAX_LIBRARY_FILES) break;
                String fileId = value.getAsString();
                if (isSafeFileId(fileId) && seen.add(fileId)) pins.add(fileId);
            }
            return List.copyOf(pins);
        } catch (IOException | IllegalArgumentException exception) {
            throw new BlueprintException("preferences_read_failed", exception.getMessage(), exception);
        }
    }

    private static void writePins(MinecraftServer server, UUID playerId, List<String> pins)
        throws BlueprintException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray values = new JsonArray();
        pins.stream().filter(MoldingBlueprintLibrary::isSafeFileId).limit(MAX_LIBRARY_FILES).forEach(values::add);
        root.add("pins", values);
        writeAtomic(preferencePath(server, playerId), root.toString() + "\n", true);
    }

    private static Path preferencePath(MinecraftServer server, UUID playerId) throws BlueprintException {
        Path root = ensureOwnedDirectory(server, PREFERENCE_DIRECTORY);
        Path path = root.resolve(playerId + ".json").toAbsolutePath().normalize();
        if (!path.getParent().equals(root.toAbsolutePath().normalize()) || Files.isSymbolicLink(path)) {
            throw new BlueprintException("unsafe_path", "Invalid blueprint preference path");
        }
        return path;
    }

    private static MinecraftServer requireServer(ServerPlayer player) throws BlueprintException {
        MinecraftServer server = player.getServer();
        if (server == null) throw new BlueprintException("server_unavailable", "Server is unavailable");
        return server;
    }

    private static void requireManagePermission(ServerPlayer player, MoldingBlueprint blueprint)
        throws BlueprintException {
        if (!blueprint.ownerId().equals(player.getUUID()) && !player.hasPermissions(2)) {
            throw new BlueprintException("permission_denied", "Only the blueprint owner or an administrator may change it");
        }
    }

    private static BlueprintException ioFailure(String reason, Path path, IOException exception) {
        return new BlueprintException(reason, path.getFileName() + ": " + exception.getMessage(), exception);
    }

    public record StoredBlueprint(String fileId, MoldingBlueprint blueprint) {
    }

    public record EditableBlueprint(String name, EditableMoldingModel model) {
    }
}
