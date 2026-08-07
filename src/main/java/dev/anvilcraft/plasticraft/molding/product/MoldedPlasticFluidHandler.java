package dev.anvilcraft.plasticraft.molding.product;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.minecraft.world.item.ItemStack;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 无菜单储罐使用的有序共享容量多流体能力。 */
public final class MoldedPlasticFluidHandler implements IFluidHandlerItem {
    private final Supplier<Optional<MoldedPlasticData>> data;
    private final Consumer<MoldedPlasticData> update;
    private final Supplier<ItemStack> container;

    public MoldedPlasticFluidHandler(
        Supplier<Optional<MoldedPlasticData>> data,
        Consumer<MoldedPlasticData> update
    ) {
        this(data, update, () -> ItemStack.EMPTY);
    }

    public MoldedPlasticFluidHandler(
        Supplier<Optional<MoldedPlasticData>> data,
        Consumer<MoldedPlasticData> update,
        Supplier<ItemStack> container
    ) {
        this.data = data;
        this.update = update;
        this.container = container;
    }

    @Override
    public ItemStack getContainer() {
        return this.container.get();
    }

    @Override
    public int getTanks() {
        return this.data.get().filter(value -> MoldingProductTypes.isTank(value.finalType()))
            .map(value -> Math.max(1, value.contents().fluids().size())).orElse(0);
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        List<FluidStack> fluids = this.fluids();
        return tank >= 0 && tank < fluids.size() ? fluids.get(tank) : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank >= 0 && tank < this.getTanks()
            ? this.data.get().filter(value -> MoldingProductTypes.isTank(value.finalType()))
                .map(value -> Math.multiplyExact(value.capacity(), 1000)).orElse(0)
            : 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return stack != null && !stack.isEmpty() && tank >= 0 && tank < this.getTanks();
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource == null || resource.isEmpty()) return 0;
        Optional<MoldedPlasticData> current = this.data.get();
        if (current.isEmpty() || !MoldingProductTypes.isTank(current.orElseThrow().finalType())) return 0;
        MoldedPlasticData value = current.orElseThrow();
        int totalCapacity = Math.multiplyExact(value.capacity(), 1000);
        List<FluidStack> fluids = new ArrayList<>(value.contents().fluids());
        long total = fluids.stream().mapToLong(FluidStack::getAmount).sum();
        int accepted = (int) Math.min((long) resource.getAmount(), (long) totalCapacity - total);
        if (accepted <= 0) return 0;
        int match = -1;
        for (int index = 0; index < fluids.size(); index++) {
            if (FluidStack.isSameFluidSameComponents(fluids.get(index), resource)) {
                match = index;
                break;
            }
        }
        if (action.execute()) {
            if (match >= 0) fluids.set(match, fluids.get(match).copyWithAmount(fluids.get(match).getAmount() + accepted));
            else fluids.add(resource.copyWithAmount(accepted));
            this.update.accept(value.withContents(new MoldedPlasticContents(value.contents().items(), fluids)));
        }
        return accepted;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource == null || resource.isEmpty()) return FluidStack.EMPTY;
        List<FluidStack> fluids = this.fluids();
        for (int index = fluids.size() - 1; index >= 0; index--) {
            FluidStack stored = fluids.get(index);
            if (!FluidStack.isSameFluidSameComponents(stored, resource)) continue;
            return this.drainIndex(index, Math.min(resource.getAmount(), stored.getAmount()), action);
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int amount, FluidAction action) {
        if (amount <= 0) return FluidStack.EMPTY;
        List<FluidStack> fluids = this.fluids();
        return fluids.isEmpty()
            ? FluidStack.EMPTY
            : this.drainIndex(
                fluids.size() - 1,
                Math.min(amount, fluids.getLast().getAmount()),
                action
            );
    }

    private FluidStack drainIndex(int index, int amount, FluidAction action) {
        List<FluidStack> fluids = new ArrayList<>(this.fluids());
        FluidStack stored = fluids.get(index);
        FluidStack extracted = stored.copyWithAmount(amount);
        if (action.execute()) {
            fluids.set(index, stored.copyWithAmount(stored.getAmount() - amount));
            fluids.removeIf(FluidStack::isEmpty);
            MoldedPlasticData value = this.data.get().orElseThrow();
            this.update.accept(value.withContents(new MoldedPlasticContents(value.contents().items(), fluids)));
        }
        return extracted;
    }

    private List<FluidStack> fluids() {
        return this.data.get().map(value -> value.contents().fluids()).orElse(List.of());
    }
}
