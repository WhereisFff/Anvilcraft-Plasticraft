package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.block.PlasticPotBlock;
import dev.anvilcraft.plasticraft.entity.PlasticAnvilOrientation;
import dev.anvilcraft.plasticraft.entity.PlasticPotEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/** Color-preserving placement item for plastic pots. */
public class PlasticPotItem extends AbstractPlasticAnvilItem<PlasticPotEntity> {
    public PlasticPotItem(
        Block block,
        Properties properties,
        Supplier<? extends EntityType<? extends PlasticPotEntity>> entityType,
        Supplier<BlockState> displayState
    ) {
        super(block, properties, entityType, displayState);
    }

    @Override
    protected BlockState prepareDisplayState(ItemStack stack, BlockState state) {
        return PlasticPotBlock.withColor(state, PlasticItemData.getColor(stack));
    }

    @Override
    protected PlasticPotEntity createEntity(
        EntityType<? extends PlasticPotEntity> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticAnvilOrientation orientation
    ) {
        return new PlasticPotEntity(entityType, level, position, displayState, dropStack, orientation);
    }
}
