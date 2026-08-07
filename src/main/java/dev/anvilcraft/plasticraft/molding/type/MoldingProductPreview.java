package dev.anvilcraft.plasticraft.molding.type;

import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.ManufacturedMoldingGeometry;
import dev.anvilcraft.plasticraft.molding.bake.MoldingBarrierFace;
import dev.anvilcraft.plasticraft.molding.bake.MoldingFunctionalAnalysis;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.bake.MoldingQuad;
import dev.anvilcraft.plasticraft.molding.bake.MoldingShellAnalyzer;
import dev.anvilcraft.plasticraft.molding.bake.MoldingTrayShapeAnalyzer;
import dev.anvilcraft.plasticraft.molding.bake.MoldingVolumeMask;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/** 以指定熔体数量预演实际制品类型，制造、GUI 和状态提示共用此结果。 */
public record MoldingProductPreview(
    ResourceLocation requestedType,
    ResourceLocation finalType,
    int capacity,
    MoldingVolumeMask formedVolume,
    MoldingFunctionalAnalysis analysis
) {
    public MoldingProductPreview {
        formedVolume = formedVolume.copy();
    }

    @Override
    public MoldingVolumeMask formedVolume() {
        return this.formedVolume.copy();
    }

    public boolean downgraded() {
        return !this.requestedType.equals(this.finalType);
    }

    public static MoldingProductPreview evaluate(
        EditableMoldingModel model,
        BakedMoldingModel baked,
        int meltMillibuckets
    ) {
        return evaluate(model, baked, meltMillibuckets, false, false);
    }

    /**
     * 计算资源覆盖规则下的预览。内部调试覆盖始终使用完整模型和外接体积，多相超限合金只在
     * 调用方确认需要越过类型校验时启用最大属性估算。
     */
    public static MoldingProductPreview evaluate(
        EditableMoldingModel model,
        BakedMoldingModel baked,
        int meltMillibuckets,
        boolean typeOverride,
        boolean creativeOverride
    ) {
        if (meltMillibuckets < 0) throw new IllegalArgumentException("Melt amount must not be negative");
        MoldingVolumeMask bakedVolume = baked.volumeMask();
        int maximumCells = (int) Math.min(
            bakedVolume.cellCount(),
            Math.max(0L, (long) meltMillibuckets * 4L)
        );
        MoldingVolumeMask formedVolume = creativeOverride
            ? bakedVolume
            : MoldingModelBaker.createPaidVolumeMask(bakedVolume, baked.fillOrder(), maximumCells);
        int requiredCells = baked.analysis().volume();
        double formedProportion = requiredCells == 0
            ? 1.0D
            : Math.min(1.0D, formedVolume.volume() / (double) requiredCells);
        ManufacturedMoldingGeometry geometry = MoldingModelBaker.createManufacturedGeometry(
            model,
            formedProportion
        );
        Set<MoldingBarrierFace> barriers =
            MoldingModelBaker.barrierFacesFromZeroThickness(
                geometry.surfaceMesh().stream().filter(MoldingQuad::doubleSided).toList()
        );
        MoldingFunctionalAnalysis analysis = typeOverride || creativeOverride
            ? maximumCaseAnalysis(baked, bakedVolume, geometry)
            : MoldingShellAnalyzer.analyze(formedVolume, barriers).withTrayShape(
                MoldingTrayShapeAnalyzer.analyze(formedVolume, geometry.surfaceMesh())
            );
        boolean fullyFormed = formedProportion >= 1.0D - 1.0E-7D;
        MoldingTypeValidation validation = fullyFormed
            ? MoldingProductTypes.validate(model.requestedType(), model, baked)
            : MoldingProductTypes.validate(model.requestedType(), analysis);
        boolean forced = typeOverride || creativeOverride;
        boolean retainsFunction = validation.valid()
            && (fullyFormed || !MoldingProductTypes.isTray(model.requestedType()));
        ResourceLocation finalType = forced || retainsFunction
            ? model.requestedType()
            : EditableMoldingModel.NORMAL_TYPE;
        int capacity = forced
            ? MoldingProductTypes.unitsPerCapacity(model.requestedType()) == 0
                ? 0
                : analysis.cavityVolume() / MoldingProductTypes.unitsPerCapacity(model.requestedType())
            : retainsFunction ? validation.capacity() : 0;
        return new MoldingProductPreview(
            model.requestedType(),
            finalType,
            capacity,
            formedVolume,
            analysis
        );
    }

    private static MoldingFunctionalAnalysis maximumCaseAnalysis(
        BakedMoldingModel baked,
        MoldingVolumeMask dimensions,
        ManufacturedMoldingGeometry geometry
    ) {
        int minX = dimensions.sizeX();
        int minY = dimensions.sizeY();
        int minZ = dimensions.sizeZ();
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;
        for (MoldingQuad quad : geometry.surfaceMesh()) {
            for (MoldingVec3 point : List.of(quad.first(), quad.second(), quad.third(), quad.fourth())) {
                minX = Math.min(minX, (int) Math.floor(point.x()));
                minY = Math.min(minY, (int) Math.floor(point.y()));
                minZ = Math.min(minZ, (int) Math.floor(point.z()));
                maxX = Math.max(maxX, (int) Math.ceil(point.x()));
                maxY = Math.max(maxY, (int) Math.ceil(point.y()));
                maxZ = Math.max(maxZ, (int) Math.ceil(point.z()));
            }
        }
        minX = Math.clamp(minX, 0, dimensions.sizeX());
        minY = Math.clamp(minY, 0, dimensions.sizeY());
        minZ = Math.clamp(minZ, 0, dimensions.sizeZ());
        maxX = Math.clamp(maxX, minX, dimensions.sizeX());
        maxY = Math.clamp(maxY, minY, dimensions.sizeY());
        maxZ = Math.clamp(maxZ, minZ, dimensions.sizeZ());
        if (maxX == minX) maxX = Math.min(dimensions.sizeX(), minX + 1);
        if (maxY == minY) maxY = Math.min(dimensions.sizeY(), minY + 1);
        if (maxZ == minZ) maxZ = Math.min(dimensions.sizeZ(), minZ + 1);
        MoldingVolumeMask maximumCavity = MoldingVolumeMask.filledBox(
            dimensions.sizeX(),
            dimensions.sizeY(),
            dimensions.sizeZ(),
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ
        );
        return new MoldingFunctionalAnalysis(
            maximumCavity,
            maximumCavity.isEmpty() ? 0 : 1,
            maximumCavity.volume(),
            0,
            false,
            baked.functionalAnalysis().anvilShape(),
            MoldingTrayShapeAnalyzer.analyze(dimensions, geometry.surfaceMesh())
        );
    }
}
