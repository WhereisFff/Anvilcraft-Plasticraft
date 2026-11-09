package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.init.item.PlasticraftItemTags;
import dev.dubhe.anvilcraft.recipe.ChargerChargingRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** 树脂材料按合成输入向最终制品传播，配方回收产物不继承材料身份。 */
public final class ResinCatalysts {
    private static final Map<RecipeManager, Snapshot> CACHE = new WeakHashMap<>();

    private ResinCatalysts() {
    }

    public static boolean contains(Level level, ItemStack stack) {
        return !stack.isEmpty() && (stack.is(PlasticraftItemTags.RESIN_ITEMS) || items(level).contains(stack.getItem()));
    }

    public static synchronized Set<Item> items(Level level) {
        RecipeManager manager = level.getRecipeManager();
        Collection<RecipeHolder<?>> recipes = manager.getRecipes();
        Snapshot cached = CACHE.get(manager);
        // 原版配方管理器重载时替换不可变配方表，值视图的身份随之改变。
        if (cached != null && cached.recipes() == recipes) return cached.items();

        Set<Item> resin = new HashSet<>();
        BuiltInRegistries.ITEM.getTag(PlasticraftItemTags.RESIN_ITEMS)
            .ifPresent(tag -> tag.forEach(holder -> resin.add(holder.value())));
        Map<Item, Set<Item>> products = new HashMap<>();
        for (RecipeHolder<?> holder : recipes) {
            Recipe<?> recipe = holder.value();
            // 粉碎、熔炼和世界加工可能拆出铁、铜等原料，不能把回收链扩散成树脂材料。
            List<Ingredient> ingredients;
            if (recipe instanceof ChargerChargingRecipe charging) {
                // 充放电只切换电容器的能量形态，不会把树脂分离为其它原料。
                ingredients = List.of(charging.getIngredient());
            } else if (recipe instanceof CraftingRecipe) {
                ingredients = recipe.getIngredients();
            } else if (recipe instanceof SmithingRecipe smithing && !(recipe instanceof SmithingTrimRecipe)) {
                // 原版锻造不公开 Ingredient 列表；纹饰配方的预览产物也不代表真实输出物品。
                ingredients = List.of(Ingredient.of(BuiltInRegistries.ITEM.stream()
                    .map(ItemStack::new)
                    .filter(input -> smithing.isBaseIngredient(input)
                        || smithing.isAdditionIngredient(input)
                        || smithing.isTemplateIngredient(input))));
            } else {
                continue;
            }
            ItemStack output = recipe.getResultItem(level.registryAccess());
            if (output.isEmpty()) continue;
            for (Ingredient ingredient : ingredients) {
                for (ItemStack input : ingredient.getItems()) {
                    if (input.isEmpty() || input.getCraftingRemainingItem().is(input.getItem())) continue;
                    products.computeIfAbsent(input.getItem(), ignored -> new HashSet<>()).add(output.getItem());
                }
            }
        }
        ArrayDeque<Item> pending = new ArrayDeque<>(resin);
        while (!pending.isEmpty()) {
            for (Item output : products.getOrDefault(pending.removeFirst(), Set.of())) {
                if (resin.add(output)) pending.addLast(output);
            }
        }
        Set<Item> result = Set.copyOf(resin);
        CACHE.put(manager, new Snapshot(recipes, result));
        return result;
    }

    public static synchronized void clear() {
        CACHE.clear();
    }

    private record Snapshot(Collection<RecipeHolder<?>> recipes, Set<Item> items) {
    }
}
