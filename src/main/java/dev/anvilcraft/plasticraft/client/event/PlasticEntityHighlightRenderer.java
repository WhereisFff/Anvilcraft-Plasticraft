package dev.anvilcraft.plasticraft.client.event;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.api.entity.ShapedCollisionEntity;
import dev.anvilcraft.plasticraft.client.renderer.PlasticCollisionOutlineRenderer;
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
        Entity entity = event.getTarget().getEntity();
        if (!(entity instanceof ShapedCollisionEntity shaped)) return;

        float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(
            !entity.level().tickRateManager().isEntityFrozen(entity)
        );
        Vec3 translation = entity.getPosition(partialTick)
            .subtract(entity.position())
            .subtract(event.getCamera().getPosition());
        PlasticCollisionOutlineRenderer.renderSelectionOutline(
            event.getPoseStack(),
            event.getMultiBufferSource().getBuffer(RenderType.lines()),
            shaped.plasticraft$getCollisionBox(),
            translation
        );
    }
}
