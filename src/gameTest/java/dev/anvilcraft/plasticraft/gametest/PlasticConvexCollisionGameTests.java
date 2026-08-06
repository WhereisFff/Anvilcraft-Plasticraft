package dev.anvilcraft.plasticraft.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.entity.collision.BondedPlasticShapeIndex;
import dev.anvilcraft.plasticraft.entity.collision.BuiltInPlasticEntityModels;
import dev.anvilcraft.plasticraft.entity.collision.PlasticCarrierPrediction;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexCollisionResolver;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexShape;
import dev.anvilcraft.plasticraft.entity.collision.PlasticModelCube;
import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityPhysics;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingTransform;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 全部移动者与方块化塑料共享真实凸体 SAT 的专项回归。 */
public final class PlasticConvexCollisionGameTests {
    private static final double EPSILON = 1.0E-6D;
    private static final double MODEL_EPSILON = 1.0E-5D;

    private PlasticConvexCollisionGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "11x8x11", floor = true)
    @TestHolder(description = "Every mover passes through a bonded sloped cube's empty compatibility corner")
    static void allMoversPassThroughBondedSlopeEmptyCorner(ExtendedGameTestHelper helper) {
        BondedPlasticShapeIndex.Entry slope = createBondedSlope(helper, new BlockPos(5, 1, 5));
        AABB compatibility = slope.collisionShape().bounds();
        List<Entity> movers = createMovers(helper);

        for (Entity mover : movers) {
            double halfWidth = mover.getBbWidth() * 0.5D;
            double startY = compatibility.maxY + 0.05D;
            mover.setPos(
                compatibility.minX + halfWidth + 0.05D,
                startY,
                compatibility.getCenter().z
            );
            mover.move(MoverType.SELF, new Vec3(0.0D, -0.25D, 0.0D));
            check(
                startY - mover.getY() > 0.20D,
                mover.getType() + " was stopped by the sloped cube's empty compatibility corner"
            );
            check(
                !intersectsAny(mover.getBoundingBox(), slope.convexShapes()),
                mover.getType() + " crossed into the real sloped cube while traversing its empty corner"
            );
            mover.discard();
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "11x8x11", floor = true)
    @TestHolder(description = "Every mover settles on a bonded real slope without sinking or jittering")
    static void allMoversSettleOnBondedSlope(ExtendedGameTestHelper helper) {
        BondedPlasticShapeIndex.Entry slope = createBondedSlope(helper, new BlockPos(5, 1, 5));
        AABB compatibility = slope.collisionShape().bounds();
        List<Entity> movers = createMovers(helper);

        for (Entity mover : movers) {
            mover.setPos(
                compatibility.getCenter().x + compatibility.getXsize() * 0.28D,
                compatibility.maxY + 0.2D,
                compatibility.getCenter().z
            );
            mover.move(MoverType.SELF, new Vec3(0.0D, -3.0D, 0.0D));
            double standingY = mover.getY();
            check(
                standingY < compatibility.maxY - 0.18D,
                mover.getType() + " stood on the compatibility box instead of the real slope"
            );
            check(
                standingY > compatibility.minY + 0.2D,
                mover.getType() + " fell through the real slope"
            );
            for (int probe = 0; probe < 4; probe++) {
                mover.move(MoverType.SELF, new Vec3(0.0D, -0.08D, 0.0D));
                check(
                    Math.abs(mover.getY() - standingY) <= EPSILON,
                    mover.getType() + " sank or jittered after settling on the real slope"
                );
                check(mover.verticalCollisionBelow, mover.getType() + " lost lower collision on the real slope");
            }
            mover.discard();
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "11x8x11", floor = true)
    @TestHolder(description = "Player follows the real bonded slope while crossing its apex")
    static void playerFollowsBondedSlopeDuringHorizontalMove(ExtendedGameTestHelper helper) {
        BondedPlasticShapeIndex.Entry slope = createBondedSlope(helper, new BlockPos(5, 1, 5));
        AABB compatibility = slope.collisionShape().bounds();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setNoGravity(true);
        double startX = compatibility.getCenter().x + compatibility.getXsize() * 0.28D;
        player.setPos(startX, compatibility.maxY + 0.2D, compatibility.getCenter().z);
        player.move(MoverType.SELF, new Vec3(0.0D, -3.0D, 0.0D));

        double previousY = player.getY();
        boolean ascended = false;
        boolean descended = false;
        for (int tick = 0; tick < 20; tick++) {
            player.move(MoverType.SELF, new Vec3(-0.08D, -0.08D, 0.0D));
            double verticalMovement = player.getY() - previousY;
            check(
                Math.abs(verticalMovement) <= 0.081D,
                "player used a compatibility-box step instead of following the slope: movement="
                    + verticalMovement + ", position=" + player.position()
            );
            check(
                !intersectsAny(player.getBoundingBox(), slope.convexShapes()),
                "player entered the real sloped cube while crossing its apex"
            );
            check(
                PlasticConvexCollisionResolver.hasBlockSupport(
                    player,
                    player.getBoundingBox(),
                    Direction.DOWN,
                    1.0E-3D
                ),
                "player lost the real sloped surface while crossing its apex: tick=" + tick
                    + ", movement=" + verticalMovement + ", position=" + player.position()
            );
            ascended |= verticalMovement > EPSILON;
            descended |= verticalMovement < -EPSILON;
            previousY = player.getY();
        }
        check(ascended, "player never followed the uphill half of the slope");
        check(descended, "player never followed the downhill half of the slope");
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "11x8x11", floor = true)
    @TestHolder(description = "Every mover remains stable on a dynamic sloped plastic cube")
    static void allMoversSettleOnDynamicSlope(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity slope = createDynamicPlastic(
            helper,
            new Vec3(5.5D, 1.0D, 5.5D),
            slopedModel()
        );
        AABB compatibility = slope.getBoundingBox();
        List<Entity> movers = createMovers(helper);

        for (Entity mover : movers) {
            mover.setPos(
                compatibility.getCenter().x + compatibility.getXsize() * 0.28D,
                compatibility.maxY + 0.2D,
                compatibility.getCenter().z
            );
            mover.move(MoverType.SELF, new Vec3(0.0D, -3.0D, 0.0D));
            double standingY = mover.getY();
            check(
                PlasticEntityPhysics.hasSurfaceSupport(
                    mover,
                    mover.getBoundingBox(),
                    slope,
                    Direction.DOWN
                ),
                mover.getType() + " did not settle on the dynamic real slope"
            );
            check(
                standingY < compatibility.maxY - 0.1D,
                mover.getType() + " stood on the dynamic slope's compatibility box"
            );
            for (int probe = 0; probe < 4; probe++) {
                mover.move(MoverType.SELF, new Vec3(0.0D, -0.08D, 0.0D));
                check(
                    Math.abs(mover.getY() - standingY) <= EPSILON,
                    mover.getType() + " jittered on the dynamic real slope"
                );
                check(
                    PlasticEntityPhysics.hasSurfaceSupport(
                        mover,
                        mover.getBoundingBox(),
                        slope,
                        Direction.DOWN
                    ),
                    mover.getType() + " lost the dynamic real slope"
                );
            }
            mover.discard();
        }
        slope.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "11x8x11", floor = true)
    @TestHolder(description = "Server movement packets accept a player standing on a dynamic real slope")
    static void serverPlayerMovementAcceptsDynamicSlopeSurface(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity slope = createDynamicPlastic(
            helper,
            new Vec3(5.5D, 1.0D, 5.5D),
            slopedModel()
        );
        AABB compatibility = slope.getBoundingBox();
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.CREATIVE);
        ClientboundPlayerPositionPacket initialPosition = player
            .getOutboundPackets(ClientboundPlayerPositionPacket.class)
            .reduce((first, second) -> second)
            .orElseThrow(() -> new GameTestAssertException("mock player login did not send an initial position"));
        player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(initialPosition.getId()));
        player.clearOutboundPackets();
        player.setNoGravity(true);
        player.moveTo(
            compatibility.getCenter().x + compatibility.getXsize() * 0.28D,
            compatibility.maxY + 0.2D,
            compatibility.getCenter().z
        );
        player.move(MoverType.SELF, new Vec3(0.0D, -3.0D, 0.0D));
        Vec3 target = player.position();
        AABB targetBox = player.getBoundingBox();
        check(targetBox.intersects(compatibility), "network test target missed the compatibility AABB");
        check(
            !intersectsAny(targetBox, slope.plasticraft$getCollisionBox().convexComponents()),
            "network test target entered the dynamic real slope"
        );

        player.moveTo(target.x, compatibility.maxY + 0.1D, target.z);
        player.connection.resetPosition();
        player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(
            target.x,
            target.y,
            target.z,
            true
        ));
        check(
            player.position().distanceToSqr(target) <= EPSILON * EPSILON,
            "server movement validation returned the player to the compatibility AABB edge"
        );
        slope.discard();
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "11x8x11", floor = true)
    @TestHolder(description = "Sneaking uses the dynamic real slope instead of its compatibility edge")
    static void sneakingPlayerStopsAtDynamicSlopeEdge(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity slope = createDynamicPlastic(
            helper,
            new Vec3(5.5D, 1.0D, 5.5D),
            slopedModel()
        );
        AABB compatibility = slope.getBoundingBox();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.setPos(
            compatibility.getCenter().x + compatibility.getXsize() * 0.2D,
            compatibility.maxY + 0.2D,
            compatibility.getCenter().z
        );
        player.move(MoverType.SELF, new Vec3(0.0D, -3.0D, 0.0D));
        AABB startBox = player.getBoundingBox();
        double requestedX = 0.9D;
        AABB fullMovementProbe = new AABB(
            startBox.minX + requestedX,
            startBox.minY - player.maxUpStep() - 1.0E-5F,
            startBox.minZ,
            startBox.maxX + requestedX,
            startBox.minY,
            startBox.maxZ
        );
        check(fullMovementProbe.intersects(compatibility), "sneak test missed the compatibility edge");
        check(
            !helper.getLevel().noCollision(player, fullMovementProbe),
            "sneak test compatibility shape did not cover the unsupported target"
        );
        check(
            PlasticConvexCollisionResolver.noCollisionWithExactPlastic(
                player,
                fullMovementProbe,
                helper.getLevel()
            ),
            "sneak test target unexpectedly retained real slope support"
        );

        double startX = player.getX();
        player.setShiftKeyDown(true);
        player.setOnGround(true);
        player.move(MoverType.SELF, new Vec3(requestedX, 0.0D, 0.0D));
        double actualX = player.getX() - startX;
        check(actualX > 0.1D, "sneaking blocked all movement on the dynamic slope");
        check(actualX < requestedX - 0.05D, "sneaking accepted support from the compatibility edge");
        check(
            PlasticConvexCollisionResolver.sweep(
                PlasticConvexCollisionResolver.collisionShapes(player, player.getBoundingBox()),
                slope.plasticraft$getCollisionBox().convexComponents(),
                new Vec3(0.0D, -player.maxUpStep() - 1.0E-3D, 0.0D)
            ) != null,
            "sneaking stopped beyond the dynamic real slope's supported edge"
        );
        slope.discard();
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("17x6x17")
    @TestHolder(description = "Player and mob pushes follow a rotated cube's real horizontal face normal")
    static void playerAndMobPushRotatedCubeAlongFaceNormal(ExtendedGameTestHelper helper) {
        for (int x = 1; x < 16; x++) {
            for (int z = 1; z < 16; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        }
        EditableMoldingModel model = horizontallyRotatedCubeModel();
        UniversalPlasticEntity playerTarget = createDynamicPlastic(helper, new Vec3(5.5D, 2.0D, 5.5D), model);
        UniversalPlasticEntity mobTarget = createDynamicPlastic(helper, new Vec3(11.5D, 2.0D, 11.5D), model);
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Creeper mob = EntityType.CREEPER.create(helper.getLevel());
        if (mob == null) throw new GameTestAssertException("failed to create rotated-face mob pusher");
        mob.setNoAi(true);
        mob.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(mob), "failed to add rotated-face mob pusher");
        player.setNoGravity(true);

        FacePushFixture playerFixture = placePusherAtDiagonalFace(playerTarget, player);
        FacePushFixture mobFixture = placePusherAtDiagonalFace(mobTarget, mob);
        helper.runAfterDelay(3, () -> {
            assertFixtureStayedAtRest(playerFixture, "player");
            assertFixtureStayedAtRest(mobFixture, "mob");
            assertPushFollowsFaceNormal(playerFixture, "player");
            assertPushFollowsFaceNormal(mobFixture, "mob");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "11x7x11", floor = true)
    @TestHolder(description = "An upside-down cauldron follows every horizontal step of its supporting player's head")
    static void upsideDownCauldronImmediatelyFollowsPlayerHead(ExtendedGameTestHelper helper) {
        for (int x = 1; x < 10; x++) {
            for (int z = 1; z < 10; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        }
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 floorPosition = helper.absoluteVec(new Vec3(4.5D, 2.0D, 5.5D));
        player.moveTo(floorPosition.x, floorPosition.y, floorPosition.z);
        player.setOnGround(true);

        HardenedResinCauldronEntity cauldron = new HardenedResinCauldronEntity(
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get(),
            helper.getLevel(),
            helper.absoluteVec(new Vec3(5.5D, 4.0D, 5.5D)),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            new PlasticEntityOrientation(Direction.DOWN, 0)
        );
        check(helper.getLevel().addFreshEntity(cauldron), "failed to add upside-down cauldron");
        AABB cauldronBounds = cauldron.getBoundingBox();
        double rimOverlap = 0.06D;
        player.moveTo(
            cauldronBounds.minX - player.getBbWidth() * 0.5D + rimOverlap,
            floorPosition.y,
            cauldronBounds.getCenter().z
        );
        cauldron.setPos(
            cauldron.getX(),
            cauldron.getY() + player.getBoundingBox().maxY - cauldronBounds.minY,
            cauldron.getZ()
        );

        Vec3[] relativeStart = {Vec3.ZERO};
        int[] step = {0};
        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                check(
                    PlasticEntityPhysics.hasImmediateEntityContact(cauldron, player, Direction.DOWN),
                    "upside-down cauldron did not settle on the player's head"
                );
                relativeStart[0] = cauldron.position().subtract(player.position());
            })
            .thenExecuteFor(6, () -> {
                int currentStep = step[0]++;
                check(player.getPose() == Pose.STANDING,
                    "upside-down cauldron forced the player into " + player.getPose() + " before step " + currentStep);
                Vec3 playerStart = player.position();
                Vec3 cauldronStart = cauldron.position();
                player.setOnGround(true);
                player.move(MoverType.SELF, new Vec3(0.08D, -0.08D, 0.0D));
                Vec3 playerMovement = player.position().subtract(playerStart);
                Vec3 cauldronMovement = cauldron.position().subtract(cauldronStart);
                check(
                    cauldronMovement.distanceToSqr(playerMovement) <= EPSILON * EPSILON,
                    "upside-down cauldron lagged behind its supporting head at step " + currentStep
                        + ": player=" + playerMovement + ", cauldron=" + cauldronMovement
                );
                check(
                    cauldron.position().subtract(player.position()).distanceToSqr(relativeStart[0])
                        <= EPSILON * EPSILON,
                    "upside-down cauldron changed its offset from the player's head at step " + currentStep
                );
                check(
                    PlasticEntityPhysics.hasImmediateEntityContact(cauldron, player, Direction.DOWN),
                    "upside-down cauldron lost direct head support at step " + currentStep
                );
                check(player.getPose() == Pose.STANDING,
                    "upside-down cauldron forced the player into " + player.getPose() + " at step " + currentStep);
            })
            .thenExecute(() -> {
                cauldron.discard();
                player.discard();
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "11x7x11", floor = true)
    @TestHolder(description = "A player under an upside-down cauldron rim can reverse without being crushed")
    static void playerUnderUpsideDownCauldronRimCanReverseWithoutBeingCrushed(ExtendedGameTestHelper helper) {
        for (int x = 1; x < 10; x++) {
            for (int z = 1; z < 10; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        }
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 playerPosition = helper.absoluteVec(new Vec3(5.5D, 2.0D, 5.5D));
        player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setOnGround(true);

        HardenedResinCauldronEntity cauldron = new HardenedResinCauldronEntity(
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get(),
            helper.getLevel(),
            helper.absoluteVec(new Vec3(5.5D, 4.0D, 5.5D)),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            new PlasticEntityOrientation(Direction.DOWN, 0)
        );
        check(helper.getLevel().addFreshEntity(cauldron), "failed to add upside-down cauldron");
        AABB cauldronBounds = cauldron.getBoundingBox();
        double rimOverlap = 0.18D;
        player.moveTo(
            cauldronBounds.minX - player.getBbWidth() * 0.5D + rimOverlap,
            playerPosition.y,
            cauldronBounds.getCenter().z
        );
        AABB playerBox = player.getBoundingBox();
        cauldron.setPos(
            cauldron.getX(),
            cauldron.getY() + playerBox.maxY - cauldronBounds.minY,
            cauldron.getZ()
        );
        runUpsideDownCauldronReversalTest(helper, player, cauldron, "rim");
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "11x7x11", floor = true)
    @TestHolder(description = "A player touching an upside-down cauldron's inner ceiling can reverse safely")
    static void playerInsideUpsideDownCauldronCanReverseWithoutBeingCrushed(ExtendedGameTestHelper helper) {
        for (int x = 1; x < 10; x++) {
            for (int z = 1; z < 10; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        }
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 playerPosition = helper.absoluteVec(new Vec3(5.5D, 2.0D, 5.5D));
        player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setOnGround(true);

        HardenedResinCauldronEntity cauldron = new HardenedResinCauldronEntity(
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get(),
            helper.getLevel(),
            helper.absoluteVec(new Vec3(5.5D, 4.0D, 5.5D)),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            new PlasticEntityOrientation(Direction.DOWN, 0)
        );
        check(helper.getLevel().addFreshEntity(cauldron), "failed to add upside-down cauldron");
        AABB playerBox = player.getBoundingBox();
        double innerCeilingMinY = cauldron.plasticraft$getCollisionBox().convexComponents().stream()
            .map(PlasticConvexShape::bounds)
            .filter(bounds -> bounds.minX <= playerBox.minX + EPSILON
                && bounds.maxX >= playerBox.maxX - EPSILON
                && bounds.minZ <= playerBox.minZ + EPSILON
                && bounds.maxZ >= playerBox.maxZ - EPSILON)
            .mapToDouble(bounds -> bounds.minY)
            .min()
            .orElseThrow(() -> new GameTestAssertException(
                "upside-down cauldron exposed no inner ceiling over the player"
            ));
        cauldron.setPos(
            cauldron.getX(),
            cauldron.getY() + playerBox.maxY - innerCeilingMinY,
            cauldron.getZ()
        );
        assertPredictionReversal(new Vec3(0.12D, 0.0D, 0.0D));
        assertPredictionReversal(new Vec3(0.0D, 0.0D, 0.12D));
        runUpsideDownCauldronReversalTest(helper, player, cauldron, "inner ceiling");
    }

    private static void assertAxisCandidatePruning(Direction.Axis axis, double requestedMovement) {
        List<PlasticConvexShape> moving = List.of(axisBox(axis, 0.0D, 1.0D, 0.0D, 1.0D, 0.0D, 1.0D));
        boolean positive = requestedMovement > 0.0D;
        List<PlasticConvexShape> obstacles = new ArrayList<>(256);
        obstacles.add(axisBox(
            axis,
            positive ? 2.25D : -1.5D,
            positive ? 2.5D : -1.25D,
            0.0D,
            1.0D,
            0.0D,
            1.0D
        ));
        obstacles.add(axisBox(
            axis,
            positive ? 2.0D : -1.25D,
            positive ? 2.25D : -1.0D,
            0.0D,
            1.0D,
            0.0D,
            1.0D
        ));
        addAxisDecoys(obstacles, axis, requestedMovement, 126, 128);

        PlasticConvexCollisionResolver.AxisCollisionResult result =
            PlasticConvexCollisionResolver.collideAxisWithStats(moving, obstacles, axis, requestedMovement);
        assertSameMovementAsSourceOrder(moving, obstacles, axis, requestedMovement, result.movement());
        check(result.stats().inputPairs() == 256, axis + " candidate index reported an incorrect input pair count");
        check(
            result.stats().broadPhaseCandidatePairs() > 0
                && result.stats().broadPhaseCandidatePairs() < result.stats().inputPairs(),
            axis + " candidate index did not prune the broad phase for movement " + requestedMovement
        );
        check(
            result.stats().satPairs() > 0
                && result.stats().satPairs() < result.stats().broadPhaseCandidatePairs(),
            axis + " candidate index did not prune SAT pairs for movement " + requestedMovement
        );
        check(
            Math.abs(result.movement()) < Math.abs(requestedMovement)
                && Math.signum(result.movement()) == Math.signum(requestedMovement),
            axis + " candidate index lost the real blocking obstacle for movement " + requestedMovement
        );
    }

    private static void assertLongSlopedCandidatePruning() {
        List<PlasticConvexShape> moving = List.of(PlasticConvexShape.box(
            new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D)
        ));
        PlasticConvexShape slope = PlasticConvexShape.box(
            new AABB(2.8D, -3.5D, 0.4D, 2.95D, 4.5D, 0.6D)
        ).rotateAroundAxis(
            Direction.Axis.Z,
            Math.toRadians(22.5D),
            new Vec3(2.875D, 0.5D, 0.5D)
        );
        List<PlasticConvexShape> obstacles = new ArrayList<>(256);
        obstacles.add(slope);
        addAxisDecoys(obstacles, Direction.Axis.X, 5.0D, 127, 128);

        PlasticConvexCollisionResolver.AxisCollisionResult result =
            PlasticConvexCollisionResolver.collideAxisWithStats(
                moving,
                obstacles,
                Direction.Axis.X,
                5.0D
            );
        assertSameMovementAsSourceOrder(moving, obstacles, Direction.Axis.X, 5.0D, result.movement());
        check(result.stats().inputPairs() == 256, "long slope candidate index lost its input pair count");
        check(
            result.stats().broadPhaseCandidatePairs() < result.stats().inputPairs(),
            "long slope candidate index did not prune distant axis intervals"
        );
        check(
            result.stats().satPairs() > 0
                && result.stats().satPairs() < result.stats().broadPhaseCandidatePairs(),
            "long slope candidate index did not prune transverse SAT pairs"
        );
        check(result.movement() > 0.0D && result.movement() < 5.0D,
            "long sloped obstacle did not clip the requested movement");
    }

    private static void assertDisconnectedCandidatePruning() {
        List<PlasticConvexShape> moving = new ArrayList<>(8);
        List<PlasticConvexShape> obstacles = new ArrayList<>(256);
        for (int index = 0; index < 8; index++) {
            double offset = index * 3.0D;
            moving.add(PlasticConvexShape.box(new AABB(
                0.0D,
                offset,
                0.0D,
                1.0D,
                offset + 1.0D,
                1.0D
            )));
            obstacles.add(PlasticConvexShape.box(new AABB(
                2.0D,
                offset,
                0.0D,
                2.25D,
                offset + 1.0D,
                1.0D
            )));
        }
        addAxisDecoys(obstacles, Direction.Axis.X, 4.0D, 120, 128);

        PlasticConvexCollisionResolver.AxisCollisionResult result =
            PlasticConvexCollisionResolver.collideAxisWithStats(
                moving,
                obstacles,
                Direction.Axis.X,
                4.0D
            );
        assertSameMovementAsSourceOrder(moving, obstacles, Direction.Axis.X, 4.0D, result.movement());
        check(result.stats().inputPairs() == 2048, "disconnected candidate index lost its input pair count");
        check(
            result.stats().broadPhaseCandidatePairs() < result.stats().inputPairs(),
            "disconnected candidate index did not prune distant axis intervals"
        );
        check(
            result.stats().satPairs() > 0
                && result.stats().satPairs() < result.stats().broadPhaseCandidatePairs(),
            "disconnected candidate index did not prune transverse SAT pairs"
        );
        check(result.movement() > 0.0D && result.movement() < 4.0D,
            "disconnected structure lost its nearest blocking obstacle");
    }

    private static void addAxisDecoys(
        List<PlasticConvexShape> obstacles,
        Direction.Axis axis,
        double requestedMovement,
        int transverseCount,
        int distantCount
    ) {
        boolean positive = requestedMovement > 0.0D;
        for (int index = 0; index < transverseCount; index++) {
            obstacles.add(axisBox(
                axis,
                positive ? 1.5D : -0.75D,
                positive ? 1.75D : -0.5D,
                10.0D + index * 2.0D,
                11.0D + index * 2.0D,
                0.0D,
                1.0D
            ));
        }
        for (int index = 0; index < distantCount; index++) {
            double minimum = positive ? 12.0D + index : -12.25D - index;
            obstacles.add(axisBox(
                axis,
                minimum,
                minimum + 0.25D,
                0.0D,
                1.0D,
                0.0D,
                1.0D
            ));
        }
    }

    private static PlasticConvexShape axisBox(
        Direction.Axis axis,
        double minimum,
        double maximum,
        double firstMinimum,
        double firstMaximum,
        double secondMinimum,
        double secondMaximum
    ) {
        AABB box = switch (axis) {
            case X -> new AABB(
                minimum, firstMinimum, secondMinimum,
                maximum, firstMaximum, secondMaximum
            );
            case Y -> new AABB(
                firstMinimum, minimum, secondMinimum,
                firstMaximum, maximum, secondMaximum
            );
            case Z -> new AABB(
                firstMinimum, secondMinimum, minimum,
                firstMaximum, secondMaximum, maximum
            );
        };
        return PlasticConvexShape.box(box);
    }

    private static void assertSameMovementAsSourceOrder(
        List<PlasticConvexShape> movingShapes,
        List<PlasticConvexShape> obstacles,
        Direction.Axis axis,
        double requestedMovement,
        double indexedMovement
    ) {
        double sourceOrderMovement = requestedMovement;
        for (PlasticConvexShape moving : movingShapes) {
            for (PlasticConvexShape obstacle : obstacles) {
                sourceOrderMovement = PlasticConvexCollisionResolver.collideAxis(
                    moving,
                    obstacle,
                    axis,
                    sourceOrderMovement
                );
            }
        }
        check(
            Math.abs(indexedMovement - sourceOrderMovement) <= 1.0E-9D,
            axis + " candidate ordering changed movement from "
                + sourceOrderMovement + " to " + indexedMovement
        );
    }

    private static EditableMoldingModel slopedModel() {
        MoldingElement source = MoldingElement.cube(
            "Slope",
            new MoldingVec3(8.0D, 8.0D, 8.0D),
            new MoldingVec3(40.0D, 40.0D, 40.0D)
        );
        MoldingElement rotated = new MoldingElement(
            source.id(),
            source.name(),
            source.groupId(),
            source.from(),
            source.to(),
            new MoldingTransform(
                MoldingVec3.ZERO,
                new MoldingVec3(0.0D, 0.0D, 45.0D),
                MoldingVec3.ONE,
                source.transform().pivot()
            ),
            source.visible(),
            source.locked()
        );
        return EditableMoldingModel.empty()
            .withName("Bonded SAT Slope")
            .withElements(List.of(rotated));
    }

    private static EditableMoldingModel horizontallyRotatedCubeModel() {
        MoldingElement source = MoldingElement.cube(
            "Horizontal Diagonal",
            new MoldingVec3(8.0D, 0.0D, 8.0D),
            new MoldingVec3(24.0D, 16.0D, 24.0D)
        );
        MoldingElement rotated = new MoldingElement(
            source.id(),
            source.name(),
            source.groupId(),
            source.from(),
            source.to(),
            new MoldingTransform(
                MoldingVec3.ZERO,
                new MoldingVec3(0.0D, 45.0D, 0.0D),
                MoldingVec3.ONE,
                source.transform().pivot()
            ),
            source.visible(),
            source.locked()
        );
        return EditableMoldingModel.empty()
            .withName("Horizontal Face Normal")
            .withElements(List.of(rotated));
    }

    private static UniversalPlasticEntity createDynamicPlastic(
        ExtendedGameTestHelper helper,
        Vec3 relativePosition,
        EditableMoldingModel model
    ) {
        BakedMoldingModel baked = MoldingModelBaker.bake(model);
        int amount = Math.max(250, (baked.analysis().volume() + 3) / 4);
        FluidStack melt = new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), amount);
        PlasticMeltColor.set(melt, DyeColor.CYAN);
        MoldedPlasticData data = MoldedPlasticData.manufacture(model, baked, melt, amount);

        ItemStack drop = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(drop, data);
        PlasticMeltColor.set(drop, DyeColor.CYAN);
        BlockState displayState = PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState()
            .setValue(DyeableMaterial.COLOR, DyeColor.CYAN);
        UniversalPlasticEntity product = new UniversalPlasticEntity(
            PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
            helper.getLevel(),
            helper.absoluteVec(relativePosition),
            displayState,
            drop,
            PlasticEntityOrientation.DEFAULT
        );
        product.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(product), "failed to add dynamic plastic test product");
        return product;
    }

    private static BondedPlasticShapeIndex.Entry createBondedSlope(
        ExtendedGameTestHelper helper,
        BlockPos relativeAnchor
    ) {
        UniversalPlasticEntity product = createDynamicPlastic(
            helper,
            Vec3.atBottomCenterOf(relativeAnchor),
            slopedModel()
        );
        BlockState displayState = product.getDisplayState();

        BlockState bondedState = displayState.setValue(AbstractPlasticEntityBlock.BONDED, true);
        helper.setBlock(relativeAnchor, bondedState);
        if (!(helper.getBlockEntity(relativeAnchor) instanceof BondedEntityBlockEntity bonded)) {
            throw new GameTestAssertException("bonded sloped product block entity was missing");
        }
        check(
            bonded.initialize(
                product,
                displayState,
                Direction.UP,
                PlasticEntityOrientation.DEFAULT,
                true
            ),
            "failed to initialize the bonded sloped product"
        );
        product.discard();

        BlockPos absoluteAnchor = helper.absolutePos(relativeAnchor);
        BondedPlasticShapeIndex.Entry entry = BondedPlasticShapeIndex.collisionEntries(
            helper.getLevel(),
            new AABB(absoluteAnchor).inflate(4.0D)
        ).stream().filter(candidate -> candidate.anchor().equals(absoluteAnchor)).findFirst().orElseThrow(() ->
            new GameTestAssertException("bonded sloped product was absent from the convex index")
        );
        check(entry.convexShapes().size() == 1, "single source cube did not produce one bonded convex body");
        check(
            entry.convexShapes().getFirst().faceNormals().stream().anyMatch(normal ->
                Math.abs(normal.x) > EPSILON && Math.abs(normal.y) > EPSILON
            ),
            "bonded source cube lost its diagonal face normal"
        );
        return entry;
    }

    private static FacePushFixture placePusherAtDiagonalFace(
        UniversalPlasticEntity target,
        Entity pusher
    ) {
        PlasticConvexShape shape = target.plasticraft$getCollisionBox().convexComponents().getFirst();
        Vec3 outwardNormal = shape.faceNormals().stream().filter(normal ->
            Math.abs(normal.y) <= EPSILON
                && Math.abs(normal.x) > 0.2D
                && Math.abs(normal.z) > 0.2D
        ).findFirst().orElseThrow(() ->
            new GameTestAssertException("rotated cube exposed no horizontal diagonal face normal")
        ).normalize();
        AABB targetBounds = target.getBoundingBox();
        Vec3 targetBase = new Vec3(
            targetBounds.getCenter().x,
            targetBounds.minY,
            targetBounds.getCenter().z
        );
        pusher.setPos(targetBase);
        AABB pusherBox = pusher.getBoundingBox();
        double projectionRadius = Math.abs(outwardNormal.x) * pusherBox.getXsize() * 0.5D
            + Math.abs(outwardNormal.z) * pusherBox.getZsize() * 0.5D;
        double faceDistance = shape.project(outwardNormal).maximum()
            - targetBase.dot(outwardNormal)
            + projectionRadius
            + PlasticEntityPhysics.FACE_EPSILON * 0.5D;
        pusher.setPos(targetBase.add(outwardNormal.scale(faceDistance)));
        pusher.setOnGround(true);
        return new FacePushFixture(
            target,
            pusher,
            outwardNormal,
            target.position(),
            pusher.position()
        );
    }

    private static void assertFixtureStayedAtRest(FacePushFixture fixture, String pusherName) {
        check(
            fixture.target().position().subtract(fixture.targetStart()).horizontalDistanceSqr() <= 1.0E-6D,
            pusherName + " contact moved the resting rotated cube"
        );
        check(
            fixture.pusher().position().subtract(fixture.pusherStart()).horizontalDistanceSqr() <= 1.0E-6D,
            pusherName + " was repelled from the resting rotated cube"
        );
    }

    private static void assertPushFollowsFaceNormal(FacePushFixture fixture, String pusherName) {
        Vec3 pushDirection = fixture.outwardNormal().scale(-1.0D);
        Vec3 request = pushDirection.scale(0.18D);
        fixture.pusher().setOnGround(true);
        fixture.pusher().move(MoverType.SELF, request);
        fixture.pusher().move(MoverType.SELF, request);
        Vec3 movement = fixture.target().position().subtract(fixture.targetStart());
        double normalDistance = movement.dot(pushDirection);
        Vec3 residual = movement.subtract(pushDirection.scale(normalDistance));
        check(normalDistance > 0.30D, pusherName + " did not push the rotated cube continuously");
        check(
            residual.horizontalDistanceSqr() < 0.03D * 0.03D && Math.abs(movement.y) < 0.03D,
            pusherName + " push did not follow the rotated cube's real face normal: " + movement
        );
        check(
            Math.abs(movement.x) > 0.12D && Math.abs(movement.z) > 0.12D,
            pusherName + " push collapsed the diagonal face normal onto a world axis"
        );
    }

    private static BondedPlasticShapeIndex.Entry bondBuiltInModel(
        ExtendedGameTestHelper helper,
        BondedModel model
    ) {
        AbstractPlasticEntity entity = model.entity();
        entity.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(entity), "failed to add " + entity.getType() + " source entity");
        BlockState displayState = entity.getDisplayState();
        helper.setBlock(
            model.relativeAnchor(),
            displayState.setValue(AbstractPlasticEntityBlock.BONDED, true)
        );
        if (!(helper.getBlockEntity(model.relativeAnchor()) instanceof BondedEntityBlockEntity bonded)) {
            throw new GameTestAssertException(entity.getType() + " bonded block entity was missing");
        }
        check(
            bonded.initialize(
                entity,
                displayState,
                Direction.UP,
                PlasticEntityOrientation.DEFAULT,
                true
            ),
            "failed to initialize bonded " + entity.getType()
        );
        entity.discard();
        BlockPos absoluteAnchor = helper.absolutePos(model.relativeAnchor());
        return BondedPlasticShapeIndex.collisionEntries(
            helper.getLevel(),
            new AABB(absoluteAnchor).inflate(4.0D)
        ).stream().filter(entry -> entry.anchor().equals(absoluteAnchor)).findFirst().orElseThrow(() ->
            new GameTestAssertException(entity.getType() + " was absent from the bonded convex index")
        );
    }

    private static List<Entity> createMovers(ExtendedGameTestHelper helper) {
        List<Entity> movers = new ArrayList<>(4);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setNoGravity(true);
        movers.add(player);

        Creeper creeper = EntityType.CREEPER.create(helper.getLevel());
        if (creeper == null) throw new GameTestAssertException("failed to create creeper collision probe");
        creeper.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(creeper), "failed to add creeper collision probe");
        movers.add(creeper);

        ItemEntity item = new ItemEntity(helper.getLevel(), 0.0D, 0.0D, 0.0D, new ItemStack(Items.STICK));
        item.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(item), "failed to add item collision probe");
        movers.add(item);

        UniversalPlasticEntity plastic = new UniversalPlasticEntity(
            PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
            helper.getLevel(),
            Vec3.ZERO,
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        plastic.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(plastic), "failed to add plastic collision probe");
        movers.add(plastic);
        return movers;
    }

    private static List<PlasticConvexShape> boxGrid(int sizeX, int sizeY, int sizeZ, boolean checkerboard) {
        List<PlasticConvexShape> result = new ArrayList<>();
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    if (checkerboard && (x + y + z & 1) != 0) continue;
                    result.add(PlasticConvexShape.box(new AABB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D)));
                }
            }
        }
        return List.copyOf(result);
    }

    private static void assertModelResource(BuiltInPlasticEntityModels.Model model) {
        String resourceName = "assets/anvilcraftplasticraft/models/block/" + model.resourceName() + ".json";
        Path resourcePath = ModList.get()
            .getModFileById(AnvilcraftPlasticraft.MOD_ID)
            .getFile()
            .findResource(resourceName.split("/"));
        try (InputStream stream = Files.newInputStream(resourcePath)) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
            JsonArray elements = root.getAsJsonArray("elements");
            check(elements != null, model.resourceName() + " model has no elements array");
            check(
                elements.size() == model.cubes().size(),
                model.resourceName() + " element count diverged from its server collision cubes"
            );
            for (int index = 0; index < elements.size(); index++) {
                JsonObject element = elements.get(index).getAsJsonObject();
                PlasticModelCube cube = model.cubes().get(index);
                assertVector(cube.from(), element.getAsJsonArray("from"), model.resourceName(), index, "from");
                assertVector(cube.to(), element.getAsJsonArray("to"), model.resourceName(), index, "to");
                if (!element.has("rotation")) {
                    check(
                        Math.abs(cube.rotationDegrees()) <= MODEL_EPSILON,
                        model.resourceName() + " element " + index + " gained a server-only rotation"
                    );
                    continue;
                }
                JsonObject rotation = element.getAsJsonObject("rotation");
                double degrees = rotation.get("angle").getAsDouble();
                check(
                    Math.abs(cube.rotationDegrees() - degrees) <= MODEL_EPSILON,
                    model.resourceName() + " element " + index + " rotation angle diverged"
                );
                if (Math.abs(degrees) <= MODEL_EPSILON) continue;
                Direction.Axis axis = Direction.Axis.valueOf(
                    rotation.get("axis").getAsString().toUpperCase(Locale.ROOT)
                );
                check(
                    cube.rotationAxis() == axis,
                    model.resourceName() + " element " + index + " rotation axis diverged"
                );
                assertVector(
                    cube.rotationOrigin(),
                    rotation.getAsJsonArray("origin"),
                    model.resourceName(),
                    index,
                    "rotation origin"
                );
            }
        } catch (IOException exception) {
            throw new GameTestAssertException(
                "unable to read model resource " + resourceName + ": " + exception.getMessage()
            );
        }
    }

    private static void assertVector(
        Vec3 actual,
        JsonArray expected,
        String resourceName,
        int elementIndex,
        String field
    ) {
        check(expected != null && expected.size() == 3,
            resourceName + " element " + elementIndex + " has invalid " + field);
        check(
            Math.abs(actual.x - expected.get(0).getAsDouble()) <= MODEL_EPSILON
                && Math.abs(actual.y - expected.get(1).getAsDouble()) <= MODEL_EPSILON
                && Math.abs(actual.z - expected.get(2).getAsDouble()) <= MODEL_EPSILON,
            resourceName + " element " + elementIndex + " " + field + " diverged"
        );
    }

    private static boolean intersectsAny(AABB box, List<PlasticConvexShape> shapes) {
        PlasticConvexShape probe = PlasticConvexShape.box(box);
        for (PlasticConvexShape shape : shapes) {
            if (PlasticConvexCollisionResolver.intersects(probe, shape)) {
                return true;
            }
        }
        return false;
    }

    private static void runUpsideDownCauldronReversalTest(
        ExtendedGameTestHelper helper,
        GameTestPlayer player,
        HardenedResinCauldronEntity cauldron,
        String contactName
    ) {
        Vec3[] directions = {
            new Vec3(0.12D, -0.08D, 0.0D),
            new Vec3(-0.12D, -0.08D, 0.0D),
            new Vec3(0.0D, -0.08D, 0.12D),
            new Vec3(0.0D, -0.08D, -0.12D),
            new Vec3(0.12D, -0.08D, 0.0D),
            new Vec3(-0.12D, -0.08D, 0.0D),
            new Vec3(0.0D, -0.08D, 0.12D),
            new Vec3(0.0D, -0.08D, -0.12D)
        };
        Vec3[] relativeStart = {Vec3.ZERO};
        int[] step = {0};
        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                check(player.getPose() == Pose.STANDING,
                    contactName + " contact started with player pose " + player.getPose());
                check(
                    PlasticEntityPhysics.hasImmediateEntityContact(cauldron, player, Direction.DOWN),
                    contactName + " did not directly support the upside-down cauldron"
                );
                relativeStart[0] = cauldron.position().subtract(player.position());
            })
            .thenExecuteFor(directions.length, () -> {
                int currentStep = step[0]++;
                Vec3 playerStart = player.position();
                Vec3 cauldronStart = cauldron.position();
                player.setOnGround(true);
                Vec3 requested = directions[currentStep];
                check(
                    PlasticEntityPhysics.hasSurfaceSupport(
                        cauldron,
                        cauldron.getBoundingBox(),
                        player,
                        Direction.DOWN
                    ),
                    contactName + " lost head support before step " + currentStep
                );
                check(
                    PlasticEntityPhysics.canMoveWithCarrier(
                        cauldron,
                        player,
                        player.getBoundingBox(),
                        Direction.DOWN,
                        requested
                    ),
                    contactName + " rejected its head carrier before step " + currentStep
                );
                player.move(MoverType.SELF, requested);
                check(player.getPose() == Pose.STANDING,
                    contactName + " reversal forced pose " + player.getPose() + " at step " + currentStep);
                Vec3 playerMovement = player.position().subtract(playerStart);
                Vec3 cauldronMovement = cauldron.position().subtract(cauldronStart);
                check(
                    cauldronMovement.distanceToSqr(playerMovement) <= EPSILON * EPSILON,
                    contactName + " cauldron lagged at step " + currentStep
                        + ": player=" + playerMovement + ", cauldron=" + cauldronMovement
                );
                check(
                    cauldron.position().subtract(player.position()).distanceToSqr(relativeStart[0])
                        <= EPSILON * EPSILON,
                    contactName + " changed its head offset at step " + currentStep
                );
            })
            .thenExecute(() -> {
                cauldron.discard();
                player.discard();
            })
            .thenSucceed();
    }

    private static void assertPredictionReversal(Vec3 forward) {
        PlasticCarrierPrediction prediction = new PlasticCarrierPrediction();
        Vec3 reverse = forward.scale(-1.0D);
        prediction.add(forward);
        Vec3 pendingBeforeStationarySnapshot = prediction.pendingMovement();
        double travelBeforeStationarySnapshot = prediction.remainingTravel();
        check(
            !prediction.resetIfDiscontinuous(Vec3.ZERO, 16.0D),
            "stationary server snapshot requested a hard carrier correction"
        );
        check(
            !prediction.reconcile(Vec3.ZERO),
            "stationary server snapshot was treated as a carrier confirmation"
        );
        check(
            prediction.pendingMovement().distanceToSqr(pendingBeforeStationarySnapshot) <= EPSILON * EPSILON
                && Math.abs(prediction.remainingTravel() - travelBeforeStationarySnapshot) <= EPSILON,
            "stationary server snapshot discarded the unconfirmed carrier path"
        );
        prediction.add(reverse);
        check(
            prediction.pendingMovement().lengthSqr() <= EPSILON * EPSILON,
            "opposite local carrier steps did not cancel in the pending position"
        );
        check(prediction.remainingTravel() > forward.length() * 1.9D,
            "prediction discarded the reversed path before server confirmation");
        check(prediction.reconcile(forward), "first reversed carrier step was not confirmed");
        check(
            prediction.pendingMovement().distanceToSqr(reverse) <= EPSILON * EPSILON,
            "late forward snapshot discarded the unconfirmed reverse step: "
                + prediction.pendingMovement()
        );
        check(
            forward.add(prediction.pendingMovement()).lengthSqr() <= EPSILON * EPSILON,
            "server snapshot plus pending reverse step changed the local endpoint"
        );
        check(prediction.reconcile(reverse), "reverse carrier step was not confirmed");
        check(prediction.isEmpty(), "prediction retained a step after both directions were confirmed");

        PlasticCarrierPrediction quantizedPrediction = new PlasticCarrierPrediction();
        quantizedPrediction.add(forward);
        quantizedPrediction.add(reverse);
        Vec3 quantizedForward = forward.add(forward.normalize().scale(1.0D / 4096.0D));
        check(
            quantizedPrediction.reconcile(quantizedForward),
            "quantized forward snapshot did not confirm the predicted carrier step"
        );
        check(
            quantizedPrediction.pendingMovement().distanceToSqr(reverse) <= EPSILON * EPSILON,
            "quantized forward snapshot discarded or distorted the pending reverse step: "
                + quantizedPrediction.pendingMovement()
        );
        check(
            Math.abs(quantizedPrediction.remainingTravel() - reverse.length()) <= EPSILON,
            "quantized forward snapshot changed the remaining reverse travel"
        );
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private record FacePushFixture(
        UniversalPlasticEntity target,
        Entity pusher,
        Vec3 outwardNormal,
        Vec3 targetStart,
        Vec3 pusherStart
    ) {
    }

    private record BondedModel(
        BlockPos relativeAnchor,
        AbstractPlasticEntity entity,
        BuiltInPlasticEntityModels.Model model
    ) {
    }
}
