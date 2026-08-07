package dev.anvilcraft.plasticraft.entity.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.ticks.TickPriority;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** 将移动支架的虚拟元件接入服务端原生计划刻与方块事件阶段。 */
public final class MoldedTrayRedstoneScheduler {
    private static final int BLOCK_EVENT_MARKER = 0x5054;
    private static final Map<ServerLevel, LevelSchedule> LEVELS = new IdentityHashMap<>();

    private MoldedTrayRedstoneScheduler() {
    }

    static void scheduleTick(
        MoldedTrayRedstoneRuntime runtime,
        int delay,
        TickPriority priority
    ) {
        if (delay <= 0) throw new IllegalArgumentException("Tray scheduled tick delay must be positive");
        ServerLevel level = runtime.serverLevel();
        PendingAction action;
        synchronized (LEVELS) {
            LevelSchedule schedule = LEVELS.computeIfAbsent(level, ignored -> new LevelSchedule());
            schedule.cancelCurrent(runtime);
            BlockPos markerPosition = schedule.allocate(level, runtime.host().blockPosition());
            action = PendingAction.scheduledTick(
                runtime,
                markerPosition,
                new ChunkPos(runtime.host().blockPosition()).toLong(),
                level.getGameTime() + delay,
                priority
            );
            schedule.add(runtime, action);
        }
        level.scheduleTick(action.markerPosition, Blocks.STRUCTURE_VOID, delay, priority);
    }

    static void scheduleBlockEvent(MoldedTrayRedstoneRuntime runtime, int componentEvent) {
        ServerLevel level = runtime.serverLevel();
        PendingAction action;
        synchronized (LEVELS) {
            LevelSchedule schedule = LEVELS.computeIfAbsent(level, ignored -> new LevelSchedule());
            schedule.cancelCurrent(runtime);
            BlockPos markerPosition = schedule.allocate(level, runtime.host().blockPosition());
            int token = schedule.nextEventToken++;
            action = PendingAction.blockEvent(
                runtime,
                markerPosition,
                new ChunkPos(runtime.host().blockPosition()).toLong(),
                componentEvent,
                token
            );
            schedule.add(runtime, action);
        }
        level.blockEvent(action.markerPosition, Blocks.STRUCTURE_VOID, BLOCK_EVENT_MARKER, action.token);
    }

    static void cancel(MoldedTrayRedstoneRuntime runtime) {
        synchronized (LEVELS) {
            LevelSchedule schedule = LEVELS.get(runtime.host().level());
            if (schedule != null) schedule.cancelCurrent(runtime);
        }
    }

    static int remainingTicks(MoldedTrayRedstoneRuntime runtime, int fallback) {
        synchronized (LEVELS) {
            LevelSchedule schedule = LEVELS.get(runtime.host().level());
            if (schedule == null) return fallback;
            PendingAction action = schedule.currentActions.get(runtime);
            if (action == null) return fallback;
            if (action.kind == ActionKind.BLOCK_EVENT) return 0;
            long remaining = action.triggerTick - runtime.host().level().getGameTime();
            return (int) Math.clamp(remaining, 0L, Integer.MAX_VALUE);
        }
    }

    static void relocateIfNeeded(MoldedTrayRedstoneRuntime runtime) {
        PendingAction action;
        int remaining = 0;
        synchronized (LEVELS) {
            LevelSchedule schedule = LEVELS.get(runtime.host().level());
            if (schedule == null) return;
            action = schedule.currentActions.get(runtime);
            if (action == null) return;
            long currentChunk = new ChunkPos(runtime.host().blockPosition()).toLong();
            if (currentChunk == action.hostChunk) return;
            if (action.kind == ActionKind.SCHEDULED_TICK) {
                remaining = (int) Math.clamp(
                    action.triggerTick - runtime.host().level().getGameTime(),
                    1L,
                    Integer.MAX_VALUE
                );
            }
            schedule.cancelCurrent(runtime);
        }
        if (action.kind == ActionKind.SCHEDULED_TICK) {
            scheduleTick(runtime, remaining, action.priority);
        } else {
            scheduleBlockEvent(runtime, action.componentEvent);
        }
    }

    public static boolean runScheduledTick(ServerLevel level, BlockPos markerPosition) {
        PendingAction action;
        synchronized (LEVELS) {
            LevelSchedule schedule = LEVELS.get(level);
            if (schedule == null) return false;
            action = schedule.remove(markerPosition, ActionKind.SCHEDULED_TICK, 0);
        }
        if (action == null) return false;
        MoldedTrayRedstoneRuntime runtime = action.runtime.get();
        if (!action.cancelled && runtime != null) runtime.runScheduledTick();
        return true;
    }

