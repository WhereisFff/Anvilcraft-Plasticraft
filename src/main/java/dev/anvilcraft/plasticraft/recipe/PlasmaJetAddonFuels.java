package dev.anvilcraft.plasticraft.recipe;

import dev.dubhe.anvilcraft.api.plasma.PlasmaJetFuelHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalInt;

/** 把实体锅与高热燃料接到本体喷流燃料扩展点。 */
public final class PlasmaJetAddonFuels {
    public static final PlasmaJetFuelHandler RESIN_CAULDRON = new ResinCauldronFuel();
    public static final PlasmaJetFuelHandler HIGH_HEAT = new HighHeatFuel();

    private PlasmaJetAddonFuels() {
    }

    private static final class ResinCauldronFuel implements PlasmaJetFuelHandler {
        @Override
        public TriState isIgnitedFuel(Level level, BlockPos cauldronPos) {
            return HardenedResinCauldronSupport.isIgnitedOil(level, cauldronPos)
                ? TriState.TRUE
                : TriState.DEFAULT;
        }

        @Override
        public TriState isValidBase(Level level, BlockPos cauldronPos) {
            Boolean valid = HardenedResinCauldronSupport.validBase(level, cauldronPos);
            if (valid == null) return TriState.DEFAULT;
            return valid ? TriState.TRUE : TriState.FALSE;
        }

        @Override
        public OptionalInt tryConsumeOnce(Level level, BlockPos cauldronPos, boolean simulate) {
            return HardenedResinCauldronSupport.consumeOnce(level, cauldronPos, simulate);
        }

        @Override
        public @Nullable Integer fuelAmount(Level level, BlockPos cauldronPos) {
            return HardenedResinCauldronSupport.fluidAmount(level, cauldronPos);
        }
    }

    private static final class HighHeatFuel implements PlasmaJetFuelHandler {
        @Override
        public TriState isIgnitedFuel(Level level, BlockPos cauldronPos) {
            return EnhancedPlasmaJetFuel.isIgnitedHighHeatFuel(level, cauldronPos)
                ? TriState.TRUE
                : TriState.DEFAULT;
        }

        @Override
        public TriState isValidBase(Level level, BlockPos cauldronPos) {
            return EnhancedPlasmaJetFuel.isValidBase(level, cauldronPos)
                ? TriState.TRUE
                : TriState.DEFAULT;
        }
    }
}
