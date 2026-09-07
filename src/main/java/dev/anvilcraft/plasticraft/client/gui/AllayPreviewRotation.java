package dev.anvilcraft.plasticraft.client.gui;

public final class AllayPreviewRotation {
    private static final double DEGREES_PER_PIXEL = 3.0D;
    private static final double DAMPING = 5.5D;
    private static final double MAX_SPEED = 720.0D;
    private static final double STOP_SPEED = 2.0D;

    private double angle = -25.0D;
    private double velocity;
    private long lastUpdate;
    private long lastDrag;
    private boolean dragging;

    public float angle() {
        return (float) this.angle;
    }

    public void beginDrag(long now) {
        this.update(now);
        this.dragging = true;
        this.velocity = 0.0D;
        this.lastDrag = now;
    }

    public void drag(double pixels, long now) {
        this.update(now);
        if (pixels == 0.0D) return;
        double seconds = Math.clamp((now - this.lastDrag) / 1_000_000_000.0D, 1.0D / 240.0D, 0.1D);
        double delta = -pixels * DEGREES_PER_PIXEL;
        this.angle = (this.angle + delta) % 360.0D;
        double speed = Math.clamp(delta / seconds, -MAX_SPEED, MAX_SPEED);
        this.velocity = this.velocity * 0.25D + speed * 0.75D;
        this.lastDrag = now;
    }

    public void endDrag(long now) {
        this.update(now);
        this.dragging = false;
    }

    public void update(long now) {
        if (this.lastUpdate == 0L) {
            this.lastUpdate = now;
            return;
        }
        double seconds = Math.max(0.0D, (now - this.lastUpdate) / 1_000_000_000.0D);
        this.lastUpdate = now;
        if (this.dragging && now - this.lastDrag <= 60_000_000L) return;
        double decay = Math.exp(-DAMPING * seconds);
        if (!this.dragging) {
            // 对指数阻尼积分，使不同帧率下的余转角度一致。
            this.angle = (this.angle + this.velocity * (1.0D - decay) / DAMPING) % 360.0D;
        }
        this.velocity *= decay;
        if (Math.abs(this.velocity) < STOP_SPEED) this.velocity = 0.0D;
    }
}
