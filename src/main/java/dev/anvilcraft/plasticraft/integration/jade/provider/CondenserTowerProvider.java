package dev.anvilcraft.plasticraft.integration.jade.provider;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.CondenserTowerBlockEntity;
import dev.dubhe.anvilcraft.util.UnitUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.addon.universal.FluidStorageProvider;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElementHelper;

/** 把任意冷凝塔部件映射到主塔，并使用 Jade 的标准储罐进度条。 */
public final class CondenserTowerProvider extends FluidStorageProvider.ForBlock {
    public static final CondenserTowerProvider INSTANCE = new CondenserTowerProvider();
    private static final String FLUID_ID = "plasticraft_fluid_id";
    private static final String FLUID_AMOUNT = "plasticraft_fluid_amount";
    private static final String GAS_ID = "plasticraft_gas_id";
    private static final String GAS_AMOUNT = "plasticraft_gas_amount";

    private CondenserTowerProvider() {
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        CondenserTowerBlockEntity tower = getTower(accessor);
        if (tower == null) return;
        FluidStack fluid = tower.getStoredFluid();
        if (!fluid.isEmpty()) {
            tag.putString(FLUID_ID, BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString());
            tag.putInt(FLUID_AMOUNT, fluid.getAmount());
        }
        ResourceLocation gas = tower.getGasId();
        if (gas != null && tower.getGasAmount() > 0) {
            tag.putString(GAS_ID, gas.toString());
            tag.putInt(GAS_AMOUNT, tower.getGasAmount());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        IElementHelper helper = IElementHelper.get();
        ResourceLocation gas = ResourceLocation.tryParse(tag.getString(GAS_ID));
        int gasAmount = Math.clamp(tag.getInt(GAS_AMOUNT), 0, CondenserTowerBlockEntity.CAPACITY);
        ResourceLocation fluidId = ResourceLocation.tryParse(tag.getString(FLUID_ID));
        Fluid fluid = fluidId == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(fluidId);
        int amount = Math.clamp(tag.getInt(FLUID_AMOUNT), 0, CondenserTowerBlockEntity.CAPACITY);
        FluidStack stack = fluid == null || fluid == Fluids.EMPTY ? FluidStack.EMPTY : new FluidStack(fluid, amount);
        if (!stack.isEmpty() || gas == null || gasAmount <= 0) {
            Component fluidName = stack.isEmpty()
                ? Component.translatable("tooltip.anvilcraftplasticraft.jade.empty").withStyle(ChatFormatting.WHITE)
                : stack.getHoverName().copy().withStyle(ChatFormatting.WHITE);
            Component fluidText = fluidName.copy().append(Component.literal(
                " " + UnitUtil.fluidUnit(amount, false)
                    + " / " + UnitUtil.fluidUnit(CondenserTowerBlockEntity.CAPACITY, false)
            ).withStyle(ChatFormatting.GRAY));
            var fluidStyle = helper.progressStyle();
            if (!stack.isEmpty()) {
                fluidStyle.overlay(helper.fluid(JadeFluidObject.of(stack.getFluid(), stack.getAmount())));
            }
            tooltip.add(helper.progress(
                (float) amount / CondenserTowerBlockEntity.CAPACITY,
                fluidText,
                fluidStyle,
                BoxStyle.getNestedBox(),
                true
            ));
        }

        if (gas == null || gasAmount <= 0) return;
        Component gasText = Component.translatable("jei.anvilcraftplasticraft.gas." + gas.getPath())
            .withStyle(ChatFormatting.WHITE)
            .append(Component.literal(
                " " + UnitUtil.fluidUnit(gasAmount, false)
                    + " / " + UnitUtil.fluidUnit(CondenserTowerBlockEntity.CAPACITY, false)
            ).withStyle(ChatFormatting.GRAY));
        tooltip.add(helper.progress(
            (float) gasAmount / CondenserTowerBlockEntity.CAPACITY,
            gasText,
            helper.progressStyle(),
            BoxStyle.getNestedBox(),
            true
        ));
    }

    @Override
    public boolean shouldRequestData(BlockAccessor accessor) {
        return getTower(accessor) != null;
    }

    private static CondenserTowerBlockEntity getTower(BlockAccessor accessor) {
        return CondenserTowerBlockEntity.getMain(
            accessor.getLevel(),
            accessor.getPosition(),
            accessor.getBlockState()
        );
    }

    @Override
    public ResourceLocation getUid() {
        return AnvilcraftPlasticraft.of("condenser_tower");
    }
}
