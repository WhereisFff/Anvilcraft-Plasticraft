package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.entity.collision.BondedPlasticShapeIndex;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexCollisionResolver;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractArrow.class)
abstract class AbstractArrowCollisionMixin {
    @Redirect(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;contains(Lnet/minecraft/world/phys/Vec3;)Z")
    )
    private boolean plasticraft$checkEmbeddedInRealShape(AABB bounds, Vec3 point) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        BondedPlasticShapeIndex.Entry entry = BondedPlasticShapeIndex.entryAt(self.level(), self.blockPosition());
        if (entry == null || entry.convexShapes().isEmpty()
            || !BondedPlasticShapeIndex.isCurrent(self.level(), entry.anchor())) return bounds.contains(point);
        return entry.convexShapes().stream().anyMatch(shape -> shape.contains(point, 0.0D));
    }

    @Redirect(
        method = "shouldFall",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/phys/AABB;)Z")
    )
    private boolean plasticraft$checkRealSurfaceSupport(Level level, AABB bounds) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        return PlasticConvexCollisionResolver.hasExactEntityObstacle(self, Vec3.ZERO, bounds, level)
            ? PlasticConvexCollisionResolver.noCollisionWithExactPlastic(self, bounds, level)
            : level.noCollision(bounds);
    }
}
