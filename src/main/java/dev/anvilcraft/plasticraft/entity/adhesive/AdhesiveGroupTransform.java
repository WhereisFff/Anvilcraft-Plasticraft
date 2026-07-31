package dev.anvilcraft.plasticraft.entity.adhesive;

import dev.anvilcraft.plasticraft.api.entity.ShapedCollisionEntity;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityCollisionBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 根据被牵引实体的离散旋转投影整组实体，普通实体只跟随位置而不改变朝向。 */
public final class AdhesiveGroupTransform {
    private static final double ALIGNMENT_EPSILON_SQR = 1.0E-8D;
    private static final double COLLISION_EPSILON = 1.0E-5D;
    private static final double FINAL_CONTACT_EPSILON = 0.002D;

    private AdhesiveGroupTransform() {
    }

    /**
     * 以 {@code rootPosition} 为基准计算整个已粘接分量的目标状态。
     * 塑料成员共享基准实体的世界旋转，普通实体则保持原有姿态。
     */
    public static Projection project(
        Entity root,
        Vec3 rootPosition,
        @Nullable PlasticEntityOrientation rootOrientation
    ) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(rootPosition, "rootPosition");
        PlasticEntityOrientation startOrientation = root instanceof AbstractPlasticEntity plastic
            ? plastic.getOrientation()
            : null;
        PlasticEntityOrientation targetOrientation = rootOrientation == null ? startOrientation : rootOrientation;
        WorldRotation rotation = new WorldRotation(startOrientation, targetOrientation);
        Map<UUID, PlasticEntityOrientation> orientations = new HashMap<>();
        List<Entity> component = EntityBondManager.component(root.level(), root);
        for (Entity member : component) {
            if (member instanceof AbstractPlasticEntity plastic) {
                PlasticEntityOrientation orientation = member == root && targetOrientation != null
                    ? targetOrientation
                    : rotateOrientation(plastic.getOrientation(), rotation);
                orientations.put(member.getUUID(), orientation);
            }
        }

