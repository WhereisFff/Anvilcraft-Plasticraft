package dev.anvilcraft.plasticraft.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.lib.v2.cube.client.CubeSelection;
import dev.anvilcraft.lib.v2.cube.client.OutlineRenderer;
import dev.anvilcraft.lib.v2.cube.client.SelectionPart;
import dev.anvilcraft.lib.v2.rendering.projection.ProjectionRenderer;
import dev.anvilcraft.lib.v2.rendering.projection.ProjectionScene;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberStructure;
import dev.anvilcraft.plasticraft.block.entity.Plastic3DPrintingComponentBlockEntity;
import dev.anvilcraft.plasticraft.client.selection.MachineBlockSelection;
import dev.anvilcraft.plasticraft.client.selection.PlasticSelectionGeometry;
import dev.anvilcraft.plasticraft.config.PlasticraftClientConfig.PlacementPreviewMode;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.item.AbstractPlasticEntityItem;
import dev.anvilcraft.plasticraft.item.Plastic3DPrintingComponentItem;
import dev.anvilcraft.plasticraft.item.PlasticMoldingChamberItem;
import dev.dubhe.anvilcraft.building.BuildingRodService;
import dev.dubhe.anvilcraft.item.BuildingRodItem;
import dev.dubhe.anvilcraft.util.PlacementInteractions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 使用砧库绘制机器和实体的放置预览，预览对象始终不加入世界。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class PlasticraftPlacementPreviewRenderer {
    private static final ProjectionRenderer GHOST = new ProjectionRenderer();
    private static final Map<Direction, SelectionPart> REGIONS = new EnumMap<>(Direction.class);
    private static @Nullable ClientLevel cachedLevel;
    private static @Nullable PlacementKey cachedKey;
    private static ItemStack cachedStack = ItemStack.EMPTY;
    private static @Nullable Preview preview;
    private static long checkedTick = Long.MIN_VALUE;
    private static int ghostAlpha = -1;
    private static boolean ghostDirty = true;

    private PlasticraftPlacementPreviewRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        PlacementPreviewMode mode = AnvilcraftPlasticraft.CLIENT_CONFIG.placementPreviewMode;
        UseOnContext context = mode == PlacementPreviewMode.OFF ? null : placementContext(minecraft);
        if (context == null || minecraft.level == null || minecraft.player == null) {
            clear();
            return;
        }
        update(minecraft.level, context);
        Preview current = preview;
        if (current == null) {
            GHOST.close();
            return;
        }
        // AFTER_LEVEL 不再保留世界渲染的相机旋转，必须先恢复视图矩阵再应用相对位置。
        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        if (mode == PlacementPreviewMode.GHOST) {
            int alpha = (int) (255 * AnvilcraftPlasticraft.CLIENT_CONFIG.placementPreviewGhostOpacity);
            if (ghostDirty || !GHOST.isValid() || ghostAlpha != alpha) {
                rebuildGhost(minecraft.level, context, current, alpha);
            }
            pose.pushPose();
            try {
                pose.translate(current.origin().getX() - camera.x, current.origin().getY() - camera.y,
                    current.origin().getZ() - camera.z);
                GHOST.render(pose, event.getProjectionMatrix(), buffers);
            } finally {
                pose.popPose();
            }
        }
        if (mode != PlacementPreviewMode.GHOST && GHOST.isValid()) {
            GHOST.close();
        }
        if (mode == PlacementPreviewMode.OUTLINE || !current.valid()) {
            renderOutlines(pose, buffers, camera, current.outlines(), current.valid());
        } else if (current.workspace() != null) {
            // 成型工作区没有可见方块模型，虚影模式仍需标出其真实占用范围。
            renderOutlines(pose, buffers, camera, List.of(current.workspace()), true);
        }
    }

    public static @Nullable UseOnContext placementContext(Minecraft minecraft) {
        var player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || player.isSpectator() || minecraft.options.hideGui || minecraft.screen != null
            || BuildingRodItem.isHeld(player)) {
            return null;
        }
        InteractionHand hand = player.getMainHandItem().getItem() instanceof BlockItem
            ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!supports(stack)) {
            return null;
        }
        BlockHitResult hit;
        if (minecraft.hitResult instanceof EntityHitResult entityHit) {
            if (!player.isShiftKeyDown() || !(entityHit.getEntity() instanceof AbstractPlasticEntity plastic)) {
                return null;
            }
            hit = plastic.placementHit(entityHit.getLocation().subtract(plastic.position()));
            if (!level.getBlockState(hit.getBlockPos()).canBeReplaced()) {
                return null;
            }
        } else if (minecraft.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            hit = blockHit;
        } else if (stack.getItem() instanceof AbstractPlasticEntityItem<?>) {
            Vec3 eye = player.getEyePosition();
            hit = level.clip(new ClipContext(eye, eye.add(player.getViewVector(1).scale(player.blockInteractionRange())),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
            if (hit.getType() != HitResult.Type.BLOCK) {
                return null;
            }
        } else {
            return null;
        }
        UseOnContext context = new UseOnContext(level, player, hand, stack, hit);
        boolean installingPrinter = stack.getItem() instanceof Plastic3DPrintingComponentItem
            && level.getBlockState(hit.getBlockPos()).is(PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.get());
        if (!installingPrinter && !PlacementInteractions.allowsPlacement(context)) {
            return null;
        }
        return installingPrinter ? Plastic3DPrintingComponentItem.placementContext(context) : context;
    }

    private static boolean supports(ItemStack stack) {
        return stack.getItem() instanceof AbstractPlasticEntityItem<?>
            || stack.getItem() instanceof PlasticMoldingChamberItem
            || stack.getItem() instanceof Plastic3DPrintingComponentItem
            || stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CondenserTowerBlock;
    }

    private static void update(ClientLevel level, UseOnContext context) {
        BlockHitResult hit = hit(context);
        ItemStack stack = context.getItemInHand();
        PlasticEntityOrientation orientation = stack.getItem() instanceof AbstractPlasticEntityItem<?> item
            ? item.placementOrientation(stack, hit, context.getPlayer()) : null;
        BlockPos occupied = new BlockPlaceContext(context).getClickedPos();
        PlacementKey key = new PlacementKey(context.getClickedPos(), occupied, context.getClickedFace(),
            context.getHorizontalDirection(), context.isSecondaryUseActive(), orientation);
        boolean changed = cachedLevel != level || !key.equals(cachedKey)
            || !ItemStack.isSameItemSameComponents(cachedStack, stack);
        if (!changed && checkedTick == level.getGameTime()) {
            return;
        }
        checkedTick = level.getGameTime();
        if (!changed && preview != null && preview.entity() != null) {
            AbstractPlasticEntity entity = preview.entity();
            preview = new Preview(preview.origin(), preview.blocks(), preview.outlines(), entity, null,
                entity.plasticraft$canOccupy(entity.getOrientation(), entity.position()));
            return;
        }
        Preview next = build(level, context, hit);
        ghostDirty |= changed || preview == null || next == null || !preview.blocks().equals(next.blocks())
            || !preview.origin().equals(next.origin());
        preview = next;
        cachedLevel = level;
        cachedKey = key;
        cachedStack = stack.copyWithCount(1);
    }

    private static @Nullable Preview build(ClientLevel level, UseOnContext context, BlockHitResult hit) {
        if (context.getItemInHand().getItem() instanceof AbstractPlasticEntityItem<?> item) {
            AbstractPlasticEntity entity = item.createPlacementEntity(level, context.getPlayer(), context.getItemInHand(), hit);
            if (entity == null) {
                return null;
            }
            SelectionPart part = PlasticSelectionGeometry.get(entity.plasticraft$getGeometry()).selection(entity.getOrientation());
            return new Preview(BlockPos.containing(entity.position()), List.of(), List.of(new Outline(entity.position(), part)),
                entity, null, entity.plasticraft$canOccupy(entity.getOrientation(), entity.position()));
        }
        if (context.getItemInHand().getItem() instanceof PlasticMoldingChamberItem) {
            BlockPlaceContext placement = new BlockPlaceContext(context);
            BlockPos origin = placement.getClickedPos();
            Direction facing = placement.getHorizontalDirection().getOpposite();
            BlockState state = PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.getDefaultState()
                .setValue(PlasticMoldingChamberBlock.FACING, facing);
            List<Outline> outlines = blockOutlines(level, origin, state);
            Outline workspace = new Outline(Vec3.atLowerCornerOf(origin), REGIONS.computeIfAbsent(facing,
                direction -> PlasticSelectionGeometry.fromShape(Shapes.create(
                    PlasticMoldingChamberStructure.regionBounds(BlockPos.ZERO, direction)))));
            outlines.add(workspace);
            boolean valid = placement.canPlace() && level.isInWorldBounds(origin)
                && state.canSurvive(level, origin) && level.isUnobstructed(state, origin, CollisionContext.of(context.getPlayer()))
                && PlasticMoldingChamberStructure.canPlace(level, origin, facing);
            return new Preview(origin, List.of(new Cell(origin, state)), List.copyOf(outlines), null, workspace, valid);
        }
        List<BuildingRodService.Cell> placed = BuildingRodService.singlePlacement(context);
        if (placed.isEmpty()) {
            return null;
        }
        List<Cell> blocks = placed.stream().map(cell -> new Cell(cell.pos(), cell.state())).toList();
        Cell first = blocks.getFirst();
        List<Outline> outlines = new ArrayList<>();
        SelectionPart towerOutline = MachineBlockSelection.condenserOutline();
        if (first.state().getBlock() instanceof CondenserTowerBlock tower && towerOutline != null) {
            BlockPos main = tower.getMainPartPos(first.pos(), first.state());
            outlines.add(new Outline(Vec3.atLowerCornerOf(main), towerOutline));
        } else {
            for (Cell cell : blocks) {
                outlines.addAll(blockOutlines(level, cell.pos(), cell.state()));
            }
        }
        return new Preview(first.pos(), blocks, List.copyOf(outlines), null, null, true);
    }

    private static List<Outline> blockOutlines(ClientLevel level, BlockPos pos, BlockState state) {
        List<Outline> parts = new ArrayList<>();
        CubeSelection.Target target = CubeSelection.target(level, pos, state, 0);
        if (target != null) {
            Vec3 origin = Vec3.atLowerCornerOf(pos).add(target.offset());
            for (SelectionPart part : target.parts()) {
                parts.add(new Outline(origin, part));
            }
        }
        return parts;
    }

    private static void rebuildGhost(ClientLevel level, UseOnContext context, Preview current, int alpha) {
        ProjectionScene scene = new ProjectionScene(level, current.origin());
        for (Cell cell : current.blocks()) {
            BlockPos local = cell.pos().subtract(current.origin());
            BlockEntity blockEntity = null;
            if (cell.state().is(PlasticraftBlocks.PLASTIC_3D_PRINTING_COMPONENT.get())) {
                blockEntity = new Plastic3DPrintingComponentBlockEntity(local, cell.state());
                CustomData data = context.getItemInHand().get(DataComponents.BLOCK_ENTITY_DATA);
                if (data != null) {
                    blockEntity.loadWithComponents(data.copyTag(), level.registryAccess());
                }
            }
            scene.put(local, cell.state(), blockEntity);
        }
        if (current.entity() != null && context.getItemInHand().getItem() instanceof AbstractPlasticEntityItem<?> item) {
            AbstractPlasticEntity ghost = item.createPlacementEntity(level, context.getPlayer(), context.getItemInHand(), hit(context));
            if (ghost != null) {
                ghost.setPos(ghost.position().subtract(Vec3.atLowerCornerOf(current.origin())));
                ghost.setOldPosAndRot();
                scene.addEntity(ghost);
            }
        }
        GHOST.rebuild(scene, alpha);
        ghostAlpha = alpha;
        ghostDirty = false;
    }

    private static BlockHitResult hit(UseOnContext context) {
        return new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), false);
    }

    private static void renderOutlines(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera,
                                       List<Outline> outlines, boolean valid) {
        float alpha = (float) AnvilcraftPlasticraft.CLIENT_CONFIG.placementPreviewOutlineOpacity;
        for (Outline outline : outlines) {
            pose.pushPose();
            try {
                Vec3 position = outline.origin().subtract(camera);
                pose.translate(position.x, position.y, position.z);
                outline.part().apply(pose);
                OutlineRenderer.render(pose, buffers.getBuffer(RenderType.lines()), CubeSelection.outlines().get(outline.part().geometry())
                    .forView(outline.part().localPosition(camera.subtract(outline.origin()))),
                    1, valid ? 1 : 0.2f, valid ? 1 : 0.2f, alpha);
            } finally {
                pose.popPose();
            }
        }
        buffers.endBatch(RenderType.lines());
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            clear();
        }
    }

    public static void reload(ModelEvent.BakingCompleted event) {
        Minecraft.getInstance().execute(() -> {
            clear();
            REGIONS.clear();
        });
    }

    private static void clear() {
        GHOST.close();
        preview = null;
        cachedLevel = null;
        cachedKey = null;
        cachedStack = ItemStack.EMPTY;
        ghostDirty = true;
    }

    private record Cell(BlockPos pos, BlockState state) {
    }

    private record Outline(Vec3 origin, SelectionPart part) {
    }

    private record Preview(BlockPos origin, List<Cell> blocks, List<Outline> outlines,
                           @Nullable AbstractPlasticEntity entity, @Nullable Outline workspace, boolean valid) {
    }

    private record PlacementKey(BlockPos clicked, BlockPos occupied, Direction face, Direction horizontal, boolean sneaking,
                                @Nullable PlasticEntityOrientation orientation) {
    }
}
