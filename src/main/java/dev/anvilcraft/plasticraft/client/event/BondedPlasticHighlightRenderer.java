package dev.anvilcraft.plasticraft.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.collision.BondedPlasticShapeIndex;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexCollisionOutline;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityCollisionBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/** 用方块化塑料的真实凸碰撞棱线替换原版轴对齐选中轮廓。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class BondedPlasticHighlightRenderer {
    private static final float OUTLINE_ALPHA = 0.4F;

    private BondedPlasticHighlightRenderer() {
    }

    @SubscribeEvent
    public static void renderBlockHighlight(RenderHighlightEvent.Block event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        BlockPos pos = event.getTarget().getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded)
            || !bonded.isInitialized()
            || !bonded.isPlastic()) {
            return;
        }

        BondedPlasticShapeIndex.Entry entry = BondedPlasticShapeIndex.entryAt(level, pos);
        if (entry == null || !BondedPlasticShapeIndex.isCurrent(level, pos)) return;
        PlasticEntityCollisionBox collisionBox = entry.collisionBox();
        if (!collisionBox.hasConvexComponents()) return;

        var outline = collisionBox.convexOutline();
        if (outline.isEmpty()) return;
        Vec3 cameraPosition = event.getCamera().getPosition();
        PoseStack.Pose pose = event.getPoseStack().last();
        VertexConsumer consumer = event.getMultiBufferSource().getBuffer(RenderType.lines());
        for (PlasticConvexCollisionOutline.Segment segment : outline) {
            renderSegment(pose, consumer, segment, cameraPosition);
        }
        event.setCanceled(true);
    }

    private static void renderSegment(
        PoseStack.Pose pose,
        VertexConsumer consumer,
        PlasticConvexCollisionOutline.Segment segment,
        Vec3 cameraPosition
    ) {
        Vec3 start = segment.start().subtract(cameraPosition);
        Vec3 end = segment.end().subtract(cameraPosition);
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length == 0.0D) return;
        Vec3 normal = delta.scale(1.0D / length);
        consumer.addVertex(pose, (float) start.x, (float) start.y, (float) start.z)
            .setColor(0.0F, 0.0F, 0.0F, OUTLINE_ALPHA)
            .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(pose, (float) end.x, (float) end.y, (float) end.z)
            .setColor(0.0F, 0.0F, 0.0F, OUTLINE_ALPHA)
            .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
