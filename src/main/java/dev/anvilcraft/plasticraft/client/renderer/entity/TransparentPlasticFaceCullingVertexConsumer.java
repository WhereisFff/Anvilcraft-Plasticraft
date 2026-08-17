package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.client.renderer.ClearPlasticRenderTypes;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.anvilcraft.plasticraft.molding.bake.MoldingQuad;
import dev.anvilcraft.plasticraft.molding.bake.MoldingConvexHull;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** 将相同颜色透明塑料的真实接触区域从表面网格中裁掉，避免保留内部重叠面。 */
public final class TransparentPlasticFaceCullingVertexConsumer implements VertexConsumer {
    private static final double CONTACT_EPSILON = 1.0E-3D;
    private static final double PIXELS_PER_BLOCK = 16.0D;
    private final UniversalPlasticEntity entity;
    private final VertexConsumer delegate;
    private final List<Face> occluders;
    private final List<VolumeOccluder> volumes;
    private final boolean shadeBlockFaces;
    private final List<Vertex> quad = new ArrayList<>(4);
    private Vertex current;
    private boolean currentDoubleSided;

    private TransparentPlasticFaceCullingVertexConsumer(
        UniversalPlasticEntity entity,
        VertexConsumer delegate,
        List<Face> occluders,
        List<VolumeOccluder> volumes,
        boolean shadeBlockFaces
    ) {
        this.entity = entity;
        this.delegate = delegate;
        this.occluders = occluders;
        this.volumes = volumes;
        this.shadeBlockFaces = shadeBlockFaces;
    }

    public static VertexConsumer wrap(UniversalPlasticEntity entity, VertexConsumer delegate) {
        return wrap(entity, delegate, false);
    }

    public static VertexConsumer wrapWithBlockFaceShading(UniversalPlasticEntity entity, VertexConsumer delegate) {
        return wrap(entity, delegate, true);
    }

    private static VertexConsumer wrap(
        UniversalPlasticEntity entity,
        VertexConsumer delegate,
        boolean shadeBlockFaces
    ) {
        if (!ClearPlasticRenderTypes.isDeferredPassActive() || !isTransparent(entity)) return delegate;
        Vec3 camera = ClearPlasticRenderTypes.deferredCamera();
        float partialTick = ClearPlasticRenderTypes.deferredPartialTick();
        float sourcePartialTick = isLiveWorldEntity(entity) ? partialTick : 1.0F;
        List<Face> occluders = new ArrayList<>();
        List<VolumeOccluder> volumes = new ArrayList<>();
        // 成型网格按源 Cube 保留表面，先把同一制品内部相接的实体面加入遮挡集合。
        entity.getMoldedData().ifPresent(data -> occluders.addAll(
            moldedFaces(entity, data, camera, sourcePartialTick, false)
        ));
        entity.getMoldedData().ifPresent(data -> volumes.add(
            new VolumeOccluder(entity, data.collisionHulls(), camera, sourcePartialTick)
        ));
        addBlockOccluders(entity, camera, partialTick, occluders);
        for (UniversalPlasticEntity other : entity.level().getEntitiesOfClass(
            UniversalPlasticEntity.class,
            entity.getBoundingBox().inflate(CONTACT_EPSILON)
        )) {
            if (other == entity || !other.isAlive() || !matches(entity, other)) continue;
            occluders.addAll(facesOf(other, camera, partialTick, true));
            other.getMoldedData().ifPresent(data -> volumes.add(
                new VolumeOccluder(other, data.collisionHulls(), camera, partialTick)
            ));
        }
        if (occluders.isEmpty() && volumes.isEmpty() && !shadeBlockFaces) return delegate;
        return new TransparentPlasticFaceCullingVertexConsumer(entity, delegate, occluders, volumes, shadeBlockFaces);
    }

    public static void finish(VertexConsumer consumer) {
        if (consumer instanceof TransparentPlasticFaceCullingVertexConsumer culling) culling.finish();
    }

    public static void beginQuad(VertexConsumer consumer, boolean doubleSided) {
        if (consumer instanceof TransparentPlasticFaceCullingVertexConsumer culling) {
            culling.currentDoubleSided = doubleSided;
        }
    }

    private static boolean isTransparent(UniversalPlasticEntity entity) {
        return entity.getDisplayState().is(PlasticraftBlocks.CLEAR_PLASTIC.get())
            || entity.getMoldedData()
                .flatMap(data -> PlasticMaterial.fromMelt(data.material()))
                .map(PlasticMaterial::isTransparent)
                .orElse(false);
    }

