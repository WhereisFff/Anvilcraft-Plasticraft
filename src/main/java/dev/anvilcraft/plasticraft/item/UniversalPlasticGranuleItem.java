package dev.anvilcraft.plasticraft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** 通过自定义数据从单一物品 ID 中选择十六种染料颜色。 */
public class UniversalPlasticGranuleItem extends Item {
    public UniversalPlasticGranuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(
            "tooltip.anvilcraftplasticraft.color",
            Component.translatable("color.minecraft." + PlasticMeltColor.get(stack).getName())
        ).withStyle(ChatFormatting.GRAY));
    }
}
