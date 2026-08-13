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

    public static void renderSelectionOutline(
        PoseStack poseStack,
        VertexConsumer consumer,
        PlasticConvexCollisionOutline.PackedOutline outline,
        double translationX,
        double translationY,
        double translationZ
    ) {
        renderPackedOutline(
            poseStack,
            consumer,
            outline,
            translationX,
            translationY,
            translationZ,
            0.0F,
            0.0F,
            0.0F,
            SELECTION_ALPHA
        );
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
            renderPackedOutline(
                poseStack,
                consumer,
                collisionBox.packedOutline(),
                translation.x,
                translation.y,
                translation.z,
                red,
                green,
                blue,
                alpha
            );
            return;
        }
        renderVoxelOutline(poseStack.last(), consumer, collisionBox, translation, red, green, blue, alpha);
    }

    public static void renderPackedOutline(
        PoseStack poseStack,
        VertexConsumer consumer,
        PlasticConvexCollisionOutline.PackedOutline outline,
        double translationX,
        double translationY,
        double translationZ,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        if (outline.isEmpty()) return;
        PoseStack.Pose pose = poseStack.last();
        float offsetX = (float) translationX;
        float offsetY = (float) translationY;
        float offsetZ = (float) translationZ;
        float[] vertices = outline.vertices();
        for (int offset = 0; offset < vertices.length; offset += PlasticConvexCollisionOutline.PackedOutline.STRIDE) {
            float normalX = vertices[offset + 6];
            float normalY = vertices[offset + 7];
            float normalZ = vertices[offset + 8];
            consumer.addVertex(
                    pose,
                    vertices[offset] + offsetX,
                    vertices[offset + 1] + offsetY,
                    vertices[offset + 2] + offsetZ
                )
                .setColor(red, green, blue, alpha)
                .setNormal(pose, normalX, normalY, normalZ);
            consumer.addVertex(
                    pose,
                    vertices[offset + 3] + offsetX,
                    vertices[offset + 4] + offsetY,
                    vertices[offset + 5] + offsetZ
                )
                .setColor(red, green, blue, alpha)
                .setNormal(pose, normalX, normalY, normalZ);
        }
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
}
