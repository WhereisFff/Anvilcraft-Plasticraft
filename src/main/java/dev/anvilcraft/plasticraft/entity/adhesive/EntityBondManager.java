package dev.anvilcraft.plasticraft.entity.adhesive;

import dev.anvilcraft.plasticraft.api.entity.ShapedCollisionEntity;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexCollisionResolver;
import dev.anvilcraft.plasticraft.init.PlasticraftAttachments;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 维护实体粘接组的面占用、组基准和逐刻刚性约束。 */
public final class EntityBondManager {
    private static final ThreadLocal<ComponentMovement> COMPONENT_MOVEMENT = new ThreadLocal<>();

    private EntityBondManager() {
    }

    public static @Nullable EntityBondState get(Entity entity) {
        return entity.getExistingDataOrNull(PlasticraftAttachments.ENTITY_BONDS.get());
    }

    public static boolean hasBonds(Entity entity) {
        EntityBondState state = get(entity);
        return state != null && !state.links().isEmpty();
    }

    public static boolean isFaceOccupied(Entity entity, Direction storedFace) {
        EntityBondState state = get(entity);
        return state != null && state.linkAt(storedFace) != null;
    }

    public static List<Entity> component(Level level, Entity start) {
        List<Entity> result = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        ArrayDeque<Entity> pending = new ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            Entity current = pending.removeFirst();
            if (!current.isAlive() || !visited.add(current.getUUID())) continue;
            result.add(current);
            EntityBondState state = get(current);
            if (state == null) continue;
            for (EntityBondLink link : state.links()) {
                Entity other = resolve(level, link);
                if (other != null && !visited.contains(other.getUUID())) pending.addLast(other);
            }
        }
        return result;
    }

    public static void prepareLeader(ServerLevel level, Entity leader) {
        List<Entity> component = component(level, leader);
        for (Entity member : component) {
            EntityBondState state = get(member);
            if (state == null) continue;
            member.setData(
                PlasticraftAttachments.ENTITY_BONDS,
                state.withLeader(leader, member.position().subtract(leader.position()))
            );
            if (member == leader) {
                member.setNoGravity(state.originalNoGravity());
            } else {
                member.setNoGravity(true);
                member.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    /** 树脂牵引期间只刷新相对偏移，避免基准实体恢复到牵引前的重力状态。 */
    public static void updateLeaderOffsets(Level level, Entity leader) {
        for (Entity member : component(level, leader)) {
            EntityBondState state = get(member);
            if (state == null) continue;
            member.setData(
                PlasticraftAttachments.ENTITY_BONDS,
                state.withLeader(leader, member.position().subtract(leader.position()))
            );
        }
    }

    /** 供树脂牵引使用：先消除胶接面缝隙，再按新的成员位置重设基准。 */
    public static void prepareAlignedLeader(ServerLevel level, Entity leader) {
        alignComponentLinks(leader);
        prepareLeader(level, leader);
    }

    /** 普通实体不旋转，但所有连接记录都要映射到旋转后的普通实体世界面。 */
    public static void transformOrdinaryMemberFaces(
        Level level,
        Entity root,
        PlasticEntityOrientation startOrientation,
        PlasticEntityOrientation targetOrientation
    ) {
        if (startOrientation.equals(targetOrientation)) return;
        Map<UUID, List<EntityBondLink>> changed = new HashMap<>();
        for (Entity member : component(level, root)) {
            EntityBondState state = get(member);
            if (state == null) continue;
            List<EntityBondLink> links = new ArrayList<>(state.links().size());
            for (EntityBondLink link : state.links()) {
                Entity other = resolve(level, link);
                if (other == null) {
                    links.add(link);
                    continue;
                }
                links.add(new EntityBondLink(
                    transformedStoredFace(startOrientation, targetOrientation, member, link.face()),
                    link.otherEntityUuid(),
                    link.otherEntityId(),
                    transformedStoredFace(
                        startOrientation,
                        targetOrientation,
                        other,
                        link.otherFace()
                    ),
                    link.invisible()
                ));
            }
            changed.put(member.getUUID(), links);
        }
        for (Entity member : component(level, root)) {
            List<EntityBondLink> links = changed.get(member.getUUID());
            if (links == null) continue;
            EntityBondState state = get(member);
            if (state != null) member.setData(PlasticraftAttachments.ENTITY_BONDS, state.withLinks(links));
        }
    }

    private static Direction transformedStoredFace(
        PlasticEntityOrientation startOrientation,
        PlasticEntityOrientation targetOrientation,
        Entity member,
        Direction storedFace
    ) {
        if (member instanceof AbstractPlasticEntity) return storedFace;
        return targetOrientation.worldDirection(startOrientation.localDirection(storedFace));
    }

    public static boolean connect(
        ServerLevel level,
        Entity source,
        Direction sourceFace,
        Entity target,
        Direction targetFace,
        boolean sourceOriginalNoGravity
    ) {
        if (source == target || isFaceOccupied(source, sourceFace) || isFaceOccupied(target, targetFace)) {
            return false;
        }
        List<Entity> sourceComponent = component(level, source);
        List<Entity> targetComponent = component(level, target);
        Set<UUID> targetUuids = new HashSet<>();
        for (Entity member : targetComponent) targetUuids.add(member.getUUID());
        if (sourceComponent.stream().anyMatch(member -> targetUuids.contains(member.getUUID()))) return false;

        Entity targetLeader = resolveLeader(level, target);
        if (targetLeader == null) targetLeader = target;
        EntityBondLink sourceLink = new EntityBondLink(
            sourceFace,
            target.getUUID(),
            target.getId(),
            targetFace
        );
        EntityBondLink targetLink = new EntityBondLink(
            targetFace,
            source.getUUID(),
            source.getId(),
            sourceFace
        );
        Map<UUID, EntityBondState> changed = new HashMap<>();
        changed.put(source.getUUID(), stateWithLink(source, sourceLink, sourceOriginalNoGravity));
        changed.put(target.getUUID(), stateWithLink(target, targetLink, target.isNoGravity()));

        List<Entity> merged = new ArrayList<>(targetComponent.size() + sourceComponent.size());
        merged.addAll(targetComponent);
        merged.addAll(sourceComponent);
        for (Entity member : merged) {
            // 胶合后整个连通分量由领导者统一驱动位移，任何成员都不能停留在休眠态。
            if (member instanceof AbstractPlasticEntity plastic) plastic.plasticraft$wakeFromRest();
            EntityBondState state = changed.getOrDefault(member.getUUID(), get(member));
            if (state == null) continue;
            EntityBondState rebased = state.withLeader(
                targetLeader,
                member.position().subtract(targetLeader.position())
            );
            member.setData(PlasticraftAttachments.ENTITY_BONDS, rebased);
            if (member == targetLeader) {
                member.setNoGravity(rebased.originalNoGravity());
            } else {
                member.setNoGravity(true);
                member.setDeltaMovement(Vec3.ZERO);
            }
        }
        return true;
    }

    public static void tick(Entity entity) {
        EntityBondState state = get(entity);
        if (state == null) return;
        if (entity.level() instanceof ServerLevel serverLevel) {
            state = refreshRuntimeEntityIds(serverLevel, entity, state);
            if (pruneMissingLoadedPartners(serverLevel, entity, state)) return;
        }
        Entity leader = resolveLeader(entity.level(), state);
        if (leader == null || !leader.isAlive()) return;
        if (leader == entity) {
            synchronizeComponent(entity);
            return;
        }
        enforceFollower(entity, leader);
    }

    /** 在同一次推动内对齐全部从实体，避免被直接推动的从实体延迟到下一 tick 才追上主实体。 */
    public static void synchronizeComponent(Entity member) {
        Entity leader = resolveLeader(member.level(), member);
        if (leader == null || !leader.isAlive()) return;
        for (Entity componentMember : component(member.level(), leader)) {
            if (componentMember != leader) enforceFollower(componentMember, leader);
        }
    }

    public static void removeForBlockification(Entity entity) {
        entity.removeData(PlasticraftAttachments.ENTITY_BONDS);
    }

    public static boolean isFollower(Entity entity) {
        EntityBondState state = get(entity);
        return state != null && !state.leaderUuid().equals(entity.getUUID());
    }

    public static boolean areInSameComponent(Entity first, Entity second) {
        EntityBondState firstState = get(first);
        EntityBondState secondState = get(second);
        return firstState != null
            && secondState != null
            && firstState.leaderUuid().equals(secondState.leaderUuid());
    }

    /** 用每个成员的碰撞箱共同裁剪基准位移，避免跟随成员被直接搬进方块。 */
    public static Vec3 clampLeaderMovement(Entity leader, Vec3 movement) {
        ComponentMovement componentMovement = COMPONENT_MOVEMENT.get();
        if (componentMovement != null && componentMovement.leader() == leader) return movement;
        EntityBondState state = get(leader);
        if (movement.lengthSqr() <= 1.0E-12D
            || state == null
            || !state.leaderUuid().equals(leader.getUUID())) {
            return movement;
        }

        Vec3 allowed = movement;
        for (Entity member : component(leader.level(), leader)) {
            if (member == leader || allowed.lengthSqr() <= 1.0E-12D) continue;
            List<Entity> obstacles = leader.level().getEntities(
                member,
                ShapedCollisionEntity.collisionBounds(member).expandTowards(allowed),
                other -> !other.isRemoved()
                    && !other.isSpectator()
                    && !other.isPassengerOfSameVehicle(member)
                    && !areInSameComponent(member, other)
                    && member.canCollideWith(other)
            );
            List<VoxelShape> entityCollisions = obstacles.stream()
                .filter(other -> !PlasticConvexCollisionResolver.hasConvexCollision(other))
                .map(ShapedCollisionEntity::collisionShape)
                .toList();
            allowed = ShapedCollisionEntity.collideBoundingBox(
                member,
                allowed,
                member.getBoundingBox(),
                leader.level(),
                entityCollisions,
                obstacles::contains
            );
        }
        return allowed;
    }

    /** 在外层已经完成全组裁剪后移动内部基准，避免再次被推动者或从实体截断。 */
    public static void runPreclippedComponentMovement(Entity member, Entity ignored, Runnable movement) {
        Entity leader = resolveLeader(member.level(), member);
        if (leader == null || !leader.isAlive()) return;
        ComponentMovement previous = COMPONENT_MOVEMENT.get();
        COMPONENT_MOVEMENT.set(new ComponentMovement(leader, ignored));
        try {
            movement.run();
        } finally {
            if (previous == null) {
                COMPONENT_MOVEMENT.remove();
            } else {
                COMPONENT_MOVEMENT.set(previous);
            }
        }
    }

    public static boolean ignoresPreclippedCollision(Entity mover, Entity target) {
        ComponentMovement movement = COMPONENT_MOVEMENT.get();
        return movement != null && movement.leader() == mover && movement.ignored() == target;
    }

    /** 从任一成员发起移动时，用整个连接组的碰撞箱共同裁剪位移。 */
    public static Vec3 clampComponentMovement(Entity member, Entity ignored, Vec3 movement) {
        Entity leader = resolveLeader(member.level(), member);
        if (leader == null || !leader.isAlive()) return Vec3.ZERO;

        Vec3 allowed = movement;
        for (Entity componentMember : component(member.level(), leader)) {
            if (allowed.lengthSqr() <= 1.0E-12D) break;
            List<Entity> obstacles = member.level().getEntities(
                componentMember,
                ShapedCollisionEntity.collisionBounds(componentMember).expandTowards(allowed),
                other -> !other.isRemoved()
                    && !other.isSpectator()
                    && other != ignored
                    && !other.isPassengerOfSameVehicle(ignored)
                    && !areInSameComponent(componentMember, other)
                    && componentMember.canCollideWith(other)
            );
            List<VoxelShape> entityCollisions = obstacles.stream()
                .filter(other -> !PlasticConvexCollisionResolver.hasConvexCollision(other))
                .map(ShapedCollisionEntity::collisionShape)
                .toList();
            allowed = ShapedCollisionEntity.collideBoundingBox(
                componentMember,
                allowed,
                componentMember.getBoundingBox(),
                member.level(),
                entityCollisions,
                obstacles::contains
            );
        }
        return allowed;
    }

    /** 只移除指定实体的连接，并为删点后仍存在的每个连通分量重新选择基准。 */
    public static boolean disconnectEntity(ServerLevel level, Entity entity) {
        EntityBondState removedState = get(entity);
        if (removedState == null) return false;

        Map<UUID, Entity> neighbors = new HashMap<>();
        for (EntityBondLink link : removedState.links()) {
            Entity neighbor = resolve(level, link);
            if (neighbor != null) neighbors.put(neighbor.getUUID(), neighbor);
        }
        disconnectKnownEntity(level, entity, removedState, neighbors.values());
        return true;
    }

    /** 只解除指定面的实体连接，并分别重建断开后仍存在的连接分量。 */
    public static boolean disconnectFace(ServerLevel level, Entity entity, Direction storedFace) {
        EntityBondState state = get(entity);
        EntityBondLink link = state == null ? null : state.linkAt(storedFace);
        if (link == null) return false;

        Entity other = resolve(level, link);
        updateAfterLinkRemoval(entity, state.withoutLinkAt(storedFace));
        List<Entity> remaining = new ArrayList<>();
        remaining.add(entity);
        if (other != null) {
            EntityBondState otherState = get(other);
            if (otherState != null) {
                EntityBondLink reverse = otherState.linkAt(link.otherFace());
                EntityBondState changed = reverse != null && reverse.otherEntityUuid().equals(entity.getUUID())
                    ? otherState.withoutLinkAt(link.otherFace())
                    : otherState.withoutLinksTo(entity.getUUID());
                updateAfterLinkRemoval(other, changed);
            }
            remaining.add(other);
        }
        rebaseRemainingComponents(level, remaining);
        return true;
    }

    public static boolean setBondInvisible(ServerLevel level, Entity entity, Direction storedFace) {
        EntityBondState state = get(entity);
        EntityBondLink link = state == null ? null : state.linkAt(storedFace);
        if (link == null || link.invisible()) return false;
        entity.setData(PlasticraftAttachments.ENTITY_BONDS, state.withLink(link.withInvisible()));

        Entity other = resolve(level, link);
        EntityBondState otherState = other == null ? null : get(other);
        EntityBondLink reverse = otherState == null ? null : otherState.linkAt(link.otherFace());
        if (other != null
            && reverse != null
            && reverse.otherEntityUuid().equals(entity.getUUID())) {
            other.setData(PlasticraftAttachments.ENTITY_BONDS, otherState.withLink(reverse.withInvisible()));
        }
        return true;
    }

    private static boolean pruneMissingLoadedPartners(
        ServerLevel level,
        Entity entity,
        EntityBondState state
    ) {
        List<UUID> missingPartners = new ArrayList<>();
        for (EntityBondLink link : state.links()) {
            Entity other = resolve(level, link);
            if (other != null && other.isAlive()) continue;
            Direction worldFace = AdhesiveFaces.worldFace(entity, link.face());
            BlockPos expectedPos = BlockPos.containing(
                entity.getBoundingBox().getCenter().add(
                    worldFace.getStepX() * 2.0D,
                    worldFace.getStepY() * 2.0D,
                    worldFace.getStepZ() * 2.0D
                )
            );
            if (!level.hasChunkAt(expectedPos)) continue;
            if (other != null) {
                EntityBondState otherState = get(other);
                if (otherState != null) {
                    List<Entity> otherNeighbors = new ArrayList<>();
                    for (EntityBondLink otherLink : otherState.links()) {
                        Entity neighbor = resolve(level, otherLink);
                        if (neighbor != null && neighbor != other) otherNeighbors.add(neighbor);
                    }
                    disconnectKnownEntity(level, other, otherState, otherNeighbors);
                    return true;
                }
            }
            missingPartners.add(link.otherEntityUuid());
        }
        if (missingPartners.isEmpty()) return false;

        EntityBondState changed = get(entity);
        if (changed == null) return true;
        for (UUID missingPartner : missingPartners) changed = changed.withoutLinksTo(missingPartner);
        updateAfterLinkRemoval(entity, changed);
        if (hasBonds(entity)) prepareLeader(level, entity);
        return true;
    }

    private static void disconnectKnownEntity(
        ServerLevel level,
        Entity entity,
        EntityBondState removedState,
        Iterable<Entity> neighbors
    ) {
        Map<UUID, Entity> remainingNeighbors = new HashMap<>();
        for (Entity neighbor : neighbors) {
            if (neighbor != entity) remainingNeighbors.put(neighbor.getUUID(), neighbor);
        }
        restoreStandalone(entity, removedState);
        for (Entity neighbor : remainingNeighbors.values()) {
            EntityBondState neighborState = get(neighbor);
            if (neighborState == null) continue;
            updateAfterLinkRemoval(neighbor, neighborState.withoutLinksTo(entity.getUUID()));
        }
        rebaseRemainingComponents(level, remainingNeighbors.values());
    }

    private static void rebaseRemainingComponents(ServerLevel level, Iterable<Entity> candidates) {
        Set<UUID> rebased = new HashSet<>();
        for (Entity candidate : candidates) {
            if (!candidate.isAlive() || !hasBonds(candidate) || rebased.contains(candidate.getUUID())) continue;
            List<Entity> members = component(level, candidate);
            Entity leader = resolveLeader(level, candidate);
            boolean leaderRemains = false;
            for (Entity member : members) {
                if (member == leader) {
                    leaderRemains = true;
                    break;
                }
            }
            if (!leaderRemains) leader = candidate;
            prepareLeader(level, leader);
            for (Entity member : members) rebased.add(member.getUUID());
        }
    }

    private static void updateAfterLinkRemoval(Entity entity, EntityBondState state) {
        if (state.links().isEmpty()) {
            restoreStandalone(entity, state);
        } else {
            entity.setData(PlasticraftAttachments.ENTITY_BONDS, state);
        }
    }

    private static void restoreStandalone(Entity entity, EntityBondState state) {
        entity.removeData(PlasticraftAttachments.ADHESIVE_ELASTIC_MOTION);
        entity.removeData(PlasticraftAttachments.ENTITY_BONDS);
        entity.setNoGravity(state.originalNoGravity());
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0F;
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }

    public static @Nullable Entity resolve(Level level, EntityBondLink link) {
        Entity byId = level.getEntity(link.otherEntityId());
        if (byId != null && byId.getUUID().equals(link.otherEntityUuid())) return byId;
        return level instanceof ServerLevel serverLevel ? serverLevel.getEntity(link.otherEntityUuid()) : null;
    }

    public static @Nullable Entity resolveLeader(Level level, Entity entity) {
        EntityBondState state = get(entity);
        return state == null ? entity : resolveLeader(level, state);
    }

    private static @Nullable Entity resolveLeader(Level level, EntityBondState state) {
        Entity byId = level.getEntity(state.leaderEntityId());
        if (byId != null && byId.getUUID().equals(state.leaderUuid())) return byId;
        return level instanceof ServerLevel serverLevel ? serverLevel.getEntity(state.leaderUuid()) : null;
    }

    private static EntityBondState refreshRuntimeEntityIds(
        ServerLevel level,
        Entity entity,
        EntityBondState state
    ) {
        Entity leader = resolveLeader(level, state);
        int leaderEntityId = leader != null && leader.isAlive() ? leader.getId() : state.leaderEntityId();
        boolean changed = leaderEntityId != state.leaderEntityId();
        List<EntityBondLink> links = new ArrayList<>(state.links().size());
        for (EntityBondLink link : state.links()) {
            Entity other = resolve(level, link);
            EntityBondLink refreshed = other != null
                && other.isAlive()
                && other.getId() != link.otherEntityId()
                ? link.withOtherEntityId(other.getId())
                : link;
            changed |= refreshed != link;
            links.add(refreshed);
        }
        if (!changed) return state;

        EntityBondState refreshed = state.withResolvedEntityIds(leaderEntityId, links);
        entity.setData(PlasticraftAttachments.ENTITY_BONDS, refreshed);
        return refreshed;
    }

    private static EntityBondState stateWithLink(
        Entity entity,
        EntityBondLink link,
        boolean originalNoGravity
    ) {
        EntityBondState state = get(entity);
        return state == null
            ? new EntityBondState(
                entity.getUUID(),
                entity.getId(),
                Vec3.ZERO,
                originalNoGravity,
                List.of(link)
            )
            : state.withLink(link);
    }

    /** 沿胶接图从基准向外传播真实胶接面对齐点，消除旧坐标偏移留下的缝隙。 */
    private static void alignComponentLinks(Entity leader) {
        Set<UUID> aligned = new HashSet<>();
        ArrayDeque<Entity> pending = new ArrayDeque<>();
        aligned.add(leader.getUUID());
        pending.add(leader);
        while (!pending.isEmpty()) {
            Entity current = pending.removeFirst();
            EntityBondState state = get(current);
            if (state == null) continue;
            for (EntityBondLink link : state.links()) {
                Entity neighbor = resolve(current.level(), link);
                if (neighbor == null || !neighbor.isAlive() || !aligned.add(neighbor.getUUID())) continue;
                Vec3 targetPosition = neighbor.position().add(
                    AdhesiveFaces.storedFaceAlignmentPoint(current, link.face()).subtract(
                        AdhesiveFaces.storedFaceAlignmentPoint(neighbor, link.otherFace())
                    )
                );
                if (neighbor.position().distanceToSqr(targetPosition) > 1.0E-10D) {
                    neighbor.setPos(targetPosition);
                    neighbor.hasImpulse = true;
                    neighbor.hurtMarked = true;
                }
                pending.addLast(neighbor);
            }
        }
    }

    private static void enforceFollower(Entity follower, Entity leader) {
        if (AdhesiveBondingService.isElasticMotion(follower)) return;
        EntityBondState state = get(follower);
        if (state == null) return;
        Vec3 expected = leader.position().add(state.offsetFromLeader());
        follower.setNoGravity(true);
        follower.setDeltaMovement(Vec3.ZERO);
        follower.fallDistance = 0.0F;
        if (follower.level().isClientSide) alignClientInterpolation(follower, leader, state.offsetFromLeader());
        if (follower.position().distanceToSqr(expected) > 1.0E-10D) follower.setPos(expected);
        follower.hasImpulse = true;
        follower.hurtMarked = true;
    }

    /** 让从属实体使用与基准相同的客户端插值区间，避免按逻辑刻跳动。 */
    private static void alignClientInterpolation(Entity follower, Entity leader, Vec3 offset) {
        follower.xo = leader.xo + offset.x;
        follower.yo = leader.yo + offset.y;
        follower.zo = leader.zo + offset.z;
        follower.xOld = leader.xOld + offset.x;
        follower.yOld = leader.yOld + offset.y;
        follower.zOld = leader.zOld + offset.z;
    }

    private record ComponentMovement(Entity leader, Entity ignored) {
    }
}
