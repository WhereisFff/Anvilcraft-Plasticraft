package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.allay.AllayDefaultHardHat;
import dev.anvilcraft.plasticraft.allay.AllayHardHats;
import dev.anvilcraft.plasticraft.allay.AllayShortageStrategy;
import dev.anvilcraft.plasticraft.allay.AllayWorkRecord;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import dev.anvilcraft.plasticraft.blueprint.ConstructionPermission;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 覆盖悦灵休息室召回节流、稳定队列、满员拒绝、破坏打包与磁铁优先出库。 */
public final class AllayLoungeGameTests {
    private AllayLoungeGameTests() {
    }

    private static final BlockPos LOUNGE_POS = new BlockPos(1, 2, 1);

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Lounge access requires its owner or a current teammate and never claims an unowned lounge")
    static void loungeAccessRechecksTeamMembership(ExtendedGameTestHelper helper) {
        AllayLoungeBlockEntity lounge = placeLounge(helper);
        lounge.setOwner(null);
        GameTestPlayer owner = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        GameTestPlayer teammate = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);

        check(lounge.owner() == null, "the fixture lounge must start unowned");
        check(!ConstructionPermission.canUseLounge(owner, lounge), "an unowned lounge was usable");
        check(lounge.owner() == null, "checking access claimed an unowned lounge");
        WorkingAllayEntity worker = AllayGameTests.spawnHattedForOwner(
            helper,
            new Vec3(2.5D, 2.0D, 2.5D),
            owner.getUUID()
        );
        check(!lounge.canHost(worker), "an unowned lounge accepted a working allay");
        check(lounge.owner() == null, "checking worker admission claimed an unowned lounge");

