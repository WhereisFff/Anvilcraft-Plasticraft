package dev.anvilcraft.plasticraft.init;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.recipe.FluidFastCookingRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.FastCookingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 复用本体配方类型时所需的附属序列化器。 */
public final class ModRecipeTypes {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(
        Registries.RECIPE_SERIALIZER,
        AnvilcraftPlasticraft.MOD_ID
    );

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FastCookingRecipe>> FLUID_FAST_COOKING =
        SERIALIZERS.register("fluid_fast_cooking", FluidFastCookingRecipe.Serializer::new);

    private ModRecipeTypes() {
    }

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
