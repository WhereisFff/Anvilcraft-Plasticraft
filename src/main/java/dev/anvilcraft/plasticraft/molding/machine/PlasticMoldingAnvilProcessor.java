package dev.anvilcraft.plasticraft.molding.machine;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberStructure;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 把成型舱动态快照接入 AnvilCraft 巨型铁砧落地流程。 */
public final class PlasticMoldingAnvilProcessor {
    private PlasticMoldingAnvilProcessor() {
    }

    /** 返回是否识别到成型舱专用结构；识别后始终阻止静态多方块配方重复处理。 */
    public static boolean handleLanding(AnvilEvent.GiantOnLand event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return false;
        return tryProcess(level, event.getPos().below(2));
    }

    public static boolean tryProcess(ServerLevel level, BlockPos topCenter) {
        List<ChamberMatch> matches = findChambers(level, topCenter);
        if (matches.isEmpty()) return false;
        OutputMode outputMode = detectOutputMode(level, topCenter);
        if (outputMode == null) return false;
        if (matches.size() != 1) {
            AnvilcraftPlasticraft.LOGGER.warn(
                "Ambiguous plastic molding chambers below giant anvil at {}",
                topCenter
            );
            return true;
        }
        ChamberMatch match = matches.getFirst();
        process(level, match, outputMode);
        return true;
    }

