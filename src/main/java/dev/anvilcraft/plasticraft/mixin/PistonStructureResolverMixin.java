package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.BlockAdhesionState;
import dev.anvilcraft.plasticraft.block.BondedFallingBlockInfo;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.block.piston.HighViscosityPistonBudget;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 将高粘性树脂和胶粘方块加入活塞本次真实推动结构。 */
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
    @Final
    private Direction pushDirection;

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
        Set<BlockPos> branchedStickyBlocks = new HashSet<>();
        int previousSize;
        do {
            previousSize = this.toPush.size();
            List<BlockPos> snapshot = List.copyOf(this.toPush);
            for (BlockPos movedPos : snapshot) {
                if (!this.plasticraft$addBondedPartner(movedPos)) return false;
                BlockState movedState = this.level.getBlockState(movedPos);
                for (Direction direction : Direction.values()) {
                    BlockPos resinPos = movedPos.relative(direction);
                    BlockState resinState = this.level.getBlockState(resinPos);
                    if (!resinState.is(PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.get())) continue;
                    if (!HighViscosityPistonBudget.canStickTogether(
                        movedPos,
                        movedState,
                        resinPos,
                        resinState
                    )) {
                        continue;
                    }
                    if (!this.plasticraft$addRequiredBlock(resinPos, direction)) return false;
                }
            }

            for (BlockPos pos : List.copyOf(this.toPush)) {
                if (!this.level.getBlockState(pos).isStickyBlock()) continue;
                if (!branchedStickyBlocks.add(pos.immutable())) continue;
                if (!this.addBranchingBlocks(pos)) return false;
            }
            if (this.toPush.size() > MAX_PHYSICAL_PUSH_COUNT) return false;
        } while (this.toPush.size() != previousSize);
        return true;
    }

    private boolean plasticraft$addBondedPartner(BlockPos movedPos) {
        if (this.level.getBlockEntity(movedPos) instanceof BondedEntityBlockEntity bonded) {
            if (!bonded.isPistonMovable()) return false;
            Direction supportDirection = bonded.getAttachmentFace().getOpposite();
            if (!this.plasticraft$addRequiredBlock(bonded.getSupportPos(), supportDirection)) return false;
        }

        for (Direction direction : this.plasticraft$blockBondFaces(movedPos)) {
            BlockPos candidatePos = movedPos.relative(direction);
            if (this.level.getBlockEntity(candidatePos) instanceof BondedEntityBlockEntity candidate
                && !candidate.isPistonMovable()) {
                return false;
            }
            BondedFallingBlockInfo candidate = BondedFallingBlocks.get(this.level, candidatePos);
            if (candidate != null && !candidate.pistonMovable()) return false;
            if (!this.plasticraft$addRequiredBlock(candidatePos, direction)) return false;
        }
        return true;
    }

    private List<Direction> plasticraft$blockBondFaces(BlockPos movedPos) {
        if (this.level.isClientSide) {
            // 区块附件包先于原版活塞事件到达客户端，此时胶粘数据已经位于目标坐标。
            BlockState movedState = this.level.getBlockState(movedPos);
            BlockAdhesionState movedAdhesion = BondedFallingBlocks.getAdhesion(
                this.level,
                movedPos.relative(this.pushDirection)
            );
            if (movedAdhesion != null && movedAdhesion.matches(movedState)) {
                return plasticraft$blockBondFaces(movedAdhesion);
            }
        }
        return BondedFallingBlocks.blockBondFaces(this.level, movedPos);
    }

    private static List<Direction> plasticraft$blockBondFaces(BlockAdhesionState adhesion) {
        ArrayList<Direction> result = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            if (adhesion.hasBlockBond(direction)) result.add(direction);
        }
        return result;
    }

    private boolean plasticraft$addRequiredBlock(BlockPos candidatePos, Direction branchDirection) {
        if (this.level.getBlockState(candidatePos).isAir()) return true;
        if (this.toPush.contains(candidatePos)) return true;
        if (!this.addBlockLine(candidatePos, branchDirection)) return false;
        return this.toPush.contains(candidatePos);
    }
}
