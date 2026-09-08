package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.allay.AllayDefaultHardHat;
import dev.anvilcraft.plasticraft.allay.AllayFlightState;
import dev.anvilcraft.plasticraft.allay.AllayHardHats;
import dev.anvilcraft.plasticraft.allay.tool.AllayCapability;
import dev.anvilcraft.plasticraft.allay.tool.AllayToolDefinitions;
import dev.anvilcraft.plasticraft.blueprint.ConstructionPermission;
import dev.anvilcraft.plasticraft.blueprint.ConstructionTraffic;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.anvilcraft.plasticraft.event.AllayHardHatEvents;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

import java.util.List;
import java.util.UUID;

/** 覆盖戴帽转换、空手通用工、手持加强、原版游荡、推挤、拴绳、无硬碰撞与局部让行。 */
public final class AllayGameTests {
    private AllayGameTests() {
    }

    static final UUID TEST_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000013");

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "3x4x3", floor = true)
    @TestHolder(description = "A vanilla allay wearing a hard hat becomes a hovering working allay")
    static void hatConvertsVanillaAllay(ExtendedGameTestHelper helper) {
        ItemStack hat = AllayDefaultHardHat.stack();
        check(AllayHardHats.isHardHat(hat), "default hard hat is not recognized");
        check(MoldingProductTypes.ALLAY_HARD_HAT_ID.equals(
            MoldedPlasticData.get(hat).orElseThrow().finalType()),
            "default hard hat is not an allay_hard_hat product");

        Allay vanilla = EntityType.ALLAY.create(helper.getLevel());
        check(vanilla != null, "failed to create vanilla allay");
        // 刻意偏离格心并让三个朝向互不相同:戴帽既不能把悦灵吸到格心,也不能把某一个朝向搬错
        Vec3 pos = helper.absoluteVec(new Vec3(1.37D, 2.35D, 1.62D));
        vanilla.moveTo(pos.x, pos.y, pos.z, 41.0F, 0.0F);
        vanilla.setYBodyRot(53.0F);
        vanilla.setYHeadRot(67.0F);
        vanilla.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.CRAB_CLAW.get()));
        UUID id = vanilla.getUUID();
        check(helper.getLevel().addFreshEntity(vanilla), "failed to add vanilla allay");

        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, hat.copy());
        AllayHardHatEvents.entityInteract(new PlayerInteractEvent.EntityInteract(
            player,
            InteractionHand.MAIN_HAND,
            vanilla
        ));

        WorkingAllayEntity worker = helper.getLevel().getEntitiesOfClass(
            WorkingAllayEntity.class,
            vanilla.getBoundingBox().inflate(1.0D),
            candidate -> candidate.getUUID().equals(id)
        ).stream().findFirst().orElse(null);
        check(worker != null, "wearing a hard hat did not create a working allay keeping the vanilla UUID");
        check(worker.position().distanceToSqr(pos) < 1.0E-9D, "hat conversion left the exact spawn position");
        check(worker.getYRot() == 41.0F, "hat conversion changed the allay facing");
        check(worker.yBodyRot == 53.0F, "hat conversion reset the rendered body facing");
        check(worker.getYHeadRot() == 67.0F, "hat conversion reset the rendered head facing");
        check(worker.flightState() == AllayFlightState.HOVERING, "working allay is not hovering");
        check(!worker.isNoGravity() || worker.flightState() == AllayFlightState.HOVERING,
            "working allay should stay aloft");
        check(worker.isNoGravity(), "working allay should ignore gravity");
        check(!worker.canBeCollidedWith(), "working allay must not have hard collision");
        check(worker.toolDefinition() == AllayToolDefinitions.CONSTRUCTION,
            "held crab claw did not resolve as construction");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "3x4x3", floor = true)
    @TestHolder(description = "Empty hand is a generalist; stonecutter only demolishes and magnet cannot build")
    static void heldToolSelectsJob(ExtendedGameTestHelper helper) {
        WorkingAllayEntity worker = spawnHatted(helper, new Vec3(1.5D, 2.0D, 1.5D), null);
        check(worker.toolDefinition() == AllayToolDefinitions.NONE, "empty hand must resolve as the generalist");
        check(worker.toolDefinition().hasCapability(AllayCapability.PICK_UP_MATERIAL), "empty hand must construct");
        check(worker.toolDefinition().hasCapability(AllayCapability.COLLECT_ITEMS), "empty hand must collect");
        check(!worker.toolDefinition().hasCapability(AllayCapability.DEMOLISH), "empty hand must not demolish");
        check(worker.toolDefinition().reachDistance() == 1.0D, "empty hand reach must be 1");
        check(worker.toolDefinition().inventorySize() == 0, "empty hand must have no cargo bag");

        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.CRAB_CLAW.get()));
        check(worker.toolDefinition() == AllayToolDefinitions.CONSTRUCTION, "crab claw must be the reach upgrade");
        check(worker.toolDefinition().hasCapability(AllayCapability.PICK_UP_MATERIAL), "crab claw must still construct");
        check(!worker.toolDefinition().hasCapability(AllayCapability.COLLECT_ITEMS), "crab claw must not collect");
        check(worker.toolDefinition().reachDistance() == 4.0D, "crab claw reach must be 4");

        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONECUTTER));
        check(worker.toolDefinition() == AllayToolDefinitions.DEMOLITION, "stonecutter must be demolition only");
        check(worker.toolDefinition().hasCapability(AllayCapability.DEMOLISH), "stonecutter must demolish");
        check(!worker.toolDefinition().hasCapability(AllayCapability.PICK_UP_MATERIAL), "stonecutter must not construct");
        check(!worker.toolDefinition().hasCapability(AllayCapability.COLLECT_ITEMS), "stonecutter must not collect");

        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.MAGNET.get()));
        check(worker.toolDefinition() == AllayToolDefinitions.COLLECTION, "magnet must be the vacuum upgrade");
        check(worker.toolDefinition().hasCapability(AllayCapability.COLLECT_ITEMS), "magnet must collect");
        check(!worker.toolDefinition().hasCapability(AllayCapability.PICK_UP_MATERIAL), "magnet must not construct");
        check(worker.toolDefinition().inventorySize() == 9, "magnet must have nine cargo slots");

        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SPYGLASS));
        check(worker.toolDefinition() == AllayToolDefinitions.OBSERVATION, "spyglass must be observation");
        check(!worker.toolDefinition().hasCapability(AllayCapability.PICK_UP_MATERIAL), "spyglass must not construct");
        worker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        worker.setOwner(player.getUUID());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        worker.setHostedCarry(new ItemStack(Items.STONE));
        worker.mobInteract(player, InteractionHand.MAIN_HAND);
        check(worker.getMainHandItem().isEmpty(), "a worker carrying blocks must refuse flint and steel until unloaded");
        worker.setHostedCarry(ItemStack.EMPTY);
        worker.mobInteract(player, InteractionHand.MAIN_HAND);
        check(worker.toolDefinition() == AllayToolDefinitions.IGNITION, "player-given flint and steel must enable ignition");
        check(worker.toolDefinition().hasCapability(AllayCapability.IGNITE), "flint and steel must ignite");
        check(!worker.toolDefinition().hasCapability(AllayCapability.PICK_UP_MATERIAL), "ignition worker must not fetch building materials");
        check(!worker.toolDefinition().hasCapability(AllayCapability.SEAL_FLUID), "ignition worker must not seal fluids");
        check(!worker.canBorrowTool(), "player-given flint and steel must remain a fixed tool");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "3x4x3", floor = true)
    @TestHolder(description = "An idle hatted allay keeps vanilla flight and does not start scripted pathing")
    static void hattedAllayHoversWithoutLanding(ExtendedGameTestHelper helper) {
        WorkingAllayEntity worker = spawnHatted(helper, new Vec3(1.5D, 2.2D, 1.5D), null);
        helper.startSequence()
            .thenExecuteAfter(20, () -> {
                check(worker.isAlive(), "working allay despawned");
                check(!worker.isCommanded(), "idle allay must not be on a scripted job");
                check(!worker.navigator().hasPath(), "idle allay must not start a construction path");
                check(worker.isNoGravity(), "working allay should ignore gravity");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 80)
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "A player shove displaces a hatted allay and it does not snap back")
    static void playerPushDisplacesWithoutSnapback(ExtendedGameTestHelper helper) {
        WorkingAllayEntity worker = spawnHatted(helper, new Vec3(3.5D, 2.2D, 3.5D), null);
        Vec3 start = worker.position();
        worker.push(0.8D, 0.0D, 0.0D);
        helper.startSequence()
            .thenExecuteAfter(40, () -> {
                check(worker.isAlive(), "working allay despawned after a shove");
                check(worker.position().distanceTo(start) > 0.25D, "shoved allay snapped back to the start");
                check(worker.flightState() == AllayFlightState.HOVERING, "shoved allay left hover");
                check(!worker.onGround(), "shoved allay landed");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 80)
    @EmptyTemplate(value = "9x6x5", floor = true)
    @TestHolder(description = "A lead pulls a hatted allay instead of leaving it locked in place")
    static void leadPullsHattedAllay(ExtendedGameTestHelper helper) {
        WorkingAllayEntity worker = spawnHatted(helper, new Vec3(1.5D, 2.2D, 2.5D), null);
        GameTestPlayer player = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.moveTo(worker.getX(), worker.getY(), worker.getZ());
        check(worker.canBeLeashed(), "working allay must accept a lead");
        worker.setLeashedTo(player, true);
        check(worker.isLeashed(), "working allay did not attach to the lead");
        Vec3 start = worker.position();
        player.moveTo(start.x + 6.5D, start.y, start.z);
        helper.startSequence()
            .thenExecuteAfter(40, () -> {
                check(worker.isLeashed(), "working allay dropped the lead");
                check(worker.position().distanceTo(start) > 1.0D, "leashed allay did not follow the player");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 80)
    @EmptyTemplate(value = "7x7x7", floor = true)
    @TestHolder(description = "An idle hatted allay flies on the vanilla move control while a commanded one ignores it")
    static void idleWorkerUsesVanillaFlightControl(ExtendedGameTestHelper helper) {
        WorkingAllayEntity worker = spawnHatted(helper, new Vec3(3.5D, 3.0D, 3.5D), null);
        helper.startSequence()
            .thenExecuteAfter(40, () -> {
                check(worker.isAlive(), "idle allay despawned");
                check(!worker.isCommanded(), "idle allay must stay on vanilla AI");
                check(!worker.navigator().hasPath(), "idle allay must not keep a scripted path");
                check(worker.assignedJobId().isEmpty(), "idle allay must not hold a job lease");
                // 空闲时必须真的吃下原版飞行目标:移动控制器收不到目标点就只会呆在原地
                worker.getMoveControl().setWantedPosition(
                    worker.getX(),
                    worker.getY() + 2.0D,
                    worker.getZ() + 3.0D,
                    1.0D
                );
                worker.getMoveControl().tick();
                check(worker.zza > 0.0F, "idle allay ignored a vanilla flight target");
                check(worker.yya > 0.0F, "idle allay ignored the climb of a vanilla flight target");
            })
            .thenExecute(() -> {
                worker.navigator().setPath(List.of(helper.absoluteVec(new Vec3(5.5D, 3.0D, 5.5D))));
                check(worker.isCommanded(), "a scripted path must count as being commanded");
                worker.getMoveControl().setWantedPosition(
                    worker.getX(),
                    worker.getY() + 2.0D,
                    worker.getZ() + 3.0D,
                    1.0D
                );
                worker.getMoveControl().tick();
                check(worker.zza == 0.0F && worker.yya == 0.0F,
                    "a commanded allay must ignore vanilla flight targets");
                worker.navigator().clear();
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x5x7", floor = true)
    @TestHolder(description = "Head-on allays yield deterministically while active work outranks docking")
    static void headOnWorkersYieldDeterministically(ExtendedGameTestHelper helper) {
        WorkingAllayEntity first = spawnHatted(helper, new Vec3(2.5D, 3.0D, 3.5D), null);
        WorkingAllayEntity second = spawnHatted(helper, new Vec3(4.5D, 3.0D, 3.5D), null);
        first.navigator().setPath(List.of(helper.absoluteVec(new Vec3(5.5D, 3.0D, 3.5D))));
        second.navigator().setPath(List.of(helper.absoluteVec(new Vec3(1.5D, 3.0D, 3.5D))));
        first.setDeltaMovement(new Vec3(0.25D, 0.0D, 0.0D));
        second.setDeltaMovement(new Vec3(-0.25D, 0.0D, 0.0D));

        first.navigator().follow(first);
        second.navigator().follow(second);

        WorkingAllayEntity priority = first.getUUID().compareTo(second.getUUID()) < 0 ? first : second;
        WorkingAllayEntity yielding = priority == first ? second : first;
        check(Math.abs(priority.getDeltaMovement().z) < 1.0E-4D,
            "the priority allay must hold its head-on course");
        check(Math.abs(priority.getDeltaMovement().x) > 0.2D,
            "the priority allay must keep making forward progress");
        check(Math.abs(yielding.getDeltaMovement().z) > 0.05D,
            "the lower-priority allay must take a lateral sidestep");
        double yieldingSide = Math.signum(yielding.getDeltaMovement().z);
        first.navigator().follow(first);
        second.navigator().follow(second);
        check(Math.signum(yielding.getDeltaMovement().z) == yieldingSide,
            "the yielding allay must hold its chosen side instead of oscillating");

        WorkingAllayEntity left = spawnHatted(helper, new Vec3(2.5D, 3.0D, 1.5D), null);
        WorkingAllayEntity right = spawnHatted(helper, new Vec3(4.5D, 3.0D, 1.5D), null);
        WorkingAllayEntity active = left.getUUID().compareTo(right.getUUID()) > 0 ? left : right;
        WorkingAllayEntity docking = active == left ? right : left;
        Vec3 leftGoal = helper.absoluteVec(new Vec3(5.5D, 3.0D, 1.5D));
        Vec3 rightGoal = helper.absoluteVec(new Vec3(1.5D, 3.0D, 1.5D));
        left.navigator().setPath(List.of(leftGoal));
        right.navigator().setPath(List.of(rightGoal));
        left.setDeltaMovement(new Vec3(0.25D, 0.0D, 0.0D));
        right.setDeltaMovement(new Vec3(-0.25D, 0.0D, 0.0D));
        active.setFlightState(AllayFlightState.FLYING);
        docking.setFlightState(AllayFlightState.DOCKING);

        active.navigator().follow(active);
        docking.navigator().follow(docking);

        check(Math.abs(active.getDeltaMovement().z) < 1.0E-4D,
            "an active worker yielded its route to a docking allay");
        check(Math.abs(active.getDeltaMovement().x) > 0.2D,
            "an active worker stopped progressing for a docking allay");
        check(Math.abs(docking.getDeltaMovement().z) > 0.05D,
            "a docking allay did not yield to active work");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 40)
    @EmptyTemplate(value = "3x4x3", floor = true)
    @TestHolder(description = "Only an owner or current teammate can manage a working allay")
    static void workingAllayManagementRechecksTeamMembership(ExtendedGameTestHelper helper) {
        GameTestPlayer owner = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        GameTestPlayer teammate = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        GameTestPlayer stranger = helper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        WorkingAllayEntity worker = spawnHatted(helper, new Vec3(1.5D, 2.0D, 1.5D), owner);

        worker.setHardHat(namedHardHat("initial"));
        owner.setItemInHand(InteractionHand.MAIN_HAND, namedHardHat("owner"));
        worker.mobInteract(owner, InteractionHand.MAIN_HAND);
        check(worker.getHardHat().getHoverName().getString().equals("owner"),
            "the owner could not replace the working allay's hard hat");

        stranger.setItemInHand(InteractionHand.MAIN_HAND, namedHardHat("stranger"));
        worker.mobInteract(stranger, InteractionHand.MAIN_HAND);
        check(worker.getHardHat().getHoverName().getString().equals("owner"),
            "a stranger replaced the working allay's hard hat");

        try {
            ConstructionPermission.setCollaboratorProvider((server, first, second) ->
                first.equals(owner.getUUID()) && second.equals(teammate.getUUID())
                    || first.equals(teammate.getUUID()) && second.equals(owner.getUUID())
            );
            teammate.setItemInHand(InteractionHand.MAIN_HAND, namedHardHat("teammate"));
            worker.mobInteract(teammate, InteractionHand.MAIN_HAND);
            check(worker.getHardHat().getHoverName().getString().equals("teammate"),
                "a current teammate could not manage the working allay");
        } finally {
            ConstructionPermission.setCollaboratorProvider(null);
        }

        teammate.setItemInHand(InteractionHand.MAIN_HAND, namedHardHat("former"));
        worker.mobInteract(teammate, InteractionHand.MAIN_HAND);
        check(worker.getHardHat().getHoverName().getString().equals("teammate"),
            "a former teammate retained working allay access");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x5x7", floor = true)
    @TestHolder(description = "Opposing working allays pass through a one-cell tunnel without reservation or avoidance livelock")
    static void opposingWorkersPassSingleCellTunnel(ExtendedGameTestHelper helper) {
        for (int x = 1; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 2, 2), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 2, 4), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 3, 3), Blocks.STONE);
        }
        WorkingAllayEntity first = spawnHatted(helper, new Vec3(1.5D, 2.0D, 3.5D), null);
        WorkingAllayEntity second = spawnHatted(helper, new Vec3(5.5D, 2.0D, 3.5D), null);
        Vec3 firstGoal = helper.absoluteVec(new Vec3(5.5D, 2.0D, 3.5D));
        Vec3 secondGoal = helper.absoluteVec(new Vec3(1.5D, 2.0D, 3.5D));
        BlockPos corridor = helper.absolutePos(new BlockPos(3, 2, 3));
        ConstructionTraffic.reserveCorridor(helper.getLevel(), first.getUUID(), List.of(corridor));
        check(
            !ConstructionTraffic.isReserved(helper.getLevel(), corridor, second.getUUID()),
            "a narrow-corridor reservation must not become a hard worker-space obstacle"
        );
        first.navigator().setPath(List.of(firstGoal));
        second.navigator().setPath(List.of(secondGoal));
        for (int tick = 0; tick < 24; tick++) {
            first.navigator().follow(first);
            second.navigator().follow(second);
            first.move(MoverType.SELF, first.getDeltaMovement());
            second.move(MoverType.SELF, second.getDeltaMovement());
        }
        ConstructionTraffic.release(helper.getLevel(), first.getUUID());
        ConstructionTraffic.release(helper.getLevel(), second.getUUID());
        check(first.getX() > second.getX(), "opposing allays did not pass each other inside the tunnel");
        check(first.position().distanceTo(firstGoal) < 0.4D, "first allay did not clear the tunnel");
        check(second.position().distanceTo(secondGoal) < 0.4D, "second allay did not clear the tunnel");
        helper.succeed();
    }

    static WorkingAllayEntity spawnHatted(
        ExtendedGameTestHelper helper,
        Vec3 relativePos,
        GameTestPlayer owner
    ) {
        return spawnHattedForOwner(helper, relativePos, owner == null ? null : owner.getUUID());
    }

    static WorkingAllayEntity spawnHattedForOwner(
        ExtendedGameTestHelper helper,
        Vec3 relativePos,
        UUID ownerId
    ) {
        Allay vanilla = EntityType.ALLAY.create(helper.getLevel());
        check(vanilla != null, "failed to create vanilla allay");
        Vec3 pos = helper.absoluteVec(relativePos);
        vanilla.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        check(helper.getLevel().addFreshEntity(vanilla), "failed to add vanilla allay");
        WorkingAllayEntity worker = WorkingAllayEntity.convertFrom(
            vanilla,
            AllayDefaultHardHat.stack(),
            ownerId
        );
        return worker;
    }

    private static ItemStack namedHardHat(String name) {
        ItemStack hat = AllayDefaultHardHat.stack();
        hat.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return hat;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
