package dev.anvilcraft.plasticraft.client.event;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.client.renderer.PlasticCollisionOutlineRenderer;
import dev.anvilcraft.plasticraft.entity.collision.BondedPlasticShapeIndex;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityCollisionBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/** 用方块化塑料的真实凸碰撞棱线替换原版轴对齐选中轮廓。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class BondedPlasticHighlightRenderer {
    private BondedPlasticHighlightRenderer() {
    }

    @SubscribeEvent
    public static void renderBlockHighlight(RenderHighlightEvent.Block event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        BlockPos pos = event.getTarget().getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded)
            || !bonded.isInitialized()
            || !bonded.isPlastic()) {
            return;
        }

        BondedPlasticShapeIndex.Entry entry = BondedPlasticShapeIndex.entryAt(level, pos);
        if (entry == null || !BondedPlasticShapeIndex.isCurrent(level, pos)) return;
        PlasticEntityCollisionBox collisionBox = entry.collisionBox();
        if (!collisionBox.hasConvexComponents()) return;

        if (collisionBox.convexOutline().isEmpty()) return;
        PlasticCollisionOutlineRenderer.renderSelectionOutline(
            event.getPoseStack(),
            event.getMultiBufferSource().getBuffer(RenderType.lines()),
            collisionBox,
            event.getCamera().getPosition().scale(-1.0D)
        );
        event.setCanceled(true);
    }
}
