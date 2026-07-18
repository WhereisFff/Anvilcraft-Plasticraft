package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.entity.PlasticAnvilEntity;
import dev.anvilcraft.plasticraft.block.PlasticAnvilBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
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

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Places a solid plastic anvil entity instead of a falling/block instance. */
public class PlasticAnvilItem extends BlockItem {
    private static final java.util.function.Predicate<Entity> PLACEMENT_ENTITY_PREDICATE =
        EntitySelector.NO_SPECTATORS.and(Entity::isPickable);

    private final Supplier<? extends EntityType<? extends PlasticAnvilEntity>> entityType;
    private final Supplier<BlockState> displayState;

    public PlasticAnvilItem(
        Block block,
        Properties properties,
        Supplier<? extends EntityType<? extends PlasticAnvilEntity>> entityType,
        Supplier<BlockState> displayState
    ) {
        super(block, properties);
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.displayState = Objects.requireNonNull(displayState, "displayState");
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = context.getLevel().getBlockState(clickedPos);
        BlockPos placementPos = clickedState.canBeReplaced()
            ? clickedPos
            : clickedPos.relative(context.getClickedFace());
        return this.placeEntity(
            context.getLevel(),
            context.getPlayer(),
            context.getHand(),
            context.getItemInHand(),
            placementPos,
            context.getPlayer() == null
                ? Direction.NORTH
                : context.getPlayer().getDirection().getOpposite()
        );
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(stack);
        }
        BlockPos clickedPos = blockHit.getBlockPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        BlockPos placementPos = clickedState.canBeReplaced()
            ? clickedPos
            : clickedPos.relative(blockHit.getDirection());
        InteractionResult result = this.placeEntity(
            level,
            player,
            hand,
            stack,
            placementPos,
            player.getDirection().getOpposite()
        );
        return result.consumesAction()
            ? InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
            : InteractionResultHolder.fail(stack);
    }

    private InteractionResult placeEntity(
        Level level,
        Player player,
        InteractionHand hand,
        ItemStack stack,
        BlockPos placementPos,
        Direction facing
    ) {
        Vec3 position = placementPos.getBottomCenter();
        BlockState state = displayState.get();
        if (state == null) state = Blocks.ANVIL.defaultBlockState();
        state = orient(PlasticAnvilBlock.withColor(state, getColor(stack)), facing);
        EntityType<? extends PlasticAnvilEntity> type = entityType.get();
        if (type == null) return InteractionResult.FAIL;

        PlasticAnvilEntity entity = new PlasticAnvilEntity(type, level, position, state, stack);
        entity.setYRot(player == null ? 0.0F : player.getYRot());
        AABB box = entity.getBoundingBox();
        List<Entity> overlapping = level.getEntities(
            entity,
            box.inflate(1.0E-4D),
            PLACEMENT_ENTITY_PREDICATE
        );
        if (!overlapping.isEmpty() || !level.noCollision(entity, box)) return InteractionResult.FAIL;

        if (!level.isClientSide) {
            level.addFreshEntity(entity);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, position);
            if (player != null) stack.consume(1, player);
        }
        if (player != null) player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static BlockState orient(BlockState state, Direction facing) {
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
            return state.setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                facing
            );
        }
        return state;
    }

    /** Reads the future-proof color component used by additional plastic variants. */
    public static DyeColor getColor(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains("PlasticColor")) return DyeColor.WHITE;
        return DyeColor.byName(data.copyTag().getString("PlasticColor"), DyeColor.WHITE);
    }

    public static void setColor(ItemStack stack, DyeColor color) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("PlasticColor", color.getName()));
    }
}
