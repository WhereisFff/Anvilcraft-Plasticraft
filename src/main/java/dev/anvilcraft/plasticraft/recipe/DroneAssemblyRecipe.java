package dev.anvilcraft.plasticraft.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.plasticraft.drone.DroneData;
import dev.anvilcraft.plasticraft.init.PlasticraftRecipeTypes;
import dev.anvilcraft.plasticraft.item.DroneItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * 无人机装配配方。形状匹配沿用原版有序配方,装配时把两格螺旋桨的完整物品堆
 * 写入成品数据组件,两个螺旋桨各自保留玩家制作的模型、颜色与材质,不合并外观。
 */
public class DroneAssemblyRecipe extends ShapedRecipe {
    // 父类 result 字段是包私有,序列化器需要在此保留一份可访问副本。
    private final ItemStack resultStack;

    public DroneAssemblyRecipe(
        String group,
        CraftingBookCategory category,
        ShapedRecipePattern pattern,
        ItemStack result,
        boolean showNotification
    ) {
        super(group, category, pattern, result, showNotification);
        this.resultStack = result;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = super.assemble(input, registries);
        if (result.getItem() instanceof DroneItem droneItem) {
            ItemStack leftPropeller = ItemStack.EMPTY;
            ItemStack rightPropeller = ItemStack.EMPTY;
            // 不硬编码槽位:按阅读顺序找到的第一个螺旋桨是左桨,第二个是右桨,
            // 这样配方在更大合成网格中偏移放置时仍然取到正确物品。
            for (int slot = 0; slot < input.size(); slot++) {
                ItemStack candidate = input.getItem(slot);
                if (!DronePropellerIngredient.INSTANCE.test(candidate)) continue;
                if (leftPropeller.isEmpty()) {
                    leftPropeller = candidate.copyWithCount(1);
                } else {
                    rightPropeller = candidate.copyWithCount(1);
                    break;
                }
            }
            DroneData.set(result, DroneData.assembled(droneItem.definition().id(), leftPropeller, rightPropeller));
        }
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return PlasticraftRecipeTypes.DRONE_ASSEMBLY.get();
    }

    public static class Serializer implements RecipeSerializer<DroneAssemblyRecipe> {
        public static final MapCodec<DroneAssemblyRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(ShapedRecipe::getGroup),
                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC)
                    .forGetter(ShapedRecipe::category),
                ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.resultStack),
                Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(ShapedRecipe::showNotification)
            )
            .apply(instance, DroneAssemblyRecipe::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, DroneAssemblyRecipe> STREAM_CODEC = StreamCodec.of(
            Serializer::toNetwork,
            Serializer::fromNetwork
        );

        @Override
        public MapCodec<DroneAssemblyRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, DroneAssemblyRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static DroneAssemblyRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            String group = buffer.readUtf();
            CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
            ShapedRecipePattern pattern = ShapedRecipePattern.STREAM_CODEC.decode(buffer);
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            boolean showNotification = buffer.readBoolean();
            return new DroneAssemblyRecipe(group, category, pattern, result, showNotification);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, DroneAssemblyRecipe recipe) {
            buffer.writeUtf(recipe.getGroup());
            buffer.writeEnum(recipe.category());
            ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.pattern);
            ItemStack.STREAM_CODEC.encode(buffer, recipe.resultStack);
            buffer.writeBoolean(recipe.showNotification());
        }
    }
}
