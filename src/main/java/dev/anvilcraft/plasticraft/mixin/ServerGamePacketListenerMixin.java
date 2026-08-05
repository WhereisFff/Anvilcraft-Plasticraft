package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexCollisionResolver;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让服务端玩家位置校验与实体移动使用相同的真实塑料凸体。 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "isPlayerCollidingWithAnythingNew", at = @At("HEAD"), cancellable = true)
    private void plasticraft$validateAgainstExactPlastic(
        LevelReader reader,
        AABB previousBox,
        double x,
        double y,
        double z,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!(reader instanceof Level level)) return;
        AABB targetBox = this.player.getBoundingBox().move(
            x - this.player.getX(),
            y - this.player.getY(),
            z - this.player.getZ()
        ).deflate(1.0E-5F);
        AABB deflatedPreviousBox = previousBox.deflate(1.0E-5F);
        Vec3 movement = targetBox.getCenter().subtract(deflatedPreviousBox.getCenter());
        if (!PlasticConvexCollisionResolver.hasExactEntityObstacle(
            this.player,
            movement,
            targetBox,
            level
        )) {
            return;
        }
        cir.setReturnValue(PlasticConvexCollisionResolver.isEntityCollidingWithAnythingNew(
            this.player,
            deflatedPreviousBox,
            targetBox,
            level
        ));
    }
}
