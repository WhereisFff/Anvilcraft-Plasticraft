package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.function.Supplier;

/** 把熔体颜色写入显示方块状态，并直接放置对应的可移动塑料实体。 */
public class UniversalPlasticBlockItem extends AbstractPlasticEntityItem<UniversalPlasticEntity> {
    public UniversalPlasticBlockItem(
        Block block,
        Properties properties,
        Supplier<? extends EntityType<? extends UniversalPlasticEntity>> entityType,
        Supplier<BlockState> displayState
    ) {
        super(block, properties, entityType, displayState);
    }

    @Override
    protected BlockState prepareDisplayState(ItemStack stack, BlockState state) {
        return state.setValue(
            DyeableMaterial.COLOR,
            MoldedPlasticData.get(stack)
                .map(data -> PlasticMeltColor.get(data.material()))
                .orElseGet(() -> PlasticMeltColor.get(stack))
        );
    }

    @Override
    protected String materialKey() {
        return "universal_plastic";
    }

    @Override
    protected UniversalPlasticEntity createEntity(
        EntityType<? extends UniversalPlasticEntity> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        return new UniversalPlasticEntity(entityType, level, position, displayState, dropStack, orientation);
    }
}