    private static boolean matches(UniversalPlasticEntity source, UniversalPlasticEntity other) {
        return isTransparent(other) && source.getDisplayTint() == other.getDisplayTint();
    }

    private static boolean isLiveWorldEntity(UniversalPlasticEntity entity) {
        return entity.level().getEntity(entity.getId()) == entity;
    }

    private static void addBlockOccluders(
        UniversalPlasticEntity entity,
        Vec3 camera,
        float partialTick,
        List<Face> occluders
    ) {
        AABB bounds = entity.getBoundingBox().inflate(CONTACT_EPSILON);
        BlockPos minimum = BlockPos.containing(
            bounds.minX - CONTACT_EPSILON,
            bounds.minY - CONTACT_EPSILON,
            bounds.minZ - CONTACT_EPSILON
        );
        BlockPos maximum = BlockPos.containing(
            bounds.maxX + CONTACT_EPSILON,
            bounds.maxY + CONTACT_EPSILON,
            bounds.maxZ + CONTACT_EPSILON
        );
        for (BlockPos pos : BlockPos.betweenClosed(minimum, maximum)) {
            BlockState state = entity.level().getBlockState(pos);
            if (!matches(entity, state)) continue;
            if (!isLiveWorldEntity(entity)
                && !state.getValue(AbstractPlasticEntityBlock.BONDED)
                && pos.equals(entity.blockPosition())) {
                continue;
            }
            if (state.getValue(AbstractPlasticEntityBlock.BONDED)) {
                if (entity.level().getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
                    && bonded.getOrCreateRenderEntity() instanceof UniversalPlasticEntity other
                    && other != entity
                    && matches(entity, other)) {
                    occluders.addAll(facesOf(other, camera, 1.0F, true));
                }
                continue;
            }
            occluders.addAll(blockFaces(new AABB(pos), camera));
        }
    }

    private static boolean matches(UniversalPlasticEntity entity, BlockState state) {
        return state.is(PlasticraftBlocks.CLEAR_PLASTIC.get())
            && entity.getDisplayTint() == DyeableMaterial.tint(state);
    }

    private static List<Face> facesOf(
        UniversalPlasticEntity entity,
        Vec3 camera,
        float partialTick,
        boolean includeDoubleSided
    ) {
        return entity.getMoldedData()
            .map(data -> moldedFaces(entity, data, camera, partialTick, includeDoubleSided))
            .orElseGet(() -> blockFaces(entity, camera, partialTick));
    }

    private static List<Face> blockFaces(UniversalPlasticEntity entity, Vec3 camera, float partialTick) {
        Vec3 movement = entity.getPosition(partialTick).subtract(entity.position());
        return blockFaces(entity.getBoundingBox().move(movement), camera);
    }

    private static List<Face> blockFaces(AABB worldBounds, Vec3 camera) {
        AABB bounds = worldBounds.move(-camera.x, -camera.y, -camera.z);
        List<Face> faces = new ArrayList<>(6);
        for (int axis = 0; axis < 3; axis++) {
            faces.add(Face.fromBounds(bounds, axis, -1));
            faces.add(Face.fromBounds(bounds, axis, 1));
        }
        return faces;
    }

    private static List<Face> moldedFaces(
        UniversalPlasticEntity entity,
        MoldedPlasticData data,
        Vec3 camera,
        float partialTick,
        boolean includeDoubleSided
    ) {
        List<Face> faces = new ArrayList<>();
        for (MoldingQuad quad : data.surfaceMesh()) {
            if (!includeDoubleSided && quad.doubleSided()) continue;
            List<Vertex> vertices = new ArrayList<>(4);
            vertices.add(vertex(entity, quad.first(), quad.normal(), camera, partialTick));
            vertices.add(vertex(entity, quad.second(), quad.normal(), camera, partialTick));
            vertices.add(vertex(entity, quad.third(), quad.normal(), camera, partialTick));
            vertices.add(vertex(entity, quad.fourth(), quad.normal(), camera, partialTick));
            Face face = Face.create(vertices);
            if (face != null) faces.add(face);
            if (includeDoubleSided && quad.doubleSided()) {
                Face reverse = Face.create(vertices.stream().map(Vertex::reverseNormal).toList());
                if (reverse != null) faces.add(reverse);
            }
        }
        return faces;
    }

