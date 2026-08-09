package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexCollisionResolver;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 维护塑料实体与原版侦测器观察面的接触边沿。 */
public final class PlasticObserverContact {
    private static final double PROBE_DISTANCE = 1.0E-3D;
    private static final double NORMAL_ALIGNMENT_EPSILON = 1.0E-5D;

    private PlasticObserverContact() {
    }

    public static Set<BlockPos> update(AbstractPlasticEntity entity, Set<BlockPos> previousContacts) {
        Set<BlockPos> contacts = findContacts(entity);
        if (contacts.equals(previousContacts)) return previousContacts;
        for (BlockPos previous : previousContacts) {
            if (!contacts.contains(previous)) scheduleSignal(entity.level(), previous);
        }
        for (BlockPos contact : contacts) {
            if (!previousContacts.contains(contact)) scheduleSignal(entity.level(), contact);
        }
        return contacts;
    }

    public static void clear(Level level, Set<BlockPos> contacts) {
        for (BlockPos contact : contacts) scheduleSignal(level, contact);
    }

    private static Set<BlockPos> findContacts(AbstractPlasticEntity entity) {
        List<PlasticConvexShape> entityShapes = entity.plasticraft$getCollisionBox().convexComponents();
        if (entityShapes.isEmpty()) return Set.of();
        AABB bounds = entity.plasticraft$getCollisionBox().bounds();
        int minX = Mth.floor(bounds.minX - PROBE_DISTANCE);
        int minY = Mth.floor(bounds.minY - PROBE_DISTANCE);
        int minZ = Mth.floor(bounds.minZ - PROBE_DISTANCE);
        int maxX = Mth.floor(bounds.maxX + PROBE_DISTANCE);
        int maxY = Mth.floor(bounds.maxY + PROBE_DISTANCE);
        int maxZ = Mth.floor(bounds.maxZ + PROBE_DISTANCE);
        Set<BlockPos> contacts = new LinkedHashSet<>();
        for (BlockPos mutablePos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockPos pos = mutablePos.immutable();
            if (!entity.level().hasChunkAt(pos)) continue;
            BlockState state = entity.level().getBlockState(pos);
            if (!state.is(Blocks.OBSERVER)) continue;
            Direction facing = state.getValue(ObserverBlock.FACING);
            Vec3 probe = Vec3.atLowerCornerOf(facing.getOpposite().getNormal()).scale(PROBE_DISTANCE);
            PlasticConvexCollisionResolver.SweepContact contact = PlasticConvexCollisionResolver.sweep(
                entityShapes,
                List.of(PlasticConvexShape.box(new AABB(pos))),
                probe
            );
            if (contact != null
                && contact.normal().dot(Vec3.atLowerCornerOf(facing.getNormal()))
                    > NORMAL_ALIGNMENT_EPSILON) {
                contacts.add(pos);
            }
        }
        return contacts.isEmpty() ? Set.of() : Set.copyOf(contacts);
    }

    private static void scheduleSignal(Level level, BlockPos pos) {
        if (level.isClientSide || !level.hasChunkAt(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.OBSERVER)
            || state.getValue(ObserverBlock.POWERED)
            || level.getBlockTicks().hasScheduledTick(pos, Blocks.OBSERVER)) {
            return;
        }
        // 直接复用 ObserverBlock.startSignal 的原版延迟与去重规则，避免伪造方块更新。
        level.scheduleTick(pos, Blocks.OBSERVER, 2);
    }
}
