package dev.anvilcraft.plasticraft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.client.renderer.HighHeatFuelFlameRenderer;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import dev.dubhe.anvilcraft.block.entity.FishTankBlockEntity;
import dev.dubhe.anvilcraft.client.renderer.blockentity.FishTankBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将鱼缸内的高热燃料火焰替换为蓝白色强化喷流。 */
@Mixin(FishTankBlockEntityRenderer.class)
abstract class FishTankBlockEntityRendererMixin {
    private static final float TANK_WALL = 1.0F / 16.0F + 0.001F;

    @Redirect(
        method = "render(Ldev/dubhe/anvilcraft/block/entity/FishTankBlockEntity;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At(
            value = "INVOKE",
            target = "Ldev/dubhe/anvilcraft/block/entity/FishTankBlockEntity;isIgnited()Z"
        )
    )
    private boolean plasticraft$showOrdinaryFire(FishTankBlockEntity tank) {
        return tank.isIgnited() && !tank.getFluidHandler().getFluid().is(ModFluids.HIGH_HEAT_FUEL.get());
    }

    @Inject(
        method = "render(Ldev/dubhe/anvilcraft/block/entity/FishTankBlockEntity;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At("TAIL")
    )
    private void plasticraft$renderHighHeatFuelFlame(
        FishTankBlockEntity tank,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay,
        CallbackInfo ci
    ) {
        FluidStack fluid = tank.getFluidHandler().getFluid();
        if (!tank.isIgnited() || !fluid.is(ModFluids.HIGH_HEAT_FUEL.get())) return;
        float fill = Math.min((float) fluid.getAmount() / tank.getFluidHandler().getCapacity(), 1.0F);
        float surfaceY = TANK_WALL + (1.0F - 2.0F * TANK_WALL) * fill;
        long gameTime = tank.getLevel() == null ? 0L : tank.getLevel().getGameTime();
        HighHeatFuelFlameRenderer.render(
            poseStack,
            buffers,
            surfaceY,
            0.5F - TANK_WALL,
            gameTime + partialTick,
            tank.getBlockPos().asLong()
        );
    }

    public AABB getRenderBoundingBox(FishTankBlockEntity tank) {
        return new AABB(tank.getBlockPos()).expandTowards(0.0D, 2.0D, 0.0D);
    }
}
