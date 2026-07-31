package dev.anvilcraft.plasticraft.event;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FallingBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.List;

/** 在方块被破坏、放置或炸毁后维护胶粘关系。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID)
public final class BondedFallingBlockEvents {
    private BondedFallingBlockEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void blockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.isCanceled()) return;
        List<BlockPos> neighbors = BondedFallingBlocks.bondedNeighbors(level, event.getPos());
        BondedFallingBlocks.removeAll(level, event.getPos());
        neighbors.forEach(pos -> scheduleFallingCheck(level, pos));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void blockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.isCanceled()) return;
        BlockPos placedPos = event.getPos();
        BondedFallingBlocks.validate(level, placedPos);
        for (Direction direction : Direction.values()) {
            BlockPos supportPos = placedPos.relative(direction);
            Direction supportFace = direction.getOpposite();
            if (!BondedFallingBlocks.hasPatch(level, supportPos, supportFace)) continue;
            BondedFallingBlocks.connect(level, supportPos, placedPos);
        }
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        for (BlockPos affected : event.getAffectedBlocks()) {
            List<BlockPos> neighbors = BondedFallingBlocks.bondedNeighbors(level, affected);
            BondedFallingBlocks.removeAll(level, affected);
            neighbors.forEach(pos -> scheduleFallingCheck(level, pos));
        }
    }

    public static void scheduleFallingCheck(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof FallingBlock) {
            level.scheduleTick(pos, state.getBlock(), 1);
            return;
        }
        if (state.getBlock() instanceof GiantAnvilBlock giantAnvil) {
            BlockPos middleCenter = giantAnvil.getMainPartPos(pos, state);
            level.scheduleTick(middleCenter.below(), giantAnvil, 1);
        }
    }
}
