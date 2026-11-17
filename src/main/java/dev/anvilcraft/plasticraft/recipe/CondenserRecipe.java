package dev.anvilcraft.plasticraft.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.plasticraft.init.PlasticraftRecipeTypes;
import dev.dubhe.anvilcraft.recipe.anvil.builder.AbstractRecipeBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

import java.util.Objects;

/** 冷凝塔把内部虚拟气体转成真实流体的配方。 */
public final class CondenserRecipe implements Recipe<CondenserRecipe.Input> {
    private final Identifier gas;
    private final int consume;
    private final Identifier fluid;
    private final int produce;
    private final int towerLevel;

    public CondenserRecipe(Identifier gas, int consume, Identifier fluid, int produce, int towerLevel) {
        this.gas = Objects.requireNonNull(CondenserGas.canonicalize(gas), "gas");
        this.consume = consume;
        this.fluid = fluid;
        this.produce = produce;
        this.towerLevel = towerLevel;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Identifier gas() {
        return this.gas;
    }

    public int consume() {
        return this.consume;
    }

    public Identifier fluid() {
        return this.fluid;
    }

    public int produce() {
        return this.produce;
    }

    public int towerLevel() {
        return this.towerLevel;
    }

    public boolean matches(Identifier gasId, int amount) {
        return this.gas.equals(gasId) && amount >= this.consume;
    }

    @Override
    public boolean matches(Input input, Level level) {
        return this.matches(input.gas(), input.amount());
    }

    @Override
    public ItemStack assemble(Input input) {
        return ItemStack.EMPTY;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<CondenserRecipe> getSerializer() {
        return PlasticraftRecipeTypes.CONDENSER_SERIALIZER.get();
    }

    @Override
    public RecipeType<CondenserRecipe> getType() {
        return PlasticraftRecipeTypes.CONDENSER_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    /** 配方管理器只需要气体标识和数量作为匹配输入。 */
    public record Input(Identifier gas, int amount) implements RecipeInput {
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

    private static final MapCodec<CondenserRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Identifier.CODEC.fieldOf("gas").forGetter(CondenserRecipe::gas),
        Codec.INT.fieldOf("consume").forGetter(CondenserRecipe::consume),
        Identifier.CODEC.fieldOf("fluid").forGetter(CondenserRecipe::fluid),
        Codec.INT.fieldOf("produce").forGetter(CondenserRecipe::produce),
        Codec.INT.optionalFieldOf("tower_level", 1)
            .forGetter(CondenserRecipe::towerLevel)
    ).apply(instance, CondenserRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, CondenserRecipe> STREAM_CODEC =
        StreamCodec.composite(
            Identifier.STREAM_CODEC,
            CondenserRecipe::gas,
            ByteBufCodecs.INT,
            CondenserRecipe::consume,
            Identifier.STREAM_CODEC,
            CondenserRecipe::fluid,
            ByteBufCodecs.INT,
            CondenserRecipe::produce,
            ByteBufCodecs.INT,
            CondenserRecipe::towerLevel,
            CondenserRecipe::new
        );
    public static final RecipeSerializer<CondenserRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);


    public static final class Builder extends AbstractRecipeBuilder<CondenserRecipe> {
        private Identifier gas;
        private Identifier fluid;
        private int consume;
        private int produce;
        private int towerLevel = 1;

        public Builder gas(Identifier gas) {
            this.gas = gas;
            return this;
        }

        public Builder consume(int consume) {
            this.consume = consume;
            return this;
        }

        public Builder fluid(Identifier fluid) {
            this.fluid = fluid;
            return this;
        }

        public Builder produce(int produce) {
            this.produce = produce;
            return this;
        }

        public Builder towerLevel(int towerLevel) {
            this.towerLevel = towerLevel;
            return this;
        }

        @Override
        public CondenserRecipe buildRecipe() {
            return new CondenserRecipe(this.gas, this.consume, this.fluid, this.produce, this.towerLevel);
        }

        @Override
        public void validate(Identifier id) {
            if (this.gas == null) {
                throw new IllegalArgumentException("Condenser gas must not be empty, RecipeId: " + id);
            }
            if (this.consume <= 0 || this.produce <= 0) {
                throw new IllegalArgumentException("Condenser amounts must be positive, RecipeId: " + id);
            }
            if (this.towerLevel <= 0) {
                throw new IllegalArgumentException("Condenser tower level must be positive, RecipeId: " + id);
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
        public ItemStackTemplate getResult() {
            if (this.fluid == null) {
                throw new IllegalStateException("Missing condenser output fluid");
            }
            Fluid value = BuiltInRegistries.FLUID.getValue(this.fluid);
            return new ItemStackTemplate(value.getBucket());
        }
    }
}
