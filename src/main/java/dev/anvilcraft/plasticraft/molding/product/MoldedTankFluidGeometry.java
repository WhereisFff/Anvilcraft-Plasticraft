package dev.anvilcraft.plasticraft.molding.product;

import dev.anvilcraft.plasticraft.molding.bake.MoldingBarrierFace;
import dev.anvilcraft.plasticraft.molding.bake.MoldingFaceDirection;
import dev.anvilcraft.plasticraft.molding.bake.MoldingVolumeMask;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 储罐内腔的等体积半空间裁切器，服务端和客户端使用同一套几何约定。 */
public final class MoldedTankFluidGeometry {
    private static final double EPSILON = 1.0E-8D;
    private static final double FACE_INSET = 0.02D;
    private static final int SOLVER_STEPS = 42;
    private static final int TOPOLOGY_CACHE_LIMIT = 32;
    private static final Vec3 NEGATIVE_X_NORMAL = new Vec3(-1.0D, 0.0D, 0.0D);
    private static final Vec3 POSITIVE_X_NORMAL = new Vec3(1.0D, 0.0D, 0.0D);
    private static final Vec3 NEGATIVE_Y_NORMAL = new Vec3(0.0D, -1.0D, 0.0D);
    private static final Vec3 POSITIVE_Y_NORMAL = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Vec3 NEGATIVE_Z_NORMAL = new Vec3(0.0D, 0.0D, -1.0D);
    private static final Vec3 POSITIVE_Z_NORMAL = new Vec3(0.0D, 0.0D, 1.0D);
    private static final MoldingFaceDirection[] FACE_DIRECTIONS = MoldingFaceDirection.values();
    private static final int[][] CUBE_EDGES = {
        {0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4},
        {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private static final Map<TopologyKey, Topology> TOPOLOGY_CACHE = new LinkedHashMap<>(
        TOPOLOGY_CACHE_LIMIT,
        0.75F,
        true
    ) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TopologyKey, Topology> eldest) {
            return this.size() > TOPOLOGY_CACHE_LIMIT;
        }
    };

    private MoldedTankFluidGeometry() {
    }

    public static List<Layer> solve(MoldedPlasticData data, Vec3 upDirection) {
        if (data.limitOverride()) return List.of();
        List<FluidStack> fluids = data.contents().fluids();
        if (data.capacity() <= 0 || fluids.isEmpty()) return List.of();
        Vec3 up = upDirection.lengthSqr() <= EPSILON
            ? new Vec3(0.0D, 1.0D, 0.0D)
            : upDirection.normalize();
        Topology topology = topology(data);
        if (topology.cells.length == 0) return List.of();
        Projection projection = Projection.create(topology, up);

        long capacity = (long) data.capacity() * 1000L;
        long remainingCapacity = capacity;
        long consumed = 0L;
        double lower = projection.minimum;
        List<Layer> layers = new ArrayList<>();
        for (int index = 0; index < fluids.size() && remainingCapacity > 0L; index++) {
            FluidStack fluid = fluids.get(index);
            int amount = (int) Math.min(fluid.getAmount(), remainingCapacity);
            if (amount <= 0) continue;
            consumed += amount;
            remainingCapacity -= amount;
            double target = topology.cells.length * (consumed / (double) capacity);
            double upper = solveThreshold(projection, target);
            List<Polygon> polygons = createLayer(topology, projection, up, lower, upper);
            if (!polygons.isEmpty()) layers.add(new Layer(fluid.copyWithAmount(amount), polygons));
            lower = upper;
        }
        return List.copyOf(layers);
    }

    private static Topology topology(MoldedPlasticData data) {
        TopologyKey key = new TopologyKey(data.modelHash(), data.cavityMask(), data.barrierFaces());
        synchronized (TOPOLOGY_CACHE) {
            Topology cached = TOPOLOGY_CACHE.get(key);
            if (cached != null) return cached;
            Topology created = Topology.create(key.cavity, key.barriers);
            TOPOLOGY_CACHE.put(key, created);
            return created;
        }
    }

    private static double solveThreshold(Projection projection, double targetVolume) {
        if (targetVolume <= EPSILON) return projection.minimum;
        if (targetVolume >= projection.minimums.length - EPSILON) return projection.maximum;
        double low = projection.minimum;
        double high = projection.maximum;
        for (int step = 0; step < SOLVER_STEPS; step++) {
            double middle = (low + high) * 0.5D;
            if (volumeBelow(projection, middle) < targetVolume) low = middle;
            else high = middle;
        }
        return (low + high) * 0.5D;
    }

