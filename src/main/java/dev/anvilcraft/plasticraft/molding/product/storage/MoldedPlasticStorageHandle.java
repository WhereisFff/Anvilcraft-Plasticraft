package dev.anvilcraft.plasticraft.molding.product.storage;

import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContentSummary;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContents;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.dubhe.anvilcraft.saved.storage.Storages;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/** 板条箱、储罐、摘要和回收物品共用的仓储句柄。 */
public final class MoldedPlasticStorageHandle {
    private final Supplier<Optional<MoldedPlasticData>> data;
    private final @Nullable Consumer<MoldedPlasticData> update;
    private final @Nullable ItemAccess access;
    private @Nullable TransactionContext transfer;
    private @Nullable MoldedPlasticData working;
    private @Nullable MoldedPlasticData pending;
    private final SnapshotJournal<@Nullable MoldedPlasticData> carrier = new SnapshotJournal<>() {
        @Override
        protected @Nullable MoldedPlasticData createSnapshot() {
            return MoldedPlasticStorageHandle.this.pending;
        }

        @Override
        protected void revertToSnapshot(@Nullable MoldedPlasticData snapshot) {
            MoldedPlasticStorageHandle.this.pending = snapshot;
        }

        @Override
        protected void onRootCommit(@Nullable MoldedPlasticData original) {
            MoldedPlasticData value = MoldedPlasticStorageHandle.this.pending;
            MoldedPlasticStorageHandle.this.pending = null;
            if (value != null && MoldedPlasticStorageHandle.this.update != null) {
                MoldedPlasticStorageHandle.this.update.accept(value);
            }
        }
    };

    public MoldedPlasticStorageHandle(
        Supplier<Optional<MoldedPlasticData>> data,
        Consumer<MoldedPlasticData> update
    ) {
        this.data = data;
        this.update = update;
        this.access = null;
    }

    public MoldedPlasticStorageHandle(ItemAccess access) {
        this.data = () -> access.getAmount() == 1
            ? MoldedPlasticData.get(access.getResource().toStack()) : Optional.empty();
        this.update = null;
        this.access = access;
    }

    public Optional<MoldedPlasticData> data() {
        if (this.working != null) {
            return Optional.of(this.working);
        }
        return this.pending == null ? this.data.get() : Optional.of(this.pending);
    }

    private void publish(MoldedPlasticData value) {
        if (this.transfer != null) {
            this.working = value;
        } else if (this.update != null) {
            this.update.accept(value);
        } else {
            throw new IllegalStateException("Item capability change outside a transaction");
        }
    }

    /** 将世界仓储、载体上的 UUID 与物品槽替换纳入同一事务。 */
    public int transfer(TransactionContext parent, ToIntFunction<TransactionContext> operation) {
        Optional<MoldedPlasticData> current = this.data();
        if (current.isEmpty()) {
            return 0;
        }
        MoldedPlasticData original = current.orElseThrow();
        try (Transaction tx = Transaction.open(parent)) {
            this.working = original;
            this.transfer = tx;
            MoldedPlasticData result;
            int transferred;
            try {
                transferred = operation.applyAsInt(tx);
                result = this.working;
            } finally {
                this.working = null;
                this.transfer = null;
            }
            if (transferred <= 0) {
                return 0;
            }
            if (result != original) {
                if (this.access != null) {
                    ItemStack replacement = this.access.getResource().toStack();
                    MoldedPlasticData.set(replacement, result);
                    if (this.access.exchange(ItemResource.of(replacement), 1, tx) != 1) {
                        return 0;
                    }
                } else {
                    this.carrier.updateSnapshots(tx);
                    this.pending = result;
                }
            }
            tx.commit();
            return transferred;
        }
    }

    public List<MoldedPlasticContents.StoredItem> items() {
        if (this.isCrate()) {
            return this.crateStorage(false).map(MoldedPlasticCrateStorage::items).orElse(List.of());
        }
        return this.storage()
            .map(MoldedPlasticStorage::items)
            .orElseGet(() -> this.data().map(value -> value.contents().items()).orElse(List.of()));
    }

    public List<FluidStack> fluids() {
        return this.storage()
            .map(MoldedPlasticStorage::fluids)
            .orElseGet(() -> this.data().map(value -> value.contents().fluids()).orElse(List.of()));
    }

