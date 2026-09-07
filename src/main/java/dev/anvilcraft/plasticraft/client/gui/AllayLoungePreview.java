package dev.anvilcraft.plasticraft.client.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.anvilcraft.plasticraft.allay.AllayWorkRecord;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

public final class AllayLoungePreview {
    private final PreviewEntity entity;
    private final AllayPreviewRotation rotation = new AllayPreviewRotation();
    private final long animationStart;
    private final int animationOffset;
    @Nullable
    private AllayWorkRecord record;
    private float height;
    private float extent;

    public AllayLoungePreview(ClientLevel level, AllayWorkRecord record, long now) {
        this.entity = new PreviewEntity(level);
        this.animationStart = now;
        this.animationOffset = Math.floorMod(record.entityId().hashCode(), 200);
        this.updateRecord(record);
    }

    public AllayPreviewRotation rotation() {
        return this.rotation;
    }

    public void updateRecord(AllayWorkRecord record) {
        if (this.record == record) return;
        this.record = record;
        this.entity.applyWorkRecord(record);
        // 仅创建界面内的显示实体，不加入世界，也不调用实体 tick，避免取料、拾取和音效等副作用。
        this.entity.setCustomName(null);
        this.entity.setNoAi(true);
        this.height = 0.7F;
        this.extent = 0.9F;
        MoldedPlasticData hat = MoldedPlasticData.get(record.hardHat()).orElse(null);
        if (hat != null) {
            AABB bounds = hat.surfaceBounds();
            this.height = Math.max(this.height, 0.6F + (float) bounds.getYsize());
            this.extent = Math.max(this.extent, (float) Math.hypot(bounds.getXsize(), bounds.getZsize()));
        }
        this.extent = Math.max(this.extent, this.height);
    }

    public void render(GuiGraphics graphics, int x, int y, int size, long now) {
        this.rotation.update(now);
        double age = (now - this.animationStart) / 50_000_000.0D + this.animationOffset;
        this.entity.tickCount = (int) age;
        float partialTick = (float) (age - this.entity.tickCount);
        float yaw = 180.0F + this.rotation.angle();
        this.entity.setYRot(yaw);
        this.entity.yRotO = yaw;
        this.entity.yBodyRot = yaw;
        this.entity.yBodyRotO = yaw;
        this.entity.yHeadRot = yaw;
        this.entity.yHeadRotO = yaw;
        float scale = (size - 2.0F) / this.extent;
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Quaternionf cameraOrientation = new Quaternionf(dispatcher.cameraOrientation());
        boolean hitBoxes = dispatcher.shouldRenderHitBoxes();
        graphics.enableScissor(x, y, x + size, y + size);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x + size / 2.0F, y + size / 2.0F + this.height * scale / 2.0F, 50.0F);
            graphics.pose().scale(scale, scale, -scale);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(180.0F));
            Lighting.setupForEntityInInventory();
            dispatcher.overrideCameraOrientation(Axis.YP.rotationDegrees(180.0F));
            dispatcher.setRenderShadow(false);
            dispatcher.setRenderHitBoxes(false);
            RenderSystem.runAsFancy(() -> dispatcher.render(
                this.entity, 0.0D, 0.0D, 0.0D, 0.0F, partialTick,
                graphics.pose(), graphics.bufferSource(), LightTexture.FULL_BRIGHT
            ));
            graphics.flush();
        } finally {
            dispatcher.setRenderShadow(true);
            dispatcher.setRenderHitBoxes(hitBoxes);
            dispatcher.overrideCameraOrientation(cameraOrientation);
            graphics.pose().popPose();
            graphics.disableScissor();
            Lighting.setupFor3DItems();
        }
    }

    private static final class PreviewEntity extends WorkingAllayEntity {
        private PreviewEntity(ClientLevel level) {
            super(PlasticraftEntities.WORKING_ALLAY.get(), level);
        }

        @Override
        public float getHoldingItemAnimationProgress(float partialTick) {
            return this.hasItemInHand() ? 1.0F : 0.0F;
        }
    }
}