    private static double volumeBelow(Projection projection, double threshold) {
        double volume = 0.0D;
        for (double minimum : projection.minimums) {
            if (threshold <= minimum) continue;
            if (threshold >= minimum + projection.span) {
                volume += 1.0D;
                continue;
            }
            volume += projection.volumeFunction.volumeBelow(threshold - minimum);
        }
        return volume;
    }

    private static List<Polygon> createLayer(
        Topology topology,
        Projection projection,
        Vec3 up,
        double lower,
        double upper
    ) {
        if (upper - lower <= EPSILON) return List.of();
        List<Polygon> polygons = new ArrayList<>();
        for (int index = 0; index < topology.cells.length; index++) {
            int cell = topology.cells[index];
            int x = topology.xOf(cell);
            int y = topology.yOf(cell);
            int z = topology.zOf(cell);
            double minimum = projection.minimums[index];
            double maximum = minimum + projection.span;
            if (upper <= minimum + EPSILON || lower >= maximum - EPSILON) continue;
            byte boundaryMask = topology.boundaryMasks[index];
            for (MoldingFaceDirection direction : FACE_DIRECTIONS) {
                if ((boundaryMask & 1 << direction.ordinal()) == 0) continue;
                Face face = face(x, y, z, direction);
                List<Vec3> clipped = clip(clip(face.vertices, up, upper, false), up, lower, true);
                if (clipped.size() >= 3) {
                    polygons.add(new Polygon(inset(clipped, face.normal), face.normal));
                }
            }
            if (upper < projection.maximum - EPSILON) {
                List<Vec3> cap = planeIntersection(x, y, z, up, upper);
                cap = clip(cap, up, lower, true);
                if (cap.size() >= 3) {
                    polygons.add(new Polygon(cap, up));
                }
            }
        }
        return List.copyOf(polygons);
    }

    private static double cellMinimum(Vec3 normal, int x, int y, int z) {
        return normal.x * (normal.x >= 0.0D ? x : x + 1)
            + normal.y * (normal.y >= 0.0D ? y : y + 1)
            + normal.z * (normal.z >= 0.0D ? z : z + 1);
    }

    private static List<Vec3> inset(List<Vec3> vertices, Vec3 normal) {
        Vec3 offset = normal.scale(-FACE_INSET / 16.0D);
        return vertices.stream().map(vertex -> vertex.add(offset)).toList();
    }

    private static List<Vec3> clip(List<Vec3> polygon, Vec3 normal, double limit, boolean keepGreater) {
        if (polygon.isEmpty()) return polygon;
        int insideCount = 0;
        for (Vec3 vertex : polygon) {
            double distance = normal.dot(vertex) - limit;
            if (keepGreater ? distance >= -EPSILON : distance <= EPSILON) insideCount++;
        }
        if (insideCount == 0) return List.of();
        if (insideCount == polygon.size()) return polygon;
        List<Vec3> result = new ArrayList<>(polygon.size() + 1);
        Vec3 previous = polygon.getLast();
        double previousDistance = normal.dot(previous) - limit;
        boolean previousInside = keepGreater ? previousDistance >= -EPSILON : previousDistance <= EPSILON;
        for (Vec3 current : polygon) {
            double currentDistance = normal.dot(current) - limit;
            boolean currentInside = keepGreater ? currentDistance >= -EPSILON : currentDistance <= EPSILON;
            if (previousInside != currentInside) {
                double denominator = currentDistance - previousDistance;
                double progress = Math.clamp(-previousDistance / denominator, 0.0D, 1.0D);
                addDistinct(result, previous.lerp(current, progress));
            }
            if (currentInside) addDistinct(result, current);
            previous = current;
            previousDistance = currentDistance;
            previousInside = currentInside;
        }
        if (result.size() > 1 && result.getFirst().distanceToSqr(result.getLast()) <= EPSILON * EPSILON) {
            result.removeLast();
        }
        return result;
    }

