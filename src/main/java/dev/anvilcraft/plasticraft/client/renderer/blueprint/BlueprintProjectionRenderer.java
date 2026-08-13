package dev.anvilcraft.plasticraft.client.renderer.blueprint;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.BlueprintPlacement;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.StructureSnapshot;
import dev.anvilcraft.plasticraft.client.blueprint.BlueprintDeploySession;
import dev.anvilcraft.plasticraft.client.blueprint.ClientBlueprintJobCache;
import dev.anvilcraft.plasticraft.client.blueprint.ClientBlueprintSnapshotCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 施工投影渲染器:为已放置蓝图与部署会话渲染半透明目标方块投影。
 * 网格按(哈希、旋转、镜像、分层)缓存为顶点缓冲,只在放置参数变化时重建;
 * 每帧仅按锚点与相机平移绘制。当前阶段的明确简化:纯方块实体渲染的方块
 * 以其粒子贴图画半透明占位盒,实体条目只保留在数据中不渲染投影。
 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class BlueprintProjectionRenderer {
    /** 投影可见距离(格),超出的已放置蓝图不渲染。 */
    private static final double VIEW_DISTANCE = 160.0D;
    /** 网格缓存上限,超出按最久未用淘汰。 */
    private static final int MAX_CACHED_MESHES = 4;
    /** 投影统一着色:偏蓝的半透明全息色。 */
    private static final float TINT_RED = 0.62F;
    private static final float TINT_GREEN = 0.82F;
    private static final float TINT_BLUE = 1.0F;
    private static final float TINT_ALPHA = 0.55F;

    private record MeshKey(String hash, Rotation rotation, Mirror mirror, int layerView) {
    }

    private static final Map<MeshKey, VertexBuffer> MESHES = new LinkedHashMap<>();

    private BlueprintProjectionRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;

        List<RenderItem> items = collectRenderItems(minecraft);
        if (items.isEmpty()) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        for (RenderItem item : items) {
            StructureSnapshot snapshot = ClientBlueprintSnapshotCache.snapshotOrRequest(item.hash());
            if (snapshot == null) continue;
            VertexBuffer mesh = meshFor(item, snapshot);
            if (mesh == null) continue;
            poseStack.pushPose();
            poseStack.translate(
                item.placement().anchor().getX() - camera.x,
                item.placement().anchor().getY() - camera.y,
                item.placement().anchor().getZ() - camera.z
            );
            drawMesh(mesh, poseStack.last().pose(), event.getProjectionMatrix());
            poseStack.popPose();
        }
    }

    private record RenderItem(String hash, BlueprintPlacement placement, int layerView) {
    }

    private static List<RenderItem> collectRenderItems(Minecraft minecraft) {
        List<RenderItem> items = new ArrayList<>();
        String sessionHash = BlueprintDeploySession.activeHash();
        BlueprintPlacement sessionPlacement = BlueprintDeploySession.activePlacement();
        UUID sessionJobId = BlueprintDeploySession.activeJobId();
        if (sessionHash != null && sessionPlacement != null) {
            items.add(new RenderItem(sessionHash, sessionPlacement, BlueprintDeploySession.layerView()));
        }
        assert minecraft.level != null && minecraft.player != null;
        for (ConstructionJob job : ClientBlueprintJobCache.jobs()) {
            if (!job.dimension().equals(minecraft.level.dimension())) continue;
            if (job.jobId().equals(sessionJobId)) continue;
            if (!job.anchor().closerToCenterThan(minecraft.player.position(), VIEW_DISTANCE)) continue;
            items.add(new RenderItem(
                job.hash(),
                BlueprintPlacement.of(job),
                BlueprintDeploySession.LAYERS_ALL
            ));
        }
        return items;
    }

    @Nullable
    private static VertexBuffer meshFor(RenderItem item, StructureSnapshot snapshot) {
        MeshKey key = new MeshKey(
            item.hash(),
            item.placement().rotation(),
            item.placement().mirror(),
            item.layerView()
        );
        VertexBuffer cached = MESHES.remove(key);
        if (cached != null) {
            MESHES.put(key, cached);
            return cached;
        }
        VertexBuffer built = buildMesh(item, snapshot);
        if (built == null) return null;
        MESHES.put(key, built);
        while (MESHES.size() > MAX_CACHED_MESHES) {
            MeshKey oldest = MESHES.keySet().iterator().next();
            VertexBuffer evicted = MESHES.remove(oldest);
            if (evicted != null) evicted.close();
        }
        return built;
    }

    /** 以锚点为原点烘焙整份投影网格;方块坐标与状态都按放置参数变换。 */
    @Nullable
    private static VertexBuffer buildMesh(RenderItem item, StructureSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        BlueprintPlacement local = new BlueprintPlacement(
            BlockPos.ZERO,
            item.placement().rotation(),
            item.placement().mirror()
        );
        ByteBufferBuilder byteBuffer = new ByteBufferBuilder(4 * 1024 * 1024);
        try {
            BufferBuilder builder = new BufferBuilder(
                byteBuffer,
                VertexFormat.Mode.QUADS,
                RenderType.translucent().format()
            );
            VertexConsumer tinted = new TintedVertexConsumer(builder, TINT_RED, TINT_GREEN, TINT_BLUE, TINT_ALPHA);
            MultiBufferSource singleBuffer = ignored -> tinted;
            PoseStack poseStack = new PoseStack();
            for (StructureSnapshot.BlockEntry entry : snapshot.blocks()) {
                if (item.layerView() != BlueprintDeploySession.LAYERS_ALL
                    && entry.pos().getY() != item.layerView()) {
                    continue;
                }
                BlockState state = snapshot.stateOf(entry);
                if (state.isAir()) continue;
                BlockState transformed = local.stateOf(state);
                BlockPos renderPos = local.worldOf(entry.pos());
                poseStack.pushPose();
                poseStack.translate(renderPos.getX(), renderPos.getY(), renderPos.getZ());
                if (transformed.getRenderShape() == RenderShape.MODEL) {
                    minecraft.getBlockRenderer().renderSingleBlock(
                        transformed,
                        poseStack,
                        singleBuffer,
                        LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY,
                        ModelData.EMPTY,
                        null
                    );
                } else {
                    renderPlaceholderBox(minecraft, transformed, poseStack, tinted);
                }
                poseStack.popPose();
            }
            MeshData meshData = builder.build();
            if (meshData == null) return null;
            VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vertexBuffer.bind();
            vertexBuffer.upload(meshData);
            VertexBuffer.unbind();
            return vertexBuffer;
        } finally {
            byteBuffer.close();
        }
    }

    /** 纯方块实体渲染的方块(箱子、告示牌等)以粒子贴图画一个内缩占位盒。 */
    private static void renderPlaceholderBox(
        Minecraft minecraft,
        BlockState state,
        PoseStack poseStack,
        VertexConsumer consumer
    ) {
        TextureAtlasSprite sprite = minecraft.getBlockRenderer().getBlockModel(state).getParticleIcon(ModelData.EMPTY);
        PoseStack.Pose pose = poseStack.last();
        float min = 0.05F;
        float max = 0.95F;
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        // 六个面按面法线逐面写入;占位盒只求可辨识,不做遮挡剔除。
        quad(consumer, pose, u0, v0, u1, v1, new float[][]{
            {min, max, min}, {max, max, min}, {max, max, max}, {min, max, max}
        }, 0.0F, 1.0F, 0.0F);
        quad(consumer, pose, u0, v0, u1, v1, new float[][]{
            {min, min, max}, {max, min, max}, {max, min, min}, {min, min, min}
        }, 0.0F, -1.0F, 0.0F);
        quad(consumer, pose, u0, v0, u1, v1, new float[][]{
            {min, min, min}, {min, max, min}, {max, max, min}, {max, min, min}
        }, 0.0F, 0.0F, -1.0F);
        quad(consumer, pose, u0, v0, u1, v1, new float[][]{
            {max, min, max}, {max, max, max}, {min, max, max}, {min, min, max}
        }, 0.0F, 0.0F, 1.0F);
        quad(consumer, pose, u0, v0, u1, v1, new float[][]{
            {min, min, max}, {min, max, max}, {min, max, min}, {min, min, min}
        }, -1.0F, 0.0F, 0.0F);
        quad(consumer, pose, u0, v0, u1, v1, new float[][]{
            {max, min, min}, {max, max, min}, {max, max, max}, {max, min, max}
        }, 1.0F, 0.0F, 0.0F);
    }

    private static void quad(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        float u0,
        float v0,
        float u1,
        float v1,
        float[][] corners,
        float normalX,
        float normalY,
        float normalZ
    ) {
        float[] us = {u0, u0, u1, u1};
        float[] vs = {v1, v0, v0, v1};
        for (int corner = 0; corner < 4; corner++) {
            consumer.addVertex(pose, corners[corner][0], corners[corner][1], corners[corner][2])
                .setColor(255, 255, 255, 255)
                .setUv(us[corner], vs[corner])
                .setUv2(LightTexture.FULL_BRIGHT & 0xFFFF, LightTexture.FULL_BRIGHT >> 16)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(pose, normalX, normalY, normalZ);
        }
    }

    private static void drawMesh(VertexBuffer mesh, Matrix4f modelView, Matrix4f projection) {
        RenderType renderType = RenderType.translucent();
        renderType.setupRenderState();
        mesh.bind();
        mesh.drawWithShader(modelView, projection, RenderSystem.getShader());
        VertexBuffer.unbind();
        renderType.clearRenderState();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearMeshes();
    }

    private static void clearMeshes() {
        for (VertexBuffer mesh : MESHES.values()) {
            mesh.close();
        }
        MESHES.clear();
    }

    /** 半透明统一着色的顶点包装:把模型颜色乘上投影色与透明度。 */
    private record TintedVertexConsumer(
        VertexConsumer delegate,
        float red,
        float green,
        float blue,
        float alpha
    ) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.delegate.setColor(
                (int) (red * this.red),
                (int) (green * this.green),
                (int) (blue * this.blue),
                (int) (alpha * this.alpha)
            );
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.delegate.setNormal(x, y, z);
            return this;
        }
    }
}
