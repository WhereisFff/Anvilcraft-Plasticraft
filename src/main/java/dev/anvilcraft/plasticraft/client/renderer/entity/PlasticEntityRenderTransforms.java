package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveTransit;
import dev.anvilcraft.plasticraft.init.PlasticraftAttachments;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/** 仅客户端使用的变换，用于按 24 种朝向渲染标准塑料模型。 */
public final class PlasticEntityRenderTransforms {
    private static final int TURNS_PER_FACE = 4;
    private static final Quaternionf[] ROTATIONS = createRotations();
    private static final ThreadLocal<RenderOverride> RENDER_OVERRIDE = new ThreadLocal<>();

    private PlasticEntityRenderTransforms() {
    }

    /** 应用实体变换前移除烘焙模型的水平旋转。 */
    public static BlockState canonicalize(BlockState state) {
        Objects.requireNonNull(state, "state");
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
            ? state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
            : state;
    }

    /** 按实体几何声明的枢轴和原点应用离散朝向。 */
    public static void apply(PoseStack pose, AbstractPlasticEntity entity) {
        apply(pose, entity, entity.getOrientation());
    }

    public static void apply(PoseStack pose, AbstractPlasticEntity entity, float partialTick) {
        RenderOverride override = RENDER_OVERRIDE.get();
        if (override != null && override.entity() == entity) {
            applyInterpolated(pose, entity, override.from(), override.to(), override.progress());
            return;
        }
        AbstractPlasticEntity.HammerRotationAnimation hammerAnimation =
            entity.getHammerRotationAnimation(partialTick);
        if (hammerAnimation != null) {
            applyInterpolated(
                pose,
                entity,
                hammerAnimation.from(),
                hammerAnimation.to(),
                hammerAnimation.progress()
            );
            return;
        }
        AdhesiveTransit transit = entity.getExistingDataOrNull(PlasticraftAttachments.ADHESIVE_TRANSIT.get());
        if (transit == null || !transit.plastic()) {
            apply(pose, entity, entity.getOrientation());
            return;
        }
        applyInterpolated(
            pose,
            entity,
            PlasticEntityOrientation.unpack(transit.startOrientation()),
            PlasticEntityOrientation.unpack(transit.targetOrientation()),
            (float) transit.rotationProgress(entity.level().getGameTime(), partialTick)
        );
    }

    public static void apply(
        PoseStack pose,
        AbstractPlasticEntity entity,
        PlasticEntityOrientation orientation
    ) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(orientation, "orientation");

