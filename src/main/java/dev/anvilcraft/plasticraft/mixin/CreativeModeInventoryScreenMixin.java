package dev.anvilcraft.plasticraft.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.anvilcraft.plasticraft.client.gui.CreativeColorPickerOverlay;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItemGroups;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让 Plasticraft 创造标签中的多色物品通过公共叠加层选择具体颜色。 */
@Mixin(CreativeModeInventoryScreen.class)
abstract class CreativeModeInventoryScreenMixin
    extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {
    @Shadow
    private static CreativeModeTab selectedTab;
    @Shadow
    @Final
    private static SimpleContainer CONTAINER;

    @Unique
    @Nullable
    private CreativeColorPickerOverlay plasticraft$colorOverlay;
    @Unique
    @Nullable
    private ItemStack plasticraft$hoveredColorVariant;
    @Unique
    private int plasticraft$consumedMouseButtons;

    protected CreativeModeInventoryScreenMixin(
        CreativeModeInventoryScreen.ItemPickerMenu menu,
        Inventory inventory,
        Component title
    ) {
        super(menu, inventory, title);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void plasticraft$handleColorOverlayClick(
        double mouseX,
        double mouseY,
        int button,
        CallbackInfoReturnable<Boolean> cir
    ) {
        CreativeColorPickerOverlay overlay = this.plasticraft$validColorOverlay();
        if (overlay != null && overlay.contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            this.plasticraft$consumeMouseButton(button);
            if (button == 0 || button == 1) {
                overlay.variantAt(this.leftPos, this.topPos, mouseX, mouseY)
                    .ifPresent(variant -> this.plasticraft$selectColorVariant(overlay, variant, button));
            }
            cir.setReturnValue(true);
            return;
        }

        Slot sourceSlot = this.plasticraft$findCreativeSlot(mouseX, mouseY);
        if (overlay != null) {
            this.plasticraft$closeColorOverlay();
            if (button == 1 && sourceSlot == overlay.sourceSlot()) {
                this.plasticraft$consumeMouseButton(button);
                cir.setReturnValue(true);
                return;
            }
        }
        if (button != 1 || selectedTab != PlasticraftItemGroups.MAIN.get()) return;
        if (sourceSlot == null) return;
        CreativeColorPickerOverlay.create(sourceSlot).ifPresent(created -> {
            this.plasticraft$colorOverlay = created;
            this.plasticraft$hoveredColorVariant = null;
            this.plasticraft$consumeMouseButton(button);
            cir.setReturnValue(true);
        });
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void plasticraft$finishColorOverlayClick(
        double mouseX,
        double mouseY,
        int button,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!this.plasticraft$isMouseButtonConsumed(button)) return;
        this.plasticraft$releaseMouseButton(button);
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void plasticraft$blockColorOverlayDrag(
        double mouseX,
        double mouseY,
        int button,
        double dragX,
        double dragY,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (this.plasticraft$isMouseButtonConsumed(button)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"))
    private void plasticraft$closeColorOverlayOnScroll(
        double mouseX,
        double mouseY,
        double scrollX,
        double scrollY,
        CallbackInfoReturnable<Boolean> cir
    ) {
        this.plasticraft$closeColorOverlay();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void plasticraft$handleColorOverlayKey(
        int keyCode,
        int scanCode,
        int modifiers,
        CallbackInfoReturnable<Boolean> cir
    ) {
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (keyCode == InputConstants.KEY_ESCAPE
            || this.minecraft.options.keyInventory.isActiveAndMatches(key)) {
            this.plasticraft$closeColorOverlay();
            return;
        }
        CreativeColorPickerOverlay overlay = this.plasticraft$validColorOverlay();
        ItemStack variant = this.plasticraft$hoveredColorVariant;
        if (overlay == null || variant == null
            || !this.minecraft.options.keyDrop.isActiveAndMatches(key)) return;
        this.plasticraft$clickColorVariant(
            overlay,
            variant,
            hasControlDown() ? 1 : 0,
            ClickType.THROW
        );
        cir.setReturnValue(true);
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void plasticraft$clearColorOverlay(CallbackInfo ci) {
        this.plasticraft$closeColorOverlay();
        this.plasticraft$consumedMouseButtons = 0;
    }

    @Inject(
        method = "checkTabHovering",
        at = @At("HEAD"),
        cancellable = true
    )
    private void plasticraft$hideCoveredTabTooltip(
        GuiGraphics graphics,
        CreativeModeTab tab,
        int mouseX,
        int mouseY,
        CallbackInfoReturnable<Boolean> cir
    ) {
        CreativeColorPickerOverlay overlay = this.plasticraft$validColorOverlay();
        if (overlay != null && overlay.contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen;"
                + "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V"
        )
    )
    private void plasticraft$renderColorOverlay(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        float partialTick,
        CallbackInfo ci
    ) {
        CreativeColorPickerOverlay overlay = this.plasticraft$validColorOverlay();
        if (overlay == null) {
            this.plasticraft$hoveredColorVariant = null;
            return;
        }
        this.plasticraft$hoveredColorVariant = overlay.variantAt(
            this.leftPos,
            this.topPos,
            mouseX,
            mouseY
        ).orElse(null);
        overlay.render(
            graphics,
            this.leftPos,
            this.topPos,
            mouseX,
            mouseY,
            this.menu.getCarried()
        );
        if (overlay.contains(this.leftPos, this.topPos, mouseX, mouseY)) this.hoveredSlot = null;
    }

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen;"
                + "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            shift = At.Shift.AFTER
        )
    )
    private void plasticraft$renderColorOverlayTooltip(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        float partialTick,
        CallbackInfo ci
    ) {
        CreativeColorPickerOverlay overlay = this.plasticraft$validColorOverlay();
        ItemStack variant = this.plasticraft$hoveredColorVariant;
        if (overlay == null || variant == null) return;
        Slot previousHoveredSlot = this.hoveredSlot;
        this.hoveredSlot = overlay.sourceSlot();
        try {
            overlay.renderTooltip(
                graphics,
                this.font,
                this.getTooltipFromContainerItem(variant),
                variant,
                mouseX,
                mouseY
            );
        } finally {
            this.hoveredSlot = previousHoveredSlot;
        }
    }

    @Unique
    @Nullable
    private CreativeColorPickerOverlay plasticraft$validColorOverlay() {
        CreativeColorPickerOverlay overlay = this.plasticraft$colorOverlay;
        if (overlay == null) return null;
        if (selectedTab != PlasticraftItemGroups.MAIN.get() || !overlay.isValid()) {
            this.plasticraft$closeColorOverlay();
            return null;
        }
        return overlay;
    }

    @Unique
    @Nullable
    private Slot plasticraft$findCreativeSlot(double mouseX, double mouseY) {
        for (Slot slot : this.menu.slots) {
            if (slot.container != CONTAINER || !slot.isActive()) continue;
            double slotLeft = this.leftPos + slot.x - 1.0D;
            double slotTop = this.topPos + slot.y - 1.0D;
            if (mouseX >= slotLeft && mouseX < slotLeft + 18.0D
                && mouseY >= slotTop && mouseY < slotTop + 18.0D) {
                return slot;
            }
        }
        return null;
    }

    @Unique
    private void plasticraft$selectColorVariant(
        CreativeColorPickerOverlay overlay,
        ItemStack variant,
        int button
    ) {
        ClickType clickType = hasShiftDown() ? ClickType.QUICK_MOVE : ClickType.PICKUP;
        this.plasticraft$clickColorVariant(overlay, variant, button, clickType);
    }

    @Unique
    private void plasticraft$clickColorVariant(
        CreativeColorPickerOverlay overlay,
        ItemStack variant,
        int button,
        ClickType clickType
    ) {
        Slot sourceSlot = overlay.sourceSlot();
        ItemStack original = sourceSlot.getItem();
        sourceSlot.set(variant);
        try {
            this.slotClicked(sourceSlot, sourceSlot.index, button, clickType);
        } finally {
            sourceSlot.set(original);
        }
    }

    @Unique
    private void plasticraft$closeColorOverlay() {
        this.plasticraft$colorOverlay = null;
        this.plasticraft$hoveredColorVariant = null;
    }

    @Unique
    private void plasticraft$consumeMouseButton(int button) {
        if (button >= 0 && button < Integer.SIZE) {
            this.plasticraft$consumedMouseButtons |= 1 << button;
        }
    }

    @Unique
    private boolean plasticraft$isMouseButtonConsumed(int button) {
        return button >= 0 && button < Integer.SIZE
            && (this.plasticraft$consumedMouseButtons & 1 << button) != 0;
    }

    @Unique
    private void plasticraft$releaseMouseButton(int button) {
        if (button >= 0 && button < Integer.SIZE) {
            this.plasticraft$consumedMouseButtons &= ~(1 << button);
        }
    }
}
