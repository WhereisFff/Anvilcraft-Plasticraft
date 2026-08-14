package dev.anvilcraft.plasticraft.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 安静提交:分区静默写入已交付状态,再恢复方块实体与多方块,最后只更新非红石边界形状。
 * 通用路径不发邻居更新、不掉落、不调用假玩家。
 */
public final class ConstructionCommitService {
    static final int QUIET_FLAGS =
        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    /** GameTest 可把预算打到 1,以断言分区日志只发布一次。 */
    public static int blocksPerTick = 4096;

    private ConstructionCommitService() {
    }

    public static void commitDelivered(Level level, ConstructionJobProgress progress) {
        int previous = blocksPerTick;
        blocksPerTick = Integer.MAX_VALUE;
        try {
            while (!tick(level, progress)) {
                // 取消与小结构一次写完
            }
        } finally {
            blocksPerTick = previous;
        }
    }

    /** @return 是否已经发布完成 */
    public static boolean tick(Level level, ConstructionJobProgress progress) {
        ConstructionCommitLog log = progress.commitLog();
        if (log.phase() == ConstructionCommitLog.Phase.NONE) {
            log.setPhase(ConstructionCommitLog.Phase.STATES);
        }
        ConstructionCommitLog.Phase previous;
        do {
            previous = log.phase();
            boolean done = switch (log.phase()) {
                case STATES -> writeStates(level, progress, log);
                case BLOCK_ENTITIES -> writeBlockEntities(level, progress, log);
                case MULTIBLOCK -> {
                    MultiblockBuildAdapter.restore(level, progress);
                    log.setPhase(ConstructionCommitLog.Phase.BOUNDARY);
                    yield false;
                }
                case BOUNDARY -> {
                    updateBoundaryShapes(level, progress);
                    restorePreservedStates(level, progress);
                    log.setPhase(ConstructionCommitLog.Phase.PUBLISH);
                    yield false;
                }
                case PUBLISH -> {
                    ConstructionProjectionIndex.clearJob(level, progress.jobId());
                    log.setPhase(ConstructionCommitLog.Phase.DONE);
                    yield true;
                }
                case DONE -> true;
                case NONE -> false;
            };
            if (done) {
                return true;
            }
        } while (log.phase() != previous);
        return false;
    }

    private static boolean writeStates(Level level, ConstructionJobProgress progress, ConstructionCommitLog log) {
        List<ConstructionBuildOp> ops = deliveredProjections(progress);
        int budget = Math.max(1, blocksPerTick);
        int index = log.nextIndex();
        while (index < ops.size() && budget > 0) {
            ConstructionBuildOp op = ops.get(index);
            if (!log.isWritten(op.id())) {
                quietSet(level, op.pos(), op.target());
                log.markWritten(op.id());
                budget--;
            }
            index++;
        }
        log.setNextIndex(index);
        if (index >= ops.size()) {
            log.setPhase(ConstructionCommitLog.Phase.BLOCK_ENTITIES);
        }
        return false;
    }

    private static boolean writeBlockEntities(
        Level level,
        ConstructionJobProgress progress,
        ConstructionCommitLog log
    ) {
        List<ConstructionBuildOp> ops = deliveredProjections(progress);
        HolderLookup.Provider registries = level.registryAccess();
        int budget = Math.max(1, blocksPerTick);
        int index = log.nextIndex();
        while (index < ops.size() && budget > 0) {
            ConstructionBuildOp op = ops.get(index);
            BlockEntity blockEntity = level.getBlockEntity(op.pos());
            if (blockEntity != null) {
                if (op.blockEntity() != null) {
                    blockEntity.loadWithComponents(op.blockEntity(), registries);
                }
                List<BlockEntityContentAdapter.SlotStack> contents = new ArrayList<>();
                for (ConstructionBuildOp child : progress.operations()) {
                    if (child.kind() != ConstructionBuildOp.Kind.CONTENT) continue;
                    if (child.parentId() != op.id() || child.status() != ConstructionBuildOp.Status.DELIVERED) {
                        continue;
                    }
                    contents.add(new BlockEntityContentAdapter.SlotStack(child.slot(), child.material()));
                }
                BlockEntityContentAdapter.insert(blockEntity, contents, registries);
                blockEntity.setChanged();
            }
            index++;
            budget--;
        }
        log.setNextIndex(index);
        if (index >= ops.size()) {
            log.setPhase(ConstructionCommitLog.Phase.MULTIBLOCK);
        }
        return false;
    }

