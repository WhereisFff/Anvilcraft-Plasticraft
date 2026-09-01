package dev.anvilcraft.plasticraft.entity.collision;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;

/** 按移动顺序保留尚未获服务端确认的客户端承载位移。 */
public final class PlasticCarrierPrediction {
    private static final double MOVEMENT_EPSILON = 1.0E-5D;
    // 原版 VecDeltaCodec 将实体坐标量化到 1/4096 格，两端舍入后的位移误差最多为一个量化步长。
    private static final double RECONCILIATION_EPSILON = 1.0D / 4096.0D + MOVEMENT_EPSILON;
    private static final int MAX_AXIS_SEGMENTS = 16;

    private final AxisPrediction x = new AxisPrediction();
    private final AxisPrediction y = new AxisPrediction();
    private final AxisPrediction z = new AxisPrediction();

    public void add(Vec3 movement) {
        Vec3 value = requireFinite(movement, "movement");
        this.x.add(value.x);
        this.y.add(value.y);
        this.z.add(value.z);
    }

    /**
     * 用一次服务端位置增量消费预测路径中最后一次到达该位置的前缀。
     * 返回值表示至少一个分量确认了仍在等待的非零行程。
     */
    public boolean reconcile(Vec3 serverMovement) {
        Vec3 value = requireFinite(serverMovement, "serverMovement");
        boolean confirmed = this.x.reconcile(value.x);
        confirmed |= this.y.reconcile(value.y);
        confirmed |= this.z.reconcile(value.z);
        return confirmed;
    }

    /** 仅在服务端位置发生非有限或超出正常同步范围的跳变时丢弃预测。 */
    public boolean resetIfDiscontinuous(Vec3 serverMovement, double maximumDistance) {
        Vec3 value = Objects.requireNonNull(serverMovement, "serverMovement");
        if (!Double.isFinite(maximumDistance) || maximumDistance <= 0.0D) {
            throw new IllegalArgumentException("maximumDistance must be finite and positive");
        }
        double movementLengthSqr = value.lengthSqr();
        if (Double.isFinite(movementLengthSqr)
            && movementLengthSqr <= maximumDistance * maximumDistance) {
            return false;
        }
        this.clear();
        return true;
    }

    public Vec3 pendingMovement() {
        return new Vec3(this.x.pending(), this.y.pending(), this.z.pending());
    }

    /** 返回仍待确认路径在三轴上的绝对行程之和。 */
    public double remainingTravel() {
        return this.x.travel() + this.y.travel() + this.z.travel();
    }

    public boolean isEmpty() {
        return this.x.isEmpty() && this.y.isEmpty() && this.z.isEmpty();
    }

    public void clear() {
        this.x.clear();
        this.y.clear();
        this.z.clear();
    }

    private static Vec3 requireFinite(Vec3 vector, String name) {
        Vec3 value = Objects.requireNonNull(vector, name);
        if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    private static final class AxisPrediction {
        private final Deque<Double> segments = new ArrayDeque<>();
        private double pending;
        private double travel;

        private void add(double movement) {
            if (Math.abs(movement) <= MOVEMENT_EPSILON) return;
            if (!this.segments.isEmpty()
                && Math.signum(this.segments.getLast()) == Math.signum(movement)) {
                double previous = this.segments.removeLast();
                this.pending -= previous;
                this.travel -= Math.abs(previous);
                movement += previous;
            }
            this.segments.addLast(movement);
            this.pending += movement;
            this.travel += Math.abs(movement);
            if (this.segments.size() > MAX_AXIS_SEGMENTS) this.compact();
        }

        private boolean reconcile(double serverMovement) {
            if (this.segments.isEmpty()) return false;
            Reconciliation reconciliation = this.findReconciliation(serverMovement);
            if (reconciliation == null) return false;
            this.apply(reconciliation);
            return true;
        }

        private Reconciliation findReconciliation(double serverMovement) {
            double next = this.pending;
            double tailTravel = 0.0D;
            double maximum = 0.0D;
            double minimum = 0.0D;
            Reconciliation positiveExtreme = null;
            Reconciliation negativeExtreme = null;
            int index = this.segments.size() - 1;
            Iterator<Double> iterator = this.segments.descendingIterator();
            while (iterator.hasNext()) {
                double segment = iterator.next();
                double previous = next - segment;
                if (between(serverMovement, previous, next)) {
                    double confirmedPosition = Math.clamp(
                        serverMovement,
                        Math.min(previous, next),
                        Math.max(previous, next)
                    );
                    double confirmedTravel = this.travel - tailTravel - Math.abs(next - confirmedPosition);
                    if (confirmedTravel > MOVEMENT_EPSILON) {
                        return new Reconciliation(index, next, confirmedPosition);
                    }
                }
                if (next > maximum + MOVEMENT_EPSILON) {
                    maximum = next;
                    positiveExtreme = new Reconciliation(index, next, next);
                }
                if (next < minimum - MOVEMENT_EPSILON) {
                    minimum = next;
                    negativeExtreme = new Reconciliation(index, next, next);
                }
                tailTravel += Math.abs(segment);
                next = previous;
                index--;
            }
            // 同向快照越过预测极值时只确认到极值；完全反向的快照不得破坏待确认路径。
            if (serverMovement > maximum + RECONCILIATION_EPSILON) return positiveExtreme;
            if (serverMovement < minimum - RECONCILIATION_EPSILON) return negativeExtreme;
            return null;
        }

        private void compact() {
            double movement = this.pending;
            this.segments.clear();
            if (Math.abs(movement) <= MOVEMENT_EPSILON) {
                this.pending = 0.0D;
                this.travel = 0.0D;
                return;
            }
            // 超长历史只保留当前预测终点，避免复杂碰撞产生的细碎换向使快照对账无界增长。
            this.segments.addLast(movement);
            this.travel = Math.abs(movement);
        }

        private void apply(Reconciliation reconciliation) {
            for (int index = 0; index < reconciliation.segmentIndex(); index++) {
                double segment = this.segments.removeFirst();
                this.pending -= segment;
                this.travel -= Math.abs(segment);
            }
            double segment = this.segments.removeFirst();
            this.pending -= segment;
            this.travel -= Math.abs(segment);
            double remaining = reconciliation.segmentEnd() - reconciliation.confirmedPosition();
            if (Math.abs(remaining) > MOVEMENT_EPSILON) {
                this.segments.addFirst(remaining);
                this.pending += remaining;
                this.travel += Math.abs(remaining);
            }
            this.normalize();
        }

        private static boolean between(double value, double first, double second) {
            return value >= Math.min(first, second) - RECONCILIATION_EPSILON
                && value <= Math.max(first, second) + RECONCILIATION_EPSILON;
        }

        private double pending() {
            return Math.abs(this.pending) <= MOVEMENT_EPSILON ? 0.0D : this.pending;
        }

        private double travel() {
            return Math.max(0.0D, this.travel);
        }

        private boolean isEmpty() {
            return this.segments.isEmpty();
        }

        private void clear() {
            this.segments.clear();
            this.pending = 0.0D;
            this.travel = 0.0D;
        }

        private void normalize() {
            if (Math.abs(this.pending) <= MOVEMENT_EPSILON) this.pending = 0.0D;
            if (this.travel <= MOVEMENT_EPSILON) this.travel = 0.0D;
        }

        private record Reconciliation(int segmentIndex, double segmentEnd, double confirmedPosition) {
        }
    }
}
