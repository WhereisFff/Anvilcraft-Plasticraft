package dev.anvilcraft.plasticraft.data;

import dev.anvilcraft.lib.v2.registrum.providers.ProviderType;
import dev.anvilcraft.lib.v2.registrum.providers.RegistrumRecipeProvider;
import dev.anvilcraft.lib.v2.recipe.data.advancement.predicate.item.NotPredicate;
import dev.anvilcraft.lib.v2.recipe.init.LibItemSubPredicates;
import dev.anvilcraft.lib.v2.util.predicate.ItemIngredientPredicate;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.item.ModItemGroups;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.init.item.ModItemSubPredicates;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.init.recipe.ModRecipeTriggers;
import dev.dubhe.anvilcraft.item.property.predicate.ItemSavedEntityPredicate;
import dev.dubhe.anvilcraft.block.CorruptedBeaconBlock;
import dev.dubhe.anvilcraft.recipe.anvil.builder.ExtendInWorldRecipeBuilder;
import dev.dubhe.anvilcraft.recipe.anvil.outcome.ResentmentAmberOutcome;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.FastCookingRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.TimeWarpRecipe;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.anvilcraft.plasticraft.AnvilcraftPlasticraft.REGISTRUM;

/** 首个树脂砧完整功能切片的数据生成注册。 */
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
            provider.add("config.jade.plugin_anvilcraft.fluid_tank", "Fluid Tank");
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

        generateResinTimeWarpRecipes(provider);
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
    }
}