    private static Vertex vertex(
        UniversalPlasticEntity entity,
        MoldingVec3 position,
        MoldingVec3 normal,
        Vec3 camera,
        float partialTick
    ) {
        Vec3 transformed = transformPoint(
            entity,
            new Vec3(position.x() / PIXELS_PER_BLOCK, position.y() / PIXELS_PER_BLOCK, position.z() / PIXELS_PER_BLOCK),
            partialTick
        ).subtract(camera);
        Vec3 transformedNormal = rotate(
            new Vec3(normal.x(), normal.y(), normal.z()),
            entity.getOrientation()
        );
        return new Vertex(
            transformed.x,
            transformed.y,
            transformed.z,
            (float) transformedNormal.x,
            (float) transformedNormal.y,
            (float) transformedNormal.z
        );
    }

    private static Vec3 transformPoint(UniversalPlasticEntity entity, Vec3 point, float partialTick) {
        Vec3 pivot = entity.plasticraft$getGeometry().rotationPivot();
        Vec3 origin = entity.plasticraft$getGeometry().entityOrigin();
        return entity.getPosition(partialTick)
            .add(pivot)
            .subtract(origin)
            .add(rotate(point.subtract(pivot), entity.getOrientation()));
    }

    private static Vec3 rotate(Vec3 vector, PlasticEntityOrientation orientation) {
        Direction xAxis = orientation.orthogonalAxis();
        Direction yAxis = orientation.attachmentFace();
        Direction zAxis = orientation.longAxis();
        return new Vec3(
            vector.x * xAxis.getStepX() + vector.y * yAxis.getStepX() + vector.z * zAxis.getStepX(),
            vector.x * xAxis.getStepY() + vector.y * yAxis.getStepY() + vector.z * zAxis.getStepY(),
            vector.x * xAxis.getStepZ() + vector.y * yAxis.getStepZ() + vector.z * zAxis.getStepZ()
        );
    }

    public void finish() {
        if (this.current != null) {
            this.quad.add(this.current);
            this.current = null;
        }
        this.flushQuad();
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (this.current != null) this.quad.add(this.current);
        if (this.quad.size() == 4) this.flushQuad();
        this.current = new Vertex(x, y, z);
        return this;
    }

    @Override
    public void addVertex(
        float x,
        float y,
        float z,
        int color,
        float u,
        float v,
        int overlay,
        int light,
        float normalX,
        float normalY,
        float normalZ
    ) {
        this.addVertex(x, y, z)
            .setColor(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >>> 24)
            .setUv(u, v)
            .setUv1(overlay & 0xFFFF, overlay >>> 16)
            .setUv2(light & 0xFFFF, light >>> 16)
            .setNormal(normalX, normalY, normalZ);
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        Vertex vertex = this.requireCurrent();
        vertex.red = red;
        vertex.green = green;
        vertex.blue = blue;
        vertex.alpha = alpha;
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        Vertex vertex = this.requireCurrent();
        vertex.u = u;
        vertex.v = v;
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        Vertex vertex = this.requireCurrent();
        vertex.overlay = u & 0xFFFF | v << 16;
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        Vertex vertex = this.requireCurrent();
        vertex.light = u & 0xFFFF | v << 16;
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        Vertex vertex = this.requireCurrent();
        vertex.normalX = x;
        vertex.normalY = y;
        vertex.normalZ = z;
        return this;
    }

    private Vertex requireCurrent() {
        if (this.current == null) throw new IllegalStateException("Vertex attributes were written before a vertex");
        return this.current;
    }

    private void flushQuad() {
        if (this.quad.isEmpty()) return;
        if (this.quad.size() != 4) {
            this.quad.forEach(this::emit);
            this.quad.clear();
            return;
        }
        Face face = Face.create(this.quad);
        if (face == null) {
            this.quad.forEach(this::emit);
        } else {
            this.emitVisible(face, !this.currentDoubleSided);
        }
        this.quad.clear();
    }

