package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockTags;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayCell;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponentGeometry;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponentPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.IntSupplier;

/** 将移动支架的方向性输入输出映射到元件真实表面附近的方块格。 */
public final class MoldedTrayRedstoneNetwork {
    private static final double FACE_EPSILON = 1.0E-5D;
    private static final Map<Level, Network> NETWORKS = new WeakHashMap<>();
    /** 输出投影可能落入同一宿主的输入候选格，查询期间必须排除自身以免发生回灌。 */
    private static final ThreadLocal<UUID> EXCLUDED_HOST = new ThreadLocal<>();

    private MoldedTrayRedstoneNetwork() {
    }

    public static void update(UniversalPlasticEntity host, MoldedTrayComponent component) {
        update(host, MoldedTrayCell.CENTER, component);
    }

    public static void update(
        UniversalPlasticEntity host,
        MoldedTrayCell cell,
        MoldedTrayComponent component
    ) {
        if (!(host.level() instanceof ServerLevel level)) return;
        long gameTime = level.getGameTime();
        String shapeHash = host.getMoldedData().map(MoldedPlasticData::shapeHash).orElse("");
        int occupiedCellMask = occupiedCellMask(host);
        EntryKey key = new EntryKey(host.getUUID(), cell);
        Entry previous;
        synchronized (NETWORKS) {
            Network network = NETWORKS.computeIfAbsent(level, ignored -> new Network());
            previous = network.entries.get(key);
            if (previous != null && previous.matches(host, component, shapeHash, occupiedCellMask)) {
                network.entries.put(key, previous.refreshed(gameTime));
                return;
            }
        }
        Entry replacement = Entry.create(host, cell, component, shapeHash, occupiedCellMask, gameTime);
        synchronized (NETWORKS) {
            previous = NETWORKS.computeIfAbsent(level, ignored -> new Network())
                .entries.put(key, replacement);
        }
        notifyChangedPorts(level, previous, replacement);
    }

    public static void remove(UniversalPlasticEntity host) {
        List<Entry> removed = new ArrayList<>();
        synchronized (NETWORKS) {
            Network network = NETWORKS.get(host.level());
            if (network != null) {
                network.entries.entrySet().removeIf(entry -> {
                    boolean matches = entry.getKey().hostId.equals(host.getUUID())
                        && entry.getValue().belongsTo(host);
                    if (matches) removed.add(entry.getValue());
                    return matches;
                });
            }
        }
        if (host.level() instanceof ServerLevel level) {
            for (Entry entry : removed) notifyChangedPorts(level, entry, null);
        }
    }

    public static void remove(UniversalPlasticEntity host, MoldedTrayCell cell) {
        Entry removed = null;
        EntryKey key = new EntryKey(host.getUUID(), cell);
        synchronized (NETWORKS) {
            Network network = NETWORKS.get(host.level());
            Entry current = network == null ? null : network.entries.get(key);
            if (current != null && current.belongsTo(host)) removed = network.entries.remove(key);
        }
        if (removed != null && host.level() instanceof ServerLevel level) {
            notifyChangedPorts(level, removed, null);
        }
    }

    public static int weakSignal(SignalGetter getter, BlockPos sourcePos, Direction queryDirection) {
        return signal(getter, new SignalKey(sourcePos, queryDirection), SignalKind.WEAK);
    }

    public static int directSignal(SignalGetter getter, BlockPos sourcePos, Direction queryDirection) {
        return signal(getter, new SignalKey(sourcePos, queryDirection), SignalKind.DIRECT);
    }

    public static int controlSignal(
        SignalGetter getter,
        BlockPos sourcePos,
        Direction queryDirection,
        boolean diodesOnly
    ) {
        return signal(
            getter,
            new SignalKey(sourcePos, queryDirection),
            diodesOnly ? SignalKind.DIODE_CONTROL : SignalKind.WEAK
        );
    }

    /** 返回元件面中心略向内侧所在的方块格。 */
    public static BlockPos componentCell(UniversalPlasticEntity host, Direction localFace) {
        return componentCell(host, MoldedTrayCell.CENTER, localFace);
    }

    public static BlockPos componentCell(
        UniversalPlasticEntity host,
        MoldedTrayCell cell,
        Direction localFace
    ) {
        return faceCenterCell(host, cell, localFace, -FACE_EPSILON);
    }

