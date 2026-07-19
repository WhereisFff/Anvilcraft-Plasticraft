package dev.anvilcraft.plasticraft.api.entity;

import dev.dubhe.anvilcraft.api.entity.IAnvilCraftEntityExtension;
import dev.dubhe.anvilcraft.util.GravityType;

/**
 * Supplies the AnvilCraft gravity profile used by a Plasticraft entity.
 */
@FunctionalInterface
public interface PlasticGravityTypeProvider extends IAnvilCraftEntityExtension {
    GravityType plasticraft$getGravityType();

    @Override
    default GravityType anvilcraft$getGravityType() {
        return this.plasticraft$getGravityType();
    }
}
