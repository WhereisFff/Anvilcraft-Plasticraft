package dev.anvilcraft.plasticraft.vapor;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * 虚拟气体标识。命名空间保持 {@code yukkuri}，以免改动已生成配方里的 gas 字段。
 */
public final class VaporTypes {
    public static final String NAMESPACE = "yukkuri";
    public static final Identifier GASEOUS_OIL = of("gaseous_oil");
    public static final Identifier GASEOUS_WATER = of("gaseous_water");

    private VaporTypes() {
    }

    public static Identifier of(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static boolean isStandard(@Nullable Identifier id) {
        return GASEOUS_OIL.equals(id) || GASEOUS_WATER.equals(id);
    }
}
