package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.network.BlueprintImportResultPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端上传会话:每名玩家同一时间只保留一个分块上传,全部到齐后解析并写入主手磁盘。
 * 文件名与大小上限在接收阶段校验,解析失败不会留下半成品组件。
 */
public final class BlueprintUploadTracker {
    /** 上传文件的字节上限。 */
    public static final int MAX_UPLOAD_BYTES = 8 * 1024 * 1024;
    /** 上传文件名长度上限。 */
    public static final int MAX_FILE_NAME_LENGTH = 128;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private BlueprintUploadTracker() {
    }

    private record Session(UUID sessionId, String fileName, byte[][] chunks, int totalBytes) {
    }

    public static void accept(
        ServerPlayer player,
        UUID sessionId,
        String fileName,
        int chunkIndex,
        int totalChunks,
        int totalBytes,
        byte[] bytes
    ) {
        if (fileName.length() > MAX_FILE_NAME_LENGTH
            || totalBytes <= 0
            || totalBytes > MAX_UPLOAD_BYTES
            || totalChunks <= 0
            || chunkIndex < 0
            || chunkIndex >= totalChunks) {
            SESSIONS.remove(player.getUUID());
            BlueprintImportResultPacket.sendFailure(
                player,
                new ConstructionBlueprintException("upload_rejected", fileName)
            );
            return;
        }
        Session session = SESSIONS.compute(player.getUUID(), (ignored, existing) ->
            existing != null && existing.sessionId().equals(sessionId)
                ? existing
                : new Session(sessionId, fileName, new byte[totalChunks][], totalBytes));
        if (session.chunks().length != totalChunks || !session.fileName().equals(fileName)) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        session.chunks()[chunkIndex] = bytes;
        for (byte[] chunk : session.chunks()) {
            if (chunk == null) return;
        }
        SESSIONS.remove(player.getUUID());
        finish(player, session);
    }

    private static void finish(ServerPlayer player, Session session) {
        try {
            byte[] assembled = assemble(session);
            CompoundTag tag = readStructureNbt(assembled);
            BlueprintSource source = sourceOf(session.fileName());
            List<StructureSnapshotCodec.BlueprintWarning> conversionWarnings = List.of();
            if (source == BlueprintSource.LITEMATICA_FILE) {
                LitematicaImporter.ConvertedStructure converted = LitematicaImporter.convert(tag);
                tag = converted.structureTag();
                conversionWarnings = converted.warnings();
            }
            ItemStack disk = player.getItemInHand(InteractionHand.MAIN_HAND);
            String name = stripExtension(session.fileName());
            ConstructionBlueprintService.ImportResult result = ConstructionBlueprintService.importIntoDisk(
                player,
                disk,
                tag,
                name,
                source
            );
            List<StructureSnapshotCodec.BlueprintWarning> allWarnings = new ArrayList<>(conversionWarnings);
            allWarnings.addAll(result.warnings());
            BlueprintImportResultPacket.sendSuccess(player, "imported", result.data().name(), allWarnings);
        } catch (ConstructionBlueprintException exception) {
            BlueprintImportResultPacket.sendFailure(player, exception);
        }
    }

    private static byte[] assemble(Session session) throws ConstructionBlueprintException {
        byte[] assembled = new byte[session.totalBytes()];
        int offset = 0;
        for (byte[] chunk : session.chunks()) {
            if (offset + chunk.length > assembled.length) {
                throw new ConstructionBlueprintException("upload_rejected", session.fileName());
            }
            System.arraycopy(chunk, 0, assembled, offset, chunk.length);
            offset += chunk.length;
        }
        if (offset != assembled.length) {
            throw new ConstructionBlueprintException("upload_rejected", session.fileName());
        }
        return assembled;
    }

    private static CompoundTag readStructureNbt(byte[] bytes) throws ConstructionBlueprintException {
        try {
            return NbtIo.readCompressed(
                new ByteArrayInputStream(bytes),
                NbtAccounter.create(ConstructionStructureLibrary.MAX_FILE_NBT_BYTES)
            );
        } catch (IOException exception) {
            throw new ConstructionBlueprintException("corrupt_file", exception.getMessage(), exception);
        }
    }

    /** 按扩展名识别文件格式;Create 蓝图就是原版结构 .nbt,走同一路径。 */
    private static BlueprintSource sourceOf(String fileName) throws ConstructionBlueprintException {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".nbt")) return BlueprintSource.VANILLA_FILE;
        if (lower.endsWith(".litematic")) return BlueprintSource.LITEMATICA_FILE;
        throw new ConstructionBlueprintException("unsupported_format", fileName);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    public static void clear(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }
}
