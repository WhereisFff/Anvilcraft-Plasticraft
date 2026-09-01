package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.entity.ClearPlasticBeaconInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 让信标将无方块占位的透明塑料实体视作对应颜色的染色玻璃。 */
@Mixin(BeaconBlockEntity.class)
abstract class BeaconBlockEntityMixin {
    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getHeight"
                + "(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"
        )
    )
    private static int plasticraft$extendBeamScanForClearPlastic(
        Level level,
        Heightmap.Types heightmapType,
        int x,
        int z
    ) {
        return ClearPlasticBeaconInteraction.scanHeight(level, x, z, level.getHeight(heightmapType, x, z));
    }

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getBeaconColorMultiplier"
                + "(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;"
                + "Lnet/minecraft/core/BlockPos;)Ljava/lang/Integer;"
        )
    )
    private static Integer plasticraft$readClearPlasticBeamColor(
        BlockState state,
        LevelReader levelReader,
        BlockPos beamPos,
        BlockPos beaconPos
    ) {
        Integer color = state.getBeaconColorMultiplier(levelReader, beamPos, beaconPos);
        if (color != null || !(levelReader instanceof Level level)) return color;
        return ClearPlasticBeaconInteraction.beamColorAt(level, beamPos);
    }
}
