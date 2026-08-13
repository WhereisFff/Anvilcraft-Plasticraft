package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.drone.DroneData;
import dev.anvilcraft.plasticraft.drone.DroneDefaultPropeller;
import dev.anvilcraft.plasticraft.drone.DroneShortageStrategy;
import dev.anvilcraft.plasticraft.drone.tool.DroneToolDefinitions;
import dev.anvilcraft.plasticraft.entity.drone.DroneEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.item.DroneItem;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.recipe.DronePropellerIngredient;
import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 覆盖四种无人机的装配配方、物品与实体数据往返、硬碰撞、堆放和铁砧锤回收。 */
public final class DroneGameTests {
    private DroneGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "All four drone recipes assemble and copy both propeller stacks verbatim")
    static void droneAssemblyCopiesPropellers(ExtendedGameTestHelper helper) {
        ItemStack leftPropeller = propeller(DyeColor.RED);
        ItemStack rightPropeller = propeller(DyeColor.BLUE);
        check(!ItemStack.isSameItemSameComponents(leftPropeller, rightPropeller),
            "test propellers must differ to prove they are not merged");

        record Variant(Item tool, Item result) {
        }
        List<Variant> variants = List.of(
            new Variant(ModItems.CRAB_CLAW.get(), PlasticraftItems.CONSTRUCTION_DRONE.get()),
            new Variant(Items.STONECUTTER, PlasticraftItems.DEMOLITION_DRONE.get()),
            new Variant(ModItems.MAGNET.get(), PlasticraftItems.COLLECTION_DRONE.get()),
            new Variant(Items.SPYGLASS, PlasticraftItems.OBSERVATION_DRONE.get())
        );
        for (Variant variant : variants) {
            CraftingInput input = CraftingInput.of(3, 3, List.of(
                leftPropeller.copy(), new ItemStack(ModItems.IONOCRAFT.get()), rightPropeller.copy(),
                ItemStack.EMPTY, new ItemStack(ModBlocks.MAGNETO_ELECTRIC_CORE_BLOCK.asItem()),
                new ItemStack(ModItems.PROCESSOR.get()),
                new ItemStack(variant.tool()), new ItemStack(ModItems.CAPACITOR.get()), ItemStack.EMPTY
            ));
            Optional<RecipeHolder<CraftingRecipe>> recipe = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
            check(recipe.isPresent(), "no crafting recipe matched the drone assembly layout for "
                + variant.result());
            ItemStack result = recipe.get().value().assemble(input, helper.getLevel().registryAccess());
            check(result.is(variant.result()), "drone assembly produced " + result + " instead of "
                + variant.result());
            DroneData data = DroneData.get(result).orElseThrow(
                () -> new GameTestAssertException("assembled drone has no drone data"));
            check(ItemStack.isSameItemSameComponents(data.leftPropeller(), leftPropeller),
                "left propeller was not copied verbatim into the assembled drone");
            check(ItemStack.isSameItemSameComponents(data.rightPropeller(), rightPropeller),
                "right propeller was not copied verbatim into the assembled drone");
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Drone data survives NBT reload, drop stacks, and anvil-hammer recovery")
    static void droneDataSurvivesReloadAndHammerRecovery(ExtendedGameTestHelper helper) {
        DroneData data = new DroneData(
            DroneToolDefinitions.COLLECTION.id(),
            propeller(DyeColor.LIME),
            propeller(DyeColor.MAGENTA),
            123456,
            Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000042")),
            DroneShortageStrategy.SKIP,
            List.of(new ItemStack(Items.COBBLESTONE, 17)),
            Optional.empty(),
            ItemStack.EMPTY
        );
        DroneEntity drone = spawnDrone(helper, new Vec3(1.5D, 2.0D, 1.5D), data);

        CompoundTag saved = new CompoundTag();
        drone.saveWithoutId(saved);
        DroneEntity reloaded = PlasticraftEntities.DRONE.get().create(helper.getLevel());
        check(reloaded != null, "failed to create drone entity for reload");
        reloaded.load(saved);
        check(equalData(reloaded.toDroneData(), data), "drone data changed after entity NBT reload");

        ItemStack drop = drone.getDropStack();
        check(drop.is(PlasticraftItems.COLLECTION_DRONE.get()),
            "collection drone recovered as the wrong item variant");
        DroneData dropData = DroneData.get(drop).orElseThrow(
            () -> new GameTestAssertException("drop stack has no drone data"));
        check(equalData(dropData, data), "drone data changed inside the drop stack");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ANVIL_HAMMER.get()));
        player.setShiftKeyDown(true);
        InteractionResult result = drone.interact(player, InteractionHand.MAIN_HAND);
        check(result == InteractionResult.CONSUME, "anvil hammer recovery was not consumed");
        check(drone.isRemoved(), "drone entity remained in the world after recovery");

        ItemStack recovered = ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(PlasticraftItems.COLLECTION_DRONE.get())) {
                recovered = stack;
                break;
            }
        }
        check(!recovered.isEmpty(), "recovered collection drone item was not in the player inventory");
        DroneData recoveredData = DroneData.get(recovered).orElseThrow(
            () -> new GameTestAssertException("recovered drone item has no drone data"));
        check(equalData(recoveredData, data), "drone data changed during anvil hammer recovery");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 80)
    @EmptyTemplate(value = "3x4x3", floor = true)
    @TestHolder(description = "Eight drones stack freely inside one block space and stay put")
    static void eightDronesStackInOneBlock(ExtendedGameTestHelper helper) {
        List<DroneEntity> drones = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            double x = 1.25D + (index & 1) * 0.5D;
            double z = 1.25D + ((index >> 1) & 1) * 0.5D;
            double y = 2.0D + ((index >> 2) & 1) * 0.5D;
            drones.add(spawnDrone(
                helper,
                new Vec3(x, y, z),
                DroneData.assembled(
                    DroneToolDefinitions.CONSTRUCTION.id(),
                    ItemStack.EMPTY,
                    ItemStack.EMPTY
                )
            ));
        }
        // 只断言位置稳定性:GameTest 框架清理相邻结构时可能合法丢弃实体,
        // 被丢弃实体保留最后位置,该断言仍能捕捉真实的漂移或穿透。
        AABB allowed = new AABB(helper.absolutePos(new BlockPos(1, 2, 1))).inflate(0.35D, 0.6D, 0.35D);
        helper.onEachTick(() -> {
            for (DroneEntity drone : drones) {
                check(allowed.contains(drone.position()),
                    "a stacked drone drifted out of the shared block space to " + drone.position());
            }
        });
        helper.runAfterDelay(60, helper::succeed);
    }

    @GameTest(timeoutTicks = 60)
    @EmptyTemplate(value = "5x3x3", floor = true)
    @TestHolder(description = "A pushed drone collides with walls instead of passing through")
    static void pushedDroneStopsAtWall(ExtendedGameTestHelper helper) {
        BlockPos wall = new BlockPos(3, 2, 1);
        helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(wall.above(), Blocks.STONE);
        DroneEntity drone = spawnDrone(
            helper,
            new Vec3(1.5D, 2.0D, 1.5D),
            DroneData.assembled(
                DroneToolDefinitions.OBSERVATION.id(),
                ItemStack.EMPTY,
                ItemStack.EMPTY
            )
        );
        // 模拟被推动:直接施加朝墙的水平冲量。碰撞箱断言对框架合法清理免疫,
        // 只捕捉真实的穿墙渗透。
        drone.push(1.4D, 0.0D, 0.0D);
        AABB wallBox = new AABB(helper.absolutePos(wall));
        helper.onEachTick(() -> check(!wallBox.intersects(drone.getBoundingBox()),
            "pushed drone entered the wall block at " + drone.position()));
        helper.runAfterDelay(40, helper::succeed);
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "The embedded default propeller drives the creative entry and picker variants")
    static void defaultPropellerAndCreativePicker(ExtendedGameTestHelper helper) {
        // 默认螺旋桨固化在模组资源中,与世界蓝图库无关。
        ItemStack propeller = DroneDefaultPropeller.stack();
        MoldedPlasticData propellerData = MoldedPlasticData.get(propeller).orElseThrow(
            () -> new GameTestAssertException("default propeller stack has no molded data"));
        check(MoldingProductTypes.PROPELLER_ID.equals(propellerData.finalType()),
            "default propeller is not a propeller type product");
        check(DronePropellerIngredient.INSTANCE.test(propeller),
            "default propeller does not match the recipe ingredient");

        ItemStack source = DroneItem.creativePickerSource();
        check(source.is(PlasticraftItems.DRONE.get()), "creative entry is not the toolless drone");
        DroneData sourceData = DroneData.get(source).orElseThrow(
            () -> new GameTestAssertException("creative entry has no drone data"));
        check(DroneToolDefinitions.NONE.id().equals(sourceData.toolId()),
            "creative entry is not toolless");
        check(ItemStack.isSameItemSameComponents(sourceData.leftPropeller(), propeller),
            "creative entry does not carry the default white propeller");

        List<ItemStack> variants = ((DroneItem) source.getItem()).createCreativePickerVariants(source);
        check(variants.size() == DroneToolDefinitions.values().size(),
            "picker variant count mismatch: " + variants.size());
        check(variants.get(0).is(PlasticraftItems.DRONE.get()), "first picker variant is not toolless");
        check(variants.get(1).is(PlasticraftItems.CONSTRUCTION_DRONE.get()),
            "second picker variant is not the construction drone");
        DroneData constructionData = DroneData.get(variants.get(1)).orElseThrow(
            () -> new GameTestAssertException("construction variant has no drone data"));
        check(ItemStack.isSameItemSameComponents(constructionData.leftPropeller(), sourceData.leftPropeller()),
            "picker variant lost the source propellers");
        helper.succeed();
    }

    private static DroneEntity spawnDrone(ExtendedGameTestHelper helper, Vec3 relativePos, DroneData data) {
        DroneEntity drone = PlasticraftEntities.DRONE.get().create(helper.getLevel());
        check(drone != null, "failed to create drone entity");
        drone.applyDroneData(data);
        Vec3 position = helper.absoluteVec(relativePos);
        drone.setPos(position.x, position.y, position.z);
        check(helper.getLevel().addFreshEntity(drone), "failed to add drone entity");
        return drone;
    }

    /** 构造一个通过成型系统正常制造、类型标记为螺旋桨的塑料制品物品。 */
    private static ItemStack propeller(DyeColor color) {
        EditableMoldingModel model = EditableMoldingModel.empty()
            .withName("Test Propeller " + color.getName())
            .withElements(List.of(
                MoldingElement.cube(
                    "Disc",
                    new MoldingVec3(16.0D, 23.0D, 16.0D),
                    new MoldingVec3(32.0D, 25.0D, 32.0D)
                )
            ));
        BakedMoldingModel baked = MoldingModelBaker.bake(model);
        FluidStack melt = new FluidStack(
            PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(),
            Math.max(250, baked.analysis().volume())
        );
        PlasticMeltColor.set(melt, color);
        MoldedPlasticData manufactured = MoldedPlasticData.manufacture(model, baked, melt, melt.getAmount());
        MoldedPlasticData data = manufactured.withFunction(
            MoldingProductTypes.PROPELLER_ID,
            0,
            manufactured.cavityMask()
        );
        ItemStack stack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(stack, data);
        return stack;
    }

    private static boolean equalData(DroneData actual, DroneData expected) {
        if (!actual.toolId().equals(expected.toolId())
            || !ItemStack.isSameItemSameComponents(actual.leftPropeller(), expected.leftPropeller())
            || !ItemStack.isSameItemSameComponents(actual.rightPropeller(), expected.rightPropeller())
            || actual.energy() != expected.energy()
            || !actual.owner().equals(expected.owner())
            || actual.shortageStrategy() != expected.shortageStrategy()
            || actual.collectionInventory().size() != expected.collectionInventory().size()
            || !actual.assignedJobId().equals(expected.assignedJobId())
            || !ItemStack.isSameItemSameComponents(actual.hostedCarry(), expected.hostedCarry())
            || actual.hostedCarry().getCount() != expected.hostedCarry().getCount()) {
            return false;
        }
        for (int index = 0; index < actual.collectionInventory().size(); index++) {
            ItemStack actualStack = actual.collectionInventory().get(index);
            ItemStack expectedStack = expected.collectionInventory().get(index);
            if (!ItemStack.isSameItemSameComponents(actualStack, expectedStack)
                || actualStack.getCount() != expectedStack.getCount()) {
                return false;
            }
        }
        return true;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
