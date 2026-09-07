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

    public static Matrix4f loungeHatch(boolean left, float openness) {
        float hingeX = (left ? 1.0F : 15.0F) / 16;
        float hingeY = 14.5F / 16;
        float angle = 100.0F * openness * (left ? 1 : -1);
        return new Matrix4f().translation(hingeX, hingeY, 0.5F)
            .rotate(Axis.ZP.rotationDegrees(angle)).translate(-hingeX, -hingeY, -0.5F);
    }
}
