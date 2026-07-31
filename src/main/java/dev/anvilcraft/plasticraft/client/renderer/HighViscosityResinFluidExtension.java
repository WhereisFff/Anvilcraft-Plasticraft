package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.dubhe.anvilcraft.util.ModClientFluidTypeExtensionImpl;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.textures.FluidSpriteCache;

/** 使用单张 16x16 动画帧完整渲染流体的静止面与流动面。 */
public class HighViscosityResinFluidExtension extends ModClientFluidTypeExtensionImpl {
    private static final float MAX_FLUID_HEIGHT = 8.0F / 9.0F;
    private static final float FACE_OFFSET = 0.001F;

    public HighViscosityResinFluidExtension() {
        this(
            AnvilcraftPlasticraft.of("block/liquid_high_viscosity_resin"),
            0x6B481D,
            1.5F,
            0xFFFFFFFF,
            false
        );
    }

    public HighViscosityResinFluidExtension(ResourceLocation texture) {
        super(texture, texture);
    }

    public HighViscosityResinFluidExtension(
        ResourceLocation texture,
        int fogColor,
        float fogDistance,
        int tintColor,
        boolean opaque
    ) {
        super(
            texture,
            texture,
            fogColor,
            fogDistance,
            tintColor,
            opaque
        );
    }

    @Override
    public boolean renderFluid(
        FluidState fluidState,
        BlockAndTintGetter level,
        BlockPos pos,
        VertexConsumer consumer,
        BlockState blockState
    ) {
        this.render(level, pos, consumer, blockState, fluidState);
        return true;
    }

