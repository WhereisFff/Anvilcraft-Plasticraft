package dev.anvilcraft.plasticraft.event;

import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.CatalyticPressLidEntity;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.entity.FallingGiantAnvilEntity;
import dev.dubhe.anvilcraft.util.AccelerateManager;
import dev.dubhe.anvilcraft.util.GravityManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;

/** 下落铁砧到达前向压盖发送预告，并在接触高度确认压制。 */
public final class CatalyticPressAnvilEvents {
    private static final double LOOK_AHEAD = 10.0D;
    private static final double CONTACT_MARGIN = 0.1D;
    private static final double AIR_DRAG = 0.98D;

    private CatalyticPressAnvilEvents() {
    }

    public static void beforeFallingAnvilTick(FallingBlockEntity anvil) {
        if (!(anvil.level() instanceof ServerLevel level)
            || anvil.getDeltaMovement().y >= -1.0E-4D
            || !isAnvil(anvil)
            || !hasDownwardPressingFace(anvil)) {
            return;
        }
        AABB box = anvil.getBoundingBox();
        AABB search = new AABB(
            box.minX - 0.05D, box.minY - LOOK_AHEAD, box.minZ - 0.05D,
            box.maxX + 0.05D, box.minY + 0.25D, box.maxZ + 0.05D
        );
        Set<CatalyticPressLidEntity> lids = new HashSet<>(
            level.getEntitiesOfClass(CatalyticPressLidEntity.class, search, CatalyticPressLidEntity::isReady)
        );
        int minX = Mth.floor(search.minX);
        int maxX = Mth.floor(search.maxX - 1.0E-6D);
        int minY = Mth.floor(search.minY);
        int maxY = Mth.floor(search.maxY - 1.0E-6D);
        int minZ = Mth.floor(search.minZ);
        int maxZ = Mth.floor(search.maxZ - 1.0E-6D);
        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            if (level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
                && bonded.getOrCreateRenderEntity() instanceof CatalyticPressLidEntity lid
                && lid.isReady()) {
                lids.add(lid);
            }
        }
        double notificationDistance = expectedDropDistance(anvil, CatalyticPressLidEntity.PRESS_ANIMATION_TICKS)
            + CONTACT_MARGIN;
        double contactAllowance = expectedDropDistance(anvil, 1) + CONTACT_MARGIN;
        for (CatalyticPressLidEntity lid : lids) {
            if (lid.getOrientation().attachmentFace() != Direction.UP) continue;
            AABB lidBox = lid.getBoundingBox();
            if (box.maxX <= lidBox.minX || box.minX >= lidBox.maxX
                || box.maxZ <= lidBox.minZ || box.minZ >= lidBox.maxZ) continue;
            double distance = box.minY - (lid.getY() + 1.0D);
            if (distance < -0.5D || distance > LOOK_AHEAD) continue;
            if (distance <= notificationDistance) lid.notifyIncomingAnvil();
            if (distance <= contactAllowance) lid.confirmAnvilPress();
        }
    }

    private static double expectedDropDistance(FallingBlockEntity anvil, int ticks) {
        double velocityY = anvil.getDeltaMovement().y;
        double gravityY = anvil.isNoGravity() || AccelerateManager.isControlledByRing(anvil)
            ? 0.0D
            : GravityManager.getNetGravityVectorForFallingBlock(anvil).y;
        double distance = 0.0D;
        for (int tick = 0; tick < ticks; tick++) {
            velocityY += gravityY;
            distance += Math.max(0.0D, -velocityY);
            velocityY *= AIR_DRAG;
        }
        return distance;
    }

    private static boolean isAnvil(FallingBlockEntity entity) {
        if (entity instanceof FallingGiantAnvilEntity) return true;
        if (entity.getBlockState().is(BlockTags.ANVIL)
            || entity.getBlockState().getBlock() instanceof GiantAnvilBlock) return true;
        return entity instanceof AbstractPlasticEntity plastic && plastic.getDisplayState().is(BlockTags.ANVIL);
    }

    private static boolean hasDownwardPressingFace(FallingBlockEntity anvil) {
        return !(anvil instanceof AbstractPlasticEntity plastic)
            || plastic.getOrientation().worldDirection(Direction.DOWN) == Direction.DOWN;
    }
}
