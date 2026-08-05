package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.CatalyticPressLidEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.recipe.CatalyticPressProcess;
import dev.anvilcraft.plasticraft.recipe.PlasticGranuleCauldronOutput;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.block.state.GiantAnvilCube;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.recipe.FluidMixingRecipe;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.ArrayList;
import java.util.List;

/** TODO-00 中保留塑料粒的生产路径及珠宝商交易回归测试。 */
public final class UniversalPlasticProductionGameTests {
    private static final int BUCKET = 1_000;
    private static final int GRANULES = 16;
    private static final int JEWELER_GRANULE_COST = 8;
    private static final int JEWELER_EMERALD_PAYMENT = 2;

    private UniversalPlasticProductionGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x6x7")
    @TestHolder(description = "A catalytic outlet consumes one full water cauldron and one bucket of coloured melt")
    static void catalyticOutletProducesColoredGranules(ExtendedGameTestHelper helper) {
        BlockPos cauldronPos = new BlockPos(2, 2, 3);
        BlockPos outputPos = cauldronPos.east();
        HardenedResinCauldronEntity cauldron = spawnCauldron(helper, cauldronPos);
        CatalyticPressLidEntity lid = spawnReadyLid(helper, cauldronPos.above());
        FluidStack melt = coloredMelt(BUCKET, DyeColor.PURPLE);
        check(
            cauldron.getFluidHandler().fill(melt, IFluidHandler.FluidAction.EXECUTE) == BUCKET,
            "source cauldron rejected the plastic melt"
        );

        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setPos(cauldron.position().add(0.0D, 0.5D, -2.0D));
        player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.ANVIL_HAMMER.asStack());
        check(
            cauldron.plasticraft$useAnvilHammer(player, InteractionHand.MAIN_HAND, Direction.EAST).consumesAction(),
            "anvil hammer did not open the catalytic outlet"
        );
        check(cauldron.getOutletDirection() == Direction.EAST, "catalytic outlet faced the wrong direction");
        helper.setBlock(outputPos, fullWaterCauldron());

        CatalyticPressProcess.press(lid);

