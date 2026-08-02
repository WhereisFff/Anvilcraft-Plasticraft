package dev.anvilcraft.plasticraft.client.molding.blueprint;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.inventory.PlasticMoldingChamberMenu;
import dev.anvilcraft.plasticraft.molding.blueprint.BlueprintException;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprint;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintCodec;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintImporter;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintLibrary;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import dev.anvilcraft.plasticraft.network.MoldingBlueprintListRequestPacket;
import dev.anvilcraft.plasticraft.network.MoldingBlueprintUploadPacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** 只在客户端定位本地权威目录或创建远程只读副本。 */
public final class MoldingBlueprintClientFiles {
    private static final int MAX_SCANNED_FILES = MoldingBlueprintLibrary.MAX_LIBRARY_FILES + 128;
    private static final Pattern MANAGED_EXPORT = Pattern.compile(".+-r\\d+-[0-9a-f]{8}\\.json");
    @Nullable
    private static UploadContext uploadContext;

    private MoldingBlueprintClientFiles() {
    }

    public static void open(
        Player player,
        BlockPos chamberPos,
        UUID sessionId,
        String fileId,
        String text,
        boolean localAuthority,
        boolean openFolder
    ) {
        Path path = null;
        try {
            if (!fileId.isEmpty() && !MoldingBlueprintLibrary.isSafeFileId(fileId)) {
                throw new IOException("Unsafe blueprint filename");
            }
            Minecraft minecraft = Minecraft.getInstance();
            MinecraftServer integrated = minecraft.getSingleplayerServer();
            boolean authoritative = localAuthority && integrated != null;
            Path root;
            if (authoritative) {
                root = MoldingBlueprintLibrary.libraryRoot(integrated);
                path = fileId.isEmpty() ? root : checkedFile(root, fileId);
                if (!fileId.isEmpty() && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Authoritative blueprint no longer exists");
                }
            } else {
                root = clientExportRoot(minecraft);
                path = root;
                if (!fileId.isEmpty()) {
                    MoldingBlueprint blueprint = MoldingBlueprintCodec.decode(text);
                    String stem = fileId.substring(0, fileId.length() - ".json".length());
                    String exportedId = stem + "-r" + blueprint.fileRevision() + '-'
                        + blueprint.modelHash().substring(0, 8) + ".json";
                    path = checkedFile(root, exportedId);
                    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) writeReadOnly(path, text);
                }
            }
            UploadContext context = uploadContext;
            boolean sameContext = context != null
                && context.chamberPos.equals(chamberPos)
                && context.sessionId.equals(sessionId)
                && context.root.equals(root)
                && context.localAuthority == authoritative;
            if (!sameContext) uploadContext = new UploadContext(root, chamberPos, sessionId, authoritative);
            UploadContext active = uploadContext;
            if (openFolder) {
                minecraft.keyboardHandler.setClipboard(root.toAbsolutePath().toString());
                Util.getPlatform().openFile(root.toFile());
            } else {
                if (sameContext && active != null && active.pending != null) confirmPending(player, active);
                scan(player, active);
            }
        } catch (IOException | BlueprintException | RuntimeException exception) {
            String fallback = path == null ? "" : path.toAbsolutePath().toString();
            if (!fallback.isEmpty()) Minecraft.getInstance().keyboardHandler.setClipboard(fallback);
            PlasticMoldingChamberMenu.showTitleMessage(
                player,
                chamberPos,
                Component.translatable("message.anvilcraftplasticraft.molding.open_folder_failed")
                    .append(fallback.isEmpty() ? "" : ": " + fallback)
                    .append(exception.getMessage() == null ? "" : " (" + exception.getMessage() + ')')
            );
        }
    }

    public static void handleUploadResult(
        Player player,
        BlockPos chamberPos,
        UUID sessionId,
        String filename,
        long sourceSize,
        long sourceModifiedAt,
        boolean accepted
    ) {
        UploadContext context = uploadContext;
        if (context == null
            || !context.chamberPos.equals(chamberPos)
            || !context.sessionId.equals(sessionId)) {
            return;
        }
        try {
            Path path = checkedUploadFile(context.root, filename);
            FileStamp expected = new FileStamp(sourceSize, sourceModifiedAt);
            if (!expected.equals(context.inFlight.get(path))) return;
            context.inFlight.remove(path);
            if (!accepted
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !expected.equals(stamp(path))) {
                return;
            }
            Files.delete(path);
        } catch (IOException | BlueprintException exception) {
            PlasticMoldingChamberMenu.showTitleMessage(
                player,
                chamberPos,
                Component.translatable(
                    "message.anvilcraftplasticraft.molding.delete_imported_source_failed",
                    filename
                ).append(exception.getMessage() == null ? "" : ": " + exception.getMessage())
            );
        }
    }

    private static void scan(Player player, @Nullable UploadContext context) {
        if (context == null) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(context.root)) {
            int visited = 0;
            for (Path candidate : stream) {
                if (++visited > MAX_SCANNED_FILES) break;
                String filename = candidate.getFileName().toString();
                String lower = filename.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".json") && !lower.endsWith(".bbmodel")) continue;
                Path path = candidate.toAbsolutePath().normalize();
                if (!path.getParent().equals(context.root.toAbsolutePath().normalize())
                    || Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                FileStamp stamp = stamp(path);
                if (MANAGED_EXPORT.matcher(lower).matches()) {
                    continue;
                }
                if (stamp.size < 1L || stamp.size > MoldingBlueprintCodec.MAX_TEXT_BYTES) {
                    displayImportFailure(player, context.chamberPos, filename, "File exceeds the 1 MiB limit");
                    continue;
                }
                if (stamp.equals(context.inFlight.get(path))) continue;
                processCandidate(player, context, path, stamp);
            }
            if (context.listRefreshNeeded) {
                context.listRefreshNeeded = false;
                PacketDistributor.sendToServer(new MoldingBlueprintListRequestPacket(
                    context.chamberPos,
                    context.sessionId
                ));
            }
        } catch (IOException exception) {
            displayImportFailure(
                player,
                context.chamberPos,
                context.root.getFileName().toString(),
                exception.getMessage()
            );
        }
    }

    private static void processCandidate(
        Player player,
        UploadContext context,
        Path path,
        FileStamp stamp
    ) {
        String filename = path.getFileName().toString();
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            if (context.localAuthority && MoldingBlueprintCodec.parseObject(text).has("blueprint_format")) {
                MoldingBlueprintCodec.decode(text);
                context.listRefreshNeeded = true;
                return;
            }
            MoldingBlueprintImporter.ImportPreview preview = MoldingBlueprintImporter.inspect(filename, text);
            if (preview.translationRequired()) {
                if (context.pending == null
                    || !context.pending.path.equals(path)
                    || !context.pending.stamp.equals(stamp)) {
                    context.pending = new PendingImport(path, stamp, text, preview);
                    MoldingVec3 suggestion = preview.suggestedTranslation();
                    PlasticMoldingChamberMenu.showTitleMessage(
                        player,
                        context.chamberPos,
                        Component.translatable(
                            "message.anvilcraftplasticraft.molding.import_translation_prompt",
                            filename,
                            format(suggestion.x()),
                            format(suggestion.y()),
                            format(suggestion.z())
                        )
                    );
                }
                return;
            }
            sendUpload(context, path, stamp, text, false, MoldingVec3.ZERO);
        } catch (IOException | BlueprintException | RuntimeException exception) {
            displayImportFailure(player, context.chamberPos, filename, exception.getMessage());
        }
    }

    private static void confirmPending(Player player, UploadContext context) {
        PendingImport pending = context.pending;
        if (pending == null) return;
        try {
            if (!Files.isRegularFile(pending.path, LinkOption.NOFOLLOW_LINKS)
                || !pending.stamp.equals(stamp(pending.path))) {
                context.pending = null;
                return;
            }
            sendUpload(
                context,
                pending.path,
                pending.stamp,
                pending.text,
                true,
                pending.preview.suggestedTranslation()
            );
            context.pending = null;
        } catch (IOException exception) {
            context.pending = null;
            displayImportFailure(
                player,
                context.chamberPos,
                pending.path.getFileName().toString(),
                exception.getMessage()
            );
        }
    }

    private static void sendUpload(
        UploadContext context,
        Path path,
        FileStamp stamp,
        String text,
        boolean confirmed,
        MoldingVec3 translation
    ) {
        context.inFlight.put(path, stamp);
        PacketDistributor.sendToServer(new MoldingBlueprintUploadPacket(
            context.chamberPos,
            context.sessionId,
            path.getFileName().toString(),
            text,
            confirmed,
            translation.x(),
            translation.y(),
            translation.z(),
            stamp.size,
            stamp.modifiedAt
        ));
    }

    private static void displayImportFailure(
        Player player,
        BlockPos chamberPos,
        String filename,
        @Nullable String detail
    ) {
        PlasticMoldingChamberMenu.showTitleMessage(
            player,
            chamberPos,
            Component.translatable("message.anvilcraftplasticraft.molding.import_failed", filename)
                .append(detail == null || detail.isBlank() ? "" : ": " + detail)
        );
    }

    private static FileStamp stamp(Path path) throws IOException {
        return new FileStamp(Files.size(path), Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis());
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-7D) return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static Path clientExportRoot(Minecraft minecraft) throws IOException {
        Path game = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        Path base = game.resolve(AnvilcraftPlasticraft.MOD_ID);
        Path root = base.resolve("blueprints");
        createCheckedDirectory(base);
        createCheckedDirectory(root);
        if (!root.toRealPath().startsWith(game.toRealPath())) {
            throw new IOException("Client blueprint directory escapes the game directory");
        }
        return root;
    }

    private static Path checkedFile(Path root, String fileId) throws IOException {
        if (!MoldingBlueprintLibrary.isSafeFileId(fileId)) throw new IOException("Unsafe blueprint filename");
        Path path = root.resolve(fileId).toAbsolutePath().normalize();
        if (!path.getParent().equals(root.toAbsolutePath().normalize()) || Files.isSymbolicLink(path)) {
            throw new IOException("Blueprint path escapes its safe directory");
        }
        return path;
    }

    private static Path checkedUploadFile(Path root, String filename) throws IOException, BlueprintException {
        MoldingBlueprintImporter.validateUploadFilename(filename);
        Path path = root.resolve(filename).toAbsolutePath().normalize();
        if (!path.getParent().equals(root.toAbsolutePath().normalize()) || Files.isSymbolicLink(path)) {
            throw new IOException("Blueprint path escapes its safe directory");
        }
        return path;
    }

    private static void createCheckedDirectory(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Blueprint export path is not a real directory");
            }
            return;
        }
        Files.createDirectory(path);
        if (Files.isSymbolicLink(path)) throw new IOException("Blueprint export path is a symbolic link");
    }

    private static void writeReadOnly(Path target, String text) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), ".blueprint-export-", ".tmp");
        try {
            Files.writeString(
                temporary,
                text,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            temporary = null;
            target.toFile().setReadOnly();
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Filesystem does not support atomic blueprint exports", exception);
        } finally {
            if (temporary != null) Files.deleteIfExists(temporary);
        }
    }

    private static final class UploadContext {
        private final Path root;
        private final BlockPos chamberPos;
        private final UUID sessionId;
        private final boolean localAuthority;
        private final Map<Path, FileStamp> inFlight = new HashMap<>();
        private boolean listRefreshNeeded;
        @Nullable
        private PendingImport pending;

        private UploadContext(Path root, BlockPos chamberPos, UUID sessionId, boolean localAuthority) {
            this.root = root;
            this.chamberPos = chamberPos;
            this.sessionId = sessionId;
            this.localAuthority = localAuthority;
        }
    }

    private record FileStamp(long size, long modifiedAt) {
    }

    private record PendingImport(
        Path path,
        FileStamp stamp,
        String text,
        MoldingBlueprintImporter.ImportPreview preview
    ) {
    }
}
