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
import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityGridSnapping;
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
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
    @EmptyTemplate("3x3x3")
    @TestHolder(description = "Axis-aligned convex cubes stop flush instead of leaving a contact-epsilon gap")
    static void axisAlignedCubesCollideFlush(ExtendedGameTestHelper helper) {
        PlasticConvexShape moving = PlasticConvexShape.box(new AABB(0.0D, 2.0D, 0.0D, 1.0D, 3.0D, 1.0D));
        PlasticConvexShape obstacle = PlasticConvexShape.box(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
        double downward = PlasticConvexCollisionResolver.collideAxis(
            List.of(moving),
            List.of(obstacle),
            Direction.Axis.Y,
            -2.0D
        );
        check(
            Math.abs(downward + 1.0D) <= 1.0E-12D,
            "Y contact left a skin gap: allowed=" + downward
        );

        PlasticConvexShape eastMoving = PlasticConvexShape.box(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
        PlasticConvexShape eastWall = PlasticConvexShape.box(new AABB(3.0D, 0.0D, 0.0D, 4.0D, 1.0D, 1.0D));
        double eastward = PlasticConvexCollisionResolver.collideAxis(
            List.of(eastMoving),
            List.of(eastWall),
            Direction.Axis.X,
            4.0D
        );
        check(
            Math.abs(eastward - 2.0D) <= 1.0E-12D,
            "X contact left a skin gap: allowed=" + eastward
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 80)
    @EmptyTemplate(value = "5x8x5", floor = true)
    @TestHolder(description = "Stacked 1x1x1 plastic cubes rest on integer planes so the cell above stays placeable")
    static void stackedUnitCubesLeavePlaceableCellAbove(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity lower = createDynamicPlastic(
            helper,
            new Vec3(2.5D, 3.0D, 2.5D),
            cubeModel(16.0D),
            false
        );
        UniversalPlasticEntity upper = createDynamicPlastic(
            helper,
            new Vec3(2.5D, 5.0D, 2.5D),
            cubeModel(16.0D),
            false
        );
        helper.runAfterDelay(50, () -> {
            check(lower.isAlive(), "lower stacked cube was discarded");
            check(upper.isAlive(), "upper stacked cube was discarded");
            AABB lowerBounds = lower.plasticraft$getCollisionBox().bounds();
            AABB upperBounds = upper.plasticraft$getCollisionBox().bounds();
            check(
                Math.abs(upperBounds.minY - lowerBounds.maxY) <= 1.0E-12D,
                "stacked cubes were not flush: lowerMaxY=" + lowerBounds.maxY
                    + " upperMinY=" + upperBounds.minY
            );
            assertIntegerPlane(lowerBounds.minY, "lower cube minY");
            assertIntegerPlane(lowerBounds.maxY, "lower cube maxY");
            assertIntegerPlane(upperBounds.minY, "upper cube minY");
            assertIntegerPlane(upperBounds.maxY, "upper cube maxY");
            BlockPos above = BlockPos.containing(
                upperBounds.getCenter().x,
                upperBounds.maxY,
                upperBounds.getCenter().z
            );
            check(
                helper.getLevel().noCollision(new AABB(above)),
                "cell above stacked cubes was blocked by a sub-micron protrusion at " + above
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 60)
    @EmptyTemplate(value = "6x5x5", floor = true)
    @TestHolder(description = "A 1x1x1 plastic cube pushed into a wall rests flush so the vacated cell stays placeable")
    static void sidePushedUnitCubeLeavesPlaceableCell(ExtendedGameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.STONE);
        UniversalPlasticEntity cube = createDynamicPlastic(
            helper,
            new Vec3(1.5D, 3.0D, 2.5D),
            cubeModel(16.0D),
            false
        );
        helper.runAfterDelay(30, () -> {
            check(cube.isAlive(), "side-pushed cube was discarded before the push");
            Vec3 start = cube.position();
            Vec3 requested = new Vec3(3.0D, 0.0D, 0.0D);
            cube.move(MoverType.SELF, requested);
            PlasticEntityGridSnapping.applyAfterMove(cube, requested, cube.position().subtract(start));
            AABB bounds = cube.plasticraft$getCollisionBox().bounds();
            BlockPos wall = helper.absolutePos(new BlockPos(4, 1, 2));
            check(
                Math.abs(bounds.maxX - wall.getX()) <= 1.0E-12D,
                "side contact did not rest flush with the wall: maxX=" + bounds.maxX
                    + " wallX=" + wall.getX()
                    + " start=" + start
                    + " after=" + cube.position()
                    + " bounds=" + bounds
            );
            assertIntegerPlane(bounds.minX, "side-pushed cube minX");
            assertIntegerPlane(bounds.maxX, "side-pushed cube maxX");
            check(
                Math.abs(bounds.maxX - bounds.minX - 1.0D) <= 1.0E-12D,
                "side-pushed cube was not one block wide: " + bounds
            );
            AABB behind = new AABB(
                bounds.minX - 1.0D + 1.0E-6D,
                bounds.minY + 1.0E-6D,
                bounds.minZ + 1.0E-6D,
                bounds.minX - 1.0E-6D,
                bounds.maxY - 1.0E-6D,
                bounds.maxZ - 1.0E-6D
            );
            check(
                helper.getLevel().noCollision(behind),
                "cell behind the side-pushed cube was blocked by a sub-micron protrusion: bounds="
                    + bounds + " behind=" + behind
            );
            helper.succeed();
        });
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

    @GameTest(timeoutTicks = 60)
    @EmptyTemplate(value = "21x10x11", floor = true)
    @TestHolder(description = "Dropped items tick through empty bounds and remain on dynamic and bonded slopes")
    static void droppedItemsTickOnRealSlopes(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity dynamic = createDynamicPlastic(helper, new Vec3(5.5D, 2.0D, 5.5D), slopedModel());
        BondedPlasticShapeIndex.Entry bonded = createBondedSlope(helper, new BlockPos(14, 2, 5));
        List<AABB> bounds = List.of(dynamic.getBoundingBox(), bonded.collisionShape().bounds());
        List<ItemEntity> items = new ArrayList<>();
        for (AABB box : bounds) {
            ItemEntity item = new ItemEntity(
                helper.getLevel(), box.getCenter().x + box.getXsize() * 0.28D,
                box.maxY + 0.2D, box.getCenter().z, new ItemStack(Items.STICK), 0.0D, 0.0D, 0.0D
            );
            item.setNeverPickUp();
            check(helper.getLevel().addFreshEntity(item), "failed to spawn falling slope item");
            items.add(item);
        }
        List<Vec3> settled = new ArrayList<>();
        helper.runAfterDelay(25, () -> {
            for (int index = 0; index < items.size(); index++) {
                ItemEntity item = items.get(index);
                check(item.getY() < bounds.get(index).maxY - 0.1D,
                    "slope " + index + " item stopped on the outer AABB");
                check(item.getY() > bounds.get(index).minY + 0.2D,
                    "slope " + index + " item fell through the real surface");
                settled.add(item.position());
            }
        });
        helper.onEachTick(() -> {
            for (int index = 0; index < items.size(); index++) {
                ItemEntity item = items.get(index);
                check(!item.noPhysics, "slope " + index + " item falsely entered escape mode");
                if (settled.isEmpty()) continue;
                check(item.position().distanceToSqr(settled.get(index)) <= EPSILON * EPSILON,
                    "slope " + index + " item jittered during normal ticks after settling");
                check(!intersectsAny(item.getBoundingBox(), index == 0
                        ? dynamic.plasticraft$getCollisionBox().convexComponents() : bonded.convexShapes()),
                    "slope " + index + " item entered the real surface");
            }
        });
        helper.runAfterDelay(45, helper::succeed);
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "11x10x11", floor = true)
    @TestHolder(description = "Projectiles cross empty slope corners and hit the nearest real surface")
    static void projectilesHitRealDynamicSlope(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity slope = createDynamicPlastic(helper, new Vec3(5.5D, 2.0D, 5.5D), slopedModel());
        AABB bounds = slope.getBoundingBox();
        double x = bounds.getCenter().x + bounds.getXsize() * 0.28D;
        double z = bounds.getCenter().z;
        Vec3 above = new Vec3(x, bounds.maxY + 0.6D, z);
        Vec3 corner = new Vec3(x, bounds.maxY - 0.2D, z);
        Vec3 below = new Vec3(x, bounds.minY, z);
        Snowball snowball = new Snowball(EntityType.SNOWBALL, helper.getLevel());
        snowball.setNoGravity(true);
        snowball.setPos(above);
        check(helper.getLevel().addFreshEntity(snowball), "failed to spawn slope snowball");
        snowball.setDeltaMovement(corner.subtract(above));
        snowball.tick();
        check(!snowball.isRemoved(), "snowball hit the slope's empty AABB corner");
        check(!slope.isRemoved(), "snowball damaged the slope from its empty AABB corner");
        check(snowball.position().distanceToSqr(corner) <= EPSILON * EPSILON,
            "snowball did not fly into the empty AABB corner");

        Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
        arrow.setNoGravity(true);
        arrow.setPos(above);
        check(helper.getLevel().addFreshEntity(arrow), "failed to spawn slope arrow");
        arrow.setDeltaMovement(corner.subtract(above));
        arrow.tick();
        check(arrow.position().distanceToSqr(corner) <= EPSILON * EPSILON,
            "arrow hit the slope's empty AABB corner");
        check(!slope.isRemoved(), "arrow damaged the slope from its empty AABB corner");

        check(helper.getLevel().getEntities(snowball, bounds.inflate(1.0D), target -> target == slope).contains(slope),
            "slope was absent from the projectile candidate query");

        EntityHitResult surface = ProjectileUtil.getEntityHitResult(
            helper.getLevel(), snowball, corner, below, bounds.inflate(1.0D), target -> target == slope
        );
        check(surface != null && surface.getEntity() == slope, "projectile missed the real slope");
        check(surface.getLocation().y < corner.y - 0.1D, "projectile hit the AABB instead of the real slope");
        check(Math.abs(surface.getLocation().x - x) <= EPSILON
                && Math.abs(surface.getLocation().z - z) <= EPSILON,
            "projectile hit location was the target center instead of the flight path");
        boolean onSurface = slope.plasticraft$getCollisionBox().convexComponents().stream().anyMatch(shape ->
            shape.contains(surface.getLocation(), EPSILON)
                && shape.faces().stream().anyMatch(face -> Math.abs(face.signedDistance(surface.getLocation())) <= EPSILON)
        );
        check(onSurface, "projectile hit location was not on a real convex face");
        EntityHitResult precise = ProjectileUtil.getEntityHitResult(
            arrow, corner, below, bounds.inflate(1.0D), target -> target == slope, 100.0D
        );
        check(precise != null && precise.getLocation().distanceToSqr(surface.getLocation()) <= EPSILON * EPSILON,
            "precise projectile query treated an empty AABB corner as starting inside the slope");
        Vec3 outward = corner.add(0.0D, 0.4D, 0.0D);
        check(ProjectileUtil.getEntityHitResult(
            arrow, corner, outward, bounds.inflate(1.0D), target -> target == slope, 100.0D
        ) == null, "projectile leaving an empty corner falsely hit the containing AABB");

        snowball.setDeltaMovement(below.subtract(corner));
        snowball.tick();
        check(snowball.isRemoved(), "snowball failed to collide when crossing the real slope");
        arrow.discard();
        slope.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "21x10x11", floor = true)
    @TestHolder(description = "Projectiles cross bonded empty corners, hit real slopes and retain block occlusion")
    static void projectilesHitRealBondedSlopes(ExtendedGameTestHelper helper) {
        for (int index = 0; index < 2; index++) {
            BondedPlasticShapeIndex.Entry slope = createBondedSlope(
                helper, new BlockPos(5 + index * 9, 4, 5), slopedModel(index == 0 ? 8.0D : 32.0D)
            );
            AABB bounds = slope.collisionShape().bounds();
            double x = bounds.getCenter().x + bounds.getXsize() * 0.28D;
            double z = bounds.getCenter().z;
            Vec3 above = new Vec3(x, bounds.maxY + 0.6D, z);
            Vec3 corner = new Vec3(x, bounds.maxY - bounds.getYsize() * 0.05D, z);
            Vec3 below = new Vec3(x, bounds.minY - 0.1D, z);
            String label = index == 0 ? "in-cell slope" : "extended slope";
            check(BlockPos.containing(corner).equals(slope.anchor()) == (index == 0),
                label + " did not exercise the intended anchor-cell boundary");
            Snowball snowball = new Snowball(EntityType.SNOWBALL, helper.getLevel());
            snowball.setNoGravity(true);
            snowball.setPos(above);
            check(helper.getLevel().addFreshEntity(snowball), "failed to spawn " + label + " snowball");
            snowball.setDeltaMovement(corner.subtract(above));
            snowball.tick();
            check(!snowball.isRemoved() && snowball.position().distanceToSqr(corner) <= EPSILON * EPSILON,
                label + " blocked a snowball in its empty corner");

            Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
            arrow.setNoGravity(true);
            arrow.setPos(above);
            check(helper.getLevel().addFreshEntity(arrow), "failed to spawn " + label + " arrow");
            arrow.setDeltaMovement(corner.subtract(above));
            arrow.tick();
            check(arrow.position().distanceToSqr(corner) <= EPSILON * EPSILON,
                label + " blocked an arrow before reaching the real surface");
            Vec3 outward = corner.add(0.0D, 0.1D, 0.0D);
            arrow.setDeltaMovement(outward.subtract(corner));
            arrow.tick();
            check(arrow.position().distanceToSqr(outward) <= EPSILON * EPSILON,
                label + " falsely embedded an arrow on the next tick inside its empty corner");

            BlockHitResult hit = helper.getLevel().clip(new ClipContext(
                corner, below, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, snowball
            ));
            check(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(slope.anchor()),
                label + " did not report its anchor when hit on the real surface");
            check(hit.getLocation().y < corner.y - bounds.getYsize() * 0.15D,
                label + " returned the compatibility box as its hit position");
            check(slope.convexShapes().stream().anyMatch(shape -> shape.contains(hit.getLocation(), EPSILON)
                    && shape.faces().stream().anyMatch(face -> Math.abs(face.signedDistance(hit.getLocation())) <= EPSILON)),
                label + " hit position was not on a real face");
            snowball.setDeltaMovement(below.subtract(corner));
            snowball.tick();
            check(snowball.isRemoved(), label + " failed to stop a snowball at the real surface");
            arrow.setPos(corner);
            arrow.setDeltaMovement(below.subtract(corner));
            arrow.tick();
            check(Math.abs(arrow.getY() - hit.getLocation().y - 0.05D) < 0.01D,
                label + " arrow did not lodge at the real hit position");
            Vec3 lodged = arrow.position();
            arrow.tick();
            check(arrow.position().distanceToSqr(lodged) <= EPSILON * EPSILON,
                label + " arrow lost support after lodging on the real slope");

            Vec3 start = new Vec3(x, corner.y, bounds.minZ - 1.0D);
            Vec3 end = new Vec3(x, corner.y, bounds.maxZ + 2.0D);
            BlockPos wall = BlockPos.containing(x, corner.y, bounds.maxZ + 1.0D);
            helper.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            BlockHitResult wallHit = helper.getLevel().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, arrow
            ));
            check(wallHit.getType() == HitResult.Type.BLOCK && wallHit.getBlockPos().equals(wall),
                label + " empty corner hid the ordinary block behind it");
            BlockPos front = BlockPos.containing(above.add(0.0D, 1.0D, 0.0D));
            helper.getLevel().setBlockAndUpdate(front, Blocks.STONE.defaultBlockState());
            BlockHitResult frontHit = helper.getLevel().clip(new ClipContext(
                above.add(0.0D, 2.0D, 0.0D), below, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, arrow
            ));
            check(frontHit.getType() == HitResult.Type.BLOCK && frontHit.getBlockPos().equals(front),
                label + " real surface overrode a nearer ordinary block");
            arrow.discard();
        }
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
    @EmptyTemplate(value = "21x7x11", floor = true)
    @TestHolder(description = "A player under an upside-down cauldron rim or inner ceiling can reverse without being crushed")
    static void playerUnderUpsideDownCauldronCanReverseWithoutBeingCrushed(ExtendedGameTestHelper helper) {
        for (int x = 1; x < 20; x++) {
            for (int z = 1; z < 10; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        }
        GameTestPlayer rimPlayer = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 rimPlayerPosition = helper.absoluteVec(new Vec3(5.5D, 2.0D, 5.5D));
        rimPlayer.moveTo(rimPlayerPosition.x, rimPlayerPosition.y, rimPlayerPosition.z);
        rimPlayer.setOnGround(true);
        HardenedResinCauldronEntity rimCauldron = spawnUpsideDownCauldron(helper, new Vec3(5.5D, 4.0D, 5.5D));
        AABB rimBounds = rimCauldron.getBoundingBox();
        rimPlayer.moveTo(
            rimBounds.minX - rimPlayer.getBbWidth() * 0.5D + 0.18D,
            rimPlayerPosition.y,
            rimBounds.getCenter().z
        );
        AABB rimPlayerBox = rimPlayer.getBoundingBox();
        rimCauldron.setPos(
            rimCauldron.getX(),
            rimCauldron.getY() + rimPlayerBox.maxY - rimBounds.minY,
            rimCauldron.getZ()
        );

        GameTestPlayer innerPlayer = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 innerPlayerPosition = helper.absoluteVec(new Vec3(15.5D, 2.0D, 5.5D));
        innerPlayer.moveTo(innerPlayerPosition.x, innerPlayerPosition.y, innerPlayerPosition.z);
        innerPlayer.setOnGround(true);
        HardenedResinCauldronEntity innerCauldron = spawnUpsideDownCauldron(helper, new Vec3(15.5D, 4.0D, 5.5D));
        AABB innerPlayerBox = innerPlayer.getBoundingBox();
        double innerCeilingMinY = innerCauldron.plasticraft$getCollisionBox().convexComponents().stream()
            .map(PlasticConvexShape::bounds)
            .filter(bounds -> bounds.minX <= innerPlayerBox.minX + EPSILON
                && bounds.maxX >= innerPlayerBox.maxX - EPSILON
                && bounds.minZ <= innerPlayerBox.minZ + EPSILON
                && bounds.maxZ >= innerPlayerBox.maxZ - EPSILON)
            .mapToDouble(bounds -> bounds.minY)
            .min()
            .orElseThrow(() -> new GameTestAssertException(
                "upside-down cauldron exposed no inner ceiling over the player"
            ));
        innerCauldron.setPos(
            innerCauldron.getX(),
            innerCauldron.getY() + innerPlayerBox.maxY - innerCeilingMinY,
            innerCauldron.getZ()
        );
        runUpsideDownCauldronReversalTest(
            helper,
            new ReversalContact(rimPlayer, rimCauldron, "rim"),
            new ReversalContact(innerPlayer, innerCauldron, "inner ceiling")
        );
    }

    private static HardenedResinCauldronEntity spawnUpsideDownCauldron(
        ExtendedGameTestHelper helper,
        Vec3 relativePosition
    ) {
        HardenedResinCauldronEntity cauldron = new HardenedResinCauldronEntity(
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get(),
            helper.getLevel(),
            helper.absoluteVec(relativePosition),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            new PlasticEntityOrientation(Direction.DOWN, 0)
        );
        check(helper.getLevel().addFreshEntity(cauldron), "failed to add upside-down cauldron");
        return cauldron;
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "11x10x11", floor = true)
    @TestHolder(description = "A sloped plastic face on a player's head does not force crouch or crawl")
    static void slopedPlasticOnPlayerHeadDoesNotForceCrouch(ExtendedGameTestHelper helper) {
        for (int x = 1; x < 10; x++) {
            for (int z = 1; z < 10; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        }
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 floorPosition = helper.absoluteVec(new Vec3(4.6D, 2.0D, 5.5D));
        player.moveTo(floorPosition.x, floorPosition.y, floorPosition.z);
        player.setOnGround(true);

        UniversalPlasticEntity slope = createDynamicPlastic(
            helper,
            new Vec3(5.5D, 5.0D, 5.5D),
            slopedModel(),
            true
        );
        slope.move(MoverType.SELF, new Vec3(0.0D, -3.0D, 0.0D));
        slope.setNoGravity(false);

        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                check(
                    PlasticEntityPhysics.hasImmediateEntityContact(slope, player, Direction.DOWN),
                    "sloped plastic did not settle on the player's head"
                );
                check(
                    slope.getBoundingBox().minY < player.getBoundingBox().maxY - 0.05D,
                    "sloped plastic rested on its lowest vertex instead of a sloped face"
                );
                check(
                    !intersectsAny(
                        player.getBoundingBox(),
                        slope.plasticraft$getCollisionBox().convexComponents()
                    ),
                    "sloped plastic SAT overlapped the supporting player"
                );
                AABB standingBox = player.getDimensions(Pose.STANDING)
                    .makeBoundingBox(player.position())
                    .deflate(1.0E-7D);
                check(
                    standingBox.intersects(slope.getBoundingBox()),
                    "compatibility AABB of the sloped face did not overlap the standing player"
                );
                assertPlayerCanStandWithHeadLoad(helper, player, "after the sloped face landed");
                check(
                    slope.plasticraft$canMoveWithCarrier(player, new Vec3(0.08D, -0.08D, 0.0D)),
                    "sloped plastic rejected the player as a head carrier after landing"
                );
            })
            .thenExecuteFor(6, () -> {
                Vec3 playerStart = player.position();
                Vec3 slopeStart = slope.position();
                player.setOnGround(true);
                player.move(MoverType.SELF, new Vec3(0.08D, -0.08D, 0.0D));
                check(
                    player.position().subtract(playerStart).horizontalDistanceSqr() > 0.0025D,
                    "player could not walk while a sloped plastic face rested on their head"
                );
                assertPlayerCanStandWithHeadLoad(helper, player, "while walking under a sloped face");
                if (PlasticEntityPhysics.hasImmediateEntityContact(slope, player, Direction.DOWN)) {
                    check(
                        slope.position().subtract(slopeStart).horizontalDistanceSqr() > 0.0025D,
                        "sloped plastic did not follow its supporting head"
                    );
                }
            })
            .thenExecute(() -> {
                slope.discard();
                player.discard();
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 60)
    @EmptyTemplate(value = "11x8x11", floor = true)
    @TestHolder(description = "A player carrying a 2x2x2 plastic can walk through a one-block gap")
    static void playerWalksThroughNarrowGapWhileCarryingLargePlastic(ExtendedGameTestHelper helper) {
        for (int x = 1; x < 10; x++) {
            for (int z = 1; z < 10; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        }
        for (int z = 5; z < 10; z++) {
            for (int y = 2; y < 7; y++) {
                helper.setBlock(4, y, z, Blocks.STONE);
                helper.setBlock(6, y, z, Blocks.STONE);
            }
        }
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 floorPosition = helper.absoluteVec(new Vec3(5.5D, 2.0D, 3.5D));
        player.moveTo(floorPosition.x, floorPosition.y, floorPosition.z);
        player.setOnGround(true);

        UniversalPlasticEntity cube = createDynamicPlastic(
            helper,
            new Vec3(5.5D, 4.0D, 3.5D),
            cubeModel(32.0D),
            false
        );
        double[] blockedCubeZ = {Double.NaN};

        helper.startSequence()
            .thenIdle(6)
            .thenExecute(() -> {
                check(
                    PlasticEntityPhysics.hasImmediateEntityContact(cube, player, Direction.DOWN),
                    "2x2x2 plastic did not settle on the player's head"
                );
                check(
                    cube.getBoundingBox().getXsize() > 1.9D && cube.getBoundingBox().getZsize() > 1.9D,
                    "narrow-gap test did not spawn a 2x2x2 plastic body"
                );
                assertPlayerCanStandWithHeadLoad(helper, player, "after the 2x2x2 settled");
            })
            .thenExecuteFor(8, () -> {
                player.setOnGround(true);
                player.move(MoverType.SELF, new Vec3(0.0D, -0.08D, 0.16D));
            })
            .thenExecute(() -> {
                blockedCubeZ[0] = cube.getZ();
                check(
                    cube.getZ() < helper.absoluteVec(new Vec3(5.5D, 2.0D, 5.2D)).z,
                    "2x2x2 plastic passed through the one-block gap: z=" + cube.getZ()
                );
                check(
                    player.getZ() > cube.getZ() + 0.15D,
                    "player was clamped to the blocked 2x2x2 plastic: playerZ="
                        + player.getZ() + ", cubeZ=" + cube.getZ()
                );
            })
            .thenExecuteFor(10, () -> {
                player.setOnGround(true);
                player.move(MoverType.SELF, new Vec3(0.0D, -0.08D, 0.16D));
            })
            .thenExecute(() -> {
                check(
                    player.getZ() > helper.absoluteVec(new Vec3(5.5D, 2.0D, 6.2D)).z,
                    "player did not walk into the one-block gap: z=" + player.getZ()
                );
                check(
                    Math.abs(cube.getZ() - blockedCubeZ[0]) < 0.08D,
                    "blocked 2x2x2 plastic kept following the player through the gap"
                );
            })
            .thenIdle(12)
            .thenExecute(() -> {
                check(
                    !PlasticEntityPhysics.hasImmediateEntityContact(cube, player, Direction.DOWN),
                    "2x2x2 plastic was still supported after the player entered the gap"
                );
                check(
                    cube.getBoundingBox().minY < player.getBoundingBox().minY + 0.25D,
                    "2x2x2 plastic did not fall after losing head support: cubeMinY="
                        + cube.getBoundingBox().minY + ", playerFeet=" + player.getBoundingBox().minY
                );
                check(player.getPose() == Pose.STANDING,
                    "narrow-gap carry changed the player pose to " + player.getPose());
                cube.discard();
                player.discard();
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("5x5x5")
    @TestHolder(description = "Carrier prediction reconciles coalesced server movement without drift")
    static void carrierPredictionReconcilesCoalescedMovement(ExtendedGameTestHelper helper) {
        assertPredictionReversal(new Vec3(0.12D, 0.0D, 0.0D));
        assertPredictionReversal(new Vec3(0.0D, 0.0D, 0.12D));
        assertPredictionCoalescing(new Vec3(0.12D, 0.0D, 0.0D));
        assertPredictionCoalescing(new Vec3(0.0D, 0.0D, 0.12D));
        helper.succeed();
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
        return slopedModel(32.0D);
    }

    private static EditableMoldingModel slopedModel(double size) {
        MoldingElement source = MoldingElement.cube(
            "Slope",
            new MoldingVec3(8.0D, 8.0D, 8.0D),
            new MoldingVec3(8.0D + size, 8.0D + size, 8.0D + size)
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
        return createDynamicPlastic(helper, relativePosition, model, true);
    }

    private static UniversalPlasticEntity createDynamicPlastic(
        ExtendedGameTestHelper helper,
        Vec3 relativePosition,
        EditableMoldingModel model,
        boolean noGravity
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
        product.setNoGravity(noGravity);
        check(helper.getLevel().addFreshEntity(product), "failed to add dynamic plastic test product");
        return product;
    }

    private static EditableMoldingModel cubeModel(double sizePx) {
        return EditableMoldingModel.empty()
            .withName("Head Carry Cube")
            .withElements(List.of(MoldingElement.cube(
                "Cube",
                MoldingVec3.ZERO,
                new MoldingVec3(sizePx, sizePx, sizePx)
            )));
    }

    private static void assertPlayerCanStandWithHeadLoad(
        ExtendedGameTestHelper helper,
        GameTestPlayer player,
        String when
    ) {
        AABB standingBox = player.getDimensions(Pose.STANDING)
            .makeBoundingBox(player.position())
            .deflate(1.0E-7D);
        check(
            PlasticConvexCollisionResolver.noCollisionWithExactPlastic(
                player,
                standingBox,
                helper.getLevel()
            ),
            "player SAT standing box collided with a head-carried plastic " + when
        );
        check(
            helper.getLevel().noCollision(player, standingBox),
            "player compatibility standing box collided with a head-carried plastic " + when
        );
        check(player.getPose() == Pose.STANDING,
            "head-carried plastic forced the player into " + player.getPose() + " " + when);
    }

    private static BondedPlasticShapeIndex.Entry createBondedSlope(
        ExtendedGameTestHelper helper,
        BlockPos relativeAnchor
    ) {
        return createBondedSlope(helper, relativeAnchor, slopedModel());
    }

    private static BondedPlasticShapeIndex.Entry createBondedSlope(
        ExtendedGameTestHelper helper,
        BlockPos relativeAnchor,
        EditableMoldingModel model
    ) {
        UniversalPlasticEntity product = createDynamicPlastic(
            helper,
            Vec3.atBottomCenterOf(relativeAnchor),
            model
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
        ReversalContact... contacts
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
        Vec3[] relativeStart = new Vec3[contacts.length];
        int[] step = {0};
        helper.startSequence()
            .thenIdle(3)
            .thenExecute(() -> {
                for (int index = 0; index < contacts.length; index++) {
                    ReversalContact contact = contacts[index];
                    check(contact.player().getPose() == Pose.STANDING,
                        contact.name() + " contact started with player pose " + contact.player().getPose());
                    check(
                        PlasticEntityPhysics.hasImmediateEntityContact(contact.cauldron(), contact.player(), Direction.DOWN),
                        contact.name() + " did not directly support the upside-down cauldron"
                    );
                    relativeStart[index] = contact.cauldron().position().subtract(contact.player().position());
                }
            })
            .thenExecuteFor(directions.length, () -> {
                int currentStep = step[0]++;
                Vec3 requested = directions[currentStep];
                for (int index = 0; index < contacts.length; index++) {
                    ReversalContact contact = contacts[index];
                    Vec3 playerStart = contact.player().position();
                    Vec3 cauldronStart = contact.cauldron().position();
                    contact.player().setOnGround(true);
                    check(
                        PlasticEntityPhysics.hasSurfaceSupport(
                            contact.cauldron(),
                            contact.cauldron().getBoundingBox(),
                            contact.player(),
                            Direction.DOWN
                        ),
                        contact.name() + " lost head support before step " + currentStep
                    );
                    check(
                        PlasticEntityPhysics.canMoveWithCarrier(
                            contact.cauldron(),
                            contact.player(),
                            contact.player().getBoundingBox(),
                            Direction.DOWN,
                            requested
                        ),
                        contact.name() + " rejected its head carrier before step " + currentStep
                    );
                    contact.player().move(MoverType.SELF, requested);
                    check(contact.player().getPose() == Pose.STANDING,
                        contact.name() + " reversal forced pose " + contact.player().getPose() + " at step " + currentStep);
                    Vec3 playerMovement = contact.player().position().subtract(playerStart);
                    Vec3 cauldronMovement = contact.cauldron().position().subtract(cauldronStart);
                    check(
                        cauldronMovement.distanceToSqr(playerMovement) <= EPSILON * EPSILON,
                        contact.name() + " cauldron lagged at step " + currentStep
                            + ": player=" + playerMovement + ", cauldron=" + cauldronMovement
                    );
                    check(
                        contact.cauldron().position().subtract(contact.player().position()).distanceToSqr(relativeStart[index])
                            <= EPSILON * EPSILON,
                        contact.name() + " changed its head offset at step " + currentStep
                    );
                }
            })
            .thenExecute(() -> {
                for (ReversalContact contact : contacts) {
                    contact.cauldron().discard();
                    contact.player().discard();
                }
            })
            .thenSucceed();
    }

    private record ReversalContact(
        GameTestPlayer player,
        HardenedResinCauldronEntity cauldron,
        String name
    ) {
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

    private static void assertPredictionCoalescing(Vec3 forward) {
        Vec3 reverse = forward.scale(-1.0D);
        PlasticCarrierPrediction coalescedPrediction = new PlasticCarrierPrediction();
        coalescedPrediction.add(forward);
        coalescedPrediction.add(reverse);
        coalescedPrediction.add(forward);
        Vec3 localEndpoint = coalescedPrediction.pendingMovement();
        check(coalescedPrediction.reconcile(forward), "coalesced carrier path was not confirmed");
        check(coalescedPrediction.isEmpty(), "coalesced carrier path retained a closed reversal");
        check(
            forward.add(coalescedPrediction.pendingMovement()).distanceToSqr(localEndpoint)
                <= EPSILON * EPSILON,
            "coalesced carrier snapshot changed the local endpoint"
        );

        PlasticCarrierPrediction closedPrediction = new PlasticCarrierPrediction();
        closedPrediction.add(forward);
        closedPrediction.add(reverse);
        check(closedPrediction.reconcile(Vec3.ZERO), "zero-net carrier path was not confirmed");
        check(closedPrediction.isEmpty(), "zero-net carrier path retained stale travel");

        PlasticCarrierPrediction shortPrediction = new PlasticCarrierPrediction();
        shortPrediction.add(forward);
        Vec3 shortServerMovement = forward.scale(0.997D);
        check(shortPrediction.reconcile(shortServerMovement), "short carrier snapshot was not confirmed");
        check(
            shortServerMovement.add(shortPrediction.pendingMovement()).distanceToSqr(forward)
                <= EPSILON * EPSILON,
            "short carrier snapshot changed the local endpoint"
        );

        PlasticCarrierPrediction overshootPrediction = new PlasticCarrierPrediction();
        overshootPrediction.add(forward);
        Vec3 overshoot = forward.add(forward.normalize().scale(1.0E-3D));
        check(overshootPrediction.reconcile(overshoot), "carrier snapshot overshoot was not confirmed");
        check(overshootPrediction.isEmpty(), "carrier snapshot overshoot retained confirmed travel");

        PlasticCarrierPrediction unmatchedPrediction = new PlasticCarrierPrediction();
        unmatchedPrediction.add(forward);
        double unmatchedTravel = unmatchedPrediction.remainingTravel();
        check(!unmatchedPrediction.reconcile(reverse), "opposite server movement confirmed the carrier path");
        check(
            unmatchedPrediction.pendingMovement().distanceToSqr(forward) <= EPSILON * EPSILON
                && Math.abs(unmatchedPrediction.remainingTravel() - unmatchedTravel) <= EPSILON,
            "opposite server movement discarded the carrier path"
        );

        PlasticCarrierPrediction continuousPrediction = new PlasticCarrierPrediction();
        Vec3 serverBatch = forward.scale(3.0D).add(forward.normalize().scale(1.0E-3D));
        for (int batch = 0; batch < 64; batch++) {
            continuousPrediction.add(forward);
            continuousPrediction.add(forward);
            continuousPrediction.add(forward);
            check(continuousPrediction.reconcile(serverBatch),
                "continuous carrier batch " + batch + " was not confirmed");
            check(continuousPrediction.isEmpty(),
                "continuous carrier batch " + batch + " retained confirmed travel");
        }

        PlasticCarrierPrediction fragmentedPrediction = new PlasticCarrierPrediction();
        Vec3 almostReverse = reverse.scale(0.99D);
        for (int segment = 0; segment < 512; segment++) {
            fragmentedPrediction.add((segment & 1) == 0 ? forward : almostReverse);
        }
        Vec3 fragmentedEndpoint = fragmentedPrediction.pendingMovement();
        check(fragmentedPrediction.remainingTravel() < 4.0D,
            "fragmented carrier path retained unbounded reconciliation history");
        check(fragmentedPrediction.reconcile(fragmentedEndpoint),
            "compacted carrier path endpoint was not confirmed");
        check(fragmentedPrediction.isEmpty(),
            "compacted carrier path retained confirmed travel");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static void assertIntegerPlane(double coordinate, String label) {
        check(
            Math.abs(coordinate - Math.rint(coordinate)) <= 1.0E-12D,
            label + " was not on an integer plane: " + coordinate
        );
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
