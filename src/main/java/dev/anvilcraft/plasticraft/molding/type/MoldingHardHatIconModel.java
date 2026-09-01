package dev.anvilcraft.plasticraft.molding.type;

import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingTransform;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 已知有效的安全帽几何,供校验测试使用;类型按钮图标另有内置橙色副本。 */
public final class MoldingHardHatIconModel {
    private static final MoldingVec3 COMMON_PIVOT = vec(24, 22, 24);
    private static final EditableMoldingModel MODEL = createModel();

    private MoldingHardHatIconModel() {
    }

    public static EditableMoldingModel model() {
        return MODEL;
    }

    private static EditableMoldingModel createModel() {
        return new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Allay hard hat type icon",
            MoldingProductTypes.ALLAY_HARD_HAT_ID,
            List.of(
                element(
                    "7c2f1a90-4b3e-4d6a-9c11-2e8f0a1b4d55",
                    "Brim",
                    vec(18.5, 22, 18.5),
                    vec(29.5, 23, 29.5),
                    MoldingVec3.ZERO
                ),
                element(
                    "b91e6d22-8a47-4f0c-81d3-6c5e9a2f7710",
                    "Crown",
                    vec(20, 23, 20),
                    vec(28, 28, 28),
                    MoldingVec3.ZERO
                )
            ),
            List.of()
        );
    }

    private static MoldingElement element(
        String id,
        String name,
        MoldingVec3 from,
        MoldingVec3 to,
        MoldingVec3 translation
    ) {
        return new MoldingElement(
            UUID.fromString(id),
            name,
            Optional.empty(),
            from,
            to,
            new MoldingTransform(
                translation,
                MoldingVec3.ZERO,
                MoldingVec3.ONE,
                COMMON_PIVOT.subtract(translation)
            ),
            true,
            false
        );
    }

    private static MoldingVec3 vec(double x, double y, double z) {
        return new MoldingVec3(x, y, z);
    }
}
