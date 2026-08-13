package dev.anvilcraft.plasticraft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.api.entity.ShapedCollisionEntity;
import dev.anvilcraft.plasticraft.client.renderer.PlasticCollisionOutlineRenderer;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexCollisionOutline;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 让 F3+B 显示塑料实体真实的组合碰撞体。 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
    private static void plasticraft$renderPlasticCollisionBox(
        PoseStack poseStack,
        VertexConsumer buffer,
        Entity entity,
        float partialTick,
        float red,
        float green,
        float blue,
        CallbackInfo ci
    ) {
        if (!(entity instanceof ShapedCollisionEntity shaped)) return;

        if (entity instanceof AbstractPlasticEntity plastic) {
            PlasticConvexCollisionOutline.PackedOutline outline = plastic.plasticraft$getCollisionOutline();
            if (!outline.isEmpty()) {
                Vec3 origin = plastic.plasticraft$getGeometry().entityOrigin();
                PlasticCollisionOutlineRenderer.renderPackedOutline(
                    poseStack,
                    buffer,
                    outline,
                    -origin.x,
                    -origin.y,
                    -origin.z,
                    red,
                    green,
                    blue,
                    1.0F
                );
                renderDirectionVector(poseStack, buffer, entity, partialTick);
                ci.cancel();
                return;
            }
        }

        PlasticCollisionOutlineRenderer.renderOutline(
            poseStack,
            buffer,
            shaped.plasticraft$getCollisionBox(),
            entity.position().scale(-1.0D),
            red,
            green,
            blue,
            1.0F
        );
        renderDirectionVector(poseStack, buffer, entity, partialTick);
        ci.cancel();
    }

    private static void renderDirectionVector(
        PoseStack poseStack,
        VertexConsumer buffer,
        Entity entity,
        float partialTick
    ) {
        Vec3 vector = entity.getViewVector(partialTick).scale(2.0D);
        Vector3f start = new Vector3f(0.0F, entity.getEyeHeight(), 0.0F);
        PoseStack.Pose pose = poseStack.last();
        buffer.addVertex(pose, start)
            .setColor(-16776961)
            .setNormal(pose, (float) vector.x, (float) vector.y, (float) vector.z);
        buffer.addVertex(
                pose,
                (float) (start.x() + vector.x),
                (float) (start.y() + vector.y),
                (float) (start.z() + vector.z)
            )
            .setColor(-16776961)
            .setNormal(pose, (float) vector.x, (float) vector.y, (float) vector.z);
    }
}
