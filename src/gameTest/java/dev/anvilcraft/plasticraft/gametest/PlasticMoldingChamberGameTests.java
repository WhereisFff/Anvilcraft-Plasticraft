package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.block.MoldingRegionPart;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberStructure;
import dev.anvilcraft.plasticraft.block.PlasticMoldingRegionBlock;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.molding.model.MoldingCommand;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import dev.anvilcraft.plasticraft.molding.session.MoldingSessionSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
            BlockState controllerState = ModBlocks.PLASTIC_MOLDING_CHAMBER.get().defaultBlockState()
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
                check(state.is(ModBlocks.PLASTIC_MOLDING_REGION.get()), "region part was missing at " + region);
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
        BlockState state = ModBlocks.PLASTIC_MOLDING_CHAMBER.get().defaultBlockState()
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
        BlockState state = ModBlocks.PLASTIC_MOLDING_CHAMBER.get().defaultBlockState()
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

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private record Placement(BlockPos relativeController, Direction front) {
    }
}
