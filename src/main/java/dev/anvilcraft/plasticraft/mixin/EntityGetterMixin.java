package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.anvilcraft.plasticraft.api.entity.CarrierMovableEntity;
import dev.anvilcraft.plasticraft.api.entity.ElasticCollisionEntity;
import dev.anvilcraft.plasticraft.api.entity.ShapedCollisionEntity;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.entity.collision.CarrierMoveContext;
import dev.anvilcraft.plasticraft.entity.collision.CarrierMoveContextHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

/** 从移动者的碰撞形状中排除可随动目标，最终位移随后按目标可移动距离裁剪。 */
@Mixin(EntityGetter.class)
interface EntityGetterMixin {
    /**
     * 原版只会把实体外包围盒加入碰撞列表；这里替换为可组合形状。
     * 26.1 移植时需重新核对 EntityGetter#getEntityCollisions 和 isUnobstructed 的形状构造点。
     */
    @ModifyExpressionValue(
        method = "getEntityCollisions",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/Shapes;create(Lnet/minecraft/world/phys/AABB;)"
                + "Lnet/minecraft/world/phys/shapes/VoxelShape;"
        )
    )
    private VoxelShape plasticraft$useShapedCollisionInCollisionQuery(
        VoxelShape original,
        Entity mover,
        AABB collisionBox,
        @Local(ordinal = 1) Entity entity
    ) {
        if (!(entity instanceof ShapedCollisionEntity shaped)) return original;
        Vec3 requestedMovement = Vec3.ZERO;
        if (mover instanceof CarrierMoveContextHolder holder
            && entity instanceof CarrierMovableEntity movable) {
            CarrierMoveContext context = holder.plasticraft$getCarrierMoveContext();
            // 原版步进会复用首次水平查询的形状，承载重试必须保留当前仅接触的目标。
            if (context != null) {
                requestedMovement = context.requestedMovement();
                if (context.collidesDuringRetry(movable)) return shaped.plasticraft$getCollisionShape();
            }
        } else if (mover instanceof CarrierMoveContextHolder holder) {
            CarrierMoveContext context = holder.plasticraft$getCarrierMoveContext();
            if (context != null) requestedMovement = context.requestedMovement();
        }
        VoxelShape collisionShape = shaped.plasticraft$getCollisionShape(mover, requestedMovement);
        return Shapes.joinIsNotEmpty(Shapes.create(collisionBox), collisionShape, BooleanOp.AND)
            ? collisionShape
            : Shapes.empty();
    }

    @ModifyExpressionValue(
        method = "isUnobstructed",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/Shapes;create(Lnet/minecraft/world/phys/AABB;)"
                + "Lnet/minecraft/world/phys/shapes/VoxelShape;"
        )
    )
    private VoxelShape plasticraft$useShapedCollisionInObstructionQuery(
        VoxelShape original,
        @Local(ordinal = 1) Entity entity
    ) {
        return entity instanceof ShapedCollisionEntity shaped
            ? shaped.plasticraft$getCollisionShape()
            : original;
    }

    @ModifyReturnValue(method = "getEntityCollisions", at = @At("RETURN"))
    private List<VoxelShape> plasticraft$filterCollisionShapes(
        List<VoxelShape> original,
        Entity ignored,
        AABB ignoredCollisionBox
    ) {
        if (original.isEmpty()) return original;
        return original.stream().filter(shape -> !shape.isEmpty()).toList();
    }

    @ModifyExpressionValue(
        method = "getEntityCollisions",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/function/Predicate;and(Ljava/util/function/Predicate;)Ljava/util/function/Predicate;"
        )
    )
    private Predicate<Entity> plasticraft$allowCarrierMovement(
        Predicate<Entity> original,
        Entity mover
    ) {
        Predicate<Entity> withoutBondedMembers = target ->
            !EntityBondManager.areInSameComponent(mover, target)
                && !EntityBondManager.ignoresPreclippedCollision(mover, target)
                && original.test(target);
        if (!(mover instanceof CarrierMoveContextHolder holder)) return withoutBondedMembers;
        CarrierMoveContext context = holder.plasticraft$getCarrierMoveContext();
        if (context == null) return withoutBondedMembers;
        return target -> {
            boolean collides = withoutBondedMembers.test(target);
            if (!collides) return false;
            if (!(target instanceof CarrierMovableEntity movable)) {
                if (target instanceof ElasticCollisionEntity elastic) {
                    context.addElasticCollisionTarget(elastic);
                }
                return true;
            }
            if (context.collidesDuringRetry(movable)) return true;
            if (!movable.plasticraft$canMoveWithCarrier(mover, context.requestedMovement())) {
                if (target instanceof ElasticCollisionEntity elastic) {
                    context.addElasticCollisionTarget(elastic);
                }
                return true;
            }
            context.addTarget(movable);
            return false;
        };
    }
}