        check(cauldron.getFluidHandler().getFluid().isEmpty(), "catalytic outlet did not consume exactly one bucket");
        check(helper.getBlockState(outputPos).is(Blocks.CAULDRON), "catalytic outlet did not consume the full water");
        ItemStack output = singleGranuleDrop(helper, outputPos);
        check(output.getCount() == GRANULES, "catalytic outlet produced " + output.getCount() + " granules");
        check(PlasticMeltColor.get(output) == DyeColor.PURPLE, "catalytic outlet lost the melt colour");
        check(!helper.getBlockState(outputPos).is(PlasticraftBlocks.UNIVERSAL_PLASTIC.get()), "outlet created a plastic block");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x5x7")
    @TestHolder(description = "The water-cauldron transaction consumes exactly one bucket and preserves components")
    static void directCauldronTransactionConsumesExactAmount(ExtendedGameTestHelper helper) {
        BlockPos outputPos = new BlockPos(3, 2, 3);
        helper.setBlock(outputPos, fullWaterCauldron());
        FluidStack melt = coloredMelt(1_500, DyeColor.CYAN);
        CompoundTag marker = melt.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        marker.putBoolean("Preserved", true);
        melt.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        FluidTank source = new FluidTank(2_000);
        check(source.fill(melt, IFluidHandler.FluidAction.EXECUTE) == 1_500, "test tank rejected melt");

        check(
            PlasticGranuleCauldronOutput.tryProcess(
                helper.getLevel(),
                helper.absolutePos(outputPos),
                source,
                source.getFluid().copy()
            ),
            "valid full-water transaction was rejected"
        );

        check(source.getFluidAmount() == 500, "transaction consumed a non-bucket amount of melt");
        check(PlasticMeltColor.get(source.getFluid()) == DyeColor.CYAN, "remaining melt lost its colour");
        check(
            source.getFluid().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean("Preserved"),
            "remaining melt lost an unrelated component"
        );
        ItemStack output = singleGranuleDrop(helper, outputPos);
        check(output.getCount() == GRANULES, "direct transaction produced the wrong granule count");
        check(PlasticMeltColor.get(output) == DyeColor.CYAN, "direct transaction lost the melt colour");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("9x5x7")
    @TestHolder(description = "Insufficient melt and partial water leave both sides unchanged")
    static void cauldronPreflightFailuresPreserveResources(ExtendedGameTestHelper helper) {
        BlockPos insufficientPos = new BlockPos(2, 2, 3);
        BlockPos partialWaterPos = new BlockPos(6, 2, 3);
        helper.setBlock(insufficientPos, fullWaterCauldron());
        helper.setBlock(
            partialWaterPos,
            Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2)
        );
        FluidTank insufficient = filledTank(coloredMelt(999, DyeColor.RED));
        FluidTank partialWater = filledTank(coloredMelt(BUCKET, DyeColor.BLUE));

        check(
            !PlasticGranuleCauldronOutput.tryProcess(
                helper.getLevel(),
                helper.absolutePos(insufficientPos),
                insufficient,
                insufficient.getFluid().copy()
            ),
            "insufficient melt unexpectedly completed the transaction"
        );
        check(
            !PlasticGranuleCauldronOutput.tryProcess(
                helper.getLevel(),
                helper.absolutePos(partialWaterPos),
                partialWater,
                partialWater.getFluid().copy()
            ),
            "partial water unexpectedly completed the transaction"
        );

        check(insufficient.getFluidAmount() == 999, "insufficient transaction consumed melt");
        check(partialWater.getFluidAmount() == BUCKET, "partial-water transaction consumed melt");
        check(helper.getBlockState(insufficientPos).equals(fullWaterCauldron()), "failed transaction consumed water");
        check(
            helper.getBlockState(partialWaterPos).getValue(LayeredCauldronBlock.LEVEL) == 2,
            "failed transaction changed the partial water level"
        );
        check(granuleDrops(helper, new AABB(
            helper.absoluteVec(Vec3.ZERO),
            helper.absoluteVec(new Vec3(9.0D, 5.0D, 7.0D))
        )).isEmpty(), "preflight failure created granules");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x5x7")
    @TestHolder(description = "A simulation-only fluid handler failure rolls back water, items, and partial drainage")
    static void cauldronExecutionFailureRollsBack(ExtendedGameTestHelper helper) {
        BlockPos outputPos = new BlockPos(3, 2, 3);
        helper.setBlock(outputPos, fullWaterCauldron());
        PartialExecutionTank source = new PartialExecutionTank(BUCKET);
        FluidStack melt = coloredMelt(BUCKET, DyeColor.LIME);
        check(source.fill(melt, IFluidHandler.FluidAction.EXECUTE) == BUCKET, "test tank rejected melt");

        check(
            !PlasticGranuleCauldronOutput.tryProcess(
                helper.getLevel(),
                helper.absolutePos(outputPos),
                source,
                source.getFluid().copy()
            ),
            "partial execution unexpectedly committed"
        );

        check(source.getFluidAmount() == BUCKET, "rollback did not restore partially drained melt");
        check(PlasticMeltColor.get(source.getFluid()) == DyeColor.LIME, "rollback changed the melt colour");
        check(helper.getBlockState(outputPos).equals(fullWaterCauldron()), "rollback did not restore full water");
        check(
            granuleDrops(helper, new AABB(helper.absolutePos(outputPos)).inflate(1.0D)).isEmpty(),
            "rollback left a granule item entity"
        );
        helper.succeed();
    }

    private static FluidMixingRecipe fluidMixingRecipe(ExtendedGameTestHelper helper, String name) {
        RecipeHolder<?> holder = helper.getLevel().getRecipeManager()
            .byKey(AnvilcraftPlasticraft.of("fluid_mixing/" + name))
            .orElseThrow(() -> new GameTestAssertException(name + " recipe was not loaded"));
        check(holder.value() instanceof FluidMixingRecipe, name + " did not load as fluid mixing");
        return (FluidMixingRecipe) holder.value();
    }

    private static void assertFluidMixingDefinition(
        FluidMixingRecipe recipe,
        Fluid coolant,
        String name,
        Level level
    ) {
        check(recipe.getFluidIngredients().size() == 2, name + " recipe has the wrong ingredient count");
        check(
            recipe.getFluidIngredients().stream().allMatch(ingredient -> ingredient.amount() == BUCKET),
            name + " recipe does not consume two one-bucket ingredients"
        );
        check(
            recipe.matches(new FluidMixingRecipe.Input(List.of(
                coloredMelt(BUCKET, DyeColor.BROWN),
                new FluidStack(coolant, BUCKET)
            )), level),
            name + " recipe rejected coloured melt and its coolant"
        );
        check(recipe.getItemResults().size() == 1, name + " recipe has the wrong result count");
        ItemStack result = recipe.getItemResults().getFirst();
        check(result.is(PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE.get()), name + " recipe returns the wrong item");
        check(result.getCount() == GRANULES, name + " recipe returns the wrong granule count");
    }

    private static void processLargeCauldron(LargeCauldronBlockEntity cauldron) {
        check(cauldron.getLevel() instanceof ServerLevel, "large cauldron has no server level");
        ServerLevel level = (ServerLevel) cauldron.getLevel();
        BlockPos impactPos = cauldron.getBlockPos().above(3);
        BlockState giantAnvil = ModBlocks.GIANT_ANVIL.getDefaultState()
            .setValue(GiantAnvilBlock.HALF, Cube3x3PartHalf.MID_CENTER)
            .setValue(GiantAnvilBlock.CUBE, GiantAnvilCube.CENTER);
        level.setBlock(impactPos, giantAnvil, Block.UPDATE_ALL);
        check(
            ModBlocks.GIANT_ANVIL.get().getMainPartPos(impactPos, giantAnvil).equals(impactPos),
            "synthetic giant-anvil impact was not placed at its main part"
        );
        FallingBlockEntity impactEntity = new FallingBlockEntity(EntityType.FALLING_BLOCK, level);
        check(
            cauldron.handleGiantAnvilImpact(new AnvilEvent.OnLand(level, impactPos, impactEntity, 1.0F)),
            "large cauldron rejected its giant-anvil impact"
        );
    }

    private static LargeCauldronBlockEntity placeLargeCauldron(
        ExtendedGameTestHelper helper,
        BlockPos relativeBase
    ) {
        Level level = helper.getLevel();
        LargeCauldronBlock block = ModBlocks.LARGE_CAULDRON.get();
        BlockPos base = helper.absolutePos(relativeBase);
        BlockState state = block.defaultBlockState();
        level.setBlock(base, state, Block.UPDATE_ALL);
        block.setPlacedBy(level, base, state, null, ItemStack.EMPTY);
        check(
            level.getBlockEntity(base.above()) instanceof LargeCauldronBlockEntity,
            "large cauldron main block entity was not created"
        );
        return (LargeCauldronBlockEntity) level.getBlockEntity(base.above());
    }

    private static HardenedResinCauldronEntity spawnCauldron(
        ExtendedGameTestHelper helper,
        BlockPos relativePos
    ) {
        BlockPos pos = helper.absolutePos(relativePos);
        Vec3 position = PlasticEntityOrientation.DEFAULT.entityPosition(
            pos,
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get().getWidth(),
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get().getHeight()
        );
        HardenedResinCauldronEntity cauldron = new HardenedResinCauldronEntity(
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get(),
            helper.getLevel(),
            position,
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        cauldron.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(cauldron), "failed to add hardened resin cauldron");
        return cauldron;
    }

    private static HardenedResinAnvilEntity spawnAnvil(
        ExtendedGameTestHelper helper,
        BlockPos relativePos
    ) {
        BlockPos pos = helper.absolutePos(relativePos);
        Vec3 position = PlasticEntityOrientation.DEFAULT.entityPosition(
            pos,
            PlasticraftEntities.HARDEND_RESIN_ANVIL.get().getWidth(),
            PlasticraftEntities.HARDEND_RESIN_ANVIL.get().getHeight()
        );
        HardenedResinAnvilEntity anvil = new HardenedResinAnvilEntity(
            PlasticraftEntities.HARDEND_RESIN_ANVIL.get(),
            helper.getLevel(),
            position,
            PlasticraftBlocks.HARDEND_RESIN_ANVIL.get().defaultBlockState(),
            PlasticraftBlocks.HARDEND_RESIN_ANVIL.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        anvil.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(anvil), "failed to add hardened resin anvil");
        return anvil;
    }

    private static CatalyticPressLidEntity spawnReadyLid(
        ExtendedGameTestHelper helper,
        BlockPos relativePos
    ) {
        BlockPos pos = helper.absolutePos(relativePos);
        Vec3 position = PlasticEntityOrientation.DEFAULT.entityPosition(
            pos,
            CatalyticPressLidEntity.WIDTH,
            CatalyticPressLidEntity.HEIGHT
        );
        CatalyticPressLidEntity lid = new CatalyticPressLidEntity(
            PlasticraftEntities.CATALYTIC_PRESS_LID.get(),
            helper.getLevel(),
            position,
            PlasticraftBlocks.CATALYTIC_PRESS_LID.get().defaultBlockState(),
            PlasticraftBlocks.CATALYTIC_PRESS_LID.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        lid.completeCatalysis();
        check(helper.getLevel().addFreshEntity(lid), "failed to add catalytic press lid");
        return lid;
    }

    private static FluidStack coloredMelt(int amount, DyeColor color) {
        FluidStack melt = new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), amount);
        PlasticMeltColor.set(melt, color);
        return melt;
    }

