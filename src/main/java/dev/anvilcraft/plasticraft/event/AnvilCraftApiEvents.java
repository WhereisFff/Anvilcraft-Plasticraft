package dev.anvilcraft.plasticraft.event;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.block.HighViscosityResinFluidBlock;
import dev.anvilcraft.plasticraft.block.IgnitedFluidEffects;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveFallingBlockBehavior;
import dev.anvilcraft.plasticraft.entity.adhesive.SlidingAdhesionData;
import dev.anvilcraft.plasticraft.init.PlasticraftAttachments;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.molding.machine.PlasticMoldingAnvilProcessor;
import dev.anvilcraft.plasticraft.recipe.CauldronImpactRecipeProcessor;
import dev.anvilcraft.plasticraft.recipe.PlasticOilCatalysis;
import dev.anvilcraft.plasticraft.vapor.LargeCauldronVaporHost;
import dev.anvilcraft.plasticraft.vapor.VaporizationManager;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.api.event.FishTankEvent;
import dev.dubhe.anvilcraft.api.event.GiantAnvilEvent;
import dev.dubhe.anvilcraft.api.event.LargeCauldronEvent;
import dev.dubhe.anvilcraft.api.event.SlidingBlockEvent;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.entity.SlidingBlockEntity;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.block.ModFluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Triple;