    private void render(
        BlockAndTintGetter level,
        BlockPos pos,
        VertexConsumer consumer,
        BlockState blockState,
        FluidState fluidState
    ) {
        BlockState belowState = level.getBlockState(pos.below());
        FluidState belowFluid = belowState.getFluidState();
        BlockState aboveState = level.getBlockState(pos.above());
        BlockState northState = level.getBlockState(pos.north());
        FluidState northFluid = northState.getFluidState();
        BlockState southState = level.getBlockState(pos.south());
        FluidState southFluid = southState.getFluidState();
        BlockState westState = level.getBlockState(pos.west());
        FluidState westFluid = westState.getFluidState();
        BlockState eastState = level.getBlockState(pos.east());
        FluidState eastFluid = eastState.getFluidState();

        boolean renderTop = !aboveState.shouldHideAdjacentFluidFace(Direction.DOWN, fluidState);
        boolean renderBottom = LiquidBlockRenderer.shouldRenderFace(
            level,
            pos,
            fluidState,
            blockState,
            Direction.DOWN,
            belowState
        ) && !isFaceOccludedByNeighbor(level, pos, Direction.DOWN, MAX_FLUID_HEIGHT, belowState);
        boolean renderNorth = LiquidBlockRenderer.shouldRenderFace(
            level,
            pos,
            fluidState,
            blockState,
            Direction.NORTH,
            northState
        );
        boolean renderSouth = LiquidBlockRenderer.shouldRenderFace(
            level,
            pos,
            fluidState,
            blockState,
            Direction.SOUTH,
            southState
        );
        boolean renderWest = LiquidBlockRenderer.shouldRenderFace(
            level,
            pos,
            fluidState,
            blockState,
            Direction.WEST,
            westState
        );
        boolean renderEast = LiquidBlockRenderer.shouldRenderFace(
            level,
            pos,
            fluidState,
            blockState,
            Direction.EAST,
            eastState
        );
        if (!renderTop && !renderBottom && !renderNorth && !renderSouth && !renderWest && !renderEast) return;

        int tint = this.getTintColor(fluidState, level, pos);
        float alpha = (float) (tint >> 24 & 0xFF) / 255.0F;
        float red = (float) (tint >> 16 & 0xFF) / 255.0F;
        float green = (float) (tint >> 8 & 0xFF) / 255.0F;
        float blue = (float) (tint & 0xFF) / 255.0F;
        float downShade = level.getShade(Direction.DOWN, true);
        float upShade = level.getShade(Direction.UP, true);
        float northShade = level.getShade(Direction.NORTH, true);
        float westShade = level.getShade(Direction.WEST, true);
        Fluid fluid = fluidState.getType();
        float centerHeight = getHeight(level, fluid, pos, blockState, fluidState);
        float northEastHeight;
        float northWestHeight;
        float southEastHeight;
        float southWestHeight;
        if (centerHeight >= 1.0F) {
            northEastHeight = 1.0F;
            northWestHeight = 1.0F;
            southEastHeight = 1.0F;
            southWestHeight = 1.0F;
        } else {
            float northHeight = getHeight(level, fluid, pos.north(), northState, northFluid);
            float southHeight = getHeight(level, fluid, pos.south(), southState, southFluid);
            float eastHeight = getHeight(level, fluid, pos.east(), eastState, eastFluid);
            float westHeight = getHeight(level, fluid, pos.west(), westState, westFluid);
            northEastHeight = calculateAverageHeight(
                level,
                fluid,
                centerHeight,
                northHeight,
                eastHeight,
                pos.north().east()
            );
            northWestHeight = calculateAverageHeight(
                level,
                fluid,
                centerHeight,
                northHeight,
                westHeight,
                pos.north().west()
            );
            southEastHeight = calculateAverageHeight(
                level,
                fluid,
                centerHeight,
                southHeight,
                eastHeight,
                pos.south().east()
            );
            southWestHeight = calculateAverageHeight(
                level,
                fluid,
                centerHeight,
                southHeight,
                westHeight,
                pos.south().west()
            );
        }

        float x = (float) (pos.getX() & 15);
        float y = (float) (pos.getY() & 15);
        float z = (float) (pos.getZ() & 15);
        float bottomOffset = renderBottom ? FACE_OFFSET : 0.0F;
        TextureAtlasSprite sprite = FluidSpriteCache.getFluidSprites(level, pos, fluidState)[0];

        if (renderTop && !isFaceOccludedByNeighbor(
            level,
            pos,
            Direction.UP,
            Math.min(Math.min(northWestHeight, southWestHeight), Math.min(southEastHeight, northEastHeight)),
            aboveState
        )) {
            northEastHeight -= FACE_OFFSET;
            northWestHeight -= FACE_OFFSET;
            southEastHeight -= FACE_OFFSET;
            southWestHeight -= FACE_OFFSET;
            float rawU0 = sprite.getU(0.0F);
            float rawU1 = sprite.getU(1.0F);
            float rawV0 = sprite.getV(0.0F);
            float rawV1 = sprite.getV(1.0F);
            float centerU = (rawU0 + rawU1) * 0.5F;
            float centerV = (rawV0 + rawV1) * 0.5F;
            float shrink = sprite.uvShrinkRatio();
            float u0 = Mth.lerp(shrink, rawU0, centerU);
            float u1 = Mth.lerp(shrink, rawU1, centerU);
            float v0 = Mth.lerp(shrink, rawV0, centerV);
            float v1 = Mth.lerp(shrink, rawV1, centerV);
            int light = getLightColor(level, pos);
            float shadedRed = upShade * red;
            float shadedGreen = upShade * green;
            float shadedBlue = upShade * blue;
            vertex(consumer, x, y + northWestHeight, z, shadedRed, shadedGreen, shadedBlue, alpha, u0, v0, light);
            vertex(consumer, x, y + southWestHeight, z + 1.0F, shadedRed, shadedGreen, shadedBlue, alpha, u0, v1, light);
            vertex(consumer, x + 1.0F, y + southEastHeight, z + 1.0F, shadedRed, shadedGreen, shadedBlue, alpha, u1, v1, light);
            vertex(consumer, x + 1.0F, y + northEastHeight, z, shadedRed, shadedGreen, shadedBlue, alpha, u1, v0, light);
            if (fluidState.shouldRenderBackwardUpFace(level, pos.above())) {
                vertex(consumer, x, y + northWestHeight, z, shadedRed, shadedGreen, shadedBlue, alpha, u0, v0, light);
                vertex(consumer, x + 1.0F, y + northEastHeight, z, shadedRed, shadedGreen, shadedBlue, alpha, u1, v0, light);
                vertex(consumer, x + 1.0F, y + southEastHeight, z + 1.0F, shadedRed, shadedGreen, shadedBlue, alpha, u1, v1, light);
                vertex(consumer, x, y + southWestHeight, z + 1.0F, shadedRed, shadedGreen, shadedBlue, alpha, u0, v1, light);
            }
        }

        if (renderBottom) {
            float u0 = sprite.getU0();
            float u1 = sprite.getU1();
            float v0 = sprite.getV0();
            float v1 = sprite.getV1();
            int light = getLightColor(level, pos.below());
            float shadedRed = downShade * red;
            float shadedGreen = downShade * green;
            float shadedBlue = downShade * blue;
            vertex(consumer, x, y + bottomOffset, z + 1.0F, shadedRed, shadedGreen, shadedBlue, alpha, u0, v1, light);
            vertex(consumer, x, y + bottomOffset, z, shadedRed, shadedGreen, shadedBlue, alpha, u0, v0, light);
            vertex(consumer, x + 1.0F, y + bottomOffset, z, shadedRed, shadedGreen, shadedBlue, alpha, u1, v0, light);
            vertex(consumer, x + 1.0F, y + bottomOffset, z + 1.0F, shadedRed, shadedGreen, shadedBlue, alpha, u1, v1, light);
        }

        int light = getLightColor(level, pos);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            float leftHeight;
            float rightHeight;
            float leftX;
            float rightX;
            float leftZ;
            float rightZ;
            boolean shouldRender;
            switch (direction) {
                case NORTH -> {
                    leftHeight = northWestHeight;
                    rightHeight = northEastHeight;
                    leftX = x;
                    rightX = x + 1.0F;
                    leftZ = z + FACE_OFFSET;
                    rightZ = z + FACE_OFFSET;
                    shouldRender = renderNorth;
                }
                case SOUTH -> {
                    leftHeight = southEastHeight;
                    rightHeight = southWestHeight;
                    leftX = x + 1.0F;
                    rightX = x;
                    leftZ = z + 1.0F - FACE_OFFSET;
                    rightZ = z + 1.0F - FACE_OFFSET;
                    shouldRender = renderSouth;
                }
                case WEST -> {
                    leftHeight = southWestHeight;
                    rightHeight = northWestHeight;
                    leftX = x + FACE_OFFSET;
                    rightX = x + FACE_OFFSET;
                    leftZ = z + 1.0F;
                    rightZ = z;
                    shouldRender = renderWest;
                }
                default -> {
                    leftHeight = northEastHeight;
                    rightHeight = southEastHeight;
                    leftX = x + 1.0F - FACE_OFFSET;
                    rightX = x + 1.0F - FACE_OFFSET;
                    leftZ = z;
                    rightZ = z + 1.0F;
                    shouldRender = renderEast;
                }
            }
            if (!shouldRender || isFaceOccludedByNeighbor(
                level,
                pos,
                direction,
                Math.max(leftHeight, rightHeight),
                level.getBlockState(pos.relative(direction))
            )) continue;

            float u0 = sprite.getU(0.0F);
            float u1 = sprite.getU(1.0F);
            float topV0 = sprite.getV(1.0F - leftHeight);
            float topV1 = sprite.getV(1.0F - rightHeight);
            float bottomV = sprite.getV(1.0F);
            float sideShade = direction.getAxis() == Direction.Axis.Z ? northShade : westShade;
            float shadedRed = upShade * sideShade * red;
            float shadedGreen = upShade * sideShade * green;
            float shadedBlue = upShade * sideShade * blue;
            vertex(consumer, leftX, y + leftHeight, leftZ, shadedRed, shadedGreen, shadedBlue, alpha, u0, topV0, light);
            vertex(consumer, rightX, y + rightHeight, rightZ, shadedRed, shadedGreen, shadedBlue, alpha, u1, topV1, light);
            vertex(consumer, rightX, y + bottomOffset, rightZ, shadedRed, shadedGreen, shadedBlue, alpha, u1, bottomV, light);
            vertex(consumer, leftX, y + bottomOffset, leftZ, shadedRed, shadedGreen, shadedBlue, alpha, u0, bottomV, light);
            vertex(consumer, leftX, y + bottomOffset, leftZ, shadedRed, shadedGreen, shadedBlue, alpha, u0, bottomV, light);
            vertex(consumer, rightX, y + bottomOffset, rightZ, shadedRed, shadedGreen, shadedBlue, alpha, u1, bottomV, light);
            vertex(consumer, rightX, y + rightHeight, rightZ, shadedRed, shadedGreen, shadedBlue, alpha, u1, topV1, light);
            vertex(consumer, leftX, y + leftHeight, leftZ, shadedRed, shadedGreen, shadedBlue, alpha, u0, topV0, light);
        }
    }

    private static boolean isFaceOccludedByNeighbor(
        BlockGetter level,
        BlockPos pos,
        Direction direction,
        float height,
        BlockState neighborState
    ) {
        if (!neighborState.canOcclude()) return false;
        VoxelShape fluidShape = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, height, 1.0D);
        VoxelShape neighborShape = neighborState.getOcclusionShape(level, pos.relative(direction));
        return Shapes.blockOccudes(fluidShape, neighborShape, direction);
    }

    private static float calculateAverageHeight(
        BlockAndTintGetter level,
        Fluid fluid,
        float centerHeight,
        float firstHeight,
        float secondHeight,
        BlockPos diagonalPos
    ) {
        if (firstHeight >= 1.0F || secondHeight >= 1.0F) return 1.0F;
        float[] weightedHeight = new float[2];
        if (firstHeight > 0.0F || secondHeight > 0.0F) {
            float diagonalHeight = getHeight(level, fluid, diagonalPos);
            if (diagonalHeight >= 1.0F) return 1.0F;
            addWeightedHeight(weightedHeight, diagonalHeight);
        }
        addWeightedHeight(weightedHeight, centerHeight);
        addWeightedHeight(weightedHeight, firstHeight);
        addWeightedHeight(weightedHeight, secondHeight);
        return weightedHeight[0] / weightedHeight[1];
    }

    private static void addWeightedHeight(float[] output, float height) {
        if (height >= 0.8F) {
            output[0] += height * 10.0F;
            output[1] += 10.0F;
        } else if (height >= 0.0F) {
            output[0] += height;
            output[1]++;
        }
    }

    private static float getHeight(BlockAndTintGetter level, Fluid fluid, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        return getHeight(level, fluid, pos, blockState, blockState.getFluidState());
    }

    private static float getHeight(
        BlockAndTintGetter level,
        Fluid fluid,
        BlockPos pos,
        BlockState blockState,
        FluidState fluidState
    ) {
        if (fluid.isSame(fluidState.getType())) {
            return fluid.isSame(level.getBlockState(pos.above()).getFluidState().getType())
                ? 1.0F
                : fluidState.getOwnHeight();
        }
        return blockState.isSolid() ? -1.0F : 0.0F;
    }

    private static void vertex(
        VertexConsumer consumer,
        float x,
        float y,
        float z,
        float red,
        float green,
        float blue,
        float alpha,
        float u,
        float v,
        int packedLight
    ) {
        consumer.addVertex(x, y, z)
            .setColor(red, green, blue, alpha)
            .setUv(u, v)
            .setLight(packedLight)
            .setNormal(0.0F, 1.0F, 0.0F);
    }

    private static int getLightColor(BlockAndTintGetter level, BlockPos pos) {
        int currentLight = LevelRenderer.getLightColor(level, pos);
        int aboveLight = LevelRenderer.getLightColor(level, pos.above());
        int blockLight = Math.max(currentLight & 0xFF, aboveLight & 0xFF);
        int skyLight = Math.max(currentLight >> 16 & 0xFF, aboveLight >> 16 & 0xFF);
        return blockLight | skyLight << 16;
    }
}