    private void emitVisible(Face face, boolean volumeCullable) {
        List<Rectangle> covered = new ArrayList<>();
        for (Face occluder : this.occluders) {
            Rectangle rectangle = face.coveredBy(occluder);
            if (rectangle != null) covered.add(rectangle);
        }
        if (volumeCullable) {
            for (VolumeOccluder volume : this.volumes) {
                covered.addAll(volume.coveredBy(face));
            }
        }
        if (covered.isEmpty()) {
            face.vertices.forEach(this::emit);
            return;
        }

        List<Double> firstBounds = boundaries(face.minFirst, face.maxFirst, covered, true);
        List<Double> secondBounds = boundaries(face.minSecond, face.maxSecond, covered, false);
        for (int firstIndex = 0; firstIndex < firstBounds.size() - 1; firstIndex++) {
            double firstMin = firstBounds.get(firstIndex);
            double firstMax = firstBounds.get(firstIndex + 1);
            if (firstMax - firstMin <= CONTACT_EPSILON) continue;
            for (int secondIndex = 0; secondIndex < secondBounds.size() - 1; secondIndex++) {
                double secondMin = secondBounds.get(secondIndex);
                double secondMax = secondBounds.get(secondIndex + 1);
                if (secondMax - secondMin <= CONTACT_EPSILON
                    || isCovered((firstMin + firstMax) * 0.5D, (secondMin + secondMax) * 0.5D, covered)) {
                    continue;
                }
                for (Vertex vertex : face.vertices) {
                    double first = Face.value(vertex, face.firstAxis) <= face.minFirst + CONTACT_EPSILON
                        ? firstMin
                        : firstMax;
                    double second = Face.value(vertex, face.secondAxis) <= face.minSecond + CONTACT_EPSILON
                        ? secondMin
                        : secondMax;
                    this.emit(face.interpolate(first, second));
                }
            }
        }
    }

    private static List<Double> boundaries(
        double minimum,
        double maximum,
        List<Rectangle> rectangles,
        boolean first
    ) {
        List<Double> result = new ArrayList<>();
        addBoundary(result, minimum);
        addBoundary(result, maximum);
        for (Rectangle rectangle : rectangles) {
            addBoundary(result, first ? rectangle.minFirst : rectangle.minSecond);
            addBoundary(result, first ? rectangle.maxFirst : rectangle.maxSecond);
        }
        result.sort(Double::compare);
        return result;
    }

    private static void addBoundary(List<Double> boundaries, double value) {
        for (double existing : boundaries) {
            if (Math.abs(existing - value) <= CONTACT_EPSILON) return;
        }
        boundaries.add(value);
    }

    private static boolean isCovered(double first, double second, List<Rectangle> rectangles) {
        return rectangles.stream().anyMatch(rectangle -> rectangle.contains(first, second));
    }

    private void emit(Vertex vertex) {
        int red = vertex.red;
        int green = vertex.green;
        int blue = vertex.blue;
        if (this.shadeBlockFaces && (vertex.normalX != 0.0F || vertex.normalY != 0.0F || vertex.normalZ != 0.0F)) {
            float shade = this.entity.level().getShade(vertex.normalX, vertex.normalY, vertex.normalZ, true);
            red = shade(red, shade);
            green = shade(green, shade);
            blue = shade(blue, shade);
        }
        this.delegate.addVertex((float) vertex.x, (float) vertex.y, (float) vertex.z)
            .setColor(
                Math.clamp(red, 0, 255),
                Math.clamp(green, 0, 255),
                Math.clamp(blue, 0, 255),
                Math.clamp(vertex.alpha, 0, 255)
            )
            .setUv(vertex.u, vertex.v)
            .setUv1(vertex.overlay & 0xFFFF, vertex.overlay >>> 16)
            .setUv2(vertex.light & 0xFFFF, vertex.light >>> 16)
            .setNormal(vertex.normalX, vertex.normalY, vertex.normalZ);
    }

    private static int shade(int channel, float shade) {
        return Math.clamp(Math.round(channel * shade), 0, 255);
    }

    private static final class Face {
        private final List<Vertex> vertices;
        private final int planeAxis;
        private final int firstAxis;
        private final int secondAxis;
        private final double plane;
        private final double minFirst;
        private final double maxFirst;
        private final double minSecond;
        private final double maxSecond;
        private final int normalDirection;

        private Face(
            List<Vertex> vertices,
            int planeAxis,
            int firstAxis,
            int secondAxis,
            double plane,
            double minFirst,
            double maxFirst,
            double minSecond,
            double maxSecond,
            int normalDirection
        ) {
            this.vertices = List.copyOf(vertices);
            this.planeAxis = planeAxis;
            this.firstAxis = firstAxis;
            this.secondAxis = secondAxis;
            this.plane = plane;
            this.minFirst = minFirst;
            this.maxFirst = maxFirst;
            this.minSecond = minSecond;
            this.maxSecond = maxSecond;
            this.normalDirection = normalDirection;
        }

