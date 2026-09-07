package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.anvilcraft.plasticraft.blueprint.ConstructionProjectionIndex;
import dev.anvilcraft.plasticraft.entity.collision.BondedPlasticShapeIndex;
import dev.anvilcraft.plasticraft.entity.collision.PlasticProjectileClipContext;
import dev.anvilcraft.plasticraft.entity.collision.PlasticProjectileCollision;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 让射线命中粘合制品溢出轮廓与已交付施工假方块。 */
@Mixin(BlockGetter.class)
interface BlockGetterMixin {
    @ModifyReturnValue(method = "clip", at = @At("RETURN"))
    private BlockHitResult plasticraft$clipVirtualShapes(
        BlockHitResult original,
        ClipContext context
    ) {
        BlockGetter getter = (BlockGetter) this;
        Vec3 from = context.getFrom();
        Vec3 to = context.getTo();
        AABB rayBounds = new AABB(from, to).inflate(1.0E-7D);
        BlockHitResult nearest = original;
        double nearestDistance = from.distanceToSqr(original.getLocation());
        if (context instanceof PlasticProjectileClipContext exact && exact.plasticraft$usesExactPlasticCollision()) {
            for (BondedPlasticShapeIndex.Entry entry : BondedPlasticShapeIndex.collisionEntries(getter, rayBounds)) {
                if (!BondedPlasticShapeIndex.isCurrent(getter, entry.anchor())) continue;
                BlockHitResult candidate = PlasticProjectileCollision.clip(entry, from, to);
                if (candidate == null) continue;
                double distance = from.distanceToSqr(candidate.getLocation());
                if (distance > nearestDistance) continue;
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        for (BondedPlasticShapeIndex.Entry entry : BondedPlasticShapeIndex.extendedInteractionEntries(
            getter,
            rayBounds
        )) {
            BlockPos anchor = entry.anchor();
            if (!BondedPlasticShapeIndex.isCurrent(getter, anchor)) continue;
            BlockState state = getter.getBlockState(anchor);
            VoxelShape shape = context.getBlockShape(state, getter, anchor);
            BlockHitResult candidate = getter.clipWithInteractionOverride(from, to, anchor, shape, state);
            if (candidate == null) continue;
            double candidateDistance = from.distanceToSqr(candidate.getLocation());
            if (candidateDistance >= nearestDistance) continue;
            nearest = candidate;
            nearestDistance = candidateDistance;
        }
        for (ConstructionProjectionIndex.Collision collision : ConstructionProjectionIndex.collisions(
            getter,
            rayBounds
        )) {
            BlockHitResult candidate = collision.worldShape().clip(from, to, BlockPos.ZERO);
            if (candidate == null) continue;
            double candidateDistance = from.distanceToSqr(candidate.getLocation());
            if (candidateDistance >= nearestDistance) continue;
            nearest = new BlockHitResult(
                candidate.getLocation(),
                Direction.getNearest(to.x - from.x, to.y - from.y, to.z - from.z),
                collision.pos(),
                false
            );
            nearestDistance = candidateDistance;
        }
        return nearest;
    }
}
