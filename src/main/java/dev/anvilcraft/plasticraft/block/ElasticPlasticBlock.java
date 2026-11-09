package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.entity.ElasticPlasticEntity;
import dev.anvilcraft.plasticraft.entity.physics.ElasticPlasticPhysics;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** 弹性塑料制品的方块兼容态。 */
public final class ElasticPlasticBlock extends UniversalPlasticBlock {
    public ElasticPlasticBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        ModBlocks.RESIN_BLOCK.get().fallOn(level, state, pos, entity, fallDistance);
    }

    @Override
    public void updateEntityAfterFallOn(BlockGetter level, Entity entity) {
        if (entity.isSuppressingBounce()) {
            super.updateEntityAfterFallOn(level, entity);
        } else {
            ElasticPlasticPhysics.bounceOnSmoothSurface(entity);
        }
    }

    @Override
    public boolean isSlimeBlock(BlockState state) {
        return true;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected EntityType<? extends UniversalPlasticEntity> getPlasticEntityType() {
        return PlasticraftEntities.ELASTIC_PLASTIC.get();
    }

    @Override
    protected ItemStack createDropStack(BlockState state) {
        ItemStack stack = new ItemStack(this);
        PlasticItemData.setMaterial(stack, "elastic_plastic");
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
        return new ElasticPlasticEntity(
            PlasticraftEntities.ELASTIC_PLASTIC.get(),
            level,
            position,
            displayState,
            dropStack,
            orientation
        );
    }
}
