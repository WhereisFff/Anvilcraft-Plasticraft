package dev.anvilcraft.plasticraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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
import dev.anvilcraft.plasticraft.molding.bake.ManufacturedMoldingGeometry;
import dev.anvilcraft.plasticraft.molding.bake.MoldingConvexHull;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.bake.MoldingQuad;
import dev.anvilcraft.plasticraft.molding.bake.MoldingVolumeMask;
import dev.anvilcraft.plasticraft.molding.machine.MoldingFormingMode;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPrintingPlan;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPrinterMotion;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelBounds;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** 用同一世界坐标系绘制编辑投影、粘土模具和逐体素打印机构。 */
public final class PlasticMoldingChamberRenderer implements BlockEntityRenderer<PlasticMoldingChamberBlockEntity> {
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
    private static final double CLIP_EPSILON = 1.0E-7D;
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
        if (shouldRenderProjection(chamber)) {
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

    private static boolean shouldRenderProjection(PlasticMoldingChamberBlockEntity chamber) {
        MoldingFormingMode formingMode = chamber.isLocked()
            ? chamber.cycleFormingMode()
            : chamber.formingMode();
        if (formingMode == MoldingFormingMode.PRINTING) return !chamber.isPrintingProcess();
        PlasticMoldingMachineState state = chamber.machineState();
        if (state == PlasticMoldingMachineState.EDITABLE
            || state == PlasticMoldingMachineState.WAITING_TO_LOCK
            || state == PlasticMoldingMachineState.MOLD_FILLING) {
            return true;
        }
        return false;
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
                cached = CachedPrintedGeometry.empty(modelHash);
            }
            this.printedCache.put(chamber, cached);
        }
        int completed = Math.min(chamber.printingProgress(), chamber.printingPlan().size());
        if (completed <= 0) {
            cached.advanceTo(0);
            return;
        }
        cached.advanceTo(completed);
        if (cached.isEmpty()) return;

