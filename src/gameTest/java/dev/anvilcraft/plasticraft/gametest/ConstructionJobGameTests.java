package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.blueprint.BlueprintSource;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintException;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintService;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBuildOp;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobProgress;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobStore;
import dev.anvilcraft.plasticraft.blueprint.ConstructionLedgerEntry;
import dev.anvilcraft.plasticraft.blueprint.ConstructionProjectionIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionDebris;
import dev.anvilcraft.plasticraft.blueprint.ConstructionWaitReason;
import dev.anvilcraft.plasticraft.blueprint.DemolitionPlanner;
import dev.anvilcraft.plasticraft.blueprint.StonecutterSmashAdapter;
import dev.anvilcraft.plasticraft.drone.DroneData;
import dev.anvilcraft.plasticraft.drone.DroneEnergyModel;
import dev.anvilcraft.plasticraft.drone.DroneFlightState;
import dev.anvilcraft.plasticraft.drone.DroneShortageStrategy;
import dev.anvilcraft.plasticraft.drone.tool.CollectionDroneToolBehavior;
import dev.anvilcraft.plasticraft.drone.tool.ConstructionDroneToolBehavior;
import dev.anvilcraft.plasticraft.drone.tool.DemolitionDroneToolBehavior;
import dev.anvilcraft.plasticraft.drone.tool.DroneToolDefinitions;
import dev.anvilcraft.plasticraft.entity.drone.DroneEntity;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

import java.util.Map;
import java.util.UUID;

/**
 * 覆盖单架无站建设/拆除/收集无人机施工闭环的服务器契约:台账、假方块碰撞、占用、安静提交、
 * 缺料策略、电量拒派、停止后飞回还物、同模板短时序交付,以及封堵、拆除与任务掉落回收。不扫描 128 格实体。
 */
