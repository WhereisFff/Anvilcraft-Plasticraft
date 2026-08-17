package dev.anvilcraft.plasticraft.client.renderer;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.UniversalPlasticMeltBlockEntity;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidStack;

/** 使用同一张灰度纹理渲染流体，并从存储位置读取调色数据。 */
public final class UniversalPlasticMeltFluidExtension extends HighViscosityResinFluidExtension {
    private static final String CATALYSIS_OPACITY_KEY = "PlasticraftCatalysisOpacity";
    private final PlasticMaterial material;
    private final boolean opaque;
    private final int defaultTint;

    public UniversalPlasticMeltFluidExtension() {
        this(PlasticMaterial.UNIVERSAL, AnvilcraftPlasticraft.of("block/universal_plastic_melt"), true, 0xFFFFFFFF);
    }

    public UniversalPlasticMeltFluidExtension(ResourceLocation texture) {
        this(PlasticMaterial.UNIVERSAL, texture, true, 0xFFFFFFFF);
    }

    public UniversalPlasticMeltFluidExtension(ResourceLocation texture, boolean opaque, int defaultTint) {
        this(PlasticMaterial.UNIVERSAL, texture, opaque, defaultTint);
    }

    public UniversalPlasticMeltFluidExtension(
        PlasticMaterial material,
        ResourceLocation texture,
        boolean opaque,
        int defaultTint
    ) {
        super(texture);
        this.material = material;
        this.opaque = opaque;
        this.defaultTint = defaultTint;
    }

    @Override
    public boolean isOpaque() {
        return this.opaque;
    }

    @Override
    public int getTintColor(FluidStack stack) {
        int tint = this.tint(PlasticMeltColor.get(stack));
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return tint;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(CATALYSIS_OPACITY_KEY, Tag.TAG_FLOAT)) return tint;
        int alpha = Math.round((tint >>> 24) * Mth.clamp(tag.getFloat(CATALYSIS_OPACITY_KEY), 0.0F, 1.0F));
        return alpha << 24 | tint & 0x00FFFFFF;
    }

    @Override
    public int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
        if (getter.getBlockEntity(pos) instanceof UniversalPlasticMeltBlockEntity melt) {
            return this.tint(melt.getColor());
        }
        return this.tint(DyeColor.WHITE);
    }

    private int tint(DyeColor color) {
        int rgb = this.opaque
            ? PlasticPaletteTintManager.INSTANCE.tint(this.material, color)
            : PlasticPaletteTintManager.INSTANCE.transparentTint(this.material, color);
        int alpha = this.opaque ? 0xFF : this.defaultTint >>> 24;
        return alpha << 24 | rgb & 0x00FFFFFF;
    }

    /** 只在客户端临时渲染栈上记录催化覆层透明度。 */
    public static void setCatalysisOpacity(FluidStack stack, float opacity) {
        CompoundTag tag = stack.has(DataComponents.CUSTOM_DATA)
            ? stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
            : new CompoundTag();
        tag.putFloat(CATALYSIS_OPACITY_KEY, Mth.clamp(opacity, 0.0F, 1.0F));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
