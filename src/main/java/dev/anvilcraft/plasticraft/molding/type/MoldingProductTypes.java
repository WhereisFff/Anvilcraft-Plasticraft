package dev.anvilcraft.plasticraft.molding.type;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.MoldingCauldronShapeAnalysis;
import dev.anvilcraft.plasticraft.molding.bake.MoldingCauldronShapeAnalyzer;
import dev.anvilcraft.plasticraft.molding.bake.MoldingFunctionalAnalysis;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.bake.MoldingNegativeCavityAnalyzer;
import dev.anvilcraft.plasticraft.molding.bake.MoldingTrayShapeAnalysis;
import dev.anvilcraft.plasticraft.molding.bake.MoldingTrayShapeAnalyzer;
import dev.anvilcraft.plasticraft.molding.bake.MoldingVolumeMask;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

/** 当前版本注册的制品类型。顺序同时是类型叠加层的稳定显示顺序。 */
public final class MoldingProductTypes {
    public static final ResourceLocation NORMAL_ID = AnvilcraftPlasticraft.of("normal");
    public static final ResourceLocation CHEST_ID = AnvilcraftPlasticraft.of("chest");
    public static final ResourceLocation TANK_ID = AnvilcraftPlasticraft.of("tank");
    public static final ResourceLocation ANVIL_ID = AnvilcraftPlasticraft.of("anvil");
    public static final ResourceLocation CAULDRON_ID = AnvilcraftPlasticraft.of("cauldron");
    public static final ResourceLocation LARGE_CAULDRON_ID = AnvilcraftPlasticraft.of("large_cauldron");
    public static final ResourceLocation TRAY_ID = AnvilcraftPlasticraft.of("tray");
    public static final ResourceLocation ALLAY_HARD_HAT_ID = AnvilcraftPlasticraft.of("allay_hard_hat");
    private static final Map<ResourceLocation, MoldingProductType> TYPES = createTypes();

    private MoldingProductTypes() {
    }

    public static List<MoldingProductType> values() {
        synchronized (TYPES) {
            return List.copyOf(TYPES.values());
        }
    }

    public static Optional<MoldingProductType> get(ResourceLocation id) {
        synchronized (TYPES) {
            return Optional.ofNullable(TYPES.get(id));
        }
    }

    public static boolean isRegistered(ResourceLocation id) {
        synchronized (TYPES) {
            return TYPES.containsKey(id);
        }
    }

    public static boolean isChest(ResourceLocation id) {
        return storageKind(id) == MoldingProductType.StorageKind.ITEMS;
    }

    /** 仅指储罐本身；炼药锅同样存流体，需要「能存流体」语义时用 {@link #holdsFluids}。 */
    public static boolean isTank(ResourceLocation id) {
        return TANK_ID.equals(id);
    }

    public static boolean isCauldron(ResourceLocation id) {
        return CAULDRON_ID.equals(id) || LARGE_CAULDRON_ID.equals(id);
    }

    public static boolean isLargeCauldron(ResourceLocation id) {
        return LARGE_CAULDRON_ID.equals(id);
    }

    public static boolean holdsFluids(ResourceLocation id) {
        return storageKind(id) == MoldingProductType.StorageKind.FLUIDS;
    }

    public static boolean isAnvil(ResourceLocation id) {
        return ANVIL_ID.equals(id);
    }

    public static boolean isTray(ResourceLocation id) {
        return storageKind(id) == MoldingProductType.StorageKind.TRAY;
    }

    public static boolean selectable(ResourceLocation id) {
        synchronized (TYPES) {
            return Optional.ofNullable(TYPES.get(id)).map(MoldingProductType::selectable).orElse(false);
        }
    }

    public static MoldingProductType.StorageKind storageKind(ResourceLocation id) {
        synchronized (TYPES) {
            return Optional.ofNullable(TYPES.get(id))
                .map(MoldingProductType::storageKind)
                .orElse(MoldingProductType.StorageKind.NONE);
        }
    }

    public static int unitsPerCapacity(ResourceLocation id) {
        synchronized (TYPES) {
            return Optional.ofNullable(TYPES.get(id)).map(MoldingProductType::unitsPerCapacity).orElse(0);
        }
    }

    public static int capacityFor(ResourceLocation id, int cavityVolume) {
        synchronized (TYPES) {
            return Optional.ofNullable(TYPES.get(id)).map(type -> type.capacityFor(cavityVolume)).orElse(0);
        }
    }

    public static boolean validateCapacity(ResourceLocation id, int capacity, MoldingVolumeMask cavityMask) {
        MoldingProductType type;
        synchronized (TYPES) {
            type = TYPES.get(id);
        }
        return type == null ? capacity == 0 : type.validateCapacity(capacity, cavityMask);
    }

    public static MoldingTypeValidation validate(ResourceLocation id, MoldingFunctionalAnalysis analysis) {
        MoldingProductType type;
        synchronized (TYPES) {
            type = TYPES.get(id);
        }
        return type == null
            ? MoldingTypeValidation.invalid("unknown_type")
            : type.validate(analysis);
    }

