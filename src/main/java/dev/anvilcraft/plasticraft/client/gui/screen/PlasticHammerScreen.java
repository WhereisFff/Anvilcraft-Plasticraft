package dev.anvilcraft.plasticraft.client.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.anvilcraft.lib.v2.wheel.client.gui.component.WheelWidget;
import dev.anvilcraft.plasticraft.client.renderer.entity.PlasticEntityRenderHelper;
import dev.anvilcraft.plasticraft.client.renderer.entity.PlasticEntityRenderTransforms;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.network.BondedPlasticHammerRotatePacket;
import dev.anvilcraft.plasticraft.network.PlasticEntityHammerRotatePacket;
import dev.dubhe.anvilcraft.client.support.RenderSupport;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.List;

/** 铁砧锤长按时使用的六向附着面轮盘。 */
public final class PlasticHammerScreen extends Screen {
    private static final Direction[] DIRECTIONS = {
        Direction.UP,
        Direction.DOWN,
        Direction.NORTH,
        Direction.EAST,
        Direction.SOUTH,
        Direction.WEST
    };
    private static final int DEAD_ZONE = 15;
    private static final float ICON_SCALE = 18.0F;

    private final AbstractPlasticEntity target;
    @Nullable
    private final BlockPos bondedBlockPos;
    private final InteractionHand hand;
    private final int quarterTurn;
    private Direction selectedDirection;
    @Nullable
    private DirectionWheelWidget wheel;
    private boolean finishing;

    public PlasticHammerScreen(AbstractPlasticEntity target, InteractionHand hand) {
        this(target, hand, null);
    }

    public PlasticHammerScreen(AbstractPlasticEntity target, InteractionHand hand, BlockPos bondedBlockPos) {
        super(Component.empty());
        this.target = target;
        this.hand = hand;
        this.bondedBlockPos = bondedBlockPos == null ? null : bondedBlockPos.immutable();
        PlasticEntityOrientation orientation = target.getOrientation();
        this.quarterTurn = orientation.quarterTurn();
        this.selectedDirection = orientation.attachmentFace();
    }

    @Override
    protected void init() {
        float innerRadius = Math.min(this.width, this.height) * 0.12F;
        float outerRadius = Math.min(this.width, this.height) * 0.22F;
        WheelWidget.RawSection[] sections = new WheelWidget.RawSection[DIRECTIONS.length];
        for (int i = 0; i < DIRECTIONS.length; i++) {
            Direction direction = DIRECTIONS[i];
            sections[i] = new WheelWidget.RawSection(
                directionLabel(direction),
                (graphics, pose, width, height) -> this.renderDirectionIcon(graphics, pose, width, height, direction),
                true
            );
        }
        this.wheel = new DirectionWheelWidget(
            0,
            0,
            this.width,
            this.height,
            Component.empty(),
            innerRadius,
            outerRadius,
            0,
            300,
            150,
            0x88000000,
            0xDDFFFF00,
            20,
            5.0F,
            0xFDFDFD,
            1.0F,
            0.0F,
            List.of(sections),
            DEAD_ZONE
        );
        this.wheel.setCurrentIndex(indexOf(this.selectedDirection));
    }

    private static Component directionLabel(Direction direction) {
        return Component.translatable(
            "screen.anvilcraftplasticraft.hammer_direction." + direction.getName()
        );
    }

    private static int indexOf(Direction direction) {
        for (int i = 0; i < DIRECTIONS.length; i++) {
            if (DIRECTIONS[i] == direction) return i;
        }
        return 0;
    }

