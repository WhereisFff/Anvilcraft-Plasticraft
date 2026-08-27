package dev.anvilcraft.plasticraft.init.item;

import dev.anvilcraft.lib.v2.registrum.util.CreativeTabSection;
import dev.anvilcraft.lib.v2.registrum.util.CreativeTabSections;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.item.CreativeColorVariantItem;
import dev.anvilcraft.plasticraft.item.MoldedPlasticDemoItemStacks;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.dubhe.anvilcraft.init.item.ModItemGroups;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;
import java.util.function.Predicate;

public final class PlasticraftItemGroups {
    public static final String TITLE_KEY = "itemGroup.anvilcraftplasticraft.main";
    public static final String SECTION_TITLE_KEY_PREFIX = "creative_tab.anvilcraftplasticraft.section.";
    public static final ResourceLocation MAIN_ID = AnvilcraftPlasticraft.of("main");
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
        Registries.CREATIVE_MODE_TAB,
        AnvilcraftPlasticraft.MOD_ID
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register(
        "main",
        () -> CreativeModeTab.builder()
            .icon(PlasticraftBlocks.RESIN_ANVIL::asStack)
            .displayItems((parameters, output) -> buildDisplayItems(
                parameters,
                output,
                material -> !AnvilcraftPlasticraft.CLIENT_CONFIG.foldCreativeColorVariants
            ))
            .title(Component.translatable(TITLE_KEY))
            // NeoForge 的排序图将 withTabsBefore(X) 记录为 X -> this。
            .withTabsBefore(ModItemGroups.ANVILCRAFT_ITEMS.getId())
            .build()
    );

    private PlasticraftItemGroups() {
    }

    /** 根据材料分区策略生成创造栏内容，客户端横幅刷新也复用这条入口。 */
    public static void buildDisplayItems(
        CreativeModeTab.ItemDisplayParameters parameters,
        CreativeModeTab.Output output,
        Predicate<PlasticMaterial> expandVariants
    ) {
        CreativeTabSections.build(
            MAIN_ID,
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
                    boolean expand = expandVariants.test(material);
                    sections.section(plasticSection(material), section -> {
                        acceptPlasticVariants(section, material.bucket().getDefaultInstance(), expand);
                        acceptPlasticVariants(section, material.granule().getDefaultInstance(), expand);
                        MoldedPlasticDemoItemStacks.creativeProducts(material).forEach(
                            stack -> acceptPlasticVariants(section, stack, expand)
                        );
                    });
                }
            }
        );
    }

    /** 从横幅纹理反查材料，避免依赖本地化后的横幅标题。 */
    public static Optional<PlasticMaterial> materialOfSection(CreativeTabSection section) {
        ResourceLocation texture = section.bannerTexture();
        String prefix = "textures/gui/creative_tab/";
        String suffix = ".png";
        if (!AnvilcraftPlasticraft.MOD_ID.equals(texture.getNamespace())
            || !texture.getPath().startsWith(prefix)
            || !texture.getPath().endsWith(suffix)) {
            return Optional.empty();
        }
        String key = texture.getPath().substring(prefix.length(), texture.getPath().length() - suffix.length());
        return PlasticMaterial.fromKey(key);
    }

    private static CreativeTabSection plasticSection(PlasticMaterial material) {
        return CreativeTabSection.builder(
                AnvilcraftPlasticraft.of("textures/gui/creative_tab/" + material.key() + ".png")
            )
            .text(Component.translatable(SECTION_TITLE_KEY_PREFIX + material.key()))
            .tooltip(Component.translatable(SECTION_TITLE_KEY_PREFIX + material.key() + ".tooltip"))
            .build();
    }

    private static void acceptPlasticVariants(
        CreativeTabSections section,
        ItemStack source,
        boolean expandVariants
    ) {
        if (source.getItem() instanceof CreativeColorVariantItem provider && expandVariants) {
            provider.createCreativeColorVariants(source).forEach(section::accept);
            return;
        }
        section.accept(source);
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
