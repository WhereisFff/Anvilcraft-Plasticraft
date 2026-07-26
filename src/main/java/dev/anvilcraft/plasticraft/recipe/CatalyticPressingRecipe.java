package dev.anvilcraft.plasticraft.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.plasticraft.init.ModRecipeTypes;
import dev.dubhe.anvilcraft.recipe.anvil.builder.AbstractRecipeBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.ArrayList;
import java.util.List;

/** 催化压盖在密封容器中执行的固液加工配方。 */
public final class CatalyticPressingRecipe implements Recipe<CatalyticPressingRecipe.Input> {
    private final List<Ingredient> itemIngredients;
    private final SizedFluidIngredient fluidIngredient;
    private final FluidStack fluidResult;
    private final int processingTime;
    private final boolean consumeMaximum;

    public CatalyticPressingRecipe(
        List<Ingredient> itemIngredients,
        SizedFluidIngredient fluidIngredient,
        FluidStack fluidResult,
        int processingTime,
        boolean consumeMaximum
    ) {
        this.itemIngredients = List.copyOf(itemIngredients);
        this.fluidIngredient = fluidIngredient;
        this.fluidResult = fluidResult.copy();
        this.processingTime = processingTime;
        this.consumeMaximum = consumeMaximum;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Ingredient> itemIngredients() {
        return this.itemIngredients;
    }

    public SizedFluidIngredient fluidIngredient() {
        return this.fluidIngredient;
    }

    public FluidStack fluidResult() {
        return this.fluidResult.copy();
    }

    public int processingTime() {
        return this.processingTime;
    }

    public boolean consumeMaximum() {
        return this.consumeMaximum;
    }

    public int batches(FluidStack stored) {
        if (!this.fluidIngredient.test(stored)) return 0;
        int possible = stored.getAmount() / this.fluidIngredient.amount();
        return this.consumeMaximum ? possible : Math.min(1, possible);
    }

    public FluidStack resultFor(FluidStack stored) {
        int batches = this.batches(stored);
        if (batches <= 0) return FluidStack.EMPTY;
        long amount = (long) this.fluidResult.getAmount() * batches;
        return amount > Integer.MAX_VALUE ? FluidStack.EMPTY : this.fluidResult.copyWithAmount((int) amount);
    }

    @Override
    public boolean matches(Input input, Level level) {
        if (this.batches(input.fluid()) <= 0 || input.items().size() < this.itemIngredients.size()) return false;
        List<ItemStack> unmatched = new ArrayList<>(input.items().stream().map(ItemStack::copy).toList());
        for (Ingredient ingredient : this.itemIngredients) {
            int index = -1;
            for (int i = 0; i < unmatched.size(); i++) {
                if (!unmatched.get(i).isEmpty() && ingredient.test(unmatched.get(i))) {
                    index = i;
                    break;
                }
            }
            if (index < 0) return false;
            ItemStack matched = unmatched.get(index);
            matched.shrink(1);
            if (matched.isEmpty()) unmatched.remove(index);
        }
        return true;
    }

    @Override
    public ItemStack assemble(Input input, HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public RecipeSerializer<CatalyticPressingRecipe> getSerializer() {
        return ModRecipeTypes.CATALYTIC_PRESSING_SERIALIZER.get();
    }

    @Override
    public RecipeType<CatalyticPressingRecipe> getType() {
        return ModRecipeTypes.CATALYTIC_PRESSING_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public record Input(List<ItemStack> items, FluidStack fluid) implements RecipeInput {
        public Input {
            items = items.stream().map(ItemStack::copy).toList();
            fluid = fluid.copy();
        }

        @Override
        public ItemStack getItem(int index) {
            return this.items.get(index);
        }

        @Override
        public int size() {
            return this.items.size();
        }

        @Override
        public boolean isEmpty() {
            return this.items.isEmpty() && this.fluid.isEmpty();
        }
    }

    public static final class Serializer implements RecipeSerializer<CatalyticPressingRecipe> {
        private static final MapCodec<CatalyticPressingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Ingredient.CODEC.listOf().optionalFieldOf("ingredients", List.of())
                    .forGetter(CatalyticPressingRecipe::itemIngredients),
                SizedFluidIngredient.FLAT_CODEC.fieldOf("fluid").forGetter(CatalyticPressingRecipe::fluidIngredient),
                FluidStack.CODEC.fieldOf("result").forGetter(CatalyticPressingRecipe::fluidResult),
                Codec.INT.fieldOf("processing_time").forGetter(CatalyticPressingRecipe::processingTime),
                Codec.BOOL.optionalFieldOf("consume_maximum", false)
                    .forGetter(CatalyticPressingRecipe::consumeMaximum)
            ).apply(instance, CatalyticPressingRecipe::new)
        );
        private static final StreamCodec<RegistryFriendlyByteBuf, CatalyticPressingRecipe> STREAM_CODEC =
            StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()),
                CatalyticPressingRecipe::itemIngredients,
                SizedFluidIngredient.STREAM_CODEC,
                CatalyticPressingRecipe::fluidIngredient,
                FluidStack.STREAM_CODEC,
                CatalyticPressingRecipe::fluidResult,
                ByteBufCodecs.INT,
                CatalyticPressingRecipe::processingTime,
                ByteBufCodecs.BOOL,
                CatalyticPressingRecipe::consumeMaximum,
                CatalyticPressingRecipe::new
            );

        @Override
        public MapCodec<CatalyticPressingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CatalyticPressingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }

    public static final class Builder extends AbstractRecipeBuilder<CatalyticPressingRecipe> {
        private final List<Ingredient> ingredients = new ArrayList<>();
        private SizedFluidIngredient fluidIngredient;
        private FluidStack result = FluidStack.EMPTY;
        private int processingTime = 800;
        private boolean consumeMaximum;

        public Builder requires(Item item) {
            this.ingredients.add(Ingredient.of(item));
            return this;
        }

        public Builder requires(Fluid fluid, int amount) {
            this.fluidIngredient = SizedFluidIngredient.of(fluid, amount);
            return this;
        }

        public Builder result(Fluid fluid, int amount) {
            this.result = new FluidStack(fluid, amount);
            return this;
        }

        public Builder processingTime(int processingTime) {
            this.processingTime = processingTime;
            return this;
        }

        public Builder consumeMaximum() {
            this.consumeMaximum = true;
            return this;
        }

        @Override
        public CatalyticPressingRecipe buildRecipe() {
            return new CatalyticPressingRecipe(
                this.ingredients,
                this.fluidIngredient,
                this.result,
                this.processingTime,
                this.consumeMaximum
            );
        }

        @Override
        public void validate(ResourceLocation id) {
            if (this.fluidIngredient == null || this.result.isEmpty()) {
                throw new IllegalArgumentException("Catalytic pressing needs a fluid input and output: " + id);
            }
            if (this.processingTime <= 0) {
                throw new IllegalArgumentException("Catalytic pressing time must be positive: " + id);
            }
        }

        @Override
        public String getType() {
            return "catalytic_pressing";
        }

        @Override
        public Item getResult() {
            return this.result.isEmpty() ? Items.AIR : this.result.getFluid().getBucket();
        }
    }
}
