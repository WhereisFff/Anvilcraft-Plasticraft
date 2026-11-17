package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticCauldron;
import dev.dubhe.anvilcraft.api.itemhandler.ItemHandlerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/** 让溜槽等本体装置从塑料制品的实际接触面访问自动化槽位。 */
@Mixin(ItemHandlerUtil.class)
abstract class PlasticItemTransferMixin {
    @ModifyReturnValue(method = "getSourceItemHandler", at = @At("RETURN"))
    private static @Nullable ResourceHandler<ItemResource> plasticraft$source(
        @Nullable ResourceHandler<ItemResource> original, BlockPos pos, Direction side, @Nullable Level level
    ) {
        if (original != null || level == null) {
            return original;
        }
        for (AbstractPlasticEntity entity : level.getEntitiesOfClass(AbstractPlasticEntity.class, new AABB(pos), Entity::isAlive)) {
            var items = entity.getCapability(Capabilities.Item.ENTITY_AUTOMATION, side);
            if (items != null && !ResourceHandlerUtil.isEmpty(items)) {
                return items;
            }
        }
        return null;
    }

    @ModifyReturnValue(method = "getTargetItemHandlerList", at = @At("RETURN"))
    private static @Nullable List<ResourceHandler<ItemResource>> plasticraft$targets(
        @Nullable List<ResourceHandler<ItemResource>> original, BlockPos pos, @Nullable Direction side, @Nullable Level level
    ) {
        if (level == null || original == null || level.getCapability(Capabilities.Item.BLOCK, pos, side) != null) {
            return original;
        }
        var entities = level.getEntitiesOfClass(AbstractPlasticEntity.class, new AABB(pos).inflate(0.01), Entity::isAlive);
        if (entities.isEmpty()) {
            return original;
        }
        List<ResourceHandler<ItemResource>> targets = new ArrayList<>(original);
        for (AbstractPlasticEntity entity : entities) {
            if (entity instanceof PlasticCauldron cauldron) {
                targets.remove(cauldron.getItemHandler());
            }
            var items = entity.getCapability(Capabilities.Item.ENTITY_AUTOMATION, side);
            if (items != null && !targets.contains(items)) {
                targets.add(items);
            }
        }
        return targets;
    }
}
