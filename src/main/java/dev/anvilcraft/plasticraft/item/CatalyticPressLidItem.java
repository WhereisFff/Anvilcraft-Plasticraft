package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.entity.CatalyticPressLidEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/** 催化压盖的六向实体放置物品。 */
public final class CatalyticPressLidItem extends AbstractPlasticEntityItem<CatalyticPressLidEntity> {
    public CatalyticPressLidItem(
        Block block,
        Properties properties,
        Supplier<? extends EntityType<? extends CatalyticPressLidEntity>> entityType,
        Supplier<BlockState> displayState
    ) {
        super(block, properties, entityType, displayState);
    }

    @Override
    protected String materialKey() {
        return "hardened_resin";
    }

    @Override
    protected CatalyticPressLidEntity createEntity(
        EntityType<? extends CatalyticPressLidEntity> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        return new CatalyticPressLidEntity(entityType, level, position, displayState, dropStack, orientation);
    }
}
