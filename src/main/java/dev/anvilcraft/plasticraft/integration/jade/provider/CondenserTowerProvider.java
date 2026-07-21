package dev.anvilcraft.plasticraft.integration.jade.provider;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.CondenserTowerBlockEntity;
import dev.anvilcraft.plasticraft.recipe.CondenserGas;
import dev.dubhe.anvilcraft.util.UnitUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElementHelper;

/** 为冷凝塔的每个部件显示核心流体容量和当前虚拟气体缓存。 */
public enum CondenserTowerProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String FLUID_ID = "fluid_id";
    private static final String FLUID_AMOUNT = "fluid_amount";
    private static final String GAS_ID = "gas_id";
    private static final String GAS_AMOUNT = "gas_amount";

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        CondenserTowerBlockEntity tower = CondenserTowerBlockEntity.getMain(
            accessor.getLevel(),
            accessor.getPosition(),
            accessor.getBlockState()
        );
        if (tower == null) return;
        FluidStack fluid = tower.getStoredFluid();
        tag.putInt(FLUID_ID, fluid.isEmpty() ? -1 : BuiltInRegistries.FLUID.getId(fluid.getFluid()));
        tag.putInt(FLUID_AMOUNT, fluid.getAmount());
        ResourceLocation gas = tower.getGasId();
        if (gas != null && CondenserGas.isGas(gas) && tower.getGasAmount() > 0) {
            tag.putString(GAS_ID, gas.toString());
            tag.putInt(GAS_AMOUNT, tower.getGasAmount());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        int fluidId = data.getInt(FLUID_ID);
        int amount = Math.max(0, data.getInt(FLUID_AMOUNT));
        Fluid registeredFluid = fluidId < 0 ? null : BuiltInRegistries.FLUID.byId(fluidId);
        FluidStack fluid = registeredFluid == null || registeredFluid == Fluids.EMPTY || amount == 0
            ? FluidStack.EMPTY
            : new FluidStack(registeredFluid, amount);
        tooltip.clear();
        tooltip.add(Component.translatable(accessor.getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE));

        IElementHelper helper = IElementHelper.get();
        Component fluidText = fluid.isEmpty()
            ? Component.translatable("tooltip.anvilcraftplasticraft.jade.empty")
                .append(Component.literal(" 0 / " + UnitUtil.fluidUnit(CondenserTowerBlockEntity.CAPACITY, false)))
            : Component.translatable(
                "tooltip.anvilcraftplasticraft.jade.fluid",
                fluid.getHoverName(),
                UnitUtil.fluidUnit(fluid.getAmount(), false),
                UnitUtil.fluidUnit(CondenserTowerBlockEntity.CAPACITY, false)
            );
        if (fluid.isEmpty()) {
            tooltip.add(fluidText.copy().withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(helper.progress(
                (float) amount / CondenserTowerBlockEntity.CAPACITY,
                fluidText,
                helper.progressStyle().overlay(helper.fluid(JadeFluidObject.of(fluid.getFluid(), amount))),
                BoxStyle.getNestedBox(),
                true
            ));
        }

        ResourceLocation gas = ResourceLocation.tryParse(data.getString(GAS_ID));
        int gasAmount = Math.max(0, data.getInt(GAS_AMOUNT));
        if (CondenserGas.isGas(gas) && gasAmount > 0) {
            Component gasName = Component.translatable("jei.anvilcraftplasticraft.gas." + gas.getPath());
            tooltip.add(Component.translatable(
                "tooltip.anvilcraftplasticraft.jade.gas",
                gasName,
                UnitUtil.fluidUnit(gasAmount, false),
                UnitUtil.fluidUnit(CondenserTowerBlockEntity.CAPACITY, false)
            ).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return AnvilcraftPlasticraft.of("condenser_tower");
    }
}
