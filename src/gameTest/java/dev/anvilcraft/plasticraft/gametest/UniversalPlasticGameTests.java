package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.UniversalPlasticBlock;
import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltFluidBlock;
import dev.anvilcraft.plasticraft.block.UniversalPlasticSolidification;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.UniversalPlasticMeltBlockEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityCollisionBox;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.dubhe.anvilcraft.block.sliding.SlidingRailBlock;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.util.AccelerateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

import java.util.List;

/** TODO-00 通用塑料块、物品、实体、固化和公共物理的回归测试。 */
public final class UniversalPlasticGameTests {
    private static final double EPSILON = 1.0E-6D;

    private UniversalPlasticGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x5x7", floor = true)
    @TestHolder(description = "Creative attacks remove plastic entities without item drops")
    static void creativeEntityDestructionHasNoDrop(ExtendedGameTestHelper helper) {
        BlockPos occupied = new BlockPos(3, 2, 3);
        ItemStack drop = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        PlasticMeltColor.set(drop, DyeColor.CYAN);
        UniversalPlasticEntity entity = createUniversal(
            helper,
            helper.absolutePos(occupied).getCenter().add(0.0D, -0.5D, 0.0D),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            drop
        );
        entity.setNoGravity(true);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);

        check(entity.hurt(player.damageSources().playerAttack(player), 1.0F),
            "creative attack did not destroy the plastic entity");
        check(entity.isRemoved(), "creative attack left the plastic entity alive");
        check(helper.getLevel().getEntitiesOfClass(
            ItemEntity.class,
            new AABB(helper.absolutePos(occupied)).inflate(1.0D),
            item -> item.getItem().is(PlasticraftBlocks.UNIVERSAL_PLASTIC.asItem())
        ).isEmpty(), "creative attack dropped the plastic entity item");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "World melt solidification is colour-preserving, in-place, and idempotent")
    static void solidificationTransaction(ExtendedGameTestHelper helper) {
        BlockPos relativePos = new BlockPos(3, 2, 3);
        BlockPos pos = helper.absolutePos(relativePos);
        placeMelt(helper, relativePos, DyeColor.PURPLE);

        check(UniversalPlasticSolidification.solidify(helper.getLevel(), pos), "melt did not solidify");
        check(!UniversalPlasticSolidification.solidify(helper.getLevel(), pos), "melt solidified twice");
        BlockState product = helper.getLevel().getBlockState(pos);
        check(product.is(PlasticraftBlocks.UNIVERSAL_PLASTIC.get()), "solidification did not replace the melt in place");
        check(
            product.getValue(DyeableMaterial.COLOR) == DyeColor.PURPLE,
            "solidification lost the melt colour"
        );

        VoxelShape outline = product.getShape(helper.getLevel(), pos);
        VoxelShape collision = product.getCollisionShape(helper.getLevel(), pos);
        check(outline.equals(collision), "universal block outline and collision diverged");
        check(close(outline.bounds().getXsize(), 1.0D), "universal block width is not 16 px");
        check(close(outline.bounds().getYsize(), 0.875D), "universal block height is not 14 px");
        check(close(outline.bounds().getZsize(), 1.0D), "universal block length is not 16 px");

        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        ItemStack clone = ((UniversalPlasticBlock) product.getBlock()).getCloneItemStack(
            product,
            new BlockHitResult(pos.getCenter(), Direction.UP, pos, false),
            helper.getLevel(),
            pos,
            player
        );
        check(PlasticMeltColor.get(clone) == DyeColor.PURPLE, "pick-block stack lost the product colour");

        check(helper.getLevel().destroyBlock(pos, true, player), "universal plastic block could not be mined");
        ItemStack mined = helper.getLevel().getEntitiesOfClass(
            ItemEntity.class,
            new AABB(pos).inflate(1.0D),
            item -> item.getItem().is(PlasticraftBlocks.UNIVERSAL_PLASTIC.asItem())
        ).stream().map(ItemEntity::getItem).findFirst().orElseThrow(() ->
            new GameTestAssertException("mined universal plastic block produced no item")
        );
        check(PlasticMeltColor.get(mined) == DyeColor.PURPLE, "ordinary block drop lost the product colour");
        assertNoGranules(helper);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "11x7x7", floor = true)
    @TestHolder(description = "Rain, adjacent water, and coolant tags all use the same block result")
    static void allWorldCoolingTriggers(ExtendedGameTestHelper helper) {
        BlockPos waterMelt = new BlockPos(2, 2, 3);
        placeMelt(helper, waterMelt, DyeColor.CYAN);
        helper.setBlock(waterMelt.east(), Blocks.WATER);
        check(
            UniversalPlasticMeltFluidBlock.trySolidifyFromEnvironment(
                helper.getLevel(),
                helper.absolutePos(waterMelt)
            ),
            "adjacent water did not trigger solidification"
        );

        BlockPos coolantMelt = new BlockPos(5, 2, 3);
        placeMelt(helper, coolantMelt, DyeColor.RED);
        helper.setBlock(coolantMelt.east(), Blocks.ICE);
        check(
            UniversalPlasticMeltFluidBlock.trySolidifyFromEnvironment(
                helper.getLevel(),
                helper.absolutePos(coolantMelt)
            ),
            "PLASTIC_MELT_COOLANTS block did not trigger solidification"
        );

        BlockPos rainMelt = helper.getLevel().getHeightmapPos(
            Heightmap.Types.MOTION_BLOCKING,
            helper.absolutePos(new BlockPos(8, 0, 3))
        );
        placeMeltAt(helper, rainMelt, DyeColor.LIME);
        helper.setBiome(Biomes.PLAINS);
        helper.getLevel().setWeatherParameters(0, 20, true, false);
        helper.getLevel().setRainLevel(1.0F);
        try {
            BlockPos precipitationPos = rainMelt.above();
            check(helper.getLevel().isRaining(), "rain level was not active");
            check(helper.getLevel().canSeeSky(precipitationPos), "rain test position could not see the sky");
            check(
                helper.getLevel().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, precipitationPos).getY()
                    <= precipitationPos.getY(),
                "rain test position was below the motion-blocking heightmap"
            );
            check(
                helper.getLevel().getBiome(precipitationPos).value().getPrecipitationAt(precipitationPos)
                    == Biome.Precipitation.RAIN,
                "rain test biome did not produce rain"
            );
            check(
                helper.getLevel().isRainingAt(precipitationPos),
                "rain test position was not exposed to precipitation"
            );
            check(
                UniversalPlasticMeltFluidBlock.trySolidifyFromEnvironment(
                    helper.getLevel(),
                    rainMelt
                ),
                "rain did not trigger solidification"
            );
        } finally {
            // 天气属于整张测试维度；立即恢复，避免影响并行的其他 GameTest。
            helper.getLevel().setWeatherParameters(6_000, 0, false, false);
            helper.getLevel().setRainLevel(0.0F);
        }

        assertProduct(helper, waterMelt, DyeColor.CYAN);
        assertProduct(helper, coolantMelt, DyeColor.RED);
        assertProduct(helper.getLevel().getBlockState(rainMelt), DyeColor.LIME);
        assertNoGranules(helper);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x6x9", floor = true)
    @TestHolder(description = "Universal plastic item placement, persistence, magnetism, and hammer recovery preserve colour")
    static void itemEntityAndHammerRoundTrip(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(4, 1, 4);
        helper.setBlock(support, Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        PlasticMeltColor.set(stack, DyeColor.MAGENTA);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(
            helper.absolutePos(support).getCenter(),
            Direction.UP,
            helper.absolutePos(support),
            false
        );
        InteractionResult placement = stack.useOn(new UseOnContext(
            helper.getLevel(),
            player,
            InteractionHand.MAIN_HAND,
            stack,
            hit
        ));
        check(placement.consumesAction(), "universal plastic item did not place its entity");
        UniversalPlasticEntity entity = helper.getLevel().getEntitiesOfClass(
            UniversalPlasticEntity.class,
            new AABB(helper.absolutePos(support.above())).inflate(0.2D)
        ).stream().findFirst().orElseThrow(() ->
            new GameTestAssertException("placed universal plastic entity was not found")
        );
        entity.setNoGravity(true);

        check(
            entity.getOrientation().equals(PlasticEntityOrientation.forPlacement(Direction.UP, player)),
            "universal plastic did not use the common floor placement orientation"
        );
        check(close(entity.getBbWidth(), 1.0D), "entity width is not 16 px");
        check(close(entity.getBbHeight(), 1.0D), "entity broad-phase height is not one full block");
        AABB collision = entity.plasticraft$getCollisionBox().bounds();
        check(close(collision.getXsize(), 1.0D), "entity collision width drifted from the model");
        check(close(collision.getYsize(), 0.875D), "entity collision height drifted from the model");
        check(close(collision.getZsize(), 1.0D), "entity collision length drifted from the model");
        check(sameBounds(entity.getBoundingBox(), collision), "entity broad-phase box did not follow its geometry");
        check(
            entity.getDisplayState().getValue(DyeableMaterial.COLOR) == DyeColor.MAGENTA,
            "placed entity display state lost its colour"
        );
        check(PlasticMeltColor.get(entity.getDropStack()) == DyeColor.MAGENTA, "placed entity drop lost its colour");

        CompoundTag saved = entity.saveWithoutId(new CompoundTag());
        UniversalPlasticEntity loaded = new UniversalPlasticEntity(PlasticraftEntities.UNIVERSAL_PLASTIC.get(), helper.getLevel());
        loaded.load(saved);
        check(
            loaded.getDisplayState().getValue(DyeableMaterial.COLOR) == DyeColor.MAGENTA,
            "saved entity display state lost its colour"
        );
        check(PlasticMeltColor.get(loaded.getDropStack()) == DyeColor.MAGENTA, "saved entity drop lost its colour");

        var menuBefore = player.containerMenu;
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(entity.interact(player, InteractionHand.MAIN_HAND) == InteractionResult.PASS, "entity consumed normal use");
        check(player.containerMenu == menuBefore, "functionless universal product opened a menu");

        check(!AccelerateManager.canBeAccelerated(entity), "unmagnetized universal product entered acceleration");
        entity.setMagnetized(true);
        check(AccelerateManager.canBeAccelerated(entity), "magnetized universal product was rejected by acceleration");
        check(PlasticItemData.isMagnetized(entity.getDropStack()), "magnetized state was absent from the drop");

        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, PlasticraftItems.RESIN_ANVIL_HAMMER.asStack());
        check(entity.interact(player, InteractionHand.MAIN_HAND).consumesAction(), "hammer did not recover the product");
        ItemStack recovered = findInventoryStack(player, PlasticraftBlocks.UNIVERSAL_PLASTIC.asItem());
        check(!recovered.isEmpty(), "hammer recovery did not return the product item");
        check(PlasticMeltColor.get(recovered) == DyeColor.MAGENTA, "hammer recovery lost the colour");
        check(PlasticItemData.isMagnetized(recovered), "hammer recovery lost magnetization");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("9x9x9")
    @TestHolder(description = "Universal plastic item placement attaches its bottom to every clicked face")
    static void itemPlacementUsesEveryClickedFace(ExtendedGameTestHelper helper) {
        BlockPos clicked = new BlockPos(4, 4, 4);
        helper.setBlock(clicked, Blocks.STONE);
        BlockPos absoluteClicked = helper.absolutePos(clicked);
        for (Direction face : Direction.values()) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setYRot(0.0F);
            ItemStack stack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockHitResult hit = new BlockHitResult(
                absoluteClicked.getCenter(),
                face,
                absoluteClicked,
                false
            );

            InteractionResult result = stack.useOn(new UseOnContext(
                helper.getLevel(),
                player,
                InteractionHand.MAIN_HAND,
                stack,
                hit
            ));
            check(result.consumesAction(), "universal plastic placement failed on " + face);

            PlasticEntityOrientation expected = PlasticEntityOrientation.forPlacement(face, player);
            BlockPos occupied = absoluteClicked.relative(face);
            UniversalPlasticEntity placed = helper.getLevel().getEntitiesOfClass(
                UniversalPlasticEntity.class,
                new AABB(occupied).inflate(0.1D)
            ).stream().findFirst().orElseThrow(() ->
                new GameTestAssertException("universal plastic entity was not created on " + face)
            );
            AABB exactCollision = placed.plasticraft$getCollisionBox().bounds();
            check(placed.getOrientation().equals(expected), "universal plastic ignored clicked face " + face);
            check(
                close(attachmentContact(exactCollision, face), clickedFaceCoordinate(absoluteClicked, face)),
                "universal plastic bottom did not touch clicked face " + face
            );
            check(
                contains(placed.getBoundingBox(), exactCollision),
                "rotated collision escaped the broad-phase box on " + face
            );
            placed.discard();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("9x6x7")
    @TestHolder(description = "Hammer rotation to west matches directly placed universal plastic collision")
    static void hammerRotationMatchesDirectWestCollision(ExtendedGameTestHelper helper) {
        BlockState state = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState();
        ItemStack drop = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        UniversalPlasticEntity rotated = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(2.5D, 1.0D, 3.5D)),
            state,
            drop,
            PlasticEntityOrientation.DEFAULT
        );
        rotated.setNoGravity(true);
        AABB uprightCollision = rotated.plasticraft$getCollisionBox().bounds();
        PlasticEntityOrientation west = new PlasticEntityOrientation(Direction.WEST, 0);
        UniversalPlasticEntity directlyPlaced = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(6.5D, 1.0D, 3.5D)),
            state,
            drop,
            west
        );
        directlyPlaced.setNoGravity(true);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(4.5D, 3.0D, 1.5D)));
        player.setItemInHand(InteractionHand.MAIN_HAND, PlasticraftItems.RESIN_ANVIL_HAMMER.asStack());
        check(
            rotated.plasticraft$changeAttachmentFace(player, InteractionHand.MAIN_HAND, Direction.WEST),
            "hammer rotation to west was rejected"
        );
        check(rotated.getOrientation().equals(west), "hammer rotation did not apply the west orientation");

        AABB rotatedCollision = rotated.plasticraft$getCollisionBox().bounds();
        AABB directCollision = directlyPlaced.plasticraft$getCollisionBox().bounds()
            .move(rotated.position().subtract(directlyPlaced.position()));
        check(sameBounds(rotatedCollision, directCollision), "hammer rotation changed the precise collision bounds");
        check(close(rotatedCollision.getXsize(), 0.875D), "west collision is not 14 px wide");
        check(close(rotatedCollision.getYsize(), 1.0D), "west collision is not 16 px tall");
        check(
            close(rotatedCollision.getCenter().y, uprightCollision.getCenter().y),
            "hammer rotation moved the universal plastic collision center"
        );
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x5x7", floor = true)
    @TestHolder(description = "Universal plastic rotates around its 14 px bounds center and previews floor obstruction")
    static void centeredHammerRotationUsesActualBounds(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity entity = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D)),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack(),
            new PlasticEntityOrientation(Direction.DOWN, 0)
        );
        entity.setNoGravity(true);
        double floorTop = helper.absolutePos(new BlockPos(3, 2, 3)).getY();
        entity.setPos(entity.position().add(
            0.0D,
            floorTop - entity.plasticraft$getCollisionBox().bounds().minY,
            0.0D
        ));

        PlasticEntityGeometry geometry = entity.plasticraft$getGeometry();
        AABB localBounds = geometry.localBounds();
        Vec3 localCenter = localBounds.getCenter();
        check(geometry.rotationPivot().equals(localCenter), "default geometry pivot is not the actual bounds center");

        AABB upsideDown = entity.plasticraft$getCollisionBox().bounds();
        AABB upright = geometry.collisionBoxAt(entity.position(), PlasticEntityOrientation.DEFAULT).bounds();
        check(sameBounds(upsideDown, upright), "up/down rotation moved the symmetric 14 px collision");
        check(
            entity.plasticraft$getRotationCenter().equals(upsideDown.getCenter()),
            "universal plastic world rotation center differs from its collision center"
        );
        check(entity.canHammerRotateTo(PlasticEntityOrientation.DEFAULT), "centered up/down rotation was rejected");

        PlasticEntityOrientation west = new PlasticEntityOrientation(Direction.WEST, 0);
        AABB westBounds = geometry.collisionBoxAt(entity.position(), west).bounds();
        check(westBounds.minY < floorTop - EPSILON, "side rotation did not enter the floor test block");
        check(!entity.canHammerRotateTo(west), "floor obstruction was not exposed to the hammer preview");

        entity.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 35)
    @EmptyTemplate(value = "7x8x7", floor = true)
    @TestHolder(description = "A falling anvil shatters on a plastic entity whose top is below the block grid")
    static void fallingAnvilBreaksOnFourteenPixelPlastic(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity support = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D)),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack()
        );
        support.setNoGravity(true);
        BlockPos sourcePos = new BlockPos(3, 5, 3);
        helper.setBlock(sourcePos, Blocks.ANVIL);
        FallingBlockEntity falling = FallingBlockEntity.fall(
            helper.getLevel(),
            helper.absolutePos(sourcePos),
            Blocks.ANVIL.defaultBlockState()
        );
        falling.setDeltaMovement(0.0D, -0.4D, 0.0D);

        helper.runAfterDelay(20, () -> {
            check(!falling.isAlive(), "falling anvil remained an entity after hitting 14 px plastic");
            check(helper.getBlockState(new BlockPos(3, 3, 3)).isAir(), "falling anvil landed above 14 px plastic");
            int drops = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                support.getBoundingBox().inflate(3.0D),
                item -> item.getItem().is(Items.ANVIL)
            ).stream().mapToInt(item -> item.getItem().getCount()).sum();
            check(drops == 1, "14 px plastic collision produced " + drops + " anvil drops");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 35)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "A directly placed falling block waits until the 14 px plastic cell becomes empty")
    static void placedAnvilWaitsForFourteenPixelPlasticToLeave(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity support = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D)),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack()
        );
        support.setNoGravity(true);
        BlockPos placedPos = new BlockPos(3, 3, 3);
        helper.setBlock(placedPos, Blocks.ANVIL);

        helper.startSequence()
            .thenIdle(8)
            .thenExecute(() -> {
                check(helper.getBlockState(placedPos).is(Blocks.ANVIL), "placed anvil fell through the occupied cell");
                check(
                    helper.getLevel().getEntitiesOfClass(
                        FallingBlockEntity.class,
                        new AABB(helper.absolutePos(placedPos)).inflate(2.0D),
                        entity -> entity.getBlockState().is(Blocks.ANVIL)
                    ).isEmpty(),
                    "placed anvil became a falling entity while plastic still occupied the cell below"
                );
            })
            .thenExecute(support::discard)
            .thenIdle(4)
            .thenExecute(() -> {
                check(helper.getBlockState(placedPos).isAir(), "placed anvil remained fixed after the cell became empty");
                check(
                    !helper.getLevel().getEntitiesOfClass(
                        FallingBlockEntity.class,
                        new AABB(helper.absolutePos(placedPos)).inflate(2.0D),
                        entity -> entity.getBlockState().is(Blocks.ANVIL)
                    ).isEmpty(),
                    "placed anvil did not begin falling after the plastic entity left"
                );
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x8x7", floor = true)
    @TestHolder(description = "A player remains supported by an upside-down universal plastic bonded top-to-top")
    static void upsideDownBondedPlasticSupportsPlayer(ExtendedGameTestHelper helper) {
        BlockState state = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState();
        ItemStack drop = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        Vec3 lowerPosition = helper.absoluteVec(new Vec3(3.5D, 1.0D, 3.5D));
        UniversalPlasticEntity lower = createUniversal(
            helper,
            lowerPosition,
            state,
            drop,
            PlasticEntityOrientation.DEFAULT
        );
        lower.setNoGravity(true);
        UniversalPlasticEntity upper = createUniversal(
            helper,
            lowerPosition.add(0.0D, 1.0D, 0.0D),
            state,
            drop,
            new PlasticEntityOrientation(Direction.DOWN, 0)
        );
        AABB lowerCollision = lower.plasticraft$getCollisionBox().bounds();
        AABB upperCollision = upper.plasticraft$getCollisionBox().bounds();
        upper.setPos(upper.position().add(0.0D, lowerCollision.maxY - upperCollision.minY, 0.0D));
        upperCollision = upper.plasticraft$getCollisionBox().bounds();

        check(
            EntityBondManager.connect(
                helper.getLevel(),
                upper,
                Direction.DOWN,
                lower,
                Direction.UP,
                true
            ),
            "universal plastic top faces could not be bonded"
        );
        check(
            contains(upper.getBoundingBox(), upperCollision),
            "upside-down collision escaped the entity broad-phase box"
        );

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(upper.getX(), upperCollision.maxY, upper.getZ());
        double standingY = player.getY();
        player.move(MoverType.SELF, new Vec3(0.0D, -0.08D, 0.0D));
        check(close(player.getY(), standingY), "player fell through the upside-down universal plastic");
        check(player.verticalCollisionBelow, "upside-down universal plastic did not report lower collision");
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x6x7", floor = true)
    @TestHolder(description = "Stepping onto a raised member does not push the bonded plastic component")
    static void steppingOntoRaisedBondedPlasticDoesNotPushIt(ExtendedGameTestHelper helper) {
        BlockState state = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState();
        ItemStack drop = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        UniversalPlasticEntity lower = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D)),
            state,
            drop
        );
        UniversalPlasticEntity raised = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(4.5D, 2.125D, 3.5D)),
            state,
            drop
        );
        lower.setNoGravity(true);
        raised.setNoGravity(true);
        check(
            EntityBondManager.connect(
                helper.getLevel(),
                lower,
                Direction.EAST,
                raised,
                Direction.WEST,
                true
            ),
            "raised universal plastic could not be bonded"
        );

        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        AABB lowerBounds = lower.plasticraft$getCollisionBox().bounds();
        AABB raisedBounds = raised.plasticraft$getCollisionBox().bounds();
        player.moveTo(
            lowerBounds.maxX - player.getBbWidth() * 0.5D - 0.05D,
            lowerBounds.maxY,
            lowerBounds.getCenter().z
        );
        player.setOnGround(true);
        Vec3 lowerStart = lower.position();
        Vec3 raisedStart = raised.position();
        Vec3 playerStart = player.position();

        player.move(MoverType.SELF, new Vec3(0.55D, -0.08D, 0.0D));

        check(close(lower.position().distanceToSqr(lowerStart), 0.0D), "lower bonded plastic was pushed");
        check(close(raised.position().distanceToSqr(raisedStart), 0.0D), "raised bonded plastic was pushed");
        check(player.getX() > playerStart.x + 0.35D, "raised plastic blocked the player's step");
        check(player.getY() >= raisedBounds.maxY - EPSILON, "player did not step onto the raised plastic");
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "9x6x7", floor = true)
    @TestHolder(description = "A player walks smoothly across a one-pixel raised bonded universal plastic member")
    static void walkingAcrossOnePixelRaisedBondedPlasticIsSmooth(ExtendedGameTestHelper helper) {
        BlockState state = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState();
        ItemStack drop = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        UniversalPlasticEntity lower = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D)),
            state,
            drop
        );
        UniversalPlasticEntity raised = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(4.5D, 2.0D + 1.0D / 16.0D, 3.5D)),
            state,
            drop
        );
        lower.setNoGravity(true);
        raised.setNoGravity(true);
        check(
            EntityBondManager.connect(
                helper.getLevel(),
                lower,
                Direction.EAST,
                raised,
                Direction.WEST,
                true
            ),
            "one-pixel raised universal plastic could not be bonded"
        );

        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        AABB lowerBounds = lower.plasticraft$getCollisionBox().bounds();
        AABB raisedBounds = raised.plasticraft$getCollisionBox().bounds();
        player.moveTo(
            lowerBounds.maxX - player.getBbWidth() * 0.5D - 0.05D,
            lowerBounds.maxY,
            lowerBounds.getCenter().z
        );
        player.setOnGround(true);
        Vec3 lowerStart = lower.position();
        Vec3 raisedStart = raised.position();
        double[] previousX = {player.getX()};

        helper.startSequence()
            .thenExecuteFor(8, () -> {
                player.move(MoverType.SELF, new Vec3(0.12D, -0.08D, 0.0D));
                check(
                    player.getX() > previousX[0] + 0.10D,
                    "one-pixel raised plastic interrupted the player's forward step"
                );
                check(
                    player.getY() >= raisedBounds.maxY - EPSILON,
                    "one-pixel raised plastic dropped the player below its top"
                );
                check(
                    close(lower.position().distanceToSqr(lowerStart), 0.0D),
                    "lower bonded plastic was pushed while the player stepped"
                );
                check(
                    close(raised.position().distanceToSqr(raisedStart), 0.0D),
                    "raised bonded plastic was pushed while the player stepped"
                );
                previousX[0] = player.getX();
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x6x7", floor = true)
    @TestHolder(description = "A player steps directly onto a nine-pixel raised bonded universal plastic member")
    static void bondedUniversalPlasticStepsUpNinePixels(ExtendedGameTestHelper helper) {
        BlockState state = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState();
        ItemStack drop = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        UniversalPlasticEntity lower = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D)),
            state,
            drop
        );
        UniversalPlasticEntity raised = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(4.5D, 2.0D + 9.0D / 16.0D, 3.5D)),
            state,
            drop
        );
        lower.setNoGravity(true);
        raised.setNoGravity(true);
        check(
            EntityBondManager.connect(
                helper.getLevel(),
                lower,
                Direction.EAST,
                raised,
                Direction.WEST,
                true
            ),
            "nine-pixel raised universal plastic could not be bonded"
        );

        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        AABB lowerBounds = lower.plasticraft$getCollisionBox().bounds();
        AABB raisedBounds = raised.plasticraft$getCollisionBox().bounds();
        player.moveTo(
            lowerBounds.maxX - player.getBbWidth() * 0.5D - 0.05D,
            lowerBounds.maxY,
            lowerBounds.getCenter().z
        );
        player.setOnGround(true);
        Vec3 lowerStart = lower.position();
        Vec3 raisedStart = raised.position();
        Vec3 playerStart = player.position();

        player.move(MoverType.SELF, new Vec3(0.55D, -0.08D, 0.0D));

        check(player.getX() > playerStart.x + 0.35D, "nine-pixel raised plastic blocked the player's step");
        check(player.getY() >= raisedBounds.maxY - EPSILON, "player did not step onto nine-pixel raised plastic");
        check(close(lower.position().distanceToSqr(lowerStart), 0.0D), "lower bonded plastic was pushed");
        check(close(raised.position().distanceToSqr(raisedStart), 0.0D), "raised bonded plastic was pushed");
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x6x7", floor = true)
    @TestHolder(description = "A supported player pushes an unbonded low universal plastic member instead of stepping onto it")
    static void unbondedRaisedUniversalPlasticIsPushedBeforeStepping(ExtendedGameTestHelper helper) {
        BlockState state = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState();
        ItemStack drop = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        UniversalPlasticEntity target = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(3.5D, 2.0D + 2.0D / 16.0D, 3.5D)),
            state,
            drop
        );
        UniversalPlasticEntity support = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(4.5D, 2.0D, 3.5D)),
            state,
            drop
        );
        target.setNoGravity(true);
        support.setNoGravity(true);

        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        AABB supportBounds = support.plasticraft$getCollisionBox().bounds();
        player.moveTo(
            supportBounds.minX + player.getBbWidth() * 0.5D,
            supportBounds.maxY,
            supportBounds.getCenter().z
        );
        player.setOnGround(true);
        Vec3 targetStart = target.position();
        double standingY = player.getY();

        player.move(MoverType.SELF, new Vec3(-0.30D, -0.08D, 0.0D));

        check(target.getX() < targetStart.x - 0.20D, "unbonded raised plastic was not pushed");
        check(Math.abs(player.getY() - standingY) < EPSILON, "player stepped onto a pushable raised plastic member");
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x5x7", floor = true)
    @TestHolder(description = "A side-pushed plastic snaps to a nearby observer and triggers its vanilla pulse")
    static void sidePushedPlasticSnapsToAndTriggersObserver(ExtendedGameTestHelper helper) {
        BlockPos observerPos = new BlockPos(5, 1, 3);
        helper.setBlock(
            observerPos,
            Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.WEST)
        );
        double initialGap = 1.0D / 32.0D;
        UniversalPlasticEntity target = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(4.5D - initialGap, 1.0D, 3.5D)),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack()
        );
        target.setNoGravity(true);
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setNoGravity(true);
        AABB targetBounds = target.plasticraft$getCollisionBox().bounds();
        player.moveTo(
            targetBounds.minX - player.getBbWidth() * 0.5D,
            targetBounds.minY,
            targetBounds.getCenter().z
        );
        BlockPos absoluteObserverPos = helper.absolutePos(observerPos);
        double targetStartX = target.getX();

        helper.startSequence()
            .thenIdle(5)
            .thenExecute(() -> {
                check(!helper.getBlockState(observerPos).getValue(ObserverBlock.POWERED),
                    "observer did not settle before the plastic push");
                check(!helper.getLevel().getBlockTicks().hasScheduledTick(absoluteObserverPos, Blocks.OBSERVER),
                    "observer retained an unrelated scheduled pulse before the plastic push");
                player.setOnGround(true);
                player.move(MoverType.SELF, new Vec3(0.01D, 0.0D, 0.0D));
                AABB movedBounds = target.plasticraft$getCollisionBox().bounds();
                double remainingGap = absoluteObserverPos.getX() - movedBounds.maxX;
                check(target.getX() - targetStartX > 0.025D,
                    "plastic did not extend its short side push to the nearby observer");
                check(remainingGap >= -EPSILON && remainingGap <= 1.0E-5D,
                    "plastic left a visible gap before the observer: " + remainingGap);
                check(helper.getLevel().getBlockTicks().hasScheduledTick(absoluteObserverPos, Blocks.OBSERVER),
                    "plastic contact did not schedule the observer pulse");
            })
            .thenIdle(2)
            .thenExecute(() -> check(
                helper.getBlockState(observerPos).getValue(ObserverBlock.POWERED),
                "observer did not power after detecting the plastic contact"
            ))
            .thenExecute(() -> {
                target.discard();
                player.discard();
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "9x5x7", floor = true)
    @TestHolder(description = "Reloading a plastic that already touches an observer does not retrigger its pulse")
    static void reloadedPlasticContactDoesNotRetriggerObserver(ExtendedGameTestHelper helper) {
        BlockPos observerPos = new BlockPos(5, 1, 3);
        helper.setBlock(
            observerPos,
            Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.WEST)
        );
        BlockPos absoluteObserverPos = helper.absolutePos(observerPos);
        UniversalPlasticEntity[] plastic = new UniversalPlasticEntity[1];

        helper.startSequence()
            .thenIdle(5)
            .thenExecute(() -> {
                check(!helper.getBlockState(observerPos).getValue(ObserverBlock.POWERED),
                    "observer did not settle before the plastic spawn");
                check(!helper.getLevel().getBlockTicks().hasScheduledTick(absoluteObserverPos, Blocks.OBSERVER),
                    "observer retained an unrelated scheduled pulse before the plastic spawn");
                plastic[0] = createUniversal(
                    helper,
                    helper.absoluteVec(new Vec3(4.5D, 1.0D, 3.5D)),
                    PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
                    PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack()
                );
                plastic[0].setNoGravity(true);
            })
            .thenIdle(3)
            .thenExecute(() -> check(
                helper.getBlockState(observerPos).getValue(ObserverBlock.POWERED),
                "fresh plastic contact did not power the observer"
            ))
            .thenIdle(2)
            .thenExecute(() -> {
                check(!helper.getBlockState(observerPos).getValue(ObserverBlock.POWERED),
                    "observer did not finish its fresh-contact pulse before reload");
                check(!helper.getLevel().getBlockTicks().hasScheduledTick(absoluteObserverPos, Blocks.OBSERVER),
                    "observer retained a scheduled pulse before reload");
                UniversalPlasticEntity previous = plastic[0];
                CompoundTag saved = previous.saveWithoutId(new CompoundTag());
                previous.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                UniversalPlasticEntity loaded = new UniversalPlasticEntity(
                    PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
                    helper.getLevel()
                );
                loaded.load(saved);
                loaded.setNoGravity(true);
                check(helper.getLevel().addFreshEntity(loaded), "failed to restore the saved plastic entity");
                plastic[0] = loaded;
            })
            .thenIdle(5)
            .thenExecute(() -> {
                check(!helper.getBlockState(observerPos).getValue(ObserverBlock.POWERED),
                    "reloaded plastic contact powered the observer");
                check(!helper.getLevel().getBlockTicks().hasScheduledTick(absoluteObserverPos, Blocks.OBSERVER),
                    "reloaded plastic contact scheduled an observer pulse");
                plastic[0].discard();
            })
            .thenIdle(2)
            .thenExecute(() -> check(
                helper.getBlockState(observerPos).getValue(ObserverBlock.POWERED),
                "leaving a rehydrated plastic contact did not power the observer"
            ))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "9x7x7", floor = true)
    @TestHolder(description = "Pushing away from a plastic support leaves enough room for the player to fall")
    static void sidePushFromPlasticEdgeLeavesFallClearance(ExtendedGameTestHelper helper) {
        BlockState state = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState();
        ItemStack drop = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        UniversalPlasticEntity support = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D)),
            state,
            drop
        );
        UniversalPlasticEntity target = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(4.5D, 2.0D + 2.0D / 16.0D, 3.5D)),
            state,
            drop
        );
        support.setNoGravity(true);
        target.setNoGravity(true);

        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        AABB supportBounds = support.plasticraft$getCollisionBox().bounds();
        player.moveTo(
            supportBounds.maxX - player.getBbWidth() * 0.5D,
            supportBounds.maxY,
            supportBounds.getCenter().z
        );
        player.setOnGround(true);
        double startY = player.getY();
        double targetStartX = target.getX();

        StringBuilder pushProgress = new StringBuilder();
        for (int step = 0; step < 5; step++) {
            player.move(MoverType.SELF, new Vec3(0.12D, -0.08D, 0.0D));
            pushProgress.append(step).append(':').append(target.getX() - targetStartX).append(' ');
        }
        check(
            target.getX() > targetStartX + 0.59D,
            "plastic target stopped before the player left its support: target=" + target.position()
                + ", start=" + targetStartX + ", player=" + player.position()
                + ", playerBox=" + player.getBoundingBox()
                + ", supportBox=" + support.plasticraft$getCollisionBox().bounds()
                + ", progress=" + pushProgress
        );

        player.move(MoverType.SELF, new Vec3(0.0D, -0.30D, 0.0D));
        check(player.getY() < startY - 0.20D, "edge push left too little space for the player to fall");
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "Bonded universal plastic preserves display, collision, and recovered colour")
    static void bondedStatePreservesColour(ExtendedGameTestHelper helper) {
        BlockPos relativePos = new BlockPos(3, 2, 3);
        BlockPos pos = helper.absolutePos(relativePos);
        helper.setBlock(relativePos.below(), Blocks.STONE);
        BlockState displayState = PlasticraftBlocks.UNIVERSAL_PLASTIC.get()
            .defaultBlockState()
            .setValue(DyeableMaterial.COLOR, DyeColor.BLUE);
        ItemStack drop = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        PlasticMeltColor.set(drop, DyeColor.BLUE);
        UniversalPlasticEntity source = createUniversal(helper, pos.getCenter().add(0.0D, -0.5D, 0.0D), displayState, drop);
        source.setNoGravity(true);

        BlockState fixedState = displayState.setValue(AbstractPlasticEntityBlock.BONDED, true);
        helper.setBlock(relativePos, fixedState);
        check(
            helper.getLevel().getBlockEntity(pos) instanceof BondedEntityBlockEntity,
            "bonded universal block did not create its block entity"
        );
        BondedEntityBlockEntity bonded = (BondedEntityBlockEntity) helper.getLevel().getBlockEntity(pos);
        check(
            bonded.initialize(
                source,
                displayState,
                Direction.UP,
                PlasticEntityOrientation.DEFAULT,
                source.isNoGravity()
            ),
            "bonded universal block could not capture its entity"
        );
        source.discard();

        Entity renderEntity = bonded.getOrCreateRenderEntity();
        check(renderEntity instanceof UniversalPlasticEntity, "bonded state restored the wrong entity type");
        UniversalPlasticEntity restored = (UniversalPlasticEntity) renderEntity;
        check(
            restored.getDisplayState().getValue(DyeableMaterial.COLOR) == DyeColor.BLUE,
            "bonded display state lost its colour"
        );
        check(PlasticMeltColor.get(restored.getDropStack()) == DyeColor.BLUE, "bonded drop stack lost its colour");
        VoxelShape shape = helper.getLevel().getBlockState(pos).getCollisionShape(helper.getLevel(), pos);
        check(close(shape.bounds().getYsize(), 0.875D), "bonded collision no longer matches the 14 px model");
        check(
            shape == helper.getLevel().getBlockState(pos).getCollisionShape(helper.getLevel(), pos),
            "bonded collision shape was rebuilt for an unchanged model"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("9x9x9")
    @TestHolder(description = "Plastic geometry supports large concave products and interaction-only planes")
    static void generalizedEntityGeometry(ExtendedGameTestHelper helper) {
        VoxelShape concave = Shapes.or(
            Shapes.box(0.0D, 0.0D, 0.0D, 3.0D, 1.0D, 1.0D),
            Shapes.box(0.0D, 0.0D, 2.0D, 3.0D, 1.0D, 3.0D),
            Shapes.box(0.0D, 0.0D, 1.0D, 1.0D, 1.0D, 2.0D)
        );
        Vec3 pivot = new Vec3(1.5D, 1.5D, 1.5D);
        Vec3 origin = new Vec3(1.5D, 0.0D, 1.5D);
        PlasticEntityGeometry geometry = PlasticEntityGeometry.of(concave, concave, pivot, origin);
        BlockPos occupied = helper.absolutePos(new BlockPos(4, 2, 4));

        Vec3 floorPosition = geometry.placementPosition(occupied, PlasticEntityOrientation.DEFAULT);
        PlasticEntityCollisionBox floorBox = geometry.collisionBoxAt(
            floorPosition,
            PlasticEntityOrientation.DEFAULT
        );
        check(close(floorBox.bounds().getXsize(), 3.0D), "large geometry width was clamped to one block");
        check(close(floorBox.bounds().getYsize(), 1.0D), "large geometry height changed");
        check(close(floorBox.bounds().getZsize(), 3.0D), "large geometry length was clamped to one block");
        check(close(floorBox.bounds().minY, occupied.getY()), "large geometry did not touch the floor face");
        check(
            close(floorBox.bounds().getCenter().x, occupied.getX() + 0.5D)
                && close(floorBox.bounds().getCenter().z, occupied.getZ() + 0.5D),
            "large geometry was not centered on the clicked cell"
        );

        Vec3 translation = floorPosition.subtract(origin);
        VoxelShape hollowProbe = Shapes.box(1.25D, 0.2D, 1.25D, 2.75D, 0.8D, 1.75D)
            .move(translation.x, translation.y, translation.z);
        check(
            !Shapes.joinIsNotEmpty(floorBox.shape(), hollowProbe, BooleanOp.AND),
            "concave geometry filled its empty interior"
        );

        for (Direction face : Direction.values()) {
            for (int turn = 0; turn < 4; turn++) {
                PlasticEntityOrientation orientation = new PlasticEntityOrientation(face, turn);
                check(
                    geometry.oriented(orientation) == geometry.oriented(orientation),
                    "oriented geometry was not cached for " + face + "/" + turn
                );
            }
        }

        PlasticEntityOrientation east = new PlasticEntityOrientation(Direction.EAST, 0);
        Vec3 eastPosition = geometry.placementPosition(occupied, east);
        AABB eastBounds = geometry.boundingBoxAt(eastPosition, east);
        check(close(eastBounds.minX, occupied.getX()), "large wall geometry did not touch the east face");
        check(close(eastBounds.getXsize(), 1.0D), "large wall rotation used the old unit-cube bounds");
        check(close(eastBounds.getYsize(), 3.0D), "large wall rotation lost its vertical extent");

        VoxelShape planeInteraction = Shapes.box(0.0D, 1.499D, 0.0D, 3.0D, 1.501D, 3.0D);
        PlasticEntityGeometry plane = PlasticEntityGeometry.of(Shapes.empty(), planeInteraction, pivot, origin);
        PlasticEntityCollisionBox planeBox = plane.collisionBoxAt(
            plane.placementPosition(occupied, PlasticEntityOrientation.DEFAULT),
            PlasticEntityOrientation.DEFAULT
        );
        check(planeBox.shape().isEmpty(), "interaction-only plane gained physical collision");
        check(planeBox.components().isEmpty(), "interaction-only plane created collision boxes");
        check(close(planeBox.bounds().getXsize(), 3.0D), "interaction-only plane lost its query bounds");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 requested = new Vec3(0.35D, -0.2D, 0.1D);
        check(
            planeBox.collide(player, requested, helper.getLevel(), List.of()).equals(requested),
            "interaction-only plane clipped physical movement"
        );
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("11x7x7")
    @TestHolder(description = "Universal plastic inherits buoyancy and sliding-rail transport")
    static void commonPlasticPhysics(ExtendedGameTestHelper helper) {
        for (int y = 1; y <= 4; y++) {
            for (int x = 1; x <= 4; x++) {
                for (int z = 1; z <= 4; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
            }
        }
        BlockState cyanState = PlasticraftBlocks.UNIVERSAL_PLASTIC.get()
            .defaultBlockState()
            .setValue(DyeableMaterial.COLOR, DyeColor.CYAN);
        ItemStack cyanDrop = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        PlasticMeltColor.set(cyanDrop, DyeColor.CYAN);
        UniversalPlasticEntity floating = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(2.5D, 1.2D, 2.5D)),
            cyanState,
            cyanDrop
        );
        double startY = floating.getY();

        for (int x = 5; x <= 9; x++) {
            helper.setBlock(
                new BlockPos(x, 1, 5),
                ModBlocks.SLIDING_RAIL.get().defaultBlockState().setValue(SlidingRailBlock.AXIS, Direction.Axis.X)
            );
        }
        UniversalPlasticEntity sliding = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(6.5D, 2.0D, 5.5D)),
            cyanState,
            cyanDrop
        );
        sliding.setDeltaMovement(0.22D, 0.0D, 0.0D);

        helper.runAfterDelay(10, () -> {
            check(floating.getY() > startY + 0.1D, "universal plastic did not rise in water");
            check(sliding.getDeltaMovement().x > 0.20D, "universal plastic lost speed on a sliding rail");
            check(Math.abs(sliding.getDeltaMovement().z) < EPSILON, "universal plastic drifted across the rail");
            helper.succeed();
        });
    }

    private static void placeMelt(ExtendedGameTestHelper helper, BlockPos relativePos, DyeColor color) {
        placeMeltAt(helper, helper.absolutePos(relativePos), color);
    }

    private static void placeMeltAt(ExtendedGameTestHelper helper, BlockPos pos, DyeColor color) {
        helper.getLevel().setBlockAndUpdate(pos, PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT.get().defaultBlockState());
        check(
            helper.getLevel().getBlockEntity(pos) instanceof UniversalPlasticMeltBlockEntity,
            "melt block entity was not created"
        );
        ((UniversalPlasticMeltBlockEntity) helper.getLevel().getBlockEntity(pos)).setColor(color);
    }

    private static UniversalPlasticEntity createUniversal(
        ExtendedGameTestHelper helper,
        Vec3 position,
        BlockState state,
        ItemStack drop
    ) {
        return createUniversal(helper, position, state, drop, PlasticEntityOrientation.DEFAULT);
    }

    private static UniversalPlasticEntity createUniversal(
        ExtendedGameTestHelper helper,
        Vec3 position,
        BlockState state,
        ItemStack drop,
        PlasticEntityOrientation orientation
    ) {
        UniversalPlasticEntity entity = new UniversalPlasticEntity(
            PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
            helper.getLevel(),
            position,
            state,
            drop,
            orientation
        );
        check(helper.getLevel().addFreshEntity(entity), "failed to add universal plastic entity");
        return entity;
    }

    private static void assertProduct(ExtendedGameTestHelper helper, BlockPos relativePos, DyeColor color) {
        assertProduct(helper.getBlockState(relativePos), color);
    }

    private static void assertProduct(BlockState state, DyeColor color) {
        check(state.is(PlasticraftBlocks.UNIVERSAL_PLASTIC.get()), "cooling trigger did not create universal plastic");
        check(state.getValue(DyeableMaterial.COLOR) == color, "cooling trigger lost the melt colour");
    }

    private static void assertNoGranules(ExtendedGameTestHelper helper) {
        AABB bounds = new AABB(
            helper.absoluteVec(Vec3.ZERO),
            helper.absoluteVec(new Vec3(11.0D, 8.0D, 11.0D))
        );
        int granules = helper.getLevel().getEntitiesOfClass(
            ItemEntity.class,
            bounds,
            item -> item.getItem().is(PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE.get())
        ).stream().mapToInt(item -> item.getItem().getCount()).sum();
        check(granules == 0, "world solidification produced " + granules + " plastic granules");
    }

    private static ItemStack findInventoryStack(Player player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) <= EPSILON;
    }

    private static boolean contains(AABB outer, AABB inner) {
        return inner.minX >= outer.minX - EPSILON
            && inner.minY >= outer.minY - EPSILON
            && inner.minZ >= outer.minZ - EPSILON
            && inner.maxX <= outer.maxX + EPSILON
            && inner.maxY <= outer.maxY + EPSILON
            && inner.maxZ <= outer.maxZ + EPSILON;
    }

    private static boolean sameBounds(AABB first, AABB second) {
        return close(first.minX, second.minX)
            && close(first.minY, second.minY)
            && close(first.minZ, second.minZ)
            && close(first.maxX, second.maxX)
            && close(first.maxY, second.maxY)
            && close(first.maxZ, second.maxZ);
    }

    private static double attachmentContact(AABB bounds, Direction attachmentFace) {
        return switch (attachmentFace) {
            case DOWN -> bounds.maxY;
            case UP -> bounds.minY;
            case NORTH -> bounds.maxZ;
            case SOUTH -> bounds.minZ;
            case WEST -> bounds.maxX;
            case EAST -> bounds.minX;
        };
    }

    private static double clickedFaceCoordinate(BlockPos clicked, Direction face) {
        return switch (face) {
            case DOWN -> clicked.getY();
            case UP -> clicked.getY() + 1.0D;
            case NORTH -> clicked.getZ();
            case SOUTH -> clicked.getZ() + 1.0D;
            case WEST -> clicked.getX();
            case EAST -> clicked.getX() + 1.0D;
        };
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
