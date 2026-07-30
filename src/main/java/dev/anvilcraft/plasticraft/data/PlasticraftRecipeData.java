package dev.anvilcraft.plasticraft.data;

import dev.anvilcraft.lib.v2.recipe.data.advancement.predicate.item.NotPredicate;
import dev.anvilcraft.lib.v2.recipe.init.LibItemSubPredicates;
import dev.anvilcraft.lib.v2.registrum.providers.ProviderType;
import dev.anvilcraft.lib.v2.registrum.providers.RegistrumRecipeProvider;
import dev.anvilcraft.lib.v2.util.predicate.ItemIngredientPredicate;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import dev.anvilcraft.plasticraft.init.item.ModItemTags;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.recipe.CondenserGas;
import dev.anvilcraft.plasticraft.recipe.CondenserRecipe;
import dev.anvilcraft.plasticraft.recipe.FluidFastCookingRecipe;
import dev.anvilcraft.plasticraft.recipe.PlasmaJetBlastingRecipe;
import dev.dubhe.anvilcraft.block.CorruptedBeaconBlock;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.init.item.ModItemSubPredicates;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.init.recipe.ModRecipeTriggers;
import dev.dubhe.anvilcraft.item.property.predicate.ItemSavedEntityPredicate;
import dev.dubhe.anvilcraft.recipe.FluidMixingRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.builder.ExtendInWorldRecipeBuilder;
import dev.dubhe.anvilcraft.recipe.anvil.outcome.ResentmentAmberOutcome;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.FastCookingRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.SolidLiquidRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.TimeWarpRecipe;
import dev.dubhe.anvilcraft.recipe.multiblock.BlockPredicateWithState;
import dev.dubhe.anvilcraft.recipe.multiblock.MultiblockConversionRecipe;
import dev.dubhe.anvilcraft.recipe.multiblock.MultiblockRecipe;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.anvilcraft.plasticraft.AnvilcraftPlasticraft.REGISTRUM;
import static dev.anvilcraft.plasticraft.init.item.ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET;
import static dev.anvilcraft.plasticraft.init.item.ModItems.RESIN_ANVIL_HAMMER;
import static dev.anvilcraft.plasticraft.init.item.ModItems.UNIVERSAL_PLASTIC_GRANULE;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.AMBER_BLOCK;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.CORRUPTED_BEACON;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.MOB_AMBER_BLOCK;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.RESENTFUL_AMBER_BLOCK;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK;
import static dev.dubhe.anvilcraft.init.block.ModFluids.EXP_FLUID;
import static dev.dubhe.anvilcraft.init.block.ModFluids.OIL;
import static dev.dubhe.anvilcraft.init.block.ModFluids.POWDER_SNOW;

/** 只负责 Plasticraft 配方数据的注册与构建。 */
public final class PlasticraftRecipeData {
    private PlasticraftRecipeData() {
    }

    public static void register() {
        REGISTRUM.addDataGenerator(ProviderType.RECIPE, PlasticraftRecipeData::generateRecipes);
    }

    private static void generateRecipes(RegistrumRecipeProvider provider) {
        // 有序合成：用树脂块构成砧面、树脂构成砧腰和底座，产出初期弹性树脂砧。
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.RESIN_ANVIL.asItem())
            .pattern("BBB")
            .pattern(" R ")
            .pattern("RRR")
            .define('B', Ingredient.of(RESIN_BLOCK.get()))
            .define('R', Ingredient.of(ModItems.RESIN.get()))
            .unlockedBy("has_resin", RegistrumRecipeProvider.has(ModItems.RESIN))
            .unlockedBy("has_resin_block", RegistrumRecipeProvider.has(RESIN_BLOCK))
            .save(provider, AnvilcraftPlasticraft.of("resin_anvil"));

