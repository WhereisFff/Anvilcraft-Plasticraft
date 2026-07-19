package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.api.item.EntityFacePlaceableItem;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticAnvilEntity;
import dev.anvilcraft.plasticraft.entity.PlasticAnvilOrientation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/** Shared six-face placement flow for every entity-backed plastic anvil item. */
public abstract class AbstractPlasticAnvilItem<E extends AbstractPlasticAnvilEntity> extends BlockItem
    implements EntityFacePlaceableItem {
    private static final double PLACEMENT_COLLISION_EPSILON = 1.0E-7D;

    private final Supplier<? extends EntityType<? extends E>> entityType;
    private final Supplier<BlockState> displayState;

    protected AbstractPlasticAnvilItem(
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
        return PlasticItemData.isMagnetized(stack) || super.isFoil(stack);
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
        PlasticAnvilOrientation orientation = PlasticAnvilOrientation.forPlacement(attachmentFace, player);

        EntityType<? extends E> type = this.entityType.get();
        if (type == null) {
            return InteractionResult.FAIL;
        }
        Vec3 position = orientation.entityPosition(occupiedPos, type.getWidth(), type.getHeight());

        BlockState state = this.displayState.get();
        if (state == null) {
            state = Blocks.ANVIL.defaultBlockState();
        }
        state = orientation.applyToState(this.prepareDisplayState(stack, state));

        E entity = this.createEntity(type, level, position, state, stack, orientation);
        entity.setMagnetized(PlasticItemData.isMagnetized(stack));
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
                net.minecraft.sounds.SoundEvents.BONE_BLOCK_PLACE,
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

    protected BlockState prepareDisplayState(ItemStack stack, BlockState state) {
        return state;
    }

    protected abstract E createEntity(
        EntityType<? extends E> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticAnvilOrientation orientation
    );
}
