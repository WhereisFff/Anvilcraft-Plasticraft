package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayLightSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.neoforged.neoforge.common.extensions.IBlockStateExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 将没有实体方块占位的支架元件光源并入方块光计算。 */
@Mixin(IBlockStateExtension.class)
interface BlockStateLightEmissionMixin {
    @ModifyReturnValue(method = "getLightEmission", at = @At("RETURN"))
    private int plasticraft$addMoldedTrayLight(int original, BlockGetter getter, BlockPos position) {
        return Math.max(original, MoldedTrayLightSource.lightAt(getter, position));
    }
}
