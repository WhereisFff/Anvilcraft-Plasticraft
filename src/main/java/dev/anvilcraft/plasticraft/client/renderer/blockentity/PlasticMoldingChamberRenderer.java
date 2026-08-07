package dev.anvilcraft.plasticraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberStructure;
import dev.anvilcraft.plasticraft.block.PlasticMoldingMachineState;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorDrawPhase;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorSceneMesh;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorScenePart;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorVertex;
import dev.anvilcraft.plasticraft.client.molding.scene.MoldingSceneBuilder;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.bake.MoldingQuad;
import dev.anvilcraft.plasticraft.molding.bake.MoldingVolumeMask;
import dev.anvilcraft.plasticraft.molding.machine.MoldingFormingMode;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPrintingPlan;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPrintingVoxel;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelBounds;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** 用同一世界坐标系绘制编辑投影、粘土模具和逐体素打印机构。 */
public final class PlasticMoldingChamberRenderer implements BlockEntityRenderer<PlasticMoldingChamberBlockEntity> {
    private static final double WORLD_PROJECTION_LIFT_PIXELS = 0.05D;
    private static final double CLIP_EPSILON = 1.0E-7D;
    private static final int FRAME_COLOR = 0xFF747E86;
    private static final int FRAME_DARK_COLOR = 0xFF4B535A;
    private static final int X_AXIS_COLOR = 0xFFE85252;
    private static final int Y_AXIS_COLOR = 0xFF5EC870;
    private static final int Z_AXIS_COLOR = 0xFF578BE4;
    private static final int NOZZLE_COLOR = 0xFFE7EEF1;
    private static final int[][] BOX_FACES = {
        {0, 3, 7, 4}, {1, 5, 6, 2},
        {0, 4, 5, 1}, {3, 2, 6, 7},
        {0, 1, 2, 3}, {4, 7, 6, 5}
    };
    private final Map<PlasticMoldingChamberBlockEntity, CachedProjection> projectionCache = new WeakHashMap<>();
    private final Map<PlasticMoldingChamberBlockEntity, CachedPrintedGeometry> printedCache = new WeakHashMap<>();

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
        if (shouldRenderProjection(chamber)) {
            renderProjection(chamber, poseStack, bufferSource);
        }
        if (chamber.isPrintingProcess()) {
            renderPrintedSurface(chamber, poseStack, bufferSource, packedLight);
        }
        if (chamber.printingComponentPresent()) {
            renderPrinter(chamber, poseStack, bufferSource);
        }
        if (chamber.hasMoldCollision()) {
            renderMold(chamber, partialTick, poseStack, bufferSource);
        }
    }

    private static boolean shouldRenderProjection(PlasticMoldingChamberBlockEntity chamber) {
        PlasticMoldingMachineState state = chamber.machineState();
        if (state == PlasticMoldingMachineState.EDITABLE
            || state == PlasticMoldingMachineState.WAITING_TO_LOCK
            || state == PlasticMoldingMachineState.MOLD_FILLING) {
            return true;
        }
        return chamber.cycleFormingMode() == MoldingFormingMode.PRINTING
            && (state == PlasticMoldingMachineState.MOLD_READY
                || state == PlasticMoldingMachineState.PROCESS_READY);
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
            cameraPosition
        );
        Matrix4f pose = poseStack.last().pose();
        renderMesh(cached.mesh, chamber, front, pose, bufferSource, true);
        renderMesh(guides, chamber, front, pose, bufferSource, false);
    }

    private void renderPrintedSurface(
        PlasticMoldingChamberBlockEntity chamber,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight
    ) {
        String modelHash = chamber.bakedModel().modelHash();
        CachedPrintedGeometry cached = this.printedCache.get(chamber);
        if (cached == null || !cached.modelHash.equals(modelHash)) {
            try {
                cached = buildPrintedGeometry(chamber, modelHash);
            } catch (RuntimeException exception) {
                AnvilcraftPlasticraft.LOGGER.debug(
                    "Unable to build plastic printing preview at {}",
                    chamber.getBlockPos(),
                    exception
                );
                cached = new CachedPrintedGeometry(modelHash, List.of());
            }
            this.printedCache.put(chamber, cached);
        }
        if (cached.cells.isEmpty() || chamber.printingProgress() <= 0) return;

        Direction front = chamber.getBlockState().getValue(PlasticMoldingChamberBlock.FACING);
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugSectionQuads());
        int skyDarken = chamber.getLevel() == null ? 0 : chamber.getLevel().getSkyDarken();
        float brightness = worldBrightness(packedLight, skyDarken);
        int baseColor = 0xFF000000 | DyeableMaterial.tint(PlasticMeltColor.get(chamber.batchFluid()));
        MoldingPrintingPlan plan = chamber.printingPlan();
        int completed = Math.min(chamber.printingProgress(), plan.size());
        for (PrintedCell cell : cached.cells) {
            if (cell.completedAt > completed) break;
            for (PrintedTriangle triangle : cell.triangles) {
                int color = shadePrintedColor(baseColor, triangle.normal, brightness);
                renderTriangle(consumer, pose, front, triangle, color, false);
                if (triangle.doubleSided) {
                    renderTriangle(consumer, pose, front, triangle, color, true);
                }
            }
        }
    }

    private static CachedPrintedGeometry buildPrintedGeometry(
        PlasticMoldingChamberBlockEntity chamber,
        String modelHash
    ) {
        MoldingPrintingPlan plan = chamber.printingPlan();
        MoldingVolumeMask mask = plan.voxelMask();
        Map<Integer, List<PrintedTriangle>> fragments = new HashMap<>();
        List<MoldingQuad> surface = MoldingModelBaker.createManufacturedGeometry(
            chamber.model(),
            1.0D
        ).surfaceMesh();
        for (MoldingQuad quad : surface) {
            double minX = minimum(quad, 0);
            double maxX = maximum(quad, 0);
            double minY = minimum(quad, 1);
            double maxY = maximum(quad, 1);
            double minZ = minimum(quad, 2);
            double maxZ = maximum(quad, 2);
            CellRange xRange = cellRange(minX, maxX, mask.sizeX());
            CellRange yRange = cellRange(minY, maxY, mask.sizeY());
            CellRange zRange = cellRange(minZ, maxZ, mask.sizeZ());
            List<Vector3d> source = List.of(
                vector(quad.first()),
                vector(quad.second()),
                vector(quad.third()),
                vector(quad.fourth())
            );
            for (int y = yRange.from; y < yRange.to; y++) {
                for (int z = zRange.from; z < zRange.to; z++) {
                    for (int x = xRange.from; x < xRange.to; x++) {
                        if (!mask.get(x, y, z)) continue;
                        List<Vector3d> clipped = clipToCell(source, x, y, z);
                        if (clipped.size() < 3) continue;
                        List<PrintedTriangle> cell = fragments.computeIfAbsent(
                            mask.indexOf(x, y, z),
                            ignored -> new ArrayList<>()
                        );
                        for (int index = 1; index + 1 < clipped.size(); index++) {
                            Vector3d first = clipped.getFirst();
                            Vector3d second = clipped.get(index);
                            Vector3d third = clipped.get(index + 1);
                            if (new Vector3d(second).sub(first)
                                .cross(new Vector3d(third).sub(first))
                                .lengthSquared() <= CLIP_EPSILON * CLIP_EPSILON) {
                                continue;
                            }
                            cell.add(new PrintedTriangle(
                                first,
                                second,
                                third,
                                quad.normal(),
                                quad.doubleSided()
                            ));
                        }
                    }
                }
            }
        }
        List<PrintedCell> cells = new ArrayList<>(fragments.size());
        for (int index = 0; index < plan.size() && !fragments.isEmpty(); index++) {
            List<PrintedTriangle> triangles = fragments.remove(plan.cellAt(index));
            if (triangles != null) cells.add(new PrintedCell(index + 1, List.copyOf(triangles)));
        }
        return new CachedPrintedGeometry(modelHash, List.copyOf(cells));
    }

    private static List<Vector3d> clipToCell(List<Vector3d> source, int x, int y, int z) {
        List<Vector3d> clipped = clip(source, 0, x, true);
        clipped = clip(clipped, 0, x + 1.0D, false);
        clipped = clip(clipped, 1, y, true);
        clipped = clip(clipped, 1, y + 1.0D, false);
        clipped = clip(clipped, 2, z, true);
        clipped = clip(clipped, 2, z + 1.0D, false);
        return removeDuplicateEnds(clipped);
    }

    private static List<Vector3d> clip(
        List<Vector3d> input,
        int axis,
        double boundary,
        boolean keepGreater
    ) {
        if (input.isEmpty()) return List.of();
        List<Vector3d> output = new ArrayList<>(input.size() + 2);
        Vector3d previous = input.getLast();
        double previousCoordinate = coordinate(previous, axis);
        boolean previousInside = inside(previousCoordinate, boundary, keepGreater);
        for (Vector3d current : input) {
            double currentCoordinate = coordinate(current, axis);
            boolean currentInside = inside(currentCoordinate, boundary, keepGreater);
            if (currentInside != previousInside) {
                double denominator = currentCoordinate - previousCoordinate;
                if (Math.abs(denominator) > CLIP_EPSILON) {
                    double amount = Math.clamp((boundary - previousCoordinate) / denominator, 0.0D, 1.0D);
                    output.add(new Vector3d(previous).lerp(current, amount));
                }
            }
            if (currentInside) output.add(new Vector3d(current));
            previous = current;
            previousCoordinate = currentCoordinate;
            previousInside = currentInside;
        }
        return output;
    }

    private static List<Vector3d> removeDuplicateEnds(List<Vector3d> polygon) {
        if (polygon.size() < 2) return polygon;
        List<Vector3d> result = new ArrayList<>(polygon.size());
        for (Vector3d point : polygon) {
            if (result.isEmpty() || result.getLast().distanceSquared(point) > CLIP_EPSILON * CLIP_EPSILON) {
                result.add(point);
            }
        }
        if (result.size() > 1
            && result.getFirst().distanceSquared(result.getLast()) <= CLIP_EPSILON * CLIP_EPSILON) {
            result.removeLast();
        }
        return result;
    }

    private static boolean inside(double coordinate, double boundary, boolean keepGreater) {
        return keepGreater
            ? coordinate >= boundary - CLIP_EPSILON
            : coordinate <= boundary + CLIP_EPSILON;
    }

    private static CellRange cellRange(double minimum, double maximum, int size) {
        if (maximum < -CLIP_EPSILON || minimum > size + CLIP_EPSILON) return CellRange.EMPTY;
        if (maximum - minimum <= CLIP_EPSILON) {
            double rounded = Math.rint(minimum);
            if (Math.abs(minimum - rounded) <= CLIP_EPSILON) {
                int boundary = (int) rounded;
                return new CellRange(
                    Math.clamp(boundary - 1, 0, size),
                    Math.clamp(boundary + 1, 0, size)
                );
            }
            int cell = Math.clamp((int) Math.floor(minimum), 0, size - 1);
            return new CellRange(cell, cell + 1);
        }
        int from = Math.clamp((int) Math.floor(minimum + CLIP_EPSILON), 0, size);
        int to = Math.clamp((int) Math.ceil(maximum - CLIP_EPSILON), 0, size);
        return new CellRange(from, Math.max(from, to));
    }

    private static double minimum(MoldingQuad quad, int axis) {
        return Math.min(
            Math.min(coordinate(quad.first(), axis), coordinate(quad.second(), axis)),
            Math.min(coordinate(quad.third(), axis), coordinate(quad.fourth(), axis))
        );
    }

    private static double maximum(MoldingQuad quad, int axis) {
        return Math.max(
            Math.max(coordinate(quad.first(), axis), coordinate(quad.second(), axis)),
            Math.max(coordinate(quad.third(), axis), coordinate(quad.fourth(), axis))
        );
    }

    private static double coordinate(MoldingVec3 point, int axis) {
        return switch (axis) {
            case 0 -> point.x();
            case 1 -> point.y();
            case 2 -> point.z();
            default -> throw new IllegalArgumentException("Unknown molding axis " + axis);
        };
    }

    private static double coordinate(Vector3d point, int axis) {
        return switch (axis) {
            case 0 -> point.x;
            case 1 -> point.y;
            case 2 -> point.z;
            default -> throw new IllegalArgumentException("Unknown molding axis " + axis);
        };
    }

    private static void renderTriangle(
        VertexConsumer consumer,
        Matrix4f pose,
        Direction front,
        PrintedTriangle triangle,
        int color,
        boolean reverse
    ) {
        Vector3d first = reverse ? triangle.third : triangle.first;
        Vector3d second = triangle.second;
        Vector3d third = reverse ? triangle.first : triangle.third;
        addMappedVertex(consumer, pose, front, first, color);
        addMappedVertex(consumer, pose, front, second, color);
        addMappedVertex(consumer, pose, front, third, color);
        addMappedVertex(consumer, pose, front, third, color);
    }

    private static void renderPrinter(
        PlasticMoldingChamberBlockEntity chamber,
        PoseStack poseStack,
        MultiBufferSource bufferSource
    ) {
        Direction front = chamber.getBlockState().getValue(PlasticMoldingChamberBlock.FACING);
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugSectionQuads());
        MoldingPrintingVoxel head = printingHead(chamber);
        double targetX = Math.clamp(head.x() + 0.5D, 0.5D, 47.5D);
        double targetY = Math.clamp(head.y() + 0.5D, 0.5D, 47.5D);
        double targetZ = Math.clamp(head.z() + 0.5D, 0.5D, 47.5D);

        renderBox(consumer, pose, front, 0.0D, 0.0D, 47.0D, 1.0D, 50.0D, 48.0D, FRAME_DARK_COLOR);
        renderBox(consumer, pose, front, 47.0D, 0.0D, 47.0D, 48.0D, 50.0D, 48.0D, FRAME_DARK_COLOR);
        renderBox(consumer, pose, front, 0.0D, 48.0D, 47.0D, 48.0D, 50.0D, 48.0D, FRAME_COLOR);
        renderBox(consumer, pose, front, 0.0D, 48.0D, 0.0D, 1.0D, 49.0D, 48.0D, FRAME_COLOR);
        renderBox(consumer, pose, front, 47.0D, 48.0D, 0.0D, 48.0D, 49.0D, 48.0D, FRAME_COLOR);

        renderBox(
            consumer,
            pose,
            front,
            0.0D,
            50.0D,
            targetZ - 0.3D,
            48.0D,
            50.6D,
            targetZ + 0.3D,
            X_AXIS_COLOR
        );
        renderBox(
            consumer,
            pose,
            front,
            targetX - 0.3D,
            49.3D,
            0.0D,
            targetX + 0.3D,
            49.9D,
            48.0D,
            Z_AXIS_COLOR
        );
        renderBox(
            consumer,
            pose,
            front,
            targetX - 0.24D,
            targetY,
            targetZ - 0.24D,
            targetX + 0.24D,
            49.5D,
            targetZ + 0.24D,
            Y_AXIS_COLOR
        );
        renderBox(
            consumer,
            pose,
            front,
            targetX - 0.42D,
            targetY - 0.18D,
            targetZ - 0.42D,
            targetX + 0.42D,
            targetY + 0.18D,
            targetZ + 0.42D,
            NOZZLE_COLOR
        );
    }

    private static MoldingPrintingVoxel printingHead(PlasticMoldingChamberBlockEntity chamber) {
        if (!chamber.isPrintingProcess()) {
            return new MoldingPrintingVoxel(23, 47, 23);
        }
        MoldingPrintingPlan plan = chamber.printingPlan();
        if (plan.size() == 0) return new MoldingPrintingVoxel(23, 47, 23);
        int index = Math.min(chamber.printingProgress(), plan.size() - 1);
        return plan.voxelAt(index);
    }

    private static void renderBox(
        VertexConsumer consumer,
        Matrix4f pose,
        Direction front,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        int color
    ) {
        Vector3d[] corners = {
            new Vector3d(minX, minY, minZ), new Vector3d(maxX, minY, minZ),
            new Vector3d(maxX, maxY, minZ), new Vector3d(minX, maxY, minZ),
            new Vector3d(minX, minY, maxZ), new Vector3d(maxX, minY, maxZ),
            new Vector3d(maxX, maxY, maxZ), new Vector3d(minX, maxY, maxZ)
        };
        for (int[] face : BOX_FACES) {
            for (int index : face) addMappedVertex(consumer, pose, front, corners[index], color);
        }
    }

    private static void addMappedVertex(
        VertexConsumer consumer,
        Matrix4f pose,
        Direction front,
        Vector3d point,
        int color
    ) {
        Vec3 offset = PlasticMoldingChamberStructure.worldAlignedProjectionOffset(
            front,
            point.x,
            point.y,
            point.z
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

    private static Vector3d vector(MoldingVec3 point) {
        return new Vector3d(point.x(), point.y(), point.z());
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
            maxY = Math.max(maxY, 51.0D);
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

    private record CachedPrintedGeometry(String modelHash, List<PrintedCell> cells) {
    }

    private record PrintedCell(int completedAt, List<PrintedTriangle> triangles) {
    }

    private record PrintedTriangle(
        Vector3d first,
        Vector3d second,
        Vector3d third,
        MoldingVec3 normal,
        boolean doubleSided
    ) {
        private PrintedTriangle {
            first = new Vector3d(first);
            second = new Vector3d(second);
            third = new Vector3d(third);
        }
    }

    private record CellRange(int from, int to) {
        private static final CellRange EMPTY = new CellRange(0, 0);
    }
}
