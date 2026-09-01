package dev.anvilcraft.plasticraft.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.lib.v2.util.predicate.ChanceItemStack;
import dev.anvilcraft.lib.v2.util.predicate.ItemIngredientPredicate;
import dev.anvilcraft.plasticraft.init.PlasticraftRecipeTypes;
import dev.dubhe.anvilcraft.recipe.anvil.util.WrapUtils;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.AbstractProcessRecipe;
import dev.dubhe.anvilcraft.recipe.component.HasCauldronSimple;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/** 支持物品、流体或两者共同输入输出的等离子喷流灼烧配方。 */
public final class PlasmaJetBlastingRecipe extends AbstractProcessRecipe<PlasmaJetBlastingRecipe> {
    // 冷凝塔气体是虚拟标识而非注册流体,无法进入 HasCauldronSimple 的 FluidStack
    // 转换列表,因此作为配方自身的独立输出字段保存。
    private final Optional<GasOutput> gasOutput;

    public PlasmaJetBlastingRecipe(
        List<ItemIngredientPredicate> itemIngredients,
        List<ChanceItemStack> results,
        HasCauldronSimple hasCauldron,
        Optional<GasOutput> gasOutput
    ) {
        super(new Property()
            .setItemInputOffset(new Vec3(0.0D, 0.625D, 0.0D))
            .setItemInputRange(new Vec3(1.5D, 1.5D, 1.5D))
            .setInputItems(itemIngredients)
            .setItemOutputOffset(new Vec3(0.0D, 0.25D, 0.0D))
            .setResultItems(results)
            .setCauldronOffset(Vec3i.ZERO)
            .setHasCauldron(hasCauldron));
        this.gasOutput = gasOutput;
    }

    /** 汽化产物的虚拟气体标识与产量。 */
    public record GasOutput(ResourceLocation id, int amount) {
        public static final Codec<GasOutput> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(GasOutput::id),
            Codec.INT.fieldOf("amount").forGetter(GasOutput::amount)
        ).apply(instance, GasOutput::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, GasOutput> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            GasOutput::id,
            ByteBufCodecs.VAR_INT,
            GasOutput::amount,
            GasOutput::new
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<GasOutput> gasOutput() {
        return this.gasOutput;
    }

    public boolean hasFluidInput() {
        HasCauldronSimple cauldron = this.getHasCauldron();
        return cauldron.hasFluid() && cauldron.consume() > 0;
    }

    public boolean hasFluidOutput() {
        return this.gasOutput.isPresent() || this.getHasCauldron().produce() > 0;
    }

    @Override
    public RecipeSerializer<PlasmaJetBlastingRecipe> getSerializer() {
        return PlasticraftRecipeTypes.PLASMA_JET_BLASTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<PlasmaJetBlastingRecipe> getType() {
        return PlasticraftRecipeTypes.PLASMA_JET_BLASTING_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<PlasmaJetBlastingRecipe> {
        private static final MapCodec<PlasmaJetBlastingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ItemIngredientPredicate.CODEC.listOf()
                .optionalFieldOf("ingredients", List.of())
                .forGetter(PlasmaJetBlastingRecipe::getInputItems),
            ChanceItemStack.CODEC.listOf()
                .optionalFieldOf("results", List.of())
                .forGetter(PlasmaJetBlastingRecipe::getResultItems),
            HasCauldronSimple.CODEC.forGetter(PlasmaJetBlastingRecipe::getHasCauldron),
            GasOutput.CODEC.optionalFieldOf("gas").forGetter(PlasmaJetBlastingRecipe::gasOutput)
        ).apply(instance, PlasmaJetBlastingRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, PlasmaJetBlastingRecipe> STREAM_CODEC =
            StreamCodec.composite(
                ItemIngredientPredicate.STREAM_CODEC.apply(ByteBufCodecs.list()),
                PlasmaJetBlastingRecipe::getInputItems,
                ChanceItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()),
                PlasmaJetBlastingRecipe::getResultItems,
                HasCauldronSimple.STREAM_CODEC,
                PlasmaJetBlastingRecipe::getHasCauldron,
                ByteBufCodecs.optional(GasOutput.STREAM_CODEC),
                PlasmaJetBlastingRecipe::gasOutput,
                PlasmaJetBlastingRecipe::new
            );

        @Override
        public MapCodec<PlasmaJetBlastingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PlasmaJetBlastingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }

    public static final class Builder extends SimpleAbstractBuilder<PlasmaJetBlastingRecipe, Builder> {
        private final HasCauldronSimple.Builder cauldron = HasCauldronSimple.empty();
        private Optional<GasOutput> gasOutput = Optional.empty();

        public Builder fluid(Fluid fluid) {
            this.cauldron.fluid(fluid);
            return this;
        }

        public Builder fluid(Holder<Fluid> fluid) {
            this.cauldron.fluid(fluid);
            return this;
        }

        public Builder fluid(TagKey<Fluid> tag) {
            this.cauldron.fluid(tag);
            return this;
        }

        public Builder cauldron(Block cauldronBlock) {
            return this.fluid(BuiltInRegistries.FLUID.get(WrapUtils.cauldron2Fluid(cauldronBlock)));
        }

        public Builder consume(int amount) {
            this.cauldron.consume(amount);
            return this;
        }

        public Builder transform(Fluid fluid, int amount) {
            this.cauldron.transform(fluid, amount);
            return this;
        }

        public Builder gas(ResourceLocation gasId, int amount) {
            this.gasOutput = Optional.of(new GasOutput(gasId, amount));
            return this;
        }

        @Override
        protected PlasmaJetBlastingRecipe of(
            List<ItemIngredientPredicate> itemIngredients,
            List<ChanceItemStack> results
        ) {
            return new PlasmaJetBlastingRecipe(itemIngredients, results, this.cauldron.build(), this.gasOutput);
        }

        @Override
        public void validate(ResourceLocation id) {
            HasCauldronSimple fluid = this.cauldron.build();
            boolean declaresFluidInput = fluid.hasFluid();
            boolean hasFluidInput = declaresFluidInput && fluid.consume() > 0;
            boolean hasFluidOutput = this.gasOutput.isPresent() || fluid.produce() > 0;
            if (declaresFluidInput && fluid.consume() <= 0) {
                throw new IllegalArgumentException("Plasma jet blasting fluid input must consume a positive amount: " + id);
            }
            if (this.gasOutput.map(GasOutput::amount).orElse(1) <= 0) {
                throw new IllegalArgumentException("Plasma jet blasting gas output must produce a positive amount: " + id);
            }
            if (this.itemIngredients.isEmpty() && !hasFluidInput) {
                throw new IllegalArgumentException("Plasma jet blasting recipe needs an item or fluid input: " + id);
            }
            if (this.results.isEmpty() && !hasFluidOutput) {
                throw new IllegalArgumentException("Plasma jet blasting recipe needs an item or fluid output: " + id);
            }
        }

        @Override
        public String getType() {
            return "plasma_jet_blasting";
        }

        @Override
        protected Builder getThis() {
            return this;
        }
    }
}
