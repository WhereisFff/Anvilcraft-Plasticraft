package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.entity.CatalyticPressLidEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.ResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.physics.PlasticFallingBlockSupport;
import dev.anvilcraft.plasticraft.event.CatalyticPressAnvilEvents;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.List;

public final class CatalyticPressLidGameTests {
    private static final double EPSILON = 1.0E-7D;

    private CatalyticPressLidGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "A blockified catalytic press lid collision follows its stepped model")
    static void blockifiedCollisionMatchesModel(ExtendedGameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState state = ModBlocks.CATALYTIC_PRESS_LID.get()
            .defaultBlockState()
            .setValue(AbstractPlasticEntityBlock.BONDED, true);
        VoxelShape shape = state.getCollisionShape(helper.getLevel(), pos);
        List<AABB> boxes = shape.toAabbs();

        check(boxes.size() == 3, "catalytic press lid collision did not contain its three model tiers");
        check(hasBox(boxes, 0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D), "lid base collision changed");
        check(
            hasBox(boxes, 0.0625D, 0.5D, 0.0625D, 0.9375D, 0.8125D, 0.9375D),
            "lid middle collision changed"
        );
        check(
            hasBox(boxes, 0.125D, 0.8125D, 0.125D, 0.875D, 1.0D, 0.875D),
            "lid top collision changed"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "A catalytic press lid entity uses a full-block collision box")
    static void entityCollisionIsFullBlock(ExtendedGameTestHelper helper) {
        CatalyticPressLidEntity lid = spawnReadyLid(helper, new BlockPos(1, 1, 1));

        check(close(lid.getBbWidth(), 1.0D), "catalytic press lid entity width was not one block");
        check(close(lid.getBbHeight(), 1.0D), "catalytic press lid entity height was not one block");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "5x10x5", floor = true)
    @TestHolder(description = "A distant falling anvil does not lower the catalytic press arm early")
    static void distantAnvilDoesNotStartPressAnimation(ExtendedGameTestHelper helper) {
        CatalyticPressLidEntity lid = spawnReadyLid(helper, new BlockPos(2, 2, 2));
        FallingBlockEntity anvil = spawnFallingAnvil(helper, new BlockPos(2, 8, 2), -0.5D);

        CatalyticPressAnvilEvents.beforeFallingAnvilTick(anvil);

        check(lid.pressAnimationProgress(1.0F) == 0.0F, "distant anvil lowered the press arm early");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "5x7x5", floor = true)
    @TestHolder(description = "A falling anvil starts the press animation within its four-tick approach")
    static void nearbyAnvilStartsPressAnimation(ExtendedGameTestHelper helper) {
        CatalyticPressLidEntity lid = spawnReadyLid(helper, new BlockPos(2, 2, 2));
        FallingBlockEntity anvil = spawnFallingAnvil(helper, new BlockPos(2, 5, 2), -0.5D);

        CatalyticPressAnvilEvents.beforeFallingAnvilTick(anvil);

        check(lid.pressAnimationProgress(1.0F) > 0.0F, "nearby anvil did not start the press animation");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "5x7x5", floor = true)
    @TestHolder(description = "A resin anvil presses the arm only with its bottom face pointing down")
    static void resinAnvilRequiresBottomFaceDown(ExtendedGameTestHelper helper) {
        CatalyticPressLidEntity sidewaysLid = spawnReadyLid(helper, new BlockPos(1, 2, 2));
        ResinAnvilEntity sidewaysAnvil = spawnResinAnvil(
            helper,
            new BlockPos(1, 5, 2),
            new PlasticEntityOrientation(Direction.EAST, 0)
        );
        CatalyticPressLidEntity uprightLid = spawnReadyLid(helper, new BlockPos(3, 2, 2));
        ResinAnvilEntity uprightAnvil = spawnResinAnvil(
            helper,
            new BlockPos(3, 5, 2),
            PlasticEntityOrientation.DEFAULT
        );

        CatalyticPressAnvilEvents.beforeFallingAnvilTick(sidewaysAnvil);
        CatalyticPressAnvilEvents.beforeFallingAnvilTick(uprightAnvil);

        check(
            sidewaysLid.pressAnimationProgress(1.0F) == 0.0F,
            "sideways resin anvil lowered the press arm"
        );
        check(uprightLid.pressAnimationProgress(1.0F) > 0.0F, "upright resin anvil did not lower the press arm");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 55)
    @EmptyTemplate(value = "5x8x5", floor = true)
    @TestHolder(description = "A falling anvil blockifies on a full-height plastic entity and falls when it moves")
    static void fallingAnvilUsesDynamicPlasticEntitySupport(ExtendedGameTestHelper helper) {
        HardenedResinCauldronEntity cauldron = spawnCauldron(helper, new BlockPos(2, 2, 2));
        spawnFallingAnvil(helper, new BlockPos(2, 6, 2), -0.1D);
        BlockPos landedPos = new BlockPos(2, 3, 2);

        helper.runAfterDelay(25, () -> {
            check(helper.getBlockState(landedPos).is(Blocks.ANVIL), "anvil did not blockify on the resin cauldron");
            BlockPos absoluteLandedPos = helper.absolutePos(landedPos);
            check(
                PlasticFallingBlockSupport.hasSupport(helper.getLevel(), absoluteLandedPos, null),
                "landed anvil no longer touched the resin cauldron"
            );
            cauldron.setPos(cauldron.position().add(2.0D, 0.0D, 0.0D));
            check(
                !PlasticFallingBlockSupport.hasSupport(helper.getLevel(), helper.absolutePos(landedPos), null),
                "moved resin cauldron still counted as support"
            );
            check(
                !BondedFallingBlocks.isBonded(helper.getLevel(), helper.absolutePos(landedPos)),
                "landed anvil unexpectedly became adhesive-bonded"
            );
            helper.runAfterDelay(2, () -> {
                check(helper.getBlockState(landedPos).isAir(), "anvil block stayed behind after its support moved");
                boolean fallingAgain = !helper.getLevel().getEntitiesOfClass(
                    FallingBlockEntity.class,
                    new AABB(absoluteLandedPos).inflate(2.0D),
                    entity -> entity.getBlockState().is(Blocks.ANVIL)
                ).isEmpty();
                check(fallingAgain, "anvil did not continue falling after its support moved");
                helper.succeed();
            });
        });
    }

    private static CatalyticPressLidEntity spawnReadyLid(
        ExtendedGameTestHelper helper,
        BlockPos relativePos
    ) {
        BlockPos pos = helper.absolutePos(relativePos);
        Vec3 position = PlasticEntityOrientation.DEFAULT.entityPosition(
            pos,
            CatalyticPressLidEntity.WIDTH,
            CatalyticPressLidEntity.HEIGHT
        );
        CatalyticPressLidEntity lid = new CatalyticPressLidEntity(
            ModEntities.CATALYTIC_PRESS_LID.get(),
            helper.getLevel(),
            position,
            ModBlocks.CATALYTIC_PRESS_LID.get().defaultBlockState(),
            ModBlocks.CATALYTIC_PRESS_LID.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        lid.completeCatalysis();
        check(helper.getLevel().addFreshEntity(lid), "failed to add catalytic press lid");
        return lid;
    }

    private static FallingBlockEntity spawnFallingAnvil(
        ExtendedGameTestHelper helper,
        BlockPos relativePos,
        double velocityY
    ) {
        helper.setBlock(relativePos, Blocks.ANVIL);
        FallingBlockEntity anvil = FallingBlockEntity.fall(
            helper.getLevel(),
            helper.absolutePos(relativePos),
            Blocks.ANVIL.defaultBlockState()
        );
        anvil.setDeltaMovement(0.0D, velocityY, 0.0D);
        return anvil;
    }

    private static ResinAnvilEntity spawnResinAnvil(
        ExtendedGameTestHelper helper,
        BlockPos relativePos,
        PlasticEntityOrientation orientation
    ) {
        BlockPos pos = helper.absolutePos(relativePos);
        Vec3 position = orientation.entityPosition(
            pos,
            ModEntities.RESIN_ANVIL.get().getWidth(),
            ModEntities.RESIN_ANVIL.get().getHeight()
        );
        ResinAnvilEntity anvil = new ResinAnvilEntity(
            ModEntities.RESIN_ANVIL.get(),
            helper.getLevel(),
            position,
            ModBlocks.RESIN_ANVIL.get().defaultBlockState(),
            ModBlocks.RESIN_ANVIL.asStack(),
            orientation
        );
        anvil.setDeltaMovement(0.0D, -0.5D, 0.0D);
        check(helper.getLevel().addFreshEntity(anvil), "failed to add resin anvil");
        return anvil;
    }

    private static HardenedResinCauldronEntity spawnCauldron(
        ExtendedGameTestHelper helper,
        BlockPos relativePos
    ) {
        BlockPos pos = helper.absolutePos(relativePos);
        Vec3 position = PlasticEntityOrientation.DEFAULT.entityPosition(
            pos,
            ModEntities.HARDEND_RESIN_CAULDRON.get().getWidth(),
            ModEntities.HARDEND_RESIN_CAULDRON.get().getHeight()
        );
        HardenedResinCauldronEntity cauldron = new HardenedResinCauldronEntity(
            ModEntities.HARDEND_RESIN_CAULDRON.get(),
            helper.getLevel(),
            position,
            ModBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            ModBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        cauldron.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(cauldron), "failed to add resin cauldron");
        return cauldron;
    }

    private static boolean hasBox(
        List<AABB> boxes,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
    ) {
        return boxes.stream().anyMatch(box -> close(box.minX, minX)
            && close(box.minY, minY)
            && close(box.minZ, minZ)
            && close(box.maxX, maxX)
            && close(box.maxY, maxY)
            && close(box.maxZ, maxZ));
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) <= EPSILON;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
