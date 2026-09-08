package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeContext;
import dev.anvilcraft.plasticraft.entity.PlasticCauldron;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.recipe.MoldedLargeCauldronFluidRecipes;
import dev.anvilcraft.plasticraft.recipe.PlasticMeltRecipeColor;
import dev.anvilcraft.plasticraft.recipe.CauldronImpactRecipeProcessor;
import dev.dubhe.anvilcraft.api.entity.IEntityCauldron;
import dev.dubhe.anvilcraft.recipe.anvil.predicate.block.HasCauldron;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 实体锅配方按锁定锅格选择目标，并在修改流体前保存熔体颜色。 */
@Mixin(HasCauldron.class)
abstract class HasCauldronMixin {
    @Inject(method = "findEntityCauldron", at = @At("HEAD"), cancellable = true)
    private static void plasticraft$selectActiveRecipeTarget(
        InWorldRecipeContext context,
        BlockPos pos,
        CallbackInfoReturnable<IEntityCauldron> cir
    ) {
        PlasticCauldron target = CauldronImpactRecipeProcessor.activeRecipeTarget();
        BlockPos targetCell = CauldronImpactRecipeProcessor.activeRecipeTargetCell();
        if (target == null
            || targetCell == null
            || target.level() != context.getLevel()
            || target.isRemoved()
            || !targetCell.equals(pos)) {
            return;
        }
        cir.setReturnValue(target);
    }

    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void plasticraft$testLargeFluids(InWorldRecipeContext context, CallbackInfoReturnable<Boolean> cir) {
        HasCauldron predicate = (HasCauldron) (Object) this;
        UniversalPlasticEntity pot = MoldedLargeCauldronFluidRecipes.target(context, predicate);
        if (pot != null) cir.setReturnValue(MoldedLargeCauldronFluidRecipes.test(context, pot, predicate));
    }

    @Inject(method = "snapshot", at = @At("HEAD"), cancellable = true)
    private void plasticraft$snapshotLargeFluids(InWorldRecipeContext context, CallbackInfo ci) {
        HasCauldron predicate = (HasCauldron) (Object) this;
        UniversalPlasticEntity pot = MoldedLargeCauldronFluidRecipes.target(context, predicate);
        if (pot == null) return;
        MoldedLargeCauldronFluidRecipes.snapshot(context, pot, predicate);
        ci.cancel();
    }

    @Inject(method = "rollback", at = @At("HEAD"), cancellable = true)
    private void plasticraft$rollbackLargeFluids(InWorldRecipeContext context, CallbackInfo ci) {
        UniversalPlasticEntity pot = MoldedLargeCauldronFluidRecipes.target(context, (HasCauldron) (Object) this);
        if (pot == null) return;
        MoldedLargeCauldronFluidRecipes.rollback(context, pot);
        ci.cancel();
    }

    @Inject(method = "clearStack", at = @At("HEAD"), cancellable = true)
    private void plasticraft$clearLargeFluids(InWorldRecipeContext context, CallbackInfo ci) {
        UniversalPlasticEntity pot = MoldedLargeCauldronFluidRecipes.target(context, (HasCauldron) (Object) this);
        if (pot == null) return;
        MoldedLargeCauldronFluidRecipes.clear(context, pot);
        ci.cancel();
    }

    @Inject(method = "accept", at = @At("HEAD"), cancellable = true)
    private void plasticraft$captureMeltColor(InWorldRecipeContext context, CallbackInfo ci) {
        PlasticMeltRecipeColor.captureCauldron(context, ((HasCauldron) (Object) this).offset());
        if (MoldedLargeCauldronFluidRecipes.target(context, (HasCauldron) (Object) this) != null) {
            MoldedLargeCauldronFluidRecipes.accept(context);
            ci.cancel();
        }
    }
}
