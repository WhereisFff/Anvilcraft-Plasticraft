package dev.anvilcraft.plasticraft.client.renderer.blockentity;

import com.mojang.math.Axis;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/** 机器 BER 与选择几何共用朝向、枢轴和旋转，避免两套变换出现偏差。 */
public final class MachineModelTransforms {
    private MachineModelTransforms() {
    }

    public static Matrix4f facing(Direction facing) {
        float rotation = (facing.toYRot() + 180.0F) % 360.0F;
        return new Matrix4f().translation(0.5F, 0, 0.5F)
            .rotate(Axis.YP.rotationDegrees(-rotation)).translate(-0.5F, 0, -0.5F);
    }
}
