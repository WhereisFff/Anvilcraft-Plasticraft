package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.allay.AllayDefaultHardHat;
import dev.anvilcraft.plasticraft.allay.AllayFlightState;
import dev.anvilcraft.plasticraft.allay.AllayShortageStrategy;
import dev.anvilcraft.plasticraft.allay.AllayWorkRecord;
import dev.anvilcraft.plasticraft.allay.observation.ObservationChunkLoader;
import dev.anvilcraft.plasticraft.allay.observation.ObservationCoverage;
import dev.anvilcraft.plasticraft.allay.observation.ObservationCoverageService;
import dev.anvilcraft.plasticraft.allay.observation.ObservationLease;
import dev.anvilcraft.plasticraft.allay.observation.ObservationLoadingIndex;
import dev.anvilcraft.plasticraft.allay.observation.ObservationOwner;
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
import dev.anvilcraft.plasticraft.blueprint.ConstructionWaitReason;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 覆盖 TODO 15 观察悦灵区块加载的服务器契约:持票资格(戴帽+主手望远镜、休息室托管记录)、
 * 休息室与实体之间的双向原子交接、世界级租约台账、九个完整区块柱的实体刻与第十柱的排除、
 * 跨区块先加新票再撤旧票,以及覆盖缺失时任务停在等待观察者并在任一加载来源出现后自行恢复。
 *
 * <p>一份覆盖有 48×48 格,而 GameTest 模板之间只隔 5 格且网格只朝 +X/+Z 生长,因此凡是要断言
 * 「区块确实在实体刻」或「区块确实不在实体刻」的测试,都必须挪到 -X/-Z 的空白世界,并且每个测试
 * 各占一块相距 2048 格以上的区域,保证任何两份九柱覆盖都不可能重叠。留在模板附近的瞬时测试只用
 * 按持票人取值的索引断言(has / covered / 同刻增量),不读原始实体刻状态。
 *
 * <p>把悦灵挪进远处空白区块之前必须先用原版强制加载票把落脚区块顶成实体刻:悦灵一旦被挪进实体
 * 存储尚未加载的区段就会暂时脱离追踪,校验周期会把它当成已消失而撤票。这一步只是模拟「玩家曾经
 * 在场」,建立观察覆盖后立刻撤掉,后续实体刻全部由观察悦灵自己的票维持。
 */
public final class ObservationAllayGameTests {
    private ObservationAllayGameTests() {
    }

    private static final UUID ELIGIBILITY_OWNER = UUID.fromString("00000000-0000-0000-0000-000000001100");
    private static final UUID HANDOVER_OWNER = UUID.fromString("00000000-0000-0000-0000-000000001108");
    private static final UUID COVERAGE_OWNER = UUID.fromString("00000000-0000-0000-0000-000000001110");
    private static final UUID BORDER_OWNER = UUID.fromString("00000000-0000-0000-0000-000000001118");
    private static final UUID WAIT_OWNER = UUID.fromString("00000000-0000-0000-0000-000000001120");
    private static final UUID CONVOY_OWNER = UUID.fromString("00000000-0000-0000-0000-000000001128");
    private static final UUID LAUNCH_OWNER = UUID.fromString("00000000-0000-0000-0000-000000001130");

