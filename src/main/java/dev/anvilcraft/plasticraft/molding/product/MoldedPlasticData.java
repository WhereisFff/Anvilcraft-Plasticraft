package dev.anvilcraft.plasticraft.molding.product;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.plasticraft.api.texture.PlasticSurface;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureInput;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureLayout;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import dev.anvilcraft.plasticraft.init.PlasticraftDataComponents;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.ManufacturedMoldingGeometry;
import dev.anvilcraft.plasticraft.molding.bake.MoldingBarrierFace;
import dev.anvilcraft.plasticraft.molding.bake.MoldingAnvilShapeAnalyzer;
import dev.anvilcraft.plasticraft.molding.bake.MoldingConvexFace;
import dev.anvilcraft.plasticraft.molding.bake.MoldingConvexHull;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.bake.MoldingQuad;
import dev.anvilcraft.plasticraft.molding.bake.MoldingTrayShapeAnalysis;
import dev.anvilcraft.plasticraft.molding.bake.MoldingTrayShapeAnalyzer;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductPreview;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductType;
import dev.anvilcraft.plasticraft.molding.bake.MoldingVolumeMask;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 单一动态制品 ID 携带的版本化制造结果。 */
public record MoldedPlasticData(
    int formatVersion,
    String modelHash,
    MoldingVolumeMask volumeMask,
    MoldingVolumeMask cavityMask,
    List<MoldingQuad> surfaceMesh,
    FluidStack material,
    ResourceLocation finalType,
    int capacity,
    MoldedPlasticContents contents,
    String name,
    MoldingVec3 rotationPivot,
    MoldingVec3 entityOrigin,
    PlasticEntityOrientation orientation,
    List<MoldingConvexHull> collisionHulls,
    boolean limitOverride,
    Optional<UUID> storageId,
    MoldedPlasticContentSummary summary
) {
    public static final int CURRENT_FORMAT_VERSION = 6;
    private static final int SINGLE_TRAY_COMPONENT_FORMAT_VERSION = 4;
    public static final int MAX_SURFACE_QUADS = MoldedPlasticSurfaceAdapter.MAX_SURFACES;
    public static final int MAX_COLLISION_HULLS = EditableMoldingModel.MAX_ELEMENTS;
    private static final int DERIVED_CACHE_LIMIT = 128;
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
    // 格式 4–5 的外形与托盘可定向迁移；物品/流体改走仓储 UUID，网络流只接受当前格式。
    private static final Codec<Integer> FORMAT_VERSION_CODEC = Codec.intRange(
        SINGLE_TRAY_COMPONENT_FORMAT_VERSION,
        CURRENT_FORMAT_VERSION
    ).xmap(ignored -> CURRENT_FORMAT_VERSION, version -> version);
    private static final Codec<List<MoldingConvexHull>> COLLISION_HULLS_CODEC = MoldingConvexHull.CODEC.listOf()
        .validate(hulls -> hulls.size() <= MAX_COLLISION_HULLS
            ? DataResult.success(hulls)
            : DataResult.error(() -> "Invalid molded plastic collision hull count"));
    private static final Map<ShapeKey, DerivedData> DERIVED_CACHE = new LinkedHashMap<>(
        DERIVED_CACHE_LIMIT,
        0.75F,
        true
    ) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ShapeKey, DerivedData> eldest) {
            return this.size() > DERIVED_CACHE_LIMIT;
        }
    };
    private static final Map<ShapeKey, MoldingTrayShapeAnalysis> TRAY_SHAPE_CACHE = new LinkedHashMap<>(
        DERIVED_CACHE_LIMIT,
        0.75F,
        true
    ) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ShapeKey, MoldingTrayShapeAnalysis> eldest) {
            return this.size() > DERIVED_CACHE_LIMIT;
        }
    };
    public static final Codec<MoldedPlasticData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        FORMAT_VERSION_CODEC.fieldOf("format_version").forGetter(MoldedPlasticData::formatVersion),
        Codec.STRING.fieldOf("model_hash").forGetter(MoldedPlasticData::modelHash),
        MoldingVolumeMask.CODEC.fieldOf("volume").forGetter(MoldedPlasticData::volumeMask),
        MoldingVolumeMask.CODEC.fieldOf("cavities").forGetter(MoldedPlasticData::cavityMask),
        SURFACE_MESH_CODEC.fieldOf("surface_mesh").forGetter(MoldedPlasticData::surfaceMesh),
        FluidStack.CODEC.fieldOf("material").forGetter(MoldedPlasticData::material),
        ResourceLocation.CODEC.fieldOf("final_type").forGetter(MoldedPlasticData::finalType),
        Codec.INT.optionalFieldOf("capacity", 0).forGetter(MoldedPlasticData::capacity),
        MoldedPlasticContents.CODEC.optionalFieldOf("contents", MoldedPlasticContents.EMPTY)
            .forGetter(MoldedPlasticData::contents),
        Codec.STRING.fieldOf("name").forGetter(MoldedPlasticData::name),
        MoldingVec3.CODEC.fieldOf("rotation_pivot").forGetter(MoldedPlasticData::rotationPivot),
        MoldingVec3.CODEC.fieldOf("entity_origin").forGetter(MoldedPlasticData::entityOrigin),
        ORIENTATION_CODEC.fieldOf("orientation").forGetter(MoldedPlasticData::orientation),
        COLLISION_HULLS_CODEC.fieldOf("collision_hulls").forGetter(MoldedPlasticData::collisionHulls),
        Codec.BOOL.fieldOf("limit_override").forGetter(MoldedPlasticData::limitOverride),
        StorageFields.MAP_CODEC.forGetter(StorageFields::from)
    ).apply(instance, (
        formatVersion,
        modelHash,
        volumeMask,
        cavityMask,
        surfaceMesh,
        material,
        finalType,
        capacity,
        contents,
        name,
        rotationPivot,
        entityOrigin,
        orientation,
        collisionHulls,
        limitOverride,
        storage
    ) -> new MoldedPlasticData(
        formatVersion,
        modelHash,
        volumeMask,
        cavityMask,
        surfaceMesh,
        material,
        finalType,
        capacity,
        contents,
        name,
        rotationPivot,
        entityOrigin,
        orientation,
        collisionHulls,
        limitOverride,
        storage.storageId(),
        storage.summary()
    )));
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
        cavityMask = cavityMask.copy();
        if (volumeMask.sizeX() != cavityMask.sizeX()
            || volumeMask.sizeY() != cavityMask.sizeY()
            || volumeMask.sizeZ() != cavityMask.sizeZ()) {
            throw new IllegalArgumentException("Molded plastic masks must share dimensions");
        }
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
        Objects.requireNonNull(contents, "contents");
        storageId = Objects.requireNonNull(storageId, "storageId");
        summary = Objects.requireNonNull(summary, "summary");
        MoldingProductType productType = MoldingProductTypes.get(finalType)
            .orElseThrow(() -> new IllegalArgumentException("Unknown molded plastic product type " + finalType));
        if (capacity < 0) throw new IllegalArgumentException("Molded plastic capacity must not be negative");
        int unitsPerCapacity = productType.unitsPerCapacity();
        if (unitsPerCapacity > 0
            && capacity != cavityMask.volume() / unitsPerCapacity) {
            throw new IllegalArgumentException("Molded plastic capacity does not match its cavity volume");
        }
        switch (productType.storageKind()) {
            case NONE -> {
                if (capacity != 0 || !contents.isEmpty() || storageId.isPresent()) {
                    throw new IllegalArgumentException("Normal molded plastic cannot carry storage contents");
                }
            }
            case ITEMS -> {
                if (!contents.fluids().isEmpty() || !contents.trayComponents().isEmpty()) {
                    throw new IllegalArgumentException("Molded chest cannot carry fluids");
                }
                if (contents.items().stream().anyMatch(item -> item.slot() >= capacity)) {
                    throw new IllegalArgumentException("Molded chest item slot exceeds its capacity");
                }
            }
            case FLUIDS -> {
                if (!contents.items().isEmpty() || !contents.trayComponents().isEmpty()) {
                    throw new IllegalArgumentException("Molded tank cannot carry items");
                }
                long fluidAmount = contents.fluids().stream()
                    .mapToLong(FluidStack::getAmount)
                    .sum();
                if (fluidAmount > (long) capacity * 1000L) {
                    throw new IllegalArgumentException("Molded tank fluids exceed its capacity");
                }
            }
            case TRAY -> {
                if (capacity != 0 || !contents.items().isEmpty() || !contents.fluids().isEmpty()
                    || storageId.isPresent()) {
                    throw new IllegalArgumentException("Molded tray can only carry its redstone component");
                }
                int supportedCells = MoldingTrayShapeAnalyzer.supportedCellMask(volumeMask);
                if (contents.trayComponents().stream()
                    .map(MoldedTrayComponentPlacement::cell)
                    .anyMatch(cell -> (supportedCells & cell.bit()) == 0)) {
                    throw new IllegalArgumentException("Molded tray component cell has no plastic support");
                }
            }
        }
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
        derivedData(
            new ShapeKey(modelHash, volumeMask.volume(), volumeMask.sizeX(), volumeMask.sizeY(), volumeMask.sizeZ()),
            surfaceMesh
        );
    }

    public MoldedPlasticData(
        int formatVersion,
        String modelHash,
        MoldingVolumeMask volumeMask,
        MoldingVolumeMask cavityMask,
        List<MoldingQuad> surfaceMesh,
        FluidStack material,
        ResourceLocation finalType,
        int capacity,
        MoldedPlasticContents contents,
        String name,
        MoldingVec3 rotationPivot,
        MoldingVec3 entityOrigin,
        PlasticEntityOrientation orientation,
        List<MoldingConvexHull> collisionHulls,
        boolean limitOverride
    ) {
        this(
            formatVersion,
            modelHash,
            volumeMask,
            cavityMask,
            surfaceMesh,
            material,
            finalType,
            capacity,
            contents,
            name,
            rotationPivot,
            entityOrigin,
            orientation,
            collisionHulls,
            limitOverride,
            Optional.empty(),
            MoldedPlasticContentSummary.EMPTY
        );
    }

    public static MoldedPlasticData manufacture(
        EditableMoldingModel model,
        BakedMoldingModel baked,
        FluidStack material,
        int meltMillibuckets
    ) {
        return manufacture(model, baked, material, meltMillibuckets, false, false);
    }

    public static MoldedPlasticData manufacture(
        EditableMoldingModel model,
        BakedMoldingModel baked,
        FluidStack material,
        int meltMillibuckets,
        boolean typeOverride,
        boolean creativeOverride
    ) {
        MoldingProductPreview preview = MoldingProductPreview.evaluate(
            model,
            baked,
            meltMillibuckets,
            typeOverride,
            creativeOverride
        );
        MoldingVolumeMask paidVolume = preview.formedVolume();
        int requiredCells = baked.analysis().volume();
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
            preview.analysis().cavityMask(),
            surface,
            material.isEmpty()
                ? new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), 1)
                : material,
            preview.finalType(),
            preview.capacity(),
            MoldedPlasticContents.EMPTY,
            model.name(),
            defaultRotationPivot(surface),
            entityOrigin(paidVolume),
            PlasticEntityOrientation.DEFAULT,
            geometry.collisionHulls(),
            typeOverride || creativeOverride
        );
    }

    private static MoldingVec3 entityOrigin(MoldingVolumeMask volume) {
        return new MoldingVec3(
            volume.sizeX() == MoldingVolumeMask.SIZE ? 24.0D : volume.sizeX() * 0.5D,
            0.0D,
            volume.sizeZ() == MoldingVolumeMask.SIZE ? 24.0D : volume.sizeZ() * 0.5D
        );
    }

    public MoldedPlasticData withOrientation(PlasticEntityOrientation replacement) {
        if (this.orientation.equals(replacement)) return this;
        return new MoldedPlasticData(
            this.formatVersion,
            this.modelHash,
            this.volumeMask,
            this.cavityMask,
            this.surfaceMesh,
            this.material,
            this.finalType,
            this.capacity,
            this.contents,
            this.name,
            this.rotationPivot,
            this.entityOrigin,
            replacement,
            this.collisionHulls,
            this.limitOverride,
            this.storageId,
            this.summary
        );
    }

    @Override
    public MoldingVolumeMask volumeMask() {
        return this.volumeMask.copy();
    }

    public boolean hasGiantAnvilAbility() {
        return MoldingProductTypes.isAnvil(this.finalType)
            && MoldingAnvilShapeAnalyzer.hasGiantBottom(this.volumeMask);
    }

    @Override
    public MoldingVolumeMask cavityMask() {
        return this.cavityMask.copy();
    }

    public MoldedPlasticData withFunction(
        ResourceLocation type,
        int replacementCapacity,
        MoldingVolumeMask cavities
    ) {
        return new MoldedPlasticData(
            this.formatVersion,
            this.modelHash,
            this.volumeMask,
            cavities,
            this.surfaceMesh,
            this.material,
            type,
            replacementCapacity,
            MoldedPlasticContents.EMPTY,
            this.name,
            this.rotationPivot,
            this.entityOrigin,
            this.orientation,
            this.collisionHulls,
            this.limitOverride,
            Optional.empty(),
            MoldedPlasticContentSummary.EMPTY
        );
    }

    public MoldedPlasticData withContents(MoldedPlasticContents replacement) {
        if (this.contents.equals(replacement)) return this;
        return new MoldedPlasticData(
            this.formatVersion,
            this.modelHash,
            this.volumeMask,
            this.cavityMask,
            this.surfaceMesh,
            this.material,
            this.finalType,
            this.capacity,
            replacement,
            this.name,
            this.rotationPivot,
            this.entityOrigin,
            this.orientation,
            this.collisionHulls,
            this.limitOverride,
            this.storageId,
            this.summary
        );
    }

    public MoldedPlasticData withStorageId(Optional<UUID> replacement) {
        Optional<UUID> id = Objects.requireNonNull(replacement, "replacement");
        if (this.storageId.equals(id)) return this;
        return new MoldedPlasticData(
            this.formatVersion,
            this.modelHash,
            this.volumeMask,
            this.cavityMask,
            this.surfaceMesh,
            this.material,
            this.finalType,
            this.capacity,
            this.contents,
            this.name,
            this.rotationPivot,
            this.entityOrigin,
            this.orientation,
            this.collisionHulls,
            this.limitOverride,
            id,
            this.summary
        );
    }

    public MoldedPlasticData withSummary(MoldedPlasticContentSummary replacement) {
        MoldedPlasticContentSummary value = Objects.requireNonNull(replacement, "replacement");
        if (this.summary.equals(value)) return this;
        return new MoldedPlasticData(
            this.formatVersion,
            this.modelHash,
            this.volumeMask,
            this.cavityMask,
            this.surfaceMesh,
            this.material,
            this.finalType,
            this.capacity,
            this.contents,
            this.name,
            this.rotationPivot,
            this.entityOrigin,
            this.orientation,
            this.collisionHulls,
            this.limitOverride,
            this.storageId,
            value
        );
    }

    public boolean hasStoredContents() {
        return this.storageId.isPresent() || this.contents.hasInlineStorage() || !this.summary.isVacant();
    }

    public MoldedPlasticData withMaterial(FluidStack replacement) {
        return new MoldedPlasticData(
            this.formatVersion,
            this.modelHash,
            this.volumeMask,
            this.cavityMask,
            this.surfaceMesh,
            replacement,
            this.finalType,
            this.capacity,
            this.contents,
            this.name,
            this.rotationPivot,
            this.entityOrigin,
            this.orientation,
            this.collisionHulls,
            this.limitOverride,
            this.storageId,
            this.summary
        );
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

    public MoldingTrayShapeAnalysis trayShapeAnalysis() {
        ShapeKey key = this.shapeKey();
        synchronized (TRAY_SHAPE_CACHE) {
            return TRAY_SHAPE_CACHE.computeIfAbsent(
                key,
                ignored -> MoldingTrayShapeAnalyzer.analyze(this.volumeMask, this.surfaceMesh)
            );
        }
    }

    public Set<MoldingBarrierFace> barrierFaces() {
        return this.derivedData().barrierFaces;
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
        return derivedData(this.shapeKey(), this.surfaceMesh);
    }

    private static DerivedData derivedData(ShapeKey key, List<MoldingQuad> surfaceMesh) {
        synchronized (DERIVED_CACHE) {
            return DERIVED_CACHE.computeIfAbsent(key, ignored -> DerivedData.create(surfaceMesh));
        }
    }

    ShapeKey shapeKey() {
        return new ShapeKey(
            this.modelHash,
            this.volumeMask.volume(),
            this.volumeMask.sizeX(),
            this.volumeMask.sizeY(),
            this.volumeMask.sizeZ()
        );
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MoldedPlasticData data) {
        buffer.writeVarInt(data.formatVersion);
        buffer.writeUtf(data.modelHash, 128);
        writeMask(buffer, data.volumeMask);
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
        writeMask(buffer, data.cavityMask);
        MoldedPlasticContents.encode(buffer, data.contents);
        buffer.writeBoolean(data.limitOverride);
        ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).encode(buffer, data.storageId);
        MoldedPlasticContentSummary.STREAM_CODEC.encode(buffer, data.summary);
    }

    private static MoldedPlasticData decode(RegistryFriendlyByteBuf buffer) {
        int formatVersion = buffer.readVarInt();
        String modelHash = buffer.readUtf(128);
        MoldingVolumeMask volume = readMask(buffer, "molding volume");
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
        MoldingVolumeMask cavity = readMask(buffer, "molding cavity");
        MoldedPlasticContents contents = MoldedPlasticContents.decode(buffer);
        boolean limitOverride = buffer.readBoolean();
        Optional<UUID> storageId = ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buffer);
        MoldedPlasticContentSummary summary = MoldedPlasticContentSummary.STREAM_CODEC.decode(buffer);
        return new MoldedPlasticData(
            formatVersion,
            modelHash,
            volume,
            cavity,
            quads,
            material,
            finalType,
            capacity,
            contents,
            name,
            rotationPivot,
            entityOrigin,
            orientation,
            collisionHulls,
            limitOverride,
            storageId,
            summary
        );
    }

    private static int readBoundedCount(RegistryFriendlyByteBuf buffer, int maximum, String name) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid " + name + " count");
        return count;
    }

    private static void writeMask(RegistryFriendlyByteBuf buffer, MoldingVolumeMask mask) {
        buffer.writeVarInt(mask.sizeX());
        buffer.writeVarInt(mask.sizeY());
        buffer.writeVarInt(mask.sizeZ());
        long[] cells = mask.toLongArray();
        buffer.writeVarInt(cells.length);
        for (long cell : cells) buffer.writeLong(cell);
    }

    private static MoldingVolumeMask readMask(RegistryFriendlyByteBuf buffer, String name) {
        int sizeX = buffer.readVarInt();
        int sizeY = buffer.readVarInt();
        int sizeZ = buffer.readVarInt();
        int longCount = readBoundedCount(
            buffer,
            MoldingVolumeMask.maxLongCount(sizeX, sizeY, sizeZ),
            name + " longs"
        );
        long[] cells = new long[longCount];
        for (int index = 0; index < longCount; index++) cells[index] = buffer.readLong();
        return MoldingVolumeMask.fromLongArray(sizeX, sizeY, sizeZ, cells);
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
        if (point.x() < 0.0D || point.x() > MoldingVolumeMask.MAX_SIZE
            || point.y() < 0.0D || point.y() > MoldingVolumeMask.MAX_SIZE
            || point.z() < 0.0D || point.z() > MoldingVolumeMask.MAX_SIZE) {
            throw new IllegalArgumentException("Molded plastic " + name + " is outside the workspace");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MoldedPlasticData data)) return false;
        return this.formatVersion == data.formatVersion
            && this.capacity == data.capacity
            && this.limitOverride == data.limitOverride
            && this.modelHash.equals(data.modelHash)
            && this.volumeMask.equals(data.volumeMask)
            && this.cavityMask.equals(data.cavityMask)
            && this.surfaceMesh.equals(data.surfaceMesh)
            && FluidStack.matches(this.material, data.material)
            && this.finalType.equals(data.finalType)
            && this.contents.equals(data.contents)
            && this.storageId.equals(data.storageId)
            && this.summary.equals(data.summary)
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
            this.cavityMask,
            this.surfaceMesh,
            this.finalType,
            this.capacity,
            this.contents,
            this.storageId,
            this.summary,
            this.name,
            this.rotationPivot,
            this.entityOrigin,
            this.orientation,
            this.collisionHulls,
            this.limitOverride
        );
        result = 31 * result + FluidStack.hashFluidAndComponents(this.material);
        result = 31 * result + this.material.getAmount();
        return result;
    }

    private record DerivedData(
        List<PlasticSurface> plasticSurfaces,
        PlasticTextureLayout textureLayout,
        String shapeHash,
        AABB surfaceBounds,
        Set<MoldingBarrierFace> barrierFaces
    ) {
        private static DerivedData create(List<MoldingQuad> surfaceMesh) {
            MoldedPlasticSurfaceAdapter.AdaptedSurfaces adapted =
                MoldedPlasticSurfaceAdapter.adaptWithLayout(surfaceMesh);
            List<PlasticSurface> surfaces = adapted.surfaces();
            return new DerivedData(
                surfaces,
                adapted.textureLayout(),
                PlasticTextureInput.computeShapeHash(surfaces),
                createSurfaceBounds(surfaceMesh),
                MoldingModelBaker.barrierFacesFromZeroThickness(
                    surfaceMesh.stream().filter(MoldingQuad::doubleSided).toList()
                )
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

    record ShapeKey(String modelHash, int formedCells, int sizeX, int sizeY, int sizeZ) {
    }

    /** 把仓储 UUID 与摘要编进同一层 NBT，避免 RecordCodecBuilder 超过 16 个字段。 */
    private record StorageFields(Optional<UUID> storageId, MoldedPlasticContentSummary summary) {
        private static final MapCodec<StorageFields> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("storage_id").forGetter(StorageFields::storageId),
            MoldedPlasticContentSummary.CODEC.optionalFieldOf("summary", MoldedPlasticContentSummary.EMPTY)
                .forGetter(StorageFields::summary)
        ).apply(instance, StorageFields::new));

        private static StorageFields from(MoldedPlasticData data) {
            return new StorageFields(data.storageId(), data.summary());
        }
    }
}
