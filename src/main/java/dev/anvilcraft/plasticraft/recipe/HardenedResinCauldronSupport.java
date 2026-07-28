package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import dev.dubhe.anvilcraft.init.block.ModFluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 给本体喷流静态 API 提供实体炼药锅的兼容查询。 */
public final class HardenedResinCauldronSupport {
    private HardenedResinCauldronSupport() {
    }

    public static boolean isIgnitedOil(Level level, BlockPos pos) {
        HardenedResinCauldronEntity cauldron = find(level, pos);
        return cauldron != null
            && cauldron.anvilcraft$isIgnited()
            && cauldron.getFluidHandler().getFluid().is(ModFluidTags.OIL);
    }

    public static Boolean isIgnitedHighHeatFuel(Level level, BlockPos pos) {
        HardenedResinCauldronEntity cauldron = find(level, pos);
        return cauldron == null ? null : cauldron.anvilcraft$isIgnited()
            && cauldron.getFluidHandler().getFluid().is(ModFluids.HIGH_HEAT_FUEL.get());
    }

    public static Boolean hasHighHeatFuel(Level level, BlockPos pos) {
        HardenedResinCauldronEntity cauldron = find(level, pos);
        return cauldron == null ? null : cauldron.getFluidHandler().getFluid().is(ModFluids.HIGH_HEAT_FUEL.get());
    }

    public static Boolean validBase(Level level, BlockPos pos) {
        HardenedResinCauldronEntity cauldron = find(level, pos);
        if (cauldron == null) return null;
        if (cauldron.getOrientation().attachmentFace() != Direction.UP) return false;
        FluidStack fluid = cauldron.getFluidHandler().getFluid();
        return fluid.isEmpty() || fluid.is(ModFluidTags.OIL) || fluid.is(ModFluids.HIGH_HEAT_FUEL.get());
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
        HardenedResinCauldronEntity cauldron = find(level, pos);
        if (cauldron == null) return null;
        IFluidHandler handler = cauldron.getFluidHandler();
        FluidStack request = new FluidStack(ModFluids.HIGH_HEAT_FUEL.get(), amount);
        FluidStack simulated = handler.drain(request, IFluidHandler.FluidAction.SIMULATE);
        if (!FluidStack.matches(simulated, request)) return false;
        FluidStack drained = handler.drain(request, IFluidHandler.FluidAction.EXECUTE);
        return FluidStack.matches(drained, request);
    }

    private static Boolean consumeOil(Level level, BlockPos pos, int amount) {
        HardenedResinCauldronEntity cauldron = find(level, pos);
        if (cauldron == null) return null;
        if (amount <= 0) return false;
        IFluidHandler handler = cauldron.getFluidHandler();
        FluidStack simulated = handler.drain(amount, IFluidHandler.FluidAction.SIMULATE);
        if (!simulated.is(ModFluidTags.OIL) || simulated.getAmount() != amount) return false;
        handler.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    public static Integer fluidAmount(Level level, BlockPos pos) {
        HardenedResinCauldronEntity cauldron = find(level, pos);
        return cauldron == null ? null : cauldron.getFluidHandler().getFluidAmount();
    }

    /** 返回与落砧配方锅格相交的全部实体锅，包括从四个角跨入同一格的锅。 */
    public static List<HardenedResinCauldronEntity> findRecipeTargets(Level level, BlockPos pos) {
        List<HardenedResinCauldronEntity> targets = new ArrayList<>(level.getEntitiesOfClass(
            HardenedResinCauldronEntity.class,
            new AABB(pos).inflate(0.0625D),
            Entity::isAlive
        ));
        if (level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
            && bonded.isInitialized()
            && bonded.isPlastic()
            && bonded.getOrCreateRenderEntity() instanceof HardenedResinCauldronEntity cauldron
            && !targets.contains(cauldron)) {
            targets.add(cauldron);
        }
        targets.sort(Comparator.comparingInt(Entity::getId));
        return List.copyOf(targets);
    }

    private static HardenedResinCauldronEntity find(Level level, BlockPos pos) {
        Entity selected = null;
        for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(pos),
            candidate -> candidate instanceof HardenedResinCauldronEntity
                && !candidate.isRemoved()
                && BlockPos.containing(candidate.getBoundingBox().getCenter()).equals(pos))) {
            if (selected == null || entity.getId() < selected.getId()) selected = entity;
        }
        if (selected instanceof HardenedResinCauldronEntity cauldron) return cauldron;
        if (level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
            && bonded.isInitialized()
            && bonded.isPlastic()
            && bonded.getOrCreateRenderEntity() instanceof HardenedResinCauldronEntity cauldron) {
            return cauldron;
        }
        return null;
    }
}
