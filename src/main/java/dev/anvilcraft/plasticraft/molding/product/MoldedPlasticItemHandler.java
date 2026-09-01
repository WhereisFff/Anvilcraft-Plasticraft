package dev.anvilcraft.plasticraft.molding.product;

import dev.anvilcraft.plasticraft.init.PlasticraftDataComponents;
import dev.anvilcraft.plasticraft.molding.product.storage.MoldedPlasticStorageHandle;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 无菜单箱子与塑料炼药锅使用的稀疏物品能力。
 *
 * <p>箱子每格上限即物品自身堆叠上限，槽数等于容量；炼药锅按 {@link PlasticCauldronLayout} 取槽数与上限，
 * 并沿用硬化树脂炼药锅的槽序语义：只能向输入槽插入，输出槽与输入槽都允许取出。
 */
public final class MoldedPlasticItemHandler implements IItemHandler {
    private final MoldedPlasticStorageHandle handle;
    private final Runnable onOutputChanged;

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
        this.handle = new MoldedPlasticStorageHandle(data, update);
        this.onOutputChanged = onOutputChanged;
    }

    @Override
    public int getSlots() {
        return this.handle.data().map(MoldedPlasticItemHandler::slotCount).orElse(0);
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < this.getSlots() ? this.handle.itemInSlot(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return this.insert(slot, stack, simulate, false);
    }

    private ItemStack insert(int slot, ItemStack stack, boolean simulate, boolean intoOutput) {
        if (stack.isEmpty()
            || slot < 0
            || slot >= this.getSlots()
            || stack.has(PlasticraftDataComponents.MOLDED_PLASTIC.get())) {
            return stack;
        }
        Optional<MoldedPlasticData> current = this.handle.data();
        if (current.isEmpty()) return stack;
        MoldedPlasticData value = current.orElseThrow();
        if (!this.accepts(slot, stack, intoOutput)) return stack;
        ItemStack existing = this.handle.itemInSlot(slot);
        int limit = slotLimit(value, slot, stack);
        int accepted = existing.isEmpty()
            ? Math.min(stack.getCount(), limit)
            : ItemStack.isSameItemSameComponents(existing, stack)
                ? Math.min(stack.getCount(), limit - existing.getCount())
                : 0;
        if (accepted <= 0) return stack.copy();
        if (!simulate) {
            ItemStack replacement = existing.isEmpty()
                ? stack.copyWithCount(accepted)
                : existing.copyWithCount(existing.getCount() + accepted);
            this.handle.setItem(slot, replacement);
            if (isOutputSlot(value, slot)) this.onOutputChanged.run();
        }
        return stack.copyWithCount(stack.getCount() - accepted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < 0 || slot >= this.getSlots()) return ItemStack.EMPTY;
        ItemStack existing = this.handle.itemInSlot(slot);
        if (existing.isEmpty()) return ItemStack.EMPTY;
        // 多倍堆叠槽单次最多取出一个完整堆叠，与本体大型锅输入槽的取出语义一致
        int available = Math.min(existing.getCount(), existing.getMaxStackSize());
        ItemStack extracted = existing.copyWithCount(Math.min(amount, available));
        if (!simulate) {
            int remaining = existing.getCount() - extracted.getCount();
            this.handle.setItem(slot, remaining > 0 ? existing.copyWithCount(remaining) : ItemStack.EMPTY);
            this.handle.data().ifPresent(value -> {
                if (isOutputSlot(value, slot)) this.onOutputChanged.run();
            });
        }
        return extracted;
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot >= 0 && slot < this.getSlots()
            ? this.handle.data().map(value -> slotLimit(value, slot, ItemStack.EMPTY)).orElse(0)
            : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return this.accepts(slot, stack, false);
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
        return MoldingProductTypes.isChest(data.finalType()) ? data.capacity() : 0;
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

    /** 输出槽区间视图。 */
    public IItemHandler outputView() {
        return new SlotRangeView(true);
    }

    /** 输入槽区间视图。 */
    public IItemHandler inputView() {
        return new SlotRangeView(false);
    }

    /**
     * 把「先输出后输入」的平铺槽序切成两段，供 AnvilCraft 配方缓存分别取输入与输出。
     * 非炼药锅制品没有布局，两段都报 0 槽，配方查询自然落空。
     *
     * <p>输出视图只交给配方系统，因此它必须能写入输出槽：AnvilLib 的物品缓存在放置产物前会先问
     * {@code isItemValid}，平铺能力上的「输出槽拒绝插入」是给外部自动化用的，套到这里会让配方产物凭空消失。
     */
    private final class SlotRangeView implements IItemHandler {
        private final boolean outputs;

        private SlotRangeView(boolean outputs) {
            this.outputs = outputs;
        }

        private int offset() {
            PlasticCauldronLayout layout = MoldedPlasticItemHandler.this.layout();
            return this.outputs || layout == null ? 0 : layout.outputSlots();
        }

        @Override
        public int getSlots() {
            PlasticCauldronLayout layout = MoldedPlasticItemHandler.this.layout();
            if (layout == null) return 0;
            return this.outputs ? layout.outputSlots() : layout.inputSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < this.getSlots()
                ? MoldedPlasticItemHandler.this.getStackInSlot(this.offset() + slot)
                : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot >= 0 && slot < this.getSlots()
                ? MoldedPlasticItemHandler.this.insert(this.offset() + slot, stack, simulate, this.outputs)
                : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot >= 0 && slot < this.getSlots()
                ? MoldedPlasticItemHandler.this.extractItem(this.offset() + slot, amount, simulate)
                : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot >= 0 && slot < this.getSlots()
                ? MoldedPlasticItemHandler.this.getSlotLimit(this.offset() + slot)
                : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0
                && slot < this.getSlots()
                && MoldedPlasticItemHandler.this.accepts(this.offset() + slot, stack, this.outputs);
        }
    }
}
