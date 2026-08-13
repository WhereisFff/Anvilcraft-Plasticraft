package dev.anvilcraft.plasticraft.recipe;

import com.mojang.serialization.MapCodec;
import dev.anvilcraft.plasticraft.drone.DroneDefaultPropeller;
import dev.anvilcraft.plasticraft.init.PlasticraftRecipeTypes;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

import java.util.stream.Stream;

/**
 * 匹配玩家在成型舱制作的塑料螺旋桨:通用塑料物品且制品类型为 propeller。
 * 螺旋桨没有固定物品 ID,必须按数据组件测试,不能写成普通物品 Ingredient。
 */
public final class DronePropellerIngredient implements ICustomIngredient {
    public static final DronePropellerIngredient INSTANCE = new DronePropellerIngredient();
    public static final MapCodec<DronePropellerIngredient> CODEC = MapCodec.unit(INSTANCE);

    private DronePropellerIngredient() {
    }

    @Override
    public boolean test(ItemStack stack) {
        return stack.is(PlasticraftBlocks.UNIVERSAL_PLASTIC.asItem())
            && MoldedPlasticData.get(stack)
                .map(data -> MoldingProductTypes.PROPELLER_ID.equals(data.finalType()))
                .orElse(false);
    }

    @Override
    public Stream<ItemStack> getItems() {
        // 展示用途;配方界面显示默认白色螺旋桨制品,真实匹配始终经过 test。
        return Stream.of(DroneDefaultPropeller.stack());
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return PlasticraftRecipeTypes.DRONE_PROPELLER_INGREDIENT.get();
    }
}
