package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.init.ModAttachments;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 对区块附件中的固定下落方块数据执行查询、增删和活塞迁移。 */
public final class BondedFallingBlocks {
    private BondedFallingBlocks() {
    }

    public static @Nullable BondedFallingBlockInfo get(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        LevelChunk chunk = level.getChunkAt(pos);
        BondedFallingChunkData data = chunk.getExistingDataOrNull(ModAttachments.BONDED_FALLING_BLOCKS.get());
        BondedFallingBlockInfo info = data == null ? null : data.get(pos);
        if (info != null && level instanceof ServerLevel serverLevel) migrateLegacyBond(serverLevel, pos, info);
        return info;
    }

    public static @Nullable BlockAdhesionState getAdhesion(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        BondedFallingChunkData data = level.getChunkAt(pos)
            .getExistingDataOrNull(ModAttachments.BONDED_FALLING_BLOCKS.get());
        return data == null ? null : data.getAdhesion(pos);
    }

    public static boolean isBonded(Level level, BlockPos pos) {
        BlockAdhesionState state = getAdhesion(level, pos);
        return state != null && state.hasAnyBond() || get(level, pos) != null;
    }

    public static boolean hasAnyAdhesive(Level level, BlockPos pos) {
        BlockAdhesionState state = getAdhesion(level, pos);
        return state != null && !state.isEmpty() || get(level, pos) != null;
    }

    public static boolean hasPatch(Level level, BlockPos pos, Direction face) {
        BlockAdhesionState state = getAdhesion(level, pos);
        return state != null && state.hasPatch(face);
    }

    public static boolean hasBlockBond(Level level, BlockPos pos, Direction face) {
        BlockAdhesionState state = getAdhesion(level, pos);
        return state != null && state.hasBlockBond(face);
    }

    public static boolean hasEntityBond(Level level, BlockPos pos, Direction face) {
        BlockAdhesionState state = getAdhesion(level, pos);
        return state != null && state.hasEntityBond(face);
    }

    public static List<Direction> blockBondFaces(Level level, BlockPos pos) {
        if (level instanceof ServerLevel) get(level, pos);
        BlockAdhesionState state = getAdhesion(level, pos);
        if (state == null) return List.of();
        List<Direction> result = new ArrayList<>();
        for (Direction face : Direction.values()) {
            if (state.hasBlockBond(face)) result.add(face);
        }
        return result;
    }

    public static List<BlockPos> bondedNeighbors(Level level, BlockPos pos) {
        return blockBondFaces(level, pos).stream().map(pos::relative).toList();
    }