        Direction front = chamber.getBlockState().getValue(PlasticMoldingChamberBlock.FACING);
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugSectionQuads());
        int skyDarken = chamber.getLevel() == null ? 0 : chamber.getLevel().getSkyDarken();
        float brightness = worldBrightness(packedLight, skyDarken);
        FluidStack material = chamber.printingMaterial();
        if (material.isEmpty()) material = chamber.batchFluid();
        int baseColor = 0xFF000000 | DyeableMaterial.tint(PlasticMeltColor.get(material));
        for (PrintedTriangle triangle : cached.permanentTriangles) {
            renderPrintedTriangle(consumer, pose, front, triangle, baseColor, brightness);
        }
        for (List<PrintedTriangle> cap : cached.caps.values()) {
            for (PrintedTriangle triangle : cap) {
                renderPrintedTriangle(consumer, pose, front, triangle, baseColor, brightness);
            }
        }
    }

    private static void renderPrintedTriangle(
        VertexConsumer consumer,
        Matrix4f pose,
        Direction front,
        PrintedTriangle triangle,
        int baseColor,
        float brightness
    ) {
        int color = shadePrintedColor(baseColor, triangle.normal, brightness);
        renderTriangle(consumer, pose, front, triangle, color, false);
        if (triangle.doubleSided) renderTriangle(consumer, pose, front, triangle, color, true);
    }

    private static CachedPrintedGeometry buildPrintedGeometry(
        PlasticMoldingChamberBlockEntity chamber,
        String modelHash
    ) {
        MoldingPrintingPlan plan = chamber.printingPlan();
        MoldingVolumeMask mask = plan.voxelMask();
        ManufacturedMoldingGeometry geometry = MoldingModelBaker.createManufacturedGeometry(
            chamber.model(),
            1.0D
        );
        List<HullCellGeometry> hulls = geometry.collisionHulls().stream()
            .map(hull -> new HullCellGeometry(hull, hull.bounds()))
            .toList();
        Map<Integer, List<PrintedTriangle>> zeroThickness = new HashMap<>();
        for (MoldingQuad quad : geometry.surfaceMesh()) {
            if (!quad.doubleSided()) continue;
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
                        List<PrintedTriangle> cell = zeroThickness.computeIfAbsent(
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
                                true
                            ));
                        }
                    }
                }
            }
        }
        for (Map.Entry<Integer, List<PrintedTriangle>> entry : zeroThickness.entrySet()) {
            entry.setValue(List.copyOf(entry.getValue()));
        }
        return new CachedPrintedGeometry(modelHash, plan, hulls, Map.copyOf(zeroThickness));
    }

    private static CapKey capKey(int hullIndex, MoldingQuad quad, int x, int y, int z) {
        MoldingVec3 normal = quad.normal();
        if (Math.abs(Math.abs(normal.x()) - 1.0D) <= CLIP_EPSILON
            && Math.abs(normal.y()) <= CLIP_EPSILON
            && Math.abs(normal.z()) <= CLIP_EPSILON) {
            if (onPlane(quad, 0, x)) return new CapKey(hullIndex, 0, x, y, z);
            if (onPlane(quad, 0, x + 1.0D)) return new CapKey(hullIndex, 0, x + 1, y, z);
        }
        if (Math.abs(Math.abs(normal.y()) - 1.0D) <= CLIP_EPSILON
            && Math.abs(normal.x()) <= CLIP_EPSILON
            && Math.abs(normal.z()) <= CLIP_EPSILON) {
            if (onPlane(quad, 1, y)) return new CapKey(hullIndex, 1, y, x, z);
            if (onPlane(quad, 1, y + 1.0D)) return new CapKey(hullIndex, 1, y + 1, x, z);
        }
        if (Math.abs(Math.abs(normal.z()) - 1.0D) <= CLIP_EPSILON
            && Math.abs(normal.x()) <= CLIP_EPSILON
            && Math.abs(normal.y()) <= CLIP_EPSILON) {
            if (onPlane(quad, 2, z)) return new CapKey(hullIndex, 2, z, x, y);
            if (onPlane(quad, 2, z + 1.0D)) return new CapKey(hullIndex, 2, z + 1, x, y);
        }
        return null;
    }

    private static boolean onPlane(MoldingQuad quad, int axis, double coordinate) {
        return Math.abs(coordinate(quad.first(), axis) - coordinate) <= CLIP_EPSILON
            && Math.abs(coordinate(quad.second(), axis) - coordinate) <= CLIP_EPSILON
            && Math.abs(coordinate(quad.third(), axis) - coordinate) <= CLIP_EPSILON
            && Math.abs(coordinate(quad.fourth(), axis) - coordinate) <= CLIP_EPSILON;
    }

    private static void addQuadTriangles(
        List<PrintedTriangle> output,
        MoldingQuad quad,
        boolean doubleSided
    ) {
        addTriangle(output, quad.first(), quad.second(), quad.third(), quad.normal(), doubleSided);
        addTriangle(output, quad.first(), quad.third(), quad.fourth(), quad.normal(), doubleSided);
    }

    private static void addTriangle(
        List<PrintedTriangle> output,
        MoldingVec3 first,
        MoldingVec3 second,
        MoldingVec3 third,
        MoldingVec3 normal,
        boolean doubleSided
    ) {
        Vector3d firstVector = vector(first);
        Vector3d secondVector = vector(second);
        Vector3d thirdVector = vector(third);
        if (new Vector3d(secondVector).sub(firstVector)
            .cross(new Vector3d(thirdVector).sub(firstVector))
            .lengthSquared() <= CLIP_EPSILON * CLIP_EPSILON) {
            return;
        }
        output.add(new PrintedTriangle(firstVector, secondVector, thirdVector, normal, doubleSided));
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
        private final MoldingPrintingPlan plan;
        private final MoldingVolumeMask mask;
        private final List<HullCellGeometry> hulls;
        private final Map<Integer, List<PrintedTriangle>> zeroThickness;
        private final List<PrintedTriangle> permanentTriangles = new ArrayList<>();
        private final Map<CapKey, List<PrintedTriangle>> caps = new HashMap<>();
        private int completed;

        private CachedPrintedGeometry(
            String modelHash,
            MoldingPrintingPlan plan,
            List<HullCellGeometry> hulls,
            Map<Integer, List<PrintedTriangle>> zeroThickness
        ) {
            this.modelHash = modelHash;
            this.plan = plan;
            this.mask = plan.voxelMask();
            this.hulls = List.copyOf(hulls);
            this.zeroThickness = Map.copyOf(zeroThickness);
        }

        private static CachedPrintedGeometry empty(String modelHash) {
            return new CachedPrintedGeometry(
                modelHash,
                new MoldingPrintingPlan(new MoldingVolumeMask(), new int[0]),
                List.of(),
                Map.of()
            );
        }

        private boolean isEmpty() {
            return this.permanentTriangles.isEmpty() && this.caps.isEmpty();
        }

        private void advanceTo(int requestedCompleted) {
            int target = Math.clamp(requestedCompleted, 0, this.plan.size());
            if (target < this.completed) {
                this.completed = 0;
                this.permanentTriangles.clear();
                this.caps.clear();
            }
            while (this.completed < target) {
                int cell = this.plan.cellAt(this.completed++);
                int x = this.mask.xOf(cell);
                int y = this.mask.yOf(cell);
                int z = this.mask.zOf(cell);
                for (int hullIndex = 0; hullIndex < this.hulls.size(); hullIndex++) {
                    HullCellGeometry source = this.hulls.get(hullIndex);
                    if (!source.mayIntersect(x, y, z)) continue;
                    for (MoldingQuad quad : MoldingModelBaker.clipConvexHullToCell(source.hull, x, y, z)) {
                        CapKey cap = capKey(hullIndex, quad, x, y, z);
                        if (cap == null) {
                            addQuadTriangles(this.permanentTriangles, quad, false);
                            continue;
                        }
                        List<PrintedTriangle> triangles = new ArrayList<>(2);
                        addQuadTriangles(triangles, quad, false);
                        if (this.caps.remove(cap) == null && !triangles.isEmpty()) {
                            this.caps.put(cap, List.copyOf(triangles));
                        }
                    }
                }
                this.permanentTriangles.addAll(this.zeroThickness.getOrDefault(cell, List.of()));
            }
        }
    }

    private record HullCellGeometry(MoldingConvexHull hull, MoldingConvexHull.Bounds bounds) {
        private boolean mayIntersect(int x, int y, int z) {
            return this.bounds.maximum().x() > x + CLIP_EPSILON
                && this.bounds.minimum().x() < x + 1.0D - CLIP_EPSILON
                && this.bounds.maximum().y() > y + CLIP_EPSILON
                && this.bounds.minimum().y() < y + 1.0D - CLIP_EPSILON
                && this.bounds.maximum().z() > z + CLIP_EPSILON
                && this.bounds.minimum().z() < z + 1.0D - CLIP_EPSILON;
        }
    }

    private record CapKey(int hull, int axis, int plane, int first, int second) {
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
