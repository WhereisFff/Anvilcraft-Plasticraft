package dev.anvilcraft.plasticraft.client.event;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.lib.v2.cube.client.CubeSelection;
import dev.anvilcraft.lib.v2.cube.client.OutlineRenderer;
import dev.anvilcraft.lib.v2.cube.client.SelectionPart;
import dev.anvilcraft.plasticraft.client.selection.PlasticSelectionGeometry;
import dev.anvilcraft.plasticraft.client.selection.PlasticSelectionIntegration;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/** 绘制动态塑料及超出砧库方块范围的粘合制品，普通方块态由砧库直接处理。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class PlasticEntityHighlightRenderer {
    private PlasticEntityHighlightRenderer() {
    }

    @SubscribeEvent
    public static void renderEntityHighlight(RenderHighlightEvent.Entity event) {
        if (Minecraft.getInstance().options.hideGui) return;
        Entity entity = event.getTarget().getEntity();
        if (!(entity instanceof AbstractPlasticEntity plastic)) return;

        float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(
            !entity.level().tickRateManager().isEntityFrozen(entity)
        );
        Vec3 interpolated = entity.getPosition(partialTick);
        Vec3 camera = event.getCamera().getPosition();
        SelectionPart part = PlasticSelectionGeometry.get(plastic.plasticraft$getGeometry()).selection(plastic.getOrientation());
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(interpolated.x - camera.x, interpolated.y - camera.y, interpolated.z - camera.z);
        part.apply(event.getPoseStack());
        OutlineRenderer.render(
            event.getPoseStack(),
            event.getMultiBufferSource().getBuffer(RenderType.lines()),
            CubeSelection.outlines().get(part.geometry()),
            0, 0, 0, 0.4F
        );
        event.getPoseStack().popPose();
    }

    @SubscribeEvent
    public static void renderOversizedBlockHighlight(RenderHighlightEvent.Block event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || Minecraft.getInstance().options.hideGui) return;
        BlockPos pos = event.getTarget().getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded)
            || !bonded.isInitialized() || !bonded.isPlastic()
            || !(bonded.getOrCreateRenderEntity() instanceof AbstractPlasticEntity plastic)) return;
        PlasticEntityGeometry geometry = plastic.plasticraft$getGeometry();
        SelectionPart part = PlasticSelectionGeometry.get(geometry)
            .placed(geometry, bonded.getPlasticOrientation(), bonded.getAdhesiveLocalFace()).getFirst();
        if (PlasticSelectionIntegration.supported(part)) return;
        // 巨型制品超过 520 的邻格拾取范围时保留兼容选取，轮廓仍复用砧库的几何与预算。
        Vec3 camera = event.getCamera().getPosition();
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        part.apply(event.getPoseStack());
        OutlineRenderer.render(event.getPoseStack(), event.getMultiBufferSource().getBuffer(RenderType.lines()),
            CubeSelection.outlines().get(part.geometry()), 0, 0, 0, 0.4F);
        event.getPoseStack().popPose();
        event.setCanceled(true);
    }
}