        // 有序合成：将树脂砧、避雷针和硬化树脂竖直组合，产出轻量树脂砧锤。
        ShapedRecipeBuilder.shaped(
            RecipeCategory.TOOLS,
            RESIN_ANVIL_HAMMER.get()
        )
            .pattern("A")
            .pattern("L")
            .pattern("R")
            .define('A', Ingredient.of(ModBlocks.RESIN_ANVIL.asItem()))
            .define('L', Ingredient.of(Items.LIGHTNING_ROD))
            .define('R', Ingredient.of(ModItems.HARDEND_RESIN.get()))
            .unlockedBy("has_resin_anvil", RegistrumRecipeProvider.has(ModBlocks.RESIN_ANVIL.asItem()))
            .unlockedBy("has_hardend_resin", RegistrumRecipeProvider.has(ModItems.HARDEND_RESIN))
            .save(provider, AnvilcraftPlasticraft.of("resin_anvil_hammer"));

        // 有序合成：以磁铁锭替代普通树脂砧的中心树脂，直接产出带磁化数据的树脂砧。
        // 原版 builder 无法给结果写入自定义组件，因此在这里显式构造配方和解锁进度。
        Map<Character, Ingredient> key = new LinkedHashMap<>();
        key.put('B', Ingredient.of(RESIN_BLOCK.get()));
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
            .addCriterion("has_resin", RegistrumRecipeProvider.has(ModItems.RESIN))
            .addCriterion("has_resin_block", RegistrumRecipeProvider.has(RESIN_BLOCK))
            .addCriterion("has_magnet_ingot", RegistrumRecipeProvider.has(ModItems.MAGNET_INGOT))
            .rewards(AdvancementRewards.Builder.recipe(magneticId))
            .requirements(AdvancementRequirements.Strategy.OR);
        provider.accept(
            magneticId,
            magneticRecipe,
            advancement.build(magneticId.withPrefix("recipes/building_blocks/"))
        );

        // 快速烹饪：未磁化且未捕获生物的树脂砧受热后，转化为普通硬化树脂砧。
        // 自定义模型数据用于让普通与磁化输入互斥，避免两份配方同时匹配。
        ItemIngredientPredicate ordinaryInput = resinAnvilVariant(0);
        ItemStack ordinaryOutput = ModBlocks.HARDEND_RESIN_ANVIL.asStack();
        PlasticItemData.setMaterial(ordinaryOutput, "hardened_resin");
        FastCookingRecipe.builder()
            .requires(ordinaryInput)
            .result(ordinaryOutput)
            .save(provider, AnvilcraftPlasticraft.of("fast_cooking/harden_resin_anvil"));

        // 快速烹饪：磁化且未捕获生物的树脂砧受热后，保留磁化状态并完成硬化。
        ItemStack magneticOutput = ModBlocks.HARDEND_RESIN_ANVIL.asStack();
        PlasticItemData.setMaterial(magneticOutput, "hardened_resin");
        PlasticItemData.setMagnetized(magneticOutput, true);
        FastCookingRecipe.builder()
            .requires(resinAnvilVariant(1))
            .result(magneticOutput)
            .save(provider, AnvilcraftPlasticraft.of("fast_cooking/harden_magnetic_resin_anvil"));

        // 流体快速烹饪：水釜消耗树脂、黏液球和石灰粉，转化为一桶液态高黏度树脂。
        FluidFastCookingRecipe.fluidBuilder()
            .cauldron(Blocks.WATER_CAULDRON)
            .consume(1000)
            .transform(ModFluids.liquidHighViscosityResinId())
            .produce(1000)
            .requires(ModItems.RESIN.get(), 4)
            .requires(Items.SLIME_BALL, 4)
            .requires(ModItems.LIME_POWDER.get())
            .unlockedBy("has_resin", RegistrumRecipeProvider.has(ModItems.RESIN))
            .unlockedBy("has_slime_ball", RegistrumRecipeProvider.has(Items.SLIME_BALL))
            .unlockedBy("has_lime_powder", RegistrumRecipeProvider.has(ModItems.LIME_POWDER))
            .save(provider, AnvilcraftPlasticraft.of("fast_cooking/liquid_high_viscosity_resin"));

