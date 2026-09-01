package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.entity.HeatResistantPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/** 放置耐热塑料时创建带有耐热材质身份的实体。 */
public final class HeatResistantPlasticBlockItem extends UniversalPlasticBlockItem {
    public HeatResistantPlasticBlockItem(
        Block block,
        Properties properties,
        Supplier<? extends EntityType<? extends UniversalPlasticEntity>> entityType,
        Supplier<BlockState> displayState
    ) {
        super(block, properties, entityType, displayState);
    }

    @Override
    protected String materialKey() {
        return "heat_resistant_plastic";
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
        return new HeatResistantPlasticEntity(
            entityType,
            level,
            position,
            displayState,
            dropStack,
            orientation
        );
    }
}
