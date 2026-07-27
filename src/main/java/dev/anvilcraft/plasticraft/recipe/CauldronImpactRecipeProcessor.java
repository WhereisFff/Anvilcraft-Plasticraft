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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** 将实体间的釜冲击接入 AnvilCraft 常规世界内配方触发器。 */
public final class CauldronImpactRecipeProcessor {
    private static final ThreadLocal<HardenedResinCauldronEntity> ACTIVE_OUTPUT_TARGET = new ThreadLocal<>();

    private CauldronImpactRecipeProcessor() {
    }

    public static void process(ServerLevel level, AbstractPlasticEntity anvil, HardenedResinCauldronEntity pot) {
        processAtPotPosition(level, anvil, pot, pot.position());
    }

    /** 按锅在接触瞬间的位置构造配方上下文，避免高速扫掠越过方块边界后错位。 */
    public static void processAtPotPosition(
        ServerLevel level,
        AbstractPlasticEntity anvil,
        HardenedResinCauldronEntity pot,
        Vec3 potPosition
    ) {
        InWorldRecipeManager manager = level.getRecipeManager().anvillib$getInWorldRecipeManager();
        // 配方按原版朝向编写，即釜位于砧下方一格。
        // 即使两个实体附着在墙面或天花板上，也以釜作为该标准原点。
        BlockPos potCell = recipePotCell(pot, potPosition);
        Vec3 recipeOrigin = potCell.getCenter().add(0.0D, 0.5D, 0.0D);
        InWorldRecipeContext context = new InWorldRecipeContext(level, recipeOrigin, anvil);
        HardenedResinCauldronEntity previousTarget = ACTIVE_OUTPUT_TARGET.get();
        ACTIVE_OUTPUT_TARGET.set(pot);
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
            if (previousTarget == null) {
                ACTIVE_OUTPUT_TARGET.remove();
            } else {
                ACTIVE_OUTPUT_TARGET.set(previousTarget);
            }
        }
        if (damageAnvil) {
            anvil.applyAnvilCraftRecipeDamage(BlockPos.containing(anvil.getBoundingBox().getCenter()));
        }
    }

    /** 将同步执行中的实体锅配方产物直接写回发起加工的锅。 */
    public static boolean captureActiveRecipeOutput(ItemEntity item) {
        HardenedResinCauldronEntity target = ACTIVE_OUTPUT_TARGET.get();
        if (target == null || target.isRemoved() || target.level() != item.level()) return false;
        ItemStack remaining = target.insertRecipeOutput(item.getItem());
        if (remaining.isEmpty()) {
            item.discard();
        } else {
            item.setItem(remaining);
        }
        return true;
    }

    /** 返回原版落砧配方用于定位该实体锅的方块格。 */
    public static BlockPos recipePotCell(HardenedResinCauldronEntity pot) {
        return recipePotCell(pot, pot.position());
    }

    private static BlockPos recipePotCell(HardenedResinCauldronEntity pot, Vec3 potPosition) {
        Vec3 offset = potPosition.subtract(pot.position());
        var potBox = pot.getBoundingBox().move(offset);
        Vec3 potCenter = potBox.getCenter();
        // 矮工作方块会让锅的中心仍处于其方块格内，以上沿所在格才代表配方中的锅位置。
        return BlockPos.containing(
            potCenter.x,
            Math.nextDown(potBox.maxY),
            potCenter.z
        );
    }
}
