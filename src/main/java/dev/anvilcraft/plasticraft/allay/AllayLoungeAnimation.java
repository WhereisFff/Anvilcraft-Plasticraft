package dev.anvilcraft.plasticraft.allay;

public final class AllayLoungeAnimation {
    public static final int OPEN_TICKS = 9;
    public static final int CLOSE_TICKS = 12;
    public static final int IDLE_TICKS = 40;
    public static final int ITEM_SLIDE_TICKS = 4;

    private float previous;
    private float progress;

    public void tick(boolean open) {
        this.previous = this.progress;
        this.progress = open
            ? Math.min(1.0F, this.progress + 1.0F / OPEN_TICKS)
            : Math.max(0.0F, this.progress - 1.0F / CLOSE_TICKS);
    }

    public float openness(float partialTick) {
        float partial = Math.clamp(partialTick, 0.0F, 1.0F);
        return smoothStep(this.previous + (this.progress - this.previous) * partial);
    }

    public static float smoothStep(float progress) {
        float value = Math.clamp(progress, 0.0F, 1.0F);
        return value * value * (3.0F - 2.0F * value);
    }
}
