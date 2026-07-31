package dev.anvilcraft.plasticraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * 可移动塑料制品共用的离散朝向和放置几何。
 * 附着面是被点击面的外法线：模型局部向上方向沿此外法线，
 * 模型底部则指回支撑方块。
 *
 * @param attachmentFace 被点击的面，也是模型局部向上方向对应的世界方向
 * @param quarterTurn    绕模型局部向上方向顺时针旋转的四分之一圈数，归一化到 {@code [0, 3]}
 */
public record PlasticEntityOrientation(Direction attachmentFace, int quarterTurn) {
    public static final PlasticEntityOrientation DEFAULT = new PlasticEntityOrientation(Direction.UP, 0);
    public static final double ATTACHMENT_INSET = 0.0D;

    private static final int FACE_MASK = 0b111;
    private static final int TURN_SHIFT = 3;
    private static final int TURN_MASK = 0b11;
    private static final int PACKED_MASK = FACE_MASK | (TURN_MASK << TURN_SHIFT);

    public PlasticEntityOrientation {
        Objects.requireNonNull(attachmentFace, "attachmentFace");
        quarterTurn = Math.floorMod(quarterTurn, 4);
    }

    /**
     * 为玩家创建符合直觉的放置朝向。在地面和天花板上，此朝向与原版铁砧放置完全一致：
     * 长轴为玩家水平方向顺时针旋转后的方向。在墙面上，当屏幕右侧方向与墙面相切时使用该方向，
     * 否则长轴保持竖直。
     */
    public static PlasticEntityOrientation forPlacement(Direction attachmentFace, Player player) {
        Direction playerDirection = player == null ? Direction.NORTH : player.getDirection();
        return forPlacement(attachmentFace, playerDirection);
    }

    public static PlasticEntityOrientation forPlacement(
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

    /** 根据期望长轴创建四种有效平面内旋转之一。 */
    public static PlasticEntityOrientation fromLongAxis(Direction attachmentFace, Direction longAxis) {
        Objects.requireNonNull(attachmentFace, "attachmentFace");
        Objects.requireNonNull(longAxis, "longAxis");
        if (attachmentFace.getAxis() == longAxis.getAxis()) {
            throw new IllegalArgumentException("Long axis must be perpendicular to the attachment face");
        }

        Direction candidate = zeroTurnLongAxis(attachmentFace);
        for (int turn = 0; turn < 4; turn++) {
            if (candidate == longAxis) {
                return new PlasticEntityOrientation(attachmentFace, turn);
            }
            candidate = rotateClockwise(attachmentFace, candidate);
        }
        throw new IllegalArgumentException("Long axis is not a cardinal direction in the attachment plane");
    }

    /** 迁移仅适用于地面的旧表示，其中水平朝向就是渲染后的长轴。 */
    public static PlasticEntityOrientation fromLegacyState(BlockState state) {
        if (state == null || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return DEFAULT;
        }
        return fromLongAxis(Direction.UP, state.getValue(BlockStateProperties.HORIZONTAL_FACING));
    }

    /** 将此朝向的水平部分映射给旧方块状态使用方。 */
    public BlockState applyToState(BlockState state) {
        Objects.requireNonNull(state, "state");
        Direction longAxis = this.longAxis();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
            && longAxis.getAxis() != Direction.Axis.Y) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, longAxis);
        }
        return state;
    }

    /** 模型局部 Z 轴正方向对应的世界方向。 */
    public Direction longAxis() {
        Direction result = zeroTurnLongAxis(this.attachmentFace);
        for (int turn = 0; turn < this.quarterTurn; turn++) {
            result = rotateClockwise(this.attachmentFace, result);
        }
        return result;
    }

    /** 模型局部 X 轴正方向对应的世界方向。 */
    public Direction orthogonalAxis() {
        return cross(this.attachmentFace, this.longAxis());
    }

    /** 强调局部 X 轴是补全模型基底所用叉积结果的别名。 */
    public Direction crossAxis() {
        return this.orthogonalAxis();
    }

    /** 将模型局部六方向转换为当前朝向下的世界方向。 */
    public Direction worldDirection(Direction localDirection) {
        Objects.requireNonNull(localDirection, "localDirection");
        return switch (localDirection) {
            case EAST -> this.orthogonalAxis();
            case WEST -> this.orthogonalAxis().getOpposite();
            case UP -> this.attachmentFace;
            case DOWN -> this.attachmentFace.getOpposite();
            case SOUTH -> this.longAxis();
            case NORTH -> this.longAxis().getOpposite();
        };
    }

    /** 将世界六方向转换为当前朝向下对应的模型局部方向。 */
    public Direction localDirection(Direction worldDirection) {
        Objects.requireNonNull(worldDirection, "worldDirection");
        for (Direction localDirection : Direction.values()) {
            if (this.worldDirection(localDirection) == worldDirection) return localDirection;
        }
        throw new IllegalArgumentException("World direction does not map to a local face");
    }

    /**
     * 将 24 种朝向打包到五个位中。低三位是原版三维方向 ID，
     * 随后的两位是四分之一圈旋转数。
     */
    public byte pack() {
        return (byte) (this.attachmentFace.get3DDataValue() | (this.quarterTurn << TURN_SHIFT));
    }

    /** 解码网络数据，遇到保留值或无效值时返回标准默认值。 */
    public static PlasticEntityOrientation unpack(byte packed) {
        return unpack(Byte.toUnsignedInt(packed));
    }

    public static PlasticEntityOrientation unpack(int packed) {
        if ((packed & ~PACKED_MASK) != 0) {
            return DEFAULT;
        }
        int faceId = packed & FACE_MASK;
        if (faceId >= Direction.values().length) {
            return DEFAULT;
        }
        return new PlasticEntityOrientation(
            Direction.from3DDataValue(faceId),
            (packed >> TURN_SHIFT) & TURN_MASK
        );
    }

    /**
     * 仅供尚未创建实体时生成一格实体的临时位置；实际放置必须改用实体几何。
     */
    public Vec3 entityPosition(BlockPos occupiedPos) {
        return entityPosition(occupiedPos, 1.0D, 1.0D);
    }

    /**
     * 仅保留给现有测试和一格实体构造器；X/Z 不等或偏心形状无法由 EntityDimensions 表达。
     */
    public Vec3 entityPosition(BlockPos occupiedPos, double width, double height) {
        Objects.requireNonNull(occupiedPos, "occupiedPos");
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0.0D || height <= 0.0D) {
            throw new IllegalArgumentException("Entity dimensions must be finite and positive");
        }
        double faceSize = this.attachmentFace.getAxis() == Direction.Axis.Y ? height : width;
        double attachmentInset = Math.max(0.0D, (1.0D - faceSize) * 0.5D);
        return Vec3.atCenterOf(occupiedPos)
            .add(
                -this.attachmentFace.getStepX() * attachmentInset,
                -this.attachmentFace.getStepY() * attachmentInset,
                -this.attachmentFace.getStepZ() * attachmentInset
            )
            .subtract(0.0D, height * 0.5D, 0.0D);
    }

    /** 返回一格构造辅助入口对应的方块单元中心。 */
    public Vec3 collisionCenter(BlockPos occupiedPos) {
        Objects.requireNonNull(occupiedPos, "occupiedPos");
        return Vec3.atCenterOf(occupiedPos).add(
            -this.attachmentFace.getStepX() * ATTACHMENT_INSET,
            -this.attachmentFace.getStepY() * ATTACHMENT_INSET,
            -this.attachmentFace.getStepZ() * ATTACHMENT_INSET
        );
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
