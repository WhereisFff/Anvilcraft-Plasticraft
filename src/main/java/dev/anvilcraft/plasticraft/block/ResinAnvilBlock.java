package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.ResinAnvilEntity;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** 初期弹性树脂砧使用的固定颜色兼容方块。 */
public class ResinAnvilBlock extends AbstractPlasticEntityBlock<ResinAnvilEntity> {
    public ResinAnvilBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(MAGNETIZED, false));
    }

    @Override
    protected EntityType<? extends ResinAnvilEntity> getPlasticEntityType() {
        return ModEntities.RESIN_ANVIL.get();
    }

    @Override
    protected ItemStack createDropStack(BlockState state) {
        ItemStack stack = new ItemStack(this);
        PlasticItemData.setMaterial(stack, "resin");
        if (state.hasProperty(MAGNETIZED) && state.getValue(MAGNETIZED)) {
            PlasticItemData.setMagnetized(stack, true);
        }
        return stack;
    }

    @Override
    protected ResinAnvilEntity createPlasticEntity(
        EntityType<? extends ResinAnvilEntity> entityType,
        ServerLevel level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        return new ResinAnvilEntity(entityType, level, position, displayState, dropStack, orientation);
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
        // 兼容方块绝不能打开铁砧菜单；树脂的释放和捕获交互由持久化实体负责。
        return InteractionResult.PASS;
    }
}
