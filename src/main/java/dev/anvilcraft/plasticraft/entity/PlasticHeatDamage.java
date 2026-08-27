package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityPhysics;
import dev.dubhe.anvilcraft.api.block.IDamagingHeater;
import dev.dubhe.anvilcraft.init.entity.ModDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** 成型塑料制品站在会灼伤实体的热源上时沿用该热源的伤害语义。 */
public final class PlasticHeatDamage {
    private static final float CAMPFIRE_DAMAGE = 1.0F;
    private static final float HEATER_DAMAGE = 4.0F;

    private PlasticHeatDamage() {
    }

    public static void hurtFromSupport(UniversalPlasticEntity entity) {
        Level level = entity.level();
        if (level.isClientSide || entity.isRemoved()) return;
        AABB bounds = entity.getBoundingBox();
        int minX = Mth.floor(bounds.minX + PlasticEntityPhysics.FACE_EPSILON);
        int maxX = Mth.floor(bounds.maxX - PlasticEntityPhysics.FACE_EPSILON);
        int minZ = Mth.floor(bounds.minZ + PlasticEntityPhysics.FACE_EPSILON);
        int maxZ = Mth.floor(bounds.maxZ - PlasticEntityPhysics.FACE_EPSILON);
        int y = Mth.floor(bounds.minY - PlasticEntityPhysics.FACE_EPSILON);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                pos.set(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (CampfireBlock.isLitCampfire(state)) {
                    entity.hurt(level.damageSources().campfire(), CAMPFIRE_DAMAGE);
                    return;
                }
                if (state.getBlock() instanceof IDamagingHeater heater && heater.isActive(state)) {
                    entity.hurt(ModDamageTypes.heaterBurn(level), HEATER_DAMAGE);
                    return;
                }
            }
        }
    }
}