        lounge.setOwner(owner.getUUID());
        check(ConstructionPermission.canUseLounge(owner, lounge), "the lounge owner was denied access");
        check(!ConstructionPermission.canUseLounge(teammate, lounge), "a stranger used the lounge");
        try {
            ConstructionPermission.setCollaboratorProvider((server, first, second) ->
                first.equals(owner.getUUID()) && second.equals(teammate.getUUID())
                    || first.equals(teammate.getUUID()) && second.equals(owner.getUUID())
            );
            check(lounge.setShortageStrategy(teammate, AllayShortageStrategy.SKIP),
                "a current teammate could not change lounge settings");
        } finally {
            ConstructionPermission.setCollaboratorProvider(null);
        }
        check(!lounge.setShortageStrategy(teammate, AllayShortageStrategy.PAUSE),
            "a former teammate retained lounge access");
        check(lounge.shortageStrategy() == AllayShortageStrategy.SKIP,
            "the denied former teammate changed lounge settings");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Collector launch prefers a hosted magnet over an empty-hand allay")
    static void tryLaunchCollectorPrefersMagnet(ExtendedGameTestHelper helper) {
        AllayLoungeBlockEntity lounge = placeLounge(helper);
        UUID owner = AllayGameTests.TEST_OWNER;
        check(lounge.addHosted(emptyHandRecord(owner)), "failed to host the empty-hand collector");
        check(lounge.addHosted(magnetRecord(owner)), "failed to host the magnet collector");
        check(ConstructionJobController.tryLaunchCollector(lounge), "lounge must launch a collector");
        check(lounge.hosted().size() == 1, "tryLaunchCollector must remove exactly one hosted record");
        check(
            lounge.hosted().getFirst().heldTool().isEmpty(),
            "the remaining hosted record must be the empty-hand allay"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 500)
    @EmptyTemplate(value = "9x8x9", floor = true)
    @TestHolder(description = "Queued allays use stable distance order and dock through the single bay one per second")
    static void recallDocksAllaysWithThrottle(ExtendedGameTestHelper helper) {
        BlockPos loungePos = new BlockPos(4, 2, 4);
        AllayLoungeBlockEntity lounge = placeLounge(helper, loungePos);
        List<WorkingAllayEntity> workers = spawnQueueWorkers(helper, 10);
        BlockPos absoluteLoungePos = helper.absolutePos(loungePos);
        for (WorkingAllayEntity worker : workers) {
            check(worker.startDockingTo(absoluteLoungePos), "allay rejected docking");
        }
        Vec3 approach = lounge.dockApproachPoint();
        WorkingAllayEntity expectedHead = workers.stream()
            .min(Comparator
                .comparingDouble((WorkingAllayEntity worker) -> worker.position().distanceToSqr(approach))
                .thenComparing(WorkingAllayEntity::getUUID))
            .orElseThrow();
        check(expectedHead != workers.getFirst(), "queue-order setup must register a farther allay first");
        for (WorkingAllayEntity worker : workers) {
            check(
                lounge.assignDockTarget(worker).head() == (worker == expectedHead),
                "docking queue head did not follow initial distance order"
            );
        }
        List<Long> filledTimes = new ArrayList<>();
        Map<UUID, Vec3> waitingTargets = new HashMap<>();
        helper.onEachTick(() -> {
            int filled = lounge.hosted().size();
            long time = helper.getLevel().getGameTime();
            while (filledTimes.size() < filled) {
                filledTimes.add(time);
            }
            for (WorkingAllayEntity worker : workers) {
                if (worker.isRemoved()) continue;
                AllayLoungeBlockEntity.DockAssignment assignment = lounge.assignDockTarget(worker);
                if (assignment.head()) continue;
                Vec3 previous = waitingTargets.putIfAbsent(worker.getUUID(), assignment.target());
                check(previous == null || previous.distanceToSqr(assignment.target()) < 1.0E-8D,
                    "a non-head allay was reassigned while the queue advanced");
            }
        });
        helper.startSequence()
            .thenWaitUntil(() -> check(lounge.hosted().size() == workers.size(),
                "queued allays did not all dock in time"))
            .thenExecute(() -> {
                check(workers.stream().allMatch(WorkingAllayEntity::isRemoved),
                    "a docked allay remained in the world");
                check(filledTimes.size() == workers.size(), "not every docking completion was observed");
                check(!waitingTargets.isEmpty(), "the multi-allay queue never assigned waiting slots");
                for (int index = 1; index < filledTimes.size(); index++) {
                    long gap = filledTimes.get(index) - filledTimes.get(index - 1);
                    check(gap >= AllayLoungeBlockEntity.DOCKING_DURATION_TICKS,
                        "allay " + index + " docked only " + gap + " ticks after its predecessor");
                }
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 300)
    @EmptyTemplate(value = "9x8x9", floor = true)
    @TestHolder(description = "Waiting allays hold a separated ring around the single lounge approach")
    static void waitingAllaysHoldTidyFormation(ExtendedGameTestHelper helper) {
        BlockPos loungePos = new BlockPos(4, 2, 4);
        AllayLoungeBlockEntity lounge = placeLounge(helper, loungePos);
        fillLounge(lounge);
        List<WorkingAllayEntity> workers = spawnQueueWorkers(helper, 12);
        helper.startSequence()
            .thenExecute(() -> {
                for (WorkingAllayEntity worker : workers) {
                    check(worker.startDockingTo(helper.absolutePos(loungePos)), "allay rejected docking");
                }
            })
            .thenExecuteAfter(180, () -> {
                Vec3 center = lounge.dockApproachPoint();
                double loungeY = helper.absolutePos(loungePos).getY();
                List<WorkingAllayEntity> waiters = new ArrayList<>();
                int heads = 0;
                for (WorkingAllayEntity worker : workers) {
                    check(!worker.isRemoved(), "an allay docked into a full lounge");
                    AllayLoungeBlockEntity.DockAssignment assignment = lounge.assignDockTarget(worker);
                    check(worker.position().distanceToSqr(assignment.target()) < 0.25D,
                        "an allay did not settle at its assigned queue target");
                    check(!worker.navigator().hasPath(),
                        "a settled docking allay retained stale navigator waypoints");
                    check(!worker.hasFlightTaskFor(assignment.target()),
                        "a settled docking allay retained its completed flight task");
                    if (assignment.head()) {
                        heads++;
                    } else {
                        waiters.add(worker);
                    }
                }
                check(heads == 1, "expected exactly one allay at the approach point, found " + heads);
                for (WorkingAllayEntity waiter : waiters) {
                    double horizontal = Math.hypot(waiter.getX() - center.x, waiter.getZ() - center.z);
                    check(horizontal >= 2.5D,
                        "waiting allay crowded the lounge approach at radius " + horizontal);
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
                        check(distance >= 1.1D, "waiting allays crowded together at distance " + distance);
                    }
                }
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 120)
    @EmptyTemplate(value = "3x6x3", floor = true)
    @TestHolder(description = "A full lounge never overwrites hosted records")
    static void fullLoungeRejectsDocking(ExtendedGameTestHelper helper) {
        AllayLoungeBlockEntity lounge = placeLounge(helper);
        fillLounge(lounge);
        WorkingAllayEntity worker = AllayGameTests.spawnHattedForOwner(
            helper,
            new Vec3(1.5D, 3.5D, 1.5D),
            AllayGameTests.TEST_OWNER
        );
        helper.startSequence()
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

    @GameTest(timeoutTicks = 120)
    @EmptyTemplate(value = "5x6x5", floor = true)
    @TestHolder(description = "Duplicate hosted records collapse and the matching world allay can return")
    static void duplicateHostedRecordsDoNotBlockReturn(ExtendedGameTestHelper helper) {
        AllayLoungeBlockEntity lounge = placeLounge(helper);
        WorkingAllayEntity worker = AllayGameTests.spawnHattedForOwner(
            helper,
            new Vec3(1.5D, 3.5D, 1.5D),
            AllayGameTests.TEST_OWNER
        );
        AllayWorkRecord stale = hostedRecord(
            worker.getUUID(),
            new ItemStack(Items.SPYGLASS),
            Optional.of(AllayGameTests.TEST_OWNER)
        );
        check(lounge.addHosted(stale), "failed to add the stale hosted record");
        check(!lounge.addHosted(stale), "lounge accepted a duplicate hosted UUID through its public API");

        CompoundTag saved = lounge.saveWithoutMetadata(helper.getLevel().registryAccess());
        ListTag hosted = saved.getList("Hosted", Tag.TAG_COMPOUND);
        hosted.add(hosted.getCompound(0).copy());
        hosted.add(hosted.getCompound(0).copy());
        lounge.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(lounge.hosted().size() == 1, "duplicate hosted UUIDs survived NBT loading");
        check(worker.startDockingTo(helper.absolutePos(LOUNGE_POS)), "matching world allay rejected docking");
        check(lounge.hosted().isEmpty(), "stale hosted copy was not removed when the world allay queued");

        helper.startSequence()
            .thenWaitUntil(() -> check(
                worker.isRemoved() && lounge.hosted().size() == 1,
                "matching world allay was not stored exactly once"
            ))
            .thenExecute(() -> {
                check(lounge.hosted().getFirst().entityId().equals(worker.getUUID()),
                    "returned allay record has the wrong UUID");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 180)
    @EmptyTemplate(value = "9x6x9", floor = true)
    @TestHolder(description = "A returning allay already sealed inside a room is recalled to its lounge")
    static void sealedReturningAllayIsRecalled(ExtendedGameTestHelper helper) {
        AllayLoungeBlockEntity lounge = placeLounge(helper);
        BlockPos chamber = new BlockPos(5, 2, 5);
        helper.setBlock(chamber.west(), Blocks.STONE);
        helper.setBlock(chamber.east(), Blocks.STONE);
        helper.setBlock(chamber.north(), Blocks.STONE);
        helper.setBlock(chamber.south(), Blocks.STONE);
        helper.setBlock(chamber.above(), Blocks.STONE);
        WorkingAllayEntity worker = AllayGameTests.spawnHattedForOwner(
            helper,
            new Vec3(5.5D, 2.01D, 5.5D),
            AllayGameTests.TEST_OWNER
        );
        check(worker.startDockingTo(helper.absolutePos(LOUNGE_POS)), "sealed allay rejected docking");

        helper.startSequence()
            .thenWaitUntil(() -> check(
                worker.isRemoved() && lounge.hosted().size() == 1,
                "sealed allay was not recalled and stored"
            ))
            .thenExecute(() -> check(
                lounge.hosted().getFirst().entityId().equals(worker.getUUID()),
                "recalled lounge record has the wrong UUID"
            ))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 100)
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A manually released allay becomes unbound and does not return to its lounge")
    static void manuallyReleasedAllayBecomesUnbound(ExtendedGameTestHelper helper) {
        AllayLoungeBlockEntity lounge = placeLounge(helper);
        lounge.setShortageStrategy(AllayShortageStrategy.SKIP);
        check(lounge.addHosted(fillerRecord()), "failed to host an allay record");
        WorkingAllayEntity[] released = new WorkingAllayEntity[1];
        helper.startSequence()
            .thenExecute(() -> check(lounge.releaseHosted(0), "lounge must release the hosted allay"))
            .thenExecuteAfter(5, () -> {
                List<WorkingAllayEntity> workers = helper.getLevel().getEntitiesOfClass(
                    WorkingAllayEntity.class,
                    new AABB(helper.absolutePos(LOUNGE_POS)).inflate(3.0D)
                );
                check(!workers.isEmpty(), "released allay is missing");
                WorkingAllayEntity worker = workers.getFirst();
                released[0] = worker;
                check(worker.homeLoungePos() == null, "a manually released allay must not retain a home lounge");
                check(worker.originLoungePos() == null, "a manually released allay must not retain a transfer origin");
                check(worker.transitJobId().isEmpty(), "a manually released allay must not retain a transfer job");
                check(worker.shortageStrategy() == AllayShortageStrategy.PAUSE,
                    "an unbound allay must use the fixed PAUSE strategy");
            })
            .thenExecuteAfter(35, () -> {
                // 放出的悦灵会像原版一样飞开,必须精确引用自己那只:按休息室周边扫描会把正常游荡误判成回库
                check(!released[0].isRemoved(), "an unbound released allay was recalled into the lounge");
                check(released[0].homeLoungePos() == null, "an unbound released allay rebound itself to the lounge");
                check(lounge.hosted().isEmpty(), "manual release unexpectedly recreated a hosted record");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 100)
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "A released allay killed during its death animation drops one hard hat and never docks")
    static void releasedAllayDeathDoesNotRedockOrDuplicateHat(ExtendedGameTestHelper helper) {
        AllayLoungeBlockEntity lounge = placeLounge(helper);
        check(lounge.addHosted(fillerRecord()), "failed to host an allay record");
        helper.startSequence()
            .thenExecute(() -> check(lounge.releaseHosted(0), "lounge must release the hosted allay"))
            .thenExecute(() -> {
                List<WorkingAllayEntity> workers = helper.getLevel().getEntitiesOfClass(
                    WorkingAllayEntity.class,
                    new AABB(helper.absolutePos(LOUNGE_POS)).inflate(3.0D)
                );
                check(!workers.isEmpty(), "released allay is missing");
                WorkingAllayEntity worker = workers.getFirst();
                check(worker.hurt(helper.getLevel().damageSources().generic(), 100.0F),
                    "released allay did not accept lethal damage");
            })
            .thenExecuteAfter(40, () -> {
                check(lounge.hosted().isEmpty(), "a dying released allay was put back into the lounge");
                List<ItemEntity> hats = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class,
                    new AABB(helper.absolutePos(LOUNGE_POS)).inflate(3.0D),
                    item -> AllayHardHats.isHardHat(item.getItem())
                );
                check(hats.size() == 1, "released allay death dropped " + hats.size() + " hard hats instead of one");
                check(helper.getLevel().getEntitiesOfClass(
                    WorkingAllayEntity.class,
                    new AABB(helper.absolutePos(LOUNGE_POS)).inflate(3.0D)
                ).isEmpty(), "the dead released allay remained in the world");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 80)
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "GUI releases the selected allays immediately without occupying or resetting the bay")
    static void releaseHostedBypassesBusyBay(ExtendedGameTestHelper helper) {
        AllayLoungeBlockEntity lounge = placeLounge(helper);
        GameTestPlayer owner = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        lounge.setOwner(owner.getUUID());
        AllayWorkRecord first = emptyHandRecord(owner.getUUID());
        AllayWorkRecord second = emptyHandRecord(owner.getUUID());
        AllayWorkRecord third = emptyHandRecord(owner.getUUID());
        helper.startSequence()
            .thenExecute(() -> {
                check(lounge.addHosted(emptyHandRecord(owner.getUUID())), "failed to host the automatic launch record");
                check(lounge.addHosted(first), "failed to host the first manual record");
                check(lounge.addHosted(second), "failed to host the second manual record");
                check(lounge.addHosted(third), "failed to host the third manual record");
                WorkingAllayEntity launched = lounge.tryLaunchFor(record -> true);
                if (launched == null) {
                    throw new GameTestAssertException("lounge must launch the first hosted allay");
                }
                check(lounge.isBayBusy(), "launch must occupy the bay");
                check(lounge.releaseHosted(owner, 1, second.entityId()), "GUI release must bypass the busy bay");
                check(lounge.releaseHosted(owner, 2, third.entityId()), "a shifted card must release the clicked allay");
                check(!lounge.releaseHosted(owner, 0, second.entityId()), "a repeated click must not release a neighbour");
                check(lounge.hosted().size() == 1 && lounge.hosted().getFirst().entityId().equals(first.entityId()),
                    "manual release must leave the unclicked allay hosted");
                for (AllayWorkRecord record : List.of(second, third)) {
                    check(helper.getLevel().getEntity(record.entityId()) instanceof WorkingAllayEntity worker
                        && worker.homeLoungePos() == null, "the selected allay must immediately exist unbound in the world");
                }
                check(lounge.isBayBusy(), "manual release must preserve the automatic launch bay");
                check(!lounge.tryLaunch(record -> true), "automatic launch must still respect the busy bay");
                // 出库通道的吞吐契约与出库悦灵之后干什么无关:任何无活可做的悦灵都会自行返库
                // 再占一次通道,留着它就测不到通道自己是否按 20 gt 释放
                launched.discard();
            })
            .thenExecuteAfter(10, () -> check(lounge.releaseHosted(owner, 0, first.entityId()),
                "a later manual click must still bypass the busy bay"))
            .thenExecuteAfter(AllayLoungeBlockEntity.DOCKING_DURATION_TICKS - 9, () -> {
                check(!lounge.isBayBusy(), "the bay must clear after 20 gt");
                check(lounge.addHosted(emptyHandRecord(owner.getUUID())), "failed to host an idle-bay record");
                check(lounge.releaseHosted(0), "GUI release must succeed after the bay is free");
                check(!lounge.isBayBusy(), "manual release must not occupy a free bay");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Breaking the lounge packs hosted allays into the dropped item")
    static void breakingLoungePacksAllays(ExtendedGameTestHelper helper) {
        AllayLoungeBlockEntity lounge = placeLounge(helper);
        List<UUID> hostedIds = new ArrayList<>();
        for (int index = 0; index < AllayLoungeBlockEntity.HOST_CAPACITY; index++) {
            UUID id = UUID.randomUUID();
            hostedIds.add(id);
            check(lounge.addHosted(hostedRecord(id, new ItemStack(Items.SPYGLASS), Optional.of(AllayGameTests.TEST_OWNER))),
                "failed to host allay record " + index);
        }
        lounge.items().setStackInSlot(AllayLoungeBlockEntity.DISK_SLOT, new ItemStack(ModItems.STRUCTURE_DISK.get()));
        helper.getLevel().destroyBlock(helper.absolutePos(LOUNGE_POS), true);
        helper.startSequence()
            .thenExecuteAfter(5, () -> {
                List<WorkingAllayEntity> workers = helper.getLevel().getEntitiesOfClass(
                    WorkingAllayEntity.class,
                    new AABB(helper.absolutePos(LOUNGE_POS)).inflate(3.0D)
                );
                check(workers.isEmpty(), "broken lounge released hosted allays into the world");
                List<ItemEntity> lounges = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class,
                    new AABB(helper.absolutePos(LOUNGE_POS)).inflate(2.0D),
                    item -> item.getItem().is(PlasticraftBlocks.ALLAY_LOUNGE.asItem())
                );
                check(lounges.size() == 1, "broken lounge did not drop exactly one packed lounge");
                ItemStack packed = lounges.getFirst().getItem().copy();
                CustomData data = packed.get(DataComponents.BLOCK_ENTITY_DATA);
                check(data != null, "packed lounge is missing block entity data");
                CompoundTag saved = data.copyTag();
                ListTag hosted = saved.getList("Hosted", Tag.TAG_COMPOUND);
                check(hosted.size() == AllayLoungeBlockEntity.HOST_CAPACITY,
                    "packed lounge stored " + hosted.size() + " hosted records instead of 16");
                check(saved.contains("Items", Tag.TAG_COMPOUND), "packed lounge lost its structure disk slot");

                BlockPos restoredPos = new BlockPos(3, 2, 1);
                helper.setBlock(restoredPos, PlasticraftBlocks.ALLAY_LOUNGE.get());
                check(BlockItem.updateCustomBlockEntityTag(
                        helper.getLevel(),
                        null,
                        helper.absolutePos(restoredPos),
                        packed
                    ), "packed lounge data was not applied on placement");
                if (!(helper.getBlockEntity(restoredPos) instanceof AllayLoungeBlockEntity restored)) {
                    throw new GameTestAssertException("restored lounge block entity is missing");
                }
                check(restored.hosted().size() == AllayLoungeBlockEntity.HOST_CAPACITY,
                    "restored lounge did not recover all 16 hosted records");
                check(restored.hosted().stream().map(AllayWorkRecord::entityId).toList().equals(hostedIds),
                    "restored lounge changed the hosted allay UUID order");
            })
            .thenSucceed();
    }

    private static AllayLoungeBlockEntity placeLounge(ExtendedGameTestHelper helper) {
        return placeLounge(helper, LOUNGE_POS);
    }

    private static AllayLoungeBlockEntity placeLounge(ExtendedGameTestHelper helper, BlockPos loungePos) {
        helper.setBlock(loungePos, PlasticraftBlocks.ALLAY_LOUNGE.get());
        if (!(helper.getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity lounge)) {
            throw new GameTestAssertException("allay lounge block entity is missing");
        }
        lounge.setOwner(AllayGameTests.TEST_OWNER);
        return lounge;
    }

    private static List<WorkingAllayEntity> spawnQueueWorkers(ExtendedGameTestHelper helper, int count) {
        List<Vec3> spawnPositions = List.of(
            new Vec3(1.5D, 2.0D, 1.5D),
            new Vec3(3.5D, 2.0D, 1.5D),
            new Vec3(5.5D, 2.0D, 1.5D),
            new Vec3(7.5D, 2.0D, 1.5D),
            new Vec3(7.5D, 2.0D, 3.5D),
            new Vec3(7.5D, 2.0D, 5.5D),
            new Vec3(7.5D, 2.0D, 7.5D),
            new Vec3(5.5D, 2.0D, 7.5D),
            new Vec3(3.5D, 2.0D, 7.5D),
            new Vec3(1.5D, 2.0D, 7.5D),
            new Vec3(1.5D, 2.0D, 5.5D),
            new Vec3(1.5D, 2.0D, 3.5D)
        );
        check(count <= spawnPositions.size(), "queue test requested too many workers");
        List<WorkingAllayEntity> workers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            workers.add(AllayGameTests.spawnHattedForOwner(
                helper,
                spawnPositions.get(index),
                AllayGameTests.TEST_OWNER
            ));
        }
        return workers;
    }

    private static void fillLounge(AllayLoungeBlockEntity lounge) {
        for (int index = 0; index < AllayLoungeBlockEntity.HOST_CAPACITY; index++) {
            check(lounge.addHosted(fillerRecord()), "failed to fill lounge record " + index);
        }
    }

    private static AllayWorkRecord fillerRecord() {
        return hostedRecord(new ItemStack(Items.SPYGLASS), Optional.of(AllayGameTests.TEST_OWNER));
    }

    private static AllayWorkRecord emptyHandRecord(UUID owner) {
        return hostedRecord(ItemStack.EMPTY, Optional.of(owner));
    }

    private static AllayWorkRecord magnetRecord(UUID owner) {
        return hostedRecord(new ItemStack(ModItems.MAGNET.get()), Optional.of(owner));
    }

    private static AllayWorkRecord hostedRecord(ItemStack tool, Optional<UUID> owner) {
        return hostedRecord(UUID.randomUUID(), tool, owner);
    }

    private static AllayWorkRecord hostedRecord(UUID entityId, ItemStack tool, Optional<UUID> owner) {
        return new AllayWorkRecord(
            entityId,
            AllayDefaultHardHat.stack(),
            tool,
            owner,
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
