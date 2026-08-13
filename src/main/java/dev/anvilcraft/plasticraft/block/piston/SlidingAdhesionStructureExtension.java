package dev.anvilcraft.plasticraft.block.piston;

import dev.dubhe.anvilcraft.api.sliding.SlidingBlockStructureResolver;
import dev.dubhe.anvilcraft.api.sliding.SlidingStructureExtension;
import dev.dubhe.anvilcraft.block.sliding.ISlidingRail;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

import java.util.List;

/** 滑轨结构解析时把胶粘 DESTROY 方块收成可推动组。 */
public final class SlidingAdhesionStructureExtension implements SlidingStructureExtension {
    public static final SlidingAdhesionStructureExtension INSTANCE = new SlidingAdhesionStructureExtension();

    private SlidingAdhesionStructureExtension() {
    }

    @Override
    public PushReaction modifyPushReaction(
        Level level,
        BlockPos pos,
        BlockState state,
        PushReaction original,
        Direction pushDirection
    ) {
        return BondedPistonReactions.pushReaction(level, pos, state, original, pushDirection);
    }

    @Override
    public boolean expand(SlidingBlockStructureResolver resolver) {
        Level level = resolver.getLevel();
        Direction pushDirection = resolver.getPushDirection();
        List<BlockPos> toPush = resolver.getToPush();
        List<BlockPos> toDestroy = resolver.getToDestroy();
        BondedPistonReactions.claimDestroyBlocks(level, toPush, toDestroy, pushDirection);
        int previousSize;
        do {
            previousSize = toPush.size();
            for (BlockPos movedPos : List.copyOf(toPush)) {
                for (Direction face : BondedPistonReactions.blockBondFaces(level, movedPos, pushDirection)) {
                    BlockPos partner = movedPos.relative(face);
                    if (toPush.contains(partner)) continue;
                    if (level.getBlockState(partner).getBlock() instanceof ISlidingRail) {
                        return false;
                    }
                    if (!resolver.addBlockLine(partner, face)
                        || !level.getBlockState(partner).isAir()
                            && !toPush.contains(partner)
                            && !BondedPistonReactions.claimDestroyBlock(
                                level,
                                toPush,
                                toDestroy,
                                partner,
                                pushDirection
                            )) {
                        return false;
                    }
                }
                BlockState state = level.getBlockState(movedPos);
                if (state.isStickyBlock() && !resolver.addBranchingBlocks(movedPos)) {
                    return false;
                }
            }
            BondedPistonReactions.claimDestroyBlocks(level, toPush, toDestroy, pushDirection);
        } while (toPush.size() != previousSize);
        return true;
    }
}
