package dev.anvilcraft.plasticraft.client.event;

import dev.anvilcraft.lib.v2.cube.client.CubeSelection;
import dev.anvilcraft.lib.v2.cube.client.OutlineRenderer;
import dev.anvilcraft.lib.v2.cube.client.SelectionPart;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.client.selection.MachineBlockSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class CondenserTowerHighlightRenderer {
    private CondenserTowerHighlightRenderer() {
    }

    @SubscribeEvent
    public static void renderHighlight(RenderHighlightEvent.Block event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || Minecraft.getInstance().options.hideGui) return;
        BlockPos pos = event.getTarget().getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CondenserTowerBlock tower)) return;
        SelectionPart outline = MachineBlockSelection.condenserOutline();
        if (outline == null) return;

        // 拾取仍按部件裁切，只将高亮锚定到中心，避免侧面接口命中串位与裁切边线。
        BlockPos main = tower.getMainPartPos(pos, state);
        Vec3 camera = event.getCamera().getPosition();
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(main.getX() - camera.x, main.getY() - camera.y, main.getZ() - camera.z);
        outline.apply(event.getPoseStack());
        OutlineRenderer.render(event.getPoseStack(), event.getMultiBufferSource().getBuffer(RenderType.lines()),
            CubeSelection.outlines().get(outline.geometry()), 0, 0, 0, 0.4F);
        event.getPoseStack().popPose();
        event.setCanceled(true);
    }
}
