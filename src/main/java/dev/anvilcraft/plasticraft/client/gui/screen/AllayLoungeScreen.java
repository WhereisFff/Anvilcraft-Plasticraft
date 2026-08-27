package dev.anvilcraft.plasticraft.client.gui.screen;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.allay.AllayClearanceStrategy;
import dev.anvilcraft.plasticraft.allay.AllayShortageStrategy;
import dev.anvilcraft.plasticraft.allay.AllayWorkRecord;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.inventory.AllayLoungeMenu;
import dev.anvilcraft.plasticraft.network.AllayLoungeClearancePacket;
import dev.anvilcraft.plasticraft.network.AllayLoungeRecallPacket;
import dev.anvilcraft.plasticraft.network.AllayLoungeReleasePacket;
import dev.anvilcraft.plasticraft.network.AllayLoungeSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** 悦灵休息室界面:4x4 托管卡片、磁盘槽、召回、暂停/跳过与整片清场/保留空白。 */
public class AllayLoungeScreen extends AbstractContainerScreen<AllayLoungeMenu> {
    private static final ResourceLocation TEXTURE =
        AnvilcraftPlasticraft.of("textures/gui/background/allay_lounge.png");
    private static final ResourceLocation RETURN_HOME_BUTTON =
        AnvilcraftPlasticraft.of("textures/gui/button/allay/return_home.png");
    private static final ResourceLocation PAUSE_BUTTON =
        AnvilcraftPlasticraft.of("textures/gui/button/allay/pause.png");
    private static final ResourceLocation SKIP_BUTTON =
        AnvilcraftPlasticraft.of("textures/gui/button/allay/skip.png");
    private static final ResourceLocation CLEAR_AREA_BUTTON =
        AnvilcraftPlasticraft.of("textures/gui/button/allay/clear_area.png");
    private static final ResourceLocation KEEP_BLANK_BUTTON =
        AnvilcraftPlasticraft.of("textures/gui/button/allay/keep_blank.png");
    private static final int BUTTON_SIZE = 16;
    private static final int RECALL_BUTTON_X = 9;
    private static final int RECALL_BUTTON_Y = 66;
    private static final int PAUSE_BUTTON_X = 9;
    private static final int SKIP_BUTTON_X = 27;
    private static final int CLEAR_AREA_BUTTON_X = 134;
    private static final int KEEP_BLANK_BUTTON_X = 152;
    private static final int STRATEGY_BUTTON_Y = 84;
    private static final int CARD_SIZE = 18;

