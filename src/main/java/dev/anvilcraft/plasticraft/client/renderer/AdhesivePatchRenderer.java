package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.BondedFallingBlockInfo;
import dev.anvilcraft.plasticraft.block.BondedFallingChunkData;
import dev.anvilcraft.plasticraft.block.BlockAdhesionState;
import dev.anvilcraft.plasticraft.block.piston.PistonAdhesionController;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.client.renderer.entity.PlasticEntityRenderTransforms;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityAdhesion;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveFaces;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondLink;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondState;
import dev.anvilcraft.plasticraft.entity.adhesive.SlidingAdhesionData;
import dev.anvilcraft.plasticraft.init.ModAttachments;
import dev.anvilcraft.plasticraft.item.ResinAnvilHammerItem;
import dev.dubhe.anvilcraft.entity.SlidingBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
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
    private static List<BondedFace> cachedBondedFaces = List.of();
    private static final double STRETCH_EPSILON = 0.035D;
    private static final double OUTLINE_OFFSET = 0.004D;
    private static final int OUTLINE_ORANGE = 0xFFFF8018;
    private static final int WRAP_SEGMENTS = 6;
    private static final RenderType STRETCHED_ADHESIVE_RENDER_TYPE = Sheets.translucentCullBlockSheet();

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
        buffers.endBatch(STRETCHED_ADHESIVE_RENDER_TYPE);
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
                renderEntityAdhesive(
                    level,
                    pose,
                    buffers,
                    entity,
                    adhesion,
                    partialTick,
                    PistonAdhesionController.movementOffset(level, entity, adhesion, partialTick)
                );
            }
            EntityBondState bonds = EntityBondManager.get(entity);
            if (bonds == null) continue;
            for (EntityBondLink link : bonds.links()) {
                Entity other = EntityBondManager.resolve(level, link);
                if (other == null || entity.getUUID().compareTo(other.getUUID()) >= 0) continue;
                renderEntityBond(level, pose, buffers, entity, link, other, partialTick);
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
        renderBondedFaceOutlines(level, pose, buffers, partialTick);
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
        Set<BondedFace> bondedFaces = new HashSet<>();
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
                        if (state.hasBlockBond(face) || state.hasEntityBond(face)) {
                            bondedFaces.add(new BondedFace(ownerPos, face));
                        }
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
        cachedBondedFaces = List.copyOf(bondedFaces);
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

    private static void renderEntityAdhesive(
        ClientLevel level,
        PoseStack pose,
        MultiBufferSource buffers,
        Entity entity,
        EntityAdhesion adhesion,
        float partialTick,
        Vec3 movementOffset
    ) {
        Vec3 anchor = adhesionAnchor(entity, adhesion).add(movementOffset);
        Vec3 currentPosition = entity.getPosition(partialTick);
        Vec3 attachedPoint = anchor.add(currentPosition.subtract(adhesion.fixedPosition()));
        if (anchor.distanceToSqr(attachedPoint) <= STRETCH_EPSILON * STRETCH_EPSILON) {
            renderPatch(level, pose, buffers, adhesion.supportPos(), adhesion.attachmentFace(), movementOffset);
            return;
        }
        renderStretchSegment(level, pose, buffers, anchor, attachedPoint, Vec3.ZERO);
    }

    private static Vec3 adhesionAnchor(Entity entity, EntityAdhesion adhesion) {
        AABB restBox = entity.getBoundingBox().move(adhesion.fixedPosition().subtract(entity.position()));
        BlockPos supportPos = adhesion.supportPos();
        Direction face = adhesion.attachmentFace();
        double x = overlapCenter(restBox.minX, restBox.maxX, supportPos.getX() + 0.25D, supportPos.getX() + 0.75D);
        double y = overlapCenter(restBox.minY, restBox.maxY, supportPos.getY() + 0.25D, supportPos.getY() + 0.75D);
        double z = overlapCenter(restBox.minZ, restBox.maxZ, supportPos.getZ() + 0.25D, supportPos.getZ() + 0.75D);
        return switch (face) {
            case EAST -> new Vec3(supportPos.getX() + 1.0D, y, z);
            case WEST -> new Vec3(supportPos.getX(), y, z);
            case UP -> new Vec3(x, supportPos.getY() + 1.0D, z);
            case DOWN -> new Vec3(x, supportPos.getY(), z);
            case SOUTH -> new Vec3(x, y, supportPos.getZ() + 1.0D);
            case NORTH -> new Vec3(x, y, supportPos.getZ());
        };
    }

    private static double overlapCenter(double firstMin, double firstMax, double secondMin, double secondMax) {
        double min = Math.max(firstMin, secondMin);
        double max = Math.min(firstMax, secondMax);
        if (max > min) return (min + max) * 0.5D;
        return Math.clamp((firstMin + firstMax) * 0.5D, secondMin, secondMax);
    }

    private static void renderEntityBond(
        ClientLevel level,
        PoseStack pose,
        MultiBufferSource buffers,
        Entity entity,
        EntityBondLink link,
        Entity other,
        float partialTick
    ) {
        Vec3 firstPoint = entityBondSurfacePoint(entity, link.face(), partialTick);
        Vec3 secondPoint = entityBondSurfacePoint(other, link.otherFace(), partialTick);
        if (firstPoint.distanceToSqr(secondPoint) <= STRETCH_EPSILON * STRETCH_EPSILON) {
            renderEntityPatch(level, pose, buffers, entity, link.face(), partialTick);
            return;
        }
        renderStretchSegment(level, pose, buffers, firstPoint, secondPoint, Vec3.ZERO);
    }

    private static Vec3 entityBondSurfacePoint(Entity entity, Direction storedFace, float partialTick) {
        if (!(entity instanceof AbstractPlasticEntity plasticEntity)) {
            return entitySurfacePoint(entity, storedFace, partialTick);
        }
        AbstractPlasticEntity.HammerRotationAnimation animation =
            plasticEntity.getHammerRotationAnimation(partialTick);
        PlasticEntityOrientation from = animation == null ? plasticEntity.getOrientation() : animation.from();
        PlasticEntityOrientation to = animation == null ? from : animation.to();
        float progress = animation == null ? 0.0F : animation.progress();
        return entity.getPosition(partialTick).add(PlasticEntityRenderTransforms.faceCenterOffset(
            plasticEntity,
            storedFace,
            from,
            to,
            progress
        ));
    }

    private static Vec3 entitySurfacePoint(Entity entity, Direction face, float partialTick) {
        AABB box = entity.getBoundingBox().move(entity.getPosition(partialTick).subtract(entity.position()));
        Vec3 center = box.getCenter();
        return switch (face) {
            case EAST -> new Vec3(box.maxX, center.y, center.z);
            case WEST -> new Vec3(box.minX, center.y, center.z);
            case UP -> new Vec3(center.x, box.maxY, center.z);
            case DOWN -> new Vec3(center.x, box.minY, center.z);
            case SOUTH -> new Vec3(center.x, center.y, box.maxZ);
            case NORTH -> new Vec3(center.x, center.y, box.minZ);
        };
    }

    private static void renderStretchSegment(
        ClientLevel level,
        PoseStack pose,
        MultiBufferSource buffers,
        Vec3 from,
        Vec3 to,
        Vec3 worldOffset
    ) {
        Vec3 difference = to.subtract(from);
        double distance = difference.length();
        if (distance <= 1.0E-5D) return;
        Vec3 middle = from.add(to).scale(0.5D);
        Quaternionf rotation = new Quaternionf().rotationTo(
            new Vector3f(0.0F, 1.0F, 0.0F),
            new Vector3f((float) difference.x, (float) difference.y, (float) difference.z).normalize()
        );
        pose.pushPose();
        pose.translate(middle.x, middle.y, middle.z);
        pose.mulPose(rotation);
        pose.scale(0.58F, (float) ((distance + 0.12D) / 0.375D), 0.58F);
        pose.translate(-0.5D, 0.0D, -0.5D);
        renderPatchModel(
            pose,
            buffers,
            null,
            STRETCHED_ADHESIVE_RENDER_TYPE,
            stretchedAdhesiveLight(level, middle.add(worldOffset))
        );
        pose.popPose();
    }

    private static int stretchedAdhesiveLight(ClientLevel level, Vec3 worldPosition) {
        BlockPos center = BlockPos.containing(worldPosition);
        int packedLight = LevelRenderer.getLightColor(level, center);
        for (Direction direction : Direction.values()) {
            packedLight = maxPackedLight(
                packedLight,
                LevelRenderer.getLightColor(level, center.relative(direction))
            );
        }
        return packedLight;
    }

    private static int maxPackedLight(int first, int second) {
        return LightTexture.pack(
            Math.max(LightTexture.block(first), LightTexture.block(second)),
            Math.max(LightTexture.sky(first), LightTexture.sky(second))
        );
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

    public static void renderAttachedBlockAdhesive(
        ClientLevel level,
        PoseStack pose,
        MultiBufferSource buffers,
        BondedEntityBlockEntity blockEntity,
        int packedLight,
        float partialTick
    ) {
        BlockPos blockPos = blockEntity.getBlockPos();
        if (!(blockEntity.getOrCreateRenderEntity() instanceof AbstractPlasticEntity entity)
            || !blockEntity.isHammerDeflected()) {
            renderAttachedPatch(
                level,
                pose,
                buffers,
                blockPos,
                blockEntity.getSupportPos(),
                blockEntity.getAttachmentFace(),
                packedLight
            );
            return;
        }

        BondedEntityBlockEntity.HammerRotationAnimation animation =
            blockEntity.getHammerRotationAnimation(partialTick);
        PlasticEntityOrientation from = animation == null
            ? blockEntity.getPlasticOrientation()
            : animation.from();
        PlasticEntityOrientation to = animation == null
            ? from
            : animation.to();
        float progress = animation == null ? 0.0F : animation.progress();
        Vec3 fromPosition = from.entityPosition(blockPos, entity.getBbWidth(), entity.getBbHeight());
        Vec3 toPosition = to.entityPosition(blockPos, entity.getBbWidth(), entity.getBbHeight());
        Vec3 entityPosition = fromPosition.lerp(toPosition, progress);
        Vec3 attachedPoint = entityPosition.add(PlasticEntityRenderTransforms.faceCenterOffset(
            entity,
            blockEntity.getAdhesiveLocalFace(),
            from,
            to,
            progress
        ));
        Vec3 anchor = Vec3.atCenterOf(blockEntity.getSupportPos()).add(
            Vec3.atLowerCornerOf(blockEntity.getAttachmentFace().getNormal()).scale(0.5D)
        );
        Vec3 origin = Vec3.atLowerCornerOf(blockPos);
        renderWrappedAdhesive(
            level,
            pose,
            buffers,
            blockPos,
            anchor.subtract(origin),
            attachedPoint.subtract(origin),
            Vec3.atCenterOf(blockPos).subtract(origin)
        );
    }

    private static void renderWrappedAdhesive(
        ClientLevel level,
        PoseStack pose,
        MultiBufferSource buffers,
        BlockPos renderOrigin,
        Vec3 from,
        Vec3 to,
        Vec3 blockCenter
    ) {
        Vec3 firstNormal = from.subtract(blockCenter).normalize();
        Vec3 secondNormal = to.subtract(blockCenter).normalize();
        Vec3 middleNormal = firstNormal.add(secondNormal);
        if (middleNormal.lengthSqr() < 1.0E-6D) {
            middleNormal = firstNormal.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (middleNormal.lengthSqr() < 1.0E-6D) middleNormal = firstNormal.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        Vec3 control = blockCenter.add(middleNormal.normalize().scale(0.88D));
        Vec3 previous = from;
        for (int segment = 1; segment <= WRAP_SEGMENTS; segment++) {
            double progress = segment / (double) WRAP_SEGMENTS;
            double inverse = 1.0D - progress;
            Vec3 current = from.scale(inverse * inverse)
                .add(control.scale(2.0D * inverse * progress))
                .add(to.scale(progress * progress));
            renderStretchSegment(
                level,
                pose,
                buffers,
                previous,
                current,
                Vec3.atLowerCornerOf(renderOrigin)
            );
            previous = current;
        }
    }

    private static void renderBondedFaceOutlines(
        ClientLevel level,
        PoseStack pose,
        MultiBufferSource.BufferSource buffers,
        float partialTick
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
            || !(minecraft.player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof ResinAnvilHammerItem)) {
            return;
        }

        List<ThickLineRenderer.Segment> outlineSegments = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!entity.isAlive()) continue;
            Set<Direction> faces = new HashSet<>();
            EntityAdhesion adhesion = entity.getExistingDataOrNull(ModAttachments.ENTITY_ADHESION.get());
            if (adhesion != null) faces.add(adhesion.attachmentFace().getOpposite());
            EntityBondState bonds = EntityBondManager.get(entity);
            if (bonds != null) {
                for (EntityBondLink link : bonds.links()) {
                    faces.add(AdhesiveFaces.worldFace(entity, link.face()));
                }
            }
            if (faces.isEmpty()) continue;
            AABB box = entity.getBoundingBox().move(entity.getPosition(partialTick).subtract(entity.position()));
            for (Direction face : faces) addFaceOutline(outlineSegments, box, face);
        }
        for (BondedFace bondedFace : cachedBondedFaces) {
            addFaceOutline(outlineSegments, new AABB(bondedFace.pos()), bondedFace.face());
        }
        ThickLineRenderer.renderSegments(
            pose,
            buffers,
            outlineSegments,
            OUTLINE_ORANGE,
            ThickLineRenderer.SELECTION_WIDTH
        );
    }

    private static void addFaceOutline(
        List<ThickLineRenderer.Segment> outlineSegments,
        AABB box,
        Direction face
    ) {
        double plane = switch (face.getAxis()) {
            case X -> (face == Direction.EAST ? box.maxX : box.minX) + face.getStepX() * OUTLINE_OFFSET;
            case Y -> (face == Direction.UP ? box.maxY : box.minY) + face.getStepY() * OUTLINE_OFFSET;
            case Z -> (face == Direction.SOUTH ? box.maxZ : box.minZ) + face.getStepZ() * OUTLINE_OFFSET;
        };
        Vec3 first;
        Vec3 second;
        Vec3 third;
        Vec3 fourth;
        switch (face.getAxis()) {
            case X -> {
                first = new Vec3(plane, box.minY, box.minZ);
                second = new Vec3(plane, box.maxY, box.minZ);
                third = new Vec3(plane, box.maxY, box.maxZ);
                fourth = new Vec3(plane, box.minY, box.maxZ);
            }
            case Y -> {
                first = new Vec3(box.minX, plane, box.minZ);
                second = new Vec3(box.maxX, plane, box.minZ);
                third = new Vec3(box.maxX, plane, box.maxZ);
                fourth = new Vec3(box.minX, plane, box.maxZ);
            }
            case Z -> {
                first = new Vec3(box.minX, box.minY, plane);
                second = new Vec3(box.maxX, box.minY, plane);
                third = new Vec3(box.maxX, box.maxY, plane);
                fourth = new Vec3(box.minX, box.maxY, plane);
            }
            default -> throw new MatchException(null, null);
        }
        outlineSegments.add(new ThickLineRenderer.Segment(first, second));
        outlineSegments.add(new ThickLineRenderer.Segment(second, third));
        outlineSegments.add(new ThickLineRenderer.Segment(third, fourth));
        outlineSegments.add(new ThickLineRenderer.Segment(fourth, first));
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
        renderPatchModel(
            pose,
            buffers,
            level.getBlockState(statePos),
            RenderType.translucent(),
            packedLight
        );
    }

    private static void renderPatchModel(
        PoseStack pose,
        MultiBufferSource buffers,
        @Nullable BlockState state,
        RenderType renderType,
        int packedLight
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        BakedModel model = minecraft.getModelManager().getModel(MODEL);
        dispatcher.getModelRenderer().renderModel(
            pose.last(),
            buffers.getBuffer(renderType),
            state,
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

    private record BondedFace(BlockPos pos, Direction face) {
        private BondedFace {
            pos = pos.immutable();
        }
    }
}