        Map<UUID, Member> members = new HashMap<>();
        List<Member> orderedMembers = new ArrayList<>();
        ArrayDeque<Entity> pending = new ArrayDeque<>();
        Member rootMember = createMember(root, rootPosition, orientations.get(root.getUUID()));
        members.put(root.getUUID(), rootMember);
        orderedMembers.add(rootMember);
        pending.add(root);
        boolean complete = true;
        boolean consistent = true;
        while (!pending.isEmpty()) {
            Entity current = pending.removeFirst();
            Member currentMember = members.get(current.getUUID());
            EntityBondState state = EntityBondManager.get(current);
            if (state == null) continue;
            for (EntityBondLink link : state.links()) {
                Entity neighbor = EntityBondManager.resolve(root.level(), link);
                if (neighbor == null || !neighbor.isAlive()) {
                    complete = false;
                    continue;
                }
                Direction currentFace = transformedStoredFace(
                    startOrientation,
                    targetOrientation,
                    current,
                    link.face()
                );
                Direction neighborFace = transformedStoredFace(
                    startOrientation,
                    targetOrientation,
                    neighbor,
                    link.otherFace()
                );
                Vec3 neighborPosition = neighbor.position().add(
                    alignmentPoint(currentMember, currentFace).subtract(
                        alignmentPoint(
                            createMember(neighbor, neighbor.position(), orientations.get(neighbor.getUUID())),
                            neighborFace
                        )
                    )
                );
                Member projected = createMember(
                    neighbor,
                    neighborPosition,
                    orientations.get(neighbor.getUUID())
                );
                Member existing = members.putIfAbsent(neighbor.getUUID(), projected);
                if (existing == null) {
                    orderedMembers.add(projected);
                    pending.addLast(neighbor);
                } else if (existing.position().distanceToSqr(projected.position()) > ALIGNMENT_EPSILON_SQR) {
                    consistent = false;
                }
            }
        }
        return new Projection(root, orderedMembers, complete && consistent);
    }

    /** 计算根实体沿指定粘接计划到达终点后的整组投影。 */
    public static Projection projectTarget(Entity root, AdhesivePathPlanner.Plan plan) {
        Objects.requireNonNull(plan, "plan");
        return project(root, plan.targetPosition(), plan.targetOrientation());
    }

    /** 返回应用根实体目标旋转后，指定成员应继续使用的存储胶接面。 */
    public static Direction transformedStoredFace(
        Entity root,
        @Nullable PlasticEntityOrientation rootOrientation,
        Entity member,
        Direction storedFace
    ) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(storedFace, "storedFace");
        if (member instanceof AbstractPlasticEntity) return storedFace;
        PlasticEntityOrientation startOrientation = root instanceof AbstractPlasticEntity plastic
            ? plastic.getOrientation()
            : null;
        return transformedStoredFace(
            startOrientation,
            rootOrientation == null ? startOrientation : rootOrientation,
            member,
            storedFace
        );
    }

    private static Direction transformedStoredFace(
        @Nullable PlasticEntityOrientation startOrientation,
        @Nullable PlasticEntityOrientation targetOrientation,
        Entity member,
        Direction storedFace
    ) {
        if (member instanceof AbstractPlasticEntity) return storedFace;
        return new WorldRotation(startOrientation, targetOrientation).apply(storedFace);
    }

    /** 检查整组在目标粘接位置的碰撞，牵引过程不参与判定。 */
    public static boolean isPlanClear(Level level, Entity root, AdhesivePathPlanner.Plan plan) {
        return isPlanClear(level, root, plan, null);
    }

    /** 树脂使用者不参与目标位置碰撞，保留原有可在自身附近发起牵引的交互。 */
    public static boolean isPlanClear(
        Level level,
        Entity root,
        AdhesivePathPlanner.Plan plan,
        @Nullable Entity ignoredEntity
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(plan, "plan");
        if (!plan.valid()) return false;

        Projection projection = projectTarget(root, plan);
        return isProjectionClearAtFinalContact(level, projection, root, ignoredEntity);
    }

    /** 校验投影后的所有成员；同组成员彼此允许接触，但任何外部碰撞都会拒绝。 */
    public static boolean isProjectionClear(Level level, Projection projection) {
        return isProjectionClear(level, projection, null, COLLISION_EPSILON, null);
    }

    public static boolean isProjectionClearAtFinalContact(
        Level level,
        Projection projection,
        Entity finalContactRoot,
        @Nullable Entity ignoredEntity
    ) {
        return isProjectionClear(
            level,
            projection,
            finalContactRoot,
            FINAL_CONTACT_EPSILON,
            ignoredEntity
        );
    }

    /**
     * 牵引途中允许成员接触目标实体，因此只裁定世界方块是否阻挡投影。
     */
    public static boolean isProjectionClearOfBlocks(Level level, Projection projection) {
        return isProjectionClearOfBlocks(level, projection, COLLISION_EPSILON);
    }

    public static boolean isProjectionClearOfBlocksAtFinalContact(Level level, Projection projection) {
        return isProjectionClearOfBlocks(level, projection, FINAL_CONTACT_EPSILON);
    }

    private static boolean isProjectionClearOfBlocks(
        Level level,
        Projection projection,
        double collisionDeflation
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(projection, "projection");
        if (!projection.valid()) return false;
        for (Member member : projection.members()) {
            if (!level.getWorldBorder().isWithinBounds(member.collisionBounds())) return false;
            if (!hasLoadedBlocks(level, member.collisionBounds())) return false;
            if (collidesWithBlocks(level, member, collisionDeflation)) return false;
        }
        return true;
    }

    private static boolean isProjectionClear(
        Level level,
        Projection projection,
        @Nullable Entity finalContactRoot,
        double finalContactDeflation,
        @Nullable Entity ignoredEntity
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(projection, "projection");
        if (!projection.valid()) return false;
        Set<UUID> memberIds = new HashSet<>();
        for (Member member : projection.members()) memberIds.add(member.entity().getUUID());
        for (Member member : projection.members()) {
            double collisionDeflation = member.entity() == finalContactRoot
                ? finalContactDeflation
                : COLLISION_EPSILON;
            if (!level.getWorldBorder().isWithinBounds(member.collisionBounds())) return false;
            if (!hasLoadedBlocks(level, member.collisionBounds())) return false;
            if (collidesWithBlocks(level, member, collisionDeflation)) return false;
            if (collidesWithEntities(level, member, memberIds, collisionDeflation, ignoredEntity)) return false;
        }
        return true;
    }

    private static boolean hasLoadedBlocks(Level level, AABB bounds) {
        int minX = (int) Math.floor(bounds.minX);
        int minY = (int) Math.floor(bounds.minY);
        int minZ = (int) Math.floor(bounds.minZ);
        int maxX = (int) Math.floor(bounds.maxX);
        int maxY = (int) Math.floor(bounds.maxY);
        int maxZ = (int) Math.floor(bounds.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!level.hasChunkAt(new BlockPos(x, y, z))) return false;
                }
            }
        }
        return true;
    }

    private static boolean collidesWithBlocks(Level level, Member member, double deflation) {
        for (AABB component : member.collisionShape().toAabbs()) {
            AABB probe = deflate(component, deflation);
            if (!level.noBlockCollision(null, probe)) return true;
        }
        return false;
    }

    private static boolean collidesWithEntities(
        Level level,
        Member member,
        Set<UUID> memberIds,
        double deflation,
        @Nullable Entity ignoredEntity
    ) {
        VoxelShape probe = insetShape(member.collisionShape(), deflation);
        if (probe.isEmpty()) return false;
        return level.getEntities(
            (Entity) null,
            member.collisionBounds().inflate(COLLISION_EPSILON),
            other -> other.isAlive()
                && !other.isSpectator()
                && !memberIds.contains(other.getUUID())
                && other != ignoredEntity
                && member.entity().canCollideWith(other)
        ).stream().map(ShapedCollisionEntity::collisionShape).anyMatch(other ->
            Shapes.joinIsNotEmpty(probe, other, BooleanOp.AND)
        );
    }

    private static VoxelShape insetShape(VoxelShape shape, double deflation) {
        VoxelShape result = Shapes.empty();
        for (AABB component : shape.toAabbs()) {
            result = Shapes.or(result, Shapes.create(deflate(component, deflation)));
        }
        return result;
    }

    private static AABB deflate(AABB box, double deflation) {
        double maximumDeflation = Math.min(
            box.getXsize(),
            Math.min(box.getYsize(), box.getZsize())
        ) * 0.25D;
        return box.deflate(Math.min(deflation, maximumDeflation));
    }

    private static Member createMember(
        Entity entity,
        Vec3 position,
        @Nullable PlasticEntityOrientation orientation
    ) {
        VoxelShape collisionShape;
        if (entity instanceof AbstractPlasticEntity plastic) {
            PlasticEntityOrientation plasticOrientation = Objects.requireNonNull(orientation, "plastic orientation");
            PlasticEntityCollisionBox collisionBox = plastic.plasticraft$getGeometry().collisionBoxAt(
                position,
                plasticOrientation
            );
            collisionShape = collisionBox.shape();
        } else {
            Vec3 movement = position.subtract(entity.position());
            collisionShape = ShapedCollisionEntity.collisionShape(entity).move(
                movement.x,
                movement.y,
                movement.z
            );
        }
        return new Member(entity, position, orientation, collisionShape, collisionShape.bounds());
    }

    private static Vec3 alignmentPoint(Member member, Direction storedFace) {
        Entity entity = member.entity();
        if (entity instanceof AbstractPlasticEntity plastic) {
            return plastic.plasticraft$getGeometry().faceAlignmentPointAt(
                member.position(),
                Objects.requireNonNull(member.orientation(), "plastic orientation"),
                storedFace
            );
        }
        return member.position().add(
            AdhesiveFaces.storedFaceAlignmentPoint(entity, storedFace).subtract(entity.position())
        );
    }

    private static PlasticEntityOrientation rotateOrientation(
        PlasticEntityOrientation orientation,
        WorldRotation rotation
    ) {
        Direction attachmentFace = rotation.apply(orientation.attachmentFace());
        Direction longAxis = rotation.apply(orientation.longAxis());
        return PlasticEntityOrientation.fromLongAxis(attachmentFace, longAxis);
    }

    public record Projection(Entity root, List<Member> members, boolean valid) {
        public Projection {
            Objects.requireNonNull(root, "root");
            members = List.copyOf(members);
        }

        public @Nullable Member member(Entity entity) {
            for (Member member : this.members) {
                if (member.entity() == entity) return member;
            }
            return null;
        }
    }

    public record Member(
        Entity entity,
        Vec3 position,
        @Nullable PlasticEntityOrientation orientation,
        VoxelShape collisionShape,
        AABB collisionBounds
    ) {
        public Member {
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(collisionShape, "collisionShape");
            Objects.requireNonNull(collisionBounds, "collisionBounds");
        }

        public boolean plastic() {
            return this.orientation != null;
        }
    }

    private record WorldRotation(
        @Nullable PlasticEntityOrientation start,
        @Nullable PlasticEntityOrientation target
    ) {
        private Direction apply(Direction direction) {
            if (this.start == null || this.target == null) return direction;
            return this.target.worldDirection(this.start.localDirection(direction));
        }
    }
}
