package dev.anvilcraft.plasticraft.entity.adhesive;

import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.BondedFallingBlockInfo;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 把已粘接的下落实体组原子地转换为互相支撑的方块组。 */
public final class AdhesiveGroupBlockifier {
    private AdhesiveGroupBlockifier() {
    }

    public static boolean shouldBlockifyEntityTarget(
        ServerLevel level,
        Entity source,
        Entity target
    ) {
        List<Entity> merged = mergedComponents(level, source, target);
        return merged.size() >= 2
            && merged.stream().allMatch(AdhesiveFallingBlockBehavior::canBlockify)
            && merged.stream().anyMatch(entity -> !(entity instanceof AbstractPlasticEntity));
    }

    public static boolean blockifyEntityTarget(
        ServerLevel level,
        Entity source,
        Entity target,
        boolean sourceOriginalNoGravity
    ) {
        List<Entity> members = mergedComponents(level, source, target);
        if (members.size() < 2
            || members.stream().anyMatch(entity -> !AdhesiveFallingBlockBehavior.canBlockify(entity))) {
            return false;
        }
        Map<UUID, List<Entity>> graph = graph(level, members);
        addEdge(graph, source, target);
        Layout layout = layout(target, members, graph, blockPosition(target), true);
        if (layout == null) return false;
        return placeGroup(level, layout, null, null, source, sourceOriginalNoGravity);
    }

    public static boolean blockifyPlasticGroup(
        ServerLevel level,
        AbstractPlasticEntity root,
        AdhesiveTransit transit
    ) {
        List<Entity> members = EntityBondManager.component(level, root);
        if (members.size() < 2 || members.stream().anyMatch(entity -> !(entity instanceof AbstractPlasticEntity))) {
            return false;
        }
        Map<UUID, List<Entity>> graph = graph(level, members);
        BlockPos rootPos = transit.supportPos().relative(transit.attachmentFace());
        Layout layout = layout(root, members, graph, rootPos, false);
        if (layout == null) return false;
        return placeGroup(
            level,
            layout,
            transit.supportPos(),
            transit.attachmentFace(),
            root,
            transit.originalNoGravity()
        );
    }

    public static boolean isPlasticGroup(ServerLevel level, AbstractPlasticEntity root) {
        List<Entity> members = EntityBondManager.component(level, root);
        return members.size() > 1 && members.stream().allMatch(AbstractPlasticEntity.class::isInstance);
    }

    private static List<Entity> mergedComponents(ServerLevel level, Entity source, Entity target) {
        Map<UUID, Entity> merged = new LinkedHashMap<>();
        for (Entity member : EntityBondManager.component(level, target)) merged.put(member.getUUID(), member);
        for (Entity member : EntityBondManager.component(level, source)) merged.put(member.getUUID(), member);
        return List.copyOf(merged.values());
    }

    private static Map<UUID, List<Entity>> graph(ServerLevel level, List<Entity> members) {
        Set<UUID> memberUuids = new HashSet<>();
        Map<UUID, List<Entity>> graph = new LinkedHashMap<>();
        for (Entity member : members) {
            memberUuids.add(member.getUUID());
            graph.put(member.getUUID(), new ArrayList<>());
        }
        for (Entity member : members) {
            EntityBondState state = EntityBondManager.get(member);
            if (state == null) continue;
            for (EntityBondLink link : state.links()) {
                Entity other = EntityBondManager.resolve(level, link);
                if (other == null || !memberUuids.contains(other.getUUID())) continue;
                List<Entity> neighbors = graph.get(member.getUUID());
                if (!neighbors.contains(other)) neighbors.add(other);
            }
        }
        return graph;
    }

    private static void addEdge(Map<UUID, List<Entity>> graph, Entity first, Entity second) {
        graph.computeIfAbsent(first.getUUID(), ignored -> new ArrayList<>()).add(second);
        graph.computeIfAbsent(second.getUUID(), ignored -> new ArrayList<>()).add(first);
    }

