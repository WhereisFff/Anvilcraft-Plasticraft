package dev.anvilcraft.plasticraft.data;

import dev.anvilcraft.lib.v2.recipe.data.advancement.predicate.item.NotPredicate;
import dev.anvilcraft.lib.v2.recipe.init.LibItemSubPredicates;
import dev.anvilcraft.lib.v2.registrum.providers.ProviderType;
import dev.anvilcraft.lib.v2.registrum.providers.RegistrumRecipeProvider;
import dev.anvilcraft.lib.v2.util.predicate.BlockStatePredicate;
import dev.anvilcraft.lib.v2.util.predicate.ChanceItemStack;
import dev.anvilcraft.lib.v2.util.predicate.ItemIngredientPredicate;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItemTags;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.recipe.CondenserGas;
import dev.anvilcraft.plasticraft.recipe.CondenserRecipe;
import dev.anvilcraft.plasticraft.recipe.FluidFastCookingRecipe;
import dev.anvilcraft.plasticraft.recipe.PlasmaJetBlastingRecipe;
import dev.anvilcraft.plasticraft.recipe.PlasticMoldingChamberRecipe;
import dev.dubhe.anvilcraft.block.CorruptedBeaconBlock;
import dev.dubhe.anvilcraft.block.fluid.PipeBlock;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.block.ModFluids;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.init.item.ModItemSubPredicates;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.init.recipe.ModRecipeTriggers;
import dev.dubhe.anvilcraft.item.property.component.SavedEntity;
import dev.dubhe.anvilcraft.item.property.predicate.ItemSavedEntityPredicate;
import dev.dubhe.anvilcraft.recipe.FluidMixingRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.builder.ExtendInWorldRecipeBuilder;
import dev.dubhe.anvilcraft.recipe.anvil.outcome.ResentmentAmberOutcome;
import dev.dubhe.anvilcraft.recipe.anvil.outcome.ProduceHeat;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.FastCookingRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.SolidLiquidRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.TimeWarpRecipe;
import dev.dubhe.anvilcraft.recipe.component.HasCauldronSimple;
import dev.dubhe.anvilcraft.recipe.multiblock.MultiblockConversionRecipe;
import dev.dubhe.anvilcraft.recipe.multiblock.MultiblockRecipe;
import dev.dubhe.anvilcraft.util.FluidStackPredicate;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 只负责 Plasticraft 配方数据的注册与构建。 */
public final class PlasticraftRecipeData {
    private PlasticraftRecipeData() {
    }

    public static void register() {
        AnvilcraftPlasticraft.REGISTRUM.addDataGenerator(ProviderType.RECIPE, PlasticraftRecipeData::generateRecipes);
    }

    private static void generateRecipes(RegistrumRecipeProvider provider) {
        // 有序合成：电感灯、活版门与紫水晶碎片作顶，原木作柱，圆石台阶与磁性溜槽作底，产出悦灵休息室。
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PlasticraftBlocks.ALLAY_LOUNGE.asItem())
            .pattern("ITA")
            .pattern("L L")
            .pattern("SMS")
            .define('I', ModBlocks.INDUCTION_LIGHT.get())
            .define('T', ItemTags.TRAPDOORS)
            .define('A', Items.AMETHYST_SHARD)
            .define('L', ItemTags.LOGS)
            .define('S', Items.COBBLESTONE_SLAB)
            .define('M', ModBlocks.MAGNETIC_CHUTE.get())
            .unlockedBy("has_magnetic_chute", RegistrumRecipeProvider.has(ModBlocks.MAGNETIC_CHUTE))
            .save(provider, AnvilcraftPlasticraft.of("allay_lounge"));