    public AllayLoungeScreen(AllayLoungeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 186;
        this.inventoryLabelY = AllayLoungeMenu.PLAYER_INVENTORY_Y - 11;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        if (this.isRecallHovered(mouseX, mouseY)) {
            graphics.renderTooltip(
                this.font,
                Component.translatable("screen.anvilcraftplasticraft.allay_lounge.recall"),
                mouseX,
                mouseY
            );
        }
        if (this.isHovering(PAUSE_BUTTON_X, STRATEGY_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
            graphics.renderTooltip(
                this.font,
                Component.translatable("screen.anvilcraftplasticraft.allay.strategy.pause"),
                mouseX,
                mouseY
            );
        } else if (this.isHovering(SKIP_BUTTON_X, STRATEGY_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
            graphics.renderTooltip(
                this.font,
                Component.translatable("screen.anvilcraftplasticraft.allay.strategy.skip"),
                mouseX,
                mouseY
            );
        } else if (this.isHovering(CLEAR_AREA_BUTTON_X, STRATEGY_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
            graphics.renderTooltip(
                this.font,
                Component.translatable("screen.anvilcraftplasticraft.allay.clearance.clear_area"),
                mouseX,
                mouseY
            );
        } else if (this.isHovering(KEEP_BLANK_BUTTON_X, STRATEGY_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
            graphics.renderTooltip(
                this.font,
                Component.translatable("screen.anvilcraftplasticraft.allay.clearance.keep_blank"),
                mouseX,
                mouseY
            );
        }
        int hovered = this.hoveredCard(mouseX, mouseY);
        List<AllayWorkRecord> hosted = this.hosted();
        if (hovered >= 0 && hovered < hosted.size()) {
            AllayWorkRecord record = hosted.get(hovered);
            ItemStack tool = record.heldTool();
            graphics.renderTooltip(
                this.font,
                tool.isEmpty()
                    ? Component.translatable("screen.anvilcraftplasticraft.allay.tool.none")
                    : tool.getHoverName(),
                mouseX,
                mouseY
            );
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(
            TEXTURE,
            this.leftPos,
            this.topPos,
            0,
            0,
            this.imageWidth,
            this.imageHeight,
            this.imageWidth,
            this.imageHeight
        );
        int frame = this.isRecallHovered(mouseX, mouseY) ? 1 : 0;
        graphics.blit(
            RETURN_HOME_BUTTON,
            this.leftPos + RECALL_BUTTON_X,
            this.topPos + RECALL_BUTTON_Y,
            0,
            frame * BUTTON_SIZE,
            BUTTON_SIZE,
            BUTTON_SIZE,
            BUTTON_SIZE,
            BUTTON_SIZE * 4
        );
        this.renderHostedCards(graphics);
        AllayShortageStrategy strategy = this.menu.shortageStrategy();
        this.renderStrategyButton(graphics, PAUSE_BUTTON, PAUSE_BUTTON_X, strategy == AllayShortageStrategy.PAUSE, mouseX, mouseY);
        this.renderStrategyButton(graphics, SKIP_BUTTON, SKIP_BUTTON_X, strategy == AllayShortageStrategy.SKIP, mouseX, mouseY);
        AllayClearanceStrategy clearance = this.menu.clearanceStrategy();
        this.renderStrategyButton(
            graphics,
            CLEAR_AREA_BUTTON,
            CLEAR_AREA_BUTTON_X,
            clearance == AllayClearanceStrategy.CLEAR_AREA,
            mouseX,
            mouseY
        );
        this.renderStrategyButton(
            graphics,
            KEEP_BLANK_BUTTON,
            KEEP_BLANK_BUTTON_X,
            clearance == AllayClearanceStrategy.KEEP_BLANK,
            mouseX,
            mouseY
        );
    }

    private void renderStrategyButton(
        GuiGraphics graphics,
        ResourceLocation texture,
        int buttonX,
        boolean selected,
        int mouseX,
        int mouseY
    ) {
        int frame = selected ? 2 : this.isHovering(buttonX, STRATEGY_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY) ? 1 : 0;
        graphics.blit(
            texture,
            this.leftPos + buttonX,
            this.topPos + STRATEGY_BUTTON_Y,
            0,
            frame * BUTTON_SIZE,
            BUTTON_SIZE,
            BUTTON_SIZE,
            BUTTON_SIZE,
            BUTTON_SIZE * 4
        );
    }

    private void renderHostedCards(GuiGraphics graphics) {
        List<AllayWorkRecord> hosted = this.hosted();
        for (int index = 0; index < AllayLoungeBlockEntity.HOST_CAPACITY; index++) {
            int column = index % 4;
            int row = index / 4;
            int x = this.leftPos + AllayLoungeMenu.CARD_GRID_X + column * CARD_SIZE;
            int y = this.topPos + AllayLoungeMenu.CARD_GRID_Y + row * CARD_SIZE;
            if (index >= hosted.size()) continue;
            AllayWorkRecord record = hosted.get(index);
            graphics.renderItem(record.hardHat(), x + 1, y + 1);
            if (!record.heldTool().isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().translate(x + 9.0F, y + 9.0F, 100.0F);
                graphics.pose().scale(0.5F, 0.5F, 0.5F);
                graphics.renderItem(record.heldTool(), 0, 0);
                graphics.pose().popPose();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isRecallHovered((int) mouseX, (int) mouseY)) {
            PacketDistributor.sendToServer(new AllayLoungeRecallPacket(this.menu.loungePos()));
            this.playClick();
            return true;
        }
        if (button == 0 && this.isHovering(PAUSE_BUTTON_X, STRATEGY_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, (int) mouseX, (int) mouseY)) {
            PacketDistributor.sendToServer(new AllayLoungeSettingsPacket(this.menu.loungePos(), AllayShortageStrategy.PAUSE));
            this.playClick();
            return true;
        }
        if (button == 0 && this.isHovering(SKIP_BUTTON_X, STRATEGY_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, (int) mouseX, (int) mouseY)) {
            PacketDistributor.sendToServer(new AllayLoungeSettingsPacket(this.menu.loungePos(), AllayShortageStrategy.SKIP));
            this.playClick();
            return true;
        }
        if (button == 0
            && this.isHovering(CLEAR_AREA_BUTTON_X, STRATEGY_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, (int) mouseX, (int) mouseY)) {
            PacketDistributor.sendToServer(
                new AllayLoungeClearancePacket(this.menu.loungePos(), AllayClearanceStrategy.CLEAR_AREA)
            );
            this.playClick();
            return true;
        }
        if (button == 0
            && this.isHovering(KEEP_BLANK_BUTTON_X, STRATEGY_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, (int) mouseX, (int) mouseY)) {
            PacketDistributor.sendToServer(
                new AllayLoungeClearancePacket(this.menu.loungePos(), AllayClearanceStrategy.KEEP_BLANK)
            );
            this.playClick();
            return true;
        }
        int card = this.hoveredCard((int) mouseX, (int) mouseY);
        if (button == 0 && card >= 0 && card < this.hosted().size()) {
            PacketDistributor.sendToServer(new AllayLoungeReleasePacket(this.menu.loungePos(), card));
            this.playClick();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private List<AllayWorkRecord> hosted() {
        AllayLoungeBlockEntity lounge = this.menu.lounge();
        return lounge == null ? List.of() : lounge.hosted();
    }

    private int hoveredCard(int mouseX, int mouseY) {
        int localX = mouseX - this.leftPos - AllayLoungeMenu.CARD_GRID_X;
        int localY = mouseY - this.topPos - AllayLoungeMenu.CARD_GRID_Y;
        if (localX < 0 || localY < 0) return -1;
        int column = localX / CARD_SIZE;
        int row = localY / CARD_SIZE;
        if (column < 0 || column >= 4 || row < 0 || row >= 4) return -1;
        if (localX % CARD_SIZE >= CARD_SIZE || localY % CARD_SIZE >= CARD_SIZE) return -1;
        return row * 4 + column;
    }

    private boolean isRecallHovered(int mouseX, int mouseY) {
        return this.isHovering(RECALL_BUTTON_X, RECALL_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY);
    }

    private void playClick() {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 1.0F);
        }
    }
}
