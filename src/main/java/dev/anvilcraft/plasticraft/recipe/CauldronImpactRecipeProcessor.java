package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.lib.v2.recipe.AnvilLibRecipe;
import dev.anvilcraft.lib.v2.recipe.InWorldRecipe;
import dev.anvilcraft.lib.v2.recipe.event.InWorldRecipeEvent;
import dev.anvilcraft.lib.v2.recipe.predicate.IRecipePredicate;
import dev.anvilcraft.lib.v2.recipe.predicate.block.HasBlockBase;
import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeContext;
import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeManager;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticCauldron;
import dev.anvilcraft.plasticraft.entity.PlasticCauldronWorkBlockFinder;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.init.recipe.ModRecipeTriggers;
import dev.dubhe.anvilcraft.recipe.anvil.outcome.DamageAnvil;
import dev.dubhe.anvilcraft.recipe.anvil.predicate.block.HasCauldron;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/** 将实体间的釜冲击接入 AnvilCraft 常规世界内配方触发器。 */
public final class CauldronImpactRecipeProcessor {
    private static final ThreadLocal<Deque<RecipeTarget>> ACTIVE_RECIPE_TARGETS = new ThreadLocal<>();
    private static final ThreadLocal<Map<InWorldRecipeContext, RecipeAttempt>> ACTIVE_RECIPE_ATTEMPTS = new ThreadLocal<>();
    private static final ThreadLocal<Deque<PlasticCauldron>> ACTIVE_HAMMER_TARGETS = new ThreadLocal<>();

    private CauldronImpactRecipeProcessor() {
    }

    public static void process(ServerLevel level, AbstractPlasticEntity anvil, PlasticCauldron pot) {
        processAtPotPosition(level, anvil, pot, pot.position());
    }

    /** 按锅在接触瞬间的位置构造配方上下文，避免高速扫掠越过方块边界后错位。 */
    public static void processAtPotPosition(
        ServerLevel level,
        AbstractPlasticEntity anvil,
        PlasticCauldron pot,
        Vec3 potPosition
    ) {
        if (!pot.tryClaimRecipeImpact(anvil)) return;
        boolean damageAnvil = runRecipeAtPotPosition(level, anvil, pot, potPosition);
        if (damageAnvil) {
            anvil.applyAnvilCraftRecipeDamage(BlockPos.containing(anvil.getBoundingBox().getCenter()));
        }
    }

    /**
     * 实体落砧以铁砧自身的精确落点格为边界，而不是按碰撞外包围盒扩散。
     * 接触锅只负责确定本次配方所在高度；同一格内的其他锅仍须各自独立执行配方。
     */
    public static void processEntityLandingImpact(
        ServerLevel level,
        AbstractPlasticEntity anvil,
        PlasticCauldron contactedPot,
        Vec3 contactedPotPosition
    ) {
        BlockPos contactedPotCell = recipePotCell(contactedPot, contactedPotPosition);
        Vec3 impactCenter = anvil.plasticraft$getRotationCenter();
        BlockPos impactCell = BlockPos.containing(
            impactCenter.x,
            contactedPotCell.getY(),
            impactCenter.z
        );
        boolean damageAnvil = false;
        for (PlasticCauldron pot : HardenedResinCauldronSupport.findRecipeTargets(level, impactCell)) {
            if (!pot.tryClaimRecipeImpact(anvil)) continue;
            Vec3 potPosition = pot == contactedPot ? contactedPotPosition : pot.position();
            damageAnvil |= runRecipeAtPotPosition(level, anvil, pot, potPosition);
        }
        if (damageAnvil) {
            anvil.applyAnvilCraftRecipeDamage(BlockPos.containing(anvil.getBoundingBox().getCenter()));
        }
    }

    /** 在本体落砧配方执行前，将落点内的所有实体锅逐个隔离加工。 */
    public static void beginEventRecipeProcessing(ServerLevel level, AnvilEvent.OnLand event) {
        PlasticCauldron hammerTarget = activeHammerTarget();
        if (hammerTarget != null && hammerTarget.level() == level && !hammerTarget.isRemoved()) {
            processEventTarget(level, event, hammerTarget);
        } else {
            for (PlasticCauldron pot : HardenedResinCauldronSupport.findRecipeTargets(
                level,
                event.getPos().below()
            )) {
                processEventTarget(level, event, pot);
            }
        }
        // 本体流程仍执行一次以处理散落物等目标，但不能再次读取任何实体锅。
        beginTargetedRecipe(null);
    }

    /** 结束本体落砧配方使用的实体锅屏蔽作用域。 */
    public static void finishEventRecipeProcessing() {
        finishTargetedRecipe();
    }

