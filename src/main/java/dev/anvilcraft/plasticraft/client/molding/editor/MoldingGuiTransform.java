package dev.anvilcraft.plasticraft.client.molding.editor;

/** GUI 逻辑坐标、屏幕坐标和实际 framebuffer 像素之间的统一换算。 */
public record MoldingGuiTransform(double scale, double originX, double originY) {
    public MoldingGuiTransform {
        if (!Double.isFinite(scale) || scale <= 0.0D
            || !Double.isFinite(originX) || !Double.isFinite(originY)) {
            throw new IllegalArgumentException("Invalid molding GUI transform");
        }
    }

    public static MoldingGuiTransform fit(
        int screenWidth,
        int screenHeight,
        int visibleWidth,
        int logicalHeight,
        int padding,
        double guiScale
    ) {
        if (!Double.isFinite(guiScale) || guiScale <= 0.0D) {
            throw new IllegalArgumentException("Invalid GUI scale");
        }
        int availableWidth = Math.max(1, screenWidth - padding * 2);
        int availableHeight = Math.max(1, screenHeight - padding * 2);
        double maximumScale = Math.min(
            1.0D,
            Math.min((double) availableWidth / visibleWidth, (double) availableHeight / logicalHeight)
        );
        // 优先让逻辑像素对应整数个 framebuffer 像素；极窄窗口无法容纳一倍时按可用空间缩放。
        int framebufferScale = (int) Math.floor(maximumScale * guiScale + 1.0E-9D);
        double scale = framebufferScale >= 1 ? Math.min(1.0D, framebufferScale / guiScale) : maximumScale;
        return new MoldingGuiTransform(
            scale,
            Math.floor((screenWidth - visibleWidth * scale) * 0.5D),
            Math.floor((screenHeight - logicalHeight * scale) * 0.5D)
        );
    }

    public double toLogicalX(double screenX) {
        return (screenX - this.originX) / this.scale;
    }

    public double toLogicalY(double screenY) {
        return (screenY - this.originY) / this.scale;
    }

    public double toScreenX(double logicalX) {
        return this.originX + logicalX * this.scale;
    }

    public double toScreenY(double logicalY) {
        return this.originY + logicalY * this.scale;
    }

    public int framebufferPixels(int logicalPixels, double guiScale) {
        if (logicalPixels < 0 || !Double.isFinite(guiScale) || guiScale <= 0.0D) {
            throw new IllegalArgumentException("Invalid framebuffer scale");
        }
        return Math.max(1, (int) Math.round(logicalPixels * this.scale * guiScale));
    }
}
