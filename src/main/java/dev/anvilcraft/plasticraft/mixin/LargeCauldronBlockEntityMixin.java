package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.IgnitedFluidEffects;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import dev.anvilcraft.plasticraft.init.item.ModItems;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 将大型炼药锅内高热燃料的燃烧伤害提高到普通燃料的两倍。 */
@Mixin(LargeCauldronBlockEntity.class)
abstract class LargeCauldronBlockEntityMixin {
    @Unique
    private DyeColor plasticraft$mixingColor = DyeColor.WHITE;

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
        for (net.neoforged.neoforge.fluids.FluidStack fluid : cauldron.getFluids().copyFluids()) {
            if (!fluid.is(ModFluids.UNIVERSAL_PLASTIC_MELT.get())) continue;
            this.plasticraft$mixingColor = PlasticMeltColor.get(fluid);
            break;
        }
    }

    @ModifyArg(
        method = "tryProcessFluidMixingRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lnet/neoforged/neoforge/items/ItemHandlerHelper;insertItem("
                + "Lnet/neoforged/neoforge/items/IItemHandler;"
                + "Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"
        ),
        index = 1
    )
    private ItemStack plasticraft$colorMixedGranules(ItemStack result) {
        if (result.is(ModItems.UNIVERSAL_PLASTIC_GRANULE.get())) {
            PlasticMeltColor.set(result, this.plasticraft$mixingColor);
        }
        return result;
    }
}
