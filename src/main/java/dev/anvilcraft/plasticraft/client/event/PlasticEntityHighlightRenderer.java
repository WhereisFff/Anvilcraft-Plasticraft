package dev.anvilcraft.plasticraft.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.lib.v2.cube.client.CubeSelection;
import dev.anvilcraft.lib.v2.cube.client.OutlineRenderer;
import dev.anvilcraft.lib.v2.cube.client.SelectionPart;
import dev.anvilcraft.lib.v2.cube.geometry.SelectionGeometry;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.client.renderer.PlasticRenderGeometry;
import dev.anvilcraft.plasticraft.client.selection.PlasticSelectionGeometry;
import dev.anvilcraft.plasticraft.client.selection.PlasticSelectionIntegration;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/** 绘制动态塑料及超出砧库方块范围的粘合制品，普通方块态由砧库直接处理。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class PlasticEntityHighlightRenderer {
    private PlasticEntityHighlightRenderer() {
    }

    private static final ContextKey<PlasticRenderGeometry> GEOMETRY = new ContextKey<>(AnvilcraftPlasticraft.of("plastic_selection"));

    @SubscribeEvent
    public static void extractEntity(ExtractLevelRenderStateEvent event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || !(minecraft.hitResult instanceof EntityHitResult hit)
            || !(hit.getEntity() instanceof AbstractPlasticEntity plastic)) {
            return;
        }
        float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(!plastic.level().tickRateManager().isEntityFrozen(plastic));
        Vec3 position = plastic.getPosition(partialTick);
        Vec3 camera = event.getCamera().position();
        SelectionPart part = PlasticSelectionGeometry.get(plastic.plasticraft$getGeometry()).selection(plastic.getOrientation());
        PlasticRenderGeometry geometry = new PlasticRenderGeometry();
        PoseStack pose = new PoseStack();
        pose.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
        part.apply(pose);
        OutlineRenderer.render(pose, geometry.getBuffer(RenderTypes.lines()),
            CubeSelection.outlines().get(part.geometry()).forView(part.localPosition(camera.subtract(position))), 0, 0, 0, 0.4f);
        event.getRenderState().setRenderData(GEOMETRY, geometry);
    }

    @SubscribeEvent
    public static void submit(SubmitCustomGeometryEvent event) {
        var state = event.getLevelRenderState();
        var geometry = state.getRenderData(GEOMETRY);
        if (geometry != null) {
            geometry.submit(event.getPoseStack(), event.getSubmitNodeCollector(), state.cameraRenderState);
        }
    }

    @SubscribeEvent
    public static void renderOversizedBlockHighlight(ExtractBlockOutlineRenderStateEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || Minecraft.getInstance().options.hideGui) return;
        BlockPos pos = event.getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded)
            || !bonded.isInitialized() || !bonded.isPlastic()
            || !(bonded.getOrCreateRenderEntity() instanceof AbstractPlasticEntity plastic)) return;
        PlasticEntityGeometry geometry = plastic.plasticraft$getGeometry();
        SelectionPart part = PlasticSelectionGeometry.get(geometry)
            .placed(geometry, bonded.getPlasticOrientation(), bonded.getAdhesiveLocalFace()).getFirst();
        if (PlasticSelectionIntegration.supported(part)) return;
        addOutline(event, pos, part);
    }

    static void addOutline(ExtractBlockOutlineRenderStateEvent event, BlockPos origin, SelectionPart part) {
        var outline = CubeSelection.outlines().get(part.geometry());
        float lineWidth = Minecraft.getInstance().gameRenderer.getGameRenderState().windowRenderState.appropriateLineWidth;
        event.addCustomRenderer((state, buffers, pose, translucent, level) -> {
            if (state.isTranslucent() == translucent) {
                Vec3 camera = level.cameraRenderState.pos;
                var visible = outline.forView(part.localPosition(camera.subtract(Vec3.atLowerCornerOf(origin))));
                pose.pushPose();
                pose.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);
                part.apply(pose);
                OutlineRenderer.render(pose, buffers.getBuffer(RenderTypes.lines()), visible, 0, 0, 0, 0.4f,
                    SelectionGeometry.MAX_OUTLINE_SEGMENTS, lineWidth);
                pose.popPose();
            }
            return true;
        });
    }
}
