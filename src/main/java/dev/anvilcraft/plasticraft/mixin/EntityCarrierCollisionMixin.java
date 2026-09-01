package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.anvilcraft.plasticraft.api.entity.CarrierMovableEntity;
import dev.anvilcraft.plasticraft.api.entity.ElasticCollisionEntity;
import dev.anvilcraft.plasticraft.api.entity.ShapedCollisionEntity;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.entity.collision.CarrierMoveContext;
import dev.anvilcraft.plasticraft.entity.collision.CarrierMoveContextHolder;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexCollisionResolver;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexShape;
import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityPhysics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 将支撑实体经过碰撞裁剪的位移传递给其承载对象。 */
@Mixin(Entity.class)
abstract class EntityCarrierCollisionMixin implements CarrierMoveContextHolder {
    @Unique
    @Nullable
    private CarrierMoveContext plasticraft$carrierMoveContext;

    @Invoker("collide")
    protected abstract Vec3 plasticraft$invokeCollide(Vec3 movement);

    @ModifyArg(
        method = "collide",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntityCollisions("
                + "Lnet/minecraft/world/entity/Entity;"
                + "Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
        ),
        index = 1
    )
    private AABB plasticraft$includeAdjacentCarrierContacts(AABB queryBounds) {
        return queryBounds.inflate(
            PlasticEntityPhysics.SUPPORT_PROBE_DEPTH + PlasticEntityPhysics.FACE_EPSILON
        );
    }

