package dev.anvilcraft.plasticraft.integration.jade.provider;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.CondenserTowerBlockEntity;
import dev.anvilcraft.plasticraft.recipe.CondenserGas;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.ui.IDisplayHelper;
import snownee.jade.api.ui.IElementHelper;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/** 将每个冷凝塔部件映射到核心储罐，并复用 Jade 的统一流体容器视图。 */
public enum CondenserTowerProvider implements IServerExtensionProvider<CompoundTag>,
    IClientExtensionProvider<CompoundTag, FluidView> {
    INSTANCE;

    private static final String GAS_ID = "plasticraft_gas_id";
    private static final String GAS_AMOUNT = "plasticraft_gas_amount";

    @Override
    public List<ViewGroup<CompoundTag>> getGroups(Accessor<?> accessor) {
        CondenserTowerBlockEntity tower = getTower(accessor);
        if (tower == null) return List.of();

        List<CompoundTag> views = new ArrayList<>();
        FluidStack fluid = tower.getStoredFluid();
        JadeFluidObject fluidObject = fluid.isEmpty()
            ? JadeFluidObject.of(Fluids.EMPTY, 0)
            : JadeFluidObject.of(fluid.getFluid(), fluid.getAmount());
        views.add(FluidView.writeDefault(fluidObject, CondenserTowerBlockEntity.CAPACITY));

        ResourceLocation gas = tower.getGasId();
        int gasAmount = tower.getGasAmount();
        if (CondenserGas.isGas(gas) && gasAmount > 0) {
            CompoundTag gasView = new CompoundTag();
            gasView.putString(GAS_ID, gas.toString());
            gasView.putInt(GAS_AMOUNT, gasAmount);
            views.add(gasView);
        }
        return List.of(new ViewGroup<>(views));
    }

    @Override
    public List<ClientViewGroup<FluidView>> getClientGroups(
        Accessor<?> accessor,
        List<ViewGroup<CompoundTag>> groups
    ) {
        return ClientViewGroup.map(groups, CondenserTowerProvider::readView, null);
    }

    @Override
    public boolean shouldRequestData(Accessor<?> accessor) {
        return getTower(accessor) != null;
    }

    private static CondenserTowerBlockEntity getTower(Accessor<?> accessor) {
        if (!(accessor instanceof BlockAccessor block)) return null;
        return CondenserTowerBlockEntity.getMain(
            block.getLevel(),
            block.getPosition(),
            block.getBlockState()
        );
    }

    private static FluidView readView(CompoundTag tag) {
        if (!tag.contains(GAS_ID)) return FluidView.readDefault(tag);

        ResourceLocation gas = ResourceLocation.tryParse(tag.getString(GAS_ID));
        int amount = Math.max(0, tag.getInt(GAS_AMOUNT));
        if (!CondenserGas.isGas(gas) || amount == 0) return null;

        FluidView view = new FluidView(IElementHelper.get().fluid(JadeFluidObject.of(Fluids.EMPTY, 0)));
        view.fluidName = Component.translatable("jei.anvilcraftplasticraft.gas." + gas.getPath());
        view.current = IDisplayHelper.get().humanReadableNumber(amount, "B", true);
        view.max = IDisplayHelper.get().humanReadableNumber(CondenserTowerBlockEntity.CAPACITY, "B", true);
        view.ratio = (float) amount / CondenserTowerBlockEntity.CAPACITY;
        return view;
    }

    @Override
    public ResourceLocation getUid() {
        return AnvilcraftPlasticraft.of("condenser_tower");
    }
}
