package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.client.renderer.ClearPlasticRenderTypes;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.molding.bake.MoldingQuad;
import dev.anvilcraft.plasticraft.molding.bake.MoldingConvexHull;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** 将相同颜色透明塑料的真实接触区域从表面网格中裁掉，避免保留内部重叠面。 */
public final class TransparentPlasticFaceCullingVertexConsumer implements VertexConsumer {
    private static final double CONTACT_EPSILON = 1.0E-3D;
    private static final double PIXELS_PER_BLOCK = 16.0D;
    // 仅投影轴对齐凸体，避免逐像素点测试拖慢复杂模型。
    private static final double PLANE_EPSILON = 1.0E-2D;

    /**
     * 按实体缓存的世界坐标遮挡面。
     *
     * <p>表面变换与矩形化只依赖插值后的位置、朝向和显示状态，静止实体因此在整段静止期间
     * 复用同一套遮挡面。仅在渲染线程的延迟透明阶段访问，不需要额外同步。</p>
     */
    private static final Map<UniversalPlasticEntity, CachedOccluders> OCCLUDER_CACHE = new WeakHashMap<>();

    private final UniversalPlasticEntity entity;
    private final VertexConsumer delegate;
    private final OccluderBuckets occluders;
    private final List<VolumeBox> volumes;
    private final Vec3 camera;
    private final boolean shadeBlockFaces;
    /** 顶点、来面和覆盖矩形都只在单个四边形内有效，逐帧重发网格时必须复用而不是重新分配。 */
    private final Vertex[] quad = Vertex.array(4);
    private final Face face = new Face();
    private final RectangleBuffer covered = new RectangleBuffer();
    private final Vertex emitted = Vertex.empty();
    private double[] firstBounds = new double[8];
    private double[] secondBounds = new double[8];
    private int quadSize;
    private Vertex current;
    private boolean currentDoubleSided;

    private TransparentPlasticFaceCullingVertexConsumer(
        UniversalPlasticEntity entity,
        VertexConsumer delegate,
        OccluderBuckets occluders,
        List<VolumeBox> volumes,
        Vec3 camera,
        boolean shadeBlockFaces
    ) {
        this.entity = entity;
        this.delegate = delegate;
        this.occluders = occluders;
        this.volumes = volumes;
        this.camera = camera;
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
        // 成型网格按源 Cube 保留表面，自身内部相接的实体面同样参与遮挡。
        CachedOccluders self = cached(entity, partialTick);
        List<PlanarOccluder> neighbours = new ArrayList<>();
        List<VolumeBox> volumes = self.volumes();
        addBlockOccluders(entity, partialTick, neighbours);
        for (UniversalPlasticEntity other : entity.level().getEntitiesOfClass(
            UniversalPlasticEntity.class,
            entity.getBoundingBox().inflate(CONTACT_EPSILON)
        )) {
            if (other == entity || !other.isAlive() || !matches(entity, other)) continue;
            CachedOccluders neighbour = cached(other, partialTick);
            neighbours.addAll(neighbour.shared());
            if (!neighbour.volumes().isEmpty()) {
                // 自身的凸体集合来自缓存，追加邻居时才需要另建一份可变列表。
                if (volumes == self.volumes()) volumes = new ArrayList<>(volumes);
                volumes.addAll(neighbour.volumes());
            }
        }
        // 孤立摆放时邻居集合为空，直接复用缓存好的分桶结果，整帧不产生遮挡面分配。
        OccluderBuckets buckets = neighbours.isEmpty()
            ? self.ownBuckets()
            : OccluderBuckets.of(self.own(), neighbours);
        if (buckets.isEmpty() && volumes.isEmpty() && !shadeBlockFaces) return delegate;
        return new TransparentPlasticFaceCullingVertexConsumer(
            entity,
            delegate,
            buckets,
            volumes,
            camera,
            shadeBlockFaces
        );
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
        return PlasticEntityRenderHelper.isTransparent(entity);
    }

    private static boolean matches(UniversalPlasticEntity source, UniversalPlasticEntity other) {
        return isTransparent(other) && source.getDisplayTint() == other.getDisplayTint();
    }

    private static boolean isLiveWorldEntity(UniversalPlasticEntity entity) {
        return entity.level().getEntity(entity.getId()) == entity;
    }

