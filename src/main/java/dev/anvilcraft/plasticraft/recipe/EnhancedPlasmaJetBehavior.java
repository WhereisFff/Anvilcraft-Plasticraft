package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.block.HighHeatFuelCauldronBlock;
import dev.anvilcraft.plasticraft.init.PlasticraftParticles;
import dev.dubhe.anvilcraft.api.heat.HeaterInfo;
import dev.dubhe.anvilcraft.api.plasma.PlasmaJetBehavior;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.entity.PlasmaJetsBlockEntity;
import dev.dubhe.anvilcraft.init.ModParticles;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** 强化喷流的热源、伤害、时长与粒子。 */
public final class EnhancedPlasmaJetBehavior implements PlasmaJetBehavior {
    public static final EnhancedPlasmaJetBehavior INSTANCE = new EnhancedPlasmaJetBehavior();
    private static final int TOP_CELL_PARTICLES_PER_TICK = 3;
    private static final double TOP_CELL_VERTICAL_SPEED = 0.2D;

    private EnhancedPlasmaJetBehavior() {
    }

    @Override
    public void onServerTickHead(PlasmaJetsBlockEntity jet, ServerLevel level) {
        if (EnhancedPlasmaJets.isEnhanced(jet)) return;
        EnhancedPlasmaJetFuel.FuelMode mode = EnhancedPlasmaJetFuel.activate(level, EnhancedPlasmaJets.basePos(jet));
        if (mode == EnhancedPlasmaJetFuel.FuelMode.NONE) return;
        EnhancedPlasmaJets.setEnhanced(jet, true);
        EnhancedPlasmaJets.setUsesLayeredFuel(jet, mode == EnhancedPlasmaJetFuel.FuelMode.LAYERED);
        if (mode == EnhancedPlasmaJetFuel.FuelMode.LAYERED) {
            jet.setDuration(EnhancedPlasmaJetFuel.LAYERED_DURATION);
        }
    }

    @Override
    public void onServerTickTail(PlasmaJetsBlockEntity jet, ServerLevel level) {
        if (!jet.isRemoved() && level.getBlockState(jet.getBlockPos()).is(ModBlocks.PLASMA_JETS.get())) {
            CondenserTowerProcess.tickSmallContainerAboveJet(level, jet.getBlockPos());
        }
    }

    @Override
    public void afterRaise(PlasmaJetsBlockEntity from, PlasmaJetsBlockEntity to) {
        if (EnhancedPlasmaJets.isEnhanced(to)) {
            EnhancedPlasmaJetHeat.promoteProducer(to.getLevel(), to.getBlockPos());
        }
    }

    @Override
    public HeaterInfo<?> heatInfo(PlasmaJetsBlockEntity jet, HeaterInfo<?> ordinary) {
        return EnhancedPlasmaJets.isEnhanced(jet)
            ? EnhancedPlasmaJetHeat.replaceOrdinary(ordinary)
            : ordinary;
    }

    @Override
    public float modifyDamage(PlasmaJetsBlockEntity jet, float ordinary) {
        return EnhancedPlasmaJets.isEnhanced(jet) ? ordinary * 2.0F : ordinary;
    }

    @Override
    public boolean refreshDuration(PlasmaJetsBlockEntity jet, Level level) {
        if (!EnhancedPlasmaJets.isEnhanced(jet)) return false;
        if (EnhancedPlasmaJets.usesLayeredFuel(jet)) {
            int duration = jet.getDuration() - 1;
            jet.setDuration(duration);
            if (duration < 0) {
                if (EnhancedPlasmaJetFuel.consumeLayeredFuel(level, EnhancedPlasmaJets.basePos(jet))) {
                    jet.setDuration(duration + EnhancedPlasmaJetFuel.LAYERED_DURATION);
                } else {
                    level.removeBlock(jet.getBlockPos(), false);
                }
            }
            return true;
        }
        if (!EnhancedPlasmaJetFuel.consumeContinuousFuel(level, EnhancedPlasmaJets.basePos(jet))) {
            level.removeBlock(jet.getBlockPos(), false);
        }
        return true;
    }

    @Override
    public ParticleOptions particle(PlasmaJetsBlockEntity jet, ParticleOptions ordinary) {
        return EnhancedPlasmaJets.isEnhanced(jet)
            ? PlasticraftParticles.ENHANCED_PLASMA_JETS.get()
            : ordinary;
    }

    @Override
    public void extraParticles(PlasmaJetsBlockEntity jet, ClientLevel level) {
        BlockPos jetPos = jet.getBlockPos();
        BlockState stateAbove = level.getBlockState(jetPos.above());
        if (!(stateAbove.getBlock() instanceof LargeCauldronBlock)
            || !stateAbove.hasProperty(LargeCauldronBlock.HALF)
            || stateAbove.getValue(LargeCauldronBlock.HALF).getOffsetY() != 0) {
            return;
        }
        Vec3 start = jetPos.getBottomCenter().add(0.0D, 0.05D, 0.0D);
        RandomSource random = level.getRandom();
        ParticleOptions particle = EnhancedPlasmaJets.isEnhanced(jet)
            ? PlasticraftParticles.ENHANCED_PLASMA_JETS.get()
            : ModParticles.PLASMA_JETS.get();
        for (int i = 0; i < TOP_CELL_PARTICLES_PER_TICK; i++) {
            level.addParticle(
                particle,
                true,
                start.x,
                start.y,
                start.z,
                (random.nextIntBetweenInclusive(0, 20) - 10) / 100.0D,
                TOP_CELL_VERTICAL_SPEED,
                (random.nextIntBetweenInclusive(0, 20) - 10) / 100.0D
            );
        }
    }

    @Override
    public void onRemoved(PlasmaJetsBlockEntity jet) {
        Level level = jet.getLevel();
        if (level == null) return;
        EnhancedPlasmaJetHeat.removeProducer(level, jet.getBlockPos());
        if (!EnhancedPlasmaJets.isEnhanced(jet)
            || !EnhancedPlasmaJets.usesLayeredFuel(jet)
            || level.isClientSide()) {
            return;
        }
        boolean raising = jet.getTubeWalls().stream().anyMatch(
            layer -> layer.first().getFirst().south().equals(jet.getBlockPos())
        );
        if (!raising) {
            HighHeatFuelCauldronBlock.clearSpent(level, EnhancedPlasmaJets.basePos(jet));
        }
    }
}
