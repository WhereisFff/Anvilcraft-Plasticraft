package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexCollisionOutline;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityCollisionBox;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** 统一绘制 F3+B 与选中高亮使用的塑料碰撞轮廓。 */
public final class PlasticCollisionOutlineRenderer {
    private static final float SELECTION_ALPHA = 0.4F;

    private PlasticCollisionOutlineRenderer() {
    }

    public static void renderSelectionOutline(
        PoseStack poseStack,
        VertexConsumer consumer,
        PlasticEntityCollisionBox collisionBox,
        Vec3 translation
    ) {
        renderOutline(poseStack, consumer, collisionBox, translation, 0.0F, 0.0F, 0.0F, SELECTION_ALPHA);
    }

    public static void renderOutline(
        PoseStack poseStack,
        VertexConsumer consumer,
        PlasticEntityCollisionBox collisionBox,
        Vec3 translation,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        if (collisionBox.hasConvexComponents()) {
            renderConvexOutline(poseStack.last(), consumer, collisionBox, translation, red, green, blue, alpha);
            return;
        }
        renderVoxelOutline(poseStack.last(), consumer, collisionBox, translation, red, green, blue, alpha);
    }

    private static void renderVoxelOutline(
        PoseStack.Pose pose,
        VertexConsumer consumer,
        PlasticEntityCollisionBox collisionBox,
        Vec3 translation,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        collisionBox.shape().forAllEdges((minX, minY, minZ, maxX, maxY, maxZ) -> {
            float normalX = (float) (maxX - minX);
            float normalY = (float) (maxY - minY);
            float normalZ = (float) (maxZ - minZ);
            float length = Mth.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
            if (length <= 0.0F) return;
            normalX /= length;
            normalY /= length;
            normalZ /= length;
            consumer.addVertex(
                    pose,
                    (float) (minX + translation.x),
                    (float) (minY + translation.y),
                    (float) (minZ + translation.z)
                )
                .setColor(red, green, blue, alpha)
                .setNormal(pose, normalX, normalY, normalZ);
            consumer.addVertex(
                    pose,
                    (float) (maxX + translation.x),
                    (float) (maxY + translation.y),
                    (float) (maxZ + translation.z)
                )
                .setColor(red, green, blue, alpha)
                .setNormal(pose, normalX, normalY, normalZ);
        });
    }

    private static void renderConvexOutline(
        PoseStack.Pose pose,
        VertexConsumer consumer,
        PlasticEntityCollisionBox collisionBox,
        Vec3 translation,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        for (PlasticConvexCollisionOutline.Segment segment : collisionBox.convexOutline()) {
            Vec3 start = segment.start().add(translation);
            Vec3 end = segment.end().add(translation);
            Vec3 delta = end.subtract(start);
            double length = delta.length();
            if (length == 0.0D) continue;
            Vec3 normal = delta.scale(1.0D / length);
            consumer.addVertex(pose, (float) start.x, (float) start.y, (float) start.z)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
            consumer.addVertex(pose, (float) end.x, (float) end.y, (float) end.z)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        }
    }
}
