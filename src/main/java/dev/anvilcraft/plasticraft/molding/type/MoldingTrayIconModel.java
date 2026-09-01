package dev.anvilcraft.plasticraft.molding.type;

import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingTransform;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 支架类型图标的内置模型，不依赖玩家蓝图文件。 */
public final class MoldingTrayIconModel {
    private static final EditableMoldingModel MODEL = createModel();

    private MoldingTrayIconModel() {
    }

    public static EditableMoldingModel model() {
        return MODEL;
    }

    private static EditableMoldingModel createModel() {
        return new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Tray type icon",
            MoldingProductTypes.TRAY_ID,
            List.of(
                element(
                    "e3821cba-de07-4ac4-8371-f8e2a2374baa",
                    "Outer north",
                    vec(18, 16, 16),
                    vec(32, 18, 18),
                    vec(0, 4, 0),
                    MoldingVec3.ZERO,
                    vec(24, 17, 24)
                ),
                element(
                    "9b9ab77d-bb24-4e77-873e-a47fd205e618",
                    "Outer south",
                    vec(16, 16, 16),
                    vec(30, 18, 18),
                    vec(0, 4, 14),
                    MoldingVec3.ZERO,
                    vec(24, 17, 10)
                ),
                element(
                    "3630ba7d-2081-4fc8-8f87-d42593fdc30d",
                    "Diagonal northeast",
                    vec(16, 16, 12),
                    vec(18, 18, 32),
                    vec(7, 4, 2),
                    vec(0, 45, 0),
                    vec(17, 17, 22)
                ),
                element(
                    "5ec95a8f-50b5-4844-9c65-ecf53a4ab0b0",
                    "Diagonal northwest",
                    vec(16, 16, 12),
                    vec(18, 18, 32),
                    vec(7, 4, 2),
                    vec(0, -45, 0),
                    vec(17, 17, 22)
                ),
                element(
                    "c4cf4c59-75b0-4a77-a27d-bc15b49ee459",
                    "Cross north south",
                    vec(16, 16, 16),
                    vec(18, 18, 30),
                    vec(0, 4, 0),
                    MoldingVec3.ZERO,
                    vec(24, 17, 24)
                ),
                element(
                    "b2c835ec-2dcb-4173-ae46-0367d9546cb5",
                    "Cross east west",
                    vec(16, 16, 16),
                    vec(18, 18, 30),
                    vec(14, 4, 2),
                    MoldingVec3.ZERO,
                    vec(10, 17, 22)
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
        MoldingVec3 translation,
        MoldingVec3 rotation,
        MoldingVec3 pivot
    ) {
        return new MoldingElement(
            UUID.fromString(id),
            name,
            Optional.empty(),
            from,
            to,
            new MoldingTransform(translation, rotation, MoldingVec3.ONE, pivot),
            true,
            false
        );
    }

    private static MoldingVec3 vec(double x, double y, double z) {
        return new MoldingVec3(x, y, z);
    }
}
