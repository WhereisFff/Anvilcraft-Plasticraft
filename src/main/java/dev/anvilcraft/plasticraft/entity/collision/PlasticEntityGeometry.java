package dev.anvilcraft.plasticraft.entity.collision;

import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** 塑料实体的物理形状、交互轮廓与坐标约定。 */
public final class PlasticEntityGeometry {

    public static final Vec3 UNIT_CUBE_PIVOT = new Vec3(0.5D, 0.5D, 0.5D);
    public static final Vec3 UNIT_CUBE_ENTITY_ORIGIN = new Vec3(0.5D, 0.0D, 0.5D);

    private final VoxelShape collisionShape;
    private final VoxelShape interactionShape;
    private final AABB localBounds;
    private final List<PlasticConvexShape> convexShapes;
    private final Vec3 rotationPivot;
    private final Vec3 entityOrigin;
    private final Map<PlasticEntityOrientation, Oriented> orientations = new ConcurrentHashMap<>();
    private final Map<PlasticEntityOrientation, PlasticConvexCollisionOutline.PackedOutline> packedOutlines =
        new ConcurrentHashMap<>();
    private final Map<Direction, Vec3> surfaceCenters = new ConcurrentHashMap<>();

    private PlasticEntityGeometry(
        VoxelShape collisionShape,
        VoxelShape interactionShape,
        List<PlasticConvexShape> convexShapes,
        Vec3 rotationPivot,
        Vec3 entityOrigin
    ) {
        this.collisionShape = Objects.requireNonNull(collisionShape, "collisionShape").optimize();
        this.interactionShape = Objects.requireNonNull(interactionShape, "interactionShape").optimize();
        List<PlasticConvexShape> physicalShapes = convexShapes.isEmpty() && !this.collisionShape.isEmpty()
            ? this.collisionShape.toAabbs().stream().map(PlasticConvexShape::box).toList()
            : convexShapes;
        this.convexShapes = PlasticConvexShapeOptimizer.optimize(physicalShapes);
        this.localBounds = combinedBounds(this.collisionShape, this.interactionShape, this.convexShapes);
        if (this.localBounds == null) {
            throw new IllegalArgumentException("Plastic entity geometry requires a collision or interaction shape");
        }
        this.rotationPivot = requireFinite(rotationPivot, "rotationPivot");
        this.entityOrigin = requireFinite(entityOrigin, "entityOrigin");
    }

    /** 未显式声明坐标时，以完整轮廓中心为旋转枢轴、轮廓底面中心为实体原点。 */
    public static PlasticEntityGeometry of(VoxelShape collisionShape) {
        return of(collisionShape, collisionShape);
    }

    public static PlasticEntityGeometry of(VoxelShape collisionShape, VoxelShape interactionShape) {
        VoxelShape collision = Objects.requireNonNull(collisionShape, "collisionShape");
        VoxelShape interaction = Objects.requireNonNull(interactionShape, "interactionShape");
        VoxelShape boundsShape = Shapes.or(collision, interaction).optimize();
        if (boundsShape.isEmpty()) {
            throw new IllegalArgumentException("Plastic entity geometry requires a collision or interaction shape");
        }
        AABB bounds = boundsShape.bounds();
        Vec3 pivot = bounds.getCenter();
        Vec3 origin = new Vec3(pivot.x, bounds.minY, pivot.z);
        return of(collision, interaction, pivot, origin);
    }

    public static PlasticEntityGeometry of(
        VoxelShape collisionShape,
        VoxelShape interactionShape,
        Vec3 rotationPivot,
        Vec3 entityOrigin
    ) {
        return new PlasticEntityGeometry(collisionShape, interactionShape, List.of(), rotationPivot, entityOrigin);
    }

    public static PlasticEntityGeometry of(
        VoxelShape collisionShape,
        VoxelShape interactionShape,
        List<PlasticConvexShape> convexShapes,
        Vec3 rotationPivot,
        Vec3 entityOrigin
    ) {
        return new PlasticEntityGeometry(
            collisionShape,
            interactionShape,
            convexShapes,
            rotationPivot,
            entityOrigin
        );
    }