    public static @Nullable OutputMode detectOutputMode(ServerLevel level, BlockPos topCenter) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos pos = topCenter.offset(x, 0, z);
                if (!level.isInWorldBounds(pos) || !level.isLoaded(pos)) return null;
                if (x == 0 && z == 0) continue;
                if (!level.getBlockState(pos)
                    .is(Tags.Blocks.PLAYER_WORKSTATIONS_CRAFTING_TABLES)) {
                    return null;
                }
            }
        }
        BlockState centerState = level.getBlockState(topCenter);
        if (centerState.is(ModBlocks.SPACE_OVERCOMPRESSOR)) return OutputMode.ITEM;
        return centerState.is(Tags.Blocks.PLAYER_WORKSTATIONS_CRAFTING_TABLES)
            ? OutputMode.ENTITY
            : null;
    }

    public static Optional<BlockPos> findChamber(ServerLevel level, BlockPos topCenter) {
        List<ChamberMatch> matches = findChambers(level, topCenter);
        return matches.size() == 1 ? Optional.of(matches.getFirst().controller) : Optional.empty();
    }

    private static List<ChamberMatch> findChambers(ServerLevel level, BlockPos topCenter) {
        List<ChamberMatch> matches = new ArrayList<>();
        for (Direction front : Direction.Plane.HORIZONTAL) {
            BlockPos controller = topCenter.relative(front, 2).below(3);
            if (!level.isLoaded(controller)) continue;
            BlockState state = level.getBlockState(controller);
            if (!state.is(PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.get())
                || state.getValue(PlasticMoldingChamberBlock.FACING) != front
                || !PlasticMoldingChamberStructure.isComplete(level, controller, front)
                || !(level.getBlockEntity(controller) instanceof PlasticMoldingChamberBlockEntity chamber)) {
                continue;
            }
            matches.add(new ChamberMatch(controller.immutable(), front, chamber));
        }
        return List.copyOf(matches);
    }

    private static void process(ServerLevel level, ChamberMatch match, OutputMode outputMode) {
        PlasticMoldingChamberBlockEntity chamber = match.chamber;
        MoldingProcessSnapshot preview = new MoldingProcessSnapshot(
            chamber.revision(),
            chamber.model(),
            chamber.bakedModel(),
            chamber.batchFluid(),
            chamber.moldedClayBalls(),
            chamber.cycleMode()
        );
        PreparedOutput prepared;
        try {
            prepared = prepare(level, match, outputMode, preview);
        } catch (RuntimeException exception) {
            AnvilcraftPlasticraft.LOGGER.debug(
                "Plastic molding preflight failed at {}",
                match.controller,
                exception
            );
            return;
        }
        Optional<MoldingProcessSnapshot> started = chamber.beginProcessing();
        if (started.isEmpty()) return;
        MoldingProcessSnapshot snapshot = started.get();
        if (!sameSnapshot(preview, snapshot)) {
            chamber.abortProcessing(snapshot);
            return;
        }

        for (Entity entity : prepared.entities) {
            if (entity instanceof UniversalPlasticEntity plastic
                && !plastic.plasticraft$canOccupyBlocks(plastic.getOrientation(), plastic.position())) {
                rollback(chamber, snapshot, List.of());
                return;
            }
        }

        List<Entity> spawned = new ArrayList<>(prepared.entities.size());
        for (Entity entity : prepared.entities) {
            if (level.addFreshEntity(entity)) {
                spawned.add(entity);
                continue;
            }
            rollback(chamber, snapshot, spawned);
            return;
        }
        if (!chamber.finishProcessing(snapshot, true)) {
            rollback(chamber, snapshot, spawned);
            return;
        }
        playEffects(level, prepared.bounds, prepared.displayState);
    }

    private static PreparedOutput prepare(
        ServerLevel level,
        ChamberMatch match,
        OutputMode outputMode,
        MoldingProcessSnapshot snapshot
    ) {
        if (snapshot.batchFluid().getAmount() < PlasticMoldingChamberBlockEntity.MINIMUM_PROCESS_MELT
            || snapshot.moldedClayBalls() <= 0) {
            throw new IllegalArgumentException("Molding snapshot is not processable");
        }
        AABB bounds = PlasticMoldingChamberStructure.regionBounds(match.controller, match.front);
        ensureLoaded(level, match, bounds);
        if (!level.getEntities(
            (Entity) null,
            bounds,
            entity -> entity.isAlive() && !entity.isSpectator()
        ).isEmpty()) {
            throw new IllegalArgumentException("Molding output region is occupied");
        }
        MoldedPlasticData data = MoldedPlasticData.manufacture(
            snapshot.model(),
            snapshot.bakedModel(),
            snapshot.batchFluid(),
            snapshot.batchFluid().getAmount()
        );
        ItemStack product = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(product, data);
        PlasticMeltColor.set(product, PlasticMeltColor.get(snapshot.batchFluid()));
        PlasticItemData.setMaterial(product, "universal_plastic");
        BlockState displayState = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState()
            .setValue(DyeableMaterial.COLOR, PlasticMeltColor.get(snapshot.batchFluid()));

        List<Entity> entities = new ArrayList<>();
        if (outputMode == OutputMode.ITEM) {
            Vec3 center = bounds.getCenter();
            ItemEntity item = new ItemEntity(level, center.x, center.y, center.z, product);
            item.setDefaultPickUpDelay();
            entities.add(item);
        } else {
            PlasticEntityOrientation orientation = PlasticEntityOrientation.DEFAULT;
            Vec3 origin = data.geometry().entityOrigin();
            UniversalPlasticEntity entity = new UniversalPlasticEntity(
                PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
                level,
                new Vec3(bounds.minX + origin.x, bounds.minY + origin.y, bounds.minZ + origin.z),
                displayState,
                product,
                orientation
            );
            entity.setStartPos(entity.blockPosition());
            entities.add(entity);
        }
        addClayEntities(level, match, snapshot.moldedClayBalls(), entities);
        return new PreparedOutput(List.copyOf(entities), bounds, displayState);
    }

    private static void ensureLoaded(ServerLevel level, ChamberMatch match, AABB bounds) {
        if (!level.isInWorldBounds(match.controller) || !level.isLoaded(match.controller)) {
            throw new IllegalArgumentException("Molding chamber is not loaded");
        }
        for (BlockPos pos : PlasticMoldingChamberStructure.regionPositions(match.controller, match.front)) {
            if (!level.isInWorldBounds(pos) || !level.isLoaded(pos)) {
                throw new IllegalArgumentException("Molding output region is not loaded");
            }
        }
        BlockPos minimum = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos maximum = BlockPos.containing(bounds.maxX - 1.0D, bounds.maxY - 1.0D, bounds.maxZ - 1.0D);
        if (!level.isLoaded(minimum) || !level.isLoaded(maximum)) {
            throw new IllegalArgumentException("Molding output chunks are not loaded");
        }
    }

    private static void addClayEntities(
        ServerLevel level,
        ChamberMatch match,
        int amount,
        List<Entity> output
    ) {
        List<BlockPos> positions = PlasticMoldingChamberStructure.regionPositions(match.controller, match.front);
        List<BlockPos> preferred = new ArrayList<>(positions.size());
        List<BlockPos> obstructed = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            AABB cell = new AABB(pos).deflate(1.0E-6D);
            if (output.stream().anyMatch(entity -> entity.getBoundingBox().intersects(cell))) {
                obstructed.add(pos);
            } else {
                preferred.add(pos);
            }
        }
        preferred.addAll(obstructed);
        int dropped = 0;
        int positionIndex = 0;
        while (dropped < amount) {
            int count = Math.min(Items.CLAY_BALL.getDefaultMaxStackSize(), amount - dropped);
            BlockPos pos = preferred.get(positionIndex++ % preferred.size());
            ItemEntity clay = new ItemEntity(
                level,
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                new ItemStack(Items.CLAY_BALL, count)
            );
            clay.setDefaultPickUpDelay();
            output.add(clay);
            dropped += count;
        }
    }

    private static boolean sameSnapshot(MoldingProcessSnapshot first, MoldingProcessSnapshot second) {
        return first.modelRevision() == second.modelRevision()
            && first.bakedModel().modelHash().equals(second.bakedModel().modelHash())
            && first.moldedClayBalls() == second.moldedClayBalls()
            && first.cycleMode() == second.cycleMode()
            && FluidStack.matches(first.batchFluid(), second.batchFluid());
    }

    private static void rollback(
        PlasticMoldingChamberBlockEntity chamber,
        MoldingProcessSnapshot snapshot,
        List<Entity> spawned
    ) {
        for (Entity entity : spawned) entity.discard();
        if (!chamber.abortProcessing(snapshot)) {
            AnvilcraftPlasticraft.LOGGER.error(
                "Unable to roll back plastic molding transaction at {}",
                chamber.getBlockPos()
            );
        }
    }

    private static void playEffects(ServerLevel level, AABB bounds, BlockState displayState) {
        Vec3 center = bounds.getCenter();
        level.sendParticles(
            new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CLAY.defaultBlockState()),
            center.x,
            center.y,
            center.z,
            48,
            1.25D,
            1.25D,
            1.25D,
            0.08D
        );
        level.sendParticles(
            new BlockParticleOption(ParticleTypes.BLOCK, displayState),
            center.x,
            center.y,
            center.z,
            32,
            1.0D,
            1.0D,
            1.0D,
            0.04D
        );
        level.playSound(
            null,
            BlockPos.containing(center),
            SoundEvents.STONE_BREAK,
            SoundSource.BLOCKS,
            1.0F,
            0.75F
        );
    }

    public enum OutputMode {
        ITEM,
        ENTITY
    }

    private record ChamberMatch(
        BlockPos controller,
        Direction front,
        PlasticMoldingChamberBlockEntity chamber
    ) {
    }

    private record PreparedOutput(List<Entity> entities, AABB bounds, BlockState displayState) {
    }
}
