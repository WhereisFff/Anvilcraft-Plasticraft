package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.dubhe.anvilcraft.block.item.HasMobBlockItem;
import dev.dubhe.anvilcraft.block.item.ResinBlockItem;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/** 取消体型上限、但保留敌对生物虚弱要求的树脂捕获物品。 */
public class HighViscosityResinBlockItem extends ResinBlockItem {
    private static final DefaultDispenseItemBehavior DEFAULT_DISPENSE = new DefaultDispenseItemBehavior();

    public HighViscosityResinBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static InteractionResult useEntity(Player player, Entity target, ItemStack stack) {
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

        Mob target = source.level().getEntitiesOfClass(
            Mob.class,
            new AABB(targetPos),
            mob -> canMobBeSaved(mob, null, stack)
        ).stream().findFirst().orElse(null);
        if (target == null) return DEFAULT_DISPENSE.dispense(source, stack);

        ItemStack captured = HasMobBlockItem.saveMobInItem(source.level(), target, stack);
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
