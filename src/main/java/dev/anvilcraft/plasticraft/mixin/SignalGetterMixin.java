package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedPlasticRedstoneConductor;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayRedstoneNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.SignalGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 把离散几何表面的支架端口并入原版红石查询。 */
@Mixin(SignalGetter.class)
interface SignalGetterMixin {
    @ModifyReturnValue(method = "getSignal", at = @At("RETURN"))
    private int plasticraft$addTrayWeakSignal(int original, BlockPos sourcePos, Direction direction) {
        SignalGetter getter = (SignalGetter) (Object) this;
        return Math.max(original, Math.max(
            MoldedTrayRedstoneNetwork.weakSignal(getter, sourcePos, direction),
            MoldedPlasticRedstoneConductor.weakSignal(getter, sourcePos, direction)
        ));
    }

    @ModifyReturnValue(method = "getDirectSignal", at = @At("RETURN"))
    private int plasticraft$addTrayDirectSignal(int original, BlockPos sourcePos, Direction direction) {
        return Math.max(
            original,
            MoldedTrayRedstoneNetwork.directSignal((SignalGetter) (Object) this, sourcePos, direction)
        );
    }

    @ModifyReturnValue(method = "getControlInputSignal", at = @At("RETURN"))
    private int plasticraft$addTrayControlSignal(
        int original,
        BlockPos sourcePos,
        Direction direction,
        boolean diodesOnly
    ) {
        return Math.max(
            original,
            MoldedTrayRedstoneNetwork.controlSignal(
                (SignalGetter) (Object) this,
                sourcePos,
                direction,
                diodesOnly
            )
        );
    }
}
