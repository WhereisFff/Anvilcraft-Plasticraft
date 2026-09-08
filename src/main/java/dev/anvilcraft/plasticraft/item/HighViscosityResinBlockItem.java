package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.dubhe.anvilcraft.block.item.HasMobBlockItem;
import dev.dubhe.anvilcraft.block.item.ResinBlockItem;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.item.property.component.SavedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** 取消体型上限、但保留敌对生物虚弱要求的树脂捕获物品。 */
public class HighViscosityResinBlockItem extends ResinBlockItem {
    private static final DefaultDispenseItemBehavior DEFAULT_DISPENSE = new DefaultDispenseItemBehavior();

    public HighViscosityResinBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (captureLookedAtSkull(player, stack).consumesAction()) {
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }
        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null) {
            InteractionResult result = captureLookedAtSkull(player, context.getItemInHand());
            if (result.consumesAction()) return result;
        }
        return super.useOn(context);
    }

    private static InteractionResult captureLookedAtSkull(Player player, ItemStack stack) {
        if (stack.has(ModComponents.SAVED_ENTITY)) return InteractionResult.PASS;
        Vec3 start = player.getEyePosition();
        Vec3 end = player.level().clip(new ClipContext(
            start, start.add(player.getLookAngle().scale(player.entityInteractionRange())),
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player
        )).getLocation();
        // 凋零之首不参与原版准星选取，单独射线检查，并以最近方块截断避免隔墙捕获。
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
            player.level(), player, start, end, new AABB(start, end).inflate(1.0D),
            entity -> entity instanceof WitherSkull && entity.isAlive(), 0.0F
        );
        return hit == null ? InteractionResult.PASS : useEntity(player, hit.getEntity(), stack);
    }

    public static InteractionResult useEntity(Player player, Entity target, ItemStack stack) {
        if (target instanceof WitherSkull skull) {
            if (stack.isEmpty() || stack.has(ModComponents.SAVED_ENTITY) || skull.isRemoved()) {
                return InteractionResult.PASS;
            }
            if (!player.level().isClientSide()) {
                player.getInventory().placeItemBackInInventory(captureSkull(skull, stack));
            }
            return InteractionResult.SUCCESS;
        }
        if (!(target instanceof Mob mob) || !canMobBeSaved(mob, player, stack)) {
            return InteractionResult.PASS;
        }
        HasMobBlockItem.saveMobInItem(player.level(), mob, player, stack);
        return InteractionResult.SUCCESS;
    }

    public static boolean canMobBeSaved(Mob mob, @Nullable Player player, @Nullable ItemStack stack) {
        if (mob instanceof WorkingAllayEntity) return false;
        if (player != null && player.getAbilities().instabuild) return true;
        if (stack != null && stack.has(ModComponents.SAVED_ENTITY)) return false;
        return !(mob instanceof Monster monster && !monster.hasEffect(MobEffects.WEAKNESS));
    }

    public static ItemStack captureSkull(WitherSkull skull, ItemStack stack) {
        if (skull.level().isClientSide() || skull.isRemoved() || stack.isEmpty()
            || stack.has(ModComponents.SAVED_ENTITY)) return ItemStack.EMPTY;
        CompoundTag tag = new CompoundTag();
        if (!skull.saveAsPassenger(tag)) return ItemStack.EMPTY;
        tag.remove(Entity.UUID_TAG);
        ItemStack captured = stack.split(1);
        captured.set(ModComponents.SAVED_ENTITY, new SavedEntity(tag, false));
        skull.discard();
        return captured;
    }

    public static ItemStack dispense(BlockSource source, ItemStack stack) {
        Direction facing = source.state().getValue(DispenserBlock.FACING);
        BlockPos targetPos = source.pos().relative(facing);
        if (HasMobBlockItem.hasMob(stack)) {
            ItemStack resin = ResinBlockItem.spawnMobFromItem(source.level(), targetPos, stack);
            if (!resin.isEmpty()) {
                DefaultDispenseItemBehavior.spawnItem(
                    source.level(),
                    resin,
                    6,
                    facing,
                    DispenserBlock.getDispensePosition(source)
                );
            }
            return stack;
        }

        Entity target = source.level().getEntitiesOfClass(
            Entity.class,
            new AABB(targetPos),
            entity -> entity instanceof WitherSkull
                || entity instanceof Mob mob && canMobBeSaved(mob, null, stack)
        ).stream().findFirst().orElse(null);
        if (target == null) return DEFAULT_DISPENSE.dispense(source, stack);

        ItemStack captured = target instanceof WitherSkull skull
            ? captureSkull(skull, stack)
            : HasMobBlockItem.saveMobInItem(source.level(), (Mob) target, stack);
        if (stack.isEmpty()) return captured;
        ItemStack remaining = source.blockEntity().insertItem(captured);
        if (!remaining.isEmpty()) {
            DefaultDispenseItemBehavior.spawnItem(
                source.level(),
                remaining,
                6,
                facing,
                DispenserBlock.getDispensePosition(source)
            );
        }
        return stack;
    }
}
