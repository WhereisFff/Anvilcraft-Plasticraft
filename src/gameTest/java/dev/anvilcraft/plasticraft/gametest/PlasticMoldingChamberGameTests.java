package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.block.MoldingRegionPart;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberStructure;
import dev.anvilcraft.plasticraft.block.PlasticMoldingMachineState;
import dev.anvilcraft.plasticraft.block.PlasticMoldingRegionBlock;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockEntities;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.molding.blueprint.BlueprintException;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprint;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintCodec;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintDisk;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintImporter;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintLibrary;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintService;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintSummary;
import dev.anvilcraft.plasticraft.molding.machine.MoldingMachineAction;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPowerBridge;
import dev.anvilcraft.plasticraft.molding.machine.MoldingProcessSnapshot;
import dev.anvilcraft.plasticraft.molding.machine.MoldingProductionMode;
import dev.anvilcraft.plasticraft.molding.machine.MoldingWaitReason;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingCommand;
import dev.anvilcraft.plasticraft.molding.model.MoldingCoordinateSystem;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelBounds;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import dev.anvilcraft.plasticraft.molding.session.MoldingSessionSnapshot;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

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
        check(chamber.clayItemHandler().insertItem(0, new ItemStack(Items.CLAY_BALL, 64), false).getCount() == 30,
            "third-layer refill exceeded the configured slot limit");
        tick(chamber, 1);
        check(chamber.machineState() == PlasticMoldingMachineState.MOLD_READY,
            "twelve active ticks did not complete the mold");
        check(chamber.moldedClayBalls() == 101 && chamber.requiredClayBalls() == 101,
            "multi-refill mold did not preserve the exact clay requirement");

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
    @TestHolder(description = "Editable blueprints use structure disks and reject oversized models only when loaded")
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
            5
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
        String modelBeforeRejectedLoad = chamber.bakedModel().modelHash();
        BlueprintException oversizedLoad = expectBlueprintFailure(() -> MoldingBlueprintService.loadDiskModel(
            player,
            chamber,
            session.sessionId(),
            chamber.revision(),
            MoldingBlueprintDisk.stateToken(oversizedDisk),
            true
        ));
        check(oversizedLoad.reason().equals("model_too_large"),
            "oversized structure disk model did not return the chamber size error");
        check(chamber.bakedModel().modelHash().equals(modelBeforeRejectedLoad),
            "rejected oversized structure disk load changed the chamber model");
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "Resource slot empties melt buckets and consumes at most one capacitor per tick")
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

    private record Placement(BlockPos relativeController, Direction front) {
    }

    @FunctionalInterface
    private interface BlueprintOperation {
        void run() throws BlueprintException;
    }
}
