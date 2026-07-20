package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.piston.HighViscosityPistonBudget;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 为高粘性树脂补充反向粘连发现，并按粘连组结算活塞预算。 */
@Mixin(value = PistonStructureResolver.class, priority = 1100)
abstract class PistonStructureResolverMixin {
    private static final int MAX_PHYSICAL_PUSH_COUNT = HighViscosityPistonBudget.VANILLA_PUSH_BUDGET * 7;

    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private List<BlockPos> toPush;

    @Shadow
    protected abstract boolean addBlockLine(BlockPos start, Direction direction);

    @Shadow
    protected abstract boolean addBranchingBlocks(BlockPos fromPos);

    @Redirect(
        method = "addBlockLine",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/List;size()I"
        ),
        require = 3
    )
    private int plasticraft$useGroupedPhysicalLimit(List<BlockPos> blocks) {
        return blocks.size() - (MAX_PHYSICAL_PUSH_COUNT - HighViscosityPistonBudget.VANILLA_PUSH_BUDGET);
    }

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void plasticraft$resolveHighViscosityAdhesion(CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue()) return;
        if (!this.plasticraft$addReverseAdhesion() || !HighViscosityPistonBudget.withinBudget(this.level, this.toPush)) {
            callback.setReturnValue(false);
        }
    }

    private boolean plasticraft$addReverseAdhesion() {
        Set<BlockPos> branchedResin = new HashSet<>();
        int previousSize;
        do {
            previousSize = this.toPush.size();
            List<BlockPos> snapshot = List.copyOf(this.toPush);
            for (BlockPos movedPos : snapshot) {
                BlockState movedState = this.level.getBlockState(movedPos);
                for (Direction direction : Direction.values()) {
                    BlockPos resinPos = movedPos.relative(direction);
                    BlockState resinState = this.level.getBlockState(resinPos);
                    if (!resinState.is(ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.get())) continue;
                    if (!HighViscosityPistonBudget.canStickTogether(
                        movedPos,
                        movedState,
                        resinPos,
                        resinState
                    )) {
                        continue;
                    }
                    if (!this.addBlockLine(resinPos, direction)) return false;
                }
            }

            for (BlockPos pos : List.copyOf(this.toPush)) {
                if (!this.level.getBlockState(pos).is(ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.get())) continue;
                if (!branchedResin.add(pos.immutable())) continue;
                if (!this.addBranchingBlocks(pos)) return false;
            }
            if (this.toPush.size() > MAX_PHYSICAL_PUSH_COUNT) return false;
        } while (this.toPush.size() != previousSize);
        return true;
    }
}