    public VoxelShape collisionShape() {
        return this.collisionShape;
    }

    public VoxelShape interactionShape() {
        return this.interactionShape;
    }

    public List<PlasticConvexShape> convexShapes() {
        return this.convexShapes;
    }

    public PlasticEntityGeometry include(
        VoxelShape additionalCollision,
        VoxelShape additionalInteraction
    ) {
        Objects.requireNonNull(additionalCollision, "additionalCollision");
        Objects.requireNonNull(additionalInteraction, "additionalInteraction");
        if (additionalCollision.isEmpty() && additionalInteraction.isEmpty()) return this;
        ArrayList<PlasticConvexShape> combinedConvexShapes = new ArrayList<>(this.convexShapes);
        additionalCollision.toAabbs().stream()
            .map(PlasticConvexShape::box)
            .forEach(combinedConvexShapes::add);
        return of(
            Shapes.or(this.collisionShape, additionalCollision).optimize(),
            Shapes.or(this.interactionShape, additionalInteraction).optimize(),
            combinedConvexShapes,
            this.rotationPivot,
            this.entityOrigin
        );
    }

    /** 返回物理形状与交互轮廓共同占据的局部范围。 */
    public AABB localBounds() {
        return this.localBounds;
    }

    public Vec3 rotationPivot() {
        return this.rotationPivot;
    }

    public Vec3 entityOrigin() {
        return this.entityOrigin;
    }

    /** 返回局部方向上整个物理外接面的中心；无物理体时回退到交互轮廓。 */
    public Vec3 surfaceCenter(Direction localFace) {
        Objects.requireNonNull(localFace, "localFace");
        return this.surfaceCenters.computeIfAbsent(localFace, this::createSurfaceCenter);
    }

    /** 返回指定朝向下，局部面中心相对于实体位置的偏移。 */
    public Vec3 surfaceOffset(PlasticEntityOrientation orientation, Direction localFace) {
        Objects.requireNonNull(orientation, "orientation");
        Vec3 rotated = rotatePoint(this.surfaceCenter(localFace), orientation, this.rotationPivot);
        return rotated.subtract(this.entityOrigin);
    }

    public Vec3 surfacePointAt(
        Vec3 entityPosition,
        PlasticEntityOrientation orientation,
        Direction localFace
    ) {
        return this.worldPointAt(entityPosition, orientation, this.surfaceCenter(localFace));
    }

    /** 将模型局部点按实体姿态转换为世界坐标。 */
    public Vec3 worldPointAt(
        Vec3 entityPosition,
        PlasticEntityOrientation orientation,
        Vec3 localPoint
    ) {
        Objects.requireNonNull(orientation, "orientation");
        Vec3 rotated = rotatePoint(requireFinite(localPoint, "localPoint"), orientation, this.rotationPivot);
        return requireFinite(entityPosition, "entityPosition").add(rotated.subtract(this.entityOrigin));
    }

    /** 将世界坐标逆变换为模型局部坐标。 */
    public Vec3 localPointAt(
        Vec3 entityPosition,
        PlasticEntityOrientation orientation,
        Vec3 worldPoint
    ) {
        Objects.requireNonNull(orientation, "orientation");
        Vec3 rotated = requireFinite(worldPoint, "worldPoint")
            .subtract(requireFinite(entityPosition, "entityPosition"))
            .add(this.entityOrigin);
        Vec3 offset = rotated.subtract(this.rotationPivot);
        Direction xAxis = orientation.orthogonalAxis();
        Direction yAxis = orientation.attachmentFace();
        Direction zAxis = orientation.longAxis();
        return this.rotationPivot.add(
            componentAlong(offset, xAxis),
            componentAlong(offset, yAxis),
            componentAlong(offset, zAxis)
        );
    }

