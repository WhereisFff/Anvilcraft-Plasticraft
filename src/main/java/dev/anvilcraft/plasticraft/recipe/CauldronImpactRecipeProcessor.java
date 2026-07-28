package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeContext;
import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeManager;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.init.recipe.ModRecipeTriggers;
import dev.dubhe.anvilcraft.recipe.anvil.outcome.DamageAnvil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

import javax.annotation.Nullable;

/** 将实体间的釜冲击接入 AnvilCraft 常规世界内配方触发器。 */
public final class CauldronImpactRecipeProcessor {
    private static final ThreadLocal<Deque<RecipeTarget>> ACTIVE_RECIPE_TARGETS = new ThreadLocal<>();

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
        if (!pot.tryClaimRecipeImpact(anvil)) return;
        boolean damageAnvil = runRecipeAtPotPosition(level, anvil, pot, potPosition);
        if (damageAnvil) {
            anvil.applyAnvilCraftRecipeDamage(BlockPos.containing(anvil.getBoundingBox().getCenter()));
        }
    }

    /** 在本体落砧配方执行前，将落点内的所有实体锅逐个隔离加工。 */
    public static void beginEventRecipeProcessing(ServerLevel level, AnvilEvent.OnLand event) {
        for (HardenedResinCauldronEntity pot : HardenedResinCauldronSupport.findRecipeTargets(
            level,
            event.getPos().below()
        )) {
            if (!pot.tryClaimRecipeImpact(event.getEntity())) continue;
            if (runRecipeAtPotPosition(level, event.getEntity(), pot, pot.position())) {
                event.setAnvilDamage(true);
            }
        }
        // 本体流程仍执行一次以处理散落物等目标，但不能再次读取任何实体锅。
        beginTargetedRecipe(null);
    }

    /** 结束本体落砧配方使用的实体锅屏蔽作用域。 */
    public static void finishEventRecipeProcessing() {
        finishTargetedRecipe();
    }

    private static boolean runRecipeAtPotPosition(
        ServerLevel level,
        FallingBlockEntity anvil,
        HardenedResinCauldronEntity pot,
        Vec3 potPosition
    ) {
        InWorldRecipeManager manager = level.getRecipeManager().anvillib$getInWorldRecipeManager();
        // 配方按原版朝向编写，即釜位于砧下方一格。
        // 即使两个实体附着在墙面或天花板上，也以釜作为该标准原点。
        BlockPos potCell = recipePotCell(pot, potPosition);
        Vec3 recipeOrigin = potCell.getCenter().add(0.0D, 0.5D, 0.0D);
        InWorldRecipeContext context = new InWorldRecipeContext(level, recipeOrigin, anvil);
        beginTargetedRecipe(pot);
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
            finishTargetedRecipe();
        }
        return damageAnvil;
    }

    /** 开始一次仅允许目标锅参与物品缓存的配方作用域。 */
    public static void beginTargetedRecipe(@Nullable HardenedResinCauldronEntity target) {
        Deque<RecipeTarget> targets = ACTIVE_RECIPE_TARGETS.get();
        if (targets == null) {
            targets = new ArrayDeque<>();
            ACTIVE_RECIPE_TARGETS.set(targets);
        }
        boolean managesProcessing = target != null
            && targets.stream().noneMatch(active -> active.cauldron() == target);
        targets.push(new RecipeTarget(target, managesProcessing));
        if (managesProcessing) target.beginRecipeProcessing();
    }

    /** 结束当前目标锅配方作用域。 */
    public static void finishTargetedRecipe() {
        Deque<RecipeTarget> targets = ACTIVE_RECIPE_TARGETS.get();
        if (targets == null || targets.isEmpty()) {
            throw new IllegalStateException("No active cauldron recipe target");
        }
        RecipeTarget target = targets.pop();
        if (target.managesProcessing()) target.cauldron().finishRecipeProcessing();
        if (targets.isEmpty()) ACTIVE_RECIPE_TARGETS.remove();
    }

    /** 返回当前线程是否正按单个锅隔离配方缓存。 */
    public static boolean hasTargetedRecipe() {
        Deque<RecipeTarget> targets = ACTIVE_RECIPE_TARGETS.get();
        return targets != null && !targets.isEmpty();
    }

    /** 返回当前配方作用域绑定的锅；空目标表示本次落砧格没有实体锅。 */
    public static @Nullable HardenedResinCauldronEntity activeRecipeTarget() {
        Deque<RecipeTarget> targets = ACTIVE_RECIPE_TARGETS.get();
        return targets == null || targets.isEmpty() ? null : targets.peek().cauldron();
    }

    /** 限制 AnvilLib 的空间物品缓存只读取当前落砧格对应的锅。 */
    public static boolean canAccessRecipeInventory(HardenedResinCauldronEntity cauldron) {
        return !hasTargetedRecipe() || activeRecipeTarget() == cauldron;
    }

    /** 将同步执行中的实体锅配方产物直接写回发起加工的锅。 */
    public static boolean captureActiveRecipeOutput(ItemEntity item) {
        HardenedResinCauldronEntity target = activeRecipeTarget();
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

    private record RecipeTarget(
        @Nullable HardenedResinCauldronEntity cauldron,
        boolean managesProcessing
    ) {
    }
}
