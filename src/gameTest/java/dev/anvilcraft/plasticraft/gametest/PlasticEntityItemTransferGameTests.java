package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.dubhe.anvilcraft.block.ChuteBlock;
import dev.dubhe.anvilcraft.block.entity.BaseChuteBlockEntity;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.MinecartHopper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

/** 覆盖实体塑料容器与原版、AnvilCraft 物品运输设备之间的自动化传输。 */
public final class PlasticEntityItemTransferGameTests {
    private PlasticEntityItemTransferGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x5x7", floor = true)
    @TestHolder(description = "A hopper below a plastic cauldron extracts its output")
    static void hopperExtractsPlasticCauldronOutput(ExtendedGameTestHelper helper) {
        BlockPos hopperPos = new BlockPos(3, 1, 3);
        HopperBlockEntity hopper = placeHopper(helper, hopperPos, Direction.DOWN);
        HardenedResinCauldronEntity cauldron = createCauldron(helper, new Vec3(3.5D, 2.0D, 3.5D));
        check(cauldron.insertRecipeOutput(new ItemStack(Items.DIAMOND)).isEmpty(), "failed to fill cauldron output");

        tickHopper(helper, hopperPos, hopper);

        check(hopper.countItem(Items.DIAMOND) == 1, "hopper did not extract the cauldron output");
        check(countItem(cauldron.getItemHandler(), Items.DIAMOND) == 0, "extracted item remained in cauldron");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x5x7", floor = true)
    @TestHolder(description = "A hopper inserts items into a plastic cauldron entity")
    static void hopperInsertsIntoPlasticCauldron(ExtendedGameTestHelper helper) {
        BlockPos hopperPos = new BlockPos(2, 2, 3);
        HopperBlockEntity hopper = placeHopper(helper, hopperPos, Direction.EAST);
        HardenedResinCauldronEntity cauldron = createCauldron(helper, new Vec3(3.5D, 2.0D, 3.5D));
        hopper.setItem(0, new ItemStack(Items.IRON_INGOT));

        tickHopper(helper, hopperPos, hopper);

        check(hopper.countItem(Items.IRON_INGOT) == 0, "hopper retained the inserted item");
        check(countItem(cauldron.getItemHandler(), Items.IRON_INGOT) == 1, "cauldron did not receive hopper input");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x5x7", floor = true)
    @TestHolder(description = "A hopper minecart extracts output from a plastic cauldron entity")
    static void hopperMinecartExtractsPlasticCauldronOutput(ExtendedGameTestHelper helper) {
        HardenedResinCauldronEntity cauldron = createCauldron(helper, new Vec3(3.5D, 2.0D, 3.5D));
        check(cauldron.insertRecipeOutput(new ItemStack(Items.EMERALD)).isEmpty(), "failed to fill cauldron output");
        MinecartHopper minecart = new MinecartHopper(EntityType.HOPPER_MINECART, helper.getLevel());
        Vec3 position = helper.absoluteVec(new Vec3(3.5D, 1.0D, 3.5D));
        minecart.setPos(position.x, position.y, position.z);
        minecart.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(minecart), "failed to add hopper minecart");

        check(minecart.suckInItems(), "hopper minecart reported no transfer");

        check(minecart.countItem(Items.EMERALD) == 1, "hopper minecart did not extract cauldron output");
        check(countItem(cauldron.getItemHandler(), Items.EMERALD) == 0, "extracted item remained in cauldron");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x5x7", floor = true)
    @TestHolder(description = "An AnvilCraft chute extracts output from a plastic cauldron entity")
    static void chuteExtractsPlasticCauldronOutput(ExtendedGameTestHelper helper) {
        BlockPos chutePos = new BlockPos(3, 1, 3);
        BaseChuteBlockEntity chute = placeChute(helper, chutePos);
        HardenedResinCauldronEntity cauldron = createCauldron(helper, new Vec3(3.5D, 2.0D, 3.5D));
        check(cauldron.insertRecipeOutput(new ItemStack(Items.GOLD_INGOT)).isEmpty(), "failed to fill cauldron output");

        chute.tick();

        check(countItem(chute.getItemHandler(), Items.GOLD_INGOT) == 1, "chute did not extract cauldron output");
        check(countItem(cauldron.getItemHandler(), Items.GOLD_INGOT) == 0, "extracted item remained in cauldron");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x5x7", floor = true)
    @TestHolder(description = "An empty plastic cauldron does not block a chute from collecting loose items")
    static void emptyPlasticCauldronDoesNotBlockChuteItemCollection(ExtendedGameTestHelper helper) {
        BlockPos chutePos = new BlockPos(3, 1, 3);
        BaseChuteBlockEntity chute = placeChute(helper, chutePos);
        createCauldron(helper, new Vec3(3.5D, 2.0D, 3.5D));
        Vec3 itemPosition = helper.absoluteVec(new Vec3(3.5D, 2.5D, 3.5D));
        ItemEntity item = new ItemEntity(
            helper.getLevel(),
            itemPosition.x,
            itemPosition.y,
            itemPosition.z,
            new ItemStack(Items.REDSTONE)
        );
        item.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(item), "failed to add loose item");

        chute.tick();

        check(countItem(chute.getItemHandler(), Items.REDSTONE) == 1, "empty cauldron blocked loose item collection");
        helper.succeed();
    }

    private static HopperBlockEntity placeHopper(
        ExtendedGameTestHelper helper,
        BlockPos relativePos,
        Direction facing
    ) {
        BlockState state = Blocks.HOPPER.defaultBlockState()
            .setValue(HopperBlock.FACING, facing)
            .setValue(HopperBlock.ENABLED, true);
        helper.setBlock(relativePos, state);
        check(helper.getBlockEntity(relativePos) instanceof HopperBlockEntity, "hopper block entity was not created");
        return (HopperBlockEntity) helper.getBlockEntity(relativePos);
    }

    private static BaseChuteBlockEntity placeChute(ExtendedGameTestHelper helper, BlockPos relativePos) {
        BlockState state = ModBlocks.CHUTE.get()
            .defaultBlockState()
            .setValue(ChuteBlock.FACING, Direction.DOWN)
            .setValue(ChuteBlock.ENABLED, true);
        helper.setBlock(relativePos, state);
        check(helper.getBlockEntity(relativePos) instanceof BaseChuteBlockEntity, "chute block entity was not created");
        return (BaseChuteBlockEntity) helper.getBlockEntity(relativePos);
    }

    private static void tickHopper(
        ExtendedGameTestHelper helper,
        BlockPos relativePos,
        HopperBlockEntity hopper
    ) {
        BlockPos absolutePos = helper.absolutePos(relativePos);
        HopperBlockEntity.pushItemsTick(
            helper.getLevel(),
            absolutePos,
            helper.getLevel().getBlockState(absolutePos),
            hopper
        );
    }

    private static HardenedResinCauldronEntity createCauldron(
        ExtendedGameTestHelper helper,
        Vec3 relativePosition
    ) {
        Level level = helper.getLevel();
        HardenedResinCauldronEntity cauldron = new HardenedResinCauldronEntity(
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get(),
            level,
            helper.absoluteVec(relativePosition),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        cauldron.setNoGravity(true);
        check(level.addFreshEntity(cauldron), "failed to add hardened resin cauldron");
        return cauldron;
    }

    private static int countItem(IItemHandler handler, Item item) {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