    /** 返回真实外层平面与旋转枢轴中心线的交点，供胶合时在切向上对齐整个实体。 */
    public Vec3 faceAlignmentPoint(Direction localFace) {
        Objects.requireNonNull(localFace, "localFace");
        Vec3 surface = this.surfaceCenter(localFace);
        return switch (localFace.getAxis()) {
            case X -> new Vec3(surface.x, this.rotationPivot.y, this.rotationPivot.z);
            case Y -> new Vec3(this.rotationPivot.x, surface.y, this.rotationPivot.z);
            case Z -> new Vec3(this.rotationPivot.x, this.rotationPivot.y, surface.z);
        };
    }

    public Vec3 faceAlignmentOffset(PlasticEntityOrientation orientation, Direction localFace) {
        Objects.requireNonNull(orientation, "orientation");
        Vec3 rotated = rotatePoint(this.faceAlignmentPoint(localFace), orientation, this.rotationPivot);
        return rotated.subtract(this.entityOrigin);
    }

    public Vec3 faceAlignmentPointAt(
        Vec3 entityPosition,
        PlasticEntityOrientation orientation,
        Direction localFace
    ) {
        return requireFinite(entityPosition, "entityPosition").add(this.faceAlignmentOffset(orientation, localFace));
    }

    public Oriented oriented(PlasticEntityOrientation orientation) {
        Objects.requireNonNull(orientation, "orientation");
        return this.orientations.computeIfAbsent(orientation, this::createOriented);
    }

    /** 轮廓只取决于朝向后的局部凸体，按实体平移复用，避免推动时每 tick 重建。 */
    public PlasticConvexCollisionOutline.PackedOutline packedOutline(PlasticEntityOrientation orientation) {
        Objects.requireNonNull(orientation, "orientation");
        PlasticConvexCollisionOutline.PackedOutline cached = this.packedOutlines.get(orientation);
        if (cached != null) return cached;
        List<PlasticConvexShape> shapes = this.oriented(orientation).convexShapes();
        PlasticConvexCollisionOutline.PackedOutline built = shapes.isEmpty()
            ? PlasticConvexCollisionOutline.PackedOutline.EMPTY
            : PlasticConvexCollisionOutline.PackedOutline.of(PlasticConvexCollisionOutline.build(shapes));
        PlasticConvexCollisionOutline.PackedOutline existing = this.packedOutlines.putIfAbsent(orientation, built);
        return existing == null ? built : existing;
    }

    public PlasticEntityCollisionBox collisionBoxAt(
        Vec3 entityPosition,
        PlasticEntityOrientation orientation
    ) {
        Oriented oriented = this.oriented(orientation);
        return PlasticEntityCollisionBox.atEntityPosition(
            oriented.collisionShape(),
            oriented.collisionComponents(),
            oriented.bounds(),
            oriented.convexShapes(),
            entityPosition,
            this.entityOrigin
        );
    }

    public VoxelShape interactionShapeAt(
        Vec3 entityPosition,
        PlasticEntityOrientation orientation
    ) {
        Vec3 translation = this.worldTranslation(entityPosition);
        return this.oriented(orientation).interactionShape().move(
            translation.x,
            translation.y,
            translation.z
        );
    }

    public AABB boundingBoxAt(Vec3 entityPosition, PlasticEntityOrientation orientation) {
        return this.oriented(orientation).bounds().move(this.worldTranslation(entityPosition));
    }

    /** 返回按指定朝向贴合原点方块单元后的局部物理形状。 */
    public VoxelShape placedCollisionShape(PlasticEntityOrientation orientation) {
        Vec3 position = this.placementPosition(BlockPos.ZERO, orientation);
        return this.collisionBoxAt(position, orientation).shape();
    }

    /** 返回让指定局部面贴合原点方块单元后的局部物理形状。 */
    public VoxelShape placedCollisionShape(
        PlasticEntityOrientation orientation,
        Direction localFace
    ) {
        Vec3 position = this.placementPosition(BlockPos.ZERO, orientation, localFace);
        return this.collisionBoxAt(position, orientation).shape();
    }

