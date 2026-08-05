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
    @EmptyTemplate(value = "21x5x7", floor = true)
    @TestHolder(description = "Plastic cauldrons exchange items with hoppers, hopper minecarts, and chutes")
    static void plasticCauldronSupportsAllItemTransferPaths(ExtendedGameTestHelper helper) {
        BlockPos hopperExtractPos = new BlockPos(2, 1, 3);
        HopperBlockEntity hopperExtract = placeHopper(helper, hopperExtractPos, Direction.DOWN);
        HardenedResinCauldronEntity extractCauldron = createCauldron(helper, new Vec3(2.5D, 2.0D, 3.5D));
        check(extractCauldron.insertRecipeOutput(new ItemStack(Items.DIAMOND)).isEmpty(),
            "failed to fill hopper output cauldron");
        tickHopper(helper, hopperExtractPos, hopperExtract);
        check(hopperExtract.countItem(Items.DIAMOND) == 1, "hopper did not extract cauldron output");
        check(countItem(extractCauldron.getItemHandler(), Items.DIAMOND) == 0,
            "hopper-extracted item remained in cauldron");

        BlockPos hopperInsertPos = new BlockPos(6, 2, 3);
        HopperBlockEntity hopperInsert = placeHopper(helper, hopperInsertPos, Direction.EAST);
        HardenedResinCauldronEntity insertCauldron = createCauldron(helper, new Vec3(7.5D, 2.0D, 3.5D));
        hopperInsert.setItem(0, new ItemStack(Items.IRON_INGOT));
        tickHopper(helper, hopperInsertPos, hopperInsert);
        check(hopperInsert.countItem(Items.IRON_INGOT) == 0, "hopper retained its inserted item");
        check(countItem(insertCauldron.getItemHandler(), Items.IRON_INGOT) == 1,
            "cauldron did not receive hopper input");

        HardenedResinCauldronEntity minecartCauldron = createCauldron(helper, new Vec3(10.5D, 2.0D, 3.5D));
        check(minecartCauldron.insertRecipeOutput(new ItemStack(Items.EMERALD)).isEmpty(),
            "failed to fill hopper-minecart output cauldron");
        MinecartHopper minecart = new MinecartHopper(EntityType.HOPPER_MINECART, helper.getLevel());
        Vec3 minecartPosition = helper.absoluteVec(new Vec3(10.5D, 1.0D, 3.5D));
        minecart.setPos(minecartPosition.x, minecartPosition.y, minecartPosition.z);
        minecart.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(minecart), "failed to add hopper minecart");
        check(minecart.suckInItems(), "hopper minecart reported no transfer");
        check(minecart.countItem(Items.EMERALD) == 1, "hopper minecart did not extract cauldron output");
        check(countItem(minecartCauldron.getItemHandler(), Items.EMERALD) == 0,
            "hopper-minecart item remained in cauldron");

        BlockPos chuteExtractPos = new BlockPos(14, 1, 3);
        BaseChuteBlockEntity chuteExtract = placeChute(helper, chuteExtractPos);
        HardenedResinCauldronEntity chuteCauldron = createCauldron(helper, new Vec3(14.5D, 2.0D, 3.5D));
        check(chuteCauldron.insertRecipeOutput(new ItemStack(Items.GOLD_INGOT)).isEmpty(),
            "failed to fill chute output cauldron");
        chuteExtract.tick();
        check(countItem(chuteExtract.getItemHandler(), Items.GOLD_INGOT) == 1,
            "chute did not extract cauldron output");
        check(countItem(chuteCauldron.getItemHandler(), Items.GOLD_INGOT) == 0,
            "chute-extracted item remained in cauldron");

        BlockPos chuteCollectPos = new BlockPos(18, 1, 3);
        BaseChuteBlockEntity chuteCollect = placeChute(helper, chuteCollectPos);
        createCauldron(helper, new Vec3(18.5D, 2.0D, 3.5D));
        Vec3 itemPosition = helper.absoluteVec(new Vec3(18.5D, 2.5D, 3.5D));
        ItemEntity item = new ItemEntity(
            helper.getLevel(),
            itemPosition.x,
            itemPosition.y,
            itemPosition.z,
            new ItemStack(Items.REDSTONE)
        );
        item.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(item), "failed to add loose item");
        chuteCollect.tick();
        check(countItem(chuteCollect.getItemHandler(), Items.REDSTONE) == 1,
            "empty cauldron blocked chute item collection");
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
