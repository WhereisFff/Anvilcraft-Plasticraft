package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.blueprint.BlueprintPlacement;
import dev.anvilcraft.plasticraft.blueprint.BlueprintSource;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintData;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintException;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintService;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBuildOp;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobProgress;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobStore;
import dev.anvilcraft.plasticraft.blueprint.ConstructionPermission;
import dev.anvilcraft.plasticraft.blueprint.ConstructionStructureLibrary;
import dev.anvilcraft.plasticraft.blueprint.LitematicaImporter;
import dev.anvilcraft.plasticraft.blueprint.ScannerDiskImporter;
import dev.anvilcraft.plasticraft.blueprint.StructureSnapshot;
import dev.anvilcraft.plasticraft.blueprint.StructureSnapshotCodec;
import dev.anvilcraft.plasticraft.init.PlasticraftMenuTypes;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.inventory.AllayLoungeMenu;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.item.property.component.StructureDiskData;
import dev.dubhe.anvilcraft.util.StructureLoadUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 覆盖施工蓝图的数据层与服务层:规范快照哈希的来源无关性、导入校验的显式报错、
 * 结构方块模板导入、扫描器坐标归一化、部署生命周期与每玩家单活动约束。
 * 告示牌/熔岩/实体进入快照后由客户端投影渲染,本类只锁数据契约;红石粉连接方向在规范化时写入快照。
 */
public final class BlueprintConstructionGameTests {
    private BlueprintConstructionGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    /** 同一内容打乱方块顺序与调色板编号后,规范化解析仍产生相同哈希。 */
    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Canonical snapshot hash is independent of entry order and palette numbering")
    static void canonicalHashIsOrderInvariant(ExtendedGameTestHelper helper) {
        CompoundTag first = sampleStructureNbt(false);
        CompoundTag second = sampleStructureNbt(true);
        try {
            StructureSnapshotCodec.ParsedSnapshot parsedFirst = StructureSnapshotCodec.parse(
                first,
                helper.getLevel().registryAccess()
            );
            StructureSnapshotCodec.ParsedSnapshot parsedSecond = StructureSnapshotCodec.parse(
                second,
                helper.getLevel().registryAccess()
            );
            String hashFirst = StructureSnapshotCodec.hash(StructureSnapshotCodec.write(parsedFirst.snapshot()));
            String hashSecond = StructureSnapshotCodec.hash(StructureSnapshotCodec.write(parsedSecond.snapshot()));
            check(hashFirst.equals(hashSecond), "reordered structure produced a different canonical hash");
            check(ConstructionStructureLibrary.isValidHash(hashFirst), "canonical hash is not a sha-256 hex string");

            // 规范 NBT 再解析仍得到相同哈希,保证写出与解析互逆。
            CompoundTag canonical = StructureSnapshotCodec.write(parsedFirst.snapshot());
            StructureSnapshotCodec.ParsedSnapshot reparsed = StructureSnapshotCodec.parse(
                canonical,
                helper.getLevel().registryAccess()
            );
            check(
                StructureSnapshotCodec.hash(StructureSnapshotCodec.write(reparsed.snapshot())).equals(hashFirst),
                "canonical NBT did not round-trip to the same hash"
            );
        } catch (ConstructionBlueprintException exception) {
            throw new GameTestAssertException("sample structure failed to parse: " + exception.reason());
        }
        helper.succeed();
    }

    /** 两份内容:一份基准排序,一份打乱方块顺序并调换调色板编号。 */
    private static CompoundTag sampleStructureNbt(boolean shuffled) {
        CompoundTag tag = new CompoundTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(2));
        size.add(IntTag.valueOf(2));
        size.add(IntTag.valueOf(1));
        tag.put("size", size);

        ListTag palette = new ListTag();
        CompoundTag stone = new CompoundTag();
        stone.putString("Name", "minecraft:stone");
        CompoundTag glass = new CompoundTag();
        glass.putString("Name", "minecraft:glass");
        if (shuffled) {
            palette.add(glass);
            palette.add(stone);
        } else {
            palette.add(stone);
            palette.add(glass);
        }
        tag.put("palette", palette);

