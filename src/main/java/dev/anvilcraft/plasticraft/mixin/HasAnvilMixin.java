package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeContext;
import dev.dubhe.anvilcraft.recipe.anvil.predicate.block.HasAnvil;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让默认铁砧谓词识别动态成型铁砧，同时不改变专用铁砧谓词。 */
@Mixin(HasAnvil.class)
abstract class HasAnvilMixin {
    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void plasticraft$testMoldedAnvil(
        InWorldRecipeContext context,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (!(context.getEntity() instanceof UniversalPlasticEntity plastic)
            || !plastic.isMoldedAnvil()) return;
        HasAnvil predicate = (HasAnvil) (Object) this;
        if (!predicate.anvil().getProperties().isEmpty() || !predicate.anvil().getNbts().isEmpty()) return;
        if (isGeneralAnvilPredicate(predicate)) {
            callback.setReturnValue(!predicate.inverted());
        }
    }

    private static boolean isGeneralAnvilPredicate(HasAnvil predicate) {
        if (predicate.anvil().getBlocks().size() == 0) return true;
        return predicate.anvil().getBlocks().unwrap().left()
            .map(BlockTags.ANVIL::equals)
            .orElseGet(() -> predicate.anvil().getBlocks().unwrap().right()
                .map(holders -> holders.size() == 1 && holders.getFirst().value() == Blocks.ANVIL)
                .orElse(false));
    }
}