    public static boolean runBlockEvent(ServerLevel level, BlockEventData event) {
        if (event.paramA() != BLOCK_EVENT_MARKER) return false;
        PendingAction action;
        synchronized (LEVELS) {
            LevelSchedule schedule = LEVELS.get(level);
            if (schedule == null) return false;
            action = schedule.remove(event.pos(), ActionKind.BLOCK_EVENT, event.paramB());
        }
        if (action == null) return false;
        MoldedTrayRedstoneRuntime runtime = action.runtime.get();
        if (!action.cancelled && runtime != null) runtime.runBlockEvent(action.componentEvent);
        return true;
    }

    public static void clear(ServerLevel level) {
        synchronized (LEVELS) {
            LEVELS.remove(level);
        }
    }

    private enum ActionKind {
        SCHEDULED_TICK,
        BLOCK_EVENT
    }

    private static final class PendingAction {
        private final WeakReference<MoldedTrayRedstoneRuntime> runtime;
        private final BlockPos markerPosition;
        private final long hostChunk;
        private final ActionKind kind;
        private final long triggerTick;
        private final TickPriority priority;
        private final int componentEvent;
        private final int token;
        private boolean cancelled;

        private PendingAction(
            MoldedTrayRedstoneRuntime runtime,
            BlockPos markerPosition,
            long hostChunk,
            ActionKind kind,
            long triggerTick,
            TickPriority priority,
            int componentEvent,
            int token
        ) {
            this.runtime = new WeakReference<>(runtime);
            this.markerPosition = markerPosition;
            this.hostChunk = hostChunk;
            this.kind = kind;
            this.triggerTick = triggerTick;
            this.priority = priority;
            this.componentEvent = componentEvent;
            this.token = token;
        }

        private static PendingAction scheduledTick(
            MoldedTrayRedstoneRuntime runtime,
            BlockPos markerPosition,
            long hostChunk,
            long triggerTick,
            TickPriority priority
        ) {
            return new PendingAction(
                runtime,
                markerPosition,
                hostChunk,
                ActionKind.SCHEDULED_TICK,
                triggerTick,
                priority,
                0,
                0
            );
        }

        private static PendingAction blockEvent(
            MoldedTrayRedstoneRuntime runtime,
            BlockPos markerPosition,
            long hostChunk,
            int componentEvent,
            int token
        ) {
            return new PendingAction(
                runtime,
                markerPosition,
                hostChunk,
                ActionKind.BLOCK_EVENT,
                0L,
                TickPriority.NORMAL,
                componentEvent,
                token
            );
        }
    }

    private static final class LevelSchedule {
        private final Map<MoldedTrayRedstoneRuntime, PendingAction> currentActions = new IdentityHashMap<>();
        private final Map<BlockPos, PendingAction> actionsByPosition = new HashMap<>();
        private final Map<Long, Integer> nextSlots = new HashMap<>();
        private int nextEventToken = 1;

        private void add(MoldedTrayRedstoneRuntime runtime, PendingAction action) {
            this.currentActions.put(runtime, action);
            this.actionsByPosition.put(action.markerPosition, action);
        }

        private void cancelCurrent(MoldedTrayRedstoneRuntime runtime) {
            PendingAction current = this.currentActions.remove(runtime);
            if (current != null) current.cancelled = true;
        }

        private PendingAction remove(BlockPos markerPosition, ActionKind kind, int token) {
            PendingAction action = this.actionsByPosition.get(markerPosition);
            if (action == null || action.kind != kind || kind == ActionKind.BLOCK_EVENT && action.token != token) {
                return null;
            }
            this.actionsByPosition.remove(markerPosition);
            MoldedTrayRedstoneRuntime runtime = action.runtime.get();
            if (runtime != null && this.currentActions.get(runtime) == action) {
                this.currentActions.remove(runtime);
            }
            return action;
        }

        private BlockPos allocate(ServerLevel level, BlockPos hostPosition) {
            ChunkPos chunk = new ChunkPos(hostPosition);
            long chunkKey = chunk.toLong();
            int height = level.getMaxBuildHeight() - level.getMinBuildHeight();
            int slotCount = 16 * 16 * height;
            int start = Math.floorMod(this.nextSlots.getOrDefault(chunkKey, 0), slotCount);
            for (int offset = 0; offset < slotCount; offset++) {
                int slot = (start + offset) % slotCount;
                int localX = slot & 15;
                int localZ = slot >> 4 & 15;
                int y = level.getMinBuildHeight() + (slot >> 8);
                BlockPos candidate = new BlockPos(
                    chunk.getMinBlockX() + localX,
                    y,
                    chunk.getMinBlockZ() + localZ
                );
                if (this.actionsByPosition.containsKey(candidate)) continue;
                this.nextSlots.put(chunkKey, slot + 1);
                return candidate;
            }
            throw new IllegalStateException("No virtual tray redstone scheduler slot is available in chunk " + chunk);
        }
    }
}
