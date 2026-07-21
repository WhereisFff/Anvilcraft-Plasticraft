package dev.anvilcraft.plasticraft.recipe;

import net.minecraft.resources.ResourceLocation;

/** 冷凝塔内部使用的虚拟气体标识，不注册新的方块或流体。 */
public final class CondenserGas {
    public static final ResourceLocation GASEOUS_OIL = ResourceLocation.fromNamespaceAndPath(
        "anvilcraftplasticraft",
        "gaseous_oil"
    );
    public static final ResourceLocation GASEOUS_WATER = ResourceLocation.fromNamespaceAndPath(
        "anvilcraftplasticraft",
        "gaseous_water"
    );
    public static final ResourceLocation EXPERIENCE_ORBS = ResourceLocation.fromNamespaceAndPath(
        "anvilcraftplasticraft",
        "experience_orbs"
    );

    private CondenserGas() {
    }

    public static boolean isGas(ResourceLocation id) {
        return GASEOUS_OIL.equals(id) || GASEOUS_WATER.equals(id);
    }
}
