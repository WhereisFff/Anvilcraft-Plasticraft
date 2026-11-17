package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.serialization.MapCodec;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** 根据物品材质选取对应的色板，透明颗粒同时读取色板的透明度。 */
public enum PlasticPaletteItemTint implements ItemTintSource {
    INSTANCE;

    public static final MapCodec<PlasticPaletteItemTint> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        return PlasticMaterial.fromGranule(stack).map(material -> color(material, PlasticMeltColor.get(stack))).orElse(-1);
    }

    public static int color(PlasticMaterial material, DyeColor color) {
        return material.isTransparent()
            ? PlasticPaletteTintManager.INSTANCE.transparentTint(material, color)
            : PlasticPaletteTintManager.INSTANCE.tint(material, color);
    }

    @Override
    public MapCodec<PlasticPaletteItemTint> type() {
        return CODEC;
    }
}
