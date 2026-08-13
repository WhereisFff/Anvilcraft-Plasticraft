package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.block.entity.DroneStationBlockEntity;
import dev.anvilcraft.plasticraft.drone.DroneData;
import dev.anvilcraft.plasticraft.drone.DroneEnergyModel;
import dev.anvilcraft.plasticraft.drone.tool.DroneToolDefinitions;
import dev.anvilcraft.plasticraft.entity.drone.DroneEntity;
import dev.anvilcraft.plasticraft.init.PlasticraftDataComponents;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.item.CapacitorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.ArrayList;
import java.util.List;

/** 覆盖无人机站的电网充电、电容器消耗返还、站内充电、顶部单通道停泊与数据保留。 */
public final class DroneStationGameTests {
    private DroneStationGameTests() {
    }

    private static final BlockPos STATION_POS = new BlockPos(1, 2, 1);

    @GameTest(timeoutTicks = 120)
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "The station requests grid power and charges its internal buffer")
    static void stationChargesFromGrid(ExtendedGameTestHelper helper) {
        DroneStationBlockEntity station = placeStation(helper);
        helper.setBlock(STATION_POS.east(), ModBlocks.CREATIVE_GENERATOR.get());
        helper.startSequence()
            .thenWaitUntil(() -> check(station.energy() > 0, "station did not charge from the power grid"))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 60)
    @EmptyTemplate(value = "3x4x3", floor = true)
    @TestHolder(description = "Charged capacitors are consumed whole and empty shells stay in the slot")
    static void capacitorConsumedWholeAndReturned(ExtendedGameTestHelper helper) {
        DroneStationBlockEntity station = placeStation(helper);
        station.items().setStackInSlot(
            DroneStationBlockEntity.CAPACITOR_SLOT,
            new ItemStack(ModItems.CAPACITOR.get())
        );
        helper.startSequence()
            .thenWaitUntil(() -> check(station.energy() == CapacitorItem.ENERGY,
                "station did not absorb the full capacitor energy: " + station.energy()))
            .thenExecute(() -> check(
                station.items().getStackInSlot(DroneStationBlockEntity.CAPACITOR_SLOT)
                    .is(ModItems.CAPACITOR_EMPTY.get()),
                "empty capacitor shell did not stay in the freed slot"))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "3x4x3", floor = true)
    @TestHolder(description = "A capacitor is not consumed when the remaining capacity cannot take it whole")
    static void capacitorRejectedWhenNearlyFull(ExtendedGameTestHelper helper) {
        DroneStationBlockEntity station = placeStation(helper);
        station.setEnergy(DroneStationBlockEntity.capacity() - 100);
        station.items().setStackInSlot(
            DroneStationBlockEntity.CAPACITOR_SLOT,
            new ItemStack(ModItems.CAPACITOR.get())
        );
        helper.startSequence()
            .thenExecuteAfter(10, () -> {
                check(station.items().getStackInSlot(DroneStationBlockEntity.CAPACITOR_SLOT)
                    .is(ModItems.CAPACITOR.get()), "nearly full station consumed a capacitor it cannot hold");
                check(station.energy() == DroneStationBlockEntity.capacity() - 100,
                    "station energy changed without accepting the capacitor");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 60)
    @EmptyTemplate(value = "3x4x3", floor = true)
    @TestHolder(description = "Docked drones charge from the station at the eight kilowatt rate")
    static void stationChargesDockedDrones(ExtendedGameTestHelper helper) {
        DroneStationBlockEntity station = placeStation(helper);
        station.setEnergy(1_000_000);
        ItemStack drone = new ItemStack(PlasticraftItems.CONSTRUCTION_DRONE.get());
        DroneData.set(drone, DroneData.assembled(
            DroneToolDefinitions.CONSTRUCTION.id(),
            ItemStack.EMPTY,
            ItemStack.EMPTY
        ));
        station.items().setStackInSlot(0, drone);
        helper.startSequence()
            .thenExecuteAfter(10, () -> {
                ItemStack stored = station.items().getStackInSlot(0);
                int energy = DroneData.get(stored).orElseThrow(
                    () -> new GameTestAssertException("docked drone lost its data")).energy();
                int perTick = DroneEnergyModel.chargePerTick();
                check(energy >= 8 * perTick && energy <= 12 * perTick,
                    "docked drone charge rate out of range: " + energy);
                check(station.energy() == 1_000_000 - energy,
                    "station energy does not mirror the transferred amount");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 400)
    @EmptyTemplate(value = "5x6x5", floor = true)
    @TestHolder(description = "Recalled drones dock through the single top bay at one per second")
    static void recallDocksDronesWithThrottle(ExtendedGameTestHelper helper) {
        DroneStationBlockEntity station = placeStation(helper);
        station.setEnergy(DroneStationBlockEntity.capacity());
        // 直接下达与召回等价的入库指令;召回按钮的 16 格扫描会波及相邻测试结构,
        // 其覆盖交给游戏内验收。
        DroneEntity first = spawnDrone(helper, new Vec3(0.5D, 2.0D, 1.5D));
        DroneEntity second = spawnDrone(helper, new Vec3(2.5D, 2.0D, 1.5D));
        check(first.startDockingTo(helper.absolutePos(STATION_POS)), "first drone rejected docking order");
        check(second.startDockingTo(helper.absolutePos(STATION_POS)), "second drone rejected docking order");

        long[] filledTimes = new long[]{-1L, -1L};
        helper.onEachTick(() -> {
            int filled = countDroneItems(station);
            long time = helper.getLevel().getGameTime();
            if (filled >= 1 && filledTimes[0] < 0L) filledTimes[0] = time;
            if (filled >= 2 && filledTimes[1] < 0L) filledTimes[1] = time;
        });
        helper.startSequence()
            .thenWaitUntil(() -> check(filledTimes[1] >= 0L, "both drones did not dock in time"))
            .thenExecute(() -> {
                check(first.isRemoved() && second.isRemoved(),
                    "docked drone entities were not removed from the world");
                long gap = filledTimes[1] - filledTimes[0];
                check(gap >= DroneStationBlockEntity.DOCKING_DURATION_TICKS,
                    "second drone docked only " + gap + " ticks after the first");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 260)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "Waiting drones hold a tidy grid formation above the station instead of climbing")
    static void waitingDronesHoldTidyFormation(ExtendedGameTestHelper helper) {
        BlockPos stationPos = new BlockPos(3, 2, 3);
        helper.setBlock(stationPos, PlasticraftBlocks.DRONE_STATION.get());
        if (!(helper.getBlockEntity(stationPos) instanceof DroneStationBlockEntity station)) {
            throw new GameTestAssertException("drone station block entity is missing");
        }
        station.setEnergy(DroneStationBlockEntity.capacity());
        // 站满使泊位始终不可用:队首停在对准点,其余保持方阵,队形长期可观测。
        for (int slot = 0; slot < DroneStationBlockEntity.DRONE_SLOT_COUNT; slot++) {
            ItemStack filler = new ItemStack(PlasticraftItems.OBSERVATION_DRONE.get());
            DroneData.set(filler, DroneData.assembled(
                DroneToolDefinitions.OBSERVATION.id(),
                ItemStack.EMPTY,
                ItemStack.EMPTY
            ));
            station.items().setStackInSlot(slot, filler);
        }
        List<DroneEntity> drones = List.of(
            spawnDrone(helper, new Vec3(1.5D, 2.0D, 1.5D)),
            spawnDrone(helper, new Vec3(5.5D, 2.0D, 1.5D)),
            spawnDrone(helper, new Vec3(1.5D, 2.0D, 5.5D)),
            spawnDrone(helper, new Vec3(5.5D, 2.0D, 5.5D)),
            spawnDrone(helper, new Vec3(3.5D, 2.0D, 5.5D))
        );
        for (DroneEntity drone : drones) {
            check(drone.startDockingTo(helper.absolutePos(stationPos)), "drone rejected docking order");
        }
        Vec3 center = helper.absoluteVec(new Vec3(3.5D, 0.0D, 3.5D));
        double stationY = helper.absolutePos(stationPos).getY();
        // 途中互相越障允许短暂高出格位;只有旧缺陷那种持续互挤抬升才会突破该上限。
        double ceilingY = stationY + DroneStationBlockEntity.FORMATION_BASE_OFFSET_Y + 2.5D;
        helper.onEachTick(() -> {
            for (DroneEntity drone : drones) {
                check(drone.getY() <= ceilingY,
                    "drone climbed to " + drone.getY() + " above the formation ceiling " + ceilingY);
            }
        });
        helper.startSequence()
            .thenExecuteAfter(160, () -> {
                List<DroneEntity> waiters = new ArrayList<>();
                int heads = 0;
                for (DroneEntity drone : drones) {
                    check(!drone.isRemoved(), "a drone docked into a full station");
                    double horizontal = Math.hypot(drone.getX() - center.x, drone.getZ() - center.z);
                    if (horizontal < 0.5D && drone.getY() < stationY + 2.0D) {
                        heads++;
                    } else {
                        waiters.add(drone);
                    }
                }
                check(heads == 1, "expected exactly one drone at the approach point, found " + heads);
                for (DroneEntity waiter : waiters) {
                    double altitude = waiter.getY() - stationY;
                    check(Math.abs(altitude - DroneStationBlockEntity.FORMATION_BASE_OFFSET_Y) < 0.5D,
                        "waiting drone altitude off formation layer: " + altitude);
                }
                for (int a = 0; a < waiters.size(); a++) {
                    for (int b = a + 1; b < waiters.size(); b++) {
                        double distance = Math.hypot(
                            waiters.get(a).getX() - waiters.get(b).getX(),
                            waiters.get(a).getZ() - waiters.get(b).getZ()
                        );
                        check(distance >= 0.9D,
                            "waiting drones crowded together at distance " + distance);
                    }
                }
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 120)
    @EmptyTemplate(value = "3x6x3", floor = true)
    @TestHolder(description = "A full station never overwrites stored drones")
    static void fullStationRejectsDocking(ExtendedGameTestHelper helper) {
        DroneStationBlockEntity station = placeStation(helper);
        station.setEnergy(DroneStationBlockEntity.capacity());
        for (int slot = 0; slot < DroneStationBlockEntity.DRONE_SLOT_COUNT; slot++) {
            ItemStack filler = new ItemStack(PlasticraftItems.OBSERVATION_DRONE.get());
            DroneData.set(filler, DroneData.assembled(
                DroneToolDefinitions.OBSERVATION.id(),
                ItemStack.EMPTY,
                ItemStack.EMPTY
            ));
            station.items().setStackInSlot(slot, filler);
        }
        DroneEntity drone = spawnDrone(helper, new Vec3(1.5D, 3.5D, 1.5D));
        check(!station.canAcceptDocking(), "full station reported an open bay");
        drone.startDockingTo(helper.absolutePos(STATION_POS));
        helper.startSequence()
            .thenExecuteAfter(60, () -> {
                check(!drone.isRemoved(), "drone docked into a full station");
                for (int slot = 0; slot < DroneStationBlockEntity.DRONE_SLOT_COUNT; slot++) {
                    check(station.items().getStackInSlot(slot).is(PlasticraftItems.OBSERVATION_DRONE.get()),
                        "full station overwrote slot " + slot);
                }
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 120)
    @EmptyTemplate(value = "3x6x3", floor = true)
    @TestHolder(description = "Docking pauses without power and resumes from the same progress")
    static void dockingPausesWithoutPower(ExtendedGameTestHelper helper) {
        DroneStationBlockEntity station = placeStation(helper);
        station.setEnergy(1);
        DroneEntity drone = spawnDrone(helper, new Vec3(1.5D, 3.5D, 1.5D));
        check(station.tryDock(drone), "station rejected a docking request with energy available");
        drone.discard();
        station.setEnergy(0);
        helper.startSequence()
            .thenExecuteAfter(30, () -> {
                check(station.dockingData() != null, "unpowered station lost its escrowed docking drone");
                check(countDroneItems(station) == 0, "unpowered station finished docking anyway");
            })
            .thenExecute(() -> station.setEnergy(DroneStationBlockEntity.capacity()))
            .thenWaitUntil(() -> check(countDroneItems(station) == 1,
                "docking did not resume after power returned"))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "3x4x3", floor = true)
    @TestHolder(description = "Breaking the station keeps its internal FE on the dropped item")
    static void stationKeepsEnergyWhenBroken(ExtendedGameTestHelper helper) {
        DroneStationBlockEntity station = placeStation(helper);
        station.setEnergy(123_456);
        helper.getLevel().destroyBlock(helper.absolutePos(STATION_POS), true);
        helper.startSequence()
            .thenExecuteAfter(5, () -> {
                List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class,
                    new AABB(helper.absolutePos(STATION_POS)).inflate(2.0D),
                    item -> item.getItem().is(PlasticraftBlocks.DRONE_STATION.asItem())
                );
                check(!drops.isEmpty(), "broken station dropped no item");
                Integer stored = drops.getFirst().getItem()
                    .get(PlasticraftDataComponents.STATION_ENERGY.get());
                check(stored != null && stored == 123_456,
                    "dropped station item did not keep internal FE: " + stored);
            })
            .thenSucceed();
    }

    private static DroneStationBlockEntity placeStation(ExtendedGameTestHelper helper) {
        helper.setBlock(STATION_POS, PlasticraftBlocks.DRONE_STATION.get());
        if (!(helper.getBlockEntity(STATION_POS) instanceof DroneStationBlockEntity station)) {
            throw new GameTestAssertException("drone station block entity is missing");
        }
        return station;
    }

    private static int countDroneItems(DroneStationBlockEntity station) {
        int count = 0;
        for (int slot = 0; slot < DroneStationBlockEntity.DRONE_SLOT_COUNT; slot++) {
            if (!station.items().getStackInSlot(slot).isEmpty()) count++;
        }
        return count;
    }

    private static DroneEntity spawnDrone(ExtendedGameTestHelper helper, Vec3 relativePos) {
        DroneEntity drone = PlasticraftEntities.DRONE.get().create(helper.getLevel());
        check(drone != null, "failed to create drone entity");
        drone.applyDroneData(DroneData.assembled(
            DroneToolDefinitions.CONSTRUCTION.id(),
            ItemStack.EMPTY,
            ItemStack.EMPTY
        ));
        drone.setEnergy(DroneEnergyModel.capacity());
        Vec3 position = helper.absoluteVec(relativePos);
        drone.setPos(position.x, position.y, position.z);
        check(helper.getLevel().addFreshEntity(drone), "failed to add drone entity");
        return drone;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
