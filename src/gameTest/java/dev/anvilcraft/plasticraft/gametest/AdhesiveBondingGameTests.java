package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.BondedFallingBlockInfo;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.ResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveBondingService;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesivePathPlanner;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveSelectionManager;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityAdhesion;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondState;
import dev.anvilcraft.plasticraft.event.BondedFallingBlockEvents;
import dev.anvilcraft.plasticraft.init.ModAttachments;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.entity.ModEntities;
import dev.anvilcraft.plasticraft.init.item.ModItems;
import dev.anvilcraft.plasticraft.inventory.HardenedResinAnvilMenu;
import dev.anvilcraft.plasticraft.network.BondedPlasticHammerRotatePacket;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.block.sliding.ISlidingRail;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.entity.FallingGiantAnvilEntity;
import dev.dubhe.anvilcraft.entity.SlidingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

/** 高粘性树脂桶固定实体的服务端行为测试。 */
public final class AdhesiveBondingGameTests {
    private static final double EPSILON = 1.0E-5D;

    private AdhesiveBondingGameTests() {
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
            dev.dubhe.anvilcraft.init.block.ModBlocks.GIANT_ANVIL.get().defaultBlockState(),
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
            GiantAnvilBlock block = dev.dubhe.anvilcraft.init.block.ModBlocks.GIANT_ANVIL.get();
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
            dev.dubhe.anvilcraft.init.block.ModBlocks.GIANT_ANVIL.get().defaultBlockState(),
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
            EntityAdhesion adhesion = zombie.getExistingDataOrNull(ModAttachments.ENTITY_ADHESION.get());
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
                !BondedFallingBlocks.hasPatch(helper.getLevel(), helper.absolutePos(support), Direction.UP),
                "consumed bare patch remained available"
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
                EntityAdhesion adhesion = support.getExistingDataOrNull(ModAttachments.ENTITY_ADHESION.get());
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

            player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.asStack());
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
                player.getMainHandItem().is(ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get()),
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

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "8x6x8", floor = true)
    @TestHolder(description = "Strong knockback disconnects only the struck entity from its component")
    static void strongKnockbackDisconnectsOnlyTargetEntity(ExtendedGameTestHelper helper) {
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

        CommonHooks.onLivingKnockBack(first, 2.5F, 0.0D, 1.0D);
        check(!EntityBondManager.hasBonds(first), "struck entity retained its bond");
        EntityBondState middleBonds = EntityBondManager.get(middle);
        EntityBondState lastBonds = EntityBondManager.get(last);
        check(
            middleBonds != null
                && middleBonds.linkAt(Direction.WEST) == null
                && middleBonds.linkAt(Direction.EAST) != null,
            "strong knockback removed the remaining component bond"
        );
        check(
            lastBonds != null && lastBonds.linkAt(Direction.WEST) != null,
            "strong knockback disconnected an entity that was not struck"
        );
        helper.succeed();
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

    @GameTest(timeoutTicks = 25)
    @EmptyTemplate(value = "8x6x8", floor = true)
    @TestHolder(description = "A discarded entity only removes its own bonds from the remaining component")
    static void discardedEntityOnlyDisconnectsItself(ExtendedGameTestHelper helper) {
        Zombie removed = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 2.0D, 3.5D));
        Zombie first = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 2.0D, 3.5D));
        Zombie second = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 2.0D, 3.5D));
        removed.setNoGravity(true);
        first.setNoGravity(true);
        second.setNoGravity(true);
        check(EntityBondManager.connect(
            helper.getLevel(), removed, Direction.EAST, first, Direction.WEST, true
        ), "discarded-to-first bond failed");
        check(EntityBondManager.connect(
            helper.getLevel(), first, Direction.EAST, second, Direction.WEST, true
        ), "remaining entity bond failed");

        removed.discard();
        EntityBondManager.tick(first);
        helper.runAfterDelay(1, () -> {
            EntityBondState firstBonds = EntityBondManager.get(first);
            EntityBondState secondBonds = EntityBondManager.get(second);
            check(
                firstBonds != null
                    && firstBonds.linkAt(Direction.EAST) != null
                    && firstBonds.linkAt(Direction.WEST) == null,
                "discarding one entity removed the wrong remaining bond"
            );
            check(
                secondBonds != null && secondBonds.linkAt(Direction.WEST) != null,
                "discarding one entity released the whole component"
            );
            helper.succeed();
        });
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
            check(
                helper.getBlockState(occupied).is(ModBlocks.HARDEND_RESIN_ANVIL.get())
                    && helper.getBlockState(occupied).getValue(AbstractPlasticEntityBlock.BONDED),
                "mixed-group plastic entity did not blockify"
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
            check(!anvil.hasData(ModAttachments.ADHESIVE_TRANSIT), "failed transit was not canceled");
            check(anvil.getOrientation().equals(startingOrientation), "failed transit kept its target orientation");
            check(!anvil.isRemoved(), "failed transit removed the plastic entity");
            helper.succeed();
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
    @TestHolder(description = "A plastic entity group blockifies together when one member bonds to a block")
    static void plasticEntityGroupBlockifiesTogether(ExtendedGameTestHelper helper) {
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
            player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.asStack());
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
                    helper.getBlockState(rootBlock).is(ModBlocks.HARDEND_RESIN_ANVIL.get())
                        && helper.getBlockState(rootBlock).getValue(AbstractPlasticEntityBlock.BONDED),
                    "plastic group root did not blockify"
                );
                check(
                    helper.getBlockState(followerBlock).is(ModBlocks.HARDEND_RESIN_ANVIL.get())
                        && helper.getBlockState(followerBlock).getValue(AbstractPlasticEntityBlock.BONDED),
                    "plastic group follower did not blockify"
                );
                check(bondedBlockEntity(helper, rootBlock).isInitialized(), "root block entity was not initialized");
                check(
                    bondedBlockEntity(helper, followerBlock).isInitialized(),
                    "follower block entity was not initialized"
                );
                helper.succeed();
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
        check(zombie.hasData(ModAttachments.ADHESIVE_TRANSIT), "ordinary entity did not enter adhesive transit");
        check(player.getMainHandItem().is(Items.BUCKET), "survival bonding did not return an empty bucket");

        helper.runAfterDelay(10, () -> {
            check(zombie.hasData(ModAttachments.ENTITY_ADHESION), "ordinary entity has no adhesion attachment");
            Vec3 fixedPosition = zombie.position();
            zombie.move(MoverType.SELF, new Vec3(1.0D, 0.0D, 0.0D));
            check(close(zombie.position(), fixedPosition), "bonded entity moved through the move entry point");
            helper.setBlock(support, Blocks.DIRT);
            helper.runAfterDelay(2, () -> {
                check(!zombie.hasData(ModAttachments.ENTITY_ADHESION), "replaced support did not release the entity");
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
            check(zombie.hasData(ModAttachments.ENTITY_ADHESION), "ordinary entity has no adhesion attachment");
            zombie.setPos(zombie.position().add(3.0D, 0.0D, 0.0D));
            AdhesiveBondingService.tickBondedEntity(zombie, true);
            check(!zombie.hasData(ModAttachments.ENTITY_ADHESION), "teleported entity remained bonded");
            check(!zombie.isNoGravity(), "teleported entity kept its bonded no-gravity flag");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "7x5x7", floor = true)
    @TestHolder(description = "Knockback V releases an ordinary bonded living entity")
    static void strongKnockbackReleasesBondedEntity(ExtendedGameTestHelper helper) {
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
            check(zombie.hasData(ModAttachments.ENTITY_ADHESION), "ordinary entity has no adhesion attachment");
            CommonHooks.onLivingKnockBack(zombie, 2.5F, 0.0D, 1.0D);
            check(!zombie.hasData(ModAttachments.ENTITY_ADHESION), "Knockback V did not release the entity");
            check(!zombie.isNoGravity(), "Knockback V did not restore the entity's gravity flag");
            helper.succeed();
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
        check(anvil.hasData(ModAttachments.ADHESIVE_TRANSIT), "plastic anvil did not enter adhesive transit");
        helper.runAfterDelay(10, () -> {
            check(helper.getBlockState(occupied).is(ModBlocks.HARDEND_RESIN_ANVIL.get()), "wrong fixed block was placed");
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
                anvil.hasData(ModAttachments.ADHESIVE_TRANSIT),
                "plastic anvil lost its transit ghost before the fixed block appeared"
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
                        !restored.hasData(ModAttachments.ADHESIVE_TRANSIT),
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
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.asStack());
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
            VoxelShape royalFloorShape = dev.dubhe.anvilcraft.init.block.ModBlocks.ROYAL_ANVIL
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
            player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.RESIN_ANVIL_HAMMER.asStack());
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
                player.getInventory().countItem(ModBlocks.HARDEND_RESIN_ANVIL.asItem()) == 0,
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
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.asStack());
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
                    helper.getBlockState(cauldronPos).is(ModBlocks.HARDEND_RESIN_CAULDRON.get())
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
            player.getMainHandItem().is(ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get()),
            "failed distant bonding consumed the resin bucket"
        );
        check(!zombie.hasData(ModAttachments.ADHESIVE_TRANSIT), "distant entity entered adhesive transit");
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
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 1.5D));
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
                    helper.getBlockState(movedOccupied).is(ModBlocks.HARDEND_RESIN_ANVIL.get())
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
            check(zombie.hasData(ModAttachments.ENTITY_ADHESION), "extension released the bonded entity");
            check(
                zombie.getX() > startPosition.x + 0.05D && zombie.getX() <= startPosition.x + 1.0D + EPSILON,
                "bonded entity did not follow the extending block"
            );
            helper.runAfterDelay(4, () -> {
                EntityAdhesion pushed = zombie.getExistingDataOrNull(ModAttachments.ENTITY_ADHESION.get());
                check(pushed != null, "extended entity lost its adhesive attachment");
                check(
                    pushed.supportPos().equals(helper.absolutePos(movedSupport)),
                    "extended entity kept the old support position"
                );
                check(close(zombie.position(), startPosition.add(1.0D, 0.0D, 0.0D)), "entity did not finish extending");

                helper.setBlock(piston.west(), Blocks.AIR);
                helper.runAfterDelay(2, () -> {
                    check(zombie.hasData(ModAttachments.ENTITY_ADHESION), "retraction released the bonded entity");
                    check(
                        zombie.getX() < startPosition.x + 0.95D && zombie.getX() >= startPosition.x - EPSILON,
                        "bonded entity did not follow the retracting block"
                    );
                    helper.runAfterDelay(4, () -> {
                        EntityAdhesion returned = zombie.getExistingDataOrNull(ModAttachments.ENTITY_ADHESION.get());
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
                dev.dubhe.anvilcraft.init.block.ModBlocks.SLIDING_RAIL.get().defaultBlockState()
            );
        }
        helper.setBlock(
            new BlockPos(5, 1, 3),
            dev.dubhe.anvilcraft.init.block.ModBlocks.SLIDING_RAIL_STOP.get().defaultBlockState()
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
        GameTestPlayer player = bucketPlayer(helper, new Vec3(5.5D, 2.0D, 1.5D));

        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, hardened), "hardened anvil selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(hardenedSupport),
            Direction.UP
        ), "hardened anvil bonding failed");
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.asStack());
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
                dev.dubhe.anvilcraft.init.block.ModBlocks.MAGNET_BLOCK.get().defaultBlockState()
            );
            helper.setBlock(
                resinOccupied.above(4),
                dev.dubhe.anvilcraft.init.block.ModBlocks.MAGNET_BLOCK.get().defaultBlockState()
            );
            helper.runAfterDelay(4, () -> {
                check(
                    helper.getBlockState(hardenedOccupied).is(ModBlocks.HARDEND_RESIN_ANVIL.get()),
                    "AnvilCraft magnet lifted the bonded hardened resin anvil"
                );
                check(
                    helper.getBlockState(resinOccupied).is(ModBlocks.RESIN_ANVIL.get()),
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
    @TestHolder(description = "Rotating a bonded plastic block with an anvil hammer releases its entity")
    static void hammerRotationReleasesPlasticEntity(ExtendedGameTestHelper helper) {
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
            player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.RESIN_ANVIL_HAMMER.asStack());
            new BondedPlasticHammerRotatePacket(
                helper.absolutePos(occupied),
                InteractionHand.MAIN_HAND,
                Direction.EAST
            ).handleOnServer(player);

            check(helper.getBlockState(occupied).isAir(), "hammer release left the bonded block behind");
            HardenedResinAnvilEntity restored = helper.getLevel().getEntitiesOfClass(
                HardenedResinAnvilEntity.class,
                new AABB(helper.absolutePos(occupied)).inflate(1.0D)
            ).stream().findFirst().orElseThrow(() -> new GameTestAssertException("hammer release did not restore entity"));
            check(
                restored.getOrientation().attachmentFace() == Direction.EAST,
                "six-face hammer selection was not applied to the restored entity"
            );
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate(value = "9x7x7", floor = true)
    @TestHolder(description = "A piston moves a pushable bonded falling block together with its support")
    static void pistonMovesBondedFallingBlockAndSupport(ExtendedGameTestHelper helper) {
        BlockPos piston = new BlockPos(2, 2, 3);
        BlockPos support = piston.east();
        BlockPos occupied = support.above();
        BlockPos source = new BlockPos(6, 4, 3);
        helper.setBlock(support, Blocks.STONE);
        helper.setBlock(source, Blocks.SAND);
        FallingBlockEntity sand = FallingBlockEntity.fall(
            helper.getLevel(),
            helper.absolutePos(source),
            Blocks.SAND.defaultBlockState()
        );
        GameTestPlayer player = bucketPlayer(helper, new Vec3(3.5D, 2.0D, 1.5D));
        check(AdhesiveBondingService.select(player, InteractionHand.MAIN_HAND, sand), "falling sand selection failed");
        check(AdhesiveBondingService.bondSelected(
            player,
            InteractionHand.MAIN_HAND,
            helper.absolutePos(support),
            Direction.UP
        ), "falling sand bonding failed");

        helper.runAfterDelay(12, () -> {
            check(helper.getBlockState(occupied).is(Blocks.SAND), "falling sand did not restore its block state");
            helper.setBlock(
                piston,
                Blocks.PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST)
            );
            helper.setBlock(piston.west(), Blocks.REDSTONE_BLOCK);
            helper.runAfterDelay(5, () -> {
                BlockPos movedSupport = support.east();
                BlockPos movedOccupied = occupied.east();
                check(helper.getBlockState(movedSupport).is(Blocks.STONE), "piston did not move the support block");
                check(
                    helper.getBlockState(movedOccupied).is(Blocks.SAND),
                    "piston did not move the bonded falling block: old="
                        + helper.getBlockState(occupied)
                        + ", moved="
                        + helper.getBlockState(movedOccupied)
                        + ", oldBe="
                        + helper.getLevel().getBlockEntity(helper.absolutePos(occupied))
                        + ", movedBe="
                        + helper.getLevel().getBlockEntity(helper.absolutePos(movedOccupied))
                        + ", falling="
                        + helper.getLevel().getEntitiesOfClass(
                            FallingBlockEntity.class,
                            new AABB(helper.absolutePos(occupied)).inflate(3.0D)
                        ).size()
                );
                BondedFallingBlockInfo bonded = BondedFallingBlocks.get(
                    helper.getLevel(),
                    helper.absolutePos(movedOccupied)
                );
                check(
                    bonded != null
                        && bonded.supportPos().equals(helper.absolutePos(movedSupport))
                        && bonded.blockState().is(Blocks.SAND),
                    "piston movement lost the bonded falling block data"
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
            dev.anvilcraft.plasticraft.init.item.ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.asStack()
        );
        return player;
    }

    private static HardenedResinAnvilEntity createAnvil(ExtendedGameTestHelper helper, Vec3 relativePosition) {
        HardenedResinAnvilEntity anvil = new HardenedResinAnvilEntity(
            ModEntities.HARDEND_RESIN_ANVIL.get(),
            helper.getLevel(),
            helper.absoluteVec(relativePosition),
            ModBlocks.HARDEND_RESIN_ANVIL.get().defaultBlockState(),
            ModBlocks.HARDEND_RESIN_ANVIL.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        check(helper.getLevel().addFreshEntity(anvil), "failed to add plastic anvil");
        return anvil;
    }

    private static ResinAnvilEntity createResinAnvil(ExtendedGameTestHelper helper, Vec3 relativePosition) {
        ResinAnvilEntity anvil = new ResinAnvilEntity(
            ModEntities.RESIN_ANVIL.get(),
            helper.getLevel(),
            helper.absoluteVec(relativePosition),
            ModBlocks.RESIN_ANVIL.get().defaultBlockState(),
            ModBlocks.RESIN_ANVIL.asStack(),
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
            ModEntities.HARDEND_RESIN_CAULDRON.get(),
            helper.getLevel(),
            helper.absoluteVec(relativePosition),
            ModBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            ModBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        check(helper.getLevel().addFreshEntity(cauldron), "failed to add plastic pot");
        return cauldron;
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

    private static int countItem(HardenedResinCauldronEntity cauldron, net.minecraft.world.item.Item item) {
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
