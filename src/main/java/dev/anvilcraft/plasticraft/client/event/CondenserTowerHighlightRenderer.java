package dev.anvilcraft.plasticraft.client.event;

import dev.anvilcraft.lib.v2.cube.client.CubeSelection;
import dev.anvilcraft.lib.v2.cube.client.SelectionPart;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.client.selection.MachineBlockSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;

@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class CondenserTowerHighlightRenderer {
    private CondenserTowerHighlightRenderer() {
    }

    @SubscribeEvent
    public static void renderHighlight(ExtractBlockOutlineRenderStateEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || Minecraft.getInstance().options.hideGui) return;
        BlockPos pos = event.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CondenserTowerBlock tower)) return;
        if (!CubeSelection.isEnabled(state.getBlock()) || CubeSelection.isTargetExcluded(state)) {
            return;
        }
        SelectionPart outline = MachineBlockSelection.condenserOutline();
        if (outline == null) return;

        // 拾取仍按部件裁切，只将高亮锚定到中心，避免侧面接口命中串位与裁切边线。
        BlockPos main = tower.getMainPartPos(pos, state);
        PlasticEntityHighlightRenderer.addOutline(event, main, outline);
    }
}
