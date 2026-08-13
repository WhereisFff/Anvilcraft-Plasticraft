package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.MoldingRegionPart;
import dev.anvilcraft.plasticraft.block.Plastic3DPrintingComponentBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberStructure;
import dev.anvilcraft.plasticraft.block.PlasticMoldingMachineState;
import dev.anvilcraft.plasticraft.block.PlasticMoldingRegionBlock;
import dev.anvilcraft.plasticraft.block.entity.Plastic3DPrintingComponentBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockEntities;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.inventory.PlasticMoldingChamberMenu;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.item.PlasticMoldingChamberItem;
import dev.anvilcraft.plasticraft.molding.blueprint.BlueprintException;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprint;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintCodec;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintDisk;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintImporter;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintLibrary;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintService;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintSummary;
import dev.anvilcraft.plasticraft.molding.bake.MoldingConvexFace;
import dev.anvilcraft.plasticraft.molding.bake.MoldingConvexHull;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.bake.MoldingPreparedHull;
import dev.anvilcraft.plasticraft.molding.bake.MoldingQuad;
import dev.anvilcraft.plasticraft.molding.bake.MoldingVolumeMask;
import dev.anvilcraft.plasticraft.molding.machine.MoldingFormingMode;
import dev.anvilcraft.plasticraft.molding.machine.MoldingMachineAction;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPowerBridge;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPrintedGeometry;
import dev.anvilcraft.plasticraft.molding.machine.MoldingProcessSnapshot;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPrinterMotion;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPrintingPlan;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPrintingVoxel;
import dev.anvilcraft.plasticraft.molding.machine.MoldingProductionMode;
import dev.anvilcraft.plasticraft.molding.machine.MoldingWaitReason;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingCommand;
import dev.anvilcraft.plasticraft.molding.model.MoldingCoordinateSystem;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelBounds;
import dev.anvilcraft.plasticraft.molding.model.MoldingTransform;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import dev.anvilcraft.plasticraft.molding.session.MoldingSessionSnapshot;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.item.property.component.StructureDiskData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** TODO-01 成型舱空间结构、原子占用和编辑租约回归测试。 */
public final class PlasticMoldingChamberGameTests {
    private static final double EPSILON = 1.0E-8D;

