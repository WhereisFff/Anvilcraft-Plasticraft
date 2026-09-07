package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltFluidBlock;
import dev.anvilcraft.plasticraft.block.UniversalPlasticSolidification;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.UniversalPlasticMeltBlockEntity;
import dev.anvilcraft.plasticraft.entity.ClearPlasticEntity;
import dev.anvilcraft.plasticraft.entity.ClearPlasticBeaconInteraction;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.ResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityCollisionBox;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.MoldedPlasticDemoItemStacks;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.dubhe.anvilcraft.block.sliding.SlidingRailBlock;
import dev.dubhe.anvilcraft.block.HeaterBlock;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.util.AccelerateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

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
    @EmptyTemplate(value = "13x7x7", floor = true)
    @TestHolder(description = "Molded tanks and cauldrons retain lava in free and bonded forms")
    static void moldedFluidContainersRetainLava(ExtendedGameTestHelper helper) {
        assertMoldedTankRetainsLava(helper, PlasticMaterial.UNIVERSAL, new BlockPos(2, 2, 3));
        assertMoldedTankRetainsLava(helper, PlasticMaterial.HEAT_RESISTANT, new BlockPos(6, 2, 3));
        assertBondedMoldedTankRetainsLava(helper, new BlockPos(10, 2, 3));
        assertBondedMoldedCauldronRetainsLava(helper, new BlockPos(10, 2, 5));
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "11x6x7", floor = true)
    @TestHolder(description = "Lit campfires and active electric heaters destroy non-heat-resistant plastic entities")
    static void hotSupportsDamagePlasticEntities(ExtendedGameTestHelper helper) {
        assertHeatSourceDamage(
            helper,
            new BlockPos(2, 2, 3),
            Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true),
            "campfire"
        );
        assertHeatSourceDamage(
            helper,
            new BlockPos(5, 2, 3),
            Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true),
            "soul campfire"
        );
        assertHeatSourceDamage(
            helper,
            new BlockPos(8, 2, 3),
            ModBlocks.HEATER.get().defaultBlockState()
                .setValue(HeaterBlock.POWERED, false)
                .setValue(HeaterBlock.OVERLOAD, false),
            "electric heater"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "Every clear plastic dye state provides its matching beacon beam color")
    static void clearPlasticDyeStatesTintBeaconBeams(ExtendedGameTestHelper helper) {
        BlockPos relativePos = new BlockPos(2, 2, 2);
        BlockPos pos = helper.absolutePos(relativePos);
        BlockPos beaconPos = pos.below();
        for (DyeColor color : DyeColor.values()) {
            helper.setBlock(
                relativePos,
                PlasticraftBlocks.CLEAR_PLASTIC.get().defaultBlockState().setValue(DyeableMaterial.COLOR, color)
            );
            Integer beamColor = helper.getLevel()
                .getBlockState(pos)
                .getBeaconColorMultiplier(helper.getLevel(), pos, beaconPos);
            check(
                Integer.valueOf(color.getTextureDiffuseColor()).equals(beamColor),
                "clear plastic " + color.getName() + " did not match its beacon beam color"
            );
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 100)
    @EmptyTemplate(value = "5x7x5", floor = true)
    @TestHolder(description = "A placed clear plastic entity tints a beacon beam above the world surface")
    static void placedClearPlasticTintsBeaconBeam(ExtendedGameTestHelper helper) {
        BlockPos beaconPos = new BlockPos(2, 2, 2);
        BlockPos plasticPos = new BlockPos(2, 4, 2);
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.IRON_BLOCK);
            }
        }
        helper.setBlock(beaconPos, Blocks.BEACON);
        BlockState displayState = PlasticraftBlocks.CLEAR_PLASTIC.get().defaultBlockState()
            .setValue(DyeableMaterial.COLOR, DyeColor.YELLOW);
        ItemStack drop = PlasticraftBlocks.CLEAR_PLASTIC.asStack();
        PlasticMeltColor.set(drop, DyeColor.YELLOW);
        ClearPlasticEntity plastic = new ClearPlasticEntity(
            PlasticraftEntities.CLEAR_PLASTIC.get(),
            helper.getLevel(),
            helper.absolutePos(plasticPos).getCenter().add(0.75D, -0.5D, 0.0D),
            displayState,
            drop,
            PlasticEntityOrientation.DEFAULT
        );
        plastic.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(plastic), "failed to add clear plastic entity above beacon");

        helper.runAfterDelay(1, () -> {
            BlockPos absolutePlasticPos = helper.absolutePos(plasticPos);
            check(
                plastic.getBoundingBox().intersects(new AABB(absolutePlasticPos)),
                "edge-overlapping clear plastic entity did not enter the beacon block"
            );
            check(
                ClearPlasticBeaconInteraction.beamColorAt(helper.getLevel(), absolutePlasticPos) == null,
                "edge-overlapping clear plastic entity unexpectedly tinted the beacon beam"
            );
            plastic.setPos(absolutePlasticPos.getCenter().add(0.0D, -0.5D, 0.0D));
        });

        helper.runAfterDelay(90, () -> {
            check(helper.getLevel().getBlockState(helper.absolutePos(plasticPos)).isAir(),
                "clear plastic entity unexpectedly left a block placeholder");
            check(
                plastic.getDisplayState().getValue(DyeableMaterial.COLOR) == DyeColor.YELLOW,
                "clear plastic entity lost its yellow display state"
            );
            check(helper.getLevel().getBlockEntity(helper.absolutePos(beaconPos)) instanceof BeaconBlockEntity,
                "beacon block entity was not created");
            BeaconBlockEntity beacon = (BeaconBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(beaconPos));
            check(!beacon.getBeamSections().isEmpty(), "beacon did not find the clear plastic entity");
            int expectedColor = DyeColor.YELLOW.getTextureDiffuseColor();
            String sectionColors = beacon.getBeamSections().stream()
                .map(section -> String.format("%08X", section.getColor()))
                .toList()
                .toString();
            check(
                beacon.getBeamSections().getLast().getColor() == expectedColor,
                "clear plastic entity did not produce the yellow stained-glass beam colour: expected=%08X, actual=%08X, "
                    .formatted(
                        expectedColor,
                        beacon.getBeamSections().getLast().getColor()
                    )
                    + "sections=" + sectionColors
                    + ", entityColor=%08X".formatted(
                        ClearPlasticBeaconInteraction.beamColorAt(
                            helper.getLevel(),
                            helper.absolutePos(plasticPos)
                        )
                    )
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "World melt solidification creates a colour-preserving 16x16x14 product once")
    static void solidificationTransaction(ExtendedGameTestHelper helper) {
        BlockPos relativePos = new BlockPos(3, 2, 3);
        BlockPos pos = helper.absolutePos(relativePos);
        placeMelt(helper, relativePos, DyeColor.PURPLE);

        check(UniversalPlasticSolidification.solidify(helper.getLevel(), pos), "melt did not solidify");
        check(!UniversalPlasticSolidification.solidify(helper.getLevel(), pos), "melt solidified twice");
        assertCooledProduct(helper, pos, PlasticMaterial.UNIVERSAL, DyeColor.PURPLE);
        assertNoGranules(helper);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "Engineering melt solidifies into a 16x16x14 engineering product with its colour")
    static void engineeringSolidificationPreservesMaterial(ExtendedGameTestHelper helper) {
        BlockPos relativePos = new BlockPos(3, 2, 3);
        BlockPos pos = helper.absolutePos(relativePos);
        helper.getLevel().setBlockAndUpdate(
            pos,
            PlasticraftBlocks.ENGINEERING_PLASTIC_MELT.get().defaultBlockState()
        );
        check(helper.getLevel().getBlockEntity(pos) instanceof UniversalPlasticMeltBlockEntity,
            "engineering melt block entity was not created");
        ((UniversalPlasticMeltBlockEntity) helper.getLevel().getBlockEntity(pos)).setColor(DyeColor.LIGHT_BLUE);

        check(UniversalPlasticSolidification.solidify(helper.getLevel(), pos), "engineering melt did not solidify");
        assertCooledProduct(helper, pos, PlasticMaterial.ENGINEERING, DyeColor.LIGHT_BLUE);
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

        assertCooledProduct(
            helper,
            helper.absolutePos(waterMelt),
            PlasticMaterial.UNIVERSAL,
            DyeColor.CYAN
        );
        assertCooledProduct(
            helper,
            helper.absolutePos(coolantMelt),
            PlasticMaterial.UNIVERSAL,
            DyeColor.RED
        );
        assertCooledProduct(helper, rainMelt, PlasticMaterial.UNIVERSAL, DyeColor.LIME);
        assertNoGranules(helper);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x6x9", floor = true)
    @TestHolder(description = "Universal plastic item placement, persistence, magnetism, and hammer recovery preserve material data")
    static void itemEntityAndHammerRoundTrip(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(4, 1, 4);
        helper.setBlock(support, Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = PlasticMaterial.UNIVERSAL.productStack(DyeColor.MAGENTA);
        DyeColor expectedDisplayColor = DyeableMaterial.supportsDyeing(PlasticItemData.getMaterial(stack))
            ? DyeColor.MAGENTA
            : DyeColor.WHITE;
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
        check(close(collision.getYsize(), 1.0D), "entity collision height drifted from the model");
        check(close(collision.getZsize(), 1.0D), "entity collision length drifted from the model");
        check(sameBounds(entity.getBoundingBox(), collision), "entity broad-phase box did not follow its geometry");
        check(
            entity.getDisplayState().getValue(DyeableMaterial.COLOR) == expectedDisplayColor,
            "placed entity display state did not follow the material colour capability"
        );
        check(PlasticMeltColor.get(entity.getDropStack()) == DyeColor.MAGENTA, "placed entity drop lost its colour");

        CompoundTag saved = entity.saveWithoutId(new CompoundTag());
        UniversalPlasticEntity loaded = new UniversalPlasticEntity(PlasticraftEntities.UNIVERSAL_PLASTIC.get(), helper.getLevel());
        loaded.load(saved);
        check(
            loaded.getDisplayState().getValue(DyeableMaterial.COLOR) == expectedDisplayColor,
            "saved entity display state did not preserve the material colour capability"
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
        check(close(rotatedCollision.getXsize(), 1.0D), "west collision is not 16 px wide");
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
    @TestHolder(description = "Full-sized universal plastic rotates around its bounds centre without shifting")
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
        check(sameBounds(upsideDown, upright), "up/down rotation moved the full-cube collision");
        check(
            entity.plasticraft$getRotationCenter().equals(upsideDown.getCenter()),
            "universal plastic world rotation center differs from its collision center"
        );
        check(entity.canHammerRotateTo(PlasticEntityOrientation.DEFAULT), "centered up/down rotation was rejected");

        PlasticEntityOrientation west = new PlasticEntityOrientation(Direction.WEST, 0);
        AABB westBounds = geometry.collisionBoxAt(entity.position(), west).bounds();
        check(sameBounds(westBounds, upright), "side rotation moved the full-cube collision");
        check(entity.canHammerRotateTo(west), "clear full-cube side rotation was rejected");

        entity.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 35)
    @EmptyTemplate(value = "7x8x7", floor = true)
    @TestHolder(description = "A falling anvil settles on a full-sized plastic entity")
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
            check(!falling.isAlive(), "falling anvil remained an entity after hitting full-sized plastic");
            check(
                helper.getBlockState(new BlockPos(3, 3, 3)).is(Blocks.ANVIL),
                "falling anvil did not settle on full-sized plastic"
            );
            int drops = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                support.getBoundingBox().inflate(3.0D),
                item -> item.getItem().is(Items.ANVIL)
            ).stream().mapToInt(item -> item.getItem().getCount()).sum();
            check(drops == 0, "full-sized plastic collision produced " + drops + " anvil drops");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 35)
    @EmptyTemplate(value = "11x7x7", floor = true)
    @TestHolder(description = "普通铁砧和树脂砧放在标准塑料上保持承托，移走塑料后开始下落")
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

        UniversalPlasticEntity resinSupport = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(7.5D, 2.0D, 3.5D)),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.getDefaultState(),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack()
        );
        resinSupport.setNoGravity(true);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        ItemStack resinStack = PlasticraftBlocks.RESIN_ANVIL.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, resinStack);
        AABB supportBounds = resinSupport.plasticraft$getCollisionBox().bounds();
        Vec3 topFace = new Vec3(supportBounds.getCenter().x, supportBounds.maxY, supportBounds.getCenter().z);
        InteractionResult placement = resinSupport.interactAt(
            player, topFace.subtract(resinSupport.position()), InteractionHand.MAIN_HAND
        );
        check(placement.consumesAction() && resinStack.isEmpty(),
            "placing a resin anvil on the solid plastic top face failed or did not consume one item");
        ResinAnvilEntity resinAnvil = helper.getLevel().getEntitiesOfClass(
            ResinAnvilEntity.class, new AABB(resinSupport.blockPosition().above()).inflate(0.1D)
        ).stream().findFirst().orElseThrow(() ->
            new GameTestAssertException("top-face placement did not create a resin anvil entity")
        );

        helper.startSequence()
            .thenIdle(8)
            .thenExecute(() -> {
                check(resinAnvil.isAlive()
                        && Math.abs(resinAnvil.plasticraft$getCollisionBox().bounds().minY - supportBounds.maxY) < 0.01D,
                    "placed resin anvil did not remain supported by the solid plastic top face");
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
            .thenExecute(() -> {
                support.discard();
                resinSupport.discard();
            })
            .thenIdle(4)
            .thenExecute(() -> {
                check(resinAnvil.isAlive()
                        && resinAnvil.plasticraft$getCollisionBox().bounds().minY < supportBounds.maxY - 0.1D,
                    "placed resin anvil did not start falling after its plastic support was removed");
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
    @TestHolder(description = "A side-pushed full-size plastic reaches an observer and triggers its vanilla pulse")
    static void sidePushedPlasticSnapsToAndTriggersObserver(ExtendedGameTestHelper helper) {
        BlockPos observerPos = new BlockPos(5, 2, 3);
        helper.setBlock(
            observerPos,
            Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.WEST)
        );
        double initialGap = 0.25D;
        UniversalPlasticEntity target = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(4.5D - initialGap, 2.0D, 3.5D)),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack()
        );
        target.setNoGravity(true);
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
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
                player.move(MoverType.SELF, new Vec3(0.12D, -0.08D, 0.0D));
                double advance = target.getX() - targetStartX;
                check(advance > 0.10D && advance < initialGap - 0.02D,
                    "plastic did not complete the first side-push step: " + advance);
            })
            .thenIdle(1)
            .thenExecute(() -> {
                player.setOnGround(true);
                player.move(MoverType.SELF, new Vec3(0.12D, -0.08D, 0.0D));
                AABB movedBounds = target.plasticraft$getCollisionBox().bounds();
                double remainingGap = absoluteObserverPos.getX() - movedBounds.maxX;
                check(target.getX() - targetStartX > 0.20D,
                    "plastic did not reach the nearby observer during the side push");
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
        BlockPos observerPos = new BlockPos(5, 2, 3);
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
                    helper.absoluteVec(new Vec3(4.5D, 2.0D, 3.5D)),
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
        check(close(shape.bounds().getYsize(), 1.0D), "bonded collision no longer matches the full-cube model");
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

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "A resting plastic entity resumes falling once its supporting block is removed")
    static void restingPlasticResumesFallingWhenSupportIsRemoved(ExtendedGameTestHelper helper) {
        BlockPos supportPos = new BlockPos(3, 2, 3);
        helper.setBlock(supportPos, Blocks.STONE);
        UniversalPlasticEntity plastic = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(3.5D, 3.0D, 3.5D)),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack()
        );
        double restY = plastic.getY();

        helper.startSequence()
            .thenIdle(8)
            .thenExecute(() -> {
                check(plastic.plasticraft$isResting(), "block-supported plastic never entered rest");
                // 抽掉支撑与唤醒断言必须在同一刻完成，否则无法区分「即时唤醒」和「靠错峰复核兜底」。
                helper.setBlock(supportPos, Blocks.AIR);
                check(!plastic.plasticraft$isResting(), "plastic stayed asleep after its support block was removed");
            })
            .thenIdle(10)
            .thenExecute(() -> {
                check(plastic.getY() < restY - 0.5D, "woken plastic did not resume falling");
                check(plastic.plasticraft$isResting(), "plastic never settled again on the floor");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "A block write next to a resting plastic entity wakes it and it settles again")
    static void restingPlasticWakesOnNeighbourBlockWriteAndSettlesAgain(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity plastic = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D)),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack()
        );
        double restY = plastic.getY();
        double restX = plastic.getX();

        helper.startSequence()
            .thenIdle(8)
            .thenExecute(() -> {
                check(plastic.plasticraft$isResting(), "block-supported plastic never entered rest");
                helper.setBlock(new BlockPos(4, 2, 3), Blocks.STONE);
                check(!plastic.plasticraft$isResting(), "plastic stayed asleep after a neighbouring block write");
            })
            .thenIdle(10)
            .thenExecute(() -> {
                // 唤醒后必须重新收敛。若这里仍然清醒，说明唤醒与休眠在互相打架。
                check(plastic.plasticraft$isResting(), "plastic never returned to rest after being woken");
                check(close(plastic.getY(), restY), "woken plastic drifted vertically");
                check(close(plastic.getX(), restX), "woken plastic drifted horizontally");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 50)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "Moving one plastic entity wakes the resting plastic entity beside it")
    static void restingPlasticWakesWhenAdjacentPlasticMoves(ExtendedGameTestHelper helper) {
        BlockState state = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState();
        ItemStack drop = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        UniversalPlasticEntity mover = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(2.5D, 2.0D, 3.5D)),
            state,
            drop
        );
        UniversalPlasticEntity neighbour = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D)),
            state,
            drop
        );
        double neighbourY = neighbour.getY();

        helper.startSequence()
            .thenIdle(8)
            .thenExecute(() -> {
                check(mover.plasticraft$isResting(), "the moving side of the pair never entered rest");
                check(neighbour.plasticraft$isResting(), "the neighbouring side of the pair never entered rest");
                mover.setPos(mover.position().add(0.0D, 1.0D, 0.0D));
                check(!neighbour.plasticraft$isResting(), "a resting plastic ignored its neighbour moving away");
            })
            .thenIdle(14)
            .thenExecute(() -> {
                check(mover.plasticraft$isResting(), "the displaced plastic never settled back onto the floor");
                check(neighbour.plasticraft$isResting(), "the woken neighbour never returned to rest");
                check(close(neighbour.getY(), neighbourY), "the woken neighbour was displaced by its neighbour moving");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "A piston still pushes a resting plastic entity")
    static void restingPlasticIsStillMovedByAPiston(ExtendedGameTestHelper helper) {
        BlockPos pistonPos = new BlockPos(1, 2, 2);
        BlockPos occupied = new BlockPos(2, 2, 2);
        helper.setBlock(
            pistonPos,
            Blocks.PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST)
        );
        UniversalPlasticEntity plastic = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D)),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack()
        );

        helper.startSequence()
            .thenIdle(8)
            .thenExecute(() -> {
                check(plastic.plasticraft$isResting(), "block-supported plastic never entered rest");
                // 从活塞背面供电，避免红石方块本身落在休眠实体的登记格内而混淆唤醒来源。
                helper.getLevel().setBlockAndUpdate(
                    helper.absolutePos(pistonPos.west()),
                    Blocks.REDSTONE_BLOCK.defaultBlockState()
                );
            })
            .thenIdle(8)
            .thenExecute(() -> check(
                plastic.plasticraft$getAnchorBlockPos().equals(helper.absolutePos(occupied.east())),
                "a resting plastic entity was not pushed by the piston"
            ))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "A resting plastic entity still carries a standing player")
    static void restingPlasticKeepsSupportingAStandingPlayer(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity plastic = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D)),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack()
        );

        helper.startSequence()
            .thenIdle(8)
            .thenExecute(() -> {
                check(plastic.plasticraft$isResting(), "block-supported plastic never entered rest");
                AABB collision = plastic.plasticraft$getCollisionBox().bounds();
                Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                player.setPos(plastic.getX(), collision.maxY, plastic.getZ());
                double standingY = player.getY();
                player.move(MoverType.SELF, new Vec3(0.0D, -0.08D, 0.0D));
                check(close(player.getY(), standingY), "player fell through a resting plastic entity");
                check(player.verticalCollisionBelow, "a resting plastic entity reported no lower collision");
                player.discard();
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "A resting plastic entity wakes on a raw velocity write and on a pushing entity")
    static void restingPlasticWakesOnExternalImpulse(ExtendedGameTestHelper helper) {
        BlockState state = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState();
        ItemStack drop = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        UniversalPlasticEntity impulsed = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)),
            state,
            drop
        );
        UniversalPlasticEntity pushed = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(6.5D, 2.0D, 6.5D)),
            state,
            drop
        );
        double impulsedX = impulsed.getX();

        helper.startSequence()
            .thenIdle(8)
            .thenExecute(() -> {
                check(impulsed.plasticraft$isResting(), "the impulse target never entered rest");
                check(pushed.plasticraft$isResting(), "the push target never entered rest");
                // 爆炸击退与命令只写速度，既不经过 push(Entity) 也不改变错峰复核关心的任何条件。
                impulsed.push(0.4D, 0.0D, 0.0D);
                check(!impulsed.plasticraft$isResting(), "a resting plastic entity swallowed a raw velocity impulse");
                Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                AABB collision = pushed.plasticraft$getCollisionBox().bounds();
                player.setPos(collision.maxX + 0.4D, collision.minY, pushed.getZ());
                pushed.push(player);
                check(!pushed.plasticraft$isResting(), "a resting plastic entity ignored a pushing entity");
                player.discard();
            })
            .thenIdle(4)
            .thenExecute(() -> check(
                impulsed.getX() > impulsedX + 0.1D,
                "a woken plastic entity did not travel under its impulse"
            ))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 60)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "An entity-supported plastic stays awake and a settled one stops broadcasting motion")
    static void entitySupportedPlasticStaysAwakeWithoutMotionSpam(ExtendedGameTestHelper helper) {
        BlockState state = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState();
        ItemStack drop = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        UniversalPlasticEntity lower = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D)),
            state,
            drop
        );
        UniversalPlasticEntity upper = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(3.5D, 3.0D, 3.5D)),
            state,
            drop
        );
        double upperY = upper.getY();

        helper.startSequence()
            .thenIdle(10)
            .thenExecute(() -> {
                check(lower.plasticraft$isResting(), "the block-supported carrier never entered rest");
                check(!upper.plasticraft$isResting(), "an entity-supported plastic entered rest");
                // 重力每刻给速度加负值又被 move() 裁回零，逐刻置位 hurtMarked 会让每个静止实体
                // 每秒广播二十个运动包。清醒但静止的实体同样不允许广播。
                helper.failIfEver(() -> {
                    check(!upper.plasticraft$isResting(), "an entity-supported plastic entered rest");
                    check(!upper.hurtMarked, "a settled plastic entity broadcast a motion packet while standing still");
                    check(!lower.hurtMarked, "a resting plastic entity broadcast a motion packet");
                });
            })
            .thenIdle(20)
            .thenExecute(() -> check(close(upper.getY(), upperY), "an entity-supported plastic slid off its carrier"))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "A resting plastic entity still holds a vanilla falling block above it")
    static void restingPlasticKeepsSupportingFallingBlocks(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity plastic = createUniversal(
            helper,
            helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D)),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack()
        );
        BlockPos sandPos = new BlockPos(3, 3, 3);

        helper.startSequence()
            .thenIdle(8)
            .thenExecute(() -> {
                check(plastic.plasticraft$isResting(), "block-supported plastic never entered rest");
                helper.setBlock(sandPos, Blocks.SAND);
            })
            .thenIdle(12)
            .thenExecute(() -> {
                // 休眠期间跳过 updateSupportChecks，登记必须在唤醒的那一刻完成并在再次休眠后保留。
                check(plastic.plasticraft$isResting(), "plastic never returned to rest under the sand");
                check(helper.getBlockState(sandPos).is(Blocks.SAND), "sand fell through a resting plastic entity");
                check(
                    helper.getLevel().getEntitiesOfClass(
                        FallingBlockEntity.class,
                        new AABB(helper.absolutePos(sandPos)).inflate(2.0D),
                        entity -> entity.getBlockState().is(Blocks.SAND)
                    ).isEmpty(),
                    "sand became a falling entity above a resting plastic entity"
                );
            })
            .thenSucceed();
    }

    private static void placeMelt(ExtendedGameTestHelper helper, BlockPos relativePos, DyeColor color) {
        placeMeltAt(helper, helper.absolutePos(relativePos), color);
    }

    private static void assertMoldedTankRetainsLava(
        ExtendedGameTestHelper helper,
        PlasticMaterial material,
        BlockPos relativePos
    ) {
        UniversalPlasticEntity tank = newMoldedProduct(
            helper,
            material,
            relativePos,
            MoldingProductTypes.TANK_ID,
            "tank"
        );
        check(helper.getLevel().addFreshEntity(tank), "failed to add " + material.key() + " molded tank entity");
        int capacity = tank.getMoldedFluidHandler().getTankCapacity(0);
        int water = capacity / 2;
        check(tank.getMoldedFluidHandler().fill(new FluidStack(Fluids.WATER, water), IFluidHandler.FluidAction.EXECUTE)
                == water,
            material.key() + " molded tank rejected water before lava");
        check(tank.getMoldedFluidHandler().fill(
            new FluidStack(Fluids.LAVA, capacity - water),
            IFluidHandler.FluidAction.EXECUTE
        ) == capacity - water, material.key() + " molded tank reported the wrong lava transfer");
        check(!tank.isRemoved(), material.key() + " molded tank was destroyed by lava");
        check(tank.getMoldedFluidHandler().getFluidInTank(0).is(Fluids.WATER),
            material.key() + " molded tank lost water after filling lava");
        check(tank.getMoldedFluidHandler().getFluidInTank(1).is(Fluids.LAVA),
            material.key() + " molded tank did not retain lava");
    }

    private static void assertBondedMoldedTankRetainsLava(ExtendedGameTestHelper helper, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        BlockState fixedState = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState()
            .setValue(AbstractPlasticEntityBlock.BONDED, true);
        helper.setBlock(relativePos, fixedState);
        check(helper.getLevel().getBlockEntity(pos) instanceof BondedEntityBlockEntity,
            "bonded molded tank block entity was not created");
        BondedEntityBlockEntity bonded = (BondedEntityBlockEntity) helper.getLevel().getBlockEntity(pos);
        UniversalPlasticEntity source = newMoldedProduct(
            helper,
            PlasticMaterial.UNIVERSAL,
            relativePos,
            MoldingProductTypes.TANK_ID,
            "tank"
        );
        check(bonded.initialize(
            source,
            source.getDisplayState(),
            Direction.UP,
            PlasticEntityOrientation.DEFAULT,
            true
        ), "bonded molded tank could not be initialized");

        IFluidHandler fluids = bonded.getCapabilityFluidHandler();
        check(fluids != null, "bonded molded tank did not expose its fluid handler");
        int capacity = fluids.getTankCapacity(0);
        int water = capacity / 2;
        check(fluids.fill(new FluidStack(Fluids.WATER, water), IFluidHandler.FluidAction.EXECUTE) == water,
            "bonded molded tank rejected water before lava");
        check(fluids.fill(new FluidStack(Fluids.LAVA, capacity - water), IFluidHandler.FluidAction.EXECUTE)
                == capacity - water,
            "bonded molded tank reported the wrong lava transfer");
        bonded.tickFunctionalEntity();
        check(helper.getBlockState(relativePos).is(fixedState.getBlock()), "bonded molded tank was destroyed by lava");
        check(fluids.getFluidInTank(0).is(Fluids.WATER), "bonded molded tank lost water after filling lava");
        check(fluids.getFluidInTank(1).is(Fluids.LAVA), "bonded molded tank did not retain lava");
    }

    private static void assertBondedMoldedCauldronRetainsLava(ExtendedGameTestHelper helper, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        BlockState fixedState = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState()
            .setValue(AbstractPlasticEntityBlock.BONDED, true);
        helper.setBlock(relativePos, fixedState);
        check(helper.getLevel().getBlockEntity(pos) instanceof BondedEntityBlockEntity,
            "bonded molded cauldron block entity was not created");
        BondedEntityBlockEntity bonded = (BondedEntityBlockEntity) helper.getLevel().getBlockEntity(pos);
        UniversalPlasticEntity source = newMoldedProduct(
            helper,
            PlasticMaterial.UNIVERSAL,
            relativePos,
            MoldingProductTypes.CAULDRON_ID,
            "cauldron"
        );
        check(bonded.initialize(
            source,
            source.getDisplayState(),
            Direction.UP,
            PlasticEntityOrientation.DEFAULT,
            true
        ), "bonded molded cauldron could not be initialized");

        IFluidHandler fluids = bonded.getCapabilityFluidHandler();
        check(fluids != null, "bonded molded cauldron did not expose its fluid handler");
        int amount = Math.min(1000, fluids.getTankCapacity(0));
        check(amount > 0, "bonded molded cauldron had no usable fluid capacity");
        FluidStack lava = new FluidStack(Fluids.LAVA, amount);
        check(fluids.fill(lava, IFluidHandler.FluidAction.SIMULATE) == amount,
            "bonded molded cauldron reported the wrong simulated lava transfer");
        check(fluids.fill(lava, IFluidHandler.FluidAction.EXECUTE) == amount,
            "bonded molded cauldron reported the wrong lava transfer");
        bonded.tickFunctionalEntity();
        check(helper.getBlockState(relativePos).is(fixedState.getBlock()),
            "bonded molded cauldron was destroyed by lava");
        check(fluids.getFluidInTank(0).is(Fluids.LAVA)
                && fluids.getFluidInTank(0).getAmount() == amount,
            "bonded molded cauldron did not retain lava");
    }

    private static void assertHeatSourceDamage(
        ExtendedGameTestHelper helper,
        BlockPos relativeSource,
        BlockState sourceState,
        String sourceName
    ) {
        helper.setBlock(relativeSource, sourceState);
        for (PlasticMaterial material : PlasticMaterial.values()) {
            UniversalPlasticEntity plastic = createPlasticOnHeatSource(helper, material, relativeSource);
            plastic.tick();
            if (material == PlasticMaterial.HEAT_RESISTANT) {
                check(!plastic.isRemoved(), sourceName + " destroyed heat-resistant plastic");
                plastic.discard();
            } else {
                check(plastic.isRemoved(), sourceName + " did not destroy " + material.key() + " plastic");
            }
        }
    }

    private static UniversalPlasticEntity createPlasticOnHeatSource(
        ExtendedGameTestHelper helper,
        PlasticMaterial material,
        BlockPos relativeSource
    ) {
        BlockPos source = helper.absolutePos(relativeSource);
        BlockState sourceState = helper.getLevel().getBlockState(source);
        UniversalPlasticEntity entity = material.createEntity(
            helper.getLevel(),
            new Vec3(source.getX() + 0.5D, source.getY() + 1.0D, source.getZ() + 0.5D),
            material.displayState(DyeColor.WHITE),
            material.productStack(DyeColor.WHITE),
            PlasticEntityOrientation.DEFAULT
        );
        entity.setNoGravity(true);
        double sourceTop = source.getY() + sourceState.getCollisionShape(helper.getLevel(), source).bounds().maxY;
        entity.setPos(entity.getX(), entity.getY() + sourceTop - entity.getBoundingBox().minY, entity.getZ());
        entity.setStartPos(entity.blockPosition());
        check(helper.getLevel().addFreshEntity(entity), "failed to add plastic above " + sourceState.getBlock());
        return entity;
    }

    private static UniversalPlasticEntity newMoldedProduct(
        ExtendedGameTestHelper helper,
        PlasticMaterial material,
        BlockPos relativePos,
        ResourceLocation type,
        String productName
    ) {
        ItemStack stack = MoldedPlasticDemoItemStacks.product(material, type).orElseThrow(
            () -> new GameTestAssertException(material.key() + " " + productName + " demonstration stack was unavailable")
        );
        MoldedPlasticData data = MoldedPlasticData.get(stack).orElseThrow(
            () -> new GameTestAssertException(material.key() + " " + productName + " demonstration stack lost molded data")
        );
        UniversalPlasticEntity product = material.createEntity(
            helper.getLevel(),
            data.geometry().placementPosition(helper.absolutePos(relativePos), PlasticEntityOrientation.DEFAULT),
            material.displayState(DyeColor.WHITE),
            stack,
            PlasticEntityOrientation.DEFAULT
        );
        product.setNoGravity(true);
        return product;
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

    private static UniversalPlasticEntity assertCooledProduct(
        ExtendedGameTestHelper helper,
        BlockPos pos,
        PlasticMaterial material,
        DyeColor color
    ) {
        check(
            !helper.getLevel().getBlockState(pos).is(material.meltBlock()),
            material.key() + " cooling left the melt block in place"
        );
        List<UniversalPlasticEntity> products = helper.getLevel().getEntitiesOfClass(
            UniversalPlasticEntity.class,
            new AABB(pos).inflate(0.25D),
            entity -> entity.getType() == material.entityType()
        );
        check(products.size() == 1, material.key() + " cooling did not create exactly one product entity");
        UniversalPlasticEntity product = products.getFirst();
        check(product.getDisplayState().is(material.productBlock()), material.key() + " cooling used the wrong block");
        check(
            product.getDisplayState().getValue(DyeableMaterial.COLOR) == color,
            material.key() + " cooling lost the display colour"
        );
        ItemStack drop = product.getDropStack();
        check(drop.is(material.productBlock().asItem()), material.key() + " cooled product used the wrong drop item");
        check(
            PlasticItemData.getMaterial(drop).equals(material.key()),
            material.key() + " cooled product lost its material key"
        );
        check(PlasticMeltColor.get(drop) == color, material.key() + " cooled product lost its item colour");
        MoldedPlasticData data = MoldedPlasticData.get(drop).orElseThrow(() ->
            new GameTestAssertException(material.key() + " cooled product has no molded data")
        );
        check(data.material().getFluid() == material.melt(), material.key() + " cooled product used the wrong melt");
        check(
            PlasticMeltColor.get(data.material()) == color,
            material.key() + " cooled product lost its molded colour"
        );
        AABB bounds = data.surfaceBounds();
        check(close(bounds.getXsize(), 1.0D), material.key() + " cooled product width is not 16 px");
        check(close(bounds.getYsize(), 0.875D), material.key() + " cooled product height is not 14 px");
        check(close(bounds.getZsize(), 1.0D), material.key() + " cooled product length is not 16 px");
        AABB collision = product.plasticraft$getCollisionBox().bounds();
        check(close(collision.getXsize(), 1.0D), material.key() + " cooled collision width is not 16 px");
        check(close(collision.getYsize(), 0.875D), material.key() + " cooled collision height is not 14 px");
        check(close(collision.getZsize(), 1.0D), material.key() + " cooled collision length is not 16 px");
        return product;
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
                || item.getItem().is(PlasticraftItems.ENGINEERING_PLASTIC_GRANULE.get())
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
