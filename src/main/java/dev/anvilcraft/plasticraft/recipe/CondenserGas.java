package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.vapor.VaporTypes;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** 冷凝塔内部使用的虚拟气体标识，不注册新的方块或流体。 */
public final class CondenserGas {
    public static final Identifier GASEOUS_OIL = VaporTypes.GASEOUS_OIL;
    public static final Identifier GASEOUS_WATER = VaporTypes.GASEOUS_WATER;
    private static final Identifier LEGACY_GASEOUS_OIL = Identifier.fromNamespaceAndPath(
        "anvilcraftplasticraft",
        "gaseous_oil"
    );
    private static final Identifier LEGACY_GASEOUS_WATER = Identifier.fromNamespaceAndPath(
        "anvilcraftplasticraft",
        "gaseous_water"
    );
    private static final Identifier LEGACY_EXPERIENCE_ORBS = Identifier.fromNamespaceAndPath(
        "anvilcraftplasticraft",
        "experience_orbs"
    );
    public static final Identifier GASEOUS_EXPERIENCE = Identifier.fromNamespaceAndPath(
        "anvilcraftplasticraft",
        "gaseous_experience"
    );

    private CondenserGas() {
    }

    public static boolean isGas(@Nullable Identifier id) {
        Identifier canonical = canonicalize(id);
        return VaporTypes.isStandard(canonical) || GASEOUS_EXPERIENCE.equals(canonical);
    }

    public static @Nullable Identifier canonicalize(@Nullable Identifier id) {
        if (LEGACY_GASEOUS_OIL.equals(id)) return GASEOUS_OIL;
        if (LEGACY_GASEOUS_WATER.equals(id)) return GASEOUS_WATER;
        if (LEGACY_EXPERIENCE_ORBS.equals(id)) return GASEOUS_EXPERIENCE;
        return id;
    }
}
