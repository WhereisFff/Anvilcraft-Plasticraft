package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.block.entity.DroneStationBlockEntity;
import dev.anvilcraft.plasticraft.blueprint.BlueprintPlacement;
import dev.anvilcraft.plasticraft.blueprint.BlueprintSource;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintData;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintException;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintService;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionStructureLibrary;
import dev.anvilcraft.plasticraft.blueprint.LitematicaImporter;
import dev.anvilcraft.plasticraft.blueprint.ScannerDiskImporter;
import dev.anvilcraft.plasticraft.blueprint.StructureSnapshot;
import dev.anvilcraft.plasticraft.blueprint.StructureSnapshotCodec;
import dev.dubhe.anvilcraft.init.item.ModItems;
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

/**
 * 覆盖施工蓝图的数据层与服务层:规范快照哈希的来源无关性、导入校验的显式报错、
 * 结构方块模板导入、扫描器坐标归一化、部署生命周期与每玩家单活动约束。
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
        helper.succeed();
    }

    /** 部署生命周期:创建任务、锚点移动、启动切换、单活动约束与取消清理。 */
    @GameTest(timeoutTicks = 60)
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
                check(index.jobs().size() == 1, "index should contain exactly one job");

                // 启动切换与每玩家单活动:启动第二份会暂停第一份。
                check(
                    ConstructionBlueprintService.toggleActive(player, job.jobId()),
                    "first toggle should start the job"
                );
                check(index.job(job.jobId()).isActive(), "job did not become active");

                player.setItemInHand(InteractionHand.MAIN_HAND, secondDisk);
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

                // 取消清理任务条目并清除手中磁盘的引用。
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

                ConstructionBlueprintService.cancel(player, job.jobId());
            } catch (ConstructionBlueprintException exception) {
                throw new GameTestAssertException(
                    "lifecycle failed: " + exception.reason() + " " + exception.detail()
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

    /** 站点磁盘槽只接受带施工蓝图的结构磁盘。 */
    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "The station disk slot only accepts disks carrying a construction blueprint")
    static void stationDiskSlotRequiresBlueprint(ExtendedGameTestHelper helper) {
        ItemStack emptyDisk = new ItemStack(ModItems.STRUCTURE_DISK.get());
        check(
            !DroneStationBlockEntity.isValidForSlot(DroneStationBlockEntity.DISK_SLOT, emptyDisk),
            "empty structure disk must not enter the station disk slot"
        );
        try {
            ItemStack imported = importSampleDisk(helper, "first");
            check(
                DroneStationBlockEntity.isValidForSlot(DroneStationBlockEntity.DISK_SLOT, imported),
                "imported blueprint disk should enter the station disk slot"
            );
        } catch (ConstructionBlueprintException exception) {
            throw new GameTestAssertException("sample import failed: " + exception.reason());
        }
        helper.succeed();
    }
}
