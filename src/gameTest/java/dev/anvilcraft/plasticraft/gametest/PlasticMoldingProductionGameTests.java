package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberStructure;
import dev.anvilcraft.plasticraft.block.PlasticMoldingMachineState;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveBondingService;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockEntities;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPowerBridge;
import dev.anvilcraft.plasticraft.molding.machine.MoldingProcessSnapshot;
import dev.anvilcraft.plasticraft.molding.machine.MoldingProductionMode;
import dev.anvilcraft.plasticraft.molding.machine.MoldingWaitReason;
import dev.anvilcraft.plasticraft.molding.machine.PlasticMoldingAnvilProcessor;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.event.giantanvil.GiantAnvilLandingEventListener;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

public final class PlasticMoldingProductionGameTests {
    private static final double EPSILON = 1.0E-8;
    private static final int STAGING_RESERVE = 137;

    private PlasticMoldingProductionGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "5x5x5", floor = true)
    @TestHolder(description = "A molded 16x16x14 cuboid uses the cooled universal plastic entity and geometry contract")
    static void standardMoldMatchesCooledPlastic(ExtendedGameTestHelper helper) {
        EditableMoldingModel model = model(
            "Standard Universal Plastic",
            cube("Body", 16.0, 0.0, 16.0, 32.0, 14.0, 32.0)
        );
        MoldedPlasticData data = fullData(model, DyeColor.WHITE);
        BlockState displayState = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState();
        ItemStack cooledStack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        ItemStack moldedStack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(moldedStack, data);
        UniversalPlasticEntity cooled = new UniversalPlasticEntity(
            PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
            helper.getLevel(),
            Vec3.ZERO,
            displayState,
            cooledStack,
            PlasticEntityOrientation.DEFAULT
        );
        UniversalPlasticEntity molded = new UniversalPlasticEntity(
            PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
            helper.getLevel(),
            Vec3.ZERO,
            displayState,
            moldedStack,
            PlasticEntityOrientation.DEFAULT
        );
        check(cooled.getClass() == molded.getClass(), "standard molded product used a different entity class");
        check(cooled.getType() == molded.getType(), "standard molded product used a different entity type");
        check(
            molded.getName().getString().equals(model.name()),
            "molded entity did not expose its real model name"
        );
        check(
            moldedStack.getHoverName().getString().equals(model.name()),
            "molded item did not expose its real model name"
        );
        PlasticEntityGeometry cooledGeometry = cooled.plasticraft$getGeometry();
        PlasticEntityGeometry moldedGeometry = molded.plasticraft$getGeometry();
        for (Direction face : Direction.values()) {
            for (int turn = 0; turn < 4; turn++) {
                PlasticEntityOrientation orientation = new PlasticEntityOrientation(face, turn);
                check(
                    !Shapes.joinIsNotEmpty(
                        cooledGeometry.collisionBoxAt(Vec3.ZERO, orientation).shape(),
                        moldedGeometry.collisionBoxAt(Vec3.ZERO, orientation).shape(),
                        BooleanOp.NOT_SAME
                    ),
                    "standard molded product collision differed at " + face + "/" + turn
                );
                check(
                    !Shapes.joinIsNotEmpty(
                        cooledGeometry.interactionShapeAt(Vec3.ZERO, orientation),
                        moldedGeometry.interactionShapeAt(Vec3.ZERO, orientation),
                        BooleanOp.NOT_SAME
                    ),
                    "standard molded product interaction shape differed at " + face + "/" + turn
                );
                check(
                    cooledGeometry.rotationCenterAt(Vec3.ZERO)
                        .distanceToSqr(moldedGeometry.rotationCenterAt(Vec3.ZERO)) <= EPSILON,
                    "standard molded product rotation center differed at " + face + "/" + turn
                );
            }
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "13x11x13", floor = true)
    @TestHolder(description = "Entities preserve 24 orientations while placement, recovery, and pick-block use the standard item direction")
    static void allOrientationsAndBondedPersistence(ExtendedGameTestHelper helper) {
        EditableMoldingModel shapeModel = model("Orientation Marker", cube("Marker", 21.0, 22.0, 19.0, 27.0, 26.0, 29.0));
        MoldedPlasticData baseData = fullData(shapeModel, DyeColor.MAGENTA);
        BlockPos support = new BlockPos(6, 5, 6);
        BlockPos absoluteSupport = helper.absolutePos(support);
        helper.setBlock(support, Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(6.5, 5.5, 2.5)));
        for (Direction face : Direction.values()) {
            ItemStack stack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
            MoldedPlasticData.set(stack, baseData);
            PlasticMeltColor.set(stack, DyeColor.MAGENTA);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockHitResult hit = new BlockHitResult(absoluteSupport.getCenter(), face, absoluteSupport, false);
            InteractionResult placement = stack.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
            check(placement.consumesAction(), "dynamic product placement failed for " + String.valueOf(face));
            UniversalPlasticEntity entity = onlyPlasticEntity(helper, new AABB(absoluteSupport).inflate(4.0));
            entity.setNoGravity(true);
            PlasticEntityOrientation placed = PlasticEntityOrientation.forPlacement(face, player);
            check(entity.getOrientation().equals(placed), "molded item did not use standard universal-plastic placement for " + face);
            check(entity.getMoldedData().orElseThrow().orientation().equals(PlasticEntityOrientation.DEFAULT), "synchronized product item data retained its placed orientation");
            for (int turn = 0; turn < 4; ++turn) {
                PlasticEntityOrientation orientation = new PlasticEntityOrientation(face, turn);
                entity.setOrientation(orientation);
                CompoundTag saved = entity.saveWithoutId(new CompoundTag());
                UniversalPlasticEntity loaded = new UniversalPlasticEntity(PlasticraftEntities.UNIVERSAL_PLASTIC.get(), helper.getLevel());
                loaded.load(saved);
                check(loaded.getOrientation().equals(orientation), "saved product entity lost orientation " + face + "/" + turn);
                check(loaded.getMoldedData().orElseThrow().orientation().equals(PlasticEntityOrientation.DEFAULT), "saved product item data retained orientation " + face + "/" + turn);
                ItemStack recovered = entity.getDropStack();
                ItemStack picked = entity.getPickResult();
                check(MoldedPlasticData.get(recovered).orElseThrow().orientation().equals(PlasticEntityOrientation.DEFAULT), "rotated product recovery did not reset to the default item direction");
                check(ItemStack.isSameItemSameComponents(recovered, picked), "pick-block result differed from the product's destruction drop");
                loaded.discard();
            }
            entity.discard();
        }
        EditableMoldingModel planeModel = model("Hammer Plane", cube("Plane", 16.0, 24.0, 16.0, 32.0, 24.0, 32.0));
        MoldedPlasticData planeData = fullData(planeModel, DyeColor.YELLOW);
        ItemStack planeStack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(planeStack, planeData);
        PlasticMeltColor.set(planeStack, DyeColor.YELLOW);
        player.setItemInHand(InteractionHand.MAIN_HAND, planeStack);
        InteractionResult planePlacement = planeStack.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, planeStack, new BlockHitResult(absoluteSupport.getCenter(), Direction.UP, absoluteSupport, false)));
        check(planePlacement.consumesAction(), "zero-thickness product could not be placed");
        UniversalPlasticEntity plane = onlyPlasticEntity(helper, new AABB(absoluteSupport).inflate(4.0));
        plane.setNoGravity(true);
        check(plane.plasticraft$getCollisionBox().shape().isEmpty(), "placed zero-thickness product gained physical collision");
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, PlasticraftItems.RESIN_ANVIL_HAMMER.asStack());
        check(plane.interact(player, InteractionHand.MAIN_HAND).consumesAction(), "hammer could not recover a zero-thickness product");
        ItemStack hammered = findInventoryStack(player, PlasticraftBlocks.UNIVERSAL_PLASTIC.asItem());
        check(MoldedPlasticData.get(hammered).isPresent(), "hammer recovery lost zero-thickness product data");
        player.setShiftKeyDown(false);
        PlasticEntityOrientation bondedOrientation = new PlasticEntityOrientation(Direction.EAST, 3);
        MoldedPlasticData bondedData = baseData;
        ItemStack bondedStack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(bondedStack, bondedData);
        PlasticMeltColor.set(bondedStack, DyeColor.MAGENTA);
        BlockState displayState = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState()
            .setValue(DyeableMaterial.COLOR, DyeColor.MAGENTA);
        UniversalPlasticEntity bondedProduct = new UniversalPlasticEntity(PlasticraftEntities.UNIVERSAL_PLASTIC.get(), helper.getLevel(), helper.absoluteVec(new Vec3(3.5, 4.0, 3.5)), displayState, bondedStack, bondedOrientation);
        BlockPos bondedPos = new BlockPos(4, 4, 4);
        helper.setBlock(bondedPos.west(), Blocks.STONE);
        BlockState bondedState = displayState.setValue(AbstractPlasticEntityBlock.BONDED, true);
        helper.setBlock(bondedPos, bondedState);
        BlockEntity blockEntity = helper.getBlockEntity(bondedPos);
        if (!(blockEntity instanceof BondedEntityBlockEntity bonded)) {
            throw new GameTestAssertException("bonded dynamic product block entity was missing");
        }
        check(bonded.initialize(bondedProduct, displayState, Direction.EAST, bondedOrientation, false), "dynamic product could not initialize bonded storage");
        UniversalPlasticEntity renderEntity = (UniversalPlasticEntity)bonded.getOrCreateRenderEntity();
        check(
            renderEntity.getMoldedData().orElseThrow().equals(bondedData),
            "bonded dynamic product lost its synchronized shape"
        );
        CompoundTag bondedTag = bonded.saveWithoutMetadata(helper.getLevel().registryAccess());
        BondedEntityBlockEntity loadedBonded = new BondedEntityBlockEntity(PlasticraftBlockEntities.BONDED_ENTITY.get(), bonded.getBlockPos(), bondedState);
        loadedBonded.setLevel(helper.getLevel());
        loadedBonded.loadWithComponents(bondedTag, helper.getLevel().registryAccess());
        check(loadedBonded.getPlasticOrientation().equals(bondedOrientation), "bonded persistence lost the 24-way orientation");
        Entity entity = loadedBonded.getOrCreateRenderEntity();
        check(entity instanceof UniversalPlasticEntity loadedRender
            && loadedRender.getMoldedData().orElseThrow().equals(bondedData), "bonded persistence lost dynamic product data");
        ItemStack bondedClone = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().getCloneItemStack(
            bondedState,
            new BlockHitResult(helper.absolutePos(bondedPos).getCenter(), Direction.UP, helper.absolutePos(bondedPos), false),
            helper.getLevel(),
            helper.absolutePos(bondedPos),
            player
        );
        check(MoldedPlasticData.get(bondedClone).orElseThrow().equals(baseData), "pick-block on a bonded product returned the generic universal-plastic block");
        check(helper.getLevel().destroyBlock(helper.absolutePos(bondedPos), true, player), "bonded product could not be destroyed for drop verification");
        ItemStack bondedDrop = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(bondedPos)).inflate(1.0), item -> MoldedPlasticData.get(item.getItem()).isPresent()).stream().map(ItemEntity::getItem).findFirst().orElseThrow(() -> new GameTestAssertException("bonded product destruction produced no molded item"));
        check(ItemStack.isSameItemSameComponents(bondedClone, bondedDrop), "bonded pick-block result differed from its destruction drop");
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "13x10x13", floor = true)
    @TestHolder(description = "A three-block bonded product keeps remote collision, selection, and atomic obstruction checks")
    static void largeBondedProductKeepsRemoteShape(ExtendedGameTestHelper helper) {
        EditableMoldingModel largeModel = model("Large Bonded Product", cube("Body", 0.0, 0.0, 0.0, 48.0, 48.0, 48.0));
        MoldedPlasticData data = fullData(largeModel, DyeColor.CYAN);
        PlasticEntityOrientation orientation = PlasticEntityOrientation.DEFAULT;
        ItemStack stack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(stack, data);
        PlasticMeltColor.set(stack, DyeColor.CYAN);
        BlockState displayState = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState()
            .setValue(DyeableMaterial.COLOR, DyeColor.CYAN);
        BlockPos support = new BlockPos(6, 2, 6);
        BlockPos occupied = support.above();
        BlockPos obstacle = occupied.above(2);
        helper.setBlock(support, Blocks.STONE);
        helper.setBlock(obstacle, Blocks.OBSIDIAN);
        BlockPos absoluteSupport = helper.absolutePos(support);
        BlockPos absoluteOccupied = helper.absolutePos(occupied);
        UniversalPlasticEntity product = new UniversalPlasticEntity(PlasticraftEntities.UNIVERSAL_PLASTIC.get(), helper.getLevel(), data.geometry().placementPosition(absoluteOccupied, orientation), displayState, stack, orientation);
        check(helper.getLevel().addFreshEntity(product), "large product entity could not be spawned");
        check(BondedFallingBlocks.putPatch(helper.getLevel(), absoluteSupport, Direction.UP), "large product adhesive patch could not be placed");
        check(!AdhesiveBondingService.bondEntityFromPatch(helper.getLevel(), product, absoluteSupport, Direction.UP), "remote obstacle did not reject large product blockification");
        check(product.isAlive() && helper.getLevel().getBlockState(absoluteOccupied).isAir(), "failed large product blockification changed the entity or anchor");
        helper.setBlock(obstacle, Blocks.AIR);
        check(AdhesiveBondingService.bondEntityFromPatch(helper.getLevel(), product, absoluteSupport, Direction.UP), "large product did not blockify after the remote obstacle was removed");
        check(product.isRemoved(), "blockified large product entity was not removed");
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(absoluteOccupied);
        check(blockEntity instanceof BondedEntityBlockEntity bonded && bonded.isInitialized(), "blockified large product did not create initialized bonded storage");
        AABB remoteProbe = new AABB((double)absoluteOccupied.getX() + 0.25, (double)absoluteOccupied.getY() + 2.25, (double)absoluteOccupied.getZ() + 0.25, (double)absoluteOccupied.getX() + 0.75, (double)absoluteOccupied.getY() + 2.75, (double)absoluteOccupied.getZ() + 0.75);
        check(!helper.getLevel().noBlockCollision(null, remoteProbe), "remote third layer of bonded product lost physical collision");
        Vec3 rayFrom = new Vec3((double)absoluteOccupied.getX() + 0.5, (double)absoluteOccupied.getY() + 4.0, (double)absoluteOccupied.getZ() + 0.5);
        Vec3 rayTo = rayFrom.add(0.0, -2.0, 0.0);
        BlockHitResult hit = helper.getLevel().clip(new ClipContext(rayFrom, rayTo, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        check(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(absoluteOccupied), "remote third layer could not be selected through its bonded anchor");
        helper.setBlock(occupied, Blocks.AIR);
        check(helper.getLevel().noBlockCollision(null, remoteProbe), "removed bonded product retained a stale remote collision");
        BlockHitResult removedHit = helper.getLevel().clip(new ClipContext(rayFrom, rayTo, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        check(removedHit.getType() == HitResult.Type.MISS, "removed bonded product retained a stale remote selection outline");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "27x8x27", floor = true)
    @TestHolder(description = "Both 3x3 anvil tops resolve the chamber anchor in all four horizontal facings")
    static void fourFacingAnvilStructures(ExtendedGameTestHelper helper) {
        List<FacingPlacement> placements = List.of(
            new FacingPlacement(new BlockPos(6, 2, 2), Direction.NORTH, PlasticMoldingAnvilProcessor.OutputMode.ITEM),
            new FacingPlacement(new BlockPos(24, 2, 6), Direction.EAST, PlasticMoldingAnvilProcessor.OutputMode.ENTITY),
            new FacingPlacement(new BlockPos(20, 2, 24), Direction.SOUTH, PlasticMoldingAnvilProcessor.OutputMode.ITEM),
            new FacingPlacement(new BlockPos(2, 2, 20), Direction.WEST, PlasticMoldingAnvilProcessor.OutputMode.ENTITY)
        );
        for (FacingPlacement placement : placements) {
            PlasticMoldingChamberBlockEntity chamber = placeChamber(helper, placement.relativeController(), placement.front());
            BlockPos controller = chamber.getBlockPos();
            BlockPos topCenter = topCenter(controller, placement.front());
            placeTopStructure(helper.getLevel(), topCenter, placement.outputMode());
            check(PlasticMoldingAnvilProcessor.detectOutputMode(helper.getLevel(), topCenter) == placement.outputMode(), "top structure mode was not detected for " + placement.front());
            check(PlasticMoldingAnvilProcessor.findChamber(helper.getLevel(), topCenter).filter(controller::equals).isPresent(), "top structure did not resolve its chamber for " + placement.front());
            check(PlasticMoldingAnvilProcessor.tryProcess(helper.getLevel(), topCenter), "valid but unready molding structure was not claimed for " + placement.front());
            check(chamber.machineState() == PlasticMoldingMachineState.EDITABLE, "unready top structure changed the chamber state");
            assertTopStructureIntact(helper.getLevel(), topCenter, placement.outputMode());
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "13x8x13", floor = true)
    @TestHolder(description = "Overcompressor output recovers one clay ball, preserves staging melt, and ignores duplicate impacts")
    static void itemProductionRecoversOneClayWithoutDuplication(ExtendedGameTestHelper helper) {
        ReadyChamber ready = prepareChamber(helper, new BlockPos(6, 2, 3), Direction.NORTH, almostFullModel(), 1, MoldingProductionMode.REDSTONE, PlasticMoldingAnvilProcessor.OutputMode.ITEM, DyeColor.RED);
        fireGiantAnvil(helper, ready.topCenter());
        List<ItemEntity> products = productItems(helper.getLevel(), ready.bounds());
        check(products.size() == 1, "overcompressor structure did not produce exactly one item product");
        MoldedPlasticData data = MoldedPlasticData.get(products.getFirst().getItem()).orElseThrow();
        check(data.volumeMask().volume() == 1000, "250 mB item output did not contain exactly 1000 cells");
        check(PlasticMeltColor.get(data.material()) == DyeColor.RED, "item output lost batch color");
        check(clayCount(helper.getLevel(), ready.bounds()) == 1, "one consumed clay ball was not returned exactly");
        assertCompletedCycle(ready, PlasticMoldingMachineState.EDITABLE);
        assertTopStructureIntact(helper.getLevel(), ready.topCenter(), PlasticMoldingAnvilProcessor.OutputMode.ITEM);
        int entityCount = entitiesIn(helper.getLevel(), ready.bounds()).size();
        fireGiantAnvil(helper, ready.topCenter());
        check(entitiesIn(helper.getLevel(), ready.bounds()).size() == entityCount, "duplicate anvil event produced a second item or clay return");
        check(clayCount(helper.getLevel(), ready.bounds()) == 1, "duplicate anvil event changed the recovered clay total");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "13x8x13", floor = true)
    @TestHolder(description = "Crafting-table output rolls back a blocked preflight and recovers exactly 64 clay balls")
    static void entityProductionPreflightAndSixtyFourClay(ExtendedGameTestHelper helper) {
        ReadyChamber ready = prepareChamber(helper, new BlockPos(6, 2, 3), Direction.NORTH, model("Sixty Four Clay", cube("Body", 0.0, 0.0, 0.0, 44.0, 32.0, 32.0)), 64, MoldingProductionMode.SINGLE, PlasticMoldingAnvilProcessor.OutputMode.ENTITY, DyeColor.BLUE);
        Vec3 center = ready.bounds().getCenter();
        ItemEntity blocker = new ItemEntity(helper.getLevel(), center.x, center.y, center.z, new ItemStack(Items.STICK));
        check(helper.getLevel().addFreshEntity(blocker), "preflight blocker could not be spawned");
        int energyBefore = ready.chamber().energyStored();
        fireGiantAnvil(helper, ready.topCenter());
        check(ready.chamber().machineState() == PlasticMoldingMachineState.PROCESS_READY, "blocked preflight changed the process-ready state");
        check(ready.chamber().batchFluidAmount() == 250, "blocked preflight consumed batch melt");
        check(ready.chamber().stagingFluidAmount() == STAGING_RESERVE, "blocked preflight changed staging melt");
        check(ready.chamber().moldedClayBalls() == 64, "blocked preflight consumed molded clay");
        check(ready.chamber().energyStored() == energyBefore, "blocked preflight consumed processing energy");
        check(productEntities(helper.getLevel(), ready.bounds()).isEmpty(), "blocked preflight spawned a partial entity product");
        blocker.discard();
        fireGiantAnvil(helper, ready.topCenter());
        List<UniversalPlasticEntity> products = productEntities(helper.getLevel(), ready.bounds());
        check(products.size() == 1, "crafting-table structure did not produce exactly one entity product");
        UniversalPlasticEntity product = products.getFirst();
        product.setNoGravity(true);
        MoldedPlasticData data = product.getMoldedData().orElseThrow();
        check(data.volumeMask().volume() == 1000, "250 mB entity output did not contain exactly 1000 cells");
        Vec3 expectedPosition = new Vec3(ready.bounds().minX + data.geometry().entityOrigin().x, ready.bounds().minY + data.geometry().entityOrigin().y, ready.bounds().minZ + data.geometry().entityOrigin().z);
        check(product.position().distanceTo(expectedPosition) <= 1.0E-8, "entity output did not use the persisted manufacturing anchor");
        check(MoldedPlasticData.get(product.getDropStack()).orElseThrow().equals(data), "entity output drop did not preserve its manufactured data");
        check(clayCount(helper.getLevel(), ready.bounds()) == 64, "64 consumed clay balls were not returned exactly");
        assertCompletedCycle(ready, PlasticMoldingMachineState.EDITABLE);
        assertTopStructureIntact(helper.getLevel(), ready.topCenter(), PlasticMoldingAnvilProcessor.OutputMode.ENTITY);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "13x8x13", floor = true)
    @TestHolder(description = "Continuous production recovers 108 clay balls and waits until every output is removed")
    static void continuousProductionRecoversOneHundredEightClay(ExtendedGameTestHelper helper) {
        ReadyChamber ready = prepareChamber(helper, new BlockPos(6, 2, 3), Direction.NORTH, model("One Cell", cube("Cell", 0.0, 0.0, 0.0, 1.0, 1.0, 1.0)), 108, MoldingProductionMode.CONTINUOUS, PlasticMoldingAnvilProcessor.OutputMode.ITEM, DyeColor.GREEN);
        fireGiantAnvil(helper, ready.topCenter());
        check(productItems(helper.getLevel(), ready.bounds()).size() == 1, "continuous cycle did not create its item product");
        MoldedPlasticData data = MoldedPlasticData.get(productItems(helper.getLevel(), ready.bounds()).getFirst().getItem()).orElseThrow();
        check(data.volumeMask().volume() == 1, "one-cell model did not stay one cell after manufacturing");
        check(clayCount(helper.getLevel(), ready.bounds()) == 108, "108 consumed clay balls were not returned exactly");
        assertCompletedCycle(ready, PlasticMoldingMachineState.WAITING_NEXT_CYCLE);
        check(ready.chamber().waitReason() == MoldingWaitReason.WAITING_FOR_CLEAR_REGION, "continuous cycle did not report the occupied output region");
        tick(ready.chamber(), 1);
        check(ready.chamber().machineState() == PlasticMoldingMachineState.WAITING_NEXT_CYCLE, "continuous mode started a mold while product or clay remained");
        for (Entity entity : entitiesIn(helper.getLevel(), ready.bounds())) {
            entity.discard();
        }
        ItemStack clayRemainder = ready.chamber().clayItemHandler().insertItem(0, new ItemStack(Items.CLAY_BALL, 108), false);
        check(clayRemainder.isEmpty(), "returned clay could not be fed back through the item capability");
        tick(ready.chamber(), 1);
        check(ready.chamber().machineState() == PlasticMoldingMachineState.WAITING_TO_LOCK, "cleared continuous cycle did not prepare the next mold");
        tick(ready.chamber(), 1);
        check(ready.chamber().machineState() == PlasticMoldingMachineState.MOLD_FILLING, "continuous cycle did not resume clay filling after logistics returned the clay");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "11x8x11", floor = true)
    @TestHolder(description = "Reloading an interrupted processing snapshot restores melt, clay, mode, and processing energy")
    static void interruptedProcessingReloadRestoresTransaction(ExtendedGameTestHelper helper) {
        ReadyChamber ready = prepareChamber(helper, new BlockPos(5, 2, 3), Direction.NORTH, model("Reload", cube("Body", 0.0, 0.0, 0.0, 8.0, 8.0, 8.0)), 108, MoldingProductionMode.SINGLE, PlasticMoldingAnvilProcessor.OutputMode.ITEM, DyeColor.PURPLE);
        int energyBefore = ready.chamber().energyStored();
        MoldingProcessSnapshot snapshot = ready.chamber().beginProcessing().orElseThrow(() -> new GameTestAssertException("process-ready chamber refused an interruption snapshot"));
        check(ready.chamber().machineState() == PlasticMoldingMachineState.PROCESSING, "beginProcessing did not enter the transaction state");
        check(ready.chamber().energyStored() == energyBefore - MoldingPowerBridge.energyPerWorkingTick(), "beginProcessing did not reserve one processing tick of energy");
        CompoundTag saved = ready.chamber().saveWithoutMetadata(helper.getLevel().registryAccess());
        PlasticMoldingChamberBlockEntity restored = new PlasticMoldingChamberBlockEntity(PlasticraftBlockEntities.PLASTIC_MOLDING_CHAMBER.get(), ready.chamber().getBlockPos(), ready.chamber().getBlockState());
        restored.setLevel(helper.getLevel());
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(restored.machineState() == PlasticMoldingMachineState.PROCESSING, "saved interruption did not retain its transaction marker");
        restored.onLoad();
        check(restored.machineState() == PlasticMoldingMachineState.PROCESS_READY, "interrupted transaction did not return to process-ready after load");
        check(restored.batchFluidAmount() == snapshot.batchFluid().getAmount(), "interrupted transaction lost its batch melt");
        check(restored.stagingFluidAmount() == STAGING_RESERVE, "interrupted transaction changed independently staged melt");
        check(restored.moldedClayBalls() == snapshot.moldedClayBalls(), "interrupted transaction lost molded clay");
        check(restored.cycleMode() == MoldingProductionMode.SINGLE, "interrupted transaction changed its cycle mode");
        check(restored.energyStored() == energyBefore, "interrupted transaction did not refund reserved processing energy");
        helper.succeed();
    }

    private static MoldedPlasticData fullData(EditableMoldingModel model, DyeColor color) {
        BakedMoldingModel baked = MoldingModelBaker.bake(model);
        int amount = Math.max(250, (baked.analysis().volume() + 3) / 4);
        return MoldedPlasticData.manufacture(model, baked, melt(amount, color), amount);
    }

    private static EditableMoldingModel almostFullModel() {
        return model("One Clay", cube("Lower Volume", 0.0, 0.0, 0.0, 48.0, 47.0, 48.0), cube("Upper Main", 0.0, 47.0, 0.0, 47.0, 48.0, 48.0), cube("Upper Edge", 47.0, 47.0, 1.0, 48.0, 48.0, 48.0));
    }

    private static EditableMoldingModel model(String name, MoldingElement ... elements) {
        return EditableMoldingModel.empty().withName(name).withElements(List.of(elements));
    }

    private static MoldingElement cube(String name, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return MoldingElement.cube(name, new MoldingVec3(minX, minY, minZ), new MoldingVec3(maxX, maxY, maxZ));
    }

    private static FluidStack melt(int amount, DyeColor color) {
        FluidStack melt = new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), amount);
        PlasticMeltColor.set(melt, color);
        return melt;
    }

    private static ReadyChamber prepareChamber(ExtendedGameTestHelper helper, BlockPos relativeController, Direction front, EditableMoldingModel model, int clayBalls, MoldingProductionMode mode, PlasticMoldingAnvilProcessor.OutputMode outputMode, DyeColor color) {
        PlasticMoldingChamberBlockEntity chamber = placeChamber(helper, relativeController, front);
        check(chamber.replaceEditableModel(model, chamber.revision()).accepted(), "production model was rejected");
        check(chamber.bakedModel().analysis().clayBallRequirement() == clayBalls, "production model did not require the expected clay total");
        check(chamber.setProductionMode(mode, chamber.revision()).accepted(), "production mode was rejected");
        chamber.inventory().setItem(0, new ItemStack(Items.CLAY_BALL, clayBalls));
        check(chamber.energyStorage().receiveEnergy(MoldingPowerBridge.capacity(), false) == MoldingPowerBridge.capacity(), "production chamber did not accept a full energy buffer");
        check(chamber.requestLock().accepted(), "production model could not be locked");
        tick(chamber, 12);
        check(chamber.machineState() == PlasticMoldingMachineState.MOLD_READY, "production mold did not finish in twelve active ticks");
        check(chamber.moldedClayBalls() == clayBalls, "production mold did not reserve exact clay");
        IFluidHandler fluids = chamber.fluidHandler();
        check(fluids.fill(melt(250, color), IFluidHandler.FluidAction.EXECUTE) == 250, "production chamber did not accept its minimum batch melt");
        tick(chamber, 1);
        check(chamber.machineState() == PlasticMoldingMachineState.PROCESS_READY, "minimum batch melt did not make the chamber process-ready");
        check(chamber.batchFluidAmount() == 250, "minimum batch did not transfer exactly 250 mB");
        check(fluids.fill(melt(STAGING_RESERVE, color), IFluidHandler.FluidAction.EXECUTE) == STAGING_RESERVE, "production chamber did not accept independent staging reserve");
        BlockPos topCenter = topCenter(chamber.getBlockPos(), front);
        placeTopStructure(helper.getLevel(), topCenter, outputMode);
        AABB bounds = PlasticMoldingChamberStructure.regionBounds(chamber.getBlockPos(), front);
        return new ReadyChamber(chamber, topCenter, bounds, outputMode);
    }

    private static PlasticMoldingChamberBlockEntity placeChamber(ExtendedGameTestHelper helper, BlockPos relativeController, Direction front) {
        BlockPos controller = helper.absolutePos(relativeController);
        BlockState state = PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.get().defaultBlockState()
            .setValue(PlasticMoldingChamberBlock.FACING, front);
        check(PlasticMoldingChamberStructure.placeAtomically(helper.getLevel(), controller, state), "production test chamber placement failed for " + front);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(controller);
        if (!(blockEntity instanceof PlasticMoldingChamberBlockEntity chamber)) {
            throw new GameTestAssertException("production test chamber block entity was missing");
        }
        tick(chamber, 1);
        check(chamber.structureComplete(), "production test chamber structure was incomplete");
        return chamber;
    }

    private static BlockPos topCenter(BlockPos controller, Direction front) {
        return controller.relative(front.getOpposite(), 2).above(3);
    }

    private static void placeTopStructure(ServerLevel level, BlockPos topCenter, PlasticMoldingAnvilProcessor.OutputMode outputMode) {
        for (int x = -1; x <= 1; ++x) {
            for (int z = -1; z <= 1; ++z) {
                BlockState state = x == 0 && z == 0 && outputMode == PlasticMoldingAnvilProcessor.OutputMode.ITEM ? ModBlocks.SPACE_OVERCOMPRESSOR.getDefaultState() : Blocks.CRAFTING_TABLE.defaultBlockState();
                level.setBlockAndUpdate(topCenter.offset(x, 0, z), state);
            }
        }
    }

    private static void assertTopStructureIntact(ServerLevel level, BlockPos topCenter, PlasticMoldingAnvilProcessor.OutputMode outputMode) {
        for (int x = -1; x <= 1; ++x) {
            for (int z = -1; z <= 1; ++z) {
                BlockState state = level.getBlockState(topCenter.offset(x, 0, z));
                if (x == 0 && z == 0 && outputMode == PlasticMoldingAnvilProcessor.OutputMode.ITEM) {
                    check(state.is(ModBlocks.SPACE_OVERCOMPRESSOR), "processing consumed the space overcompressor");
                    continue;
                }
                check(state.is(Blocks.CRAFTING_TABLE), "processing consumed a crafting-table top block");
            }
        }
    }

    private static void fireGiantAnvil(ExtendedGameTestHelper helper, BlockPos topCenter) {
        GiantAnvilLandingEventListener.handleMultiblock(
            new AnvilEvent.GiantOnLand(helper.getLevel(), topCenter.above(2), null, 2.0f)
        );
    }

    private static void assertCompletedCycle(ReadyChamber ready, PlasticMoldingMachineState state) {
        check(ready.chamber().machineState() == state, "successful cycle entered the wrong production state");
        check(ready.chamber().batchFluidAmount() == 0, "successful cycle retained batch melt");
        check(ready.chamber().stagingFluidAmount() == STAGING_RESERVE, "successful cycle changed independent staging melt");
        check(ready.chamber().moldedClayBalls() == 0, "successful cycle retained molded clay accounting");
        check(PlasticMoldingChamberStructure.isComplete(
            ready.chamber().getLevel(),
            ready.chamber().getBlockPos(),
            ready.chamber().getBlockState().getValue(PlasticMoldingChamberBlock.FACING)
        ), "successful cycle consumed a chamber or region part");
    }

    private static List<Entity> entitiesIn(ServerLevel level, AABB bounds) {
        return level.getEntities((Entity)null, bounds, Entity::isAlive);
    }

    private static List<ItemEntity> productItems(ServerLevel level, AABB bounds) {
        return level.getEntitiesOfClass(ItemEntity.class, bounds, entity -> entity.isAlive() && entity.getItem().is(PlasticraftBlocks.UNIVERSAL_PLASTIC.asItem()));
    }

    private static List<UniversalPlasticEntity> productEntities(ServerLevel level, AABB bounds) {
        return level.getEntitiesOfClass(UniversalPlasticEntity.class, bounds, Entity::isAlive);
    }

    private static int clayCount(ServerLevel level, AABB bounds) {
        return level.getEntitiesOfClass(ItemEntity.class, bounds, entity -> entity.isAlive() && entity.getItem().is(Items.CLAY_BALL)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static UniversalPlasticEntity onlyPlasticEntity(ExtendedGameTestHelper helper, AABB bounds) {
        List<UniversalPlasticEntity> entities = helper.getLevel().getEntitiesOfClass(UniversalPlasticEntity.class, bounds, Entity::isAlive);
        if (entities.size() != 1) {
            throw new GameTestAssertException("expected one dynamic plastic entity, found " + entities.size());
        }
        return entities.getFirst();
    }

    private static ItemStack findInventoryStack(Player player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); ++slot) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) continue;
            return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void tick(PlasticMoldingChamberBlockEntity chamber, int ticks) {
        for (int index = 0; index < ticks; ++index) {
            PlasticMoldingChamberBlockEntity.serverTick(
                chamber.getLevel(),
                chamber.getBlockPos(),
                chamber.getBlockState(),
                chamber
            );
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private record FacingPlacement(BlockPos relativeController, Direction front, PlasticMoldingAnvilProcessor.OutputMode outputMode) {
    }

    private record ReadyChamber(PlasticMoldingChamberBlockEntity chamber, BlockPos topCenter, AABB bounds, PlasticMoldingAnvilProcessor.OutputMode outputMode) {
    }
}
