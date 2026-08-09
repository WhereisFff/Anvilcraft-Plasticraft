package dev.anvilcraft.plasticraft.gametest;

import io.netty.buffer.Unpooled;
import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesivePathPlanner;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayComponentLookup;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayComponentSupport;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayLightSource;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayRedstoneNetwork;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.bake.MoldingTrayShapeAnalysis;
import dev.anvilcraft.plasticraft.molding.bake.MoldingTrayShapeAnalyzer;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingTransform;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContents;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayCell;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponentGeometry;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponentPlacement;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductPreview;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.anvilcraft.plasticraft.molding.type.MoldingTrayIconModel;
import dev.anvilcraft.plasticraft.molding.type.MoldingTypeValidation;
import dev.dubhe.anvilcraft.block.AdvancedComparatorBlock;
import dev.dubhe.anvilcraft.block.entity.ItemDetectorBlockEntity;
import dev.dubhe.anvilcraft.block.entity.PulseGeneratorBlockEntity;
import dev.dubhe.anvilcraft.block.entity.AdvancedComparatorBlockEntity;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 支架扫描、承载元件和移动红石端口的服务端回归测试。 */
public final class MoldedPlasticTrayGameTests {
    private MoldedPlasticTrayGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Tray validation accepts permissive flat faces within its fixed-Y bounds")
    static void trayShapeBoundaries(ExtendedGameTestHelper helper) {
        EditableMoldingModel valid = trayModel(List.of(cube("Body", 16, 0, 16, 32, 4, 32)));
        checkValid(valid, "central 16 x 16 x 4 tray was rejected");

        EditableMoldingModel fractional = trayModel(List.of(
            cube("Fractional body", 16, 0.25D, 16, 32, 4.25D, 32)
        ));
        checkValid(fractional, "exactly four-pixel fractional tray was rejected");
        var fractionalBaked = MoldingModelBaker.bake(fractional);
        MoldingTrayShapeAnalysis fractionalAnalysis = MoldingTrayShapeAnalyzer.analyze(
            fractionalBaked.volumeMask(),
            MoldingModelBaker.createExactSurfaceMesh(fractional)
        );
        check(Math.abs(fractionalAnalysis.topY() - 4.25D) < 1.0E-7D,
            "fractional tray top was rounded: " + fractionalAnalysis);

        MoldingElement rotated = transformedCube(
            "Y-rotated body",
            vec(19, 0, 19),
            vec(29, 4, 29),
            new MoldingVec3(0, 45, 0)
        );
        checkValid(trayModel(List.of(rotated)), "flat tray rotated around Y was rejected");
        checkValid(MoldingTrayIconModel.model(), "bundled open-frame tray icon model was rejected");
        EditableMoldingModel corner = trayModel(List.of(cube("Corner", 0, 0, 0, 16, 2, 16)));
        checkValid(corner, "single corner-cell tray was rejected");
        MoldingTrayShapeAnalysis cornerAnalysis = MoldingTrayShapeAnalyzer.analyze(
            MoldingModelBaker.bake(corner).volumeMask(),
            MoldingModelBaker.createExactSurfaceMesh(corner)
        );
        check(cornerAnalysis.supportsCell(0, 0) && !cornerAnalysis.supportsCell(1, 1),
            "corner tray exposed the wrong component support cells");
        checkValid(
            trayModel(List.of(cube("Full grid", 0, 0, 0, 48, 4, 48))),
            "complete 3 x 3 tray was rejected"
        );

        checkReason(
            trayModel(List.of(cube("Tall", 16, 0, 16, 32, 5, 32))),
            "tray_too_tall"
        );
        checkReason(
            trayModel(List.of(cube("Fractionally tall", 16, 0.25D, 16, 32, 4.251D, 32))),
            "tray_too_tall"
        );
        checkValid(
            trayModel(List.of(cube("Outside", 15, 0, 16, 31, 4, 32))),
            "tray crossing the former center boundary was rejected"
        );
        checkValid(
            trayModel(List.of(cube("Fractionally outside", 15.75D, 0, 16, 31.75D, 4, 32))),
            "fractional tray crossing the former center boundary was rejected"
        );
        checkValid(
            trayModel(List.of(cube("Missing center", 16, 0, 16, 23, 4, 32))),
            "tray without the exact model center was rejected"
        );
        checkValid(
            trayModel(List.of(
                cube("Center", 23, 0, 23, 25, 4, 25),
                cube("Island", 16, 0, 16, 18, 4, 18)
            )),
            "tray with a disconnected face was rejected"
        );
        checkValid(
            trayModel(trayWithHole()),
            "tray with an open face was rejected"
        );
        checkValid(
            trayModel(List.of(
                cube("Bottom", 16, 0, 16, 32, 1, 32),
                cube("Narrow top", 17, 1, 17, 31, 4, 31)
            )),
            "tray with different top and bottom projections was rejected"
        );

        EditableMoldingModel sloped = trayModel(List.of(transformedCube(
            "Sloped body",
            vec(19, 1, 19),
            vec(29, 3, 29),
            new MoldingVec3(5, 0, 0)
        )));
        checkReason(sloped, "tray_bottom_not_flat");

        EditableMoldingModel slopedTop = trayModel(List.of(
            cube("Flat bottom", 19, 0, 19, 29, 1, 29),
            transformedCube(
                "Sloped top",
                vec(19, 1, 19),
                vec(29, 3, 29),
                new MoldingVec3(5, 0, 0)
            )
        ));
        checkReason(slopedTop, "tray_top_not_flat");

        EditableMoldingModel zeroThicknessBottom = trayModel(List.of(
            cube("Interior", 16, 1, 16, 32, 3, 32),
            cube("Bottom sheet", 16, 0, 16, 32, 0, 32),
            cube("Interior top", 16, 3, 16, 32, 3, 32)
        ));
        checkReason(zeroThicknessBottom, "tray_bottom_not_flat");
        EditableMoldingModel zeroThicknessTop = trayModel(List.of(
            cube("Body below sheet", 16, 0, 16, 32, 3, 32),
            cube("Top sheet", 16, 4, 16, 32, 4, 32)
        ));
        checkReason(zeroThicknessTop, "tray_top_not_flat");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "Offset thin products drop beside their real geometry on all attachment faces")
    static void offsetTrayDropsOutsideItsSupport(ExtendedGameTestHelper helper) {
        MoldedPlasticData data = manufactureTray(MoldingTrayIconModel.model());
        BlockPos occupiedPos = new BlockPos(2, 2, 2);
        for (Direction attachmentFace : Direction.values()) {
            PlasticEntityOrientation orientation = new PlasticEntityOrientation(attachmentFace, 0);
            BlockPos supportPos = occupiedPos.relative(attachmentFace.getOpposite());
            helper.setBlock(supportPos, Blocks.STONE);
            UniversalPlasticEntity tray = createTray(
                helper,
                occupiedPos,
                orientation,
                Vec3.ZERO,
                data
            );
            AABB geometryBounds = tray.getBoundingBox();
            check(tray.hurt(helper.getLevel().damageSources().generic(), 1.0F),
                "offset tray rejected destruction damage on " + attachmentFace);
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                geometryBounds.inflate(2.0D),
                item -> MoldedPlasticData.get(item.getItem()).isPresent()
            );
            check(drops.size() == 1, "offset tray produced the wrong drop count on " + attachmentFace);
            ItemEntity drop = drops.getFirst();
            AABB supportBounds = new AABB(helper.absolutePos(supportPos));
            check(!drop.getBoundingBox().intersects(supportBounds),
                "offset tray drop intersected its support block on " + attachmentFace + ": " + drop.position());
            check(drop.getBoundingBox().getCenter().distanceTo(geometryBounds.getCenter()) <= 0.126D,
                "offset tray drop was not generated by its real geometry on " + attachmentFace);
            drop.discard();
            helper.setBlock(supportPos, Blocks.AIR);
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Incomplete tray forming downgrades while the complete model retains tray capability")
    static void partialTrayDowngrade(ExtendedGameTestHelper helper) {
        EditableMoldingModel model = trayModel(List.of(cube("Body", 16, 0, 16, 32, 4, 32)));
        var baked = MoldingModelBaker.bake(model);
        MoldingProductPreview complete = MoldingProductPreview.evaluate(
            model,
            baked,
            baked.analysis().minimumMeltMillibuckets()
        );
        check(!complete.downgraded() && MoldingProductTypes.TRAY_ID.equals(complete.finalType()),
            "complete tray was downgraded: " + complete);
        MoldingProductPreview partial = MoldingProductPreview.evaluate(model, baked, 250);
        check(partial.downgraded() && MoldingProductTypes.NORMAL_ID.equals(partial.finalType()),
            "incomplete tray retained its functional type: " + partial);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Tray component whitelist and persistence codecs remain exact")
    static void componentWhitelistAndCodecs(ExtendedGameTestHelper helper) {
        for (Block block : List.of(
            Blocks.REPEATER,
            Blocks.COMPARATOR,
            Blocks.REDSTONE_TORCH,
            Blocks.LEVER,
            Blocks.DAYLIGHT_DETECTOR,
            ModBlocks.ADVANCED_COMPARATOR.get(),
            ModBlocks.PULSE_GENERATOR.get(),
            ModBlocks.ITEM_DETECTOR.get()
        )) {
            check(MoldedTrayComponent.isSupported(new ItemStack(block)),
                "supported tray component was rejected: " + block);
        }
        BuiltInRegistries.BLOCK.stream()
            .filter(block -> block.defaultBlockState().is(BlockTags.BUTTONS)
                || block.defaultBlockState().is(BlockTags.PRESSURE_PLATES))
            .forEach(block -> check(
                MoldedTrayComponent.isSupported(new ItemStack(block)),
                "tagged tray component was rejected: " + block
            ));
        for (Block block : List.of(Blocks.REDSTONE_WIRE, Blocks.OBSERVER)) {
            check(!MoldedTrayComponent.isSupported(new ItemStack(block)),
                "unsupported tray component was accepted: " + block);
        }

        CompoundTag stateData = new CompoundTag();
        stateData.putInt("OutputSignal", 11);
        stateData.putInt("x", 99);
        MoldedTrayComponent component = new MoldedTrayComponent(
            new ItemStack(Blocks.REPEATER),
            Blocks.REPEATER.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST),
            stateData,
            17,
            List.of(3L, 8L)
        );
        check(!component.blockEntityData().contains("x"), "component retained a world position in persistent data");

        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var encoded = MoldedTrayComponent.CODEC.encodeStart(ops, component).getOrThrow();
        MoldedTrayComponent decoded = MoldedTrayComponent.CODEC.parse(ops, encoded).getOrThrow();
        check(component.equals(decoded), "tray component codec round trip changed its value");

        List<MoldedTrayComponentPlacement> reversedPlacements = MoldedTrayCell.VALUES.reversed().stream()
            .map(cell -> new MoldedTrayComponentPlacement(cell, component))
            .toList();
        MoldedPlasticContents gridContents = new MoldedPlasticContents(List.of(), List.of(), reversedPlacements);
        check(gridContents.trayComponents().size() == 9
                && gridContents.trayComponents().getFirst().cell().equals(new MoldedTrayCell(0, 0))
                && gridContents.trayComponents().getLast().cell().equals(new MoldedTrayCell(2, 2)),
            "nine tray components were not normalized into stable cell order");
        var encodedContents = MoldedPlasticContents.CODEC.encodeStart(ops, gridContents).getOrThrow();
        MoldedPlasticContents decodedContents = MoldedPlasticContents.CODEC.parse(ops, encodedContents).getOrThrow();
        check(gridContents.equals(decodedContents), "nine tray components changed during codec round trip");

        MoldedPlasticData currentData = manufactureTray(trayModel(List.of(
            cube("Legacy support", 0, 0, 0, 48, 4, 48)
        ))).withContents(MoldedPlasticContents.EMPTY.withTrayComponent(Optional.of(component)));
        CompoundTag legacyData = ((CompoundTag) MoldedPlasticData.CODEC.encodeStart(ops, currentData)
            .getOrThrow()).copy();
        legacyData.putInt("format_version", 4);
        CompoundTag legacyContents = legacyData.getCompound("contents");
        legacyContents.remove("tray_components");
        legacyContents.put(
            "tray_component",
            MoldedTrayComponent.CODEC.encodeStart(ops, component).getOrThrow()
        );
        legacyData.put("contents", legacyContents);
        MoldedPlasticData migratedData = MoldedPlasticData.CODEC.parse(ops, legacyData).getOrThrow();
        check(migratedData.formatVersion() == MoldedPlasticData.CURRENT_FORMAT_VERSION,
            "format four molded plastic was not normalized to the current format");
        check(migratedData.contents().trayComponent(MoldedTrayCell.CENTER).filter(component::equals).isPresent(),
            "format four tray component was not migrated to the center cell");
        CompoundTag migratedEncoding = (CompoundTag) MoldedPlasticData.CODEC.encodeStart(ops, migratedData)
            .getOrThrow();
        check(migratedEncoding.getInt("format_version") == MoldedPlasticData.CURRENT_FORMAT_VERSION,
            "migrated molded plastic was written with its legacy format version");
        CompoundTag migratedContents = migratedEncoding.getCompound("contents");
        check(migratedContents.contains("tray_components") && !migratedContents.contains("tray_component"),
            "migrated tray contents were written with the legacy component field");

        boolean duplicateRejected = false;
        try {
            List<MoldedTrayComponentPlacement> duplicate = new ArrayList<>(reversedPlacements);
            duplicate.add(new MoldedTrayComponentPlacement(MoldedTrayCell.CENTER, component));
            new MoldedPlasticContents(List.of(), List.of(), duplicate);
        } catch (IllegalArgumentException ignored) {
            duplicateRejected = true;
        }
        check(duplicateRejected, "a tenth or duplicate tray component was accepted");

        boolean unsupportedCellRejected = false;
        try {
            MoldedPlasticData cornerData = manufactureTray(trayModel(List.of(
                cube("Corner support", 0, 0, 0, 16, 2, 16)
            )));
            cornerData.withContents(MoldedPlasticContents.EMPTY.withTrayComponent(Optional.of(component)));
        } catch (IllegalArgumentException ignored) {
            unsupportedCellRejected = true;
        }
        check(unsupportedCellRejected, "a tray component was stored in a cell without a volumetric cube");

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
            Unpooled.buffer(),
            helper.getLevel().registryAccess(),
            ConnectionType.NEOFORGE
        );
        try {
            MoldedTrayComponent.STREAM_CODEC.encode(buffer, component);
            MoldedTrayComponent streamed = MoldedTrayComponent.STREAM_CODEC.decode(buffer);
            check(component.equals(streamed), "tray component stream codec round trip changed its value");
        } finally {
            buffer.release();
        }
        RegistryFriendlyByteBuf contentsBuffer = new RegistryFriendlyByteBuf(
            Unpooled.buffer(),
            helper.getLevel().registryAccess(),
            ConnectionType.NEOFORGE
        );
        try {
            MoldedPlasticContents.encode(contentsBuffer, gridContents);
            MoldedPlasticContents streamed = MoldedPlasticContents.decode(contentsBuffer);
            check(gridContents.equals(streamed), "nine tray components changed during stream round trip");
        } finally {
            contentsBuffer.release();
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Tray placement and extraction preserve configuration but remove runtime state and ports")
    static void componentPlacementAndExtraction(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity tray = createTray(
            helper,
            new BlockPos(4, 3, 4),
            PlasticEntityOrientation.DEFAULT,
            pulseGenerator(1, 1),
            Vec3.ZERO
        );
        tray.plasticraft$clearTrayComponent();

        ItemStack configured = MoldedTrayComponentSupport.extractionStack(
            pulseGenerator(0, 5),
            null,
            helper.getLevel()
        );
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, configured);
        check(tray.interact(player, InteractionHand.MAIN_HAND).consumesAction(),
            "tray rejected a supported component interaction");
        check(player.getMainHandItem().isEmpty(), "tray did not consume the mounted component");

        Direction facing = trayComponent(tray).state().getValue(BlockStateProperties.HORIZONTAL_FACING);
        tray.getMoldedTrayRuntime().tick();
        helper.getLevel().setBlockAndUpdate(
            MoldedTrayRedstoneNetwork.adjacentCell(tray, facing),
            Blocks.REDSTONE_BLOCK.defaultBlockState()
        );
        tray.getMoldedTrayRuntime().tick();
        BlockPos outputCell = MoldedTrayRedstoneNetwork.componentCell(tray, facing.getOpposite());
        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                check(MoldedTrayRedstoneNetwork.weakSignal(helper.getLevel(), outputCell, facing) == 15,
                    "mounted pulse generator did not publish its output port");

                player.setShiftKeyDown(true);
                check(tray.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND).consumesAction(),
                    "tray rejected sneak-empty-hand extraction");
                check(tray.getMoldedData().flatMap(data -> data.contents().trayComponent()).isEmpty(),
                    "tray retained its extracted component");
                check(MoldedTrayRedstoneNetwork.weakSignal(helper.getLevel(), outputCell, facing) == 0,
                    "tray extraction left its redstone port in the network");

                ItemStack extracted = findInventoryItem(player, ModBlocks.PULSE_GENERATOR.get());
                CompoundTag extractedData = extracted.getOrDefault(
                    DataComponents.BLOCK_ENTITY_DATA,
                    CustomData.EMPTY
                ).copyTag();
                CompoundTag extraData = extractedData.getCompound("ExtraData");
                check(extraData.getInt("WaitingTime") == 0 && extraData.getInt("SignalDuration") == 5,
                    "tray extraction lost pulse generator configuration");
                check(!extractedData.contains("OutputSignal")
                        && !extraData.contains("Inputting")
                        && !extraData.contains("State")
                        && !extraData.contains("PhaseStartGameTime")
                        && !extraData.contains("PhaseDuration"),
                    "tray extraction leaked transient component runtime state");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "11x8x11", floor = true)
    @TestHolder(description = "Tray hit positions select independent supported cells across rotated off-grid geometry")
    static void componentPlacementUsesHitCell(ExtendedGameTestHelper helper) {
        MoldedPlasticData data = manufactureTray(trayModel(List.of(
            cube("Full support", 0, 0, 0, 48, 4, 48)
        )));
        PlasticEntityOrientation orientation = new PlasticEntityOrientation(Direction.EAST, 1);
        UniversalPlasticEntity tray = createTray(
            helper,
            new BlockPos(5, 4, 5),
            orientation,
            new Vec3(0.23D, 0.17D, -0.19D),
            data
        );
        MoldedTrayCell target = new MoldedTrayCell(0, 2);
        Vec3 localHit = new Vec3(target.x() + 0.5D, data.surfaceBounds().maxY, target.z() + 0.5D);
        Vec3 relativeHit = tray.plasticraft$getGeometry().worldPointAt(
            tray.position(),
            orientation,
            localHit
        ).subtract(tray.position());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.LEVER));
        check(tray.interactAt(player, relativeHit, InteractionHand.MAIN_HAND).consumesAction(),
            "tray rejected placement at the selected off-grid cell");
        check(tray.getMoldedData().flatMap(value -> value.contents().trayComponent(target)).isPresent(),
            "tray mounted the component in a different cell than the hit position");
        check(tray.getMoldedData().orElseThrow().contents().trayComponents().size() == 1,
            "single-cell placement duplicated the component");

        player.setShiftKeyDown(true);
        check(tray.interactAt(player, relativeHit, InteractionHand.MAIN_HAND).consumesAction(),
            "tray rejected extraction from the selected off-grid cell");
        check(tray.getMoldedData().orElseThrow().contents().trayComponents().isEmpty(),
            "tray extraction cleared a different cell");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "15x9x13", floor = true)
    @TestHolder(description = "A block adhesive target aligns the selected asymmetric tray face instead of its bounds")
    static void adhesiveTargetAlignsSelectedTrayFace(ExtendedGameTestHelper helper) {
        MoldedPlasticData data = manufactureTray(trayModel(List.of(
            cube("Long arm", 0, 0, 0, 48, 4, 16),
            cube("Short arm", 0, 0, 16, 16, 4, 48)
        )));
        UniversalPlasticEntity tray = createTray(
            helper,
            new BlockPos(3, 4, 6),
            PlasticEntityOrientation.DEFAULT,
            Vec3.ZERO,
            data
        );
        tray.setNoGravity(true);
        BlockPos support = new BlockPos(12, 4, 6);
        helper.setBlock(support, Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        AdhesivePathPlanner.Plan plan = AdhesivePathPlanner.plan(
            helper.getLevel(),
            tray,
            player,
            helper.absolutePos(support),
            Direction.WEST,
            Direction.EAST
        );
        check(plan.valid(), "asymmetric tray face alignment produced no adhesive path: " + plan.status());
        Vec3 selectedFaceCenter = tray.plasticraft$getGeometry().surfacePointAt(
            plan.targetPosition(),
            plan.targetOrientation(),
            Direction.EAST
        );
        Vec3 targetFaceCenter = Vec3.atCenterOf(helper.absolutePos(support)).add(-0.5D, 0.0D, 0.0D);
        check(selectedFaceCenter.distanceToSqr(targetFaceCenter) < 1.0E-8D,
            "adhesive target centered the tray bounds instead of aligning its selected face: selected="
                + selectedFaceCenter + ", target=" + targetFaceCenter);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "13x8x13", floor = true)
    @TestHolder(description = "A tray button powers an adjacent inward-facing repeater and its world output")
    static void adjacentComponentsInteractAndOutput(ExtendedGameTestHelper helper) {
        MoldedTrayCell buttonCell = new MoldedTrayCell(1, 1);
        MoldedTrayCell repeaterCell = new MoldedTrayCell(1, 2);
        MoldedTrayComponent button = new MoldedTrayComponent(
            new ItemStack(Blocks.STONE_BUTTON),
            Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.POWERED, false),
            new CompoundTag(),
            0,
            List.of()
        );
        MoldedTrayComponent repeater = new MoldedTrayComponent(
            new ItemStack(Blocks.REPEATER),
            Blocks.REPEATER.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.POWERED, false),
            new CompoundTag(),
            0,
            List.of()
        );
        MoldedPlasticContents contents = new MoldedPlasticContents(
            List.of(),
            List.of(),
            List.of(
                new MoldedTrayComponentPlacement(buttonCell, button),
                new MoldedTrayComponentPlacement(repeaterCell, repeater)
            )
        );
        MoldedPlasticData data = manufactureTray(trayModel(List.of(
            cube("Full support", 0, 0, 0, 48, 4, 48)
        ))).withContents(contents);
        UniversalPlasticEntity tray = createTray(
            helper,
            new BlockPos(6, 4, 5),
            PlasticEntityOrientation.DEFAULT,
            Vec3.ZERO,
            data
        );
        BlockPos receiver = MoldedTrayRedstoneNetwork.adjacentCell(tray, repeaterCell, Direction.SOUTH);
        helper.getLevel().setBlockAndUpdate(receiver, Blocks.REDSTONE_LAMP.defaultBlockState());
        tray.getMoldedTrayRuntimes().tick();

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        check(tray.getMoldedTrayRuntime(buttonCell).interact(player, InteractionHand.MAIN_HAND).consumesAction(),
            "mounted button rejected interaction");
        tray.getMoldedTrayRuntimes().tick();
        helper.startSequence()
            .thenIdle(5)
            .thenExecute(() -> {
                MoldedTrayComponent poweredRepeater = tray.getMoldedData()
                    .flatMap(value -> value.contents().trayComponent(repeaterCell))
                    .orElseThrow();
                check(poweredRepeater.state().getValue(BlockStateProperties.POWERED),
                    "adjacent repeater did not receive the mounted button signal");
                BlockPos source = MoldedTrayRedstoneNetwork.componentCell(
                    tray,
                    repeaterCell,
                    Direction.SOUTH
                );
                check(MoldedTrayRedstoneNetwork.weakSignal(helper.getLevel(), source, Direction.NORTH) == 15,
                    "adjacent repeater did not publish its signal at the corresponding world position");
                check(helper.getLevel().getBlockState(receiver).getValue(BlockStateProperties.LIT),
                    "adjacent repeater did not power its world receiver");
                helper.succeed();
            });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "13x8x9", floor = true)
    @TestHolder(description = "Mounted emitting components keep their native block light through state and movement changes")
    static void emittingComponentLightsWorld(ExtendedGameTestHelper helper) {
        MoldedTrayComponent litTorch = new MoldedTrayComponent(
            new ItemStack(Blocks.REDSTONE_TORCH),
            Blocks.REDSTONE_TORCH.defaultBlockState().setValue(BlockStateProperties.LIT, true),
            new CompoundTag(),
            0,
            List.of()
        );
        UniversalPlasticEntity tray = createTray(
            helper,
            new BlockPos(3, 4, 4),
            PlasticEntityOrientation.DEFAULT,
            litTorch,
            Vec3.ZERO
        );
        ServerLevel level = helper.getLevel();
        BlockPos source = MoldedTrayRedstoneNetwork.componentPosition(tray, MoldedTrayCell.CENTER);
        MoldedTrayLightSource.update(tray);
        BlockPos[] movedSource = new BlockPos[1];
        helper.startSequence()
            .thenIdle(2)
            .thenExecute(() -> {
                check(level.getBrightness(LightLayer.BLOCK, source) == 7,
                    "mounted redstone torch did not emit its native level-seven block light");
                check(level.getBrightness(LightLayer.BLOCK, source.east()) == 6,
                    "mounted redstone torch light did not propagate into the world");

                tray.plasticraft$replaceTrayComponent(litTorch.withState(
                    litTorch.state().setValue(BlockStateProperties.LIT, false)
                ));
                MoldedTrayLightSource.update(tray);
            })
            .thenIdle(2)
            .thenExecute(() -> {
                check(level.getBrightness(LightLayer.BLOCK, source) == 0,
                    "extinguished mounted redstone torch left world light behind");

                tray.plasticraft$replaceTrayComponent(litTorch);
                tray.setPos(tray.position().add(3.0D, 0.0D, 0.0D));
                movedSource[0] = MoldedTrayRedstoneNetwork.componentPosition(tray, MoldedTrayCell.CENTER);
                MoldedTrayLightSource.update(tray);
            })
            .thenIdle(2)
            .thenExecute(() -> {
                check(level.getBlockState(source).getLightEmission(level, source) == 0,
                    "moving the tray left its old component light source registered");
                check(level.getBrightness(LightLayer.BLOCK, movedSource[0]) == 7,
                    "mounted redstone torch light did not follow the tray");
                tray.discard();
            })
            .thenIdle(2)
            .thenExecute(() -> check(level.getBrightness(LightLayer.BLOCK, movedSource[0]) == 0,
                "removing the tray left its component light source behind"))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "9x8x9", floor = true)
    @TestHolder(description = "Blockification handoff keeps one tray payload and the cloned redstone network owner")
    static void blockificationHandoffKeepsTrayNetwork(ExtendedGameTestHelper helper) {
        MoldedTrayComponent torch = new MoldedTrayComponent(
            new ItemStack(Blocks.REDSTONE_TORCH),
            Blocks.REDSTONE_TORCH.defaultBlockState().setValue(BlockStateProperties.LIT, true),
            new CompoundTag(),
            0,
            List.of()
        );
        BlockPos occupied = new BlockPos(4, 4, 4);
        helper.setBlock(occupied.below(), Blocks.STONE);
        UniversalPlasticEntity original = createTray(
            helper,
            occupied,
            PlasticEntityOrientation.DEFAULT,
            torch,
            Vec3.ZERO
        );
        original.getMoldedTrayRuntime().tick();

        BlockState bondedState = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState()
            .setValue(AbstractPlasticEntityBlock.BONDED, true);
        helper.setBlock(occupied, bondedState);
        check(helper.getBlockEntity(occupied) instanceof BondedEntityBlockEntity bonded
                && bonded.initialize(
                    original,
                    original.getDisplayState(),
                    Direction.UP,
                    original.getOrientation(),
                    false
                ),
            "failed to create the blockified tray snapshot");
        BondedEntityBlockEntity bonded = (BondedEntityBlockEntity) helper.getBlockEntity(occupied);
        check(bonded.getOrCreateRenderEntity() instanceof UniversalPlasticEntity clone,
            "blockified tray did not restore its functional entity");
        UniversalPlasticEntity clone = (UniversalPlasticEntity) bonded.getOrCreateRenderEntity();
        check(clone.getUUID().equals(original.getUUID()), "blockified tray clone changed the entity UUID");
        clone.plasticraft$tickBondedTray();
        original.discard();

        check(original.isRemoved(), "original tray survived its blockification handoff");
        check(helper.getBlockState(occupied).is(PlasticraftBlocks.UNIVERSAL_PLASTIC.get()),
            "original tray removal deleted the blockified tray");
        check(clone.getMoldedData().orElseThrow().contents().trayComponents().size() == 1,
            "blockified tray duplicated or lost its mounted payload");
        BlockPos source = MoldedTrayRedstoneNetwork.componentCell(clone, Direction.SOUTH);
        check(MoldedTrayRedstoneNetwork.weakSignal(helper.getLevel(), source, Direction.NORTH) == 15,
            "original tray removal unregistered the blockified clone's redstone output");
        BlockPos lightSource = MoldedTrayRedstoneNetwork.componentPosition(clone, MoldedTrayCell.CENTER);
        helper.startSequence()
            .thenIdle(2)
            .thenExecute(() -> check(helper.getLevel().getBrightness(LightLayer.BLOCK, lightSource) == 7,
                "original tray removal cleared the blockified clone's light source"))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "A range-one tray item detector scans only its first adjacent cell")
    static void itemDetectorRangeOne(ExtendedGameTestHelper helper) {
        CompoundTag detectorData = new CompoundTag();
        detectorData.putInt("Range", 1);
        detectorData.putString("FilterMode", "ANY");
        BlockState detectorState = ModBlocks.ITEM_DETECTOR.get().defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        MoldedTrayComponent detector = new MoldedTrayComponent(
            new ItemStack(ModBlocks.ITEM_DETECTOR.get()),
            detectorState,
            detectorData,
            0,
            List.of()
        );
        UniversalPlasticEntity tray = createTray(
            helper,
            new BlockPos(4, 2, 4),
            PlasticEntityOrientation.DEFAULT,
            detector,
            Vec3.ZERO
        );
        BlockPos first = MoldedTrayRedstoneNetwork.adjacentCell(tray, Direction.SOUTH);
        BlockPos second = first.relative(Direction.SOUTH);
        ItemEntity outside = spawnItem(helper, second);
        tray.getMoldedTrayRuntime().tick();
        BlockEntity blockEntity = tray.plasticraft$getTrayBlockEntity();
        check(blockEntity instanceof ItemDetectorBlockEntity, "tray item detector block entity was not restored");
        check(AABB.encapsulatingFullBlocks(first, first).equals(((ItemDetectorBlockEntity) blockEntity).shape()),
            "tray item detector displayed a range different from its scanned cells");
        check(detectorOutput(tray) == 0, "range-one detector scanned its second adjacent cell");

        outside.discard();
        spawnItem(helper, first);
        tray.getMoldedTrayRuntime().tick();
        check(detectorOutput(tray) > 0, "range-one detector ignored its first adjacent cell");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "15x15x15", floor = true)
    @TestHolder(description = "Narrow tray ports use the carried component cube instead of the plastic surface")
    static void narrowTrayPortsUseComponentCube(ExtendedGameTestHelper helper) {
        MoldedTrayComponent torch = new MoldedTrayComponent(
            new ItemStack(Blocks.REDSTONE_TORCH),
            Blocks.REDSTONE_TORCH.defaultBlockState().setValue(BlockStateProperties.LIT, true),
            new CompoundTag(),
            0,
            List.of()
        );
        EditableMoldingModel narrowModel = trayModel(List.of(cube("Narrow body", 22, 0, 22, 26, 4, 26)));
        UniversalPlasticEntity tray = createTray(
            helper,
            new BlockPos(7, 7, 7),
            PlasticEntityOrientation.DEFAULT,
            torch,
            new Vec3(0.21D, 0.17D, -0.18D),
            manufactureTray(narrowModel)
        );
        for (Direction face : Direction.values()) {
            BlockPos relativeExpected = switch (face) {
                case DOWN, SOUTH, WEST -> new BlockPos(7, 7, 7);
                case UP -> new BlockPos(7, 8, 7);
                case NORTH -> new BlockPos(7, 7, 6);
                case EAST -> new BlockPos(8, 7, 7);
            };
            BlockPos expected = helper.absolutePos(relativeExpected);
            BlockPos actual = MoldedTrayRedstoneNetwork.componentCell(tray, face);
            check(expected.equals(actual),
                "wrong narrow-tray component cell for " + face + ": expected " + expected + ", got " + actual);
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "Authoritative tray component updates reload once and survive host movement")
    static void authoritativeComponentReloadSurvivesMovement(ExtendedGameTestHelper helper) {
        CompoundTag initialData = new CompoundTag();
        initialData.putInt("OutputSignal", 0);
        MoldedTrayComponent initial = new MoldedTrayComponent(
            new ItemStack(Blocks.COMPARATOR),
            Blocks.COMPARATOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.POWERED, false),
            initialData,
            0,
            List.of()
        );
        UniversalPlasticEntity tray = createTray(
            helper,
            new BlockPos(3, 3, 3),
            PlasticEntityOrientation.DEFAULT,
            initial,
            Vec3.ZERO
        );
        BlockEntity original = tray.plasticraft$getTrayBlockEntity();
        check(original != null, "tray comparator block entity was not restored");

        CompoundTag replacementData = new CompoundTag();
        replacementData.putInt("OutputSignal", 9);
        MoldedTrayComponent replacement = initial.replace(
            initial.state().setValue(BlockStateProperties.POWERED, true),
            replacementData,
            0,
            List.of()
        );
        tray.plasticraft$replaceTrayComponent(replacement);
        BlockEntity refreshed = tray.plasticraft$getTrayBlockEntity();
        check(refreshed != null && refreshed.getBlockState().getValue(BlockStateProperties.POWERED),
            "runtime replaced an authoritative component update with its stale snapshot");

        tray.setPos(tray.position().add(1.1D, 0.0D, 0.0D));
        check(tray.plasticraft$getTrayBlockEntity() == refreshed,
            "crossing a block boundary replaced the live tray block entity");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Tray menu lookup selects the requested host instead of the first nearby component")
    static void menuLookupSelectsRequestedHost(ExtendedGameTestHelper helper) {
        createTray(
            helper,
            new BlockPos(3, 3, 4),
            PlasticEntityOrientation.DEFAULT,
            pulseGenerator(5, 2),
            Vec3.ZERO
        );
        MoldedTrayCell otherCell = new MoldedTrayCell(2, 1);
        MoldedPlasticData targetData = manufactureTray(trayModel(List.of(
            cube("Full support", 0, 0, 0, 48, 4, 48)
        ))).withContents(new MoldedPlasticContents(
            List.of(),
            List.of(),
            List.of(
                new MoldedTrayComponentPlacement(MoldedTrayCell.CENTER, pulseGenerator(7, 3)),
                new MoldedTrayComponentPlacement(otherCell, pulseGenerator(9, 4))
            )
        ));
        UniversalPlasticEntity target = createTray(
            helper,
            new BlockPos(4, 3, 4),
            PlasticEntityOrientation.DEFAULT,
            Vec3.ZERO,
            targetData
        );
        BlockEntity expected = target.plasticraft$getTrayBlockEntity();
        BlockEntity other = target.plasticraft$getTrayBlockEntity(otherCell);
        BlockEntity found = MoldedTrayComponentLookup.find(
            helper.getLevel(),
            expected.getBlockPos(),
            MoldedTrayComponentLookup.Kind.PULSE_GENERATOR
        );
        check(found == expected, "tray menu lookup selected a nearby component instead of its requested host");
        check(!expected.getBlockPos().equals(other.getBlockPos()),
            "two tray menu components received the same virtual position");
        check(MoldedTrayComponentLookup.find(
            helper.getLevel(),
            other.getBlockPos(),
            MoldedTrayComponentLookup.Kind.PULSE_GENERATOR
        ) == other, "tray menu lookup selected another cell from the same host");
        target.setPos(target.position().add(1.1D, 0.0D, 0.0D));
        BlockPos movedPosition = MoldedTrayRedstoneNetwork.componentPosition(target, MoldedTrayCell.CENTER);
        check(MoldedTrayComponentLookup.find(
            helper.getLevel(),
            movedPosition,
            MoldedTrayComponentLookup.Kind.PULSE_GENERATOR
        ) == expected, "moving a tray made its pulse generator unavailable to the menu lookup");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Changing a tray comparator mode refreshes its output immediately")
    static void comparatorModeRefreshesImmediately(ExtendedGameTestHelper helper) {
        CompoundTag comparatorData = new CompoundTag();
        comparatorData.putInt("OutputSignal", 15);
        MoldedTrayComponent comparator = new MoldedTrayComponent(
            new ItemStack(Blocks.COMPARATOR),
            Blocks.COMPARATOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(ComparatorBlock.MODE, ComparatorMode.COMPARE)
                .setValue(BlockStateProperties.POWERED, true),
            comparatorData,
            0,
            List.of()
        );
        UniversalPlasticEntity tray = createTray(
            helper,
            new BlockPos(4, 3, 4),
            PlasticEntityOrientation.DEFAULT,
            comparator,
            Vec3.ZERO
        );
        ServerLevel level = helper.getLevel();
        level.setBlockAndUpdate(
            MoldedTrayRedstoneNetwork.adjacentCell(tray, Direction.NORTH),
            Blocks.REDSTONE_BLOCK.defaultBlockState()
        );
        level.setBlockAndUpdate(
            MoldedTrayRedstoneNetwork.adjacentCell(tray, Direction.EAST),
            Blocks.REDSTONE_BLOCK.defaultBlockState()
        );
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        check(tray.getMoldedTrayRuntime().interact(player, InteractionHand.MAIN_HAND).consumesAction(),
            "tray comparator rejected its mode interaction");

        MoldedTrayComponent updated = tray.getMoldedData()
            .flatMap(data -> data.contents().trayComponent())
            .orElseThrow();
        check(updated.state().getValue(ComparatorBlock.MODE) == ComparatorMode.SUBTRACT,
            "tray comparator did not change to subtract mode");
        check(!updated.state().getValue(BlockStateProperties.POWERED)
                && updated.blockEntityData().getInt("OutputSignal") == 0,
            "tray comparator deferred its mode-dependent output refresh");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Tray comparators resample their input when the native scheduled tick executes")
    static void comparatorScheduledTickResamplesInput(ExtendedGameTestHelper helper) {
        MoldedTrayComponent comparator = new MoldedTrayComponent(
            new ItemStack(Blocks.COMPARATOR),
            Blocks.COMPARATOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
            new CompoundTag(),
            0,
            List.of()
        );
        UniversalPlasticEntity tray = createTray(
            helper,
            new BlockPos(4, 3, 4),
            PlasticEntityOrientation.DEFAULT,
            comparator,
            Vec3.ZERO
        );
        ServerLevel level = helper.getLevel();
        BlockPos input = MoldedTrayRedstoneNetwork.adjacentCell(tray, Direction.NORTH);
        level.setBlockAndUpdate(input, Blocks.REDSTONE_BLOCK.defaultBlockState());
        tray.getMoldedTrayRuntime().tick();
        check(scheduledTicks(tray) == 2, "tray comparator did not schedule its two-tick delay");
        check(trayComponent(tray).blockEntityData().getInt("OutputSignal") == 0,
            "tray comparator changed output before its scheduled tick");

        helper.startSequence()
            .thenIdle(1)
            .thenExecute(() -> {
                level.setBlockAndUpdate(
                    input,
                    Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 7)
                );
                check(trayComponent(tray).blockEntityData().getInt("OutputSignal") == 0,
                    "tray comparator changed output before the pending scheduled tick");
            })
            .thenIdle(2)
            .thenExecute(() -> {
                MoldedTrayComponent updated = trayComponent(tray);
                check(updated.blockEntityData().getInt("OutputSignal") == 7,
                    "tray comparator did not resample input during its scheduled tick");
                check(updated.state().getValue(BlockStateProperties.POWERED),
                    "tray comparator did not publish its scheduled output");
                check(updated.scheduledTicks() == 0,
                    "tray comparator retained an expired scheduled tick");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "A tray repeater whose delay expires while locked restarts its full delay after unlocking")
    static void repeaterLockExpiryRestartsDelay(ExtendedGameTestHelper helper) {
        MoldedTrayComponent repeater = new MoldedTrayComponent(
            new ItemStack(Blocks.REPEATER),
            Blocks.REPEATER.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
            new CompoundTag(),
            0,
            List.of()
        );
        UniversalPlasticEntity tray = createTray(
            helper,
            new BlockPos(4, 3, 4),
            PlasticEntityOrientation.DEFAULT,
            repeater,
            Vec3.ZERO
        );
        ServerLevel level = helper.getLevel();
        level.setBlockAndUpdate(
            MoldedTrayRedstoneNetwork.adjacentCell(tray, Direction.NORTH),
            Blocks.REDSTONE_BLOCK.defaultBlockState()
        );
        tray.getMoldedTrayRuntime().tick();
        check(scheduledTicks(tray) == 2, "tray repeater did not schedule its two-tick delay");

        BlockPos lockSource = MoldedTrayRedstoneNetwork.adjacentCell(tray, Direction.EAST);
        level.setBlockAndUpdate(
            lockSource,
            Blocks.REPEATER.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .setValue(BlockStateProperties.POWERED, true)
        );
        tray.getMoldedTrayRuntime().tick();
        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                MoldedTrayComponent locked = trayComponent(tray);
                check(locked.state().getValue(BlockStateProperties.LOCKED),
                    "side diode did not lock the tray repeater");
                check(locked.scheduledTicks() == 0
                        && !locked.state().getValue(BlockStateProperties.POWERED),
                    "tray repeater changed output when its scheduled tick expired while locked");

                level.setBlockAndUpdate(lockSource, Blocks.AIR.defaultBlockState());
                tray.getMoldedTrayRuntime().tick();
                check(scheduledTicks(tray) == 2
                        && !trayComponent(tray).state().getValue(BlockStateProperties.POWERED),
                    "unlocking applied an expired tray repeater transition without a fresh delay");
            })
            .thenIdle(1)
            .thenExecute(() -> check(!trayComponent(tray).state().getValue(BlockStateProperties.POWERED),
                "tray repeater completed its restarted delay one tick early"))
            .thenIdle(1)
            .thenExecute(() -> check(trayComponent(tray).state().getValue(BlockStateProperties.POWERED),
                "tray repeater did not complete its restarted delay"))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Tray repeaters preserve a short input pulse through native scheduled ticks")
    static void repeaterScheduledTickPreservesShortPulse(ExtendedGameTestHelper helper) {
        MoldedTrayComponent repeater = new MoldedTrayComponent(
            new ItemStack(Blocks.REPEATER),
            Blocks.REPEATER.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
            new CompoundTag(),
            0,
            List.of()
        );
        UniversalPlasticEntity tray = createTray(
            helper,
            new BlockPos(4, 3, 4),
            PlasticEntityOrientation.DEFAULT,
            repeater,
            Vec3.ZERO
        );
        ServerLevel level = helper.getLevel();
        BlockPos input = MoldedTrayRedstoneNetwork.adjacentCell(tray, Direction.NORTH);
        level.setBlockAndUpdate(input, Blocks.REDSTONE_BLOCK.defaultBlockState());
        tray.getMoldedTrayRuntime().tick();
        check(scheduledTicks(tray) == 2, "tray repeater did not schedule its rising edge");

        helper.startSequence()
            .thenIdle(1)
            .thenExecute(() -> level.setBlockAndUpdate(input, Blocks.AIR.defaultBlockState()))
            .thenIdle(2)
            .thenExecute(() -> {
                MoldedTrayComponent pulsing = trayComponent(tray);
                check(pulsing.state().getValue(BlockStateProperties.POWERED),
                    "tray repeater discarded an input pulse shorter than its delay");
                check(pulsing.scheduledTicks() > 0,
                    "tray repeater did not schedule the short pulse falling edge");
            })
            .thenIdle(2)
            .thenExecute(() -> check(!trayComponent(tray).state().getValue(BlockStateProperties.POWERED),
                "tray repeater did not end its preserved short pulse"))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "13x7x9", floor = true)
    @TestHolder(description = "Tray pulse generators preserve zero waiting and zero-duration pulse behavior")
    static void pulseGeneratorZeroPhaseDurations(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity zeroWait = createTray(
            helper,
            new BlockPos(3, 3, 4),
            PlasticEntityOrientation.DEFAULT,
            pulseGenerator(0, 1),
            Vec3.ZERO
        );
        ServerLevel level = helper.getLevel();
        zeroWait.getMoldedTrayRuntime().tick();
        BlockPos zeroWaitLamp = MoldedTrayRedstoneNetwork.adjacentCell(zeroWait, Direction.SOUTH);
        level.setBlockAndUpdate(zeroWaitLamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        level.setBlockAndUpdate(
            MoldedTrayRedstoneNetwork.adjacentCell(zeroWait, Direction.NORTH),
            Blocks.REDSTONE_BLOCK.defaultBlockState()
        );

        UniversalPlasticEntity zeroOutput = createTray(
            helper,
            new BlockPos(9, 3, 4),
            PlasticEntityOrientation.DEFAULT,
            pulseGenerator(1, 0),
            Vec3.ZERO
        );
        BlockPos lamp = MoldedTrayRedstoneNetwork.adjacentCell(zeroOutput, Direction.SOUTH);
        level.setBlockAndUpdate(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        zeroOutput.getMoldedTrayRuntime().tick();
        level.setBlockAndUpdate(
            MoldedTrayRedstoneNetwork.adjacentCell(zeroOutput, Direction.NORTH),
            Blocks.REDSTONE_BLOCK.defaultBlockState()
        );
        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                check(level.getBlockState(zeroWaitLamp).getValue(BlockStateProperties.LIT),
                    "zero-wait tray pulse never reached its neighboring redstone");
                check(!trayComponent(zeroWait).state().getValue(BlockStateProperties.POWERED),
                    "zero-wait tray pulse did not end its one-tick output");
                check(level.getBlockState(lamp).getValue(BlockStateProperties.LIT),
                    "zero-duration tray pulse was collapsed before neighboring redstone observed it");
                check(!trayComponent(zeroOutput).state().getValue(BlockStateProperties.POWERED)
                        && scheduledTicks(zeroOutput) == 0,
                    "zero-duration tray pulse did not return to its default state in the triggering tick");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "16x8x9", floor = true)
    @TestHolder(description = "Tray pulse generators retain the block-tick to block-event zero-tick piston behavior")
    static void pulseGeneratorZeroTickPistonParity(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ordinaryPulsePos = helper.absolutePos(new BlockPos(4, 3, 3));
        BlockPos ordinaryPistonPos = ordinaryPulsePos.south();
        BlockPos ordinaryCarriedPos = ordinaryPistonPos.west();
        BlockPos ordinaryDestination = ordinaryCarriedPos.west();
        level.setBlockAndUpdate(
            ordinaryPulsePos,
            ModBlocks.PULSE_GENERATOR.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
        );
        BlockEntity ordinaryBlockEntity = level.getBlockEntity(ordinaryPulsePos);
        check(ordinaryBlockEntity instanceof PulseGeneratorBlockEntity,
            "ordinary pulse generator block entity was not created");
        PulseGeneratorBlockEntity ordinaryPulse = (PulseGeneratorBlockEntity) ordinaryBlockEntity;
        ordinaryPulse.setWaitingTime(1);
        ordinaryPulse.setSignalDuration(0);
        level.setBlockAndUpdate(
            ordinaryPistonPos,
            Blocks.STICKY_PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.WEST)
        );
        level.setBlockAndUpdate(ordinaryCarriedPos, Blocks.GLASS.defaultBlockState());
        ArmorStand ordinaryStand = stationaryArmorStand(helper, ordinaryDestination);

        UniversalPlasticEntity tray = createTray(
            helper,
            new BlockPos(11, 3, 3),
            PlasticEntityOrientation.DEFAULT,
            pulseGenerator(1, 0),
            Vec3.ZERO
        );
        tray.getMoldedTrayRuntime().tick();
        BlockPos trayPistonPos = MoldedTrayRedstoneNetwork.adjacentCell(tray, Direction.SOUTH);
        BlockPos trayCarriedPos = trayPistonPos.west();
        BlockPos trayDestination = trayCarriedPos.west();
        level.setBlockAndUpdate(
            trayPistonPos,
            Blocks.STICKY_PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.WEST)
        );
        level.setBlockAndUpdate(trayCarriedPos, Blocks.GLASS.defaultBlockState());
        ArmorStand trayStand = stationaryArmorStand(helper, trayDestination);
        Vec3 ordinaryStandStart = ordinaryStand.position();
        Vec3 trayStandStart = trayStand.position();

        helper.startSequence()
            .thenIdle(2)
            .thenExecute(() -> {
                level.setBlockAndUpdate(ordinaryPulsePos.north(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(
                    MoldedTrayRedstoneNetwork.adjacentCell(tray, Direction.NORTH),
                    Blocks.REDSTONE_BLOCK.defaultBlockState()
                );
            })
            .thenIdle(4)
            .thenExecute(() -> {
                assertZeroTickPistonResult(
                    level,
                    ordinaryPistonPos,
                    ordinaryCarriedPos,
                    ordinaryDestination,
                    ordinaryStand,
                    ordinaryStandStart,
                    "ordinary pulse generator"
                );
                assertZeroTickPistonResult(
                    level,
                    trayPistonPos,
                    trayCarriedPos,
                    trayDestination,
                    trayStand,
                    trayStandStart,
                    "tray pulse generator"
                );
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "23x12x15", floor = true)
    @TestHolder(description = "Off-grid tray ports accept every touched near and far cell and prefer the nearest input layer")
    static void offGridPortsUseTouchedCellsAndNearestInput(ExtendedGameTestHelper helper) {
        Vec3 offset = new Vec3(0.31D, 0.0D, -0.28D);
        UniversalPlasticEntity pulseTray = createTray(
            helper,
            new BlockPos(6, 5, 7),
            PlasticEntityOrientation.DEFAULT,
            pulseGenerator(0, 100, true),
            offset
        );
        List<List<BlockPos>> inputLayers = MoldedTrayRedstoneNetwork.inputCandidateLayers(
            pulseTray,
            Direction.NORTH
        );
        int inputY = MoldedTrayRedstoneNetwork.adjacentCell(pulseTray, Direction.NORTH).getY();
        List<BlockPos> nearInputs = inputLayers.getFirst().stream()
            .filter(position -> position.getY() == inputY)
            .toList();
        List<BlockPos> farInputs = inputLayers.get(1).stream()
            .filter(position -> position.getY() == inputY)
            .toList();
        check(nearInputs.size() == 2 && farInputs.size() == 2,
            "off-grid input face did not expose the expected four horizontal cells: " + inputLayers);

        List<BlockPos> picturedInputs = new ArrayList<>(nearInputs);
        picturedInputs.addAll(farInputs);
        PulseGeneratorBlockEntity pulse = (PulseGeneratorBlockEntity) pulseTray.plasticraft$getTrayBlockEntity();
        pulseTray.getMoldedTrayRuntime().tick();
        for (BlockPos input : picturedInputs) {
            helper.getLevel().setBlock(input, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            pulseTray.getMoldedTrayRuntime().tick();
            check(pulse.isInputtingSignal(), "off-grid pulse generator ignored input at " + input);
            helper.getLevel().setBlock(input, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            pulseTray.getMoldedTrayRuntime().tick();
            check(!pulse.isInputtingSignal(),
                "powered off-grid pulse generator fed its own output back into the input at " + input);
        }

        CompoundTag extraData = new CompoundTag();
        extraData.putByte("CompareMode", AdvancedComparatorBlockEntity.Mode.HYSTERESIS.index());
        extraData.putBoolean("OutputMode", false);
        extraData.putBoolean("RedstoneControl", false);
        extraData.putInt("HighLimit", 15);
        extraData.putInt("LowLimit", 0);
        CompoundTag comparatorData = new CompoundTag();
        comparatorData.put("ExtraData", extraData);
        MoldedTrayComponent comparator = new MoldedTrayComponent(
            new ItemStack(ModBlocks.ADVANCED_COMPARATOR.get()),
            ModBlocks.ADVANCED_COMPARATOR.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
            comparatorData,
            0,
            List.of()
        );
        UniversalPlasticEntity comparatorTray = createTray(
            helper,
            new BlockPos(16, 5, 7),
            PlasticEntityOrientation.DEFAULT,
            comparator,
            offset
        );
        List<BlockPos> closestLayer = MoldedTrayRedstoneNetwork.inputCandidateLayers(
            comparatorTray,
            Direction.NORTH
        ).getFirst();
        BlockPos closest = closestLayer.stream()
            .filter(position -> position.getY()
                == MoldedTrayRedstoneNetwork.adjacentCell(comparatorTray, Direction.NORTH).getY())
            .findFirst()
            .orElseThrow();
        BlockPos farther = closest.relative(Direction.NORTH);
        helper.getLevel().setBlock(farther, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        helper.getLevel().setBlock(
            closest,
            Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState()
                .setValue(BlockStateProperties.POWER, 4),
            Block.UPDATE_CLIENTS
        );
        comparatorTray.getMoldedTrayRuntime().tick();
        int selectedSignal = trayComponent(comparatorTray).state().getValue(AdvancedComparatorBlock.POWER);
        check(selectedSignal == 4,
            "farther strength-15 input overrode the closest strength-4 input: " + selectedSignal);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Weak-only signal sources activate mounted tray components without block-type exceptions")
    static void weakOnlySignalActivatesMountedRepeater(ExtendedGameTestHelper helper) {
        MoldedTrayComponent repeater = new MoldedTrayComponent(
            new ItemStack(Blocks.REPEATER),
            Blocks.REPEATER.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
            new CompoundTag(),
            0,
            List.of()
        );
        UniversalPlasticEntity tray = createTray(
            helper,
            new BlockPos(4, 3, 4),
            PlasticEntityOrientation.DEFAULT,
            repeater,
            Vec3.ZERO
        );
        ServerLevel level = helper.getLevel();
        BlockPos input = MoldedTrayRedstoneNetwork.adjacentCell(tray, Direction.NORTH);
        level.setBlockAndUpdate(input.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(input, ModBlocks.REDSTONE_WIRE.get().defaultBlockState());
        level.setBlockAndUpdate(input.north(), Blocks.REDSTONE_BLOCK.defaultBlockState());

        check(level.getSignal(input, Direction.NORTH) == 15,
            "AnvilCraft redstone wire did not expose its weak output to the tray");
        check(level.getDirectSignal(input, Direction.NORTH) == 0,
            "AnvilCraft redstone wire unexpectedly exposed direct strong power");
        tray.getMoldedTrayRuntime().tick();
        check(scheduledTicks(tray) == 2,
            "weak-only tray input did not schedule the mounted repeater");
        helper.runAfterDelay(3, () -> {
            check(trayComponent(tray).state().getValue(BlockStateProperties.POWERED),
                "weak-only tray input did not activate the mounted repeater");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "17x10x15", floor = true)
    @TestHolder(description = "Tray output uses one moving forward cell and powers only its nearest receiver")
    static void outputUsesMovingForwardCellAndNearestReceiver(ExtendedGameTestHelper helper) {
        BlockPos alignedOccupied = new BlockPos(4, 4, 4);
        UniversalPlasticEntity aligned = createTray(
            helper,
            alignedOccupied,
            PlasticEntityOrientation.DEFAULT,
            pulseGenerator(0, 100, true),
            Vec3.ZERO
        );
        aligned.getMoldedTrayRuntime().tick();
        BlockPos alignedReceiver = helper.absolutePos(alignedOccupied).south();
        AABB expectedRange = AABB.encapsulatingFullBlocks(alignedReceiver, alignedReceiver);
        check(sameBounds(MoldedTrayRedstoneNetwork.outputRange(aligned, Direction.SOUTH), expectedRange),
            "aligned tray output range was not its complete forward cell");

        BlockPos aboveRange = alignedReceiver.above();
        BlockPos besideRange = alignedReceiver.east();
        helper.getLevel().setBlockAndUpdate(aboveRange, Blocks.REDSTONE_LAMP.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(besideRange, Blocks.REDSTONE_LAMP.defaultBlockState());
        aligned.getMoldedTrayRuntime().tick();
        check(MoldedTrayRedstoneNetwork.outputSourceCells(aligned, Direction.SOUTH)
                .equals(List.of(alignedReceiver.north())),
            "aligned tray projected output outside its forward cell");
        check(!helper.getLevel().getBlockState(aboveRange).getValue(BlockStateProperties.LIT)
                && !helper.getLevel().getBlockState(besideRange).getValue(BlockStateProperties.LIT),
            "tray output powered a block that only touched the forward range boundary");

        helper.getLevel().setBlockAndUpdate(aboveRange, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(besideRange, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(alignedReceiver, Blocks.REDSTONE_LAMP.defaultBlockState());
        aligned.getMoldedTrayRuntime().tick();
        check(helper.getLevel().getBlockState(alignedReceiver).getValue(BlockStateProperties.LIT),
            "tray output did not power the block inside its forward cell");

        BlockPos movingOccupied = new BlockPos(11, 4, 8);
        BlockPos movingAnchor = helper.absolutePos(movingOccupied);
        UniversalPlasticEntity moving = createTray(
            helper,
            movingOccupied,
            PlasticEntityOrientation.DEFAULT,
            pulseGenerator(0, 100, true),
            new Vec3(0.31D, 0.17D, -0.28D)
        );
        moving.getMoldedTrayRuntime().tick();
        BlockPos nearest = movingAnchor.south();
        BlockPos farther = nearest.east();
        helper.getLevel().setBlockAndUpdate(movingAnchor, Blocks.WATER.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(nearest, Blocks.IRON_TRAPDOOR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(farther, Blocks.IRON_TRAPDOOR.defaultBlockState());
        moving.getMoldedTrayRuntime().tick();
        check(helper.getLevel().getBlockState(nearest).getValue(BlockStateProperties.OPEN),
            "off-grid tray output stopped at fluid before its nearest receiver");
        check(!helper.getLevel().getBlockState(farther).getValue(BlockStateProperties.OPEN),
            "off-grid tray output powered more than its nearest receiver");
        check(MoldedTrayRedstoneNetwork.outputSourceCells(moving, Direction.SOUTH)
                .equals(List.of(movingAnchor)),
            "off-grid tray selected the wrong nearest output source");

        AABB rangeBeforeMove = MoldedTrayRedstoneNetwork.outputRange(moving, Direction.SOUTH);
        moving.setPos(moving.position().add(1.0D, 0.0D, 0.0D));
        moving.getMoldedTrayRuntime().tick();
        check(sameBounds(
            MoldedTrayRedstoneNetwork.outputRange(moving, Direction.SOUTH),
            rangeBeforeMove.move(1.0D, 0.0D, 0.0D)
        ), "tray output range did not follow the moving entity");
        check(!helper.getLevel().getBlockState(nearest).getValue(BlockStateProperties.OPEN)
                && helper.getLevel().getBlockState(farther).getValue(BlockStateProperties.OPEN),
            "moving tray did not transfer output to its new nearest receiver");
        check(MoldedTrayRedstoneNetwork.outputSourceCells(moving, Direction.SOUTH)
                .equals(List.of(movingAnchor.east())),
            "moving tray retained its previous output source cell");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "11x8x11", floor = true)
    @TestHolder(description = "A tray's nearest output receiver keeps vanilla strong-power propagation")
    static void nearestOutputReceiverPropagatesStrongPower(ExtendedGameTestHelper helper) {
        BlockPos occupied = new BlockPos(4, 4, 3);
        UniversalPlasticEntity tray = createTray(
            helper,
            occupied,
            PlasticEntityOrientation.DEFAULT,
            pulseGenerator(0, 100, true),
            Vec3.ZERO
        );
        tray.getMoldedTrayRuntime().tick();

        BlockPos receiver = helper.absolutePos(occupied).south();
        BlockPos forwardLamp = receiver.south();
        BlockPos sideLamp = receiver.east();
        BlockPos upperLamp = receiver.above();
        helper.getLevel().setBlockAndUpdate(receiver, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(forwardLamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(sideLamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(upperLamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        tray.getMoldedTrayRuntime().tick();

        check(helper.getLevel().getDirectSignalTo(receiver) == 15,
            "tray output did not strongly power its nearest receiver");
        check(helper.getLevel().getBlockState(forwardLamp).getValue(BlockStateProperties.LIT)
                && helper.getLevel().getBlockState(sideLamp).getValue(BlockStateProperties.LIT)
                && helper.getLevel().getBlockState(upperLamp).getValue(BlockStateProperties.LIT),
            "strongly powered receiver did not activate its neighboring blocks");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "15x15x15", floor = true)
    @TestHolder(description = "A tray component input maps to every world direction across six-face orientations")
    static void redstoneInputsFollowSixWorldDirections(ExtendedGameTestHelper helper) {
        for (Direction worldInput : Direction.values()) {
            PlasticEntityOrientation orientation = orientationMapping(
                Direction.NORTH,
                worldInput
            );
            UniversalPlasticEntity tray = createTray(
                helper,
                new BlockPos(7, 7, 7),
                orientation,
                pulseGenerator(0, 4),
                new Vec3(0.21D, 0.17D, -0.18D)
            );
            tray.getMoldedTrayRuntime().tick();
            BlockPos input = MoldedTrayRedstoneNetwork.adjacentCell(tray, Direction.NORTH);
            helper.getLevel().setBlock(input, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            tray.getMoldedTrayRuntime().tick();
            check(tray.plasticraft$getTrayBlockEntity() instanceof PulseGeneratorBlockEntity pulse
                    && pulse.isInputtingSignal(),
                "tray pulse input did not map local north to world " + worldInput);
            helper.getLevel().setBlock(input, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            tray.getMoldedTrayRuntime().remove();
            tray.discard();
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "15x15x15", floor = true)
    @TestHolder(description = "Off-grid tray redstone ports follow every attachment direction")
    static void redstonePortsFollowSixDirections(ExtendedGameTestHelper helper) {
        MoldedTrayComponent torch = new MoldedTrayComponent(
            new ItemStack(Blocks.REDSTONE_TORCH),
            Blocks.REDSTONE_TORCH.defaultBlockState().setValue(BlockStateProperties.LIT, true),
            new CompoundTag(),
            0,
            List.of()
        );
        Vec3 offset = new Vec3(0.21D, 0.17D, -0.18D);
        for (Direction attachment : Direction.values()) {
            PlasticEntityOrientation orientation = new PlasticEntityOrientation(attachment, 1);
            UniversalPlasticEntity tray = createTray(
                helper,
                new BlockPos(7, 7, 7),
                orientation,
                torch,
                offset
            );
            tray.getMoldedTrayRuntime().tick();
            for (Direction localQuery : Direction.values()) {
                Direction worldQuery = orientation.worldDirection(localQuery);
                List<BlockPos> sources = MoldedTrayRedstoneNetwork.outputSourceCells(
                    tray,
                    localQuery.getOpposite()
                );
                int expected = localQuery == Direction.UP ? 0 : 15;
                check(sources.size() == 1, "tray output face did not select exactly one source cell");
                for (BlockPos source : sources) {
                    int actual = MoldedTrayRedstoneNetwork.weakSignal(
                        helper.getLevel(),
                        source,
                        worldQuery
                    );
                    check(actual == expected,
                        "wrong torch signal for attachment " + attachment + " and local query " + localQuery
                            + " at " + source + ": " + actual);
                }
            }
            tray.getMoldedTrayRuntime().remove();
            tray.discard();
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "13x8x9", floor = true)
    @TestHolder(description = "Mounted component shapes extend dynamic and bonded tray collision and interaction")
    static void mountedComponentShapesJoinTrayGeometry(ExtendedGameTestHelper helper) {
        MoldedTrayComponent repeater = new MoldedTrayComponent(
            new ItemStack(Blocks.REPEATER),
            Blocks.REPEATER.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
            new CompoundTag(),
            0,
            List.of()
        );
        UniversalPlasticEntity selectable = createTray(
            helper,
            new BlockPos(3, 3, 4),
            PlasticEntityOrientation.DEFAULT,
            repeater,
            Vec3.ZERO
        );
        double plasticTop = selectable.getY() + 4.0D / 16.0D;
        check(selectable.getBoundingBox().maxY > plasticTop + 0.01D,
            "mounted repeater did not extend the tray entity bounds");
        check(selectable.plasticraft$getInteractionShape().bounds().maxY > plasticTop + 0.01D,
            "mounted repeater outline was absent from tray interaction geometry");

        MoldedTrayComponent advancedComparator = new MoldedTrayComponent(
            new ItemStack(ModBlocks.ADVANCED_COMPARATOR.get()),
            ModBlocks.ADVANCED_COMPARATOR.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
            new CompoundTag(),
            0,
            List.of()
        );
        BlockPos occupied = new BlockPos(9, 3, 4);
        UniversalPlasticEntity collidable = createTray(
            helper,
            occupied,
            PlasticEntityOrientation.DEFAULT,
            advancedComparator,
            Vec3.ZERO
        );
        check(collidable.plasticraft$getCollisionBox().bounds().maxY > collidable.getY() + 1.0D,
            "mounted advanced comparator did not extend dynamic tray collision");

        BlockState displayState = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState();
        helper.setBlock(occupied, displayState.setValue(AbstractPlasticEntityBlock.BONDED, true));
        check(helper.getBlockEntity(occupied) instanceof BondedEntityBlockEntity,
            "bonded tray test did not create bonded storage");
        BondedEntityBlockEntity bonded = (BondedEntityBlockEntity) helper.getBlockEntity(occupied);
        check(bonded.initialize(
            collidable,
            displayState,
            Direction.UP,
            PlasticEntityOrientation.DEFAULT,
            true
        ), "bonded tray test could not capture its entity");
        collidable.discard();
        BlockPos absolute = helper.absolutePos(occupied);
        VoxelShape bondedCollision = helper.getBlockState(occupied).getCollisionShape(helper.getLevel(), absolute);
        check(bondedCollision.bounds().maxY > 1.0D,
            "mounted component collision was absent after the tray became bonded");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x8x9", floor = true)
    @TestHolder(description = "Tray buttons, levers, and daylight detectors retain interaction and directional output")
    static void manualComponentsRetainInteractionAndPorts(ExtendedGameTestHelper helper) {
        PlasticEntityOrientation orientation = new PlasticEntityOrientation(Direction.EAST, 1);
        MoldedTrayComponent placeholder = new MoldedTrayComponent(
            new ItemStack(Blocks.LEVER),
            Blocks.LEVER.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
            new CompoundTag(),
            0,
            List.of()
        );
        UniversalPlasticEntity tray = createTray(
            helper,
            new BlockPos(4, 3, 4),
            orientation,
            placeholder,
            Vec3.ZERO
        );
        tray.plasticraft$clearTrayComponent();
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.STONE_BUTTON));
        check(tray.interact(player, InteractionHand.MAIN_HAND).consumesAction(),
            "tray rejected a tagged button item");
        BlockState mounted = trayComponent(tray).state();
        check(mounted.getValue(BlockStateProperties.ATTACH_FACE) == AttachFace.FLOOR
                && !mounted.getValue(BlockStateProperties.POWERED),
            "mounted button was not initialized on the tray top in its released state");

        check(tray.getMoldedTrayRuntime().interact(player, InteractionHand.MAIN_HAND).consumesAction(),
            "mounted button rejected its press interaction");
        for (Direction localQuery : Direction.values()) {
            Direction worldQuery = orientation.worldDirection(localQuery);
            int expectedDirect = localQuery == Direction.UP ? 15 : 0;
            for (BlockPos source : MoldedTrayRedstoneNetwork.outputSourceCells(
                tray,
                localQuery.getOpposite()
            )) {
                check(MoldedTrayRedstoneNetwork.weakSignal(helper.getLevel(), source, worldQuery) == 15,
                    "pressed button lost weak output toward " + localQuery + " at " + source);
                check(MoldedTrayRedstoneNetwork.directSignal(
                    helper.getLevel(),
                    source,
                    worldQuery
                ) == expectedDirect, "pressed button had wrong direct output toward " + localQuery
                    + " at " + source);
            }
        }
        for (int tick = 0; tick < 20; tick++) tray.getMoldedTrayRuntime().tick();
        check(!trayComponent(tray).state().getValue(BlockStateProperties.POWERED),
            "stone-duration tray button stayed pressed after twenty ticks");

        tray.plasticraft$replaceTrayComponent(placeholder);
        check(tray.getMoldedTrayRuntime().interact(player, InteractionHand.MAIN_HAND).consumesAction(),
            "mounted lever rejected its pull interaction");
        check(trayComponent(tray).state().getValue(BlockStateProperties.POWERED),
            "mounted lever did not retain its powered state");

        MoldedTrayComponent daylight = new MoldedTrayComponent(
            new ItemStack(Blocks.DAYLIGHT_DETECTOR),
            Blocks.DAYLIGHT_DETECTOR.defaultBlockState(),
            new CompoundTag(),
            0,
            List.of()
        );
        tray.plasticraft$replaceTrayComponent(daylight);
        check(tray.getMoldedTrayRuntime().interact(player, InteractionHand.MAIN_HAND).consumesAction(),
            "mounted daylight detector rejected inversion");
        check(trayComponent(tray).state().getValue(BlockStateProperties.INVERTED),
            "mounted daylight detector did not retain its inverted state");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "13x8x9", floor = true)
    @TestHolder(description = "Vanilla and AnvilCraft tray pressure plates detect entities on their oriented surface")
    static void pressurePlatesUseMountedSurface(ExtendedGameTestHelper helper) {
        MoldedTrayComponent stonePlate = new MoldedTrayComponent(
            new ItemStack(Blocks.STONE_PRESSURE_PLATE),
            Blocks.STONE_PRESSURE_PLATE.defaultBlockState(),
            new CompoundTag(),
            0,
            List.of()
        );
        UniversalPlasticEntity tray = createTray(
            helper,
            new BlockPos(3, 3, 4),
            PlasticEntityOrientation.DEFAULT,
            stonePlate,
            Vec3.ZERO
        );
        ArmorStand stand = pressurePlateEntity(helper, tray);
        tray.getMoldedTrayRuntime().tick();
        check(trayComponent(tray).state().getValue(BlockStateProperties.POWERED),
            "mounted stone pressure plate ignored a living entity on its surface");
        stand.discard();
        for (int tick = 0; tick < 20; tick++) tray.getMoldedTrayRuntime().tick();
        check(!trayComponent(tray).state().getValue(BlockStateProperties.POWERED),
            "mounted stone pressure plate did not release after its normal delay");

        CompoundTag copperData = new CompoundTag();
        copperData.putInt("tick", 0);
        copperData.putInt("NeedTick", 10);
        MoldedTrayComponent copperPlate = new MoldedTrayComponent(
            new ItemStack(ModBlocks.COPPER_PRESSURE_PLATE.get()),
            ModBlocks.COPPER_PRESSURE_PLATE.get().defaultBlockState(),
            copperData,
            0,
            List.of()
        );
        tray.plasticraft$replaceTrayComponent(copperPlate);
        pressurePlateEntity(helper, tray);
        for (int tick = 0; tick < 10; tick++) tray.getMoldedTrayRuntime().tick();
        check(trayComponent(tray).state().getValue(BlockStateProperties.POWER) == 1,
            "mounted AnvilCraft copper pressure plate lost its accumulated output algorithm");

        MoldedTrayCell outerCell = new MoldedTrayCell(0, 2);
        MoldedPlasticData wideData = manufactureTray(trayModel(List.of(
            cube("Full support", 0, 0, 0, 48, 4, 48)
        ))).withContents(new MoldedPlasticContents(
            List.of(),
            List.of(),
            List.of(new MoldedTrayComponentPlacement(outerCell, stonePlate))
        ));
        UniversalPlasticEntity wideTray = createTray(
            helper,
            new BlockPos(9, 3, 4),
            PlasticEntityOrientation.DEFAULT,
            Vec3.ZERO,
            wideData
        );
        pressurePlateEntity(helper, wideTray, outerCell);
        wideTray.getMoldedTrayRuntime(outerCell).tick();
        check(wideTray.getMoldedData()
                .flatMap(value -> value.contents().trayComponent(outerCell))
                .orElseThrow()
                .state()
                .getValue(BlockStateProperties.POWERED),
            "off-center mounted pressure plate ignored an entity on its own cell");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x8x9", floor = true)
    @TestHolder(description = "A tray advanced comparator retains thresholds, input, output, and virtual lookup")
    static void advancedComparatorRetainsBehavior(ExtendedGameTestHelper helper) {
        CompoundTag extraData = new CompoundTag();
        extraData.putByte("CompareMode", AdvancedComparatorBlockEntity.Mode.HYSTERESIS.index());
        extraData.putBoolean("OutputMode", false);
        extraData.putBoolean("RedstoneControl", false);
        extraData.putInt("HighLimit", 10);
        extraData.putInt("LowLimit", 5);
        CompoundTag comparatorData = new CompoundTag();
        comparatorData.put("ExtraData", extraData);
        MoldedTrayComponent comparator = new MoldedTrayComponent(
            new ItemStack(ModBlocks.ADVANCED_COMPARATOR.get()),
            ModBlocks.ADVANCED_COMPARATOR.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
            comparatorData,
            0,
            List.of()
        );
        UniversalPlasticEntity tray = createTray(
            helper,
            new BlockPos(4, 3, 4),
            PlasticEntityOrientation.DEFAULT,
            comparator,
            Vec3.ZERO
        );
        BlockPos input = MoldedTrayRedstoneNetwork.adjacentCell(tray, Direction.NORTH);
        helper.getLevel().setBlockAndUpdate(input, Blocks.REDSTONE_BLOCK.defaultBlockState());
        tray.getMoldedTrayRuntime().tick();
        check(trayComponent(tray).state().getValue(BlockStateProperties.POWERED),
            "mounted advanced comparator ignored its front input");
        BlockPos output = MoldedTrayRedstoneNetwork.componentCell(tray, Direction.SOUTH);
        check(MoldedTrayRedstoneNetwork.weakSignal(helper.getLevel(), output, Direction.NORTH) == 15,
            "mounted advanced comparator did not publish its directional output");
        BlockEntity expectedBlockEntity = tray.plasticraft$getTrayBlockEntity();
        tray.setPos(tray.position().add(1.1D, 0.0D, 0.0D));
        BlockEntity blockEntity = MoldedTrayComponentLookup.find(
            helper.getLevel(),
            MoldedTrayRedstoneNetwork.componentPosition(tray, MoldedTrayCell.CENTER),
            MoldedTrayComponentLookup.Kind.ADVANCED_COMPARATOR
        );
        check(blockEntity == expectedBlockEntity && blockEntity instanceof AdvancedComparatorBlockEntity,
            "moving a tray made its advanced comparator unavailable to the menu lookup");

        ItemStack extracted = MoldedTrayComponentSupport.extractionStack(
            trayComponent(tray),
            blockEntity,
            helper.getLevel()
        );
        CompoundTag extractedData = extracted.getOrDefault(
            DataComponents.BLOCK_ENTITY_DATA,
            CustomData.EMPTY
        ).copyTag().getCompound("ExtraData");
        check(extractedData.getInt("HighLimit") == 10 && extractedData.getInt("LowLimit") == 5,
            "advanced comparator extraction lost its configured thresholds");
        check(!extractedData.contains("InputSignal"),
            "advanced comparator extraction leaked its transient input signal");

        helper.getLevel().setBlockAndUpdate(input, Blocks.AIR.defaultBlockState());
        tray.getMoldedTrayRuntime().tick();
        check(!trayComponent(tray).state().getValue(BlockStateProperties.POWERED),
            "mounted advanced comparator ignored its low threshold after input removal");
        helper.succeed();
    }

    private static void checkValid(EditableMoldingModel model, String message) {
        var baked = MoldingModelBaker.bake(model);
        MoldingTypeValidation validation = MoldingProductTypes.validate(
            MoldingProductTypes.TRAY_ID,
            model,
            baked
        );
        check(validation.valid(), message + ": " + validation);
    }

    private static void checkReason(EditableMoldingModel model, String reason) {
        var baked = MoldingModelBaker.bake(model);
        MoldingTypeValidation validation = MoldingProductTypes.validate(
            MoldingProductTypes.TRAY_ID,
            model,
            baked
        );
        check(!validation.valid() && reason.equals(validation.reason()),
            "expected " + reason + " but received " + validation);
    }

    private static List<MoldingElement> trayWithHole() {
        return List.of(
            cube("North", 16, 0, 16, 32, 4, 18),
            cube("South", 16, 0, 19, 32, 4, 32),
            cube("West", 16, 0, 18, 18, 4, 19),
            cube("East", 19, 0, 18, 32, 4, 19)
        );
    }

    private static EditableMoldingModel trayModel(List<MoldingElement> elements) {
        return new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Tray regression model",
            MoldingProductTypes.TRAY_ID,
            elements,
            List.of()
        );
    }

    private static MoldingElement cube(
        String name,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
    ) {
        return MoldingElement.cube(name, vec(minX, minY, minZ), vec(maxX, maxY, maxZ));
    }

    private static MoldingElement transformedCube(
        String name,
        MoldingVec3 from,
        MoldingVec3 to,
        MoldingVec3 rotation
    ) {
        MoldingElement element = MoldingElement.cube(name, from, to);
        return new MoldingElement(
            element.id(),
            element.name(),
            element.groupId(),
            element.from(),
            element.to(),
            new MoldingTransform(
                MoldingVec3.ZERO,
                rotation,
                MoldingVec3.ONE,
                from.add(to).scale(0.5D)
            ),
            element.visible(),
            element.locked()
        );
    }

    private static UniversalPlasticEntity createTray(
        ExtendedGameTestHelper helper,
        BlockPos occupiedPos,
        PlasticEntityOrientation orientation,
        MoldedTrayComponent component,
        Vec3 offset
    ) {
        return createTray(helper, occupiedPos, orientation, component, offset, manufactureTray());
    }

    private static UniversalPlasticEntity createTray(
        ExtendedGameTestHelper helper,
        BlockPos occupiedPos,
        PlasticEntityOrientation orientation,
        MoldedTrayComponent component,
        Vec3 offset,
        MoldedPlasticData manufactured
    ) {
        MoldedPlasticData data = manufactured.withContents(
            MoldedPlasticContents.EMPTY.withTrayComponent(Optional.of(component))
        );
        return createTray(helper, occupiedPos, orientation, offset, data);
    }

    private static UniversalPlasticEntity createTray(
        ExtendedGameTestHelper helper,
        BlockPos occupiedPos,
        PlasticEntityOrientation orientation,
        Vec3 offset,
        MoldedPlasticData data
    ) {
        ItemStack stack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(stack, data);
        UniversalPlasticEntity tray = new UniversalPlasticEntity(
            PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
            helper.getLevel(),
            Vec3.ZERO,
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            stack,
            orientation
        );
        tray.setPos(tray.plasticraft$placementPosition(helper.absolutePos(occupiedPos), orientation).add(offset));
        tray.setStartPos(tray.blockPosition());
        tray.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(tray), "failed to add molded tray entity");
        return tray;
    }

    private static MoldedPlasticData manufactureTray() {
        return manufactureTray(trayModel(List.of(cube("Body", 16, 0, 16, 32, 4, 32))));
    }

    private static MoldedPlasticData manufactureTray(EditableMoldingModel model) {
        var baked = MoldingModelBaker.bake(model);
        int melt = baked.analysis().minimumMeltMillibuckets();
        MoldedPlasticData data = MoldedPlasticData.manufacture(
            model,
            baked,
            new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), melt),
            melt
        );
        check(MoldingProductTypes.TRAY_ID.equals(data.finalType()), "manufactured tray lost its type");
        return data;
    }

    private static ArmorStand pressurePlateEntity(
        ExtendedGameTestHelper helper,
        UniversalPlasticEntity tray
    ) {
        return pressurePlateEntity(helper, tray, MoldedTrayCell.CENTER);
    }

    private static ArmorStand pressurePlateEntity(
        ExtendedGameTestHelper helper,
        UniversalPlasticEntity tray,
        MoldedTrayCell cell
    ) {
        MoldedPlasticData data = tray.getMoldedData().orElseThrow();
        AABB bounds = MoldedTrayComponentGeometry.localBounds(data, cell);
        Vec3 center = bounds.getCenter();
        Vec3 position = tray.plasticraft$getGeometry().worldPointAt(
            tray.position(),
            tray.getOrientation(),
            new Vec3(center.x, bounds.minY + 1.0D / 64.0D, center.z)
        );
        ArmorStand stand = new ArmorStand(
            helper.getLevel(),
            position.x,
            position.y,
            position.z
        );
        stand.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(stand), "failed to add pressure plate test entity");
        return stand;
    }

    private static ArmorStand stationaryArmorStand(
        ExtendedGameTestHelper helper,
        BlockPos position
    ) {
        ArmorStand stand = new ArmorStand(
            helper.getLevel(),
            position.getX() + 0.5D,
            position.getY(),
            position.getZ() + 0.5D
        );
        stand.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(stand), "failed to add zero-tick piston armor stand");
        return stand;
    }

    private static void assertZeroTickPistonResult(
        ServerLevel level,
        BlockPos pistonPosition,
        BlockPos carriedPosition,
        BlockPos destination,
        ArmorStand stand,
        Vec3 standStart,
        String fixture
    ) {
        check(!level.getBlockState(pistonPosition).getValue(BlockStateProperties.EXTENDED),
            fixture + " left its sticky piston extended");
        check(level.getBlockState(carriedPosition).isAir(),
            fixture + " pulled the zero-tick block back instead of spitting it");
        check(level.getBlockState(destination).is(Blocks.GLASS),
            fixture + " did not move the glass into the armor stand cell");
        check(stand.position().distanceToSqr(standStart) < 1.0E-10D,
            fixture + " moved the armor stand during its zero-tick pulse");
    }

    private static ItemEntity spawnItem(ExtendedGameTestHelper helper, BlockPos position) {
        ItemEntity entity = new ItemEntity(
            helper.getLevel(),
            position.getX() + 0.5D,
            position.getY() + 0.5D,
            position.getZ() + 0.5D,
            new ItemStack(Items.DIAMOND)
        );
        entity.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(entity), "failed to add detector test item");
        return entity;
    }

    private static ItemStack findInventoryItem(Player player, Block block) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(block.asItem())) return stack;
        }
        throw new GameTestAssertException("extracted tray component was not returned to the player");
    }

    private static int detectorOutput(UniversalPlasticEntity tray) {
        return tray.getMoldedData()
            .flatMap(data -> data.contents().trayComponent())
            .map(component -> component.blockEntityData().getInt("OutputSignal"))
            .orElseThrow();
    }

    private static MoldedTrayComponent pulseGenerator(int waitingTime, int signalDuration) {
        return pulseGenerator(waitingTime, signalDuration, false);
    }

    private static MoldedTrayComponent pulseGenerator(
        int waitingTime,
        int signalDuration,
        boolean outputInvert
    ) {
        CompoundTag extraData = new CompoundTag();
        extraData.putByte("StartMode", PulseGeneratorBlockEntity.Mode.RISING_EDGE.index());
        extraData.putBoolean("OutputMode", outputInvert);
        extraData.putBoolean("Inputting", false);
        extraData.putInt("WaitingTime", waitingTime);
        extraData.putInt("SignalDuration", signalDuration);
        CompoundTag blockEntityData = new CompoundTag();
        blockEntityData.put("ExtraData", extraData);
        return new MoldedTrayComponent(
            new ItemStack(ModBlocks.PULSE_GENERATOR.get()),
            ModBlocks.PULSE_GENERATOR.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.POWERED, false),
            blockEntityData,
            0,
            List.of()
        );
    }

    private static MoldedTrayComponent trayComponent(UniversalPlasticEntity tray) {
        return tray.getMoldedData()
            .flatMap(data -> data.contents().trayComponent())
            .orElseThrow();
    }

    private static int scheduledTicks(UniversalPlasticEntity tray) {
        return trayComponent(tray).scheduledTicks();
    }

    private static boolean sameBounds(AABB first, AABB second) {
        return Math.abs(first.minX - second.minX) < 1.0E-7D
            && Math.abs(first.minY - second.minY) < 1.0E-7D
            && Math.abs(first.minZ - second.minZ) < 1.0E-7D
            && Math.abs(first.maxX - second.maxX) < 1.0E-7D
            && Math.abs(first.maxY - second.maxY) < 1.0E-7D
            && Math.abs(first.maxZ - second.maxZ) < 1.0E-7D;
    }

    private static PlasticEntityOrientation orientationMapping(
        Direction localDirection,
        Direction worldDirection
    ) {
        for (Direction attachment : Direction.values()) {
            for (int turn = 0; turn < 4; turn++) {
                PlasticEntityOrientation orientation = new PlasticEntityOrientation(attachment, turn);
                if (orientation.worldDirection(localDirection) == worldDirection) return orientation;
            }
        }
        throw new GameTestAssertException(
            "no plastic orientation maps " + localDirection + " to " + worldDirection
        );
    }

    private static MoldingVec3 vec(double x, double y, double z) {
        return new MoldingVec3(x, y, z);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