    private static FluidTank filledTank(FluidStack fluid) {
        FluidTank tank = new FluidTank(Math.max(BUCKET, fluid.getAmount()));
        check(tank.fill(fluid, IFluidHandler.FluidAction.EXECUTE) == fluid.getAmount(), "test tank rejected fluid");
        return tank;
    }

    private static BlockState fullWaterCauldron() {
        return Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3);
    }

    private static ItemStack singleGranuleDrop(ExtendedGameTestHelper helper, BlockPos relativePos) {
        List<ItemStack> drops = granuleDrops(
            helper,
            new AABB(helper.absolutePos(relativePos)).inflate(1.0D)
        );
        check(drops.size() == 1, "expected one granule stack, found " + drops.size());
        return drops.getFirst();
    }

    private static List<ItemStack> granuleDrops(ExtendedGameTestHelper helper, AABB bounds) {
        return helper.getLevel().getEntitiesOfClass(
            ItemEntity.class,
            bounds,
            item -> item.isAlive() && item.getItem().is(PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE.get())
        ).stream().map(ItemEntity::getItem).toList();
    }

    private static void assertInventoryGranules(IItemHandler inventory, DyeColor color, String path) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.is(PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE.get())) continue;
            check(PlasticMeltColor.get(stack) == color, path + " produced a wrong-colour stack");
            count += stack.getCount();
        }
        check(count == GRANULES, path + " produced " + count + " granules");
    }

    private static Int2ObjectMap<List<VillagerTrades.ItemListing>> emptyTradeMap() {
        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = new Int2ObjectOpenHashMap<>();
        for (int level = 1; level <= 5; level++) trades.put(level, new ArrayList<>());
        return trades;
    }

    private static ItemStack coloredGranules(DyeColor color) {
        ItemStack granules = new ItemStack(PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE.get(), GRANULES);
        PlasticMeltColor.set(granules, color);
        return granules;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    /** 模拟阶段守约、执行阶段只排出一半，用于验证跨方块与能力的补偿回滚。 */
    private static final class PartialExecutionTank extends FluidTank {
        private PartialExecutionTank(int capacity) {
            super(capacity);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (action.simulate()) return super.drain(resource, action);
            return super.drain(resource.copyWithAmount(Math.min(resource.getAmount(), BUCKET / 2)), action);
        }
    }
}
