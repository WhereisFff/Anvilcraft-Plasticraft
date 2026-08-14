package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.blueprint.BlueprintSource;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintException;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintService;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBuildOp;
import dev.anvilcraft.plasticraft.blueprint.ConstructionCommitLog;
import dev.anvilcraft.plasticraft.blueprint.ConstructionCommitService;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobProgress;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobStore;
import dev.anvilcraft.plasticraft.blueprint.ConstructionLedgerEntry;
import dev.anvilcraft.plasticraft.blueprint.ConstructionOverlayView;
import dev.anvilcraft.plasticraft.blueprint.ConstructionProjectionIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionDebris;
import dev.anvilcraft.plasticraft.blueprint.DemolitionPlanner;
import dev.anvilcraft.plasticraft.blueprint.StonecutterSmashAdapter;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.item.property.component.SavedEntity;
import dev.anvilcraft.plasticraft.allay.AllayFlightState;
import dev.anvilcraft.plasticraft.allay.AllayShortageStrategy;
import dev.anvilcraft.plasticraft.allay.tool.CollectionAllayToolBehavior;
import dev.anvilcraft.plasticraft.allay.tool.ConstructionAllayToolBehavior;
import dev.anvilcraft.plasticraft.allay.tool.DemolitionAllayToolBehavior;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.block.RedstoneWireBlock;
import dev.dubhe.anvilcraft.block.RedstoneWireNetworkManager;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

