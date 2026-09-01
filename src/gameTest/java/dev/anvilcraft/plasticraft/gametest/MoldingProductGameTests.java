package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.entity.MoldedPlasticCauldronState;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.anvilcraft.plasticraft.molding.bake.MoldingBarrierFace;
import dev.anvilcraft.plasticraft.molding.bake.MoldingAnvilShapeAnalysis;
import dev.anvilcraft.plasticraft.molding.bake.MoldingAnvilShapeAnalyzer;
import dev.anvilcraft.plasticraft.molding.bake.MoldingCauldronShapeAnalysis;
import dev.anvilcraft.plasticraft.molding.bake.MoldingCauldronShapeAnalyzer;
import dev.anvilcraft.plasticraft.molding.bake.MoldingFaceDirection;
import dev.anvilcraft.plasticraft.molding.bake.MoldingFunctionalAnalysis;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.bake.MoldingQuad;
import dev.anvilcraft.plasticraft.molding.bake.MoldingShellAnalyzer;
import dev.anvilcraft.plasticraft.molding.bake.MoldingVolumeMask;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelBounds;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContentSummary;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContents;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticFluidHandler;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticItemHandler;
import dev.anvilcraft.plasticraft.molding.product.MoldedTankFluidGeometry;
import dev.anvilcraft.plasticraft.molding.product.PlasticCauldronLayout;
import dev.anvilcraft.plasticraft.molding.product.storage.MoldedPlasticStorage;
import dev.anvilcraft.plasticraft.molding.product.storage.MoldedPlasticStorageHandle;
import dev.anvilcraft.plasticraft.molding.product.storage.PlasticraftStorages;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductPreview;
import dev.anvilcraft.plasticraft.molding.type.MoldingHardHatIconModel;
import dev.anvilcraft.plasticraft.molding.type.MoldingTypeValidation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** TODO-06 功能类型、容量派生和无菜单能力的服务端回归测试。 */
public final class MoldingProductGameTests {
    private MoldingProductGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Chest, tank and cauldron capacity boundaries remain exact")
    static void capacityBoundaries(ExtendedGameTestHelper helper) {
        checkCapacity(helper, MoldingProductTypes.CHEST_ID, 1728, 27, true);
        checkCapacity(helper, MoldingProductTypes.CHEST_ID, 64, 1, true);
        checkCapacity(helper, MoldingProductTypes.CHEST_ID, 63, 0, false);
        checkCapacity(helper, MoldingProductTypes.TANK_ID, 2744, 16, true);
        checkCapacity(helper, MoldingProductTypes.TANK_ID, 171, 1, true);
        checkCapacity(helper, MoldingProductTypes.TANK_ID, 170, 0, false);
        // 炼药锅只按整 B 计量：不足 1 B 也算 1 B，之后每凑满一个 1728 px³ 才多 1 B
        checkCauldronCapacity(stubbedCauldron(12, 12), 1727, 1);
        checkCauldronCapacity(squareCauldron(12, 12), 1728, 1);
        checkCauldronCapacity(stubbedCauldron(12, 24), 3455, 1);
        checkCauldronCapacity(squareCauldron(12, 24), 3456, 2);
        checkCauldronCapacity(stubbedCauldron(12, 36), 5183, 2);
        checkCauldronCapacity(squareCauldron(12, 36), 5184, 3);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Cauldrons need a 144 square pixel opening and 8 pixel deep walls, and upgrade past 1600 square pixels")
    static void cauldronShapeBoundaries(ExtendedGameTestHelper helper) {
        // 原版炼药锅内腔 12×12×12：开口 144 px²、深 12 px，正好压在开口与深度两条阈值上
        MoldingCauldronShapeAnalysis vanilla = analyzeCauldron(squareCauldron(12, 12));
        check(vanilla.valid(), "vanilla-shaped cauldron cavity was rejected: " + vanilla.reason());
        check(vanilla.cavityVolume() == 1728 && vanilla.openingArea() == 144 && vanilla.depth() == 12,
            "vanilla-shaped cauldron measured " + vanilla.cavityVolume() + " cubic pixels, opening "
                + vanilla.openingArea() + " and depth " + vanilla.depth());
        check(!vanilla.large(), "a 144 square pixel opening upgraded to a large cauldron");

        // 开口按暴露的像素列数计，不看外接矩形：内腔立柱只吃掉一列，外接矩形仍是 12×12
        checkCauldronRejected(cauldronMask(pillarInterior(12), 12), "cauldron_opening_too_small",
            "a 143 pixel opening whose bounding rectangle is still 12x12");
        checkCauldronRejected(cauldronMask(rectangleInterior(11, 13), 12), "cauldron_opening_too_small",
            "an 11x13 opening");

        // 深度取内腔的整体高度，等价于最矮的一面侧壁
        check(analyzeCauldron(squareCauldron(12, 8)).depth() == 8,
            "8 pixel walls did not measure 8 pixels of depth");
        checkCauldronRejected(squareCauldron(12, 7), "cauldron_too_shallow", "7 pixel walls");

        // 侧壁允许不等高，内腔在最矮的那面封顶
        MoldingCauldronShapeAnalysis uneven = analyzeCauldron(unevenWallCauldron(12, 12, 8));
        check(uneven.valid(), "unevenly walled cauldron was rejected: " + uneven.reason());
        check(uneven.cavityVolume() == 1152 && uneven.openingArea() == 144 && uneven.depth() == 8,
            "unevenly walled cauldron measured " + uneven.cavityVolume() + " cubic pixels, opening "
                + uneven.openingArea() + " and depth " + uneven.depth());
        checkCauldronRejected(unevenWallCauldron(12, 12, 7), "cauldron_too_shallow", "a 7 pixel low wall");

        // 侧壁底层漏一格，内腔就顺着漏孔与外界连通，什么都留不住
        checkCauldronRejected(leakingCauldron(12, 12), "cauldron_not_sealed", "a side wall leak");

        // 圆形开口同样只看面积：直径 14 px 有 156 px²，直径 13 px 只有 137 px²
        MoldingCauldronShapeAnalysis disc = analyzeCauldron(cauldronMask(discInterior(14), 12));
        check(disc.valid(), "a 14 pixel wide round cauldron was rejected: " + disc.reason());
        check(disc.openingArea() == 156, "round cauldron opening measured " + disc.openingArea());
        checkCauldronRejected(cauldronMask(discInterior(13), 12), "cauldron_opening_too_small",
            "a 13 pixel wide round opening");

        // 升级线是严格大于 1600 px²
        MoldingCauldronShapeAnalysis exact = analyzeCauldron(cauldronMask(rectangleInterior(40, 40), 8));
        check(exact.valid() && exact.openingArea() == 1600 && !exact.large(),
            "a 1600 square pixel opening upgraded to a large cauldron: opening " + exact.openingArea()
                + ", large=" + exact.large());
        MoldingTypeValidation exactNormal = MoldingProductTypes.validate(
            MoldingProductTypes.CAULDRON_ID,
            cauldronAnalysis(exact)
        );
        check(exactNormal.valid() && exactNormal.capacity() == 7,
            "a 12800 cubic pixel cavity did not hold 7B: " + exactNormal.capacity());
        MoldingTypeValidation refusedLarge = MoldingProductTypes.validate(
            MoldingProductTypes.LARGE_CAULDRON_ID,
            cauldronAnalysis(exact)
        );
        check(!refusedLarge.valid() && "cauldron_not_large".equals(refusedLarge.reason()),
            "the large cauldron type accepted a 1600 square pixel opening: " + refusedLarge.reason());

        MoldingCauldronShapeAnalysis bumped = analyzeCauldron(cauldronMask(bumpedInterior(40), 8));
        check(bumped.valid() && bumped.openingArea() == 1601 && bumped.large(),
            "a 1601 square pixel opening did not upgrade: opening " + bumped.openingArea()
                + ", large=" + bumped.large());
        MoldingTypeValidation large = MoldingProductTypes.validate(
            MoldingProductTypes.LARGE_CAULDRON_ID,
            cauldronAnalysis(bumped)
        );
        check(large.valid() && large.capacity() == 512,
            "the large cauldron type did not fix its capacity at 512B: " + large.capacity());

        // 完整模型走一遍预览与制造：请求普通锅，开口够大时算升级而不是降级
        EditableMoldingModel largeCauldron = cauldronModel(MoldingProductTypes.CAULDRON_ID, 2, 41, 8);
        var baked = MoldingModelBaker.bake(largeCauldron);
        MoldingProductPreview preview = MoldingProductPreview.evaluate(
            largeCauldron,
            baked,
            baked.analysis().minimumMeltMillibuckets()
        );
        check(MoldingProductTypes.LARGE_CAULDRON_ID.equals(preview.finalType()),
            "a 1681 square pixel model did not preview as a large cauldron: " + preview.finalType());
        check(preview.upgraded() && !preview.downgraded(),
            "the large cauldron upgrade was reported as a downgrade");
        check(preview.capacity() == 512,
            "the previewed large cauldron did not hold 512B: " + preview.capacity());
        MoldedPlasticData data = completeData(largeCauldron);
        check(MoldingProductTypes.LARGE_CAULDRON_ID.equals(data.finalType()) && data.capacity() == 512,
            "manufacturing dropped the large cauldron upgrade: " + data.finalType() + " " + data.capacity());
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Inward-facing cubes can be contained cavities or walls for chests, tanks and cauldrons")
    static void negativeCubesFillContainerCavities(ExtendedGameTestHelper helper) {
        for (ResourceLocation type : List.of(MoldingProductTypes.CHEST_ID, MoldingProductTypes.TANK_ID)) {
            checkNegativeSealedCavity(type, false);
            checkNegativeSealedCavity(type, true);
            checkNegativeSealedWalls(type);
        }
        checkNegativeCauldronCavity(false, false);
        checkNegativeCauldronCavity(true, false);
        checkNegativeCauldronCavity(false, true);
        checkNegativeCauldronWalls();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Anvil type accepts the three-section profile, minimum base, projection/giant bounds, and reversed Ember outline")
    static void anvilShapeBoundaries(ExtendedGameTestHelper helper) {
        MoldingAnvilShapeAnalysis valid = MoldingAnvilShapeAnalyzer.analyze(anvilMask(
            3,
            3,
            5,
            16,
            10,
            18
        ));
        check(valid.valid(), "valid anvil profile was rejected: " + valid);
        check(MoldingAnvilShapeAnalyzer.analyze(expandedBottomUpperLayers()).valid(),
            "valid bottom-section profile with an expanded upper layer was rejected");
        MoldingFunctionalAnalysis functional = new MoldingFunctionalAnalysis(
            new MoldingVolumeMask(),
            0,
            0,
            0,
            false,
            valid
        );
        check(MoldingProductTypes.validate(MoldingProductTypes.ANVIL_ID, functional).valid(),
            "anvil product type rejected a valid shape");

        EditableMoldingModel anvilModel = anvilModel(MoldingProductTypes.ANVIL_ID);
        var baked = MoldingModelBaker.bake(anvilModel);
        check(baked.functionalAnalysis().anvilShape().valid(),
            "baked anvil model did not retain its shape analysis: " + baked.functionalAnalysis().anvilShape());
        MoldingProductPreview preview = MoldingProductPreview.evaluate(
            anvilModel,
            baked,
            baked.analysis().minimumMeltMillibuckets()
        );
        check(!preview.downgraded() && MoldingProductTypes.ANVIL_ID.equals(preview.finalType()),
            "complete baked anvil model was downgraded: " + preview);

        check(MoldingAnvilShapeAnalyzer.analyze(anvilMask(3, 3, 5, 12, 10, 18)).valid(),
            "12 x 12 bottom did not pass the minimum rule");
        check(!MoldingAnvilShapeAnalyzer.analyze(anvilRectMask(11, 12, 9, 10, 18, 18)).valid(),
            "11 x 12 bottom passed the 12 x 12 rule");
        check(!MoldingAnvilShapeAnalyzer.analyze(anvilMask(3, 2, 5, 16, 10, 18)).valid(),
            "2 px middle passed the thickness rule");
        check(!MoldingAnvilShapeAnalyzer.analyze(anvilMask(3, 3, 5, 16, 16, 18)).valid(),
            "middle projection with an equal face passed");
        check(MoldingAnvilShapeAnalyzer.analyze(anvilMask(3, 3, 5, 16, 10, 16)).valid(),
            "top projection with equal area was rejected");
        check(!MoldingAnvilShapeAnalyzer.analyze(anvilMask(4, 3, 4, 16, 10, 18)).valid(),
            "top segment no thicker than bottom passed");

        check(MoldingAnvilShapeAnalyzer.analyze(anvilRectMask(15, 15, 9, 9, 11, 19)).valid(),
            "top projection exactly 16 square pixels smaller than the bottom was rejected");
        check(!MoldingAnvilShapeAnalyzer.analyze(anvilRectMask(15, 15, 9, 9, 13, 16)).valid(),
            "top projection 17 square pixels smaller than the bottom was accepted");
        MoldingAnvilShapeAnalysis belowBoundary = MoldingAnvilShapeAnalyzer.analyze(
            anvilRectMask(39, 40, 30, 30, 41, 42)
        );
        check(belowBoundary.valid(), "39 x 40 anvil profile was rejected");
        check(!belowBoundary.giant(), "39 x 40 bottom gained giant-anvil ability");
        MoldingAnvilShapeAnalysis boundary = MoldingAnvilShapeAnalyzer.analyze(
            anvilRectMask(40, 40, 32, 32, 42, 42)
        );
        check(boundary.valid(), "40 x 40 anvil profile was rejected");
        check(boundary.giant(), "40 x 40 bottom did not gain giant-anvil ability");

        EditableMoldingModel emberAnvil = emberAnvilModel(true);
        var emberBaked = MoldingModelBaker.bake(emberAnvil);
        check(emberBaked.functionalAnalysis().anvilShape().valid(),
            "ember-anvil reversed bottom outline was rejected: " + emberBaked.functionalAnalysis().anvilShape());
        check(MoldingProductTypes.validate(MoldingProductTypes.ANVIL_ID, emberAnvil, emberBaked).valid(),
            "ember-anvil could not be assigned the anvil type");
        check(MoldingModelBaker.bake(emberAnvilModel(false)).functionalAnalysis().anvilShape().valid(),
            "ordinary 12 x 12 bottom did not pass the minimum rule");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Hard hats accept the bundled icon and reject models wider than 11 px or taller than 16 px")
    static void hardHatSurfaceAndHeightBoundaries(ExtendedGameTestHelper helper) {
        checkHardHatValid(MoldingHardHatIconModel.model(), "bundled hard hat icon");
        checkHardHatValid(centerHat(8.0D), "eight-pixel-tall hat");
        checkHardHatValid(centerHat(16.0D), "sixteen-pixel-tall hat");
        checkHardHatValid(offCenterHat(), "off-center hat inside the 11 x 11 footprint");
        checkHardHatReason(centerHat(16.001D), "allay_hard_hat_too_tall");
        checkHardHatReason(tooWideHat(), "allay_hard_hat_too_wide");
        checkHardHatReason(emptyHat(), "allay_hard_hat_empty");

        EditableMoldingModel icon = MoldingHardHatIconModel.model();
        MoldingModelBounds bounds = MoldingModelBounds.visible(icon).orElseThrow();
        check(bounds.minimum().equals(vec(18.5, 22, 18.5)) && bounds.maximum().equals(vec(29.5, 28, 29.5)),
            "bundled hard hat icon bounds drifted: " + bounds);
        check(icon.elements().size() == 2, "bundled hard hat icon no longer has two cubes");
        checkIconElement(icon, icon.elements().get(0), vec(18.5, 22, 18.5), vec(29.5, 23, 29.5));
        checkIconElement(icon, icon.elements().get(1), vec(20, 23, 20), vec(28, 28, 28));
        for (MoldingElement element : icon.elements()) {
            MoldingVec3 pivot = MoldingModelBaker.transformedPoint(
                icon,
                element,
                element.transform().pivot()
            );
            check(pivot.equals(vec(24, 22, 24)),
                "bundled hard hat icon element has a different common pivot: " + pivot);
        }
        helper.succeed();
    }

    private static void checkIconElement(
        EditableMoldingModel model,
        MoldingElement element,
        MoldingVec3 expectedFrom,
        MoldingVec3 expectedTo
    ) {
        MoldingVec3 actualFrom = MoldingModelBaker.transformedPoint(model, element, element.from());
        MoldingVec3 actualTo = MoldingModelBaker.transformedPoint(model, element, element.to());
        check(actualFrom.equals(expectedFrom) && actualTo.equals(expectedTo),
            "bundled hard hat icon cube drifted from the complete model: "
                + element.name() + " " + actualFrom + " -> " + actualTo);
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Partial molding revalidates functional types before manufacture")
    static void partialTypeDowngrade(ExtendedGameTestHelper helper) {
        EditableMoldingModel model = largeModel(MoldingProductTypes.CHEST_ID);
        var baked = MoldingModelBaker.bake(model);
        MoldingProductPreview complete = MoldingProductPreview.evaluate(
            model,
            baked,
            baked.analysis().minimumMeltMillibuckets()
        );
        check(!complete.downgraded() && complete.capacity() == 64,
            "complete shell did not retain chest type: " + complete);
        MoldingProductPreview partial = MoldingProductPreview.evaluate(model, baked, 250);
        check(partial.downgraded(), "partial open shell retained chest type");
        MoldedPlasticData result = MoldedPlasticData.manufacture(
            model,
            baked,
            new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), 250),
            250
        );
        check(MoldingProductTypes.NORMAL_ID.equals(result.finalType()) && result.capacity() == 0,
            "manufactured partial result was not downgraded");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Creative override derives forced storage capacity from the complete outer bounds")
    static void creativeOverrideUsesCompleteBoundingVolume(ExtendedGameTestHelper helper) {
        EditableMoldingModel model = new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Creative oversized chest",
            MoldingProductTypes.CHEST_ID,
            List.of(MoldingElement.cube("Solid", vec(0, 0, 0), vec(100, 100, 100))),
            List.of()
        );
        var baked = MoldingModelBaker.bake(model);
        check(baked.volumeMask().sizeX() == 100
                && baked.volumeMask().sizeY() == 100
                && baked.volumeMask().sizeZ() == 100,
            "oversized model did not preserve its 100 px manufacturing bounds");
        check(!MoldingProductTypes.validate(MoldingProductTypes.CHEST_ID, baked.functionalAnalysis()).valid(),
            "solid oversized cube unexpectedly passed ordinary chest validation");

        MoldingProductPreview preview = MoldingProductPreview.evaluate(model, baked, 0, false, true);
        check(MoldingProductTypes.CHEST_ID.equals(preview.finalType()),
            "creative override did not retain the forced chest type");
        check(preview.capacity() == 15_625,
            "creative override did not derive 1,000,000 / 64 chest slots: " + preview.capacity());
        check(preview.analysis().cavityVolume() == 1_000_000,
            "creative override did not treat the complete outer bounds as usable space");
        check(preview.formedVolume().volume() == 1_000_000,
            "creative override did not manufacture the complete model without melt");

        MoldedPlasticData product = MoldedPlasticData.manufacture(
            model,
            baked,
            FluidStack.EMPTY,
            0,
            false,
            true
        );
        check(MoldingProductTypes.CHEST_ID.equals(product.finalType()) && product.capacity() == 15_625,
            "manufactured creative chest lost its forced type or maximum capacity");
        check(product.limitOverride(), "manufactured creative chest lost its limit-override marker");
        check(product.volumeMask().sizeX() == 100
                && product.volumeMask().sizeY() == 100
                && product.volumeMask().sizeZ() == 100,
            "manufactured creative chest lost its oversized dimensions");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Forced tanks retain fluid storage without rendering internal fluid geometry")
    static void forcedTankStoresFluidWithoutRendering(ExtendedGameTestHelper helper) {
        EditableMoldingModel model = new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Forced solid tank",
            MoldingProductTypes.TANK_ID,
            List.of(MoldingElement.cube("Solid", vec(0, 0, 0), vec(8, 8, 8))),
            List.of()
        );
        var baked = MoldingModelBaker.bake(model);
        check(!MoldingProductTypes.validate(MoldingProductTypes.TANK_ID, baked.functionalAnalysis()).valid(),
            "solid tank unexpectedly passed ordinary validation");

        MoldedPlasticData forcedTank = MoldedPlasticData.manufacture(
            model,
            baked,
            new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), 250),
            250,
            true,
            false
        );
        check(forcedTank.limitOverride(), "type-overridden tank lost its limit-override marker");
        check(forcedTank.capacity() == 2, "forced tank did not derive 512 / 171 buckets of capacity");
        final MoldedPlasticData[] stored = {forcedTank};
        MoldedPlasticFluidHandler fluids = new MoldedPlasticFluidHandler(
            () -> Optional.of(stored[0]),
            replacement -> stored[0] = replacement
        );
        check(fluids.fill(new FluidStack(Fluids.WATER, 1500), IFluidHandler.FluidAction.EXECUTE) == 1500,
            "forced tank did not retain its fluid capability");
        check(stored[0].limitOverride(), "updating forced tank contents cleared its limit-override marker");
        check(stored[0].storageId().isPresent(), "forced tank did not allocate an external storage id");
        check(stored[0].contents().fluids().isEmpty(),
            "forced tank kept a render fluid snapshot after inserting fluid");
        check(handleFluids(stored).getFirst().getAmount() == 1500,
            "forced tank did not persist the inserted fluid");
        check(MoldedTankFluidGeometry.solve(stored[0], new Vec3(0.0D, 1.0D, 0.0D)).isEmpty(),
            "forced tank generated internal fluid or free-surface geometry");
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var encoded = MoldedPlasticData.CODEC.encodeStart(ops, stored[0]).getOrThrow();
        MoldedPlasticData decoded = MoldedPlasticData.CODEC.parse(ops, encoded).getOrThrow();
        check(decoded.limitOverride(), "forced tank codec round trip cleared its limit-override marker");
        check(decoded.storageId().equals(stored[0].storageId()),
            "forced tank codec round trip changed its storage id");
        check(decoded.contents().fluids().isEmpty(),
            "forced tank codec round trip restored a render fluid snapshot");
        final MoldedPlasticData[] decodedHolder = {decoded};
        check(handleFluids(decodedHolder).getFirst().getAmount() == 1500,
            "forced tank codec round trip lost the stored fluid");
        check(MoldedTankFluidGeometry.solve(decoded, new Vec3(0.0D, 1.0D, 0.0D)).isEmpty(),
            "decoded forced tank generated internal fluid or free-surface geometry");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Padding flood scan accepts sealed zero-thickness shells and rejects decorations")
    static void shellAndDecorationBoundaries(ExtendedGameTestHelper helper) {
        MoldingVolumeMask shell = hollowCube(10);
        MoldingFunctionalAnalysis analysis = MoldingShellAnalyzer.analyze(shell, Set.of());
        check(analysis.cavityVolume() == 512,
            "10 cube shell did not expose an 8 cube cavity: " + analysis.cavityVolume()
                + ", shell=" + analysis.shellVolume() + ", cavities=" + analysis.cavityCount());
        check(!analysis.internalDecoration(), "ordinary shell was marked as decorated");
        MoldingVolumeMask decorated = shell.copy();
        decorated.set(4, 4, 4);
        MoldingFunctionalAnalysis decoratedAnalysis = MoldingShellAnalyzer.analyze(decorated, Set.of());
        check(decoratedAnalysis.internalDecoration(), "isolated cavity decoration was accepted");
        MoldingVolumeMask protruding = shell.copy();
        for (int y = 4; y < 6; y++) {
            for (int z = 4; z < 6; z++) protruding.set(1, y, z);
        }
        MoldingFunctionalAnalysis protrudingAnalysis = MoldingShellAnalyzer.analyze(protruding, Set.of());
        check(protrudingAnalysis.internalDecoration(), "cavity-facing protrusion was accepted as shell");

        Set<MoldingBarrierFace> barriers = new HashSet<>();
        barriers.add(new MoldingBarrierFace(MoldingFaceDirection.Axis.X, 0, 0, 0));
        barriers.add(new MoldingBarrierFace(MoldingFaceDirection.Axis.X, 1, 0, 0));
        barriers.add(new MoldingBarrierFace(MoldingFaceDirection.Axis.Y, 0, 0, 0));
        barriers.add(new MoldingBarrierFace(MoldingFaceDirection.Axis.Y, 1, 0, 0));
        barriers.add(new MoldingBarrierFace(MoldingFaceDirection.Axis.Z, 0, 0, 0));
        barriers.add(new MoldingBarrierFace(MoldingFaceDirection.Axis.Z, 1, 0, 0));
        MoldingFunctionalAnalysis flat = MoldingShellAnalyzer.analyze(new MoldingVolumeMask(), barriers);
        check(flat.cavityVolume() == 1 && flat.shellVolume() == 0, "zero-thickness shell was not sealed");

        EditableMoldingModel zeroSealed = zeroThicknessSealedModel(MoldingProductTypes.TANK_ID);
        var zeroBaked = MoldingModelBaker.bake(zeroSealed);
        check(!zeroBaked.barrierFaces().isEmpty(), "zero-thickness seal produced no barriers");
        check(zeroBaked.functionalAnalysis().cavityVolume() == 512,
            "zero-thickness seal exposed the wrong cavity volume: "
                + zeroBaked.functionalAnalysis().cavityVolume());
        Set<MoldingBarrierFace> reconstructed = MoldingModelBaker.barrierFacesFromZeroThickness(
            zeroBaked.surfaceMesh().stream().filter(MoldingQuad::doubleSided).toList()
        );
        check(reconstructed.equals(zeroBaked.barrierFaces()), "persisted zero-thickness barriers changed on rebuild");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Chest and tank contents survive capability round trips")
    static void capabilityRoundTrips(ExtendedGameTestHelper helper) {
        EditableMoldingModel chestModel = model(MoldingProductTypes.CHEST_ID);
        MoldedPlasticData chestData = fullData(chestModel);
        final MoldedPlasticData[] chest = {chestData};
        MoldedPlasticItemHandler items = new MoldedPlasticItemHandler(
            () -> Optional.of(chest[0]),
            replacement -> chest[0] = replacement
        );
        check(items.getSlots() == 8,
            "chest capability did not expose derived slots: type=" + chestData.finalType()
                + ", capacity=" + chestData.capacity());
        check(items.insertItem(0, new ItemStack(Items.DIAMOND, 3), false).isEmpty(),
            "chest capability rejected a valid item");
        check(items.getStackInSlot(0).getCount() == 3, "chest item was not retained");
        check(chest[0].storageId().isPresent(), "chest capability did not allocate an external storage id");
        check(chest[0].contents().items().isEmpty(), "chest capability kept inline item contents");
        check(chest[0].summary().occupiedSlots() == 1, "chest capability did not refresh its content summary");

        EditableMoldingModel tankModel = model(MoldingProductTypes.TANK_ID);
        MoldedPlasticData tankData = fullData(tankModel);
        check(!tankData.limitOverride(), "ordinary tank was marked as limit-overridden");
        final MoldedPlasticData[] tank = {tankData};
        MoldedPlasticFluidHandler fluids = new MoldedPlasticFluidHandler(
            () -> Optional.of(tank[0]),
            replacement -> tank[0] = replacement
        );
        check(fluids.fill(new FluidStack(Fluids.WATER, 1500), IFluidHandler.FluidAction.EXECUTE) == 1500,
            "tank did not accept the first fluid");
        check(fluids.fill(new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), 600), IFluidHandler.FluidAction.EXECUTE) == 500,
            "tank did not enforce shared capacity");
        check(tank[0].contents().fluids().size() == 2, "tank did not preserve fluid order");
        check(fluids.getTanks() == 2, "tank capability did not expose every fluid layer");
        check(fluids.getFluidInTank(0).is(Fluids.WATER)
            && fluids.getFluidInTank(1).is(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get()),
            "tank capability changed fluid layer order");
        check(fluids.drain(250, IFluidHandler.FluidAction.SIMULATE).is(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get()),
            "tank amount drain did not start at the top layer");
        check(fluids.drain(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE)
                .getAmount() == 500,
            "tank did not drain by fluid identity");
        MoldedPlasticData filledTank = tank[0].withContents(new MoldedPlasticContents(
            tank[0].contents().items(),
            List.of(new FluidStack(Fluids.WATER, 1000))
        ));
        for (Vec3 up : List.of(
            new Vec3(0.0D, 1.0D, 0.0D),
            new Vec3(0.0D, 0.8660254D, 0.5D),
            new Vec3(0.7071067D, 0.7071067D, 0.0D),
            new Vec3(1.0D, 0.0D, 0.0D)
        )) {
            check(!MoldedTankFluidGeometry.solve(filledTank, up).isEmpty(),
                "tank liquid solver produced no geometry for gravity " + up);
        }
        MoldedPlasticData partialTank = tank[0].withContents(new MoldedPlasticContents(
            tank[0].contents().items(),
            List.of(new FluidStack(Fluids.WATER, 700))
        ));
        List<MoldedTankFluidGeometry.Layer> partialLayers = MoldedTankFluidGeometry.solve(
            partialTank,
            new Vec3(0.0D, 1.0D, 0.0D)
        );
        List<Vec3> freeSurface = partialLayers.getFirst().polygons().stream()
            .filter(polygon -> polygon.normal().y() > 0.99D)
            .flatMap(polygon -> polygon.vertices().stream())
            .toList();
        check(!freeSurface.isEmpty(), "tank liquid solver did not create a free surface");
        double surfaceY = freeSurface.stream().mapToDouble(Vec3::y).average().orElseThrow();
        check(Math.abs(surfaceY - 3.8D) < 1.0E-5D,
            "axis-aligned liquid volume used the wrong slice integral: " + surfaceY);
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Molded cauldrons keep their output/input slot split, input stack multiplier and fluid layers")
    static void cauldronCapabilityRoundTrips(ExtendedGameTestHelper helper) {
        MoldedPlasticData ordinaryData = completeData(cauldronModel(MoldingProductTypes.CAULDRON_ID, 17, 12, 12));
        check(MoldingProductTypes.CAULDRON_ID.equals(ordinaryData.finalType()) && ordinaryData.capacity() == 1,
            "the vanilla-shaped cauldron model did not manufacture a 1B cauldron: "
                + ordinaryData.finalType() + " " + ordinaryData.capacity());
        final MoldedPlasticData[] ordinary = {ordinaryData};
        MoldedPlasticItemHandler ordinaryItems = new MoldedPlasticItemHandler(
            () -> Optional.of(ordinary[0]),
            replacement -> ordinary[0] = replacement
        );
        check(ordinaryItems.getSlots() == 16,
            "ordinary cauldron did not expose 8 output and 8 input slots: " + ordinaryItems.getSlots());
        check(!ordinaryItems.insertItem(0, new ItemStack(Items.DIAMOND, 1), false).isEmpty(),
            "ordinary cauldron accepted an insertion into an output slot");
        check(ordinaryItems.getSlotLimit(8) == 64,
            "ordinary cauldron input slot changed its stack limit: " + ordinaryItems.getSlotLimit(8));
        check(ordinaryItems.insertItem(8, new ItemStack(Items.DIAMOND, 64), false).isEmpty(),
            "ordinary cauldron rejected a full stack in its first input slot");
        check(ordinaryItems.getStackInSlot(8).getCount() == 64, "ordinary cauldron lost its input stack");
        check(ordinary[0].storageId().isPresent(),
            "ordinary cauldron did not allocate an external storage id");

        MoldedPlasticFluidHandler ordinaryFluids = new MoldedPlasticFluidHandler(
            () -> Optional.of(ordinary[0]),
            replacement -> ordinary[0] = replacement
        );
        check(ordinaryFluids.getTanks() == 1,
            "ordinary cauldron exposed more than one fluid layer: " + ordinaryFluids.getTanks());
        check(ordinaryFluids.getTankCapacity(0) == 1000,
            "ordinary cauldron layer did not hold its whole 1B: " + ordinaryFluids.getTankCapacity(0));
        check(ordinaryFluids.fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE) == 500,
            "ordinary cauldron rejected its first fluid");
        check(ordinaryFluids.fill(new FluidStack(PlasticraftFluids.PLASTIC_OIL.get(), 500),
            IFluidHandler.FluidAction.EXECUTE) == 0,
            "ordinary cauldron accepted a second fluid layer");
        check(ordinaryFluids.fill(new FluidStack(Fluids.WATER, 700), IFluidHandler.FluidAction.EXECUTE) == 500,
            "ordinary cauldron did not cap its single layer at 1B");
        check(ordinaryFluids.getBottomFluid().is(Fluids.WATER)
                && ordinaryFluids.getBottomFluid().getAmount() == 1000,
            "ordinary cauldron lost its bottom layer");
        check(handleFluids(ordinary).size() == 1,
            "ordinary cauldron did not keep exactly one layer in external storage");

        MoldedPlasticData largeData = completeData(cauldronModel(MoldingProductTypes.CAULDRON_ID, 2, 41, 8));
        check(MoldingProductTypes.LARGE_CAULDRON_ID.equals(largeData.finalType()) && largeData.capacity() == 512,
            "the wide cauldron model did not manufacture a 512B large cauldron: "
                + largeData.finalType() + " " + largeData.capacity());
        final MoldedPlasticData[] large = {largeData};
        MoldedPlasticItemHandler largeItems = new MoldedPlasticItemHandler(
            () -> Optional.of(large[0]),
            replacement -> large[0] = replacement
        );
        check(largeItems.getSlots() == 40,
            "large cauldron did not expose 32 output and 8 input slots: " + largeItems.getSlots());
        check(largeItems.getSlotLimit(0) == 64,
            "large cauldron output slot gained the input stack multiplier: " + largeItems.getSlotLimit(0));
        check(largeItems.getSlotLimit(32) == 576,
            "large cauldron input slot did not stack 9 times the vanilla limit: " + largeItems.getSlotLimit(32));
        ItemStack overflow = largeItems.insertItem(32, new ItemStack(Items.DIAMOND, 600), false);
        check(overflow.getCount() == 24,
            "large cauldron input slot did not cap at 576 items: " + overflow.getCount());
        check(largeItems.getStackInSlot(32).getCount() == 576, "large cauldron lost its stacked input");
        check(largeItems.extractItem(32, 600, false).getCount() == 64,
            "large cauldron extraction did not cap at a single stack");
        check(largeItems.getStackInSlot(32).getCount() == 512,
            "large cauldron extraction removed the wrong amount: " + largeItems.getStackInSlot(32).getCount());

        MoldedPlasticFluidHandler largeFluids = new MoldedPlasticFluidHandler(
            () -> Optional.of(large[0]),
            replacement -> large[0] = replacement
        );
        check(largeFluids.getTanks() == 8,
            "large cauldron did not expose 8 fluid layers: " + largeFluids.getTanks());
        check(largeFluids.getTankCapacity(0) == 64_000,
            "large cauldron layer did not hold 64B: " + largeFluids.getTankCapacity(0));
        List<Fluid> layers = List.of(
            Fluids.WATER,
            PlasticraftFluids.PLASTIC_OIL.get(),
            PlasticraftFluids.CRUDE_OIL_ACID.get(),
            PlasticraftFluids.HIGH_HEAT_FUEL.get(),
            PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(),
            PlasticraftFluids.ENGINEERING_PLASTIC_MELT.get(),
            PlasticraftFluids.HEAT_RESISTANT_PLASTIC_MELT.get(),
            PlasticraftFluids.CLEAR_PLASTIC_MELT.get()
        );
        check(largeFluids.fill(new FluidStack(layers.getFirst(), 70_000), IFluidHandler.FluidAction.EXECUTE) == 64_000,
            "large cauldron bottom layer did not cap at 64B");
        for (int layer = 1; layer < layers.size(); layer++) {
            check(largeFluids.fill(new FluidStack(layers.get(layer), 1000), IFluidHandler.FluidAction.EXECUTE) == 1000,
                "large cauldron rejected fluid layer " + layer);
        }
        check(largeFluids.fill(new FluidStack(PlasticraftFluids.LIQUID_HIGH_VISCOSITY_RESIN.get(), 1000),
            IFluidHandler.FluidAction.EXECUTE) == 0,
            "large cauldron accepted a ninth fluid layer");
        check(largeFluids.getBottomFluid().is(layers.getFirst()),
            "large cauldron changed its bottom layer");
        check(largeFluids.drain(500, IFluidHandler.FluidAction.EXECUTE).is(layers.getLast()),
            "large cauldron amount drain did not start at the top layer");
        check(largeFluids.getFluidInTank(7).getAmount() == 500,
            "large cauldron drained the wrong layer: " + largeFluids.getFluidInTank(7).getAmount());
        check(largeFluids.bottomAccess().drain(1000, IFluidHandler.FluidAction.EXECUTE).is(layers.getFirst()),
            "the bottom layer view drained a different layer");
        check(largeFluids.getFluidInTank(0).getAmount() == 63_000,
            "the bottom layer view drained the wrong amount: " + largeFluids.getFluidInTank(0).getAmount());
        check(handleFluids(large).size() == 8,
            "large cauldron lost a fluid layer in external storage: " + handleFluids(large).size());
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x5x7", floor = true)
    @TestHolder(description = "Creative pick returns an empty molded cauldron normally and preserves its full state with control")
    static void creativePickSeparatesMoldedCauldronContents(ExtendedGameTestHelper helper) {
        MoldedPlasticData sourceData = completeData(cauldronModel(MoldingProductTypes.CAULDRON_ID, 17, 12, 12));
        UniversalPlasticEntity source = createProduct(
            helper,
            new BlockPos(1, 1, 1),
            sourceData,
            PlasticMaterial.ENGINEERING,
            DyeColor.CYAN
        );
        source.setMagnetized(true);
        check(source.getMoldedItemHandler().insertItem(8, new ItemStack(Items.DIAMOND, 3), false).isEmpty(),
            "molded cauldron rejected a pick-state input item");
        check(source.getMoldedFluidHandler().fill(
            new FluidStack(PlasticraftFluids.HIGH_HEAT_FUEL.get(), 500),
            IFluidHandler.FluidAction.EXECUTE
        ) == 500, "molded cauldron rejected a pick-state fluid");
        source.anvilcraft$setIgnited(true);
        check(source.anvilcraft$isIgnited(), "molded cauldron did not retain its ignited state before pick");

        ItemStack initial = source.getPickResult();
        ItemStack complete = source.getCompletePickResult();
        MoldedPlasticData initialData = MoldedPlasticData.get(initial).orElseThrow(
            () -> new GameTestAssertException("normal creative pick lost molded cauldron data")
        );
        MoldedPlasticData completeData = MoldedPlasticData.get(complete).orElseThrow(
            () -> new GameTestAssertException("control creative pick lost molded cauldron data")
        );

        check(initialData.finalType().equals(sourceData.finalType())
                && initialData.capacity() == sourceData.capacity()
                && initialData.modelHash().equals(sourceData.modelHash()),
            "normal creative pick changed the molded cauldron identity");
        check(PlasticItemData.getMaterial(initial).equals(PlasticMaterial.ENGINEERING.key()),
            "normal creative pick changed the plastic material");
        check(PlasticMeltColor.get(initial) == DyeColor.CYAN,
            "normal creative pick changed the plastic color");
        check(PlasticItemData.isMagnetized(initial), "normal creative pick lost magnetization");
        check(initialData.contents().isEmpty()
                && initialData.storageId().isEmpty()
                && initialData.summary().isVacant(),
            "normal creative pick retained molded cauldron contents");
        check(MoldedPlasticCauldronState.get(initial).isEmpty(),
            "normal creative pick retained molded cauldron runtime state");
        check(completeData.storageId().equals(source.getMoldedData().flatMap(MoldedPlasticData::storageId)),
            "control creative pick did not retain molded cauldron storage");
        check(MoldedPlasticCauldronState.get(complete).map(MoldedPlasticCauldronState::ignited).orElse(false),
            "control creative pick did not retain the ignited cauldron state");

        UniversalPlasticEntity restoredComplete = createProduct(
            helper,
            new BlockPos(4, 1, 4),
            complete,
            PlasticMaterial.ENGINEERING,
            DyeColor.CYAN
        );
        check(restoredComplete.getMoldedItemHandler().getStackInSlot(8).is(Items.DIAMOND)
                && restoredComplete.getMoldedItemHandler().getStackInSlot(8).getCount() == 3,
            "control creative pick did not restore molded cauldron items");
        check(restoredComplete.plasticraft$bottomFluid().is(PlasticraftFluids.HIGH_HEAT_FUEL.get())
                && restoredComplete.plasticraft$bottomFluid().getAmount() == 500,
            "control creative pick did not restore molded cauldron fluid");
        check(restoredComplete.anvilcraft$isIgnited(),
            "control creative pick did not restore molded cauldron ignition");

        UniversalPlasticEntity restoredInitial = createProduct(
            helper,
            new BlockPos(4, 1, 1),
            initial,
            PlasticMaterial.ENGINEERING,
            DyeColor.CYAN
        );
        check(restoredInitial.getMoldedItemHandler().getStackInSlot(8).isEmpty()
                && restoredInitial.plasticraft$bottomFluid().isEmpty()
                && !restoredInitial.anvilcraft$isIgnited(),
            "normal creative pick did not restore an empty molded cauldron");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x5x7", floor = true)
    @TestHolder(description = "Molded tanks and cauldrons retain lava for every plastic material")
    static void fluidContainersRetainLavaForEveryMaterial(ExtendedGameTestHelper helper) {
        MoldedPlasticData tank = fullData(model(MoldingProductTypes.TANK_ID));
        MoldedPlasticData cauldron = completeData(cauldronModel(MoldingProductTypes.CAULDRON_ID, 17, 12, 12));
        MoldedPlasticData largeCauldron = completeData(cauldronModel(MoldingProductTypes.CAULDRON_ID, 2, 41, 8));
        for (PlasticMaterial material : PlasticMaterial.values()) {
            checkLavaStorage(tank, material, "tank");
            checkLavaStorage(cauldron, material, "cauldron");
            checkLavaStorage(largeCauldron, material, "large cauldron");
            checkCauldronEntityLavaStorage(helper, cauldron, material, "cauldron");
            checkCauldronEntityLavaStorage(helper, largeCauldron, material, "large cauldron");
        }
        helper.succeed();
    }

    /** 普通锅只有一层流体，因此只让可分层的载体预装水来验证熔岩会保留已有内容。 */
    private static void checkLavaStorage(
        MoldedPlasticData template,
        PlasticMaterial material,
        String carrier
    ) {
        final MoldedPlasticData[] holder = {template.withMaterial(new FluidStack(material.melt(), 250))};
        MoldedPlasticFluidHandler fluids = new MoldedPlasticFluidHandler(
            () -> Optional.of(holder[0]),
            replacement -> holder[0] = replacement,
            () -> ItemStack.EMPTY
        );
        PlasticCauldronLayout layout = PlasticCauldronLayout.of(holder[0].finalType());
        int portion = Math.min(1000, fluids.getTankCapacity(0));
        check(portion > 0, material.key() + " " + carrier + " had no usable capacity");
        int water = layout == null || layout.fluidLayers() > 1 ? portion : 0;
        if (water > 0) {
            check(fluids.fill(new FluidStack(Fluids.WATER, water), IFluidHandler.FluidAction.EXECUTE) == water,
                material.key() + " " + carrier + " rejected water before the lava check");
        }

        check(fluids.fill(new FluidStack(Fluids.LAVA, portion), IFluidHandler.FluidAction.SIMULATE) == portion,
            material.key() + " " + carrier + " reported the wrong simulated lava transfer");
        check(fluids.fill(new FluidStack(Fluids.LAVA, portion), IFluidHandler.FluidAction.EXECUTE) == portion,
            material.key() + " " + carrier + " reported the wrong executed lava transfer");
        check(fluids.getFluidInTank(water > 0 ? 1 : 0).is(Fluids.LAVA),
            material.key() + " " + carrier + " did not retain lava");
        if (water > 0) {
            check(fluids.getFluidInTank(0).is(Fluids.WATER),
                material.key() + " " + carrier + " lost the layer below its lava");
        }
        fluids.discardFluids();
        check(holder[0].storageId().isEmpty(),
            material.key() + " " + carrier + " leaked its storage id after cleanup");
    }

    /** 用真实成型锅验证熔岩存储与外部仓储清理。 */
    private static void checkCauldronEntityLavaStorage(
        ExtendedGameTestHelper helper,
        MoldedPlasticData template,
        PlasticMaterial material,
        String carrier
    ) {
        MoldedPlasticData data = template.withMaterial(new FluidStack(
            material.melt(),
            template.material().getAmount()
        ));
        UniversalPlasticEntity entity = createProduct(
            helper,
            new BlockPos(3, 2, 3),
            data,
            material
        );
        PlasticCauldronLayout layout = PlasticCauldronLayout.of(data.finalType());
        check(layout != null, material.key() + " " + carrier + " lost its cauldron layout");
        check(entity.getMoldedItemHandler().insertItem(
            layout.outputSlots(),
            new ItemStack(Items.DIAMOND),
            false
        ).isEmpty(), material.key() + " " + carrier + " rejected its storage cleanup marker");
        UUID storageId = entity.getMoldedData().flatMap(MoldedPlasticData::storageId).orElseThrow(() ->
            new GameTestAssertException(material.key() + " " + carrier + " did not allocate external storage")
        );
        var server = helper.getLevel().getServer();
        check(server != null, "game test level has no server");
        check(PlasticraftStorages.get(server).get(storageId).isPresent(),
            material.key() + " " + carrier + " storage marker was not persisted");

        IFluidHandler fluids = entity.getFluidHandler();
        int portion = Math.min(1000, fluids.getTankCapacity(0));
        check(portion > 0, material.key() + " " + carrier + " entity had no usable fluid capacity");
        FluidStack lava = new FluidStack(Fluids.LAVA, portion);
        check(fluids.fill(lava, IFluidHandler.FluidAction.SIMULATE) == portion,
            material.key() + " " + carrier + " entity reported the wrong simulated lava transfer");
        check(entity.isAlive(), material.key() + " " + carrier + " entity was destroyed by simulated lava");

        check(fluids.fill(lava, IFluidHandler.FluidAction.EXECUTE) == portion,
            material.key() + " " + carrier + " entity reported the wrong executed lava transfer");
        check(entity.isAlive(), material.key() + " " + carrier + " entity was destroyed by lava");
        check(fluids.getFluidInTank(0).is(Fluids.LAVA)
                && fluids.getFluidInTank(0).getAmount() == portion,
            material.key() + " " + carrier + " entity did not retain lava");
        entity.getMoldedItemHandler().extractItem(layout.outputSlots(), 1, false);
        entity.getMoldedFluidHandler().discardFluids();
        check(entity.getMoldedData().flatMap(MoldedPlasticData::storageId).isEmpty(),
            material.key() + " " + carrier + " entity retained its storage id after cleanup");
        entity.discard();
        check(PlasticraftStorages.get(server).get(storageId).isEmpty(),
            material.key() + " " + carrier + " leaked its external storage record");
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Only empty molded storage products can stack")
    static void storageContentsPreventStacking(ExtendedGameTestHelper helper) {
        MoldedPlasticData chestData = fullData(model(MoldingProductTypes.CHEST_ID));
        ItemStack chest = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(chest, chestData);
        check(chest.getMaxStackSize() > 1, "empty molded chest could not stack");
        MoldedPlasticItemHandler items = new MoldedPlasticItemHandler(
            () -> MoldedPlasticData.get(chest),
            replacement -> MoldedPlasticData.set(chest, replacement)
        );
        check(items.insertItem(0, new ItemStack(Items.DIAMOND), false).isEmpty(),
            "stacking chest rejected a valid item");
        check(chest.getMaxStackSize() == 1, "non-empty molded chest remained stackable");

        MoldedPlasticData tankData = fullData(model(MoldingProductTypes.TANK_ID));
        ItemStack tank = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(tank, tankData);
        check(tank.getMaxStackSize() > 1, "empty molded tank could not stack");
        MoldedPlasticFluidHandler fluids = new MoldedPlasticFluidHandler(
            () -> MoldedPlasticData.get(tank),
            replacement -> MoldedPlasticData.set(tank, replacement),
            () -> tank
        );
        check(fluids.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000,
            "stacking tank rejected a valid fluid");
        check(tank.getMaxStackSize() == 1, "non-empty molded tank remained stackable");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Hammer recovery and placement keep the same molded storage id")
    static void storageIdSurvivesHammerAndPlace(ExtendedGameTestHelper helper) {
        ItemStack stack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(stack, fullData(model(MoldingProductTypes.CHEST_ID)));
        MoldedPlasticItemHandler items = new MoldedPlasticItemHandler(
            () -> MoldedPlasticData.get(stack),
            replacement -> MoldedPlasticData.set(stack, replacement)
        );
        check(items.insertItem(0, new ItemStack(Items.DIAMOND, 2), false).isEmpty(),
            "placed chest rejected a valid item");
        UUID storageId = MoldedPlasticData.get(stack).flatMap(MoldedPlasticData::storageId).orElseThrow(
            () -> new GameTestAssertException("filled chest did not allocate a storage id")
        );

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos clicked = helper.absolutePos(new BlockPos(2, 1, 2));
        InteractionResult placed = stack.useOn(new UseOnContext(
            helper.getLevel(),
            player,
            InteractionHand.MAIN_HAND,
            stack,
            new BlockHitResult(clicked.getCenter().add(0.0D, 0.5D, 0.0D), Direction.UP, clicked, false)
        ));
        check(placed.consumesAction(), "filled chest item was not placed");
        UniversalPlasticEntity entity = helper.getLevel().getEntitiesOfClass(
            UniversalPlasticEntity.class,
            new AABB(clicked.above()).inflate(0.2D)
        ).stream().findFirst().orElseThrow(() ->
            new GameTestAssertException("placed chest entity was not created")
        );
        check(entity.getMoldedData().flatMap(MoldedPlasticData::storageId).equals(Optional.of(storageId)),
            "placing a filled chest allocated a new storage id");
        check(entity.getMoldedItemHandler().getStackInSlot(0).is(Items.DIAMOND),
            "placed chest lost its stored item");

        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, PlasticraftItems.RESIN_ANVIL_HAMMER.asStack());
        check(entity.interact(player, InteractionHand.MAIN_HAND).consumesAction(),
            "hammer did not recover the placed chest");
        final ItemStack[] recovered = {ItemStack.EMPTY};
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (MoldedPlasticData.get(candidate).isPresent()) {
                recovered[0] = candidate;
                break;
            }
        }
        check(!recovered[0].isEmpty(), "hammer recovery did not return a molded chest");
        check(MoldedPlasticData.get(recovered[0]).flatMap(MoldedPlasticData::storageId).equals(Optional.of(storageId)),
            "hammer recovery allocated a new storage id");
        check(new MoldedPlasticItemHandler(
            () -> MoldedPlasticData.get(recovered[0]),
            replacement -> MoldedPlasticData.set(recovered[0], replacement)
        ).getStackInSlot(0).getCount() == 2, "hammer recovery lost the stored item");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "5x4x5", floor = true)
    @TestHolder(description = "Entity summary sync omits full chest slots")
    static void summarySyncOmitsFullSlots(ExtendedGameTestHelper helper) {
        ItemStack stack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        EditableMoldingModel chestModel = largeModel(MoldingProductTypes.CHEST_ID);
        var baked = MoldingModelBaker.bake(chestModel);
        int melt = baked.analysis().minimumMeltMillibuckets();
        MoldedPlasticData.set(stack, MoldedPlasticData.manufacture(
            chestModel,
            baked,
            new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), melt),
            melt
        ));
        MoldedPlasticItemHandler items = new MoldedPlasticItemHandler(
            () -> MoldedPlasticData.get(stack),
            replacement -> MoldedPlasticData.set(stack, replacement)
        );
        List<Item> samples = List.of(
            Items.DIRT,
            Items.COBBLESTONE,
            Items.SAND,
            Items.GRAVEL,
            Items.OAK_LOG,
            Items.SPRUCE_LOG,
            Items.BIRCH_LOG,
            Items.JUNGLE_LOG,
            Items.ACACIA_LOG
        );
        for (int slot = 0; slot < samples.size(); slot++) {
            check(items.insertItem(slot, new ItemStack(samples.get(slot)), false).isEmpty(),
                "summary chest rejected " + samples.get(slot));
        }
        UniversalPlasticEntity entity = createProduct(helper, new BlockPos(2, 2, 2), stack);
        MoldedPlasticContentSummary summary = entity.getMoldedContentSummary();
        check(summary.occupiedSlots() == samples.size(),
            "entity summary lost occupied slot count: " + summary.occupiedSlots());
        check(summary.items().size() == MoldedPlasticContentSummary.MAX_VISIBLE_ENTRIES,
            "entity summary did not cap visible item types");
        check(summary.omittedItemTypes() == 1, "entity summary did not report omitted item types");
        check(entity.getMoldedData().orElseThrow().contents().items().isEmpty(),
            "entity synced the complete chest slot list");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Recursive molded containers are rejected and debug chests keep 15625 compact slots")
    static void debugChestStorageContract(ExtendedGameTestHelper helper) {
        EditableMoldingModel model = new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Creative oversized chest",
            MoldingProductTypes.CHEST_ID,
            List.of(MoldingElement.cube("Solid", vec(0, 0, 0), vec(100, 100, 100))),
            List.of()
        );
        MoldedPlasticData product = MoldedPlasticData.manufacture(
            model,
            MoldingModelBaker.bake(model),
            FluidStack.EMPTY,
            0,
            false,
            true
        );
        check(product.capacity() == 15_625, "debug chest lost its 15625 slot capacity");
        ItemStack chest = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(chest, product);
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        CompoundTag emptyTag = (CompoundTag) MoldedPlasticData.CODEC.encodeStart(ops, product).getOrThrow();
        check(!emptyTag.contains("storage_id"), "empty debug chest allocated a storage id");
        check(!emptyTag.getCompound("contents").contains("items"),
            "empty debug chest persisted an inline item list");

        MoldedPlasticItemHandler items = new MoldedPlasticItemHandler(
            () -> MoldedPlasticData.get(chest),
            replacement -> MoldedPlasticData.set(chest, replacement)
        );
        ItemStack nested = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(nested, fullData(model(MoldingProductTypes.CHEST_ID)));
        check(ItemStack.matches(items.insertItem(0, nested, false), nested),
            "debug chest accepted a recursive molded container");
        check(items.insertItem(15_624, new ItemStack(Items.DIAMOND, 4), false).isEmpty(),
            "debug chest truncated the highest slot");
        check(items.getStackInSlot(15_624).getCount() == 4, "debug chest did not retain the highest slot");
        UUID storageId = MoldedPlasticData.get(chest).flatMap(MoldedPlasticData::storageId).orElseThrow(
            () -> new GameTestAssertException("occupied debug chest did not allocate a storage id")
        );
        var server = helper.getLevel().getServer();
        check(server != null, "game test level has no server");
        MoldedPlasticStorage storage = PlasticraftStorages.get(server)
            .get(storageId)
            .orElseThrow(() -> new GameTestAssertException("occupied debug chest missing SavedData"));
        ListTag storedItems = storage.save(helper.getLevel().registryAccess())
            .getCompound("items")
            .getList("Items", 10);
        check(storedItems.size() == 1, "occupied debug chest persisted every empty slot");
        check(storedItems.getCompound(0).getInt("Slot") == 15_624,
            "occupied debug chest persisted the wrong sparse slot");
        check(items.extractItem(15_624, 4, false).getCount() == 4, "debug chest could not empty the highest slot");
        check(MoldedPlasticData.get(chest).flatMap(MoldedPlasticData::storageId).isEmpty(),
            "emptied debug chest kept its storage id");
        check(PlasticraftStorages.get(server).get(storageId).isEmpty(),
            "emptied debug chest leaked its SavedData record");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Molded product names combine plastic material and final functional type")
    static void productNamesUseMaterialAndFinalType(ExtendedGameTestHelper helper) {
        checkProductName(MoldingProductTypes.NORMAL_ID, "item.anvilcraftplasticraft.molded_product_suffix.block");
        checkProductName(MoldingProductTypes.CHEST_ID, "screen.anvilcraftplasticraft.molding.type.chest");
        checkProductName(MoldingProductTypes.TANK_ID, "screen.anvilcraftplasticraft.molding.type.tank");
        checkProductName(MoldingProductTypes.ANVIL_ID, "screen.anvilcraftplasticraft.molding.type.anvil");
        checkProductName(MoldingProductTypes.CAULDRON_ID, "screen.anvilcraftplasticraft.molding.type.cauldron");
        // 大型锅由普通锅升级得到，名字必须跟随最终类型而不是请求类型
        checkProductName(
            cauldronModel(MoldingProductTypes.CAULDRON_ID, 2, 41, 8),
            "screen.anvilcraftplasticraft.molding.type.large_cauldron"
        );
        checkProductName(MoldingProductTypes.TRAY_ID, "screen.anvilcraftplasticraft.molding.type.tray");
        helper.succeed();
    }

    private static void checkProductName(
        ResourceLocation type,
        String suffixKey
    ) {
        EditableMoldingModel selectedModel;
        if (MoldingProductTypes.ANVIL_ID.equals(type)) {
            selectedModel = anvilModel(type);
        } else if (MoldingProductTypes.TRAY_ID.equals(type)) {
            selectedModel = trayModel(type);
        } else if (MoldingProductTypes.CAULDRON_ID.equals(type)) {
            selectedModel = cauldronModel(type, 17, 12, 12);
        } else {
            selectedModel = model(type);
        }
        checkProductName(selectedModel, suffixKey);
    }

    private static void checkProductName(
        EditableMoldingModel model,
        String suffixKey
    ) {
        ItemStack stack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(stack, completeData(model));
        Component expected = Component.translatable(
            "item.anvilcraftplasticraft.molded_product_name",
            Component.translatable("material.anvilcraftplasticraft.universal_plastic"),
            Component.translatable(suffixKey)
        );
        check(stack.getHoverName().equals(expected),
            "molded product name did not combine material and final type: " + stack.getHoverName());
    }

    private static void checkCapacity(
        ExtendedGameTestHelper helper,
        ResourceLocation type,
        int cavity,
        int expectedCapacity,
        boolean expectedValid
    ) {
        MoldingFunctionalAnalysis analysis = new MoldingFunctionalAnalysis(
            new MoldingVolumeMask(),
            1,
            cavity,
            cavity,
            false
        );
        MoldingTypeValidation result = MoldingProductTypes.validate(type, analysis);
        check(result.valid() == expectedValid, "unexpected type validation at cavity " + cavity);
        check(result.capacity() == expectedCapacity, "unexpected capacity at cavity " + cavity);
    }

    private static void checkCauldronCapacity(
        MoldingVolumeMask mask,
        int expectedCavity,
        int expectedCapacity
    ) {
        MoldingCauldronShapeAnalysis shape = analyzeCauldron(mask);
        check(shape.valid(), "cauldron capacity fixture was rejected: " + shape.reason());
        check(shape.cavityVolume() == expectedCavity,
            "cauldron fixture measured " + shape.cavityVolume() + " instead of "
                + expectedCavity + " cubic pixels");
        MoldingTypeValidation result = MoldingProductTypes.validate(
            MoldingProductTypes.CAULDRON_ID,
            cauldronAnalysis(shape)
        );
        check(result.valid(),
            "cauldron type rejected a " + expectedCavity + " cubic pixel cavity: " + result.reason());
        check(result.capacity() == expectedCapacity,
            "cauldron cavity " + expectedCavity + " produced " + result.capacity()
                + "B instead of " + expectedCapacity + "B");
    }

    private static MoldingCauldronShapeAnalysis analyzeCauldron(MoldingVolumeMask mask) {
        return MoldingCauldronShapeAnalyzer.analyze(mask, Set.of());
    }

    /** 炼药锅校验只读 cauldronShape，其余组件按空壳填充即可。 */
    private static MoldingFunctionalAnalysis cauldronAnalysis(MoldingCauldronShapeAnalysis shape) {
        return new MoldingFunctionalAnalysis(
            shape.cavityMask(),
            1,
            shape.cavityVolume(),
            0,
            false
        ).withCauldronShape(shape);
    }

    private static void checkCauldronRejected(MoldingVolumeMask mask, String reason, String description) {
        MoldingCauldronShapeAnalysis shape = analyzeCauldron(mask);
        check(!shape.valid() && reason.equals(shape.reason()),
            "expected " + reason + " for " + description + " but received "
                + shape.reason() + " (valid=" + shape.valid() + ")");
    }

    /** 锅体掩码留出的边距，保证四周的壁和外界洪泛都落在工作区内。 */
    private static final int CAULDRON_ORIGIN = 2;

    /**
     * 开顶锅掩码：内腔由 {@code interior} 的 (x,z) 布尔网格给出，底板铺满内腔投影，
     * 每个与内腔四邻接的非内腔格都立起一道 {@code wallHeight} 高的壁。
     * 从外界到内腔的任何四邻接路径都必须经过这样的格子，所以圆形和异形轮廓无需另写几何。
     */
    private static MoldingVolumeMask cauldronMask(boolean[][] interior, int wallHeight) {
        MoldingVolumeMask mask = new MoldingVolumeMask();
        for (int x = 0; x < interior.length; x++) {
            for (int z = 0; z < interior[x].length; z++) {
                if (!interior[x][z]) continue;
                mask.set(CAULDRON_ORIGIN + x, 0, CAULDRON_ORIGIN + z);
                markCauldronWall(mask, interior, x - 1, z, wallHeight);
                markCauldronWall(mask, interior, x + 1, z, wallHeight);
                markCauldronWall(mask, interior, x, z - 1, wallHeight);
                markCauldronWall(mask, interior, x, z + 1, wallHeight);
            }
        }
        return mask;
    }

    private static void markCauldronWall(
        MoldingVolumeMask mask,
        boolean[][] interior,
        int x,
        int z,
        int wallHeight
    ) {
        if (x >= 0 && x < interior.length && z >= 0 && z < interior[x].length && interior[x][z]) return;
        for (int y = 0; y <= wallHeight; y++) mask.set(CAULDRON_ORIGIN + x, y, CAULDRON_ORIGIN + z);
    }

    private static boolean[][] rectangleInterior(int sizeX, int sizeZ) {
        boolean[][] interior = new boolean[sizeX][sizeZ];
        for (boolean[] row : interior) Arrays.fill(row, true);
        return interior;
    }

    /** 内腔中央立一根通高柱子：开口面积掉到 143 px²，但开口的外接矩形仍是 12×12。 */
    private static boolean[][] pillarInterior(int size) {
        boolean[][] interior = rectangleInterior(size, size);
        interior[size / 2][size / 2] = false;
        return interior;
    }

    /** 圆形开口：格心落在圆内即算内腔，直径 14 px 得 156 px²，直径 13 px 只有 137 px²。 */
    private static boolean[][] discInterior(int diameter) {
        boolean[][] interior = new boolean[diameter][diameter];
        double radius = diameter / 2.0D;
        for (int x = 0; x < diameter; x++) {
            for (int z = 0; z < diameter; z++) {
                double offsetX = x + 0.5D - radius;
                double offsetZ = z + 0.5D - radius;
                interior[x][z] = offsetX * offsetX + offsetZ * offsetZ <= radius * radius;
            }
        }
        return interior;
    }

    /** 1601 是质数，只能在 40×40 的一条边上多凸出一格，才能刚好越过 1600 px² 的升级线。 */
    private static boolean[][] bumpedInterior(int size) {
        boolean[][] interior = new boolean[size + 1][size];
        for (int x = 0; x < size; x++) Arrays.fill(interior[x], true);
        interior[size][0] = true;
        return interior;
    }

    private static MoldingVolumeMask squareCauldron(int interiorSize, int wallHeight) {
        return cauldronMask(rectangleInterior(interiorSize, interiorSize), wallHeight);
    }

    /** 内腔底面立一格凸起，只吃掉 1 px³，用来压容量的整 B 边界。 */
    private static MoldingVolumeMask stubbedCauldron(int interiorSize, int wallHeight) {
        MoldingVolumeMask mask = squareCauldron(interiorSize, wallHeight);
        mask.set(CAULDRON_ORIGIN, 1, CAULDRON_ORIGIN);
        return mask;
    }

    /** 西壁只有 {@code lowWallHeight} 高，其余三面仍是 {@code wallHeight}。 */
    private static MoldingVolumeMask unevenWallCauldron(
        int interiorSize,
        int wallHeight,
        int lowWallHeight
    ) {
        MoldingVolumeMask mask = new MoldingVolumeMask();
        int wallMin = CAULDRON_ORIGIN - 1;
        int wallMax = CAULDRON_ORIGIN + interiorSize;
        fillBox(mask, wallMin, 0, wallMin, wallMax + 1, 1, wallMax + 1);
        fillBox(mask, wallMin, 1, wallMin, wallMin + 1, lowWallHeight + 1, wallMax + 1);
        fillBox(mask, wallMax, 1, wallMin, wallMax + 1, wallHeight + 1, wallMax + 1);
        fillBox(mask, CAULDRON_ORIGIN, 1, wallMin, wallMax, wallHeight + 1, CAULDRON_ORIGIN);
        fillBox(mask, CAULDRON_ORIGIN, 1, wallMax, wallMax, wallHeight + 1, wallMax + 1);
        return mask;
    }

    /** 西壁最底层缺一格，内腔会顺着漏孔与外界连通。 */
    private static MoldingVolumeMask leakingCauldron(int interiorSize, int wallHeight) {
        MoldingVolumeMask mask = new MoldingVolumeMask();
        int wallMin = CAULDRON_ORIGIN - 1;
        int wallMax = CAULDRON_ORIGIN + interiorSize;
        int leakZ = CAULDRON_ORIGIN;
        fillBox(mask, wallMin, 0, wallMin, wallMax + 1, 1, wallMax + 1);
        fillBox(mask, wallMin, 1, wallMin, wallMin + 1, wallHeight + 1, leakZ);
        fillBox(mask, wallMin, 1, leakZ + 1, wallMin + 1, wallHeight + 1, wallMax + 1);
        fillBox(mask, wallMin, 2, leakZ, wallMin + 1, wallHeight + 1, leakZ + 1);
        fillBox(mask, wallMax, 1, wallMin, wallMax + 1, wallHeight + 1, wallMax + 1);
        fillBox(mask, CAULDRON_ORIGIN, 1, wallMin, wallMax, wallHeight + 1, CAULDRON_ORIGIN);
        fillBox(mask, CAULDRON_ORIGIN, 1, wallMax, wallMax, wallHeight + 1, wallMax + 1);
        return mask;
    }

    private static MoldingVolumeMask hollowCube(int size) {
        MoldingVolumeMask mask = new MoldingVolumeMask();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    if (x == 0 || x == size - 1 || y == 0 || y == size - 1 || z == 0 || z == size - 1) {
                        mask.set(x, y, z);
                    }
                }
            }
        }
        return mask;
    }

    private static MoldingVolumeMask anvilMask(
        int bottomThickness,
        int middleThickness,
        int topThickness,
        int bottomWidth,
        int middleWidth,
        int topWidth
    ) {
        MoldingVolumeMask mask = new MoldingVolumeMask();
        int bottomMin = (48 - bottomWidth) / 2;
        int middleMin = (48 - middleWidth) / 2;
        int topMin = (48 - topWidth) / 2;
        int y = 0;
        fillBox(mask, bottomMin, y, bottomMin, bottomMin + bottomWidth, y + bottomThickness, bottomMin + bottomWidth);
        y += bottomThickness;
        fillBox(mask, middleMin, y, middleMin, middleMin + middleWidth, y + middleThickness, middleMin + middleWidth);
        y += middleThickness;
        fillBox(mask, topMin, y, topMin, topMin + topWidth, y + topThickness, topMin + topWidth);
        return mask;
    }

    private static MoldingVolumeMask anvilRectMask(
        int bottomWidth,
        int bottomDepth,
        int middleWidth,
        int middleDepth,
        int topWidth,
        int topDepth
    ) {
        MoldingVolumeMask mask = new MoldingVolumeMask();
        int bottomMinX = (48 - bottomWidth) / 2;
        int bottomMinZ = (48 - bottomDepth) / 2;
        int middleMinX = (48 - middleWidth) / 2;
        int middleMinZ = (48 - middleDepth) / 2;
        int topMinX = (48 - topWidth) / 2;
        int topMinZ = (48 - topDepth) / 2;
        fillBox(mask, bottomMinX, 0, bottomMinZ, bottomMinX + bottomWidth, 3, bottomMinZ + bottomDepth);
        fillBox(mask, middleMinX, 3, middleMinZ, middleMinX + middleWidth, 6, middleMinZ + middleDepth);
        fillBox(mask, topMinX, 6, topMinZ, topMinX + topWidth, 11, topMinZ + topDepth);
        return mask;
    }

    private static MoldingVolumeMask expandedBottomUpperLayers() {
        MoldingVolumeMask mask = anvilMask(3, 3, 5, 16, 10, 18);
        fillBox(mask, 15, 1, 15, 33, 3, 33);
        return mask;
    }

    private static void fillBox(
        MoldingVolumeMask mask,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
    ) {
        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                for (int z = minZ; z < maxZ; z++) mask.set(x, y, z);
            }
        }
    }

    private static EditableMoldingModel centerHat(double height) {
        return hatModel(List.of(MoldingElement.cube("Crown", vec(18.5, 20, 18.5), vec(29.5, 20 + height, 29.5))));
    }

    private static EditableMoldingModel offCenterHat() {
        return hatModel(List.of(MoldingElement.cube("Crown", vec(0, 20, 0), vec(11, 28, 11))));
    }

    private static EditableMoldingModel tooWideHat() {
        return hatModel(List.of(MoldingElement.cube("Brim", vec(18.5, 20, 18.5), vec(29.501, 24, 29.501))));
    }

    private static EditableMoldingModel emptyHat() {
        return hatModel(List.of());
    }

    private static EditableMoldingModel hatModel(List<MoldingElement> elements) {
        return new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Hard hat shape test",
            MoldingProductTypes.ALLAY_HARD_HAT_ID,
            elements,
            List.of()
        );
    }

    private static void checkHardHatValid(EditableMoldingModel model, String description) {
        var baked = MoldingModelBaker.bake(model);
        MoldingTypeValidation validation = MoldingProductTypes.validate(
            MoldingProductTypes.ALLAY_HARD_HAT_ID,
            model,
            baked
        );
        check(validation.valid(), description + " was rejected: " + validation.reason());
    }

    private static void checkHardHatReason(EditableMoldingModel model, String reason) {
        var baked = MoldingModelBaker.bake(model);
        MoldingTypeValidation validation = MoldingProductTypes.validate(
            MoldingProductTypes.ALLAY_HARD_HAT_ID,
            model,
            baked
        );
        check(!validation.valid() && reason.equals(validation.reason()),
            "expected " + reason + " but received " + validation);
    }

    private static EditableMoldingModel model(ResourceLocation type) {
        return new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Functional product test",
            type,
            List.of(
                MoldingElement.cube("West", vec(0, 0, 0), vec(1, 10, 10)),
                MoldingElement.cube("East", vec(9, 0, 0), vec(10, 10, 10)),
                MoldingElement.cube("Down", vec(1, 0, 1), vec(9, 1, 9)),
                MoldingElement.cube("Up", vec(1, 9, 1), vec(9, 10, 9)),
                MoldingElement.cube("North", vec(1, 1, 0), vec(9, 9, 1)),
                MoldingElement.cube("South", vec(1, 1, 9), vec(9, 9, 10)
                )
            ),
            List.of()
        );
    }

    private static EditableMoldingModel largeModel(ResourceLocation type) {
        return new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Large functional product test",
            type,
            List.of(
                MoldingElement.cube("West", vec(0, 0, 0), vec(1, 18, 18)),
                MoldingElement.cube("East", vec(17, 0, 0), vec(18, 18, 18)),
                MoldingElement.cube("Down", vec(1, 0, 1), vec(17, 1, 17)),
                MoldingElement.cube("Up", vec(1, 17, 1), vec(17, 18, 17)),
                MoldingElement.cube("North", vec(1, 1, 0), vec(17, 17, 1)),
                MoldingElement.cube("South", vec(1, 1, 17), vec(17, 17, 18)
                )
            ),
            List.of()
        );
    }

    private static EditableMoldingModel anvilModel(ResourceLocation type) {
        return new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Anvil shape test",
            type,
            List.of(
                MoldingElement.cube("Bottom", vec(16, 0, 16), vec(32, 3, 32)),
                MoldingElement.cube("Middle", vec(19, 3, 19), vec(29, 6, 29)),
                MoldingElement.cube("Top", vec(15, 6, 15), vec(33, 11, 33))
            ),
            List.of()
        );
    }

    private static EditableMoldingModel emberAnvilModel(boolean includeNegativeOutline) {
        List<MoldingElement> elements = new ArrayList<>(List.of(
            MoldingElement.cube("Anvil base", vec(18.5, 16.5, 18.5), vec(29.5, 19.5, 29.5)),
            MoldingElement.cube("Middle", vec(21.5, 19.5, 20.5), vec(26.5, 26.5, 27.5)),
            MoldingElement.cube("Anvil top", vec(19.5, 26.5, 16.5), vec(28.5, 31.5, 31.5))
        ));
        if (includeNegativeOutline) {
            elements.add(MoldingElement.cube(
                "Anvil base_outline",
                vec(29.975, 19.975, 29.975),
                vec(18.025, 16.025, 18.025)
            ));
        }
        return new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "ember_anvil",
            MoldingProductTypes.ANVIL_ID,
            elements,
            List.of()
        );
    }

    private static EditableMoldingModel trayModel(ResourceLocation type) {
        return new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Tray shape test",
            type,
            List.of(MoldingElement.cube("Body", vec(16, 0, 16), vec(32, 4, 32))),
            List.of()
        );
    }

    /**
     * 开顶锅模型：底板加四面等高侧壁，顶面留空，壁厚固定 1 px。
     * {@code min} 是外壁最小角，{@code interiorSize} 是内腔的水平边长。
     */
    private static EditableMoldingModel cauldronModel(
        ResourceLocation type,
        int min,
        int interiorSize,
        int wallHeight
    ) {
        int outer = min + interiorSize + 2;
        int innerMin = min + 1;
        int innerMax = min + interiorSize + 1;
        int top = 1 + wallHeight;
        return new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Cauldron shape test",
            type,
            List.of(
                MoldingElement.cube("Floor", vec(min, 0, min), vec(outer, 1, outer)),
                MoldingElement.cube("West", vec(min, 1, min), vec(innerMin, top, outer)),
                MoldingElement.cube("East", vec(innerMax, 1, min), vec(outer, top, outer)),
                MoldingElement.cube("North", vec(innerMin, 1, min), vec(innerMax, top, innerMin)),
                MoldingElement.cube("South", vec(innerMin, 1, innerMax), vec(innerMax, top, outer))
            ),
            List.of()
        );
    }

    private static void checkNegativeSealedCavity(ResourceLocation type, boolean partialFill) {
        EditableMoldingModel model = negativeSealedContainerModel(type, partialFill);
        var baked = MoldingModelBaker.bake(model);
        int expectedCavity = 14 * 14 * 14;
        int expectedCapacity = MoldingProductTypes.capacityFor(type, expectedCavity);
        check(baked.functionalAnalysis().cavityVolume() < expectedCavity,
            "raw " + type.getPath() + " analysis counted a " + (partialFill ? "partial" : "full")
                + " inward-facing fill as a cavity");
        MoldingTypeValidation validation = MoldingProductTypes.validate(type, model, baked);
        check(validation.valid() && validation.capacity() == expectedCapacity,
            "inward-facing " + (partialFill ? "partial" : "full") + " fill did not restore the "
                + type.getPath() + " cavity: " + validation);
        MoldingProductPreview preview = MoldingProductPreview.evaluate(
            model,
            baked,
            baked.analysis().minimumMeltMillibuckets()
        );
        check(type.equals(preview.finalType()) && preview.cavityMask().volume() == expectedCavity,
            "preview did not persist the inward-facing " + type.getPath() + " cavity: " + preview);
        MoldedPlasticData product = completeData(model);
        check(type.equals(product.finalType())
                && product.capacity() == expectedCapacity
                && product.cavityMask().volume() == expectedCavity,
            "manufactured inward-facing " + type.getPath() + " lost its cavity or capacity");
    }

    private static void checkNegativeSealedWalls(ResourceLocation type) {
        EditableMoldingModel model = negativeSealedWallContainerModel(type);
        var baked = MoldingModelBaker.bake(model);
        MoldingTypeValidation validation = MoldingProductTypes.validate(type, model, baked);
        check(validation.valid(), "inward-facing " + type.getPath() + " walls were treated only as cavities");
        MoldingProductPreview preview = MoldingProductPreview.evaluate(
            model,
            baked,
            baked.analysis().minimumMeltMillibuckets()
        );
        check(type.equals(preview.finalType()) && preview.cavityMask().volume() == 14 * 14 * 14,
            "inward-facing " + type.getPath() + " walls did not retain their enclosed cavity");
    }

    private static void checkNegativeCauldronCavity(boolean partialFill, boolean splitFill) {
        String fillDescription = splitFill ? "split" : partialFill ? "partial" : "full";
        EditableMoldingModel model = negativeCauldronModel(partialFill, splitFill);
        var baked = MoldingModelBaker.bake(model);
        int expectedCavity = 12 * 12 * 12;
        check(!baked.functionalAnalysis().cauldronShape().valid(),
            "raw cauldron analysis accepted a " + fillDescription + " inward-facing fill");
        MoldingTypeValidation validation = MoldingProductTypes.validate(MoldingProductTypes.CAULDRON_ID, model, baked);
        check(validation.valid() && validation.capacity() == 1,
            "inward-facing " + fillDescription + " fill did not restore the cauldron cavity: " + validation);
        MoldingProductPreview preview = MoldingProductPreview.evaluate(
            model,
            baked,
            baked.analysis().minimumMeltMillibuckets()
        );
        check(MoldingProductTypes.CAULDRON_ID.equals(preview.finalType())
                && preview.cavityMask().volume() == expectedCavity,
            "preview did not persist the inward-facing cauldron cavity: " + preview);
        MoldedPlasticData product = completeData(model);
        check(MoldingProductTypes.CAULDRON_ID.equals(product.finalType())
                && product.capacity() == 1
                && product.cavityMask().volume() == expectedCavity,
            "manufactured inward-facing cauldron lost its cavity or capacity");
    }

    private static void checkNegativeCauldronWalls() {
        EditableMoldingModel model = negativeCauldronWallModel();
        var baked = MoldingModelBaker.bake(model);
        MoldingTypeValidation validation = MoldingProductTypes.validate(MoldingProductTypes.CAULDRON_ID, model, baked);
        check(validation.valid(), "inward-facing cauldron walls were treated only as cavities");
        MoldingProductPreview preview = MoldingProductPreview.evaluate(
            model,
            baked,
            baked.analysis().minimumMeltMillibuckets()
        );
        check(MoldingProductTypes.CAULDRON_ID.equals(preview.finalType())
                && preview.cavityMask().volume() == 12 * 12 * 12,
            "inward-facing cauldron walls did not retain their open cavity");
    }

    private static EditableMoldingModel negativeSealedContainerModel(ResourceLocation type, boolean partialFill) {
        List<MoldingElement> elements = new ArrayList<>(sealedContainerWalls());
        int top = partialFill ? 24 : 31;
        elements.add(MoldingElement.cube(
            "Inner negative volume",
            vec(31, top, 31),
            vec(17, 17, 17)
        ));
        return new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Negative cavity container",
            type,
            elements,
            List.of()
        );
    }

    private static EditableMoldingModel negativeSealedWallContainerModel(ResourceLocation type) {
        return new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Negative wall container",
            type,
            sealedContainerWalls().stream().map(MoldingProductGameTests::reverseX).toList(),
            List.of()
        );
    }

    private static List<MoldingElement> sealedContainerWalls() {
        return List.of(
            MoldingElement.cube("Floor", vec(16, 16, 16), vec(32, 17, 32)),
            MoldingElement.cube("Ceiling", vec(16, 31, 16), vec(32, 32, 32)),
            MoldingElement.cube("West", vec(16, 17, 16), vec(17, 31, 32)),
            MoldingElement.cube("East", vec(31, 17, 16), vec(32, 31, 32)),
            MoldingElement.cube("North", vec(17, 17, 16), vec(31, 31, 17)),
            MoldingElement.cube("South", vec(17, 17, 31), vec(31, 31, 32))
        );
    }

    private static EditableMoldingModel negativeCauldronModel(boolean partialFill, boolean splitFill) {
        EditableMoldingModel base = cauldronModel(MoldingProductTypes.CAULDRON_ID, 17, 12, 12);
        List<MoldingElement> elements = new ArrayList<>(base.elements());
        if (splitFill) {
            elements.add(MoldingElement.cube("Inner negative volume west", vec(24, 13, 30), vec(18, 1, 18)));
            elements.add(MoldingElement.cube("Inner negative volume east", vec(30, 13, 30), vec(24, 1, 18)));
        } else {
            elements.add(MoldingElement.cube(
                "Inner negative volume",
                vec(30, partialFill ? 7 : 13, 30),
                vec(18, 1, 18)
            ));
        }
        return base.withElements(elements);
    }

    private static EditableMoldingModel negativeCauldronWallModel() {
        EditableMoldingModel base = cauldronModel(MoldingProductTypes.CAULDRON_ID, 17, 12, 12);
        return base.withElements(base.elements().stream().map(MoldingProductGameTests::reverseX).toList());
    }

    private static MoldingElement reverseX(MoldingElement element) {
        return MoldingElement.cube(
            element.name(),
            vec(element.to().x(), element.from().y(), element.from().z()),
            vec(element.from().x(), element.to().y(), element.to().z())
        );
    }

    private static EditableMoldingModel zeroThicknessSealedModel(ResourceLocation type) {
        return new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Zero thickness seal test",
            type,
            List.of(
                MoldingElement.cube("East", vec(9, 0, 0), vec(10, 10, 10)),
                MoldingElement.cube("Down", vec(1, 0, 1), vec(9, 1, 9)),
                MoldingElement.cube("Up", vec(1, 9, 1), vec(9, 10, 9)),
                MoldingElement.cube("North", vec(1, 1, 0), vec(9, 9, 1)),
                MoldingElement.cube("South", vec(1, 1, 9), vec(9, 9, 10)),
                MoldingElement.cube("West seal", vec(1, 1, 1), vec(1, 9, 9))
            ),
            List.of()
        );
    }

    private static MoldingVec3 vec(double x, double y, double z) {
        return new MoldingVec3(x, y, z);
    }

    private static List<FluidStack> handleFluids(MoldedPlasticData[] holder) {
        return new MoldedPlasticStorageHandle(
            () -> Optional.of(holder[0]),
            replacement -> holder[0] = replacement
        ).fluids();
    }

    private static UniversalPlasticEntity createProduct(
        ExtendedGameTestHelper helper,
        BlockPos occupiedPos,
        ItemStack stack
    ) {
        MoldedPlasticData data = MoldedPlasticData.get(stack).orElseThrow(
            () -> new GameTestAssertException("product stack lost molded data")
        );
        BlockPos absolutePos = helper.absolutePos(occupiedPos);
        UniversalPlasticEntity entity = new UniversalPlasticEntity(
            PlasticraftEntities.UNIVERSAL_PLASTIC.get(),
            helper.getLevel(),
            data.geometry().placementPosition(absolutePos, PlasticEntityOrientation.DEFAULT),
            PlasticraftBlocks.UNIVERSAL_PLASTIC.get().defaultBlockState(),
            stack.copy(),
            PlasticEntityOrientation.DEFAULT
        );
        entity.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(entity), "failed to add molded plastic product");
        return entity;
    }

    private static UniversalPlasticEntity createProduct(
        ExtendedGameTestHelper helper,
        BlockPos occupiedPos,
        MoldedPlasticData data,
        PlasticMaterial material,
        DyeColor color
    ) {
        ItemStack stack = material.productStack(color);
        MoldedPlasticData.set(stack, data);
        return createProduct(helper, occupiedPos, stack, material, color);
    }

    private static UniversalPlasticEntity createProduct(
        ExtendedGameTestHelper helper,
        BlockPos occupiedPos,
        ItemStack stack,
        PlasticMaterial material,
        DyeColor color
    ) {
        MoldedPlasticData data = MoldedPlasticData.get(stack).orElseThrow(
            () -> new GameTestAssertException("product stack lost molded data")
        );
        BlockPos absolutePos = helper.absolutePos(occupiedPos);
        UniversalPlasticEntity entity = material.createEntity(
            helper.getLevel(),
            data.geometry().placementPosition(absolutePos, PlasticEntityOrientation.DEFAULT),
            material.displayState(color),
            stack.copy(),
            PlasticEntityOrientation.DEFAULT
        );
        entity.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(entity), "failed to add " + material.key() + " colored molded product");
        return entity;
    }

    private static UniversalPlasticEntity createProduct(
        ExtendedGameTestHelper helper,
        BlockPos occupiedPos,
        MoldedPlasticData data,
        PlasticMaterial material
    ) {
        ItemStack stack = material.productBlock().asItem().getDefaultInstance();
        MoldedPlasticData.set(stack, data);
        BlockPos absolutePos = helper.absolutePos(occupiedPos);
        UniversalPlasticEntity entity = material.createEntity(
            helper.getLevel(),
            data.geometry().placementPosition(absolutePos, PlasticEntityOrientation.DEFAULT),
            material.productBlock().defaultBlockState(),
            stack,
            PlasticEntityOrientation.DEFAULT
        );
        entity.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(entity), "failed to add " + material.key() + " molded product");
        return entity;
    }

    private static MoldedPlasticData fullData(EditableMoldingModel model) {
        var baked = MoldingModelBaker.bake(model);
        return MoldedPlasticData.manufacture(
            model,
            baked,
            new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), 250),
            250
        );
    }

    /** 按模型自身的最小熔体量制造，供 250 mB 不够成型的大件使用。 */
    private static MoldedPlasticData completeData(EditableMoldingModel model) {
        var baked = MoldingModelBaker.bake(model);
        int melt = baked.analysis().minimumMeltMillibuckets();
        return MoldedPlasticData.manufacture(
            model,
            baked,
            new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), melt),
            melt
        );
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
