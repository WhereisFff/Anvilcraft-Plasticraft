package dev.anvilcraft.plasticraft.recipe;

import dev.dubhe.anvilcraft.block.entity.PlasmaJetsBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** 强化喷流状态写在本体 BE 的 addon 字段里，而不是 Mixin 附加成员。 */
public final class EnhancedPlasmaJets {
    public static final String TAG_ENHANCED = "enhanced";
    public static final String TAG_LAYERED_FUEL = "layered_fuel";

    private EnhancedPlasmaJets() {
    }

    public static boolean isEnhanced(PlasmaJetsBlockEntity jet) {
        return jet.getAddonData().getBoolean(TAG_ENHANCED);
    }

    public static void setEnhanced(PlasmaJetsBlockEntity jet, boolean enhanced) {
        if (isEnhanced(jet) == enhanced) return;
        jet.getAddonData().putBoolean(TAG_ENHANCED, enhanced);
        Level level = jet.getLevel();
        if (level != null && enhanced) {
            EnhancedPlasmaJetHeat.promoteProducer(level, jet.getBlockPos());
        }
        jet.syncToClient();
    }

    public static boolean usesLayeredFuel(PlasmaJetsBlockEntity jet) {
        return jet.getAddonData().getBoolean(TAG_LAYERED_FUEL);
    }

    public static void setUsesLayeredFuel(PlasmaJetsBlockEntity jet, boolean layeredFuel) {
        if (usesLayeredFuel(jet) == layeredFuel) return;
        jet.getAddonData().putBoolean(TAG_LAYERED_FUEL, layeredFuel);
        jet.syncToClient();
    }

    public static BlockPos basePos(PlasmaJetsBlockEntity jet) {
        BlockPos cauldronPos = jet.getCauldronPos();
        return cauldronPos == null
            ? jet.getBlockPos().below(jet.getTubeWalls().size() + 1)
            : cauldronPos;
    }
}
