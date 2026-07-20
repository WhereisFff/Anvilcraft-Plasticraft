package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.HighViscosityResinFluidBlock;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 让大型炼药锅内储存的高粘性树脂参与实体移动交互。 */
@Mixin(LargeCauldronBlock.class)
abstract class LargeCauldronBlockMixin {
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
}
