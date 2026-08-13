package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.anvilcraft.plasticraft.block.BondedFallingBlockInfo;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.block.piston.BondedPistonReactions;
import dev.anvilcraft.plasticraft.block.piston.HighViscosityPistonBudget;
import dev.anvilcraft.plasticraft.block.piston.PlasticPistonOccupancy;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 将高粘性树脂和胶粘方块加入活塞本次真实推动结构。 */
@Mixin(value = PistonStructureResolver.class, priority = 1100)
abstract class PistonStructureResolverMixin {
    private static final int MAX_RESOLUTION_POSITION_COUNT = HighViscosityPistonBudget.VANILLA_PUSH_BUDGET * 27;

    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private List<BlockPos> toPush;

    @Shadow
    @Final
    private List<BlockPos> toDestroy;

    @Shadow
    @Final
    private Direction pushDirection;

    @Unique
    private @Nullable BlockPos plasticraft$pushReactionPos;

    @Shadow
    protected abstract boolean addBlockLine(BlockPos start, Direction direction);

    @Shadow
    protected abstract boolean addBranchingBlocks(BlockPos fromPos);

    @Redirect(
        method = {"resolve", "addBlockLine", "addBranchingBlocks"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState("
                     + "Lnet/minecraft/core/BlockPos;"
                     + ")Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState plasticraft$includePlasticEntityOccupancy(Level level, BlockPos pos) {
        this.plasticraft$pushReactionPos = pos.immutable();
        return PlasticPistonOccupancy.blockState(level, pos, level.getBlockState(pos));
    }

    @WrapOperation(
        method = {"resolve", "addBlockLine"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getPistonPushReaction()"
                     + "Lnet/minecraft/world/level/material/PushReaction;"
        )
    )
    private PushReaction plasticraft$treatBondedDestroyAsNormal(
        BlockState state,
        Operation<PushReaction> original
    ) {
        PushReaction reaction = original.call(state);
        BlockPos pos = this.plasticraft$pushReactionPos;
        return pos == null
            ? reaction
            : BondedPistonReactions.pushReaction(this.level, pos, state, reaction, this.pushDirection);
    }

    @Redirect(
        method = "addBlockLine",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/List;size()I"
        ),
        require = 3
    )
    private int plasticraft$useGroupedPhysicalLimit(List<BlockPos> blocks) {
        return blocks.size()
            - (MAX_RESOLUTION_POSITION_COUNT - HighViscosityPistonBudget.VANILLA_PUSH_BUDGET);
    }

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void plasticraft$resolveHighViscosityAdhesion(CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue()) return;
        if (!this.plasticraft$resolveAdditionalAdhesion()
            || !HighViscosityPistonBudget.withinBudget(this.level, this.toPush, this.pushDirection)) {
            callback.setReturnValue(false);
        }
    }

    private boolean plasticraft$resolveAdditionalAdhesion() {
        BondedPistonReactions.claimDestroyBlocks(this.level, this.toPush, this.toDestroy, this.pushDirection);
        int previousSize;
        do {
            previousSize = this.toPush.size();
            if (!this.plasticraft$expandPlasticEntities() || !this.plasticraft$addReverseAdhesion()) {
                return false;
            }
            BondedPistonReactions.claimDestroyBlocks(this.level, this.toPush, this.toDestroy, this.pushDirection);
            if (this.toPush.size() > MAX_RESOLUTION_POSITION_COUNT) return false;
        } while (this.toPush.size() != previousSize);
        return true;
    }

    private boolean plasticraft$expandPlasticEntities() {
        Set<UUID> expandedEntities = new HashSet<>();
        boolean expanded;
        do {
            expanded = false;
            for (BlockPos movedPos : List.copyOf(this.toPush)) {
                AbstractPlasticEntity plastic = PlasticPistonOccupancy.plasticEntityAt(this.level, movedPos);
                if (plastic == null || !expandedEntities.add(plastic.getUUID())) continue;
                expanded = true;
                for (BlockPos occupiedPos : PlasticPistonOccupancy.occupiedPositions(plastic)) {
                    if (PlasticPistonOccupancy.plasticEntityAt(this.level, occupiedPos) != plastic) continue;
                    if (this.toPush.contains(occupiedPos)) continue;
                    if (this.toPush.size() >= MAX_RESOLUTION_POSITION_COUNT
                        || !this.addBlockLine(occupiedPos, this.pushDirection)
                        || !this.toPush.contains(occupiedPos)) {
                        return false;
                    }
                }
            }
        } while (expanded);
        return true;
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
            if (this.toPush.size() > MAX_RESOLUTION_POSITION_COUNT) return false;
        } while (this.toPush.size() != previousSize);
        return true;
    }

    private boolean plasticraft$addBondedPartner(BlockPos movedPos) {
        if (this.level.getBlockEntity(movedPos) instanceof BondedEntityBlockEntity bonded) {
            if (!bonded.isPistonMovable()) return false;
            Direction supportDirection = bonded.getAttachmentFace().getOpposite();
            if (!this.plasticraft$addRequiredBlock(bonded.getSupportPos(), supportDirection)) return false;
        }

        for (Direction direction : BondedPistonReactions.blockBondFaces(this.level, movedPos, this.pushDirection)) {
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

    private boolean plasticraft$addRequiredBlock(BlockPos candidatePos, Direction branchDirection) {
        if (this.level.getBlockState(candidatePos).isAir()) return true;
        if (this.toPush.contains(candidatePos)) return true;
        if (!this.addBlockLine(candidatePos, branchDirection)) return false;
        return this.toPush.contains(candidatePos)
            || BondedPistonReactions.claimDestroyBlock(
                this.level,
                this.toPush,
                this.toDestroy,
                candidatePos,
                this.pushDirection
            );
    }
}
