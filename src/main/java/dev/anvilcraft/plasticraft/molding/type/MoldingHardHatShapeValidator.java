package dev.anvilcraft.plasticraft.molding.type;

import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelBounds;

/**
 * 悦灵安全帽只限制占地和高度:非空、水平外接不超过 11x11 px、高度不超过 16 px。
 * 不限制落在工作区中心,也不沿用螺旋桨的叶片与轮毂规则。
 */
public final class MoldingHardHatShapeValidator {
    private static final double MAX_FOOTPRINT = 11.0D;
    private static final double MAX_HEIGHT = 16.0D;
    private static final double EPSILON = 1.0E-7D;

    private MoldingHardHatShapeValidator() {
    }

    public static MoldingTypeValidation validate(EditableMoldingModel model, BakedMoldingModel baked) {
        MoldingModelBounds bounds = MoldingModelBounds.visible(model).orElse(null);
        if (bounds == null) return MoldingTypeValidation.invalid("allay_hard_hat_empty");
        if (baked.analysis().volume() <= 0 && baked.surfaceMesh().isEmpty()) {
            return MoldingTypeValidation.invalid("allay_hard_hat_empty");
        }
        if (bounds.sizePixels().x() > MAX_FOOTPRINT + EPSILON
            || bounds.sizePixels().z() > MAX_FOOTPRINT + EPSILON) {
            return MoldingTypeValidation.invalid("allay_hard_hat_too_wide");
        }
        if (bounds.sizePixels().y() > MAX_HEIGHT + EPSILON) {
            return MoldingTypeValidation.invalid("allay_hard_hat_too_tall");
        }
        return MoldingTypeValidation.valid(0);
    }
}
