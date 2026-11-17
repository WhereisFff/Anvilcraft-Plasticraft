package dev.anvilcraft.plasticraft.molding.product;

import dev.anvilcraft.plasticraft.init.PlasticraftDataComponents;
import dev.anvilcraft.plasticraft.molding.product.storage.MoldedPlasticCrateStorage;
import dev.anvilcraft.plasticraft.molding.product.storage.MoldedPlasticStorageHandle;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.dubhe.anvilcraft.api.itemhandler.unlimited.SpaceSizeItemStacksResourceHandler;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 塑料板条箱与炼药锅的物品能力。
 *
 * <p>板条箱委托本体仓储按类型合并；炼药锅按 {@link PlasticCauldronLayout} 取槽数与上限，
 * 并沿用硬化树脂炼药锅的槽序语义：只能向输入槽插入，输出槽与输入槽都允许取出。
 */
public final class MoldedPlasticItemHandler implements MoldedItemSlots {
    private final MoldedPlasticStorageHandle handle;
    private final Runnable onOutputChanged;
    private final BooleanSupplier dispose;

    private final List<ItemStack> recipeOutputs = new ArrayList<>();
    private final SnapshotJournal<List<ItemStack>> recipeOutputChanges = new SnapshotJournal<>() {
        @Override
        protected List<ItemStack> createSnapshot() {
            return List.copyOf(MoldedPlasticItemHandler.this.recipeOutputs);
        }

        @Override
        protected void revertToSnapshot(List<ItemStack> original) {
            MoldedPlasticItemHandler.this.recipeOutputs.clear();
            MoldedPlasticItemHandler.this.recipeOutputs.addAll(original);
        }
    };

    private final SnapshotJournal<Boolean> outputChanges = new SnapshotJournal<>() {
        @Override
        protected Boolean createSnapshot() {
            return true;
        }

        @Override
        protected void revertToSnapshot(Boolean original) {
            // 输出信号仅在最外层事务提交时触发。
        }

        @Override
        protected void onRootCommit(Boolean original) {
            MoldedPlasticItemHandler.this.onOutputChanged.run();
        }
    };

    public MoldedPlasticItemHandler(ItemAccess access) {
        this.handle = new MoldedPlasticStorageHandle(access);
        this.onOutputChanged = () -> {};
        this.dispose = () -> false;
    }

    public MoldedPlasticItemHandler(
        Supplier<Optional<MoldedPlasticData>> data,
        Consumer<MoldedPlasticData> update
    ) {
        this(data, update, () -> {
        });
    }

    public MoldedPlasticItemHandler(
        Supplier<Optional<MoldedPlasticData>> data,
        Consumer<MoldedPlasticData> update,
        Runnable onOutputChanged
    ) {
        this(data, update, onOutputChanged, () -> false);
    }

    public MoldedPlasticItemHandler(
        Supplier<Optional<MoldedPlasticData>> data,
        Consumer<MoldedPlasticData> update,
        Runnable onOutputChanged,
        BooleanSupplier dispose
    ) {
        this(new MoldedPlasticStorageHandle(data, update), onOutputChanged, dispose);
    }

    public MoldedPlasticItemHandler(MoldedPlasticStorageHandle handle, Runnable onOutputChanged, BooleanSupplier dispose) {
        this.handle = handle;
        this.onOutputChanged = onOutputChanged;
        this.dispose = dispose;
    }

    @Override
    public int getSlots() {
        if (this.handle.isCrate()) {
            return this.crateStorage(false).map(storage -> storage.getItems().size() + 1).orElse(0);
        }
        return this.handle.data().map(MoldedPlasticItemHandler::slotCount).orElse(0);
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (this.handle.isCrate()) {
            return this.crateStorage(false).filter(storage -> slot >= 0 && slot < storage.getItems().size())
                .map(storage -> ItemUtil.getStack(storage.getItems(), slot))
                .orElse(ItemStack.EMPTY);
        }
        return slot >= 0 && slot < this.getSlots() ? this.handle.itemInSlot(slot) : ItemStack.EMPTY;
    }