    /** 由世界内配方事件标记当前候选加工方块确实命中了一条配方。 */
    public static void markRecipeMatched(InWorldRecipeContext context) {
        Map<InWorldRecipeContext, RecipeAttempt> attempts = ACTIVE_RECIPE_ATTEMPTS.get();
        if (attempts == null) return;
        RecipeAttempt attempt = attempts.get(context);
        if (attempt != null) attempt.matched = true;
    }

    /** 绑定玩家本次实际锤击的锅，供同步发布的落砧事件只加工该实体。 */
    public static void beginHammerImpact(PlasticCauldron target) {
        Deque<PlasticCauldron> targets = ACTIVE_HAMMER_TARGETS.get();
        if (targets == null) {
            targets = new ArrayDeque<>();
            ACTIVE_HAMMER_TARGETS.set(targets);
        }
        targets.push(target);
    }

    /** 结束本次玩家锤击的目标绑定。 */
    public static void finishHammerImpact() {
        Deque<PlasticCauldron> targets = ACTIVE_HAMMER_TARGETS.get();
        if (targets == null || targets.isEmpty()) {
            throw new IllegalStateException("No active cauldron hammer target");
        }
        targets.pop();
        if (targets.isEmpty()) ACTIVE_HAMMER_TARGETS.remove();
    }

    private static @Nullable PlasticCauldron activeHammerTarget() {
        Deque<PlasticCauldron> targets = ACTIVE_HAMMER_TARGETS.get();
        return targets == null || targets.isEmpty() ? null : targets.peek();
    }

    private static void processEventTarget(ServerLevel level, AnvilEvent.OnLand event, PlasticCauldron pot) {
        if (!pot.tryClaimRecipeImpact(event.getEntity())) return;
        if (runRecipeAtPotPosition(level, event.getEntity(), pot, pot.position())) {
            event.setAnvilDamage(true);
        }
    }

    private static boolean runRecipeAtPotPosition(
        ServerLevel level,
        FallingBlockEntity anvil,
        PlasticCauldron pot,
        Vec3 potPosition
    ) {
        InWorldRecipeManager manager = level.getRecipeManager().anvillib$getInWorldRecipeManager();
        int passes = pot.plasticraft$cauldronLayout().recipePasses();
        beginTargetedRecipe(pot, null);
        try {
            for (BlockPos workBlock : PlasticCauldronWorkBlockFinder.findWorkBlockPositions(pot, potPosition)) {
                if (level.getBlockState(workBlock).isAir()) continue;
                RecipePass result = runRecipeAtPotCell(
                    level,
                    anvil,
                    pot,
                    manager,
                    workBlock.above(),
                    workBlock,
                    passes,
                    true
                );
                if (result.matched()) return result.damageAnvil();
            }
            return runRecipeAtPotCell(
                level,
                anvil,
                pot,
                manager,
                defaultRecipePotCell(pot, potPosition),
                null,
                passes,
                false
            ).damageAnvil();
        } finally {
            finishTargetedRecipe();
        }
    }

    private static RecipePass runRecipeAtPotCell(
        ServerLevel level,
        FallingBlockEntity anvil,
        PlasticCauldron pot,
        InWorldRecipeManager manager,
        BlockPos potCell,
        @Nullable BlockPos workBlock,
        int passes,
        boolean workBlockRecipesOnly
    ) {
        Vec3 recipeOrigin = potCell.getCenter().add(0.0D, 0.5D, 0.0D);
        boolean matched = false;
        boolean damageAnvil = false;
        beginTargetedRecipe(pot, potCell);
        try {
            for (int pass = 0; pass < passes; pass++) {
                CauldronState before = passes > 1 ? CauldronState.of(pot) : null;
                RecipePass result = runOneRecipePass(
                    level,
                    anvil,
                    manager,
                    recipeOrigin,
                    workBlock,
                    workBlockRecipesOnly
                );
                matched |= result.matched();
                damageAnvil |= result.damageAnvil();
                if (!result.matched() || before != null && before.matches(CauldronState.of(pot))) break;
            }
        } finally {
            finishTargetedRecipe();
        }
        return new RecipePass(matched, damageAnvil);
    }

