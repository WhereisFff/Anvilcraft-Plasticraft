package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintData;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintException;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintService;
import dev.anvilcraft.plasticraft.network.BlueprintImportResultPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 已部署结构磁盘在任意菜单中的右击覆盖:空手右击该槽位不再执行原版拿取,
 * 而是切换蓝图任务的启动状态;服务端在切换时保证每名玩家最多一份活动任务。
 */
@Mixin(AbstractContainerMenu.class)
abstract class DiskSlotClickMixin {
    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void plasticraft$toggleBlueprintOnRightClick(
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
        if (data == null) return;
        UUID jobId = data.jobId().orElse(null);
        if (jobId == null) return;
        callback.cancel();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        try {
            boolean active = ConstructionBlueprintService.toggleActive(serverPlayer, jobId);
            BlueprintImportResultPacket.sendSuccess(serverPlayer, active ? "started" : "stopped", data.name());
        } catch (ConstructionBlueprintException exception) {
            BlueprintImportResultPacket.sendFailure(serverPlayer, exception);
        }
    }
}
