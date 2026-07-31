package dev.anvilcraft.plasticraft.mixin;

import dev.dubhe.anvilcraft.api.itemhandler.ItemHandlerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让溜槽等 AnvilCraft 设备也能发现不实现 ContainerEntity 的实体物品能力。 */
@Mixin(ItemHandlerUtil.class)
abstract class ItemHandlerUtilMixin {
    @Inject(method = "getSourceItemHandler", at = @At("RETURN"), cancellable = true)
    private static void plasticraft$findEntitySource(
        BlockPos pos,
        Direction side,
        Level level,
        CallbackInfoReturnable<IItemHandler> cir
    ) {
        if (level == null || cir.getReturnValue() != null) return;
        for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(pos), Entity::isAlive)) {
            IItemHandler handler = entity.getCapability(Capabilities.ItemHandler.ENTITY_AUTOMATION, side);
            if (handler == null) {
                handler = entity.getCapability(Capabilities.ItemHandler.ENTITY);
            }
            if (handler == null || !plasticraft$hasExtractableItem(handler)) continue;
            cir.setReturnValue(handler);
            return;
        }
    }

    @Unique
    private static boolean plasticraft$hasExtractableItem(IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.extractItem(slot, 1, true).isEmpty()) return true;
        }
        return false;
    }
}
