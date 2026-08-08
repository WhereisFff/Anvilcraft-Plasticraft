package dev.anvilcraft.plasticraft.recipe;

import com.mojang.serialization.MapCodec;
import dev.anvilcraft.plasticraft.init.PlasticraftRecipeTypes;
import dev.anvilcraft.plasticraft.item.PlasticMoldingChamberItem;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPowerBridge;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/** 将合成输入中的超级电容器充电状态写入成型舱物品。 */
public final class PlasticMoldingChamberRecipe implements CraftingRecipe {
    private final ShapedRecipe base;

    public PlasticMoldingChamberRecipe(ShapedRecipe base) {
        this.base = base;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return this.base.matches(input, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider provider) {
        ItemStack result = this.base.assemble(input, provider);
        boolean charged = input.items().stream().anyMatch(stack -> stack.is(ModItems.SUPER_CAPACITOR));
        return PlasticMoldingChamberItem.setStoredEnergy(
            result,
            charged ? MoldingPowerBridge.capacity() : 0
        );
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return this.base.canCraftInDimensions(width, height);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider provider) {
        return PlasticMoldingChamberItem.setStoredEnergy(
            this.base.getResultItem(provider).copy(),
            MoldingPowerBridge.capacity()
        );
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return this.base.getRemainingItems(input);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.base.getIngredients();
    }

    @Override
    public boolean isSpecial() {
        return this.base.isSpecial();
    }

    @Override
    public boolean showNotification() {
        return this.base.showNotification();
    }

    @Override
    public String getGroup() {
        return this.base.getGroup();
    }

    @Override
    public ItemStack getToastSymbol() {
        return this.base.getToastSymbol();
    }

    @Override
    public CraftingBookCategory category() {
        return this.base.category();
    }

    @Override
    public boolean isIncomplete() {
        return this.base.isIncomplete();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return PlasticraftRecipeTypes.PLASTIC_MOLDING_CHAMBER.get();
    }

    private ShapedRecipe base() {
        return this.base;
    }

    public static final class Serializer implements RecipeSerializer<PlasticMoldingChamberRecipe> {
        private static final MapCodec<PlasticMoldingChamberRecipe> CODEC = ShapedRecipe.Serializer.CODEC.xmap(
            PlasticMoldingChamberRecipe::new,
            PlasticMoldingChamberRecipe::base
        );
        private static final StreamCodec<RegistryFriendlyByteBuf, PlasticMoldingChamberRecipe> STREAM_CODEC =
            ShapedRecipe.Serializer.STREAM_CODEC.map(
                PlasticMoldingChamberRecipe::new,
                PlasticMoldingChamberRecipe::base
            );

        @Override
        public MapCodec<PlasticMoldingChamberRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PlasticMoldingChamberRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
