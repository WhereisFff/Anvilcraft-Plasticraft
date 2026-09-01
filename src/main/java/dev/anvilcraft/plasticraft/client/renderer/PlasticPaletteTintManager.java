package dev.anvilcraft.plasticraft.client.renderer;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.api.texture.PlasticColorPalette;
import dev.anvilcraft.plasticraft.api.texture.PlasticGrayscaleImage;
import dev.anvilcraft.plasticraft.item.CreativeColorVariantItem;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.DyeColor;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * 为熔体、容器和粒料提供与制品相同的材料色板主色。
 *
 * <p>这些渲染器只能提交单一 tint，故选用对应色板行的最高亮度级别作为稳定主色。</p>
 */
public final class PlasticPaletteTintManager implements ResourceManagerReloadListener {
    public static final PlasticPaletteTintManager INSTANCE = new PlasticPaletteTintManager();

    private volatile Map<PlasticMaterial, int[]> tints = fallbackTints();

    private PlasticPaletteTintManager() {
    }

    public int tint(PlasticMaterial material, DyeColor color) {
        return this.tints.get(material)[color.getId()];
    }

    public int transparentTint(PlasticMaterial material, DyeColor color) {
        int tint = this.tint(material, color);
        // 透明度属于渲染状态，贴图本身保持满 alpha，避免颜色与背景叠加后发灰
        return 0x80000000 | (tint & 0x00FFFFFF);
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        Map<PlasticMaterial, int[]> loaded = new EnumMap<>(PlasticMaterial.class);
        Map<PlasticMaterial, int[]> fallback = fallbackTints();
        for (PlasticMaterial material : PlasticMaterial.values()) {
            try {
                loaded.put(material, tints(PlasticTextureResourceLoader.loadPalette(resourceManager, material)));
            } catch (IOException | IllegalArgumentException exception) {
                AnvilcraftPlasticraft.LOGGER.error(
                    "Unable to load {} palette tints; using dye defaults: {}",
                    material.key(),
                    exception.getMessage(),
                    exception
                );
                loaded.put(material, fallback.get(material));
            }
        }
        this.tints = Map.copyOf(loaded);
    }

    private static int[] tints(PlasticColorPalette palette) {
        int[] result = new int[DyeColor.values().length];
        for (DyeColor color : DyeColor.values()) {
            result[color.getId()] = palette.colorAt(
                CreativeColorVariantItem.paletteRow(color),
                PlasticGrayscaleImage.MAX_LEVELS - 1
            ) | 0xFF000000;
        }
        return result;
    }

    private static Map<PlasticMaterial, int[]> fallbackTints() {
        Map<PlasticMaterial, int[]> result = new EnumMap<>(PlasticMaterial.class);
        for (PlasticMaterial material : PlasticMaterial.values()) {
            int[] colors = new int[DyeColor.values().length];
            for (DyeColor color : DyeColor.values()) {
                colors[color.getId()] = 0xFF000000 | PlasticMeltColor.tint(color);
            }
            result.put(material, colors);
        }
        return Map.copyOf(result);
    }
}
