package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.drone.DroneData;
import dev.anvilcraft.plasticraft.drone.DroneEnergyModel;
import dev.anvilcraft.plasticraft.drone.DroneFlightState;
import dev.anvilcraft.plasticraft.drone.DroneShortageStrategy;
import dev.anvilcraft.plasticraft.drone.tool.DroneToolDefinitions;
import dev.anvilcraft.plasticraft.entity.drone.DroneEntity;
import dev.anvilcraft.plasticraft.init.PlasticraftMenuTypes;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.inventory.DroneMenu;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

/** 覆盖无人机能耗计量、电网充电、悬停/降落状态机、任务报价与策略往返。 */
public final class DroneEnergyGameTests {
    private DroneEnergyGameTests() {
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "A landed drone pays no hover energy")
    static void landedDronePaysNoHoverCost(ExtendedGameTestHelper helper) {
        DroneEntity drone = spawnDrone(helper, DroneToolDefinitions.CONSTRUCTION.id().getPath());
        drone.setEnergy(DroneEnergyModel.capacity());
        helper.startSequence()
            .thenExecuteAfter(20, () -> {
                check(drone.flightState() == DroneFlightState.LANDED, "construction drone did not stay landed");
                check(drone.getEnergy() == DroneEnergyModel.capacity(),
                    "landed drone lost energy: " + drone.getEnergy());
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 200)
    @EmptyTemplate(value = "3x8x3", floor = true)
    @TestHolder(description = "An idle observation drone hovers four blocks up and pays exact hover cost")
    static void observationDroneHoversAndPaysHoverCost(ExtendedGameTestHelper helper) {
        DroneEntity drone = spawnDrone(helper, DroneToolDefinitions.OBSERVATION.id().getPath());
        drone.setEnergy(DroneEnergyModel.capacity());
        int[] recorded = new int[1];
        helper.startSequence()
            .thenWaitUntil(() -> check(drone.flightState() == DroneFlightState.HOVERING,
                "observation drone is not hovering yet: " + drone.flightState()
                    + " y=" + drone.getY() + " energy=" + drone.getEnergy()))
            .thenExecute(() -> {
                // 悬停目标:碰撞箱底面在地面(相对 y=0)之上 4 格。
                double relativeY = drone.getY() - helper.absoluteVec(new Vec3(0.0D, 0.0D, 0.0D)).y;
                check(Math.abs(relativeY - 6.0D) < 0.25D,
                    "hover height is not four blocks above the floor top: " + relativeY);
                recorded[0] = drone.getEnergy();
            })
            .thenExecuteAfter(20, () -> {
                int consumed = recorded[0] - drone.getEnergy();
                check(consumed == 20 * DroneEnergyModel.HOVER_COST_PER_AIR_TICK,
                    "stationary hover cost mismatch: " + consumed);
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "3x8x3", floor = true)
    @TestHolder(description = "An observation drone below the takeoff threshold stays landed")
    static void lowEnergyObservationDroneStaysLanded(ExtendedGameTestHelper helper) {
        DroneEntity drone = spawnDrone(helper, DroneToolDefinitions.OBSERVATION.id().getPath());
        drone.setEnergy((int) DroneEnergyModel.IDLE_TAKEOFF_MINIMUM - 1);
        helper.startSequence()
            .thenExecuteAfter(20, () -> {
                check(drone.flightState() == DroneFlightState.LANDED,
                    "low-energy observation drone took off");
                check(drone.getEnergy() == DroneEnergyModel.IDLE_TAKEOFF_MINIMUM - 1,
                    "landed low-energy drone lost energy");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 300)
    @EmptyTemplate(value = "3x8x3", floor = true)
    @TestHolder(description = "A hovering drone lands at the safe reserve and stops draining")
    static void hoveringDroneLandsAtSafeReserve(ExtendedGameTestHelper helper) {
        DroneEntity drone = spawnDrone(helper, DroneToolDefinitions.OBSERVATION.id().getPath());
        drone.setEnergy(DroneEnergyModel.capacity());
        int[] recorded = new int[1];
        helper.startSequence()
            .thenWaitUntil(() -> check(drone.flightState() == DroneFlightState.HOVERING,
                "observation drone is not hovering yet"))
            .thenExecute(() -> drone.setEnergy(
                (int) DroneEnergyModel.SAFE_LANDING_RESERVE + 5 * DroneEnergyModel.HOVER_COST_PER_AIR_TICK
            ))
            .thenWaitUntil(() -> check(drone.flightState() == DroneFlightState.LANDED,
                "drone did not land after reaching the safe reserve"))
            .thenExecute(() -> {
                check(drone.getEnergy() > 0, "drone drained to zero while landing from four blocks");
                recorded[0] = drone.getEnergy();
            })
            .thenExecuteAfter(10, () -> check(drone.getEnergy() == recorded[0],
                "landed drone kept draining energy"))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 200)
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A drone inside a power grid charges at eight kilowatts")
    static void droneChargesInsidePowerGrid(ExtendedGameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.CREATIVE_GENERATOR.get());
        DroneEntity drone = spawnDroneAt(
            helper,
            new Vec3(2.5D, 2.0D, 1.5D),
            DroneToolDefinitions.CONSTRUCTION.id().getPath()
        );
        drone.setEnergy(0);
        helper.startSequence()
            .thenWaitUntil(() -> check(drone.getEnergy() > 0, "drone did not charge inside the power grid"))
            .thenExecute(() -> {
                int before = drone.getEnergy();
                helper.runAfterDelay(10, () -> {
                    int gained = drone.getEnergy() - before;
                    check(gained == 10 * DroneEnergyModel.chargePerTick(),
                        "charge rate mismatch over ten ticks: " + gained);
                    helper.succeed();
                });
            });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Energy quotes gate job acceptance above the safe landing reserve")
    static void droneQuoteAcceptanceRespectsReserve(ExtendedGameTestHelper helper) {
        DroneEntity drone = spawnDrone(helper, DroneToolDefinitions.CONSTRUCTION.id().getPath());
        DroneEnergyModel.Quote quote = new DroneEnergyModel.Quote(100, 10.0D, 2);
        long expected = 100L * DroneEnergyModel.HOVER_COST_PER_AIR_TICK
            + 10L * DroneEnergyModel.MOVE_COST_PER_BLOCK
            + 2L * DroneToolDefinitions.INSTANT_ACTION_ENERGY_COST;
        check(quote.totalCost(DroneToolDefinitions.INSTANT_ACTION_ENERGY_COST) == expected,
            "quote total cost formula mismatch");

        drone.setEnergy((int) (expected + DroneEnergyModel.SAFE_LANDING_RESERVE));
        check(drone.canAcceptQuote(quote), "drone with exact budget rejected the quote");
        drone.setEnergy((int) (expected + DroneEnergyModel.SAFE_LANDING_RESERVE - 1));
        check(!drone.canAcceptQuote(quote), "drone without the landing reserve accepted the quote");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Shortage strategy edits apply to the entity and survive recovery")
    static void strategySurvivesMenuAndRecovery(ExtendedGameTestHelper helper) {
        DroneEntity drone = spawnDrone(helper, DroneToolDefinitions.DEMOLITION.id().getPath());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DroneMenu menu = new DroneMenu(PlasticraftMenuTypes.DRONE.get(), 1, player.getInventory(), drone);
        menu.applyStrategy(DroneShortageStrategy.SKIP);
        check(drone.shortageStrategy() == DroneShortageStrategy.SKIP,
            "menu strategy edit did not reach the entity");

        ItemStack recovered = drone.getDropStack();
        DroneData data = DroneData.get(recovered).orElseThrow(
            () -> new GameTestAssertException("recovered drone lost its data"));
        check(data.shortageStrategy() == DroneShortageStrategy.SKIP,
            "strategy did not survive recovery into the item");
        helper.succeed();
    }

    private static DroneEntity spawnDrone(ExtendedGameTestHelper helper, String toolPath) {
        return spawnDroneAt(helper, new Vec3(1.5D, 2.0D, 1.5D), toolPath);
    }

    private static DroneEntity spawnDroneAt(ExtendedGameTestHelper helper, Vec3 relativePos, String toolPath) {
        DroneEntity drone = PlasticraftEntities.DRONE.get().create(helper.getLevel());
        check(drone != null, "failed to create drone entity");
        drone.applyDroneData(DroneData.assembled(
            DroneToolDefinitions.get(AnvilcraftPlasticraft.of(toolPath))
                .orElseThrow(() -> new GameTestAssertException("unknown tool " + toolPath))
                .id(),
            ItemStack.EMPTY,
            ItemStack.EMPTY
        ));
        Vec3 position = helper.absoluteVec(relativePos);
        drone.setPos(position.x, position.y, position.z);
        check(helper.getLevel().addFreshEntity(drone), "failed to add drone entity");
        return drone;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
