package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.init.ModParticles;
import dev.dubhe.anvilcraft.init.item.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** 处理真实出口附近的水蒸气灭火与原油蒸气点燃。 */
public final class EscapingVaporEffects {
    static final double HORIZONTAL_RANGE = 3.0D;
    static final double HEIGHT = 3.0D;
    static final double POSITION_EPSILON = 0.001D;
    private static final int OIL_CLOUD_LIFETIME = 8;
    private static final double OIL_INTERACTION_BELOW_OUTLET = 1.5D;
    private static final Map<ServerLevel, Map<BlockPos, OilVaporCloud>> OIL_CLOUDS = new WeakHashMap<>();

    private EscapingVaporEffects() {
    }

    static AABB effectArea(BlockPos outlet) {
        double centerX = outlet.getX() + 0.5D;
        double centerZ = outlet.getZ() + 0.5D;
        return new AABB(
            centerX - HORIZONTAL_RANGE,
            outlet.getY() - POSITION_EPSILON,
            centerZ - HORIZONTAL_RANGE,
            centerX + HORIZONTAL_RANGE,
            outlet.getY() + HEIGHT + POSITION_EPSILON,
            centerZ + HORIZONTAL_RANGE
        );
    }

    static boolean isInEffectRange(Entity entity, BlockPos outlet) {
        return isInEffectRange(entity.position(), outlet, 0.0D);
    }

    static void extinguishWaterVapor(ServerLevel level, List<BlockPos> outlets) {
        Set<Entity> burningEntities = new LinkedHashSet<>();
        Set<BlockPos> affectedBlocks = new LinkedHashSet<>();
        int range = (int) HORIZONTAL_RANGE;
        int height = (int) HEIGHT;
        for (BlockPos outlet : outlets) {
            burningEntities.addAll(level.getEntitiesOfClass(
                Entity.class,
                effectArea(outlet),
                entity -> entity.isOnFire() && isInEffectRange(entity, outlet)
            ));
            for (BlockPos candidate : BlockPos.betweenClosed(
                outlet.offset(-range, 0, -range),
                outlet.offset(range, height, range)
            )) {
                if (isBlockInEffectRange(candidate, outlet)) affectedBlocks.add(candidate.immutable());
            }
        }
        for (Entity entity : burningEntities) entity.clearFire();
        for (BlockPos pos : affectedBlocks) extinguishBlock(level, pos);
        extinguishOilVaporClouds(level, outlets);
    }

    static void updateOilVapor(
        ServerLevel level,
        BlockPos sourcePos,
        List<BlockPos> outlets,
        int amount
    ) {
        long gameTime = level.getGameTime();
        Map<BlockPos, OilVaporCloud> clouds = activeClouds(level, gameTime);
        OilVaporCloud cloud = clouds.get(sourcePos);
        if (cloud == null) {
            cloud = new OilVaporCloud();
            clouds.put(sourcePos.immutable(), cloud);
        }
        cloud.outlets = List.copyOf(outlets);
        cloud.amount = amount;
        cloud.expiresAt = gameTime + OIL_CLOUD_LIFETIME;
    }

    public static boolean isOilVaporIgnited(ServerLevel level, BlockPos sourcePos) {
        OilVaporCloud cloud = activeClouds(level, level.getGameTime()).get(sourcePos);
        return cloud != null && cloud.ignited;
    }

    public static boolean igniteOilVapor(ServerLevel level, Vec3 position) {
        OilVaporCloud selected = closestCloud(level, position, OIL_INTERACTION_BELOW_OUTLET);
        if (selected == null || selected.ignited) return false;
        selected.ignited = true;
        emitOilVaporFlames(level, selected);
        return true;
    }

    static void tickOilVapor(ServerLevel level, BlockPos sourcePos) {
        OilVaporCloud cloud = activeClouds(level, level.getGameTime()).get(sourcePos);
        if (cloud != null && cloud.ignited) emitOilVaporFlames(level, cloud);
    }

    public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        boolean flintAndSteel = stack.is(Items.FLINT_AND_STEEL);
        boolean fireCharge = stack.is(Items.FIRE_CHARGE);
        if (!flintAndSteel && !fireCharge || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!igniteOilVapor(level, event.getHitVec().getLocation())) return;

