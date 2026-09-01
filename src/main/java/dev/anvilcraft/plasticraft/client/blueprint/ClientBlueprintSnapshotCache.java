package dev.anvilcraft.plasticraft.client.blueprint;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintException;
import dev.anvilcraft.plasticraft.blueprint.ConstructionStructureLibrary;
import dev.anvilcraft.plasticraft.blueprint.StructureSnapshot;
import dev.anvilcraft.plasticraft.blueprint.StructureSnapshotCodec;
import dev.anvilcraft.plasticraft.network.BlueprintSnapshotChunkPacket;
import dev.anvilcraft.plasticraft.network.BlueprintSnapshotRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 客户端结构快照缓存,按内容哈希保存,缺失时向服务端请求分块传输。
 * 分块收齐后在专用工作线程完成解压与解析,客户端线程只负责原子发布解析结果。
 */
public final class ClientBlueprintSnapshotCache {
    private static final int MAX_CACHED_SNAPSHOTS = 8;
    private static final long MAX_CACHED_ESTIMATED_BYTES = 256L * 1024L * 1024L;
    /** 未收到新分块时的重试间隔,游戏刻。 */
    private static final int RETRY_INTERVAL_TICKS = 20;
    /** 部分下载长时间没有进展后允许重新请求,游戏刻。 */
    private static final int DOWNLOAD_TIMEOUT_TICKS = 200;
    private static final int MAX_QUEUED_DECODES = 2;

    private static final Object LOCK = new Object();
    private static final Map<String, CachedSnapshot> SNAPSHOTS = new LinkedHashMap<>(16, 0.75F, true);
    private static final Map<String, PendingDownload> PENDING_DOWNLOADS = new HashMap<>();
    private static final Map<String, DecodeTask> DECODING = new HashMap<>();
    private static final Map<String, Long> LAST_REQUEST_TICK = new HashMap<>();
    private static final ThreadPoolExecutor DECODE_EXECUTOR = createDecodeExecutor();

    private static long cacheGeneration;
    private static long cachedEstimatedBytes;

    private ClientBlueprintSnapshotCache() {
    }

    @Nullable
    public static StructureSnapshot cached(String hash) {
        synchronized (LOCK) {
            CachedSnapshot cached = SNAPSHOTS.get(hash);
            return cached == null ? null : cached.snapshot();
        }
    }

