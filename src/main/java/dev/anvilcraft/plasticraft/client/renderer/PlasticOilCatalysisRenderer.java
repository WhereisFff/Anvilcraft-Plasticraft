package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.PlasticOilCauldronBlock;
import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltCauldronBlock;
import dev.anvilcraft.plasticraft.block.entity.UniversalPlasticMeltBlockEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.anvilcraft.plasticraft.network.PlasticOilCatalysisSyncPacket;
import dev.dubhe.anvilcraft.client.support.FluidRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 缓存催化进度，并在塑料油上叠加逐渐变得不透明的熔体纹理。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class PlasticOilCatalysisRenderer {
    private static final int COMPLETE_HOLD_TICKS = 20;
    private static final float MAX_PROGRESS_PER_SYNC = 0.025F;
    private static final float MAX_PREDICTION_TICKS = 22.0F;
    private static final float BOX_OFFSET = 0.0005F;
    private static final float CAULDRON_MIN_XZ = 2.0F / 16.0F;
    private static final float CAULDRON_MAX_XZ = 14.0F / 16.0F;
    private static final Map<Long, VisualState> STATES = new HashMap<>();
    private static ClientLevel syncedLevel;

    private PlasticOilCatalysisRenderer() {
    }

    public static void handleSync(
        Level level,
        BlockPos pos,
        float progress,
        float progressPerTick,
        long serverGameTime,
        int mode
    ) {
        if (!(level instanceof ClientLevel clientLevel)) return;
        ensureLevel(clientLevel);
        long key = pos.asLong();
        switch (mode) {
            case PlasticOilCatalysisSyncPacket.UPDATE -> STATES.put(
                key,
                new VisualState(
                    Mth.clamp(progress, 0.0F, 1.0F),
                    Math.max(0.0F, progressPerTick),
                    serverGameTime,
                    Long.MAX_VALUE
                )
            );
            case PlasticOilCatalysisSyncPacket.COMPLETE -> STATES.put(
                key,
                new VisualState(1.0F, 0.0F, serverGameTime, clientLevel.getGameTime() + COMPLETE_HOLD_TICKS)
            );
            case PlasticOilCatalysisSyncPacket.CLEAR -> STATES.remove(key);
            default -> {
            }
        }
    }

    public static float progress(Level level, BlockPos pos, float partialTick) {
        if (level != syncedLevel) return 0.0F;
        VisualState state = STATES.get(pos.asLong());
        if (state == null || state.expired(level.getGameTime())) return 0.0F;
        return state.progress(level.getGameTime(), partialTick);
    }

    public static void renderContainerOverlay(
        Level level,
        BlockPos pos,
        float partialTick,
        FluidStack fluid,
        float minX,
        float minY,
        float minZ,
        float maxX,
        float maxY,
        float maxZ,
        MultiBufferSource buffers,
        PoseStack pose,
        int packedLight,
        boolean renderBottom
    ) {
        if (!isReactiveSource(fluid)) return;
        float progress = progress(level, pos, partialTick);
        if (progress <= 0.0F) return;
        renderMeltBox(
            progress,
            fluid,
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            buffers,
            pose,
            packedLight,
            renderBottom
        );
    }

    @SubscribeEvent
    public static void renderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || level != syncedLevel || STATES.isEmpty()) return;

        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(minecraft.isPaused());
        boolean rendered = false;
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (long key : List.copyOf(STATES.keySet())) {
            BlockPos pos = BlockPos.of(key);
            if (!level.hasChunkAt(pos) || !event.getFrustum().isVisible(new AABB(pos))) continue;
            float progress = progress(level, pos, partialTick);
            if (progress <= 0.0F) continue;

            BlockState state = level.getBlockState(pos);
            boolean plasticOilCauldron = state.is(PlasticraftBlocks.PLASTIC_OIL_CAULDRON.get());
            boolean universalMeltCauldron = state.is(PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT_CAULDRON.get());
            boolean cauldron = plasticOilCauldron || universalMeltCauldron;
            boolean plasticOilSource = level.getFluidState(pos).isSourceOfType(PlasticraftFluids.PLASTIC_OIL.get());
            boolean universalMeltSource = level.getFluidState(pos)
                .isSourceOfType(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get());
            boolean source = plasticOilSource || universalMeltSource;
            if (!cauldron && !source) continue;
            FluidStack sourceFluid = new FluidStack(
                plasticOilCauldron || plasticOilSource
                    ? PlasticraftFluids.PLASTIC_OIL.get()
                    : PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(),
                1_000
            );
            if (universalMeltCauldron) {
                PlasticMeltColor.set(sourceFluid, state.getValue(UniversalPlasticMeltCauldronBlock.COLOR));
            } else if (universalMeltSource
                && level.getBlockEntity(pos) instanceof UniversalPlasticMeltBlockEntity melt) {
                PlasticMeltColor.set(sourceFluid, melt.getColor());
            }

            pose.pushPose();
            pose.translate(pos.getX(), pos.getY(), pos.getZ());
            int packedLight = LevelRenderer.getLightColor(level, pos);
            if (cauldron) {
                float surface = cauldronSurface(state.getValue(PlasticOilCauldronBlock.LEVEL));
                renderMeltBox(
                    progress,
                    sourceFluid,
                    CAULDRON_MIN_XZ,
                    surface - 0.001F,
                    CAULDRON_MIN_XZ,
                    CAULDRON_MAX_XZ,
                    surface,
                    CAULDRON_MAX_XZ,
                    buffers,
                    pose,
                    packedLight,
                    false
                );
            } else {
                float surface = Math.min(0.999F, level.getFluidState(pos).getHeight(level, pos));
                renderMeltBox(
                    progress,
                    sourceFluid,
                    0.001F,
                    0.001F,
                    0.001F,
                    0.999F,
                    surface,
                    0.999F,
                    buffers,
                    pose,
                    packedLight,
                    true
                );
            }
            pose.popPose();
            rendered = true;
        }
        pose.popPose();
        if (rendered) buffers.endBatch(RenderType.translucent());
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != syncedLevel) {
            STATES.clear();
            syncedLevel = level;
        }
        if (level == null || STATES.isEmpty()) return;
        long gameTime = level.getGameTime();
        STATES.entrySet().removeIf(entry -> {
            BlockPos pos = BlockPos.of(entry.getKey());
            return entry.getValue().expired(gameTime) || !level.hasChunkAt(pos);
        });
    }

    private static void renderMeltBox(
        float progress,
        FluidStack source,
        float minX,
        float minY,
        float minZ,
        float maxX,
        float maxY,
        float maxZ,
        MultiBufferSource buffers,
        PoseStack pose,
        int packedLight,
        boolean renderBottom
    ) {
        float opacity = smoothStep(Mth.clamp(progress, 0.0F, 1.0F));
        PlasticMaterial sourceMaterial = PlasticMaterial.fromMelt(source).orElse(PlasticMaterial.UNIVERSAL);
        PlasticMaterial outputMaterial = source.is(PlasticraftFluids.PLASTIC_OIL.get())
            ? PlasticMaterial.UNIVERSAL
            : sourceMaterial == PlasticMaterial.UNIVERSAL
                ? PlasticMaterial.ENGINEERING
                : sourceMaterial;
        FluidStack overlay = new FluidStack(outputMaterial.melt(), Math.max(1, source.getAmount()));
        PlasticMeltColor.set(overlay, PlasticMeltColor.get(source));
        UniversalPlasticMeltFluidExtension.setCatalysisOpacity(overlay, opacity);
        FluidRenderHelper.INSTANCE.renderFluidBox(
            overlay,
            minX - BOX_OFFSET,
            minY - BOX_OFFSET,
            minZ - BOX_OFFSET,
            maxX + BOX_OFFSET,
            maxY + BOX_OFFSET,
            maxZ + BOX_OFFSET,
            buffers.getBuffer(RenderType.translucent()),
            pose,
            packedLight,
            renderBottom,
            false
        );
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static boolean isReactiveSource(FluidStack fluid) {
        return fluid.is(PlasticraftFluids.PLASTIC_OIL.get())
            || fluid.is(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get());
    }

    private static float cauldronSurface(int level) {
        return switch (level) {
            case 1 -> 6.0F / 16.0F;
            case 2 -> 9.0F / 16.0F;
            case 3 -> 12.0F / 16.0F;
            default -> 15.0F / 16.0F;
        };
    }

    private static void ensureLevel(ClientLevel level) {
        if (syncedLevel == level) return;
        STATES.clear();
        syncedLevel = level;
    }

    private record VisualState(float progress, float rate, long serverGameTime, long expiresAt) {
        private float progress(long gameTime, float partialTick) {
            float predictionTicks = this.rate <= 0.0F
                ? 0.0F
                : Mth.clamp(Mth.ceil(MAX_PROGRESS_PER_SYNC / this.rate) + 2.0F, 2.0F, MAX_PREDICTION_TICKS);
            float elapsed = Mth.clamp((float) (gameTime - this.serverGameTime) + partialTick, 0.0F, predictionTicks);
            return Mth.clamp(this.progress + this.rate * elapsed, 0.0F, 1.0F);
        }

        private boolean expired(long gameTime) {
            return gameTime > this.expiresAt;
        }
    }
}
