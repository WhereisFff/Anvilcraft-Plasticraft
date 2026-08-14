package dev.anvilcraft.plasticraft.allay;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** 沿规划路点移动戴帽悦灵;到达后保持悬停等待工具执行器。 */
public final class AllayFlightNavigator {
    private final List<Vec3> waypoints = new ArrayList<>();
    private int index;

    public void setPath(List<Vec3> path) {
        this.waypoints.clear();
        this.waypoints.addAll(path);
        this.index = 0;
    }

    public void clear() {
        this.waypoints.clear();
        this.index = 0;
    }

    public boolean hasPath() {
        return this.index < this.waypoints.size();
    }

    public boolean endsNear(Vec3 goal) {
        if (this.waypoints.isEmpty()) return false;
        return this.waypoints.getLast().distanceToSqr(goal) < 0.09D;
    }

    public boolean follow(Entity worker) {
        if (!this.hasPath()) {
            worker.setDeltaMovement(Vec3.ZERO);
            return false;
        }
        Vec3 target = this.waypoints.get(this.index);
        Vec3 delta = target.subtract(worker.position());
        if (delta.lengthSqr() < 0.09D) {
            this.index++;
            if (!this.hasPath()) {
                worker.setDeltaMovement(Vec3.ZERO);
                return false;
            }
            target = this.waypoints.get(this.index);
            delta = target.subtract(worker.position());
        }
        double speed = Math.min(AllayFlightPlanner.SPEED, delta.length());
        if (delta.lengthSqr() < 1.0E-8D) {
            worker.setDeltaMovement(Vec3.ZERO);
            return true;
        }
        worker.setDeltaMovement(delta.normalize().scale(speed));
        worker.setYRot((float) (Math.toDegrees(Math.atan2(-delta.x, delta.z))));
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Index", this.index);
        ListTag points = new ListTag();
        for (Vec3 point : this.waypoints) {
            CompoundTag pointTag = new CompoundTag();
            pointTag.putDouble("X", point.x);
            pointTag.putDouble("Y", point.y);
            pointTag.putDouble("Z", point.z);
            points.add(pointTag);
        }
        tag.put("Points", points);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.clear();
        this.index = tag.getInt("Index");
        ListTag points = tag.getList("Points", Tag.TAG_COMPOUND);
        for (int entry = 0; entry < points.size(); entry++) {
            CompoundTag pointTag = points.getCompound(entry);
            this.waypoints.add(new Vec3(pointTag.getDouble("X"), pointTag.getDouble("Y"), pointTag.getDouble("Z")));
        }
    }
}
