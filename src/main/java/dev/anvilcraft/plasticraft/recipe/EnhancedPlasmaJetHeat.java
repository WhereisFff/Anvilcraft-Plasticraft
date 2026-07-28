package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.api.blockentity.EnhancedPlasmaJetExtension;
import dev.dubhe.anvilcraft.api.heat.HeatRecorder;
import dev.dubhe.anvilcraft.api.heat.HeatTier;
import dev.dubhe.anvilcraft.api.heat.HeatTierLine;
import dev.dubhe.anvilcraft.api.heat.HeaterInfo;
import dev.dubhe.anvilcraft.api.heat.HeaterManager;
import dev.dubhe.anvilcraft.block.entity.PlasmaJetsBlockEntity;
import dev.dubhe.anvilcraft.init.ModHeaterInfos;
import dev.dubhe.anvilcraft.init.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Optional;

/** 将强化喷流注册为可达到白炽温度的热源。 */
public final class EnhancedPlasmaJetHeat {
    private static final HeaterInfo<PlasmaJetsBlockEntity> NO_MAGNET = HeatRecorder.registerProducerInfo(
        HeaterInfo.blockEntity(
            EnhancedPlasmaJetHeat::getEnhancedJet,
            jet -> jet.getHeatingPoses().getFirst(),
            HeatTierLine.always(HeatTier.INCANDESCENT, 2)
        )
    );
    private static final HeaterInfo<PlasmaJetsBlockEntity> MAGNET = HeatRecorder.registerProducerInfo(
        HeaterInfo.blockEntity(
            EnhancedPlasmaJetHeat::getEnhancedJet,
            jet -> jet.getHeatingPoses().getSecond(),
            HeatTierLine.always(HeatTier.INCANDESCENT, 20)
        )
    );

    private EnhancedPlasmaJetHeat() {
    }

    public static HeaterInfo<?> replaceOrdinary(HeaterInfo<?> ordinary) {
        if (ordinary == ModHeaterInfos.NO_MAGNET_PLASMA_JETS) return NO_MAGNET;
        if (ordinary == ModHeaterInfos.MAGNET_PLASMA_JETS) return MAGNET;
        return ordinary;
    }

    public static void promoteProducer(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        HeaterManager.removeProducer(pos, level, ModHeaterInfos.NO_MAGNET_PLASMA_JETS);
        HeaterManager.removeProducer(pos, level, ModHeaterInfos.MAGNET_PLASMA_JETS);
        HeaterManager.addProducer(pos, level, NO_MAGNET);
        HeaterManager.addProducer(pos, level, MAGNET);
    }

    public static void removeProducer(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        HeaterManager.removeProducer(pos, level, NO_MAGNET);
        HeaterManager.removeProducer(pos, level, MAGNET);
    }

    private static Optional<PlasmaJetsBlockEntity> getEnhancedJet(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return Optional.empty();
        return level.getBlockEntity(pos, ModBlockEntities.PLASMA_JETS.get())
            .filter(jet -> jet instanceof EnhancedPlasmaJetExtension extension
                && extension.plasticraft$isEnhanced());
    }
}