    @Inject(method = "move", at = @At("HEAD"))
    private void plasticraft$beginCarrierMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        this.plasticraft$carrierMoveContext = new CarrierMoveContext(
            this.plasticraft$carrierMoveContext,
            self.position(),
            movement
        );
    }

    @Override
    public CarrierMoveContext plasticraft$getCarrierMoveContext() {
        return this.plasticraft$carrierMoveContext;
    }

    /** 仅替换原版首次 AABB 裁剪；后续承载限制仍由 RETURN 注入统一处理。 */
    @Redirect(
        method = "collide",
        at = @At(
            value = "INVOKE",
            target = """
                Lnet/minecraft/world/entity/Entity;collideBoundingBox(\
                Lnet/minecraft/world/entity/Entity;\
                Lnet/minecraft/world/phys/Vec3;\
                Lnet/minecraft/world/phys/AABB;\
                Lnet/minecraft/world/level/Level;\
                Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;"""
        )
    )
    private Vec3 plasticraft$collideWithPlasticShape(
        Entity entity,
        Vec3 movement,
        AABB collisionBox,
        Level level,
        List<VoxelShape> entityCollisions
    ) {
        return ShapedCollisionEntity.collideBoundingBox(
            entity,
            movement,
            collisionBox,
            level,
            entityCollisions
        );
    }

    /** 原版步进的候选位移也必须经过与首次移动相同的连续凸碰撞。 */
    @Redirect(
        method = "collide",
        at = @At(
            value = "INVOKE",
            target = """
                Lnet/minecraft/world/entity/Entity;collideWithShapes(\
                Lnet/minecraft/world/phys/Vec3;\
                Lnet/minecraft/world/phys/AABB;\
                Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;"""
        )
    )
    private Vec3 plasticraft$collideStepWithPlasticShape(
        Vec3 movement,
        AABB collisionBox,
        List<VoxelShape> colliders
    ) {
        Entity self = (Entity) (Object) this;
        return ShapedCollisionEntity.collideBoundingBox(
            self,
            movement,
            collisionBox,
            self.level(),
            colliders
        );
    }

    @ModifyReturnValue(method = "collide", at = @At("RETURN"))
    private Vec3 plasticraft$limitCarrierMovement(Vec3 actualMovement, Vec3 requestedMovement) {
        Entity self = (Entity) (Object) this;
        actualMovement = plasticraft$resolveExactStep(self, actualMovement, requestedMovement);
        CarrierMoveContext context = this.plasticraft$carrierMoveContext;
        if (context == null || context.isRetryingCollision()) return actualMovement;
        Vec3 componentLimitedMovement = EntityBondManager.clampLeaderMovement(self, actualMovement);
        List<CarrierMovableEntity> targets = context.targets();
        if (targets == null || targets.isEmpty()) return componentLimitedMovement;

        Vec3 limitedMovement = componentLimitedMovement;
        List<CarrierMovableEntity> stepBlockedTargets = null;
        boolean steppedUp = actualMovement.y > requestedMovement.y + PlasticEntityPhysics.FACE_EPSILON;
        for (CarrierMovableEntity target : targets) {
            Vec3 targetLimitedMovement = target.plasticraft$clampCarrierMovement(self, limitedMovement);
            if (steppedUp && targetLimitedMovement.distanceToSqr(limitedMovement)
                > PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
                if (stepBlockedTargets == null) stepBlockedTargets = new ArrayList<>(1);
                stepBlockedTargets.add(target);
            }
            limitedMovement = targetLimitedMovement;
        }
        if (stepBlockedTargets != null) {
            if (actualMovement.y > PlasticEntityPhysics.FACE_EPSILON) {
                context.beginCollisionRetry(stepBlockedTargets);
                try {
                    Vec3 steppedMovement = this.plasticraft$invokeCollide(requestedMovement);
                    if (isSafeSteppedCollision(self, steppedMovement, stepBlockedTargets)) {
                        return steppedMovement;
                    }
                } finally {
                    context.endCollisionRetry();
                }
            }
            context.beginCollisionRetry(stepBlockedTargets);
            try {
                AABB collisionBox = self.getBoundingBox();
                List<VoxelShape> collisions = self.level().getEntityCollisions(
                    self,
                    collisionBox.expandTowards(requestedMovement)
                );
                // 目标无法随动时只重新执行普通碰撞裁剪，不能借锅底或矮实体自动跨步进入其内部。
                Vec3 collisionLimitedMovement = ShapedCollisionEntity.collideBoundingBox(
                    self,
                    requestedMovement,
                    collisionBox,
                    self.level(),
                    collisions
                );
                // 已经轻微嵌入时，原版轴向裁剪不会主动退出交叠区域。
                // 水平位移还必须受承载预检的结果约束，才能在目标被墙挡住时保持在锅外。
                return new Vec3(
                    restrictRetryComponent(
                        collisionLimitedMovement.x,
                        limitedMovement.x,
                        requestedMovement.x
                    ),
                    collisionLimitedMovement.y,
                    restrictRetryComponent(
                        collisionLimitedMovement.z,
                        limitedMovement.z,
                        requestedMovement.z
                    )
                );
            } finally {
                context.endCollisionRetry();
            }
        }
        return limitedMovement;
    }

    private static Vec3 plasticraft$resolveExactStep(
        Entity entity,
        Vec3 fallbackMovement,
        Vec3 requestedMovement
    ) {
        AABB collisionBox = entity.getBoundingBox();
        AABB sweptBounds = collisionBox.expandTowards(requestedMovement).inflate(entity.maxUpStep());
        if (!PlasticConvexCollisionResolver.hasNonAxisAlignedExactObstacle(
            entity,
            requestedMovement,
            sweptBounds,
            entity.level()
        )) {
            return fallbackMovement;
        }
        Vec3 exactMovement = ShapedCollisionEntity.collideBoundingBox(
            entity,
            requestedMovement,
            collisionBox,
            entity.level(),
            List.of()
        );
        return PlasticConvexCollisionResolver.resolveStepMovement(
            entity,
            requestedMovement,
            collisionBox,
            exactMovement
        );
    }

    private static boolean isSafeSteppedCollision(
        Entity carrier,
        Vec3 movement,
        List<CarrierMovableEntity> targets
    ) {
        if (movement.y <= PlasticEntityPhysics.FACE_EPSILON) return false;
        List<PlasticConvexShape> movedCarrier = PlasticConvexCollisionResolver.collisionShapes(
            carrier,
            carrier.getBoundingBox().move(movement)
        );
        for (CarrierMovableEntity target : targets) {
            if (!(target instanceof Entity targetEntity) || targetEntity.isRemoved()) continue;
            List<PlasticConvexShape> targetShapes = PlasticConvexCollisionResolver.collisionShapes(
                targetEntity,
                targetEntity.getBoundingBox(),
                carrier,
                movement
            );
            if (PlasticConvexCollisionResolver.intersects(
                movedCarrier,
                targetShapes
            )) {
                return false;
            }
        }
        return true;
    }

    private static double restrictRetryComponent(double collisionMovement, double carrierMovement, double requested) {
        if (requested > 0.0D) {
            return Math.min(Math.max(0.0D, collisionMovement), Math.max(0.0D, carrierMovement));
        }
        if (requested < 0.0D) {
            return Math.max(Math.min(0.0D, collisionMovement), Math.min(0.0D, carrierMovement));
        }
        return 0.0D;
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void plasticraft$finishCarrierMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        CarrierMoveContext context = this.plasticraft$carrierMoveContext;
        if (context == null) return;
        this.plasticraft$carrierMoveContext = context.parent();
        if (EntityBondManager.isFollower(self)) return;
        Vec3 actualMovement = self.position().subtract(context.startPosition());
        AABB startBox = self.getBoundingBox().move(-actualMovement.x, -actualMovement.y, -actualMovement.z);
        Set<ElasticCollisionEntity> elasticTargets = new LinkedHashSet<>();
        List<ElasticCollisionEntity> recordedTargets = context.elasticCollisionTargets();
        if (recordedTargets != null) {
            elasticTargets.addAll(recordedTargets);
        }
        if (context.requestedMovement().lengthSqr() > 0.0D) {
            AABB sweptBox = startBox.expandTowards(context.requestedMovement()).inflate(0.08D);
            for (Entity target : self.level().getEntities(
                self,
                sweptBox,
                candidate -> candidate instanceof ElasticCollisionEntity
                    && !EntityBondManager.areInSameComponent(self, candidate)
            )) {
                elasticTargets.add((ElasticCollisionEntity) target);
            }
        }
        for (ElasticCollisionEntity target : elasticTargets) {
            target.plasticraft$onEntityCollision(
                self,
                startBox,
                context.requestedMovement(),
                actualMovement
            );
        }
        List<CarrierMovableEntity> targets = context.targets();
        if (targets == null || targets.isEmpty()) return;
        for (CarrierMovableEntity target : targets) {
            // RETURN 注入点在承载实体移动后执行，因此目标会暂时偏离支撑面。
            // collide 已在 setPos 前把承载者裁剪到目标能够完成的位移。
            target.plasticraft$moveWithCarrier(self, actualMovement);
        }
    }
}
