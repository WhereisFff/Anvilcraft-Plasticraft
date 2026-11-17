package dev.anvilcraft.plasticraft.molding.product.storage;

import dev.anvilcraft.lib.v2.util.UnlimitedItemStack;
import dev.anvilcraft.plasticraft.init.PlasticraftDataComponents;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContentSummary;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContents;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.dubhe.anvilcraft.api.itemhandler.unlimited.OverflowDisposalItemStacksResourceHandler;
import dev.dubhe.anvilcraft.saved.storage.CrateStorage;
import it.unimi.dsi.fastutil.ints.IntObjectBiConsumer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 以成型内腔容量限制本体仓储，界面、自动化和合成共用同一份内容。 */
public final class MoldedPlasticCrateStorage extends CrateStorage {
    private @Nullable MoldedPlasticContentSummary summary;

    public MoldedPlasticCrateStorage(UUID id) {
        super(id);
    }

    public void initializeCapacity(int space) {
        this.getItems().addSpaceSize(current -> space);
    }

    @Override
    protected OverflowDisposalItemStacksResourceHandler constructItemHandler(
        IntObjectBiConsumer<UnlimitedItemStack> onContentsChanged
    ) {
        return new OverflowDisposalItemStacksResourceHandler(0) {
            @Override
            public boolean isValid(int index, ItemResource resource) {
                return !resource.has(PlasticraftDataComponents.MOLDED_PLASTIC.get()) && super.isValid(index, resource);
            }

            @Override
            public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
                return resource.has(PlasticraftDataComponents.MOLDED_PLASTIC.get())
                    ? 0 : super.insert(index, resource, amount, transaction);
            }

            @Override
            public int insert(ItemResource resource, int amount, TransactionContext transaction) {
                return resource.has(PlasticraftDataComponents.MOLDED_PLASTIC.get())
                    ? 0 : super.insert(resource, amount, transaction);
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

    public List<MoldedPlasticContents.StoredItem> items() {
        List<MoldedPlasticContents.StoredItem> items = new ArrayList<>();
        for (int slot = 0; slot < this.getItems().size(); slot++) {
            ItemStack stack = ItemUtil.getStack(this.getItems(), slot);
            if (!stack.isEmpty()) {
                items.add(new MoldedPlasticContents.StoredItem(slot, stack));
            }
        }
        return items;
    }

    public void invalidateSummary() {
        this.summary = null;
    }

    public MoldedPlasticContentSummary summary() {
        if (this.summary == null) {
            int capacity = this.getItems().getSpaceSize();
            MoldedPlasticContentSummary contents = MoldedPlasticContentSummary.create(
                MoldingProductTypes.CRATE_ID, capacity, this.items(), List.of()
            );
            this.summary = new MoldedPlasticContentSummary(
                contents.type(), capacity, this.getItems().getSpace(), 0,
                contents.items(), contents.omittedItemTypes(), List.of(), 0
            );
        }
        return this.summary;
    }
}
