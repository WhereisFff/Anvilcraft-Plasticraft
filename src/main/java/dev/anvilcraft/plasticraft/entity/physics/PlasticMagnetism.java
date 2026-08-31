package dev.anvilcraft.plasticraft.entity.physics;

import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.dubhe.anvilcraft.AnvilCraft;
import dev.dubhe.anvilcraft.block.MagnetBlock;
import dev.dubhe.anvilcraft.init.block.ModBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** 可移动塑料实体的磁性节点状态和选择性点力行为。 */
public final class PlasticMagnetism {
    private static final double SOURCE_STRENGTH = 25.0D;
    private static final double MAX_ACCELERATION = 0.35D;
    private static final double EPSILON_SQR = 1.0E-8D;

    private PlasticMagnetism() {
    }

    public static Vec3 calculatePointGravity(AbstractPlasticEntity entity, double baseGravity) {
        if (!entity.anvilcraft$isMagnetized()) return Vec3.ZERO;
        int radius = Math.max(1, AnvilCraft.CONFIG.magnetAttractsDistance);
        Vec3 center = entity.plasticraft$getRotationCenter();
        BlockPos origin = BlockPos.containing(center);
        Vec3 result = Vec3.ZERO;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
            if (!entity.level().hasChunkAt(pos)) continue;
            BlockState state = entity.level().getBlockState(pos);
            if (!state.is(ModBlockTags.MAGNET) && !(state.getBlock() instanceof MagnetBlock)) continue;
            if (state.hasProperty(MagnetBlock.LIT) && state.getValue(MagnetBlock.LIT)) continue;
            Vec3 offset = pos.getCenter().subtract(center);
            double distanceSqr = offset.lengthSqr();
            if (distanceSqr > (double) radius * radius || distanceSqr <= EPSILON_SQR) continue;
            double distance = Math.sqrt(distanceSqr);
            double factor = Math.abs(baseGravity) * SOURCE_STRENGTH
                / (Math.max(distanceSqr, 1.0D) * distance);
            result = result.add(offset.scale(factor));
        }
        double length = result.length();
        return length > MAX_ACCELERATION ? result.scale(MAX_ACCELERATION / length) : result;
    }

}
