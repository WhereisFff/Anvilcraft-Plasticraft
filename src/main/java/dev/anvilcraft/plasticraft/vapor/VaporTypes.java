package dev.anvilcraft.plasticraft.vapor;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 虚拟气体标识。命名空间保持 {@code yukkuri}，以免改动已生成配方里的 gas 字段。
 */
public final class VaporTypes {
    public static final String NAMESPACE = "yukkuri";
    public static final ResourceLocation GASEOUS_OIL = of("gaseous_oil");
    public static final ResourceLocation GASEOUS_WATER = of("gaseous_water");

    private VaporTypes() {
    }

    public static ResourceLocation of(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static boolean isStandard(@Nullable ResourceLocation id) {
        return GASEOUS_OIL.equals(id) || GASEOUS_WATER.equals(id);
    }
}