    private static RecipePass runOneRecipePass(
        ServerLevel level,
        FallingBlockEntity anvil,
        InWorldRecipeManager manager,
        Vec3 recipeOrigin,
        @Nullable BlockPos workBlock,
        boolean workBlockRecipesOnly
    ) {
        InWorldRecipeContext context = new InWorldRecipeContext(level, recipeOrigin, anvil);
        RecipeAttempt attempt = beginRecipeAttempt(context);
        try {
            if (workBlockRecipesOnly) {
                triggerWorkBlockRecipes(manager, context, workBlock);
            } else {
                manager.trigger(ModRecipeTriggers.ON_ANVIL_FALL_ON, context);
            }
            boolean damageAnvil = context.get(DamageAnvil.DAMAGE_ANVIL);
            boolean previousSuppressDrops = GiantAnvilBlock.SUPPRESS_DROPS.get();
            GiantAnvilBlock.SUPPRESS_DROPS.set(true);
            try {
                context.accept();
            } finally {
                GiantAnvilBlock.SUPPRESS_DROPS.set(previousSuppressDrops);
            }
            return new RecipePass(attempt.matched, damageAnvil);
        } finally {
            finishRecipeAttempt(context);
        }
    }

    /**
     * 锅底候选格先只尝试实际读取该格的锅配方，避免普通物品压缩或其他方块加工在无关位置抢先匹配。
     * 保留原配方管理器的排序和效率上限，保证同类加工方块配方的优先级不变。
     */
    private static void triggerWorkBlockRecipes(
        InWorldRecipeManager manager,
        InWorldRecipeContext context,
        @Nullable BlockPos workBlock
    ) {
        if (workBlock == null) return;
        for (RecipeHolder<InWorldRecipe> holder : manager.recipeHolders.get(ModRecipeTriggers.ON_ANVIL_FALL_ON.get())) {
            InWorldRecipe recipe = holder.value();
            if (!usesWorkBlock(recipe, context, workBlock)) continue;
            boolean accepted = false;
            for (int efficiency = 0; efficiency < AnvilLibRecipe.CONFIG.inWorldRecipeMaxEfficiency; efficiency++) {
                if (efficiency >= recipe.maxEfficiency()) break;
                if (!recipe.matches(context, context.getLevel())) {
                    if (!accepted) break;
                    return;
                }
                accepted = true;
                recipe.assemble(context, context.getLevel().registryAccess());
                NeoForge.EVENT_BUS.post(new InWorldRecipeEvent(recipe.getType(), holder.id(), recipe, context));
            }
            if (accepted) return;
        }
    }

    private static boolean usesWorkBlock(
        InWorldRecipe recipe,
        InWorldRecipeContext context,
        BlockPos workBlock
    ) {
        if (!hasCauldronPredicate(recipe.nonConflicting())
            && !hasCauldronPredicate(recipe.conflicting())) {
            return false;
        }
        return hasWorkBlockPredicate(recipe.nonConflicting(), context, workBlock)
            || hasWorkBlockPredicate(recipe.conflicting(), context, workBlock);
    }

    private static boolean hasCauldronPredicate(List<IRecipePredicate<?>> predicates) {
        for (IRecipePredicate<?> predicate : predicates) {
            if (predicate instanceof HasCauldron) return true;
        }
        return false;
    }

    private static boolean hasWorkBlockPredicate(
        List<IRecipePredicate<?>> predicates,
        InWorldRecipeContext context,
        BlockPos workBlock
    ) {
        for (IRecipePredicate<?> predicate : predicates) {
            if (predicate instanceof HasBlockBase<?> hasBlock
                && BlockPos.containing(context.getPos().add(hasBlock.getOffset())).equals(workBlock)) {
                return true;
            }
        }
        return false;
    }

    private static RecipeAttempt beginRecipeAttempt(InWorldRecipeContext context) {
        Map<InWorldRecipeContext, RecipeAttempt> attempts = ACTIVE_RECIPE_ATTEMPTS.get();
        if (attempts == null) {
            attempts = new IdentityHashMap<>();
            ACTIVE_RECIPE_ATTEMPTS.set(attempts);
        }
        RecipeAttempt attempt = new RecipeAttempt();
        attempts.put(context, attempt);
        return attempt;
    }

    private static void finishRecipeAttempt(InWorldRecipeContext context) {
        Map<InWorldRecipeContext, RecipeAttempt> attempts = ACTIVE_RECIPE_ATTEMPTS.get();
        if (attempts == null) return;
        attempts.remove(context);
        if (attempts.isEmpty()) ACTIVE_RECIPE_ATTEMPTS.remove();
    }

    /** 开始一次仅允许目标锅参与物品缓存的配方作用域。 */
    public static void beginTargetedRecipe(@Nullable PlasticCauldron target) {
        beginTargetedRecipe(target, target == null ? null : recipePotCell(target));
    }

