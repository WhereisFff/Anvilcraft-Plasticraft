package dev.anvilcraft.plasticraft.recipe;

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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Objects;

/** 冷凝塔把内部虚拟气体转成真实流体的配方。 */
public final class CondenserRecipe implements Recipe<CondenserRecipe.Input> {
    private final ResourceLocation gas;
    private final int consume;
    private final ResourceLocation fluid;
    private final int produce;

    public CondenserRecipe(ResourceLocation gas, int consume, ResourceLocation fluid, int produce) {
        this.gas = Objects.requireNonNull(CondenserGas.canonicalize(gas), "gas");
        this.consume = consume;
        this.fluid = fluid;
        this.produce = produce;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ResourceLocation gas() {
        return this.gas;
    }

    public int consume() {
        return this.consume;
    }

    public ResourceLocation fluid() {
        return this.fluid;
    }

    public int produce() {
        return this.produce;
    }

    public boolean matches(ResourceLocation gasId, int amount) {
        return this.gas.equals(gasId) && amount >= this.consume;
    }

    @Override
    public boolean matches(Input input, Level level) {
        return this.matches(input.gas(), input.amount());
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
    public RecipeSerializer<CondenserRecipe> getSerializer() {
        return ModRecipeTypes.CONDENSER_SERIALIZER.get();
    }

    @Override
    public RecipeType<CondenserRecipe> getType() {
        return ModRecipeTypes.CONDENSER_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    /** 配方管理器只需要气体标识和数量作为匹配输入。 */
    public record Input(ResourceLocation gas, int amount) implements RecipeInput {
        @Override
        public ItemStack getItem(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return this.amount <= 0;
        }
    }

    public static final class Serializer implements RecipeSerializer<CondenserRecipe> {
        private static final MapCodec<CondenserRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("gas").forGetter(CondenserRecipe::gas),
            com.mojang.serialization.Codec.INT.fieldOf("consume").forGetter(CondenserRecipe::consume),
            ResourceLocation.CODEC.fieldOf("fluid").forGetter(CondenserRecipe::fluid),
            com.mojang.serialization.Codec.INT.fieldOf("produce").forGetter(CondenserRecipe::produce)
        ).apply(instance, CondenserRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, CondenserRecipe> STREAM_CODEC =
            StreamCodec.composite(
                ResourceLocation.STREAM_CODEC,
                CondenserRecipe::gas,
                ByteBufCodecs.INT,
                CondenserRecipe::consume,
                ResourceLocation.STREAM_CODEC,
                CondenserRecipe::fluid,
                ByteBufCodecs.INT,
                CondenserRecipe::produce,
                CondenserRecipe::new
            );

        @Override
        public MapCodec<CondenserRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CondenserRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }

    public static final class Builder extends AbstractRecipeBuilder<CondenserRecipe> {
        private ResourceLocation gas;
        private ResourceLocation fluid;
        private int consume;
        private int produce;

        public Builder gas(ResourceLocation gas) {
            this.gas = gas;
            return this;
        }

        public Builder consume(int consume) {
            this.consume = consume;
            return this;
        }

        public Builder fluid(ResourceLocation fluid) {
            this.fluid = fluid;
            return this;
        }

        public Builder produce(int produce) {
            this.produce = produce;
            return this;
        }

        @Override
        public CondenserRecipe buildRecipe() {
            return new CondenserRecipe(this.gas, this.consume, this.fluid, this.produce);
        }

        @Override
        public void validate(ResourceLocation id) {
            if (this.gas == null) {
                throw new IllegalArgumentException("Condenser gas must not be empty, RecipeId: " + id);
            }
            if (this.consume <= 0 || this.produce <= 0) {
                throw new IllegalArgumentException("Condenser amounts must be positive, RecipeId: " + id);
            }
            if (this.fluid == null) {
                throw new IllegalArgumentException("Condenser output fluid must not be empty, RecipeId: " + id);
            }
        }

        @Override
        public String getType() {
            return "condenser";
        }

        @Override
        public Item getResult() {
            if (this.fluid == null) return Items.AIR;
            Fluid value = BuiltInRegistries.FLUID.get(this.fluid);
            return value == null || value == Fluids.EMPTY ? Items.AIR : value.getBucket();
        }
    }
}
