package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberStructure;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockEntities;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPowerBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** 在消耗物品前一次性提交控制器和全部 27 个区域部件。 */
public class PlasticMoldingChamberItem extends BlockItem {
    private static final String TAG_ENERGY = "Energy";

    public PlasticMoldingChamberItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack setStoredEnergy(ItemStack stack, int energy) {
        CompoundTag tag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        int stored = Math.clamp(energy, 0, MoldingPowerBridge.capacity());
        if (stored == 0) {
            tag.remove(TAG_ENERGY);
        } else {
            tag.putInt(TAG_ENERGY, stored);
        }
        BlockItem.setBlockEntityData(stack, PlasticraftBlockEntities.PLASTIC_MOLDING_CHAMBER.get(), tag);
        return stack;
    }

    public static int storedEnergy(ItemStack stack) {
        CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) return 0;
        return Math.clamp(data.copyTag().getInt(TAG_ENERGY), 0, MoldingPowerBridge.capacity());
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return PlasticMoldingChamberStructure.placeAtomically(
            context.getLevel(),
            context.getClickedPos(),
            state
        );
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        Item.TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.anvilcraftplasticraft.plastic_molding_chamber").withStyle(ChatFormatting.GRAY));
    }
}
