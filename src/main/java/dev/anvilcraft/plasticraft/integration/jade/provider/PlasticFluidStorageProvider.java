package dev.anvilcraft.plasticraft.integration.jade.provider;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.CondenserTowerBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.product.PlasticCauldronLayout;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.ui.IDisplayHelper;
import snownee.jade.api.ui.IElementHelper;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;
import snownee.jade.util.CommonProxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 统一塑料容器的总存量标题与流体行，共享容量只统计一次。 */
public enum PlasticFluidStorageProvider implements IServerExtensionProvider<CompoundTag>,
    IClientExtensionProvider<CompoundTag, FluidView> {
    INSTANCE;

    private static final int MAX_VISIBLE_FLUIDS = 8;
    private static final String TOTAL = "Total";
    private static final String CAPACITY = "Capacity";
    private static final String SHARED = "Shared";
    private static final String GAS = "Gas";
    private static final ResourceLocation UID = AnvilcraftPlasticraft.of("fluid_storage");

    @Override
    public @Nullable List<ViewGroup<CompoundTag>> getGroups(Accessor<?> accessor) {
        if (accessor instanceof EntityAccessor entityAccessor) {
            return this.entityGroups(accessor, entityAccessor.getEntity());
        }
        if (!(accessor instanceof BlockAccessor block)) {
            return null;
        }
        if (block.getBlockEntity() instanceof BondedEntityBlockEntity bonded) {
            return this.entityGroups(accessor, bonded.getOrCreateRenderEntity());
        }
        if (block.getBlockEntity() instanceof PlasticMoldingChamberBlockEntity chamber) {
            IFluidHandler handler = chamber.fluidHandler();
            List<ViewGroup<CompoundTag>> groups = new ArrayList<>();
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                int capacity = handler.getTankCapacity(tank);
                if (capacity > 0) {
                    FluidStack fluid = handler.getFluidInTank(tank);
                    ViewGroup<CompoundTag> group = new ViewGroup<>(List.of(this.fluidData(accessor, fluid, capacity)));
                    group.getExtraData().putLong(TOTAL, fluid.getAmount());
                    group.getExtraData().putLong(CAPACITY, capacity);
                    groups.add(group);
                }
            }
            return groups;
        }
        if (block.getBlock() instanceof CondenserTowerBlock) {
            return this.towerGroups(block);
        }
        IFluidHandler handler = block.getLevel().getCapability(
            Capabilities.FluidHandler.BLOCK, block.getPosition(), Direction.UP
        );
        return handler == null ? null : List.of(this.group(accessor, handler, handler.getTankCapacity(0), false));
    }

    private @Nullable List<ViewGroup<CompoundTag>> entityGroups(Accessor<?> accessor, @Nullable Entity entity) {
        if (entity instanceof UniversalPlasticEntity plastic) {
            MoldedPlasticData data = plastic.getMoldedData().orElse(null);
            if (data == null || !MoldingProductTypes.holdsFluids(data.finalType())) {
                return null;
            }
            IFluidHandler handler = plastic.getMoldedFluidHandler();
            PlasticCauldronLayout layout = PlasticCauldronLayout.of(data.finalType());
            int capacity = layout == null ? handler.getTankCapacity(0) : layout.fluidCapacity(data.capacity());
            return List.of(this.group(accessor, handler, capacity, layout != PlasticCauldronLayout.NORMAL));
        }
        if (entity instanceof HardenedResinCauldronEntity cauldron) {
            return List.of(this.group(accessor, cauldron.getFluidHandler(), HardenedResinCauldronEntity.CAPACITY, false));
        }
        return null;
    }

    private List<ViewGroup<CompoundTag>> towerGroups(BlockAccessor accessor) {
        CondenserTowerBlockEntity tower = CondenserTowerBlockEntity.getMain(
            accessor.getLevel(), accessor.getPosition(), accessor.getBlockState()
        );
        if (tower == null) {
            return List.of();
        }
        List<ViewGroup<CompoundTag>> groups = new ArrayList<>();
        ResourceLocation gas = tower.getGasId();
        int gasAmount = tower.getGasAmount();
        if (!tower.getStoredFluid().isEmpty() || gas == null || gasAmount <= 0) {
            groups.add(this.group(accessor, tower.getFluidHandler(), CondenserTowerBlockEntity.CAPACITY, false));
        }
        if (gas != null && gasAmount > 0) {
            CompoundTag view = this.fluidData(accessor, FluidStack.EMPTY, CondenserTowerBlockEntity.CAPACITY);
            ViewGroup<CompoundTag> group = new ViewGroup<>(List.of(view));
            group.getExtraData().putString(GAS, "jei.anvilcraftplasticraft.gas." + gas.getPath());
            group.getExtraData().putLong(TOTAL, gasAmount);
            group.getExtraData().putLong(CAPACITY, CondenserTowerBlockEntity.CAPACITY);
            groups.add(group);
        }
        return groups;
    }

    private ViewGroup<CompoundTag> group(Accessor<?> accessor, IFluidHandler handler, long capacity, boolean shared) {
        List<CompoundTag> fluids = new ArrayList<>();
        long total = 0;
        int omitted = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack fluid = handler.getFluidInTank(tank);
            if (fluid.isEmpty()) {
                continue;
            }
            total += fluid.getAmount();
            if (fluids.size() < MAX_VISIBLE_FLUIDS) {
                fluids.add(this.fluidData(accessor, fluid, capacity));
            } else {
                omitted++;
            }
        }
        if (fluids.isEmpty()) {
            fluids.add(this.fluidData(accessor, FluidStack.EMPTY, capacity));
        }
        ViewGroup<CompoundTag> group = new ViewGroup<>(fluids);
        group.getExtraData().putLong(TOTAL, total);
        group.getExtraData().putLong(CAPACITY, capacity);
        group.getExtraData().putBoolean(SHARED, shared);
        if (omitted > 0) {
            group.getExtraData().putInt("+", omitted);
        }
        return group;
    }

    private CompoundTag fluidData(Accessor<?> accessor, FluidStack fluid, long capacity) {
        JadeFluidObject object = JadeFluidObject.of(fluid.getFluid(), fluid.getAmount(), fluid.getComponentsPatch());
        CompoundTag tag = new CompoundTag();
        tag.put("fluid", JadeFluidObject.CODEC.encodeStart(accessor.nbtOps(), object)
            .getOrThrow(message -> new IllegalStateException("Unable to encode Plasticraft Jade fluid: " + message)));
        tag.putLong("capacity", capacity);
        tag.putLong(TOTAL, fluid.getAmount());
        tag.putBoolean("Empty", fluid.isEmpty());
        return tag;
    }

    @Override
    public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
        List<ClientViewGroup<FluidView>> result = ClientViewGroup.map(groups, tag -> this.createView(accessor, tag), (group, client) -> {
            CompoundTag extra = group.getExtraData();
            long total = extra.getLong(TOTAL);
            long capacity = extra.getLong(CAPACITY);
            client.title = Component.translatable(
                "tooltip.anvilcraftplasticraft.jade.fluid_total", formatAmount(total), formatAmount(capacity), percent(total, capacity)
            ).withStyle(ChatFormatting.GRAY);
            for (int index = 0; index < client.views.size(); index++) {
                FluidView view = client.views.get(index);
                CompoundTag tag = group.views.get(index);
                if (extra.contains(GAS)) {
                    view.ratio = ratio(total, capacity);
                    view.current = formatAmount(total);
                    view.overrideText = Component.translatable(extra.getString(GAS)).withStyle(ChatFormatting.WHITE)
                        .append(" ").append(Component.literal(formatAmount(total)).withStyle(ChatFormatting.GRAY));
                } else if (!tag.getBoolean("Empty") && view.fluidName != null) {
                    view.overrideText = view.fluidName.copy().withStyle(ChatFormatting.WHITE)
                        .append(" ").append(Component.literal(formatAmount(tag.getLong(TOTAL))).withStyle(ChatFormatting.GRAY));
                    if (extra.getBoolean(SHARED)) {
                        view.overrideText = view.overrideText.copy().append(" ").append(Component.translatable(
                            "tooltip.anvilcraftplasticraft.jade.fluid_share", percent(tag.getLong(TOTAL), capacity)
                        ).withStyle(ChatFormatting.GRAY));
                    }
                }
            }
        });
        return result.stream().filter(group -> !group.views.isEmpty()).toList();
    }

    private @Nullable FluidView createView(Accessor<?> accessor, CompoundTag tag) {
        long capacity = tag.getLong("capacity");
        if (capacity <= 0) {
            return null;
        }
        JadeFluidObject fluid = JadeFluidObject.CODEC.parse(accessor.nbtOps(), tag.getCompound("fluid"))
            .getOrThrow(message -> new IllegalStateException("Unable to decode Plasticraft Jade fluid: " + message));
        FluidView view = new FluidView(IElementHelper.get().fluid(fluid));
        view.fluidName = CommonProxy.getFluidName(fluid);
        view.current = formatAmount(tag.getLong(TOTAL));
        view.max = formatAmount(capacity);
        view.ratio = ratio(tag.getLong(TOTAL), capacity);
        if (tag.getBoolean("Empty")) {
            view.overrideText = Component.translatable(
                "jade.fluid", FluidView.EMPTY_FLUID, Component.literal(view.max).withStyle(ChatFormatting.GRAY)
            );
        }
        return view;
    }

    private static String formatAmount(long amount) {
        return IDisplayHelper.get().humanReadableNumber(amount, "B", true);
    }

    private static float ratio(long amount, long capacity) {
        return capacity <= 0 ? 0 : Math.clamp((float) amount / capacity, 0, 1);
    }

    private static String percent(long amount, long capacity) {
        return String.format(Locale.ROOT, "%.1f%%", ratio(amount, capacity) * 100);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