        ListTag blocks = new ListTag();
        int stoneIndex = shuffled ? 1 : 0;
        int glassIndex = shuffled ? 0 : 1;
        CompoundTag stoneBlock = blockEntry(0, 0, 0, stoneIndex);
        CompoundTag glassBlock = blockEntry(1, 1, 0, glassIndex);
        if (shuffled) {
            blocks.add(glassBlock);
            blocks.add(stoneBlock);
        } else {
            blocks.add(stoneBlock);
            blocks.add(glassBlock);
        }
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        return tag;
    }

    private static CompoundTag blockEntry(int x, int y, int z, int state) {
        CompoundTag entry = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(x));
        pos.add(IntTag.valueOf(y));
        pos.add(IntTag.valueOf(z));
        entry.put("pos", pos);
        entry.putInt("state", state);
        return entry;
    }

    /** 损坏与超限文件按原因显式报错,未知方块列出命名空间提示。 */
    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Import validation reports explicit reasons instead of silently degrading")
    static void importValidationReportsExplicitReasons(ExtendedGameTestHelper helper) {
        CompoundTag oversized = sampleStructureNbt(false);
        ListTag hugeSize = new ListTag();
        hugeSize.add(IntTag.valueOf(600));
        hugeSize.add(IntTag.valueOf(1));
        hugeSize.add(IntTag.valueOf(1));
        oversized.put("size", hugeSize);
        expectReason(helper, oversized, "oversized");

        CompoundTag unknownBlock = sampleStructureNbt(false);
        unknownBlock.getList("palette", Tag.TAG_COMPOUND).getCompound(0).putString("Name", "missing_mod:widget");
        expectReason(helper, unknownBlock, "unknown_block");

        CompoundTag badState = sampleStructureNbt(false);
        badState.getList("blocks", Tag.TAG_COMPOUND).getCompound(0).putInt("state", 7);
        expectReason(helper, badState, "corrupt_palette");

        CompoundTag outOfBounds = sampleStructureNbt(false);
        ListTag badPos = new ListTag();
        badPos.add(IntTag.valueOf(5));
        badPos.add(IntTag.valueOf(0));
        badPos.add(IntTag.valueOf(0));
        outOfBounds.getList("blocks", Tag.TAG_COMPOUND).getCompound(0).put("pos", badPos);
        expectReason(helper, outOfBounds, "position_out_of_bounds");
        helper.succeed();
    }

    private static void expectReason(ExtendedGameTestHelper helper, CompoundTag tag, String reason) {
        try {
            StructureSnapshotCodec.parse(tag, helper.getLevel().registryAccess());
        } catch (ConstructionBlueprintException exception) {
            check(
                exception.reason().equals(reason),
                "expected reason " + reason + " but got " + exception.reason() + ": " + exception.detail()
            );
            return;
        }
        throw new GameTestAssertException("corrupt structure parsed without error, expected " + reason);
    }

    /** 世界模板(含箱子内容物与展示框实体)导入磁盘:摘要、结构库与内容完整性。 */
    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "4x4x4", floor = true)
    @TestHolder(description = "A world template with block entities and entities imports onto a disk")
    static void worldTemplateImportsOntoDisk(ExtendedGameTestHelper helper) {
        BlockPos chestPos = new BlockPos(1, 2, 1);
        helper.setBlock(chestPos, Blocks.CHEST);
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(chestPos)) instanceof ChestBlockEntity chest)) {
            throw new GameTestAssertException("chest block entity missing");
        }
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        ItemFrame frame = new ItemFrame(
            helper.getLevel(),
            helper.absolutePos(new BlockPos(1, 2, 2)),
            Direction.SOUTH
        );
        helper.getLevel().addFreshEntity(frame);

        helper.startSequence().thenExecuteAfter(5, () -> {
            StructureTemplate template = new StructureTemplate();
            template.fillFromWorld(
                helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 2, 1)),
                new Vec3i(1, 1, 2),
                true,
                null
            );
            CompoundTag tag = template.save(new CompoundTag());
            ItemStack disk = new ItemStack(ModItems.STRUCTURE_DISK.get());
            disk.set(
                ModComponents.STRUCTURE_DISK_DATA,
                new StructureDiskData(
                    "old_scan_00000000-0000-0000-0000-000000000000.nbt",
                    "structure_1786621645655",
                    UUID.fromString("00000000-0000-0000-0000-000000000000"),
                    Direction.SOUTH,
                    9,
                    9,
                    9,
                    true
                )
            );
            try {
                ConstructionBlueprintService.ImportResult result = ConstructionBlueprintService.importIntoDisk(
                    helper.getLevel().getServer(),
                    disk,
                    tag,
                    "test_template",
                    BlueprintSource.VANILLA_TEMPLATE
                );
                ConstructionBlueprintData data = result.data();
                check(data.size().equals(new Vec3i(1, 1, 2)), "imported size mismatch: " + data.size());
                check(data.hasBlockEntities(), "chest NBT was lost during import");
                check(data.hasEntities(), "item frame entity was lost during import");
                check(
                    ConstructionStructureLibrary.exists(helper.getLevel().getServer(), data.hash()),
                    "structure library file was not written"
                );
                check(
                    ConstructionBlueprintData.get(disk).isPresent(),
                    "blueprint component was not written to the disk"
                );
                StructureDiskData vanilla = disk.get(ModComponents.STRUCTURE_DISK_DATA);
                check(vanilla != null, "vanilla structure disk data missing after import");
                check(vanilla.name().equals("test_template"), "structure name was " + vanilla.name());
                check(
                    vanilla.sizeX() == 1 && vanilla.sizeY() == 1 && vanilla.sizeZ() == 2,
                    "vanilla size mismatch: " + vanilla.sizeX() + "x" + vanilla.sizeY() + "x" + vanilla.sizeZ()
                );
                check(
                    vanilla.direction() == Direction.NORTH && !vanilla.upsideDown(),
                    "scanner facing leaked into the blueprint disk"
                );
                StructureLoadUtil.StructureData loaded = StructureLoadUtil.loadStructureFromDisk(
                    helper.getLevel(),
                    disk
                );
                check(
                    loaded != null && !loaded.isEmpty(),
                    "vanilla structure file could not be loaded for preview or smart block placer"
                );

                // 结构库中的规范内容能重新解析,方块实体数据仍在。
                CompoundTag stored = ConstructionStructureLibrary.load(helper.getLevel().getServer(), data.hash());
                StructureSnapshotCodec.ParsedSnapshot reparsed = StructureSnapshotCodec.parse(
                    stored,
                    helper.getLevel().registryAccess()
                );
                check(reparsed.snapshot().hasBlockEntities(), "stored snapshot lost block entity NBT");
                check(reparsed.snapshot().hasEntities(), "stored snapshot lost entities");
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException(
                    "import failed: " + exception.reason() + " " + exception.detail()
                );
            }
        }).thenSucceed();
    }

    /** 扫描器预览空间在四朝向与上下翻转下归一化回世界对齐坐标。 */
    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Scanner preview coordinates normalize back to world alignment")
    static void scannerNormalizationRestoresWorldAlignment(ExtendedGameTestHelper helper) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        // 预览空间尺寸 (2,2,3),唯一方块位于预览 (1,0,2),实体位于预览 (1.5,0.5,2.5)。
        StructureSnapshot preview = new StructureSnapshot(
            new Vec3i(2, 2, 3),
            List.of(stone),
            List.of(new StructureSnapshot.BlockEntry(new BlockPos(1, 0, 2), 0, Optional.empty())),
            List.of(new StructureSnapshot.EntityEntry(
                new Vec3(1.5D, 0.5D, 2.5D),
                new BlockPos(1, 0, 2),
                new CompoundTag()
            ))
        );

        StructureSnapshot north = ScannerDiskImporter.normalize(preview, Direction.NORTH, false);
        check(north.size().equals(new Vec3i(2, 2, 3)), "north normalization changed size");
        check(
            north.blocks().getFirst().pos().equals(new BlockPos(1, 0, 2)),
            "north normalization moved the block"
        );

        StructureSnapshot south = ScannerDiskImporter.normalize(preview, Direction.SOUTH, false);
        check(
            south.blocks().getFirst().pos().equals(new BlockPos(0, 0, 0)),
            "south normalization wrong block pos: " + south.blocks().getFirst().pos().toShortString()
        );
        check(
            south.entities().getFirst().pos().equals(new Vec3(0.5D, 0.5D, 0.5D)),
            "south normalization wrong entity pos: " + south.entities().getFirst().pos()
        );

        StructureSnapshot west = ScannerDiskImporter.normalize(preview, Direction.WEST, false);
        check(west.size().equals(new Vec3i(3, 2, 2)), "west normalization did not swap axes");
        check(
            west.blocks().getFirst().pos().equals(new BlockPos(2, 0, 0)),
            "west normalization wrong block pos: " + west.blocks().getFirst().pos().toShortString()
        );

        StructureSnapshot east = ScannerDiskImporter.normalize(preview, Direction.EAST, true);
        check(east.size().equals(new Vec3i(3, 2, 2)), "east normalization did not swap axes");
        check(
            east.blocks().getFirst().pos().equals(new BlockPos(0, 1, 1)),
            "east+upsideDown wrong block pos: " + east.blocks().getFirst().pos().toShortString()
        );
        check(
            east.entities().getFirst().pos().equals(new Vec3(0.5D, 1.5D, 1.5D)),
            "east+upsideDown wrong entity pos: " + east.entities().getFirst().pos()
        );
        helper.succeed();
    }

    /** 放置变换与包围盒:旋转 90 度交换水平尺寸,镜像与原版模板语义一致。 */
    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Placement math matches vanilla template transform semantics")
    static void placementBoundsFollowVanillaTransforms(ExtendedGameTestHelper helper) {
        Vec3i size = new Vec3i(3, 2, 5);
        BlockPos anchor = new BlockPos(10, 64, -20);
        BoundingBox plain = new BlueprintPlacement(anchor, Rotation.NONE, Mirror.NONE).bounds(size);
        check(plain.getXSpan() == 3 && plain.getYSpan() == 2 && plain.getZSpan() == 5, "plain bounds wrong");
        check(plain.minX() == anchor.getX() && plain.minY() == anchor.getY() && plain.minZ() == anchor.getZ(),
            "plain bounds must start at the anchor");

        BoundingBox rotated = new BlueprintPlacement(anchor, Rotation.CLOCKWISE_90, Mirror.NONE).bounds(size);
        check(rotated.getXSpan() == 5 && rotated.getZSpan() == 3, "rotated bounds did not swap spans");

        BlueprintPlacement mirrored = new BlueprintPlacement(anchor, Rotation.NONE, Mirror.LEFT_RIGHT);
        BlockPos mirroredPos = mirrored.worldOf(new BlockPos(1, 0, 2));
        check(
            mirroredPos.equals(anchor.offset(1, 0, -2)),
            "left-right mirror should negate Z: " + mirroredPos.toShortString()
        );

        BlueprintPlacement rotatedPlacement = new BlueprintPlacement(anchor, Rotation.CLOCKWISE_90, Mirror.NONE);
        BlockPos snapshotLocal = new BlockPos(2, 1, 1);
        check(
            rotatedPlacement.worldOf(snapshotLocal).equals(rotatedPlacement.localOf(snapshotLocal).offset(anchor)),
            "worldOf must equal localOf plus the anchor"
        );

        // 矿车等实体必须留在变换后方块格内;原版 Vec3 transform 会把格内小数甩到邻格。
        Vec3 minecart = new Vec3(2.5D, 0.0625D, 3.5D);
        BlockPos minecartBlock = new BlockPos(2, 0, 3);
        for (Rotation rotation : Rotation.values()) {
            for (Mirror mirror : Mirror.values()) {
                BlueprintPlacement placement = new BlueprintPlacement(BlockPos.ZERO, rotation, mirror);
                Vec3 local = placement.localOf(minecart, minecartBlock);
                check(
                    BlockPos.containing(local).equals(placement.localOf(minecartBlock)),
                    "entity local " + local + " left block " + placement.localOf(minecartBlock)
                        + " after " + rotation + "/" + mirror
                );
            }
        }
        helper.succeed();
    }

    /** 部署生命周期:创建任务、锚点移动、启动切换、单活动约束与取消清理。 */
    @GameTest(timeoutTicks = 60, batch = "zzz_blueprint_lifecycle")
    @EmptyTemplate(value = "4x4x4", floor = true)
    @TestHolder(description = "Deploy, move, single-active toggle and cancel keep index and disk consistent")
    static void deployLifecycleKeepsSingleActiveJob(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                ItemStack firstDisk = importSampleDisk(helper, "first");
                ItemStack secondDisk = importSampleDisk(helper, "second");
                player.setItemInHand(InteractionHand.MAIN_HAND, firstDisk);

                BlockPos anchor = helper.absolutePos(new BlockPos(1, 2, 1));
                ConstructionJob job = ConstructionBlueprintService.deploy(
                    player,
                    InteractionHand.MAIN_HAND,
                    anchor,
                    Rotation.NONE,
                    Mirror.NONE
                );
                ConstructionJobIndex index = ConstructionJobIndex.get(helper.getLevel().getServer());
                check(index.job(job.jobId()) != null, "deployed job missing from the index");
                ItemStack heldFirst = player.getItemInHand(InteractionHand.MAIN_HAND);
                check(
                    ConstructionBlueprintData.get(heldFirst).flatMap(ConstructionBlueprintData::jobId)
                        .map(job.jobId()::equals).orElse(false),
                    "disk did not record the job id"
                );

                // 再次部署同一磁盘只移动锚点,不产生新任务。
                BlockPos movedAnchor = anchor.east(3);
                ConstructionJob moved = ConstructionBlueprintService.deploy(
                    player,
                    InteractionHand.MAIN_HAND,
                    movedAnchor,
                    Rotation.CLOCKWISE_90,
                    Mirror.NONE
                );
                check(moved.jobId().equals(job.jobId()), "moving the anchor created a new job");
                check(moved.anchor().equals(movedAnchor), "anchor move was not applied");
                check(moved.rotation() == Rotation.CLOCKWISE_90, "rotation was not applied");
                check(index.job(job.jobId()) != null, "moving the blueprint removed its job from the index");

                try {
                    ConstructionBlueprintService.deploy(
                        player,
                        InteractionHand.MAIN_HAND,
                        new BlockPos(anchor.getX(), helper.getLevel().getMaxBuildHeight(), anchor.getZ()),
                        Rotation.NONE,
                        Mirror.NONE
                    );
                    throw new GameTestAssertException("moving a blueprint above build height was accepted");
                } catch (ConstructionBlueprintException exception) {
                    check(exception.reason().equals("placement_out_of_world"),
                        "unexpected out-of-world placement reason: " + exception.reason());
                }

                // 启动切换与每玩家单活动:启动第二份会暂停第一份。
                check(
                    ConstructionBlueprintService.toggleActive(player, job.jobId()),
                    "first toggle should start the job"
                );
                check(index.job(job.jobId()).isActive(), "job did not become active");

                player.setItemInHand(InteractionHand.MAIN_HAND, secondDisk);
                try {
                    ConstructionBlueprintService.deploy(
                        player,
                        InteractionHand.MAIN_HAND,
                        moved.anchor(),
                        moved.rotation(),
                        moved.mirror()
                    );
                    throw new GameTestAssertException("overlapping deployed blueprints were accepted");
                } catch (ConstructionBlueprintException exception) {
                    check(exception.reason().equals("placement_overlaps_job"),
                        "unexpected overlapping placement reason: " + exception.reason());
                }
                ConstructionJob secondJob = ConstructionBlueprintService.deploy(
                    player,
                    InteractionHand.MAIN_HAND,
                    anchor.south(3),
                    Rotation.NONE,
                    Mirror.FRONT_BACK
                );
                ConstructionBlueprintService.start(player, secondJob.jobId());
                check(index.job(secondJob.jobId()).isActive(), "second job did not start");
                check(!index.job(job.jobId()).isActive(), "starting the second job must pause the first");
                check(
                    index.activeJobOf(player.getUUID()).map(active -> active.jobId().equals(secondJob.jobId()))
                        .orElse(false),
                    "active job lookup did not return the second job"
                );

                ConstructionJobProgress oldPlan = ConstructionJobStore.get(helper.getLevel().getServer())
                    .get(job.jobId());
                check(oldPlan != null && oldPlan.planned() && !oldPlan.operations().isEmpty(),
                    "paused first job must retain its original coordinate plan before moving");
                player.setItemInHand(InteractionHand.MAIN_HAND, heldFirst);
                BlockPos replannedAnchor = anchor.west();
                ConstructionBlueprintService.deploy(
                    player,
                    InteractionHand.MAIN_HAND,
                    replannedAnchor,
                    Rotation.NONE,
                    Mirror.NONE
                );
                ConstructionJobProgress resetPlan = ConstructionJobStore.get(helper.getLevel().getServer())
                    .get(job.jobId());
                check(resetPlan != null && !resetPlan.planned() && resetPlan.operations().isEmpty(),
                    "moving a paused untouched job must discard its old coordinate plan");
                ConstructionBlueprintService.start(player, job.jobId());
                ConstructionJobProgress newPlan = ConstructionJobStore.get(helper.getLevel().getServer())
                    .get(job.jobId());
                check(newPlan != null && newPlan.operations().stream()
                        .filter(op -> op.kind() == ConstructionBuildOp.Kind.PLACE)
                        .anyMatch(op -> op.pos().equals(replannedAnchor)),
                    "restarted job did not rebuild operations at the moved anchor");

                // 取消清理任务条目并清除手中磁盘的引用。
                player.setItemInHand(InteractionHand.MAIN_HAND, secondDisk);
                ConstructionBlueprintService.cancel(player, secondJob.jobId());
                check(index.job(secondJob.jobId()) == null, "cancelled job still present");
                check(
                    ConstructionBlueprintData.get(player.getItemInHand(InteractionHand.MAIN_HAND))
                        .flatMap(ConstructionBlueprintData::jobId).isEmpty(),
                    "cancel did not clear the disk job reference"
                );

                // 其他玩家不能操作这份任务。
                GameTestPlayer stranger = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
                try {
                    ConstructionBlueprintService.toggleActive(stranger, job.jobId());
                    throw new GameTestAssertException("stranger toggled a job they do not own");
                } catch (ConstructionBlueprintException exception) {
                    check(exception.reason().equals("not_owner"), "unexpected reason: " + exception.reason());
                }

                try {
                    ConstructionPermission.setCollaboratorProvider((server, first, second) ->
                        first.equals(player.getUUID()) && second.equals(stranger.getUUID())
                            || first.equals(stranger.getUUID()) && second.equals(player.getUUID())
                    );
                    check(
                        !ConstructionBlueprintService.toggleActive(stranger, job.jobId()),
                        "a current teammate must be able to pause the owner's job"
                    );
                } finally {
                    ConstructionPermission.setCollaboratorProvider(null);
                }
                try {
                    ConstructionBlueprintService.toggleActive(stranger, job.jobId());
                    throw new GameTestAssertException("a former teammate retained job access");
                } catch (ConstructionBlueprintException exception) {
                    check(exception.reason().equals("not_owner"), "unexpected former teammate reason: "
                        + exception.reason());
                }

                ConstructionBlueprintService.cancel(player, job.jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException(
                    "lifecycle failed: " + exception.reason() + " " + exception.detail()
                );
            }
        }).thenSucceed();
    }

    /** 未部署但手持磁盘的哈希允许读取结构库,随机哈希拒绝,部署后仍可读。 */
    @GameTest(timeoutTicks = 40, batch = "zzz_blueprint_snapshot")
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Snapshot reads are allowed for a held imported disk before and after deploy")
    static void snapshotReadableFromHeldDiskBeforeDeploy(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                ItemStack disk = importSampleDisk(helper, "preview");
                String hash = ConstructionBlueprintData.get(disk)
                    .map(ConstructionBlueprintData::hash)
                    .orElseThrow(() -> new GameTestAssertException("imported disk has no hash"));
                player.setItemInHand(InteractionHand.MAIN_HAND, disk);
                check(
                    ConstructionBlueprintService.canReadSnapshot(player, hash),
                    "held imported disk must be readable before deploy so the placement preview can load"
                );
                check(
                    !ConstructionBlueprintService.canReadSnapshot(player, "a".repeat(64)),
                    "an unrelated hash must not be readable"
                );

                ConstructionJob job = ConstructionBlueprintService.deploy(
                    player,
                    InteractionHand.MAIN_HAND,
                    helper.absolutePos(new BlockPos(1, 2, 1)),
                    Rotation.NONE,
                    Mirror.NONE
                );
                check(
                    ConstructionBlueprintService.canReadSnapshot(player, job.hash()),
                    "deployed job hash must remain readable"
                );

                GameTestPlayer teammate = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
                GameTestPlayer stranger = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
                ItemStack deployedDisk = player.getItemInHand(InteractionHand.MAIN_HAND).copy();
                stranger.setItemInHand(InteractionHand.MAIN_HAND, deployedDisk.copy());
                check(
                    !ConstructionBlueprintService.canReadSnapshot(stranger, job.hash()),
                    "a stranger must not read an owner's deployed snapshot"
                );
                try {
                    ConstructionPermission.setCollaboratorProvider((server, first, second) ->
                        first.equals(player.getUUID()) && second.equals(teammate.getUUID())
                            || first.equals(teammate.getUUID()) && second.equals(player.getUUID())
                    );
                    check(
                        ConstructionBlueprintService.canReadSnapshot(teammate, job.hash()),
                        "a current teammate must read the owner's deployed snapshot"
                    );
                    check(
                        ConstructionBlueprintService.canReadSnapshot(teammate, hash),
                        "a current teammate must read the owner's deployed snapshot by hash"
                    );
                } finally {
                    ConstructionPermission.setCollaboratorProvider(null);
                }
                check(
                    !ConstructionBlueprintService.canReadSnapshot(teammate, job.hash()),
                    "a former teammate must lose snapshot access immediately"
                );
                ConstructionBlueprintService.cancel(player, job.jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException(
                    "snapshot access failed: " + exception.reason() + " " + exception.detail()
                );
            }
        }).thenSucceed();
    }

    /** 对已部署磁盘覆盖导入会移除旧投影,磁盘不再引用被删任务。 */
    @GameTest(timeoutTicks = 40, batch = "zzz_blueprint_reimport")
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Re-importing a deployed disk removes the previous world projection")
    static void reimportingDiskRemovesPreviousDeployment(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.startSequence().thenExecuteAfter(5, () -> {
            try {
                ItemStack disk = importSampleDisk(helper, "first");
                player.setItemInHand(InteractionHand.MAIN_HAND, disk);
                ConstructionJob job = ConstructionBlueprintService.deploy(
                    player,
                    InteractionHand.MAIN_HAND,
                    helper.absolutePos(new BlockPos(1, 2, 1)),
                    Rotation.NONE,
                    Mirror.NONE
                );
                ConstructionJobIndex index = ConstructionJobIndex.get(helper.getLevel().getServer());
                check(index.job(job.jobId()) != null, "deployed job missing before reimport");

                CompoundTag replacement = sampleStructureNbt(false);
                replacement.getList("blocks", Tag.TAG_COMPOUND).remove(1);
                ConstructionBlueprintService.importIntoDisk(
                    helper.getLevel().getServer(),
                    player.getItemInHand(InteractionHand.MAIN_HAND),
                    replacement,
                    "replacement",
                    BlueprintSource.VANILLA_FILE
                );
                check(index.job(job.jobId()) == null, "reimport left the previous projection in the world");
                check(
                    ConstructionBlueprintData.get(player.getItemInHand(InteractionHand.MAIN_HAND))
                        .flatMap(ConstructionBlueprintData::jobId)
                        .isEmpty(),
                    "reimport must not keep the deleted job id on the disk"
                );
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException(
                    "reimport cleanup failed: " + exception.reason() + " " + exception.detail()
                );
            }
        }).thenSucceed();
    }

    private static ItemStack importSampleDisk(ExtendedGameTestHelper helper, String name)
        throws ConstructionBlueprintException {
        ItemStack disk = new ItemStack(ModItems.STRUCTURE_DISK.get());
        CompoundTag tag = sampleStructureNbt(false);
        if (!name.equals("first")) {
            // 让第二份内容不同,得到不同哈希。
            tag.getList("blocks", Tag.TAG_COMPOUND).remove(1);
        }
        ConstructionBlueprintService.importIntoDisk(
            helper.getLevel().getServer(),
            disk,
            tag,
            name,
            BlueprintSource.VANILLA_FILE
        );
        return disk;
    }

    /** 多区域(含负尺寸轴)Litematica 与等价原版稠密 NBT 产生相同规范哈希。 */
    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "A multi-region litematic converts to the same canonical hash as vanilla NBT")
    static void litematicMatchesVanillaCanonicalHash(ExtendedGameTestHelper helper) {
        try {
            LitematicaImporter.ConvertedStructure converted = LitematicaImporter.convert(sampleLitematic(false));
            check(converted.warnings().isEmpty(), "clean litematic produced warnings: " + converted.warnings());
            StructureSnapshotCodec.ParsedSnapshot fromLitematic = StructureSnapshotCodec.parse(
                converted.structureTag(),
                helper.getLevel().registryAccess()
            );
            StructureSnapshotCodec.ParsedSnapshot fromVanilla = StructureSnapshotCodec.parse(
                equivalentVanillaStructure(),
                helper.getLevel().registryAccess()
            );
            String litematicHash = StructureSnapshotCodec.hash(StructureSnapshotCodec.write(fromLitematic.snapshot()));
            String vanillaHash = StructureSnapshotCodec.hash(StructureSnapshotCodec.write(fromVanilla.snapshot()));
            check(
                litematicHash.equals(vanillaHash),
                "litematic hash " + litematicHash + " differs from vanilla hash " + vanillaHash
            );
            check(fromLitematic.snapshot().hasBlockEntities(), "litematic chest NBT was lost");
            check(fromLitematic.snapshot().hasEntities(), "litematic entity was lost");
            check(
                fromLitematic.snapshot().size().equals(new Vec3i(3, 2, 2)),
                "litematic union size wrong: " + fromLitematic.snapshot().size()
            );
        } catch (ConstructionBlueprintException exception) {
            throw new GameTestAssertException(
                "conversion failed: " + exception.reason() + " " + exception.detail()
            );
        }
        helper.succeed();
    }

    /** Litematica 实体 Pos 相对区域 Position(选区角点),负尺寸时该角点不是最小角。 */
    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Litematica entities stay relative to region origin rather than min corner")
    static void litematicEntitiesFollowRegionOrigin(ExtendedGameTestHelper helper) {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 6);
        root.putInt("MinecraftDataVersion", currentDataVersion());
        CompoundTag region = new CompoundTag();
        region.put("Position", vecTag(5, 0, 2));
        region.put("Size", vecTag(-5, 1, 1));
        ListTag palette = new ListTag();
        palette.add(namedState("minecraft:stone"));
        region.put("BlockStatePalette", palette);
        region.putLongArray("BlockStates", new long[]{0L});
        region.put("TileEntities", new ListTag());
        CompoundTag stand = new CompoundTag();
        stand.putString("id", "minecraft:armor_stand");
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(0.5D));
        pos.add(DoubleTag.valueOf(0.0D));
        pos.add(DoubleTag.valueOf(0.5D));
        stand.put("Pos", pos);
        ListTag entities = new ListTag();
        entities.add(stand);
        region.put("Entities", entities);
        CompoundTag regions = new CompoundTag();
        regions.put("a", region);
        root.put("Regions", regions);
        try {
            LitematicaImporter.ConvertedStructure converted = LitematicaImporter.convert(root);
            StructureSnapshot snapshot = StructureSnapshotCodec.parse(
                converted.structureTag(),
                helper.getLevel().registryAccess()
            ).snapshot();
            check(snapshot.entities().size() == 1, "expected one entity");
            Vec3 entityPos = snapshot.entities().getFirst().pos();
            check(
                entityPos.equals(new Vec3(4.5D, 0.0D, 0.5D)),
                "entity should follow region Position, got " + entityPos
            );
        } catch (ConstructionBlueprintException exception) {
            throw new GameTestAssertException(
                "conversion failed: " + exception.reason() + " " + exception.detail()
            );
        }
        helper.succeed();
    }

    /** 损坏的 Litematica 数组、缺失区域与重叠区域按设计显式报告。 */
    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Corrupted litematic files report explicit reasons and overlaps warn")
    static void litematicCorruptionAndOverlapAreReported(ExtendedGameTestHelper helper) {
        CompoundTag missingRegions = new CompoundTag();
        missingRegions.putInt("MinecraftDataVersion", currentDataVersion());
        try {
            LitematicaImporter.convert(missingRegions);
            throw new GameTestAssertException("litematic without regions converted successfully");
        } catch (ConstructionBlueprintException exception) {
            check(exception.reason().equals("corrupt_litematic"), "unexpected reason: " + exception.reason());
        }

        CompoundTag truncated = sampleLitematic(false);
        truncated.getCompound("Regions").getCompound("a").putLongArray("BlockStates", new long[0]);
        try {
            LitematicaImporter.convert(truncated);
            throw new GameTestAssertException("litematic with truncated BlockStates converted successfully");
        } catch (ConstructionBlueprintException exception) {
            check(exception.reason().equals("corrupt_litematic"), "unexpected reason: " + exception.reason());
        }

        try {
            LitematicaImporter.ConvertedStructure overlapped = LitematicaImporter.convert(sampleLitematic(true));
            check(
                overlapped.warnings().stream().anyMatch(warning -> warning.reason().equals("overlapping_regions")),
                "overlapping regions did not produce a warning"
            );
        } catch (ConstructionBlueprintException exception) {
            throw new GameTestAssertException("overlapping litematic failed to convert: " + exception.reason());
        }
        helper.succeed();
    }

    private static int currentDataVersion() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }

    /**
     * 两区域样例:区域 a 为 2x2x2 正尺寸(石头层+玻璃+带内容箱子+盔甲架),
     * 区域 b 用负 Y/Z 尺寸覆盖 x=2 的泥土列;overlap 为真时区域 b 平移进区域 a 制造重叠。
     */
    private static CompoundTag sampleLitematic(boolean overlap) {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 6);
        root.putInt("MinecraftDataVersion", currentDataVersion());
        CompoundTag regions = new CompoundTag();

        CompoundTag regionA = new CompoundTag();
        regionA.put("Position", vecTag(0, 0, 0));
        regionA.put("Size", vecTag(2, 2, 2));
        ListTag paletteA = new ListTag();
        paletteA.add(namedState("minecraft:air"));
        paletteA.add(namedState("minecraft:stone"));
        paletteA.add(namedState("minecraft:glass"));
        paletteA.add(namedState("minecraft:chest"));
        regionA.put("BlockStatePalette", paletteA);
        // 2 bit 条目,索引序 x+z*2+y*4,低位在前:石头(1)、箱子(3)、石头、石头、玻璃(2)、空气x3。
        regionA.putLongArray("BlockStates", new long[]{0b10_01_01_11_01L});
        ListTag tileEntities = new ListTag();
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        ListTag items = new ListTag();
        CompoundTag diamond = new CompoundTag();
        diamond.putByte("Slot", (byte) 0);
        diamond.putString("id", "minecraft:diamond");
        diamond.putInt("count", 1);
        items.add(diamond);
        chest.put("Items", items);
        chest.putInt("x", 1);
        chest.putInt("y", 0);
        chest.putInt("z", 0);
        tileEntities.add(chest);
        regionA.put("TileEntities", tileEntities);
        ListTag entitiesA = new ListTag();
        entitiesA.add(armorStandNbt());
        regionA.put("Entities", entitiesA);
        regions.put("a", regionA);

        CompoundTag regionB = new CompoundTag();
        regionB.put("Position", overlap ? vecTag(1, 1, 1) : vecTag(2, 1, 1));
        regionB.put("Size", vecTag(1, -2, -2));
        ListTag paletteB = new ListTag();
        paletteB.add(namedState("minecraft:dirt"));
        regionB.put("BlockStatePalette", paletteB);
        regionB.putLongArray("BlockStates", new long[]{0L});
        regionB.put("TileEntities", new ListTag());
        regionB.put("Entities", new ListTag());
        regions.put("b", regionB);

        root.put("Regions", regions);
        return root;
    }

    private static CompoundTag vecTag(int x, int y, int z) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return tag;
    }

    private static CompoundTag namedState(String id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", id);
        return tag;
    }

    private static CompoundTag armorStandNbt() {
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:armor_stand");
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(0.5D));
        pos.add(DoubleTag.valueOf(1.0D));
        pos.add(DoubleTag.valueOf(0.5D));
        entity.put("Pos", pos);
        return entity;
    }

    /** 与 {@link #sampleLitematic} 等价的原版稠密结构 NBT。 */
    private static CompoundTag equivalentVanillaStructure() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", currentDataVersion());
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(3));
        size.add(IntTag.valueOf(2));
        size.add(IntTag.valueOf(2));
        tag.put("size", size);

        ListTag palette = new ListTag();
        palette.add(namedState("minecraft:stone"));
        palette.add(namedState("minecraft:chest"));
        palette.add(namedState("minecraft:glass"));
        palette.add(namedState("minecraft:dirt"));
        palette.add(namedState("minecraft:air"));
        tag.put("palette", palette);

        ListTag blocks = new ListTag();
        blocks.add(blockEntry(0, 0, 0, 0));
        CompoundTag chestEntry = blockEntry(1, 0, 0, 1);
        CompoundTag chestNbt = new CompoundTag();
        chestNbt.putString("id", "minecraft:chest");
        ListTag items = new ListTag();
        CompoundTag diamond = new CompoundTag();
        diamond.putByte("Slot", (byte) 0);
        diamond.putString("id", "minecraft:diamond");
        diamond.putInt("count", 1);
        items.add(diamond);
        chestNbt.put("Items", items);
        chestEntry.put("nbt", chestNbt);
        blocks.add(chestEntry);
        blocks.add(blockEntry(0, 0, 1, 0));
        blocks.add(blockEntry(1, 0, 1, 0));
        blocks.add(blockEntry(0, 1, 0, 2));
        blocks.add(blockEntry(1, 1, 0, 4));
        blocks.add(blockEntry(0, 1, 1, 4));
        blocks.add(blockEntry(1, 1, 1, 4));
        blocks.add(blockEntry(2, 0, 0, 3));
        blocks.add(blockEntry(2, 0, 1, 3));
        blocks.add(blockEntry(2, 1, 0, 3));
        blocks.add(blockEntry(2, 1, 1, 3));
        tag.put("blocks", blocks);

        ListTag entities = new ListTag();
        CompoundTag entry = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(0.5D));
        pos.add(DoubleTag.valueOf(1.0D));
        pos.add(DoubleTag.valueOf(0.5D));
        entry.put("pos", pos);
        ListTag blockPos = new ListTag();
        blockPos.add(IntTag.valueOf(0));
        blockPos.add(IntTag.valueOf(1));
        blockPos.add(IntTag.valueOf(0));
        entry.put("blockPos", blockPos);
        entry.put("nbt", armorStandNbt());
        entities.add(entry);
        tag.put("entities", entities);
        return tag;
    }

    /** 告示牌方块实体、熔岩流体与苦力怕实体都进入规范快照,供投影按原外观绘制。 */
    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Snapshots keep wall signs, hanging signs, lava and creeper entities")
    static void snapshotKeepsSignsFluidsAndEntities(ExtendedGameTestHelper helper) {
        try {
            StructureSnapshotCodec.ParsedSnapshot parsed = StructureSnapshotCodec.parse(
                signsFluidsAndCreeperStructure(),
                helper.getLevel().registryAccess()
            );
            StructureSnapshot snapshot = parsed.snapshot();
            check(snapshot.hasBlockEntities(), "sign block entity NBT was lost");
            check(snapshot.hasEntities(), "creeper entity was lost");
            boolean wallSign = false;
            boolean hangingSign = false;
            boolean lava = false;
            boolean signNbt = false;
            for (StructureSnapshot.BlockEntry entry : snapshot.blocks()) {
                BlockState state = snapshot.stateOf(entry);
                if (state.is(Blocks.OAK_WALL_SIGN)) {
                    wallSign = true;
                    signNbt |= entry.nbt().isPresent();
                }
                if (state.is(Blocks.OAK_WALL_HANGING_SIGN)) hangingSign = true;
                if (state.is(Blocks.LAVA)) lava = true;
            }
            check(wallSign, "wall sign missing from palette/blocks");
            check(hangingSign, "hanging sign missing from palette/blocks");
            check(lava, "lava missing from palette/blocks");
            check(signNbt, "wall sign lost its block entity NBT");
            check(
                snapshot.entities().getFirst().nbt().getString("id").equals("minecraft:creeper"),
                "creeper id was " + snapshot.entities().getFirst().nbt().getString("id")
            );
        } catch (ConstructionBlueprintException exception) {
            throw new GameTestAssertException("sign/fluid/entity snapshot failed: " + exception.reason());
        }
        helper.succeed();
    }

    private static CompoundTag signsFluidsAndCreeperStructure() {
        CompoundTag tag = new CompoundTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(2));
        size.add(IntTag.valueOf(2));
        size.add(IntTag.valueOf(1));
        tag.put("size", size);

        ListTag palette = new ListTag();
        palette.add(namedState("minecraft:oak_log"));
        palette.add(namedState("minecraft:oak_wall_sign"));
        palette.add(namedState("minecraft:oak_wall_hanging_sign"));
        palette.add(namedState("minecraft:lava"));
        tag.put("palette", palette);

        ListTag blocks = new ListTag();
        blocks.add(blockEntry(0, 0, 0, 0));
        CompoundTag wallSign = blockEntry(0, 1, 0, 1);
        CompoundTag signNbt = new CompoundTag();
        signNbt.putString("id", "minecraft:sign");
        wallSign.put("nbt", signNbt);
        blocks.add(wallSign);
        blocks.add(blockEntry(1, 1, 0, 2));
        blocks.add(blockEntry(1, 0, 0, 3));
        tag.put("blocks", blocks);

        ListTag entities = new ListTag();
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
        CompoundTag creeper = new CompoundTag();
        creeper.putString("id", "minecraft:creeper");
        entry.put("nbt", creeper);
        entities.add(entry);
        tag.put("entities", entities);
        return tag;
    }

    /** 休息室磁盘槽只接受带施工蓝图的结构磁盘。 */
    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "The lounge disk slot only accepts disks carrying a construction blueprint")
    static void loungeDiskSlotRequiresBlueprint(ExtendedGameTestHelper helper) {
        ItemStack emptyDisk = new ItemStack(ModItems.STRUCTURE_DISK.get());
        check(
            !AllayLoungeBlockEntity.isValidDisk(emptyDisk),
            "empty structure disk must not enter the lounge disk slot"
        );
        try {
            ItemStack imported = importSampleDisk(helper, "first");
            check(
                AllayLoungeBlockEntity.isValidDisk(imported),
                "imported blueprint disk should enter the lounge disk slot"
            );
        } catch (ConstructionBlueprintException exception) {
            throw new GameTestAssertException("sample import failed: " + exception.reason());
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40, batch = "zzz_blueprint_lifecycle")
    @EmptyTemplate(value = "4x4x4", floor = true)
    @TestHolder(description = "Hotbar swaps into the lounge disk slot recheck current job collaboration")
    static void loungeDiskHotbarSwapRechecksJobPermission(ExtendedGameTestHelper helper) {
        GameTestPlayer owner = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        GameTestPlayer loungeOwner = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        BlockPos loungePos = new BlockPos(3, 2, 3);
        helper.setBlock(loungePos, PlasticraftBlocks.ALLAY_LOUNGE.get());
        if (!(helper.getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity lounge)) {
            throw new GameTestAssertException("allay lounge block entity is missing");
        }
        lounge.setOwner(loungeOwner.getUUID());

        ConstructionJob job = null;
        AllayLoungeMenu menu = null;
        try {
            ItemStack disk = importSampleDisk(helper, "permission");
            owner.setItemInHand(InteractionHand.MAIN_HAND, disk);
            job = ConstructionBlueprintService.deploy(
                owner,
                InteractionHand.MAIN_HAND,
                helper.absolutePos(new BlockPos(1, 2, 1)),
                Rotation.NONE,
                Mirror.NONE
            );
            ItemStack deployedDisk = owner.getItemInHand(InteractionHand.MAIN_HAND).copy();
            loungeOwner.getInventory().setItem(0, deployedDisk);
            menu = new AllayLoungeMenu(
                PlasticraftMenuTypes.ALLAY_LOUNGE.get(),
                1,
                loungeOwner.getInventory(),
                lounge
            );

            menu.clicked(0, 0, ClickType.SWAP, loungeOwner);
            check(lounge.items().getStackInSlot(AllayLoungeBlockEntity.DISK_SLOT).isEmpty(),
                "a stranger inserted another owner's job disk with a hotbar swap");
            ItemStack deniedDisk = loungeOwner.getInventory().getItem(0);
            check(deniedDisk.getCount() == deployedDisk.getCount()
                    && ItemStack.isSameItemSameComponents(deniedDisk, deployedDisk),
                "a denied hotbar swap moved the job disk");

            ConstructionPermission.setCollaboratorProvider((server, first, second) ->
                first.equals(owner.getUUID()) && second.equals(loungeOwner.getUUID())
                    || first.equals(loungeOwner.getUUID()) && second.equals(owner.getUUID())
            );
            menu.clicked(0, 0, ClickType.SWAP, loungeOwner);
            ItemStack insertedDisk = lounge.items().getStackInSlot(AllayLoungeBlockEntity.DISK_SLOT);
            check(insertedDisk.getCount() == deployedDisk.getCount()
                    && ItemStack.isSameItemSameComponents(insertedDisk, deployedDisk),
                "a current teammate could not insert the job disk with a hotbar swap");
            check(loungeOwner.getInventory().getItem(0).isEmpty(),
                "an allowed hotbar swap left the job disk in the hotbar");
        } catch (ConstructionBlueprintException exception) {
            throw new GameTestAssertException(
                "lounge disk permission setup failed: " + exception.reason() + " " + exception.detail()
            );
        } finally {
            ConstructionPermission.setCollaboratorProvider(null);
            if (menu != null) menu.removed(loungeOwner);
            lounge.items().setStackInSlot(AllayLoungeBlockEntity.DISK_SLOT, ItemStack.EMPTY);
            if (job != null && ConstructionJobIndex.get(helper.getLevel().getServer()).job(job.jobId()) != null) {
                try {
                    ConstructionBlueprintService.cancel(owner, job.jobId());
                } catch (ConstructionBlueprintException exception) {
                    throw new GameTestAssertException(
                        "lounge disk permission cleanup failed: " + exception.reason() + " " + exception.detail()
                    );
                }
            }
        }
        helper.succeed();
    }
}
