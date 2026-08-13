package dev.anvilcraft.plasticraft.client.blueprint;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintException;
import dev.anvilcraft.plasticraft.blueprint.ConstructionStructureLibrary;
import dev.anvilcraft.plasticraft.blueprint.StructureSnapshot;
import dev.anvilcraft.plasticraft.blueprint.StructureSnapshotCodec;
import dev.anvilcraft.plasticraft.network.BlueprintSnapshotRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端结构快照缓存:按内容哈希保存,缺失时向服务端请求分块传输。
 * 同一哈希在分块未齐前不重复请求;若服务端静默拒绝,约一秒后重试。
 */
public final class ClientBlueprintSnapshotCache {
    private static final int MAX_CACHED_SNAPSHOTS = 8;
    /** 未收到任何分块时的重试间隔(游戏刻)。 */
    private static final int RETRY_INTERVAL_TICKS = 20;

    private static final Map<String, StructureSnapshot> SNAPSHOTS = new LinkedHashMap<>();
    private static final Map<String, byte[][]> PENDING_CHUNKS = new HashMap<>();
    private static final Map<String, Long> LAST_REQUEST_TICK = new HashMap<>();

    private ClientBlueprintSnapshotCache() {
    }

    /** 取缓存的快照;缺失时发起一次请求并返回 null,内容到齐后自然可用。 */
    @Nullable
    public static synchronized StructureSnapshot snapshotOrRequest(String hash) {
        StructureSnapshot snapshot = SNAPSHOTS.get(hash);
        if (snapshot != null) {
            SNAPSHOTS.remove(hash);
            SNAPSHOTS.put(hash, snapshot);
            return snapshot;
        }
        if (!ConstructionStructureLibrary.isValidHash(hash)) return null;
        if (PENDING_CHUNKS.containsKey(hash)) return null;
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        Long last = LAST_REQUEST_TICK.get(hash);
        if (last != null && now - last < RETRY_INTERVAL_TICKS) return null;
        LAST_REQUEST_TICK.put(hash, now);
        PacketDistributor.sendToServer(new BlueprintSnapshotRequestPacket(hash));
        return null;
    }

    public static synchronized void acceptChunk(
        String hash,
        int chunkIndex,
        int totalChunks,
        int totalBytes,
        byte[] bytes
    ) {
        if (!ConstructionStructureLibrary.isValidHash(hash)) return;
        if (totalChunks <= 0 || chunkIndex < 0 || chunkIndex >= totalChunks) return;
        if (totalBytes <= 0 || totalBytes > ConstructionStructureLibrary.MAX_FILE_NBT_BYTES) return;
        byte[][] chunks = PENDING_CHUNKS.computeIfAbsent(hash, ignored -> new byte[totalChunks][]);
        if (chunks.length != totalChunks) return;
        chunks[chunkIndex] = bytes;
        for (byte[] chunk : chunks) {
            if (chunk == null) return;
        }
        PENDING_CHUNKS.remove(hash);
        LAST_REQUEST_TICK.remove(hash);
        assemble(hash, chunks, totalBytes);
    }

    private static void assemble(String hash, byte[][] chunks, int totalBytes) {
        byte[] assembled = new byte[totalBytes];
        int offset = 0;
        for (byte[] chunk : chunks) {
            if (offset + chunk.length > totalBytes) return;
            System.arraycopy(chunk, 0, assembled, offset, chunk.length);
            offset += chunk.length;
        }
        if (offset != totalBytes) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        try {
            CompoundTag tag = NbtIo.readCompressed(
                new ByteArrayInputStream(assembled),
                NbtAccounter.create(ConstructionStructureLibrary.MAX_FILE_NBT_BYTES)
            );
            StructureSnapshotCodec.ParsedSnapshot parsed = StructureSnapshotCodec.parse(
                tag,
                minecraft.level.registryAccess()
            );
            SNAPSHOTS.put(hash, parsed.snapshot());
            while (SNAPSHOTS.size() > MAX_CACHED_SNAPSHOTS) {
                SNAPSHOTS.remove(SNAPSHOTS.keySet().iterator().next());
            }
        } catch (IOException | ConstructionBlueprintException exception) {
            AnvilcraftPlasticraft.LOGGER.warn("Failed to decode blueprint snapshot {}: {}", hash, exception.getMessage());
        }
    }

    public static synchronized void clear() {
        SNAPSHOTS.clear();
        PENDING_CHUNKS.clear();
        LAST_REQUEST_TICK.clear();
    }
}
