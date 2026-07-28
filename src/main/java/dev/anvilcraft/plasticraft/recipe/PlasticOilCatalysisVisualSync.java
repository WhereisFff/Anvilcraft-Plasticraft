package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.network.PlasticOilCatalysisSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** 限流并分发塑料油催化的客户端视觉进度。 */
public final class PlasticOilCatalysisVisualSync {
    private static final int MIN_PERIODIC_SYNC_TICKS = 2;
    private static final int MAX_PERIODIC_SYNC_TICKS = 20;
    private static final float MAX_PROGRESS_PER_SYNC = 0.025F;
    private static final float RATE_EPSILON = 1.0E-7F;
    private static final float CORRECTION_EPSILON = 0.005F;
    private static final Map<ServerLevel, Map<Long, LastSync>> LAST_SYNCS = new WeakHashMap<>();

    private PlasticOilCatalysisVisualSync() {
    }

    public static void update(ServerLevel level, BlockPos pos, double work, double workPerTick) {
        float progress = normalize(work);
        float rate = normalize(workPerTick);
        if (progress <= 0.0F && rate <= 0.0F) return;

        long gameTime = level.getGameTime();
        Map<Long, LastSync> levelSyncs = LAST_SYNCS.computeIfAbsent(level, ignored -> new HashMap<>());
        long key = pos.asLong();
        LastSync previous = levelSyncs.get(key);
        boolean shouldSend = previous == null;
        if (previous != null) {
            float expected = Mth.clamp(
                previous.progress() + previous.rate() * (gameTime - previous.gameTime()),
                0.0F,
                1.0F
            );
            shouldSend = Math.abs(previous.rate() - rate) > RATE_EPSILON
                || Math.abs(expected - progress) > CORRECTION_EPSILON
                || gameTime - previous.gameTime() >= syncInterval(rate);
        }
        if (!shouldSend) return;

        PlasticOilCatalysisSyncPacket packet = packet(
            pos,
            progress,
            rate,
            gameTime,
            PlasticOilCatalysisSyncPacket.UPDATE
        );
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos), packet);
        levelSyncs.put(key, new LastSync(progress, rate, gameTime));
    }

    public static void complete(ServerLevel level, BlockPos pos) {
        removeLastSync(level, pos);
        PacketDistributor.sendToPlayersTrackingChunk(
            level,
            new ChunkPos(pos),
            packet(pos, 1.0F, 0.0F, level.getGameTime(), PlasticOilCatalysisSyncPacket.COMPLETE)
        );
    }

    public static void clear(ServerLevel level, BlockPos pos) {
        clear(level, pos, false);
    }

    public static void clear(ServerLevel level, BlockPos pos, boolean force) {
        boolean tracked = removeLastSync(level, pos);
        if (!tracked && !force) return;
        PacketDistributor.sendToPlayersTrackingChunk(
            level,
            new ChunkPos(pos),
            packet(pos, 0.0F, 0.0F, level.getGameTime(), PlasticOilCatalysisSyncPacket.CLEAR)
        );
    }

    public static void sendToPlayer(ServerLevel level, ServerPlayer player, BlockPos pos, double work) {
        PacketDistributor.sendToPlayer(
            player,
            packet(pos, normalize(work), 0.0F, level.getGameTime(), PlasticOilCatalysisSyncPacket.UPDATE)
        );
    }

    private static PlasticOilCatalysisSyncPacket packet(
        BlockPos pos,
        float progress,
        float rate,
        long gameTime,
        int mode
    ) {
        return new PlasticOilCatalysisSyncPacket(pos.immutable(), progress, rate, gameTime, mode);
    }

    private static float normalize(double work) {
        return (float) Math.clamp(work / PlasticOilCatalysis.BASE_WORK, 0.0D, 1.0D);
    }

    private static int syncInterval(float rate) {
        if (rate <= RATE_EPSILON) return MAX_PERIODIC_SYNC_TICKS;
        return Mth.clamp(
            Mth.ceil(MAX_PROGRESS_PER_SYNC / rate),
            MIN_PERIODIC_SYNC_TICKS,
            MAX_PERIODIC_SYNC_TICKS
        );
    }

    private static boolean removeLastSync(ServerLevel level, BlockPos pos) {
        Map<Long, LastSync> levelSyncs = LAST_SYNCS.get(level);
        if (levelSyncs == null) return false;
        boolean removed = levelSyncs.remove(pos.asLong()) != null;
        if (levelSyncs.isEmpty()) LAST_SYNCS.remove(level);
        return removed;
    }

    private record LastSync(float progress, float rate, long gameTime) {
    }
}
