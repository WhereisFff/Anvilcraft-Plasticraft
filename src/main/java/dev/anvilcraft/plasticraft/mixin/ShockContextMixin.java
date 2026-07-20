package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.anvilcraft.plasticraft.init.block.ModBlockTags;
import dev.dubhe.anvilcraft.event.giantanvil.shock.ShockContext;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 让本体树脂撼地底座接受跨模组树脂兼容标签。 */
@Mixin(ShockContext.class)
abstract class ShockContextMixin {
    @ModifyReturnValue(method = "testCorner(Lnet/minecraft/core/Holder;)Z", at = @At("RETURN"))
    private boolean plasticraft$testTaggedCorner(boolean original, Holder<Block> expected) {
        if (original || expected.value() != dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK.get()) return original;
        return this.plasticraft$self().testCorner(ModBlockTags.RESIN_SHOCK_COMPATIBLE);
    }

    @ModifyReturnValue(method = "testBorder(Lnet/minecraft/core/Holder;)Z", at = @At("RETURN"))
    private boolean plasticraft$testTaggedBorder(boolean original, Holder<Block> expected) {
        if (original || expected.value() != dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK.get()) return original;
        return this.plasticraft$self().testBorder(ModBlockTags.RESIN_SHOCK_COMPATIBLE);
    }

    @ModifyReturnValue(method = "testCorner(Lnet/minecraft/world/level/block/Block;)Z", at = @At("RETURN"))
    private boolean plasticraft$testTaggedCorner(boolean original, Block expected) {
        if (original || expected != dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK.get()) return original;
        return this.plasticraft$self().testCorner(ModBlockTags.RESIN_SHOCK_COMPATIBLE);
    }

    @ModifyReturnValue(method = "testBorder(Lnet/minecraft/world/level/block/Block;)Z", at = @At("RETURN"))
    private boolean plasticraft$testTaggedBorder(boolean original, Block expected) {
        if (original || expected != dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK.get()) return original;
        return this.plasticraft$self().testBorder(ModBlockTags.RESIN_SHOCK_COMPATIBLE);
    }

    private ShockContext plasticraft$self() {
        return (ShockContext) (Object) this;
    }
}