    private static void beginTargetedRecipe(@Nullable PlasticCauldron target, @Nullable BlockPos potCell) {
        Deque<RecipeTarget> targets = ACTIVE_RECIPE_TARGETS.get();
        if (targets == null) {
            targets = new ArrayDeque<>();
            ACTIVE_RECIPE_TARGETS.set(targets);
        }
        boolean managesProcessing = target != null
            && targets.stream().noneMatch(active -> active.cauldron() == target);
        targets.push(new RecipeTarget(target, potCell, managesProcessing));
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
    public static @Nullable PlasticCauldron activeRecipeTarget() {
        Deque<RecipeTarget> targets = ACTIVE_RECIPE_TARGETS.get();
        return targets == null || targets.isEmpty() ? null : targets.peek().cauldron();
    }

    /** 返回当前配方作用域锁定的锅格，避免高速接触后用实体的新位置重新推导。 */
    public static @Nullable BlockPos activeRecipeTargetCell() {
        Deque<RecipeTarget> targets = ACTIVE_RECIPE_TARGETS.get();
        return targets == null || targets.isEmpty() ? null : targets.peek().potCell();
    }

    /** 限制 AnvilLib 的空间物品缓存只读取当前落砧格对应的锅。 */
    public static boolean canAccessRecipeInventory(PlasticCauldron cauldron) {
        return !hasTargetedRecipe() || activeRecipeTarget() == cauldron;
    }

    /** 将同步执行中的实体锅配方产物直接写回发起加工的锅。 */
    public static boolean captureActiveRecipeOutput(ItemEntity item) {
        PlasticCauldron target = activeRecipeTarget();
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
    public static BlockPos recipePotCell(PlasticCauldron pot) {
        return recipePotCell(pot, pot.position());
    }

    /** 返回指定格是否可能作为该锅本次落砧配方的虚拟锅格。 */
    public static boolean isPotentialRecipePotCell(PlasticCauldron pot, BlockPos potCell) {
        for (BlockPos workBlock : PlasticCauldronWorkBlockFinder.findWorkBlockPositions(pot)) {
            if (!pot.level().getBlockState(workBlock).isAir() && workBlock.above().equals(potCell)) {
                return true;
            }
        }
        return defaultRecipePotCell(pot, pot.position()).equals(potCell);
    }

    private static BlockPos recipePotCell(PlasticCauldron pot, Vec3 potPosition) {
        BlockPos workBlock = PlasticCauldronWorkBlockFinder.findFirstNonAir(pot, potPosition);
        if (workBlock != null) {
            // 原版落砧配方的偏移固定沿世界 Y 轴，虚拟锅格必须位于实际加工方块正上方。
            return workBlock.above();
        }
        return defaultRecipePotCell(pot, potPosition);
    }

    private static BlockPos defaultRecipePotCell(PlasticCauldron pot, Vec3 potPosition) {
        Vec3 offset = potPosition.subtract(pot.position());
        var potBox = pot.getBoundingBox().move(offset);
        Vec3 potCenter = potBox.getCenter();
        return BlockPos.containing(
            potCenter.x,
            Math.nextDown(potBox.maxY),
            potCenter.z
        );
    }

    private record RecipeTarget(
        @Nullable PlasticCauldron cauldron,
        @Nullable BlockPos potCell,
        boolean managesProcessing
    ) {
    }

    private record RecipePass(boolean matched, boolean damageAnvil) {
    }

    private static final class RecipeAttempt {
        private boolean matched;
    }

    /** 锅内物品与流体的浅快照。{@link ItemStack} 与 {@link FluidStack} 都不实现结构相等，只能逐项比较。 */
    private record CauldronState(List<ItemStack> items, List<FluidStack> fluids) {
        static CauldronState of(PlasticCauldron pot) {
            IItemHandler itemHandler = pot.getItemHandler();
            List<ItemStack> items = new ArrayList<>(itemHandler.getSlots());
            for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                items.add(itemHandler.getStackInSlot(slot).copy());
            }
            IFluidHandler fluidHandler = pot.getFluidHandler();
            List<FluidStack> fluids = new ArrayList<>(fluidHandler.getTanks());
            for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
                fluids.add(fluidHandler.getFluidInTank(tank).copy());
            }
            return new CauldronState(items, fluids);
        }

        boolean matches(CauldronState other) {
            if (this.items.size() != other.items.size() || this.fluids.size() != other.fluids.size()) return false;
            for (int index = 0; index < this.items.size(); index++) {
                ItemStack left = this.items.get(index);
                ItemStack right = other.items.get(index);
                if (left.getCount() != right.getCount() || !ItemStack.isSameItemSameComponents(left, right)) {
                    return false;
                }
            }
            for (int index = 0; index < this.fluids.size(); index++) {
                FluidStack left = this.fluids.get(index);
                FluidStack right = other.fluids.get(index);
                if (left.getAmount() != right.getAmount() || !FluidStack.isSameFluidSameComponents(left, right)) {
                    return false;
                }
            }
            return true;
        }
    }
}
