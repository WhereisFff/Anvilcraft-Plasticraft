package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveTransit;
import dev.anvilcraft.plasticraft.init.ModAttachments;
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

    private PlasticEntityRenderTransforms() {
    }

    /** 应用实体变换前移除烘焙模型的水平旋转。 */
    public static BlockState canonicalize(BlockState state) {
        Objects.requireNonNull(state, "state");
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
            ? state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
            : state;
    }

    /**
     * 从 Entity 的底面中心原点移动到方块视觉中心，应用离散朝向，
     * 随后还原方块模型的 [0, 1] 立方体原点。
     */
    public static void apply(PoseStack pose, AbstractPlasticEntity entity) {
        apply(pose, entity, entity.getOrientation());
    }

    public static void apply(PoseStack pose, AbstractPlasticEntity entity, float partialTick) {
        AdhesiveTransit transit = entity.getExistingDataOrNull(ModAttachments.ADHESIVE_TRANSIT.get());
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

        pose.translate(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        Direction face = orientation.attachmentFace();
        double faceSize = face.getAxis() == Direction.Axis.Y ? entity.getBbHeight() : entity.getBbWidth();
        double attachmentInset = Math.max(0.0D, (1.0D - faceSize) * 0.5D);
        // 从碰撞中心移回所占方块单元的中心。
        pose.translate(
            face.getStepX() * attachmentInset,
            face.getStepY() * attachmentInset,
            face.getStepZ() * attachmentInset
        );
        pose.mulPose(ROTATIONS[rotationIndex(orientation)]);
        pose.translate(-0.5D, -0.5D, -0.5D);
    }

    /** 以实体当前位置应用预览朝向，不将自由移动后的实体吸附回方块网格。 */
    public static void applyPreview(
        PoseStack pose,
        AbstractPlasticEntity entity,
        PlasticEntityOrientation orientation
    ) {
        apply(pose, entity, orientation);
    }

    /** 将世界坐标轴模型的中心放到实体碰撞箱中心。 */
    public static void applyWorldAlignedPreview(PoseStack pose, AbstractPlasticEntity entity) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(entity, "entity");
        pose.translate(-0.5D, entity.getBbHeight() * 0.5D - 0.5D, -0.5D);
    }

    /** 在已经移动到方块中心的 PoseStack 上应用实体的离散朝向。 */
    public static void rotate(PoseStack pose, PlasticEntityOrientation orientation) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(orientation, "orientation");
        pose.mulPose(ROTATIONS[rotationIndex(orientation)]);
    }

    /**
     * Returns the model's semantic bottom-center offset from the entity position.
     * The entity position is always the world-Y bottom-center of its collision box,
     * which is not the model bottom after a wall or ceiling rotation.
     */
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
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(localFace, "localFace");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(target, "target");
        float clamped = Math.clamp(progress, 0.0F, 1.0F);
        Vec3 startOffset = attachmentOffset(entity, start.attachmentFace());
        Vec3 targetOffset = attachmentOffset(entity, target.attachmentFace());
        Vec3 attachmentOffset = startOffset.lerp(targetOffset, clamped);
        Quaternionf rotation = new Quaternionf(ROTATIONS[rotationIndex(start)])
            .slerp(ROTATIONS[rotationIndex(target)], clamped);
        Vector3f localFaceCenter = new Vector3f(
            localFace.getStepX() * 0.5F,
            localFace.getStepY() * 0.5F,
            localFace.getStepZ() * 0.5F
        ).rotate(rotation);
        return new Vec3(
            localFaceCenter.x(),
            entity.getBbHeight() * 0.5D + localFaceCenter.y(),
            localFaceCenter.z()
        ).add(attachmentOffset);
    }

    private static void applyInterpolated(
        PoseStack pose,
        AbstractPlasticEntity entity,
        PlasticEntityOrientation start,
        PlasticEntityOrientation target,
        float progress
    ) {
        pose.translate(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        Vec3 startOffset = attachmentOffset(entity, start.attachmentFace());
        Vec3 targetOffset = attachmentOffset(entity, target.attachmentFace());
        Vec3 offset = startOffset.lerp(targetOffset, progress);
        pose.translate(offset.x, offset.y, offset.z);
        Quaternionf rotation = new Quaternionf(ROTATIONS[rotationIndex(start)])
            .slerp(ROTATIONS[rotationIndex(target)], progress);
        pose.mulPose(rotation);
        pose.translate(-0.5D, -0.5D, -0.5D);
    }

    private static Vec3 attachmentOffset(AbstractPlasticEntity entity, Direction face) {
        double faceSize = face.getAxis() == Direction.Axis.Y ? entity.getBbHeight() : entity.getBbWidth();
        double attachmentInset = Math.max(0.0D, (1.0D - faceSize) * 0.5D);
        return Vec3.atLowerCornerOf(face.getNormal()).scale(attachmentInset);
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
}
