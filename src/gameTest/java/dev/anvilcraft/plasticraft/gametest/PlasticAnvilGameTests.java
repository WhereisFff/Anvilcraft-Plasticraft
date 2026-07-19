package dev.anvilcraft.plasticraft.gametest;

import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.entity.MagnetizedNodeEntity;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.util.AccelerateManager;
import dev.dubhe.anvilcraft.util.GravityManager;
import dev.dubhe.anvilcraft.util.GravityType;
import dev.anvilcraft.plasticraft.block.PlasticAnvilBlock;
import dev.anvilcraft.plasticraft.entity.PlasticAnvilEntity;
import dev.anvilcraft.plasticraft.entity.PlasticAnvilOrientation;
import dev.anvilcraft.plasticraft.entity.PlasticAnvilPhysics;
import dev.anvilcraft.plasticraft.entity.PlasticPotEntity;
import dev.anvilcraft.plasticraft.init.PlasticBlocks;
import dev.anvilcraft.plasticraft.init.PlasticEntities;
import dev.anvilcraft.plasticraft.item.PlasticAnvilItem;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Runtime coverage for the persistent falling-block physics contract. */
public final class PlasticAnvilGameTests {
    private static final double EPSILON = 1.0E-6D;

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("5x5x5")
    @TestHolder(description = "All 24 plastic-anvil orientations remain unique and orthogonal")
    static void orientationGeometry(ExtendedGameTestHelper helper) {
        Set<Integer> packed = new HashSet<>();
        BlockPos cell = new BlockPos(2, 2, 2);
        Vec3 cellCenter = Vec3.atCenterOf(cell);
        for (Direction face : Direction.values()) {
            for (int turn = 0; turn < 4; turn++) {
                PlasticAnvilOrientation orientation = new PlasticAnvilOrientation(face, turn);
                check(packed.add(Byte.toUnsignedInt(orientation.pack())), "orientation packing collided");
                check(PlasticAnvilOrientation.unpack(orientation.pack()).equals(orientation), "orientation did not round-trip");
                check(orientation.longAxis().getAxis() != face.getAxis(), "long axis is not in the attachment plane");
                check(orientation.orthogonalAxis().getAxis() != face.getAxis(), "cross axis is not in the attachment plane");
                check(orientation.longAxis().getAxis() != orientation.orthogonalAxis().getAxis(), "model basis is not orthogonal");

                Vec3 offset = orientation.collisionCenter(cell).subtract(cellCenter);
                Vec3 expected = Vec3.atLowerCornerOf(face.getNormal())
                    .scale(-PlasticAnvilOrientation.ATTACHMENT_INSET);
                check(close(offset, expected), "collision center does not preserve the requested face gaps");
            }
        }
        check(packed.size() == 24, "expected exactly 24 orientations");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate
    @TestHolder(description = "Floor placement keeps the anvil's long axis perpendicular to the player")
    static void placementFacesLongSideTowardPlayer(ExtendedGameTestHelper helper) {
        for (Direction playerDirection : Direction.Plane.HORIZONTAL) {
            PlasticAnvilOrientation floor = PlasticAnvilOrientation.forPlacement(Direction.UP, playerDirection);
            check(
                floor.longAxis().getAxis() != playerDirection.getAxis(),
                playerDirection + " placement showed the short end to the player"
            );

            PlasticAnvilOrientation ceiling = PlasticAnvilOrientation.forPlacement(Direction.DOWN, playerDirection);
            check(
                ceiling.longAxis().getAxis() != playerDirection.getAxis(),
                playerDirection + " ceiling placement showed the short end to the player"
            );
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "The actual item path attaches a 0.98 entity to each clicked face")
    static void itemPlacesOnAllSixFaces(ExtendedGameTestHelper helper) {
        BlockPos clicked = new BlockPos(3, 3, 3);
        helper.setBlock(clicked, Blocks.STONE);
        for (Direction face : Direction.values()) {
            ItemStack stack = PlasticBlocks.PLASTIC_ANVIL.asStack();
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setYRot(0.0F);
            BlockHitResult hit = new BlockHitResult(
                helper.absolutePos(clicked).getCenter(),
                face,
                helper.absolutePos(clicked),
                false
            );
            InteractionResult result = stack.useOn(new UseOnContext(
                helper.getLevel(),
                player,
                InteractionHand.MAIN_HAND,
                stack,
                hit
            ));
            check(result.consumesAction(), "item placement failed on " + face);

            Vec3 expectedCenter = PlasticAnvilOrientation.forPlacement(face, player)
                .collisionCenter(helper.absolutePos(clicked.relative(face)));
            PlasticAnvilEntity placed = helper.getLevel()
                .getEntitiesOfClass(
                    PlasticAnvilEntity.class,
                    new AABB(expectedCenter, expectedCenter).inflate(0.6D)
                )
                .stream()
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("item did not create an entity on " + face));
            check(close(placed.getBoundingBox().getCenter(), expectedCenter), "collision box was offset on " + face);
            check(Math.abs(placed.getBbWidth() - 0.98F) <= EPSILON, "entity width changed on " + face);
            check(Math.abs(placed.getBbHeight() - 0.98F) <= EPSILON, "entity height changed on " + face);
            placed.discard();
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate("5x8x5")
    @TestHolder(description = "A plastic anvil uses normal falling-block gravity in air and remains a persistent entity")
    static void fallsInAir(ExtendedGameTestHelper helper) {
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 6.0D, 2.5D));
        double startY = anvil.getY();
        helper.runAfterDelay(10, () -> {
            check(anvil.isAlive(), "plastic anvil was discarded or converted into a block");
            check(anvil.getY() < startY - 0.2D, "plastic anvil did not fall in air");
            check(anvil.getY() > startY - 3.5D, "plastic anvil fell faster than normal falling-block gravity");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate("5x5x5")
    @TestHolder(description = "A submerged plastic anvil rises instead of falling")
    static void risesInWater(ExtendedGameTestHelper helper) {
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(x, y, z, Blocks.WATER);
                }
            }
        }
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 1.2D, 2.5D));
        double startY = anvil.getY();
        helper.runAfterDelay(10, () -> {
            check(anvil.isAlive(), "submerged plastic anvil was discarded");
            check(anvil.getY() > startY + 0.1D, "plastic anvil did not rise in water");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 90)
    @EmptyTemplate("7x8x7")
    @TestHolder(description = "A buoyant plastic anvil settles half-submerged at the water surface")
    static void settlesHalfSubmergedAtWaterSurface(ExtendedGameTestHelper helper) {
        fillWater(helper, 1, 5, 1, 4, 1, 5);
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(3.5D, 1.2D, 3.5D));
        double surfaceY = helper.absolutePos(new BlockPos(0, 5, 0)).getY();
        helper.runAfterDelay(60, () -> {
            double centerY = anvil.getBoundingBox().getCenter().y;
            check(Math.abs(centerY - surfaceY) < 0.16D, "plastic anvil did not settle at half immersion");
            check(Math.abs(anvil.getDeltaMovement().y) < 0.08D, "plastic anvil did not settle vertically");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 90)
    @EmptyTemplate("7x8x7")
    @TestHolder(description = "A solid block above the water surface stops buoyant plastic entities")
    static void waterSurfaceCeilingStopsBuoyantAnvil(ExtendedGameTestHelper helper) {
        fillWater(helper, 1, 5, 1, 4, 1, 5);
        helper.setBlock(3, 5, 3, Blocks.STONE);
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(3.5D, 1.2D, 3.5D));
        double ceilingY = helper.absolutePos(new BlockPos(3, 5, 3)).getY();
        helper.runAfterDelay(60, () -> {
            check(anvil.getBoundingBox().maxY <= ceilingY + 0.002D, "buoyant anvil crossed the ceiling block");
            check(Math.abs(anvil.getDeltaMovement().y) < 0.08D, "ceiling contact retained upward velocity");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 90)
    @EmptyTemplate("7x8x7")
    @TestHolder(description = "Plastic block items share the entity's half-submerged buoyancy")
    static void plasticItemSettlesAtWaterSurface(ExtendedGameTestHelper helper) {
        fillWater(helper, 1, 5, 1, 4, 1, 5);
        Vec3 start = helper.absoluteVec(new Vec3(3.5D, 1.2D, 3.5D));
        ItemEntity item = new ItemEntity(
            helper.getLevel(),
            start.x,
            start.y,
            start.z,
            PlasticBlocks.PLASTIC_ANVIL.asStack()
        );
        check(helper.getLevel().addFreshEntity(item), "failed to add buoyant plastic item");
        double surfaceY = helper.absolutePos(new BlockPos(0, 5, 0)).getY();
        helper.runAfterDelay(60, () -> {
            check(item.isAlive(), "buoyant plastic item disappeared");
            check(
                Math.abs(item.getBoundingBox().getCenter().y - surfaceY) < 0.16D,
                "plastic item did not settle at half immersion"
            );
            check(Math.abs(item.getDeltaMovement().y) < 0.08D, "plastic item did not settle vertically");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 50)
    @EmptyTemplate(value = "5x7x5", floor = true)
    @TestHolder(description = "An entity head supports a falling plastic anvil without converting it to a block")
    static void entityHeadSupportsFallingAnvil(ExtendedGameTestHelper helper) {
        Zombie support = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 1.0D, 2.5D));
        support.setNoGravity(true);
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 4.3D, 2.5D));
        helper.runAfterDelay(30, () -> {
            double gap = anvil.getBoundingBox().minY - support.getBoundingBox().maxY;
            check(anvil.isAlive(), "supported plastic anvil stopped being an entity");
            check(Math.abs(gap) <= PlasticAnvilPhysics.SUPPORT_PROBE_DEPTH + 0.02D, "anvil did not settle on the entity head");
            check(helper.getLevel().getBlockState(anvil.blockPosition()).isAir(), "supported anvil converted into a block");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 50)
    @EmptyTemplate(value = "5x7x5", floor = true)
    @TestHolder(description = "A server-ticked player can stand on the plastic anvil without pushing it aside")
    static void playerStandsWithoutPushingAnvil(ExtendedGameTestHelper helper) {
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 1.0D, 2.5D));
        anvil.setNoGravity(true);
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 playerPosition = helper.absoluteVec(new Vec3(2.68D, 1.98D, 2.61D));
        player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z);
        Vec3 start = anvil.position();
        helper.runAfterDelay(25, () -> {
            check(anvil.position().subtract(start).horizontalDistance() < 0.01D, "standing player pushed the anvil sideways");
            check(
                Math.abs(player.getBoundingBox().minY - anvil.getBoundingBox().maxY) < 0.03D,
                "player did not remain on the anvil's top face"
            );
            check(anvil.getDeltaMovement().horizontalDistanceSqr() < 1.0E-6D, "standing player gave the anvil horizontal velocity");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate
    @TestHolder(description = "Orientation, color, drop stack, and gravity survive an entity NBT round trip")
    static void stateSurvivesSaveLoad(ExtendedGameTestHelper helper) {
        PlasticAnvilEntity original = createAnvil(helper, new Vec3(1.5D, 1.0D, 1.5D));
        PlasticAnvilOrientation orientation = new PlasticAnvilOrientation(Direction.WEST, 3);
        original.setOrientation(orientation);
        original.setDisplayState(PlasticAnvilBlock.withColor(original.getDisplayState(), net.minecraft.world.item.DyeColor.CYAN));
        ItemStack drop = PlasticBlocks.PLASTIC_ANVIL.asStack();
        dev.anvilcraft.plasticraft.item.PlasticAnvilItem.setColor(drop, net.minecraft.world.item.DyeColor.CYAN);
        original.setDropStack(drop);

        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        PlasticAnvilEntity loaded = new PlasticAnvilEntity(PlasticEntities.PLASTIC_ANVIL.get(), helper.getLevel());
        loaded.load(saved);
        check(loaded.getOrientation().equals(orientation), "orientation did not survive NBT");
        check(
            loaded.getDisplayState().getValue(PlasticAnvilBlock.COLOR) == net.minecraft.world.item.DyeColor.CYAN,
            "display color did not survive NBT"
        );
        check(
            dev.anvilcraft.plasticraft.item.PlasticAnvilItem.getColor(loaded.getDropStack())
                == net.minecraft.world.item.DyeColor.CYAN,
            "colored drop stack did not survive NBT"
        );
        check(!loaded.isNoGravity(), "gravity flag changed during NBT round trip");
        original.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate
    @TestHolder(description = "Runtime display-state changes are carried by synchronized entity data")
    static void displayStateSynchronizesAtRuntime(ExtendedGameTestHelper helper) {
        PlasticAnvilEntity server = createAnvil(helper, new Vec3(1.5D, 1.0D, 1.5D));
        PlasticAnvilEntity client = new PlasticAnvilEntity(PlasticEntities.PLASTIC_ANVIL.get(), helper.getLevel());
        server.getEntityData().packDirty();

        BlockState cyan = PlasticAnvilBlock.withColor(server.getDisplayState(), net.minecraft.world.item.DyeColor.CYAN);
        server.setDisplayState(cyan);
        var update = server.getEntityData().packDirty();
        check(update != null && !update.isEmpty(), "display-state change did not mark synchronized data dirty");
        client.getEntityData().assignValues(update);

        check(client.getDisplayState().equals(cyan), "client display state did not receive the runtime change");
        check(client.blockState.equals(cyan), "inherited falling-block state diverged after synchronization");
        check(server.getBlockState().equals(cyan), "AnvilCraft-facing block state diverged from the display state");
        server.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("9x5x7")
    @TestHolder(description = "Only magnetized plastic anvils and pots qualify for magnetic acceleration")
    static void magnetizationControlsAcceleration(ExtendedGameTestHelper helper) {
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 2.0D, 2.5D));
        PlasticPotEntity pot = createPot(helper, new Vec3(6.5D, 2.0D, 2.5D), PlasticAnvilOrientation.DEFAULT);
        check(!AccelerateManager.canBeAccelerated(anvil), "unmagnetized plastic anvil entered an acceleration ring");
        check(!AccelerateManager.canBeAccelerated(pot), "unmagnetized plastic pot entered an acceleration ring");
        anvil.setMagnetized(true);
        pot.setMagnetized(true);
        check(AccelerateManager.canBeAccelerated(anvil), "magnetized plastic anvil was rejected by acceleration");
        check(AccelerateManager.canBeAccelerated(pot), "magnetized plastic pot was rejected by acceleration");
        ItemStack drop = pot.getDropStack();
        check(PlasticItemData.isMagnetized(drop), "pot drop lost magnetized state");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x6x7")
    @TestHolder(description = "Shift-using the AnvilCraft magnet magnetizes a plastic pot without spawning a node")
    static void magnetToolMagnetizesPlasticPot(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(3, 1, 3);
        helper.setBlock(support, Blocks.STONE);
        PlasticPotEntity pot = createPot(helper, new Vec3(3.5D, 2.0D, 3.5D), PlasticAnvilOrientation.DEFAULT);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        ItemStack magnet = ModItems.MAGNET.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, magnet);
        BlockHitResult hit = new BlockHitResult(
            helper.absolutePos(support).getCenter(),
            Direction.UP,
            helper.absolutePos(support),
            false
        );

        InteractionResult result = magnet.useOn(new UseOnContext(
            helper.getLevel(),
            player,
            InteractionHand.MAIN_HAND,
            magnet,
            hit
        ));
        check(result.consumesAction(), "magnet did not consume the entity magnetization interaction");
        check(pot.isMagnetized(), "magnet interaction did not mark the plastic pot");
        check(
            helper.getLevel().getEntitiesOfClass(
                MagnetizedNodeEntity.class,
                pot.getBoundingBox().inflate(1.0D),
                Entity::isAlive
            ).isEmpty(),
            "magnet interaction spawned a node instead of magnetizing the pot"
        );
        pot.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("9x6x7")
    @TestHolder(description = "Magnet forces target only magnetized plastic entities")
    static void magnetForcesPlasticEntitiesSelectively(ExtendedGameTestHelper helper) {
        BlockPos magnetPos = new BlockPos(1, 2, 3);
        helper.setBlock(magnetPos, ModBlocks.MAGNET_BLOCK.get().defaultBlockState());
        PlasticAnvilEntity magnetized = createAnvil(helper, new Vec3(4.5D, 2.0D, 3.5D));
        PlasticAnvilEntity plain = createAnvil(helper, new Vec3(6.5D, 2.0D, 3.5D));
        magnetized.setMagnetized(true);

        Vec3 pointForce = magnetized.anvilcraft$getAdditionalGravity(0.04D);
        check(pointForce.x < -PlasticAnvilPhysics.FACE_EPSILON, "magnet block did not pull the magnetized anvil");
        check(plain.anvilcraft$getAdditionalGravity(0.04D).lengthSqr() <= EPSILON * EPSILON,
            "magnet block affected an unmagnetized plastic anvil");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(2.5D, 2.0D, 3.5D)));
        player.setShiftKeyDown(false);
        ItemStack magnet = ModItems.MAGNET.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, magnet);
        Vec3 before = magnetized.getDeltaMovement();
        magnet.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        check(magnetized.getDeltaMovement().x < before.x, "handheld magnet did not pull the magnetized anvil");
        magnetized.discard();
        plain.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("13x6x7")
    @TestHolder(description = "A non-magnetic plastic pot spills full fluid only through a side or bottom opening")
    static void plasticPotSpillsFluidByOrientation(ExtendedGameTestHelper helper) {
        PlasticPotEntity side = createPot(
            helper,
            new Vec3(2.5D, 2.0D, 2.5D),
            new PlasticAnvilOrientation(Direction.EAST, 0)
        );
        side.setNoGravity(true);
        side.getFluidHandler().fill(new FluidStack(Fluids.WATER, PlasticPotEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);

        PlasticPotEntity magnetic = createPot(
            helper,
            new Vec3(7.5D, 2.0D, 2.5D),
            new PlasticAnvilOrientation(Direction.EAST, 0)
        );
        magnetic.setNoGravity(true);
        magnetic.setMagnetized(true);
        magnetic.getFluidHandler().fill(new FluidStack(Fluids.WATER, PlasticPotEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);

        helper.runAfterDelay(3, () -> {
            BlockPos sideTarget = BlockPos.containing(side.getBoundingBox().getCenter()).relative(Direction.EAST);
            BlockPos magneticTarget = BlockPos.containing(magnetic.getBoundingBox().getCenter()).relative(Direction.EAST);
            check(helper.getLevel().getFluidState(sideTarget).isSource(), "side-facing pot did not spill a source fluid");
            check(side.getFluidHandler().getFluid().isEmpty(), "side-facing pot retained spilled fluid");
            check(
                magnetic.getFluidHandler().getFluidAmount() == PlasticPotEntity.CAPACITY,
                "magnetized pot spilled fluid despite being a sealed container"
            );
            check(helper.getLevel().getFluidState(magneticTarget).isEmpty(), "magnetized pot created an external fluid source");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x5x7")
    @TestHolder(description = "Plastic pot item and fluid contents survive entity persistence")
    static void plasticPotContentsSurviveSaveLoad(ExtendedGameTestHelper helper) {
        PlasticPotEntity original = createPot(helper, new Vec3(3.5D, 2.0D, 3.5D), PlasticAnvilOrientation.DEFAULT);
        original.setNoGravity(true);
        original.setMagnetized(true);
        original.getInput().insertItem(0, new ItemStack(Items.COBBLESTONE, 3), false);
        original.getFluidHandler().fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE);

        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        PlasticPotEntity loaded = new PlasticPotEntity(PlasticEntities.PLASTIC_POT.get(), helper.getLevel());
        loaded.load(saved);
        check(loaded.isMagnetized(), "pot magnetized state did not survive NBT");
        check(loaded.getInput().getStackInSlot(0).getCount() == 3, "pot input contents did not survive NBT");
        check(
            loaded.getSyncedItems().stream().mapToInt(ItemStack::getCount).sum() == 3,
            "loaded pot did not rebuild its display-item sync data"
        );
        check(loaded.getFluidHandler().getFluidAmount() == 500, "pot fluid contents did not survive NBT");
        original.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("9x7x9")
    @TestHolder(description = "A plastic pot reuses AnvilCraft recipes from either valid impact direction")
    static void plasticPotProcessesRecipesFromBothImpactDirections(ExtendedGameTestHelper helper) {
        PlasticPotEntity pot = createPot(helper, new Vec3(3.5D, 2.0D, 3.5D), PlasticAnvilOrientation.DEFAULT);
        PlasticAnvilEntity anvil = createAnvil(
            helper,
            new Vec3(3.5D, 3.0D, 3.5D),
            PlasticAnvilOrientation.DEFAULT
        );
        pot.setNoGravity(true);
        anvil.setNoGravity(true);

        pot.getInput().insertItem(0, new ItemStack(Items.STICK), false);
        pot.processAnvilImpact(anvil, Direction.EAST);
        check(pot.getInput().getStackInSlot(0).getCount() == 1, "a non-contact direction processed the recipe");

        pot.processAnvilImpact(anvil, Direction.DOWN);
        check(pot.getInput().getStackInSlot(0).isEmpty(), "anvil-down impact did not consume the pot input");
        check(countItem(pot.getOutput(), Items.DIAMOND) == 1, "anvil-down impact did not write the recipe output");

        pot.getInput().insertItem(0, new ItemStack(Items.STICK), false);
        pot.processAnvilImpact(anvil, Direction.UP);
        check(pot.getInput().getStackInSlot(0).isEmpty(), "pot-up impact did not consume the pot input");
        check(countItem(pot.getOutput(), Items.DIAMOND) == 2, "pot-up impact did not merge the recipe output");

        pot.discard();
        anvil.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 60)
    @EmptyTemplate("7x8x7")
    @TestHolder(description = "A falling plastic anvil physically processes a plastic pot on impact")
    static void fallingPlasticAnvilProcessesPot(ExtendedGameTestHelper helper) {
        PlasticPotEntity pot = createPot(helper, new Vec3(3.5D, 1.0D, 3.5D), PlasticAnvilOrientation.DEFAULT);
        pot.setNoGravity(true);
        pot.getInput().insertItem(0, new ItemStack(Items.STICK), false);
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(3.5D, 5.0D, 3.5D));

        helper.runAfterDelay(40, () -> {
            check(pot.getInput().getStackInSlot(0).isEmpty(), "falling anvil did not process the pot input");
            check(countItem(pot.getOutput(), Items.DIAMOND) == 1, "falling anvil did not create the pot output");
            check(anvil.isAlive(), "ordinary pot processing consumed the plastic anvil");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 60)
    @EmptyTemplate("7x8x7")
    @TestHolder(description = "A plastic pot moving upward physically processes the anvil above it")
    static void risingPlasticPotProcessesAnvil(ExtendedGameTestHelper helper) {
        Vec3 potPosition = helper.absoluteVec(new Vec3(3.5D, 1.0D, 3.5D));
        BlockPos sourceId = helper.absolutePos(new BlockPos(3, 3, 3));
        Vec3 sourceCenter = helper.absoluteVec(new Vec3(3.5D, 3.5D, 3.5D));
        GravityManager.GravitySourceManager.upsertSource(
            helper.getLevel(),
            sourceId,
            sourceCenter,
            new GravityManager.GravitySourceType(20.0D, 8, 0.0D)
        );
        helper.addEndListener(ignored -> GravityManager.GravitySourceManager.removeSource(helper.getLevel(), sourceId));

        PlasticPotEntity pot = createPot(helper, new Vec3(3.5D, 1.0D, 3.5D), PlasticAnvilOrientation.DEFAULT);
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(3.5D, 4.0D, 3.5D));
        anvil.setNoGravity(true);
        pot.getInput().insertItem(0, new ItemStack(Items.STICK), false);
        check(
            Direction.getNearest(GravityManager.getNetGravityVectorForFallingBlock(pot)) == Direction.UP,
            "test source did not create upward pot gravity at " + potPosition
        );

        helper.runAfterDelay(40, () -> {
            check(pot.getInput().getStackInSlot(0).isEmpty(), "rising pot did not process its input against the anvil");
            check(countItem(pot.getOutput(), Items.DIAMOND) == 1, "rising pot did not create the recipe output");
            check(anvil.isAlive(), "ordinary rising-pot processing consumed the plastic anvil");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("9x7x9")
    @TestHolder(description = "A side-attached plastic pot keeps the canonical recipe context")
    static void sideAttachedPlasticPotProcessesRecipes(ExtendedGameTestHelper helper) {
        PlasticAnvilOrientation sideOrientation = new PlasticAnvilOrientation(Direction.EAST, 0);
        PlasticPotEntity pot = createPot(helper, new Vec3(3.5D, 2.0D, 3.5D), sideOrientation);
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(4.5D, 2.0D, 3.5D), sideOrientation);
        pot.setNoGravity(true);
        anvil.setNoGravity(true);
        pot.getInput().insertItem(0, new ItemStack(Items.STICK), false);

        pot.processAnvilImpact(anvil, Direction.WEST);
        check(pot.getInput().getStackInSlot(0).isEmpty(), "side-attached impact did not consume the pot input");
        check(countItem(pot.getOutput(), Items.DIAMOND) == 1, "side-attached impact missed the pot recipe context");

        pot.discard();
        anvil.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("15x7x15")
    @TestHolder(description = "Only an anvil physical bottom against a pot opening processes a side impact")
    static void sideImpactRequiresAnvilBottomAndPotOpening(ExtendedGameTestHelper helper) {
        PlasticAnvilOrientation potOpenEast = new PlasticAnvilOrientation(Direction.EAST, 0);
        PlasticAnvilOrientation anvilBottomWest = new PlasticAnvilOrientation(Direction.EAST, 0);
        PlasticPotEntity validPot = createPot(helper, new Vec3(3.5D, 2.0D, 3.5D), potOpenEast);
        PlasticAnvilEntity validAnvil = createAnvil(helper, new Vec3(5.0D, 2.0D, 3.5D), anvilBottomWest);
        validPot.setNoGravity(true);
        validAnvil.setNoGravity(true);
        validPot.getInput().insertItem(0, new ItemStack(Items.STICK), false);
        validAnvil.setDeltaMovement(-0.65D, 0.0D, 0.0D);

        PlasticPotEntity sideFacePot = createPot(helper, new Vec3(3.5D, 2.0D, 7.5D), potOpenEast);
        PlasticAnvilEntity sideFaceAnvil = createAnvil(
            helper,
            new Vec3(5.0D, 2.0D, 7.5D),
            new PlasticAnvilOrientation(Direction.UP, 0)
        );
        sideFacePot.setNoGravity(true);
        sideFaceAnvil.setNoGravity(true);
        sideFacePot.getInput().insertItem(0, new ItemStack(Items.STICK), false);
        sideFaceAnvil.setDeltaMovement(-0.65D, 0.0D, 0.0D);

        PlasticPotEntity closedFacePot = createPot(
            helper,
            new Vec3(9.5D, 2.0D, 3.5D),
            new PlasticAnvilOrientation(Direction.UP, 0)
        );
        PlasticAnvilEntity closedFaceAnvil = createAnvil(helper, new Vec3(11.0D, 2.0D, 3.5D), anvilBottomWest);
        closedFacePot.setNoGravity(true);
        closedFaceAnvil.setNoGravity(true);
        closedFacePot.getInput().insertItem(0, new ItemStack(Items.STICK), false);
        closedFaceAnvil.setDeltaMovement(-0.65D, 0.0D, 0.0D);

        helper.runAfterDelay(4, () -> {
            check(validPot.getInput().getStackInSlot(0).isEmpty(), "valid side-facing physical faces did not process");
            check(countItem(validPot.getOutput(), Items.DIAMOND) == 1, "valid side impact did not produce its output");
            check(
                sideFacePot.getInput().getStackInSlot(0).getCount() == 1,
                "anvil side face processed against a pot opening"
            );
            check(
                closedFacePot.getInput().getStackInSlot(0).getCount() == 1,
                "anvil bottom processed against a closed pot face"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("5x5x5")
    @TestHolder(description = "The plastic pot entity owns a full one-block collision box")
    static void plasticPotUsesFullBlockCollisionSize(ExtendedGameTestHelper helper) {
        PlasticPotEntity pot = createPot(helper, new Vec3(2.5D, 1.0D, 2.5D), PlasticAnvilOrientation.DEFAULT);
        check(Math.abs(pot.getBbWidth() - 1.0F) < 1.0E-6F, "pot width is not one block");
        check(Math.abs(pot.getBbHeight() - 1.0F) < 1.0E-6F, "pot height is not one block");
        check(Math.abs(pot.getBoundingBox().getXsize() - 1.0D) < EPSILON, "pot bounding box width is not one block");
        check(Math.abs(pot.getBoundingBox().getYsize() - 1.0D) < EPSILON, "pot bounding box height is not one block");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "A DamageAnvil recipe outcome consumes the plastic anvil after pot processing")
    static void plasticPotAppliesDamageAnvilOutcome(ExtendedGameTestHelper helper) {
        PlasticPotEntity pot = createPot(helper, new Vec3(2.5D, 2.0D, 2.5D), PlasticAnvilOrientation.DEFAULT);
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 3.0D, 2.5D), PlasticAnvilOrientation.DEFAULT);
        pot.setNoGravity(true);
        anvil.setNoGravity(true);
        pot.getInput().insertItem(0, new ItemStack(Items.BLAZE_ROD), false);

        pot.processAnvilImpact(anvil, Direction.DOWN);
        check(!anvil.isAlive(), "DamageAnvil outcome left the plastic anvil alive");
        check(pot.getInput().getStackInSlot(0).isEmpty(), "DamageAnvil recipe did not consume the pot input");
        pot.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "A supported plastic anvil follows tangential motion but separates from a falling carrier")
    static void followsThenSeparatesFromEntityHead(ExtendedGameTestHelper helper) {
        Zombie support = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 1.0D, 2.5D));
        support.setNoGravity(true);
        Vec3 anvilPosition = helper.absoluteVec(new Vec3(2.5D, 1.0D + support.getBbHeight(), 2.5D));
        PlasticAnvilEntity anvil = createAnvilAbsolute(helper, anvilPosition);

        helper.runAfterDelay(2, () -> support.setPos(support.getX() + 0.3D, support.getY(), support.getZ()));
        helper.runAfterDelay(4, () -> {
            check(anvil.getX() > anvilPosition.x + 0.2D, "anvil did not follow the entity head sideways");
            support.setPos(support.getX(), support.getY() - 0.3D, support.getZ());
        });
        helper.runAfterDelay(6, () -> {
            check(
                anvil.getBoundingBox().minY > support.getBoundingBox().maxY + 0.15D,
                "anvil remained attached when the supporting entity moved down"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "A buoyant plastic anvil follows an entity foot upward but separates when the entity rises away")
    static void followsThenSeparatesFromEntityFeet(ExtendedGameTestHelper helper) {
        for (int x = 1; x <= 5; x++) {
            for (int y = 1; y <= 5; y++) {
                for (int z = 1; z <= 5; z++) {
                    helper.setBlock(x, y, z, Blocks.WATER);
                }
            }
        }
        Zombie support = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 3.0D, 3.5D));
        support.setNoGravity(true);
        Vec3 supportPosition = support.position();
        Vec3 anvilPosition = new Vec3(supportPosition.x, supportPosition.y - 0.98D, supportPosition.z);
        PlasticAnvilEntity anvil = createAnvilAbsolute(helper, anvilPosition);

        helper.runAfterDelay(2, () -> support.setPos(support.getX() + 0.3D, support.getY(), support.getZ()));
        helper.runAfterDelay(4, () -> {
            check(anvil.getX() > anvilPosition.x + 0.2D, "buoyant anvil did not follow the entity feet sideways");
            support.setPos(support.getX(), support.getY() + 0.3D, support.getZ());
        });
        helper.runAfterDelay(6, () -> {
            check(
                support.getBoundingBox().minY > anvil.getBoundingBox().maxY + 0.15D,
                "buoyant anvil remained attached when the supporting entity moved up"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("5x5x5")
    @TestHolder(description = "A command-placed plastic-anvil block converts to the persistent custom entity")
    static void blockStateConvertsToEntity(ExtendedGameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PlasticBlocks.PLASTIC_ANVIL.get());
        helper.runAfterDelay(5, () -> {
            check(!helper.getBlockState(pos).is(PlasticBlocks.PLASTIC_ANVIL.get()), "compatibility block did not convert");
            helper.assertEntityPresent(PlasticEntities.PLASTIC_ANVIL.get(), pos, 1.5D);
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "A plastic-anvil block under horizontal gravity still creates its custom entity")
    static void blockStateConvertsToEntityUnderHorizontalGravity(ExtendedGameTestHelper helper) {
        assertBlockConvertsUnderGravity(helper, Direction.WEST);
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "A plastic-anvil block under upward gravity still creates its custom entity")
    static void blockStateConvertsToEntityUnderUpwardGravity(ExtendedGameTestHelper helper) {
        assertBlockConvertsUnderGravity(helper, Direction.UP);
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate
    @TestHolder(description = "Top contact does not trigger horizontal soft-push geometry")
    static void topContactIsNotSideContact(ExtendedGameTestHelper helper) {
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(1.5D, 1.0D, 1.5D));
        Zombie top = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(1.5D, 1.98D, 1.5D));
        top.setNoGravity(true);
        check(!PlasticAnvilPhysics.isSideContact(anvil, top), "top face was classified as a side contact");
        top.setPos(anvil.getX(), anvil.getBoundingBox().maxY - 0.005D, anvil.getZ());
        check(!PlasticAnvilPhysics.isSideContact(anvil, top), "slightly embedded feet were classified as a side contact");

        double touchingCenterX = anvil.getBoundingBox().maxX + top.getBbWidth() * 0.5D;
        top.setPos(touchingCenterX, anvil.getY(), anvil.getZ());
        check(PlasticAnvilPhysics.isSideContact(anvil, top), "real side contact was not detected");
        top.setPos(touchingCenterX + 0.19D, anvil.getY(), anvil.getZ());
        check(!PlasticAnvilPhysics.isSideContact(anvil, top), "side push reached across an air gap");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "A supporting head moving into the anvil carries it instead of being collision-clipped")
    static void carrierNormalMovementTransfersToAnvil(ExtendedGameTestHelper helper) {
        Zombie support = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 1.0D, 3.5D));
        support.setNoGravity(true);
        Vec3 anvilPosition = helper.absoluteVec(new Vec3(3.5D, 1.0D + support.getBbHeight(), 3.5D));
        PlasticAnvilEntity anvil = createAnvilAbsolute(helper, anvilPosition);

        helper.runAfterDelay(3, () -> {
            double supportStartY = support.getY();
            double anvilStartY = anvil.getY();
            support.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0.0D, 0.25D, 0.0D));
            check(support.getY() > supportStartY + 0.2D, "carrier was clipped by the supported anvil");
            check(anvil.getY() > anvilStartY + 0.2D, "normal carrier movement was not transferred to the anvil");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "A carrier cannot pass through its anvil when the anvil is blocked by a solid ceiling")
    static void blockedCarrierRemainsBelowAnvil(ExtendedGameTestHelper helper) {
        helper.setBlock(3, 4, 3, Blocks.STONE);
        Zombie support = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 1.0D, 3.5D));
        support.setNoGravity(true);
        PlasticAnvilEntity anvil = createAnvilAbsolute(
            helper,
            helper.absoluteVec(new Vec3(3.5D, 1.0D + support.getBbHeight(), 3.5D))
        );

        helper.runAfterDelay(3, () -> {
            double supportStartY = support.getY();
            double anvilStartY = anvil.getY();
            support.move(MoverType.SELF, new Vec3(0.0D, 0.6D, 0.0D));
            check(support.getY() < supportStartY + 0.35D, "carrier moved through a ceiling-blocked anvil");
            check(anvil.getY() < anvilStartY + 0.35D, "blocked anvil moved into the ceiling");
            check(
                support.getBoundingBox().maxY <= anvil.getBoundingBox().minY + 0.02D,
                "carrier overlapped the blocked anvil"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "Carrier step-up cannot move through an anvil blocked by a low ceiling")
    static void blockedStepUpDoesNotOverlapAnvil(ExtendedGameTestHelper helper) {
        helper.setBlock(3, 1, 3, Blocks.STONE_SLAB);
        helper.setBlock(3, 1, 5, Blocks.STONE_SLAB);
        helper.setBlock(3, 4, 3, Blocks.STONE);
        Zombie baseline = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 1.0D, 5.5D));
        baseline.setNoGravity(true);
        Zombie support = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 1.0D, 3.5D));
        support.setNoGravity(true);
        PlasticAnvilEntity anvil = createAnvilAbsolute(
            helper,
            helper.absoluteVec(new Vec3(2.5D, 1.0D + support.getBbHeight(), 3.5D))
        );

        helper.runAfterDelay(3, () -> {
            Vec3 baselineStart = baseline.position();
            baseline.setOnGround(true);
            baseline.move(MoverType.SELF, new Vec3(0.7D, 0.0D, 0.0D));
            check(
                baseline.getX() > baselineStart.x + 0.4D && baseline.getY() > baselineStart.y + 0.4D,
                "baseline carrier did not exercise the step-up path: start=" + baselineStart
                    + ", end=" + baseline.position() + ", maxUpStep=" + baseline.maxUpStep()
            );

            Vec3 supportStart = support.position();
            support.setOnGround(true);
            support.move(MoverType.SELF, new Vec3(0.7D, 0.0D, 0.0D));
            check(
                support.getY() < supportStart.y + 0.1D,
                "ceiling-blocked carrier was allowed to step through its anvil: start=" + supportStart
                    + ", end=" + support.position() + ", supportBox=" + support.getBoundingBox()
                    + ", anvilBox=" + anvil.getBoundingBox()
            );
            check(
                !support.getBoundingBox().intersects(anvil.getBoundingBox()),
                "step-up carrier overlapped its ceiling-blocked anvil: carrier=" + support.getBoundingBox()
                    + ", anvil=" + anvil.getBoundingBox()
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("9x7x9")
    @TestHolder(description = "Vehicle collision overrides still participate in plastic-anvil carrier movement")
    static void vehicleOverridesCarryAnvil(ExtendedGameTestHelper helper) {
        Vec3 boatPosition = helper.absoluteVec(new Vec3(2.5D, 1.0D, 2.5D));
        Boat boat = new Boat(helper.getLevel(), boatPosition.x, boatPosition.y, boatPosition.z);
        boat.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(boat), "failed to add boat carrier");
        PlasticAnvilEntity boatAnvil = createAnvilAbsolute(
            helper,
            new Vec3(boat.getX(), boat.getBoundingBox().maxY, boat.getZ())
        );

        Vec3 minecartPosition = helper.absoluteVec(new Vec3(6.5D, 1.0D, 6.5D));
        Minecart minecart = new Minecart(
            helper.getLevel(),
            minecartPosition.x,
            minecartPosition.y,
            minecartPosition.z
        );
        minecart.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(minecart), "failed to add minecart carrier");
        PlasticAnvilEntity minecartAnvil = createAnvilAbsolute(
            helper,
            new Vec3(minecart.getX(), minecart.getBoundingBox().maxY, minecart.getZ())
        );

        helper.runAfterDelay(3, () -> {
            double boatStartY = boat.getY();
            double boatAnvilStartY = boatAnvil.getY();
            boat.move(MoverType.SELF, new Vec3(0.0D, 0.2D, 0.0D));
            check(boat.getY() > boatStartY + 0.15D, "boat override remained collision-clipped");
            check(boatAnvil.getY() > boatAnvilStartY + 0.15D, "boat did not carry its anvil");

            double minecartStartY = minecart.getY();
            double minecartAnvilStartY = minecartAnvil.getY();
            minecart.move(MoverType.SELF, new Vec3(0.0D, 0.2D, 0.0D));
            check(minecart.getY() > minecartStartY + 0.15D, "minecart override remained collision-clipped");
            check(minecartAnvil.getY() > minecartAnvilStartY + 0.15D, "minecart did not carry its anvil");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "No-gravity plastic anvils immediately release their previous support relationship")
    static void noGravityReleasesCarrier(ExtendedGameTestHelper helper) {
        Zombie support = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 1.0D, 3.5D));
        support.setNoGravity(true);
        Vec3 anvilPosition = helper.absoluteVec(new Vec3(3.5D, 1.0D + support.getBbHeight(), 3.5D));
        PlasticAnvilEntity anvil = createAnvilAbsolute(helper, anvilPosition);

        helper.runAfterDelay(2, () -> anvil.setNoGravity(true));
        helper.runAfterDelay(4, () -> {
            double startX = anvil.getX();
            support.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0.3D, 0.0D, 0.0D));
            check(Math.abs(anvil.getX() - startX) < 0.05D, "no-gravity anvil retained a stale carrier");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate
    @TestHolder(description = "The effective gravity face is never classified as a lateral soft-push face")
    static void sixAxisSupportFacesAreNotSideContacts(ExtendedGameTestHelper helper) {
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(1.5D, 1.0D, 1.5D));
        Zombie other = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(1.5D, 1.0D, 1.5D));
        other.setNoGravity(true);
        for (Direction gravity : Direction.values()) {
            AABB box = anvil.getBoundingBox();
            double x = box.getCenter().x;
            double y = box.minY;
            double z = box.getCenter().z;
            switch (gravity) {
                case DOWN -> y = box.minY - other.getBbHeight();
                case UP -> y = box.maxY;
                case WEST -> x = box.minX - other.getBbWidth() * 0.5D;
                case EAST -> x = box.maxX + other.getBbWidth() * 0.5D;
                case NORTH -> z = box.minZ - other.getBbWidth() * 0.5D;
                case SOUTH -> z = box.maxZ + other.getBbWidth() * 0.5D;
            }
            other.setPos(x, y, z);
            check(
                !PlasticAnvilPhysics.isSideContact(anvil, other, gravity),
                gravity + " support face was classified as a side contact"
            );
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "5x8x5", floor = true)
    @TestHolder(description = "A plastic anvil preserves the landing block's bounce response")
    static void slimeBlockBounceIsPreserved(ExtendedGameTestHelper helper) {
        helper.setBlock(2, 1, 2, Blocks.SLIME_BLOCK);
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 4.0D, 2.5D));
        helper.runAfterDelay(12, () -> {
            check(anvil.getDeltaMovement().y > 0.01D, "slime block bounce velocity was cleared after move");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 35)
    @EmptyTemplate("15x6x15")
    @TestHolder(description = "Ordinary ground stops a pushed plastic anvil while ice preserves sliding")
    static void surfaceFrictionDistinguishesIce(ExtendedGameTestHelper helper) {
        for (int z = 0; z < 14; z++) {
            helper.setBlock(3, 1, z, Blocks.STONE);
            helper.setBlock(10, 1, z, Blocks.PACKED_ICE);
        }
        PlasticAnvilEntity ordinary = createAnvil(helper, new Vec3(3.5D, 2.0D, 3.5D));
        PlasticAnvilEntity ice = createAnvil(helper, new Vec3(10.5D, 2.0D, 3.5D));
        ordinary.setDeltaMovement(0.0D, 0.0D, 0.35D);
        ice.setDeltaMovement(0.0D, 0.0D, 0.35D);
        double ordinaryStart = ordinary.getZ();
        double iceStart = ice.getZ();
        helper.runAfterDelay(8, () -> {
            check(ordinary.getDeltaMovement().horizontalDistance() < 0.02D, "ordinary ground kept sliding velocity");
            check(ice.getDeltaMovement().horizontalDistance() > 0.08D, "ice did not preserve sliding velocity");
            check(ice.getZ() - iceStart > ordinary.getZ() - ordinaryStart + 0.35D, "ice and ordinary ground behaved alike");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("11x6x11")
    @TestHolder(description = "A walking player can continuously transfer side movement to a plastic body")
    static void playerSidePushTransfersEveryMovement(ExtendedGameTestHelper helper) {
        for (int x = 2; x <= 8; x++) {
            helper.setBlock(x, 1, 5, Blocks.STONE);
        }
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(5.5D, 2.0D, 5.5D));
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.71D, 2.0D, 5.5D));
        player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z);

        helper.runAfterDelay(3, () -> {
            double anvilStart = anvil.getX();
            double playerStart = player.getX();
            player.move(MoverType.SELF, new Vec3(0.18D, 0.0D, 0.0D));
            player.move(MoverType.SELF, new Vec3(0.18D, 0.0D, 0.0D));
            check(
                player.getX() - playerStart > 0.30D,
                "plastic body clipped the player's repeated side movement: player=" + (player.getX() - playerStart)
                    + ", anvil=" + (anvil.getX() - anvilStart)
                    + ", playerBox=" + player.getBoundingBox()
                    + ", anvilBox=" + anvil.getBoundingBox()
            );
            check(
                anvil.getX() - anvilStart > 0.30D,
                "repeated player movement was not transferred to the plastic body: player="
                    + (player.getX() - playerStart) + ", anvil=" + (anvil.getX() - anvilStart)
            );
            check(
                Math.abs((anvil.getX() - anvilStart) - (player.getX() - playerStart)) < 0.03D,
                "plastic body did not stay against the moving player"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("11x6x11")
    @TestHolder(description = "Diagonal walking transfers only the contacted face normal to a plastic body")
    static void diagonalPlayerMovementSlidesAlongPlasticFace(ExtendedGameTestHelper helper) {
        for (int x = 2; x <= 8; x++) {
            for (int z = 3; z <= 8; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(5.5D, 2.0D, 5.5D));
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.71D, 2.0D, 5.5D));
        player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z);

        helper.runAfterDelay(3, () -> {
            Vec3 anvilStart = anvil.position();
            Vec3 playerStart = player.position();
            player.move(MoverType.SELF, new Vec3(0.18D, 0.0D, 0.16D));
            player.move(MoverType.SELF, new Vec3(0.18D, 0.0D, 0.16D));
            Vec3 anvilMovement = anvil.position().subtract(anvilStart);
            Vec3 playerMovement = player.position().subtract(playerStart);
            check(anvilMovement.x > 0.30D, "diagonal walking did not push the contacted face forward");
            check(Math.abs(anvilMovement.z) < 0.03D, "plastic body followed the player's lateral strafe");
            check(playerMovement.z > 0.25D, "plastic face incorrectly blocked tangential player movement");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("15x6x11")
    @TestHolder(description = "A player can push a touching chain of independent plastic bodies")
    static void playerPushesPlasticEntityChain(ExtendedGameTestHelper helper) {
        for (int x = 2; x <= 12; x++) {
            for (int z = 4; z <= 6; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        PlasticAnvilEntity rear = createAnvil(helper, new Vec3(5.5D, 2.0D, 5.5D));
        PlasticPotEntity front = createPot(helper, new Vec3(6.49D, 2.0D, 5.5D), PlasticAnvilOrientation.DEFAULT);
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.71D, 2.0D, 5.5D));
        player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z);

        helper.runAfterDelay(3, () -> {
            double playerStart = player.getX();
            double rearStart = rear.getX();
            double frontStart = front.getX();
            player.move(MoverType.SELF, new Vec3(0.18D, 0.0D, 0.0D));
            player.move(MoverType.SELF, new Vec3(0.18D, 0.0D, 0.0D));
            check(player.getX() - playerStart > 0.30D, "front plastic body blocked the player through the chain");
            check(rear.getX() - rearStart > 0.30D, "rear plastic body did not receive the player push");
            check(front.getX() - frontStart > 0.30D, "front plastic body did not receive the propagated push");
            check(
                Math.abs((rear.getX() - rearStart) - (front.getX() - frontStart)) < 0.03D,
                "plastic bodies in the chain did not preserve their independent spacing: player="
                    + (player.getX() - playerStart)
                    + ", rear=" + (rear.getX() - rearStart)
                    + ", front=" + (front.getX() - frontStart)
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("15x7x9")
    @TestHolder(description = "Sneak-use treats every plastic entity face as a solid placement surface")
    static void shiftUsePlacesBlocksOnPlasticEntityFaces(ExtendedGameTestHelper helper) {
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(3.5D, 2.0D, 3.5D));
        PlasticPotEntity pot = createPot(helper, new Vec3(8.5D, 2.0D, 3.5D), PlasticAnvilOrientation.DEFAULT);
        anvil.setNoGravity(true);
        pot.setNoGravity(true);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);

        ItemStack stone = new ItemStack(Items.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stone);
        InteractionResult blockResult = anvil.interactAt(
            player,
            new Vec3(anvil.getBbWidth() * 0.5D, anvil.getBbHeight() * 0.5D, 0.0D),
            InteractionHand.MAIN_HAND
        );
        check(blockResult.consumesAction(), "sneak-use did not place the held vanilla block");
        check(helper.getBlockState(new BlockPos(4, 2, 3)).is(Blocks.STONE), "vanilla block was not placed on the entity's east face");

        ItemStack plastic = PlasticBlocks.PLASTIC_ANVIL.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, plastic);
        InteractionResult entityResult = pot.interactAt(
            player,
            new Vec3(pot.getBbWidth() * 0.5D, pot.getBbHeight() * 0.5D, 0.0D),
            InteractionHand.MAIN_HAND
        );
        check(entityResult.consumesAction(), "sneak-use did not place the held plastic entity item");
        AABB targetCell = new AABB(helper.absolutePos(new BlockPos(9, 2, 3)));
        check(
            helper.getLevel().getEntitiesOfClass(PlasticAnvilEntity.class, targetCell.inflate(0.05D)).size() == 1,
            "plastic entity was not placed on the pot's east face"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "15x8x7", floor = true)
    @TestHolder(description = "High-speed acceleration recentering uses collision and contributes to landing distance")
    static void acceleratedRecenteringCannotBypassFloor(ExtendedGameTestHelper helper) {
        AtomicInteger eventCount = new AtomicInteger();
        float[] fallDistance = {0.0F};
        AccelerateManager.AccelerationEntry acceleration = new AccelerateManager.AccelerationEntry(
            helper.absolutePos(new BlockPos(2, -1, 3)),
            Direction.EAST,
            0.0D
        );
        AccelerationTestAnvilEntity anvil = new AccelerationTestAnvilEntity(
            PlasticEntities.PLASTIC_ANVIL.get(),
            helper.getLevel(),
            helper.absoluteVec(new Vec3(2.5D, 5.0D, 3.5D)),
            acceleration
        );
        anvil.setDeltaMovement(new Vec3(6.0D, 0.0D, 0.0D));
        helper.addTemporaryListener((AnvilEvent.OnLand event) -> {
            if (event.getEntity() == anvil) {
                eventCount.incrementAndGet();
                fallDistance[0] = event.getFallDistance();
            }
        });
        check(helper.getLevel().addFreshEntity(anvil), "failed to add accelerated plastic anvil");
        double startY = anvil.getY();

        helper.runAfterDelay(2, () -> {
            check(
                anvil.getY() < startY - 1.0D && PlasticAnvilPhysics.hasBlockSupport(anvil, Direction.DOWN),
                "acceleration correction did not stop on the floor: startY=" + startY
                    + ", box=" + anvil.getBoundingBox()
            );
            check(
                eventCount.get() == 1,
                "expected one acceleration landing event, got " + eventCount.get()
                    + ", y=" + anvil.getY()
                    + ", x=" + anvil.getX()
                    + ", delta=" + anvil.getDeltaMovement()
                    + ", distance=" + anvil.anvilcraft$getFallDistance()
                    + ", gravity=" + GravityManager.getNetGravityVectorForFallingBlock(anvil)
                    + ", floorSupport=" + PlasticAnvilPhysics.hasBlockSupport(anvil, Direction.DOWN)
            );
            check(fallDistance[0] > 1.0F, "acceleration correction was absent from directional fall distance");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 50)
    @EmptyTemplate(value = "5x8x5", floor = true)
    @TestHolder(description = "AnvilCraft receives exactly one landing-edge event with accumulated fall distance")
    static void landingEventFiresOnce(ExtendedGameTestHelper helper) {
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 5.0D, 2.5D));
        AtomicInteger eventCount = new AtomicInteger();
        float[] fallDistance = {0.0F};
        helper.addTemporaryListener((AnvilEvent.OnLand event) -> {
            if (event.getEntity() == anvil) {
                eventCount.incrementAndGet();
                fallDistance[0] = event.getFallDistance();
            }
        });
        helper.runAfterDelay(35, () -> {
            check(anvil.isAlive(), "landing event converted or discarded the persistent anvil");
            check(eventCount.get() == 1, "expected one landing edge event, got " + eventCount.get());
            check(fallDistance[0] > 1.0F, "landing event did not carry accumulated fall distance");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 50)
    @EmptyTemplate(value = "5x8x5", floor = true)
    @TestHolder(description = "A non-bottom face impact never posts an AnvilCraft landing recipe event")
    static void nonBottomImpactDoesNotFireLandingEvent(ExtendedGameTestHelper helper) {
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 5.0D, 2.5D));
        anvil.setOrientation(new PlasticAnvilOrientation(Direction.DOWN, 0));
        AtomicInteger eventCount = new AtomicInteger();
        helper.addTemporaryListener((AnvilEvent.OnLand event) -> {
            if (event.getEntity() == anvil) eventCount.incrementAndGet();
        });
        helper.runAfterDelay(35, () -> {
            check(anvil.isAlive(), "non-bottom impact discarded the plastic anvil");
            check(eventCount.get() == 0, "non-bottom impact posted a landing recipe event");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "A plastic anvil created in floor contact does not fabricate a landing impact")
    static void stationaryContactDoesNotFireLandingEvent(ExtendedGameTestHelper helper) {
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 0.0D, 2.5D));
        AtomicInteger eventCount = new AtomicInteger();
        helper.addTemporaryListener((AnvilEvent.OnLand event) -> {
            if (event.getEntity() == anvil) eventCount.incrementAndGet();
        });
        helper.runAfterDelay(12, () -> {
            check(eventCount.get() == 0, "stationary contact fabricated " + eventCount.get() + " landing event(s)");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 50)
    @EmptyTemplate(value = "5x8x5", floor = true)
    @TestHolder(description = "AnvilCraft landing damage consumes a plastic anvil without duplicating its item")
    static void landingDamageConsumesEntityWithoutDrop(ExtendedGameTestHelper helper) {
        PlasticAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 5.0D, 2.5D));
        helper.addTemporaryListener((AnvilEvent.OnLand event) -> {
            if (event.getEntity() == anvil) {
                event.setAnvilDamage(true);
            }
        });
        helper.runAfterDelay(35, () -> {
            check(!anvil.isAlive(), "landing-damage request left the plastic anvil alive");
            helper.assertItemEntityCountIsAtLeast(
                PlasticBlocks.PLASTIC_ANVIL.get().asItem(),
                new BlockPos(2, 1, 2),
                3.0D,
                0
            );
            check(
                helper.getLevel().getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class,
                    new AABB(helper.absolutePos(new BlockPos(2, 1, 2))).inflate(3.0D),
                    item -> item.getItem().is(PlasticBlocks.PLASTIC_ANVIL.get().asItem())
                ).isEmpty(),
                "landing damage duplicated the plastic-anvil item"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "Horizontal gravity settles on and follows an entity support face")
    static void horizontalGravityDirectionIsSupported(ExtendedGameTestHelper helper) {
        BlockPos sourceId = helper.absolutePos(new BlockPos(0, 2, 3));
        Vec3 anvilPosition = helper.absoluteVec(new Vec3(3.5D, 1.5D, 3.5D));
        GravityManager.GravitySourceType sourceType = new GravityManager.GravitySourceType(10.0D, 8, 0.0D);
        GravityManager.GravitySourceManager.upsertSource(
            helper.getLevel(),
            sourceId,
            helper.absoluteVec(new Vec3(0.5D, 1.99D, 3.5D)),
            sourceType
        );
        helper.addEndListener(ignored -> GravityManager.GravitySourceManager.removeSource(helper.getLevel(), sourceId));

        Zombie support = helper.spawnWithNoFreeWill(
            EntityType.ZOMBIE,
            new Vec3(3.5D - 0.49D - EntityType.ZOMBIE.getWidth() * 0.5D, 1.0D, 3.5D)
        );
        support.setNoGravity(true);
        PlasticAnvilEntity anvil = createAnvilAbsolute(helper, anvilPosition);
        Vec3 gravity = GravityManager.getNetGravityVectorForFallingBlock(anvil);
        check(Direction.getNearest(gravity) == Direction.WEST, "test source did not create westward gravity");

        helper.runAfterDelay(3, () -> {
            EntityType<?> supportType = support.getType();
            check(
                PlasticAnvilPhysics.findSupport(anvil, Direction.WEST) == support,
                "westward gravity did not acquire the entity support face: anvil=" + anvil.getBoundingBox()
                    + ", support=" + support.getBoundingBox()
                    + ", candidate=" + PlasticAnvilPhysics.isSupportCandidate(anvil, support, Direction.WEST)
                    + ", collidable=" + anvil.canCollideWith(support)
                    + ", supportType=" + supportType
                    + ", gravity=" + GravityManager.getNetGravityVectorForFallingBlock(anvil)
            );
            double supportStartX = support.getX();
            double anvilStartX = anvil.getX();
            support.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0.2D, 0.0D, 0.0D));
            check(support.getX() > supportStartX + 0.15D, "horizontal carrier was clipped by the supported anvil");
            check(anvil.getX() > anvilStartX + 0.15D, "horizontal carrier movement did not transfer to the anvil");
            helper.succeed();
        });
    }

    private static PlasticAnvilEntity createAnvil(ExtendedGameTestHelper helper, Vec3 relativeBottomCenter) {
        return createAnvil(helper, relativeBottomCenter, PlasticAnvilOrientation.DEFAULT);
    }

    private static PlasticAnvilEntity createAnvil(
        ExtendedGameTestHelper helper,
        Vec3 relativeBottomCenter,
        PlasticAnvilOrientation orientation
    ) {
        return createAnvilAbsolute(helper, helper.absoluteVec(relativeBottomCenter), orientation);
    }

    private static PlasticAnvilEntity createAnvilAbsolute(ExtendedGameTestHelper helper, Vec3 position) {
        return createAnvilAbsolute(helper, position, PlasticAnvilOrientation.DEFAULT);
    }

    private static PlasticAnvilEntity createAnvilAbsolute(
        ExtendedGameTestHelper helper,
        Vec3 position,
        PlasticAnvilOrientation orientation
    ) {
        PlasticAnvilEntity anvil = new PlasticAnvilEntity(
            PlasticEntities.PLASTIC_ANVIL.get(),
            helper.getLevel(),
            position,
            PlasticBlocks.PLASTIC_ANVIL.get().defaultBlockState(),
            new ItemStack(PlasticBlocks.PLASTIC_ANVIL.get()),
            orientation
        );
        check(helper.getLevel().addFreshEntity(anvil), "failed to add plastic anvil to the test level");
        return anvil;
    }

    private static PlasticPotEntity createPot(
        ExtendedGameTestHelper helper,
        Vec3 relativeBottomCenter,
        PlasticAnvilOrientation orientation
    ) {
        PlasticPotEntity pot = new PlasticPotEntity(
            PlasticEntities.PLASTIC_POT.get(),
            helper.getLevel(),
            helper.absoluteVec(relativeBottomCenter),
            PlasticBlocks.PLASTIC_POT.get().defaultBlockState(),
            PlasticBlocks.PLASTIC_POT.asStack(),
            orientation
        );
        check(helper.getLevel().addFreshEntity(pot), "failed to add plastic pot to the test level");
        return pot;
    }

    private static void fillWater(
        ExtendedGameTestHelper helper,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ
    ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    helper.setBlock(x, y, z, Blocks.WATER);
                }
            }
        }
    }

    /** Supplies the public AccelerateManager entry point without constructing a powered multiblock in this test. */
    private static final class AccelerationTestAnvilEntity extends PlasticAnvilEntity {
        private AccelerateManager.AccelerationEntry acceleration;

        private AccelerationTestAnvilEntity(
            EntityType<? extends PlasticAnvilEntity> entityType,
            Level level,
            Vec3 position,
            AccelerateManager.AccelerationEntry acceleration
        ) {
            super(
                entityType,
                level,
                position,
                PlasticBlocks.PLASTIC_ANVIL.get().defaultBlockState(),
                new ItemStack(PlasticBlocks.PLASTIC_ANVIL.get()),
                PlasticAnvilOrientation.DEFAULT
            );
            this.acceleration = acceleration;
        }

        @Override
        protected void applyAnvilCraftAcceleration() {
            if (this.acceleration == null) {
                this.setDeltaMovement(Vec3.ZERO);
                return;
            }
            AccelerateManager.AccelerationEntry current = this.acceleration;
            this.acceleration = null;
            AccelerateManager.applyAcceleration(this, current);
        }
    }

    private static void assertBlockConvertsUnderGravity(ExtendedGameTestHelper helper, Direction direction) {
        BlockPos pos = new BlockPos(3, 3, 3);
        BlockPos absolutePos = helper.absolutePos(pos);
        Vec3 blockCenter = absolutePos.getCenter();
        Vec3 sourceCenter = blockCenter.add(Vec3.atLowerCornerOf(direction.getNormal()).scale(3.0D));
        BlockPos sourceId = BlockPos.containing(sourceCenter);
        GravityManager.GravitySourceType sourceType = new GravityManager.GravitySourceType(20.0D, 8, 0.0D);
        GravityManager.GravitySourceManager.upsertSource(helper.getLevel(), sourceId, sourceCenter, sourceType);
        helper.addEndListener(ignored -> GravityManager.GravitySourceManager.removeSource(helper.getLevel(), sourceId));

        Vec3 gravity = GravityManager.getNetGravityVectorForFallingBlock(
            helper.getLevel(),
            blockCenter,
            GravityType.NORMAL
        );
        check(Direction.getNearest(gravity) == direction, "test source did not create " + direction + " gravity: " + gravity);

        BlockState state = PlasticAnvilBlock.withColor(
            PlasticBlocks.PLASTIC_ANVIL.get().defaultBlockState().setValue(PlasticAnvilBlock.FACING, Direction.EAST),
            net.minecraft.world.item.DyeColor.CYAN
        );
        helper.setBlock(pos, state);
        helper.runAfterDelay(5, () -> {
            check(!helper.getBlockState(pos).is(PlasticBlocks.PLASTIC_ANVIL.get()), "compatibility block did not convert");
            List<FallingBlockEntity> fallingBlocks = helper.getLevel().getEntitiesOfClass(
                FallingBlockEntity.class,
                new AABB(blockCenter, blockCenter).inflate(3.0D)
            );
            check(fallingBlocks.size() == 1, "expected one converted falling-block entity, got " + fallingBlocks.size());
            check(
                fallingBlocks.getFirst() instanceof PlasticAnvilEntity,
                direction + " gravity created vanilla " + fallingBlocks.getFirst().getClass().getName()
            );
            PlasticAnvilEntity anvil = (PlasticAnvilEntity) fallingBlocks.getFirst();
            check(anvil.getDisplayState().equals(state), direction + " conversion lost the placed block state");
            check(
                PlasticAnvilItem.getColor(anvil.getDropStack()) == net.minecraft.world.item.DyeColor.CYAN,
                direction + " conversion lost the drop-stack color"
            );
            helper.succeed();
        });
    }

    private static boolean close(Vec3 first, Vec3 second) {
        return first.distanceToSqr(second) <= EPSILON * EPSILON;
    }

    private static int countItem(IItemHandler handler, Item item) {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
