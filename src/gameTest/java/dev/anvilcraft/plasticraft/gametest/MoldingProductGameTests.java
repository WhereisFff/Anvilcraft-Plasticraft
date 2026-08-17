package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.anvilcraft.plasticraft.molding.bake.MoldingBarrierFace;
import dev.anvilcraft.plasticraft.molding.bake.MoldingAnvilShapeAnalysis;
import dev.anvilcraft.plasticraft.molding.bake.MoldingAnvilShapeAnalyzer;
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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.ArrayList;
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
    @TestHolder(description = "Chest and tank capacity boundaries remain exact")
    static void capacityBoundaries(ExtendedGameTestHelper helper) {
        checkCapacity(helper, MoldingProductTypes.CHEST_ID, 1728, 27, true);
        checkCapacity(helper, MoldingProductTypes.CHEST_ID, 64, 1, true);
        checkCapacity(helper, MoldingProductTypes.CHEST_ID, 63, 0, false);
        checkCapacity(helper, MoldingProductTypes.TANK_ID, 2744, 16, true);
        checkCapacity(helper, MoldingProductTypes.TANK_ID, 171, 1, true);
        checkCapacity(helper, MoldingProductTypes.TANK_ID, 170, 0, false);
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
    @TestHolder(description = "Only heat-resistant molded tanks retain lava; other materials discard every fluid")
    static void tankLavaHazardsFollowMaterial(ExtendedGameTestHelper helper) {
        EditableMoldingModel model = model(MoldingProductTypes.TANK_ID);
        for (PlasticMaterial material : PlasticMaterial.values()) {
            final MoldedPlasticData[] tank = {
                fullData(model).withMaterial(new FluidStack(material.melt(), 250))
            };
            boolean[] destroyed = {false};
            MoldedPlasticFluidHandler fluids = new MoldedPlasticFluidHandler(
                () -> Optional.of(tank[0]),
                replacement -> tank[0] = replacement,
                () -> ItemStack.EMPTY,
                () -> destroyed[0] = true
            );
            int capacity = fluids.getTankCapacity(0);
            int water = capacity / 2;
            check(water > 0, material.key() + " tank had no usable capacity");
            check(fluids.fill(new FluidStack(Fluids.WATER, water), IFluidHandler.FluidAction.EXECUTE) == water,
                material.key() + " tank rejected water before the lava check");

            int lava = capacity - water;
            check(fluids.fill(new FluidStack(Fluids.LAVA, lava), IFluidHandler.FluidAction.SIMULATE) == lava,
                material.key() + " tank reported the wrong simulated lava transfer");
            check(!destroyed[0], material.key() + " tank was destroyed by simulated lava");
            check(fluids.fill(new FluidStack(Fluids.LAVA, lava), IFluidHandler.FluidAction.EXECUTE) == lava,
                material.key() + " tank reported the wrong executed lava transfer");

            if (material == PlasticMaterial.HEAT_RESISTANT) {
                check(!destroyed[0], "heat-resistant tank was destroyed by lava");
                check(fluids.getFluidInTank(0).is(Fluids.WATER)
                        && fluids.getFluidInTank(1).is(Fluids.LAVA),
                    "heat-resistant tank did not retain its fluids after lava fill");
            } else {
                check(destroyed[0], material.key() + " tank was not destroyed by lava");
                check(handleFluids(tank).isEmpty(), material.key() + " tank retained fluid after lava destruction");
            }
        }
        helper.succeed();
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
        checkProductName(MoldingProductTypes.TRAY_ID, "screen.anvilcraftplasticraft.molding.type.tray");
        helper.succeed();
    }

    private static void checkProductName(
        ResourceLocation type,
        String suffixKey
    ) {
        EditableMoldingModel selectedModel = MoldingProductTypes.ANVIL_ID.equals(type)
            ? anvilModel(type)
            : MoldingProductTypes.TRAY_ID.equals(type) ? trayModel(type) : model(type);
        var baked = MoldingModelBaker.bake(selectedModel);
        int melt = baked.analysis().minimumMeltMillibuckets();
        MoldedPlasticData data = MoldedPlasticData.manufacture(
            selectedModel,
            baked,
            new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), melt),
            melt
        );
        ItemStack stack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(stack, data);
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

    private static MoldedPlasticData fullData(EditableMoldingModel model) {
        var baked = MoldingModelBaker.bake(model);
        return MoldedPlasticData.manufacture(
            model,
            baked,
            new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), 250),
            250
        );
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
