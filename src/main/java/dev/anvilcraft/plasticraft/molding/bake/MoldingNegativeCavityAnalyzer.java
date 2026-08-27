package dev.anvilcraft.plasticraft.molding.bake;

import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;

import java.util.BitSet;
import java.util.List;

/** 为储存容器选择反向绕序 cube 的空腔或壁语义。 */
public final class MoldingNegativeCavityAnalyzer {
    private MoldingNegativeCavityAnalyzer() {
    }

    public static MoldingFunctionalAnalysis analyzeSealed(
        EditableMoldingModel model,
        BakedMoldingModel baked
    ) {
        return analyze(model, baked, CavityKind.SEALED);
    }

    public static MoldingFunctionalAnalysis analyzeCauldron(
        EditableMoldingModel model,
        BakedMoldingModel baked
    ) {
        return analyze(model, baked, CavityKind.CAULDRON);
    }

    private static MoldingFunctionalAnalysis analyze(
        EditableMoldingModel model,
        BakedMoldingModel baked,
        CavityKind kind
    ) {
        MoldingVolumeMask currentVolume = baked.volumeMask();
        MoldingFunctionalAnalysis current = baked.functionalAnalysis();
        List<MoldingVolumeMask> negativeMasks = MoldingModelBaker.negativeVolumeMasks(model);
        for (MoldingVolumeMask negativeMask : negativeMasks) {
            if (!currentVolume.copyBits().intersects(negativeMask.copyBits())) continue;
            MoldingVolumeMask candidateVolume = subtract(currentVolume, negativeMask);
            MoldingFunctionalAnalysis candidate = MoldingShellAnalyzer.analyze(
                candidateVolume,
                baked.barrierFaces()
            );
            if (!kind.expands(current, candidate)) continue;
            currentVolume = candidateVolume;
            current = preserveOtherShapes(candidate, baked.functionalAnalysis());
        }
        return current;
    }

    private static MoldingVolumeMask subtract(MoldingVolumeMask source, MoldingVolumeMask removal) {
        BitSet cells = source.copyBits();
        cells.andNot(removal.copyBits());
        return MoldingVolumeMask.fromLongArray(
            source.sizeX(),
            source.sizeY(),
            source.sizeZ(),
            cells.toLongArray()
        );
    }

    private static MoldingFunctionalAnalysis preserveOtherShapes(
        MoldingFunctionalAnalysis candidate,
        MoldingFunctionalAnalysis original
    ) {
        return new MoldingFunctionalAnalysis(
            candidate.cavityMask(),
            candidate.cavityCount(),
            candidate.cavityVolume(),
            candidate.shellVolume(),
            candidate.internalDecoration(),
            original.anvilShape(),
            original.trayShape(),
            candidate.cauldronShape()
        );
    }

    private static boolean contains(MoldingVolumeMask outer, MoldingVolumeMask inner) {
        BitSet missing = inner.copyBits();
        missing.andNot(outer.copyBits());
        return missing.isEmpty();
    }

    private enum CavityKind {
        SEALED {
            @Override
            boolean expands(MoldingFunctionalAnalysis current, MoldingFunctionalAnalysis candidate) {
                return candidate.cavityVolume() > current.cavityVolume()
                    && contains(candidate.cavityMask(), current.cavityMask());
            }
        },
        CAULDRON {
            @Override
            boolean expands(MoldingFunctionalAnalysis current, MoldingFunctionalAnalysis candidate) {
                MoldingCauldronShapeAnalysis next = candidate.cauldronShape();
                MoldingCauldronShapeAnalysis previous = current.cauldronShape();
                if (previous.valid() && !next.valid()) return false;
                return next.cavityVolume() > previous.cavityVolume()
                    && contains(next.cavityMask(), previous.cavityMask());
            }
        };

        abstract boolean expands(MoldingFunctionalAnalysis current, MoldingFunctionalAnalysis candidate);
    }
}