/** 监听铁砧工艺生命周期事件。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID)
public final class AnvilCraftApiEvents {
    private static final Map<SlidingBlockEntity, BondedFallingBlocks.MovementSnapshot> SLIDING_SNAPSHOTS =
        new IdentityHashMap<>();

    private AnvilCraftApiEvents() {
    }

    @SubscribeEvent
    public static void beforeInWorldRecipe(AnvilEvent.BeforeInWorldRecipe event) {
        if (event.getLanding().getLevel() instanceof ServerLevel level) {
            CauldronImpactRecipeProcessor.beginEventRecipeProcessing(level, event.getLanding());
        }
    }

    @SubscribeEvent
    public static void afterInWorldRecipe(AnvilEvent.AfterInWorldRecipe event) {
        if (event.getLanding().getLevel() instanceof ServerLevel) {
            CauldronImpactRecipeProcessor.finishEventRecipeProcessing();
        }
    }

    @SubscribeEvent
    public static void onSlidingStart(SlidingBlockEvent.Start event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        List<BlockPos> positions = new ArrayList<>();
        for (Triple<BlockPos, BlockState, Optional<BlockEntity>> block : event.getBlocks()) {
            positions.add(block.getLeft().immutable());
        }
        BondedFallingBlocks.MovementSnapshot snapshot = BondedFallingBlocks.takeForMovement(serverLevel, positions);
        SLIDING_SNAPSHOTS.put(event.getEntity(), snapshot);
        if (!snapshot.isEmpty()) {
            event.getEntity().setData(
                PlasticraftAttachments.SLIDING_BLOCK_ADHESION,
                SlidingAdhesionData.from(snapshot, event.getOrigin())
            );
        }
    }

    @SubscribeEvent
    public static void onSlidingStop(SlidingBlockEvent.Stop event) {
        SlidingBlockEntity entity = event.getEntity();
        BondedFallingBlocks.MovementSnapshot snapshot = SLIDING_SNAPSHOTS.remove(entity);
        if (!(event.getLevel() instanceof ServerLevel serverLevel)
            || snapshot == null
            || snapshot.isEmpty()) {
            return;
        }
        BlockPos offset = entity.blockPosition().subtract(entity.getStartPos());
        BondedFallingBlocks.restoreAfterMovement(serverLevel, snapshot, offset);
    }

    @SubscribeEvent
    public static void onGiantMultiblock(GiantAnvilEvent.Multiblock event) {
        if (PlasticMoldingAnvilProcessor.handleLanding(event.getLanding())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGiantBlockTick(GiantAnvilEvent.BlockTick event) {
        BlockState state = event.getState();
        if (!state.hasProperty(GiantAnvilBlock.HALF)) return;
        Cube3x3PartHalf currentPart = state.getValue(GiantAnvilBlock.HALF);
        BlockPos bottomCenter = event.getPos().subtract(currentPart.getOffset());
        GiantAnvilBlock block = event.getBlock();
        for (Cube3x3PartHalf part : block.getParts()) {
            if (!BondedFallingBlocks.isBonded(event.getLevel(), bottomCenter.offset(part.getOffset()))) continue;
            event.getLevel().scheduleTick(bottomCenter, block, 2);
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void notifyCatalyticPress(GiantAnvilEvent.FallingTick event) {
        CatalyticPressAnvilEvents.beforeFallingAnvilTick(event.getEntity());
    }

    @SubscribeEvent
    public static void holdAdhesiveGiantAnvil(GiantAnvilEvent.FallingTick event) {
        if (AdhesiveFallingBlockBehavior.beforeTick(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void tickLargeCauldron(LargeCauldronEvent.ServerTick event) {
        LargeCauldronBlockEntity cauldron = event.getCauldron();
        VaporizationManager.tick(event.getServerLevel(), new LargeCauldronVaporHost(cauldron));
        PlasticOilCatalysis.tickLargeCauldron(event.getServerLevel(), cauldron);
    }

    @SubscribeEvent
    public static void stickInLargeCauldron(LargeCauldronEvent.EntityInside event) {
        HighViscosityResinFluidBlock.stickEntityInContainer(
            event.getState(),
            event.getLevel(),
            event.getPos(),
            event.getEntity()
        );
    }

    @SubscribeEvent
    public static void igniteLargeCauldron(LargeCauldronEvent.UseItem event) {
        ItemStack stack = event.getStack();
        boolean flintAndSteel = stack.is(Items.FLINT_AND_STEEL);
        boolean fireCharge = stack.is(Items.FIRE_CHARGE);
        if (!flintAndSteel && !fireCharge) return;
        Level level = event.getLevel();
        LargeCauldronBlockEntity cauldron = LargeCauldronBlockEntity.getMain(
            level,
            event.getPos(),
            event.getState()
        );
        if (cauldron == null || !cauldron.getTopFluid().is(ModFluidTags.IGNITABLE)) return;
        Player player = event.getPlayer();
        InteractionHand hand = event.getHand();
        if (cauldron.isIgnited()) {
            event.setResult(ItemInteractionResult.sidedSuccess(level.isClientSide()));
            event.setCanceled(true);
            return;
        }
        if (!level.isClientSide()) {
            cauldron.setIgnited(true);
            if (flintAndSteel) {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            } else if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(
                null,
                cauldron.getBlockPos(),
                flintAndSteel ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.FIRECHARGE_USE,
                SoundSource.BLOCKS
            );
        }
        event.setResult(ItemInteractionResult.sidedSuccess(level.isClientSide()));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void disguiseMoldedGiantAnvil(LargeCauldronEvent.GiantAnvilImpact event) {
        if (event.getLanding().getEntity() instanceof UniversalPlasticEntity plastic
            && plastic.isMoldedGiantAnvil()) {
            event.setLandedAnvilState(
                ModBlocks.GIANT_ANVIL.getDefaultState()
                    .setValue(GiantAnvilBlock.HALF, Cube3x3PartHalf.BOTTOM_CENTER)
            );
        }
    }

    @SubscribeEvent
    public static void largeCauldronFluidDamage(LargeCauldronEvent.FluidDamage event) {
        event.setDamage(IgnitedFluidEffects.damageFor(event.getFluid(), event.getDamage()));
    }

    @SubscribeEvent
    public static void colorMixedGranules(LargeCauldronEvent.MixingOutput event) {
        ItemStack result = event.getResult();
        if (!result.is(PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE.get())) return;
        DyeColor color = DyeColor.WHITE;
        for (FluidStack fluid : event.getCauldron().getFluids().copyFluids()) {
            if (!fluid.is(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get())) continue;
            color = PlasticMeltColor.get(fluid);
            break;
        }
        PlasticMeltColor.set(result, color);
        event.setResult(result);
    }

    @SubscribeEvent
    public static void tickFishTank(FishTankEvent.ServerTick event) {
        PlasticOilCatalysis.tickFishTank(event.getServerLevel(), event.getTank());
    }

    @SubscribeEvent
    public static void stickInFishTank(FishTankEvent.EntityInside event) {
        HighViscosityResinFluidBlock.stickEntityInContainer(
            event.getState(),
            event.getLevel(),
            event.getPos(),
            event.getEntity()
        );
    }

    @SubscribeEvent
    public static void fishTankFluidDamage(FishTankEvent.FluidDamage event) {
        event.setDamage(IgnitedFluidEffects.damageFor(event.getFluid(), event.getDamage()));
    }
}