    /** 返回元件面中心略向外侧首先接触的方块格。 */
    public static BlockPos adjacentCell(UniversalPlasticEntity host, Direction localFace) {
        return adjacentCell(host, MoldedTrayCell.CENTER, localFace);
    }

    public static BlockPos adjacentCell(
        UniversalPlasticEntity host,
        MoldedTrayCell cell,
        Direction localFace
    ) {
        return faceCenterCell(host, cell, localFace, FACE_EPSILON);
    }

    public static BlockPos componentPosition(UniversalPlasticEntity host, MoldedTrayCell cell) {
        MoldedPlasticData data = host.getMoldedData().orElseThrow();
        Vec3 center = MoldedTrayComponentGeometry.localBounds(data, cell).getCenter();
        return BlockPos.containing(host.plasticraft$getGeometry().worldPointAt(
            host.position(),
            host.getOrientation(),
            center
        ));
    }

    /**
     * 返回输入端口由近到远的两个候选层。同层方块都与同一真实面区域相交，
     * 用于非网格对齐时覆盖一个面同时接触的多个方块格。
     */
    public static List<List<BlockPos>> inputCandidateLayers(
        UniversalPlasticEntity host,
        Direction localFace
    ) {
        return inputCandidateLayers(host, MoldedTrayCell.CENTER, localFace);
    }

    public static List<List<BlockPos>> inputCandidateLayers(
        UniversalPlasticEntity host,
        MoldedTrayCell cell,
        Direction localFace
    ) {
        Direction worldFace = host.getOrientation().worldDirection(localFace);
        AABB bounds = host.getMoldedData()
            .map(data -> MoldedTrayComponentGeometry.localBounds(data, cell))
            .map(local -> MoldedTrayPressurePlateSupport.worldBounds(host, local))
            .orElseGet(host::getBoundingBox);
        return MoldedRedstonePortGeometry.candidateLayers(bounds, worldFace);
    }

    /** 返回最近有方块层所需的虚拟源方块格；没有接收方块时默认使用近层。 */
    public static List<BlockPos> outputSourceCells(
        UniversalPlasticEntity host,
        Direction localFace
    ) {
        return outputSourceCells(host, MoldedTrayCell.CENTER, localFace);
    }

    public static List<BlockPos> outputSourceCells(
        UniversalPlasticEntity host,
        MoldedTrayCell cell,
        Direction localFace
    ) {
        return List.of(outputProjection(host, cell, localFace).sourcePos);
    }

    static boolean shouldPrioritizeDiode(UniversalPlasticEntity host, Direction localOutputFace) {
        return shouldPrioritizeDiode(host, MoldedTrayCell.CENTER, localOutputFace);
    }

    static boolean shouldPrioritizeDiode(
        UniversalPlasticEntity host,
        MoldedTrayCell cell,
        Direction localOutputFace
    ) {
        Direction worldOutputFace = host.getOrientation().worldDirection(localOutputFace);
        Optional<BlockPos> receiver = nearestOutputReceiver(host, cell, localOutputFace);
        if (receiver.isEmpty()) return false;
        BlockState state = host.level().getBlockState(receiver.get());
        return DiodeBlock.isDiode(state)
            && state.getValue(BlockStateProperties.HORIZONTAL_FACING) != worldOutputFace;
    }

    public static AABB outputRange(UniversalPlasticEntity host, Direction localFace) {
        return outputRange(host, MoldedTrayCell.CENTER, localFace);
    }

    public static AABB outputRange(
        UniversalPlasticEntity host,
        MoldedTrayCell cell,
        Direction localFace
    ) {
        MoldedPlasticData data = host.getMoldedData().orElseThrow();
        AABB localRange = MoldedTrayComponentGeometry.localOutputRange(data, cell, localFace);
        return MoldedTrayPressurePlateSupport.worldBounds(host, localRange);
    }

    public static AABB forwardRange(UniversalPlasticEntity host, Direction localFace, int distance) {
        return forwardRange(host, MoldedTrayCell.CENTER, localFace, distance);
    }

    public static AABB forwardRange(
        UniversalPlasticEntity host,
        MoldedTrayCell cell,
        Direction localFace,
        int distance
    ) {
        if (distance < 1) throw new IllegalArgumentException("Tray component range must be positive");
        BlockPos first = adjacentCell(host, cell, localFace);
        BlockPos last = first.relative(
            host.getOrientation().worldDirection(localFace),
            distance - 1
        );
        return AABB.encapsulatingFullBlocks(first, last);
    }

