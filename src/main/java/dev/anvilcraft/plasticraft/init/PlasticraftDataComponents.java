package dev.anvilcraft.plasticraft.init;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintData;
import dev.anvilcraft.plasticraft.blueprint.ConstructionDebris;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronContents;
import dev.anvilcraft.plasticraft.entity.MoldedPlasticCauldronState;
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

    /** 硬化树脂锅 Ctrl 中键复制时保留的物品、流体与功能状态。 */
    public static final Supplier<DataComponentType<HardenedResinCauldronContents>> HARDENED_RESIN_CAULDRON_CONTENTS =
        COMPONENTS.register(
            "hardened_resin_cauldron_contents",
            () -> DataComponentType.<HardenedResinCauldronContents>builder()
                .persistent(HardenedResinCauldronContents.CODEC)
                .networkSynchronized(HardenedResinCauldronContents.STREAM_CODEC)
                .build()
        );

    /** 成型塑料锅 Ctrl 中键复制时保留的出料口与点燃状态。 */
    public static final Supplier<DataComponentType<MoldedPlasticCauldronState>> MOLDED_PLASTIC_CAULDRON_STATE =
        COMPONENTS.register(
            "molded_plastic_cauldron_state",
            () -> DataComponentType.<MoldedPlasticCauldronState>builder()
                .persistent(MoldedPlasticCauldronState.CODEC)
                .networkSynchronized(MoldedPlasticCauldronState.STREAM_CODEC)
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
