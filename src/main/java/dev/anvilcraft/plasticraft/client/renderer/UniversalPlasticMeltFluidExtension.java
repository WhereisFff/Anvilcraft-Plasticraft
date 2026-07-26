package dev.anvilcraft.plasticraft.client.renderer;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.UniversalPlasticMeltBlockEntity;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/** 使用同一张灰度纹理渲染流体，并从存储位置读取调色数据。 */
public final class UniversalPlasticMeltFluidExtension implements IClientFluidTypeExtensions {
    private static final ResourceLocation TEXTURE = AnvilcraftPlasticraft.of("block/universal_plastic_melt");

    @Override
    public ResourceLocation getStillTexture() {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getFlowingTexture() {
        return TEXTURE;
    }

    @Override
    public int getTintColor() {
        return 0xFFFFFFFF;
    }

    @Override
    public int getTintColor(FluidStack stack) {
        return PlasticMeltColor.tint(stack);
    }

    @Override
    public int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
        if (getter.getBlockEntity(pos) instanceof UniversalPlasticMeltBlockEntity melt) {
            return PlasticMeltColor.tint(melt.getColor());
        }
        return PlasticMeltColor.tint(net.minecraft.world.item.DyeColor.WHITE);
    }
}