    private static void updateBoundaryShapes(Level level, ConstructionJobProgress progress) {
        Set<Long> committed = new HashSet<>();
        for (ConstructionBuildOp op : deliveredProjections(progress)) {
            committed.add(op.pos().asLong());
        }
        for (ConstructionBuildOp op : deliveredProjections(progress)) {
            BlockState state = level.getBlockState(op.pos());
            if (skipsBoundaryUpdate(state)) {
                continue;
            }
            BlockState updated = state;
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = op.pos().relative(direction);
                if (committed.contains(neighbor.asLong())) {
                    continue;
                }
                updated = updated.updateShape(
                    direction,
                    level.getBlockState(neighbor),
                    level,
                    op.pos(),
                    neighbor
                );
            }
            if (updated != state) {
                quietSet(level, op.pos(), updated);
            }
        }
    }

    static void quietSet(Level level, BlockPos pos, BlockState state) {
        BlockState previous = level.getBlockState(pos);
        if (previous == state) {
            return;
        }
        if (preservesExactState(state) || preservesExactState(previous)) {
            writeWithoutCallbacks(level, pos, state);
            return;
        }
        level.getChunk(pos).setBlockState(pos, state, false);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(pos, previous, state, QUIET_FLAGS);
        }
    }

    /**
     * 红石粉 onPlace 会重算功率,活塞 onPlace 会伸缩;这些格子只写区块段,不走回调。
     */
    private static void writeWithoutCallbacks(Level level, BlockPos pos, BlockState state) {
        BlockState previous = level.getBlockState(pos);
        if (previous == state) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        if (previous.hasBlockEntity() && previous.getBlock() != state.getBlock()) {
            chunk.removeBlockEntity(pos);
        }
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
        int localX = pos.getX() & 15;
        int localY = pos.getY() & 15;
        int localZ = pos.getZ() & 15;
        section.setBlockState(localX, localY, localZ, state, false);
        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING).update(localX, pos.getY(), localZ, state);
        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES)
            .update(localX, pos.getY(), localZ, state);
        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR).update(localX, pos.getY(), localZ, state);
        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE).update(localX, pos.getY(), localZ, state);
        if (state.hasBlockEntity() && chunk.getBlockEntity(pos) == null && state.getBlock() instanceof EntityBlock entityBlock) {
            BlockEntity created = entityBlock.newBlockEntity(pos, state);
            if (created != null) {
                chunk.addAndRegisterBlockEntity(created);
            }
        } else {
            BlockEntity existing = chunk.getBlockEntity(pos);
            if (existing != null) {
                existing.setBlockState(state);
            }
        }
        chunk.setUnsaved(true);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().getLightEngine().checkBlock(pos);
            serverLevel.sendBlockUpdated(pos, previous, state, QUIET_FLAGS);
        }
    }

    private static void restorePreservedStates(Level level, ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : deliveredProjections(progress)) {
            if (!preservesExactState(op.target())) {
                continue;
            }
            writeWithoutCallbacks(level, op.pos(), op.target());
        }
    }

    private static boolean preservesExactState(BlockState state) {
        return skipsBoundaryUpdate(state);
    }

    private static boolean skipsBoundaryUpdate(BlockState state) {
        return state.getBlock() instanceof RedStoneWireBlock
            || state.getBlock() instanceof DiodeBlock
            || state.getBlock() instanceof LeverBlock
            || state.getBlock() instanceof ButtonBlock
            || state.getBlock() instanceof PistonBaseBlock
            || state.getBlock() instanceof PistonHeadBlock;
    }

    private static List<ConstructionBuildOp> deliveredProjections(ConstructionJobProgress progress) {
        List<ConstructionBuildOp> ops = new ArrayList<>();
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.status() == ConstructionBuildOp.Status.DELIVERED && op.writesProjection()) {
                ops.add(op);
            }
        }
        return ops;
    }
}