    public static MoldingTypeValidation validate(
        ResourceLocation id,
        EditableMoldingModel model,
        BakedMoldingModel baked
    ) {
        MoldingProductType type;
        synchronized (TYPES) {
            type = TYPES.get(id);
        }
        return type == null
            ? MoldingTypeValidation.invalid("unknown_type")
            : usesNegativeCavitySemantics(id)
                ? type.validate(functionalAnalysis(id, model, baked))
                : type.validate(model, baked);
    }

    static MoldingFunctionalAnalysis functionalAnalysis(
        ResourceLocation id,
        EditableMoldingModel model,
        BakedMoldingModel baked
    ) {
        if (CHEST_ID.equals(id) || TANK_ID.equals(id)) {
            return MoldingNegativeCavityAnalyzer.analyzeSealed(model, baked);
        }
        if (isCauldron(id)) return MoldingNegativeCavityAnalyzer.analyzeCauldron(model, baked);
        return baked.functionalAnalysis();
    }

    private static boolean usesNegativeCavitySemantics(ResourceLocation id) {
        return CHEST_ID.equals(id) || TANK_ID.equals(id) || isCauldron(id);
    }

    public static boolean requiresPrinting(ResourceLocation id) {
        synchronized (TYPES) {
            return Optional.ofNullable(TYPES.get(id))
                .map(MoldingProductType::requiresPrinting)
                .orElse(false);
        }
    }

