package dev.anvilcraft.plasticraft.data;

import dev.anvilcraft.lib.v2.registrum.providers.ProviderType;
import dev.anvilcraft.lib.v2.registrum.providers.RegistrumRecipeProvider;
import dev.anvilcraft.lib.v2.recipe.data.advancement.predicate.item.NotPredicate;
import dev.anvilcraft.lib.v2.recipe.init.LibItemSubPredicates;
import dev.anvilcraft.lib.v2.util.predicate.ItemIngredientPredicate;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.block.ModBlockTags;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.item.ModItemGroups;
import dev.anvilcraft.plasticraft.init.item.ModItemTags;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.recipe.FluidFastCookingRecipe;
import dev.anvilcraft.plasticraft.recipe.CondenserGas;
import dev.anvilcraft.plasticraft.recipe.CondenserRecipe;
import dev.anvilcraft.plasticraft.recipe.PlasmaJetBlastingRecipe;
import dev.dubhe.anvilcraft.init.block.ModFluidTags;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.init.item.ModItemSubPredicates;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.init.recipe.ModRecipeTriggers;
import dev.dubhe.anvilcraft.item.property.predicate.ItemSavedEntityPredicate;
import dev.dubhe.anvilcraft.block.CorruptedBeaconBlock;
import dev.dubhe.anvilcraft.block.fluid.PipeBlock;
import dev.dubhe.anvilcraft.recipe.multiblock.BlockPredicateWithState;
import dev.dubhe.anvilcraft.recipe.multiblock.MultiblockConversionRecipe;
import dev.dubhe.anvilcraft.recipe.multiblock.MultiblockRecipe;
import dev.dubhe.anvilcraft.recipe.FluidMixingRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.SolidLiquidRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.builder.ExtendInWorldRecipeBuilder;
import dev.dubhe.anvilcraft.recipe.anvil.outcome.ResentmentAmberOutcome;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.FastCookingRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.TimeWarpRecipe;
import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluids;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.anvilcraft.plasticraft.AnvilcraftPlasticraft.REGISTRUM;

public final class PlasticraftDatagen {
    private PlasticraftDatagen() {
    }

