package dev.anvilcraft.plasticraft.molding.type;

import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingTransform;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 螺旋桨类型图标的内置模型，不依赖玩家蓝图文件。 */
public final class MoldingPropellerIconModel {
    private static final MoldingVec3 ICON_OFFSET = vec(0, 4, 0);
    private static final MoldingVec3 COMMON_PIVOT = vec(24, 17, 24).add(ICON_OFFSET);
    private static final EditableMoldingModel MODEL = createModel();

    private MoldingPropellerIconModel() {
    }

    public static EditableMoldingModel model() {
        return MODEL;
    }

    private static EditableMoldingModel createModel() {
        return new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Propeller type icon",
            MoldingProductTypes.PROPELLER_ID,
            List.of(
                element(
                    "5a54f13b-41d9-419e-9f42-c19ca196365b",
                    "North south blade",
                    vec(23, 16, 18),
                    vec(25, 16, 30),
                    vec(0, 1, 0).add(ICON_OFFSET)
                ),
                element(
                    "f30ac88a-696b-4bf6-8914-fd912aa79013",
                    "Core",
                    vec(16, 16, 16),
                    vec(18, 17, 18),
                    vec(7, 0, 7).add(ICON_OFFSET)
                ),
                element(
                    "6f02eea7-5433-4575-b31d-c610bb3293e2",
                    "East west blade",
                    vec(11, 16, 16),
                    vec(23, 16, 18),
                    vec(7, 1, 7).add(ICON_OFFSET)
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