public final class ConstructionJobGameTests {
    private ConstructionJobGameTests() {
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Ledger extracts cobble once, marks delivered, and cancel returns in-transit items without duplicating")
    static void ledgerExtractsAndCancelReturnsOnce(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startCobbleJob(helper, player, 2);
                player.getInventory().add(new ItemStack(Items.COBBLESTONE, 4));
                int before = countCobble(player);
                ConstructionBuildOp first = firstPlace(started.progress());
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), first, UUID.randomUUID()),
                    "extracting cobble from the ticking player inventory must succeed"
                );
                check(countCobble(player) == before - 1, "extract must remove exactly one cobble");
                check(carriedCount(started.progress()) == 1, "ledger must record the carried cobble");

                check(
                    ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), first),
                    "deliver after extract must succeed"
                );
                check(carriedCount(started.progress()) == 0, "delivered ledger entries must leave the carried state");
                check(countCobble(player) == before - 1, "deliver must not return the cobble to the inventory");

                ConstructionBuildOp second = firstOpenPlace(started.progress());
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), second, UUID.randomUUID()),
                    "second extract must succeed"
                );
                int duringCarry = countCobble(player);
                ConstructionBlueprintService.cancel(player, started.job().jobId());
                check(countCobble(player) == duringCarry + 1, "cancel must return the in-transit cobble once");
                check(carriedCount(started.progress()) == 0 || ConstructionJobStore.get(helper.getLevel()).get(started.job().jobId()) == null,
                    "cancelled job must not keep a carried ledger entry");
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("ledger setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Delivered projections collide and support a player, stay air without a block entity, and leave undelivered cells passable")
    static void deliveredProjectionCollidesWithoutBlockEntity(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID jobId = UUID.randomUUID();
        BlockPos delivered = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos undelivered = helper.absolutePos(new BlockPos(3, 2, 1));
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        check(level.getBlockState(delivered).isAir(), "delivered cell must start as air");
        check(
            ConstructionProjectionIndex.tryDeliver(level, jobId, delivered, cobble, Map.of(delivered.asLong(), cobble)),
            "cobble projection deliver must succeed in air"
        );
        check(level.getBlockState(delivered).isAir(), "world cell must stay air after projection deliver");
        check(level.getBlockEntity(delivered) == null, "projection must not place a block entity");
        check(hasCollision(level, new AABB(delivered)), "delivered cobble must appear in block collisions");
        check(!hasCollision(level, new AABB(undelivered)), "undelivered air must stay passable");

        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 stand = Vec3.atBottomCenterOf(delivered).add(0.0D, 1.0D, 0.0D);
        player.moveTo(stand.x, stand.y, stand.z);
        check(
            level.noCollision(player),
            "a player standing on top of the delivered cobble must not intersect it"
        );
        check(
            !level.noCollision(player, player.getBoundingBox().move(0.0D, -0.2D, 0.0D)),
            "a player slightly lowered onto the delivered cobble must collide"
        );
        ConstructionProjectionIndex.clearJob(level, jobId);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "An occupying entity blocks projection delivery")
    static void occupiedShapeRejectsDeliver(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        Pig pig = EntityType.PIG.create(level);
        check(pig != null, "failed to create occupying pig");
        Vec3 center = Vec3.atCenterOf(pos);
        pig.moveTo(center.x, pos.getY(), center.z);
        check(level.addFreshEntity(pig), "failed to add occupying pig");
        check(
            !ConstructionProjectionIndex.tryDeliver(
                level,
                UUID.randomUUID(),
                pos,
                Blocks.COBBLESTONE.defaultBlockState(),
                Map.of()
            ),
            "delivery must be rejected while an entity occupies the target shape"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Empty-collision targets such as redstone dust create no fake collision")
    static void emptyCollisionTargetCreatesNoFakeCollision(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockState dust = Blocks.REDSTONE_WIRE.defaultBlockState();
        UUID jobId = UUID.randomUUID();
        check(
            ConstructionProjectionIndex.tryDeliver(level, jobId, pos, dust, Map.of(pos.asLong(), dust)),
            "redstone dust projection must still be recorded"
        );
        check(!hasCollision(level, new AABB(pos)), "redstone dust must not inject a fake collision shape");
        ConstructionProjectionIndex.clearJob(level, jobId);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Cancel commits delivered projections and leaves undelivered cells as air")
    static void cancelCommitsDeliveredOnly(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startCobbleJob(helper, player, 2);
                player.getInventory().add(new ItemStack(Items.COBBLESTONE, 4));
                ConstructionBuildOp first = firstPlace(started.progress());
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), first, UUID.randomUUID()),
                    "extract before partial cancel must succeed"
                );
                check(
                    ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), first),
                    "first cobble must be delivered before cancel"
                );
                ConstructionBuildOp second = firstOpenPlace(started.progress());
                ConstructionBlueprintService.cancel(player, started.job().jobId());
                check(
                    helper.getLevel().getBlockState(first.pos()).is(Blocks.COBBLESTONE),
                    "cancel must quietly commit the delivered cobble"
                );
                check(
                    helper.getLevel().getBlockState(second.pos()).isAir(),
                    "cancel must leave the undelivered cell as air"
                );
                check(
                    !ConstructionProjectionIndex.has(helper.getLevel(), first.pos()),
                    "committed cell must drop its projection collision"
                );
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("partial cancel setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "Quiet commit writes blocks without neighbour chain reactions and clears projection collision")
    static void quietCommitDoesNotBreakNeighbours(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startCobbleJob(helper, player, 1);
                player.getInventory().add(new ItemStack(Items.COBBLESTONE, 2));
                ConstructionBuildOp op = firstPlace(started.progress());
                helper.getLevel().setBlock(op.pos().west().below(), Blocks.SAND.defaultBlockState(), 3);
                helper.getLevel().setBlock(op.pos().west(), Blocks.CACTUS.defaultBlockState(), 3);
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), op, UUID.randomUUID()),
                    "extract before quiet commit must succeed"
                );
                check(
                    ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), op),
                    "deliver before quiet commit must succeed"
                );
                ConstructionJobController.finish(
                    helper.getLevel().getServer(),
                    helper.getLevel(),
                    started.job(),
                    started.progress()
                );
                check(
                    helper.getLevel().getBlockState(op.pos()).is(Blocks.COBBLESTONE),
                    "quiet commit must write the delivered cobble"
                );
                check(
                    helper.getLevel().getBlockState(op.pos().west()).is(Blocks.CACTUS),
                    "quiet commit must not neighbour-update the adjacent cactus into breaking"
                );
                check(
                    !ConstructionProjectionIndex.has(helper.getLevel(), op.pos()),
                    "quiet commit must clear projection collision"
                );
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("quiet commit setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "PAUSE waits for material and SKIP finishes the remaining job incomplete")
    static void pauseWaitsAndSkipFinishesIncomplete(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startCobbleJob(helper, player, 2);
                ConstructionJobController.applyShortage(
                    helper.getLevel().getServer(),
                    started.job(),
                    started.progress(),
                    DroneShortageStrategy.PAUSE,
                    new ItemStack(Items.COBBLESTONE)
                );
                ConstructionJob paused = ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId());
                check(paused != null && paused.state() == ConstructionJob.STATE_WAITING_MATERIAL,
                    "PAUSE shortage must enter WAITING_MATERIAL");

                ConstructionJobController.applyShortage(
                    helper.getLevel().getServer(),
                    paused,
                    started.progress(),
                    DroneShortageStrategy.SKIP,
                    new ItemStack(Items.COBBLESTONE)
                );
                ConstructionJob skipped = ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId());
                if (skipped != null) {
                    ConstructionJobController.tickJob(
                        helper.getLevel().getServer(),
                        helper.getLevel(),
                        skipped
                    );
                }
                check(
                    ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId()) == null,
                    "skipping the remaining cobble must finish and clear the job"
                );
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("shortage setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A drone that cannot accept the energy quote is not assigned a construction lease")
    static void insufficientEnergyDoesNotClaim(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                player.moveTo(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
                StartedJob started = startCobbleJob(helper, player, 1);
                DroneEntity drone = spawnConstructionDrone(helper, new Vec3(2.5D, 2.0D, 1.5D), player, 100);
                check(
                    !ConstructionDroneToolBehavior.tryClaim(
                        drone,
                        helper.getLevel(),
                        started.job(),
                        started.progress()
                    ),
                    "a drone below the quote plus landing reserve must not claim a lease"
                );
                check(drone.waitReason() == ConstructionWaitReason.ENERGY,
                    "rejected claim must record ENERGY, was " + drone.waitReason());
                check(drone.assignedJobId().isEmpty(), "rejected claim must leave the drone unassigned");
                ConstructionBlueprintService.cancel(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("energy claim setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 200, batch = "zzz_construction_return")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "Stopping a job makes a loaded drone fly back and insert carry beside the owner")
    static void pauseReturnsCarryByFlyingToOwner(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.moveTo(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
        DroneEntity[] droneSlot = new DroneEntity[1];
        int[] beforeSlot = new int[1];
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                player.setNoGravity(true);
                player.moveTo(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
                StartedJob started = startCobbleJob(helper, player, 2, new BlockPos(5, 2, 5));
                player.getInventory().add(new ItemStack(Items.COBBLESTONE, 4));
                ConstructionBuildOp first = firstPlace(started.progress());
                DroneEntity drone = spawnConstructionDrone(
                    helper,
                    new Vec3(5.5D, 3.0D, 5.5D),
                    player,
                    DroneEnergyModel.capacity()
                );
                drone.setNoGravity(true);
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), first, drone.getUUID()),
                    "extract before pause must succeed"
                );
                drone.setHostedCarry(first.material().copyWithCount(1));
                first.setStatus(ConstructionBuildOp.Status.LEASED);
                first.setLeaseDrone(drone.getUUID());
                drone.assign(started.job().jobId(), first.id());
                droneSlot[0] = drone;
                beforeSlot[0] = countCobble(player);
                check(
                    drone.distanceTo(player) > ConstructionJobController.REACH + 0.5D,
                    "setup must place the drone away from the owner"
                );
                ConstructionBlueprintService.toggleActive(player, started.job().jobId());
                check(countCobble(player) == beforeSlot[0], "pause must not teleport carry into the inventory");
                check(!drone.hostedCarry().isEmpty(), "drone must keep hosted carry after pause");
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("pause return setup failed: " + exception.reason());
            }
        }).thenWaitUntil(() -> {
            DroneEntity drone = droneSlot[0];
            check(drone != null, "construction drone missing after pause");
            check(drone.hostedCarry().isEmpty(), "drone must empty carry after flying back");
            check(
                countCobble(player) == beforeSlot[0] + 1,
                "owner must receive the returned cobble after the drone arrives"
            );
            check(
                drone.distanceTo(player) <= ConstructionJobController.REACH + 1.0D,
                "drone must return beside the owner"
            );
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 400, batch = "zzz_construction_live")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "A construction drone takes cobble from the owner and delivers a short air wall")
    static void constructionDroneDeliversShortWall(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.moveTo(helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D)));
        DroneEntity[] droneSlot = new DroneEntity[1];
        UUID[] jobSlot = new UUID[1];
        AABB[] siteSlot = new AABB[1];
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                player.setNoGravity(true);
                player.moveTo(helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D)));
                StartedJob started = startCobbleJob(helper, player, 2, new BlockPos(3, 2, 3));
                jobSlot[0] = started.job().jobId();
                siteSlot[0] = ConstructionJobController.worldBox(started.job());
                player.getInventory().add(new ItemStack(Items.COBBLESTONE, 8));
                DroneEntity drone = spawnConstructionDrone(
                    helper,
                    new Vec3(2.5D, 2.0D, 3.5D),
                    player,
                    DroneEnergyModel.capacity()
                );
                drone.setNoGravity(true);
                droneSlot[0] = drone;
                check(
                    ConstructionDroneToolBehavior.tryClaim(
                        drone,
                        helper.getLevel(),
                        started.job(),
                        started.progress()
                    ),
                    "construction drone failed to claim: wait=" + drone.waitReason()
                        + " energy=" + drone.getEnergy()
                        + " ownerListed=" + (helper.getLevel().getServer().getPlayerList().getPlayer(player.getUUID()) != null)
                        + " ownerInLevel=" + (ConstructionJobController.findOwner(
                            helper.getLevel().getServer(),
                            helper.getLevel(),
                            player.getUUID()
                        ) != null)
                );
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("live loop setup failed: " + exception.reason());
            }
        }).thenWaitUntil(() -> {
            DroneEntity drone = droneSlot[0];
            UUID jobId = jobSlot[0];
            ConstructionJob job = jobId == null ? null : ConstructionJobIndex.get(helper.getLevel()).job(jobId);
            ConstructionJobProgress progress = jobId == null
                ? null
                : ConstructionJobStore.get(helper.getLevel()).get(jobId);
            BlockPos first = new BlockPos(3, 2, 3);
            BlockPos second = new BlockPos(4, 2, 3);
            check(
                helper.getBlockState(first).is(Blocks.COBBLESTONE),
                "first cobble wall cell was not committed"
                    + "; jobState=" + (job == null ? "cleared" : job.state())
                    + "; delivered=" + deliveredCount(progress)
                    + "; projected=" + ConstructionProjectionIndex.has(
                        helper.getLevel(),
                        helper.absolutePos(first)
                    )
                    + (drone == null ? "" : "; wait=" + drone.waitReason()
                    + " flight=" + drone.flightState()
                    + " carry=" + drone.hostedCarry()
                    + " assigned=" + drone.assignedJobId()
                    + " pos=" + drone.blockPosition())
            );
            check(
                helper.getBlockState(second).is(Blocks.COBBLESTONE),
                "second cobble wall cell was not committed"
            );
            check(countCobble(player) < 8, "owner inventory must lose cobble used for delivery");
            check(siteSlot[0] != null, "construction site bounds missing");
            check(
                drone != null && !siteSlot[0].intersects(drone.getBoundingBox()),
                "drone must leave the construction site before landing"
                    + (drone == null ? "" : "; pos=" + drone.blockPosition()
                    + " flight=" + drone.flightState())
            );
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_demolish")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Stonecutter smash clears cobble with marked drops, awards no XP, and rejects bedrock")
    static void stonecutterSmashMatchesAnvilSemantics(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cobble = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos bedrock = helper.absolutePos(new BlockPos(3, 2, 2));
        level.setBlockAndUpdate(cobble, Blocks.COBBLESTONE.defaultBlockState());
        level.setBlockAndUpdate(bedrock, Blocks.BEDROCK.defaultBlockState());
        UUID jobId = UUID.randomUUID();
        check(
            StonecutterSmashAdapter.smash(level, cobble, jobId, 1),
            "cobble must be smashable"
        );
        check(level.getBlockState(cobble).isAir(), "smashed cobble must become air");
        check(
            !StonecutterSmashAdapter.smash(level, bedrock, jobId, 2),
            "bedrock must be rejected as a permanent obstacle"
        );
        check(level.getBlockState(bedrock).is(Blocks.BEDROCK), "bedrock must remain");
        boolean marked = false;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(cobble).inflate(1.5D))) {
            if (item.getItem().is(Items.COBBLESTONE) && ConstructionDebris.isMarked(item.getItem())) {
                marked = true;
            }
        }
        check(marked, "cobble smash must drop a job-marked cobble");
        check(
            level.getEntitiesOfClass(ExperienceOrb.class, new AABB(cobble).inflate(2.0D)).isEmpty(),
            "ordinary stonecutter smash must not drop experience"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_demolish")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Job-marked cobble drops do not merge with unmarked cobble drops")
    static void jobMarkedDropsDoNotMerge(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(2.5D, 2.2D, 2.5D));
        ItemStack marked = new ItemStack(Items.COBBLESTONE);
        ConstructionDebris.mark(marked, UUID.randomUUID(), 3);
        ItemEntity first = new ItemEntity(level, pos.x, pos.y, pos.z, marked);
        ItemEntity second = new ItemEntity(level, pos.x, pos.y, pos.z, new ItemStack(Items.COBBLESTONE));
        first.setDeltaMovement(Vec3.ZERO);
        second.setDeltaMovement(Vec3.ZERO);
        check(level.addFreshEntity(first), "failed to spawn marked drop");
        check(level.addFreshEntity(second), "failed to spawn unmarked drop");
        helper.startSequence().thenExecuteAfter(10, () -> {
            int markedCount = 0;
            int unmarkedCount = 0;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos, pos).inflate(2.0D))) {
                if (!item.getItem().is(Items.COBBLESTONE)) continue;
                if (ConstructionDebris.isMarked(item.getItem())) {
                    markedCount += item.getItem().getCount();
                } else {
                    unmarkedCount += item.getItem().getCount();
                }
            }
            check(markedCount == 1, "marked cobble must stay a separate stack, was " + markedCount);
            check(unmarkedCount == 1, "unmarked cobble must stay a separate stack, was " + unmarkedCount);
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_demolish")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A world stone cell is demolished before its PLACE op becomes assignable")
    static void worldStoneIsDemolishedBeforePlace(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                player.moveTo(helper.absoluteVec(new Vec3(1.5D, 2.0D, 3.5D)));
                helper.setBlock(new BlockPos(3, 2, 1), Blocks.STONE);
                StartedJob started = startCobbleJob(helper, player, 1);
                try {
                    check(
                        started.job().state() == ConstructionJob.STATE_DEMOLISHING,
                        "occupied cobble cell must start in DEMOLISHING, was " + started.job().state()
                    );
                    ConstructionBuildOp demolish = firstKind(started.progress(), ConstructionBuildOp.Kind.DEMOLISH);
                    check(
                        ConstructionJobController.tryDemolish(helper.getLevel(), started.progress(), demolish),
                        "demolish of the world stone must succeed"
                    );
                    check(helper.getBlockState(new BlockPos(3, 2, 1)).isAir(), "demolished stone must become air");
                    ConstructionBuildOp place = firstPlace(started.progress());
                    ConstructionBuildOp assignable = ConstructionJobController.nextAssignable(
                        helper.getLevel(),
                        started.progress()
                    );
                    check(
                        assignable != null,
                        "PLACE must become assignable only after the world block is gone"
                            + "; status=" + place.status()
                            + " approach=" + ConstructionJobController.chooseApproach(
                                helper.getLevel(),
                                started.progress(),
                                place
                            )
                    );
                    ConstructionJobIndex index = ConstructionJobIndex.get(helper.getLevel());
                    ConstructionJob live = index.job(started.job().jobId());
                    check(live != null, "demolished job must still be indexed");
                    index.put(live.withState(ConstructionJob.STATE_BUILDING));
                    ConstructionJobController.tickJob(
                        helper.getLevel().getServer(),
                        helper.getLevel(),
                        index.job(started.job().jobId())
                    );
                    check(
                        !ConstructionProjectionIndex.has(helper.getLevel(), place.pos()),
                        "demolish must not write a delivered solid projection"
                    );
                    check(
                        place.status() != ConstructionBuildOp.Status.DELIVERED,
                        "PLACE must stay undelivered after demolish"
                    );
                } finally {
                    cancelQuietly(player, started.job().jobId());
                }
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("demolish-before-place setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_demolish")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Bedrock skips its PLACE op and leaves the remaining air cell buildable")
    static void permanentObstacleSkipsPlaceAndContinues(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                helper.setBlock(new BlockPos(3, 2, 1), Blocks.BEDROCK);
                StartedJob started = startCobbleJob(helper, player, 2);
                try {
                    check(
                        started.job().state() == ConstructionJob.STATE_BUILDING,
                        "bedrock must not stall the job in demolition, was " + started.job().state()
                    );
                    ConstructionBuildOp first = firstPlace(started.progress());
                    check(
                        first.status() == ConstructionBuildOp.Status.SKIPPED,
                        "bedrock cell PLACE must be skipped"
                    );
                    ConstructionBuildOp open = firstOpenPlace(started.progress());
                    check(open.pos().equals(helper.absolutePos(new BlockPos(4, 2, 1))),
                        "the remaining air cobble cell must stay assignable");
                } finally {
                    cancelQuietly(player, started.job().jobId());
                }
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("permanent obstacle setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_demolish")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Missing demolition drones pause or skip remaining breakable cells")
    static void missingDemolitionPauseAndSkip(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                helper.setBlock(new BlockPos(3, 2, 1), Blocks.STONE);
                StartedJob started = startCobbleJob(helper, player, 1);
                try {
                    ConstructionJobController.applyDemolitionShortage(
                        helper.getLevel().getServer(),
                        started.job(),
                        started.progress(),
                        DroneShortageStrategy.PAUSE
                    );
                    ConstructionJob paused = ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId());
                    check(
                        paused != null && paused.state() == ConstructionJob.STATE_WAITING_DEMOLITION,
                        "PAUSE must enter WAITING_DEMOLITION"
                    );
                    ConstructionJobController.applyDemolitionShortage(
                        helper.getLevel().getServer(),
                        paused,
                        started.progress(),
                        DroneShortageStrategy.SKIP
                    );
                    ConstructionJob afterSkip = ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId());
                    check(
                        afterSkip != null && afterSkip.state() == ConstructionJob.STATE_BUILDING,
                        "SKIP must leave demolition and continue, was "
                            + (afterSkip == null ? "cleared" : afterSkip.state())
                    );
                    check(started.progress().incomplete(), "skipped demolition must mark the job incomplete");
                    check(
                        firstPlace(started.progress()).status() == ConstructionBuildOp.Status.SKIPPED,
                        "the still-occupied PLACE cell must be skipped"
                    );
                } finally {
                    cancelQuietly(player, started.job().jobId());
                }
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("demolition shortage setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_demolish")
    @EmptyTemplate(value = "7x4x7", floor = true)
    @TestHolder(description = "Fluid sealing fills the declared cell plus a one-block shell and does not chase the pool")
    static void fluidSealPlacesDeclaredAndOneBlockShell(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                for (int x = 1; x <= 3; x++) {
                    for (int z = 1; z <= 3; z++) {
                        helper.setBlock(new BlockPos(x, 2, z), Blocks.WATER);
                    }
                }
                player.getInventory().add(new ItemStack(Items.COBBLESTONE, 8));
                StartedJob started = startCobbleJob(helper, player, 1, new BlockPos(2, 2, 2));
                try {
                    check(
                        started.job().state() == ConstructionJob.STATE_SEALING_FLUID,
                        "a flooded declared cell must start in SEALING_FLUID, was " + started.job().state()
                    );
                    int seals = countKind(started.progress(), ConstructionBuildOp.Kind.SEAL);
                    int shells = 0;
                    for (ConstructionBuildOp op : started.progress().operations()) {
                        if (op.kind() == ConstructionBuildOp.Kind.SEAL && op.shell()) shells++;
                    }
                    check(seals == 5, "declared water plus four face-neighbors must be sealed, was " + seals);
                    check(shells == 4, "exactly four shell cells must be planned, was " + shells);
                    check(
                        helper.getBlockState(new BlockPos(1, 2, 1)).is(Blocks.WATER),
                        "diagonal pool water must not be chased"
                    );
                } finally {
                    cancelQuietly(player, started.job().jobId());
                }
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("fluid seal setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_demolish")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Cancel during sealing keeps placed fills and returns in-transit fill material")
    static void cancelDuringSealKeepsFills(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                helper.setBlock(new BlockPos(3, 2, 1), Blocks.WATER);
                player.getInventory().add(new ItemStack(Items.DIRT, 8));
                player.getInventory().add(new ItemStack(Items.COBBLESTONE, 2));
                StartedJob started = startCobbleJob(helper, player, 1);
                try {
                    ConstructionBuildOp seal = firstKind(started.progress(), ConstructionBuildOp.Kind.SEAL);
                    int dirtBefore = countItem(player, Items.DIRT);
                    check(
                        ConstructionJobController.extractMaterial(player, started.progress(), seal, UUID.randomUUID()),
                        "extracting seal fill must succeed"
                    );
                    check(
                        ConstructionJobController.tryPlaceSeal(helper.getLevel(), started.progress(), seal, null),
                        "placing the first fill must succeed"
                    );
                    check(helper.getBlockState(new BlockPos(3, 2, 1)).is(Blocks.DIRT), "placed fill must be real dirt");
                    ConstructionBuildOp next = firstOpenKind(started.progress(), ConstructionBuildOp.Kind.SEAL);
                    int during = countItem(player, Items.DIRT);
                    if (next != null) {
                        check(
                            ConstructionJobController.extractMaterial(player, started.progress(), next, UUID.randomUUID()),
                            "second extract must succeed"
                        );
                        during = countItem(player, Items.DIRT);
                    }
                    ConstructionBlueprintService.cancel(player, started.job().jobId());
                    check(helper.getBlockState(new BlockPos(3, 2, 1)).is(Blocks.DIRT), "cancel must keep the placed fill");
                    if (next != null) {
                        check(
                            countItem(player, Items.DIRT) == during + 1,
                            "cancel must return the in-transit fill once"
                        );
                    } else {
                        check(countItem(player, Items.DIRT) == dirtBefore - 1, "placed fill must stay consumed");
                    }
                } finally {
                    cancelQuietly(player, started.job().jobId());
                }
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("cancel-during-seal setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_demolish")
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "Smashing a door core drops one door and clears both halves")
    static void doorDemolishDropsOnce(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lower = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos upper = helper.absolutePos(new BlockPos(2, 3, 2));
        level.setBlockAndUpdate(lower, Blocks.OAK_DOOR.defaultBlockState());
        level.setBlockAndUpdate(
            upper,
            Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
        );
        check(
            StonecutterSmashAdapter.smash(level, lower, UUID.randomUUID(), 4),
            "door core must be smashable"
        );
        DemolitionPlanner.clearAttachedResidue(level, lower);
        check(level.getBlockState(lower).isAir(), "door lower half must be air");
        check(level.getBlockState(upper).isAir(), "door upper half must be cleared without a second smash");
        int doors = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(lower).inflate(2.0D))) {
            if (item.getItem().is(Items.OAK_DOOR)) {
                doors += item.getItem().getCount();
            }
        }
        check(doors == 1, "door smash must drop exactly one door, was " + doors);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 400, batch = "zzz_construction_demolish_live")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "A demolition drone flies to a stone cell and smashes it")
    static void demolitionDroneClearsShortWall(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.moveTo(helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D)));
        UUID[] jobSlot = new UUID[1];
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                player.setNoGravity(true);
                player.moveTo(helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D)));
                helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
                DroneEntity drone = spawnDemolitionDrone(
                    helper,
                    new Vec3(2.5D, 2.0D, 3.5D),
                    player,
                    DroneEnergyModel.capacity()
                );
                drone.setNoGravity(true);
                StartedJob started = startCobbleJob(helper, player, 1, new BlockPos(3, 2, 3));
                jobSlot[0] = started.job().jobId();
                if (!DemolitionDroneToolBehavior.tryClaim(
                    drone,
                    helper.getLevel(),
                    started.job(),
                    started.progress()
                )) {
                    cancelQuietly(player, started.job().jobId());
                    jobSlot[0] = null;
                    throw new GameTestAssertException("demolition drone failed to claim: wait=" + drone.waitReason());
                }
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("live demolish setup failed: " + exception.reason());
            }
        }).thenWaitUntil(() -> {
            check(
                helper.getBlockState(new BlockPos(3, 2, 3)).isAir(),
                "demolition drone must smash the stone cell to air"
            );
        }).thenExecute(() -> {
            if (jobSlot[0] != null) {
                cancelQuietly(player, jobSlot[0]);
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_collection")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Task collection claims only this job's marked drops and leaves unmarked stacks")
    static void taskCollectionClaimsOnlyMarkedDrops(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                helper.setBlock(new BlockPos(3, 2, 1), Blocks.STONE);
                StartedJob started = startCobbleJob(helper, player, 1);
                try {
                    ConstructionBuildOp demolish = firstKind(started.progress(), ConstructionBuildOp.Kind.DEMOLISH);
                    check(
                        ConstructionJobController.tryDemolish(helper.getLevel(), started.progress(), demolish),
                        "demolish must spawn marked drops"
                    );
                    DroneEntity drone = spawnCollectionDrone(
                        helper,
                        new Vec3(2.5D, 2.0D, 1.5D),
                        player,
                        DroneEnergyModel.capacity()
                    );
                    ConstructionJobController.tickJob(
                        helper.getLevel().getServer(),
                        helper.getLevel(),
                        ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId())
                    );
                    ConstructionJob collecting = ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId());
                    check(
                        collecting != null && collecting.state() == ConstructionJob.STATE_COLLECTING_DEBRIS,
                        "a ready collector plus marked drops must enter COLLECTING_DEBRIS"
                    );
                    Vec3 unmarkedPos = helper.absoluteVec(new Vec3(3.5D, 2.2D, 1.5D));
                    ItemEntity unmarked = new ItemEntity(
                        helper.getLevel(),
                        unmarkedPos.x,
                        unmarkedPos.y,
                        unmarkedPos.z,
                        new ItemStack(Items.COBBLESTONE)
                    );
                    unmarked.setDeltaMovement(Vec3.ZERO);
                    unmarked.setPickUpDelay(0);
                    check(helper.getLevel().addFreshEntity(unmarked), "failed to spawn unmarked cobble");
                    for (ItemEntity item : ConstructionJobController.markedDebrisIn(
                        helper.getLevel(),
                        collecting,
                        started.progress(),
                        ConstructionJobController.worldBox(collecting)
                    )) {
                        item.setPickUpDelay(0);
                    }
                    ItemEntity claimed = ConstructionJobController.nextAssignableDebris(
                        helper.getLevel(),
                        collecting,
                        started.progress(),
                        drone.position()
                    );
                    check(claimed != null && ConstructionDebris.isMarked(claimed.getItem()),
                        "task mode must select a marked drop");
                    check(claimed != unmarked, "task mode must not claim the unmarked cobble");
                    check(
                        CollectionDroneToolBehavior.tryClaim(drone, helper.getLevel(), collecting, started.progress()),
                        "collection drone must claim the marked drop"
                    );
                    check(unmarked.isAlive() && !ConstructionDebris.isMarked(unmarked.getItem()),
                        "unmarked cobble must stay in the world");
                    check(
                        !ItemStack.isSameItemSameComponents(unmarked.getItem(), claimed.getItem()),
                        "marked and unmarked cobble must not share components"
                    );
                } finally {
                    cancelQuietly(player, started.job().jobId());
                }
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("marked-only collection setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_collection")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Player pickup strips the mark, records external settlement, and does not reissue drops")
    static void playerPickupSettlesExternallyWithoutReissue(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                helper.setBlock(new BlockPos(3, 2, 1), Blocks.STONE);
                StartedJob started = startCobbleJob(helper, player, 1);
                try {
                    ConstructionBuildOp demolish = firstKind(started.progress(), ConstructionBuildOp.Kind.DEMOLISH);
                    check(
                        ConstructionJobController.tryDemolish(helper.getLevel(), started.progress(), demolish),
                        "demolish must spawn marked drops"
                    );
                    int spawned = started.progress().debrisSpawned();
                    check(spawned > 0, "smash must record spawned debris");
                    ItemEntity drop = null;
                    Item dropItem = Items.COBBLESTONE;
                    int worldBefore = 0;
                    for (ItemEntity item : ConstructionJobController.markedDebrisIn(
                        helper.getLevel(),
                        started.job(),
                        started.progress(),
                        ConstructionJobController.worldBox(started.job())
                    )) {
                        item.setPickUpDelay(0);
                        drop = item;
                        dropItem = item.getItem().getItem();
                        worldBefore += item.getItem().getCount();
                    }
                    check(drop != null, "demolish must leave a marked drop");
                    drop.setPos(player.getX(), player.getY(), player.getZ());
                    drop.playerTouch(player);
                    for (ItemStack stack : player.getInventory().items) {
                        check(!ConstructionDebris.isMarked(stack), "picked items must lose the construction mark");
                    }
                    check(
                        countItem(player, dropItem) >= worldBefore,
                        "the player must receive the demolished drop"
                    );
                    check(
                        started.progress().debrisSettled() >= worldBefore,
                        "player pickup must count as external settlement"
                    );
                    int stillMarked = 0;
                    for (ItemEntity item : helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class,
                        ConstructionJobController.worldBox(started.job())
                    )) {
                        if (ConstructionDebris.isMarked(item.getItem())) {
                            stillMarked += item.getItem().getCount();
                        }
                    }
                    check(
                        started.progress().debrisSpawned() == spawned,
                        "player pickup must not reissue extra spawned debris"
                    );
                    check(stillMarked == 0, "player pickup must not leave a marked copy in the world");
                } finally {
                    cancelQuietly(player, started.job().jobId());
                }
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("player pickup settlement setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_collection")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Demolition without a collection drone still enters BUILDING")
    static void missingCollectorDoesNotBlockBuilding(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                helper.setBlock(new BlockPos(3, 2, 1), Blocks.STONE);
                StartedJob started = startCobbleJob(helper, player, 1);
                try {
                    ConstructionBuildOp demolish = firstKind(started.progress(), ConstructionBuildOp.Kind.DEMOLISH);
                    check(
                        ConstructionJobController.tryDemolish(helper.getLevel(), started.progress(), demolish),
                        "demolish must succeed without a collector"
                    );
                    ConstructionJobController.tickJob(
                        helper.getLevel().getServer(),
                        helper.getLevel(),
                        ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId())
                    );
                    ConstructionJob live = ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId());
                    check(
                        live != null && live.state() == ConstructionJob.STATE_BUILDING,
                        "missing collectors must not block BUILDING, was "
                            + (live == null ? "cleared" : live.state())
                    );
                    check(
                        ConstructionJobController.hasWorldDebris(helper.getLevel(), live, started.progress()),
                        "marked drops must remain in the world"
                    );
                } finally {
                    cancelQuietly(player, started.job().jobId());
                }
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("missing-collector setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_collection")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A ready collector enters COLLECTING_DEBRIS and leaves for BUILDING after the marks are gone")
    static void collectorSweepsThenBuilds(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                helper.setBlock(new BlockPos(3, 2, 1), Blocks.STONE);
                StartedJob started = startCobbleJob(helper, player, 1);
                try {
                    ConstructionBuildOp demolish = firstKind(started.progress(), ConstructionBuildOp.Kind.DEMOLISH);
                    check(
                        ConstructionJobController.tryDemolish(helper.getLevel(), started.progress(), demolish),
                        "demolish must spawn marked drops"
                    );
                    DroneEntity drone = spawnCollectionDrone(
                        helper,
                        new Vec3(2.5D, 2.0D, 1.5D),
                        player,
                        DroneEnergyModel.capacity()
                    );
                    ConstructionJobController.tickJob(
                        helper.getLevel().getServer(),
                        helper.getLevel(),
                        ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId())
                    );
                    ConstructionJob collecting = ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId());
                    check(
                        collecting != null && collecting.state() == ConstructionJob.STATE_COLLECTING_DEBRIS,
                        "a ready collector must enter COLLECTING_DEBRIS"
                    );
                    for (ItemEntity item : ConstructionJobController.markedDebrisIn(
                        helper.getLevel(),
                        collecting,
                        started.progress(),
                        ConstructionJobController.worldBox(collecting)
                    )) {
                        item.setPickUpDelay(0);
                        check(
                            ConstructionJobController.tryCollect(drone, item, started.progress()),
                            "collector must inhale the marked drop"
                        );
                    }
                    ConstructionJobController.tickJob(
                        helper.getLevel().getServer(),
                        helper.getLevel(),
                        ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId())
                    );
                    ConstructionJob building = ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId());
                    check(
                        building != null && building.state() == ConstructionJob.STATE_BUILDING,
                        "clearing or filling collectors must continue to BUILDING"
                    );
                    check(
                        started.progress().debrisSettled() > 0
                            || countInDrone(drone, Items.COBBLESTONE) + countInDrone(drone, Items.STONE) > 0,
                        "the collector must keep the inhaled items"
                    );
                    CollectionDroneToolBehavior.INSTANCE.serverTick(drone);
                    AABB site = ConstructionJobController.worldBox(building);
                    if (site.inflate(2.0D).intersects(drone.getBoundingBox())) {
                        check(
                            drone.flightState() == DroneFlightState.FLYING,
                            "a collector still on site after the sweep must evacuate, was "
                                + drone.flightState()
                        );
                    }
                } finally {
                    cancelQuietly(player, started.job().jobId());
                }
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("collector sweep setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    private static StartedJob startCobbleJob(ExtendedGameTestHelper helper, GameTestPlayer player, int count)
        throws ConstructionBlueprintException {
        return startCobbleJob(helper, player, count, new BlockPos(3, 2, 1));
    }

    private static StartedJob startCobbleJob(
        ExtendedGameTestHelper helper,
        GameTestPlayer player,
        int count,
        BlockPos relativeAnchor
    ) throws ConstructionBlueprintException {
        ItemStack disk = new ItemStack(ModItems.STRUCTURE_DISK.get());
        ConstructionBlueprintService.importIntoDisk(
            helper.getLevel().getServer(),
            disk,
            cobbleStructure(count),
            "cobble-wall",
            BlueprintSource.VANILLA_FILE
        );
        player.setItemInHand(InteractionHand.MAIN_HAND, disk);
        BlockPos anchor = helper.absolutePos(relativeAnchor);
        ConstructionJob job = ConstructionBlueprintService.deploy(
            player,
            InteractionHand.MAIN_HAND,
            anchor,
            Rotation.NONE,
            Mirror.NONE
        );
        ConstructionBlueprintService.start(player, job.jobId());
        MinecraftServer server = helper.getLevel().getServer();
        ConstructionJob started = ConstructionJobIndex.get(server).job(job.jobId());
        check(started != null && started.isActive(), "started cobble job must be active");
        ConstructionJobProgress progress = ConstructionJobStore.get(server).get(job.jobId());
        check(progress != null && progress.planned(), "started cobble job must be planned");
        return new StartedJob(started, progress);
    }

    private static CompoundTag cobbleStructure(int count) {
        CompoundTag tag = new CompoundTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(count));
        size.add(IntTag.valueOf(1));
        size.add(IntTag.valueOf(1));
        tag.put("size", size);
        ListTag palette = new ListTag();
        CompoundTag cobble = new CompoundTag();
        cobble.putString("Name", "minecraft:cobblestone");
        palette.add(cobble);
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        for (int index = 0; index < count; index++) {
            CompoundTag entry = new CompoundTag();
            ListTag pos = new ListTag();
            pos.add(IntTag.valueOf(index));
            pos.add(IntTag.valueOf(0));
            pos.add(IntTag.valueOf(0));
            entry.put("pos", pos);
            entry.putInt("state", 0);
            blocks.add(entry);
        }
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        return tag;
    }

    private static DroneEntity spawnConstructionDrone(
        ExtendedGameTestHelper helper,
        Vec3 relativePos,
        GameTestPlayer player,
        int energy
    ) {
        DroneEntity drone = PlasticraftEntities.DRONE.get().create(helper.getLevel());
        check(drone != null, "failed to create construction drone");
        drone.applyDroneData(DroneData.assembled(
            DroneToolDefinitions.CONSTRUCTION.id(),
            ItemStack.EMPTY,
            ItemStack.EMPTY
        ).withOwner(player.getUUID()).withEnergy(energy));
        Vec3 position = helper.absoluteVec(relativePos);
        drone.setPos(position.x, position.y, position.z);
        check(helper.getLevel().addFreshEntity(drone), "failed to add construction drone");
        return drone;
    }

    private static DroneEntity spawnCollectionDrone(
        ExtendedGameTestHelper helper,
        Vec3 relativePos,
        GameTestPlayer player,
        int energy
    ) {
        DroneEntity drone = PlasticraftEntities.DRONE.get().create(helper.getLevel());
        check(drone != null, "failed to create collection drone");
        drone.applyDroneData(DroneData.assembled(
            DroneToolDefinitions.COLLECTION.id(),
            ItemStack.EMPTY,
            ItemStack.EMPTY
        ).withOwner(player.getUUID()).withEnergy(energy));
        Vec3 position = helper.absoluteVec(relativePos);
        drone.setPos(position.x, position.y, position.z);
        check(helper.getLevel().addFreshEntity(drone), "failed to add collection drone");
        return drone;
    }

    private static int countInDrone(DroneEntity drone, Item item) {
        int count = 0;
        for (ItemStack stack : drone.collectionInventory()) {
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static DroneEntity spawnDemolitionDrone(
        ExtendedGameTestHelper helper,
        Vec3 relativePos,
        GameTestPlayer player,
        int energy
    ) {
        DroneEntity drone = PlasticraftEntities.DRONE.get().create(helper.getLevel());
        check(drone != null, "failed to create demolition drone");
        drone.applyDroneData(DroneData.assembled(
            DroneToolDefinitions.DEMOLITION.id(),
            ItemStack.EMPTY,
            ItemStack.EMPTY
        ).withOwner(player.getUUID()).withEnergy(energy));
        Vec3 position = helper.absoluteVec(relativePos);
        drone.setPos(position.x, position.y, position.z);
        check(helper.getLevel().addFreshEntity(drone), "failed to add demolition drone");
        return drone;
    }

    private static ConstructionBuildOp firstKind(ConstructionJobProgress progress, ConstructionBuildOp.Kind kind) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() == kind) return op;
        }
        throw new GameTestAssertException("planned job has no " + kind + " operation");
    }

    private static ConstructionBuildOp firstOpenKind(ConstructionJobProgress progress, ConstructionBuildOp.Kind kind) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() == kind && op.isOpen()) return op;
        }
        return null;
    }

    private static int countKind(ConstructionJobProgress progress, ConstructionBuildOp.Kind kind) {
        int count = 0;
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() == kind) count++;
        }
        return count;
    }

    private static int countItem(GameTestPlayer player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) count += stack.getCount();
        }
        if (player.getOffhandItem().is(item)) {
            count += player.getOffhandItem().getCount();
        }
        return count;
    }

    private static ConstructionBuildOp firstPlace(ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() == ConstructionBuildOp.Kind.PLACE) return op;
        }
        throw new GameTestAssertException("planned job has no PLACE operation");
    }

    private static ConstructionBuildOp firstOpenPlace(ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() == ConstructionBuildOp.Kind.PLACE && op.isOpen()) return op;
        }
        throw new GameTestAssertException("planned job has no remaining PLACE operation");
    }

    private static int deliveredCount(ConstructionJobProgress progress) {
        if (progress == null) return 0;
        int count = 0;
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.status() == ConstructionBuildOp.Status.DELIVERED) count++;
        }
        return count;
    }

    private static int carriedCount(ConstructionJobProgress progress) {
        int count = 0;
        for (ConstructionLedgerEntry entry : progress.ledger()) {
            if (entry.state() == ConstructionLedgerEntry.State.CARRIED) count++;
        }
        return count;
    }

    private static int countCobble(GameTestPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.COBBLESTONE)) count += stack.getCount();
        }
        if (player.getOffhandItem().is(Items.COBBLESTONE)) {
            count += player.getOffhandItem().getCount();
        }
        return count;
    }

    private static boolean hasCollision(ServerLevel level, AABB box) {
        for (VoxelShape shape : level.getBlockCollisions(null, box)) {
            if (!shape.isEmpty()) return true;
        }
        return false;
    }

    private static void cancelQuietly(GameTestPlayer player, UUID jobId) {
        try {
            ConstructionBlueprintService.cancel(player, jobId);
        } catch (ConstructionBlueprintException ignored) {
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private record StartedJob(ConstructionJob job, ConstructionJobProgress progress) {
    }
}
