package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.recipe.CondenserTowerProcess;
import dev.dubhe.anvilcraft.block.HeaterBlock;
import dev.dubhe.anvilcraft.block.PlasmaJetsBlock;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.block.entity.PlasmaJetsBlockEntity;
import dev.dubhe.anvilcraft.block.entity.PlasmaJetsBlockEntity.TubeWallLayer;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** 喷流抬升到大型容器下方时保留顶层喷流，并处理普通容器气化。 */
@Mixin(PlasmaJetsBlockEntity.class)
abstract class PlasmaJetsBlockEntityMixin {
    @Shadow @Final private Set<TubeWallLayer> tubeWalls;
    @Shadow private BlockPos cauldronPos;

    @Inject(method = "tryRaise", at = @At("HEAD"), cancellable = true)
    private void plasticraft$stopAtLargeContainer(CallbackInfoReturnable<Boolean> cir) {
        PlasmaJetsBlockEntity self = (PlasmaJetsBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level != null && CondenserTowerProcess.isPlasmaPassThrough(
            level.getBlockState(self.getBlockPos().above()))) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "checkTubeWallIntegrity", at = @At("HEAD"), cancellable = true)
    private void plasticraft$keepInitialJetBelowContainer(Level level, CallbackInfo ci) {
        if (!this.tubeWalls.isEmpty()) return;

        BlockPos jetPos = ((PlasmaJetsBlockEntity) (Object) this).getBlockPos();
        if (!CondenserTowerProcess.isPlasmaPassThrough(level.getBlockState(jetPos.above()))) return;
        if (this.cauldronPos == null || !PlasmaJetsBlock.isValidBaseCauldron(level, this.cauldronPos)) return;

        BlockState heater = level.getBlockState(this.cauldronPos.below());
        if (!heater.is(ModBlocks.HEATER) || heater.getOptionalValue(HeaterBlock.OVERLOAD).orElse(true)) return;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = jetPos.relative(direction);
            if (!level.getBlockState(side).isFaceSturdy(level, side, direction.getOpposite())) return;
        }
        ci.cancel();
    }

    @Inject(method = "serverTick", at = @At("TAIL"))
    private void plasticraft$vaporizeSmallContainer(ServerLevel level, CallbackInfo ci) {
        PlasmaJetsBlockEntity self = (PlasmaJetsBlockEntity) (Object) this;
        if (!self.isRemoved() && level.getBlockState(self.getBlockPos()).is(ModBlocks.PLASMA_JETS)) {
            CondenserTowerProcess.tickSmallContainerAboveJet(level, self.getBlockPos());
        }
    }

    @Inject(method = "getParticleEndPos", at = @At("RETURN"), cancellable = true)
    private void plasticraft$restoreContainerClearance(CallbackInfoReturnable<Vec3> cir) {
        PlasmaJetsBlockEntity self = (PlasmaJetsBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null) return;
        // 大型炼药锅占用喷流上方的一格，但这格不应让喷流视觉高度少一格。
        if (CondenserTowerProcess.isPlasmaPassThrough(level.getBlockState(self.getBlockPos().above()))) {
            cir.setReturnValue(self.getBlockPos().above(2).getBottomCenter());
        }
    }
}
