package dev.anvilcraft.plasticraft.recipe;

import com.mojang.serialization.MapCodec;
import dev.anvilcraft.plasticraft.init.PlasticraftRecipeTypes;
import dev.anvilcraft.plasticraft.item.PlasticMoldingChamberItem;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPowerBridge;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

import java.util.List;

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
    public ItemStack assemble(CraftingInput input) {
        ItemStack result = this.base.assemble(input);
        boolean charged = input.items().stream().anyMatch(stack -> stack.is(ModItems.SUPER_CAPACITOR));
        return PlasticMoldingChamberItem.setStoredEnergy(
            result,
            charged ? MoldingPowerBridge.capacity() : 0
        );
    }

    @Override
    public PlacementInfo placementInfo() {
        return this.base.placementInfo();
    }

    @Override
    public List<RecipeDisplay> display() {
        return this.base.display().stream().map(display -> {
            if (display instanceof ShapedCraftingRecipeDisplay shaped
                && shaped.result() instanceof SlotDisplay.ItemStackSlotDisplay result) {
                SlotDisplay charged = new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(
                    PlasticMoldingChamberItem.setStoredEnergy(result.stack().create(), MoldingPowerBridge.capacity())
                ));
                return (RecipeDisplay) new ShapedCraftingRecipeDisplay(
                    shaped.width(), shaped.height(), shaped.ingredients(), charged, shaped.craftingStation()
                );
            }
            return display;
        }).toList();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return this.base.getRemainingItems(input);
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
    public String group() {
        return this.base.group();
    }

    @Override
    public CraftingBookCategory category() {
        return this.base.category();
    }

    @Override
    public RecipeSerializer<PlasticMoldingChamberRecipe> getSerializer() {
        return PlasticraftRecipeTypes.PLASTIC_MOLDING_CHAMBER.get();
    }

    private ShapedRecipe base() {
        return this.base;
    }

    private static final MapCodec<PlasticMoldingChamberRecipe> CODEC = ShapedRecipe.MAP_CODEC.xmap(
        PlasticMoldingChamberRecipe::new,
        PlasticMoldingChamberRecipe::base
    );
    private static final StreamCodec<RegistryFriendlyByteBuf, PlasticMoldingChamberRecipe> STREAM_CODEC =
        ShapedRecipe.STREAM_CODEC.map(
            PlasticMoldingChamberRecipe::new,
            PlasticMoldingChamberRecipe::base
        );
    public static final RecipeSerializer<PlasticMoldingChamberRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

}
