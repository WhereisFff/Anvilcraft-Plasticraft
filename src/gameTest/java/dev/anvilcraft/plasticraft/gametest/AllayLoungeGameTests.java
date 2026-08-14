package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.allay.AllayDefaultHardHat;
import dev.anvilcraft.plasticraft.allay.AllayShortageStrategy;
import dev.anvilcraft.plasticraft.allay.AllayWorkRecord;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 覆盖悦灵休息室召回节流、方阵、满员拒绝、断电暂停、破坏放出与固定 16 功率。 */
public final class AllayLoungeGameTests {
    private AllayLoungeGameTests() {
    }

    private static final BlockPos LOUNGE_POS = new BlockPos(1, 2, 1);

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "The lounge always requests 16 kW and becomes powered beside a generator")
    static void loungeRequestsFixedPower(ExtendedGameTestHelper helper) {
        AllayLoungeBlockEntity lounge = placeLounge(helper);
        check(lounge.getInputPower() == AllayLoungeBlockEntity.RATED_POWER_KW,
            "lounge must always request 16 kW");
        helper.setBlock(LOUNGE_POS.east(), ModBlocks.CREATIVE_GENERATOR.get());
        helper.startSequence()
            .thenWaitUntil(() -> check(lounge.isPowered(), "lounge did not connect to the power grid"))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 400)
    @EmptyTemplate(value = "5x6x5", floor = true)
    @TestHolder(description = "Recalled allays dock through the single top bay at one per second")
    static void recallDocksAllaysWithThrottle(ExtendedGameTestHelper helper) {
        AllayLoungeBlockEntity lounge = placePoweredLounge(helper);
        WorkingAllayEntity first = AllayGameTests.spawnHatted(helper, new Vec3(0.5D, 2.0D, 1.5D), null);
        WorkingAllayEntity second = AllayGameTests.spawnHatted(helper, new Vec3(2.5D, 2.0D, 1.5D), null);
        check(first.startDockingTo(helper.absolutePos(LOUNGE_POS)), "first allay rejected docking");
        check(second.startDockingTo(helper.absolutePos(LOUNGE_POS)), "second allay rejected docking");
        long[] filledTimes = new long[]{-1L, -1L};
        helper.onEachTick(() -> {
            int filled = lounge.hosted().size();
            long time = helper.getLevel().getGameTime();
            if (filled >= 1 && filledTimes[0] < 0L) filledTimes[0] = time;
            if (filled >= 2 && filledTimes[1] < 0L) filledTimes[1] = time;
        });
        helper.startSequence()
            .thenWaitUntil(() -> check(filledTimes[1] >= 0L, "both allays did not dock in time"))
            .thenExecute(() -> {
                check(first.isRemoved() && second.isRemoved(), "docked allays were not removed");
                long gap = filledTimes[1] - filledTimes[0];
                check(gap >= AllayLoungeBlockEntity.DOCKING_DURATION_TICKS,
                    "second allay docked only " + gap + " ticks after the first");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 260)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "Waiting allays hold a tidy grid formation above a full lounge")
    static void waitingAllaysHoldTidyFormation(ExtendedGameTestHelper helper) {
        BlockPos loungePos = new BlockPos(3, 2, 3);
        helper.setBlock(loungePos, PlasticraftBlocks.ALLAY_LOUNGE.get());
        helper.setBlock(loungePos.east(), ModBlocks.CREATIVE_GENERATOR.get());
        if (!(helper.getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity lounge)) {
            throw new GameTestAssertException("allay lounge block entity is missing");
        }
        fillLounge(lounge);
        List<WorkingAllayEntity> workers = List.of(
            AllayGameTests.spawnHatted(helper, new Vec3(1.5D, 2.0D, 1.5D), null),
            AllayGameTests.spawnHatted(helper, new Vec3(5.5D, 2.0D, 1.5D), null),
            AllayGameTests.spawnHatted(helper, new Vec3(1.5D, 2.0D, 5.5D), null),
            AllayGameTests.spawnHatted(helper, new Vec3(5.5D, 2.0D, 5.5D), null),
            AllayGameTests.spawnHatted(helper, new Vec3(3.5D, 2.0D, 5.5D), null)
        );
        helper.startSequence()
            .thenWaitUntil(() -> check(lounge.isPowered(), "lounge did not connect to the power grid"))
            .thenExecute(() -> {
                for (WorkingAllayEntity worker : workers) {
                    check(worker.startDockingTo(helper.absolutePos(loungePos)), "allay rejected docking");
                }
            })
            .thenExecuteAfter(160, () -> {
                Vec3 center = helper.absoluteVec(new Vec3(3.5D, 0.0D, 3.5D));
                double loungeY = helper.absolutePos(loungePos).getY();
                List<WorkingAllayEntity> waiters = new ArrayList<>();
                int heads = 0;
                for (WorkingAllayEntity worker : workers) {
                    check(!worker.isRemoved(), "an allay docked into a full lounge");
                    double horizontal = Math.hypot(worker.getX() - center.x, worker.getZ() - center.z);
                    if (horizontal < 0.5D && worker.getY() < loungeY + 2.0D) {
                        heads++;
                    } else {
                        waiters.add(worker);
                    }
                }
                check(heads == 1, "expected exactly one allay at the approach point, found " + heads);
                for (WorkingAllayEntity waiter : waiters) {
                    double altitude = waiter.getY() - loungeY;
                    check(Math.abs(altitude - AllayLoungeBlockEntity.FORMATION_BASE_OFFSET_Y) < 0.5D,
                        "waiting allay altitude off formation layer: " + altitude);
                }
                for (int a = 0; a < waiters.size(); a++) {
                    for (int b = a + 1; b < waiters.size(); b++) {
                        double distance = Math.hypot(
                            waiters.get(a).getX() - waiters.get(b).getX(),
                            waiters.get(a).getZ() - waiters.get(b).getZ()
                        );
                        check(distance >= 0.9D, "waiting allays crowded together at distance " + distance);
                    }
                }
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 120)
    @EmptyTemplate(value = "3x6x3", floor = true)
    @TestHolder(description = "A full lounge never overwrites hosted records")
    static void fullLoungeRejectsDocking(ExtendedGameTestHelper helper) {
        AllayLoungeBlockEntity lounge = placePoweredLounge(helper);
        fillLounge(lounge);
        WorkingAllayEntity worker = AllayGameTests.spawnHatted(helper, new Vec3(1.5D, 3.5D, 1.5D), null);
        helper.startSequence()
            .thenWaitUntil(() -> check(lounge.isPowered(), "lounge did not connect to the power grid"))
            .thenExecute(() -> {
                check(!lounge.canAcceptDocking(), "full lounge reported an open bay");
                worker.startDockingTo(helper.absolutePos(LOUNGE_POS));
            })
            .thenExecuteAfter(60, () -> {
                check(!worker.isRemoved(), "allay docked into a full lounge");
                check(lounge.hosted().size() == AllayLoungeBlockEntity.HOST_CAPACITY,
                    "full lounge lost hosted records");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 160)
    @EmptyTemplate(value = "5x6x5", floor = true)
    @TestHolder(description = "Docking pauses without power and resumes from the same progress")
    static void dockingPausesWithoutPower(ExtendedGameTestHelper helper) {
        AllayLoungeBlockEntity lounge = placeLounge(helper);
        helper.setBlock(LOUNGE_POS.east(), ModBlocks.CREATIVE_GENERATOR.get());
        WorkingAllayEntity worker = AllayGameTests.spawnHatted(helper, new Vec3(1.5D, 3.5D, 1.5D), null);
        helper.startSequence()
            .thenWaitUntil(() -> check(lounge.isPowered(), "lounge did not connect to the power grid"))
            .thenExecute(() -> {
                check(lounge.tryDock(worker), "powered lounge rejected docking");
                worker.discard();
                helper.setBlock(LOUNGE_POS.east(), Blocks.AIR);
            })
            .thenExecuteAfter(30, () -> {
                check(lounge.dockingRecord() != null, "unpowered lounge lost its docking record");
                check(lounge.hosted().isEmpty(), "unpowered lounge finished docking anyway");
            })
            .thenExecute(() -> helper.setBlock(LOUNGE_POS.east(), ModBlocks.CREATIVE_GENERATOR.get()))
            .thenWaitUntil(() -> check(lounge.hosted().size() == 1, "docking did not resume after power returned"))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 80)
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Released lounge allays follow the lounge pause or skip setting")
    static void releasedAllayFollowsLoungeStrategy(ExtendedGameTestHelper helper) {
        AllayLoungeBlockEntity lounge = placePoweredLounge(helper);
        lounge.setShortageStrategy(AllayShortageStrategy.SKIP);
        check(lounge.addHosted(fillerRecord()), "failed to host an allay record");
        helper.startSequence()
            .thenWaitUntil(() -> check(lounge.isPowered(), "lounge did not connect to the power grid"))
            .thenExecute(() -> check(lounge.releaseHosted(0), "powered lounge must release the hosted allay"))
            .thenExecuteAfter(5, () -> {
                List<WorkingAllayEntity> workers = helper.getLevel().getEntitiesOfClass(
                    WorkingAllayEntity.class,
                    new AABB(helper.absolutePos(LOUNGE_POS)).inflate(3.0D)
                );
                check(!workers.isEmpty(), "released allay is missing");
                WorkingAllayEntity worker = workers.getFirst();
                check(lounge.getBlockPos().equals(worker.homeLoungePos()), "released allay must remember the lounge");
                check(worker.shortageStrategy() == AllayShortageStrategy.SKIP, "released allay must use the lounge skip setting");
                lounge.setShortageStrategy(AllayShortageStrategy.PAUSE);
                check(worker.shortageStrategy() == AllayShortageStrategy.PAUSE, "changing the lounge must update the released allay");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 80)
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "GUI release fails while the top bay is busy")
    static void releaseHostedFailsWhileBayBusy(ExtendedGameTestHelper helper) {
        AllayLoungeBlockEntity lounge = placePoweredLounge(helper);
        helper.startSequence()
            .thenWaitUntil(() -> check(lounge.isPowered(), "lounge did not connect to the power grid"))
            .thenExecute(() -> {
                check(lounge.addHosted(fillerRecord()), "failed to host the first record");
                check(lounge.addHosted(fillerRecord()), "failed to host the second record");
                check(lounge.tryLaunch(record -> true), "powered lounge must launch the first hosted allay");
                check(lounge.isBayBusy(), "launch must occupy the bay");
                check(!lounge.releaseHosted(0), "GUI release must fail while the bay is busy");
                check(lounge.hosted().size() == 1, "the remaining hosted record must stay");
            })
            .thenExecuteAfter(AllayLoungeBlockEntity.DOCKING_DURATION_TICKS + 1, () -> {
                check(!lounge.isBayBusy(), "the bay must clear after 20 gt");
                check(lounge.releaseHosted(0), "GUI release must succeed after the bay is free");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Breaking the lounge releases hosted allays and drops the disk")
    static void breakingLoungeReleasesAllays(ExtendedGameTestHelper helper) {
        AllayLoungeBlockEntity lounge = placeLounge(helper);
        lounge.addHosted(fillerRecord());
        lounge.items().setStackInSlot(AllayLoungeBlockEntity.DISK_SLOT, new ItemStack(ModItems.STRUCTURE_DISK.get()));
        helper.getLevel().destroyBlock(helper.absolutePos(LOUNGE_POS), true);
        helper.startSequence()
            .thenExecuteAfter(5, () -> {
                List<WorkingAllayEntity> workers = helper.getLevel().getEntitiesOfClass(
                    WorkingAllayEntity.class,
                    new AABB(helper.absolutePos(LOUNGE_POS)).inflate(3.0D)
                );
                check(!workers.isEmpty(), "broken lounge did not release hosted allays");
                List<ItemEntity> disks = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class,
                    new AABB(helper.absolutePos(LOUNGE_POS)).inflate(2.0D),
                    item -> item.getItem().is(ModItems.STRUCTURE_DISK.get())
                );
                check(!disks.isEmpty(), "broken lounge did not drop the structure disk");
            })
            .thenSucceed();
    }

    private static AllayLoungeBlockEntity placeLounge(ExtendedGameTestHelper helper) {
        helper.setBlock(LOUNGE_POS, PlasticraftBlocks.ALLAY_LOUNGE.get());
        if (!(helper.getBlockEntity(LOUNGE_POS) instanceof AllayLoungeBlockEntity lounge)) {
            throw new GameTestAssertException("allay lounge block entity is missing");
        }
        return lounge;
    }

    private static AllayLoungeBlockEntity placePoweredLounge(ExtendedGameTestHelper helper) {
        AllayLoungeBlockEntity lounge = placeLounge(helper);
        helper.setBlock(LOUNGE_POS.east(), ModBlocks.CREATIVE_GENERATOR.get());
        return lounge;
    }

    private static void fillLounge(AllayLoungeBlockEntity lounge) {
        for (int index = 0; index < AllayLoungeBlockEntity.HOST_CAPACITY; index++) {
            check(lounge.addHosted(fillerRecord()), "failed to fill lounge record " + index);
        }
    }

    private static AllayWorkRecord fillerRecord() {
        return new AllayWorkRecord(
            UUID.randomUUID(),
            AllayDefaultHardHat.stack(),
            new ItemStack(Items.SPYGLASS),
            Optional.empty(),
            AllayShortageStrategy.PAUSE,
            List.of(),
            Optional.empty(),
            ItemStack.EMPTY,
            Optional.empty()
        );
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