    /** 返回按指定朝向贴合原点方块单元后的局部交互轮廓。 */
    public VoxelShape placedInteractionShape(PlasticEntityOrientation orientation) {
        Vec3 position = this.placementPosition(BlockPos.ZERO, orientation);
        return this.interactionShapeAt(position, orientation);
    }

    /** 返回让指定局部面贴合原点方块单元后的局部交互轮廓。 */
    public VoxelShape placedInteractionShape(
        PlasticEntityOrientation orientation,
        Direction localFace
    ) {
        Vec3 position = this.placementPosition(BlockPos.ZERO, orientation, localFace);
        return this.interactionShapeAt(position, orientation);
    }

    /**
     * 将邻接支撑面的首个方块单元作为放置基准；法向贴住该单元边界，
     * 两条切向轴则以该单元中心对齐，因此超过一格的形状会向两侧展开。
     */
    public Vec3 placementPosition(BlockPos occupiedPos, PlasticEntityOrientation orientation) {
        Objects.requireNonNull(occupiedPos, "occupiedPos");
        PlasticEntityOrientation targetOrientation = Objects.requireNonNull(orientation, "orientation");
        AABB bounds = this.oriented(targetOrientation).bounds();
        Direction attachmentFace = targetOrientation.attachmentFace();
        double translationX = placementTranslation(
            Direction.Axis.X,
            bounds.minX,
            bounds.maxX,
            occupiedPos.getX(),
            attachmentFace
        );
        double translationY = placementTranslation(
            Direction.Axis.Y,
            bounds.minY,
            bounds.maxY,
            occupiedPos.getY(),
            attachmentFace
        );
        double translationZ = placementTranslation(
            Direction.Axis.Z,
            bounds.minZ,
            bounds.maxZ,
            occupiedPos.getZ(),
            attachmentFace
        );
        return this.entityOrigin.add(translationX, translationY, translationZ);
    }

    /**
     * 将指定局部方向上整个物理外接面的中心贴到方块单元对应面的中心。
     * 不能只取到达最外层平面的局部 Cube，否则不对称模型会在目标面上产生切向漂移。
     */
    public Vec3 placementPosition(
        BlockPos occupiedPos,
        PlasticEntityOrientation orientation,
        Direction localFace
    ) {
        Objects.requireNonNull(occupiedPos, "occupiedPos");
        PlasticEntityOrientation targetOrientation = Objects.requireNonNull(orientation, "orientation");
        Direction targetWorldFace = targetOrientation.worldDirection(
            Objects.requireNonNull(localFace, "localFace")
        );
        Vec3 targetFaceCenter = Vec3.atCenterOf(occupiedPos).add(
            targetWorldFace.getStepX() * 0.5D,
            targetWorldFace.getStepY() * 0.5D,
            targetWorldFace.getStepZ() * 0.5D
        );
        return targetFaceCenter.subtract(this.surfaceOffset(targetOrientation, localFace));
    }

    /** 返回模型旋转枢轴在世界中的位置。 */
    public Vec3 rotationCenterAt(Vec3 entityPosition) {
        return this.rotationPivot.add(this.worldTranslation(entityPosition));
    }

    private Oriented createOriented(PlasticEntityOrientation orientation) {
        VoxelShape rotatedCollision = PlasticEntityCollisionShapes.rotate(
            this.collisionShape,
            orientation,
            this.rotationPivot
        );
        VoxelShape rotatedInteraction = PlasticEntityCollisionShapes.rotate(
            this.interactionShape,
            orientation,
            this.rotationPivot
        );
        List<PlasticConvexShape> rotatedConvexShapes = this.convexShapes.stream()
            .map(shape -> shape.rotate(orientation, this.rotationPivot))
            .toList();
        AABB bounds = Objects.requireNonNull(
            combinedBounds(rotatedCollision, rotatedInteraction, rotatedConvexShapes),
            "oriented bounds"
        );
        return new Oriented(
            rotatedCollision,
            rotatedCollision.toAabbs(),
            rotatedInteraction,
            bounds,
            rotatedConvexShapes
        );
    }

