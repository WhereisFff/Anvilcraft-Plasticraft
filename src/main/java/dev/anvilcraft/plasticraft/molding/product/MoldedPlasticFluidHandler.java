package dev.anvilcraft.plasticraft.molding.product;

import dev.anvilcraft.plasticraft.molding.product.storage.MoldedPlasticStorageHandle;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.dubhe.anvilcraft.api.fluid.LargeCauldronFluidHandler;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

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
public final class MoldedPlasticFluidHandler implements IFluidHandlerItem {
    private final MoldedPlasticStorageHandle handle;
    private final Supplier<ItemStack> container;
    private final IFluidHandler bottomAccess = new BottomLayerView();

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
        this.handle = new MoldedPlasticStorageHandle(data, update);
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
    public IFluidHandler bottomAccess() {
        return this.bottomAccess;
    }

    public IFluidHandler topAccess() {
        return new LayeredAccess(LargeCauldronFluidHandler.TOTAL_CAPACITY);
    }

    public IFluidHandler sideAccess(int accessibleAmount) {
        return new LayeredAccess(accessibleAmount);
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

    /** 视图每次读取最新仓储，复用本体按点击高度选层及向下查找的规则。 */
    private final class LayeredAccess implements IFluidHandler {
        private final int accessibleAmount;

        private LayeredAccess(int accessibleAmount) {
            this.accessibleAmount = accessibleAmount;
        }

        private LargeCauldronFluidHandler snapshot() {
            LargeCauldronFluidHandler handler = new LargeCauldronFluidHandler(() -> {});
            handler.setFluids(MoldedPlasticFluidHandler.this.handle.fluids());
            return handler;
        }

        private IFluidHandler view(LargeCauldronFluidHandler handler) {
            return handler.sideAccess(this.accessibleAmount);
        }

        @Override
        public int getTanks() {
            return MoldedPlasticFluidHandler.this.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return this.view(this.snapshot()).getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return MoldedPlasticFluidHandler.this.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return MoldedPlasticFluidHandler.this.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return MoldedPlasticFluidHandler.this.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            LargeCauldronFluidHandler handler = this.snapshot();
            FluidStack result = this.view(handler).drain(resource, action);
            if (action.execute() && !result.isEmpty()) MoldedPlasticFluidHandler.this.setFluids(handler.copyFluids());
            return result;
        }

        @Override
        public FluidStack drain(int amount, FluidAction action) {
            LargeCauldronFluidHandler handler = this.snapshot();
            FluidStack result = this.view(handler).drain(amount, action);
            if (action.execute() && !result.isEmpty()) MoldedPlasticFluidHandler.this.setFluids(handler.copyFluids());
            return result;
        }
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
        return fluids.stream().anyMatch(stored -> FluidStack.isSameFluidSameComponents(stored, stack))
            || fluids.size() < this.getTanks();
    }

    public void discardFluids() {
        this.handle.setFluids(List.of());
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return this.fillInternal(resource, action, false);
    }

    /**
     * 按容器形态分别实现储罐和炼药锅的填充语义。炼药锅的普通入口从顶部追加，
     * {@code bottomAccess()} 则把新层放到底部；储罐继续使用整个容器的总容量。
     */
    private int fillInternal(FluidStack resource, FluidAction action, boolean atBottom) {
        if (resource == null || resource.isEmpty()) return 0;
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
        if (action.execute()) {
            FluidStack filled = match < 0
                ? resource.copyWithAmount(accepted)
                : fluids.get(match).copyWithAmount(fluids.get(match).getAmount() + accepted);
            if (match >= 0) fluids.remove(match);
            if (atBottom) {
                fluids.addFirst(filled);
            } else if (match >= 0) {
                fluids.add(match, filled);
            } else {
                fluids.add(filled);
            }
            this.handle.setFluids(fluids);
        }
        return accepted;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource == null || resource.isEmpty()) return FluidStack.EMPTY;
        List<FluidStack> fluids = this.handle.fluids();
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
        List<FluidStack> fluids = this.handle.fluids();
        return fluids.isEmpty()
            ? FluidStack.EMPTY
            : this.drainIndex(fluids.size() - 1, Math.min(amount, fluids.getLast().getAmount()), action);
    }

    private FluidStack drainIndex(int index, int amount, FluidAction action) {
        List<FluidStack> fluids = new ArrayList<>(this.handle.fluids());
        FluidStack stored = fluids.get(index);
        FluidStack extracted = stored.copyWithAmount(amount);
        if (action.execute()) {
            fluids.set(index, stored.copyWithAmount(stored.getAmount() - amount));
            fluids.removeIf(FluidStack::isEmpty);
            this.handle.setFluids(fluids);
        }
        return extracted;
    }

    /** 储罐共享整体容量，炼药锅按层均分。 */
    private static int layerCapacity(MoldedPlasticData data) {
        PlasticCauldronLayout layout = PlasticCauldronLayout.of(data.finalType());
        return layout == null
            ? Math.multiplyExact(data.capacity(), 1000)
            : layout.fluidLayerCapacity(data.capacity());
    }

    /** 以底层优先顺序暴露分层流体能力，与大型炼药锅的 bottomAccess 保持一致。 */
    private final class BottomLayerView implements IFluidHandler {
        @Override
        public int getTanks() {
            return MoldedPlasticFluidHandler.this.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return MoldedPlasticFluidHandler.this.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return MoldedPlasticFluidHandler.this.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return MoldedPlasticFluidHandler.this.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return MoldedPlasticFluidHandler.this.fillInternal(resource, action, true);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource == null || resource.isEmpty()) return FluidStack.EMPTY;
            List<FluidStack> fluids = MoldedPlasticFluidHandler.this.handle.fluids();
            for (int index = 0; index < fluids.size(); index++) {
                FluidStack stored = fluids.get(index);
                if (FluidStack.isSameFluidSameComponents(stored, resource)) {
                    return MoldedPlasticFluidHandler.this.drainIndex(
                        index,
                        Math.min(resource.getAmount(), stored.getAmount()),
                        action
                    );
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int amount, FluidAction action) {
            if (amount <= 0) return FluidStack.EMPTY;
            List<FluidStack> fluids = MoldedPlasticFluidHandler.this.handle.fluids();
            if (fluids.isEmpty()) return FluidStack.EMPTY;
            FluidStack bottom = fluids.getFirst();
            return MoldedPlasticFluidHandler.this.drainIndex(
                0,
                Math.min(amount, bottom.getAmount()),
                action
            );
        }
    }
}
