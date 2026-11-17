package dev.anvilcraft.plasticraft.molding.product.storage;

import com.google.common.collect.MapMaker;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 主世界级塑料制品仓储。物品与实体只持 UUID，权威内容在此。 */
public final class PlasticraftStorages extends SavedData {
    public static final SavedDataType<PlasticraftStorages> TYPE = new SavedDataType<>(
        AnvilcraftPlasticraft.of("storages"), PlasticraftStorages::new,
        MoldedPlasticStorage.CODEC.listOf().fieldOf("storages").codec()
            .xmap(PlasticraftStorages::new, store -> List.copyOf(store.storages.values()))
    );
    private final Map<UUID, MoldedPlasticStorage> storages = new HashMap<>();
    // 同一外层事务可能经物品、实体或多个能力实例交替访问同一 UUID。
    private final Map<UUID, SnapshotJournal<Optional<MoldedPlasticStorage>>> journals = new MapMaker().weakValues().makeMap();

    private PlasticraftStorages() {
    }

    private PlasticraftStorages(List<MoldedPlasticStorage> contents) {
        for (MoldedPlasticStorage storage : contents) {
            this.storages.put(storage.id(), storage);
        }
    }

    public static Optional<PlasticraftStorages> tryGet() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        // 集成服务器与客户端同处一个进程，客户端线程不得借生命周期钩子写入服务端 SavedData。
        if (server == null || !server.isSameThread()) return Optional.empty();
        return Optional.of(get(server));
    }

    public static PlasticraftStorages get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<MoldedPlasticStorage> get(UUID id) {
        return Optional.ofNullable(this.storages.get(id));
    }

    void replace(UUID id, @Nullable MoldedPlasticStorage replacement, @Nullable TransactionContext tx) {
        if (tx != null) {
            this.journals.computeIfAbsent(id, key -> new SnapshotJournal<>() {
                @Override
                protected Optional<MoldedPlasticStorage> createSnapshot() {
                    return PlasticraftStorages.this.get(key);
                }

                @Override
                protected void revertToSnapshot(Optional<MoldedPlasticStorage> snapshot) {
                    PlasticraftStorages.this.set(key, snapshot.orElse(null));
                }

                @Override
                protected void onRootCommit(Optional<MoldedPlasticStorage> original) {
                    PlasticraftStorages.this.setDirty();
                }
            }).updateSnapshots(tx);
        } else {
            this.setDirty();
        }
        this.set(id, replacement);
    }

    private void set(UUID id, @Nullable MoldedPlasticStorage replacement) {
        if (replacement == null) {
            this.storages.remove(id);
        } else {
            this.storages.put(id, replacement);
        }
    }
}
