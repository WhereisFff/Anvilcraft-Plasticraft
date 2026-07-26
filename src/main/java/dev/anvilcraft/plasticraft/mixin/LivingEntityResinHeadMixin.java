package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.item.ResinAnvilHammerItem;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mixin(LivingEntity.class)
abstract class LivingEntityResinHeadMixin {
    private static final double CEILING_RESTITUTION = 1.2D;
    private static final double ELYTRA_RESTITUTION = 0.75D;
    private static final double HEADBUTT_ENTITY_MULTIPLIER = 1.5D;
    private static final double MAX_ENTITY_IMPULSE = 1.2D;
    private static final double MIN_SPEED_THRESHOLD = 0.08D;
    private static final double CLIP_EPSILON = 0.003D;

    @Unique
    @Nullable
    private Vec3 plasticraft$preTravelVelocity;

    @Unique
    private Vec3 plasticraft$preTravelPosition = Vec3.ZERO;

    @Inject(method = "travel", at = @At("HEAD"))
    private void plasticraft$capturePreTravel(Vec3 movementInput, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) {
            this.plasticraft$preTravelVelocity = null;
            return;
        }
        if (!(player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof ResinAnvilHammerItem)) {
            this.plasticraft$preTravelVelocity = null;
            return;
        }
        this.plasticraft$preTravelVelocity = self.getDeltaMovement();
        this.plasticraft$preTravelPosition = self.position();
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void plasticraft$applyHeadBehaviors(Vec3 movementInput, CallbackInfo ci) {
        Vec3 preVelocity = this.plasticraft$preTravelVelocity;
        if (preVelocity == null) return;
        this.plasticraft$preTravelVelocity = null;

        LivingEntity self = (LivingEntity) (Object) this;
        Player player = (Player) self;

        if (self.isFallFlying() && self.horizontalCollision) {
            plasticraft$handleElytraBounce(player, preVelocity);
            return;
        }

        if (preVelocity.y > MIN_SPEED_THRESHOLD) {
            if (self.verticalCollision && !self.verticalCollisionBelow) {
                plasticraft$handleCeilingBounce(player, preVelocity);
            }
            plasticraft$handleEntityHeadbutt(player, preVelocity);
        }
    }

    @Unique
    private void plasticraft$handleElytraBounce(Player player, Vec3 preVelocity) {
        Vec3 postPosition = player.position();
        Vec3 actualDisplacement = postPosition.subtract(this.plasticraft$preTravelPosition);

        double vx = preVelocity.x;
        double vz = preVelocity.z;
        boolean clippedX = Math.abs(preVelocity.x) > MIN_SPEED_THRESHOLD
            && Math.abs(actualDisplacement.x - preVelocity.x) > CLIP_EPSILON;
        boolean clippedZ = Math.abs(preVelocity.z) > MIN_SPEED_THRESHOLD
            && Math.abs(actualDisplacement.z - preVelocity.z) > CLIP_EPSILON;

        if (!clippedX && !clippedZ) return;

        double newX = clippedX ? -vx * ELYTRA_RESTITUTION : vx;
        double newZ = clippedZ ? -vz * ELYTRA_RESTITUTION : vz;
        player.setDeltaMovement(newX, preVelocity.y, newZ);
        player.resetFallDistance();
        player.hasImpulse = true;
        player.hurtMarked = true;
        plasticraft$playResinSound(player);
    }

    @Unique
    private void plasticraft$handleCeilingBounce(Player player, Vec3 preVelocity) {
        Vec3 current = player.getDeltaMovement();
        double downSpeed = preVelocity.y * CEILING_RESTITUTION;
        player.setDeltaMovement(current.x, -downSpeed, current.z);
        player.resetFallDistance();
        player.hasImpulse = true;
        player.hurtMarked = true;
        plasticraft$playResinSound(player);
    }

    @Unique
    private void plasticraft$handleEntityHeadbutt(Player player, Vec3 preVelocity) {
        AABB playerBox = player.getBoundingBox();
        AABB headProbe = new AABB(
            playerBox.minX - 0.1D, playerBox.maxY, playerBox.minZ - 0.1D,
            playerBox.maxX + 0.1D, playerBox.maxY + 0.3D, playerBox.maxZ + 0.1D
        );
        List<Entity> targets = player.level().getEntities(
            player,
            headProbe,
            entity -> entity.isAlive() && !entity.isSpectator() && entity.isPushable()
        );
        if (targets.isEmpty()) return;

        double impulse = Math.min(MAX_ENTITY_IMPULSE, preVelocity.y * HEADBUTT_ENTITY_MULTIPLIER);
        Set<UUID> launchedComponents = new HashSet<>();
        for (Entity target : targets) {
            Entity impulseTarget = EntityBondManager.resolveLeader(player.level(), target);
            if (impulseTarget == null || !impulseTarget.isAlive()) impulseTarget = target;
            if (!launchedComponents.add(impulseTarget.getUUID())) continue;
            Vec3 targetVelocity = impulseTarget.getDeltaMovement();
            impulseTarget.setDeltaMovement(targetVelocity.x, targetVelocity.y + impulse, targetVelocity.z);
            impulseTarget.hasImpulse = true;
            impulseTarget.hurtMarked = true;
        }
        if (!player.level().isClientSide) {
            player.getItemBySlot(EquipmentSlot.HEAD)
                .hurtAndBreak(1, player, EquipmentSlot.HEAD);
        }
        plasticraft$playResinSound(player);
    }

    @Unique
    private static void plasticraft$playResinSound(Player player) {
        player.level().playSound(
            null,
            player.blockPosition(),
            dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK.getDefaultState().getSoundType().getHitSound(),
            SoundSource.PLAYERS,
            0.8F,
            0.9F + player.getRandom().nextFloat() * 0.2F
        );
    }
}
