package dev.anvilcraft.plasticraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.client.renderer.MoldedPlasticMeshRenderer;
import dev.anvilcraft.plasticraft.client.renderer.MoldedTrayComponentRenderer;
import dev.anvilcraft.plasticraft.entity.MoldedPlasticCauldronState;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.Map;
import java.util.WeakHashMap;

final class AllayLoungeItemRenderer {
    private static final AABB UNIT_BOUNDS = new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    private static final float TRAY_ANGLE = 22.5F;
    private static final double TRAY_PIVOT_Y = 8.0D / 16.0D;
    private static final double TRAY_PIVOT_Z = -5.5D / 16.0D;
    private static final double TRAY_TOP_Y = 4.5D / 16.0D;
    private static final double SLIDE_START_Z = -3.75D / 16.0D;
    private static final double SLIDE_END_Z = -5.75D / 16.0D;
    private static final double BASE_MAX_ITEM_SIZE = 4.0D / 16.0D;
    private static final double BASE_DEPTH_LIMIT = 3.25D / 16.0D;
    private static final double DISPLAY_SCALE = 2.0D;
    private static final double REAR_CLEARANCE_Z = -3.75D / 16.0D;
    private static final int VERTEX_STRIDE = DefaultVertexFormat.BLOCK.getVertexSize() / Integer.BYTES;

    private final ItemRenderer renderer;
    private final Map<BakedModel, AABB> modelBounds = new WeakHashMap<>();

    AllayLoungeItemRenderer(ItemRenderer renderer) {
        this.renderer = renderer;
    }

    void render(
        AllayLoungeBlockEntity lounge,
        Direction side,
        ItemStack stack,
        float progress,
        PoseStack pose,
        MultiBufferSource buffers,
        int light,
        int overlay
    ) {
        BakedModel model = this.renderer.getModel(stack, lounge.getLevel(), null, 0);
        AABB bounds = model.isCustomRenderer()
            ? customBounds(stack)
            : this.modelBounds.computeIfAbsent(model, value -> measure(value, stack));
        double largest = Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize()));
        double scale = BASE_MAX_ITEM_SIZE / largest;
        if (bounds.getZsize() > 0.0D) scale = Math.min(scale, BASE_DEPTH_LIMIT / bounds.getZsize());
        scale *= DISPLAY_SCALE;
        Vec3 center = bounds.getCenter();
        double endZ = Math.min(SLIDE_END_Z, REAR_CLEARANCE_Z - bounds.getZsize() * scale * 0.5D);
        double slideZ = Mth.lerp(progress, SLIDE_START_Z, endZ);

        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-((side.toYRot() + 180.0F) % 360.0F)));
        pose.translate(0.0D, TRAY_PIVOT_Y, TRAY_PIVOT_Z);
        pose.mulPose(Axis.XP.rotationDegrees(TRAY_ANGLE));
        pose.translate(0.0D, TRAY_TOP_Y - TRAY_PIVOT_Y, slideZ - TRAY_PIVOT_Z);
        pose.scale((float) scale, (float) scale, (float) scale);
        // 原版物品渲染器随后减去半格；在斜槽局部坐标中把真实几何底面压到 y=0。
        pose.translate(0.5D - center.x, 0.5D - bounds.minY, 0.5D - center.z);
        this.renderer.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, model);
        pose.popPose();
    }

    private static AABB customBounds(ItemStack stack) {
        MoldedPlasticData data = MoldedPlasticData.get(stack).orElse(null);
        if (data == null) return UNIT_BOUNDS;
        AABB bounds = MoldedTrayComponentRenderer.renderBounds(data);
        Direction outlet = MoldingProductTypes.isCauldron(data.finalType())
            ? MoldedPlasticCauldronState.get(stack).flatMap(MoldedPlasticCauldronState::outletSide).orElse(null)
            : null;
        if (outlet != null) bounds = bounds.minmax(MoldedPlasticMeshRenderer.cauldronOutletBounds(data, outlet));
        double largest = Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize()));
        if (!Double.isFinite(largest) || largest <= 0.0D) return UNIT_BOUNDS;
        // 成型物品的自定义渲染器会先把最长边归一化为一格，这里使用相同的归一化边界。
        double x = bounds.getXsize() / largest * 0.5D;
        double y = bounds.getYsize() / largest * 0.5D;
        double z = bounds.getZsize() / largest * 0.5D;
        return new AABB(0.5D - x, 0.5D - y, 0.5D - z, 0.5D + x, 0.5D + y, 0.5D + z);
    }

    private static AABB measure(BakedModel model, ItemStack stack) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        RandomSource random = RandomSource.create(42L);
        for (BakedModel pass : model.getRenderPasses(stack, true)) {
            for (int index = 0; index <= 6; index++) {
                Direction side = index < 6 ? Direction.from3DDataValue(index) : null;
                random.setSeed(42L);
                for (BakedQuad quad : pass.getQuads(null, side, random, ModelData.EMPTY, null)) {
                    int[] vertices = quad.getVertices();
                    for (int offset = 0; offset + 2 < vertices.length; offset += VERTEX_STRIDE) {
                        float x = Float.intBitsToFloat(vertices[offset]);
                        float y = Float.intBitsToFloat(vertices[offset + 1]);
                        float z = Float.intBitsToFloat(vertices[offset + 2]);
                        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) continue;
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        minZ = Math.min(minZ, z);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                        maxZ = Math.max(maxZ, z);
                    }
                }
            }
        }
        if (!Double.isFinite(minX)
            || Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) < 1.0E-6D) {
            return UNIT_BOUNDS;
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
