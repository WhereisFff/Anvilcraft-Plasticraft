package dev.anvilcraft.plasticraft.allay.path;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** 悦灵专用守护线程池,不与树脂牵引搜索线程混用。 */
public final class AllayPathExecutor {
    private static final int POOL_SIZE = Mth.clamp(Runtime.getRuntime().availableProcessors() / 2, 1, 4);
    private static final PriorityBlockingQueue<Task> QUEUE = new PriorityBlockingQueue<>();
    private static final ConcurrentHashMap<UUID, Task> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();

    static {
        for (int index = 0; index < POOL_SIZE; index++) {
            Thread thread = new Thread(AllayPathExecutor::loop, "plasticraft-allay-path-search-" + index);
            thread.setDaemon(true);
            thread.start();
        }
    }

    private AllayPathExecutor() {
    }

    @FunctionalInterface
    public interface Work {
        List<Vec3> run(BooleanSupplier cancelled) throws Exception;
    }

    public static CompletableFuture<List<Vec3>> submit(
        UUID allayId,
        AllayPathPriority priority,
        Work work
    ) {
        cancel(allayId);
        CompletableFuture<List<Vec3>> future = new CompletableFuture<>();
        Task task = new Task(allayId, priority, work, future, SEQUENCE.getAndIncrement());
        IN_FLIGHT.put(allayId, task);
        QUEUE.offer(task);
        return future;
    }

    public static void cancel(UUID allayId) {
        Task task = IN_FLIGHT.remove(allayId);
        if (task == null) return;
        task.cancelled = true;
        QUEUE.remove(task);
        task.future.cancel(true);
    }

    public static boolean hasRequest(UUID allayId) {
        return IN_FLIGHT.containsKey(allayId);
    }

    private static void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Task task = QUEUE.take();
                if (task.cancelled || task.future.isCancelled()) {
                    continue;
                }
                task.run();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static final class Task implements Comparable<Task> {
        private final UUID allayId;
        private final AllayPathPriority priority;
        private final Work work;
        private final CompletableFuture<List<Vec3>> future;
        private final long sequence;
        private volatile boolean cancelled;

        private Task(
            UUID allayId,
            AllayPathPriority priority,
            Work work,
            CompletableFuture<List<Vec3>> future,
            long sequence
        ) {
            this.allayId = allayId;
            this.priority = priority;
            this.work = work;
            this.future = future;
            this.sequence = sequence;
        }

        private void run() {
            try {
                if (this.isCancelled()) return;
                List<Vec3> result = this.work.run(this::isCancelled);
                if (!this.isCancelled()) {
                    this.future.complete(result);
                }
            } catch (Exception ignored) {
                if (!this.isCancelled()) {
                    this.future.complete(List.of());
                }
            } finally {
                IN_FLIGHT.remove(this.allayId, this);
            }
        }

        private boolean isCancelled() {
            return this.cancelled || this.future.isCancelled() || Thread.currentThread().isInterrupted();
        }

        @Override
        public int compareTo(Task other) {
            int byPriority = Integer.compare(this.priority.ordinal(), other.priority.ordinal());
            if (byPriority != 0) return byPriority;
            return Long.compare(this.sequence, other.sequence);
        }
    }
}
