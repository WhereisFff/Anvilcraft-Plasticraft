package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.vapor.VaporTypes;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** 冷凝塔内部使用的虚拟气体标识，不注册新的方块或流体。 */
public final class CondenserGas {
    public static final ResourceLocation GASEOUS_OIL = VaporTypes.GASEOUS_OIL;
    public static final ResourceLocation GASEOUS_WATER = VaporTypes.GASEOUS_WATER;
    private static final ResourceLocation LEGACY_GASEOUS_OIL = ResourceLocation.fromNamespaceAndPath(
        "anvilcraftplasticraft",
        "gaseous_oil"
    );
    private static final ResourceLocation LEGACY_GASEOUS_WATER = ResourceLocation.fromNamespaceAndPath(
        "anvilcraftplasticraft",
        "gaseous_water"
    );
    private static final ResourceLocation LEGACY_EXPERIENCE_ORBS = ResourceLocation.fromNamespaceAndPath(
        "anvilcraftplasticraft",
        "experience_orbs"
    );
    public static final ResourceLocation GASEOUS_EXPERIENCE = ResourceLocation.fromNamespaceAndPath(
        "anvilcraftplasticraft",
        "gaseous_experience"
    );

    private CondenserGas() {
    }

    public static boolean isGas(@Nullable ResourceLocation id) {
        ResourceLocation canonical = canonicalize(id);
        return VaporTypes.isStandard(canonical) || GASEOUS_EXPERIENCE.equals(canonical);
    }

    public static @Nullable ResourceLocation canonicalize(@Nullable ResourceLocation id) {
        if (LEGACY_GASEOUS_OIL.equals(id)) return GASEOUS_OIL;
        if (LEGACY_GASEOUS_WATER.equals(id)) return GASEOUS_WATER;
        if (LEGACY_EXPERIENCE_ORBS.equals(id)) return GASEOUS_EXPERIENCE;
        return id;
    }
}
