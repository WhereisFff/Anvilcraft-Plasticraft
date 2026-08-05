package dev.anvilcraft.plasticraft.init;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Plasticraft 物品数据组件注册入口。 */
public final class PlasticraftDataComponents {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(
        Registries.DATA_COMPONENT_TYPE,
        AnvilcraftPlasticraft.MOD_ID
    );

    public static final Supplier<DataComponentType<MoldedPlasticData>> MOLDED_PLASTIC = COMPONENTS.register(
        "molded_plastic",
        () -> DataComponentType.<MoldedPlasticData>builder()
            .persistent(MoldedPlasticData.CODEC)
            .networkSynchronized(MoldedPlasticData.STREAM_CODEC)
            .build()
    );

    private PlasticraftDataComponents() {
    }

    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }
}
