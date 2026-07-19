package dev.anvilcraft.plasticraft.entity;

import dev.dubhe.anvilcraft.AnvilCraft;
import dev.dubhe.anvilcraft.api.event.UseMagnetEvent;
import dev.dubhe.anvilcraft.block.MagnetBlock;
import dev.dubhe.anvilcraft.init.block.ModBlockTags;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.item.MultitoolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Magnetic-node state and selective point-force behavior for movable plastic entities. */
public final class PlasticMagnetism {
    private static final double SOURCE_STRENGTH = 10.0D;
    private static final double MAX_ACCELERATION = 0.35D;
    private static final double EPSILON_SQR = 1.0E-8D;

    private PlasticMagnetism() {
    }

    public static boolean isMagnetTool(ItemStack stack) {
        return stack.is(ModItems.MAGNET)
            || stack.is(ModItems.MULTITOOL_ITEM) && MultitoolItem.getMode(stack) == MultitoolItem.MAGNET_MODE;
    }

    public static Vec3 calculatePointGravity(AbstractPlasticAnvilEntity entity, double baseGravity) {
        if (!entity.anvilcraft$isMagnetized()) return Vec3.ZERO;
        int radius = Math.max(1, AnvilCraft.CONFIG.magnetAttractsDistance);
        Vec3 center = entity.getBoundingBox().getCenter();
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

    public static void onUseMagnet(UseMagnetEvent event) {
        double radius = Math.max(0.0D, event.getAttractRadius());
        Vec3 playerCenter = event.getPlayer().getBoundingBox().getCenter();
        AABB area = new AABB(playerCenter, playerCenter).inflate(radius);
        for (AbstractPlasticAnvilEntity entity : event.getLevel().getEntitiesOfClass(
            AbstractPlasticAnvilEntity.class,
            area,
            Entity::isAlive
        )) {
            if (!entity.anvilcraft$isMagnetized()) continue;
            Vec3 offset = playerCenter.subtract(entity.getBoundingBox().getCenter());
            double distance = offset.length();
            if (distance < 0.05D) continue;
            double impulse = Mth.clamp(0.12D + distance * 0.04D, 0.12D, 0.45D);
            entity.setDeltaMovement(entity.getDeltaMovement().add(offset.scale(impulse / distance)));
            entity.hasImpulse = true;
            entity.hurtMarked = true;
        }
    }
}
