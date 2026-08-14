package dev.anvilcraft.plasticraft.molding.product.storage;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 主世界级塑料制品仓储。物品与实体只持 UUID，权威内容在此。 */
public final class PlasticraftStorages extends SavedData {
    private static final String DATA_NAME = AnvilcraftPlasticraft.MOD_ID + "_storages";
    private static final SavedData.Factory<PlasticraftStorages> FACTORY = new SavedData.Factory<>(
        PlasticraftStorages::new,
        PlasticraftStorages::load,
        null
    );
    private static PlasticraftStorages loading;
    private final Map<UUID, MoldedPlasticStorage> storages = new HashMap<>();

    private PlasticraftStorages() {
    }

    public static Optional<PlasticraftStorages> tryGet() {
        PlasticraftStorages inProgress = loading;
        if (inProgress != null) return Optional.of(inProgress);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return Optional.empty();
        return Optional.of(get(server));
    }

    public static PlasticraftStorages get(MinecraftServer server) {
        PlasticraftStorages inProgress = loading;
        if (inProgress != null) return inProgress;
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Optional<MoldedPlasticStorage> get(UUID id) {
        return Optional.ofNullable(this.storages.get(id));
    }

    public MoldedPlasticStorage getOrCreate(UUID id) {
        return this.storages.computeIfAbsent(id, key -> {
            this.setDirty();
            return new MoldedPlasticStorage(key);
        });
    }

    public void remove(UUID id) {
        if (this.storages.remove(id) != null) this.setDirty();
    }

    public void markDirty() {
        this.setDirty();
    }

    private static PlasticraftStorages load(CompoundTag tag, HolderLookup.Provider registries) {
        PlasticraftStorages previous = loading;
        PlasticraftStorages store = new PlasticraftStorages();
        loading = store;
        try {
            if (!tag.contains("storages", Tag.TAG_COMPOUND)) return store;
            CompoundTag storagesTag = tag.getCompound("storages");
            for (String key : storagesTag.getAllKeys()) {
                UUID id;
                try {
                    id = UUID.fromString(key);
                } catch (IllegalArgumentException ignored) {
                    AnvilcraftPlasticraft.LOGGER.warn("Skipping molded plastic storage with invalid id {}", key);
                    continue;
                }
                MoldedPlasticStorage storage = new MoldedPlasticStorage(id);
                storage.load(storagesTag.getCompound(key), registries);
                store.storages.put(id, storage);
            }
            return store;
        } finally {
            loading = previous;
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag storagesTag = new CompoundTag();
        for (Map.Entry<UUID, MoldedPlasticStorage> entry : this.storages.entrySet()) {
            storagesTag.put(entry.getKey().toString(), entry.getValue().save(registries));
        }
        tag.put("storages", storagesTag);
        return tag;
    }
}
