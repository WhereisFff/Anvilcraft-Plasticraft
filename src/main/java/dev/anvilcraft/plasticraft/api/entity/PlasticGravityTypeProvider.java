package dev.anvilcraft.plasticraft.api.entity;

import dev.dubhe.anvilcraft.util.GravityType;

/**
 * Supplies the AnvilCraft gravity profile used by a Plasticraft entity.
 */
@FunctionalInterface
public interface PlasticGravityTypeProvider {
    GravityType plasticraft$getGravityType();
}
