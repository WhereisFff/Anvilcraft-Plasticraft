package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.dubhe.anvilcraft.init.block.ModFluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

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

    public static Boolean validBase(Level level, BlockPos pos) {
        HardenedResinCauldronEntity cauldron = find(level, pos);
        if (cauldron == null) return null;
        if (cauldron.getOrientation().attachmentFace() != Direction.UP) return false;
        FluidStack fluid = cauldron.getFluidHandler().getFluid();
        return fluid.isEmpty() || fluid.is(ModFluidTags.OIL);
    }

    public static Boolean consumeOnce(Level level, BlockPos pos) {
        HardenedResinCauldronEntity cauldron = find(level, pos);
        if (cauldron == null) return null;
        IFluidHandler handler = cauldron.getFluidHandler();
        FluidStack simulated = handler.drain(250, IFluidHandler.FluidAction.SIMULATE);
        if (!simulated.is(ModFluidTags.OIL) || simulated.getAmount() != 250) return false;
        handler.drain(250, IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    public static Integer fluidAmount(Level level, BlockPos pos) {
        HardenedResinCauldronEntity cauldron = find(level, pos);
        return cauldron == null ? null : cauldron.getFluidHandler().getFluidAmount();
    }

    private static HardenedResinCauldronEntity find(Level level, BlockPos pos) {
        Entity selected = null;
        for (Entity entity : level.getEntitiesOfClass(Entity.class, new net.minecraft.world.phys.AABB(pos),
            candidate -> candidate instanceof HardenedResinCauldronEntity
                && !candidate.isRemoved()
                && BlockPos.containing(candidate.getBoundingBox().getCenter()).equals(pos))) {
            if (selected == null || entity.getId() < selected.getId()) selected = entity;
        }
        return selected instanceof HardenedResinCauldronEntity cauldron ? cauldron : null;
    }
}
