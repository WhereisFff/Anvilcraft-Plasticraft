package dev.anvilcraft.plasticraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberStructure;
import dev.anvilcraft.plasticraft.block.PlasticMoldingMachineState;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorDrawPhase;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorSceneMesh;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorScenePart;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorVertex;
import dev.anvilcraft.plasticraft.client.molding.scene.MoldingSceneBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** 用编辑器世界轴网格和结构锚点绘制无碰撞世界投影。 */
public final class PlasticMoldingChamberRenderer implements BlockEntityRenderer<PlasticMoldingChamberBlockEntity> {
    private static final double WORLD_PROJECTION_LIFT_PIXELS = 0.05D;
    private final Map<PlasticMoldingChamberBlockEntity, CachedProjection> cache = new WeakHashMap<>();

    public PlasticMoldingChamberRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
        PlasticMoldingChamberBlockEntity chamber,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        if (chamber.machineState() != PlasticMoldingMachineState.EDITABLE) return;
        Direction front = chamber.getBlockState().getValue(PlasticMoldingChamberBlock.FACING);
        Vector3d directionToCamera = worldDirectionToCamera(chamber, front);
        Vector3d cameraPosition = new Vector3d(directionToCamera).mul(16.0D).add(24.0D, 24.0D, 24.0D);
        CachedProjection cached = this.cache.get(chamber);
        if (cached == null || cached.revision != chamber.revision()) {
            cached = new CachedProjection(
                chamber.revision(),
                MoldingSceneBuilder.buildWorldSurfaces(chamber.revision(), chamber.model())
            );
            this.cache.put(chamber, cached);
        }
        EditorSceneMesh guides = MoldingSceneBuilder.buildWorldGuides(
            directionToCamera,
            worldUnitsPerPixel(directionToCamera.length()) * 16.0D,
            cameraPosition
        );
        Matrix4f pose = poseStack.last().pose();
        renderMesh(cached.mesh, chamber, front, pose, bufferSource, true);
        renderMesh(guides, chamber, front, pose, bufferSource, false);
    }

    private static void renderMesh(
        EditorSceneMesh mesh,
        PlasticMoldingChamberBlockEntity chamber,
        Direction front,
        Matrix4f pose,
        MultiBufferSource bufferSource,
        boolean useWorldLight
    ) {
        Level level = chamber.getLevel();
        int skyDarken = level == null ? 0 : level.getSkyDarken();
        Map<BlockPos, Float> lightCache = useWorldLight && level != null ? new HashMap<>() : Map.of();
        for (EditorScenePart part : mesh.parts()) {
            VertexConsumer consumer = bufferSource.getBuffer(
                part.phase() == EditorDrawPhase.MANUFACTURING_SURFACE
                    ? RenderType.debugSectionQuads()
                    : RenderType.debugQuads()
            );
            for (int index : part.indices()) {
                EditorVertex vertex = part.vertices().get(index);
                Vec3 offset = PlasticMoldingChamberStructure.worldAlignedProjectionOffset(
                    front,
                    vertex.x(),
                    vertex.y() + WORLD_PROJECTION_LIFT_PIXELS,
                    vertex.z()
                );
                BlockPos worldPos = BlockPos.containing(
                    offset.add(Vec3.atLowerCornerOf(chamber.getBlockPos()))
                );
                float brightness = useWorldLight && level != null ? lightCache.computeIfAbsent(
                    worldPos,
                    pos -> worldBrightness(LevelRenderer.getLightColor(level, pos), skyDarken)
                ) : 1.0F;
                consumer.addVertex(pose, (float) offset.x, (float) offset.y, (float) offset.z)
                    .setColor(modulateColor(vertex.color(), brightness));
            }
        }
    }

    private static float worldBrightness(int packedLight, int skyDarken) {
        int skyLight = Math.max(0, LightTexture.sky(packedLight) - skyDarken);
        int light = Math.max(LightTexture.block(packedLight), skyLight);
        return 0.18F + 0.82F * light / 15.0F;
    }

    private static int modulateColor(int color, float brightness) {
        int alpha = color >>> 24;
        int red = Math.round(((color >> 16) & 0xFF) * brightness);
        int green = Math.round(((color >> 8) & 0xFF) * brightness);
        int blue = Math.round((color & 0xFF) * brightness);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    @Override
    public boolean shouldRenderOffScreen(PlasticMoldingChamberBlockEntity chamber) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(PlasticMoldingChamberBlockEntity chamber) {
        Direction front = chamber.getBlockState().getValue(PlasticMoldingChamberBlock.FACING);
        Vec3 controller = Vec3.atLowerCornerOf(chamber.getBlockPos());
        AABB bounds = new AABB(chamber.getBlockPos());
        for (double x : new double[]{0.0D, 48.0D}) {
            for (double y : new double[]{0.0D, 48.0D}) {
                for (double z : new double[]{0.0D, 48.0D}) {
                    Vec3 point = PlasticMoldingChamberStructure.worldAlignedProjectionOffset(front, x, y, z)
                        .add(controller);
                    bounds = bounds.minmax(new AABB(point, point));
                }
            }
        }
        return bounds.inflate(0.125D);
    }

    private static Vector3d worldDirectionToCamera(
        PlasticMoldingChamberBlockEntity chamber,
        Direction front
    ) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 center = PlasticMoldingChamberStructure.worldAlignedProjectionOffset(front, 24.0D, 24.0D, 24.0D)
            .add(Vec3.atLowerCornerOf(chamber.getBlockPos()));
        Vec3 direction = camera.subtract(center);
        return new Vector3d(direction.x, direction.y, direction.z);
    }

    private static double worldUnitsPerPixel(double distanceBlocks) {
        Minecraft minecraft = Minecraft.getInstance();
        double verticalFov = Math.toRadians(minecraft.options.fov().get());
        int framebufferHeight = Math.max(1, minecraft.getWindow().getHeight());
        return 2.0D * Math.tan(verticalFov * 0.5D) * Math.max(distanceBlocks, 0.25D) / framebufferHeight;
    }

    private record CachedProjection(long revision, EditorSceneMesh mesh) {
    }
}
