package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.allay.AllayDefaultHardHat;
import dev.anvilcraft.plasticraft.allay.AllayFlightState;
import dev.anvilcraft.plasticraft.allay.AllayHardHats;
import dev.anvilcraft.plasticraft.allay.tool.AllayCapability;
import dev.anvilcraft.plasticraft.allay.tool.AllayToolDefinitions;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.anvilcraft.plasticraft.event.AllayHardHatEvents;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

import java.util.UUID;

/** 覆盖戴帽转换、空手通用工、手持加强、原版游荡、推挤、拴绳与无硬碰撞。 */
public final class AllayGameTests {
    private AllayGameTests() {
    }

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
        Vec3 pos = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
        vanilla.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
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
            vanilla.getBoundingBox().inflate(1.0D)
        ).stream().findFirst().orElse(null);
        check(worker != null, "wearing a hard hat did not create a working allay");
        check(worker.getUUID().equals(id), "working allay did not keep the vanilla UUID");
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
    @TestHolder(description = "An idle hatted allay is not locked to a scripted 5x5x5 wander")
    static void idleWanderStaysInsideFiveCube(ExtendedGameTestHelper helper) {
        WorkingAllayEntity worker = spawnHatted(helper, new Vec3(3.5D, 3.0D, 3.5D), null);
        helper.startSequence()
            .thenExecuteAfter(40, () -> {
                check(worker.isAlive(), "idle allay despawned");
                check(!worker.isCommanded(), "idle allay must stay on vanilla AI");
                check(!worker.navigator().hasPath(), "idle allay must not keep a scripted path");
                check(worker.assignedJobId().isEmpty(), "idle allay must not hold a job lease");
            })
            .thenSucceed();
    }

    static WorkingAllayEntity spawnHatted(
        ExtendedGameTestHelper helper,
        Vec3 relativePos,
        GameTestPlayer owner
    ) {
        Allay vanilla = EntityType.ALLAY.create(helper.getLevel());
        check(vanilla != null, "failed to create vanilla allay");
        Vec3 pos = helper.absoluteVec(relativePos);
        vanilla.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        check(helper.getLevel().addFreshEntity(vanilla), "failed to add vanilla allay");
        WorkingAllayEntity worker = WorkingAllayEntity.convertFrom(
            vanilla,
            AllayDefaultHardHat.stack(),
            owner == null ? null : owner.getUUID()
        );
        return worker;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
