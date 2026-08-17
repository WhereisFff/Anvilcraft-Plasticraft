package dev.anvilcraft.plasticraft.init.item;

import dev.anvilcraft.lib.v2.registrum.util.CreativeTabSection;
import dev.anvilcraft.lib.v2.registrum.util.CreativeTabSections;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.item.MoldedPlasticDemoItemStacks;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.dubhe.anvilcraft.init.item.ModItemGroups;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PlasticraftItemGroups {
    public static final String TITLE_KEY = "itemGroup.anvilcraftplasticraft.main";
    public static final String SECTION_TITLE_KEY_PREFIX = "creative_tab.anvilcraftplasticraft.section.";
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
        Registries.CREATIVE_MODE_TAB,
        AnvilcraftPlasticraft.MOD_ID
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register(
        "main",
        () -> CreativeModeTab.builder()
            .icon(PlasticraftBlocks.RESIN_ANVIL::asStack)
            .displayItems((parameters, output) -> CreativeTabSections.build(
                AnvilcraftPlasticraft.of("main"),
                parameters,
                output,
                sections -> {
                    sections.accept(PlasticraftItems.RESIN_ANVIL_HAMMER.get());
                    sections.accept(PlasticraftBlocks.RESIN_ANVIL.asItem());
                    sections.accept(PlasticraftBlocks.HARDEND_RESIN_ANVIL.asItem());
                    sections.accept(PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asItem());
                    sections.accept(PlasticraftBlocks.CATALYTIC_PRESS_LID.asItem());
                    sections.accept(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get());
                    sections.accept(PlasticraftItems.HIGH_HEAT_FUEL_BUCKET.get());
                    sections.accept(PlasticraftItems.PLASTIC_OIL_BUCKET.get());
                    sections.accept(PlasticraftItems.CRUDE_OIL_ACID_BUCKET.get());
                    sections.accept(PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem());
                    sections.accept(PlasticraftBlocks.CONDENSER_TOWER.asItem());
                    sections.accept(PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.asItem());
                    sections.accept(PlasticraftBlocks.PLASTIC_3D_PRINTING_COMPONENT.asItem());
                    sections.accept(PlasticraftBlocks.ALLAY_LOUNGE.asItem());
                    for (PlasticMaterial material : PlasticMaterial.values()) {
                        sections.section(plasticSection(material), section -> {
                            section.accept(material.bucket());
                            section.accept(material.granule());
                            MoldedPlasticDemoItemStacks.creativeProducts(material).forEach(section::accept);
                        });
                    }
                }
            ))
            .title(Component.translatable(TITLE_KEY))
            // NeoForge 的排序图将 withTabsBefore(X) 记录为 X -> this，
            // 将 withTabsAfter(X) 记录为 this -> X。
            .withTabsBefore(ModItemGroups.ANVILCRAFT_FUNCTION_BLOCK.getId())
            .withTabsAfter(ModItemGroups.ANVILCRAFT_BUILD_BLOCK.getId())
            .build()
    );

    private PlasticraftItemGroups() {
    }

    private static CreativeTabSection plasticSection(PlasticMaterial material) {
        return CreativeTabSection.builder(
                AnvilcraftPlasticraft.of("textures/gui/creative_tab/" + material.key() + ".png")
            )
            .text(Component.translatable(SECTION_TITLE_KEY_PREFIX + material.key()))
            .textBackgroundColor(switch (material) {
                case UNIVERSAL -> 0xB0233E54;
                case ENGINEERING -> 0xB01B5F3A;
                case CLEAR -> 0xB0205D69;
                case HEAT_RESISTANT -> 0xB06C2F16;
            })
            .tooltip(Component.translatable(SECTION_TITLE_KEY_PREFIX + material.key() + ".tooltip"))
            .build();
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
