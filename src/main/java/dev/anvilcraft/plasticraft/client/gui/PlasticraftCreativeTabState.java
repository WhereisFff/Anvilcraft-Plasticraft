package dev.anvilcraft.plasticraft.client.gui;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;

import java.util.EnumMap;
import java.util.Map;

/** 保存塑料创造栏分区在当前客户端会话中的展开状态。 */
public final class PlasticraftCreativeTabState {
    private static final Map<PlasticMaterial, Boolean> EXPANDED = new EnumMap<>(PlasticMaterial.class);
    private static boolean initialized;

    private PlasticraftCreativeTabState() {
    }

    public static boolean isExpanded(PlasticMaterial material) {
        initialize();
        return EXPANDED.get(material);
    }

    public static void toggle(PlasticMaterial material) {
        initialize();
        EXPANDED.put(material, !EXPANDED.get(material));
    }

    public static void reset() {
        EXPANDED.clear();
        initialized = false;
    }

    private static void initialize() {
        if (initialized) return;
        boolean expanded = !AnvilcraftPlasticraft.CLIENT_CONFIG.foldCreativeColorVariants;
        for (PlasticMaterial material : PlasticMaterial.values()) {
            EXPANDED.put(material, expanded);
        }
        initialized = true;
    }
}
