package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.anvilcraft.plasticraft.client.selection.PlasticEntityPicking;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

@Mixin(GameRenderer.class)
abstract class PlasticEntitySelectionMixin {
    @WrapOperation(
        method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult("
                + "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                + "Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;")
    )
    private @Nullable EntityHitResult plasticraft$pickModel(
        Entity camera, Vec3 start, Vec3 end, AABB bounds, Predicate<Entity> filter, double distance,
        Operation<EntityHitResult> original, @Local(argsOnly = true) float partialTick
    ) {
        List<Entity> candidates = camera.level().getEntities(camera, bounds, filter);
        if (candidates.stream().noneMatch(AbstractPlasticEntity.class::isInstance)) {
            return original.call(camera, start, end, bounds, filter, distance);
        }
        return PlasticEntityPicking.pick(camera, candidates, start, end, distance, partialTick);
    }
}
