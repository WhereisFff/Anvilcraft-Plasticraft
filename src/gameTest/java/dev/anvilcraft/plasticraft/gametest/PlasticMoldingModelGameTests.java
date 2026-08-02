package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberStructure;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingAxis;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingCamera;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingDragTransaction;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingEditorController;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingGizmoGeometry;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingGuiTransform;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingHitTester;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingNumericProperty;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingSelection;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingTool;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingToolButtonState;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingViewPreset;
import dev.anvilcraft.plasticraft.client.molding.editor.ViewportRay;
import dev.anvilcraft.plasticraft.client.molding.editor.ViewportTransform;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorDrawPhase;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorSceneMesh;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorScenePart;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorVertex;
import dev.anvilcraft.plasticraft.client.molding.scene.MoldingSceneBuilder;
import dev.anvilcraft.plasticraft.client.molding.scene.MoldingWorkspaceOutline;
import dev.anvilcraft.plasticraft.client.renderer.molding.MoldingViewportTargetLifecycle;
import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelHasher;
import dev.anvilcraft.plasticraft.molding.bake.MoldingVolumeMask;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingCommand;
import dev.anvilcraft.plasticraft.molding.model.MoldingCoordinateSystem;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingGroup;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelPersistence;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelStreams;
import dev.anvilcraft.plasticraft.molding.model.MoldingTransform;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** TODO-01 模型格式、制造烘焙和纯 CPU 编辑器数学回归测试。 */
public final class PlasticMoldingModelGameTests {
    private static final double EPSILON = 1.0E-5D;
    private static final long MAX_FULL_BAKE_NANOS = 5_000_000_000L;