        private static Face fromBounds(AABB bounds, int axis, int normalDirection) {
            int firstAxis = axis == 0 ? 1 : 0;
            int secondAxis = axis == 2 ? 1 : 2;
            double plane = normalDirection < 0 ? min(bounds, axis) : max(bounds, axis);
            double minFirst = min(bounds, firstAxis);
            double maxFirst = max(bounds, firstAxis);
            double minSecond = min(bounds, secondAxis);
            double maxSecond = max(bounds, secondAxis);
            List<Vertex> vertices = List.of(
                Vertex.at(axis, plane, firstAxis, minFirst, secondAxis, minSecond, axis, normalDirection),
                Vertex.at(axis, plane, firstAxis, maxFirst, secondAxis, minSecond, axis, normalDirection),
                Vertex.at(axis, plane, firstAxis, maxFirst, secondAxis, maxSecond, axis, normalDirection),
                Vertex.at(axis, plane, firstAxis, minFirst, secondAxis, maxSecond, axis, normalDirection)
            );
            return new Face(
                vertices,
                axis,
                firstAxis,
                secondAxis,
                plane,
                minFirst,
                maxFirst,
                minSecond,
                maxSecond,
                normalDirection
            );
        }

        private static Face create(List<Vertex> vertices) {
            double[] minima = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
            double[] maxima = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (Vertex vertex : vertices) {
                minima[0] = Math.min(minima[0], vertex.x);
                minima[1] = Math.min(minima[1], vertex.y);
                minima[2] = Math.min(minima[2], vertex.z);
                maxima[0] = Math.max(maxima[0], vertex.x);
                maxima[1] = Math.max(maxima[1], vertex.y);
                maxima[2] = Math.max(maxima[2], vertex.z);
            }
            int planeAxis = 0;
            for (int axis = 1; axis < 3; axis++) {
                if (maxima[axis] - minima[axis] < maxima[planeAxis] - minima[planeAxis]) planeAxis = axis;
            }
            if (maxima[planeAxis] - minima[planeAxis] > CONTACT_EPSILON) return null;
            int firstAxis = planeAxis == 0 ? 1 : 0;
            int secondAxis = planeAxis == 2 ? 1 : 2;
            if (maxima[firstAxis] - minima[firstAxis] <= CONTACT_EPSILON
                || maxima[secondAxis] - minima[secondAxis] <= CONTACT_EPSILON) {
                return null;
            }
            double normal = 0.0D;
            for (Vertex vertex : vertices) normal += value(vertex, planeAxis + 3);
            if (Math.abs(normal) <= CONTACT_EPSILON) {
                Vertex first = vertices.getFirst();
                Vertex second = vertices.get(1);
                Vertex third = vertices.get(2);
                double firstX = second.x - first.x;
                double firstY = second.y - first.y;
                double firstZ = second.z - first.z;
                double secondX = third.x - first.x;
                double secondY = third.y - first.y;
                double secondZ = third.z - first.z;
                normal = switch (planeAxis) {
                    case 0 -> firstY * secondZ - firstZ * secondY;
                    case 1 -> firstZ * secondX - firstX * secondZ;
                    default -> firstX * secondY - firstY * secondX;
                };
            }
            if (Math.abs(normal) <= CONTACT_EPSILON || !isRectangle(
                vertices,
                firstAxis,
                secondAxis,
                minima[firstAxis],
                maxima[firstAxis],
                minima[secondAxis],
                maxima[secondAxis]
            )) return null;
            return new Face(
                vertices,
                planeAxis,
                firstAxis,
                secondAxis,
                (minima[planeAxis] + maxima[planeAxis]) * 0.5D,
                minima[firstAxis],
                maxima[firstAxis],
                minima[secondAxis],
                maxima[secondAxis],
                normal > 0.0D ? 1 : -1
            );
        }

        private Rectangle coveredBy(Face other) {
            if (this.planeAxis != other.planeAxis
                || this.normalDirection == other.normalDirection
                || Math.abs(this.plane - other.plane) > CONTACT_EPSILON) {
                return null;
            }
            double minFirst = Math.max(this.minFirst, other.minFirst);
            double maxFirst = Math.min(this.maxFirst, other.maxFirst);
            double minSecond = Math.max(this.minSecond, other.minSecond);
            double maxSecond = Math.min(this.maxSecond, other.maxSecond);
            return maxFirst - minFirst <= CONTACT_EPSILON || maxSecond - minSecond <= CONTACT_EPSILON
                ? null
                : new Rectangle(minFirst, maxFirst, minSecond, maxSecond);
        }

