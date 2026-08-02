package dev.anvilcraft.plasticraft.client.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberStructure;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingAxis;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingCamera;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingEditorController;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingGizmoGeometry;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingGuiTransform;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingHitTester;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingNumericProperty;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingSelection;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingTool;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingToolButtonState;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingViewPreset;
import dev.anvilcraft.plasticraft.client.molding.editor.ViewportRay;
import dev.anvilcraft.plasticraft.client.molding.editor.ViewportTransform;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorSceneMesh;
import dev.anvilcraft.plasticraft.client.molding.scene.MoldingSceneBuilder;
import dev.anvilcraft.plasticraft.client.molding.scene.ViewportFrame;
import dev.anvilcraft.plasticraft.client.renderer.molding.MoldingViewportBackend;
import dev.anvilcraft.plasticraft.client.renderer.molding.MoldingViewportBackend1211;
import dev.anvilcraft.plasticraft.inventory.PlasticMoldingChamberMenu;
import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingCommand;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingGroup;
import dev.anvilcraft.plasticraft.network.MoldingEditPacket;
import dev.anvilcraft.plasticraft.network.MoldingHeartbeatPacket;
import dev.anvilcraft.plasticraft.network.MoldingReleasePacket;
import dev.anvilcraft.plasticraft.network.MoldingTakeoverPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** 349x226 成型舱界面的客户端宿主与离屏建模器入口。 */
public class PlasticMoldingChamberScreen extends AbstractContainerScreen<PlasticMoldingChamberMenu> {
    public static final int LOGICAL_WIDTH = 349;
    public static final int LOGICAL_HEIGHT = 226;
    public static final int VIEWPORT_X = 32;
    public static final int VIEWPORT_Y = 18;
    public static final int VIEWPORT_WIDTH = 230;
    public static final int VIEWPORT_HEIGHT = 115;
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 256;
    private static final int OVERLAY_WIDTH = 73;
    private static final int OVERLAY_HEIGHT = 131;
    private static final int OVERLAY_Y = 72;
    private static final int HEARTBEAT_INTERVAL = 40;
    private static final int CONTEXT_WIDTH = 92;
    private static final int CONTEXT_ROW_HEIGHT = 11;
    private static final int ELEMENT_VISIBLE_ROWS = 2;
    private static final int ELEMENT_SCROLLBAR_X = 335;
    private static final int ELEMENT_SCROLLBAR_Y = 81;
    private static final int ELEMENT_SCROLLBAR_WIDTH = 6;
    private static final int ELEMENT_SCROLLBAR_HEIGHT = 49;
    private static final int ELEMENT_SCROLL_TRACK_Y = 82;
    private static final int ELEMENT_SCROLL_TRACK_HEIGHT = 46;
    private static final int ELEMENT_SCROLL_HANDLE_WIDTH = 5;
    private static final int ELEMENT_SCROLL_HANDLE_HEIGHT = 9;
    private static final int ELEMENT_SCROLL_HANDLE_TEXTURE_WIDTH = 10;
    private static final double CAMERA_DRAG_THRESHOLD_SQUARED = 4.0D;
    private static final int VIEWPORT_SUPERSAMPLING = 2;
    private static final int DIRECTION_COMPASS_X = VIEWPORT_WIDTH - 18;
    private static final int DIRECTION_COMPASS_Y = 18;
    private static final double DIRECTION_COMPASS_LINE_RADIUS = 6.5D;
    private static final double DIRECTION_COMPASS_LABEL_RADIUS = 12.5D;
    private static final double DIRECTION_COMPASS_LINE_WIDTH = 0.35D;
    private static final double DIRECTION_COMPASS_FEATHER = 0.25D;
    private static final MoldingTool[] TOOL_BUTTONS = {
        MoldingTool.MOVE,
        MoldingTool.SCALE,
        MoldingTool.ROTATE,
        MoldingTool.PIVOT,
        MoldingTool.MIRROR
    };
    private static final String[] TOOL_NAMES = {"move", "scale", "rotate", "pivot", "mirror"};
    private static final ResourceLocation BACKGROUND = texture(
        "textures/gui/background/plastic_molding_chamber.png"
    );
    private static final ResourceLocation LEFT_OVERLAY = texture(
        "textures/gui/background/plastic_molding_chamber_left.png"
    );
    private static final ResourceLocation RIGHT_OVERLAY = texture(
        "textures/gui/background/plastic_molding_chamber_right.png"
    );
    private static final ResourceLocation BUTTON_16_3 = widget("button_16x16_3.png");
    private static final ResourceLocation BUTTON_32_23_2 = widget("button_32x23_2.png");
    private static final ResourceLocation CUBE_BUTTON = widget("cube.png");
    private static final ResourceLocation NEW_CUBE_BUTTON = widget("new_cube.png");
    private static final ResourceLocation ELEMENT_SCROLL_HANDLE = ResourceLocation.fromNamespaceAndPath(
        "anvilcraft",
        "textures/gui/machine/slider.png"
    );
    private static final ResourceLocation BUTTON_10_3 = widget("button_10x10_3.png");
    private static final ResourceLocation BUTTON_34_16_4 = widget("button_34x16_4.png");
    private static final ResourceLocation BUTTON_13_3 = widget("button_13x13_3.png");
    private static final ResourceLocation BUTTON_13_4 = widget("button_13x13_4.png");
    private static final ResourceLocation BUTTON_52_15_2 = widget("button_52x15_2.png");
    private static final ResourceLocation MOVE_TOOL = widget("move.png");
    private static final ResourceLocation SCALE_TOOL = widget("resize.png");
    private static final ResourceLocation ROTATE_TOOL = widget("rotate.png");
    private static final ResourceLocation PIVOT_TOOL = widget("pivot_tool.png");
    private static final ResourceLocation MIRROR_TOOL = widget("flip.png");

    private final MoldingViewportBackend viewportBackend = new MoldingViewportBackend1211();
    private final MoldingCamera camera = new MoldingCamera();
    private final MoldingEditorController editor;
    private final MoldingToolButtonState toolButtons = new MoldingToolButtonState(MoldingTool.MOVE);
    private final List<NumericField> numericFields = new ArrayList<>();
    private EditableMoldingModel seenMenuModel;
    private long seenMenuRevision;
    private boolean seenWritable;
    private String seenWriterName;
    private boolean leftOverlayOpen;
    private boolean rightOverlayOpen;
    private boolean showGrid = true;
    private boolean showAxes = true;
    private MoldingGuiTransform layoutTransform = new MoldingGuiTransform(1.0D, 0.0D, 0.0D);
    private int heartbeatCountdown = HEARTBEAT_INTERVAL;
    private int elementScrollRow;
    private long dynamicSceneRevision;
    @Nullable
    private EditorSceneMesh cachedScene;
    @Nullable
    private BakedMoldingModel cachedBaked;
    @Nullable
    private EditableMoldingModel cachedSceneModel;
    private MoldingSelection cachedSelection = MoldingSelection.EMPTY;
    private MoldingTool cachedTool = MoldingTool.MOVE;
    private boolean cachedInvalid;
    private boolean cachedShowGrid = true;
    private boolean cachedShowAxes = true;
    private double cachedGizmoWorldUnitsPerPixel = Double.NaN;
    private final Vector3d cachedGizmoCameraDirection = new Vector3d();
    @Nullable
    private UUID cachedHoveredElement;
    @Nullable
    private MoldingAxis cachedHoveredGizmoAxis;
    @Nullable
    private List<Component> hoveredControlTooltip;
    @Nullable
    private EditBox leftSearch;
    @Nullable
    private EditBox rightSearch;
    @Nullable
    private EditBox renameField;
    private boolean contextMenuOpen;
    private int contextMenuX;
    private int contextMenuY;
    private int cameraDragButton = -1;
    private boolean cameraDragged;
    private boolean elementScrollbarDragging;
    private boolean newCubeButtonPressed;
    private double elementScrollbarGrabOffset;
    private double cameraDragStartX;
    private double cameraDragStartY;
    private double lastDragX;
    private double lastDragY;
    @Nullable
    private MoldingAxis modelDragAxis;
    private double modelDragStartX;
    private double modelDragStartY;
    @Nullable
    private ViewportTransform modelDragTransform;
    @Nullable
    private Vector3d modelDragOrigin;
    private double modelDragDirection = 1.0D;
    private boolean removed;

