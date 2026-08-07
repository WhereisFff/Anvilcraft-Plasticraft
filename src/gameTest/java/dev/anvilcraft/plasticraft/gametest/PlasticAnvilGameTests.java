package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeContext;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.HardenedResinAnvilBlock;
import dev.anvilcraft.plasticraft.block.HardenedResinCauldronBlock;
import dev.anvilcraft.plasticraft.block.HighViscosityResinFluidBlock;
import dev.anvilcraft.plasticraft.block.piston.HighViscosityPistonBudget;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.CatalyticPressLidEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.ResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondState;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityCollisionShapes;
import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityPhysics;
import dev.anvilcraft.plasticraft.entity.physics.PlasticFluidPhysics;
import dev.anvilcraft.plasticraft.entity.physics.ResinShockDropBehavior;
import dev.anvilcraft.plasticraft.event.ResinAnvilHammerEvents;
import dev.anvilcraft.plasticraft.init.PlasticraftAttachments;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.inventory.HardenedResinAnvilMenu;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.HighViscosityResinBlockItem;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.ResinAnvilHammerItem;
import dev.anvilcraft.plasticraft.recipe.CauldronImpactRecipeProcessor;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.api.fluid.network.FluidContainerLookup;
import dev.dubhe.anvilcraft.api.giantanvil.ShockDropBehavior;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.entity.FishTankBlockEntity;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.block.fluid.PipeBlock;
import dev.dubhe.anvilcraft.block.item.HasMobBlockItem;
import dev.dubhe.anvilcraft.block.sliding.ActivatorSlidingRailBlock;
import dev.dubhe.anvilcraft.block.sliding.DetectorSlidingRailBlock;
import dev.dubhe.anvilcraft.block.sliding.PoweredSlidingRailBlock;
import dev.dubhe.anvilcraft.block.sliding.SlidingRailBlock;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.event.giantanvil.shock.GiantAnvilShockEventListener;
import dev.dubhe.anvilcraft.event.giantanvil.shock.ShockContext;
import dev.dubhe.anvilcraft.init.block.ModBlockEntities;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.init.recipe.ModRecipeTypes;
import dev.dubhe.anvilcraft.item.AnvilHammerItem;
import dev.dubhe.anvilcraft.recipe.anvil.outcome.RoyalPreferenceOutcome;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.FastCookingRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.SuperHeatingRecipe;
import dev.dubhe.anvilcraft.util.AccelerateManager;
import dev.dubhe.anvilcraft.util.GravityManager;
import dev.dubhe.anvilcraft.util.GravityType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** 对持久化落方块物理约定的运行时测试。 */
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
                PlasticEntityOrientation orientation = new PlasticEntityOrientation(face, turn);
                check(packed.add(Byte.toUnsignedInt(orientation.pack())), "orientation packing collided");
                check(PlasticEntityOrientation.unpack(orientation.pack()).equals(orientation), "orientation did not round-trip");
                check(orientation.longAxis().getAxis() != face.getAxis(), "long axis is not in the attachment plane");
                check(orientation.orthogonalAxis().getAxis() != face.getAxis(), "cross axis is not in the attachment plane");
                check(orientation.longAxis().getAxis() != orientation.orthogonalAxis().getAxis(), "model basis is not orthogonal");

                Vec3 offset = orientation.collisionCenter(cell).subtract(cellCenter);
                Vec3 expected = Vec3.atLowerCornerOf(face.getNormal())
                    .scale(-PlasticEntityOrientation.ATTACHMENT_INSET);
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
            PlasticEntityOrientation floor = PlasticEntityOrientation.forPlacement(Direction.UP, playerDirection);
            check(
                floor.longAxis().getAxis() != playerDirection.getAxis(),
                playerDirection + " placement showed the short end to the player"
            );

            PlasticEntityOrientation ceiling = PlasticEntityOrientation.forPlacement(Direction.DOWN, playerDirection);
            check(
                ceiling.longAxis().getAxis() != playerDirection.getAxis(),
                playerDirection + " ceiling placement showed the short end to the player"
            );
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "The actual item path exposes all four in-plane directions on every clicked face")
    static void itemPlacesOnAllSixFaces(ExtendedGameTestHelper helper) {
        BlockPos clicked = new BlockPos(3, 3, 3);
        helper.setBlock(clicked, Blocks.STONE);
        BlockPos absoluteClicked = helper.absolutePos(clicked);
        for (Direction face : Direction.values()) {
            Vec3 faceCenter = absoluteClicked.getCenter()
                .add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5D));
            for (Direction longAxis : Direction.values()) {
                if (longAxis.getAxis() == face.getAxis()) continue;
                ItemStack stack = PlasticraftBlocks.HARDEND_RESIN_ANVIL.asStack();
                Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                BlockHitResult hit = new BlockHitResult(
                    faceCenter.add(Vec3.atLowerCornerOf(longAxis.getNormal()).scale(0.3D)),
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
                check(result.consumesAction(), "item placement failed on " + face + " toward " + longAxis);

                PlasticEntityOrientation expected = PlasticEntityOrientation.fromLongAxis(face, longAxis);
                Vec3 expectedCenter = expected.collisionCenter(absoluteClicked.relative(face));
                HardenedResinAnvilEntity placed = helper.getLevel()
                    .getEntitiesOfClass(
                        HardenedResinAnvilEntity.class,
                        new AABB(expectedCenter, expectedCenter).inflate(0.6D)
                    )
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new GameTestAssertException(
                        "item did not create an entity on " + face + " toward " + longAxis
                    ));
                check(placed.getOrientation().equals(expected), "item selected the wrong in-plane direction");
                check(
                    close(placed.getBoundingBox().getCenter(), expectedCenter),
                    "collision box was offset on " + face + " toward " + longAxis
                );
                check(Math.abs(placed.getBbWidth() - 1.0F) <= EPSILON, "entity width changed on " + face);
                check(Math.abs(placed.getBbHeight() - 1.0F) <= EPSILON, "entity height changed on " + face);
                placed.discard();
                player.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate("5x8x5")
    @TestHolder(description = "A plastic anvil uses normal falling-block gravity in air and remains a persistent entity")
    static void fallsInAir(ExtendedGameTestHelper helper) {
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 6.0D, 2.5D));
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
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 1.2D, 2.5D));
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
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(3.5D, 1.2D, 3.5D));
        BlockPos topWater = helper.absolutePos(new BlockPos(3, 4, 3));
        double surfaceY = topWater.getY() + helper.getLevel().getFluidState(topWater)
            .getHeight(helper.getLevel(), topWater);
        helper.runAfterDelay(60, () -> {
            PlasticFluidPhysics.FluidContact contact = PlasticFluidPhysics.sample(anvil);
            check(contact.isPresent(), "plastic anvil left the water surface");
            check(
                Math.abs(contact.submergedFraction() - 0.5D) < 0.12D,
                "plastic anvil did not settle at half-volume immersion: " + contact.submergedFraction()
            );
            check(Math.abs(contact.surfaceY() - surfaceY) < EPSILON, "plastic anvil sampled the wrong water surface");
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
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(3.5D, 1.2D, 3.5D));
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
            PlasticraftBlocks.HARDEND_RESIN_ANVIL.asStack()
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
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 4.3D, 2.5D));
        helper.runAfterDelay(30, () -> {
            double gap = anvil.getBoundingBox().minY - support.getBoundingBox().maxY;
            check(anvil.isAlive(), "supported plastic anvil stopped being an entity");
            check(Math.abs(gap) <= PlasticEntityPhysics.SUPPORT_PROBE_DEPTH + 0.02D, "anvil did not settle on the entity head");
            check(helper.getLevel().getBlockState(anvil.blockPosition()).isAir(), "supported anvil converted into a block");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 50)
    @EmptyTemplate(value = "5x7x5", floor = true)
    @TestHolder(description = "A server-ticked player can stand on the plastic anvil without pushing it aside")
    static void playerStandsWithoutPushingAnvil(ExtendedGameTestHelper helper) {
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 1.0D, 2.5D));
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
    @TestHolder(description = "Runtime magnetic-model changes are carried by synchronized entity data")
    static void displayStateSynchronizesAtRuntime(ExtendedGameTestHelper helper) {
        HardenedResinAnvilEntity server = createAnvil(helper, new Vec3(1.5D, 1.0D, 1.5D));
        HardenedResinAnvilEntity client = new HardenedResinAnvilEntity(PlasticraftEntities.HARDEND_RESIN_ANVIL.get(), helper.getLevel());
        server.getEntityData().packDirty();

        BlockState magnetic = server.getDisplayState().setValue(
            AbstractPlasticEntityBlock.MAGNETIZED,
            true
        );
        server.setDisplayState(magnetic);
        var update = server.getEntityData().packDirty();
        check(update != null && !update.isEmpty(), "display-state change did not mark synchronized data dirty");
        client.getEntityData().assignValues(update);

        check(client.getDisplayState().equals(magnetic), "client display state did not receive the runtime change");
        check(client.blockState.equals(magnetic), "inherited falling-block state diverged after synchronization");
        check(server.getBlockState().equals(magnetic), "AnvilCraft-facing block state diverged from the display state");
        check(client.isMagnetized(), "magnetic entity state did not synchronize with the model state");
        server.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "High-viscosity resin freezes non-players but slows players like cobwebs")
    static void highViscosityResinEntityMovement(ExtendedGameTestHelper helper) {
        BlockPos zombieFluidPos = new BlockPos(2, 1, 2);
        BlockPos playerFluidPos = new BlockPos(4, 1, 2);
        helper.setBlock(zombieFluidPos, PlasticraftBlocks.LIQUID_HIGH_VISCOSITY_RESIN.get());
        helper.setBlock(playerFluidPos, PlasticraftBlocks.LIQUID_HIGH_VISCOSITY_RESIN.get());
        BlockState fluidState = PlasticraftBlocks.LIQUID_HIGH_VISCOSITY_RESIN.get().defaultBlockState();
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 1.0D, 2.5D));
        zombie.setDeltaMovement(0.8D, 0.4D, -0.6D);
        HighViscosityResinFluidBlock.stickEntity(fluidState, zombie);
        Vec3 zombieStart = zombie.position();
        zombie.move(MoverType.SELF, new Vec3(0.8D, 0.4D, -0.6D));
        check(zombie.position().equals(zombieStart), "non-player moved while touching liquid resin");
        check(zombie.getDeltaMovement().equals(Vec3.ZERO), "non-player retained movement in liquid resin");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(4.5D, 1.0D, 2.5D)));
        HighViscosityResinFluidBlock.stickEntity(fluidState, player);
        Vec3 start = player.position();
        player.move(MoverType.SELF, new Vec3(1.0D, 1.0D, 1.0D));
        Vec3 movement = player.position().subtract(start);
        check(movement.x <= 0.250001D && movement.z <= 0.250001D, "player horizontal speed exceeded cobweb speed");
        check(movement.y <= 0.050001D, "player vertical speed exceeded cobweb speed");
        check(movement.lengthSqr() > 0.0D, "player was completely immobilized");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("9x6x9")
    @TestHolder(description = "High-viscosity resin sticks entities inside fish tanks and large cauldrons")
    static void highViscosityResinContainerEntityMovement(ExtendedGameTestHelper helper) {
        FluidStack resin = new FluidStack(
            PlasticraftFluids.LIQUID_HIGH_VISCOSITY_RESIN.get(),
            1000
        );
        BlockPos fishTankPos = new BlockPos(2, 1, 2);
        helper.setBlock(
            fishTankPos,
            ModBlocks.FISH_TANK.get().defaultBlockState()
        );
        BlockPos absoluteFishTankPos = helper.absolutePos(fishTankPos);
        check(
            helper.getLevel().getBlockEntity(absoluteFishTankPos) instanceof FishTankBlockEntity,
            "fish tank block entity was not created for resin interaction test"
        );
        FishTankBlockEntity fishTank = (FishTankBlockEntity) helper.getLevel()
            .getBlockEntity(absoluteFishTankPos);
        fishTank.getFluidHandler().fill(resin, IFluidHandler.FluidAction.EXECUTE);
        BlockState fishTankState = helper.getLevel().getBlockState(absoluteFishTankPos);

        Zombie fishTankZombie = helper.spawnWithNoFreeWill(
            EntityType.ZOMBIE,
            new Vec3(2.5D, 1.1D, 2.5D)
        );
        fishTankZombie.setNoGravity(true);
        check(
            HighViscosityResinFluidBlock.isEntityInsideContainer(
                fishTankState,
                helper.getLevel(),
                absoluteFishTankPos,
                fishTankZombie
            ),
            "fish tank resin did not detect an entity in its fluid area"
        );
        Vec3 fishTankStart = fishTankZombie.position();
        fishTankZombie.move(MoverType.SELF, new Vec3(0.8D, 0.4D, -0.6D));
        check(
            fishTankZombie.position().equals(fishTankStart),
            "non-player moved inside fish tank resin"
        );

        BlockPos largeCauldronPos = new BlockPos(6, 2, 6);
        BlockState largeCauldronState = ModBlocks.LARGE_CAULDRON.get()
            .defaultBlockState()
            .setValue(LargeCauldronBlock.HALF, Cube3x3PartHalf.MID_CENTER);
        helper.setBlock(largeCauldronPos, largeCauldronState);
        BlockPos absoluteLargeCauldronPos = helper.absolutePos(largeCauldronPos);
        check(
            helper.getLevel().getBlockEntity(absoluteLargeCauldronPos) instanceof LargeCauldronBlockEntity,
            "large cauldron block entity was not created for resin interaction test"
        );
        LargeCauldronBlockEntity largeCauldron = (LargeCauldronBlockEntity) helper.getLevel()
            .getBlockEntity(absoluteLargeCauldronPos);
        int tankCapacity = largeCauldron.getFluidHandler().getTankCapacity(0);
        largeCauldron.getFluidHandler().fill(
            resin.copyWithAmount(tankCapacity),
            IFluidHandler.FluidAction.EXECUTE
        );
        BlockState actualLargeCauldronState = helper.getLevel().getBlockState(absoluteLargeCauldronPos);
        Zombie largeCauldronZombie = helper.spawnWithNoFreeWill(
            EntityType.ZOMBIE,
            new Vec3(6.5D, 1.55D, 6.5D)
        );
        largeCauldronZombie.setNoGravity(true);
        check(
            HighViscosityResinFluidBlock.isEntityInsideContainer(
                actualLargeCauldronState,
                helper.getLevel(),
                absoluteLargeCauldronPos,
                largeCauldronZombie
            ),
            "large cauldron resin did not detect an entity in its fluid area"
        );
        Vec3 largeCauldronStart = largeCauldronZombie.position();
        largeCauldronZombie.move(MoverType.SELF, new Vec3(0.8D, 0.4D, -0.6D));
        check(
            largeCauldronZombie.position().equals(largeCauldronStart),
            "non-player moved inside large cauldron resin"
        );

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(helper.absoluteVec(new Vec3(2.5D, 1.1D, 2.5D)));
        fishTankState.entityInside(helper.getLevel(), absoluteFishTankPos, player);
        Vec3 playerStart = player.position();
        player.move(MoverType.SELF, new Vec3(1.0D, 1.0D, 1.0D));
        Vec3 playerMovement = player.position().subtract(playerStart);
        check(
            Math.abs(playerMovement.x) <= 0.250001D
                && Math.abs(playerMovement.z) <= 0.250001D
                && Math.abs(playerMovement.y) <= 0.050001D
                && playerMovement.lengthSqr() > 0.0D,
            "player was not slowed by fish tank resin"
        );

        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(helper.absoluteVec(new Vec3(6.5D, 1.55D, 6.5D)));
        actualLargeCauldronState.entityInside(helper.getLevel(), absoluteLargeCauldronPos, player);
        playerStart = player.position();
        player.move(MoverType.SELF, new Vec3(1.0D, 1.0D, 1.0D));
        playerMovement = player.position().subtract(playerStart);
        check(
            Math.abs(playerMovement.x) <= 0.250001D
                && Math.abs(playerMovement.z) <= 0.250001D
                && Math.abs(playerMovement.y) <= 0.050001D
                && playerMovement.lengthSqr() > 0.0D,
            "player was not slowed by large cauldron resin"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 120)
    @EmptyTemplate(value = "9x4x9", floor = true)
    @TestHolder(description = "Liquid high-viscosity resin advances every 40 ticks and stops after two blocks")
    static void highViscosityResinFlowRange(ExtendedGameTestHelper helper) {
        BlockPos source = new BlockPos(4, 0, 4);
        helper.setBlock(source, PlasticraftBlocks.LIQUID_HIGH_VISCOSITY_RESIN.get());
        helper.getLevel().scheduleTick(
            helper.absolutePos(source),
            PlasticraftFluids.LIQUID_HIGH_VISCOSITY_RESIN.get(),
            1
        );
        check(
            PlasticraftFluids.LIQUID_HIGH_VISCOSITY_RESIN.get()
                .getTickDelay(helper.getLevel()) == 40,
            "liquid resin did not use a 40 tick flow delay"
        );
        helper.runAfterDelay(90, () -> {
            BlockPos one = source.east();
            BlockPos two = source.east(2);
            BlockPos three = source.east(3);
            check(!helper.getBlockState(one).getFluidState().isEmpty(), "liquid resin did not reach the first block");
            check(!helper.getBlockState(two).getFluidState().isEmpty(), "liquid resin did not reach the second block");
            check(helper.getBlockState(three).getFluidState().isEmpty(), "liquid resin flowed farther than two blocks");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate
    @TestHolder(description = "Large hostile mobs ignore resin size limits but still require Weakness")
    static void highViscosityResinCapturesLargeMobs(ExtendedGameTestHelper helper) {
        Ravager ravager = helper.spawnWithNoFreeWill(
            EntityType.RAVAGER,
            new Vec3(1.5D, 1.0D, 1.5D)
        );
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack resin = PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asStack();
        check(
            !HasMobBlockItem.canMobBeSaved(ravager, null, resin),
            "base resin unexpectedly accepted the oversized ravager"
        );
        check(
            !HighViscosityResinBlockItem.canMobBeSaved(ravager, null, resin),
            "hostile ravager did not require Weakness"
        );
        ravager.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200));
        check(
            HighViscosityResinBlockItem.canMobBeSaved(ravager, null, resin),
            "high-viscosity resin still rejected an oversized weakened ravager"
        );
        check(
            HighViscosityResinBlockItem.useEntity(player, ravager, resin) == InteractionResult.SUCCESS,
            "high-viscosity resin did not capture the oversized weakened ravager"
        );
        check(!ravager.isAlive(), "captured ravager remained in the level");
        ItemStack captured = ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate.is(PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
                && candidate.has(ModComponents.SAVED_ENTITY)) {
                captured = candidate;
                break;
            }
        }
        check(!captured.isEmpty(), "captured ravager was not stored in the resin block item");
        check(
            captured.get(ModComponents.SAVED_ENTITY).isMonster(),
            "captured hostile mob lost its resentment-compatible marker"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x3x7")
    @TestHolder(description = "High-viscosity resin satisfies every base-resin shock pedestal check")
    static void highViscosityResinSupportsResinShockPedestal(ExtendedGameTestHelper helper) {
        BlockPos center = new BlockPos(3, 0, 3);
        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int zOffset = -1; zOffset <= 1; zOffset++) {
                if (xOffset == 0 && zOffset == 0) continue;
                helper.setBlock(center.offset(xOffset, 0, zOffset), PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.get());
            }
        }
        ShockContext context = new ShockContext(
            helper.getLevel(),
            helper.absolutePos(center),
            null,
            List.of(),
            0.0F
        );
        check(
            context.testCorner(ModBlocks.RESIN_BLOCK.get()),
            "high-viscosity resin failed the resin shock corner check"
        );
        check(
            context.testBorder(ModBlocks.RESIN_BLOCK.get()),
            "high-viscosity resin failed the resin shock border check"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x5x11")
    @TestHolder(description = "Plastic anvil entities participate in both shock pedestal modes")
    static void plasticAnvilsSupportShockContext(ExtendedGameTestHelper helper) {
        BlockPos center = new BlockPos(5, 2, 5);
        for (int x = 3; x <= 7; x++) {
            for (int z = 3; z <= 7; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }

        for (Direction direction : ShockContext.HORIZONTAL) {
            createResinAnvilInCell(helper, center.relative(direction), PlasticraftBlocks.RESIN_ANVIL.asStack());
        }
        for (Direction direction1 : ShockContext.HORIZONTAL_X) {
            for (Direction direction2 : ShockContext.HORIZONTAL_Z) {
                createResinAnvilInCell(
                    helper,
                    center.relative(direction1).relative(direction2),
                    PlasticraftBlocks.RESIN_ANVIL.asStack()
                );
            }
        }

        ShockContext context = new ShockContext(
            helper.getLevel(),
            helper.absolutePos(center),
            null,
            List.of(helper.absolutePos(center.above())),
            1.0F
        );
        check(
            context.testCorner(ModBlocks.RESIN_BLOCK.get()),
            "resin anvil entities did not match the resin shock corners"
        );
        check(
            context.testBorder(ModBlocks.RESIN_BLOCK.get()),
            "resin anvil entities did not match the resin shock border"
        );
        ShockDropBehavior resinDrop = context.getBorderAnvilBehavior()
            .orElseThrow(() -> new GameTestAssertException("resin anvil entities did not form a shock border"))
            .dropBehavior();
        check(resinDrop == ResinShockDropBehavior.INSTANCE, "resin shock behavior lost its drop handler");

        for (var entity : helper.getLevel().getEntitiesOfClass(ResinAnvilEntity.class, new AABB(
            helper.absolutePos(center).getX() - 2,
            helper.absolutePos(center).getY() - 1,
            helper.absolutePos(center).getZ() - 2,
            helper.absolutePos(center).getX() + 3,
            helper.absolutePos(center).getY() + 2,
            helper.absolutePos(center).getZ() + 3
        ))) {
            entity.discard();
        }
        for (Direction direction : ShockContext.HORIZONTAL) {
            createHardenedResinAnvilInCell(helper, center.relative(direction));
        }
        ShockContext hardenedContext = new ShockContext(
            helper.getLevel(),
            helper.absolutePos(center),
            null,
            List.of(helper.absolutePos(center.above())),
            1.0F
        );
        check(
            hardenedContext.getBorderAnvilBehavior().isPresent(),
            "hardened resin anvil entities did not form a shock border"
        );
        check(
            hardenedContext.getBorderAnvilBehavior().orElseThrow().dropBehavior() == ShockDropBehavior.DEFAULT,
            "hardened resin anvils did not use the normal shock drop behavior"
        );
        check(
            ShockContext.bounceVelocityForHeight(2.0D) > ShockContext.bounceVelocityForHeight(1.0D),
            "resin shock height multiplier did not increase the launch velocity"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x6x11")
    @TestHolder(description = "Bonded resin-anvil blocks form a resin shock base without being bounced loose")
    static void bondedResinAnvilBlocksStayFixedDuringResinShock(ExtendedGameTestHelper helper) {
        BlockPos center = new BlockPos(5, 1, 5);
        helper.setBlock(center, ModBlocks.HEAVY_IRON_BLOCK.get());
        BlockState bondedState = PlasticraftBlocks.RESIN_ANVIL.get()
            .defaultBlockState()
            .setValue(AbstractPlasticEntityBlock.BONDED, true);
        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int zOffset = -1; zOffset <= 1; zOffset++) {
                if (xOffset == 0 && zOffset == 0) continue;
                helper.setBlock(center.offset(xOffset, 0, zOffset), bondedState);
            }
        }
        ShockContext context = new ShockContext(
            helper.getLevel(),
            helper.absolutePos(center),
            null,
            List.of(),
            0.0F
        );
        check(
            context.testCorner(ModBlocks.RESIN_BLOCK.get())
                && context.testBorder(ModBlocks.RESIN_BLOCK.get()),
            "bonded resin anvils did not qualify as a resin shock pedestal"
        );

        BlockPos bondedInRange = center.above().east(2);
        BlockPos freeInRange = center.above().west(2);
        helper.setBlock(bondedInRange.below(), Blocks.STONE);
        helper.setBlock(freeInRange.below(), Blocks.STONE);
        helper.setBlock(bondedInRange, bondedState);
        helper.setBlock(freeInRange, PlasticraftBlocks.RESIN_ANVIL.get().defaultBlockState());
        GiantAnvilShockEventListener.onLand(new AnvilEvent.GiantOnLand(
            helper.getLevel(),
            helper.absolutePos(center.above(2)),
            null,
            2.0F
        ));

        check(
            helper.getBlockState(bondedInRange).is(PlasticraftBlocks.RESIN_ANVIL.get())
                && helper.getBlockState(bondedInRange)
                    .getValue(AbstractPlasticEntityBlock.BONDED),
            "resin shock bounced a bonded resin anvil loose"
        );
        check(helper.getBlockState(freeInRange).isAir(), "resin shock did not bounce a free resin anvil");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("9x4x9")
    @TestHolder(description = "Resin shock drops use a 45-degree trajectory to clear the square range")
    static void resinShockDropTrajectory(ExtendedGameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(4, 1, 4));
        List<BlockPos> range = new ArrayList<>();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                range.add(center.above().offset(dx, 0, dz));
            }
        }
        ShockContext context = new ShockContext(helper.getLevel(), center, null, range, 8.0F);
        BlockPos near = center.above().offset(1, 0, 0);
        BlockPos edge = center.above().offset(8, 0, 0);
        Vec3 nearVelocity = ResinShockDropBehavior.calculateLaunchVelocity(context, near);
        Vec3 edgeVelocity = ResinShockDropBehavior.calculateLaunchVelocity(context, edge);
        check(
            close(Math.sqrt(nearVelocity.x * nearVelocity.x + nearVelocity.z * nearVelocity.z), nearVelocity.y),
            "resin shock drop was not launched at 45 degrees"
        );
        check(nearVelocity.y > edgeVelocity.y, "drops near the center did not receive a longer launch");
        for (BlockPos source : List.of(near, edge, center.above().offset(4, 0, 4))) {
            Vec3 velocity = ResinShockDropBehavior.calculateLaunchVelocity(context, source);
            Vec3 horizontal = new Vec3(velocity.x, 0.0D, velocity.z).normalize();
            double distance = ResinShockDropBehavior.simulateHorizontalRange(velocity.y);
            Vec3 endpoint = Vec3.atCenterOf(source).add(horizontal.scale(distance));
            double squareRadius = Math.max(
                Math.abs(endpoint.x - Vec3.atCenterOf(center).x),
                Math.abs(endpoint.z - Vec3.atCenterOf(center).z)
            );
            check(close(squareRadius, context.getShockRadius() + 0.75D), "drop trajectory missed the shock boundary");
        }

        ItemStack sourceStack = new ItemStack(Items.COBBLESTONE);
        Vec3 expectedVelocity = ResinShockDropBehavior.calculateLaunchVelocity(context, near);
        ResinShockDropBehavior.INSTANCE.drop(context, near, sourceStack);
        ItemEntity spawnedDrop = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(near).inflate(1.0D))
            .stream()
            .filter(item -> item.getItem().is(Items.COBBLESTONE))
            .findFirst()
            .orElseThrow(() -> new GameTestAssertException("resin shock behavior spawned no item entity"));
        check(close(spawnedDrop.getDeltaMovement(), expectedVelocity), "spawned drop used the wrong launch velocity");
        check(sourceStack.getCount() == 1, "resin shock behavior consumed its input stack");
        spawnedDrop.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("20x8x8")
    @TestHolder(description = "A moved normal block discovers adjacent high-viscosity resin and its grouped load")
    static void highViscosityResinReversePistonAdhesion(ExtendedGameTestHelper helper) {
        BlockPos piston = new BlockPos(1, 2, 3);
        for (int x = 2; x <= 13; x++) helper.setBlock(x, 2, 3, Blocks.STONE);
        BlockPos resin = new BlockPos(2, 3, 3);
        helper.setBlock(resin, PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.get());
        helper.setBlock(resin.above(), Blocks.GOLD_BLOCK);
        helper.setBlock(resin.north(), Blocks.IRON_BLOCK);
        helper.setBlock(resin.south(), Blocks.COPPER_BLOCK);

        PistonStructureResolver resolver = new PistonStructureResolver(
            helper.getLevel(),
            helper.absolutePos(piston),
            Direction.EAST,
            true
        );
        check(resolver.resolve(), "grouped physical load exceeded the ungrouped piston limit");
        List<BlockPos> pushed = resolver.getToPush();
        check(pushed.contains(helper.absolutePos(resin)), "normal moved block did not discover adjacent resin");
        check(pushed.contains(helper.absolutePos(resin.above())), "resin did not collect its upper neighbor");
        check(pushed.size() > HighViscosityPistonBudget.VANILLA_PUSH_BUDGET, "test did not exceed the physical limit");
        check(
            HighViscosityPistonBudget.effectivePushCount(helper.getLevel(), pushed)
                <= HighViscosityPistonBudget.VANILLA_PUSH_BUDGET,
            "resin-connected group did not collapse to one push budget"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate
    @TestHolder(description = "Pure renaming is free and keeps the prior-work penalty unchanged")
    static void hardenedResinPureRenameIsFree(ExtendedGameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.experienceLevel = 12;
        HardenedResinAnvilMenu menu = new HardenedResinAnvilMenu(1, player.getInventory(), -1);
        ItemStack input = new ItemStack(Items.DIAMOND_SWORD);
        input.set(DataComponents.REPAIR_COST, 7);
        menu.getSlot(0).set(input);
        check(menu.setItemName("Free Rename"), "rename request was rejected");

        ItemStack output = menu.getSlot(2).getItem();
        check(!output.isEmpty(), "pure rename produced no output");
        check(output.getHoverName().getString().equals("Free Rename"), "output did not receive the requested name");
        check(menu.getCost() == 0, "pure rename retained an experience cost of " + menu.getCost());
        check(output.getOrDefault(DataComponents.REPAIR_COST, 0) == 7, "pure rename changed the prior-work penalty");
        check(menu.getSlot(2).mayPickup(player), "zero-cost rename output could not be taken");
        int experienceBefore = player.experienceLevel;
        menu.getSlot(2).onTake(player, output.copy());
        check(player.experienceLevel == experienceBefore, "taking a pure rename output consumed experience");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate
    @TestHolder(description = "The resin anvil hammer inherits hammer behavior, repairs with resin, and has no portable menu")
    static void resinAnvilHammerItemContract(ExtendedGameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hammer = PlasticraftItems.RESIN_ANVIL_HAMMER.asStack();
        ResinAnvilHammerItem item = (ResinAnvilHammerItem) hammer.getItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, hammer);

        check(item instanceof AnvilHammerItem, "resin hammer did not inherit AnvilHammerItem");
        check(hammer.getMaxDamage() == 35, "resin hammer durability differs from the standard hammer");
        var modifiers = hammer.getAttributeModifiers().modifiers();
        var attackDamageModifier = modifiers.stream()
            .filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE))
            .findFirst()
            .orElseThrow(() -> new GameTestAssertException("resin hammer has no attack damage modifier"));
        var attackSpeedModifier = modifiers.stream()
            .filter(entry -> entry.attribute().equals(Attributes.ATTACK_SPEED))
            .findFirst()
            .orElseThrow(() -> new GameTestAssertException("resin hammer has no attack speed modifier"));
        check(
            attackDamageModifier.modifier().is(Item.BASE_ATTACK_DAMAGE_ID)
                && attackDamageModifier.modifier().operation() == AttributeModifier.Operation.ADD_VALUE
                && attackDamageModifier.slot() == EquipmentSlotGroup.MAINHAND
                && Math.abs(
                    player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)
                        + attackDamageModifier.modifier().amount()
                ) <= EPSILON,
            "resin hammer tooltip attack damage is not 0"
        );
        check(
            attackSpeedModifier.modifier().is(Item.BASE_ATTACK_SPEED_ID)
                && attackSpeedModifier.modifier().operation() == AttributeModifier.Operation.ADD_VALUE
                && attackSpeedModifier.slot() == EquipmentSlotGroup.MAINHAND
                && Math.abs(
                    player.getAttributeBaseValue(Attributes.ATTACK_SPEED)
                        + attackSpeedModifier.modifier().amount()
                        - 4.0D
                ) <= EPSILON,
            "resin hammer tooltip attack speed is not 4"
        );
        check(
            item.isValidRepairItem(hammer, ModItems.RESIN.asStack()),
            "resin was not accepted as the resin hammer's repair material"
        );
        check(
            !item.isValidRepairItem(hammer, new ItemStack(Items.IRON_INGOT)),
            "the resin hammer retained the standard hammer's iron repair material"
        );
        check(item.getAnvil() == PlasticraftBlocks.RESIN_ANVIL.get(), "resin hammer impacts did not use the resin anvil");
        check(
            item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.PASS,
            "using the resin hammer in air started the portable anvil action"
        );
        check(!player.isUsingItem(), "resin hammer entered a held-use state for a portable menu");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate
    @TestHolder(description = "Resin repairs in a hardened resin anvil are free and preserve prior-work penalty")
    static void hardenedResinHammerRepairIsFree(ExtendedGameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.experienceLevel = 12;
        HardenedResinAnvilMenu menu = new HardenedResinAnvilMenu(1, player.getInventory(), -1);
        ItemStack input = PlasticraftItems.RESIN_ANVIL_HAMMER.asStack();
        input.setDamageValue(30);
        input.set(DataComponents.REPAIR_COST, 80);
        menu.getSlot(0).set(input);
        menu.getSlot(1).set(ModItems.RESIN.asStack());
        menu.createResult();

        ItemStack output = menu.getSlot(2).getItem();
        check(!output.isEmpty(), "resin repair produced no output");
        check(output.getDamageValue() < input.getDamageValue(), "resin repair restored no durability");
        check(menu.getCost() == 0, "resin repair retained an experience cost of " + menu.getCost());
        check(
            output.getOrDefault(DataComponents.REPAIR_COST, 0) == 80,
            "resin repair changed the existing prior-work penalty"
        );
        check(menu.getSlot(2).mayPickup(player), "zero-cost resin repair output could not be taken");
        int experienceBefore = player.experienceLevel;
        menu.getSlot(2).onTake(player, output.copy());
        check(player.experienceLevel == experienceBefore, "taking a resin repair output consumed experience");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "10x6x12", floor = true)
    @TestHolder(description = "Resin hammer entity clicks trigger a resin-sounded anvil impact without damage")
    static void resinAnvilHammerKnockbackIsNonDamaging(ExtendedGameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 playerPosition = helper.absoluteVec(new Vec3(3.5D, 2.0D, 1.5D));
        player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setYRot(0.0F);
        ItemStack hammer = PlasticraftItems.RESIN_ANVIL_HAMMER.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, hammer);
        ResinAnvilEntity target = createResinAnvil(
            helper,
            new Vec3(3.5D, 2.0D, 3.5D),
            PlasticraftBlocks.RESIN_ANVIL.asStack()
        );
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);
        Zombie middle = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 2.0D, 3.5D));
        Zombie last = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(5.5D, 2.0D, 3.5D));
        middle.setNoGravity(true);
        last.setNoGravity(true);
        check(EntityBondManager.connect(
            helper.getLevel(), target, Direction.EAST, middle, Direction.WEST, true
        ), "hammer target bond failed");
        check(EntityBondManager.connect(
            helper.getLevel(), middle, Direction.EAST, last, Direction.WEST, true
        ), "remaining hammer-test bond failed");
        Vec3 targetStart = target.position();
        Vec3 middleOffset = middle.position().subtract(targetStart);
        Vec3 lastOffset = last.position().subtract(targetStart);
        AtomicInteger landingEventCount = new AtomicInteger();
        AtomicInteger resinSoundCount = new AtomicInteger();
        AtomicInteger anvilSoundCount = new AtomicInteger();
        ResourceLocation resinImpactSound = ModBlocks.RESIN_BLOCK
            .getDefaultState().getSoundType().getHitSound().getLocation();
        BlockPos impactPos = target.blockPosition();
        helper.addTemporaryListener((AnvilEvent.OnLand event) -> {
            if (event.getPos().equals(impactPos.above())
                && event.getEntity().getBlockState().is(PlasticraftBlocks.RESIN_ANVIL.get())) {
                landingEventCount.incrementAndGet();
            }
        });
        helper.addTemporaryListener((PlayLevelSoundEvent.AtPosition event) -> {
            if (event.getLevel() != helper.getLevel() || event.getSound() == null) return;
            ResourceLocation sound = event.getSound().value().getLocation();
            if (sound.equals(resinImpactSound)) resinSoundCount.incrementAndGet();
            if (sound.equals(SoundEvents.ANVIL_LAND.getLocation())) anvilSoundCount.incrementAndGet();
        });

        player.attack(target);

        check(target.isAlive(), "zero-damage resin hammer attack destroyed its target");
        check(target.getDeltaMovement().z > 2.4D, "entity did not receive Knockback V away from the player's view");
        check(hammer.getDamageValue() == 1, "entity knockback consumed the wrong amount of durability");
        check(landingEventCount.get() == 1, "entity click did not post exactly one anvil landing event");
        check(resinSoundCount.get() == 1, "entity click did not play exactly one resin impact sound");
        check(anvilSoundCount.get() == 0, "entity click also played the vanilla anvil landing sound");
        check(EntityBondManager.hasBonds(target), "resin hammer disconnected the struck entity");
        check(
            !target.hasData(PlasticraftAttachments.ADHESIVE_ELASTIC_MOTION),
            "resin hammer started elastic motion on an unanchored group"
        );
        EntityBondState targetBonds = EntityBondManager.get(target);
        check(
            targetBonds != null && targetBonds.leaderUuid().equals(target.getUUID()),
            "resin hammer did not make the struck entity the unanchored group leader"
        );
        check(
            EntityBondManager.hasBonds(middle) && EntityBondManager.hasBonds(last),
            "resin hammer disconnected entities that were not struck"
        );
        helper.runAfterDelay(2, () -> {
            check(target.getZ() > targetStart.z + 0.05D, "resin hammer did not move the unanchored group");
            check(
                close(middle.position(), target.position().add(middleOffset)),
                "resin hammer did not move the middle entity with the unanchored group"
            );
            check(
                close(last.position(), target.position().add(lastOffset)),
                "resin hammer did not move the last entity with the unanchored group"
            );
            target.discard();
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "10x7x32", floor = true)
    @TestHolder(description = "A resin hammer does not double a vertically bonded plastic pair's carried movement")
    static void resinAnvilHammerDoesNotAccelerateVerticalPlasticPair(ExtendedGameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.5D, 2.0D, 2.5D));
        player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setYRot(0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, PlasticraftItems.RESIN_ANVIL_HAMMER.asStack());

        UniversalPlasticEntity lower = new UniversalPlasticEntity(
            PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
            helper.getLevel(),
            helper.absoluteVec(new Vec3(4.5D, 2.0D, 4.5D)),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        UniversalPlasticEntity upper = new UniversalPlasticEntity(
            PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
            helper.getLevel(),
            helper.absoluteVec(new Vec3(4.5D, 3.0D, 4.5D)),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        check(helper.getLevel().addFreshEntity(lower), "failed to add lower universal plastic");
        check(helper.getLevel().addFreshEntity(upper), "failed to add upper universal plastic");
        AABB lowerBounds = lower.plasticraft$getCollisionBox().bounds();
        AABB upperBounds = upper.plasticraft$getCollisionBox().bounds();
        upper.setPos(upper.position().add(0.0D, lowerBounds.maxY - upperBounds.minY, 0.0D));
        lower.setNoGravity(true);
        upper.setNoGravity(true);
        lower.setDeltaMovement(Vec3.ZERO);
        upper.setDeltaMovement(Vec3.ZERO);
        check(
            EntityBondManager.connect(
                helper.getLevel(), lower, Direction.UP, upper, Direction.DOWN, true
            ),
            "vertical universal plastic bond failed"
        );
        check(
            PlasticEntityPhysics.findSupport(upper, Direction.DOWN) == null,
            "a vertically bonded plastic member still treated its own group as support"
        );
        Vec3 lowerStart = lower.position();
        Vec3 upperOffset = upper.position().subtract(lowerStart);
        Vec3[] previousLowerPosition = {lowerStart};

        player.attack(lower);
        check(lower.getDeltaMovement().z > 2.4D, "resin hammer did not launch the vertical plastic pair");
        List<VoxelShape> knockbackCollisions = helper.getLevel().getEntityCollisions(
            lower,
            lower.getBoundingBox().expandTowards(lower.getDeltaMovement())
        );
        check(
            knockbackCollisions.isEmpty(),
            "a vertical bonded member still blocked its leader's knockback"
        );
        Vec3 clampedKnockback = EntityBondManager.clampLeaderMovement(lower, lower.getDeltaMovement());
        check(
            clampedKnockback.z > 2.4D,
            "component collision clipping removed the vertical pair's knockback: " + clampedKnockback
        );
        EntityBondState lowerBonds = EntityBondManager.get(lower);
        check(
            lowerBonds != null && lowerBonds.leaderUuid().equals(lower.getUUID()),
            "resin hammer did not make the struck vertical member the group leader"
        );

        helper.startSequence()
            .thenExecuteFor(5, () -> {
                Vec3 movement = lower.position().subtract(previousLowerPosition[0]);
                check(
                    movement.lengthSqr() < 9.0D,
                    "vertical plastic pair accelerated after a single hammer hit: " + movement
                );
                check(
                    close(upper.position(), lower.position().add(upperOffset)),
                    "vertical plastic follower separated during resin hammer knockback"
                );
                previousLowerPosition[0] = lower.position();
            })
            .thenExecute(() -> {
                double travelled = lower.getZ() - lowerStart.z;
                check(
                    travelled > 1.0D,
                    "resin hammer did not move the vertical plastic pair: travelled=" + travelled
                        + ", lower=" + lower.position()
                        + ", delta=" + lower.getDeltaMovement()
                );
                check(
                    travelled < 16.0D,
                    "vertical plastic pair counted its own carried movement twice: " + travelled
                );
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "10x8x64", floor = true)
    @TestHolder(description = "A resin hammer uses a grounded bonded member's friction when striking an upper plastic")
    static void resinAnvilHammerFollowerKnockbackUsesGroundFriction(ExtendedGameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.5D, 2.0D, 1.5D));
        player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setYRot(0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, PlasticraftItems.RESIN_ANVIL_HAMMER.asStack());

        UniversalPlasticEntity lower = new UniversalPlasticEntity(
            PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
            helper.getLevel(),
            helper.absoluteVec(new Vec3(4.5D, 2.0D, 4.5D)),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        UniversalPlasticEntity upper = new UniversalPlasticEntity(
            PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
            helper.getLevel(),
            helper.absoluteVec(new Vec3(4.5D, 3.0D, 4.5D)),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        check(helper.getLevel().addFreshEntity(lower), "failed to add grounded lower universal plastic");
        check(helper.getLevel().addFreshEntity(upper), "failed to add upper universal plastic");
        AABB lowerBounds = lower.plasticraft$getCollisionBox().bounds();
        AABB upperBounds = upper.plasticraft$getCollisionBox().bounds();
        upper.setPos(upper.position().add(0.0D, lowerBounds.maxY - upperBounds.minY, 0.0D));
        check(
            PlasticEntityPhysics.hasBlockSupport(lower, Direction.DOWN),
            "lower universal plastic was not placed on the test floor"
        );
        check(
            EntityBondManager.connect(
                helper.getLevel(), lower, Direction.UP, upper, Direction.DOWN, false
            ),
            "grounded vertical universal plastic bond failed"
        );
        Vec3 lowerOffset = lower.position().subtract(upper.position());

        player.attack(upper);
        check(upper.getDeltaMovement().z > 2.4D, "resin hammer did not launch the upper plastic");
        EntityBondState upperBonds = EntityBondManager.get(upper);
        check(
            upperBonds != null && upperBonds.leaderUuid().equals(upper.getUUID()),
            "resin hammer did not make the struck upper plastic the group leader"
        );

        helper.startSequence()
            .thenExecuteFor(10, () -> check(
                close(lower.position(), upper.position().add(lowerOffset)),
                "grounded follower separated during resin hammer knockback"
            ))
            .thenExecute(() -> check(
                upper.getDeltaMovement().z < 0.5D,
                "upper-led bonded group did not inherit the lower member's ground friction: "
                    + upper.getDeltaMovement()
            ))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate
    @TestHolder(description = "Resin hammer block clicks trigger a resin-sounded anvil impact and recoil")
    static void resinAnvilHammerBlockRecoil(ExtendedGameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        player.setItemInHand(
            InteractionHand.MAIN_HAND,
            PlasticraftItems.RESIN_ANVIL_HAMMER.asStack()
        );
        BlockPos target = helper.absolutePos(BlockPos.ZERO);
        AtomicInteger landingEventCount = new AtomicInteger();
        AtomicInteger resinSoundCount = new AtomicInteger();
        AtomicInteger anvilSoundCount = new AtomicInteger();
        ResourceLocation resinImpactSound = ModBlocks.RESIN_BLOCK
            .getDefaultState().getSoundType().getHitSound().getLocation();
        helper.addTemporaryListener((AnvilEvent.OnLand event) -> {
            if (event.getPos().equals(target.above())
                && event.getEntity().getBlockState().is(PlasticraftBlocks.RESIN_ANVIL.get())) {
                landingEventCount.incrementAndGet();
            }
        });
        helper.addTemporaryListener((PlayLevelSoundEvent.AtPosition event) -> {
            if (event.getLevel() != helper.getLevel() || event.getSound() == null) return;
            ResourceLocation sound = event.getSound().value().getLocation();
            if (sound.equals(resinImpactSound)) resinSoundCount.incrementAndGet();
            if (sound.equals(SoundEvents.ANVIL_LAND.getLocation())) anvilSoundCount.incrementAndGet();
        });

        ResinAnvilHammerEvents.leftClickBlock(new PlayerInteractEvent.LeftClickBlock(
            player,
            target,
            Direction.UP,
            PlayerInteractEvent.LeftClickBlock.Action.START
        ));
        check(player.getDeltaMovement().z < -2.4D, "block click did not launch the player directly backward");
        check(player.isIgnoringFallDamageFromCurrentImpulse(), "block recoil did not suppress its resulting fall damage");
        check(landingEventCount.get() == 1, "block click did not post exactly one anvil landing event");
        check(resinSoundCount.get() == 1, "block click did not play exactly one resin impact sound");
        check(anvilSoundCount.get() == 0, "block click also played the vanilla anvil landing sound");
        check(player.getMainHandItem().getDamageValue() == 1, "block click consumed the wrong amount of durability");

        player.setDeltaMovement(Vec3.ZERO);
        player.setXRot(90.0F);
        ResinAnvilHammerEvents.leftClickBlock(new PlayerInteractEvent.LeftClickBlock(
            player,
            target,
            Direction.UP,
            PlayerInteractEvent.LeftClickBlock.Action.START
        ));
        check(
            player.getDeltaMovement().y > 2.4D
                && player.getDeltaMovement().horizontalDistanceSqr() < EPSILON,
            "looking down did not launch the player upward"
        );

        player.setDeltaMovement(Vec3.ZERO);
        player.setXRot(-90.0F);
        ResinAnvilHammerEvents.leftClickBlock(new PlayerInteractEvent.LeftClickBlock(
            player,
            target,
            Direction.UP,
            PlayerInteractEvent.LeftClickBlock.Action.START
        ));
        check(
            player.getDeltaMovement().y < -2.4D
                && player.getDeltaMovement().horizontalDistanceSqr() < EPSILON,
            "looking up did not launch the player downward"
        );

        player.setDeltaMovement(Vec3.ZERO);
        ResinAnvilHammerEvents.leftClickBlock(new PlayerInteractEvent.LeftClickBlock(
            player,
            target,
            Direction.UP,
            PlayerInteractEvent.LeftClickBlock.Action.CLIENT_HOLD
        ));
        check(close(player.getDeltaMovement(), Vec3.ZERO), "holding left click repeatedly applied block recoil");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "A resin hammer helmet rebounds downward after striking a ceiling")
    static void resinAnvilHammerHelmetBouncesFromCeiling(ExtendedGameTestHelper helper) {
        helper.setBlock(3, 3, 3, Blocks.STONE);
        GameTestPlayer player = makeResinHammerHelmetPlayer(helper, new Vec3(3.5D, 1.0D, 3.5D));
        player.setOnGround(true);
        player.setDeltaMovement(0.0D, 0.4D, 0.0D);

        player.travel(Vec3.ZERO);

        check(player.verticalCollision && !player.verticalCollisionBelow, "upward travel did not hit the ceiling");
        check(close(player.getDeltaMovement().y, -0.48D), "ceiling rebound did not preserve the 1.2 restitution");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "A resin hammer helmet launches a headbutted entity and loses one durability")
    static void resinAnvilHammerHelmetLaunchesEntity(ExtendedGameTestHelper helper) {
        GameTestPlayer player = makeResinHammerHelmetPlayer(helper, new Vec3(3.5D, 1.0D, 3.5D));
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 2.85D, 3.5D));
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);
        ItemStack hammer = player.getItemBySlot(EquipmentSlot.HEAD);
        player.setOnGround(true);
        player.setDeltaMovement(0.0D, 0.2D, 0.0D);

        player.travel(Vec3.ZERO);

        check(target.getDeltaMovement().y > 0.29D, "headbutted entity did not receive the upward impulse");
        check(hammer.getDamageValue() == 1, "entity headbutt consumed the wrong amount of helmet durability");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x9x7", floor = true)
    @TestHolder(description = "A resin hammer helmet routes a follower headbutt impulse through its bonded leader")
    static void resinAnvilHammerHelmetLaunchesBondedGroupThroughFollower(ExtendedGameTestHelper helper) {
        GameTestPlayer player = makeResinHammerHelmetPlayer(helper, new Vec3(3.5D, 1.0D, 3.5D));
        Zombie follower = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 2.85D, 3.5D));
        Zombie leader = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 4.65D, 3.5D));
        follower.setNoGravity(true);
        leader.setNoGravity(true);
        follower.setDeltaMovement(Vec3.ZERO);
        leader.setDeltaMovement(Vec3.ZERO);
        check(EntityBondManager.connect(
            helper.getLevel(), follower, Direction.UP, leader, Direction.DOWN, true
        ), "headbutt test entities could not be bonded");
        check(EntityBondManager.isFollower(follower), "headbutted entity was not the bonded follower");
        ItemStack hammer = player.getItemBySlot(EquipmentSlot.HEAD);
        double followerStart = follower.getY();
        double leaderStart = leader.getY();
        player.setOnGround(true);
        player.setDeltaMovement(0.0D, 0.2D, 0.0D);

        player.travel(Vec3.ZERO);

        check(leader.getDeltaMovement().y > 0.29D, "follower headbutt did not route its impulse to the bonded leader");
        check(hammer.getDamageValue() == 1, "bonded follower headbutt consumed the wrong hammer durability");
        leader.move(MoverType.SELF, leader.getDeltaMovement());
        EntityBondManager.synchronizeComponent(leader);
        double followerMovement = follower.getY() - followerStart;
        double leaderMovement = leader.getY() - leaderStart;
        check(followerMovement > 0.29D, "bonded follower did not rise with its launched leader");
        check(leaderMovement > 0.29D, "bonded leader did not apply the headbutt impulse");
        check(
            Math.abs(followerMovement - leaderMovement) < 0.01D,
            "headbutted bonded group did not preserve its member spacing"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 80)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "A resin hammer helmet rebounds from an elytra wall collision without taking damage")
    static void resinAnvilHammerElytraWallBounceIsNonDamaging(ExtendedGameTestHelper helper) {
        helper.setBlock(4, 2, 3, Blocks.STONE);
        helper.setBlock(4, 3, 3, Blocks.STONE);
        GameTestPlayer player = makeResinHammerHelmetPlayer(helper, new Vec3(3.5D, 2.0D, 3.5D));
        player.setNoGravity(true);
        helper.runAfterDelay(61, () -> {
            Vec3 position = helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D));
            player.moveTo(position.x, position.y, position.z);
            player.invulnerableTime = 0;
            player.setYRot(-90.0F);
            player.setXRot(0.0F);
            player.setOnGround(false);
            player.startFallFlying();
            player.setDeltaMovement(0.9D, 0.0D, 0.0D);
            float healthBefore = player.getHealth();

            player.travel(Vec3.ZERO);

            check(player.horizontalCollision, "elytra travel did not collide with the wall");
            check(player.getDeltaMovement().x < -0.65D, "wall collision did not reflect the horizontal velocity");
            check(close(player.getHealth(), healthBefore), "elytra wall collision damaged the helmeted player");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("13x6x7")
    @TestHolder(description = "Sneak-using any anvil hammer retrieves every plastic product entity")
    static void anvilHammerRetrievesPlasticEntities(ExtendedGameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);

        ResinAnvilEntity resinAnvil = createResinAnvil(
            helper,
            new Vec3(2.5D, 2.0D, 3.5D),
            PlasticraftBlocks.RESIN_ANVIL.asStack()
        );
        HardenedResinAnvilEntity hardenedAnvil = createAnvil(helper, new Vec3(6.5D, 2.0D, 3.5D));
        HardenedResinCauldronEntity cauldron = createPot(
            helper,
            new Vec3(10.5D, 2.0D, 3.5D),
            PlasticEntityOrientation.DEFAULT
        );
        resinAnvil.setNoGravity(true);
        hardenedAnvil.setNoGravity(true);
        cauldron.setNoGravity(true);
        resinAnvil.setMagnetized(true);
        hardenedAnvil.setMagnetized(true);
        cauldron.setMagnetized(true);
        cauldron.getInput().insertItem(0, new ItemStack(Items.COBBLESTONE, 3), false);

        ItemStack resinHammer = PlasticraftItems.RESIN_ANVIL_HAMMER.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, resinHammer);
        check(
            resinAnvil.interact(player, InteractionHand.MAIN_HAND).consumesAction(),
            "resin hammer did not retrieve the resin anvil"
        );
        check(!resinAnvil.isAlive(), "retrieved resin anvil remained in the level");
        check(resinHammer.getDamageValue() == 0, "retrieving an entity damaged the resin hammer");

        ItemStack standardHammer = ModItems.ANVIL_HAMMER.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, standardHammer);
        check(
            hardenedAnvil.interact(player, InteractionHand.MAIN_HAND).consumesAction(),
            "standard hammer did not retrieve the hardened resin anvil"
        );
        check(
            cauldron.interact(player, InteractionHand.MAIN_HAND).consumesAction(),
            "standard hammer did not retrieve the hardened resin cauldron"
        );

        check(!hardenedAnvil.isAlive(), "retrieved hardened resin anvil remained in the level");
        check(!cauldron.isAlive(), "retrieved hardened resin cauldron remained in the level");
        check(countItem(player.getInventory(), PlasticraftBlocks.RESIN_ANVIL.asItem()) == 1, "resin anvil was not returned");
        check(
            countItem(player.getInventory(), PlasticraftBlocks.HARDEND_RESIN_ANVIL.asItem()) == 1,
            "hardened resin anvil was not returned"
        );
        check(
            countItem(player.getInventory(), PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asItem()) == 1,
            "hardened resin cauldron was not returned"
        );
        check(countItem(player.getInventory(), Items.COBBLESTONE) == 3, "cauldron contents were not returned");
        check(
            PlasticItemData.isMagnetized(findInventoryStack(player, PlasticraftBlocks.RESIN_ANVIL.asItem())),
            "retrieved resin anvil lost its magnetized state"
        );
        check(
            PlasticItemData.isMagnetized(findInventoryStack(player, PlasticraftBlocks.HARDEND_RESIN_ANVIL.asItem())),
            "retrieved hardened resin anvil lost its magnetized state"
        );
        check(
            PlasticItemData.isMagnetized(findInventoryStack(player, PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asItem())),
            "retrieved hardened resin cauldron lost its magnetized state"
        );
        check(standardHammer.getDamageValue() == 0, "retrieving entities damaged the standard hammer");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "A resin anvil never opens a menu when used")
    static void resinAnvilHasNoMenu(ExtendedGameTestHelper helper) {
        ResinAnvilEntity anvil = createResinAnvil(helper, new Vec3(3.5D, 1.0D, 3.5D), PlasticraftBlocks.RESIN_ANVIL.asStack());
        anvil.setNoGravity(true);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var menuBefore = player.containerMenu;
        InteractionResult result = anvil.interact(player, InteractionHand.MAIN_HAND);
        check(result == InteractionResult.PASS, "empty resin anvil consumed a normal use");
        check(player.containerMenu == menuBefore, "resin anvil opened or replaced the player's menu");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "9x8x9", floor = true)
    @TestHolder(description = "A captured mob can be placed inside a resin anvil, saved, loaded, and released")
    static void resinAnvilCapturedMobSurvivesAndReleases(ExtendedGameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack resinAnvil = PlasticraftBlocks.RESIN_ANVIL.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, resinAnvil);
        Entity capturedMob = helper.spawnWithNoFreeWill(EntityType.COW, new Vec3(2.5D, 1.0D, 2.5D));
        InteractionResult captureResult = resinAnvil.interactLivingEntity(
            player,
            (LivingEntity) capturedMob,
            InteractionHand.MAIN_HAND
        );
        check(captureResult.consumesAction(), "resin anvil item did not capture the mob");
        check(!capturedMob.isAlive(), "captured mob remained in the world");
        ItemStack capturedStack = findCapturedResinAnvil(player);
        check(!capturedStack.isEmpty(), "captured resin anvil was not returned to the player");

        BlockPos support = new BlockPos(4, 1, 4);
        helper.setBlock(support, Blocks.STONE);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, capturedStack);
        BlockHitResult hit = new BlockHitResult(
            helper.absolutePos(support).getCenter(),
            Direction.UP,
            helper.absolutePos(support),
            false
        );
        InteractionResult placeResult = capturedStack.useOn(new UseOnContext(
            helper.getLevel(),
            player,
            InteractionHand.MAIN_HAND,
            capturedStack,
            hit
        ));
        check(placeResult.consumesAction(), "sneak-use did not place the captured resin anvil");
        ResinAnvilEntity placed = helper.getLevel().getEntitiesOfClass(
            ResinAnvilEntity.class,
            new AABB(helper.absolutePos(support.above())).inflate(0.1D)
        ).stream().findFirst().orElseThrow(() -> new GameTestAssertException("captured resin anvil entity was not placed"));
        check(placed.hasCapturedMob(), "placed resin anvil lost its captured mob");

        CompoundTag saved = placed.saveWithoutId(new CompoundTag());
        placed.discard();
        ResinAnvilEntity loaded = new ResinAnvilEntity(PlasticraftEntities.RESIN_ANVIL.get(), helper.getLevel());
        loaded.load(saved);
        check(loaded.hasCapturedMob(), "captured mob component did not survive entity NBT");
        check(helper.getLevel().addFreshEntity(loaded), "failed to restore the saved resin anvil");
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        InteractionResult releaseResult = loaded.interact(player, InteractionHand.MAIN_HAND);
        check(releaseResult.consumesAction(), "empty-hand use did not release the captured mob");
        check(!loaded.hasCapturedMob(), "resin anvil retained the captured component after release");
        check(
            !helper.getLevel().getEntitiesOfClass(
                Cow.class,
                loaded.getBoundingBox().inflate(3.0D),
                Entity::isAlive
            ).isEmpty(),
            "released cow was not added back to the world"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("15x12x15")
    @TestHolder(description = "A resin anvil reflects its velocity from block impacts on all six faces")
    static void resinAnvilBouncesFromBlocksInAllDirections(ExtendedGameTestHelper helper) {
        List<Direction> directions = List.of(
            Direction.DOWN,
            Direction.UP,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
        );
        List<BlockPos> cells = List.of(
            new BlockPos(2, 4, 2),
            new BlockPos(6, 4, 2),
            new BlockPos(10, 4, 2),
            new BlockPos(2, 4, 8),
            new BlockPos(6, 4, 8),
            new BlockPos(10, 4, 8)
        );
        List<ResinAnvilEntity> anvils = new ArrayList<>();
        for (int index = 0; index < directions.size(); index++) {
            Direction direction = directions.get(index);
            BlockPos cell = cells.get(index);
            helper.setBlock(cell.relative(direction), Blocks.STONE);
            ResinAnvilEntity anvil = createResinAnvilInCell(helper, cell, PlasticraftBlocks.RESIN_ANVIL.asStack());
            anvil.setNoGravity(true);
            anvil.setDeltaMovement(Vec3.atLowerCornerOf(direction.getNormal()).scale(0.32D));
            anvils.add(anvil);
        }

        helper.runAfterDelay(2, () -> {
            for (int index = 0; index < directions.size(); index++) {
                Direction direction = directions.get(index);
                ResinAnvilEntity anvil = anvils.get(index);
                double reflectedSpeed = anvil.getDeltaMovement().dot(Vec3.atLowerCornerOf(direction.getNormal()));
                check(reflectedSpeed < -0.04D, direction + " block impact did not reflect velocity: " + anvil.getDeltaMovement());
            }
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 80)
    @EmptyTemplate(value = "11x8x11", floor = true)
    @TestHolder(description = "A resin anvil enters a plastic cauldron before bouncing from its real collision shape")
    static void resinAnvilDoesNotBounceFromPlasticCauldronBounds(ExtendedGameTestHelper helper) {
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(5.5D, 2.0D, 5.5D),
            PlasticEntityOrientation.DEFAULT
        );
        pot.setNoGravity(true);
        ResinAnvilEntity anvil = createResinAnvil(
            helper,
            new Vec3(5.5D, 5.5D, 5.5D),
            PlasticraftBlocks.RESIN_ANVIL.asStack()
        );
        boolean[] enteredOpening = {false};
        boolean[] bounced = {false};

        helper.startSequence()
            .thenExecuteFor(60, () -> {
                if (anvil.getY() < pot.getBoundingBox().maxY - 0.2D) {
                    enteredOpening[0] = true;
                }
                if (anvil.getDeltaMovement().y > 0.02D) {
                    check(
                        enteredOpening[0],
                        "resin anvil bounced from the cauldron bounds before entering its opening: y="
                            + anvil.getY() + ", velocity=" + anvil.getDeltaMovement()
                            + ", potTop=" + pot.getBoundingBox().maxY
                    );
                    bounced[0] = true;
                }
            })
            .thenExecute(() -> check(bounced[0], "resin anvil never bounced from the cauldron's real collision shape"))
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x5x7", floor = true)
    @TestHolder(description = "A resin anvil bounces from an entity and applies a bounded outward impulse")
    static void resinAnvilBouncesFromEntity(ExtendedGameTestHelper helper) {
        ResinAnvilEntity anvil = createResinAnvilInCell(
            helper,
            new BlockPos(3, 2, 3),
            PlasticraftBlocks.RESIN_ANVIL.asStack()
        );
        anvil.setNoGravity(true);
        Creeper target = helper.spawnWithNoFreeWill(EntityType.CREEPER, new Vec3(4.35D, 2.0D, 3.5D));
        target.setNoGravity(true);
        float healthBefore = target.getHealth();
        anvil.setDeltaMovement(0.32D, 0.0D, 0.0D);
        helper.runAfterDelay(2, () -> {
            check(anvil.getDeltaMovement().x < -0.04D, "entity impact did not bounce the resin anvil");
            check(target.getDeltaMovement().x > 0.0D, "entity impact did not push the target outward");
            check(target.getDeltaMovement().x <= 0.45D + EPSILON, "entity impulse exceeded its configured bound");
            check(close(target.getHealth(), healthBefore), "resin anvil impact damaged its target");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("17x5x9")
    @TestHolder(description = "Powered sliding rails accelerate, brake, and center all free resin products")
    static void poweredSlidingRailsControlPlasticProducts(ExtendedGameTestHelper helper) {
        List<AbstractPlasticEntity> accelerated = new ArrayList<>();
        List<AbstractPlasticEntity> braking = new ArrayList<>();
        int[] xPositions = {2, 7, 12};
        for (int x : xPositions) {
            BlockPos poweredPos = new BlockPos(x, 1, 2);
            helper.setBlock(poweredPos.below(), Blocks.REDSTONE_BLOCK);
            helper.setBlock(
                poweredPos,
                ModBlocks.POWERED_SLIDING_RAIL.get()
                    .defaultBlockState()
                    .setValue(PoweredSlidingRailBlock.FACING, Direction.EAST)
                    .setValue(PoweredSlidingRailBlock.POWERED, true)
            );
            BlockPos brakePos = new BlockPos(x, 1, 6);
            helper.setBlock(
                brakePos,
                ModBlocks.POWERED_SLIDING_RAIL.get()
                    .defaultBlockState()
                    .setValue(PoweredSlidingRailBlock.FACING, Direction.EAST)
                    .setValue(PoweredSlidingRailBlock.POWERED, false)
            );
        }

        accelerated.add(createAnvil(helper, new Vec3(2.5D, 2.0D, 2.78D)));
        accelerated.add(createResinAnvil(helper, new Vec3(7.5D, 2.0D, 2.78D), PlasticraftBlocks.RESIN_ANVIL.asStack()));
        accelerated.add(createPot(helper, new Vec3(12.5D, 2.0D, 2.78D), PlasticEntityOrientation.DEFAULT));
        braking.add(createAnvil(helper, new Vec3(2.5D, 2.0D, 6.78D)));
        braking.add(createResinAnvil(helper, new Vec3(7.5D, 2.0D, 6.78D), PlasticraftBlocks.RESIN_ANVIL.asStack()));
        braking.add(createPot(helper, new Vec3(12.5D, 2.0D, 6.78D), PlasticEntityOrientation.DEFAULT));
        for (AbstractPlasticEntity entity : accelerated) {
            entity.setNoGravity(true);
            entity.setDeltaMovement(Vec3.ZERO);
        }
        for (AbstractPlasticEntity entity : braking) {
            entity.setNoGravity(true);
            entity.setDeltaMovement(0.3D, 0.0D, 0.0D);
        }

        helper.runAfterDelay(1, () -> {
            for (AbstractPlasticEntity entity : accelerated) {
                double railCenterZ = Math.floor(entity.getZ()) + 0.5D;
                check(entity.getDeltaMovement().x > 0.3D, "powered rail did not accelerate " + entity.getType());
                check(
                    Math.abs(entity.getZ() - railCenterZ) < 0.24D,
                    "powered rail did not pull " + entity.getType() + " toward its centerline"
                );
            }
            for (AbstractPlasticEntity entity : braking) {
                double railCenterZ = Math.floor(entity.getZ()) + 0.5D;
                check(
                    entity.getDeltaMovement().horizontalDistance() < 0.27D,
                    "unpowered rail did not slow " + entity.getType()
                );
                check(
                    Math.abs(entity.getZ() - railCenterZ) < 0.28D,
                    "unpowered rail did not pull " + entity.getType() + " toward its center"
                );
            }
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x5x11")
    @TestHolder(description = "Powered sliding rails pull all free resin products from an adjacent rail stop or workstation")
    static void poweredSlidingRailsPullPlasticProductsFromStopLikeBlocks(ExtendedGameTestHelper helper) {
        int[] zPositions = {2, 5, 8};
        BlockState[] supports = {
            ModBlocks.SLIDING_RAIL_STOP.get().defaultBlockState(),
            ModBlocks.HEATER.get().defaultBlockState(),
            ModBlocks.HEATER.get().defaultBlockState()
        };
        for (int index = 0; index < zPositions.length; index++) {
            BlockPos supportPos = new BlockPos(3, 1, zPositions[index]);
            BlockPos railPos = supportPos.east();
            helper.setBlock(supportPos, supports[index]);
            helper.setBlock(railPos.below(), Blocks.REDSTONE_BLOCK);
            helper.setBlock(
                railPos,
                ModBlocks.POWERED_SLIDING_RAIL.get()
                    .defaultBlockState()
                    .setValue(PoweredSlidingRailBlock.FACING, Direction.EAST)
                    .setValue(PoweredSlidingRailBlock.POWERED, true)
            );
        }

        List<AbstractPlasticEntity> products = List.of(
            createAnvil(helper, new Vec3(3.5D, 2.0D, 2.78D)),
            createResinAnvil(helper, new Vec3(3.5D, 2.0D, 5.78D), PlasticraftBlocks.RESIN_ANVIL.asStack()),
            createPot(helper, new Vec3(3.5D, 2.0D, 8.78D), PlasticEntityOrientation.DEFAULT)
        );
        for (AbstractPlasticEntity product : products) {
            product.setDeltaMovement(Vec3.ZERO);
        }

        helper.runAfterDelay(2, () -> {
            for (int index = 0; index < products.size(); index++) {
                AbstractPlasticEntity product = products.get(index);
                double centerZ = zPositions[index] + 0.5D;
                check(product.getX() > helper.absolutePos(new BlockPos(4, 2, 0)).getX(),
                    "powered rail did not pull " + product.getType() + " off its stop-like support");
                check(product.getDeltaMovement().x > 0.3D,
                    "powered rail did not launch " + product.getType() + " forward");
                check(Math.abs(product.getZ() - helper.absoluteVec(new Vec3(0.0D, 0.0D, centerZ)).z) < 0.2D,
                    "powered rail did not center " + product.getType() + " while pulling it in");
            }
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("17x5x11")
    @TestHolder(description = "All free resin products coast at constant horizontal speed on ordinary sliding rails")
    static void plasticProductsCoastWithoutDragOnSlidingRails(ExtendedGameTestHelper helper) {
        int[] zPositions = {2, 5, 8};
        for (int z : zPositions) {
            for (int x = 1; x <= 15; x++) {
                helper.setBlock(
                    new BlockPos(x, 1, z),
                    ModBlocks.SLIDING_RAIL.get()
                        .defaultBlockState()
                        .setValue(SlidingRailBlock.AXIS, Direction.Axis.X)
                );
            }
        }

        List<AbstractPlasticEntity> products = List.of(
            createAnvil(helper, new Vec3(3.5D, 2.0D, 2.5D)),
            createResinAnvil(helper, new Vec3(3.5D, 2.0D, 5.5D), PlasticraftBlocks.RESIN_ANVIL.asStack()),
            createPot(helper, new Vec3(3.5D, 2.0D, 8.5D), PlasticEntityOrientation.DEFAULT)
        );
        double speed = 0.22D;
        double[] previousX = new double[products.size()];
        for (AbstractPlasticEntity product : products) {
            product.setDeltaMovement(speed, 0.0D, 0.0D);
        }
        for (int index = 0; index < products.size(); index++) {
            previousX[index] = products.get(index).getX();
        }

        helper.startSequence()
            .thenIdle(1)
            .thenExecuteFor(12, () -> {
                for (int index = 0; index < products.size(); index++) {
                    AbstractPlasticEntity product = products.get(index);
                    check(
                        product.getX() > previousX[index] + speed - EPSILON,
                        product.getType() + " paused while crossing a sliding-rail seam"
                    );
                    check(
                        product.getDeltaMovement().x > speed - EPSILON,
                        product.getType() + " lost horizontal speed on sliding rails: " + product.getDeltaMovement()
                    );
                    check(
                        Math.abs(product.getDeltaMovement().z) < EPSILON,
                        product.getType() + " drifted sideways on straight sliding rails"
                    );
                    previousX[index] = product.getX();
                }
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("9x5x7")
    @TestHolder(description = "Plastic products keep sliding while their physical collision still overlaps a rail")
    static void slidingRailSupportsPartiallyOverlappingPlasticProduct(ExtendedGameTestHelper helper) {
        for (int x = 1; x <= 7; x++) {
            helper.setBlock(
                new BlockPos(x, 1, 3),
                ModBlocks.SLIDING_RAIL.get().defaultBlockState().setValue(SlidingRailBlock.AXIS, Direction.Axis.X)
            );
        }
        double speed = 0.22D;
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(3.5D, 2.0D, 4.05D),
            PlasticEntityOrientation.DEFAULT
        );
        pot.setNoGravity(true);
        pot.setDeltaMovement(speed, 0.0D, 0.0D);
        double startX = pot.getX();

        helper.runAfterDelay(3, () -> {
            check(pot.getX() > startX + speed * 2.9D, "partially supported plastic product paused on the rail");
            check(
                pot.getDeltaMovement().x > speed - EPSILON,
                "partially supported plastic product lost rail speed: " + pot.getDeltaMovement()
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("15x5x13")
    @TestHolder(description = "Player pushes cannot replace the forward drive of a powered sliding rail")
    static void poweredSlidingRailDriveSurvivesPlayerPushes(ExtendedGameTestHelper helper) {
        List<BlockPos> railPositions = List.of(
            new BlockPos(4, 1, 2),
            new BlockPos(4, 1, 6),
            new BlockPos(10, 1, 10)
        );
        for (BlockPos railPos : railPositions) {
            helper.setBlock(railPos.below(), Blocks.REDSTONE_BLOCK);
            helper.setBlock(
                railPos,
                ModBlocks.POWERED_SLIDING_RAIL.get()
                    .defaultBlockState()
                    .setValue(PoweredSlidingRailBlock.FACING, Direction.EAST)
                    .setValue(PoweredSlidingRailBlock.POWERED, true)
            );
        }

        helper.runAfterDelay(1, () -> {
            List<AbstractPlasticEntity> forwardProducts = List.of(
                createAnvil(helper, new Vec3(4.5D, 2.0D, 2.5D)),
                createResinAnvil(helper, new Vec3(4.5D, 2.0D, 6.5D), PlasticraftBlocks.RESIN_ANVIL.asStack())
            );
            for (int index = 0; index < forwardProducts.size(); index++) {
                AbstractPlasticEntity product = forwardProducts.get(index);
                GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
                AABB bounds = product.plasticraft$getCollisionBox().bounds();
                player.moveTo(
                    bounds.minX - player.getBbWidth() * 0.5D,
                    bounds.minY,
                    bounds.getCenter().z
                );
                double startX = product.getX();
                player.move(MoverType.SELF, new Vec3(0.12D, 0.0D, 0.0D));
                player.move(MoverType.SELF, new Vec3(0.12D, 0.0D, 0.0D));
                check(product.getX() - startX > 0.2D,
                    "player did not push " + product.getType() + " along the powered rail");
                check(product.getDeltaMovement().x > 0.3D,
                    "player push replaced the powered-rail drive of " + product.getType());
            }

            HardenedResinCauldronEntity pot = createPot(
                helper,
                new Vec3(10.5D, 2.0D, 10.5D),
                PlasticEntityOrientation.DEFAULT
            );
            helper.setBlock(new BlockPos(11, 1, 10), Blocks.STONE);
            GameTestPlayer opposingPlayer = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
            AABB potBounds = pot.plasticraft$getCollisionBox().bounds();
            opposingPlayer.moveTo(
                potBounds.maxX + opposingPlayer.getBbWidth() * 0.5D,
                potBounds.minY,
                potBounds.getCenter().z
            );
            double potStartX = pot.getX();
            opposingPlayer.move(MoverType.SELF, new Vec3(-0.12D, 0.0D, 0.0D));
            opposingPlayer.move(MoverType.SELF, new Vec3(-0.12D, 0.0D, 0.0D));
            check(pot.getX() - potStartX < -0.2D, "player did not push the pot against the powered rail");
            check(pot.getDeltaMovement().x > 0.3D, "opposing player push replaced the pot's powered-rail drive");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 50)
    @EmptyTemplate("9x5x7")
    @TestHolder(description = "Detector sliding rails keep detecting a free resin product and reset after it leaves")
    static void detectorSlidingRailTracksPlasticEntity(ExtendedGameTestHelper helper) {
        BlockPos railPos = new BlockPos(3, 1, 3);
        helper.setBlock(
            railPos,
            ModBlocks.DETECTOR_SLIDING_RAIL.get()
                .defaultBlockState()
                .setValue(DetectorSlidingRailBlock.FACING, Direction.EAST)
        );
        ResinAnvilEntity anvil = createResinAnvil(
            helper,
            new Vec3(3.5D, 2.0D, 3.5D),
            PlasticraftBlocks.RESIN_ANVIL.asStack()
        );
        anvil.setNoGravity(true);

        helper.runAfterDelay(22, () -> {
            BlockState state = helper.getBlockState(railPos);
            check(state.getValue(DetectorSlidingRailBlock.POWERED), "detector rail dropped its occupied signal");
            int power = helper.getLevel()
                .getBlockEntity(helper.absolutePos(railPos), ModBlockEntities.DETECTOR_SLIDING_RAIL.get())
                .orElseThrow(() -> new GameTestAssertException("detector rail block entity was missing"))
                .getPower();
            check(power == 1, "one resin entity produced detector strength " + power);
            Vec3 away = helper.absoluteVec(new Vec3(7.5D, 2.0D, 3.5D));
            anvil.setPos(away);
        });
        helper.runAfterDelay(44, () -> {
            BlockState state = helper.getBlockState(railPos);
            check(!state.getValue(DetectorSlidingRailBlock.POWERED), "detector rail stayed powered after the entity left");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("9x5x7")
    @TestHolder(description = "Activator sliding rails hold a free resin product for their full charge pulse")
    static void activatorSlidingRailChargesPlasticEntity(ExtendedGameTestHelper helper) {
        BlockPos railPos = new BlockPos(3, 1, 3);
        helper.setBlock(railPos.below(), Blocks.REDSTONE_BLOCK);
        helper.setBlock(
            railPos,
            ModBlocks.ACTIVATOR_SLIDING_RAIL.get()
                .defaultBlockState()
                .setValue(ActivatorSlidingRailBlock.FACING, Direction.EAST)
                .setValue(ActivatorSlidingRailBlock.POWERED, true)
        );
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(3.25D, 2.0D, 3.7D),
            PlasticEntityOrientation.DEFAULT
        );
        pot.setNoGravity(true);
        pot.setDeltaMovement(0.3D, 0.0D, 0.0D);
        Vec3 railCenter = helper.absolutePos(railPos).getCenter().add(0.0D, 0.5D, 0.0D);

        helper.runAfterDelay(4, () -> {
            check(pot.getDeltaMovement().horizontalDistanceSqr() < EPSILON, "activator rail released the pot too early");
            check(
                Math.abs(pot.getX() - railCenter.x) < EPSILON && Math.abs(pot.getZ() - railCenter.z) < EPSILON,
                "activator rail did not hold the pot at its center"
            );
        });
        helper.runAfterDelay(10, () -> {
            check(pot.getX() > railCenter.x + 0.2D, "activator rail did not release the charged pot forward");
            check(pot.getDeltaMovement().x > 0.2D, "charged pot did not resume in its incoming direction");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x6x7")
    @TestHolder(description = "A resin hammer launches a hardened resin anvil for ten damage and Anvil Looting")
    static void hardenedResinAnvilHammerImpactDealsTenDamage(ExtendedGameTestHelper helper) {
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(3.0D, 2.0D, 3.5D));
        anvil.setNoGravity(true);
        var target = helper.spawnWithNoFreeWill(EntityType.CREEPER, new Vec3(5.0D, 2.0D, 3.5D));
        target.setNoGravity(true);
        AtomicInteger eventCount = new AtomicInteger();
        float[] eventDamage = {0.0F};
        helper.addTemporaryListener((AnvilEvent.HurtEntity event) -> {
            if (event.getEntity() == anvil && event.getHurtedEntity() == target) {
                eventCount.incrementAndGet();
                eventDamage[0] = event.getDamage();
            }
        });

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(-90.0F);
        player.setItemInHand(
            InteractionHand.MAIN_HAND,
            PlasticraftItems.RESIN_ANVIL_HAMMER.asStack()
        );
        player.attack(anvil);
        check(anvil.getDeltaMovement().x > 2.4D, "resin hammer did not launch the hardened anvil");

        helper.runAfterDelay(2, () -> {
            check(close(target.getHealth(), 10.0D), "hammer-speed hardened anvil dealt " + (20.0F - target.getHealth()));
            check(eventCount.get() == 1, "hardened anvil posted " + eventCount.get() + " looting events");
            check(close(eventDamage[0], 10.0D), "looting event reported " + eventDamage[0] + " damage");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "7x11x7", floor = true)
    @TestHolder(description = "A hardened resin anvil begins dealing one damage after a five-block fall")
    static void hardenedResinAnvilFiveBlockFallStartsAtOneDamage(ExtendedGameTestHelper helper) {
        var target = helper.spawnWithNoFreeWill(EntityType.CREEPER, new Vec3(3.5D, 1.0D, 3.5D));
        target.setNoGravity(true);
        double startY = 1.0D + target.getBbHeight() + 5.0D;
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(3.5D, startY, 3.5D));
        AtomicInteger eventCount = new AtomicInteger();
        float[] eventDamage = {0.0F};
        helper.addTemporaryListener((AnvilEvent.HurtEntity event) -> {
            if (event.getEntity() == anvil && event.getHurtedEntity() == target) {
                eventCount.incrementAndGet();
                eventDamage[0] = event.getDamage();
            }
        });

        helper.runAfterDelay(24, () -> {
            check(close(target.getHealth(), 19.0D), "five-block fall dealt " + (20.0F - target.getHealth()));
            check(eventCount.get() == 1, "five-block fall posted " + eventCount.get() + " looting events");
            check(close(eventDamage[0], 1.0D), "five-block fall reported " + eventDamage[0] + " damage");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x8x7", floor = true)
    @TestHolder(description = "A player falling onto a resin anvil rebounds like landing on a resin block")
    static void playerBouncesFromResinAnvil(ExtendedGameTestHelper helper) {
        ResinAnvilEntity anvil = createResinAnvil(
            helper,
            new Vec3(3.5D, 1.0D, 3.5D),
            PlasticraftBlocks.RESIN_ANVIL.asStack()
        );
        anvil.setNoGravity(true);
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 start = helper.absoluteVec(new Vec3(3.5D, 2.45D, 3.5D));
        player.moveTo(start.x, start.y, start.z);
        player.setDeltaMovement(0.0D, -0.75D, 0.0D);

        player.move(MoverType.SELF, player.getDeltaMovement());

        check(player.getDeltaMovement().y > 0.70D, "falling player did not rebound from the resin anvil");
        check(
            Math.abs(player.getBoundingBox().minY - anvil.getBoundingBox().maxY) < 0.03D,
            "falling player was not collision-clipped at the resin anvil surface"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 150)
    @EmptyTemplate(value = "7x8x7", floor = true)
    @TestHolder(description = "A vanilla falling anvil bounces on resin and lands after losing its rebound speed")
    static void fallingAnvilSettlesOnResinAnvil(ExtendedGameTestHelper helper) {
        ResinAnvilEntity support = createResinAnvil(
            helper,
            new Vec3(3.5D, 1.0D, 3.5D),
            PlasticraftBlocks.RESIN_ANVIL.asStack()
        );
        support.setNoGravity(true);
        BlockPos sourcePos = new BlockPos(3, 3, 3);
        BlockPos landingPos = sourcePos.below();
        helper.setBlock(sourcePos, Blocks.ANVIL);
        FallingBlockEntity falling = FallingBlockEntity.fall(
            helper.getLevel(),
            helper.absolutePos(sourcePos),
            Blocks.ANVIL.defaultBlockState()
        );
        falling.setDeltaMovement(0.0D, -0.35D, 0.0D);
        boolean[] bounced = {false};

        helper.startSequence()
            .thenExecuteFor(120, () -> {
                if (falling.isAlive() && falling.getDeltaMovement().y > 0.05D) bounced[0] = true;
            })
            .thenExecute(() -> {
                check(bounced[0], "falling anvil never rebounded from the resin anvil");
                check(helper.getBlockState(landingPos).is(Blocks.ANVIL), "falling anvil did not settle as a block");
                check(!falling.isAlive(), "settled falling anvil remained an entity");
                check(
                    helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class,
                        support.getBoundingBox().inflate(3.0D),
                        item -> item.getItem().is(Items.ANVIL)
                    ).isEmpty(),
                    "falling anvil shattered while rebounding"
                );
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 190)
    @EmptyTemplate(value = "17x9x7", floor = true)
    @TestHolder(description = "Every movable plastic product repeatedly rebounds and then settles on a resin anvil")
    static void allPlasticProductsBounceAndSettleOnResinAnvils(ExtendedGameTestHelper helper) {
        double[] laneCenters = {2.5D, 5.5D, 8.5D, 11.5D, 14.5D};
        List<ResinAnvilEntity> supports = new ArrayList<>();
        for (double laneCenter : laneCenters) {
            ResinAnvilEntity support = createResinAnvil(
                helper,
                new Vec3(laneCenter, 1.0D, 3.5D),
                PlasticraftBlocks.RESIN_ANVIL.asStack()
            );
            support.setNoGravity(true);
            support.setDeltaMovement(Vec3.ZERO);
            supports.add(support);
        }

        HardenedResinAnvilEntity hardenedAnvil = createAnvil(
            helper,
            new Vec3(laneCenters[0], 5.0D, 3.5D)
        );
        HardenedResinCauldronEntity cauldron = createPot(
            helper,
            new Vec3(laneCenters[1], 5.0D, 3.5D),
            PlasticEntityOrientation.DEFAULT
        );
        CatalyticPressLidEntity lid = new CatalyticPressLidEntity(
            PlasticraftEntities.CATALYTIC_PRESS_LID.get(),
            helper.getLevel(),
            helper.absoluteVec(new Vec3(laneCenters[2], 5.0D, 3.5D)),
            PlasticraftBlocks.CATALYTIC_PRESS_LID.get().defaultBlockState(),
            PlasticraftBlocks.CATALYTIC_PRESS_LID.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        check(helper.getLevel().addFreshEntity(lid), "failed to add catalytic press lid rebound target");
        UniversalPlasticEntity universal = new UniversalPlasticEntity(
            PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
            helper.getLevel(),
            helper.absoluteVec(new Vec3(laneCenters[3], 5.0D, 3.5D)),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        check(helper.getLevel().addFreshEntity(universal), "failed to add universal plastic rebound target");
        ResinAnvilEntity resinAnvil = createResinAnvil(
            helper,
            new Vec3(laneCenters[4], 5.0D, 3.5D),
            PlasticraftBlocks.RESIN_ANVIL.asStack()
        );
        List<AbstractPlasticEntity> fallingProducts = List.of(
            hardenedAnvil,
            cauldron,
            lid,
            universal,
            resinAnvil
        );
        for (AbstractPlasticEntity product : fallingProducts) {
            product.setDeltaMovement(0.0D, -0.15D, 0.0D);
        }

        int[] reboundCounts = new int[fallingProducts.size()];
        boolean[] rising = new boolean[fallingProducts.size()];
        helper.startSequence()
            .thenExecuteFor(160, () -> {
                for (int index = 0; index < fallingProducts.size(); index++) {
                    double verticalSpeed = fallingProducts.get(index).getDeltaMovement().y;
                    if (verticalSpeed > 0.05D && !rising[index]) {
                        reboundCounts[index]++;
                        rising[index] = true;
                    } else if (verticalSpeed <= 0.0D) {
                        rising[index] = false;
                    }
                }
            })
            .thenExecute(() -> {
                for (int index = 0; index < fallingProducts.size(); index++) {
                    AbstractPlasticEntity product = fallingProducts.get(index);
                    String name = BuiltInRegistries.ENTITY_TYPE.getKey(product.getType()).toString();
                    check(product.isAlive(), name + " was destroyed while rebounding");
                    check(reboundCounts[index] >= 2, name + " only rebounded " + reboundCounts[index] + " times");
                    check(
                        Math.abs(product.getDeltaMovement().y) < 0.03D,
                        name + " did not settle after its rebounds: " + product.getDeltaMovement()
                    );
                    check(
                        PlasticEntityPhysics.findSupport(product, Direction.DOWN) == supports.get(index),
                        name + " did not settle on its resin anvil"
                    );
                }
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "A blockified resin anvil retains resin-block bounce behavior")
    static void playerBouncesFromBlockifiedResinAnvil(ExtendedGameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        BlockState state = PlasticraftBlocks.RESIN_ANVIL.get()
            .defaultBlockState()
            .setValue(AbstractPlasticEntityBlock.BONDED, true);
        helper.setBlock(pos, state);
        Zombie falling = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 2.0D, 3.5D));
        falling.setDeltaMovement(0.0D, -0.75D, 0.0D);

        state.getBlock().updateEntityAfterFallOn(helper.getLevel(), falling);

        check(falling.getDeltaMovement().y > 0.70D, "blockified resin anvil did not bounce a falling entity");
        check(state.getBlock().isSlimeBlock(state), "blockified resin anvil was not marked as a slime block");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x6x7")
    @TestHolder(description = "A player continuously transfers lateral movement to a resin anvil")
    static void playerPushesResinAnvilContinuously(ExtendedGameTestHelper helper) {
        for (int x = 1; x < 10; x++) {
            helper.setBlock(x, 1, 3, Blocks.STONE);
        }
        ResinAnvilEntity anvil = createResinAnvil(
            helper,
            new Vec3(5.5D, 2.0D, 3.5D),
            PlasticraftBlocks.RESIN_ANVIL.asStack()
        );
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        AABB bounds = anvil.plasticraft$getCollisionBox().bounds();
        player.moveTo(
            bounds.minX - player.getBbWidth() * 0.5D,
            bounds.minY,
            bounds.getCenter().z
        );
        helper.runAfterDelay(3, () -> {
            double startX = anvil.getX();
            double playerStartX = player.getX();
            player.move(MoverType.SELF, new Vec3(0.18D, 0.0D, 0.0D));
            player.move(MoverType.SELF, new Vec3(0.18D, 0.0D, 0.0D));
            double playerMovement = player.getX() - playerStartX;
            double anvilMovement = anvil.getX() - startX;
            check(playerMovement > 0.30D, "resin anvil clipped repeated player movement: " + playerMovement);
            check(anvilMovement > 0.30D, "resin anvil did not follow repeated player movement: " + anvilMovement);
            check(
                Math.abs(anvilMovement - playerMovement) < 0.03D,
                "resin anvil did not remain against the moving player"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("9x5x7")
    @TestHolder(description = "Only magnetized plastic anvils and pots qualify for magnetic acceleration")
    static void magnetizationControlsAcceleration(ExtendedGameTestHelper helper) {
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 2.0D, 2.5D));
        HardenedResinCauldronEntity pot = createPot(helper, new Vec3(6.5D, 2.0D, 2.5D), PlasticEntityOrientation.DEFAULT);
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
    @EmptyTemplate("9x6x7")
    @TestHolder(description = "Creative Shift-use toggles resin-product magnetization but survival cannot")
    static void creativeMagnetToolMagnetizesResinProducts(ExtendedGameTestHelper helper) {
        HardenedResinAnvilEntity hardened = createAnvil(helper, new Vec3(1.5D, 2.0D, 3.5D));
        ResinAnvilEntity resin = createResinAnvil(
            helper,
            new Vec3(3.5D, 2.0D, 3.5D),
            PlasticraftBlocks.RESIN_ANVIL.asStack()
        );
        HardenedResinCauldronEntity pot = createPot(helper, new Vec3(5.5D, 2.0D, 3.5D), PlasticEntityOrientation.DEFAULT);
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        creative.setShiftKeyDown(true);
        ItemStack magnet = ModItems.MAGNET.asStack();
        creative.setItemInHand(InteractionHand.MAIN_HAND, magnet);
        check(
            hardened.interactAt(creative, Vec3.ZERO, InteractionHand.MAIN_HAND).consumesAction(),
            "creative magnet interaction did not handle the hardened resin anvil"
        );
        check(hardened.isMagnetized(), "creative magnet did not magnetize the hardened resin anvil");
        check(
            resin.interactAt(creative, Vec3.ZERO, InteractionHand.MAIN_HAND).consumesAction(),
            "creative magnet interaction did not handle the resin anvil"
        );
        check(resin.isMagnetized(), "creative magnet did not magnetize the resin anvil");
        check(
            pot.interactAt(creative, Vec3.ZERO, InteractionHand.MAIN_HAND).consumesAction(),
            "creative magnet interaction did not handle the hardened resin cauldron"
        );
        check(pot.isMagnetized(), "creative magnet did not magnetize the hardened resin cauldron");
        check(
            hardened.interactAt(creative, Vec3.ZERO, InteractionHand.MAIN_HAND).consumesAction(),
            "creative magnet interaction did not handle hardened resin anvil demagnetization"
        );
        check(!hardened.isMagnetized(), "creative magnet did not demagnetize the hardened resin anvil");
        check(
            resin.interactAt(creative, Vec3.ZERO, InteractionHand.MAIN_HAND).consumesAction(),
            "creative magnet interaction did not handle resin anvil demagnetization"
        );
        check(!resin.isMagnetized(), "creative magnet did not demagnetize the resin anvil");
        check(
            pot.interactAt(creative, Vec3.ZERO, InteractionHand.MAIN_HAND).consumesAction(),
            "creative magnet interaction did not handle hardened resin cauldron demagnetization"
        );
        check(!pot.isMagnetized(), "creative magnet did not demagnetize the hardened resin cauldron");

        HardenedResinAnvilEntity survivalTarget = createAnvil(helper, new Vec3(7.5D, 2.0D, 3.5D));
        Player survival = helper.makeMockPlayer(GameType.SURVIVAL);
        survival.setShiftKeyDown(true);
        survival.setItemInHand(InteractionHand.MAIN_HAND, ModItems.MAGNET.asStack());
        check(
            survivalTarget.interactAt(survival, Vec3.ZERO, InteractionHand.MAIN_HAND) == InteractionResult.PASS,
            "survival magnet unexpectedly handled the hardened resin anvil"
        );
        check(!survivalTarget.isMagnetized(), "survival magnet changed the hardened resin anvil");

        hardened.discard();
        resin.discard();
        pot.discard();
        survivalTarget.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("20x6x7")
    @TestHolder(description = "Anvil hammer radial selections rotate all free plastic products in place")
    static void anvilHammerChangesPlasticAttachmentFaces(ExtendedGameTestHelper helper) {
        PlasticEntityOrientation initial = new PlasticEntityOrientation(Direction.EAST, 2);
        HardenedResinAnvilEntity hardened = createAnvil(
            helper,
            new Vec3(2.5D, 2.5D, 3.5D),
            initial
        );
        ResinAnvilEntity resin = createResinAnvil(
            helper,
            new Vec3(5.5D, 2.5D, 3.5D),
            PlasticraftBlocks.RESIN_ANVIL.asStack()
        );
        resin.setOrientation(initial);
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(8.5D, 2.5D, 3.5D),
            initial
        );
        CatalyticPressLidEntity lid = new CatalyticPressLidEntity(
            PlasticraftEntities.CATALYTIC_PRESS_LID.get(),
            helper.getLevel(),
            helper.absoluteVec(new Vec3(11.5D, 2.5D, 3.5D)),
            PlasticraftBlocks.CATALYTIC_PRESS_LID.get().defaultBlockState(),
            PlasticraftBlocks.CATALYTIC_PRESS_LID.asStack(),
            initial
        );
        check(helper.getLevel().addFreshEntity(lid), "failed to add catalytic press lid");
        UniversalPlasticEntity universal = new UniversalPlasticEntity(
            PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
            helper.getLevel(),
            helper.absoluteVec(new Vec3(14.5D, 2.5D, 3.5D)),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack(),
            initial
        );
        check(helper.getLevel().addFreshEntity(universal), "failed to add universal plastic product");
        hardened.setNoGravity(true);
        resin.setNoGravity(true);
        pot.setNoGravity(true);
        lid.setNoGravity(true);
        universal.setNoGravity(true);

        List<Vec3> originalPositions = List.of(
            hardened.position(),
            resin.position(),
            pot.position(),
            lid.position(),
            universal.position()
        );

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(18.0D, 2.0D, 3.5D)));
        player.setShiftKeyDown(false);
        ItemStack hammer = ModItems.ANVIL_HAMMER.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, hammer);

        int targetIndex = 0;
        for (AbstractPlasticEntity target : List.of(hardened, resin, pot, lid, universal)) {
            check(target.supportsAnvilHammerOrientationMenu(), "plastic product did not expose its orientation menu");
            for (Direction face : Direction.values()) {
                check(
                    target.plasticraft$changeAttachmentFace(player, InteractionHand.MAIN_HAND, face),
                    "anvil hammer rejected attachment face " + face
                );
                check(target.getOrientation().attachmentFace() == face, "anvil hammer selected the wrong attachment face");
                check(target.getOrientation().quarterTurn() == 2, "changing attachment face changed the in-plane rotation");
                check(
                    close(target.position(), originalPositions.get(targetIndex)),
                    "changing attachment face moved the free plastic entity"
                );
            }
            targetIndex++;
        }
        check(hammer.getDamageValue() == 0, "rotating resin anvils damaged the anvil hammer");
        check(
            hardened.isAlive() && resin.isAlive() && pot.isAlive() && lid.isAlive() && universal.isAlive(),
            "rotating a plastic product removed its entity"
        );

        hardened.discard();
        resin.discard();
        pot.discard();
        lid.discard();
        universal.discard();
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("13x7x7")
    @TestHolder(description = "Anvil hammer rotation rejects target shapes that intersect blocks or entities")
    static void anvilHammerRejectsObstructedPlasticRotations(ExtendedGameTestHelper helper) {
        PlasticEntityOrientation side = new PlasticEntityOrientation(Direction.EAST, 1);
        PlasticEntityOrientation upright = new PlasticEntityOrientation(Direction.UP, 1);
        HardenedResinAnvilEntity floorTarget = createAnvil(
            helper,
            new Vec3(3.5D, 2.0D, 3.5D),
            side
        );
        floorTarget.setNoGravity(true);
        helper.setBlock(3, 1, 3, Blocks.STONE);
        double floorTop = helper.absolutePos(new BlockPos(3, 1, 3)).getY() + 1.0D;
        floorTarget.setPos(floorTarget.position().add(
            0.0D,
            floorTop - floorTarget.plasticraft$getCollisionBox().bounds().minY,
            0.0D
        ));

        HardenedResinAnvilEntity entityTarget = createAnvil(
            helper,
            new Vec3(8.5D, 3.0D, 3.5D),
            side
        );
        entityTarget.setNoGravity(true);
        HardenedResinAnvilEntity blocker = createAnvil(
            helper,
            new Vec3(8.5D, 2.1D, 3.5D),
            PlasticEntityOrientation.DEFAULT
        );
        blocker.setNoGravity(true);
        VoxelShape rotatedEntityTarget = PlasticEntityCollisionShapes.rotate(
            AbstractPlasticEntityBlock.ROYAL_ANVIL_COLLISION_SHAPE,
            upright
        ).move(entityTarget.getX() - 0.5D, entityTarget.getY(), entityTarget.getZ() - 0.5D);
        check(
            !Shapes.joinIsNotEmpty(
                entityTarget.plasticraft$getCollisionShape(),
                blocker.plasticraft$getCollisionShape(),
                BooleanOp.AND
            ),
            "entity blocker overlapped the starting orientation"
        );
        check(
            Shapes.joinIsNotEmpty(rotatedEntityTarget, blocker.plasticraft$getCollisionShape(), BooleanOp.AND),
            "entity blocker did not overlap the target orientation"
        );

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(11.5D, 2.0D, 3.5D)));
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.ANVIL_HAMMER.asStack());
        check(!floorTarget.canHammerRotateTo(upright), "floor did not obstruct the upright target shape");
        check(
            !floorTarget.plasticraft$changeAttachmentFace(player, InteractionHand.MAIN_HAND, Direction.UP),
            "anvil hammer rotated the floor target into a block"
        );
        check(floorTarget.getOrientation().equals(side), "blocked floor rotation changed the entity orientation");
        check(!entityTarget.canHammerRotateTo(upright), "entity did not obstruct the upright target shape");
        check(
            !entityTarget.plasticraft$changeAttachmentFace(player, InteractionHand.MAIN_HAND, Direction.UP),
            "anvil hammer rotated the target into another entity"
        );
        check(entityTarget.getOrientation().equals(side), "blocked entity rotation changed the entity orientation");

        helper.setBlock(3, 1, 3, Blocks.AIR);
        blocker.discard();
        check(floorTarget.canHammerRotateTo(upright), "cleared floor target remained obstructed");
        check(entityTarget.canHammerRotateTo(upright), "cleared entity target remained obstructed");
        check(
            floorTarget.plasticraft$changeAttachmentFace(player, InteractionHand.MAIN_HAND, Direction.UP),
            "anvil hammer rejected the cleared floor target"
        );
        check(
            entityTarget.plasticraft$changeAttachmentFace(player, InteractionHand.MAIN_HAND, Direction.UP),
            "anvil hammer rejected the cleared entity target"
        );

        floorTarget.discard();
        entityTarget.discard();
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x6x7")
    @TestHolder(description = "Hardened resin cauldron outlets match fish tank hammer and automatic output behavior")
    static void hardenedResinCauldronHammerOutlet(ExtendedGameTestHelper helper) {
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(4.5D, 2.0D, 3.5D),
            PlasticEntityOrientation.DEFAULT
        );
        pot.setNoGravity(true);
        helper.setBlock(5, 2, 3, Blocks.CHEST);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hammer = ModItems.ANVIL_HAMMER.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, hammer);
        check(
            pot.plasticraft$useAnvilHammer(player, InteractionHand.MAIN_HAND, Direction.EAST).consumesAction(),
            "anvil hammer did not open the cauldron outlet"
        );
        check(pot.getOutletDirection() == Direction.EAST, "cauldron outlet opened on the wrong face");
        check(pot.getOutletLocalDirection() == Direction.EAST, "cauldron east outlet used the wrong local face");
        check(isEmpty(pot.getInput()), "anvil hammer was inserted into the cauldron input");
        check(player.getMainHandItem() == hammer && hammer.getCount() == 1, "opening an outlet consumed the anvil hammer");

        CompoundTag saved = pot.saveWithoutId(new CompoundTag());
        HardenedResinCauldronEntity loaded = new HardenedResinCauldronEntity(
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get(),
            helper.getLevel()
        );
        loaded.load(saved);
        check(loaded.getOutletDirection() == Direction.EAST, "cauldron outlet did not survive entity persistence");

        pot.insertRecipeOutput(new ItemStack(Items.DIAMOND, 3));
        check(isEmpty(pot.getOutput()), "cauldron outlet retained output after finding an adjacent container");
        check(
            helper.getBlockEntity(new BlockPos(5, 2, 3)) instanceof Container chest
                && countItem(chest, Items.DIAMOND) == 3,
            "cauldron outlet did not transfer output into the adjacent chest"
        );

        check(
            pot.plasticraft$useAnvilHammer(player, InteractionHand.MAIN_HAND, Direction.EAST).consumesAction(),
            "anvil hammer did not close the cauldron outlet"
        );
        check(!pot.hasOutlet(), "clicking the same cauldron outlet face did not close it");
        pot.insertRecipeOutput(new ItemStack(Items.EMERALD, 2));
        check(countItem(pot.getOutput(), Items.EMERALD) == 2, "closed cauldron outlet continued automatic output");

        check(
            pot.plasticraft$useAnvilHammer(player, InteractionHand.MAIN_HAND, Direction.WEST).consumesAction(),
            "anvil hammer did not open the west-facing cauldron outlet"
        );
        check(pot.getOutletLocalDirection() == Direction.WEST, "cauldron west outlet used the wrong local face");
        check(pot.getOutletDirection() == Direction.WEST, "cauldron west outlet used the opposite world direction");
        pot.plasticraft$useAnvilHammer(player, InteractionHand.MAIN_HAND, Direction.WEST);

        check(
            pot.plasticraft$useAnvilHammer(player, InteractionHand.MAIN_HAND, Direction.NORTH).consumesAction(),
            "anvil hammer did not open the north-facing cauldron outlet"
        );
        check(
            pot.getOutletLocalDirection() == Direction.NORTH,
            "the cauldron's north outlet was rendered on the opposite local face"
        );
        check(
            pot.getOutletDirection() == Direction.NORTH,
            "the cauldron's north outlet used the opposite world direction"
        );
        pot.plasticraft$useAnvilHammer(player, InteractionHand.MAIN_HAND, Direction.NORTH);
        check(
            pot.plasticraft$useAnvilHammer(player, InteractionHand.MAIN_HAND, Direction.SOUTH).consumesAction(),
            "anvil hammer did not open the south-facing cauldron outlet"
        );
        check(
            pot.getOutletLocalDirection() == Direction.SOUTH,
            "the cauldron's south outlet was rendered on the opposite local face"
        );
        check(
            pot.getOutletDirection() == Direction.SOUTH,
            "the cauldron's south outlet used the opposite world direction"
        );

        pot.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("13x6x7")
    @TestHolder(description = "Sneak-use places AnvilCraft pipes against resin product faces")
    static void shiftUsePlacesPipesOnResinProducts(ExtendedGameTestHelper helper) {
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(3.5D, 2.0D, 3.5D));
        HardenedResinCauldronEntity cauldron = createPot(
            helper,
            new Vec3(8.5D, 2.0D, 3.5D),
            PlasticEntityOrientation.DEFAULT
        );
        anvil.setNoGravity(true);
        cauldron.setNoGravity(true);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setYRot(0.0F);

        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.PIPE.asStack());
        InteractionResult anvilResult = anvil.interactAt(
            player,
            new Vec3(0.0D, anvil.getBbHeight() * 0.5D, -anvil.getBbWidth() * 0.5D),
            InteractionHand.MAIN_HAND
        );
        check(anvilResult.consumesAction(), "sneak-use did not place a pipe on the hardened resin anvil");
        check(
            helper.getBlockState(new BlockPos(3, 2, 2)).getBlock() instanceof PipeBlock,
            "the pipe item did not create a pipe block beside the hardened resin anvil"
        );

        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.PIPE.asStack());
        InteractionResult cauldronResult = cauldron.interactAt(
            player,
            new Vec3(0.0D, cauldron.getBbHeight() * 0.5D, -cauldron.getBbWidth() * 0.5D),
            InteractionHand.MAIN_HAND
        );
        check(cauldronResult.consumesAction(), "sneak-use did not place a pipe on the hardened resin cauldron");
        BlockState cauldronPipe = helper.getBlockState(new BlockPos(8, 2, 2));
        check(cauldronPipe.getBlock() instanceof PipeBlock, "the pipe item did not create a pipe beside the cauldron");
        check(
            cauldronPipe.getValue(PipeBlock.AXIS) == Direction.Axis.Z,
            "the pipe placed against the entity fluid endpoint did not face the cauldron"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate("7x9x7")
    @TestHolder(description = "Hardened resin cauldrons use arbitrary-volume container semantics in pipe networks")
    static void hardenedResinCauldronConnectsToFluidPipes(ExtendedGameTestHelper helper) {
        HardenedResinCauldronEntity source = createPot(
            helper,
            new Vec3(3.5D, 6.0D, 3.5D),
            PlasticEntityOrientation.DEFAULT
        );
        HardenedResinCauldronEntity target = createPot(
            helper,
            new Vec3(3.5D, 1.0D, 3.5D),
            PlasticEntityOrientation.DEFAULT
        );
        source.setNoGravity(true);
        target.setNoGravity(true);
        source.getFluidHandler().fill(
            new FluidStack(Fluids.WATER, 375),
            IFluidHandler.FluidAction.EXECUTE
        );

        BlockState pipe = ModBlocks.PIPE_STRAIGHT.get().defaultBlockState()
            .setValue(PipeBlock.AXIS, Direction.Axis.Y);
        for (int y = 2; y <= 5; y++) {
            helper.setBlock(new BlockPos(3, y, 3), pipe);
        }

        BlockPos sourcePos = BlockPos.containing(source.getBoundingBox().getCenter());
        FluidContainerLookup.Result endpoint = FluidContainerLookup.find(helper.getLevel(), sourcePos, Direction.DOWN);
        check(endpoint != null, "pipe API did not discover the hardened resin cauldron entity");
        check(endpoint.handler() == source.getFluidHandler(), "pipe API returned the wrong cauldron fluid handler");
        check(!endpoint.cauldron(), "tank-backed entity was assigned whole-cauldron transfer semantics");
        check(
            PipeBlock.isFluidHandlerOrConnectablePump(helper.getLevel(), sourcePos, Direction.DOWN),
            "pipe placement lookup did not recognize the hardened resin cauldron"
        );

        helper.runAfterDelay(8, () -> {
            check(source.getFluidHandler().getFluid().isEmpty(), "pipe network did not drain the upper cauldron");
            check(
                target.getFluidHandler().getFluidAmount() == 375,
                "pipe network did not transfer the arbitrary fluid amount"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x6x7")
    @TestHolder(description = "A hardened resin cauldron stores at most one stack of each input item")
    static void hardenedResinCauldronLimitsEachInputToOneStack(ExtendedGameTestHelper helper) {
        HardenedResinCauldronEntity cauldron = createPot(
            helper,
            new Vec3(3.5D, 2.0D, 3.5D),
            PlasticEntityOrientation.DEFAULT
        );
        cauldron.setNoGravity(true);
        for (int i = 0; i < 8; i++) {
            ItemEntity bucket = new ItemEntity(
                helper.getLevel(),
                cauldron.getX(),
                cauldron.getBoundingBox().maxY,
                cauldron.getZ(),
                new ItemStack(Items.LAVA_BUCKET)
            );
            bucket.setNoGravity(true);
            bucket.anvilcraft$setIsAdsorbable(true);
            check(helper.getLevel().addFreshEntity(bucket), "failed to add a lava bucket item entity");
        }

        cauldron.tick();

        check(
            countItem(cauldron.getInput(), Items.LAVA_BUCKET) == 1,
            "the cauldron stored the same unstackable input in more than one slot"
        );
        int remainingBuckets = helper.getLevel().getEntitiesOfClass(
            ItemEntity.class,
            cauldron.getBoundingBox().inflate(2.0D),
            item -> item.isAlive() && item.getItem().is(Items.LAVA_BUCKET)
        ).stream().mapToInt(item -> item.getItem().getCount()).sum();
        check(remainingBuckets == 7, "excess lava buckets were consumed instead of remaining outside: " + remainingBuckets);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("9x6x7")
    @TestHolder(description = "Magnet forces target only magnetized plastic entities")
    static void magnetForcesModEntitiesSelectively(ExtendedGameTestHelper helper) {
        BlockPos magnetPos = new BlockPos(1, 2, 3);
        helper.setBlock(magnetPos, ModBlocks.MAGNET_BLOCK.get().defaultBlockState());
        HardenedResinAnvilEntity magnetized = createAnvil(helper, new Vec3(4.5D, 2.0D, 3.5D));
        HardenedResinAnvilEntity plain = createAnvil(helper, new Vec3(6.5D, 2.0D, 3.5D));
        magnetized.setMagnetized(true);

        Vec3 pointForce = magnetized.anvilcraft$getAdditionalGravity(0.04D);
        check(pointForce.x < -PlasticEntityPhysics.FACE_EPSILON, "magnet block did not pull the magnetized anvil");
        check(plain.anvilcraft$getAdditionalGravity(0.04D).lengthSqr() <= EPSILON * EPSILON,
            "magnet block affected an unmagnetized plastic anvil");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(2.5D, 2.0D, 3.5D)));
        player.setShiftKeyDown(false);
        ItemStack magnet = ModItems.MAGNET.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, magnet);
        Vec3 before = magnetized.getDeltaMovement();
        magnet.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        check(
            magnetized.getDeltaMovement().distanceToSqr(before) <= EPSILON * EPSILON,
            "removed handheld magnet event still changed the magnetized anvil"
        );
        magnetized.discard();
        plain.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("13x6x7")
    @TestHolder(description = "A non-magnetic plastic pot spills or loses any fluid through a side or bottom opening")
    static void plasticPotSpillsFluidByOrientation(ExtendedGameTestHelper helper) {
        HardenedResinCauldronEntity side = createPot(
            helper,
            new Vec3(2.5D, 2.0D, 2.5D),
            new PlasticEntityOrientation(Direction.EAST, 0)
        );
        side.setNoGravity(true);
        side.getFluidHandler().fill(new FluidStack(Fluids.WATER, HardenedResinCauldronEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);

        HardenedResinCauldronEntity magnetic = createPot(
            helper,
            new Vec3(7.5D, 2.0D, 2.5D),
            new PlasticEntityOrientation(Direction.EAST, 0)
        );
        magnetic.setNoGravity(true);
        magnetic.setMagnetized(true);
        magnetic.getFluidHandler().fill(new FluidStack(Fluids.WATER, HardenedResinCauldronEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);

        HardenedResinCauldronEntity partial = createPot(
            helper,
            new Vec3(11.5D, 2.0D, 2.5D),
            new PlasticEntityOrientation(Direction.DOWN, 0)
        );
        partial.setNoGravity(true);
        partial.getFluidHandler().fill(new FluidStack(Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE);

        helper.runAfterDelay(3, () -> {
            BlockPos sideTarget = BlockPos.containing(side.getBoundingBox().getCenter()).relative(Direction.EAST);
            BlockPos magneticTarget = BlockPos.containing(magnetic.getBoundingBox().getCenter()).relative(Direction.EAST);
            BlockPos partialTarget = BlockPos.containing(partial.getBoundingBox().getCenter()).relative(Direction.DOWN);
            check(helper.getLevel().getFluidState(sideTarget).isSource(), "side-facing pot did not spill a source fluid");
            check(side.getFluidHandler().getFluid().isEmpty(), "side-facing pot retained spilled fluid");
            check(
                magnetic.getFluidHandler().getFluidAmount() == HardenedResinCauldronEntity.CAPACITY,
                "magnetized pot spilled fluid despite being a sealed container"
            );
            check(helper.getLevel().getFluidState(magneticTarget).isEmpty(), "magnetized pot created an external fluid source");
            check(partial.getFluidHandler().getFluid().isEmpty(), "partially filled inverted pot retained its fluid");
            check(helper.getLevel().getFluidState(partialTarget).isEmpty(), "partial spill created a fluid block");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("15x8x7")
    @TestHolder(description = "An inverted plastic pot ejects items while side-facing and magnetic pots retain them")
    static void plasticPotEjectsItemsByOrientation(ExtendedGameTestHelper helper) {
        HardenedResinCauldronEntity inverted = createPot(
            helper,
            new Vec3(2.5D, 4.0D, 3.5D),
            new PlasticEntityOrientation(Direction.DOWN, 0)
        );
        inverted.setNoGravity(true);
        inverted.getInput().insertItem(0, new ItemStack(Items.COBBLESTONE, 2), false);
        inverted.insertRecipeOutput(new ItemStack(Items.IRON_INGOT, 3));

        HardenedResinCauldronEntity side = createPot(
            helper,
            new Vec3(7.5D, 4.0D, 3.5D),
            new PlasticEntityOrientation(Direction.EAST, 0)
        );
        side.setNoGravity(true);
        side.getInput().insertItem(0, new ItemStack(Items.DIRT, 4), false);

        HardenedResinCauldronEntity magneticInverted = createPot(
            helper,
            new Vec3(12.5D, 4.0D, 3.5D),
            new PlasticEntityOrientation(Direction.DOWN, 0)
        );
        magneticInverted.setNoGravity(true);
        magneticInverted.setMagnetized(true);
        magneticInverted.getInput().insertItem(0, new ItemStack(Items.DIAMOND, 5), false);

        check(side.shouldUseGravityAlignedItemLayout(), "side-facing pot did not select the gravity-aligned item layout");
        check(!inverted.shouldUseGravityAlignedItemLayout(), "inverted pot selected a side-wall item layout");
        check(!magneticInverted.shouldEjectStoredItems(), "magnetized inverted pot selected item ejection");

        helper.runAfterDelay(3, () -> {
            check(isEmpty(inverted.getInput()), "inverted pot retained input items");
            check(isEmpty(inverted.getOutput()), "inverted pot retained output items");
            List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                inverted.getBoundingBox().inflate(2.0D)
            );
            int cobblestone = dropped.stream()
                .filter(item -> item.getItem().is(Items.COBBLESTONE))
                .mapToInt(item -> item.getItem().getCount())
                .sum();
            int iron = dropped.stream()
                .filter(item -> item.getItem().is(Items.IRON_INGOT))
                .mapToInt(item -> item.getItem().getCount())
                .sum();
            check(cobblestone == 2 && iron == 3, "inverted pot did not drop all stored stacks below its opening");
            check(countItem(side.getInput(), Items.DIRT) == 4, "side-facing pot incorrectly ejected its items");
            check(
                countItem(magneticInverted.getInput(), Items.DIAMOND) == 5,
                "magnetized inverted pot incorrectly ejected its items"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("15x8x7")
    @TestHolder(description = "An inverted plastic pot retains blocked items and ejects them from a collision-free opening")
    static void invertedPlasticPotRespectsBlockedOpening(ExtendedGameTestHelper helper) {
        BlockPos blockedSupport = new BlockPos(3, 3, 3);
        helper.setBlock(blockedSupport, Blocks.STONE);
        HardenedResinCauldronEntity blocked = createPot(
            helper,
            new Vec3(3.99D, 4.0D, 3.5D),
            new PlasticEntityOrientation(Direction.DOWN, 0)
        );
        blocked.setNoGravity(true);
        blocked.getInput().insertItem(0, new ItemStack(Items.COBBLESTONE, 2), false);
        blocked.getFluidHandler().fill(
            new FluidStack(Fluids.WATER, HardenedResinCauldronEntity.CAPACITY),
            IFluidHandler.FluidAction.EXECUTE
        );

        BlockPos partialSupport = new BlockPos(9, 3, 3);
        helper.setBlock(partialSupport, Blocks.STONE);
        HardenedResinCauldronEntity partial = createPot(
            helper,
            new Vec3(10.01D, 4.0D, 3.5D),
            new PlasticEntityOrientation(Direction.DOWN, 0)
        );
        partial.setNoGravity(true);
        partial.getInput().insertItem(0, new ItemStack(Items.GOLD_INGOT, 3), false);

        check(!blocked.shouldEjectStoredItems(), "mostly blocked pot selected item ejection");
        check(blocked.shouldUseGravityAlignedItemLayout(), "blocked pot did not select its downward-opening item layout");
        check(partial.shouldEjectStoredItems(), "mostly open pot did not find an item ejection position");

        helper.runAfterDelay(3, () -> {
            check(countItem(blocked.getInput(), Items.COBBLESTONE) == 2, "blocked pot leaked its stored items");
            check(
                blocked.getFluidHandler().getFluidAmount() == HardenedResinCauldronEntity.CAPACITY,
                "blocked pot leaked fluid while retaining items"
            );
            check(isEmpty(partial.getInput()), "mostly open pot retained items");
            ItemEntity dropped = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                partial.getBoundingBox().inflate(2.0D),
                item -> item.getItem().is(Items.GOLD_INGOT)
            ).stream().findFirst().orElseThrow(() -> new AssertionError("mostly open pot created no dropped item"));
            AABB supportBox = new AABB(helper.absolutePos(partialSupport));
            check(!dropped.getBoundingBox().intersects(supportBox), "dropped item spawned inside the partial obstruction");
            check(
                dropped.getBoundingBox().minX >= supportBox.maxX - 1.0E-4D,
                "dropped item did not use the unobstructed side of the opening"
            );
            helper.setBlock(blockedSupport, Blocks.AIR);
        });

        helper.runAfterDelay(6, () -> {
            check(isEmpty(blocked.getInput()), "pot retained items after its obstruction was removed");
            int cobblestone = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                blocked.getBoundingBox().inflate(2.0D),
                item -> item.getItem().is(Items.COBBLESTONE)
            ).stream().mapToInt(item -> item.getItem().getCount()).sum();
            check(cobblestone == 2, "unblocked pot did not eject all retained items");
            BlockPos fluidTarget = BlockPos.containing(blocked.getBoundingBox().getCenter()).relative(Direction.DOWN);
            check(helper.getLevel().getFluidState(fluidTarget).isSource(), "unblocked pot did not spill its retained fluid");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x5x7")
    @TestHolder(description = "Plastic pot item and fluid contents survive entity persistence")
    static void plasticPotContentsSurviveSaveLoad(ExtendedGameTestHelper helper) {
        HardenedResinCauldronEntity original = createPot(helper, new Vec3(3.5D, 2.0D, 3.5D), PlasticEntityOrientation.DEFAULT);
        original.setNoGravity(true);
        original.setMagnetized(true);
        original.getInput().insertItem(0, new ItemStack(Items.COBBLESTONE, 3), false);
        original.getFluidHandler().fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE);

        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        HardenedResinCauldronEntity loaded = new HardenedResinCauldronEntity(PlasticraftEntities.HARDEND_RESIN_CAULDRON.get(), helper.getLevel());
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
    @EmptyTemplate("5x5x5")
    @TestHolder(description = "The plastic pot entity owns a full one-block collision box")
    static void plasticPotUsesFullBlockCollisionSize(ExtendedGameTestHelper helper) {
        HardenedResinCauldronEntity pot = createPot(helper, new Vec3(2.5D, 1.0D, 2.5D), PlasticEntityOrientation.DEFAULT);
        check(Math.abs(pot.getBbWidth() - 1.0F) < 1.0E-6F, "pot width is not one block");
        check(Math.abs(pot.getBbHeight() - 1.0F) < 1.0E-6F, "pot height is not one block");
        check(Math.abs(pot.getBoundingBox().getXsize() - 1.0D) < EPSILON, "pot bounding box width is not one block");
        check(Math.abs(pot.getBoundingBox().getYsize() - 1.0D) < EPSILON, "pot bounding box height is not one block");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("5x5x5")
    @TestHolder(description = "A plastic pot entity uses the same hollow collision shape as its block state")
    static void plasticPotUsesHollowCompositeCollision(ExtendedGameTestHelper helper) {
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(2.5D, 1.0D, 2.5D),
            PlasticEntityOrientation.DEFAULT
        );
        pot.setNoGravity(true);
        AABB bounds = pot.getBoundingBox();
        VoxelShape expected = HardenedResinCauldronBlock.COLLISION_SHAPE.move(
            bounds.minX,
            bounds.minY,
            bounds.minZ
        );
        check(
            !Shapes.joinIsNotEmpty(pot.plasticraft$getCollisionShape(), expected, BooleanOp.NOT_SAME),
            "pot entity collision did not exactly match its block collision"
        );

        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 center = bounds.getCenter();
        double standingY = bounds.minY + 0.25D;
        player.moveTo(center.x, bounds.maxY + 0.1D, center.z);
        player.move(MoverType.SELF, new Vec3(0.0D, -1.0D, 0.0D));
        check(Math.abs(player.getY() - standingY) < EPSILON, "player did not enter the pot and land on its floor");
        check(helper.getLevel().noCollision(player), "player could not stand inside the pot entity");

        player.moveTo(bounds.minX, standingY, center.z);
        check(!helper.getLevel().noCollision(player), "pot entity wall no longer had collision");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x5x5")
    @TestHolder(description = "Resin and hardened resin anvil entities use the royal anvil collision shape")
    static void plasticAnvilsUseRoyalAnvilCompositeCollision(ExtendedGameTestHelper helper) {
        ResinAnvilEntity resinAnvil = createResinAnvil(
            helper,
            new Vec3(2.5D, 1.0D, 2.5D),
            PlasticraftBlocks.RESIN_ANVIL.asStack()
        );
        HardenedResinAnvilEntity hardenedAnvil = createAnvil(helper, new Vec3(4.5D, 1.0D, 2.5D));
        resinAnvil.setNoGravity(true);
        hardenedAnvil.setNoGravity(true);

        assertRoyalAnvilCollisionShape(resinAnvil, "resin anvil");
        assertRoyalAnvilCollisionShape(hardenedAnvil, "hardened resin anvil");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("5x6x5")
    @TestHolder(description = "A plastic pot moves around an obstacle extending into its hollow opening")
    static void plasticPotOwnMovementUsesCompositeCollision(ExtendedGameTestHelper helper) {
        helper.setBlock(2, 2, 2, Blocks.END_ROD);
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(2.5D, 1.0D, 2.5D),
            PlasticEntityOrientation.DEFAULT
        );
        pot.setNoGravity(true);
        double startY = pot.getY();

        pot.move(MoverType.SELF, new Vec3(0.0D, 0.5D, 0.0D));

        check(Math.abs(pot.getY() - startY - 0.5D) < EPSILON, "pot outer AABB blocked movement through its opening");
        check(
            !helper.getLevel().noBlockCollision(pot, pot.getBoundingBox()),
            "test obstacle did not intersect the legacy outer AABB"
        );
        for (AABB component : pot.plasticraft$getCollisionBox().components()) {
            check(helper.getLevel().noBlockCollision(pot, component), "pot component overlapped the opening obstacle");
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 60)
    @EmptyTemplate("5x8x5")
    @TestHolder(description = "A falling plastic anvil lands with its composite collision bottom flush to the block")
    static void plasticAnvilLandsWithoutCollisionGap(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(2, 1, 2);
        helper.setBlock(support, Blocks.STONE);
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 6.0D, 2.5D));
        double supportTop = helper.absolutePos(support).getY() + 1.0D;

        helper.runAfterDelay(40, () -> {
            double collisionBottom = anvil.plasticraft$getCollisionBox().bounds().minY;
            check(
                Math.abs(collisionBottom - supportTop) <= PlasticEntityPhysics.FACE_EPSILON,
                "anvil collision bottom did not meet its support: bottom=" + collisionBottom + ", top=" + supportTop
            );
            check(
                Math.abs(anvil.getY() - supportTop) <= PlasticEntityPhysics.FACE_EPSILON,
                "anvil model origin retained a landing gap"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "A supported plastic anvil follows tangential motion but separates from a falling carrier")
    static void followsThenSeparatesFromEntityHead(ExtendedGameTestHelper helper) {
        Zombie support = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 1.0D, 2.5D));
        support.setNoGravity(true);
        Vec3 anvilPosition = helper.absoluteVec(new Vec3(2.5D, 1.0D + support.getBbHeight(), 2.5D));
        HardenedResinAnvilEntity anvil = createAnvilAbsolute(helper, anvilPosition);

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
        Vec3 anvilPosition = new Vec3(
            supportPosition.x,
            supportPosition.y - AbstractPlasticEntity.COLLISION_SIZE,
            supportPosition.z
        );
        HardenedResinAnvilEntity anvil = createAnvilAbsolute(helper, anvilPosition);

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
        helper.setBlock(pos, PlasticraftBlocks.HARDEND_RESIN_ANVIL.get());
        helper.runAfterDelay(5, () -> {
            check(!helper.getBlockState(pos).is(PlasticraftBlocks.HARDEND_RESIN_ANVIL.get()), "compatibility block did not convert");
            helper.assertEntityPresent(PlasticraftEntities.HARDEND_RESIN_ANVIL.get(), pos, 1.5D);
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
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(1.5D, 1.0D, 1.5D));
        Zombie top = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(1.5D, 1.98D, 1.5D));
        top.setNoGravity(true);
        check(!PlasticEntityPhysics.isSideContact(anvil, top), "top face was classified as a side contact");
        top.setPos(anvil.getX(), anvil.getBoundingBox().maxY - 0.005D, anvil.getZ());
        check(!PlasticEntityPhysics.isSideContact(anvil, top), "slightly embedded feet were classified as a side contact");

        double touchingCenterX = anvil.getBoundingBox().maxX + top.getBbWidth() * 0.5D;
        top.setPos(touchingCenterX, anvil.getY(), anvil.getZ());
        check(PlasticEntityPhysics.isSideContact(anvil, top), "real side contact was not detected");
        top.setPos(touchingCenterX + 0.19D, anvil.getY(), anvil.getZ());
        check(!PlasticEntityPhysics.isSideContact(anvil, top), "side push reached across an air gap");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "A supporting head moving into the anvil carries it instead of being collision-clipped")
    static void carrierNormalMovementTransfersToAnvil(ExtendedGameTestHelper helper) {
        Zombie support = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 1.0D, 3.5D));
        support.setNoGravity(true);
        Vec3 anvilPosition = helper.absoluteVec(new Vec3(3.5D, 1.0D + support.getBbHeight(), 3.5D));
        HardenedResinAnvilEntity anvil = createAnvilAbsolute(helper, anvilPosition);

        helper.runAfterDelay(3, () -> {
            double supportStartY = support.getY();
            double anvilStartY = anvil.getY();
            support.move(MoverType.SELF, new Vec3(0.0D, 0.25D, 0.0D));
            check(support.getY() > supportStartY + 0.2D, "carrier was clipped by the supported anvil");
            check(anvil.getY() > anvilStartY + 0.2D, "normal carrier movement was not transferred to the anvil");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "A player can jump and step up while carrying a resin anvil on their head")
    static void playerJumpsAndStepsWithResinAnvil(ExtendedGameTestHelper helper) {
        assertPlayerJumpsAndStepsWithHeadProduct(
            helper,
            "resin anvil",
            position -> createResinAnvil(helper, position, PlasticraftBlocks.RESIN_ANVIL.asStack())
        );
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "A player can jump and step up while carrying a hardened resin anvil on their head")
    static void playerJumpsAndStepsWithHardenedResinAnvil(ExtendedGameTestHelper helper) {
        assertPlayerJumpsAndStepsWithHeadProduct(
            helper,
            "hardened resin anvil",
            position -> createAnvil(helper, position)
        );
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "A player can jump and step up while carrying a hardened resin cauldron on their head")
    static void playerJumpsAndStepsWithHardenedResinCauldron(ExtendedGameTestHelper helper) {
        assertPlayerJumpsAndStepsWithHeadProduct(
            helper,
            "hardened resin cauldron",
            position -> createPot(helper, position, PlasticEntityOrientation.DEFAULT)
        );
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "A carrier cannot pass through its anvil when the anvil is blocked by a solid ceiling")
    static void blockedCarrierRemainsBelowAnvil(ExtendedGameTestHelper helper) {
        helper.setBlock(3, 4, 3, Blocks.STONE);
        Zombie support = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 1.0D, 3.5D));
        support.setNoGravity(true);
        HardenedResinAnvilEntity anvil = createAnvilAbsolute(
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
    @TestHolder(description = "Carrier step-up cannot overlap a ceiling-blocked anvil collision shape")
    static void blockedStepUpDoesNotOverlapAnvil(ExtendedGameTestHelper helper) {
        helper.setBlock(3, 1, 3, Blocks.STONE_SLAB);
        helper.setBlock(3, 1, 5, Blocks.STONE_SLAB);
        helper.setBlock(3, 4, 3, Blocks.STONE);
        Zombie baseline = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 1.0D, 5.5D));
        baseline.setNoGravity(true);
        Zombie support = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 1.0D, 3.5D));
        support.setNoGravity(true);
        HardenedResinAnvilEntity anvil = createAnvilAbsolute(
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
                "ceiling-blocked carrier was allowed to step through its anvil shape: start=" + supportStart
                    + ", end=" + support.position() + ", supportBox=" + support.getBoundingBox()
                    + ", anvilBox=" + anvil.getBoundingBox()
            );
            check(
                !Shapes.joinIsNotEmpty(
                    Shapes.create(support.getBoundingBox()),
                    anvil.plasticraft$getCollisionShape(),
                    BooleanOp.AND
                ),
                "step-up carrier overlapped the ceiling-blocked anvil shape: carrier=" + support.getBoundingBox()
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
        HardenedResinAnvilEntity boatAnvil = createAnvilAbsolute(
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
        HardenedResinAnvilEntity minecartAnvil = createAnvilAbsolute(
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
        HardenedResinAnvilEntity anvil = createAnvilAbsolute(helper, anvilPosition);

        helper.runAfterDelay(2, () -> anvil.setNoGravity(true));
        helper.runAfterDelay(4, () -> {
            double startX = anvil.getX();
            support.move(MoverType.SELF, new Vec3(0.3D, 0.0D, 0.0D));
            check(Math.abs(anvil.getX() - startX) < 0.05D, "no-gravity anvil retained a stale carrier");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate
    @TestHolder(description = "The effective gravity face is never classified as a lateral soft-push face")
    static void sixAxisSupportFacesAreNotSideContacts(ExtendedGameTestHelper helper) {
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(1.5D, 1.0D, 1.5D));
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
                !PlasticEntityPhysics.isSideContact(anvil, other, gravity),
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
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 4.0D, 2.5D));
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
        HardenedResinAnvilEntity ordinary = createAnvil(helper, new Vec3(3.5D, 2.0D, 3.5D));
        HardenedResinAnvilEntity ice = createAnvil(helper, new Vec3(10.5D, 2.0D, 3.5D));
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
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(5.5D, 2.0D, 5.5D));
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        AABB bounds = anvil.plasticraft$getCollisionBox().bounds();
        player.moveTo(
            bounds.minX - player.getBbWidth() * 0.5D,
            bounds.minY,
            bounds.getCenter().z
        );

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
    @TestHolder(description = "Releasing a side push does not leave a plastic body drifting")
    static void releasedPlayerSidePushDoesNotDriftPlasticEntity(ExtendedGameTestHelper helper) {
        for (int x = 2; x <= 8; x++) {
            for (int z = 3; z <= 7; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(5.5D, 2.0D, 5.5D));
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3[] releasedPosition = {Vec3.ZERO};
        Vec3[] releaseVelocity = {Vec3.ZERO};

        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                AABB bounds = anvil.plasticraft$getCollisionBox().bounds();
                player.moveTo(
                    bounds.minX - player.getBbWidth() * 0.5D,
                    bounds.minY,
                    bounds.getCenter().z
                );
                player.setOnGround(true);
                anvil.setDeltaMovement(Vec3.ZERO);
                double startX = anvil.getX();
                for (int step = 0; step < 4; step++) {
                    player.move(MoverType.SELF, new Vec3(0.12D, 0.0D, 0.0D));
                }
                check(anvil.getX() - startX > 0.4D, "the test player did not side-push the anvil");
                releasedPosition[0] = anvil.position();
                releaseVelocity[0] = anvil.getDeltaMovement();
                Vec3 retreat = helper.absoluteVec(new Vec3(2.5D, 2.0D, 5.5D));
                player.moveTo(retreat.x, retreat.y, retreat.z);
                player.setDeltaMovement(Vec3.ZERO);
            })
            .thenIdle(6)
            .thenExecute(() -> {
                check(
                    releaseVelocity[0].horizontalDistanceSqr() < EPSILON * EPSILON,
                    "releasing the side push left horizontal velocity: " + releaseVelocity[0]
                );
                check(
                    anvil.position().subtract(releasedPosition[0]).horizontalDistanceSqr() < 1.0E-6D,
                    "the anvil drifted after the side push was released: "
                        + anvil.position().subtract(releasedPosition[0])
                );
                check(
                    anvil.getDeltaMovement().horizontalDistanceSqr() < EPSILON * EPSILON,
                    "the released anvil retained horizontal velocity: " + anvil.getDeltaMovement()
                );
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("11x6x11")
    @TestHolder(description = "A mob side push transfers movement without adding a second vanilla collision impulse")
    static void mobSidePushDoesNotAddVanillaImpulse(ExtendedGameTestHelper helper) {
        for (int x = 2; x <= 8; x++) {
            for (int z = 3; z <= 7; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(5.5D, 2.0D, 5.5D));
        anvil.setNoGravity(true);
        Zombie pusher = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 5.5D));
        pusher.setNoGravity(true);

        helper.runAfterDelay(3, () -> {
            AABB bounds = anvil.plasticraft$getCollisionBox().bounds();
            pusher.moveTo(
                bounds.minX - pusher.getBbWidth() * 0.5D,
                bounds.minY,
                bounds.getCenter().z
            );
            anvil.setDeltaMovement(Vec3.ZERO);
            pusher.setDeltaMovement(Vec3.ZERO);
            double anvilStartX = anvil.getX();
            double pusherStartX = pusher.getX();

            pusher.move(MoverType.SELF, new Vec3(0.16D, 0.0D, 0.0D));

            check(pusher.getX() - pusherStartX > 0.14D, "plastic body clipped the mob's carrier movement");
            check(anvil.getX() - anvilStartX > 0.14D, "mob carrier movement was not transferred to the plastic body");
            PlasticEntityPhysics.pushSideEntities(anvil, Direction.DOWN, null);
            check(
                anvil.getDeltaMovement().horizontalDistanceSqr() < EPSILON * EPSILON,
                "mob carrier push added horizontal velocity to the plastic body: " + anvil.getDeltaMovement()
            );
            check(
                pusher.getDeltaMovement().horizontalDistanceSqr() < EPSILON * EPSILON,
                "mob carrier push added horizontal velocity to the mob: " + pusher.getDeltaMovement()
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate("15x6x15")
    @TestHolder(description = "Composite plastic bodies remain level during continuous pushes from every horizontal axis")
    static void compositePlasticBodiesPushSmoothly(ExtendedGameTestHelper helper) {
        for (int x = 2; x <= 12; x++) {
            for (int z = 2; z <= 12; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        HardenedResinAnvilEntity xAnvil = createAnvil(helper, new Vec3(4.5D, 2.0D, 4.5D));
        HardenedResinAnvilEntity zAnvil = createAnvil(helper, new Vec3(10.5D, 2.0D, 4.5D));
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(4.5D, 2.0D, 10.5D),
            PlasticEntityOrientation.DEFAULT
        );
        GameTestPlayer xPlayer = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        GameTestPlayer zPlayer = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        GameTestPlayer potPlayer = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        double[] standingY = new double[3];
        double[] starts = new double[3];

        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                AABB xBounds = xAnvil.plasticraft$getCollisionBox().bounds();
                AABB zBounds = zAnvil.plasticraft$getCollisionBox().bounds();
                AABB potBounds = pot.plasticraft$getCollisionBox().bounds();
                xPlayer.moveTo(
                    xBounds.minX - xPlayer.getBbWidth() * 0.5D,
                    xBounds.minY,
                    xBounds.getCenter().z
                );
                zPlayer.moveTo(
                    zBounds.getCenter().x,
                    zBounds.minY,
                    zBounds.minZ - zPlayer.getBbWidth() * 0.5D
                );
                potPlayer.moveTo(
                    potBounds.minX - potPlayer.getBbWidth() * 0.5D,
                    potBounds.minY,
                    potBounds.getCenter().z
                );
                standingY[0] = xPlayer.getY();
                standingY[1] = zPlayer.getY();
                standingY[2] = potPlayer.getY();
                starts[0] = xAnvil.getX();
                starts[1] = zAnvil.getZ();
                starts[2] = pot.getX();
            })
            .thenExecuteFor(8, () -> {
                assertLevelPush(xPlayer, xAnvil, new Vec3(0.12D, -0.08D, 0.0D), standingY[0], Direction.Axis.X);
                assertLevelPush(zPlayer, zAnvil, new Vec3(0.0D, -0.08D, 0.12D), standingY[1], Direction.Axis.Z);
                assertLevelPush(potPlayer, pot, new Vec3(0.12D, -0.08D, 0.0D), standingY[2], Direction.Axis.X);
            })
            .thenExecute(() -> {
                check(xAnvil.getX() - starts[0] > 0.6D, "x-axis anvil push did not remain continuous");
                check(zAnvil.getZ() - starts[1] > 0.6D, "z-axis anvil push did not remain continuous");
                check(pot.getX() - starts[2] > 0.6D, "cauldron push did not remain continuous");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("11x6x9")
    @TestHolder(description = "An outside player can push a cauldron without entering its collision shape")
    static void outsidePlayerDoesNotStepIntoPushedPlasticPot(ExtendedGameTestHelper helper) {
        for (int x = 2; x <= 8; x++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(4.5D, 2.0D, 4.5D),
            PlasticEntityOrientation.DEFAULT
        );
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);

        helper.runAfterDelay(3, () -> {
            AABB bounds = pot.plasticraft$getCollisionBox().bounds();
            player.moveTo(
                bounds.minX - player.getBbWidth() * 0.5D,
                bounds.minY,
                bounds.getCenter().z
            );
            player.setOnGround(true);
            double floorY = player.getY();
            double potStartX = pot.getX();
            for (int step = 0; step < 8; step++) {
                double playerStartX = player.getX();
                double potStepStartX = pot.getX();
                Vec3 requestedMovement = new Vec3(0.12D, -0.08D, 0.0D);
                player.move(MoverType.SELF, requestedMovement);
                double playerMovement = player.getX() - playerStartX;
                double potMovement = pot.getX() - potStepStartX;
                check(playerMovement <= 0.1201D, "cauldron contact accelerated the player at step " + step);
                check(potMovement > 0.10D, "slight wall penetration stalled the cauldron at step " + step);
                check(Math.abs(player.getY() - floorY) < EPSILON, "player stepped onto the cauldron floor at step " + step);
                check(
                    !Shapes.joinIsNotEmpty(
                        Shapes.create(player.getBoundingBox()),
                        pot.plasticraft$getCollisionShape(),
                        BooleanOp.AND
                    ),
                    "player entered the cauldron collision shape at step " + step
                );
            }
            check(pot.getX() - potStartX > 0.8D, "the cauldron push did not remain continuous");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("11x6x9")
    @TestHolder(description = "A wall-blocked cauldron never lets an outside player step into its bottom")
    static void wallBlockedPlasticPotDoesNotStepPlayerIntoBottom(ExtendedGameTestHelper helper) {
        for (int x = 2; x <= 8; x++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        BlockPos wallPos = new BlockPos(7, 2, 4);
        helper.setBlock(wallPos, Blocks.STONE);
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(6.4D, 2.0D, 4.5D),
            PlasticEntityOrientation.DEFAULT
        );
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);

        helper.runAfterDelay(3, () -> {
            AABB bounds = pot.plasticraft$getCollisionBox().bounds();
            player.moveTo(
                bounds.minX - player.getBbWidth() * 0.5D,
                bounds.minY,
                bounds.getCenter().z
            );
            player.setOnGround(true);
            double floorY = player.getY();
            double wallMinX = helper.absolutePos(wallPos).getX();
            for (int step = 0; step < 5; step++) {
                player.move(MoverType.SELF, new Vec3(0.12D, -0.08D, 0.0D));
                check(
                    Math.abs(player.getY() - floorY) < EPSILON,
                    "wall-blocked cauldron stepped the player into its bottom at step " + step
                );
                check(
                    pot.plasticraft$getCollisionBox().bounds().maxX <= wallMinX + PlasticEntityPhysics.FACE_EPSILON,
                    "wall-blocked cauldron crossed the wall at step " + step
                );
                check(
                    !Shapes.joinIsNotEmpty(
                        Shapes.create(player.getBoundingBox()),
                        pot.plasticraft$getCollisionShape(),
                        BooleanOp.AND
                    ),
                    "wall-blocked cauldron let the player enter its collision shape at step " + step
                );
            }
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "9x6x9", floor = true)
    @TestHolder(description = "A player standing in a plastic pot cannot push it from the inner wall")
    static void playerInsidePlasticPotCannotPushIt(ExtendedGameTestHelper helper) {
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(4.5D, 1.0D, 4.5D),
            PlasticEntityOrientation.DEFAULT
        );
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);

        helper.runAfterDelay(3, () -> {
            AABB bounds = pot.plasticraft$getCollisionBox().bounds();
            Vec3 center = bounds.getCenter();
            player.moveTo(center.x, bounds.minY + 0.25D, center.z);
            player.setOnGround(true);
            Vec3 potStart = pot.position();
            double playerStartY = player.getY();
            for (int i = 0; i < 16; i++) {
                player.move(MoverType.SELF, new Vec3(0.12D, -0.08D, 0.04D));
            }
            check(
                pot.position().distanceToSqr(potStart) < EPSILON * EPSILON,
                "pushing the inner cauldron wall moved the cauldron: " + pot.position().subtract(potStart)
            );
            check(Math.abs(player.getY() - playerStartY) < EPSILON, "the inner cauldron wall stepped the player upward");
            check(player.verticalCollisionBelow, "the cauldron floor stopped supporting the player");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("9x6x9")
    @TestHolder(description = "A player walking from a plastic pot rim onto its top does not push the pot")
    static void playerWalksAcrossPlasticPotRimWithoutPushingIt(ExtendedGameTestHelper helper) {
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(4.5D, 2.0D, 4.5D),
            PlasticEntityOrientation.DEFAULT
        );
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);

        helper.runAfterDelay(3, () -> {
            AABB bounds = pot.plasticraft$getCollisionBox().bounds();
            double rimOverlap = 0.08D;
            player.moveTo(
                bounds.maxX + player.getBbWidth() * 0.5D - rimOverlap,
                bounds.maxY,
                bounds.getCenter().z
            );
            player.setOnGround(true);
            check(
                PlasticEntityPhysics.hasSurfaceSupport(
                    player,
                    player.getBoundingBox(),
                    pot,
                    Direction.DOWN
                ),
                "the test player was not standing on the cauldron rim"
            );
            Vec3 potStart = pot.position();
            Vec3 playerStart = player.position();

            player.move(MoverType.SELF, new Vec3(-0.18D, -0.08D, 0.0D));

            check(
                pot.position().distanceToSqr(potStart) < EPSILON * EPSILON,
                "walking across the cauldron rim pushed the cauldron: " + pot.position().subtract(potStart)
            );
            check(player.getX() < playerStart.x - 0.12D, "the cauldron rim blocked movement across its top surface");
            check(Math.abs(player.getY() - playerStart.y) < EPSILON, "the cauldron rim changed the player's height");
            check(player.getPose() == Pose.STANDING, "the cauldron rim changed the player pose to " + player.getPose());
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 70)
    @EmptyTemplate("11x8x11")
    @TestHolder(description = "A pushed plastic pot carries the plastic anvil resting in its opening")
    static void pushedPlasticPotCarriesContainedAnvil(ExtendedGameTestHelper helper) {
        for (int x = 2; x <= 8; x++) {
            for (int z = 3; z <= 7; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(5.5D, 2.0D, 5.5D),
            PlasticEntityOrientation.DEFAULT
        );
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(5.5D, 5.5D, 5.5D));
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);

        helper.runAfterDelay(35, () -> {
            check(
                PlasticEntityPhysics.hasImmediateEntityContact(anvil, pot, Direction.DOWN),
                "the fallen anvil was not resting on the cauldron collision shape"
            );
            AABB potBounds = pot.plasticraft$getCollisionBox().bounds();
            player.moveTo(
                potBounds.minX - player.getBbWidth() * 0.5D,
                potBounds.minY,
                potBounds.getCenter().z
            );
            player.setOnGround(true);
            Vec3 potStart = pot.position();
            Vec3 anvilStart = anvil.position();
            for (int i = 0; i < 4; i++) {
                player.move(MoverType.SELF, new Vec3(0.12D, -0.08D, 0.0D));
            }
            double potMovement = pot.getX() - potStart.x;
            double anvilMovement = anvil.getX() - anvilStart.x;
            check(potMovement > 0.4D, "the contained anvil blocked the cauldron push: " + potMovement);
            check(anvilMovement > 0.4D, "the contained anvil did not move with the cauldron: " + anvilMovement);
            check(
                Math.abs(potMovement - anvilMovement) < 0.02D,
                "the cauldron and contained anvil separated while pushed: pot=" + potMovement
                    + ", anvil=" + anvilMovement
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 70)
    @EmptyTemplate("11x8x11")
    @TestHolder(description = "A plastic pot carries its contained anvil when pushed toward the anvil's short face")
    static void pushedPlasticPotCarriesContainedAnvilAlongShortAxis(ExtendedGameTestHelper helper) {
        for (int x = 3; x <= 7; x++) {
            for (int z = 2; z <= 8; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(5.5D, 2.0D, 5.5D),
            PlasticEntityOrientation.DEFAULT
        );
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(5.5D, 5.5D, 5.5D));
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);

        helper.runAfterDelay(35, () -> {
            check(
                PlasticEntityPhysics.hasImmediateEntityContact(anvil, pot, Direction.DOWN),
                "the fallen anvil was not resting on the cauldron collision shape"
            );
            AABB potBounds = pot.plasticraft$getCollisionBox().bounds();
            player.moveTo(
                potBounds.getCenter().x,
                potBounds.minY,
                potBounds.minZ - player.getBbWidth() * 0.5D
            );
            player.setOnGround(true);
            Vec3 potStart = pot.position();
            Vec3 anvilStart = anvil.position();
            for (int i = 0; i < 4; i++) {
                player.move(MoverType.SELF, new Vec3(0.0D, -0.08D, 0.12D));
            }
            double potMovement = pot.getZ() - potStart.z;
            double anvilMovement = anvil.getZ() - anvilStart.z;
            check(potMovement > 0.4D, "the short-axis anvil contact blocked the cauldron push: " + potMovement);
            check(anvilMovement > 0.4D, "the contained anvil did not follow the short-axis push: " + anvilMovement);
            check(
                Math.abs(potMovement - anvilMovement) < 0.02D,
                "the cauldron and contained anvil separated on the short axis: pot=" + potMovement
                    + ", anvil=" + anvilMovement
            );
            check(player.getPose() == Pose.STANDING, "short-axis pot push changed the player pose to " + player.getPose());
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 70)
    @EmptyTemplate("11x8x11")
    @TestHolder(description = "A plastic carrier is clipped when its contained anvil is blocked by a wall")
    static void plasticCarrierIsClippedWhenContainedAnvilHitsWall(ExtendedGameTestHelper helper) {
        for (int x = 2; x <= 8; x++) {
            for (int z = 3; z <= 7; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        BlockPos wallPos = new BlockPos(7, 3, 5);
        helper.setBlock(wallPos, Blocks.STONE);
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(6.3D, 2.0D, 5.5D),
            PlasticEntityOrientation.DEFAULT
        );
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(6.3D, 5.5D, 5.5D));
        Vec3 requestedMovement = new Vec3(0.6D, 0.0D, 0.0D);

        helper.runAfterDelay(35, () -> {
            check(
                PlasticEntityPhysics.hasImmediateEntityContact(anvil, pot, Direction.DOWN),
                "the fallen anvil was not resting on the carrier"
            );
            VoxelShape wallShape = Shapes.create(new AABB(helper.absolutePos(wallPos)));
            check(
                !Shapes.joinIsNotEmpty(pot.plasticraft$getCollisionShape(), wallShape, BooleanOp.AND),
                "the test wall already blocks the lower carrier"
            );
            check(
                !Shapes.joinIsNotEmpty(anvil.plasticraft$getCollisionShape(), wallShape, BooleanOp.AND),
                "the test wall already overlaps the carried anvil"
            );
            check(
                Shapes.joinIsNotEmpty(
                    anvil.plasticraft$getCollisionShape().move(
                        requestedMovement.x,
                        requestedMovement.y,
                        requestedMovement.z
                    ),
                    wallShape,
                    BooleanOp.AND
                ),
                "the test wall does not obstruct the requested carried-anvil movement"
            );

            pot.setDeltaMovement(Vec3.ZERO);
            anvil.setDeltaMovement(Vec3.ZERO);
            Vec3 potStart = pot.position();
            Vec3 anvilStart = anvil.position();
            pot.move(MoverType.SELF, requestedMovement);
            double potMovement = pot.getX() - potStart.x;
            double anvilMovement = anvil.getX() - anvilStart.x;

            check(
                potMovement < requestedMovement.x - 0.01D,
                "the lower carrier ignored the wall-blocked anvil: carrier=" + potMovement
                    + ", carried=" + anvilMovement
            );
            check(
                Math.abs(potMovement - anvilMovement) < 0.02D,
                "the wall-blocked anvil separated from its carrier: carrier=" + potMovement
                    + ", carried=" + anvilMovement
            );
            check(
                !Shapes.joinIsNotEmpty(anvil.plasticraft$getCollisionShape(), wallShape, BooleanOp.AND),
                "the carried anvil crossed the wall"
            );
            check(
                !Shapes.joinIsNotEmpty(
                    pot.plasticraft$getCollisionShape(),
                    anvil.plasticraft$getCollisionShape(),
                    BooleanOp.AND
                ),
                "the clipped carrier overlapped its blocked anvil"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("11x6x11")
    @TestHolder(description = "A player push is clipped to place a plastic body flush against a wall")
    static void playerPushStopsPlasticEntityAtWall(ExtendedGameTestHelper helper) {
        for (int x = 2; x <= 8; x++) {
            helper.setBlock(x, 1, 5, Blocks.STONE);
        }
        BlockPos wallPos = new BlockPos(7, 2, 5);
        helper.setBlock(wallPos, Blocks.STONE);
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(5.5D, 2.0D, 5.5D),
            PlasticEntityOrientation.DEFAULT
        );
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.69D, 2.0D, 5.5D));
        player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z);

        helper.runAfterDelay(3, () -> {
            double playerStart = player.getX();
            player.move(MoverType.SELF, new Vec3(0.7D, 0.0D, 0.0D));
            player.move(MoverType.SELF, new Vec3(0.7D, 0.0D, 0.0D));

            double wallMinX = helper.absolutePos(wallPos).getX();
            double wallGap = wallMinX - pot.getBoundingBox().maxX;
            check(wallGap >= -PlasticEntityPhysics.FACE_EPSILON, "pushed pot overlapped the wall: gap=" + wallGap);
            check(wallGap <= 1.0E-3D, "pushed pot stopped before reaching the wall: gap=" + wallGap);
            check(player.getX() - playerStart > 0.9D, "player was stopped before moving the pot to the wall");
            check(
                player.getBoundingBox().maxX <= pot.getBoundingBox().minX + 1.0E-3D,
                "player overlapped the wall-blocked pot"
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
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(5.5D, 2.0D, 5.5D));
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        AABB bounds = anvil.plasticraft$getCollisionBox().bounds();
        player.moveTo(
            bounds.minX - player.getBbWidth() * 0.5D,
            bounds.minY,
            bounds.getCenter().z
        );

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
    @EmptyTemplate("11x6x11")
    @TestHolder(description = "Diagonal walking pushes a plastic pot smoothly along only the contacted face normal")
    static void diagonalPlayerMovementPushesPotWithoutSidewaysDragging(ExtendedGameTestHelper helper) {
        for (int x = 2; x <= 8; x++) {
            for (int z = 2; z <= 8; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        }
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(5.5D, 2.0D, 5.5D),
            PlasticEntityOrientation.DEFAULT
        );
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        AABB bounds = pot.plasticraft$getCollisionBox().bounds();
        player.moveTo(
            bounds.minX - player.getBbWidth() * 0.5D,
            bounds.minY,
            bounds.getCenter().z
        );

        helper.runAfterDelay(3, () -> {
            Vec3 potStart = pot.position();
            Vec3 playerStart = player.position();
            double previousPotX = pot.getX();
            for (int step = 0; step < 6; step++) {
                player.move(MoverType.SELF, new Vec3(0.12D, 0.0D, 0.09D));
                double stepMovement = pot.getX() - previousPotX;
                check(stepMovement > 0.08D, "plastic pot push stalled at step " + step);
                previousPotX = pot.getX();
            }
            Vec3 potMovement = pot.position().subtract(potStart);
            Vec3 playerMovement = player.position().subtract(playerStart);
            check(potMovement.x > 0.6D, "diagonal walking did not push the pot continuously");
            check(Math.abs(potMovement.z) < 0.03D, "plastic pot followed the player's lateral movement");
            check(playerMovement.z > 0.45D, "plastic pot blocked tangential player movement");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate("15x6x11")
    @TestHolder(description = "A player can push a touching plastic chain again after it stops")
    static void playerPushesPlasticEntityChain(ExtendedGameTestHelper helper) {
        for (int x = 2; x <= 12; x++) {
            for (int z = 4; z <= 6; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        HardenedResinAnvilEntity rear = createAnvil(helper, new Vec3(5.5D, 2.0D, 5.5D));
        HardenedResinCauldronEntity front = createPot(
            helper,
            new Vec3(6.5D, 2.0D, 5.5D),
            PlasticEntityOrientation.DEFAULT
        );
        AABB rearBounds = rear.plasticraft$getCollisionBox().bounds();
        AABB frontBounds = front.plasticraft$getCollisionBox().bounds();
        front.setPos(
            rearBounds.maxX + frontBounds.getXsize() * 0.5D,
            front.getY(),
            rear.getZ()
        );
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.moveTo(
            rearBounds.minX - player.getBbWidth() * 0.5D,
            rearBounds.minY,
            rearBounds.getCenter().z
        );

        double[] stoppedRearX = {0.0D};
        double[] stoppedFrontX = {0.0D};
        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                double rearStart = rear.getX();
                double frontStart = front.getX();
                player.setOnGround(true);
                player.move(MoverType.SELF, new Vec3(0.18D, 0.0D, 0.0D));
                player.move(MoverType.SELF, new Vec3(0.18D, 0.0D, 0.0D));
                check(rear.getX() - rearStart > 0.30D, "rear plastic body did not receive the first push");
                check(front.getX() - frontStart > 0.30D, "front plastic body did not receive the first push");
                stoppedRearX[0] = rear.getX();
                stoppedFrontX[0] = front.getX();
                rear.setDeltaMovement(Vec3.ZERO);
                front.setDeltaMovement(Vec3.ZERO);
            })
            .thenIdle(5)
            .thenExecute(() -> {
                check(Math.abs(rear.getX() - stoppedRearX[0]) < EPSILON, "rear plastic body drifted while stopped");
                check(Math.abs(front.getX() - stoppedFrontX[0]) < EPSILON, "front plastic body drifted while stopped");
                double playerStart = player.getX();
                double rearStart = rear.getX();
                double frontStart = front.getX();
                player.setOnGround(true);
                player.move(MoverType.SELF, new Vec3(0.18D, 0.0D, 0.0D));
                player.move(MoverType.SELF, new Vec3(0.18D, 0.0D, 0.0D));
                double playerMovement = player.getX() - playerStart;
                double rearMovement = rear.getX() - rearStart;
                double frontMovement = front.getX() - frontStart;
                check(playerMovement > 0.30D, "stopped plastic chain blocked the player");
                check(rearMovement > 0.30D, "rear plastic body did not move after stopping");
                check(frontMovement > 0.30D, "front plastic body did not move after stopping");
                check(
                    Math.abs(rearMovement - frontMovement) < 0.03D,
                    "stopped plastic bodies did not preserve their spacing: player=" + playerMovement
                        + ", rear=" + rearMovement + ", front=" + frontMovement
                );
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("15x7x9")
    @TestHolder(description = "Sneak-use treats every plastic entity face as a solid placement surface")
    static void shiftUsePlacesBlocksOnPlasticEntityFaces(ExtendedGameTestHelper helper) {
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(3.5D, 2.0D, 3.5D));
        HardenedResinCauldronEntity pot = createPot(helper, new Vec3(8.5D, 2.0D, 3.5D), PlasticEntityOrientation.DEFAULT);
        anvil.setNoGravity(true);
        pot.setNoGravity(true);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);

        ItemStack stone = new ItemStack(Items.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stone);
        InteractionResult blockResult = anvil.interactAt(
            player,
            eastFaceHit(anvil),
            InteractionHand.MAIN_HAND
        );
        check(blockResult.consumesAction(), "sneak-use did not place the held vanilla block");
        check(helper.getBlockState(new BlockPos(4, 2, 3)).is(Blocks.STONE), "vanilla block was not placed on the entity's east face");

        ItemStack plastic = PlasticraftBlocks.HARDEND_RESIN_ANVIL.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, plastic);
        InteractionResult entityResult = pot.interactAt(
            player,
            eastFaceHit(pot),
            InteractionHand.MAIN_HAND
        );
        check(entityResult.consumesAction(), "sneak-use did not place the held plastic entity item");
        AABB targetCell = new AABB(helper.absolutePos(new BlockPos(9, 2, 3)));
        check(
            helper.getLevel().getEntitiesOfClass(HardenedResinAnvilEntity.class, targetCell.inflate(0.05D)).size() == 1,
            "plastic entity was not placed on the pot's east face"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "A sneak-placed falling block sounds and stays above an incomplete plastic entity")
    static void shiftPlacedAnvilStaysOnPlasticEntity(ExtendedGameTestHelper helper) {
        HardenedResinAnvilEntity support = createAnvil(helper, new Vec3(3.5D, 1.0D, 3.5D));
        support.setNoGravity(true);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, Items.ANVIL.getDefaultInstance());
        AtomicInteger placementSounds = new AtomicInteger();
        ResourceLocation placementSound = Blocks.ANVIL.defaultBlockState()
            .getSoundType()
            .getPlaceSound()
            .getLocation();
        helper.addTemporaryListener((PlayLevelSoundEvent.AtPosition event) -> {
            if (event.getLevel() == helper.getLevel()
                && event.getSound() != null
                && event.getSound().value().getLocation().equals(placementSound)) {
                placementSounds.incrementAndGet();
            }
        });

        InteractionResult result = support.interactAt(
            player,
            new Vec3(0.0D, support.getBbHeight(), 0.0D),
            InteractionHand.MAIN_HAND
        );
        BlockPos placedPos = new BlockPos(3, 2, 3);
        check(result.consumesAction(), "sneak-use did not place the falling anvil");
        check(helper.getBlockState(placedPos).is(Blocks.ANVIL), "falling anvil was not placed above the plastic entity");
        check(placementSounds.get() == 1, "entity-face placement played " + placementSounds.get() + " placement sounds");

        helper.runAfterDelay(8, () -> {
            check(helper.getBlockState(placedPos).is(Blocks.ANVIL), "directly placed anvil started falling");
            boolean falling = !helper.getLevel().getEntitiesOfClass(
                FallingBlockEntity.class,
                new AABB(helper.absolutePos(placedPos)).inflate(2.0D),
                entity -> entity.getBlockState().is(Blocks.ANVIL)
            ).isEmpty();
            check(!falling, "directly placed anvil converted into a falling entity");
            helper.succeed();
        });
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
            PlasticraftEntities.HARDEND_RESIN_ANVIL.get(),
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
                anvil.getY() < startY - 1.0D && PlasticEntityPhysics.hasBlockSupport(anvil, Direction.DOWN),
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
                    + ", floorSupport=" + PlasticEntityPhysics.hasBlockSupport(anvil, Direction.DOWN)
            );
            check(fallDistance[0] > 1.0F, "acceleration correction was absent from directional fall distance");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 50)
    @EmptyTemplate(value = "5x8x5", floor = true)
    @TestHolder(description = "AnvilCraft receives exactly one landing-edge event with accumulated fall distance")
    static void landingEventFiresOnce(ExtendedGameTestHelper helper) {
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 5.0D, 2.5D));
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
            new Vec3(
                3.5D - AbstractPlasticEntity.COLLISION_SIZE * 0.5D - EntityType.ZOMBIE.getWidth() * 0.5D,
                1.0D,
                3.5D
            )
        );
        support.setNoGravity(true);
        HardenedResinAnvilEntity anvil = createAnvilAbsolute(helper, anvilPosition);
        Vec3 gravity = GravityManager.getNetGravityVectorForFallingBlock(anvil);
        check(Direction.getNearest(gravity) == Direction.WEST, "test source did not create westward gravity");

        helper.runAfterDelay(3, () -> {
            EntityType<?> supportType = support.getType();
            check(
                PlasticEntityPhysics.findSupport(anvil, Direction.WEST) == support,
                "westward gravity did not acquire the entity support face: anvil=" + anvil.getBoundingBox()
                    + ", support=" + support.getBoundingBox()
                    + ", candidate=" + PlasticEntityPhysics.isSupportCandidate(anvil, support, Direction.WEST)
                    + ", collidable=" + anvil.canCollideWith(support)
                    + ", supportType=" + supportType
                    + ", gravity=" + GravityManager.getNetGravityVectorForFallingBlock(anvil)
            );
            double supportStartX = support.getX();
            double anvilStartX = anvil.getX();
            support.move(MoverType.SELF, new Vec3(0.2D, 0.0D, 0.0D));
            check(support.getX() > supportStartX + 0.15D, "horizontal carrier was clipped by the supported anvil");
            check(anvil.getX() > anvilStartX + 0.15D, "horizontal carrier movement did not transfer to the anvil");
            helper.succeed();
        });
    }

    private static CraftingInput resinAnvilCraftingInput(ItemStack center) {
        ItemStack resinBlock = new ItemStack(ModBlocks.RESIN_BLOCK.get());
        ItemStack resin = ModItems.RESIN.asStack();
        return CraftingInput.of(3, 3, List.of(
            resinBlock.copy(), resinBlock.copy(), resinBlock.copy(),
            ItemStack.EMPTY, center, ItemStack.EMPTY,
            resin.copy(), resin.copy(), resin.copy()
        ));
    }

    private static CraftingInput hardenedResinCauldronCraftingInput(ItemStack topCorner) {
        ItemStack hardenedResin = ModItems.HARDEND_RESIN.asStack();
        return CraftingInput.of(3, 3, List.of(
            topCorner.copy(), ItemStack.EMPTY, topCorner.copy(),
            hardenedResin.copy(), ItemStack.EMPTY, hardenedResin.copy(),
            hardenedResin.copy(), hardenedResin.copy(), hardenedResin.copy()
        ));
    }

    private static FastCookingRecipe fastCookingRecipe(ExtendedGameTestHelper helper, String path) {
        RecipeHolder<?> holder = helper.getLevel().getRecipeManager()
            .byKey(AnvilcraftPlasticraft.of(path))
            .orElseThrow(() -> new GameTestAssertException(path + " recipe was not loaded"));
        check(holder.value() instanceof FastCookingRecipe, path + " did not load as a fast-cooking recipe");
        FastCookingRecipe recipe = (FastCookingRecipe) holder.value();
        check(recipe.getType() == ModRecipeTypes.FAST_COOKING_TYPE.get(), path + " has the wrong recipe type");
        return recipe;
    }

    private static SuperHeatingRecipe superHeatingRecipe(ExtendedGameTestHelper helper, String path) {
        RecipeHolder<?> holder = helper.getLevel().getRecipeManager()
            .byKey(ResourceLocation.fromNamespaceAndPath("anvilcraft", path))
            .orElseThrow(() -> new GameTestAssertException(path + " recipe was not loaded"));
        check(holder.value() instanceof SuperHeatingRecipe, path + " did not load as a super-heating recipe");
        SuperHeatingRecipe recipe = (SuperHeatingRecipe) holder.value();
        check(recipe.getType() == ModRecipeTypes.SUPER_HEATING_TYPE.get(), path + " has the wrong recipe type");
        return recipe;
    }

    private static void processCauldronRecipe(
        ServerLevel level,
        HardenedResinCauldronEntity cauldron,
        AbstractPlasticEntity anvil,
        SuperHeatingRecipe recipe
    ) {
        Vec3 recipeOrigin = CauldronImpactRecipeProcessor.recipePotCell(cauldron)
            .getCenter()
            .add(0.0D, 0.5D, 0.0D);
        InWorldRecipeContext context = new InWorldRecipeContext(level, recipeOrigin, anvil);
        cauldron.beginRecipeProcessing();
        try {
            check(recipe.matches(context, level), "royal steel recipe did not match the hardened resin cauldron");
            recipe.assemble(context, level.registryAccess());
            context.accept();
        } finally {
            cauldron.finishRecipeProcessing();
        }
    }

    private static FourPotBatch createFourPotBatch(ExtendedGameTestHelper helper) {
        List<HardenedResinCauldronEntity> pots = List.of(
            createPot(helper, new Vec3(3.99D, 2.0D, 3.99D), PlasticEntityOrientation.DEFAULT),
            createPot(helper, new Vec3(4.99D, 2.0D, 3.99D), PlasticEntityOrientation.DEFAULT),
            createPot(helper, new Vec3(3.99D, 2.0D, 4.99D), PlasticEntityOrientation.DEFAULT),
            createPot(helper, new Vec3(4.99D, 2.0D, 4.99D), PlasticEntityOrientation.DEFAULT)
        );
        List<Direction> outletDirections = List.of(
            Direction.WEST,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST
        );
        List<BlockPos> outputPositions = List.of(
            new BlockPos(2, 2, 3),
            new BlockPos(4, 2, 2),
            new BlockPos(3, 2, 5),
            new BlockPos(5, 2, 4)
        );
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.ANVIL_HAMMER.asStack());
        for (int index = 0; index < pots.size(); index++) {
            HardenedResinCauldronEntity pot = pots.get(index);
            pot.setNoGravity(true);
            helper.setBlock(outputPositions.get(index), Blocks.CHEST);
            check(
                pot.plasticraft$useAnvilHammer(
                    player,
                    InteractionHand.MAIN_HAND,
                    outletDirections.get(index)
                ).consumesAction(),
                "failed to open outlet for pot " + index
            );
        }
        return new FourPotBatch(pots, outputPositions);
    }

    private static void postStandardAnvilImpact(ExtendedGameTestHelper helper, BlockPos potCell) {
        ServerLevel level = helper.getLevel();
        FallingBlockEntity anvil = new FallingBlockEntity(EntityType.FALLING_BLOCK, level);
        anvil.blockState = Blocks.ANVIL.defaultBlockState();
        NeoForge.EVENT_BUS.post(new AnvilEvent.OnLand(level, helper.absolutePos(potCell).above(), anvil, 1.0F));
    }

    private record FourPotBatch(
        List<HardenedResinCauldronEntity> pots,
        List<BlockPos> outputPositions
    ) {
    }

    private static ItemStack findCapturedResinAnvil(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(PlasticraftBlocks.RESIN_ANVIL.asItem()) && stack.has(ModComponents.SAVED_ENTITY)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findInventoryStack(Player player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static ResinAnvilEntity createResinAnvil(
        ExtendedGameTestHelper helper,
        Vec3 relativeBottomCenter,
        ItemStack dropStack
    ) {
        ResinAnvilEntity anvil = new ResinAnvilEntity(
            PlasticraftEntities.RESIN_ANVIL.get(),
            helper.getLevel(),
            helper.absoluteVec(relativeBottomCenter),
            PlasticraftBlocks.RESIN_ANVIL.get().defaultBlockState(),
            dropStack,
            PlasticEntityOrientation.DEFAULT
        );
        check(helper.getLevel().addFreshEntity(anvil), "failed to add resin anvil to the test level");
        return anvil;
    }

    private static ResinAnvilEntity createResinAnvilInCell(
        ExtendedGameTestHelper helper,
        BlockPos relativeCell,
        ItemStack dropStack
    ) {
        EntityType<? extends ResinAnvilEntity> type = PlasticraftEntities.RESIN_ANVIL.get();
        Vec3 position = PlasticEntityOrientation.DEFAULT.entityPosition(
            helper.absolutePos(relativeCell),
            type.getWidth(),
            type.getHeight()
        );
        ResinAnvilEntity anvil = new ResinAnvilEntity(
            type,
            helper.getLevel(),
            position,
            PlasticraftBlocks.RESIN_ANVIL.get().defaultBlockState(),
            dropStack,
            PlasticEntityOrientation.DEFAULT
        );
        check(helper.getLevel().addFreshEntity(anvil), "failed to add resin anvil to the test level");
        return anvil;
    }

    private static HardenedResinAnvilEntity createAnvil(ExtendedGameTestHelper helper, Vec3 relativeBottomCenter) {
        return createAnvil(helper, relativeBottomCenter, PlasticEntityOrientation.DEFAULT);
    }

    private static HardenedResinAnvilEntity createHardenedResinAnvilInCell(
        ExtendedGameTestHelper helper,
        BlockPos relativeCell
    ) {
        EntityType<? extends HardenedResinAnvilEntity> type = PlasticraftEntities.HARDEND_RESIN_ANVIL.get();
        BlockPos absoluteCell = helper.absolutePos(relativeCell);
        Vec3 position = PlasticEntityOrientation.DEFAULT.entityPosition(
            absoluteCell,
            type.getWidth(),
            type.getHeight()
        );
        HardenedResinAnvilEntity anvil = new HardenedResinAnvilEntity(
            type,
            helper.getLevel(),
            position,
            PlasticraftBlocks.HARDEND_RESIN_ANVIL.get().defaultBlockState(),
            PlasticraftBlocks.HARDEND_RESIN_ANVIL.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        check(helper.getLevel().addFreshEntity(anvil), "failed to add hardened resin anvil to the test level");
        return anvil;
    }

    private static HardenedResinAnvilEntity createAnvil(
        ExtendedGameTestHelper helper,
        Vec3 relativeBottomCenter,
        PlasticEntityOrientation orientation
    ) {
        return createAnvilAbsolute(helper, helper.absoluteVec(relativeBottomCenter), orientation);
    }

    private static HardenedResinAnvilEntity createAnvilAbsolute(ExtendedGameTestHelper helper, Vec3 position) {
        return createAnvilAbsolute(helper, position, PlasticEntityOrientation.DEFAULT);
    }

    private static HardenedResinAnvilEntity createAnvilAbsolute(
        ExtendedGameTestHelper helper,
        Vec3 position,
        PlasticEntityOrientation orientation
    ) {
        HardenedResinAnvilEntity anvil = new HardenedResinAnvilEntity(
            PlasticraftEntities.HARDEND_RESIN_ANVIL.get(),
            helper.getLevel(),
            position,
            PlasticraftBlocks.HARDEND_RESIN_ANVIL.get().defaultBlockState(),
            new ItemStack(PlasticraftBlocks.HARDEND_RESIN_ANVIL.get()),
            orientation
        );
        check(helper.getLevel().addFreshEntity(anvil), "failed to add plastic anvil to the test level");
        return anvil;
    }

    private static HardenedResinCauldronEntity createPot(
        ExtendedGameTestHelper helper,
        Vec3 relativeBottomCenter,
        PlasticEntityOrientation orientation
    ) {
        HardenedResinCauldronEntity pot = new HardenedResinCauldronEntity(
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get(),
            helper.getLevel(),
            helper.absoluteVec(relativeBottomCenter),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            orientation
        );
        check(helper.getLevel().addFreshEntity(pot), "failed to add plastic pot to the test level");
        return pot;
    }

    private static void assertRoyalAnvilCollisionShape(AbstractPlasticEntity anvil, String name) {
        AABB bounds = anvil.getBoundingBox();
        VoxelShape actual = anvil.plasticraft$getCollisionShape();
        VoxelShape expected = PlasticEntityCollisionShapes.rotate(
            AbstractPlasticEntityBlock.ROYAL_ANVIL_COLLISION_SHAPE,
            anvil.getOrientation()
        ).move(anvil.getX() - 0.5D, anvil.getY(), anvil.getZ() - 0.5D);
        check(
            !Shapes.joinIsNotEmpty(actual, expected, BooleanOp.NOT_SAME),
            name + " collision did not exactly match the royal anvil"
        );
        check(anvil.plasticraft$getCollisionBox().components().size() >= 3, name + " collision lost its component boxes");
        check(sameBounds(bounds, actual.bounds()), name + " broad-phase box did not follow the precise collision bounds");
        check(Math.abs(actual.bounds().getZsize() - 1.0D) <= EPSILON, name + " collision was scaled below one block");

        VoxelShape recessedCorner = Shapes.create(new AABB(
            bounds.minX,
            bounds.minY,
            bounds.minZ,
            bounds.minX + bounds.getXsize() * 0.1D,
            bounds.minY + bounds.getYsize() * 0.2D,
            bounds.minZ + bounds.getZsize() * 0.1D
        ));
        check(
            !Shapes.joinIsNotEmpty(actual, recessedCorner, BooleanOp.AND),
            name + " filled a recessed royal-anvil corner"
        );

        Vec3 center = bounds.getCenter();
        VoxelShape baseCenter = Shapes.create(new AABB(
            center.x - bounds.getXsize() * 0.05D,
            bounds.minY,
            center.z - bounds.getZsize() * 0.05D,
            center.x + bounds.getXsize() * 0.05D,
            bounds.minY + bounds.getYsize() * 0.2D,
            center.z + bounds.getZsize() * 0.05D
        ));
        check(
            Shapes.joinIsNotEmpty(actual, baseCenter, BooleanOp.AND),
            name + " lost the solid royal-anvil base"
        );
    }

    private static Item findRoyalPreferredItem(ServerLevel level, TagKey<Item> candidates) {
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(candidates)) {
            Item item = holder.value();
            if (RoyalPreferenceOutcome.RoyalPreference.isRoyalPreferred(level, new ItemStack(item))) {
                return item;
            }
        }
        throw new GameTestAssertException("royal preference selected no item from " + candidates.location());
    }

    private static void assertPlayerJumpsAndStepsWithHeadProduct(
        ExtendedGameTestHelper helper,
        String productName,
        Function<Vec3, AbstractPlasticEntity> productFactory
    ) {
        helper.setBlock(3, 1, 3, Blocks.STONE_SLAB);
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 playerPosition = helper.absoluteVec(new Vec3(2.5D, 1.0D, 3.5D));
        player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z);
        Vec3 relativeProductPosition = new Vec3(2.5D, 1.0D + player.getBbHeight(), 3.5D);
        AbstractPlasticEntity product = productFactory.apply(relativeProductPosition);

        helper.runAfterDelay(3, () -> {
            Vec3 playerStart = player.position();
            Vec3 productStart = product.position();
            check(
                PlasticEntityPhysics.hasImmediateEntityContact(product, player, Direction.DOWN),
                productName + " did not settle on the player's head"
            );

            player.setOnGround(true);
            player.jumpFromGround();
            player.travel(Vec3.ZERO);
            check(player.getY() > playerStart.y + 0.35D, productName + " prevented the player from jumping");
            check(product.getY() > productStart.y + 0.35D, productName + " did not follow the player's jump");

            player.moveTo(playerStart.x, playerStart.y, playerStart.z);
            product.setPos(productStart);
            player.setDeltaMovement(Vec3.ZERO);
            product.setDeltaMovement(Vec3.ZERO);
            player.setOnGround(true);
            player.setDeltaMovement(0.7D, 0.0D, 0.0D);
            player.travel(Vec3.ZERO);
            check(
                player.getX() > playerStart.x + 0.4D && player.getY() > playerStart.y + 0.4D,
                productName + " prevented the player from stepping up"
            );
            check(
                product.getX() > productStart.x + 0.4D && product.getY() > productStart.y + 0.4D,
                productName + " did not follow the player's step-up"
            );
            helper.succeed();
        });
    }

    private static GameTestPlayer makeResinHammerHelmetPlayer(
        ExtendedGameTestHelper helper,
        Vec3 relativePosition
    ) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 position = helper.absoluteVec(relativePosition);
        player.moveTo(position.x, position.y, position.z);
        player.setItemSlot(
            EquipmentSlot.HEAD,
            PlasticraftItems.RESIN_ANVIL_HAMMER.asStack()
        );
        return player;
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

    /** 为测试提供公开的 AccelerateManager 入口点，无需构造已供能的多方块结构。 */
    private static final class AccelerationTestAnvilEntity extends HardenedResinAnvilEntity {
        private AccelerateManager.AccelerationEntry acceleration;

        private AccelerationTestAnvilEntity(
            EntityType<? extends HardenedResinAnvilEntity> entityType,
            Level level,
            Vec3 position,
            AccelerateManager.AccelerationEntry acceleration
        ) {
            super(
                entityType,
                level,
                position,
                PlasticraftBlocks.HARDEND_RESIN_ANVIL.get().defaultBlockState(),
                new ItemStack(PlasticraftBlocks.HARDEND_RESIN_ANVIL.get()),
                PlasticEntityOrientation.DEFAULT
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

        BlockState state = PlasticraftBlocks.HARDEND_RESIN_ANVIL.get()
            .defaultBlockState()
            .setValue(HardenedResinAnvilBlock.FACING, Direction.EAST);
        helper.setBlock(pos, state);
        helper.runAfterDelay(5, () -> {
            check(!helper.getBlockState(pos).is(PlasticraftBlocks.HARDEND_RESIN_ANVIL.get()), "compatibility block did not convert");
            List<FallingBlockEntity> fallingBlocks = helper.getLevel().getEntitiesOfClass(
                FallingBlockEntity.class,
                new AABB(blockCenter, blockCenter).inflate(3.0D)
            );
            check(fallingBlocks.size() == 1, "expected one converted falling-block entity, got " + fallingBlocks.size());
            check(
                fallingBlocks.getFirst() instanceof HardenedResinAnvilEntity,
                direction + " gravity created vanilla " + fallingBlocks.getFirst().getClass().getName()
            );
            HardenedResinAnvilEntity anvil = (HardenedResinAnvilEntity) fallingBlocks.getFirst();
            check(anvil.getDisplayState().equals(state), direction + " conversion lost the placed block state");
            check(
                "hardened_resin".equals(PlasticItemData.getMaterial(anvil.getDropStack())),
                direction + " conversion lost the hardened resin material"
            );
            check(!anvil.getDisplayState().hasProperty(DyeableMaterial.COLOR), "fixed material gained a colour state");
            helper.succeed();
        });
    }

    private static boolean close(Vec3 first, Vec3 second) {
        return first.distanceToSqr(second) <= EPSILON * EPSILON;
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) <= EPSILON;
    }

    private static boolean sameBounds(AABB first, AABB second) {
        return close(first.minX, second.minX)
            && close(first.minY, second.minY)
            && close(first.minZ, second.minZ)
            && close(first.maxX, second.maxX)
            && close(first.maxY, second.maxY)
            && close(first.maxZ, second.maxZ);
    }

    private static Vec3 eastFaceHit(AbstractPlasticEntity entity) {
        AABB faceBox = entity.plasticraft$getInteractionShape().toAabbs().stream()
            .max((first, second) -> Double.compare(first.maxX, second.maxX))
            .orElseThrow(() -> new GameTestAssertException("plastic entity has no interaction shape"));
        Vec3 center = faceBox.getCenter();
        return new Vec3(faceBox.maxX - entity.getX(), center.y - entity.getY(), center.z - entity.getZ());
    }

    private static int countItem(IItemHandler handler, Item item) {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static int countItem(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static boolean isEmpty(IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) return false;
        }
        return true;
    }

    private static void assertLevelPush(
        GameTestPlayer player,
        AbstractPlasticEntity target,
        Vec3 requestedMovement,
        double standingY,
        Direction.Axis movementAxis
    ) {
        double playerStart = player.position().get(movementAxis);
        double targetStart = target.position().get(movementAxis);
        player.setOnGround(true);
        player.move(MoverType.SELF, requestedMovement);
        double playerMovement = player.position().get(movementAxis) - playerStart;
        double targetMovement = target.position().get(movementAxis) - targetStart;
        check(playerMovement > 0.1D, movementAxis + " push stalled the player: " + playerMovement);
        check(targetMovement > 0.1D, movementAxis + " push stalled the plastic body: " + targetMovement);
        check(
            Math.abs(playerMovement - targetMovement) < 0.02D,
            movementAxis + " push separated the player and plastic body: player=" + playerMovement
                + ", target=" + targetMovement
        );
        check(Math.abs(player.getY() - standingY) < 1.0E-4D, movementAxis + " push stepped the player upward");
        check(player.getPose() == Pose.STANDING, movementAxis + " push changed the player pose to " + player.getPose());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