    public ItemStack itemInSlot(int slot) {
        if (this.isCrate()) {
            return this.crateStorage(false).map(storage -> ItemUtil.getStack(storage.getItems(), slot)).orElse(ItemStack.EMPTY);
        }
        return this.storage()
            .map(storage -> storage.itemInSlot(slot))
            .orElseGet(() -> this.data()
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
        Optional<MoldedPlasticData> current = this.data();
        if (current.isEmpty()) return;
        List<MoldedPlasticContents.StoredItem> items = new ArrayList<>(this.items());
        items.removeIf(item -> item.slot() == slot);
        if (!stack.isEmpty()) items.add(new MoldedPlasticContents.StoredItem(slot, stack));
        this.commit(current.orElseThrow(), items, this.fluids());
    }

    public void setFluids(List<FluidStack> fluids) {
        Optional<MoldedPlasticData> current = this.data();
        if (current.isEmpty()) return;
        this.commit(current.orElseThrow(), this.items(), fluids);
    }

    public MoldedPlasticContentSummary summary() {
        if (this.isCrate()) {
            return this.crateStorage(false).map(MoldedPlasticCrateStorage::summary)
                .orElseGet(() -> this.data().map(MoldedPlasticData::summary).orElse(MoldedPlasticContentSummary.EMPTY));
        }
        return this.data()
            .map(value -> MoldedPlasticContentSummary.create(
                value.finalType(),
                value.capacity(),
                this.items(),
                this.fluids()
            ))
            .orElse(MoldedPlasticContentSummary.EMPTY);
    }

    public boolean isCrate() {
        return this.data().filter(value -> MoldingProductTypes.isCrate(value.finalType())).isPresent();
    }

    public Optional<MoldedPlasticCrateStorage> crateStorage(boolean create) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) {
            return Optional.empty();
        }
        Optional<MoldedPlasticData> current = this.data().filter(value -> MoldingProductTypes.isCrate(value.finalType()));
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
        if (!create && Storages.get().get(id).isEmpty()) {
            return Optional.empty();
        }
        MoldedPlasticCrateStorage storage = Storages.get().get(id, MoldedPlasticCrateStorage.class).orElseGet(() -> {
            if (Storages.get().get(id).isPresent()) {
                throw new IllegalStateException("Unexpected storage type for molded crate " + id);
            }
            MoldedPlasticCrateStorage created = new MoldedPlasticCrateStorage(id);
            Storages.get().put(created);
            if (this.transfer != null) {
                new SnapshotJournal<Boolean>() {
                    @Override
                    protected Boolean createSnapshot() {
                        return true;
                    }

                    @Override
                    protected void revertToSnapshot(Boolean original) {
                        Storages.get().remove(id);
                    }
                }.updateSnapshots(this.transfer);
            }
            return created;
        });
        storage.initializeCapacity(value.capacity());
        if (value.storageId().isEmpty()) {
            this.publish(value.withStorageId(Optional.of(id)).withSummary(storage.summary()));
        }
        return Optional.of(storage);
    }

    public void refreshCrate() {
        Optional<MoldedPlasticData> current = this.data();
        if (current.isEmpty() || current.orElseThrow().storageId().isEmpty() || !this.isCrate()) {
            return;
        }
        this.crateStorage(false).ifPresent(storage -> {
            MoldedPlasticData value = this.data().orElseThrow();
            MoldedPlasticData updated = value.withSummary(storage.summary());
            if (updated != value) {
                this.publish(updated);
            }
        });
    }

    private Optional<MoldedPlasticStorage> storage() {
        Optional<UUID> id = this.data().flatMap(MoldedPlasticData::storageId);
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
            this.publish(current.withContents(new MoldedPlasticContents(
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
            store.orElseThrow().replace(id, null, this.transfer);
            this.publish(current
                .withStorageId(Optional.empty())
                .withSummary(MoldedPlasticContentSummary.EMPTY)
                .withContents(new MoldedPlasticContents(
                    List.of(),
                    List.of(),
                    current.contents().trayComponents()
                )));
            return;
        }
        store.orElseThrow().replace(id, new MoldedPlasticStorage(id, items, fluids), this.transfer);
        boolean renderFluids = MoldingProductTypes.holdsFluids(current.finalType()) && !current.limitOverride();
        this.publish(current
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
