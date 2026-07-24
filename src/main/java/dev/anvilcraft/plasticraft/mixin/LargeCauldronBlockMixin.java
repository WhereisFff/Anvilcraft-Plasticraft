package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.HighViscosityResinFluidBlock;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.init.block.ModFluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让大型炼药锅内储存的高粘性树脂参与实体移动交互。 */
@Mixin(LargeCauldronBlock.class)
abstract class LargeCauldronBlockMixin {
    @Unique
    private static final ResourceLocation CONDENSER_TOWER_ID = ResourceLocation.fromNamespaceAndPath(
        "anvilcraftplasticraft",
        "condenser_tower"
    );

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void plasticraft$showCondenserPlacementShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context,
        CallbackInfoReturnable<VoxelShape> cir
    ) {
        if (state.getValue(LargeCauldronBlock.HALF).getOffsetY() == 2
            && context.isHoldingItem(BuiltInRegistries.ITEM.get(CONDENSER_TOWER_ID))) {
            cir.setReturnValue(Shapes.block());
        }
    }

    @Inject(method = "entityInside", at = @At("HEAD"))
    private void plasticraft$stickEntitiesInResin(
        BlockState state,
        Level level,
        BlockPos pos,
        Entity entity,
        CallbackInfo ci
    ) {
        HighViscosityResinFluidBlock.stickEntityInContainer(state, level, pos, entity);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void plasticraft$igniteTopFluidFromHand(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hit,
        CallbackInfoReturnable<ItemInteractionResult> cir
    ) {
        boolean flintAndSteel = stack.is(Items.FLINT_AND_STEEL);
        boolean fireCharge = stack.is(Items.FIRE_CHARGE);
        if (!flintAndSteel && !fireCharge) return;
        LargeCauldronBlockEntity cauldron = LargeCauldronBlockEntity.getMain(level, pos, state);
        if (cauldron == null || !cauldron.getTopFluid().is(ModFluidTags.IGNITABLE)) return;
        if (cauldron.isIgnited()) {
            cir.setReturnValue(ItemInteractionResult.sidedSuccess(level.isClientSide()));
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
        cir.setReturnValue(ItemInteractionResult.sidedSuccess(level.isClientSide()));
    }
}
