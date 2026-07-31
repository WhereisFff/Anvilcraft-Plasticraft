package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.ResinAnvilEntity;
import dev.dubhe.anvilcraft.block.item.HasMobBlockItem;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.mixin.accessor.BaseSpawnerAccessor;
import dev.dubhe.anvilcraft.util.EntityUtil;
import dev.dubhe.anvilcraft.util.ResentmentUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/** 遵循 AnvilCraft 已保存生物组件约定的固定颜色树脂砧物品。 */
public class ResinAnvilItem extends AbstractPlasticEntityItem<ResinAnvilEntity> {
    private static final DefaultDispenseItemBehavior DEFAULT_DISPENSE = new DefaultDispenseItemBehavior();

    public ResinAnvilItem(
        Block block,
        Properties properties,
        Supplier<? extends EntityType<? extends ResinAnvilEntity>> entityType,
        Supplier<BlockState> displayState
    ) {
        super(block, properties, entityType, displayState);
    }

    @Override
    protected String materialKey() {
        return "resin";
    }

    @Override
    protected SoundEvent placementSound(BlockState displayState) {
        return ModBlocks.RESIN_BLOCK.getDefaultState().getSoundType().getPlaceSound();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!HasMobBlockItem.hasMob(stack)
            && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof SpawnerBlockEntity spawner
        ) {
            return captureSpawner(context, spawner);
        }
        if (!HasMobBlockItem.hasMob(stack)) return super.useOn(context);

        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        // 潜行时放置捕获了生物的砧实体；对已放置实体普通空手使用会释放生物，
        // 与树脂块的流程一致。
        if (player.isShiftKeyDown()) return super.useOn(context);
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;

        BlockPos targetPos = context.getClickedPos().relative(context.getClickedFace());
        return releaseCapturedMob(context.getLevel(), targetPos, stack)
            ? InteractionResult.CONSUME
            : InteractionResult.FAIL;
    }

    private static InteractionResult captureSpawner(UseOnContext context, SpawnerBlockEntity blockEntity) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockPos pos = context.getClickedPos();
        BaseSpawner spawner = blockEntity.getSpawner();
        BaseSpawnerAccessor accessor = (BaseSpawnerAccessor) spawner;
        SpawnData spawnData = accessor.invokeGetOrCreateNextSpawnData(level, level.getRandom(), pos);
        CompoundTag entityTag = spawnData.getEntityToSpawn().copy();
        Entity entity = EntityType.loadEntityRecursive(entityTag, level, it -> it);
        if (!(entity instanceof Mob mob)) return InteractionResult.FAIL;

        ResentmentUtil.setForcedResentment(mob, 100);
        HasMobBlockItem.saveMobInItem(level, mob, player, context.getItemInHand());
        accessor.invokeSetNextSpawnData(level, pos, new SpawnData());
        accessor.setSpawnPotentials(SimpleWeightedRandomList.empty());
        accessor.setDisplayEntity(null);
        blockEntity.setChanged();
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        return InteractionResult.CONSUME;
    }

    private static boolean releaseCapturedMob(Level level, BlockPos pos, ItemStack stack) {
        Entity entity = HasMobBlockItem.getMobFromItem(level, stack);
        if (!(entity instanceof Mob mob)) return false;
        mob.moveTo(pos.getCenter());
        if (!level.noCollision(mob, mob.getBoundingBox().inflate(0.02D))) return false;
        if (!level.addFreshEntity(mob)) return false;
        stack.remove(ModComponents.SAVED_ENTITY);
        return true;
    }

    /** 复用树脂块的自动捕获和释放逻辑，同时保留砧物品。 */
    public static ItemStack dispense(BlockSource source, ItemStack stack) {
        Direction facing = source.state().getValue(DispenserBlock.FACING);
        BlockPos targetPos = source.pos().relative(facing);
        Level level = source.level();
        if (HasMobBlockItem.hasMob(stack)) {
            ItemStack captured = stack.split(1);
            if (!releaseCapturedMob(level, targetPos, captured)) {
                stack.grow(1);
                return stack;
            }
            return putBackOrDispense(source, stack, captured, facing);
        }

        Mob mob = EntityUtil.getAnyEntityOfClass(
            level,
            Mob.class,
            new AABB(targetPos),
            HasMobBlockItem::canMobBeSaved
        );
        if (mob == null) return DEFAULT_DISPENSE.dispense(source, stack);

        ItemStack captured = HasMobBlockItem.saveMobInItem(level, mob, stack);
        return putBackOrDispense(source, stack, captured, facing);
    }

    private static ItemStack putBackOrDispense(
        BlockSource source,
        ItemStack remaining,
        ItemStack returned,
        Direction facing
    ) {
        if (remaining.isEmpty()) return returned;
        ItemStack overflow = source.blockEntity().insertItem(returned);
        if (!overflow.isEmpty()) {
            DefaultDispenseItemBehavior.spawnItem(
                source.level(),
                overflow,
                6,
                facing,
                DispenserBlock.getDispensePosition(source)
            );
        }
        return remaining;
    }

    @Override
    protected ResinAnvilEntity createEntity(
        EntityType<? extends ResinAnvilEntity> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        return new ResinAnvilEntity(entityType, level, position, displayState, dropStack, orientation);
    }

    @Override
    public InteractionResult interactLivingEntity(
        ItemStack stack,
        Player player,
        LivingEntity target,
        InteractionHand hand
    ) {
        if (!(target instanceof Mob mob) || !HasMobBlockItem.canMobBeSaved(mob, player, stack)) {
            return InteractionResult.PASS;
        }
        HasMobBlockItem.saveMobInItem(player.level(), mob, player, stack);
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

}
