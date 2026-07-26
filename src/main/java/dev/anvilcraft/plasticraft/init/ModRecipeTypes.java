package dev.anvilcraft.plasticraft.init;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.recipe.FluidFastCookingRecipe;
import dev.anvilcraft.plasticraft.recipe.CatalyticPressingRecipe;
import dev.anvilcraft.plasticraft.recipe.CondenserRecipe;
import dev.anvilcraft.plasticraft.recipe.PlasmaJetBlastingRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.FastCookingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 复用本体配方类型时所需的附属序列化器。 */
public final class ModRecipeTypes {
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(
        Registries.RECIPE_TYPE,
        AnvilcraftPlasticraft.MOD_ID
    );
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(
        Registries.RECIPE_SERIALIZER,
        AnvilcraftPlasticraft.MOD_ID
    );

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FastCookingRecipe>> FLUID_FAST_COOKING =
        SERIALIZERS.register("fluid_fast_cooking", FluidFastCookingRecipe.Serializer::new);

    public static final DeferredHolder<RecipeType<?>, RecipeType<CatalyticPressingRecipe>> CATALYTIC_PRESSING_TYPE =
        TYPES.register("catalytic_pressing", () -> new RecipeType<>() {
            @Override
            public String toString() {
                return AnvilcraftPlasticraft.of("catalytic_pressing").toString();
            }
        });
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CatalyticPressingRecipe>>
        CATALYTIC_PRESSING_SERIALIZER = SERIALIZERS.register(
            "catalytic_pressing",
            CatalyticPressingRecipe.Serializer::new
        );

    public static final DeferredHolder<RecipeType<?>, RecipeType<PlasmaJetBlastingRecipe>> PLASMA_JET_BLASTING_TYPE =
        TYPES.register("plasma_jet_blasting", () -> new RecipeType<>() {
            @Override
            public String toString() {
                return AnvilcraftPlasticraft.of("plasma_jet_blasting").toString();
            }
        });
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PlasmaJetBlastingRecipe>>
        PLASMA_JET_BLASTING_SERIALIZER = SERIALIZERS.register(
            "plasma_jet_blasting",
            PlasmaJetBlastingRecipe.Serializer::new
        );

    public static final DeferredHolder<RecipeType<?>, RecipeType<CondenserRecipe>> CONDENSER_TYPE =
        TYPES.register("condenser", () -> new RecipeType<>() {
            @Override
            public String toString() {
                return AnvilcraftPlasticraft.of("condenser").toString();
            }
        });
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CondenserRecipe>> CONDENSER_SERIALIZER =
        SERIALIZERS.register("condenser", CondenserRecipe.Serializer::new);

    private ModRecipeTypes() {
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
        SERIALIZERS.register(modEventBus);
    }
}
