package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.dubhe.anvilcraft.api.fluid.IFluidHandlerHolder;
import dev.dubhe.anvilcraft.block.RedstoneWireBlock;
import dev.dubhe.anvilcraft.block.RedstoneWireNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 安静提交:分区静默写入已交付状态,再恢复方块实体与多方块,最后只更新非红石边界形状。
 * 本体红石导线另把蓝图四向写入端口覆盖表,之后网络更新也遵守这些端口。
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
                case MULTIBLOCK -> restoreMultiblocks(level, progress, log);
                case BOUNDARY -> updateBoundaryShapes(level, progress, log);
                case PASTE -> pasteDeliveredRegion(
                    level,
                    progress,
                    log,
                    ConstructionCommitLog.Phase.WIRE_TOPOLOGY
                );
                case WIRE_TOPOLOGY -> updateWireTopology(
                    level,
                    progress,
                    log,
                    ConstructionCommitLog.Phase.WIRE_PORTS
                );
                case WIRE_PORTS -> reconcileWirePorts(
                    level,
                    progress,
                    log,
                    ConstructionCommitLog.Phase.ENTITIES
                );
                case ENTITIES -> writeEntities(level, progress, log);
                case FINAL_PASTE -> pasteDeliveredRegion(
                    level,
                    progress,
                    log,
                    ConstructionCommitLog.Phase.FINAL_WIRE_TOPOLOGY
                );
                case FINAL_WIRE_TOPOLOGY -> updateWireTopology(
                    level,
                    progress,
                    log,
                    ConstructionCommitLog.Phase.FINAL_WIRE_PORTS
                );
                case FINAL_WIRE_PORTS -> reconcileWirePorts(
                    level,
                    progress,
                    log,
                    ConstructionCommitLog.Phase.PUBLISH
                );
                case PUBLISH -> {
                    ConstructionProjectionIndex.clearJob(level, progress.jobId());
                    ConstructionEntityProjectionIndex.clearJob(level, progress.jobId());
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
        Set<Long> delivered = progress.deliveredProjectionPositions();
        int budget = Math.max(1, blocksPerTick);
        int index = log.nextIndex();
        while (index < ops.size() && budget > 0) {
            ConstructionBuildOp op = ops.get(index);
            if (!log.isWritten(op.id())) {
                try {
                    if (level.isInWorldBounds(op.pos())) {
                        quietSet(level, op.pos(), committedState(op, delivered));
                    } else {
                        progress.setIncomplete(true);
                    }
                } catch (RuntimeException exception) {
                    progress.setIncomplete(true);
                    AnvilcraftPlasticraft.LOGGER.error(
                        "Failed to write construction state for operation {}",
                        op.id(),
                        exception
                    );
                }
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
            try {
                List<BlockEntityContentAdapter.SlotStack> contents = new ArrayList<>();
                List<FluidBuildAdapter.TankFluid> fluids = new ArrayList<>();
                for (ConstructionBuildOp child : progress.childrenOf(op)) {
                    if (child.status() != ConstructionBuildOp.Status.DELIVERED) continue;
                    if (child.kind() == ConstructionBuildOp.Kind.CONTENT) {
                        contents.add(new BlockEntityContentAdapter.SlotStack(child.slot(), child.material()));
                    } else if (child.kind() == ConstructionBuildOp.Kind.FLUID) {
                        fluids.add(new FluidBuildAdapter.TankFluid(child.slot(), child.fluid()));
                    }
                }
                BlockEntity blockEntity = level.isInWorldBounds(op.pos()) ? level.getBlockEntity(op.pos()) : null;
                boolean expected = op.target().hasBlockEntity()
                    || op.blockEntity() != null
                    || !contents.isEmpty()
                    || !fluids.isEmpty();
                if (blockEntity == null) {
                    if (expected) progress.setIncomplete(true);
                } else {
                    if (op.blockEntity() != null) {
                        blockEntity.loadWithComponents(op.blockEntity(), registries);
                    }
                    BlockEntityContentAdapter.insert(blockEntity, contents, registries);
                    FluidBuildAdapter.insert(blockEntity, fluids);
                    blockEntity.setChanged();
                }
            } catch (RuntimeException exception) {
                progress.setIncomplete(true);
                AnvilcraftPlasticraft.LOGGER.error(
                    "Failed to restore construction block entity for operation {}",
                    op.id(),
                    exception
                );
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

    private static boolean writeEntities(
        Level level,
        ConstructionJobProgress progress,
        ConstructionCommitLog log
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            log.setPhase(ConstructionCommitLog.Phase.FINAL_PASTE);
            return false;
        }
        List<ConstructionBuildOp> ops = progress.deliveredEntityOperations();
        int budget = Math.max(1, blocksPerTick);
        int index = log.nextIndex();
        while (index < ops.size() && budget > 0) {
            ConstructionBuildOp op = ops.get(index);
            if (!log.isWritten(op.id())) {
                try {
                    Entity entity = EntityBuildAdapters.spawn(serverLevel, op);
                    if (entity == null) {
                        progress.setIncomplete(true);
                    } else {
                        List<EntityBuildAdapter.SlotStack> contents = new ArrayList<>();
                        for (ConstructionBuildOp child : progress.childrenOf(op)) {
                            if (child.status() != ConstructionBuildOp.Status.DELIVERED) {
                                continue;
                            }
                            if (child.kind() == ConstructionBuildOp.Kind.CONTENT) {
                                contents.add(new EntityBuildAdapter.SlotStack(child.slot(), child.material()));
                            }
                        }
                        EntityBuildAdapters.insertContents(entity, contents, serverLevel.registryAccess());
                        IFluidHandler fluidsHandler = fluidHandlerOf(entity);
                        if (fluidsHandler != null) {
                            for (ConstructionBuildOp child : progress.childrenOf(op)) {
                                if (child.kind() != ConstructionBuildOp.Kind.FLUID) continue;
                                if (child.status() != ConstructionBuildOp.Status.DELIVERED) {
                                    continue;
                                }
                                int filled = fluidsHandler.fill(child.fluid(), IFluidHandler.FluidAction.EXECUTE);
                                if (filled < child.fluid().getAmount()) {
                                    progress.setIncomplete(true);
                                }
                            }
                        }
                    }
                } catch (RuntimeException exception) {
                    progress.setIncomplete(true);
                    AnvilcraftPlasticraft.LOGGER.error(
                        "Failed to restore construction entity for operation {}",
                        op.id(),
                        exception
                    );
                }
                log.markWritten(op.id());
                budget--;
            }
            index++;
        }
        log.setNextIndex(index);
        if (index >= ops.size()) {
            log.setPhase(ConstructionCommitLog.Phase.FINAL_PASTE);
        }
        return false;
    }

    private static boolean restoreMultiblocks(
        Level level,
        ConstructionJobProgress progress,
        ConstructionCommitLog log
    ) {
        List<ConstructionBuildOp> ops = deliveredProjections(progress);
        int budget = Math.max(1, blocksPerTick);
        int index = log.nextIndex();
        while (index < ops.size() && budget > 0) {
            ConstructionBuildOp op = ops.get(index);
            try {
                if (!level.isInWorldBounds(op.pos())) {
                    progress.setIncomplete(true);
                } else if (MultiblockBuildAdapter.isMultiPart(op.target())
                    && MultiblockBuildAdapter.isCore(op.pos(), op.target())
                    && !level.getBlockState(op.pos()).is(op.target().getBlock())) {
                    quietSet(level, op.pos(), op.target());
                }
            } catch (RuntimeException exception) {
                progress.setIncomplete(true);
                AnvilcraftPlasticraft.LOGGER.error(
                    "Failed to restore construction multiblock for operation {}",
                    op.id(),
                    exception
                );
            }
            index++;
            budget--;
        }
        log.setNextIndex(index);
        if (index >= ops.size()) {
            log.setPhase(ConstructionCommitLog.Phase.BOUNDARY);
        }
        return false;
    }

    private static boolean updateBoundaryShapes(
        Level level,
        ConstructionJobProgress progress,
        ConstructionCommitLog log
    ) {
        List<ConstructionBuildOp> ops = deliveredProjections(progress);
        Set<Long> committed = progress.deliveredProjectionPositions();
        int budget = Math.max(1, blocksPerTick);
        int index = log.nextIndex();
        while (index < ops.size() && budget > 0) {
            ConstructionBuildOp op = ops.get(index);
            try {
                if (!level.isInWorldBounds(op.pos())) {
                    progress.setIncomplete(true);
                } else {
                    BlockState state = level.getBlockState(op.pos());
                    if (!skipsBoundaryUpdate(state)) {
                        BlockState updated = state;
                        for (Direction direction : Direction.values()) {
                            BlockPos neighbor = op.pos().relative(direction);
                            if (committed.contains(neighbor.asLong())) continue;
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
            } catch (RuntimeException exception) {
                progress.setIncomplete(true);
                AnvilcraftPlasticraft.LOGGER.error(
                    "Failed to update construction boundary for operation {}",
                    op.id(),
                    exception
                );
            }
            index++;
            budget--;
        }
        log.setNextIndex(index);
        if (index >= ops.size()) {
            log.setPhase(ConstructionCommitLog.Phase.PASTE);
        }
        return false;
    }

    static void quietSet(Level level, BlockPos pos, BlockState state) {
        writeWithoutCallbacks(level, pos, state);
    }

    /**
     * 红石粉 onPlace 会重算功率,活塞 onPlace 会伸缩;这些格子只写区块段,不走回调。
     */
    private static void writeWithoutCallbacks(Level level, BlockPos pos, BlockState state) {
        if (!level.isInWorldBounds(pos)) return;
        BlockState previous = level.getBlockState(pos);
        if (previous == state) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        if (previous.getBlock() instanceof RedstoneWireBlock && !(state.getBlock() instanceof RedstoneWireBlock)) {
            RedstoneWireNetworkManager.wireRemoved(level, pos);
        }
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

    /** 按投影模组粘贴:整区再写一遍蓝图状态,只通知客户端,不跑邻居重算。 */
    private static boolean pasteDeliveredRegion(
        Level level,
        ConstructionJobProgress progress,
        ConstructionCommitLog log,
        ConstructionCommitLog.Phase nextPhase
    ) {
        List<ConstructionBuildOp> ops = deliveredProjections(progress);
        Set<Long> delivered = progress.deliveredProjectionPositions();
        int budget = Math.max(1, blocksPerTick);
        int index = log.nextIndex();
        while (index < ops.size() && budget > 0) {
            ConstructionBuildOp op = ops.get(index);
            try {
                if (level.isInWorldBounds(op.pos())) {
                    writeWithoutCallbacks(level, op.pos(), committedState(op, delivered));
                } else {
                    progress.setIncomplete(true);
                }
            } catch (RuntimeException exception) {
                progress.setIncomplete(true);
                AnvilcraftPlasticraft.LOGGER.error(
                    "Failed to paste construction state for operation {}",
                    op.id(),
                    exception
                );
            }
            index++;
            budget--;
        }
        log.setNextIndex(index);
        if (index >= ops.size()) {
            log.setPhase(nextPhase);
        }
        return false;
    }

    private static boolean updateWireTopology(
        Level level,
        ConstructionJobProgress progress,
        ConstructionCommitLog log,
        ConstructionCommitLog.Phase nextPhase
    ) {
        List<ConstructionBuildOp> ops = deliveredProjections(progress);
        int budget = Math.max(1, blocksPerTick);
        int index = log.nextIndex();
        while (index < ops.size() && budget > 0) {
            ConstructionBuildOp op = ops.get(index);
            try {
                if (level instanceof ServerLevel serverLevel && isCommittedWire(serverLevel, op)) {
                    RedstoneWireNetworkManager.topologyChanged(serverLevel, op.pos());
                }
            } catch (RuntimeException exception) {
                progress.setIncomplete(true);
                AnvilcraftPlasticraft.LOGGER.error(
                    "Failed to update construction wire topology for operation {}",
                    op.id(),
                    exception
                );
            }
            index++;
            budget--;
        }
        log.setNextIndex(index);
        if (index >= ops.size()) {
            log.setPhase(nextPhase);
        }
        return false;
    }

    private static boolean reconcileWirePorts(
        Level level,
        ConstructionJobProgress progress,
        ConstructionCommitLog log,
        ConstructionCommitLog.Phase nextPhase
    ) {
        List<ConstructionBuildOp> ops = deliveredProjections(progress);
        int budget = Math.max(1, blocksPerTick);
        int index = log.nextIndex();
        while (index < ops.size() && budget > 0) {
            ConstructionBuildOp op = ops.get(index);
            try {
                if (level instanceof ServerLevel serverLevel && isCommittedWire(serverLevel, op)) {
                    AnvilCraftRedstoneWirePorts.reconcile(serverLevel, op.pos(), op.target());
                }
            } catch (RuntimeException exception) {
                progress.setIncomplete(true);
                AnvilcraftPlasticraft.LOGGER.error(
                    "Failed to restore construction wire ports for operation {}",
                    op.id(),
                    exception
                );
            }
            index++;
            budget--;
        }
        log.setNextIndex(index);
        if (index >= ops.size()) {
            log.setPhase(nextPhase);
        }
        return false;
    }

    private static boolean isCommittedWire(ServerLevel level, ConstructionBuildOp op) {
        return level.isInWorldBounds(op.pos())
            && AnvilCraftRedstoneWirePorts.isWire(op.target())
            && AnvilCraftRedstoneWirePorts.isWire(level.getBlockState(op.pos()));
    }

    private static BlockState committedState(ConstructionBuildOp op, Set<Long> delivered) {
        return OrdinaryBlockAdapter.commitState(op.target(), op.pos(), delivered);
    }

    /** 拆除临时封堵壳后再校正一次导线端口，避免壳的邻居更新覆盖蓝图方向。 */
    public static void restoreWirePorts(Level level, ConstructionJobProgress progress) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        List<ConstructionBuildOp> wires = new ArrayList<>();
        for (ConstructionBuildOp op : deliveredProjections(progress)) {
            if (isCommittedWire(serverLevel, op)) wires.add(op);
        }
        for (ConstructionBuildOp op : wires) {
            try {
                RedstoneWireNetworkManager.topologyChanged(serverLevel, op.pos());
            } catch (RuntimeException exception) {
                progress.setIncomplete(true);
                AnvilcraftPlasticraft.LOGGER.error(
                    "Failed to update final construction wire topology for operation {}",
                    op.id(),
                    exception
                );
            }
        }
        for (ConstructionBuildOp op : wires) {
            try {
                AnvilCraftRedstoneWirePorts.reconcile(serverLevel, op.pos(), op.target());
            } catch (RuntimeException exception) {
                progress.setIncomplete(true);
                AnvilcraftPlasticraft.LOGGER.error(
                    "Failed to restore final construction wire ports for operation {}",
                    op.id(),
                    exception
                );
            }
        }
    }

    static boolean preservesExactState(BlockState state) {
        return skipsBoundaryUpdate(state);
    }

    private static boolean skipsBoundaryUpdate(BlockState state) {
        return state.getBlock() instanceof RedStoneWireBlock
            || state.getBlock() instanceof RedstoneWireBlock
            || state.getBlock() instanceof DiodeBlock
            || state.getBlock() instanceof LeverBlock
            || state.getBlock() instanceof ButtonBlock
            || state.getBlock() instanceof PistonBaseBlock
            || state.getBlock() instanceof PistonHeadBlock;
    }

    @Nullable
    private static IFluidHandler fluidHandlerOf(Entity entity) {
        if (entity instanceof IFluidHandlerHolder holder) {
            return holder.getFluidHandler();
        }
        return entity.getCapability(Capabilities.FluidHandler.ENTITY, null);
    }

    private static List<ConstructionBuildOp> deliveredProjections(ConstructionJobProgress progress) {
        return progress.deliveredProjectionOperations();
    }
}
