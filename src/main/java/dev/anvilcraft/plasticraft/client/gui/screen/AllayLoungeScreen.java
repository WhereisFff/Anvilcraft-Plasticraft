package dev.anvilcraft.plasticraft.client.gui.screen;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.allay.AllayClearanceStrategy;
import dev.anvilcraft.plasticraft.allay.AllayShortageStrategy;
import dev.anvilcraft.plasticraft.allay.AllayWorkRecord;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.client.gui.AllayLoungeButton;
import dev.anvilcraft.plasticraft.client.gui.AllayLoungePreview;
import dev.anvilcraft.plasticraft.inventory.AllayLoungeMenu;
import dev.anvilcraft.plasticraft.network.AllayLoungeClearancePacket;
import dev.anvilcraft.plasticraft.network.AllayLoungeRecallPacket;
import dev.anvilcraft.plasticraft.network.AllayLoungeReleasePacket;
import dev.anvilcraft.plasticraft.network.AllayLoungeSettingsPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public class AllayLoungeScreen extends AbstractContainerScreen<AllayLoungeMenu> {
    private static final ResourceLocation TEXTURE =
        AnvilcraftPlasticraft.of("textures/gui/background/allay_lounge.png");
    private static final int BUTTON_SIZE = 16;
    private static final int RECALL_BUTTON_SIZE = 12;
    private static final int GRID_COLUMNS = 4;
    private static final double DRAG_THRESHOLD_SQUARED = 9.0D;

    private final Map<UUID, AllayLoungePreview> previews = new HashMap<>();
    @Nullable
    private UUID pressedAllay;
    private double pressX;
    private double pressY;
    private double lastDragX;
    private boolean dragged;

    public AllayLoungeScreen(AllayLoungeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 216;
        this.imageHeight = 240;
        this.titleLabelY = 2;
    }

    @Override
    protected void init() {
        super.init();
        this.cancelDrag();
        this.addLoungeButton(71, 23, RECALL_BUTTON_SIZE, "return_home", "allay_lounge.recall", null,
            () -> PacketDistributor.sendToServer(new AllayLoungeRecallPacket(this.menu.loungePos())));
        this.addLoungeButton(39, 83, BUTTON_SIZE, "pause", "allay.strategy.pause",
            () -> this.menu.shortageStrategy() == AllayShortageStrategy.PAUSE,
            () -> PacketDistributor.sendToServer(
                new AllayLoungeSettingsPacket(this.menu.loungePos(), AllayShortageStrategy.PAUSE)));
        this.addLoungeButton(39, 99, BUTTON_SIZE, "skip", "allay.strategy.skip",
            () -> this.menu.shortageStrategy() == AllayShortageStrategy.SKIP,
            () -> PacketDistributor.sendToServer(
                new AllayLoungeSettingsPacket(this.menu.loungePos(), AllayShortageStrategy.SKIP)));
        this.addLoungeButton(59, 83, BUTTON_SIZE, "clear_area", "allay.clearance.clear_area",
            () -> this.menu.clearanceStrategy() == AllayClearanceStrategy.CLEAR_AREA,
            () -> PacketDistributor.sendToServer(
                new AllayLoungeClearancePacket(this.menu.loungePos(), AllayClearanceStrategy.CLEAR_AREA)));
        this.addLoungeButton(59, 99, BUTTON_SIZE, "keep_blank", "allay.clearance.keep_blank",
            () -> this.menu.clearanceStrategy() == AllayClearanceStrategy.KEEP_BLANK,
            () -> PacketDistributor.sendToServer(
                new AllayLoungeClearancePacket(this.menu.loungePos(), AllayClearanceStrategy.KEEP_BLANK)));
    }

    private void addLoungeButton(
        int x, int y, int size, String texture, String translation, @Nullable BooleanSupplier selected, Runnable action
    ) {
        this.addRenderableWidget(new AllayLoungeButton(
            this.leftPos + x, this.topPos + y, size,
            AnvilcraftPlasticraft.of("textures/gui/button/allay/" + texture + ".png"),
            Component.translatable("screen.anvilcraftplasticraft." + translation), selected, action
        ));
    }

    @Override
    public void setFocused(@Nullable GuiEventListener listener) {
        if (this.getFocused() != listener && this.getFocused() instanceof AllayLoungeButton loungeButton) {
            loungeButton.cancelPress();
        }
        super.setFocused(listener);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        if (this.pressedAllay != null || !this.menu.getCarried().isEmpty()) return;
        int hovered = this.hoveredCard(mouseX, mouseY);
        List<AllayWorkRecord> hosted = this.hosted();
        if (hovered >= 0 && hovered < hosted.size()) {
            AllayWorkRecord record = hosted.get(hovered);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(record.customName().orElseGet(() -> record.hardHat().getHoverName())
                .copy().withStyle(ChatFormatting.WHITE));
            ItemStack tool = record.heldTool();
            Component toolName = tool.isEmpty()
                ? Component.translatable("screen.anvilcraftplasticraft.allay.tool.none") : tool.getHoverName();
            tooltip.add(Component.translatable("screen.anvilcraftplasticraft.allay_lounge.tool",
                toolName.copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("screen.anvilcraftplasticraft.allay_lounge.release")
                .withStyle(ChatFormatting.GRAY));
            graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        } else if (this.menu.getSlot(0).getItem().isEmpty() && this.isHovering(
            AllayLoungeMenu.DISK_SLOT_X, AllayLoungeMenu.DISK_SLOT_Y, 16, 16, mouseX, mouseY
        )) {
            graphics.renderTooltip(this.font,
                Component.translatable("screen.anvilcraftplasticraft.allay_lounge.disk"), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
        graphics.blit(TEXTURE,
            this.leftPos + AllayLoungeMenu.DISK_SLOT_X - 1, this.topPos + AllayLoungeMenu.DISK_SLOT_Y - 1,
            AllayLoungeMenu.PLAYER_INVENTORY_X - 1, AllayLoungeMenu.PLAYER_INVENTORY_Y - 1,
            18, 18, 256, 256);
        this.renderHostedAllays(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title,
            (this.imageWidth - this.font.width(this.title)) / 2, this.titleLabelY, 0x404040, false);
    }

    private void renderHostedAllays(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.minecraft == null || this.minecraft.level == null) return;
        List<AllayWorkRecord> hosted = this.hosted();
        Set<UUID> present = new HashSet<>();
        long now = Util.getNanos();
        int hovered = this.hoveredCard(mouseX, mouseY);
        for (int index = 0; index < Math.min(hosted.size(), AllayLoungeBlockEntity.HOST_CAPACITY); index++) {
            AllayWorkRecord record = hosted.get(index);
            present.add(record.entityId());
            AllayLoungePreview preview = this.previews.computeIfAbsent(record.entityId(),
                id -> new AllayLoungePreview(this.minecraft.level, record, now));
            preview.updateRecord(record);
            int x = this.leftPos + AllayLoungeMenu.CARD_GRID_X + index % GRID_COLUMNS * AllayLoungeMenu.CARD_SPACING;
            int y = this.topPos + AllayLoungeMenu.CARD_GRID_Y + index / GRID_COLUMNS * AllayLoungeMenu.CARD_SPACING;
            if (index == hovered || record.entityId().equals(this.pressedAllay)) {
                graphics.fill(x, y, x + AllayLoungeMenu.CARD_SIZE, y + AllayLoungeMenu.CARD_SIZE, 0x28FFF2B0);
            }
            preview.render(graphics, x, y, AllayLoungeMenu.CARD_SIZE, now);
        }
        this.previews.keySet().retainAll(present);
        if (this.pressedAllay != null && !present.contains(this.pressedAllay)) this.cancelDrag();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int card = this.hoveredCard(mouseX, mouseY);
        List<AllayWorkRecord> hosted = this.hosted();
        if (button == 0 && this.menu.getCarried().isEmpty() && card >= 0 && card < hosted.size()) {
            this.pressedAllay = hosted.get(card).entityId();
            this.pressX = mouseX;
            this.pressY = mouseY;
            this.lastDragX = mouseX;
            this.dragged = false;
            AllayLoungePreview preview = this.previews.get(this.pressedAllay);
            if (preview != null) preview.rotation().beginDrag(Util.getNanos());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.getFocused() instanceof AllayLoungeButton loungeButton && loungeButton.isPressed()) {
            return true;
        }
        if (button != 0 || this.pressedAllay == null) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        double offsetX = mouseX - this.pressX;
        double offsetY = mouseY - this.pressY;
        this.dragged |= offsetX * offsetX + offsetY * offsetY > DRAG_THRESHOLD_SQUARED;
        AllayLoungePreview preview = this.previews.get(this.pressedAllay);
        if (this.dragged && preview != null) {
            preview.rotation().drag(mouseX - this.lastDragX, Util.getNanos());
            this.lastDragX = mouseX;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.getFocused() instanceof AllayLoungeButton loungeButton && loungeButton.isPressed()) {
            // 消费按钮的松开事件，防止容器继续处理拖拽落点，误操作物品槽或丢出手中物品。
            this.setDragging(false);
            return loungeButton.mouseReleased(mouseX, mouseY, button);
        }
        if (button != 0 || this.pressedAllay == null) return super.mouseReleased(mouseX, mouseY, button);
        double offsetX = mouseX - this.pressX;
        double offsetY = mouseY - this.pressY;
        this.dragged |= offsetX * offsetX + offsetY * offsetY > DRAG_THRESHOLD_SQUARED;
        int card = this.hoveredCard(mouseX, mouseY);
        List<AllayWorkRecord> hosted = this.hosted();
        // 松开时重新核对 UUID，异步出入库导致卡片移位时不能放错悦灵。
        if (!this.dragged && card >= 0 && card < hosted.size()
            && hosted.get(card).entityId().equals(this.pressedAllay)) {
            PacketDistributor.sendToServer(new AllayLoungeReleasePacket(this.menu.loungePos(), card, this.pressedAllay));
            if (this.minecraft != null) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
        }
        this.cancelDrag();
        return true;
    }

    private void cancelDrag() {
        AllayLoungePreview preview = this.previews.get(this.pressedAllay);
        if (preview != null) preview.rotation().endDrag(Util.getNanos());
        this.pressedAllay = null;
        this.dragged = false;
    }

    @Override
    public void removed() {
        if (this.getFocused() instanceof AllayLoungeButton loungeButton) loungeButton.cancelPress();
        this.cancelDrag();
        this.previews.clear();
        super.removed();
    }

    private List<AllayWorkRecord> hosted() {
        AllayLoungeBlockEntity lounge = this.menu.lounge();
        return lounge == null ? List.of() : lounge.hosted();
    }

    private int hoveredCard(double mouseX, double mouseY) {
        double localX = mouseX - this.leftPos - AllayLoungeMenu.CARD_GRID_X;
        double localY = mouseY - this.topPos - AllayLoungeMenu.CARD_GRID_Y;
        if (localX < 0.0D || localY < 0.0D) return -1;
        int column = (int) (localX / AllayLoungeMenu.CARD_SPACING);
        int row = (int) (localY / AllayLoungeMenu.CARD_SPACING);
        if (column >= GRID_COLUMNS || row >= GRID_COLUMNS) return -1;
        if (localX % AllayLoungeMenu.CARD_SPACING >= AllayLoungeMenu.CARD_SIZE
            || localY % AllayLoungeMenu.CARD_SPACING >= AllayLoungeMenu.CARD_SIZE) return -1;
        return row * GRID_COLUMNS + column;
    }
}
