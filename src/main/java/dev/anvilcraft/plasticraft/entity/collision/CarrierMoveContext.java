package dev.anvilcraft.plasticraft.entity.collision;

import dev.anvilcraft.plasticraft.api.entity.CarrierMovableEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** One reentrant Entity.move invocation; targets are allocated only when needed. */
public final class CarrierMoveContext {
    @Nullable
    private final CarrierMoveContext parent;
    private final Vec3 startPosition;
    private final Vec3 requestedMovement;
    @Nullable
    private List<CarrierMovableEntity> targets;
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
        if (!this.targets.contains(target)) {
            this.targets.add(target);
        }
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
}
