package dev.anvilcraft.plasticraft.molding.product.storage;

import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContentSummary;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContents;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.dubhe.anvilcraft.saved.storage.Storages;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 板条箱、储罐、摘要和回收物品共用的仓储句柄。 */
public final class MoldedPlasticStorageHandle {
    private final Supplier<Optional<MoldedPlasticData>> data;
    private final Consumer<MoldedPlasticData> update;

    public MoldedPlasticStorageHandle(
        Supplier<Optional<MoldedPlasticData>> data,
        Consumer<MoldedPlasticData> update
    ) {
        this.data = data;
        this.update = update;
    }

    public Optional<MoldedPlasticData> data() {
        return this.data.get();
    }

    public List<MoldedPlasticContents.StoredItem> items() {
        if (this.isCrate()) {
            return this.crateStorage(false).map(MoldedPlasticCrateStorage::items).orElse(List.of());
        }
        return this.storage()
            .map(MoldedPlasticStorage::items)
            .orElseGet(() -> this.data.get().map(value -> value.contents().items()).orElse(List.of()));
    }

    public List<FluidStack> fluids() {
        return this.storage()
            .map(MoldedPlasticStorage::fluids)
            .orElseGet(() -> this.data.get().map(value -> value.contents().fluids()).orElse(List.of()));
    }

    public ItemStack itemInSlot(int slot) {
        if (this.isCrate()) {
            return this.crateStorage(false).map(storage -> storage.getItems().getStackInSlot(slot)).orElse(ItemStack.EMPTY);
        }
        return this.storage()
            .map(storage -> storage.itemInSlot(slot))
            .orElseGet(() -> this.data.get()
                .flatMap(value -> value.contents().items().stream()
                    .filter(item -> item.slot() == slot)
                    .findFirst()
                    .map(MoldedPlasticContents.StoredItem::stack))
                .orElse(ItemStack.EMPTY));
    }

    public void setItem(int slot, ItemStack stack) {
        if (this.isCrate()) {
            throw new IllegalStateException("Crate contents must be changed through warehouse insertion and extraction");
        }
        Optional<MoldedPlasticData> current = this.data.get();
        if (current.isEmpty()) return;
        List<MoldedPlasticContents.StoredItem> items = new ArrayList<>(this.items());
        items.removeIf(item -> item.slot() == slot);
        if (!stack.isEmpty()) items.add(new MoldedPlasticContents.StoredItem(slot, stack));
        this.commit(current.orElseThrow(), items, this.fluids());
    }

    public void setFluids(List<FluidStack> fluids) {
        Optional<MoldedPlasticData> current = this.data.get();
        if (current.isEmpty()) return;
        this.commit(current.orElseThrow(), this.items(), fluids);
    }

    public MoldedPlasticContentSummary summary() {
        if (this.isCrate()) {
            return this.crateStorage(false).map(MoldedPlasticCrateStorage::summary)
                .orElseGet(() -> this.data.get().map(MoldedPlasticData::summary).orElse(MoldedPlasticContentSummary.EMPTY));
        }
        return this.data.get()
            .map(value -> MoldedPlasticContentSummary.create(
                value.finalType(),
                value.capacity(),
                this.items(),
                this.fluids()
            ))
            .orElse(MoldedPlasticContentSummary.EMPTY);
    }

    public boolean isCrate() {
        return this.data.get().filter(value -> MoldingProductTypes.isCrate(value.finalType())).isPresent();
    }

    public Optional<MoldedPlasticCrateStorage> crateStorage(boolean create) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) {
            return Optional.empty();
        }
        Optional<MoldedPlasticData> current = this.data.get().filter(value -> MoldingProductTypes.isCrate(value.finalType()));
        if (current.isEmpty()) {
            return Optional.empty();
        }
        MoldedPlasticData value = current.orElseThrow();
        if (value.storageId().isEmpty() && !create) {
            MoldedPlasticCrateStorage empty = new MoldedPlasticCrateStorage(new UUID(0, 0));
            empty.initializeCapacity(value.capacity());
            return Optional.of(empty);
        }
        UUID id = value.storageId().orElseGet(UUID::randomUUID);
        MoldedPlasticCrateStorage storage = Storages.get().getOrCreate(id, MoldedPlasticCrateStorage.class);
        storage.initializeCapacity(value.capacity());
        if (value.storageId().isEmpty()) {
            this.update.accept(value.withStorageId(Optional.of(id)).withSummary(storage.summary()));
        }
        return Optional.of(storage);
    }

    public void refreshCrate() {
        Optional<MoldedPlasticData> current = this.data.get();
        if (current.isEmpty() || current.orElseThrow().storageId().isEmpty() || !this.isCrate()) {
            return;
        }
        this.crateStorage(false).ifPresent(storage -> {
            MoldedPlasticData value = this.data.get().orElseThrow();
            MoldedPlasticData updated = value.withSummary(storage.summary());
            if (updated != value) {
                this.update.accept(updated);
            }
        });
    }

    private Optional<MoldedPlasticStorage> storage() {
        Optional<UUID> id = this.data.get().flatMap(MoldedPlasticData::storageId);
        if (id.isEmpty()) return Optional.empty();
        return PlasticraftStorages.tryGet().flatMap(store -> store.get(id.orElseThrow()));
    }

    private void commit(
        MoldedPlasticData current,
        List<MoldedPlasticContents.StoredItem> items,
        List<FluidStack> fluids
    ) {
        Optional<PlasticraftStorages> store = PlasticraftStorages.tryGet();
        if (store.isEmpty()) {
            this.update.accept(current.withContents(new MoldedPlasticContents(
                items,
                fluids,
                current.contents().trayComponents()
            )).withSummary(MoldedPlasticContentSummary.create(
                current.finalType(),
                current.capacity(),
                items,
                fluids
            )));
            return;
        }
        boolean empty = items.isEmpty() && fluids.isEmpty();
        UUID id = current.storageId().orElseGet(UUID::randomUUID);
        if (empty) {
            store.orElseThrow().remove(id);
            this.update.accept(current
                .withStorageId(Optional.empty())
                .withSummary(MoldedPlasticContentSummary.EMPTY)
                .withContents(new MoldedPlasticContents(
                    List.of(),
                    List.of(),
                    current.contents().trayComponents()
                )));
            return;
        }
        MoldedPlasticStorage storage = store.orElseThrow().getOrCreate(id);
        storage.setItems(items);
        storage.setFluids(fluids);
        store.orElseThrow().markDirty();
        boolean renderFluids = MoldingProductTypes.holdsFluids(current.finalType()) && !current.limitOverride();
        this.update.accept(current
            .withStorageId(Optional.of(id))
            .withSummary(MoldedPlasticContentSummary.create(
                current.finalType(),
                current.capacity(),
                items,
                fluids
            ))
            .withContents(new MoldedPlasticContents(
                List.of(),
                renderFluids ? fluids : List.of(),
                current.contents().trayComponents()
            )));
    }
}