    /** 方块渲染代理没有上一刻位置作为插值基准，必须直接取目标位置。 */
    private static float sourcePartialTick(UniversalPlasticEntity entity, float partialTick) {
        return isLiveWorldEntity(entity) ? partialTick : 1.0F;
    }

    /**
     * 取得实体的世界坐标遮挡面，位置、朝向、显示状态或制品形状变化时才重建。
     */
    private static CachedOccluders cached(UniversalPlasticEntity entity, float partialTick) {
        float source = sourcePartialTick(entity, partialTick);
        Vec3 renderPosition = entity.getPosition(source);
        PlasticEntityOrientation orientation = entity.getOrientation();
        BlockState displayState = entity.getDisplayState();
        MoldedPlasticData data = entity.getMoldedData().orElse(null);
        // 锤击改形不改变位置、朝向与显示状态，必须靠形状哈希才能让缓存失效。
        String shapeHash = data == null ? "" : data.shapeHash();
        CachedOccluders cached = OCCLUDER_CACHE.get(entity);
        if (cached != null && cached.matches(renderPosition, orientation, displayState, shapeHash)) return cached;

        CachedOccluders result;
        if (data == null) {
            result = new CachedOccluders(
                renderPosition,
                orientation,
                displayState,
                shapeHash,
                List.of(),
                blockOccluders(entity.getBoundingBox().move(renderPosition.subtract(entity.position()))),
                OccluderBuckets.EMPTY,
                List.of()
            );
        } else {
            List<PlanarOccluder> own = moldedOccluders(entity, data, source, false);
            result = new CachedOccluders(
                renderPosition,
                orientation,
                displayState,
                shapeHash,
                own,
                moldedOccluders(entity, data, source, true),
                OccluderBuckets.of(own, List.of()),
                volumeBoxes(entity, data, source)
            );
        }
        OCCLUDER_CACHE.put(entity, result);
        return result;
    }

    private static List<VolumeBox> volumeBoxes(
        UniversalPlasticEntity entity,
        MoldedPlasticData data,
        float partialTick
    ) {
        List<VolumeBox> boxes = new ArrayList<>(data.collisionHulls().size());
        for (MoldingConvexHull hull : data.collisionHulls()) {
            if (!VolumeBox.isAxisAligned(hull)) continue;
            boxes.add(VolumeBox.of(entity, hull, partialTick));
        }
        return List.copyOf(boxes);
    }

