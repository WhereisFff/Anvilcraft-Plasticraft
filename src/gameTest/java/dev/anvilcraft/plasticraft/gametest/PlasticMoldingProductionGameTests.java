package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberStructure;
import dev.anvilcraft.plasticraft.block.PlasticMoldingMachineState;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.block.piston.PlasticPistonOccupancy;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveBondingService;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesivePathPlanner;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveTransit;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityGeometry;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedPlasticRedstoneConductor;
import dev.anvilcraft.plasticraft.init.PlasticraftAttachments;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockEntities;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.machine.MoldingFormingMode;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPowerBridge;
import dev.anvilcraft.plasticraft.molding.machine.MoldingProcessSnapshot;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPrintingDoorState;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
        Component expectedName = Component.translatable(
            "item.anvilcraftplasticraft.molded_product_name",
            Component.translatable("material.anvilcraftplasticraft.universal_plastic"),
            Component.translatable("item.anvilcraftplasticraft.molded_product_suffix.block")
        );
        check(
            molded.getName().equals(expectedName),
            "molded entity name depended on its model name: " + molded.getName()
        );
        check(
            moldedStack.getHoverName().equals(expectedName),
            "molded item name depended on its model name: " + moldedStack.getHoverName()
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

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "9x6x5", floor = true)
    @TestHolder(description = "A plastic entity occupies a piston position and relays the push into the block ahead")
    static void plasticEntityRelaysPistonPush(ExtendedGameTestHelper helper) {
        BlockPos piston = new BlockPos(1, 2, 2);
        BlockPos occupied = piston.east();
        BlockPos ahead = occupied.east();
        helper.setBlock(
            piston,
            Blocks.PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST)
        );
        MoldedPlasticData data = fullData(model(
            "Piston Cube",
            cube("Body", 16.0D, 0.0D, 16.0D, 32.0D, 16.0D, 32.0D)
        ), DyeColor.WHITE);
        UniversalPlasticEntity plastic = createMoldedProduct(helper, occupied, data);

        helper.setBlock(ahead, Blocks.OBSIDIAN);
        PistonStructureResolver blocked = new PistonStructureResolver(
            helper.getLevel(),
            helper.absolutePos(piston),
            Direction.EAST,
            true
        );
        check(!blocked.resolve(), "plastic occupancy did not relay the immovable block obstruction");

        helper.setBlock(ahead, Blocks.STONE);
        PistonStructureResolver movable = new PistonStructureResolver(
            helper.getLevel(),
            helper.absolutePos(piston),
            Direction.EAST,
            true
        );
        check(movable.resolve(), "plastic occupancy rejected a movable block ahead");
        check(movable.getToPush().contains(helper.absolutePos(occupied)),
            "plastic anchor was absent from the piston push list");
        check(movable.getToPush().contains(helper.absolutePos(ahead)),
            "block ahead of the plastic entity was absent from the piston push list");

        helper.getLevel().setBlockAndUpdate(
            helper.absolutePos(piston.north()),
            Blocks.REDSTONE_BLOCK.defaultBlockState()
        );
        helper.runAfterDelay(6, () -> {
            check(helper.getBlockState(ahead.east()).is(Blocks.STONE),
                "piston did not move the block ahead of the plastic entity");
            check(plastic.plasticraft$getAnchorBlockPos().equals(helper.absolutePos(occupied.east())),
                "plastic entity did not move normally with the piston");
            AABB bounds = plastic.plasticraft$getCollisionBox().bounds();
            BlockPos dest = helper.absolutePos(occupied.east());
            check(
                Math.abs(bounds.minX - dest.getX()) <= 1.0E-12D
                    && Math.abs(bounds.maxX - (dest.getX() + 1.0D)) <= 1.0E-12D,
                "piston push left a side protrusion: bounds=" + bounds + " dest=" + dest
            );
            check(helper.getBlockState(ahead).isAir(), "temporary plastic occupancy remained after the push");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 35)
    @EmptyTemplate(value = "14x8x14", floor = true)
    @TestHolder(description = "A piston moves every occupied cell of a multi-block plastic entity as one load")
    static void pistonMovesMultiBlockPlasticEntity(ExtendedGameTestHelper helper) {
        BlockPos placement = new BlockPos(6, 3, 6);
        MoldedPlasticData data = fullData(model(
            "Large Piston Body",
            cube("Body", 0.0D, 16.0D, 0.0D, 48.0D, 32.0D, 48.0D)
        ), DyeColor.WHITE);
        UniversalPlasticEntity plastic = createMoldedProduct(helper, placement, data);
        List<BlockPos> occupied = PlasticPistonOccupancy.occupiedPositions(plastic);
        check(occupied.size() > 1, "multi-block piston test product occupied only one cell");

        BlockPos anchor = plastic.plasticraft$getAnchorBlockPos();
        BlockPos contact = occupied.stream()
            .filter(pos -> pos.getY() == anchor.getY())
            .min(Comparator.comparingInt((BlockPos pos) -> pos.getX()).thenComparingInt(pos -> pos.getZ()))
            .orElseThrow();
        BlockPos remoteFront = occupied.stream()
            .filter(pos -> pos.getY() == anchor.getY())
            .max(Comparator.comparingInt((BlockPos pos) -> pos.getX()).thenComparingInt(pos -> pos.getZ()))
            .orElseThrow();
        BlockPos piston = contact.west();
        BlockPos obstruction = remoteFront.east();
        helper.getLevel().setBlockAndUpdate(
            piston,
            Blocks.PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST)
        );
        helper.getLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());

        PistonStructureResolver resolver = new PistonStructureResolver(
            helper.getLevel(),
            piston,
            Direction.EAST,
            true
        );
        check(resolver.resolve(), "multi-block plastic entity was rejected by the piston resolver");
        for (BlockPos occupiedPos : occupied) {
            check(resolver.getToPush().contains(occupiedPos),
                "multi-block plastic cell was absent from the push list: " + occupiedPos);
        }
        check(resolver.getToPush().contains(obstruction),
            "multi-block plastic entity did not relay the remote obstruction");

        Vec3 startPosition = plastic.position();
        helper.getLevel().setBlockAndUpdate(piston.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(2, () -> {
            check(plastic.getX() > startPosition.x + 0.05D,
                "multi-block plastic entity did not follow the extending piston");
            helper.runAfterDelay(5, () -> {
                check(plastic.plasticraft$getAnchorBlockPos().equals(anchor.east()),
                    "multi-block plastic entity did not finish moving one block");
                check(helper.getLevel().getBlockState(obstruction.east()).is(Blocks.STONE),
                    "multi-block plastic entity did not push the remote obstruction");
                for (BlockPos occupiedPos : occupied) {
                    check(!helper.getLevel().getBlockState(occupiedPos.east()).is(Blocks.MOVING_PISTON),
                        "multi-block plastic movement left a virtual moving piston behind");
                }
                helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks = 45)
    @EmptyTemplate(value = "9x6x5", floor = true)
    @TestHolder(description = "A sticky piston retracts a plastic entity touching its extended head")
    static void stickyPistonRetractsPlasticEntity(ExtendedGameTestHelper helper) {
        BlockPos piston = new BlockPos(1, 2, 2);
        BlockPos power = piston.north();
        BlockPos occupied = piston.relative(Direction.EAST, 2);
        helper.setBlock(
            piston,
            Blocks.STICKY_PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST)
        );
        helper.setBlock(power, Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(5, () -> {
            check(helper.getBlockState(piston).getValue(BlockStateProperties.EXTENDED),
                "sticky piston did not extend before the plastic entity was placed");
            MoldedPlasticData data = fullData(model(
                "Sticky Piston Cube",
                cube("Body", 16.0D, 0.0D, 16.0D, 32.0D, 16.0D, 32.0D)
            ), DyeColor.WHITE);
            UniversalPlasticEntity plastic = createMoldedProduct(helper, occupied, data);
            Vec3 startPosition = plastic.position();

            helper.setBlock(power, Blocks.AIR);
            helper.runAfterDelay(2, () -> {
                check(plastic.getX() < startPosition.x - 0.05D,
                    "plastic entity did not follow the retracting sticky piston");
                helper.runAfterDelay(4, () -> {
                    check(plastic.plasticraft$getAnchorBlockPos().equals(helper.absolutePos(occupied.west())),
                        "sticky piston did not finish pulling the plastic entity back");
                    check(helper.getBlockState(occupied.west()).isAir(),
                        "sticky piston left the temporary plastic occupancy behind");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "10x6x9", floor = true)
    @TestHolder(description = "One- and two-tick sticky-piston pulses spit plastic without ghost blocks")
    static void shortStickyPistonPulsesSpitPlasticWithoutGhostWall(ExtendedGameTestHelper helper) {
        BlockPos[] pistons = {new BlockPos(1, 2, 2), new BlockPos(1, 2, 6)};
        BlockPos[] powers = new BlockPos[pistons.length];
        BlockPos[] occupied = new BlockPos[pistons.length];
        UniversalPlasticEntity[] plastics = new UniversalPlasticEntity[pistons.length];
        Vec3[] startPositions = new Vec3[pistons.length];
        MoldedPlasticData data = fullData(model(
            "Short Pulse Piston Cube",
            cube("Body", 16.0D, 0.0D, 16.0D, 32.0D, 16.0D, 32.0D)
        ), DyeColor.WHITE);

        for (int index = 0; index < pistons.length; index++) {
            powers[index] = pistons[index].north();
            occupied[index] = pistons[index].east();
            helper.setBlock(
                pistons[index],
                Blocks.STICKY_PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST)
            );
            plastics[index] = createMoldedProduct(helper, occupied[index], data);
            startPositions[index] = plastics[index].position();
            helper.setBlock(powers[index], Blocks.REDSTONE_BLOCK);
        }

        helper.runAfterDelay(1, () -> {
            for (int index = 0; index < pistons.length; index++) {
                check(helper.getBlockState(pistons[index]).getValue(BlockStateProperties.EXTENDED),
                    "sticky piston did not begin pulse " + (index + 1));
                check(plastics[index].getX() > startPositions[index].x + 0.05D,
                    "plastic entity did not begin pulse " + (index + 1));
                check(!helper.getBlockState(occupied[index].east()).is(Blocks.MOVING_PISTON),
                    "virtual plastic occupancy became a moving-piston block");
            }
            helper.setBlock(powers[0], Blocks.AIR);
        });
        helper.runAfterDelay(2, () -> helper.setBlock(powers[1], Blocks.AIR));
        helper.runAfterDelay(9, () -> {
            for (int index = 0; index < pistons.length; index++) {
                check(!helper.getBlockState(pistons[index]).getValue(BlockStateProperties.EXTENDED),
                    "sticky piston did not finish retracting pulse " + (index + 1));
                check(plastics[index].plasticraft$getAnchorBlockPos().equals(
                        helper.absolutePos(occupied[index].east())),
                    (index + 1) + "-tick pulse pulled the plastic entity back instead of spitting it");
            }
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(5, 3, 8))) {
                check(!helper.getBlockState(pos).is(Blocks.MOVING_PISTON),
                    "short piston pulse left a moving-piston wall at " + pos);
            }
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 55)
    @EmptyTemplate(value = "10x6x15", floor = true)
    @TestHolder(description = "Slime, honey, and high-viscosity resin move adjacent plastic entities both ways")
    static void stickyBlocksMoveAdjacentPlasticEntitiesBothWays(ExtendedGameTestHelper helper) {
        BlockState[] stickyStates = {
            Blocks.SLIME_BLOCK.defaultBlockState(),
            Blocks.HONEY_BLOCK.defaultBlockState(),
            PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.get().defaultBlockState()
        };
        String[] labels = {"slime block", "honey block", "high-viscosity resin block"};
        BlockPos[] pistons = new BlockPos[stickyStates.length];
        BlockPos[] carriers = new BlockPos[stickyStates.length];
        BlockPos[] occupied = new BlockPos[stickyStates.length];
        UniversalPlasticEntity[] plastics = new UniversalPlasticEntity[stickyStates.length];
        Vec3[] startPositions = new Vec3[stickyStates.length];
        MoldedPlasticData data = fullData(model(
            "Sticky Block Cube",
            cube("Body", 16.0D, 0.0D, 16.0D, 32.0D, 16.0D, 32.0D)
        ), DyeColor.WHITE);

        for (int index = 0; index < stickyStates.length; index++) {
            BlockPos piston = new BlockPos(2, 3, 2 + index * 4);
            BlockPos carrier = piston.east();
            BlockPos plasticPos = carrier.south();
            pistons[index] = piston;
            carriers[index] = carrier;
            occupied[index] = plasticPos;
            helper.setBlock(
                piston,
                Blocks.STICKY_PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST)
            );
            helper.setBlock(carrier, stickyStates[index]);
            plastics[index] = createMoldedProduct(helper, plasticPos, data);
            startPositions[index] = plastics[index].position();
            PistonStructureResolver resolver = new PistonStructureResolver(
                helper.getLevel(),
                helper.absolutePos(piston),
                Direction.EAST,
                true
            );
            check(resolver.resolve(), labels[index] + " rejected its adjacent plastic entity");
            check(resolver.getToPush().contains(helper.absolutePos(plasticPos)),
                labels[index] + " did not add its adjacent plastic entity to the push list");
            helper.setBlock(piston.west(), Blocks.REDSTONE_BLOCK);
        }

        helper.runAfterDelay(3, () -> {
            for (int index = 0; index < stickyStates.length; index++) {
                check(plastics[index].getX() > startPositions[index].x + 0.05D,
                    labels[index] + " did not move its adjacent plastic entity while extending");
                double carrierDelta = stickyTravelEast(
                    helper,
                    carriers[index],
                    carriers[index].east(),
                    stickyStates[index].getBlock()
                );
                check(
                    Math.abs((plastics[index].getX() - startPositions[index].x) - carrierDelta) <= 0.08D,
                    labels[index] + " left a mid-push gap: plastic="
                        + (plastics[index].getX() - startPositions[index].x)
                        + " carrier="
                        + carrierDelta
                );
            }
            helper.runAfterDelay(3, () -> {
                for (int index = 0; index < stickyStates.length; index++) {
                    check(helper.getBlockState(carriers[index].east()).is(stickyStates[index].getBlock()),
                        labels[index] + " did not finish extending");
                    check(plastics[index].plasticraft$getAnchorBlockPos().equals(
                        helper.absolutePos(occupied[index].east())
                    ), labels[index] + " did not move its adjacent plastic entity one block");
                    helper.setBlock(pistons[index].west(), Blocks.AIR);
                }
                helper.runAfterDelay(3, () -> {
                    for (int index = 0; index < stickyStates.length; index++) {
                        check(plastics[index].getX() < startPositions[index].x + 0.95D,
                            labels[index] + " did not pull its adjacent plastic entity while retracting");
                    }
                    helper.runAfterDelay(3, () -> {
                        for (int index = 0; index < stickyStates.length; index++) {
                            check(helper.getBlockState(carriers[index]).is(stickyStates[index].getBlock()),
                                labels[index] + " did not return with the sticky piston");
                            check(plastics[index].plasticraft$getAnchorBlockPos().equals(
                                helper.absolutePos(occupied[index])
                            ), labels[index] + " did not return its adjacent plastic entity");
                        }
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "11x6x8", floor = true)
    @TestHolder(description = "Only an exact 16x16x16 molded entity conducts redstone power through six faces")
    static void fullBlockSizedProductConductsRedstone(ExtendedGameTestHelper helper) {
        BlockPos fullAnchor = new BlockPos(3, 2, 2);
        BlockPos shortAnchor = new BlockPos(7, 2, 5);
        MoldedPlasticData fullProductData = fullData(model(
            "Redstone Cube",
            cube("Body", 16.0D, 0.0D, 16.0D, 32.0D, 16.0D, 32.0D)
        ), DyeColor.RED);
        UniversalPlasticEntity full = createMoldedProduct(helper, fullAnchor, fullProductData);
        createMoldedProduct(helper, fullAnchor.south(), fullProductData);
        createMoldedProduct(helper, shortAnchor, fullData(model(
            "Short Redstone Cube",
            cube("Body", 16.0D, 0.0D, 16.0D, 32.0D, 15.0D, 32.0D)
        ), DyeColor.RED));

        BlockState poweredRepeater = Blocks.REPEATER.defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
            .setValue(BlockStateProperties.POWERED, true);
        helper.setBlock(fullAnchor.west().below(), Blocks.STONE);
        helper.setBlock(fullAnchor.west(), poweredRepeater);
        helper.setBlock(fullAnchor.west(2), Blocks.REDSTONE_BLOCK);
        helper.setBlock(fullAnchor.east(), Blocks.REDSTONE_LAMP);
        helper.setBlock(shortAnchor.west().below(), Blocks.STONE);
        helper.setBlock(shortAnchor.west(), poweredRepeater);
        helper.setBlock(shortAnchor.west(2), Blocks.REDSTONE_BLOCK);
        helper.setBlock(shortAnchor.east(), Blocks.REDSTONE_LAMP);
        helper.runAfterDelay(3, () -> {
            BlockPos absoluteFullAnchor = helper.absolutePos(fullAnchor);
            int conductedSignal = helper.getLevel().getSignal(absoluteFullAnchor, Direction.WEST);
            check(conductedSignal == 15,
                "full block-sized plastic did not conduct its input; conducted=" + conductedSignal
                    + ", virtual=" + MoldedPlasticRedstoneConductor.weakSignal(
                        helper.getLevel(),
                        absoluteFullAnchor,
                        Direction.WEST
                    )
                    + ", source=" + helper.getLevel().getDirectSignal(
                        absoluteFullAnchor.west(),
                        Direction.WEST
                    )
                    + ", sourceState=" + helper.getLevel().getBlockState(absoluteFullAnchor.west())
                    + ", bounds=" + full.getBoundingBox()
                    + ", surface=" + fullProductData.surfaceBounds()
                    + ", ticks=" + full.tickCount);
            check(helper.getBlockState(fullAnchor.east()).getValue(BlockStateProperties.LIT),
                "full block-sized plastic did not power the receiver across it");
            check(helper.getLevel().getSignal(helper.absolutePos(shortAnchor), Direction.WEST) == 0,
                "15 px-tall plastic incorrectly conducted redstone");
            check(!helper.getBlockState(shortAnchor.east()).getValue(BlockStateProperties.LIT),
                "15 px-tall plastic powered the receiver across it");

            helper.setBlock(fullAnchor.west(), Blocks.AIR);
            helper.runAfterDelay(6, () -> {
                check(helper.getLevel().getSignal(helper.absolutePos(fullAnchor), Direction.WEST) == 0,
                    "adjacent full block-sized plastic conductors retained a removed redstone input");
                check(!helper.getBlockState(fullAnchor.east()).getValue(BlockStateProperties.LIT),
                    "receiver remained powered after the conducted input was removed");
                helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "24x6x11", floor = true)
    @TestHolder(description = "Full-sized plastic uses exact one-cell ports and vanilla strong-power propagation")
    static void fullBlockRedstoneDoesNotBridgeGapsOrWeakPower(ExtendedGameTestHelper helper) {
        MoldedPlasticData fullProductData = fullData(model(
            "Redstone Port Cube",
            cube("Body", 16.0D, 0.0D, 16.0D, 32.0D, 16.0D, 32.0D)
        ), DyeColor.RED);
        BlockState poweredRepeater = Blocks.REPEATER.defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
            .setValue(BlockStateProperties.POWERED, true);

        BlockPos weakAnchor = new BlockPos(4, 2, 2);
        createMoldedProduct(helper, weakAnchor, fullProductData);
        helper.setBlock(weakAnchor.west().below(), Blocks.STONE);
        helper.setBlock(weakAnchor.west(), ModBlocks.REDSTONE_WIRE.get());
        helper.setBlock(weakAnchor.west(2), Blocks.REDSTONE_BLOCK);
        helper.setBlock(weakAnchor.east(), Blocks.REDSTONE_LAMP);

        BlockPos inputGapAnchor = new BlockPos(11, 2, 2);
        createMoldedProduct(helper, inputGapAnchor, fullProductData);
        BlockPos separatedInput = inputGapAnchor.west(2);
        helper.setBlock(separatedInput.below(), Blocks.STONE);
        helper.setBlock(separatedInput, poweredRepeater);
        helper.setBlock(separatedInput.west(), Blocks.REDSTONE_BLOCK);
        helper.setBlock(inputGapAnchor.east(), Blocks.REDSTONE_LAMP);

        BlockPos outputGapAnchor = new BlockPos(18, 2, 2);
        createMoldedProduct(helper, outputGapAnchor, fullProductData);
        helper.setBlock(outputGapAnchor.west().below(), Blocks.STONE);
        helper.setBlock(outputGapAnchor.west(), poweredRepeater);
        helper.setBlock(outputGapAnchor.west(2), Blocks.REDSTONE_BLOCK);
        helper.setBlock(outputGapAnchor.east(2), Blocks.REDSTONE_LAMP);

        BlockPos dustAnchor = new BlockPos(11, 2, 7);
        createMoldedProduct(helper, dustAnchor, fullProductData);
        BlockPos inputDust = dustAnchor.west();
        BlockPos outputDust = dustAnchor.east();
        helper.setBlock(inputDust.below(), Blocks.STONE);
        helper.setBlock(outputDust.below(), Blocks.STONE);
        helper.setBlock(inputDust, Blocks.REDSTONE_WIRE);
        helper.setBlock(inputDust.west(), Blocks.REDSTONE_BLOCK);
        helper.setBlock(outputDust, Blocks.REDSTONE_WIRE);
        helper.setBlock(dustAnchor.north(), Blocks.REDSTONE_LAMP);

        helper.runAfterDelay(6, () -> {
            BlockPos absoluteWeakSource = helper.absolutePos(weakAnchor.west());
            check(helper.getLevel().getSignal(absoluteWeakSource, Direction.WEST) > 0,
                "AnvilCraft redstone wire had no weak activation output");
            check(helper.getLevel().getDirectSignal(absoluteWeakSource, Direction.WEST) == 0,
                "AnvilCraft redstone wire unexpectedly supplied direct strong power");
            check(!helper.getBlockState(weakAnchor.east()).getValue(BlockStateProperties.LIT),
                "weak-only source propagated through full-sized plastic");
            check(!helper.getBlockState(inputGapAnchor.east()).getValue(BlockStateProperties.LIT),
                "full-sized plastic read a source across an empty input cell");
            check(!helper.getBlockState(outputGapAnchor.east(2)).getValue(BlockStateProperties.LIT),
                "full-sized plastic powered a receiver across an empty output cell");
            check(helper.getBlockState(inputDust).getValue(BlockStateProperties.POWER) > 0,
                "input redstone dust was not powered for the propagation test");
            check(helper.getBlockState(dustAnchor.north()).getValue(BlockStateProperties.LIT),
                "redstone dust did not activate an ordinary component through full-sized plastic");
            check(helper.getBlockState(outputDust).getValue(BlockStateProperties.POWER) == 0,
                "redstone dust propagated through full-sized plastic into redstone dust");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "15x9x13", floor = true)
    @TestHolder(description = "Adhesive placement centers the complete model face instead of its outermost cube")
    static void adhesivePlacementCentersCompleteModelFace(ExtendedGameTestHelper helper) {
        MoldedPlasticData data = fullData(model(
            "Asymmetric Foot",
            cube("Corner foot", 0.0D, 0.0D, 0.0D, 8.0D, 4.0D, 8.0D),
            cube("Wide body", 0.0D, 4.0D, 0.0D, 48.0D, 20.0D, 48.0D)
        ), DyeColor.WHITE);
        UniversalPlasticEntity plastic = createMoldedProduct(helper, new BlockPos(3, 5, 6), data);
        BlockPos support = new BlockPos(11, 1, 6);
        helper.setBlock(support, Blocks.STONE);

        AdhesivePathPlanner.Plan plan = AdhesivePathPlanner.plan(
            helper.getLevel(),
            plastic,
            helper.makeMockPlayer(GameType.SURVIVAL),
            helper.absolutePos(support),
            Direction.UP,
            Direction.DOWN
        );
        check(plan.valid(), "whole-face adhesive alignment produced no path: " + plan.status());
        check(plan.targetOrientation() != null
                && plan.targetOrientation().worldDirection(Direction.DOWN) == Direction.DOWN,
            "whole-face adhesive alignment produced the wrong target orientation");
        AABB targetBounds = plastic.plasticraft$getGeometry()
            .collisionBoxAt(plan.targetPosition(), plan.targetOrientation())
            .bounds();
        Vec3 actualFaceCenter = new Vec3(
            (targetBounds.minX + targetBounds.maxX) * 0.5D,
            targetBounds.minY,
            (targetBounds.minZ + targetBounds.maxZ) * 0.5D
        );
        Vec3 expectedFaceCenter = Vec3.atCenterOf(helper.absolutePos(support)).add(0.0D, 0.5D, 0.0D);
        check(actualFaceCenter.distanceToSqr(expectedFaceCenter) < EPSILON,
            "adhesive placement centered the corner foot instead of the complete model face: actual="
                + actualFaceCenter + ", expected=" + expectedFaceCenter);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "15x10x13", floor = true)
    @TestHolder(description = "A sideways wide model can rotate onto a clear upright ground target")
    static void adhesivePathAllowsClearTargetOrientationAtGround(ExtendedGameTestHelper helper) {
        MoldedPlasticData data = fullData(model(
            "Wide Slab",
            cube("Body", 0.0D, 0.0D, 0.0D, 48.0D, 8.0D, 48.0D)
        ), DyeColor.LIGHT_BLUE);
        UniversalPlasticEntity plastic = createMoldedProduct(helper, new BlockPos(3, 6, 6), data);
        PlasticEntityOrientation sideways = new PlasticEntityOrientation(Direction.EAST, 0);
        plastic.setOrientation(sideways);
        plastic.setPos(data.geometry().placementPosition(helper.absolutePos(new BlockPos(3, 6, 6)), sideways));
        BlockPos support = new BlockPos(11, 1, 6);
        helper.setBlock(support, Blocks.STONE);

        AdhesivePathPlanner.Plan plan = AdhesivePathPlanner.plan(
            helper.getLevel(),
            plastic,
            helper.makeMockPlayer(GameType.SURVIVAL),
            helper.absolutePos(support),
            Direction.UP,
            Direction.DOWN
        );
        check(plan.targetOrientation() != null
                && plan.targetOrientation().worldDirection(Direction.DOWN) == Direction.DOWN,
            "sideways model did not receive an upright target orientation");
        boolean startOrientationBlocked = plastic.plasticraft$getGeometry()
            .collisionBoxAt(plan.targetPosition(), sideways)
            .shape()
            .toAabbs()
            .stream()
            .map(box -> box.deflate(0.002D))
            .anyMatch(box -> !helper.getLevel().noBlockCollision(plastic, box));
        boolean targetOrientationClear = plastic.plasticraft$getGeometry()
            .collisionBoxAt(plan.targetPosition(), plan.targetOrientation())
            .shape()
            .toAabbs()
            .stream()
            .map(box -> box.deflate(0.002D))
            .allMatch(box -> helper.getLevel().noBlockCollision(plastic, box));
        check(startOrientationBlocked, "sideways regression fixture also fit at the upright endpoint");
        check(targetOrientationClear, "upright regression fixture actually collided at the endpoint");
        check(plan.valid(), "clear upright endpoint was rejected by the sideways route: " + plan.status());

        AdhesiveTransit transit = new AdhesiveTransit(
            helper.absolutePos(support),
            Direction.UP,
            BuiltInRegistries.BLOCK.getKey(Blocks.STONE),
            plan.sourceFace(),
            Optional.empty(),
            -1,
            Vec3.ZERO,
            plan.points(),
            helper.getLevel().getGameTime(),
            10,
            plastic.isNoGravity(),
            true,
            sideways.pack(),
            plan.targetOrientation().pack()
        );
        plastic.setData(PlasticraftAttachments.ADHESIVE_TRANSIT, transit);
        plastic.setNoGravity(true);
        helper.runAfterDelay(13, () -> {
            BlockPos occupied = support.above();
            check(helper.getBlockState(occupied).is(PlasticraftBlocks.UNIVERSAL_PLASTIC.get()),
                "sideways model canceled instead of completing its upright transit");
            check(helper.getBlockEntity(occupied) instanceof BondedEntityBlockEntity bonded
                    && bonded.getPlasticOrientation().equals(plan.targetOrientation()),
                "completed upright transit lost its target orientation");
            helper.succeed();
        });
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
        check(products.size() == 1,
            "crafting-table structure produced " + products.size()
                + " entity products; state=" + ready.chamber().machineState()
                + ", batch=" + ready.chamber().batchFluidAmount()
                + ", clay=" + ready.chamber().moldedClayBalls());
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

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "31x8x13", floor = true)
    @TestHolder(description = "Automatic 3D printing preserves continuous, redstone, and single production modes")
    static void automaticPrintingPreservesProductionModes(ExtendedGameTestHelper helper) {
        PlasticMoldingChamberBlockEntity continuous = prepareAutomaticPrinter(
            helper,
            new BlockPos(5, 2, 3),
            MoldingProductionMode.CONTINUOUS,
            500
        );
        check(continuous.showsWorldProjection(), "idle printer hid its world projection");
        check(continuous.requestLock().accepted(), "continuous printer did not lock");
        completeOneVoxelPrint(continuous);
        AABB continuousBounds = PlasticMoldingChamberStructure.regionBounds(
            continuous.getBlockPos(),
            Direction.NORTH
        );
        check(productEntities(helper.getLevel(), continuousBounds).size() == 1,
            "continuous printing did not directly create one plastic entity");
        UniversalPlasticEntity continuousProduct = productEntities(helper.getLevel(), continuousBounds).getFirst();
        continuousProduct.setNoGravity(true);
        check(continuous.printingDischargeOpen()
                && continuous.printingDoorState() == MoldingPrintingDoorState.OPENING
                && continuous.waitReason() == MoldingWaitReason.PRINTING_OUTPUT_PENDING,
            "continuous printing did not begin opening its discharge for the product");
        check(!continuous.showsWorldProjection(),
            "world projection appeared while the discharge door was still opening");
        check(continuous.requestLock().reason().equals("printing_output_pending"),
            "open printing discharge accepted a manual lock request");
        for (BlockPos region : PlasticMoldingChamberStructure.regionPositions(
            continuous.getBlockPos(),
            Direction.NORTH
        )) {
            BlockState state = helper.getLevel().getBlockState(region);
            check(state.getShape(helper.getLevel(), region).isEmpty()
                    && state.getCollisionShape(helper.getLevel(), region).isEmpty(),
                "open printing discharge retained a forming-region shape");
        }
        check(continuous.machineState() == PlasticMoldingMachineState.WAITING_NEXT_CYCLE,
            "continuous printing did not wait for its next cycle");
        check(clayCount(helper.getLevel(), continuousBounds) == 0,
            "continuous printing created clay recovery drops");

        int dischargeFloorY = Mth.floor(continuousBounds.minY) - 1;
        for (int x = Mth.floor(continuousBounds.minX); x < Mth.ceil(continuousBounds.maxX); x++) {
            for (int z = Mth.floor(continuousBounds.minZ); z < Mth.ceil(continuousBounds.maxZ); z++) {
                helper.getLevel().setBlockAndUpdate(
                    new BlockPos(x, dischargeFloorY, z),
                    Blocks.STONE.defaultBlockState()
                );
            }
        }
        AABB supportedBounds = continuousProduct.getBoundingBox();
        continuousProduct.setPos(
            continuousProduct.getX(),
            continuousProduct.getY() + continuousBounds.minY - supportedBounds.minY,
            continuousProduct.getZ()
        );
        tick(continuous, 1);
        check(continuous.printingDischargeOpen(),
            "printing discharge closed while the product was held inside by blocks below");

        tick(continuous, PlasticMoldingChamberBlockEntity.PRINTING_DOOR_ANIMATION_TICKS);
        check(continuous.printingDoorState() == MoldingPrintingDoorState.OPEN,
            "printing discharge door did not finish opening");

        AABB leavingBounds = continuousProduct.getBoundingBox();
        continuousProduct.setPos(
            continuousProduct.getX(),
            continuousProduct.getY() + continuousBounds.minY - leavingBounds.maxY - 0.1D,
            continuousProduct.getZ()
        );
        tick(continuous, 1);
        check(continuous.printingDischargeOpen()
                && continuous.printingDoorState() == MoldingPrintingDoorState.CLOSING,
            "printing discharge did not begin closing after the product left");
        tick(continuous, PlasticMoldingChamberBlockEntity.PRINTING_DOOR_ANIMATION_TICKS);
        check(!continuous.printingDischargeOpen()
                && continuous.printingDoorState() == MoldingPrintingDoorState.CLOSED,
            "printing discharge stayed open after its closing animation");
        check(continuous.showsWorldProjection(),
            "world projection did not return after the discharge door fully closed");
        check(continuous.machineState() == PlasticMoldingMachineState.WAITING_NEXT_CYCLE,
            "continuous printing advanced before the discharge door closed");
        tick(continuous, 1);
        check(continuous.machineState() == PlasticMoldingMachineState.WAITING_TO_LOCK,
            "continuous printing did not prepare its next cycle after the door closed");
        tick(continuous, 1);
        check(continuous.batchFluidAmount() == 250,
            "continuous printing did not begin pumping the next batch automatically");

        PlasticMoldingChamberBlockEntity redstone = prepareAutomaticPrinter(
            helper,
            new BlockPos(15, 2, 3),
            MoldingProductionMode.REDSTONE,
            500
        );
        BlockPos redstoneSignal = redstone.getBlockPos().west();
        helper.getLevel().setBlockAndUpdate(redstoneSignal, Blocks.REDSTONE_BLOCK.defaultBlockState());
        tick(redstone, 1);
        check(redstone.isLocked() && redstone.batchFluidAmount() == 250,
            "redstone rising edge did not start the first printing batch");
        waitForPrintingDischarge(redstone);
        AABB redstoneBounds = PlasticMoldingChamberStructure.regionBounds(redstone.getBlockPos(), Direction.NORTH);
        check(redstone.machineState() == PlasticMoldingMachineState.EDITABLE,
            "redstone printing did not return to the unlocked state");
        entitiesIn(helper.getLevel(), redstoneBounds).forEach(Entity::discard);
        tick(redstone, 1);
        check(!redstone.isLocked(), "steady redstone signal retriggered printing");
        helper.getLevel().setBlockAndUpdate(redstoneSignal, Blocks.AIR.defaultBlockState());
        tick(redstone, 1);
        int doorLimit = PlasticMoldingChamberBlockEntity.PRINTING_DOOR_ANIMATION_TICKS * 2 + 2;
        while (redstone.printingDischargeOpen() && doorLimit-- > 0) tick(redstone, 1);
        check(!redstone.printingDischargeOpen(),
            "redstone printer did not close its discharge after the product left");
        helper.getLevel().setBlockAndUpdate(redstoneSignal, Blocks.REDSTONE_BLOCK.defaultBlockState());
        tick(redstone, 1);
        check(redstone.isLocked(), "a new redstone rising edge did not start the next print");

        PlasticMoldingChamberBlockEntity single = prepareAutomaticPrinter(
            helper,
            new BlockPos(25, 2, 3),
            MoldingProductionMode.SINGLE,
            250
        );
        check(single.requestLock().accepted(), "single printer did not lock manually");
        completeOneVoxelPrint(single);
        AABB singleBounds = PlasticMoldingChamberStructure.regionBounds(single.getBlockPos(), Direction.NORTH);
        check(single.machineState() == PlasticMoldingMachineState.EDITABLE,
            "single printing did not return to editing");
        entitiesIn(helper.getLevel(), singleBounds).forEach(Entity::discard);
        helper.getLevel().setBlockAndUpdate(single.getBlockPos().west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        tick(single, 1);
        check(!single.isLocked(), "single printing accepted a redstone lock request");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "31x8x13", floor = true)
    @TestHolder(description = "Production mode buttons immediately change active printing completion behavior")
    static void printingModeChangesApplyToActiveCycle(ExtendedGameTestHelper helper) {
        PlasticMoldingChamberBlockEntity redstoneToContinuous = prepareAutomaticPrinter(
            helper,
            new BlockPos(5, 2, 3),
            MoldingProductionMode.REDSTONE,
            250
        );
        BlockPos signal = redstoneToContinuous.getBlockPos().west();
        helper.getLevel().setBlockAndUpdate(signal, Blocks.REDSTONE_BLOCK.defaultBlockState());
        tick(redstoneToContinuous, 2);
        check(redstoneToContinuous.machineState() == PlasticMoldingMachineState.PROCESSING,
            "redstone printer did not enter processing before its mode change");
        check(redstoneToContinuous.setProductionMode(
            MoldingProductionMode.CONTINUOUS,
            redstoneToContinuous.revision()
        ).accepted(), "active redstone printer rejected continuous mode");
        waitForPrintingDischarge(redstoneToContinuous);
        check(redstoneToContinuous.machineState() == PlasticMoldingMachineState.WAITING_NEXT_CYCLE,
            "redstone-to-continuous change waited until another batch");
        check(redstoneToContinuous.setProductionMode(
            MoldingProductionMode.SINGLE,
            redstoneToContinuous.revision()
        ).accepted(), "discharging continuous printer rejected single mode");
        check(redstoneToContinuous.machineState() == PlasticMoldingMachineState.EDITABLE,
            "leaving continuous mode did not immediately cancel the pending next cycle");

        PlasticMoldingChamberBlockEntity continuousToSingle = prepareAutomaticPrinter(
            helper,
            new BlockPos(15, 2, 3),
            MoldingProductionMode.CONTINUOUS,
            250
        );
        check(continuousToSingle.requestLock().accepted(), "continuous printer did not lock");
        tick(continuousToSingle, 2);
        check(continuousToSingle.setProductionMode(
            MoldingProductionMode.SINGLE,
            continuousToSingle.revision()
        ).accepted(), "active continuous printer rejected single mode");
        waitForPrintingDischarge(continuousToSingle);
        check(continuousToSingle.machineState() == PlasticMoldingMachineState.EDITABLE,
            "continuous-to-single change waited until another batch");
        check(continuousToSingle.setProductionMode(
            MoldingProductionMode.CONTINUOUS,
            continuousToSingle.revision()
        ).accepted(), "discharging single printer rejected continuous mode");
        check(continuousToSingle.machineState() == PlasticMoldingMachineState.WAITING_NEXT_CYCLE,
            "continuous mode did not immediately schedule the next cycle during discharge");

        PlasticMoldingChamberBlockEntity singleToRedstone = prepareAutomaticPrinter(
            helper,
            new BlockPos(25, 2, 3),
            MoldingProductionMode.SINGLE,
            250
        );
        check(singleToRedstone.requestLock().accepted(), "single printer did not lock");
        tick(singleToRedstone, 2);
        check(singleToRedstone.setProductionMode(
            MoldingProductionMode.REDSTONE,
            singleToRedstone.revision()
        ).accepted(), "active single printer rejected redstone mode");
        waitForPrintingDischarge(singleToRedstone);
        check(singleToRedstone.machineState() == PlasticMoldingMachineState.EDITABLE
                && singleToRedstone.cycleMode() == MoldingProductionMode.REDSTONE,
            "single-to-redstone change did not apply to the completed batch");
        helper.succeed();
    }

    private static MoldedPlasticData fullData(EditableMoldingModel model, DyeColor color) {
        BakedMoldingModel baked = MoldingModelBaker.bake(model);
        int amount = Math.max(250, (baked.analysis().volume() + 3) / 4);
        return MoldedPlasticData.manufacture(model, baked, melt(amount, color), amount);
    }

    private static UniversalPlasticEntity createMoldedProduct(
        ExtendedGameTestHelper helper,
        BlockPos occupiedPos,
        MoldedPlasticData data
    ) {
        ItemStack stack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(stack, data);
        BlockPos absolutePos = helper.absolutePos(occupiedPos);
        UniversalPlasticEntity entity = new UniversalPlasticEntity(
            PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
            helper.getLevel(),
            data.geometry().placementPosition(absolutePos, PlasticEntityOrientation.DEFAULT),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            stack,
            PlasticEntityOrientation.DEFAULT
        );
        entity.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(entity), "failed to add molded plastic product");
        return entity;
    }

    private static PlasticMoldingChamberBlockEntity prepareAutomaticPrinter(
        ExtendedGameTestHelper helper,
        BlockPos relativeController,
        MoldingProductionMode mode,
        int meltAmount
    ) {
        PlasticMoldingChamberBlockEntity chamber = placeChamber(helper, relativeController, Direction.NORTH);
        check(chamber.replaceEditableModel(
            model("Printed Cell", cube("Cell", 16.0, 16.0, 16.0, 17.0, 17.0, 17.0)),
            chamber.revision()
        ).accepted(), "automatic printing model was rejected");
        check(chamber.setProductionMode(mode, chamber.revision()).accepted(),
            "automatic printing production mode was rejected");
        helper.getLevel().setBlockAndUpdate(
            chamber.getBlockPos().above(),
            PlasticraftBlocks.PLASTIC_3D_PRINTING_COMPONENT.get().defaultBlockState()
        );
        check(chamber.formingMode() == MoldingFormingMode.PRINTING,
            "printing component did not select automatic printing");
        chamber.energyStorage().receiveEnergy(MoldingPowerBridge.capacity(), false);
        check(chamber.fluidHandler().fill(
            melt(meltAmount, DyeColor.WHITE),
            IFluidHandler.FluidAction.EXECUTE
        ) == meltAmount, "automatic printer rejected its staged melt");
        return chamber;
    }

    private static void completeOneVoxelPrint(PlasticMoldingChamberBlockEntity chamber) {
        tick(chamber, 1);
        check(chamber.machineState() == PlasticMoldingMachineState.PROCESS_READY,
            "automatic printer did not fill its component at 250 mB per tick");
        tick(chamber, 1);
        check(chamber.machineState() == PlasticMoldingMachineState.PROCESSING,
            "automatic printer waited for a giant anvil");
        waitForPrintingDischarge(chamber);
    }

    private static void waitForPrintingDischarge(PlasticMoldingChamberBlockEntity chamber) {
        int limit = chamber.batchFluidCapacity() + 128;
        while (!chamber.printingDischargeOpen() && limit-- > 0) tick(chamber, 1);
        check(chamber.printingDischargeOpen(), "automatic printer did not complete its movement and output");
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

    private static double stickyTravelEast(
        ExtendedGameTestHelper helper,
        BlockPos source,
        BlockPos destination,
        Block expected
    ) {
        BlockEntity destinationEntity = helper.getLevel().getBlockEntity(helper.absolutePos(destination));
        if (destinationEntity instanceof PistonMovingBlockEntity piston
            && piston.getMovedState().is(expected)) {
            return 1.0D + piston.getXOff(1.0F);
        }
        BlockEntity sourceEntity = helper.getLevel().getBlockEntity(helper.absolutePos(source));
        if (sourceEntity instanceof PistonMovingBlockEntity piston
            && piston.getMovedState().is(expected)) {
            return piston.getXOff(1.0F);
        }
        if (helper.getBlockState(destination).is(expected)) return 1.0D;
        if (helper.getBlockState(source).is(expected)) return 0.0D;
        return 0.0D;
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