    public PlasticMoldingChamberScreen(
        PlasticMoldingChamberMenu menu,
        Inventory inventory,
        Component title
    ) {
        super(menu, inventory, title);
        this.imageWidth = LOGICAL_WIDTH;
        this.imageHeight = LOGICAL_HEIGHT;
        this.seenMenuModel = menu.model();
        this.seenMenuRevision = menu.revision();
        this.seenWritable = menu.writable();
        this.seenWriterName = menu.writerName();
        this.editor = new MoldingEditorController(
            menu.model(),
            menu.revision(),
            menu.writable(),
            this::sendCommand
        );
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.titleLabelY = 2;
        this.inventoryLabelY = -1000;
        createNumericFields();
        this.leftSearch = createSearchBox("screen.anvilcraftplasticraft.molding.search_type");
        this.rightSearch = createSearchBox("screen.anvilcraftplasticraft.molding.search_json");
        this.renameField = new EditBox(
            this.font,
            CONTEXT_WIDTH,
            13,
            Component.translatable("screen.anvilcraftplasticraft.molding.rename")
        );
        this.renameField.setMaxLength(MoldingElement.MAX_NAME_LENGTH);
        this.renameField.setVisible(false);
        this.addRenderableWidget(this.leftSearch);
        this.addRenderableWidget(this.rightSearch);
        this.addRenderableWidget(this.renameField);
        updateLayout();
        refreshNumericFields();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
        double logicalMouseX = toLogicalX(mouseX);
        double logicalMouseY = toLogicalY(mouseY);
        this.hoveredControlTooltip = null;
        graphics.pose().pushPose();
        graphics.pose().translate(
            (float) this.layoutTransform.originX(),
            (float) this.layoutTransform.originY(),
            0.0F
        );
        graphics.pose().scale((float) this.layoutTransform.scale(), (float) this.layoutTransform.scale(), 1.0F);
        try {
            super.render(graphics, (int) logicalMouseX, (int) logicalMouseY, partialTick);
            updateNumericFieldTooltip(logicalMouseX, logicalMouseY);
        } finally {
            graphics.pose().popPose();
        }
        super.renderTooltip(graphics, mouseX, mouseY);
        if (this.hoveredControlTooltip != null && this.hoveredSlot == null) {
            graphics.renderTooltip(
                this.font,
                wrapTooltip(this.hoveredControlTooltip, graphics.guiWidth(), mouseX),
                mouseX,
                mouseY
            );
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(
            BACKGROUND,
            this.leftPos,
            this.topPos,
            0,
            0,
            LOGICAL_WIDTH,
            LOGICAL_HEIGHT,
            TEXTURE_WIDTH,
            TEXTURE_HEIGHT
        );
        if (this.leftOverlayOpen) {
            graphics.blit(
                LEFT_OVERLAY,
                this.leftPos - 72,
                this.topPos + OVERLAY_Y,
                0,
                0,
                OVERLAY_WIDTH,
                OVERLAY_HEIGHT,
                OVERLAY_WIDTH,
                OVERLAY_HEIGHT
            );
        }
        if (this.rightOverlayOpen) {
            graphics.blit(
                RIGHT_OVERLAY,
                this.leftPos + 348,
                this.topPos + OVERLAY_Y,
                0,
                0,
                OVERLAY_WIDTH,
                OVERLAY_HEIGHT,
                OVERLAY_WIDTH,
                OVERLAY_HEIGHT
            );
        }
        renderViewport(graphics, mouseX, mouseY);
        renderToolButtons(graphics, mouseX, mouseY);
        renderElementList(graphics, mouseX, mouseY);
        renderMainControls(graphics, mouseX, mouseY);
        renderOverlays(graphics, mouseX, mouseY);
        if (this.contextMenuOpen) renderContextMenu(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x202020, false);
        if (new GuiRect(0, 0, LOGICAL_WIDTH, 13).contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            this.hoveredControlTooltip = titleStatusTooltip();
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        synchronizeMenuSnapshot();
        if (--this.heartbeatCountdown <= 0) {
            this.heartbeatCountdown = HEARTBEAT_INTERVAL;
            if (this.menu.writable()) {
                PacketDistributor.sendToServer(new MoldingHeartbeatPacket(
                    this.menu.chamberPos(),
                    this.menu.sessionId(),
                    this.editor.authoritativeRevision()
                ));
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double logicalX = toLogicalX(mouseX);
        double logicalY = toLogicalY(mouseY);
        if (!isOverSearchField(logicalX, logicalY)) clearSearchFocus();
        if (isOverTextField(logicalX, logicalY)) {
            this.contextMenuOpen = false;
            return super.mouseClicked(logicalX, logicalY, button);
        }
        if (button == 0) commitFocusedNumeric();
        if (handleContextClick(logicalX, logicalY, button)) return true;
        if (handleElementScrollbarClick(logicalX, logicalY, button)) return true;
        if (handleFixedControlClick(logicalX, logicalY, button)) return true;
        if (isInsideMain(VIEWPORT_X, VIEWPORT_Y, VIEWPORT_WIDTH, VIEWPORT_HEIGHT, logicalX, logicalY)) {
            return handleViewportClick(logicalX, logicalY, button);
        }
        this.contextMenuOpen = false;
        return super.mouseClicked(logicalX, logicalY, button);
    }

    @Override
    public boolean mouseDragged(
        double mouseX,
        double mouseY,
        int button,
        double dragX,
        double dragY
    ) {
        double logicalX = toLogicalX(mouseX);
        double logicalY = toLogicalY(mouseY);
        if (this.elementScrollbarDragging && button == 0) {
            updateElementScrollFromPointer(logicalY, elementEntryCount());
            return true;
        }
        if (this.modelDragAxis != null && button == 0) {
            updateModelDrag(logicalX, logicalY);
            return true;
        }
        if (this.cameraDragButton == button) {
            double fromStartX = logicalX - this.cameraDragStartX;
            double fromStartY = logicalY - this.cameraDragStartY;
            boolean startedThisFrame = !this.cameraDragged
                && fromStartX * fromStartX + fromStartY * fromStartY > CAMERA_DRAG_THRESHOLD_SQUARED;
            if (startedThisFrame) this.cameraDragged = true;
            if (this.cameraDragged) {
                double deltaX = startedThisFrame ? fromStartX : logicalX - this.lastDragX;
                double deltaY = startedThisFrame ? fromStartY : logicalY - this.lastDragY;
                if (button == 0) this.camera.orbit(-deltaX * 0.012D, deltaY * 0.012D);
                else this.camera.pan(deltaX, deltaY, VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
            }
            this.lastDragX = logicalX;
            this.lastDragY = logicalY;
            return true;
        }
        return super.mouseDragged(
            logicalX,
            logicalY,
            button,
            dragX / this.layoutTransform.scale(),
            dragY / this.layoutTransform.scale()
        );
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double logicalX = toLogicalX(mouseX);
        double logicalY = toLogicalY(mouseY);
        if (this.elementScrollbarDragging && button == 0) {
            this.elementScrollbarDragging = false;
            return true;
        }
        if (this.modelDragAxis != null && button == 0) {
            this.editor.finishDrag();
            this.modelDragAxis = null;
            this.modelDragTransform = null;
            this.modelDragOrigin = null;
            this.modelDragDirection = 1.0D;
            refreshNumericFields();
            return true;
        }
        if (button == 0 && releaseCreateCubeButton(logicalX, logicalY)) return true;
        if (button == 0 && releaseToolButton(logicalX, logicalY)) return true;
        if (this.cameraDragButton == button) {
            boolean dragged = this.cameraDragged;
            this.cameraDragButton = -1;
            this.cameraDragged = false;
            if (!dragged && button == 0) selectViewportAt(logicalX, logicalY);
            else if (!dragged && button == 1) {
                selectViewportForContext(logicalX, logicalY);
                openContextMenu(logicalX, logicalY);
            }
            return true;
        }
        return super.mouseReleased(logicalX, logicalY, button);
    }

    @Override
    public boolean mouseScrolled(
        double mouseX,
        double mouseY,
        double scrollX,
        double scrollY
    ) {
        double logicalX = toLogicalX(mouseX);
        double logicalY = toLogicalY(mouseY);
        if (isInsideMain(VIEWPORT_X, VIEWPORT_Y, VIEWPORT_WIDTH, VIEWPORT_HEIGHT, logicalX, logicalY)) {
            this.camera.zoom(-scrollY);
            return true;
        }
        if (isInsideMain(268, 82, 73, 48, logicalX, logicalY)) {
            int max = elementMaxScroll(elementEntryCount());
            this.elementScrollRow = Math.clamp(this.elementScrollRow - (int) Math.signum(scrollY), 0, max);
            return true;
        }
        return super.mouseScrolled(logicalX, logicalY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.renameField != null && this.renameField.isVisible() && this.renameField.isFocused()) {
            if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                commitRename();
                return true;
            }
            if (keyCode == InputConstants.KEY_ESCAPE) {
                closeRename();
                return true;
            }
            return this.renameField.keyPressed(keyCode, scanCode, modifiers);
        }
        if (focusedSearch() != null) {
            if (keyCode == InputConstants.KEY_ESCAPE) {
                focusedSearch().setFocused(false);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (focusedNumeric() != null) {
            if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                commitFocusedNumeric();
                return true;
            }
            if (keyCode == InputConstants.KEY_ESCAPE) {
                refreshNumericFields();
                focusedNumeric().box.setFocused(false);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (Screen.hasControlDown()) {
            if (Screen.hasAltDown()) {
                if (keyCode == InputConstants.KEY_X) {
                    this.editor.align(MoldingAxis.X);
                    return true;
                }
                if (keyCode == InputConstants.KEY_Y) {
                    this.editor.align(MoldingAxis.Y);
                    return true;
                }
                if (keyCode == InputConstants.KEY_Z) {
                    this.editor.align(MoldingAxis.Z);
                    return true;
                }
            }
            if (keyCode == InputConstants.KEY_C) {
                this.editor.copySelection();
                return true;
            }
            if (keyCode == InputConstants.KEY_X) {
                this.editor.cutSelection();
                return true;
            }
            if (keyCode == InputConstants.KEY_V) {
                this.editor.paste();
                return true;
            }
            if (keyCode == InputConstants.KEY_Z) {
                if (Screen.hasShiftDown()) this.editor.redo();
                else this.editor.undo();
                return true;
            }
            if (keyCode == InputConstants.KEY_Y) {
                this.editor.redo();
                return true;
            }
            if (keyCode == InputConstants.KEY_G) {
                this.editor.groupSelection();
                return true;
            }
        }
        if (handleCameraKey(keyCode)) return true;
        switch (keyCode) {
            case InputConstants.KEY_N -> {
                createCubeAtViewportOrigin();
                return true;
            }
            case InputConstants.KEY_DELETE -> {
                this.editor.deleteSelection();
                return true;
            }
            case InputConstants.KEY_H -> {
                this.editor.toggleHidden();
                return true;
            }
            case InputConstants.KEY_L -> {
                this.editor.toggleLocked();
                return true;
            }
            case InputConstants.KEY_F -> {
                focusSelection();
                return true;
            }
            case InputConstants.KEY_G -> {
                this.showGrid = !this.showGrid;
                return true;
            }
            case InputConstants.KEY_I -> {
                this.showAxes = !this.showAxes;
                return true;
            }
            case InputConstants.KEY_V -> {
                selectTool(MoldingTool.MOVE);
                return true;
            }
            case InputConstants.KEY_S -> {
                selectTool(MoldingTool.SCALE);
                return true;
            }
            case InputConstants.KEY_R -> {
                selectTool(MoldingTool.ROTATE);
                return true;
            }
            case InputConstants.KEY_P -> {
                selectTool(MoldingTool.PIVOT);
                return true;
            }
            case InputConstants.KEY_M -> {
                selectTool(MoldingTool.MIRROR);
                return true;
            }
            case InputConstants.KEY_F2 -> {
                openRename();
                return true;
            }
            default -> {
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean hasClickedOutside(
        double mouseX,
        double mouseY,
        int guiLeft,
        int guiTop,
        int mouseButton
    ) {
        int minX = this.leftOverlayOpen ? this.leftPos - 72 : this.leftPos;
        int maxX = this.rightOverlayOpen ? this.leftPos + 421 : this.leftPos + LOGICAL_WIDTH;
        return mouseX < minX
            || mouseY < this.topPos
            || mouseX >= maxX
            || mouseY >= this.topPos + LOGICAL_HEIGHT;
    }

    @Override
    public void removed() {
        if (this.removed) return;
        this.removed = true;
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new MoldingReleasePacket(
                this.menu.chamberPos(),
                this.menu.sessionId(),
                this.editor.authoritativeRevision()
            ));
        }
        this.viewportBackend.close();
        super.removed();
    }

    private void renderViewport(GuiGraphics graphics, int mouseX, int mouseY) {
        double guiScale = this.minecraft.getWindow().getGuiScale();
        int targetWidth = this.layoutTransform.framebufferPixels(VIEWPORT_WIDTH, guiScale);
        int targetHeight = this.layoutTransform.framebufferPixels(VIEWPORT_HEIGHT, guiScale);
        targetWidth *= VIEWPORT_SUPERSAMPLING;
        targetHeight *= VIEWPORT_SUPERSAMPLING;
        Direction front = chamberFront();
        ViewportTransform transform = this.camera.transform(VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
        updateScene(transform, mouseX, mouseY);
        Vec3 controllerOrigin = PlasticMoldingChamberStructure.worldAlignedControllerOrigin(front);
        this.viewportBackend.ensureTarget(targetWidth, targetHeight);
        this.viewportBackend.render(new ViewportFrame(
            this.cachedScene,
            transform,
            0xFF202326,
            this.editor.invalidPreview(),
            controllerPreviewState(),
            new Vector3d(controllerOrigin.x, controllerOrigin.y, controllerOrigin.z),
            controllerPreviewLight()
        ));
        this.viewportBackend.compose(
            graphics,
            this.leftPos + VIEWPORT_X,
            this.topPos + VIEWPORT_Y,
            VIEWPORT_WIDTH,
            VIEWPORT_HEIGHT
        );
        renderDirectionLabels(graphics, transform);
    }

    private void renderDirectionLabels(GuiGraphics graphics, ViewportTransform transform) {
        Vector2d xOffset = directionCompassOffset(transform, new Vector3d(1.0D, 0.0D, 0.0D));
        Vector2d yOffset = directionCompassOffset(transform, new Vector3d(0.0D, 1.0D, 0.0D));
        Vector2d zOffset = directionCompassOffset(transform, new Vector3d(0.0D, 0.0D, 1.0D));
        drawDirectionAxis(graphics, xOffset, 0xFFF06B6B);
        drawDirectionAxis(graphics, yOffset, 0xFF70E281);
        drawDirectionAxis(graphics, zOffset, 0xFF6F9EFF);
        drawDirectionPairLabels(graphics, xOffset, "E", "W", 0xFFF06B6B);
        drawDirectionPairLabels(graphics, yOffset, "U", "D", 0xFF70E281);
        drawDirectionPairLabels(graphics, zOffset, "S", "N", 0xFF6F9EFF);
    }

    private static Vector2d directionCompassOffset(ViewportTransform transform, Vector3d worldDirection) {
        Vector3d viewDirection = transform.directionInView(worldDirection);
        Vector2d offset = new Vector2d(viewDirection.x, viewDirection.y);
        if (offset.lengthSquared() < 1.0E-4D) {
            offset.set(0.0D, Math.copySign(1.0D, viewDirection.z));
        } else {
            offset.normalize();
        }
        return offset;
    }

    private void drawDirectionAxis(GuiGraphics graphics, Vector2d offset, int color) {
        drawCompassLine(
            graphics,
            -offset.x * DIRECTION_COMPASS_LINE_RADIUS,
            -offset.y * DIRECTION_COMPASS_LINE_RADIUS,
            offset.x * DIRECTION_COMPASS_LINE_RADIUS,
            offset.y * DIRECTION_COMPASS_LINE_RADIUS,
            color
        );
    }

    private void drawDirectionPairLabels(
        GuiGraphics graphics,
        Vector2d offset,
        String positiveLabel,
        String negativeLabel,
        int color
    ) {
        drawDirectionLabel(
            graphics,
            DIRECTION_COMPASS_X + offset.x * DIRECTION_COMPASS_LABEL_RADIUS,
            DIRECTION_COMPASS_Y + offset.y * DIRECTION_COMPASS_LABEL_RADIUS,
            positiveLabel,
            color
        );
        drawDirectionLabel(
            graphics,
            DIRECTION_COMPASS_X - offset.x * DIRECTION_COMPASS_LABEL_RADIUS,
            DIRECTION_COMPASS_Y - offset.y * DIRECTION_COMPASS_LABEL_RADIUS,
            negativeLabel,
            color
        );
    }

    private void drawCompassLine(
        GuiGraphics graphics,
        double fromX,
        double fromY,
        double toX,
        double toY,
        int color
    ) {
        double startX = this.leftPos + VIEWPORT_X + DIRECTION_COMPASS_X + fromX;
        double startY = this.topPos + VIEWPORT_Y + DIRECTION_COMPASS_Y + fromY;
        double endX = this.leftPos + VIEWPORT_X + DIRECTION_COMPASS_X + toX;
        double endY = this.topPos + VIEWPORT_Y + DIRECTION_COMPASS_Y + toY;
        drawAntialiasedGuiLine(graphics, startX, startY, endX, endY, color);
    }

    private @Nullable BlockState controllerPreviewState() {
        if (this.minecraft.level == null) return null;
        BlockState state = this.minecraft.level.getBlockState(this.menu.chamberPos());
        if (!(state.getBlock() instanceof PlasticMoldingChamberBlock)) return null;
        return state;
    }

    private Direction chamberFront() {
        BlockState state = this.minecraft.level == null
            ? null
            : this.minecraft.level.getBlockState(this.menu.chamberPos());
        return state != null && state.hasProperty(PlasticMoldingChamberBlock.FACING)
            ? state.getValue(PlasticMoldingChamberBlock.FACING)
            : Direction.NORTH;
    }

    private ViewportTransform viewportTransform() {
        return this.camera.transform(VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
    }

    private int controllerPreviewLight() {
        if (this.minecraft.level == null) return LightTexture.pack(10, 10);
        return LevelRenderer.getLightColor(this.minecraft.level, this.menu.chamberPos());
    }

    private void drawDirectionLabel(GuiGraphics graphics, double localX, double localY, String label, int color) {
        int x = this.leftPos + VIEWPORT_X
            + Math.clamp((int) Math.round(localX - this.font.width(label) * 0.5D), 2, VIEWPORT_WIDTH - 8);
        int y = this.topPos + VIEWPORT_Y
            + Math.clamp((int) Math.round(localY - this.font.lineHeight * 0.5D), 2, VIEWPORT_HEIGHT - 10);
        graphics.drawString(this.font, label, x, y, color, true);
    }

    private void drawAntialiasedGuiLine(
        GuiGraphics graphics,
        double startX,
        double startY,
        double endX,
        double endY,
        int color
    ) {
        double deltaX = endX - startX;
        double deltaY = endY - startY;
        double length = Math.hypot(deltaX, deltaY);
        if (length < 1.0E-6D) return;
        double normalX = -deltaY / length;
        double normalY = deltaX / length;
        double inner = DIRECTION_COMPASS_LINE_WIDTH * 0.5D;
        double outer = inner + DIRECTION_COMPASS_FEATHER;
        MultiBufferSource.BufferSource buffers = graphics.bufferSource();
        addGuiLineQuad(graphics, buffers, startX, startY, endX, endY, normalX, normalY, inner, color);
        int transparent = color & 0x00FFFFFF;
        addGuiLineFeather(
            graphics,
            buffers,
            startX,
            startY,
            endX,
            endY,
            normalX,
            normalY,
            inner,
            outer,
            color,
            transparent,
            false
        );
        addGuiLineFeather(
            graphics,
            buffers,
            startX,
            startY,
            endX,
            endY,
            normalX,
            normalY,
            inner,
            outer,
            color,
            transparent,
            true
        );
        graphics.flush();
    }

    private static void addGuiLineQuad(
        GuiGraphics graphics,
        MultiBufferSource.BufferSource buffers,
        double startX,
        double startY,
        double endX,
        double endY,
        double normalX,
        double normalY,
        double width,
        int color
    ) {
        addGuiQuad(
            graphics,
            buffers,
            startX - normalX * width,
            startY - normalY * width,
            endX - normalX * width,
            endY - normalY * width,
            endX + normalX * width,
            endY + normalY * width,
            startX + normalX * width,
            startY + normalY * width,
            color,
            color
        );
    }

    private static void addGuiLineFeather(
        GuiGraphics graphics,
        MultiBufferSource.BufferSource buffers,
        double startX,
        double startY,
        double endX,
        double endY,
        double normalX,
        double normalY,
        double inner,
        double outer,
        int color,
        int transparent,
        boolean positive
    ) {
        double sign = positive ? 1.0D : -1.0D;
        if (!positive) {
            addGuiQuad(
                graphics,
                buffers,
                startX + normalX * outer * sign,
                startY + normalY * outer * sign,
                endX + normalX * outer * sign,
                endY + normalY * outer * sign,
                endX + normalX * inner * sign,
                endY + normalY * inner * sign,
                startX + normalX * inner * sign,
                startY + normalY * inner * sign,
                transparent,
                color
            );
            return;
        }
        addGuiQuad(
            graphics,
            buffers,
            startX + normalX * inner * sign,
            startY + normalY * inner * sign,
            endX + normalX * inner * sign,
            endY + normalY * inner * sign,
            endX + normalX * outer * sign,
            endY + normalY * outer * sign,
            startX + normalX * outer * sign,
            startY + normalY * outer * sign,
            color,
            transparent
        );
    }

    private static void addGuiQuad(
        GuiGraphics graphics,
        MultiBufferSource.BufferSource buffers,
        double firstX,
        double firstY,
        double secondX,
        double secondY,
        double thirdX,
        double thirdY,
        double fourthX,
        double fourthY,
        int innerColor,
        int outerColor
    ) {
        Vector3f first = graphics.pose().last().pose().transformPosition((float) firstX, (float) firstY, 0.0F, new Vector3f());
        Vector3f second = graphics.pose().last().pose().transformPosition((float) secondX, (float) secondY, 0.0F, new Vector3f());
        Vector3f third = graphics.pose().last().pose().transformPosition((float) thirdX, (float) thirdY, 0.0F, new Vector3f());
        Vector3f fourth = graphics.pose().last().pose().transformPosition((float) fourthX, (float) fourthY, 0.0F, new Vector3f());
        VertexConsumer consumer = buffers.getBuffer(RenderType.guiOverlay());
        consumer.addVertex(fourth.x, fourth.y, fourth.z).setColor(outerColor);
        consumer.addVertex(third.x, third.y, third.z).setColor(outerColor);
        consumer.addVertex(second.x, second.y, second.z).setColor(innerColor);
        consumer.addVertex(first.x, first.y, first.z).setColor(innerColor);
    }

    private List<Component> titleStatusTooltip() {
        Component writer = this.menu.writerName().isEmpty()
            ? Component.translatable("screen.anvilcraftplasticraft.molding.no_writer")
            : Component.translatable("screen.anvilcraftplasticraft.molding.writer", this.menu.writerName());
        List<Component> lines = new ArrayList<>();
        lines.add(writer);
        if (this.cachedBaked != null) {
            lines.add(Component.translatable(
                "screen.anvilcraftplasticraft.molding.analysis",
                this.cachedBaked.analysis().volume(),
                this.cachedBaked.analysis().minimumMeltMillibuckets(),
                this.cachedBaked.analysis().clayBallRequirement()
            ));
        }
        if (this.editor.invalidPreview()) {
            lines.add(Component.translatable("screen.anvilcraftplasticraft.molding.out_of_bounds")
                .withStyle(ChatFormatting.RED));
        }
        return List.copyOf(lines);
    }

    private void renderToolButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int index = 0; index < TOOL_BUTTONS.length; index++) {
            MoldingTool tool = TOOL_BUTTONS[index];
            GuiRect rect = new GuiRect(9, 19 + index * 18, 16, 16);
            boolean hovered = rect.contains(this.leftPos, this.topPos, mouseX, mouseY);
            drawAtlas(
                graphics,
                rect,
                toolTexture(tool),
                toolFrames(tool),
                this.toolButtons.frame(tool, hovered)
            );
            tooltip(rect, mouseX, mouseY, "screen.anvilcraftplasticraft.molding.tool." + TOOL_NAMES[index]);
        }
    }

    private void renderElementList(GuiGraphics graphics, int mouseX, int mouseY) {
        List<SceneObject> objects = sceneObjects();
        int entryCount = objects.size() + 1;
        clampElementScrollRow(entryCount);
        int first = this.elementScrollRow * 2;
        for (int index = 0; index < 4; index++) {
            int objectIndex = first + index;
            GuiRect rect = new GuiRect(268 + index % 2 * 32, 82 + index / 2 * 23, 32, 23);
            if (objectIndex >= entryCount) continue;
            if (objectIndex == objects.size()) {
                renderCreateCubeButton(graphics, rect, mouseX, mouseY);
                continue;
            }
            SceneObject object = objects.get(objectIndex);
            boolean hovered = rect.contains(this.leftPos, this.topPos, mouseX, mouseY);
            boolean selected = this.editor.selection().contains(object.id());
            int frame = object.element() ? selected ? 2 : hovered ? 1 : 0 : selected ? 1 : 0;
            drawAtlas(
                graphics,
                rect,
                object.element() ? CUBE_BUTTON : BUTTON_32_23_2,
                object.element() ? 3 : 2,
                frame
            );
            String name = this.font.plainSubstrByWidth(object.name(), 28);
            graphics.drawCenteredString(
                this.font,
                name,
                this.leftPos + rect.x + rect.width / 2,
                this.topPos + rect.y + (object.element() ? 7 : 3),
                object.element() ? 0xFFFFFFFF : 0xFFE8E8E8
            );
            if (!object.element()) {
                graphics.drawCenteredString(
                    this.font,
                    object.kind(),
                    this.leftPos + rect.x + rect.width / 2,
                    this.topPos + rect.y + 13,
                    0xFFB6B6B6
                );
            }
            if (hovered) {
                this.hoveredControlTooltip = object.element()
                    ? List.of(Component.literal(object.name()))
                    : List.of(Component.translatable(
                        "screen.anvilcraftplasticraft.molding.element.tooltip",
                        object.name(),
                        Component.translatable(object.typeKey())
                    ));
            }
        }
        int handleX = elementScrollbarHandleX();
        int handleY = elementScrollbarHandleY(entryCount);
        boolean handleHovered = new GuiRect(
            handleX,
            handleY,
            ELEMENT_SCROLL_HANDLE_WIDTH,
            ELEMENT_SCROLL_HANDLE_HEIGHT
        ).contains(this.leftPos, this.topPos, mouseX, mouseY);
        graphics.blit(
            ELEMENT_SCROLL_HANDLE,
            this.leftPos + handleX,
            this.topPos + handleY,
            handleHovered ? ELEMENT_SCROLL_HANDLE_WIDTH : 0,
            0,
            ELEMENT_SCROLL_HANDLE_WIDTH,
            ELEMENT_SCROLL_HANDLE_HEIGHT,
            ELEMENT_SCROLL_HANDLE_TEXTURE_WIDTH,
            ELEMENT_SCROLL_HANDLE_HEIGHT
        );
    }

    private void renderCreateCubeButton(GuiGraphics graphics, GuiRect rect, int mouseX, int mouseY) {
        boolean enabled = this.editor.writable() && !this.editor.pending();
        boolean hovered = rect.contains(this.leftPos, this.topPos, mouseX, mouseY);
        int frame = this.newCubeButtonPressed ? 2 : hovered ? 1 : 0;
        drawAtlas(graphics, rect, NEW_CUBE_BUTTON, 3, frame);
        if (!enabled) drawDisabledOverlay(graphics, rect);
        tooltip(rect, mouseX, mouseY, "screen.anvilcraftplasticraft.molding.context.new_cube");
    }

    private void renderMainControls(GuiGraphics graphics, int mouseX, int mouseY) {
        GuiRect type = new GuiRect(9, 114, 16, 16);
        drawThreeFrame(graphics, type, this.leftOverlayOpen, true, mouseX, mouseY);
        tooltip(type, mouseX, mouseY, "screen.anvilcraftplasticraft.molding.type");

        GuiRect undo = new GuiRect(269, 136, 16, 16);
        GuiRect copy = new GuiRect(287, 136, 16, 16);
        GuiRect cut = new GuiRect(305, 136, 16, 16);
        GuiRect paste = new GuiRect(323, 136, 16, 16);
        drawThreeFrame(graphics, undo, false, this.editor.writable() && !this.editor.pending(), mouseX, mouseY);
        drawThreeFrame(graphics, copy, false, !this.editor.selection().isEmpty(), mouseX, mouseY);
        drawThreeFrame(graphics, cut, false, this.editor.writable() && !this.editor.pending(), mouseX, mouseY);
        drawThreeFrame(graphics, paste, false, this.editor.writable() && !this.editor.pending(), mouseX, mouseY);
        tooltip(undo, mouseX, mouseY, "screen.anvilcraftplasticraft.molding.undo");
        tooltip(copy, mouseX, mouseY, "screen.anvilcraftplasticraft.molding.copy");
        tooltip(cut, mouseX, mouseY, "screen.anvilcraftplasticraft.molding.cut");
        tooltip(paste, mouseX, mouseY, "screen.anvilcraftplasticraft.molding.paste");

        drawDisabledAtlas(graphics, new GuiRect(47, 143, 34, 16), BUTTON_34_16_4, 4);
        drawDisabledAtlas(graphics, new GuiRect(47, 162, 10, 10), BUTTON_10_3, 3);
        drawDisabledAtlas(graphics, new GuiRect(59, 162, 10, 10), BUTTON_10_3, 3);
        drawDisabledAtlas(graphics, new GuiRect(71, 162, 10, 10), BUTTON_10_3, 3);
        drawDisabledAtlas(graphics, new GuiRect(265, 162, 16, 16), BUTTON_16_3, 3);
        drawDisabledAtlas(graphics, new GuiRect(265, 180, 16, 16), BUTTON_16_3, 3);

        GuiRect json = new GuiRect(324, 171, 16, 16);
        drawThreeFrame(graphics, json, this.rightOverlayOpen, true, mouseX, mouseY);
        tooltip(json, mouseX, mouseY, "screen.anvilcraftplasticraft.molding.json");
    }

    private void renderOverlays(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.leftOverlayOpen) {
            GuiRect normal = new GuiRect(-68, 90, 52, 15);
            drawAtlas(graphics, normal, BUTTON_52_15_2, 2, 1);
            graphics.drawString(
                this.font,
                Component.translatable("screen.anvilcraftplasticraft.molding.type.normal"),
                this.leftPos - 66,
                this.topPos + 94,
                0xFFF0F0F0,
                false
            );
            tooltip(normal, mouseX, mouseY, "screen.anvilcraftplasticraft.molding.type.normal");
        }
        if (this.rightOverlayOpen) {
            for (int row = 0; row < 6; row++) {
                GuiRect entry = new GuiRect(357, 90 + row * 15, 52, 15);
                drawAtlas(graphics, entry, BUTTON_52_15_2, 2, 0);
                graphics.fill(
                    this.leftPos + entry.x,
                    this.topPos + entry.y,
                    this.leftPos + entry.x + entry.width,
                    this.topPos + entry.y + entry.height,
                    0x50000000
                );
            }
            drawDisabledAtlas(graphics, new GuiRect(358, 184, 13, 13), BUTTON_13_3, 3);
            drawDisabledAtlas(graphics, new GuiRect(373, 184, 13, 13), BUTTON_13_3, 3);
            drawDisabledAtlas(graphics, new GuiRect(388, 184, 13, 13), BUTTON_13_3, 3);
            drawDisabledAtlas(graphics, new GuiRect(403, 184, 13, 13), BUTTON_13_4, 4);
        }
    }

    private void renderContextMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ContextAction> actions = contextActions();
        int height = actions.size() * CONTEXT_ROW_HEIGHT + 2;
        int x = this.leftPos + this.contextMenuX;
        int y = this.topPos + this.contextMenuY;
        graphics.fill(x, y, x + CONTEXT_WIDTH, y + height, 0xEE17191C);
        graphics.renderOutline(x, y, CONTEXT_WIDTH, height, 0xFF8F7AA8);
        for (int index = 0; index < actions.size(); index++) {
            GuiRect rect = new GuiRect(
                this.contextMenuX + 1,
                this.contextMenuY + 1 + index * CONTEXT_ROW_HEIGHT,
                CONTEXT_WIDTH - 2,
                CONTEXT_ROW_HEIGHT
            );
            if (rect.contains(this.leftPos, this.topPos, mouseX, mouseY)) {
                graphics.fill(
                    this.leftPos + rect.x,
                    this.topPos + rect.y,
                    this.leftPos + rect.x + rect.width,
                    this.topPos + rect.y + rect.height,
                    0xFF4C315F
                );
            }
            graphics.drawString(
                this.font,
                Component.translatable(actions.get(index).translationKey),
                this.leftPos + rect.x + 3,
                this.topPos + rect.y + 2,
                actions.get(index).enabled.getAsBoolean() ? 0xFFE8E8E8 : 0xFF777777,
                false
            );
        }
    }

    private void updateScene(
        ViewportTransform transform,
        double mouseX,
        double mouseY
    ) {
        ViewportHover hover = viewportHover(transform, mouseX, mouseY);
        Vector3d gizmoOrigin = this.editor.gizmoOrigin();
        double gizmoWorldUnitsPerPixel = transform.worldUnitsPerPixel(gizmoOrigin);
        Vector3d gizmoCameraDirection = transform.directionToCamera(gizmoOrigin);
        boolean hasSelection = !this.editor.selection().isEmpty();
        boolean sceneViewChanged = Math.abs(gizmoWorldUnitsPerPixel - this.cachedGizmoWorldUnitsPerPixel) > 1.0E-9D
            || gizmoCameraDirection.distanceSquared(this.cachedGizmoCameraDirection) > 1.0E-12D;
        boolean gizmoViewChanged = hasSelection && sceneViewChanged;
        boolean rebuildScene = this.cachedScene == null
            || this.cachedSceneModel != this.editor.model()
            || !this.cachedSelection.equals(this.editor.selection())
            || this.cachedInvalid != this.editor.invalidPreview()
            || this.cachedShowGrid != this.showGrid
            || this.cachedShowAxes != this.showAxes
            || !Objects.equals(this.cachedHoveredElement, hover.elementId())
            || sceneViewChanged;
        boolean rebuildGizmo = hasSelection
            && (rebuildScene
            || this.cachedTool != this.editor.tool()
            || gizmoViewChanged
            || this.cachedHoveredGizmoAxis != hover.gizmoAxis());
        if (!rebuildScene && !rebuildGizmo) return;
        if (rebuildScene) {
            BakedMoldingModel baked = null;
            try {
                baked = MoldingModelBaker.bake(this.editor.model());
            } catch (IllegalArgumentException ignored) {
            }
            this.cachedBaked = baked;
            this.cachedScene = MoldingSceneBuilder.buildEditor(
                this.editor.authoritativeRevision(),
                ++this.dynamicSceneRevision,
                this.editor.model(),
                baked,
                this.editor.selection(),
                this.editor.tool(),
                gizmoOrigin,
                gizmoWorldUnitsPerPixel,
                gizmoCameraDirection,
                this.editor.invalidPreview(),
                this.showGrid,
                this.showAxes,
                hover.elementId(),
                hover.gizmoAxis(),
                transform
            );
        } else {
            this.cachedScene = MoldingSceneBuilder.replaceGizmo(
                this.cachedScene,
                ++this.dynamicSceneRevision,
                gizmoOrigin,
                this.editor.tool(),
                gizmoWorldUnitsPerPixel,
                gizmoCameraDirection,
                hover.gizmoAxis(),
                transform
            );
        }
        this.cachedSceneModel = this.editor.model();
        this.cachedSelection = this.editor.selection();
        this.cachedTool = this.editor.tool();
        this.cachedInvalid = this.editor.invalidPreview();
        this.cachedShowGrid = this.showGrid;
        this.cachedShowAxes = this.showAxes;
        this.cachedGizmoWorldUnitsPerPixel = gizmoWorldUnitsPerPixel;
        this.cachedGizmoCameraDirection.set(gizmoCameraDirection);
        this.cachedHoveredElement = hover.elementId();
        this.cachedHoveredGizmoAxis = hover.gizmoAxis();
    }

    private ViewportHover viewportHover(ViewportTransform transform, double mouseX, double mouseY) {
        if (!isInsideMain(VIEWPORT_X, VIEWPORT_Y, VIEWPORT_WIDTH, VIEWPORT_HEIGHT, mouseX, mouseY)) {
            return ViewportHover.NONE;
        }
        ViewportRay ray = transform.ray(
            mouseX - this.leftPos - VIEWPORT_X,
            mouseY - this.topPos - VIEWPORT_Y
        );
        if (!this.editor.selection().isEmpty()) {
            Vector3d gizmoOrigin = this.editor.gizmoOrigin();
            Optional<MoldingHitTester.GizmoHit> gizmo = MoldingHitTester.hitGizmo(
                ray,
                gizmoOrigin,
                this.editor.tool(),
                transform.worldUnitsPerPixel(gizmoOrigin),
                transform.directionToCamera(gizmoOrigin)
            );
            if (gizmo.isPresent()) return new ViewportHover(null, gizmo.get().axis());
        }
        return MoldingHitTester.hitElement(this.editor.model(), ray)
            .map(hit -> new ViewportHover(hit.id(), null))
            .orElse(ViewportHover.NONE);
    }

    private boolean handleViewportClick(double mouseX, double mouseY, int button) {
        this.contextMenuOpen = false;
        if (button == 1 || button == 2) {
            startCameraDrag(mouseX, mouseY, button);
            return true;
        }
        if (button != 0) return false;
        ViewportTransform transform = viewportTransform();
        ViewportRay ray = transform.ray(
            mouseX - this.leftPos - VIEWPORT_X,
            mouseY - this.topPos - VIEWPORT_Y
        );
        if (!this.editor.selection().isEmpty()) {
            Vector3d gizmoOrigin = this.editor.gizmoOrigin();
            Optional<MoldingHitTester.GizmoHit> gizmo = MoldingHitTester.hitGizmo(
                ray,
                gizmoOrigin,
                this.editor.tool(),
                transform.worldUnitsPerPixel(gizmoOrigin),
                transform.directionToCamera(gizmoOrigin)
            );
            if (gizmo.isPresent()) {
                if (this.editor.tool() == MoldingTool.MIRROR) {
                    this.toolButtons.selectMirrorAxis(gizmo.get().axis());
                    if (this.editor.mirror(gizmo.get().axis())) return true;
                } else if (this.editor.beginDrag(gizmo.get().axis(), gizmo.get().direction())) {
                    this.modelDragAxis = gizmo.get().axis();
                    this.modelDragDirection = gizmo.get().direction();
                    this.modelDragStartX = mouseX - this.leftPos - VIEWPORT_X;
                    this.modelDragStartY = mouseY - this.topPos - VIEWPORT_Y;
                    this.modelDragTransform = transform;
                    this.modelDragOrigin = gizmoOrigin;
                    return true;
                }
            }
        }
        startCameraDrag(mouseX, mouseY, button);
        return true;
    }

    private void startCameraDrag(double mouseX, double mouseY, int button) {
        this.cameraDragButton = button;
        this.cameraDragged = false;
        this.cameraDragStartX = mouseX;
        this.cameraDragStartY = mouseY;
        this.lastDragX = mouseX;
        this.lastDragY = mouseY;
    }

    private void selectViewportAt(double mouseX, double mouseY) {
        ViewportTransform transform = viewportTransform();
        ViewportRay ray = transform.ray(
            mouseX - this.leftPos - VIEWPORT_X,
            mouseY - this.topPos - VIEWPORT_Y
        );
        Optional<MoldingHitTester.ElementHit> hit = MoldingHitTester.hitElement(this.editor.model(), ray);
        if (hit.isPresent()) {
            this.editor.select(hit.get().id(), Screen.hasShiftDown());
            ensurePrimaryVisible();
        } else if (!Screen.hasShiftDown()) {
            this.editor.clearSelection();
        }
        refreshNumericFields();
    }

    private void selectViewportForContext(double mouseX, double mouseY) {
        ViewportTransform transform = viewportTransform();
        ViewportRay ray = transform.ray(
            mouseX - this.leftPos - VIEWPORT_X,
            mouseY - this.topPos - VIEWPORT_Y
        );
        MoldingHitTester.hitElement(this.editor.model(), ray).ifPresent(hit -> {
            if (this.editor.selection().contains(hit.id())) return;
            this.editor.select(hit.id(), false);
            ensurePrimaryVisible();
            refreshNumericFields();
        });
    }

    private void updateModelDrag(double mouseX, double mouseY) {
        if (this.modelDragAxis == null || this.modelDragTransform == null || this.modelDragOrigin == null) return;
        double currentX = mouseX - this.leftPos - VIEWPORT_X;
        double currentY = mouseY - this.topPos - VIEWPORT_Y;
        double deltaX = currentX - this.modelDragStartX;
        double deltaY = currentY - this.modelDragStartY;
        double amount;
        if (this.editor.tool() == MoldingTool.ROTATE) {
            amount = (deltaX - deltaY) * 0.75D;
        } else {
            Vector3d center = this.modelDragOrigin;
            double referenceLength = MoldingGizmoGeometry.axisLength(
                this.modelDragTransform.worldUnitsPerPixel(center)
            );
            Optional<Vector2d> start = this.modelDragTransform.project(center);
            Optional<Vector2d> end = this.modelDragTransform.project(
                new Vector3d(this.modelDragAxis.vector())
                    .mul(referenceLength * this.modelDragDirection)
                    .add(center)
            );
            if (start.isEmpty() || end.isEmpty()) return;
            Vector2d screenAxis = new Vector2d(end.get()).sub(start.get());
            double length = screenAxis.length();
            if (length < 1.0E-5D) return;
            screenAxis.div(length);
            amount = (deltaX * screenAxis.x + deltaY * screenAxis.y) * referenceLength / length;
        }
        this.editor.updateDrag(amount);
        refreshNumericFields();
    }

    private boolean handleFixedControlClick(double mouseX, double mouseY, int button) {
        if (handleElementListClick(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        for (int index = 0; index < TOOL_BUTTONS.length; index++) {
            GuiRect rect = new GuiRect(9, 19 + index * 18, 16, 16);
            if (!rect.contains(this.leftPos, this.topPos, mouseX, mouseY)) continue;
            return this.toolButtons.press(TOOL_BUTTONS[index]);
        }
        if (new GuiRect(9, 114, 16, 16).contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            this.leftOverlayOpen = !this.leftOverlayOpen;
            updateLayout();
            return true;
        }
        if (new GuiRect(324, 171, 16, 16).contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            this.rightOverlayOpen = !this.rightOverlayOpen;
            updateLayout();
            return true;
        }
        if (new GuiRect(269, 136, 16, 16).contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            this.editor.undo();
            return true;
        }
        if (new GuiRect(287, 136, 16, 16).contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            this.editor.copySelection();
            return true;
        }
        if (new GuiRect(305, 136, 16, 16).contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            this.editor.cutSelection();
            return true;
        }
        if (new GuiRect(323, 136, 16, 16).contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            this.editor.paste();
            return true;
        }
        return false;
    }

    private boolean releaseToolButton(double mouseX, double mouseY) {
        for (int index = 0; index < TOOL_BUTTONS.length; index++) {
            MoldingTool tool = TOOL_BUTTONS[index];
            GuiRect rect = new GuiRect(9, 19 + index * 18, 16, 16);
            MoldingToolButtonState.ReleaseAction action = this.toolButtons.release(
                tool,
                rect.contains(this.leftPos, this.topPos, mouseX, mouseY)
            );
            if (action == MoldingToolButtonState.ReleaseAction.NONE) continue;
            if (action == MoldingToolButtonState.ReleaseAction.CENTER_PIVOT) {
                this.editor.centerPivot();
            } else {
                this.editor.setTool(this.toolButtons.selectedTool());
            }
            refreshNumericFields();
            return true;
        }
        return false;
    }

    private void selectTool(MoldingTool tool) {
        this.toolButtons.select(tool);
        this.editor.setTool(tool);
    }

    private static ResourceLocation toolTexture(MoldingTool tool) {
        return switch (tool) {
            case MOVE -> MOVE_TOOL;
            case SCALE -> SCALE_TOOL;
            case ROTATE -> ROTATE_TOOL;
            case PIVOT -> PIVOT_TOOL;
            case MIRROR -> MIRROR_TOOL;
            case NONE -> throw new IllegalArgumentException("No tool does not have a button texture");
        };
    }

    private static int toolFrames(MoldingTool tool) {
        return switch (tool) {
            case MOVE, SCALE, ROTATE -> 5;
            case PIVOT -> 6;
            case MIRROR -> 12;
            case NONE -> throw new IllegalArgumentException("No tool does not have button frames");
        };
    }

    private boolean handleElementListClick(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) return false;
        List<SceneObject> objects = sceneObjects();
        clampElementScrollRow(objects.size() + 1);
        int first = this.elementScrollRow * 2;
        for (int index = 0; index < 4; index++) {
            GuiRect rect = new GuiRect(268 + index % 2 * 32, 82 + index / 2 * 23, 32, 23);
            if (!rect.contains(this.leftPos, this.topPos, mouseX, mouseY)) continue;
            int objectIndex = first + index;
            if (objectIndex > objects.size()) return false;
            this.contextMenuOpen = false;
            if (objectIndex == objects.size()) {
                if (button == 0 && this.editor.writable() && !this.editor.pending()) {
                    this.newCubeButtonPressed = true;
                }
                return true;
            }
            SceneObject object = objects.get(objectIndex);
            if (button == 1) {
                if (!object.element()) return false;
                this.editor.select(object.id(), false);
                ensurePrimaryVisible();
                refreshNumericFields();
                openContextMenu(mouseX, mouseY);
                return true;
            }
            this.editor.select(object.id(), Screen.hasShiftDown());
            refreshNumericFields();
            return true;
        }
        return false;
    }

    private boolean releaseCreateCubeButton(double mouseX, double mouseY) {
        if (!this.newCubeButtonPressed) return false;
        this.newCubeButtonPressed = false;
        GuiRect rect = visibleCreateCubeButtonRect();
        if (rect == null
            || !rect.contains(this.leftPos, this.topPos, mouseX, mouseY)
            || !this.editor.writable()
            || this.editor.pending()) {
            return true;
        }
        createCubeAtViewportOrigin();
        return true;
    }

    @Nullable
    private GuiRect visibleCreateCubeButtonRect() {
        List<SceneObject> objects = sceneObjects();
        clampElementScrollRow(objects.size() + 1);
        int visibleIndex = objects.size() - this.elementScrollRow * 2;
        if (visibleIndex < 0 || visibleIndex >= 4) return null;
        return new GuiRect(
            268 + visibleIndex % 2 * 32,
            82 + visibleIndex / 2 * 23,
            32,
            23
        );
    }

    private boolean handleElementScrollbarClick(double mouseX, double mouseY, int button) {
        GuiRect scrollbar = new GuiRect(
            ELEMENT_SCROLLBAR_X,
            ELEMENT_SCROLLBAR_Y,
            ELEMENT_SCROLLBAR_WIDTH,
            ELEMENT_SCROLLBAR_HEIGHT
        );
        if (button != 0 || !scrollbar.contains(this.leftPos, this.topPos, mouseX, mouseY)) return false;
        int objectCount = elementEntryCount();
        clampElementScrollRow(objectCount);
        int maxScroll = elementMaxScroll(objectCount);
        if (maxScroll == 0) return true;
        int handleY = elementScrollbarHandleY(objectCount);
        double localY = mouseY - this.topPos;
        GuiRect handle = new GuiRect(
            elementScrollbarHandleX(),
            handleY,
            ELEMENT_SCROLL_HANDLE_WIDTH,
            ELEMENT_SCROLL_HANDLE_HEIGHT
        );
        if (handle.contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            this.elementScrollbarGrabOffset = localY - handleY;
        } else {
            this.elementScrollbarGrabOffset = ELEMENT_SCROLL_HANDLE_HEIGHT * 0.5D;
            updateElementScrollFromPointer(mouseY, objectCount);
        }
        this.elementScrollbarDragging = true;
        return true;
    }

    private void updateElementScrollFromPointer(double mouseY, int objectCount) {
        int maxScroll = elementMaxScroll(objectCount);
        if (maxScroll == 0) {
            this.elementScrollRow = 0;
            return;
        }
        int travel = ELEMENT_SCROLL_TRACK_HEIGHT - ELEMENT_SCROLL_HANDLE_HEIGHT;
        double offset = mouseY - this.topPos - this.elementScrollbarGrabOffset - ELEMENT_SCROLL_TRACK_Y;
        this.elementScrollRow = Math.clamp((int) Math.round(offset * maxScroll / travel), 0, maxScroll);
    }

    private void clampElementScrollRow(int objectCount) {
        this.elementScrollRow = Math.clamp(this.elementScrollRow, 0, elementMaxScroll(objectCount));
    }

    private int elementScrollbarHandleY(int objectCount) {
        int maxScroll = elementMaxScroll(objectCount);
        if (maxScroll == 0) return ELEMENT_SCROLL_TRACK_Y;
        int travel = ELEMENT_SCROLL_TRACK_HEIGHT - ELEMENT_SCROLL_HANDLE_HEIGHT;
        return ELEMENT_SCROLL_TRACK_Y + travel * this.elementScrollRow / maxScroll;
    }

    private static int elementScrollbarHandleX() {
        return ELEMENT_SCROLLBAR_X + (ELEMENT_SCROLLBAR_WIDTH - ELEMENT_SCROLL_HANDLE_WIDTH) / 2;
    }

    private static int elementMaxScroll(int objectCount) {
        return Math.max(0, elementRowCount(objectCount) - ELEMENT_VISIBLE_ROWS);
    }

    private static int elementRowCount(int objectCount) {
        return Math.max(1, (objectCount + 1) / 2);
    }

    private boolean handleContextClick(double mouseX, double mouseY, int button) {
        if (!this.contextMenuOpen || button != 0) return false;
        List<ContextAction> actions = contextActions();
        int localX = (int) mouseX - this.leftPos;
        int localY = (int) mouseY - this.topPos;
        if (localX < this.contextMenuX || localX >= this.contextMenuX + CONTEXT_WIDTH
            || localY < this.contextMenuY + 1
            || localY >= this.contextMenuY + 1 + actions.size() * CONTEXT_ROW_HEIGHT) {
            this.contextMenuOpen = false;
            return false;
        }
        int row = (localY - this.contextMenuY - 1) / CONTEXT_ROW_HEIGHT;
        ContextAction action = actions.get(row);
        if (action.enabled.getAsBoolean()) executeContextAction(action.kind);
        if (action.kind != ContextActionKind.RENAME) this.contextMenuOpen = false;
        return true;
    }

    private void executeContextAction(ContextActionKind action) {
        switch (action) {
            case TAKEOVER -> PacketDistributor.sendToServer(new MoldingTakeoverPacket(
                this.menu.chamberPos(), this.menu.sessionId(), this.editor.authoritativeRevision()
            ));
            case CREATE_CUBE -> createCubeAtViewportOrigin();
            case RENAME -> openRename();
            case DELETE -> this.editor.deleteSelection();
            case HIDE -> this.editor.toggleHidden();
            case LOCK -> this.editor.toggleLocked();
            case GROUP -> this.editor.groupSelection();
            case CENTER_PIVOT -> this.editor.centerPivot();
            case FOCUS -> focusSelection();
            case RESET_CAMERA -> this.camera.reset();
        }
    }

    private List<ContextAction> contextActions() {
        List<ContextAction> actions = new ArrayList<>();
        if (!this.editor.writable()) {
            actions.add(new ContextAction(
                ContextActionKind.TAKEOVER,
                "screen.anvilcraftplasticraft.molding.takeover",
                () -> true
            ));
        } else {
            actions.add(context(ContextActionKind.CREATE_CUBE, "new_cube", () -> !this.editor.pending()));
            actions.add(context(ContextActionKind.RENAME, "rename", () -> !this.editor.selection().isEmpty()));
            actions.add(context(ContextActionKind.DELETE, "delete", () -> !this.editor.selection().isEmpty()));
            actions.add(context(ContextActionKind.HIDE, "hide", () -> !this.editor.selection().isEmpty()));
            actions.add(context(ContextActionKind.LOCK, "lock", () -> !this.editor.selection().isEmpty()));
            actions.add(context(ContextActionKind.GROUP, "group", () -> !this.editor.selection().isEmpty()));
            actions.add(context(ContextActionKind.CENTER_PIVOT, "center_pivot", () -> !this.editor.selection().isEmpty()));
        }
        actions.add(context(ContextActionKind.FOCUS, "focus", () -> !this.editor.selection().isEmpty()));
        actions.add(context(ContextActionKind.RESET_CAMERA, "reset_camera", () -> true));
        return actions;
    }

    private ContextAction context(ContextActionKind kind, String name, BooleanSupplier enabled) {
        return new ContextAction(kind, "screen.anvilcraftplasticraft.molding.context." + name, enabled);
    }

    private void openContextMenu(double mouseX, double mouseY) {
        int localX = (int) Math.floor(mouseX - this.leftPos);
        int localY = (int) Math.floor(mouseY - this.topPos);
        int height = contextActions().size() * CONTEXT_ROW_HEIGHT + 2;
        this.contextMenuX = Math.clamp(localX, VIEWPORT_X, VIEWPORT_X + VIEWPORT_WIDTH - CONTEXT_WIDTH);
        this.contextMenuY = Math.clamp(localY, VIEWPORT_Y, VIEWPORT_Y + VIEWPORT_HEIGHT - height);
        this.contextMenuOpen = true;
    }

    private boolean handleCameraKey(int keyCode) {
        MoldingViewPreset preset = switch (keyCode) {
            case InputConstants.KEY_NUMPAD1 -> Screen.hasControlDown() ? MoldingViewPreset.BACK : MoldingViewPreset.FRONT;
            case InputConstants.KEY_NUMPAD3 -> Screen.hasControlDown() ? MoldingViewPreset.LEFT : MoldingViewPreset.RIGHT;
            case InputConstants.KEY_NUMPAD7 -> Screen.hasControlDown() ? MoldingViewPreset.BOTTOM : MoldingViewPreset.TOP;
            case InputConstants.KEY_NUMPAD5 -> MoldingViewPreset.PERSPECTIVE;
            default -> null;
        };
        if (preset == null) return false;
        this.camera.setPreset(preset);
        return true;
    }

    private void focusSelection() {
        if (!this.editor.selection().isEmpty()) {
            this.camera.focus(this.editor.selectionCenter(), this.editor.selectionExtent());
        } else {
            this.camera.reset();
        }
    }

    private boolean createCubeAtViewportOrigin() {
        if (!this.editor.createCube()) return false;
        ensurePrimaryVisible();
        refreshNumericFields();
        return true;
    }

    private void createNumericFields() {
        this.numericFields.clear();
        MoldingNumericProperty[] properties = MoldingNumericProperty.values();
        MoldingAxis[] axes = MoldingAxis.values();
        for (int propertyIndex = 0; propertyIndex < properties.length; propertyIndex++) {
            for (int axisIndex = 0; axisIndex < axes.length; axisIndex++) {
                EditBox box = new CenteredEditBox(
                    this.font,
                    24,
                    13,
                    Component.translatable("screen.anvilcraftplasticraft.molding.numeric")
                );
                box.setMaxLength(12);
                box.setBordered(false);
                box.setFilter(PlasticMoldingChamberScreen::isNumericText);
                NumericField field = new NumericField(properties[propertyIndex], axes[axisIndex], box);
                this.numericFields.add(field);
                this.addRenderableWidget(box);
            }
        }
    }

    private void updateNumericFieldTooltip(double mouseX, double mouseY) {
        for (NumericField field : this.numericFields) {
            if (!field.box.visible || !field.box.isMouseOver(mouseX, mouseY)) continue;
            Component property = Component.translatable(
                "screen.anvilcraftplasticraft.molding.numeric."
                    + field.property.name().toLowerCase(Locale.ROOT)
            );
            this.hoveredControlTooltip = List.of(Component.translatable(
                "screen.anvilcraftplasticraft.molding.numeric.tooltip",
                property,
                field.axis.name().toLowerCase(Locale.ROOT)
            ));
            return;
        }
    }

    private EditBox createSearchBox(String hintKey) {
        EditBox box = new VerticallyOffsetEditBox(this.font, 60, 11, Component.translatable(hintKey));
        box.setMaxLength(32);
        box.setBordered(false);
        box.setHint(Component.translatable(hintKey));
        return box;
    }

    private void updateLayout() {
        int minX = this.leftOverlayOpen ? -72 : 0;
        int maxX = this.rightOverlayOpen ? 421 : LOGICAL_WIDTH;
        int visibleWidth = maxX - minX;
        this.layoutTransform = MoldingGuiTransform.fit(
            this.width,
            this.height,
            visibleWidth,
            LOGICAL_HEIGHT,
            4
        );
        this.leftPos = -minX;
        this.topPos = 0;
        for (int index = 0; index < this.numericFields.size(); index++) {
            NumericField field = this.numericFields.get(index);
            int property = index / 3;
            int axis = index % 3;
            field.box.setX(this.leftPos + 270 + axis * 24);
            field.box.setY(this.topPos + 23 + property * 14);
        }
        if (this.leftSearch != null) {
            this.leftSearch.setX(this.leftPos - 68);
            this.leftSearch.setY(this.topPos + 76);
            this.leftSearch.setVisible(this.leftOverlayOpen);
            if (!this.leftOverlayOpen) this.leftSearch.setFocused(false);
        }
        if (this.rightSearch != null) {
            this.rightSearch.setX(this.leftPos + 357);
            this.rightSearch.setY(this.topPos + 76);
            this.rightSearch.setVisible(this.rightOverlayOpen);
            if (!this.rightOverlayOpen) this.rightSearch.setFocused(false);
        }
        if (this.renameField != null && this.renameField.isVisible()) positionRenameField();
    }

    private void refreshNumericFields() {
        for (NumericField field : this.numericFields) {
            if (field.box.isFocused()) continue;
            Optional<Double> value = this.editor.numericValue(field.property, field.axis);
            field.box.setValue(value.map(PlasticMoldingChamberScreen::formatNumber).orElse(""));
            field.box.active = this.editor.writable() && !this.editor.pending() && !this.editor.selection().isEmpty();
        }
    }

    private void commitFocusedNumeric() {
        NumericField focused = focusedNumeric();
        if (focused == null) return;
        try {
            double value = Double.parseDouble(focused.box.getValue());
            this.editor.setNumeric(focused.property, focused.axis, value);
        } catch (NumberFormatException ignored) {
        }
        focused.box.setFocused(false);
        refreshNumericFields();
    }

    private void openRename() {
        Optional<String> primaryName = this.editor.primaryName();
        if (primaryName.isEmpty() || this.renameField == null) return;
        this.renameField.setValue(primaryName.get());
        this.renameField.setVisible(true);
        positionRenameField();
        this.renameField.setFocused(true);
        this.setFocused(this.renameField);
        this.renameField.moveCursorToEnd(false);
    }

    private void positionRenameField() {
        this.renameField.setX(this.leftPos + Math.clamp(this.contextMenuX, VIEWPORT_X, VIEWPORT_X + VIEWPORT_WIDTH - CONTEXT_WIDTH));
        this.renameField.setY(this.topPos + Math.clamp(this.contextMenuY, VIEWPORT_Y, VIEWPORT_Y + VIEWPORT_HEIGHT - 13));
    }

    private void commitRename() {
        if (this.renameField != null) this.editor.renamePrimary(this.renameField.getValue());
        closeRename();
    }

    private void closeRename() {
        if (this.renameField == null) return;
        this.renameField.setFocused(false);
        if (this.getFocused() == this.renameField) this.setFocused(null);
        this.renameField.setVisible(false);
        this.contextMenuOpen = false;
    }

    private void synchronizeMenuSnapshot() {
        if (this.seenMenuModel == this.menu.model()
            && this.seenMenuRevision == this.menu.revision()
            && this.seenWritable == this.menu.writable()
            && this.seenWriterName.equals(this.menu.writerName())) {
            return;
        }
        this.seenMenuModel = this.menu.model();
        this.seenMenuRevision = this.menu.revision();
        this.seenWritable = this.menu.writable();
        this.seenWriterName = this.menu.writerName();
        this.editor.syncAuthoritative(this.menu.model(), this.menu.revision(), this.menu.writable());
        refreshNumericFields();
        ensurePrimaryVisible();
    }

    private void ensurePrimaryVisible() {
        Optional<UUID> primary = this.editor.selection().primary();
        if (primary.isEmpty()) return;
        List<SceneObject> objects = sceneObjects();
        for (int index = 0; index < objects.size(); index++) {
            if (!objects.get(index).id().equals(primary.get())) continue;
            int row = index / 2;
            if (row < this.elementScrollRow) this.elementScrollRow = row;
            else if (row >= this.elementScrollRow + 2) this.elementScrollRow = row - 1;
            return;
        }
    }

    private List<SceneObject> sceneObjects() {
        List<SceneObject> objects = new ArrayList<>();
        List<MoldingElement> elements = this.editor.model().elements();
        for (MoldingGroup group : this.editor.model().groups()) {
            objects.add(new SceneObject(
                group.id(),
                group.name(),
                "G",
                "screen.anvilcraftplasticraft.molding.element.group",
                false
            ));
        }
        for (MoldingElement element : elements) {
            objects.add(sceneObject(element));
        }
        return objects;
    }

    private int elementEntryCount() {
        return sceneObjects().size() + 1;
    }

    private static SceneObject sceneObject(MoldingElement element) {
        return new SceneObject(
            element.id(),
            element.name(),
            "C",
            "screen.anvilcraftplasticraft.molding.element.cube",
            true
        );
    }

    private void sendCommand(long baseRevision, MoldingCommand command) {
        PacketDistributor.sendToServer(new MoldingEditPacket(
            this.menu.chamberPos(),
            this.menu.sessionId(),
            baseRevision,
            command
        ));
    }

    private void drawThreeFrame(
        GuiGraphics graphics,
        GuiRect rect,
        boolean pressed,
        boolean enabled,
        int mouseX,
        int mouseY
    ) {
        int frame = pressed ? 2 : rect.contains(this.leftPos, this.topPos, mouseX, mouseY) ? 1 : 0;
        drawAtlas(graphics, rect, BUTTON_16_3, 3, frame);
        if (!enabled) drawDisabledOverlay(graphics, rect);
    }

    private void drawDisabledAtlas(
        GuiGraphics graphics,
        GuiRect rect,
        ResourceLocation atlas,
        int frames
    ) {
        drawAtlas(graphics, rect, atlas, frames, 0);
        drawDisabledOverlay(graphics, rect);
    }

    private void drawDisabledOverlay(GuiGraphics graphics, GuiRect rect) {
        graphics.fill(
            this.leftPos + rect.x,
            this.topPos + rect.y,
            this.leftPos + rect.x + rect.width,
            this.topPos + rect.y + rect.height,
            0x78000000
        );
    }

    private void drawAtlas(
        GuiGraphics graphics,
        GuiRect rect,
        ResourceLocation atlas,
        int frames,
        int frame
    ) {
        graphics.blit(
            atlas,
            this.leftPos + rect.x,
            this.topPos + rect.y,
            0.0F,
            (float) (Math.clamp(frame, 0, frames - 1) * rect.height),
            rect.width,
            rect.height,
            rect.width,
            rect.height * frames
        );
    }

    private void tooltip(GuiRect rect, int mouseX, int mouseY, String key) {
        if (rect.contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            this.hoveredControlTooltip = List.of(Component.translatable(key));
        }
    }

    private List<FormattedCharSequence> wrapTooltip(List<Component> lines, int screenWidth, int mouseX) {
        int screenLimit = Math.max(1, screenWidth - 16);
        int leftSpace = Math.max(1, mouseX - 20);
        int rightSpace = Math.max(1, screenWidth - mouseX - 20);
        int lineWidth = Math.min(screenLimit, Math.max(40, Math.max(leftSpace, rightSpace)));
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) wrapped.addAll(this.font.split(line, lineWidth));
        return List.copyOf(wrapped);
    }

    private boolean isInsideMain(
        int x,
        int y,
        int width,
        int height,
        double mouseX,
        double mouseY
    ) {
        return mouseX >= this.leftPos + x
            && mouseX < this.leftPos + x + width
            && mouseY >= this.topPos + y
            && mouseY < this.topPos + y + height;
    }

    private boolean isOverTextField(double mouseX, double mouseY) {
        for (NumericField field : this.numericFields) {
            if (field.box.visible && field.box.isMouseOver(mouseX, mouseY)) return true;
        }
        return this.leftSearch != null && this.leftSearch.isVisible() && this.leftSearch.isMouseOver(mouseX, mouseY)
            || this.rightSearch != null && this.rightSearch.isVisible() && this.rightSearch.isMouseOver(mouseX, mouseY)
            || this.renameField != null && this.renameField.isVisible() && this.renameField.isMouseOver(mouseX, mouseY);
    }

    private boolean isOverSearchField(double mouseX, double mouseY) {
        return this.leftSearch != null && this.leftSearch.isVisible() && this.leftSearch.isMouseOver(mouseX, mouseY)
            || this.rightSearch != null && this.rightSearch.isVisible() && this.rightSearch.isMouseOver(mouseX, mouseY);
    }

    private void clearSearchFocus() {
        if (this.leftSearch != null) this.leftSearch.setFocused(false);
        if (this.rightSearch != null) this.rightSearch.setFocused(false);
    }

    private @Nullable NumericField focusedNumeric() {
        return this.numericFields.stream().filter(field -> field.box.isFocused()).findFirst().orElse(null);
    }

    private @Nullable EditBox focusedSearch() {
        if (this.leftSearch != null && this.leftSearch.isFocused()) return this.leftSearch;
        if (this.rightSearch != null && this.rightSearch.isFocused()) return this.rightSearch;
        return null;
    }

    private double toLogicalX(double mouseX) {
        return this.layoutTransform.toLogicalX(mouseX);
    }

    private double toLogicalY(double mouseY) {
        return this.layoutTransform.toLogicalY(mouseY);
    }

    private static boolean isNumericText(String text) {
        return text.isEmpty() || text.equals("-") || text.matches("-?\\d*(\\.\\d*)?");
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-8D) return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static ResourceLocation texture(String path) {
        return AnvilcraftPlasticraft.of(path);
    }

    private static ResourceLocation widget(String name) {
        return texture("textures/gui/button/plastic_molding_chamber/" + name);
    }

    private static final class CenteredEditBox extends EditBox {
        private final Font font;

        private CenteredEditBox(Font font, int width, int height, Component message) {
            super(font, width, height, message);
            this.font = font;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int originalX = getX();
            setX(originalX + textOffset());
            try {
                super.renderWidget(graphics, mouseX, mouseY, partialTick);
            } finally {
                setX(originalX);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            int originalX = getX();
            int offset = textOffset();
            setX(originalX + offset);
            try {
                super.onClick(Math.max(mouseX, originalX + offset), mouseY);
            } finally {
                setX(originalX);
            }
        }

        private int textOffset() {
            return Math.max(0, (getWidth() - this.font.width(getValue())) / 2 - 2);
        }
    }

    private static final class VerticallyOffsetEditBox extends EditBox {
        private VerticallyOffsetEditBox(Font font, int width, int height, Component message) {
            super(font, width, height, message);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int originalY = getY();
            setY(originalY + 1);
            try {
                super.renderWidget(graphics, mouseX, mouseY, partialTick);
            } finally {
                setY(originalY);
            }
        }
    }

    private record NumericField(MoldingNumericProperty property, MoldingAxis axis, EditBox box) {
    }

    private record ViewportHover(@Nullable UUID elementId, @Nullable MoldingAxis gizmoAxis) {
        private static final ViewportHover NONE = new ViewportHover(null, null);
    }

    private record SceneObject(UUID id, String name, String kind, String typeKey, boolean element) {
    }

    private record GuiRect(int x, int y, int width, int height) {
        private boolean contains(int left, int top, double mouseX, double mouseY) {
            return mouseX >= left + this.x
                && mouseX < left + this.x + this.width
                && mouseY >= top + this.y
                && mouseY < top + this.y + this.height;
        }
    }

    private record ContextAction(
        ContextActionKind kind,
        String translationKey,
        BooleanSupplier enabled
    ) {
    }

    private enum ContextActionKind {
        TAKEOVER,
        CREATE_CUBE,
        RENAME,
        DELETE,
        HIDE,
        LOCK,
        GROUP,
        CENTER_PIVOT,
        FOCUS,
        RESET_CAMERA
    }
}
