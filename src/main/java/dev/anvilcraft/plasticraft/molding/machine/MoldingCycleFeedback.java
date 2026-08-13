package dev.anvilcraft.plasticraft.molding.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.NoteBlock;

/** 成型舱整轮成功与停滞共用的紫色 F 音符反馈。 */
public final class MoldingCycleFeedback {
    public static final int NOTE_F = 11;
    public static final double PARTICLE_COLOR = NOTE_F / 24.0D;
    public static final float PITCH = NoteBlock.getPitchFromNote(NOTE_F);

    private MoldingCycleFeedback() {
    }

    public static void success(ServerLevel level, BlockPos chamberPos) {
        play(level, chamberPos, SoundEvents.NOTE_BLOCK_BELL.value());
    }

    public static void stalled(ServerLevel level, BlockPos chamberPos) {
        play(level, chamberPos, SoundEvents.NOTE_BLOCK_BASS.value());
    }

    private static void play(ServerLevel level, BlockPos chamberPos, SoundEvent sound) {
        double x = chamberPos.getX() + 0.5D;
        double y = chamberPos.getY() + 1.2D;
        double z = chamberPos.getZ() + 0.5D;
        level.sendParticles(
            ParticleTypes.NOTE,
            x,
            y,
            z,
            0,
            PARTICLE_COLOR,
            0.0D,
            0.0D,
            1.0D
        );
        level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, 1.0F, PITCH);
    }
}
