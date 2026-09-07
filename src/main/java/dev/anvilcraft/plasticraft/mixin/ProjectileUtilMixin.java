package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.anvilcraft.plasticraft.api.entity.ShapedCollisionEntity;
import dev.anvilcraft.plasticraft.entity.collision.PlasticProjectileCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;
import java.util.function.Predicate;

@Mixin(ProjectileUtil.class)
abstract class ProjectileUtilMixin {
    @Redirect(
        method = {
            "getEntityHitResult(Lnet/minecraft/world/entity/Entity;"
                + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                + "Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)"
                + "Lnet/minecraft/world/phys/EntityHitResult;",
            "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;"
                + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                + "Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)"
                + "Lnet/minecraft/world/phys/EntityHitResult;"
        },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/AABB;clip("
                + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;"
        )
    )
    private static Optional<Vec3> plasticraft$clipRealSurface(
        AABB bounds, Vec3 start, Vec3 end,
        @Local(ordinal = 2) Entity target, @Local(argsOnly = true) Entity shooter
    ) {
        return shooter instanceof Projectile && target instanceof ShapedCollisionEntity shaped
            ? PlasticProjectileCollision.clip(shaped, start, end)
            : bounds.clip(start, end);
    }

    @Redirect(
        method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;"
            + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
            + "Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)"
            + "Lnet/minecraft/world/phys/EntityHitResult;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/AABB;contains(Lnet/minecraft/world/phys/Vec3;)Z"
        )
    )
    private static boolean plasticraft$containsRealSurface(
        AABB bounds, Vec3 point,
        @Local(ordinal = 2) Entity target, @Local(argsOnly = true) Entity shooter
    ) {
        return shooter instanceof Projectile && target instanceof ShapedCollisionEntity shaped
            ? PlasticProjectileCollision.contains(shaped, point)
            : bounds.contains(point);
    }

    // 原版此重载只返回目标中心，塑料命中还需保留真实表面位置供后续命中处理使用。
    @ModifyReturnValue(
        method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;"
            + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
            + "Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)"
            + "Lnet/minecraft/world/phys/EntityHitResult;",
        at = @At("RETURN")
    )
    @Nullable
    private static EntityHitResult plasticraft$preserveSurfaceHitPosition(
        @Nullable EntityHitResult original,
        Level level, Entity projectile, Vec3 start, Vec3 end,
        AABB bounds, Predicate<Entity> filter, float inflation
    ) {
        if (!(projectile instanceof Projectile)
            || original == null || !(original.getEntity() instanceof ShapedCollisionEntity shaped)) return original;
        return PlasticProjectileCollision.clip(shaped, start, end)
            .map(position -> new EntityHitResult(original.getEntity(), position))
            .orElse(original);
    }
}
