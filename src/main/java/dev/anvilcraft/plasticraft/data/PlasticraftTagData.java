package dev.anvilcraft.plasticraft.data;

import dev.anvilcraft.lib.v2.registrum.providers.ProviderType;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockTags;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItemTags;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.block.ModFluidTags;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
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
            provider.addTag(PlasticraftBlockTags.RESIN_SHOCK_COMPATIBLE)
                .add(ResourceKey.create(
                    Registries.BLOCK,
                    BuiltInRegistries.BLOCK.getKey(ModBlocks.RESIN_BLOCK.get())
                ));

            // 熔融塑料接触这些含水或低温方块时，可以进入冷却凝固流程。
            provider.addTag(PlasticraftBlockTags.PLASTIC_MELT_COOLANTS).add(
                blockKey(Blocks.WATER),
                blockKey(Blocks.ICE),
                blockKey(Blocks.PACKED_ICE),
                blockKey(Blocks.BLUE_ICE),
                blockKey(Blocks.SNOW_BLOCK),
                blockKey(Blocks.POWDER_SNOW),
                blockKey(Blocks.SNOW)
            );
        });
    }

    private static void registerItemTags() {
        AnvilcraftPlasticraft.REGISTRUM.addDataGenerator(ProviderType.ITEM_TAGS, provider -> {
            // 冷却物品标签供固液配方消耗；同时覆盖原版冰雪与 AnvilCraft 霜钢制品。
            provider.addTag(PlasticraftItemTags.COLD_ITEMS).add(
                itemKey(Blocks.ICE),
                itemKey(Blocks.PACKED_ICE),
                itemKey(Blocks.BLUE_ICE),
                itemKey(Blocks.SNOW_BLOCK),
                itemKey(Items.SNOWBALL),
                itemKey(Blocks.SNOW),
                itemKey(Items.POWDER_SNOW_BUCKET),
                itemKey(ModBlocks.SLIDING_RAIL),
                itemKey(ModBlocks.POWERED_SLIDING_RAIL),
                itemKey(ModBlocks.ACTIVATOR_SLIDING_RAIL),
                itemKey(ModBlocks.DETECTOR_SLIDING_RAIL),
                itemKey(ModBlocks.SLIDING_RAIL_STOP),
                itemKey(ModBlocks.FROST_ANVIL),
                itemKey(ModBlocks.FROST_GRINDSTONE),
                itemKey(ModBlocks.FROST_SMITHING_TABLE),
                itemKey(ModBlocks.FROST_METAL_BLOCK),
                itemKey(ModBlocks.CUT_FROST_METAL_BLOCK),
                itemKey(ModBlocks.CUT_FROST_METAL_PILLAR),
                itemKey(ModBlocks.CUT_FROST_METAL_SLAB),
                itemKey(ModBlocks.CUT_FROST_METAL_STAIRS),
                itemKey(ModBlocks.FROST_DECO_BLOCK),
                itemKey(ModBlocks.FROST_DECO_OUTLINE),
                itemKey(ModBlocks.FROST_GLASS),
                itemKey(ModItems.FROST_METAL_INGOT),
                itemKey(ModItems.FROST_METAL_NUGGET),
                itemKey(ModItems.FROST_METAL_PICKAXE),
                itemKey(ModItems.FROST_METAL_AXE),
                itemKey(ModItems.FROST_METAL_SHOVEL),
                itemKey(ModItems.FROST_METAL_HOE),
                itemKey(ModItems.FROST_METAL_SWORD),
                itemKey(ModItems.FROST_METAL_HEAVY_HALBERD),
                itemKey(ModItems.FROST_METAL_RESONATOR),
                itemKey(ModItems.FROST_METAL_UPGRADE_SMITHING_TEMPLATE)
            );

            // 浮霜金属催化剂覆盖浮霜金属及其衍生制品，冰雪和滑轨仅作为冷却物品，不参与催化。
            provider.addTag(PlasticraftItemTags.FROST_METAL_ITEMS).add(
                itemKey(ModBlocks.FROST_ANVIL),
                itemKey(ModBlocks.FROST_GRINDSTONE),
                itemKey(ModBlocks.FROST_SMITHING_TABLE),
                itemKey(ModBlocks.FROST_METAL_BLOCK),
                itemKey(ModBlocks.CUT_FROST_METAL_BLOCK),
                itemKey(ModBlocks.CUT_FROST_METAL_PILLAR),
                itemKey(ModBlocks.CUT_FROST_METAL_SLAB),
                itemKey(ModBlocks.CUT_FROST_METAL_STAIRS),
                itemKey(ModBlocks.FROST_DECO_BLOCK),
                itemKey(ModBlocks.FROST_DECO_OUTLINE),
                itemKey(ModBlocks.FROST_GLASS),
                itemKey(ModItems.FROST_METAL_INGOT),
                itemKey(ModItems.FROST_METAL_NUGGET),
                itemKey(ModItems.FROST_METAL_PICKAXE),
                itemKey(ModItems.FROST_METAL_AXE),
                itemKey(ModItems.FROST_METAL_SHOVEL),
                itemKey(ModItems.FROST_METAL_HOE),
                itemKey(ModItems.FROST_METAL_SWORD),
                itemKey(ModItems.FROST_ANVIL_HAMMER),
                itemKey(ModItems.FROST_DRAGON_ROD),
                itemKey(ModItems.FROST_METAL_HEAVY_HALBERD),
                itemKey(ModItems.FROST_METAL_RESONATOR),
                itemKey(ModItems.FROST_METAL_UPGRADE_SMITHING_TEMPLATE)
            );

            // 皇家钢催化剂标签覆盖本体皇家钢制品，并包含 Plasticraft 的催化压盖。
            provider.addTag(PlasticraftItemTags.ROYAL_STEEL_ITEMS).add(
                itemKey(ModBlocks.ROYAL_ANVIL),
                itemKey(ModBlocks.ROYAL_GRINDSTONE),
                itemKey(ModBlocks.ROYAL_SMITHING_TABLE),
                itemKey(ModBlocks.ROYAL_STEEL_BLOCK),
                itemKey(ModBlocks.SMOOTH_ROYAL_STEEL_BLOCK),
                itemKey(ModBlocks.CUT_ROYAL_STEEL_BLOCK),
                itemKey(ModBlocks.CUT_ROYAL_STEEL_PILLAR),
                itemKey(ModBlocks.CUT_ROYAL_STEEL_SLAB),
                itemKey(ModBlocks.CUT_ROYAL_STEEL_STAIRS),
                itemKey(ModBlocks.TEMPERING_GLASS),
                itemKey(ModItems.ROYAL_STEEL_INGOT),
                itemKey(ModItems.ROYAL_STEEL_NUGGET),
                itemKey(ModItems.ROYAL_STEEL_PICKAXE),
                itemKey(ModItems.ROYAL_STEEL_AXE),
                itemKey(ModItems.ROYAL_STEEL_SHOVEL),
                itemKey(ModItems.ROYAL_STEEL_HOE),
                itemKey(ModItems.ROYAL_STEEL_SWORD),
                itemKey(ModItems.ROYAL_ANVIL_HAMMER),
                itemKey(ModItems.ROYAL_DRAGON_ROD),
                itemKey(ModItems.ROYAL_STEEL_UPGRADE_SMITHING_TEMPLATE),
                itemKey(PlasticraftBlocks.CATALYTIC_PRESS_LID)
            );
        });
    }

    private static void registerFluidTags() {
        // 高热燃料的静止与流动流体都属于 AnvilCraft 可点燃流体。
        AnvilcraftPlasticraft.REGISTRUM.addDataGenerator(ProviderType.FLUID_TAGS, provider -> provider
            .addTag(ModFluidTags.IGNITABLE)
            .add(
                ResourceKey.create(Registries.FLUID, PlasticraftFluids.HIGH_HEAT_FUEL.getId()),
                ResourceKey.create(Registries.FLUID, PlasticraftFluids.FLOWING_HIGH_HEAT_FUEL.getId())
            ));
    }

    private static ResourceKey<Block> blockKey(Block block) {
        return ResourceKey.create(Registries.BLOCK, BuiltInRegistries.BLOCK.getKey(block));
    }

    private static ResourceKey<Item> itemKey(ItemLike item) {
        return ResourceKey.create(Registries.ITEM, BuiltInRegistries.ITEM.getKey(item.asItem()));
    }
}
