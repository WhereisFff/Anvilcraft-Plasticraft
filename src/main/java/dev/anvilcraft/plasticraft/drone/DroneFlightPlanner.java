package dev.anvilcraft.plasticraft.drone;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 单架无人机的基础三维寻路:先直线扫掠 0.5 碰撞箱,受阻后在路径包围盒膨胀窗口内做有预算的 A*。
 * 不复用树脂牵引规划器,也不做多机预约。
 */
public final class DroneFlightPlanner {
    public static final double SPEED = 0.25D;
    private static final int WINDOW = 12;
    private static final int NODE_BUDGET = 4096;
    private static final double MAX_STRAIGHT = 128.0D;

    private DroneFlightPlanner() {
    }

    public static List<Vec3> plan(Entity drone, Vec3 goal) {
        Vec3 start = drone.position();
        if (start.distanceToSqr(goal) < 0.04D) return List.of(goal);
        if (start.distanceTo(goal) > MAX_STRAIGHT) return List.of(goal);
        if (isClear(drone, start, goal)) return List.of(goal);
        List<Vec3> path = astar(drone, start, goal);
        return path.isEmpty() ? List.of(goal) : path;
    }

    public static double length(List<Vec3> path, Vec3 from) {
        double total = 0.0D;
        Vec3 previous = from;
        for (Vec3 point : path) {
            total += previous.distanceTo(point);
            previous = point;
        }
        return total;
    }

    public static DroneEnergyModel.Quote quote(List<Vec3> path, Vec3 from, int instantActions) {
        double distance = length(path, from);
        int airTicks = (int) Math.ceil(distance / SPEED) + 20;
        return new DroneEnergyModel.Quote(airTicks, distance, instantActions);
    }

    /**
     * 沿直线按 0.5 格步进扫掠碰撞箱。禁止用起终点包围盒一次判定:
     * 那样会把地面整段落进检测,也会在 GameTest 里对远距假玩家扫出超大 AABB。
     */
    private static boolean isClear(Entity drone, Vec3 from, Vec3 to) {
        double distance = from.distanceTo(to);
        if (distance < 1.0E-4D) return true;
        if (distance > MAX_STRAIGHT) return false;
        int steps = Math.max(1, (int) Math.ceil(distance / 0.5D));
        Vec3 previous = from;
        for (int step = 1; step <= steps; step++) {
            Vec3 point = from.lerp(to, (double) step / (double) steps);
            AABB swept = boxAt(drone, previous).minmax(boxAt(drone, point));
            if (!drone.level().noCollision(drone, swept)) return false;
            previous = point;
        }
        return true;
    }

    private static AABB boxAt(Entity drone, Vec3 pos) {
        AABB current = drone.getBoundingBox();
        Vec3 delta = pos.subtract(drone.position());
        return current.move(delta);
    }

    private static List<Vec3> astar(Entity drone, Vec3 start, Vec3 goal) {
        Level level = drone.level();
        BlockPos startPos = BlockPos.containing(start);
        BlockPos goalPos = BlockPos.containing(goal);
        int minX = Math.min(startPos.getX(), goalPos.getX()) - WINDOW;
        int maxX = Math.max(startPos.getX(), goalPos.getX()) + WINDOW;
        int minY = Math.min(startPos.getY(), goalPos.getY()) - WINDOW;
        int maxY = Math.max(startPos.getY(), goalPos.getY()) + WINDOW;
        int minZ = Math.min(startPos.getZ(), goalPos.getZ()) - WINDOW;
        int maxZ = Math.max(startPos.getZ(), goalPos.getZ()) + WINDOW;
        Map<Long, Long> cameFrom = new HashMap<>();
        Map<Long, Double> cost = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        long startKey = startPos.asLong();
        cost.put(startKey, 0.0D);
        open.add(new Node(startPos, 0.0D, heuristic(startPos, goalPos)));
        int expansions = 0;
        BlockPos found = null;
        while (!open.isEmpty() && expansions < NODE_BUDGET) {
            Node current = open.poll();
            expansions++;
            if (current.pos().equals(goalPos) || current.pos().distManhattan(goalPos) <= 1) {
                found = current.pos();
                break;
            }
            for (BlockPos next : neighbors(current.pos())) {
                if (next.getX() < minX || next.getX() > maxX
                    || next.getY() < minY || next.getY() > maxY
                    || next.getZ() < minZ || next.getZ() > maxZ) {
                    continue;
                }
                Vec3 nextCenter = new Vec3(next.getX() + 0.5D, next.getY(), next.getZ() + 0.5D);
                if (!level.noCollision(drone, boxAt(drone, nextCenter))) continue;
                double nextCost = cost.get(current.pos().asLong()) + 1.0D;
                long nextKey = next.asLong();
                if (nextCost >= cost.getOrDefault(nextKey, Double.POSITIVE_INFINITY)) continue;
                cost.put(nextKey, nextCost);
                cameFrom.put(nextKey, current.pos().asLong());
                open.add(new Node(next, nextCost, nextCost + heuristic(next, goalPos)));
            }
        }
        if (found == null) return List.of();
        List<Vec3> points = new ArrayList<>();
        long cursor = found.asLong();
        while (cursor != startKey) {
            BlockPos pos = BlockPos.of(cursor);
            points.add(new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D));
            Long previous = cameFrom.get(cursor);
            if (previous == null) break;
            cursor = previous;
        }
        Collections.reverse(points);
        points.add(goal);
        return points;
    }

    private static int heuristic(BlockPos from, BlockPos to) {
        return from.distManhattan(to);
    }

    private static BlockPos[] neighbors(BlockPos pos) {
        return new BlockPos[] {
            pos.above(),
            pos.below(),
            pos.north(),
            pos.south(),
            pos.east(),
            pos.west()
        };
    }

    private record Node(BlockPos pos, double cost, double score) {
    }
}
