package dev.anvilcraft.plasticraft.integration.jei;

import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItemTags;
import dev.anvilcraft.plasticraft.recipe.PlasticCatalysisHeat;
import dev.anvilcraft.plasticraft.recipe.PlasticCatalysisCold;
import dev.anvilcraft.plasticraft.recipe.ResinCatalysts;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 开放环境催化的 JEI 展示数据，不注册为可执行的机器配方。 */
public record PlasticMeltCatalysisRecipe(
    Fluid input,
    Fluid output,
    TagKey<Item> catalyst,
    String environment,
    boolean frost,
    String priority,
    List<BlockState> environmentBlocks
) {
    public static List<PlasticMeltCatalysisRecipe> all() {
        Fluid oil = PlasticraftFluids.PLASTIC_OIL.get();
        Fluid universal = PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get();
        Fluid clear = PlasticraftFluids.CLEAR_PLASTIC_MELT.get();
        Fluid engineering = PlasticraftFluids.ENGINEERING_PLASTIC_MELT.get();
        Fluid heatResistant = PlasticraftFluids.HEAT_RESISTANT_PLASTIC_MELT.get();
        Fluid elastic = PlasticMaterial.ELASTIC.melt();
        List<BlockState> heatSources = findEnvironmentBlocks(true);
        List<BlockState> coldSources = findEnvironmentBlocks(false);
        return List.of(
            new PlasticMeltCatalysisRecipe(
                oil, elastic, PlasticraftItemTags.RESIN_ITEMS, "cold", false, "resin_priority", coldSources
            ),
            new PlasticMeltCatalysisRecipe(
                oil, universal, PlasticraftItemTags.ROYAL_STEEL_ITEMS, "heat", false, "glass_priority", heatSources
            ),
            new PlasticMeltCatalysisRecipe(
                oil, universal, PlasticraftItemTags.FROST_METAL_ITEMS, "heat", true, "glass_priority", heatSources
            ),
            new PlasticMeltCatalysisRecipe(
                oil, clear, PlasticraftItemTags.ROYAL_GLASS_ITEMS, "heat", false, "glass_priority", heatSources
            ),
            new PlasticMeltCatalysisRecipe(
                oil, clear, PlasticraftItemTags.FROST_GLASS_ITEMS, "heat", true, "glass_priority", heatSources
            ),
            new PlasticMeltCatalysisRecipe(
                universal, engineering, PlasticraftItemTags.ROYAL_STEEL_ITEMS, "cold", false, "ember_priority", coldSources
            ),
            new PlasticMeltCatalysisRecipe(
                universal, engineering, PlasticraftItemTags.FROST_METAL_ITEMS, "none", true, "ember_priority", List.of()
            ),
            new PlasticMeltCatalysisRecipe(
                universal, heatResistant, PlasticraftItemTags.EMBER_METAL_ITEMS, "heat", false, "ember_priority", heatSources
            )
        );
    }

    public Ingredient catalystIngredient() {
        if (this.catalyst.equals(PlasticraftItemTags.RESIN_ITEMS) && Minecraft.getInstance().level != null) {
            return Ingredient.of(ResinCatalysts.items(Minecraft.getInstance().level).stream().map(ItemStack::new));
        }
        return Ingredient.of(this.catalyst);
    }

    public List<ItemStack> environmentStacks() {
        return this.environmentBlocks.stream().map(PlasticMeltCatalysisRecipe::displayItem).distinct().map(ItemStack::new).toList();
    }

    public BlockState environmentState(ItemStack stack) {
        return this.environmentBlocks.stream()
            .filter(state -> stack.is(displayItem(state)))
            .findFirst()
            .orElse(Blocks.AIR.defaultBlockState());
    }

    private static List<BlockState> findEnvironmentBlocks(boolean heat) {
        List<BlockState> states = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!heat) {
                BlockState state = block.defaultBlockState();
                if (PlasticCatalysisCold.isCold(state) && displayItem(state) != Items.AIR) states.add(state);
                continue;
            }
            // 每种方块只展示一个有效状态，避免朝向挤占轮播，并排除未点燃或未工作的加热器。
            block.getStateDefinition().getPossibleStates().stream()
                .filter(state -> PlasticCatalysisHeat.power(state) > 0)
                .max(Comparator.comparingInt(PlasticCatalysisHeat::power))
                .filter(state -> displayItem(state) != Items.AIR)
                .ifPresent(states::add);
        }
        return List.copyOf(states);
    }

    private static Item displayItem(BlockState state) {
        if (state.getBlock().asItem() != Items.AIR) return state.getBlock().asItem();
        if (state.is(Blocks.POWDER_SNOW)) return Items.POWDER_SNOW_BUCKET;
        return state.getFluidState().getType().getBucket();
    }
}