    private static void addBlockOccluders(
        UniversalPlasticEntity entity,
        float partialTick,
        List<PlanarOccluder> occluders
    ) {
        AABB bounds = entity.getBoundingBox().inflate(CONTACT_EPSILON);
        int minX = Mth.floor(bounds.minX - CONTACT_EPSILON);
        int minY = Mth.floor(bounds.minY - CONTACT_EPSILON);
        int minZ = Mth.floor(bounds.minZ - CONTACT_EPSILON);
        int maxX = Mth.floor(bounds.maxX + CONTACT_EPSILON);
        int maxY = Mth.floor(bounds.maxY + CONTACT_EPSILON);
        int maxZ = Mth.floor(bounds.maxZ + CONTACT_EPSILON);
        // 显式整数游标遍历，邻域没有同色透明方块时完全不产生分配。
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    BlockState state = entity.level().getBlockState(cursor);
                    if (!matches(entity, state)) continue;
                    if (!isLiveWorldEntity(entity)
                        && !state.getValue(AbstractPlasticEntityBlock.BONDED)
                        && cursor.equals(entity.blockPosition())) {
                        continue;
                    }
                    if (state.getValue(AbstractPlasticEntityBlock.BONDED)) {
                        if (entity.level().getBlockEntity(cursor) instanceof BondedEntityBlockEntity bonded
                            && bonded.getOrCreateRenderEntity() instanceof UniversalPlasticEntity other
                            && other != entity
                            && matches(entity, other)) {
                            occluders.addAll(cached(other, partialTick).shared());
                        }
                        continue;
                    }
                    occluders.addAll(blockOccluders(new AABB(cursor)));
                }
            }
        }
    }

    private static boolean matches(UniversalPlasticEntity entity, BlockState state) {
        return state.is(PlasticraftBlocks.CLEAR_PLASTIC.get())
            && entity.getDisplayTint() == DyeableMaterial.tint(state);
    }

    /** 方块与非成型制品用包围盒的六个面充当遮挡面。 */
    private static List<PlanarOccluder> blockOccluders(AABB bounds) {
        List<PlanarOccluder> occluders = new ArrayList<>(6);
        for (int axis = 0; axis < 3; axis++) {
            int firstAxis = axis == 0 ? 1 : 0;
            int secondAxis = axis == 2 ? 1 : 2;
            double minFirst = Face.min(bounds, firstAxis);
            double maxFirst = Face.max(bounds, firstAxis);
            double minSecond = Face.min(bounds, secondAxis);
            double maxSecond = Face.max(bounds, secondAxis);
            occluders.add(new PlanarOccluder(
                axis, Face.min(bounds, axis), minFirst, maxFirst, minSecond, maxSecond, -1
            ));
            occluders.add(new PlanarOccluder(
                axis, Face.max(bounds, axis), minFirst, maxFirst, minSecond, maxSecond, 1
            ));
        }
        return List.copyOf(occluders);
    }

    /** 制品表面的遮挡面，按世界坐标输出，使结果与相机无关从而可以跨帧缓存。 */
    private static List<PlanarOccluder> moldedOccluders(
        UniversalPlasticEntity entity,
        MoldedPlasticData data,
        float partialTick,
        boolean includeDoubleSided
    ) {
        List<PlanarOccluder> occluders = new ArrayList<>();
        Vertex[] vertices = Vertex.array(4);
        Vertex[] reversed = Vertex.array(4);
        Face face = new Face();
        for (MoldingQuad quad : data.surfaceMesh()) {
            if (!includeDoubleSided && quad.doubleSided()) continue;
            vertex(vertices[0], entity, quad.first(), quad.normal(), partialTick);
            vertex(vertices[1], entity, quad.second(), quad.normal(), partialTick);
            vertex(vertices[2], entity, quad.third(), quad.normal(), partialTick);
            vertex(vertices[3], entity, quad.fourth(), quad.normal(), partialTick);
            if (face.init(vertices, 4)) occluders.add(PlanarOccluder.of(face));
            // 零厚度面两侧都参与遮挡。法线朝向仍交给 Face 判定，因为它在法线退化时会回退到
            // 顶点叉积，那种情况下反向面的朝向与正向面相同，不能简单取反。
            if (includeDoubleSided && quad.doubleSided()) {
                for (int index = 0; index < 4; index++) reversed[index].setReversedNormal(vertices[index]);
                if (face.init(reversed, 4)) occluders.add(PlanarOccluder.of(face));
            }
        }
        return List.copyOf(occluders);
    }

    private static void vertex(
        Vertex target,
        UniversalPlasticEntity entity,
        MoldingVec3 position,
        MoldingVec3 normal,
        float partialTick
    ) {
        Vec3 transformed = transformPoint(
            entity,
            new Vec3(position.x() / PIXELS_PER_BLOCK, position.y() / PIXELS_PER_BLOCK, position.z() / PIXELS_PER_BLOCK),
            partialTick
        );
        Vec3 transformedNormal = rotate(
            new Vec3(normal.x(), normal.y(), normal.z()),
            entity.getOrientation()
        );
        target.reset(transformed.x, transformed.y, transformed.z);
        target.normalX = (float) transformedNormal.x;
        target.normalY = (float) transformedNormal.y;
        target.normalZ = (float) transformedNormal.z;
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

    private static double component(Vec3 vector, int axis) {
        return switch (axis) {
            case 0 -> vector.x;
            case 1 -> vector.y;
            default -> vector.z;
        };
    }

    public void finish() {
        if (this.current != null) {
            this.quadSize++;
            this.current = null;
        }
        this.flushQuad();
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (this.current != null) this.quadSize++;
        if (this.quadSize == 4) this.flushQuad();
        this.current = this.quad[this.quadSize].reset(x, y, z);
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
        int size = this.quadSize;
        this.quadSize = 0;
        if (size == 0) return;
        if (size != 4 || !this.face.init(this.quad, size)) {
            for (int index = 0; index < size; index++) this.emit(this.quad[index]);
            return;
        }
        this.emitVisible(this.face, !this.currentDoubleSided);
    }

    private void emitVisible(Face face, boolean volumeCullable) {
        this.covered.clear();
        for (PlanarOccluder occluder : this.occluders.facing(face.planeAxis, face.normalDirection)) {
            this.addCoveredBy(face, occluder);
        }
        if (volumeCullable) {
            double worldPlane = face.plane + component(this.camera, face.planeAxis);
            for (VolumeBox volume : this.volumes) {
                this.addCoveredBy(face, worldPlane, volume);
            }
        }
        if (this.covered.isEmpty()) {
            for (int index = 0; index < face.vertexCount; index++) this.emit(face.vertices[index]);
            return;
        }

        this.firstBounds = this.covered.grow(this.firstBounds);
        this.secondBounds = this.covered.grow(this.secondBounds);
        int firstCount = this.covered.boundaries(this.firstBounds, face.minFirst, face.maxFirst, true);
        int secondCount = this.covered.boundaries(this.secondBounds, face.minSecond, face.maxSecond, false);
        for (int firstIndex = 0; firstIndex < firstCount - 1; firstIndex++) {
            double firstMin = this.firstBounds[firstIndex];
            double firstMax = this.firstBounds[firstIndex + 1];
            if (firstMax - firstMin <= CONTACT_EPSILON) continue;
            for (int secondIndex = 0; secondIndex < secondCount - 1; secondIndex++) {
                double secondMin = this.secondBounds[secondIndex];
                double secondMax = this.secondBounds[secondIndex + 1];
                if (secondMax - secondMin <= CONTACT_EPSILON
                    || this.covered.contains((firstMin + firstMax) * 0.5D, (secondMin + secondMax) * 0.5D)) {
                    continue;
                }
                for (int index = 0; index < face.vertexCount; index++) {
                    Vertex vertex = face.vertices[index];
                    double first = Face.value(vertex, face.firstAxis) <= face.minFirst + CONTACT_EPSILON
                        ? firstMin
                        : firstMax;
                    double second = Face.value(vertex, face.secondAxis) <= face.minSecond + CONTACT_EPSILON
                        ? secondMin
                        : secondMax;
                    face.interpolate(first, second, this.emitted);
                    this.emit(this.emitted);
                }
            }
        }
    }

    /**
     * 判定来面被遮挡面覆盖的区域。
     *
     * <p>遮挡面存的是世界坐标而来面是相机坐标，因此用相机偏移换算比较值，
     * 而不是每帧把整套遮挡面平移到相机坐标系重建一遍。同轴与法线反向已由分桶保证。</p>
     */
    private void addCoveredBy(Face face, PlanarOccluder occluder) {
        if (Math.abs(face.plane + component(this.camera, face.planeAxis) - occluder.plane()) > CONTACT_EPSILON) {
            return;
        }
        double firstOffset = component(this.camera, face.firstAxis);
        double secondOffset = component(this.camera, face.secondAxis);
        double minFirst = Math.max(face.minFirst, occluder.minFirst() - firstOffset);
        double maxFirst = Math.min(face.maxFirst, occluder.maxFirst() - firstOffset);
        double minSecond = Math.max(face.minSecond, occluder.minSecond() - secondOffset);
        double maxSecond = Math.min(face.maxSecond, occluder.maxSecond() - secondOffset);
        if (maxFirst - minFirst <= CONTACT_EPSILON || maxSecond - minSecond <= CONTACT_EPSILON) return;
        this.covered.add(minFirst, maxFirst, minSecond, maxSecond);
    }

    /** 朝向实心凸体内部的面被该凸体在世界坐标下的横截范围覆盖。 */
    private void addCoveredBy(Face face, double worldPlane, VolumeBox volume) {
        if (!volume.facesInterior(face.planeAxis, worldPlane, face.normalDirection)) return;
        double firstOffset = component(this.camera, face.firstAxis);
        double secondOffset = component(this.camera, face.secondAxis);
        double minFirst = Math.max(face.minFirst, volume.minimum(face.firstAxis) - firstOffset);
        double maxFirst = Math.min(face.maxFirst, volume.maximum(face.firstAxis) - firstOffset);
        double minSecond = Math.max(face.minSecond, volume.minimum(face.secondAxis) - secondOffset);
        double maxSecond = Math.min(face.maxSecond, volume.maximum(face.secondAxis) - secondOffset);
        if (maxFirst - minFirst <= CONTACT_EPSILON || maxSecond - minSecond <= CONTACT_EPSILON) return;
        this.covered.add(minFirst, maxFirst, minSecond, maxSecond);
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
        this.delegate.addVertex(
            (float) vertex.x,
            (float) vertex.y,
            (float) vertex.z,
            FastColor.ARGB32.color(
                Math.clamp(vertex.alpha, 0, 255),
                Math.clamp(red, 0, 255),
                Math.clamp(green, 0, 255),
                Math.clamp(blue, 0, 255)
            ),
            vertex.u,
            vertex.v,
            vertex.overlay,
            vertex.light,
            vertex.normalX,
            vertex.normalY,
            vertex.normalZ
        );
    }

    private static int shade(int channel, float shade) {
        return Math.clamp(Math.round(channel * shade), 0, 255);
    }

    /** 单个实体缓存下来的世界坐标遮挡面与凸体。 */
    private record CachedOccluders(
        Vec3 renderPosition,
        PlasticEntityOrientation orientation,
        BlockState displayState,
        String shapeHash,
        List<PlanarOccluder> own,
        List<PlanarOccluder> shared,
        OccluderBuckets ownBuckets,
        List<VolumeBox> volumes
    ) {
        private boolean matches(
            Vec3 renderPosition,
            PlasticEntityOrientation orientation,
            BlockState displayState,
            String shapeHash
        ) {
            return this.renderPosition.equals(renderPosition)
                && this.orientation.equals(orientation)
                && this.displayState == displayState
                && this.shapeHash.equals(shapeHash);
        }
    }

    /**
     * 世界坐标下的轴对齐遮挡矩形。
     *
     * <p>覆盖判定只用到平面位置与两个切向范围，不涉及顶点也不涉及相机；切向轴由平面轴唯一确定，
     * 因此无需另存。</p>
     */
    private record PlanarOccluder(
        int planeAxis,
        double plane,
        double minFirst,
        double maxFirst,
        double minSecond,
        double maxSecond,
        int normalDirection
    ) {
        private static PlanarOccluder of(Face face) {
            return new PlanarOccluder(
                face.planeAxis,
                face.plane,
                face.minFirst,
                face.maxFirst,
                face.minSecond,
                face.maxSecond,
                face.normalDirection
            );
        }
    }

    /**
     * 按 (平面轴, 法线朝向) 分成六桶的遮挡面集合。
     *
     * <p>只有同轴且法线相反的面才可能贴合，因此逐面判定从遍历整套遮挡面收敛到只遍历一桶。</p>
     */
    private static final class OccluderBuckets {
        private static final int BUCKETS = 6;
        private static final OccluderBuckets EMPTY = new OccluderBuckets(List.of(), List.of());
        private final List<List<PlanarOccluder>> buckets;
        private final boolean empty;

        private OccluderBuckets(List<PlanarOccluder> first, List<PlanarOccluder> second) {
            List<List<PlanarOccluder>> result = new ArrayList<>(BUCKETS);
            for (int index = 0; index < BUCKETS; index++) result.add(new ArrayList<>());
            distribute(result, first);
            distribute(result, second);
            this.buckets = result;
            this.empty = first.isEmpty() && second.isEmpty();
        }

        private static OccluderBuckets of(List<PlanarOccluder> first, List<PlanarOccluder> second) {
            return first.isEmpty() && second.isEmpty() ? EMPTY : new OccluderBuckets(first, second);
        }

        private static void distribute(List<List<PlanarOccluder>> buckets, List<PlanarOccluder> occluders) {
            for (PlanarOccluder occluder : occluders) {
                buckets.get(index(occluder.planeAxis(), occluder.normalDirection())).add(occluder);
            }
        }

        /** 取与给定面同轴、法线相反的那一桶。 */
        private List<PlanarOccluder> facing(int planeAxis, int normalDirection) {
            return this.buckets.get(index(planeAxis, -normalDirection));
        }

        private boolean isEmpty() {
            return this.empty;
        }

        private static int index(int planeAxis, int normalDirection) {
            return planeAxis * 2 + (normalDirection > 0 ? 1 : 0);
        }
    }

    /** 单个候选四边形的轴对齐解析结果；实例在同一消费者内复用，只在填入到用完之间有效。 */
    private static final class Face {
        private Vertex[] vertices = Vertex.EMPTY;
        private int vertexCount;
        private int planeAxis;
        private int firstAxis;
        private int secondAxis;
        private double plane;
        private double minFirst;
        private double maxFirst;
        private double minSecond;
        private double maxSecond;
        private int normalDirection;

        /** 填入候选四边形，返回它是否是可参与遮挡运算的轴对齐矩形。 */
        private boolean init(Vertex[] vertices, int count) {
            this.vertices = vertices;
            this.vertexCount = count;
            double minimumX = Double.POSITIVE_INFINITY;
            double minimumY = Double.POSITIVE_INFINITY;
            double minimumZ = Double.POSITIVE_INFINITY;
            double maximumX = Double.NEGATIVE_INFINITY;
            double maximumY = Double.NEGATIVE_INFINITY;
            double maximumZ = Double.NEGATIVE_INFINITY;
            for (int index = 0; index < count; index++) {
                Vertex vertex = vertices[index];
                minimumX = Math.min(minimumX, vertex.x);
                minimumY = Math.min(minimumY, vertex.y);
                minimumZ = Math.min(minimumZ, vertex.z);
                maximumX = Math.max(maximumX, vertex.x);
                maximumY = Math.max(maximumY, vertex.y);
                maximumZ = Math.max(maximumZ, vertex.z);
            }
            double spanX = maximumX - minimumX;
            double spanY = maximumY - minimumY;
            double spanZ = maximumZ - minimumZ;
            int planeAxis = 0;
            if (spanY < spanX) planeAxis = 1;
            if (spanZ < (planeAxis == 0 ? spanX : spanY)) planeAxis = 2;
            double planeSpan = planeAxis == 0 ? spanX : planeAxis == 1 ? spanY : spanZ;
            if (planeSpan > CONTACT_EPSILON) return false;
            int firstAxis = planeAxis == 0 ? 1 : 0;
            int secondAxis = planeAxis == 2 ? 1 : 2;
            double minFirst = axisValue(firstAxis, minimumX, minimumY, minimumZ);
            double maxFirst = axisValue(firstAxis, maximumX, maximumY, maximumZ);
            double minSecond = axisValue(secondAxis, minimumX, minimumY, minimumZ);
            double maxSecond = axisValue(secondAxis, maximumX, maximumY, maximumZ);
            if (maxFirst - minFirst <= CONTACT_EPSILON || maxSecond - minSecond <= CONTACT_EPSILON) {
                return false;
            }
            double normal = 0.0D;
            for (int index = 0; index < count; index++) normal += value(vertices[index], planeAxis + 3);
            if (Math.abs(normal) <= CONTACT_EPSILON) {
                Vertex first = vertices[0];
                Vertex second = vertices[1];
                Vertex third = vertices[2];
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
                count,
                firstAxis,
                secondAxis,
                minFirst,
                maxFirst,
                minSecond,
                maxSecond
            )) return false;
            this.planeAxis = planeAxis;
            this.firstAxis = firstAxis;
            this.secondAxis = secondAxis;
            this.plane = (axisValue(planeAxis, minimumX, minimumY, minimumZ)
                + axisValue(planeAxis, maximumX, maximumY, maximumZ)) * 0.5D;
            this.minFirst = minFirst;
            this.maxFirst = maxFirst;
            this.minSecond = minSecond;
            this.maxSecond = maxSecond;
            this.normalDirection = normal > 0.0D ? 1 : -1;
            return true;
        }

        private static double axisValue(int axis, double x, double y, double z) {
            return switch (axis) {
                case 0 -> x;
                case 1 -> y;
                default -> z;
            };
        }

        private void interpolate(double first, double second, Vertex target) {
            double firstProgress = (first - this.minFirst) / (this.maxFirst - this.minFirst);
            double secondProgress = (second - this.minSecond) / (this.maxSecond - this.minSecond);
            target.clear();
            for (int index = 0; index < this.vertexCount; index++) {
                Vertex vertex = this.vertices[index];
                boolean highFirst = value(vertex, this.firstAxis) > (this.minFirst + this.maxFirst) * 0.5D;
                boolean highSecond = value(vertex, this.secondAxis) > (this.minSecond + this.maxSecond) * 0.5D;
                double weight = (highFirst ? firstProgress : 1.0D - firstProgress)
                    * (highSecond ? secondProgress : 1.0D - secondProgress);
                target.add(vertex, weight);
            }
            Vertex firstVertex = this.vertices[0];
            target.overlay = firstVertex.overlay;
            target.light = firstVertex.light;
            target.normalX = firstVertex.normalX;
            target.normalY = firstVertex.normalY;
            target.normalZ = firstVertex.normalZ;
        }

        private static boolean isRectangle(
            Vertex[] vertices,
            int count,
            int firstAxis,
            int secondAxis,
            double minFirst,
            double maxFirst,
            double minSecond,
            double maxSecond
        ) {
            if (count != 4) return false;
            int corners = 0;
            for (int index = 0; index < count; index++) {
                Vertex vertex = vertices[index];
                double first = value(vertex, firstAxis);
                double second = value(vertex, secondAxis);
                boolean lowFirst = Math.abs(first - minFirst) <= CONTACT_EPSILON;
                boolean highFirst = Math.abs(first - maxFirst) <= CONTACT_EPSILON;
                boolean lowSecond = Math.abs(second - minSecond) <= CONTACT_EPSILON;
                boolean highSecond = Math.abs(second - maxSecond) <= CONTACT_EPSILON;
                if (lowFirst == highFirst || lowSecond == highSecond) return false;
                int corner = 1 << ((highFirst ? 1 : 0) | (highSecond ? 2 : 0));
                if ((corners & corner) != 0) return false;
                corners |= corner;
            }
            return corners == 0b1111;
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

    /**
     * 实体某个轴对齐碰撞凸体的世界坐标范围。
     *
     * <p>凸体在模型空间轴对齐，离散朝向又只做 90 度旋转，所以它在世界坐标下同样轴对齐；
     * 于是「面朝向实体内部实心区域」这一判定退化为纯标量比较，不必逐面把来面变换回模型空间
     * 再把凸体投影回相机空间——那条路每个面每个凸体要走几十次 {@link Vec3} 运算。</p>
     */
    private record VolumeBox(
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
    ) {
        // 采样偏移和包容裕度沿用像素空间的原始取值，这里按每格 16 像素换算到方块单位。
        private static final double SAMPLE_OFFSET = CONTACT_EPSILON / PIXELS_PER_BLOCK;
        private static final double CONTAINMENT_EPSILON = 1.0E-5D / PIXELS_PER_BLOCK;

        private static VolumeBox of(UniversalPlasticEntity entity, MoldingConvexHull hull, float partialTick) {
            MoldingConvexHull.Bounds bounds = hull.bounds();
            Vec3 first = transformPoint(entity, toBlocks(bounds.minimum()), partialTick);
            Vec3 second = transformPoint(entity, toBlocks(bounds.maximum()), partialTick);
            return new VolumeBox(
                Math.min(first.x, second.x),
                Math.min(first.y, second.y),
                Math.min(first.z, second.z),
                Math.max(first.x, second.x),
                Math.max(first.y, second.y),
                Math.max(first.z, second.z)
            );
        }

        private static Vec3 toBlocks(MoldingVec3 point) {
            return new Vec3(
                point.x() / PIXELS_PER_BLOCK,
                point.y() / PIXELS_PER_BLOCK,
                point.z() / PIXELS_PER_BLOCK
            );
        }

        private double minimum(int axis) {
            return switch (axis) {
                case 0 -> this.minX;
                case 1 -> this.minY;
                default -> this.minZ;
            };
        }

        private double maximum(int axis) {
            return switch (axis) {
                case 0 -> this.maxX;
                case 1 -> this.maxY;
                default -> this.maxZ;
            };
        }

        /** 沿来面法线微移后仍落在凸体内，说明这一面朝向实心内部。 */
        private boolean facesInterior(int axis, double worldPlane, int normalDirection) {
            double sample = worldPlane + normalDirection * SAMPLE_OFFSET;
            return sample >= this.minimum(axis) - CONTAINMENT_EPSILON
                && sample <= this.maximum(axis) + CONTAINMENT_EPSILON;
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

    /**
     * 一个来面上的覆盖矩形集合。
     *
     * <p>覆盖判定和边界切分只用到四个标量，逐面新建矩形对象和装箱边界列表会让分配量随四边形数量
     * 线性膨胀，因此改用可复用的扁平数组。</p>
     */
    private static final class RectangleBuffer {
        private static final int STRIDE = 4;
        private double[] values = new double[STRIDE * 4];
        private int count;

        private void clear() {
            this.count = 0;
        }

        private boolean isEmpty() {
            return this.count == 0;
        }

        private void add(double minFirst, double maxFirst, double minSecond, double maxSecond) {
            if ((this.count + 1) * STRIDE > this.values.length) {
                this.values = Arrays.copyOf(this.values, this.values.length * 2);
            }
            int offset = this.count * STRIDE;
            this.values[offset] = minFirst;
            this.values[offset + 1] = maxFirst;
            this.values[offset + 2] = minSecond;
            this.values[offset + 3] = maxSecond;
            this.count++;
        }

        private boolean contains(double first, double second) {
            for (int index = 0; index < this.count; index++) {
                int offset = index * STRIDE;
                if (first > this.values[offset] + CONTACT_EPSILON
                    && first < this.values[offset + 1] - CONTACT_EPSILON
                    && second > this.values[offset + 2] + CONTACT_EPSILON
                    && second < this.values[offset + 3] - CONTACT_EPSILON) {
                    return true;
                }
            }
            return false;
        }

        /** 返回能容纳当前矩形数量对应边界个数的数组，必要时换成更大的一块。 */
        private double[] grow(double[] target) {
            int required = 2 + this.count * 2;
            return target.length >= required ? target : new double[required];
        }

        /** 把去重后的切分边界写入 target 并返回个数；顺序与矩形加入顺序无关。 */
        private int boundaries(double[] target, double minimum, double maximum, boolean first) {
            int size = addBoundary(target, 0, minimum);
            size = addBoundary(target, size, maximum);
            int offset = first ? 0 : 2;
            for (int index = 0; index < this.count; index++) {
                int base = index * STRIDE + offset;
                size = addBoundary(target, size, this.values[base]);
                size = addBoundary(target, size, this.values[base + 1]);
            }
            Arrays.sort(target, 0, size);
            return size;
        }

        private static int addBoundary(double[] target, int size, double value) {
            for (int index = 0; index < size; index++) {
                if (Math.abs(target[index] - value) <= CONTACT_EPSILON) return size;
            }
            target[size] = value;
            return size + 1;
        }
    }

    private static final class Vertex {
        private static final Vertex[] EMPTY = new Vertex[0];

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

        private static Vertex[] array(int size) {
            Vertex[] result = new Vertex[size];
            for (int index = 0; index < size; index++) result[index] = new Vertex();
            return result;
        }

        private static Vertex empty() {
            return new Vertex();
        }

        /** 恢复到刚写入位置、尚未写入任何顶点属性的状态。 */
        private Vertex reset(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.red = 255;
            this.green = 255;
            this.blue = 255;
            this.alpha = 255;
            this.u = 0.0F;
            this.v = 0.0F;
            this.overlay = 0;
            this.light = 0;
            this.normalX = 0.0F;
            this.normalY = 0.0F;
            this.normalZ = 0.0F;
            return this;
        }

        /** 清零全部分量，供加权累加求插值顶点。 */
        private void clear() {
            this.reset(0.0D, 0.0D, 0.0D);
            this.red = 0;
            this.green = 0;
            this.blue = 0;
            this.alpha = 0;
        }

        private void setReversedNormal(Vertex source) {
            this.x = source.x;
            this.y = source.y;
            this.z = source.z;
            this.red = source.red;
            this.green = source.green;
            this.blue = source.blue;
            this.alpha = source.alpha;
            this.u = source.u;
            this.v = source.v;
            this.overlay = source.overlay;
            this.light = source.light;
            this.normalX = -source.normalX;
            this.normalY = -source.normalY;
            this.normalZ = -source.normalZ;
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
