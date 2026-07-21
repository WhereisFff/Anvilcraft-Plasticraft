package dev.anvilcraft.plasticraft.data;

import dev.anvilcraft.lib.v2.registrum.providers.ProviderType;
import dev.anvilcraft.lib.v2.registrum.providers.RegistrumRecipeProvider;
import dev.anvilcraft.lib.v2.recipe.data.advancement.predicate.item.NotPredicate;
import dev.anvilcraft.lib.v2.recipe.init.LibItemSubPredicates;
import dev.anvilcraft.lib.v2.util.predicate.ItemIngredientPredicate;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.item.ModItemGroups;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.recipe.FluidFastCookingRecipe;
import dev.anvilcraft.plasticraft.recipe.CondenserGas;
import dev.anvilcraft.plasticraft.recipe.CondenserRecipe;
import dev.anvilcraft.plasticraft.recipe.PlasmaJetBlastingRecipe;
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
            provider.add("tooltip.anvilcraftplasticraft.magnetized", "Magnetized");
            provider.add("tooltip.anvilcraftplasticraft.resin_anvil.captured", "Contains: %s");
            provider.add("tooltip.anvilcraftplasticraft.jade.color", "Material colour: %s");
            provider.add("tooltip.anvilcraftplasticraft.jade.pushable", "Can be pushed");
            provider.add("tooltip.anvilcraftplasticraft.jade.item_count", "%1$s x %2$s");
            provider.add("config.jade.plugin_anvilcraftplasticraft.hardend_resin_anvil", "Hardened Resin Anvil");
            provider.add("config.jade.plugin_anvilcraftplasticraft.condenser_tower", "Condenser Tower");
            provider.add("config.jade.plugin_anvilcraft.fluid_tank", "Fluid Tank");
            provider.add("gui.anvilcraftplasticraft.category.plasma_jet_blasting", "Plasma Jet Blasting");
            provider.add("gui.anvilcraftplasticraft.category.condenser", "Condensation");
            provider.add("jei.anvilcraftplasticraft.gas.gaseous_oil", "Gaseous oil");
            provider.add("jei.anvilcraftplasticraft.gas.gaseous_water", "Gaseous water");
            provider.add("jei.anvilcraftplasticraft.gas.experience_orbs", "Experience orbs");
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
            provider.addTag(dev.anvilcraft.plasticraft.init.block.ModBlockTags.RESIN_SHOCK_COMPATIBLE)
                .add(ResourceKey.create(
                    Registries.BLOCK,
                    BuiltInRegistries.BLOCK.getKey(dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK.get())
                ));
        });
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

        MultiblockRecipe.builder(ModBlocks.CONDENSER_TOWER, 1)
            .layer(" E ", "ABA", " E ")
            .layer(" C ", "C C", " C ")
            .layer("DCD", "CFC", "DCD")
            .symbol('A', condenserPipe())
            .symbol('B', BlockPredicateWithState.of("anvilcraft:heavy_iron_trapdoor")
                .hasState(TrapDoorBlock.HALF, Half.TOP))
            .symbol('C', "anvilcraft:cut_heavy_iron_block")
            .symbol('D', "anvilcraftplasticraft:high_viscosity_resin_block")
            .symbol('E', condenserPipe())
            .symbol('F', BlockPredicateWithState.of("anvilcraft:heavy_iron_trapdoor")
                .hasState(TrapDoorBlock.HALF, Half.BOTTOM))
            .save(provider, AnvilcraftPlasticraft.of("multiblock/condenser_tower"));

        MultiblockConversionRecipe.builder()
            .inputLayer(" E ", "ABA", " E ")
            .inputLayer(" C ", "C C", " C ")
            .inputLayer("DCD", "CFC", "DCD")
            .inputSymbol('A', condenserPipe())
            .inputSymbol('B', BlockPredicateWithState.of("anvilcraft:heavy_iron_trapdoor")
                .hasState(TrapDoorBlock.HALF, Half.TOP))
            .inputSymbol('C', "anvilcraft:cut_heavy_iron_block")
            .inputSymbol('D', "anvilcraftplasticraft:high_viscosity_resin_block")
            .inputSymbol('E', condenserPipe())
            .inputSymbol('F', BlockPredicateWithState.of("anvilcraft:heavy_iron_trapdoor")
                .hasState(TrapDoorBlock.HALF, Half.BOTTOM))
            .outputLayer("ABC", "DEF", "GHI")
            .outputLayer("JKL", "MNO", "PQR")
            .outputLayer("STU", "VWX", "YZ[")
            .outputSymbol('A', condenserTowerPart(Cube3x3PartHalf.BOTTOM_WN))
            .outputSymbol('B', condenserTowerPart(Cube3x3PartHalf.BOTTOM_N))
            .outputSymbol('C', condenserTowerPart(Cube3x3PartHalf.BOTTOM_EN))
            .outputSymbol('D', condenserTowerPart(Cube3x3PartHalf.BOTTOM_W))
            .outputSymbol('E', condenserTowerPart(Cube3x3PartHalf.BOTTOM_CENTER))
            .outputSymbol('F', condenserTowerPart(Cube3x3PartHalf.BOTTOM_E))
            .outputSymbol('G', condenserTowerPart(Cube3x3PartHalf.BOTTOM_WS))
            .outputSymbol('H', condenserTowerPart(Cube3x3PartHalf.BOTTOM_S))
            .outputSymbol('I', condenserTowerPart(Cube3x3PartHalf.BOTTOM_ES))
            .outputSymbol('J', condenserTowerPart(Cube3x3PartHalf.MID_WN))
            .outputSymbol('K', condenserTowerPart(Cube3x3PartHalf.MID_N))
            .outputSymbol('L', condenserTowerPart(Cube3x3PartHalf.MID_EN))
            .outputSymbol('M', condenserTowerPart(Cube3x3PartHalf.MID_W))
            .outputSymbol('N', condenserTowerPart(Cube3x3PartHalf.MID_CENTER))
            .outputSymbol('O', condenserTowerPart(Cube3x3PartHalf.MID_E))
            .outputSymbol('P', condenserTowerPart(Cube3x3PartHalf.MID_WS))
            .outputSymbol('Q', condenserTowerPart(Cube3x3PartHalf.MID_S))
            .outputSymbol('R', condenserTowerPart(Cube3x3PartHalf.MID_ES))
            .outputSymbol('S', condenserTowerPart(Cube3x3PartHalf.TOP_WN))
            .outputSymbol('T', condenserTowerPart(Cube3x3PartHalf.TOP_N))
            .outputSymbol('U', condenserTowerPart(Cube3x3PartHalf.TOP_EN))
            .outputSymbol('V', condenserTowerPart(Cube3x3PartHalf.TOP_W))
            .outputSymbol('W', condenserTowerPart(Cube3x3PartHalf.TOP_CENTER))
            .outputSymbol('X', condenserTowerPart(Cube3x3PartHalf.TOP_E))
            .outputSymbol('Y', condenserTowerPart(Cube3x3PartHalf.TOP_WS))
            .outputSymbol('Z', condenserTowerPart(Cube3x3PartHalf.TOP_S))
            .outputSymbol('[', condenserTowerPart(Cube3x3PartHalf.TOP_ES))
            .save(provider, AnvilcraftPlasticraft.of("multiblock_conversion/condenser_tower"));

        generatePlasmaJetBlastingRecipes(provider);
        generateCondenserRecipes(provider);

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
            .requires(dev.dubhe.anvilcraft.init.item.ModItems.RUBY)
            .transform(dev.dubhe.anvilcraft.init.block.ModFluids.MELT_GEM.getId())
            .produce(100)
            .save(provider, AnvilcraftPlasticraft.of("plasma_jet_blasting/ruby_to_molten_gem"));
        PlasmaJetBlastingRecipe.builder()
            .requires(dev.dubhe.anvilcraft.init.item.ModItems.SAPPHIRE)
            .transform(dev.dubhe.anvilcraft.init.block.ModFluids.MELT_GEM.getId())
            .produce(100)
            .save(provider, AnvilcraftPlasticraft.of("plasma_jet_blasting/sapphire_to_molten_gem"));
        PlasmaJetBlastingRecipe.builder()
            .requires(Items.EMERALD)
            .transform(dev.dubhe.anvilcraft.init.block.ModFluids.MELT_GEM.getId())
            .produce(100)
            .save(provider, AnvilcraftPlasticraft.of("plasma_jet_blasting/emerald_to_molten_gem"));
        PlasmaJetBlastingRecipe.builder()
            .requires(dev.dubhe.anvilcraft.init.item.ModItems.TOPAZ)
            .transform(dev.dubhe.anvilcraft.init.block.ModFluids.MELT_GEM.getId())
            .produce(100)
            .save(provider, AnvilcraftPlasticraft.of("plasma_jet_blasting/topaz_to_molten_gem"));

        PlasmaJetBlastingRecipe.builder()
            .fluid(dev.dubhe.anvilcraft.init.block.ModFluids.EXP_FLUID.getId())
            .consume(50)
            .transform(CondenserGas.EXPERIENCE_ORBS)
            .produce(1)
            .save(provider, AnvilcraftPlasticraft.of("plasma_jet_blasting/experience_fluid_to_orbs"));
    }

    private static void generateCondenserRecipes(RegistrumRecipeProvider provider) {
        CondenserRecipe.builder()
            .gas(CondenserGas.GASEOUS_WATER)
            .consume(250)
            .fluid(ResourceLocation.fromNamespaceAndPath("minecraft", "water"))
            .produce(250)
            .save(provider, AnvilcraftPlasticraft.of("condenser/gaseous_water_to_water"));
    }

    private static BlockPredicateWithState condenserPipe() {
        return BlockPredicateWithState.of("anvilcraft:pipe_straight")
            .hasState(PipeBlock.AXIS, Direction.Axis.X)
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