    private static List<Vec3> planeIntersection(int x, int y, int z, Vec3 normal, double plane) {
        Vec3[] corners = {
            new Vec3(x, y, z), new Vec3(x + 1, y, z), new Vec3(x + 1, y + 1, z), new Vec3(x, y + 1, z),
            new Vec3(x, y, z + 1), new Vec3(x + 1, y, z + 1), new Vec3(x + 1, y + 1, z + 1),
            new Vec3(x, y + 1, z + 1)
        };
        List<Vec3> points = new ArrayList<>(6);
        for (int[] edge : CUBE_EDGES) {
            Vec3 first = corners[edge[0]];
            Vec3 second = corners[edge[1]];
            double firstDistance = normal.dot(first) - plane;
            double secondDistance = normal.dot(second) - plane;
            if (Math.abs(firstDistance) <= EPSILON) addDistinct(points, first);
            if (firstDistance * secondDistance < -EPSILON * EPSILON) {
                double progress = firstDistance / (firstDistance - secondDistance);
                addDistinct(points, first.lerp(second, progress));
            }
        }
        if (points.size() < 3) return List.of();
        Vec3 center = points.stream().reduce(Vec3.ZERO, Vec3::add).scale(1.0D / points.size());
        Vec3 basis = Math.abs(normal.x) < 0.9D
            ? normal.cross(new Vec3(1.0D, 0.0D, 0.0D)).normalize()
            : normal.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        Vec3 other = normal.cross(basis).normalize();
        points.sort(Comparator.comparingDouble(point -> Math.atan2(
            point.subtract(center).dot(other),
            point.subtract(center).dot(basis)
        )));
        Vec3 polygonNormal = points.get(1).subtract(points.getFirst())
            .cross(points.get(2).subtract(points.getFirst()));
        if (polygonNormal.dot(normal) < 0.0D) points = points.reversed();
        return points;
    }

    private static void addDistinct(List<Vec3> points, Vec3 point) {
        for (Vec3 existing : points) {
            if (existing.distanceToSqr(point) <= EPSILON * EPSILON) return;
        }
        points.add(point);
    }

    private static Face face(int x, int y, int z, MoldingFaceDirection direction) {
        return switch (direction) {
            case NEGATIVE_X -> face(List.of(
                new Vec3(x, y, z),
                new Vec3(x, y, z + 1),
                new Vec3(x, y + 1, z + 1),
                new Vec3(x, y + 1, z)
            ), NEGATIVE_X_NORMAL);
            case POSITIVE_X -> face(List.of(
                new Vec3(x + 1, y, z),
                new Vec3(x + 1, y + 1, z),
                new Vec3(x + 1, y + 1, z + 1),
                new Vec3(x + 1, y, z + 1)
            ), POSITIVE_X_NORMAL);
            case NEGATIVE_Y -> face(List.of(
                new Vec3(x, y, z),
                new Vec3(x + 1, y, z),
                new Vec3(x + 1, y, z + 1),
                new Vec3(x, y, z + 1)
            ), NEGATIVE_Y_NORMAL);
            case POSITIVE_Y -> face(List.of(
                new Vec3(x, y + 1, z),
                new Vec3(x, y + 1, z + 1),
                new Vec3(x + 1, y + 1, z + 1),
                new Vec3(x + 1, y + 1, z)
            ), POSITIVE_Y_NORMAL);
            case NEGATIVE_Z -> face(List.of(
                new Vec3(x, y, z),
                new Vec3(x, y + 1, z),
                new Vec3(x + 1, y + 1, z),
                new Vec3(x + 1, y, z)
            ), NEGATIVE_Z_NORMAL);
            case POSITIVE_Z -> face(List.of(
                new Vec3(x, y, z + 1),
                new Vec3(x + 1, y, z + 1),
                new Vec3(x + 1, y + 1, z + 1),
                new Vec3(x, y + 1, z + 1)
            ), POSITIVE_Z_NORMAL);
        };
    }

    private static Face face(List<Vec3> vertices, Vec3 normal) {
        return new Face(vertices, normal);
    }

    public record Layer(FluidStack fluid, List<Polygon> polygons) {
        public Layer {
            fluid = fluid.copy();
            polygons = List.copyOf(polygons);
        }

        @Override
        public FluidStack fluid() {
            return this.fluid.copy();
        }
    }

    public record Polygon(List<Vec3> vertices, Vec3 normal) {
        public Polygon {
            vertices = List.copyOf(vertices);
            if (vertices.size() < 3) throw new IllegalArgumentException("Fluid polygon needs three vertices");
        }
    }

    private record Face(
        List<Vec3> vertices,
        Vec3 normal
    ) {
    }

    private record TopologyKey(
        String modelHash,
        MoldingVolumeMask cavity,
        Set<MoldingBarrierFace> barriers
    ) {
        private TopologyKey {
            barriers = Set.copyOf(barriers);
        }
    }

