package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.init.entity.ModEntities;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 六向硬化树脂釜使用的兼容展示方块。 */
public class HardenedResinCauldronBlock extends AbstractPlasticEntityBlock<HardenedResinCauldronEntity> {
    public HardenedResinCauldronBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(MAGNETIZED, false));
    }

    @Override
    protected EntityType<? extends HardenedResinCauldronEntity> getPlasticEntityType() {
        return ModEntities.HARDEND_RESIN_CAULDRON.get();
    }

    @Override
    protected ItemStack createDropStack(BlockState state) {
        ItemStack stack = new ItemStack(this);
        PlasticItemData.setMaterial(stack, "hardened_resin");
        if (state.hasProperty(MAGNETIZED) && state.getValue(MAGNETIZED)) {
            PlasticItemData.setMagnetized(stack, true);
        }
        return stack;
    }

    @Override
    protected HardenedResinCauldronEntity createPlasticEntity(
        EntityType<? extends HardenedResinCauldronEntity> entityType,
        ServerLevel level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        return new HardenedResinCauldronEntity(entityType, level, position, displayState, dropStack, orientation);
    }

    @Override
    public VoxelShape getShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return Blocks.CAULDRON.defaultBlockState().getShape(level, pos, context);
    }

    @Override
    public InteractionResult use(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hit
    ) {
        return InteractionResult.PASS;
    }
}
