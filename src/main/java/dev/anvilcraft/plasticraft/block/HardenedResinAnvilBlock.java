package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.entity.HardenedResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 作为可移动硬化树脂砧基础的皇家铁砧形展示方块。
 * 常规物品路径会创建实体，而不是放置此方块。
 */
public class HardenedResinAnvilBlock extends AbstractPlasticEntityBlock<HardenedResinAnvilEntity> {
    public HardenedResinAnvilBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(MAGNETIZED, false)
            .setValue(BONDED, false));
    }

    @Override
    protected EntityType<? extends HardenedResinAnvilEntity> getPlasticEntityType() {
        return PlasticraftEntities.HARDEND_RESIN_ANVIL.get();
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
    protected HardenedResinAnvilEntity createPlasticEntity(
        EntityType<? extends HardenedResinAnvilEntity> entityType,
        ServerLevel level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        return new HardenedResinAnvilEntity(entityType, level, position, displayState, dropStack, orientation);
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
        if (state.getValue(BONDED)) return super.use(state, level, pos, player, hand, hit);
        // 通过命令放置的展示方块不应意外打开皇家铁砧菜单。
        return InteractionResult.PASS;
    }
}