import java.util.ArrayList;
import java.util.List;
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

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A creative owner supplies any blueprint item without consuming inventory")
    static void creativeOwnerSuppliesAnyMaterial(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.CREATIVE);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startCobbleJob(helper, player, 2);
                check(countCobble(player) == 0, "creative setup must start without cobble");
                ConstructionBuildOp first = firstPlace(started.progress());
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), first, UUID.randomUUID()),
                    "creative extract must succeed without cobble in the inventory"
                );
                check(countCobble(player) == 0, "creative extract must not consume inventory");
                check(carriedCount(started.progress()) == 1, "ledger must still record the supplied cobble");
                check(
                    ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), first),
                    "creative deliver after extract must succeed"
                );
                ConstructionBuildOp second = firstOpenPlace(started.progress());
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), second, UUID.randomUUID()),
                    "creative extract must succeed again for the next block"
                );
                check(countCobble(player) == 0, "second creative extract must still leave inventory empty");
                ConstructionBlueprintService.cancel(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("creative extract setup failed: " + exception.reason());
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
                    AllayShortageStrategy.PAUSE,
                    new ItemStack(Items.COBBLESTONE)
                );
                ConstructionJob paused = ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId());
                check(paused != null && paused.state() == ConstructionJob.STATE_WAITING_MATERIAL,
                    "PAUSE shortage must enter WAITING_MATERIAL");

                ConstructionJobController.applyShortage(
                    helper.getLevel().getServer(),
                    paused,
                    started.progress(),
                    AllayShortageStrategy.SKIP,
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

    @GameTest(timeoutTicks = 200, batch = "zzz_construction_return")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "Stopping a job makes a loaded drone fly back and insert carry beside the owner")
    static void pauseReturnsCarryByFlyingToOwner(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.moveTo(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
        WorkingAllayEntity[] droneSlot = new WorkingAllayEntity[1];
        int[] beforeSlot = new int[1];
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                player.setNoGravity(true);
                player.moveTo(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
                StartedJob started = startCobbleJob(helper, player, 2, new BlockPos(5, 2, 5));
                player.getInventory().add(new ItemStack(Items.COBBLESTONE, 4));
                ConstructionBuildOp first = firstPlace(started.progress());
                WorkingAllayEntity drone = spawnConstructionAllay(
                    helper,
                    new Vec3(5.5D, 3.0D, 5.5D),
                    player,
                    0
                );
                drone.setNoGravity(true);
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), first, drone.getUUID()),
                    "extract before pause must succeed"
                );
                drone.setHostedCarry(first.material().copyWithCount(1));
                first.setStatus(ConstructionBuildOp.Status.LEASED);
                first.setLeaseAllay(drone.getUUID());
                drone.assign(started.job().jobId(), first.id());
                droneSlot[0] = drone;
                beforeSlot[0] = countCobble(player);
                check(
                    drone.distanceTo(player) > ConstructionJobController.reach(drone) + 0.5D,
                    "setup must place the drone away from the owner"
                );
                ConstructionBlueprintService.toggleActive(player, started.job().jobId());
                check(countCobble(player) == beforeSlot[0], "pause must not teleport carry into the inventory");
                check(!drone.hostedCarry().isEmpty(), "drone must keep hosted carry after pause");
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("pause return setup failed: " + exception.reason());
            }
        }).thenWaitUntil(() -> {
            WorkingAllayEntity drone = droneSlot[0];
            check(drone != null, "construction drone missing after pause");
            check(drone.hostedCarry().isEmpty(), "drone must empty carry after flying back");
            check(
                countCobble(player) == beforeSlot[0] + 1,
                "owner must receive the returned cobble after the drone arrives"
            );
            check(
                drone.distanceTo(player) <= ConstructionJobController.reach(drone) + 1.0D,
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
        WorkingAllayEntity[] droneSlot = new WorkingAllayEntity[1];
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
                WorkingAllayEntity drone = spawnConstructionAllay(
                    helper,
                    new Vec3(2.5D, 2.0D, 3.5D),
                    player,
                    0
                );
                drone.setNoGravity(true);
                droneSlot[0] = drone;
                check(
                    ConstructionAllayToolBehavior.tryClaim(
                        drone,
                        helper.getLevel(),
                        started.job(),
                        started.progress()
                    ),
                    "construction drone failed to claim: wait=" + drone.waitReason()

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
            WorkingAllayEntity drone = droneSlot[0];
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
                "allay must leave the construction site before hovering"
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
                        AllayShortageStrategy.PAUSE
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
                        AllayShortageStrategy.SKIP
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
                WorkingAllayEntity drone = spawnDemolitionAllay(
                    helper,
                    new Vec3(2.5D, 2.0D, 3.5D),
                    player,
                    0
                );
                drone.setNoGravity(true);
                StartedJob started = startCobbleJob(helper, player, 1, new BlockPos(3, 2, 3));
                jobSlot[0] = started.job().jobId();
                if (!DemolitionAllayToolBehavior.tryClaim(
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
                    WorkingAllayEntity drone = spawnCollectionAllay(
                        helper,
                        new Vec3(2.5D, 2.0D, 1.5D),
                        player,
                        0
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
                        CollectionAllayToolBehavior.tryClaim(drone, helper.getLevel(), collecting, started.progress()),
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
                    WorkingAllayEntity drone = spawnCollectionAllay(
                        helper,
                        new Vec3(2.5D, 2.0D, 1.5D),
                        player,
                        0
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
                            || countInAllay(drone, Items.COBBLESTONE) + countInAllay(drone, Items.STONE) > 0,
                        "the collector must keep the inhaled items"
                    );
                    CollectionAllayToolBehavior.INSTANCE.serverTick(drone);
                    AABB site = ConstructionJobController.worldBox(building);
                    if (site.inflate(2.0D).intersects(drone.getBoundingBox())) {
                        check(
                            drone.flightState() == AllayFlightState.FLYING,
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

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A chest with three diamonds plans PLACE plus CONTENT, deducts both, and commit writes the diamonds once")
    static void chestContentsDeductAndCommitOnce(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    chestWithDiamonds(helper.getLevel().registryAccess()),
                    "chest-diamonds",
                    new BlockPos(2, 2, 2)
                );
                player.getInventory().add(new ItemStack(Items.CHEST));
                player.getInventory().add(new ItemStack(Items.DIAMOND, 3));
                ConstructionBuildOp place = firstPlace(started.progress());
                ConstructionBuildOp content = firstKind(started.progress(), ConstructionBuildOp.Kind.CONTENT);
                check(content.parentId() == place.id(), "CONTENT must hang on the chest PLACE");
                check(content.material().getCount() == 3 && content.material().is(Items.DIAMOND),
                    "CONTENT must ask for the three diamonds");
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), place, UUID.randomUUID()),
                    "extracting the chest must succeed"
                );
                check(
                    ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), place),
                    "delivering the chest projection must succeed"
                );
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), content, UUID.randomUUID()),
                    "extracting the diamond stack must succeed"
                );
                check(countItem(player, Items.DIAMOND) == 0, "CONTENT extract must take the full diamond stack");
                check(
                    ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), content),
                    "delivering CONTENT must succeed after the parent PLACE"
                );
                ConstructionJobController.finish(
                    helper.getLevel().getServer(),
                    helper.getLevel(),
                    started.job(),
                    started.progress()
                );
                check(helper.getLevel().getBlockState(place.pos()).is(Blocks.CHEST), "commit must write the chest");
                BlockEntity blockEntity = helper.getLevel().getBlockEntity(place.pos());
                check(blockEntity instanceof Container, "committed chest must have a container");
                check(
                    countContainer((Container) blockEntity, Items.DIAMOND) == 3,
                    "committed chest must contain the deducted diamonds, not a copied NBT stack"
                );
                check(countItem(player, Items.DIAMOND) == 0, "commit must not return the diamonds");
                check(countItem(player, Items.CHEST) == 0, "commit must keep the chest consumed");
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("chest content setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Cancel after a delivered chest and undelivered contents returns the items without copying them")
    static void cancelDeliveredChestReturnsUndeliveredContents(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    chestWithDiamonds(helper.getLevel().registryAccess()),
                    "chest-cancel",
                    new BlockPos(2, 2, 2)
                );
                player.getInventory().add(new ItemStack(Items.CHEST));
                player.getInventory().add(new ItemStack(Items.DIAMOND, 3));
                ConstructionBuildOp place = firstPlace(started.progress());
                ConstructionBuildOp content = firstKind(started.progress(), ConstructionBuildOp.Kind.CONTENT);
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), place, UUID.randomUUID()),
                    "extracting the chest before cancel must succeed"
                );
                check(
                    ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), place),
                    "the chest must be delivered before cancel"
                );
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), content, UUID.randomUUID()),
                    "extracting undelivered contents must succeed"
                );
                ConstructionBlueprintService.cancel(player, started.job().jobId());
                check(helper.getLevel().getBlockState(place.pos()).is(Blocks.CHEST), "cancel must commit the delivered chest");
                BlockEntity blockEntity = helper.getLevel().getBlockEntity(place.pos());
                check(blockEntity instanceof Container, "cancelled chest must exist");
                check(
                    countContainer((Container) blockEntity, Items.DIAMOND) == 0,
                    "undelivered contents must not be copied into the committed chest"
                );
                check(countItem(player, Items.DIAMOND) == 3, "cancel must return the in-transit diamonds once");
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("chest cancel setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Adjacent delivered fences use the connected OverlayView collision instead of a lone post")
    static void adjacentFencesUseConnectedCollision(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    fencePairStructure(),
                    "fence-pair",
                    new BlockPos(1, 2, 2)
                );
                player.getInventory().add(new ItemStack(Items.OAK_FENCE, 2));
                for (ConstructionBuildOp op : started.progress().operations()) {
                    if (op.kind() != ConstructionBuildOp.Kind.PLACE) continue;
                    check(
                        ConstructionJobController.extractMaterial(player, started.progress(), op, UUID.randomUUID()),
                        "extracting a fence must succeed"
                    );
                    check(
                        ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), op),
                        "delivering a fence must succeed"
                    );
                }
                ConstructionBuildOp first = firstPlace(started.progress());
                ConstructionProjectionIndex.Collision collision = ConstructionProjectionIndex.at(
                    helper.getLevel(),
                    first.pos()
                );
                check(collision != null, "delivered fence must have a projection collision");
                check(
                    collision.worldShape().bounds().getXsize() > 0.4D,
                    "adjacent fences must use the connected collision, not a lone post"
                );
                VoxelShape expected = ConstructionProjectionIndex.projectionShape(
                    first.target(),
                    new ConstructionOverlayView(helper.getLevel(), started.progress().overlayStates()),
                    first.pos()
                );
                check(
                    Math.abs(expected.bounds().getXsize() - collision.worldShape().bounds().getXsize()) < 1.0E-4D,
                    "delivered fence collision must match the OverlayView connected shape"
                );
                cancelQuietly(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("fence collision setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "A giant anvil plans one core PLACE plus ATTACHED parts, consumes one item, and commits every part")
    static void giantAnvilFoldsToOneCore(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    giantAnvilStructure(),
                    "giant-anvil",
                    new BlockPos(2, 2, 2)
                );
                int places = countKind(started.progress(), ConstructionBuildOp.Kind.PLACE);
                int attached = countKind(started.progress(), ConstructionBuildOp.Kind.ATTACHED);
                check(places == 1, "giant anvil must fold to one core PLACE, was " + places);
                check(
                    attached == Cube3x3PartHalf.values().length - 1,
                    "remaining giant anvil parts must be ATTACHED, was " + attached
                );
                player.getInventory().add(new ItemStack(ModBlocks.GIANT_ANVIL.asItem()));
                ConstructionBuildOp core = firstPlace(started.progress());
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), core, UUID.randomUUID()),
                    "extracting the giant anvil core must succeed once"
                );
                check(countItem(player, ModBlocks.GIANT_ANVIL.asItem()) == 0, "parts must not deduct extra cores");
                check(
                    ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), core),
                    "delivering the core must activate the whole multipart"
                );
                for (ConstructionBuildOp op : started.progress().operations()) {
                    if (op.kind() == ConstructionBuildOp.Kind.ATTACHED) {
                        check(
                            op.status() == ConstructionBuildOp.Status.DELIVERED,
                            "core delivery must deliver corner ATTACHED parts too: " + op.pos()
                        );
                    }
                }
                ConstructionJobController.finish(
                    helper.getLevel().getServer(),
                    helper.getLevel(),
                    started.job(),
                    started.progress()
                );
                GiantAnvilBlock block = ModBlocks.GIANT_ANVIL.get();
                BlockPos bottom = core.pos().subtract(core.target().getValue(GiantAnvilBlock.HALF).getOffset());
                for (Cube3x3PartHalf part : block.getParts()) {
                    BlockState state = helper.getLevel().getBlockState(bottom.offset(part.getOffset()));
                    check(
                        state.is(block) && state.getValue(GiantAnvilBlock.HALF) == part,
                        "committed giant anvil is missing part " + part
                    );
                }
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("giant anvil setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 80, batch = "zzz_construction")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "Quiet commit writes AnvilCraft wire port overrides so topology updates keep blueprint arms")
    static void quietCommitRestoresRedstoneWithoutBreakingCactus(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        List<ConstructionBuildOp> wireOps = new ArrayList<>();
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    redstoneMachineStructure(),
                    "redstone-machine",
                    new BlockPos(2, 2, 2)
                );
                player.getInventory().add(new ItemStack(ModBlocks.REDSTONE_WIRE.asItem(), 3));
                player.getInventory().add(new ItemStack(Items.REPEATER));
                player.getInventory().add(new ItemStack(Items.COMPARATOR));
                player.getInventory().add(new ItemStack(Items.PISTON));
                player.getInventory().add(new ItemStack(Items.COBBLESTONE));
                ConstructionBuildOp cobble = null;
                for (ConstructionBuildOp op : started.progress().operations()) {
                    if (op.kind() != ConstructionBuildOp.Kind.PLACE) continue;
                    check(
                        ConstructionJobController.extractMaterial(player, started.progress(), op, UUID.randomUUID()),
                        "extracting " + op.material() + " must succeed"
                    );
                    check(
                        ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), op),
                        "delivering " + op.target().getBlock() + " must succeed"
                    );
                    if (op.target().is(Blocks.COBBLESTONE)) {
                        cobble = op;
                    }
                }
                check(cobble != null, "redstone fixture must include a cobble cell for the cactus");
                boolean sawWire = false;
                for (ConstructionBuildOp op : started.progress().operations()) {
                    if (!(op.target().getBlock() instanceof RedstoneWireBlock)) {
                        continue;
                    }
                    ConstructionProjectionIndex.Collision collision = ConstructionProjectionIndex.at(
                        helper.getLevel(),
                        op.pos()
                    );
                    check(collision != null, "delivered wire must stay in the projection index");
                    check(
                        sameAnvilCraftWirePorts(collision.state(), op.target()),
                        "delivered wire must keep the blueprint ports and must not grow extras toward the comparator"
                    );
                    sawWire = true;
                }
                check(sawWire, "fixture must deliver AnvilCraft redstone wire");
                wireOps.clear();
                for (ConstructionBuildOp op : started.progress().operations()) {
                    if (op.target().getBlock() instanceof RedstoneWireBlock) {
                        wireOps.add(op);
                    }
                }
                helper.getLevel().setBlock(cobble.pos().west().below(), Blocks.SAND.defaultBlockState(), 3);
                helper.getLevel().setBlock(cobble.pos().west(), Blocks.CACTUS.defaultBlockState(), 3);
                ConstructionJobController.finish(
                    helper.getLevel().getServer(),
                    helper.getLevel(),
                    started.job(),
                    started.progress()
                );
                check(
                    helper.getLevel().getBlockState(cobble.pos().west()).is(Blocks.CACTUS),
                    "quiet commit must not break the adjacent cactus"
                );
                boolean sawPorts = false;
                boolean sawLocked = false;
                boolean sawComparator = false;
                boolean sawExtended = false;
                for (ConstructionBuildOp op : started.progress().operations()) {
                    if (op.status() != ConstructionBuildOp.Status.DELIVERED || !op.writesProjection()) {
                        continue;
                    }
                    BlockState written = helper.getLevel().getBlockState(op.pos());
                    if (written.getBlock() instanceof RedstoneWireBlock) {
                        check(
                            sameAnvilCraftWirePorts(written, op.target()),
                            "committed wire must restore the blueprint ports, not neighbour-inferred extras"
                        );
                        sawPorts = true;
                    }
                    if (written.hasProperty(RepeaterBlock.LOCKED)) {
                        check(written.getValue(RepeaterBlock.LOCKED), "committed repeater must stay locked");
                        sawLocked = true;
                    }
                    if (written.hasProperty(ComparatorBlock.MODE)) {
                        check(
                            written.getValue(ComparatorBlock.FACING) == Direction.WEST,
                            "committed comparator must keep its facing"
                        );
                        sawComparator = true;
                    }
                    if (written.hasProperty(PistonBaseBlock.EXTENDED)) {
                        check(written.getValue(PistonBaseBlock.EXTENDED), "committed piston must stay extended");
                        sawExtended = true;
                    }
                }
                check(
                    sawPorts && sawLocked && sawComparator && sawExtended,
                    "fixture must commit AnvilCraft wire, comparator, locked repeater and extended piston"
                );
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("redstone commit setup failed: " + exception.reason());
            }
        }).thenExecute(() -> {
            check(!wireOps.isEmpty(), "fixture must keep wire ops for the post-commit settle");
            for (ConstructionBuildOp op : wireOps) {
                helper.getLevel().updateNeighborsAt(op.pos(), ModBlocks.REDSTONE_WIRE.get());
                RedstoneWireNetworkManager.topologyChanged(helper.getLevel(), op.pos());
            }
        }).thenExecuteAfter(25, () -> {
            for (ConstructionBuildOp op : wireOps) {
                BlockState written = helper.getLevel().getBlockState(op.pos());
                check(
                    written.getBlock() instanceof RedstoneWireBlock,
                    "settled cell must still be AnvilCraft redstone wire"
                );
                check(
                    sameAnvilCraftWirePorts(written, op.target()),
                    "settled wire must keep blueprint ports after topology updates, not merge parallel lines"
                );
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A commit budget of one block per tick writes a recoverable log and publishes only once")
    static void commitLogPublishesOnceWithBudgetOne(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            int previous = ConstructionCommitService.blocksPerTick;
            ConstructionCommitService.blocksPerTick = 1;
            try {
                StartedJob started = startCobbleJob(helper, player, 2);
                player.getInventory().add(new ItemStack(Items.COBBLESTONE, 2));
                for (ConstructionBuildOp op : started.progress().operations()) {
                    if (op.kind() != ConstructionBuildOp.Kind.PLACE) continue;
                    check(
                        ConstructionJobController.extractMaterial(player, started.progress(), op, UUID.randomUUID()),
                        "extract before partitioned commit must succeed"
                    );
                    check(
                        ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), op),
                        "deliver before partitioned commit must succeed"
                    );
                }
                check(
                    !ConstructionCommitService.tick(helper.getLevel(), started.progress()),
                    "budget 1 must not finish on the first tick"
                );
                check(
                    started.progress().commitLog().phase() == ConstructionCommitLog.Phase.STATES,
                    "first tick must stay in STATES"
                );
                int written = 0;
                for (ConstructionBuildOp op : started.progress().operations()) {
                    if (op.writesProjection() && helper.getLevel().getBlockState(op.pos()).is(Blocks.COBBLESTONE)) {
                        written++;
                    }
                }
                check(written == 1, "budget 1 must write exactly one block on the first tick, was " + written);
                check(
                    ConstructionProjectionIndex.has(helper.getLevel(), firstPlace(started.progress()).pos()),
                    "projections must stay until the publish phase"
                );
                int ticks = 0;
                while (!ConstructionCommitService.tick(helper.getLevel(), started.progress()) && ticks++ < 32) {
                    // 分 tick 续写
                }
                check(
                    started.progress().commitLog().phase() == ConstructionCommitLog.Phase.DONE,
                    "partitioned commit must reach DONE"
                );
                check(
                    !ConstructionProjectionIndex.has(helper.getLevel(), firstPlace(started.progress()).pos()),
                    "publish must clear projections only once at the end"
                );
                ConstructionJobController.finish(
                    helper.getLevel().getServer(),
                    helper.getLevel(),
                    started.job(),
                    started.progress()
                );
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("commit log setup failed: " + exception.reason());
            } finally {
                ConstructionCommitService.blocksPerTick = previous;
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_adapt")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A water source takes a full bucket, writes water on commit, and returns the empty bucket")
    static void waterBucketDeductsAndReturnsEmpty(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    multiBlockStructure(List.of(BlockPos.ZERO), List.of(Blocks.WATER.defaultBlockState())),
                    "water-source",
                    new BlockPos(2, 2, 2)
                );
                player.getInventory().add(new ItemStack(Items.WATER_BUCKET));
                ConstructionBuildOp place = firstPlace(started.progress());
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), place, UUID.randomUUID()),
                    "a water bucket must supply the source cell"
                );
                check(countItem(player, Items.WATER_BUCKET) == 0, "the filled bucket must be consumed");
                check(
                    ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), place),
                    "delivering the water projection must succeed"
                );
                player.getInventory().add(place.returnStack().copy());
                ConstructionJobController.finish(
                    helper.getLevel().getServer(),
                    helper.getLevel(),
                    started.job(),
                    started.progress()
                );
                check(helper.getLevel().getBlockState(place.pos()).is(Blocks.WATER), "commit must write the water source");
                check(countItem(player, Items.BUCKET) == 1, "the empty bucket must come back");
                check(countItem(player, Items.WATER_BUCKET) == 0, "the filled bucket must stay consumed");
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("water bucket setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_adapt")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A partial cauldron keeps its exact millibuckets and does not accept a full bucket")
    static void partialCauldronDoesNotRoundToBucket(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    multiBlockStructure(
                        List.of(BlockPos.ZERO),
                        List.of(Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 1))
                    ),
                    "partial-cauldron",
                    new BlockPos(2, 2, 2)
                );
                ConstructionBuildOp fluid = firstKind(started.progress(), ConstructionBuildOp.Kind.FLUID);
                check(fluid.fluid().getAmount() > 0 && fluid.fluid().getAmount() < 1000, "level 1 must be a partial bucket");
                player.getInventory().add(new ItemStack(Items.WATER_BUCKET));
                check(
                    !ConstructionJobController.extractMaterial(player, started.progress(), fluid, UUID.randomUUID()),
                    "a full water bucket must not satisfy a partial millibucket fluid"
                );
                check(countItem(player, Items.WATER_BUCKET) == 1, "the unused bucket must stay in the inventory");
                ConstructionBlueprintService.cancel(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("partial cauldron setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_adapt")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A boat entity takes the boat item and spawns on commit")
    static void boatUsesRealBoatItem(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                assertBoatJob(helper, player);
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("boat adapter setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_adapt")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A creeper takes a spawn egg and appears only after commit")
    static void creeperUsesSpawnEgg(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                assertCreeperEggJob(helper, player);
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("creeper egg setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_adapt")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A matching resin capture supplies a creeper and returns 1-3 resin")
    static void resinCaptureReturnsResinThenSpawns(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                assertResinCreeperJob(
                    helper,
                    player,
                    captureStack(PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem()),
                    true,
                    "resin-creeper"
                );
                assertResinCreeperJob(
                    helper,
                    player,
                    captureStack(ModBlocks.RESIN_BLOCK.asItem()),
                    true,
                    "plain-resin-creeper"
                );
                assertResinCreeperJob(
                    helper,
                    player,
                    captureStack(PlasticraftBlocks.RESIN_ANVIL.asItem()),
                    false,
                    "resin-anvil-creeper"
                );
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("resin capture setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_adapt")
    @EmptyTemplate(value = "5x6x5", floor = true)
    @TestHolder(description = "An enclosed entity uses its own cell as the drone approach when every neighbour is a reserved PLACE")
    static void enclosedEntityUsesOwnCellApproach(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                CompoundTag nbt = new CompoundTag();
                nbt.putString("id", "minecraft:boat");
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    enclosedEntityStructure(nbt),
                    "enclosed-entity",
                    new BlockPos(1, 2, 1)
                );
                ConstructionBuildOp entity = firstKind(started.progress(), ConstructionBuildOp.Kind.ENTITY);
                int reservedNeighbors = 0;
                for (Direction direction : Direction.values()) {
                    if (isUndeliveredPlace(started.progress(), entity.pos().relative(direction))) {
                        reservedNeighbors++;
                    }
                }
                check(reservedNeighbors == 6, "the boat must be enclosed by six undelivered PLACE cells");
                BlockPos approach = ConstructionJobController.chooseApproach(
                    helper.getLevel(),
                    started.progress(),
                    entity
                );
                check(entity.pos().equals(approach), "an enclosed entity must use its own cell as the approach");
                ConstructionBlueprintService.cancel(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("enclosed entity setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_adapt")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A plastic entity deducts its item and contents separately")
    static void plasticEntityContentsUseRealMaterials(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                assertPlasticContentJob(helper, player);
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("plastic entity setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_adapt")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Players and item entities stay skipped and do not create ENTITY operations")
    static void transientEntitiesStaySkipped(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                CompoundTag item = new CompoundTag();
                item.putString("id", "minecraft:item");
                CompoundTag playerTag = new CompoundTag();
                playerTag.putString("id", "minecraft:player");
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    entityOnlyStructure(List.of(item, playerTag)),
                    "transient-entities",
                    new BlockPos(2, 2, 2)
                );
                check(countKind(started.progress(), ConstructionBuildOp.Kind.ENTITY) == 0, "transient entries must not plan ENTITY ops");
                check(started.progress().incomplete(), "skipped transients must mark the job incomplete");
                ConstructionBlueprintService.cancel(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("transient entity setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_adapt")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Cancel before delivering a water bucket returns the filled bucket and does not write water")
    static void cancelInTransitWaterReturnsFilledBucket(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    multiBlockStructure(List.of(BlockPos.ZERO), List.of(Blocks.WATER.defaultBlockState())),
                    "water-cancel",
                    new BlockPos(2, 2, 2)
                );
                player.getInventory().add(new ItemStack(Items.WATER_BUCKET));
                ConstructionBuildOp place = firstPlace(started.progress());
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), place, UUID.randomUUID()),
                    "extracting the water bucket before cancel must succeed"
                );
                ConstructionBlueprintService.cancel(player, started.job().jobId());
                check(!helper.getLevel().getBlockState(place.pos()).is(Blocks.WATER), "undelivered water must not be written");
                check(countItem(player, Items.WATER_BUCKET) == 1, "cancel must return the filled bucket");
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("in-transit water cancel setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    private static void assertBoatJob(ExtendedGameTestHelper helper, GameTestPlayer player)
        throws ConstructionBlueprintException {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:boat");
        StartedJob started = startStructureJob(
            helper,
            player,
            entityOnlyStructure(List.of(nbt)),
            "oak-boat",
            new BlockPos(2, 2, 2)
        );
        ConstructionBuildOp entity = firstKind(started.progress(), ConstructionBuildOp.Kind.ENTITY);
        player.getInventory().add(new ItemStack(Items.OAK_BOAT));
        check(
            ConstructionJobController.extractMaterial(player, started.progress(), entity, UUID.randomUUID()),
            "the oak boat item must supply the boat entity"
        );
        check(
            ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), entity),
            "delivering the boat projection must succeed"
        );
        ConstructionJobController.finish(
            helper.getLevel().getServer(),
            helper.getLevel(),
            started.job(),
            started.progress()
        );
        check(
            !helper.getLevel().getEntitiesOfClass(Boat.class, new AABB(entity.pos()).inflate(1.0D)).isEmpty(),
            "commit must spawn the oak boat"
        );
        discardNearby(helper, entity.pos());
    }

    private static void assertCreeperEggJob(ExtendedGameTestHelper helper, GameTestPlayer player)
        throws ConstructionBlueprintException {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:creeper");
        StartedJob started = startStructureJob(
            helper,
            player,
            entityOnlyStructure(List.of(nbt)),
            "creeper-egg",
            new BlockPos(2, 2, 2)
        );
        ConstructionBuildOp entity = firstKind(started.progress(), ConstructionBuildOp.Kind.ENTITY);
        player.getInventory().add(new ItemStack(Items.CREEPER_SPAWN_EGG));
        check(
            ConstructionJobController.extractMaterial(player, started.progress(), entity, UUID.randomUUID()),
            "a creeper spawn egg must supply the creeper"
        );
        check(
            ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), entity),
            "delivering the creeper projection must succeed"
        );
        ConstructionJobController.finish(
            helper.getLevel().getServer(),
            helper.getLevel(),
            started.job(),
            started.progress()
        );
        check(
            !helper.getLevel().getEntities(EntityType.CREEPER, new AABB(entity.pos()).inflate(1.0D), creeper -> true).isEmpty(),
            "commit must spawn the creeper from the egg"
        );
        discardNearby(helper, entity.pos());
    }

    private static ItemStack captureStack(Item item) {
        CompoundTag captured = new CompoundTag();
        captured.putString("id", "minecraft:creeper");
        ItemStack stack = new ItemStack(item);
        stack.set(ModComponents.SAVED_ENTITY, new SavedEntity(captured, true));
        return stack;
    }

    private static void assertResinCreeperJob(
        ExtendedGameTestHelper helper,
        GameTestPlayer player,
        ItemStack capture,
        boolean expectResinReturn,
        String name
    ) throws ConstructionBlueprintException {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:creeper");
        StartedJob started = startStructureJob(
            helper,
            player,
            entityOnlyStructure(List.of(nbt)),
            name,
            new BlockPos(2, 2, 2)
        );
        ConstructionBuildOp entity = firstKind(started.progress(), ConstructionBuildOp.Kind.ENTITY);
        player.getInventory().add(capture);
        check(
            ConstructionJobController.extractMaterial(player, started.progress(), entity, UUID.randomUUID()),
            "a matching resin capture must supply the creeper when no egg is taken"
        );
        check(
            ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), entity),
            "delivering the resin creeper projection must succeed"
        );
        int resinBefore = countItem(player, ModItems.RESIN.get());
        player.getInventory().add(entity.returnStack().copy());
        int returned = countItem(player, ModItems.RESIN.get()) - resinBefore;
        if (expectResinReturn) {
            check(returned >= 1 && returned <= 3, "resin block delivery must return 1-3 resin");
        } else {
            check(returned == 0, "a captured resin anvil must be consumed without returning resin");
        }
        ConstructionJobController.finish(
            helper.getLevel().getServer(),
            helper.getLevel(),
            started.job(),
            started.progress()
        );
        check(
            !helper.getLevel().getEntities(EntityType.CREEPER, new AABB(entity.pos()).inflate(1.0D), found -> true).isEmpty(),
            "the real creeper must appear only after commit"
        );
        discardNearby(helper, entity.pos());
    }

    private static void assertPlasticContentJob(ExtendedGameTestHelper helper, GameTestPlayer player)
        throws ConstructionBlueprintException {
        HardenedResinCauldronEntity cauldron = PlasticraftEntities.HARDEND_RESIN_CAULDRON.get().create(helper.getLevel());
        check(cauldron != null, "failed to create a hardened resin cauldron entity");
        cauldron.getItemHandler().setStackInSlot(0, new ItemStack(Items.DIAMOND, 2));
        CompoundTag nbt = new CompoundTag();
        cauldron.saveWithoutId(nbt);
        nbt.putString("id", "anvilcraftplasticraft:hardend_resin_cauldron");
        if (cauldron.isAlive()) {
            cauldron.discard();
        }
        StartedJob started = startStructureJob(
            helper,
            player,
            entityOnlyStructure(List.of(nbt)),
            "plastic-cauldron",
            new BlockPos(2, 2, 2)
        );
        ConstructionBuildOp entity = firstKind(started.progress(), ConstructionBuildOp.Kind.ENTITY);
        ConstructionBuildOp content = firstKind(started.progress(), ConstructionBuildOp.Kind.CONTENT);
        player.getInventory().add(entity.material().copy());
        player.getInventory().add(new ItemStack(Items.DIAMOND, 2));
        check(
            ConstructionJobController.extractMaterial(player, started.progress(), entity, UUID.randomUUID()),
            "the plastic item must supply the cauldron entity"
        );
        check(
            ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), entity),
            "delivering the plastic entity projection must succeed"
        );
        check(
            ConstructionJobController.extractMaterial(player, started.progress(), content, UUID.randomUUID()),
            "the diamond contents must be deducted separately"
        );
        check(countItem(player, Items.DIAMOND) == 0, "plastic contents must come from the inventory");
        check(
            ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), content),
            "delivering plastic contents must succeed after the parent entity"
        );
        ConstructionJobController.finish(
            helper.getLevel().getServer(),
            helper.getLevel(),
            started.job(),
            started.progress()
        );
        HardenedResinCauldronEntity spawned = helper.getLevel().getEntities(
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get(),
            new AABB(entity.pos()).inflate(1.5D),
            found -> true
        ).stream().findFirst().orElse(null);
        check(spawned != null, "commit must spawn the hardened resin cauldron entity");
        check(
            spawned.getItemHandler().getStackInSlot(0).is(Items.DIAMOND)
                && spawned.getItemHandler().getStackInSlot(0).getCount() == 2,
            "committed plastic contents must be the deducted diamonds"
        );
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
        return startStructureJob(helper, player, cobbleStructure(count), "cobble-wall", relativeAnchor);
    }

    private static StartedJob startStructureJob(
        ExtendedGameTestHelper helper,
        GameTestPlayer player,
        CompoundTag structure,
        String name,
        BlockPos relativeAnchor
    ) throws ConstructionBlueprintException {
        ItemStack disk = new ItemStack(ModItems.STRUCTURE_DISK.get());
        ConstructionBlueprintService.importIntoDisk(
            helper.getLevel().getServer(),
            disk,
            structure,
            name,
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
        check(started != null && started.isActive(), "started " + name + " job must be active");
        ConstructionJobProgress progress = ConstructionJobStore.get(server).get(job.jobId());
        check(progress != null && progress.planned(), "started " + name + " job must be planned");
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

    private static CompoundTag chestWithDiamonds(HolderLookup.Provider registries) {
        CompoundTag item = (CompoundTag) new ItemStack(Items.DIAMOND, 3).save(registries);
        item.putByte("Slot", (byte) 0);
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:chest");
        ListTag items = new ListTag();
        items.add(item);
        nbt.put("Items", items);
        return singleBlockStructure(Blocks.CHEST.defaultBlockState(), nbt);
    }

    private static CompoundTag fencePairStructure() {
        return multiBlockStructure(List.of(
            new BlockPos(0, 0, 0),
            new BlockPos(1, 0, 0)
        ), List.of(
            Blocks.OAK_FENCE.defaultBlockState(),
            Blocks.OAK_FENCE.defaultBlockState()
        ));
    }

    private static CompoundTag giantAnvilStructure() {
        GiantAnvilBlock block = ModBlocks.GIANT_ANVIL.get();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        for (Cube3x3PartHalf part : block.getParts()) {
            Vec3i offset = part.getOffset();
            minX = Math.min(minX, offset.getX());
            minY = Math.min(minY, offset.getY());
            minZ = Math.min(minZ, offset.getZ());
            maxX = Math.max(maxX, offset.getX());
            maxY = Math.max(maxY, offset.getY());
            maxZ = Math.max(maxZ, offset.getZ());
            positions.add(new BlockPos(offset.getX(), offset.getY(), offset.getZ()));
            states.add(block.placedState(part, block.defaultBlockState()));
        }
        List<BlockPos> shifted = new ArrayList<>();
        for (BlockPos pos : positions) {
            shifted.add(pos.offset(-minX, -minY, -minZ));
        }
        return sizedStructure(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, shifted, states, List.of());
    }

    private static CompoundTag redstoneMachineStructure() {
        BlockState northSouthWire = ModBlocks.REDSTONE_WIRE.getDefaultState();
        return multiBlockStructure(List.of(
            new BlockPos(1, 0, 0),
            new BlockPos(1, 0, 1),
            new BlockPos(0, 0, 1),
            new BlockPos(1, 0, 2),
            new BlockPos(2, 0, 1),
            new BlockPos(3, 0, 1),
            new BlockPos(2, 0, 0),
            new BlockPos(3, 0, 0)
        ), List.of(
            Blocks.COBBLESTONE.defaultBlockState(),
            northSouthWire,
            Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.WEST),
            Blocks.REPEATER.defaultBlockState()
                .setValue(RepeaterBlock.LOCKED, true)
                .setValue(RepeaterBlock.FACING, Direction.SOUTH),
            Blocks.PISTON.defaultBlockState()
                .setValue(PistonBaseBlock.EXTENDED, true)
                .setValue(PistonBaseBlock.FACING, Direction.EAST),
            Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.FACING, Direction.EAST),
            northSouthWire,
            northSouthWire
        ));
    }

    private static CompoundTag singleBlockStructure(BlockState state, CompoundTag nbt) {
        return sizedStructure(1, 1, 1, List.of(BlockPos.ZERO), List.of(state), List.of(nbt));
    }

    private static CompoundTag multiBlockStructure(List<BlockPos> positions, List<BlockState> states) {
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;
        for (BlockPos pos : positions) {
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return sizedStructure(maxX + 1, maxY + 1, maxZ + 1, positions, states, List.of());
    }

    private static CompoundTag sizedStructure(
        int sizeX,
        int sizeY,
        int sizeZ,
        List<BlockPos> positions,
        List<BlockState> states,
        List<CompoundTag> blockEntities
    ) {
        CompoundTag tag = new CompoundTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(sizeX));
        size.add(IntTag.valueOf(sizeY));
        size.add(IntTag.valueOf(sizeZ));
        tag.put("size", size);
        ListTag palette = new ListTag();
        for (BlockState state : states) {
            palette.add(NbtUtils.writeBlockState(state));
        }
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        for (int index = 0; index < positions.size(); index++) {
            BlockPos pos = positions.get(index);
            CompoundTag entry = new CompoundTag();
            ListTag posTag = new ListTag();
            posTag.add(IntTag.valueOf(pos.getX()));
            posTag.add(IntTag.valueOf(pos.getY()));
            posTag.add(IntTag.valueOf(pos.getZ()));
            entry.put("pos", posTag);
            entry.putInt("state", index);
            if (index < blockEntities.size() && blockEntities.get(index) != null) {
                entry.put("nbt", blockEntities.get(index));
            }
            blocks.add(entry);
        }
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        return tag;
    }

    private static CompoundTag enclosedEntityStructure(CompoundTag entityNbt) {
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            positions.add(BlockPos.ZERO.relative(direction).offset(1, 1, 1));
            states.add(Blocks.SANDSTONE.defaultBlockState());
        }
        CompoundTag tag = sizedStructure(3, 3, 3, positions, states, List.of());
        CompoundTag entry = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(1.5D));
        pos.add(DoubleTag.valueOf(1.0D));
        pos.add(DoubleTag.valueOf(1.5D));
        entry.put("pos", pos);
        ListTag blockPos = new ListTag();
        blockPos.add(IntTag.valueOf(1));
        blockPos.add(IntTag.valueOf(1));
        blockPos.add(IntTag.valueOf(1));
        entry.put("blockPos", blockPos);
        entry.put("nbt", entityNbt);
        ListTag entities = new ListTag();
        entities.add(entry);
        tag.put("entities", entities);
        return tag;
    }

    private static boolean isUndeliveredPlace(ConstructionJobProgress progress, BlockPos pos) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() == ConstructionBuildOp.Kind.PLACE
                && op.status() != ConstructionBuildOp.Status.DELIVERED
                && op.status() != ConstructionBuildOp.Status.SKIPPED
                && op.pos().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    private static CompoundTag entityOnlyStructure(List<CompoundTag> entities) {
        CompoundTag tag = sizedStructure(1, 1, 1, List.of(BlockPos.ZERO), List.of(Blocks.AIR.defaultBlockState()), List.of());
        ListTag list = new ListTag();
        for (CompoundTag nbt : entities) {
            CompoundTag entry = new CompoundTag();
            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(0.5D));
            pos.add(DoubleTag.valueOf(0.0D));
            pos.add(DoubleTag.valueOf(0.5D));
            entry.put("pos", pos);
            ListTag blockPos = new ListTag();
            blockPos.add(IntTag.valueOf(0));
            blockPos.add(IntTag.valueOf(0));
            blockPos.add(IntTag.valueOf(0));
            entry.put("blockPos", blockPos);
            entry.put("nbt", nbt);
            list.add(entry);
        }
        tag.put("entities", list);
        return tag;
    }

    private static WorkingAllayEntity spawnConstructionAllay(
        ExtendedGameTestHelper helper,
        Vec3 relativePos,
        GameTestPlayer player,
        int ignoredEnergy
    ) {
        WorkingAllayEntity worker = AllayGameTests.spawnHatted(helper, relativePos, player);
        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.CRAB_CLAW.get()));
        return worker;
    }

    private static WorkingAllayEntity spawnCollectionAllay(
        ExtendedGameTestHelper helper,
        Vec3 relativePos,
        GameTestPlayer player,
        int ignoredEnergy
    ) {
        WorkingAllayEntity worker = AllayGameTests.spawnHatted(helper, relativePos, player);
        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.MAGNET.get()));
        return worker;
    }

    private static int countInAllay(WorkingAllayEntity worker, Item item) {
        int count = 0;
        for (ItemStack stack : worker.collectionInventory()) {
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static WorkingAllayEntity spawnDemolitionAllay(
        ExtendedGameTestHelper helper,
        Vec3 relativePos,
        GameTestPlayer player,
        int ignoredEnergy
    ) {
        WorkingAllayEntity worker = AllayGameTests.spawnHatted(helper, relativePos, player);
        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONECUTTER));
        return worker;
    }

    private static void discardNearby(ExtendedGameTestHelper helper, BlockPos pos) {
        for (Entity entity : helper.getLevel().getEntities(null, new AABB(pos).inflate(1.5D))) {
            if (entity instanceof GameTestPlayer) {
                continue;
            }
            entity.discard();
        }
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

    private static int countContainer(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
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

    private static boolean sameAnvilCraftWirePorts(BlockState written, BlockState target) {
        if (!(written.getBlock() instanceof RedstoneWireBlock)
            || !(target.getBlock() instanceof RedstoneWireBlock)) {
            return false;
        }
        for (int index = 0; index < RedstoneWireBlock.CONNECTION_PROPERTIES.size(); index++) {
            if (written.getValue(RedstoneWireBlock.CONNECTION_PROPERTIES.get(index)).isConnected()
                != target.getValue(RedstoneWireBlock.CONNECTION_PROPERTIES.get(index)).isConnected()) {
                return false;
            }
        }
        return true;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private record StartedJob(ConstructionJob job, ConstructionJobProgress progress) {
    }
}
