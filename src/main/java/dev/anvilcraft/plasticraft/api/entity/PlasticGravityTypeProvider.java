package dev.anvilcraft.plasticraft.api.entity;

import dev.dubhe.anvilcraft.api.entity.IAnvilCraftEntityExtension;
import dev.dubhe.anvilcraft.util.GravityType;

/**
 * 提供 Plasticraft 实体使用的 AnvilCraft 重力配置。
 */
@FunctionalInterface
public interface PlasticGravityTypeProvider extends IAnvilCraftEntityExtension {
    GravityType plasticraft$getGravityType();

    @Override
    default GravityType anvilcraft$getGravityType() {
        return this.plasticraft$getGravityType();
    }
}
