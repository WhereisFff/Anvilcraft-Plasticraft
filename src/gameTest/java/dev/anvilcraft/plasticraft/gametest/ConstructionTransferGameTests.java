package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.allay.AllayDefaultHardHat;
import dev.anvilcraft.plasticraft.allay.AllayShortageStrategy;
import dev.anvilcraft.plasticraft.allay.AllayWorkRecord;
import dev.anvilcraft.plasticraft.allay.tool.AllayCapability;
import dev.anvilcraft.plasticraft.allay.transfer.AllayLoungeNetwork;
import dev.anvilcraft.plasticraft.allay.transfer.ConstructionTransferOptimizer;
import dev.anvilcraft.plasticraft.allay.transfer.ConstructionTransferService;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.blueprint.BlueprintSource;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintData;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintException;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintService;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobProgress;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobStore;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 覆盖 TODO 14 远程休息室转运与综合工种调度的服务器契约:
 * 同维度休息室路由图(128 格单段、异主隔离、破坏退出注册表)、ETA 借调决策、
 * 借调出库与 20gt 冷却、逐跳转发、最后一跳的客工到场、外室记录遣返、单通道吞吐,以及权限隔离。
 * 借调悦灵到达协调室时只挂靠不入栈(客工),因此协调室的托管位与 20gt 通道必须始终不受借调影响。
 * 转运图按世界分桶而非按模板,同批测试的模板仅相距数格,因此每个测试都必须用独立 owner 常量,
 * 否则一个测试的协调室会把另一个测试的源室当作合法借调对象。
 */
public final class ConstructionTransferGameTests {
    private ConstructionTransferGameTests() {
    }

    private static final UUID ROUTE_OWNER = UUID.fromString("00000000-0000-0000-0000-0000000010a0");
    private static final UUID ROUTE_STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000010a1");
    private static final UUID BORROW_OWNER = UUID.fromString("00000000-0000-0000-0000-0000000010c0");
    private static final UUID LOCAL_OWNER = UUID.fromString("00000000-0000-0000-0000-0000000010c8");
    private static final UUID DEMOLISH_OWNER = UUID.fromString("00000000-0000-0000-0000-0000000010cc");
    private static final UUID HOP_OWNER = UUID.fromString("00000000-0000-0000-0000-0000000010d0");
    private static final UUID GUEST_OWNER = UUID.fromString("00000000-0000-0000-0000-0000000010d8");
    private static final UUID HOMECOMING_OWNER = UUID.fromString("00000000-0000-0000-0000-0000000010e0");
    private static final UUID THROUGHPUT_OWNER = UUID.fromString("00000000-0000-0000-0000-0000000010f0");
    private static final UUID ISOLATION_OWNER = UUID.fromString("00000000-0000-0000-0000-0000000010f8");
    private static final UUID ISOLATION_FOREIGN = UUID.fromString("00000000-0000-0000-0000-0000000010f9");