    /** 每个体素只长期缓存六位边界掩码，避免复杂内腔持有逐面顶点。 */
    private record Topology(int[] cells, byte[] boundaryMasks, int sizeX, int sizeY, int sizeZ) {
        private static Topology create(MoldingVolumeMask mask, Set<MoldingBarrierFace> barriers) {
            BitSet bits = mask.copyBits();
            int[] cells = bits.stream().toArray();
            Set<MoldingBarrierFace> blockedFaces = Set.copyOf(barriers);
            byte[] boundaryMasks = new byte[cells.length];
            for (int index = 0; index < cells.length; index++) {
                int cell = cells[index];
                int x = mask.xOf(cell);
                int y = mask.yOf(cell);
                int z = mask.zOf(cell);
                int boundaryMask = 0;
                for (MoldingFaceDirection direction : FACE_DIRECTIONS) {
                    int neighborX = x + direction.stepX();
                    int neighborY = y + direction.stepY();
                    int neighborZ = z + direction.stepZ();
                    boolean connected = mask.containsCoordinate(neighborX, neighborY, neighborZ)
                        && bits.get(mask.indexOf(neighborX, neighborY, neighborZ))
                        && !blockedFaces.contains(MoldingBarrierFace.between(x, y, z, direction));
                    if (!connected) boundaryMask |= 1 << direction.ordinal();
                }
                boundaryMasks[index] = (byte) boundaryMask;
            }
            return new Topology(cells, boundaryMasks, mask.sizeX(), mask.sizeY(), mask.sizeZ());
        }

        private int xOf(int index) {
            return index / this.sizeZ % this.sizeX;
        }

        private int yOf(int index) {
            return index / (this.sizeX * this.sizeZ);
        }

        private int zOf(int index) {
            return index % this.sizeZ;
        }
    }

    private record Projection(
        double[] minimums,
        double minimum,
        double maximum,
        double span,
        VolumeFunction volumeFunction
    ) {
        private static Projection create(Topology topology, Vec3 up) {
            double[] minimums = new double[topology.cells.length];
            double minimum = Double.POSITIVE_INFINITY;
            double maximumMinimum = Double.NEGATIVE_INFINITY;
            for (int index = 0; index < topology.cells.length; index++) {
                int cell = topology.cells[index];
                double cellMinimum = cellMinimum(
                    up,
                    topology.xOf(cell),
                    topology.yOf(cell),
                    topology.zOf(cell)
                );
                minimums[index] = cellMinimum;
                minimum = Math.min(minimum, cellMinimum);
                maximumMinimum = Math.max(maximumMinimum, cellMinimum);
            }
            double span = Math.abs(up.x) + Math.abs(up.y) + Math.abs(up.z);
            return new Projection(
                minimums,
                minimum,
                maximumMinimum + span,
                span,
                VolumeFunction.create(up)
            );
        }
    }

    private record VolumeFunction(
        int dimensions,
        double first,
        double second,
        double third,
        double denominator
    ) {
        private static VolumeFunction create(Vec3 normal) {
            double first = 0.0D;
            double second = 0.0D;
            double third = 0.0D;
            int dimensions = 0;
            double x = Math.abs(normal.x);
            double y = Math.abs(normal.y);
            double z = Math.abs(normal.z);
            if (x > EPSILON) {
                first = x;
                dimensions++;
            }
            if (y > EPSILON) {
                if (dimensions == 0) first = y;
                else second = y;
                dimensions++;
            }
            if (z > EPSILON) {
                if (dimensions == 0) first = z;
                else if (dimensions == 1) second = z;
                else third = z;
                dimensions++;
            }
            double denominator = switch (dimensions) {
                case 1 -> first;
                case 2 -> 2.0D * first * second;
                case 3 -> 6.0D * first * second * third;
                default -> 1.0D;
            };
            return new VolumeFunction(dimensions, first, second, third, denominator);
        }

        private double volumeBelow(double value) {
            double sum = switch (this.dimensions) {
                case 1 -> positive(value) - positive(value - this.first);
                case 2 -> positiveSquare(value)
                    - positiveSquare(value - this.first)
                    - positiveSquare(value - this.second)
                    + positiveSquare(value - this.first - this.second);
                case 3 -> positiveCube(value)
                    - positiveCube(value - this.first)
                    - positiveCube(value - this.second)
                    - positiveCube(value - this.third)
                    + positiveCube(value - this.first - this.second)
                    + positiveCube(value - this.first - this.third)
                    + positiveCube(value - this.second - this.third)
                    - positiveCube(value - this.first - this.second - this.third);
                default -> value >= 0.0D ? 1.0D : 0.0D;
            };
            return Math.clamp(sum / this.denominator, 0.0D, 1.0D);
        }

        private static double positive(double value) {
            return Math.max(value, 0.0D);
        }

        private static double positiveSquare(double value) {
            return value > 0.0D ? value * value : 0.0D;
        }

        private static double positiveCube(double value) {
            return value > 0.0D ? value * value * value : 0.0D;
        }
    }
}
