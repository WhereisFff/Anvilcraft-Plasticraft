package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.api.item.EntityFacePlaceableItem;
import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.function.Supplier;

/** 所有实体化塑料制品共用的六面放置流程。 */
public abstract class AbstractPlasticEntityItem<E extends AbstractPlasticEntity> extends BlockItem
    implements EntityFacePlaceableItem {
    private final Supplier<? extends EntityType<? extends E>> entityType;
    private final Supplier<BlockState> displayState;

    protected AbstractPlasticEntityItem(
        Block block,
        Properties properties,
        Supplier<? extends EntityType<? extends E>> entityType,
        Supplier<BlockState> displayState
    ) {
        super(block, properties);
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.displayState = Objects.requireNonNull(displayState, "displayState");
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockHitResult hit = new BlockHitResult(
            context.getClickLocation(),
            context.getClickedFace(),
            context.getClickedPos(),
            false
        );
        return this.placeFromHit(context.getLevel(), context.getPlayer(), context.getItemInHand(), hit);
    }

    @Override
    public final InteractionResult plasticraft$placeOnEntityFace(
        Level level,
        Player player,
        InteractionHand hand,
        ItemStack stack,
        BlockHitResult hit
    ) {
        return this.placeFromHit(level, player, stack, hit);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(stack);
        }
        InteractionResult result = this.placeFromHit(level, player, stack, blockHit);
        return result.consumesAction()
            ? InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
            : InteractionResultHolder.fail(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // 磁性变体拥有明确模型，不要在该模型上叠加原版附魔光效。
        // 附魔物品堆仍通过父类使用常规物品行为。
        return super.isFoil(stack);
    }

    private InteractionResult placeFromHit(
        Level level,
        Player player,
        ItemStack stack,
        BlockHitResult hit
    ) {
        BlockPos clickedPos = hit.getBlockPos();
        Direction attachmentFace = hit.getDirection();
        BlockPos occupiedPos = level.getBlockState(clickedPos).canBeReplaced()
            ? clickedPos
            : clickedPos.relative(attachmentFace);
        PlasticEntityOrientation orientation = this.placementOrientation(stack, hit, player);

        EntityType<? extends E> type = this.entityType.get();
        if (type == null) {
            return InteractionResult.FAIL;
        }
        Vec3 provisionalPosition = Vec3.atBottomCenterOf(occupiedPos);

        ItemStack entityStack = this.prepareEntityStack(stack.copyWithCount(1), orientation);
        PlasticItemData.setMaterial(entityStack, this.materialKey());
        if (!this.supportsDyeing()) {
            PlasticItemData.clearColor(entityStack);
        }

        BlockState state = this.displayState.get();
        if (state == null) {
            state = Blocks.ANVIL.defaultBlockState();
        }
        state = this.prepareDisplayState(entityStack, state);
        if (state.hasProperty(AbstractPlasticEntityBlock.MAGNETIZED)) {
            state = state.setValue(
                AbstractPlasticEntityBlock.MAGNETIZED,
                PlasticItemData.isMagnetized(entityStack)
            );
        }
        state = orientation.applyToState(state);

        E entity = this.createEntity(type, level, provisionalPosition, state, entityStack, orientation);
        Vec3 position = entity.plasticraft$placementPosition(occupiedPos, orientation);
        entity.setPos(position);
        entity.setStartPos(entity.blockPosition());
        entity.setMagnetized(PlasticItemData.isMagnetized(entityStack));
        if (!entity.plasticraft$canOccupy(orientation, position)) {
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            if (!level.addFreshEntity(entity)) {
                return InteractionResult.FAIL;
            }
            level.playSound(
                null,
                BlockPos.containing(position),
                this.placementSound(state),
                SoundSource.BLOCKS,
                0.72F,
                1.08F + level.getRandom().nextFloat() * 0.12F
            );
            level.gameEvent(player, GameEvent.ENTITY_PLACE, position);
            if (player != null) {
                stack.consume(1, player);
            }
        }
        if (player != null) {
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    protected SoundEvent placementSound(BlockState displayState) {
        return SoundEvents.BONE_BLOCK_PLACE;
    }

    protected BlockState prepareDisplayState(ItemStack stack, BlockState state) {
        return state;
    }

    /** 允许尺寸不是完整立方体的制品约束放置朝向，确保实体包围盒和局部碰撞保持一致。 */
    protected PlasticEntityOrientation placementOrientation(BlockHitResult hit, Player player) {
        return PlasticEntityOrientation.forPlacement(hit, player);
    }

    /** 允许带姿态组件的制品在选择放置面后保留面内旋转。 */
    protected PlasticEntityOrientation placementOrientation(
        ItemStack stack,
        BlockHitResult hit,
        Player player
    ) {
        return this.placementOrientation(hit, player);
    }

    protected ItemStack prepareEntityStack(ItemStack stack, PlasticEntityOrientation orientation) {
        return stack;
    }

    /** 仅含旧颜色数据的物品堆转换为实体时写入的稳定材料键。 */
    protected abstract String materialKey();

    /** 后续支持调色板的材料在此启用；树脂制品保持固定颜色。 */
    protected boolean supportsDyeing() {
        return DyeableMaterial.supportsDyeing(this.materialKey());
    }

    protected abstract E createEntity(
        EntityType<? extends E> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    );
}
