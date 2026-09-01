package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintData;
import dev.anvilcraft.plasticraft.network.BlueprintToggleActivePacket;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * 任意容器界面空手右击已部署磁盘:在客户端消费这次点击并发送启动/停止包。
 * 创造物品栏的槽位编号与服务端 InventoryMenu 不一致,不能依赖原版点击包到达服务端。
 */
@Mixin(AbstractContainerScreen.class)
abstract class DiskSlotClickScreenMixin {
    @Shadow
    @Final
    protected AbstractContainerMenu menu;

    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void plasticraft$toggleBlueprintOnRightClick(
        double mouseX,
        double mouseY,
        int button,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (button != 1 || !this.menu.getCarried().isEmpty()) return;
        Slot slot = this.hoveredSlot;
        if (slot == null) return;
        ItemStack stack = slot.getItem();
        ConstructionBlueprintData data = ConstructionBlueprintData.get(stack).orElse(null);
        if (data == null) return;
        UUID jobId = data.jobId().orElse(null);
        if (jobId == null) return;
        PacketDistributor.sendToServer(new BlueprintToggleActivePacket(jobId));
        callback.setReturnValue(true);
    }
}
