package dev.anvilcraft.plasticraft.client.renderer.molding;

import org.jetbrains.annotations.Nullable;

/** 只记录离屏目标的尺寸与幂等释放语义，不暴露任何图形 API 细节。 */
public final class MoldingViewportTargetLifecycle<T> implements AutoCloseable {
    private final Adapter<T> adapter;
    @Nullable
    private T target;
    private int width;
    private int height;
    private boolean closed;

    public MoldingViewportTargetLifecycle(Adapter<T> adapter) {
        this.adapter = adapter;
    }

    public void ensure(int width, int height) {
        requireOpen();
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (this.target == null) {
            this.target = this.adapter.create(safeWidth, safeHeight);
        } else if (this.width != safeWidth || this.height != safeHeight) {
            this.adapter.resize(this.target, safeWidth, safeHeight);
        }
        this.width = safeWidth;
        this.height = safeHeight;
    }

    public @Nullable T target() {
        return this.target;
    }

    public void reload() {
        if (this.closed) return;
        release();
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        release();
    }

    private void release() {
        if (this.target != null) this.adapter.release(this.target);
        this.target = null;
        this.width = 0;
        this.height = 0;
    }

    private void requireOpen() {
        if (this.closed) throw new IllegalStateException("Viewport target lifecycle is closed");
    }

    public interface Adapter<T> {
        T create(int width, int height);

        void resize(T target, int width, int height);

        void release(T target);
    }
}
