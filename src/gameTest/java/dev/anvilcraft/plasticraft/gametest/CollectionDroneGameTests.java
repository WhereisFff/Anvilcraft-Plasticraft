package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.drone.DroneData;
import dev.anvilcraft.plasticraft.drone.DroneEnergyModel;
import dev.anvilcraft.plasticraft.drone.tool.CollectionDroneToolBehavior;
import dev.anvilcraft.plasticraft.drone.tool.DroneToolDefinitions;
import dev.anvilcraft.plasticraft.entity.drone.DroneEntity;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

/**
 * 覆盖收集无人机自由拾取:九格库存、16 格重扫与向所有者卸货。不启动施工任务。
 */
public final class CollectionDroneGameTests {
    private CollectionDroneGameTests() {
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_collection")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Nine collection slots stack, then reject more when every slot is full")
    static void collectionSlotsStackThenRejectWhenFull(ExtendedGameTestHelper helper) {
        DroneEntity drone = spawnCollectionDrone(helper, new Vec3(2.5D, 2.0D, 2.5D), null, DroneEnergyModel.capacity());
        check(drone.tryInsertCollection(new ItemStack(Items.COBBLESTONE, 40)) == 40, "first insert must take 40 cobble");
        check(drone.tryInsertCollection(new ItemStack(Items.COBBLESTONE, 40)) == 40, "stacking insert must fill the first slot and spill");
        check(countInDrone(drone, Items.COBBLESTONE) == 80, "two inserts must leave 80 cobble");
        check(drone.collectionInventory().get(0).getCount() == 64, "first slot must respect max stack");
        check(drone.collectionInventory().get(1).getCount() == 16, "overflow must occupy the next slot");

        for (int slot = 0; slot < 9; slot++) {
            drone.collectionInventory().set(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        check(drone.isCollectionFull(), "nine full cobble stacks must report full");
        check(!drone.canAcceptCollection(new ItemStack(Items.COBBLESTONE)), "a full inventory must reject more cobble");
        check(drone.tryInsertCollection(new ItemStack(Items.DIRT)) == 0, "a full inventory must reject a new item");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_collection")
    @EmptyTemplate(value = "20x4x5", floor = true)
    @TestHolder(description = "Free mode locks a drop inside 16 blocks and ignores one beyond that range")
    static void freeModeScansSixteenBlocksOnly(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DroneEntity drone = spawnCollectionDrone(helper, new Vec3(2.5D, 2.0D, 2.5D), null, DroneEnergyModel.capacity());
        ItemEntity inside = spawnDrop(helper, new Vec3(10.5D, 2.0D, 2.5D), new ItemStack(Items.COBBLESTONE));
        ItemEntity outside = spawnDrop(helper, new Vec3(19.5D, 2.0D, 2.5D), new ItemStack(Items.DIRT));
        ExperienceOrb orb = new ExperienceOrb(level, drone.getX(), drone.getY(), drone.getZ(), 5);
        check(level.addFreshEntity(orb), "failed to spawn experience orb");

        ItemEntity found = CollectionDroneToolBehavior.nextFreeTarget(drone, level);
        check(found == inside, "free mode must lock the drop inside 16 blocks");
        check(drone.distanceTo(outside) > CollectionDroneToolBehavior.FREE_RANGE, "the dirt drop must sit beyond 16 blocks");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_collection")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Unload inserts what the owner can hold and leaves the rest on the drone")
    static void unloadLeavesOverflowOnDrone(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        DroneEntity drone = spawnCollectionDrone(
            helper,
            new Vec3(2.5D, 2.0D, 2.5D),
            player,
            DroneEnergyModel.capacity()
        );
        check(drone.tryInsertCollection(new ItemStack(Items.DIRT, 5)) == 5, "drone must accept five dirt");
        player.getInventory().setItem(0, new ItemStack(Items.DIRT, 63));
        for (int slot = 1; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        drone.unloadCollectionTo(player);
        check(player.getInventory().getItem(0).getCount() == 64, "owner must receive one dirt");
        check(countInDrone(drone, Items.DIRT) == 4, "the four dirt that did not fit must stay on the drone");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 400, batch = "zzz_collection_live")
    @EmptyTemplate(value = "7x4x7", floor = true)
    @TestHolder(description = "A collection drone flies to a drop inside 16 blocks and inhales it")
    static void collectionDroneInhalesNearbyDrop(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.moveTo(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
        DroneEntity[] droneSlot = new DroneEntity[1];
        helper.startSequence().thenExecuteAfter(5, () -> {
            player.setNoGravity(true);
            DroneEntity drone = spawnCollectionDrone(
                helper,
                new Vec3(2.5D, 2.0D, 2.5D),
                player,
                DroneEnergyModel.capacity()
            );
            drone.setNoGravity(true);
            spawnDrop(helper, new Vec3(4.5D, 2.0D, 2.5D), new ItemStack(Items.COBBLESTONE));
            droneSlot[0] = drone;
        }).thenWaitUntil(() -> {
            DroneEntity drone = droneSlot[0];
            check(drone != null, "collection drone was not spawned");
            check(countInDrone(drone, Items.COBBLESTONE) >= 1, "collection drone must inhale the nearby cobble");
            check(
                helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class,
                    drone.getBoundingBox().inflate(CollectionDroneToolBehavior.FREE_RANGE)
                ).stream().noneMatch(item -> item.getItem().is(Items.COBBLESTONE) && item.isAlive()),
                "the inhaled cobble entity must be gone"
            );
        }).thenSucceed();
    }

    private static DroneEntity spawnCollectionDrone(
        ExtendedGameTestHelper helper,
        Vec3 relativePos,
        GameTestPlayer owner,
        int energy
    ) {
        DroneEntity drone = PlasticraftEntities.DRONE.get().create(helper.getLevel());
        check(drone != null, "failed to create collection drone");
        DroneData data = DroneData.assembled(
            DroneToolDefinitions.COLLECTION.id(),
            ItemStack.EMPTY,
            ItemStack.EMPTY
        ).withEnergy(energy);
        if (owner != null) {
            data = data.withOwner(owner.getUUID());
        }
        drone.applyDroneData(data);
        Vec3 position = helper.absoluteVec(relativePos);
        drone.setPos(position.x, position.y, position.z);
        check(helper.getLevel().addFreshEntity(drone), "failed to add collection drone");
        return drone;
    }

    private static ItemEntity spawnDrop(ExtendedGameTestHelper helper, Vec3 relativePos, ItemStack stack) {
        Vec3 position = helper.absoluteVec(relativePos);
        ItemEntity entity = new ItemEntity(helper.getLevel(), position.x, position.y, position.z, stack);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setPickUpDelay(0);
        check(helper.getLevel().addFreshEntity(entity), "failed to spawn drop");
        return entity;
    }

    private static int countInDrone(DroneEntity drone, Item item) {
        int count = 0;
        for (ItemStack stack : drone.collectionInventory()) {
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
