package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.client.gui.screen.PlasticHammerScreen;
import dev.anvilcraft.plasticraft.client.renderer.entity.PlasticEntityRenderHelper;
import dev.anvilcraft.plasticraft.client.renderer.entity.UniversalPlasticEntityRenderer;
import dev.anvilcraft.plasticraft.entity.ClearPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** 在世界半透明内容提交后绘制透明塑料方块和实体，确保共享面使用同一套剔除与明暗规则。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class ClearPlasticEntityRenderer {
    private static final Set<BondedEntityBlockEntity> DEFERRED_BLOCK_ENTITIES = Collections.newSetFromMap(
        new IdentityHashMap<>()
    );
    private static final Map<BondedEntityBlockEntity, ClearPlasticEntity> STATIC_BLOCK_PROXIES = new WeakHashMap<>();

    private ClearPlasticEntityRenderer() {
    }

    public static boolean enqueue(BondedEntityBlockEntity blockEntity) {
        if (!blockEntity.getBlockState().is(PlasticraftBlocks.CLEAR_PLASTIC.get())) return false;
        if (blockEntity.isInitialized() && blockEntity.isHammerDeflected()) return false;
        if (blockEntity.isInitialized()
            && (!(blockEntity.getOrCreateRenderEntity() instanceof UniversalPlasticEntity plastic)
                || !PlasticEntityRenderHelper.isTransparent(plastic))) {
            return false;
        }
        DEFERRED_BLOCK_ENTITIES.add(blockEntity);
        return true;
    }

    @SubscribeEvent
    public static void renderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            DEFERRED_BLOCK_ENTITIES.clear();
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(minecraft.isPaused());
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        ClearPlasticRenderTypes.beginDeferredPass(camera, partialTick);
        dispatcher.setRenderShadow(false);
        try {
            List<DeferredPlastic> plastics = new ArrayList<>();
            for (Entity entity : level.entitiesForRendering()) {
                if (!(entity instanceof UniversalPlasticEntity plastic)
                    || !plastic.isAlive()
                    || !PlasticEntityRenderHelper.isTransparent(plastic)
                    || PlasticHammerScreen.getPreview(plastic) != null) {
                    continue;
                }
                plastics.add(new DeferredPlastic(plastic, partialTick));
            }
            for (BondedEntityBlockEntity blockEntity : DEFERRED_BLOCK_ENTITIES) {
                UniversalPlasticEntity plastic = queuedPlastic(blockEntity, level);
                if (plastic == null || !plastic.isAlive()) continue;
                plastics.add(new DeferredPlastic(plastic, 1.0F));
            }
            plastics.sort(Comparator.<DeferredPlastic>comparingDouble(
                entry -> distanceToCamera(entry, camera)
            ).reversed());
            for (DeferredPlastic plastic : plastics) {
                if (!renderPlastic(event, pose, buffers, dispatcher, plastic.entity, plastic.partialTick)) continue;
                buffers.endBatch();
            }
        } finally {
            dispatcher.setRenderShadow(true);
            ClearPlasticRenderTypes.endDeferredPass();
            pose.popPose();
            DEFERRED_BLOCK_ENTITIES.clear();
        }
    }

    private static double distanceToCamera(DeferredPlastic plastic, Vec3 camera) {
        UniversalPlasticEntity entity = plastic.entity;
        Vec3 position = entity.getPosition(plastic.partialTick);
        AABB bounds = entity.getBoundingBox().move(position.subtract(entity.position()));
        return bounds.getCenter().distanceToSqr(camera);
    }

    private static boolean renderPlastic(
        RenderLevelStageEvent event,
        PoseStack pose,
        MultiBufferSource buffers,
        EntityRenderDispatcher dispatcher,
        UniversalPlasticEntity plastic,
        float partialTick
    ) {
        Vec3 position = plastic.getPosition(partialTick);
        AABB bounds = plastic.getBoundingBox().move(position.subtract(plastic.position()));
        if (!event.getFrustum().isVisible(bounds)) return false;

        pose.pushPose();
        pose.translate(position.x, position.y, position.z);
        try {
            dispatcher.render(
                plastic,
                0.0D,
                0.0D,
                0.0D,
                plastic.getYRot(),
                partialTick,
                pose,
                buffers,
                UniversalPlasticEntityRenderer.packedLight(plastic)
            );
            return true;
        } finally {
            pose.popPose();
        }
    }

    private static UniversalPlasticEntity queuedPlastic(BondedEntityBlockEntity blockEntity, ClientLevel level) {
        if (blockEntity.isRemoved()
            || blockEntity.getLevel() != level
            || !blockEntity.getBlockState().is(PlasticraftBlocks.CLEAR_PLASTIC.get())) {
            return null;
        }
        if (blockEntity.isInitialized()) {
            return blockEntity.getOrCreateRenderEntity() instanceof UniversalPlasticEntity plastic
                && PlasticEntityRenderHelper.isTransparent(plastic)
                ? plastic
                : null;
        }
        ClearPlasticEntity proxy = STATIC_BLOCK_PROXIES.get(blockEntity);
        if (proxy == null || proxy.level() != level) {
            proxy = new ClearPlasticEntity(PlasticraftEntities.CLEAR_PLASTIC.get(), level);
            STATIC_BLOCK_PROXIES.put(blockEntity, proxy);
        }
        PlasticEntityOrientation orientation = PlasticEntityOrientation.DEFAULT;
        proxy.setDisplayState(blockEntity.getBlockState());
        proxy.setOrientation(orientation);
        proxy.setPos(proxy.plasticraft$placementPosition(blockEntity.getBlockPos(), orientation));
        proxy.setNoGravity(true);
        proxy.setDeltaMovement(Vec3.ZERO);
        return proxy;
    }

    private record DeferredPlastic(UniversalPlasticEntity entity, float partialTick) {
    }
}
