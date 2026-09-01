package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.entity.HeatResistantPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** 耐热塑料方块的实体化兼容实现。 */
public final class HeatResistantPlasticBlock extends UniversalPlasticBlock {
    public HeatResistantPlasticBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected EntityType<? extends UniversalPlasticEntity> getPlasticEntityType() {
        return PlasticraftEntities.HEAT_RESISTANT_PLASTIC.get();
    }

    @Override
    protected ItemStack createDropStack(BlockState state) {
        ItemStack stack = new ItemStack(this);
        PlasticItemData.setMaterial(stack, "heat_resistant_plastic");
        PlasticMeltColor.set(stack, state.getValue(DyeableMaterial.COLOR));
        if (state.getValue(MAGNETIZED)) PlasticItemData.setMagnetized(stack, true);
        return stack;
    }

    @Override
    protected UniversalPlasticEntity createPlasticEntity(
        EntityType<? extends UniversalPlasticEntity> entityType,
        ServerLevel level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        return new HeatResistantPlasticEntity(
            PlasticraftEntities.HEAT_RESISTANT_PLASTIC.get(),
            level,
            position,
            displayState,
            dropStack,
            orientation
        );
    }
}
