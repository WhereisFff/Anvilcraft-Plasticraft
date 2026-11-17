package dev.anvilcraft.plasticraft.data;

import dev.anvilcraft.lib.v2.registrum.providers.ProviderType;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockTags;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItemTags;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.block.ModFluidTags;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** 生成 Plasticraft 使用的方块、物品和流体标签。 */
public final class PlasticraftTagData {
    private PlasticraftTagData() {
    }

    public static void register() {
        registerBlockTags();
        registerItemTags();
        registerFluidTags();
    }

    private static void registerBlockTags() {
        AnvilcraftPlasticraft.REGISTRUM.addDataGenerator(ProviderType.BLOCK_TAGS, provider -> {
            // 树脂冲击兼容标签必须包含 AnvilCraft 本体树脂块，供跨模组冲击逻辑查询。
            provider.tag(PlasticraftBlockTags.RESIN_SHOCK_COMPATIBLE)
                .add(ModBlocks.RESIN_BLOCK.get());

            // 熔融塑料接触这些含水或低温方块时，可以进入冷却凝固流程。
            provider.tag(PlasticraftBlockTags.PLASTIC_MELT_COOLANTS).add(
                Blocks.WATER,
                Blocks.ICE,
                Blocks.PACKED_ICE,
                Blocks.BLUE_ICE,
                Blocks.SNOW_BLOCK,
                Blocks.POWDER_SNOW,
                Blocks.SNOW
            );
        });
    }

