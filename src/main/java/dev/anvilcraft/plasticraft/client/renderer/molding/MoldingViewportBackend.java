package dev.anvilcraft.plasticraft.client.renderer.molding;

import dev.anvilcraft.plasticraft.client.molding.scene.EditorSceneMesh;
import dev.anvilcraft.plasticraft.client.molding.scene.ViewportFrame;
import net.minecraft.client.gui.GuiGraphics;

public interface MoldingViewportBackend extends AutoCloseable {
    void ensureTarget(int width, int height);

    void updateMesh(EditorSceneMesh mesh);

    void render(ViewportFrame frame);

    void compose(GuiGraphics graphics, int x, int y, int width, int height);

    void reloadResources();

    @Override
    void close();
}
