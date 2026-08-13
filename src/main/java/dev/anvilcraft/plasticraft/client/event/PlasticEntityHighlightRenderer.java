package dev.anvilcraft.plasticraft.client.event;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.renderer.PlasticCollisionOutlineRenderer;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexCollisionOutline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/** 在准星命中动态塑料实体时绘制与方块态一致的真实碰撞轮廓。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class PlasticEntityHighlightRenderer {
    private PlasticEntityHighlightRenderer() {
    }

    @SubscribeEvent
    public static void renderEntityHighlight(RenderHighlightEvent.Entity event) {
        if (Minecraft.getInstance().options.hideGui) return;
        Entity entity = event.getTarget().getEntity();
        if (!(entity instanceof AbstractPlasticEntity plastic)) return;

        PlasticConvexCollisionOutline.PackedOutline outline = plastic.plasticraft$getCollisionOutline();
        float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(
            !entity.level().tickRateManager().isEntityFrozen(entity)
        );
        Vec3 interpolated = entity.getPosition(partialTick);
        Vec3 camera = event.getCamera().getPosition();
        if (!outline.isEmpty()) {
            Vec3 origin = plastic.plasticraft$getGeometry().entityOrigin();
            PlasticCollisionOutlineRenderer.renderSelectionOutline(
                event.getPoseStack(),
                event.getMultiBufferSource().getBuffer(RenderType.lines()),
                outline,
                interpolated.x - origin.x - camera.x,
                interpolated.y - origin.y - camera.y,
                interpolated.z - origin.z - camera.z
            );
            return;
        }
        PlasticCollisionOutlineRenderer.renderSelectionOutline(
            event.getPoseStack(),
            event.getMultiBufferSource().getBuffer(RenderType.lines()),
            plastic.plasticraft$getCollisionBox(),
            interpolated.subtract(entity.position()).subtract(camera)
        );
    }
}
