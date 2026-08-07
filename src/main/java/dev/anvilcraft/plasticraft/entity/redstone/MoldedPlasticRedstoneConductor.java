package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.IntSupplier;

/** 让外形恰好为一格的成型塑料按真实六面接收并传导红石信号。 */
public final class MoldedPlasticRedstoneConductor {
    private static final double SIZE_EPSILON = 1.0E-7D;
    private static final double FACE_EPSILON = 1.0E-5D;
    private static final Map<Level, Network> NETWORKS = new WeakHashMap<>();
    /** 输入查询屏蔽同类导体输出，避免相邻导体用上一刻信号形成无源锁存。 */
    private static final ThreadLocal<Boolean> IGNORE_CONDUCTOR_OUTPUTS = new ThreadLocal<>();

    private MoldedPlasticRedstoneConductor() {
    }

    public static boolean isFullBlockSized(AABB bounds) {
        return Math.abs(bounds.getXsize() - 1.0D) <= SIZE_EPSILON
            && Math.abs(bounds.getYsize() - 1.0D) <= SIZE_EPSILON
            && Math.abs(bounds.getZsize() - 1.0D) <= SIZE_EPSILON;
    }

    public static void update(UniversalPlasticEntity host) {
        if (!(host.level() instanceof ServerLevel level)) return;
        AABB localBounds = host.getMoldedData()
            .map(MoldedPlasticData::surfaceBounds)
            .orElseGet(() -> host.plasticraft$getGeometry().localBounds());
        if (host.isRemoved() || !isFullBlockSized(localBounds)) {
            remove(host);
            return;
        }

        Entry replacement = Entry.create(host, level.getGameTime());
        Entry previous;
        synchronized (NETWORKS) {
            previous = NETWORKS.computeIfAbsent(level, ignored -> new Network())
                .entries.put(host.getUUID(), replacement);
        }
        notifyChangedPorts(level, previous, replacement);
    }

    public static void remove(UniversalPlasticEntity host) {
        if (!(host.level() instanceof ServerLevel level)) return;
        Entry removed;
        synchronized (NETWORKS) {
            Network network = NETWORKS.get(level);
            removed = network == null ? null : network.entries.remove(host.getUUID());
        }
        if (removed != null) notifyChangedPorts(level, removed, null);
    }

    public static int weakSignal(SignalGetter getter, BlockPos sourcePos, Direction queryDirection) {
        if (Boolean.TRUE.equals(IGNORE_CONDUCTOR_OUTPUTS.get())) return 0;
        if (!(getter instanceof Level level)) return 0;
        SignalKey key = new SignalKey(sourcePos, queryDirection);
        long gameTime = level.getGameTime();
        int result = 0;
        synchronized (NETWORKS) {
            Network network = NETWORKS.get(level);
            if (network == null) return 0;
            for (Entry entry : network.entries.values()) {
                if (gameTime - entry.lastSeenGameTime > 1L) continue;
                result = Math.max(result, entry.ports.getOrDefault(key, 0));
                if (result >= 15) return 15;
            }
        }
        return result;
    }

    private static int receivedSignal(UniversalPlasticEntity host, AABB bounds) {
        SignalGetter getter = host.level();
        int result = 0;
        for (Direction face : Direction.values()) {
            for (List<BlockPos> layer : candidateLayers(bounds, face)) {
                int layerSignal = 0;
                for (BlockPos source : layer) {
                    int signal = withoutConductorFeedback(host, () -> getter.getSignal(source, face));
                    BlockState state = host.level().getBlockState(source);
                    if (state.is(Blocks.REDSTONE_WIRE)) {
                        signal = Math.max(signal, state.getValue(RedStoneWireBlock.POWER));
                    }
                    layerSignal = Math.max(layerSignal, Math.clamp(signal, 0, 15));
                }
                if (layerSignal <= 0) continue;
                result = Math.max(result, layerSignal);
                break;
            }
            if (result >= 15) return 15;
        }
        return result;
    }

    private static int withoutConductorFeedback(UniversalPlasticEntity host, IntSupplier query) {
        Boolean previous = IGNORE_CONDUCTOR_OUTPUTS.get();
        IGNORE_CONDUCTOR_OUTPUTS.set(true);
        try {
            return MoldedTrayRedstoneNetwork.excludingHost(host, query);
        } finally {
            if (previous == null) IGNORE_CONDUCTOR_OUTPUTS.remove();
            else IGNORE_CONDUCTOR_OUTPUTS.set(previous);
        }
    }

    private static OutputProjection outputProjection(Level level, AABB bounds, Direction face) {
        Vec3 surfaceCenter = surfaceCenter(bounds, face);
        List<List<BlockPos>> layers = candidateLayers(bounds, face);
        for (List<BlockPos> layer : layers) {
            BlockPos receiver = closestPosition(
                layer.stream().filter(position -> isOutputReceiver(level, position)).toList(),
                surfaceCenter
            );
            if (receiver != null) {
                return new OutputProjection(receiver.relative(face.getOpposite()), true);
            }
        }
        BlockPos fallback = closestPosition(layers.getFirst(), surfaceCenter);
        return new OutputProjection(fallback.relative(face.getOpposite()), false);
    }

    private static List<List<BlockPos>> candidateLayers(AABB bounds, Direction face) {
        List<BlockPos> nearest = faceContactCells(bounds, face);
        List<BlockPos> farther = nearest.stream().map(position -> position.relative(face)).toList();
        return List.of(nearest, farther);
    }

