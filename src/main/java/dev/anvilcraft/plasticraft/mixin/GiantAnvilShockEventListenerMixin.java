package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.dubhe.anvilcraft.event.giantanvil.shock.GiantAnvilShockEventListener;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 防止树脂反弹撼地把已经粘在支撑物上的塑料铁砧重新实体化。 */
@Mixin(GiantAnvilShockEventListener.class)
abstract class GiantAnvilShockEventListenerMixin {
    @Redirect(
        method = "lambda$static$25",
        at = @At(
            value = "INVOKE",
            target = """
                Lnet/minecraft/world/level/block/state/BlockState;getBlock()\
                Lnet/minecraft/world/level/block/Block;"""
        )
    )
    private static Block plasticraft$keepBondedPlasticAnvilFixed(BlockState state) {
        if (state.hasProperty(AbstractPlasticEntityBlock.BONDED)
            && state.getValue(AbstractPlasticEntityBlock.BONDED)) {
            return Blocks.AIR;
        }
        return state.getBlock();
    }
}