    @GameTest(timeoutTicks = 40, batch = "zzz_construction_transfer")
    @EmptyTemplate(value = "137x4x5", floor = true)
    @TestHolder(description = "同主休息室按 128 格单段路由，运行时节点会补登记，异主与已破坏节点被排除")
    static void routeNetworkAndRegistration(ExtendedGameTestHelper helper) {
        BlockPos relA = new BlockPos(1, 2, 1);
        BlockPos relB = new BlockPos(61, 2, 1);
        BlockPos relC = new BlockPos(131, 2, 1);
        BlockPos relD = new BlockPos(131, 2, 3);
        BlockPos[] abs = {
            helper.absolutePos(relA),
            helper.absolutePos(relB),
            helper.absolutePos(relC),
            helper.absolutePos(relD)
        };
        helper.startSequence()
            .thenExecute(() -> {
                placeLounge(helper, relA, ROUTE_OWNER);
                placeLounge(helper, relB, ROUTE_OWNER);
                placeLounge(helper, relC, ROUTE_OWNER);
                placeLounge(helper, relD, ROUTE_STRANGER);
            })
            .thenIdle(2)
            .thenExecute(() -> {
                ServerLevel level = helper.getLevel();
                AllayLoungeNetwork.unregister(level, abs[1]);
                check(!AllayLoungeNetwork.isRegistered(level, abs[1]),
                    "模拟运行时节点丢失后 B 必须暂时不在转运注册表");
            })
            .thenIdle(1)
            .thenExecute(() -> {
                ServerLevel level = helper.getLevel();
                check(AllayLoungeNetwork.isRegistered(level, abs[0]), "A 休息室放置后必须进入转运注册表");
                check(AllayLoungeNetwork.isRegistered(level, abs[1]), "B 休息室必须被休息室实体刻补登记回转运注册表");
                check(AllayLoungeNetwork.isRegistered(level, abs[2]), "C 休息室放置后必须进入转运注册表");
                check(AllayLoungeNetwork.isRegistered(level, abs[3]), "异主休息室 D 也必须已被注册");
                check(AllayLoungeNetwork.hopCount(level, abs[0], abs[2], ROUTE_OWNER) == 2,
                    "A(1)→C(131) 超过 128 格单段，必须经 B 中转成 2 跳");
                check(abs[1].equals(AllayLoungeNetwork.nextHop(level, abs[0], abs[2], ROUTE_OWNER)),
                    "A→C 的下一跳必须是不经过异主 D 的 B");
                check(AllayLoungeNetwork.hopCount(level, abs[0], abs[1], ROUTE_OWNER) == 1,
                    "A→B 相距 60 格必须是 1 跳");
                check(AllayLoungeNetwork.hopCount(level, abs[1], abs[2], ROUTE_OWNER) == 1,
                    "B→C 相距 70 格必须是 1 跳");
                check(abs[2].equals(AllayLoungeNetwork.nextHop(level, abs[1], abs[2], ROUTE_OWNER)),
                    "B→C 的下一跳必须是终点 C");
                check(AllayLoungeNetwork.hopCount(level, abs[0], abs[0], ROUTE_OWNER) == 0,
                    "起终点相同必须是 0 跳，才能让转运判定识别“已在目标室”");
                check(AllayLoungeNetwork.nextHop(level, abs[0], abs[0], ROUTE_OWNER) == null,
                    "起终点相同时不得给出下一跳");
                check(AllayLoungeNetwork.hopCount(level, abs[0], abs[3], ROUTE_OWNER) == -1,
                    "异主休息室 D 即使物理上可桥接 A→B 也不得进入路由，A→D 必须不可达");
            })
            .thenExecute(() -> {
                helper.setBlock(relB, Blocks.AIR);
                check(!AllayLoungeNetwork.isRegistered(helper.getLevel(), abs[1]),
                    "破坏休息室必须同步把 B 移出转运注册表");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_construction_transfer")
    @EmptyTemplate(value = "3x4x3", floor = true)
    @TestHolder(description = "ETA 优化器在无工人必借、本地充足拒远、在途计入工人数、多操作值得借时给出正确决策")
    static void optimizerIsBeneficial(ExtendedGameTestHelper helper) {
        check(ConstructionTransferOptimizer.isBeneficial(0, 0, 10, 1000.0D),
            "本地与在途都无工人且仍有余量时无论转运成本多高都必须借调");
        check(!ConstructionTransferOptimizer.isBeneficial(5, 0, 10, 1000.0D),
            "本地已有 5 工时转运成本过高必须拒绝远程借调");
        check(!ConstructionTransferOptimizer.isBeneficial(0, 5, 10, 1000.0D),
            "在途工人必须与已到场工人同等计入，避免同一任务被反复借调抽干远端");
        check(ConstructionTransferOptimizer.isBeneficial(2, 0, 1000, 50.0D),
            "剩余操作极多而转运成本很低时必须值得借调");
        check(!ConstructionTransferOptimizer.isBeneficial(0, 0, 0, 1.0D),
            "已无剩余操作时不得再借调");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 120, batch = "zzz_construction_transfer_live")
    @EmptyTemplate(value = "45x6x11", floor = true)
    @TestHolder(description = "协调室无本地建设记录时按 ETA 从同主源室借调，借调悦灵以客工到场不入栈，20gt 冷却内不借第二只")
    static void borrowLaunchAndCooldown(ExtendedGameTestHelper helper) {
        BlockPos relCoordinator = new BlockPos(5, 2, 5);
        // 源室必须落在建造体积(x=6..29, z=5)之外，否则会被规划成拆除目标而把任务推入等待拆除
        BlockPos relSource = new BlockPos(25, 2, 9);
        BlockPos[] absCoordinator = {helper.absolutePos(relCoordinator)};
        BlockPos[] absSource = {helper.absolutePos(relSource)};
        UUID[] jobSlot = {null};
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        helper.startSequence()
            .thenExecute(() -> {
                try {
                    AllayLoungeBlockEntity coordinator = placeLoungeWithChest(
                        helper, relCoordinator, new ItemStack(Items.COBBLESTONE, 64));
                    UUID jobId = claimAndStartLoungeJob(
                        helper, coordinator, BORROW_OWNER, 24, new BlockPos(6, 2, 5));
                    jobSlot[0] = jobId;
                    AllayLoungeBlockEntity source = placeLounge(helper, relSource, BORROW_OWNER);
                    check(source.addHosted(plainConstructionRecord(firstId, BORROW_OWNER)),
                        "源室托管第一条建设记录必须成功");
                    check(source.addHosted(plainConstructionRecord(secondId, BORROW_OWNER)),
                        "源室托管第二条建设记录必须成功");
                } catch (ConstructionBlueprintException exception) {
                    throw new GameTestAssertException("借调启动场景搭建失败: " + exception.reason());
                }
            })
            .thenWaitUntil(() -> {
                ServerLevel level = helper.getLevel();
                MinecraftServer server = level.getServer();
                AllayLoungeBlockEntity coordinator = loungeAt(helper, absCoordinator[0]);
                check(level.getEntity(firstId) instanceof WorkingAllayEntity worker
                        && absSource[0].equals(worker.originLoungePos())
                        && absCoordinator[0].equals(worker.homeLoungePos())
                        && worker.transitJobId().isEmpty()
                        && !worker.isDockingTo(absCoordinator[0]),
                    "借调悦灵必须以客工到场:homeLounge 指向协调室以取得参与资格与取放点，但不飞进协调室入栈");
                check(!hostedHas(coordinator, firstId) && !coordinator.isBayBusy(),
                    "客工不得占用协调室的托管位与 20gt 出入库通道");
                check(ConstructionTransferService.inTransitCount(server, jobSlot[0]) == 0,
                    "客工到场后必须从在途台账交接出去，不能既算在途又算到场");
                ConstructionJob job = ConstructionJobIndex.get(server).job(jobSlot[0]);
                ConstructionJobProgress progress = ConstructionJobStore.get(server).get(jobSlot[0]);
                check(job != null && progress != null
                        && ConstructionJobController.participantCount(level, job, progress) == 1,
                    "到场客工必须恰好按一名参与者计数");
                check(!hostedHas(loungeAt(helper, absSource[0]), firstId),
                    "借调出库后源室不得继续保留该托管记录");
            })
            .thenExecute(() -> {
                ServerLevel level = helper.getLevel();
                MinecraftServer server = level.getServer();
                ConstructionJob job = ConstructionJobIndex.get(server).job(jobSlot[0]);
                check(job != null, "借调断言前任务必须仍在索引中");
                ConstructionJobController.tickJob(server, level, job);
                ConstructionJobController.tickJob(server, level, job);
                check(hostedHas(loungeAt(helper, absSource[0]), secondId),
                    "20gt 冷却内的再次 tickJob 不得借出源室第二条记录");
                check(level.getEntity(secondId) == null,
                    "冷却内不得为源室第二条记录生成实体");
                check(ConstructionTransferService.inTransitCount(server, jobSlot[0]) == 0,
                    "客工模式下在途台账必须保持为空，不得被重复登记而虚占参与上限");
                cancelJobQuietly(server, jobSlot[0]);
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 120, batch = "zzz_construction_transfer_live")
    @EmptyTemplate(value = "15x6x11", floor = true)
    @TestHolder(description = "协调室有本地托管记录时仍必须直接出库，不被转运并发判断拦截")
    static void localLaunchStillWorksWithTransferAccounting(ExtendedGameTestHelper helper) {
        BlockPos relCoordinator = new BlockPos(5, 2, 5);
        UUID recordId = UUID.randomUUID();
        UUID[] jobSlot = {null};
        helper.startSequence()
            .thenExecute(() -> {
                try {
                    AllayLoungeBlockEntity coordinator = placeLoungeWithChest(
                        helper, relCoordinator, new ItemStack(Items.COBBLESTONE, 64));
                    // 8 格建造体积正好落在 15 格模板内，避免越界规划到邻近模板的地板
                    UUID jobId = claimAndStartLoungeJob(
                        helper, coordinator, LOCAL_OWNER, 8, new BlockPos(6, 2, 5));
                    jobSlot[0] = jobId;
                    check(coordinator.addHosted(plainConstructionRecord(recordId, LOCAL_OWNER)),
                        "协调室本地托管建设记录必须成功");
                } catch (ConstructionBlueprintException exception) {
                    throw new GameTestAssertException("本地出库场景搭建失败: " + exception.reason());
                }
            })
            .thenWaitUntil(() -> {
                AllayLoungeBlockEntity coordinator = loungeAt(helper, helper.absolutePos(relCoordinator));
                check(helper.getLevel().getEntity(recordId) instanceof WorkingAllayEntity,
                    "本地托管记录必须由任务调度器直接生成悦灵实体");
                check(!hostedHas(coordinator, recordId),
                    "直接出库后协调室不应继续保留该托管记录");
            })
            .thenExecute(() -> cancelJobQuietly(helper.getLevel().getServer(), jobSlot[0]))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 160, batch = "zzz_construction_transfer_live")
    @EmptyTemplate(value = "35x6x11", floor = true)
    @TestHolder(description = "等待拆除阶段也会从远程休息室借调拆除悦灵")
    static void waitingDemolitionBorrowsRemoteWorker(ExtendedGameTestHelper helper) {
        BlockPos relCoordinator = new BlockPos(5, 2, 5);
        BlockPos relSource = new BlockPos(25, 2, 5);
        BlockPos relAnchor = new BlockPos(6, 2, 5);
        BlockPos[] absSource = {helper.absolutePos(relSource)};
        BlockPos[] absCoordinator = {helper.absolutePos(relCoordinator)};
        UUID recordId = UUID.randomUUID();
        UUID[] jobSlot = {null};
        helper.startSequence()
            .thenExecute(() -> {
                try {
                    AllayLoungeBlockEntity coordinator = placeLoungeWithChest(
                        helper, relCoordinator, new ItemStack(Items.COBBLESTONE, 64));
                    helper.setBlock(relAnchor, Blocks.STONE);
                    placeLounge(helper, relSource, DEMOLISH_OWNER);
                    UUID jobId = claimAndStartLoungeJob(
                        helper, coordinator, DEMOLISH_OWNER, 1, relAnchor);
                    jobSlot[0] = jobId;
                } catch (ConstructionBlueprintException exception) {
                    throw new GameTestAssertException("等待拆除借调场景搭建失败: " + exception.reason());
                }
            })
            .thenIdle(2)
            .thenExecute(() -> {
                ConstructionJob job = ConstructionJobIndex.get(helper.getLevel().getServer()).job(jobSlot[0]);
                check(job != null && job.state() == ConstructionJob.STATE_WAITING_DEMOLITION,
                    "协调室缺拆除悦灵时任务必须先进入等待拆除");
            })
            .thenExecute(() -> check(
                loungeAt(helper, absSource[0]).addHosted(
                    recordWithTool(recordId, DEMOLISH_OWNER, Items.STONECUTTER)),
                "远程源室必须成功托管一只拆除悦灵"
            ))
            .thenExecute(() -> {
                ServerLevel level = helper.getLevel();
                MinecraftServer server = level.getServer();
                ConstructionJob job = ConstructionJobIndex.get(server).job(jobSlot[0]);
                AllayLoungeBlockEntity coordinator = loungeAt(helper, absCoordinator[0]);
                AllayLoungeBlockEntity source = loungeAt(helper, absSource[0]);
                check(job != null, "远程拆除借调任务必须存在");
                check(AllayLoungeNetwork.isRegistered(level, absSource[0]),
                    "远程拆除源室必须已注册到转运图");
                check(AllayLoungeNetwork.hopCount(level, absSource[0], absCoordinator[0], DEMOLISH_OWNER) == 1,
                    "远程拆除源室必须能一跳到达协调室");
                check(!coordinator.isBayBusy() && !source.isBayBusy(),
                    "远程拆除借调前协调室与源室出入库通道必须空闲");
                // 明确推进一次中央调度,避免把 thenWaitUntil 当作轮询而掩盖真实状态
                ConstructionJobController.tickJob(server, level, job);
                ConstructionJob current = ConstructionJobIndex.get(server).job(jobSlot[0]);
                check(ConstructionTransferService.inTransitCount(server, jobSlot[0], AllayCapability.DEMOLISH) == 0,
                    "一跳可达的借调在到达瞬间就转为客工，在途台账必须已交接清零");
                check(level.getEntity(recordId) instanceof WorkingAllayEntity worker
                        && absSource[0].equals(worker.originLoungePos())
                        && absCoordinator[0].equals(worker.homeLoungePos())
                        && worker.transitJobId().isEmpty()
                        && !worker.isDockingTo(absCoordinator[0]),
                    "等待拆除阶段的远程拆除悦灵必须从源室出库并以客工挂靠协调室");
                check(!hostedHas(coordinator, recordId) && !coordinator.isBayBusy(),
                    "到场的拆除客工不得占用协调室的托管位与 20gt 出入库通道");
                check(source.isBayBusy(),
                    "借调只消耗源室自己的 20gt 出库通道");
                check(current != null && current.state() == ConstructionJob.STATE_DEMOLISHING,
                    "远程拆除悦灵到场后任务必须离开等待拆除状态");
                check(loungeAt(helper, absSource[0]).hosted().isEmpty(),
                    "远程拆除悦灵出库后源室不得继续保留托管记录");
            })
            .thenExecute(() -> cancelJobQuietly(helper.getLevel().getServer(), jobSlot[0]))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 300, batch = "zzz_construction_transfer_live")
    @EmptyTemplate(value = "55x6x11", floor = true)
    @TestHolder(description = "中转室把在途悦灵逐跳转发到协调室，最后一跳只挂靠成客工并保留 originLounge")
    static void hopByHopForwardAndArrival(ExtendedGameTestHelper helper) {
        BlockPos relA = new BlockPos(5, 2, 5);
        BlockPos relB = new BlockPos(25, 2, 5);
        BlockPos relC = new BlockPos(45, 2, 5);
        BlockPos[] absA = {helper.absolutePos(relA)};
        BlockPos[] absB = {helper.absolutePos(relB)};
        BlockPos[] absC = {helper.absolutePos(relC)};
        UUID[] jobSlot = {null};
        UUID recordId = UUID.randomUUID();
        helper.startSequence()
            .thenExecute(() -> {
                try {
                    AllayLoungeBlockEntity coordinator = placeLoungeWithChest(
                        helper, relC, new ItemStack(Items.COBBLESTONE, 64));
                    helper.setBlock(new BlockPos(44, 2, 5), Blocks.STONE);
                    UUID jobId = claimAndStartLoungeJob(
                        helper, coordinator, HOP_OWNER, 1, new BlockPos(44, 2, 5));
                    jobSlot[0] = jobId;
                    check(ConstructionJobIndex.get(helper.getLevel().getServer()).job(jobId).state()
                            == ConstructionJob.STATE_DEMOLISHING,
                        "占用格任务必须规划为拆除阶段，才能让到场的拆除客工有活可做而不立刻返程");
                    placeLounge(helper, relA, HOP_OWNER);
                    AllayLoungeBlockEntity transit = placeLounge(helper, relB, HOP_OWNER);
                    check(transit.addHosted(
                            transferRecord(recordId, HOP_OWNER, Items.STONECUTTER, absA[0], Optional.of(jobId))),
                        "中转室托管一条 origin=A、transit=J 的在途拆除记录必须成功");
                } catch (ConstructionBlueprintException exception) {
                    throw new GameTestAssertException("逐跳转发场景搭建失败: " + exception.reason());
                }
            })
            .thenWaitUntil(() -> {
                ServerLevel level = helper.getLevel();
                AllayLoungeBlockEntity coordinator = loungeAt(helper, absC[0]);
                check(level.getEntity(recordId) instanceof WorkingAllayEntity worker
                        && absC[0].equals(worker.homeLoungePos())
                        && absA[0].equals(worker.originLoungePos())
                        && worker.transitJobId().isEmpty(),
                    "在途记录必须经中转室转发到协调室并挂靠成客工:清 transitJob、保留 originLounge=A");
                check(!hostedHas(coordinator, recordId) && !coordinator.isBayBusy(),
                    "最后一跳不得入栈协调室，也不得占用协调室的 20gt 通道");
                check(!hostedHas(loungeAt(helper, absB[0]), recordId),
                    "中转室转发后不得继续保留该在途记录");
                check(ConstructionTransferService.inTransitCount(level.getServer(), jobSlot[0]) == 0,
                    "客工到场后在途台账必须清零");
            })
            .thenExecute(() -> cancelJobQuietly(helper.getLevel().getServer(), jobSlot[0]))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 60, batch = "zzz_construction_transfer")
    @EmptyTemplate(value = "45x6x11", floor = true)
    @TestHolder(description = "借调到场的客工不占协调室托管位与通道，协调室本室悦灵仍能立刻出库")
    static void guestNeverBlocksCoordinatorBay(ExtendedGameTestHelper helper) {
        BlockPos relCoordinator = new BlockPos(5, 2, 5);
        BlockPos relSource = new BlockPos(25, 2, 9);
        BlockPos[] absCoordinator = {helper.absolutePos(relCoordinator)};
        BlockPos[] absSource = {helper.absolutePos(relSource)};
        UUID[] jobSlot = {null};
        UUID guestId = UUID.randomUUID();
        UUID localId = UUID.randomUUID();
        helper.startSequence()
            .thenExecute(() -> {
                try {
                    AllayLoungeBlockEntity coordinator = placeLoungeWithChest(
                        helper, relCoordinator, new ItemStack(Items.COBBLESTONE, 64));
                    // 不启动任务:借调与本地出库都由手动调用决定时序,免得服务器 tick 抢先消耗出库通道
                    jobSlot[0] = claimLoungeJob(
                        helper, coordinator, GUEST_OWNER, 24, new BlockPos(6, 2, 5));
                    // 本室记录换成拆除工具,使其不匹配建设借调谓词,considerBorrow 才会真正走到远程借调
                    check(coordinator.addHosted(recordWithTool(localId, GUEST_OWNER, Items.STONECUTTER)),
                        "协调室托管一条本室拆除记录必须成功");
                    AllayLoungeBlockEntity source = placeLounge(helper, relSource, GUEST_OWNER);
                    check(source.addHosted(plainConstructionRecord(guestId, GUEST_OWNER)),
                        "源室托管一条建设记录必须成功");
                } catch (ConstructionBlueprintException exception) {
                    throw new GameTestAssertException("客工不占通道场景搭建失败: " + exception.reason());
                }
            })
            .thenIdle(3)
            .thenExecute(() -> {
                ServerLevel level = helper.getLevel();
                MinecraftServer server = level.getServer();
                ConstructionJob job = ConstructionJobIndex.get(server).job(jobSlot[0]);
                AllayLoungeBlockEntity coordinator = loungeAt(helper, absCoordinator[0]);
                Predicate<AllayWorkRecord> builder = record -> record.heldTool().is(ModItems.CRAB_CLAW.get());
                Predicate<AllayWorkRecord> demolisher = record -> record.heldTool().is(Items.STONECUTTER);
                check(!coordinator.isBayBusy(), "借调前协调室出入库通道必须空闲");
                ConstructionTransferService.considerBorrow(
                    level, job, coordinator, AllayCapability.PICK_UP_MATERIAL, builder, 24, 0);
                Entity spawned = level.getEntity(guestId);
                check(spawned instanceof WorkingAllayEntity, "借调必须让源室出库生成实体");
                WorkingAllayEntity guest = (WorkingAllayEntity) spawned;
                check(absCoordinator[0].equals(guest.homeLoungePos())
                        && absSource[0].equals(guest.originLoungePos())
                        && ConstructionTransferService.isGuest(guest),
                    "借调悦灵必须挂靠协调室并被识别为客工，真正的家仍记在 originLounge");
                check(!hostedHas(coordinator, guestId) && coordinator.hosted().size() == 1,
                    "客工不得进入协调室托管栈，协调室托管位必须仍只有本室那条记录");
                check(!coordinator.isBayBusy(),
                    "客工到场不得占用协调室的 20gt 出入库通道");
                check(coordinator.tryLaunch(demolisher),
                    "客工到场后协调室本室悦灵必须仍能立刻出库，不被外室悦灵堵住");
                cancelJobQuietly(server, jobSlot[0]);
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 400, batch = "zzz_construction_transfer_live")
    @EmptyTemplate(value = "55x6x11", floor = true)
    @TestHolder(description = "滞留在协调室托管栈里的外室记录即使任务仍在进行也立刻沿链遣返，到家后清空转运字段")
    static void borrowedRecordNeverStaysAtCoordinator(ExtendedGameTestHelper helper) {
        BlockPos relA = new BlockPos(5, 2, 5);
        BlockPos relC = new BlockPos(45, 2, 5);
        BlockPos[] absA = {helper.absolutePos(relA)};
        BlockPos[] absC = {helper.absolutePos(relC)};
        UUID[] jobSlot = {null};
        UUID recordId = UUID.randomUUID();
        helper.startSequence()
            .thenExecute(() -> {
                try {
                    AllayLoungeBlockEntity coordinator = placeLoungeWithChest(
                        helper, relC, new ItemStack(Items.COBBLESTONE, 64));
                    helper.setBlock(new BlockPos(44, 2, 5), Blocks.STONE);
                    UUID jobId = claimAndStartLoungeJob(
                        helper, coordinator, HOMECOMING_OWNER, 1, new BlockPos(44, 2, 5));
                    jobSlot[0] = jobId;
                    placeLounge(helper, relA, HOMECOMING_OWNER);
                    // 模拟玩家召回等意外路径把外室悦灵塞进协调室:必须尽快遣返而不是被任务留用
                    check(coordinator.addHosted(
                            transferRecord(recordId, HOMECOMING_OWNER, absA[0], Optional.empty())),
                        "协调室托管一条 origin=A 的外室记录必须成功");
                } catch (ConstructionBlueprintException exception) {
                    throw new GameTestAssertException("遣返场景搭建失败: " + exception.reason());
                }
            })
            .thenIdle(3)
            .thenExecute(() -> {
                AllayLoungeBlockEntity coordinator = loungeAt(helper, absC[0]);
                check(!hostedHas(coordinator, recordId),
                    "任务仍在进行时协调室也不得留用外室记录，否则会堵住本室悦灵的通道与托管位");
            })
            .thenWaitUntil(() -> {
                AllayLoungeBlockEntity home = loungeAt(helper, absA[0]);
                AllayWorkRecord returned = hostedRecord(home, recordId);
                check(returned != null && returned.originLounge().isEmpty() && returned.transitJob().isEmpty(),
                    "遣返到源屋后必须转为普通托管记录，两个转运字段都已清空");
            })
            .thenExecute(() -> cancelJobQuietly(helper.getLevel().getServer(), jobSlot[0]))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 120, batch = "zzz_construction_transfer_live")
    @EmptyTemplate(value = "45x6x11", floor = true)
    @TestHolder(description = "本地仍有记录时远端也同步借调，源室 20gt 通道挡住同室第二只，协调室被破坏后客工失去参与资格")
    static void borrowBayThroughputCooldown(ExtendedGameTestHelper helper) {
        BlockPos relCoordinator = new BlockPos(5, 2, 5);
        BlockPos relSource = new BlockPos(25, 2, 5);
        BlockPos[] absCoordinator = {helper.absolutePos(relCoordinator)};
        BlockPos[] absSource = {helper.absolutePos(relSource)};
        UUID[] jobSlot = {null};
        UUID localId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        helper.startSequence()
            .thenExecute(() -> {
                try {
                    AllayLoungeBlockEntity coordinator = placeLoungeWithChest(
                        helper, relCoordinator, new ItemStack(Items.COBBLESTONE, 64));
                    // 借调决策不依赖任务已启动;不启动可让手动调用完全决定时序,避免服务器 tick 抢先出库
                    UUID jobId = claimLoungeJob(
                        helper, coordinator, THROUGHPUT_OWNER, 24, new BlockPos(6, 2, 5));
                    jobSlot[0] = jobId;
                    AllayLoungeBlockEntity source = placeLounge(helper, relSource, THROUGHPUT_OWNER);
                    check(source.addHosted(plainConstructionRecord(firstId, THROUGHPUT_OWNER)),
                        "源室托管第一条建设记录必须成功");
                    check(source.addHosted(plainConstructionRecord(secondId, THROUGHPUT_OWNER)),
                        "源室托管第二条建设记录必须成功");
                    check(coordinator.addHosted(plainConstructionRecord(localId, THROUGHPUT_OWNER)),
                        "协调室托管一条本地建设记录必须成功");
                } catch (ConstructionBlueprintException exception) {
                    throw new GameTestAssertException("通道吞吐场景搭建失败: " + exception.reason());
                }
            })
            .thenIdle(3)
            // 借调、同室节流与破坏协调室全部放在同一 tick 内手动推进:借调客工一旦被实体刻处理就会因无活可做而返程,
            // 分步断言会读到已经启程回家的中间态
            .thenExecute(() -> {
                ServerLevel level = helper.getLevel();
                MinecraftServer server = level.getServer();
                UUID jobId = jobSlot[0];
                ConstructionJob job = ConstructionJobIndex.get(server).job(jobId);
                ConstructionJobProgress progress = ConstructionJobStore.get(server).get(jobId);
                AllayLoungeBlockEntity coordinator = loungeAt(helper, absCoordinator[0]);
                Predicate<AllayWorkRecord> match = record -> record.heldTool().is(ModItems.CRAB_CLAW.get());
                ConstructionTransferService.considerBorrow(
                    level, job, coordinator, AllayCapability.PICK_UP_MATERIAL, match, 24, 0);
                check(level.getEntity(firstId) instanceof WorkingAllayEntity guest
                        && absCoordinator[0].equals(guest.homeLoungePos())
                        && absSource[0].equals(guest.originLoungePos())
                        && guest.transitJobId().isEmpty(),
                    "协调室本地仍有托管记录时远端也必须同步借调一只并以客工挂靠协调室");
                WorkingAllayEntity guest = (WorkingAllayEntity) level.getEntity(firstId);
                check(!coordinator.isBayBusy(),
                    "借调只消耗源室通道，协调室通道必须仍空闲以便本室同刻出库");
                check(coordinator.tryLaunch(match),
                    "协调室本室悦灵必须能与远端借调同刻出库，不必等对方排完");
                check(coordinator.isBayBusy(), "本室出库后协调室的 20gt 通道必须被自己占用");
                check(!hostedHas(loungeAt(helper, absCoordinator[0]), firstId),
                    "客工不得入栈协调室，协调室的托管位只留给本室悦灵");
                check(ConstructionTransferService.inTransitCount(server, jobId) == 0,
                    "一跳可达的借调在出库同刻即完成挂靠，在途台账必须已交接清零");
                check(progress != null && ConstructionJobController.isBoundToCoordinator(guest, progress),
                    "到场客工必须取得协调室绑定，才能按参与者领活并以协调室下方容器取放材料");
                ConstructionTransferService.considerBorrow(
                    level, job, coordinator, AllayCapability.PICK_UP_MATERIAL, match, 24, 0);
                check(loungeAt(helper, absSource[0]).isBayBusy(),
                    "源室出库后自己的 20gt 通道必须被占用，同室下一只只能等下一秒");
                check(hostedHas(loungeAt(helper, absSource[0]), secondId),
                    "源室通道占用期内第二次借调不得取出同室剩余托管记录");
                check(level.getEntity(secondId) == null,
                    "源室通道占用期内第二次借调不得产生第二只实体");
                helper.setBlock(relCoordinator, Blocks.AIR);
                ConstructionJobProgress after = ConstructionJobStore.get(server).get(jobId);
                check(after != null && !after.hasCoordinator(),
                    "协调室被破坏必须解除任务认领");
                check(!ConstructionJobController.isBoundToCoordinator(guest, after),
                    "协调室消失后客工必须立即失去参与资格，从而沿链返程而不是留在原地待命");
                check(ConstructionTransferService.inTransitCount(server, jobId) == 0,
                    "解除认领必须清空该任务的在途台账");
                cancelJobQuietly(server, jobId);
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 60, batch = "zzz_construction_transfer")
    @EmptyTemplate(value = "45x6x11", floor = true)
    @TestHolder(description = "异主休息室既不被借调也不进入路由图，同主节点仍正常可用")
    static void foreignOwnerIsolation(ExtendedGameTestHelper helper) {
        BlockPos relA = new BlockPos(5, 2, 5);
        BlockPos relB = new BlockPos(25, 2, 5);
        BlockPos relC = new BlockPos(15, 2, 5);
        BlockPos[] absA = {helper.absolutePos(relA)};
        BlockPos[] absB = {helper.absolutePos(relB)};
        BlockPos[] absC = {helper.absolutePos(relC)};
        UUID[] jobSlot = {null};
        UUID spyglassId = UUID.randomUUID();
        UUID foreignRecordId = UUID.randomUUID();
        helper.startSequence()
            .thenExecute(() -> {
                try {
                    AllayLoungeBlockEntity coordinator = placeLoungeWithChest(
                        helper, relA, new ItemStack(Items.COBBLESTONE, 64));
                    UUID jobId = claimLoungeJob(
                        helper, coordinator, ISOLATION_OWNER, 24, new BlockPos(6, 2, 5));
                    jobSlot[0] = jobId;
                    AllayLoungeBlockEntity sameOwner = placeLounge(helper, relB, ISOLATION_OWNER);
                    check(sameOwner.addHosted(recordWithTool(spyglassId, ISOLATION_OWNER, Items.SPYGLASS)),
                        "同主源室托管一条观测记录必须成功");
                    AllayLoungeBlockEntity foreign = placeLounge(helper, relC, ISOLATION_FOREIGN);
                    check(foreign.addHosted(plainConstructionRecord(foreignRecordId, ISOLATION_FOREIGN)),
                        "异主源室托管一条建设记录必须成功");
                } catch (ConstructionBlueprintException exception) {
                    throw new GameTestAssertException("权限隔离场景搭建失败: " + exception.reason());
                }
            })
            .thenIdle(2)
            .thenExecute(() -> {
                ServerLevel level = helper.getLevel();
                MinecraftServer server = level.getServer();
                UUID jobId = jobSlot[0];
                ConstructionJob job = ConstructionJobIndex.get(server).job(jobId);
                AllayLoungeBlockEntity coordinator = loungeAt(helper, absA[0]);
                Predicate<AllayWorkRecord> match = record -> record.heldTool().is(ModItems.CRAB_CLAW.get());
                ConstructionTransferService.considerBorrow(
                    level, job, coordinator, AllayCapability.PICK_UP_MATERIAL, match, 24, 0);
                check(ConstructionTransferService.inTransitCount(server, jobId) == 0,
                    "唯一匹配的借调源是异主 C，必须被拒绝且不出借任何悦灵");
                check(hostedHas(loungeAt(helper, absC[0]), foreignRecordId),
                    "异主 C 的托管记录不得被取出");
                check(hostedHas(loungeAt(helper, absB[0]), spyglassId),
                    "同主但能力不匹配的观测记录不得被出库");
                check(AllayLoungeNetwork.nextHop(level, absA[0], absC[0], ISOLATION_OWNER) == null,
                    "nextHop 不得把直接可达的异主 C 当作跳点");
                check(AllayLoungeNetwork.hopCount(level, absA[0], absC[0], ISOLATION_OWNER) == -1,
                    "异主 C 即使物理距离满足也必须不可达");
                check(AllayLoungeNetwork.nextHop(level, absB[0], absA[0], ISOLATION_OWNER) != null,
                    "同主休息室 B 必须仍可作为路由节点(正对照)");
                cancelJobQuietly(server, jobId);
            })
            .thenSucceed();
    }

    private static AllayLoungeBlockEntity placeLounge(
        ExtendedGameTestHelper helper, BlockPos relativePos, UUID owner
    ) {
        helper.setBlock(relativePos, PlasticraftBlocks.ALLAY_LOUNGE.get());
        if (!(helper.getBlockEntity(relativePos) instanceof AllayLoungeBlockEntity lounge)) {
            throw new GameTestAssertException("放置的休息室方块实体缺失");
        }
        lounge.setOwner(owner);
        return lounge;
    }

    private static AllayLoungeBlockEntity placeLoungeWithChest(
        ExtendedGameTestHelper helper, BlockPos relativePos, ItemStack contents
    ) {
        AllayLoungeBlockEntity lounge = placeLounge(helper, relativePos, null);
        helper.setBlock(relativePos.below(), Blocks.CHEST);
        if (!(helper.getBlockEntity(relativePos.below()) instanceof Container container)) {
            throw new GameTestAssertException("休息室下方箱子缺失");
        }
        container.setItem(0, contents);
        return lounge;
    }

    private static AllayLoungeBlockEntity loungeAt(ExtendedGameTestHelper helper, BlockPos absolutePos) {
        if (!(helper.getLevel().getBlockEntity(absolutePos) instanceof AllayLoungeBlockEntity lounge)) {
            throw new GameTestAssertException("该坐标缺少休息室方块实体");
        }
        return lounge;
    }

    private static boolean hostedHas(AllayLoungeBlockEntity lounge, UUID entityId) {
        for (AllayWorkRecord record : lounge.hosted()) {
            if (record.entityId().equals(entityId)) return true;
        }
        return false;
    }

    private static AllayWorkRecord hostedRecord(AllayLoungeBlockEntity lounge, UUID entityId) {
        for (AllayWorkRecord record : lounge.hosted()) {
            if (record.entityId().equals(entityId)) return record;
        }
        return null;
    }

    private static AllayWorkRecord plainConstructionRecord(UUID entityId, UUID owner) {
        return recordWithTool(entityId, owner, ModItems.CRAB_CLAW.get());
    }

    private static AllayWorkRecord recordWithTool(UUID entityId, UUID owner, Item tool) {
        return new AllayWorkRecord(
            entityId,
            AllayDefaultHardHat.stack(),
            new ItemStack(tool),
            Optional.of(owner),
            AllayShortageStrategy.PAUSE,
            List.of(),
            Optional.empty(),
            ItemStack.EMPTY,
            Optional.empty()
        );
    }

    private static AllayWorkRecord transferRecord(
        UUID entityId, UUID owner, BlockPos origin, Optional<UUID> transitJob
    ) {
        return transferRecord(entityId, owner, ModItems.CRAB_CLAW.get(), origin, transitJob);
    }

    private static AllayWorkRecord transferRecord(
        UUID entityId, UUID owner, Item tool, BlockPos origin, Optional<UUID> transitJob
    ) {
        return new AllayWorkRecord(
            entityId,
            AllayDefaultHardHat.stack(),
            new ItemStack(tool),
            Optional.of(owner),
            AllayShortageStrategy.PAUSE,
            List.of(),
            Optional.empty(),
            ItemStack.EMPTY,
            Optional.empty(),
            origin == null ? Optional.empty() : Optional.of(origin.asLong()),
            transitJob
        );
    }

    private static UUID claimAndStartLoungeJob(
        ExtendedGameTestHelper helper,
        AllayLoungeBlockEntity lounge,
        UUID owner,
        int count,
        BlockPos relativeAnchor
    ) throws ConstructionBlueprintException {
        UUID jobId = claimLoungeJob(helper, lounge, owner, count, relativeAnchor);
        ConstructionBlueprintService.start(helper.getLevel().getServer(), jobId);
        ConstructionJob started = ConstructionJobIndex.get(helper.getLevel().getServer()).job(jobId);
        check(started != null && started.isActive(), "启动后任务必须处于活动状态");
        check(ConstructionJobStore.get(helper.getLevel().getServer()).get(jobId).planned(),
            "启动后任务必须已完成规划");
        return jobId;
    }

    /** 服务器纯态认领:手动装配任务使 owner 可用每测试常量,无需玩家权限通道。 */
    private static UUID claimLoungeJob(
        ExtendedGameTestHelper helper,
        AllayLoungeBlockEntity lounge,
        UUID owner,
        int count,
        BlockPos relativeAnchor
    ) throws ConstructionBlueprintException {
        ItemStack disk = new ItemStack(ModItems.STRUCTURE_DISK.get());
        ConstructionBlueprintService.importIntoDisk(
            helper.getLevel().getServer(),
            disk,
            cobbleStructure(count),
            "transfer-cobble-" + count,
            BlueprintSource.VANILLA_FILE
        );
        ConstructionBlueprintData data = ConstructionBlueprintData.get(disk).orElseThrow();
        UUID jobId = UUID.randomUUID();
        ConstructionJob job = new ConstructionJob(
            jobId,
            owner,
            ConstructionJob.STATE_INACTIVE,
            data.hash(),
            helper.getLevel().dimension(),
            helper.absolutePos(relativeAnchor),
            Rotation.NONE,
            Mirror.NONE,
            data.name(),
            data.size(),
            data.source(),
            data.hasBlockEntities(),
            data.hasEntities()
        );
        ConstructionJobIndex.get(helper.getLevel().getServer()).put(job);
        ConstructionBlueprintData.set(disk, data.withJobId(jobId));
        lounge.setOwner(owner);
        lounge.items().setStackInSlot(AllayLoungeBlockEntity.DISK_SLOT, disk);
        ConstructionJob claimed = ConstructionJobIndex.get(helper.getLevel().getServer()).job(jobId);
        ConstructionJobProgress claimedProgress = ConstructionJobStore.get(helper.getLevel().getServer()).get(jobId);
        check(claimed != null && !claimed.isActive(), "磁盘入槽必须只认领而不启动任务");
        check(claimedProgress != null && lounge.getBlockPos().equals(claimedProgress.coordinatorLounge()),
            "磁盘入槽必须把本休息室登记为协调室");
        return jobId;
    }

    private static void cancelJobQuietly(MinecraftServer server, UUID jobId) {
        ConstructionJob job = ConstructionJobIndex.get(server).job(jobId);
        if (job != null) {
            ConstructionJobController.cancel(server, job);
        }
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

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