    public static boolean putPatch(ServerLevel level, BlockPos pos, Direction face) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState blockState = level.getBlockState(pos);
        if (blockState.isAir()) return false;
        BlockAdhesionState state = currentAdhesion(level, pos, blockState);
        if (state.hasPatch(face) || state.hasBlockBond(face)) return false;
        putAdhesion(level, pos, state.withPatch(face));
        return true;
    }

    public static boolean removePatch(ServerLevel level, BlockPos pos, Direction face) {
        BlockAdhesionState state = getAdhesion(level, pos);
        if (state == null || !state.hasPatch(face)) return false;
        putAdhesion(level, pos, state.withoutPatch(face));
        return true;
    }

    public static boolean connect(ServerLevel level, BlockPos first, BlockPos second) {
        Direction direction = directionBetween(first, second);
        if (direction == null || !level.hasChunkAt(first) || !level.hasChunkAt(second)) return false;
        BlockState firstBlock = level.getBlockState(first);
        BlockState secondBlock = level.getBlockState(second);
        if (firstBlock.isAir() || secondBlock.isAir()) return false;

        Direction opposite = direction.getOpposite();
        BlockAdhesionState firstState = currentAdhesion(level, first, firstBlock);
        BlockAdhesionState secondState = currentAdhesion(level, second, secondBlock);
        if (firstState.hasEntityBond(direction) || secondState.hasEntityBond(opposite)) return false;
        putAdhesion(level, first, firstState.withBlockBond(direction));
        putAdhesion(level, second, secondState.withBlockBond(opposite));
        return true;
    }

    public static boolean disconnect(ServerLevel level, BlockPos pos, Direction face) {
        BlockAdhesionState state = getAdhesion(level, pos);
        if (state == null || !state.hasBlockBond(face)) return false;
        putAdhesion(level, pos, state.withoutBlockBond(face));
        BlockPos otherPos = pos.relative(face);
        BlockAdhesionState other = getAdhesion(level, otherPos);
        if (other != null) putAdhesion(level, otherPos, other.withoutBlockBond(face.getOpposite()));
        removeSupportReference(level, pos, otherPos);
        removeSupportReference(level, otherPos, pos);
        return true;
    }

    public static boolean setEntityBond(ServerLevel level, BlockPos pos, Direction face, boolean bonded) {
        if (!level.hasChunkAt(pos)) return false;
        BlockAdhesionState state = getAdhesion(level, pos);
        if (!bonded) {
            if (state == null || !state.hasEntityBond(face)) return false;
            putAdhesion(level, pos, state.withoutEntityBond(face));
            return true;
        }
        BlockState blockState = level.getBlockState(pos);
        if (blockState.isAir()) return false;
        if (state == null || !state.matches(blockState)) state = BlockAdhesionState.empty(blockState);
        if (state.hasBlockBond(face)) return false;
        putAdhesion(level, pos, state.withEntityBond(face));
        return true;
    }

    public static void put(ServerLevel level, BlockPos pos, BondedFallingBlockInfo info) {
        putRaw(level, pos, info);
    }

    private static void putRaw(Level level, BlockPos pos, BondedFallingBlockInfo info) {
        LevelChunk chunk = level.getChunkAt(pos);
        BondedFallingChunkData data = chunk.getExistingDataOrNull(ModAttachments.BONDED_FALLING_BLOCKS.get());
        if (data == null) data = BondedFallingChunkData.empty();
        chunk.setData(ModAttachments.BONDED_FALLING_BLOCKS, data.with(pos, info));
        chunk.setUnsaved(true);
    }

    public static @Nullable BondedFallingBlockInfo remove(ServerLevel level, BlockPos pos) {
        return removeRaw(level, pos);
    }

    public static void removeAll(ServerLevel level, BlockPos pos) {
        remove(level, pos);
        BlockAdhesionState state = removeAdhesionRaw(level, pos);
        if (state == null) return;
        for (Direction face : Direction.values()) {
            if (!state.hasBlockBond(face)) continue;
            BlockPos otherPos = pos.relative(face);
            BlockAdhesionState other = getAdhesion(level, otherPos);
            if (other != null) putAdhesion(level, otherPos, other.withoutBlockBond(face.getOpposite()));
            removeSupportReference(level, otherPos, pos);
        }
    }

    public static void validate(ServerLevel level, BlockPos pos) {
        BlockAdhesionState state = getAdhesion(level, pos);
        if (state == null) return;
        if (!state.matches(level.getBlockState(pos))) {
            removeAll(level, pos);
            return;
        }
        for (Direction face : Direction.values()) {
            if (!state.hasBlockBond(face)) continue;
            BlockPos otherPos = pos.relative(face);
            if (!level.hasChunkAt(otherPos)) continue;
            BlockState otherBlock = level.getBlockState(otherPos);
            BlockAdhesionState other = getAdhesion(level, otherPos);
            if (!otherBlock.isAir()
                && other != null
                && other.matches(otherBlock)
                && other.hasBlockBond(face.getOpposite())) {
                continue;
            }
            disconnect(level, pos, face);
            state = getAdhesion(level, pos);
            if (state == null) return;
        }
    }

    public static void moveAll(Level level, List<BlockPos> oldPositions, Direction movementDirection) {
        Map<BlockPos, BondedFallingBlockInfo> falling = new LinkedHashMap<>();
        Map<BlockPos, BlockAdhesionState> adhesions = new LinkedHashMap<>();
        for (BlockPos oldPos : oldPositions) {
            BondedFallingBlockInfo info = getRaw(level, oldPos);
            if (info != null) falling.put(oldPos.immutable(), info);
            BlockAdhesionState adhesion = getAdhesion(level, oldPos);
            if (adhesion != null) adhesions.put(oldPos.immutable(), adhesion);
        }

        Map<LevelChunk, BondedFallingChunkData> changedChunks = new LinkedHashMap<>();
        for (BlockPos oldPos : oldPositions) {
            LevelChunk chunk = level.getChunkAt(oldPos);
            BondedFallingChunkData data = movementData(chunk, changedChunks)
                .without(oldPos)
                .withoutAdhesion(oldPos);
            changedChunks.put(chunk, data);
        }
        falling.forEach((oldPos, info) -> {
            BlockPos destination = oldPos.relative(movementDirection);
            LevelChunk chunk = level.getChunkAt(destination);
            changedChunks.put(
                chunk,
                movementData(chunk, changedChunks).with(destination, info.moved(movementDirection))
            );
        });
        adhesions.forEach((oldPos, state) -> {
            BlockPos destination = oldPos.relative(movementDirection);
            LevelChunk chunk = level.getChunkAt(destination);
            changedChunks.put(
                chunk,
                movementData(chunk, changedChunks).withAdhesion(destination, state)
            );
        });
        changedChunks.forEach((chunk, data) -> store(level, chunk, data));
    }

    public static MovementSnapshot takeForMovement(ServerLevel level, List<BlockPos> positions) {
        Map<BlockPos, BondedFallingBlockInfo> falling = new LinkedHashMap<>();
        Map<BlockPos, BlockAdhesionState> adhesions = new LinkedHashMap<>();
        for (BlockPos pos : positions) {
            get(level, pos);
            BondedFallingBlockInfo info = getRaw(level, pos);
            if (info != null) falling.put(pos.immutable(), info);
            BlockAdhesionState state = getAdhesion(level, pos);
            if (state != null) adhesions.put(pos.immutable(), state);
        }
        for (BlockPos pos : positions) {
            removeRaw(level, pos);
            removeAdhesionRaw(level, pos);
        }
        return new MovementSnapshot(falling, adhesions);
    }

    public static void restoreAfterMovement(
        ServerLevel level,
        MovementSnapshot snapshot,
        BlockPos offset
    ) {
        snapshot.falling().forEach((oldPos, info) -> put(
            level,
            oldPos.offset(offset),
            info.translated(offset)
        ));
        snapshot.adhesions().forEach((oldPos, state) -> putAdhesion(
            level,
            oldPos.offset(offset),
            state
        ));
    }

    private static @Nullable BondedFallingBlockInfo getRaw(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        BondedFallingChunkData data = level.getChunkAt(pos)
            .getExistingDataOrNull(ModAttachments.BONDED_FALLING_BLOCKS.get());
        return data == null ? null : data.get(pos);
    }

    private static BondedFallingChunkData movementData(
        LevelChunk chunk,
        Map<LevelChunk, BondedFallingChunkData> changedChunks
    ) {
        BondedFallingChunkData changed = changedChunks.get(chunk);
        if (changed != null) return changed;
        BondedFallingChunkData current = chunk.getExistingDataOrNull(ModAttachments.BONDED_FALLING_BLOCKS.get());
        return current == null ? BondedFallingChunkData.empty() : current;
    }

    private static @Nullable BondedFallingBlockInfo removeRaw(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        LevelChunk chunk = level.getChunkAt(pos);
        BondedFallingChunkData data = chunk.getExistingDataOrNull(ModAttachments.BONDED_FALLING_BLOCKS.get());
        if (data == null) return null;
        BondedFallingBlockInfo removed = data.get(pos);
        if (removed != null) store(level, chunk, data.without(pos));
        return removed;
    }

    private static BlockAdhesionState currentAdhesion(ServerLevel level, BlockPos pos, BlockState blockState) {
        BlockAdhesionState state = getAdhesion(level, pos);
        return state != null && state.matches(blockState) ? state : BlockAdhesionState.empty(blockState);
    }

    private static void putAdhesion(Level level, BlockPos pos, BlockAdhesionState state) {
        if (state.isEmpty()) {
            removeAdhesionRaw(level, pos);
            return;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        BondedFallingChunkData data = chunk.getExistingDataOrNull(ModAttachments.BONDED_FALLING_BLOCKS.get());
        if (data == null) data = BondedFallingChunkData.empty();
        store(level, chunk, data.withAdhesion(pos, state));
    }

    private static @Nullable BlockAdhesionState removeAdhesionRaw(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        LevelChunk chunk = level.getChunkAt(pos);
        BondedFallingChunkData data = chunk.getExistingDataOrNull(ModAttachments.BONDED_FALLING_BLOCKS.get());
        if (data == null) return null;
        BlockAdhesionState removed = data.getAdhesion(pos);
        if (removed != null) store(level, chunk, data.withoutAdhesion(pos));
        return removed;
    }

    private static void store(Level level, LevelChunk chunk, BondedFallingChunkData data) {
        if (data.entries().isEmpty() && data.adhesions().isEmpty()) {
            chunk.removeData(ModAttachments.BONDED_FALLING_BLOCKS);
        } else {
            chunk.setData(ModAttachments.BONDED_FALLING_BLOCKS, data);
        }
        chunk.setUnsaved(true);
    }

    private static void migrateLegacyBond(ServerLevel level, BlockPos pos, BondedFallingBlockInfo info) {
        if (!info.hasSupport(level) || level.getBlockState(pos).isAir()) return;
        Direction face = directionBetween(pos, info.supportPos());
        if (face == null || hasBlockBond(level, pos, face)) return;
        connect(level, pos, info.supportPos());
    }

    private static void removeSupportReference(ServerLevel level, BlockPos pos, BlockPos removedSupportPos) {
        BondedFallingBlockInfo info = getRaw(level, pos);
        if (info == null || !info.supportPos().equals(removedSupportPos)) return;
        BlockAdhesionState state = getAdhesion(level, pos);
        if (state != null) {
            for (Direction face : Direction.values()) {
                if (!state.hasBlockBond(face)) continue;
                BlockPos replacementSupport = pos.relative(face);
                BlockState replacementState = level.getBlockState(replacementSupport);
                if (replacementState.isAir()) continue;
                put(level, pos, new BondedFallingBlockInfo(
                    info.blockState(),
                    replacementSupport,
                    BuiltInRegistries.BLOCK.getKey(replacementState.getBlock()),
                    info.pistonMovable()
                ));
                return;
            }
        }
        removeRaw(level, pos);
    }

    private static @Nullable Direction directionBetween(BlockPos first, BlockPos second) {
        return Direction.fromDelta(
            second.getX() - first.getX(),
            second.getY() - first.getY(),
            second.getZ() - first.getZ()
        );
    }

    public record MovementSnapshot(
        Map<BlockPos, BondedFallingBlockInfo> falling,
        Map<BlockPos, BlockAdhesionState> adhesions
    ) {
        public MovementSnapshot {
            falling = Map.copyOf(falling);
            adhesions = Map.copyOf(adhesions);
        }

        public static MovementSnapshot empty() {
            return new MovementSnapshot(Map.of(), Map.of());
        }

        public boolean isEmpty() {
            return this.falling.isEmpty() && this.adhesions.isEmpty();
        }
    }
}
