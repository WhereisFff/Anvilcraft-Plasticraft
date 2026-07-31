package dev.anvilcraft.plasticraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.block.entity.CondenserTowerBlockEntity;
import dev.dubhe.anvilcraft.client.support.FluidRenderHelper;
import dev.dubhe.anvilcraft.util.LiquidEnchantmentClientFluidTypeExtension;
import dev.dubhe.anvilcraft.util.ModClientFluidTypeExtensionImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;

/** 在冷凝塔玻璃内壁与中心气相通道之间渲染环状冷凝液。 */
public final class CondenserTowerBlockEntityRenderer implements BlockEntityRenderer<CondenserTowerBlockEntity> {
    private static final float FACE_OFFSET = 0.001F;
    private static final float OUTER_MIN = -0.5F + FACE_OFFSET;
    private static final float OUTER_MAX = 1.5F - FACE_OFFSET;
    private static final float CENTER_MIN = -FACE_OFFSET;
    private static final float CENTER_MAX = 1.0F + FACE_OFFSET;
    private static final float MIN_Y = -10.9F / 16.0F + FACE_OFFSET;
    private static final float MAX_Y = 28.9F / 16.0F - FACE_OFFSET;

    public CondenserTowerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
        CondenserTowerBlockEntity tower,
        float partialTick,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        if (!tower.isMainPart()) return;
        FluidStack fluid = tower.getStoredFluid();
        if (fluid.isEmpty()) return;

        float fill = Mth.clamp((float) fluid.getAmount() / CondenserTowerBlockEntity.CAPACITY, 0.0F, 1.0F);
        float fluidTop = Mth.lerp(fill, MIN_Y, MAX_Y);
        renderFluidRing(fluid, MIN_Y, fluidTop, pose, buffers, packedLight);
    }

    @Override
    public AABB getRenderBoundingBox(CondenserTowerBlockEntity tower) {
        return new AABB(tower.getBlockPos()).inflate(1.0D);
    }

    private static void renderFluidRing(
        FluidStack fluid,
        float minY,
        float maxY,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        IClientFluidTypeExtensions properties = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite texture = Minecraft.getInstance()
            .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(properties.getStillTexture(fluid));
        boolean opaque = properties instanceof ModClientFluidTypeExtensionImpl extension && extension.isOpaque()
            || fluid.is(NeoForgeMod.MILK.value());
        VertexConsumer vertices = buffers.getBuffer(opaque ? RenderType.cutout() : RenderType.translucent());
        int light = withFluidLight(packedLight, fluid);
        int[] colors = properties instanceof LiquidEnchantmentClientFluidTypeExtension extension
            ? extension.getLayerColors(fluid)
            : new int[]{properties.getTintColor(fluid)};

        for (int color : colors) {
            renderRingFaces(vertices, pose, texture, light, color, minY, maxY);
        }
    }

    private static void renderRingFaces(
        VertexConsumer vertices,
        PoseStack pose,
        TextureAtlasSprite texture,
        int light,
        int color,
        float minY,
        float maxY
    ) {
        renderHorizontalRing(Direction.UP, maxY, vertices, pose, light, color, texture);
        renderHorizontalRing(Direction.DOWN, minY, vertices, pose, light, color, texture);

        FluidRenderHelper.renderStillTiledFace(
            Direction.NORTH, OUTER_MIN, minY, OUTER_MAX, maxY, OUTER_MIN,
            vertices, pose, light, color, texture
        );
        FluidRenderHelper.renderStillTiledFace(
            Direction.SOUTH, OUTER_MIN, minY, OUTER_MAX, maxY, OUTER_MAX,
            vertices, pose, light, color, texture
        );
        FluidRenderHelper.renderStillTiledFace(
            Direction.WEST, OUTER_MIN, minY, OUTER_MAX, maxY, OUTER_MIN,
            vertices, pose, light, color, texture
        );
        FluidRenderHelper.renderStillTiledFace(
            Direction.EAST, OUTER_MIN, minY, OUTER_MAX, maxY, OUTER_MAX,
            vertices, pose, light, color, texture
        );

        FluidRenderHelper.renderStillTiledFace(
            Direction.SOUTH, CENTER_MIN, minY, CENTER_MAX, maxY, CENTER_MIN,
            vertices, pose, light, color, texture
        );
        FluidRenderHelper.renderStillTiledFace(
            Direction.NORTH, CENTER_MIN, minY, CENTER_MAX, maxY, CENTER_MAX,
            vertices, pose, light, color, texture
        );
        FluidRenderHelper.renderStillTiledFace(
            Direction.EAST, CENTER_MIN, minY, CENTER_MAX, maxY, CENTER_MIN,
            vertices, pose, light, color, texture
        );
        FluidRenderHelper.renderStillTiledFace(
            Direction.WEST, CENTER_MIN, minY, CENTER_MAX, maxY, CENTER_MAX,
            vertices, pose, light, color, texture
        );
    }

    private static void renderHorizontalRing(
        Direction direction,
        float y,
        VertexConsumer vertices,
        PoseStack pose,
        int light,
        int color,
        TextureAtlasSprite texture
    ) {
        FluidRenderHelper.renderStillTiledFace(
            direction, OUTER_MIN, OUTER_MIN, OUTER_MAX, CENTER_MIN, y,
            vertices, pose, light, color, texture
        );
        FluidRenderHelper.renderStillTiledFace(
            direction, OUTER_MIN, CENTER_MAX, OUTER_MAX, OUTER_MAX, y,
            vertices, pose, light, color, texture
        );
        FluidRenderHelper.renderStillTiledFace(
            direction, OUTER_MIN, CENTER_MIN, CENTER_MIN, CENTER_MAX, y,
            vertices, pose, light, color, texture
        );
        FluidRenderHelper.renderStillTiledFace(
            direction, CENTER_MAX, CENTER_MIN, OUTER_MAX, CENTER_MAX, y,
            vertices, pose, light, color, texture
        );
    }

    private static int withFluidLight(int packedLight, FluidStack fluid) {
        int blockLight = Math.max(packedLight >> 4 & 0xF, fluid.getFluidType().getLightLevel());
        return packedLight & 0xF00000 | blockLight << 4;
    }
}
