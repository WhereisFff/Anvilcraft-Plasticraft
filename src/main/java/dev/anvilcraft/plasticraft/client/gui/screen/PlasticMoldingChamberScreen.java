package dev.anvilcraft.plasticraft.client.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberStructure;
import dev.anvilcraft.plasticraft.block.PlasticMoldingMachineState;
import dev.anvilcraft.plasticraft.block.entity.Plastic3DPrintingComponentBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.client.gui.MoldingHardHatTypeIcon;
import dev.anvilcraft.plasticraft.client.gui.MoldingTrayTypeIcon;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingAxis;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingCamera;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingEditorController;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingGizmoBasis;
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
import dev.anvilcraft.plasticraft.client.molding.scene.PrintingViewportScene;
import dev.anvilcraft.plasticraft.client.molding.scene.ViewportFrame;
import dev.anvilcraft.plasticraft.client.renderer.AntialiasedGuiLineRenderer;
import dev.anvilcraft.plasticraft.client.renderer.molding.MoldingOrientationCubeRenderer;
import dev.anvilcraft.plasticraft.client.renderer.molding.MoldingViewportBackend;
import dev.anvilcraft.plasticraft.client.renderer.molding.MoldingViewportBackend1211;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.inventory.PlasticMoldingChamberMenu;
import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprint;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintDisk;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintDiskAction;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintLibraryAction;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintSummary;
import dev.anvilcraft.plasticraft.molding.machine.MoldingMachineAction;
import dev.anvilcraft.plasticraft.molding.machine.MoldingFormingMode;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPowerBridge;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPrinterMotion;
import dev.anvilcraft.plasticraft.molding.machine.MoldingProductionMode;
import dev.anvilcraft.plasticraft.molding.machine.MoldingWaitReason;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingCommand;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingGroup;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelBounds;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductType;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductPreview;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.anvilcraft.plasticraft.molding.type.MoldingTypeValidation;
import dev.anvilcraft.plasticraft.network.MoldingBlueprintDiskPacket;
import dev.anvilcraft.plasticraft.network.MoldingBlueprintLibraryPacket;
import dev.anvilcraft.plasticraft.network.MoldingBlueprintListRequestPacket;
import dev.anvilcraft.plasticraft.network.MoldingBlueprintRenamePacket;
import dev.anvilcraft.plasticraft.network.MoldingEditPacket;
import dev.anvilcraft.plasticraft.network.MoldingHeartbeatPacket;
import dev.anvilcraft.plasticraft.network.MoldingMachinePacket;
import dev.anvilcraft.plasticraft.network.MoldingReleasePacket;
import dev.anvilcraft.plasticraft.network.MoldingTakeoverPacket;
import dev.dubhe.anvilcraft.client.support.RenderSupport;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final int LAYOUT_PADDING = 4;
    private static final int FULL_LAYOUT_WIDTH = LOGICAL_WIDTH + (OVERLAY_WIDTH - 1) * 2;
    private static final int HEARTBEAT_INTERVAL = 40;
    private static final int TITLE_MESSAGE_TICKS = 100;
    private static final int TITLE_BAR_HEIGHT = 13;
    private static final int CONTEXT_WIDTH = 92;
    private static final int CONTEXT_ROW_HEIGHT = 11;
    private static final int ELEMENT_VISIBLE_ROWS = 2;
    private static final int ELEMENT_SCROLLBAR_X = 335;
    private static final int ELEMENT_SCROLLBAR_Y = 81;
    private static final int ELEMENT_SCROLLBAR_WIDTH = 6;
    private static final int ELEMENT_SCROLLBAR_HEIGHT = 49;
    private static final int ELEMENT_SCROLL_TRACK_X = 336;
    private static final int ELEMENT_SCROLL_TRACK_Y = 82;
    private static final int ELEMENT_SCROLL_TRACK_HEIGHT = 46;
    private static final int ELEMENT_SCROLL_HANDLE_WIDTH = 4;
    private static final int ELEMENT_SCROLL_HANDLE_HEIGHT = 12;
    private static final int ELEMENT_SCROLL_HANDLE_TEXTURE_WIDTH = 8;
    private static final int BLUEPRINT_VISIBLE_ROWS = 6;
    private static final int BLUEPRINT_SCROLLBAR_X = 413;
    private static final int BLUEPRINT_SCROLLBAR_Y = 89;
    private static final int BLUEPRINT_SCROLLBAR_WIDTH = 4;
    private static final int BLUEPRINT_SCROLL_TRACK_Y = 90;
    private static final int BLUEPRINT_SCROLL_TRACK_HEIGHT = 89;
    private static final int BLUEPRINT_SCROLL_HANDLE_WIDTH = 4;
    private static final int BLUEPRINT_SCROLL_HANDLE_HEIGHT = 12;
    private static final int BLUEPRINT_SCROLL_HANDLE_TEXTURE_WIDTH = 8;
    private static final GuiRect CATEGORY_LIST_RECT = new GuiRect(-68, 90, 52, 105);
    private static final int CATEGORY_ENTRY_HEIGHT = 15;
    private static final int CATEGORY_VISIBLE_ROWS = 7;
    private static final int CATEGORY_SCROLLBAR_X = -13;
    private static final int CATEGORY_SCROLLBAR_Y = 89;
    private static final int CATEGORY_SCROLLBAR_WIDTH = 6;
    private static final int CATEGORY_SCROLLBAR_HEIGHT = 110;
    private static final int CATEGORY_SCROLL_TRACK_X = -12;
    private static final int CATEGORY_SCROLL_TRACK_Y = 90;
    private static final int CATEGORY_SCROLL_TRACK_HEIGHT = 108;
    private static final int CATEGORY_SCROLL_HANDLE_WIDTH = 4;
    private static final int CATEGORY_SCROLL_HANDLE_HEIGHT = 12;
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
    private static final ReleaseControl[] EDITOR_RELEASE_CONTROLS = {
        ReleaseControl.UNDO,
        ReleaseControl.COPY,
        ReleaseControl.CUT,
        ReleaseControl.PASTE
    };
    private static final ChamberControl[] MAIN_CHAMBER_CONTROLS = {
        ChamberControl.OPERATE,
        ChamberControl.CATEGORY,
        ChamberControl.MODEL,
        ChamberControl.DISK_LOAD,
        ChamberControl.DISK_STORE
    };
    private static final String[] TOOL_NAMES = {"move", "scale", "rotate", "pivot", "mirror"};
    private static final ResourceLocation BACKGROUND = texture("textures/gui/background/plastic_molding_chamber.png");
    private static final ResourceLocation LEFT_OVERLAY = texture("textures/gui/background/plastic_molding_chamber_left.png");
    private static final ResourceLocation RIGHT_OVERLAY = texture("textures/gui/background/plastic_molding_chamber_right.png");
    private static final ResourceLocation BUTTON_32_23_2 = widget("button_32x23_2.png");
    private static final ResourceLocation CUBE_BUTTON = widget("cube.png");
    private static final ResourceLocation NEW_CUBE_BUTTON = widget("new_cube.png");
    private static final ResourceLocation MODEL_ENTRY_BUTTON = widget("model_entry.png");
    private static final ResourceLocation ELEMENT_SCROLL_HANDLE = widget("slider.png");
    private static final ResourceLocation UNDO_BUTTON = widget("undo.png");
    private static final ResourceLocation COPY_BUTTON = widget("copy.png");
    private static final ResourceLocation CUT_BUTTON = widget("cut.png");
    private static final ResourceLocation PASTE_BUTTON = widget("paste.png");
    private static final ResourceLocation BLUEPRINT_SCROLL_HANDLE = widget("slider.png");
    private static final ResourceLocation CYCLE_MODE_BUTTON = widget("cycle.png");
    private static final ResourceLocation REDSTONE_MODE_BUTTON = widget("redstone.png");
    private static final ResourceLocation ONCE_MODE_BUTTON = widget("once.png");
    private static final ResourceLocation OPERATE_BUTTON = widget("operate.png");
    private static final ResourceLocation LOAD_BUTTON = widget("load.png");
    private static final ResourceLocation SAVE_BUTTON = widget("save.png");
    private static final ResourceLocation CATEGORY_BUTTON = widget("category.png");
    private static final ResourceLocation MODEL_BUTTON = widget("model.png");
    private static final ResourceLocation CATEGORY_ENTRY_BUTTON = widget("category_entry.png");
    private static final ResourceLocation PIN_TO_TOP_BUTTON = widget("pin_to_top.png");
    private static final ResourceLocation COPY_SMALL_BUTTON = widget("copy_small.png");
    private static final ResourceLocation FILE_BUTTON = widget("file.png");
    private static final ResourceLocation DELETE_BUTTON = widget("delete.png");
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
    private boolean rightOverlayOpenedLast;
    private boolean showGrid = true;
    private boolean showAxes = true;
    private MoldingGuiTransform layoutTransform = new MoldingGuiTransform(1.0D, 0.0D, 0.0D);
    private int heartbeatCountdown = HEARTBEAT_INTERVAL;
    private int elementScrollRow;
    @Nullable
    private UUID elementSelectionAnchor;
    private int typeScrollRow;
    private int blueprintScrollRow;
    private long seenBlueprintListGeneration = -1L;
    private String selectedBlueprintFileId = "";
    private long dynamicSceneRevision;
    @Nullable
    private EditorSceneMesh cachedScene;
    @Nullable
    private BakedMoldingModel cachedBaked;
    @Nullable
    private EditableMoldingModel cachedSceneModel;
    @Nullable
    private EditableMoldingModel cachedTypeValidationModel;
    private Optional<MoldingTypeValidation> cachedTypeValidation = Optional.empty();
    @Nullable
    private EditableMoldingModel cachedWorkspaceBoundsModel;
    private MoldingFormingMode cachedWorkspaceFormingMode = MoldingFormingMode.CASTING;
    private boolean cachedModelFitsWorkspace = true;
    @Nullable
    private EditableMoldingModel cachedDowngradeModel;
    private int cachedDowngradeMelt = -1;
    private boolean cachedDowngrade;
    private MoldingSelection cachedSelection = MoldingSelection.EMPTY;
    private MoldingTool cachedTool = MoldingTool.MOVE;
    private boolean cachedInvalid;
    private boolean cachedShowGrid = true;
    private boolean cachedShowAxes = true;
    private boolean cachedPrintingWorkspace;
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
    @Nullable
    private EditBox blueprintRenameField;
    private String renamingBlueprintFileId = "";
    private long renamingBlueprintRevision;
    private boolean contextMenuOpen;
    private int contextMenuX;
    private int contextMenuY;
    private int cameraDragButton = -1;
    private boolean cameraDragged;
    private boolean elementScrollbarDragging;
    private boolean typeScrollbarDragging;
    private boolean blueprintScrollbarDragging;
    @Nullable
    private ReleaseControl pressedReleaseControl;
    @Nullable
    private ChamberControl pressedChamberControl;
    @Nullable
    private MoldingBlueprintSummary pressedBlueprint;
    private boolean lockConfirmation;
    private int lockClayRequirement;
    @Nullable
    private DiskConfirmation diskConfirmation;
    private double elementScrollbarGrabOffset;
    private double typeScrollbarGrabOffset;
    private double blueprintScrollbarGrabOffset;
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
    @Nullable
    private Vector3d modelDragAxisDirection;
    private double modelDragDirection = 1.0D;
    private double modelDragWorldUnitsPerLocalUnit = 1.0D;
    private long seenTitleMessageGeneration;
    private int titleMessageTicks;
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
        this.seenWritable = menu.modelEditable();
        this.seenWriterName = menu.writerName();
        this.seenTitleMessageGeneration = menu.titleMessageGeneration();
        this.editor = new MoldingEditorController(
            menu.model(),
            menu.revision(),
            menu.modelEditable(),
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
        this.leftSearch.setResponder(ignored -> this.typeScrollRow = 0);
        this.rightSearch = createSearchBox("screen.anvilcraftplasticraft.molding.search_json");
        this.rightSearch.setResponder(ignored -> this.blueprintScrollRow = 0);
        this.renameField = new EditBox(
            this.font,
            CONTEXT_WIDTH,
            13,
            Component.translatable("screen.anvilcraftplasticraft.molding.rename")
        );
        this.renameField.setMaxLength(MoldingElement.MAX_NAME_LENGTH);
        this.renameField.setVisible(false);
        this.blueprintRenameField = new VerticallyOffsetEditBox(
            this.font,
            48,
            11,
            Component.translatable("screen.anvilcraftplasticraft.molding.rename")
        );
        this.blueprintRenameField.setMaxLength(MoldingElement.MAX_NAME_LENGTH);
        this.blueprintRenameField.setBordered(false);
        this.blueprintRenameField.setVisible(false);
        this.addRenderableWidget(this.leftSearch);
        this.addRenderableWidget(this.rightSearch);
        this.addRenderableWidget(this.renameField);
        this.addRenderableWidget(this.blueprintRenameField);
        updateLayout();
        refreshNumericFields();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
        synchronizeTitleMessage();
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
        boolean emptyResourceSlotHovered = this.hoveredSlot
            == this.menu.getSlot(PlasticMoldingChamberBlockEntity.RESOURCE_SLOT)
            && !this.hoveredSlot.hasItem();
        if (this.hoveredControlTooltip != null && (this.hoveredSlot == null || emptyResourceSlotHovered)) {
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
        renderViewport(graphics, mouseX, mouseY, partialTick);
        renderToolButtons(graphics, mouseX, mouseY);
        renderElementList(graphics, mouseX, mouseY);
        renderMainControls(graphics, mouseX, mouseY, partialTick);
        renderOverlays(graphics, mouseX, mouseY);
        if (this.contextMenuOpen) renderContextMenu(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean showingMessage = this.titleMessageTicks > 0 && !this.menu.titleMessage().getString().isEmpty();
        Optional<MoldingTypeValidation> typeValidation = currentTypeValidation();
        boolean invalidType = typeValidation.filter(validation -> !validation.valid()).isPresent()
            && !typeOverrideActive();
        boolean oversized = !modelFitsCurrentWorkspace() && !creativeOverrideActive();
        Component label = showingMessage
            ? this.menu.titleMessage().copy().withStyle(ChatFormatting.BOLD)
            : oversized
                ? Component.translatable("screen.anvilcraftplasticraft.molding.model_too_large")
                    .withStyle(ChatFormatting.BOLD)
                : invalidType
                ? Component.translatable("screen.anvilcraftplasticraft.molding.type_invalid")
                    .withStyle(ChatFormatting.BOLD)
                : this.title;
        drawTitleLabel(graphics, label, showingMessage || invalidType || oversized ? 0xFFE34B4B : 0xFF404040);
        if (new GuiRect(0, 0, LOGICAL_WIDTH, TITLE_BAR_HEIGHT).contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            this.hoveredControlTooltip = showingMessage
                ? List.of(label)
                : titleStatusTooltip(typeValidation);
        }
    }

    @Override
    public void renderSlot(GuiGraphics graphics, Slot slot) {
        super.renderSlot(graphics, slot);
        if (slot != this.menu.getSlot(PlasticMoldingChamberBlockEntity.CLAY_SLOT)) return;
        if (!slot.hasItem()) {
            RenderSupport.renderItemWithTransparency(
                new ItemStack(Items.CLAY_BALL),
                graphics.pose(),
                slot.x,
                slot.y,
                0.52F
            );
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x60FFAAAA);
        }
        renderClaySlotLimit(graphics, slot);
    }

    private void drawTitleLabel(GuiGraphics graphics, Component label, int color) {
        int textWidth = Math.max(1, this.font.width(label));
        float scale = Math.min(1.0F, (LOGICAL_WIDTH - 8.0F) / textWidth);
        float x = (LOGICAL_WIDTH - textWidth * scale) * 0.5F;
        float y = (TITLE_BAR_HEIGHT - this.font.lineHeight * scale) * 0.5F;
        drawScaledTitleLabel(graphics, label, color, scale, x, y);
    }

    private void drawScaledTitleLabel(
        GuiGraphics graphics,
        Component label,
        int color,
        float scale,
        float x,
        float y
    ) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, label, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void renderClaySlotLimit(GuiGraphics graphics, Slot slot) {
        int limit = this.menu.clayLimit();
        if (limit == Items.CLAY_BALL.getDefaultMaxStackSize()) return;
        String text = Integer.toString(limit);
        float scale = 0.6F;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        int width = this.font.width(text);
        int x = (int) ((slot.x + 16.25F - width * scale) / scale);
        int y = (int) ((slot.y + 14.0F - this.font.lineHeight * 2.0F * scale + 1.0F) / scale);
        graphics.drawString(this.font, text, x, y, 0xFFFFA0A0, true);
        graphics.pose().popPose();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        synchronizeTitleMessage();
        if (this.titleMessageTicks > 0) this.titleMessageTicks--;
        synchronizeMenuSnapshot();
        synchronizeBlueprintList();
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
        if (button == 0
            && !new GuiRect(47, 143, 34, 16).contains(this.leftPos, this.topPos, logicalX, logicalY)) {
            this.lockConfirmation = false;
            this.lockClayRequirement = 0;
        }
        if (button == 0 && !isDiskConfirmationControl(logicalX, logicalY)) this.diskConfirmation = null;
        if (this.blueprintRenameField != null
            && this.blueprintRenameField.isVisible()
            && !this.blueprintRenameField.isMouseOver(logicalX, logicalY)) {
            commitBlueprintRename();
        }
        if (!isOverSearchField(logicalX, logicalY)) clearSearchFocus();
        NumericField focusedNumeric = focusedNumeric();
        if (button == 0 && focusedNumeric != null && !focusedNumeric.box.isMouseOver(logicalX, logicalY)) {
            commitFocusedNumeric();
        }
        if (isOverTextField(logicalX, logicalY)) {
            this.contextMenuOpen = false;
            return super.mouseClicked(logicalX, logicalY, button);
        }
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
        if (this.typeScrollbarDragging && button == 0) {
            updateTypeScrollFromPointer(logicalY);
            return true;
        }
        if (this.blueprintScrollbarDragging && button == 0) {
            updateBlueprintScrollFromPointer(logicalY);
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
        if (this.typeScrollbarDragging && button == 0) {
            this.typeScrollbarDragging = false;
            return true;
        }
        if (this.blueprintScrollbarDragging && button == 0) {
            this.blueprintScrollbarDragging = false;
            return true;
        }
        if (this.modelDragAxis != null && button == 0) {
            this.editor.finishDrag();
            this.modelDragAxis = null;
            this.modelDragTransform = null;
            this.modelDragOrigin = null;
            this.modelDragAxisDirection = null;
            this.modelDragDirection = 1.0D;
            this.modelDragWorldUnitsPerLocalUnit = 1.0D;
            refreshNumericFields();
            return true;
        }
        if (button == 0 && releaseChamberControl(logicalX, logicalY)) return true;
        if (button == 0 && releasePressedControl(logicalX, logicalY)) return true;
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
        if (this.rightOverlayOpen && isInsideMain(357, 90, 61, 90, logicalX, logicalY)) {
            List<MoldingBlueprintSummary> filtered = filteredBlueprints();
            int max = blueprintMaxScroll(filtered.size());
            this.blueprintScrollRow = Math.clamp(
                this.blueprintScrollRow - (int) Math.signum(scrollY),
                0,
                max
            );
            return true;
        }
        if (this.leftOverlayOpen && CATEGORY_LIST_RECT.contains(this.leftPos, this.topPos, logicalX, logicalY)) {
            List<MoldingProductType> types = filteredProductTypes();
            this.typeScrollRow = Math.clamp(
                this.typeScrollRow - (int) Math.signum(scrollY),
                0,
                typeMaxScroll(types.size())
            );
            return true;
        }
        if (isInsideMain(8, 142, 18, 18, logicalX, logicalY)
            && scrollY != 0.0D
            && this.menu.writable()) {
            int step = Screen.hasShiftDown() ? 5 : 1;
            int next = Math.clamp(
                this.menu.clayLimit() + (int) Math.signum(scrollY) * step,
                PlasticMoldingChamberBlockEntity.CLAY_LIMIT_MIN,
                PlasticMoldingChamberBlockEntity.CLAY_LIMIT_MAX
            );
            if (next != this.menu.clayLimit()) {
                sendMachineAction(MoldingMachineAction.SET_CLAY_LIMIT, next);
            }
            return true;
        }
        return super.mouseScrolled(logicalX, logicalY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.blueprintRenameField != null
            && this.blueprintRenameField.isVisible()
            && this.blueprintRenameField.isFocused()) {
            if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                commitBlueprintRename();
                return true;
            }
            if (keyCode == InputConstants.KEY_ESCAPE) {
                closeBlueprintRename();
                return true;
            }
            return this.blueprintRenameField.keyPressed(keyCode, scanCode, modifiers);
        }
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
                if (releaseControlEnabled(ReleaseControl.COPY)) executeReleaseControl(ReleaseControl.COPY);
                return true;
            }
            if (keyCode == InputConstants.KEY_X) {
                if (releaseControlEnabled(ReleaseControl.CUT)) executeReleaseControl(ReleaseControl.CUT);
                return true;
            }
            if (keyCode == InputConstants.KEY_V) {
                if (releaseControlEnabled(ReleaseControl.PASTE)) executeReleaseControl(ReleaseControl.PASTE);
                return true;
            }
            if (keyCode == InputConstants.KEY_Z) {
                if (Screen.hasShiftDown()) this.editor.redo();
                else if (releaseControlEnabled(ReleaseControl.UNDO)) executeReleaseControl(ReleaseControl.UNDO);
                return true;
            }
            if (keyCode == InputConstants.KEY_Y) {
                this.editor.redo();
                return true;
            }
            if (keyCode == InputConstants.KEY_G) {
                if (this.editor.groupSelection()) syncElementSelectionAnchor();
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

    private void renderViewport(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
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
            controllerPreviewLight(),
            printingViewportScene(controllerOrigin, partialTick)
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

    private @Nullable PrintingViewportScene printingViewportScene(Vec3 controllerOrigin, float partialTick) {
        if (displayedFormingMode() != MoldingFormingMode.PRINTING || !this.menu.printingComponentPresent()) return null;
        BlockState chamberState = controllerPreviewState();
        if (chamberState == null) return null;
        BlockState componentState = this.minecraft.level == null
            ? PlasticraftBlocks.PLASTIC_3D_PRINTING_COMPONENT.get().defaultBlockState()
            : this.minecraft.level.getBlockState(this.menu.chamberPos().above());
        if (!componentState.is(PlasticraftBlocks.PLASTIC_3D_PRINTING_COMPONENT.get())) {
            componentState = PlasticraftBlocks.PLASTIC_3D_PRINTING_COMPONENT.get().defaultBlockState();
        }
        PlasticMoldingChamberBlockEntity chamber = clientChamber();
        FluidStack componentFluid = FluidStack.EMPTY;
        if (this.minecraft.level != null
            && this.minecraft.level.getBlockEntity(this.menu.chamberPos().above())
                instanceof Plastic3DPrintingComponentBlockEntity component) {
            componentFluid = component.fluid();
        }
        return new PrintingViewportScene(
            chamberState,
            componentState,
            new Vector3d(controllerOrigin.x, controllerOrigin.y, controllerOrigin.z),
            chamber == null || this.minecraft.level == null
                ? MoldingPrinterMotion.DEFAULT_POSITION
                : chamber.printingMotion().position(this.minecraft.level.getGameTime() + partialTick),
            chamber == null ? 0.0F : chamber.printingDoorProgress(partialTick),
            controllerPreviewLight(),
            componentFluid
        );
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
        AntialiasedGuiLineRenderer.render(
            graphics,
            startX,
            startY,
            endX,
            endY,
            color,
            DIRECTION_COMPASS_LINE_WIDTH,
            DIRECTION_COMPASS_FEATHER
        );
    }

    private List<Component> titleStatusTooltip(Optional<MoldingTypeValidation> typeValidation) {
        Component writer = this.menu.writerName().isEmpty()
            ? Component.translatable("screen.anvilcraftplasticraft.molding.no_writer")
            : Component.translatable("screen.anvilcraftplasticraft.molding.writer", this.menu.writerName());
        List<Component> lines = new ArrayList<>();
        lines.add(writer);
        lines.add(Component.translatable(
            "screen.anvilcraftplasticraft.molding.state",
            Component.translatable(
                "screen.anvilcraftplasticraft.molding.state." + this.menu.machineState().getSerializedName()
            )
        ));
        lines.add(Component.translatable(
            "screen.anvilcraftplasticraft.molding.forming",
            Component.translatable(
                "screen.anvilcraftplasticraft.molding.forming."
                    + displayedFormingMode().getSerializedName()
            )
        ));
        if (displayedFormingMode() == MoldingFormingMode.CASTING) {
            lines.add(Component.translatable(
                "screen.anvilcraftplasticraft.molding.clay_status",
                this.menu.getSlot(PlasticMoldingChamberBlockEntity.CLAY_SLOT).getItem().getCount(),
                this.menu.moldedClayBalls(),
                this.menu.requiredClayBalls()
            ));
        } else if (this.menu.printingTotal() > 0) {
            lines.add(Component.translatable(
                "screen.anvilcraftplasticraft.molding.printing_progress",
                this.menu.printingProgress(),
                this.menu.printingTotal()
            ));
        }
        if (this.menu.waitReason() != MoldingWaitReason.NONE
            && this.menu.waitReason() != MoldingWaitReason.PROCESS_READY) {
            lines.add(waitReasonText(this.menu.waitReason()).copy().withStyle(ChatFormatting.YELLOW));
        }
        if (this.cachedBaked != null) {
            lines.add(Component.translatable(
                "screen.anvilcraftplasticraft.molding.analysis",
                this.cachedBaked.analysis().volume(),
                this.cachedBaked.analysis().minimumMeltMillibuckets(),
                displayedFormingMode() == MoldingFormingMode.PRINTING
                    ? 0
                    : this.cachedBaked.analysis().clayBallRequirement()
            ));
        }
        if (this.willCurrentResultDowngrade()) {
            lines.add(Component.translatable("screen.anvilcraftplasticraft.molding.partial_downgrade")
                .withStyle(ChatFormatting.RED));
        }
        if (!modelFitsCurrentWorkspace() && !creativeOverrideActive()) {
            lines.add(Component.translatable(workspaceLimitMessageKey())
                .withStyle(ChatFormatting.RED));
        }
        typeValidation.filter(validation -> !validation.valid() && !typeOverrideActive())
            .ifPresent(validation -> lines.addAll(typeValidationTooltip(validation)));
        if (creativeOverrideActive()) {
            lines.add(Component.translatable("screen.anvilcraftplasticraft.molding.creative_override_active")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        } else if (typeOverrideActive()) {
            lines.add(Component.translatable("screen.anvilcraftplasticraft.molding.type_override_active")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
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
            if (!this.editor.writable()) drawDisabledOverlay(graphics, rect);
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
                    ? List.of(
                        Component.literal(object.name()),
                        Component.translatable(
                            object.visible()
                                ? "screen.anvilcraftplasticraft.molding.element.visible"
                                : "screen.anvilcraftplasticraft.molding.element.hidden"
                        ),
                        Component.translatable(
                            object.locked()
                                ? "screen.anvilcraftplasticraft.molding.element.locked"
                                : "screen.anvilcraftplasticraft.molding.element.unlocked"
                        )
                    )
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
        renderReleaseControl(
            graphics,
            ReleaseControl.NEW_CUBE,
            rect,
            NEW_CUBE_BUTTON,
            mouseX,
            mouseY
        );
        tooltip(rect, mouseX, mouseY, "screen.anvilcraftplasticraft.molding.context.new_cube");
    }

    private void renderReleaseControl(
        GuiGraphics graphics,
        ReleaseControl control,
        GuiRect rect,
        ResourceLocation texture,
        int mouseX,
        int mouseY
    ) {
        boolean enabled = releaseControlEnabled(control);
        boolean pressed = enabled
            && this.pressedReleaseControl == control
            && rect.contains(this.leftPos, this.topPos, mouseX, mouseY);
        drawThreeFrameAtlas(graphics, rect, texture, pressed, enabled, mouseX, mouseY);
    }

    private void renderMainControls(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiRect type = new GuiRect(9, 114, 16, 16);
        renderCategoryControl(graphics, type, mouseX, mouseY);
        if (type.contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("screen.anvilcraftplasticraft.molding.type"));
            MoldingProductTypes.get(this.editor.model().requestedType())
                .ifPresent(productType -> lines.add(Component.translatable(productType.translationKey())));
            currentTypeValidation().filter(validation -> !validation.valid() && !typeOverrideActive())
                .ifPresent(validation -> lines.addAll(typeValidationTooltip(validation)));
            this.hoveredControlTooltip = List.copyOf(lines);
        }

        GuiRect undo = new GuiRect(269, 136, 16, 16);
        GuiRect copy = new GuiRect(287, 136, 16, 16);
        GuiRect cut = new GuiRect(305, 136, 16, 16);
        GuiRect paste = new GuiRect(323, 136, 16, 16);
        renderReleaseControl(graphics, ReleaseControl.UNDO, undo, UNDO_BUTTON, mouseX, mouseY);
        renderReleaseControl(graphics, ReleaseControl.COPY, copy, COPY_BUTTON, mouseX, mouseY);
        renderReleaseControl(graphics, ReleaseControl.CUT, cut, CUT_BUTTON, mouseX, mouseY);
        renderReleaseControl(graphics, ReleaseControl.PASTE, paste, PASTE_BUTTON, mouseX, mouseY);
        tooltip(undo, mouseX, mouseY, "screen.anvilcraftplasticraft.molding.undo");
        tooltip(copy, mouseX, mouseY, "screen.anvilcraftplasticraft.molding.copy");
        tooltip(cut, mouseX, mouseY, "screen.anvilcraftplasticraft.molding.cut");
        tooltip(paste, mouseX, mouseY, "screen.anvilcraftplasticraft.molding.paste");

        renderLockControl(graphics, mouseX, mouseY, partialTick);
        renderModeControl(graphics, new GuiRect(47, 162, 10, 10), MoldingProductionMode.CONTINUOUS, mouseX, mouseY);
        renderModeControl(graphics, new GuiRect(59, 162, 10, 10), MoldingProductionMode.REDSTONE, mouseX, mouseY);
        renderModeControl(graphics, new GuiRect(71, 162, 10, 10), MoldingProductionMode.SINGLE, mouseX, mouseY);
        renderResourceBars(graphics, mouseX, mouseY);
        tooltip(
            new GuiRect(63, 178, 16, 16),
            mouseX,
            mouseY,
            "screen.anvilcraftplasticraft.molding.resource_slot"
        );
        GuiRect resourceRect = new GuiRect(63, 178, 16, 16);
        if (resourceRect.contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            this.hoveredControlTooltip = List.of(
                Component.translatable("screen.anvilcraftplasticraft.molding.resource_slot"),
                Component.translatable("screen.anvilcraftplasticraft.molding.resource_slot.fluid"),
                Component.translatable("screen.anvilcraftplasticraft.molding.resource_slot.type_override")
            );
        }
        renderDiskControls(graphics, mouseX, mouseY);

        GuiRect model = new GuiRect(324, 171, 16, 16);
        renderModelControl(graphics, model, mouseX, mouseY);
    }

    private void renderLockControl(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiRect rect = new GuiRect(47, 143, 34, 16);
        boolean hovered = rect.contains(this.leftPos, this.topPos, mouseX, mouseY);
        boolean enabled = chamberControlEnabled(ChamberControl.OPERATE, null);
        boolean pressed = enabled && isChamberControlPressed(ChamberControl.OPERATE, rect, mouseX, mouseY);
        boolean editable = this.menu.machineState() == PlasticMoldingMachineState.EDITABLE;
        boolean oversized = editable && !modelFitsCurrentWorkspace() && !creativeOverrideActive();
        int stateFrame = !editable ? 2 : oversized ? 0 : this.lockConfirmation ? 1 : 0;
        int interactionFrame = pressed ? 2 : hovered && enabled ? 1 : 0;
        drawAtlas(graphics, rect, OPERATE_BUTTON, 9, stateFrame * 3 + interactionFrame);
        if (overrideVisualActive()) {
            renderOverrideParticles(graphics, rect, partialTick);
            renderOverrideBorder(graphics, rect, enabled && hovered, pressed);
        }
        if (!enabled) drawDisabledOverlay(graphics, rect);
        if (hovered) {
            List<Component> lines = new ArrayList<>();
            Optional<MoldingTypeValidation> typeValidation = currentTypeValidation()
                .filter(validation -> !validation.valid() && !typeOverrideActive());
            if (this.menu.printingDischargeOpen()) {
                lines.add(Component.translatable(
                    "message.anvilcraftplasticraft.molding.printing_output_pending"
                ).withStyle(ChatFormatting.YELLOW));
            } else if (editable && missingPrintingComponent()) {
                lines.add(Component.translatable(
                    "screen.anvilcraftplasticraft.molding.high_precision_requires_component"
                ).withStyle(ChatFormatting.RED));
            } else if (oversized) {
                lines.add(Component.translatable(workspaceLimitMessageKey())
                    .withStyle(ChatFormatting.RED));
            } else if (editable && typeValidation.isPresent()) {
                lines.addAll(typeValidationTooltip(typeValidation.orElseThrow()));
            } else {
                String tooltipKey = !editable
                    ? "screen.anvilcraftplasticraft.molding.unlock"
                    : this.lockConfirmation
                        ? "screen.anvilcraftplasticraft.molding.lock_confirm"
                        : "screen.anvilcraftplasticraft.molding.lock";
                lines.add(Component.translatable(tooltipKey));
            }
            if (editable && this.lockConfirmation && typeValidation.isEmpty()
                && modelFitsCurrentWorkspace()
                && this.menu.formingMode() == MoldingFormingMode.CASTING) {
                lines.add(Component.translatable(
                    "screen.anvilcraftplasticraft.molding.clay_lock_status",
                    this.menu.getSlot(PlasticMoldingChamberBlockEntity.CLAY_SLOT).getItem().getCount(),
                    this.lockClayRequirement
                ));
            }
            this.hoveredControlTooltip = List.copyOf(lines);
        }
    }

    private void renderModeControl(
        GuiGraphics graphics,
        GuiRect rect,
        MoldingProductionMode mode,
        int mouseX,
        int mouseY
    ) {
        drawThreeFrameAtlas(
            graphics,
            rect,
            modeButtonTexture(mode),
            this.menu.productionMode() == mode,
            this.menu.writable(),
            mouseX,
            mouseY
        );
        tooltip(
            rect,
            mouseX,
            mouseY,
            "screen.anvilcraftplasticraft.molding.mode." + mode.getSerializedName()
        );
    }

    private static ResourceLocation modeButtonTexture(MoldingProductionMode mode) {
        return switch (mode) {
            case CONTINUOUS -> CYCLE_MODE_BUTTON;
            case REDSTONE -> REDSTONE_MODE_BUTTON;
            case SINGLE -> ONCE_MODE_BUTTON;
        };
    }

    private void renderResourceBars(GuiGraphics graphics, int mouseX, int mouseY) {
        GuiRect fluidFrame = new GuiRect(5, 161, 24, 37);
        GuiRect fluidContents = new GuiRect(8, 163, 18, 32);
        int fluidAmount = Math.clamp(
            this.menu.stagingFluidAmount(),
            0,
            PlasticMoldingChamberBlockEntity.STAGING_TANK_CAPACITY
        );
        int fluidHeight = fluidAmount == 0 ? 0 : Math.max(
            1,
            (int) Math.ceil((double) fluidAmount * fluidContents.height
                / PlasticMoldingChamberBlockEntity.STAGING_TANK_CAPACITY)
        );
        FluidStack staging = clientChamber() == null ? FluidStack.EMPTY : clientChamber().stagingFluid();
        if (!staging.isEmpty() && fluidHeight > 0) {
            renderFluidTexture(graphics, fluidContents, fluidHeight, staging);
        }

        GuiRect energyFrame = new GuiRect(31, 161, 13, 37);
        int energyX = 34;
        int energyY = 164;
        int energyWidth = 7;
        int energyHeightLimit = 30;
        int energyHeight = this.menu.energyStored() == 0 ? 0 : Math.max(
            1,
            (int) ((long) Math.clamp(this.menu.energyStored(), 0, MoldingPowerBridge.capacity()) * energyHeightLimit
                / MoldingPowerBridge.capacity())
        );
        renderVerticalSegmentedEnergy(
            graphics,
            this.leftPos + energyX,
            this.topPos + energyY,
            energyWidth,
            energyHeightLimit,
            energyHeight,
            creativeOverrideActive()
        );

        if (fluidFrame.contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(
                "screen.anvilcraftplasticraft.molding.fluid_staging",
                fluidAmount,
                PlasticMoldingChamberBlockEntity.STAGING_TANK_CAPACITY
            ));
            boolean printing = displayedFormingMode() == MoldingFormingMode.PRINTING;
            lines.add(Component.translatable(
                printing
                    ? "screen.anvilcraftplasticraft.molding.fluid_printing_component"
                    : "screen.anvilcraftplasticraft.molding.fluid_batch",
                this.menu.batchFluidAmount(),
                this.menu.batchFluidCapacity()
            ));
            lines.add(Component.translatable(
                printing
                    ? "screen.anvilcraftplasticraft.molding.printing_transfer_rate"
                    : "screen.anvilcraftplasticraft.molding.pump_rate",
                printing
                    ? PlasticMoldingChamberBlockEntity.PRINTING_PUMP_RATE
                    : PlasticMoldingChamberBlockEntity.MOLDING_PUMP_RATE
            ));
            if (printing) lines.add(Component.translatable(
                "screen.anvilcraftplasticraft.molding.printing_consumption_rate",
                PlasticMoldingChamberBlockEntity.PRINTING_CONSUMPTION_RATE
            ));
            if (this.willCurrentResultDowngrade()) {
                lines.add(Component.translatable("screen.anvilcraftplasticraft.molding.partial_downgrade")
                    .withStyle(ChatFormatting.RED));
            }
            appendResourceWaitReason(lines);
            this.hoveredControlTooltip = List.copyOf(lines);
        } else if (energyFrame.contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(
                "screen.anvilcraftplasticraft.molding.energy",
                this.menu.energyStored(),
                MoldingPowerBridge.capacity()
            ));
            lines.add(Component.translatable(
                "screen.anvilcraftplasticraft.molding.rated_power",
                MoldingPowerBridge.RATED_POWER_KW
            ));
            appendResourceWaitReason(lines);
            this.hoveredControlTooltip = List.copyOf(lines);
        }
    }

    private void appendResourceWaitReason(List<Component> lines) {
        MoldingWaitReason reason = this.menu.waitReason();
        if (reason == MoldingWaitReason.NONE || reason == MoldingWaitReason.PROCESS_READY) return;
        lines.add(waitReasonText(reason).copy().withStyle(ChatFormatting.YELLOW));
    }

    private boolean willCurrentResultDowngrade() {
        if (displayedFormingMode() == MoldingFormingMode.PRINTING
            || this.menu.batchFluidAmount() < PlasticMoldingChamberBlockEntity.MINIMUM_PROCESS_MELT
            || MoldingProductTypes.NORMAL_ID.equals(this.editor.model().requestedType())
            || typeOverrideActive()) {
            return false;
        }
        EditableMoldingModel model = this.editor.model();
        int melt = this.menu.batchFluidAmount();
        if (this.cachedDowngradeModel == model && this.cachedDowngradeMelt == melt) {
            return this.cachedDowngrade;
        }
        BakedMoldingModel baked = this.cachedSceneModel == model ? this.cachedBaked : null;
        if (baked == null) {
            try {
                baked = MoldingModelBaker.bake(model);
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        boolean downgraded;
        try {
            downgraded = MoldingProductPreview.evaluate(
                model,
                baked,
                melt
            ).downgraded();
        } catch (RuntimeException ignored) {
            downgraded = false;
        }
        this.cachedDowngradeModel = model;
        this.cachedDowngradeMelt = melt;
        this.cachedDowngrade = downgraded;
        return downgraded;
    }

    private void renderFluidTexture(
        GuiGraphics graphics,
        GuiRect contents,
        int fluidHeight,
        FluidStack fluid
    ) {
        IClientFluidTypeExtensions properties = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = this.minecraft
            .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(properties.getStillTexture(fluid));
        int tint = properties.getTintColor(fluid);
        float red = (tint >> 16 & 0xFF) / 255.0F;
        float green = (tint >> 8 & 0xFF) / 255.0F;
        float blue = (tint & 0xFF) / 255.0F;
        float alpha = (tint >>> 24) / 255.0F;
        if (alpha == 0.0F) alpha = 1.0F;
        int left = this.leftPos + contents.x;
        int right = left + contents.width;
        int bottom = this.topPos + contents.y + contents.height;
        int top = bottom - fluidHeight;
        int spriteWidth = sprite.contents().width();
        int spriteHeight = sprite.contents().height();
        int atlasWidth = Math.max(1, Math.round(spriteWidth / (sprite.getU1() - sprite.getU0())));
        int atlasHeight = Math.max(1, Math.round(spriteHeight / (sprite.getV1() - sprite.getV0())));

        // 手工裁切边缘纹理块，避免先换算到屏幕整数坐标后再裁剪造成像素偏移
        graphics.setColor(red, green, blue, alpha);
        try {
            for (int tileY = bottom - 16; tileY > top - 16; tileY -= 16) {
                int drawTop = Math.max(tileY, top);
                int drawBottom = Math.min(tileY + 16, bottom);
                int sourceTop = Math.round((drawTop - tileY) * spriteHeight / 16.0F);
                int sourceBottom = Math.round((drawBottom - tileY) * spriteHeight / 16.0F);
                for (int tileX = left; tileX < right; tileX += 16) {
                    int drawRight = Math.min(tileX + 16, right);
                    int sourceRight = Math.round((drawRight - tileX) * spriteWidth / 16.0F);
                    graphics.blit(
                        sprite.atlasLocation(),
                        tileX,
                        drawTop,
                        drawRight - tileX,
                        drawBottom - drawTop,
                        sprite.getX(),
                        sprite.getY() + sourceTop,
                        sourceRight,
                        sourceBottom - sourceTop,
                        atlasWidth,
                        atlasHeight
                    );
                }
            }
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void renderVerticalSegmentedEnergy(
        GuiGraphics graphics,
        int x,
        int y,
        int width,
        int height,
        int filledHeight,
        boolean creativeOverride
    ) {
        int right = x + width;
        int bottom = y + height;
        renderEnergySegments(
            graphics,
            x,
            y,
            right,
            bottom,
            creativeOverride ? 0xFF20002E : 0xFF300000,
            creativeOverride ? 0xFF310044 : 0xFF3D0000,
            creativeOverride ? 0xFF50006D : 0xFF520000,
            creativeOverride ? 0xFF3A0054 : 0xFF400000
        );
        int clampedHeight = Math.clamp(filledHeight, 0, height);
        if (clampedHeight == 0) return;
        renderEnergySegments(
            graphics,
            x,
            bottom - clampedHeight,
            right,
            bottom,
            creativeOverride ? 0xFF5B007D : 0xFF660000,
            creativeOverride ? 0xFF7800A5 : 0xFF7D0000,
            creativeOverride ? 0xFFB52CFF : 0xFFA90000,
            creativeOverride ? 0xFF8615BE : 0xFF830000
        );
    }

    private static void renderEnergySegments(
        GuiGraphics graphics,
        int left,
        int top,
        int right,
        int bottom,
        int darkColor,
        int topColor,
        int centerColor,
        int bottomColor
    ) {
        int center = left + (right - left) / 2;
        for (int stripeY = bottom - 1; stripeY >= top; stripeY--) {
            if (((bottom - stripeY - 1) & 1) == 0) {
                graphics.fill(left, stripeY, right, stripeY + 1, darkColor);
                continue;
            }
            for (int pixelX = left; pixelX < right; pixelX++) {
                int color = pixelX <= center
                    ? interpolateColor(topColor, centerColor, pixelX - left, Math.max(1, center - left))
                    : interpolateColor(
                        centerColor,
                        bottomColor,
                        pixelX - center,
                        Math.max(1, right - center - 1)
                    );
                graphics.fill(pixelX, stripeY, pixelX + 1, stripeY + 1, color);
            }
        }
    }

    private static int interpolateColor(int from, int to, int numerator, int denominator) {
        int clampedNumerator = Math.clamp(numerator, 0, denominator);
        int alpha = interpolateChannel(from >>> 24, to >>> 24, clampedNumerator, denominator);
        int red = interpolateChannel(from >> 16 & 0xFF, to >> 16 & 0xFF, clampedNumerator, denominator);
        int green = interpolateChannel(from >> 8 & 0xFF, to >> 8 & 0xFF, clampedNumerator, denominator);
        int blue = interpolateChannel(from & 0xFF, to & 0xFF, clampedNumerator, denominator);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int interpolateChannel(int from, int to, int numerator, int denominator) {
        return (from * (denominator - numerator) + to * numerator + denominator / 2) / denominator;
    }

    private @Nullable PlasticMoldingChamberBlockEntity clientChamber() {
        if (this.minecraft.level == null) return null;
        return this.minecraft.level.getBlockEntity(this.menu.chamberPos())
            instanceof PlasticMoldingChamberBlockEntity chamber ? chamber : null;
    }

    private Component waitReasonText(MoldingWaitReason reason) {
        if (reason == MoldingWaitReason.PUMPING) {
            return Component.translatable(
                displayedFormingMode() == MoldingFormingMode.PRINTING
                    ? "screen.anvilcraftplasticraft.molding.wait.pumping_printing_component"
                    : "screen.anvilcraftplasticraft.molding.wait.pumping",
                displayedFormingMode() == MoldingFormingMode.PRINTING
                    ? PlasticMoldingChamberBlockEntity.PRINTING_PUMP_RATE
                    : PlasticMoldingChamberBlockEntity.MOLDING_PUMP_RATE
            );
        }
        return Component.translatable(
            "screen.anvilcraftplasticraft.molding.wait." + reason.name().toLowerCase(Locale.ROOT)
        );
    }

    private void renderOverlays(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.leftOverlayOpen) {
            List<MoldingProductType> types = filteredProductTypes();
            clampTypeScroll(types.size());
            for (int row = 0; row < CATEGORY_VISIBLE_ROWS; row++) {
                int index = this.typeScrollRow + row;
                if (index >= types.size()) break;
                MoldingProductType type = types.get(index);
                GuiRect entry = categoryEntryRect(row);
                boolean selected = type.id().equals(this.editor.model().requestedType());
                drawThreeFrameAtlas(
                    graphics,
                    entry,
                    CATEGORY_ENTRY_BUTTON,
                    selected,
                    this.editor.writable(),
                    mouseX,
                    mouseY
                );
                renderItemIcon(
                    graphics,
                    typeIcon(type.id()),
                    entry.x + 2,
                    entry.y + 2,
                    10
                );
                String label = marqueeLabel(
                    Component.translatable(type.translationKey()).getString(),
                    35
                );
                graphics.drawString(
                    this.font,
                    label,
                    this.leftPos + entry.x + 15,
                    this.topPos + entry.y + 3,
                    selected ? 0xFFFFFFFF : 0xFFF0F0F0,
                    false
                );
                tooltip(entry, mouseX, mouseY, type.translationKey());
            }
            renderTypeScrollbar(graphics, types.size(), mouseX, mouseY);
        }
        if (this.rightOverlayOpen) {
            renderBlueprintOverlay(graphics, mouseX, mouseY);
        }
    }

    private static GuiRect categoryEntryRect(int row) {
        if (row < 0 || row >= CATEGORY_VISIBLE_ROWS) {
            throw new IllegalArgumentException("Category row is outside the list");
        }
        return new GuiRect(
            CATEGORY_LIST_RECT.x,
            CATEGORY_LIST_RECT.y + row * CATEGORY_ENTRY_HEIGHT,
            CATEGORY_LIST_RECT.width,
            CATEGORY_ENTRY_HEIGHT
        );
    }

    private List<MoldingProductType> filteredProductTypes() {
        String query = this.leftSearch == null ? "" : this.leftSearch.getValue().trim().toLowerCase(Locale.ROOT);
        return MoldingProductTypes.values().stream()
            .filter(type -> query.isEmpty()
                || Component.translatable(type.translationKey()).getString().toLowerCase(Locale.ROOT).contains(query)
                || type.id().toString().toLowerCase(Locale.ROOT).contains(query))
            .toList();
    }

    private void renderTypeScrollbar(GuiGraphics graphics, int itemCount, int mouseX, int mouseY) {
        int handleY = typeScrollbarHandleY(itemCount);
        GuiRect handle = new GuiRect(
            CATEGORY_SCROLL_TRACK_X,
            handleY,
            CATEGORY_SCROLL_HANDLE_WIDTH,
            CATEGORY_SCROLL_HANDLE_HEIGHT
        );
        boolean hovered = typeMaxScroll(itemCount) > 0
            && handle.contains(this.leftPos, this.topPos, mouseX, mouseY);
        graphics.blit(
            BLUEPRINT_SCROLL_HANDLE,
            this.leftPos + handle.x,
            this.topPos + handle.y,
            hovered ? CATEGORY_SCROLL_HANDLE_WIDTH : 0,
            0,
            CATEGORY_SCROLL_HANDLE_WIDTH,
            CATEGORY_SCROLL_HANDLE_HEIGHT,
            BLUEPRINT_SCROLL_HANDLE_TEXTURE_WIDTH,
            CATEGORY_SCROLL_HANDLE_HEIGHT
        );
    }

    private void renderDiskControls(GuiGraphics graphics, int mouseX, int mouseY) {
        GuiRect load = new GuiRect(265, 162, 16, 16);
        GuiRect store = new GuiRect(265, 180, 16, 16);
        ItemStack disk = diskStack();
        boolean hasDisk = MoldingBlueprintDisk.isStructureDisk(disk);
        boolean hasBlueprint = MoldingBlueprintDisk.read(disk).isPresent();
        drawThreeFrameAtlas(
            graphics,
            load,
            LOAD_BUTTON,
            isChamberControlPressed(ChamberControl.DISK_LOAD, load, mouseX, mouseY),
            hasBlueprint,
            mouseX,
            mouseY
        );
        drawThreeFrameAtlas(
            graphics,
            store,
            SAVE_BUTTON,
            isChamberControlPressed(ChamberControl.DISK_STORE, store, mouseX, mouseY),
            hasDisk,
            mouseX,
            mouseY
        );
        if (load.contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            this.hoveredControlTooltip = List.of(Component.translatable(
                !hasDisk
                    ? "screen.anvilcraftplasticraft.molding.disk.missing"
                    : hasBlueprint
                        ? "screen.anvilcraftplasticraft.molding.disk.load"
                        : "screen.anvilcraftplasticraft.molding.disk.invalid"
            ));
        } else if (store.contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            this.hoveredControlTooltip = List.of(Component.translatable(
                !hasDisk
                    ? "screen.anvilcraftplasticraft.molding.disk.missing"
                    : "screen.anvilcraftplasticraft.molding.disk.store"
            ));
        }
    }

    private void renderModelControl(GuiGraphics graphics, GuiRect rect, int mouseX, int mouseY) {
        GuiRect contents = renderPanelControl(
            graphics,
            ChamberControl.MODEL,
            rect,
            MODEL_BUTTON,
            this.rightOverlayOpen,
            mouseX,
            mouseY
        );
        MoldingOrientationCubeRenderer.render(
            graphics,
            viewportTransform(),
            this.leftPos + contents.x,
            this.topPos + contents.y,
            contents.width,
            contents.height
        );
        tooltip(rect, mouseX, mouseY, "screen.anvilcraftplasticraft.molding.json");
    }

    private GuiRect renderPanelControl(
        GuiGraphics graphics,
        ChamberControl control,
        GuiRect rect,
        ResourceLocation texture,
        boolean open,
        int mouseX,
        int mouseY
    ) {
        boolean hovered = rect.contains(this.leftPos, this.topPos, mouseX, mouseY);
        boolean pressed = isChamberControlPressed(control, rect, mouseX, mouseY);
        int frame;
        if (pressed) {
            frame = 2;
        } else if (open && !hovered) {
            frame = 3;
        } else {
            frame = hovered ? 1 : 0;
        }
        drawAtlas(graphics, rect, texture, 4, frame);
        return new GuiRect(rect.x + 2, rect.y + 2 + (pressed ? 1 : 0), 12, 10);
    }

    private void renderCategoryControl(GuiGraphics graphics, GuiRect rect, int mouseX, int mouseY) {
        GuiRect contents = renderPanelControl(
            graphics,
            ChamberControl.CATEGORY,
            rect,
            CATEGORY_BUTTON,
            this.leftOverlayOpen,
            mouseX,
            mouseY
        );
        GuiRect icon = new GuiRect(contents.x + 1, contents.y, 10, 10);
        renderClippedItemIcon(graphics, typeIcon(this.editor.model().requestedType()), icon, 10);
    }

    private void renderClippedItemIcon(
        GuiGraphics graphics,
        ItemStack icon,
        GuiRect clip,
        int size
    ) {
        graphics.flush();
        enableLogicalScissor(clip);
        try {
            renderItemIcon(graphics, icon, clip.x, clip.y, size);
            graphics.flush();
        } finally {
            RenderSystem.disableScissor();
        }
    }

    private void renderItemIcon(
        GuiGraphics graphics,
        ItemStack icon,
        int x,
        int y,
        int size
    ) {
        float scale = size / 16.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(this.leftPos + x, this.topPos + y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderFakeItem(icon, 0, 0);
        graphics.pose().popPose();
    }

    private void enableLogicalScissor(GuiRect rect) {
        double guiScale = this.minecraft.getWindow().getGuiScale();
        int left = (int) Math.round(this.layoutTransform.toScreenX(this.leftPos + rect.x) * guiScale);
        int top = (int) Math.round(this.layoutTransform.toScreenY(this.topPos + rect.y) * guiScale);
        int right = (int) Math.round(
            this.layoutTransform.toScreenX(this.leftPos + rect.x + rect.width) * guiScale
        );
        int bottom = (int) Math.round(
            this.layoutTransform.toScreenY(this.topPos + rect.y + rect.height) * guiScale
        );
        RenderSystem.enableScissor(
            left,
            this.minecraft.getWindow().getHeight() - bottom,
            Math.max(1, right - left),
            Math.max(1, bottom - top)
        );
    }

    private static ItemStack typeIcon(ResourceLocation type) {
        if (MoldingProductTypes.CHEST_ID.equals(type)) return Items.CHEST.getDefaultInstance();
        if (MoldingProductTypes.TANK_ID.equals(type)) return ModBlocks.FLUID_TANK.asStack();
        if (MoldingProductTypes.ANVIL_ID.equals(type)) return Items.ANVIL.getDefaultInstance();
        if (MoldingProductTypes.TRAY_ID.equals(type)) return MoldingTrayTypeIcon.stack();
        if (MoldingProductTypes.ALLAY_HARD_HAT_ID.equals(type)) return MoldingHardHatTypeIcon.stack();
        return PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
    }

    private void renderBlueprintOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        List<MoldingBlueprintSummary> filtered = filteredBlueprints();
        clampBlueprintScroll(filtered.size());
        int visibleRows = Math.min(
            BLUEPRINT_VISIBLE_ROWS,
            Math.max(0, filtered.size() - this.blueprintScrollRow)
        );
        for (int row = 0; row < visibleRows; row++) {
            GuiRect entry = blueprintEntryRect(row);
            int index = this.blueprintScrollRow + row;
            MoldingBlueprintSummary summary = filtered.get(index);
            boolean selected = summary.fileId().equals(this.selectedBlueprintFileId);
            boolean renaming = summary.fileId().equals(this.renamingBlueprintFileId)
                && this.blueprintRenameField != null
                && this.blueprintRenameField.isVisible();
            drawThreeFrameAtlas(graphics, entry, MODEL_ENTRY_BUTTON, selected, true, mouseX, mouseY);
            if (!renaming) {
                String label = marqueeLabel(summary.displayName(), 48);
                graphics.drawString(
                    this.font,
                    label,
                    this.leftPos + entry.x + 2,
                    this.topPos + entry.y + 4,
                    summary.pinned() ? 0xFFFFD56A : 0xFFF0F0F0,
                    false
                );
            }
            if (!renaming && entry.contains(this.leftPos, this.topPos, mouseX, mouseY)) {
                this.hoveredControlTooltip = List.of(
                    Component.literal(summary.displayName()),
                    Component.translatable(
                        "screen.anvilcraftplasticraft.molding.blueprint.owner",
                        summary.ownerName()
                    ),
                    Component.translatable(
                        "screen.anvilcraftplasticraft.molding.blueprint.hash",
                        summary.modelHash().substring(0, 12)
                    )
                );
            }
        }
        renderBlueprintScrollbar(graphics, filtered.size(), mouseX, mouseY);
        renderBlueprintActions(graphics, mouseX, mouseY);
    }

    private void renderBlueprintScrollbar(GuiGraphics graphics, int itemCount, int mouseX, int mouseY) {
        int handleY = blueprintScrollbarHandleY(itemCount);
        int handleX = BLUEPRINT_SCROLLBAR_X;
        GuiRect handle = new GuiRect(
            handleX,
            handleY,
            BLUEPRINT_SCROLL_HANDLE_WIDTH,
            BLUEPRINT_SCROLL_HANDLE_HEIGHT
        );
        boolean hovered = blueprintMaxScroll(itemCount) > 0
            && handle.contains(this.leftPos, this.topPos, mouseX, mouseY);
        graphics.blit(
            BLUEPRINT_SCROLL_HANDLE,
            this.leftPos + handleX,
            this.topPos + handleY,
            hovered ? BLUEPRINT_SCROLL_HANDLE_WIDTH : 0,
            0,
            BLUEPRINT_SCROLL_HANDLE_WIDTH,
            BLUEPRINT_SCROLL_HANDLE_HEIGHT,
            BLUEPRINT_SCROLL_HANDLE_TEXTURE_WIDTH,
            BLUEPRINT_SCROLL_HANDLE_HEIGHT
        );
    }

    private void renderBlueprintActions(GuiGraphics graphics, int mouseX, int mouseY) {
        Optional<MoldingBlueprintSummary> selected = selectedBlueprint();
        boolean enabled = selected.isPresent();
        GuiRect pin = new GuiRect(358, 184, 13, 13);
        GuiRect copy = new GuiRect(373, 184, 13, 13);
        GuiRect open = new GuiRect(388, 184, 13, 13);
        GuiRect delete = new GuiRect(403, 184, 13, 13);
        drawThreeFrameAtlas(
            graphics,
            pin,
            PIN_TO_TOP_BUTTON,
            isChamberControlPressed(ChamberControl.BLUEPRINT_PIN, pin, mouseX, mouseY),
            enabled,
            mouseX,
            mouseY
        );
        drawThreeFrameAtlas(
            graphics,
            copy,
            COPY_SMALL_BUTTON,
            isChamberControlPressed(ChamberControl.BLUEPRINT_COPY, copy, mouseX, mouseY),
            enabled,
            mouseX,
            mouseY
        );
        drawShiftThreeFrameAtlas(
            graphics,
            open,
            FILE_BUTTON,
            isChamberControlPressed(ChamberControl.BLUEPRINT_FILE, open, mouseX, mouseY),
            true,
            mouseX,
            mouseY
        );
        drawShiftThreeFrameAtlas(
            graphics,
            delete,
            DELETE_BUTTON,
            isChamberControlPressed(ChamberControl.BLUEPRINT_DELETE, delete, mouseX, mouseY),
            enabled,
            mouseX,
            mouseY
        );
        tooltip(pin, mouseX, mouseY, "screen.anvilcraftplasticraft.molding.blueprint.pin");
        tooltip(copy, mouseX, mouseY, "screen.anvilcraftplasticraft.molding.blueprint.copy");
        tooltip(
            open,
            mouseX,
            mouseY,
            Screen.hasShiftDown()
                ? "screen.anvilcraftplasticraft.molding.blueprint.open"
                : "screen.anvilcraftplasticraft.molding.blueprint.refresh"
        );
        tooltip(
            delete,
            mouseX,
            mouseY,
            Screen.hasShiftDown()
                ? "screen.anvilcraftplasticraft.molding.blueprint.delete"
                : "screen.anvilcraftplasticraft.molding.blueprint.delete_confirm"
        );
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
        MoldingGizmoBasis gizmoBasis = this.editor.gizmoBasis();
        double gizmoWorldUnitsPerPixel = transform.worldUnitsPerPixel(gizmoOrigin);
        Vector3d gizmoCameraDirection = transform.directionToCamera(gizmoOrigin);
        boolean hasSelection = !this.editor.selection().isEmpty();
        boolean printingWorkspace = displayedFormingMode() == MoldingFormingMode.PRINTING;
        boolean sceneViewChanged = Math.abs(gizmoWorldUnitsPerPixel - this.cachedGizmoWorldUnitsPerPixel) > 1.0E-9D
            || gizmoCameraDirection.distanceSquared(this.cachedGizmoCameraDirection) > 1.0E-12D;
        boolean gizmoViewChanged = hasSelection && sceneViewChanged;
        boolean rebuildStaticScene = this.cachedScene == null
            || this.cachedSceneModel != this.editor.model();
        boolean rebuildViewDependent = rebuildStaticScene
            || !this.cachedSelection.equals(this.editor.selection())
            || this.cachedInvalid != this.editor.invalidPreview()
            || this.cachedShowGrid != this.showGrid
            || this.cachedShowAxes != this.showAxes
            || this.cachedPrintingWorkspace != printingWorkspace
            || !Objects.equals(this.cachedHoveredElement, hover.elementId())
            || sceneViewChanged;
        boolean rebuildGizmo = hasSelection
            && (rebuildViewDependent
            || this.cachedTool != this.editor.tool()
            || gizmoViewChanged
            || this.cachedHoveredGizmoAxis != hover.gizmoAxis());
        if (!rebuildViewDependent && !rebuildGizmo) return;
        if (rebuildStaticScene) {
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
                transform,
                printingWorkspace
            );
        } else if (rebuildViewDependent) {
            this.cachedScene = MoldingSceneBuilder.replaceViewDependent(
                this.cachedScene,
                ++this.dynamicSceneRevision,
                this.editor.model(),
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
                transform,
                printingWorkspace
            );
        } else {
            this.cachedScene = MoldingSceneBuilder.replaceGizmo(
                this.cachedScene,
                ++this.dynamicSceneRevision,
                gizmoOrigin,
                this.editor.tool(),
                gizmoBasis,
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
        this.cachedPrintingWorkspace = printingWorkspace;
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
            MoldingGizmoBasis gizmoBasis = this.editor.gizmoBasis();
            Optional<MoldingHitTester.GizmoHit> gizmo = MoldingHitTester.hitGizmo(
                ray,
                gizmoOrigin,
                this.editor.tool(),
                gizmoBasis,
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
            MoldingGizmoBasis gizmoBasis = this.editor.gizmoBasis();
            Optional<MoldingHitTester.GizmoHit> gizmo = MoldingHitTester.hitGizmo(
                ray,
                gizmoOrigin,
                this.editor.tool(),
                gizmoBasis,
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
                    this.modelDragAxisDirection = gizmoBasis.direction(gizmo.get().axis());
                    this.modelDragWorldUnitsPerLocalUnit = gizmoBasis.worldUnitsPerLocalUnit(gizmo.get().axis());
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
            UUID id = hit.get().id();
            this.editor.select(id, Screen.hasShiftDown());
            this.elementSelectionAnchor = id;
            ensurePrimaryVisible();
        } else if (!Screen.hasShiftDown()) {
            this.editor.clearSelection();
            this.elementSelectionAnchor = null;
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
            this.elementSelectionAnchor = hit.id();
            if (this.editor.selection().contains(hit.id())) return;
            this.editor.select(hit.id(), false);
            ensurePrimaryVisible();
            refreshNumericFields();
        });
    }

    private void updateModelDrag(double mouseX, double mouseY) {
        if (this.modelDragAxis == null
            || this.modelDragTransform == null
            || this.modelDragOrigin == null
            || this.modelDragAxisDirection == null) {
            return;
        }
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
                new Vector3d(this.modelDragAxisDirection)
                    .mul(referenceLength * this.modelDragDirection)
                    .add(center)
            );
            if (start.isEmpty() || end.isEmpty()) return;
            Vector2d screenAxis = new Vector2d(end.get()).sub(start.get());
            double length = screenAxis.length();
            if (length < 1.0E-5D) return;
            screenAxis.div(length);
            amount = (deltaX * screenAxis.x + deltaY * screenAxis.y)
                * referenceLength
                / length
                / this.modelDragWorldUnitsPerLocalUnit;
        }
        this.editor.updateDrag(amount);
        refreshNumericFields();
    }

    private boolean handleFixedControlClick(double mouseX, double mouseY, int button) {
        if (handleTypeScrollbarClick(mouseX, mouseY, button)) return true;
        if (handleBlueprintScrollbarClick(mouseX, mouseY, button)) return true;
        if (handleBlueprintOverlayClick(mouseX, mouseY, button)) return true;
        if (handleTypeOverlayClick(mouseX, mouseY, button)) return true;
        if (handleElementListClick(mouseX, mouseY, button)) return true;
        if (handleReleaseControlClick(mouseX, mouseY, button)) return true;
        if (handleChamberControlClick(mouseX, mouseY, button)) return true;
        GuiRect fluidBar = new GuiRect(5, 161, 24, 37);
        if (button == 1 && fluidBar.contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            if (this.menu.writable()) sendMachineAction(MoldingMachineAction.INTERACT_STAGING_FLUID, 0);
            return true;
        }
        if (button != 0) return false;
        for (MoldingProductionMode mode : MoldingProductionMode.values()) {
            GuiRect modeRect = new GuiRect(47 + mode.protocolId() * 12, 162, 10, 10);
            if (!modeRect.contains(this.leftPos, this.topPos, mouseX, mouseY)) continue;
            if (this.menu.writable()) sendMachineAction(MoldingMachineAction.SET_MODE, mode.protocolId());
            return true;
        }
        for (int index = 0; index < TOOL_BUTTONS.length; index++) {
            GuiRect rect = new GuiRect(9, 19 + index * 18, 16, 16);
            if (!rect.contains(this.leftPos, this.topPos, mouseX, mouseY)) continue;
            if (!this.editor.writable()) return true;
            return this.toolButtons.press(TOOL_BUTTONS[index]);
        }
        return false;
    }

    private boolean handleTypeOverlayClick(double mouseX, double mouseY, int button) {
        if (!this.leftOverlayOpen || button != 0) return false;
        List<MoldingProductType> types = filteredProductTypes();
        for (int row = 0; row < CATEGORY_VISIBLE_ROWS; row++) {
            GuiRect entry = categoryEntryRect(row);
            if (!entry.contains(this.leftPos, this.topPos, mouseX, mouseY)) continue;
            int index = this.typeScrollRow + row;
            if (index >= types.size() || !this.editor.writable()) return true;
            this.editor.setRequestedType(types.get(index).id());
            return true;
        }
        return false;
    }

    private boolean handleBlueprintOverlayClick(double mouseX, double mouseY, int button) {
        if (!this.rightOverlayOpen || button != 0 && button != 1) return false;
        List<MoldingBlueprintSummary> filtered = filteredBlueprints();
        for (int row = 0; row < BLUEPRINT_VISIBLE_ROWS; row++) {
            GuiRect entry = blueprintEntryRect(row);
            if (!entry.contains(this.leftPos, this.topPos, mouseX, mouseY)) continue;
            int index = this.blueprintScrollRow + row;
            if (index >= filtered.size()) return true;
            MoldingBlueprintSummary summary = filtered.get(index);
            if (button == 1) {
                openBlueprintRename(summary);
                return true;
            }
            this.selectedBlueprintFileId = summary.fileId();
            writeSelectedBlueprintToDisk(summary);
            return true;
        }
        if (button != 0) return false;

        Optional<MoldingBlueprintSummary> selected = selectedBlueprint();
        GuiRect pin = new GuiRect(358, 184, 13, 13);
        GuiRect copy = new GuiRect(373, 184, 13, 13);
        GuiRect open = new GuiRect(388, 184, 13, 13);
        GuiRect delete = new GuiRect(403, 184, 13, 13);
        @Nullable MoldingBlueprintSummary summary = selected.orElse(null);
        if (open.contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            pressChamberControl(ChamberControl.BLUEPRINT_FILE, summary);
            return true;
        }
        if (pin.contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            pressChamberControl(ChamberControl.BLUEPRINT_PIN, summary);
            return true;
        }
        if (copy.contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            pressChamberControl(ChamberControl.BLUEPRINT_COPY, summary);
            return true;
        }
        if (delete.contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            pressChamberControl(ChamberControl.BLUEPRINT_DELETE, summary);
            return true;
        }
        return false;
    }

    private void loadDiskModel() {
        ItemStack disk = diskStack();
        Optional<MoldingBlueprint> blueprint = MoldingBlueprintDisk.read(disk);
        if (blueprint.isEmpty()) return;
        String token = MoldingBlueprintDisk.stateToken(disk);
        if (this.menu.machineState() != PlasticMoldingMachineState.EDITABLE
            || this.menu.batchFluidAmount() != 0
            || this.menu.moldedClayBalls() != 0) {
            sendDiskAction(MoldingBlueprintDiskAction.LOAD, "", 0L, token, false);
            return;
        }
        boolean hasDraft = !this.menu.model().isEmpty();
        if (hasDraft && !confirmationMatches(MoldingBlueprintDiskAction.LOAD, "", token)) {
            this.diskConfirmation = new DiskConfirmation(
                MoldingBlueprintDiskAction.LOAD,
                "",
                token,
                this.menu.revision()
            );
            displayBlueprintMessage("confirm_model_overwrite");
            return;
        }
        sendDiskAction(MoldingBlueprintDiskAction.LOAD, "", 0L, token, hasDraft);
    }

    private void storeDiskModel() {
        ItemStack disk = diskStack();
        if (!MoldingBlueprintDisk.isStructureDisk(disk)) return;
        String token = MoldingBlueprintDisk.stateToken(disk);
        boolean occupied = !token.isEmpty();
        if (occupied && !confirmationMatches(MoldingBlueprintDiskAction.STORE, "", token)) {
            this.diskConfirmation = new DiskConfirmation(
                MoldingBlueprintDiskAction.STORE,
                "",
                token,
                this.menu.revision()
            );
            displayBlueprintMessage("confirm_disk_overwrite");
            return;
        }
        sendDiskAction(MoldingBlueprintDiskAction.STORE, "", 0L, token, occupied);
    }

    private void writeSelectedBlueprintToDisk(MoldingBlueprintSummary summary) {
        ItemStack disk = diskStack();
        if (!MoldingBlueprintDisk.isStructureDisk(disk)) {
            displayBlueprintMessage("missing_disk");
            return;
        }
        String token = MoldingBlueprintDisk.stateToken(disk);
        MoldingBlueprint existing = MoldingBlueprintDisk.read(disk).orElse(null);
        boolean different = !token.isEmpty()
            && (existing == null
                || !existing.modelHash().equals(summary.modelHash())
                || !existing.name().equals(summary.name()));
        if (different && !confirmationMatches(MoldingBlueprintDiskAction.WRITE_SHARED, summary.fileId(), token)) {
            this.diskConfirmation = new DiskConfirmation(
                MoldingBlueprintDiskAction.WRITE_SHARED,
                summary.fileId(),
                token,
                this.menu.revision()
            );
            displayOverwriteMessage(
                existing == null
                    ? Component.translatable("screen.anvilcraftplasticraft.molding.disk.other_data").getString()
                    : existing.name(),
                existing == null ? null : existing.model(),
                summary.displayName(),
                summary.modelHash()
            );
            return;
        }
        sendDiskAction(
            MoldingBlueprintDiskAction.WRITE_SHARED,
            summary.fileId(),
            summary.fileRevision(),
            token,
            different
        );
    }

    private void sendDiskAction(
        MoldingBlueprintDiskAction action,
        String fileId,
        long fileRevision,
        String token,
        boolean confirmed
    ) {
        this.diskConfirmation = null;
        PacketDistributor.sendToServer(new MoldingBlueprintDiskPacket(
            this.menu.chamberPos(),
            this.menu.sessionId(),
            this.menu.revision(),
            action,
            fileId,
            fileRevision,
            token,
            confirmed
        ));
    }

    private void sendLibraryAction(
        MoldingBlueprintLibraryAction action,
        MoldingBlueprintSummary summary
    ) {
        PacketDistributor.sendToServer(new MoldingBlueprintLibraryPacket(
            this.menu.chamberPos(),
            this.menu.sessionId(),
            this.menu.revision(),
            action,
            summary.fileId(),
            summary.fileRevision()
        ));
    }

    private void sendOpenFolder(@Nullable MoldingBlueprintSummary summary) {
        PacketDistributor.sendToServer(new MoldingBlueprintLibraryPacket(
            this.menu.chamberPos(),
            this.menu.sessionId(),
            this.menu.revision(),
            MoldingBlueprintLibraryAction.OPEN,
            summary == null ? "" : summary.fileId(),
            summary == null ? 0L : summary.fileRevision()
        ));
    }

    private void sendRefreshFolder() {
        PacketDistributor.sendToServer(new MoldingBlueprintLibraryPacket(
            this.menu.chamberPos(),
            this.menu.sessionId(),
            this.menu.revision(),
            MoldingBlueprintLibraryAction.REFRESH,
            "",
            0L
        ));
    }

    private boolean handleChamberControlClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        for (ChamberControl control : MAIN_CHAMBER_CONTROLS) {
            GuiRect rect = chamberControlRect(control);
            if (!rect.contains(this.leftPos, this.topPos, mouseX, mouseY)) continue;
            pressChamberControl(control, null);
            return true;
        }
        return false;
    }

    private void pressChamberControl(ChamberControl control, @Nullable MoldingBlueprintSummary summary) {
        this.pressedChamberControl = null;
        this.pressedBlueprint = null;
        if (!chamberControlEnabled(control, summary)) return;
        this.pressedChamberControl = control;
        this.pressedBlueprint = summary;
    }

    private boolean releaseChamberControl(double mouseX, double mouseY) {
        ChamberControl control = this.pressedChamberControl;
        if (control == null) return false;
        @Nullable MoldingBlueprintSummary summary = this.pressedBlueprint;
        this.pressedChamberControl = null;
        this.pressedBlueprint = null;
        GuiRect rect = chamberControlRect(control);
        if (rect.contains(this.leftPos, this.topPos, mouseX, mouseY)
            && chamberControlEnabled(control, summary)) {
            executeChamberControl(control, summary);
        }
        return true;
    }

    private boolean isChamberControlPressed(ChamberControl control, GuiRect rect, int mouseX, int mouseY) {
        return this.pressedChamberControl == control
            && rect.contains(this.leftPos, this.topPos, mouseX, mouseY);
    }

    private boolean chamberControlEnabled(ChamberControl control, @Nullable MoldingBlueprintSummary summary) {
        return switch (control) {
            case CATEGORY, MODEL, BLUEPRINT_FILE -> true;
            case OPERATE -> this.menu.writable()
                && !this.editor.pending()
                && !this.editor.invalidPreview()
                && !this.menu.printingDischargeOpen()
                && (this.menu.machineState() != PlasticMoldingMachineState.EDITABLE
                || lockConstraintsSatisfied());
            case DISK_LOAD -> MoldingBlueprintDisk.read(diskStack()).isPresent();
            case DISK_STORE -> MoldingBlueprintDisk.isStructureDisk(diskStack());
            case BLUEPRINT_PIN, BLUEPRINT_COPY, BLUEPRINT_DELETE -> summary != null;
        };
    }

    private static GuiRect chamberControlRect(ChamberControl control) {
        return switch (control) {
            case OPERATE -> new GuiRect(47, 143, 34, 16);
            case CATEGORY -> new GuiRect(9, 114, 16, 16);
            case MODEL -> new GuiRect(324, 171, 16, 16);
            case DISK_LOAD -> new GuiRect(265, 162, 16, 16);
            case DISK_STORE -> new GuiRect(265, 180, 16, 16);
            case BLUEPRINT_PIN -> new GuiRect(358, 184, 13, 13);
            case BLUEPRINT_COPY -> new GuiRect(373, 184, 13, 13);
            case BLUEPRINT_FILE -> new GuiRect(388, 184, 13, 13);
            case BLUEPRINT_DELETE -> new GuiRect(403, 184, 13, 13);
        };
    }

    private void executeChamberControl(ChamberControl control, @Nullable MoldingBlueprintSummary summary) {
        switch (control) {
            case OPERATE -> {
                if (this.menu.machineState() == PlasticMoldingMachineState.EDITABLE
                    && !this.lockConfirmation) {
                    this.lockConfirmation = true;
                    this.lockClayRequirement = currentClayRequirement();
                } else {
                    this.lockConfirmation = false;
                    this.lockClayRequirement = 0;
                    sendMachineAction(MoldingMachineAction.TOGGLE_LOCK, 0);
                }
            }
            case CATEGORY -> {
                this.leftOverlayOpen = !this.leftOverlayOpen;
                if (this.leftOverlayOpen) {
                    this.rightOverlayOpenedLast = false;
                    if (this.rightOverlayOpen && !canDisplayBothOverlays()) this.rightOverlayOpen = false;
                    ensureSelectedTypeVisible();
                }
                updateLayout();
            }
            case MODEL -> {
                this.rightOverlayOpen = !this.rightOverlayOpen;
                if (this.rightOverlayOpen) {
                    this.rightOverlayOpenedLast = true;
                    if (this.leftOverlayOpen && !canDisplayBothOverlays()) this.leftOverlayOpen = false;
                    PacketDistributor.sendToServer(new MoldingBlueprintListRequestPacket(
                        this.menu.chamberPos(),
                        this.menu.sessionId()
                    ));
                }
                updateLayout();
            }
            case DISK_LOAD -> loadDiskModel();
            case DISK_STORE -> storeDiskModel();
            case BLUEPRINT_PIN -> {
                if (summary != null) sendLibraryAction(MoldingBlueprintLibraryAction.PIN, summary);
            }
            case BLUEPRINT_COPY -> {
                if (summary != null) sendLibraryAction(MoldingBlueprintLibraryAction.COPY, summary);
            }
            case BLUEPRINT_FILE -> {
                if (Screen.hasShiftDown()) sendOpenFolder(summary);
                else sendRefreshFolder();
            }
            case BLUEPRINT_DELETE -> {
                if (summary == null) return;
                if (Screen.hasShiftDown()) {
                    sendLibraryAction(MoldingBlueprintLibraryAction.DELETE, summary);
                } else {
                    displayBlueprintMessage("confirm_blueprint_delete");
                }
            }
        }
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
                if (button == 0) pressReleaseControl(ReleaseControl.NEW_CUBE);
                return true;
            }
            SceneObject object = objects.get(objectIndex);
            if (button == 1) {
                if (!object.element()) return false;
                this.editor.select(object.id(), false);
                this.elementSelectionAnchor = object.id();
                ensurePrimaryVisible();
                refreshNumericFields();
                openContextMenu(mouseX, mouseY);
                return true;
            }
            selectElementListObject(objects, object);
            refreshNumericFields();
            return true;
        }
        return false;
    }

    private void selectElementListObject(List<SceneObject> objects, SceneObject object) {
        if (!Screen.hasShiftDown()) {
            this.editor.select(object.id(), Screen.hasControlDown());
            this.elementSelectionAnchor = object.id();
            return;
        }
        List<UUID> orderedIds = objects.stream().map(SceneObject::id).toList();
        UUID anchor = this.elementSelectionAnchor;
        if (anchor == null || !orderedIds.contains(anchor)) {
            anchor = this.editor.selection().primary().filter(orderedIds::contains).orElse(object.id());
            this.elementSelectionAnchor = anchor;
        }
        this.editor.selectRange(orderedIds, anchor, object.id(), Screen.hasControlDown());
    }

    private boolean handleReleaseControlClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        for (ReleaseControl control : EDITOR_RELEASE_CONTROLS) {
            GuiRect rect = releaseControlRect(control);
            if (rect == null || !rect.contains(this.leftPos, this.topPos, mouseX, mouseY)) continue;
            pressReleaseControl(control);
            return true;
        }
        return false;
    }

    private void pressReleaseControl(ReleaseControl control) {
        if (releaseControlEnabled(control)) this.pressedReleaseControl = control;
    }

    private boolean releasePressedControl(double mouseX, double mouseY) {
        if (this.pressedReleaseControl == null) return false;
        ReleaseControl control = this.pressedReleaseControl;
        this.pressedReleaseControl = null;
        GuiRect rect = releaseControlRect(control);
        if (rect != null
            && rect.contains(this.leftPos, this.topPos, mouseX, mouseY)
            && releaseControlEnabled(control)) {
            executeReleaseControl(control);
        }
        return true;
    }

    private boolean releaseControlEnabled(ReleaseControl control) {
        boolean canEdit = this.editor.writable() && !this.editor.pending();
        return switch (control) {
            case NEW_CUBE -> canEdit;
            case UNDO -> canEdit && this.menu.canUndo();
            case COPY -> this.editor.hasSelectedElements();
            case CUT -> canEdit && this.editor.hasSelectedElements();
            case PASTE -> canEdit && this.editor.hasClipboardElements();
        };
    }

    @Nullable
    private GuiRect releaseControlRect(ReleaseControl control) {
        return switch (control) {
            case NEW_CUBE -> visibleCreateCubeButtonRect();
            case UNDO -> new GuiRect(269, 136, 16, 16);
            case COPY -> new GuiRect(287, 136, 16, 16);
            case CUT -> new GuiRect(305, 136, 16, 16);
            case PASTE -> new GuiRect(323, 136, 16, 16);
        };
    }

    private void executeReleaseControl(ReleaseControl control) {
        switch (control) {
            case NEW_CUBE -> createCubeAtViewportOrigin();
            case UNDO -> this.editor.undo();
            case COPY -> this.editor.copySelection();
            case CUT -> {
                if (this.editor.cutSelection()) {
                    syncElementSelectionAnchor();
                    refreshNumericFields();
                }
            }
            case PASTE -> {
                if (this.editor.paste()) {
                    syncElementSelectionAnchor();
                    ensurePrimaryVisible();
                    refreshNumericFields();
                }
            }
        }
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

    private boolean handleBlueprintScrollbarClick(double mouseX, double mouseY, int button) {
        if (!this.rightOverlayOpen || button != 0) return false;
        GuiRect scrollbar = new GuiRect(
            BLUEPRINT_SCROLLBAR_X,
            BLUEPRINT_SCROLLBAR_Y,
            BLUEPRINT_SCROLLBAR_WIDTH,
            93
        );
        if (!scrollbar.contains(this.leftPos, this.topPos, mouseX, mouseY)) return false;
        List<MoldingBlueprintSummary> filtered = filteredBlueprints();
        clampBlueprintScroll(filtered.size());
        if (blueprintMaxScroll(filtered.size()) == 0) return true;
        int handleY = blueprintScrollbarHandleY(filtered.size());
        double localY = mouseY - this.topPos;
        GuiRect handle = new GuiRect(
            BLUEPRINT_SCROLLBAR_X,
            handleY,
            BLUEPRINT_SCROLLBAR_WIDTH,
            BLUEPRINT_SCROLL_HANDLE_HEIGHT
        );
        if (handle.contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            this.blueprintScrollbarGrabOffset = localY - handleY;
        } else {
            this.blueprintScrollbarGrabOffset = BLUEPRINT_SCROLL_HANDLE_HEIGHT * 0.5D;
            updateBlueprintScrollFromPointer(mouseY);
        }
        this.blueprintScrollbarDragging = true;
        return true;
    }

    private boolean handleTypeScrollbarClick(double mouseX, double mouseY, int button) {
        if (!this.leftOverlayOpen || button != 0) return false;
        GuiRect scrollbar = new GuiRect(
            CATEGORY_SCROLLBAR_X,
            CATEGORY_SCROLLBAR_Y,
            CATEGORY_SCROLLBAR_WIDTH,
            CATEGORY_SCROLLBAR_HEIGHT
        );
        if (!scrollbar.contains(this.leftPos, this.topPos, mouseX, mouseY)) return false;
        List<MoldingProductType> types = filteredProductTypes();
        clampTypeScroll(types.size());
        if (typeMaxScroll(types.size()) == 0) return true;
        int handleY = typeScrollbarHandleY(types.size());
        double localY = mouseY - this.topPos;
        GuiRect handle = new GuiRect(
            CATEGORY_SCROLL_TRACK_X,
            handleY,
            CATEGORY_SCROLL_HANDLE_WIDTH,
            CATEGORY_SCROLL_HANDLE_HEIGHT
        );
        if (handle.contains(this.leftPos, this.topPos, mouseX, mouseY)) {
            this.typeScrollbarGrabOffset = localY - handleY;
        } else {
            this.typeScrollbarGrabOffset = CATEGORY_SCROLL_HANDLE_HEIGHT * 0.5D;
            updateTypeScrollFromPointer(mouseY);
        }
        this.typeScrollbarDragging = true;
        return true;
    }

    private void updateTypeScrollFromPointer(double mouseY) {
        int itemCount = filteredProductTypes().size();
        int maximum = typeMaxScroll(itemCount);
        if (maximum == 0) {
            this.typeScrollRow = 0;
            return;
        }
        int travel = CATEGORY_SCROLL_TRACK_HEIGHT - CATEGORY_SCROLL_HANDLE_HEIGHT;
        double offset = mouseY - this.topPos - this.typeScrollbarGrabOffset - CATEGORY_SCROLL_TRACK_Y;
        this.typeScrollRow = Math.clamp((int) Math.round(offset * maximum / travel), 0, maximum);
    }

    private void updateBlueprintScrollFromPointer(double mouseY) {
        int itemCount = filteredBlueprints().size();
        int maximum = blueprintMaxScroll(itemCount);
        if (maximum == 0) {
            this.blueprintScrollRow = 0;
            return;
        }
        int travel = BLUEPRINT_SCROLL_TRACK_HEIGHT - BLUEPRINT_SCROLL_HANDLE_HEIGHT;
        double offset = mouseY - this.topPos - this.blueprintScrollbarGrabOffset - BLUEPRINT_SCROLL_TRACK_Y;
        this.blueprintScrollRow = Math.clamp((int) Math.round(offset * maximum / travel), 0, maximum);
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
        return ELEMENT_SCROLL_TRACK_X;
    }

    private static int elementMaxScroll(int objectCount) {
        return Math.max(0, elementRowCount(objectCount) - ELEMENT_VISIBLE_ROWS);
    }

    private static int elementRowCount(int objectCount) {
        return Math.max(1, (objectCount + 1) / 2);
    }

    private void clampBlueprintScroll(int itemCount) {
        this.blueprintScrollRow = Math.clamp(this.blueprintScrollRow, 0, blueprintMaxScroll(itemCount));
    }

    private void clampTypeScroll(int itemCount) {
        this.typeScrollRow = Math.clamp(this.typeScrollRow, 0, typeMaxScroll(itemCount));
    }

    private int typeScrollbarHandleY(int itemCount) {
        int maximum = typeMaxScroll(itemCount);
        if (maximum == 0) return CATEGORY_SCROLL_TRACK_Y;
        int travel = CATEGORY_SCROLL_TRACK_HEIGHT - CATEGORY_SCROLL_HANDLE_HEIGHT;
        return CATEGORY_SCROLL_TRACK_Y + travel * this.typeScrollRow / maximum;
    }

    private static int typeMaxScroll(int itemCount) {
        return Math.max(0, itemCount - CATEGORY_VISIBLE_ROWS);
    }

    private int blueprintScrollbarHandleY(int itemCount) {
        int maximum = blueprintMaxScroll(itemCount);
        if (maximum == 0) return BLUEPRINT_SCROLL_TRACK_Y;
        int travel = BLUEPRINT_SCROLL_TRACK_HEIGHT - BLUEPRINT_SCROLL_HANDLE_HEIGHT;
        return BLUEPRINT_SCROLL_TRACK_Y + travel * this.blueprintScrollRow / maximum;
    }

    private static int blueprintMaxScroll(int itemCount) {
        return Math.max(0, itemCount - BLUEPRINT_VISIBLE_ROWS);
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
            case DELETE -> {
                if (this.editor.deleteSelection()) syncElementSelectionAnchor();
            }
            case DELETE_ALL -> {
                if (this.editor.deleteAllElements()) syncElementSelectionAnchor();
            }
            case HIDE -> this.editor.toggleHidden();
            case LOCK -> this.editor.toggleLocked();
            case GROUP -> {
                if (this.editor.groupSelection()) syncElementSelectionAnchor();
            }
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
            actions.add(context(
                ContextActionKind.DELETE_ALL,
                "delete_all",
                () -> !this.editor.model().elements().isEmpty()
            ));
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
        syncElementSelectionAnchor();
        ensurePrimaryVisible();
        refreshNumericFields();
        return true;
    }

    private void syncElementSelectionAnchor() {
        this.elementSelectionAnchor = this.editor.selection().primary().orElse(null);
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
        if (this.leftOverlayOpen && this.rightOverlayOpen && !canDisplayBothOverlays()) {
            if (this.rightOverlayOpenedLast) this.leftOverlayOpen = false;
            else this.rightOverlayOpen = false;
        }
        int visibleMinX = this.leftOverlayOpen ? -(OVERLAY_WIDTH - 1) : 0;
        int visibleMaxX = this.rightOverlayOpen ? LOGICAL_WIDTH + OVERLAY_WIDTH - 1 : LOGICAL_WIDTH;
        int visibleWidth = visibleMaxX - visibleMinX;
        this.layoutTransform = MoldingGuiTransform.fit(
            this.width,
            this.height,
            visibleWidth,
            LOGICAL_HEIGHT,
            LAYOUT_PADDING,
            this.minecraft.getWindow().getGuiScale()
        );
        this.leftPos = -visibleMinX;
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
        if (this.blueprintRenameField != null) {
            this.blueprintRenameField.setVisible(this.rightOverlayOpen && !this.renamingBlueprintFileId.isEmpty());
            if (this.blueprintRenameField.isVisible()) positionBlueprintRenameField();
            else if (!this.rightOverlayOpen) closeBlueprintRename();
        }
    }

    private static GuiRect blueprintEntryRect(int row) {
        return new GuiRect(357, 90 + row * 15, 52, 15);
    }

    private boolean canDisplayBothOverlays() {
        double guiScale = this.minecraft.getWindow().getGuiScale();
        int availableWidth = Math.max(1, this.width - LAYOUT_PADDING * 2);
        int availableHeight = Math.max(1, this.height - LAYOUT_PADDING * 2);
        return availableWidth * guiScale >= FULL_LAYOUT_WIDTH
            && availableHeight * guiScale >= LOGICAL_HEIGHT;
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

    private void openBlueprintRename(MoldingBlueprintSummary summary) {
        if (this.blueprintRenameField == null) return;
        closeRename();
        this.renamingBlueprintFileId = summary.fileId();
        this.renamingBlueprintRevision = summary.fileRevision();
        this.blueprintRenameField.setValue(summary.displayName());
        this.blueprintRenameField.setVisible(true);
        positionBlueprintRenameField();
        this.blueprintRenameField.setFocused(true);
        this.setFocused(this.blueprintRenameField);
        this.blueprintRenameField.moveCursorToEnd(false);
    }

    private void positionBlueprintRenameField() {
        if (this.blueprintRenameField == null || this.renamingBlueprintFileId.isEmpty()) return;
        List<MoldingBlueprintSummary> filtered = filteredBlueprints();
        for (int row = 0; row < BLUEPRINT_VISIBLE_ROWS; row++) {
            int index = this.blueprintScrollRow + row;
            if (index >= filtered.size()) break;
            if (!filtered.get(index).fileId().equals(this.renamingBlueprintFileId)) continue;
            GuiRect entry = blueprintEntryRect(row);
            this.blueprintRenameField.setX(this.leftPos + entry.x + 2);
            this.blueprintRenameField.setY(this.topPos + entry.y + 2);
            return;
        }
    }

    private void commitBlueprintRename() {
        if (this.blueprintRenameField == null || !this.blueprintRenameField.isVisible()) return;
        String fileId = this.renamingBlueprintFileId;
        long revision = this.renamingBlueprintRevision;
        String requested = this.blueprintRenameField.getValue().strip();
        closeBlueprintRename();
        if (fileId.isEmpty() || requested.isEmpty()) return;
        Optional<MoldingBlueprintSummary> current = this.menu.blueprints().stream()
            .filter(summary -> summary.fileId().equals(fileId))
            .findFirst();
        if (current.isEmpty()) return;
        MoldingBlueprintSummary summary = current.get();
        if (requested.equals(summary.displayName()) && requested.equals(summary.name())) return;
        PacketDistributor.sendToServer(new MoldingBlueprintRenamePacket(
            this.menu.chamberPos(),
            this.menu.sessionId(),
            this.menu.revision(),
            fileId,
            revision,
            requested
        ));
    }

    private void closeBlueprintRename() {
        this.renamingBlueprintFileId = "";
        this.renamingBlueprintRevision = 0L;
        if (this.blueprintRenameField == null) return;
        this.blueprintRenameField.setFocused(false);
        if (this.getFocused() == this.blueprintRenameField) this.setFocused(null);
        this.blueprintRenameField.setVisible(false);
    }

    private void synchronizeMenuSnapshot() {
        boolean editable = this.menu.modelEditable();
        if (this.seenMenuModel == this.menu.model()
            && this.seenMenuRevision == this.menu.revision()
            && this.seenWritable == editable
            && this.seenWriterName.equals(this.menu.writerName())) {
            return;
        }
        this.seenMenuModel = this.menu.model();
        this.seenMenuRevision = this.menu.revision();
        this.seenWritable = editable;
        this.seenWriterName = this.menu.writerName();
        this.editor.syncAuthoritative(this.menu.model(), this.menu.revision(), editable);
        if (!editable) {
            this.lockConfirmation = false;
            this.lockClayRequirement = 0;
            closeRename();
        }
        refreshNumericFields();
        ensurePrimaryVisible();
        if (this.leftOverlayOpen) ensureSelectedTypeVisible();
    }

    private void synchronizeTitleMessage() {
        long generation = this.menu.titleMessageGeneration();
        if (generation == this.seenTitleMessageGeneration) return;
        this.seenTitleMessageGeneration = generation;
        this.titleMessageTicks = TITLE_MESSAGE_TICKS;
    }

    private void synchronizeBlueprintList() {
        if (this.seenBlueprintListGeneration == this.menu.blueprintListGeneration()) return;
        this.seenBlueprintListGeneration = this.menu.blueprintListGeneration();
        String requestedSelection = this.menu.selectedBlueprintFileId();
        if (!requestedSelection.isEmpty()) this.selectedBlueprintFileId = requestedSelection;
        boolean selectionStillExists = this.menu.blueprints().stream()
            .anyMatch(summary -> summary.fileId().equals(this.selectedBlueprintFileId));
        if (!selectionStillExists) this.selectedBlueprintFileId = "";
        if (!this.renamingBlueprintFileId.isEmpty()
            && this.menu.blueprints().stream()
                .noneMatch(summary -> summary.fileId().equals(this.renamingBlueprintFileId))) {
            closeBlueprintRename();
        }
        this.diskConfirmation = null;
        List<MoldingBlueprintSummary> filtered = filteredBlueprints();
        clampBlueprintScroll(filtered.size());
        if (!requestedSelection.isEmpty()) ensureSelectedBlueprintVisible(filtered);
    }

    private List<MoldingBlueprintSummary> filteredBlueprints() {
        String query = this.rightSearch == null ? "" : this.rightSearch.getValue().strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return this.menu.blueprints();
        return this.menu.blueprints().stream()
            .filter(summary -> summary.fileId().toLowerCase(Locale.ROOT).contains(query)
                || summary.displayName().toLowerCase(Locale.ROOT).contains(query)
                || summary.name().toLowerCase(Locale.ROOT).contains(query)
                || summary.ownerName().toLowerCase(Locale.ROOT).contains(query))
            .toList();
    }

    private Optional<MoldingBlueprintSummary> selectedBlueprint() {
        return this.menu.blueprints().stream()
            .filter(summary -> summary.fileId().equals(this.selectedBlueprintFileId))
            .findFirst();
    }

    private void ensureSelectedBlueprintVisible(List<MoldingBlueprintSummary> filtered) {
        for (int index = 0; index < filtered.size(); index++) {
            if (!filtered.get(index).fileId().equals(this.selectedBlueprintFileId)) continue;
            if (index < this.blueprintScrollRow) this.blueprintScrollRow = index;
            else if (index >= this.blueprintScrollRow + BLUEPRINT_VISIBLE_ROWS) {
                this.blueprintScrollRow = index - BLUEPRINT_VISIBLE_ROWS + 1;
            }
            return;
        }
    }

    private void ensureSelectedTypeVisible() {
        List<MoldingProductType> filtered = filteredProductTypes();
        ResourceLocation selectedType = this.editor.model().requestedType();
        boolean selectedVisible = filtered.stream().anyMatch(type -> type.id().equals(selectedType));
        if (!selectedVisible && this.leftSearch != null && !this.leftSearch.getValue().isEmpty()) {
            this.leftSearch.setValue("");
            filtered = filteredProductTypes();
        }
        clampTypeScroll(filtered.size());
        for (int index = 0; index < filtered.size(); index++) {
            if (!filtered.get(index).id().equals(selectedType)) continue;
            if (index < this.typeScrollRow) this.typeScrollRow = index;
            else if (index >= this.typeScrollRow + CATEGORY_VISIBLE_ROWS) {
                this.typeScrollRow = index - CATEGORY_VISIBLE_ROWS + 1;
            }
            return;
        }
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
        Map<UUID, MoldingGroup> groups = this.editor.model().groupMap();
        for (MoldingGroup group : this.editor.model().groups()) {
            objects.add(new SceneObject(
                group.id(),
                group.name(),
                "G",
                "screen.anvilcraftplasticraft.molding.element.group",
                false,
                group.visible(),
                group.locked()
            ));
        }
        for (MoldingElement element : elements) {
            objects.add(sceneObject(element, groups));
        }
        return objects;
    }

    private int elementEntryCount() {
        return sceneObjects().size() + 1;
    }

    private static SceneObject sceneObject(MoldingElement element, Map<UUID, MoldingGroup> groups) {
        boolean visible = element.visible();
        boolean locked = element.locked();
        MoldingGroup group = element.groupId().map(groups::get).orElse(null);
        while (group != null) {
            visible &= group.visible();
            locked |= group.locked();
            group = group.parentId().map(groups::get).orElse(null);
        }
        return new SceneObject(
            element.id(),
            element.name(),
            "C",
            "screen.anvilcraftplasticraft.molding.element.cube",
            true,
            visible,
            locked
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

    private void sendMachineAction(MoldingMachineAction action, int value) {
        PacketDistributor.sendToServer(new MoldingMachinePacket(
            this.menu.chamberPos(),
            this.menu.sessionId(),
            this.menu.revision(),
            action,
            value
        ));
    }

    private int currentClayRequirement() {
        if (this.menu.formingMode() == MoldingFormingMode.PRINTING) return 0;
        try {
            BakedMoldingModel baked = MoldingModelBaker.bake(this.editor.model());
            return baked.analysis().empty() ? 0 : baked.analysis().clayBallRequirement();
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private Optional<MoldingTypeValidation> currentTypeValidation() {
        EditableMoldingModel model = this.editor.model();
        if (this.cachedTypeValidationModel == model) return this.cachedTypeValidation;
        BakedMoldingModel baked = this.cachedSceneModel == model ? this.cachedBaked : null;
        if (baked == null) {
            try {
                baked = MoldingModelBaker.bake(model);
            } catch (RuntimeException exception) {
                this.cachedTypeValidationModel = model;
                this.cachedTypeValidation = Optional.empty();
                return Optional.empty();
            }
        }
        this.cachedTypeValidationModel = model;
        this.cachedTypeValidation = Optional.of(MoldingProductTypes.validate(
            model.requestedType(),
            model,
            baked
        ));
        return this.cachedTypeValidation;
    }

    private boolean modelFitsCurrentWorkspace() {
        EditableMoldingModel model = this.editor.model();
        MoldingFormingMode formingMode = displayedFormingMode();
        if (this.cachedWorkspaceBoundsModel == model && this.cachedWorkspaceFormingMode == formingMode) {
            return this.cachedModelFitsWorkspace;
        }
        this.cachedWorkspaceBoundsModel = model;
        this.cachedWorkspaceFormingMode = formingMode;
        this.cachedModelFitsWorkspace = MoldingModelBounds.all(model)
            .map(bounds -> formingMode == MoldingFormingMode.PRINTING
                ? bounds.fitsPrintingWorkspace()
                : bounds.fitsWorkspaceSize())
            .orElse(true);
        return this.cachedModelFitsWorkspace;
    }

    private String workspaceLimitMessageKey() {
        return displayedFormingMode() == MoldingFormingMode.PRINTING
            ? "message.anvilcraftplasticraft.molding.printing_model_too_large"
            : "message.anvilcraftplasticraft.molding.model_too_large";
    }

    private ItemStack resourceStack() {
        return this.menu.getSlot(PlasticMoldingChamberBlockEntity.RESOURCE_SLOT).getItem();
    }

    private boolean creativeOverrideActive() {
        return this.menu.creativeOverrideLocked()
            || PlasticMoldingChamberBlockEntity.isCreativeGenerator(this.resourceStack());
    }

    private boolean typeOverrideActive() {
        return this.menu.typeOverrideCommitted()
            || PlasticMoldingChamberBlockEntity.isCreativeGenerator(this.resourceStack())
            || PlasticMoldingChamberBlockEntity.isMultiphaseTranscendium(this.resourceStack());
    }

    private boolean overrideVisualActive() {
        return creativeOverrideActive() || typeOverrideActive();
    }

    private boolean lockConstraintsSatisfied() {
        if (missingPrintingComponent()) return false;
        if (!modelFitsCurrentWorkspace() && !creativeOverrideActive()) return false;
        return currentTypeValidation()
            .map(validation -> validation.valid() || typeOverrideActive())
            .orElse(false);
    }

    private boolean missingPrintingComponent() {
        return MoldingProductTypes.requiresPrinting(this.editor.model().requestedType())
            && !this.menu.printingComponentPresent();
    }

    private MoldingFormingMode displayedFormingMode() {
        return this.menu.machineState() == PlasticMoldingMachineState.EDITABLE
            ? this.menu.formingMode()
            : this.menu.cycleFormingMode();
    }

    private void renderOverrideBorder(GuiGraphics graphics, GuiRect rect, boolean hovered, boolean pressed) {
        int color = hovered ? 0xFFFFB0FF : 0xFFE94CFF;
        int left = this.leftPos + rect.x + 1;
        int right = this.leftPos + rect.x + rect.width - 1;
        int top = this.topPos + rect.y + 1 + (pressed ? 1 : 0);
        int bottom = this.topPos + rect.y + rect.height - 1;
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, top, left + 1, bottom, color);
        graphics.fill(right - 1, top, right, bottom, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
    }

    private void renderOverrideParticles(GuiGraphics graphics, GuiRect rect, float partialTick) {
        double clock = (this.minecraft.level == null
            ? Util.getMillis() / 50.0D
            : this.minecraft.level.getGameTime() + partialTick);
        int left = this.leftPos + rect.x + 3;
        int right = this.leftPos + rect.x + rect.width - 3;
        int bottom = this.topPos + rect.y + rect.height - 3;
        for (int index = 0; index < 5; index++) {
            double phase = (clock * 0.045D + index * 0.197D) % 1.0D;
            if (phase < 0.0D) phase += 1.0D;
            int x = left + Math.floorMod(index * 11 + (int) Math.floor(clock * 0.25D), Math.max(1, right - left));
            x += (int) Math.round(Math.sin(clock * 0.08D + index) * 1.2D);
            x = Math.clamp(x, left, right - 1);
            int y = bottom - 1 - (int) Math.round(phase * Math.max(1, rect.height - 6));
            int alpha = 90 + (int) Math.round(90.0D * (1.0D - Math.abs(phase - 0.5D) * 2.0D));
            int color = alpha << 24 | 0xD85AFF;
            graphics.fill(x, y, x + 1, y + 1, color);
            if ((index & 1) == 0) graphics.fill(x + 1, y, x + 2, y + 1, color);
        }
    }

    private static List<Component> typeValidationTooltip(MoldingTypeValidation validation) {
        return List.of(
            Component.translatable("screen.anvilcraftplasticraft.molding.type_invalid")
                .withStyle(ChatFormatting.RED),
            Component.translatable("message.anvilcraftplasticraft.molding.type_" + validation.reason())
                .withStyle(ChatFormatting.RED)
        );
    }

    private ItemStack diskStack() {
        return this.menu.getSlot(PlasticMoldingChamberBlockEntity.DISK_SLOT).getItem();
    }

    private boolean confirmationMatches(
        MoldingBlueprintDiskAction action,
        String fileId,
        String diskToken
    ) {
        return this.diskConfirmation != null
            && this.diskConfirmation.action == action
            && this.diskConfirmation.fileId.equals(fileId)
            && this.diskConfirmation.diskToken.equals(diskToken)
            && this.diskConfirmation.modelRevision == this.menu.revision();
    }

    private void displayOverwriteMessage(
        String oldName,
        @Nullable EditableMoldingModel oldModel,
        String newName,
        String newHash
    ) {
        String oldHash = oldModel == null
            ? "?"
            : MoldingModelBaker.bake(oldModel).modelHash().substring(0, 8);
        this.menu.showTitleMessage(Component.translatable(
            "message.anvilcraftplasticraft.molding.confirm_blueprint_overwrite",
            oldName,
            oldHash,
            newName,
            newHash.substring(0, Math.min(8, newHash.length()))
        ));
    }

    private void displayBlueprintMessage(String reason) {
        this.menu.showTitleMessage(Component.translatable("message.anvilcraftplasticraft.molding." + reason));
    }

    private boolean isDiskConfirmationControl(double mouseX, double mouseY) {
        if (new GuiRect(265, 162, 16, 34).contains(this.leftPos, this.topPos, mouseX, mouseY)) return true;
        return this.rightOverlayOpen
            && new GuiRect(357, 90, 52, 90).contains(this.leftPos, this.topPos, mouseX, mouseY);
    }

    private String marqueeLabel(String value, int width) {
        if (this.font.width(value) <= width) return value;
        String cycle = value + "   ";
        int start = (int) (Util.getMillis() / 300L % cycle.length());
        String rotated = cycle.substring(start) + cycle.substring(0, start) + cycle;
        return this.font.plainSubstrByWidth(rotated, width);
    }

    private void drawThreeFrameAtlas(
        GuiGraphics graphics,
        GuiRect rect,
        ResourceLocation atlas,
        boolean pressed,
        boolean enabled,
        int mouseX,
        int mouseY
    ) {
        int frame = pressed
            ? 2
            : enabled && rect.contains(this.leftPos, this.topPos, mouseX, mouseY) ? 1 : 0;
        drawAtlas(graphics, rect, atlas, 3, frame);
        if (!enabled) drawDisabledOverlay(graphics, rect);
    }

    private void drawShiftThreeFrameAtlas(
        GuiGraphics graphics,
        GuiRect rect,
        ResourceLocation atlas,
        boolean pressed,
        boolean enabled,
        int mouseX,
        int mouseY
    ) {
        int frame = pressed
            ? 2
            : enabled && rect.contains(this.leftPos, this.topPos, mouseX, mouseY) ? 1 : 0;
        if (Screen.hasShiftDown()) frame += 3;
        drawAtlas(graphics, rect, atlas, 6, frame);
        if (!enabled) drawDisabledOverlay(graphics, rect);
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
            || this.renameField != null && this.renameField.isVisible() && this.renameField.isMouseOver(mouseX, mouseY)
            || this.blueprintRenameField != null
                && this.blueprintRenameField.isVisible()
                && this.blueprintRenameField.isMouseOver(mouseX, mouseY);
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

    private record DiskConfirmation(
        MoldingBlueprintDiskAction action,
        String fileId,
        String diskToken,
        long modelRevision
    ) {
    }

    private record ViewportHover(@Nullable UUID elementId, @Nullable MoldingAxis gizmoAxis) {
        private static final ViewportHover NONE = new ViewportHover(null, null);
    }

    private record SceneObject(
        UUID id,
        String name,
        String kind,
        String typeKey,
        boolean element,
        boolean visible,
        boolean locked
    ) {
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

    private enum ReleaseControl {
        NEW_CUBE,
        UNDO,
        COPY,
        CUT,
        PASTE
    }

    private enum ChamberControl {
        OPERATE,
        CATEGORY,
        MODEL,
        DISK_LOAD,
        DISK_STORE,
        BLUEPRINT_PIN,
        BLUEPRINT_COPY,
        BLUEPRINT_FILE,
        BLUEPRINT_DELETE
    }

    private enum ContextActionKind {
        TAKEOVER,
        CREATE_CUBE,
        RENAME,
        DELETE,
        DELETE_ALL,
        HIDE,
        LOCK,
        GROUP,
        CENTER_PIVOT,
        FOCUS,
        RESET_CAMERA
    }
}
