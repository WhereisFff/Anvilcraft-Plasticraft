package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.api.item.EntityFacePlaceableItem;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.List;
import java.util.function.Supplier;

/** 所有实体化塑料制品共用的六面放置流程。 */
public abstract class AbstractPlasticEntityItem<E extends AbstractPlasticEntity> extends BlockItem
    implements EntityFacePlaceableItem {
    private static final double PLACEMENT_COLLISION_EPSILON = 1.0E-7D;

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
    public void appendHoverText(
        ItemStack stack,
        Item.TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (PlasticItemData.isMagnetized(stack)) {
            tooltip.add(Component.translatable("tooltip.anvilcraftplasticraft.magnetized").withStyle(ChatFormatting.AQUA));
        }
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
        PlasticEntityOrientation orientation = PlasticEntityOrientation.forPlacement(attachmentFace, player);

        EntityType<? extends E> type = this.entityType.get();
        if (type == null) {
            return InteractionResult.FAIL;
        }
        Vec3 position = orientation.entityPosition(occupiedPos, type.getWidth(), type.getHeight());

        ItemStack entityStack = stack.copyWithCount(1);
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

        E entity = this.createEntity(type, level, position, state, entityStack, orientation);
        entity.setMagnetized(PlasticItemData.isMagnetized(entityStack));
        AABB collisionBox = entity.getBoundingBox().deflate(PLACEMENT_COLLISION_EPSILON);
        if (!level.noCollision(entity, collisionBox)) {
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
                net.minecraft.sounds.SoundSource.BLOCKS,
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