        Vec3 pivot = entity.plasticraft$getGeometry().rotationPivot();
        Vec3 origin = entity.plasticraft$getGeometry().entityOrigin();
        pose.translate(pivot.x - origin.x, pivot.y - origin.y, pivot.z - origin.z);
        pose.mulPose(ROTATIONS[rotationIndex(orientation)]);
        pose.translate(-pivot.x, -pivot.y, -pivot.z);
    }

    /** 以实体当前位置应用预览朝向，不将自由移动后的实体吸附回方块网格。 */
    public static void applyPreview(
        PoseStack pose,
        AbstractPlasticEntity entity,
        PlasticEntityOrientation orientation
    ) {
        apply(pose, entity, orientation);
    }

    /** 将未旋转模型的声明原点放到实体位置。 */
    public static void applyWorldAlignedPreview(PoseStack pose, AbstractPlasticEntity entity) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(entity, "entity");
        Vec3 origin = entity.plasticraft$getGeometry().entityOrigin();
        pose.translate(-origin.x, -origin.y, -origin.z);
    }

    /** 在已经移动到方块中心的 PoseStack 上应用实体的离散朝向。 */
    public static void rotate(PoseStack pose, PlasticEntityOrientation orientation) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(orientation, "orientation");
        pose.mulPose(ROTATIONS[rotationIndex(orientation)]);
    }

    public static RenderOverrideScope overrideRotation(
        AbstractPlasticEntity entity,
        PlasticEntityOrientation from,
        PlasticEntityOrientation to,
        float progress
    ) {
        RenderOverride previous = RENDER_OVERRIDE.get();
        RENDER_OVERRIDE.set(new RenderOverride(entity, from, to, Math.clamp(progress, 0.0F, 1.0F)));
        return new RenderOverrideScope(previous);
    }

    /** 返回模型局部底面中心相对实体位置的插值偏移。 */
    public static Vec3 bottomCenterOffset(
        AbstractPlasticEntity entity,
        PlasticEntityOrientation start,
        PlasticEntityOrientation target,
        float progress
    ) {
        return faceCenterOffset(entity, Direction.DOWN, start, target, progress);
    }

    /** 返回指定模型局部面的中心相对实体位置的插值偏移。 */
    public static Vec3 faceCenterOffset(
        AbstractPlasticEntity entity,
        Direction localFace,
        PlasticEntityOrientation start,
        PlasticEntityOrientation target,
        float progress
    ) {
        return facePointOffset(
            entity,
            entity.plasticraft$getGeometry().surfaceCenter(localFace),
            start,
            target,
            progress
        );
    }

    /** 返回胶合面对齐锚点相对实体位置的插值偏移。 */
    public static Vec3 faceAlignmentOffset(
        AbstractPlasticEntity entity,
        Direction localFace,
        PlasticEntityOrientation start,
        PlasticEntityOrientation target,
        float progress
    ) {
        return facePointOffset(
            entity,
            entity.plasticraft$getGeometry().faceAlignmentPoint(localFace),
            start,
            target,
            progress
        );
    }

    private static Vec3 facePointOffset(
        AbstractPlasticEntity entity,
        Vec3 localFaceCenter,
        PlasticEntityOrientation start,
        PlasticEntityOrientation target,
        float progress
    ) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(localFaceCenter, "localFaceCenter");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(target, "target");
        float clamped = Math.clamp(progress, 0.0F, 1.0F);
        Vec3 pivot = entity.plasticraft$getGeometry().rotationPivot();
        Vec3 origin = entity.plasticraft$getGeometry().entityOrigin();
        Quaternionf rotation = new Quaternionf(ROTATIONS[rotationIndex(start)])
            .slerp(ROTATIONS[rotationIndex(target)], clamped);
        Vector3f rotatedOffset = new Vector3f(
            (float) (localFaceCenter.x - pivot.x),
            (float) (localFaceCenter.y - pivot.y),
            (float) (localFaceCenter.z - pivot.z)
        ).rotate(rotation);
        return new Vec3(
            pivot.x - origin.x + rotatedOffset.x(),
            pivot.y - origin.y + rotatedOffset.y(),
            pivot.z - origin.z + rotatedOffset.z()
        );
    }

    private static void applyInterpolated(
        PoseStack pose,
        AbstractPlasticEntity entity,
        PlasticEntityOrientation start,
        PlasticEntityOrientation target,
        float progress
    ) {
        Vec3 pivot = entity.plasticraft$getGeometry().rotationPivot();
        Vec3 origin = entity.plasticraft$getGeometry().entityOrigin();
        pose.translate(pivot.x - origin.x, pivot.y - origin.y, pivot.z - origin.z);
        Quaternionf rotation = new Quaternionf(ROTATIONS[rotationIndex(start)])
            .slerp(ROTATIONS[rotationIndex(target)], progress);
        pose.mulPose(rotation);
        pose.translate(-pivot.x, -pivot.y, -pivot.z);
    }

    private static Quaternionf[] createRotations() {
        Quaternionf[] rotations = new Quaternionf[Direction.values().length * TURNS_PER_FACE];
        for (Direction face : Direction.values()) {
            for (int turn = 0; turn < TURNS_PER_FACE; turn++) {
                PlasticEntityOrientation orientation = new PlasticEntityOrientation(face, turn);
                rotations[rotationIndex(orientation)] = createRotation(orientation);
            }
        }
        return rotations;
    }

    private static Quaternionf createRotation(PlasticEntityOrientation orientation) {
        Direction xAxis = orientation.orthogonalAxis();
        Direction yAxis = orientation.attachmentFace();
        Direction zAxis = orientation.longAxis();
        Matrix3f basis = new Matrix3f()
            .setColumn(0, xAxis.getStepX(), xAxis.getStepY(), xAxis.getStepZ())
            .setColumn(1, yAxis.getStepX(), yAxis.getStepY(), yAxis.getStepZ())
            .setColumn(2, zAxis.getStepX(), zAxis.getStepY(), zAxis.getStepZ());
        return new Quaternionf().setFromNormalized(basis);
    }

    private static int rotationIndex(PlasticEntityOrientation orientation) {
        return orientation.attachmentFace().get3DDataValue() * TURNS_PER_FACE + orientation.quarterTurn();
    }

    private record RenderOverride(
        AbstractPlasticEntity entity,
        PlasticEntityOrientation from,
        PlasticEntityOrientation to,
        float progress
    ) {
    }

    public static final class RenderOverrideScope implements AutoCloseable {
        private final RenderOverride previous;
        private boolean closed;

        private RenderOverrideScope(RenderOverride previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (this.closed) return;
            this.closed = true;
            if (this.previous == null) {
                RENDER_OVERRIDE.remove();
            } else {
                RENDER_OVERRIDE.set(this.previous);
            }
        }
    }
}