        Player player = event.getEntity();
        if (flintAndSteel) {
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(event.getHand()));
        } else if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(
            null,
            event.getPos(),
            flintAndSteel ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.FIRECHARGE_USE,
            SoundSource.BLOCKS,
            1.0F,
            1.0F
        );
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, event.getHitVec().getLocation());
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
    }

    public static void tryIgniteOilVapor(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        ItemEntity item = entity instanceof ItemEntity itemEntity ? itemEntity : null;
        boolean consumedStarter = item != null && item.getItem().is(ModItemTags.FIRE_STARTER);
        boolean reusableStarter = item != null && item.getItem().is(ModItemTags.UNBROKEN_FIRE_STARTER);
        if (!entity.isOnFire() && !consumedStarter && !reusableStarter) return;
        if (!igniteOilVapor(level, entity.position()) || !consumedStarter) return;
        item.getItem().shrink(1);
        if (item.getItem().isEmpty()) item.discard();
    }

    private static Map<BlockPos, OilVaporCloud> activeClouds(ServerLevel level, long gameTime) {
        Map<BlockPos, OilVaporCloud> clouds = OIL_CLOUDS.computeIfAbsent(level, ignored -> new HashMap<>());
        clouds.values().removeIf(cloud -> cloud.expiresAt < gameTime);
        return clouds;
    }

    private static OilVaporCloud closestCloud(ServerLevel level, Vec3 position, double belowOutlet) {
        OilVaporCloud closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (OilVaporCloud cloud : activeClouds(level, level.getGameTime()).values()) {
            for (BlockPos outlet : cloud.outlets) {
                if (!isInEffectRange(position, outlet, belowOutlet)) continue;
                double distance = position.distanceToSqr(outlet.getCenter());
                if (distance >= closestDistance) continue;
                closestDistance = distance;
                closest = cloud;
            }
        }
        return closest;
    }

    private static boolean isInEffectRange(Vec3 position, BlockPos outlet, double belowOutlet) {
        double deltaX = position.x - (outlet.getX() + 0.5D);
        double deltaZ = position.z - (outlet.getZ() + 0.5D);
        return deltaX * deltaX + deltaZ * deltaZ <= HORIZONTAL_RANGE * HORIZONTAL_RANGE
            && position.y >= outlet.getY() - belowOutlet - POSITION_EPSILON
            && position.y <= outlet.getY() + HEIGHT + POSITION_EPSILON;
    }

    private static boolean isBlockInEffectRange(BlockPos pos, BlockPos outlet) {
        int deltaX = pos.getX() - outlet.getX();
        int deltaZ = pos.getZ() - outlet.getZ();
        return deltaX * deltaX + deltaZ * deltaZ <= HORIZONTAL_RANGE * HORIZONTAL_RANGE;
    }

    private static void extinguishBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BaseFireBlock || state.is(BlockTags.FIRE)) {
            level.removeBlock(pos, false);
            level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0F, 1.0F);
            return;
        }
        if (state.getBlock() instanceof CampfireBlock
            && state.hasProperty(BlockStateProperties.LIT)
            && state.getValue(BlockStateProperties.LIT)) {
            CampfireBlock.dowse(null, level, pos, state);
            level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LIT, false));
            level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0F, 1.0F);
            return;
        }
        if (AbstractCandleBlock.isLit(state)) AbstractCandleBlock.extinguish(null, state, level, pos);
    }

    private static void extinguishOilVaporClouds(ServerLevel level, List<BlockPos> waterOutlets) {
        for (OilVaporCloud cloud : activeClouds(level, level.getGameTime()).values()) {
            if (!cloud.ignited) continue;
            boolean inRange = cloud.outlets.stream().anyMatch(oilOutlet -> waterOutlets.stream()
                .anyMatch(waterOutlet -> isInEffectRange(oilOutlet.getCenter(), waterOutlet, 0.0D)));
            if (inRange) cloud.ignited = false;
        }
    }

    private static void emitOilVaporFlames(ServerLevel level, OilVaporCloud cloud) {
        if (cloud.outlets.isEmpty() || cloud.amount <= 0) return;
        int count = Math.min(
            cloud.outlets.size(),
            CondenserTowerProcess.outletVaporParticleCount(cloud.amount)
        );
        int phase = Math.floorMod(level.getGameTime(), cloud.outlets.size());
        double intensity = Math.clamp(
            cloud.amount / (double) CondenserTowerProcess.ENHANCED_VAPORIZATION_PER_JET,
            0.1D,
            1.0D
        );
        for (int index = 0; index < count; index++) {
            BlockPos outlet = cloud.outlets.get(Math.floorMod(phase + index, cloud.outlets.size()));
            Vec3 center = outlet.getBottomCenter();
            level.sendParticles(
                ModParticles.GASEOUS_OIL_FLAME.get(),
                center.x,
                center.y + 0.03D,
                center.z,
                0,
                0.16D + intensity * 0.18D,
                0.55D + intensity * 0.45D,
                level.getRandom().nextDouble(),
                1.0D
            );
        }
    }

    private static final class OilVaporCloud {
        private List<BlockPos> outlets = new ArrayList<>();
        private long expiresAt;
        private int amount;
        private boolean ignited;
    }
}
