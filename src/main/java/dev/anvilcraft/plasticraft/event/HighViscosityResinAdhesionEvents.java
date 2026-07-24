package dev.anvilcraft.plasticraft.event;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveBondingService;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveFaces;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveSelectionManager;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.init.ModAttachments;
import dev.anvilcraft.plasticraft.init.item.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.common.util.TriState;

/** 为非标准输入路径提供服务端树脂桶交互兜底。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID)
public final class HighViscosityResinAdhesionEvents {
    private HighViscosityResinAdhesionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void strongKnockbackBreaksAdhesive(LivingKnockBackEvent event) {
        if (event.getOriginalStrength() < 2.5F
            || !hasAdhesive(event.getEntity())) {
            return;
        }
        releaseEntity(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void knockbackFiveAttackBreaksAdhesive(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide || !hasAdhesive(event.getTarget())) {
            return;
        }
        var enchantment = event.getEntity().registryAccess()
            .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
            .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK);
        if (event.getEntity().getWeaponItem().getEnchantments().getLevel(enchantment) >= 5) {
            releaseEntity(event.getTarget());
        }
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
            || !event.getItemStack().is(ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())) {
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
            || !event.getItemStack().is(ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())) {
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
        if (!event.getItemStack().is(ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())) {
            return;
        }
        if (!AdhesiveSelectionManager.hasSelection(event.getEntity())) {
            // 短按重放普通交互时只允许方块处理，避免树脂桶退回流体放置。
            event.setUseItem(TriState.FALSE);
            return;
        }
        if (event.getEntity().level().isClientSide) return;
        boolean bonded = AdhesiveBondingService.bondSelected(
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
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!AdhesiveSelectionManager.hasSelection(event.getEntity())) return;
        boolean holdingBucket = event.getEntity().getMainHandItem()
            .is(ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())
            || event.getEntity().getOffhandItem().is(ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get());
        if (!holdingBucket) {
            AdhesiveSelectionManager.clear(event.getEntity());
            return;
        }
        if (!event.getEntity().level().isClientSide) {
            var selected = AdhesiveSelectionManager.resolveServerSelection(event.getEntity());
            if (selected == null
                || selected.hasData(ModAttachments.ENTITY_ADHESION)
                || selected.hasData(ModAttachments.ADHESIVE_TRANSIT)) {
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
        Entity selected = AdhesiveSelectionManager.resolveServerSelection(player);
        if (selected != null && selected != target) {
            AdhesiveBondingService.bondSelectedToEntity(player, hand, target, hitFace);
        } else {
            AdhesiveBondingService.select(player, hand, target, hitFace);
        }
    }

    private static boolean hasAdhesive(Entity entity) {
        return entity.hasData(ModAttachments.ENTITY_ADHESION) || EntityBondManager.hasBonds(entity);
    }

    private static void releaseEntity(Entity entity) {
        AdhesiveBondingService.release(entity);
        if (entity.level() instanceof ServerLevel level) EntityBondManager.disconnectEntity(level, entity);
    }

}
