package dev.anvilcraft.plasticraft.entity.adhesive;

import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.init.PlasticraftAttachments;
import dev.dubhe.anvilcraft.entity.FallingGiantAnvilEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 区分可还原为单格方块的下落实体，并暂停由树脂接管的原版生命周期。 */
public final class AdhesiveFallingBlockBehavior {
    private AdhesiveFallingBlockBehavior() {
    }

    public static boolean canBlockify(Entity entity) {
        return entity instanceof FallingBlockEntity
            && !(entity instanceof FallingGiantAnvilEntity);
    }

    public static boolean canBlockifyComponent(Level level, Entity entity) {
        return canBlockify(entity)
            && EntityBondManager.component(level, entity).stream()
                .noneMatch(FallingGiantAnvilEntity.class::isInstance);
    }

    /** 返回是否应跳过本刻；作为粘合组基准的下落方块仍保留自身运动。 */
    public static boolean beforeTick(FallingBlockEntity entity) {
        if (entity instanceof AbstractPlasticEntity) return false;

        boolean hasBonds = EntityBondManager.hasBonds(entity);
        boolean controlled = entity.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT)
            || entity.hasData(PlasticraftAttachments.ENTITY_ADHESION);
        if (hasBonds || controlled) entity.time = 0;
        if (hasBonds && entity instanceof FallingGiantAnvilEntity) return true;
        return controlled;
    }

    public static void afterMovement(FallingBlockEntity entity) {
        if (EntityBondManager.hasBonds(entity)) markNearLanding(entity);
    }

    public static void afterTick(FallingBlockEntity entity) {
        if (entity instanceof AbstractPlasticEntity) return;
        if (EntityBondManager.hasBonds(entity)
            || entity.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT)
            || entity.hasData(PlasticraftAttachments.ENTITY_ADHESION)) {
            entity.time = 0;
        }
    }

    private static void markNearLanding(FallingBlockEntity entity) {
        if (entity.onGround()) return;
        BlockPos landingPos = entity.blockPosition();
        if (!entity.level().getBlockState(landingPos).canBeReplaced()) return;
        BlockPos belowPos = landingPos.below();
        BlockState belowState = entity.level().getBlockState(belowPos);
        if (FallingBlock.isFree(belowState)) return;
        VoxelShape collision = belowState.getCollisionShape(entity.level(), belowPos);
        if (collision.isEmpty()) return;
        double surfaceY = belowPos.getY() + collision.max(Direction.Axis.Y);
        double distance = entity.getBoundingBox().minY - surfaceY;
        if (distance >= -0.05D && distance <= 0.20D) entity.setOnGround(true);
    }

    public static void afterLanding(ServerLevel level, FallingBlockEntity fallingBlock, BlockPos placedPos) {
        if (fallingBlock instanceof AbstractPlasticEntity || fallingBlock instanceof FallingGiantAnvilEntity) return;
        EntityBondState state = EntityBondManager.get(fallingBlock);
        if (state == null) return;

        Map<UUID, Entity> neighbors = new LinkedHashMap<>();
        for (EntityBondLink link : state.links()) {
            Entity neighbor = EntityBondManager.resolve(level, link);
            if (neighbor != null && neighbor.isAlive()) neighbors.put(neighbor.getUUID(), neighbor);
        }
        EntityBondManager.disconnectEntity(level, fallingBlock);

        Map<UUID, Entity> anchors = new LinkedHashMap<>();
        for (Entity neighbor : neighbors.values()) {
            Entity leader = EntityBondManager.resolveLeader(level, neighbor);
            if (leader == null || !leader.isAlive()) leader = neighbor;
            anchors.put(leader.getUUID(), leader);
        }
        for (Entity anchor : anchors.values()) {
            Direction face = Direction.getNearest(
                anchor.getBoundingBox().getCenter().subtract(placedPos.getCenter())
            );
            if (!BondedFallingBlocks.setEntityBond(level, placedPos, face, true)) continue;
            EntityAdhesion adhesion = new EntityAdhesion(
                placedPos,
                face,
                BuiltInRegistries.BLOCK.getKey(
                    level.getBlockState(placedPos).getBlock()
                ),
                anchor.position(),
                anchor.isNoGravity()
            );
            anchor.setData(PlasticraftAttachments.ENTITY_ADHESION, adhesion);
            anchor.setDeltaMovement(Vec3.ZERO);
            anchor.fallDistance = 0.0F;
            anchor.hasImpulse = true;
            anchor.hurtMarked = true;
        }
    }
}