        // 有序合成：用七份硬化树脂围成釜体，产出普通硬化树脂釜。
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ModBlocks.HARDEND_RESIN_CAULDRON.asItem())
            .pattern("H H")
            .pattern("H H")
            .pattern("HHH")
            .define('H', Ingredient.of(ModItems.HARDEND_RESIN.get()))
            .unlockedBy("has_hardend_resin", RegistrumRecipeProvider.has(ModItems.HARDEND_RESIN))
            .save(provider, AnvilcraftPlasticraft.of("hardend_resin_cauldron"));

        // 有序合成：横向排列三份硬化树脂，产出用于密封催化容器的压盖。
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ModBlocks.CATALYTIC_PRESS_LID.asItem())
            .pattern("HHH")
            .define('H', Ingredient.of(ModItems.HARDEND_RESIN.get()))
            .unlockedBy("has_hardend_resin", RegistrumRecipeProvider.has(ModItems.HARDEND_RESIN))
            .save(provider, AnvilcraftPlasticraft.of("catalytic_press_lid"));

        // 有序合成：在硬化树脂釜两侧加入磁铁锭，直接产出带磁化数据的硬化树脂釜。
        ItemStack magneticCauldron = ModBlocks.HARDEND_RESIN_CAULDRON.asStack();
        PlasticItemData.setMaterial(magneticCauldron, "hardened_resin");
        PlasticItemData.setMagnetized(magneticCauldron, true);
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, magneticCauldron)
            .pattern("M M")
            .pattern("H H")
            .pattern("HHH")
            .define('M', Ingredient.of(ModItems.MAGNET_INGOT.get()))
            .define('H', Ingredient.of(ModItems.HARDEND_RESIN.get()))
            .unlockedBy("has_hardend_resin", RegistrumRecipeProvider.has(ModItems.HARDEND_RESIN))
            .unlockedBy("has_magnet_ingot", RegistrumRecipeProvider.has(ModItems.MAGNET_INGOT))
            .save(provider, AnvilcraftPlasticraft.of("magnetic_hardend_resin_cauldron"));

        // 多方块展示配方：声明冷凝塔搭建前的 3x3x3 树脂、止回管道、黄铜柱和活板门结构。
        // 该配方供配方查看器展示结构，不负责把结构替换成冷凝塔方块。
        MultiblockRecipe.builder("anvilcraftplasticraft:condenser_tower", 1)
            .layer("DCD", "CFC", "DCD")
            .layer(" C ", "C C", " C ")
            .layer(" E ", "ABA", " E ")
            .symbol('A', BlockPredicateWithState.of("anvilcraft:pipe_straight")
                .hasState("axis", "x")
                .hasState("has_end_start", "true")
                .hasState("has_end_end", "true")
                .hasState("has_check_valve", "true")
                .hasState("waterlogged", "false")
            )
            .symbol('B', BlockPredicateWithState.of("minecraft:copper_trapdoor")
                .hasState("facing", "north")
                .hasState("open", "false")
                .hasState("half", "top")
                .hasState("waterlogged", "false")
            )
            .symbol('C', BlockPredicateWithState.of("anvilcraft:cut_brass_pillar")
                .hasState("axis", "y")
            )
            .symbol('D', "anvilcraftplasticraft:high_viscosity_resin_block")
            .symbol('E', BlockPredicateWithState.of("anvilcraft:pipe_straight")
                .hasState("axis", "z")
                .hasState("has_end_start", "true")
                .hasState("has_end_end", "true")
                .hasState("has_check_valve", "true")
                .hasState("waterlogged", "false")
            )
            .symbol('F', BlockPredicateWithState.of("minecraft:copper_trapdoor")
                .hasState("facing", "north")
                .hasState("open", "false")
                .hasState("half", "bottom")
                .hasState("waterlogged", "false")
            )
            // 显式指定附属模组命名空间，避免 AnvilCraft 构建器按结果物品回退到本体命名空间。
            .save(provider, AnvilcraftPlasticraft.of("multiblock/condenser_tower"));

        // 多方块转换配方：识别与上方展示配方相同的 3x3x3 结构，并逐格转换为冷凝塔的 27 个部件。
        // 每个输出部件都固定 half 方位且保持 sealed=false，确保生成后可立即组成未封顶塔层。
        MultiblockConversionRecipe.builder()
            .inputLayer("DCD", "CFC", "DCD")
            .inputLayer(" C ", "C C", " C ")
            .inputLayer(" E ", "ABA", " E ")
            .inputSymbol('A', BlockPredicateWithState.of("anvilcraft:pipe_straight")
                .hasState("axis", "x")
                .hasState("has_end_start", "true")
                .hasState("has_end_end", "true")
                .hasState("has_check_valve", "true")
                .hasState("waterlogged", "false")
            )
            .inputSymbol('B', BlockPredicateWithState.of("minecraft:copper_trapdoor")
                .hasState("facing", "north")
                .hasState("open", "false")
                .hasState("half", "top")
                .hasState("waterlogged", "false")
            )
            .inputSymbol('C', BlockPredicateWithState.of("anvilcraft:cut_brass_pillar")
                .hasState("axis", "y")
            )
            .inputSymbol('D', "anvilcraftplasticraft:high_viscosity_resin_block")
            .inputSymbol('E', BlockPredicateWithState.of("anvilcraft:pipe_straight")
                .hasState("axis", "z")
                .hasState("has_end_start", "true")
                .hasState("has_end_end", "true")
                .hasState("has_check_valve", "true")
                .hasState("waterlogged", "false")
            )
            .inputSymbol('F', BlockPredicateWithState.of("minecraft:copper_trapdoor")
                .hasState("facing", "north")
                .hasState("open", "false")
                .hasState("half", "bottom")
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
            // 转换配方没有结果物品，必须显式给 ID，不能让构建器以 minecraft:air 推导文件名。
            .save(provider, AnvilcraftPlasticraft.of("multiblock_conversion/condenser_tower"));

        generatePlasmaJetBlastingRecipes(provider);
        generateCondenserRecipes(provider);
        generateFluidMixingRecipes(provider);
        generatePlasticMeltSolidLiquidRecipes(provider);

        generateResinTimeWarpRecipes(provider);
    }

    private static void generatePlasmaJetBlastingRecipes(RegistrumRecipeProvider provider) {
        // 等离子喷流配方：每次把 50 mB 原油完全汽化为等量气态原油，供冷凝塔分层处理。
        PlasmaJetBlastingRecipe.builder()
            .fluid(OIL.getId())
            .consume(50)
            .transform(CondenserGas.GASEOUS_OIL)
            .produce(50)
            .save(provider, AnvilcraftPlasticraft.of("plasma_jet_blasting/crude_oil_to_gaseous_oil"));

        // 等离子喷流配方：每次把 50 mB 水完全汽化为等量气态水，随后可冷凝回收。
        PlasmaJetBlastingRecipe.builder()
            .fluid(ResourceLocation.fromNamespaceAndPath("minecraft", "water"))
            .consume(50)
            .transform(CondenserGas.GASEOUS_WATER)
            .produce(50)
            .save(provider, AnvilcraftPlasticraft.of("plasma_jet_blasting/water_to_gaseous_water"));

        // 等离子喷流配方：每次把 50 mB 经验液完全汽化为等量气态经验，随后可冷凝回收。
        PlasmaJetBlastingRecipe.builder()
            .fluid(EXP_FLUID.getId())
            .consume(50)
            .transform(CondenserGas.GASEOUS_EXPERIENCE)
            .produce(50)
            .save(provider, AnvilcraftPlasticraft.of("plasma_jet_blasting/experience_fluid_to_gaseous_experience"));
    }

    private static void generateCondenserRecipes(RegistrumRecipeProvider provider) {
        // 冷凝配方：任意有效塔层把 50 mB 气态水还原为等量水，不要求特定层级。
        CondenserRecipe.builder()
            .gas(CondenserGas.GASEOUS_WATER)
            .consume(50)
            .fluid(ResourceLocation.fromNamespaceAndPath("minecraft", "water"))
            .produce(50)
            .save(provider, AnvilcraftPlasticraft.of("condenser/gaseous_water_to_water"));

        // 冷凝配方：任意有效塔层把 50 mB 气态经验还原为等量经验液。
        CondenserRecipe.builder()
            .gas(CondenserGas.GASEOUS_EXPERIENCE)
            .consume(50)
            .fluid(EXP_FLUID.getId())
            .produce(50)
            .save(provider, AnvilcraftPlasticraft.of("condenser/gaseous_experience_to_experience_fluid"));

        // 冷凝配方：冷凝塔第一层从气态原油中分离 10 mB 高热燃料。
        CondenserRecipe.builder()
            .gas(CondenserGas.GASEOUS_OIL)
            .consume(10)
            .fluid(ModFluids.HIGH_HEAT_FUEL.getId())
            .produce(10)
            .towerLevel(1)
            .save(provider, AnvilcraftPlasticraft.of("condenser/gaseous_oil_to_high_heat_fuel"));

        // 冷凝配方：冷凝塔第二层从气态原油中分离 30 mB 塑料油，作为塑料加工主原料。
        CondenserRecipe.builder()
            .gas(CondenserGas.GASEOUS_OIL)
            .consume(30)
            .fluid(ModFluids.PLASTIC_OIL.getId())
            .produce(30)
            .towerLevel(2)
            .save(provider, AnvilcraftPlasticraft.of("condenser/gaseous_oil_to_plastic_oil"));

        // 冷凝配方：冷凝塔第三层从气态原油中分离 10 mB 原油精华。
        CondenserRecipe.builder()
            .gas(CondenserGas.GASEOUS_OIL)
            .consume(10)
            .fluid(ModFluids.CRUDE_OIL_ACID.getId())
            .produce(10)
            .towerLevel(3)
            .save(provider, AnvilcraftPlasticraft.of("condenser/gaseous_oil_to_crude_oil_acid"));
    }

    private static void generateFluidMixingRecipes(RegistrumRecipeProvider provider) {
        // 流体混合配方：高热燃料与原油精华按 1:1 混合，并按最大可消费量增产为三倍高热燃料。
        FluidMixingRecipe.builder()
            .requires(ModFluids.HIGH_HEAT_FUEL.get(), 1)
            .requires(ModFluids.CRUDE_OIL_ACID.get(), 1)
            .result(ModFluids.HIGH_HEAT_FUEL.get(), 3)
            .consumeMaximum()
            .save(provider, AnvilcraftPlasticraft.of("fluid_mixing/high_heat_fuel_enrichment"));

        // 流体混合配方：塑料油与原油精华按 1:1 混合，并按最大可消费量增产为三倍塑料油。
        FluidMixingRecipe.builder()
            .requires(ModFluids.PLASTIC_OIL.get(), 1)
            .requires(ModFluids.CRUDE_OIL_ACID.get(), 1)
            .result(ModFluids.PLASTIC_OIL.get(), 3)
            .consumeMaximum()
            .save(provider, AnvilcraftPlasticraft.of("fluid_mixing/plastic_oil_enrichment"));

        // 流体混合配方：一桶通用塑料熔体与一桶水冷却混合，凝固为 16 个通用塑料颗粒。
        FluidMixingRecipe.builder()
            .requires(ModFluids.UNIVERSAL_PLASTIC_MELT.get(), 1000)
            .requires(Fluids.WATER, 1000)
            .result(UNIVERSAL_PLASTIC_GRANULE, 16)
            .save(provider, AnvilcraftPlasticraft.of("fluid_mixing/universal_plastic_melt_with_water"));

        // 流体混合配方：一桶通用塑料熔体与一桶细雪冷却混合，同样凝固为 16 个塑料颗粒。
        FluidMixingRecipe.builder()
            .requires(ModFluids.UNIVERSAL_PLASTIC_MELT.get(), 1000)
            .requires(POWDER_SNOW.get(), 1000)
            .result(UNIVERSAL_PLASTIC_GRANULE, 16)
            .save(provider, AnvilcraftPlasticraft.of("fluid_mixing/universal_plastic_melt_with_powder_snow"));
    }

    private static void generatePlasticMeltSolidLiquidRecipes(RegistrumRecipeProvider provider) {
        // 固液配方：向一桶通用塑料熔体投入任意冷却物品，消耗熔体并产出 16 个塑料颗粒。
        SolidLiquidRecipe.builder()
            .cauldron(ModFluids.UNIVERSAL_PLASTIC_MELT.getId())
            .consume(1000)
            .requires(ModItemTags.COLD_ITEMS)
            .result(UNIVERSAL_PLASTIC_GRANULE, 16)
            .save(provider, AnvilcraftPlasticraft.of("solid_liquid/cool_universal_plastic_melt"));

        // 固液配方（每种染料各一份）：投入染料后不消耗熔体，只把整釜熔体转换为对应颜色。
        for (DyeColor color : DyeColor.values()) {
            DyeItem dye = DyeItem.byColor(color);
            SolidLiquidRecipe.builder()
                .cauldron(ModFluids.UNIVERSAL_PLASTIC_MELT.getId())
                .transform(ModFluids.UNIVERSAL_PLASTIC_MELT.getId())
                .requires(dye)
                .save(provider, AnvilcraftPlasticraft.of("solid_liquid/dye_universal_plastic_melt_" + color.getName()));
        }
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
        // 时间扭曲配方：一桶液态高黏度树脂经过漫长时间固化，产出高黏度树脂块。
        TimeWarpRecipe.builder()
            .fluid(ModFluids.liquidHighViscosityResinId())
            .consume(1000)
            .result(ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK)
            .unlockedBy(
                "has_liquid_high_viscosity_resin_bucket",
                RegistrumRecipeProvider.has(LIQUID_HIGH_VISCOSITY_RESIN_BUCKET)
            )
            .save(provider, AnvilcraftPlasticraft.of("time_warp/high_viscosity_resin_block"));

        // 时间扭曲配方：未捕获生物的树脂砧随时间完全琥珀化，转化为普通琥珀块。
        TimeWarpRecipe.builder()
            .requires(ItemIngredientPredicate.Builder.item()
                .of(ModBlocks.RESIN_ANVIL.asItem())
                .withSubPredicate(
                    LibItemSubPredicates.NOT.get(),
                    NotPredicate.of(ModItemSubPredicates.SAVED_ENTITY.get(), ItemSavedEntityPredicate.any())
                )
                .build())
            .result(AMBER_BLOCK)
            .unlockedBy("has_resin_anvil", RegistrumRecipeProvider.has(ModBlocks.RESIN_ANVIL.asItem()))
            .save(provider, AnvilcraftPlasticraft.of("time_warp/resin_anvil_to_amber"));

        // 扩展世界配方：树脂砧捕获非敌对生物后，在点亮的腐化信标和釜结构上受砧击，
        // 保存生物组件并生成对应的生物琥珀块。
        ExtendInWorldRecipeBuilder.extendCompatible(ModRecipeTriggers.ON_ANVIL_FALL_ON)
            .hasCauldron(0, -1, 0)
            .hasBlock(builder -> builder
                .of(CORRUPTED_BEACON.get())
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
                .item(MOB_AMBER_BLOCK)
                .offset(0.0, -0.75, 0.0)
                .applyComponent(ModComponents.SAVED_ENTITY, AnvilcraftPlasticraft.of("saved_entity")))
            .maxEfficiency(1)
            .unlockedBy("has_resin_anvil", RegistrumRecipeProvider.has(ModBlocks.RESIN_ANVIL.asItem()))
            .group("time_warp")
            .icon(MOB_AMBER_BLOCK.asStack())
            .save(provider, AnvilcraftPlasticraft.of("resin_anvil_mob_amber"));

        // 扩展世界配方：树脂砧捕获敌对生物后，在相同结构上受砧击，
        // 将保存的生物及怨念数据写入怨念琥珀结果。
        ExtendInWorldRecipeBuilder.extendCompatible(ModRecipeTriggers.ON_ANVIL_FALL_ON)
            .hasCauldron(0, -1, 0)
            .hasBlock(builder -> builder
                .of(CORRUPTED_BEACON.get())
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
            .unlockedBy("has_resin_anvil", RegistrumRecipeProvider.has(ModBlocks.RESIN_ANVIL.asItem()))
            .group("time_warp")
            .icon(RESENTFUL_AMBER_BLOCK.asStack())
            .save(provider, AnvilcraftPlasticraft.of("resin_anvil_resentful_amber"));

        // 时间扭曲配方：未捕获生物的高黏度树脂块随时间完全琥珀化，转化为普通琥珀块。
        TimeWarpRecipe.builder()
            .requires(ItemIngredientPredicate.Builder.item()
                .of(ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
                .withSubPredicate(
                    LibItemSubPredicates.NOT.get(),
                    NotPredicate.of(ModItemSubPredicates.SAVED_ENTITY.get(), ItemSavedEntityPredicate.any())
                )
                .build())
            .result(AMBER_BLOCK)
            .unlockedBy(
                "has_high_viscosity_resin_block",
                RegistrumRecipeProvider.has(ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
            )
            .save(provider, AnvilcraftPlasticraft.of("time_warp/high_viscosity_resin_to_amber"));

        // 扩展世界配方：高黏度树脂块捕获非敌对生物后，在点亮的腐化信标和釜结构上受砧击，
        // 保存生物组件并生成对应的生物琥珀块。
        ExtendInWorldRecipeBuilder.extendCompatible(ModRecipeTriggers.ON_ANVIL_FALL_ON)
            .hasCauldron(0, -1, 0)
            .hasBlock(builder -> builder
                .of(CORRUPTED_BEACON.get())
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
                .item(MOB_AMBER_BLOCK)
                .offset(0.0, -0.75, 0.0)
                .applyComponent(ModComponents.SAVED_ENTITY, AnvilcraftPlasticraft.of("saved_entity")))
            .maxEfficiency(1)
            .unlockedBy(
                "has_high_viscosity_resin_block",
                RegistrumRecipeProvider.has(ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
            )
            .group("time_warp")
            .icon(MOB_AMBER_BLOCK.asStack())
            .save(provider, AnvilcraftPlasticraft.of("high_viscosity_resin_mob_amber"));

        // 扩展世界配方：高黏度树脂块捕获敌对生物后，在相同结构上受砧击，
        // 将保存的生物及怨念数据写入怨念琥珀结果。
        ExtendInWorldRecipeBuilder.extendCompatible(ModRecipeTriggers.ON_ANVIL_FALL_ON)
            .hasCauldron(0, -1, 0)
            .hasBlock(builder -> builder
                .of(CORRUPTED_BEACON.get())
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
                RegistrumRecipeProvider.has(ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
            )
            .group("time_warp")
            .icon(RESENTFUL_AMBER_BLOCK.asStack())
            .save(provider, AnvilcraftPlasticraft.of("high_viscosity_resin_resentful_amber"));
    }
}
