package dev.anvilcraft.plasticraft.molding.product;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.plasticraft.api.texture.PlasticSurface;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureGenerator;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureInput;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureLayout;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import dev.anvilcraft.plasticraft.init.PlasticraftDataComponents;
import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.ManufacturedMoldingGeometry;
import dev.anvilcraft.plasticraft.molding.bake.MoldingConvexFace;
import dev.anvilcraft.plasticraft.molding.bake.MoldingConvexHull;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.bake.MoldingQuad;
import dev.anvilcraft.plasticraft.molding.bake.MoldingVolumeMask;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

/** 单一动态制品 ID 携带的版本化制造结果。 */
public record MoldedPlasticData(
    int formatVersion,
    String modelHash,
    MoldingVolumeMask volumeMask,
    List<MoldingQuad> surfaceMesh,
    FluidStack material,
    ResourceLocation finalType,
    int capacity,
    String name,
    MoldingVec3 rotationPivot,
    MoldingVec3 entityOrigin,
    PlasticEntityOrientation orientation,
    List<MoldingConvexHull> collisionHulls
) {
    public static final int CURRENT_FORMAT_VERSION = 2;
    public static final int MAX_SURFACE_QUADS = MoldedPlasticSurfaceAdapter.MAX_SURFACES;
    public static final int MAX_COLLISION_HULLS = EditableMoldingModel.MAX_ELEMENTS;
    private static final int MAX_TEXT_LENGTH = 64;
    private static final double PIXELS_PER_BLOCK = 16.0D;
    private static final Codec<List<MoldingQuad>> SURFACE_MESH_CODEC = MoldingQuad.CODEC.listOf()
        .validate(quads -> !quads.isEmpty() && quads.size() <= MAX_SURFACE_QUADS
            ? DataResult.success(quads)
            : DataResult.error(() -> "Invalid molded plastic surface count"));
    private static final Codec<PlasticEntityOrientation> ORIENTATION_CODEC = Codec.INT.xmap(
        PlasticEntityOrientation::unpack,
        orientation -> Byte.toUnsignedInt(orientation.pack())
    );
    private static final Codec<List<MoldingConvexHull>> COLLISION_HULLS_CODEC = MoldingConvexHull.CODEC.listOf()
        .validate(hulls -> hulls.size() <= MAX_COLLISION_HULLS
            ? DataResult.success(hulls)
            : DataResult.error(() -> "Invalid molded plastic collision hull count"));
    private static final Map<MoldedPlasticData, DerivedData> DERIVED_CACHE = new WeakHashMap<>();
    public static final Codec<MoldedPlasticData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("format_version").forGetter(MoldedPlasticData::formatVersion),
        Codec.STRING.fieldOf("model_hash").forGetter(MoldedPlasticData::modelHash),
        MoldingVolumeMask.CODEC.fieldOf("volume").forGetter(MoldedPlasticData::volumeMask),
        SURFACE_MESH_CODEC.fieldOf("surface_mesh").forGetter(MoldedPlasticData::surfaceMesh),
        FluidStack.CODEC.fieldOf("material").forGetter(MoldedPlasticData::material),
        ResourceLocation.CODEC.fieldOf("final_type").forGetter(MoldedPlasticData::finalType),
        Codec.INT.optionalFieldOf("capacity", 0).forGetter(MoldedPlasticData::capacity),
        Codec.STRING.fieldOf("name").forGetter(MoldedPlasticData::name),
        MoldingVec3.CODEC.fieldOf("rotation_pivot").forGetter(MoldedPlasticData::rotationPivot),
        MoldingVec3.CODEC.fieldOf("entity_origin").forGetter(MoldedPlasticData::entityOrigin),
        ORIENTATION_CODEC.fieldOf("orientation").forGetter(MoldedPlasticData::orientation),
        COLLISION_HULLS_CODEC.fieldOf("collision_hulls").forGetter(MoldedPlasticData::collisionHulls)
    ).apply(instance, MoldedPlasticData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldedPlasticData> STREAM_CODEC = StreamCodec.of(
        MoldedPlasticData::encode,
        MoldedPlasticData::decode
    );

    public MoldedPlasticData {
        if (formatVersion != CURRENT_FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported molded plastic format " + formatVersion);
        }
        if (modelHash.isBlank() || modelHash.length() > 128) {
            throw new IllegalArgumentException("Invalid molded plastic model hash");
        }
        volumeMask = volumeMask.copy();
        surfaceMesh = List.copyOf(surfaceMesh);
        if (surfaceMesh.isEmpty() || surfaceMesh.size() > MAX_SURFACE_QUADS) {
            throw new IllegalArgumentException("Invalid molded plastic surface count");
        }
        for (MoldingQuad quad : surfaceMesh) {
            requireWorkspacePoint(quad.first(), "surface vertex");
            requireWorkspacePoint(quad.second(), "surface vertex");
            requireWorkspacePoint(quad.third(), "surface vertex");
            requireWorkspacePoint(quad.fourth(), "surface vertex");
        }
        if (material.isEmpty()) throw new IllegalArgumentException("Molded plastic material must not be empty");
        material = material.copyWithAmount(1);
        Objects.requireNonNull(finalType, "finalType");
        if (capacity < 0) throw new IllegalArgumentException("Molded plastic capacity must not be negative");
        if (name.isBlank() || name.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Invalid molded plastic name");
        }
        requireWorkspacePoint(rotationPivot, "rotation pivot");
        requireWorkspacePoint(entityOrigin, "entity origin");
        Objects.requireNonNull(orientation, "orientation");
        collisionHulls = List.copyOf(collisionHulls);
        if (collisionHulls.size() > MAX_COLLISION_HULLS) {
            throw new IllegalArgumentException("Invalid molded plastic collision hull count");
        }
        for (MoldingConvexHull hull : collisionHulls) {
            for (MoldingVec3 vertex : hull.vertices()) requireWorkspacePoint(vertex, "collision vertex");
        }
        MoldedPlasticSurfaceAdapter.adapt(surfaceMesh);
    }

    public static MoldedPlasticData manufacture(
        EditableMoldingModel model,
        BakedMoldingModel baked,
        FluidStack material,
        int meltMillibuckets
    ) {
        if (meltMillibuckets < 0) throw new IllegalArgumentException("Melt amount must not be negative");
        int maximumCells = Math.min(
            MoldingVolumeMask.CELL_COUNT,
            Math.multiplyExact(meltMillibuckets, 4)
        );
        MoldingVolumeMask paidVolume = MoldingModelBaker.createPaidVolumeMask(baked, maximumCells);
        int requiredCells = baked.volumeMask().volume();
        double formedProportion = requiredCells == 0
            ? 1.0D
            : Math.min(1.0D, paidVolume.volume() / (double) requiredCells);
        ManufacturedMoldingGeometry geometry = MoldingModelBaker.createManufacturedGeometry(
            model,
            formedProportion
        );
        List<MoldingQuad> surface = geometry.surfaceMesh();
        return new MoldedPlasticData(
            CURRENT_FORMAT_VERSION,
            baked.modelHash(),
            paidVolume,
            surface,
            material,
            EditableMoldingModel.NORMAL_TYPE,
            0,
            model.name(),
            defaultRotationPivot(surface),
            new MoldingVec3(24.0D, 0.0D, 24.0D),
            PlasticEntityOrientation.DEFAULT,
            geometry.collisionHulls()
        );
    }

    public MoldedPlasticData withOrientation(PlasticEntityOrientation replacement) {
        if (this.orientation.equals(replacement)) return this;
        return new MoldedPlasticData(
            this.formatVersion,
            this.modelHash,
            this.volumeMask,
            this.surfaceMesh,
            this.material,
            this.finalType,
            this.capacity,
            this.name,
            this.rotationPivot,
            this.entityOrigin,
            replacement,
            this.collisionHulls
        );
    }

    @Override
    public MoldingVolumeMask volumeMask() {
        return this.volumeMask.copy();
    }

    @Override
    public FluidStack material() {
        return this.material.copy();
    }

    public List<MoldingQuad> zeroThicknessQuads() {
        return zeroThicknessQuads(this.surfaceMesh);
    }

    public List<PlasticSurface> plasticSurfaces() {
        return this.derivedData().plasticSurfaces;
    }

    public String shapeHash() {
        return this.derivedData().shapeHash;
    }

    public PlasticTextureLayout textureLayout() {
        return this.derivedData().textureLayout;
    }

    public PlasticEntityGeometry geometry() {
        return MoldedPlasticGeometry.get(this);
    }

    /** 返回物品渲染与尺寸提示共用的精确可见表面范围，单位为格。 */
    public AABB surfaceBounds() {
        return this.derivedData().surfaceBounds;
    }

    public static Optional<MoldedPlasticData> get(ItemStack stack) {
        return Optional.ofNullable(stack.get(PlasticraftDataComponents.MOLDED_PLASTIC.get()));
    }

    public static void set(ItemStack stack, MoldedPlasticData data) {
        MoldedPlasticData normalized = data.orientation.equals(PlasticEntityOrientation.DEFAULT)
            ? data
            : data.withOrientation(PlasticEntityOrientation.DEFAULT);
        stack.set(PlasticraftDataComponents.MOLDED_PLASTIC.get(), normalized);
    }

    private DerivedData derivedData() {
        synchronized (DERIVED_CACHE) {
            return DERIVED_CACHE.computeIfAbsent(this, DerivedData::create);
        }
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MoldedPlasticData data) {
        buffer.writeVarInt(data.formatVersion);
        buffer.writeUtf(data.modelHash, 128);
        long[] cells = data.volumeMask.toLongArray();
        buffer.writeVarInt(cells.length);
        for (long cell : cells) buffer.writeLong(cell);
        buffer.writeVarInt(data.surfaceMesh.size());
        for (MoldingQuad quad : data.surfaceMesh) writeQuad(buffer, quad);
        FluidStack.STREAM_CODEC.encode(buffer, data.material);
        buffer.writeResourceLocation(data.finalType);
        buffer.writeVarInt(data.capacity);
        buffer.writeUtf(data.name, MAX_TEXT_LENGTH);
        writeVector(buffer, data.rotationPivot);
        writeVector(buffer, data.entityOrigin);
        buffer.writeByte(data.orientation.pack());
        buffer.writeVarInt(data.collisionHulls.size());
        for (MoldingConvexHull hull : data.collisionHulls) writeHull(buffer, hull);
    }

    private static MoldedPlasticData decode(RegistryFriendlyByteBuf buffer) {
        int formatVersion = buffer.readVarInt();
        String modelHash = buffer.readUtf(128);
        int longCount = readBoundedCount(buffer, MoldingVolumeMask.MAX_LONG_COUNT, "molding volume longs");
        long[] cells = new long[longCount];
        for (int index = 0; index < longCount; index++) cells[index] = buffer.readLong();
        int quadCount = readBoundedCount(buffer, MAX_SURFACE_QUADS, "molded plastic surfaces");
        List<MoldingQuad> quads = new ArrayList<>(quadCount);
        for (int index = 0; index < quadCount; index++) quads.add(readQuad(buffer));
        FluidStack material = FluidStack.STREAM_CODEC.decode(buffer);
        ResourceLocation finalType = buffer.readResourceLocation();
        int capacity = buffer.readVarInt();
        String name = buffer.readUtf(MAX_TEXT_LENGTH);
        MoldingVec3 rotationPivot = readVector(buffer);
        MoldingVec3 entityOrigin = readVector(buffer);
        PlasticEntityOrientation orientation = PlasticEntityOrientation.unpack(buffer.readUnsignedByte());
        int hullCount = readBoundedCount(buffer, MAX_COLLISION_HULLS, "molded plastic collision hulls");
        List<MoldingConvexHull> collisionHulls = new ArrayList<>(hullCount);
        for (int index = 0; index < hullCount; index++) collisionHulls.add(readHull(buffer));
        return new MoldedPlasticData(
            formatVersion,
            modelHash,
            MoldingVolumeMask.fromLongArray(cells),
            quads,
            material,
            finalType,
            capacity,
            name,
            rotationPivot,
            entityOrigin,
            orientation,
            collisionHulls
        );
    }

    private static int readBoundedCount(RegistryFriendlyByteBuf buffer, int maximum, String name) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid " + name + " count");
        return count;
    }

    private static void writeQuad(RegistryFriendlyByteBuf buffer, MoldingQuad quad) {
        writeVector(buffer, quad.first());
        writeVector(buffer, quad.second());
        writeVector(buffer, quad.third());
        writeVector(buffer, quad.fourth());
        writeVector(buffer, quad.normal());
        buffer.writeBoolean(quad.doubleSided());
    }

    private static MoldingQuad readQuad(RegistryFriendlyByteBuf buffer) {
        return new MoldingQuad(
            readVector(buffer),
            readVector(buffer),
            readVector(buffer),
            readVector(buffer),
            readVector(buffer),
            buffer.readBoolean()
        );
    }

    private static void writeHull(RegistryFriendlyByteBuf buffer, MoldingConvexHull hull) {
        buffer.writeVarInt(hull.vertices().size());
        for (MoldingVec3 vertex : hull.vertices()) writeVector(buffer, vertex);
        buffer.writeVarInt(hull.faces().size());
        for (MoldingConvexFace face : hull.faces()) {
            buffer.writeVarInt(face.vertices().size());
            for (int vertex : face.vertices()) buffer.writeVarInt(vertex);
            writeVector(buffer, face.normal());
        }
    }

    private static MoldingConvexHull readHull(RegistryFriendlyByteBuf buffer) {
        int vertexCount = readBoundedCount(
            buffer,
            MoldingConvexHull.MAX_VERTICES,
            "molded plastic collision vertices"
        );
        if (vertexCount < 4) throw new IllegalArgumentException("Invalid molded plastic collision vertex count");
        List<MoldingVec3> vertices = new ArrayList<>(vertexCount);
        for (int index = 0; index < vertexCount; index++) vertices.add(readVector(buffer));
        int faceCount = readBoundedCount(buffer, MoldingConvexHull.MAX_FACES, "molded plastic collision faces");
        if (faceCount < 4) throw new IllegalArgumentException("Invalid molded plastic collision face count");
        List<MoldingConvexFace> faces = new ArrayList<>(faceCount);
        for (int index = 0; index < faceCount; index++) {
            int faceVertexCount = readBoundedCount(
                buffer,
                MoldingConvexFace.MAX_VERTICES,
                "molded plastic collision face vertices"
            );
            if (faceVertexCount < 3) {
                throw new IllegalArgumentException("Invalid molded plastic collision face vertex count");
            }
            List<Integer> faceVertices = new ArrayList<>(faceVertexCount);
            for (int vertex = 0; vertex < faceVertexCount; vertex++) {
                faceVertices.add(buffer.readVarInt());
            }
            faces.add(new MoldingConvexFace(faceVertices, readVector(buffer)));
        }
        return new MoldingConvexHull(vertices, faces);
    }

    private static void writeVector(RegistryFriendlyByteBuf buffer, MoldingVec3 vector) {
        buffer.writeDouble(vector.x());
        buffer.writeDouble(vector.y());
        buffer.writeDouble(vector.z());
    }

    private static MoldingVec3 readVector(RegistryFriendlyByteBuf buffer) {
        return new MoldingVec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    private static MoldingVec3 defaultRotationPivot(List<MoldingQuad> surfaces) {
        MoldingVec3 minimum = surfaces.getFirst().first();
        MoldingVec3 maximum = minimum;
        for (MoldingQuad surface : surfaces) {
            minimum = minimum.min(surface.first()).min(surface.second()).min(surface.third()).min(surface.fourth());
            maximum = maximum.max(surface.first()).max(surface.second()).max(surface.third()).max(surface.fourth());
        }
        return minimum.add(maximum).scale(0.5D);
    }

    private static List<MoldingQuad> zeroThicknessQuads(List<MoldingQuad> surfaces) {
        return surfaces.stream().filter(MoldingQuad::doubleSided).toList();
    }

    private static void requireWorkspacePoint(MoldingVec3 point, String name) {
        Objects.requireNonNull(point, name);
        if (point.x() < 0.0D || point.x() > MoldingVolumeMask.SIZE
            || point.y() < 0.0D || point.y() > MoldingVolumeMask.SIZE
            || point.z() < 0.0D || point.z() > MoldingVolumeMask.SIZE) {
            throw new IllegalArgumentException("Molded plastic " + name + " is outside the workspace");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MoldedPlasticData data)) return false;
        return this.formatVersion == data.formatVersion
            && this.capacity == data.capacity
            && this.modelHash.equals(data.modelHash)
            && this.volumeMask.equals(data.volumeMask)
            && this.surfaceMesh.equals(data.surfaceMesh)
            && FluidStack.matches(this.material, data.material)
            && this.finalType.equals(data.finalType)
            && this.name.equals(data.name)
            && this.rotationPivot.equals(data.rotationPivot)
            && this.entityOrigin.equals(data.entityOrigin)
            && this.orientation.equals(data.orientation)
            && this.collisionHulls.equals(data.collisionHulls);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
            this.formatVersion,
            this.modelHash,
            this.volumeMask,
            this.surfaceMesh,
            this.finalType,
            this.capacity,
            this.name,
            this.rotationPivot,
            this.entityOrigin,
            this.orientation,
            this.collisionHulls
        );
        result = 31 * result + FluidStack.hashFluidAndComponents(this.material);
        result = 31 * result + this.material.getAmount();
        return result;
    }

    private record DerivedData(
        List<PlasticSurface> plasticSurfaces,
        PlasticTextureLayout textureLayout,
        String shapeHash,
        AABB surfaceBounds
    ) {
        private static DerivedData create(MoldedPlasticData data) {
            List<PlasticSurface> surfaces = MoldedPlasticSurfaceAdapter.adapt(data.surfaceMesh);
            return new DerivedData(
                surfaces,
                PlasticTextureGenerator.layout(surfaces),
                PlasticTextureInput.computeShapeHash(surfaces),
                createSurfaceBounds(data.surfaceMesh)
            );
        }

        private static AABB createSurfaceBounds(List<MoldingQuad> surfaces) {
            MoldingQuad firstSurface = surfaces.getFirst();
            MoldingVec3 minimum = firstSurface.first();
            MoldingVec3 maximum = minimum;
            for (MoldingQuad surface : surfaces) {
                minimum = minimum.min(surface.first())
                    .min(surface.second())
                    .min(surface.third())
                    .min(surface.fourth());
                maximum = maximum.max(surface.first())
                    .max(surface.second())
                    .max(surface.third())
                    .max(surface.fourth());
            }
            return new AABB(
                minimum.x() / PIXELS_PER_BLOCK,
                minimum.y() / PIXELS_PER_BLOCK,
                minimum.z() / PIXELS_PER_BLOCK,
                maximum.x() / PIXELS_PER_BLOCK,
                maximum.y() / PIXELS_PER_BLOCK,
                maximum.z() / PIXELS_PER_BLOCK
            );
        }
    }
}
