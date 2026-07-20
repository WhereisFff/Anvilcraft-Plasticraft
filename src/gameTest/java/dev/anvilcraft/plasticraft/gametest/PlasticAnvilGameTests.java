package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.HighViscosityResinFluidBlock;
import dev.anvilcraft.plasticraft.block.piston.HighViscosityPistonBudget;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.api.fluid.network.FluidContainerLookup;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.entity.FishTankBlockEntity;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.block.fluid.PipeBlock;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.event.anvil.AnvilEventListener;
import dev.dubhe.anvilcraft.event.giantanvil.shock.ShockContext;
import dev.dubhe.anvilcraft.init.block.ModBlockTags;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.init.recipe.ModRecipeTypes;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.FastCookingRecipe;
import dev.dubhe.anvilcraft.util.AccelerateManager;
import dev.dubhe.anvilcraft.util.GravityManager;
import dev.dubhe.anvilcraft.util.GravityType;
import dev.anvilcraft.plasticraft.block.HardenedResinAnvilBlock;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityPhysics;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.ResinAnvilEntity;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.entity.ModEntities;
import dev.anvilcraft.plasticraft.init.ModMenuTypes;
import dev.anvilcraft.plasticraft.inventory.HardenedResinAnvilMenu;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.HardenedResinAnvilItem;
import dev.anvilcraft.plasticraft.item.HighViscosityResinBlockItem;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.recipe.FluidFastCookingRecipe;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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
    @TestHolder(description = "The actual item path attaches a 0.98 entity to each clicked face")
    static void itemPlacesOnAllSixFaces(ExtendedGameTestHelper helper) {
        BlockPos clicked = new BlockPos(3, 3, 3);
        helper.setBlock(clicked, Blocks.STONE);
        for (Direction face : Direction.values()) {
            ItemStack stack = ModBlocks.HARDEND_RESIN_ANVIL.asStack();
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

            Vec3 expectedCenter = PlasticEntityOrientation.forPlacement(face, player)
                .collisionCenter(helper.absolutePos(clicked.relative(face)));
            HardenedResinAnvilEntity placed = helper.getLevel()
                .getEntitiesOfClass(
                    HardenedResinAnvilEntity.class,
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
            ModBlocks.HARDEND_RESIN_ANVIL.asStack()
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
    @TestHolder(description = "Hardened resin migrates legacy colour data while preserving entity state")
    static void stateSurvivesSaveLoad(ExtendedGameTestHelper helper) {
        HardenedResinAnvilEntity original = createAnvil(helper, new Vec3(1.5D, 1.0D, 1.5D));
        PlasticEntityOrientation orientation = new PlasticEntityOrientation(Direction.WEST, 3);
        original.setOrientation(orientation);
        ItemStack drop = ModBlocks.HARDEND_RESIN_ANVIL.asStack();
        HardenedResinAnvilItem.setColor(drop, net.minecraft.world.item.DyeColor.CYAN);
        original.setDropStack(drop);

        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        HardenedResinAnvilEntity loaded = new HardenedResinAnvilEntity(ModEntities.HARDEND_RESIN_ANVIL.get(), helper.getLevel());
        loaded.load(saved);
        check(loaded.getOrientation().equals(orientation), "orientation did not survive NBT");
        check(!loaded.getDisplayState().hasProperty(DyeableMaterial.COLOR), "hardened resin still exposes a colour state");
        ItemStack normalizedDrop = loaded.getDropStack();
        check(
            "hardened_resin".equals(PlasticItemData.getMaterial(normalizedDrop)),
            "legacy drop did not migrate to hardened resin"
        );
        check(
            normalizedDrop.get(DataComponents.CUSTOM_DATA) == null
                || !normalizedDrop.get(DataComponents.CUSTOM_DATA).contains(DyeableMaterial.COLOR_KEY),
            "fixed-colour drop retained legacy PlasticColor data"
        );
        check(!loaded.isNoGravity(), "gravity flag changed during NBT round trip");
        original.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate
    @TestHolder(description = "Runtime magnetic-model changes are carried by synchronized entity data")
    static void displayStateSynchronizesAtRuntime(ExtendedGameTestHelper helper) {
        HardenedResinAnvilEntity server = createAnvil(helper, new Vec3(1.5D, 1.0D, 1.5D));
        HardenedResinAnvilEntity client = new HardenedResinAnvilEntity(ModEntities.HARDEND_RESIN_ANVIL.get(), helper.getLevel());
        server.getEntityData().packDirty();

        BlockState magnetic = server.getDisplayState().setValue(
            dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock.MAGNETIZED,
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
    @EmptyTemplate
    @TestHolder(description = "Resin and magnet-ingot centers select the ordinary and magnetic crafting recipes")
    static void resinAnvilCraftingRecipesSelectMagnetism(ExtendedGameTestHelper helper) {
        RecipeHolder<?> ordinaryHolder = helper.getLevel().getRecipeManager()
            .byKey(AnvilcraftPlasticraft.of("resin_anvil"))
            .orElseThrow(() -> new GameTestAssertException("ordinary resin anvil recipe was not loaded"));
        RecipeHolder<?> magneticHolder = helper.getLevel().getRecipeManager()
            .byKey(AnvilcraftPlasticraft.of("magnetic_resin_anvil"))
            .orElseThrow(() -> new GameTestAssertException("magnetic resin anvil recipe was not loaded"));
        check(ordinaryHolder.value() instanceof ShapedRecipe, "ordinary resin anvil recipe was not shaped");
        check(magneticHolder.value() instanceof ShapedRecipe, "magnetic resin anvil recipe was not shaped");
        ShapedRecipe ordinaryRecipe = (ShapedRecipe) ordinaryHolder.value();
        ShapedRecipe magneticRecipe = (ShapedRecipe) magneticHolder.value();

        CraftingInput ordinaryInput = resinAnvilCraftingInput(ModItems.RESIN.asStack());
        CraftingInput magneticInput = resinAnvilCraftingInput(ModItems.MAGNET_INGOT.asStack());
        check(ordinaryRecipe.matches(ordinaryInput, helper.getLevel()), "resin center did not match the ordinary recipe");
        check(!ordinaryRecipe.matches(magneticInput, helper.getLevel()), "magnet-ingot center also matched the ordinary recipe");
        check(magneticRecipe.matches(magneticInput, helper.getLevel()), "magnet-ingot center did not match the magnetic recipe");
        check(!magneticRecipe.matches(ordinaryInput, helper.getLevel()), "resin center also matched the magnetic recipe");

        ItemStack ordinary = ordinaryRecipe.assemble(ordinaryInput, helper.getLevel().registryAccess());
        ItemStack magnetic = magneticRecipe.assemble(magneticInput, helper.getLevel().registryAccess());
        check(ordinary.is(ModBlocks.RESIN_ANVIL.asItem()), "ordinary recipe returned the wrong item");
        check(!PlasticItemData.isMagnetized(ordinary), "ordinary recipe returned a magnetic anvil");
        check("resin".equals(PlasticItemData.getMaterial(ordinary)), "ordinary recipe lost its resin identity");
        check(magnetic.is(ModBlocks.RESIN_ANVIL.asItem()), "magnetic recipe returned the wrong item");
        check(PlasticItemData.isMagnetized(magnetic), "magnetic recipe did not set magnetic state");
        check(
            magnetic.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT).value() == 1,
            "magnetic recipe did not select the magnetic item model"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate
    @TestHolder(description = "Hardened resin and magnet-ingot rims select the ordinary and magnetic cauldron recipes")
    static void hardenedResinCauldronCraftingRecipesSelectMagnetism(ExtendedGameTestHelper helper) {
        RecipeHolder<?> ordinaryHolder = helper.getLevel().getRecipeManager()
            .byKey(AnvilcraftPlasticraft.of("hardend_resin_cauldron"))
            .orElseThrow(() -> new GameTestAssertException("ordinary hardened resin cauldron recipe was not loaded"));
        RecipeHolder<?> magneticHolder = helper.getLevel().getRecipeManager()
            .byKey(AnvilcraftPlasticraft.of("magnetic_hardend_resin_cauldron"))
            .orElseThrow(() -> new GameTestAssertException("magnetic hardened resin cauldron recipe was not loaded"));
        check(ordinaryHolder.value() instanceof ShapedRecipe, "ordinary hardened resin cauldron recipe was not shaped");
        check(magneticHolder.value() instanceof ShapedRecipe, "magnetic hardened resin cauldron recipe was not shaped");
        ShapedRecipe ordinaryRecipe = (ShapedRecipe) ordinaryHolder.value();
        ShapedRecipe magneticRecipe = (ShapedRecipe) magneticHolder.value();

        CraftingInput ordinaryInput = hardenedResinCauldronCraftingInput(ModItems.HARDEND_RESIN.asStack());
        CraftingInput magneticInput = hardenedResinCauldronCraftingInput(ModItems.MAGNET_INGOT.asStack());
        check(ordinaryRecipe.matches(ordinaryInput, helper.getLevel()), "hardened resin rim did not match the ordinary recipe");
        check(!ordinaryRecipe.matches(magneticInput, helper.getLevel()), "magnet-ingot rim also matched the ordinary recipe");
        check(magneticRecipe.matches(magneticInput, helper.getLevel()), "magnet-ingot rim did not match the magnetic recipe");
        check(!magneticRecipe.matches(ordinaryInput, helper.getLevel()), "hardened resin rim also matched the magnetic recipe");

        ItemStack ordinary = ordinaryRecipe.assemble(ordinaryInput, helper.getLevel().registryAccess());
        ItemStack magnetic = magneticRecipe.assemble(magneticInput, helper.getLevel().registryAccess());
        check(ordinary.is(ModBlocks.HARDEND_RESIN_CAULDRON.asItem()), "ordinary recipe returned the wrong item");
        check(!PlasticItemData.isMagnetized(ordinary), "ordinary recipe returned a magnetic cauldron");
        check(magnetic.is(ModBlocks.HARDEND_RESIN_CAULDRON.asItem()), "magnetic recipe returned the wrong item");
        check(PlasticItemData.isMagnetized(magnetic), "magnetic recipe did not set magnetic state");
        check(
            magnetic.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT).value() == 1,
            "magnetic cauldron recipe did not select the magnetic item model"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate
    @TestHolder(description = "Hardened resin products use their migrated IDs and no legacy plastic IDs remain registered")
    static void hardenedResinRegistryIdsAreMigrated(ExtendedGameTestHelper helper) {
        check(
            BuiltInRegistries.BLOCK.getKey(ModBlocks.HARDEND_RESIN_ANVIL.get())
                .equals(AnvilcraftPlasticraft.of("hardend_resin_anvil")),
            "hardened resin anvil block kept the legacy registry id"
        );
        check(
            BuiltInRegistries.BLOCK.getKey(ModBlocks.HARDEND_RESIN_CAULDRON.get())
                .equals(AnvilcraftPlasticraft.of("hardend_resin_cauldron")),
            "hardened resin cauldron block kept the legacy registry id"
        );
        check(
            BuiltInRegistries.ITEM.getKey(ModBlocks.HARDEND_RESIN_ANVIL.asItem())
                .equals(AnvilcraftPlasticraft.of("hardend_resin_anvil")),
            "hardened resin anvil item kept the legacy registry id"
        );
        check(
            BuiltInRegistries.ITEM.getKey(ModBlocks.HARDEND_RESIN_CAULDRON.asItem())
                .equals(AnvilcraftPlasticraft.of("hardend_resin_cauldron")),
            "hardened resin cauldron item kept the legacy registry id"
        );
        check(
            BuiltInRegistries.ENTITY_TYPE.getKey(ModEntities.HARDEND_RESIN_ANVIL.get())
                .equals(AnvilcraftPlasticraft.of("hardend_resin_anvil")),
            "hardened resin anvil entity kept the legacy registry id"
        );
        check(
            BuiltInRegistries.ENTITY_TYPE.getKey(ModEntities.HARDEND_RESIN_CAULDRON.get())
                .equals(AnvilcraftPlasticraft.of("hardend_resin_cauldron")),
            "hardened resin cauldron entity kept the legacy registry id"
        );
        check(
            BuiltInRegistries.MENU.getKey(ModMenuTypes.HARDEND_RESIN_ANVIL.get())
                .equals(AnvilcraftPlasticraft.of("hardend_resin_anvil")),
            "hardened resin anvil menu kept the legacy registry id"
        );
        check(BuiltInRegistries.BLOCK.getOptional(AnvilcraftPlasticraft.of("plastic_anvil")).isEmpty(),
            "legacy plastic_anvil block id is still registered");
        check(BuiltInRegistries.BLOCK.getOptional(AnvilcraftPlasticraft.of("plastic_pot")).isEmpty(),
            "legacy plastic_pot block id is still registered");
        check(BuiltInRegistries.ITEM.getOptional(AnvilcraftPlasticraft.of("plastic_anvil")).isEmpty(),
            "legacy plastic_anvil item id is still registered");
        check(BuiltInRegistries.ITEM.getOptional(AnvilcraftPlasticraft.of("plastic_pot")).isEmpty(),
            "legacy plastic_pot item id is still registered");
        check(BuiltInRegistries.ENTITY_TYPE.getOptional(AnvilcraftPlasticraft.of("plastic_anvil")).isEmpty(),
            "legacy plastic_anvil entity id is still registered");
        check(BuiltInRegistries.ENTITY_TYPE.getOptional(AnvilcraftPlasticraft.of("plastic_pot")).isEmpty(),
            "legacy plastic_pot entity id is still registered");
        check(BuiltInRegistries.MENU.getOptional(AnvilcraftPlasticraft.of("plastic_anvil")).isEmpty(),
            "legacy plastic_anvil menu id is still registered");
        check(ModBlocks.HARDEND_RESIN_ANVIL.get().defaultBlockState().is(ModBlockTags.NON_MAGNETIC),
            "hardened resin compatibility block can bypass crafted magnetism");
        check(ModBlocks.RESIN_ANVIL.get().defaultBlockState().is(ModBlockTags.NON_MAGNETIC),
            "resin compatibility block can bypass crafted magnetism");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate
    @TestHolder(description = "Fast cooking hardens ordinary and magnetic resin anvils without mixing their states")
    static void fastCookingPreservesResinAnvilMagnetism(ExtendedGameTestHelper helper) {
        FastCookingRecipe ordinaryRecipe = fastCookingRecipe(helper, "fast_cooking/harden_resin_anvil");
        FastCookingRecipe magneticRecipe = fastCookingRecipe(helper, "fast_cooking/harden_magnetic_resin_anvil");
        ItemStack ordinaryInput = ModBlocks.RESIN_ANVIL.asStack();
        ItemStack magneticInput = ModBlocks.RESIN_ANVIL.asStack();
        PlasticItemData.setMagnetized(magneticInput, true);

        check(ordinaryRecipe.getInputItems().getFirst().test(ordinaryInput), "ordinary hardening rejected ordinary resin");
        check(!ordinaryRecipe.getInputItems().getFirst().test(magneticInput), "ordinary hardening accepted magnetic resin");
        check(magneticRecipe.getInputItems().getFirst().test(magneticInput), "magnetic hardening rejected magnetic resin");
        check(!magneticRecipe.getInputItems().getFirst().test(ordinaryInput), "magnetic hardening accepted ordinary resin");

        ItemStack ordinaryOutput = ordinaryRecipe.getResultItems().getFirst().stack();
        ItemStack magneticOutput = magneticRecipe.getResultItems().getFirst().stack();
        check(ordinaryOutput.is(ModBlocks.HARDEND_RESIN_ANVIL.asItem()), "ordinary hardening returned the wrong item");
        check(!PlasticItemData.isMagnetized(ordinaryOutput), "ordinary hardening added magnetism");
        check("hardened_resin".equals(PlasticItemData.getMaterial(ordinaryOutput)), "ordinary output was not hardened resin");
        check(magneticOutput.is(ModBlocks.HARDEND_RESIN_ANVIL.asItem()), "magnetic hardening returned the wrong item");
        check(PlasticItemData.isMagnetized(magneticOutput), "magnetic hardening lost magnetism");
        check("hardened_resin".equals(PlasticItemData.getMaterial(magneticOutput)), "magnetic output was not hardened resin");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate
    @TestHolder(description = "High-viscosity resin recipes retain exact fluid transformations and quantities")
    static void highViscosityResinRecipesLoad(ExtendedGameTestHelper helper) {
        RecipeHolder<?> fastHolder = helper.getLevel().getRecipeManager()
            .byKey(AnvilcraftPlasticraft.of("fast_cooking/liquid_high_viscosity_resin"))
            .orElseThrow(() -> new GameTestAssertException("liquid high-viscosity resin recipe was not loaded"));
        check(fastHolder.value() instanceof FluidFastCookingRecipe, "fluid fast-cooking serializer was not used");
        FastCookingRecipe fastRecipe = (FastCookingRecipe) fastHolder.value();
        check(fastRecipe.getType() == ModRecipeTypes.FAST_COOKING_TYPE.get(), "fluid recipe left fast-cooking category");
        check(fastRecipe.getInputItems().size() == 3, "fluid recipe did not retain all three ingredients");
        check(fastRecipe.getInputItems().get(0).test(new ItemStack(ModItems.RESIN.get(), 4)), "four resin did not match");
        check(fastRecipe.getInputItems().get(1).test(new ItemStack(Items.SLIME_BALL, 4)), "four slime balls did not match");
        check(fastRecipe.getInputItems().get(2).test(ModItems.LIME_POWDER.asStack()), "lime powder did not match");
        check(fastRecipe.getResultItems().isEmpty(), "fluid recipe unexpectedly produced an item");
        check(fastRecipe.getHasCauldron().fluid().equals(BuiltInRegistries.FLUID.getKey(Fluids.WATER)), "input was not water");
        check(fastRecipe.getHasCauldron().consume() == 1000, "fast cooking did not consume a full water bucket");
        check(
            fastRecipe.getHasCauldron().transform().equals(dev.anvilcraft.plasticraft.init.block.ModFluids.liquidHighViscosityResinId()),
            "fast cooking transformed into the wrong fluid"
        );
        check(fastRecipe.getHasCauldron().produce() == 1000, "fast cooking did not produce a full resin bucket");

        RecipeHolder<?> warpHolder = helper.getLevel().getRecipeManager()
            .byKey(AnvilcraftPlasticraft.of("time_warp/high_viscosity_resin_block"))
            .orElseThrow(() -> new GameTestAssertException("high-viscosity resin time-warp recipe was not loaded"));
        check(
            warpHolder.value() instanceof dev.dubhe.anvilcraft.recipe.anvil.wrap.TimeWarpRecipe,
            "fluid solidification recipe was not a time-warp recipe"
        );
        dev.dubhe.anvilcraft.recipe.anvil.wrap.TimeWarpRecipe warpRecipe =
            (dev.dubhe.anvilcraft.recipe.anvil.wrap.TimeWarpRecipe) warpHolder.value();
        check(
            warpRecipe.getHasCauldron().fluid().equals(dev.anvilcraft.plasticraft.init.block.ModFluids.liquidHighViscosityResinId()),
            "time warp consumed the wrong fluid"
        );
        check(warpRecipe.getHasCauldron().consume() == 1000, "time warp did not consume the full resin bucket");
        check(
            warpRecipe.getResultItems().getFirst().getItem() == ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem(),
            "time warp produced the wrong solid block"
        );

        RecipeHolder<?> nativeHolder = helper.getLevel().getRecipeManager()
            .byKey(ResourceLocation.fromNamespaceAndPath(
                "anvilcraftplasticraft_tests",
                "fast_cooking_fluid_codec"
            ))
            .orElseThrow(() -> new GameTestAssertException("native fluid fast-cooking recipe was not loaded"));
        check(
            nativeHolder.value().getClass() == FastCookingRecipe.class,
            "native fast-cooking serializer did not create the base recipe class"
        );
        FastCookingRecipe nativeRecipe = (FastCookingRecipe) nativeHolder.value();
        check(nativeRecipe.getHasCauldron().consume() == 1000, "native serializer lost fluid consumption");
        check(nativeRecipe.getHasCauldron().produce() == 1000, "native serializer lost fluid production");
        check(
            nativeRecipe.getHasCauldron().transform().equals(BuiltInRegistries.FLUID.getKey(Fluids.LAVA)),
            "native serializer lost the transformed fluid"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "A plastic pot touching a low campfire still runs fast-cooking recipes")
    static void plasticPotProcessesFastCookingOnLowWorkBlock(ExtendedGameTestHelper helper) {
        BlockPos campfirePos = new BlockPos(3, 1, 3);
        helper.setBlock(
            campfirePos,
            Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true)
        );
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(3.5D, 1.4375D, 3.5D),
            PlasticEntityOrientation.DEFAULT
        );
        pot.setNoGravity(true);
        pot.getFluidHandler().fill(
            new FluidStack(Fluids.WATER, HardenedResinCauldronEntity.CAPACITY),
            IFluidHandler.FluidAction.EXECUTE
        );
        pot.getInput().insertItem(0, new ItemStack(ModItems.RESIN.get(), 4), false);
        pot.getInput().insertItem(1, new ItemStack(Items.SLIME_BALL, 4), false);
        pot.getInput().insertItem(2, ModItems.LIME_POWDER.asStack(), false);
        HardenedResinAnvilEntity anvil = createAnvil(
            helper,
            new Vec3(3.5D, 2.4375D, 3.5D)
        );
        anvil.setNoGravity(true);

        pot.processAnvilImpact(anvil, Direction.DOWN);

        check(isEmpty(pot.getInput()), "low campfire fast cooking did not consume every ingredient");
        check(
            pot.getFluidHandler().getFluid().is(dev.anvilcraft.plasticraft.init.block.ModFluids.LIQUID_HIGH_VISCOSITY_RESIN.get()),
            "low campfire fast cooking did not transform the pot fluid"
        );
        check(
            pot.getFluidHandler().getFluidAmount() == HardenedResinCauldronEntity.CAPACITY,
            "low campfire fast cooking produced the wrong fluid amount"
        );
        pot.discard();
        anvil.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "High-viscosity resin freezes non-players but slows players like cobwebs")
    static void highViscosityResinEntityMovement(ExtendedGameTestHelper helper) {
        BlockPos zombieFluidPos = new BlockPos(2, 1, 2);
        BlockPos playerFluidPos = new BlockPos(4, 1, 2);
        helper.setBlock(zombieFluidPos, ModBlocks.LIQUID_HIGH_VISCOSITY_RESIN.get());
        helper.setBlock(playerFluidPos, ModBlocks.LIQUID_HIGH_VISCOSITY_RESIN.get());
        BlockState fluidState = ModBlocks.LIQUID_HIGH_VISCOSITY_RESIN.get().defaultBlockState();
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
            dev.anvilcraft.plasticraft.init.block.ModFluids.LIQUID_HIGH_VISCOSITY_RESIN.get(),
            1000
        );
        BlockPos fishTankPos = new BlockPos(2, 1, 2);
        helper.setBlock(
            fishTankPos,
            dev.dubhe.anvilcraft.init.block.ModBlocks.FISH_TANK.get().defaultBlockState()
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
        BlockState largeCauldronState = dev.dubhe.anvilcraft.init.block.ModBlocks.LARGE_CAULDRON.get()
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
        helper.setBlock(source, ModBlocks.LIQUID_HIGH_VISCOSITY_RESIN.get());
        helper.getLevel().scheduleTick(
            helper.absolutePos(source),
            dev.anvilcraft.plasticraft.init.block.ModFluids.LIQUID_HIGH_VISCOSITY_RESIN.get(),
            1
        );
        check(
            dev.anvilcraft.plasticraft.init.block.ModFluids.LIQUID_HIGH_VISCOSITY_RESIN.get()
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
        net.minecraft.world.entity.monster.Ravager ravager = helper.spawnWithNoFreeWill(
            EntityType.RAVAGER,
            new Vec3(1.5D, 1.0D, 1.5D)
        );
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack resin = ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asStack();
        check(
            !dev.dubhe.anvilcraft.block.item.HasMobBlockItem.canMobBeSaved(ravager, null, resin),
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
            if (candidate.is(ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
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
                helper.setBlock(center.offset(xOffset, 0, zOffset), ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.get());
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
            context.testCorner(dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK.get()),
            "high-viscosity resin failed the resin shock corner check"
        );
        check(
            context.testBorder(dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK.get()),
            "high-viscosity resin failed the resin shock border check"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("20x8x8")
    @TestHolder(description = "A moved normal block discovers adjacent high-viscosity resin and its grouped load")
    static void highViscosityResinReversePistonAdhesion(ExtendedGameTestHelper helper) {
        BlockPos piston = new BlockPos(1, 2, 3);
        for (int x = 2; x <= 13; x++) helper.setBlock(x, 2, 3, Blocks.STONE);
        BlockPos resin = new BlockPos(2, 3, 3);
        helper.setBlock(resin, ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.get());
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
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "A resin anvil never opens a menu when used")
    static void resinAnvilHasNoMenu(ExtendedGameTestHelper helper) {
        ResinAnvilEntity anvil = createResinAnvil(helper, new Vec3(3.5D, 1.0D, 3.5D), ModBlocks.RESIN_ANVIL.asStack());
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
        ItemStack resinAnvil = ModBlocks.RESIN_ANVIL.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, resinAnvil);
        Entity capturedMob = helper.spawnWithNoFreeWill(EntityType.COW, new Vec3(2.5D, 1.0D, 2.5D));
        InteractionResult captureResult = resinAnvil.interactLivingEntity(
            player,
            (net.minecraft.world.entity.LivingEntity) capturedMob,
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
        ResinAnvilEntity loaded = new ResinAnvilEntity(ModEntities.RESIN_ANVIL.get(), helper.getLevel());
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
                net.minecraft.world.entity.animal.Cow.class,
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
            ResinAnvilEntity anvil = createResinAnvilInCell(helper, cell, ModBlocks.RESIN_ANVIL.asStack());
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

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x5x7", floor = true)
    @TestHolder(description = "A resin anvil bounces from an entity and applies a bounded outward impulse")
    static void resinAnvilBouncesFromEntity(ExtendedGameTestHelper helper) {
        ResinAnvilEntity anvil = createResinAnvilInCell(
            helper,
            new BlockPos(3, 2, 3),
            ModBlocks.RESIN_ANVIL.asStack()
        );
        anvil.setNoGravity(true);
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.35D, 2.0D, 3.5D));
        target.setNoGravity(true);
        anvil.setDeltaMovement(0.32D, 0.0D, 0.0D);
        helper.runAfterDelay(2, () -> {
            check(anvil.getDeltaMovement().x < -0.04D, "entity impact did not bounce the resin anvil");
            check(target.getDeltaMovement().x > 0.0D, "entity impact did not push the target outward");
            check(target.getDeltaMovement().x <= 0.45D + EPSILON, "entity impulse exceeded its configured bound");
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
            ModBlocks.RESIN_ANVIL.asStack()
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
            ModBlocks.RESIN_ANVIL.asStack()
        );
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.71D, 2.0D, 3.5D));
        player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z);
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
            ModBlocks.RESIN_ANVIL.asStack()
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
    @EmptyTemplate("9x6x7")
    @TestHolder(description = "Anvil hammer radial selections change all plastic products between six attachment faces")
    static void anvilHammerChangesPlasticAttachmentFaces(ExtendedGameTestHelper helper) {
        PlasticEntityOrientation initial = new PlasticEntityOrientation(Direction.EAST, 2);
        HardenedResinAnvilEntity hardened = createAnvil(
            helper,
            new Vec3(2.5D, 2.0D, 3.5D),
            initial
        );
        ResinAnvilEntity resin = createResinAnvil(
            helper,
            new Vec3(6.5D, 2.0D, 3.5D),
            ModBlocks.RESIN_ANVIL.asStack()
        );
        resin.setOrientation(initial);
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(4.5D, 2.0D, 3.5D),
            initial
        );
        hardened.setNoGravity(true);
        resin.setNoGravity(true);
        pot.setNoGravity(true);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(false);
        ItemStack hammer = ModItems.ANVIL_HAMMER.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, hammer);

        for (AbstractPlasticEntity target : List.of(hardened, resin, pot)) {
            check(target.supportsAnvilHammerOrientationMenu(), "plastic product did not expose its orientation menu");
            for (Direction face : Direction.values()) {
                check(
                    target.plasticraft$changeAttachmentFace(player, InteractionHand.MAIN_HAND, face),
                    "anvil hammer rejected attachment face " + face
                );
                check(target.getOrientation().attachmentFace() == face, "anvil hammer selected the wrong attachment face");
                check(target.getOrientation().quarterTurn() == 2, "changing attachment face changed the in-plane rotation");
            }
        }
        check(hammer.getDamageValue() == 0, "rotating resin anvils damaged the anvil hammer");
        check(hardened.isAlive() && resin.isAlive() && pot.isAlive(), "rotating a plastic product removed its entity");

        hardened.discard();
        resin.discard();
        pot.discard();
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
            ModEntities.HARDEND_RESIN_CAULDRON.get(),
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
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "Attacking a hardened resin cauldron with an anvil hammer runs its recipe")
    static void anvilHammerProcessesHardenedResinCauldron(ExtendedGameTestHelper helper) {
        helper.setBlock(
            new BlockPos(3, 1, 3),
            Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true)
        );
        HardenedResinCauldronEntity pot = createPot(
            helper,
            new Vec3(3.5D, 1.4375D, 3.5D),
            PlasticEntityOrientation.DEFAULT
        );
        pot.setNoGravity(true);
        pot.getFluidHandler().fill(
            new FluidStack(Fluids.WATER, HardenedResinCauldronEntity.CAPACITY),
            IFluidHandler.FluidAction.EXECUTE
        );
        pot.getInput().insertItem(0, new ItemStack(ModItems.RESIN.get(), 4), false);
        pot.getInput().insertItem(1, new ItemStack(Items.SLIME_BALL, 4), false);
        pot.getInput().insertItem(2, ModItems.LIME_POWDER.asStack(), false);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hammer = ModItems.ANVIL_HAMMER.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, hammer);
        player.attack(pot);

        check(pot.isAlive(), "anvil hammer attack destroyed the hardened resin cauldron");
        check(isEmpty(pot.getInput()), "anvil hammer attack did not consume every fast-cooking ingredient");
        check(
            pot.getFluidHandler().getFluid().is(
                dev.anvilcraft.plasticraft.init.block.ModFluids.LIQUID_HIGH_VISCOSITY_RESIN.get()
            ),
            "anvil hammer attack did not transform the hardened resin cauldron fluid"
        );
        check(hammer.getDamageValue() == 1, "anvil hammer attack consumed the wrong amount of durability");
        check(
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, pot.getBoundingBox().inflate(2.0D)).isEmpty(),
            "anvil hammer attack dropped the cauldron or its stored ingredients"
        );

        player.attack(pot);
        check(pot.isAlive(), "anvil hammer attack destroyed the cauldron while the hammer was cooling down");
        check(hammer.getDamageValue() == 1, "a cooling-down anvil hammer consumed durability again");
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

        BlockState pipe = dev.dubhe.anvilcraft.init.block.ModBlocks.PIPE_STRAIGHT.get().defaultBlockState()
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
        helper.setBlock(magnetPos, dev.dubhe.anvilcraft.init.block.ModBlocks.MAGNET_BLOCK.get().defaultBlockState());
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
    @TestHolder(description = "A non-magnetic plastic pot spills full fluid only through a side or bottom opening")
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

        helper.runAfterDelay(3, () -> {
            BlockPos sideTarget = BlockPos.containing(side.getBoundingBox().getCenter()).relative(Direction.EAST);
            BlockPos magneticTarget = BlockPos.containing(magnetic.getBoundingBox().getCenter()).relative(Direction.EAST);
            check(helper.getLevel().getFluidState(sideTarget).isSource(), "side-facing pot did not spill a source fluid");
            check(side.getFluidHandler().getFluid().isEmpty(), "side-facing pot retained spilled fluid");
            check(
                magnetic.getFluidHandler().getFluidAmount() == HardenedResinCauldronEntity.CAPACITY,
                "magnetized pot spilled fluid despite being a sealed container"
            );
            check(helper.getLevel().getFluidState(magneticTarget).isEmpty(), "magnetized pot created an external fluid source");
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
        HardenedResinCauldronEntity loaded = new HardenedResinCauldronEntity(ModEntities.HARDEND_RESIN_CAULDRON.get(), helper.getLevel());
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
    @TestHolder(description = "Fish tank outputs become inputs on the next anvil impact")
    static void fishTankReprocessesItsOutputWithoutDuplicatingInput(ExtendedGameTestHelper helper) {
        BlockPos tankPos = new BlockPos(3, 2, 3);
        helper.setBlock(tankPos, dev.dubhe.anvilcraft.init.block.ModBlocks.FISH_TANK.get().defaultBlockState());
        BlockPos absoluteTankPos = helper.absolutePos(tankPos);
        check(
            helper.getLevel().getBlockEntity(absoluteTankPos) instanceof FishTankBlockEntity,
            "fish tank block entity was not created"
        );
        FishTankBlockEntity tank = (FishTankBlockEntity) helper.getLevel().getBlockEntity(absoluteTankPos);
        check(
            tank.getInputHandler().insertItem(0, new ItemStack(Items.STICK), false).isEmpty(),
            "failed to insert the first fish tank recipe input"
        );

        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(3.5D, 3.0D, 3.5D));
        anvil.setNoGravity(true);
        AnvilEventListener.handleNeoAnvilRecipe(
            new AnvilEvent.OnLand(helper.getLevel(), absoluteTankPos.above(), anvil, 1.0F)
        );
        check(tank.getInputHandler().getStackInSlot(0).isEmpty(), "fish tank did not consume the initial input");
        check(countItem(tank.getOutputHandler(), Items.DIAMOND) == 1, "fish tank did not create the first output");

        helper.runAfterDelay(1, () -> {
            AnvilEventListener.handleNeoAnvilRecipe(
                new AnvilEvent.OnLand(helper.getLevel(), absoluteTankPos.above(), anvil, 1.0F)
            );
            check(
                countItem(tank.getOutputHandler(), Items.DIAMOND) == 0,
                "fish tank created the second result without consuming its first output"
            );
            check(countItem(tank.getOutputHandler(), Items.EMERALD) == 1, "fish tank did not reprocess its output");
            anvil.discard();
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("9x7x9")
    @TestHolder(description = "Hardened resin cauldron outputs become inputs on the next impact")
    static void hardenedResinCauldronReprocessesItsOutputWithoutDuplicatingInput(
        ExtendedGameTestHelper helper
    ) {
        HardenedResinCauldronEntity cauldron = createPot(
            helper,
            new Vec3(3.5D, 2.0D, 3.5D),
            PlasticEntityOrientation.DEFAULT
        );
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(3.5D, 3.0D, 3.5D));
        cauldron.setNoGravity(true);
        anvil.setNoGravity(true);
        check(
            cauldron.getInput().insertItem(0, new ItemStack(Items.STICK), false).isEmpty(),
            "failed to insert the first cauldron recipe input"
        );

        cauldron.processAnvilImpact(anvil, Direction.DOWN);
        check(countItem(cauldron.getOutput(), Items.DIAMOND) == 1, "cauldron did not create the first output");
        cauldron.processAnvilImpact(anvil, Direction.DOWN);
        check(
            countItem(cauldron.getOutput(), Items.DIAMOND) == 1
                && countItem(cauldron.getOutput(), Items.EMERALD) == 0,
            "continuous contact reprocessed the cauldron output without a new impact"
        );
        anvil.setPos(anvil.position().add(0.0D, 0.25D, 0.0D));

        helper.runAfterDelay(1, () -> {
            anvil.setPos(helper.absoluteVec(new Vec3(3.5D, 3.0D, 3.5D)));
            cauldron.processAnvilImpact(anvil, Direction.DOWN);
            check(
                countItem(cauldron.getOutput(), Items.DIAMOND) == 0,
                "cauldron created the second result without consuming its first output"
            );
            check(countItem(cauldron.getOutput(), Items.EMERALD) == 1, "cauldron did not reprocess its output");
            cauldron.discard();
            anvil.discard();
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("9x7x9")
    @TestHolder(description = "A plastic pot reuses AnvilCraft recipes from either valid impact direction")
    static void plasticPotProcessesRecipesFromBothImpactDirections(ExtendedGameTestHelper helper) {
        HardenedResinCauldronEntity pot = createPot(helper, new Vec3(3.5D, 2.0D, 3.5D), PlasticEntityOrientation.DEFAULT);
        HardenedResinAnvilEntity anvil = createAnvil(
            helper,
            new Vec3(3.5D, 3.0D, 3.5D),
            PlasticEntityOrientation.DEFAULT
        );
        pot.setNoGravity(true);
        anvil.setNoGravity(true);

        pot.getInput().insertItem(0, new ItemStack(Items.STICK), false);
        pot.processAnvilImpact(anvil, Direction.EAST);
        check(pot.getInput().getStackInSlot(0).getCount() == 1, "a non-contact direction processed the recipe");

        pot.processAnvilImpact(anvil, Direction.DOWN);
        check(pot.getInput().getStackInSlot(0).isEmpty(), "anvil-down impact did not consume the pot input");
        check(countItem(pot.getOutput(), Items.DIAMOND) == 1, "anvil-down impact did not write the recipe output");
        anvil.setPos(anvil.position().add(0.0D, 0.25D, 0.0D));

        helper.runAfterDelay(1, () -> {
            anvil.setPos(helper.absoluteVec(new Vec3(3.5D, 3.0D, 3.5D)));
            pot.getInput().insertItem(0, new ItemStack(Items.STICK), false);
            pot.processAnvilImpact(anvil, Direction.UP);
            check(pot.getInput().getStackInSlot(0).isEmpty(), "pot-up impact did not consume the pot input");
            check(countItem(pot.getOutput(), Items.DIAMOND) == 2, "pot-up impact did not merge the recipe output");

            pot.discard();
            anvil.discard();
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x6x7")
    @TestHolder(description = "A resin anvil uses the same plastic-pot recipe path as hardened resin")
    static void resinAnvilProcessesPotRecipes(ExtendedGameTestHelper helper) {
        HardenedResinCauldronEntity pot = createPot(helper, new Vec3(3.5D, 1.0D, 3.5D), PlasticEntityOrientation.DEFAULT);
        ResinAnvilEntity anvil = createResinAnvil(
            helper,
            new Vec3(3.5D, 2.0D, 3.5D),
            ModBlocks.RESIN_ANVIL.asStack()
        );
        pot.setNoGravity(true);
        anvil.setNoGravity(true);
        pot.getInput().insertItem(0, new ItemStack(Items.STICK), false);

        pot.processAnvilImpact(anvil, Direction.DOWN);

        check(pot.getInput().getStackInSlot(0).isEmpty(), "resin anvil did not consume the pot input");
        check(countItem(pot.getOutput(), Items.DIAMOND) == 1, "resin anvil did not create the pot output");
        check(anvil.isAlive(), "ordinary pot processing consumed the resin anvil");
        pot.discard();
        anvil.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 60)
    @EmptyTemplate("7x8x7")
    @TestHolder(description = "A falling plastic anvil physically processes a plastic pot on impact")
    static void fallingPlasticAnvilProcessesPot(ExtendedGameTestHelper helper) {
        HardenedResinCauldronEntity pot = createPot(helper, new Vec3(3.5D, 1.0D, 3.5D), PlasticEntityOrientation.DEFAULT);
        pot.setNoGravity(true);
        pot.getInput().insertItem(0, new ItemStack(Items.STICK), false);
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(3.5D, 5.0D, 3.5D));

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

        HardenedResinCauldronEntity pot = createPot(helper, new Vec3(3.5D, 1.0D, 3.5D), PlasticEntityOrientation.DEFAULT);
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(3.5D, 4.0D, 3.5D));
        anvil.setNoGravity(true);
        pot.getInput().insertItem(0, new ItemStack(Items.STICK), false);
        check(
            Direction.getNearest(GravityManager.getNetGravityVectorForFallingBlock(pot)) == Direction.UP,
            "test source did not create upward pot gravity at " + potPosition
        );

        helper.runAfterDelay(40, () -> {
            check(pot.getInput().getStackInSlot(0).isEmpty(), "rising pot did not process its input against the anvil");
            int diamonds = countItem(pot.getOutput(), Items.DIAMOND);
            int emeralds = countItem(pot.getOutput(), Items.EMERALD);
            int looseDiamonds = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                pot.getBoundingBox().inflate(4.0D),
                item -> item.getItem().is(Items.DIAMOND)
            ).stream().mapToInt(item -> item.getItem().getCount()).sum();
            check(
                diamonds == 1,
                "rising pot did not retain the recipe output: diamonds=" + diamonds
                    + ", emeralds=" + emeralds
                    + ", looseDiamonds=" + looseDiamonds
            );
            check(anvil.isAlive(), "ordinary rising-pot processing consumed the plastic anvil");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("9x7x9")
    @TestHolder(description = "A side-attached plastic pot keeps the canonical recipe context")
    static void sideAttachedPlasticPotProcessesRecipes(ExtendedGameTestHelper helper) {
        PlasticEntityOrientation sideOrientation = new PlasticEntityOrientation(Direction.EAST, 0);
        HardenedResinCauldronEntity pot = createPot(helper, new Vec3(3.5D, 2.0D, 3.5D), sideOrientation);
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(4.5D, 2.0D, 3.5D), sideOrientation);
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
        PlasticEntityOrientation potOpenEast = new PlasticEntityOrientation(Direction.EAST, 0);
        PlasticEntityOrientation anvilBottomWest = new PlasticEntityOrientation(Direction.EAST, 0);
        HardenedResinCauldronEntity validPot = createPot(helper, new Vec3(3.5D, 2.0D, 3.5D), potOpenEast);
        HardenedResinAnvilEntity validAnvil = createAnvil(helper, new Vec3(5.0D, 2.0D, 3.5D), anvilBottomWest);
        validPot.setNoGravity(true);
        validAnvil.setNoGravity(true);
        validPot.getInput().insertItem(0, new ItemStack(Items.STICK), false);
        validAnvil.setDeltaMovement(-0.65D, 0.0D, 0.0D);

        HardenedResinCauldronEntity sideFacePot = createPot(helper, new Vec3(3.5D, 2.0D, 7.5D), potOpenEast);
        HardenedResinAnvilEntity sideFaceAnvil = createAnvil(
            helper,
            new Vec3(5.0D, 2.0D, 7.5D),
            new PlasticEntityOrientation(Direction.UP, 0)
        );
        sideFacePot.setNoGravity(true);
        sideFaceAnvil.setNoGravity(true);
        sideFacePot.getInput().insertItem(0, new ItemStack(Items.STICK), false);
        sideFaceAnvil.setDeltaMovement(-0.65D, 0.0D, 0.0D);

        HardenedResinCauldronEntity closedFacePot = createPot(
            helper,
            new Vec3(9.5D, 2.0D, 3.5D),
            new PlasticEntityOrientation(Direction.UP, 0)
        );
        HardenedResinAnvilEntity closedFaceAnvil = createAnvil(helper, new Vec3(11.0D, 2.0D, 3.5D), anvilBottomWest);
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
        HardenedResinCauldronEntity pot = createPot(helper, new Vec3(2.5D, 1.0D, 2.5D), PlasticEntityOrientation.DEFAULT);
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
        HardenedResinCauldronEntity pot = createPot(helper, new Vec3(2.5D, 2.0D, 2.5D), PlasticEntityOrientation.DEFAULT);
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 3.0D, 2.5D), PlasticEntityOrientation.DEFAULT);
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
        Vec3 anvilPosition = new Vec3(supportPosition.x, supportPosition.y - 0.98D, supportPosition.z);
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
        helper.setBlock(pos, ModBlocks.HARDEND_RESIN_ANVIL.get());
        helper.runAfterDelay(5, () -> {
            check(!helper.getBlockState(pos).is(ModBlocks.HARDEND_RESIN_ANVIL.get()), "compatibility block did not convert");
            helper.assertEntityPresent(ModEntities.HARDEND_RESIN_ANVIL.get(), pos, 1.5D);
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
    @TestHolder(description = "Carrier step-up cannot move through an anvil blocked by a low ceiling")
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
            support.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0.3D, 0.0D, 0.0D));
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
        HardenedResinAnvilEntity rear = createAnvil(helper, new Vec3(5.5D, 2.0D, 5.5D));
        HardenedResinCauldronEntity front = createPot(helper, new Vec3(6.49D, 2.0D, 5.5D), PlasticEntityOrientation.DEFAULT);
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
            new Vec3(anvil.getBbWidth() * 0.5D, anvil.getBbHeight() * 0.5D, 0.0D),
            InteractionHand.MAIN_HAND
        );
        check(blockResult.consumesAction(), "sneak-use did not place the held vanilla block");
        check(helper.getBlockState(new BlockPos(4, 2, 3)).is(Blocks.STONE), "vanilla block was not placed on the entity's east face");

        ItemStack plastic = ModBlocks.HARDEND_RESIN_ANVIL.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, plastic);
        InteractionResult entityResult = pot.interactAt(
            player,
            new Vec3(pot.getBbWidth() * 0.5D, pot.getBbHeight() * 0.5D, 0.0D),
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
            ModEntities.HARDEND_RESIN_ANVIL.get(),
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

    @GameTest(timeoutTicks = 50)
    @EmptyTemplate(value = "5x8x5", floor = true)
    @TestHolder(description = "A non-bottom face impact never posts an AnvilCraft landing recipe event")
    static void nonBottomImpactDoesNotFireLandingEvent(ExtendedGameTestHelper helper) {
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 5.0D, 2.5D));
        anvil.setOrientation(new PlasticEntityOrientation(Direction.DOWN, 0));
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
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 0.0D, 2.5D));
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
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 5.0D, 2.5D));
        helper.addTemporaryListener((AnvilEvent.OnLand event) -> {
            if (event.getEntity() == anvil) {
                event.setAnvilDamage(true);
            }
        });
        helper.runAfterDelay(35, () -> {
            check(!anvil.isAlive(), "landing-damage request left the plastic anvil alive");
            helper.assertItemEntityCountIsAtLeast(
                ModBlocks.HARDEND_RESIN_ANVIL.get().asItem(),
                new BlockPos(2, 1, 2),
                3.0D,
                0
            );
            check(
                helper.getLevel().getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class,
                    new AABB(helper.absolutePos(new BlockPos(2, 1, 2))).inflate(3.0D),
                    item -> item.getItem().is(ModBlocks.HARDEND_RESIN_ANVIL.get().asItem())
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
            support.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0.2D, 0.0D, 0.0D));
            check(support.getX() > supportStartX + 0.15D, "horizontal carrier was clipped by the supported anvil");
            check(anvil.getX() > anvilStartX + 0.15D, "horizontal carrier movement did not transfer to the anvil");
            helper.succeed();
        });
    }

    private static CraftingInput resinAnvilCraftingInput(ItemStack center) {
        ItemStack resinBlock = new ItemStack(dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK.get());
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

    private static ItemStack findCapturedResinAnvil(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModBlocks.RESIN_ANVIL.asItem()) && stack.has(ModComponents.SAVED_ENTITY)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ResinAnvilEntity createResinAnvil(
        ExtendedGameTestHelper helper,
        Vec3 relativeBottomCenter,
        ItemStack dropStack
    ) {
        ResinAnvilEntity anvil = new ResinAnvilEntity(
            ModEntities.RESIN_ANVIL.get(),
            helper.getLevel(),
            helper.absoluteVec(relativeBottomCenter),
            ModBlocks.RESIN_ANVIL.get().defaultBlockState(),
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
        EntityType<? extends ResinAnvilEntity> type = ModEntities.RESIN_ANVIL.get();
        Vec3 position = PlasticEntityOrientation.DEFAULT.entityPosition(
            helper.absolutePos(relativeCell),
            type.getWidth(),
            type.getHeight()
        );
        ResinAnvilEntity anvil = new ResinAnvilEntity(
            type,
            helper.getLevel(),
            position,
            ModBlocks.RESIN_ANVIL.get().defaultBlockState(),
            dropStack,
            PlasticEntityOrientation.DEFAULT
        );
        check(helper.getLevel().addFreshEntity(anvil), "failed to add resin anvil to the test level");
        return anvil;
    }

    private static HardenedResinAnvilEntity createAnvil(ExtendedGameTestHelper helper, Vec3 relativeBottomCenter) {
        return createAnvil(helper, relativeBottomCenter, PlasticEntityOrientation.DEFAULT);
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
            ModEntities.HARDEND_RESIN_ANVIL.get(),
            helper.getLevel(),
            position,
            ModBlocks.HARDEND_RESIN_ANVIL.get().defaultBlockState(),
            new ItemStack(ModBlocks.HARDEND_RESIN_ANVIL.get()),
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
            ModEntities.HARDEND_RESIN_CAULDRON.get(),
            helper.getLevel(),
            helper.absoluteVec(relativeBottomCenter),
            ModBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            ModBlocks.HARDEND_RESIN_CAULDRON.asStack(),
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
                ModBlocks.HARDEND_RESIN_ANVIL.get().defaultBlockState(),
                new ItemStack(ModBlocks.HARDEND_RESIN_ANVIL.get()),
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

        BlockState state = ModBlocks.HARDEND_RESIN_ANVIL.get()
            .defaultBlockState()
            .setValue(HardenedResinAnvilBlock.FACING, Direction.EAST);
        helper.setBlock(pos, state);
        helper.runAfterDelay(5, () -> {
            check(!helper.getBlockState(pos).is(ModBlocks.HARDEND_RESIN_ANVIL.get()), "compatibility block did not convert");
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

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
