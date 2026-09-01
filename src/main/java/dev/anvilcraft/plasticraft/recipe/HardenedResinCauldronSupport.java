package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.PlasticCauldron;
import dev.anvilcraft.plasticraft.entity.PlasticCauldrons;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.dubhe.anvilcraft.init.block.ModFluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 给本体喷流静态 API 提供实体炼药锅的兼容查询。 */
public final class HardenedResinCauldronSupport {
    private HardenedResinCauldronSupport() {
    }

    public static boolean isIgnitedOil(Level level, BlockPos pos) {
        PlasticCauldron cauldron = find(level, pos);
        return cauldron != null
            && cauldron.anvilcraft$isIgnited()
            && cauldron.plasticraft$bottomFluid().is(ModFluidTags.OIL);
    }

    public static Boolean isIgnitedHighHeatFuel(Level level, BlockPos pos) {
        PlasticCauldron cauldron = find(level, pos);
        return cauldron == null ? null : cauldron.anvilcraft$isIgnited()
            && cauldron.plasticraft$bottomFluid().is(PlasticraftFluids.HIGH_HEAT_FUEL.get());
    }

    public static Boolean hasHighHeatFuel(Level level, BlockPos pos) {
        PlasticCauldron cauldron = find(level, pos);
        return cauldron == null ? null : cauldron.plasticraft$bottomFluid().is(PlasticraftFluids.HIGH_HEAT_FUEL.get());
    }

    public static Boolean validBase(Level level, BlockPos pos) {
        PlasticCauldron cauldron = find(level, pos);
        if (cauldron == null) return null;
        if (cauldron.getOrientation().attachmentFace() != Direction.UP) return false;
        FluidStack fluid = cauldron.plasticraft$bottomFluid();
        return fluid.isEmpty() || fluid.is(ModFluidTags.OIL) || fluid.is(PlasticraftFluids.HIGH_HEAT_FUEL.get());
    }

    public static Boolean consumeOnce(Level level, BlockPos pos) {
        return consumeOil(level, pos, 250);
    }

    public static Boolean usesContinuousFuel(Level level, BlockPos pos) {
        return find(level, pos) == null ? null : true;
    }

    public static Boolean consumeContinuousFuel(Level level, BlockPos pos, int amount) {
        return consumeOil(level, pos, amount);
    }

    public static Boolean consumeHighHeatFuel(Level level, BlockPos pos, int amount) {
        PlasticCauldron cauldron = find(level, pos);
        if (cauldron == null) return null;
        IFluidHandler handler = cauldron.plasticraft$bottomFluidAccess();
        FluidStack request = new FluidStack(PlasticraftFluids.HIGH_HEAT_FUEL.get(), amount);
        FluidStack simulated = handler.drain(request, IFluidHandler.FluidAction.SIMULATE);
        if (!FluidStack.matches(simulated, request)) return false;
        FluidStack drained = handler.drain(request, IFluidHandler.FluidAction.EXECUTE);
        return FluidStack.matches(drained, request);
    }

    private static Boolean consumeOil(Level level, BlockPos pos, int amount) {
        PlasticCauldron cauldron = find(level, pos);
        if (cauldron == null) return null;
        if (amount <= 0) return false;
        IFluidHandler handler = cauldron.plasticraft$bottomFluidAccess();
        FluidStack simulated = handler.drain(amount, IFluidHandler.FluidAction.SIMULATE);
        if (!simulated.is(ModFluidTags.OIL) || simulated.getAmount() != amount) return false;
        handler.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    public static Integer fluidAmount(Level level, BlockPos pos) {
        PlasticCauldron cauldron = find(level, pos);
        return cauldron == null ? null : cauldron.plasticraft$bottomFluid().getAmount();
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
