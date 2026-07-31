package dev.anvilcraft.plasticraft.entity.physics;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.dubhe.anvilcraft.api.giantanvil.ShockDropBehavior;
import dev.dubhe.anvilcraft.event.giantanvil.shock.ShockContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

/** 将树脂铁砧撼地产生的掉落物以四十五度方向抛到范围外。 */
public enum ResinShockDropBehavior implements ShockDropBehavior {
    INSTANCE;

    private static final ResourceLocation ID = AnvilcraftPlasticraft.of("resin_shock_drop");
    private static final double ITEM_GRAVITY = 0.04D;
    private static final double AIR_DRAG = 0.98D;
    private static final double OUTSIDE_MARGIN = 0.25D;
    private static final double MIN_FLIGHT_DISTANCE = 0.75D;
    private static final int SOLVER_ITERATIONS = 40;
    private static final int MAX_FLIGHT_TICKS = 200;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void drop(ShockContext context, BlockPos pos, ItemStack stack) {
        if (!(context.level() instanceof ServerLevel level)
            || stack.isEmpty()
            || !level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            return;
        }
        Vec3 origin = Vec3.atCenterOf(pos);
        Vec3 velocity = calculateLaunchVelocity(context, pos);
        ItemEntity item = new ItemEntity(
            level,
            origin.x,
            origin.y,
            origin.z,
            stack.copy(),
            velocity.x,
            velocity.y,
            velocity.z
        );
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    /**
     * 先沿中心到掉落位置的射线求方形边界交点，再反解物品实体带空气阻力的四十五度初速度。
     */
    public static Vec3 calculateLaunchVelocity(ShockContext context, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(context.centerPos().above());
        Vec3 origin = Vec3.atCenterOf(pos);
        double offsetX = origin.x - center.x;
        double offsetZ = origin.z - center.z;
        double horizontalLength = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);

        double directionX;
        double directionZ;
        if (horizontalLength < 1.0E-7D) {
            Direction fallback = ShockContext.HORIZONTAL[Math.floorMod(pos.hashCode(), ShockContext.HORIZONTAL.length)];
            directionX = fallback.getStepX();
            directionZ = fallback.getStepZ();
        } else {
            directionX = offsetX / horizontalLength;
            directionZ = offsetZ / horizontalLength;
        }

        double directionMaxComponent = Math.max(Math.abs(directionX), Math.abs(directionZ));
        double currentSquareRadius = Math.max(Math.abs(offsetX), Math.abs(offsetZ));
        double targetSquareRadius = context.getShockRadius() + 0.5D + OUTSIDE_MARGIN;
        double distanceToOutside = (targetSquareRadius - currentSquareRadius) / directionMaxComponent;
        double flightDistance = Math.max(MIN_FLIGHT_DISTANCE, distanceToOutside);
        double componentSpeed = calculateLaunchComponent(flightDistance);
        return new Vec3(directionX * componentSpeed, componentSpeed, directionZ * componentSpeed);
    }

    /** 返回使物品实体回到起始高度时水平移动指定距离的初速度分量。 */
    public static double calculateLaunchComponent(double horizontalDistance) {
        if (!Double.isFinite(horizontalDistance) || horizontalDistance <= 0.0D) return 0.0D;
        double low = 0.0D;
        double high = Math.max(0.1D, Math.sqrt(horizontalDistance * ITEM_GRAVITY));
        while (simulateHorizontalRange(high) < horizontalDistance && high < 4.0D) {
            high *= 2.0D;
        }
        for (int i = 0; i < SOLVER_ITERATIONS; i++) {
            double middle = (low + high) * 0.5D;
            if (simulateHorizontalRange(middle) < horizontalDistance) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return high;
    }

    /** 按物品实体的重力与空气阻力模拟回到起始高度前的水平射程。 */
    public static double simulateHorizontalRange(double componentSpeed) {
        if (!Double.isFinite(componentSpeed) || componentSpeed <= ITEM_GRAVITY) return 0.0D;
        double horizontal = 0.0D;
        double height = 0.0D;
        double horizontalVelocity = componentSpeed;
        double verticalVelocity = componentSpeed;
        boolean roseAboveOrigin = false;
        for (int tick = 0; tick < MAX_FLIGHT_TICKS; tick++) {
            verticalVelocity -= ITEM_GRAVITY;
            double nextHeight = height + verticalVelocity;
            if (!roseAboveOrigin && nextHeight <= 0.0D) return 0.0D;
            if (roseAboveOrigin && nextHeight <= 0.0D) {
                double fraction = height / (height - nextHeight);
                return horizontal + horizontalVelocity * fraction;
            }
            horizontal += horizontalVelocity;
            height = nextHeight;
            roseAboveOrigin |= height > 0.0D;
            horizontalVelocity *= AIR_DRAG;
            verticalVelocity *= AIR_DRAG;
        }
        return horizontal;
    }
}
