package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeContext;
import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeManager;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.recipe.anvil.outcome.DamageAnvil;
import dev.dubhe.anvilcraft.init.recipe.ModRecipeTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Bridges entity-to-entity pot impacts into AnvilCraft's normal in-world recipe trigger. */
public final class PlasticRecipeProcessor {
    private PlasticRecipeProcessor() {
    }

    public static void process(ServerLevel level, PlasticAnvilEntity anvil, PlasticPotEntity pot) {
        InWorldRecipeManager manager = level.getRecipeManager().anvillib$getInWorldRecipeManager();
        // Recipes are authored in the vanilla orientation: the cauldron is one
        // block below the anvil. Use the pot as that canonical origin even when
        // the two entities are attached to a wall or ceiling.
        BlockPos potCell = BlockPos.containing(pot.getBoundingBox().getCenter());
        Vec3 recipeOrigin = potCell.getCenter().add(0.0D, 0.5D, 0.0D);
        InWorldRecipeContext context = new InWorldRecipeContext(level, recipeOrigin, anvil);
        manager.trigger(ModRecipeTriggers.ON_ANVIL_FALL_ON, context);
        boolean damageAnvil = context.get(DamageAnvil.DAMAGE_ANVIL);
        GiantAnvilBlock.SUPPRESS_DROPS.set(true);
        try {
            context.accept();
        } finally {
            GiantAnvilBlock.SUPPRESS_DROPS.set(false);
        }
        if (damageAnvil) {
            anvil.handleAnvilCraftRecipeDamage(BlockPos.containing(anvil.getBoundingBox().getCenter()));
        }
    }
}
