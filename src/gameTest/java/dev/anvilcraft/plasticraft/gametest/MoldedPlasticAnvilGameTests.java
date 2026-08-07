package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeContext;
import dev.anvilcraft.lib.v2.util.predicate.BlockStatePredicate;
import dev.anvilcraft.plasticraft.entity.MoldedPlasticAnvilAbilities;
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
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.recipe.anvil.predicate.block.HasAnvil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class MoldedPlasticAnvilGameTests {
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
    @EmptyTemplate(value = "15x9x7", floor = true)
    @TestHolder(description = "Only a bottom-down molded anvil publishes one AnvilCraft landing event")
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
        AtomicInteger bottomDownEvents = new AtomicInteger();
        AtomicInteger ordinaryEvents = new AtomicInteger();
        AtomicInteger wallEvents = new AtomicInteger();
        helper.addTemporaryListener((AnvilEvent.OnLand event) -> {
            if (event.getEntity() == bottomDown) bottomDownEvents.incrementAndGet();
            if (event.getEntity() == ordinaryProduct) ordinaryEvents.incrementAndGet();
            if (event.getEntity() == wallOriented) wallEvents.incrementAndGet();
        });

        helper.runAfterDelay(35, () -> {
            check(bottomDownEvents.get() == 1,
                "bottom-down molded anvil published " + bottomDownEvents.get() + " landing events");
            check(ordinaryEvents.get() == 0,
                "ordinary molded product published " + ordinaryEvents.get() + " landing events");
            check(wallEvents.get() == 0,
                "wall-oriented molded anvil published " + wallEvents.get() + " landing events");
            check(bottomDown.isAlive() && ordinaryProduct.isAlive() && wallOriented.isAlive(),
                "landing unexpectedly removed a molded product");
            helper.succeed();
        });
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
        ));
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
        var baked = MoldingModelBaker.bake(model);
        int melt = baked.analysis().minimumMeltMillibuckets();
        MoldedPlasticData data = MoldedPlasticData.manufacture(
            model,
            baked,
            new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), melt),
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
        return MoldedPlasticAnvilAbilities.handleLanding(
            anvil,
            new AnvilEvent.OnLand(helper.getLevel(), helper.absolutePos(landingPos), anvil, fallDistance)
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
        int count = 0;
        for (int slot = 0; slot < cauldron.getOutputHandler().getSlots(); slot++) {
            ItemStack stack = cauldron.getOutputHandler().getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static MoldingVec3 vec(double x, double y, double z) {
        return new MoldingVec3(x, y, z);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
