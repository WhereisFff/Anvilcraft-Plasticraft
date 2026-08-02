package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** 成型舱结构落点以及固定世界轴模型投影相对控制器的坐标映射。 */
public final class PlasticMoldingChamberStructure {
    public static final int PART_COUNT = 27;

    private PlasticMoldingChamberStructure() {
    }

    public static Direction back(Direction front) {
        return front.getOpposite();
    }

    public static Direction right(Direction front) {
        return back(front).getClockWise();
    }

    public static BlockPos regionPos(BlockPos controller, Direction front, MoldingRegionPart part) {
        return controller.relative(back(front), part.depth())
            .relative(right(front), part.right())
            .above(part.up());
    }

    public static BlockPos controllerPos(BlockPos region, Direction front, MoldingRegionPart part) {
        return region.relative(back(front), -part.depth())
            .relative(right(front), -part.right())
            .below(part.up());
    }

    /** 机器局部 right/back 只用于核对 27 格结构，模型数据和渲染不得使用此映射。 */
    public static Vec3 regionProjectionOffset(Direction front, double x, double y, double z) {
        Direction back = back(front);
        Direction right = right(front);
        double localRight = x / 16.0D - 1.5D;
        double localBack = z / 16.0D + 0.5D;
        return new Vec3(
            0.5D + right.getStepX() * localRight + back.getStepX() * localBack,
            y / 16.0D,
            0.5D + right.getStepZ() * localRight + back.getStepZ() * localBack
        );
    }

    /** 固定世界 X/Y/Z 模型坐标到当前成型区域的平移映射，不旋转或镜像模型。 */
    public static Vec3 worldAlignedProjectionOffset(Direction front, double x, double y, double z) {
        Direction back = back(front);
        double east = x / 16.0D - 1.5D;
        double south = z / 16.0D - 1.5D;
        return new Vec3(
            0.5D + back.getStepX() * 2.0D + east,
            y / 16.0D,
            0.5D + back.getStepZ() * 2.0D + south
        );
    }

    public static Vec3 worldAlignedControllerOrigin(Direction front) {
        if (!front.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Molding chamber front must be horizontal");
        }
        return new Vec3(
            16.0D + front.getStepX() * 32.0D,
            0.0D,
            16.0D + front.getStepZ() * 32.0D
        );
    }

    public static List<BlockPos> regionPositions(BlockPos controller, Direction front) {
        List<BlockPos> positions = new ArrayList<>(PART_COUNT);
        for (MoldingRegionPart part : MoldingRegionPart.values()) {
            positions.add(regionPos(controller, front, part));
        }
        return List.copyOf(positions);
    }

    public static boolean canPlace(Level level, BlockPos controller, Direction front) {
        for (BlockPos region : regionPositions(controller, front)) {
            if (!level.isInWorldBounds(region) || !level.isLoaded(region) || !level.getBlockState(region).isAir()) {
                return false;
            }
            if (level.getBlockEntity(region) != null) return false;
        }
        return true;
    }

    public static boolean placeAtomically(Level level, BlockPos controller, BlockState controllerState) {
        Direction front = controllerState.getValue(PlasticMoldingChamberBlock.FACING);
        if (!canPlace(level, controller, front)) return false;
        BlockState previousControllerState = level.getBlockState(controller);
        List<BlockPos> placed = new ArrayList<>(PART_COUNT);
        if (!level.setBlock(controller, controllerState, Block.UPDATE_ALL)) return false;
        for (MoldingRegionPart part : MoldingRegionPart.values()) {
            BlockPos region = regionPos(controller, front, part);
            BlockState state = ModBlocks.PLASTIC_MOLDING_REGION.get().defaultBlockState()
                .setValue(PlasticMoldingRegionBlock.FACING, front)
                .setValue(PlasticMoldingRegionBlock.PART, part);
            if (level.setBlock(region, state, Block.UPDATE_ALL)) {
                placed.add(region);
                continue;
            }
            for (BlockPos rollback : placed) level.removeBlock(rollback, false);
            level.setBlock(controller, previousControllerState, Block.UPDATE_ALL);
            return false;
        }
        return true;
    }

    public static boolean repair(Level level, BlockPos controller, Direction front) {
        boolean complete = true;
        for (MoldingRegionPart part : MoldingRegionPart.values()) {
            BlockPos region = regionPos(controller, front, part);
            if (!level.isInWorldBounds(region) || !level.isLoaded(region)) {
                complete = false;
                continue;
            }
            BlockState expected = ModBlocks.PLASTIC_MOLDING_REGION.get().defaultBlockState()
                .setValue(PlasticMoldingRegionBlock.FACING, front)
                .setValue(PlasticMoldingRegionBlock.PART, part);
            BlockState current = level.getBlockState(region);
            if (current == expected) continue;
            if (current.isAir()) {
                complete &= level.setBlock(region, expected, Block.UPDATE_ALL);
            } else {
                complete = false;
            }
        }
        return complete;
    }

    public static void removeParts(Level level, BlockPos controller, Direction front) {
        for (MoldingRegionPart part : MoldingRegionPart.values()) {
            BlockPos region = regionPos(controller, front, part);
            BlockState state = level.getBlockState(region);
            if (state.is(ModBlocks.PLASTIC_MOLDING_REGION.get())
                && state.getValue(PlasticMoldingRegionBlock.FACING) == front
                && state.getValue(PlasticMoldingRegionBlock.PART) == part) {
                level.removeBlock(region, false);
            }
        }
    }

    public static boolean isValidPart(Level level, BlockPos region, BlockState state) {
        Direction front = state.getValue(PlasticMoldingRegionBlock.FACING);
        MoldingRegionPart part = state.getValue(PlasticMoldingRegionBlock.PART);
        BlockPos controller = controllerPos(region, front, part);
        BlockState controllerState = level.getBlockState(controller);
        return controllerState.is(ModBlocks.PLASTIC_MOLDING_CHAMBER.get())
            && controllerState.getValue(PlasticMoldingChamberBlock.FACING) == front;
    }
}
