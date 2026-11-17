package dev.anvilcraft.plasticraft.molding.product;

import dev.anvilcraft.plasticraft.molding.product.storage.MoldedPlasticStorageHandle;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 无菜单储罐与塑料炼药锅使用的有序共享容量多流体能力。
 *
 * <p>列表索引 0 是最底层、末尾是最顶层：{@link #fill} 追加新层、{@link #drain} 自顶向下消耗，
 * 与 AnvilCraft 大型炼药锅的分层顺序一致。储罐不限层数，炼药锅按
 * {@link PlasticCauldronLayout} 限制层数与单层容量。
 */
public final class MoldedPlasticFluidHandler implements MoldedFluidLayers, IFluidHandlerItem {
    private final MoldedPlasticStorageHandle handle;
    private final Supplier<ItemStack> container;
    private final MoldedFluidLayers bottomAccess = new LayeredAccess(true, 0);
    private final MoldedFluidLayers topAccess = new LayeredAccess(false, Integer.MAX_VALUE);

    public MoldedPlasticFluidHandler(ItemAccess access) {
        this.handle = new MoldedPlasticStorageHandle(access);
        this.container = () -> access.getResource().toStack(access.getAmount());
    }

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
        this(new MoldedPlasticStorageHandle(data, update), container);
    }

    public MoldedPlasticFluidHandler(MoldedPlasticStorageHandle handle, Supplier<ItemStack> container) {
        this.handle = handle;
        this.container = container;
    }

    @Override
    public ItemStack getContainer() {
        return this.container.get();
    }

    @Override
    public int getTanks() {
        return this.handle.data()
            .filter(value -> MoldingProductTypes.holdsFluids(value.finalType()))
            .map(value -> {
                PlasticCauldronLayout layout = PlasticCauldronLayout.of(value.finalType());
                return layout == null
                    ? Math.max(1, this.handle.fluids().size())
                    : layout.fluidLayers();
            })
            .orElse(0);
    }

    /** 原始层序的最底层流体，对齐本体大型炼药锅的 index 0 语义。 */
    public FluidStack getBottomFluid() {
        List<FluidStack> fluids = this.handle.fluids();
        return fluids.isEmpty() ? FluidStack.EMPTY : fluids.getFirst();
    }

    /** 只作用于最底层的句柄，对齐大型炼药锅 {@code bottomAccess()} 的扣量方向。 */
    public MoldedFluidLayers bottomAccess() {
        return this.bottomAccess;
    }

    public MoldedFluidLayers topAccess() {
        // 顶部视图始终从当前最高层抽取，不缓存可配置的总容量。
        return this.topAccess;
    }

    public MoldedFluidLayers sideAccess(int accessibleAmount) {
        return new LayeredAccess(false, accessibleAmount);
    }

    public List<FluidStack> copyFluids() {
        return this.handle.fluids();
    }

    public void setFluids(List<FluidStack> fluids) {
        this.handle.data().ifPresent(data -> {
            List<FluidStack> compact = fluids.stream().filter(fluid -> !fluid.isEmpty()).toList();
            // 提交前走制品本身的容量校验，不能让模拟器截断超量流体后静默丢弃。
            data.withContents(new MoldedPlasticContents(List.of(), compact, data.contents().trayComponents()));
            this.handle.setFluids(compact);
        });
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        List<FluidStack> fluids = this.handle.fluids();
        return tank >= 0 && tank < fluids.size() ? fluids.get(tank) : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank >= 0 && tank < this.getTanks()
            ? this.handle.data()
                .filter(value -> MoldingProductTypes.holdsFluids(value.finalType()))
                .map(MoldedPlasticFluidHandler::layerCapacity)
                .orElse(0)
            : 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        if (stack == null || stack.isEmpty() || tank < 0 || tank >= this.getTanks()) return false;
        List<FluidStack> fluids = this.handle.fluids();
        if (tank < fluids.size()) {
            return FluidStack.isSameFluidSameComponents(fluids.get(tank), stack);
        }
        return tank == fluids.size() && fluids.stream().noneMatch(stored -> FluidStack.isSameFluidSameComponents(stored, stack));
    }

    public void discardFluids() {
        this.handle.setFluids(List.of());
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!this.isFluidValid(index, resource.toStack(1))) {
            return 0;
        }
        return this.insert(resource, amount, false, tx);
    }

    @Override
    public int insert(FluidResource resource, int amount, TransactionContext tx) {
        return this.insert(resource, amount, false, tx);
    }

    private int insert(FluidResource fluid, int amount, boolean atBottom, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(fluid, amount);
        FluidStack resource = fluid.toStack(amount);
        Optional<MoldedPlasticData> current = this.handle.data();
        if (current.isEmpty() || !MoldingProductTypes.holdsFluids(current.orElseThrow().finalType())) return 0;
        MoldedPlasticData value = current.orElseThrow();
        PlasticCauldronLayout layout = PlasticCauldronLayout.of(value.finalType());
        List<FluidStack> fluids = new ArrayList<>(this.handle.fluids());
        int match = -1;
        for (int index = 0; index < fluids.size(); index++) {
            if (FluidStack.isSameFluidSameComponents(fluids.get(index), resource)) {
                match = index;
                break;
            }
        }
        int accepted;
        if (layout == null) {
            if (match < 0 && fluids.size() >= MoldedPlasticContents.MAX_STORED_FLUIDS) return 0;
            // 储罐保持原有的共享总容量；每种流体可以占一个有序层。
            int totalCapacity = Math.multiplyExact(value.capacity(), 1000);
            long total = fluids.stream().mapToLong(FluidStack::getAmount).sum();
            accepted = (int) Math.min((long) resource.getAmount(), (long) totalCapacity - total);
        } else {
            // 炼药锅每层独立限额，空层数量达到布局上限后拒绝新流体。
            if (match < 0 && fluids.size() >= layout.fluidLayers()) return 0;
            int stored = match < 0 ? 0 : fluids.get(match).getAmount();
            accepted = Math.min(resource.getAmount(), layerCapacity(value) - stored);
        }
        if (accepted <= 0) return 0;
        int layer = match;
        return this.handle.transfer(tx, nested -> {
            FluidStack filled = layer < 0
                ? resource.copyWithAmount(accepted)
                : fluids.get(layer).copyWithAmount(fluids.get(layer).getAmount() + accepted);
            if (layer >= 0) fluids.remove(layer);
            if (atBottom) {
                fluids.addFirst(filled);
            } else if (layer >= 0) {
                fluids.add(layer, filled);
            } else {
                fluids.add(filled);
            }
            this.handle.setFluids(fluids);
            return accepted;
        });
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        List<FluidStack> fluids = new ArrayList<>(this.handle.fluids());
        if (index < 0 || index >= fluids.size() || !resource.matches(fluids.get(index))) {
            return 0;
        }
        FluidStack stored = fluids.get(index);
        int extracted = Math.min(amount, stored.getAmount());
        if (extracted <= 0) {
            return 0;
        }
        return this.handle.transfer(tx, nested -> {
            if (extracted == stored.getAmount()) {
                fluids.remove(index);
            } else {
                fluids.set(index, stored.copyWithAmount(stored.getAmount() - extracted));
            }
            this.handle.setFluids(fluids);
            return extracted;
        });
    }

    @Override
    public FluidStack drain(int amount, FluidAction action) {
        return this.topAccess.drain(amount, action);
    }

    /** 储罐共享整体容量，炼药锅按层均分。 */
    private static int layerCapacity(MoldedPlasticData data) {
        PlasticCauldronLayout layout = PlasticCauldronLayout.of(data.finalType());
        return layout == null
            ? Math.multiplyExact(data.capacity(), 1000)
            : layout.fluidLayerCapacity(data.capacity());
    }

    private final class LayeredAccess implements MoldedFluidLayers {
        private final boolean bottom;
        private final int height;

        private LayeredAccess(boolean bottom, int height) {
            this.bottom = bottom;
            this.height = height;
        }

        private int layer(int index) {
            if (this.bottom) {
                return index;
            }
            List<FluidStack> fluids = MoldedPlasticFluidHandler.this.handle.fluids();
            long sum = 0;
            for (int layer = 0; layer < fluids.size(); layer++) {
                sum += fluids.get(layer).getAmount();
                if (sum >= this.height) {
                    return layer - index;
                }
            }
            return fluids.size() - 1 - index;
        }

        @Override
        public int getTanks() {
            return MoldedPlasticFluidHandler.this.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int index) {
            return MoldedPlasticFluidHandler.this.getFluidInTank(this.layer(index));
        }

        @Override
        public int getTankCapacity(int index) {
            return MoldedPlasticFluidHandler.this.getTankCapacity(index);
        }

        @Override
        public boolean isFluidValid(int index, FluidStack resource) {
            if (index < 0 || index >= this.getTanks() || resource.isEmpty()) {
                return false;
            }
            int physical = this.layer(index);
            if (MoldedPlasticFluidHandler.this.handle.fluids().isEmpty()) {
                return index == 0;
            }
            return physical >= 0 && MoldedPlasticFluidHandler.this.isFluidValid(physical, resource);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (!this.isFluidValid(index, resource.toStack(1))) {
                return 0;
            }
            int physical = Math.max(0, this.layer(index));
            return MoldedPlasticFluidHandler.this.insert(physical, resource, amount, tx);
        }

        @Override
        public int insert(FluidResource resource, int amount, TransactionContext tx) {
            return MoldedPlasticFluidHandler.this.insert(resource, amount, this.bottom, tx);
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            return MoldedPlasticFluidHandler.this.extract(this.layer(index), resource, amount, tx);
        }
    }
}
