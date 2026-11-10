package dev.anvilcraft.plasticraft.molding.product.storage;

import dev.anvilcraft.lib.v2.util.stack.UnlimitedItemStack;
import dev.anvilcraft.plasticraft.init.PlasticraftDataComponents;
import dev.anvilcraft.plasticraft.init.PlasticraftStorageTypes;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContentSummary;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContents;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.dubhe.anvilcraft.api.itemhandler.unlimited.OverflowDisposalItemStacksResourceHandler;
import dev.dubhe.anvilcraft.saved.storage.BaseStorage;
import dev.dubhe.anvilcraft.saved.storage.IStorageType;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/** 以成型内腔容量限制本体仓储，界面、自动化和合成共用同一份内容。 */
public final class MoldedPlasticCrateStorage extends BaseStorage<OverflowDisposalItemStacksResourceHandler> {
    private @Nullable MoldedPlasticContentSummary summary;

    public MoldedPlasticCrateStorage(UUID id) {
        super(id);
    }

    public void initializeCapacity(int stacks) {
        int space = Math.multiplyExact(stacks, 64);
        this.getItems().addSpaceSize(current -> space);
    }

    @Override
    protected OverflowDisposalItemStacksResourceHandler constructItemHandler(
        BiConsumer<Integer, UnlimitedItemStack> onContentsChanged
    ) {
        return new OverflowDisposalItemStacksResourceHandler(0) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return !stack.has(PlasticraftDataComponents.MOLDED_PLASTIC.get()) && super.isItemValid(slot, stack);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (stack.has(PlasticraftDataComponents.MOLDED_PLASTIC.get())) {
                    return stack;
                }
                return super.insertItem(slot, stack, simulate);
            }

            @Override
            public ItemStack insertItem(ItemStack stack, boolean simulate) {
                if (stack.has(PlasticraftDataComponents.MOLDED_PLASTIC.get())) {
                    return stack;
                }
                return super.insertItem(stack, simulate);
            }

            @Override
            protected void onContentsChanged(int index, UnlimitedItemStack original) {
                onContentsChanged.accept(index, original);
            }
        };
    }

    @Override
    protected void onContentsChanged(int index, UnlimitedItemStack original) {
        this.summary = null;
        super.onContentsChanged(index, original);
    }

    @Override
    public Holder<IStorageType<?>> getTypeHolder() {
        return PlasticraftStorageTypes.MOLDED_CRATE;
    }

    public List<MoldedPlasticContents.StoredItem> items() {
        List<MoldedPlasticContents.StoredItem> items = new ArrayList<>();
        for (int slot = 0; slot < this.getItems().size(); slot++) {
            ItemStack stack = this.getItems().getUnlimitedStackInSlot(slot).toStack();
            if (!stack.isEmpty()) {
                items.add(new MoldedPlasticContents.StoredItem(slot, stack));
            }
        }
        return items;
    }

    public MoldedPlasticContentSummary summary() {
        if (this.summary == null) {
            int capacity = this.getItems().getSpaceSize() / 64;
            MoldedPlasticContentSummary contents = MoldedPlasticContentSummary.create(
                MoldingProductTypes.CRATE_ID, capacity, this.items(), List.of()
            );
            this.summary = new MoldedPlasticContentSummary(
                contents.type(), capacity, Math.ceilDiv(this.getItems().getSpace(), 64), 0,
                contents.items(), contents.omittedItemTypes(), List.of(), 0
            );
        }
        return this.summary;
    }
}
