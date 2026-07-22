package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.yukkuri.api.vapor.YukkuriVaporTypes;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** 冷凝塔内部使用的虚拟气体标识，不注册新的方块或流体。 */
public final class CondenserGas {
    public static final ResourceLocation GASEOUS_OIL = YukkuriVaporTypes.GASEOUS_OIL;
    public static final ResourceLocation GASEOUS_WATER = YukkuriVaporTypes.GASEOUS_WATER;
    private static final ResourceLocation LEGACY_GASEOUS_OIL = ResourceLocation.fromNamespaceAndPath(
        "anvilcraftplasticraft",
        "gaseous_oil"
    );
    private static final ResourceLocation LEGACY_GASEOUS_WATER = ResourceLocation.fromNamespaceAndPath(
        "anvilcraftplasticraft",
        "gaseous_water"
    );
    public static final ResourceLocation EXPERIENCE_ORBS = ResourceLocation.fromNamespaceAndPath(
        "anvilcraftplasticraft",
        "experience_orbs"
    );

    private CondenserGas() {
    }

    public static boolean isGas(@Nullable ResourceLocation id) {
        return YukkuriVaporTypes.isStandard(canonicalize(id));
    }

    public static @Nullable ResourceLocation canonicalize(@Nullable ResourceLocation id) {
        if (LEGACY_GASEOUS_OIL.equals(id)) return GASEOUS_OIL;
        if (LEGACY_GASEOUS_WATER.equals(id)) return GASEOUS_WATER;
        return id;
    }
}
