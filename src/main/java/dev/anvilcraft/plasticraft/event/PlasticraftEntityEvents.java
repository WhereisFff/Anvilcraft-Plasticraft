package dev.anvilcraft.plasticraft.event;

import dev.anvilcraft.lib.v2.recipe.event.ItemCacheEvent;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveBondingService;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.entity.adhesive.SurfaceAdhesiveService;
import dev.anvilcraft.plasticraft.entity.physics.PlasticFluidPhysics;
import dev.anvilcraft.plasticraft.recipe.EscapingVaporEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID)
public final class PlasticraftEntityEvents {
    private PlasticraftEntityEvents() {
    }

    @SubscribeEvent
    public static void beforeEntityTick(EntityTickEvent.Pre event) {
        AdhesiveBondingService.tickAdhesiveTransit(event.getEntity(), true);
        AdhesiveBondingService.tickBondedEntity(event.getEntity(), true);
        EntityBondManager.tick(event.getEntity());
        SurfaceAdhesiveService.tickEntity(event.getEntity());
    }

    @SubscribeEvent
    public static void afterEntityTick(EntityTickEvent.Post event) {
        AdhesiveBondingService.tickAdhesiveTransit(event.getEntity(), false);
        AdhesiveBondingService.tickBondedEntity(event.getEntity(), false);
        EntityBondManager.tick(event.getEntity());
        if (event.getEntity() instanceof ItemEntity item) {
            PlasticFluidPhysics.floatPlasticItem(item);
        }
        EscapingVaporEffects.tryIgniteOilVapor(event.getEntity());
    }

    @SubscribeEvent
    public static void captureCauldronRecipeOutput(ItemCacheEvent.SpawnItemEntity event) {
        ItemEntity item = event.getEntity();
        if (item.isRemoved()) return;
        BlockPos cell = item.blockPosition();
        HardenedResinCauldronEntity selected = null;
        for (HardenedResinCauldronEntity cauldron : item.level().getEntitiesOfClass(
            HardenedResinCauldronEntity.class,
            new AABB(cell),
            candidate -> !candidate.isRemoved()
                && BlockPos.containing(candidate.getBoundingBox().getCenter()).equals(cell)
        )) {
            if (selected == null || cauldron.getId() < selected.getId()) selected = cauldron;
        }
        if (selected != null) {
            ItemStack remaining = selected.insertRecipeOutput(item.getItem());
            if (remaining.isEmpty()) {
                item.discard();
            } else {
                item.setItem(remaining);
            }
            return;
        }

        if (item.level().getBlockEntity(cell) instanceof BondedEntityBlockEntity bonded
            && bonded.isInitialized()
            && bonded.isPlastic()) {
            ItemStack bondedRemaining = bonded.insertRecipeOutput(item.getItem());
            if (bondedRemaining.isEmpty()) {
                item.discard();
            } else {
                item.setItem(bondedRemaining);
            }
        }
    }
}
