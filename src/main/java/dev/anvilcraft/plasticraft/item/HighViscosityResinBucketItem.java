package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveSelectionManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

import java.util.function.Supplier;

/** 在选中实体期间禁止退回普通流体放置行为的高粘性树脂桶。 */
public class HighViscosityResinBucketItem extends BucketItem {
    public HighViscosityResinBucketItem(Supplier<? extends Fluid> fluid, Properties properties) {
        super(fluid.get(), properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (AdhesiveSelectionManager.hasSelection(player)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }
}
