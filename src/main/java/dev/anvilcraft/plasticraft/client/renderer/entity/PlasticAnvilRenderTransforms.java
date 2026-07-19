package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticAnvilEntity;
import dev.anvilcraft.plasticraft.entity.PlasticAnvilOrientation;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

import java.util.Objects;

/** Client-only transforms for rendering a canonical plastic anvil model in any of its 24 orientations. */
public final class PlasticAnvilRenderTransforms {
    private static final int TURNS_PER_FACE = 4;
    private static final Quaternionf[] ROTATIONS = createRotations();

    private PlasticAnvilRenderTransforms() {
    }

    /** Removes the baked horizontal rotation before the entity transform is applied. */
    public static BlockState canonicalize(BlockState state) {
        Objects.requireNonNull(state, "state");
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
            ? state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
            : state;
    }

    /**
     * Moves from Entity's bottom-center origin to the visual block center, applies
     * the discrete orientation, then restores the block model's [0, 1] cube origin.
     */
    public static void apply(PoseStack pose, AbstractPlasticAnvilEntity entity) {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(entity, "entity");
        PlasticAnvilOrientation orientation = entity.getOrientation();
        Objects.requireNonNull(orientation, "orientation");

        pose.translate(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        Direction face = orientation.attachmentFace();
        double faceSize = face.getAxis() == Direction.Axis.Y ? entity.getBbHeight() : entity.getBbWidth();
        double attachmentInset = Math.max(0.0D, (1.0D - faceSize) * 0.5D);
        // Move from the collision center back to the occupied block-cell center.
        pose.translate(
            face.getStepX() * attachmentInset,
            face.getStepY() * attachmentInset,
            face.getStepZ() * attachmentInset
        );
        pose.mulPose(ROTATIONS[rotationIndex(orientation)]);
        pose.translate(-0.5D, -0.5D, -0.5D);
    }

    private static Quaternionf[] createRotations() {
        Quaternionf[] rotations = new Quaternionf[Direction.values().length * TURNS_PER_FACE];
        for (Direction face : Direction.values()) {
            for (int turn = 0; turn < TURNS_PER_FACE; turn++) {
                PlasticAnvilOrientation orientation = new PlasticAnvilOrientation(face, turn);
                rotations[rotationIndex(orientation)] = createRotation(orientation);
            }
        }
        return rotations;
    }

    private static Quaternionf createRotation(PlasticAnvilOrientation orientation) {
        Direction xAxis = orientation.orthogonalAxis();
        Direction yAxis = orientation.attachmentFace();
        Direction zAxis = orientation.longAxis();
        Matrix3f basis = new Matrix3f()
            .setColumn(0, xAxis.getStepX(), xAxis.getStepY(), xAxis.getStepZ())
            .setColumn(1, yAxis.getStepX(), yAxis.getStepY(), yAxis.getStepZ())
            .setColumn(2, zAxis.getStepX(), zAxis.getStepY(), zAxis.getStepZ());
        return new Quaternionf().setFromNormalized(basis);
    }

    private static int rotationIndex(PlasticAnvilOrientation orientation) {
        return orientation.attachmentFace().get3DDataValue() * TURNS_PER_FACE + orientation.quarterTurn();
    }
}