    private static void registerItemTags() {
        AnvilcraftPlasticraft.REGISTRUM.addDataGenerator(ProviderType.ITEM_TAGS, provider -> {
            // 树脂催化剂的材料种子；合成和锻造产生的后续制品由运行时配方闭包补齐。
            provider.tag(PlasticraftItemTags.RESIN_ITEMS).add(
                ModItems.RESIN.asItem(),
                ModItems.HARDEND_RESIN.asItem(),
                ModBlocks.RESIN_BLOCK.asItem(),
                PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.asItem(),
                PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem(),
                PlasticraftBlocks.RESIN_ANVIL.asItem(),
                PlasticraftBlocks.HARDEND_RESIN_ANVIL.asItem(),
                PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asItem(),
                PlasticraftItems.RESIN_ANVIL_HAMMER.asItem()
            );

            // 冷却物品标签供固液配方消耗；同时覆盖原版冰雪与 AnvilCraft 霜钢制品。
            provider.tag(PlasticraftItemTags.COLD_ITEMS).add(
                Blocks.ICE.asItem(),
                Blocks.PACKED_ICE.asItem(),
                Blocks.BLUE_ICE.asItem(),
                Blocks.SNOW_BLOCK.asItem(),
                Items.SNOWBALL.asItem(),
                Blocks.SNOW.asItem(),
                Items.POWDER_SNOW_BUCKET.asItem(),
                ModBlocks.SLIDING_RAIL.asItem(),
                ModBlocks.POWERED_SLIDING_RAIL.asItem(),
                ModBlocks.ACTIVATOR_SLIDING_RAIL.asItem(),
                ModBlocks.DETECTOR_SLIDING_RAIL.asItem(),
                ModBlocks.SLIDING_RAIL_STOP.asItem(),
                ModBlocks.FROST_ANVIL.asItem(),
                ModBlocks.FROST_GRINDSTONE.asItem(),
                ModBlocks.FROST_SMITHING_TABLE.asItem(),
                ModBlocks.FROST_METAL_BLOCK.asItem(),
                ModBlocks.CUT_FROST_METAL_BLOCK.asItem(),
                ModBlocks.CUT_FROST_METAL_PILLAR.asItem(),
                ModBlocks.CUT_FROST_METAL_SLAB.asItem(),
                ModBlocks.CUT_FROST_METAL_STAIRS.asItem(),
                ModBlocks.FROST_DECO_BLOCK.asItem(),
                ModBlocks.FROST_DECO_OUTLINE.asItem(),
                ModBlocks.FROST_GLASS.asItem(),
                ModItems.FROST_METAL_INGOT.asItem(),
                ModItems.FROST_METAL_NUGGET.asItem(),
                ModItems.FROST_METAL_PICKAXE.asItem(),
                ModItems.FROST_METAL_AXE.asItem(),
                ModItems.FROST_METAL_SHOVEL.asItem(),
                ModItems.FROST_METAL_HOE.asItem(),
                ModItems.FROST_METAL_SWORD.asItem(),
                ModItems.FROST_METAL_HEAVY_HALBERD.asItem(),
                ModItems.FROST_METAL_RESONATOR.asItem(),
                ModItems.FROST_METAL_UPGRADE_SMITHING_TEMPLATE.asItem()
            );

            // 浮霜金属催化剂覆盖浮霜金属及其衍生制品，冰雪和滑轨仅作为冷却物品，不参与催化。
            provider.tag(PlasticraftItemTags.FROST_METAL_ITEMS).add(
                ModBlocks.FROST_ANVIL.asItem(),
                ModBlocks.FROST_GRINDSTONE.asItem(),
                ModBlocks.FROST_SMITHING_TABLE.asItem(),
                ModBlocks.FROST_METAL_BLOCK.asItem(),
                ModBlocks.CUT_FROST_METAL_BLOCK.asItem(),
                ModBlocks.CUT_FROST_METAL_PILLAR.asItem(),
                ModBlocks.CUT_FROST_METAL_SLAB.asItem(),
                ModBlocks.CUT_FROST_METAL_STAIRS.asItem(),
                ModBlocks.FROST_DECO_BLOCK.asItem(),
                ModBlocks.FROST_DECO_OUTLINE.asItem(),
                ModItems.FROST_METAL_INGOT.asItem(),
                ModItems.FROST_METAL_NUGGET.asItem(),
                ModItems.FROST_METAL_PICKAXE.asItem(),
                ModItems.FROST_METAL_AXE.asItem(),
                ModItems.FROST_METAL_SHOVEL.asItem(),
                ModItems.FROST_METAL_HOE.asItem(),
                ModItems.FROST_METAL_SWORD.asItem(),
                ModItems.FROST_ANVIL_HAMMER.asItem(),
                ModItems.FROST_DRAGON_ROD.asItem(),
                ModItems.FROST_METAL_HEAVY_HALBERD.asItem(),
                ModItems.FROST_METAL_RESONATOR.asItem(),
                ModItems.FROST_METAL_UPGRADE_SMITHING_TEMPLATE.asItem()
            );

            // 余烬金属催化剂覆盖余烬铁砧本体及其金属制品，供耐热塑料反应查询。
            provider.tag(PlasticraftItemTags.EMBER_METAL_ITEMS).add(
                ModBlocks.EMBER_ANVIL.asItem(),
                ModBlocks.EMBER_GRINDSTONE.asItem(),
                ModBlocks.EMBER_SMITHING_TABLE.asItem(),
                ModBlocks.EMBER_METAL_BLOCK.asItem(),
                ModBlocks.CUT_EMBER_METAL_BLOCK.asItem(),
                ModBlocks.CUT_EMBER_METAL_PILLAR.asItem(),
                ModBlocks.CUT_EMBER_METAL_SLAB.asItem(),
                ModBlocks.CUT_EMBER_METAL_STAIRS.asItem(),
                ModBlocks.EMBER_DECO_BLOCK.asItem(),
                ModBlocks.EMBER_DECO_OUTLINE.asItem(),
                ModBlocks.EMBER_GLASS.asItem(),
                ModItems.EMBER_METAL_INGOT.asItem(),
                ModItems.EMBER_METAL_NUGGET.asItem(),
                ModItems.EMBER_METAL_PICKAXE.asItem(),
                ModItems.EMBER_METAL_AXE.asItem(),
                ModItems.EMBER_METAL_SHOVEL.asItem(),
                ModItems.EMBER_METAL_HOE.asItem(),
                ModItems.EMBER_METAL_SWORD.asItem(),
                ModItems.EMBER_ANVIL_HAMMER.asItem(),
                ModItems.EMBER_DRAGON_ROD.asItem(),
                ModItems.EMBER_METAL_HEAVY_HALBERD.asItem(),
                ModItems.EMBER_METAL_RESONATOR.asItem(),
                ModItems.EMBER_METAL_UPGRADE_SMITHING_TEMPLATE.asItem()
            );

            provider.tag(PlasticraftItemTags.ROYAL_STEEL_ITEMS).add(
                ModBlocks.ROYAL_ANVIL.asItem(),
                ModBlocks.ROYAL_GRINDSTONE.asItem(),
                ModBlocks.ROYAL_SMITHING_TABLE.asItem(),
                ModBlocks.ROYAL_STEEL_BLOCK.asItem(),
                ModBlocks.SMOOTH_ROYAL_STEEL_BLOCK.asItem(),
                ModBlocks.CUT_ROYAL_STEEL_BLOCK.asItem(),
                ModBlocks.CUT_ROYAL_STEEL_PILLAR.asItem(),
                ModBlocks.CUT_ROYAL_STEEL_SLAB.asItem(),
                ModBlocks.CUT_ROYAL_STEEL_STAIRS.asItem(),
                ModItems.ROYAL_STEEL_INGOT.asItem(),
                ModItems.ROYAL_STEEL_NUGGET.asItem(),
                ModItems.ROYAL_STEEL_PICKAXE.asItem(),
                ModItems.ROYAL_STEEL_AXE.asItem(),
                ModItems.ROYAL_STEEL_SHOVEL.asItem(),
                ModItems.ROYAL_STEEL_HOE.asItem(),
                ModItems.ROYAL_STEEL_SWORD.asItem(),
                ModItems.ROYAL_ANVIL_HAMMER.asItem(),
                ModItems.ROYAL_DRAGON_ROD.asItem(),
                ModItems.ROYAL_STEEL_UPGRADE_SMITHING_TEMPLATE.asItem()
            );
            // 浮霜玻璃作为透明塑料催化剂单独建标签，效率按玻璃种类参与半速计算。
            provider.tag(PlasticraftItemTags.FROST_GLASS_ITEMS).add(ModBlocks.FROST_GLASS.asItem());
            // 皇家玻璃作为透明塑料催化剂单独建标签，避免与皇家钢催化语义混用。
            provider.tag(PlasticraftItemTags.ROYAL_GLASS_ITEMS).add(ModBlocks.TEMPERING_GLASS.asItem());
        });
    }

    private static void registerFluidTags() {
        // 高热燃料的静止与流动流体都属于 AnvilCraft 可点燃流体。
        AnvilcraftPlasticraft.REGISTRUM.addDataGenerator(ProviderType.FLUID_TAGS, provider -> provider
            .tag(ModFluidTags.IGNITABLE)
            .add(
                PlasticraftFluids.HIGH_HEAT_FUEL.get(),
                PlasticraftFluids.FLOWING_HIGH_HEAT_FUEL.get()
            ));
    }
}
