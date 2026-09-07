package dev.anvilcraft.plasticraft.client.selection;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.anvilcraft.lib.v2.cube.client.SelectionPart;
import dev.anvilcraft.lib.v2.cube.client.model.CubeModelDecoder;
import dev.anvilcraft.lib.v2.cube.geometry.ConvexShape;
import dev.anvilcraft.lib.v2.cube.geometry.SelectionGeometry;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexShape;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 将公共塑料几何适配为砧库选择几何，缓存不持有实体、世界或世界位置。 */
public final class PlasticSelectionGeometry {
    private static final int MAX_CACHE_BYTES = 16 * 1024 * 1024;
    private static final int POSE_CACHE_BYTES = 128 * 1024;
    private static final int ORIENTATION_COUNT = 24;
    // 520 的面法线归一化会丢弃过小的面；放大缓存坐标后由位姿还原，保留打印薄层。
    static final float GEOMETRY_SCALE = 128.0F;
    private static final double MIN_SELECTION_THICKNESS = 1.0E-4D;
    private static final Cache<PlasticEntityGeometry, Model> MODELS = CacheBuilder.newBuilder()
        .weakKeys()
        .maximumWeight(MAX_CACHE_BYTES)
        .weigher((PlasticEntityGeometry key, Model value) -> value.weight())
        .build();
    private static final Cache<VoxelShape, SelectionPart> FALLBACK_SHAPES = CacheBuilder.newBuilder()
        .weakKeys()
        .maximumWeight(4 * 1024 * 1024)
        .weigher((VoxelShape key, SelectionPart value) -> (int) Math.min(Integer.MAX_VALUE, value.geometry().estimatedBytes() + 512))
        .build();

    private PlasticSelectionGeometry() {
    }

    public static Model get(PlasticEntityGeometry geometry) {
        Model cached = MODELS.getIfPresent(geometry);
        if (cached != null) return cached;
        Model built = create(geometry);
        MODELS.put(geometry, built);
        return built;
    }

    public static void clear() {
        MODELS.invalidateAll();
        MODELS.cleanUp();
        FALLBACK_SHAPES.invalidateAll();
        FALLBACK_SHAPES.cleanUp();
    }

    public static SelectionPart fromShape(VoxelShape shape) {
        SelectionPart cached = FALLBACK_SHAPES.getIfPresent(shape);
        if (cached != null) return cached;
        SelectionPart built = new SelectionPart(voxelGeometry(shape), new Matrix4f().scaling(1.0F / GEOMETRY_SCALE));
        FALLBACK_SHAPES.put(shape, built);
        return built;
    }

    static List<ConvexShape> modelElements(List<BlockElement> elements) {
        List<BlockElement> scaled = new ArrayList<>(elements.size());
        for (BlockElement element : elements) {
            Vector3f from = new Vector3f(element.from);
            Vector3f to = new Vector3f(element.to);
            for (int axis = 0; axis < 3; axis++) {
                if (Math.abs(to.get(axis) - from.get(axis)) < MIN_SELECTION_THICKNESS * 16) {
                    float center = (to.get(axis) + from.get(axis)) * 0.5F;
                    from.setComponent(axis, center - (float) MIN_SELECTION_THICKNESS * 8);
                    to.setComponent(axis, center + (float) MIN_SELECTION_THICKNESS * 8);
                }
            }
            BlockElementRotation rotation = element.rotation == null ? null : new BlockElementRotation(
                new Vector3f(element.rotation.origin()).mul(GEOMETRY_SCALE), element.rotation.axis(),
                element.rotation.angle(), element.rotation.rescale()
            );
            scaled.add(new BlockElement(from.mul(GEOMETRY_SCALE), to.mul(GEOMETRY_SCALE), element.faces, rotation, element.shade));
        }
        return elements.isEmpty() ? List.of() : CubeModelDecoder.decode(scaled, BlockModelRotation.X0_Y0);
    }

    private static SelectionGeometry voxelGeometry(VoxelShape shape) {
        List<ConvexShape> shapes = new ArrayList<>();
        for (AABB box : shape.toAabbs()) {
            shapes.add(box(box));
            if (shapes.size() > SelectionGeometry.MAX_SHAPES) {
                return new SelectionGeometry(List.of(box(shape.bounds())));
            }
        }
        if (shapes.isEmpty()) throw new IllegalArgumentException("Selection shape must not be empty");
        return new SelectionGeometry(shapes);
    }

    private static Model create(PlasticEntityGeometry geometry) {
        List<ConvexShape> solids = new ArrayList<>();
        if (geometry.convexShapes().size() <= SelectionGeometry.MAX_SHAPES) {
            for (PlasticConvexShape shape : geometry.convexShapes()) solids.add(convert(shape));
        } else {
            solids.add(box(geometry.localBounds()));
        }
        SelectionGeometry collision = solids.isEmpty() ? null : new SelectionGeometry(solids);
        SelectionGeometry selection = collision;
        if (solids.isEmpty()) {
            // 零厚度制品没有物理凸体，仍须保留其已有的可选取区域。
            selection = voxelGeometry(geometry.interactionShape());
        } else if (!geometry.additionalSelectionShape().isEmpty()) {
            for (AABB box : geometry.additionalSelectionShape().toAabbs()) {
                solids.add(box(box));
                if (solids.size() > SelectionGeometry.MAX_SHAPES) break;
            }
            selection = solids.size() <= SelectionGeometry.MAX_SHAPES
                ? new SelectionGeometry(solids)
                : new SelectionGeometry(List.of(box(geometry.localBounds())));
        }
        return new Model(collision, selection, geometry.rotationPivot(), geometry.entityOrigin());
    }

