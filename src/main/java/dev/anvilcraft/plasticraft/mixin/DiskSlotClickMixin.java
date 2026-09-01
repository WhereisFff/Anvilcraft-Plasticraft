package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 已部署结构磁盘在任意菜单中的右击覆盖:空手右击该槽位不再执行原版拿取。
 * 启动/停止由客户端专用包处理,这里只挡住原版拆半/拾取,避免创造物品栏槽位错位。
 */
@Mixin(AbstractContainerMenu.class)
abstract class DiskSlotClickMixin {
    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void plasticraft$blockPickupOnDeployedDisk(
        int slotId,
        int button,
        ClickType clickType,
        Player player,
        CallbackInfo callback
    ) {
        if (clickType != ClickType.PICKUP || button != 1 || slotId < 0) return;
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (!menu.getCarried().isEmpty()) return;
        if (slotId >= menu.slots.size()) return;
        Slot slot = menu.slots.get(slotId);
        ItemStack stack = slot.getItem();
        ConstructionBlueprintData data = ConstructionBlueprintData.get(stack).orElse(null);
        if (data == null || data.jobId().isEmpty()) return;
        callback.cancel();
    }
}
