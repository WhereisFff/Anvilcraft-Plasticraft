package dev.anvilcraft.plasticraft.init.item;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static dev.dubhe.anvilcraft.init.item.ModItemGroups.ANVILCRAFT_BUILD_BLOCK;
import static dev.dubhe.anvilcraft.init.item.ModItemGroups.ANVILCRAFT_FUNCTION_BLOCK;

public final class ModItemGroups {
    public static final String TITLE_KEY = "itemGroup.anvilcraftplasticraft.main";
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
        Registries.CREATIVE_MODE_TAB,
        AnvilcraftPlasticraft.MOD_ID
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register(
        "main",
        () -> CreativeModeTab.builder()
            .icon(ModBlocks.RESIN_ANVIL::asStack)
            .displayItems((parameters, output) -> {
                output.accept(ModItems.RESIN_ANVIL_HAMMER.get());
                output.accept(ModBlocks.RESIN_ANVIL.asItem());
                output.accept(ModBlocks.HARDEND_RESIN_ANVIL.asItem());
                output.accept(ModBlocks.HARDEND_RESIN_CAULDRON.asItem());
                output.accept(ModBlocks.CATALYTIC_PRESS_LID.asItem());
                output.accept(ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get());
                output.accept(ModItems.HIGH_HEAT_FUEL_BUCKET.get());
                output.accept(ModItems.PLASTIC_OIL_BUCKET.get());
                output.accept(ModItems.CRUDE_OIL_ACID_BUCKET.get());
                output.accept(ModItems.UNIVERSAL_PLASTIC_MELT_BUCKET.get());
                output.accept(ModItems.UNIVERSAL_PLASTIC_GRANULE.get());
                output.accept(ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem());
                output.accept(ModBlocks.CONDENSER_TOWER.asItem());
                output.accept(ModBlocks.PLASTIC_MOLDING_CHAMBER.asItem());
            })
            .title(Component.translatable(TITLE_KEY))
            // NeoForge 的排序图将 withTabsBefore(X) 记录为 X -> this，
            // 将 withTabsAfter(X) 记录为 this -> X。
            .withTabsBefore(ANVILCRAFT_FUNCTION_BLOCK.getId())
            .withTabsAfter(ANVILCRAFT_BUILD_BLOCK.getId())
            .build()
    );

    private ModItemGroups() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
