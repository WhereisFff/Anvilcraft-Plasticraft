package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.init.item.PlasticraftItemTags;
import dev.dubhe.anvilcraft.recipe.ChargerChargingRecipe;
import dev.dubhe.anvilcraft.recipe.sync.RecipesRecord;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** 树脂材料按合成输入向最终制品传播，配方回收产物不继承材料身份。 */
public final class ResinCatalysts {
    private static final Map<RecipeMap, Set<Item>> CACHE = new WeakHashMap<>();

    private ResinCatalysts() {
    }

    public static boolean contains(Level level, ItemStack stack) {
        return !stack.isEmpty() && (stack.is(PlasticraftItemTags.RESIN_ITEMS) || items(level).contains(stack.getItem()));
    }

    public static synchronized Set<Item> items(Level level) {
        RecipeMap recipes = RecipesRecord.getRecipes(level);
        Set<Item> cached = CACHE.get(recipes);
        if (cached != null) {
            return cached;
        }
        Set<Item> resin = new HashSet<>();
        BuiltInRegistries.ITEM.get(PlasticraftItemTags.RESIN_ITEMS)
            .ifPresent(tag -> tag.forEach(holder -> resin.add(holder.value())));
        if (recipes == null) {
            return Set.copyOf(resin);
        }
        var displayContext = SlotDisplayContext.fromLevel(level);
        Map<Item, Set<Item>> products = new HashMap<>();
        for (RecipeHolder<?> holder : recipes.values()) {
            Recipe<?> recipe = holder.value();
            // 粉碎、熔炼和世界加工可能拆出铁、铜等原料，不能把回收链扩散成树脂材料。
            List<Ingredient> ingredients;
            Set<Item> outputs = new HashSet<>();
            if (recipe instanceof ChargerChargingRecipe charging) {
                ingredients = List.of(charging.ingredient());
                outputs.add(charging.result().create().getItem());
            } else if (recipe instanceof CraftingRecipe || recipe instanceof SmithingRecipe && !(recipe instanceof SmithingTrimRecipe)) {
                ingredients = recipe.placementInfo().ingredients();
                recipe.display().forEach(display -> display.result().resolveForStacks(displayContext)
                    .forEach(stack -> outputs.add(stack.getItem())));
            } else {
                continue;
            }
            for (Ingredient ingredient : ingredients) {
                ingredient.items().forEach(holderItem -> {
                    ItemStack input = new ItemStack(holderItem);
                    var remainder = input.getCraftingRemainder();
                    if (remainder == null || !remainder.is(input.getItem())) {
                        products.computeIfAbsent(input.getItem(), ignored -> new HashSet<>()).addAll(outputs);
                    }
                });
            }
        }
        ArrayDeque<Item> pending = new ArrayDeque<>(resin);
        while (!pending.isEmpty()) {
            for (Item output : products.getOrDefault(pending.removeFirst(), Set.of())) {
                if (resin.add(output)) pending.addLast(output);
            }
        }
        Set<Item> result = Set.copyOf(resin);
        CACHE.put(recipes, result);
        return result;
    }

    public static synchronized void clear() {
        CACHE.clear();
    }

}