    private void renderDirectionIcon(
        GuiGraphics graphics,
        PoseStack pose,
        int width,
        int height,
        Direction direction
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        PlasticEntityOrientation preview = new PlasticEntityOrientation(direction, this.quarterTurn);
        pose.pushPose();
        pose.translate(width * 0.5F, height * 0.5F + 2.0F, 0.0F);
        // 沿用本体轮盘模型的原点补偿，抵消方块模型的视觉偏移。
        pose.translate(-7.0F, 7.0F, 0.0F);
        pose.scale(ICON_SCALE, ICON_SCALE, ICON_SCALE);
        pose.mulPose(new Matrix4f().scaling(1.0F, -1.0F, 1.0F));
        pose.translate(0.5F, 0.5F, 0.5F);
        if (camera.getEntity() != null) {
            pose.mulPose(Axis.XP.rotationDegrees(camera.getEntity().getXRot()));
            pose.mulPose(Axis.YP.rotationDegrees(camera.getEntity().getYRot() + 180.0F));
        }
        PlasticEntityRenderTransforms.rotate(pose, preview);
        pose.translate(-0.5F, -0.5F, -0.5F);
        RenderSystem.setupGui3DDiffuseLighting(RenderSupport.L1, RenderSupport.L2);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PlasticEntityRenderHelper.renderBlock(
            this.target,
            dispatcher,
            pose,
            buffers,
            LightTexture.FULL_BLOCK
        );
        buffers.endLastBatch();
        pose.popPose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.wheel != null) {
            this.wheel.updatePointer(mouseX, mouseY);
            this.wheel.render(graphics, mouseX, mouseY, partialTick);
            this.syncSelection();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.wheel == null) return true;
        boolean handled = this.wheel.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        this.syncSelection();
        return handled;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (this.wheel != null) {
            this.wheel.updatePointer(mouseX, mouseY);
            this.syncSelection();
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.wheel != null) {
            this.wheel.updatePointer(mouseX, mouseY);
            this.syncSelection();
        }
        return true;
    }

    private void syncSelection() {
        if (this.wheel == null) return;
        int index = this.wheel.getCurrentSectionIndex();
        if (index >= 0 && index < DIRECTIONS.length) {
            this.selectedDirection = DIRECTIONS[index];
        }
    }

    public boolean targets(AbstractPlasticEntity entity) {
        return this.bondedBlockPos == null && this.target == entity;
    }

    public boolean targets(BlockPos pos) {
        return this.bondedBlockPos != null && this.bondedBlockPos.equals(pos);
    }

    public void completeSelection() {
        if (this.finishing || this.wheel == null) return;
        this.syncSelection();
        if (this.bondedBlockPos == null) {
            PacketDistributor.sendToServer(new PlasticEntityHammerRotatePacket(
                this.target.getId(),
                this.hand,
                this.selectedDirection
            ));
        } else {
            PacketDistributor.sendToServer(new BondedPlasticHammerRotatePacket(
                this.bondedBlockPos,
                this.hand,
                this.selectedDirection
            ));
        }
        this.finishing = true;
        this.wheel.onClosing();
    }

    public void cancelSelection() {
        this.finishing = true;
        Minecraft.getInstance().setScreen(null);
    }

    @Nullable
    public static PlasticEntityOrientation getPreviewOrientation(AbstractPlasticEntity entity) {
        if (!(Minecraft.getInstance().screen instanceof PlasticHammerScreen screen)
            || screen.target != entity
            || screen.finishing) {
            return null;
        }
        return new PlasticEntityOrientation(screen.selectedDirection, screen.quarterTurn);
    }

    @Nullable
    public static PlasticEntityOrientation getPreviewOrientation(BlockPos pos) {
        if (!(Minecraft.getInstance().screen instanceof PlasticHammerScreen screen)
            || !screen.targets(pos)
            || screen.finishing) {
            return null;
        }
        return new PlasticEntityOrientation(screen.selectedDirection, screen.quarterTurn);
    }

    @Override
    public void onClose() {
        if (!this.finishing && this.wheel != null) {
            this.finishing = true;
            this.wheel.onClosing();
            return;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class DirectionWheelWidget extends WheelWidget {
        private boolean scrollLocked;
        private double lockedMouseX;
        private double lockedMouseY;

        private DirectionWheelWidget(
            int x,
            int y,
            int width,
            int height,
            Component message,
            float ringInnerRadius,
            float ringOuterRadius,
            int delay,
            int animationMs,
            int closingAnimationMs,
            int ringColor,
            int selectionEffectColor,
            int selectionEffectRadius,
            float selectionAnimationSpeedFactor,
            int textColor,
            float textScale,
            float degreeOffsetAngle,
            List<RawSection> sections,
            int deadZone
        ) {
            super(
                x,
                y,
                width,
                height,
                message,
                ringInnerRadius,
                ringOuterRadius,
                delay,
                animationMs,
                closingAnimationMs,
                ringColor,
                selectionEffectColor,
                selectionEffectRadius,
                selectionAnimationSpeedFactor,
                textColor,
                textScale,
                degreeOffsetAngle,
                sections,
                deadZone
            );
        }

        private void updatePointer(double mouseX, double mouseY) {
            this.checkMousePos(mouseX, mouseY);
        }

        @Override
        public void checkMousePos(double mouseX, double mouseY) {
            if (this.scrollLocked) {
                double dx = mouseX - this.lockedMouseX;
                double dy = mouseY - this.lockedMouseY;
                if (dx * dx + dy * dy <= 4.0D) return;
                this.scrollLocked = false;
            }
            float centerX = this.getX() + this.getWidth() * 0.5F;
            float centerY = this.getY() + this.getHeight() * 0.5F;
            double dx = mouseX - centerX;
            double dy = mouseY - centerY;
            if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) return;
            super.checkMousePos(mouseX, mouseY);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            boolean handled = super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            if (scrollY != 0.0D) {
                this.scrollLocked = true;
                this.lockedMouseX = mouseX;
                this.lockedMouseY = mouseY;
            }
            return handled;
        }

    }
}
