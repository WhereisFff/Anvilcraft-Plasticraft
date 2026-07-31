package dev.anvilcraft.plasticraft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.api.entity.ShapedCollisionEntity;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
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

        renderCollisionShape(
            poseStack,
            buffer,
            shaped.plasticraft$getCollisionShape(),
            entity,
            red,
            green,
            blue
        );
        renderDirectionVector(poseStack, buffer, entity, partialTick);
        ci.cancel();
    }

    private static void renderCollisionShape(
        PoseStack poseStack,
        VertexConsumer buffer,
        VoxelShape shape,
        Entity entity,
        float red,
        float green,
        float blue
    ) {
        PoseStack.Pose pose = poseStack.last();
        shape.forAllEdges((minX, minY, minZ, maxX, maxY, maxZ) -> {
            float normalX = (float) (maxX - minX);
            float normalY = (float) (maxY - minY);
            float normalZ = (float) (maxZ - minZ);
            float length = Mth.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
            if (length <= 0.0F) return;
            normalX /= length;
            normalY /= length;
            normalZ /= length;
            buffer.addVertex(
                    pose,
                    (float) (minX - entity.getX()),
                    (float) (minY - entity.getY()),
                    (float) (minZ - entity.getZ())
                )
                .setColor(red, green, blue, 1.0F)
                .setNormal(pose, normalX, normalY, normalZ);
            buffer.addVertex(
                    pose,
                    (float) (maxX - entity.getX()),
                    (float) (maxY - entity.getY()),
                    (float) (maxZ - entity.getZ())
                )
                .setColor(red, green, blue, 1.0F)
                .setNormal(pose, normalX, normalY, normalZ);
        });
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