    @Override
    public int insert(int slot, ItemResource resource, int amount, TransactionContext tx) {
        return this.insert(slot, resource, amount, tx, false);
    }

    @Override
    public int insert(ItemResource resource, int amount, TransactionContext tx) {
        if (this.handle.isCrate()) {
            return this.crateTransfer(-1, resource, amount, tx, true);
        }
        return MoldedItemSlots.super.insert(resource, amount, tx);
    }

    @Override
    public int extract(ItemResource resource, int amount, TransactionContext tx) {
        if (this.handle.isCrate()) {
            return this.crateTransfer(-1, resource, amount, tx, false);
        }
        return MoldedItemSlots.super.extract(resource, amount, tx);
    }

    private int crateTransfer(int slot, ItemResource resource, int amount, TransactionContext tx, boolean insert) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount == 0 || (insert && resource.has(PlasticraftDataComponents.MOLDED_PLASTIC.get()))) {
            return 0;
        }
        return this.handle.transfer(tx, nested -> this.crateStorage(insert).map(storage -> {
            var items = storage.getItems();
            int moved;
            if (slot > items.size() || (!insert && slot == items.size())) {
                return 0;
            }
            if (insert) {
                moved = slot < 0 || slot == items.size() ? items.insert(resource, amount, nested) : items.insert(slot, resource, amount, nested);
            } else {
                moved = slot < 0 ? items.extract(resource, amount, nested) : items.extract(slot, resource, amount, nested);
            }
            if (moved > 0) {
                storage.invalidateSummary();
                this.handle.refreshCrate();
            }
            return moved;
        }).orElse(0));
    }

    private int insert(int slot, ItemResource resource, int amount, TransactionContext tx, boolean intoOutput) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (slot < 0 || amount == 0 || resource.has(PlasticraftDataComponents.MOLDED_PLASTIC.get())) {
            return 0;
        }
        if (this.handle.isCrate()) {
            return this.crateTransfer(slot, resource, amount, tx, true);
        }
        if (!this.accepts(slot, resource.toStack(), intoOutput)) {
            return 0;
        }
        ItemStack existing = this.getStackInSlot(slot);
        if (!existing.isEmpty() && !resource.matches(existing)) {
            return 0;
        }
        int accepted = Math.min(amount, this.getCapacityAsInt(slot, resource) - existing.getCount());
        if (accepted <= 0) {
            return 0;
        }
        return this.handle.transfer(tx, nested -> {
            this.handle.setItem(slot, resource.toStack(existing.getCount() + accepted));
            if (isOutputSlot(this.handle.data().orElseThrow(), slot)) {
                this.outputChanges.updateSnapshots(nested);
            }
            return accepted;
        });
    }

    @Override
    public int extract(int slot, ItemResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (slot < 0 || amount == 0) {
            return 0;
        }
        if (this.handle.isCrate()) {
            return this.crateTransfer(slot, resource, amount, tx, false);
        }
        ItemStack current = this.getStackInSlot(slot);
        int extracted = Math.min(amount, current.getCount());
        if (extracted <= 0 || !resource.matches(current)) {
            return 0;
        }
        return this.handle.transfer(tx, nested -> {
            this.handle.setItem(slot, current.copyWithCount(current.getCount() - extracted));
            if (isOutputSlot(this.handle.data().orElseThrow(), slot)) {
                this.outputChanges.updateSnapshots(nested);
            }
            return extracted;
        });
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.getCapacityAsInt(slot, this.getResource(slot));
    }

    @Override
    public long getCapacityAsLong(int slot, ItemResource resource) {
        if (this.handle.isCrate()) {
            return this.crateStorage(false).map(storage -> {
                var items = storage.getItems();
                if (slot == items.size()) {
                    int space = Math.max(0, items.getSpaceSize() - items.getSpace());
                    return resource.isEmpty() ? (long) space : (long) SpaceSizeItemStacksResourceHandler.computeCount(resource, space);
                }
                return slot >= 0 && slot < items.size() ? items.getCapacityAsLong(slot, resource) : 0L;
            }).orElse(0L);
        }
        return slot >= 0 && slot < this.getSlots()
            ? this.handle.data().map(value -> slotLimit(value, slot, resource.toStack())).orElse(0)
            : 0;
    }

    /** 配方缓存与熔岩修复需要原位更新，避免先清空再写入时触发出口或丢失超大堆叠。 */
    public void setStackInSlot(int slot, ItemStack stack) {
        if (this.handle.isCrate()) {
            throw new IllegalStateException("Crate contents must be changed through warehouse insertion and extraction");
        }
        Optional<MoldedPlasticData> current = this.handle.data();
        if (current.isEmpty() || slot < 0 || slot >= this.getSlots()) throw new IndexOutOfBoundsException(slot);
        if (!stack.isEmpty() && stack.getCount() > slotLimit(current.orElseThrow(), slot, stack)) {
            throw new IllegalArgumentException("Molded cauldron item exceeds its slot limit");
        }
        this.handle.setItem(slot, stack.copy());
        if (isOutputSlot(current.orElseThrow(), slot)) this.onOutputChanged.run();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (this.handle.isCrate()) {
            return this.crateStorage(false).map(storage -> slot >= 0 && slot <= storage.getItems().size()
                && !stack.has(PlasticraftDataComponents.MOLDED_PLASTIC.get())).orElse(false);
        }
        return this.accepts(slot, stack, false);
    }

    public Optional<MoldedPlasticCrateStorage> crateStorage(boolean create) {
        return this.handle.crateStorage(create).map(storage -> {
            storage.getItems().setDispose(this.dispose.getAsBoolean());
            return storage;
        });
    }

    public void refreshCrate() {
        if (this.handle.data().flatMap(MoldedPlasticData::storageId).isPresent() && this.handle.isCrate()) {
            this.crateStorage(false);
            this.handle.refreshCrate();
        }
    }

    private boolean accepts(int slot, ItemStack stack, boolean intoOutput) {
        if (slot < 0 || slot >= this.getSlots() || stack.isEmpty()
            || stack.has(PlasticraftDataComponents.MOLDED_PLASTIC.get())) {
            return false;
        }
        Optional<MoldedPlasticData> current = this.handle.data();
        if (current.isEmpty()) return false;
        MoldedPlasticData value = current.orElseThrow();
        if (intoOutput || isOutputSlot(value, slot)) return intoOutput;
        ItemStack existing = this.handle.itemInSlot(slot);
        if (!existing.isEmpty()) return ItemStack.isSameItemSameComponents(existing, stack);
        PlasticCauldronLayout layout = PlasticCauldronLayout.of(value.finalType());
        if (layout == null) return true;
        int inputStart = layout.outputSlots();
        int inputEnd = inputStart + layout.inputSlots();
        for (int other = inputStart; other < inputEnd; other++) {
            if (other == slot) continue;
            ItemStack otherStack = this.handle.itemInSlot(other);
            if (!otherStack.isEmpty() && ItemStack.isSameItemSameComponents(otherStack, stack)) return false;
        }
        return true;
    }

    private static int slotCount(MoldedPlasticData data) {
        PlasticCauldronLayout layout = PlasticCauldronLayout.of(data.finalType());
        if (layout != null) return layout.totalSlots();
        return MoldingProductTypes.isCrate(data.finalType()) ? data.capacity() : 0;
    }

    private static boolean isOutputSlot(MoldedPlasticData data, int slot) {
        PlasticCauldronLayout layout = PlasticCauldronLayout.of(data.finalType());
        return layout != null && layout.isOutputSlot(slot);
    }

    private static int slotLimit(MoldedPlasticData data, int slot, ItemStack stack) {
        PlasticCauldronLayout layout = PlasticCauldronLayout.of(data.finalType());
        return layout == null
            ? Math.min(stack.isEmpty() ? 64 : stack.getMaxStackSize(), 64)
            : layout.slotLimit(slot, stack);
    }

    private @Nullable PlasticCauldronLayout layout() {
        return this.handle.data().map(value -> PlasticCauldronLayout.of(value.finalType())).orElse(null);
    }

    public MoldedItemSlots outputView() {
        return new SlotView(true, -1, false);
    }

    public MoldedItemSlots inputView() {
        return new SlotView(false, -1, false);
    }

    public MoldedItemSlots recipeView(boolean outputs, int selected) {
        return new SlotView(outputs, selected, false);
    }

    public void captureRecipeOutputs() {
        this.recipeOutputs.clear();
        MoldedItemSlots outputs = this.outputView();
        for (int slot = 0; slot < outputs.size(); slot++) {
            this.recipeOutputs.add(outputs.getStackInSlot(slot).copy());
        }
    }

    public void clearRecipeOutputs() {
        this.recipeOutputs.clear();
    }

    public MoldedItemSlots outputRecipeView(int selected) {
        return new SlotView(true, selected, true);
    }

    private final class SlotView implements MoldedItemSlots {
        private final boolean outputs;
        private final int selected;
        private final boolean originalOutputs;

        private SlotView(boolean outputs, int selected, boolean originalOutputs) {
            this.outputs = outputs;
            this.selected = selected;
            this.originalOutputs = originalOutputs;
        }

        private int slot(int index) {
            int mapped = this.selected < 0 ? index : index == 0 ? this.selected : index <= this.selected ? index - 1 : index;
            PlasticCauldronLayout layout = MoldedPlasticItemHandler.this.layout();
            return mapped + (this.outputs || layout == null ? 0 : layout.outputSlots());
        }

        @Override
        public int getSlots() {
            PlasticCauldronLayout layout = MoldedPlasticItemHandler.this.layout();
            return layout == null ? 0 : this.outputs ? layout.outputSlots() : layout.inputSlots();
        }

        @Override
        public ItemStack getStackInSlot(int index) {
            int slot = this.slot(index);
            ItemStack current = MoldedPlasticItemHandler.this.getStackInSlot(slot);
            if (!this.originalOutputs) {
                return current;
            }
            ItemStack original = MoldedPlasticItemHandler.this.recipeOutputs.get(slot);
            return ItemStack.isSameItemSameComponents(original, current)
                ? current.copyWithCount(Math.min(original.getCount(), current.getCount())) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int index) {
            return MoldedPlasticItemHandler.this.getSlotLimit(this.slot(index));
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return MoldedPlasticItemHandler.this.getCapacityAsLong(this.slot(index), resource);
        }

        @Override
        public boolean isItemValid(int index, ItemStack stack) {
            return MoldedPlasticItemHandler.this.accepts(this.slot(index), stack, this.outputs);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
            return MoldedPlasticItemHandler.this.insert(this.slot(index), resource, amount, tx, this.outputs);
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
            ItemStack current = this.getStackInSlot(index);
            if (!resource.matches(current)) {
                return 0;
            }
            int slot = this.slot(index);
            int extracted = MoldedPlasticItemHandler.this.extract(slot, resource, Math.min(amount, current.getCount()), tx);
            if (extracted > 0 && this.originalOutputs) {
                // 多个选槽视图必须共用本轮原料快照，回滚时才能恢复首次扣量前的物品数。
                MoldedPlasticItemHandler.this.recipeOutputChanges.updateSnapshots(tx);
                MoldedPlasticItemHandler.this.recipeOutputs.set(slot, current.copyWithCount(current.getCount() - extracted));
            }
            return extracted;
        }
    }
}
