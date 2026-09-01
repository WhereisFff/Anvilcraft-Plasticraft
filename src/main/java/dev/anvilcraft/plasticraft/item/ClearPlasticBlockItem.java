package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.entity.ClearPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/** 透明塑料物品，使用通用塑料制品的颜色变体与实体放置流程。 */
public final class ClearPlasticBlockItem extends UniversalPlasticBlockItem {
    public ClearPlasticBlockItem(
        Block block,
        Properties properties,
        Supplier<? extends EntityType<? extends UniversalPlasticEntity>> entityType,
        Supplier<BlockState> displayState
    ) {
        super(block, properties, entityType, displayState);
    }

    @Override
    protected String materialKey() {
        return "clear_plastic";
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
        return new ClearPlasticEntity(entityType, level, position, displayState, dropStack, orientation);
    }

}
