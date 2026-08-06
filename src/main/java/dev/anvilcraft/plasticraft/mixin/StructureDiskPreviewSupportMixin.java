package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.client.renderer.MoldingBlueprintDiskPreviewRenderer;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintDisk;
import dev.dubhe.anvilcraft.client.support.StructureDiskPreviewSupport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 上游没有结构磁盘预览扩展点，仅在磁盘携带成型蓝图时替换其方块结构渲染。 */
@Mixin(StructureDiskPreviewSupport.class)
abstract class StructureDiskPreviewSupportMixin {
    @Inject(method = "renderPreviewAt", at = @At("HEAD"), cancellable = true)
    private static void plasticraft$renderMoldingBlueprint(
        GuiGraphics graphics,
        ItemStack stack,
        int mouseX,
        int mouseY,
        CallbackInfo ci
    ) {
        if (!MoldingBlueprintDisk.hasBlueprintData(stack)) return;
        if (MoldingBlueprintDiskPreviewRenderer.renderPreviewAt(graphics, stack, mouseX, mouseY)) ci.cancel();
    }
}