    /** 取缓存的快照;缺失时发起一次请求并返回 null,内容解析完成后自然可用。 */
    @Nullable
    public static StructureSnapshot snapshotOrRequest(String hash) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        synchronized (LOCK) {
            CachedSnapshot cached = SNAPSHOTS.get(hash);
            if (cached != null) return cached.snapshot();
            if (!ConstructionStructureLibrary.isValidHash(hash) || minecraft.level == null) return null;
            if (DECODING.containsKey(hash)) return null;

            PendingDownload pending = PENDING_DOWNLOADS.get(hash);
            if (pending != null && !pending.hasTimedOut(now)) return null;
            if (pending != null) PENDING_DOWNLOADS.remove(hash);

            Long last = LAST_REQUEST_TICK.get(hash);
            if (last != null && now >= last && now - last < RETRY_INTERVAL_TICKS) return null;
            LAST_REQUEST_TICK.put(hash, now);
        }
        PacketDistributor.sendToServer(new BlueprintSnapshotRequestPacket(hash));
        return null;
    }

    public static void acceptChunk(
        String hash,
        int chunkIndex,
        int totalChunks,
        int totalBytes,
        byte[] bytes
    ) {
        if (!validChunk(hash, chunkIndex, totalChunks, totalBytes, bytes)) return;
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        DecodeTask completed = null;
        synchronized (LOCK) {
            if (SNAPSHOTS.containsKey(hash) || DECODING.containsKey(hash)) return;
            PendingDownload pending = PENDING_DOWNLOADS.get(hash);
            if (pending == null) {
                pending = new PendingDownload(totalChunks, totalBytes, now);
                PENDING_DOWNLOADS.put(hash, pending);
            } else if (!pending.matches(totalChunks, totalBytes)) {
                return;
            }
            if (!pending.accept(chunkIndex, bytes, now)) return;
            if (pending.isComplete()) {
                PENDING_DOWNLOADS.remove(hash);
                LAST_REQUEST_TICK.remove(hash);
                completed = new DecodeTask(hash, cacheGeneration, pending.chunks());
                DECODING.put(hash, completed);
            }
        }
        if (completed != null) scheduleDecode(completed, minecraft);
    }

    private static boolean validChunk(
        String hash,
        int chunkIndex,
        int totalChunks,
        int totalBytes,
        byte[] bytes
    ) {
        if (!ConstructionStructureLibrary.isValidHash(hash) || bytes == null) return false;
        if (totalBytes <= 0 || totalBytes > ConstructionStructureLibrary.MAX_FILE_NBT_BYTES) return false;
        int chunkBytes = BlueprintSnapshotChunkPacket.CHUNK_BYTES;
        int expectedChunks = Math.ceilDiv(totalBytes, chunkBytes);
        if (totalChunks != expectedChunks || chunkIndex < 0 || chunkIndex >= totalChunks) return false;
        int expectedBytes = Math.min(chunkBytes, totalBytes - chunkIndex * chunkBytes);
        return bytes.length == expectedBytes;
    }

    private static void scheduleDecode(DecodeTask task, Minecraft minecraft) {
        try {
            minecraft.execute(() -> beginDecode(task, minecraft));
        } catch (RuntimeException exception) {
            abandonDecodeOffThread(task, exception);
        }
    }

    private static void beginDecode(DecodeTask task, Minecraft minecraft) {
        if (minecraft.level == null) {
            abandonDecode(task, null);
            return;
        }
        // 连接建立后注册表已冻结,后台阶段仅进行只读查找;缓存发布仍回到客户端线程。
        HolderLookup.Provider registries = minecraft.level.registryAccess();
        RejectedExecutionException rejection = null;
        synchronized (LOCK) {
            if (!isCurrent(task)) return;
            try {
                task.future = DECODE_EXECUTOR.submit(() -> decode(task, registries, minecraft));
            } catch (RejectedExecutionException exception) {
                rejection = exception;
            }
        }
        if (rejection != null) abandonDecode(task, rejection);
    }

    private static void decode(DecodeTask task, HolderLookup.Provider registries, Minecraft minecraft) {
        DecodeResult result;
        try (InputStream input = new ChunkInputStream(task.chunks)) {
            CompoundTag tag = NbtIo.readCompressed(
                input,
                NbtAccounter.create(ConstructionStructureLibrary.MAX_FILE_NBT_BYTES)
            );
            ChunkInputStream.checkCancelled();
            StructureSnapshot snapshot = StructureSnapshotCodec.parse(tag, registries).snapshot();
            ChunkInputStream.checkCancelled();
            result = DecodeResult.success(snapshot, estimateRetainedBytes(snapshot));
        } catch (IOException | ConstructionBlueprintException | RuntimeException exception) {
            result = DecodeResult.failure(exception);
        }
        DecodeResult completed = result;
        try {
            minecraft.execute(() -> publishDecode(task, completed));
        } catch (RuntimeException exception) {
            abandonDecodeOffThread(task, exception);
        }
    }

    private static void publishDecode(DecodeTask task, DecodeResult result) {
        boolean published = false;
        List<String> invalidatedHashes = List.of();
        synchronized (LOCK) {
            if (!isCurrent(task)) return;
            DECODING.remove(task.hash);
            if (result.snapshot() != null) {
                invalidatedHashes = putSnapshot(task.hash, result.snapshot(), result.estimatedBytes());
                LAST_REQUEST_TICK.remove(task.hash);
                published = true;
            } else {
                LAST_REQUEST_TICK.put(task.hash, clientTick());
            }
        }
        for (String invalidatedHash : invalidatedHashes) {
            ClientConstructionOverlayLookup.invalidateHash(invalidatedHash);
        }
        if (!published && result.failure() != null) {
            AnvilcraftPlasticraft.LOGGER.warn(
                "Failed to decode blueprint snapshot {}: {}",
                task.hash,
                result.failure().getMessage()
            );
        }
    }

    private static void abandonDecode(DecodeTask task, @Nullable RuntimeException failure) {
        boolean abandoned;
        synchronized (LOCK) {
            abandoned = isCurrent(task);
            if (abandoned) {
                DECODING.remove(task.hash);
                LAST_REQUEST_TICK.put(task.hash, clientTick());
            }
        }
        if (abandoned && failure != null) {
            AnvilcraftPlasticraft.LOGGER.debug("Blueprint snapshot decode queue is full for {}", task.hash);
        }
    }

    private static void abandonDecodeOffThread(DecodeTask task, RuntimeException failure) {
        boolean abandoned;
        synchronized (LOCK) {
            abandoned = isCurrent(task);
            if (abandoned) DECODING.remove(task.hash);
        }
        if (abandoned) {
            AnvilcraftPlasticraft.LOGGER.warn(
                "Failed to publish decoded blueprint snapshot {}: {}",
                task.hash,
                failure.getMessage()
            );
        }
    }

    private static boolean isCurrent(DecodeTask task) {
        return task.generation == cacheGeneration && DECODING.get(task.hash) == task;
    }

    private static List<String> putSnapshot(String hash, StructureSnapshot snapshot, long estimatedBytes) {
        List<String> invalidatedHashes = new ArrayList<>();
        CachedSnapshot replaced = SNAPSHOTS.remove(hash);
        if (replaced != null) {
            cachedEstimatedBytes -= replaced.estimatedBytes();
            invalidatedHashes.add(hash);
        }
        SNAPSHOTS.put(hash, new CachedSnapshot(snapshot, estimatedBytes));
        cachedEstimatedBytes = saturatedAdd(cachedEstimatedBytes, estimatedBytes);
        while (SNAPSHOTS.size() > 1
               && (SNAPSHOTS.size() > MAX_CACHED_SNAPSHOTS
                   || cachedEstimatedBytes > MAX_CACHED_ESTIMATED_BYTES)) {
            String eldestHash = SNAPSHOTS.keySet().iterator().next();
            CachedSnapshot removed = SNAPSHOTS.remove(eldestHash);
            if (removed != null) {
                cachedEstimatedBytes -= removed.estimatedBytes();
                invalidatedHashes.add(eldestHash);
            }
        }
        return invalidatedHashes;
    }

    private static long estimateRetainedBytes(StructureSnapshot snapshot) {
        long estimate = 1_024L;
        estimate = saturatedAdd(estimate, (long) snapshot.palette().size() * 16L);
        estimate = saturatedAdd(estimate, (long) snapshot.blocks().size() * 88L);
        estimate = saturatedAdd(estimate, (long) snapshot.entities().size() * 144L);
        for (StructureSnapshot.BlockEntry entry : snapshot.blocks()) {
            if (entry.nbt().isPresent()) {
                estimate = saturatedAdd(estimate, Math.max(64L, (long) entry.nbt().get().sizeInBytes() * 2L));
            }
        }
        for (StructureSnapshot.EntityEntry entry : snapshot.entities()) {
            estimate = saturatedAdd(estimate, Math.max(64L, (long) entry.nbt().sizeInBytes() * 2L));
        }
        return estimate;
    }

    private static long saturatedAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static long clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }

    private static ThreadPoolExecutor createDecodeExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1,
            1,
            30L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(MAX_QUEUED_DECODES),
            runnable -> {
                Thread thread = new Thread(runnable, "plasticraft-blueprint-snapshot-decode");
                thread.setDaemon(true);
                thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
                return thread;
            }
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    public static void clear() {
        List<Future<?>> cancelled = new ArrayList<>();
        synchronized (LOCK) {
            cacheGeneration++;
            for (DecodeTask task : DECODING.values()) {
                if (task.future != null) cancelled.add(task.future);
            }
            SNAPSHOTS.clear();
            PENDING_DOWNLOADS.clear();
            DECODING.clear();
            LAST_REQUEST_TICK.clear();
            cachedEstimatedBytes = 0L;
        }
        for (Future<?> future : cancelled) future.cancel(true);
        DECODE_EXECUTOR.purge();
        ClientConstructionOverlayLookup.clear();
    }

    private record CachedSnapshot(StructureSnapshot snapshot, long estimatedBytes) {
    }

    private static final class PendingDownload {
        private final byte[][] chunks;
        private final int totalBytes;
        private int receivedChunks;
        private int receivedBytes;
        private long lastChunkTick;

        private PendingDownload(int totalChunks, int totalBytes, long now) {
            this.chunks = new byte[totalChunks][];
            this.totalBytes = totalBytes;
            this.lastChunkTick = now;
        }

        private boolean matches(int totalChunks, int bytes) {
            return this.chunks.length == totalChunks && this.totalBytes == bytes;
        }

        private boolean accept(int chunkIndex, byte[] bytes, long now) {
            if (this.chunks[chunkIndex] != null) return false;
            this.chunks[chunkIndex] = bytes;
            this.receivedChunks++;
            this.receivedBytes += bytes.length;
            this.lastChunkTick = now;
            return true;
        }

        private boolean isComplete() {
            return this.receivedChunks == this.chunks.length && this.receivedBytes == this.totalBytes;
        }

        private boolean hasTimedOut(long now) {
            return now < this.lastChunkTick || now - this.lastChunkTick > DOWNLOAD_TIMEOUT_TICKS;
        }

        private byte[][] chunks() {
            return this.chunks;
        }
    }

    private static final class DecodeTask {
        private final String hash;
        private final long generation;
        private final byte[][] chunks;
        @Nullable
        private Future<?> future;

        private DecodeTask(String hash, long generation, byte[][] chunks) {
            this.hash = hash;
            this.generation = generation;
            this.chunks = chunks;
        }
    }

    private record DecodeResult(
        @Nullable StructureSnapshot snapshot,
        long estimatedBytes,
        @Nullable Exception failure
    ) {
        private static DecodeResult success(StructureSnapshot snapshot, long estimatedBytes) {
            return new DecodeResult(snapshot, estimatedBytes, null);
        }

        private static DecodeResult failure(Exception failure) {
            return new DecodeResult(null, 0L, failure);
        }
    }

    private static final class ChunkInputStream extends InputStream {
        private final byte[][] chunks;
        private int chunkIndex;
        private int offset;

        private ChunkInputStream(byte[][] chunks) {
            this.chunks = chunks;
        }

        @Override
        public int read() throws IOException {
            checkCancelled();
            while (this.chunkIndex < this.chunks.length) {
                byte[] chunk = this.chunks[this.chunkIndex];
                if (this.offset < chunk.length) return chunk[this.offset++] & 0xFF;
                this.chunkIndex++;
                this.offset = 0;
            }
            return -1;
        }

        @Override
        public int read(byte[] destination, int destinationOffset, int length) throws IOException {
            checkCancelled();
            if (destinationOffset < 0 || length < 0 || length > destination.length - destinationOffset) {
                throw new IndexOutOfBoundsException();
            }
            if (length == 0) return 0;
            int copied = 0;
            while (copied < length && this.chunkIndex < this.chunks.length) {
                byte[] chunk = this.chunks[this.chunkIndex];
                int available = chunk.length - this.offset;
                if (available == 0) {
                    this.chunkIndex++;
                    this.offset = 0;
                    continue;
                }
                int toCopy = Math.min(length - copied, available);
                System.arraycopy(chunk, this.offset, destination, destinationOffset + copied, toCopy);
                this.offset += toCopy;
                copied += toCopy;
            }
            return copied == 0 ? -1 : copied;
        }

        private static void checkCancelled() throws InterruptedIOException {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Blueprint snapshot decode was cancelled");
            }
        }
    }
}
