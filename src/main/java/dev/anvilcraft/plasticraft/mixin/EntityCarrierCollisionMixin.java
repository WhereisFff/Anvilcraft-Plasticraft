package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.anvilcraft.plasticraft.api.entity.CarrierMovableEntity;
import dev.anvilcraft.plasticraft.api.entity.ElasticCollisionEntity;
import dev.anvilcraft.plasticraft.entity.collision.CarrierMoveContext;
import dev.anvilcraft.plasticraft.entity.collision.CarrierMoveContextHolder;
import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityPhysics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

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

    @ModifyReturnValue(method = "collide", at = @At("RETURN"))
    private Vec3 plasticraft$limitCarrierMovement(Vec3 actualMovement, Vec3 requestedMovement) {
        Entity self = (Entity) (Object) this;
        CarrierMoveContext context = this.plasticraft$carrierMoveContext;
        if (context == null || context.isRetryingCollision()) return actualMovement;
        List<CarrierMovableEntity> targets = context.targets();
        if (targets == null || targets.isEmpty()) return actualMovement;

        Vec3 limitedMovement = actualMovement;
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
            context.beginCollisionRetry(stepBlockedTargets);
            try {
                return this.plasticraft$invokeCollide(requestedMovement);
            } finally {
                context.endCollisionRetry();
            }
        }
        return limitedMovement;
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void plasticraft$finishCarrierMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        CarrierMoveContext context = this.plasticraft$carrierMoveContext;
        if (context == null) return;
        this.plasticraft$carrierMoveContext = context.parent();
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
