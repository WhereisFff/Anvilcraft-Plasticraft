package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.entity.ClearPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** 透明塑料制品方块，保留染色玻璃式颜色状态并允许本体的穿透标签识别。 */
public final class ClearPlasticBlock extends UniversalPlasticBlock {
    public ClearPlasticBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean hasColorState() {
        return true;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public Integer getBeaconColorMultiplier(BlockState state, LevelReader level, BlockPos pos, BlockPos beaconPos) {
        return state.getValue(DyeableMaterial.COLOR).getTextureDiffuseColor();
    }

    @Override
    protected EntityType<? extends UniversalPlasticEntity> getPlasticEntityType() {
        return PlasticraftEntities.CLEAR_PLASTIC.get();
    }

    @Override
    protected ItemStack createDropStack(BlockState state) {
        ItemStack stack = new ItemStack(this);
        PlasticItemData.setMaterial(stack, "clear_plastic");
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
        return new ClearPlasticEntity(
            PlasticraftEntities.CLEAR_PLASTIC.get(),
            level,
            position,
            displayState,
            dropStack,
            orientation
        );
    }
}
