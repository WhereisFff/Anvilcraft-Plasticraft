package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.block.IgnitedFluidEffects;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.dubhe.anvilcraft.api.fluid.LargeCauldronFluidHandler;
import dev.dubhe.anvilcraft.util.FireReforgingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.wrappers.BucketPickupHandlerWrapper;

import java.util.ArrayList;
import java.util.List;

/** 大型塑料锅的源流体吸收、分层接触效果和锅壁攀爬。 */
public final class MoldedLargeCauldronEnvironment {
    private static final double EPSILON = 1.0E-5;

    private MoldedLargeCauldronEnvironment() {
    }

    public static void absorbSources(UniversalPlasticEntity pot) {
        if (pot.getOrientation().attachmentFace() != Direction.UP) return;
        AABB inside = pot.cauldronFluidArea(1);
        for (BlockPos pos : BlockPos.betweenClosed(
            BlockPos.containing(inside.minX + EPSILON, inside.maxY + EPSILON, inside.minZ + EPSILON),
            BlockPos.containing(inside.maxX - EPSILON, inside.maxY + EPSILON, inside.maxZ - EPSILON)
        )) {
            if (!pot.level().hasChunkAt(pos)) continue;
            FluidState fluid = pot.level().getFluidState(pos);
            if (fluid.isEmpty() || !fluid.isSource()) continue;
            if (!(pot.level().getBlockState(pos).getBlock() instanceof BucketPickup pickup)) continue;
            @SuppressWarnings("DataFlowIssue") IFluidHandler source = new BucketPickupHandlerWrapper(null, pickup, pot.level(), pos);
            FluidUtil.tryFluidTransfer(pot.getMoldedFluidHandler(), source, new FluidStack(fluid.getType(), FluidType.BUCKET_VOLUME), true);
        }
    }

    public static void tick(UniversalPlasticEntity pot) {
        if (!(pot.level() instanceof ServerLevel level) || pot.getOrientation().attachmentFace() != Direction.UP) return;
        List<FluidStack> fluids = pot.getMoldedFluidHandler().copyFluids();
        int total = fluids.stream().mapToInt(FluidStack::getAmount).sum();
        if (total > 0) {
            AABB area = pot.cauldronFluidArea(total / (double) LargeCauldronFluidHandler.TOTAL_CAPACITY);
            for (Entity entity : level.getEntities(pot, area, Entity::isAlive)) {
                if (pot.anvilcraft$isIgnited()) {
                    IgnitedFluidEffects.hurt(entity, level, pot.plasticraft$ignitionFluid());
                    continue;
                }
                int below = 0;
                boolean lava = false;
                FluidStack extinguishing = FluidStack.EMPTY;
                for (FluidStack fluid : fluids) {
                    if (layerArea(pot, below, fluid.getAmount()).intersects(entity.getBoundingBox())) {
                        lava |= fluid.is(Fluids.LAVA);
                        if (extinguishing.isEmpty() && entity.canFluidExtinguish(fluid.getFluidType())) extinguishing = fluid;
                    }
                    below += fluid.getAmount();
                }
                if (lava) entity.lavaHurt();
                if (!extinguishing.isEmpty() && entity.isOnFire()) {
                    entity.clearFire();
                    if (!(entity instanceof Player player && player.isCreative()) && entity.mayInteract(level, pot.blockPosition())) {
                        pot.getMoldedFluidHandler().drain(extinguishing.copyWithAmount(250), IFluidHandler.FluidAction.EXECUTE);
                    }
                }
            }
        }
        if (fluids.stream().anyMatch(fluid -> fluid.is(Fluids.LAVA))) {
            int offset = pot.plasticraft$cauldronLayout().outputSlots();
            for (int slot = 0; slot < pot.plasticraft$cauldronLayout().inputSlots(); slot++) {
                ItemStack stack = pot.getMoldedItemHandler().getStackInSlot(offset + slot);
                if (FireReforgingUtil.repair(stack, FireReforgingUtil.LAVA_REPAIR_PER_TICK, level, pot.blockPosition())) {
                    pot.getMoldedItemHandler().setStackInSlot(offset + slot, stack);
                }
            }
        }
        int campfireDamage = 0;
        for (BlockPos pos : PlasticCauldronWorkBlockFinder.findWorkBlockPositions(pot)) {
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (CampfireBlock.isLitCampfire(state)) campfireDamage = Math.max(campfireDamage, state.is(Blocks.SOUL_CAMPFIRE) ? 2 : 1);
        }
        if (campfireDamage > 0) {
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, pot.cauldronFluidArea(1))) {
                if (!entity.fireImmune() && !entity.isSteppingCarefully()) entity.hurt(level.damageSources().inFire(), campfireDamage);
            }
        }
    }

    private static AABB layerArea(UniversalPlasticEntity pot, int below, int amount) {
        AABB top = pot.cauldronFluidArea((below + amount) / (double) LargeCauldronFluidHandler.TOTAL_CAPACITY);
        double bottom = pot.cauldronFluidArea(below / (double) LargeCauldronFluidHandler.TOTAL_CAPACITY).maxY;
        return new AABB(top.minX, bottom, top.minZ, top.maxX, top.maxY, top.maxZ);
    }

    public static boolean insideMelt(UniversalPlasticEntity pot, Entity entity) {
        if (pot.getOrientation().attachmentFace() != Direction.UP) return false;
        int below = 0;
        for (FluidStack fluid : pot.getSyncedFluids()) {
            if (PlasticMaterial.isMelt(fluid) && layerArea(pot, below, fluid.getAmount()).intersects(entity.getBoundingBox())) return true;
            below += fluid.getAmount();
        }
        return false;
    }

    public static boolean canClimb(LivingEntity entity) {
        if (entity.isSpectator()) return false;
        AABB box = entity.getBoundingBox();
        AABB search = box.inflate(EPSILON);
        List<UniversalPlasticEntity> pots = new ArrayList<>(entity.level().getEntitiesOfClass(
            UniversalPlasticEntity.class, search, MoldedLargeCauldronInteraction::applies
        ));
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(search.minX, search.minY, search.minZ),
            BlockPos.containing(search.maxX, search.maxY, search.maxZ))) {
            if (!entity.level().hasChunkAt(pos)) continue;
            if (entity.level().getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
                && bonded.getOrCreateRenderEntity() instanceof UniversalPlasticEntity pot
                && MoldedLargeCauldronInteraction.applies(pot) && !pots.contains(pot)) pots.add(pot);
        }
        for (UniversalPlasticEntity pot : pots) {
            if (pot.getOrientation().attachmentFace() != Direction.UP) continue;
            double floor = pot.cauldronFluidArea(0).maxY;
            if (box.maxY <= floor + EPSILON) continue;
            for (AABB wall : pot.plasticraft$getCollisionBox().shape().toAabbs()) {
                if (wall.maxY <= floor + EPSILON || box.maxY <= wall.minY + EPSILON || box.minY >= wall.maxY - EPSILON) continue;
                if (box.maxZ > wall.minZ + EPSILON && box.minZ < wall.maxZ - EPSILON
                    && (Math.abs(box.maxX - wall.minX) <= EPSILON || Math.abs(box.minX - wall.maxX) <= EPSILON)) return true;
                if (box.maxX > wall.minX + EPSILON && box.minX < wall.maxX - EPSILON
                    && (Math.abs(box.maxZ - wall.minZ) <= EPSILON || Math.abs(box.minZ - wall.maxZ) <= EPSILON)) return true;
            }
        }
        return false;
    }
}
