package dev.anvilcraft.plasticraft.client.molding.scene;

import dev.anvilcraft.plasticraft.client.molding.editor.ViewportTransform;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public record ViewportFrame(
    EditorSceneMesh scene,
    ViewportTransform transform,
    int clearColor,
    boolean invalidPreview,
    @Nullable BlockState controllerState,
    @Nullable Vector3d controllerOrigin,
    int controllerLight
) {
    public ViewportFrame {
        controllerOrigin = controllerOrigin == null ? null : new Vector3d(controllerOrigin);
    }

    @Override
    public @Nullable Vector3d controllerOrigin() {
        return this.controllerOrigin == null ? null : new Vector3d(this.controllerOrigin);
    }
}
