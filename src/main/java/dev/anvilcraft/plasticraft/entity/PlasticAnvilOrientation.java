package dev.anvilcraft.plasticraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Discrete orientation and placement geometry shared by every plastic anvil variant.
 * The attachment face is the clicked face's outward normal: local model up points
 * along it, while the model bottom points back toward the supporting block.
 *
 * @param attachmentFace clicked face and world direction of local model up
 * @param quarterTurn    clockwise quarter turns around local model up, normalized to {@code [0, 3]}
 */
public record PlasticAnvilOrientation(Direction attachmentFace, int quarterTurn) {
    public static final PlasticAnvilOrientation DEFAULT = new PlasticAnvilOrientation(Direction.UP, 0);

    public static final double COLLISION_SIZE = 0.98D;
    public static final double COLLISION_HALF_SIZE = 0.49D;
    public static final double ATTACHMENT_INSET = 0.01D;

    private static final int FACE_MASK = 0b111;
    private static final int TURN_SHIFT = 3;
    private static final int TURN_MASK = 0b11;
    private static final int PACKED_MASK = FACE_MASK | (TURN_MASK << TURN_SHIFT);

    public PlasticAnvilOrientation {
        Objects.requireNonNull(attachmentFace, "attachmentFace");
        quarterTurn = Math.floorMod(quarterTurn, 4);
    }

    /**
     * Creates the intuitive placement orientation for a player. On floors and
     * ceilings this exactly matches vanilla anvil placement: the long axis is
     * the player's horizontal direction rotated clockwise. On a wall, that
     * screen-right direction is used when tangent to the wall; otherwise the
     * long axis is vertical.
     */
    public static PlasticAnvilOrientation forPlacement(Direction attachmentFace, Player player) {
        Direction playerDirection = player == null ? Direction.NORTH : player.getDirection();
        return forPlacement(attachmentFace, playerDirection);
    }

    public static PlasticAnvilOrientation forPlacement(
        Direction attachmentFace,
        Direction playerHorizontalDirection
    ) {
        Objects.requireNonNull(attachmentFace, "attachmentFace");
        Objects.requireNonNull(playerHorizontalDirection, "playerHorizontalDirection");

        Direction horizontalDirection = playerHorizontalDirection.getAxis() == Direction.Axis.Y
            ? Direction.NORTH
            : playerHorizontalDirection;
        Direction preferredLongAxis = horizontalDirection.getClockWise();
        if (preferredLongAxis.getAxis() == attachmentFace.getAxis()) {
            preferredLongAxis = Direction.UP;
        }
        return fromLongAxis(attachmentFace, preferredLongAxis);
    }

    /** Creates one of the four valid in-plane rotations from its desired long axis. */
    public static PlasticAnvilOrientation fromLongAxis(Direction attachmentFace, Direction longAxis) {
        Objects.requireNonNull(attachmentFace, "attachmentFace");
        Objects.requireNonNull(longAxis, "longAxis");
        if (attachmentFace.getAxis() == longAxis.getAxis()) {
            throw new IllegalArgumentException("Long axis must be perpendicular to the attachment face");
        }

        Direction candidate = zeroTurnLongAxis(attachmentFace);
        for (int turn = 0; turn < 4; turn++) {
            if (candidate == longAxis) {
                return new PlasticAnvilOrientation(attachmentFace, turn);
            }
            candidate = rotateClockwise(attachmentFace, candidate);
        }
        throw new IllegalArgumentException("Long axis is not a cardinal direction in the attachment plane");
    }

    /** Migrates the old floor-only representation whose horizontal facing was the rendered long axis. */
    public static PlasticAnvilOrientation fromLegacyState(BlockState state) {
        if (state == null || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return DEFAULT;
        }
        return fromLongAxis(Direction.UP, state.getValue(BlockStateProperties.HORIZONTAL_FACING));
    }

