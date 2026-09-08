package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeContext;
import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeData;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.MoldedLargeCauldronInteraction;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.dubhe.anvilcraft.api.fluid.LargeCauldronFluidHandler;
import dev.dubhe.anvilcraft.recipe.anvil.predicate.block.HasCauldron;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** 实体大锅的分层流体事务，保持与本体大锅相同的预检、快照、回滚和最终提交顺序。 */
public final class MoldedLargeCauldronFluidRecipes {
    private static final InWorldRecipeData<Map<UniversalPlasticEntity, State>> STATES = InWorldRecipeData.of(
        AnvilcraftPlasticraft.of("molded_large_cauldron_fluids"), (context, key) -> new IdentityHashMap<>()
    );
    private static final ResourceLocation ACCEPTOR = AnvilcraftPlasticraft.of("molded_large_cauldron_fluids");

    private MoldedLargeCauldronFluidRecipes() {
    }

    public static @Nullable UniversalPlasticEntity target(InWorldRecipeContext context, HasCauldron predicate) {
        if (!(CauldronImpactRecipeProcessor.activeRecipeTarget() instanceof UniversalPlasticEntity pot)
            || !MoldedLargeCauldronInteraction.applies(pot) || pot.isRemoved() || pot.level() != context.getLevel()) return null;
        BlockPos position = BlockPos.containing(context.getPos().add(predicate.offset()));
        return position.equals(CauldronImpactRecipeProcessor.activeRecipeTargetCell()) ? pot : null;
    }

    public static boolean test(InWorldRecipeContext context, UniversalPlasticEntity pot, HasCauldron predicate) {
        if (predicate.consume() < 0 || predicate.consume() > LargeCauldronFluidHandler.TANK_CAPACITY
            || predicate.chance() < 0 || predicate.chance() > 1
            || predicate.fluid().amount().flatMap(MinMaxBounds.Ints::max).map(max -> predicate.consume() > max).orElse(false)
            || predicate.ignited() && !pot.anvilcraft$isIgnited()) return false;
        return apply(copy(state(context, pot).fluids), predicate);
    }

    public static void snapshot(InWorldRecipeContext context, UniversalPlasticEntity pot, HasCauldron predicate) {
        State state = state(context, pot);
        state.rollback.push(copy(state.fluids));
        if (context.getLevel().random.nextFloat() <= predicate.chance()) apply(state.fluids, predicate);
    }

    public static void rollback(InWorldRecipeContext context, UniversalPlasticEntity pot) {
        State state = state(context, pot);
        if (!state.rollback.isEmpty()) state.fluids = state.rollback.pop();
    }

    public static void clear(InWorldRecipeContext context, UniversalPlasticEntity pot) {
        state(context, pot).rollback.clear();
    }

    public static void accept(InWorldRecipeContext context) {
        context.putAcceptor(ACCEPTOR, accepted -> {
            for (Map.Entry<UniversalPlasticEntity, State> entry : accepted.computeIfAbsent(STATES).entrySet()) {
                if (!entry.getKey().isRemoved()) entry.getKey().getMoldedFluidHandler().setFluids(entry.getValue().fluids);
            }
        });
    }

    private static State state(InWorldRecipeContext context, UniversalPlasticEntity pot) {
        return context.computeIfAbsent(STATES).computeIfAbsent(pot, key -> new State(key.getMoldedFluidHandler().copyFluids()));
    }

    private static boolean apply(List<FluidStack> fluids, HasCauldron predicate) {
        int source = -1;
        if (predicate.requiresEmptyCauldron()) {
            if (fluids.stream().anyMatch(fluid -> !fluid.isEmpty())) return false;
            source = 0;
        } else if (predicate.hasCheck()) {
            for (int index = 0; index < fluids.size(); index++) {
                if (predicate.matchesFluid(fluids.get(index))) {
                    source = index;
                    break;
                }
            }
            if (source < 0) return false;
        }
        int amount = source < 0 ? 0 : fluids.get(source).getAmount();
        if (predicate.consume() > amount) return false;
        if (source >= 0 && predicate.consume() > 0) {
            fluids.set(source, fluids.get(source).copyWithAmount(amount - predicate.consume()));
        }
        for (FluidStack result : predicate.transforms()) {
            int target = -1;
            for (int index = 0; index < fluids.size(); index++) {
                if (FluidStack.isSameFluidSameComponents(fluids.get(index), result)) {
                    target = index;
                    break;
                }
            }
            long total = (long) result.getAmount() + (target < 0 ? 0 : fluids.get(target).getAmount());
            if (total > LargeCauldronFluidHandler.TANK_CAPACITY) return false;
            if (target < 0) {
                for (int index = 0; index < fluids.size(); index++) {
                    if (fluids.get(index).isEmpty()) {
                        target = index;
                        break;
                    }
                }
            }
            if (target < 0) return false;
            fluids.set(target, result.copyWithAmount((int) total));
        }
        return true;
    }

    private static List<FluidStack> copy(List<FluidStack> fluids) {
        List<FluidStack> result = new ArrayList<>();
        for (FluidStack fluid : fluids) result.add(fluid.copy());
        return result;
    }

    private static final class State {
        private List<FluidStack> fluids;
        private final Deque<List<FluidStack>> rollback = new ArrayDeque<>();

        private State(List<FluidStack> fluids) {
            this.fluids = copy(fluids);
            while (this.fluids.size() < LargeCauldronFluidHandler.TANK_COUNT) this.fluids.add(FluidStack.EMPTY);
        }
    }
}
