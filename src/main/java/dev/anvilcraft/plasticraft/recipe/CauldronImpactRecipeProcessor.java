package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeContext;
import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeManager;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.recipe.anvil.outcome.DamageAnvil;
import dev.dubhe.anvilcraft.init.recipe.ModRecipeTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** 将实体间的釜冲击接入 AnvilCraft 常规世界内配方触发器。 */
public final class CauldronImpactRecipeProcessor {
    private CauldronImpactRecipeProcessor() {
    }

    public static void process(ServerLevel level, AbstractPlasticEntity anvil, HardenedResinCauldronEntity pot) {
        InWorldRecipeManager manager = level.getRecipeManager().anvillib$getInWorldRecipeManager();
        // 配方按原版朝向编写，即釜位于砧下方一格。
        // 即使两个实体附着在墙面或天花板上，也以釜作为该标准原点。
        BlockPos potCell = BlockPos.containing(pot.getBoundingBox().getCenter());
        Vec3 recipeOrigin = potCell.getCenter().add(0.0D, 0.5D, 0.0D);
        InWorldRecipeContext context = new InWorldRecipeContext(level, recipeOrigin, anvil);
        pot.beginRecipeProcessing();
        boolean damageAnvil;
        try {
            manager.trigger(ModRecipeTriggers.ON_ANVIL_FALL_ON, context);
            damageAnvil = context.get(DamageAnvil.DAMAGE_ANVIL);
            GiantAnvilBlock.SUPPRESS_DROPS.set(true);
            try {
                context.accept();
            } finally {
                GiantAnvilBlock.SUPPRESS_DROPS.set(false);
            }
        } finally {
            pot.finishRecipeProcessing();
        }
        if (damageAnvil) {
            anvil.applyAnvilCraftRecipeDamage(BlockPos.containing(anvil.getBoundingBox().getCenter()));
        }
    }
}
