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
import dev.anvilcraft.plasticraft.blueprint.ConstructionWaitReason;
import dev.anvilcraft.plasticraft.drone.DroneData;
import dev.anvilcraft.plasticraft.drone.DroneEnergyModel;
import dev.anvilcraft.plasticraft.drone.DroneShortageStrategy;
import dev.anvilcraft.plasticraft.drone.tool.ConstructionDroneToolBehavior;
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
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
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
 * 覆盖单架无站建设无人机施工闭环的服务器契约:台账、假方块碰撞、占用、安静提交、
 * 缺料策略、电量拒派、停止后飞回还物与同模板短时序交付。不扫描 128 格实体。
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

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private record StartedJob(ConstructionJob job, ConstructionJobProgress progress) {
    }
}
