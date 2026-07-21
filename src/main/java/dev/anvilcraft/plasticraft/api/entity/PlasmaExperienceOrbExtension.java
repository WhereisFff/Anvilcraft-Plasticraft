package dev.anvilcraft.plasticraft.api.entity;

import dev.dubhe.anvilcraft.api.entity.IAnvilCraftEntityExtension;
import dev.dubhe.anvilcraft.util.GravityType;

/** 标记由喷流灼烧产生的经验球，并让本体重力管理器施加反重力。 */
public interface PlasmaExperienceOrbExtension extends IAnvilCraftEntityExtension {
    boolean plasticraft$isPlasmaProduced();

    void plasticraft$setPlasmaProduced(boolean produced);

    @Override
    default GravityType anvilcraft$getGravityType() {
        return this.plasticraft$isPlasmaProduced() ? GravityType.ANTI_GRAVITY : null;
    }
}
