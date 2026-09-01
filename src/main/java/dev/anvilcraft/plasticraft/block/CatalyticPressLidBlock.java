package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.CatalyticPressLidEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.collision.BuiltInPlasticEntityModels;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 可被高粘性树脂固定并方块化的催化压盖。 */
public final class CatalyticPressLidBlock extends AbstractPlasticEntityBlock<CatalyticPressLidEntity> {
    private static final VoxelShape LID_SHAPE = BuiltInPlasticEntityModels
        .CATALYTIC_PRESS_LID_COMPATIBILITY;
    private static final Map<PlasticEntityOrientation, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    public CatalyticPressLidBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(MAGNETIZED, false)
            .setValue(BONDED, false));
    }

    @Override
    protected EntityType<? extends CatalyticPressLidEntity> getPlasticEntityType() {
        return PlasticraftEntities.CATALYTIC_PRESS_LID.get();
    }

    @Override
    protected CatalyticPressLidEntity createPlasticEntity(
        EntityType<? extends CatalyticPressLidEntity> entityType,
        ServerLevel level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        return new CatalyticPressLidEntity(entityType, level, position, displayState, dropStack, orientation);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        PlasticEntityOrientation orientation = state.getValue(BONDED)
            && level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
            && bonded.isInitialized()
            ? bonded.getPlasticOrientation()
            : PlasticEntityOrientation.fromLegacyState(state);
        return SHAPES.computeIfAbsent(orientation, key -> rotateShape(LID_SHAPE, key));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }
}
