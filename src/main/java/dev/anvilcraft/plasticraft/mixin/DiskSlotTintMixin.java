package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintData;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.client.blueprint.ClientBlueprintJobCache;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 已部署结构磁盘的槽底状态色:任意容器界面里在物品下方程序绘制半透明底色,
 * 黄色表示已放置未启动,绿色表示任务进行中;颜色跟随磁盘所在的任何菜单槽位。
 */
@Mixin(AbstractContainerScreen.class)
abstract class DiskSlotTintMixin {
    private static final int INACTIVE_TINT = 0x90FFD83D;
    private static final int ACTIVE_TINT = 0x9046C946;

    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void plasticraft$tintDeployedDiskSlot(GuiGraphics graphics, Slot slot, CallbackInfo callback) {
        ConstructionBlueprintData data = ConstructionBlueprintData.get(slot.getItem()).orElse(null);
        if (data == null) return;
        UUID jobId = data.jobId().orElse(null);
        if (jobId == null) return;
        ConstructionJob job = ClientBlueprintJobCache.job(jobId);
        if (job == null) return;
        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, job.isActive() ? ACTIVE_TINT : INACTIVE_TINT);
    }
}
