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
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** 将相同颜色透明塑料的真实接触区域从表面网格中裁掉，避免保留内部重叠面。 */
public final class TransparentPlasticFaceCullingVertexConsumer implements VertexConsumer {
    private static final double CONTACT_EPSILON = 1.0E-3D;
    private static final double PIXELS_PER_BLOCK = 16.0D;

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
    private final List<VolumeOccluder> volumes;
    private final Vec3 camera;
    private final boolean shadeBlockFaces;
    private final List<Vertex> quad = new ArrayList<>(4);
    private Vertex current;
    private boolean currentDoubleSided;

    private TransparentPlasticFaceCullingVertexConsumer(
        UniversalPlasticEntity entity,
        VertexConsumer delegate,
        OccluderBuckets occluders,
        List<VolumeOccluder> volumes,
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
        List<VolumeOccluder> volumes = new ArrayList<>(1);
        if (!self.hulls().isEmpty()) {
            volumes.add(new VolumeOccluder(entity, self.hulls(), camera, sourcePartialTick(entity, partialTick)));
        }
        addBlockOccluders(entity, partialTick, neighbours);
        for (UniversalPlasticEntity other : entity.level().getEntitiesOfClass(
            UniversalPlasticEntity.class,
            entity.getBoundingBox().inflate(CONTACT_EPSILON)
        )) {
            if (other == entity || !other.isAlive() || !matches(entity, other)) continue;
            CachedOccluders neighbour = cached(other, partialTick);
            neighbours.addAll(neighbour.shared());
            if (!neighbour.hulls().isEmpty()) {
                volumes.add(
                    new VolumeOccluder(other, neighbour.hulls(), camera, sourcePartialTick(other, partialTick))
                );
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
                data.collisionHulls()
                    .stream()
                    .map(VolumeOccluder.VolumeHull::new)
                    .filter(VolumeOccluder.VolumeHull::axisAligned)
                    .toList()
            );
        }
        OCCLUDER_CACHE.put(entity, result);
        return result;
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
        List<Vertex> vertices = new ArrayList<>(4);
        List<Vertex> reversed = new ArrayList<>(4);
        for (MoldingQuad quad : data.surfaceMesh()) {
            if (!includeDoubleSided && quad.doubleSided()) continue;
            vertices.clear();
            vertices.add(vertex(entity, quad.first(), quad.normal(), partialTick));
            vertices.add(vertex(entity, quad.second(), quad.normal(), partialTick));
            vertices.add(vertex(entity, quad.third(), quad.normal(), partialTick));
            vertices.add(vertex(entity, quad.fourth(), quad.normal(), partialTick));
            Face face = Face.create(vertices);
            if (face != null) occluders.add(PlanarOccluder.of(face));
            // 零厚度面两侧都参与遮挡。法线朝向仍交给 Face.create 判定，因为它在法线退化时会回退到
            // 顶点叉积，那种情况下反向面的朝向与正向面相同，不能简单取反。
            if (includeDoubleSided && quad.doubleSided()) {
                reversed.clear();
                for (Vertex vertex : vertices) reversed.add(vertex.reverseNormal());
                Face reverseFace = Face.create(reversed);
                if (reverseFace != null) occluders.add(PlanarOccluder.of(reverseFace));
            }
        }
        return List.copyOf(occluders);
    }

    private static Vertex vertex(
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

    private static double component(Vec3 vector, int axis) {
        return switch (axis) {
            case 0 -> vector.x;
            case 1 -> vector.y;
            default -> vector.z;
        };
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
        for (PlanarOccluder occluder : this.occluders.facing(face.planeAxis, face.normalDirection)) {
            Rectangle rectangle = this.coveredBy(face, occluder);
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

    /**
     * 判定来面被遮挡面覆盖的区域。
     *
     * <p>遮挡面存的是世界坐标而来面是相机坐标，因此用相机偏移换算比较值，
     * 而不是每帧把整套遮挡面平移到相机坐标系重建一遍。同轴与法线反向已由分桶保证。</p>
     */
    @Nullable
    private Rectangle coveredBy(Face face, PlanarOccluder occluder) {
        if (Math.abs(face.plane + component(this.camera, face.planeAxis) - occluder.plane()) > CONTACT_EPSILON) {
            return null;
        }
        double firstOffset = component(this.camera, face.firstAxis);
        double secondOffset = component(this.camera, face.secondAxis);
        double minFirst = Math.max(face.minFirst, occluder.minFirst() - firstOffset);
        double maxFirst = Math.min(face.maxFirst, occluder.maxFirst() - firstOffset);
        double minSecond = Math.max(face.minSecond, occluder.minSecond() - secondOffset);
        double maxSecond = Math.min(face.maxSecond, occluder.maxSecond() - secondOffset);
        return maxFirst - minFirst <= CONTACT_EPSILON || maxSecond - minSecond <= CONTACT_EPSILON
            ? null
            : new Rectangle(minFirst, maxFirst, minSecond, maxSecond);
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

    /** 单个实体缓存下来的世界坐标遮挡面与凸体。 */
    private record CachedOccluders(
        Vec3 renderPosition,
        PlasticEntityOrientation orientation,
        BlockState displayState,
        String shapeHash,
        List<PlanarOccluder> own,
        List<PlanarOccluder> shared,
        OccluderBuckets ownBuckets,
        List<VolumeOccluder.VolumeHull> hulls
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
            List<VolumeHull> hulls,
            Vec3 camera,
            float partialTick
        ) {
            this.entity = entity;
            this.hulls = hulls;
            this.camera = camera;
            this.partialTick = partialTick;
        }

        private List<Rectangle> coveredBy(Face face) {
            Vertex reference = face.vertices.getFirst();
            Vec3 localNormal = inverseRotate(
                new Vec3(reference.normalX, reference.normalY, reference.normalZ),
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

        private static double component(MoldingVec3 vector, int axis) {
            return switch (axis) {
                case 0 -> vector.x();
                case 1 -> vector.y();
                default -> vector.z();
            };
        }

        // 嵌套类一旦声明同名方法就会按名字隐藏外层重载，Vec3 版本必须在此处重新给出。
        private static double component(Vec3 vector, int axis) {
            return switch (axis) {
                case 0 -> vector.x;
                case 1 -> vector.y;
                default -> vector.z;
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