    static int excludingHost(UniversalPlasticEntity host, IntSupplier query) {
        UUID previous = EXCLUDED_HOST.get();
        EXCLUDED_HOST.set(host.getUUID());
        try {
            return query.getAsInt();
        } finally {
            if (previous == null) EXCLUDED_HOST.remove();
            else EXCLUDED_HOST.set(previous);
        }
    }

    private static BlockPos faceCenterCell(
        UniversalPlasticEntity host,
        MoldedTrayCell cell,
        Direction localFace,
        double offset
    ) {
        PlasticEntityOrientation orientation = host.getOrientation();
        Direction worldFace = orientation.worldDirection(localFace);
        Vec3 localSurface = host.getMoldedData()
            .map(data -> MoldedTrayComponentGeometry.localFaceCenter(data, cell, localFace))
            .orElseGet(() -> host.plasticraft$getGeometry().surfaceCenter(localFace));
        Vec3 surface = host.plasticraft$getGeometry().worldPointAt(
            host.position(),
            orientation,
            localSurface
        );
        return BlockPos.containing(surface.add(
            worldFace.getStepX() * offset,
            worldFace.getStepY() * offset,
            worldFace.getStepZ() * offset
        ));
    }

    private static int containingMinimum(double coordinate) {
        return (int) Math.floor(coordinate + FACE_EPSILON);
    }

    private static int containingMaximum(double coordinate) {
        return (int) Math.floor(coordinate - FACE_EPSILON);
    }

    private static boolean isOutputReceiver(UniversalPlasticEntity host, BlockPos position) {
        BlockState state = host.level().getBlockState(position);
        return !state.isAir()
            && !isFluidBlock(state)
            && !(position.equals(host.plasticraft$getAnchorBlockPos())
                && state.is(PlasticraftBlockTags.PLASTIC_PRODUCTS));
    }

    private static Optional<BlockPos> nearestOutputReceiver(
        UniversalPlasticEntity host,
        MoldedTrayCell cell,
        Direction localFace
    ) {
        return nearestOutputCell(host, cell, localFace, true, null);
    }

    private static OutputProjection outputProjection(
        UniversalPlasticEntity host,
        MoldedTrayCell cell,
        Direction localFace
    ) {
        return outputProjection(host, cell, localFace, null);
    }

    private static OutputProjection outputProjection(
        UniversalPlasticEntity host,
        MoldedTrayCell cell,
        Direction localFace,
        Map<BlockPos, Boolean> receiverStates
    ) {
        Direction worldFace = host.getOrientation().worldDirection(localFace);
        Optional<BlockPos> receiver = nearestOutputCell(host, cell, localFace, true, receiverStates);
        BlockPos receiverCell = receiver.orElseGet(() -> nearestOutputCell(
            host,
            cell,
            localFace,
            false,
            receiverStates
        ).orElseThrow());
        return new OutputProjection(
            receiverCell.relative(worldFace.getOpposite()).immutable(),
            receiver.isPresent()
        );
    }

    private static BlockPos nearestOutputCell(
        UniversalPlasticEntity host,
        MoldedTrayCell cell,
        Direction localFace
    ) {
        return nearestOutputCell(host, cell, localFace, false, null).orElseThrow();
    }