        private Vertex interpolate(double first, double second) {
            double firstProgress = (first - this.minFirst) / (this.maxFirst - this.minFirst);
            double secondProgress = (second - this.minSecond) / (this.maxSecond - this.minSecond);
            Vertex result = Vertex.empty();
            for (Vertex vertex : this.vertices) {
                boolean highFirst = value(vertex, this.firstAxis) > (this.minFirst + this.maxFirst) * 0.5D;
                boolean highSecond = value(vertex, this.secondAxis) > (this.minSecond + this.maxSecond) * 0.5D;
                double weight = (highFirst ? firstProgress : 1.0D - firstProgress)
                    * (highSecond ? secondProgress : 1.0D - secondProgress);
                result.add(vertex, weight);
            }
            Vertex firstVertex = this.vertices.getFirst();
            result.overlay = firstVertex.overlay;
            result.light = firstVertex.light;
            result.normalX = firstVertex.normalX;
            result.normalY = firstVertex.normalY;
            result.normalZ = firstVertex.normalZ;
            return result;
        }

        private static boolean isRectangle(
            List<Vertex> vertices,
            int firstAxis,
            int secondAxis,
            double minFirst,
            double maxFirst,
            double minSecond,
            double maxSecond
        ) {
            if (vertices.size() != 4) return false;
            boolean[] corners = new boolean[4];
            for (Vertex vertex : vertices) {
                double first = value(vertex, firstAxis);
                double second = value(vertex, secondAxis);
                boolean lowFirst = Math.abs(first - minFirst) <= CONTACT_EPSILON;
                boolean highFirst = Math.abs(first - maxFirst) <= CONTACT_EPSILON;
                boolean lowSecond = Math.abs(second - minSecond) <= CONTACT_EPSILON;
                boolean highSecond = Math.abs(second - maxSecond) <= CONTACT_EPSILON;
                if (lowFirst == highFirst || lowSecond == highSecond) return false;
                int index = (highFirst ? 1 : 0) | (highSecond ? 2 : 0);
                if (corners[index]) return false;
                corners[index] = true;
            }
            for (boolean corner : corners) {
                if (!corner) return false;
            }
            return true;
        }

        private static double value(Vertex vertex, int axis) {
            return switch (axis) {
                case 0 -> vertex.x;
                case 1 -> vertex.y;
                case 2 -> vertex.z;
                case 3 -> vertex.normalX;
                case 4 -> vertex.normalY;
                default -> vertex.normalZ;
            };
        }

        private static double min(AABB bounds, int axis) {
            return switch (axis) {
                case 0 -> bounds.minX;
                case 1 -> bounds.minY;
                default -> bounds.minZ;
            };
        }

        private static double max(AABB bounds, int axis) {
            return switch (axis) {
                case 0 -> bounds.maxX;
                case 1 -> bounds.maxY;
                default -> bounds.maxZ;
            };
        }
    }

    private static final class VolumeOccluder {
        // 仅投影轴对齐凸体，避免逐像素点测试拖慢复杂模型。
        private static final double PLANE_EPSILON = 1.0E-2D;
        private static final double CONTAINMENT_EPSILON = 1.0E-5D;
        private final UniversalPlasticEntity entity;
        private final List<VolumeHull> hulls;
        private final Vec3 camera;
        private final float partialTick;

        private VolumeOccluder(
            UniversalPlasticEntity entity,
            List<MoldingConvexHull> hulls,
            Vec3 camera,
            float partialTick
        ) {
            this.entity = entity;
            this.hulls = hulls.stream()
                .map(VolumeHull::new)
                .filter(VolumeHull::axisAligned)
                .toList();
            this.camera = camera;
            this.partialTick = partialTick;
        }

