package dev.anvilcraft.plasticraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberStructure;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorDrawPhase;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorSceneMesh;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorScenePart;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorVertex;
import dev.anvilcraft.plasticraft.client.molding.scene.MoldingSceneBuilder;
import dev.anvilcraft.plasticraft.client.renderer.molding.MoldingProjectionRenderTypes;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.molding.machine.MoldingFormingMode;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPrintedGeometry;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPrinterMotion;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelBounds;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.dubhe.anvilcraft.client.support.FluidRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** 用同一世界坐标系绘制编辑投影、粘土模具和逐体素打印机构。 */
public final class PlasticMoldingChamberRenderer implements BlockEntityRenderer<PlasticMoldingChamberBlockEntity> {
    private static final int CLEAR_PLASTIC_PREVIEW_ALPHA = 0x88000000;
    public static final ModelResourceLocation PRINTER_FRAME_MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/print_warehouse")
    );
    public static final ModelResourceLocation PRINTER_GLASS_MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/print_warehouse_glass")
    );
    public static final ModelResourceLocation PRINTER_GUI_MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/print_warehouse_gui")
    );
    public static final ModelResourceLocation PRINTER_X_MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/print_warehouse_x")
    );
    public static final ModelResourceLocation PRINTER_Y_MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/print_warehouse_y")
    );
    public static final ModelResourceLocation PRINTER_Z_MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/print_warehouse_z")
    );
    public static final ModelResourceLocation PRINTER_Z_EYE_MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/print_warehouse_z_eye")
    );
    public static final ModelResourceLocation PRINTER_DOOR_LEFT_MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/print_warehouse_door_left")
    );
    public static final ModelResourceLocation PRINTER_DOOR_RIGHT_MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/print_warehouse_door_right")
    );
    public static final MoldingVec3 PRINTER_MODEL_ORIGIN = new MoldingVec3(16.0D, 16.0D, 16.0D);
    private static final double WORLD_PROJECTION_LIFT_PIXELS = 0.05D;
    private static final float PROJECTION_HOLOGRAM_ALPHA = 0.55F;
    private static final double PRINTER_RENDER_MAX_Y = 58.0D;
    private static final float TANK_MIN_X = 10.6F / 16.0F;
    private static final float TANK_MAX_X = 14.4F / 16.0F;
    private static final float TANK_MIN_Y = 5.05F / 16.0F;
    private static final float TANK_MAX_Y = 10.95F / 16.0F;
    private static final float TANK_MIN_Z = 7.1F / 16.0F;
    private static final float TANK_MAX_Z = 10.9F / 16.0F;
    private static final float TANK_ORIGIN_X = 12.5F / 16.0F;
    private static final float TANK_ORIGIN_Y = 8.0F / 16.0F;
    private static final float TANK_ORIGIN_Z = 7.0F / 16.0F;
    private final BlockRenderDispatcher dispatcher;
    private final Map<PlasticMoldingChamberBlockEntity, CachedProjection> projectionCache = new WeakHashMap<>();
    private final Map<PlasticMoldingChamberBlockEntity, CachedPrintedGeometry> printedCache = new WeakHashMap<>();

    public PlasticMoldingChamberRenderer(BlockEntityRendererProvider.Context context) {
        this.dispatcher = context.getBlockRenderDispatcher();
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
        renderStagingTank(chamber, poseStack, bufferSource, packedLight);
        if (chamber.showsWorldProjection()) {
            renderProjection(chamber, poseStack, bufferSource);
        }
        if (chamber.isPrintingProcess()) {
            renderPrintedSurface(chamber, poseStack, bufferSource, packedLight);
        }
        if (chamber.printingComponentPresent()) {
            this.renderPrinter(chamber, partialTick, poseStack, bufferSource, packedLight);
        }
        if (chamber.hasMoldCollision()) {
            renderMold(chamber, partialTick, poseStack, bufferSource);
        }
    }

    private static void renderStagingTank(
        PlasticMoldingChamberBlockEntity chamber,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight
    ) {
        FluidStack fluid = chamber.stagingFluid();
        if (fluid.isEmpty()) return;
        float fill = Mth.clamp(
            (float) fluid.getAmount() / PlasticMoldingChamberBlockEntity.STAGING_TANK_CAPACITY,
            0.0F,
            1.0F
        );
        float fluidTop = Mth.lerp(fill, TANK_MIN_Y, TANK_MAX_Y);
        Direction facing = chamber.getBlockState().getValue(PlasticMoldingChamberBlock.FACING);
        float blockRotation = (facing.toYRot() + 180.0F) % 360.0F;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-blockRotation));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        poseStack.translate(TANK_ORIGIN_X, TANK_ORIGIN_Y, TANK_ORIGIN_Z);
        poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));
        poseStack.translate(-TANK_ORIGIN_X, -TANK_ORIGIN_Y, -TANK_ORIGIN_Z);
        FluidRenderHelper.INSTANCE.renderFluidBox(
            fluid,
            TANK_MIN_X,
            TANK_MIN_Y,
            TANK_MIN_Z,
            TANK_MAX_X,
            fluidTop,
            TANK_MAX_Z,
            bufferSource,
            poseStack,
            packedLight,
            true,
            false
        );
        poseStack.popPose();
    }

    private void renderProjection(
        PlasticMoldingChamberBlockEntity chamber,
        PoseStack poseStack,
        MultiBufferSource bufferSource
    ) {
        Direction front = chamber.getBlockState().getValue(PlasticMoldingChamberBlock.FACING);
        Vector3d directionToCamera = worldDirectionToCamera(chamber, front);
        Vector3d cameraPosition = new Vector3d(directionToCamera).mul(16.0D).add(24.0D, 24.0D, 24.0D);
        CachedProjection cached = this.projectionCache.get(chamber);
        boolean printing = chamber.isLocked()
            ? chamber.cycleFormingMode() == MoldingFormingMode.PRINTING
            : chamber.formingMode() == MoldingFormingMode.PRINTING;
        if (cached == null || cached.revision != chamber.revision()) {
            cached = new CachedProjection(
                chamber.revision(),
                MoldingSceneBuilder.buildWorldSurfaces(chamber.revision(), chamber.model())
            );
            this.projectionCache.put(chamber, cached);
        }
        EditorSceneMesh guides = MoldingSceneBuilder.buildWorldGuides(
            directionToCamera,
            worldUnitsPerPixel(directionToCamera.length()) * 16.0D,
            cameraPosition,
            printing
        );
        Matrix4f pose = poseStack.last().pose();
        renderHologramSurfaces(cached.mesh, chamber, front, pose, bufferSource);
        renderMesh(guides, chamber, front, pose, bufferSource, false);
    }

    private void renderPrintedSurface(
        PlasticMoldingChamberBlockEntity chamber,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight
    ) {
        int completed = Math.min(chamber.printingProgress(), chamber.printingTotal());
        if (completed <= 0) return;
        String modelHash = chamber.bakedModel().modelHash();
        CachedPrintedGeometry cached = this.printedCache.get(chamber);
        if (cached == null || !cached.modelHash.equals(modelHash)) {
            try {
                cached = new CachedPrintedGeometry(
                    modelHash,
                    MoldingPrintedGeometry.prepare(chamber.model(), chamber.printingPlan())
                );
            } catch (RuntimeException exception) {
                AnvilcraftPlasticraft.LOGGER.debug(
                    "Unable to build plastic printing preview at {}",
                    chamber.getBlockPos(),
                    exception
                );
                cached = CachedPrintedGeometry.empty(modelHash);
            }
            this.printedCache.put(chamber, cached);
        }
        cached.geometry.advanceTo(completed);
        if (cached.geometry.isEmpty()) return;

        Direction front = chamber.getBlockState().getValue(PlasticMoldingChamberBlock.FACING);
        Matrix4f pose = poseStack.last().pose();
        int skyDarken = chamber.getLevel() == null ? 0 : chamber.getLevel().getSkyDarken();
        float brightness = worldBrightness(packedLight, skyDarken);
        FluidStack material = chamber.printingMaterial();
        if (material.isEmpty()) material = chamber.batchFluid();
        boolean transparent = PlasticMaterial.fromMelt(material)
            .map(PlasticMaterial::isTransparent)
            .orElse(false);
        int baseColor = (transparent ? CLEAR_PLASTIC_PREVIEW_ALPHA : 0xFF000000)
            | DyeableMaterial.tint(PlasticMeltColor.get(material));
        if (transparent) {
            renderPrintedGeometry(
                bufferSource,
                MoldingProjectionRenderTypes.depth(true),
                pose,
                front,
                cached.geometry,
                0xFFFFFFFF,
                1.0F
            );
            MoldingProjectionRenderTypes.endDepth(bufferSource);
            renderPrintedGeometry(
                bufferSource,
                MoldingProjectionRenderTypes.color(true),
                pose,
                front,
                cached.geometry,
                baseColor,
                brightness
            );
            MoldingProjectionRenderTypes.endColor(bufferSource);
            return;
        }
        renderPrintedGeometry(
            bufferSource,
            RenderType.debugQuads(),
            pose,
            front,
            cached.geometry,
            baseColor,
            brightness
        );
    }

    private static void renderPrintedGeometry(
        MultiBufferSource bufferSource,
        RenderType renderType,
        Matrix4f pose,
        Direction front,
        MoldingPrintedGeometry geometry,
        int baseColor,
        float brightness
    ) {
        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        for (MoldingPrintedGeometry.PrintedTriangle triangle : geometry.surfaceTriangles()) {
            renderPrintedTriangle(consumer, pose, front, triangle, baseColor, brightness);
        }
        for (MoldingPrintedGeometry.PrintedTriangle triangle : geometry.capTriangles()) {
            renderPrintedTriangle(consumer, pose, front, triangle, baseColor, brightness);
        }
    }

    private static void renderPrintedTriangle(
        VertexConsumer consumer,
        Matrix4f pose,
        Direction front,
        MoldingPrintedGeometry.PrintedTriangle triangle,
        int baseColor,
        float brightness
    ) {
        int color = shadePrintedColor(baseColor, triangle.normal(), brightness);
        renderTriangle(consumer, pose, front, triangle, color, false);
        if (triangle.doubleSided()) renderTriangle(consumer, pose, front, triangle, color, true);
    }

    private static void renderTriangle(
        VertexConsumer consumer,
        Matrix4f pose,
        Direction front,
        MoldingPrintedGeometry.PrintedTriangle triangle,
        int color,
        boolean reverse
    ) {
        MoldingVec3 first = reverse ? triangle.third() : triangle.first();
        MoldingVec3 second = triangle.second();
        MoldingVec3 third = reverse ? triangle.first() : triangle.third();
        addMappedVertex(consumer, pose, front, first, color);
        addMappedVertex(consumer, pose, front, second, color);
        addMappedVertex(consumer, pose, front, third, color);
        addMappedVertex(consumer, pose, front, third, color);
    }

    private void renderPrinter(
        PlasticMoldingChamberBlockEntity chamber,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight
    ) {
        MoldingVec3 head = printingHead(chamber, partialTick);
        MoldingVec3 movement = head.subtract(MoldingPrinterMotion.DEFAULT_POSITION);
        double moveX = movement.x();
        double moveY = movement.y();
        double moveZ = movement.z();
        double doorProgress = chamber.printingDoorProgress(partialTick);
        renderPrinterModel(
            chamber, poseStack, bufferSource, packedLight, Sheets.cutoutBlockSheet(),
            PRINTER_FRAME_MODEL, 0.0D, 0.0D, 0.0D
        );
        renderPrinterModel(
            chamber, poseStack, bufferSource, packedLight, Sheets.cutoutBlockSheet(),
            PRINTER_Y_MODEL, 0.0D, moveY, 0.0D
        );
        renderPrinterModel(
            chamber, poseStack, bufferSource, packedLight, Sheets.cutoutBlockSheet(),
            PRINTER_X_MODEL, moveX, moveY, 0.0D
        );
        renderPrinterModel(
            chamber, poseStack, bufferSource, packedLight, Sheets.cutoutBlockSheet(),
            PRINTER_Z_MODEL, moveX, moveY, moveZ
        );
        renderPrinterModel(
            chamber, poseStack, bufferSource, packedLight, Sheets.translucentCullBlockSheet(),
            PRINTER_GLASS_MODEL, 0.0D, 0.0D, 0.0D
        );
        renderPrinterModel(
            chamber, poseStack, bufferSource, packedLight, Sheets.cutoutBlockSheet(),
            PRINTER_DOOR_LEFT_MODEL, doorProgress * 16.0D, 0.0D, 0.0D
        );
        renderPrinterModel(
            chamber, poseStack, bufferSource, packedLight, Sheets.cutoutBlockSheet(),
            PRINTER_DOOR_RIGHT_MODEL, doorProgress * -16.0D, 0.0D, 0.0D
        );
        renderPrinterEye(chamber, poseStack, bufferSource, moveX, moveY, moveZ);
    }

    private static MoldingVec3 printingHead(PlasticMoldingChamberBlockEntity chamber, float partialTick) {
        Level level = chamber.getLevel();
        if (level == null) return MoldingPrinterMotion.DEFAULT_POSITION;
        return chamber.printingMotion().position(level.getGameTime() + partialTick);
    }

    private void renderPrinterModel(
        PlasticMoldingChamberBlockEntity chamber,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        RenderType renderType,
        ModelResourceLocation modelLocation,
        double moveX,
        double moveY,
        double moveZ
    ) {
        Direction front = chamber.getBlockState().getValue(PlasticMoldingChamberBlock.FACING);
        Vec3 origin = PlasticMoldingChamberStructure.worldAlignedProjectionOffset(
            front,
            PRINTER_MODEL_ORIGIN.x() + moveX,
            PRINTER_MODEL_ORIGIN.y() + moveY,
            PRINTER_MODEL_ORIGIN.z() + moveZ
        );
        BlockState state = chamber.getBlockState();
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(modelLocation);
        poseStack.pushPose();
        poseStack.translate(origin.x, origin.y, origin.z);
        this.dispatcher.getModelRenderer().renderModel(
            poseStack.last(),
            bufferSource.getBuffer(renderType),
            state,
            model,
            1.0F,
            1.0F,
            1.0F,
            packedLight,
            OverlayTexture.NO_OVERLAY
        );
        poseStack.popPose();
    }

    private void renderPrinterEye(
        PlasticMoldingChamberBlockEntity chamber,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        double moveX,
        double moveY,
        double moveZ
    ) {
        Direction front = chamber.getBlockState().getValue(PlasticMoldingChamberBlock.FACING);
        Vec3 origin = PlasticMoldingChamberStructure.worldAlignedProjectionOffset(
            front,
            PRINTER_MODEL_ORIGIN.x() + moveX,
            PRINTER_MODEL_ORIGIN.y() + moveY,
            PRINTER_MODEL_ORIGIN.z() + moveZ
        );
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(PRINTER_Z_EYE_MODEL);
        poseStack.pushPose();
        poseStack.translate(origin.x, origin.y, origin.z);
        this.dispatcher.getModelRenderer().renderModel(
            poseStack.last(),
            bufferSource.getBuffer(RenderType.cutout()),
            null,
            model,
            0.0F,
            0.0F,
            0.0F,
            LightTexture.FULL_BLOCK,
            OverlayTexture.NO_OVERLAY
        );
        poseStack.popPose();
    }

    private static void addMappedVertex(
        VertexConsumer consumer,
        Matrix4f pose,
        Direction front,
        MoldingVec3 point,
        int color
    ) {
        Vec3 offset = PlasticMoldingChamberStructure.worldAlignedProjectionOffset(
            front,
            point.x(),
            point.y(),
            point.z()
        );
        consumer.addVertex(pose, (float) offset.x, (float) offset.y, (float) offset.z).setColor(color);
    }

    private static int shadePrintedColor(int color, MoldingVec3 normal, float worldBrightness) {
        double diffuse = Math.max(
            0.0D,
            normal.x() * -0.4D + normal.y() * 0.85D + normal.z() * -0.35D
        );
        float brightness = (float) ((0.45D + 0.55D * diffuse) * worldBrightness);
        return modulateColor(color, brightness);
    }

    private static void renderMold(
        PlasticMoldingChamberBlockEntity chamber,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource
    ) {
        Level level = chamber.getLevel();
        if (level == null) return;
        Direction front = chamber.getBlockState().getValue(PlasticMoldingChamberBlock.FACING);
        int completedLayers = Math.round(
            chamber.moldFillFraction(partialTick) * PlasticMoldingChamberBlockEntity.MOLD_FILL_LAYERS
        );
        for (BlockPos region : PlasticMoldingChamberStructure.regionPositions(chamber.getBlockPos(), front)) {
            int layer = region.getY() - chamber.getBlockPos().getY();
            if (layer >= completedLayers) continue;
            BlockPos offset = region.subtract(chamber.getBlockPos());
            poseStack.pushPose();
            poseStack.translate(offset.getX(), offset.getY(), offset.getZ());
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                Blocks.CLAY.defaultBlockState(),
                poseStack,
                bufferSource,
                LevelRenderer.getLightColor(level, region),
                OverlayTexture.NO_OVERLAY
            );
            poseStack.popPose();
        }
    }

    private static void renderHologramSurfaces(
        EditorSceneMesh mesh,
        PlasticMoldingChamberBlockEntity chamber,
        Direction front,
        Matrix4f pose,
        MultiBufferSource bufferSource
    ) {
        Level level = chamber.getLevel();
        int skyDarken = level == null ? 0 : level.getSkyDarken();
        Map<BlockPos, Float> lightCache = level == null ? Map.of() : new HashMap<>();
        submitHologramSurfaces(mesh, chamber, front, pose, bufferSource, lightCache, skyDarken, true);
        MoldingProjectionRenderTypes.endDepth(bufferSource);
        submitHologramSurfaces(mesh, chamber, front, pose, bufferSource, lightCache, skyDarken, false);
        MoldingProjectionRenderTypes.endColor(bufferSource);
    }

    private static void submitHologramSurfaces(
        EditorSceneMesh mesh,
        PlasticMoldingChamberBlockEntity chamber,
        Direction front,
        Matrix4f pose,
        MultiBufferSource bufferSource,
        Map<BlockPos, Float> lightCache,
        int skyDarken,
        boolean depthPass
    ) {
        Level level = chamber.getLevel();
        for (EditorScenePart part : mesh.parts()) {
            boolean volume = part.phase() == EditorDrawPhase.MANUFACTURING_SURFACE;
            if (!volume && part.phase() != EditorDrawPhase.ZERO_THICKNESS_SURFACE) continue;
            VertexConsumer consumer = bufferSource.getBuffer(
                depthPass
                    ? MoldingProjectionRenderTypes.depth(volume)
                    : MoldingProjectionRenderTypes.color(volume)
            );
            for (int index : part.indices()) {
                EditorVertex vertex = part.vertices().get(index);
                Vec3 offset = PlasticMoldingChamberStructure.worldAlignedProjectionOffset(
                    front,
                    vertex.x(),
                    vertex.y() + WORLD_PROJECTION_LIFT_PIXELS,
                    vertex.z()
                );
                int color = 0xFFFFFFFF;
                if (!depthPass) {
                    BlockPos worldPos = BlockPos.containing(
                        offset.add(Vec3.atLowerCornerOf(chamber.getBlockPos()))
                    );
                    float brightness = level == null ? 1.0F : lightCache.computeIfAbsent(
                        worldPos,
                        pos -> worldBrightness(LevelRenderer.getLightColor(level, pos), skyDarken)
                    );
                    color = hologramColor(vertex.color(), brightness);
                }
                consumer.addVertex(pose, (float) offset.x, (float) offset.y, (float) offset.z)
                    .setColor(color);
            }
        }
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
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());
        for (EditorScenePart part : mesh.parts()) {
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

    private static int hologramColor(int color, float brightness) {
        int shaded = modulateColor(color, brightness);
        int alpha = Math.round(255.0F * PROJECTION_HOLOGRAM_ALPHA);
        return alpha << 24 | shaded & 0x00FFFFFF;
    }

    private static int modulateColor(int color, float brightness) {
        int alpha = color >>> 24;
        int red = Math.clamp(Math.round(((color >> 16) & 0xFF) * brightness), 0, 255);
        int green = Math.clamp(Math.round(((color >> 8) & 0xFF) * brightness), 0, 255);
        int blue = Math.clamp(Math.round((color & 0xFF) * brightness), 0, 255);
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
        Optional<MoldingModelBounds> modelBounds = MoldingModelBounds.all(chamber.model());
        double minX = modelBounds.map(modelRange -> modelRange.minimum().x()).orElse(0.0D);
        double minY = modelBounds.map(modelRange -> modelRange.minimum().y()).orElse(0.0D);
        double minZ = modelBounds.map(modelRange -> modelRange.minimum().z()).orElse(0.0D);
        double maxX = modelBounds.map(modelRange -> modelRange.maximum().x()).orElse(48.0D);
        double maxY = modelBounds.map(modelRange -> modelRange.maximum().y()).orElse(48.0D);
        double maxZ = modelBounds.map(modelRange -> modelRange.maximum().z()).orElse(48.0D);
        if (chamber.printingComponentPresent()) {
            minX = Math.min(minX, 0.0D);
            minY = Math.min(minY, 0.0D);
            minZ = Math.min(minZ, 0.0D);
            maxX = Math.max(maxX, 48.0D);
            maxY = Math.max(maxY, PRINTER_RENDER_MAX_Y);
            maxZ = Math.max(maxZ, 48.0D);
        }
        for (double x : new double[]{minX, maxX}) {
            for (double y : new double[]{minY, maxY}) {
                for (double z : new double[]{minZ, maxZ}) {
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

    private static final class CachedPrintedGeometry {
        private final String modelHash;
        private final MoldingPrintedGeometry geometry;

        private CachedPrintedGeometry(String modelHash, MoldingPrintedGeometry geometry) {
            this.modelHash = modelHash;
            this.geometry = geometry;
        }

        private static CachedPrintedGeometry empty(String modelHash) {
            return new CachedPrintedGeometry(modelHash, MoldingPrintedGeometry.empty());
        }
    }
}