    public static void init() {
        REGISTRUM.addDataGenerator(ProviderType.LANG, provider -> {
            PlasticraftItemTooltipLang.init(provider);
            provider.add(ModItemGroups.TITLE_KEY, "Anvilcraft: Plasticraft");
            provider.add("item.anvilcraftplasticraft.hardend_resin_anvil", "Hardened Resin Anvil");
            provider.add("item.anvilcraftplasticraft.hardend_resin_cauldron", "Hardened Resin Cauldron");
            provider.add("item.anvilcraftplasticraft.resin_anvil", "Resin Anvil");
            provider.add("message.anvilcraftplasticraft.adhesive.out_of_range", "Too far away");
            provider.add(
                "message.anvilcraftplasticraft.adhesive.too_far_disconnected",
                "Too far away; selection disconnected"
            );
            provider.add("tooltip.anvilcraftplasticraft.magnetized", "Magnetized");
            provider.add("tooltip.anvilcraftplasticraft.resin_anvil.captured", "Contains: %s");
            provider.add("tooltip.anvilcraftplasticraft.jade.color", "Material colour: %s");
            provider.add("tooltip.anvilcraftplasticraft.jade.pushable", "Can be pushed");
            provider.add("tooltip.anvilcraftplasticraft.bonded", "Bonded in place");
            provider.add("config.jade.plugin_anvilcraftplasticraft.bonded_entity", "Bonded entities");
            provider.add("config.jade.plugin_anvilcraftplasticraft.bonded_block", "Bonded blocks");
            provider.add("tooltip.anvilcraftplasticraft.jade.item_count", "%1$s x %2$s");
            provider.add("config.jade.plugin_anvilcraftplasticraft.hardend_resin_anvil", "Hardened Resin Anvil");
            provider.add("config.jade.plugin_anvilcraftplasticraft.condenser_tower", "Condenser Tower");
            provider.add("config.jade.plugin_anvilcraft.fluid_tank", "Fluid Tank");
            provider.add("gui.anvilcraftplasticraft.category.plasma_jet_blasting", "Plasma Jet Blasting");
            provider.add("gui.anvilcraftplasticraft.category.condenser", "Condensation");
            provider.add("tooltip.anvilcraftplasticraft.color", "Color: %s");
            provider.add("jei.anvilcraftplasticraft.gas.gaseous_oil", "Gaseous oil");
            provider.add("jei.anvilcraftplasticraft.gas.gaseous_water", "Gaseous water");
            provider.add("jei.anvilcraftplasticraft.gas.gaseous_experience", "Gaseous experience");
            provider.add("jei.anvilcraftplasticraft.vaporization_rate", "Vaporization rate: %s mB/gt");
            provider.add("tooltip.anvilcraftplasticraft.jade.empty", "Empty");
            provider.add("tooltip.anvilcraftplasticraft.jade.gas", "%1$s %2$s / %3$s");
            provider.add("tooltip.anvilcraftplasticraft.jade.fluid", "%1$s %2$s / %3$s");
            provider.add("screen.anvilcraftplasticraft.hammer_direction.up", "Up");
            provider.add("screen.anvilcraftplasticraft.hammer_direction.down", "Down");
            provider.add("screen.anvilcraftplasticraft.hammer_direction.north", "North");
            provider.add("screen.anvilcraftplasticraft.hammer_direction.east", "East");
            provider.add("screen.anvilcraftplasticraft.hammer_direction.south", "South");
            provider.add("screen.anvilcraftplasticraft.hammer_direction.west", "West");
        });

        REGISTRUM.addDataGenerator(ProviderType.RECIPE, PlasticraftDatagen::generateRecipes);
        // 随已注册方块一同生成跨模组的树脂冲击标签，
        // 同时保留 AnvilCraft 自身的树脂块。
        REGISTRUM.addDataGenerator(ProviderType.BLOCK_TAGS, provider -> {
            provider.addTag(ModBlockTags.RESIN_SHOCK_COMPATIBLE)
                .add(ResourceKey.create(
                    Registries.BLOCK,
                    BuiltInRegistries.BLOCK.getKey(dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK.get())
                ));
            provider.addTag(ModBlockTags.PLASTIC_MELT_COOLANTS).add(
                blockKey(Blocks.WATER),
                blockKey(Blocks.ICE),
                blockKey(Blocks.PACKED_ICE),
                blockKey(Blocks.BLUE_ICE),
                blockKey(Blocks.SNOW_BLOCK),
                blockKey(Blocks.POWDER_SNOW),
                blockKey(Blocks.SNOW)
            );
        });
        REGISTRUM.addDataGenerator(ProviderType.ITEM_TAGS, provider -> {
            provider.addTag(ModItemTags.COLD_ITEMS).add(
                itemKey(Blocks.ICE),
                itemKey(Blocks.PACKED_ICE),
                itemKey(Blocks.BLUE_ICE),
                itemKey(Blocks.SNOW_BLOCK),
                itemKey(Items.SNOWBALL),
                itemKey(Blocks.SNOW),
                itemKey(Items.POWDER_SNOW_BUCKET),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.SLIDING_RAIL),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.POWERED_SLIDING_RAIL),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.ACTIVATOR_SLIDING_RAIL),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.DETECTOR_SLIDING_RAIL),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.SLIDING_RAIL_STOP),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.FROST_ANVIL),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.FROST_GRINDSTONE),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.FROST_SMITHING_TABLE),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.FROST_METAL_BLOCK),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.CUT_FROST_METAL_BLOCK),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.CUT_FROST_METAL_PILLAR),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.CUT_FROST_METAL_SLAB),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.CUT_FROST_METAL_STAIRS),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.FROST_DECO_BLOCK),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.FROST_DECO_OUTLINE),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.FROST_GLASS),
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
            provider.addTag(ModItemTags.ROYAL_STEEL_ITEMS).add(
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.ROYAL_ANVIL),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.ROYAL_GRINDSTONE),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.ROYAL_SMITHING_TABLE),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.ROYAL_STEEL_BLOCK),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.SMOOTH_ROYAL_STEEL_BLOCK),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.CUT_ROYAL_STEEL_BLOCK),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.CUT_ROYAL_STEEL_PILLAR),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.CUT_ROYAL_STEEL_SLAB),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.CUT_ROYAL_STEEL_STAIRS),
                itemKey(dev.dubhe.anvilcraft.init.block.ModBlocks.TEMPERING_GLASS),
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
                itemKey(ModBlocks.CATALYTIC_PRESS_LID)
            );
        });
        REGISTRUM.addDataGenerator(ProviderType.FLUID_TAGS, provider -> provider
            .addTag(ModFluidTags.IGNITABLE)
            .add(
                ResourceKey.create(Registries.FLUID, ModFluids.HIGH_HEAT_FUEL.getId()),
                ResourceKey.create(Registries.FLUID, ModFluids.FLOWING_HIGH_HEAT_FUEL.getId())
            ));
    }

    private static void generateRecipes(RegistrumRecipeProvider provider) {
        // 初期弹性砧的中心原料有意使用树脂。
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.RESIN_ANVIL.asItem())
            .pattern("BBB")
            .pattern(" R ")
            .pattern("RRR")
            .define('B', Ingredient.of(dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK.get()))
            .define('R', Ingredient.of(ModItems.RESIN.get()))
            .unlockedBy("has_resin", provider.has(ModItems.RESIN))
            .unlockedBy("has_resin_block", provider.has(dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK))
            .save(provider, AnvilcraftPlasticraft.of("resin_anvil"));

        ShapedRecipeBuilder.shaped(
            RecipeCategory.TOOLS,
            dev.anvilcraft.plasticraft.init.item.ModItems.RESIN_ANVIL_HAMMER.get()
        )
            .pattern("A")
            .pattern("L")
            .pattern("R")
            .define('A', Ingredient.of(ModBlocks.RESIN_ANVIL.asItem()))
            .define('L', Ingredient.of(Items.LIGHTNING_ROD))
            .define('R', Ingredient.of(ModItems.HARDEND_RESIN.get()))
            .unlockedBy("has_resin_anvil", provider.has(ModBlocks.RESIN_ANVIL.asItem()))
            .unlockedBy("has_hardend_resin", provider.has(ModItems.HARDEND_RESIN))
            .save(provider, AnvilcraftPlasticraft.of("resin_anvil_hammer"));

        // 在同一中心槽放入磁铁锭可合成磁性变体。
        Map<Character, Ingredient> key = new LinkedHashMap<>();
        key.put('B', Ingredient.of(dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK.get()));
        key.put('I', Ingredient.of(ModItems.MAGNET_INGOT.get()));
        key.put('R', Ingredient.of(ModItems.RESIN.get()));
        ResourceLocation magneticId = AnvilcraftPlasticraft.of("magnetic_resin_anvil");
        ItemStack magneticResult = ModBlocks.RESIN_ANVIL.asStack();
        PlasticItemData.setMaterial(magneticResult, "resin");
        PlasticItemData.setMagnetized(magneticResult, true);
        ShapedRecipe magneticRecipe = new ShapedRecipe(
            "",
            CraftingBookCategory.BUILDING,
            ShapedRecipePattern.of(key, List.of("BBB", " I ", "RRR")),
            magneticResult
        );
        Advancement.Builder advancement = provider.advancement()
            .addCriterion("has_resin", provider.has(ModItems.RESIN))
            .addCriterion("has_resin_block", provider.has(dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK))
            .addCriterion("has_magnet_ingot", provider.has(ModItems.MAGNET_INGOT))
            .rewards(AdvancementRewards.Builder.recipe(magneticId))
            .requirements(AdvancementRequirements.Strategy.OR);
        provider.accept(
            magneticId,
            magneticRecipe,
            advancement.build(magneticId.withPrefix("recipes/building_blocks/"))
        );

        // 无流体快速烹饪会硬化树脂砧。输入上的模型标记使两份配方互斥，
        // 并保留磁化状态。
        ItemIngredientPredicate ordinaryInput = resinAnvilVariant(0);
        ItemStack ordinaryOutput = ModBlocks.HARDEND_RESIN_ANVIL.asStack();
        PlasticItemData.setMaterial(ordinaryOutput, "hardened_resin");
        FastCookingRecipe.builder()
            .requires(ordinaryInput)
            .result(ordinaryOutput)
            .save(provider, AnvilcraftPlasticraft.of("fast_cooking/harden_resin_anvil"));

        ItemStack magneticOutput = ModBlocks.HARDEND_RESIN_ANVIL.asStack();
        PlasticItemData.setMaterial(magneticOutput, "hardened_resin");
        PlasticItemData.setMagnetized(magneticOutput, true);
        FastCookingRecipe.builder()
            .requires(resinAnvilVariant(1))
            .result(magneticOutput)
            .save(provider, AnvilcraftPlasticraft.of("fast_cooking/harden_magnetic_resin_anvil"));

        FluidFastCookingRecipe.fluidBuilder()
            .cauldron(Blocks.WATER_CAULDRON)
            .consume(1000)
            .transform(ModFluids.liquidHighViscosityResinId())
            .produce(1000)
            .requires(ModItems.RESIN.get(), 4)
            .requires(Items.SLIME_BALL, 4)
            .requires(ModItems.LIME_POWDER.get())
            .unlockedBy("has_resin", provider.has(ModItems.RESIN))
            .unlockedBy("has_slime_ball", provider.has(Items.SLIME_BALL))
            .unlockedBy("has_lime_powder", provider.has(ModItems.LIME_POWDER))
            .save(provider, AnvilcraftPlasticraft.of("fast_cooking/liquid_high_viscosity_resin"));

        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ModBlocks.HARDEND_RESIN_CAULDRON.asItem())
            .pattern("H H")
            .pattern("H H")
            .pattern("HHH")
            .define('H', Ingredient.of(ModItems.HARDEND_RESIN.get()))
            .unlockedBy("has_hardend_resin", provider.has(ModItems.HARDEND_RESIN))
            .save(provider, AnvilcraftPlasticraft.of("hardend_resin_cauldron"));

        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ModBlocks.CATALYTIC_PRESS_LID.asItem())
            .pattern("HHH")
            .define('H', Ingredient.of(ModItems.HARDEND_RESIN.get()))
            .unlockedBy("has_hardend_resin", provider.has(ModItems.HARDEND_RESIN))
            .save(provider, AnvilcraftPlasticraft.of("catalytic_press_lid"));

        ItemStack magneticCauldron = ModBlocks.HARDEND_RESIN_CAULDRON.asStack();
        PlasticItemData.setMaterial(magneticCauldron, "hardened_resin");
        PlasticItemData.setMagnetized(magneticCauldron, true);
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, magneticCauldron)
            .pattern("M M")
            .pattern("H H")
            .pattern("HHH")
            .define('M', Ingredient.of(ModItems.MAGNET_INGOT.get()))
            .define('H', Ingredient.of(ModItems.HARDEND_RESIN.get()))
            .unlockedBy("has_hardend_resin", provider.has(ModItems.HARDEND_RESIN))
            .unlockedBy("has_magnet_ingot", provider.has(ModItems.MAGNET_INGOT))
            .save(provider, AnvilcraftPlasticraft.of("magnetic_hardend_resin_cauldron"));

        MultiblockRecipe.builder("anvilcraftplasticraft:condenser_tower", 1)
            .layer("ABA", "CDC", "ABA")
            .layer("EFE", "F F", "EFE")
            .layer("EFE", "FGF", "EFE")
            .symbol('A', "anvilcraftplasticraft:high_viscosity_resin_block")
            .symbol('B', BlockPredicateWithState.of("anvilcraft:pipe_straight")
                .hasState("axis", "z")
                .hasState("waterlogged", "false")
            )
            .symbol('C', BlockPredicateWithState.of("anvilcraft:pipe_straight")
                .hasState("axis", "x")
                .hasState("waterlogged", "false")
            )
            .symbol('D', BlockPredicateWithState.of("minecraft:copper_trapdoor")
                .hasState("facing", "north")
                .hasState("open", "false")
                .hasState("half", "bottom")
                .hasState("waterlogged", "false")
            )
            .symbol('E', BlockPredicateWithState.of("anvilcraft:cut_brass_pillar")
                .hasState("axis", "y")
            )
            .symbol('F', "minecraft:glass")
            .symbol('G', BlockPredicateWithState.of("minecraft:copper_trapdoor")
                .hasState("facing", "north")
                .hasState("open", "false")
                .hasState("half", "top")
                .hasState("waterlogged", "false")
            )
            .save(provider);

        MultiblockConversionRecipe.builder()
            .inputLayer("ABA", "CDC", "ABA")
            .inputLayer("EFE", "F F", "EFE")
            .inputLayer("EFE", "FGF", "EFE")
            .inputSymbol('A', "anvilcraftplasticraft:high_viscosity_resin_block")
            .inputSymbol('B', BlockPredicateWithState.of("anvilcraft:pipe_straight")
                .hasState("axis", "z")
                .hasState("waterlogged", "false")
            )
            .inputSymbol('C', BlockPredicateWithState.of("anvilcraft:pipe_straight")
                .hasState("axis", "x")
                .hasState("waterlogged", "false")
            )
            .inputSymbol('D', BlockPredicateWithState.of("minecraft:copper_trapdoor")
                .hasState("facing", "north")
                .hasState("open", "false")
                .hasState("half", "bottom")
                .hasState("waterlogged", "false")
            )
            .inputSymbol('E', BlockPredicateWithState.of("anvilcraft:cut_brass_pillar")
                .hasState("axis", "y")
            )
            .inputSymbol('F', "minecraft:glass")
            .inputSymbol('G', BlockPredicateWithState.of("minecraft:copper_trapdoor")
                .hasState("facing", "north")
                .hasState("open", "false")
                .hasState("half", "top")
                .hasState("waterlogged", "false")
            )
            .outputLayer("ABC", "DEF", "GHI")
            .outputLayer("JKL", "MNO", "PQR")
            .outputLayer("STU", "VWX", "YZ[")
            .outputSymbol('A', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "bottom_wn")
                .hasState("sealed", "false")
            )
            .outputSymbol('B', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "bottom_n")
                .hasState("sealed", "false")
            )
            .outputSymbol('C', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "bottom_en")
                .hasState("sealed", "false")
            )
            .outputSymbol('D', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "bottom_w")
                .hasState("sealed", "false")
            )
            .outputSymbol('E', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "bottom_center")
                .hasState("sealed", "false")
            )
            .outputSymbol('F', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "bottom_e")
                .hasState("sealed", "false")
            )
            .outputSymbol('G', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "bottom_ws")
                .hasState("sealed", "false")
            )
            .outputSymbol('H', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "bottom_s")
                .hasState("sealed", "false")
            )
            .outputSymbol('I', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "bottom_es")
                .hasState("sealed", "false")
            )
            .outputSymbol('J', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "mid_wn")
                .hasState("sealed", "false")
            )
            .outputSymbol('K', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "mid_n")
                .hasState("sealed", "false")
            )
            .outputSymbol('L', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "mid_en")
                .hasState("sealed", "false")
            )
            .outputSymbol('M', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "mid_w")
                .hasState("sealed", "false")
            )
            .outputSymbol('N', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "mid_center")
                .hasState("sealed", "false")
            )
            .outputSymbol('O', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "mid_e")
                .hasState("sealed", "false")
            )
            .outputSymbol('P', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "mid_ws")
                .hasState("sealed", "false")
            )
            .outputSymbol('Q', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "mid_s")
                .hasState("sealed", "false")
            )
            .outputSymbol('R', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "mid_es")
                .hasState("sealed", "false")
            )
            .outputSymbol('S', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "top_wn")
                .hasState("sealed", "false")
            )
            .outputSymbol('T', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "top_n")
                .hasState("sealed", "false")
            )
            .outputSymbol('U', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "top_en")
                .hasState("sealed", "false")
            )
            .outputSymbol('V', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "top_w")
                .hasState("sealed", "false")
            )
            .outputSymbol('W', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "top_center")
                .hasState("sealed", "false")
            )
            .outputSymbol('X', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "top_e")
                .hasState("sealed", "false")
            )
            .outputSymbol('Y', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "top_ws")
                .hasState("sealed", "false")
            )
            .outputSymbol('Z', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "top_s")
                .hasState("sealed", "false")
            )
            .outputSymbol('[', BlockPredicateWithState.of("anvilcraftplasticraft:condenser_tower")
                .hasState("half", "top_es")
                .hasState("sealed", "false")
            )
            .save(provider);

        generatePlasmaJetBlastingRecipes(provider);
        generateCondenserRecipes(provider);
        generateFluidMixingRecipes(provider);
        generatePlasticMeltSolidLiquidRecipes(provider);

        generateResinTimeWarpRecipes(provider);
    }

    private static void generatePlasmaJetBlastingRecipes(RegistrumRecipeProvider provider) {
        PlasmaJetBlastingRecipe.builder()
            .fluid(dev.dubhe.anvilcraft.init.block.ModFluids.OIL.getId())
            .consume(50)
            .transform(CondenserGas.GASEOUS_OIL)
            .produce(50)
            .save(provider, AnvilcraftPlasticraft.of("plasma_jet_blasting/crude_oil_to_gaseous_oil"));

        PlasmaJetBlastingRecipe.builder()
            .fluid(ResourceLocation.fromNamespaceAndPath("minecraft", "water"))
            .consume(50)
            .transform(CondenserGas.GASEOUS_WATER)
            .produce(50)
            .save(provider, AnvilcraftPlasticraft.of("plasma_jet_blasting/water_to_gaseous_water"));

        PlasmaJetBlastingRecipe.builder()
            .fluid(dev.dubhe.anvilcraft.init.block.ModFluids.EXP_FLUID.getId())
            .consume(50)
            .transform(CondenserGas.GASEOUS_EXPERIENCE)
            .produce(50)
            .save(provider, AnvilcraftPlasticraft.of("plasma_jet_blasting/experience_fluid_to_gaseous_experience"));
    }

    private static void generateCondenserRecipes(RegistrumRecipeProvider provider) {
        CondenserRecipe.builder()
            .gas(CondenserGas.GASEOUS_WATER)
            .consume(50)
            .fluid(ResourceLocation.fromNamespaceAndPath("minecraft", "water"))
            .produce(50)
            .save(provider, AnvilcraftPlasticraft.of("condenser/gaseous_water_to_water"));

        CondenserRecipe.builder()
            .gas(CondenserGas.GASEOUS_EXPERIENCE)
            .consume(50)
            .fluid(dev.dubhe.anvilcraft.init.block.ModFluids.EXP_FLUID.getId())
            .produce(50)
            .save(provider, AnvilcraftPlasticraft.of("condenser/gaseous_experience_to_experience_fluid"));

        CondenserRecipe.builder()
            .gas(CondenserGas.GASEOUS_OIL)
            .consume(10)
            .fluid(ModFluids.HIGH_HEAT_FUEL.getId())
            .produce(10)
            .towerLevel(1)
            .save(provider, AnvilcraftPlasticraft.of("condenser/gaseous_oil_to_high_heat_fuel"));

        CondenserRecipe.builder()
            .gas(CondenserGas.GASEOUS_OIL)
            .consume(30)
            .fluid(ModFluids.PLASTIC_OIL.getId())
            .produce(30)
            .towerLevel(2)
            .save(provider, AnvilcraftPlasticraft.of("condenser/gaseous_oil_to_plastic_oil"));

        CondenserRecipe.builder()
            .gas(CondenserGas.GASEOUS_OIL)
            .consume(10)
            .fluid(ModFluids.CRUDE_OIL_ACID.getId())
            .produce(10)
            .towerLevel(3)
            .save(provider, AnvilcraftPlasticraft.of("condenser/gaseous_oil_to_crude_oil_acid"));
    }

    private static void generateFluidMixingRecipes(RegistrumRecipeProvider provider) {
        FluidMixingRecipe.builder()
            .requires(ModFluids.HIGH_HEAT_FUEL.get(), 1)
            .requires(ModFluids.CRUDE_OIL_ACID.get(), 1)
            .result(ModFluids.HIGH_HEAT_FUEL.get(), 3)
            .consumeMaximum()
            .save(provider, AnvilcraftPlasticraft.of("fluid_mixing/high_heat_fuel_enrichment"));

        FluidMixingRecipe.builder()
            .requires(ModFluids.PLASTIC_OIL.get(), 1)
            .requires(ModFluids.CRUDE_OIL_ACID.get(), 1)
            .result(ModFluids.PLASTIC_OIL.get(), 3)
            .consumeMaximum()
            .save(provider, AnvilcraftPlasticraft.of("fluid_mixing/plastic_oil_enrichment"));

        FluidMixingRecipe.builder()
            .requires(ModFluids.UNIVERSAL_PLASTIC_MELT.get(), 1000)
            .requires(Fluids.WATER, 1000)
            .result(dev.anvilcraft.plasticraft.init.item.ModItems.UNIVERSAL_PLASTIC_GRANULE, 16)
            .save(provider, AnvilcraftPlasticraft.of("fluid_mixing/universal_plastic_melt_with_water"));

        FluidMixingRecipe.builder()
            .requires(ModFluids.UNIVERSAL_PLASTIC_MELT.get(), 1000)
            .requires(dev.dubhe.anvilcraft.init.block.ModFluids.POWDER_SNOW.get(), 1000)
            .result(dev.anvilcraft.plasticraft.init.item.ModItems.UNIVERSAL_PLASTIC_GRANULE, 16)
            .save(provider, AnvilcraftPlasticraft.of("fluid_mixing/universal_plastic_melt_with_powder_snow"));
    }

    private static void generatePlasticMeltSolidLiquidRecipes(RegistrumRecipeProvider provider) {
        SolidLiquidRecipe.builder()
            .cauldron(ModFluids.UNIVERSAL_PLASTIC_MELT.getId())
            .consume(1000)
            .requires(ModItemTags.COLD_ITEMS)
            .result(dev.anvilcraft.plasticraft.init.item.ModItems.UNIVERSAL_PLASTIC_GRANULE, 16)
            .save(provider, AnvilcraftPlasticraft.of("solid_liquid/cool_universal_plastic_melt"));

        for (DyeColor color : DyeColor.values()) {
            DyeItem dye = DyeItem.byColor(color);
            SolidLiquidRecipe.builder()
                .cauldron(ModFluids.UNIVERSAL_PLASTIC_MELT.getId())
                .transform(ModFluids.UNIVERSAL_PLASTIC_MELT.getId())
                .requires(dye)
                .save(provider, AnvilcraftPlasticraft.of("solid_liquid/dye_universal_plastic_melt_" + color.getName()));
        }
    }

    private static ResourceKey<net.minecraft.world.level.block.Block> blockKey(
        net.minecraft.world.level.block.Block block
    ) {
        return ResourceKey.create(Registries.BLOCK, BuiltInRegistries.BLOCK.getKey(block));
    }

    private static ResourceKey<Item> itemKey(ItemLike item) {
        return ResourceKey.create(Registries.ITEM, BuiltInRegistries.ITEM.getKey(item.asItem()));
    }

    private static BlockPredicateWithState condenserPipe(Direction.Axis axis) {
        return BlockPredicateWithState.of("anvilcraft:pipe_straight")
            .hasState(PipeBlock.AXIS, axis)
            .hasState(PipeBlock.HAS_END_START, true)
            .hasState(PipeBlock.HAS_END_END, true)
            .hasState(PipeBlock.HAS_CHECK_VALVE, true)
            .hasState(PipeBlock.WATERLOGGED, false);
    }

    private static BlockPredicateWithState condenserTowerPart(Cube3x3PartHalf part) {
        return BlockPredicateWithState.of(ModBlocks.CONDENSER_TOWER.get())
            .hasState(CondenserTowerBlock.HALF, part)
            .hasState(CondenserTowerBlock.SEALED, false);
    }

    private static ItemIngredientPredicate resinAnvilVariant(int modelVariant) {
        return ItemIngredientPredicate.Builder.item()
            .of(ModBlocks.RESIN_ANVIL.asItem())
            .hasComponents(DataComponentPredicate.builder()
                .expect(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(modelVariant))
                .build())
            .withSubPredicate(
                LibItemSubPredicates.NOT.get(),
                NotPredicate.of(ModItemSubPredicates.SAVED_ENTITY.get(), ItemSavedEntityPredicate.any())
            )
            .build();
    }

    private static void generateResinTimeWarpRecipes(RegistrumRecipeProvider provider) {
        TimeWarpRecipe.builder()
            .fluid(ModFluids.liquidHighViscosityResinId())
            .consume(1000)
            .result(ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK)
            .unlockedBy(
                "has_liquid_high_viscosity_resin_bucket",
                provider.has(dev.anvilcraft.plasticraft.init.item.ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET)
            )
            .save(provider, AnvilcraftPlasticraft.of("time_warp/high_viscosity_resin_block"));

        TimeWarpRecipe.builder()
            .requires(ItemIngredientPredicate.Builder.item()
                .of(ModBlocks.RESIN_ANVIL.asItem())
                .withSubPredicate(
                    LibItemSubPredicates.NOT.get(),
                    NotPredicate.of(ModItemSubPredicates.SAVED_ENTITY.get(), ItemSavedEntityPredicate.any())
                )
                .build())
            .result(dev.dubhe.anvilcraft.init.block.ModBlocks.AMBER_BLOCK)
            .unlockedBy("has_resin_anvil", provider.has(ModBlocks.RESIN_ANVIL.asItem()))
            .save(provider, AnvilcraftPlasticraft.of("time_warp/resin_anvil_to_amber"));

        ExtendInWorldRecipeBuilder.extendCompatible(ModRecipeTriggers.ON_ANVIL_FALL_ON)
            .hasCauldron(0, -1, 0)
            .hasBlock(builder -> builder
                .of(dev.dubhe.anvilcraft.init.block.ModBlocks.CORRUPTED_BEACON.get())
                .with(CorruptedBeaconBlock.LIT, true)
                .offset(0, -2, 0))
            .hasItemIngredient(builder -> builder
                .of(ModBlocks.RESIN_ANVIL.asItem())
                .offset(0.0, -0.375, 0.0)
                .range(0.75, 0.75, 0.75)
                .with(ModItemSubPredicates.SAVED_ENTITY.get(), ItemSavedEntityPredicate.any())
                .with(
                    LibItemSubPredicates.NOT.get(),
                    NotPredicate.of(ModItemSubPredicates.SAVED_ENTITY.get(), ItemSavedEntityPredicate.monster())
                )
                .saveComponent(ModComponents.SAVED_ENTITY, AnvilcraftPlasticraft.of("saved_entity")))
            .spawnItem(builder -> builder
                .item(dev.dubhe.anvilcraft.init.block.ModBlocks.MOB_AMBER_BLOCK)
                .offset(0.0, -0.75, 0.0)
                .applyComponent(ModComponents.SAVED_ENTITY, AnvilcraftPlasticraft.of("saved_entity")))
            .maxEfficiency(1)
            .unlockedBy("has_resin_anvil", provider.has(ModBlocks.RESIN_ANVIL.asItem()))
            .group("time_warp")
            .icon(dev.dubhe.anvilcraft.init.block.ModBlocks.MOB_AMBER_BLOCK.asStack())
            .save(provider, AnvilcraftPlasticraft.of("resin_anvil_mob_amber"));

        ExtendInWorldRecipeBuilder.extendCompatible(ModRecipeTriggers.ON_ANVIL_FALL_ON)
            .hasCauldron(0, -1, 0)
            .hasBlock(builder -> builder
                .of(dev.dubhe.anvilcraft.init.block.ModBlocks.CORRUPTED_BEACON.get())
                .with(CorruptedBeaconBlock.LIT, true)
                .offset(0, -2, 0))
            .hasItemIngredient(builder -> builder
                .of(ModBlocks.RESIN_ANVIL.asItem())
                .offset(0.0, -0.375, 0.0)
                .range(0.75, 0.75, 0.75)
                .with(ModItemSubPredicates.SAVED_ENTITY.get(), ItemSavedEntityPredicate.monster())
                .saveComponent(ModComponents.SAVED_ENTITY, AnvilcraftPlasticraft.of("saved_entity")))
            .out(new ResentmentAmberOutcome(
                new Vec3(0.0, -0.75, 0.0),
                AnvilcraftPlasticraft.of("saved_entity")
            ))
            .maxEfficiency(1)
            .unlockedBy("has_resin_anvil", provider.has(ModBlocks.RESIN_ANVIL.asItem()))
            .group("time_warp")
            .icon(dev.dubhe.anvilcraft.init.block.ModBlocks.RESENTFUL_AMBER_BLOCK.asStack())
            .save(provider, AnvilcraftPlasticraft.of("resin_anvil_resentful_amber"));

        TimeWarpRecipe.builder()
            .requires(ItemIngredientPredicate.Builder.item()
                .of(ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
                .withSubPredicate(
                    LibItemSubPredicates.NOT.get(),
                    NotPredicate.of(ModItemSubPredicates.SAVED_ENTITY.get(), ItemSavedEntityPredicate.any())
                )
                .build())
            .result(dev.dubhe.anvilcraft.init.block.ModBlocks.AMBER_BLOCK)
            .unlockedBy(
                "has_high_viscosity_resin_block",
                provider.has(ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
            )
            .save(provider, AnvilcraftPlasticraft.of("time_warp/high_viscosity_resin_to_amber"));

        ExtendInWorldRecipeBuilder.extendCompatible(ModRecipeTriggers.ON_ANVIL_FALL_ON)
            .hasCauldron(0, -1, 0)
            .hasBlock(builder -> builder
                .of(dev.dubhe.anvilcraft.init.block.ModBlocks.CORRUPTED_BEACON.get())
                .with(CorruptedBeaconBlock.LIT, true)
                .offset(0, -2, 0))
            .hasItemIngredient(builder -> builder
                .of(ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
                .offset(0.0, -0.375, 0.0)
                .range(0.75, 0.75, 0.75)
                .with(
                    LibItemSubPredicates.NOT.get(),
                    NotPredicate.of(ModItemSubPredicates.SAVED_ENTITY.get(), ItemSavedEntityPredicate.monster())
                )
                .saveComponent(ModComponents.SAVED_ENTITY, AnvilcraftPlasticraft.of("saved_entity")))
            .spawnItem(builder -> builder
                .item(dev.dubhe.anvilcraft.init.block.ModBlocks.MOB_AMBER_BLOCK)
                .offset(0.0, -0.75, 0.0)
                .applyComponent(ModComponents.SAVED_ENTITY, AnvilcraftPlasticraft.of("saved_entity")))
            .maxEfficiency(1)
            .unlockedBy(
                "has_high_viscosity_resin_block",
                provider.has(ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
            )
            .group("time_warp")
            .icon(dev.dubhe.anvilcraft.init.block.ModBlocks.MOB_AMBER_BLOCK.asStack())
            .save(provider, AnvilcraftPlasticraft.of("high_viscosity_resin_mob_amber"));

        ExtendInWorldRecipeBuilder.extendCompatible(ModRecipeTriggers.ON_ANVIL_FALL_ON)
            .hasCauldron(0, -1, 0)
            .hasBlock(builder -> builder
                .of(dev.dubhe.anvilcraft.init.block.ModBlocks.CORRUPTED_BEACON.get())
                .with(CorruptedBeaconBlock.LIT, true)
                .offset(0, -2, 0))
            .hasItemIngredient(builder -> builder
                .of(ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
                .offset(0.0, -0.375, 0.0)
                .range(0.75, 0.75, 0.75)
                .with(ModItemSubPredicates.SAVED_ENTITY.get(), ItemSavedEntityPredicate.monster())
                .saveComponent(ModComponents.SAVED_ENTITY, AnvilcraftPlasticraft.of("saved_entity")))
            .out(new ResentmentAmberOutcome(
                new Vec3(0.0, -0.75, 0.0),
                AnvilcraftPlasticraft.of("saved_entity")
            ))
            .maxEfficiency(1)
            .unlockedBy(
                "has_high_viscosity_resin_block",
                provider.has(ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
            )
            .group("time_warp")
            .icon(dev.dubhe.anvilcraft.init.block.ModBlocks.RESENTFUL_AMBER_BLOCK.asStack())
            .save(provider, AnvilcraftPlasticraft.of("high_viscosity_resin_resentful_amber"));
    }
}