    private static @Nullable Layout layout(
        Entity root,
        List<Entity> members,
        Map<UUID, List<Entity>> graph,
        BlockPos rootPos,
        boolean floatingCycle
    ) {
        Map<UUID, Entity> byUuid = new HashMap<>();
        for (Entity member : members) byUuid.put(member.getUUID(), member);
        Map<UUID, BlockPos> positions = new LinkedHashMap<>();
        Map<UUID, UUID> parents = new LinkedHashMap<>();
        ArrayDeque<Entity> pending = new ArrayDeque<>();
        positions.put(root.getUUID(), rootPos.immutable());
        pending.add(root);
        while (!pending.isEmpty()) {
            Entity current = pending.removeFirst();
            BlockPos currentPos = positions.get(current.getUUID());
            for (Entity other : graph.getOrDefault(current.getUUID(), List.of())) {
                Direction direction = directionBetween(current, other);
                BlockPos candidate = currentPos.relative(direction);
                BlockPos existing = positions.get(other.getUUID());
                if (existing != null) {
                    if (!existing.equals(candidate)) return null;
                    continue;
                }
                if (positions.containsValue(candidate)) return null;
                positions.put(other.getUUID(), candidate);
                parents.put(other.getUUID(), current.getUUID());
                pending.addLast(other);
            }
        }
        if (positions.size() != members.size()) return null;

        Map<UUID, UUID> supports = new LinkedHashMap<>(parents);
        if (floatingCycle) {
            Entity cyclePartner = graph.getOrDefault(root.getUUID(), List.of()).stream()
                .filter(entity -> parents.get(entity.getUUID()) != null)
                .findFirst()
                .orElse(null);
            if (cyclePartner == null) return null;
            supports.put(root.getUUID(), cyclePartner.getUUID());
            supports.put(cyclePartner.getUUID(), root.getUUID());
        }
        return new Layout(byUuid, positions, supports);
    }

    private static Direction directionBetween(Entity from, Entity to) {
        Vec3 difference = to.getBoundingBox().getCenter().subtract(from.getBoundingBox().getCenter());
        if (difference.lengthSqr() < 1.0E-8D) return Direction.UP;
        return Direction.getNearest(difference.x, difference.y, difference.z);
    }

    private static BlockPos blockPosition(Entity entity) {
        return BlockPos.containing(
            entity.getX(),
            entity.getBoundingBox().minY + 1.0E-4D,
            entity.getZ()
        );
    }

    private static boolean placeGroup(
        ServerLevel level,
        Layout layout,
        @Nullable BlockPos externalSupportPos,
        @Nullable Direction externalAttachmentFace,
        Entity source,
        boolean sourceOriginalNoGravity
    ) {
        Map<UUID, BlockState> fixedStates = new LinkedHashMap<>();
        Map<BlockPos, BlockState> replacedStates = new LinkedHashMap<>();
        Set<UUID> memberUuids = layout.entities.keySet();
        for (Map.Entry<UUID, BlockPos> entry : layout.positions.entrySet()) {
            Entity member = layout.entities.get(entry.getKey());
            if (!AdhesiveFallingBlockBehavior.canBlockify(member)) return false;
            FallingBlockEntity fallingBlock = (FallingBlockEntity) member;
            BlockPos pos = entry.getValue();
            if (!level.hasChunkAt(pos)
                || !level.getWorldBorder().isWithinBounds(pos)
                || !level.getBlockState(pos).canBeReplaced()) {
                return false;
            }
            BlockState fixedState = member instanceof AbstractPlasticEntity plastic
                ? plastic.getOrientation().applyToState(plastic.getDisplayState())
                    .setValue(AbstractPlasticEntityBlock.BONDED, true)
                : fallingBlock.getBlockState();
            if (!isPlacementUnobstructed(level, pos, fixedState, memberUuids)) return false;
            fixedStates.put(entry.getKey(), fixedState);
            replacedStates.put(pos, level.getBlockState(pos));
        }

        List<BlockPos> placed = new ArrayList<>();
        for (Map.Entry<UUID, BlockPos> entry : layout.positions.entrySet()) {
            if (!level.setBlock(entry.getValue(), fixedStates.get(entry.getKey()), Block.UPDATE_ALL)) {
                rollback(level, placed, replacedStates);
                return false;
            }
            placed.add(entry.getValue());
        }

        for (Map.Entry<UUID, BlockPos> entry : layout.positions.entrySet()) {
            Entity member = layout.entities.get(entry.getKey());
            BlockPos ownerPos = entry.getValue();
            UUID supportUuid = layout.supports.get(entry.getKey());
            BlockPos supportPos;
            Direction attachmentFace;
            if (supportUuid == null) {
                if (externalSupportPos == null || externalAttachmentFace == null) {
                    rollback(level, placed, replacedStates);
                    return false;
                }
                supportPos = externalSupportPos;
                attachmentFace = externalAttachmentFace;
            } else {
                supportPos = layout.positions.get(supportUuid);
                attachmentFace = directionFromTo(supportPos, ownerPos);
                if (attachmentFace == null) {
                    rollback(level, placed, replacedStates);
                    return false;
                }
            }
            boolean invisible = supportUuid != null
                && isInvisibleBond(member, layout.entities.get(supportUuid));
            boolean originalNoGravity = originalNoGravity(member, source, sourceOriginalNoGravity);
            if (member instanceof AbstractPlasticEntity plastic) {
                if (!(level.getBlockEntity(ownerPos) instanceof BondedEntityBlockEntity bonded)
                    || !bonded.initialize(
                        plastic,
                        plastic.getDisplayState(),
                        attachmentFace,
                        plastic.getOrientation(),
                        originalNoGravity
                    )) {
                    rollback(level, placed, replacedStates);
                    return false;
                }
            } else if (member instanceof FallingBlockEntity fallingBlock) {
                BlockState fixedState = fixedStates.get(entry.getKey());
                boolean pistonMovable = fixedState.getPistonPushReaction() == PushReaction.NORMAL
                    && fallingBlock.getPistonPushReaction() == PushReaction.NORMAL
                    && !(fixedState.getBlock() instanceof AnvilBlock);
                BondedFallingBlocks.put(level, ownerPos, new BondedFallingBlockInfo(
                    fixedState,
                    supportPos,
                    BuiltInRegistries.BLOCK.getKey(level.getBlockState(supportPos).getBlock()),
                    pistonMovable
                ));
            }
            if (!BondedFallingBlocks.connect(level, ownerPos, supportPos)) {
                rollback(level, placed, replacedStates);
                return false;
            }
            if (invisible) {
                Direction supportFace = directionFromTo(ownerPos, supportPos);
                if (supportFace != null) BondedFallingBlocks.setInvisible(level, ownerPos, supportFace);
            }
        }

        for (Entity member : layout.entities.values()) {
            EntityBondManager.removeForBlockification(member);
            AdhesiveBondingService.markBlockificationHandoff(member);
        }
        return true;
    }

