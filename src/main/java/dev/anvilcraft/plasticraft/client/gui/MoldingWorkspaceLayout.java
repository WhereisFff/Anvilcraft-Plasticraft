package dev.anvilcraft.plasticraft.client.gui;

public record MoldingWorkspaceLayout(int width, int height, int sidebarX, int sidebarWidth) {
    public static MoldingWorkspaceLayout fit(int availableWidth, int availableHeight) {
        int width = Math.max(400, availableWidth - 8);
        int height = Math.max(240, availableHeight - 8);
        int sidebar = Math.clamp(width / 4, 140, 190);
        return new MoldingWorkspaceLayout(width, height, width - sidebar - 4, sidebar);
    }

    public int viewportX() {
        return 30;
    }

    public int viewportY() {
        return 26;
    }

    public int viewportWidth() {
        return this.sidebarX - 34;
    }

    public int viewportHeight() {
        return this.height - 48;
    }

    public int fieldWidth() {
        return (this.sidebarWidth - 16) / 3;
    }

    public int fieldX(int axis) {
        return this.sidebarX + 6 + axis * (this.fieldWidth() + 2);
    }

    public int fieldY(int property) {
        return 40 + property * 24;
    }

    public int outlinerY() {
        return 157;
    }

    public int outlinerRows() {
        return Math.max(1, (this.height - 23 - this.outlinerY()) / 16);
    }
}