        private List<Rectangle> coveredBy(Face face) {
            Vec3 localNormal = inverseRotate(
                new Vec3(face.vertices.getFirst().normalX, face.vertices.getFirst().normalY, face.vertices.getFirst().normalZ),
                this.entity.getOrientation()
            );
            int planeAxis = dominantAxis(localNormal);
            if (Math.abs(component(localNormal, planeAxis)) < 1.0D - PLANE_EPSILON) return List.of();

            double[] minima = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
            double[] maxima = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (int index = 0; index < face.vertices.size(); index++) {
                Vec3 local = toLocal(face.vertices.get(index));
                minima[0] = Math.min(minima[0], local.x);
                minima[1] = Math.min(minima[1], local.y);
                minima[2] = Math.min(minima[2], local.z);
                maxima[0] = Math.max(maxima[0], local.x);
                maxima[1] = Math.max(maxima[1], local.y);
                maxima[2] = Math.max(maxima[2], local.z);
            }
            double plane = (minima[planeAxis] + maxima[planeAxis]) * 0.5D;
            int firstAxis = planeAxis == 0 ? 1 : 0;
            int secondAxis = planeAxis == 2 ? 1 : 2;
            int direction = component(localNormal, planeAxis) < 0.0D ? -1 : 1;
            List<Rectangle> result = new ArrayList<>();
            for (VolumeHull hull : this.hulls) {
                if (!hull.containsPlane(planeAxis, plane, direction)) continue;
                double firstLower = component(hull.bounds.minimum(), firstAxis);
                double firstUpper = component(hull.bounds.maximum(), firstAxis);
                double secondLower = component(hull.bounds.minimum(), secondAxis);
                double secondUpper = component(hull.bounds.maximum(), secondAxis);
                Vec3 lower = fromLocal(firstAxis, firstLower, secondAxis, secondLower, planeAxis, plane);
                Vec3 upperFirst = fromLocal(firstAxis, firstUpper, secondAxis, secondLower, planeAxis, plane);
                Vec3 upperSecond = fromLocal(firstAxis, firstLower, secondAxis, secondUpper, planeAxis, plane);
                Vec3 upper = fromLocal(firstAxis, firstUpper, secondAxis, secondUpper, planeAxis, plane);
                double minFirst = Math.max(face.minFirst, minValue(face.firstAxis, lower, upperFirst, upperSecond, upper));
                double maxFirst = Math.min(face.maxFirst, maxValue(face.firstAxis, lower, upperFirst, upperSecond, upper));
                double minSecond = Math.max(face.minSecond, minValue(face.secondAxis, lower, upperFirst, upperSecond, upper));
                double maxSecond = Math.min(face.maxSecond, maxValue(face.secondAxis, lower, upperFirst, upperSecond, upper));
                if (maxFirst - minFirst > CONTACT_EPSILON && maxSecond - minSecond > CONTACT_EPSILON) {
                    result.add(new Rectangle(minFirst, maxFirst, minSecond, maxSecond));
                }
            }
            return result;
        }

        private Vec3 toLocal(Vertex vertex) {
            Vec3 world = new Vec3(vertex.x + this.camera.x, vertex.y + this.camera.y, vertex.z + this.camera.z);
            Vec3 pivot = this.entity.plasticraft$getGeometry().rotationPivot();
            Vec3 origin = this.entity.plasticraft$getGeometry().entityOrigin();
            Vec3 relative = world.subtract(this.entity.getPosition(this.partialTick)).subtract(pivot).add(origin);
            return inverseRotate(relative, this.entity.getOrientation()).add(pivot).scale(PIXELS_PER_BLOCK);
        }

        private Vec3 fromLocal(int firstAxis, double first, int secondAxis, double second, int planeAxis, double plane) {
            double[] coordinates = {0.0D, 0.0D, 0.0D};
            coordinates[firstAxis] = first / PIXELS_PER_BLOCK;
            coordinates[secondAxis] = second / PIXELS_PER_BLOCK;
            coordinates[planeAxis] = plane / PIXELS_PER_BLOCK;
            Vec3 local = new Vec3(coordinates[0], coordinates[1], coordinates[2]);
            Vec3 world = transformPoint(this.entity, local, this.partialTick);
            return world.subtract(this.camera);
        }

        private static Vec3 inverseRotate(Vec3 vector, PlasticEntityOrientation orientation) {
            Direction xAxis = orientation.orthogonalAxis();
            Direction yAxis = orientation.attachmentFace();
            Direction zAxis = orientation.longAxis();
            return new Vec3(
                vector.x * xAxis.getStepX() + vector.y * xAxis.getStepY() + vector.z * xAxis.getStepZ(),
                vector.x * yAxis.getStepX() + vector.y * yAxis.getStepY() + vector.z * yAxis.getStepZ(),
                vector.x * zAxis.getStepX() + vector.y * zAxis.getStepY() + vector.z * zAxis.getStepZ()
            );
        }

        private static int dominantAxis(Vec3 vector) {
            int axis = 0;
            if (Math.abs(vector.y) > Math.abs(component(vector, axis))) axis = 1;
            if (Math.abs(vector.z) > Math.abs(component(vector, axis))) axis = 2;
            return axis;
        }

        private static double component(Vec3 vector, int axis) {
            return switch (axis) {
                case 0 -> vector.x;
                case 1 -> vector.y;
                default -> vector.z;
            };
        }