    private PlasticMoldingModelGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("1x1x1")
    @TestHolder(description = "Molding model Codec and bounded network streams round-trip every object field")
    static void modelAndNetworkRoundTrip(ExtendedGameTestHelper helper) {
        UUID groupId = uuid(1);
        MoldingGroup group = new MoldingGroup(
            groupId,
            "Body",
            Optional.empty(),
            new MoldingTransform(
                new MoldingVec3(1.0D, 2.0D, 3.0D),
                new MoldingVec3(0.0D, 90.0D, 0.0D),
                MoldingVec3.ONE,
                new MoldingVec3(24.0D, 24.0D, 24.0D)
            ),
            true,
            false
        );
        MoldingElement cube = element(
            uuid(2),
            "Cube",
            Optional.of(groupId),
            new MoldingVec3(20.0D, 20.0D, 20.0D),
            new MoldingVec3(28.0D, 28.0D, 28.0D),
            MoldingTransform.IDENTITY
        );
        MoldingElement zeroThicknessCube = element(
            uuid(3),
            "Flat cube",
            Optional.empty(),
            new MoldingVec3(8.0D, 8.0D, 12.0D),
            new MoldingVec3(16.0D, 16.0D, 12.0D),
            MoldingTransform.IDENTITY
        );
        EditableMoldingModel model = model(List.of(cube, zeroThicknessCube), List.of(group));

        CompoundTag saved = MoldingModelPersistence.save(model);
        check(MoldingModelPersistence.load(saved).equals(model), "NBT Codec round-trip changed the model");
        CompoundTag legacyCoordinates = saved.copy();
        legacyCoordinates.putInt("format_version", EditableMoldingModel.CURRENT_FORMAT_VERSION - 1);
        boolean rejectedLegacyCoordinates = false;
        try {
            MoldingModelPersistence.load(legacyCoordinates);
        } catch (IllegalArgumentException exception) {
            rejectedLegacyCoordinates = true;
        }
        check(rejectedLegacyCoordinates, "legacy machine-local coordinates were silently read as world coordinates");
        check(saved.getList("elements", Tag.TAG_COMPOUND).stream()
            .map(CompoundTag.class::cast)
            .noneMatch(element -> element.contains("kind")), "saved cube retained a separate kind field");

        RegistryFriendlyByteBuf modelBuffer = new RegistryFriendlyByteBuf(
            Unpooled.buffer(),
            helper.getLevel().registryAccess()
        );
        try {
            MoldingModelStreams.writeModel(modelBuffer, model);
            check(MoldingModelStreams.readModel(modelBuffer).equals(model), "network model round-trip changed the model");
        } finally {
            modelBuffer.release();
        }

        MoldingCommand command = new MoldingCommand.Batch(List.of(
            new MoldingCommand.ReplaceElement(cube),
            new MoldingCommand.ReplaceGroup(group)
        ));
        RegistryFriendlyByteBuf commandBuffer = new RegistryFriendlyByteBuf(
            Unpooled.buffer(),
            helper.getLevel().registryAccess()
        );
        try {
            MoldingModelStreams.writeCommand(commandBuffer, command);
            check(MoldingModelStreams.readCommand(commandBuffer).equals(command), "network command round-trip changed fields");
        } finally {
            commandBuffer.release();
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("1x1x1")
    @TestHolder(description = "Signed cube dimensions survive persistence and behave consistently throughout the editor")
    static void signedCubeDimensions(ExtendedGameTestHelper helper) {
        MoldingElement negativeCube = cube(
            uuid(4),
            28.0D,
            28.0D,
            28.0D,
            20.0D,
            24.0D,
            22.0D
        );
        EditableMoldingModel negativeModel = model(List.of(negativeCube), List.of());

        CompoundTag saved = MoldingModelPersistence.save(negativeModel);
        EditableMoldingModel loaded = MoldingModelPersistence.load(saved);
        check(loaded.equals(negativeModel), "NBT Codec normalized a negative cube dimension");
        check(loaded.elements().getFirst().from().equals(negativeCube.from())
                && loaded.elements().getFirst().to().equals(negativeCube.to()),
            "NBT Codec swapped the signed cube endpoints");

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
            Unpooled.buffer(),
            helper.getLevel().registryAccess()
        );
        try {
            MoldingModelStreams.writeModel(buffer, negativeModel);
            MoldingElement streamed = MoldingModelStreams.readModel(buffer).elements().getFirst();
            check(streamed.from().equals(negativeCube.from()) && streamed.to().equals(negativeCube.to()),
                "network round-trip swapped the signed cube endpoints");
        } finally {
            buffer.release();
        }

        MoldingElement sameBoundsPositiveCube = cube(
            negativeCube.id(),
            20.0D,
            24.0D,
            22.0D,
            28.0D,
            28.0D,
            28.0D
        );
        check(!MoldingModelHasher.hash(negativeModel, MoldingModelBaker.BAKE_VERSION).equals(
                MoldingModelHasher.hash(model(List.of(sameBoundsPositiveCube), List.of()), MoldingModelBaker.BAKE_VERSION)
            ),
            "model hash discarded signed endpoint order");

        BakedMoldingModel negativeBake = MoldingModelBaker.bake(negativeModel);
        check(negativeBake.analysis().volume() == 192, "negative cube baked with the wrong physical volume");
        check(negativeBake.volumeMask().get(20, 24, 22) && negativeBake.volumeMask().get(27, 27, 27),
            "negative cube did not occupy its actual endpoint bounds");
        check(!negativeBake.volumeMask().get(19, 24, 22) && !negativeBake.volumeMask().get(28, 24, 22),
            "negative cube escaped its actual endpoint bounds");

        ViewportRay negativeRay = new ViewportRay(
            new Vector3d(24.0D, 26.0D, 0.0D),
            new Vector3d(0.0D, 0.0D, 1.0D)
        );
        MoldingHitTester.ElementHit negativeHit = MoldingHitTester.hitElement(negativeModel, negativeRay)
            .orElseThrow(() -> new GameTestAssertException("ray missed a negative cube"));
        check(negativeHit.id().equals(negativeCube.id()) && close(negativeHit.point().z, 22.0D),
            "ray hit the wrong negative cube surface");

        EditorScenePart negativeEditorPart = MoldingSceneBuilder.buildEditor(
            0L,
            0L,
            negativeModel,
            negativeBake,
            MoldingSelection.EMPTY,
            MoldingTool.NONE,
            new Vector3d(24.0D, 26.0D, 25.0D),
            0.25D,
            new Vector3d(0.0D, 0.0D, -1.0D),
            false,
            false,
            false
        ).parts(EditorDrawPhase.MANUFACTURING_SURFACE).getFirst();
        List<EditorVertex> editorVertices = negativeEditorPart.vertices();
        check(sourceFacesPointInward(editorVertices), "negative cube editor faces did not preserve inward winding");
        check(editorVertices.stream().map(EditorVertex::color).distinct().count() >= 3,
            "negative cube editor faces lost directional lighting");

        EditorScenePart negativeWorldPart = MoldingSceneBuilder.buildWorldSurfaces(0L, negativeModel)
            .parts(EditorDrawPhase.MANUFACTURING_SURFACE)
            .getFirst();
        List<EditorVertex> worldVertices = negativeWorldPart.vertices();
        check(sourceFacesPointInward(worldVertices), "negative cube world faces did not preserve inward winding");
        check(worldVertices.stream().map(EditorVertex::color).distinct().count() >= 3,
            "negative cube world faces lost directional lighting");

        MoldingElement positiveReference = cube(
            uuid(401),
            20.0D,
            24.0D,
            22.0D,
            28.0D,
            28.0D,
            28.0D
        );
        EditableMoldingModel positiveModel = model(List.of(positiveReference), List.of());
        EditorScenePart positiveEditorPart = MoldingSceneBuilder.buildEditor(
            0L,
            0L,
            positiveModel,
            MoldingModelBaker.bake(positiveModel),
            MoldingSelection.EMPTY,
            MoldingTool.NONE,
            new Vector3d(24.0D, 26.0D, 25.0D),
            0.25D,
            new Vector3d(0.0D, 0.0D, -1.0D),
            false,
            false,
            false
        ).parts(EditorDrawPhase.MANUFACTURING_SURFACE).getFirst();
        List<EditorVertex> positiveVertices = positiveEditorPart.vertices();
        int negativeTop = surfaceFaceColor(editorVertices, MoldingAxis.Y, 28.0D);
        int positiveTop = surfaceFaceColor(positiveVertices, MoldingAxis.Y, 28.0D);
        int negativeBottom = surfaceFaceColor(editorVertices, MoldingAxis.Y, 24.0D);
        int positiveBottom = surfaceFaceColor(positiveVertices, MoldingAxis.Y, 24.0D);
        check(channelBrightness(positiveTop) > channelBrightness(positiveBottom),
            "positive cube upward face was not brighter than its downward face");
        check(channelBrightness(negativeBottom) > channelBrightness(negativeTop),
            "negative cube inward lower face was not brighter than its inward upper face");
        check(negativeTop == positiveBottom && negativeBottom == positiveTop,
            "negative cube lighting did not follow its reversed face winding");

        EditorScenePart positiveWorldPart = MoldingSceneBuilder.buildWorldSurfaces(0L, positiveModel)
            .parts(EditorDrawPhase.MANUFACTURING_SURFACE)
            .getFirst();
        for (Direction front : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            UnaryOperator<Vector3d> worldProjection = point -> {
                Vec3 projected = PlasticMoldingChamberStructure.worldAlignedProjectionOffset(
                    front,
                    point.x,
                    point.y,
                    point.z
                );
                return new Vector3d(projected.x, projected.y, projected.z);
            };
            List<EditorVertex> finalPositiveWorld = transformedPartVertices(
                positiveWorldPart,
                worldProjection
            );
            List<EditorVertex> finalNegativeWorld = transformedPartVertices(
                negativeWorldPart,
                worldProjection
            );
            check(sourceFacesPointOutward(finalPositiveWorld), front + " world positive cube was rendered inward");
            check(sourceFacesPointInward(finalNegativeWorld), front + " world negative cube was rendered outward");
            check(channelBrightness(surfaceFaceColor(finalPositiveWorld, MoldingAxis.Y, 1.75D))
                    > channelBrightness(surfaceFaceColor(finalPositiveWorld, MoldingAxis.Y, 1.5D)),
                front + " world positive cube lighting did not follow its final winding");
            check(channelBrightness(surfaceFaceColor(finalNegativeWorld, MoldingAxis.Y, 1.5D))
                    > channelBrightness(surfaceFaceColor(finalNegativeWorld, MoldingAxis.Y, 1.75D)),
                front + " world negative cube lighting did not follow its final winding");
        }

        MoldingEditorController numericController = new MoldingEditorController(
            negativeModel,
            0L,
            true,
            (revision, command) -> {
            }
        );
        numericController.select(negativeCube.id(), false);
        check(numericController.numericValue(MoldingNumericProperty.SIZE, MoldingAxis.X)
                .filter(value -> value == -8.0D)
                .isPresent(),
            "numeric size field did not expose the negative dimension");
        check(numericController.setNumeric(MoldingNumericProperty.SIZE, MoldingAxis.Y, -5.0D),
            "numeric size input rejected a negative dimension");
        MoldingElement numericCube = numericController.model().elements().getFirst();
        check(numericCube.from().y() == 28.0D && numericCube.to().y() == 23.0D,
            "numeric size input normalized the negative dimension");

        MoldingElement signChangingCube = cube(uuid(402), 20.0D, 20.0D, 20.0D, 28.0D, 28.0D, 28.0D);
        MoldingEditorController signController = new MoldingEditorController(
            model(List.of(signChangingCube), List.of()),
            0L,
            true,
            (revision, command) -> {
            }
        );
        signController.select(signChangingCube.id(), false);
        check(signController.numericValue(MoldingNumericProperty.POSITION, MoldingAxis.X)
                .filter(value -> value == 4.0D)
                .isPresent(),
            "signed cube did not start at its from-anchor position");
        check(signController.setNumeric(MoldingNumericProperty.SIZE, MoldingAxis.X, -8.0D),
            "signed cube could not be changed to a negative size");
        MoldingElement negativeSized = signController.model().elements().getFirst();
        check(negativeSized.from().x() == 20.0D && negativeSized.to().x() == 12.0D,
            "changing size sign moved the from anchor");
        check(signController.numericValue(MoldingNumericProperty.POSITION, MoldingAxis.X)
                .filter(value -> value == 4.0D)
                .isPresent(),
            "changing size sign changed the cube position");
        signController.syncAuthoritative(signController.model(), 1L, true);
        check(signController.setNumeric(MoldingNumericProperty.SIZE, MoldingAxis.X, 8.0D),
            "signed cube could not be changed back to a positive size");
        MoldingElement positiveSized = signController.model().elements().getFirst();
        check(positiveSized.from().x() == 20.0D && positiveSized.to().x() == 28.0D,
            "changing size sign back moved the from anchor");
        check(signController.numericValue(MoldingNumericProperty.POSITION, MoldingAxis.X)
                .filter(value -> value == 4.0D)
                .isPresent(),
            "changing size sign back changed the cube position");

        MoldingElement positiveCube = cube(uuid(5), 20.0D, 20.0D, 20.0D, 28.0D, 28.0D, 28.0D);
        MoldingDragTransaction crossZero = new MoldingDragTransaction(
            model(List.of(positiveCube), List.of()),
            new MoldingSelection(List.of(positiveCube.id())),
            MoldingTool.SCALE,
            MoldingAxis.X
        );
        MoldingElement crossed = crossZero.preview(-10.0D).elements().getFirst();
        check(crossed.from().x() == 20.0D && crossed.to().x() == 18.0D,
            "scale drag did not cross zero into a negative dimension");
        check(MoldingModelBaker.bake(model(List.of(crossed), List.of())).analysis().volume() == 128,
            "cube dragged through zero did not retain physical volume");

        MoldingElement negativeFlatCube = cube(uuid(6), 28.0D, 28.0D, 24.0D, 20.0D, 20.0D, 24.0D);
        EditableMoldingModel negativeFlatModel = model(List.of(negativeFlatCube), List.of());
        BakedMoldingModel negativeFlatBake = MoldingModelBaker.bake(negativeFlatModel);
        check(!negativeFlatCube.hasVolume() && negativeFlatBake.analysis().volume() == 0
                && !negativeFlatBake.barrierFaces().isEmpty(),
            "negative zero-thickness cube lost its barrier geometry");
        check(MoldingHitTester.hitElement(negativeFlatModel, negativeRay).isPresent(),
            "ray missed a negative zero-thickness cube");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("1x1x1")
    @TestHolder(description = "Molding bake handles empty, overlapping, rotated, planar, boundary, cavity, and maximum models")
    static void deterministicManufacturingBake(ExtendedGameTestHelper helper) {
        BakedMoldingModel empty = MoldingModelBaker.bake(EditableMoldingModel.empty());
        check(empty.volumeMask().isEmpty(), "empty model produced volume");
        check(empty.barrierFaces().isEmpty(), "empty model produced barriers");
        check(empty.analysis().minimumMeltMillibuckets() == 0, "empty model requires melt");

        MoldingElement first = cube(uuid(10), 0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
        MoldingElement second = cube(uuid(11), 8.0D, 0.0D, 0.0D, 24.0D, 16.0D, 16.0D);
        BakedMoldingModel overlap = MoldingModelBaker.bake(model(List.of(first, second), List.of()));
        check(overlap.analysis().volume() == 6144, "overlapping cubes were not unioned");

        MoldingElement rotated = element(
            uuid(12),
            "Rotated",
            Optional.empty(),
            new MoldingVec3(20.0D, 22.0D, 18.0D),
            new MoldingVec3(28.0D, 26.0D, 30.0D),
            new MoldingTransform(
                MoldingVec3.ZERO,
                new MoldingVec3(0.0D, 90.0D, 0.0D),
                MoldingVec3.ONE,
                new MoldingVec3(24.0D, 24.0D, 24.0D)
            )
        );
        BakedMoldingModel rotatedBake = MoldingModelBaker.bake(model(List.of(rotated), List.of()));
        check(rotatedBake.analysis().volume() == 384, "quarter-turn bake changed cube volume");

        MoldingElement zeroThicknessCube = element(
            uuid(13),
            "Barrier",
            Optional.empty(),
            new MoldingVec3(8.0D, 8.0D, 24.0D),
            new MoldingVec3(16.0D, 16.0D, 24.0D),
            MoldingTransform.IDENTITY
        );
        BakedMoldingModel zeroThicknessBake = MoldingModelBaker.bake(model(List.of(zeroThicknessCube), List.of()));
        check(zeroThicknessBake.analysis().volume() == 0, "zero-thickness cube produced physical volume");
        check(!zeroThicknessBake.barrierFaces().isEmpty(), "zero-thickness cube produced no barrier faces");
        check(zeroThicknessBake.collisionShape().isEmpty(), "zero-thickness cube produced collision boxes");
        check(zeroThicknessBake.analysis().minimumMeltMillibuckets() == 250,
            "zero-thickness cube did not use minimum melt rule");
        EditorSceneMesh zeroThicknessScene = MoldingSceneBuilder.buildEditor(
            0L,
            0L,
            model(List.of(zeroThicknessCube), List.of()),
            zeroThicknessBake,
            MoldingSelection.EMPTY,
            MoldingTool.MOVE,
            new Vector3d(24.0D, 24.0D, 24.0D),
            0.25D,
            new Vector3d(0.0D, 0.0D, -1.0D),
            false,
            false,
            false
        );
        int zeroThicknessColor = zeroThicknessScene.parts(EditorDrawPhase.ZERO_THICKNESS_SURFACE)
            .getFirst()
            .vertices()
            .getFirst()
            .color();
        MoldingElement referenceCube = cube(uuid(130), 8.0D, 8.0D, 16.0D, 16.0D, 16.0D, 24.0D);
        EditableMoldingModel referenceModel = model(List.of(referenceCube), List.of());
        EditorSceneMesh referenceScene = MoldingSceneBuilder.buildEditor(
            0L,
            0L,
            referenceModel,
            MoldingModelBaker.bake(referenceModel),
            MoldingSelection.EMPTY,
            MoldingTool.MOVE,
            new Vector3d(24.0D, 24.0D, 24.0D),
            0.25D,
            new Vector3d(0.0D, 0.0D, -1.0D),
            false,
            false,
            false
        );
        int referenceColor = surfaceFaceColor(
            referenceScene.parts(EditorDrawPhase.MANUFACTURING_SURFACE).getFirst().vertices(),
            MoldingAxis.Z,
            24.0D
        );
        check(isOpaqueNeutral(zeroThicknessColor) && zeroThicknessColor == referenceColor,
            "zero-thickness cube did not share the cube surface shading");

        BakedMoldingModel boundary = MoldingModelBaker.bake(model(List.of(
            cube(uuid(14), 32.0D, 32.0D, 32.0D, 48.0D, 48.0D, 48.0D)
        ), List.of()));
        check(boundary.analysis().volume() == 4096, "three-block boundary cube was clipped");
        expectFailure(() -> MoldingModelBaker.bake(model(List.of(element(
            uuid(15),
            "Outside",
            Optional.empty(),
            new MoldingVec3(32.0D, 32.0D, 32.0D),
            new MoldingVec3(48.0D, 48.0D, 48.0D),
            new MoldingTransform(
                new MoldingVec3(1.0D, 0.0D, 0.0D),
                MoldingVec3.ZERO,
                MoldingVec3.ONE,
                new MoldingVec3(40.0D, 40.0D, 40.0D)
            )
        )), List.of())), "out-of-bounds model was accepted");

        BakedMoldingModel cavity = MoldingModelBaker.bake(hollowBoxModel());
        check(cavity.cavities().size() == 1, "sealed hollow model did not produce one cavity");
        check(cavity.cavities().getFirst().volume() == 8, "sealed hollow model cavity volume changed");

        EditableMoldingModel fullModel = model(List.of(
            cube(uuid(16), 0.0D, 0.0D, 0.0D, 48.0D, 48.0D, 48.0D)
        ), List.of());
        long started = System.nanoTime();
        BakedMoldingModel full = MoldingModelBaker.bake(fullModel);
        long elapsed = System.nanoTime() - started;
        check(full.analysis().volume() == MoldingVolumeMask.CELL_COUNT, "maximum model did not contain 110592 cells");
        check(full.fillOrder().length == MoldingVolumeMask.CELL_COUNT, "maximum fill order lost cells");
        check(full.fillOrder()[0] == MoldingVolumeMask.index(0, 0, 0), "fill order did not start at -Y/-X/-Z");
        check(
            full.fillOrder()[full.fillOrder().length - 1] == MoldingVolumeMask.index(47, 47, 47),
            "fill order did not end at +Y/+X/+Z"
        );
        check(full.collisionShape().size() == 1, "maximum model collision was not merged");
        check(elapsed <= MAX_FULL_BAKE_NANOS, "maximum model exceeded the five-second bake budget");

        EditableMoldingModel reversed = model(List.of(second, first), List.of());
        check(
            MoldingModelHasher.hash(model(List.of(first, second), List.of()), MoldingModelBaker.BAKE_VERSION)
                .equals(MoldingModelHasher.hash(reversed, MoldingModelBaker.BAKE_VERSION)),
            "canonical hash depended on element list order"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("1x1x1")
    @TestHolder(description = "Perspective and six orthographic cameras share projection, rays, and CPU hit testing")
    static void cameraProjectionAndHitTesting(ExtendedGameTestHelper helper) {
        MoldingCamera camera = new MoldingCamera();
        for (MoldingViewPreset preset : MoldingViewPreset.values()) {
            camera.reset();
            camera.setPreset(preset);
            ViewportTransform transform = camera.transform(230, 115);
            Vector3d target = new Vector3d(24.0D, 24.0D, 24.0D);
            Vector2d projected = transform.project(target).orElseThrow(() ->
                new GameTestAssertException("camera target was behind " + preset)
            );
            check(close(projected.x, 115.0D) && close(projected.y, 57.5D), preset + " target was not centered");
            ViewportRay ray = transform.ray(projected.x, projected.y);
            check(distanceToRay(ray, target) < EPSILON, preset + " projection and ray diverged");
            check(finite(transform.ray(0.0D, 0.0D)), preset + " top-left ray was invalid");
            check(finite(transform.ray(230.0D, 115.0D)), preset + " bottom-right ray was invalid");
            Vector3d east = transform.directionInView(new Vector3d(1.0D, 0.0D, 0.0D));
            Vector3d west = transform.directionInView(new Vector3d(-1.0D, 0.0D, 0.0D));
            check(new Vector3d(east).add(west).length() < EPSILON, preset + " opposite compass axes diverged");
        }
        MoldingCamera centeredCamera = new MoldingCamera();
        centeredCamera.focus(new Vector3d(24.0D, 24.0D, 24.0D), 10.0D);
        MoldingCamera shiftedCamera = new MoldingCamera();
        shiftedCamera.focus(new Vector3d(120.0D, 80.0D, 40.0D), 10.0D);
        check(
            close(
                centeredCamera.transform(230, 115).worldUnitsPerPixel(),
                shiftedCamera.transform(230, 115).worldUnitsPerPixel()
            ),
            "perspective pixel scale depended on distance from the workspace center"
        );

        EditableMoldingModel cubeModel = model(List.of(
            cube(uuid(20), 20.0D, 20.0D, 20.0D, 28.0D, 28.0D, 28.0D)
        ), List.of());
        ViewportRay cubeRay = new ViewportRay(new Vector3d(24.0D, 24.0D, -10.0D), new Vector3d(0.0D, 0.0D, 1.0D));
        check(MoldingHitTester.hitElement(cubeModel, cubeRay).isPresent(), "cube ray hit was missed");

        EditableMoldingModel zeroThicknessModel = model(List.of(element(
            uuid(21),
            "Flat cube",
            Optional.empty(),
            new MoldingVec3(20.0D, 20.0D, 24.0D),
            new MoldingVec3(28.0D, 28.0D, 24.0D),
            MoldingTransform.IDENTITY
        )), List.of());
        check(MoldingHitTester.hitElement(zeroThicknessModel, cubeRay).isPresent(),
            "double-sided zero-thickness cube ray hit was missed");
        ViewportRay gizmoRay = new ViewportRay(new Vector3d(27.0D, 24.0D, -10.0D), new Vector3d(0.0D, 0.0D, 1.0D));
        check(
            MoldingHitTester.hitGizmo(
                gizmoRay,
                new Vector3d(24.0D, 24.0D, 24.0D),
                MoldingTool.MOVE,
                0.25D,
                new Vector3d(0.0D, 0.0D, -1.0D)
            )
                .map(MoldingHitTester.GizmoHit::axis)
                .filter(axis -> axis == MoldingAxis.X)
                .isPresent(),
            "X gizmo proxy ray hit was missed"
        );
        ViewportRay negativeScaleRay = new ViewportRay(
            new Vector3d(21.0D, 24.0D, -10.0D),
            new Vector3d(0.0D, 0.0D, 1.0D)
        );
        check(
            MoldingHitTester.hitGizmo(
                negativeScaleRay,
                new Vector3d(24.0D, 24.0D, 24.0D),
                MoldingTool.SCALE,
                0.25D,
                new Vector3d(0.0D, 0.0D, -1.0D)
            )
                .filter(hit -> hit.axis() == MoldingAxis.X && hit.direction() < 0.0D)
                .isPresent(),
            "negative X scale handle proxy ray hit was missed"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("1x1x1")
    @TestHolder(description = "Every chamber facing shares one unmirrored world-axis viewport and projection")
    static void chamberFacingWorldCoordinates(ExtendedGameTestHelper helper) {
        MoldingCamera camera = new MoldingCamera();
        ViewportTransform transform = camera.transform(230, 115);
        Vector3d worldCenter = new Vector3d(24.0D, 24.0D, 24.0D);
        Vector2d centerOnScreen = transform.project(worldCenter).orElseThrow();
        Vector2d northOnScreen = transform.project(new Vector3d(worldCenter).add(0.0D, 0.0D, -8.0D))
            .orElseThrow();
        check(
            northOnScreen.x < centerOnScreen.x && northOnScreen.y > centerOnScreen.y,
            "fixed world viewport did not keep north toward the lower left"
        );

        EditableMoldingModel cubeModel = model(List.of(
            cube(uuid(201), 20.0D, 20.0D, 20.0D, 28.0D, 28.0D, 28.0D)
        ), List.of());
        ViewportRay ray = transform.ray(centerOnScreen.x, centerOnScreen.y);
        check(distanceToRay(ray, worldCenter) < EPSILON, "world projection and inverse ray diverged");
        check(MoldingHitTester.hitElement(cubeModel, ray).isPresent(), "world-axis GUI ray missed its cube");

        Vector3d worldPoint = new Vector3d(31.0D, 19.0D, 14.0D);
        Vector3d expectedDelta = new Vector3d(worldPoint).sub(worldCenter).div(16.0D);
        for (Direction front : Direction.Plane.HORIZONTAL) {
            Vec3 projectedOrigin = PlasticMoldingChamberStructure.worldAlignedProjectionOffset(
                front,
                worldCenter.x,
                worldCenter.y,
                worldCenter.z
            );
            Vec3 projectedPoint = PlasticMoldingChamberStructure.worldAlignedProjectionOffset(
                front,
                worldPoint.x,
                worldPoint.y,
                worldPoint.z
            );
            Vec3 projectedDelta = projectedPoint.subtract(projectedOrigin);
            check(
                new Vector3d(projectedDelta.x, projectedDelta.y, projectedDelta.z).distance(expectedDelta) < EPSILON,
                "world-axis model projection rotated or mirrored for " + front
            );
            Vec3 controllerOrigin = PlasticMoldingChamberStructure.worldAlignedControllerOrigin(front);
            check(controllerOrigin.equals(new Vec3(
                    16.0D + front.getStepX() * 32.0D,
                    0.0D,
                    16.0D + front.getStepZ() * 32.0D
                )),
                "controller preview moved independently of its world facing " + front);
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("1x1x1")
    @TestHolder(description = "World-aligned GUI coordinates keep one northwest-lower origin for every chamber facing")
    static void fixedViewportCoordinateOrigin(ExtendedGameTestHelper helper) {
        check(MoldingCoordinateSystem.DISPLAY_MIN == -16.0D, "GUI coordinate minimum was not -16");
        check(MoldingCoordinateSystem.DISPLAY_MAX == 32.0D, "GUI coordinate maximum was not 32");
        MoldingVec3 expectedMaximum = new MoldingVec3(18.0D, 18.0D, 18.0D);
        for (Direction front : Direction.Plane.HORIZONTAL) {
            MoldingEditorController controller = new MoldingEditorController(
                EditableMoldingModel.empty(),
                0L,
                true,
                (revision, command) -> {
                }
            );
            check(controller.createCube(), "GUI-origin cube was not created for " + front);
            MoldingElement created = controller.model().elements().getFirst();
            check(created.from().equals(MoldingCoordinateSystem.ORIGIN),
                "new cube missed the fixed GUI origin for " + front);
            check(created.to().equals(expectedMaximum),
                "new cube missed the fixed GUI size for " + front);
            for (MoldingAxis axis : MoldingAxis.values()) {
                check(controller.numericValue(MoldingNumericProperty.POSITION, axis)
                        .filter(value -> value == 0.0D)
                        .isPresent(),
                    "new cube position did not use the GUI origin for " + front + " " + axis);
                check(controller.numericValue(MoldingNumericProperty.SIZE, axis)
                        .filter(value -> value == 2.0D)
                        .isPresent(),
                    "new cube size did not use world axes for " + front + " " + axis);
                check(controller.numericValue(MoldingNumericProperty.PIVOT, axis)
                        .filter(value -> value == 0.0D)
                        .isPresent(),
                    "new cube pivot did not use the GUI origin for " + front + " " + axis);
            }

            controller.syncAuthoritative(controller.model(), 1L, true);
            check(controller.setNumeric(MoldingNumericProperty.POSITION, MoldingAxis.X, 4.0D),
                "world X position input was rejected for " + front);
            check(controller.numericValue(MoldingNumericProperty.POSITION, MoldingAxis.X)
                    .filter(value -> value == 4.0D)
                    .isPresent(),
                "world X position input changed the wrong axis for " + front);
            check(controller.numericValue(MoldingNumericProperty.PIVOT, MoldingAxis.X)
                    .filter(value -> value == 0.0D)
                    .isPresent(),
                "world X position input moved the displayed pivot for " + front);

            controller.syncAuthoritative(controller.model(), 2L, true);
            check(controller.setNumeric(MoldingNumericProperty.PIVOT, MoldingAxis.Z, 3.0D),
                "world Z pivot input was rejected for " + front);
            check(controller.numericValue(MoldingNumericProperty.PIVOT, MoldingAxis.Z)
                    .filter(value -> value == 3.0D)
                    .isPresent(),
                "world Z pivot input changed the wrong axis for " + front);

            controller.syncAuthoritative(controller.model(), 3L, true);
            check(controller.setNumeric(MoldingNumericProperty.SIZE, MoldingAxis.Z, 5.0D),
                "world Z size input was rejected for " + front);
            check(controller.numericValue(MoldingNumericProperty.SIZE, MoldingAxis.Z)
                    .filter(value -> value == 5.0D)
                    .isPresent(),
                "world Z size input changed the wrong axis for " + front);

            for (MoldingAxis axis : MoldingAxis.values()) {
                for (double direction : List.of(-1.0D, 1.0D)) {
                    MoldingElement handleCube = cube(
                        uuid(700 + front.ordinal() * 10 + axis.ordinal() * 2 + (direction > 0.0D ? 1 : 0)),
                        16.0D,
                        16.0D,
                        16.0D,
                        18.0D,
                        18.0D,
                        18.0D
                    );
                    MoldingEditorController handleController = new MoldingEditorController(
                        model(List.of(handleCube), List.of()),
                        0L,
                        true,
                        (revision, command) -> {
                        }
                    );
                    handleController.select(handleCube.id(), false);
                    handleController.setTool(MoldingTool.SCALE);
                    check(handleController.beginDrag(axis, direction),
                        "scale handle did not start for " + front + " " + axis + " " + direction);
                    handleController.updateDrag(3.0D);
                    MoldingElement resized = handleController.model().elements().getFirst();
                    check(axisValue(resized.from(), axis) == (direction < 0.0D ? 13.0D : 16.0D),
                        "negative world-axis handle moved the opposite face for " + front + " " + axis);
                    check(axisValue(resized.to(), axis) == (direction < 0.0D ? 18.0D : 21.0D),
                        "positive world-axis handle moved the opposite face for " + front + " " + axis);
                    for (MoldingAxis other : MoldingAxis.values()) {
                        if (other == axis) continue;
                        check(axisValue(resized.from(), other) == 16.0D
                                && axisValue(resized.to(), other) == 18.0D,
                            "scale handle changed a different world axis for " + front + " " + axis);
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("1x1x1")
    @TestHolder(description = "World guides keep fixed compass axes and use distinct readable line widths")
    static void worldGuideAlignmentAndWidths(ExtendedGameTestHelper helper) {
        for (Direction front : Direction.Plane.HORIZONTAL) {
            Vec3 origin = PlasticMoldingChamberStructure.worldAlignedProjectionOffset(
                front,
                16.0D,
                16.0D,
                16.0D
            );
            Direction back = PlasticMoldingChamberStructure.back(front);
            check(origin.equals(new Vec3(back.getStepX() * 2.0D, 1.0D, back.getStepZ() * 2.0D)),
                "world guide origin missed the center block northwest-lower corner for " + front);
            Vec3 redEnd = PlasticMoldingChamberStructure.worldAlignedProjectionOffset(
                front,
                32.0D,
                16.0D,
                16.0D
            );
            Vec3 blueEnd = PlasticMoldingChamberStructure.worldAlignedProjectionOffset(
                front,
                16.0D,
                16.0D,
                32.0D
            );
            check(redEnd.subtract(origin).equals(new Vec3(1.0D, 0.0D, 0.0D)),
                "world red guide did not point east for " + front);
            check(blueEnd.subtract(origin).equals(new Vec3(0.0D, 0.0D, 1.0D)),
                "world blue guide did not point south for " + front);
        }

        double pixelScale = 0.2D;
        EditorSceneMesh guides = MoldingSceneBuilder.buildWorldGuides(
            new Vector3d(1.0D, 1.0D, 1.0D),
            pixelScale
        );
        checkSegmentWidth(
            guides.parts(EditorDrawPhase.FINE_GRID).getFirst(),
            0xFF343A3F,
            0.5D * pixelScale,
            "world fine grid did not retain its thin line width"
        );
        EditorScenePart mainGrid = guides.parts(EditorDrawPhase.MAIN_GRID).getFirst();
        checkSegmentWidth(
            mainGrid,
            0xFF69737C,
            1.0D * pixelScale,
            "world main grid did not use its thicker line width"
        );
        checkSegmentWidth(
            mainGrid,
            0xFFEC4B4B,
            1.25D * pixelScale,
            "world red guide did not use its thicker line width"
        );
        checkSegmentWidth(
            mainGrid,
            0xFF4C83E8,
            1.25D * pixelScale,
            "world blue guide did not use its thicker line width"
        );
        checkSegmentWidth(
            guides.parts(EditorDrawPhase.WORKSPACE_OUTLINE).getFirst(),
            0xFF8A9197,
            1.1D * pixelScale,
            "world workspace outline did not use its thicker line width"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("1x1x1")
    @TestHolder(description = "Editor grid is one grounded plane and every tool has a fixed-size distinct gizmo")
    static void editorGridAndToolGizmos(ExtendedGameTestHelper helper) {
        Vector3d origin = new Vector3d(24.0D, 24.0D, 24.0D);
        Vector3d cameraDirection = new Vector3d(0.0D, 0.0D, -1.0D);
        EditorSceneMesh gridScene = MoldingSceneBuilder.buildEditor(
            0L,
            0L,
            EditableMoldingModel.empty(),
            null,
            MoldingSelection.EMPTY,
            MoldingTool.MOVE,
            origin,
            0.25D,
            cameraDirection,
            false,
            true,
            true
        );
        check(
            gridScene.parts(EditorDrawPhase.FINE_GRID).getFirst().vertices().size() == 120,
            "fine grid was not limited to the fifteen inner lines on each center-cell axis"
        );
        check(
            gridScene.parts(EditorDrawPhase.FINE_GRID).getFirst().vertices().stream()
                .anyMatch(vertex -> vertex.color() == 0xFF343A3F),
            "fine grid did not retain an opaque solid-color core"
        );
        check(
            gridScene.parts(EditorDrawPhase.FINE_GRID).getFirst().vertices().stream()
                .allMatch(vertex -> vertex.x() >= 15.5F && vertex.x() <= 32.5F
                    && vertex.y() >= 15.5F && vertex.y() <= 16.5F
                    && vertex.z() >= 15.5F && vertex.z() <= 32.5F),
            "fine grid escaped the grounded center cell"
        );
        check(
            gridScene.parts(EditorDrawPhase.MAIN_GRID).getFirst().vertices().stream()
                .noneMatch(vertex -> vertex.y() > 17.0F),
            "editor grid still contained upper workspace layers"
        );
        check(
            gridScene.parts(EditorDrawPhase.MAIN_GRID).getFirst().vertices().stream()
                .noneMatch(vertex -> vertex.color() == 0xFF55C86A),
            "editor grid still contained a green world axis"
        );
        check(
            gridScene.parts(EditorDrawPhase.WORKSPACE_OUTLINE).getFirst().vertices().size() == 16,
            "axis-aligned workspace view did not reduce to four silhouette edges"
        );
        for (Vector3d direction : List.of(
            new Vector3d(1.0D, 1.0D, 1.0D),
            new Vector3d(-1.0D, 1.0D, 1.0D),
            new Vector3d(1.0D, -1.0D, 1.0D),
            new Vector3d(1.0D, 1.0D, -1.0D)
        )) {
            check(
                MoldingWorkspaceOutline.silhouetteEdges(direction).size() == 6,
                "oblique workspace view did not reduce to six silhouette edges"
            );
        }
        List<EditorVertex> redAxis = gridScene.parts(EditorDrawPhase.MAIN_GRID).getFirst().vertices().stream()
            .filter(vertex -> vertex.color() == 0xFFEC4B4B)
            .toList();
        check(
            !redAxis.isEmpty() && redAxis.stream()
                .allMatch(vertex -> vertex.x() >= 15.8F && vertex.x() <= 32.2F
                    && vertex.z() >= 15.8F && vertex.z() <= 16.2F),
            "red ground axis did not follow the fine grid X edge"
        );
        List<EditorVertex> blueAxis = gridScene.parts(EditorDrawPhase.MAIN_GRID).getFirst().vertices().stream()
            .filter(vertex -> vertex.color() == 0xFF4C83E8)
            .toList();
        check(
            !blueAxis.isEmpty() && blueAxis.stream()
                .allMatch(vertex -> vertex.x() >= 15.8F && vertex.x() <= 16.2F
                    && vertex.z() >= 15.8F && vertex.z() <= 32.2F),
            "blue ground axis did not follow the shifted center-cell edge"
        );

        MoldingElement selectedCube = cube(uuid(22), 20.0D, 20.0D, 20.0D, 28.0D, 28.0D, 28.0D);
        EditableMoldingModel selectedModel = model(List.of(selectedCube), List.of());
        MoldingSelection selection = new MoldingSelection(List.of(selectedCube.id()));
        int moveVertices = gizmoVertexCount(selectedModel, selection, MoldingTool.MOVE, origin, cameraDirection);
        int pivotVertices = gizmoVertexCount(selectedModel, selection, MoldingTool.PIVOT, origin, cameraDirection);
        int scaleVertices = gizmoVertexCount(selectedModel, selection, MoldingTool.SCALE, origin, cameraDirection);
        int rotateVertices = gizmoVertexCount(selectedModel, selection, MoldingTool.ROTATE, origin, cameraDirection);
        int mirrorVertices = gizmoVertexCount(selectedModel, selection, MoldingTool.MIRROR, origin, cameraDirection);
        check(moveVertices == pivotVertices, "pivot gizmo did not reuse the move arrows");
        check(scaleVertices != moveVertices && rotateVertices != moveVertices && mirrorVertices != moveVertices,
            "tool gizmos did not use distinct geometry");
        check(scaleVertices != rotateVertices && scaleVertices != mirrorVertices && rotateVertices != mirrorVertices,
            "scale, rotate, and mirror gizmos shared geometry");
        EditorScenePart scaleGizmo = MoldingSceneBuilder.buildEditor(
            0L,
            0L,
            selectedModel,
            null,
            selection,
            MoldingTool.SCALE,
            origin,
            0.25D,
            cameraDirection,
            false,
            false,
            false
        ).parts(EditorDrawPhase.GIZMO).getFirst();
        checkColoredAxis(scaleGizmo, 0xFFEC4B4B, MoldingAxis.X, "red scale gizmo did not follow world W/E");
        checkColoredAxis(scaleGizmo, 0xFF55C86A, MoldingAxis.Y, "green scale gizmo did not follow world U/D");
        checkColoredAxis(scaleGizmo, 0xFF4C83E8, MoldingAxis.Z, "blue scale gizmo did not follow world N/S");

        MoldingCamera camera = new MoldingCamera();
        ViewportTransform defaultTransform = camera.transform(230, 115);
        Vector2d defaultCenter = defaultTransform.project(origin).orElseThrow();
        Vector2d defaultNorth = defaultTransform.project(new Vector3d(origin).add(0.0D, 0.0D, -8.0D))
            .orElseThrow();
        check(
            defaultNorth.x < defaultCenter.x && defaultNorth.y > defaultCenter.y,
            "default camera did not place north toward the lower left"
        );
        camera.setPreset(MoldingViewPreset.TOP);
        ViewportTransform firstTransform = camera.transform(230, 115);
        double firstLength = projectedAxisLength(firstTransform, origin);
        camera.zoom(4.0D);
        ViewportTransform secondTransform = camera.transform(230, 115);
        double secondLength = projectedAxisLength(secondTransform, origin);
        check(Math.abs(firstLength - 28.0D) < 0.01D, "gizmo did not use the requested screen-space size");
        check(Math.abs(firstLength - secondLength) < 0.01D, "gizmo changed screen size after camera zoom");

        MoldingDragTransaction negativeScale = new MoldingDragTransaction(
            selectedModel,
            selection,
            MoldingTool.SCALE,
            MoldingAxis.X,
            -1.0D
        );
        MoldingElement scaled = negativeScale.preview(2.0D).elements().getFirst();
        check(scaled.from().x() == 18.0D && scaled.to().x() == 28.0D,
            "negative scale handle did not resize from the negative face");

        MoldingTransform translatedTransform = new MoldingTransform(
            new MoldingVec3(2.0D, 3.0D, 4.0D),
            MoldingVec3.ZERO,
            MoldingVec3.ONE,
            selectedCube.transform().pivot()
        );
        MoldingElement translatedCube = element(
            selectedCube.id(),
            selectedCube.name(),
            selectedCube.groupId(),
            selectedCube.from(),
            selectedCube.to(),
            translatedTransform
        );
        MoldingEditorController controller = new MoldingEditorController(
            model(List.of(translatedCube), List.of()),
            0L,
            true,
            (revision, command) -> {
            }
        );
        controller.select(translatedCube.id(), false);
        controller.setTool(MoldingTool.ROTATE);
        check(controller.gizmoOrigin().distance(new Vector3d(26.0D, 27.0D, 28.0D)) < EPSILON,
            "rotation gizmo was not centered on the transformed pivot");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("1x1x1")
    @TestHolder(description = "Cube scaling crosses zero thickness without creating a separate plane type")
    static void zeroThicknessCubeScaling(ExtendedGameTestHelper helper) {
        MoldingElement cube = cube(uuid(220), 20.0D, 20.0D, 20.0D, 28.0D, 28.0D, 28.0D);
        EditableMoldingModel cubeModel = model(List.of(cube), List.of());
        MoldingSelection selection = new MoldingSelection(List.of(cube.id()));
        MoldingDragTransaction flatten = new MoldingDragTransaction(
            cubeModel,
            selection,
            MoldingTool.SCALE,
            MoldingAxis.X
        );
        MoldingElement flattened = flatten.preview(-8.0D).elements().getFirst();
        check(!flattened.hasVolume() && flattened.from().x() == flattened.to().x(),
            "scale tool did not flatten a cube at zero pixels");
        BakedMoldingModel flattenedBake = MoldingModelBaker.bake(model(List.of(flattened), List.of()));
        check(flattenedBake.analysis().volume() == 0 && !flattenedBake.barrierFaces().isEmpty(),
            "flattened cube did not use zero-thickness geometry");

        EditableMoldingModel flattenedModel = model(List.of(flattened), List.of());
        MoldingDragTransaction thicken = new MoldingDragTransaction(
            flattenedModel,
            selection,
            MoldingTool.SCALE,
            MoldingAxis.X
        );
        MoldingElement thickened = thicken.preview(3.0D).elements().getFirst();
        check(thickened.hasVolume() && thickened.to().x() - thickened.from().x() == 3.0D,
            "scale tool did not thicken a zero-thickness cube");
        check(MoldingModelBaker.bake(model(List.of(thickened), List.of())).analysis().volume() == 192,
            "thickened cube did not regain physical volume");

        MoldingDragTransaction rejectSecondFlatAxis = new MoldingDragTransaction(
            flattenedModel,
            selection,
            MoldingTool.SCALE,
            MoldingAxis.Y
        );
        MoldingElement clamped = rejectSecondFlatAxis.preview(-8.0D).elements().getFirst();
        check(clamped.from().x() == clamped.to().x() && clamped.to().y() - clamped.from().y() == 1.0D,
            "scale tool allowed a zero-thickness cube to collapse into a line");

        MoldingEditorController numericThickenController = new MoldingEditorController(
            flattenedModel,
            0L,
            true,
            (revision, command) -> {
            }
        );
        numericThickenController.select(flattened.id(), false);
        check(numericThickenController.setNumeric(MoldingNumericProperty.SIZE, MoldingAxis.X, 3.0D),
            "numeric size input did not thicken a zero-thickness cube");
        MoldingElement numericallyThickened = numericThickenController.model().elements().getFirst();
        check(numericallyThickened.hasVolume()
                && numericallyThickened.to().x() - numericallyThickened.from().x() == 3.0D,
            "numeric size input produced the wrong cube thickness");

        MoldingEditorController rejectSecondZeroController = new MoldingEditorController(
            flattenedModel,
            0L,
            true,
            (revision, command) -> {
            }
        );
        rejectSecondZeroController.select(flattened.id(), false);
        check(!rejectSecondZeroController.setNumeric(MoldingNumericProperty.SIZE, MoldingAxis.Y, 0.0D),
            "numeric size input allowed a zero-thickness cube to collapse into a line");
        check(rejectSecondZeroController.model().equals(flattenedModel),
            "rejected numeric size input changed the model");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("1x1x1")
    @TestHolder(description = "Editor and world projections keep exact rotated source faces and expose hover overlays")
    static void exactSourceProjectionAndHoverFeedback(ExtendedGameTestHelper helper) {
        MoldingElement base = cube(uuid(23), 20.0D, 20.0D, 20.0D, 28.0D, 28.0D, 28.0D);
        MoldingElement rotated = element(
            base.id(),
            base.name(),
            base.groupId(),
            base.from(),
            base.to(),
            new MoldingTransform(
                MoldingVec3.ZERO,
                new MoldingVec3(0.0D, 30.0D, 0.0D),
                MoldingVec3.ONE,
                base.transform().pivot()
            )
        );
        EditableMoldingModel model = model(List.of(rotated), List.of());
        MoldingSelection selection = new MoldingSelection(List.of(rotated.id()));
        EditorSceneMesh editorScene = MoldingSceneBuilder.buildEditor(
            0L,
            0L,
            model,
            MoldingModelBaker.bake(model),
            selection,
            MoldingTool.ROTATE,
            new Vector3d(24.0D, 24.0D, 24.0D),
            0.25D,
            new Vector3d(0.0D, 0.0D, -1.0D),
            false,
            true,
            true,
            rotated.id(),
            MoldingAxis.Z
        );
        check(
            editorScene.parts(EditorDrawPhase.MANUFACTURING_SURFACE).getFirst().vertices().size() == 24,
            "editor projection used voxelized faces instead of six exact source faces"
        );
        check(
            editorScene.parts(EditorDrawPhase.MANUFACTURING_SURFACE).getFirst().vertices().stream()
                .anyMatch(vertex -> Math.abs(vertex.x() - Math.rint(vertex.x())) > 0.01D
                    || Math.abs(vertex.z() - Math.rint(vertex.z())) > 0.01D),
            "rotated source projection lost its continuous sloped coordinates"
        );
        check(
            editorScene.parts(EditorDrawPhase.MANUFACTURING_SURFACE).getFirst().vertices().stream()
                .allMatch(vertex -> isOpaqueNeutral(vertex.color())),
            "source cube surface shading was not opaque and neutral"
        );
        check(
            editorScene.parts(EditorDrawPhase.MANUFACTURING_SURFACE).getFirst().vertices().stream()
                .map(EditorVertex::color)
                .distinct()
                .count() >= 3,
            "source cube faces were still rendered at one full-bright value"
        );
        check(
            sourceFacesPointOutward(editorScene.parts(EditorDrawPhase.MANUFACTURING_SURFACE).getFirst().vertices()),
            "opaque source cube faces were wound inward"
        );
        check(
            EditorDrawPhase.MANUFACTURING_SURFACE.ordinal() < EditorDrawPhase.SOURCE_OUTLINE.ordinal()
                && EditorDrawPhase.SOURCE_OUTLINE.ordinal() < EditorDrawPhase.HOVER_SURFACE.ordinal(),
            "source faces or hover feedback would cover the element outline"
        );
        check(
            editorScene.parts(EditorDrawPhase.HOVER_SURFACE).getFirst().vertices().size() == 72,
            "hovered cube did not receive a translucent surface and edge overlay"
        );
        check(
            editorScene.parts(EditorDrawPhase.GIZMO).getFirst().vertices().stream()
                .anyMatch(vertex -> vertex.color() == 0x78FFFFFF),
            "hovered gizmo axis did not receive a white overlay"
        );
        check(
            editorScene.parts(EditorDrawPhase.GIZMO).getFirst().vertices().stream()
                .anyMatch(vertex -> vertex.color() == 0xFFFFFFFF),
            "rotation gizmo did not include the camera-facing outer ring"
        );

        EditorSceneMesh worldScene = MoldingSceneBuilder.buildWorldProjection(0L, model);
        check(
            worldScene.parts(EditorDrawPhase.MANUFACTURING_SURFACE).getFirst().vertices().size() == 24,
            "world projection used voxelized geometry"
        );
        check(
            worldScene.parts(EditorDrawPhase.MAIN_GRID).getFirst().vertices().stream()
                .allMatch(vertex -> vertex.y() >= 15.8F && vertex.y() <= 16.2F),
            "world projection still contained three-dimensional grid layers"
        );
        check(
            worldScene.parts(EditorDrawPhase.WORKSPACE_OUTLINE).getFirst().vertices().size() == 24,
            "world projection did not include the six-edge workspace silhouette"
        );
        for (MoldingAxis axis : MoldingAxis.values()) {
            MoldingVec3 scale = switch (axis) {
                case X -> new MoldingVec3(-1.0D, 1.0D, 1.0D);
                case Y -> new MoldingVec3(1.0D, -1.0D, 1.0D);
                case Z -> new MoldingVec3(1.0D, 1.0D, -1.0D);
            };
            MoldingElement mirrored = element(
                base.id(),
                base.name(),
                base.groupId(),
                base.from(),
                base.to(),
                new MoldingTransform(MoldingVec3.ZERO, MoldingVec3.ZERO, scale, base.transform().pivot())
            );
            List<EditorVertex> mirroredVertices = MoldingSceneBuilder.buildWorldSurfaces(
                0L,
                model(List.of(mirrored), List.of())
            ).parts(EditorDrawPhase.MANUFACTURING_SURFACE).getFirst().vertices();
            check(sourceFacesPointOutward(mirroredVertices), axis + " mirror inverted source surface winding");
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("1x1x1")
    @TestHolder(description = "Group selection highlights descendants and one continuous drag submits one command")
    static void groupedSelectionAndDragMerging(ExtendedGameTestHelper helper) {
        UUID groupId = uuid(30);
        MoldingGroup group = new MoldingGroup(
            groupId,
            "Group",
            Optional.empty(),
            MoldingTransform.IDENTITY,
            true,
            false
        );
        MoldingElement first = cube(uuid(31), 8.0D, 8.0D, 8.0D, 12.0D, 12.0D, 12.0D).withGroup(Optional.of(groupId));
        MoldingElement second = cube(uuid(32), 16.0D, 8.0D, 8.0D, 20.0D, 12.0D, 12.0D).withGroup(Optional.of(groupId));
        EditableMoldingModel model = model(List.of(first, second), List.of(group));
        MoldingSelection selection = new MoldingSelection(List.of(groupId));

        EditorSceneMesh scene = MoldingSceneBuilder.buildEditor(
            0L,
            0L,
            model,
            MoldingModelBaker.bake(model),
            selection,
            MoldingTool.MOVE,
            new Vector3d(14.0D, 10.0D, 10.0D),
            1.0D,
            new Vector3d(0.0D, 0.0D, -1.0D),
            false,
            true,
            true
        );
        check(
            scene.parts(EditorDrawPhase.SELECTION).getFirst().vertices().size() == 96,
            "group selection did not highlight both descendant cubes"
        );

        List<MoldingCommand> submitted = new ArrayList<>();
        MoldingEditorController controller = new MoldingEditorController(
            model,
            0L,
            true,
            (revision, command) -> submitted.add(command)
        );
        controller.select(groupId, false);
        check(controller.beginDrag(MoldingAxis.X), "group drag did not start");
        controller.updateDrag(1.2D);
        controller.updateDrag(3.1D);
        controller.updateDrag(4.0D);
        check(controller.finishDrag(), "group drag did not submit");
        check(submitted.size() == 1, "continuous group drag submitted more than one command");
        check(submitted.getFirst() instanceof MoldingCommand.Batch, "multi-element drag was not one semantic batch");
        EditableMoldingModel moved = submitted.getFirst().apply(model);
        check(moved.elements().stream().allMatch(element -> element.transform().translation().x() == 4.0D), "group drag missed a child");

        MoldingDragTransaction direct = new MoldingDragTransaction(model, selection, MoldingTool.MOVE, MoldingAxis.Y);
        direct.preview(2.0D);
        check(direct.command() instanceof MoldingCommand.Batch, "direct drag transaction did not merge replacements");

        UUID parentId = uuid(33);
        UUID childId = uuid(34);
        MoldingGroup parent = new MoldingGroup(
            parentId,
            "Parent",
            Optional.empty(),
            MoldingTransform.IDENTITY,
            true,
            false
        );
        MoldingGroup child = new MoldingGroup(
            childId,
            "Child",
            Optional.of(parentId),
            MoldingTransform.IDENTITY,
            true,
            false
        );
        MoldingElement nestedElement = cube(
            uuid(35),
            20.0D,
            20.0D,
            20.0D,
            24.0D,
            24.0D,
            24.0D
        ).withGroup(Optional.of(childId));
        EditableMoldingModel childFirstModel = model(List.of(nestedElement), List.of(child, parent));
        List<MoldingCommand> pasteCommands = new ArrayList<>();
        MoldingEditorController clipboardController = new MoldingEditorController(
            childFirstModel,
            0L,
            true,
            (revision, command) -> pasteCommands.add(command)
        );
        clipboardController.select(parentId, false);
        clipboardController.copySelection();
        check(clipboardController.paste(), "child-first nested groups could not be pasted");
        check(pasteCommands.getFirst() instanceof MoldingCommand.Batch, "nested paste was not one semantic batch");
        MoldingCommand.Batch paste = (MoldingCommand.Batch) pasteCommands.getFirst();
        check(paste.commands().getFirst() instanceof MoldingCommand.AddGroup, "nested paste did not add groups first");
        MoldingCommand.AddGroup firstAddedGroup = (MoldingCommand.AddGroup) paste.commands().getFirst();
        check(firstAddedGroup.group().parentId().isEmpty(), "nested paste added a child before its parent");
        check(clipboardController.model().groups().size() == 4, "nested paste did not preserve both groups");
        check(clipboardController.model().elements().size() == 2, "nested paste did not preserve its descendant element");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("1x1x1")
    @TestHolder(description = "Pasting a cube preserves its original coordinates and transform")
    static void pasteCubeInPlace(ExtendedGameTestHelper helper) {
        MoldingTransform transform = new MoldingTransform(
            new MoldingVec3(0.0D, 0.0D, 0.0D),
            new MoldingVec3(0.0D, 0.0D, 0.0D),
            MoldingVec3.ONE,
            new MoldingVec3(46.0D, 46.0D, 46.0D)
        );
        MoldingElement source = element(
            uuid(36),
            "Boundary cube",
            Optional.empty(),
            new MoldingVec3(44.0D, 44.0D, 44.0D),
            new MoldingVec3(48.0D, 48.0D, 48.0D),
            transform
        );
        List<MoldingCommand> submitted = new ArrayList<>();
        MoldingEditorController controller = new MoldingEditorController(
            model(List.of(source), List.of()),
            0L,
            true,
            (revision, command) -> submitted.add(command)
        );

        controller.select(source.id(), false);
        controller.copySelection();
        check(controller.paste(), "cube touching the positive workspace boundaries could not be pasted");
        check(submitted.size() == 1, "in-place paste submitted more than one command");
        MoldingElement pasted = controller.model().elements().getLast();
        check(!pasted.id().equals(source.id()), "pasted cube reused the source id");
        check(pasted.from().equals(source.from()), "pasted cube changed its from coordinates");
        check(pasted.to().equals(source.to()), "pasted cube changed its to coordinates");
        check(pasted.transform().equals(source.transform()), "pasted cube changed its transform");
        check(controller.selection().contains(pasted.id()), "pasted cube was not selected");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("1x1x1")
    @TestHolder(description = "Delete all cubes submits one atomic command and preserves everything when a cube is locked")
    static void deleteAllCubesAtomically(ExtendedGameTestHelper helper) {
        MoldingElement first = cube(uuid(223), 16.0D, 16.0D, 16.0D, 20.0D, 20.0D, 20.0D);
        MoldingElement second = cube(uuid(224), 24.0D, 24.0D, 24.0D, 28.0D, 28.0D, 28.0D);
        EditableMoldingModel source = model(List.of(first, second), List.of());
        List<MoldingCommand> submitted = new ArrayList<>();
        MoldingEditorController controller = new MoldingEditorController(
            source,
            0L,
            true,
            (revision, command) -> submitted.add(command)
        );
        controller.select(first.id(), false);

        check(controller.deleteAllElements(), "delete-all command was rejected");
        check(submitted.size() == 1 && submitted.getFirst() instanceof MoldingCommand.RemoveElements,
            "delete-all did not submit one semantic removal command");
        check(controller.model().elements().isEmpty(), "delete-all left cubes in the model");
        check(controller.selection().isEmpty(), "delete-all retained a stale selection");

        MoldingElement locked = new MoldingElement(
            second.id(),
            second.name(),
            second.groupId(),
            second.from(),
            second.to(),
            second.transform(),
            second.visible(),
            true
        );
        List<MoldingCommand> rejected = new ArrayList<>();
        MoldingEditorController lockedController = new MoldingEditorController(
            model(List.of(first, locked), List.of()),
            0L,
            true,
            (revision, command) -> rejected.add(command)
        );
        check(!lockedController.deleteAllElements(), "delete-all removed a locked cube");
        check(lockedController.model().elements().size() == 2 && rejected.isEmpty(),
            "failed delete-all partially changed the model");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("1x1x1")
    @TestHolder(description = "New cubes use the shared origin and moving an element carries its pivot")
    static void axisCornerCubeCreation(ExtendedGameTestHelper helper) {
        List<MoldingCommand> submitted = new ArrayList<>();
        MoldingEditorController controller = new MoldingEditorController(
            EditableMoldingModel.empty(),
            0L,
            true,
            (revision, command) -> submitted.add(command)
        );

        check(controller.createCube(), "axis-corner cube was not created");
        check(submitted.size() == 1, "cube creation submitted more than one command");
        check(submitted.getFirst() instanceof MoldingCommand.AddElement, "cube creation submitted the wrong command");
        MoldingElement created = controller.model().elements().getFirst();
        check(created.from().equals(new MoldingVec3(16.0D, 16.0D, 16.0D)), "cube missed the shared origin");
        check(created.to().equals(new MoldingVec3(18.0D, 18.0D, 18.0D)), "cube was not two pixels wide");
        for (MoldingAxis axis : MoldingAxis.values()) {
            check(controller.numericValue(MoldingNumericProperty.POSITION, axis).orElseThrow() == 0.0D,
                "new cube position did not start at zero");
            check(controller.numericValue(MoldingNumericProperty.PIVOT, axis).orElseThrow() == 0.0D,
                "new cube pivot did not start at zero");
        }
        check(controller.selection().contains(created.id()), "new cube was not selected");

        controller.syncAuthoritative(controller.model(), 1L, true);
        controller.setTool(MoldingTool.MOVE);
        check(controller.beginDrag(MoldingAxis.X), "move drag did not start");
        controller.updateDrag(3.0D);
        check(controller.numericValue(MoldingNumericProperty.POSITION, MoldingAxis.X).orElseThrow() == 3.0D,
            "move drag did not update position");
        check(controller.numericValue(MoldingNumericProperty.PIVOT, MoldingAxis.X).orElseThrow() == 3.0D,
            "move drag did not carry the pivot");
        controller.cancelDrag();

        controller.setTool(MoldingTool.PIVOT);
        check(controller.beginDrag(MoldingAxis.Z), "pivot drag did not start");
        controller.updateDrag(2.0D);
        check(controller.numericValue(MoldingNumericProperty.POSITION, MoldingAxis.Z).orElseThrow() == 0.0D,
            "pivot drag changed position");
        check(controller.numericValue(MoldingNumericProperty.PIVOT, MoldingAxis.Z).orElseThrow() == 2.0D,
            "pivot drag did not update the pivot");
        controller.cancelDrag();

        check(controller.setNumeric(MoldingNumericProperty.POSITION, MoldingAxis.Y, 4.0D),
            "numeric position input was rejected");
        check(controller.numericValue(MoldingNumericProperty.POSITION, MoldingAxis.Y).orElseThrow() == 4.0D,
            "numeric position input did not update position");
        check(controller.numericValue(MoldingNumericProperty.PIVOT, MoldingAxis.Y).orElseThrow() == 0.0D,
            "numeric position input unexpectedly moved the pivot");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("1x1x1")
    @TestHolder(description = "Equivalent cube geometry reports the same position after scale and move interleaving")
    static void equivalentGeometryPosition(ExtendedGameTestHelper helper) {
        MoldingVec3 pivot = new MoldingVec3(16.0D, 16.0D, 16.0D);
        MoldingElement scaleThenMove = element(
            uuid(91),
            "Scale then move",
            Optional.empty(),
            new MoldingVec3(2.0D, 16.0D, 0.0D),
            new MoldingVec3(18.0D, 18.0D, 16.0D),
            new MoldingTransform(
                new MoldingVec3(30.0D, 0.0D, 0.0D),
                MoldingVec3.ZERO,
                MoldingVec3.ONE,
                pivot
            )
        );
        MoldingElement interleaved = element(
            uuid(92),
            "Interleaved",
            Optional.empty(),
            new MoldingVec3(16.0D, 16.0D, 16.0D),
            new MoldingVec3(32.0D, 18.0D, 32.0D),
            new MoldingTransform(
                new MoldingVec3(16.0D, 0.0D, -16.0D),
                MoldingVec3.ZERO,
                MoldingVec3.ONE,
                pivot
            )
        );
        EditableMoldingModel model = model(List.of(scaleThenMove, interleaved), List.of());
        check(
            MoldingModelBaker.transformedVertices(model, scaleThenMove)
                .equals(MoldingModelBaker.transformedVertices(model, interleaved)),
            "scale and move edit orders did not produce equivalent geometry"
        );

        MoldingEditorController controller = new MoldingEditorController(
            model,
            0L,
            true,
            (revision, command) -> {
            }
        );
        controller.select(scaleThenMove.id(), false);
        controller.select(interleaved.id(), true);
        double[] expected = {16.0D, 0.0D, -16.0D};
        for (MoldingAxis axis : MoldingAxis.values()) {
            check(
                controller.numericValue(MoldingNumericProperty.POSITION, axis)
                    .filter(value -> value == expected[axis.ordinal()])
                    .isPresent(),
                "equivalent cube geometry reported different " + axis + " positions"
            );
        }

        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("1x1x1")
    @TestHolder(description = "Cube position uses its signed from anchor for every edit path")
    static void signedFromAnchorPosition(ExtendedGameTestHelper helper) {
        MoldingElement negativeSize = element(
            uuid(93),
            "Negative size",
            Optional.empty(),
            new MoldingVec3(30.0D, 26.0D, 30.0D),
            new MoldingVec3(20.0D, 18.0D, 12.0D),
            new MoldingTransform(
                new MoldingVec3(2.0D, 1.0D, 4.0D),
                new MoldingVec3(15.0D, 30.0D, 45.0D),
                MoldingVec3.ONE,
                new MoldingVec3(25.0D, 22.0D, 21.0D)
            )
        );
        MoldingEditorController negativeController = new MoldingEditorController(
            model(List.of(negativeSize), List.of()),
            0L,
            true,
            (revision, command) -> {
            }
        );
        negativeController.select(negativeSize.id(), false);
        double[] expected = {16.0D, 11.0D, 18.0D};
        for (MoldingAxis axis : MoldingAxis.values()) {
            check(
                negativeController.numericValue(MoldingNumericProperty.POSITION, axis)
                    .filter(value -> value == expected[axis.ordinal()])
                    .isPresent(),
                "negative-size cube did not use its signed from anchor on " + axis
            );
        }

        double pivotX = negativeController.numericValue(MoldingNumericProperty.PIVOT, MoldingAxis.X).orElseThrow();
        check(negativeController.setNumeric(MoldingNumericProperty.POSITION, MoldingAxis.X, 7.0D),
            "signed-anchor position input was rejected");
        check(negativeController.numericValue(MoldingNumericProperty.POSITION, MoldingAxis.X).orElseThrow() == 7.0D,
            "position input did not move the from anchor to the requested value");
        check(negativeController.numericValue(MoldingNumericProperty.PIVOT, MoldingAxis.X).orElseThrow() == pivotX,
            "signed-anchor position input unexpectedly moved the pivot");

        MoldingElement positiveSize = element(
            uuid(94),
            "Positive size",
            Optional.empty(),
            new MoldingVec3(18.0D, 18.0D, 18.0D),
            new MoldingVec3(24.0D, 24.0D, 24.0D),
            MoldingTransform.IDENTITY
        );
        MoldingEditorController alignController = new MoldingEditorController(
            model(List.of(negativeSize, positiveSize), List.of()),
            0L,
            true,
            (revision, command) -> {
            }
        );
        alignController.select(positiveSize.id(), false);
        alignController.select(negativeSize.id(), true);
        check(alignController.align(MoldingAxis.Z), "signed-anchor alignment was rejected");
        check(alignController.numericValue(MoldingNumericProperty.POSITION, MoldingAxis.Z).orElseThrow() == 18.0D,
            "alignment did not use both cubes' signed from anchors");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("1x1x1")
    @TestHolder(description = "Tool button frames and release actions follow the supplied atlases")
    static void toolButtonStateMachine(ExtendedGameTestHelper helper) {
        MoldingToolButtonState buttons = new MoldingToolButtonState(MoldingTool.MOVE);
        check(buttons.frame(MoldingTool.MOVE, false) == 3, "selected move tool used the wrong frame");
        check(buttons.frame(MoldingTool.MOVE, true) == 4, "hovered selected move tool used the wrong frame");
        check(buttons.press(MoldingTool.MOVE), "move tool did not enter its pressed state");
        check(buttons.frame(MoldingTool.MOVE, true) == 2, "pressed move tool used the wrong frame");
        check(buttons.release(MoldingTool.MOVE, true) == MoldingToolButtonState.ReleaseAction.TOOL_CHANGED
                && buttons.selectedTool() == MoldingTool.NONE,
            "selected move tool could not be toggled off");
        check(
            MoldingSceneBuilder.buildEditor(
                0L,
                0L,
                model(List.of(cube(uuid(222), 16.0D, 16.0D, 16.0D, 18.0D, 18.0D, 18.0D)), List.of()),
                null,
                new MoldingSelection(List.of(uuid(222))),
                MoldingTool.NONE,
                new Vector3d(16.0D, 16.0D, 16.0D),
                0.25D,
                new Vector3d(0.0D, 0.0D, -1.0D),
                false,
                false,
                false
            ).parts(EditorDrawPhase.GIZMO).isEmpty(),
            "no-tool state retained an empty gizmo upload"
        );

        buttons.select(MoldingTool.PIVOT);
        check(buttons.press(MoldingTool.PIVOT), "selected pivot tool did not enter its pressed state");
        check(buttons.frame(MoldingTool.PIVOT, true) == 5, "selected pivot press used the wrong frame");
        check(buttons.release(MoldingTool.PIVOT, true) == MoldingToolButtonState.ReleaseAction.CENTER_PIVOT
                && buttons.selectedTool() == MoldingTool.PIVOT,
            "selected pivot tool did not remain selected while centering");

        buttons.select(MoldingTool.NONE);
        for (MoldingAxis axis : MoldingAxis.values()) {
            check(buttons.press(MoldingTool.MIRROR), "mirror button did not enter its pressed state");
            check(buttons.release(MoldingTool.MIRROR, true) == MoldingToolButtonState.ReleaseAction.TOOL_CHANGED,
                "mirror button did not advance on release");
            check(buttons.selectedTool() == MoldingTool.MIRROR && buttons.mirrorAxis().orElseThrow() == axis,
                "mirror button advanced to the wrong axis");
        }
        check(buttons.press(MoldingTool.MIRROR), "Z mirror button did not enter its pressed state");
        check(buttons.release(MoldingTool.MIRROR, true) == MoldingToolButtonState.ReleaseAction.TOOL_CHANGED
                && buttons.selectedTool() == MoldingTool.NONE
                && buttons.mirrorAxis().isEmpty(),
            "mirror button did not cycle back to standard");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("1x1x1")
    @TestHolder(description = "Off-screen target lifecycle reuses, resizes, reloads, and closes resources idempotently")
    static void viewportTargetLifecycle(ExtendedGameTestHelper helper) {
        TargetCounters counters = new TargetCounters();
        MoldingViewportTargetLifecycle<FakeTarget> lifecycle = new MoldingViewportTargetLifecycle<>(
            new MoldingViewportTargetLifecycle.Adapter<>() {
                @Override
                public FakeTarget create(int width, int height) {
                    counters.created++;
                    return new FakeTarget(width, height);
                }

                @Override
                public void resize(FakeTarget target, int width, int height) {
                    counters.resized++;
                    target.width = width;
                    target.height = height;
                }

                @Override
                public void release(FakeTarget target) {
                    counters.released++;
                }
            }
        );
        lifecycle.ensure(230, 115);
        lifecycle.ensure(230, 115);
        check(counters.created == 1 && counters.resized == 0, "unchanged viewport reallocated its target");
        lifecycle.ensure(460, 230);
        check(counters.resized == 1, "viewport resize did not resize the target");
        check(lifecycle.target().width == 460 && lifecycle.target().height == 230, "target size was not updated");
        lifecycle.reload();
        check(counters.released == 1 && lifecycle.target() == null, "resource reload did not release the target");
        lifecycle.ensure(120, 60);
        check(counters.created == 2, "target was not rebuilt after resource reload");
        lifecycle.close();
        lifecycle.close();
        check(counters.released == 2, "idempotent close released the target more than once");
        expectFailure(() -> lifecycle.ensure(1, 1), "closed target lifecycle accepted a new allocation");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("1x1x1")
    @TestHolder(description = "Small-screen fitting uses one reversible transform for input and framebuffer sizing")
    static void guiScalingTransform(ExtendedGameTestHelper helper) {
        MoldingGuiTransform compact = MoldingGuiTransform.fit(320, 180, 493, 226, 4);
        check(compact.scale() < 1.0D, "small screen did not scale the expanded GUI");
        double logicalX = 177.25D;
        double logicalY = 91.75D;
        check(
            close(compact.toLogicalX(compact.toScreenX(logicalX)), logicalX)
                && close(compact.toLogicalY(compact.toScreenY(logicalY)), logicalY),
            "GUI input inverse did not match the drawing transform"
        );
        check(
            compact.framebufferPixels(230, 2.5D) == (int) Math.ceil(230.0D * compact.scale() * 2.5D),
            "viewport framebuffer size ignored GUI or DPI scale"
        );
        MoldingGuiTransform fullSize = MoldingGuiTransform.fit(1920, 1080, 349, 226, 4);
        check(close(fullSize.scale(), 1.0D), "large screen scaled the GUI above its native size");
        helper.succeed();
    }

    private static int gizmoVertexCount(
        EditableMoldingModel model,
        MoldingSelection selection,
        MoldingTool tool,
        Vector3d origin,
        Vector3d cameraDirection
    ) {
        EditorSceneMesh scene = MoldingSceneBuilder.buildEditor(
            0L,
            0L,
            model,
            null,
            selection,
            tool,
            origin,
            0.25D,
            cameraDirection,
            false,
            false,
            false
        );
        return scene.parts(EditorDrawPhase.GIZMO).getFirst().vertices().size();
    }

    private static boolean sourceFacesPointOutward(List<EditorVertex> vertices) {
        return sourceFacesHaveDirection(vertices, true);
    }

    private static boolean sourceFacesPointInward(List<EditorVertex> vertices) {
        return sourceFacesHaveDirection(vertices, false);
    }

    private static List<EditorVertex> transformedPartVertices(
        EditorScenePart part,
        UnaryOperator<Vector3d> transform
    ) {
        int[] indices = part.indices();
        List<EditorVertex> result = new ArrayList<>(indices.length);
        for (int index : indices) {
            EditorVertex source = part.vertices().get(index);
            Vector3d transformed = transform.apply(vector(source));
            result.add(new EditorVertex(
                (float) transformed.x,
                (float) transformed.y,
                (float) transformed.z,
                source.color()
            ));
        }
        return result;
    }

    private static double axisValue(MoldingVec3 value, MoldingAxis axis) {
        return switch (axis) {
            case X -> value.x();
            case Y -> value.y();
            case Z -> value.z();
        };
    }

    private static void checkColoredAxis(
        EditorScenePart part,
        int color,
        MoldingAxis expectedAxis,
        String message
    ) {
        List<EditorVertex> vertices = part.vertices().stream()
            .filter(vertex -> vertex.color() == color)
            .toList();
        check(!vertices.isEmpty(), message + ": missing color");
        double expectedSpan = axisSpan(vertices, expectedAxis);
        for (MoldingAxis axis : MoldingAxis.values()) {
            if (axis == expectedAxis) continue;
            check(expectedSpan > axisSpan(vertices, axis) * 4.0D, message);
        }
    }

    private static double axisSpan(List<EditorVertex> vertices, MoldingAxis axis) {
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (EditorVertex vertex : vertices) {
            double value = switch (axis) {
                case X -> vertex.x();
                case Y -> vertex.y();
                case Z -> vertex.z();
            };
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        return maximum - minimum;
    }

    private static boolean sourceFacesHaveDirection(List<EditorVertex> vertices, boolean outward) {
        Vector3d center = new Vector3d();
        for (EditorVertex vertex : vertices) center.add(vertex.x(), vertex.y(), vertex.z());
        center.div(vertices.size());
        for (int index = 0; index < vertices.size(); index += 4) {
            Vector3d first = vector(vertices.get(index));
            Vector3d second = vector(vertices.get(index + 1));
            Vector3d third = vector(vertices.get(index + 2));
            Vector3d faceCenter = new Vector3d(first)
                .add(second)
                .add(third)
                .add(vector(vertices.get(index + 3)))
                .mul(0.25D);
            Vector3d normal = new Vector3d(second).sub(first).cross(new Vector3d(third).sub(first));
            double direction = normal.dot(new Vector3d(faceCenter).sub(center));
            if (outward ? direction <= 0.0D : direction >= 0.0D) return false;
        }
        return true;
    }

    private static int surfaceFaceColor(List<EditorVertex> vertices, MoldingAxis axis, double coordinate) {
        for (int start = 0; start < vertices.size(); start += 4) {
            boolean onPlane = true;
            for (int offset = 0; offset < 4; offset++) {
                EditorVertex vertex = vertices.get(start + offset);
                double value = switch (axis) {
                    case X -> vertex.x();
                    case Y -> vertex.y();
                    case Z -> vertex.z();
                };
                onPlane &= close(value, coordinate);
            }
            if (onPlane) return vertices.get(start).color();
        }
        throw new GameTestAssertException("Expected source surface face was not found");
    }

    private static boolean isOpaqueNeutral(int color) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        return color >>> 24 == 0xFF && red == green && green == blue;
    }

    private static int channelBrightness(int color) {
        return (color >> 16 & 0xFF) + (color >> 8 & 0xFF) + (color & 0xFF);
    }

    private static Vector3d vector(EditorVertex vertex) {
        return new Vector3d(vertex.x(), vertex.y(), vertex.z());
    }

    private static double projectedAxisLength(ViewportTransform transform, Vector3d origin) {
        double worldLength = MoldingGizmoGeometry.axisLength(transform.worldUnitsPerPixel(origin));
        Vector2d start = transform.project(origin).orElseThrow();
        Vector2d end = transform.project(new Vector3d(origin).add(worldLength, 0.0D, 0.0D)).orElseThrow();
        return start.distance(end);
    }

    private static EditableMoldingModel hollowBoxModel() {
        return model(List.of(
            cube(uuid(40), 10.0D, 10.0D, 10.0D, 14.0D, 11.0D, 14.0D),
            cube(uuid(41), 10.0D, 13.0D, 10.0D, 14.0D, 14.0D, 14.0D),
            cube(uuid(42), 10.0D, 11.0D, 10.0D, 11.0D, 13.0D, 14.0D),
            cube(uuid(43), 13.0D, 11.0D, 10.0D, 14.0D, 13.0D, 14.0D),
            cube(uuid(44), 11.0D, 11.0D, 10.0D, 13.0D, 13.0D, 11.0D),
            cube(uuid(45), 11.0D, 11.0D, 13.0D, 13.0D, 13.0D, 14.0D)
        ), List.of());
    }

    private static EditableMoldingModel model(List<MoldingElement> elements, List<MoldingGroup> groups) {
        return new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Test",
            EditableMoldingModel.NORMAL_TYPE,
            elements,
            groups
        );
    }

    private static MoldingElement cube(
        UUID id,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
    ) {
        MoldingVec3 from = new MoldingVec3(minX, minY, minZ);
        MoldingVec3 to = new MoldingVec3(maxX, maxY, maxZ);
        return element(
            id,
            "Cube " + id.getLeastSignificantBits(),
            Optional.empty(),
            from,
            to,
            new MoldingTransform(MoldingVec3.ZERO, MoldingVec3.ZERO, MoldingVec3.ONE, from.add(to).scale(0.5D))
        );
    }

    private static MoldingElement element(
        UUID id,
        String name,
        Optional<UUID> group,
        MoldingVec3 from,
        MoldingVec3 to,
        MoldingTransform transform
    ) {
        return new MoldingElement(id, name, group, from, to, transform, true, false);
    }

    private static UUID uuid(long value) {
        return new UUID(0L, value);
    }

    private static double distanceToRay(ViewportRay ray, Vector3d point) {
        Vector3d separation = new Vector3d(point).sub(ray.origin());
        double distance = Math.max(0.0D, separation.dot(ray.direction()));
        return ray.pointAt(distance).distance(point);
    }

    private static boolean finite(ViewportRay ray) {
        Vector3d origin = ray.origin();
        Vector3d direction = ray.direction();
        return origin.isFinite() && direction.isFinite() && close(direction.length(), 1.0D);
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new GameTestAssertException(message);
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) <= EPSILON;
    }

    private static void checkSegmentWidth(
        EditorScenePart part,
        int color,
        double expectedWidth,
        String message
    ) {
        boolean found = false;
        List<EditorVertex> vertices = part.vertices();
        for (int index = 0; index < vertices.size(); index += 4) {
            if (vertices.get(index).color() != color) continue;
            found = true;
            if (!close(vector(vertices.get(index)).distance(vector(vertices.get(index + 3))), expectedWidth)) {
                throw new GameTestAssertException(message);
            }
        }
        if (!found) throw new GameTestAssertException(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static final class TargetCounters {
        private int created;
        private int resized;
        private int released;
    }

    private static final class FakeTarget {
        private int width;
        private int height;

        private FakeTarget(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
