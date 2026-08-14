package dev.anvilcraft.plasticraft.init;

import com.mojang.serialization.Codec;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintData;
import dev.anvilcraft.plasticraft.blueprint.ConstructionDebris;
import dev.anvilcraft.plasticraft.drone.DroneData;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
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

    public static final Supplier<DataComponentType<DroneData>> DRONE_DATA = COMPONENTS.register(
        "drone_data",
        () -> DataComponentType.<DroneData>builder()
            .persistent(DroneData.CODEC)
            .networkSynchronized(DroneData.STREAM_CODEC)
            .build()
    );

    /** 无人机站拆下与重放时随物品保留的内部 FE。 */
    public static final Supplier<DataComponentType<Integer>> STATION_ENERGY = COMPONENTS.register(
        "station_energy",
        () -> DataComponentType.<Integer>builder()
            .persistent(Codec.intRange(0, Integer.MAX_VALUE))
            .networkSynchronized(ByteBufCodecs.VAR_INT)
            .build()
    );

    /** 结构磁盘上的施工蓝图引用:内容哈希、摘要与部署任务 UUID。 */
    public static final Supplier<DataComponentType<ConstructionBlueprintData>> BLUEPRINT_TASK = COMPONENTS.register(
        "blueprint_task",
        () -> DataComponentType.<ConstructionBlueprintData>builder()
            .persistent(ConstructionBlueprintData.CODEC)
            .networkSynchronized(ConstructionBlueprintData.STREAM_CODEC)
            .build()
    );

    /** 拆除掉落物的任务来源标记,参与物品合并判定。 */
    public static final Supplier<DataComponentType<ConstructionDebris>> CONSTRUCTION_DEBRIS = COMPONENTS.register(
        "construction_debris",
        () -> DataComponentType.<ConstructionDebris>builder()
            .persistent(ConstructionDebris.CODEC)
            .networkSynchronized(ConstructionDebris.STREAM_CODEC)
            .build()
    );

    private PlasticraftDataComponents() {
    }

    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }
}