        // 有序合成：用树脂块构成砧面、树脂构成砧腰和底座，产出弹性树脂铁砧。
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, PlasticraftBlocks.RESIN_ANVIL.asItem())
            .pattern("BBB")
            .pattern(" R ")
            .pattern("RRR")
            .define('B', Ingredient.of(ModBlocks.RESIN_BLOCK.get()))
            .define('R', Ingredient.of(ModItems.RESIN.get()))
            .unlockedBy("has_resin", RegistrumRecipeProvider.has(ModItems.RESIN))
            .unlockedBy("has_resin_block", RegistrumRecipeProvider.has(ModBlocks.RESIN_BLOCK))
            .save(provider, AnvilcraftPlasticraft.of("resin_anvil"));

        // 有序合成：将树脂砧、避雷针和硬化树脂竖直组合，产出树脂铁砧锤。
        ShapedRecipeBuilder.shaped(
            RecipeCategory.TOOLS,
            PlasticraftItems.RESIN_ANVIL_HAMMER.get()
        )
            .pattern("A")
            .pattern("L")
            .pattern("R")
            .define('A', Ingredient.of(PlasticraftBlocks.RESIN_ANVIL.asItem()))
            .define('L', Ingredient.of(Items.LIGHTNING_ROD))
            .define('R', Ingredient.of(ModItems.HARDEND_RESIN.get()))
            .unlockedBy("has_resin_anvil", RegistrumRecipeProvider.has(PlasticraftBlocks.RESIN_ANVIL.asItem()))
            .unlockedBy("has_hardend_resin", RegistrumRecipeProvider.has(ModItems.HARDEND_RESIN))
            .save(provider, AnvilcraftPlasticraft.of("resin_anvil_hammer"));

        // 有序合成：以磁铁锭替代普通树脂砧的中心树脂，直接产出带磁化数据的树脂砧。
        // 原版 builder 无法给结果写入自定义组件，因此在这里显式构造配方和解锁进度。
        Map<Character, Ingredient> key = new LinkedHashMap<>();
        key.put('B', Ingredient.of(ModBlocks.RESIN_BLOCK.get()));
        key.put('I', Ingredient.of(ModItems.MAGNET_INGOT.get()));
        key.put('R', Ingredient.of(ModItems.RESIN.get()));
        ResourceLocation magneticId = AnvilcraftPlasticraft.of("magnetic_resin_anvil");
        ItemStack magneticResult = PlasticraftBlocks.RESIN_ANVIL.asStack();
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
            .addCriterion("has_resin_block", RegistrumRecipeProvider.has(ModBlocks.RESIN_BLOCK))
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
        ItemStack ordinaryOutput = PlasticraftBlocks.HARDEND_RESIN_ANVIL.asStack();
        PlasticItemData.setMaterial(ordinaryOutput, "hardened_resin");
        FastCookingRecipe.builder()
            .requires(ordinaryInput)
            .result(ordinaryOutput)
            .save(provider, AnvilcraftPlasticraft.of("fast_cooking/harden_resin_anvil"));

        // 快速烹饪：磁化且未捕获生物的树脂砧受热后，保留磁化状态并完成硬化。
        ItemStack magneticOutput = PlasticraftBlocks.HARDEND_RESIN_ANVIL.asStack();
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
            .transform(PlasticraftFluids.LIQUID_HIGH_VISCOSITY_RESIN.get(), 1000)
            .requires(ModItems.RESIN.get(), 4)
            .requires(Items.SLIME_BALL, 4)
            .requires(ModItems.LIME_POWDER.get())
            .unlockedBy("has_resin", RegistrumRecipeProvider.has(ModItems.RESIN))
            .unlockedBy("has_slime_ball", RegistrumRecipeProvider.has(Items.SLIME_BALL))
            .unlockedBy("has_lime_powder", RegistrumRecipeProvider.has(ModItems.LIME_POWDER))
            .save(provider, AnvilcraftPlasticraft.of("fast_cooking/liquid_high_viscosity_resin"));

        // 有序合成：用七份硬化树脂围成釜体，产出普通硬化树脂釜。
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asItem())
            .pattern("H H")
            .pattern("H H")
            .pattern("HHH")
            .define('H', Ingredient.of(ModItems.HARDEND_RESIN.get()))
            .unlockedBy("has_hardend_resin", RegistrumRecipeProvider.has(ModItems.HARDEND_RESIN))
            .save(provider, AnvilcraftPlasticraft.of("hardend_resin_cauldron"));

        // 有序合成：重质铁围墙、皇家钢锭和硬化树脂共同构成催化容器压盖。
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, PlasticraftBlocks.CATALYTIC_PRESS_LID.asItem())
            .pattern("WRW")
            .pattern("HHH")
            .pattern(" R ")
            .define('W', Ingredient.of(ModBlocks.HEAVY_IRON_WALL.asItem()))
            .define('R', Ingredient.of(ModItems.ROYAL_STEEL_INGOT.get()))
            .define('H', Ingredient.of(ModItems.HARDEND_RESIN.get()))
            .unlockedBy("has_heavy_iron_wall", RegistrumRecipeProvider.has(ModBlocks.HEAVY_IRON_WALL))
            .unlockedBy("has_royal_steel_ingot", RegistrumRecipeProvider.has(ModItems.ROYAL_STEEL_INGOT))
            .unlockedBy("has_hardend_resin", RegistrumRecipeProvider.has(ModItems.HARDEND_RESIN))
            .save(provider, AnvilcraftPlasticraft.of("catalytic_press_lid"));

        // 有序合成：以储罐、结构扫描仪和超级电容器构成核心，输出继承电容器的满电或空电状态。
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.asItem())
            .pattern(" P ")
            .pattern("TSC")
            .pattern(" P ")
            .define('P', Ingredient.of(ModItems.PIPE.get()))
            .define('T', Ingredient.of(ModBlocks.FLUID_TANK.asItem()))
            .define('S', Ingredient.of(ModBlocks.STRUCTURE_SCANNER.asItem()))
            .define('C', Ingredient.of(
                ModItems.SUPER_CAPACITOR.get(),
                ModItems.SUPER_CAPACITOR_EMPTY.get()
            ))
            .unlockedBy("has_fluid_tank", RegistrumRecipeProvider.has(ModBlocks.FLUID_TANK))
            .unlockedBy("has_structure_scanner", RegistrumRecipeProvider.has(ModBlocks.STRUCTURE_SCANNER))
            .unlockedBy("has_supercapacitor", RegistrumRecipeProvider.has(ModItems.SUPER_CAPACITOR))
            .unlockedBy("has_empty_supercapacitor", RegistrumRecipeProvider.has(ModItems.SUPER_CAPACITOR_EMPTY))
            .save(moldingChamberRecipeOutput(provider), AnvilcraftPlasticraft.of("plastic_molding_chamber"));

        // 有序合成：用电感灯、末影之眼、储罐、处理器、磁电核心、管道和铁锭制作 3D 打印组件。
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, PlasticraftBlocks.PLASTIC_3D_PRINTING_COMPONENT.asItem())
            .pattern("LEB")
            .pattern("PT ")
            .pattern("MFI")
            .define('L', ModBlocks.INDUCTION_LIGHT.get())
            .define('E', Items.ENDER_EYE)
            .define('B', ModBlocks.LARGE_FLUID_TANK.get())
            .define('P', ModItems.PROCESSOR.get())
            .define('T', ModBlocks.FLUID_TANK.get())
            .define('M', ModBlocks.MAGNETO_ELECTRIC_CORE_BLOCK.get())
            .define('F', ModItems.PIPE.get())
            .define('I', Items.IRON_INGOT)
            .unlockedBy("has_processor", RegistrumRecipeProvider.has(ModItems.PROCESSOR))
            .save(provider, AnvilcraftPlasticraft.of("plastic_3d_printing_component"));

        // 有序合成：在硬化树脂釜两侧加入磁铁锭，直接产出带磁化数据的硬化树脂釜。
        ItemStack magneticCauldron = PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asStack();
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

        // 多方块合成：按从下到上的顺序搭建树脂底座、直管接口、黄铜玻璃塔壁和铜活板门，压缩为冷凝塔物品。
        MultiblockRecipe.builder("anvilcraftplasticraft:condenser_tower", 1)
            .layer("0A0", "BCB", "0A0")
            .layer("DED", "E E", "DED")
            .layer("DED", "EFE", "DED")
            .symbol('0', PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.get())
            .symbol('A', condenserPipe(Direction.Axis.Z))
            .symbol('B', condenserPipe(Direction.Axis.X))
            .symbol('C', condenserTrapdoor(Half.BOTTOM))
            .symbol('D', BlockStatePredicate.builder()
                .of(ModBlocks.CUT_BRASS_PILLAR.get())
                .with(BlockStateProperties.AXIS, Direction.Axis.Y)
            )
            .symbol('E', Blocks.GLASS)
            .symbol('F', condenserTrapdoor(Half.TOP))
            // 显式指定附属模组命名空间，避免 AnvilCraft 构建器按结果物品回退到本体命名空间。
            .save(provider, AnvilcraftPlasticraft.of("multiblock/condenser_tower"));

        // 多方块转换配方：识别与上方合成配方相同的 3x3x3 结构，并逐格转换为冷凝塔的 27 个部件。
        // 每个输出部件都固定 half 方位且保持 sealed=false，确保生成后可立即组成未封顶塔层。
        MultiblockConversionRecipe.builder()
            .inputLayer("0A0", "BCB", "0A0")
            .inputLayer("DED", "E E", "DED")
            .inputLayer("DED", "EFE", "DED")
            .inputSymbol('0', PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.get())
            .inputSymbol('A', condenserPipe(Direction.Axis.Z))
            .inputSymbol('B', condenserPipe(Direction.Axis.X))
            .inputSymbol('C', condenserTrapdoor(Half.BOTTOM))
            .inputSymbol('D', BlockStatePredicate.builder()
                .of(ModBlocks.CUT_BRASS_PILLAR.get())
                .with(BlockStateProperties.AXIS, Direction.Axis.Y)
            )
            .inputSymbol('E', Blocks.GLASS)
            .inputSymbol('F', condenserTrapdoor(Half.TOP))
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
            // 转换配方没有结果物品，必须显式给 ID，不能让构建器以 minecraft:air 推导文件名。
            .save(provider, AnvilcraftPlasticraft.of("multiblock_conversion/condenser_tower"));

        generatePlasmaJetBlastingRecipes(provider);
        generateCondenserRecipes(provider);
        generateFluidMixingRecipes(provider);
        generatePlasticMeltSolidLiquidRecipes(provider);

        generateResinTimeWarpRecipes(provider);
    }

    private static BlockStatePredicate.Builder condenserPipe(Direction.Axis axis) {
        return BlockStatePredicate.builder()
            .of(ModBlocks.PIPE_STRAIGHT.get())
            .with(PipeBlock.AXIS, axis)
            .with(PipeBlock.WATERLOGGED, false);
    }

    private static BlockStatePredicate.Builder condenserTrapdoor(Half half) {
        return BlockStatePredicate.builder()
            .of(Blocks.COPPER_TRAPDOOR)
            .with(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
            .with(BlockStateProperties.OPEN, false)
            .with(TrapDoorBlock.HALF, half)
            .with(BlockStateProperties.WATERLOGGED, false);
    }

    private static BlockStatePredicate.Builder condenserTowerPart(Cube3x3PartHalf half) {
        return BlockStatePredicate.builder()
            .of(PlasticraftBlocks.CONDENSER_TOWER.get())
            .with(CondenserTowerBlock.HALF, half)
            .with(CondenserTowerBlock.SEALED, false);
    }

    private static RecipeOutput moldingChamberRecipeOutput(RecipeOutput output) {
        return new RecipeOutput() {
            @Override
            public void accept(
                ResourceLocation id,
                Recipe<?> recipe,
                @Nullable AdvancementHolder advancement,
                ICondition... conditions
            ) {
                if (!(recipe instanceof ShapedRecipe shaped)) {
                    throw new IllegalArgumentException("Plastic molding chamber recipe must be shaped");
                }
                output.accept(id, new PlasticMoldingChamberRecipe(shaped), advancement, conditions);
            }

            @Override
            public Advancement.Builder advancement() {
                return output.advancement();
            }
        };
    }

    private static void generatePlasmaJetBlastingRecipes(RegistrumRecipeProvider provider) {
        // 等离子喷流配方：每次把 50 mB 原油完全汽化为等量气态原油，供冷凝塔分层处理。
        PlasmaJetBlastingRecipe.builder()
            .fluid(ModFluids.OIL.get())
            .consume(50)
            .gas(CondenserGas.GASEOUS_OIL, 50)
            .save(provider, AnvilcraftPlasticraft.of("plasma_jet_blasting/crude_oil_to_gaseous_oil"));

        // 等离子喷流配方：每次把 50 mB 水完全汽化为等量气态水，随后可冷凝回收。
        PlasmaJetBlastingRecipe.builder()
            .fluid(Fluids.WATER)
            .consume(50)
            .gas(CondenserGas.GASEOUS_WATER, 50)
            .save(provider, AnvilcraftPlasticraft.of("plasma_jet_blasting/water_to_gaseous_water"));

        // 等离子喷流配方：每次把 50 mB 经验液完全汽化为等量气态经验，随后可冷凝回收。
        PlasmaJetBlastingRecipe.builder()
            .fluid(ModFluids.EXP_FLUID.get())
            .consume(50)
            .gas(CondenserGas.GASEOUS_EXPERIENCE, 50)
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
            .fluid(ModFluids.EXP_FLUID.getId())
            .produce(50)
            .save(provider, AnvilcraftPlasticraft.of("condenser/gaseous_experience_to_experience_fluid"));

        // 冷凝配方：冷凝塔第一层从气态原油中分离 10 mB 高热燃料。
        CondenserRecipe.builder()
            .gas(CondenserGas.GASEOUS_OIL)
            .consume(10)
            .fluid(PlasticraftFluids.HIGH_HEAT_FUEL.getId())
            .produce(10)
            .towerLevel(1)
            .save(provider, AnvilcraftPlasticraft.of("condenser/gaseous_oil_to_high_heat_fuel"));

        // 冷凝配方：冷凝塔第二层从气态原油中分离 30 mB 塑料油，作为塑料加工主原料。
        CondenserRecipe.builder()
            .gas(CondenserGas.GASEOUS_OIL)
            .consume(30)
            .fluid(PlasticraftFluids.PLASTIC_OIL.getId())
            .produce(30)
            .towerLevel(2)
            .save(provider, AnvilcraftPlasticraft.of("condenser/gaseous_oil_to_plastic_oil"));

        // 冷凝配方：冷凝塔第三层从气态原油中分离 10 mB 原油精华。
        CondenserRecipe.builder()
            .gas(CondenserGas.GASEOUS_OIL)
            .consume(10)
            .fluid(PlasticraftFluids.CRUDE_OIL_ACID.getId())
            .produce(10)
            .towerLevel(3)
            .save(provider, AnvilcraftPlasticraft.of("condenser/gaseous_oil_to_crude_oil_acid"));
    }

    private static void generateFluidMixingRecipes(RegistrumRecipeProvider provider) {
        // 流体混合配方：高热燃料与原油精华按 1:1 混合，并按最大可消费量增产为三倍高热燃料。
        FluidMixingRecipe.builder()
            .requires(PlasticraftFluids.HIGH_HEAT_FUEL.get(), 1)
            .requires(PlasticraftFluids.CRUDE_OIL_ACID.get(), 1)
            .result(PlasticraftFluids.HIGH_HEAT_FUEL.get(), 3)
            .consumeMaximum()
            .save(provider, AnvilcraftPlasticraft.of("fluid_mixing/high_heat_fuel_enrichment"));

        // 流体混合配方：塑料油与原油精华按 1:1 混合，并按最大可消费量增产为三倍塑料油。
        FluidMixingRecipe.builder()
            .requires(PlasticraftFluids.PLASTIC_OIL.get(), 1)
            .requires(PlasticraftFluids.CRUDE_OIL_ACID.get(), 1)
            .result(PlasticraftFluids.PLASTIC_OIL.get(), 3)
            .consumeMaximum()
            .save(provider, AnvilcraftPlasticraft.of("fluid_mixing/plastic_oil_enrichment"));

        // 流体混合配方：一桶通用塑料熔体与一桶水冷却混合，凝固为 16 个通用塑料颗粒。
        FluidMixingRecipe.builder()
            .requires(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), 1000)
            .requires(Fluids.WATER, 1000)
            .result(PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE, 16)
            .save(provider, AnvilcraftPlasticraft.of("fluid_mixing/universal_plastic_melt_with_water"));

        // 流体混合配方：一桶通用塑料熔体与一桶细雪冷却混合，同样凝固为 16 个塑料颗粒。
        FluidMixingRecipe.builder()
            .requires(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), 1000)
            .requires(ModFluids.POWDER_SNOW.get(), 1000)
            .result(PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE, 16)
            .save(provider, AnvilcraftPlasticraft.of("fluid_mixing/universal_plastic_melt_with_powder_snow"));

        // 流体混合配方：一桶工程塑料熔体与一桶水冷却混合，凝固为 16 个工程塑料颗粒。
        FluidMixingRecipe.builder()
            .requires(PlasticraftFluids.ENGINEERING_PLASTIC_MELT.get(), 1000)
            .requires(Fluids.WATER, 1000)
            .result(PlasticraftItems.ENGINEERING_PLASTIC_GRANULE, 16)
            .save(provider, AnvilcraftPlasticraft.of("fluid_mixing/engineering_plastic_melt_with_water"));

        // 流体混合配方：一桶工程塑料熔体与一桶细雪冷却混合，同样凝固为 16 个工程塑料颗粒。
        FluidMixingRecipe.builder()
            .requires(PlasticraftFluids.ENGINEERING_PLASTIC_MELT.get(), 1000)
            .requires(ModFluids.POWDER_SNOW.get(), 1000)
            .result(PlasticraftItems.ENGINEERING_PLASTIC_GRANULE, 16)
            .save(provider, AnvilcraftPlasticraft.of("fluid_mixing/engineering_plastic_melt_with_powder_snow"));

        FluidMixingRecipe.builder()
            .requires(PlasticraftFluids.HEAT_RESISTANT_PLASTIC_MELT.get(), 1000)
            .requires(Fluids.WATER, 1000)
            .result(PlasticraftItems.HEAT_RESISTANT_PLASTIC_GRANULE, 16)
            .save(provider, AnvilcraftPlasticraft.of("fluid_mixing/heat_resistant_plastic_melt_with_water"));

        FluidMixingRecipe.builder()
            .requires(PlasticraftFluids.HEAT_RESISTANT_PLASTIC_MELT.get(), 1000)
            .requires(ModFluids.POWDER_SNOW.get(), 1000)
            .result(PlasticraftItems.HEAT_RESISTANT_PLASTIC_GRANULE, 16)
            .save(provider, AnvilcraftPlasticraft.of(
                "fluid_mixing/heat_resistant_plastic_melt_with_powder_snow"
            ));

        FluidMixingRecipe.builder()
            .requires(PlasticraftFluids.CLEAR_PLASTIC_MELT.get(), 1000)
            .requires(Fluids.WATER, 1000)
            .result(PlasticraftItems.CLEAR_PLASTIC_GRANULE, 16)
            .save(provider, AnvilcraftPlasticraft.of("fluid_mixing/clear_plastic_melt_with_water"));

        FluidMixingRecipe.builder()
            .requires(PlasticraftFluids.CLEAR_PLASTIC_MELT.get(), 1000)
            .requires(ModFluids.POWDER_SNOW.get(), 1000)
            .result(PlasticraftItems.CLEAR_PLASTIC_GRANULE, 16)
            .save(provider, AnvilcraftPlasticraft.of("fluid_mixing/clear_plastic_melt_with_powder_snow"));
    }

    private static void generatePlasticMeltSolidLiquidRecipes(RegistrumRecipeProvider provider) {
        // 固液配方：向一桶通用塑料熔体投入任意冷却物品，消耗熔体并产出 16 个塑料颗粒。
        SolidLiquidRecipe.builder()
            .cauldron(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get())
            .consume(1000)
            .requires(PlasticraftItemTags.COLD_ITEMS)
            .result(PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE, 16)
            .save(provider, AnvilcraftPlasticraft.of("solid_liquid/cool_universal_plastic_melt"));

        // 固液配方：向一桶工程塑料熔体投入任意冷却物品，消耗熔体并产出 16 个工程塑料颗粒。
        SolidLiquidRecipe.builder()
            .cauldron(PlasticraftFluids.ENGINEERING_PLASTIC_MELT.get())
            .consume(1000)
            .requires(PlasticraftItemTags.COLD_ITEMS)
            .result(PlasticraftItems.ENGINEERING_PLASTIC_GRANULE, 16)
            .save(provider, AnvilcraftPlasticraft.of("solid_liquid/cool_engineering_plastic_melt"));
        SolidLiquidRecipe.builder()
            .cauldron(PlasticraftFluids.CLEAR_PLASTIC_MELT.get())
            .consume(1000)
            .requires(PlasticraftItemTags.COLD_ITEMS)
            .result(PlasticraftItems.CLEAR_PLASTIC_GRANULE, 16)
            .save(provider, AnvilcraftPlasticraft.of("solid_liquid/cool_clear_plastic_melt"));

        SolidLiquidRecipe.builder()
            .cauldron(PlasticraftFluids.HEAT_RESISTANT_PLASTIC_MELT.get())
            .consume(1000)
            .requires(PlasticraftItemTags.COLD_ITEMS)
            .result(PlasticraftItems.HEAT_RESISTANT_PLASTIC_GRANULE, 16)
            .save(provider, AnvilcraftPlasticraft.of("solid_liquid/cool_heat_resistant_plastic_melt"));

        // 固液配方（每种染料各一份）：投入染料后熔体种类与数量都不变，
        // 颜色由配方上下文在提交后写回，因此有意不声明物品或流体产出,
        // 用覆盖 validate 的构建器跳过本体"必须有产出"的数据生成校验。
        generatePlasticMeltDyeRecipes(provider, PlasticMaterial.UNIVERSAL);
        generatePlasticMeltDyeRecipes(provider, PlasticMaterial.ENGINEERING);
        generatePlasticMeltDyeRecipes(provider, PlasticMaterial.CLEAR);
        generatePlasticMeltDyeRecipes(provider, PlasticMaterial.HEAT_RESISTANT);
    }

    private static void generatePlasticMeltDyeRecipes(
        RegistrumRecipeProvider provider,
        PlasticMaterial material
    ) {
        // 固液染色配方：为当前塑料材料的 16 种染料逐一生成只改组件颜色、不改变熔体数量的配方。
        for (DyeColor color : DyeColor.values()) {
            DyeItem dye = DyeItem.byColor(color);
            SolidLiquidRecipe.Builder dyeBuilder = new SolidLiquidRecipe.Builder() {
                @Override
                public void validate(ResourceLocation id) {
                }
            };
            dyeBuilder
                .cauldron(material.melt())
                .requires(dye)
                .save(provider, AnvilcraftPlasticraft.of(
                    "solid_liquid/dye_" + material.key() + "_melt_" + color.getName()
                ));
        }
    }

    private static ItemIngredientPredicate resinAnvilVariant(int modelVariant) {
        return ItemIngredientPredicate.Builder.item()
            .of(PlasticraftBlocks.RESIN_ANVIL.asItem())
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
        // 时移配方：凋零之首借助不死图腾与 1 mB 经验修补液态魔咒转化为悦灵。
        CompoundTag allayTag = new CompoundTag();
        allayTag.putString("id", "minecraft:allay");
        ItemStack capturedAllay = PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asStack();
        capturedAllay.set(ModComponents.SAVED_ENTITY, new SavedEntity(allayTag, false));
        // 本体时移构建器只接受流体类型，需要显式构造带魔咒组件条件的炼药锅参数。
        TimeWarpRecipe.Builder allayBuilder = new TimeWarpRecipe.Builder() {
            @Override
            protected TimeWarpRecipe of(List<ItemIngredientPredicate> ingredients, List<ChanceItemStack> results) {
                return new TimeWarpRecipe(ingredients, results,
                    HasCauldronSimple.empty()
                        .fluid(FluidStackPredicate.builder()
                            .fluid(ModFluids.LIQUID_ENCHANTMENT)
                            .component(builder -> builder.expect(ModComponents.LIQUID_ENCHANTMENT, Enchantments.MENDING))
                            .build())
                        .consume(1)
                        .build(),
                    ProduceHeat.builder().build());
            }
        };
        allayBuilder
            .requires(ItemIngredientPredicate.Builder.item()
                .of(PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
                .withSubPredicate(
                    ModItemSubPredicates.SAVED_ENTITY.get(), ItemSavedEntityPredicate.of(EntityType.WITHER_SKULL)
                )
                .build())
            .requires(Items.TOTEM_OF_UNDYING)
            .result(capturedAllay)
            .save(provider, AnvilcraftPlasticraft.of("time_warp/wither_skull_to_allay"));

        // 时间扭曲配方：一桶液态高黏度树脂经过漫长时间固化，产出高黏度树脂块。
        TimeWarpRecipe.builder()
            .fluid(PlasticraftFluids.LIQUID_HIGH_VISCOSITY_RESIN.get())
            .consume(1000)
            .result(PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK)
            .unlockedBy(
                "has_liquid_high_viscosity_resin_bucket",
                RegistrumRecipeProvider.has(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET)
            )
            .save(provider, AnvilcraftPlasticraft.of("time_warp/high_viscosity_resin_block"));

        // 时间扭曲配方：未捕获生物的树脂砧随时间完全琥珀化，转化为普通琥珀块。
        TimeWarpRecipe.builder()
            .requires(ItemIngredientPredicate.Builder.item()
                .of(PlasticraftBlocks.RESIN_ANVIL.asItem())
                .withSubPredicate(
                    LibItemSubPredicates.NOT.get(),
                    NotPredicate.of(ModItemSubPredicates.SAVED_ENTITY.get(), ItemSavedEntityPredicate.any())
                )
                .build())
            .result(ModBlocks.AMBER_BLOCK)
            .unlockedBy("has_resin_anvil", RegistrumRecipeProvider.has(PlasticraftBlocks.RESIN_ANVIL.asItem()))
            .save(provider, AnvilcraftPlasticraft.of("time_warp/resin_anvil_to_amber"));

        // 扩展世界配方：树脂砧捕获非敌对生物后，在点亮的腐化信标和釜结构上受砧击，
        // 保存生物组件并生成对应的生物琥珀块。
        ExtendInWorldRecipeBuilder.extendCompatible(ModRecipeTriggers.ON_ANVIL_FALL_ON)
            .hasCauldron(0, -1, 0)
            .hasBlock(builder -> builder
                .of(ModBlocks.CORRUPTED_BEACON.get())
                .with(CorruptedBeaconBlock.LIT, true)
                .offset(0, -2, 0))
            .hasItemIngredient(builder -> builder
                .of(PlasticraftBlocks.RESIN_ANVIL.asItem())
                .offset(0.0, -0.375, 0.0)
                .range(0.75, 0.75, 0.75)
                .with(ModItemSubPredicates.SAVED_ENTITY.get(), ItemSavedEntityPredicate.any())
                .with(
                    LibItemSubPredicates.NOT.get(),
                    NotPredicate.of(ModItemSubPredicates.SAVED_ENTITY.get(), ItemSavedEntityPredicate.monster())
                )
                .saveComponent(ModComponents.SAVED_ENTITY, AnvilcraftPlasticraft.of("saved_entity")))
            .spawnItem(builder -> builder
                .item(ModBlocks.MOB_AMBER_BLOCK)
                .offset(0.0, -0.75, 0.0)
                .applyComponent(ModComponents.SAVED_ENTITY, AnvilcraftPlasticraft.of("saved_entity")))
            .maxEfficiency(1)
            .unlockedBy("has_resin_anvil", RegistrumRecipeProvider.has(PlasticraftBlocks.RESIN_ANVIL.asItem()))
            .group("time_warp")
            .icon(ModBlocks.MOB_AMBER_BLOCK.asStack())
            .save(provider, AnvilcraftPlasticraft.of("resin_anvil_mob_amber"));

        // 扩展世界配方：树脂砧捕获敌对生物后，在相同结构上受砧击，
        // 将保存的生物及怨念数据写入怨念琥珀结果。
        ExtendInWorldRecipeBuilder.extendCompatible(ModRecipeTriggers.ON_ANVIL_FALL_ON)
            .hasCauldron(0, -1, 0)
            .hasBlock(builder -> builder
                .of(ModBlocks.CORRUPTED_BEACON.get())
                .with(CorruptedBeaconBlock.LIT, true)
                .offset(0, -2, 0))
            .hasItemIngredient(builder -> builder
                .of(PlasticraftBlocks.RESIN_ANVIL.asItem())
                .offset(0.0, -0.375, 0.0)
                .range(0.75, 0.75, 0.75)
                .with(ModItemSubPredicates.SAVED_ENTITY.get(), ItemSavedEntityPredicate.monster())
                .saveComponent(ModComponents.SAVED_ENTITY, AnvilcraftPlasticraft.of("saved_entity")))
            .out(new ResentmentAmberOutcome(
                new Vec3(0.0, -0.75, 0.0),
                AnvilcraftPlasticraft.of("saved_entity")
            ))
            .maxEfficiency(1)
            .unlockedBy("has_resin_anvil", RegistrumRecipeProvider.has(PlasticraftBlocks.RESIN_ANVIL.asItem()))
            .group("time_warp")
            .icon(ModBlocks.RESENTFUL_AMBER_BLOCK.asStack())
            .save(provider, AnvilcraftPlasticraft.of("resin_anvil_resentful_amber"));

        // 时间扭曲配方：未捕获生物的高黏度树脂块随时间完全琥珀化，转化为普通琥珀块。
        TimeWarpRecipe.builder()
            .requires(ItemIngredientPredicate.Builder.item()
                .of(PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
                .withSubPredicate(
                    LibItemSubPredicates.NOT.get(),
                    NotPredicate.of(ModItemSubPredicates.SAVED_ENTITY.get(), ItemSavedEntityPredicate.any())
                )
                .build())
            .result(ModBlocks.AMBER_BLOCK)
            .unlockedBy(
                "has_high_viscosity_resin_block",
                RegistrumRecipeProvider.has(PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
            )
            .save(provider, AnvilcraftPlasticraft.of("time_warp/high_viscosity_resin_to_amber"));

        // 扩展世界配方：高黏度树脂块捕获非敌对生物后，在点亮的腐化信标和釜结构上受砧击，
        // 保存生物组件并生成对应的生物琥珀块。
        ExtendInWorldRecipeBuilder.extendCompatible(ModRecipeTriggers.ON_ANVIL_FALL_ON)
            .hasCauldron(0, -1, 0)
            .hasBlock(builder -> builder
                .of(ModBlocks.CORRUPTED_BEACON.get())
                .with(CorruptedBeaconBlock.LIT, true)
                .offset(0, -2, 0))
            .hasItemIngredient(builder -> builder
                .of(PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
                .offset(0.0, -0.375, 0.0)
                .range(0.75, 0.75, 0.75)
                .with(
                    LibItemSubPredicates.NOT.get(),
                    NotPredicate.of(ModItemSubPredicates.SAVED_ENTITY.get(), ItemSavedEntityPredicate.monster())
                )
                .saveComponent(ModComponents.SAVED_ENTITY, AnvilcraftPlasticraft.of("saved_entity")))
            .spawnItem(builder -> builder
                .item(ModBlocks.MOB_AMBER_BLOCK)
                .offset(0.0, -0.75, 0.0)
                .applyComponent(ModComponents.SAVED_ENTITY, AnvilcraftPlasticraft.of("saved_entity")))
            .maxEfficiency(1)
            .unlockedBy(
                "has_high_viscosity_resin_block",
                RegistrumRecipeProvider.has(PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
            )
            .group("time_warp")
            .icon(ModBlocks.MOB_AMBER_BLOCK.asStack())
            .save(provider, AnvilcraftPlasticraft.of("high_viscosity_resin_mob_amber"));

        // 扩展世界配方：高黏度树脂块捕获敌对生物后，在相同结构上受砧击，
        // 将保存的生物及怨念数据写入怨念琥珀结果。
        ExtendInWorldRecipeBuilder.extendCompatible(ModRecipeTriggers.ON_ANVIL_FALL_ON)
            .hasCauldron(0, -1, 0)
            .hasBlock(builder -> builder
                .of(ModBlocks.CORRUPTED_BEACON.get())
                .with(CorruptedBeaconBlock.LIT, true)
                .offset(0, -2, 0))
            .hasItemIngredient(builder -> builder
                .of(PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
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
                RegistrumRecipeProvider.has(PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
            )
            .group("time_warp")
            .icon(ModBlocks.RESENTFUL_AMBER_BLOCK.asStack())
            .save(provider, AnvilcraftPlasticraft.of("high_viscosity_resin_resentful_amber"));
    }
}
