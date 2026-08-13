package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.dubhe.anvilcraft.api.event.EntityThroughPortalEvent;
import dev.dubhe.anvilcraft.api.portal.PortalType;
import dev.dubhe.anvilcraft.init.block.ModBlockTags;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

/** 塑料实体穿过传送门时不得被 AnvilCraft 转成末地尘。 */
public final class PlasticEntityPortalGameTests {
    private PlasticEntityPortalGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "8x6x8", floor = true)
    @TestHolder(description = "A plastic entity keeps its identity when AnvilCraft handles an end portal")
    static void plasticEntitySurvivesEndPortalConversion(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity plastic = createUniversalPlastic(helper, new Vec3(3.5D, 2.0D, 3.5D));
        BlockState before = plastic.getDisplayState();
        check(before.is(ModBlockTags.END_PORTAL_UNABLE_CHANGE), "plastic display is missing the end-portal exemption tag");
        NeoForge.EVENT_BUS.post(new EntityThroughPortalEvent(
            helper.getLevel(),
            plastic,
            PortalType.END_PORTAL
        ));
        check(!plastic.isRemoved(), "end portal conversion removed the plastic entity");
        check(
            plastic.getDisplayState().equals(before) && plastic.blockState.equals(before),
            "end portal conversion changed the plastic block state"
        );
        check(
            !plastic.blockState.is(ModBlocks.END_DUST.get()),
            "plastic entity was converted into end dust"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "8x6x8", floor = true)
    @TestHolder(description = "A plastic entity keeps its identity when AnvilCraft handles a nether portal")
    static void plasticEntitySurvivesNetherPortalConversion(ExtendedGameTestHelper helper) {
        UniversalPlasticEntity plastic = createUniversalPlastic(helper, new Vec3(3.5D, 2.0D, 3.5D));
        BlockState before = plastic.getDisplayState();
        NeoForge.EVENT_BUS.post(new EntityThroughPortalEvent(
            helper.getLevel(),
            plastic,
            PortalType.NETHER_PORTAL
        ));
        check(!plastic.isRemoved(), "nether portal conversion removed the plastic entity");
        check(
            plastic.getDisplayState().equals(before) && plastic.blockState.equals(before),
            "nether portal conversion changed the plastic block state"
        );
        check(
            !plastic.blockState.is(ModBlocks.NETHER_DUST.get()),
            "plastic entity was converted into nether dust"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "8x6x8", floor = true)
    @TestHolder(description = "An ordinary falling block still becomes end dust in an end portal")
    static void ordinaryFallingBlockStillBecomesEndDust(ExtendedGameTestHelper helper) {
        BlockPos cell = new BlockPos(3, 2, 3);
        helper.setBlock(cell, Blocks.SAND);
        FallingBlockEntity sand = FallingBlockEntity.fall(
            helper.getLevel(),
            helper.absolutePos(cell),
            Blocks.SAND.defaultBlockState()
        );
        NeoForge.EVENT_BUS.post(new EntityThroughPortalEvent(
            helper.getLevel(),
            sand,
            PortalType.END_PORTAL
        ));
        check(sand.blockState.is(ModBlocks.END_DUST.get()), "ordinary falling sand did not become end dust");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "8x6x8", floor = true)
    @TestHolder(description = "A plastic entity restores its display if AnvilCraft overwrites blockState")
    static void plasticEntityRejectsExternalBlockStateMutation(ExtendedGameTestHelper helper) {
        AbstractPlasticEntity plastic = createUniversalPlastic(helper, new Vec3(3.5D, 2.0D, 3.5D));
        BlockState original = plastic.getDisplayState();
        plastic.blockState = ModBlocks.END_DUST.getDefaultState();
        plastic.rejectPortalConversion();
        check(plastic.blockState.equals(original), "plastic entity kept the overwritten blockState");
        check(plastic.getDisplayState().equals(original), "plastic entity changed its display state");
        helper.succeed();
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

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
