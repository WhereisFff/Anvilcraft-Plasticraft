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
        for (int index = 0; index < this.targets.size(); index++) {
            CarrierMovableEntity existing = this.targets.get(index);
            if (existing == target) return;
            if (existing instanceof Entity existingEntity
                && target instanceof Entity targetEntity) {
                if (EntityBondManager.areInSameComponent(existingEntity, targetEntity)) return;
                // 玩家可能同时扫到锅和露出锅口的铁砧。只保留下层承载者，
                // 否则铁砧会先随锅移动、随后又被当作独立目标推动一次。
                if (target.plasticraft$canMoveWithCarrier(existingEntity, Vec3.ZERO)) return;
                if (existing.plasticraft$canMoveWithCarrier(targetEntity, Vec3.ZERO)) {
                    this.targets.remove(index--);
                }
            }
        }
        this.targets.add(target);
    }

    public boolean isRetryingCollision() {
        return this.retryingCollision;
    }

    public boolean collidesDuringRetry(CarrierMovableEntity target) {
        if (!this.retryingCollision || this.retryCollisionTargets == null) return false;
        for (CarrierMovableEntity blockedTarget : this.retryCollisionTargets) {
            if (movesWith(blockedTarget, target)) return true;
        }
        return false;
    }

    public boolean movesWithRecordedTarget(CarrierMovableEntity target) {
        if (this.targets == null) return false;
        for (CarrierMovableEntity recordedTarget : this.targets) {
            if (movesWith(recordedTarget, target)) return true;
        }
        return false;
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

    private static boolean movesWith(CarrierMovableEntity carrier, CarrierMovableEntity target) {
        if (carrier == target) return true;
        if (!(carrier instanceof Entity carrierEntity) || !(target instanceof Entity targetEntity)) {
            return false;
        }
        return EntityBondManager.areInSameComponent(carrierEntity, targetEntity)
            || target.plasticraft$canMoveWithCarrier(carrierEntity, Vec3.ZERO);
    }
}
