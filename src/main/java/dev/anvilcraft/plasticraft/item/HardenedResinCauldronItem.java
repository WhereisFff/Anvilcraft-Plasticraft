package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.api.tooltip.PlasticItemTooltipManager;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/** 用于放置硬化树脂釜的固定颜色物品。 */
public class HardenedResinCauldronItem extends AbstractPlasticEntityItem<HardenedResinCauldronEntity> {
    public HardenedResinCauldronItem(
        Block block,
        Properties properties,
        Supplier<? extends EntityType<? extends HardenedResinCauldronEntity>> entityType,
        Supplier<BlockState> displayState
    ) {
        super(block, properties, entityType, displayState);
        PlasticItemTooltipManager.register(
            AnvilcraftPlasticraft.of("hardend_resin_cauldron"),
            "A light, portable cauldron assembled from hardened resin plates",
            """
                Pushable cauldron for items and up to 1000 mB of fluid
                Shift-use with any Anvil Hammer to retrieve it and its stored items
                Connects to pipe heads, pumps, and control valves from any side
                Creative players can Shift-use a magnet to magnetize it
                Can be placed in any direction; when it does not face up, it looks like the fluid will spill"""
        );
    }

    @Override
    protected String materialKey() {
        return "hardened_resin";
    }

    @Override
    protected HardenedResinCauldronEntity createEntity(
        EntityType<? extends HardenedResinCauldronEntity> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        return new HardenedResinCauldronEntity(entityType, level, position, displayState, dropStack, orientation);
    }
}
