package dev.anvilcraft.plasticraft.init.item;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.item.UniversalPlasticItemStacks;
import dev.dubhe.anvilcraft.init.item.ModItemGroups;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PlasticraftItemGroups {
    public static final String TITLE_KEY = "itemGroup.anvilcraftplasticraft.main";
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
        Registries.CREATIVE_MODE_TAB,
        AnvilcraftPlasticraft.MOD_ID
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register(
        "main",
        () -> CreativeModeTab.builder()
            .icon(PlasticraftBlocks.RESIN_ANVIL::asStack)
            .displayItems((parameters, output) -> {
                output.accept(PlasticraftItems.RESIN_ANVIL_HAMMER.get());
                output.accept(PlasticraftBlocks.RESIN_ANVIL.asItem());
                output.accept(PlasticraftBlocks.HARDEND_RESIN_ANVIL.asItem());
                output.accept(PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asItem());
                output.accept(PlasticraftBlocks.CATALYTIC_PRESS_LID.asItem());
                output.accept(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get());
                output.accept(PlasticraftItems.HIGH_HEAT_FUEL_BUCKET.get());
                output.accept(PlasticraftItems.PLASTIC_OIL_BUCKET.get());
                output.accept(PlasticraftItems.CRUDE_OIL_ACID_BUCKET.get());
                output.accept(PlasticraftItems.UNIVERSAL_PLASTIC_MELT_BUCKET.get());
                output.accept(PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE.get());
                output.accept(UniversalPlasticItemStacks.fullBlock());
                output.accept(PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem());
                output.accept(PlasticraftBlocks.CONDENSER_TOWER.asItem());
                output.accept(PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.asItem());
                output.accept(PlasticraftBlocks.PLASTIC_3D_PRINTING_COMPONENT.asItem());
                output.accept(PlasticraftItems.CONSTRUCTION_DRONE.get());
                output.accept(PlasticraftItems.DEMOLITION_DRONE.get());
                output.accept(PlasticraftItems.COLLECTION_DRONE.get());
                output.accept(PlasticraftItems.OBSERVATION_DRONE.get());
            })
            .title(Component.translatable(TITLE_KEY))
            // NeoForge 的排序图将 withTabsBefore(X) 记录为 X -> this，
            // 将 withTabsAfter(X) 记录为 this -> X。
            .withTabsBefore(ModItemGroups.ANVILCRAFT_FUNCTION_BLOCK.getId())
            .withTabsAfter(ModItemGroups.ANVILCRAFT_BUILD_BLOCK.getId())
            .build()
    );

    private PlasticraftItemGroups() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
