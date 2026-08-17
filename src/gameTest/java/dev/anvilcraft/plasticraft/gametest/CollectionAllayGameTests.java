package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.allay.tool.CollectionAllayToolBehavior;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.blueprint.ConstructionMaterialAccess;
import dev.anvilcraft.plasticraft.blueprint.ConstructionPermission;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

import java.util.UUID;

/** 覆盖磁铁九格真空收集与空手近距捡 1 个。 */
public final class CollectionAllayGameTests {
    private CollectionAllayGameTests() {
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_collection")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Nine collection slots stack, then reject more when every slot is full")
    static void collectionSlotsStackThenRejectWhenFull(ExtendedGameTestHelper helper) {
        WorkingAllayEntity worker = spawnCollectionAllay(helper, new Vec3(2.5D, 2.0D, 2.5D), null);
        check(worker.tryInsertCollection(new ItemStack(Items.COBBLESTONE, 40)) == 40, "first insert must take 40 cobble");
        check(worker.tryInsertCollection(new ItemStack(Items.COBBLESTONE, 40)) == 40, "stacking insert must fill the first slot and spill");
        check(countInAllay(worker, Items.COBBLESTONE) == 80, "two inserts must leave 80 cobble");
        check(worker.collectionInventory().get(0).getCount() == 64, "first slot must respect max stack");
        check(worker.collectionInventory().get(1).getCount() == 16, "overflow must occupy the next slot");

        for (int slot = 0; slot < 9; slot++) {
            worker.collectionInventory().set(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        check(worker.isCollectionFull(), "nine full cobble stacks must report full");
        check(!worker.canAcceptCollection(new ItemStack(Items.COBBLESTONE)), "a full inventory must reject more cobble");
        check(worker.tryInsertCollection(new ItemStack(Items.DIRT)) == 0, "a full inventory must reject a new item");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_collection")
    @EmptyTemplate(value = "20x4x5", floor = true)
    @TestHolder(description = "Free mode locks a drop inside 16 blocks and ignores one beyond that range")
    static void freeModeScansSixteenBlocksOnly(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorkingAllayEntity worker = AllayGameTests.spawnHattedForOwner(
            helper,
            new Vec3(2.5D, 2.0D, 2.5D),
            AllayGameTests.TEST_OWNER
        );
        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.MAGNET.get()));
        ItemEntity inside = spawnDrop(helper, new Vec3(10.5D, 2.0D, 2.5D), new ItemStack(Items.COBBLESTONE));
        ItemEntity outside = spawnDrop(helper, new Vec3(19.5D, 2.0D, 2.5D), new ItemStack(Items.DIRT));
        ExperienceOrb orb = new ExperienceOrb(level, worker.getX(), worker.getY(), worker.getZ(), 5);
        check(level.addFreshEntity(orb), "failed to spawn experience orb");

        ItemEntity found = CollectionAllayToolBehavior.nextFreeTarget(worker, level);
        check(found == inside, "free mode must lock the drop inside 16 blocks");
        check(worker.distanceTo(outside) > CollectionAllayToolBehavior.FREE_RANGE, "the dirt drop must sit beyond 16 blocks");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_collection")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Unload inserts what the owner can hold and drops the rest beside them")
    static void unloadLeavesOverflowOnAllay(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        WorkingAllayEntity worker = spawnCollectionAllay(helper, new Vec3(2.5D, 2.0D, 2.5D), player);
        check(worker.tryInsertCollection(new ItemStack(Items.DIRT, 5)) == 5, "allay must accept five dirt");
        player.getInventory().setItem(0, new ItemStack(Items.DIRT, 63));
        for (int slot = 1; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        worker.unloadCollectionTo(player);
        check(player.getInventory().getItem(0).getCount() == 64, "owner must receive one dirt");
        check(countInAllay(worker, Items.DIRT) == 0, "unload must empty the allay even when the owner is full");
        int dropped = helper.getLevel().getEntitiesOfClass(
            ItemEntity.class,
            player.getBoundingBox().inflate(2.0D),
            item -> item.getItem().is(Items.DIRT)
        ).stream().mapToInt(item -> item.getItem().getCount()).sum();
        check(dropped == 4, "the four dirt that did not fit must drop beside the owner");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_collection")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A free collector unloads to a current teammate without treating inventory insertion as a world edit")
    static void freeCollectorUnloadsToCurrentTeammate(ExtendedGameTestHelper helper) {
        GameTestPlayer teammate = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        teammate.setNoGravity(true);
        teammate.moveTo(helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D)));
        UUID workerOwner = UUID.fromString("00000000-0000-0000-0000-000000000113");
        WorkingAllayEntity worker = AllayGameTests.spawnHattedForOwner(
            helper,
            new Vec3(2.5D, 2.0D, 2.5D),
            workerOwner
        );
        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.MAGNET.get()));
        worker.tickCount = 20;
        check(worker.tryInsertCollection(new ItemStack(Items.DIRT, 3)) == 3,
            "the team unload fixture must hold three dirt");

        try {
            ConstructionPermission.setCollaboratorProvider((server, first, second) ->
                first.equals(workerOwner) && second.equals(teammate.getUUID())
                    || first.equals(teammate.getUUID()) && second.equals(workerOwner)
            );
            ConstructionPermission.setWorldPermissionProvider((level, pos, owner) -> false);
            for (int attempt = 0; attempt < 12 && worker.hasCollectionItems(); attempt++) {
                CollectionAllayToolBehavior.INSTANCE.serverTick(worker);
            }
            check(countInPlayer(teammate, Items.DIRT) == 3,
                "the current teammate did not receive the free collection items");
            check(!worker.hasCollectionItems(), "team unload left collection items on the allay");
        } finally {
            ConstructionPermission.setWorldPermissionProvider(null);
            ConstructionPermission.setCollaboratorProvider(null);
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_collection")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A lounge-bound collector unloads into the chest below and drops leftovers beside the lounge")
    static void unloadToLoungeChestBelow(ExtendedGameTestHelper helper) {
        WorkingAllayEntity worker = spawnCollectionAllay(helper, new Vec3(2.5D, 3.0D, 2.5D), null);
        BlockPos loungePos = new BlockPos(1, 2, 1);
        helper.setBlock(loungePos, PlasticraftBlocks.ALLAY_LOUNGE.get());
        helper.setBlock(loungePos.below(), Blocks.CHEST);
        if (!(helper.getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity lounge)) {
            throw new GameTestAssertException("allay lounge block entity is missing");
        }
        if (!(helper.getBlockEntity(loungePos.below()) instanceof Container chest)) {
            throw new GameTestAssertException("chest below the lounge is missing");
        }
        worker.setHomeLounge(lounge.getBlockPos());
        check(worker.tryInsertCollection(new ItemStack(Items.DIRT, 5)) == 5, "allay must accept five dirt");
        worker.unloadCollectionTo(ConstructionMaterialAccess.below(helper.getLevel(), lounge.getBlockPos()));
        check(chest.getItem(0).is(Items.DIRT) && chest.getItem(0).getCount() == 5,
            "the chest below must receive the five dirt");
        check(countInAllay(worker, Items.DIRT) == 0, "lounge unload must empty the allay");

        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            chest.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        check(worker.tryInsertCollection(new ItemStack(Items.DIRT, 3)) == 3, "allay must accept overflow dirt");
        worker.unloadCollectionTo(ConstructionMaterialAccess.below(helper.getLevel(), lounge.getBlockPos()));
        check(countInAllay(worker, Items.DIRT) == 0, "a full chest must still empty the allay");
        int dropped = helper.getLevel().getEntitiesOfClass(
            ItemEntity.class,
            new AABB(lounge.getBlockPos()).inflate(2.0D),
            item -> item.getItem().is(Items.DIRT)
        ).stream().mapToInt(item -> item.getItem().getCount()).sum();
        check(dropped == 3, "overflow dirt must drop beside the lounge");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 400, batch = "zzz_collection_live_magnet")
    @EmptyTemplate(value = "7x4x7", floor = true)
    @TestHolder(description = "A collection allay flies to a drop inside 16 blocks and inhales it")
    static void collectionAllayInhalesNearbyDrop(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.moveTo(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
        WorkingAllayEntity[] slot = new WorkingAllayEntity[1];
        helper.startSequence().thenExecuteAfter(5, () -> {
            player.setNoGravity(true);
            WorkingAllayEntity worker = spawnCollectionAllay(helper, new Vec3(2.5D, 2.0D, 2.5D), player);
            spawnDrop(helper, new Vec3(4.5D, 2.0D, 2.5D), new ItemStack(Items.COBBLESTONE));
            slot[0] = worker;
        }).thenWaitUntil(() -> {
            WorkingAllayEntity worker = slot[0];
            check(worker != null, "collection allay was not spawned");
            check(
                countInAllay(worker, Items.COBBLESTONE) + countInPlayer(player, Items.COBBLESTONE) >= 1,
                "collection allay must inhale the nearby cobble"
            );
            check(
                helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class,
                    worker.getBoundingBox().inflate(CollectionAllayToolBehavior.FREE_RANGE)
                ).stream().noneMatch(item -> item.getItem().is(Items.COBBLESTONE) && item.isAlive()),
                "the inhaled cobble entity must be gone"
            );
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 400, batch = "zzz_collection_live_empty_hand")
    @EmptyTemplate(value = "7x4x7", floor = true)
    @TestHolder(description = "An empty-handed allay must walk up to a drop before picking exactly one item")
    static void emptyHandApproachesToPickOne(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.moveTo(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
        WorkingAllayEntity[] slot = new WorkingAllayEntity[1];
        helper.startSequence().thenExecuteAfter(5, () -> {
            player.setNoGravity(true);
            WorkingAllayEntity worker = AllayGameTests.spawnHatted(helper, new Vec3(2.5D, 2.0D, 2.5D), player);
            spawnDrop(helper, new Vec3(3.2D, 2.2D, 2.5D), new ItemStack(Items.COBBLESTONE, 1));
            slot[0] = worker;
        }).thenWaitUntil(() -> {
            WorkingAllayEntity worker = slot[0];
            check(worker != null, "empty-hand allay was not spawned");
            int held = countInAllay(worker, Items.COBBLESTONE);
            int given = countInPlayer(player, Items.COBBLESTONE);
            check(held + given == 1, "empty hand must pick exactly one cobble");
            check(
                helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class,
                    worker.getBoundingBox().inflate(CollectionAllayToolBehavior.FREE_RANGE)
                ).stream().noneMatch(item -> item.getItem().is(Items.COBBLESTONE) && item.isAlive()),
                "the picked cobble entity must be gone"
            );
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_collection")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "An empty-handed allay picks one item, has no nine-slot bag, and unloads to the owner")
    static void emptyHandPicksOneAndUnloadsToOwner(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        WorkingAllayEntity worker = AllayGameTests.spawnHatted(helper, new Vec3(2.5D, 2.0D, 2.5D), player);
        check(worker.tryInsertCollection(new ItemStack(Items.COBBLESTONE, 8)) == 1, "empty hand must take only one cobble");
        check(worker.isCollectionFull(), "one hosted cobble must fill the empty-hand carry");
        check(worker.tryInsertCollection(new ItemStack(Items.DIRT)) == 0, "empty hand must reject a second item");
        check(worker.collectionInventory().isEmpty(), "empty hand must not grow a nine-slot bag");
        check(countInAllay(worker, Items.COBBLESTONE) == 1, "empty hand must keep the one cobble");
        worker.unloadCollectionTo(player);
        check(countInPlayer(player, Items.COBBLESTONE) == 1, "owner must receive the one cobble");
        check(countInAllay(worker, Items.COBBLESTONE) == 0, "empty hand must be empty after unload");
        helper.succeed();
    }

    static WorkingAllayEntity spawnCollectionAllay(
        ExtendedGameTestHelper helper,
        Vec3 relativePos,
        GameTestPlayer owner
    ) {
        WorkingAllayEntity worker = AllayGameTests.spawnHatted(helper, relativePos, owner);
        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.MAGNET.get()));
        return worker;
    }

    private static ItemEntity spawnDrop(ExtendedGameTestHelper helper, Vec3 relativePos, ItemStack stack) {
        Vec3 position = helper.absoluteVec(relativePos);
        ItemEntity entity = new ItemEntity(helper.getLevel(), position.x, position.y, position.z, stack);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setPickUpDelay(0);
        check(helper.getLevel().addFreshEntity(entity), "failed to spawn drop");
        return entity;
    }

    private static int countInAllay(WorkingAllayEntity worker, Item item) {
        int count = 0;
        for (ItemStack stack : worker.collectionInventory()) {
            if (stack.is(item)) count += stack.getCount();
        }
        if (worker.hostedCarry().is(item)) {
            count += worker.hostedCarry().getCount();
        }
        return count;
    }

    private static int countInPlayer(GameTestPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
