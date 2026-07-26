package dev.anvilcraft.plasticraft.entity.collision;

import dev.anvilcraft.plasticraft.api.entity.CarrierMovableEntity;
import dev.anvilcraft.plasticraft.api.entity.ElasticCollisionEntity;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** 一次可重入的 Entity.move 调用，仅在需要时分配目标集合。 */
public final class CarrierMoveContext {
    @Nullable
    private final CarrierMoveContext parent;
    private final Vec3 startPosition;
    private final Vec3 requestedMovement;
    @Nullable
    private List<CarrierMovableEntity> targets;
    @Nullable
    private List<ElasticCollisionEntity> elasticCollisionTargets;
    @Nullable
    private List<CarrierMovableEntity> retryCollisionTargets;
    private boolean retryingCollision;

    public CarrierMoveContext(
        @Nullable CarrierMoveContext parent,
        Vec3 startPosition,
        Vec3 requestedMovement
    ) {
        this.parent = parent;
        this.startPosition = startPosition;
        this.requestedMovement = requestedMovement;
    }

    @Nullable
    public CarrierMoveContext parent() {
        return this.parent;
    }

    public Vec3 startPosition() {
        return this.startPosition;
    }

    public Vec3 requestedMovement() {
        return this.requestedMovement;
    }

    @Nullable
    public List<CarrierMovableEntity> targets() {
        return this.targets;
    }

    public void addTarget(CarrierMovableEntity target) {
        if (this.targets == null) {
            this.targets = new ArrayList<>(1);
        }
        for (CarrierMovableEntity existing : this.targets) {
            if (existing == target) return;
            if (existing instanceof Entity existingEntity
                && target instanceof Entity targetEntity
                && EntityBondManager.areInSameComponent(existingEntity, targetEntity)) {
                return;
            }
        }
        this.targets.add(target);
    }

    public boolean isRetryingCollision() {
        return this.retryingCollision;
    }

    public boolean collidesDuringRetry(CarrierMovableEntity target) {
        return this.retryingCollision
            && this.retryCollisionTargets != null
            && this.retryCollisionTargets.contains(target);
    }

    public void beginCollisionRetry(List<CarrierMovableEntity> blockedTargets) {
        if (this.retryingCollision) {
            throw new IllegalStateException("Carrier collision retry is already active");
        }
        this.retryCollisionTargets = List.copyOf(blockedTargets);
        this.retryingCollision = true;
    }

    public void endCollisionRetry() {
        this.retryingCollision = false;
        this.retryCollisionTargets = null;
    }

    public void addElasticCollisionTarget(ElasticCollisionEntity target) {
        if (this.elasticCollisionTargets == null) {
            this.elasticCollisionTargets = new ArrayList<>(1);
        }
        if (!this.elasticCollisionTargets.contains(target)) {
            this.elasticCollisionTargets.add(target);
        }
    }

    @Nullable
    public List<ElasticCollisionEntity> elasticCollisionTargets() {
        return this.elasticCollisionTargets;
    }
}