    private static List<BlockPos> faceContactCells(AABB bounds, Direction face) {
        int minimumX = containingMinimum(bounds.minX);
        int minimumY = containingMinimum(bounds.minY);
        int minimumZ = containingMinimum(bounds.minZ);
        int maximumX = containingMaximum(bounds.maxX);
        int maximumY = containingMaximum(bounds.maxY);
        int maximumZ = containingMaximum(bounds.maxZ);
        int faceCell = containingOutsideFace(bounds, face);
        switch (face.getAxis()) {
            case X -> minimumX = maximumX = faceCell;
            case Y -> minimumY = maximumY = faceCell;
            case Z -> minimumZ = maximumZ = faceCell;
        }
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos position : BlockPos.betweenClosed(
            minimumX,
            minimumY,
            minimumZ,
            maximumX,
            maximumY,
            maximumZ
        )) {
            result.add(position.immutable());
        }
        return List.copyOf(result);
    }

    private static int containingMinimum(double coordinate) {
        return (int) Math.floor(coordinate + FACE_EPSILON);
    }

    private static int containingMaximum(double coordinate) {
        return (int) Math.floor(coordinate - FACE_EPSILON);
    }

    private static int containingOutsideFace(AABB bounds, Direction face) {
        double coordinate = switch (face.getAxis()) {
            case X -> face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? bounds.maxX : bounds.minX;
            case Y -> face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? bounds.maxY : bounds.minY;
            case Z -> face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? bounds.maxZ : bounds.minZ;
        };
        return (int) Math.floor(coordinate + face.getAxisDirection().getStep() * FACE_EPSILON);
    }

    private static Vec3 surfaceCenter(AABB bounds, Direction face) {
        Vec3 center = bounds.getCenter();
        return switch (face.getAxis()) {
            case X -> new Vec3(
                face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? bounds.maxX : bounds.minX,
                center.y,
                center.z
            );
            case Y -> new Vec3(
                center.x,
                face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? bounds.maxY : bounds.minY,
                center.z
            );
            case Z -> new Vec3(
                center.x,
                center.y,
                face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? bounds.maxZ : bounds.minZ
            );
        };
    }

    private static BlockPos closestPosition(List<BlockPos> positions, Vec3 target) {
        BlockPos closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (BlockPos position : positions) {
            double distance = Vec3.atCenterOf(position).distanceToSqr(target);
            if (closest == null
                || distance < closestDistance - FACE_EPSILON
                || Math.abs(distance - closestDistance) <= FACE_EPSILON
                    && comparePositions(position, closest) < 0) {
                closest = position;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private static int comparePositions(BlockPos first, BlockPos second) {
        int x = Integer.compare(first.getX(), second.getX());
        if (x != 0) return x;
        int y = Integer.compare(first.getY(), second.getY());
        return y != 0 ? y : Integer.compare(first.getZ(), second.getZ());
    }

    private static boolean isOutputReceiver(Level level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        return !state.isAir()
            && (state.getFluidState().isEmpty()
                || !state.getFluidState().createLegacyBlock().is(state.getBlock()));
    }

    private static void notifyChangedPorts(ServerLevel level, Entry previous, Entry replacement) {
        Map<SignalKey, Integer> before = previous == null ? Map.of() : previous.ports;
        Map<SignalKey, Integer> after = replacement == null ? Map.of() : replacement.ports;
        Set<SignalKey> connectedBefore = previous == null ? Set.of() : previous.connectedPorts;
        Set<SignalKey> connectedAfter = replacement == null ? Set.of() : replacement.connectedPorts;
        if (before.equals(after) && connectedBefore.equals(connectedAfter)) return;

        Set<SignalKey> changed = new HashSet<>();
        before.forEach((key, signal) -> {
            if (!signal.equals(after.get(key))) changed.add(key);
        });
        after.forEach((key, signal) -> {
            if (!signal.equals(before.get(key))) changed.add(key);
        });
        for (SignalKey key : connectedBefore) {
            if (!connectedAfter.contains(key)) changed.add(key);
        }
        for (SignalKey key : connectedAfter) {
            if (!connectedBefore.contains(key)) changed.add(key);
        }

        Block notifyingBlock = PlasticraftBlocks.UNIVERSAL_PLASTIC.get();
        for (SignalKey key : changed) {
            level.updateNeighborsAt(key.sourcePos, notifyingBlock);
            level.updateNeighborsAtExceptFromFacing(
                key.receiverPos(),
                notifyingBlock,
                key.queryDirection
            );
        }
    }

    private static final class Network {
        private final Map<UUID, Entry> entries = new HashMap<>();
    }

    private record Entry(
        long lastSeenGameTime,
        Map<SignalKey, Integer> ports,
        Set<SignalKey> connectedPorts
    ) {
        private static Entry create(UniversalPlasticEntity host, long gameTime) {
            AABB bounds = host.getBoundingBox();
            int signal = receivedSignal(host, bounds);
            Map<SignalKey, Integer> ports = new HashMap<>();
            Set<SignalKey> connected = new HashSet<>();
            for (Direction outputFace : Direction.values()) {
                OutputProjection projection = outputProjection(host.level(), bounds, outputFace);
                SignalKey key = new SignalKey(projection.sourcePos, outputFace.getOpposite());
                ports.merge(key, signal, Math::max);
                if (projection.connected) connected.add(key);
            }
            return new Entry(gameTime, Map.copyOf(ports), Set.copyOf(connected));
        }
    }

    private record OutputProjection(BlockPos sourcePos, boolean connected) {
        private OutputProjection {
            sourcePos = sourcePos.immutable();
        }
    }

    private record SignalKey(BlockPos sourcePos, Direction queryDirection) {
        private SignalKey {
            sourcePos = sourcePos.immutable();
        }

        private BlockPos receiverPos() {
            return this.sourcePos.relative(this.queryDirection.getOpposite());
        }
    }
}
