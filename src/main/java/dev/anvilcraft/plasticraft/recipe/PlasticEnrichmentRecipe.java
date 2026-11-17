package dev.anvilcraft.plasticraft.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.dubhe.anvilcraft.recipe.FluidMixingRecipe;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.List;
import java.util.Optional;

/** 流体组件在配方加载后才绑定，增产配方只在加工或展示时创建产物流体栈。 */
public final class PlasticEnrichmentRecipe extends FluidMixingRecipe {
    private static final MapCodec<FluidMixingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        SizedFluidIngredient.CODEC.listOf(2, MAX_INGREDIENTS).fieldOf("ingredients").forGetter(FluidMixingRecipe::getFluidIngredients),
        FluidStackTemplate.CODEC.fieldOf("result").forGetter(PlasticEnrichmentRecipe::resultTemplate)
    ).apply(instance, PlasticEnrichmentRecipe::new));
    private static final StreamCodec<RegistryFriendlyByteBuf, FluidMixingRecipe> STREAM_CODEC = StreamCodec.composite(
        SizedFluidIngredient.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_INGREDIENTS)), FluidMixingRecipe::getFluidIngredients,
        FluidStackTemplate.STREAM_CODEC, PlasticEnrichmentRecipe::resultTemplate,
        PlasticEnrichmentRecipe::new
    );
    public static final RecipeSerializer<FluidMixingRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);
    private final FluidStackTemplate result;

    private PlasticEnrichmentRecipe(List<SizedFluidIngredient> ingredients, FluidStackTemplate result) {
        super(ingredients, List.of(), List.of(), true);
        this.result = result;
    }

    private static FluidStackTemplate resultTemplate(FluidMixingRecipe recipe) {
        return ((PlasticEnrichmentRecipe) recipe).result;
    }

    @Override
    public RecipeSerializer<FluidMixingRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public int getResultCount() {
        return 1;
    }

    @Override
    public List<FluidStack> getFluidResults() {
        return List.of(this.result.create());
    }

    @Override
    public Optional<List<FluidStack>> getFluidResults(int batches) {
        long amount = (long) this.result.amount() * batches;
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        return Optional.of(List.of(this.result.withAmount((int) amount).create()));
    }
}
