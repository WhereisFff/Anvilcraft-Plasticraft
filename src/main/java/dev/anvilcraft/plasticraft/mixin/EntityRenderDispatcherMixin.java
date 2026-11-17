package dev.anvilcraft.plasticraft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.lib.v2.cube.client.CubeSelection;
import dev.anvilcraft.lib.v2.cube.client.SelectionPart;
import dev.anvilcraft.plasticraft.client.selection.PlasticSelectionGeometry;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 让 F3+B 显示塑料实体真实的组合碰撞体。 */
@Mixin(EntityHitboxDebugRenderer.class)
abstract class EntityRenderDispatcherMixin {
    @Inject(method = "showHitboxes", at = @At("HEAD"), cancellable = true)
    private void plasticraft$renderPlasticCollisionBox(Entity entity, float partialTick, boolean server, CallbackInfo callback) {
        if (!(entity instanceof AbstractPlasticEntity plastic)) {
            return;
        }
        Vec3 origin = entity.getPosition(partialTick);
        SelectionPart part = PlasticSelectionGeometry.get(plastic.plasticraft$getGeometry()).collision(plastic.getOrientation());
        if (part != null) {
            PoseStack pose = new PoseStack();
            part.apply(pose);
            var outline = CubeSelection.outlines().get(part.geometry());
            for (int line = 0; line < outline.segmentCount(); line++) {
                Vector3f from = new Vector3f(outline.coordinate(line, 0), outline.coordinate(line, 1), outline.coordinate(line, 2));
                Vector3f to = new Vector3f(outline.coordinate(line, 3), outline.coordinate(line, 4), outline.coordinate(line, 5));
                pose.last().pose().transformPosition(from);
                pose.last().pose().transformPosition(to);
                Gizmos.line(origin.add(from.x, from.y, from.z), origin.add(to.x, to.y, to.z), server ? 0xFF00FF00 : -1);
            }
        }
        Vec3 eye = origin.add(0, entity.getEyeHeight(), 0);
        Gizmos.line(eye, eye.add(entity.getViewVector(partialTick).scale(2)), 0xFF0000FF);
        callback.cancel();
    }
}
