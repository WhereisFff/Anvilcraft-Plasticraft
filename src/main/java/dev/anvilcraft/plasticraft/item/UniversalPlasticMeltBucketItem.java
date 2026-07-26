package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.block.entity.UniversalPlasticMeltBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/** 保存单一熔体流体 ID 的颜色，并在放置后写入世界方块实体。 */
public final class UniversalPlasticMeltBucketItem extends BucketItem {
    public UniversalPlasticMeltBucketItem(Supplier<? extends Fluid> fluid, Properties properties) {
        super(fluid.get(), properties);
    }

    @Override
    public void checkExtraContent(@Nullable Player player, Level level, ItemStack stack, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof UniversalPlasticMeltBlockEntity melt) {
            melt.setColor(PlasticMeltColor.get(stack));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(
            "tooltip.anvilcraftplasticraft.color",
            Component.translatable("color.minecraft." + PlasticMeltColor.get(stack).getName())
        ).withStyle(ChatFormatting.GRAY));
    }
}
