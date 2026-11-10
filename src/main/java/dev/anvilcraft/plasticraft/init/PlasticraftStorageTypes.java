package dev.anvilcraft.plasticraft.init;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.molding.product.storage.MoldedPlasticCrateStorage;
import dev.dubhe.anvilcraft.init.registry.ModRegistryKeys;
import dev.dubhe.anvilcraft.saved.storage.IStorageType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;

/** 将塑料板条箱登记到铁砧工艺的仓储持久化与类型查找接口。 */
public final class PlasticraftStorageTypes {
    private static final DeferredRegister<IStorageType<?>> TYPES = DeferredRegister.create(
        ModRegistryKeys.STORAGE_TYPE, AnvilcraftPlasticraft.MOD_ID
    );
    public static final DeferredHolder<IStorageType<?>, IStorageType<MoldedPlasticCrateStorage>> MOLDED_CRATE =
        TYPES.register("molded_plastic_crate", () -> new IStorageType<>() {
            @Override
            public MoldedPlasticCrateStorage newInstance(UUID id) {
                return new MoldedPlasticCrateStorage(id);
            }

            @Override
            public Class<MoldedPlasticCrateStorage> clazz() {
                return MoldedPlasticCrateStorage.class;
            }
        });

    private PlasticraftStorageTypes() {
    }

    public static void register(IEventBus bus) {
        IStorageType.CLASS_MAP.put(MoldedPlasticCrateStorage.class, MOLDED_CRATE);
        TYPES.register(bus);
    }
}
