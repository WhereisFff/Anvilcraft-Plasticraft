package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.client.gui.screen.PlasticHammerScreen;
import dev.anvilcraft.plasticraft.client.renderer.entity.PlasticEntityRenderHelper;
import dev.anvilcraft.plasticraft.client.renderer.entity.UniversalPlasticEntityRenderer;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
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
    private static final Map<BondedEntityBlockEntity, UniversalPlasticEntity> STATIC_BLOCK_PROXIES = new WeakHashMap<>();

    private ClearPlasticEntityRenderer() {
    }

    public static boolean enqueue(BondedEntityBlockEntity blockEntity) {
        if (!PlasticEntityRenderHelper.isTransparent(blockEntity.getBlockState())) return false;
        if (blockEntity.isInitialized() && blockEntity.isHammerDeflected()) return false;
        if (blockEntity.isInitialized()
            && (!(blockEntity.getOrCreateRenderEntity() instanceof UniversalPlasticEntity plastic)
                || !PlasticEntityRenderHelper.isTransparent(plastic))) {
            return false;
        }
        DEFERRED_BLOCK_ENTITIES.add(blockEntity);
        return true;
    }

    private static final ContextKey<PlasticRenderGeometry> CONTENTS = new ContextKey<>(AnvilcraftPlasticraft.of("clear_contents"));
    private static final ContextKey<List<PlasticRenderGeometry>> SHELLS = new ContextKey<>(AnvilcraftPlasticraft.of("clear_shells"));

    @SubscribeEvent
    public static void extract(ExtractLevelRenderStateEvent event) {
        ClientLevel level = event.getLevel();
        Vec3 camera = event.getCamera().position();
        float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(Minecraft.getInstance().isPaused());
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        List<DeferredPlastic> plastics = collectPlastics(level, partialTick, camera);
        DEFERRED_BLOCK_ENTITIES.clear();
        if (plastics.isEmpty()) {
            return;
        }
        plastics.sort(Comparator.comparingDouble(DeferredPlastic::cameraDistance).reversed());
        PlasticRenderGeometry contents = new PlasticRenderGeometry();
        List<PlasticRenderGeometry> shells = new ArrayList<>();
        for (DeferredPlastic entry : plastics) {
            UniversalPlasticEntity plastic = entry.entity;
            Vec3 position = plastic.getPosition(entry.partialTick);
            if (!event.getFrustum().isVisible(plastic.getBoundingBox().move(position.subtract(plastic.position())))) {
                continue;
            }
            if (!(dispatcher.getRenderer(plastic) instanceof UniversalPlasticEntityRenderer renderer)) {
                continue;
            }
            PoseStack pose = new PoseStack();
            pose.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
            int light = UniversalPlasticEntityRenderer.packedLight(plastic);
            renderer.renderOpaqueFluidContents(plastic, entry.partialTick, pose, contents, light);
            PlasticRenderGeometry shell = new PlasticRenderGeometry();
            ClearPlasticRenderTypes.beginDeferredPass(camera, entry.partialTick);
            try {
                renderer.extractGeometry(plastic, entry.partialTick, pose, shell, light);
            } finally {
                ClearPlasticRenderTypes.endDeferredPass();
            }
            shells.add(shell);
        }
        event.getRenderState().setRenderData(CONTENTS, contents);
        event.getRenderState().setRenderData(SHELLS, shells);
    }

    @SubscribeEvent
    public static void renderContents(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        var state = event.getLevelRenderState();
        var contents = state.getRenderData(CONTENTS);
        if (contents != null) {
            var minecraft = Minecraft.getInstance();
            var buffers = minecraft.renderBuffers().bufferSource();
            contents.draw(event.getPoseStack(), buffers, state.cameraRenderState);
            buffers.endBatch();
            var itemTarget = minecraft.levelRenderer.getItemEntityTarget();
            if (itemTarget != null) {
                itemTarget.copyDepthFrom(minecraft.getMainRenderTarget());
            }
        }
    }

    @SubscribeEvent
    public static void renderShells(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        var state = event.getLevelRenderState();
        var shells = state.getRenderData(SHELLS);
        if (shells != null) {
            var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            for (var shell : shells) {
                shell.draw(event.getPoseStack(), buffers, state.cameraRenderState);
            }
            buffers.endBatch();
        }
    }

    private static List<DeferredPlastic> collectPlastics(ClientLevel level, float partialTick, Vec3 camera) {
        List<DeferredPlastic> plastics = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof UniversalPlasticEntity plastic)
                || !plastic.isAlive()
                || !PlasticEntityRenderHelper.isTransparent(plastic)
                || PlasticHammerScreen.getPreview(plastic) != null) {
                continue;
            }
            plastics.add(new DeferredPlastic(plastic, partialTick, cameraDistance(plastic, partialTick, camera)));
        }
        for (BondedEntityBlockEntity blockEntity : DEFERRED_BLOCK_ENTITIES) {
            UniversalPlasticEntity plastic = queuedPlastic(blockEntity, level);
            if (plastic == null || !plastic.isAlive()) continue;
            plastics.add(new DeferredPlastic(plastic, 1.0F, cameraDistance(plastic, 1.0F, camera)));
        }
        return plastics;
    }

    private static double cameraDistance(UniversalPlasticEntity entity, float partialTick, Vec3 camera) {
        Vec3 position = entity.getPosition(partialTick);
        AABB bounds = entity.getBoundingBox().move(position.subtract(entity.position()));
        return bounds.getCenter().distanceToSqr(camera);
    }

    private static UniversalPlasticEntity queuedPlastic(BondedEntityBlockEntity blockEntity, ClientLevel level) {
        if (blockEntity.isRemoved()
            || blockEntity.getLevel() != level
            || !PlasticEntityRenderHelper.isTransparent(blockEntity.getBlockState())) {
            return null;
        }
        if (blockEntity.isInitialized()) {
            return blockEntity.getOrCreateRenderEntity() instanceof UniversalPlasticEntity plastic
                && PlasticEntityRenderHelper.isTransparent(plastic)
                ? plastic
                : null;
        }
        PlasticMaterial material = PlasticMaterial.fromProductBlock(blockEntity.getBlockState()).orElseThrow();
        UniversalPlasticEntity proxy = STATIC_BLOCK_PROXIES.get(blockEntity);
        if (proxy == null || proxy.level() != level || proxy.getType() != material.entityType()) {
            proxy = material.entityType().create(level, EntitySpawnReason.LOAD);
            if (proxy == null) return null;
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

    private record DeferredPlastic(UniversalPlasticEntity entity, float partialTick, double cameraDistance) {
    }
}
