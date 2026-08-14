package dev.anvilcraft.plasticraft.allay;

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
 * 单只戴帽悦灵的基础三维寻路:先按自身碰撞箱直线扫掠,受阻后在路径包围盒膨胀窗口内做有预算的 A*。
 * 不复用树脂牵引规划器,也不做多机预约。
 */
public final class AllayFlightPlanner {
    public static final double SPEED = 0.25D;
    private static final int WINDOW = 12;
    private static final int NODE_BUDGET = 4096;
    private static final double MAX_STRAIGHT = 128.0D;

    private AllayFlightPlanner() {
    }

    public static List<Vec3> plan(Entity worker, Vec3 goal) {
        Vec3 actual = worker.position();
        Vec3 start = snapToFree(worker, actual);
        Vec3 safeGoal = snapToFree(worker, goal);
        List<Vec3> path;
        if (start.distanceToSqr(safeGoal) < 0.04D) {
            path = List.of(safeGoal);
        } else if (start.distanceTo(safeGoal) > MAX_STRAIGHT) {
            path = isClear(worker, start, safeGoal) ? List.of(safeGoal) : List.of();
        } else if (isClear(worker, start, safeGoal)) {
            path = List.of(safeGoal);
        } else {
            path = astar(worker, start, safeGoal);
            if (path.isEmpty()) {
                path = elevate(worker, start, safeGoal);
            }
        }
        return escape(actual, start, path);
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

    private static Vec3 snapToFree(Entity worker, Vec3 goal) {
        if (worker.level().noCollision(worker, boxAt(worker, goal))) return goal;
        BlockPos origin = BlockPos.containing(goal);
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (int y = -1; y <= 4; y++) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    Vec3 candidate = new Vec3(
                        origin.getX() + x + 0.5D,
                        origin.getY() + y,
                        origin.getZ() + z + 0.5D
                    );
                    if (!worker.level().noCollision(worker, boxAt(worker, candidate))) continue;
                    double dist = candidate.distanceToSqr(goal);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = candidate;
                    }
                }
            }
        }
        return best != null ? best : goal;
    }

    private static List<Vec3> escape(Vec3 actual, Vec3 start, List<Vec3> path) {
        if (path.isEmpty() || start.distanceToSqr(actual) < 0.04D) return path;
        List<Vec3> escaped = new ArrayList<>(path.size() + 1);
        escaped.add(start);
        escaped.addAll(path);
        return escaped;
    }

    private static List<Vec3> elevate(Entity worker, Vec3 start, Vec3 goal) {
        for (int rise = 2; rise <= 8; rise++) {
            Vec3 up = start.add(0.0D, rise, 0.0D);
            Vec3 aboveGoal = goal.add(0.0D, rise, 0.0D);
            if (!isClear(worker, start, up)) continue;
            if (isClear(worker, up, aboveGoal) && isClear(worker, aboveGoal, goal)) {
                return List.of(up, aboveGoal, goal);
            }
            Vec3 over = new Vec3(goal.x, start.y + rise, goal.z);
            if (isClear(worker, up, over) && isClear(worker, over, aboveGoal) && isClear(worker, aboveGoal, goal)) {
                return List.of(up, over, aboveGoal, goal);
            }
        }
        return List.of();
    }

    private static boolean isClear(Entity worker, Vec3 from, Vec3 to) {
        double distance = from.distanceTo(to);
        if (distance < 1.0E-4D) return true;
        if (distance > MAX_STRAIGHT) return false;
        int steps = Math.max(1, (int) Math.ceil(distance / 0.5D));
        Vec3 previous = from;
        for (int step = 1; step <= steps; step++) {
            Vec3 point = from.lerp(to, (double) step / (double) steps);
            AABB swept = boxAt(worker, previous).minmax(boxAt(worker, point));
            if (!worker.level().noCollision(worker, swept)) return false;
            previous = point;
        }
        return true;
    }

    private static AABB boxAt(Entity worker, Vec3 pos) {
        AABB current = worker.getBoundingBox();
        Vec3 delta = pos.subtract(worker.position());
        return current.move(delta);
    }

    private static List<Vec3> astar(Entity worker, Vec3 start, Vec3 goal) {
        Level level = worker.level();
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
                if (!level.noCollision(worker, boxAt(worker, nextCenter))) continue;
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