    /** 供未来模块追加策略类型，内置 ID 的顺序和含义保持不变。 */
    public static void register(MoldingProductType type) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(type.id(), "type.id");
        Objects.requireNonNull(type.translationKey(), "type.translationKey");
        Objects.requireNonNull(type.productNameSuffixKey(), "type.productNameSuffixKey");
        synchronized (TYPES) {
            if (TYPES.putIfAbsent(type.id(), type) != null) {
                throw new IllegalStateException("Duplicate molding product type " + type.id());
            }
        }
    }

    private static Map<ResourceLocation, MoldingProductType> createTypes() {
        Map<ResourceLocation, MoldingProductType> types = new LinkedHashMap<>();
        register(types, new SimpleType(
            NORMAL_ID,
            MoldingProductType.StorageKind.NONE,
            0,
            "item.anvilcraftplasticraft.molded_product_suffix.block"
        ));
        register(types, new SimpleType(
            CHEST_ID,
            MoldingProductType.StorageKind.ITEMS,
            64,
            typeTranslationKey(CHEST_ID)
        ));
        register(types, new SimpleType(
            TANK_ID,
            MoldingProductType.StorageKind.FLUIDS,
            171,
            typeTranslationKey(TANK_ID)
        ));
        register(types, new AnvilType());
        register(types, new CauldronType());
        register(types, new LargeCauldronType());
        register(types, new TrayType());
        register(types, new AllayHardHatType());
        return types;
    }

    private static String typeTranslationKey(ResourceLocation id) {
        return "screen.anvilcraftplasticraft.molding.type." + id.getPath();
    }

    private static void register(Map<ResourceLocation, MoldingProductType> types, MoldingProductType type) {
        if (types.putIfAbsent(type.id(), type) != null) {
            throw new IllegalStateException("Duplicate molding product type " + type.id());
        }
    }

    private record SimpleType(
        ResourceLocation id,
        MoldingProductType.StorageKind storageKind,
        int unitsPerCapacity,
        String productNameSuffixKey
    ) implements MoldingProductType {
        @Override
        public String translationKey() {
            return typeTranslationKey(this.id);
        }

        @Override
        public MoldingProductType.StorageKind storageKind() {
            return this.storageKind;
        }

        @Override
        public MoldingTypeValidation validate(MoldingFunctionalAnalysis analysis) {
            if (this.unitsPerCapacity == 0) return MoldingTypeValidation.valid(0);
            if (analysis.cavityVolume() < this.unitsPerCapacity) {
                return MoldingTypeValidation.invalid("cavity_too_small");
            }
            if (analysis.internalDecoration()) {
                return MoldingTypeValidation.invalid("cavity_not_empty");
            }
            if ((long) analysis.cavityVolume() * 2L
                    < (long) analysis.cavityVolume() + analysis.shellVolume()) {
                return MoldingTypeValidation.invalid("cavity_ratio_too_small");
            }
            return MoldingTypeValidation.valid(analysis.cavityVolume() / this.unitsPerCapacity);
        }
    }

    private record AnvilType() implements MoldingProductType {
        @Override
        public ResourceLocation id() {
            return ANVIL_ID;
        }

        @Override
        public String translationKey() {
            return typeTranslationKey(ANVIL_ID);
        }

        @Override
        public String productNameSuffixKey() {
            return this.translationKey();
        }

        @Override
        public MoldingTypeValidation validate(MoldingFunctionalAnalysis analysis) {
            return analysis.anvilShape().valid()
                ? MoldingTypeValidation.valid(0)
                : MoldingTypeValidation.invalid(analysis.anvilShape().reason());
        }
    }

    private record TrayType() implements MoldingProductType {
        @Override
        public ResourceLocation id() {
            return TRAY_ID;
        }

        @Override
        public String translationKey() {
            return typeTranslationKey(TRAY_ID);
        }

        @Override
        public String productNameSuffixKey() {
            return this.translationKey();
        }

        @Override
        public StorageKind storageKind() {
            return StorageKind.TRAY;
        }

        @Override
        public MoldingTypeValidation validate(MoldingFunctionalAnalysis analysis) {
            return analysis.trayShape().valid()
                ? MoldingTypeValidation.valid(0)
                : MoldingTypeValidation.invalid(analysis.trayShape().reason());
        }

        @Override
        public MoldingTypeValidation validate(EditableMoldingModel model, BakedMoldingModel baked) {
            MoldingTrayShapeAnalysis shape = MoldingTrayShapeAnalyzer.analyze(
                baked.volumeMask(),
                MoldingModelBaker.createExactSurfaceMesh(model)
            );
            return shape.valid()
                ? MoldingTypeValidation.valid(0)
                : MoldingTypeValidation.invalid(shape.reason());
        }
    }

    // 炼药锅：开顶内腔容量 = max(1, floor(v/1728))，两种形态均存流体，以 FLUIDS 注册
    private record CauldronType() implements MoldingProductType {
        @Override
        public ResourceLocation id() {
            return CAULDRON_ID;
        }

        @Override
        public String translationKey() {
            return typeTranslationKey(CAULDRON_ID);
        }

        @Override
        public StorageKind storageKind() {
            return StorageKind.FLUIDS;
        }

        /** 原版炼药锅内腔 1728 px³ = 1 B，容量取最小 1 B 的整 B 向下取整。 */
        @Override
        public int unitsPerCapacity() {
            return MoldingCauldronShapeAnalyzer.UNITS_PER_BUCKET;
        }

        @Override
        public int capacityFor(int cavityVolume) {
            return Math.max(1, cavityVolume / MoldingCauldronShapeAnalyzer.UNITS_PER_BUCKET);
        }

        @Override
        public boolean validateCapacity(int capacity, MoldingVolumeMask cavityMask) {
            return capacity == capacityFor(cavityMask.volume());
        }

        @Override
        public MoldingTypeValidation validate(MoldingFunctionalAnalysis analysis) {
            MoldingCauldronShapeAnalysis shape = analysis.cauldronShape();
            return shape.valid()
                ? MoldingTypeValidation.valid(capacityFor(shape.cavityVolume()))
                : MoldingTypeValidation.invalid(shape.reason());
        }
    }

    /** 大型塑料炼药锅：固定 512 B 流体、8 槽输入 × 9 倍堆叠、落砧最多 9 次配方，由普通炼药锅自动升级得到。 */
    private record LargeCauldronType() implements MoldingProductType {
        /** 8 层 × 64 B；对齐 AnvilCraft LargeCauldronFluidHandler。 */
        private static final int FIXED_CAPACITY = 8 * 64;

        @Override
        public ResourceLocation id() {
            return LARGE_CAULDRON_ID;
        }

        @Override
        public String translationKey() {
            return typeTranslationKey(LARGE_CAULDRON_ID);
        }

        @Override
        public StorageKind storageKind() {
            return StorageKind.FLUIDS;
        }

        @Override
        public boolean selectable() {
            // 大型锅只能由普通炼药锅在开口超出阈值时自动升级，不在类型面板中显示
            return false;
        }

        @Override
        public int unitsPerCapacity() {
            return 0;
        }

        @Override
        public int capacityFor(int cavityVolume) {
            return FIXED_CAPACITY;
        }

        @Override
        public boolean validateCapacity(int capacity, MoldingVolumeMask cavityMask) {
            return capacity == FIXED_CAPACITY;
        }

        @Override
        public MoldingTypeValidation validate(MoldingFunctionalAnalysis analysis) {
            MoldingCauldronShapeAnalysis shape = analysis.cauldronShape();
            if (!shape.valid()) return MoldingTypeValidation.invalid(shape.reason());
            return shape.large()
                ? MoldingTypeValidation.valid(FIXED_CAPACITY)
                : MoldingTypeValidation.invalid("cauldron_not_large");
        }
    }

    private record AllayHardHatType() implements MoldingProductType {
        @Override
        public ResourceLocation id() {
            return ALLAY_HARD_HAT_ID;
        }

        @Override
        public String translationKey() {
            return typeTranslationKey(ALLAY_HARD_HAT_ID);
        }

        @Override
        public MoldingTypeValidation validate(MoldingFunctionalAnalysis analysis) {
            return MoldingTypeValidation.invalid("allay_hard_hat_model_required");
        }

        @Override
        public MoldingTypeValidation validate(EditableMoldingModel model, BakedMoldingModel baked) {
            return MoldingHardHatShapeValidator.validate(model, baked);
        }

        @Override
        public boolean requiresPrinting() {
            return true;
        }
    }
}
