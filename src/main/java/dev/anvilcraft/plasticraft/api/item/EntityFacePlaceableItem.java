package dev.anvilcraft.plasticraft.api.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** An item that can place its entity-backed form against a synthetic solid face. */
public interface EntityFacePlaceableItem {
    InteractionResult plasticraft$placeOnEntityFace(
        Level level,
        Player player,
        InteractionHand hand,
        ItemStack stack,
        BlockHitResult hit
    );
}