    @GameTest(timeoutTicks = 40, batch = "zzz_observation")
    @EmptyTemplate(value = "7x5x7", floor = true)
    @TestHolder(description = "只有戴帽且主手望远镜的悦灵、以及托管着这种记录的休息室才持票，换手或脱帽当刻撤票")
    static void observerEligibilityRules(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ObservationLoadingIndex index = ObservationLoadingIndex.get(level);
        WorkingAllayEntity observer = spawnObserver(helper, new Vec3(1.5D, 3.0D, 1.5D), ELIGIBILITY_OWNER);
        ObservationOwner observerOwner = ObservationOwner.observer(level.dimension(), observer.getUUID());
        check(ObservationCoverage.isEligible(observer), "戴帽且主手望远镜必须具备观察资格");
        ObservationChunkLoader.syncObserver(observer);
        check(index.has(observerOwner), "有资格的观察悦灵必须在世界级索引中持票");
        check(ObservationCoverage.centerOf(observer).equals(index.center(observerOwner)),
            "覆盖中心必须是观察悦灵当前所在区块柱");

        // 普通工种绝不加载区块:换掉主手望远镜必须当刻撤票,而不是等校验周期
        observer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.CRAB_CLAW.get()));
        check(!ObservationCoverage.isEligible(observer), "主手换成蟹钳后必须失去观察资格");
        ObservationChunkLoader.syncObserver(observer);
        check(!index.has(observerOwner), "换掉望远镜必须当刻撤票");
        observer.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        ObservationChunkLoader.syncObserver(observer);
        check(!index.has(observerOwner), "空手通用工不得持票");

        // 正对照:重新拿回望远镜必须重新持票,再脱帽必须再次撤票
        observer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SPYGLASS));
        ObservationChunkLoader.syncObserver(observer);
        check(index.has(observerOwner), "重新拿回望远镜必须重新持票");
        observer.setHardHat(ItemStack.EMPTY);
        check(!ObservationCoverage.isEligible(observer), "脱帽后即使手持望远镜也没有观察资格");
        ObservationChunkLoader.syncObserver(observer);
        check(!index.has(observerOwner), "脱帽必须当刻撤票");

        // 托管记录资格:望远镜必须配戴帽,普通箱子和玩家背包里的望远镜不算
        check(ObservationCoverage.isEligible(observationRecord(UUID.randomUUID(), ELIGIBILITY_OWNER)),
            "戴帽望远镜托管记录必须具备资格");
        check(!ObservationCoverage.isEligible(hatlessObservationRecord(UUID.randomUUID(), ELIGIBILITY_OWNER)),
            "没有安全帽的望远镜记录不得具备资格");
        check(!ObservationCoverage.isEligible(plainRecord(UUID.randomUUID(), ELIGIBILITY_OWNER)),
            "蟹钳记录不得具备资格");

        // 休息室资格只看托管记录,与供电无关
        AllayLoungeBlockEntity lounge = placeLounge(helper, new BlockPos(4, 2, 4), ELIGIBILITY_OWNER);
        ObservationOwner loungeOwner = ObservationOwner.lounge(level.dimension(), lounge.getBlockPos());
        UUID spyglassRecordId = UUID.randomUUID();
        check(!ObservationCoverage.isEligible(lounge), "空休息室不得具备观察资格");
        ObservationChunkLoader.syncLounge(lounge);
        check(!index.has(loungeOwner), "空休息室不得持票");
        check(lounge.addHosted(plainRecord(UUID.randomUUID(), ELIGIBILITY_OWNER)), "托管蟹钳记录必须成功");
        ObservationChunkLoader.syncLounge(lounge);
        check(!index.has(loungeOwner), "只托管蟹钳记录的休息室不得持票");
        check(lounge.addHosted(observationRecord(spyglassRecordId, ELIGIBILITY_OWNER)), "托管望远镜记录必须成功");
        ObservationChunkLoader.syncLounge(lounge);
        check(index.has(loungeOwner), "托管望远镜记录后休息室必须持票");
        check(new ChunkPos(lounge.getBlockPos()).equals(index.center(loungeOwner)),
            "休息室覆盖中心必须是它自己所在区块柱");

        // 现场清理:把望远镜记录换成蟹钳,休息室自行撤票,不给后续测试留下无主加载区
        check(lounge.updateHostedRecord(spyglassRecordId, record -> plainRecord(record.entityId(), ELIGIBILITY_OWNER)),
            "替换托管记录必须成功");
        ObservationChunkLoader.syncLounge(lounge);
        check(!index.has(loungeOwner), "托管记录不再持有望远镜后休息室必须撤票");
        observer.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 120, batch = "zzz_observation")
    @EmptyTemplate(value = "7x5x7", floor = true)
    @TestHolder(description = "出库先让观察悦灵持票再退休息室票，入库反向交接，交接刻两份票并存不断刻")
    static void loungeHandoverKeepsCoverage(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ObservationLoadingIndex index = ObservationLoadingIndex.get(level);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        AllayLoungeBlockEntity lounge = placeLounge(helper, new BlockPos(3, 2, 3), HANDOVER_OWNER);
        ObservationOwner loungeOwner = ObservationOwner.lounge(level.dimension(), lounge.getBlockPos());
        ObservationOwner firstOwner = ObservationOwner.observer(level.dimension(), firstId);
        ObservationOwner secondOwner = ObservationOwner.observer(level.dimension(), secondId);
        ChunkPos loungeChunk = new ChunkPos(lounge.getBlockPos());
        helper.startSequence()
            .thenExecute(() -> {
                check(lounge.addHosted(observationRecord(firstId, HANDOVER_OWNER)), "托管第一条望远镜记录必须成功");
                ObservationChunkLoader.syncLounge(lounge);
                check(index.has(loungeOwner) && loungeChunk.equals(index.center(loungeOwner)),
                    "托管观察记录的休息室必须以自身区块为中心持票");
                // 同刻取值再比对:多存几只不扩大范围,也不叠加成第二份覆盖
                int single = index.refCount(level.dimension(), loungeChunk);
                check(lounge.addHosted(observationRecord(secondId, HANDOVER_OWNER)), "托管第二条望远镜记录必须成功");
                ObservationChunkLoader.syncLounge(lounge);
                check(loungeChunk.equals(index.center(loungeOwner))
                        && index.refCount(level.dimension(), loungeChunk) == single,
                    "多存几只观察悦灵不得扩大范围或叠加出第二份覆盖");
            })
            .thenExecute(() -> {
                // 出库:记录变实体后必须由实体各自先持票,休息室票才允许退掉
                lounge.releaseAllToWorld();
                check(level.getEntity(firstId) instanceof WorkingAllayEntity, "出库必须真的生成观察悦灵实体");
                check(index.has(firstOwner) && index.has(secondOwner), "出库的观察悦灵必须各自立刻持票");
                check(!index.has(loungeOwner), "托管记录全部出库后休息室票必须退掉");
                check(index.covered(level.dimension(), loungeChunk), "出库交接后休息室所在柱必须仍被覆盖，不得出现空窗");
                if (!(level.getEntity(secondId) instanceof WorkingAllayEntity spare)) {
                    throw new GameTestAssertException("出库的第二只观察悦灵必须可查到");
                }
                spare.discard();
                check(!index.has(secondOwner), "销毁的观察悦灵必须撤掉自己的票");
            })
            .thenExecute(() -> {
                // 入库:休息室必须在实体销毁之前先接过覆盖,因此同刻两份票并存
                if (!(level.getEntity(firstId) instanceof WorkingAllayEntity worker)) {
                    throw new GameTestAssertException("入库交接前必须能拿到出库的观察悦灵");
                }
                check(lounge.tryDock(worker), "观察悦灵入库必须被接受");
                check(index.has(loungeOwner) && index.has(firstOwner),
                    "入库刻休息室票与实体票必须并存，反向交接才不断刻");
                check(index.refCount(level.dimension(), loungeChunk) >= 2,
                    "并存期间该柱引用计数必须大于一，撤掉任一张票都不会停刻");
                worker.discard();
                check(!index.has(firstOwner), "入库后实体票必须撤掉");
                check(index.has(loungeOwner) && index.covered(level.dimension(), loungeChunk),
                    "实体消失后休息室票必须仍在，入库交接不得断刻");
            })
            .thenWaitUntil(() -> check(lounge.hosted().size() == 1, "20gt 入库通道必须把记录落进托管栈"))
            .thenExecute(() -> {
                check(index.has(loungeOwner), "入库完成后休息室必须继续持票");
                // 现场清理:换掉望远镜让休息室自行撤票,顺带锁住托管记录换手即撤票
                check(lounge.updateHostedRecord(firstId, record -> plainRecord(record.entityId(), HANDOVER_OWNER)),
                    "替换托管记录必须成功");
                ObservationChunkLoader.syncLounge(lounge);
                check(!index.has(loungeOwner), "托管记录换掉望远镜后休息室必须撤票");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_observation")
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "观察租约按任务与观察者双向索引，同一窗口只留一名观察者，任务结束时租约一并清空")
    static void observationLeaseBookkeeping(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ObservationLoadingIndex index = ObservationLoadingIndex.get(level);
        UUID jobId = UUID.randomUUID();
        UUID otherJobId = UUID.randomUUID();
        UUID firstObserver = UUID.randomUUID();
        UUID secondObserver = UUID.randomUUID();
        UUID thirdObserver = UUID.randomUUID();
        ChunkPos firstWindow = new ChunkPos(helper.absolutePos(new BlockPos(0, 2, 0)));
        ChunkPos secondWindow = new ChunkPos(firstWindow.x + 8, firstWindow.z);

        index.addLease(jobId, new ObservationLease(level.dimension(), firstWindow, firstObserver));
        check(index.hasLease(jobId, firstWindow), "登记后任务必须持有该窗口的租约");
        check(!index.hasLease(otherJobId, firstWindow), "租约按任务分账，其他任务不得看到这份租约");
        check(firstWindow.equals(requireLease(index, firstObserver).center()), "观察者反查必须指回它负责的窗口");
        check(jobId.equals(index.jobOf(firstObserver)), "观察者反查必须指回它服务的任务");

        // 同一窗口只能有一名观察者,否则两只观察悦灵会被同一个窗口重复占用
        index.addLease(jobId, new ObservationLease(level.dimension(), firstWindow, secondObserver));
        check(index.leases(jobId).size() == 1, "同一窗口重新指派不得留下两份租约");
        check(index.leaseOf(firstObserver) == null, "被替换的观察者必须立即解除占用");
        check(jobId.equals(index.jobOf(secondObserver)), "接手的观察者必须登记到同一任务");

        index.addLease(jobId, new ObservationLease(level.dimension(), secondWindow, thirdObserver));
        check(index.leases(jobId).size() == 2, "不同窗口必须各自登记租约");
        index.releaseObserverLeases(thirdObserver);
        check(index.leases(jobId).size() == 1 && index.leaseOf(thirdObserver) == null,
            "观察者失效必须清掉它名下的租约，让任务下一刻重新规划");
        index.removeLease(jobId, firstWindow);
        check(index.leases(jobId).isEmpty() && !index.hasLease(jobId, firstWindow),
            "窗口释放后不得残留租约");

        // 任务结束时只清自己的租约,不得动别的任务
        index.addLease(jobId, new ObservationLease(level.dimension(), firstWindow, firstObserver));
        index.addLease(jobId, new ObservationLease(level.dimension(), secondWindow, secondObserver));
        index.addLease(otherJobId, new ObservationLease(level.dimension(), secondWindow, thirdObserver));
        ObservationCoverageService.releaseJob(server, jobId);
        check(index.leases(jobId).isEmpty(), "任务结束必须清空自己的全部租约");
        check(index.leases(otherJobId).size() == 1, "任务结束不得动其他任务的租约");
        index.removeLeases(otherJobId);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 600, batch = "zzz_observation_live")
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "一只观察悦灵让九个完整区块柱持续实体刻，地下与高空同样有效，第十柱不被它加载，重叠覆盖按持票人计数")
    static void nineColumnCoverageSpansFullHeight(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ObservationLoadingIndex index = ObservationLoadingIndex.get(level);
        BlockPos anchor = farAnchor(helper, -2048, -2048);
        ChunkPos center = new ChunkPos(anchor);
        ChunkPos overlap = new ChunkPos(center.x + 1, center.z);
        ChunkPos tenth = new ChunkPos(center.x + 2, center.z);
        WorkingAllayEntity[] slots = new WorkingAllayEntity[2];
        helper.startSequence()
            .thenExecute(() -> {
                check(!ObservationChunkLoader.isTicking(level, center),
                    "远处空白区块在搭建场景前必须不是实体刻，否则覆盖断言不成立");
                level.setChunkForced(center.x, center.z, true);
            })
            .thenWaitUntil(() -> check(ObservationChunkLoader.isTicking(level, center),
                "落脚区块必须先由原版强制加载票顶成实体刻，观察悦灵才能被挪进去"))
            .thenExecute(() -> {
                slots[0] = spawnObserver(helper, new Vec3(1.5D, 3.0D, 1.5D), COVERAGE_OWNER);
                pin(helper, slots[0], center, anchor.getY());
                for (ChunkPos column : ObservationCoverage.columns(center)) {
                    check(index.covered(level.dimension(), column), "九柱必须全部登记为已覆盖: " + column);
                }
                check(!index.covered(level.dimension(), tenth), "第十柱不得出现在覆盖索引中");
                // 撤掉模拟玩家的强制加载票,此后九柱的实体刻只能由观察悦灵自己的票维持
                level.setChunkForced(center.x, center.z, false);
            })
            .thenWaitUntil(() -> {
                pin(helper, slots[0], center, anchor.getY());
                check(ObservationChunkLoader.isCoverageTicking(level, center), "九个完整区块柱必须全部进入实体刻");
            })
            .thenExecute(() -> {
                // 加载单位是完整高度区块柱:同一份覆盖同时罩住地下与高空目标
                for (ChunkPos column : ObservationCoverage.columns(center)) {
                    BlockPos deep = column.getMiddleBlockPosition(level.getMinBuildHeight() + 1);
                    BlockPos high = column.getMiddleBlockPosition(level.getMaxBuildHeight() - 1);
                    check(ObservationCoverageService.isWorkable(level, deep), "覆盖内的地下目标必须可派发: " + column);
                    check(ObservationCoverageService.isWorkable(level, high), "覆盖内的高空目标必须可派发: " + column);
                }
                check(!ObservationCoverageService.isWorkable(level, tenth.getMiddleBlockPosition(anchor.getY())),
                    "第十柱不得被这只观察悦灵加载，落在那里的目标必须不可派发");
                // 重叠覆盖按持票人引用计数,撤掉一张票不会让共享柱停刻
                slots[1] = spawnObserver(helper, new Vec3(3.5D, 3.0D, 1.5D), COVERAGE_OWNER);
                pin(helper, slots[1], overlap, anchor.getY());
                check(index.refCount(level.dimension(), overlap) == 2, "重叠柱必须同时被两份覆盖引用");
            })
            .thenExecute(() -> {
                slots[1].discard();
                check(index.refCount(level.dimension(), overlap) == 1, "撤掉一份覆盖后重叠柱必须只剩一份引用");
                check(ObservationChunkLoader.isCoverageTicking(level, center),
                    "重叠覆盖退掉后第一只观察悦灵的九柱必须仍然实体刻");
            })
            .thenExecute(() -> {
                ObservationOwner owner = ObservationOwner.observer(level.dimension(), slots[0].getUUID());
                slots[0].discard();
                check(!index.has(owner), "观察悦灵消失后必须撤掉自己的全部九张票");
            })
            .thenWaitUntil(() -> check(!ObservationChunkLoader.isTicking(level, center),
                "撤票后远处空白区块必须停止实体刻，不留下无主加载区"))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 600, batch = "zzz_observation_live")
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "观察悦灵跨区块时先建立新九柱再延后释放旧票，交接刻新旧柱同时实体刻")
    static void borderHandoverNeverStopsTicking(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ObservationLoadingIndex index = ObservationLoadingIndex.get(level);
        BlockPos anchor = farAnchor(helper, -2048, -4096);
        ChunkPos before = new ChunkPos(anchor);
        ChunkPos after = new ChunkPos(before.x + 1, before.z);
        List<ChunkPos> staleOnly = List.of(
            new ChunkPos(before.x - 1, before.z - 1),
            new ChunkPos(before.x - 1, before.z),
            new ChunkPos(before.x - 1, before.z + 1)
        );
        WorkingAllayEntity[] slot = new WorkingAllayEntity[1];
        helper.startSequence()
            .thenExecute(() -> {
                check(!ObservationChunkLoader.isTicking(level, before),
                    "远处空白区块在搭建场景前必须不是实体刻，否则交接断言不成立");
                level.setChunkForced(before.x, before.z, true);
            })
            .thenWaitUntil(() -> check(ObservationChunkLoader.isTicking(level, before),
                "落脚区块必须先顶成实体刻，观察悦灵才能被挪进去"))
            .thenExecute(() -> {
                slot[0] = spawnObserver(helper, new Vec3(1.5D, 3.0D, 1.5D), BORDER_OWNER);
                pin(helper, slot[0], before, anchor.getY());
                level.setChunkForced(before.x, before.z, false);
            })
            .thenWaitUntil(() -> {
                pin(helper, slot[0], before, anchor.getY());
                check(ObservationChunkLoader.isCoverageTicking(level, before), "跨界之前旧九柱必须已经全部实体刻");
            })
            .thenExecute(() -> {
                // 同刻跨界:先申请新中心九柱、旧柱转入待撤集,因此这一刻新旧共 12 柱都不许掉出实体刻
                pin(helper, slot[0], after, anchor.getY());
                for (ChunkPos column : ObservationCoverage.columns(before)) {
                    check(ObservationChunkLoader.isTicking(level, column),
                        "跨界刻旧覆盖的每一柱都必须仍在实体刻，先撤旧票就会出现空窗: " + column);
                }
                for (ChunkPos column : ObservationCoverage.columns(after)) {
                    check(index.covered(level.dimension(), column), "跨界刻新九柱必须已全部登记: " + column);
                }
            })
            .thenWaitUntil(() -> {
                pin(helper, slot[0], after, anchor.getY());
                check(ObservationChunkLoader.isCoverageTicking(level, after), "新中心的九柱必须全部进入实体刻");
                for (ChunkPos column : staleOnly) {
                    check(!ObservationChunkLoader.isTicking(level, column),
                        "确认新覆盖实体刻后只属于旧覆盖的柱必须被释放: " + column);
                }
            })
            .thenExecute(() -> {
                check(after.equals(index.center(ObservationOwner.observer(level.dimension(), slot[0].getUUID()))),
                    "释放旧票不得把覆盖中心挪回去");
                check(ObservationChunkLoader.isCoverageTicking(level, after), "释放旧票不得影响新九柱的实体刻");
                slot[0].discard();
            })
            .thenWaitUntil(() -> check(!ObservationChunkLoader.isTicking(level, after),
                "观察悦灵消失后新九柱必须一并停刻，不留下无主加载区"))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 600, batch = "zzz_observation_live")
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "下一批目标既无观察覆盖也无其他加载来源时任务停在等待观察者，任一加载来源出现即自行恢复")
    static void waitingObserverHoldsUntilAnyLoadingSource(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos anchor = farAnchor(helper, -4096, -2048);
        ChunkPos target = new ChunkPos(anchor);
        UUID[] jobSlot = new UUID[1];
        helper.startSequence()
            .thenExecute(() -> {
                check(!ObservationChunkLoader.isTicking(level, target),
                    "远处空白区块在启动任务前必须不是实体刻，否则等待观察者不成立");
                jobSlot[0] = startUnclaimedJob(helper, WAIT_OWNER, anchor, "observation-wait");
            })
            .thenWaitUntil(() -> {
                ConstructionJob job = ConstructionJobIndex.get(server).job(jobSlot[0]);
                ConstructionJobProgress progress = ConstructionJobStore.get(server).get(jobSlot[0]);
                check(job != null && progress != null, "断言等待观察者之前任务必须仍在索引中");
                check(job.state() == ConstructionJob.STATE_WAITING_OBSERVER,
                    "没有覆盖也没有其他加载来源时任务必须停在等待观察者状态");
                check(progress.waitReason() == ConstructionWaitReason.OBSERVER,
                    "等待原因必须是 OBSERVER，聊天提示才对得上");
                check(ObservationCoverageService.isWaitingObserver(server, job), "调度器必须认定该任务正在等待观察窗口");
                int wanted = ObservationCoverageService.wantedObservers(server, job);
                check(wanted >= 1 && wanted <= ObservationCoverageService.MAX_WINDOWS,
                    "窗口缺口必须至少一个且不超过单任务上限，前沿才是收缩而不是无限申请: " + wanted);
            })
            .thenExecute(() -> {
                // 蓝图和普通工种绝不自行加载区块:规划期读方块只把区块拉到完整状态,仍然不是实体刻
                check(!ObservationChunkLoader.isTicking(level, target),
                    "规划读取方块状态不得把目标区块顶成实体刻，否则普通工种会自行加载");
                // 玩家等其他加载来源出现后必须直接复用,不额外占用观察悦灵
                level.setChunkForced(target.x, target.z, true);
            })
            .thenWaitUntil(() -> {
                ConstructionJob job = ConstructionJobIndex.get(server).job(jobSlot[0]);
                check(job != null, "断言恢复之前任务必须仍在索引中");
                check(job.state() != ConstructionJob.STATE_WAITING_OBSERVER,
                    "其他加载来源出现后任务必须自行离开等待观察者状态，无需重启");
                check(job.state() != ConstructionJob.STATE_FAILED, "覆盖恢复不得让任务失败");
                check(!ObservationCoverageService.isWaitingObserver(server, job), "已有加载来源时不得继续申请观察窗口");
                check(ObservationLoadingIndex.get(server).leases(jobSlot[0]).isEmpty(),
                    "天然加载来源不得占用观察租约");
            })
            .thenExecute(() -> {
                cancelJobQuietly(server, jobSlot[0]);
                level.setChunkForced(target.x, target.z, false);
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 400, batch = "zzz_observation_live")
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "窗口尚未实体刻时闲置工人结队跟随该窗口的观察悦灵，窗口建立后不再跟随")
    static void idleWorkersConvoyUntilWindowTicks(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ObservationLoadingIndex index = ObservationLoadingIndex.get(level);
        BlockPos anchor = farAnchor(helper, -4096, -4096);
        ChunkPos window = new ChunkPos(anchor);
        UUID jobId = UUID.randomUUID();
        // 只需要 jobId 作为租约分账键,不注册进索引,避免调度器改写本测试植入的租约
        ConstructionJob job = new ConstructionJob(
            jobId,
            CONVOY_OWNER,
            ConstructionJob.STATE_BUILDING,
            "observation-convoy",
            level.dimension(),
            anchor,
            Rotation.NONE,
            Mirror.NONE,
            "observation-convoy",
            new Vec3i(1, 1, 1),
            BlueprintSource.VANILLA_FILE,
            false,
            false
        );
        WorkingAllayEntity observer = spawnObserver(helper, new Vec3(1.5D, 3.0D, 1.5D), CONVOY_OWNER);
        WorkingAllayEntity builder = AllayGameTests.spawnHattedForOwner(
            helper, new Vec3(3.5D, 3.0D, 1.5D), CONVOY_OWNER);
        builder.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.CRAB_CLAW.get()));
        helper.startSequence()
            .thenExecute(() -> {
                check(!ObservationChunkLoader.isTicking(level, window),
                    "远处空白窗口在搭建场景前必须不是实体刻，否则结队跟随不成立");
                index.addLease(jobId, new ObservationLease(level.dimension(), window, observer.getUUID()));
                check(ObservationCoverageService.convoyObserver(level, job, builder) == observer,
                    "窗口尚未实体刻时闲置工人必须结队跟随该窗口的观察悦灵");
                for (ChunkPos column : ObservationCoverage.columns(window)) {
                    level.setChunkForced(column.x, column.z, true);
                }
            })
            .thenWaitUntil(() -> check(ObservationCoverageService.convoyObserver(level, job, builder) == null,
                "窗口整体进入实体刻后工人必须各自进场，不再结队跟随"))
            .thenExecute(() -> {
                for (ChunkPos column : ObservationCoverage.columns(window)) {
                    level.setChunkForced(column.x, column.z, false);
                }
                index.removeLeases(jobId);
                observer.discard();
                builder.discard();
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 400, batch = "zzz_observation_live")
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "窗口还在等人接手时刚出库的观察悦灵原地待命，需求消失后才返库")
    static void launchedObserverWaitsForWindowAssignment(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos anchor = farAnchor(helper, -6144, -6144);
        AllayLoungeBlockEntity lounge = placeLounge(helper, new BlockPos(2, 2, 2), LAUNCH_OWNER);
        UUID[] jobSlot = new UUID[1];
        WorkingAllayEntity[] observerSlot = new WorkingAllayEntity[1];
        helper.startSequence()
            .thenExecute(() -> {
                check(!ObservationChunkLoader.isTicking(level, new ChunkPos(anchor)),
                    "远处空白目标在启动任务前必须不是实体刻，否则窗口缺口不成立");
                jobSlot[0] = startUnclaimedJob(helper, LAUNCH_OWNER, anchor, "observation-launch");
            })
            .thenWaitUntil(() -> {
                ConstructionJob job = ConstructionJobIndex.get(server).job(jobSlot[0]);
                check(job != null, "断言窗口缺口之前任务必须仍在索引中");
                check(ObservationCoverageService.wantedObservers(server, job) >= 1,
                    "现场没有观察悦灵时规划必须报出窗口缺口");
            })
            .thenExecute(() -> {
                // 紧跟报出缺口的那次规划出库:租约要等下一次规划才派发,这段空窗正是回归点
                check(lounge.addHosted(observationRecord(UUID.randomUUID(), LAUNCH_OWNER)),
                    "休息室必须能托管观察记录");
                WorkingAllayEntity observer = lounge.tryLaunchFor(ObservationCoverage::isEligible);
                if (observer == null) throw new GameTestAssertException("休息室必须放出托管的观察悦灵");
                observerSlot[0] = observer;
                check(ObservationLoadingIndex.get(server).leaseOf(observer.getUUID()) == null,
                    "出库当刻还没有租约，否则测不到空窗期");
                check(ObservationCoverageService.awaitsWindow(level, observer),
                    "窗口缺口仍在时刚出库的观察悦灵必须被判定为在等指派");
            })
            .thenExecuteAfter(3, () -> {
                WorkingAllayEntity observer = observerSlot[0];
                check(observer.isAlive(), "等指派的观察悦灵不得消失");
                check(observer.flightState() != AllayFlightState.DOCKING,
                    "窗口还在等它接手时不得掉头返库，一进一出各占一次 20 gt 通道会让任务永远等不到观察者");
                check(lounge.hosted().isEmpty(), "等指派期间观察记录不得回到托管栈");
            })
            .thenExecute(() -> cancelJobQuietly(server, jobSlot[0]))
            .thenWaitUntil(() -> check(lounge.hosted().size() == 1,
                "窗口需求消失后观察悦灵必须自行返库，而不是留在世界里空等"))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20, batch = "zzz_observation")
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "重启只按世界级索引补一次票：覆盖中心落盘且重复补票不加引用，观察租约不落盘")
    static void restartReissuesCoverageOnceWithoutKeepingLeases(ExtendedGameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ObservationLoadingIndex index = ObservationLoadingIndex.get(level);
        UUID observerId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        ObservationOwner owner = ObservationOwner.observer(level.dimension(), observerId);
        ChunkPos center = new ChunkPos(helper.absolutePos(new BlockPos(1, 2, 1)));
        try {
            check(index.putCoverage(owner, center), "首次登记覆盖必须写入索引");
            check(!index.putCoverage(owner, center), "重启补票时同一持票人的同一份覆盖不得再写第二次");
            check(
                index.refCount(level.dimension(), center) == 1,
                "重复补票后引用计数必须仍是 1，否则重启一次就会多出一份无主加载区，实际为 "
                    + index.refCount(level.dimension(), center)
            );
            index.addLease(jobId, new ObservationLease(level.dimension(), center, observerId));
            CompoundTag saved = index.save(new CompoundTag(), level.registryAccess());
            ListTag coverage = saved.getList("Coverage", Tag.TAG_COMPOUND);
            check(
                coverage.size() == index.owners().size(),
                "覆盖记录必须逐条落盘：重启首刻要靠它补票，指望悦灵自己唤醒自己会死锁"
            );
            boolean stored = false;
            for (int entry = 0; entry < coverage.size(); entry++) {
                CompoundTag row = coverage.getCompound(entry);
                if (row.hasUUID("Entity")
                    && observerId.equals(row.getUUID("Entity"))
                    && row.getLong("Center") == center.toLong()) {
                    stored = true;
                }
            }
            check(stored, "落盘的覆盖记录必须包含该持票人的维度与覆盖中心");
            check(
                !saved.contains("Leases"),
                "观察租约不得落盘：它只是运行时调度结论，留着会让重启后的任务守住持票人可能已经消失的旧租约"
            );
            check(
                index.leaseOf(observerId) != null,
                "本次运行中的租约仍必须可以按观察者反查，落盘与否是两回事"
            );
        } finally {
            index.removeLeases(jobId);
            index.removeCoverage(owner);
        }
        helper.succeed();
    }

    /**
     * 远处空白世界里的锚点。GameTest 网格总是从同一角落起算并只朝 +X/+Z 生长,
     * 因此 -X/-Z 方向永远是没有测试模板的干净区域;偏移在 absolutePos 之后按世界坐标加,
     * 不受模板旋转影响。
     */
    private static BlockPos farAnchor(ExtendedGameTestHelper helper, int offsetX, int offsetZ) {
        BlockPos origin = helper.absolutePos(new BlockPos(0, 2, 0));
        return new BlockPos(origin.getX() + offsetX, origin.getY(), origin.getZ() + offsetZ);
    }

    /** 把观察悦灵钉在指定区块柱中心并同步覆盖;原版游荡会让它慢慢漂走,断言前必须重新钉住。 */
    private static void pin(ExtendedGameTestHelper helper, WorkingAllayEntity observer, ChunkPos center, int y) {
        observer.moveTo(new Vec3(center.getMiddleBlockX() + 0.5D, y, center.getMiddleBlockZ() + 0.5D));
        ObservationChunkLoader.syncObserver(observer);
        ServerLevel level = helper.getLevel();
        ObservationOwner owner = ObservationOwner.observer(level.dimension(), observer.getUUID());
        check(center.equals(ObservationLoadingIndex.get(level).center(owner)),
            "覆盖中心必须跟随观察悦灵所在区块柱: " + center);
    }

    private static WorkingAllayEntity spawnObserver(ExtendedGameTestHelper helper, Vec3 relativePos, UUID owner) {
        WorkingAllayEntity worker = AllayGameTests.spawnHattedForOwner(helper, relativePos, owner);
        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SPYGLASS));
        return worker;
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

    private static ObservationLease requireLease(ObservationLoadingIndex index, UUID observer) {
        ObservationLease lease = index.leaseOf(observer);
        if (lease == null) throw new GameTestAssertException("观察者必须能反查到自己的租约");
        return lease;
    }

    /** 服务器纯态启动一份无休息室任务:等待观察者的判定发生在取料之前,因此不需要材料也不需要工人。 */
    private static UUID startUnclaimedJob(
        ExtendedGameTestHelper helper, UUID owner, BlockPos absoluteAnchor, String name
    ) {
        MinecraftServer server = helper.getLevel().getServer();
        ItemStack disk = new ItemStack(ModItems.STRUCTURE_DISK.get());
        ConstructionBlueprintData data;
        try {
            ConstructionBlueprintService.importIntoDisk(server, disk, cobbleStructure(), name, BlueprintSource.VANILLA_FILE);
            data = ConstructionBlueprintData.get(disk).orElseThrow();
        } catch (ConstructionBlueprintException exception) {
            throw new GameTestAssertException("导入测试蓝图失败: " + exception.getMessage());
        }
        UUID jobId = UUID.randomUUID();
        ConstructionJob job = new ConstructionJob(
            jobId,
            owner,
            ConstructionJob.STATE_INACTIVE,
            data.hash(),
            helper.getLevel().dimension(),
            absoluteAnchor,
            Rotation.NONE,
            Mirror.NONE,
            data.name(),
            data.size(),
            data.source(),
            data.hasBlockEntities(),
            data.hasEntities()
        );
        ConstructionJobIndex.get(server).put(job);
        ConstructionBlueprintService.start(server, jobId);
        check(ConstructionJobStore.get(server).get(jobId).planned(), "启动后任务必须已完成规划");
        return jobId;
    }

    private static void cancelJobQuietly(MinecraftServer server, UUID jobId) {
        ConstructionJob job = ConstructionJobIndex.get(server).job(jobId);
        if (job != null) {
            ConstructionJobController.cancel(server, job);
        }
    }

    /** 单块圆石蓝图:等待观察者只需要一个待办目标。 */
    private static CompoundTag cobbleStructure() {
        CompoundTag tag = new CompoundTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(1));
        size.add(IntTag.valueOf(1));
        size.add(IntTag.valueOf(1));
        tag.put("size", size);
        ListTag palette = new ListTag();
        CompoundTag cobble = new CompoundTag();
        cobble.putString("Name", "minecraft:cobblestone");
        palette.add(cobble);
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        CompoundTag entry = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(0));
        pos.add(IntTag.valueOf(0));
        pos.add(IntTag.valueOf(0));
        entry.put("pos", pos);
        entry.putInt("state", 0);
        blocks.add(entry);
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        return tag;
    }

    private static AllayWorkRecord observationRecord(UUID entityId, UUID owner) {
        return recordWithTool(entityId, owner, Items.SPYGLASS, AllayDefaultHardHat.stack());
    }

    private static AllayWorkRecord hatlessObservationRecord(UUID entityId, UUID owner) {
        return recordWithTool(entityId, owner, Items.SPYGLASS, ItemStack.EMPTY);
    }

    private static AllayWorkRecord plainRecord(UUID entityId, UUID owner) {
        return recordWithTool(entityId, owner, ModItems.CRAB_CLAW.get(), AllayDefaultHardHat.stack());
    }

    private static AllayWorkRecord recordWithTool(UUID entityId, UUID owner, Item tool, ItemStack hardHat) {
        return new AllayWorkRecord(
            entityId,
            hardHat,
            new ItemStack(tool),
            Optional.of(owner),
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