    private Vec3 createSurfaceCenter(Direction localFace) {
        VoxelShape surfaceShape = this.collisionShape.isEmpty()
            ? this.interactionShape
            : this.collisionShape;
        AABB bounds = surfaceShape.bounds();
        double plane = faceCoordinate(bounds, localFace);
        Vec3 center = bounds.getCenter();
        return switch (localFace.getAxis()) {
            case X -> new Vec3(plane, center.y, center.z);
            case Y -> new Vec3(center.x, plane, center.z);
            case Z -> new Vec3(center.x, center.y, plane);
        };
    }

    private static Vec3 rotatePoint(
        Vec3 point,
        PlasticEntityOrientation orientation,
        Vec3 pivot
    ) {
        Vec3 local = point.subtract(pivot);
        Direction xAxis = orientation.orthogonalAxis();
        Direction yAxis = orientation.attachmentFace();
        Direction zAxis = orientation.longAxis();
        return pivot.add(
            local.x * xAxis.getStepX() + local.y * yAxis.getStepX() + local.z * zAxis.getStepX(),
            local.x * xAxis.getStepY() + local.y * yAxis.getStepY() + local.z * zAxis.getStepY(),
            local.x * xAxis.getStepZ() + local.y * yAxis.getStepZ() + local.z * zAxis.getStepZ()
        );
    }

    private static double componentAlong(Vec3 vector, Direction direction) {
        return vector.x * direction.getStepX()
            + vector.y * direction.getStepY()
            + vector.z * direction.getStepZ();
    }

    private static double faceCoordinate(AABB box, Direction face) {
        return switch (face) {
            case DOWN -> box.minY;
            case UP -> box.maxY;
            case NORTH -> box.minZ;
            case SOUTH -> box.maxZ;
            case WEST -> box.minX;
            case EAST -> box.maxX;
        };
    }

    private static AABB combinedBounds(
        VoxelShape collisionShape,
        VoxelShape interactionShape,
        List<PlasticConvexShape> convexShapes
    ) {
        AABB result = collisionShape.isEmpty() ? null : collisionShape.bounds();
        if (!interactionShape.isEmpty()) {
            result = result == null ? interactionShape.bounds() : result.minmax(interactionShape.bounds());
        }
        for (PlasticConvexShape shape : convexShapes) {
            result = result == null ? shape.bounds() : result.minmax(shape.bounds());
        }
        return result;
    }

    private Vec3 worldTranslation(Vec3 entityPosition) {
        return requireFinite(entityPosition, "entityPosition").subtract(this.entityOrigin);
    }

    private static double placementTranslation(
        Direction.Axis axis,
        double minimum,
        double maximum,
        int occupiedCoordinate,
        Direction attachmentFace
    ) {
        if (attachmentFace.getAxis() != axis) {
            return occupiedCoordinate + 0.5D - (minimum + maximum) * 0.5D;
        }
        return attachmentFace.getAxisDirection() == Direction.AxisDirection.POSITIVE
            ? occupiedCoordinate - minimum
            : occupiedCoordinate + 1.0D - maximum;
    }

    private static Vec3 requireFinite(Vec3 vector, String name) {
        Vec3 value = Objects.requireNonNull(vector, name);
        if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    public record Oriented(
        VoxelShape collisionShape,
        List<AABB> collisionComponents,
        VoxelShape interactionShape,
        AABB bounds,
        List<PlasticConvexShape> convexShapes
    ) {
        public Oriented {
            Objects.requireNonNull(collisionShape, "collisionShape");
            collisionComponents = List.copyOf(collisionComponents);
            Objects.requireNonNull(interactionShape, "interactionShape");
            Objects.requireNonNull(bounds, "bounds");
            convexShapes = List.copyOf(convexShapes);
        }
    }
}
