package dev.anvilcraft.plasticraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.block.DroneStationBlock;
import dev.anvilcraft.plasticraft.block.entity.DroneStationBlockEntity;
import dev.anvilcraft.plasticraft.client.renderer.entity.drone.DroneRenderDispatcher;
import dev.anvilcraft.plasticraft.drone.DroneData;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * 无人机站方块实体渲染器:入库中的无人机以无碰撞停泊显示对象在顶部舱门下沉。
 * 无电模型不播放动态;断电时同步进度冻结,画面停在当前位置。
 */
public class DroneStationRenderer implements BlockEntityRenderer<DroneStationBlockEntity> {
    /** 下沉动画的起始与结束高度(相对方块底部);终点位于站体内,视觉上没入舱门。 */
    private static final float DOCK_START_Y = 1.55F;
    private static final float DOCK_END_Y = 0.55F;
    private static final float PROPELLER_SPIN_SPEED = 42.0F;
    private final DroneRenderDispatcher dispatcher;

    public DroneStationRenderer(BlockEntityRendererProvider.Context context) {
        this.dispatcher = new DroneRenderDispatcher(context::bakeLayer);
    }

    @Override
    public void render(
        DroneStationBlockEntity station,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        DroneData docking = station.dockingData();
        if (docking == null) return;
        if (!station.getBlockState().hasProperty(DroneStationBlock.POWERED)
            || !station.getBlockState().getValue(DroneStationBlock.POWERED)) {
            return;
        }
        float progress = station.clientDockingProgress(partialTick)
            / DroneStationBlockEntity.DOCKING_DURATION_TICKS;
        float y = Mth.lerp(Mth.clamp(progress, 0.0F, 1.0F), DOCK_START_Y, DOCK_END_Y);
        float spin = station.isDockingRunning()
            ? (station.getLevel() == null
                ? 0.0F
                : (station.getLevel().getGameTime() % 360L + partialTick) * PROPELLER_SPIN_SPEED)
            : 0.0F;
        poseStack.pushPose();
        poseStack.translate(0.5D, y, 0.5D);
        this.dispatcher.render(
            docking.toolId(),
            docking.leftPropeller(),
            docking.rightPropeller(),
            docking.hostedCarry(),
            spin,
            poseStack,
            buffers,
            packedLight,
            packedOverlay
        );
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(DroneStationBlockEntity station) {
        return new AABB(station.getBlockPos()).expandTowards(0.0D, 1.8D, 0.0D).inflate(0.6D, 0.0D, 0.6D);
    }
}