    private PlasticMoldingChamberGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "15x7x15", floor = true)
    @TestHolder(description = "All four chamber facings share the 27-part anchor and collisionless projection mapping")
    static void fourFacingStructureAndProjectionAnchors(ExtendedGameTestHelper helper) {
        List<Placement> placements = List.of(
            new Placement(new BlockPos(3, 2, 3), Direction.NORTH),
            new Placement(new BlockPos(11, 2, 3), Direction.EAST),
            new Placement(new BlockPos(3, 2, 11), Direction.SOUTH),
            new Placement(new BlockPos(11, 2, 11), Direction.WEST)
        );
        for (Placement placement : placements) {
            BlockPos controller = helper.absolutePos(placement.relativeController());
            BlockState controllerState = PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.get().defaultBlockState()
                .setValue(PlasticMoldingChamberBlock.FACING, placement.front());
            check(
                PlasticMoldingChamberStructure.placeAtomically(helper.getLevel(), controller, controllerState),
                "chamber placement failed for " + placement.front()
            );
            check(
                helper.getLevel().getBlockEntity(controller) instanceof PlasticMoldingChamberBlockEntity,
                "chamber block entity was missing for " + placement.front()
            );
            Set<BlockPos> unique = new HashSet<>();
            for (MoldingRegionPart part : MoldingRegionPart.values()) {
                BlockPos region = PlasticMoldingChamberStructure.regionPos(controller, placement.front(), part);
                check(unique.add(region), "duplicate region coordinate for " + placement.front());
                BlockState state = helper.getLevel().getBlockState(region);
                check(state.is(PlasticraftBlocks.PLASTIC_MOLDING_REGION.get()), "region part was missing at " + region);
                check(state.getValue(PlasticMoldingRegionBlock.FACING) == placement.front(), "region facing drifted");
                check(state.getValue(PlasticMoldingRegionBlock.PART) == part, "region part index drifted");
                check(
                    state.getCollisionShape(helper.getLevel(), region).isEmpty(),
                    "editable region had physical collision"
                );
                check(
                    state.getShape(helper.getLevel(), region).isEmpty(),
                    "editable region still exposed a selection shape"
                );
                check(
                    PlasticMoldingChamberStructure.controllerPos(region, placement.front(), part).equals(controller),
                    "region-to-controller inverse mapping failed"
                );

                Vec3 projectedCenter = PlasticMoldingChamberStructure.regionProjectionOffset(
                    placement.front(),
                    (part.right() + 1.5D) * 16.0D,
                    (part.up() + 0.5D) * 16.0D,
                    (part.depth() - 0.5D) * 16.0D
                ).add(Vec3.atLowerCornerOf(controller));
                check(
                    projectedCenter.distanceTo(region.getCenter()) <= EPSILON,
                    "projection center did not match region anchor for " + part
                );
            }
            check(unique.size() == PlasticMoldingChamberStructure.PART_COUNT, "region did not contain 27 unique parts");
            Vec3 origin = PlasticMoldingChamberStructure.regionProjectionOffset(
                placement.front(),
                16.0D,
                16.0D,
                16.0D
            );
            Vec3 positiveX = PlasticMoldingChamberStructure.regionProjectionOffset(
                placement.front(),
                32.0D,
                16.0D,
                16.0D
            );
            Vec3 positiveZ = PlasticMoldingChamberStructure.regionProjectionOffset(
                placement.front(),
                16.0D,
                16.0D,
                32.0D
            );
            Direction right = PlasticMoldingChamberStructure.right(placement.front());
            Direction back = PlasticMoldingChamberStructure.back(placement.front());
            check(
                positiveX.subtract(origin).distanceTo(Vec3.atLowerCornerOf(right.getNormal())) <= EPSILON,
                "local +X was mirrored away from the chamber right side"
            );
            check(
                positiveZ.subtract(origin).distanceTo(Vec3.atLowerCornerOf(back.getNormal())) <= EPSILON,
                "local +Z was mirrored away from the chamber back side"
            );
        }

        for (Placement placement : placements) {
            BlockPos controller = helper.absolutePos(placement.relativeController());
            List<BlockPos> regions = PlasticMoldingChamberStructure.regionPositions(controller, placement.front());
            check(helper.getLevel().destroyBlock(controller, false), "controller could not be removed");
            for (BlockPos region : regions) {
                check(helper.getLevel().getBlockState(region).isAir(), "controller removal left a region part");
            }
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "11x7x11", floor = true)
    @TestHolder(description = "Obstructed chamber placement leaves no controller or partial region")
    static void obstructedPlacementIsAtomic(ExtendedGameTestHelper helper) {
        BlockPos controller = helper.absolutePos(new BlockPos(5, 2, 3));
        Direction front = Direction.NORTH;
        MoldingRegionPart obstructionPart = MoldingRegionPart.D2_R1_U1;
        BlockPos obstruction = PlasticMoldingChamberStructure.regionPos(controller, front, obstructionPart);
        helper.getLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
        BlockState state = PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.get().defaultBlockState()
            .setValue(PlasticMoldingChamberBlock.FACING, front);

        check(
            !PlasticMoldingChamberStructure.placeAtomically(helper.getLevel(), controller, state),
            "obstructed chamber placement succeeded"
        );
        check(helper.getLevel().getBlockState(controller).isAir(), "failed placement left the controller");
        for (MoldingRegionPart part : MoldingRegionPart.values()) {
            BlockPos region = PlasticMoldingChamberStructure.regionPos(controller, front, part);
            if (part == obstructionPart) {
                check(helper.getLevel().getBlockState(region).is(Blocks.STONE), "failed placement replaced the obstruction");
            } else {
                check(helper.getLevel().getBlockState(region).isAir(), "failed placement left a partial region");
            }
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Single-writer sessions reject stale revisions, cap history at ten, and invalidate takeover victims")
    static void authoritativeSessionAndHistory(ExtendedGameTestHelper helper) {
        BlockPos controller = helper.absolutePos(new BlockPos(4, 2, 2));
        BlockState state = PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.get().defaultBlockState()
            .setValue(PlasticMoldingChamberBlock.FACING, Direction.NORTH);
        check(
            PlasticMoldingChamberStructure.placeAtomically(helper.getLevel(), controller, state),
            "session test chamber placement failed"
        );
        PlasticMoldingChamberBlockEntity chamber = helper.getLevel().getBlockEntity(controller)
            instanceof PlasticMoldingChamberBlockEntity result
            ? result
            : null;
        if (chamber == null) throw new GameTestAssertException("session test chamber block entity was missing");

        ServerPlayer firstPlayer = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        firstPlayer.setPos(controller.getCenter());
        MoldingSessionSnapshot firstSession = chamber.openSession(firstPlayer, false);
        check(firstSession.writable(), "first chamber session was not writable");

        for (int index = 0; index < 12; index++) {
            MoldingCommand command = new MoldingCommand.AddElement(MoldingElement.cube(
                "Cube " + index,
                new MoldingVec3(20.0D, 20.0D, 20.0D),
                new MoldingVec3(28.0D, 28.0D, 28.0D)
            ));
            PlasticMoldingChamberBlockEntity.EditOutcome outcome = chamber.applyEditorCommand(
                firstPlayer,
                firstSession.sessionId(),
                chamber.revision(),
                command
            );
            check(outcome.accepted(), "valid edit command was rejected");
        }
        check(chamber.menuData().get(12) == 1, "menu did not expose available undo history");
        PlasticMoldingChamberBlockEntity.EditOutcome stale = chamber.applyEditorCommand(
            firstPlayer,
            firstSession.sessionId(),
            0L,
            new MoldingCommand.Undo()
        );
        check(!stale.accepted() && stale.reason().equals("stale_revision"), "stale command was not rejected");

        for (int index = 0; index < PlasticMoldingChamberBlockEntity.HISTORY_LIMIT; index++) {
            PlasticMoldingChamberBlockEntity.EditOutcome undo = chamber.applyEditorCommand(
                firstPlayer,
                firstSession.sessionId(),
                chamber.revision(),
                new MoldingCommand.Undo()
            );
            check(undo.accepted(), "one of the retained ten undo steps was rejected");
        }
        check(chamber.menuData().get(12) == 0, "menu kept undo enabled after history was exhausted");
        PlasticMoldingChamberBlockEntity.EditOutcome exhausted = chamber.applyEditorCommand(
            firstPlayer,
            firstSession.sessionId(),
            chamber.revision(),
            new MoldingCommand.Undo()
        );
        check(!exhausted.accepted() && exhausted.reason().equals("nothing_to_undo"), "undo history exceeded ten steps");
        PlasticMoldingChamberBlockEntity.EditOutcome redo = chamber.applyEditorCommand(
            firstPlayer,
            firstSession.sessionId(),
            chamber.revision(),
            new MoldingCommand.Redo()
        );
        check(redo.accepted(), "redo did not restore a retained step");
        check(chamber.menuData().get(12) == 1, "menu did not re-enable undo after redo");

        ServerPlayer secondPlayer = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        secondPlayer.setPos(controller.getCenter());
        MoldingSessionSnapshot observer = chamber.openSession(secondPlayer, false);
        check(!observer.writable(), "second simultaneous session was incorrectly writable");
        MoldingSessionSnapshot takeover = chamber.openSession(secondPlayer, true);
        check(takeover.writable(), "explicit takeover did not acquire the lease");
        check(!chamber.heartbeat(firstPlayer, firstSession.sessionId()), "takeover left the previous writer valid");
        check(chamber.heartbeat(secondPlayer, takeover.sessionId()), "takeover writer could not renew the lease");
        firstPlayer.discard();
        secondPlayer.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Clay filling settles three complete layers across refills and pumps exactly 250 mB per tick")
    static void clayRefillAndFixedRatePump(ExtendedGameTestHelper helper) {
        PlasticMoldingChamberBlockEntity chamber = placeChamber(helper, new BlockPos(4, 2, 2));
        EditableMoldingModel model = cubeModel(20.0D);
        check(chamber.replaceEditableModel(model, chamber.revision()).accepted(), "production model was rejected");
        check(chamber.requiredClayBalls() == 0, "editable chamber retained a clay reservation");
        check(chamber.clayLimit() == 256, "clay limit did not default to 256");
        check(chamber.setClayLimit(64, chamber.revision()).accepted(), "clay refill limit was rejected");
        check(chamber.energyStorage().receiveEnergy(MoldingPowerBridge.capacity(), false)
            == MoldingPowerBridge.capacity(), "energy buffer did not accept one super-capacitor charge");
        chamber.inventory().setItem(
            PlasticMoldingChamberBlockEntity.CLAY_SLOT,
            new ItemStack(Items.CLAY_BALL, 64)
        );
        check(chamber.requestLock().accepted(), "valid model did not lock");

        tick(chamber, 8);
        check(chamber.machineState() == PlasticMoldingMachineState.MOLD_FILLING,
            "clay animation did not start");
        check(chamber.moldFillProgress() == 7 && chamber.moldedClayBalls() == 34,
            "clay animation did not pause before the second complete layer");
        check(chamber.waitReason() == MoldingWaitReason.MISSING_CLAY, "missing clay pause reason was not retained");
        check(chamber.moldFillFraction(0.0F) == 1.0F / 3.0F
                && chamber.moldFillFraction(0.0F) == chamber.moldFillFraction(0.75F),
            "missing clay pause did not retain exactly one complete layer");
        assertRegionShapes(chamber, 1);
        ItemStack refillRemainder = chamber.clayItemHandler().insertItem(
            0,
            new ItemStack(Items.CLAY_BALL, 64),
            false
        );
        check(refillRemainder.getCount() == 30, "clay capability exceeded its configured slot limit");
        tick(chamber, 4);
        check(chamber.moldFillProgress() == 11 && chamber.moldedClayBalls() == 68,
            "second layer did not settle as one 34-ball transaction");
        check(chamber.moldFillFraction(0.0F) == 2.0F / 3.0F,
            "second complete layer did not render as two thirds");
        assertRegionShapes(chamber, 2);
        check(chamber.clayItemHandler().insertItem(0, new ItemStack(Items.CLAY_BALL, 64), false).getCount() == 30,
            "third-layer refill exceeded the configured slot limit");
        tick(chamber, 1);
        check(chamber.machineState() == PlasticMoldingMachineState.MOLD_READY,
            "twelve active ticks did not complete the mold");
        check(chamber.moldedClayBalls() == 101 && chamber.requiredClayBalls() == 101,
            "multi-refill mold did not preserve the exact clay requirement");
        assertRegionShapes(chamber, 3);

        IFluidHandler fluids = chamber.fluidHandler();
        FluidStack melt = new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), 8000);
        check(fluids.fill(melt, IFluidHandler.FluidAction.EXECUTE) == 8000,
            "staging tank did not accept 8 B of plastic melt");
        tick(chamber, 1);
        check(chamber.batchFluidAmount() == 250 && chamber.stagingFluidAmount() == 7750,
            "melt pump did not transfer exactly 250 mB in one tick");
        check(fluids.fill(melt.copyWithAmount(250), IFluidHandler.FluidAction.EXECUTE) == 250,
            "released staging capacity could not be refilled");
        check(chamber.stagingFluidAmount() == 8000 && chamber.batchFluidAmount() == 250,
            "staging and batch tanks did not retain independent capacities");

        FluidStack redMelt = melt.copyWithAmount(1000);
        PlasticMeltColor.set(redMelt, DyeColor.RED);
        check(fluids.fill(redMelt, IFluidHandler.FluidAction.EXECUTE) == 0,
            "staging tank mixed a different batch colour");
        MoldingProcessSnapshot snapshot = chamber.beginProcessing().orElseThrow(
            () -> new GameTestAssertException("process-ready chamber refused a snapshot")
        );
        int afterBegin = chamber.energyStored();
        check(chamber.finishProcessing(snapshot, false), "failed processing snapshot did not roll back");
        check(chamber.energyStored() == afterBegin + MoldingPowerBridge.energyPerWorkingTick(),
            "failed processing did not refund its preparation energy");
        check(chamber.machineState() == PlasticMoldingMachineState.PROCESS_READY,
            "failed processing did not restore process-ready state");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "11x8x11", floor = true)
    @TestHolder(description = "Every two seconds the chamber collects clay balls from the forming region and its one-block ring up to the configured limit")
    static void clayCollectionUsesExpandedRegionAndLimit(ExtendedGameTestHelper helper) {
        PlasticMoldingChamberBlockEntity chamber = placeChamber(helper, new BlockPos(5, 2, 2));
        check(chamber.setClayLimit(70, chamber.revision()).accepted(), "clay collection limit was rejected");
        chamber.inventory().setItem(
            PlasticMoldingChamberBlockEntity.CLAY_SLOT,
            new ItemStack(Items.CLAY_BALL, 60)
        );

        BlockPos controller = chamber.getBlockPos();
        ItemEntity innerClay = addItem(
            helper,
            controller.relative(Direction.SOUTH, 2).above(),
            new ItemStack(Items.CLAY_BALL, 6)
        );
        ItemEntity ringClay = addItem(
            helper,
            controller.relative(Direction.SOUTH, 4).east(2).above(3),
            new ItemStack(Items.CLAY_BALL, 20)
        );
        ItemEntity outsideClay = addItem(
            helper,
            controller.relative(Direction.SOUTH, 5),
            new ItemStack(Items.CLAY_BALL, 7)
        );
        ItemEntity nonClay = addItem(
            helper,
            controller.relative(Direction.SOUTH, 2),
            new ItemStack(Items.STONE, 3)
        );

        tick(chamber, PlasticMoldingChamberBlockEntity.CLAY_COLLECTION_INTERVAL - 2);
        check(chamber.inventory().getItem(PlasticMoldingChamberBlockEntity.CLAY_SLOT).getCount() == 60,
            "chamber collected clay before two seconds elapsed");
        tick(chamber, 1);
        check(chamber.inventory().getItem(PlasticMoldingChamberBlockEntity.CLAY_SLOT).getCount() == 70,
            "chamber did not fill its clay slot to the configured limit");
        int collectedAreaRemainder = (innerClay.isAlive() ? innerClay.getItem().getCount() : 0)
            + (ringClay.isAlive() ? ringClay.getItem().getCount() : 0);
        check(collectedAreaRemainder == 16, "chamber did not leave the exact overflow in its collection area");
        check(outsideClay.isAlive() && outsideClay.getItem().getCount() == 7,
            "chamber collected clay outside the expanded 5x5x5 region");
        check(nonClay.isAlive() && nonClay.getItem().getCount() == 3,
            "chamber collected a non-clay item");

        tick(chamber, PlasticMoldingChamberBlockEntity.CLAY_COLLECTION_INTERVAL);
        check(collectedAreaRemainder == (innerClay.isAlive() ? innerClay.getItem().getCount() : 0)
                + (ringClay.isAlive() ? ringClay.getItem().getCount() : 0),
            "full clay slot continued collecting item entities");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Unlock returns the whole batch to staging only when all of it fits")
    static void batchMeltReturnsOnlyWhenStagingFits(ExtendedGameTestHelper helper) {
        PlasticMoldingChamberBlockEntity chamber = placeChamber(helper, new BlockPos(4, 2, 2));
        check(chamber.replaceEditableModel(cubeModel(20.0D), chamber.revision()).accepted(),
            "batch return model was rejected");
        chamber.inventory().setItem(
            PlasticMoldingChamberBlockEntity.CLAY_SLOT,
            new ItemStack(Items.CLAY_BALL, 202)
        );
        chamber.energyStorage().receiveEnergy(MoldingPowerBridge.capacity(), false);
        check(chamber.requestLock().accepted(), "batch return model did not lock");
        tick(chamber, PlasticMoldingChamberBlockEntity.MOLD_FILL_TICKS);

        IFluidHandler fluids = chamber.fluidHandler();
        FluidStack melt = new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), 8000);
        check(fluids.fill(melt, IFluidHandler.FluidAction.EXECUTE) == 8000,
            "batch return staging tank did not fill");
        tick(chamber, 1);
        check(chamber.batchFluidAmount() == 250 && chamber.stagingFluidAmount() == 7750,
            "batch return setup did not pump 250 mB");
        check(chamber.unlock().accepted(), "batch melt did not return to available staging space");
        check(chamber.batchFluidAmount() == 0 && chamber.stagingFluidAmount() == 8000,
            "successful unlock did not atomically restore the full 8 B staging amount");

        check(chamber.requestLock().accepted(), "second batch return cycle did not lock");
        tick(chamber, PlasticMoldingChamberBlockEntity.MOLD_FILL_TICKS + 1);
        check(fluids.fill(melt.copyWithAmount(250), IFluidHandler.FluidAction.EXECUTE) == 250,
            "batch return setup could not refill released staging space");
        PlasticMoldingChamberBlockEntity.MachineOutcome rejected = chamber.unlock();
        check(!rejected.accepted() && rejected.reason().equals("drain_batch_first"),
            "unlock accepted a batch that could not fully return to staging");
        check(chamber.batchFluidAmount() == 250 && chamber.stagingFluidAmount() == 8000,
            "rejected unlock partially moved or discarded batch melt");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Blocked lock starts collision atomically and preserves it through a power pause")
    static void blockedRegionAndPowerPause(ExtendedGameTestHelper helper) {
        PlasticMoldingChamberBlockEntity chamber = placeChamber(helper, new BlockPos(4, 2, 2));
        check(chamber.replaceEditableModel(cubeModel(8.0D), chamber.revision()).accepted(),
            "blocking test model was rejected");
        chamber.inventory().setItem(
            PlasticMoldingChamberBlockEntity.CLAY_SLOT,
            new ItemStack(Items.CLAY_BALL, 64)
        );
        chamber.energyStorage().receiveEnergy(MoldingPowerBridge.energyPerWorkingTick(), false);
        ServerPlayer blocker = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        blocker.setPos(PlasticMoldingChamberStructure.regionBounds(
            chamber.getBlockPos(),
            chamber.getBlockState().getValue(PlasticMoldingChamberBlock.FACING)
        ).getCenter());
        check(chamber.requestLock().accepted(), "blocking test model did not lock");
        tick(chamber, 1);
        check(chamber.machineState() == PlasticMoldingMachineState.WAITING_TO_LOCK,
            "entity obstruction did not hold the pre-animation state");
        check(chamber.waitReason() == MoldingWaitReason.REGION_BLOCKED
                && chamber.moldedClayBalls() == 0
                && !chamber.hasMoldCollision(),
            "blocked region consumed clay or enabled collision");

        blocker.discard();
        tick(chamber, 1);
        check(chamber.machineState() == PlasticMoldingMachineState.MOLD_FILLING,
            "cleared region did not begin molding");
        check(chamber.moldFillProgress() == 1 && chamber.hasMoldCollision(),
            "first animation frame did not atomically enable full collision");
        int molded = chamber.moldedClayBalls();
        tick(chamber, 1);
        check(chamber.moldFillProgress() == 1 && chamber.moldedClayBalls() == molded,
            "power loss advanced animation or consumed more clay");
        check(chamber.waitReason() == MoldingWaitReason.MISSING_POWER && chamber.hasMoldCollision(),
            "power pause did not preserve mold collision and reason");
        check(chamber.moldFillFraction(0.0F) == chamber.moldFillFraction(0.75F),
            "power pause continued to interpolate the mold height");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Maximum model stores 8 B staging plus 27.648 B batch without bypassing pump rate")
    static void maximumIndependentFluidCapacity(ExtendedGameTestHelper helper) {
        PlasticMoldingChamberBlockEntity chamber = placeChamber(helper, new BlockPos(4, 2, 2));
        check(chamber.replaceEditableModel(cubeModel(48.0D), chamber.revision()).accepted(),
            "maximum model was rejected");
        chamber.energyStorage().receiveEnergy(MoldingPowerBridge.capacity(), false);
        check(chamber.requestLock().accepted(), "maximum model did not lock");
        tick(chamber, PlasticMoldingChamberBlockEntity.MOLD_FILL_TICKS);
        check(chamber.batchFluidCapacity() == 27648, "maximum model batch capacity was not 27648 mB");

        IFluidHandler fluids = chamber.fluidHandler();
        FluidStack melt = new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), 8000);
        check(fluids.fill(melt, IFluidHandler.FluidAction.EXECUTE) == 8000,
            "maximum model staging tank did not fill");
        int previousBatch = 0;
        int guard = 0;
        while (chamber.batchFluidAmount() < chamber.batchFluidCapacity() && guard++ < 128) {
            tick(chamber, 1);
            int transferred = chamber.batchFluidAmount() - previousBatch;
            check(transferred > 0 && transferred <= PlasticMoldingChamberBlockEntity.MOLDING_PUMP_RATE,
                "maximum model exceeded or stalled the fixed pump rate");
            previousBatch = chamber.batchFluidAmount();
            fluids.fill(melt, IFluidHandler.FluidAction.EXECUTE);
        }
        check(chamber.batchFluidAmount() == 27648 && chamber.stagingFluidAmount() == 8000,
            "maximum model did not retain 35648 mB across independent tanks");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Clay limit persists and redstone mode responds only to new rising edges")
    static void clayLimitPersistenceAndRedstoneEdge(ExtendedGameTestHelper helper) {
        PlasticMoldingChamberBlockEntity chamber = placeChamber(helper, new BlockPos(4, 2, 2));
        check(chamber.clayLimit() == 256, "clay limit did not default to 256");
        ItemStack fullStack = new ItemStack(Items.CLAY_BALL, 256);
        check(chamber.clayItemHandler().insertItem(0, fullStack, false).isEmpty(),
            "clay capability retained the item default stack limit");
        check(chamber.clayItemHandler().getStackInSlot(0).getCount() == 256,
            "clay capability did not retain 256 balls");
        ItemStack synchronizedStack = new ItemStack(Items.CLAY_BALL, 256);
        var synchronizedInventory = PlasticMoldingChamberBlockEntity.createInventory();
        synchronizedInventory.setItem(PlasticMoldingChamberBlockEntity.CLAY_SLOT, synchronizedStack);
        check(synchronizedInventory.getItem(PlasticMoldingChamberBlockEntity.CLAY_SLOT).getCount() == 256,
            "client-side menu inventory truncated a synchronized clay stack to 64");
        check(chamber.clayItemHandler().insertItem(0, new ItemStack(Items.CLAY_BALL), false).getCount() == 1,
            "clay capability exceeded its default 256-ball limit");
        PlasticMoldingChamberBlockEntity fullRestored = new PlasticMoldingChamberBlockEntity(
            PlasticraftBlockEntities.PLASTIC_MOLDING_CHAMBER.get(),
            chamber.getBlockPos(),
            chamber.getBlockState()
        );
        fullRestored.loadWithComponents(
            chamber.saveWithoutMetadata(helper.getLevel().registryAccess()),
            helper.getLevel().registryAccess()
        );
        check(fullRestored.inventory().getItem(PlasticMoldingChamberBlockEntity.CLAY_SLOT).getCount() == 256,
            "save/load truncated the 256-ball clay stack");
        chamber.clayItemHandler().extractItem(0, 256, false);
        check(chamber.setClayLimit(1, chamber.revision()).accepted(), "valid clay limit was rejected");
        ItemStack remainder = chamber.clayItemHandler().insertItem(
            0,
            new ItemStack(Items.CLAY_BALL, 2),
            false
        );
        check(remainder.getCount() == 1 && chamber.clayItemHandler().getStackInSlot(0).getCount() == 1,
            "clay capability inserted a second ball above limit one");
        ItemStack nonClay = new ItemStack(Items.STONE, 1);
        check(ItemStack.matches(chamber.clayItemHandler().insertItem(0, nonClay, false), nonClay),
            "clay filter accepted another item");

        PlasticMoldingChamberBlockEntity restored = new PlasticMoldingChamberBlockEntity(
            PlasticraftBlockEntities.PLASTIC_MOLDING_CHAMBER.get(),
            chamber.getBlockPos(),
            chamber.getBlockState()
        );
        restored.handleUpdateTag(
            chamber.getUpdateTag(helper.getLevel().registryAccess()),
            helper.getLevel().registryAccess()
        );
        check(restored.clayLimit() == 1, "client/reload tag lost the clay slot limit");

        check(chamber.replaceEditableModel(cubeModel(8.0D), chamber.revision()).accepted(),
            "redstone test model was rejected");
        BlockPos signal = chamber.getBlockPos().east();
        helper.getLevel().setBlockAndUpdate(signal, Blocks.REDSTONE_BLOCK.defaultBlockState());
        tick(chamber, 1);
        check(chamber.isLocked(), "redstone rising edge did not latch a lock request");
        check(chamber.unlock().accepted(), "latched redstone request could not be cancelled");
        tick(chamber, 1);
        check(!chamber.isLocked(), "steady high redstone retriggered without a rising edge");
        helper.getLevel().setBlockAndUpdate(signal, Blocks.AIR.defaultBlockState());
        tick(chamber, 1);
        helper.getLevel().setBlockAndUpdate(signal, Blocks.REDSTONE_BLOCK.defaultBlockState());
        tick(chamber, 1);
        check(chamber.isLocked(), "second redstone rising edge did not lock");
        check(chamber.unlock().accepted(), "second redstone request could not be cancelled");
        check(chamber.setProductionMode(MoldingProductionMode.SINGLE, chamber.revision()).accepted(),
            "single mode was rejected");
        helper.getLevel().setBlockAndUpdate(signal, Blocks.AIR.defaultBlockState());
        tick(chamber, 1);
        helper.getLevel().setBlockAndUpdate(signal, Blocks.REDSTONE_BLOCK.defaultBlockState());
        tick(chamber, 1);
        check(!chamber.isLocked(), "single mode responded to redstone");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "The chamber fills its printing component, moves to a voxel, then consumes one mB per printing tick")
    static void printingSkipsClayAndStartsAutomatically(ExtendedGameTestHelper helper) {
        PlasticMoldingChamberBlockEntity chamber = placeChamber(helper, new BlockPos(4, 2, 2));
        EditableMoldingModel printingModel = EditableMoldingModel.empty().withElements(List.of(MoldingElement.cube(
            "Printing Cube",
            new MoldingVec3(8.0D, 8.0D, 8.0D),
            new MoldingVec3(19.0D, 19.0D, 19.0D)
        )));
        check(chamber.replaceEditableModel(printingModel, chamber.revision()).accepted(),
            "printing model was rejected");
        check(chamber.formingMode() == MoldingFormingMode.CASTING,
            "chamber without a printing component did not select casting");

        helper.getLevel().setBlockAndUpdate(
            chamber.getBlockPos().above(),
            PlasticraftBlocks.PLASTIC_3D_PRINTING_COMPONENT.get().defaultBlockState()
        );
        check(helper.getLevel().getBlockEntity(chamber.getBlockPos().above())
                instanceof Plastic3DPrintingComponentBlockEntity,
            "printing component did not create its internal melt storage");
        check(
            helper.getLevel().getBlockState(chamber.getBlockPos().above())
                .getValue(Plastic3DPrintingComponentBlock.FACING) == Direction.NORTH,
            "printing component did not copy the chamber facing"
        );
        check(chamber.formingMode() == MoldingFormingMode.PRINTING,
            "installing the printing component did not select printing");
        assertRegionShapes(chamber, 3);
        helper.getLevel().setBlockAndUpdate(chamber.getBlockPos().above(), Blocks.AIR.defaultBlockState());
        check(chamber.formingMode() == MoldingFormingMode.CASTING,
            "removing the printing component did not restore casting");
        assertRegionShapes(chamber, 0);
        helper.getLevel().setBlockAndUpdate(
            chamber.getBlockPos().above(),
            PlasticraftBlocks.PLASTIC_3D_PRINTING_COMPONENT.get().defaultBlockState()
        );
        chamber.inventory().setItem(
            PlasticMoldingChamberBlockEntity.CLAY_SLOT,
            new ItemStack(Items.CLAY_BALL, 64)
        );
        chamber.energyStorage().receiveEnergy(MoldingPowerBridge.capacity(), false);
        check(
            helper.getLevel().getBlockState(chamber.getBlockPos().above())
                .getValue(Plastic3DPrintingComponentBlock.POWERED),
            "printing component did not follow the chamber powered model"
        );
        check(chamber.requestLock().accepted(), "printing model did not lock with its component");
        check(chamber.requiredClayBalls() == 0 && chamber.moldedClayBalls() == 0,
            "printing reserved clay");
        check(chamber.batchFluidCapacity() == 333, "printing model did not calculate its full 333 mB batch");
        check(chamber.fluidHandler().fill(
            new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), 333),
            IFluidHandler.FluidAction.EXECUTE
        ) == 333, "printing staging tank rejected its melt");

        tick(chamber, 1);
        check(chamber.batchFluidAmount() == 250 && chamber.stagingFluidAmount() == 83,
            "chamber did not pump exactly 250 mB into the printing component");
        check(chamber.machineState() == PlasticMoldingMachineState.MOLD_READY
                && chamber.moldFillProgress() == 0
                && !chamber.hasMoldCollision(),
            "printing entered the clay animation or mold collision state");
        check(chamber.beginProcessing().isEmpty(), "a giant-anvil transaction started printing");
        tick(chamber, 1);
        check(chamber.batchFluidAmount() == chamber.batchFluidCapacity()
                && chamber.stagingFluidAmount() == 0
                && chamber.machineState() == PlasticMoldingMachineState.PROCESS_READY,
            "printing component did not receive the complete model batch");
        tick(chamber, 1);
        check(chamber.batchFluidAmount() == chamber.batchFluidCapacity()
                && chamber.machineState() == PlasticMoldingMachineState.PROCESSING
                && chamber.printingProgress() == 0
                && chamber.printingMotion().to().equals(MoldingPrinterMotion.DEFAULT_POSITION),
            "full printing component did not start automatically without consuming melt early");
        tick(chamber, 1);
        check(chamber.batchFluidAmount() == chamber.batchFluidCapacity()
                && chamber.printingProgress() == 0,
            "printing consumed material before the print head arrived");
        tick(chamber, chamber.printingMotion().durationTicks());
        check(chamber.batchFluidAmount() == chamber.batchFluidCapacity() - 1,
            "printing component did not consume exactly 1 mB after arriving");
        check(chamber.printingProgress() == 1
                && chamber.machineState() == PlasticMoldingMachineState.PROCESSING,
            "printing did not reveal the first voxel after consuming its full material share");
        check(chamber.inventory().getItem(PlasticMoldingChamberBlockEntity.CLAY_SLOT).getCount() == 64
                && chamber.moldedClayBalls() == 0,
            "printing consumed clay after it started");
        helper.getLevel().setBlockAndUpdate(chamber.getBlockPos().above(), Blocks.AIR.defaultBlockState());
        check(chamber.machineState() == PlasticMoldingMachineState.EDITABLE
                && chamber.printingProgress() == 0
                && chamber.printingTotal() == 0
                && chamber.activeProcessingSnapshot().isEmpty(),
            "breaking the active printing component did not stop and clear the interrupted print");
        helper.getLevel().setBlockAndUpdate(
            chamber.getBlockPos().above(),
            PlasticraftBlocks.PLASTIC_3D_PRINTING_COMPONENT.get().defaultBlockState()
        );
        check(chamber.machineState() == PlasticMoldingMachineState.EDITABLE
                && chamber.requestLock().accepted(),
            "a replacement printing component could not start a fresh cycle after interruption");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Right-clicking the printing component opens the chamber GUI")
    static void printingComponentOpensChamberMenu(ExtendedGameTestHelper helper) {
        PlasticMoldingChamberBlockEntity chamber = placeChamber(helper, new BlockPos(4, 2, 2));
        BlockPos componentPos = chamber.getBlockPos().above();
        helper.getLevel().setBlockAndUpdate(
            componentPos,
            PlasticraftBlocks.PLASTIC_3D_PRINTING_COMPONENT.get().defaultBlockState()
        );
        ServerPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setPos(componentPos.getCenter());
        check(
            helper.getLevel().getBlockState(componentPos).useWithoutItem(
                helper.getLevel(),
                player,
                new BlockHitResult(componentPos.getCenter(), Direction.NORTH, componentPos, false)
            ).consumesAction(),
            "printing component interaction was not consumed"
        );
        check(
            player.containerMenu instanceof PlasticMoldingChamberMenu,
            "printing component did not open the chamber GUI"
        );
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "High-precision products require the printing component and need no forming-mode action")
    static void highPrecisionTypeUsesInstalledPrintingComponent(ExtendedGameTestHelper helper) {
        PlasticMoldingChamberBlockEntity chamber = placeChamber(helper, new BlockPos(4, 2, 2));
        EditableMoldingModel propeller = EditableMoldingModel.empty().withElements(List.of(
            MoldingElement.cube("Core", new MoldingVec3(23, 24, 23), new MoldingVec3(25, 25, 25)),
            MoldingElement.cube("West", new MoldingVec3(16, 24, 23), new MoldingVec3(23, 25, 25)),
            MoldingElement.cube("East", new MoldingVec3(25, 24, 23), new MoldingVec3(32, 25, 25)),
            MoldingElement.cube("North", new MoldingVec3(23, 24, 16), new MoldingVec3(25, 25, 23)),
            MoldingElement.cube("South", new MoldingVec3(23, 24, 25), new MoldingVec3(25, 25, 32))
        )).withRequestedType(MoldingProductTypes.PROPELLER_ID);
        check(chamber.replaceEditableModel(propeller, chamber.revision()).accepted(),
            "valid propeller model was rejected");
        PlasticMoldingChamberBlockEntity.MachineOutcome missingComponent = chamber.requestLock();
        check(!missingComponent.accepted() && missingComponent.reason().equals("missing_printing_component"),
            "high-precision product locked without a printing component");

        helper.getLevel().setBlockAndUpdate(
            chamber.getBlockPos().above(),
            PlasticraftBlocks.PLASTIC_3D_PRINTING_COMPONENT.get().defaultBlockState()
        );
        PlasticMoldingChamberBlockEntity.MachineOutcome installedComponent = chamber.requestLock();
        check(installedComponent.accepted(),
            "installed printing component did not unlock high-precision processing: "
                + installedComponent.reason());
        check(chamber.cycleFormingMode() == MoldingFormingMode.PRINTING
                && chamber.requiredClayBalls() == 0,
            "high-precision cycle did not automatically lock as clay-free printing");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Printing voxels scan Y layers with X rows and alternating Z directions")
    static void printingPlanUsesSnakeOrder(ExtendedGameTestHelper helper) {
        EditableMoldingModel model = EditableMoldingModel.empty().withElements(List.of(
            MoldingElement.cube("First", new MoldingVec3(18, 20, 18), new MoldingVec3(19, 21, 19)),
            MoldingElement.cube("First Row End", new MoldingVec3(18, 20, 30), new MoldingVec3(19, 21, 31)),
            MoldingElement.cube("Second Row Start", new MoldingVec3(19, 20, 30), new MoldingVec3(20, 21, 31)),
            MoldingElement.cube("Second Row End", new MoldingVec3(19, 20, 18), new MoldingVec3(20, 21, 19)),
            MoldingElement.cube("Later Y", new MoldingVec3(18, 22, 18), new MoldingVec3(19, 23, 19))
        ));
        MoldingPrintingPlan plan = MoldingPrintingPlan.create(model, MoldingModelBaker.bake(model));
        check(plan.size() == 5, "printing plan added cells outside the model");
        check(plan.voxelAt(0).equals(new MoldingPrintingVoxel(18, 20, 18)),
            "printing plan did not begin at the north end of its first row");
        check(plan.voxelAt(1).equals(new MoldingPrintingVoxel(18, 20, 30)),
            "printing plan did not advance south along the first row");
        check(plan.voxelAt(2).equals(new MoldingPrintingVoxel(19, 20, 30)),
            "printing plan did not move one pixel east at the row end");
        check(plan.voxelAt(3).equals(new MoldingPrintingVoxel(19, 20, 18)),
            "printing plan did not reverse north along the second row");
        check(plan.voxelAt(4).equals(new MoldingPrintingVoxel(18, 22, 18)),
            "printing plan did not advance to the next Y layer last");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Printing includes positive-volume slope intersections and closes each printed pixel")
    static void printingPlanPreservesSlopedPixelGeometry(ExtendedGameTestHelper helper) {
        MoldingElement source = MoldingElement.cube(
            "Sloped Sheet",
            new MoldingVec3(12.0D, 22.5D, 16.0D),
            new MoldingVec3(36.0D, 25.5D, 32.0D)
        );
        MoldingElement sloped = new MoldingElement(
            source.id(),
            source.name(),
            source.groupId(),
            source.from(),
            source.to(),
            new MoldingTransform(
                MoldingVec3.ZERO,
                new MoldingVec3(0.0D, 0.0D, 30.0D),
                MoldingVec3.ONE,
                source.transform().pivot()
            ),
            source.visible(),
            source.locked()
        );
        EditableMoldingModel slopedModel = EditableMoldingModel.empty().withElements(List.of(sloped));
        var slopedBaked = MoldingModelBaker.bake(slopedModel);
        MoldingPrintingPlan slopedPlan = MoldingPrintingPlan.create(slopedModel, slopedBaked);
        check(slopedPlan.size() > slopedBaked.volumeMask().volume(),
            "printing plan still omitted slope intersections without a sampled cell center");

        var sampled = slopedBaked.volumeMask();
        var intersecting = slopedPlan.voxelMask();
        boolean foundBoundaryCell = false;
        for (int index = 0; index < slopedPlan.size(); index++) {
            MoldingPrintingVoxel voxel = slopedPlan.voxelAt(index);
            if (!sampled.get(voxel.x(), voxel.y(), voxel.z())) {
                foundBoundaryCell = true;
                break;
            }
        }
        check(foundBoundaryCell, "printing slope regression did not exercise a boundary-only cell");

        EditableMoldingModel solid = EditableMoldingModel.empty().withElements(List.of(MoldingElement.cube(
            "Solid",
            new MoldingVec3(8.0D, 8.0D, 8.0D),
            new MoldingVec3(40.0D, 40.0D, 40.0D)
        )));
        var solidHull = MoldingModelBaker.createManufacturedGeometry(solid, 1.0D).collisionHulls().getFirst();
        List<MoldingQuad> closedCell = MoldingModelBaker.clipConvexHullToCell(solidHull, 20, 20, 20);
        check(closedCell.size() == 6, "an interior printed pixel was not closed on all six sides");
        Set<MoldingVec3> normals = new HashSet<>();
        closedCell.forEach(quad -> normals.add(quad.normal()));
        check(normals.size() == 6, "an interior printed pixel repeated or omitted a closing face");
        check(intersecting.volume() == slopedPlan.size(), "printing plan and intersecting mask diverged");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Printing occupancy SAT reject matches CSG and fully printed faces stay source cubes")
    static void printingOccupancyAndPrintedSurfaceStayExact(ExtendedGameTestHelper helper) {
        EditableMoldingModel aligned = EditableMoldingModel.empty().withElements(List.of(MoldingElement.cube(
            "Aligned",
            new MoldingVec3(16.0D, 16.0D, 16.0D),
            new MoldingVec3(24.0D, 24.0D, 24.0D)
        )));
        checkIntersectingMaskMatchesCsg(aligned, "aligned cube");

        EditableMoldingModel sloped = rotatedCube(
            "Sloped",
            new MoldingVec3(12.0D, 22.5D, 16.0D),
            new MoldingVec3(36.0D, 25.5D, 32.0D),
            new MoldingVec3(0.0D, 0.0D, 30.0D)
        );
        checkIntersectingMaskMatchesCsg(sloped, "30 degree slope");

        EditableMoldingModel yawed = rotatedCube(
            "Yawed",
            new MoldingVec3(18.0D, 18.0D, 18.0D),
            new MoldingVec3(30.0D, 26.0D, 30.0D),
            new MoldingVec3(0.0D, 45.0D, 0.0D)
        );
        checkIntersectingMaskMatchesCsg(yawed, "yawed cube");

        MoldingConvexHull solidHull = MoldingModelBaker.createManufacturedGeometry(aligned, 1.0D)
            .collisionHulls()
            .getFirst();
        for (MoldingQuad quad : MoldingModelBaker.clipConvexHullToCell(solidHull, 20, 20, 20)) {
            MoldingVec3 cross = quad.second().subtract(quad.first()).cross(quad.third().subtract(quad.first()));
            check(cross.dot(quad.normal()) > 0.0D,
                "clipped interior cell winding disagreed with its face normal");
        }

        var slopedBaked = MoldingModelBaker.bake(sloped);
        MoldingPrintingPlan slopedPlan = MoldingPrintingPlan.create(sloped, slopedBaked);
        MoldingPrintedGeometry printed = MoldingPrintedGeometry.prepare(sloped, slopedPlan);
        printed.advanceTo(slopedPlan.size());
        check(printed.surfaceTriangles().size() >= 12 && printed.surfaceTriangles().size() <= 24,
            "fully printed slope triangle count was " + printed.surfaceTriangles().size());
        check(printed.capTriangles().isEmpty(),
            "fully printed slope kept voxel staircase caps on the exterior");
        double slopedArea = MoldingPrintedGeometry.triangleListArea(printed.surfaceTriangles());
        double slopedFragments = printedFaceFragmentArea(sloped, slopedPlan, slopedPlan.size());
        check(
            Math.abs(slopedArea - slopedFragments) <= Math.max(1.0E-4D, 1.0E-3D * Math.max(slopedArea, slopedFragments)),
            "fully printed slope area diverged: " + slopedArea + " vs " + slopedFragments
        );
        for (MoldingPrintedGeometry.PrintedTriangle triangle : printed.surfaceTriangles()) {
            MoldingVec3 cross = triangle.second().subtract(triangle.first())
                .cross(triangle.third().subtract(triangle.first()));
            check(cross.dot(triangle.normal()) > 0.0D,
                "printed slope winding disagreed with its face normal");
        }

        MoldingPrintingPlan alignedPlan = MoldingPrintingPlan.create(aligned, MoldingModelBaker.bake(aligned));
        MoldingPrintedGeometry alignedPrinted = MoldingPrintedGeometry.prepare(aligned, alignedPlan);
        alignedPrinted.advanceTo(alignedPlan.size());
        check(alignedPrinted.surfaceTriangles().size() == 12,
            "fully printed cube triangle count was " + alignedPrinted.surfaceTriangles().size());
        check(alignedPrinted.capTriangles().isEmpty(),
            "fully printed cube kept interior voxel caps");
        check(
            Math.abs(MoldingPrintedGeometry.triangleListArea(alignedPrinted.surfaceTriangles()) - 384.0D) <= 0.05D,
            "fully printed cube lost original faces"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Partial printed slope keeps occupancy fragments without corner webbing")
    static void printingPartialSlopeKeepsFragmentArea(ExtendedGameTestHelper helper) {
        EditableMoldingModel sloped = rotatedCube(
            "Sloped",
            new MoldingVec3(12.0D, 22.5D, 16.0D),
            new MoldingVec3(36.0D, 25.5D, 32.0D),
            new MoldingVec3(0.0D, 0.0D, 30.0D)
        );
        MoldingPrintingPlan slopedPlan = MoldingPrintingPlan.create(sloped, MoldingModelBaker.bake(sloped));
        int partial = Math.max(8, slopedPlan.size() / 3);
        MoldingPrintedGeometry printed = MoldingPrintedGeometry.prepare(sloped, slopedPlan);
        printed.advanceTo(partial);
        double printedArea = MoldingPrintedGeometry.triangleListArea(printed.surfaceTriangles());
        double fragmentArea = printedFaceFragmentArea(sloped, slopedPlan, partial);
        check(fragmentArea > 1.0D, "partial slope fragment area was empty: " + fragmentArea);
        check(
            printedArea + 1.0E-3D >= 0.98D * fragmentArea,
            "partial slope printed area was smaller than occupancy fragments: "
                + printedArea + " vs " + fragmentArea
        );
        check(
            printedArea <= fragmentArea * 1.08D + 0.05D,
            "partial slope webbed across unprinted face area: " + printedArea + " vs " + fragmentArea
        );
        for (MoldingPrintedGeometry.PrintedTriangle triangle : printed.surfaceTriangles()) {
            MoldingVec3 cross = triangle.second().subtract(triangle.first())
                .cross(triangle.third().subtract(triangle.first()));
            check(cross.dot(triangle.normal()) > 0.0D,
                "partial printed slope winding disagreed with its face normal");
        }

        EditableMoldingModel concave = EditableMoldingModel.empty().withElements(List.of(
            MoldingElement.cube("ArmX", new MoldingVec3(16.0D, 20.0D, 16.0D), new MoldingVec3(28.0D, 24.0D, 20.0D)),
            MoldingElement.cube("ArmZ", new MoldingVec3(16.0D, 20.0D, 16.0D), new MoldingVec3(20.0D, 24.0D, 28.0D))
        ));
        MoldingPrintingPlan concavePlan = MoldingPrintingPlan.create(concave, MoldingModelBaker.bake(concave));
        int concavePartial = Math.max(8, concavePlan.size() / 2);
        MoldingPrintedGeometry concavePrinted = MoldingPrintedGeometry.prepare(concave, concavePlan);
        concavePrinted.advanceTo(concavePartial);
        double concavePrintedArea = MoldingPrintedGeometry.triangleListArea(concavePrinted.surfaceTriangles());
        double concaveFragments = printedFaceFragmentArea(concave, concavePlan, concavePartial);
        check(
            concavePrintedArea + 1.0E-3D >= 0.98D * concaveFragments,
            "concave occupancy printed area was smaller than fragments: "
                + concavePrintedArea + " vs " + concaveFragments
        );
        check(
            concavePrintedArea <= concaveFragments * 1.08D + 0.05D,
            "concave occupancy webbed across unprinted face area: "
                + concavePrintedArea + " vs " + concaveFragments
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Rotated cube plane-slope dihedrals stay closed without outer voxel caps")
    static void printingRotatedCubeDihedralsStayClosed(ExtendedGameTestHelper helper) {
        EditableMoldingModel sloped = rotatedCube(
            "Sloped",
            new MoldingVec3(12.0D, 22.5D, 16.0D),
            new MoldingVec3(36.0D, 25.5D, 32.0D),
            new MoldingVec3(0.0D, 0.0D, 30.0D)
        );
        MoldingPrintingPlan plan = MoldingPrintingPlan.create(sloped, MoldingModelBaker.bake(sloped));
        MoldingPrintedGeometry full = MoldingPrintedGeometry.prepare(sloped, plan);
        full.advanceTo(plan.size());
        check(full.capTriangles().isEmpty(), "fully printed rotated cube kept outer voxel caps");
        checkDihedralsCovered(sloped, full.surfaceTriangles(), "full print");
        checkCapsAvoidOriginalFaces(sloped, full.capTriangles(), "full print");

        MoldingPrintedGeometry partial = MoldingPrintedGeometry.prepare(sloped, plan);
        int partialCount = Math.max(8, plan.size() / 3);
        partial.advanceTo(partialCount);
        checkCapsAvoidOriginalFaces(sloped, partial.capTriangles(), "partial print");
        checkPrintedDihedralCellsCovered(sloped, plan, partialCount, partial.surfaceTriangles());
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Printing accepts only x/z 8..40 and y 1..33")
    static void printingUsesReducedWorkspace(ExtendedGameTestHelper helper) {
        check(MoldingPrinterMotion.DEFAULT_POSITION.equals(new MoldingVec3(16.0D, 32.0D, 16.0D)),
            "printing head did not use the raised default position");
        PlasticMoldingChamberBlockEntity chamber = placeChamber(helper, new BlockPos(4, 2, 2));
        helper.getLevel().setBlockAndUpdate(
            chamber.getBlockPos().above(),
            PlasticraftBlocks.PLASTIC_3D_PRINTING_COMPONENT.get().defaultBlockState()
        );
        Plastic3DPrintingComponentBlockEntity component = helper.getLevel().getBlockEntity(
            chamber.getBlockPos().above()
        ) instanceof Plastic3DPrintingComponentBlockEntity result ? result : null;
        if (component == null) throw new GameTestAssertException("printing component block entity was missing");
        FluidStack fullComponent = new FluidStack(
            PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(),
            Plastic3DPrintingComponentBlockEntity.CAPACITY
        );
        check(Plastic3DPrintingComponentBlockEntity.CAPACITY == 8192
                && component.fillFromChamber(fullComponent, IFluidHandler.FluidAction.EXECUTE) == 8192
                && component.fluidAmount() == 8192
                && component.fillFromChamber(
                    fullComponent.copyWithAmount(1),
                    IFluidHandler.FluidAction.EXECUTE
                ) == 0,
            "printing component did not enforce its exact 8192 mB capacity");
        check(component.consumeForPrinting(8192, IFluidHandler.FluidAction.EXECUTE).getAmount() == 8192,
            "printing component could not drain its full 8192 mB capacity");
        EditableMoldingModel maximum = EditableMoldingModel.empty().withElements(List.of(MoldingElement.cube(
            "Maximum Printing Volume",
            new MoldingVec3(8.0D, 1.0D, 8.0D),
            new MoldingVec3(40.0D, 33.0D, 40.0D)
        )));
        check(chamber.replaceEditableModel(maximum, chamber.revision()).accepted(),
            "maximum printing model could not be staged");
        check(chamber.modelFitsWorkspace() && chamber.requestLock().accepted(),
            "maximum printing volume did not lock");
        check(chamber.printingTotal() == 32768 && chamber.batchFluidCapacity() == 8192,
            "maximum printing volume or melt requirement was incorrect");
        check(chamber.unlock().accepted(), "maximum printing model could not be unlocked");

        EditableMoldingModel tooWide = EditableMoldingModel.empty().withElements(List.of(MoldingElement.cube(
            "Too Wide For Printer",
            new MoldingVec3(7.0D, 1.0D, 8.0D),
            new MoldingVec3(40.0D, 33.0D, 40.0D)
        )));
        check(chamber.replaceEditableModel(tooWide, chamber.revision()).accepted(),
            "oversized printing model could not be staged");
        PlasticMoldingChamberBlockEntity.MachineOutcome wideResult = chamber.requestLock();
        check(!chamber.modelFitsWorkspace()
                && !wideResult.accepted()
                && wideResult.reason().equals("model_too_large"),
            "printing accepted geometry outside the centered 32x32 area");

        EditableMoldingModel tooLow = EditableMoldingModel.empty().withElements(List.of(MoldingElement.cube(
            "Too Low For Printer",
            new MoldingVec3(8.0D, 0.0D, 8.0D),
            new MoldingVec3(40.0D, 33.0D, 40.0D)
        )));
        check(chamber.replaceEditableModel(tooLow, chamber.revision()).accepted(),
            "too-low printing model could not be staged");
        PlasticMoldingChamberBlockEntity.MachineOutcome lowResult = chamber.requestLock();
        check(!chamber.modelFitsWorkspace()
                && !lowResult.accepted()
                && lowResult.reason().equals("model_too_large"),
            "printing accepted geometry below the centered volume");

        EditableMoldingModel tooHigh = EditableMoldingModel.empty().withElements(List.of(MoldingElement.cube(
            "Too High For Printer",
            new MoldingVec3(8.0D, 1.0D, 8.0D),
            new MoldingVec3(40.0D, 34.0D, 40.0D)
        )));
        check(chamber.replaceEditableModel(tooHigh, chamber.revision()).accepted(),
            "too-high printing model could not be staged");
        PlasticMoldingChamberBlockEntity.MachineOutcome highResult = chamber.requestLock();
        check(!chamber.modelFitsWorkspace()
                && !highResult.accepted()
                && highResult.reason().equals("model_too_large"),
            "printing accepted geometry above the centered volume");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Editable blueprints use structure disks and load oversized models as editable drafts")
    static void blueprintDiskAtomicStoreAndOfflineLoad(ExtendedGameTestHelper helper) throws BlueprintException {
        PlasticMoldingChamberBlockEntity chamber = placeChamber(helper, new BlockPos(4, 2, 2));
        ServerPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setPos(chamber.getBlockPos().getCenter());
        MoldingSessionSnapshot session = chamber.openSession(player, true);
        check(!chamber.inventory().canPlaceItem(
            PlasticMoldingChamberBlockEntity.DISK_SLOT,
            new ItemStack(ModItems.DISK.get())
        ), "ordinary AnvilCraft disk was accepted by the molding chamber");
        ItemStack emptyStructureDisk = new ItemStack(ModItems.STRUCTURE_DISK.get());
        check(!MoldingBlueprintDisk.hasBlueprintData(emptyStructureDisk),
            "empty structure disk was marked as containing a molding blueprint");
        check(chamber.inventory().canPlaceItem(
            PlasticMoldingChamberBlockEntity.DISK_SLOT,
            emptyStructureDisk
        ), "AnvilCraft structure disk was rejected by the molding chamber");
        chamber.inventory().setItem(
            PlasticMoldingChamberBlockEntity.DISK_SLOT,
            emptyStructureDisk
        );
        EditableMoldingModel storedModel = cubeModel(8.0D).withName("Disk Atomic Model");
        check(chamber.replaceEditableModel(storedModel, chamber.revision()).accepted(), "disk model was rejected");
        MoldingBlueprintService.OperationResult stored = MoldingBlueprintService.storeModel(
            player,
            chamber,
            session.sessionId(),
            chamber.revision(),
            "",
            false
        );
        ItemStack disk = chamber.inventory().getItem(PlasticMoldingChamberBlockEntity.DISK_SLOT);
        MoldingBlueprint embedded = MoldingBlueprintDisk.read(disk).orElseThrow(
            () -> new GameTestAssertException("stored disk did not contain a valid embedded blueprint")
        );
        check(MoldingBlueprintDisk.hasBlueprintData(disk),
            "stored structure disk was missing the lightweight molding blueprint marker");
        check(MoldingBlueprintDisk.previewToken(disk).equals(
                MoldingBlueprintDisk.SCHEMA_VERSION + ":" + embedded.modelHash()
            ), "stored structure disk preview token did not track its model hash");
        check(embedded.ownerId().equals(player.getUUID()), "disk owner was not the player who clicked store");
        check(embedded.modelHash().equals(chamber.bakedModel().modelHash()), "disk hash did not match the locked model");
        MoldingBlueprint shared = MoldingBlueprintLibrary.read(
            player.getServer(),
            stored.selectedFileId(),
            stored.fileRevision()
        );
        check(shared.ownerId().equals(player.getUUID()), "shared file owner was not the storing player");

        ItemStack originalDisk = disk.copy();
        check(chamber.replaceEditableModel(cubeModel(4.0D).withName("Replacement Draft"), chamber.revision()).accepted(),
            "replacement draft was rejected");
        String occupiedToken = MoldingBlueprintDisk.stateToken(disk);
        BlueprintException overwriteConfirmation = expectBlueprintFailure(() -> MoldingBlueprintService.storeModel(
            player,
            chamber,
            session.sessionId(),
            chamber.revision(),
            occupiedToken,
            false
        ));
        check(overwriteConfirmation.reason().equals("confirm_disk_overwrite"),
            "occupied disk store did not require confirmation");
        MoldingBlueprintService.OperationResult overwritten = MoldingBlueprintService.storeModel(
            player,
            chamber,
            session.sessionId(),
            chamber.revision(),
            occupiedToken,
            true
        );
        check(MoldingBlueprintDisk.read(chamber.inventory().getItem(PlasticMoldingChamberBlockEntity.DISK_SLOT))
                .orElseThrow(() -> new GameTestAssertException("confirmed store erased the disk blueprint"))
                .modelHash().equals(chamber.bakedModel().modelHash()),
            "confirmed store did not replace the disk model");
        MoldingBlueprintLibrary.delete(player, overwritten.selectedFileId(), overwritten.fileRevision());
        chamber.inventory().setItem(PlasticMoldingChamberBlockEntity.DISK_SLOT, originalDisk);
        ItemStack restoredDisk = chamber.inventory().getItem(PlasticMoldingChamberBlockEntity.DISK_SLOT);

        ItemStack beforeStaleWrite = restoredDisk.copy();
        BlueprintException staleFile = expectBlueprintFailure(() ->
            MoldingBlueprintService.writeSharedModelToDisk(
                player,
                chamber,
                session.sessionId(),
                chamber.revision(),
                stored.selectedFileId(),
                stored.fileRevision() + 1L,
                MoldingBlueprintDisk.stateToken(restoredDisk),
                true
            )
        );
        check(staleFile.reason().equals("stale_file_revision"), "stale shared write returned the wrong reason");
        check(ItemStack.matches(beforeStaleWrite, chamber.inventory().getItem(PlasticMoldingChamberBlockEntity.DISK_SLOT)),
            "failed shared write damaged the disk");

        MoldingBlueprintLibrary.delete(player, stored.selectedFileId(), stored.fileRevision());
        FluidStack melt = new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), 500);
        check(chamber.fluidHandler().fill(melt, IFluidHandler.FluidAction.EXECUTE) == 500,
            "staging tank rejected the disk-load preservation fluid");
        String diskToken = MoldingBlueprintDisk.stateToken(restoredDisk);
        BlueprintException confirmation = expectBlueprintFailure(() -> MoldingBlueprintService.loadDiskModel(
            player,
            chamber,
            session.sessionId(),
            chamber.revision(),
            diskToken,
            false
        ));
        check(confirmation.reason().equals("confirm_model_overwrite"), "draft overwrite did not require confirmation");
        MoldingBlueprintService.loadDiskModel(
            player,
            chamber,
            session.sessionId(),
            chamber.revision(),
            diskToken,
            true
        );
        check(chamber.machineState() == PlasticMoldingMachineState.EDITABLE, "disk load unexpectedly locked the model");
        check(chamber.bakedModel().modelHash().equals(embedded.modelHash()), "disk load lost its embedded geometry");
        check(chamber.stagingFluidAmount() == 500, "disk load changed the independent staging tank");

        EditableMoldingModel oversizedModel = cubeModel(49.0D).withName("Oversized Disk Model");
        MoldingBlueprint oversizedBlueprint = MoldingBlueprint.create(
            oversizedModel,
            player.getUUID(),
            player.getGameProfile().getName(),
            System.currentTimeMillis()
        );
        ItemStack blockStructureDisk = new ItemStack(ModItems.STRUCTURE_DISK.get());
        blockStructureDisk.set(ModComponents.STRUCTURE_DISK_DATA, new StructureDiskData(
            "test_structure.nbt",
            "Test Block Structure",
            player.getUUID(),
            Direction.NORTH,
            5,
            5,
            5,
            false
        ));
        ItemStack oversizedDisk = MoldingBlueprintDisk.writeCopy(
            blockStructureDisk,
            oversizedBlueprint
        );
        check(!oversizedDisk.has(ModComponents.STRUCTURE_DISK_DATA),
            "molding blueprint disk retained the replaced block structure data");
        check(MoldingBlueprintDisk.read(oversizedDisk).isPresent(),
            "oversized model could not be stored on a structure disk");
        check(MoldingModelBounds.visible(oversizedModel)
                .orElseThrow()
                .sizeBlocks()
                .equals(new MoldingVec3(3.0625D, 3.0625D, 3.0625D)),
            "oversized disk model dimensions did not preserve the manufactured size");
        chamber.inventory().setItem(PlasticMoldingChamberBlockEntity.DISK_SLOT, oversizedDisk);
        MoldingBlueprintService.loadDiskModel(
            player,
            chamber,
            session.sessionId(),
            chamber.revision(),
            MoldingBlueprintDisk.stateToken(oversizedDisk),
            true
        );
        check(chamber.machineState() == PlasticMoldingMachineState.EDITABLE,
            "oversized structure disk model did not remain editable after loading");
        check(chamber.bakedModel().modelHash().equals(oversizedBlueprint.modelHash()),
            "oversized structure disk model did not replace the current draft");
        check(chamber.stagingFluidAmount() == 500,
            "oversized structure disk load changed the independent staging tank");
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Resource slot accepts every supported input and consumes at most one capacitor per tick")
    static void resourceSlotAcceptsMeltAndCapacitors(ExtendedGameTestHelper helper) {
        PlasticMoldingChamberBlockEntity chamber = placeChamber(helper, new BlockPos(4, 2, 2));
        check(chamber.inventory().canPlaceItem(
            PlasticMoldingChamberBlockEntity.RESOURCE_SLOT,
            PlasticraftItems.UNIVERSAL_PLASTIC_MELT_BUCKET.asStack()
        ), "resource slot rejected a plastic melt bucket");
        check(chamber.inventory().canPlaceItem(
            PlasticMoldingChamberBlockEntity.RESOURCE_SLOT,
            ModItems.CAPACITOR.asStack()
        ), "resource slot rejected a charged capacitor");
        check(!chamber.inventory().canPlaceItem(
            PlasticMoldingChamberBlockEntity.RESOURCE_SLOT,
            ModItems.CAPACITOR_EMPTY.asStack()
        ), "resource slot accepted an empty capacitor");
        check(chamber.inventory().canPlaceItem(
            PlasticMoldingChamberBlockEntity.RESOURCE_SLOT,
            ModItems.MULTIPHASE_TRANSCENDIUM.asStack()
        ), "resource slot rejected Multiphase Transcendium");
        check(chamber.inventory().canPlaceItem(
            PlasticMoldingChamberBlockEntity.RESOURCE_SLOT,
            ModBlocks.CREATIVE_GENERATOR.asStack()
        ), "resource slot rejected a Creative Generator");

        ItemStack meltBucket = PlasticraftItems.UNIVERSAL_PLASTIC_MELT_BUCKET.asStack();
        PlasticMeltColor.set(meltBucket, DyeColor.RED);
        chamber.inventory().setItem(PlasticMoldingChamberBlockEntity.RESOURCE_SLOT, meltBucket);
        tick(chamber, 1);
        check(chamber.stagingFluidAmount() == FluidType.BUCKET_VOLUME,
            "resource-slot melt bucket did not fill staging");
        check(chamber.inventory().getItem(PlasticMoldingChamberBlockEntity.RESOURCE_SLOT).is(Items.BUCKET),
            "resource-slot melt bucket did not leave an empty bucket");
        check(PlasticMeltColor.get(chamber.stagingFluid()) == DyeColor.RED,
            "resource-slot melt bucket lost its colour");

        ServerPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setPos(chamber.getBlockPos().getCenter());
        chamber.openMenu(player);
        chamber.inventory().setItem(
            PlasticMoldingChamberBlockEntity.RESOURCE_SLOT,
            ModItems.CAPACITOR.asStack(2)
        );
        tick(chamber, 1);
        check(chamber.energyStored() == 8_000_000,
            "resource slot did not apply one normal-capacitor charge");
        check(chamber.inventory().getItem(PlasticMoldingChamberBlockEntity.RESOURCE_SLOT).is(ModItems.CAPACITOR)
                && chamber.inventory().getItem(PlasticMoldingChamberBlockEntity.RESOURCE_SLOT).getCount() == 1,
            "resource slot consumed more than one capacitor in one tick");
        check(player.getInventory().countItem(ModItems.CAPACITOR_EMPTY.get()) == 1,
            "blocked empty capacitor did not return to the active player");

        tick(chamber, 1);
        check(chamber.energyStored() == 16_000_000,
            "second game tick did not consume the second capacitor");
        check(chamber.inventory().getItem(PlasticMoldingChamberBlockEntity.RESOURCE_SLOT)
                .is(ModItems.CAPACITOR_EMPTY),
            "final empty capacitor did not remain in the resource slot");
        player.discard();

        ServerPlayer fullPlayer = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        fullPlayer.setPos(chamber.getBlockPos().getCenter());
        chamber.openMenu(fullPlayer);
        for (int slot = 0; slot < fullPlayer.getInventory().getContainerSize(); slot++) {
            fullPlayer.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        }
        chamber.inventory().setItem(
            PlasticMoldingChamberBlockEntity.RESOURCE_SLOT,
            ModItems.CAPACITOR.asStack(2)
        );
        tick(chamber, 1);
        List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(
            ItemEntity.class,
            new AABB(chamber.getBlockPos()).inflate(3.0D),
            item -> item.getItem().is(ModItems.CAPACITOR_EMPTY)
        );
        check(dropped.stream().mapToInt(item -> item.getItem().getCount()).sum() == 1,
            "full player inventory did not drop the empty capacitor");
        fullPlayer.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "15x7x15", floor = true)
    @TestHolder(description = "Crafted chambers inherit full or empty super-capacitor energy")
    static void craftingInheritsSuperCapacitorEnergy(ExtendedGameTestHelper helper) {
        CraftingRecipe recipe = helper.getLevel().getRecipeManager()
            .byKey(AnvilcraftPlasticraft.of("plastic_molding_chamber"))
            .filter(holder -> holder.value() instanceof CraftingRecipe)
            .map(holder -> (CraftingRecipe) holder.value())
            .orElseThrow(() -> new GameTestAssertException("plastic molding chamber recipe was not loaded"));
        CraftingInput chargedInput = moldingChamberCraftingInput(ModItems.SUPER_CAPACITOR.asStack());
        CraftingInput emptyInput = moldingChamberCraftingInput(ModItems.SUPER_CAPACITOR_EMPTY.asStack());
        check(recipe.matches(chargedInput, helper.getLevel()), "charged super-capacitor recipe did not match");
        check(recipe.matches(emptyInput, helper.getLevel()), "empty super-capacitor recipe did not match");

        ItemStack chargedResult = recipe.assemble(chargedInput, helper.getLevel().registryAccess());
        ItemStack emptyResult = recipe.assemble(emptyInput, helper.getLevel().registryAccess());
        check(chargedResult.is(PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.asItem()),
            "charged recipe produced the wrong item");
        check(emptyResult.is(PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.asItem()),
            "empty recipe produced the wrong item");
        check(PlasticMoldingChamberItem.storedEnergy(chargedResult) == MoldingPowerBridge.capacity(),
            "charged recipe did not store one full chamber buffer");
        check(PlasticMoldingChamberItem.storedEnergy(emptyResult) == 0,
            "empty recipe stored unexpected chamber energy");

        PlasticMoldingChamberBlockEntity chargedChamber = placeChamber(helper, new BlockPos(3, 2, 2));
        check(BlockItem.updateCustomBlockEntityTag(
            helper.getLevel(),
            null,
            chargedChamber.getBlockPos(),
            chargedResult
        ), "charged result did not load its block-entity data");
        tick(chargedChamber, 1);
        check(chargedChamber.energyStored() == MoldingPowerBridge.capacity(),
            "placed charged chamber did not retain full energy");
        check(chargedChamber.getBlockState().getValue(PlasticMoldingChamberBlock.POWERED),
            "placed charged chamber did not select the powered model");

        PlasticMoldingChamberBlockEntity emptyChamber = placeChamber(helper, new BlockPos(11, 2, 2));
        check(!BlockItem.updateCustomBlockEntityTag(
            helper.getLevel(),
            null,
            emptyChamber.getBlockPos(),
            emptyResult
        ), "empty result unexpectedly carried block-entity data");
        tick(emptyChamber, 1);
        check(emptyChamber.energyStored() == 0, "placed empty chamber gained unexpected energy");
        check(!emptyChamber.getBlockState().getValue(PlasticMoldingChamberBlock.POWERED),
            "placed empty chamber selected the powered model");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Multiphase Transcendium bypasses type validation but never the chamber size limit")
    static void multiphaseTranscendiumOnlyOverridesType(ExtendedGameTestHelper helper) {
        PlasticMoldingChamberBlockEntity chamber = placeChamber(helper, new BlockPos(4, 2, 2));
        EditableMoldingModel invalidChest = cubeModel(8.0D).withRequestedType(MoldingProductTypes.CHEST_ID);
        check(chamber.replaceEditableModel(invalidChest, chamber.revision()).accepted(),
            "invalid chest model could not be staged for override testing");
        PlasticMoldingChamberBlockEntity.MachineOutcome missingOverride = chamber.requestLock();
        check(!missingOverride.accepted() && missingOverride.reason().equals("type_cavity_too_small"),
            "invalid chest locked without a type override");

        chamber.inventory().setItem(
            PlasticMoldingChamberBlockEntity.RESOURCE_SLOT,
            ModItems.MULTIPHASE_TRANSCENDIUM.asStack(2)
        );
        check(chamber.requestLock().accepted(), "Multiphase Transcendium did not bypass type validation");
        check(chamber.typeOverrideCommitted() && !chamber.creativeOverrideLocked(),
            "Multiphase Transcendium committed the wrong override mode");
        check(chamber.inventory().getItem(PlasticMoldingChamberBlockEntity.RESOURCE_SLOT).getCount() == 1,
            "confirmed type override did not consume exactly one Multiphase Transcendium");
        check(chamber.unlock().accepted(), "type-overridden model could not be unlocked");
        check(chamber.inventory().getItem(PlasticMoldingChamberBlockEntity.RESOURCE_SLOT).getCount() == 1,
            "unlock refunded the committed Multiphase Transcendium");

        EditableMoldingModel oversizedChest = cubeModel(49.0D).withRequestedType(MoldingProductTypes.CHEST_ID);
        check(chamber.replaceEditableModel(oversizedChest, chamber.revision()).accepted(),
            "oversized chest model could not be staged for limit testing");
        PlasticMoldingChamberBlockEntity.MachineOutcome oversized = chamber.requestLock();
        check(!oversized.accepted() && oversized.reason().equals("model_too_large"),
            "Multiphase Transcendium bypassed the chamber size limit");
        check(chamber.inventory().getItem(PlasticMoldingChamberBlockEntity.RESOURCE_SLOT).getCount() == 1,
            "rejected oversized lock consumed Multiphase Transcendium");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Creative Generator bypasses size, type, and melt limits and is consumed when processing starts")
    static void creativeGeneratorIsConsumedAtProcessingStart(ExtendedGameTestHelper helper) {
        PlasticMoldingChamberBlockEntity chamber = placeChamber(helper, new BlockPos(4, 2, 2));
        EditableMoldingModel oversizedChest = cubeModel(49.0D).withRequestedType(MoldingProductTypes.CHEST_ID);
        check(chamber.replaceEditableModel(oversizedChest, chamber.revision()).accepted(),
            "oversized creative model could not be staged");
        PlasticMoldingChamberBlockEntity.MachineOutcome ordinaryLock = chamber.requestLock();
        check(!ordinaryLock.accepted() && ordinaryLock.reason().equals("model_too_large"),
            "oversized model locked without a Creative Generator");

        chamber.inventory().setItem(
            PlasticMoldingChamberBlockEntity.RESOURCE_SLOT,
            ModBlocks.CREATIVE_GENERATOR.asStack(2)
        );
        check(chamber.requestLock().accepted(), "Creative Generator did not bypass size and type validation");
        check(chamber.creativeOverrideLocked() && chamber.typeOverrideCommitted(),
            "Creative Generator did not commit both overrides");
        check(chamber.inventory().getItem(PlasticMoldingChamberBlockEntity.RESOURCE_SLOT).getCount() == 2,
            "Creative Generator was consumed before processing began");
        check(chamber.energyStorage().receiveEnergy(MoldingPowerBridge.capacity(), false)
                == MoldingPowerBridge.capacity(),
            "creative processing test could not charge the chamber");

        tick(chamber, PlasticMoldingChamberBlockEntity.MOLD_FILL_TICKS + 1);
        check(chamber.machineState() == PlasticMoldingMachineState.PROCESS_READY,
            "creative override without melt did not reach process-ready state");
        check(chamber.batchFluidAmount() == 0, "creative override unexpectedly filled the forming batch");
        MoldingProcessSnapshot snapshot = chamber.beginProcessing().orElseThrow(
            () -> new GameTestAssertException("creative override refused a processing snapshot without melt")
        );
        check(snapshot.creativeOverride() && snapshot.typeOverride(),
            "creative processing snapshot lost its overrides");
        check(chamber.inventory().getItem(PlasticMoldingChamberBlockEntity.RESOURCE_SLOT).getCount() == 1,
            "processing start did not consume exactly one Creative Generator");
        check(chamber.abortProcessing(snapshot), "creative processing snapshot could not be aborted");
        check(chamber.unlock().accepted(), "aborted creative process could not be unlocked");
        check(chamber.inventory().getItem(PlasticMoldingChamberBlockEntity.RESOURCE_SLOT).getCount() == 1,
            "abort or unlock refunded the consumed Creative Generator");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Vertical faces expose only fluid while GUI cursor buckets ignore the main hand")
    static void verticalFluidCapabilitiesAndCursorBucket(ExtendedGameTestHelper helper) {
        PlasticMoldingChamberBlockEntity chamber = placeChamber(helper, new BlockPos(4, 2, 2));
        IFluidHandler top = helper.getLevel().getCapability(
            Capabilities.FluidHandler.BLOCK,
            chamber.getBlockPos(),
            Direction.UP
        );
        IFluidHandler bottom = helper.getLevel().getCapability(
            Capabilities.FluidHandler.BLOCK,
            chamber.getBlockPos(),
            Direction.DOWN
        );
        IFluidHandler horizontal = helper.getLevel().getCapability(
            Capabilities.FluidHandler.BLOCK,
            chamber.getBlockPos(),
            Direction.NORTH
        );
        IItemHandler topItems = helper.getLevel().getCapability(
            Capabilities.ItemHandler.BLOCK,
            chamber.getBlockPos(),
            Direction.UP
        );
        IItemHandler bottomItems = helper.getLevel().getCapability(
            Capabilities.ItemHandler.BLOCK,
            chamber.getBlockPos(),
            Direction.DOWN
        );
        IItemHandler horizontalItems = helper.getLevel().getCapability(
            Capabilities.ItemHandler.BLOCK,
            chamber.getBlockPos(),
            Direction.NORTH
        );
        check(top != null, "top face did not expose the chamber fluid capability");
        check(bottom != null, "bottom face did not expose the chamber fluid capability");
        check(horizontal == null, "horizontal face exposed the chamber fluid capability");
        check(topItems == null && bottomItems == null, "vertical face exposed the chamber item capability");
        check(horizontalItems != null, "horizontal face did not expose the chamber item capability");

        FluidStack verticalMelt = new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), 500);
        check(top.fill(verticalMelt, IFluidHandler.FluidAction.EXECUTE) == 500
                && bottom.drain(500, IFluidHandler.FluidAction.EXECUTE).getAmount() == 500,
            "top input or bottom output failed");
        check(bottom.fill(verticalMelt, IFluidHandler.FluidAction.EXECUTE) == 500
                && top.drain(500, IFluidHandler.FluidAction.EXECUTE).getAmount() == 500,
            "bottom input or top output failed");
        check(chamber.stagingFluidAmount() == 0, "vertical capability round trips changed the staging amount");

        ServerPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setPos(chamber.getBlockPos().getCenter());
        MoldingSessionSnapshot session = chamber.openSession(player, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        ItemStack filledBucket = PlasticraftItems.UNIVERSAL_PLASTIC_MELT_BUCKET.asStack();
        PlasticMeltColor.set(filledBucket, DyeColor.RED);
        player.containerMenu.setCarried(filledBucket);
        check(chamber.applyMachineAction(
            player,
            session.sessionId(),
            chamber.revision(),
            MoldingMachineAction.INTERACT_STAGING_FLUID,
            0
        ).accepted(), "GUI cursor melt bucket was rejected");
        check(chamber.stagingFluidAmount() == 1000, "GUI cursor bucket did not fill the staging tank");
        check(player.containerMenu.getCarried().is(Items.BUCKET), "filled cursor bucket did not become empty");
        check(player.getMainHandItem().is(Items.STONE), "GUI fluid interaction changed the main hand");

        check(chamber.applyMachineAction(
            player,
            session.sessionId(),
            chamber.revision(),
            MoldingMachineAction.INTERACT_STAGING_FLUID,
            0
        ).accepted(), "GUI cursor empty bucket was rejected");
        check(chamber.stagingFluidAmount() == 0, "GUI cursor empty bucket did not drain staging");
        check(player.containerMenu.getCarried().is(PlasticraftItems.UNIVERSAL_PLASTIC_MELT_BUCKET.get())
                && PlasticMeltColor.get(player.containerMenu.getCarried()) == DyeColor.RED,
            "GUI cursor bucket lost its melt colour");
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Shared blueprint owners, revisions, copies and pinned ordering remain player-specific")
    static void sharedBlueprintOwnershipAndPins(ExtendedGameTestHelper helper) throws BlueprintException {
        ServerPlayer owner = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        ServerPlayer reader = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        String suffix = owner.getUUID().toString().substring(0, 8);
        MoldingBlueprint first = MoldingBlueprint.create(
            "Pin First " + suffix,
            cubeModel(6.0D).withName("Pin First " + suffix),
            owner.getUUID(),
            owner.getGameProfile().getName(),
            System.currentTimeMillis()
        );
        MoldingBlueprint second = MoldingBlueprint.create(
            "Pin Second " + suffix,
            cubeModel(7.0D).withName("Pin Second " + suffix),
            owner.getUUID(),
            owner.getGameProfile().getName(),
            System.currentTimeMillis()
        );
        MoldingBlueprintLibrary.StoredBlueprint firstFile = MoldingBlueprintLibrary.saveNew(owner.getServer(), first);
        MoldingBlueprintLibrary.StoredBlueprint secondFile = MoldingBlueprintLibrary.saveNew(owner.getServer(), second);
        MoldingBlueprintLibrary.StoredBlueprint copy = null;
        try {
            check(MoldingBlueprintLibrary.togglePin(owner, firstFile.fileId(), firstFile.blueprint().fileRevision()),
                "first pin did not enter the pinned state");
            check(MoldingBlueprintLibrary.togglePin(owner, secondFile.fileId(), secondFile.blueprint().fileRevision()),
                "second pin did not enter the pinned state");
            List<MoldingBlueprintSummary> ownerList = MoldingBlueprintLibrary.list(owner);
            int firstIndex = summaryIndex(ownerList, firstFile.fileId());
            int secondIndex = summaryIndex(ownerList, secondFile.fileId());
            check(firstIndex >= 0 && secondIndex > firstIndex, "multiple pins did not keep their stable insertion order");
            check(ownerList.get(firstIndex).pinned() && ownerList.get(secondIndex).pinned(), "owner pins were not persisted");
            check(MoldingBlueprintLibrary.list(reader).stream()
                .filter(summary -> summary.fileId().equals(firstFile.fileId()) || summary.fileId().equals(secondFile.fileId()))
                .noneMatch(MoldingBlueprintSummary::pinned), "one player's pins changed another player's ordering");

            BlueprintException denied = expectBlueprintFailure(() -> MoldingBlueprintLibrary.delete(
                reader,
                firstFile.fileId(),
                firstFile.blueprint().fileRevision()
            ));
            check(denied.reason().equals("permission_denied"), "non-owner delete did not fail its permission check");

            copy = MoldingBlueprintLibrary.copy(reader, firstFile.fileId(), firstFile.blueprint().fileRevision());
            check(copy.blueprint().ownerId().equals(reader.getUUID()), "shared copy retained the source owner");
            check(copy.blueprint().modelHash().equals(firstFile.blueprint().modelHash()), "shared copy changed model content");
            check(!copy.fileId().equals(firstFile.fileId()), "shared copy overwrote its source file");
            BlueprintException unsafe = expectBlueprintFailure(() ->
                MoldingBlueprintLibrary.read(owner.getServer(), "../escape.json", 0L)
            );
            check(unsafe.reason().equals("unsafe_filename"), "path traversal did not return a filename error");
        } finally {
            if (copy != null) MoldingBlueprintLibrary.delete(reader, copy.fileId(), copy.blueprint().fileRevision());
            MoldingBlueprintLibrary.togglePin(owner, firstFile.fileId(), firstFile.blueprint().fileRevision());
            MoldingBlueprintLibrary.togglePin(owner, secondFile.fileId(), secondFile.blueprint().fileRevision());
            MoldingBlueprintLibrary.delete(owner, firstFile.fileId(), firstFile.blueprint().fileRevision());
            MoldingBlueprintLibrary.delete(owner, secondFile.fileId(), secondFile.blueprint().fileRevision());
            owner.discard();
            reader.discard();
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Canonical, Minecraft and Blockbench imports preserve shape while unsafe or unsupported input is explicit")
    static void blueprintImportFormatsAndLimits(ExtendedGameTestHelper helper) throws BlueprintException {
        EditableMoldingModel source = cubeModel(9.0D).withName("Canonical Round Trip");
        MoldingBlueprint canonical = MoldingBlueprint.create(
            source,
            new UUID(1L, 2L),
            "Importer",
            1234L
        );
        String canonicalText = MoldingBlueprintCodec.encode(canonical);
        check(canonicalText.indexOf("\"blueprint_format\"") < canonicalText.indexOf("\"owner\""),
            "canonical JSON field order was not stable");
        check(MoldingBlueprintCodec.decode(canonicalText).modelHash().equals(canonical.modelHash()),
            "canonical JSON round trip changed the model hash");
        String mismatchedName = canonicalText.replaceFirst(
            "\"name\": \"Canonical Round Trip\"",
            "\"name\": \"Mismatched Name\""
        );
        BlueprintException mismatch = expectBlueprintFailure(() -> MoldingBlueprintCodec.decode(mismatchedName));
        check(mismatch.reason().equals("invalid_blueprint"), "canonical name mismatch was accepted");

        String minecraft = """
            {
              "name": "Java Plane",
              "elements": [
                {
                  "name": "Panel",
                  "from": [-8, 0, 0],
                  "to": [8, 16, 0],
                  "rotation": {"origin": [0, 8, 0], "axis": "y", "angle": 0}
                }
              ]
            }
            """;
        MoldingBlueprintImporter.ImportPreview minecraftPreview = MoldingBlueprintImporter.inspect(
            "plane.json",
            minecraft
        );
        check(!minecraftPreview.translationRequired(), "centered Minecraft plane requested translation");
        MoldingElement importedPanel = minecraftPreview.model().elements().getFirst();
        check(MoldingCoordinateSystem.toDisplay(importedPanel.from()).equals(new MoldingVec3(-8.0D, 0.0D, 0.0D))
                && MoldingCoordinateSystem.toDisplay(importedPanel.to()).equals(new MoldingVec3(8.0D, 16.0D, 0.0D)),
            "Minecraft coordinates did not map around the chamber origin");
        check(importedPanel.from().z() == importedPanel.to().z(), "Minecraft plane gained thickness");
        check(!MoldingBlueprintImporter.finish(minecraftPreview, false, MoldingVec3.ZERO).isEmpty(),
            "centered Minecraft import became empty");

        String localElementsWithExternalParent = """
            {
              "name": "Catalytic Press Lid",
              "parent": "anvilcraft:block/template_large_block",
              "elements": [
                {"name": "Lid", "from": [-1, -1, -1], "to": [17, 17, 17]}
              ]
            }
            """;
        MoldingBlueprintImporter.ImportPreview lidPreview = MoldingBlueprintImporter.inspect(
            "catalytic_press_lid.json",
            localElementsWithExternalParent
        );
        MoldingElement importedLid = lidPreview.model().elements().getFirst();
        check(!lidPreview.translationRequired(), "centered Java lid requested translation");
        check(MoldingCoordinateSystem.toDisplay(importedLid.from()).equals(new MoldingVec3(-1.0D, -1.0D, -1.0D))
                && MoldingCoordinateSystem.toDisplay(importedLid.to()).equals(new MoldingVec3(17.0D, 17.0D, 17.0D)),
            "Java lid did not remain in the center cell");

        String blockbench = """
            {
              "name": "Grouped Blockbench",
              "elements": [
                {
                  "uuid": "11111111-1111-1111-1111-111111111111",
                  "type": "cube",
                  "name": "Cube",
                  "from": [0, 0, 0],
                  "to": [16, 16, 16],
                  "origin": [8, 8, 8],
                  "rotation": [0, 0, 0]
                }
              ],
              "outliner": [
                {
                  "uuid": "22222222-2222-2222-2222-222222222222",
                  "name": "Root",
                  "origin": [8, 8, 8],
                  "children": ["11111111-1111-1111-1111-111111111111"]
                }
              ]
            }
            """;
        MoldingBlueprintImporter.ImportPreview blockbenchPreview = MoldingBlueprintImporter.inspect(
            "group.bbmodel",
            blockbench
        );
        check(blockbenchPreview.model().groups().size() == 1, "Blockbench group was discarded");
        check(blockbenchPreview.model().elements().getFirst().groupId().isPresent(),
            "Blockbench cube lost its group membership");
        MoldingElement importedCube = blockbenchPreview.model().elements().getFirst();
        check(MoldingCoordinateSystem.toDisplay(importedCube.from()).equals(MoldingVec3.ZERO),
            "Blockbench 0,0,0 did not map to the chamber 0,0,0");
        check(MoldingCoordinateSystem.toDisplay(importedCube.to()).equals(new MoldingVec3(16.0D, 16.0D, 16.0D)),
            "Blockbench cube dimensions changed during origin mapping");

        String fullBlockbench = """
            {
              "name": "Full Blockbench Cube",
              "elements": [
                {
                  "uuid": "33333333-3333-3333-3333-333333333333",
                  "type": "cube",
                  "name": "Full Cube",
                  "from": [0, 0, 0],
                  "to": [48, 48, 48],
                  "origin": [24, 24, 24],
                  "rotation": [0, 0, 0]
                }
              ],
              "outliner": ["33333333-3333-3333-3333-333333333333"]
            }
            """;
        MoldingBlueprintImporter.ImportPreview fullPreview = MoldingBlueprintImporter.inspect(
            "full.bbmodel",
            fullBlockbench
        );
        check(!fullPreview.translationRequired()
                && fullPreview.minimum().equals(MoldingVec3.ZERO)
                && fullPreview.maximum().equals(new MoldingVec3(48.0D, 48.0D, 48.0D)),
            "48x48x48 Blockbench cube did not fit the complete chamber workspace");
        check(!MoldingBlueprintImporter.finish(fullPreview, false, MoldingVec3.ZERO).isEmpty(),
            "48x48x48 Blockbench cube was not importable");

        BlueprintException mesh = expectBlueprintFailure(() -> MoldingBlueprintImporter.inspect(
            "mesh.bbmodel",
            "{\"elements\":[{\"type\":\"mesh\",\"uuid\":\"mesh-1\"}],\"outliner\":[]}"
        ));
        check(mesh.reason().equals("unsupported_element") && mesh.getMessage().contains("mesh"),
            "unsupported Blockbench mesh was not named in the error");
        BlueprintException parent = expectBlueprintFailure(() -> MoldingBlueprintImporter.inspect(
            "parent.json",
            "{\"parent\":\"example:private_parent\"}"
        ));
        check(parent.reason().equals("unsupported_parent"), "external Java parent was not rejected");
        BlueprintException filename = expectBlueprintFailure(() -> MoldingBlueprintImporter.inspect(
            "../escape.json",
            "{}"
        ));
        check(filename.reason().equals("unsafe_filename"), "upload path traversal was not rejected");
        MoldingBlueprintImporter.ImportPreview tooLarge = MoldingBlueprintImporter.inspect(
            "large.json",
            "{\"elements\":[{\"from\":[0,0,0],\"to\":[49,1,1]}]}"
        );
        check(!tooLarge.fitsChamber(), "oversized import was incorrectly marked as chamber-compatible");
        check(!MoldingBlueprintImporter.finish(tooLarge, false, MoldingVec3.ZERO).isEmpty(),
            "oversized model was not importable into the shared library");
        BlueprintException oversizedText = expectBlueprintFailure(() -> MoldingBlueprintCodec.validateText(
            "x".repeat(MoldingBlueprintCodec.MAX_TEXT_BYTES + 1)
        ));
        check(oversizedText.reason().equals("file_too_large"), "oversized upload did not return a size error");
        String deep = "{\"value\":" + "[".repeat(65) + "0" + "]".repeat(65) + "}";
        BlueprintException depth = expectBlueprintFailure(() -> MoldingBlueprintImporter.inspect("deep.json", deep));
        check(depth.reason().equals("json_too_deep"), "over-deep upload did not return a depth error");
        helper.succeed();
    }

    private static PlasticMoldingChamberBlockEntity placeChamber(
        ExtendedGameTestHelper helper,
        BlockPos relativeController
    ) {
        BlockPos controller = helper.absolutePos(relativeController);
        BlockState state = PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.get().defaultBlockState()
            .setValue(PlasticMoldingChamberBlock.FACING, Direction.NORTH);
        check(PlasticMoldingChamberStructure.placeAtomically(helper.getLevel(), controller, state),
            "production test chamber placement failed");
        PlasticMoldingChamberBlockEntity chamber = helper.getLevel().getBlockEntity(controller)
            instanceof PlasticMoldingChamberBlockEntity result ? result : null;
        if (chamber == null) throw new GameTestAssertException("production test chamber block entity was missing");
        tick(chamber, 1);
        check(chamber.structureComplete(), "production test chamber structure was incomplete");
        return chamber;
    }

    private static CraftingInput moldingChamberCraftingInput(ItemStack superCapacitor) {
        ItemStack pipe = ModItems.PIPE.asStack();
        return CraftingInput.of(3, 3, List.of(
            ItemStack.EMPTY, pipe.copy(), ItemStack.EMPTY,
            ModBlocks.FLUID_TANK.asStack(), ModBlocks.STRUCTURE_SCANNER.asStack(), superCapacitor,
            ItemStack.EMPTY, pipe.copy(), ItemStack.EMPTY
        ));
    }

    private static ItemEntity addItem(ExtendedGameTestHelper helper, BlockPos pos, ItemStack stack) {
        Vec3 center = pos.getCenter();
        ItemEntity entity = new ItemEntity(helper.getLevel(), center.x, center.y, center.z, stack);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static EditableMoldingModel cubeModel(double size) {
        return EditableMoldingModel.empty().withElements(List.of(MoldingElement.cube(
            "Production Cube",
            new MoldingVec3(0.0D, 0.0D, 0.0D),
            new MoldingVec3(size, size, size)
        )));
    }

    private static void tick(PlasticMoldingChamberBlockEntity chamber, int ticks) {
        for (int index = 0; index < ticks; index++) {
            PlasticMoldingChamberBlockEntity.serverTick(
                chamber.getLevel(),
                chamber.getBlockPos(),
                chamber.getBlockState(),
                chamber
            );
        }
    }

    private static void assertRegionShapes(PlasticMoldingChamberBlockEntity chamber, int completeLayers) {
        Direction front = chamber.getBlockState().getValue(PlasticMoldingChamberBlock.FACING);
        for (MoldingRegionPart part : MoldingRegionPart.values()) {
            BlockPos region = PlasticMoldingChamberStructure.regionPos(chamber.getBlockPos(), front, part);
            BlockState state = chamber.getLevel().getBlockState(region);
            boolean expected = part.up() < completeLayers;
            check(!state.getShape(chamber.getLevel(), region).isEmpty() == expected,
                "region selection shape did not match layer " + part);
            check(!state.getCollisionShape(chamber.getLevel(), region).isEmpty() == expected,
                "region collision shape did not match layer " + part);
        }
    }

    private static BlueprintException expectBlueprintFailure(BlueprintOperation operation) {
        try {
            operation.run();
        } catch (BlueprintException exception) {
            return exception;
        }
        throw new GameTestAssertException("Expected blueprint operation to fail");
    }

    private static int summaryIndex(List<MoldingBlueprintSummary> summaries, String fileId) {
        for (int index = 0; index < summaries.size(); index++) {
            if (summaries.get(index).fileId().equals(fileId)) return index;
        }
        return -1;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static EditableMoldingModel rotatedCube(
        String name,
        MoldingVec3 from,
        MoldingVec3 to,
        MoldingVec3 rotation
    ) {
        MoldingElement source = MoldingElement.cube(name, from, to);
        MoldingElement rotated = new MoldingElement(
            source.id(),
            source.name(),
            source.groupId(),
            source.from(),
            source.to(),
            new MoldingTransform(
                MoldingVec3.ZERO,
                rotation,
                MoldingVec3.ONE,
                source.transform().pivot()
            ),
            source.visible(),
            source.locked()
        );
        return EditableMoldingModel.empty().withElements(List.of(rotated));
    }

    private static double printedFaceFragmentArea(
        EditableMoldingModel model,
        MoldingPrintingPlan plan,
        int completed
    ) {
        double area = 0.0D;
        MoldingVolumeMask mask = plan.voxelMask();
        int limit = Math.clamp(completed, 0, plan.size());
        List<MoldingConvexHull> hulls = MoldingModelBaker.createManufacturedGeometry(model, 1.0D).collisionHulls();
        for (int index = 0; index < limit; index++) {
            int cell = plan.cellAt(index);
            int x = mask.xOf(cell);
            int y = mask.yOf(cell);
            int z = mask.zOf(cell);
            for (MoldingConvexHull hull : hulls) {
                if (!MoldingPreparedHull.prepare(hull).mayOverlapCell(x, y, z)) continue;
                List<MoldingVec3> vertices = hull.vertices();
                for (MoldingConvexFace face : hull.faces()) {
                    List<MoldingVec3> polygon = new ArrayList<>(face.vertices().size());
                    for (int vertex : face.vertices()) polygon.add(vertices.get(vertex));
                    List<MoldingVec3> clipped = MoldingModelBaker.clipPolygonToCell(polygon, x, y, z);
                    if (clipped.size() < 3) continue;
                    MoldingVec3 accumulator = MoldingVec3.ZERO;
                    for (int vertex = 0; vertex < clipped.size(); vertex++) {
                        accumulator = accumulator.add(clipped.get(vertex).cross(
                            clipped.get((vertex + 1) % clipped.size())
                        ));
                    }
                    area += 0.5D * Math.sqrt(accumulator.lengthSquared());
                }
            }
        }
        return area;
    }

    private static void checkDihedralsCovered(
        EditableMoldingModel model,
        List<MoldingPrintedGeometry.PrintedTriangle> triangles,
        String label
    ) {
        for (EdgeSegment edge : uniqueSourceEdges(model)) {
            double length = Math.sqrt(edge.span().lengthSquared());
            if (length <= 1.0E-4D) continue;
            double covered = coveredLength(edge.start(), edge.end(), triangles);
            check(covered >= 1.8D * length,
                label + " dihedral was not closed by both faces: covered " + covered + " of " + length);
        }
    }

    private static void checkPrintedDihedralCellsCovered(
        EditableMoldingModel model,
        MoldingPrintingPlan plan,
        int completed,
        List<MoldingPrintedGeometry.PrintedTriangle> triangles
    ) {
        MoldingVolumeMask mask = plan.voxelMask();
        int limit = Math.clamp(completed, 0, plan.size());
        List<EdgeSegment> edges = uniqueSourceEdges(model);
        for (int index = 0; index < limit; index++) {
            int cell = plan.cellAt(index);
            int x = mask.xOf(cell);
            int y = mask.yOf(cell);
            int z = mask.zOf(cell);
            for (EdgeSegment edge : edges) {
                EdgeSegment clipped = clipEdgeToCell(edge.start(), edge.end(), x, y, z);
                if (clipped == null) continue;
                double length = Math.sqrt(clipped.span().lengthSquared());
                if (length <= 1.0E-3D) continue;
                double covered = coveredLength(clipped.start(), clipped.end(), triangles);
                check(covered >= 0.9D * length,
                    "partial dihedral gap in cell " + x + "," + y + "," + z
                        + ": covered " + covered + " of " + length);
            }
        }
    }

    private static void checkCapsAvoidOriginalFaces(
        EditableMoldingModel model,
        List<MoldingPrintedGeometry.PrintedTriangle> caps,
        String label
    ) {
        List<MoldingConvexHull> hulls = MoldingModelBaker.createManufacturedGeometry(model, 1.0D).collisionHulls();
        for (MoldingPrintedGeometry.PrintedTriangle cap : caps) {
            for (MoldingConvexHull hull : hulls) {
                List<MoldingVec3> vertices = hull.vertices();
                for (MoldingConvexFace face : hull.faces()) {
                    MoldingVec3 origin = vertices.get(face.vertices().getFirst());
                    MoldingVec3 normal = face.normal();
                    if (Math.abs(cap.normal().dot(normal)) < 0.999D) continue;
                    if (onPlane(cap.first(), origin, normal)
                        && onPlane(cap.second(), origin, normal)
                        && onPlane(cap.third(), origin, normal)) {
                        throw new GameTestAssertException(label + " placed a voxel cap on an original hull face");
                    }
                }
            }
        }
    }

    private static boolean onPlane(MoldingVec3 point, MoldingVec3 origin, MoldingVec3 normal) {
        return Math.abs(point.subtract(origin).dot(normal)) <= 1.0E-4D;
    }

    private static List<EdgeSegment> uniqueSourceEdges(EditableMoldingModel model) {
        List<EdgeSegment> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (EdgeSegment edge : sourceEdges(model)) {
            if (seen.add(canonicalEdgeKey(edge.start(), edge.end()))) unique.add(edge);
        }
        return unique;
    }

    private static String canonicalEdgeKey(MoldingVec3 first, MoldingVec3 second) {
        String left = roundedVertex(first);
        String right = roundedVertex(second);
        return left.compareTo(right) <= 0 ? left + ">" + right : right + ">" + left;
    }

    private static String roundedVertex(MoldingVec3 point) {
        return Math.round(point.x() * 1_000_000.0D)
            + "," + Math.round(point.y() * 1_000_000.0D)
            + "," + Math.round(point.z() * 1_000_000.0D);
    }

    private static List<EdgeSegment> sourceEdges(EditableMoldingModel model) {
        List<EdgeSegment> edges = new ArrayList<>();
        for (MoldingConvexHull hull : MoldingModelBaker.createManufacturedGeometry(model, 1.0D).collisionHulls()) {
            List<MoldingVec3> vertices = hull.vertices();
            for (MoldingConvexFace face : hull.faces()) {
                List<Integer> indices = face.vertices();
                for (int index = 0; index < indices.size(); index++) {
                    MoldingVec3 start = vertices.get(indices.get(index));
                    MoldingVec3 end = vertices.get(indices.get((index + 1) % indices.size()));
                    edges.add(new EdgeSegment(start, end));
                }
            }
        }
        return edges;
    }

    private static double coveredLength(
        MoldingVec3 start,
        MoldingVec3 end,
        List<MoldingPrintedGeometry.PrintedTriangle> triangles
    ) {
        double covered = 0.0D;
        for (MoldingPrintedGeometry.PrintedTriangle triangle : triangles) {
            covered += overlapOnSegment(start, end, triangle.first(), triangle.second());
            covered += overlapOnSegment(start, end, triangle.second(), triangle.third());
            covered += overlapOnSegment(start, end, triangle.third(), triangle.first());
        }
        return covered;
    }

    private static double overlapOnSegment(
        MoldingVec3 start,
        MoldingVec3 end,
        MoldingVec3 from,
        MoldingVec3 to
    ) {
        MoldingVec3 span = end.subtract(start);
        double lengthSquared = span.lengthSquared();
        if (lengthSquared <= 1.0E-12D) return 0.0D;
        if (from.subtract(start).cross(span).lengthSquared() > 1.0E-8D * lengthSquared) return 0.0D;
        if (to.subtract(start).cross(span).lengthSquared() > 1.0E-8D * lengthSquared) return 0.0D;
        double first = Math.clamp(from.subtract(start).dot(span) / lengthSquared, 0.0D, 1.0D);
        double second = Math.clamp(to.subtract(start).dot(span) / lengthSquared, 0.0D, 1.0D);
        return Math.abs(second - first) * Math.sqrt(lengthSquared);
    }

    private static EdgeSegment clipEdgeToCell(MoldingVec3 start, MoldingVec3 end, int x, int y, int z) {
        double minT = 0.0D;
        double maxT = 1.0D;
        minT = Math.max(minT, enterT(start.x(), end.x(), x, x + 1.0D));
        maxT = Math.min(maxT, leaveT(start.x(), end.x(), x, x + 1.0D));
        if (minT > maxT) return null;
        minT = Math.max(minT, enterT(start.y(), end.y(), y, y + 1.0D));
        maxT = Math.min(maxT, leaveT(start.y(), end.y(), y, y + 1.0D));
        if (minT > maxT) return null;
        minT = Math.max(minT, enterT(start.z(), end.z(), z, z + 1.0D));
        maxT = Math.min(maxT, leaveT(start.z(), end.z(), z, z + 1.0D));
        if (minT > maxT) return null;
        MoldingVec3 span = end.subtract(start);
        return new EdgeSegment(start.add(span.scale(minT)), start.add(span.scale(maxT)));
    }

    private static double enterT(double start, double end, double min, double max) {
        double delta = end - start;
        if (Math.abs(delta) <= 1.0E-12D) {
            return start >= min - 1.0E-7D && start <= max + 1.0E-7D
                ? Double.NEGATIVE_INFINITY
                : Double.POSITIVE_INFINITY;
        }
        return delta > 0.0D ? (min - start) / delta : (max - start) / delta;
    }

    private static double leaveT(double start, double end, double min, double max) {
        double delta = end - start;
        if (Math.abs(delta) <= 1.0E-12D) {
            return start >= min - 1.0E-7D && start <= max + 1.0E-7D
                ? Double.POSITIVE_INFINITY
                : Double.NEGATIVE_INFINITY;
        }
        return delta > 0.0D ? (max - start) / delta : (min - start) / delta;
    }

    private record EdgeSegment(MoldingVec3 start, MoldingVec3 end) {
        private MoldingVec3 span() {
            return this.end.subtract(this.start);
        }
    }

    private static void checkIntersectingMaskMatchesCsg(EditableMoldingModel model, String label) {
        var baked = MoldingModelBaker.bake(model);
        MoldingVolumeMask optimized = MoldingModelBaker.createIntersectingVolumeMask(model, baked.volumeMask());
        MoldingVolumeMask expected = baked.volumeMask().copy();
        for (MoldingConvexHull hull : MoldingModelBaker.createManufacturedGeometry(model, 1.0D).collisionHulls()) {
            MoldingConvexHull.Bounds bounds = hull.bounds();
            int minX = Math.clamp((int) Math.floor(bounds.minimum().x()), 0, expected.sizeX() - 1);
            int minY = Math.clamp((int) Math.floor(bounds.minimum().y()), 0, expected.sizeY() - 1);
            int minZ = Math.clamp((int) Math.floor(bounds.minimum().z()), 0, expected.sizeZ() - 1);
            int maxX = Math.clamp((int) Math.ceil(bounds.maximum().x()) - 1, 0, expected.sizeX() - 1);
            int maxY = Math.clamp((int) Math.ceil(bounds.maximum().y()) - 1, 0, expected.sizeY() - 1);
            int maxZ = Math.clamp((int) Math.ceil(bounds.maximum().z()) - 1, 0, expected.sizeZ() - 1);
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (!expected.get(x, y, z) && MoldingModelBaker.convexHullIntersectsCell(hull, x, y, z)) {
                            expected.set(x, y, z);
                        }
                    }
                }
            }
        }
        check(optimized.volume() == expected.volume(),
            label + " intersecting volume diverged: " + optimized.volume() + " vs " + expected.volume());
        for (int y = 0; y < expected.sizeY(); y++) {
            for (int x = 0; x < expected.sizeX(); x++) {
                for (int z = 0; z < expected.sizeZ(); z++) {
                    check(optimized.get(x, y, z) == expected.get(x, y, z),
                        label + " intersecting cell diverged at " + x + "," + y + "," + z);
                }
            }
        }
    }

    private record Placement(BlockPos relativeController, Direction front) {
    }

    @FunctionalInterface
    private interface BlueprintOperation {
        void run() throws BlueprintException;
    }
}
