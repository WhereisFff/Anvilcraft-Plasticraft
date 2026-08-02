package dev.anvilcraft.plasticraft.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.lib.v2.util.predicate.ChanceItemStack;
import dev.anvilcraft.lib.v2.util.predicate.ItemIngredientPredicate;
import dev.anvilcraft.plasticraft.init.PlasticraftRecipeTypes;
import dev.dubhe.anvilcraft.recipe.anvil.predicate.block.HasCauldron;
import dev.dubhe.anvilcraft.recipe.anvil.util.WrapUtils;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.FastCookingRecipe;
import dev.dubhe.anvilcraft.recipe.component.HasCauldronSimple;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** 为快速烹饪补齐整锅流体转换字段，同时继续归入本体快速烹饪配方类型。 */
public final class FluidFastCookingRecipe extends FastCookingRecipe {
    public FluidFastCookingRecipe(
        List<ItemIngredientPredicate> itemIngredients,
        List<ChanceItemStack> results,
        HasCauldronSimple hasCauldron
    ) {
        super(itemIngredients, results, hasCauldron);
    }

    @Override
    public RecipeSerializer<FastCookingRecipe> getSerializer() {
        return PlasticraftRecipeTypes.FLUID_FAST_COOKING.get();
    }

    public static Builder fluidBuilder() {
        return new Builder();
    }

    public static final class Serializer implements RecipeSerializer<FastCookingRecipe> {
        private static final MapCodec<FastCookingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ItemIngredientPredicate.CODEC.listOf()
                .optionalFieldOf("ingredients", List.of())
                .forGetter(FastCookingRecipe::getInputItems),
            ChanceItemStack.CODEC.listOf()
                .optionalFieldOf("results", List.of())
                .forGetter(FastCookingRecipe::getResultItems),
            HasCauldronSimple.CODEC.forGetter(FastCookingRecipe::getHasCauldron)
        ).apply(instance, FluidFastCookingRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, FastCookingRecipe> STREAM_CODEC = StreamCodec.composite(
            ItemIngredientPredicate.STREAM_CODEC.apply(ByteBufCodecs.list()),
            FastCookingRecipe::getInputItems,
            ChanceItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()),
            FastCookingRecipe::getResultItems,
            HasCauldronSimple.STREAM_CODEC,
            FastCookingRecipe::getHasCauldron,
            FluidFastCookingRecipe::new
        );

        @Override
        public MapCodec<FastCookingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FastCookingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }

    public static final class Builder extends SimpleAbstractBuilder<FastCookingRecipe, Builder> {
        private final HasCauldronSimple.Builder hasCauldron = HasCauldronSimple.empty();

        public Builder cauldron(ResourceLocation fluid) {
            this.hasCauldron.fluid(fluid);
            return this;
        }

        public Builder cauldron(Block cauldron) {
            return this.cauldron(WrapUtils.cauldron2Fluid(cauldron));
        }

        public Builder transform(ResourceLocation fluid) {
            this.hasCauldron.transform(fluid);
            return this;
        }

        public Builder consume(int amount) {
            this.hasCauldron.consume(amount);
            return this;
        }

        public Builder produce(int amount) {
            this.hasCauldron.produce(amount);
            return this;
        }

        @Override
        protected FastCookingRecipe of(
            List<ItemIngredientPredicate> itemIngredients,
            List<ChanceItemStack> results
        ) {
            return new FluidFastCookingRecipe(itemIngredients, results, this.hasCauldron.build());
        }

        @Override
        public void validate(ResourceLocation id) {
            HasCauldronSimple cauldron = this.hasCauldron.build();
            if (this.itemIngredients.isEmpty()) {
                throw new IllegalArgumentException("Recipe ingredients must not be empty, RecipeId: " + id);
            }
            if (this.results.isEmpty() && !HasCauldron.isNotEmpty(cauldron.transform())) {
                throw new IllegalArgumentException("Recipe must have an item or fluid result, RecipeId: " + id);
            }
        }

        @Override
        public String getType() {
            return "fluid_fast_cooking";
        }

        @Override
        protected Builder getThis() {
            return this;
        }
    }
}