        private static double component(MoldingVec3 vector, int axis) {
            return switch (axis) {
                case 0 -> vector.x();
                case 1 -> vector.y();
                default -> vector.z();
            };
        }

        private static double minValue(int axis, Vec3... vertices) {
            double result = Double.POSITIVE_INFINITY;
            for (Vec3 vertex : vertices) result = Math.min(result, component(vertex, axis));
            return result;
        }

        private static double maxValue(int axis, Vec3... vertices) {
            double result = Double.NEGATIVE_INFINITY;
            for (Vec3 vertex : vertices) result = Math.max(result, component(vertex, axis));
            return result;
        }

        private record VolumeHull(MoldingConvexHull hull, MoldingConvexHull.Bounds bounds, boolean axisAligned) {
            private VolumeHull(MoldingConvexHull hull) {
                this(hull, hull.bounds(), isAxisAligned(hull));
            }

            private boolean containsPlane(int axis, double plane, int direction) {
                double sample = plane + direction * CONTACT_EPSILON;
                double minimum = component(this.bounds.minimum(), axis);
                double maximum = component(this.bounds.maximum(), axis);
                return sample >= minimum - CONTAINMENT_EPSILON && sample <= maximum + CONTAINMENT_EPSILON;
            }

            private static boolean isAxisAligned(MoldingConvexHull hull) {
                for (var face : hull.faces()) {
                    double x = Math.abs(face.normal().x());
                    double y = Math.abs(face.normal().y());
                    double z = Math.abs(face.normal().z());
                    if (Math.max(x, Math.max(y, z)) < 1.0D - PLANE_EPSILON) return false;
                }
                return true;
            }
        }
    }

    private record Rectangle(double minFirst, double maxFirst, double minSecond, double maxSecond) {
        private boolean contains(double first, double second) {
            return first > this.minFirst + CONTACT_EPSILON && first < this.maxFirst - CONTACT_EPSILON
                && second > this.minSecond + CONTACT_EPSILON && second < this.maxSecond - CONTACT_EPSILON;
        }
    }

    private static final class Vertex {
        private double x;
        private double y;
        private double z;
        private int red = 255;
        private int green = 255;
        private int blue = 255;
        private int alpha = 255;
        private float u;
        private float v;
        private int overlay;
        private int light;
        private float normalX;
        private float normalY;
        private float normalZ;

        private Vertex(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private Vertex(double x, double y, double z, float normalX, float normalY, float normalZ) {
            this(x, y, z);
            this.normalX = normalX;
            this.normalY = normalY;
            this.normalZ = normalZ;
        }

        private static Vertex empty() {
            Vertex result = new Vertex(0.0D, 0.0D, 0.0D);
            result.red = 0;
            result.green = 0;
            result.blue = 0;
            result.alpha = 0;
            return result;
        }

        private static Vertex at(
            int planeAxis,
            double plane,
            int firstAxis,
            double first,
            int secondAxis,
            double second,
            int normalAxis,
            int normalDirection
        ) {
            double[] coordinates = new double[3];
            coordinates[planeAxis] = plane;
            coordinates[firstAxis] = first;
            coordinates[secondAxis] = second;
            float[] normal = new float[3];
            normal[normalAxis] = normalDirection;
            return new Vertex(coordinates[0], coordinates[1], coordinates[2], normal[0], normal[1], normal[2]);
        }

        private Vertex reverseNormal() {
            Vertex result = new Vertex(this.x, this.y, this.z, -this.normalX, -this.normalY, -this.normalZ);
            result.red = this.red;
            result.green = this.green;
            result.blue = this.blue;
            result.alpha = this.alpha;
            result.u = this.u;
            result.v = this.v;
            result.overlay = this.overlay;
            result.light = this.light;
            return result;
        }

        private void add(Vertex other, double weight) {
            this.x += other.x * weight;
            this.y += other.y * weight;
            this.z += other.z * weight;
            this.red += Math.round(other.red * weight);
            this.green += Math.round(other.green * weight);
            this.blue += Math.round(other.blue * weight);
            this.alpha += Math.round(other.alpha * weight);
            this.u += (float) (other.u * weight);
            this.v += (float) (other.v * weight);
            this.overlay += Math.round(other.overlay * weight);
            this.light += Math.round(other.light * weight);
            this.normalX += (float) (other.normalX * weight);
            this.normalY += (float) (other.normalY * weight);
            this.normalZ += (float) (other.normalZ * weight);
        }
    }
}
