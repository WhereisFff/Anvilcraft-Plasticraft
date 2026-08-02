package dev.anvilcraft.plasticraft.event;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveBondingService;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveFaces;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesivePreviewService;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveSelectionManager;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.init.PlasticraftAttachments;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 为非标准输入路径提供服务端树脂桶交互兜底。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID)
public final class HighViscosityResinAdhesionEvents {
    private HighViscosityResinAdhesionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void knockbackStartsAdhesiveRebound(LivingKnockBackEvent event) {
        if (event.getEntity().level().isClientSide || !hasAdhesive(event.getEntity())) return;
        double resistance = event.getEntity().getAttributeValue(
            Attributes.KNOCKBACK_RESISTANCE
        );
        double effectiveStrength = event.getStrength() * Math.max(0.0D, 1.0D - resistance);
        Entity knockbackTarget = AdhesiveBondingService.prepareKnockback(
            event.getEntity(),
            effectiveStrength
        );
        if (knockbackTarget == event.getEntity()) return;
        event.setCanceled(true);
        AdhesiveBondingService.applyKnockback(
            knockbackTarget,
            effectiveStrength,
            event.getRatioX(),
            event.getRatioZ()
        );
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void teleportBreaksAdhesive(EntityTeleportEvent event) {
        releaseEntity(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void dimensionTravelBreaksAdhesive(EntityTravelToDimensionEvent event) {
        releaseEntity(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void deathBreaksEntityBonds(LivingDeathEvent event) {
        releaseEntity(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interactEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity().level().isClientSide
            || event.getEntity().isShiftKeyDown()
            || !event.getItemStack().is(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())) {
            return;
        }
        handleEntityUse(
            event.getEntity(),
            event.getHand(),
            event.getTarget(),
            AdhesiveFaces.hitFace(event.getEntity(), event.getTarget())
        );
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interactEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getEntity().level().isClientSide
            || event.getEntity().isShiftKeyDown()
            || !event.getItemStack().is(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())) {
            return;
        }
        handleEntityUse(
            event.getEntity(),
            event.getHand(),
            event.getTarget(),
            AdhesiveFaces.hitFaceFromLocal(event.getTarget(), event.getLocalPos())
        );
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().isShiftKeyDown()
            || !event.getItemStack().is(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())) {
            return;
        }
        if (!AdhesiveSelectionManager.hasSelection(event.getEntity())) {
            // 短按重放普通交互时只允许方块处理，避免树脂桶退回流体放置。
            event.setUseItem(TriState.FALSE);
            return;
        }
        if (event.getEntity().level().isClientSide) return;
        boolean bonded = AdhesivePreviewService.confirmBlock(
            event.getEntity(),
            event.getHand(),
            event.getPos(),
            event.getFace()
        );
        event.setCancellationResult(bonded ? InteractionResult.CONSUME : InteractionResult.FAIL);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        AdhesiveSelectionManager.clear(event.getEntity());
    }

    @SubscribeEvent
    public static void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        AdhesiveSelectionManager.clear(event.getEntity());
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        AdhesivePreviewService.tick(event.getServer());
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!AdhesiveSelectionManager.hasSelection(event.getEntity())) return;
        boolean holdingBucket = event.getEntity().getMainHandItem()
            .is(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())
            || event.getEntity().getOffhandItem().is(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get());
        if (!holdingBucket) {
            AdhesiveSelectionManager.clear(event.getEntity());
            return;
        }
        if (!event.getEntity().level().isClientSide) {
            var selected = AdhesiveSelectionManager.resolveServerSelection(event.getEntity());
            if (selected == null || selected.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT)) {
                AdhesiveSelectionManager.clear(event.getEntity());
            }
        }
    }

    private static void handleEntityUse(
        Player player,
        InteractionHand hand,
        Entity target,
        Direction hitFace
    ) {
        if (AdhesiveBondingService.reclaimEntity(player, hand, target, hitFace)) return;
        Entity selected = AdhesiveSelectionManager.resolveServerSelection(player);
        if (selected != null && selected != target) {
            AdhesivePreviewService.confirmEntity(player, hand, target, hitFace);
        } else {
            AdhesiveBondingService.select(player, hand, target, hitFace);
        }
    }

    private static boolean hasAdhesive(Entity entity) {
        return entity.hasData(PlasticraftAttachments.ENTITY_ADHESION) || EntityBondManager.hasBonds(entity);
    }

    private static void releaseEntity(Entity entity) {
        AdhesiveBondingService.release(entity);
        if (entity.level() instanceof ServerLevel level) EntityBondManager.disconnectEntity(level, entity);
    }

}