    /** Mirrors the horizontal part of this orientation into legacy block-state consumers. */
    public BlockState applyToState(BlockState state) {
        Objects.requireNonNull(state, "state");
        Direction longAxis = this.longAxis();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
            && longAxis.getAxis() != Direction.Axis.Y) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, longAxis);
        }
        return state;
    }

    /** World direction of the model's local positive Z axis. */
    public Direction longAxis() {
        Direction result = zeroTurnLongAxis(this.attachmentFace);
        for (int turn = 0; turn < this.quarterTurn; turn++) {
            result = rotateClockwise(this.attachmentFace, result);
        }
        return result;
    }

    /** World direction of the model's local positive X axis. */
    public Direction orthogonalAxis() {
        return cross(this.attachmentFace, this.longAxis());
    }

    /** Alias emphasizing that the local X axis is the cross product completing the model basis. */
    public Direction crossAxis() {
        return this.orthogonalAxis();
    }

    /**
     * Packs the 24 orientations into five bits. The low three bits are the
     * vanilla 3D direction id and the next two bits are the quarter turn.
     */
    public byte pack() {
        return (byte) (this.attachmentFace.get3DDataValue() | (this.quarterTurn << TURN_SHIFT));
    }

    /** Decodes network data, returning the canonical default for reserved or invalid values. */
    public static PlasticAnvilOrientation unpack(byte packed) {
        return unpack(Byte.toUnsignedInt(packed));
    }

    public static PlasticAnvilOrientation unpack(int packed) {
        if ((packed & ~PACKED_MASK) != 0) {
            return DEFAULT;
        }
        int faceId = packed & FACE_MASK;
        if (faceId >= Direction.values().length) {
            return DEFAULT;
        }
        return new PlasticAnvilOrientation(
            Direction.from3DDataValue(faceId),
            (packed >> TURN_SHIFT) & TURN_MASK
        );
    }

    /**
     * Returns the center of the 0.98 collision cube in the occupied block cell.
     * Its local bottom is flush with the supporting face, its four sides have a
     * 0.01 gap, and its local top has a 0.02 gap to the far cell boundary.
     */
    public Vec3 collisionCenter(BlockPos occupiedPos) {
        Objects.requireNonNull(occupiedPos, "occupiedPos");
        return Vec3.atCenterOf(occupiedPos).add(
            -this.attachmentFace.getStepX() * ATTACHMENT_INSET,
            -this.attachmentFace.getStepY() * ATTACHMENT_INSET,
            -this.attachmentFace.getStepZ() * ATTACHMENT_INSET
        );
    }

    /**
     * Returns the bottom-center position expected by {@link net.minecraft.world.entity.Entity#setPos(Vec3)}.
     * Entity positions remain bottom-center even when the rendered anvil is attached to a wall or ceiling.
     */
    public Vec3 entityPosition(BlockPos occupiedPos) {
        return this.collisionCenter(occupiedPos).subtract(0.0D, COLLISION_HALF_SIZE, 0.0D);
    }

    /** Converts a collision-box center back to the entity's bottom-center position. */
    public static Vec3 entityPositionFromCollisionCenter(Vec3 collisionCenter) {
        Objects.requireNonNull(collisionCenter, "collisionCenter");
        return collisionCenter.subtract(0.0D, COLLISION_HALF_SIZE, 0.0D);
    }

    private static Direction zeroTurnLongAxis(Direction attachmentFace) {
        return attachmentFace.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.UP;
    }

    private static Direction rotateClockwise(Direction axis, Direction direction) {
        return cross(direction, axis);
    }

    private static Direction cross(Direction first, Direction second) {
        int x = first.getStepY() * second.getStepZ() - first.getStepZ() * second.getStepY();
        int y = first.getStepZ() * second.getStepX() - first.getStepX() * second.getStepZ();
        int z = first.getStepX() * second.getStepY() - first.getStepY() * second.getStepX();
        Direction result = Direction.fromDelta(x, y, z);
        if (result == null) {
            throw new IllegalArgumentException("Directions must be perpendicular");
        }
        return result;
    }
}
