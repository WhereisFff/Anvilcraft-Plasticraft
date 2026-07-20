package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.api.tooltip.PlasticItemTooltipManager;
import dev.anvilcraft.plasticraft.entity.HardenedResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/** 带旧颜色数据读取器的固定颜色硬化树脂砧物品。 */
public class HardenedResinAnvilItem extends AbstractPlasticEntityItem<HardenedResinAnvilEntity> {
    public HardenedResinAnvilItem(
        Block block,
        Properties properties,
        Supplier<? extends EntityType<? extends HardenedResinAnvilEntity>> entityType,
        Supplier<BlockState> displayState
    ) {
        super(block, properties, entityType, displayState);
        PlasticItemTooltipManager.register(
            AnvilcraftPlasticraft.of("hardend_resin_anvil"),
            "A hardened resin anvil that functions as a complete anvil",
            """
                Pushable anvil with the vanilla anvil workflow
                Shift-use with any Anvil Hammer to retrieve it directly
                Renaming costs no experience and adds no prior-work penalty
                Creative players can Shift-use a magnet to magnetize it
                Can be placed in any direction; only impacts on its bottom face can process recipes"""
        );
    }

    @Override
    protected String materialKey() {
        return "hardened_resin";
    }

    @Override
    protected HardenedResinAnvilEntity createEntity(
        EntityType<? extends HardenedResinAnvilEntity> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        return new HardenedResinAnvilEntity(entityType, level, position, displayState, dropStack, orientation);
    }

    /** 为旧着色物品堆和数据迁移保留的兼容访问器。 */
    public static DyeColor getColor(ItemStack stack) {
        return PlasticItemData.getColor(stack);
    }

    public static void setColor(ItemStack stack, DyeColor color) {
        PlasticItemData.setColor(stack, color);
    }
}
