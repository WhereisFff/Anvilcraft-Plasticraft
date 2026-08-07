package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.IgnitedFluidEffects;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.recipe.PlasticOilCatalysis;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 扩展大型炼药锅内的高热燃料伤害、熔体颜色与塑料油催化。 */
@Mixin(LargeCauldronBlockEntity.class)
abstract class LargeCauldronBlockEntityMixin {
    @Unique
    private DyeColor plasticraft$mixingColor = DyeColor.WHITE;

    @Redirect(
        method = "handleGiantAnvilImpact",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            ordinal = 0
        )
    )
    private BlockState plasticraft$acceptMoldedGiantAnvil(
        Level level,
        BlockPos pos,
        AnvilEvent.OnLand event
    ) {
        if (event.getEntity() instanceof UniversalPlasticEntity plastic && plastic.isMoldedGiantAnvil()) {
            return ModBlocks.GIANT_ANVIL.getDefaultState()
                .setValue(GiantAnvilBlock.HALF, Cube3x3PartHalf.BOTTOM_CENTER);
        }
        return level.getBlockState(pos);
    }

    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void plasticraft$tickPlasticOilCatalysis(
        Level level,
        BlockPos pos,
        BlockState state,
        LargeCauldronBlockEntity cauldron,
        CallbackInfo ci
    ) {
        if (level instanceof ServerLevel serverLevel) {
            PlasticOilCatalysis.tickLargeCauldron(serverLevel, cauldron);
        }
    }

    @ModifyArg(
        method = "applyFluidEffects",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
        ),
        index = 1
    )
    private float plasticraft$doubleHighHeatFuelDamage(float original) {
        LargeCauldronBlockEntity cauldron = (LargeCauldronBlockEntity) (Object) this;
        return IgnitedFluidEffects.damageFor(cauldron.getTopFluid(), original);
    }

    @Inject(method = "tryProcessFluidMixingRecipe", at = @At("HEAD"))
    private void plasticraft$captureMixingColor(ServerLevel level, CallbackInfoReturnable<Boolean> cir) {
        this.plasticraft$mixingColor = DyeColor.WHITE;
        LargeCauldronBlockEntity cauldron = (LargeCauldronBlockEntity) (Object) this;
        for (FluidStack fluid : cauldron.getFluids().copyFluids()) {
            if (!fluid.is(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get())) continue;
            this.plasticraft$mixingColor = PlasticMeltColor.get(fluid);
            break;
        }
    }

    @ModifyArg(
        method = "tryProcessFluidMixingRecipe",
        at = @At(
            value = "INVOKE",
            target = """
                Lnet/neoforged/neoforge/items/ItemHandlerHelper;insertItem(\
                Lnet/neoforged/neoforge/items/IItemHandler;\
                Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"""
        ),
        index = 1
    )
    private ItemStack plasticraft$colorMixedGranules(ItemStack result) {
        if (result.is(PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE.get())) {
            PlasticMeltColor.set(result, this.plasticraft$mixingColor);
        }
        return result;
    }
}
