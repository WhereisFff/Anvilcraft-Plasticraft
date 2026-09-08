package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintData;
import dev.anvilcraft.plasticraft.blueprint.StructureSnapshot;
import dev.anvilcraft.plasticraft.client.blueprint.ClientBlueprintSnapshotCache;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.item.property.component.StructureDiskData;
import dev.dubhe.anvilcraft.util.StructureLoadUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 通过本体公开预览扩展点接管结构缩略图，缓存仅保留最近一张磁盘。 */
public final class StructureDiskPreviewRenderer {
    private static final int PREVIEW_SIZE = 80;
    private static final long IDLE_TIMEOUT_NS = 10_000_000_000L;
    private static @Nullable ClientLevel cachedLevel;
    private static @Nullable Object cachedKey;
    private static @Nullable StructureDiskPreviewMesh cachedMesh;
    private static long lastUse;

    private StructureDiskPreviewRenderer() {
    }

    public static boolean renderPreviewAt(GuiGraphics graphics, ItemStack stack, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        StructureDiskData disk = stack.get(ModComponents.STRUCTURE_DISK_DATA);
        ConstructionBlueprintData blueprint = ConstructionBlueprintData.get(stack).orElse(null);
        if (level == null || (blueprint == null && (disk == null || disk.file().isEmpty()))) return false;
        Object key = blueprint == null ? disk : blueprint.hash();
        if (cachedLevel != level || !Objects.equals(cachedKey, key)) {
            clearCache();
            cachedLevel = level;
            cachedKey = key;
        }
        lastUse = System.nanoTime();
        if (cachedMesh == null) {
            StructureDiskPreviewMesh.Source source = source(level, stack, blueprint);
            if (source != null) cachedMesh = new StructureDiskPreviewMesh(level, source);
        }
        if (cachedMesh != null) cachedMesh.prepare();

        int x = Mth.clamp(mouseX - PREVIEW_SIZE / 2, 5,
            Math.max(5, minecraft.getWindow().getGuiScaledWidth() - PREVIEW_SIZE - 5));
        int y = mouseY - PREVIEW_SIZE - 16;
        if (y < 0) y = mouseY + 30;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(0, 0, 5000);
            graphics.fill(x - 2, y - 2, x + PREVIEW_SIZE + 2, y + PREVIEW_SIZE + 2, 0xF0100010);
            graphics.renderOutline(x - 2, y - 2, PREVIEW_SIZE + 4, PREVIEW_SIZE + 4, 0x505000FF);
            graphics.flush();
            if (cachedMesh != null) renderModel(graphics, cachedMesh, x, y);
            if (cachedMesh == null || cachedMesh.progress() < 1.0F) {
                float progress = cachedMesh == null ? 0.0F : cachedMesh.progress();
                graphics.fill(x + 4, y + PREVIEW_SIZE - 5, x + PREVIEW_SIZE - 4, y + PREVIEW_SIZE - 3, 0xFF303040);
                graphics.fill(x + 4, y + PREVIEW_SIZE - 5,
                    x + 4 + (int) ((PREVIEW_SIZE - 8) * progress), y + PREVIEW_SIZE - 3, 0xFF75BFFF);
                graphics.flush();
            }
        } finally {
            pose.popPose();
            RenderSystem.disableDepthTest();
            RenderSystem.disableBlend();
        }
        StructureDiskPreviewScanEffect.render(x, y, PREVIEW_SIZE);
        return true;
    }

    private static @Nullable StructureDiskPreviewMesh.Source source(
        ClientLevel level, ItemStack stack, @Nullable ConstructionBlueprintData blueprint
    ) {
        if (blueprint != null) {
            StructureSnapshot snapshot = ClientBlueprintSnapshotCache.snapshotOrRequest(blueprint.hash());
            return snapshot == null ? null : new SnapshotSource(snapshot);
        }
        StructureLoadUtil.StructureData data = StructureLoadUtil.loadStructureFromDiskForPreview(level, stack);
        if (data == null) return null;
        Rotation rotation = switch (data.diskData.direction()) {
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.CLOCKWISE_90;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        return new ScannerSource(data, rotation);
    }

    private static void renderModel(GuiGraphics graphics, StructureDiskPreviewMesh mesh, int x, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        int sizeX = mesh.horizontalSize();
        int sizeY = mesh.verticalSize();
        float scale = Math.min(60.0F / (sizeX * Mth.SQRT_OF_TWO), 60.0F / sizeY);
        float centerOffset = ((sizeX + 1) % 2 != 0) ? -0.5F : 0.0F;
        float offsetX = -sizeX / 2.0F + centerOffset;
        float offsetZ = -sizeX / 2.0F + 1 + centerOffset;
        float rotation = (minecraft.level.getGameTime() + minecraft.getTimer().getGameTimeDeltaPartialTick(true)) * 2.0F;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x + PREVIEW_SIZE / 2, y + PREVIEW_SIZE / 2, 100);
            pose.scale(-scale, -scale, -scale);
            pose.translate(offsetX, -sizeY / 2.0F, 0);
            pose.mulPose(Axis.XP.rotationDegrees(-30));
            pose.translate(-offsetX, 0, -offsetZ);
            pose.mulPose(Axis.YP.rotationDegrees(rotation + 45));
            pose.translate(offsetX, 0, offsetZ - 1);
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            mesh.render(pose);
        } finally {
            pose.popPose();
        }
    }

    public static void tick() {
        if (cachedLevel != null && (cachedLevel != Minecraft.getInstance().level
            || System.nanoTime() - lastUse > IDLE_TIMEOUT_NS)) clearCache();
    }

    public static void clearCache() {
        if (cachedMesh != null) cachedMesh.close();
        cachedMesh = null;
        cachedKey = null;
        cachedLevel = null;
        StructureDiskPreviewScanEffect.clear();
    }

    private record SnapshotSource(StructureSnapshot snapshot) implements StructureDiskPreviewMesh.Source {
        @Override
        public int size() {
            return this.snapshot.blocks().size();
        }

        @Override
        public BlockPos pos(int index) {
            return this.snapshot.blocks().get(index).pos();
        }

        @Override
        public BlockState state(int index) {
            return this.snapshot.palette().get(this.snapshot.blocks().get(index).stateIndex());
        }
    }

    private record ScannerSource(StructureLoadUtil.StructureData data, Rotation rotation)
        implements StructureDiskPreviewMesh.Source {
        @Override
        public int size() {
            return this.data.blocks.size();
        }

        @Override
        public BlockPos pos(int index) {
            StructureLoadUtil.BlockPosition block = this.data.blocks.get(index);
            return new BlockPos(block.x(), block.y(), block.z());
        }

        @Override
        public BlockState state(int index) {
            return this.data.blocks.get(index).state().rotate(this.rotation);
        }
    }
}
