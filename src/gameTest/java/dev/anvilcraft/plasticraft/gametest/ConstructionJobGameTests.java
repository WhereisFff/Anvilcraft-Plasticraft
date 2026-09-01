package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.blueprint.BlueprintSource;
import dev.anvilcraft.plasticraft.blueprint.ConstructionAssembler;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintData;
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
import dev.anvilcraft.plasticraft.blueprint.ConstructionMaterialAccess;
import dev.anvilcraft.plasticraft.blueprint.ConstructionOverlayView;
import dev.anvilcraft.plasticraft.blueprint.ConstructionPermission;
import dev.anvilcraft.plasticraft.blueprint.ConstructionProjectionIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionDebris;
import dev.anvilcraft.plasticraft.blueprint.ConstructionEntityProjectionIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionEnclosure;
import dev.anvilcraft.plasticraft.blueprint.ConstructionTraffic;
import dev.anvilcraft.plasticraft.blueprint.ConstructionWaitReason;
import dev.anvilcraft.plasticraft.blueprint.ConstructionWorkerSpace;
import dev.anvilcraft.plasticraft.blueprint.DemolitionPlanner;
import dev.anvilcraft.plasticraft.blueprint.SignDecorationAdapter;
import dev.anvilcraft.plasticraft.blueprint.StonecutterSmashAdapter;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.item.property.component.SavedEntity;
import dev.anvilcraft.plasticraft.allay.AllayClearanceStrategy;
import dev.anvilcraft.plasticraft.allay.AllayDefaultHardHat;
import dev.anvilcraft.plasticraft.allay.AllayFlightPlanner;
import dev.anvilcraft.plasticraft.allay.AllayFlightState;
import dev.anvilcraft.plasticraft.allay.AllayHardHatTraits;
import dev.anvilcraft.plasticraft.allay.AllayShortageStrategy;
import dev.anvilcraft.plasticraft.allay.AllayWorkMotions;
import dev.anvilcraft.plasticraft.allay.AllayWorkRecord;
import dev.anvilcraft.plasticraft.allay.path.AllayPathPriority;
import dev.anvilcraft.plasticraft.allay.path.AllayPathSnapshot;
import dev.anvilcraft.plasticraft.allay.tool.AllayCapability;
import dev.anvilcraft.plasticraft.allay.tool.AllayToolDefinitions;
import dev.anvilcraft.plasticraft.allay.tool.CollectionAllayToolBehavior;
import dev.anvilcraft.plasticraft.allay.tool.ConstructionAllayToolBehavior;
import dev.anvilcraft.plasticraft.allay.tool.DemolitionAllayToolBehavior;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberStructure;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.dubhe.anvilcraft.block.ChuteBlock;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.block.MagneticChuteBlock;
import dev.dubhe.anvilcraft.block.RedstoneWireBlock;
import dev.dubhe.anvilcraft.block.RedstoneWireNetworkManager;
import dev.dubhe.anvilcraft.block.SimpleChuteBlock;
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
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.MinecartHopper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 覆盖单架无站建设/拆除/收集无人机施工闭环的服务器契约:台账、假方块碰撞、占用、安静提交、
 * 缺料策略、电量拒派、停止后飞回还物、同模板短时序交付,以及封堵、拆除与任务掉落回收。
 * `zzz_construction_lounge` 另锁单休息室入槽认领不启动、下方物流、创造板条箱无限供料与 20 gt 出库窄接口。
 * `zzz_construction_enclose` / `zzz_construction_flight` 覆盖防自封窄接口与绕障飞行,不扫描 128 格实体。
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
    @TestHolder(description = "Planning permission denial waits without skip and resumes from the original job after permission returns")
    static void planningPermissionWaitCannotBeSkipped(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        UUID[] jobId = {null};
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                ItemStack disk = deployDisk(helper, player, 1, new BlockPos(3, 2, 1));
                jobId[0] = ConstructionBlueprintData.get(disk)
                    .flatMap(ConstructionBlueprintData::jobId)
                    .orElseThrow();
                ConstructionJobIndex index = ConstructionJobIndex.get(helper.getLevel());
                ConstructionJob deployed = index.job(jobId[0]);
                check(deployed != null, "permission fixture must keep the deployed job");
                ConstructionPermission.setWorldPermissionProvider(
                    (level, pos, owner) -> !pos.equals(deployed.anchor())
                );
                ConstructionBlueprintService.start(player, jobId[0]);
                ConstructionJob waiting = index.job(jobId[0]);
                check(
                    waiting != null && waiting.state() == ConstructionJob.STATE_WAITING_PERMISSION,
                    "planning denial must enter WAITING_PERMISSION"
                );
                ConstructionJobProgress progress = ConstructionJobStore.get(helper.getLevel()).get(jobId[0]);
                check(progress != null && progress.planned(), "permission wait must retain the original plan");
                ConstructionJobController.resumeFromSkipWait(helper.getLevel().getServer(), waiting);
                check(
                    index.job(jobId[0]).state() == ConstructionJob.STATE_WAITING_PERMISSION,
                    "skip recovery must not bypass WAITING_PERMISSION"
                );

                ConstructionPermission.setWorldPermissionProvider(null);
                ConstructionJobController.tickJob(
                    helper.getLevel().getServer(),
                    helper.getLevel(),
                    index.job(jobId[0])
                );
                check(
                    index.job(jobId[0]).state() != ConstructionJob.STATE_WAITING_PERMISSION,
                    "restored permission must resume the retained job"
                );
                ConstructionJobController.pause(helper.getLevel().getServer(), index.job(jobId[0]));
                ConstructionPermission.setWorldPermissionProvider(
                    (level, pos, owner) -> !pos.equals(deployed.anchor())
                );
                ConstructionBlueprintService.start(player, jobId[0]);
                check(
                    index.job(jobId[0]).state() == ConstructionJob.STATE_WAITING_PERMISSION,
                    "restarting a retained plan must repeat the full permission precheck"
                );
                ConstructionPermission.setWorldPermissionProvider(null);
                ConstructionBlueprintService.cancel(player, jobId[0]);
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("planning permission setup failed: " + exception.reason());
            } finally {
                ConstructionPermission.setWorldPermissionProvider(null);
                if (jobId[0] != null) cancelQuietly(player, jobId[0]);
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A teammate leaving during an active lease immediately loses the operation and releases it for reassignment")
    static void departedTeammateLosesActiveLease(ExtendedGameTestHelper helper) {
        GameTestPlayer owner = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        UUID teammateOwner = UUID.fromString("00000000-0000-0000-0000-000000000213");
        UUID[] jobId = {null};
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                owner.setNoGravity(true);
                owner.moveTo(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
                StartedJob started = startCobbleJob(helper, owner, 1, new BlockPos(3, 2, 3));
                jobId[0] = started.job().jobId();
                WorkingAllayEntity worker = AllayGameTests.spawnHattedForOwner(
                    helper,
                    new Vec3(2.5D, 2.0D, 3.5D),
                    teammateOwner
                );
                worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.CRAB_CLAW.get()));
                ConstructionPermission.setCollaboratorProvider((server, first, second) ->
                    first.equals(teammateOwner) && second.equals(owner.getUUID())
                        || first.equals(owner.getUUID()) && second.equals(teammateOwner)
                );
                ConstructionAllayToolBehavior.INSTANCE.serverTick(worker);
                check(worker.waitReason() != ConstructionWaitReason.SOURCE,
                    "the teammate worker incorrectly required its own offline owner as the material source");
                ConstructionBuildOp leased = started.progress().operation(worker.taskOpId());
                check(leased != null && leased.leaseAllay().filter(worker.getUUID()::equals).isPresent(),
                    "the teammate fixture did not hold the operation lease");

                ConstructionPermission.setCollaboratorProvider(null);
                check(!ConstructionJobController.canContinueJob(worker, started.progress()),
                    "the departed teammate retained its active lease permission");
                ConstructionAllayToolBehavior.INSTANCE.serverTick(worker);
                check(worker.assignedJobId().isEmpty(), "revocation did not clear the departed teammate assignment");
                check(leased.leaseAllay().isEmpty(), "revocation did not release the operation lease");
                check(leased.status() == ConstructionBuildOp.Status.PENDING,
                    "the released operation was not returned to pending");
                ConstructionBlueprintService.cancel(owner, jobId[0]);
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("team lease revocation setup failed: " + exception.reason());
            } finally {
                ConstructionPermission.setCollaboratorProvider(null);
                if (jobId[0] != null) cancelQuietly(owner, jobId[0]);
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_collection")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A task collector leaving after pickup drops its unsettled inventory instead of unloading it")
    static void departedTaskCollectorDropsPickedInventory(ExtendedGameTestHelper helper) {
        GameTestPlayer owner = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        UUID teammateOwner = UUID.fromString("00000000-0000-0000-0000-000000000214");
        UUID jobId = UUID.randomUUID();
        ServerLevel level = helper.getLevel();
        BlockPos targetPos = helper.absolutePos(new BlockPos(3, 2, 3));
        ConstructionJob job = new ConstructionJob(
            jobId,
            owner.getUUID(),
            ConstructionJob.STATE_COLLECTING_DEBRIS,
            "departed-collector",
            level.dimension(),
            targetPos,
            Rotation.NONE,
            Mirror.NONE,
            "departed-collector",
            new Vec3i(1, 1, 1),
            BlueprintSource.VANILLA_FILE,
            false,
            false
        );
        ConstructionJobProgress progress = ConstructionJobStore.get(level).getOrCreate(jobId);
        progress.setPlanned(true);
        ConstructionBuildOp operation = progress.addOperation(
            targetPos,
            Blocks.STONE.defaultBlockState(),
            ItemStack.EMPTY,
            ConstructionBuildOp.Kind.DEMOLISH,
            ConstructionBuildOp.Status.DELIVERED
        );
        progress.addDebrisSpawned(operation.id(), 1);
        ItemStack marked = new ItemStack(Items.COBBLESTONE);
        ConstructionDebris.mark(marked, jobId, operation.id());
        ItemEntity drop = new ItemEntity(
            level,
            targetPos.getX() + 0.5D,
            targetPos.getY() + 0.2D,
            targetPos.getZ() + 0.5D,
            marked
        );
        drop.setDeltaMovement(Vec3.ZERO);
        drop.setPickUpDelay(0);
        check(level.addFreshEntity(drop), "failed to spawn the marked collection fixture");
        ConstructionJobIndex.get(level).put(job);

        WorkingAllayEntity worker = AllayGameTests.spawnHattedForOwner(
            helper,
            new Vec3(2.5D, 2.2D, 3.5D),
            teammateOwner
        );
        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.MAGNET.get()));
        try {
            ConstructionPermission.setCollaboratorProvider((server, first, second) ->
                first.equals(teammateOwner) && second.equals(owner.getUUID())
                    || first.equals(owner.getUUID()) && second.equals(teammateOwner)
            );
            check(
                CollectionAllayToolBehavior.tryClaim(worker, level, job, progress),
                "the teammate collector failed to claim the marked drop"
            );
            for (int attempt = 0; attempt < 12 && !worker.hasCollectionItems(); attempt++) {
                worker.tickCount += WorkingAllayEntity.ACTION_INTERVAL_TICKS;
                CollectionAllayToolBehavior.INSTANCE.serverTick(worker);
            }
            check(worker.hasCollectionItems(), "the teammate collector did not pick up the marked drop");
            check(worker.assignedJobId().filter(jobId::equals).isPresent(),
                "task provenance was cleared before the picked inventory could be authorized for unloading");
            check(!drop.isAlive(), "the picked marked drop remained in the world");

            ConstructionPermission.setCollaboratorProvider(null);
            CollectionAllayToolBehavior.INSTANCE.serverTick(worker);

            check(worker.assignedJobId().isEmpty(), "the departed collector kept its task assignment");
            check(!worker.hasCollectionItems(), "the departed collector kept unsettled collection inventory");
            check(countItem(owner, Items.COBBLESTONE) == 0,
                "the departed collector unloaded task items to a player after permission loss");
            List<ItemEntity> returned = level.getEntitiesOfClass(
                ItemEntity.class,
                worker.getBoundingBox().inflate(2.0D),
                item -> item.isAlive() && item.getItem().is(Items.COBBLESTONE)
            );
            check(returned.stream().mapToInt(item -> item.getItem().getCount()).sum() == 1,
                "permission revocation did not materialize exactly one picked task item");
            check(returned.stream().noneMatch(item -> ConstructionDebris.isMarked(item.getItem())),
                "permission revocation restored a task marker to the settled item");
        } finally {
            ConstructionPermission.setCollaboratorProvider(null);
            ConstructionTraffic.release(level, worker.getUUID());
            ConstructionJobIndex.get(level).remove(jobId);
            ConstructionJobStore.get(level).remove(jobId);
        }
        helper.succeed();
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
    @TestHolder(description = "Construction operation indexes and cached status summaries stay coherent after mutation and reload")
    static void operationIndexesAndStatusCachesStayCoherent(ExtendedGameTestHelper helper) {
        ConstructionJobProgress progress = new ConstructionJobProgress(UUID.randomUUID());
        BlockPos parentPos = helper.absolutePos(new BlockPos(1, 2, 1));
        ConstructionBuildOp parent = progress.addOperation(
            parentPos,
            Blocks.COBBLESTONE.defaultBlockState(),
            new ItemStack(Items.COBBLESTONE),
            ConstructionBuildOp.Kind.PLACE,
            ConstructionBuildOp.Status.PENDING
        );
        check(progress.childrenOf(parent).isEmpty(), "new parent must start without indexed children");
        ConstructionBuildOp child = progress.addOperation(
            parentPos.above(),
            Blocks.COBBLESTONE.defaultBlockState(),
            ItemStack.EMPTY,
            ConstructionBuildOp.Kind.ATTACHED,
            ConstructionBuildOp.Status.PENDING
        );
        child.setParentId(parent.id());
        check(progress.operation(parent.id()) == parent, "operation id index must return the original parent");
        check(progress.parentOf(child) == parent, "parent lookup must use the updated topology");
        check(progress.childrenOf(parent).contains(child), "child index must refresh after assigning a parent");
        check(!progress.allPlaceResolved(), "pending indexed operations must keep construction open");

        parent.setStatus(ConstructionBuildOp.Status.DELIVERED);
        child.setStatus(ConstructionBuildOp.Status.DELIVERED);
        check(progress.allPlaceResolved(), "status summary must refresh after delivery");
        ConstructionBuildOp demolition = progress.addOperation(
            parentPos.east(),
            Blocks.STONE.defaultBlockState(),
            ItemStack.EMPTY,
            ConstructionBuildOp.Kind.DEMOLISH,
            ConstructionBuildOp.Status.PENDING
        );
        check(!progress.allDemolishResolved(), "non-shell demolition must remain open");
        demolition.setShell(true);
        check(progress.allDemolishResolved(), "shell mutation must invalidate demolition status summary");

        CompoundTag saved = progress.save(helper.getLevel().registryAccess());
        ConstructionJobProgress loaded = ConstructionJobProgress.load(saved, helper.getLevel().registryAccess());
        ConstructionBuildOp loadedParent = loaded.operation(parent.id());
        ConstructionBuildOp loadedChild = loaded.operation(child.id());
        check(loadedParent != null && loadedChild != null, "operation id indexes must rebuild after load");
        check(loaded.parentOf(loadedChild) == loadedParent, "parent index must rebuild after load");
        check(loaded.childrenOf(loadedParent).contains(loadedChild), "child index must rebuild after load");
        check(loaded.allPlaceResolved(), "loaded status summary must match saved delivered operations");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Reloading world data resumes a job once: delivered seal, demolition and carried ledger are neither replayed nor duplicated")
    static void reloadResumesWithoutReplayingDeliveredWork(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID jobId = UUID.randomUUID();
        UUID allayId = UUID.randomUUID();
        ConstructionJobProgress progress = new ConstructionJobProgress(jobId);
        progress.setPlanned(true);
        BlockPos sealPos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos demolishPos = helper.absolutePos(new BlockPos(2, 2, 1));
        BlockPos deliveredPos = helper.absolutePos(new BlockPos(3, 2, 1));
        BlockPos pendingPos = helper.absolutePos(new BlockPos(1, 2, 2));
        // 重启前的真实世界结果:填充块已经放下,拆除格已经砸空
        level.setBlockAndUpdate(sealPos, Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(demolishPos, Blocks.AIR.defaultBlockState());

        progress.addOperation(
            sealPos,
            Blocks.AIR.defaultBlockState(),
            new ItemStack(Items.DIRT),
            ConstructionBuildOp.Kind.SEAL,
            ConstructionBuildOp.Status.DELIVERED
        );
        progress.addOperation(
            demolishPos,
            Blocks.STONE.defaultBlockState(),
            ItemStack.EMPTY,
            ConstructionBuildOp.Kind.DEMOLISH,
            ConstructionBuildOp.Status.DELIVERED
        );
        ConstructionBuildOp delivered = progress.addOperation(
            deliveredPos,
            Blocks.COBBLESTONE.defaultBlockState(),
            new ItemStack(Items.COBBLESTONE),
            ConstructionBuildOp.Kind.PLACE,
            ConstructionBuildOp.Status.DELIVERED
        );
        ConstructionBuildOp pending = progress.addOperation(
            pendingPos,
            Blocks.COBBLESTONE.defaultBlockState(),
            new ItemStack(Items.COBBLESTONE),
            ConstructionBuildOp.Kind.PLACE,
            ConstructionBuildOp.Status.PENDING
        );
        progress.addLedger(pending.id(), new ItemStack(Items.COBBLESTONE), allayId);

        CompoundTag saved = progress.save(level.registryAccess());
        ConstructionJobProgress reloaded = ConstructionJobProgress.load(saved, level.registryAccess());

        check(reloaded.allSealResolved(), "a delivered seal must stay resolved after reload");
        check(
            ConstructionJobController.nextAssignableSeal(level, reloaded) == null,
            "reload must not re-dispatch an already placed seal fill"
        );
        check(
            level.getBlockState(sealPos).is(Blocks.DIRT),
            "the fill block placed before the restart must stay in the world"
        );
        check(reloaded.allDemolishResolved(), "a delivered demolition must stay resolved after reload");
        check(
            ConstructionJobController.nextAssignableDemolish(level, reloaded) == null,
            "reload must not re-dispatch an already smashed cell"
        );
        check(
            level.getBlockState(demolishPos).isAir(),
            "an already demolished cell must not be restored by reload"
        );
        check(
            reloaded.carriedEntries(allayId).size() == 1,
            "reload must keep exactly one carried ledger entry, was " + reloaded.carriedEntries(allayId).size()
        );
        ItemStack carried = reloaded.carriedBy(allayId, pending.id());
        check(
            carried != null && carried.getCount() == 1,
            "the reloaded ledger must still describe the same in-transit stack"
        );
        check(
            reloaded.isCarriedBy(allayId, pending.id()),
            "the reloaded operation must still be recognised as already claimed by that allay"
        );
        check(
            reloaded.hasCarriedMaterial(pending.id()),
            "the reloaded unclaimed index must be rebuilt from the ledger, not treat the cell as free"
        );
        check(!reloaded.allPlaceResolved(), "the still pending cell must remain buildable after reload");
        check(reloaded.hasDelivered(), "reload must keep the delivered operation counted");

        // 投影补发必须幂等:同一格重复恢复不能在索引里留下第二份
        Map<Long, BlockState> overlay = reloaded.overlayStates();
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                check(
                    ConstructionProjectionIndex.tryDeliver(
                        level,
                        jobId,
                        deliveredPos,
                        delivered.target(),
                        overlay
                    ),
                    "projection recovery must accept the delivered cell on attempt " + attempt
                );
            }
            check(
                ConstructionProjectionIndex.deliveredIn(level, jobId).size() == 1,
                "repeated projection recovery must not duplicate the delivered cell"
            );
        } finally {
            ConstructionProjectionIndex.clearJob(level, jobId);
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A commit interrupted by a restart resumes from the saved cursor and publishes exactly once")
    static void reloadedCommitLogResumesAndPublishesOnce(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        UUID[] jobId = {null};
        helper.startSequence().thenExecuteAfter(5, () -> {
            int previous = ConstructionCommitService.blocksPerTick;
            ConstructionCommitService.blocksPerTick = 1;
            try {
                StartedJob started = startCobbleJob(helper, player, 2);
                jobId[0] = started.job().jobId();
                player.getInventory().add(new ItemStack(Items.COBBLESTONE, 2));
                for (ConstructionBuildOp op : started.progress().operations()) {
                    if (op.kind() != ConstructionBuildOp.Kind.PLACE) continue;
                    check(
                        ConstructionJobController.extractMaterial(player, started.progress(), op, UUID.randomUUID()),
                        "reload commit setup must extract both blocks"
                    );
                    check(
                        ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), op),
                        "reload commit setup must deliver both projections"
                    );
                }
                check(
                    !ConstructionCommitService.tick(helper.getLevel(), started.progress()),
                    "budget one must leave the commit unfinished before the restart"
                );

                HolderLookup.Provider registries = helper.getLevel().registryAccess();
                ConstructionJobProgress resumed = ConstructionJobProgress.load(
                    started.progress().save(registries),
                    registries
                );
                check(
                    resumed.commitLog().phase() == ConstructionCommitLog.Phase.STATES,
                    "the reloaded commit log must resume in STATES"
                );
                check(
                    resumed.commitLog().nextIndex() == 1,
                    "the reloaded commit log must resume from the saved cursor, was "
                        + resumed.commitLog().nextIndex()
                );

                int ticks = 0;
                while (!ConstructionCommitService.tick(helper.getLevel(), resumed) && ticks++ < 64) {
                    // 分 tick 续写剩余分区
                }
                check(
                    resumed.commitLog().phase() == ConstructionCommitLog.Phase.DONE,
                    "the reloaded commit must reach DONE instead of restarting from the first block"
                );
                ConstructionBuildOp firstOp = firstPlace(resumed);
                BlockPos first = firstOp.pos();
                BlockPos second = firstPlaceAfter(resumed, firstOp.id()).pos();
                check(
                    helper.getLevel().getBlockState(first).is(Blocks.COBBLESTONE)
                        && helper.getLevel().getBlockState(second).is(Blocks.COBBLESTONE),
                    "resuming from the saved cursor must still finish every delivered block"
                );
                check(
                    !ConstructionProjectionIndex.has(helper.getLevel(), first),
                    "the resumed commit must clear projections at its single publish point"
                );

                // 发布点只能过一次:再 tick 已完成的日志不得重写世界
                helper.getLevel().setBlockAndUpdate(second, Blocks.AIR.defaultBlockState());
                check(
                    ConstructionCommitService.tick(helper.getLevel(), resumed),
                    "a finished commit log must report done without another publish"
                );
                check(
                    helper.getLevel().getBlockState(second).isAir(),
                    "a finished commit log must not write the region a second time"
                );
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("reload commit setup failed: " + exception.reason());
            } finally {
                ConstructionCommitService.blocksPerTick = previous;
                if (jobId[0] != null) cancelQuietly(player, jobId[0]);
            }
        }).thenSucceed();
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
    @TestHolder(description = "A hopper minecart does not block rail projection delivery")
    static void railDeliverIgnoresHopperMinecart(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        MinecartHopper cart = new MinecartHopper(EntityType.HOPPER_MINECART, level);
        cart.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        check(level.addFreshEntity(cart), "failed to add hopper minecart");
        BlockState rail = Blocks.RAIL.defaultBlockState();
        UUID jobId = UUID.randomUUID();
        check(
            ConstructionProjectionIndex.tryDeliver(level, jobId, pos, rail, Map.of(pos.asLong(), rail)),
            "a rail must deliver under a hopper minecart"
        );
        ConstructionProjectionIndex.clearJob(level, jobId);
        cart.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Empty-collision targets such as redstone dust create no fake collision")
    static void emptyCollisionTargetCreatesNoFakeCollision(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID jobId = UUID.randomUUID();
        Map<BlockPos, BlockState> passable = Map.of(
            helper.absolutePos(new BlockPos(1, 2, 1)), Blocks.REDSTONE_WIRE.defaultBlockState(),
            helper.absolutePos(new BlockPos(2, 2, 1)), Blocks.RAIL.defaultBlockState(),
            helper.absolutePos(new BlockPos(3, 2, 1)), Blocks.SHORT_GRASS.defaultBlockState(),
            helper.absolutePos(new BlockPos(1, 2, 2)), Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(BlockStateProperties.OPEN, true)
        );
        try {
            for (Map.Entry<BlockPos, BlockState> entry : passable.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState state = entry.getValue();
                check(
                    state.getCollisionShape(level, pos).isEmpty(),
                    "test state must have empty collision: " + state
                );
                check(
                    ConstructionProjectionIndex.tryDeliver(
                        level,
                        jobId,
                        pos,
                        state,
                        Map.of(pos.asLong(), state)
                    ),
                    "empty-collision projection must still be recorded: " + state
                );
                check(!hasCollision(level, new AABB(pos)), "projection injected fake collision: " + state);
            }
        } finally {
            ConstructionProjectionIndex.clearJob(level, jobId);
        }
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

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A repeatedly unreachable target is deferred with its material returned, never finished incomplete")
    static void unreachableTargetIsDeferredInsteadOfSkipped(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startCobbleJob(helper, player, 1);
                try {
                    ServerLevel level = helper.getLevel();
                    WorkingAllayEntity worker = spawnConstructionAllay(
                        helper,
                        new Vec3(1.5D, 2.2D, 3.5D),
                        null,
                        0
                    );
                    player.getInventory().add(new ItemStack(Items.COBBLESTONE, 1));
                    ConstructionBuildOp op = firstPlace(started.progress());
                    check(
                        ConstructionJobController.extractMaterial(player, started.progress(), op, worker.getUUID()),
                        "取料必须成功，本例要复现悦灵手上已托管材料的处境"
                    );
                    worker.setHostedCarry(new ItemStack(Items.COBBLESTONE, 1));
                    // 卡住时只释放租约不足以脱困:托管台账仍记着材料，下一 tick 同一只悦灵立刻重领同一操作
                    check(
                        ConstructionJobController.nextAssignableCarried(
                            level,
                            started.progress(),
                            worker
                        ) == op,
                        "释放租约后托管材料必须仍能被同一只悦灵重领，否则本回归失去意义"
                    );
                    for (int strike = 1; strike <= 3; strike++) {
                        check(op.noteUnreachable() == strike, "连续确认飞不到必须逐次累计");
                    }
                    op.clearUnreachable();
                    check(op.noteUnreachable() == 1, "重新够得到目标后不可达记录必须作废");

                    ConstructionJobController.deferUnreachable(level, started.progress(), op, worker);
                    check(op.isDeferred(level.getGameTime()), "确认不可达的目标必须压一段重试退避");
                    check(op.status() == ConstructionBuildOp.Status.PENDING, "退避的位置必须退回待办");
                    check(op.isOpen(), "退避不得作废这个位置");
                    check(op.leaseAllay().isEmpty(), "退避必须同时清掉租约");
                    check(!started.progress().incomplete(), "飞不到不得计入残缺");
                    check(!started.progress().allPlaceResolved(), "退避期间施工阶段必须仍有待办，任务不能收敛到完成");
                    check(
                        started.progress().stalledByUnreachable(level.getGameTime()),
                        "剩余位置全在退避里时必须能对外报不可达"
                    );
                    // 材料留在悦灵手上会一直锁着这个位置,必须按台账退回料源,别的悦灵才能重新取料重规划
                    check(worker.hostedCarry().isEmpty(), "退避必须收走悦灵手上的托管材料");
                    check(
                        player.getInventory().countItem(Items.COBBLESTONE) == 1,
                        "退回的材料必须回到料源，不能凭空消失"
                    );
                    check(
                        ConstructionJobController.nextAssignableCarried(
                            level,
                            started.progress(),
                            worker
                        ) == null,
                        "退避期间不得靠托管台账重领同一操作，否则悦灵仍在原地空转"
                    );
                    // 先验退避结束后能重新派发:轮空的派发会给扫描压 5 刻退避，同刻再问必然还是轮空
                    op.clearUnreachable();
                    check(
                        ConstructionJobController.nextAssignable(level, started.progress()) == op,
                        "退避结束后这个位置必须重新可派发，由别的悦灵换个方向再试"
                    );

                    ConstructionJobController.deferUnreachable(level, started.progress(), op, worker);
                    check(
                        ConstructionJobController.nextAssignable(level, started.progress()) == null,
                        "退避期间派发必须轮空"
                    );
                    ConstructionJobController.tickJob(level.getServer(), level, started.job());
                    ConstructionJob building = ConstructionJobIndex.get(level).job(started.job().jobId());
                    check(
                        building != null && building.state() == ConstructionJob.STATE_BUILDING,
                        "还有位置没建成时任务必须留在施工阶段"
                    );
                } finally {
                    cancelQuietly(player, started.job().jobId());
                }
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("unreachable defer setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "9x5x9", floor = true)
    @TestHolder(description = "A same-layer ring hands out the nearest open target instead of the scan-order first")
    static void ringAssignsNearestSameLayerTarget(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    ringStructure(5),
                    "cobble-ring",
                    new BlockPos(2, 2, 2)
                );
                try {
                    ServerLevel level = helper.getLevel();
                    ConstructionBuildOp scanFirst = ConstructionJobController.nextAssignable(
                        level,
                        started.progress()
                    );
                    check(scanFirst != null, "平铺成环的蓝图必须有可派发目标");
                    // 扫描序把这一圈的队首排在 (6,2,6),悦灵停在对角的 (1,3,1) 上方,
                    // 与它斜对角相邻因此不占任何一格的接近位
                    WorkingAllayEntity worker = spawnConstructionAllay(
                        helper,
                        new Vec3(1.5D, 3.2D, 1.5D),
                        null,
                        0
                    );
                    ConstructionBuildOp nearest = ConstructionJobController.nextAssignable(
                        level,
                        started.progress(),
                        worker
                    );
                    check(nearest != null, "带上悦灵的派发必须仍能选出目标");
                    ConstructionBuildOp expected = closestOpenPlace(started.progress(), worker.position());
                    check(
                        nearest == expected,
                        "同层成环必须就近派发，期望 " + expected.pos() + " 实际 " + nearest.pos()
                    );
                    // 成环结构的剥离顺序在一层内只是逐行扫描,死板照搬会让悦灵放一块就飞到对面再飞回来
                    check(
                        centerDistanceSqr(worker, scanFirst) > centerDistanceSqr(worker, nearest) + 4.0D,
                        "本例的扫描序首目标必须明显远于就近目标，否则回归失去意义，扫描序首 "
                            + scanFirst.pos() + " 就近 " + nearest.pos()
                    );
                    check(
                        nearest.pos().getY() == scanFirst.pos().getY(),
                        "就近重排只允许发生在同一层内"
                    );
                } finally {
                    cancelQuietly(player, started.job().jobId());
                }
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("ring assignment setup failed: " + exception.reason());
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
        UUID[] jobSlot = new UUID[1];
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
                jobSlot[0] = started.job().jobId();
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
            cancelQuietly(player, jobSlot[0]);
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
        int[] maxCarry = new int[1];
        helper.onEachTick(() -> {
            WorkingAllayEntity worker = droneSlot[0];
            if (worker != null && worker.hostedCarry().is(Items.COBBLESTONE)) {
                maxCarry[0] = Math.max(maxCarry[0], worker.hostedCarry().getCount());
            }
        });
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
            check(maxCarry[0] == 2, "the allay never carried the two-block wall as one batch: " + maxCarry[0]);
            check(siteSlot[0] != null, "construction site bounds missing");
            check(
                drone != null && !siteSlot[0].intersects(drone.getBoundingBox()),
                "allay must leave the construction site before hovering"
                    + (drone == null ? "" : "; pos=" + drone.blockPosition()
                    + " flight=" + drone.flightState())
            );
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 100, batch = "zzz_construction")
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "A construction allay reserves at most one full item stack per pickup")
    static void constructionBatchStopsAtOneStack(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.moveTo(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
        WorkingAllayEntity[] workerSlot = new WorkingAllayEntity[1];
        StartedJob[] jobSlot = new StartedJob[1];
        int[] maxCarry = new int[1];
        helper.onEachTick(() -> {
            WorkingAllayEntity worker = workerSlot[0];
            if (worker != null && worker.hostedCarry().is(Items.COBBLESTONE)) {
                maxCarry[0] = Math.max(maxCarry[0], worker.hostedCarry().getCount());
            }
        });
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                player.setNoGravity(true);
                player.moveTo(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    denseCobbleStructure(65),
                    "stack-batch",
                    new BlockPos(2, 2, 2)
                );
                jobSlot[0] = started;
                player.getInventory().add(new ItemStack(Items.COBBLESTONE, 64));
                player.getInventory().add(new ItemStack(Items.COBBLESTONE));
                WorkingAllayEntity worker = spawnConstructionAllay(
                    helper,
                    new Vec3(1.5D, 2.0D, 1.5D),
                    player,
                    0
                );
                worker.setNoGravity(true);
                workerSlot[0] = worker;
                check(
                    ConstructionAllayToolBehavior.tryClaim(
                        worker,
                        helper.getLevel(),
                        started.job(),
                        started.progress()
                    ),
                    "stack-batch allay failed to claim its first operation"
                );
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("stack-batch setup failed: " + exception.reason());
            }
        }).thenWaitUntil(() -> {
            check(maxCarry[0] == 64, "one pickup must reach but not exceed a 64-item stack: " + maxCarry[0]);
            check(countCobble(player) == 1, "one stack pickup must leave the sixty-fifth cobblestone");
            check(carriedCount(jobSlot[0].progress()) == 64, "batch reservation must contain exactly 64 operations");
        }).thenExecute(() -> cancelQuietly(player, jobSlot[0].job().jobId())).thenSucceed();
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
    @EmptyTemplate(value = "9x5x9", floor = true)
    @TestHolder(description = "The lounge clearance setting decides whether a blueprint blank cell is demolished, and seal fill is cleared either way")
    static void loungeClearanceStrategyDecidesBlankCells(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            BlockPos clearAnchor = new BlockPos(4, 2, 1);
            BlockPos keepAnchor = new BlockPos(4, 2, 5);
            UUID[] running = {null, null};
            try {
                // 两格声明:锚点是蓝图空白格,东侧一格是毛石目标格,两格都先放世界石头
                helper.setBlock(clearAnchor, Blocks.STONE);
                helper.setBlock(clearAnchor.east(), Blocks.STONE);
                AllayLoungeBlockEntity clearLounge = placeLounge(helper, new BlockPos(1, 2, 1));
                clearLounge.setClearanceStrategy(AllayClearanceStrategy.CLEAR_AREA);
                StartedJob cleared = claimStructureAtLounge(
                    helper,
                    player,
                    blankCellStructure(false),
                    "blank-clear",
                    clearAnchor,
                    clearLounge
                );
                running[0] = cleared.job().jobId();
                BlockPos clearBlank = helper.absolutePos(clearAnchor);
                check(
                    hasKindAt(cleared.progress(), ConstructionBuildOp.Kind.DEMOLISH, clearBlank),
                    "CLEAR_AREA must demolish the world stone on the blueprint's blank cell"
                );
                check(
                    hasKindAt(cleared.progress(), ConstructionBuildOp.Kind.DEMOLISH, clearBlank.east()),
                    "CLEAR_AREA must demolish the world stone on the blueprint's cobble cell"
                );

                // 三格声明:末格空白放水,验证封堵填充块的拆除不受清场策略影响
                helper.setBlock(keepAnchor, Blocks.STONE);
                helper.setBlock(keepAnchor.east(), Blocks.STONE);
                helper.setBlock(keepAnchor.east().east(), Blocks.WATER);
                AllayLoungeBlockEntity keepLounge = placeLounge(helper, new BlockPos(1, 2, 5));
                keepLounge.setClearanceStrategy(AllayClearanceStrategy.KEEP_BLANK);
                StartedJob kept = claimStructureAtLounge(
                    helper,
                    player,
                    blankCellStructure(true),
                    "blank-keep",
                    keepAnchor,
                    keepLounge
                );
                running[1] = kept.job().jobId();
                BlockPos keepBlank = helper.absolutePos(keepAnchor);
                check(
                    !hasKindAt(kept.progress(), ConstructionBuildOp.Kind.DEMOLISH, keepBlank),
                    "KEEP_BLANK must leave the world stone on the blueprint's blank cell"
                );
                check(
                    hasKindAt(kept.progress(), ConstructionBuildOp.Kind.DEMOLISH, keepBlank.east()),
                    "KEEP_BLANK must still demolish the world stone on the blueprint's cobble cell"
                );
                BlockPos sealed = keepBlank.east().east();
                check(
                    hasKindAt(kept.progress(), ConstructionBuildOp.Kind.SEAL, sealed),
                    "the flooded blank cell must still be sealed under KEEP_BLANK"
                );
                check(
                    hasKindAt(kept.progress(), ConstructionBuildOp.Kind.DEMOLISH, sealed),
                    "seal fill on a blank cell must be demolished even under KEEP_BLANK"
                );
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("clearance strategy setup failed: " + exception.reason());
            } finally {
                for (UUID jobId : running) {
                    if (jobId != null) cancelQuietly(player, jobId);
                }
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_demolish")
    @EmptyTemplate(value = "7x5x7", floor = true)
    @TestHolder(description = "Changing the claimed lounge clearance strategy reconciles unfinished blank-cell demolition")
    static void loungeClearanceStrategyChangesPlannedJob(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            StartedJob started = null;
            try {
                BlockPos anchor = new BlockPos(4, 2, 3);
                helper.setBlock(anchor, Blocks.STONE);
                helper.setBlock(anchor.east(), Blocks.STONE);
                AllayLoungeBlockEntity lounge = placeLounge(helper, new BlockPos(1, 2, 1));
                lounge.setClearanceStrategy(AllayClearanceStrategy.KEEP_BLANK);
                started = claimStructureAtLounge(
                    helper,
                    player,
                    blankCellStructure(false),
                    "clearance-toggle",
                    anchor,
                    lounge
                );
                BlockPos blank = helper.absolutePos(anchor);
                ConstructionBuildOp blankDemolish = operationAt(
                    started.progress(),
                    ConstructionBuildOp.Kind.DEMOLISH,
                    blank
                );
                check(started.progress().clearanceStrategy() == AllayClearanceStrategy.KEEP_BLANK,
                    "the planned job did not record KEEP_BLANK");
                check(blankDemolish == null,
                    "KEEP_BLANK must not plan demolition on its explicit blank cell");

                lounge.setClearanceStrategy(AllayClearanceStrategy.CLEAR_AREA);
                check(started.progress().clearanceStrategy() == AllayClearanceStrategy.CLEAR_AREA,
                    "CLEAR_AREA did not synchronize into the planned job");
                blankDemolish = operationAt(started.progress(), ConstructionBuildOp.Kind.DEMOLISH, blank);
                check(blankDemolish != null && blankDemolish.status() == ConstructionBuildOp.Status.PENDING,
                    "CLEAR_AREA must add a pending demolition for an unfinished blank cell");

                lounge.setClearanceStrategy(AllayClearanceStrategy.KEEP_BLANK);
                check(blankDemolish.status() == ConstructionBuildOp.Status.SKIPPED,
                    "KEEP_BLANK must skip the unfinished blank-cell demolition");

                lounge.setClearanceStrategy(AllayClearanceStrategy.CLEAR_AREA);
                check(blankDemolish.status() == ConstructionBuildOp.Status.PENDING,
                    "CLEAR_AREA must reactivate a skipped blank-cell demolition while the block remains");
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("clearance toggle setup failed: " + exception.reason());
            } finally {
                if (started != null) cancelQuietly(player, started.job().jobId());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_demolish")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A real block placed during building is inserted as reactive demolition and keeps marked debris")
    static void runtimePlacedBlockIsDemolishedAndMarked(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            StartedJob started = null;
            try {
                player.moveTo(helper.absoluteVec(new Vec3(2.5D, 2.0D, 1.5D)));
                started = startCobbleJob(helper, player, 1);
                BlockPos target = helper.absolutePos(new BlockPos(3, 2, 1));
                helper.setBlock(new BlockPos(3, 2, 1), Blocks.STONE);
                ConstructionJobIndex index = ConstructionJobIndex.get(helper.getLevel());
                ConstructionJob live = index.job(started.job().jobId());
                check(live != null && live.state() == ConstructionJob.STATE_DEMOLISHING,
                    "a new mismatching block must pause building for demolition");
                ConstructionBuildOp reactive = firstReactiveAt(started.progress(), target);
                check(reactive.status() == ConstructionBuildOp.Status.PENDING,
                    "reactive demolition must start pending");
                check(ConstructionJobController.tryDemolish(helper.getLevel(), started.progress(), reactive),
                    "the reactive demolition must use the normal smash path");
                check(helper.getLevel().getBlockState(target).isAir(), "reactive demolition must clear the real block");
                check(started.progress().debrisSpawned(reactive.id()) > 0,
                    "reactive demolition must account for spawned debris");
                boolean marked = false;
                for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(1.5D))) {
                    if (item.getItem().is(Items.COBBLESTONE) && ConstructionDebris.isMarked(item.getItem())) {
                        marked = true;
                        break;
                    }
                }
                check(marked, "reactive demolition drops must carry the construction marker");
                ConstructionJobController.tickJob(helper.getLevel().getServer(), helper.getLevel(), index.job(started.job().jobId()));
                check(index.job(started.job().jobId()).state() == ConstructionJob.STATE_BUILDING,
                    "the job must return to building after reactive demolition");
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("runtime demolition setup failed: " + exception.reason());
            } finally {
                if (started != null) cancelQuietly(player, started.job().jobId());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_demolish")
    @EmptyTemplate(value = "9x5x9", floor = true)
    @TestHolder(description = "KEEP_BLANK preserves a new blank-cell block but still reacts on a blueprint block cell")
    static void runtimePlacedBlockHonorsKeepBlank(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            StartedJob started = null;
            try {
                AllayLoungeBlockEntity lounge = placeLounge(helper, new BlockPos(1, 2, 1));
                lounge.setClearanceStrategy(AllayClearanceStrategy.KEEP_BLANK);
                started = claimStructureAtLounge(
                    helper,
                    player,
                    blankCellStructure(false),
                    "runtime-keep-blank",
                    new BlockPos(4, 2, 5),
                    lounge
                );
                BlockPos blank = helper.absolutePos(new BlockPos(4, 2, 5));
                BlockPos target = blank.east();
                helper.getLevel().setBlockAndUpdate(blank, Blocks.STONE.defaultBlockState());
                check(helper.getLevel().getBlockState(blank).is(Blocks.STONE),
                    "KEEP_BLANK must preserve a new block on an explicit blank cell");
                check(!hasReactiveAt(started.progress(), blank),
                    "KEEP_BLANK must not add demolition for a new blank-cell block");
                check(ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId()).state()
                        == ConstructionJob.STATE_BUILDING,
                    "preserving a blank-cell block must not pause the job");

                helper.getLevel().setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
                check(ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId()).state()
                        == ConstructionJob.STATE_DEMOLISHING,
                    "a new block on a blueprint target must enter demolition under KEEP_BLANK");
                ConstructionBuildOp reactive = firstReactiveAt(started.progress(), target);
                check(ConstructionJobController.tryDemolish(helper.getLevel(), started.progress(), reactive),
                    "the KEEP_BLANK target conflict must be demolishable");
                check(helper.getBlockState(target).isAir(), "the mismatching target block must be removed");
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("runtime KEEP_BLANK setup failed: " + exception.reason());
            } finally {
                if (started != null) cancelQuietly(player, started.job().jobId());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_demolish")
    @EmptyTemplate(value = "6x4x5", floor = true)
    @TestHolder(description = "Replacing a delivered projection returns its material before reassigning the PLACE op")
    static void runtimeConflictReturnsDeliveredMaterial(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            StartedJob started = null;
            try {
                player.moveTo(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
                started = startCobbleJob(helper, player, 2);
                player.getInventory().add(new ItemStack(Items.COBBLESTONE, 1));
                ConstructionBuildOp place = firstPlace(started.progress());
                int beforeExtract = countCobble(player);
                check(ConstructionJobController.extractMaterial(player, started.progress(), place, player.getUUID()),
                    "the delivered projection fixture must extract its material");
                check(ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), place),
                    "the delivered projection fixture must publish");
                check(ConstructionProjectionIndex.has(helper.getLevel(), started.job().jobId(), place.pos()),
                    "the fixture must have a delivered projection");

                helper.getLevel().setBlockAndUpdate(place.pos(), Blocks.STONE.defaultBlockState());
                check(ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId()).state()
                        == ConstructionJob.STATE_DEMOLISHING,
                    "replacing a projection with a real block must enter demolition");
                check(place.status() == ConstructionBuildOp.Status.PENDING,
                    "the replaced PLACE operation must be reassigned");
                check(!ConstructionProjectionIndex.has(helper.getLevel(), started.job().jobId(), place.pos()),
                    "the replaced projection must be removed");
                check(countCobble(player) == beforeExtract,
                    "material from the replaced projection must be returned exactly once");
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("runtime projection setup failed: " + exception.reason());
            } finally {
                if (started != null) cancelQuietly(player, started.job().jobId());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_demolish")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "An exact external target resolves a material wait and an externally removed target is buildable again")
    static void runtimeExactTargetReconcilesWait(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            StartedJob started = null;
            try {
                started = startCobbleJob(helper, player, 1);
                ConstructionBuildOp place = firstPlace(started.progress());
                ConstructionJobController.applyShortage(
                    helper.getLevel().getServer(),
                    started.job(),
                    started.progress(),
                    AllayShortageStrategy.PAUSE,
                    place
                );
                check(
                    ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId()).state()
                        == ConstructionJob.STATE_WAITING_MATERIAL,
                    "the fixture must enter WAITING_MATERIAL before the external target arrives"
                );
                helper.getLevel().setBlockAndUpdate(place.pos(), place.target());
                ConstructionJob live = ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId());
                check(live != null && live.state() != ConstructionJob.STATE_WAITING_MATERIAL,
                    "an exact external target must clear the stale material wait");
                check(place.worldSatisfied() && place.status() == ConstructionBuildOp.Status.DELIVERED,
                    "the exact external target must satisfy the PLACE operation");

                helper.getLevel().setBlockAndUpdate(place.pos(), Blocks.AIR.defaultBlockState());
                check(place.status() == ConstructionBuildOp.Status.PENDING && !place.worldSatisfied(),
                    "removing an externally satisfied target must reopen the PLACE operation");
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("runtime exact target setup failed: " + exception.reason());
            } finally {
                if (started != null) cancelQuietly(player, started.job().jobId());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_demolish")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "KEEP_BLANK still demolishes a block placed on an explicit entity target cell")
    static void runtimeEntityTargetHonorsKeepBlank(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            StartedJob started = null;
            try {
                AllayLoungeBlockEntity lounge = placeLounge(helper, new BlockPos(1, 2, 1));
                lounge.setClearanceStrategy(AllayClearanceStrategy.KEEP_BLANK);
                CompoundTag boat = new CompoundTag();
                boat.putString("id", "minecraft:boat");
                started = claimStructureAtLounge(
                    helper,
                    player,
                    entityOnlyStructure(List.of(boat)),
                    "runtime-entity-keep-blank",
                    new BlockPos(4, 2, 3),
                    lounge
                );
                ConstructionBuildOp entity = firstKind(started.progress(), ConstructionBuildOp.Kind.ENTITY);
                helper.getLevel().setBlockAndUpdate(entity.pos(), Blocks.STONE.defaultBlockState());
                check(
                    hasReactiveAt(started.progress(), entity.pos()),
                    "KEEP_BLANK must add demolition when an entity target cell is blocked"
                );
                check(
                    ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId()).state()
                        == ConstructionJob.STATE_DEMOLISHING,
                    "an entity target conflict must enter demolition under KEEP_BLANK"
                );
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("runtime entity KEEP_BLANK setup failed: " + exception.reason());
            } finally {
                if (started != null) cancelQuietly(player, started.job().jobId());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_demolish")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A real block replacing a delivered entity projection reopens the entity operation")
    static void runtimeEntityProjectionReopensOnConflict(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            StartedJob started = null;
            try {
                CompoundTag boat = new CompoundTag();
                boat.putString("id", "minecraft:boat");
                started = startStructureJob(
                    helper,
                    player,
                    entityOnlyStructure(List.of(boat)),
                    "runtime-entity-projection",
                    new BlockPos(2, 2, 2)
                );
                ConstructionBuildOp entity = firstKind(started.progress(), ConstructionBuildOp.Kind.ENTITY);
                player.getInventory().add(new ItemStack(Items.OAK_BOAT));
                int before = countItem(player, Items.OAK_BOAT);
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), entity, player.getUUID()),
                    "the entity projection fixture must extract its material"
                );
                check(ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), entity),
                    "the entity projection fixture must publish");
                check(
                    ConstructionEntityProjectionIndex.delivered(helper.getLevel(), started.job().jobId()).stream()
                        .anyMatch(entry -> entry.opId() == entity.id()),
                    "the entity projection must be present before the conflict"
                );

                helper.getLevel().setBlockAndUpdate(entity.pos(), Blocks.STONE.defaultBlockState());
                check(entity.status() == ConstructionBuildOp.Status.PENDING,
                    "a real block must reopen the replaced entity operation");
                check(
                    ConstructionEntityProjectionIndex.delivered(helper.getLevel(), started.job().jobId()).stream()
                        .noneMatch(entry -> entry.opId() == entity.id()),
                    "the replaced entity projection must be removed"
                );
                check(countItem(player, Items.OAK_BOAT) == before,
                    "the replaced entity material must be returned exactly once");
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("runtime entity projection setup failed: " + exception.reason());
            } finally {
                if (started != null) cancelQuietly(player, started.job().jobId());
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
    @EmptyTemplate(value = "5x6x5", floor = true)
    @TestHolder(description = "Fluid sealing assigns one Y layer at a time and enters a lower cell from the open layer above")
    static void fluidSealAssignsBottomUp(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ConstructionJobProgress progress = new ConstructionJobProgress(UUID.randomUUID());
        BlockPos lowerPos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos upperPos = lowerPos.above();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos surrounding = lowerPos.offset(dx, dy, dz);
                    level.setBlockAndUpdate(
                        surrounding,
                        surrounding.equals(upperPos)
                            ? Blocks.WATER.defaultBlockState()
                            : Blocks.STONE.defaultBlockState()
                    );
                }
            }
        }
        level.setBlockAndUpdate(lowerPos, Blocks.WATER.defaultBlockState());
        ConstructionBuildOp upper = progress.addOperation(
            upperPos,
            Blocks.AIR.defaultBlockState(),
            new ItemStack(Items.DIRT),
            ConstructionBuildOp.Kind.SEAL,
            ConstructionBuildOp.Status.PENDING
        );
        ConstructionBuildOp lower = progress.addOperation(
            lowerPos,
            Blocks.AIR.defaultBlockState(),
            new ItemStack(Items.DIRT),
            ConstructionBuildOp.Kind.SEAL,
            ConstructionBuildOp.Status.PENDING
        );

        ConstructionBuildOp first = ConstructionJobController.nextAssignableSeal(level, progress);
        check(first == lower, "the lowest seal must be assigned even when its id is later");
        check(lower.approach().filter(upperPos::equals).isPresent(),
            "the lower seal must use the still-open upper seal cell as its approach");
        lower.setStatus(ConstructionBuildOp.Status.LEASED);
        check(
            ConstructionJobController.nextAssignableSeal(level, progress) == null,
            "an upper layer must wait while the lower layer is leased"
        );
        level.setBlockAndUpdate(lowerPos, Blocks.DIRT.defaultBlockState());
        lower.setStatus(ConstructionBuildOp.Status.DELIVERED);
        ConstructionBuildOp second = ConstructionJobController.nextAssignableSeal(level, progress);
        check(second == upper, "the upper seal must become assignable after the lower layer is delivered");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_lounge")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "A lounge builder docks as soon as the final fluid-seal operation is resolved")
    static void completedFluidSealReturnsBuilderToLounge(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                helper.setBlock(new BlockPos(4, 2, 4), Blocks.WATER);
                player.getInventory().add(new ItemStack(Items.DIRT, 16));
                player.getInventory().add(new ItemStack(Items.COBBLESTONE, 2));
                AllayLoungeBlockEntity lounge = placeLoungeWithChest(
                    helper,
                    new BlockPos(2, 2, 2),
                    new ItemStack(Items.DIRT, 16)
                );
                if (!(helper.getBlockEntity(new BlockPos(2, 1, 2)) instanceof Container container)) {
                    throw new GameTestAssertException("fluid-seal source chest is missing");
                }
                container.setItem(1, new ItemStack(Items.COBBLESTONE, 2));
                StartedJob started = claimCobbleAtLounge(helper, player, 1, lounge);
                check(
                    started.job().state() == ConstructionJob.STATE_SEALING_FLUID,
                    "flooded claimed job must be sealing before the docking check"
                );
                int seals = 0;
                for (ConstructionBuildOp op : started.progress().operations()) {
                    if (op.kind() != ConstructionBuildOp.Kind.SEAL) continue;
                    op.setStatus(ConstructionBuildOp.Status.DELIVERED);
                    op.setLeaseAllay(null);
                    seals++;
                }
                check(seals > 0 && started.progress().allSealResolved(), "setup did not resolve every seal operation");
                WorkingAllayEntity worker = spawnConstructionAllay(
                    helper,
                    new Vec3(4.5D, 3.0D, 3.5D),
                    player,
                    0
                );
                worker.setHomeLounge(lounge.getBlockPos());
                ConstructionJobIndex.get(helper.getLevel()).put(
                    started.job().withState(ConstructionJob.STATE_DEMOLISHING)
                );
                worker.toolDefinition().behavior().serverTick(worker);
                check(worker.flightState() == AllayFlightState.DOCKING, "completed sealing left the builder hovering");
                check(worker.isDockingTo(lounge.getBlockPos()), "completed sealing did not target the home lounge");
                cancelQuietly(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("fluid-seal docking setup failed: " + exception.reason());
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

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_demolish")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "Demolishing a multipart checks world permission for every affected part before changing the world")
    static void multipartDemolitionChecksEveryAffectedPart(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        UUID[] jobId = {null};
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                ServerLevel level = helper.getLevel();
                GiantAnvilBlock block = ModBlocks.GIANT_ANVIL.get();
                BlockPos bottom = helper.absolutePos(new BlockPos(3, 2, 3));
                BlockState bottomState = block.defaultBlockState()
                    .setValue(GiantAnvilBlock.HALF, Cube3x3PartHalf.BOTTOM_CENTER);
                level.setBlockAndUpdate(bottom, bottomState);
                block.setPlacedBy(level, bottom, bottomState, null, ItemStack.EMPTY);

                StartedJob started = startStructureJob(
                    helper,
                    player,
                    cobbleStructure(1),
                    "multipart-permission",
                    new BlockPos(3, 3, 3)
                );
                jobId[0] = started.job().jobId();
                ConstructionBuildOp demolish = firstKind(
                    started.progress(),
                    ConstructionBuildOp.Kind.DEMOLISH
                );
                BlockPos deniedPart = bottom.offset(1, 0, 1);
                check(level.getBlockState(deniedPart).is(block), "denied multipart fixture is missing its corner");
                ConstructionPermission.setWorldPermissionProvider(
                    (permissionLevel, pos, owner) -> !pos.equals(deniedPart)
                );

                check(
                    !ConstructionJobController.tryDemolish(level, started.progress(), demolish),
                    "multipart demolition changed the world despite a denied corner"
                );
                ConstructionJob waiting = ConstructionJobIndex.get(level).job(jobId[0]);
                check(
                    waiting != null && waiting.state() == ConstructionJob.STATE_WAITING_PERMISSION,
                    "denied multipart demolition did not enter WAITING_PERMISSION"
                );
                for (Cube3x3PartHalf part : block.getParts()) {
                    check(
                        level.getBlockState(bottom.offset(part.getOffset())).is(block),
                        "permission denial removed multipart part " + part
                    );
                }
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("multipart permission setup failed: " + exception.reason());
            } finally {
                ConstructionPermission.setWorldPermissionProvider(null);
                if (jobId[0] != null) cancelQuietly(player, jobId[0]);
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_demolish")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "Demolition may approach an inner target through a cleared cell reserved for later construction")
    static void demolitionApproachesThroughFutureBuildCell(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ConstructionJobProgress progress = new ConstructionJobProgress(UUID.randomUUID());
        BlockPos target = helper.absolutePos(new BlockPos(3, 3, 3));
        BlockPos passage = target.west();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos surrounding = target.offset(dx, dy, dz);
                    level.setBlockAndUpdate(
                        surrounding,
                        surrounding.equals(passage)
                            ? Blocks.AIR.defaultBlockState()
                            : Blocks.STONE.defaultBlockState()
                    );
                }
            }
        }
        level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
        progress.addOperation(
            passage,
            Blocks.COBBLESTONE.defaultBlockState(),
            new ItemStack(Items.COBBLESTONE),
            ConstructionBuildOp.Kind.PLACE,
            ConstructionBuildOp.Status.PENDING
        );
        progress.addOperation(
            target,
            Blocks.COBBLESTONE.defaultBlockState(),
            new ItemStack(Items.COBBLESTONE),
            ConstructionBuildOp.Kind.PLACE,
            ConstructionBuildOp.Status.PENDING
        );
        ConstructionBuildOp demolition = progress.addOperation(
            target,
            Blocks.STONE.defaultBlockState(),
            ItemStack.EMPTY,
            ConstructionBuildOp.Kind.DEMOLISH,
            ConstructionBuildOp.Status.PENDING
        );

        ConstructionBuildOp assignable = ConstructionJobController.nextAssignableDemolish(level, progress);
        check(assignable == demolition, "the inner demolition operation must remain assignable");
        check(demolition.approach().filter(passage::equals).isPresent(),
            "demolition must enter the cleared future build cell");
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

    @GameTest(timeoutTicks = 400, batch = "zzz_construction_demolish_live")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "An engineering hard hat gives stonecutter demolition Silk Touch drops")
    static void engineeringHardHatGrantsDemolitionSilkTouch(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.moveTo(helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D)));
        UUID[] jobSlot = new UUID[1];
        BlockPos target = new BlockPos(3, 2, 3);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                helper.setBlock(target, Blocks.GLASS);
                WorkingAllayEntity worker = spawnDemolitionAllay(
                    helper,
                    new Vec3(2.5D, 2.0D, 3.5D),
                    player,
                    0
                );
                worker.setNoGravity(true);
                worker.setHardHat(engineeringHardHat());
                check(AllayHardHatTraits.hasSilkTouch(worker.getHardHat()),
                    "engineering hard hat did not register Silk Touch");
                StartedJob started = startCobbleJob(helper, player, 1, target);
                jobSlot[0] = started.job().jobId();
                if (!DemolitionAllayToolBehavior.tryClaim(
                    worker,
                    helper.getLevel(),
                    started.job(),
                    started.progress()
                )) {
                    cancelQuietly(player, started.job().jobId());
                    jobSlot[0] = null;
                    throw new GameTestAssertException(
                        "engineering demolition allay failed to claim: wait=" + worker.waitReason()
                    );
                }
                player.moveTo(helper.absoluteVec(new Vec3(0.5D, 4.0D, 0.5D)));
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("engineering demolish setup failed: " + exception.reason());
            }
        }).thenWaitUntil(() -> check(
            helper.getBlockState(target).isAir(),
            "engineering demolition allay did not break the glass target"
        )).thenExecute(() -> {
            BlockPos absoluteTarget = helper.absolutePos(target);
            int silkDrops = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                new AABB(absoluteTarget).inflate(1.5D),
                item -> item.getItem().is(Items.GLASS) && ConstructionDebris.isMarked(item.getItem())
            ).stream().mapToInt(item -> item.getItem().getCount()).sum();
            check(silkDrops == 1,
                "engineering hard hat produced " + silkDrops + " marked glass drops instead of one");
            if (jobSlot[0] != null) cancelQuietly(player, jobSlot[0]);
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 800, batch = "zzz_construction_demolish_live")
    @EmptyTemplate(value = "8x7x8", floor = true)
    @TestHolder(description = "A demolition allay enters the cleared volume and removes the center of a solid 3 by 3 by 3 cube")
    static void demolitionAllayClearsSolidCubeCenter(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.moveTo(helper.absoluteVec(new Vec3(1.5D, 3.0D, 3.5D)));
        UUID[] jobSlot = new UUID[1];
        BlockPos anchor = new BlockPos(2, 2, 2);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                for (int x = 0; x < 3; x++) {
                    for (int y = 0; y < 3; y++) {
                        for (int z = 0; z < 3; z++) {
                            helper.setBlock(anchor.offset(x, y, z), Blocks.STONE);
                        }
                    }
                }
                WorkingAllayEntity worker = spawnDemolitionAllay(
                    helper,
                    new Vec3(1.5D, 3.0D, 3.5D),
                    player,
                    0
                );
                worker.setNoGravity(true);
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    solidCubeStructure(),
                    "solid-demolition-cube",
                    anchor
                );
                jobSlot[0] = started.job().jobId();
                check(
                    DemolitionAllayToolBehavior.tryClaim(
                        worker,
                        helper.getLevel(),
                        started.job(),
                        started.progress()
                    ),
                    "the solid cube demolition must receive its first operation"
                );
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("solid cube demolition setup failed: " + exception.reason());
            }
        }).thenWaitUntil(() -> {
            for (int x = 0; x < 3; x++) {
                for (int y = 0; y < 3; y++) {
                    for (int z = 0; z < 3; z++) {
                        check(
                            helper.getBlockState(anchor.offset(x, y, z)).isAir(),
                            "solid cube demolition left a block at " + anchor.offset(x, y, z)
                        );
                    }
                }
            }
        }).thenExecute(() -> {
            if (jobSlot[0] != null) {
                cancelQuietly(player, jobSlot[0]);
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 60, batch = "zzz_construction_demolish_live")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "A demolition allay immediately releases an operation whose approach becomes unusable")
    static void demolitionReleasesInvalidApproachImmediately(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                BlockPos target = new BlockPos(3, 3, 3);
                helper.setBlock(target, Blocks.STONE);
                WorkingAllayEntity worker = spawnDemolitionAllay(
                    helper,
                    new Vec3(2.5D, 3.0D, 3.5D),
                    player,
                    0
                );
                StartedJob started = startCobbleJob(helper, player, 1, target);
                check(
                    DemolitionAllayToolBehavior.tryClaim(
                        worker,
                        helper.getLevel(),
                        started.job(),
                        started.progress()
                    ),
                    "demolition allay failed to claim the initially reachable operation"
                );
                ConstructionBuildOp demolition = started.progress().operation(worker.taskOpId());
                check(demolition != null, "claimed demolition operation is missing");
                worker.moveTo(helper.absoluteVec(new Vec3(1.5D, 4.5D, 1.5D)));
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            helper.setBlock(target.offset(dx, dy, dz), Blocks.STONE);
                        }
                    }
                }
                DemolitionAllayToolBehavior.INSTANCE.serverTick(worker);
                check(worker.assignedJobId().isEmpty(), "invalid approach kept the demolition assignment");
                check(demolition.status() == ConstructionBuildOp.Status.PENDING, "invalid approach kept the lease status");
                check(demolition.leaseAllay().isEmpty(), "invalid approach kept the allay lease owner");
                check(demolition.approach().isEmpty(), "invalid approach was not cleared for reassignment");
                check(!worker.navigator().hasPath(), "invalid approach started a fallback flight above the target");
                check(helper.getBlockState(target).is(Blocks.STONE), "approach recovery demolished the target unexpectedly");
                cancelQuietly(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("demolition reassignment setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 60, batch = "zzz_construction")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "A builder immediately releases an operation whose safe approach disappears")
    static void constructionReleasesInvalidApproachImmediately(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.moveTo(helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D)));
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                player.setNoGravity(true);
                player.moveTo(helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D)));
                player.getInventory().add(new ItemStack(Items.COBBLESTONE, 2));
                BlockPos target = new BlockPos(3, 2, 3);
                WorkingAllayEntity worker = spawnConstructionAllay(
                    helper,
                    new Vec3(2.5D, 2.0D, 3.5D),
                    player,
                    0
                );
                StartedJob started = startCobbleJob(helper, player, 1, target);
                check(
                    ConstructionAllayToolBehavior.tryClaim(
                        worker,
                        helper.getLevel(),
                        started.job(),
                        started.progress()
                    ),
                    "builder failed to claim the initially reachable operation"
                );
                ConstructionBuildOp operation = started.progress().operation(worker.taskOpId());
                check(operation != null, "claimed construction operation is missing");
                worker.setHostedCarry(new ItemStack(Items.COBBLESTONE));
                worker.moveTo(helper.absoluteVec(new Vec3(1.5D, 4.5D, 1.5D)));
                player.moveTo(helper.absoluteVec(new Vec3(1.5D, 4.5D, 1.5D)));
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            helper.setBlock(target.offset(dx, dy, dz), Blocks.STONE);
                        }
                    }
                }

                ConstructionAllayToolBehavior.INSTANCE.serverTick(worker);

                check(worker.assignedJobId().isEmpty(), "invalid approach kept the construction assignment");
                check(operation.status() == ConstructionBuildOp.Status.PENDING, "invalid approach kept the lease status");
                check(operation.leaseAllay().isEmpty(), "invalid approach kept the allay lease owner");
                check(operation.approach().isEmpty(), "invalid approach was not cleared for reassignment");
                check(!worker.navigator().hasPath(), "invalid approach started a fallback flight above the target");
                check(helper.getBlockState(target).isAir(), "approach recovery delivered the target unexpectedly");
                check(worker.hostedCarry().is(Items.COBBLESTONE), "approach recovery discarded the carried material");
                cancelQuietly(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("construction reassignment setup failed: " + exception.reason());
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

    @GameTest(timeoutTicks = 80, batch = "zzz_collection_live_task")
    @EmptyTemplate(value = "24x6x5", floor = true)
    @TestHolder(description = "A magnet collector flies toward a marked task drop outside its suction range")
    static void taskCollectorFliesToRemoteMarkedDrop(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            ServerLevel level = helper.getLevel();
            UUID jobId = UUID.randomUUID();
            BlockPos loungePos = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockPos targetPos = helper.absolutePos(new BlockPos(20, 2, 2));
            placeLounge(helper, new BlockPos(2, 2, 2)).setOwner(player.getUUID());
            WorkingAllayEntity worker = CollectionAllayGameTests.spawnCollectionAllay(
                helper,
                new Vec3(2.5D, 3.0D, 2.5D),
                player
            );
            worker.setHomeLounge(loungePos);

            ConstructionJob job = new ConstructionJob(
                jobId,
                player.getUUID(),
                ConstructionJob.STATE_COLLECTING_DEBRIS,
                "remote-collector",
                level.dimension(),
                targetPos,
                Rotation.NONE,
                Mirror.NONE,
                "remote-collector",
                new Vec3i(1, 1, 1),
                BlueprintSource.VANILLA_FILE,
                false,
                false
            );
            ConstructionJobProgress progress = ConstructionJobStore.get(level).getOrCreate(jobId);
            progress.setPlanned(true);
            progress.setCoordinatorLounge(loungePos);
            ConstructionBuildOp operation = progress.addOperation(
                targetPos,
                Blocks.STONE.defaultBlockState(),
                ItemStack.EMPTY,
                ConstructionBuildOp.Kind.DEMOLISH,
                ConstructionBuildOp.Status.DELIVERED
            );
            ItemStack marked = new ItemStack(Items.COBBLESTONE);
            ConstructionDebris.mark(marked, jobId, operation.id());
            ItemEntity drop = new ItemEntity(
                level,
                targetPos.getX() + 0.5D,
                targetPos.getY() + 0.2D,
                targetPos.getZ() + 0.5D,
                marked
            );
            drop.setDeltaMovement(Vec3.ZERO);
            drop.setPickUpDelay(0);
            check(level.addFreshEntity(drop), "failed to spawn the remote marked drop");
            ConstructionJobIndex.get(level).put(job);

            try {
                check(
                    CollectionAllayToolBehavior.tryClaim(worker, level, job, progress),
                    "the magnet collector failed to claim the remote marked drop"
                );
                CollectionAllayToolBehavior.INSTANCE.serverTick(worker);
                check(
                    worker.hasFlightTaskFor(drop.position()),
                    "a remote marked drop must start a pickup flight"
                );
                check(
                    worker.flightState() == AllayFlightState.FLYING,
                    "the magnet collector must fly instead of holding at the lounge entrance"
                );
                check(drop.isAlive(), "the remote drop must remain until the collector reaches suction range");
            } finally {
                ConstructionTraffic.release(level, worker.getUUID());
                ConstructionJobIndex.get(level).remove(jobId);
                ConstructionJobStore.get(level).remove(jobId);
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
    @TestHolder(description = "A double chest folds to one PLACE plus ATTACHED, costs two chests, and commit writes both halves")
    static void doubleChestFoldsAndCommitsBothHalves(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    doubleChestStructure(helper.getLevel().registryAccess()),
                    "double-chest",
                    new BlockPos(1, 2, 2)
                );
                check(countKind(started.progress(), ConstructionBuildOp.Kind.PLACE) == 1,
                    "a paired double chest must fold to one PLACE");
                check(countKind(started.progress(), ConstructionBuildOp.Kind.ATTACHED) == 1,
                    "the other chest half must be ATTACHED");
                ConstructionBuildOp core = firstPlace(started.progress());
                ConstructionBuildOp attached = firstKind(started.progress(), ConstructionBuildOp.Kind.ATTACHED);
                check(core.material().getCount() == 2 && core.material().is(Items.CHEST),
                    "the core must deduct two chests at once");
                check(attached.material().isEmpty(), "the attached half must not deduct a third chest");
                check(attached.parentId() == core.id(), "the left half must hang on the right core");
                player.getInventory().add(new ItemStack(Items.CHEST, 2));
                player.getInventory().add(new ItemStack(Items.DIAMOND, 3));
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), core, UUID.randomUUID()),
                    "extracting two chests must succeed"
                );
                check(countItem(player, Items.CHEST) == 0, "both chest items must be taken in one extract");
                check(
                    ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), core),
                    "delivering the core must place both halves"
                );
                check(core.status() == ConstructionBuildOp.Status.DELIVERED, "the core must be delivered");
                check(
                    attached.status() == ConstructionBuildOp.Status.DELIVERED,
                    "core delivery must deliver the other half too"
                );
                ConstructionBuildOp content = firstKind(started.progress(), ConstructionBuildOp.Kind.CONTENT);
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), content, UUID.randomUUID()),
                    "extracting double-chest contents must succeed"
                );
                check(
                    ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), content),
                    "delivering double-chest contents must succeed"
                );
                ConstructionJobController.finish(
                    helper.getLevel().getServer(),
                    helper.getLevel(),
                    started.job(),
                    started.progress()
                );
                BlockState right = helper.getLevel().getBlockState(core.pos());
                BlockState left = helper.getLevel().getBlockState(attached.pos());
                check(right.is(Blocks.CHEST) && right.getValue(ChestBlock.TYPE) == ChestType.RIGHT,
                    "commit must write the right half");
                check(left.is(Blocks.CHEST) && left.getValue(ChestBlock.TYPE) == ChestType.LEFT,
                    "commit must write the left half");
                BlockEntity blockEntity = helper.getLevel().getBlockEntity(core.pos());
                check(blockEntity instanceof Container, "committed double chest must have a container");
                check(
                    countContainer((Container) blockEntity, Items.DIAMOND) == 3,
                    "committed double chest must keep the deducted diamonds"
                );
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("double chest setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "An empty-handed allay can deliver a double chest while only its attached half is in reach")
    static void emptyHandDeliversDoubleChestFromAttachedSide(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.moveTo(helper.absoluteVec(new Vec3(4.5D, 3.0D, 4.5D)));
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    doubleChestStructure(helper.getLevel().registryAccess()),
                    "double-chest-empty-hand",
                    new BlockPos(1, 2, 2)
                );
                try {
                    ConstructionBuildOp core = firstPlace(started.progress());
                    ConstructionBuildOp attached = firstKind(started.progress(), ConstructionBuildOp.Kind.ATTACHED);
                    BlockPos outward = attached.pos().subtract(core.pos());
                    check(outward.distManhattan(BlockPos.ZERO) == 1, "double-chest halves must be adjacent");
                    BlockPos approach = attached.pos().offset(outward);
                    WorkingAllayEntity worker = AllayGameTests.spawnHatted(
                        helper,
                        new Vec3(4.5D, 2.0D, 4.5D),
                        player
                    );
                    Vec3 position = Vec3.atBottomCenterOf(approach);
                    worker.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
                    check(worker.toolDefinition() == AllayToolDefinitions.NONE, "the worker must stay empty-handed");
                    double reach = ConstructionJobController.reach(worker);
                    AABB workerBox = worker.getBoundingBox();
                    check(
                        !workerBox.intersects(new AABB(core.pos()).inflate(reach)),
                        "the empty hand must not reach the core chest half"
                    );
                    check(
                        workerBox.intersects(new AABB(attached.pos()).inflate(reach)),
                        "the empty hand must reach the attached chest half"
                    );
                    core.setApproach(approach);
                    core.setStatus(ConstructionBuildOp.Status.LEASED);
                    core.setLeaseAllay(worker.getUUID());
                    worker.assign(started.job().jobId(), core.id());
                    player.getInventory().add(new ItemStack(Items.CHEST, 2));
                    check(
                        ConstructionJobController.extractMaterial(player, started.progress(), core, worker.getUUID()),
                        "extracting both chest items for the empty hand must succeed"
                    );
                    worker.setHostedCarry(core.material().copy());
                    worker.tickCount = WorkingAllayEntity.ACTION_INTERVAL_TICKS;
                    for (int attempt = 0;
                         attempt < 12 && core.status() != ConstructionBuildOp.Status.DELIVERED;
                         attempt++) {
                        ConstructionAllayToolBehavior.INSTANCE.serverTick(worker);
                    }
                    check(core.status() == ConstructionBuildOp.Status.DELIVERED,
                        "the empty hand must deliver through the reachable attached half");
                    check(attached.status() == ConstructionBuildOp.Status.DELIVERED,
                        "the core transaction must still deliver both chest halves");
                } finally {
                    cancelQuietly(player, started.job().jobId());
                }
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("empty-hand double chest setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A leftover LEFT chest half commits as SINGLE instead of a half-width double chest")
    static void loneChestHalfCommitsAsSingle(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    loneLeftChestStructure(),
                    "lone-left-chest",
                    new BlockPos(2, 2, 2)
                );
                check(countKind(started.progress(), ConstructionBuildOp.Kind.PLACE) == 1,
                    "an unpaired LEFT chest must stay a single PLACE");
                check(countKind(started.progress(), ConstructionBuildOp.Kind.ATTACHED) == 0,
                    "an unpaired LEFT chest must not wait for a missing partner");
                ConstructionBuildOp place = firstPlace(started.progress());
                check(place.material().getCount() == 1 && place.material().is(Items.CHEST),
                    "a leftover half must cost one chest");
                player.getInventory().add(new ItemStack(Items.CHEST));
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), place, UUID.randomUUID()),
                    "extracting the leftover chest must succeed"
                );
                check(
                    ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), place),
                    "delivering the leftover chest must succeed"
                );
                ConstructionBlueprintService.cancel(player, started.job().jobId());
                BlockState written = helper.getLevel().getBlockState(place.pos());
                check(written.is(Blocks.CHEST), "cancel must commit the delivered chest");
                check(
                    written.getValue(ChestBlock.TYPE) == ChestType.SINGLE,
                    "a leftover LEFT/RIGHT half must commit as SINGLE, not a half double chest"
                );
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("lone chest half setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "A door plans one PLACE plus ATTACHED, consumes one item, and commit writes both halves")
    static void doorPlaceCommitsBothHalves(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    oakDoorStructure(),
                    "oak-door",
                    new BlockPos(2, 2, 2)
                );
                check(countKind(started.progress(), ConstructionBuildOp.Kind.PLACE) == 1,
                    "a door must fold to one PLACE");
                check(countKind(started.progress(), ConstructionBuildOp.Kind.ATTACHED) == 1,
                    "the upper door half must be ATTACHED");
                ConstructionBuildOp lower = firstPlace(started.progress());
                ConstructionBuildOp upper = firstKind(started.progress(), ConstructionBuildOp.Kind.ATTACHED);
                check(lower.material().is(Items.OAK_DOOR) && lower.material().getCount() == 1,
                    "a door must cost one door item");
                player.getInventory().add(new ItemStack(Items.OAK_DOOR));
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), lower, UUID.randomUUID()),
                    "extracting the door must succeed"
                );
                check(
                    ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), lower),
                    "delivering the lower half must place the upper half"
                );
                check(
                    upper.status() == ConstructionBuildOp.Status.DELIVERED,
                    "door delivery must deliver the upper half too"
                );
                ConstructionJobController.finish(
                    helper.getLevel().getServer(),
                    helper.getLevel(),
                    started.job(),
                    started.progress()
                );
                BlockState lowerState = helper.getLevel().getBlockState(lower.pos());
                BlockState upperState = helper.getLevel().getBlockState(upper.pos());
                check(
                    lowerState.is(Blocks.OAK_DOOR) && lowerState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER,
                    "commit must write the lower door half"
                );
                check(
                    upperState.is(Blocks.OAK_DOOR) && upperState.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER,
                    "commit must write the upper door half"
                );
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("door place setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "An attached door half without its core is skipped instead of blocking construction")
    static void orphanDoorHalfFinishesIncomplete(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    orphanDoorUpperStructure(),
                    "orphan-door-upper",
                    new BlockPos(2, 2, 2)
                );
                ConstructionBuildOp attached = firstKind(started.progress(), ConstructionBuildOp.Kind.ATTACHED);
                check(attached.status() == ConstructionBuildOp.Status.SKIPPED,
                    "an ATTACHED operation without a core must be skipped during planning");
                check(started.progress().incomplete(),
                    "skipping an orphan ATTACHED operation must mark the task incomplete");
                check(started.progress().allPlaceResolved(),
                    "an orphan ATTACHED operation must not keep BUILDING open");
                ConstructionJobController.tickJob(
                    helper.getLevel().getServer(),
                    helper.getLevel(),
                    ConstructionJobIndex.get(helper.getLevel().getServer()).job(started.job().jobId())
                );
                check(ConstructionJobIndex.get(helper.getLevel().getServer()).job(started.job().jobId()) == null,
                    "an orphan-only task must reach its incomplete terminal state");
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("orphan door setup failed: " + exception.reason());
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

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "An empty-handed allay cannot claim a giant anvil; a crab claw can")
    static void emptyHandSkipsGiantAnvil(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    giantAnvilStructure(),
                    "giant-anvil-reach",
                    new BlockPos(2, 2, 2)
                );
                WorkingAllayEntity empty = AllayGameTests.spawnHatted(helper, new Vec3(5.5D, 3.0D, 5.5D), player);
                check(empty.toolDefinition() == AllayToolDefinitions.NONE, "the empty-hand fixture must stay empty");
                ConstructionBuildOp skipped = ConstructionJobController.nextAssignable(
                    helper.getLevel(),
                    started.progress(),
                    empty
                );
                check(skipped == null, "an empty-handed allay must not be assigned a giant anvil");
                WorkingAllayEntity claw = spawnConstructionAllay(helper, new Vec3(1.5D, 3.0D, 1.5D), player, 0);
                ConstructionBuildOp claimed = ConstructionJobController.nextAssignable(
                    helper.getLevel(),
                    started.progress(),
                    claw
                );
                check(claimed != null && claimed.kind() == ConstructionBuildOp.Kind.PLACE,
                    "a crab-claw allay must still be able to place the giant anvil");
                cancelQuietly(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("empty-hand giant anvil setup failed: " + exception.reason());
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
                check(sawPorts, "commit phase must keep the AnvilCraft wire delivered and restore its ports");
                check(sawLocked, "commit phase must keep the locked repeater delivered");
                check(sawComparator, "commit phase must keep the comparator delivered");
                check(sawExtended, "commit phase must keep the extended piston delivered");
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

    @GameTest(timeoutTicks = 40, batch = "zzz_construction")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Cancel after a partial commit keeps written material, advances the stable cursor, and does not refund committed blocks")
    static void cancelAfterPartialCommitKeepsWrittenMaterial(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        UUID[] jobId = {null};
        int previous = ConstructionCommitService.blocksPerTick;
        helper.startSequence().thenExecuteAfter(5, () -> {
            ConstructionCommitService.blocksPerTick = 1;
            try {
                StartedJob started = startCobbleJob(helper, player, 2);
                jobId[0] = started.job().jobId();
                player.getInventory().add(new ItemStack(Items.COBBLESTONE, 2));
                for (ConstructionBuildOp op : started.progress().operations()) {
                    if (op.kind() != ConstructionBuildOp.Kind.PLACE) continue;
                    check(
                        ConstructionJobController.extractMaterial(player, started.progress(), op, UUID.randomUUID()),
                        "partial permission commit must extract both blocks"
                    );
                    check(
                        ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), op),
                        "partial permission commit must deliver both projections"
                    );
                }
                ConstructionBuildOp first = firstPlace(started.progress());
                ConstructionBuildOp second = firstPlaceAfter(started.progress(), first.id());
                check(
                    !ConstructionCommitService.tick(helper.getLevel(), started.progress()),
                    "budget one must leave the commit in progress"
                );
                check(helper.getLevel().getBlockState(first.pos()).is(Blocks.COBBLESTONE),
                    "the first commit tick must write the first block");
                check(helper.getLevel().getBlockState(second.pos()).isAir(),
                    "the second block must still be a projection before cancellation");

                ConstructionPermission.setWorldPermissionProvider(
                    (level, pos, owner) -> !pos.equals(first.pos())
                );
                check(
                    !ConstructionCommitService.tick(helper.getLevel(), started.progress()),
                    "revoked commit permission must stop the next commit tick"
                );
                ConstructionJob waiting = ConstructionJobIndex.get(helper.getLevel()).job(jobId[0]);
                check(
                    waiting != null && waiting.state() == ConstructionJob.STATE_WAITING_PERMISSION,
                    "partial commit denial must enter WAITING_PERMISSION"
                );
                ConstructionJobController.resumeFromSkipWait(helper.getLevel().getServer(), waiting);
                check(
                    ConstructionJobIndex.get(helper.getLevel()).job(jobId[0]).state()
                        == ConstructionJob.STATE_WAITING_PERMISSION,
                    "skip must not advance a permission-blocked partial commit"
                );

                ConstructionBlueprintService.cancel(player, jobId[0]);
                check(helper.getLevel().getBlockState(first.pos()).is(Blocks.COBBLESTONE),
                    "cancel must keep the block already written before permission loss");
                check(helper.getLevel().getBlockState(second.pos()).is(Blocks.COBBLESTONE),
                    "cursor adjustment must still commit the authorized second block");
                check(countCobble(player) == 0, "cancel must not refund either committed cobblestone");
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("partial permission commit setup failed: " + exception.reason());
            } finally {
                ConstructionPermission.setWorldPermissionProvider(null);
                ConstructionCommitService.blocksPerTick = previous;
                if (jobId[0] != null) cancelQuietly(player, jobId[0]);
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
    @TestHolder(description = "A written sign plans a blank face plus one DECORATE per aspect and charges dye, glow ink and honeycomb")
    static void signPlansBlankFaceAndPerAspectDecorations(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    signStructure(
                        helper.getLevel().registryAccess(),
                        Component.literal("hello"),
                        true,
                        true,
                        true
                    ),
                    "written-sign",
                    new BlockPos(2, 2, 2)
                );
                ConstructionJobProgress progress = started.progress();
                ConstructionBuildOp place = firstPlace(progress);
                CompoundTag config = place.blockEntity();
                check(config != null && config.contains("id"), "the sign PLACE must keep its block entity config");
                check(
                    !config.contains("front_text")
                        && !config.contains("back_text")
                        && !config.contains("is_waxed"),
                    "planning must strip text, colour, glow and wax so commit cannot restore them for free"
                );
                ConstructionBuildOp frontText =
                    signDecoration(progress, place, SignDecorationAdapter.Aspect.TEXT, true);
                ConstructionBuildOp backText =
                    signDecoration(progress, place, SignDecorationAdapter.Aspect.TEXT, false);
                ConstructionBuildOp color =
                    signDecoration(progress, place, SignDecorationAdapter.Aspect.COLOR, true);
                ConstructionBuildOp glow =
                    signDecoration(progress, place, SignDecorationAdapter.Aspect.GLOW, true);
                ConstructionBuildOp wax =
                    signDecoration(progress, place, SignDecorationAdapter.Aspect.WAX, true);
                check(frontText != null && backText != null, "both faces must plan their own writing operation");
                check(frontText.parentId() == place.id(), "DECORATE must hang on the sign PLACE");
                check(
                    frontText.material().isEmpty() && backText.material().isEmpty(),
                    "writing text must cost nothing"
                );
                check(color != null && color.material().is(Items.RED_DYE), "colouring the front must charge red dye");
                check(
                    glow != null && glow.material().is(Items.GLOW_INK_SAC),
                    "glowing the front must charge a glow ink sac"
                );
                check(wax != null && wax.material().is(Items.HONEYCOMB), "waxing must charge a honeycomb");
                check(
                    signDecoration(progress, place, SignDecorationAdapter.Aspect.COLOR, false) == null,
                    "a default black face must not plan a dye operation"
                );
                check(
                    signDecoration(progress, place, SignDecorationAdapter.Aspect.GLOW, false) == null,
                    "a face without glowing text must not plan a glow ink sac operation"
                );
                check(
                    !ConstructionJobController.tryDeliver(helper.getLevel(), progress, frontText),
                    "writing must wait until the blank sign itself is delivered"
                );
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("sign decoration planning setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_adapt")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Commit writes only the sign aspects that were really delivered and leaves the rest blank")
    static void signCommitsOnlyDeliveredDecorations(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    signStructure(
                        helper.getLevel().registryAccess(),
                        Component.literal("hello"),
                        true,
                        true,
                        true
                    ),
                    "partial-sign",
                    new BlockPos(2, 2, 2)
                );
                ConstructionJobProgress progress = started.progress();
                ConstructionBuildOp place = firstPlace(progress);
                player.getInventory().add(new ItemStack(Items.OAK_SIGN));
                player.getInventory().add(new ItemStack(Items.RED_DYE));
                player.getInventory().add(new ItemStack(Items.GLOW_INK_SAC));
                player.getInventory().add(new ItemStack(Items.HONEYCOMB));
                check(
                    ConstructionJobController.extractMaterial(player, progress, place, UUID.randomUUID()),
                    "extracting the sign must succeed"
                );
                check(
                    ConstructionJobController.tryDeliver(helper.getLevel(), progress, place),
                    "delivering the blank sign projection must succeed"
                );
                // 只交付正面书写与染色:发光、打蜡和反面书写留着不做,提交必须照样留空
                deliverDecoration(
                    helper,
                    player,
                    progress,
                    signDecoration(progress, place, SignDecorationAdapter.Aspect.TEXT, true)
                );
                deliverDecoration(
                    helper,
                    player,
                    progress,
                    signDecoration(progress, place, SignDecorationAdapter.Aspect.COLOR, true)
                );
                check(countItem(player, Items.RED_DYE) == 0, "colouring must consume the dye");
                ConstructionJobController.finish(
                    helper.getLevel().getServer(),
                    helper.getLevel(),
                    started.job(),
                    progress
                );
                check(helper.getLevel().getBlockState(place.pos()).is(Blocks.OAK_SIGN), "commit must write the sign");
                BlockEntity blockEntity = helper.getLevel().getBlockEntity(place.pos());
                check(blockEntity instanceof SignBlockEntity, "the committed sign must have a sign block entity");
                SignBlockEntity sign = (SignBlockEntity) blockEntity;
                check(
                    sign.getFrontText().getMessage(0, false).getString().equals("hello"),
                    "commit must write the delivered front text"
                );
                check(sign.getFrontText().getColor() == DyeColor.RED, "commit must write the delivered dye colour");
                check(!sign.getFrontText().hasGlowingText(), "an undelivered glow must not be restored for free");
                check(!sign.isWaxed(), "undelivered wax must not be restored for free");
                check(
                    sign.getBackText().getMessage(0, false).getString().isEmpty(),
                    "an undelivered face must stay blank"
                );
                check(countItem(player, Items.GLOW_INK_SAC) == 1, "the unused glow ink sac must stay in the inventory");
                check(countItem(player, Items.HONEYCOMB) == 1, "the unused honeycomb must stay in the inventory");
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("partial sign commit setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_adapt")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A blueprint sign carrying a run_command click event commits as plain text with nothing executable")
    static void signCommandClickEventIsFlattenedOnCommit(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                Component command = Component.literal("click me").withStyle(style -> style.withClickEvent(
                    new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/gamemode creative")
                ));
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    signStructure(helper.getLevel().registryAccess(), command, false, false, true),
                    "command-sign",
                    new BlockPos(2, 2, 2)
                );
                ConstructionJobProgress progress = started.progress();
                ConstructionBuildOp place = firstPlace(progress);
                player.getInventory().add(new ItemStack(Items.OAK_SIGN));
                player.getInventory().add(new ItemStack(Items.HONEYCOMB));
                check(
                    ConstructionJobController.extractMaterial(player, progress, place, UUID.randomUUID()),
                    "extracting the sign must succeed"
                );
                check(
                    ConstructionJobController.tryDeliver(helper.getLevel(), progress, place),
                    "delivering the blank sign projection must succeed"
                );
                deliverDecoration(
                    helper,
                    player,
                    progress,
                    signDecoration(progress, place, SignDecorationAdapter.Aspect.TEXT, true)
                );
                deliverDecoration(
                    helper,
                    player,
                    progress,
                    signDecoration(progress, place, SignDecorationAdapter.Aspect.WAX, true)
                );
                ConstructionJobController.finish(
                    helper.getLevel().getServer(),
                    helper.getLevel(),
                    started.job(),
                    progress
                );
                BlockEntity blockEntity = helper.getLevel().getBlockEntity(place.pos());
                check(blockEntity instanceof SignBlockEntity, "the committed sign must have a sign block entity");
                SignBlockEntity sign = (SignBlockEntity) blockEntity;
                Component committed = sign.getFrontText().getMessage(0, false);
                check(committed.getString().equals("click me"), "flattening must keep the visible characters");
                check(committed.getStyle().getClickEvent() == null, "flattening must drop the click event");
                // 原版只在上蜡的牌子上执行点击命令,所以蜡必须真的交付,否则这条断言会因为没上蜡而虚假通过
                check(sign.isWaxed(), "the wax aspect must be delivered or this regression asserts nothing");
                check(
                    !sign.canExecuteClickCommands(true, player),
                    "a committed sign must never carry an executable command: vanilla runs it at permission level 2"
                );
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("command sign setup failed: " + exception.reason());
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
    @TestHolder(description = "A hopper minecart entity waits until the rail at the same cell is delivered")
    static void minecartEntityWaitsForRailPlace(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    railWithMinecartStructure(),
                    "rail-hopper-minecart",
                    new BlockPos(2, 2, 2)
                );
                ConstructionBuildOp place = firstPlace(started.progress());
                ConstructionBuildOp entity = firstKind(started.progress(), ConstructionBuildOp.Kind.ENTITY);
                check(place.pos().equals(entity.pos()), "rail and minecart must share a cell");
                ConstructionBuildOp next = ConstructionJobController.nextAssignable(
                    helper.getLevel(),
                    started.progress()
                );
                check(next != null && next.kind() == ConstructionBuildOp.Kind.PLACE,
                    "the rail must be assigned before the minecart");
                player.getInventory().add(new ItemStack(Items.RAIL));
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), place, UUID.randomUUID()),
                    "extracting the rail must succeed"
                );
                check(
                    ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), place),
                    "delivering the rail must succeed"
                );
                ConstructionBuildOp afterRail = ConstructionJobController.nextAssignable(
                    helper.getLevel(),
                    started.progress()
                );
                check(afterRail != null && afterRail.kind() == ConstructionBuildOp.Kind.ENTITY,
                    "the minecart must become assignable after the rail");
                ConstructionBlueprintService.cancel(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("rail-then-minecart setup failed: " + exception.reason());
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

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_adapt")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Canceling a denied delivered water projection returns one filled bucket and consumes the allay's empty bucket")
    static void cancelDeniedDeliveredWaterSettlesPhysicalBucket(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        UUID[] jobId = {null};
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startStructureJob(
                    helper,
                    player,
                    multiBlockStructure(List.of(BlockPos.ZERO), List.of(Blocks.WATER.defaultBlockState())),
                    "denied-water-cancel",
                    new BlockPos(2, 2, 2)
                );
                jobId[0] = started.job().jobId();
                WorkingAllayEntity worker = spawnConstructionAllay(
                    helper,
                    new Vec3(1.5D, 2.0D, 1.5D),
                    player,
                    0
                );
                player.getInventory().add(new ItemStack(Items.WATER_BUCKET));
                ConstructionBuildOp place = firstPlace(started.progress());
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), place, worker.getUUID()),
                    "denied water fixture must extract the filled bucket"
                );
                worker.setHostedCarry(new ItemStack(Items.WATER_BUCKET));
                check(
                    ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), place, worker),
                    "denied water fixture must deliver the projection"
                );
                check(place.returnStack().is(Items.BUCKET), "water delivery must produce an empty bucket");
                worker.setHostedCarry(place.returnStack().copy());
                player.moveTo(helper.absoluteVec(new Vec3(4.5D, 2.0D, 4.5D)));
                ConstructionPermission.setWorldPermissionProvider(
                    (level, pos, owner) -> !pos.equals(place.pos())
                );

                ConstructionBlueprintService.cancel(player, jobId[0]);
                check(helper.getLevel().getBlockState(place.pos()).isAir(),
                    "denied delivered water must not be committed");
                check(countItem(player, Items.WATER_BUCKET) == 1,
                    "cancel must return the original filled bucket exactly once");
                check(countItem(player, Items.BUCKET) == 0,
                    "rolling back water must not also return the transformed empty bucket");
                check(worker.hostedCarry().isEmpty(),
                    "rolling back water must consume the allay's physical empty bucket");
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("denied delivered water setup failed: " + exception.reason());
            } finally {
                ConstructionPermission.setWorldPermissionProvider(null);
                if (jobId[0] != null) cancelQuietly(player, jobId[0]);
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
        check(!entity.longReach(), "a one-block resin cauldron must stay within empty-hand reach");
        WorkingAllayEntity empty = AllayGameTests.spawnHatted(helper, new Vec3(3.5D, 3.0D, 3.5D), player);
        ConstructionBuildOp assignable = ConstructionJobController.nextAssignable(
            helper.getLevel(),
            started.progress(),
            empty
        );
        check(
            assignable != null && assignable.id() == entity.id(),
            "an empty-handed allay must still be able to place a one-block plastic entity"
        );
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

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_lounge")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "Inserting a deployed disk claims the lounge without starting, and does not pause the owner's other job")
    static void loungeDiskInsertClaimsWithoutStarting(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob first = startCobbleJob(helper, player, 2, new BlockPos(4, 2, 4));
                ItemStack secondDisk = deployDisk(helper, player, 1, new BlockPos(6, 2, 4));
                UUID secondId = ConstructionBlueprintData.get(secondDisk).flatMap(ConstructionBlueprintData::jobId).orElse(null);
                check(secondId != null, "deployed disk must carry a job id");
                AllayLoungeBlockEntity lounge = placeLounge(helper, new BlockPos(2, 2, 2));
                lounge.setOwner(player.getUUID());
                lounge.items().setStackInSlot(AllayLoungeBlockEntity.DISK_SLOT, secondDisk);
                MinecraftServer server = helper.getLevel().getServer();
                ConstructionJob second = ConstructionJobIndex.get(server).job(secondId);
                ConstructionJob other = ConstructionJobIndex.get(server).job(first.job().jobId());
                ConstructionJobProgress progress = ConstructionJobStore.get(server).get(secondId);
                check(second != null && !second.isActive(), "inserting the disk must claim without starting");
                check(other != null && other.isActive(), "the owner's other job must stay running");
                check(progress != null && lounge.getBlockPos().equals(progress.coordinatorLounge()),
                    "claimed progress must record this lounge");
                ConstructionBlueprintService.cancel(player, secondId);
                ConstructionBlueprintService.cancel(player, first.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("lounge disk claim setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 60, batch = "zzz_construction_lounge")
    @EmptyTemplate(value = "11x6x11", floor = true)
    @TestHolder(description = "A lounge-bound teammate selects that lounge's job and a worker leased elsewhere is not counted as available")
    static void teamSchedulingPrefersCoordinatorAndExcludesOtherLeases(ExtendedGameTestHelper helper) {
        GameTestPlayer loungeOwner = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        GameTestPlayer jobOwner = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        UUID[] ownJobId = {null};
        UUID[] coordinatedJobId = {null};
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                ConstructionPermission.setCollaboratorProvider((server, first, second) ->
                    first.equals(loungeOwner.getUUID()) && second.equals(jobOwner.getUUID())
                        || first.equals(jobOwner.getUUID()) && second.equals(loungeOwner.getUUID())
                );
                BlockPos ownAnchor = new BlockPos(5, 2, 7);
                BlockPos coordinatedAnchor = new BlockPos(8, 2, 7);
                helper.setBlock(ownAnchor, Blocks.STONE);
                helper.setBlock(coordinatedAnchor, Blocks.STONE);

                StartedJob own = startCobbleJob(helper, loungeOwner, 1, ownAnchor);
                ownJobId[0] = own.job().jobId();
                ItemStack coordinatedDisk = deployDisk(helper, jobOwner, 1, coordinatedAnchor);
                coordinatedJobId[0] = ConstructionBlueprintData.get(coordinatedDisk)
                    .flatMap(ConstructionBlueprintData::jobId)
                    .orElseThrow();
                AllayLoungeBlockEntity lounge = placeLounge(helper, new BlockPos(2, 2, 2));
                lounge.setOwner(loungeOwner.getUUID());
                lounge.items().setStackInSlot(AllayLoungeBlockEntity.DISK_SLOT, coordinatedDisk);
                ConstructionBlueprintService.start(helper.getLevel().getServer(), coordinatedJobId[0]);

                ConstructionJob coordinated = ConstructionJobIndex.get(helper.getLevel()).job(coordinatedJobId[0]);
                ConstructionJobProgress coordinatedProgress = ConstructionJobStore.get(helper.getLevel())
                    .get(coordinatedJobId[0]);
                check(coordinated != null && coordinated.isActive(), "the teammate job was not activated");
                check(coordinatedProgress != null
                    && lounge.getBlockPos().equals(coordinatedProgress.coordinatorLounge()),
                    "the teammate job did not retain the lounge coordinator");

                WorkingAllayEntity local = AllayGameTests.spawnHattedForOwner(
                    helper,
                    new Vec3(2.5D, 3.0D, 2.5D),
                    loungeOwner.getUUID()
                );
                local.setHomeLounge(lounge.getBlockPos());
                ConstructionJob selected = ConstructionJobController.jobForWorker(helper.getLevel(), local);
                check(selected != null && selected.jobId().equals(coordinatedJobId[0]),
                    "the lounge-bound teammate selected its owner's unrelated active job");

                WorkingAllayEntity leasedElsewhere = spawnDemolitionAllay(
                    helper,
                    new Vec3(3.5D, 3.0D, 2.5D),
                    loungeOwner,
                    0
                );
                leasedElsewhere.setHomeLounge(lounge.getBlockPos());
                ConstructionBuildOp ownDemolish = own.progress().operations().stream()
                    .filter(op -> op.kind() == ConstructionBuildOp.Kind.DEMOLISH)
                    .findFirst()
                    .orElseThrow();
                leasedElsewhere.assign(own.job().jobId(), ownDemolish.id());
                check(
                    !ConstructionJobController.hasAvailableDemolitionAllay(
                        helper.getLevel(),
                        coordinated,
                        coordinatedProgress
                    ),
                    "a worker leased to another team job satisfied this job's demolition availability"
                );

                ConstructionBlueprintService.cancel(jobOwner, coordinatedJobId[0]);
                ConstructionBlueprintService.cancel(loungeOwner, ownJobId[0]);
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("team scheduling setup failed: " + exception.reason());
            } finally {
                ConstructionPermission.setCollaboratorProvider(null);
                if (coordinatedJobId[0] != null) cancelQuietly(jobOwner, coordinatedJobId[0]);
                if (ownJobId[0] != null) cancelQuietly(loungeOwner, ownJobId[0]);
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_lounge")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "After a lounge claim, unhosted construction cannot claim or extract from the player")
    static void claimedJobRejectsUnhostedExtract(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = claimCobbleAtLounge(helper, player, 2);
                player.getInventory().add(new ItemStack(Items.COBBLESTONE, 4));
                WorkingAllayEntity worker = spawnConstructionAllay(helper, new Vec3(4.5D, 2.0D, 4.5D), player, 0);
                check(
                    !ConstructionAllayToolBehavior.tryClaim(worker, helper.getLevel(), started.job(), started.progress()),
                    "unhosted construction must not claim a lounge job"
                );
                check(
                    !ConstructionJobController.extractMaterial(
                        player,
                        started.progress(),
                        firstPlace(started.progress()),
                        worker.getUUID()
                    ),
                    "claimed jobs must not extract from the player"
                );
                ConstructionBlueprintService.cancel(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("unhosted reject setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_lounge")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "An evicted builder returns its item and clears stale carried ledgers without an assignment")
    static void claimedJobEvictsCarryingUnhosted(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                StartedJob started = startCobbleJob(helper, player, 2, new BlockPos(4, 2, 4));
                player.getInventory().add(new ItemStack(Items.COBBLESTONE, 4));
                WorkingAllayEntity worker = spawnConstructionAllay(helper, new Vec3(4.5D, 2.0D, 4.5D), player, 0);
                ConstructionBuildOp first = firstPlace(started.progress());
                started.progress().addLedger(-1, new ItemStack(Items.RAIL), worker.getUUID());
                check(
                    ConstructionJobController.extractMaterial(player, started.progress(), first, worker.getUUID()),
                    "unhosted extract must succeed before the claim"
                );
                ItemStack reserved = started.progress().carriedBy(worker.getUUID(), first.id());
                check(reserved != null && reserved.is(Items.COBBLESTONE), "stale ledger changed the claimed material");
                worker.setHostedCarry(new ItemStack(Items.COBBLESTONE));
                worker.assign(started.job().jobId(), first.id());
                int before = countCobble(player);
                AllayLoungeBlockEntity lounge = placeLounge(helper, new BlockPos(2, 2, 2));
                lounge.setOwner(player.getUUID());
                lounge.items().setStackInSlot(AllayLoungeBlockEntity.DISK_SLOT, player.getMainHandItem().copy());
                check(worker.assignedJobId().isEmpty(), "eviction must clear the unhosted assignment");
                check(!worker.hostedCarry().isEmpty(), "eviction must keep the carried cobble");
                ConstructionJobController.depositHostedCarry(worker, player);
                check(countCobble(player) == before + 1, "the evicted carry must return to the owner");
                check(worker.hostedCarry().isEmpty(), "returning to the owner must empty the carry");
                check(carriedCount(started.progress()) == 0, "returning without an assignment left stale carried ledgers");
                ConstructionBlueprintService.cancel(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("evict carry setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 80, batch = "zzz_construction_lounge")
    @EmptyTemplate(value = "9x6x9", floor = true)
    @TestHolder(description = "A reloaded worker restores its carried construction ledger and coordinator binding")
    static void reloadRestoresCarriedConstructionWorker(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                AllayLoungeBlockEntity lounge = placeLoungeWithChest(
                    helper,
                    new BlockPos(2, 2, 2),
                    new ItemStack(Items.COBBLESTONE, 4)
                );
                StartedJob started = claimCobbleAtLounge(helper, player, 1, lounge);
                WorkingAllayEntity worker = spawnConstructionAllay(
                    helper,
                    new Vec3(4.5D, 3.0D, 4.5D),
                    player,
                    0
                );
                worker.setHomeLounge(lounge.getBlockPos());
                ConstructionBuildOp operation = firstPlace(started.progress());
                check(
                    ConstructionJobController.extractMaterialFromLounge(
                        helper.getLevel(),
                        started.progress(),
                        operation,
                        worker.getUUID()
                    ),
                    "the coordinator chest must provide the carried material"
                );
                worker.setHostedCarry(new ItemStack(Items.COBBLESTONE));
                // 模拟实体 NBT 重载后丢失运行时租约、协调室字段与路径快照。
                operation.setStatus(ConstructionBuildOp.Status.PENDING);
                operation.setLeaseAllay(null);
                operation.setApproach(null);
                worker.clearAssignment(false);
                worker.setHomeLounge(null);
                worker.navigator().clear();

                ConstructionAllayToolBehavior.INSTANCE.serverTick(worker);

                check(
                    worker.assignedJobId().filter(started.job().jobId()::equals).isPresent(),
                    "a carried ledger must restore the worker's construction assignment"
                );
                check(worker.taskOpId() == operation.id(), "the original carried operation must be restored");
                check(lounge.getBlockPos().equals(worker.homeLoungePos()),
                    "the coordinator binding must be restored from the progress ledger");
                check(operation.leaseAllay().filter(worker.getUUID()::equals).isPresent(),
                    "restored operation must reacquire its allay lease");
                check(worker.hostedCarry().is(Items.COBBLESTONE),
                    "restoring the ledger must not discard the physical carried item");
                check(
                    helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class,
                        worker.getBoundingBox().inflate(1.5D),
                        item -> item.getItem().is(Items.COBBLESTONE)
                    ).isEmpty(),
                    "restoring a valid carried ledger must not drop a duplicate item"
                );
                ConstructionBlueprintService.cancel(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("reload carried-worker setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_lounge")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "A carried ledger without physical material is returned instead of recreated")
    static void missingCarriedMaterialIsNotRecreated(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                AllayLoungeBlockEntity lounge = placeLoungeWithChest(
                    helper,
                    new BlockPos(2, 2, 2),
                    new ItemStack(Items.COBBLESTONE, 1)
                );
                StartedJob started = claimCobbleAtLounge(helper, player, 1, lounge);
                WorkingAllayEntity worker = spawnConstructionAllay(
                    helper,
                    new Vec3(4.5D, 3.0D, 4.5D),
                    player,
                    0
                );
                ConstructionBuildOp operation = firstPlace(started.progress());
                started.progress().addLedger(
                    operation.id(),
                    new ItemStack(Items.COBBLESTONE),
                    worker.getUUID()
                );
                operation.setStatus(ConstructionBuildOp.Status.PENDING);
                operation.setLeaseAllay(null);
                operation.setApproach(null);

                ConstructionAllayToolBehavior.INSTANCE.serverTick(worker);

                check(worker.assignedJobId().isEmpty(),
                    "a worker without the carried item must not keep a synthetic assignment");
                check(worker.hostedCarry().isEmpty(),
                    "missing carried material must not be recreated on the worker");
                check(carriedCount(started.progress()) == 0,
                    "missing carried material must close its stale ledger entry");
                check(operation.status() == ConstructionBuildOp.Status.PENDING,
                    "missing carried material must return the operation to pending");
                check(
                    countInChest(helper, lounge.getBlockPos().below(), Items.COBBLESTONE) == 1,
                    "missing carried material must not consume another chest item"
                );
                ConstructionBlueprintService.cancel(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("missing carried-worker setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_lounge")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "Claimed extract takes cobble from the chest below, and creative players cannot bypass it")
    static void claimedExtractUsesChestNotCreative(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.CREATIVE);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                AllayLoungeBlockEntity lounge = placeLoungeWithChest(helper, new BlockPos(2, 2, 2), new ItemStack(Items.COBBLESTONE, 2));
                StartedJob started = claimCobbleAtLounge(helper, player, 2, lounge);
                WorkingAllayEntity worker = spawnConstructionAllay(
                    helper,
                    new Vec3(2.5D, 3.0D, 2.5D),
                    player,
                    0
                );
                worker.setHomeLounge(lounge.getBlockPos());
                ConstructionBuildOp first = firstPlace(started.progress());
                check(
                    !ConstructionJobController.extractMaterial(player, started.progress(), first, UUID.randomUUID()),
                    "claimed extract from the creative player must fail"
                );
                check(countCobble(player) == 0, "creative inventory must stay empty");
                check(
                    ConstructionJobController.extractMaterialFromLounge(
                        helper.getLevel(),
                        started.progress(),
                        first,
                        worker.getUUID()
                    ),
                    "claimed extract must take cobble from the chest below"
                );
                check(countInChest(helper, lounge.getBlockPos().below(), Items.COBBLESTONE) == 1,
                    "the chest must lose exactly one cobble");
                check(carriedCount(started.progress()) == 1, "the ledger must record the reserved cobble");
                check(countCobble(player) == 0, "creative extract must not invent cobble in the inventory");
                ConstructionBlueprintService.cancel(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("lounge extract setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_lounge")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "Releasing a lease before pickup returns its reserved material exactly once")
    static void claimedLeaseReleaseBeforePickupReturnsReservation(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                AllayLoungeBlockEntity lounge = placeLoungeWithChest(
                    helper,
                    new BlockPos(2, 2, 2),
                    new ItemStack(Items.COBBLESTONE, 2)
                );
                StartedJob started = claimCobbleAtLounge(helper, player, 1, lounge);
                WorkingAllayEntity worker = spawnConstructionAllay(
                    helper,
                    new Vec3(4.5D, 2.0D, 4.5D),
                    player,
                    0
                );
                worker.setHomeLounge(lounge.getBlockPos());
                ConstructionBuildOp operation = firstPlace(started.progress());
                check(
                    ConstructionAllayToolBehavior.tryClaim(
                        worker,
                        helper.getLevel(),
                        started.job(),
                        started.progress()
                    ),
                    "bound builder failed to reserve the lounge material"
                );
                check(worker.hostedCarry().isEmpty(), "reserved material moved into the builder before pickup");
                check(countInChest(helper, lounge.getBlockPos().below(), Items.COBBLESTONE) == 1,
                    "claim must reserve exactly one cobblestone");
                check(carriedCount(started.progress()) == 1, "claim must create exactly one carried ledger entry");

                worker.resetStuck();
                for (int tick = 0; tick < 80; tick++) {
                    worker.noteProgress();
                }
                ConstructionAllayToolBehavior.INSTANCE.serverTick(worker);

                check(worker.assignedJobId().isEmpty(), "stuck pre-pickup builder kept its assignment");
                check(operation.status() == ConstructionBuildOp.Status.PENDING, "released operation kept its lease status");
                check(countInChest(helper, lounge.getBlockPos().below(), Items.COBBLESTONE) == 2,
                    "released reservation did not return exactly one cobblestone");
                check(carriedCount(started.progress()) == 0, "released reservation left a carried ledger entry");
                check(lounge.pickupDisplays().isEmpty(), "released reservation left a stale pickup display");
                ConstructionBlueprintService.cancel(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("pre-pickup release setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_lounge")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "A creative crate below supplies any blueprint item infinitely without consuming the crate or the player")
    static void claimedExtractUsesCreativeCrateInfinitely(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                AllayLoungeBlockEntity lounge = placeLoungeWithCreativeCrate(helper, new BlockPos(2, 2, 2));
                StartedJob started = claimCobbleAtLounge(helper, player, 2, lounge);
                WorkingAllayEntity worker = spawnConstructionAllay(
                    helper,
                    new Vec3(2.5D, 3.0D, 2.5D),
                    player,
                    0
                );
                worker.setHomeLounge(lounge.getBlockPos());
                ConstructionBuildOp first = firstPlace(started.progress());
                ConstructionMaterialAccess access = ConstructionMaterialAccess.below(
                    helper.getLevel(),
                    lounge.getBlockPos()
                );
                check(access.isInfinite(), "a creative crate below must be infinite supply");
                check(
                    ConstructionJobController.extractMaterialFromLounge(
                        helper.getLevel(),
                        started.progress(),
                        first,
                        worker.getUUID()
                    ),
                    "claimed extract must invent cobble from the creative crate"
                );
                check(carriedCount(started.progress()) == 1, "the ledger must record the reserved cobble");
                check(access.countItems().getOrDefault(Items.COBBLESTONE, 0) == 0,
                    "the creative crate must not store or lose cobble");
                check(countCobble(player) == 0, "infinite crate extract must not take from the player");
                ConstructionBlueprintService.cancel(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("creative crate extract setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_lounge")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "Removing the disk returns a carried ledger entry to the chest, or drops it beside when full")
    static void diskRemoveReturnsOrDropsBeside(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                AllayLoungeBlockEntity lounge = placeLoungeWithChest(helper, new BlockPos(2, 2, 2), new ItemStack(Items.COBBLESTONE, 2));
                StartedJob started = claimCobbleAtLounge(helper, player, 2, lounge);
                WorkingAllayEntity worker = spawnConstructionAllay(
                    helper,
                    new Vec3(2.5D, 3.0D, 2.5D),
                    player,
                    0
                );
                worker.setHomeLounge(lounge.getBlockPos());
                ConstructionBuildOp first = firstPlace(started.progress());
                check(
                    ConstructionJobController.extractMaterialFromLounge(
                        helper.getLevel(),
                        started.progress(),
                        first,
                        worker.getUUID()
                    ),
                    "setup extract must take one cobble"
                );
                lounge.items().setStackInSlot(AllayLoungeBlockEntity.DISK_SLOT, ItemStack.EMPTY);
                check(countInChest(helper, lounge.getBlockPos().below(), Items.COBBLESTONE) == 2,
                    "removing the disk must insert the carried cobble back into the chest");
                check(carriedCount(started.progress()) == 0, "returned ledger entries must leave the carried state");

                lounge.items().setStackInSlot(AllayLoungeBlockEntity.DISK_SLOT, player.getMainHandItem().copy());
                ConstructionJob claimed = ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId());
                ConstructionJobProgress progress = ConstructionJobStore.get(helper.getLevel()).get(started.job().jobId());
                check(
                    claimed != null
                        && !claimed.isActive()
                        && progress != null
                        && lounge.getBlockPos().equals(progress.coordinatorLounge()),
                    "reinserting the disk must reclaim the lounge without restarting"
                );
                ConstructionBuildOp next = firstOpenPlace(progress);
                check(
                    ConstructionJobController.extractMaterialFromLounge(
                        helper.getLevel(),
                        progress,
                        next,
                        worker.getUUID()
                    ),
                    "second extract must take another cobble"
                );
                fillChest(helper, lounge.getBlockPos().below(), Items.COBBLESTONE);
                ConstructionJobController.pause(helper.getLevel().getServer(), claimed);
                check(countInChest(helper, lounge.getBlockPos().below(), Items.COBBLESTONE) == 27 * 64,
                    "a full chest must not receive a duplicate cobble");
                List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class,
                    new AABB(lounge.getBlockPos()).inflate(2.0D),
                    item -> item.getItem().is(Items.COBBLESTONE)
                );
                check(!drops.isEmpty(), "overflow must drop beside the lounge");
                ConstructionBlueprintService.cancel(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("disk return setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_lounge")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "Removing the chest below marks source unavailable; a leased allay hovers and keeps its carry")
    static void missingChestMarksSourceUnavailable(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                AllayLoungeBlockEntity lounge = placeLoungeWithChest(helper, new BlockPos(2, 2, 2), new ItemStack(Items.COBBLESTONE, 2));
                StartedJob started = claimCobbleAtLounge(helper, player, 2, lounge);
                WorkingAllayEntity worker = spawnConstructionAllay(helper, new Vec3(2.5D, 3.0D, 2.5D), player, 0);
                worker.setHomeLounge(lounge.getBlockPos());
                ConstructionBuildOp first = firstPlace(started.progress());
                check(
                    ConstructionJobController.extractMaterialFromLounge(
                        helper.getLevel(),
                        started.progress(),
                        first,
                        worker.getUUID()
                    ),
                    "setup extract must reserve cobble"
                );
                first.setStatus(ConstructionBuildOp.Status.LEASED);
                first.setLeaseAllay(worker.getUUID());
                worker.assign(started.job().jobId(), first.id());
                worker.setHostedCarry(new ItemStack(Items.COBBLESTONE));
                helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);
                ConstructionJob current = ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId());
                check(current != null, "claimed job must still exist before the chest is removed");
                ConstructionJobController.tickJob(helper.getLevel().getServer(), helper.getLevel(), current);
                ConstructionJob waiting = ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId());
                check(waiting != null && waiting.state() == ConstructionJob.STATE_SOURCE_UNAVAILABLE,
                    "removing the chest must mark source unavailable");
                ConstructionAllayToolBehavior.INSTANCE.serverTick(worker);
                check(!worker.hostedCarry().isEmpty(), "source wait must keep the carried item");
                check(worker.assignedJobId().filter(started.job().jobId()::equals).isPresent(),
                    "source wait must keep the lease");
                helper.setBlock(new BlockPos(2, 1, 2), Blocks.CHEST);
                ConstructionJobController.tickJob(helper.getLevel().getServer(), helper.getLevel(), waiting);
                ConstructionJob resumed = ConstructionJobIndex.get(helper.getLevel()).job(started.job().jobId());
                check(resumed != null && resumed.state() != ConstructionJob.STATE_SOURCE_UNAVAILABLE,
                    "replacing the chest must resume the job");
                ConstructionBlueprintService.cancel(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("source unavailable setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_lounge")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "The lounge does not launch another builder while the current lowest seal layer is fully leased")
    static void loungeDoesNotLaunchForBlockedUpperSealLayer(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                AllayLoungeBlockEntity lounge = placeLoungeWithChest(
                    helper,
                    new BlockPos(2, 2, 2),
                    new ItemStack(Items.DIRT, 32)
                );
                if (!(helper.getBlockEntity(new BlockPos(2, 1, 2)) instanceof Container container)) {
                    throw new GameTestAssertException("seal dispatch source chest is missing");
                }
                container.setItem(1, new ItemStack(Items.COBBLESTONE, 16));
                StartedJob started = claimCobbleAtLounge(helper, player, 4, lounge);
                UUID leaseOwner = UUID.randomUUID();
                for (int x = 0; x < 2; x++) {
                    ConstructionBuildOp lower = started.progress().addOperation(
                        helper.absolutePos(new BlockPos(4 + x, 2, 2)),
                        Blocks.AIR.defaultBlockState(),
                        new ItemStack(Items.DIRT),
                        ConstructionBuildOp.Kind.SEAL,
                        ConstructionBuildOp.Status.LEASED
                    );
                    lower.setLeaseAllay(leaseOwner);
                }
                for (int x = 0; x < 3; x++) {
                    started.progress().addOperation(
                        helper.absolutePos(new BlockPos(3 + x, 3, 2)),
                        Blocks.AIR.defaultBlockState(),
                        new ItemStack(Items.DIRT),
                        ConstructionBuildOp.Kind.SEAL,
                        ConstructionBuildOp.Status.PENDING
                    );
                }
                WorkingAllayEntity worker = spawnConstructionAllay(
                    helper,
                    new Vec3(3.5D, 3.0D, 3.5D),
                    player,
                    0
                );
                worker.setHomeLounge(lounge.getBlockPos());
                check(lounge.addHosted(constructionRecord(player.getUUID())),
                    "failed to host the spare seal builder");
                ConstructionJob sealing = started.job().withState(ConstructionJob.STATE_SEALING_FLUID);
                ConstructionJobIndex.get(helper.getLevel()).put(sealing);

                ConstructionJobController.tickJob(
                    helper.getLevel().getServer(),
                    helper.getLevel(),
                    sealing
                );

                check(lounge.hosted().size() == 1,
                    "the lounge launched a builder that could not claim the blocked upper layer");
                check(!lounge.isBayBusy(), "a blocked seal layer must not occupy the outbound bay");
                worker.discard();
                cancelQuietly(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("blocked seal dispatch setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 80, batch = "zzz_construction_lounge")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "tryLaunch releases one hosted builder and occupies the 20 gt bay")
    static void tryLaunchOccupiesBay(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        AllayLoungeBlockEntity lounge = placeLounge(helper, new BlockPos(2, 2, 2));
        lounge.setOwner(player.getUUID());
        helper.startSequence()
            .thenExecute(() -> {
                check(lounge.addHosted(constructionRecord(player.getUUID())), "failed to host a construction record");
                check(lounge.addHosted(constructionRecord(player.getUUID())), "failed to host a second record");
                check(
                    lounge.tryLaunch(record -> AllayToolDefinitions.fromHeldItem(record.heldTool())
                        .hasCapability(AllayCapability.PICK_UP_MATERIAL)),
                    "lounge must launch a hosted builder"
                );
                check(lounge.hosted().size() == 1, "tryLaunch must remove exactly one hosted record");
                check(lounge.isBayBusy(), "outbound launch must occupy the bay");
                check(!lounge.releaseHosted(0), "GUI release must fail while the bay is busy");
                check(
                    !lounge.tryLaunch(record -> true),
                    "a second launch must fail while the bay is busy"
                );
                List<WorkingAllayEntity> launched = helper.getLevel().getEntitiesOfClass(
                    WorkingAllayEntity.class,
                    new AABB(lounge.getBlockPos()).inflate(3.0D)
                );
                check(launched.size() == 1, "tryLaunch must spawn exactly one builder");
                launched.getFirst().setHomeLounge(null);
            })
            .thenExecuteAfter(AllayLoungeBlockEntity.DOCKING_DURATION_TICKS + 1, () -> {
                check(!lounge.isBayBusy(), "the outbound bay must clear after 20 gt");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 200, batch = "zzz_construction_lounge")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "A magnet allay docks after a claimed job leaves the collect phase")
    static void magnetAllayDocksWhenJobIsBuilding(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        AllayLoungeBlockEntity[] loungeSlot = new AllayLoungeBlockEntity[1];
        WorkingAllayEntity[] workerSlot = new WorkingAllayEntity[1];
        StartedJob[] jobSlot = new StartedJob[1];
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                AllayLoungeBlockEntity lounge = placeLoungeWithChest(
                    helper,
                    new BlockPos(2, 2, 2),
                    new ItemStack(Items.COBBLESTONE, 2)
                );
                StartedJob started = claimCobbleAtLounge(helper, player, 2, lounge);
                WorkingAllayEntity worker = CollectionAllayGameTests.spawnCollectionAllay(
                    helper,
                    new Vec3(2.5D, 3.5D, 2.5D),
                    player
                );
                worker.setHomeLounge(lounge.getBlockPos());
                loungeSlot[0] = lounge;
                workerSlot[0] = worker;
                jobSlot[0] = started;
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("magnet dock setup failed: " + exception.reason());
            }
        }).thenWaitUntil(() -> {
            AllayLoungeBlockEntity lounge = loungeSlot[0];
            WorkingAllayEntity worker = workerSlot[0];
            check(lounge != null && worker != null, "magnet dock setup did not run");
            check(
                worker.isRemoved() && lounge.hosted().size() == 1,
                "magnet allay must dock once the claimed job is building"
            );
        }).thenExecute(() -> {
            StartedJob started = jobSlot[0];
            check(started != null, "claimed job is missing");
            cancelQuietly(player, started.job().jobId());
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_lounge")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "A lounge builder docks when every remaining build operation is already leased")
    static void idleBuilderDocksWhenNoBuildWorkCanBeClaimed(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                AllayLoungeBlockEntity lounge = placeLoungeWithChest(
                    helper,
                    new BlockPos(2, 2, 2),
                    new ItemStack(Items.COBBLESTONE, 2)
                );
                StartedJob started = claimCobbleAtLounge(helper, player, 2, lounge);
                check(started.job().state() == ConstructionJob.STATE_BUILDING, "claimed job must be building");
                UUID leaseOwner = UUID.randomUUID();
                int leased = 0;
                for (ConstructionBuildOp operation : started.progress().operations()) {
                    if (operation.status() != ConstructionBuildOp.Status.PENDING || !operation.writesProjection()) {
                        continue;
                    }
                    operation.setStatus(ConstructionBuildOp.Status.LEASED);
                    operation.setLeaseAllay(leaseOwner);
                    leased++;
                }
                check(leased > 0, "setup found no remaining build operation to lease");

                WorkingAllayEntity worker = spawnConstructionAllay(
                    helper,
                    new Vec3(4.5D, 3.0D, 3.5D),
                    player,
                    0
                );
                worker.setHomeLounge(lounge.getBlockPos());
                worker.setActionState((byte) 5);
                worker.setWaitReason(ConstructionWaitReason.OCCUPIED);
                worker.navigator().setPath(List.of(helper.absoluteVec(new Vec3(5.5D, 3.0D, 5.5D))));
                worker.setFlightState(AllayFlightState.FLYING);
                worker.resetStuck();
                for (int tick = 0; tick < 80; tick++) {
                    worker.noteProgress();
                }
                BlockPos reserved = helper.absolutePos(new BlockPos(6, 3, 6));
                ConstructionTraffic.reserveApproach(helper.getLevel(), worker.getUUID(), reserved);
                check(ConstructionTraffic.isReserved(helper.getLevel(), reserved, null), "setup traffic reservation is missing");

                worker.toolDefinition().behavior().serverTick(worker);

                check(worker.flightState() == AllayFlightState.DOCKING, "idle builder stayed at the build site");
                check(worker.isDockingTo(lounge.getBlockPos()), "idle builder did not target its home lounge");
                check(worker.getActionState() == 0, "docking kept the stale construction action");
                check(worker.waitReason() == ConstructionWaitReason.NONE, "docking kept the stale wait reason");
                check(!worker.navigator().hasPath(), "docking kept the stale work path");
                check(!worker.isMotionStuck(), "docking kept the stale stall watchdog");
                check(
                    !ConstructionTraffic.isReserved(helper.getLevel(), reserved, null),
                    "docking kept the stale construction traffic reservation"
                );
                cancelQuietly(player, started.job().jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException("idle builder docking setup failed: " + exception.reason());
            }
        }).thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_enclose")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "A hollow cube keeps the final closure worker outside delivered projection walls")
    static void hollowCubeSealsFromOutside(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence()
            .thenExecuteAfter(1, () -> {
                try {
                    StartedJob started = startStructureJob(
                        helper,
                        player,
                        hollowCubeStructure(),
                        "hollow-cube",
                        new BlockPos(2, 2, 2)
                    );
                    ConstructionBuildOp first = lowestPlace(started.progress());
                    ConstructionBuildOp roof = placeAt(started.progress(), helper.absolutePos(new BlockPos(3, 4, 3)));
                    check(first.order() < roof.order(), "interior cells must precede the roof");
                    BlockPos approach = ConstructionJobController.chooseApproach(
                        helper.getLevel(),
                        started.progress(),
                        roof
                    );
                    check(approach != null, "roof must have an approach");
                    AABB box = new AABB(helper.absolutePos(new BlockPos(2, 2, 2))).minmax(
                        new AABB(helper.absolutePos(new BlockPos(4, 4, 4)))
                    );
                    check(
                        !box.intersects(ConstructionWorkerSpace.boxAt(approach).deflate(0.05D)),
                        "roof approach must stay outside the hollow cube"
                    );
                    for (ConstructionBuildOp op : started.progress().operations()) {
                        if (op.kind() == ConstructionBuildOp.Kind.PLACE && op.id() != roof.id()) {
                            deliverForTest(helper.getLevel(), started.progress(), op);
                        }
                    }
                    BlockPos interior = helper.absolutePos(new BlockPos(3, 3, 3));
                    WorkingAllayEntity worker = spawnConstructionAllay(
                        helper,
                        new Vec3(3.5D, 3.0D, 3.5D),
                        player,
                        0
                    );
                    ConstructionEnclosure.Analysis closing = ConstructionEnclosure.analyze(
                        helper.getLevel(),
                        started.progress(),
                        roof,
                        worker
                    );
                    check(!closing.open() && closing.enclosesSelf(),
                        "delivered projection walls did not expose the worker inside the closing cavity");
                    check(!ConstructionJobController.isUsableApproach(
                        helper.getLevel(), started.progress(), roof, interior, worker),
                        "a stale interior roof approach remained usable after the walls closed");
                    check(!ConstructionJobController.tryDeliver(
                        helper.getLevel(), started.progress(), roof, worker),
                        "the roof projection sealed its own worker inside the hollow cube");
                    BlockPos closingApproach = ConstructionJobController.chooseApproach(
                        helper.getLevel(),
                        started.progress(),
                        roof,
                        worker
                    );
                    check(closingApproach != null,
                        "the worker inside the cavity could not select an exterior closing approach");
                    check(!box.intersects(ConstructionWorkerSpace.boxAt(closingApproach).deflate(0.05D)),
                        "the refreshed roof approach stayed inside the hollow cube");
                    Vec3 outside = ConstructionWorkerSpace.navigationPoint(closingApproach);
                    worker.setPos(outside.x, outside.y, outside.z);
                    check(ConstructionJobController.tryDeliver(
                        helper.getLevel(), started.progress(), roof, worker),
                        "the roof could not close after its worker evacuated the cavity");
                    worker.discard();
                    cancelQuietly(player, started.job().jobId());
                } catch (ConstructionBlueprintException exception) {
                    throw new GameTestAssertException("hollow cube setup failed: " + exception.reason());
                }
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_enclose")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "A door is postponed while the cavity still has unfinished work")
    static void doorWaitsForUnfinishedCavity(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence()
            .thenExecuteAfter(1, () -> {
                try {
                    StartedJob started = startStructureJob(
                        helper,
                        player,
                        doorRoomStructure(),
                        "door-room",
                        new BlockPos(2, 2, 2)
                    );
                    ConstructionBuildOp interior = placeAt(started.progress(), helper.absolutePos(new BlockPos(3, 3, 3)));
                    for (ConstructionBuildOp op : started.progress().operations()) {
                        if (op.kind() != ConstructionBuildOp.Kind.PLACE) continue;
                        if (op.id() == interior.id()) continue;
                        if (op.target().is(Blocks.OAK_DOOR)) continue;
                        deliverForTest(helper.getLevel(), started.progress(), op);
                    }
                    ConstructionBuildOp door = null;
                    for (ConstructionBuildOp op : started.progress().operations()) {
                        if (op.kind() == ConstructionBuildOp.Kind.PLACE && op.target().is(Blocks.OAK_DOOR)) {
                            door = op;
                            break;
                        }
                    }
                    check(door != null, "door room must include a door");
                    check(
                        ConstructionEnclosure.wouldEnclose(helper.getLevel(), started.progress(), door, null),
                        "the door must wait while the cavity still has unfinished work"
                    );
                    cancelQuietly(player, started.job().jobId());
                } catch (ConstructionBlueprintException exception) {
                    throw new GameTestAssertException("door room setup failed: " + exception.reason());
                }
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 60, batch = "zzz_construction_enclose")
    @EmptyTemplate(value = "11x8x9", floor = true)
    @TestHolder(description = "A delivered hopper minecart leaves one serial closer and both final gaps complete")
    static void minecartTunnelLastGapsSerializeAndComplete(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.moveTo(helper.absoluteVec(new Vec3(1.5D, 5.0D, 4.5D)));
        helper.startSequence()
            .thenExecuteAfter(1, () -> {
                try {
                    player.setNoGravity(true);
                    player.moveTo(helper.absoluteVec(new Vec3(1.5D, 5.0D, 4.5D)));
                    StartedJob started = startStructureJob(
                        helper,
                        player,
                        minecartTunnelStructure(),
                        "minecart-last-gaps",
                        new BlockPos(2, 2, 2)
                    );
                    ConstructionBuildOp firstGap = placeAt(
                        started.progress(),
                        helper.absolutePos(new BlockPos(4, 3, 4))
                    );
                    ConstructionBuildOp secondGap = placeAt(
                        started.progress(),
                        helper.absolutePos(new BlockPos(6, 3, 4))
                    );
                    for (ConstructionBuildOp op : started.progress().operations()) {
                        if (op.kind() != ConstructionBuildOp.Kind.PLACE) continue;
                        if (op.id() == firstGap.id() || op.id() == secondGap.id()) continue;
                        deliverForTest(helper.getLevel(), started.progress(), op);
                    }
                    ConstructionBuildOp minecart = firstKind(started.progress(), ConstructionBuildOp.Kind.ENTITY);
                    check(
                        ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), minecart),
                        "hopper minecart projection must deliver before the tunnel closes"
                    );
                    ConstructionEnclosure.Analysis firstAnalysis = ConstructionEnclosure.analyze(
                        helper.getLevel(),
                        started.progress(),
                        firstGap,
                        null
                    );
                    ConstructionEnclosure.Analysis secondAnalysis = ConstructionEnclosure.analyze(
                        helper.getLevel(),
                        started.progress(),
                        secondGap,
                        null
                    );
                    BlockPos firstApproach = ConstructionJobController.chooseApproach(
                        helper.getLevel(),
                        started.progress(),
                        firstGap
                    );
                    BlockPos secondApproach = ConstructionJobController.chooseApproach(
                        helper.getLevel(),
                        started.progress(),
                        secondGap
                    );
                    ConstructionBuildOp leader = ConstructionJobController.nextAssignable(
                        helper.getLevel(),
                        started.progress()
                    );
                    check(leader != null, "two mutually dependent tunnel gaps left no serial closer; "
                        + enclosureSummary("first", firstGap, firstAnalysis, firstApproach) + "; "
                        + enclosureSummary("second", secondGap, secondAnalysis, secondApproach));
                    check(leader.id() == firstGap.id() || leader.id() == secondGap.id(),
                        "serial closer must be one of the final glass gaps");
                    ConstructionBuildOp follower = leader.id() == firstGap.id() ? secondGap : firstGap;
                    WorkingAllayEntity worker = spawnConstructionAllay(
                        helper,
                        new Vec3(1.5D, 5.0D, 4.5D),
                        player,
                        0
                    );
                    UUID batchWorker = worker.getUUID();
                    started.progress().addLedger(
                        leader.id(),
                        leader.material().copy(),
                        batchWorker
                    );
                    started.progress().addLedger(
                        follower.id(),
                        follower.material().copy(),
                        batchWorker
                    );
                    leader.setStatus(ConstructionBuildOp.Status.LEASED);
                    leader.setLeaseAllay(batchWorker);
                    check(
                        ConstructionEnclosure.wouldEnclose(helper.getLevel(), started.progress(), follower, null),
                        "the carried follower must wait while the serial closer owns the gap"
                    );
                    leader.setStatus(ConstructionBuildOp.Status.PENDING);
                    leader.setLeaseAllay(null);
                    check(
                        started.progress().carriedOperations(batchWorker).size() == 2,
                        "both tunnel gaps must remain indexed as carried by the same allay"
                    );
                    check(
                        ConstructionJobController.canClaimJob(worker, started.progress()),
                        "the carried tunnel-gap allay was not eligible to claim its job"
                    );
                    check(
                        ConstructionAllayToolBehavior.tryClaimCarried(
                            worker,
                            helper.getLevel(),
                            started.job(),
                            started.progress()
                        ),
                        "the carried serial closer was not assignable to its construction allay"
                    );
                    ConstructionBuildOp assigned = started.progress().operation(worker.taskOpId());
                    check(assigned != null, "the carried serial closer assignment is missing");
                    check(
                        ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), assigned, worker),
                        "the serial closer could not deliver"
                    );
                    worker.clearAssignment(false);
                    check(
                        ConstructionAllayToolBehavior.tryClaimCarried(
                            worker,
                            helper.getLevel(),
                            started.job(),
                            started.progress()
                        ),
                        "the final carried tunnel gap stayed blocked after its leader delivered"
                    );
                    ConstructionBuildOp last = started.progress().operation(worker.taskOpId());
                    check(last != null, "the final carried tunnel gap assignment is missing");
                    check(
                        ConstructionJobController.tryDeliver(helper.getLevel(), started.progress(), last, worker),
                        "the final tunnel gap could not deliver"
                    );
                    check(started.progress().allPlaceResolved(),
                        "hopper minecart tunnel remained incomplete after both final gaps delivered");
                    cancelQuietly(player, started.job().jobId());
                } catch (ConstructionBlueprintException exception) {
                    throw new GameTestAssertException("minecart last-gap setup failed: " + exception.reason());
                }
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 60, batch = "zzz_construction_enclose")
    @EmptyTemplate(value = "11x11x7", floor = true)
    @TestHolder(description = "The four final quartz-machine gaps serialize and resolve instead of deadlocking")
    static void quartzMachineFourGapsSerializeAndComplete(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ConstructionJobProgress progress = quartzMachineClosureProgress(helper);
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        List<ConstructionBuildOp> gaps = List.of(
            placeAt(progress, anchor.offset(3, 0, 1)),
            placeAt(progress, anchor.offset(2, 0, 1)),
            placeAt(progress, anchor.offset(1, 0, 1)),
            placeAt(progress, anchor.offset(1, 1, 1))
        );
        gaps.get(0).setOrder(7);
        gaps.get(1).setOrder(8);
        gaps.get(2).setOrder(9);
        gaps.get(3).setOrder(27);
        try {
            for (ConstructionBuildOp op : progress.operations()) {
                if (!gaps.contains(op)) {
                    deliverForTest(level, progress, op);
                }
            }
            for (int remaining = gaps.size(); remaining > 0; remaining--) {
                List<ConstructionBuildOp> assignable = new ArrayList<>();
                for (ConstructionBuildOp gap : gaps) {
                    if (gap.status() == ConstructionBuildOp.Status.DELIVERED) continue;
                    if (ConstructionJobController.chooseApproach(level, progress, gap) != null) {
                        assignable.add(gap);
                    }
                }
                check(assignable.size() == 1,
                    "quartz-machine closure expected one serial leader with " + remaining
                        + " gaps remaining but found " + assignable.size() + "; "
                        + enclosureSummaries(level, progress, gaps));
                ConstructionBuildOp leader = ConstructionJobController.nextAssignable(level, progress);
                check(leader == assignable.getFirst(), "assignment scan did not select the unique serial leader");
                check(
                    ConstructionJobController.tryDeliver(level, progress, leader),
                    "quartz-machine serial leader could not deliver: " + leader.id()
                );
            }
            check(progress.allPlaceResolved(), "quartz-machine closure remained incomplete after all four gaps");
        } finally {
            ConstructionProjectionIndex.clearJob(level, progress.jobId());
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_enclose")
    @EmptyTemplate(value = "9x9x9", floor = true)
    @TestHolder(description = "A solid five by five by five cube fills its final enclosed tunnel from inside to outside")
    static void solidCubeFinalTunnelCompletes(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ConstructionJobProgress progress = new ConstructionJobProgress(UUID.randomUUID());
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockState cobblestone = Blocks.COBBLESTONE.defaultBlockState();
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    progress.addOperation(
                        anchor.offset(x, y, z),
                        cobblestone,
                        new ItemStack(Blocks.COBBLESTONE),
                        ConstructionBuildOp.Kind.PLACE,
                        ConstructionBuildOp.Status.PENDING
                    );
                }
            }
        }
        ConstructionAssembler.assignBuildOrder(level, progress);
        List<ConstructionBuildOp> tunnel = List.of(
            placeAt(progress, anchor.offset(1, 2, 2)),
            placeAt(progress, anchor.offset(2, 2, 2)),
            placeAt(progress, anchor.offset(3, 2, 2)),
            placeAt(progress, anchor.offset(4, 2, 2))
        );
        WorkingAllayEntity worker = spawnConstructionAllay(helper, new Vec3(1.5D, 2.0D, 1.5D), null, 0);
        try {
            for (ConstructionBuildOp op : progress.operations()) {
                if (!tunnel.contains(op)) {
                    deliverForTest(level, progress, op);
                }
            }
            for (ConstructionBuildOp expected : tunnel) {
                ConstructionBuildOp assigned = ConstructionJobController.nextAssignable(level, progress, worker);
                check(assigned == expected,
                    "solid-cube tunnel must choose its innermost safe gap before sealing the next one");
                BlockPos approach = assigned.approach().orElse(null);
                check(approach != null, "solid-cube tunnel gap has no safe approach");
                Vec3 point = ConstructionWorkerSpace.navigationPoint(approach);
                worker.setPos(point.x, point.y, point.z);
                check(
                    ConstructionJobController.tryDeliver(level, progress, assigned, worker),
                    "solid-cube tunnel gap could not deliver without trapping its allay: " + assigned.pos()
                );
            }
            check(progress.allPlaceResolved(), "solid five by five by five cube remained incomplete after its tunnel closed");
        } finally {
            worker.discard();
            ConstructionProjectionIndex.clearJob(level, progress.jobId());
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_construction_enclose")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A future build cell reserved as an approach cannot be filled by another worker")
    static void futureBuildCellApproachReservationBlocksConcurrentPlacement(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ConstructionJobProgress progress = new ConstructionJobProgress(UUID.randomUUID());
        BlockPos leaderPos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos followerPos = leaderPos.east();
        progress.addOperation(
            leaderPos,
            Blocks.COBBLESTONE.defaultBlockState(),
            new ItemStack(Blocks.COBBLESTONE),
            ConstructionBuildOp.Kind.PLACE,
            ConstructionBuildOp.Status.PENDING
        );
        progress.addOperation(
            followerPos,
            Blocks.COBBLESTONE.defaultBlockState(),
            new ItemStack(Blocks.COBBLESTONE),
            ConstructionBuildOp.Kind.PLACE,
            ConstructionBuildOp.Status.PENDING
        );
        ConstructionBuildOp leader = placeAt(progress, leaderPos);
        ConstructionBuildOp follower = placeAt(progress, followerPos);
        leader.setOrder(0);
        follower.setOrder(1);
        check(
            ConstructionJobController.chooseApproach(level, progress, follower) != null,
            "future build cell follower must otherwise be assignable"
        );

        UUID leaderWorker = UUID.randomUUID();
        leader.setStatus(ConstructionBuildOp.Status.LEASED);
        leader.setLeaseAllay(leaderWorker);
        leader.setApproach(followerPos);
        ConstructionTraffic.reserveApproach(level, leaderWorker, followerPos);
        try {
            check(
                ConstructionJobController.chooseApproach(level, progress, follower) != null,
                "approach reservation unexpectedly removed every follower approach"
            );
            check(
                ConstructionJobController.nextAssignable(level, progress) == null,
                "another worker could fill a future build cell while the leader still occupied it"
            );
            check(
                follower.status() == ConstructionBuildOp.Status.WAITING_OCCUPIED,
                "reserved future build cell did not report occupied"
            );
        } finally {
            ConstructionTraffic.release(level, leaderWorker);
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_enclose")
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "A slab that seals a VoxelShape pocket is postponed")
    static void slabPocketIsPostponed(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence()
            .thenExecuteAfter(1, () -> {
                try {
                    StartedJob started = startStructureJob(
                        helper,
                        player,
                        slabPocketStructure(),
                        "slab-pocket",
                        new BlockPos(2, 2, 2)
                    );
                    ConstructionBuildOp slab = null;
                    ConstructionBuildOp interior = placeAt(
                        started.progress(),
                        helper.absolutePos(new BlockPos(3, 3, 3))
                    );
                    for (ConstructionBuildOp op : started.progress().operations()) {
                        if (op.target().getBlock() instanceof SlabBlock) {
                            slab = op;
                            continue;
                        }
                        if (op.id() == interior.id()) continue;
                        if (op.kind() == ConstructionBuildOp.Kind.PLACE
                            || op.kind() == ConstructionBuildOp.Kind.ATTACHED) {
                            deliverForTest(helper.getLevel(), started.progress(), op);
                        }
                    }
                    check(slab != null, "slab pocket job must include a slab");
                    check(
                        ConstructionEnclosure.wouldEnclose(helper.getLevel(), started.progress(), slab, null),
                        "a slab that seals a pocket must be postponed"
                    );
                    cancelQuietly(player, started.job().jobId());
                } catch (ConstructionBlueprintException exception) {
                    throw new GameTestAssertException("slab pocket setup failed: " + exception.reason());
                }
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_construction_enclose")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A job accepts at most 64 participating allays")
    static void participantCapIsSixtyFour(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence()
            .thenExecuteAfter(1, () -> {
                try {
                    StartedJob started = startCobbleJob(helper, player, 1, new BlockPos(2, 2, 2));
                    check(ConstructionJobController.MAX_PARTICIPANTS == 64, "participant cap must stay 64");
                    check(
                        ConstructionJobController.canAcceptMoreParticipants(
                            helper.getLevel(),
                            started.job(),
                            started.progress()
                        ),
                        "an empty job must still accept participants"
                    );
                    check(
                        ConstructionJobController.participantCount(
                            helper.getLevel(),
                            started.job(),
                            started.progress()
                        ) == 0,
                        "a job with no bound allays must count 0 participants"
                    );
                    cancelQuietly(player, started.job().jobId());
                } catch (ConstructionBlueprintException exception) {
                    throw new GameTestAssertException("participant cap setup failed: " + exception.reason());
                }
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_flight")
    @EmptyTemplate(value = "7x5x5", floor = true)
    @TestHolder(description = "A blocked goal is routed around a wall instead of charging through it")
    static void flightGoesAroundWall(ExtendedGameTestHelper helper) {
        WorkingAllayEntity worker = spawnConstructionAllay(helper, new Vec3(2.78D, 2.0D, 1.78D), null, 0);
        BlockPos wall = helper.absolutePos(new BlockPos(3, 2, 2));
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 3, 2), Blocks.STONE);
        Vec3 goal = helper.absoluteVec(new Vec3(5.5D, 3.8D, 3.5D));
        List<Vec3> path = AllayFlightPlanner.plan(worker, goal);
        check(!path.isEmpty(), "blocked flight must return a detour");
        Vec3 previous = worker.position();
        for (Vec3 point : path) {
            check(!BlockPos.containing(point).equals(wall), "path must not step through the wall");
            check(
                AllayFlightPlanner.isClear(worker, previous, point),
                "every detour segment must be reachable from the preceding real position"
            );
            previous = point;
        }
        check(
            !AllayFlightPlanner.isClear(worker, worker.position(), goal),
            "the straight line through the wall must stay blocked"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_flight")
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "An allay path snapshot respects a molding chamber's block-entity-driven collision")
    static void flightGoesAroundPrintingMoldingChamber(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Direction front = Direction.WEST;
        BlockPos relativeController = new BlockPos(3, 2, 4);
        BlockPos controller = helper.absolutePos(relativeController);
        BlockState controllerState = PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.get().defaultBlockState()
            .setValue(PlasticMoldingChamberBlock.FACING, front);
        check(
            PlasticMoldingChamberStructure.placeAtomically(level, controller, controllerState),
            "printing molding chamber could not be placed"
        );
        WorkingAllayEntity worker = spawnConstructionAllay(helper, new Vec3(5.5D, 2.0D, 1.5D), null, 0);
        Vec3 goal = helper.absoluteVec(new Vec3(5.5D, 2.0D, 7.5D));
        AABB region = PlasticMoldingChamberStructure.regionBounds(controller, front);
        Vec3 regionCenter = region.getCenter();
        Vec3 regionBottomCenter = new Vec3(regionCenter.x, region.minY + 0.01D, regionCenter.z);
        check(
            level.noBlockCollision(worker, ConstructionWorkerSpace.boxAt(regionBottomCenter)),
            "empty molding region unexpectedly exposed collision"
        );
        check(
            AllayFlightPlanner.isClear(worker, worker.position(), goal),
            "empty molding chamber unexpectedly blocked the straight route"
        );

        helper.setBlock(
            relativeController.above(),
            PlasticraftBlocks.PLASTIC_3D_PRINTING_COMPONENT.get().defaultBlockState()
        );
        check(
            !level.noBlockCollision(worker, ConstructionWorkerSpace.boxAt(regionBottomCenter)),
            "printing molding region did not expose its live collision"
        );
        check(
            !AllayFlightPlanner.isClear(worker, worker.position(), goal),
            "printing molding chamber did not block the straight route"
        );

        List<Vec3> path = AllayFlightPlanner.plan(worker, goal);
        check(path.size() > 1, "printing molding chamber path did not contain a detour");
        Vec3 previous = worker.position();
        for (Vec3 point : path) {
            check(
                AllayFlightPlanner.isClear(worker, previous, point),
                "path snapshot routed through the printing molding chamber between " + previous + " and " + point
            );
            previous = point;
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_flight")
    @EmptyTemplate(value = "7x5x5", floor = true)
    @TestHolder(description = "A single edge collision does not discard an active escape path")
    static void flightKeepsEscapePathAfterEdgeCollision(ExtendedGameTestHelper helper) {
        WorkingAllayEntity worker = spawnConstructionAllay(helper, new Vec3(1.5D, 2.0D, 2.5D), null, 0);
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE);
        Vec3 goal = helper.absoluteVec(new Vec3(5.5D, 2.0D, 2.5D));
        List<Vec3> escape = List.of(
            helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)),
            helper.absoluteVec(new Vec3(5.5D, 2.0D, 1.5D)),
            goal
        );
        worker.navigator().setPath(escape);
        worker.horizontalCollision = true;

        AllayWorkMotions.flyTo(worker, goal, AllayPathPriority.DELIVER);

        check(worker.navigator().hasPath(), "one previous edge collision discarded the active escape path");
        check(worker.navigator().follow(worker), "the retained escape path could not issue movement");
        check(worker.getDeltaMovement().lengthSqr() > 1.0E-4D, "the allay did not start leaving the block edge");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 80, batch = "zzz_construction_flight")
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "A worker buried by a block escapes to free space instead of suffocating in place")
    static void buriedWorkerEscapesInsteadOfSuffocating(ExtendedGameTestHelper helper) {
        WorkingAllayEntity worker = spawnConstructionAllay(helper, new Vec3(2.5D, 3.2D, 2.5D), null, 0);
        // 提交阶段把投影换成真实方块时悦灵可能正停在这一格里:碰撞钳制会吃掉全部位移,
        // 寻路却以为自己一直在走,于是悦灵原地窒息;这里锁住"被包住就必须自己挪出去"
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE);
        BlockPos buried = helper.absolutePos(new BlockPos(2, 3, 2));
        check(
            !helper.getLevel().noBlockCollision(worker, worker.getBoundingBox()),
            "测试前置必须让悦灵确实被方块包住，否则本回归失去意义"
        );
        helper.startSequence().thenExecuteAfter(40, () -> {
            check(worker.isAlive(), "被包住的悦灵必须先脱困，而不是一直窒息到死");
            check(
                helper.getLevel().noBlockCollision(worker, worker.getBoundingBox()),
                "被方块包住的悦灵必须自行挪到空位"
            );
            check(
                !new AABB(buried).intersects(worker.getBoundingBox()),
                "脱困后不得还留在实心格里"
            );
        }).thenSucceed();
    }


    static void pendingFlightKeepsStallWatchdog(ExtendedGameTestHelper helper) {
        WorkingAllayEntity worker = spawnConstructionAllay(helper, new Vec3(2.5D, 2.0D, 2.5D), null, 0);
        worker.resetStuck();
        worker.ensureFlightTask(
            helper.absoluteVec(new Vec3(4.5D, 2.0D, 2.5D)),
            AllayPathPriority.DELIVER
        );
        Vec3 first = worker.position();
        Vec3 second = first.add(0.25D, 0.0D, 0.0D);
        for (int tick = 0; tick < 80; tick++) {
            worker.setPos((tick & 1) == 0 ? second : first);
            worker.noteProgress();
        }
        check(worker.hasPendingFlightTask(), "test setup did not retain a pending flight task");
        check(worker.isMotionStuck(), "short backtracking erased the worker stall watchdog");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_construction_flight")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A downward segmented flight enters the next section instead of repeating its boundary waypoint")
    static void downwardSegmentedFlightAdvancesPastBoundary(ExtendedGameTestHelper helper) {
        WorkingAllayEntity worker = spawnConstructionAllay(helper, new Vec3(2.5D, 2.0D, 2.5D), null, 0);
        Vec3 goal = ConstructionWorkerSpace.navigationPoint(helper.absolutePos(new BlockPos(2, 2, 2)));
        Vec3 start = ConstructionWorkerSpace.navigationPoint(helper.absolutePos(new BlockPos(2, 50, 2)));
        worker.moveTo(start.x, start.y, start.z);

        Vec3 first = AllayPathSnapshot.nextLocalGoal(worker, start, goal);
        worker.moveTo(first.x, first.y, first.z);
        Vec3 second = AllayPathSnapshot.nextLocalGoal(worker, first, goal);

        check(first.distanceTo(goal) < start.distanceTo(goal), "first downward segment did not approach the goal");
        check(second.distanceTo(goal) < first.distanceTo(goal), "downward flight repeated the same section boundary");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_flight")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "An allay below a grid plane lifts before crossing the neighboring block edge")
    static void flightLiftsBeforeLeavingLowGridPlane(ExtendedGameTestHelper helper) {
        WorkingAllayEntity worker = spawnConstructionAllay(helper, new Vec3(1.52D, 1.998D, 2.5D), null, 0);
        Vec3 first = ConstructionWorkerSpace.navigationPoint(helper.absolutePos(new BlockPos(1, 2, 2)));
        Vec3 goal = ConstructionWorkerSpace.navigationPoint(helper.absolutePos(new BlockPos(3, 2, 2)));
        worker.navigator().setPath(List.of(first, goal));

        check(worker.navigator().follow(worker), "low-grid flight did not issue its lift movement");
        Vec3 initialMotion = worker.getDeltaMovement();
        check(initialMotion.y > 0.005D, "low-grid flight skipped the lift waypoint");
        check(initialMotion.horizontalDistance() < 0.05D, "low-grid flight moved sideways before clearing the block edge");
        worker.move(MoverType.SELF, initialMotion);
        for (int tick = 0; tick < 20 && worker.navigator().hasPath(); tick++) {
            worker.navigator().follow(worker);
            worker.move(MoverType.SELF, worker.getDeltaMovement());
        }
        check(worker.position().distanceTo(goal) < 0.4D, "allay oscillated instead of crossing the block edge");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_construction_flight")
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "A navigation point centers the worker box in its cell with margin above and below")
    static void navigationPointCentersWorkerBoxInCell(ExtendedGameTestHelper helper) {
        BlockPos cell = helper.absolutePos(new BlockPos(2, 2, 2));
        AABB box = ConstructionWorkerSpace.boxAt(ConstructionWorkerSpace.navigationPoint(cell));
        double below = box.minY - cell.getY();
        double above = cell.getY() + 1.0D - box.maxY;
        check(Math.abs(below - above) < 1.0E-9D, "落脚点必须在格内竖直居中");
        check(below > 0.1D, "落脚点距格底的余量必须足够吸收到达误差，紧贴格底会让包围盒探进正下方的目标格");
        check(new AABB(cell).contains(box.getCenter()), "落脚点包围盒必须留在本格内");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_flight")
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "An arrival inside the final-waypoint tolerance settles exactly onto the waypoint")
    static void flightSettlesExactlyOnFinalWaypoint(ExtendedGameTestHelper helper) {
        BlockPos target = helper.absolutePos(new BlockPos(2, 2, 2));
        Vec3 approach = ConstructionWorkerSpace.navigationPoint(target.above());
        WorkingAllayEntity worker = spawnConstructionAllay(helper, new Vec3(2.5D, 3.0D, 2.5D), null, 0);
        // 从下方抬升到接近位时,终点判定容差之内的欠冲会让包围盒探进正下方的目标格,
        // 交付判定据此认为工人占着目标格,而这点位移又小到无法执行,于是永久卡住
        Vec3 undershoot = approach.subtract(0.0D, 0.22D, 0.0D);
        worker.moveTo(undershoot.x, undershoot.y, undershoot.z);
        check(undershoot.distanceToSqr(approach) < 0.09D, "本例必须落在终点判定容差之内");
        check(worker.getBoundingBox().intersects(new AABB(target)), "欠冲位置必须确实探进目标格，否则本回归失去意义");

        worker.navigator().setPath(List.of(approach));
        check(!worker.navigator().follow(worker), "抵达终点这一刻不应再发出移动指令");
        check(worker.position().distanceToSqr(approach) < 1.0E-9D, "抵达终点必须精确落位");
        check(!worker.getBoundingBox().intersects(new AABB(target)), "落位后包围盒不得再探进目标格");
        check(worker.getDeltaMovement().lengthSqr() < 1.0E-12D, "落位后必须停稳");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_flight")
    @EmptyTemplate(value = "7x5x7", floor = true)
    @TestHolder(description = "An allay follows a reserved L-shaped one-block tunnel through empty-collision blocks")
    static void flightPassesReservedRailTunnel(ExtendedGameTestHelper helper) {
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            }
        }
        List<BlockPos> relativePassage = List.of(
            new BlockPos(1, 2, 1),
            new BlockPos(2, 2, 1),
            new BlockPos(3, 2, 1),
            new BlockPos(3, 2, 2),
            new BlockPos(3, 2, 3),
            new BlockPos(4, 2, 3),
            new BlockPos(5, 2, 3)
        );
        for (BlockPos pos : relativePassage) {
            helper.setBlock(pos.below(), Blocks.STONE);
            helper.setBlock(pos, Blocks.RAIL);
        }
        BlockPos gate = new BlockPos(3, 2, 2);
        helper.setBlock(
            gate,
            Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(BlockStateProperties.OPEN, true)
        );
        BlockPos grass = new BlockPos(3, 2, 3);
        helper.setBlock(grass.below(), Blocks.DIRT);
        helper.setBlock(grass, Blocks.SHORT_GRASS);

        WorkingAllayEntity worker = spawnConstructionAllay(helper, new Vec3(1.5D, 2.0D, 1.5D), null, 0);
        Vec3 goal = helper.absoluteVec(new Vec3(5.5D, 2.0D, 3.5D));
        List<BlockPos> passage = relativePassage.stream().map(helper::absolutePos).toList();
        UUID reservationOwner = UUID.randomUUID();
        BlockPos reserved = helper.absolutePos(gate);
        ConstructionTraffic.reserveApproach(helper.getLevel(), reservationOwner, reserved);
        try {
            check(
                ConstructionTraffic.isReserved(helper.getLevel(), reserved, worker.getUUID()),
                "the approach reservation must still exclude duplicate endpoint claims"
            );
            List<Vec3> path = AllayFlightPlanner.plan(worker, goal);
            check(!path.isEmpty(), "empty-collision tunnel returned no path");
            Vec3 previous = worker.position();
            for (Vec3 point : path) {
                check(
                    passage.contains(BlockPos.containing(point)),
                    "traffic reservation forced the path out of the tunnel at " + point
                );
                check(
                    AllayFlightPlanner.isClear(worker, previous, point),
                    "rail, grass or open fence gate blocked a path segment"
                );
                previous = point;
            }
            worker.navigator().setPath(path);
            for (int tick = 0; tick < 40 && worker.navigator().hasPath(); tick++) {
                worker.navigator().follow(worker);
                worker.move(MoverType.SELF, worker.getDeltaMovement());
            }
            check(worker.position().distanceTo(goal) < 0.4D, "allay did not leave the one-block tunnel");
        } finally {
            ConstructionTraffic.release(helper.getLevel(), reservationOwner);
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 160, batch = "zzz_construction_flight")
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "An allay replans around a wall that appears on its active route and still docks")
    static void flightReplansAfterRouteChanges(ExtendedGameTestHelper helper) {
        AllayLoungeBlockEntity lounge = placeLounge(helper, new BlockPos(7, 2, 4));
        WorkingAllayEntity worker = AllayGameTests.spawnHattedForOwner(
            helper,
            new Vec3(1.5D, 2.0D, 4.5D),
            AllayGameTests.TEST_OWNER
        );
        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.CRAB_CLAW.get()));
        BlockPos wallBase = helper.absolutePos(new BlockPos(4, 2, 4));
        AABB wallBounds = new AABB(wallBase).expandTowards(0.0D, 2.0D, 0.0D);
        boolean[] wallPlaced = {false};
        helper.onEachTick(() -> {
            if (!wallPlaced[0] || worker.isRemoved()) return;
            check(
                !worker.getBoundingBox().intersects(wallBounds),
                "replanning allay entered the newly placed wall at " + worker.position()
            );
        });
        helper.startSequence()
            .thenExecute(() -> check(worker.startDockingTo(lounge.getBlockPos()), "allay rejected the docking route"))
            .thenIdle(6)
            .thenExecute(() -> {
                check(worker.getX() < wallBase.getX(), "allay passed the future wall before it was placed");
                helper.setBlock(new BlockPos(4, 2, 4), Blocks.STONE);
                helper.setBlock(new BlockPos(4, 3, 4), Blocks.STONE);
                helper.setBlock(new BlockPos(4, 4, 4), Blocks.STONE);
                wallPlaced[0] = true;
            })
            .thenWaitUntil(() -> check(
                worker.isRemoved() && lounge.hosted().size() == 1,
                "allay did not replan around the new wall and dock; pos=" + worker.position()
                    + ", flight=" + worker.flightState()
                    + ", path=" + worker.navigator().hasPath()
            ))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_flight")
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "An allay embedded in a delivered projection snaps out before flying")
    static void flightSnapsOutOfProjection(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        UUID jobId = UUID.randomUUID();
        check(
            ConstructionProjectionIndex.tryDeliver(level, jobId, pos, Blocks.STONE.defaultBlockState(), Map.of()),
            "projection must deliver"
        );
        WorkingAllayEntity worker = spawnConstructionAllay(helper, new Vec3(2.5D, 2.0D, 2.5D), null, 0);
        Vec3 snapped = AllayFlightPlanner.snapToFree(worker, worker.position());
        AABB box = worker.getBoundingBox().move(snapped.subtract(worker.position()));
        check(level.noBlockCollision(worker, box), "snapToFree must leave the delivered projection");
        check(snapped.distanceToSqr(worker.position()) > 0.01D, "embedded allay must move out");
        ConstructionProjectionIndex.clearJob(level, jobId);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_flight")
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "A short hop still refuses to fly through a thin wall")
    static void shortFlightDoesNotPierceThinWall(ExtendedGameTestHelper helper) {
        WorkingAllayEntity worker = spawnConstructionAllay(helper, new Vec3(1.5D, 2.0D, 2.5D), null, 0);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
        Vec3 goal = helper.absoluteVec(new Vec3(3.5D, 2.0D, 2.5D));
        check(worker.position().distanceTo(goal) <= 3.0D, "this case must stay a short hop");
        List<Vec3> path = AllayFlightPlanner.plan(worker, goal);
        check(!path.isEmpty(), "short blocked flight must detour");
        BlockPos wall = helper.absolutePos(new BlockPos(2, 2, 2));
        for (Vec3 point : path) {
            check(!BlockPos.containing(point).equals(wall), "short path must not pierce the thin wall");
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_construction_flight")
    @EmptyTemplate(value = "7x5x5", floor = true)
    @TestHolder(description = "A sub-cell hover drift under a ceiling still plans the short same-layer hop")
    static void driftedHoverStillPlansUnderCeiling(ExtendedGameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 3, 2), Blocks.STONE);
        // 悦灵停靠常有亚格漂移,包围盒顶面探进上一格;那一格正好是天花板时,
        // 每一步扫掠都会连带把它算进去并整体否决,明明只差两格也规划不出路径
        WorkingAllayEntity worker = spawnConstructionAllay(helper, new Vec3(4.3987D, 2.4013D, 2.5D), null, 0);
        Vec3 goal = ConstructionWorkerSpace.navigationPoint(helper.absolutePos(new BlockPos(2, 2, 2)));
        check(
            worker.getBoundingBox().intersects(new AABB(helper.absolutePos(new BlockPos(4, 3, 2)))),
            "漂移姿态必须确实探进上一格，否则本回归失去意义"
        );
        check(
            !AllayFlightPlanner.isClear(worker, worker.position(), goal),
            "漂移姿态的直线扫掠必须被天花板否决"
        );
        Vec3 aligned = AllayFlightPlanner.alignToGrid(worker, worker.position());
        check(
            aligned.distanceToSqr(ConstructionWorkerSpace.navigationPoint(worker.blockPosition())) < 1.0E-9D,
            "规划起点必须拉回所在格的落脚点"
        );
        List<Vec3> path = AllayFlightPlanner.plan(worker, goal);
        check(!path.isEmpty(), "对齐起点后同层两格的短跳必须能规划出路径");
        Vec3 previous = worker.position();
        for (Vec3 point : path) {
            check(
                AllayFlightPlanner.isClear(worker, previous, point),
                "路径分段 " + previous + " -> " + point + " 撞上了天花板"
            );
            previous = point;
        }
        check(path.get(path.size() - 1).distanceToSqr(goal) < 1.0E-9D, "路径终点必须精确落在目标落脚点上");
        helper.succeed();
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

    private static ItemStack deployDisk(
        ExtendedGameTestHelper helper,
        GameTestPlayer player,
        int count,
        BlockPos relativeAnchor
    ) throws ConstructionBlueprintException {
        return deployStructureDisk(helper, player, cobbleStructure(count), "cobble-wall-" + count, relativeAnchor);
    }

    private static ItemStack deployStructureDisk(
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
        ConstructionBlueprintService.deploy(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(relativeAnchor),
            Rotation.NONE,
            Mirror.NONE
        );
        return player.getMainHandItem().copy();
    }

    private static StartedJob claimCobbleAtLounge(
        ExtendedGameTestHelper helper,
        GameTestPlayer player,
        int count
    ) throws ConstructionBlueprintException {
        return claimCobbleAtLounge(helper, player, count, placeLounge(helper, new BlockPos(2, 2, 2)));
    }

    private static StartedJob claimCobbleAtLounge(
        ExtendedGameTestHelper helper,
        GameTestPlayer player,
        int count,
        AllayLoungeBlockEntity lounge
    ) throws ConstructionBlueprintException {
        return claimStructureAtLounge(
            helper,
            player,
            cobbleStructure(count),
            "cobble-wall-" + count,
            new BlockPos(4, 2, 4),
            lounge
        );
    }

    private static StartedJob claimStructureAtLounge(
        ExtendedGameTestHelper helper,
        GameTestPlayer player,
        CompoundTag structure,
        String name,
        BlockPos relativeAnchor,
        AllayLoungeBlockEntity lounge
    ) throws ConstructionBlueprintException {
        lounge.setOwner(player.getUUID());
        ItemStack disk = deployStructureDisk(helper, player, structure, name, relativeAnchor);
        UUID jobId = ConstructionBlueprintData.get(disk).flatMap(ConstructionBlueprintData::jobId).orElse(null);
        check(jobId != null, "deployed " + name + " lounge disk must carry a job id");
        lounge.items().setStackInSlot(AllayLoungeBlockEntity.DISK_SLOT, disk);
        MinecraftServer server = helper.getLevel().getServer();
        ConstructionJob claimed = ConstructionJobIndex.get(server).job(jobId);
        ConstructionJobProgress claimedProgress = ConstructionJobStore.get(server).get(jobId);
        check(claimed != null && !claimed.isActive(), "inserting the " + name + " disk must claim without starting");
        check(
            claimedProgress != null && lounge.getBlockPos().equals(claimedProgress.coordinatorLounge()),
            "inserting the " + name + " disk must record this lounge"
        );
        ConstructionBlueprintService.start(server, jobId);
        ConstructionJob started = ConstructionJobIndex.get(server).job(jobId);
        check(
            started != null && started.isActive(),
            "right-click start after the " + name + " claim must activate the job"
        );
        ConstructionJobProgress progress = ConstructionJobStore.get(server).get(jobId);
        check(progress != null && progress.planned(), "started claimed " + name + " job must be planned");
        return new StartedJob(started, progress);
    }

    private static AllayLoungeBlockEntity placeLounge(ExtendedGameTestHelper helper, BlockPos relativePos) {
        helper.setBlock(relativePos, PlasticraftBlocks.ALLAY_LOUNGE.get());
        if (!(helper.getBlockEntity(relativePos) instanceof AllayLoungeBlockEntity lounge)) {
            throw new GameTestAssertException("allay lounge block entity is missing");
        }
        lounge.setOwner(AllayGameTests.TEST_OWNER);
        return lounge;
    }

    private static AllayLoungeBlockEntity placeLoungeWithCreativeCrate(
        ExtendedGameTestHelper helper,
        BlockPos relativePos
    ) {
        AllayLoungeBlockEntity lounge = placeLounge(helper, relativePos);
        helper.setBlock(relativePos.below(), ModBlocks.CREATIVE_CRATE.get());
        return lounge;
    }

    private static AllayLoungeBlockEntity placeLoungeWithChest(
        ExtendedGameTestHelper helper,
        BlockPos relativePos,
        ItemStack contents
    ) {
        AllayLoungeBlockEntity lounge = placeLounge(helper, relativePos);
        helper.setBlock(relativePos.below(), Blocks.CHEST);
        if (!(helper.getBlockEntity(relativePos.below()) instanceof Container container)) {
            throw new GameTestAssertException("chest below the lounge is missing");
        }
        container.setItem(0, contents);
        return lounge;
    }

    private static int countInChest(ExtendedGameTestHelper helper, BlockPos absolutePos, Item item) {
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(absolutePos);
        if (!(blockEntity instanceof Container container)) {
            return 0;
        }
        return countContainer(container, item);
    }

    private static void fillChest(ExtendedGameTestHelper helper, BlockPos absolutePos, Item item) {
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(absolutePos);
        if (!(blockEntity instanceof Container container)) {
            throw new GameTestAssertException("chest to fill is missing");
        }
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            container.setItem(slot, new ItemStack(item, 64));
        }
    }

    private static AllayWorkRecord constructionRecord(UUID owner) {
        return new AllayWorkRecord(
            UUID.randomUUID(),
            AllayDefaultHardHat.stack(),
            new ItemStack(ModItems.CRAB_CLAW.get()),
            Optional.of(owner),
            AllayShortageStrategy.PAUSE,
            List.of(),
            Optional.empty(),
            ItemStack.EMPTY,
            Optional.empty()
        );
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

    private static CompoundTag denseCobbleStructure(int count) {
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        for (int y = 0; y < 3 && positions.size() < count; y++) {
            for (int z = 0; z < 5 && positions.size() < count; z++) {
                for (int x = 0; x < 5 && positions.size() < count; x++) {
                    positions.add(new BlockPos(x, y, z));
                    states.add(Blocks.COBBLESTONE.defaultBlockState());
                }
            }
        }
        return sizedStructure(5, 3, 5, positions, states, List.of());
    }

    /**
     * 沿 X 排列的声明格:锚点格声明空气,东侧一格声明毛石。
     * {@code trailingBlank} 再追加一格空气,用于放水验证封堵填充块的拆除。
     */
    private static CompoundTag blankCellStructure(boolean trailingBlank) {
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        positions.add(BlockPos.ZERO);
        states.add(Blocks.AIR.defaultBlockState());
        positions.add(new BlockPos(1, 0, 0));
        states.add(Blocks.COBBLESTONE.defaultBlockState());
        if (trailingBlank) {
            positions.add(new BlockPos(2, 0, 0));
            states.add(Blocks.AIR.defaultBlockState());
        }
        return multiBlockStructure(positions, states);
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

    /**
     * 带字告示牌的规范快照:正面写第一行并可选染红/发光,反面固定写一行,再按需打蜡。
     * 走 {@link SignText#DIRECT_CODEC} 编码而不是手写 {@code messages} 列表,
     * 免得测试自己去猜 FLAT_CODEC 的落盘形状,与原版 {@code saveAdditional} 保持同一条路径。
     */
    private static CompoundTag signStructure(
        HolderLookup.Provider registries,
        Component frontLine,
        boolean colored,
        boolean glowing,
        boolean waxed
    ) {
        SignText front = new SignText().setMessage(0, frontLine);
        if (colored) front = front.setColor(DyeColor.RED);
        if (glowing) front = front.setHasGlowingText(true);
        SignText back = new SignText().setMessage(0, Component.literal("back"));
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:sign");
        nbt.put("front_text", encodeSignText(registries, front));
        nbt.put("back_text", encodeSignText(registries, back));
        if (waxed) nbt.putBoolean("is_waxed", true);
        return singleBlockStructure(Blocks.OAK_SIGN.defaultBlockState(), nbt);
    }

    private static CompoundTag encodeSignText(HolderLookup.Provider registries, SignText text) {
        Tag encoded = SignText.DIRECT_CODEC
            .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), text)
            .result()
            .orElse(null);
        if (!(encoded instanceof CompoundTag compound)) {
            throw new GameTestAssertException("a sign face must encode to a compound");
        }
        return compound;
    }

    /** 按加工面向和正反面取那条 DECORATE 子操作;没排这条加工时返回 null。 */
    private static ConstructionBuildOp signDecoration(
        ConstructionJobProgress progress,
        ConstructionBuildOp parent,
        SignDecorationAdapter.Aspect aspect,
        boolean front
    ) {
        int slot = SignDecorationAdapter.slotOf(aspect, front);
        for (ConstructionBuildOp child : progress.childrenOf(parent)) {
            if (child.kind() == ConstructionBuildOp.Kind.DECORATE && child.slot() == slot) return child;
        }
        return null;
    }

    /** 交付一条告示牌加工;书写没有材料,取料会因为 needsMaterial 为假直接放行。 */
    private static void deliverDecoration(
        ExtendedGameTestHelper helper,
        GameTestPlayer player,
        ConstructionJobProgress progress,
        ConstructionBuildOp decoration
    ) {
        check(decoration != null, "the decoration operation to deliver must exist");
        check(
            ConstructionJobController.extractMaterial(player, progress, decoration, UUID.randomUUID()),
            "extracting sign decoration slot " + decoration.slot() + " must succeed"
        );
        check(
            ConstructionJobController.tryDeliver(helper.getLevel(), progress, decoration),
            "delivering sign decoration slot " + decoration.slot() + " must succeed"
        );
    }

    private static CompoundTag doubleChestStructure(HolderLookup.Provider registries) {
        CompoundTag item = (CompoundTag) new ItemStack(Items.DIAMOND, 3).save(registries);
        item.putByte("Slot", (byte) 0);
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:chest");
        ListTag items = new ListTag();
        items.add(item);
        nbt.put("Items", items);
        BlockState right = Blocks.CHEST.defaultBlockState()
            .setValue(ChestBlock.FACING, Direction.SOUTH)
            .setValue(ChestBlock.TYPE, ChestType.RIGHT);
        BlockState left = Blocks.CHEST.defaultBlockState()
            .setValue(ChestBlock.FACING, Direction.SOUTH)
            .setValue(ChestBlock.TYPE, ChestType.LEFT);
        List<CompoundTag> blockEntities = new ArrayList<>();
        blockEntities.add(nbt);
        blockEntities.add(null);
        return sizedStructure(
            2,
            1,
            1,
            List.of(BlockPos.ZERO, new BlockPos(1, 0, 0)),
            List.of(right, left),
            blockEntities
        );
    }

    private static CompoundTag loneLeftChestStructure() {
        BlockState left = Blocks.CHEST.defaultBlockState()
            .setValue(ChestBlock.FACING, Direction.SOUTH)
            .setValue(ChestBlock.TYPE, ChestType.LEFT);
        return singleBlockStructure(left, null);
    }

    private static CompoundTag hollowCubeStructure() {
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    if (x == 1 && y == 1 && z == 1) continue;
                    positions.add(new BlockPos(x, y, z));
                    states.add(cobble);
                }
            }
        }
        return multiBlockStructure(positions, states);
    }

    private static CompoundTag solidCubeStructure() {
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    positions.add(new BlockPos(x, y, z));
                    states.add(cobble);
                }
            }
        }
        return multiBlockStructure(positions, states);
    }

    private static CompoundTag minecartTunnelStructure() {
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        BlockState stone = Blocks.SMOOTH_STONE.defaultBlockState();
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 4; z++) {
                positions.add(new BlockPos(x, 0, z));
                states.add(stone);
            }
            positions.add(new BlockPos(x, 1, 1));
            states.add(Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
            positions.add(new BlockPos(x, 1, 2));
            states.add(Blocks.GLASS.defaultBlockState());
            positions.add(new BlockPos(x, 1, 3));
            states.add(Blocks.RAIL.defaultBlockState());
            positions.add(new BlockPos(x, 1, 4));
            states.add(Blocks.GLASS.defaultBlockState());
            positions.add(new BlockPos(x, 2, 2));
            states.add(stone);
            positions.add(new BlockPos(x, 2, 4));
            states.add(stone);
            positions.add(new BlockPos(x, 3, 1));
            states.add(stone);
            positions.add(new BlockPos(x, 3, 3));
            states.add(stone);
            positions.add(new BlockPos(x, 3, 4));
            states.add(stone);
        }
        for (int x : List.of(0, 6)) {
            for (int y = 0; y <= 3; y++) {
                for (int z : List.of(2, 3)) {
                    positions.add(new BlockPos(x, y, z));
                    states.add(stone);
                }
            }
        }
        CompoundTag structure = sizedStructure(7, 4, 5, positions, states, List.of());
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:hopper_minecart");
        CompoundTag entry = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(3.5D));
        pos.add(DoubleTag.valueOf(1.0D));
        pos.add(DoubleTag.valueOf(3.5D));
        entry.put("pos", pos);
        ListTag blockPos = new ListTag();
        blockPos.add(IntTag.valueOf(3));
        blockPos.add(IntTag.valueOf(1));
        blockPos.add(IntTag.valueOf(3));
        entry.put("blockPos", blockPos);
        entry.put("nbt", nbt);
        ListTag entities = new ListTag();
        entities.add(entry);
        structure.put("entities", entities);
        return structure;
    }

    private static CompoundTag doorRoomStructure() {
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                positions.add(new BlockPos(x, 0, z));
                states.add(cobble);
                if (x != 1 || z != 2) {
                    positions.add(new BlockPos(x, 2, z));
                    states.add(cobble);
                }
            }
        }
        positions.add(new BlockPos(0, 1, 0));
        positions.add(new BlockPos(1, 1, 0));
        positions.add(new BlockPos(2, 1, 0));
        positions.add(new BlockPos(0, 1, 1));
        positions.add(new BlockPos(2, 1, 1));
        positions.add(new BlockPos(0, 1, 2));
        positions.add(new BlockPos(2, 1, 2));
        positions.add(new BlockPos(1, 1, 1));
        for (int index = 0; index < 8; index++) {
            states.add(cobble);
        }
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upper = Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        positions.add(new BlockPos(1, 1, 2));
        states.add(lower);
        positions.add(new BlockPos(1, 2, 2));
        states.add(upper);
        return multiBlockStructure(positions, states);
    }

    private static CompoundTag slabPocketStructure() {
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                positions.add(new BlockPos(x, 0, z));
                states.add(cobble);
                if (x != 1 || z != 2) {
                    positions.add(new BlockPos(x, 2, z));
                    states.add(cobble);
                }
            }
        }
        positions.add(new BlockPos(0, 1, 0));
        positions.add(new BlockPos(1, 1, 0));
        positions.add(new BlockPos(2, 1, 0));
        positions.add(new BlockPos(0, 1, 1));
        positions.add(new BlockPos(2, 1, 1));
        positions.add(new BlockPos(0, 1, 2));
        positions.add(new BlockPos(2, 1, 2));
        positions.add(new BlockPos(1, 1, 1));
        for (int index = 0; index < 8; index++) {
            states.add(cobble);
        }
        positions.add(new BlockPos(1, 1, 2));
        states.add(Blocks.COBBLESTONE_SLAB.defaultBlockState());
        positions.add(new BlockPos(1, 2, 2));
        states.add(cobble);
        return multiBlockStructure(positions, states);
    }

    private static ConstructionJobProgress quartzMachineClosureProgress(ExtendedGameTestHelper helper) {
        BlockState full = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState topSlab = Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
            .setValue(SlabBlock.TYPE, SlabType.TOP);
        BlockState bottomSlab = Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
            .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        Map<Character, BlockState> states = Map.ofEntries(
            Map.entry('#', full),
            Map.entry('T', topSlab),
            Map.entry('B', bottomSlab),
            Map.entry('C', ModBlocks.CHUTE.get().defaultBlockState()
                .setValue(ChuteBlock.FACING, Direction.WEST)),
            Map.entry('E', ModBlocks.CHUTE.get().defaultBlockState()
                .setValue(ChuteBlock.FACING, Direction.EAST)),
            Map.entry('H', Blocks.HOPPER.defaultBlockState()
                .setValue(HopperBlock.FACING, Direction.WEST)),
            Map.entry('M', ModBlocks.MAGNETIC_CHUTE.get().defaultBlockState()
                .setValue(MagneticChuteBlock.FACING, Direction.EAST)),
            Map.entry('U', ModBlocks.MAGNETIC_CHUTE.get().defaultBlockState()
                .setValue(MagneticChuteBlock.FACING, Direction.UP)),
            Map.entry('S', ModBlocks.SIMPLE_CHUTE.get().defaultBlockState()
                .setValue(SimpleChuteBlock.FACING, Direction.SOUTH)),
            Map.entry('J', ModBlocks.SIMPLE_CHUTE.get().defaultBlockState()
                .setValue(SimpleChuteBlock.FACING, Direction.EAST)),
            Map.entry('X', Blocks.CHEST.defaultBlockState()),
            Map.entry('F', Blocks.SCAFFOLDING.defaultBlockState()),
            Map.entry('A', Blocks.ANVIL.defaultBlockState()),
            Map.entry('R', Blocks.REPEATER.defaultBlockState()),
            Map.entry('Q', Blocks.COMPARATOR.defaultBlockState()),
            Map.entry('G', Blocks.OAK_HANGING_SIGN.defaultBlockState()),
            Map.entry('O', ModBlocks.HOLLOW_MAGNET_BLOCK.get().defaultBlockState()),
            Map.entry('D', Blocks.PISTON_HEAD.defaultBlockState()),
            Map.entry('K', Blocks.CAULDRON.defaultBlockState()),
            Map.entry('W', Blocks.REDSTONE_WIRE.defaultBlockState()),
            Map.entry('L', Blocks.REDSTONE_WALL_TORCH.defaultBlockState())
        );
        String[][] layers = {
            {"SCHT#T#", "XCHT#..", "XCMU#T#"},
            {"TBBX#Q#", "TBBX#..", "UFE###W"},
            {"R..####", "Q..###.", "JAXXQ#L"},
            {"#...###", "....##.", "..G...."},
            {"#..A##.", "..TA##.", "OD#...."},
            {"#.K####", "..Q#B#.", "##....."},
            {".....WW", ".....W.", "......."}
        };
        ConstructionJobProgress progress = new ConstructionJobProgress(UUID.randomUUID());
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 2));
        for (int y = 0; y < layers.length; y++) {
            for (int z = 0; z < layers[y].length; z++) {
                String row = layers[y][z];
                for (int x = 0; x < row.length(); x++) {
                    BlockState state = states.get(row.charAt(x));
                    if (state == null) continue;
                    progress.addOperation(
                        anchor.offset(x, y, z),
                        state,
                        ItemStack.EMPTY,
                        ConstructionBuildOp.Kind.PLACE,
                        ConstructionBuildOp.Status.PENDING
                    );
                }
            }
        }
        return progress;
    }

    private static ConstructionBuildOp lowestPlace(ConstructionJobProgress progress) {
        ConstructionBuildOp best = null;
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.PLACE) continue;
            if (best == null || op.order() < best.order()) {
                best = op;
            }
        }
        check(best != null, "planned job has no PLACE operation");
        return best;
    }

    private static ConstructionBuildOp placeAt(ConstructionJobProgress progress, BlockPos pos) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() == ConstructionBuildOp.Kind.PLACE && op.pos().equals(pos)) {
                return op;
            }
        }
        throw new GameTestAssertException("planned job has no PLACE at " + pos);
    }

    private static String enclosureSummary(
        String label,
        ConstructionBuildOp op,
        ConstructionEnclosure.Analysis analysis,
        BlockPos approach
    ) {
        return label + "[order=" + op.order()
            + ", open=" + analysis.open()
            + ", work=" + analysis.enclosesWork()
            + ", others=" + analysis.enclosesOthers()
            + ", serial=" + analysis.serialSeal()
            + ", cavity=" + analysis.cavity().size()
            + ", approach=" + approach + "]";
    }

    private static String enclosureSummaries(
        ServerLevel level,
        ConstructionJobProgress progress,
        List<ConstructionBuildOp> operations
    ) {
        List<String> summaries = new ArrayList<>();
        for (ConstructionBuildOp op : operations) {
            if (op.status() == ConstructionBuildOp.Status.DELIVERED) continue;
            ConstructionEnclosure.Analysis analysis = ConstructionEnclosure.analyze(level, progress, op, null);
            summaries.add(enclosureSummary(
                "id=" + op.id(),
                op,
                analysis,
                ConstructionJobController.chooseApproach(level, progress, op)
            ));
        }
        return String.join("; ", summaries);
    }

    private static void deliverForTest(ServerLevel level, ConstructionJobProgress progress, ConstructionBuildOp op) {
        if (op.writesProjection()) {
            ConstructionProjectionIndex.tryDeliver(
                level,
                progress.jobId(),
                op.pos(),
                op.target(),
                progress.overlayStates()
            );
        }
        op.setStatus(ConstructionBuildOp.Status.DELIVERED);
        op.setLeaseAllay(null);
    }

    private static CompoundTag oakDoorStructure() {
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upper = Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        return multiBlockStructure(
            List.of(BlockPos.ZERO, new BlockPos(0, 1, 0)),
            List.of(lower, upper)
        );
    }

    private static CompoundTag orphanDoorUpperStructure() {
        BlockState upper = Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        return multiBlockStructure(List.of(BlockPos.ZERO), List.of(upper));
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
        return sizedStructure(
            1,
            1,
            1,
            List.of(BlockPos.ZERO),
            List.of(state),
            nbt == null ? List.of() : List.of(nbt)
        );
    }

    /** 平铺成环的规范快照:size×size 外框一圈鹅卵石，全部落在同一层。 */
    private static CompoundTag ringStructure(int size) {
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (x != 0 && x != size - 1 && z != 0 && z != size - 1) continue;
                positions.add(new BlockPos(x, 0, z));
                states.add(Blocks.COBBLESTONE.defaultBlockState());
            }
        }
        return multiBlockStructure(positions, states);
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

    private static CompoundTag railWithMinecartStructure() {
        CompoundTag tag = sizedStructure(
            1,
            1,
            1,
            List.of(BlockPos.ZERO),
            List.of(Blocks.RAIL.defaultBlockState()),
            List.of()
        );
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:hopper_minecart");
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
        ListTag entities = new ListTag();
        entities.add(entry);
        tag.put("entities", entities);
        return tag;
    }

    private static CompoundTag entityOnlyStructure(List<CompoundTag> entities) {
        // 原版结构通常省略全为空气的 blocks 条目,实体所在格仍必须被任务声明
        CompoundTag tag = sizedStructure(1, 1, 1, List.of(), List.of(Blocks.AIR.defaultBlockState()), List.of());
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

    private static ItemStack engineeringHardHat() {
        ItemStack universal = AllayDefaultHardHat.stack();
        MoldedPlasticData data = MoldedPlasticData.get(universal).orElseThrow();
        FluidStack material = new FluidStack(
            PlasticraftFluids.ENGINEERING_PLASTIC_MELT.get(),
            Math.max(1, data.material().getAmount())
        );
        ItemStack engineering = PlasticraftBlocks.ENGINEERING_PLASTIC.asStack();
        MoldedPlasticData.set(engineering, data.withMaterial(material));
        return engineering;
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

    private static ConstructionBuildOp firstReactiveAt(ConstructionJobProgress progress, BlockPos pos) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() == ConstructionBuildOp.Kind.DEMOLISH
                && op.reactive()
                && op.pos().equals(pos)) {
                return op;
            }
        }
        throw new GameTestAssertException("planned job has no reactive demolition at " + pos);
    }

    private static boolean hasReactiveAt(ConstructionJobProgress progress, BlockPos pos) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() == ConstructionBuildOp.Kind.DEMOLISH
                && op.reactive()
                && op.pos().equals(pos)) {
                return true;
            }
        }
        return false;
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

    private static boolean hasKindAt(
        ConstructionJobProgress progress,
        ConstructionBuildOp.Kind kind,
        BlockPos pos
    ) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() == kind && op.pos().equals(pos)) return true;
        }
        return false;
    }

    private static ConstructionBuildOp operationAt(
        ConstructionJobProgress progress,
        ConstructionBuildOp.Kind kind,
        BlockPos pos
    ) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() == kind && op.pos().equals(pos)) return op;
        }
        return null;
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

    private static ConstructionBuildOp firstPlaceAfter(ConstructionJobProgress progress, int operationId) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() == ConstructionBuildOp.Kind.PLACE && op.id() > operationId) return op;
        }
        throw new GameTestAssertException("planned job has no later PLACE operation");
    }

    private static ConstructionBuildOp firstOpenPlace(ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() == ConstructionBuildOp.Kind.PLACE && op.isOpen()) return op;
        }
        throw new GameTestAssertException("planned job has no remaining PLACE operation");
    }

    private static ConstructionBuildOp closestOpenPlace(ConstructionJobProgress progress, Vec3 from) {
        ConstructionBuildOp best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.PLACE || !op.isOpen()) continue;
            double distance = from.distanceToSqr(Vec3.atCenterOf(op.pos()));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = op;
            }
        }
        if (best == null) throw new GameTestAssertException("planned job has no open PLACE operation");
        return best;
    }

    private static double centerDistanceSqr(WorkingAllayEntity worker, ConstructionBuildOp op) {
        return worker.position().distanceToSqr(Vec3.atCenterOf(op.pos()));
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
