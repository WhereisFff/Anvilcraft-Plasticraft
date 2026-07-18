package dev.anvilcraft.plasticraft.api.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A collision target that can move with an entity currently supporting it.
 * Implementations decide whether a requested carrier movement may pass through
 * their collision box, then receive the carrier's actual clipped movement.
 */
public interface CarrierMovableEntity {
    boolean plasticraft$canMoveWithCarrier(Entity carrier, Vec3 requestedMovement);

    void plasticraft$moveWithCarrier(Entity carrier, Vec3 actualMovement);

    /**
     * Returns whether this target can complete the carrier displacement without
     * being clipped by blocks, the world border, or unrelated entities.
     */
    boolean plasticraft$canCompleteCarrierMovement(Entity carrier, Vec3 requestedMovement);
}