    private static Optional<BlockPos> nearestOutputCell(
        UniversalPlasticEntity host,
        MoldedTrayCell cell,
        Direction localFace,
        boolean receiversOnly,
        Map<BlockPos, Boolean> receiverStates
    ) {
        AABB range = outputRange(host, cell, localFace);
        Direction worldFace = host.getOrientation().worldDirection(localFace);
        Vec3 outputCenter = range.getCenter().subtract(
            worldFace.getStepX() * 0.5D,
            worldFace.getStepY() * 0.5D,
            worldFace.getStepZ() * 0.5D
        );
        BlockPos nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (BlockPos candidate : BlockPos.betweenClosed(
            containingMinimum(range.minX),
            containingMinimum(range.minY),
            containingMinimum(range.minZ),
            containingMaximum(range.maxX),
            containingMaximum(range.maxY),
            containingMaximum(range.maxZ)
        )) {
            BlockPos position = candidate.immutable();
            if (receiversOnly || receiverStates != null) {
                boolean receiver = isOutputReceiver(host, position);
                if (receiverStates != null) receiverStates.put(position, receiver);
                if (receiversOnly && !receiver) continue;
            }
            double distance = Vec3.atCenterOf(position).distanceToSqr(outputCenter);
            if (nearest == null
                || distance < nearestDistance - FACE_EPSILON
                || Math.abs(distance - nearestDistance) <= FACE_EPSILON
                    && comparePositions(position, nearest) < 0) {
                nearest = position;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static int comparePositions(BlockPos first, BlockPos second) {
        int x = Integer.compare(first.getX(), second.getX());
        if (x != 0) return x;
        int y = Integer.compare(first.getY(), second.getY());
        return y != 0 ? y : Integer.compare(first.getZ(), second.getZ());
    }

    private static boolean isFluidBlock(BlockState state) {
        return !state.getFluidState().isEmpty()
            && state.getFluidState().createLegacyBlock().is(state.getBlock());
    }

    private static int occupiedCellMask(UniversalPlasticEntity host) {
        return host.getMoldedData()
            .map(MoldedPlasticData::contents)
            .map(contents -> contents.trayComponents().stream()
                .map(MoldedTrayComponentPlacement::cell)
                .mapToInt(MoldedTrayCell::bit)
                .reduce(0, (left, right) -> left | right))
            .orElse(0);
    }

    private static int signal(SignalGetter getter, SignalKey key, SignalKind kind) {
        if (!(getter instanceof Level level)) return 0;
        long gameTime = level.getGameTime();
        UUID excludedHost = EXCLUDED_HOST.get();
        int result = 0;
        synchronized (NETWORKS) {
            Network network = NETWORKS.get(level);
            if (network == null) return 0;
            for (Map.Entry<EntryKey, Entry> networkEntry : network.entries.entrySet()) {
                if (networkEntry.getKey().hostId.equals(excludedHost)) continue;
                Entry entry = networkEntry.getValue();
                if (gameTime - entry.lastSeenGameTime > 1L) continue;
                PortSignal port = entry.ports.get(key);
                if (port == null || kind == SignalKind.DIODE_CONTROL && !entry.diode) continue;
                result = Math.max(result, kind == SignalKind.DIRECT ? port.direct : port.weak);
                if (result >= 15) return 15;
            }
        }
        return result;
    }

    private static void notifyChangedPorts(
        ServerLevel level,
        Entry previous,
        Entry replacement
    ) {
        Map<SignalKey, PortSignal> before = previous == null ? Map.of() : previous.ports;
        Map<SignalKey, PortSignal> after = replacement == null ? Map.of() : replacement.ports;
        Set<SignalKey> connectedBefore = previous == null ? Set.of() : previous.connectedPorts;
        Set<SignalKey> connectedAfter = replacement == null ? Set.of() : replacement.connectedPorts;
        if (before.equals(after) && connectedBefore.equals(connectedAfter)) return;
        Set<SignalKey> changedPorts = new HashSet<>();
        for (Map.Entry<SignalKey, PortSignal> entry : before.entrySet()) {
            if (!entry.getValue().equals(after.get(entry.getKey()))) {
                changedPorts.add(entry.getKey());
            }
        }
        for (Map.Entry<SignalKey, PortSignal> entry : after.entrySet()) {
            if (!entry.getValue().equals(before.get(entry.getKey()))) {
                changedPorts.add(entry.getKey());
            }
        }
        for (SignalKey key : connectedBefore) {
            if (!connectedAfter.contains(key)) changedPorts.add(key);
        }
        for (SignalKey key : connectedAfter) {
            if (!connectedBefore.contains(key)) changedPorts.add(key);
        }
        Block notifyingBlock = replacement != null
            ? replacement.componentBlock
            : previous.componentBlock;
        for (SignalKey key : changedPorts) {
            level.updateNeighborsAt(key.sourcePos, notifyingBlock);
            level.updateNeighborsAtExceptFromFacing(
                key.receiverPos(),
                notifyingBlock,
                key.queryDirection
            );
        }
    }

    private enum SignalKind {
        WEAK,
        DIRECT,
        DIODE_CONTROL
    }

    private static final class Network {
        private final Map<EntryKey, Entry> entries = new HashMap<>();
    }

    private record Entry(
        WeakReference<UniversalPlasticEntity> owner,
        MoldedTrayCell cell,
        Vec3 hostPosition,
        PlasticEntityOrientation orientation,
        String shapeHash,
        int occupiedCellMask,
        MoldedTrayComponent sourceComponent,
        Map<BlockPos, Boolean> receiverStates,
        Block componentBlock,
        boolean diode,
        long lastSeenGameTime,
        Map<SignalKey, PortSignal> ports,
        Set<SignalKey> connectedPorts
    ) {
        private static Entry create(
            UniversalPlasticEntity host,
            MoldedTrayCell cell,
            MoldedTrayComponent component,
            String shapeHash,
            int occupiedCellMask,
            long gameTime
        ) {
            Map<SignalKey, PortSignal> ports = new HashMap<>();
            Set<SignalKey> connectedPorts = new HashSet<>();
            Map<BlockPos, Boolean> receiverStates = new HashMap<>();
            PlasticEntityOrientation orientation = host.getOrientation();
            MoldedTrayRedstoneBehavior behavior = MoldedTrayRedstoneBehaviors.find(component.state());
            for (Direction worldQueryDirection : Direction.values()) {
                Direction localQueryDirection = orientation.localDirection(worldQueryDirection);
                Direction outputFace = localQueryDirection.getOpposite();
                int weak = behavior.weakSignal(component, localQueryDirection);
                int direct = behavior.directSignal(component, localQueryDirection, weak);
                if (weak <= 0 && direct <= 0) continue;
                if (cell.relative(outputFace)
                    .filter(neighbor -> (occupiedCellMask & neighbor.bit()) != 0)
                    .isPresent()) {
                    continue;
                }
                PortSignal signal = new PortSignal(weak, direct);
                OutputProjection projection = outputProjection(host, cell, outputFace, receiverStates);
                SignalKey key = new SignalKey(projection.sourcePos, worldQueryDirection);
                ports.merge(key, signal, PortSignal::maximum);
                if (projection.connected) connectedPorts.add(key);
            }
            return new Entry(
                new WeakReference<>(host),
                cell,
                host.position(),
                orientation,
                shapeHash,
                occupiedCellMask,
                component,
                Map.copyOf(receiverStates),
                component.state().getBlock(),
                behavior.isDiode(),
                gameTime,
                Map.copyOf(ports),
                Set.copyOf(connectedPorts)
            );
        }

        private boolean matches(
            UniversalPlasticEntity host,
            MoldedTrayComponent component,
            String currentShapeHash,
            int currentOccupiedCellMask
        ) {
            if (!this.belongsTo(host)
                || !this.hostPosition.equals(host.position())
                || !this.orientation.equals(host.getOrientation())
                || !this.shapeHash.equals(currentShapeHash)
                || this.occupiedCellMask != currentOccupiedCellMask
                || !this.sourceComponent.hasSameSignalState(component)) {
                return false;
            }
            for (Map.Entry<BlockPos, Boolean> entry : this.receiverStates.entrySet()) {
                if (isOutputReceiver(host, entry.getKey()) != entry.getValue()) return false;
            }
            return true;
        }

        private boolean belongsTo(UniversalPlasticEntity host) {
            return this.owner.get() == host;
        }

        private Entry refreshed(long gameTime) {
            return new Entry(
                this.owner,
                this.cell,
                this.hostPosition,
                this.orientation,
                this.shapeHash,
                this.occupiedCellMask,
                this.sourceComponent,
                this.receiverStates,
                this.componentBlock,
                this.diode,
                gameTime,
                this.ports,
                this.connectedPorts
            );
        }
    }

    private record EntryKey(UUID hostId, MoldedTrayCell cell) {
    }

    private record OutputProjection(BlockPos sourcePos, boolean connected) {
    }

    private record SignalKey(BlockPos sourcePos, Direction queryDirection) {
        private SignalKey {
            sourcePos = sourcePos.immutable();
        }

        private BlockPos receiverPos() {
            return sourcePos.relative(queryDirection.getOpposite());
        }
    }

    private record PortSignal(int weak, int direct) {
        private PortSignal {
            weak = Math.clamp(weak, 0, 15);
            direct = Math.clamp(direct, 0, 15);
        }

        private PortSignal maximum(PortSignal other) {
            return new PortSignal(Math.max(this.weak, other.weak), Math.max(this.direct, other.direct));
        }
    }
}
