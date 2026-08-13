package dev.anvilcraft.plasticraft.client.renderer.entity.drone;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.anvilcraft.plasticraft.entity.drone.DroneEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** 无人机实体渲染器;只读取实体已同步的工具、螺旋桨与飞行状态,不驱动服务端行为。 */
public class DroneRenderer extends EntityRenderer<DroneEntity> {
    /** 空中状态的螺旋桨转速(度/gt);转速只表现已同步的运动状态。 */
    private static final float PROPELLER_SPIN_SPEED = 42.0F;
    private final DroneRenderDispatcher dispatcher;

    public DroneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.dispatcher = new DroneRenderDispatcher(context::bakeLayer);
        this.shadowRadius = 0.25F;
    }

    @Override
    public void render(
        DroneEntity drone,
        float entityYaw,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight
    ) {
        float spin = drone.flightState().isAirborne()
            ? (drone.tickCount + partialTick) * PROPELLER_SPIN_SPEED
            : 0.0F;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        this.dispatcher.render(
            drone.toolId(),
            drone.getLeftPropeller(),
            drone.getRightPropeller(),
            drone.hostedCarry(),
            spin,
            poseStack,
            buffers,
            packedLight,
            OverlayTexture.NO_OVERLAY
        );
        poseStack.popPose();
        super.render(drone, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(DroneEntity drone) {
        return DroneRenderDispatcher.DRONE_TEXTURE;
    }
}