    private static ConvexShape convert(PlasticConvexShape source) {
        List<Vec3> vertices = source.vertices();
        Map<Vec3, Integer> indices = new HashMap<>();
        for (int index = 0; index < vertices.size(); index++) indices.put(vertices.get(index), index);
        int[][] faces = new int[source.faces().size()][];
        for (int faceIndex = 0; faceIndex < faces.length; faceIndex++) {
            List<Vec3> face = source.faces().get(faceIndex).vertices();
            int[] faceIndices = new int[face.size()];
            for (int index = 0; index < face.size(); index++) {
                Integer vertex = indices.get(face.get(index));
                if (vertex == null) throw new IllegalArgumentException("Plastic face references an unknown vertex");
                faceIndices[index] = vertex;
            }
            faces[faceIndex] = faceIndices;
        }
        try {
            return new ConvexShape(vertices.stream().map(vertex -> vertex.scale(GEOMETRY_SCALE)).toList(), faces);
        } catch (IllegalArgumentException exception) {
            // 裁切末层可能薄于砧库闭凸体容差，只对该片使用兼容盒，保留其它片的真实斜面。
            return box(source.bounds());
        }
    }

    private static AABB selectableBounds(AABB bounds) {
        return bounds.inflate(
            Math.max(0, MIN_SELECTION_THICKNESS - bounds.getXsize()) * 0.5D,
            Math.max(0, MIN_SELECTION_THICKNESS - bounds.getYsize()) * 0.5D,
            Math.max(0, MIN_SELECTION_THICKNESS - bounds.getZsize()) * 0.5D
        );
    }

    private static ConvexShape box(AABB bounds) {
        AABB selected = selectableBounds(bounds);
        return ConvexShape.box(new AABB(
            selected.minX * GEOMETRY_SCALE, selected.minY * GEOMETRY_SCALE, selected.minZ * GEOMETRY_SCALE,
            selected.maxX * GEOMETRY_SCALE, selected.maxY * GEOMETRY_SCALE, selected.maxZ * GEOMETRY_SCALE
        ));
    }

    public static final class Model {
        private final @Nullable SelectionGeometry collision;
        private final SelectionGeometry selection;
        private final Vec3 pivot;
        private final Vec3 origin;
        private final SelectionPart[] selected = new SelectionPart[ORIENTATION_COUNT];
        private final SelectionPart[] colliding = new SelectionPart[ORIENTATION_COUNT];
        private final List<List<SelectionPart>> placed = new ArrayList<>(
            Collections.nCopies(ORIENTATION_COUNT * 6, List.of())
        );

        private Model(@Nullable SelectionGeometry collision, SelectionGeometry selection, Vec3 pivot, Vec3 origin) {
            this.collision = collision;
            this.selection = selection;
            this.pivot = pivot;
            this.origin = origin;
        }

        public SelectionPart selection(PlasticEntityOrientation orientation) {
            int index = index(orientation);
            SelectionPart part = this.selected[index];
            if (part == null) {
                part = new SelectionPart(this.selection, this.transform(orientation));
                this.selected[index] = part;
            }
            return part;
        }

        public @Nullable SelectionPart collision(PlasticEntityOrientation orientation) {
            if (this.collision == null) return null;
            if (this.collision == this.selection) return this.selection(orientation);
            int index = index(orientation);
            SelectionPart part = this.colliding[index];
            if (part == null) {
                part = new SelectionPart(this.collision, this.transform(orientation));
                this.colliding[index] = part;
            }
            return part;
        }

        public List<SelectionPart> placed(PlasticEntityGeometry source, PlasticEntityOrientation orientation, Direction face) {
            int index = index(orientation) * 6 + face.get3DDataValue();
            List<SelectionPart> parts = this.placed.get(index);
            if (!parts.isEmpty()) return parts;
            Vec3 position = source.placementPosition(BlockPos.ZERO, orientation, face);
            Matrix4f transform = new Matrix4f().translation((float) position.x, (float) position.y, (float) position.z)
                .mul(this.transform(orientation));
            parts = List.of(new SelectionPart(this.selection, transform));
            this.placed.set(index, parts);
            return parts;
        }

        private Matrix4f transform(PlasticEntityOrientation orientation) {
            Direction x = orientation.orthogonalAxis();
            Direction y = orientation.attachmentFace();
            Direction z = orientation.longAxis();
            Matrix4f basis = new Matrix4f(
                x.getStepX(), x.getStepY(), x.getStepZ(), 0,
                y.getStepX(), y.getStepY(), y.getStepZ(), 0,
                z.getStepX(), z.getStepY(), z.getStepZ(), 0,
                0, 0, 0, 1
            );
            return new Matrix4f().translation(
                (float) (this.pivot.x - this.origin.x),
                (float) (this.pivot.y - this.origin.y),
                (float) (this.pivot.z - this.origin.z)
            ).mul(basis).translate((float) -this.pivot.x, (float) -this.pivot.y, (float) -this.pivot.z)
                .scale(1.0F / GEOMETRY_SCALE);
        }

        private int weight() {
            long bytes = POSE_CACHE_BYTES + this.selection.estimatedBytes();
            if (this.collision != null && this.collision != this.selection) bytes += this.collision.estimatedBytes();
            return (int) Math.min(Integer.MAX_VALUE, bytes);
        }

        private static int index(PlasticEntityOrientation orientation) {
            return orientation.attachmentFace().get3DDataValue() * 4 + orientation.quarterTurn();
        }
    }
}