    private static boolean isInvisibleBond(Entity entity, Entity other) {
        if (other == null) return false;
        EntityBondState state = EntityBondManager.get(entity);
        if (state == null) return false;
        for (EntityBondLink link : state.links()) {
            if (link.otherEntityUuid().equals(other.getUUID())) return link.invisible();
        }
        return false;
    }

    private static boolean originalNoGravity(
        Entity member,
        Entity source,
        boolean sourceOriginalNoGravity
    ) {
        if (member == source) return sourceOriginalNoGravity;
        EntityBondState bonds = EntityBondManager.get(member);
        return bonds == null ? member.isNoGravity() : bonds.originalNoGravity();
    }

    private static boolean isPlacementUnobstructed(
        ServerLevel level,
        BlockPos pos,
        BlockState state,
        Set<UUID> ignoredEntities
    ) {
        VoxelShape collision = state.getCollisionShape(level, pos);
        if (collision.isEmpty()) return true;
        for (AABB localBounds : collision.toAabbs()) {
            AABB bounds = localBounds.move(pos.getX(), pos.getY(), pos.getZ());
            if (!level.getEntities(
                (Entity) null,
                bounds,
                entity -> entity.isAlive()
                    && entity.blocksBuilding
                    && !ignoredEntities.contains(entity.getUUID())
            ).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void rollback(
        ServerLevel level,
        List<BlockPos> placed,
        Map<BlockPos, BlockState> replacedStates
    ) {
        for (BlockPos pos : placed) {
            BondedFallingBlocks.removeAll(level, pos);
            BlockState replaced = replacedStates.get(pos);
            if (replaced != null) level.setBlock(pos, replaced, Block.UPDATE_ALL);
        }
    }

    private static @Nullable Direction directionFromTo(BlockPos from, BlockPos to) {
        return Direction.fromDelta(
            to.getX() - from.getX(),
            to.getY() - from.getY(),
            to.getZ() - from.getZ()
        );
    }

    private record Layout(
        Map<UUID, Entity> entities,
        Map<UUID, BlockPos> positions,
        Map<UUID, UUID> supports
    ) {
    }
}
