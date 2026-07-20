package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

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

    /** 将预览朝向放到实体切换附着面后实际会占据的位置。 */
    public static void applyPreview(
        PoseStack pose,
        AbstractPlasticEntity entity,
        PlasticEntityOrientation orientation
    ) {
        BlockPos occupiedPos = BlockPos.containing(entity.getBoundingBox().getCenter());
        Vec3 previewPosition = orientation.entityPosition(
            occupiedPos,
            entity.getBbWidth(),
            entity.getBbHeight()
        );
        Vec3 offset = previewPosition.subtract(entity.position());
        pose.translate(offset.x, offset.y, offset.z);
        apply(pose, entity, orientation);
    }

    /** 将预览坐标移动到占用方块原点，但不应用实体的离散朝向。 */
    public static void applyWorldAlignedPreview(PoseStack pose, AbstractPlasticEntity entity) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(entity, "entity");
        BlockPos occupiedPos = BlockPos.containing(entity.getBoundingBox().getCenter());
        Vec3 offset = Vec3.atLowerCornerOf(occupiedPos).subtract(entity.position());
        pose.translate(offset.x, offset.y, offset.z);
    }

    /** 在已经移动到方块中心的 PoseStack 上应用实体的离散朝向。 */
    public static void rotate(PoseStack pose, PlasticEntityOrientation orientation) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(orientation, "orientation");
        pose.mulPose(ROTATIONS[rotationIndex(orientation)]);
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
