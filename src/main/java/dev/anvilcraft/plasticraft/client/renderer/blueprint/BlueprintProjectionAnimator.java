package dev.anvilcraft.plasticraft.client.renderer.blueprint;

import dev.anvilcraft.plasticraft.blueprint.BlueprintPlacement;
import net.minecraft.Util;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 部署会话投影的姿态插值:跟随准星时平滑追上锚点,旋转与镜像在约 220 ms 内过渡,
 * 避免方块、流体和实体瞬间跳到目标姿态。已放置任务不走这里,仍按最终网格绘制。
 */
final class BlueprintProjectionAnimator {
    private static final long TRANSFORM_MILLIS = 220L;
    private static final float ANCHOR_FOLLOW = 14.0F;

    private @Nullable String hash;
    private @Nullable BlueprintPlacement baked;
    private @Nullable BlueprintPlacement target;
    private Vec3i size = Vec3i.ZERO;
    private Vec3 displayedAnchor = Vec3.ZERO;
    private Vec3 displayedCenter = Vec3.ZERO;
    private float displayedYaw;
    private float displayedMirrorX = 1.0F;
    private float displayedMirrorZ = 1.0F;
    private boolean tweening;
    private long tweenStart;
    private Vec3 fromAnchor = Vec3.ZERO;
    private Vec3 toAnchor = Vec3.ZERO;
    private float fromYaw;
    private float toYaw;
    private float fromMirrorX = 1.0F;
    private float toMirrorX = 1.0F;
    private float fromMirrorZ = 1.0F;
    private float toMirrorZ = 1.0F;
    private Vec3 fromCenter = Vec3.ZERO;
    private Vec3 toCenter = Vec3.ZERO;
    private long lastMillis = Util.getMillis();

    void clear() {
        this.hash = null;
        this.baked = null;
        this.target = null;
        this.tweening = false;
        this.lastMillis = Util.getMillis();
    }

    static Pose immediate(BlueprintPlacement placement, Vec3i size) {
        Vec3 center = centerOf(placement, size);
        return new Pose(
            placement,
            Vec3.atLowerCornerOf(placement.anchor()),
            center,
            center,
            0.0F,
            1.0F,
            1.0F
        );
    }

    Pose tick(String hash, BlueprintPlacement target, Vec3i size, float deltaSeconds) {
        long now = Util.getMillis();
        float dt = Math.min(0.05F, (now - this.lastMillis) / 1000.0F);
        this.lastMillis = now;
        if (deltaSeconds > 0.0F) {
            dt = Math.min(0.05F, deltaSeconds);
        }
        this.size = size;
        if (!hash.equals(this.hash) || this.baked == null || this.target == null) {
            this.snap(hash, target, size);
            return this.pose();
        }
        boolean transformChanged = this.target.rotation() != target.rotation()
            || this.target.mirror() != target.mirror();
        if (transformChanged) {
            this.beginTween(now, target, size);
        }
        this.target = target;
        if (this.tweening) {
            this.toAnchor = Vec3.atLowerCornerOf(target.anchor());
            this.toCenter = centerOf(target, size);
            float raw = (now - this.tweenStart) / (float) TRANSFORM_MILLIS;
            float t = ease(Mth.clamp(raw, 0.0F, 1.0F));
            this.displayedAnchor = this.fromAnchor.lerp(this.toAnchor, t);
            this.displayedCenter = this.fromCenter.lerp(this.toCenter, t);
            this.displayedYaw = lerpAngle(this.fromYaw, this.toYaw, t);
            this.displayedMirrorX = Mth.lerp(t, this.fromMirrorX, this.toMirrorX);
            this.displayedMirrorZ = Mth.lerp(t, this.fromMirrorZ, this.toMirrorZ);
            if (raw >= 1.0F) {
                this.tweening = false;
                this.baked = target;
                this.displayedAnchor = this.toAnchor;
                this.displayedCenter = this.toCenter;
                this.displayedYaw = this.toYaw;
                this.displayedMirrorX = this.toMirrorX;
                this.displayedMirrorZ = this.toMirrorZ;
            }
        } else {
            this.baked = target;
            this.displayedYaw = target.yawDegrees();
            this.displayedMirrorX = target.mirrorX();
            this.displayedMirrorZ = target.mirrorZ();
            Vec3 targetAnchor = Vec3.atLowerCornerOf(target.anchor());
            float follow = 1.0F - (float) Math.exp(-ANCHOR_FOLLOW * dt);
            this.displayedAnchor = this.displayedAnchor.lerp(targetAnchor, follow);
            this.displayedCenter = centerOf(this.baked, size)
                .add(this.displayedAnchor.subtract(Vec3.atLowerCornerOf(this.baked.anchor())));
        }
        return this.pose();
    }

    private void beginTween(long now, BlueprintPlacement target, Vec3i size) {
        this.fromAnchor = this.displayedAnchor;
        this.fromCenter = this.displayedCenter;
        this.fromYaw = this.displayedYaw;
        this.fromMirrorX = this.displayedMirrorX;
        this.fromMirrorZ = this.displayedMirrorZ;
        this.toAnchor = Vec3.atLowerCornerOf(target.anchor());
        this.toCenter = centerOf(target, size);
        this.toYaw = target.yawDegrees();
        this.toMirrorX = target.mirrorX();
        this.toMirrorZ = target.mirrorZ();
        this.tweening = true;
        this.tweenStart = now;
    }

    private void snap(String hash, BlueprintPlacement target, Vec3i size) {
        this.hash = hash;
        this.baked = target;
        this.target = target;
        this.size = size;
        this.tweening = false;
        this.displayedAnchor = Vec3.atLowerCornerOf(target.anchor());
        this.displayedCenter = centerOf(target, size);
        this.displayedYaw = target.yawDegrees();
        this.displayedMirrorX = target.mirrorX();
        this.displayedMirrorZ = target.mirrorZ();
    }

    private Pose pose() {
        BlueprintPlacement mesh = this.baked;
        if (mesh == null) {
            throw new IllegalStateException("blueprint projection animator has no baked placement");
        }
        return new Pose(
            mesh,
            this.displayedAnchor,
            this.displayedCenter,
            centerOf(mesh, this.size),
            this.displayedYaw - mesh.yawDegrees(),
            this.displayedMirrorX * mesh.mirrorX(),
            this.displayedMirrorZ * mesh.mirrorZ()
        );
    }

    private static Vec3 centerOf(BlueprintPlacement placement, Vec3i size) {
        AABB box = AABB.of(placement.bounds(size));
        return box.getCenter();
    }

    private static float ease(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private static float lerpAngle(float from, float to, float t) {
        return from + Mth.wrapDegrees(to - from) * t;
    }

    record Pose(
        BlueprintPlacement mesh,
        Vec3 displayedAnchor,
        Vec3 displayedCenter,
        Vec3 bakedCenter,
        float extraYaw,
        float extraMirrorX,
        float extraMirrorZ
    ) {
        Vec3 worldOf(Vec3 bakedWorld) {
            Vec3 relative = bakedWorld.subtract(this.bakedCenter);
            double x = relative.x * this.extraMirrorX;
            double y = relative.y;
            double z = relative.z * this.extraMirrorZ;
            double rad = Math.toRadians(this.extraYaw);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            double nx = x * cos + z * sin;
            double nz = -x * sin + z * cos;
            return this.displayedCenter.add(nx, y, nz);
        }
    }
}
