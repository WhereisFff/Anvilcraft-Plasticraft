package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.PlasticCauldron;
import dev.anvilcraft.plasticraft.entity.PlasticCauldrons;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.dubhe.anvilcraft.AnvilCraft;
import dev.dubhe.anvilcraft.init.block.ModFluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

/** 给本体喷流静态 API 提供实体炼药锅的兼容查询。 */
public final class HardenedResinCauldronSupport {
    private HardenedResinCauldronSupport() {
    }

    public static boolean isIgnitedOil(Level level, BlockPos pos) {
        PlasticCauldron cauldron = find(level, pos);
        return cauldron != null
            && cauldron.anvilcraft$isIgnited()
            && cauldron.plasticraft$ignitionFluid().is(ModFluidTags.OIL);
    }

    public static Boolean isIgnitedHighHeatFuel(Level level, BlockPos pos) {
        PlasticCauldron cauldron = find(level, pos);
        return cauldron == null ? null : cauldron.anvilcraft$isIgnited()
            && cauldron.plasticraft$ignitionFluid().is(PlasticraftFluids.HIGH_HEAT_FUEL.get());
    }

    public static Boolean hasHighHeatFuel(Level level, BlockPos pos) {
        PlasticCauldron cauldron = find(level, pos);
        return cauldron == null ? null : cauldron.plasticraft$ignitionFluid().is(PlasticraftFluids.HIGH_HEAT_FUEL.get());
    }

    public static Boolean validBase(Level level, BlockPos pos) {
        PlasticCauldron cauldron = find(level, pos);
        if (cauldron == null) return null;
        if (cauldron.getOrientation().attachmentFace() != Direction.UP) return false;
        FluidStack fluid = cauldron.plasticraft$ignitionFluid();
        return fluid.isEmpty() || fluid.is(ModFluidTags.OIL) || fluid.is(PlasticraftFluids.HIGH_HEAT_FUEL.get());
    }

    public static OptionalInt consumeOnce(Level level, BlockPos pos, boolean simulate) {
        PlasticCauldron cauldron = find(level, pos);
        if (cauldron == null) return OptionalInt.empty();
        int amount = AnvilCraft.CONFIG.machines.plasmaJets.fishTankConsumeAmount;
        ResourceHandler<FluidResource> handler = cauldron.plasticraft$ignitionFluidAccess();
        FluidStack oil = cauldron.plasticraft$ignitionFluid();
        if (!oil.is(ModFluidTags.OIL)) return OptionalInt.empty();
        try (var tx = Transaction.openRoot()) {
            if (handler.extract(FluidResource.of(oil), amount, tx) != amount) return OptionalInt.empty();
            if (!simulate) tx.commit();
        }
        return OptionalInt.of(AnvilCraft.CONFIG.machines.plasmaJets.fishTankExtraDuration);
    }

    public static Boolean consumeHighHeatFuel(Level level, BlockPos pos, int amount) {
        PlasticCauldron cauldron = find(level, pos);
        if (cauldron == null) return null;
        FluidStack available = cauldron.plasticraft$ignitionFluid();
        if (amount <= 0 || !available.is(PlasticraftFluids.HIGH_HEAT_FUEL.get()) || available.getAmount() < amount) return false;
        ResourceHandler<FluidResource> handler = cauldron.plasticraft$ignitionFluidAccess();
        try (var tx = Transaction.openRoot()) {
            if (handler.extract(FluidResource.of(available), amount, tx) != amount) return false;
            tx.commit();
            return true;
        }
    }

    public static Integer fluidAmount(Level level, BlockPos pos) {
        PlasticCauldron cauldron = find(level, pos);
        return cauldron == null ? null : cauldron.plasticraft$ignitionFluid().getAmount();
    }

    /** 返回实际碰撞箱进入当前落砧格的锅，不把工作方块覆盖范围扩展为受击范围。 */
    public static List<PlasticCauldron> findRecipeTargets(Level level, BlockPos pos) {
        List<PlasticCauldron> targets = new ArrayList<>();
        AABB impactCell = new AABB(pos);
        for (PlasticCauldron candidate : PlasticCauldrons.findIn(level, impactCell)) {
            if (matchesRecipeTarget(candidate, impactCell)) {
                targets.add(candidate);
            }
        }
        if (level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
            && bonded.isInitialized()
            && bonded.isPlastic()) {
            PlasticCauldron cauldron = PlasticCauldrons.of(bonded.getOrCreateRenderEntity());
            if (cauldron != null
                && matchesRecipeTarget(cauldron, impactCell)
                && !targets.contains(cauldron)) {
                targets.add(cauldron);
            }
        }
        targets.sort(Comparator.comparingInt(PlasticCauldron::getId));
        return List.copyOf(targets);
    }

    private static boolean matchesRecipeTarget(PlasticCauldron cauldron, AABB impactCell) {
        return cauldron.plasticraft$cauldronEntity().getBoundingBox().intersects(impactCell);
    }

    private static @Nullable PlasticCauldron find(Level level, BlockPos pos) {
        PlasticCauldron selected = null;
        for (PlasticCauldron cauldron : PlasticCauldrons.findIn(
            level,
            new AABB(pos),
            candidate -> !candidate.isRemoved()
                && BlockPos.containing(candidate.getBoundingBox().getCenter()).equals(pos)
        )) {
            if (selected == null || cauldron.getId() < selected.getId()) selected = cauldron;
        }
        if (selected != null) return selected;
        if (level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
            && bonded.isInitialized()
            && bonded.isPlastic()) {
            return PlasticCauldrons.of(bonded.getOrCreateRenderEntity());
        }
        return null;
    }
}
