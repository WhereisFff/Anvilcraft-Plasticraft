package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeContext;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.recipe.CauldronImpactRecipeProcessor;
import dev.anvilcraft.plasticraft.recipe.PlasticMeltRecipeColor;
import dev.dubhe.anvilcraft.api.entity.IEntityCauldron;
import dev.dubhe.anvilcraft.recipe.anvil.predicate.block.HasCauldron;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在炼药锅谓词修改流体前保存熔体颜色。 */
@Mixin(HasCauldron.class)
abstract class HasCauldronMixin {
    @Inject(method = "accept", at = @At("HEAD"))
    private void plasticraft$captureMeltColor(InWorldRecipeContext context, CallbackInfo ci) {
        PlasticMeltRecipeColor.captureCauldron(context, ((HasCauldron) (Object) this).offset());
    }

    /** 让流体谓词与物品缓存使用同一个落砧格目标，避免相邻实体锅串配方。 */
    @Inject(method = "findEntityCauldron", at = @At("RETURN"), cancellable = true)
    private static void plasticraft$selectTargetedCauldron(
        InWorldRecipeContext context,
        BlockPos pos,
        CallbackInfoReturnable<IEntityCauldron> cir
    ) {
        if (!CauldronImpactRecipeProcessor.hasTargetedRecipe()) return;
        HardenedResinCauldronEntity target = CauldronImpactRecipeProcessor.activeRecipeTarget();
        if (target != null
            && target.level() == context.getLevel()
            && !target.isRemoved()
            && target.getBoundingBox().intersects(new AABB(pos).inflate(0.0625D))) {
            cir.setReturnValue(target);
        } else if (cir.getReturnValue() instanceof HardenedResinCauldronEntity) {
            cir.setReturnValue(null);
        }
    }
}
