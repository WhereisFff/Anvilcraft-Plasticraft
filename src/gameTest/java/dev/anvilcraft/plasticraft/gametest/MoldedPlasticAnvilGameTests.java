package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeContext;
import dev.anvilcraft.lib.v2.util.predicate.BlockStatePredicate;
import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.EngineeringPlasticEntity;
import dev.anvilcraft.plasticraft.entity.MoldedPlasticAnvilAbilities;
import dev.anvilcraft.plasticraft.entity.MoldedLargeCauldronInteraction;
import dev.anvilcraft.plasticraft.entity.PlasticCauldronWorkBlockFinder;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.inventory.HardenedResinAnvilMenu;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.anvilcraft.plasticraft.recipe.CauldronImpactRecipeProcessor;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.block.ModFluids;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.item.AnvilHammerItem;
import dev.dubhe.anvilcraft.recipe.anvil.predicate.block.HasAnvil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class MoldedPlasticAnvilGameTests {
    /** 木棍数量要多于大型锅的配方次数，剩余输入才能证明多余的通道没有空转。 */
    private static final int RECIPE_INPUT_COUNT = 12;

    private MoldedPlasticAnvilGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "Molded anvils satisfy general anvil predicates and open their plastic GUI")
    static void generalPredicateAndMenu(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity anvil = createProduct(
            helper,
            new BlockPos(3, 2, 3),
            PlasticEntityOrientation.DEFAULT,
            MoldingProductTypes.ANVIL_ID
        );
        anvil.setNoGravity(true);
        check(anvil.isMoldedAnvil(), "manufactured anvil entity lost its final type");

        InWorldRecipeContext context = new InWorldRecipeContext(helper.getLevel(), anvil.position(), anvil);
        check(HasAnvil.DEFAULT.test(context), "default HasAnvil predicate rejected the molded anvil");
        check(!HasAnvil.DEFAULT_INVERTED.test(context), "inverted default HasAnvil predicate accepted the molded anvil");
        check(
            new HasAnvil(BlockStatePredicate.builder().of(Blocks.ANVIL)).test(context),
            "vanilla-anvil predicate rejected the molded anvil"
        );
        check(!HasAnvil.frostOnly().test(context), "frost-only predicate accepted the molded anvil");

        var player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        helper.runAfterDelay(1, () -> {
            player.moveTo(anvil.getX(), anvil.getY(), anvil.getZ() - 2.0D);
            player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            InteractionResult interaction = anvil.interact(player, InteractionHand.MAIN_HAND);
            check(interaction.consumesAction(),
                "molded anvil interaction was not consumed: result=" + interaction
                    + ", molded=" + anvil.isMoldedAnvil()
                    + ", alive=" + anvil.isAlive()
                    + ", shift=" + player.isShiftKeyDown());
            check(player.containerMenu instanceof HardenedResinAnvilMenu,
                "molded anvil did not open its anvil menu");
            HardenedResinAnvilMenu menu = (HardenedResinAnvilMenu) player.containerMenu;
            check(menu.entityId() == anvil.getId(), "molded anvil menu targeted the wrong entity");
            check(menu.isPlasticAnvilTarget(), "molded anvil menu did not select the plastic texture layer");
            check(menu.stillValid(player), "molded anvil menu was immediately invalid");
            player.closeContainer();
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 50)
    @EmptyTemplate(value = "19x9x7", floor = true)
    @TestHolder(description = "Only bottom-down molded anvils publish landing events, one per cell their bottom covers")
    static void landingRequiresAnvilTypeAndBottomDownOrientation(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity bottomDown = createProduct(
            helper,
            new BlockPos(3, 6, 3),
            PlasticEntityOrientation.DEFAULT,
            MoldingProductTypes.ANVIL_ID
        );
        UniversalPlasticEntity ordinaryProduct = createProduct(
            helper,
            new BlockPos(7, 6, 3),
            PlasticEntityOrientation.DEFAULT,
            MoldingProductTypes.NORMAL_ID
        );
        UniversalPlasticEntity wallOriented = createProduct(
            helper,
            new BlockPos(11, 6, 3),
            new PlasticEntityOrientation(Direction.NORTH, 0),
            MoldingProductTypes.ANVIL_ID
        );
        // 40 x 40 底面横跨三格，只砸中中心格的旧行为会让边缘格的加工方块永远不触发。
        BlockPos giantCell = new BlockPos(15, 6, 3);
        UniversalPlasticEntity giant = createGiantProduct(helper, giantCell);
        AtomicInteger bottomDownEvents = new AtomicInteger();
        AtomicInteger ordinaryEvents = new AtomicInteger();
        AtomicInteger wallEvents = new AtomicInteger();
        Set<BlockPos> giantCells = new HashSet<>();
        helper.addTemporaryListener((AnvilEvent.OnLand event) -> {
            if (event.getEntity() == bottomDown) bottomDownEvents.incrementAndGet();
            if (event.getEntity() == ordinaryProduct) ordinaryEvents.incrementAndGet();
            if (event.getEntity() == wallOriented) wallEvents.incrementAndGet();
            if (event.getEntity() == giant) giantCells.add(event.getPos().immutable());
        });

        helper.runAfterDelay(35, () -> {
            check(bottomDownEvents.get() == 1,
                "single-cell molded anvil published " + bottomDownEvents.get() + " landing events");
            check(ordinaryEvents.get() == 0,
                "ordinary molded product published " + ordinaryEvents.get() + " landing events");
            check(wallEvents.get() == 0,
                "wall-oriented molded anvil published " + wallEvents.get() + " landing events");
            Set<BlockPos> expectedGiantCells = new HashSet<>();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    expectedGiantCells.add(helper.absolutePos(
                        new BlockPos(giantCell.getX() + x, 2, giantCell.getZ() + z)
                    ));
                }
            }
            check(giantCells.equals(expectedGiantCells),
                "giant molded anvil struck " + giantCells + " instead of " + expectedGiantCells);
            check(bottomDown.isAlive() && ordinaryProduct.isAlive() && wallOriented.isAlive() && giant.isAlive(),
                "landing unexpectedly removed a molded product");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "11x6x7", floor = true)
    @TestHolder(description = "Engineering molded anvils use Silk Touch while universal anvils keep normal drops")
    static void engineeringAnvilUsesRoyalSilkTouchBreaking(ExtendedGameTestHelper helper) {
        BlockPos universalTarget = new BlockPos(3, 2, 3);
        BlockPos engineeringTarget = new BlockPos(7, 2, 3);
        helper.setBlock(universalTarget.below(), Blocks.STONECUTTER);
        helper.setBlock(engineeringTarget.below(), Blocks.STONECUTTER);
        helper.setBlock(universalTarget, Blocks.GLASS);
        helper.setBlock(engineeringTarget, Blocks.GLASS);

        UniversalPlasticEntity universal = createProduct(
            helper,
            new BlockPos(2, 4, 3),
            PlasticEntityOrientation.DEFAULT,
            MoldingProductTypes.ANVIL_ID
        );
        EngineeringPlasticEntity engineering = createEngineeringProduct(
            helper,
            new BlockPos(8, 4, 3),
            PlasticEntityOrientation.DEFAULT,
            MoldingProductTypes.ANVIL_ID
        );
        universal.setNoGravity(true);
        engineering.setNoGravity(true);

        check(handleGiantLanding(helper, universal, universalTarget.above(), 2.0F),
            "universal anvil did not consume its stonecutter break");
        check(handleGiantLanding(helper, engineering, engineeringTarget.above(), 2.0F),
            "engineering anvil did not consume its stonecutter break");
        check(helper.getBlockState(universalTarget).isAir(), "universal anvil did not break glass");
        check(helper.getBlockState(engineeringTarget).isAir(), "engineering anvil did not break glass");

        int universalGlass = helper.getLevel().getEntitiesOfClass(
            ItemEntity.class,
            new AABB(helper.absolutePos(universalTarget)).inflate(1.0D),
            item -> item.getItem().is(Blocks.GLASS.asItem())
        ).stream().mapToInt(item -> item.getItem().getCount()).sum();
        int engineeringGlass = helper.getLevel().getEntitiesOfClass(
            ItemEntity.class,
            new AABB(helper.absolutePos(engineeringTarget)).inflate(1.0D),
            item -> item.getItem().is(Blocks.GLASS.asItem())
        ).stream().mapToInt(item -> item.getItem().getCount()).sum();
        check(universalGlass == 0, "universal anvil unexpectedly used Silk Touch");
        check(engineeringGlass == 1, "engineering anvil did not produce one Silk Touch glass drop");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "An exact 40 x 40 bottom grants molded giant-anvil ability")
    static void exactFortyPixelBottomIsGiant(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity anvil = createGiantProduct(helper, new BlockPos(3, 2, 3));
        anvil.setNoGravity(true);
        check(anvil.isMoldedAnvil(), "40 x 40 molded product lost its anvil type");
        check(anvil.isMoldedGiantAnvil(), "exact 40 x 40 bottom did not grant giant-anvil ability");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "13x9x13", floor = true)
    @TestHolder(description = "A molded giant anvil crafts a 3 x 3 x 3 coal multiblock")
    static void giantAnvilCraftsMultiblock(ExtendedGameTestHelper helper) {
        BlockPos platformCenter = new BlockPos(7, 5, 7);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = -3; y <= -1; y++) {
                    helper.setBlock(platformCenter.offset(x, y, z), Blocks.COAL_BLOCK);
                }
                helper.setBlock(
                    platformCenter.offset(x, 0, z),
                    x == 0 && z == 0 ? ModBlocks.SPACE_OVERCOMPRESSOR.get() : Blocks.CRAFTING_TABLE
                );
            }
        }
        UniversalPlasticEntity anvil = createGiantProduct(helper, new BlockPos(2, 7, 2));
        anvil.setNoGravity(true);

        boolean consumed = handleGiantLanding(helper, anvil, platformCenter.above(), 4.0F);
        check(!consumed, "multiblock landing was incorrectly consumed as a Large Cauldron impact");
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = -3; y <= -1; y++) {
                    check(helper.getBlockState(platformCenter.offset(x, y, z)).isAir(),
                        "coal multiblock input was not consumed");
                }
            }
        }
        BlockPos absoluteDropCenter = helper.absolutePos(platformCenter.below());
        int diamonds = helper.getLevel().getEntitiesOfClass(
            ItemEntity.class,
            new AABB(absoluteDropCenter).inflate(2.0D),
            entity -> entity.getItem().is(Blocks.DIAMOND_BLOCK.asItem())
        ).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
        check(diamonds == 1, "coal multiblock produced " + diamonds + " diamond blocks");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "13x9x13", floor = true)
    @TestHolder(description = "A molded giant anvil converts the large-cake multiblock")
    static void giantAnvilConvertsMultiblock(ExtendedGameTestHelper helper) {
        BlockPos platformCenter = new BlockPos(7, 5, 7);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                helper.setBlock(platformCenter.offset(x, 0, z), Blocks.CRAFTING_TABLE);
                helper.setBlock(platformCenter.offset(x, -3, z), ModBlocks.CAKE_BLOCK.get());
            }
        }
        helper.setBlock(platformCenter.offset(0, -2, 0), ModBlocks.BERRY_CAKE_BLOCK.get());
        helper.setBlock(platformCenter.offset(-1, -2, 0), ModBlocks.BERRY_CAKE_BLOCK.get());
        helper.setBlock(platformCenter.offset(1, -2, 0), ModBlocks.BERRY_CAKE_BLOCK.get());
        helper.setBlock(platformCenter.offset(0, -2, -1), ModBlocks.BERRY_CAKE_BLOCK.get());
        helper.setBlock(platformCenter.offset(0, -2, 1), ModBlocks.BERRY_CAKE_BLOCK.get());
        helper.setBlock(platformCenter.offset(0, -1, 0), ModBlocks.CHOCOLATE_CAKE_BLOCK.get());
        UniversalPlasticEntity anvil = createGiantProduct(helper, new BlockPos(2, 7, 2));
        anvil.setNoGravity(true);

        handleGiantLanding(helper, anvil, platformCenter.above(), 4.0F);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = -3; y <= -1; y++) {
                    check(helper.getBlockState(platformCenter.offset(x, y, z)).is(ModBlocks.LARGE_CAKE),
                        "large-cake conversion left an incorrect multiblock part");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "13x6x13", floor = true)
    @TestHolder(description = "A molded giant anvil triggers a Heavy Iron Block ground shock")
    static void giantAnvilTriggersGroundShock(ExtendedGameTestHelper helper) {
        BlockPos shockCenter = new BlockPos(6, 1, 6);
        helper.setBlock(shockCenter, ModBlocks.HEAVY_IRON_BLOCK.get());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            helper.setBlock(shockCenter.relative(direction), Blocks.ANVIL);
        }
        for (int x : List.of(-1, 1)) {
            for (int z : List.of(-1, 1)) {
                helper.setBlock(shockCenter.offset(x, 0, z), Blocks.OBSIDIAN);
            }
        }
        BlockPos target = shockCenter.above().east(2);
        helper.setBlock(target, Blocks.STONE);
        UniversalPlasticEntity anvil = createGiantProduct(helper, new BlockPos(2, 4, 2));
        anvil.setNoGravity(true);

        handleGiantLanding(helper, anvil, shockCenter.above(), 3.0F);
        check(helper.getBlockState(target).isAir(), "molded giant anvil did not trigger ground-shock mining");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "13x10x13", floor = true)
    @TestHolder(description = "A molded giant anvil processes fluid mixing in a Large Cauldron")
    static void giantAnvilProcessesLargeCauldron(ExtendedGameTestHelper helper) {
        BlockPos cauldronBase = new BlockPos(7, 1, 7);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, cauldronBase);
        check(cauldron.getFluids().fill(
            new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), 1000),
            IFluidHandler.FluidAction.EXECUTE
        ) == 1000, "failed to fill plastic melt into the Large Cauldron");
        check(cauldron.getFluids().fill(
            new FluidStack(Fluids.WATER, 1000),
            IFluidHandler.FluidAction.EXECUTE
        ) == 1000, "failed to fill water into the Large Cauldron");
        UniversalPlasticEntity anvil = createGiantProduct(helper, new BlockPos(2, 7, 2));
        anvil.setNoGravity(true);

        boolean consumed = handleGiantLanding(helper, anvil, cauldronBase.above(3), 4.0F);
        check(consumed, "Large Cauldron impact did not consume the ordinary landing event");
        check(cauldron.getFluids().getTotalAmount() == 0, "Large Cauldron retained mixed input fluids");
        check(outputCount(cauldron, PlasticraftBlocks.UNIVERSAL_PLASTIC.asItem()) == 0,
            "Large Cauldron produced plastic blocks instead of granules");
        check(outputCount(cauldron, PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE.get()) == 16,
            "Large Cauldron did not produce 16 universal plastic granules");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "13x8x13", floor = true)
    @TestHolder(description = "One large cauldron impact processes at most nine normal stacks without duplicating inputs or reprocessing fresh outputs")
    static void moldedCauldronRecipePassesFollowTier(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity anvil = createProduct(
            helper,
            new BlockPos(1, 6, 1),
            PlasticEntityOrientation.DEFAULT,
            MoldingProductTypes.ANVIL_ID
        );
        anvil.setNoGravity(true);
        UniversalPlasticEntity ordinary = createCauldronProduct(
            helper,
            new BlockPos(2, 2, 2),
            17,
            12,
            12,
            MoldingProductTypes.CAULDRON_ID
        );
        UniversalPlasticEntity large = createCauldronProduct(
            helper,
            new BlockPos(7, 2, 7),
            2,
            41,
            8,
            MoldingProductTypes.LARGE_CAULDRON_ID
        );
        loadCauldronInput(ordinary);
        large.getItemHandler().insertItem(32, new ItemStack(Items.STICK, 576), false);

        CauldronImpactRecipeProcessor.process(helper.getLevel(), anvil, ordinary);
        CauldronImpactRecipeProcessor.process(helper.getLevel(), anvil, large);

        checkRecipePasses(ordinary, "ordinary");
        check(countItem(large.getItemHandler(), Items.STICK) == 0, "large cauldron failed to consume nine stacks of input");
        check(countItem(large.getItemHandler(), Items.DIAMOND) == 576, "large cauldron lost or duplicated its nine batches");
        check(countItem(large.getItemHandler(), Items.EMERALD) == 0, "large cauldron reprocessed fresh outputs during the same impact");
        CauldronImpactRecipeProcessor.process(helper.getLevel(), anvil, large);
        check(countItem(large.getItemHandler(), Items.DIAMOND) == 576, "duplicate landing cells processed the same pot twice");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x8x9", floor = true)
    @TestHolder(description = "Large cauldron floor cells independently insert and extract their inputs in every orientation and persist oversized stacks")
    static void largeCauldronInteractionCellsAndPersistence(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity pot = createCauldronProduct(helper, new BlockPos(4, 2, 4), 2, 41, 32, MoldingProductTypes.LARGE_CAULDRON_ID);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Item[] items = {Items.IRON_INGOT, Items.COPPER_INGOT, Items.COAL, Items.CHARCOAL,
            Items.COBBLESTONE, Items.DIRT, Items.SAND, Items.GRAVEL};
        for (Direction opening : Direction.values()) {
            for (int turn = 0; turn < 4; turn++) {
                pot.setOrientation(new PlasticEntityOrientation(opening, turn));
                for (int slot = 0; slot < 8; slot++) {
                    Vec3 point = MoldedLargeCauldronInteraction.localPoint(pot, MoldedLargeCauldronInteraction.inputPosition(pot, slot));
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(items[slot], 64));
                    check(clickLarge(pot, player, point, point.add(0, 2, 0)).consumesAction(), "large pot rejected floor insertion at " + opening + "/" + turn + "/" + slot);
                    check(pot.getItemHandler().getStackInSlot(32 + slot).getCount() == 64, "floor click selected the wrong input cell");
                }
                pot.insertRecipeOutput(new ItemStack(Items.DIAMOND, 3));
                for (int slot = 0; slot < 8; slot++) {
                    Vec3 point = MoldedLargeCauldronInteraction.localPoint(pot, MoldedLargeCauldronInteraction.inputPosition(pot, slot));
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    clickLarge(pot, player, point, point.add(0, 2, 0));
                    check(player.getMainHandItem().is(items[slot]) && player.getMainHandItem().getCount() == 64, "floor cell did not return its own input to the hand");
                    check(countItem(pot.getItemHandler(), Items.DIAMOND) == 3, "input extraction also removed output items");
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                Vec3 center = MoldedLargeCauldronInteraction.localPoint(pot, MoldedLargeCauldronInteraction.inputPosition(pot, -1));
                clickLarge(pot, player, center, center.add(0, 2, 0));
                check(player.getMainHandItem().is(Items.DIAMOND) && player.getMainHandItem().getCount() == 3, "center floor did not return outputs");
            }
        }
        pot.setOrientation(PlasticEntityOrientation.DEFAULT);
        pot.getItemHandler().insertItem(32, new ItemStack(Items.IRON_INGOT, 576), false);
        CompoundTag saved = pot.saveWithoutId(new CompoundTag());
        pot.discard();
        UniversalPlasticEntity loaded = new UniversalPlasticEntity(PlasticraftEntities.UNIVERSAL_PLASTIC.get(), helper.getLevel());
        loaded.load(saved);
        check(loaded.getItemHandler().getStackInSlot(32).getCount() == 576, "saving oversized large-cauldron input lost its count");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Vec3 input = MoldedLargeCauldronInteraction.localPoint(loaded, MoldedLargeCauldronInteraction.inputPosition(loaded, 0));
        clickLarge(loaded, player, input, input.add(0, 2, 0));
        check(countInventoryItem(player, Items.IRON_INGOT) == 576, "oversized input extraction lost inventory stacks");
        check(loaded.getItemHandler().getStackInSlot(32).isEmpty(), "oversized input extraction left items behind");
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x8x9", floor = true)
    @TestHolder(description = "Large cauldron automation separates input sides from the output bottom and fluid containers select layers by height")
    static void largeCauldronAutomationAndFluidInteractions(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity pot = createCauldronProduct(helper, new BlockPos(4, 2, 4), 2, 41, 32, MoldingProductTypes.LARGE_CAULDRON_ID);
        IItemHandler top = pot.getCapability(Capabilities.ItemHandler.ENTITY_AUTOMATION, Direction.UP);
        IItemHandler bottom = pot.getCapability(Capabilities.ItemHandler.ENTITY_AUTOMATION, Direction.DOWN);
        check(top != null && bottom != null, "large pot did not expose sided automation");
        check(top.insertItem(0, new ItemStack(Items.IRON_INGOT), false).isEmpty(), "top automation rejected input");
        check(top.extractItem(0, 64, false).isEmpty(), "top automation extracted an input");
        check(bottom.insertItem(0, new ItemStack(Items.DIRT), false).getCount() == 1, "bottom automation inserted into outputs");
        pot.insertRecipeOutput(new ItemStack(Items.DIAMOND, 2));
        check(bottom.extractItem(0, 64, false).getCount() == 2, "bottom automation could not extract outputs");
        pot.getMoldedFluidHandler().fill(new FluidStack(Fluids.WATER, 32_000), IFluidHandler.FluidAction.EXECUTE);
        pot.getMoldedFluidHandler().fill(new FluidStack(Fluids.LAVA, 16_000), IFluidHandler.FluidAction.EXECUTE);
        check(pot.getCapability(Capabilities.FluidHandler.ENTITY, Direction.UP).drain(1000, IFluidHandler.FluidAction.SIMULATE).is(Fluids.LAVA), "top fluid capability did not expose top layer");
        check(pot.getCapability(Capabilities.FluidHandler.ENTITY, Direction.DOWN).drain(1000, IFluidHandler.FluidAction.SIMULATE).is(Fluids.WATER), "bottom fluid capability did not expose bottom layer");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        AABB bounds = pot.plasticraft$getGeometry().localBounds();
        AABB cavity = MoldedLargeCauldronInteraction.cavity(pot);
        Vec3 low = new Vec3(bounds.minX, cavity.minY + cavity.getYsize() * 16000 / 512000, bounds.getCenter().z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        clickLarge(pot, player, low, low.add(-2, 0, 0));
        check(player.getMainHandItem().is(Items.WATER_BUCKET), "low side bucket drained the wrong layer");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        Vec3 high = new Vec3(bounds.minX, cavity.maxY, bounds.getCenter().z);
        clickLarge(pot, player, high, high.add(-2, 0, 0));
        check(player.getMainHandItem().is(Items.LAVA_BUCKET), "high side bucket drained the wrong layer");
        pot.getMoldedFluidHandler().discardFluids();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        clickLarge(pot, player, high, high.add(-2, 0, 0));
        check(countItem(pot.getItemHandler(), Items.BUCKET) == 0, "failed fluid interaction inserted its bucket into input");
        pot.getMoldedFluidHandler().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        pot.getMoldedFluidHandler().fill(new FluidStack(PlasticraftFluids.HIGH_HEAT_FUEL.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        clickLarge(pot, player, high, high.add(-2, 0, 0));
        check(pot.anvilcraft$isIgnited(), "large pot could not ignite fuel above water");
        pot.getMoldedFluidHandler().fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
        pot.plasticraft$tickBonded();
        check(!pot.anvilcraft$isIgnited(), "covering fuel with a nonflammable layer did not extinguish the large pot");
        player.setItemInHand(InteractionHand.MAIN_HAND, ModBlocks.MENGER_SPONGE.asStack());
        clickLarge(pot, player, high, high.add(-2, 0, 0));
        check(pot.getMoldedFluidHandler().copyFluids().isEmpty(), "Menger sponge did not empty all fluid layers");
        BlockPos bondedPos = pot.blockPosition();
        helper.getLevel().setBlock(bondedPos, pot.getDisplayState().setValue(AbstractPlasticEntityBlock.BONDED, true), Block.UPDATE_ALL);
        BondedEntityBlockEntity bonded = (BondedEntityBlockEntity) helper.getLevel().getBlockEntity(bondedPos);
        check(bonded != null && bonded.initialize(pot, pot.getDisplayState(), Direction.UP, PlasticEntityOrientation.DEFAULT, true), "large pot could not enter bonded form");
        pot.discard();
        UniversalPlasticEntity fixed = (UniversalPlasticEntity) bonded.getOrCreateRenderEntity();
        fixed.insertRecipeOutput(new ItemStack(Items.DIAMOND, 3));
        IItemHandler bondedBottom = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, bondedPos, Direction.DOWN);
        check(bondedBottom != null && countItem(bondedBottom, Items.IRON_INGOT) == 0, "bonded bottom exposed input inventory");
        check(countItem(bondedBottom, Items.DIAMOND) == 3, "bonded bottom did not expose output inventory");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Vec3 fixedCenter = MoldedLargeCauldronInteraction.inputPosition(fixed, -1);
        player.moveTo(fixedCenter.add(0, 2, 0));
        check(bonded.interact(player, InteractionHand.MAIN_HAND, new BlockHitResult(fixedCenter, Direction.UP, bondedPos, false)).consumesAction(), "bonded floor did not accept an output extraction click");
        check(player.getMainHandItem().is(Items.DIAMOND) && player.getMainHandItem().getCount() == 3, "bonded floor click lost output items");
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x8x9", floor = true)
    @TestHolder(description = "Large molded cauldron mixing preserves melt color and leaves fluids intact when outputs cannot fit")
    static void largeCauldronMixingPreservesResources(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity pot = createCauldronProduct(helper, new BlockPos(4, 2, 4), 2, 41, 8, MoldingProductTypes.LARGE_CAULDRON_ID);
        UniversalPlasticEntity anvil = createProduct(helper, new BlockPos(1, 6, 1), PlasticEntityOrientation.DEFAULT, MoldingProductTypes.ANVIL_ID);
        FluidStack melt = new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), 1000);
        PlasticMeltColor.set(melt, DyeColor.BLUE);
        pot.getMoldedFluidHandler().fill(melt, IFluidHandler.FluidAction.EXECUTE);
        pot.getMoldedFluidHandler().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        for (int slot = 0; slot < 32; slot++) pot.getMoldedItemHandler().setStackInSlot(slot, new ItemStack(Items.BARRIER, 64));
        CauldronImpactRecipeProcessor.process(helper.getLevel(), anvil, pot);
        check(pot.getMoldedFluidHandler().copyFluids().stream().mapToInt(FluidStack::getAmount).sum() == 2000, "full output consumed mixing fluids");
        pot.getMoldedItemHandler().setStackInSlot(0, ItemStack.EMPTY);
        UniversalPlasticEntity nextAnvil = createProduct(helper, new BlockPos(2, 6, 1), PlasticEntityOrientation.DEFAULT, MoldingProductTypes.ANVIL_ID);
        CauldronImpactRecipeProcessor.process(helper.getLevel(), nextAnvil, pot);
        check(pot.getMoldedFluidHandler().copyFluids().isEmpty(), "large plastic pot did not consume the mixing inputs");
        ItemStack result = pot.getItemHandler().getStackInSlot(0);
        check(result.is(PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE.get()) && result.getCount() == 16, "large plastic pot mixing produced the wrong output");
        check(PlasticMeltColor.get(result) == DyeColor.BLUE, "large plastic pot mixing lost the melt color");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x8x9", floor = true)
    @TestHolder(description = "Large molded cauldrons perform liquid enchantment without consuming unrelated layers")
    static void largeCauldronLiquidEnchantment(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity pot = createCauldronProduct(helper, new BlockPos(4, 2, 4), 2, 41, 8, MoldingProductTypes.LARGE_CAULDRON_ID);
        UniversalPlasticEntity anvil = createProduct(helper, new BlockPos(1, 6, 1), PlasticEntityOrientation.DEFAULT, MoldingProductTypes.ANVIL_ID);
        pot.getMoldedFluidHandler().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        pot.getMoldedFluidHandler().fill(new FluidStack(ModFluids.EXP_FLUID.get(), 2000), IFluidHandler.FluidAction.EXECUTE);
        pot.getItemHandler().insertItem(32, new ItemStack(Items.LAPIS_LAZULI, 3), false);
        CauldronImpactRecipeProcessor.process(helper.getLevel(), anvil, pot);
        check(countItem(pot.getItemHandler(), Items.LAPIS_LAZULI) == 0, "liquid enchantment did not consume lapis");
        check(pot.getMoldedFluidHandler().drain(new FluidStack(ModFluids.LIQUID_ENCHANTMENT.get(), 1), IFluidHandler.FluidAction.SIMULATE).getAmount() == 1, "large plastic pot did not create blank liquid enchantment");
        check(pot.getMoldedFluidHandler().getBottomFluid().is(Fluids.WATER) && pot.getMoldedFluidHandler().getBottomFluid().getAmount() == 1000, "liquid enchantment consumed an unrelated layer");
        helper.succeed();
    }

    private static InteractionResult clickLarge(UniversalPlasticEntity pot, Player player, Vec3 point, Vec3 eye) {
        Vec3 world = pot.plasticraft$getGeometry().worldPointAt(pot.position(), pot.getOrientation(), point);
        Vec3 worldEye = pot.plasticraft$getGeometry().worldPointAt(pot.position(), pot.getOrientation(), eye);
        player.moveTo(worldEye.add(0, -player.getEyeHeight(), 0));
        return pot.interactAt(player, world.subtract(pot.position()), InteractionHand.MAIN_HAND);
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "13x8x9", floor = true)
    @TestHolder(description = "Large cauldron recipes roll back failed branches, consume nonbottom layers and commit multiple fluids atomically")
    static void largeCauldronLayeredRecipeTransactions(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity anvil = createProduct(helper, new BlockPos(1, 6, 1), PlasticEntityOrientation.DEFAULT, MoldingProductTypes.ANVIL_ID);
        for (boolean full : new boolean[]{false, true}) {
            UniversalPlasticEntity pot = createCauldronProduct(helper, new BlockPos(full ? 9 : 4, 2, 4), 2, 41, 8, MoldingProductTypes.LARGE_CAULDRON_ID);
            // 不足一桶的经验不会触发本体经验宝石配方，保留它作为事务无关层。
            pot.getMoldedFluidHandler().fill(new FluidStack(ModFluids.EXP_FLUID.get(), 500), IFluidHandler.FluidAction.EXECUTE);
            pot.getMoldedFluidHandler().fill(new FluidStack(ModFluids.OIL.get(), full ? 64_000 : 500), IFluidHandler.FluidAction.EXECUTE);
            pot.getMoldedFluidHandler().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
            pot.getItemHandler().insertItem(32, new ItemStack(Items.PRISMARINE_SHARD), false);
            CauldronImpactRecipeProcessor.process(helper.getLevel(), anvil, pot);
            check(countItem(pot.getItemHandler(), Items.PRISMARINE_SHARD) == (full ? 1 : 0), "layered recipe consumed the wrong input count with full=" + full);
            check(countItem(pot.getItemHandler(), Items.NETHER_STAR) == (full ? 0 : 1), "layered recipe did not commit its output with full=" + full);
            check(pot.getMoldedFluidHandler().drain(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.SIMULATE).getAmount() == 1000, "failed branch or full-layer check lost water with full=" + full);
            check(pot.getMoldedFluidHandler().drain(new FluidStack(ModFluids.OIL.get(), 64_000), IFluidHandler.FluidAction.SIMULATE).getAmount() == (full ? 64_000 : 1500), "layered recipe duplicated or lost a fluid output with full=" + full);
            check(pot.getMoldedFluidHandler().getBottomFluid().is(ModFluids.EXP_FLUID.get())
                && pot.getMoldedFluidHandler().getBottomFluid().getAmount() == 500, "layered recipe consumed unrelated experience fluid with full=" + full);
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x8x9", floor = true)
    @TestHolder(description = "Large-cauldron output reprocessing preserves fresh items merged into a preexisting output slot")
    static void largeCauldronReprocessingPreservesFreshOutputs(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity pot = createCauldronProduct(helper, new BlockPos(4, 2, 4), 2, 41, 8, MoldingProductTypes.LARGE_CAULDRON_ID);
        UniversalPlasticEntity anvil = createProduct(helper, new BlockPos(1, 6, 1), PlasticEntityOrientation.DEFAULT, MoldingProductTypes.ANVIL_ID);
        pot.insertRecipeOutput(new ItemStack(Items.DIAMOND));
        pot.getItemHandler().insertItem(32, new ItemStack(Items.STICK), false);
        CauldronImpactRecipeProcessor.process(helper.getLevel(), anvil, pot);
        check(countItem(pot.getItemHandler(), Items.STICK) == 0, "large pot skipped inputs while reprocessing outputs");
        check(countItem(pot.getItemHandler(), Items.DIAMOND) == 1, "large pot reprocessed or overwrote a fresh merged output");
        check(countItem(pot.getItemHandler(), Items.EMERALD) == 1, "large pot did not reprocess exactly the preexisting output");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x8x9", floor = true)
    @TestHolder(description = "Large plastic cauldrons absorb opening sources, extinguish entities in water and provide climbable walls")
    static void largeCauldronEnvironmentalBehavior(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity pot = createCauldronProduct(helper, new BlockPos(4, 2, 4), 2, 41, 32, MoldingProductTypes.LARGE_CAULDRON_ID);
        AABB bounds = pot.getBoundingBox();
        BlockPos source = BlockPos.containing(bounds.getCenter().x, bounds.maxY, bounds.getCenter().z);
        helper.getLevel().setBlock(source, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        pot.plasticraft$tickBonded();
        check(helper.getLevel().getFluidState(source).isEmpty() && pot.getMoldedFluidHandler().getBottomFluid().getAmount() == 1000, "large pot failed to absorb its opening water source");
        pot.getMoldedFluidHandler().fill(new FluidStack(Fluids.WATER, 63_000), IFluidHandler.FluidAction.EXECUTE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 floor = MoldedLargeCauldronInteraction.inputPosition(pot, -1);
        player.moveTo(floor);
        helper.getLevel().addFreshEntity(player);
        player.igniteForSeconds(8);
        pot.plasticraft$tickBonded();
        check(!player.isOnFire() && pot.getMoldedFluidHandler().getBottomFluid().getAmount() == 63_750, "large-pot water did not extinguish the player for 250 mB");
        player.moveTo(bounds.minX - player.getBbWidth() / 2.0, floor.y + 0.05, bounds.getCenter().z);
        check(player.onClimbable(), "large plastic pot wall was not climbable");
        player.moveTo(floor.add(0, 0.3, 0));
        check(!player.onClimbable(), "large plastic pot allowed climbing in midair away from walls");
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "13x8x7", floor = true)
    @TestHolder(description = "Molded cauldrons use processing blocks below their complete physical bottom for Anvil Hammer recipes")
    static void moldedCauldronRecipesUseBottomWorkBlock(ExtendedGameTestHelper helper) {
        BlockPos shortWork = new BlockPos(3, 2, 3);
        BlockPos tallWork = new BlockPos(9, 2, 3);
        BlockState litCampfire = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
        helper.setBlock(shortWork, litCampfire);
        helper.setBlock(shortWork.east(), Blocks.STONE);
        helper.setBlock(tallWork, litCampfire);

        UniversalPlasticEntity shortPot = createCauldronProduct(
            helper,
            shortWork,
            17,
            12,
            8,
            MoldingProductTypes.CAULDRON_ID
        );
        UniversalPlasticEntity tallPot = createCauldronProduct(
            helper,
            tallWork,
            17,
            12,
            32,
            MoldingProductTypes.CAULDRON_ID
        );
        placeCauldronBottomOnWorkBlock(shortPot, helper.absolutePos(shortWork));
        placeCauldronBottomOnWorkBlock(tallPot, helper.absolutePos(tallWork));
        AABB shortBounds = shortPot.plasticraft$getCollisionBox().bounds();
        double centerX = helper.absolutePos(shortWork).getX() + 1.25D;
        shortPot.setPos(shortPot.position().add(centerX - shortBounds.getCenter().x, 0.0D, 0.0D));
        shortPot.setStartPos(shortPot.blockPosition());
        shortBounds = shortPot.plasticraft$getCollisionBox().bounds();
        check(
            BlockPos.containing(shortBounds.getCenter()).equals(helper.absolutePos(shortWork.east())),
            "9 px molded cauldron center did not move above the non-processing block"
        );
        check(
            shortBounds.minX < helper.absolutePos(shortWork).getX() + 1.0D
                && shortBounds.maxX > helper.absolutePos(shortWork).getX() + 1.0D,
            "9 px molded cauldron no longer covered the campfire at its bottom edge"
        );
        check(
            PlasticCauldronWorkBlockFinder.findWorkBlockPositions(shortPot).contains(helper.absolutePos(shortWork)),
            "9 px molded cauldron did not expose the edge campfire as a work-block candidate"
        );

        Player shortPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        Player tallPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        processCampfireResinRecipe(
            shortPlayer,
            shortPot,
            helper.absolutePos(shortWork),
            "9 px cauldron with an edge campfire",
            false
        );
        processCampfireResinRecipe(
            tallPlayer,
            tallPot,
            helper.absolutePos(tallWork),
            "tall cauldron",
            true
        );
        shortPlayer.discard();
        tallPlayer.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x7x9", floor = true)
    @TestHolder(description = "An Anvil Hammer impact processes only the struck molded cauldron when multiple share a campfire cell")
    static void moldedCauldronHammerProcessesOnlyStruckEntity(ExtendedGameTestHelper helper) {
        BlockPos impactCell = new BlockPos(4, 2, 4);
        BlockPos workBlock = impactCell.below();
        helper.setBlock(
            workBlock,
            Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true)
        );
        for (int x = impactCell.getX() - 1; x <= impactCell.getX() + 1; x++) {
            for (int z = impactCell.getZ() - 1; z <= impactCell.getZ() + 1; z++) {
                if (x != impactCell.getX() || z != impactCell.getZ()) {
                    helper.setBlock(new BlockPos(x, workBlock.getY(), z), Blocks.AIR);
                }
            }
        }
        List<UniversalPlasticEntity> pots = List.of(
            createCauldronProduct(helper, impactCell, 17, 12, 12, MoldingProductTypes.CAULDRON_ID),
            createCauldronProduct(helper, impactCell, 17, 12, 12, MoldingProductTypes.CAULDRON_ID),
            createCauldronProduct(helper, impactCell, 17, 12, 12, MoldingProductTypes.CAULDRON_ID),
            createCauldronProduct(helper, impactCell, 17, 12, 12, MoldingProductTypes.CAULDRON_ID)
        );
        List<Vec3> offsets = List.of(
            new Vec3(-0.51D, 0.0D, -0.51D),
            new Vec3(0.51D, 0.0D, -0.51D),
            new Vec3(-0.51D, 0.0D, 0.51D),
            new Vec3(0.51D, 0.0D, 0.51D)
        );
        AABB impactBounds = new AABB(helper.absolutePos(impactCell));
        for (int index = 0; index < pots.size(); index++) {
            UniversalPlasticEntity pot = pots.get(index);
            placeCauldronBottomOnWorkBlock(pot, helper.absolutePos(workBlock));
            pot.setPos(pot.position().add(offsets.get(index)));
            pot.setStartPos(pot.blockPosition());
            check(pot.getBoundingBox().intersects(impactBounds),
                "molded cauldron " + index + " did not enter the shared anvil landing cell");
            int firstInput = pot.plasticraft$cauldronLayout().outputSlots();
            check(pot.getItemHandler().insertItem(firstInput, new ItemStack(ModItems.RESIN.get(), 64), false).isEmpty(),
                "molded cauldron " + index + " rejected resin input");
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.ANVIL_HAMMER.asStack());
        UniversalPlasticEntity struck = pots.getFirst();
        player.attack(struck);

        for (int index = 0; index < pots.size(); index++) {
            UniversalPlasticEntity pot = pots.get(index);
            if (pot == struck) {
                check(countItem(pot.getItemHandler(), ModItems.RESIN.get()) == 0,
                    "struck molded cauldron retained resin after the hammer impact");
                check(countItem(pot.getItemHandler(), ModItems.HARDEND_RESIN.get()) == 64,
                    "struck molded cauldron did not turn its full resin stack into hardened resin");
            } else {
                check(countItem(pot.getItemHandler(), ModItems.RESIN.get()) == 64,
                    "unstruck molded cauldron " + index + " was processed by the hammer impact");
                check(countItem(pot.getItemHandler(), ModItems.HARDEND_RESIN.get()) == 0,
                    "unstruck molded cauldron " + index + " produced hardened resin");
            }
            pot.discard();
        }
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "11x6x7", floor = true)
    @TestHolder(description = "Molded cauldrons accept held items and fluids, survive hammer impacts, and return only items on pickup")
    static void moldedCauldronPlayerInteractionAndHammerRecovery(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity recipePot = createCauldronProduct(
            helper,
            new BlockPos(3, 2, 3),
            17,
            12,
            12,
            MoldingProductTypes.CAULDRON_ID
        );
        Player recipePlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        recipePlayer.moveTo(recipePot.position().add(0.0D, 0.0D, -2.0D));
        recipePlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        check(recipePot.interact(recipePlayer, InteractionHand.MAIN_HAND).consumesAction(),
            "right-clicking the molded cauldron did not insert the held item");
        check(recipePlayer.getMainHandItem().isEmpty(), "molded cauldron insertion did not consume the held stick");
        check(countItem(recipePot.getItemHandler(), Items.STICK) == 1,
            "molded cauldron did not retain the inserted stick");

        ItemStack recipeHammer = ModItems.ANVIL_HAMMER.asStack();
        recipePlayer.setItemInHand(InteractionHand.MAIN_HAND, recipeHammer);
        recipePlayer.attack(recipePot);
        check(recipePot.isAlive(), "anvil hammer attack destroyed the molded cauldron");
        check(countItem(recipePot.getItemHandler(), Items.STICK) == 0,
            "anvil hammer attack did not consume the molded cauldron recipe input");
        check(countItem(recipePot.getItemHandler(), Items.DIAMOND) == 1,
            "anvil hammer attack did not return the recipe output to the molded cauldron");
        check(recipeHammer.getDamageValue() == 1, "molded cauldron impact consumed the wrong hammer durability");

        recipePlayer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(recipePot.interact(recipePlayer, InteractionHand.MAIN_HAND).consumesAction(),
            "empty-hand interaction did not retrieve the molded cauldron output");
        check(countInventoryItem(recipePlayer, Items.DIAMOND) == 1,
            "empty-hand interaction did not return the molded cauldron output to the player");
        recipePot.discard();
        recipePlayer.discard();

        UniversalPlasticEntity recoveryPot = createCauldronProduct(
            helper,
            new BlockPos(7, 2, 3),
            17,
            12,
            12,
            MoldingProductTypes.CAULDRON_ID
        );
        BlockPos recoveryCell = CauldronImpactRecipeProcessor.recipePotCell(recoveryPot);
        Player recoveryPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        recoveryPlayer.moveTo(recoveryPot.position().add(0.0D, 0.0D, -2.0D));
        recoveryPlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        check(recoveryPot.interact(recoveryPlayer, InteractionHand.MAIN_HAND).consumesAction(),
            "water bucket did not interact with the molded cauldron");
        check(recoveryPot.getFluidHandler().getFluidInTank(0).is(Fluids.WATER)
                && recoveryPot.getFluidHandler().getFluidInTank(0).getAmount() == 1000,
            "molded cauldron did not retain one bucket of water");

        recoveryPlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 64));
        check(recoveryPot.interact(recoveryPlayer, InteractionHand.MAIN_HAND).consumesAction(),
            "right-clicking the filled molded cauldron did not insert iron ingots");
        check(recoveryPlayer.getMainHandItem().isEmpty(), "molded cauldron did not consume the inserted iron stack");
        check(countItem(recoveryPot.getItemHandler(), Items.IRON_INGOT) == 64,
            "molded cauldron did not retain the inserted iron stack");

        ItemStack recoveryHammer = ModItems.ANVIL_HAMMER.asStack();
        recoveryPlayer.setItemInHand(InteractionHand.MAIN_HAND, recoveryHammer);
        recoveryPlayer.attack(recoveryPot);
        check(recoveryPot.isAlive(), "hammering a filled molded cauldron destroyed it");
        check(countItem(recoveryPot.getItemHandler(), Items.IRON_INGOT) == 64,
            "a non-matching hammer impact removed molded cauldron items");
        check(recoveryPot.getFluidHandler().getFluidInTank(0).is(Fluids.WATER)
                && recoveryPot.getFluidHandler().getFluidInTank(0).getAmount() == 1000,
            "a non-matching hammer impact removed molded cauldron fluid");

        recoveryPlayer.setShiftKeyDown(true);
        check(recoveryPot.interact(recoveryPlayer, InteractionHand.MAIN_HAND).consumesAction(),
            "sneak-using an anvil hammer did not recover the molded cauldron");
        check(!recoveryPot.isAlive(), "recovered molded cauldron remained in the level");
        check(countInventoryItem(recoveryPlayer, Items.IRON_INGOT) == 64,
            "hammer recovery did not return every molded cauldron item");
        check(helper.getLevel().getFluidState(recoveryCell).isEmpty(),
            "hammer recovery placed the discarded cauldron water into the world");

        ItemStack recovered = findMoldedProduct(recoveryPlayer, MoldingProductTypes.CAULDRON_ID);
        MoldedPlasticData recoveredData = MoldedPlasticData.get(recovered).orElseThrow(() ->
            new GameTestAssertException("hammer recovery did not return a molded cauldron item")
        );
        check(recoveredData.storageId().isEmpty(), "empty recovered cauldron retained an external storage id");
        check(recoveredData.contents().items().isEmpty() && recoveredData.contents().fluids().isEmpty(),
            "recovered cauldron item retained items or fluid");
        recoveryPlayer.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x6x7", floor = true)
    @TestHolder(description = "Molded cauldron outlets, ignition, fluid contents and orientation survive entity persistence")
    static void moldedCauldronOutletAndPersistence(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity pot = createCauldronProduct(
            helper,
            new BlockPos(4, 2, 3),
            17,
            12,
            12,
            MoldingProductTypes.CAULDRON_ID
        );
        helper.setBlock(new BlockPos(5, 2, 3), Blocks.CHEST);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(pot.position().add(0.0D, 0.0D, -2.0D));
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.ANVIL_HAMMER.asStack());
        check(pot.plasticraft$useAnvilHammer(player, InteractionHand.MAIN_HAND, Direction.EAST).consumesAction(),
            "anvil hammer did not open a molded cauldron outlet");
        check(pot.getOutletDirection() == Direction.EAST, "molded cauldron outlet opened on the wrong side");
        check(pot.insertRecipeOutput(new ItemStack(Items.DIAMOND, 3)).isEmpty(),
            "molded cauldron rejected recipe output with an open outlet");
        check(helper.getBlockEntity(new BlockPos(5, 2, 3)) instanceof Container chest
                && countContainerItem(chest, Items.DIAMOND) == 3,
            "molded cauldron outlet did not transfer output into the adjacent chest");

        check(pot.getFluidHandler().fill(
            new FluidStack(PlasticraftFluids.HIGH_HEAT_FUEL.get(), 1000),
            IFluidHandler.FluidAction.EXECUTE
        ) == 1000, "molded cauldron rejected high-heat fuel");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        check(pot.interact(player, InteractionHand.MAIN_HAND).consumesAction(),
            "flint and steel did not interact with the molded cauldron");
        check(pot.anvilcraft$isIgnited(), "flint and steel did not ignite the molded cauldron");

        CompoundTag saved = pot.saveWithoutId(new CompoundTag());
        pot.discard();
        UniversalPlasticEntity loaded = new UniversalPlasticEntity(
            PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
            helper.getLevel()
        );
        loaded.load(saved);
        check(loaded.isMoldedCauldron(), "persisted molded cauldron lost its product type");
        check(loaded.getOutletDirection() == Direction.EAST, "molded cauldron outlet did not survive persistence");
        check(loaded.anvilcraft$isIgnited(), "molded cauldron ignition did not survive persistence");
        check(loaded.plasticraft$bottomFluid().is(PlasticraftFluids.HIGH_HEAT_FUEL.get())
                && loaded.plasticraft$bottomFluid().getAmount() == 1000,
            "molded cauldron fluid did not survive persistence");
        loaded.getMoldedFluidHandler().discardFluids();
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "A bonded molded cauldron accepts held items and processes them through an anvil-hammer block impact")
    static void bondedMoldedCauldronProcessesHammerImpact(ExtendedGameTestHelper helper) {
        BlockPos relativePos = new BlockPos(3, 2, 3);
        BlockPos pos = helper.absolutePos(relativePos);
        UniversalPlasticEntity source = createCauldronProduct(
            helper,
            relativePos,
            17,
            12,
            12,
            MoldingProductTypes.CAULDRON_ID
        );
        BlockState fixedState = source.getDisplayState().setValue(AbstractPlasticEntityBlock.BONDED, true);
        helper.setBlock(relativePos, fixedState);
        check(helper.getLevel().getBlockEntity(pos) instanceof BondedEntityBlockEntity,
            "bonded molded cauldron block entity was not created");
        BondedEntityBlockEntity bonded = (BondedEntityBlockEntity) helper.getLevel().getBlockEntity(pos);
        check(bonded.initialize(
            source,
            source.getDisplayState(),
            Direction.UP,
            PlasticEntityOrientation.DEFAULT,
            true
        ), "bonded molded cauldron could not capture its entity");
        source.discard();

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(pos.getCenter().add(0.0D, 0.0D, -2.0D));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        BlockHitResult hit = new BlockHitResult(pos.getCenter(), Direction.UP, pos, false);
        check(bonded.interact(player, InteractionHand.MAIN_HAND, hit).consumesAction(),
            "bonded molded cauldron did not accept the held stick");
        IItemHandler items = bonded.getItemHandler();
        check(items != null && countItem(items, Items.STICK) == 1,
            "bonded molded cauldron did not retain the inserted stick");

        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.ANVIL_HAMMER.asStack());
        check(AnvilHammerItem.dropAnvil(player, helper.getLevel(), pos),
            "anvil hammer did not publish a block impact for the bonded molded cauldron");
        items = bonded.getItemHandler();
        check(items != null && countItem(items, Items.STICK) == 0,
            "bonded molded cauldron did not consume its recipe input");
        check(items != null && countItem(items, Items.DIAMOND) == 1,
            "bonded molded cauldron did not retain its recipe output");
        check(helper.getBlockState(relativePos).is(PlasticraftBlocks.UNIVERSAL_PLASTIC.get())
                && helper.getBlockState(relativePos).getValue(AbstractPlasticEntityBlock.BONDED),
            "anvil-hammer processing removed the bonded molded cauldron block");

        clearItems(items);
        player.discard();
        helper.succeed();
    }

    /** 完整底面加四壁、仅顶面开放；`interiorSize` 同时决定开口面积，因此也决定是否升级为大型锅。 */
    private static UniversalPlasticEntity createCauldronProduct(
        ExtendedGameTestHelper helper,
        BlockPos occupiedPos,
        int min,
        int interiorSize,
        int wallHeight,
        ResourceLocation expectedType
    ) {
        int outer = min + interiorSize + 2;
        int innerMin = min + 1;
        int innerMax = min + interiorSize + 1;
        int top = 1 + wallHeight;
        EditableMoldingModel model = new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Molded cauldron impact test",
            MoldingProductTypes.CAULDRON_ID,
            List.of(
                MoldingElement.cube("Floor", vec(min, 0, min), vec(outer, 1, outer)),
                MoldingElement.cube("West", vec(min, 1, min), vec(innerMin, top, outer)),
                MoldingElement.cube("East", vec(innerMax, 1, min), vec(outer, top, outer)),
                MoldingElement.cube("North", vec(innerMin, 1, min), vec(innerMax, top, innerMin)),
                MoldingElement.cube("South", vec(innerMin, 1, innerMax), vec(innerMax, top, outer))
            ),
            List.of()
        );
        var baked = MoldingModelBaker.bake(model);
        int melt = baked.analysis().minimumMeltMillibuckets();
        MoldedPlasticData data = MoldedPlasticData.manufacture(
            model,
            baked,
            new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), melt),
            melt
        );
        // 开口够大的模型出锅即为大型锅，因此这里断言最终类型而不是请求类型。
        check(expectedType.equals(data.finalType()),
            "molded cauldron became " + data.finalType() + " instead of " + expectedType);
        UniversalPlasticEntity pot = createProduct(helper, occupiedPos, PlasticEntityOrientation.DEFAULT, data);
        pot.setNoGravity(true);
        check(pot.isMoldedCauldron(), "manufactured cauldron entity lost its cauldron behavior");
        return pot;
    }

    private static void loadCauldronInput(UniversalPlasticEntity pot) {
        // 槽序是「先输出后输入」，第一个输入槽的编号正好等于输出槽数量。
        int firstInput = pot.plasticraft$cauldronLayout().outputSlots();
        ItemStack rejected = pot.getItemHandler().insertItem(
            firstInput,
            new ItemStack(Items.STICK, RECIPE_INPUT_COUNT),
            false
        );
        check(rejected.isEmpty(), "molded cauldron rejected " + rejected.getCount() + " of its recipe inputs");
    }

    private static void placeCauldronBottomOnWorkBlock(UniversalPlasticEntity pot, BlockPos workBlock) {
        double lift = workBlock.getY() + 7.0D / 16.0D - pot.getBoundingBox().minY;
        pot.setPos(pot.position().add(0.0D, lift, 0.0D));
        pot.setStartPos(pot.blockPosition());
    }

    private static void processCampfireResinRecipe(
        Player player,
        UniversalPlasticEntity pot,
        BlockPos workBlock,
        String name,
        boolean verifyPrimaryWorkBlock
    ) {
        if (verifyPrimaryWorkBlock) {
            check(CauldronImpactRecipeProcessor.recipePotCell(pot).equals(workBlock.above()),
                name + " did not map its physical-bottom campfire to the recipe work position");
        }
        int firstInput = pot.plasticraft$cauldronLayout().outputSlots();
        ItemStack rejected = pot.getItemHandler().insertItem(firstInput, ModItems.RESIN.asStack(), false);
        check(rejected.isEmpty(), name + " rejected its resin recipe input");
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.ANVIL_HAMMER.asStack());
        player.attack(pot);
        check(countItem(pot.getItemHandler(), ModItems.RESIN.get()) == 0,
            name + " retained resin after the campfire hammer recipe");
        check(countItem(pot.getItemHandler(), ModItems.HARDEND_RESIN.get()) == 1,
            name + " did not produce hardened resin from the campfire hammer recipe");
    }

    /** 测试配方每次执行只把一根木棍换成一颗钻石，因此产出数量就是这次撞击实际执行的配方次数。 */
    private static void checkRecipePasses(UniversalPlasticEntity pot, String tier) {
        int passes = pot.plasticraft$cauldronLayout().recipePasses();
        int diamonds = countItem(pot.getItemHandler(), Items.DIAMOND);
        int sticks = countItem(pot.getItemHandler(), Items.STICK);
        check(diamonds == passes,
            tier + " molded cauldron ran " + diamonds + " recipe passes instead of " + passes);
        check(sticks == RECIPE_INPUT_COUNT - passes,
            tier + " molded cauldron left " + sticks + " inputs instead of " + (RECIPE_INPUT_COUNT - passes));
    }

    private static UniversalPlasticEntity createProduct(
        ExtendedGameTestHelper helper,
        BlockPos occupiedPos,
        PlasticEntityOrientation orientation,
        ResourceLocation type
    ) {
        MoldedPlasticData data = manufacture(type);
        ItemStack stack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(stack, data);
        UniversalPlasticEntity entity = new UniversalPlasticEntity(
            PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
            helper.getLevel(),
            Vec3.ZERO,
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            stack,
            orientation
        );
        Vec3 position = entity.plasticraft$placementPosition(helper.absolutePos(occupiedPos), orientation);
        entity.setPos(position);
        entity.setStartPos(entity.blockPosition());
        check(helper.getLevel().addFreshEntity(entity), "failed to add molded plastic product");
        return entity;
    }

    private static UniversalPlasticEntity createGiantProduct(
        ExtendedGameTestHelper helper,
        BlockPos occupiedPos
    ) {
        return createProduct(
            helper,
            occupiedPos,
            PlasticEntityOrientation.DEFAULT,
            manufactureGiantAnvil()
        );
    }

    private static EngineeringPlasticEntity createEngineeringProduct(
        ExtendedGameTestHelper helper,
        BlockPos occupiedPos,
        PlasticEntityOrientation orientation,
        ResourceLocation type
    ) {
        MoldedPlasticData data = manufacture(type, PlasticraftFluids.ENGINEERING_PLASTIC_MELT.get());
        ItemStack stack = PlasticraftBlocks.ENGINEERING_PLASTIC.asStack();
        MoldedPlasticData.set(stack, data);
        EngineeringPlasticEntity entity = new EngineeringPlasticEntity(
            PlasticraftEntities.ENGINEERING_PLASTIC.get(),
            helper.getLevel(),
            Vec3.ZERO,
            PlasticraftBlocks.ENGINEERING_PLASTIC.get().defaultBlockState(),
            stack,
            orientation
        );
        Vec3 position = entity.plasticraft$placementPosition(helper.absolutePos(occupiedPos), orientation);
        entity.setPos(position);
        entity.setStartPos(entity.blockPosition());
        check(helper.getLevel().addFreshEntity(entity), "failed to add engineering molded product");
        return entity;
    }

    private static UniversalPlasticEntity createProduct(
        ExtendedGameTestHelper helper,
        BlockPos occupiedPos,
        PlasticEntityOrientation orientation,
        MoldedPlasticData data
    ) {
        ItemStack stack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(stack, data);
        UniversalPlasticEntity entity = new UniversalPlasticEntity(
            PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
            helper.getLevel(),
            Vec3.ZERO,
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            stack,
            orientation
        );
        Vec3 position = entity.plasticraft$placementPosition(helper.absolutePos(occupiedPos), orientation);
        entity.setPos(position);
        entity.setStartPos(entity.blockPosition());
        check(helper.getLevel().addFreshEntity(entity), "failed to add molded plastic product");
        return entity;
    }

    private static MoldedPlasticData manufacture(ResourceLocation type) {
        return manufacture(type, PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get());
    }

    private static MoldedPlasticData manufacture(ResourceLocation type, Fluid material) {
        return manufacture(new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Molded anvil behavior test",
            type,
            List.of(
                MoldingElement.cube("Bottom", vec(1, 0, 1), vec(17, 3, 17)),
                MoldingElement.cube("Middle", vec(4, 3, 4), vec(14, 6, 14)),
                MoldingElement.cube("Top", vec(0, 6, 0), vec(18, 11, 18))
            ),
            List.of()
        ), material);
    }

    private static MoldedPlasticData manufactureGiantAnvil() {
        return manufacture(new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Exact 40 x 40 molded giant anvil test",
            MoldingProductTypes.ANVIL_ID,
            List.of(
                MoldingElement.cube("Bottom", vec(4, 0, 4), vec(44, 3, 44)),
                MoldingElement.cube("Middle", vec(8, 3, 8), vec(40, 6, 40)),
                MoldingElement.cube("Top", vec(3, 6, 3), vec(45, 11, 45))
            ),
            List.of()
        ));
    }

    private static MoldedPlasticData manufacture(EditableMoldingModel model) {
        return manufacture(model, PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get());
    }

    private static MoldedPlasticData manufacture(EditableMoldingModel model, Fluid material) {
        var baked = MoldingModelBaker.bake(model);
        int melt = baked.analysis().minimumMeltMillibuckets();
        MoldedPlasticData data = MoldedPlasticData.manufacture(
            model,
            baked,
            new FluidStack(material, melt),
            melt
        );
        check(model.requestedType().equals(data.finalType()),
            "manufactured product changed type from " + model.requestedType() + " to " + data.finalType());
        return data;
    }

    private static boolean handleGiantLanding(
        ExtendedGameTestHelper helper,
        UniversalPlasticEntity anvil,
        BlockPos landingPos,
        float fallDistance
    ) {
        BlockPos absoluteLandingPos = helper.absolutePos(landingPos);
        MoldedPlasticAnvilAbilities.handleLandingOnce(anvil, absoluteLandingPos, fallDistance);
        return MoldedPlasticAnvilAbilities.handleLanding(
            anvil,
            new AnvilEvent.OnLand(helper.getLevel(), absoluteLandingPos, anvil, fallDistance)
        );
    }

    private static LargeCauldronBlockEntity placeLargeCauldron(
        ExtendedGameTestHelper helper,
        BlockPos base
    ) {
        Level level = helper.getLevel();
        LargeCauldronBlock block = ModBlocks.LARGE_CAULDRON.get();
        BlockPos absoluteBase = helper.absolutePos(base);
        BlockState state = block.defaultBlockState();
        level.setBlock(absoluteBase, state, Block.UPDATE_ALL);
        block.setPlacedBy(level, absoluteBase, state, null, ItemStack.EMPTY);
        if (!(level.getBlockEntity(absoluteBase.above()) instanceof LargeCauldronBlockEntity cauldron)) {
            throw new GameTestAssertException("Large Cauldron main block entity was not created");
        }
        return cauldron;
    }

    private static int outputCount(LargeCauldronBlockEntity cauldron, Item item) {
        return countItem(cauldron.getOutputHandler(), item);
    }

    private static int countItem(IItemHandler handler, Item item) {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static int countInventoryItem(Player player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static int countContainerItem(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static ItemStack findMoldedProduct(Player player, ResourceLocation type) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (MoldedPlasticData.get(stack).filter(data -> type.equals(data.finalType())).isPresent()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void clearItems(IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            while (!handler.extractItem(slot, Integer.MAX_VALUE, false).isEmpty()) {
                // 大型锅输入槽允许超过一个原版堆叠，必须抽到该槽为空。
            }
        }
    }

    private static MoldingVec3 vec(double x, double y, double z) {
        return new MoldingVec3(x, y, z);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
