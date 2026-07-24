package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.BondedFallingBlockInfo;
import dev.anvilcraft.plasticraft.block.BondedFallingChunkData;
import dev.anvilcraft.plasticraft.block.BlockAdhesionState;
import dev.anvilcraft.plasticraft.block.piston.PistonAdhesionController;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityAdhesion;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveFaces;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondLink;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondState;
import dev.anvilcraft.plasticraft.entity.adhesive.SlidingAdhesionData;
import dev.anvilcraft.plasticraft.init.ModAttachments;
import dev.dubhe.anvilcraft.entity.SlidingBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 在同步的粘合状态存在时渲染高粘性树脂胶面。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class AdhesivePatchRenderer {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/high_viscosity_resin_adhesive")
    );
    private static final int CHUNK_RADIUS = 8;
    private static ClientLevel cachedLevel;
    private static long cachedGameTime = Long.MIN_VALUE;
    private static ChunkPos cachedCameraChunk;
    private static List<Patch> cachedBlockPatches = List.of();

    private AdhesivePatchRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;
        PoseStack pose = event.getPoseStack();
        net.minecraft.world.phys.Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(minecraft.isPaused());
        renderContents(level, pose, buffers, camera, partialTick);
        pose.popPose();
        buffers.endBatch(RenderType.translucent());
    }

    public static void renderContents(
        ClientLevel level,
        PoseStack pose,
        MultiBufferSource.BufferSource buffers,
        net.minecraft.world.phys.Vec3 camera,
        float partialTick
    ) {
        Set<PatchKey> slidingPatchKeys = new HashSet<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!entity.isAlive()) continue;
            if (entity instanceof SlidingBlockEntity slidingBlock) {
                renderSlidingPatches(level, pose, buffers, slidingBlock, partialTick, slidingPatchKeys);
            }
            EntityAdhesion adhesion = entity.getExistingDataOrNull(ModAttachments.ENTITY_ADHESION.get());
            if (adhesion != null) {
                renderPatch(
                    level,
                    pose,
                    buffers,
                    adhesion.supportPos(),
                    adhesion.attachmentFace(),
                    PistonAdhesionController.movementOffset(level, entity, adhesion, partialTick)
                );
            }
            EntityBondState bonds = EntityBondManager.get(entity);
            if (bonds == null) continue;
            for (EntityBondLink link : bonds.links()) {
                Entity other = EntityBondManager.resolve(level, link);
                if (other == null || entity.getUUID().compareTo(other.getUUID()) >= 0) continue;
                renderEntityPatch(level, pose, buffers, entity, link.face(), partialTick);
            }
        }

        for (Patch patch : blockPatches(level, camera)) {
            if (slidingPatchKeys.contains(patch.key())) continue;
            renderPatch(
                level,
                pose,
                buffers,
                patch.supportPos(),
                patch.face(),
                patch.movementOffset(partialTick)
            );
        }
    }

    private static void renderSlidingPatches(
        ClientLevel level,
        PoseStack pose,
        MultiBufferSource buffers,
        SlidingBlockEntity entity,
        float partialTick,
        Set<PatchKey> renderedPatches
    ) {
        SlidingAdhesionData data = entity.getExistingDataOrNull(ModAttachments.SLIDING_BLOCK_ADHESION.get());
        if (data == null || data.parts().isEmpty()) return;

        BlockPos origin = entity.getStartPos();
        Vec3 interpolatedOrigin = entity.getPosition(partialTick).add(-0.5D, 0.0D, -0.5D);
        Vec3 movementOffset = interpolatedOrigin.subtract(
            origin.getX(),
            origin.getY(),
            origin.getZ()
        );
        for (SlidingAdhesionData.Part part : data.parts()) {
            BlockPos ownerPos = origin.offset(part.relativePos());
            BlockAdhesionState state = part.state();
            for (Direction face : Direction.values()) {
                boolean shouldRender = state.hasPatch(face);
                if (state.hasBlockBond(face)) {
                    shouldRender = ownerPos.asLong() <= ownerPos.relative(face).asLong();
                }
                if (!shouldRender) continue;
                PatchKey key = new PatchKey(ownerPos, face);
                if (!renderedPatches.add(key)) continue;
                renderPatch(level, pose, buffers, ownerPos, face, movementOffset);
            }
        }
    }

    private static List<Patch> blockPatches(ClientLevel level, net.minecraft.world.phys.Vec3 camera) {
        ChunkPos cameraChunk = new ChunkPos(BlockPos.containing(camera));
        long gameTime = level.getGameTime();
        if (cachedLevel == level
            && cachedGameTime == gameTime
            && cameraChunk.equals(cachedCameraChunk)) {
            return cachedBlockPatches;
        }

        Set<Patch> patches = new HashSet<>();
        for (int chunkX = cameraChunk.x - CHUNK_RADIUS; chunkX <= cameraChunk.x + CHUNK_RADIUS; chunkX++) {
            for (int chunkZ = cameraChunk.z - CHUNK_RADIUS; chunkZ <= cameraChunk.z + CHUNK_RADIUS; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk == null) continue;
                BondedFallingChunkData data = chunk.getExistingDataOrNull(
                    ModAttachments.BONDED_FALLING_BLOCKS.get()
                );
                if (data == null) continue;
                for (java.util.Map.Entry<BlockPos, BlockAdhesionState> entry : data.adhesions().entrySet()) {
                    BlockPos ownerPos = entry.getKey();
                    BlockAdhesionState state = entry.getValue();
                    for (Direction face : Direction.values()) {
                        if (state.hasPatch(face)) {
                            patches.add(new Patch(
                                ownerPos,
                                face,
                                ownerPos,
                                findMovingPiston(level, ownerPos, state.blockId())
                            ));
                        }
                        if (!state.hasBlockBond(face)) continue;
                        BlockPos otherPos = ownerPos.relative(face);
                        if (ownerPos.asLong() > otherPos.asLong()) continue;
                        patches.add(new Patch(
                            ownerPos,
                            face,
                            ownerPos,
                            findMovingPiston(level, ownerPos, state.blockId())
                        ));
                    }
                }
                for (java.util.Map.Entry<BlockPos, BondedFallingBlockInfo> entry : data.entries().entrySet()) {
                    BondedFallingBlockInfo info = entry.getValue();
                    Direction face = Direction.fromDelta(
                        entry.getKey().getX() - info.supportPos().getX(),
                        entry.getKey().getY() - info.supportPos().getY(),
                        entry.getKey().getZ() - info.supportPos().getZ()
                    );
                    BlockAdhesionState state = data.getAdhesion(entry.getKey());
                    if (face != null && (state == null || !state.hasBlockBond(face.getOpposite()))) {
                        patches.add(new Patch(
                            info.supportPos(),
                            face,
                            entry.getKey(),
                            findMovingPiston(level, entry.getKey(), info.blockState())
                        ));
                    }
                }
            }
        }
        cachedLevel = level;
        cachedGameTime = gameTime;
        cachedCameraChunk = cameraChunk;
        cachedBlockPatches = List.copyOf(patches);
        return cachedBlockPatches;
    }

    private static void renderPatch(
        ClientLevel level,
        PoseStack pose,
        MultiBufferSource.BufferSource buffers,
        BlockPos supportPos,
        Direction face
    ) {
        renderPatch(level, pose, buffers, supportPos, face, Vec3.ZERO);
    }

    private static void renderEntityPatch(
        ClientLevel level,
        PoseStack pose,
        MultiBufferSource buffers,
        Entity entity,
        Direction storedFace,
        float partialTick
    ) {
        Direction worldFace = AdhesiveFaces.worldFace(entity, storedFace);
        net.minecraft.world.phys.AABB box = entity.getBoundingBox().move(
            entity.getPosition(partialTick).subtract(entity.position())
        );
        Vec3 center = box.getCenter();
        Vec3 surfaceCenter = switch (worldFace.getAxis()) {
            case X -> new Vec3(worldFace == Direction.EAST ? box.maxX : box.minX, center.y, center.z);
            case Y -> new Vec3(center.x, worldFace == Direction.UP ? box.maxY : box.minY, center.z);
            case Z -> new Vec3(center.x, center.y, worldFace == Direction.SOUTH ? box.maxZ : box.minZ);
        };
        pose.pushPose();
        pose.translate(surfaceCenter.x, surfaceCenter.y, surfaceCenter.z);
        pose.mulPose(rotationFromUp(worldFace));
        pose.translate(-0.5D, 0.001D, -0.5D);
        renderPatchModel(
            level,
            pose,
            buffers,
            entity.blockPosition(),
            LevelRenderer.getLightColor(level, entity.blockPosition())
        );
        pose.popPose();
    }

    private static void renderPatch(
        ClientLevel level,
        PoseStack pose,
        MultiBufferSource buffers,
        BlockPos supportPos,
        Direction face,
        Vec3 movementOffset
    ) {
        BlockPos lightPos = supportPos.relative(face);
        pose.pushPose();
        pose.translate(
            supportPos.getX() + movementOffset.x,
            supportPos.getY() + movementOffset.y,
            supportPos.getZ() + movementOffset.z
        );
        renderPatchModel(
            level,
            pose,
            buffers,
            supportPos,
            face,
            LevelRenderer.getLightColor(level, lightPos)
        );
        pose.popPose();
    }

    public static void renderAttachedPatch(
        ClientLevel level,
        PoseStack pose,
        MultiBufferSource buffers,
        BlockPos renderOrigin,
        BlockPos supportPos,
        Direction face,
        int packedLight
    ) {
        pose.pushPose();
        pose.translate(
            supportPos.getX() - renderOrigin.getX(),
            supportPos.getY() - renderOrigin.getY(),
            supportPos.getZ() - renderOrigin.getZ()
        );
        renderPatchModel(level, pose, buffers, supportPos, face, packedLight);
        pose.popPose();
    }

    private static void renderPatchModel(
        ClientLevel level,
        PoseStack pose,
        MultiBufferSource buffers,
        BlockPos supportPos,
        Direction face,
        int packedLight
    ) {
        pose.translate(0.5D, 0.5D, 0.5D);
        pose.mulPose(rotationFromUp(face));
        // 模型底面位于局部 Y=0，将它移到支撑方块正面的外侧。
        pose.translate(-0.5D, 0.5D, -0.5D);
        renderPatchModel(level, pose, buffers, supportPos, packedLight);
    }

    private static void renderPatchModel(
        ClientLevel level,
        PoseStack pose,
        MultiBufferSource buffers,
        BlockPos statePos,
        int packedLight
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        BakedModel model = minecraft.getModelManager().getModel(MODEL);
        dispatcher.getModelRenderer().renderModel(
            pose.last(),
            buffers.getBuffer(RenderType.translucent()),
            level.getBlockState(statePos),
            model,
            1.0F,
            1.0F,
            1.0F,
            packedLight,
            OverlayTexture.NO_OVERLAY
        );
    }

    private static @Nullable PistonMovingBlockEntity findMovingPiston(
        ClientLevel level,
        BlockPos ownerPos,
        BlockState movedState
    ) {
        PistonMovingBlockEntity atDestination = matchingPiston(level, ownerPos, movedState);
        if (atDestination != null) return atDestination;
        for (Direction direction : Direction.values()) {
            BlockPos candidatePos = ownerPos.relative(direction);
            PistonMovingBlockEntity candidate = matchingPiston(level, candidatePos, movedState);
            if (candidate == null) continue;
            Direction movement = candidate.isExtending()
                ? candidate.getDirection()
                : candidate.getDirection().getOpposite();
            if (candidatePos.relative(movement.getOpposite()).equals(ownerPos)) return candidate;
        }
        return null;
    }

    private static @Nullable PistonMovingBlockEntity findMovingPiston(
        ClientLevel level,
        BlockPos ownerPos,
        ResourceLocation movedBlockId
    ) {
        PistonMovingBlockEntity atDestination = matchingPiston(level, ownerPos, movedBlockId);
        if (atDestination != null) return atDestination;
        for (Direction direction : Direction.values()) {
            BlockPos candidatePos = ownerPos.relative(direction);
            PistonMovingBlockEntity candidate = matchingPiston(level, candidatePos, movedBlockId);
            if (candidate == null) continue;
            Direction movement = candidate.isExtending()
                ? candidate.getDirection()
                : candidate.getDirection().getOpposite();
            if (candidatePos.relative(movement.getOpposite()).equals(ownerPos)) return candidate;
        }
        return null;
    }

    private static @Nullable PistonMovingBlockEntity matchingPiston(
        ClientLevel level,
        BlockPos pos,
        BlockState movedState
    ) {
        if (!level.hasChunkAt(pos)) return null;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof PistonMovingBlockEntity piston)
            || piston.isSourcePiston()
            || !piston.getMovedState().is(movedState.getBlock())) {
            return null;
        }
        return piston;
    }

    private static @Nullable PistonMovingBlockEntity matchingPiston(
        ClientLevel level,
        BlockPos pos,
        ResourceLocation movedBlockId
    ) {
        if (!level.hasChunkAt(pos)) return null;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof PistonMovingBlockEntity piston)
            || piston.isSourcePiston()
            || !movedBlockId.equals(BuiltInRegistries.BLOCK.getKey(piston.getMovedState().getBlock()))) {
            return null;
        }
        return piston;
    }

    private static Quaternionf rotationFromUp(Direction face) {
        Direction yAxis = face;
        Direction zAxis = face.getAxis() == Direction.Axis.Z ? Direction.UP : Direction.SOUTH;
        if (yAxis.getAxis() == zAxis.getAxis()) zAxis = Direction.EAST;
        Direction xAxis = cross(yAxis, zAxis);
        zAxis = cross(xAxis, yAxis);
        Matrix3f basis = new Matrix3f()
            .setColumn(0, xAxis.getStepX(), xAxis.getStepY(), xAxis.getStepZ())
            .setColumn(1, yAxis.getStepX(), yAxis.getStepY(), yAxis.getStepZ())
            .setColumn(2, zAxis.getStepX(), zAxis.getStepY(), zAxis.getStepZ());
        return new Quaternionf().setFromNormalized(basis);
    }

    private static Direction cross(Direction first, Direction second) {
        Direction result = Direction.fromDelta(
            first.getStepY() * second.getStepZ() - first.getStepZ() * second.getStepY(),
            first.getStepZ() * second.getStepX() - first.getStepX() * second.getStepZ(),
            first.getStepX() * second.getStepY() - first.getStepY() * second.getStepX()
        );
        if (result == null) throw new IllegalArgumentException("Directions must be perpendicular");
        return result;
    }

    private record Patch(
        BlockPos supportPos,
        Direction face,
        BlockPos ownerPos,
        @Nullable PistonMovingBlockEntity movingPiston
    ) {
        private Patch {
            supportPos = supportPos.immutable();
            ownerPos = ownerPos.immutable();
        }

        private Vec3 movementOffset(float partialTick) {
            if (this.movingPiston == null) return Vec3.ZERO;
            BlockPos pistonPos = this.movingPiston.getBlockPos();
            return new Vec3(
                pistonPos.getX() - this.ownerPos.getX() + this.movingPiston.getXOff(partialTick),
                pistonPos.getY() - this.ownerPos.getY() + this.movingPiston.getYOff(partialTick),
                pistonPos.getZ() - this.ownerPos.getZ() + this.movingPiston.getZOff(partialTick)
            );
        }

        private PatchKey key() {
            return new PatchKey(this.supportPos, this.face);
        }
    }

    private record PatchKey(BlockPos supportPos, Direction face) {
        private PatchKey {
            supportPos = supportPos.immutable();
        }
    }
}
