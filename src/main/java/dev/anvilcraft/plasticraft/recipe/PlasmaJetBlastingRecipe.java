package dev.anvilcraft.plasticraft.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.lib.v2.util.predicate.ChanceItemStack;
import dev.anvilcraft.lib.v2.util.predicate.ItemIngredientPredicate;
import dev.anvilcraft.plasticraft.init.ModRecipeTypes;
import dev.dubhe.anvilcraft.recipe.anvil.predicate.block.HasCauldron;
import dev.dubhe.anvilcraft.recipe.anvil.util.WrapUtils;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.AbstractProcessRecipe;
import dev.dubhe.anvilcraft.recipe.component.HasCauldronSimple;
import net.minecraft.core.Vec3i;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** 支持物品、流体或两者共同输入输出的等离子喷流灼烧配方。 */
public final class PlasmaJetBlastingRecipe extends AbstractProcessRecipe<PlasmaJetBlastingRecipe> {
    public PlasmaJetBlastingRecipe(
        List<ItemIngredientPredicate> itemIngredients,
        List<ChanceItemStack> results,
        HasCauldronSimple hasCauldron
    ) {
        super(new Property()
            .setItemInputOffset(new Vec3(0.0D, 0.625D, 0.0D))
            .setItemInputRange(new Vec3(1.5D, 1.5D, 1.5D))
            .setInputItems(itemIngredients)
            .setItemOutputOffset(new Vec3(0.0D, 0.25D, 0.0D))
            .setResultItems(results)
            .setCauldronOffset(Vec3i.ZERO)
            .setHasCauldron(hasCauldron));
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean hasFluidInput() {
        HasCauldronSimple cauldron = this.getHasCauldron();
        return (HasCauldron.isNotEmpty(cauldron.fluid()) || cauldron.fluidTag() != null)
            && cauldron.consume() > 0;
    }

    public boolean hasFluidOutput() {
        HasCauldronSimple cauldron = this.getHasCauldron();
        return HasCauldron.isNotEmpty(cauldron.transform()) && cauldron.produce() > 0;
    }

    @Override
    public RecipeSerializer<PlasmaJetBlastingRecipe> getSerializer() {
        return ModRecipeTypes.PLASMA_JET_BLASTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<PlasmaJetBlastingRecipe> getType() {
        return ModRecipeTypes.PLASMA_JET_BLASTING_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<PlasmaJetBlastingRecipe> {
        private static final MapCodec<PlasmaJetBlastingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ItemIngredientPredicate.CODEC.listOf()
                .optionalFieldOf("ingredients", List.of())
                .forGetter(PlasmaJetBlastingRecipe::getInputItems),
            ChanceItemStack.CODEC.listOf()
                .optionalFieldOf("results", List.of())
                .forGetter(PlasmaJetBlastingRecipe::getResultItems),
            HasCauldronSimple.CODEC.forGetter(PlasmaJetBlastingRecipe::getHasCauldron)
        ).apply(instance, PlasmaJetBlastingRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, PlasmaJetBlastingRecipe> STREAM_CODEC =
            StreamCodec.composite(
                ItemIngredientPredicate.STREAM_CODEC.apply(ByteBufCodecs.list()),
                PlasmaJetBlastingRecipe::getInputItems,
                ChanceItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()),
                PlasmaJetBlastingRecipe::getResultItems,
                HasCauldronSimple.STREAM_CODEC,
                PlasmaJetBlastingRecipe::getHasCauldron,
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

        public Builder fluid(ResourceLocation fluid) {
            this.cauldron.fluid(fluid);
            return this;
        }

        public Builder cauldron(Block cauldronBlock) {
            return this.fluid(WrapUtils.cauldron2Fluid(cauldronBlock));
        }

        public Builder fluidTag(ResourceLocation tag) {
            this.cauldron.fluidTag(tag);
            return this;
        }

        public Builder consume(int amount) {
            this.cauldron.consume(amount);
            return this;
        }

        public Builder transform(ResourceLocation fluid) {
            this.cauldron.transform(fluid);
            return this;
        }

        public Builder produce(int amount) {
            this.cauldron.produce(amount);
            return this;
        }

        @Override
        protected PlasmaJetBlastingRecipe of(
            List<ItemIngredientPredicate> itemIngredients,
            List<ChanceItemStack> results
        ) {
            return new PlasmaJetBlastingRecipe(itemIngredients, results, this.cauldron.build());
        }

        @Override
        public void validate(ResourceLocation id) {
            HasCauldronSimple fluid = this.cauldron.build();
            boolean declaresFluidInput = HasCauldron.isNotEmpty(fluid.fluid()) || fluid.fluidTag() != null;
            boolean hasFluidInput = declaresFluidInput && fluid.consume() > 0;
            boolean hasFluidOutput = HasCauldron.isNotEmpty(fluid.transform()) && fluid.produce() > 0;
            if (declaresFluidInput && fluid.consume() <= 0) {
                throw new IllegalArgumentException("Plasma jet blasting fluid input must consume a positive amount: " + id);
            }
            if (HasCauldron.isNotEmpty(fluid.transform()) && fluid.produce() <= 0) {
                throw new IllegalArgumentException("Plasma jet blasting fluid output must produce a positive amount: " + id);
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
