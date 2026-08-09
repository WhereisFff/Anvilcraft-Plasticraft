package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.BlockAdhesionState;
import dev.anvilcraft.plasticraft.block.BondedFallingBlockInfo;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.ResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveBondingService;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveFaces;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveGroupTransform;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveInvisibilityService;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesivePathPlanner;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesivePreviewService;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveSelectionManager;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveTransit;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityAdhesion;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondLink;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondState;
import dev.anvilcraft.plasticraft.entity.adhesive.SurfaceAdhesiveService;
import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityPhysics;
import dev.anvilcraft.plasticraft.event.BondedFallingBlockEvents;
import dev.anvilcraft.plasticraft.init.PlasticraftAttachments;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.inventory.HardenedResinAnvilMenu;
import dev.anvilcraft.plasticraft.network.BondedPlasticHammerRotatePacket;
import dev.anvilcraft.plasticraft.network.PlasticEntityHammerRotatePacket;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.block.sliding.ISlidingRail;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.entity.FallingGiantAnvilEntity;
import dev.dubhe.anvilcraft.entity.SlidingBlockEntity;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** 高粘性树脂桶固定实体的服务端行为测试。 */
public final class AdhesiveBondingGameTests {
    private static final double EPSILON = 1.0E-5D;

    private AdhesiveBondingGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "8x6x8", floor = true)
    @TestHolder(description = "Splash and lingering invisibility potions permanently hide adhesive patches")
    static void invisibilityPotionsHideBlockAdhesive(ExtendedGameTestHelper helper) {
        BlockPos splashSupport = new BlockPos(2, 1, 3);
        BlockPos lingeringSupport = new BlockPos(5, 1, 3);
        helper.setBlock(splashSupport, Blocks.STONE);
        helper.setBlock(lingeringSupport, Blocks.STONE);
        BlockPos absoluteSplash = helper.absolutePos(splashSupport);
        BlockPos absoluteLingering = helper.absolutePos(lingeringSupport);
        check(BondedFallingBlocks.putPatch(helper.getLevel(), absoluteSplash, Direction.UP), "splash patch failed");
        check(BondedFallingBlocks.putPatch(helper.getLevel(), absoluteLingering, Direction.UP), "lingering patch failed");

        throwInvisibilityPotion(helper, absoluteSplash, Items.SPLASH_POTION);
        throwInvisibilityPotion(helper, absoluteLingering, Items.LINGERING_POTION);

        BlockAdhesionState splashState = BondedFallingBlocks.getAdhesion(helper.getLevel(), absoluteSplash);
        BlockAdhesionState lingeringState = BondedFallingBlocks.getAdhesion(helper.getLevel(), absoluteLingering);
        check(
            splashState != null && splashState.hasPatch(Direction.UP) && splashState.isInvisible(Direction.UP),
            "splash invisibility potion did not hide its patch"
        );
        check(
            lingeringState != null && lingeringState.hasPatch(Direction.UP) && lingeringState.isInvisible(Direction.UP),
            "lingering invisibility potion did not hide its patch"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "8x6x8", floor = true)
    @TestHolder(description = "Invisibility hides stretched entity adhesive without breaking the bond")
    static void invisibilityHidesStretchedEntityAdhesive(ExtendedGameTestHelper helper) {
        Zombie first = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 3.5D));
        Zombie second = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(5.5D, 2.0D, 3.5D));
        check(
            EntityBondManager.connect(helper.getLevel(), first, Direction.EAST, second, Direction.WEST, false),
            "entity bond failed"
        );
        Vec3 firstPoint = AdhesiveFaces.storedFaceAlignmentPoint(first, Direction.EAST);
        Vec3 secondPoint = AdhesiveFaces.storedFaceAlignmentPoint(second, Direction.WEST);
        Vec3 impact = firstPoint.add(secondPoint).scale(0.5D);

        check(
            AdhesiveInvisibilityService.makeInvisible(helper.getLevel(), impact) == 1,
            "invisibility did not find the stretched adhesive"
        );
        EntityBondLink firstLink = EntityBondManager.get(first).linkAt(Direction.EAST);
        EntityBondLink secondLink = EntityBondManager.get(second).linkAt(Direction.WEST);
        check(firstLink != null && firstLink.invisible(), "first entity did not retain the invisible bond");
        check(secondLink != null && secondLink.invisible(), "second entity did not retain the invisible bond");
        check(EntityBondManager.hasBonds(first) && EntityBondManager.hasBonds(second), "invisibility broke the bond");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "8x6x8", floor = true)
    @TestHolder(description = "Invisibility hides block-anchored entity adhesive without releasing the entity")
    static void invisibilityHidesBlockAnchoredEntityAdhesive(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(3, 1, 3);
        helper.setBlock(support, Blocks.STONE);
        BlockPos absoluteSupport = helper.absolutePos(support);
        Zombie entity = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 2.0D, 3.5D));
        EntityAdhesion adhesion = new EntityAdhesion(
            absoluteSupport,
            Direction.UP,
            BuiltInRegistries.BLOCK.getKey(Blocks.STONE),
            entity.position(),
            false
        );
        entity.setData(PlasticraftAttachments.ENTITY_ADHESION, adhesion);
        check(
            BondedFallingBlocks.setEntityBond(helper.getLevel(), absoluteSupport, Direction.UP, true),
            "block entity bond failed"
        );

        Vec3 impact = Vec3.atCenterOf(absoluteSupport).add(0.0D, 0.5D, 0.0D);
        AdhesiveInvisibilityService.makeInvisible(helper.getLevel(), impact);

        EntityAdhesion hidden = entity.getExistingDataOrNull(PlasticraftAttachments.ENTITY_ADHESION.get());
        BlockAdhesionState blockState = BondedFallingBlocks.getAdhesion(helper.getLevel(), absoluteSupport);
        check(hidden != null && hidden.invisible(), "entity-side adhesive state stayed visible");
        check(
            blockState != null && blockState.hasEntityBond(Direction.UP) && blockState.isInvisible(Direction.UP),
            "block-side adhesive state stayed visible"
        );
        check(entity.hasData(PlasticraftAttachments.ENTITY_ADHESION), "invisibility released the entity");
        helper.succeed();
    }

    private static void throwInvisibilityPotion(ExtendedGameTestHelper helper, BlockPos supportPos, Item item) {
        Vec3 impact = Vec3.atCenterOf(supportPos).add(0.0D, 0.5D, 0.0D);
        ThrownPotion potion = new ThrownPotion(helper.getLevel(), impact.x, impact.y, impact.z);
        potion.setItem(PotionContents.createItemStack(item, Potions.INVISIBILITY));
        AdhesiveInvisibilityService.projectileImpact(new ProjectileImpactEvent(
            potion,
            new BlockHitResult(impact, Direction.UP, supportPos, false)
        ));
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "8x6x8", floor = true)
    @TestHolder(description = "Persisted entity bonds refresh their runtime entity ids")
    static void persistedEntityBondsRefreshRuntimeEntityIds(ExtendedGameTestHelper helper) {
        Zombie leader = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 2.0D, 3.5D));
        Zombie follower = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 2.0D, 3.5D));
        Vec3 followerOffset = follower.position().subtract(leader.position());
        leader.setData(
            PlasticraftAttachments.ENTITY_BONDS, new EntityBondState(
            leader.getUUID(),
            -1,
            Vec3.ZERO,
            false,
            List.of(new EntityBondLink(Direction.EAST, follower.getUUID(), -1, Direction.WEST))
        ));
        follower.setData(
            PlasticraftAttachments.ENTITY_BONDS, new EntityBondState(
            leader.getUUID(),
            -1,
            followerOffset,
            false,
            List.of(new EntityBondLink(Direction.WEST, leader.getUUID(), -1, Direction.EAST))
        ));

        EntityBondManager.tick(leader);
        EntityBondManager.tick(follower);

        EntityBondState leaderBonds = EntityBondManager.get(leader);
        EntityBondState followerBonds = EntityBondManager.get(follower);
        check(
            leaderBonds != null
                && leaderBonds.leaderEntityId() == leader.getId()
                && leaderBonds.linkAt(Direction.EAST) != null
                && leaderBonds.linkAt(Direction.EAST).otherEntityId() == follower.getId(),
            "leader bond did not refresh persisted runtime ids"
        );
        check(
            followerBonds != null
                && followerBonds.leaderEntityId() == leader.getId()
                && followerBonds.linkAt(Direction.WEST) != null
                && followerBonds.linkAt(Direction.WEST).otherEntityId() == leader.getId(),
            "follower bond did not refresh persisted runtime ids"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 35)
    @EmptyTemplate(value = "10x8x8", floor = true)
    @TestHolder(description = "Falling giant anvils bonded to blocks restore all 27 multipart blocks")
    static void fallingGiantAnvilBlockifiesAsFullStructure(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(7, 3, 4);
        BlockPos bottomCenter = new BlockPos(5, 2, 4);
        helper.setBlock(support, Blocks.STONE);
        FallingGiantAnvilEntity giantAnvil = FallingGiantAnvilEntity.fall(
            helper.getLevel(),
            helper.absolutePos(new BlockPos(3, 3, 4)),
            ModBlocks.GIANT_ANVIL.get().defaultBlockState(),
            false
        );
        GameTestPlayer player = bucketPlayer(helper, new Vec3(5.5D, 2.0D, 2.5D));

        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, giantAnvil, Direction.EAST),
            "falling giant anvil could not be selected"
        );
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(support),
            Direction.WEST
        ), "falling giant anvil could not be bonded to a block");

        helper.runAfterDelay(14, () -> {
            check(!giantAnvil.isAlive(), "blockified giant anvil entity was not removed");
            GiantAnvilBlock block = ModBlocks.GIANT_ANVIL.get();
            for (Cube3x3PartHalf part : block.getParts()) {
                BlockState state = helper.getBlockState(bottomCenter.offset(part.getOffset()));
                check(
                    state.is(block) && state.getValue(GiantAnvilBlock.HALF) == part,
                    "giant anvil part was missing after blockification: " + part
                );
            }
            BlockPos adhesivePart = support.west();
            check(
                BondedFallingBlocks.hasBlockBond(helper.getLevel(), helper.absolutePos(support), Direction.WEST)
                    && BondedFallingBlocks.hasBlockBond(
                        helper.getLevel(),
                        helper.absolutePos(adhesivePart),
                        Direction.EAST
                    ),
                "giant anvil structure did not retain its bidirectional block bond"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 35)
    @EmptyTemplate(value = "12x9x8", floor = true)
    @TestHolder(description = "Falling giant anvils bonded to entities stay as 3x3x3 entities")
    static void fallingGiantAnvilBondedToEntityStaysEntity(ExtendedGameTestHelper helper) {
        FallingGiantAnvilEntity giantAnvil = FallingGiantAnvilEntity.fall(
            helper.getLevel(),
            helper.absolutePos(new BlockPos(3, 4, 4)),
            ModBlocks.GIANT_ANVIL.get().defaultBlockState(),
            false
        );
        Zombie support = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(8.5D, 3.0D, 4.5D));
        support.setNoGravity(true);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(5.5D, 3.0D, 1.5D));

        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, giantAnvil, Direction.EAST),
            "falling giant anvil could not be selected for an entity bond"
        );
        Vec3 entityBondPlayerPosition = helper.absoluteVec(new Vec3(7.5D, 3.0D, 3.5D));
        player.moveTo(entityBondPlayerPosition.x, entityBondPlayerPosition.y, entityBondPlayerPosition.z);
        check(AdhesiveBondingService.bondSelectedToEntity(
            player,
            InteractionHand.MAIN_HAND,
            support,
            Direction.WEST
        ), "falling giant anvil could not be bonded to an entity");

        helper.runAfterDelay(14, () -> {
            check(giantAnvil.isAlive(), "entity-bonded giant anvil was blockified or removed");
            check(EntityBondManager.hasBonds(giantAnvil), "entity-bonded giant anvil lost its entity link");
            AABB box = giantAnvil.getBoundingBox();
            check(
                Math.abs(box.getXsize() - 3.0D) <= EPSILON
                    && Math.abs(box.getYsize() - 3.0D) <= EPSILON
                    && Math.abs(box.getZsize() - 3.0D) <= EPSILON,
                "entity-bonded giant anvil lost its 3x3x3 bounds"
            );
            giantAnvil.time = 600;
            helper.runAfterDelay(2, () -> {
                check(giantAnvil.isAlive(), "entity-bonded giant anvil timed out");
                check(giantAnvil.time == 0, "entity-bonded giant anvil lifetime kept advancing");
                helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "8x7x7", floor = true)
    @TestHolder(description = "Entity-bonded vanilla falling blocks pause their 30-second timeout")
    static void entityBondedFallingBlockDoesNotTimeOut(ExtendedGameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 3, 3);
        helper.setBlock(sourcePos, Blocks.SAND);
        FallingBlockEntity sand = FallingBlockEntity.fall(
            helper.getLevel(),
            helper.absolutePos(sourcePos),
            Blocks.SAND.defaultBlockState()
        );
        Zombie support = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(5.5D, 2.0D, 3.5D));
        support.setNoGravity(true);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 1.5D));

        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, sand, Direction.EAST),
            "falling block could not be selected"
        );
        check(AdhesiveBondingService.bondSelectedToEntity(
            player,
            InteractionHand.MAIN_HAND,
            support,
            Direction.WEST
        ), "falling block could not be bonded to an entity");

        helper.runAfterDelay(12, () -> {
            check(EntityBondManager.isFollower(sand), "falling block did not become a bonded follower");
            sand.time = 600;
            helper.runAfterDelay(2, () -> {
                check(sand.isAlive(), "bonded falling block timed out and dropped");
                check(sand.time == 0, "bonded falling block lifetime kept advancing");
                check(EntityBondManager.hasBonds(sand), "bonded falling block lost its entity link");
                helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks = 15)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "An entity touching a bare adhesive patch bonds to its support block")
    static void bareAdhesivePatchBondsTouchingEntity(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(3, 2, 3);
        helper.setBlock(support, Blocks.STONE);
        check(
            BondedFallingBlocks.putPatch(helper.getLevel(), helper.absolutePos(support), Direction.UP),
            "bare adhesive patch could not be placed"
        );
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 3.0D, 3.5D));
        zombie.setNoGravity(true);

        helper.runAfterDelay(2, () -> {
            EntityAdhesion adhesion = zombie.getExistingDataOrNull(PlasticraftAttachments.ENTITY_ADHESION.get());
            check(adhesion != null, "entity touching a bare patch was not bonded");
            check(
                adhesion.supportPos().equals(helper.absolutePos(support))
                    && adhesion.attachmentFace() == Direction.UP,
                "bare patch bonded the entity to the wrong support face"
            );
            check(
                BondedFallingBlocks.getAdhesion(helper.getLevel(), helper.absolutePos(support)).hasEntityBond(
                    Direction.UP
                ),
                "bare patch was not converted into an entity bond"
            );
            check(
                BondedFallingBlocks.hasPatch(helper.getLevel(), helper.absolutePos(support), Direction.UP),
                "uncovered bare patch was consumed by the first entity"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 15)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "Sneaking with the resin bucket bypasses all adhesive interactions")
    static void sneakingResinBucketBypassesAdhesiveMode(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(3, 2, 3);
        helper.setBlock(support, Blocks.STONE);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 2.0D, 3.5D));
        GameTestPlayer player = bucketPlayer(helper, new Vec3(2.5D, 2.0D, 3.5D));
        player.setShiftKeyDown(true);

        check(
            !AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, zombie, Direction.WEST),
            "sneaking bucket still selected an entity"
        );
        check(
            !AdhesiveBondingService.placePatch(
                player,
                InteractionHand.MAIN_HAND,
                helper.absolutePos(support),
                Direction.UP
            ),
            "sneaking bucket still placed an adhesive patch"
        );
        check(
            !BondedFallingBlocks.hasPatch(helper.getLevel(), helper.absolutePos(support), Direction.UP),
            "sneaking bucket left adhesive data"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 15)
    @EmptyTemplate(value = "11x7x8", floor = true)
    @TestHolder(description = "A bare adhesive face bonds every entity touching an uncovered part of the patch")
    static void bareAdhesivePatchBondsMultipleUncoveredEntities(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(4, 2, 3);
        BlockPos coveredSupport = new BlockPos(8, 2, 3);
        helper.setBlock(support, Blocks.STONE);
        helper.setBlock(coveredSupport, Blocks.STONE);
        check(
            BondedFallingBlocks.putPatch(helper.getLevel(), helper.absolutePos(support), Direction.UP),
            "multi-entity adhesive patch could not be placed"
        );
        check(
            BondedFallingBlocks.putPatch(helper.getLevel(), helper.absolutePos(coveredSupport), Direction.UP),
            "covered adhesive patch could not be placed"
        );

        Zombie first = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.15D, 3.0D, 3.15D));
        Zombie second = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.85D, 3.0D, 3.85D));
        SurfaceAdhesiveService.tickEntity(first);
        SurfaceAdhesiveService.tickEntity(second);
        check(first.hasData(PlasticraftAttachments.ENTITY_ADHESION), "first patch corner did not bond its entity");
        check(second.hasData(PlasticraftAttachments.ENTITY_ADHESION), "uncovered patch corner did not bond the second entity");
        Zombie occludedCorner = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.85D, 3.0D, 3.85D));
        SurfaceAdhesiveService.tickEntity(occludedCorner);
        check(
            !occludedCorner.hasData(PlasticraftAttachments.ENTITY_ADHESION),
            "a covered corner bonded because another attached entity occupied a disjoint corner"
        );
        check(
            BondedFallingBlocks.hasPatch(helper.getLevel(), helper.absolutePos(support), Direction.UP),
            "multi-entity patch was consumed"
        );

        AdhesiveBondingService.release(first);
        check(
            BondedFallingBlocks.hasEntityBond(helper.getLevel(), helper.absolutePos(support), Direction.UP),
            "releasing one entity cleared another entity's support bond"
        );
        AdhesiveBondingService.release(second);
        check(
            !BondedFallingBlocks.hasEntityBond(helper.getLevel(), helper.absolutePos(support), Direction.UP)
                && BondedFallingBlocks.hasPatch(helper.getLevel(), helper.absolutePos(support), Direction.UP),
            "releasing the last entity did not restore a bare adhesive face"
        );

        Zombie covering = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(8.5D, 3.0D, 3.5D));
        Zombie occluded = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(8.5D, 3.0D, 3.5D));
        SurfaceAdhesiveService.tickEntity(covering);
        SurfaceAdhesiveService.tickEntity(occluded);
        check(covering.hasData(PlasticraftAttachments.ENTITY_ADHESION), "covering entity did not bond to the patch");
        check(!occluded.hasData(PlasticraftAttachments.ENTITY_ADHESION), "fully covered patch bonded an overlapping entity");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "8x6x8", floor = true)
    @TestHolder(description = "A resin bucket reclaims adhesive only from the directly clicked block face")
    static void resinBucketReclaimsOnlyClickedBlockFace(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(3, 1, 3);
        BlockPos adjacentGround = support.east();
        helper.setBlock(support, Blocks.STONE);
        helper.setBlock(adjacentGround, Blocks.STONE);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 1.5D));

        check(
            BondedFallingBlocks.putPatch(helper.getLevel(), helper.absolutePos(support), Direction.UP),
            "exact-face reclaim patch could not be placed"
        );
        check(
            BondedFallingBlocks.putPatch(helper.getLevel(), helper.absolutePos(support), Direction.NORTH),
            "secondary patch for face-specific reclaim could not be placed"
        );
        check(
            AdhesiveBondingService.placePatch(
                player,
                InteractionHand.MAIN_HAND,
                helper.absolutePos(adjacentGround),
                Direction.UP
            ),
            "neighboring face could not receive its own adhesive patch"
        );
        check(
            BondedFallingBlocks.hasPatch(helper.getLevel(), helper.absolutePos(support), Direction.UP),
            "clicking a neighboring face reclaimed the original adhesive"
        );
        check(
            BondedFallingBlocks.hasPatch(helper.getLevel(), helper.absolutePos(adjacentGround), Direction.UP),
            "clicking a neighboring face did not place adhesive on that face"
        );
        check(
            BondedFallingBlocks.hasPatch(helper.getLevel(), helper.absolutePos(support), Direction.NORTH),
            "clicking a neighboring face reclaimed another face of the original block"
        );
        check(
            player.getMainHandItem().is(Items.BUCKET),
            "placing adhesive on the neighboring face did not consume the resin bucket"
        );

        player.setItemInHand(InteractionHand.MAIN_HAND, PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.asStack());
        check(
            AdhesiveBondingService.placePatch(
                player,
                InteractionHand.MAIN_HAND,
                helper.absolutePos(support),
                Direction.UP
            ),
            "clicking the exact adhesive face did not reclaim it"
        );
        check(
            !BondedFallingBlocks.hasPatch(helper.getLevel(), helper.absolutePos(support), Direction.UP),
            "exact-face reclaim left the adhesive patch behind"
        );
        check(
            BondedFallingBlocks.hasPatch(helper.getLevel(), helper.absolutePos(adjacentGround), Direction.UP),
            "exact-face reclaim removed adhesive from the neighboring block"
        );
        check(
            player.getMainHandItem().is(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get()),
            "exact-face reclaim consumed the held resin bucket"
        );

        check(
            BondedFallingBlocks.connect(
                helper.getLevel(),
                helper.absolutePos(support),
                helper.absolutePos(adjacentGround)
            ),
            "block bond for direct reclaim could not be created"
        );
        check(
            AdhesiveBondingService.placePatch(
                player,
                InteractionHand.MAIN_HAND,
                helper.absolutePos(support),
                Direction.EAST
            ),
            "clicking a block adhesive face did not reclaim it"
        );
        check(
            !BondedFallingBlocks.hasBlockBond(helper.getLevel(), helper.absolutePos(support), Direction.EAST),
            "direct reclaim left the block bond behind"
        );
        check(
            BondedFallingBlocks.hasPatch(helper.getLevel(), helper.absolutePos(support), Direction.NORTH),
            "direct reclaim removed adhesive from another block face"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "8x6x8", floor = true)
    @TestHolder(description = "A resin bucket reclaims only the clicked adhesive face of an entity")
    static void resinBucketReclaimsClickedEntityFace(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(3, 1, 3);
        helper.setBlock(support, Blocks.STONE);
        check(
            BondedFallingBlocks.putPatch(helper.getLevel(), helper.absolutePos(support), Direction.UP),
            "entity reclaim patch could not be placed"
        );
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 2.0D, 3.5D));
        Zombie linked = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 2.0D, 3.5D));
        Zombie northLinked = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 2.0D, 2.5D));
        SurfaceAdhesiveService.tickEntity(target);
        check(target.hasData(PlasticraftAttachments.ENTITY_ADHESION), "entity reclaim target did not bond to its patch");
        check(
            EntityBondManager.connect(helper.getLevel(), target, Direction.EAST, linked, Direction.WEST, true),
            "entity reclaim target could not receive a second adhesive link"
        );
        check(
            EntityBondManager.connect(
                helper.getLevel(), target, Direction.NORTH, northLinked, Direction.SOUTH, true
            ),
            "entity reclaim target could not receive a third adhesive link"
        );
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 0.5D));

        check(
            !AdhesiveBondingService.reclaimEntity(
                player, InteractionHand.MAIN_HAND, target, Direction.WEST
            ),
            "resin bucket reclaimed an entity through an unglued face"
        );
        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, target, Direction.WEST),
            "an unglued entity face could not be selected"
        );
        check(AdhesiveSelectionManager.hasSelection(player), "unglued face selection was not retained");
        AdhesiveSelectionManager.clear(player);

        check(
            AdhesiveBondingService.reclaimEntity(
                player, InteractionHand.MAIN_HAND, target, Direction.EAST
            ),
            "resin bucket did not reclaim the clicked entity-to-entity face"
        );
        EntityBondState remaining = EntityBondManager.get(target);
        check(
            remaining != null
                && remaining.linkAt(Direction.EAST) == null
                && remaining.linkAt(Direction.NORTH) != null,
            "reclaiming one entity face changed another entity face"
        );
        check(!EntityBondManager.hasBonds(linked), "reclaimed face remained linked from its partner");
        check(EntityBondManager.hasBonds(northLinked), "reclaiming one face disconnected another partner");
        check(target.hasData(PlasticraftAttachments.ENTITY_ADHESION), "reclaiming an entity face removed the block anchor");

        check(
            AdhesiveBondingService.reclaimEntity(
                player, InteractionHand.MAIN_HAND, target, Direction.DOWN
            ),
            "resin bucket did not reclaim the clicked block-anchor face"
        );
        check(!target.hasData(PlasticraftAttachments.ENTITY_ADHESION), "clicked block-anchor face was not reclaimed");
        check(EntityBondManager.hasBonds(target), "reclaiming the block anchor removed another entity face");
        check(EntityBondManager.hasBonds(northLinked), "block-anchor reclaim disconnected another partner");
        check(
            BondedFallingBlocks.hasPatch(helper.getLevel(), helper.absolutePos(support), Direction.UP),
            "reclaiming an entity anchor removed the reusable bare patch"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 25)
    @EmptyTemplate(value = "8x6x8", floor = true)
    @TestHolder(description = "Selecting a free face on an anchored entity pulls the second entity to that face")
    static void anchoredEntityFreeFacePullsSecondEntity(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(4, 1, 3);
        helper.setBlock(support, Blocks.STONE);
        check(
            BondedFallingBlocks.putPatch(helper.getLevel(), helper.absolutePos(support), Direction.UP),
            "anchored selection patch could not be placed"
        );
        Zombie anchor = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 2.0D, 3.5D));
        Zombie moving = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(1.5D, 2.0D, 3.5D));
        SurfaceAdhesiveService.tickEntity(anchor);
        check(anchor.hasData(PlasticraftAttachments.ENTITY_ADHESION), "free-face anchor did not bond to its patch");
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 1.5D));

        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, anchor, Direction.WEST),
            "free face on anchored entity could not be selected"
        );
        check(
            AdhesiveBondingService.bondSelectedToEntity(
                player,
                InteractionHand.MAIN_HAND,
                moving,
                Direction.EAST
            ),
            "anchored free face did not start pulling the second entity"
        );
        check(
            !anchor.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT)
                && moving.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT),
            "anchored entity moved instead of the second entity"
        );

        helper.runAfterDelay(12, () -> {
            check(anchor.hasData(PlasticraftAttachments.ENTITY_ADHESION), "pulling another entity released the anchor");
            EntityBondState anchorBonds = EntityBondManager.get(anchor);
            EntityBondState movingBonds = EntityBondManager.get(moving);
            check(
                anchorBonds != null && anchorBonds.linkAt(Direction.WEST) != null,
                "anchored entity did not receive a bond on its selected face"
            );
            check(
                movingBonds != null && movingBonds.linkAt(Direction.EAST) != null,
                "pulled entity did not receive a bond on its clicked face"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "8x7x7", floor = true)
    @TestHolder(description = "Placing a block into a bare patch creates a bidirectional floating block bond")
    static void blockPlacedAtBarePatchBondsBothBlocks(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(4, 3, 3);
        BlockPos placed = support.west();
        BlockPos originalFloor = support.below();
        helper.setBlock(originalFloor, Blocks.SANDSTONE);
        helper.setBlock(support, Blocks.SAND);
        check(
            BondedFallingBlocks.putPatch(helper.getLevel(), helper.absolutePos(support), Direction.WEST),
            "bare adhesive patch could not be placed for block placement"
        );
        GameTestPlayer player = bucketPlayer(helper, new Vec3(2.5D, 2.0D, 2.5D));
        BlockPos absolutePlaced = helper.absolutePos(placed);
        BlockSnapshot snapshot = BlockSnapshot.create(
            helper.getLevel().dimension(),
            helper.getLevel(),
            absolutePlaced
        );
        helper.setBlock(placed, Blocks.SAND);
        BondedFallingBlockEvents.blockPlaced(new BlockEvent.EntityPlaceEvent(
            snapshot,
            helper.getBlockState(support),
            player
        ));

        check(
            BondedFallingBlocks.hasBlockBond(helper.getLevel(), helper.absolutePos(support), Direction.WEST),
            "original block did not record the placed block bond"
        );
        check(
            BondedFallingBlocks.hasBlockBond(helper.getLevel(), absolutePlaced, Direction.EAST),
            "placed block did not record the reverse bond"
        );
        check(
            !BondedFallingBlocks.hasPatch(helper.getLevel(), helper.absolutePos(support), Direction.WEST),
            "block placement did not consume the bare patch"
        );
        helper.getLevel().destroyBlock(helper.absolutePos(originalFloor), false, player);
        helper.runAfterDelay(5, () -> {
            check(helper.getBlockState(support).is(Blocks.SAND), "original bonded sand started falling");
            check(helper.getBlockState(placed).is(Blocks.SAND), "newly bonded sand started falling");
            check(
                BondedFallingBlocks.isBonded(helper.getLevel(), helper.absolutePos(support))
                    && BondedFallingBlocks.isBonded(helper.getLevel(), absolutePlaced),
                "floating sand pair lost its bidirectional bond"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 45)
    @EmptyTemplate(value = "10x8x7", floor = true)
    @TestHolder(description = "An entity-bonded falling block lands and preserves the entity-to-block bond")
    static void entityBondedFallingBlockLandsAndKeepsBond(ExtendedGameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 5, 3);
        helper.setBlock(sourcePos, Blocks.SAND);
        FallingBlockEntity sand = FallingBlockEntity.fall(
            helper.getLevel(),
            helper.absolutePos(sourcePos),
            Blocks.SAND.defaultBlockState()
        );
        sand.setNoGravity(true);
        Zombie support = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(6.5D, 5.0D, 3.5D));
        support.setNoGravity(true);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(4.5D, 3.0D, 1.5D));
        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, sand, Direction.EAST),
            "landing sand could not be selected"
        );
        check(AdhesiveBondingService.bondSelectedToEntity(
            player,
            InteractionHand.MAIN_HAND,
            support,
            Direction.WEST
        ), "landing sand could not be bonded to an entity");

        helper.runAfterDelay(14, () -> {
            EntityBondState bonds = EntityBondManager.get(sand);
            check(bonds != null && EntityBondManager.isFollower(sand), "landing sand was not an entity follower");
            double landingY = helper.absolutePos(new BlockPos(0, 2, 0)).getY() + 0.02D;
            Vec3 offset = bonds.offsetFromLeader();
            Vec3 targetPosition = new Vec3(
                support.getX() + offset.x,
                landingY,
                support.getZ() + offset.z
            );
            BlockPos landingPos = BlockPos.containing(targetPosition);
            helper.getLevel().setBlock(landingPos.below(), Blocks.STONE.defaultBlockState(), 3);
            support.setPos(support.getX(), landingY - offset.y, support.getZ());
            sand.time = 599;

            helper.runAfterDelay(4, () -> {
                check(!sand.isAlive(), "entity-bonded falling block did not land as a block");
                check(helper.getLevel().getBlockState(landingPos).is(Blocks.SAND), "landed block state was not sand");
                EntityAdhesion adhesion = support.getExistingDataOrNull(PlasticraftAttachments.ENTITY_ADHESION.get());
                check(adhesion != null, "remaining entity was not anchored to the landed block");
                check(adhesion.supportPos().equals(landingPos), "landed block bond used the wrong support position");
                check(
                    BondedFallingBlocks.getAdhesion(helper.getLevel(), landingPos)
                        .hasEntityBond(adhesion.attachmentFace()),
                    "landed block did not retain the reverse entity bond"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "8x6x7", floor = true)
    @TestHolder(description = "Plastic entity selection remembers and toggles the clicked model face")
    static void plasticSelectionFaceControlsFinalOrientation(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(6, 2, 3);
        BlockPos occupied = support.west();
        helper.setBlock(support, Blocks.STONE);
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 2.0D, 3.5D));
        anvil.setNoGravity(true);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 1.5D));

        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, anvil, Direction.EAST),
            "plastic side selection failed"
        );
        Direction firstFace = AdhesiveSelectionManager.getSelectedFace(player);
        check(firstFace == Direction.EAST, "clicked plastic side was not stored as a local face");
        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, anvil, Direction.EAST),
            "plastic face toggle failed"
        );
        Direction toggledFace = AdhesiveSelectionManager.getSelectedFace(player);
        check(toggledFace == Direction.WEST, "clicking the selected face did not choose its opposite");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(support),
            Direction.WEST
        ), "selected plastic face could not be bonded");

        helper.runAfterDelay(12, () -> {
            BondedEntityBlockEntity bonded = bondedBlockEntity(helper, occupied);
            check(
                bonded.getPlasticOrientation().worldDirection(toggledFace) == Direction.EAST,
                "selected plastic face did not finish against the support face"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 35)
    @EmptyTemplate(value = "9x6x7", floor = true)
    @TestHolder(description = "Entities bond face-to-face and reject a second bond on an occupied face")
    static void entitiesBondByFreeCollisionFaces(ExtendedGameTestHelper helper) {
        Zombie source = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 3.5D));
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(5.5D, 2.0D, 3.5D));
        Zombie rejected = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 2.5D));
        source.setNoGravity(true);
        target.setNoGravity(true);
        rejected.setNoGravity(true);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 1.5D));

        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, source, Direction.EAST),
            "source entity selection failed"
        );
        check(
            AdhesiveBondingService.bondSelectedToEntity(
                player,
                InteractionHand.MAIN_HAND,
                target,
                Direction.WEST
            ),
            "entity-to-entity bonding failed"
        );
        helper.runAfterDelay(12, () -> {
            EntityBondState sourceBonds = EntityBondManager.get(source);
            EntityBondState targetBonds = EntityBondManager.get(target);
            check(sourceBonds != null && sourceBonds.linkAt(Direction.EAST) != null, "source face was not bonded");
            check(targetBonds != null && targetBonds.linkAt(Direction.WEST) != null, "target face was not bonded");
            Vec3 fixedSourcePosition = source.position();
            source.move(MoverType.SELF, new Vec3(1.0D, 0.0D, 0.0D));
            EntityBondManager.tick(source);
            check(close(source.position(), fixedSourcePosition), "bonded follower separated from its leader");
            Vec3 leaderMovement = new Vec3(0.0D, 0.0D, 0.5D);
            target.setPos(target.position().add(leaderMovement));
            EntityBondManager.tick(target);
            check(
                close(source.position(), fixedSourcePosition.add(leaderMovement)),
                "bonded follower did not follow its leader"
            );

            player.setItemInHand(InteractionHand.MAIN_HAND, PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.asStack());
            check(
                AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, rejected, Direction.EAST),
                "second source selection failed"
            );
            check(
                !AdhesiveBondingService.bondSelectedToEntity(
                    player,
                    InteractionHand.MAIN_HAND,
                    target,
                    Direction.WEST
                ),
                "occupied target face accepted a second entity"
            );
            check(
                player.getMainHandItem().is(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get()),
                "rejected entity bond consumed resin"
            );
            check(
                AdhesiveBondingService.bondSelectedToEntity(
                    player,
                    InteractionHand.MAIN_HAND,
                    target,
                    Direction.NORTH
                ),
                "a free target face rejected another entity"
            );
            helper.runAfterDelay(12, () -> {
                EntityBondState expandedTargetBonds = EntityBondManager.get(target);
                check(
                    expandedTargetBonds != null
                        && expandedTargetBonds.linkAt(Direction.WEST) != null
                        && expandedTargetBonds.linkAt(Direction.NORTH) != null,
                    "target did not retain bonds on two independent faces"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "10x6x8", floor = true)
    @TestHolder(description = "Universal plastic bonds with exact side contact and remains stationary")
    static void universalPlasticEntityBondHasNoSideGapOrDrift(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity moving = createUniversalPlastic(helper, new Vec3(2.5D, 2.0D, 4.5D));
        UniversalPlasticEntity support = createUniversalPlastic(helper, new Vec3(6.5D, 2.0D, 4.5D));
        moving.setNoGravity(true);
        support.setNoGravity(true);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(4.5D, 2.0D, 2.5D));

        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, moving, Direction.EAST),
            "universal plastic selection failed"
        );
        check(
            AdhesiveBondingService.bondSelectedToEntity(
                player,
                InteractionHand.MAIN_HAND,
                support,
                Direction.WEST
            ),
            "universal plastic entity bond failed"
        );

        helper.runAfterDelay(12, () -> {
            check(EntityBondManager.hasBonds(moving), "universal plastic bond was not completed");
            check(
                close(
                    AdhesiveFaces.storedFaceCenter(moving, Direction.EAST),
                    AdhesiveFaces.worldFaceCenter(support, Direction.WEST)
                ),
                "side-bonded universal plastic retained a gap"
            );
            Vec3 movingPosition = moving.position();
            Vec3 supportPosition = support.position();
            helper.runAfterDelay(6, () -> {
                check(close(moving.position(), movingPosition), "bonded universal plastic follower drifted");
                check(close(support.position(), supportPosition), "bonded universal plastic leader drifted");
                helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks = 15)
    @EmptyTemplate(value = "10x6x8", floor = true)
    @TestHolder(description = "Rotated universal plastic cannot be bonded through the floor")
    static void universalPlasticEntityBondRejectsRotatedFloorOverlap(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity moving = createUniversalPlastic(helper, new Vec3(2.5D, 1.0D, 4.5D));
        UniversalPlasticEntity support = createUniversalPlastic(helper, new Vec3(6.5D, 1.0D, 4.5D));
        moving.setNoGravity(true);
        support.setNoGravity(true);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(4.5D, 2.0D, 2.5D));

        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, moving, Direction.DOWN),
            "universal plastic bottom selection failed"
        );
        AdhesivePathPlanner.Plan plan = AdhesivePathPlanner.planToEntity(
            helper.getLevel(),
            moving,
            player,
            support,
            Direction.WEST,
            Direction.DOWN
        );
        check(
            !moving.plasticraft$canOccupyBlocks(plan.targetOrientation(), plan.targetPosition()),
            "test setup did not rotate universal plastic into the floor"
        );
        check(
            plan.status() == AdhesivePathPlanner.Status.TARGET_BLOCKED,
            "rotated universal plastic floor overlap was not reported as a blocked target: " + plan.status()
        );
        check(
            !AdhesiveBondingService.bondSelectedToEntity(
                player,
                InteractionHand.MAIN_HAND,
                support,
                Direction.WEST
            ),
            "side bond accepted universal plastic rotated one pixel into the floor"
        );
        check(
            !moving.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT),
            "rejected universal plastic side bond started transit"
        );
        check(
            player.getMainHandItem().is(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get()),
            "rejected universal plastic side bond consumed resin"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 35)
    @EmptyTemplate(value = "11x7x8", floor = true)
    @TestHolder(description = "Every member of a transported universal plastic group snaps to its bonded face")
    static void transportedUniversalPlasticGroupHasNoFollowerGap(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity follower = createUniversalPlastic(helper, new Vec3(2.5D, 2.0D, 4.5D));
        UniversalPlasticEntity selected = createUniversalPlastic(helper, new Vec3(3.5D, 2.0D, 4.5D));
        UniversalPlasticEntity support = createUniversalPlastic(helper, new Vec3(7.5D, 2.0D, 4.5D));
        follower.setNoGravity(true);
        selected.setNoGravity(true);
        support.setNoGravity(true);
        check(
            EntityBondManager.connect(
                helper.getLevel(), follower, Direction.EAST, selected, Direction.WEST, true
            ),
            "universal plastic follower could not be bonded"
        );
        follower.setPos(follower.position().add(-1.0D / 16.0D, 0.0D, 0.0D));
        GameTestPlayer player = bucketPlayer(helper, new Vec3(5.5D, 2.0D, 2.5D));

        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, selected, Direction.EAST),
            "group leader selection failed"
        );
        check(
            AdhesiveBondingService.bondSelectedToEntity(
                player,
                InteractionHand.MAIN_HAND,
                support,
                Direction.WEST
            ),
            "universal plastic group could not be bonded to its support"
        );

        helper.runAfterDelay(12, () -> {
            check(
                close(
                    AdhesiveFaces.storedFaceCenter(follower, Direction.EAST),
                    AdhesiveFaces.storedFaceCenter(selected, Direction.WEST)
                ),
                "unselected universal plastic follower retained a gap"
            );
            check(
                close(
                    AdhesiveFaces.storedFaceCenter(selected, Direction.EAST),
                    AdhesiveFaces.storedFaceCenter(support, Direction.WEST)
                ),
                "selected universal plastic did not touch its support"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "10x8x8", floor = true)
    @TestHolder(description = "Rotating a transported universal plastic group keeps its bonded faces aligned")
    static void rotatingUniversalPlasticGroupKeepsFollowerContact(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity selected = createUniversalPlastic(helper, new Vec3(2.5D, 3.0D, 4.5D));
        UniversalPlasticEntity follower = createUniversalPlastic(helper, new Vec3(2.5D, 3.875D, 4.5D));
        UniversalPlasticEntity support = createUniversalPlastic(helper, new Vec3(6.5D, 3.0D, 4.5D));
        selected.setNoGravity(true);
        follower.setNoGravity(true);
        support.setNoGravity(true);
        check(
            EntityBondManager.connect(
                helper.getLevel(), selected, Direction.UP, follower, Direction.DOWN, true
            ),
            "rotating universal plastic follower could not be bonded"
        );
        GameTestPlayer player = bucketPlayer(helper, new Vec3(4.5D, 3.0D, 2.5D));

        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, selected, Direction.DOWN),
            "rotating universal plastic selection failed"
        );
        check(
            AdhesiveBondingService.bondSelectedToEntity(
                player,
                InteractionHand.MAIN_HAND,
                support,
                Direction.WEST
            ),
            "rotating universal plastic entity bond failed"
        );

        helper.runAfterDelay(14, () -> {
            check(
                selected.getOrientation().attachmentFace() == Direction.WEST,
                "selected universal plastic did not rotate at the transit endpoint"
            );
            check(
                follower.getOrientation().attachmentFace() == Direction.WEST,
                "rotating selected universal plastic did not rotate its follower"
            );
            check(
                close(
                    AdhesiveFaces.storedFaceCenter(selected, Direction.UP),
                    AdhesiveFaces.storedFaceCenter(follower, Direction.DOWN)
                ),
                "rotating selected universal plastic left its follower behind"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 15)
    @EmptyTemplate(value = "10x8x8", floor = true)
    @TestHolder(description = "A route-only rotation collision does not reject the adhesive transit")
    static void rotatingTransitAllowsOrientationSwitchCollision(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity moving = createUniversalPlastic(helper, new Vec3(2.5D, 3.0D, 4.5D));
        moving.setNoGravity(true);
        Vec3 targetPosition = helper.absoluteVec(new Vec3(6.5D, 3.0D, 4.5D));
        PlasticEntityOrientation targetOrientation = new PlasticEntityOrientation(Direction.WEST, 1);
        helper.setBlock(5, 2, 4, Blocks.STONE);
        BlockPos support = new BlockPos(7, 3, 4);
        helper.setBlock(support, Blocks.STONE);
        AdhesivePathPlanner.Plan unrotatedPlan = new AdhesivePathPlanner.Plan(
            AdhesivePathPlanner.Status.VALID,
            List.of(moving.position(), targetPosition),
            targetPosition,
            BlockPos.containing(targetPosition),
            moving.getOrientation(),
            Direction.DOWN,
            moving.position().distanceTo(targetPosition)
        );
        AdhesivePathPlanner.Plan rotatingPlan = new AdhesivePathPlanner.Plan(
            AdhesivePathPlanner.Status.VALID,
            List.of(moving.position(), targetPosition),
            targetPosition,
            BlockPos.containing(targetPosition),
            targetOrientation,
            Direction.DOWN,
            moving.position().distanceTo(targetPosition)
        );

        check(
            moving.plasticraft$canOccupyBlocks(targetOrientation, targetPosition),
            "rotation-switch test left the target orientation inside the obstacle"
        );
        check(
            AdhesiveGroupTransform.isPlanClear(helper.getLevel(), moving, unrotatedPlan),
            "rotation-switch obstacle blocked the unrotated path"
        );
        check(
            AdhesiveGroupTransform.isPlanClear(helper.getLevel(), moving, rotatingPlan),
            "route-only rotation collision rejected the target position"
        );
        PlasticEntityOrientation startOrientation = moving.getOrientation();
        long gameTime = helper.getLevel().getGameTime();
        AdhesiveTransit transit = new AdhesiveTransit(
            helper.absolutePos(support),
            Direction.WEST,
            BuiltInRegistries.BLOCK.getKey(Blocks.STONE),
            Direction.DOWN,
            Optional.empty(),
            -1,
            Vec3.ZERO,
            List.of(moving.position(), targetPosition),
            gameTime - 8,
            10,
            true,
            true,
            startOrientation.pack(),
            targetOrientation.pack()
        );
        moving.setData(PlasticraftAttachments.ADHESIVE_TRANSIT, transit);
        AdhesiveBondingService.tickAdhesiveTransit(moving, true);
        check(moving.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT),
            "visual rotation switched physical collision early and canceled the transit");
        check(moving.getOrientation().equals(startOrientation),
            "physical collision changed orientation before the adhesive endpoint");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "10x8x8", floor = true)
    @TestHolder(description = "A transported mixed entity group rotates plastic members but not ordinary member facing")
    static void rotatingMixedEntityGroupPreservesOrdinaryFacing(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity selected = createUniversalPlastic(helper, new Vec3(4.5D, 6.0D, 4.5D));
        Vec3 middlePosition = helper.absoluteVec(new Vec3(3.5D, 6.0D, 4.5D));
        ArmorStand middle = new ArmorStand(helper.getLevel(), middlePosition.x, middlePosition.y, middlePosition.z);
        UniversalPlasticEntity follower = createUniversalPlastic(helper, new Vec3(2.5D, 6.0D, 4.5D));
        UniversalPlasticEntity support = createUniversalPlastic(helper, new Vec3(7.5D, 6.0D, 4.5D));
        selected.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(middle), "failed to add mixed group ordinary member");
        middle.setNoGravity(true);
        follower.setNoGravity(true);
        support.setNoGravity(true);
        middle.setYRot(37.0F);
        middle.setXRot(11.0F);
        middle.setYHeadRot(37.0F);
        middle.setYBodyRot(37.0F);
        check(
            EntityBondManager.connect(
                helper.getLevel(), selected, Direction.WEST, middle, Direction.EAST, true
            ),
            "mixed group root could not bond to the ordinary member"
        );
        check(
            EntityBondManager.connect(
                helper.getLevel(), middle, Direction.WEST, follower, Direction.EAST, true
            ),
            "mixed group ordinary member could not bond to the plastic follower"
        );
        GameTestPlayer player = bucketPlayer(helper, new Vec3(5.0D, 6.0D, 2.5D));
        player.setYRot(-90.0F);
        float middleYRot = middle.getYRot();
        float middleXRot = middle.getXRot();
        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, selected, Direction.DOWN),
            "mixed group rotation selection failed"
        );
        check(
            AdhesiveBondingService.bondSelectedToEntity(
                player,
                InteractionHand.MAIN_HAND,
                support,
                Direction.WEST
            ),
            "mixed group rotation transit failed"
        );

        helper.runAfterDelay(14, () -> {
            check(
                selected.getOrientation().attachmentFace() == Direction.WEST
                    && follower.getOrientation().attachmentFace() == Direction.WEST,
                "mixed group did not rotate every plastic member"
            );
            check(
                Math.abs(middle.getYRot() - middleYRot) <= EPSILON
                    && Math.abs(middle.getXRot() - middleXRot) <= EPSILON,
                "mixed group rotated the ordinary member: expected="
                    + middleYRot + "/" + middleXRot
                    + ", actual=" + middle.getYRot() + "/" + middle.getXRot()
            );
            check(
                close(
                    AdhesiveFaces.storedFaceCenter(selected, Direction.WEST),
                    AdhesiveFaces.storedFaceCenter(middle, Direction.UP)
                ) && close(
                    AdhesiveFaces.storedFaceCenter(middle, Direction.DOWN),
                    AdhesiveFaces.storedFaceCenter(follower, Direction.EAST)
                ),
                "mixed group did not retain its internal contacts after rotation"
            );
            EntityBondState middleBonds = EntityBondManager.get(middle);
            check(
                middleBonds != null
                    && middleBonds.linkAt(Direction.UP) != null
                    && middleBonds.linkAt(Direction.DOWN) != null,
                "mixed group did not rotate the ordinary entity's stored bond faces"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 15)
    @EmptyTemplate(value = "10x7x8", floor = true)
    @TestHolder(description = "A transported group rejects a block overlap on an unselected follower")
    static void transportedGroupRejectsFollowerBlockOverlap(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity follower = createUniversalPlastic(helper, new Vec3(3.5D, 3.0D, 3.5D));
        UniversalPlasticEntity selected = createUniversalPlastic(helper, new Vec3(3.5D, 3.0D, 4.5D));
        UniversalPlasticEntity support = createUniversalPlastic(helper, new Vec3(7.5D, 3.0D, 4.5D));
        follower.setNoGravity(true);
        selected.setNoGravity(true);
        support.setNoGravity(true);
        check(
            EntityBondManager.connect(
                helper.getLevel(), follower, Direction.SOUTH, selected, Direction.NORTH, true
            ),
            "follower could not be bonded to the selected plastic"
        );
        helper.setBlock(6, 3, 3, Blocks.OBSIDIAN);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(5.5D, 3.0D, 2.5D));
        AdhesivePathPlanner.Plan plan = AdhesivePathPlanner.planToEntity(
            helper.getLevel(),
            selected,
            player,
            support,
            Direction.WEST,
            Direction.EAST
        );
        check(plan.valid(), "root-only plan was unexpectedly blocked: " + plan.status());
        Vec3 followerPosition = follower.position();
        Vec3 selectedPosition = selected.position();
        Vec3 supportPosition = support.position();
        PlasticEntityOrientation followerOrientation = follower.getOrientation();
        PlasticEntityOrientation selectedOrientation = selected.getOrientation();
        PlasticEntityOrientation supportOrientation = support.getOrientation();

        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, selected, Direction.EAST),
            "follower block overlap selection failed"
        );
        check(
            !AdhesiveBondingService.bondSelectedToEntity(
                player,
                InteractionHand.MAIN_HAND,
                support,
                Direction.WEST
            ),
            "group transit accepted a follower overlap with a block"
        );
        check(
            player.getMainHandItem().is(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get()),
            "rejected follower block overlap consumed resin"
        );
        check(
            !selected.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT)
                && !follower.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT)
                && !support.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT),
            "rejected follower block overlap started group transit"
        );
        check(
            close(follower.position(), followerPosition)
                && close(selected.position(), selectedPosition)
                && close(support.position(), supportPosition)
                && follower.getOrientation().equals(followerOrientation)
                && selected.getOrientation().equals(selectedOrientation)
                && support.getOrientation().equals(supportOrientation),
            "rejected follower block overlap changed entity positions or orientations"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 15)
    @EmptyTemplate(value = "10x7x8", floor = true)
    @TestHolder(description = "A transported group rejects an entity overlap on an unselected follower")
    static void transportedGroupRejectsFollowerEntityOverlap(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity follower = createUniversalPlastic(helper, new Vec3(3.5D, 3.0D, 3.5D));
        UniversalPlasticEntity selected = createUniversalPlastic(helper, new Vec3(3.5D, 3.0D, 4.5D));
        UniversalPlasticEntity support = createUniversalPlastic(helper, new Vec3(7.5D, 3.0D, 4.5D));
        Zombie blocker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(6.5D, 3.0D, 3.5D));
        follower.setNoGravity(true);
        selected.setNoGravity(true);
        support.setNoGravity(true);
        blocker.setNoGravity(true);
        check(
            EntityBondManager.connect(
                helper.getLevel(), follower, Direction.SOUTH, selected, Direction.NORTH, true
            ),
            "follower could not be bonded to the selected plastic"
        );
        GameTestPlayer player = bucketPlayer(helper, new Vec3(5.5D, 3.0D, 2.5D));
        AdhesivePathPlanner.Plan plan = AdhesivePathPlanner.planToEntity(
            helper.getLevel(),
            selected,
            player,
            support,
            Direction.WEST,
            Direction.EAST
        );
        check(plan.valid(), "root-only plan was unexpectedly blocked: " + plan.status());
        Vec3 followerPosition = follower.position();
        Vec3 selectedPosition = selected.position();
        Vec3 supportPosition = support.position();
        PlasticEntityOrientation followerOrientation = follower.getOrientation();
        PlasticEntityOrientation selectedOrientation = selected.getOrientation();
        PlasticEntityOrientation supportOrientation = support.getOrientation();

        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, selected, Direction.EAST),
            "follower entity overlap selection failed"
        );
        check(
            !AdhesiveBondingService.bondSelectedToEntity(
                player,
                InteractionHand.MAIN_HAND,
                support,
                Direction.WEST
            ),
            "group transit accepted a follower overlap with an ordinary entity"
        );
        check(
            player.getMainHandItem().is(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get()),
            "rejected follower entity overlap consumed resin"
        );
        check(
            !selected.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT)
                && !follower.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT)
                && !support.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT),
            "rejected follower entity overlap started group transit"
        );
        check(
            close(follower.position(), followerPosition)
                && close(selected.position(), selectedPosition)
                && close(support.position(), supportPosition)
                && follower.getOrientation().equals(followerOrientation)
                && selected.getOrientation().equals(selectedOrientation)
                && support.getOrientation().equals(supportOrientation),
            "rejected follower entity overlap changed entity positions or orientations"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 15)
    @EmptyTemplate(value = "8x7x7", floor = true)
    @TestHolder(description = "An airborne player cannot side-push universal plastic")
    static void airbornePlayerCannotPushUniversalPlastic(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity plastic = createUniversalPlastic(helper, new Vec3(4.5D, 3.0D, 3.5D));
        plastic.setNoGravity(true);
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 playerPosition = helper.absoluteVec(new Vec3(3.69D, 3.0D, 3.5D));
        player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z);
        player.setOnGround(true);
        Vec3 start = plastic.position();

        player.move(MoverType.SELF, new Vec3(0.35D, 0.0D, 0.0D));

        check(close(plastic.position(), start), "airborne player pushed universal plastic");
        player.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "10x8x8", floor = true)
    @TestHolder(description = "An inverted resin anvil bonds directly to the true cauldron rim surface")
    static void invertedAnvilBondUsesTrueCauldronSurface(ExtendedGameTestHelper helper) {
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 4.0D, 4.5D));
        HardenedResinCauldronEntity cauldron = createCauldron(helper, new Vec3(6.5D, 2.0D, 4.5D));
        anvil.setNoGravity(true);
        cauldron.setNoGravity(true);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(4.5D, 2.0D, 2.5D));

        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, anvil, Direction.UP),
            "anvil top selection failed"
        );
        check(
            AdhesiveBondingService.bondSelectedToEntity(
                player,
                InteractionHand.MAIN_HAND,
                cauldron,
                Direction.UP
            ),
            "anvil-to-cauldron entity bond failed"
        );

        helper.runAfterDelay(12, () -> {
            check(
                anvil.getOrientation().worldDirection(Direction.UP) == Direction.DOWN,
                "selected anvil top did not rotate toward the cauldron"
            );
            check(
                close(
                    AdhesiveFaces.storedFaceCenter(anvil, Direction.UP),
                    AdhesiveFaces.worldFaceCenter(cauldron, Direction.UP)
                ),
                "anvil and cauldron true collision surfaces did not touch"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "10x8x8", floor = true)
    @TestHolder(description = "An anvil bonded by an asymmetric side stays centered over a resin cauldron")
    static void sideBondedAnvilCentersOnCauldron(ExtendedGameTestHelper helper) {
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 4.0D, 4.5D));
        HardenedResinCauldronEntity cauldron = createCauldron(helper, new Vec3(6.5D, 2.0D, 4.5D));
        anvil.setNoGravity(true);
        cauldron.setNoGravity(true);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(4.5D, 2.0D, 2.5D));

        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, anvil, Direction.EAST),
            "anvil side selection failed"
        );
        check(
            AdhesiveBondingService.bondSelectedToEntity(
                player,
                InteractionHand.MAIN_HAND,
                cauldron,
                Direction.UP
            ),
            "sideways anvil-to-cauldron entity bond failed"
        );

        helper.runAfterDelay(12, () -> {
            check(
                anvil.getOrientation().worldDirection(Direction.EAST) == Direction.DOWN,
                "selected asymmetric anvil side did not rotate toward the cauldron"
            );
            check(
                Math.abs(
                    AdhesiveFaces.storedFaceCenter(anvil, Direction.EAST).y
                        - AdhesiveFaces.worldFaceCenter(cauldron, Direction.UP).y
                ) < EPSILON,
                "sideways anvil did not touch the cauldron surface"
            );
            check(
                close(
                    AdhesiveFaces.storedFaceAlignmentPoint(anvil, Direction.EAST),
                    AdhesiveFaces.worldFaceAlignmentPoint(cauldron, Direction.UP)
                ),
                "sideways anvil bond anchors did not align"
            );
            Vec3 anvilCenter = anvil.plasticraft$getRotationCenter();
            Vec3 cauldronCenter = cauldron.plasticraft$getRotationCenter();
            check(
                Math.abs(anvilCenter.x - cauldronCenter.x) < EPSILON
                    && Math.abs(anvilCenter.z - cauldronCenter.z) < EPSILON,
                "sideways anvil was not centered over the cauldron"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "8x6x8", floor = true)
    @TestHolder(description = "Strong knockback moves an unanchored entity component as one group")
    static void strongKnockbackMovesUnanchoredEntityComponent(ExtendedGameTestHelper helper) {
        Zombie first = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 3.5D));
        Zombie middle = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 2.0D, 3.5D));
        Zombie last = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 2.0D, 3.5D));
        first.setNoGravity(true);
        middle.setNoGravity(true);
        last.setNoGravity(true);
        check(EntityBondManager.connect(
            helper.getLevel(), first, Direction.EAST, middle, Direction.WEST, true
        ), "first entity bond failed");
        check(EntityBondManager.connect(
            helper.getLevel(), middle, Direction.EAST, last, Direction.WEST, true
        ), "second entity bond failed");

        Vec3 firstStart = first.position();
        Vec3 middleOffset = middle.position().subtract(firstStart);
        Vec3 lastOffset = last.position().subtract(firstStart);
        CommonHooks.onLivingKnockBack(first, 2.5F, 0.0D, 1.0D);
        check(EntityBondManager.hasBonds(first), "strong knockback disconnected the struck entity");
        check(
            !first.hasData(PlasticraftAttachments.ADHESIVE_ELASTIC_MOTION),
            "unanchored entity component started elastic motion"
        );
        EntityBondState firstBonds = EntityBondManager.get(first);
        check(
            firstBonds != null && firstBonds.leaderUuid().equals(first.getUUID()),
            "struck entity did not become the unanchored component leader"
        );
        first.setDeltaMovement(0.45D, 0.0D, 0.0D);
        helper.runAfterDelay(2, () -> {
            check(first.getX() > firstStart.x + 0.05D, "unanchored struck entity did not move");
            check(
                close(middle.position(), first.position().add(middleOffset)),
                "unanchored middle entity did not follow the group leader"
            );
            check(
                close(last.position(), first.position().add(lastOffset)),
                "unanchored last entity did not follow the group leader"
            );
            EntityBondState middleBonds = EntityBondManager.get(middle);
            EntityBondState lastBonds = EntityBondManager.get(last);
            check(
                middleBonds != null
                    && middleBonds.linkAt(Direction.WEST) != null
                    && middleBonds.linkAt(Direction.EAST) != null,
                "strong knockback changed the middle entity's bonds"
            );
            check(
                lastBonds != null && lastBonds.linkAt(Direction.WEST) != null,
                "strong knockback disconnected an entity that was not struck"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "8x6x8", floor = true)
    @TestHolder(description = "A block-anchored entity component rebounds together after a follower is hit")
    static void strongKnockbackReboundsAnchoredEntityComponent(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(2, 1, 3);
        helper.setBlock(support, Blocks.STONE);
        Zombie anchored = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 3.5D));
        Zombie struck = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 2.0D, 3.5D));
        anchored.setNoGravity(true);
        struck.setNoGravity(true);
        check(EntityBondManager.connect(
            helper.getLevel(), anchored, Direction.EAST, struck, Direction.WEST, true
        ), "anchored entity bond failed");

        Vec3 anchoredStart = anchored.position();
        Vec3 struckStart = struck.position();
        Vec3 struckOffset = struckStart.subtract(anchoredStart);
        anchored.setData(
            PlasticraftAttachments.ENTITY_ADHESION, new EntityAdhesion(
            helper.absolutePos(support),
            Direction.UP,
            BuiltInRegistries.BLOCK.getKey(Blocks.STONE),
            anchoredStart,
            true
        ));

        CommonHooks.onLivingKnockBack(struck, 2.5F, 0.0D, 1.0D);
        check(
            anchored.hasData(PlasticraftAttachments.ADHESIVE_ELASTIC_MOTION),
            "block anchor did not receive the group elastic motion"
        );
        check(
            !struck.hasData(PlasticraftAttachments.ADHESIVE_ELASTIC_MOTION),
            "struck follower retained a separate elastic motion"
        );
        EntityBondState struckBonds = EntityBondManager.get(struck);
        check(
            struckBonds != null && struckBonds.leaderUuid().equals(anchored.getUUID()),
            "block anchor did not become the rebound component leader"
        );
        helper.runAfterDelay(2, () -> {
            check(
                anchored.position().distanceToSqr(anchoredStart) > 0.01D,
                "anchored component did not move away from its adhesive point"
            );
            check(
                close(struck.position(), anchored.position().add(struckOffset)),
                "struck follower did not rebound with the anchored component"
            );
            helper.runAfterDelay(20, () -> {
                check(
                    !anchored.hasData(PlasticraftAttachments.ADHESIVE_ELASTIC_MOTION),
                    "anchored component rebound did not finish"
                );
                check(close(anchored.position(), anchoredStart), "block anchor did not return after rebound");
                check(
                    close(struck.position(), anchored.position().add(struckOffset)),
                    "anchored component did not return as one group"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "8x6x8", floor = true)
    @TestHolder(description = "Removing one entity preserves bonds between the remaining entities")
    static void removingEntityPreservesRemainingBonds(ExtendedGameTestHelper helper) {
        Zombie removed = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 3.5D));
        Zombie first = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 2.0D, 3.5D));
        Zombie second = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 2.0D, 3.5D));
        removed.setNoGravity(true);
        first.setNoGravity(true);
        second.setNoGravity(true);
        check(EntityBondManager.connect(
            helper.getLevel(), removed, Direction.EAST, first, Direction.WEST, true
        ), "removed-to-first bond failed");
        check(EntityBondManager.connect(
            helper.getLevel(), first, Direction.EAST, second, Direction.WEST, true
        ), "remaining entity bond failed");

        check(EntityBondManager.disconnectEntity(helper.getLevel(), removed), "removed entity was not disconnected");
        check(!EntityBondManager.hasBonds(removed), "removed entity retained a bond");
        EntityBondState firstBonds = EntityBondManager.get(first);
        EntityBondState secondBonds = EntityBondManager.get(second);
        check(
            firstBonds != null
                && firstBonds.linkAt(Direction.EAST) != null
                && firstBonds.linkAt(Direction.WEST) == null,
            "first remaining entity lost the wrong bond"
        );
        check(
            secondBonds != null && secondBonds.linkAt(Direction.WEST) != null,
            "remaining entities were disconnected from each other"
        );
        check(
            firstBonds.leaderUuid().equals(secondBonds.leaderUuid()),
            "remaining component did not receive a shared leader"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "8x6x7", floor = true)
    @TestHolder(description = "Bonded entities do not push their own component into continuous drift")
    static void bondedMembersDoNotPushTheirComponent(ExtendedGameTestHelper helper) {
        ResinAnvilEntity anvil = createResinAnvil(helper, new Vec3(3.5D, 1.0D, 3.5D));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.29D, 1.0D, 3.5D));
        anvil.setNoGravity(true);
        zombie.setNoGravity(true);
        check(EntityBondManager.connect(
            helper.getLevel(), anvil, Direction.EAST, zombie, Direction.WEST, true
        ), "resin-anvil-to-entity bond failed");
        Vec3 anvilStart = anvil.position();
        Vec3 zombieStart = zombie.position();

        for (int tick = 0; tick < 20; tick++) {
            zombie.push(anvil);
            anvil.push(zombie);
            zombie.move(MoverType.SELF, zombie.getDeltaMovement());
            EntityBondManager.tick(zombie);
        }

        check(close(anvil.position(), anvilStart), "bonded resin anvil drifted under internal pushing");
        check(close(zombie.position(), zombieStart), "bonded living entity drifted under internal pushing");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "13x6x8", floor = true)
    @TestHolder(description = "A player smoothly pushes a bonded plastic component through its newest follower")
    static void playerSmoothlyPushesBondedPlasticFollower(ExtendedGameTestHelper helper) {
        for (int x = 2; x <= 10; x++) {
            for (int z = 2; z <= 6; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        }
        HardenedResinCauldronEntity newestFollower = createCauldron(helper, new Vec3(4.5D, 2.0D, 3.5D));
        ResinAnvilEntity middleFollower = createResinAnvil(helper, new Vec3(5.5D, 2.0D, 3.5D));
        HardenedResinAnvilEntity leader = createAnvil(helper, new Vec3(6.5D, 2.0D, 3.5D));
        check(EntityBondManager.connect(
            helper.getLevel(), middleFollower, Direction.EAST, leader, Direction.WEST, false
        ), "resin anvil could not be bonded as a follower");
        check(EntityBondManager.connect(
            helper.getLevel(), newestFollower, Direction.EAST, middleFollower, Direction.WEST, false
        ), "resin cauldron could not be bonded as the newest follower");
        check(EntityBondManager.isFollower(newestFollower), "newest bonded plastic entity was not a follower");
        List<AbstractPlasticEntity> component = List.of(newestFollower, middleFollower, leader);
        for (AbstractPlasticEntity member : component) {
            EntityBondState state = EntityBondManager.get(member);
            check(state != null, "bonded plastic member had no state to persist");
            member.setData(
                PlasticraftAttachments.ENTITY_BONDS, state.withResolvedEntityIds(
                -1,
                state.links().stream().map(link -> link.withOtherEntityId(-1)).toList()
            ));
        }
        for (AbstractPlasticEntity member : component) EntityBondManager.tick(member);
        for (AbstractPlasticEntity member : component) {
            EntityBondState state = EntityBondManager.get(member);
            check(
                state != null && state.leaderEntityId() == leader.getId(),
                "persisted plastic bond did not restore its leader runtime id"
            );
            for (EntityBondLink link : state.links()) {
                Entity linked = EntityBondManager.resolve(helper.getLevel(), link);
                check(
                    linked != null && link.otherEntityId() == linked.getId(),
                    "persisted plastic bond did not restore a linked runtime id"
                );
            }
        }

        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.5D, 2.0D, 2.70D));
        player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z);
        helper.runAfterDelay(3, () -> {
            double playerStart = player.getZ();
            double newestStart = newestFollower.getZ();
            double middleStart = middleFollower.getZ();
            double leaderStart = leader.getZ();
            player.move(MoverType.SELF, new Vec3(0.0D, 0.0D, 0.18D));
            player.move(MoverType.SELF, new Vec3(0.0D, 0.0D, 0.18D));

            double playerMovement = player.getZ() - playerStart;
            double newestMovement = newestFollower.getZ() - newestStart;
            double middleMovement = middleFollower.getZ() - middleStart;
            double leaderMovement = leader.getZ() - leaderStart;
            check(playerMovement > 0.30D, "bonded follower blocked the player's continuous movement");
            check(newestMovement > 0.30D, "newest follower did not receive both player movements");
            check(middleMovement > 0.30D, "middle follower did not move synchronously");
            check(leaderMovement > 0.30D, "bonded leader did not receive both player movements");
            check(
                Math.abs(newestMovement - middleMovement) < 0.03D
                    && Math.abs(newestMovement - leaderMovement) < 0.03D,
                "bonded plastic component did not preserve spacing while its follower was pushed: newest="
                    + newestMovement + ", middle=" + middleMovement + ", leader=" + leaderMovement
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "16x6x9", floor = true)
    @TestHolder(description = "A bonded plastic component pushes an independent plastic entity through its follower")
    static void bondedPlasticFollowerPushesIndependentPlasticEntity(ExtendedGameTestHelper helper) {
        for (int x = 2; x <= 13; x++) {
            for (int z = 2; z <= 6; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        }
        HardenedResinCauldronEntity follower = createCauldron(helper, new Vec3(5.5D, 2.0D, 4.5D));
        HardenedResinCauldronEntity leader = createCauldron(helper, new Vec3(6.5D, 2.0D, 4.5D));
        HardenedResinCauldronEntity independent = createCauldron(helper, new Vec3(7.5D, 2.0D, 4.5D));
        check(EntityBondManager.connect(
            helper.getLevel(), follower, Direction.EAST, leader, Direction.WEST, false
        ), "plastic follower could not bond to its leader");
        check(EntityBondManager.isFollower(follower), "pushed plastic entity was not the follower");

        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.70D, 2.0D, 4.5D));
        player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z);
        helper.runAfterDelay(3, () -> {
            double playerStart = player.getX();
            double followerStart = follower.getX();
            double leaderStart = leader.getX();
            double independentStart = independent.getX();
            player.move(MoverType.SELF, new Vec3(0.18D, 0.0D, 0.0D));
            player.move(MoverType.SELF, new Vec3(0.18D, 0.0D, 0.0D));

            double followerMovement = follower.getX() - followerStart;
            double leaderMovement = leader.getX() - leaderStart;
            double independentMovement = independent.getX() - independentStart;
            check(player.getX() - playerStart > 0.30D, "independent plastic entity blocked the player through the bonded group");
            check(followerMovement > 0.30D, "bonded follower did not receive both player movements");
            check(leaderMovement > 0.30D, "bonded leader did not follow its pushed follower");
            check(independentMovement > 0.30D, "bonded component did not push the independent plastic entity");
            check(
                Math.abs(followerMovement - leaderMovement) < 0.03D,
                "bonded component changed spacing while pushing another plastic entity"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "10x8x8", floor = true)
    @TestHolder(description = "A bonded plastic follower on a player's head carries its whole component")
    static void playerCarriesBondedPlasticFollowerOnHead(ExtendedGameTestHelper helper) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 playerPosition = helper.absoluteVec(new Vec3(4.5D, 2.0D, 3.5D));
        player.moveTo(playerPosition.x, playerPosition.y, playerPosition.z);
        double productY = 2.0D + player.getBbHeight() - 3.0D / 16.0D;
        HardenedResinCauldronEntity follower = createCauldron(helper, new Vec3(4.5D, productY, 3.5D));
        HardenedResinCauldronEntity leader = createCauldron(helper, new Vec3(5.5D, productY, 3.5D));
        follower.setNoGravity(true);
        leader.setNoGravity(true);
        check(EntityBondManager.connect(
            helper.getLevel(), follower, Direction.EAST, leader, Direction.WEST, true
        ), "head-carried plastic follower could not bond to its leader");
        check(EntityBondManager.isFollower(follower), "head-carried plastic entity was not the follower");

        helper.runAfterDelay(3, () -> {
            check(
                PlasticEntityPhysics.hasImmediateEntityContact(follower, player, Direction.DOWN),
                "bonded follower did not settle on the player's head"
            );
            double playerStart = player.getZ();
            double followerStart = follower.getZ();
            double leaderStart = leader.getZ();
            Vec3 requestedMovement = new Vec3(0.0D, 0.0D, 0.18D);
            check(EntityBondManager.isFollower(follower), "head-carried plastic entity stopped being the follower");
            check(
                PlasticEntityPhysics.hasImmediateEntityContact(follower, player, Direction.DOWN),
                "bonded follower did not recognize direct contact with its head carrier"
            );
            check(
                follower.plasticraft$canMoveWithCarrier(player, requestedMovement),
                "bonded follower rejected its directly contacting head carrier"
            );
            player.move(MoverType.SELF, requestedMovement);
            player.move(MoverType.SELF, requestedMovement);

            double playerMovement = player.getZ() - playerStart;
            double followerMovement = follower.getZ() - followerStart;
            double leaderMovement = leader.getZ() - leaderStart;
            check(
                playerMovement > 0.30D,
                "bonded follower blocked its head carrier: player=" + playerMovement
                    + ", follower=" + followerMovement + ", leader=" + leaderMovement
            );
            check(followerMovement > 0.30D, "head-carried follower did not move with the player");
            check(leaderMovement > 0.30D, "head-carried follower did not move its leader");
            check(
                Math.abs(followerMovement - leaderMovement) < 0.03D,
                "head-carried bonded component did not preserve spacing"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "8x7x7", floor = true)
    @TestHolder(description = "Every bonded member clips movement after the original leader is removed")
    static void rebasedComponentCannotMoveFollowerThroughFloor(ExtendedGameTestHelper helper) {
        helper.setBlock(3, 0, 3, Blocks.STONE);
        ResinAnvilEntity floorMember = createResinAnvil(helper, new Vec3(3.5D, 1.0D, 3.5D));
        Zombie upperMember = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 1.98D, 3.5D));
        HardenedResinAnvilEntity removedLeader = createAnvil(helper, new Vec3(4.29D, 1.98D, 3.5D));
        floorMember.setNoGravity(true);
        upperMember.setNoGravity(true);
        removedLeader.setNoGravity(true);
        check(EntityBondManager.connect(
            helper.getLevel(), floorMember, Direction.UP, upperMember, Direction.DOWN, true
        ), "vertical component bond failed");
        check(EntityBondManager.connect(
            helper.getLevel(), upperMember, Direction.EAST, removedLeader, Direction.WEST, true
        ), "removable leader bond failed");

        removedLeader.discard();
        EntityBondManager.tick(upperMember);
        EntityBondState rebased = EntityBondManager.get(upperMember);
        check(
            rebased != null && rebased.leaderUuid().equals(upperMember.getUUID()),
            "remaining component did not select the expected new leader"
        );
        Vec3 upperStart = upperMember.position();
        Vec3 floorStart = floorMember.position();
        Vec3 requested = new Vec3(0.0D, -0.75D, 0.0D);
        Vec3 groupClipped = EntityBondManager.clampLeaderMovement(upperMember, requested);
        check(
            groupClipped.lengthSqr() <= EPSILON * EPSILON,
            "grounded member did not clip group movement: clipped=" + groupClipped
                + ", floorBox=" + floorMember.getBoundingBox()
                + ", block=" + helper.getBlockState(BlockPos.containing(floorMember.position()).below())
        );
        upperMember.move(MoverType.SELF, requested);
        EntityBondManager.tick(upperMember);

        check(
            close(upperMember.position(), upperStart),
            "new leader moved despite a grounded follower: start=" + upperStart
                + ", current=" + upperMember.position()
                + ", clipped=" + groupClipped
        );
        check(close(floorMember.position(), floorStart), "grounded follower was moved through the floor");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 35)
    @EmptyTemplate(value = "10x7x8", floor = true)
    @TestHolder(description = "A plastic entity bonded to an ordinary entity can still blockify")
    static void plasticEntityInMixedGroupBlockifiesAndDisconnects(ExtendedGameTestHelper helper) {
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 3.0D, 3.5D));
        Zombie passenger = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 3.0D, 3.5D));
        anvil.setNoGravity(true);
        passenger.setNoGravity(true);
        check(EntityBondManager.connect(
            helper.getLevel(), passenger, Direction.WEST, anvil, Direction.EAST, true
        ), "mixed entity bond failed");

        BlockPos support = new BlockPos(8, 3, 3);
        BlockPos occupied = support.west();
        helper.setBlock(support, Blocks.STONE);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(5.5D, 3.0D, 3.5D));
        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, anvil, Direction.WEST),
            "mixed-group plastic selection failed"
        );
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(support),
            Direction.WEST
        ), "mixed-group plastic entity could not start bonding");

        helper.runAfterDelay(14, () -> {
            AdhesiveTransit remainingTransit = anvil.getExistingDataOrNull(
                PlasticraftAttachments.ADHESIVE_TRANSIT.get()
            );
            check(
                helper.getBlockState(occupied).is(PlasticraftBlocks.HARDEND_RESIN_ANVIL.get())
                    && helper.getBlockState(occupied).getValue(AbstractPlasticEntityBlock.BONDED),
                "mixed-group plastic entity did not blockify: state=" + helper.getBlockState(occupied)
                    + ", anvilPosition=" + anvil.position()
                    + ", anvilOrientation=" + anvil.getOrientation()
                    + ", anvilAlive=" + anvil.isAlive()
                    + ", transit=" + remainingTransit
                    + ", passengerPosition=" + passenger.position()
                    + ", passengerBonds=" + EntityBondManager.hasBonds(passenger)
            );
            check(bondedBlockEntity(helper, occupied).isInitialized(), "mixed-group plastic block was not initialized");
            check(!EntityBondManager.hasBonds(passenger), "ordinary entity remained bonded after plastic blockification");
            check(passenger.isAlive(), "ordinary entity was removed with the plastic entity");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "8x7x8", floor = true)
    @TestHolder(description = "A failed plastic blockification restores the entity's starting orientation")
    static void failedPlasticBlockificationRestoresOrientation(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(6, 3, 3);
        BlockPos occupied = support.west();
        helper.setBlock(support, Blocks.STONE);
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 3.0D, 3.5D));
        anvil.setNoGravity(true);
        PlasticEntityOrientation startingOrientation = anvil.getOrientation();
        GameTestPlayer player = bucketPlayer(helper, new Vec3(4.5D, 3.0D, 3.5D));
        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, anvil, Direction.WEST),
            "plastic selection for failed blockification failed"
        );
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(support),
            Direction.WEST
        ), "plastic entity could not start its blocked transit");
        helper.setBlock(occupied, Blocks.OBSIDIAN);

        helper.runAfterDelay(12, () -> {
            check(!anvil.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT), "failed transit was not canceled");
            check(anvil.getOrientation().equals(startingOrientation), "failed transit kept its target orientation");
            check(!anvil.isRemoved(), "failed transit removed the plastic entity");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 35)
    @EmptyTemplate(value = "10x7x7", floor = true)
    @TestHolder(description = "A block placed into a resin transit stops the entity before a block bond")
    static void adhesiveTransitStopsBeforeNewBlockBondObstacle(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(8, 1, 3);
        BlockPos blocker = new BlockPos(5, 2, 3);
        helper.setBlock(support, Blocks.STONE);
        Zombie moving = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 3.5D));
        moving.setNoGravity(true);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(4.5D, 2.0D, 1.5D));

        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, moving, Direction.EAST),
            "block-transit obstacle source could not be selected"
        );
        Vec3 supportPlayerPosition = helper.absoluteVec(new Vec3(7.5D, 2.0D, 1.5D));
        player.moveTo(supportPlayerPosition.x, supportPlayerPosition.y, supportPlayerPosition.z);
        check(
            AdhesiveBondingService.bondSelected(
                player,
                InteractionHand.MAIN_HAND,
                helper.absolutePos(support),
                Direction.UP
            ),
            "block-transit obstacle setup could not start"
        );

        helper.runAfterDelay(2, () -> {
            helper.setBlock(blocker, Blocks.OBSIDIAN);
            helper.runAfterDelay(14, () -> {
                BlockPos absoluteBlocker = helper.absolutePos(blocker);
                check(!moving.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT), "blocked block transit did not stop");
                check(!moving.hasData(PlasticraftAttachments.ENTITY_ADHESION), "blocked block transit still attached");
                check(
                    moving.getBoundingBox().maxX <= absoluteBlocker.getX() + 0.002D,
                    "blocked block transit crossed the obstacle"
                );
                check(
                    !moving.getBoundingBox().intersects(new AABB(absoluteBlocker)),
                    "blocked block transit stopped inside the obstacle"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks = 35)
    @EmptyTemplate(value = "10x7x7", floor = true)
    @TestHolder(description = "A block placed into a resin transit stops the entity before an entity bond")
    static void adhesiveTransitStopsBeforeNewEntityBondObstacle(ExtendedGameTestHelper helper) {
        BlockPos blocker = new BlockPos(5, 2, 3);
        Zombie moving = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 3.5D));
        Zombie support = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(8.5D, 2.0D, 3.5D));
        moving.setNoGravity(true);
        support.setNoGravity(true);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(4.5D, 2.0D, 1.5D));

        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, moving, Direction.EAST),
            "entity-transit obstacle source could not be selected"
        );
        Vec3 supportPlayerPosition = helper.absoluteVec(new Vec3(7.5D, 2.0D, 1.5D));
        player.moveTo(supportPlayerPosition.x, supportPlayerPosition.y, supportPlayerPosition.z);
        check(
            AdhesiveBondingService.bondSelectedToEntity(
                player,
                InteractionHand.MAIN_HAND,
                support,
                Direction.WEST
            ),
            "entity-transit obstacle setup could not start"
        );

        helper.runAfterDelay(2, () -> {
            helper.setBlock(blocker, Blocks.OBSIDIAN);
            helper.runAfterDelay(14, () -> {
                BlockPos absoluteBlocker = helper.absolutePos(blocker);
                check(!moving.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT), "blocked entity transit did not stop");
                check(!EntityBondManager.hasBonds(moving), "blocked entity transit still attached");
                check(
                    moving.getBoundingBox().maxX <= absoluteBlocker.getX() + 0.002D,
                    "blocked entity transit crossed the obstacle"
                );
                check(
                    !moving.getBoundingBox().intersects(new AABB(absoluteBlocker)),
                    "blocked entity transit stopped inside the obstacle"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks = 45)
    @EmptyTemplate(value = "9x8x7", floor = true)
    @TestHolder(description = "Bonded falling blocks blockify together and lose mutual support after one breaks")
    static void fallingBlockPairBlockifiesWithMutualSupport(ExtendedGameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 4, 3);
        BlockPos targetPos = new BlockPos(5, 4, 3);
        helper.setBlock(sourcePos, Blocks.SAND);
        helper.setBlock(targetPos, Blocks.GRAVEL);
        FallingBlockEntity source = FallingBlockEntity.fall(
            helper.getLevel(),
            helper.absolutePos(sourcePos),
            Blocks.SAND.defaultBlockState()
        );
        FallingBlockEntity target = FallingBlockEntity.fall(
            helper.getLevel(),
            helper.absolutePos(targetPos),
            Blocks.GRAVEL.defaultBlockState()
        );
        source.setNoGravity(true);
        target.setNoGravity(true);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 3.0D, 1.5D));

        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, source), "sand selection failed");
        check(AdhesiveBondingService.bondSelectedToEntity(
            player,
            InteractionHand.MAIN_HAND,
            target,
            Direction.WEST
        ), "falling-block pair bonding failed");

        BlockPos bondedSource = targetPos.west();
        helper.runAfterDelay(14, () -> {
            check(helper.getBlockState(bondedSource).is(Blocks.SAND), "source falling block did not blockify");
            check(helper.getBlockState(targetPos).is(Blocks.GRAVEL), "target falling block did not blockify");
            BondedFallingBlockInfo sourceInfo = BondedFallingBlocks.get(
                helper.getLevel(),
                helper.absolutePos(bondedSource)
            );
            BondedFallingBlockInfo targetInfo = BondedFallingBlocks.get(
                helper.getLevel(),
                helper.absolutePos(targetPos)
            );
            check(
                sourceInfo != null && sourceInfo.supportPos().equals(helper.absolutePos(targetPos)),
                "source falling block does not depend on its partner"
            );
            check(
                targetInfo != null && targetInfo.supportPos().equals(helper.absolutePos(bondedSource)),
                "target falling block does not depend on its partner"
            );
            helper.getLevel().destroyBlock(helper.absolutePos(targetPos), false, player);
            helper.runAfterDelay(4, () -> {
                check(helper.getBlockState(bondedSource).isAir(), "remaining falling block kept phantom support");
                check(
                    !helper.getLevel().getEntitiesOfClass(
                        FallingBlockEntity.class,
                        new AABB(helper.absolutePos(bondedSource)).inflate(2.0D)
                    ).isEmpty(),
                    "remaining falling block did not resume falling"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks = 55)
    @EmptyTemplate(value = "10x8x7", floor = true)
    @TestHolder(description = "A plastic entity group clears mutual adhesive when its support releases it")
    static void plasticEntityGroupClearsMutualAdhesiveWhenReleased(ExtendedGameTestHelper helper) {
        HardenedResinAnvilEntity source = createAnvil(helper, new Vec3(2.5D, 3.0D, 3.5D));
        HardenedResinAnvilEntity target = createAnvil(helper, new Vec3(5.5D, 3.0D, 3.5D));
        source.setNoGravity(true);
        target.setNoGravity(true);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 3.0D, 1.5D));

        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, source, Direction.EAST),
            "plastic group source selection failed"
        );
        check(AdhesiveBondingService.bondSelectedToEntity(
            player,
            InteractionHand.MAIN_HAND,
            target,
            Direction.WEST
        ), "plastic pair bonding failed");

        BlockPos support = new BlockPos(8, 3, 3);
        BlockPos rootBlock = support.west();
        BlockPos followerBlock = rootBlock.west();
        helper.setBlock(support, Blocks.STONE);
        helper.runAfterDelay(14, () -> {
            check(EntityBondManager.hasBonds(source), "plastic pair did not remain as bonded entities");
            check(EntityBondManager.hasBonds(target), "plastic target did not retain its entity bond");
            Vec3 nearSupport = helper.absoluteVec(new Vec3(7.0D, 3.0D, 1.5D));
            player.moveTo(nearSupport.x, nearSupport.y, nearSupport.z);
            player.setItemInHand(InteractionHand.MAIN_HAND, PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.asStack());
            check(
                AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, target, Direction.EAST),
                "plastic group anchor selection failed"
            );
            check(AdhesiveBondingService.bondSelected(
                player,
                InteractionHand.MAIN_HAND,
                helper.absolutePos(support),
                Direction.WEST
            ), "plastic group could not bond to a block");
            helper.runAfterDelay(14, () -> {
                check(
                    helper.getBlockState(rootBlock).is(PlasticraftBlocks.HARDEND_RESIN_ANVIL.get())
                        && helper.getBlockState(rootBlock).getValue(AbstractPlasticEntityBlock.BONDED),
                    "plastic group root did not blockify"
                );
                check(
                    helper.getBlockState(followerBlock).is(PlasticraftBlocks.HARDEND_RESIN_ANVIL.get())
                        && helper.getBlockState(followerBlock).getValue(AbstractPlasticEntityBlock.BONDED),
                    "plastic group follower did not blockify"
                );
                check(bondedBlockEntity(helper, rootBlock).isInitialized(), "root block entity was not initialized");
                check(
                    bondedBlockEntity(helper, followerBlock).isInitialized(),
                    "follower block entity was not initialized"
                );
                check(
                    BondedFallingBlocks.hasBlockBond(
                        helper.getLevel(),
                        helper.absolutePos(rootBlock),
                        Direction.WEST
                    ) && BondedFallingBlocks.hasBlockBond(
                        helper.getLevel(),
                        helper.absolutePos(followerBlock),
                        Direction.EAST
                    ),
                    "blockified group lost its mutual adhesive before release"
                );
                check(
                    helper.getLevel().destroyBlock(helper.absolutePos(support), false, player),
                    "external support could not be destroyed"
                );
                helper.runAfterDelay(4, () -> {
                    check(helper.getBlockState(rootBlock).isAir(), "root block did not release after support removal");
                    check(helper.getBlockState(followerBlock).isAir(), "follower block did not release after support removal");
                    check(
                        BondedFallingBlocks.getAdhesion(helper.getLevel(), helper.absolutePos(rootBlock)) == null,
                        "released root block retained adhesive data"
                    );
                    check(
                        BondedFallingBlocks.getAdhesion(helper.getLevel(), helper.absolutePos(followerBlock)) == null,
                        "released follower block retained adhesive data"
                    );
                    check(
                        helper.getLevel().getEntitiesOfClass(
                            HardenedResinAnvilEntity.class,
                            new AABB(helper.absolutePos(followerBlock)).inflate(3.0D)
                        ).size() >= 2,
                        "released plastic group did not restore both entities"
                    );
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "7x5x7", floor = true)
    @TestHolder(description = "Ordinary bonded entities remain fixed and release when support is removed")
    static void ordinaryEntityFreezesAndReleases(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(3, 1, 3);
        helper.setBlock(support, Blocks.STONE);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 2.5D));
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 1.5D));

        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, zombie), "entity selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(support),
            Direction.UP
        ), "ordinary entity bonding failed");
        check(zombie.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT), "ordinary entity did not enter adhesive transit");
        check(player.getMainHandItem().is(Items.BUCKET), "survival bonding did not return an empty bucket");

        helper.runAfterDelay(10, () -> {
            check(zombie.hasData(PlasticraftAttachments.ENTITY_ADHESION), "ordinary entity has no adhesion attachment");
            Vec3 fixedPosition = zombie.position();
            zombie.move(MoverType.SELF, new Vec3(1.0D, 0.0D, 0.0D));
            check(close(zombie.position(), fixedPosition), "bonded entity moved through the move entry point");
            helper.setBlock(support, Blocks.DIRT);
            helper.runAfterDelay(2, () -> {
                check(!zombie.hasData(PlasticraftAttachments.ENTITY_ADHESION), "replaced support did not release the entity");
                check(!zombie.isNoGravity(), "released entity did not restore its gravity flag");
                helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "7x5x7", floor = true)
    @TestHolder(description = "Teleporting an ordinary bonded entity breaks its adhesive")
    static void teleportReleasesBondedEntity(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(3, 1, 3);
        helper.setBlock(support, Blocks.STONE);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 2.5D));
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 1.5D));
        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, zombie), "entity selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(support),
            Direction.UP
        ), "ordinary entity bonding failed");

        helper.runAfterDelay(10, () -> {
            check(zombie.hasData(PlasticraftAttachments.ENTITY_ADHESION), "ordinary entity has no adhesion attachment");
            zombie.setPos(zombie.position().add(3.0D, 0.0D, 0.0D));
            AdhesiveBondingService.tickBondedEntity(zombie, true);
            check(!zombie.hasData(PlasticraftAttachments.ENTITY_ADHESION), "teleported entity remained bonded");
            check(!zombie.isNoGravity(), "teleported entity kept its bonded no-gravity flag");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "7x5x7", floor = true)
    @TestHolder(description = "Knockback I starts a short rebound without releasing a bonded living entity")
    static void knockbackThresholdStartsShortBondedEntityRebound(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(3, 1, 3);
        helper.setBlock(support, Blocks.STONE);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 2.5D));
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 1.5D));

        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, zombie), "entity selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(support),
            Direction.UP
        ), "ordinary entity bonding failed");
        helper.runAfterDelay(10, () -> {
            check(zombie.hasData(PlasticraftAttachments.ENTITY_ADHESION), "ordinary entity has no adhesion attachment");
            Vec3 fixedPosition = zombie.position();
            CommonHooks.onLivingKnockBack(zombie, 0.49F, 0.0D, 1.0D);
            check(
                !zombie.hasData(PlasticraftAttachments.ADHESIVE_ELASTIC_MOTION),
                "sub-threshold knockback started elastic motion"
            );
            CommonHooks.onLivingKnockBack(zombie, 0.5F, 0.0D, 1.0D);
            check(zombie.hasData(PlasticraftAttachments.ENTITY_ADHESION), "Knockback I released the entity");
            check(
                zombie.hasData(PlasticraftAttachments.ADHESIVE_ELASTIC_MOTION),
                "Knockback I did not start elastic motion"
            );
            zombie.setDeltaMovement(0.5D, 0.0D, 0.0D);
            helper.runAfterDelay(2, () -> {
                check(
                    zombie.position().distanceToSqr(fixedPosition) > 0.01D,
                    "elastic entity did not move away from its adhesive point"
                );
                helper.runAfterDelay(9, () -> {
                    check(zombie.hasData(PlasticraftAttachments.ENTITY_ADHESION), "elastic rebound released the entity");
                    check(
                        !zombie.hasData(PlasticraftAttachments.ADHESIVE_ELASTIC_MOTION),
                        "Knockback I rebound did not settle quickly"
                    );
                    check(close(zombie.position(), fixedPosition), "elastic entity did not return to its adhesive point");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "Plastic anvils become bonded blocks and restore their entity data")
    static void plasticEntityBlockifiesAndRestores(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(3, 2, 3);
        BlockPos occupied = support.east();
        helper.setBlock(support, Blocks.STONE);
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 2.0D, 2.5D));
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 1.5D));

        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, anvil), "plastic anvil selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(support),
            Direction.EAST
        ), "plastic anvil bonding failed");
        check(anvil.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT), "plastic anvil did not enter adhesive transit");
        helper.runAfterDelay(10, () -> {
            check(helper.getBlockState(occupied).is(PlasticraftBlocks.HARDEND_RESIN_ANVIL.get()), "wrong fixed block was placed");
            check(
                helper.getBlockState(occupied).getValue(AbstractPlasticEntityBlock.BONDED),
                "fixed plastic block is missing its bonded state"
            );
            check(
                helper.getBlockEntity(occupied) instanceof BondedEntityBlockEntity bonded
                    && bonded.isInitialized()
                    && bonded.isPlastic()
                    && bonded.getPlasticOrientation().attachmentFace() == Direction.EAST,
                "fixed plastic block entity lost its orientation data"
            );
            check(!anvil.isRemoved(), "plastic anvil did not retain its blockification handoff frame");
            check(
                !anvil.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT),
                "plastic anvil retained adhesive transit after the fixed block appeared"
            );

            helper.runAfterDelay(1, () -> {
                check(anvil.isRemoved(), "plastic anvil remained after its blockification handoff frame");
                helper.setBlock(support, Blocks.DIRT);
                helper.runAfterDelay(3, () -> {
                    check(helper.getBlockState(occupied).isAir(), "released plastic block was not removed");
                    HardenedResinAnvilEntity restored = helper.getLevel().getEntitiesOfClass(
                        HardenedResinAnvilEntity.class,
                        new AABB(helper.absolutePos(occupied)).inflate(1.0D)
                    ).stream().findFirst()
                        .orElseThrow(() -> new GameTestAssertException("plastic entity was not restored"));
                    check(
                        restored.getOrientation().attachmentFace() == Direction.EAST,
                        "restored plastic entity lost its bonded orientation"
                    );
                    check(
                        !restored.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT),
                        "restored plastic entity retained its completed adhesive transit"
                    );
                    check(!restored.isNoGravity(), "restored plastic entity kept the transit no-gravity flag");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "10x7x8", floor = true)
    @TestHolder(description = "Both bonded resin anvils use the Royal Anvil collision shape in six-axis orientations")
    static void bondedPlasticAnvilsUseRoyalAnvilCollision(ExtendedGameTestHelper helper) {
        BlockPos floorSupport = new BlockPos(2, 1, 2);
        BlockPos floorOccupied = floorSupport.above();
        BlockPos wallSupport = new BlockPos(6, 3, 3);
        BlockPos wallOccupied = wallSupport.east();
        helper.setBlock(floorSupport, Blocks.STONE);
        helper.setBlock(wallSupport, Blocks.STONE);
        HardenedResinAnvilEntity hardened = createAnvil(helper, new Vec3(2.5D, 5.0D, 2.5D));
        ResinAnvilEntity resin = createResinAnvil(helper, new Vec3(7.5D, 5.0D, 5.5D));
        GameTestPlayer player = bucketPlayer(helper, new Vec3(4.5D, 2.0D, 1.5D));

        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, hardened), "hardened anvil selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(floorSupport),
            Direction.UP
        ), "hardened anvil bonding failed");
        player.setItemInHand(InteractionHand.MAIN_HAND, PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.asStack());
        player.moveTo(helper.absoluteVec(new Vec3(7.5D, 3.0D, 4.5D)));
        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, resin), "resin anvil selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(wallSupport),
            Direction.EAST
        ), "resin anvil bonding failed");

        helper.runAfterDelay(14, () -> {
            VoxelShape floorShape = helper.getBlockState(floorOccupied).getCollisionShape(
                helper.getLevel(),
                helper.absolutePos(floorOccupied)
            );
            BlockState floorState = helper.getBlockState(floorOccupied);
            VoxelShape royalFloorShape = ModBlocks.ROYAL_ANVIL
                .getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, floorState.getValue(BlockStateProperties.HORIZONTAL_FACING))
                .getCollisionShape(helper.getLevel(), helper.absolutePos(floorOccupied));
            check(
                !Shapes.joinIsNotEmpty(floorShape, royalFloorShape, BooleanOp.NOT_SAME),
                "floor-bonded hardened anvil did not exactly match the Royal Anvil collision"
            );
            check(
                !Shapes.joinIsNotEmpty(
                    floorShape,
                    Shapes.box(0.0D, 0.0D, 0.0D, 0.1D, 0.2D, 0.1D),
                    BooleanOp.AND
                ),
                "floor-bonded hardened anvil retained a cubic collision corner"
            );
            check(
                Shapes.joinIsNotEmpty(
                    floorShape,
                    Shapes.box(0.2D, 0.0D, 0.2D, 0.8D, 0.2D, 0.8D),
                    BooleanOp.AND
                ),
                "floor-bonded hardened anvil lost the Royal Anvil base"
            );

            VoxelShape wallShape = helper.getBlockState(wallOccupied).getCollisionShape(
                helper.getLevel(),
                helper.absolutePos(wallOccupied)
            );
            check(
                !Shapes.joinIsNotEmpty(
                    wallShape,
                    Shapes.box(0.0D, 0.0D, 0.0D, 0.1D, 0.1D, 0.2D),
                    BooleanOp.AND
                ),
                "wall-bonded resin anvil retained a cubic collision corner"
            );
            check(
                Shapes.joinIsNotEmpty(
                    wallShape,
                    Shapes.box(0.0D, 0.2D, 0.2D, 0.2D, 0.8D, 0.8D),
                    BooleanOp.AND
                ),
                "wall-bonded resin anvil did not rotate the Royal Anvil base"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "Bonded hardened resin anvils retain their anvil menu")
    static void bondedHardenedAnvilOpensMenu(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(3, 1, 3);
        BlockPos occupied = support.above();
        helper.setBlock(support, Blocks.STONE);
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 2.0D, 2.5D));
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 1.5D));

        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, anvil), "plastic anvil selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(support),
            Direction.UP
        ), "plastic anvil bonding failed");
        helper.runAfterDelay(10, () -> {
            BondedEntityBlockEntity bonded = bondedBlockEntity(helper, occupied);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            check(
                helper.getBlockState(occupied).useWithoutItem(
                    helper.getLevel(),
                    player,
                    blockHit(helper, occupied)
                ).consumesAction(),
                "fixed hardened resin anvil interaction was not consumed"
            );
            check(
                player.containerMenu instanceof HardenedResinAnvilMenu,
                "fixed hardened resin anvil did not open its custom menu"
            );
            player.closeContainer();

            player.setShiftKeyDown(true);
            check(
                !helper.getBlockState(occupied).useWithoutItem(
                    helper.getLevel(),
                    player,
                    blockHit(helper, occupied)
                ).consumesAction(),
                "shift-clicking a fixed hardened resin anvil unexpectedly opened its menu"
            );
            check(
                !(player.containerMenu instanceof HardenedResinAnvilMenu),
                "shift-clicking left the fixed hardened resin anvil menu open"
            );
            player.setItemInHand(InteractionHand.MAIN_HAND, PlasticraftItems.RESIN_ANVIL_HAMMER.asStack());
            check(
                !helper.getBlockState(occupied).useItemOn(
                    player.getMainHandItem(),
                    helper.getLevel(),
                    player,
                    InteractionHand.MAIN_HAND,
                    blockHit(helper, occupied)
                ).consumesAction(),
                "shift-clicking a fixed anvil with a hammer invoked the cached entity"
            );
            check(
                player.getInventory().countItem(PlasticraftBlocks.HARDEND_RESIN_ANVIL.asItem()) == 0,
                "shift-clicking the cached entity duplicated the fixed anvil item"
            );
            bondedBlockEntity(helper, occupied);
            player.setShiftKeyDown(false);
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 35)
    @EmptyTemplate(value = "8x7x8", floor = true)
    @TestHolder(description = "A wall-bonded hardened resin anvil keeps its real block interaction entry")
    static void wallBondedHardenedAnvilOpensMenuThroughBlock(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(3, 3, 3);
        BlockPos occupied = support.east();
        helper.setBlock(support, Blocks.STONE);
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(5.5D, 3.0D, 3.5D));
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 3.0D, 1.5D));

        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, anvil), "wall anvil selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(support),
            Direction.EAST
        ), "wall anvil bonding failed");

        helper.runAfterDelay(12, () -> {
            check(
                helper.getBlockState(occupied).getValue(AbstractPlasticEntityBlock.BONDED),
                "wall anvil did not become a bonded block"
            );
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            check(
                helper.getBlockState(occupied).useWithoutItem(
                    helper.getLevel(),
                    player,
                    blockHit(helper, occupied, Direction.EAST)
                ).consumesAction(),
                "wall-bonded anvil rejected the real block interaction"
            );
            check(
                player.containerMenu instanceof HardenedResinAnvilMenu,
                "wall-bonded hardened resin anvil did not open its custom menu"
            );
            player.closeContainer();
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 70)
    @EmptyTemplate(value = "7x9x7", floor = true)
    @TestHolder(description = "Bonded resin cauldrons retain fluid, item, and impact recipe interactions")
    static void bondedCauldronRetainsFunctionalInteractions(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(3, 1, 3);
        BlockPos occupied = support.above();
        helper.setBlock(support, Blocks.STONE);
        HardenedResinCauldronEntity cauldron = createCauldron(helper, new Vec3(2.5D, 2.0D, 2.5D));
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 1.5D));

        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, cauldron), "plastic pot selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(support),
            Direction.UP
        ), "plastic pot bonding failed");

        helper.runAfterDelay(10, () -> {
            BondedEntityBlockEntity bonded = bondedBlockEntity(helper, occupied);
            check(
                bonded.getOrCreateRenderEntity() instanceof HardenedResinCauldronEntity fixedCauldron,
                "fixed plastic pot did not restore its functional entity"
            );
            HardenedResinCauldronEntity fixedCauldron = (HardenedResinCauldronEntity) bonded.getOrCreateRenderEntity();

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            check(
                helper.getBlockState(occupied).useItemOn(
                    player.getMainHandItem(),
                    helper.getLevel(),
                    player,
                    InteractionHand.MAIN_HAND,
                    blockHit(helper, occupied)
                ).consumesAction(),
                "fixed plastic pot rejected a water bucket"
            );
            check(player.getMainHandItem().is(Items.BUCKET), "water transfer did not return an empty bucket");
            check(
                fixedCauldron.getFluidHandler().getFluid().is(Fluids.WATER)
                    && fixedCauldron.getFluidHandler().getFluidAmount() == HardenedResinCauldronEntity.CAPACITY,
                "fixed plastic pot did not retain the transferred water"
            );
            check(
                fixedCauldron.getFluidHandler().drain(
                    HardenedResinCauldronEntity.CAPACITY,
                    IFluidHandler.FluidAction.EXECUTE
                ).getAmount() == HardenedResinCauldronEntity.CAPACITY,
                "fixed plastic pot did not drain through its fluid capability"
            );

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            check(
                helper.getBlockState(occupied).useItemOn(
                    player.getMainHandItem(),
                    helper.getLevel(),
                    player,
                    InteractionHand.MAIN_HAND,
                    blockHit(helper, occupied)
                ).consumesAction(),
                "fixed plastic pot rejected an item"
            );
            check(player.getMainHandItem().isEmpty(), "fixed plastic pot did not consume the inserted item");
            check(countItem(fixedCauldron, Items.STICK) == 1, "inserted item was not stored in the fixed pot");

            HardenedResinAnvilEntity fallingAnvil = createAnvil(helper, new Vec3(3.5D, 6.0D, 3.5D));
            helper.runAfterDelay(45, () -> {
                check(countItem(fixedCauldron, Items.DIAMOND) == 1, "fixed pot did not retain the recipe output");
                check(fallingAnvil.isAlive(), "ordinary fixed-pot processing consumed the plastic anvil");
                helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks = 45)
    @EmptyTemplate(value = "10x8x8", floor = true)
    @TestHolder(description = "Bonded resin cauldrons keep a hollow collision shape in every attachment direction")
    static void bondedCauldronUsesRotatedHollowCollision(ExtendedGameTestHelper helper) {
        BlockPos floorSupport = new BlockPos(2, 1, 2);
        BlockPos floorCauldron = floorSupport.above();
        BlockPos wallSupport = new BlockPos(6, 3, 3);
        BlockPos wallCauldron = wallSupport.east();
        helper.setBlock(floorSupport, Blocks.STONE);
        helper.setBlock(wallSupport, Blocks.STONE);
        HardenedResinCauldronEntity floorEntity = createCauldron(helper, new Vec3(2.5D, 5.0D, 2.5D));
        HardenedResinCauldronEntity wallEntity = createCauldron(helper, new Vec3(7.5D, 5.0D, 5.5D));
        GameTestPlayer player = bucketPlayer(helper, new Vec3(4.5D, 2.0D, 1.5D));

        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, floorEntity), "floor pot selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(floorSupport),
            Direction.UP
        ), "floor pot bonding failed");
        player.setItemInHand(InteractionHand.MAIN_HAND, PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.asStack());
        Vec3 wallPlayerPosition = helper.absoluteVec(new Vec3(7.5D, 3.0D, 4.5D));
        player.moveTo(wallPlayerPosition.x, wallPlayerPosition.y, wallPlayerPosition.z);
        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, wallEntity), "wall pot selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(wallSupport),
            Direction.EAST
        ), "wall pot bonding failed");

        helper.runAfterDelay(14, () -> {
            VoxelShape floorShape = helper.getBlockState(floorCauldron).getCollisionShape(
                helper.getLevel(),
                helper.absolutePos(floorCauldron)
            );
            check(
                !Shapes.joinIsNotEmpty(
                    floorShape,
                    Shapes.box(0.25D, 0.34D, 0.25D, 0.75D, 0.95D, 0.75D),
                    BooleanOp.AND
                ),
                "floor-bonded pot collision filled its interior"
            );
            check(
                helper.getBlockState(floorCauldron).isFaceSturdy(
                    helper.getLevel(),
                    helper.absolutePos(floorCauldron),
                    Direction.UP
                ),
                "floor-bonded pot no longer supports a falling anvil"
            );
            Vec3 standingPosition = Vec3.atLowerCornerOf(helper.absolutePos(floorCauldron))
                .add(0.5D, 0.34D, 0.5D);
            player.moveTo(standingPosition.x, standingPosition.y, standingPosition.z);
            check(helper.getLevel().noCollision(player), "player could not stand inside the floor-bonded pot");

            VoxelShape wallShape = helper.getBlockState(wallCauldron).getCollisionShape(
                helper.getLevel(),
                helper.absolutePos(wallCauldron)
            );
            check(
                !Shapes.joinIsNotEmpty(
                    wallShape,
                    Shapes.box(0.34D, 0.25D, 0.25D, 0.95D, 0.75D, 0.75D),
                    BooleanOp.AND
                ),
                "wall-bonded pot collision did not rotate its hollow interior"
            );
            check(
                helper.getBlockState(wallCauldron).isFaceSturdy(
                    helper.getLevel(),
                    helper.absolutePos(wallCauldron),
                    Direction.EAST
                ),
                "wall-bonded pot support face did not rotate with its opening"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 70)
    @EmptyTemplate(value = "7x10x7", floor = true)
    @TestHolder(description = "A vanilla falling anvil lands intact on a bonded hardened resin cauldron")
    static void fallingAnvilLandsOnBondedCauldron(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(3, 1, 3);
        BlockPos cauldronPos = support.above();
        BlockPos landedAnvilPos = cauldronPos.above();
        helper.setBlock(support, Blocks.STONE);
        HardenedResinCauldronEntity cauldron = createCauldron(helper, new Vec3(2.5D, 2.0D, 2.5D));
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 1.5D));
        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, cauldron), "plastic pot selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(support),
            Direction.UP
        ), "plastic pot bonding failed");

        helper.runAfterDelay(12, () -> {
            BlockPos source = new BlockPos(3, 7, 3);
            helper.setBlock(source, Blocks.ANVIL);
            FallingBlockEntity.fall(
                helper.getLevel(),
                helper.absolutePos(source),
                Blocks.ANVIL.defaultBlockState()
            );
            helper.runAfterDelay(38, () -> {
                check(
                    helper.getBlockState(landedAnvilPos).is(BlockTags.ANVIL),
                    "falling anvil shattered instead of landing on the bonded pot"
                );
                check(
                    helper.getBlockState(cauldronPos).is(PlasticraftBlocks.HARDEND_RESIN_CAULDRON.get())
                        && helper.getBlockState(cauldronPos).getValue(AbstractPlasticEntityBlock.BONDED),
                    "falling anvil destroyed the bonded pot"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "Bonded vanilla falling anvils retain their unpushable property")
    static void fallingAnvilBlockifiesAsUnpushable(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(3, 1, 3);
        BlockPos occupied = support.above();
        helper.setBlock(support, Blocks.STONE);
        BlockPos source = new BlockPos(2, 3, 2);
        helper.setBlock(source, Blocks.ANVIL);
        FallingBlockEntity anvil = FallingBlockEntity.fall(
            helper.getLevel(),
            helper.absolutePos(source),
            Blocks.ANVIL.defaultBlockState()
        );
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 1.0D, 1.5D));

        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, anvil), "falling anvil selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(support),
            Direction.UP
        ), "falling anvil bonding failed");
        helper.runAfterDelay(10, () -> {
            check(helper.getBlockState(occupied).is(Blocks.ANVIL), "falling anvil did not restore its block state");
            BondedFallingBlockInfo bonded = BondedFallingBlocks.get(
                helper.getLevel(),
                helper.absolutePos(occupied)
            );
            check(
                bonded != null && !bonded.pistonMovable(),
                "bonded vanilla anvil became piston-pushable"
            );
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            check(
                helper.getBlockState(occupied).useWithoutItem(
                    helper.getLevel(),
                    player,
                    blockHit(helper, occupied)
                ).consumesAction(),
                "bonded vanilla anvil rejected its normal interaction"
            );
            check(player.containerMenu instanceof AnvilMenu, "bonded vanilla anvil did not open its anvil menu");
            player.closeContainer();
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 35)
    @EmptyTemplate(value = "8x8x7", floor = true)
    @TestHolder(description = "A wall-bonded falling block keeps its real state and falls after support removal")
    static void bondedFallingSandReleasesFromWall(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(3, 4, 3);
        BlockPos occupied = support.east();
        BlockPos source = new BlockPos(2, 6, 3);
        helper.setBlock(support, Blocks.STONE);
        helper.setBlock(source, Blocks.SAND);
        FallingBlockEntity sand = FallingBlockEntity.fall(
            helper.getLevel(),
            helper.absolutePos(source),
            Blocks.SAND.defaultBlockState()
        );
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 4.0D, 1.5D));

        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, sand), "falling sand selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(support),
            Direction.EAST
        ), "falling sand bonding failed");

        helper.runAfterDelay(12, () -> {
            check(helper.getBlockState(occupied).is(Blocks.SAND), "bonded falling sand was not restored as sand");
            check(
                helper.getLevel().getBlockEntity(helper.absolutePos(occupied)) == null,
                "bonded falling sand still used a carrier block entity"
            );
            check(
                BondedFallingBlocks.isBonded(helper.getLevel(), helper.absolutePos(occupied)),
                "bonded falling sand lost its support metadata"
            );
            check(
                helper.getLevel().destroyBlock(helper.absolutePos(support), false, player),
                "support block could not be destroyed in the falling-block test"
            );
            // The released entity is already falling at this point; check soon
            // after the scheduled block tick before it can leave the test area.
            helper.runAfterDelay(3, () -> {
                check(helper.getBlockState(occupied).isAir(), "released falling sand remained fixed to the wall");
                check(
                    !helper.getLevel().getEntitiesOfClass(
                        FallingBlockEntity.class,
                        new AABB(helper.absolutePos(occupied)).inflate(2.0D)
                    ).isEmpty(),
                    "released falling sand did not resume falling"
                );
                helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks = 35)
    @EmptyTemplate(value = "8x8x7", floor = true)
    @TestHolder(description = "Changing a wall-bonded falling block state does not release its adhesive")
    static void wallBondedFallingAnvilKeepsBondAfterStateChange(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(3, 4, 3);
        BlockPos occupied = support.east();
        BlockPos source = new BlockPos(2, 6, 3);
        helper.setBlock(support, Blocks.STONE);
        helper.setBlock(source, Blocks.ANVIL);
        FallingBlockEntity anvil = FallingBlockEntity.fall(
            helper.getLevel(),
            helper.absolutePos(source),
            Blocks.ANVIL.defaultBlockState()
        );
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 4.0D, 1.5D));
        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, anvil), "falling anvil selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(support),
            Direction.EAST
        ), "wall falling-anvil bonding failed");

        helper.runAfterDelay(12, () -> {
            check(helper.getBlockState(occupied).is(Blocks.ANVIL), "falling anvil did not become a real block");
            helper.setBlock(
                occupied,
                helper.getBlockState(occupied).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
            );
            helper.runAfterDelay(5, () -> {
                BondedFallingBlockInfo bonded = BondedFallingBlocks.get(
                    helper.getLevel(),
                    helper.absolutePos(occupied)
                );
                check(bonded != null, "falling anvil state change removed its adhesive metadata");
                check(
                    bonded.blockState().equals(helper.getBlockState(occupied)),
                    "falling anvil state change was not saved in its adhesive metadata"
                );
                check(helper.getBlockState(occupied).is(Blocks.ANVIL), "wall-bonded anvil started falling after rotation");
                helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "10x7x7", floor = true)
    @TestHolder(description = "Adhesive path planning routes smoothly around blocking walls")
    static void adhesivePathRoutesAroundWall(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(7, 1, 3);
        helper.setBlock(support, Blocks.STONE);
        helper.setBlock(4, 2, 3, Blocks.STONE);
        helper.setBlock(4, 3, 3, Blocks.STONE);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 3.5D));
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        AdhesivePathPlanner.Plan plan = AdhesivePathPlanner.plan(
            helper.getLevel(),
            zombie,
            player,
            helper.absolutePos(support),
            Direction.UP
        );

        check(plan.valid(), "wall route was not found: " + plan.status());
        check(plan.points().size() > 3, "wall route did not retain a rounded detour");
        Vec3 start = zombie.position();
        check(
            plan.points().stream().anyMatch(point ->
                Math.abs(point.y - start.y) > 0.25D || Math.abs(point.z - start.z) > 0.25D),
            "wall route remained on the blocked direct line"
        );
        check(
            plan.points().subList(1, plan.points().size() - 1).stream()
                .anyMatch(AdhesiveBondingGameTests::hasCurvedCoordinate),
            "wall route contained only right-angle grid points"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 35)
    @EmptyTemplate(value = "10x7x7", floor = true)
    @TestHolder(description = "A rounded detour around a two-block wall completes the adhesive transit")
    static void adhesiveTransitFollowsRoundedWallDetour(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(7, 1, 3);
        helper.setBlock(support, Blocks.STONE);
        helper.setBlock(4, 2, 3, Blocks.GLASS);
        helper.setBlock(4, 3, 3, Blocks.GLASS);
        Zombie moving = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 3.5D));
        moving.setNoGravity(true);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 1.5D));

        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, moving, Direction.EAST),
            "rounded-detour source could not be selected"
        );
        Vec3 supportPlayerPosition = helper.absoluteVec(new Vec3(6.5D, 2.0D, 1.5D));
        player.moveTo(supportPlayerPosition.x, supportPlayerPosition.y, supportPlayerPosition.z);
        check(
            AdhesiveBondingService.bondSelected(
                player,
                InteractionHand.MAIN_HAND,
                helper.absolutePos(support),
                Direction.UP
            ),
            "rounded-detour transit could not start"
        );

        helper.runAfterDelay(24, () -> {
            check(!moving.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT), "rounded detour did not finish");
            check(moving.hasData(PlasticraftAttachments.ENTITY_ADHESION), "rounded detour stopped before bonding");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 50)
    @EmptyTemplate(value = "10x7x7", floor = true)
    @TestHolder(description = "Async adhesive preview reuses its detour for the confirmed transit")
    static void adhesiveAsyncPreviewReusesAuthoritativeDetour(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(7, 1, 3);
        helper.setBlock(support, Blocks.STONE);
        helper.setBlock(4, 2, 3, Blocks.GLASS);
        helper.setBlock(4, 3, 3, Blocks.GLASS);
        Zombie moving = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 3.5D));
        moving.setNoGravity(true);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 1.5D));
        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, moving, Direction.EAST),
            "async-preview source could not be selected"
        );
        player.moveTo(
            helper.absoluteVec(new Vec3(6.5D, 2.0D, 1.5D)).x,
            helper.absoluteVec(new Vec3(6.5D, 2.0D, 1.5D)).y,
            helper.absoluteVec(new Vec3(6.5D, 2.0D, 1.5D)).z
        );
        AdhesivePreviewService.requestBlock(
            player,
            1,
            moving.getId(),
            helper.absolutePos(support),
            Direction.UP
        );

        helper.runAfterDelay(1, () -> {
            check(
                AdhesivePreviewService.confirmBlock(
                    player,
                    InteractionHand.MAIN_HAND,
                    helper.absolutePos(support),
                    Direction.UP
                ),
                "async-preview confirmation was not queued"
            );
            int[] attempts = {0};
            Runnable[] poll = new Runnable[1];
            poll[0] = () -> {
                AdhesiveTransit transit = moving.getExistingDataOrNull(PlasticraftAttachments.ADHESIVE_TRANSIT.get());
                if (transit != null) {
                    check(
                        transit.path().size() > 3,
                        "confirmed transit lost the authoritative wall detour: " + transit.path()
                    );
                    check(
                        transit.path().stream().anyMatch(point ->
                            Math.abs(point.y - moving.position().y) > 0.25D
                                || Math.abs(point.z - moving.position().z) > 0.25D),
                        "confirmed transit stayed on the blocked direct line: " + transit.path()
                    );
                    helper.succeed();
                    return;
                }
                if (++attempts[0] >= 30) {
                    check(false, "async-preview confirmation never started transit");
                    return;
                }
                helper.runAfterDelay(1, poll[0]);
            };
            poll[0].run();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "10x7x7", floor = true)
    @TestHolder(description = "Async adhesive preview keeps the selection snapshot within two blocks of movement")
    static void adhesiveAsyncPreviewKeepsNearbySelectionSnapshot(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(7, 1, 3);
        helper.setBlock(support, Blocks.STONE);
        Zombie moving = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 3.5D));
        moving.setNoGravity(true);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 1.5D));
        Vec3 selectedPosition = moving.position();
        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, moving, Direction.EAST),
            "moving-preview source could not be selected"
        );
        Vec3 supportPlayerPosition = helper.absoluteVec(new Vec3(6.5D, 2.0D, 1.5D));
        player.moveTo(supportPlayerPosition.x, supportPlayerPosition.y, supportPlayerPosition.z);
        AdhesivePreviewService.requestBlock(
            player,
            2,
            moving.getId(),
            helper.absolutePos(support),
            Direction.UP
        );
        Vec3 displacedPosition = selectedPosition.add(1.0D, 0.0D, 0.0D);
        moving.setPos(displacedPosition);
        check(
            AdhesivePreviewService.confirmBlock(
                player,
                InteractionHand.MAIN_HAND,
                helper.absolutePos(support),
                Direction.UP
            ),
            "moving-preview confirmation was not queued"
        );

        awaitTransit(helper, moving, 15, transit -> {
            check(
                close(transit.path().getFirst(), selectedPosition),
                "nearby movement replaced the selection snapshot: " + transit.path()
            );
            check(
                !close(transit.path().getFirst(), displacedPosition),
                "nearby movement unexpectedly became the transit start"
            );
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "12x7x7", floor = true)
    @TestHolder(description = "Async adhesive preview refreshes after more than two blocks of movement")
    static void adhesiveAsyncPreviewRefreshesAfterMovementLimit(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(9, 1, 3);
        helper.setBlock(support, Blocks.STONE);
        Zombie moving = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 3.5D));
        moving.setNoGravity(true);
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 1.5D));
        Vec3 selectedPosition = moving.position();
        check(
            AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, moving, Direction.EAST),
            "refresh-preview source could not be selected"
        );
        Vec3 supportPlayerPosition = helper.absoluteVec(new Vec3(8.5D, 2.0D, 1.5D));
        player.moveTo(supportPlayerPosition.x, supportPlayerPosition.y, supportPlayerPosition.z);
        AdhesivePreviewService.requestBlock(
            player,
            3,
            moving.getId(),
            helper.absolutePos(support),
            Direction.UP
        );
        Vec3 displacedPosition = selectedPosition.add(2.25D, 0.0D, 0.0D);
        moving.setPos(displacedPosition);
        check(
            AdhesivePreviewService.confirmBlock(
                player,
                InteractionHand.MAIN_HAND,
                helper.absolutePos(support),
                Direction.UP
            ),
            "refresh-preview confirmation was not queued"
        );

        awaitTransit(helper, moving, 15, transit -> {
            check(
                close(transit.path().getFirst(), displacedPosition),
                "movement beyond two blocks did not refresh the transit start: " + transit.path()
            );
            check(
                !close(transit.path().getFirst(), selectedPosition),
                "expired selection snapshot was reused after excessive movement"
            );
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "18x11x15", floor = true)
    @TestHolder(description = "Adhesive path planning can spend its detour budget beyond the old four-block margin")
    static void adhesivePathRoutesBeyondOldSearchMargin(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(14, 1, 7);
        helper.setBlock(support, Blocks.STONE);
        for (int y = 1; y <= 8; y++) {
            for (int z = 2; z <= 12; z++) {
                helper.setBlock(new BlockPos(8, y, z), Blocks.STONE);
            }
        }
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 7.5D));
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        AdhesivePathPlanner.Plan plan = AdhesivePathPlanner.plan(
            helper.getLevel(),
            zombie,
            player,
            helper.absolutePos(support),
            Direction.UP
        );

        check(plan.valid(), "wide-wall route was not found: " + plan.status());
        Vec3 start = zombie.position();
        check(
            plan.points().stream().anyMatch(point ->
                Math.abs(point.y - start.y) > 4.25D || Math.abs(point.z - start.z) > 4.25D),
            "wide-wall route never left the old four-block search margin"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "13x7x11", floor = true)
    @TestHolder(description = "Adhesive paths avoid lava and fire even when the direct route is collision-free")
    static void adhesivePathAvoidsDamagingTerrain(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(10, 1, 5);
        BlockPos lava = new BlockPos(5, 2, 5);
        BlockPos fire = new BlockPos(6, 2, 5);
        helper.setBlock(support, Blocks.STONE);
        helper.setBlock(lava, Blocks.LAVA);
        helper.setBlock(fire.below(), Blocks.NETHERRACK);
        helper.setBlock(fire, Blocks.FIRE);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 5.5D));
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        AdhesivePathPlanner.Plan plan = AdhesivePathPlanner.plan(
            helper.getLevel(),
            zombie,
            player,
            helper.absolutePos(support),
            Direction.UP
        );

        check(plan.valid(), "safe route around damaging terrain was not found: " + plan.status());
        check(plan.points().size() > 2, "damaging terrain did not force a detour");
        AABB lavaBox = new AABB(helper.absolutePos(lava));
        AABB fireBox = new AABB(helper.absolutePos(fire));
        AABB originalBox = zombie.getBoundingBox().deflate(EPSILON);
        for (int index = 1; index < plan.points().size(); index++) {
            Vec3 from = plan.points().get(index - 1);
            Vec3 to = plan.points().get(index);
            int samples = Math.max(1, (int) Math.ceil(from.distanceTo(to) / 0.05D));
            for (int sample = 1; sample <= samples; sample++) {
                Vec3 position = from.lerp(to, sample / (double) samples);
                AABB moved = originalBox.move(position.subtract(zombie.position()));
                check(!moved.intersects(lavaBox), "smoothed adhesive path crossed lava");
                check(!moved.intersects(fireBox), "smoothed adhesive path crossed fire");
            }
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "12x7x30", floor = true)
    @TestHolder(description = "Axis-swept adhesive path checks allow a full-size falling block through a one-block gap")
    static void adhesivePathUsesOneBlockGap(ExtendedGameTestHelper helper) {
        BlockPos source = new BlockPos(2, 2, 14);
        BlockPos support = new BlockPos(9, 1, 14);
        helper.setBlock(source, Blocks.SAND);
        helper.setBlock(support, Blocks.STONE);
        for (int y = 1; y <= 5; y++) {
            for (int z = 1; z <= 27; z++) {
                if (y == 2 && z == 15) continue;
                helper.setBlock(new BlockPos(5, y, z), Blocks.STONE);
            }
        }
        FallingBlockEntity sand = FallingBlockEntity.fall(
            helper.getLevel(),
            helper.absolutePos(source),
            Blocks.SAND.defaultBlockState()
        );
        sand.setNoGravity(true);
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        AdhesivePathPlanner.Plan plan = AdhesivePathPlanner.plan(
            helper.getLevel(),
            sand,
            player,
            helper.absolutePos(support),
            Direction.UP
        );

        check(plan.valid(), "one-block-gap route was not found: " + plan.status());
        check(
            plan.points().stream().anyMatch(point -> point.z - sand.position().z > 0.75D),
            "one-block-gap route did not enter the narrow channel"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "12x6x9", floor = true)
    @TestHolder(description = "Exact one-block plastic entities fit into a one-block slot in every orientation")
    static void adhesivePathFitsExactBlockEntityInOneBlockSlot(ExtendedGameTestHelper helper) {
        BlockPos targetCell = new BlockPos(8, 2, 4);
        BlockPos floorSupport = targetCell.below();
        BlockPos wallSupport = targetCell.east();
        helper.setBlock(floorSupport, Blocks.STONE);
        helper.setBlock(wallSupport, Blocks.STONE);
        helper.setBlock(targetCell.above(), Blocks.STONE);
        HardenedResinCauldronEntity cauldron = createCauldron(helper, new Vec3(2.5D, 2.0D, 4.5D));
        check(
            Math.abs(cauldron.getBbWidth() - 1.0D) < EPSILON
                && Math.abs(cauldron.getBbHeight() - 1.0D) < EPSILON,
            "exact-block path test entity no longer has a one-block collision box"
        );
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);

        AdhesivePathPlanner.Plan floorPlan = AdhesivePathPlanner.plan(
            helper.getLevel(),
            cauldron,
            player,
            helper.absolutePos(floorSupport),
            Direction.UP
        );
        check(floorPlan.valid(), "exact-block floor-slot route was not found: " + floorPlan.status());

        AdhesivePathPlanner.Plan wallPlan = AdhesivePathPlanner.plan(
            helper.getLevel(),
            cauldron,
            player,
            helper.absolutePos(wallSupport),
            Direction.WEST
        );
        check(wallPlan.valid(), "exact-block wall-slot route was not found: " + wallPlan.status());
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "14x8x12", floor = true)
    @TestHolder(description = "Adhesive paths can leave one wall face and reach the opposite face by going around")
    static void adhesivePathRoutesFromWallToOppositeFace(ExtendedGameTestHelper helper) {
        BlockPos targetWall = new BlockPos(6, 3, 5);
        for (int x = 2; x <= 10; x++) {
            for (int y = 1; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, 5), Blocks.SANDSTONE);
            }
        }
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(6.2D, 2.525D, 4.7D));
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        AdhesivePathPlanner.Plan plan = AdhesivePathPlanner.plan(
            helper.getLevel(),
            zombie,
            player,
            helper.absolutePos(targetWall),
            Direction.SOUTH
        );

        check(plan.valid(), "wall-to-opposite-face route was not found: " + plan.status());
        check(plan.points().size() > 2, "wall-to-opposite-face route did not go around the wall");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "13x7x13", floor = true)
    @TestHolder(description = "Villager adhesive paths enter a two-block-high doorway from every source grid phase")
    static void adhesivePathEntersDoorwayFromEveryGridPhase(ExtendedGameTestHelper helper) {
        BlockPos doorwayFloor = new BlockPos(6, 1, 6);
        BlockPos insideFloor = new BlockPos(6, 1, 7);
        for (int x = 1; x <= 11; x++) {
            helper.setBlock(new BlockPos(x, 1, 6), Blocks.SANDSTONE);
            if (x != 6) {
                helper.setBlock(new BlockPos(x, 2, 6), Blocks.SANDSTONE);
                helper.setBlock(new BlockPos(x, 3, 6), Blocks.SANDSTONE);
            }
            helper.setBlock(new BlockPos(x, 4, 6), Blocks.SANDSTONE);
        }
        for (int z = 7; z <= 10; z++) {
            helper.setBlock(new BlockPos(6, 1, z), Blocks.SANDSTONE);
            helper.setBlock(new BlockPos(6, 4, z), Blocks.SANDSTONE);
        }

        var villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(9.5D, 2.0D, 4.5D));
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        double[] phases = {0.0D, 0.2D, 0.5D, 0.8D};
        for (double phase : phases) {
            villager.setPos(helper.absoluteVec(new Vec3(9.0D + phase, 2.0D, 4.5D)));
            AdhesivePathPlanner.Plan doorwayPlan = AdhesivePathPlanner.plan(
                helper.getLevel(),
                villager,
                player,
                helper.absolutePos(doorwayFloor),
                Direction.UP
            );
            check(
                doorwayPlan.valid(),
                "doorway route failed at source x phase " + phase + ": " + doorwayPlan.status()
            );
            AdhesivePathPlanner.Plan insidePlan = AdhesivePathPlanner.plan(
                helper.getLevel(),
                villager,
                player,
                helper.absolutePos(insideFloor),
                Direction.UP
            );
            check(
                insidePlan.valid(),
                "one-block-inside route failed at source x phase " + phase + ": " + insidePlan.status()
            );
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "14x8x14", floor = true)
    @TestHolder(description = "Villager paths keep walking height through a doorway before attaching to an inside wall")
    static void adhesivePathEntersDoorwayBeforeAttachingInsideWall(ExtendedGameTestHelper helper) {
        for (int x = 1; x <= 12; x++) {
            if (x != 6) {
                helper.setBlock(new BlockPos(x, 2, 6), Blocks.SANDSTONE);
                helper.setBlock(new BlockPos(x, 3, 6), Blocks.SANDSTONE);
            }
            helper.setBlock(new BlockPos(x, 4, 6), Blocks.SANDSTONE);
            for (int z = 7; z <= 12; z++) {
                helper.setBlock(new BlockPos(x, 5, z), Blocks.SANDSTONE);
            }
        }
        for (int z = 7; z <= 12; z++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(1, y, z), Blocks.SANDSTONE);
                helper.setBlock(new BlockPos(12, y, z), Blocks.SANDSTONE);
            }
        }
        for (int x = 2; x <= 11; x++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, 12), Blocks.SANDSTONE);
            }
        }
        BlockPos targetWall = new BlockPos(9, 3, 9);
        for (int y = 1; y <= 4; y++) {
            helper.setBlock(new BlockPos(targetWall.getX(), y, targetWall.getZ()), Blocks.SANDSTONE);
        }

        var villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(8.5D, 2.0D, 4.5D));
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        AdhesivePathPlanner.Plan plan = AdhesivePathPlanner.plan(
            helper.getLevel(),
            villager,
            player,
            helper.absolutePos(targetWall),
            Direction.WEST
        );

        check(plan.valid(), "inside-wall route was not found: " + plan.status());
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "26x6x7", floor = true)
    @TestHolder(description = "Adhesive bonding stops at sixteen blocks and breaks selection past that range")
    static void adhesiveRangeLimitPreservesBucket(ExtendedGameTestHelper helper) {
        BlockPos limitSupport = new BlockPos(18, 1, 3);
        BlockPos distantSupport = new BlockPos(20, 1, 3);
        BlockPos breakSupport = new BlockPos(24, 1, 3);
        helper.setBlock(limitSupport, Blocks.STONE);
        helper.setBlock(distantSupport, Blocks.STONE);
        helper.setBlock(breakSupport, Blocks.STONE);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 3.5D));
        GameTestPlayer player = bucketPlayer(helper, new Vec3(2.5D, 2.0D, 2.0D));
        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, zombie), "distant entity selection failed");
        AdhesivePathPlanner.Plan limitPlan = AdhesivePathPlanner.plan(
            helper.getLevel(),
            zombie,
            player,
            helper.absolutePos(limitSupport),
            Direction.UP
        );
        check(limitPlan.valid(), "route at the sixteen-block limit was rejected");

        AdhesivePathPlanner.Plan breakPlan = AdhesivePathPlanner.plan(
            helper.getLevel(),
            zombie,
            player,
            helper.absolutePos(breakSupport),
            Direction.UP
        );
        check(
            AdhesivePathPlanner.exceedsBreakDistance(breakPlan.directDistance()),
            "route beyond twenty blocks did not cross the automatic break threshold"
        );

        Vec3 nearSupport = helper.absoluteVec(new Vec3(20.5D, 2.0D, 2.0D));
        player.moveTo(nearSupport.x, nearSupport.y, nearSupport.z);

        AdhesivePathPlanner.Plan plan = AdhesivePathPlanner.plan(
            helper.getLevel(),
            zombie,
            player,
            helper.absolutePos(distantSupport),
            Direction.UP
        );
        check(plan.status() == AdhesivePathPlanner.Status.OUT_OF_RANGE, "distant route was not rejected by range");
        check(!AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(distantSupport),
            Direction.UP
        ), "entity beyond sixteen blocks was bonded");
        check(!AdhesiveSelectionManager.hasSelection(player), "out-of-range route kept the entity selected");
        check(
            player.getMainHandItem().is(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get()),
            "failed distant bonding consumed the resin bucket"
        );
        check(!zombie.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT), "distant entity entered adhesive transit");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("8x7x7")
    @TestHolder(description = "Pistons resolve a bonded plastic block and its support as one structure")
    static void pistonCollectsBondedBlockAndSupport(ExtendedGameTestHelper helper) {
        BlockPos piston = new BlockPos(1, 2, 3);
        BlockPos support = piston.east();
        BlockPos occupied = support.above();
        helper.setBlock(support, Blocks.STONE);
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(4.5D, 2.0D, 3.5D));
        GameTestPlayer player = bucketPlayer(helper, new Vec3(2.5D, 2.0D, 1.5D));
        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, anvil), "plastic anvil selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(support),
            Direction.UP
        ), "plastic anvil bonding failed");

        helper.runAfterDelay(10, () -> {
            check(!anvil.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT), "bonded block transit did not finish");
            bondedBlockEntity(helper, occupied);
            PistonStructureResolver resolver = new PistonStructureResolver(
                helper.getLevel(),
                helper.absolutePos(piston),
                Direction.EAST,
                true
            );
            check(resolver.resolve(), "bonded support structure could not be pushed");
            check(resolver.getToPush().contains(helper.absolutePos(support)), "support block was omitted from piston push");
            check(resolver.getToPush().contains(helper.absolutePos(occupied)), "bonded block was omitted from piston push");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 35)
    @EmptyTemplate(value = "9x7x7", floor = true)
    @TestHolder(description = "A piston moves a bonded plastic block together with its support")
    static void pistonMovesBondedPlasticBlockAndSupport(ExtendedGameTestHelper helper) {
        BlockPos piston = new BlockPos(2, 2, 3);
        BlockPos support = piston.east();
        BlockPos occupied = support.above();
        helper.setBlock(support, Blocks.STONE);
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(6.5D, 3.0D, 3.5D));
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 2.0D));
        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, anvil), "plastic anvil selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(support),
            Direction.UP
        ), "plastic anvil bonding failed");

        helper.runAfterDelay(12, () -> {
            bondedBlockEntity(helper, occupied);
            helper.setBlock(
                piston,
                Blocks.PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST)
            );
            helper.setBlock(piston.west(), Blocks.REDSTONE_BLOCK);
            helper.runAfterDelay(5, () -> {
                BlockPos movedSupport = support.east();
                BlockPos movedOccupied = occupied.east();
                check(helper.getBlockState(movedSupport).is(Blocks.STONE), "piston did not move the bonded support");
                check(
                    helper.getBlockState(movedOccupied).is(PlasticraftBlocks.HARDEND_RESIN_ANVIL.get())
                        && helper.getBlockState(movedOccupied).getValue(AbstractPlasticEntityBlock.BONDED),
                    "piston did not move the bonded plastic block"
                );
                BondedEntityBlockEntity moved = bondedBlockEntity(helper, movedOccupied);
                check(
                    moved.getSupportPos().equals(helper.absolutePos(movedSupport)),
                    "piston movement did not update the bonded support position"
                );
                check(
                    moved.getPlasticOrientation().attachmentFace() == Direction.UP,
                    "piston movement changed the bonded plastic orientation"
                );
                check(helper.getBlockState(occupied).isAir(), "old bonded plastic position was not cleared");
                helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks = 55)
    @EmptyTemplate(value = "10x7x7", floor = true)
    @TestHolder(description = "A sticky piston moves a bonded block pair together in both directions")
    static void stickyPistonMovesBondedBlocksBothWays(ExtendedGameTestHelper helper) {
        BlockPos piston = new BlockPos(2, 2, 3);
        BlockPos first = piston.east();
        BlockPos second = first.above();
        BlockPos movedFirst = first.east();
        BlockPos movedSecond = second.east();
        helper.setBlock(
            piston,
            Blocks.STICKY_PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST)
        );
        helper.setBlock(first, Blocks.STONE);
        helper.setBlock(second, Blocks.GLASS);
        check(
            BondedFallingBlocks.connect(helper.getLevel(), helper.absolutePos(first), helper.absolutePos(second)),
            "bonded piston pair could not be connected"
        );

        helper.setBlock(piston.west(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(1, () -> {
            check(helper.getBlockState(movedFirst).is(Blocks.MOVING_PISTON), "first block did not start moving");
            check(helper.getBlockState(movedSecond).is(Blocks.MOVING_PISTON), "bonded block started late");
            check(
                helper.getBlockEntity(movedFirst) instanceof PistonMovingBlockEntity firstMoving
                    && firstMoving.getMovedState().is(Blocks.STONE),
                "first moving block did not preserve its render state"
            );
            check(
                helper.getBlockEntity(movedSecond) instanceof PistonMovingBlockEntity secondMoving
                    && secondMoving.getMovedState().is(Blocks.GLASS),
                "bonded moving block did not preserve its render state"
            );
            check(
                BondedFallingBlocks.getAdhesion(helper.getLevel(), helper.absolutePos(first)) == null
                    && BondedFallingBlocks.getAdhesion(helper.getLevel(), helper.absolutePos(second)) == null,
                "extension left adhesive data at the source"
            );
            check(
                BondedFallingBlocks.hasBlockBond(
                    helper.getLevel(),
                    helper.absolutePos(movedFirst),
                    Direction.UP
                ) && BondedFallingBlocks.hasBlockBond(
                    helper.getLevel(),
                    helper.absolutePos(movedSecond),
                    Direction.DOWN
                ),
                "extension did not move the adhesive bond with the blocks"
            );

            helper.runAfterDelay(4, () -> {
                check(helper.getBlockState(movedFirst).is(Blocks.STONE), "first block did not finish extending");
                check(helper.getBlockState(movedSecond).is(Blocks.GLASS), "bonded block did not finish extending");
                helper.setBlock(piston.west(), Blocks.AIR);
                helper.runAfterDelay(1, () -> {
                    check(helper.getBlockState(first).is(Blocks.MOVING_PISTON), "first block did not start retracting");
                    check(helper.getBlockState(second).is(Blocks.MOVING_PISTON), "bonded block was not retracted");
                    check(
                        helper.getBlockEntity(first) instanceof PistonMovingBlockEntity firstMoving
                            && firstMoving.getMovedState().is(Blocks.STONE),
                        "retracting first block did not preserve its render state"
                    );
                    check(
                        helper.getBlockEntity(second) instanceof PistonMovingBlockEntity secondMoving
                            && secondMoving.getMovedState().is(Blocks.GLASS),
                        "retracting bonded block did not preserve its render state"
                    );
                    check(
                        BondedFallingBlocks.getAdhesion(helper.getLevel(), helper.absolutePos(movedFirst)) == null
                            && BondedFallingBlocks.getAdhesion(
                                helper.getLevel(),
                                helper.absolutePos(movedSecond)
                            ) == null,
                        "retraction left floating adhesive data"
                    );
                    helper.runAfterDelay(4, () -> {
                        check(helper.getBlockState(first).is(Blocks.STONE), "first block did not return");
                        check(helper.getBlockState(second).is(Blocks.GLASS), "bonded block did not return");
                        check(
                            BondedFallingBlocks.hasBlockBond(
                                helper.getLevel(),
                                helper.absolutePos(first),
                                Direction.UP
                            ) && BondedFallingBlocks.hasBlockBond(
                                helper.getLevel(),
                                helper.absolutePos(second),
                                Direction.DOWN
                            ),
                            "round trip broke the adhesive bond"
                        );
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(timeoutTicks = 55)
    @EmptyTemplate(value = "10x7x7", floor = true)
    @TestHolder(description = "An entity bonded to a block follows a sticky piston in both directions")
    static void bondedEntityFollowsStickyPistonBothWays(ExtendedGameTestHelper helper) {
        BlockPos piston = new BlockPos(2, 2, 3);
        BlockPos support = piston.east();
        BlockPos movedSupport = support.east();
        helper.setBlock(
            piston,
            Blocks.STICKY_PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST)
        );
        helper.setBlock(support, Blocks.STONE);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 3.0D, 3.5D));
        check(
            BondedFallingBlocks.putPatch(helper.getLevel(), helper.absolutePos(support), Direction.UP),
            "entity piston test could not place adhesive"
        );
        check(
            AdhesiveBondingService.bondEntityFromPatch(
                helper.getLevel(),
                zombie,
                helper.absolutePos(support),
                Direction.UP
            ),
            "entity piston test could not bond the entity"
        );
        Vec3 startPosition = zombie.position();

        helper.setBlock(piston.west(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(2, () -> {
            check(zombie.hasData(PlasticraftAttachments.ENTITY_ADHESION), "extension released the bonded entity");
            check(
                zombie.getX() > startPosition.x + 0.05D && zombie.getX() <= startPosition.x + 1.0D + EPSILON,
                "bonded entity did not follow the extending block"
            );
            helper.runAfterDelay(4, () -> {
                EntityAdhesion pushed = zombie.getExistingDataOrNull(PlasticraftAttachments.ENTITY_ADHESION.get());
                check(pushed != null, "extended entity lost its adhesive attachment");
                check(
                    pushed.supportPos().equals(helper.absolutePos(movedSupport)),
                    "extended entity kept the old support position"
                );
                check(close(zombie.position(), startPosition.add(1.0D, 0.0D, 0.0D)), "entity did not finish extending");

                helper.setBlock(piston.west(), Blocks.AIR);
                helper.runAfterDelay(2, () -> {
                    check(zombie.hasData(PlasticraftAttachments.ENTITY_ADHESION), "retraction released the bonded entity");
                    check(
                        zombie.getX() < startPosition.x + 0.95D && zombie.getX() >= startPosition.x - EPSILON,
                        "bonded entity did not follow the retracting block"
                    );
                    helper.runAfterDelay(4, () -> {
                        EntityAdhesion returned = zombie.getExistingDataOrNull(PlasticraftAttachments.ENTITY_ADHESION.get());
                        check(returned != null, "returned entity lost its adhesive attachment");
                        check(
                            returned.supportPos().equals(helper.absolutePos(support)),
                            "returned entity kept the pushed support position"
                        );
                        check(close(zombie.position(), startPosition), "entity did not return with its support");
                        check(
                            BondedFallingBlocks.hasEntityBond(
                                helper.getLevel(),
                                helper.absolutePos(support),
                                Direction.UP
                            ),
                            "entity round trip broke the block-side adhesive"
                        );
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "8x7x7", floor = true)
    @TestHolder(description = "An immovable member prevents a bonded piston structure from moving")
    static void immovableBondedMemberBlocksPiston(ExtendedGameTestHelper helper) {
        BlockPos piston = new BlockPos(2, 2, 3);
        BlockPos movable = piston.east();
        BlockPos immovable = movable.above();
        helper.setBlock(movable, Blocks.STONE);
        helper.setBlock(immovable, Blocks.OBSIDIAN);
        check(
            BondedFallingBlocks.connect(helper.getLevel(), helper.absolutePos(movable), helper.absolutePos(immovable)),
            "immovable piston test could not connect the blocks"
        );
        PistonStructureResolver resolver = new PistonStructureResolver(
            helper.getLevel(),
            helper.absolutePos(piston),
            Direction.EAST,
            true
        );
        check(!resolver.resolve(), "piston ignored an immovable bonded member");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate("11x7x7")
    @TestHolder(description = "A sliding rail moves every block in a bidirectionally bonded group")
    static void slidingRailMovesBondedBlockGroup(ExtendedGameTestHelper helper) {
        BlockPos origin = new BlockPos(2, 2, 3);
        BlockPos partner = origin.above();
        BlockPos destination = new BlockPos(5, 2, 3);
        for (int x = 2; x < 5; x++) {
            helper.setBlock(
                new BlockPos(x, 1, 3),
                ModBlocks.SLIDING_RAIL.get().defaultBlockState()
            );
        }
        helper.setBlock(
            new BlockPos(5, 1, 3),
            ModBlocks.SLIDING_RAIL_STOP.get().defaultBlockState()
        );
        helper.setBlock(origin, Blocks.STONE);
        helper.setBlock(partner, Blocks.GOLD_BLOCK);
        check(
            BondedFallingBlocks.connect(
                helper.getLevel(),
                helper.absolutePos(origin),
                helper.absolutePos(partner)
            ),
            "sliding test block group could not be bonded"
        );
        check(
            ISlidingRail.moveBlocks(helper.getLevel(), helper.absolutePos(origin), Direction.EAST),
            "sliding rail rejected the bonded block group"
        );
        SlidingBlockEntity sliding = helper.getLevel().getEntitiesOfClass(
            SlidingBlockEntity.class,
            new AABB(helper.absolutePos(origin)).inflate(3.0D)
        ).stream().findFirst().orElseThrow(() -> new GameTestAssertException("sliding block entity was not created"));
        check(sliding.getBlockCount() == 2, "sliding resolver omitted a bonded block");

        helper.runAfterDelay(16, () -> {
            check(helper.getBlockState(destination).is(Blocks.STONE), "sliding rail lost the group root");
            check(helper.getBlockState(destination.above()).is(Blocks.GOLD_BLOCK), "sliding rail lost the bonded block");
            check(helper.getBlockState(origin).isAir(), "sliding rail left the old group root behind");
            check(helper.getBlockState(partner).isAir(), "sliding rail left the old bonded block behind");
            check(
                BondedFallingBlocks.hasBlockBond(
                    helper.getLevel(),
                    helper.absolutePos(destination),
                    Direction.UP
                ) && BondedFallingBlocks.hasBlockBond(
                    helper.getLevel(),
                    helper.absolutePos(destination.above()),
                    Direction.DOWN
                ),
                "sliding rail movement did not restore the bidirectional bond"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 35)
    @EmptyTemplate(value = "11x9x7", floor = true)
    @TestHolder(description = "AnvilCraft magnets do not lift either kind of bonded resin anvil")
    static void anvilcraftMagnetIgnoresBondedPlasticAnvils(ExtendedGameTestHelper helper) {
        BlockPos hardenedSupport = new BlockPos(3, 1, 3);
        BlockPos hardenedOccupied = hardenedSupport.above();
        BlockPos resinSupport = new BlockPos(7, 1, 3);
        BlockPos resinOccupied = resinSupport.above();
        helper.setBlock(hardenedSupport, Blocks.STONE);
        helper.setBlock(resinSupport, Blocks.STONE);
        HardenedResinAnvilEntity hardened = createAnvil(helper, new Vec3(2.5D, 2.0D, 3.5D));
        ResinAnvilEntity resin = createResinAnvil(helper, new Vec3(8.5D, 2.0D, 3.5D));
        GameTestPlayer player = bucketPlayer(helper, new Vec3(5.5D, 2.0D, 2.0D));

        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, hardened), "hardened anvil selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(hardenedSupport),
            Direction.UP
        ), "hardened anvil bonding failed");
        player.setItemInHand(InteractionHand.MAIN_HAND, PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.asStack());
        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, resin), "resin anvil selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(resinSupport),
            Direction.UP
        ), "resin anvil bonding failed");

        helper.runAfterDelay(12, () -> {
            bondedBlockEntity(helper, hardenedOccupied);
            bondedBlockEntity(helper, resinOccupied);
            helper.setBlock(
                hardenedOccupied.above(4),
                ModBlocks.MAGNET_BLOCK.get().defaultBlockState()
            );
            helper.setBlock(
                resinOccupied.above(4),
                ModBlocks.MAGNET_BLOCK.get().defaultBlockState()
            );
            helper.runAfterDelay(4, () -> {
                check(
                    helper.getBlockState(hardenedOccupied).is(PlasticraftBlocks.HARDEND_RESIN_ANVIL.get()),
                    "AnvilCraft magnet lifted the bonded hardened resin anvil"
                );
                check(
                    helper.getBlockState(resinOccupied).is(PlasticraftBlocks.RESIN_ANVIL.get()),
                    "AnvilCraft magnet lifted the bonded resin anvil"
                );
                bondedBlockEntity(helper, hardenedOccupied);
                bondedBlockEntity(helper, resinOccupied);
                helper.succeed();
            });
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "Rotating a bonded plastic block deflects and returns without releasing it")
    static void hammerRotationReturnsBondedPlasticBlock(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(3, 1, 3);
        BlockPos occupied = support.above();
        helper.setBlock(support, Blocks.STONE);
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(2.5D, 2.0D, 2.5D));
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 1.5D));
        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, anvil), "plastic anvil selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(support),
            Direction.UP
        ), "plastic anvil bonding failed");

        helper.runAfterDelay(10, () -> {
            player.setItemInHand(InteractionHand.MAIN_HAND, PlasticraftItems.RESIN_ANVIL_HAMMER.asStack());
            new BondedPlasticHammerRotatePacket(
                helper.absolutePos(occupied),
                InteractionHand.MAIN_HAND,
                Direction.EAST
            ).handleOnServer(player);

            check(
                helper.getBlockState(occupied).is(PlasticraftBlocks.HARDEND_RESIN_ANVIL.get()),
                "hammer rotation released the bonded block"
            );
            BondedEntityBlockEntity deflected = bondedBlockEntity(helper, occupied);
            check(
                deflected.getPlasticOrientation().attachmentFace() == Direction.EAST
                    && deflected.isHammerDeflected(),
                "six-face hammer selection did not temporarily deflect the bonded block"
            );
            helper.runAfterDelay(3, () -> {
                BondedEntityBlockEntity returned = bondedBlockEntity(helper, occupied);
                check(
                    returned.getPlasticOrientation().attachmentFace() == Direction.UP,
                    "bonded block did not return to its original direction after two ticks"
                );
                check(
                    BondedFallingBlocks.hasBlockBond(
                        helper.getLevel(),
                        helper.absolutePos(support),
                        Direction.UP
                    ),
                    "hammer deflection removed the block adhesive"
                );
                helper.runAfterDelay(BondedEntityBlockEntity.HAMMER_RETURN_ANIMATION_TICKS + 1, () -> {
                    check(
                        !bondedBlockEntity(helper, occupied).isHammerDeflected(),
                        "hammer return animation state did not finish"
                    );
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x6x7", floor = true)
    @TestHolder(description = "Rotating a plastic entity bonded to plastic and ordinary entities returns it without releasing bonds")
    static void hammerRotationReturnsEntityBondedPlasticEntities(ExtendedGameTestHelper helper) {
        HardenedResinCauldronEntity cauldron = createCauldron(helper, new Vec3(3.5D, 2.0D, 3.5D));
        HardenedResinAnvilEntity anvil = createAnvil(helper, new Vec3(4.5D, 2.0D, 3.5D));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(5.5D, 2.0D, 3.5D));
        check(
            EntityBondManager.connect(
                helper.getLevel(), cauldron, Direction.EAST, anvil, Direction.WEST, cauldron.isNoGravity()
            ),
            "plastic pot could not bond to the plastic anvil"
        );
        check(
            EntityBondManager.connect(
                helper.getLevel(), anvil, Direction.EAST, zombie, Direction.WEST, anvil.isNoGravity()
            ),
            "plastic anvil could not bond to the ordinary entity"
        );
        GameTestPlayer player = bucketPlayer(helper, new Vec3(4.5D, 2.0D, 1.5D));
        player.setItemInHand(InteractionHand.MAIN_HAND, PlasticraftItems.RESIN_ANVIL_HAMMER.asStack());

        new PlasticEntityHammerRotatePacket(
            cauldron.getId(), InteractionHand.MAIN_HAND, Direction.EAST
        ).handleOnServer(player);
        new PlasticEntityHammerRotatePacket(
            anvil.getId(), InteractionHand.MAIN_HAND, Direction.NORTH
        ).handleOnServer(player);
        check(
            cauldron.getOrientation().attachmentFace() == Direction.EAST && cauldron.isHammerDeflected(),
            "entity bond did not temporarily deflect the plastic pot"
        );
        check(
            anvil.getOrientation().attachmentFace() == Direction.NORTH && anvil.isHammerDeflected(),
            "ordinary-entity bond did not temporarily deflect the plastic anvil"
        );
        check(
            EntityBondManager.hasBonds(cauldron)
                && EntityBondManager.hasBonds(anvil)
                && EntityBondManager.hasBonds(zombie),
            "hammer deflection removed an entity bond"
        );

        helper.runAfterDelay(3, () -> {
            check(
                cauldron.getOrientation().attachmentFace() == Direction.UP
                    && anvil.getOrientation().attachmentFace() == Direction.UP,
                "entity-bonded plastic entities did not return after two ticks"
            );
            check(
                EntityBondManager.hasBonds(cauldron)
                    && EntityBondManager.hasBonds(anvil)
                    && EntityBondManager.hasBonds(zombie),
                "returning entity-bonded plastic entities released a bond"
            );
            helper.runAfterDelay(AbstractPlasticEntity.HAMMER_RETURN_ANIMATION_TICKS + 1, () -> {
                check(
                    !cauldron.isHammerDeflected() && !anvil.isHammerDeflected(),
                    "entity hammer return animation state did not finish"
                );
                helper.succeed();
            });
        });
    }

    private static GameTestPlayer bucketPlayer(ExtendedGameTestHelper helper, Vec3 relativePosition) {
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        Vec3 position = helper.absoluteVec(relativePosition);
        player.moveTo(position.x, position.y, position.z);
        player.setItemInHand(
            InteractionHand.MAIN_HAND,
            PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.asStack()
        );
        return player;
    }

    private static void awaitTransit(
        ExtendedGameTestHelper helper,
        Entity entity,
        int maximumAttempts,
        Consumer<AdhesiveTransit> assertion
    ) {
        int[] attempts = {0};
        Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            AdhesiveTransit transit = entity.getExistingDataOrNull(PlasticraftAttachments.ADHESIVE_TRANSIT.get());
            if (transit != null) {
                assertion.accept(transit);
                helper.succeed();
                return;
            }
            if (++attempts[0] >= maximumAttempts) {
                check(false, "adhesive preview confirmation never started transit");
                return;
            }
            helper.runAfterDelay(1, poll[0]);
        };
        poll[0].run();
    }

    private static HardenedResinAnvilEntity createAnvil(ExtendedGameTestHelper helper, Vec3 relativePosition) {
        HardenedResinAnvilEntity anvil = new HardenedResinAnvilEntity(
            PlasticraftEntities.HARDEND_RESIN_ANVIL.get(),
            helper.getLevel(),
            helper.absoluteVec(relativePosition),
            PlasticraftBlocks.HARDEND_RESIN_ANVIL.get().defaultBlockState(),
            PlasticraftBlocks.HARDEND_RESIN_ANVIL.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        check(helper.getLevel().addFreshEntity(anvil), "failed to add plastic anvil");
        return anvil;
    }

    private static ResinAnvilEntity createResinAnvil(ExtendedGameTestHelper helper, Vec3 relativePosition) {
        ResinAnvilEntity anvil = new ResinAnvilEntity(
            PlasticraftEntities.RESIN_ANVIL.get(),
            helper.getLevel(),
            helper.absoluteVec(relativePosition),
            PlasticraftBlocks.RESIN_ANVIL.get().defaultBlockState(),
            PlasticraftBlocks.RESIN_ANVIL.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        check(helper.getLevel().addFreshEntity(anvil), "failed to add resin anvil");
        return anvil;
    }

    private static HardenedResinCauldronEntity createCauldron(
        ExtendedGameTestHelper helper,
        Vec3 relativePosition
    ) {
        HardenedResinCauldronEntity cauldron = new HardenedResinCauldronEntity(
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get(),
            helper.getLevel(),
            helper.absoluteVec(relativePosition),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        check(helper.getLevel().addFreshEntity(cauldron), "failed to add plastic pot");
        return cauldron;
    }

    private static UniversalPlasticEntity createUniversalPlastic(
        ExtendedGameTestHelper helper,
        Vec3 relativePosition
    ) {
        UniversalPlasticEntity plastic = new UniversalPlasticEntity(
            PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
            helper.getLevel(),
            helper.absoluteVec(relativePosition),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        check(helper.getLevel().addFreshEntity(plastic), "failed to add universal plastic");
        return plastic;
    }

    private static BondedEntityBlockEntity bondedBlockEntity(ExtendedGameTestHelper helper, BlockPos relativePos) {
        if (helper.getBlockEntity(relativePos) instanceof BondedEntityBlockEntity bonded && bonded.isInitialized()) {
            return bonded;
        }
        throw new GameTestAssertException("bonded block entity was missing at " + relativePos);
    }

    private static BlockHitResult blockHit(ExtendedGameTestHelper helper, BlockPos relativePos) {
        return blockHit(helper, relativePos, Direction.UP);
    }

    private static BlockHitResult blockHit(
        ExtendedGameTestHelper helper,
        BlockPos relativePos,
        Direction direction
    ) {
        BlockPos absolutePos = helper.absolutePos(relativePos);
        return new BlockHitResult(Vec3.atCenterOf(absolutePos), direction, absolutePos, false);
    }

    private static int countItem(HardenedResinCauldronEntity cauldron, Item item) {
        int count = 0;
        for (int slot = 0; slot < cauldron.getItemHandler().getSlots(); slot++) {
            ItemStack stack = cauldron.getItemHandler().getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static boolean hasCurvedCoordinate(Vec3 point) {
        return !nearHalf(point.x) || !nearHalf(point.y) || !nearHalf(point.z);
    }

    private static boolean nearHalf(double value) {
        return Math.abs(value - Math.floor(value) - 0.5D) < 1.0E-5D
            || Math.abs(value - Math.rint(value)) < 1.0E-5D;
    }

    private static boolean close(Vec3 first, Vec3 second) {
        return first.distanceToSqr(second) <= EPSILON * EPSILON;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
