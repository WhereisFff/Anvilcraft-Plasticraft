package dev.anvilcraft.plasticraft.client.gui.screen;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.DroneStationBlockEntity;
import dev.anvilcraft.plasticraft.inventory.DroneStationMenu;
import dev.anvilcraft.plasticraft.network.DroneStationRecallPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/** 无人机站界面:16 个无人机槽、磁盘槽、电容器槽、能量条与召回按钮。 */
public class DroneStationScreen extends AbstractContainerScreen<DroneStationMenu> {
    private static final ResourceLocation TEXTURE =
        AnvilcraftPlasticraft.of("textures/gui/background/drone_station.png");
    private static final ResourceLocation RETURN_HOME_BUTTON =
        AnvilcraftPlasticraft.of("textures/gui/button/drone/return_home.png");
    private static final int ENERGY_FRAME_X = 10;
    private static final int ENERGY_FRAME_Y = 20;
    private static final int ENERGY_BAR_X = 13;
    private static final int ENERGY_BAR_Y = 24;
    private static final int ENERGY_BAR_WIDTH = 7;
    private static final int ENERGY_BAR_HEIGHT = 30;
    private static final int BUTTON_SIZE = 16;
    private static final int RECALL_BUTTON_X = 9;
    private static final int RECALL_BUTTON_Y = 66;

    public DroneStationScreen(DroneStationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 186;
        this.inventoryLabelY = DroneStationMenu.PLAYER_INVENTORY_Y - 11;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        if (this.isHovering(ENERGY_FRAME_X, ENERGY_FRAME_Y, 13, 37, mouseX, mouseY)) {
            graphics.renderTooltip(
                this.font,
                Component.translatable(
                    "screen.anvilcraftplasticraft.drone.energy",
                    String.format(Locale.ROOT, "%,d", this.menu.energy()),
                    String.format(Locale.ROOT, "%,d", DroneStationBlockEntity.capacity())
                ),
                mouseX,
                mouseY
            );
        }
        if (this.isRecallHovered(mouseX, mouseY)) {
            graphics.renderTooltip(
                this.font,
                Component.translatable("screen.anvilcraftplasticraft.drone_station.recall"),
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
        this.renderEnergyBar(graphics);
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
    }

    /** 与无人机单机界面同规格的竖直分段能量条。 */
    private void renderEnergyBar(GuiGraphics graphics) {
        int left = this.leftPos + ENERGY_BAR_X;
        int top = this.topPos + ENERGY_BAR_Y;
        long capacity = DroneStationBlockEntity.capacity();
        long stored = Math.clamp(this.menu.energy(), 0L, capacity);
        int filled = stored == 0L
            ? 0
            : Math.max(1, (int) (stored * ENERGY_BAR_HEIGHT / capacity));
        for (int row = 0; row < ENERGY_BAR_HEIGHT; row++) {
            boolean isFilled = row >= ENERGY_BAR_HEIGHT - filled;
            int y = top + row;
            if (!isFilled) {
                graphics.fill(left, y, left + ENERGY_BAR_WIDTH, y + 1, 0xFF1B0A0A);
                continue;
            }
            int base = row % 2 == 0 ? 0xFF7A1B12 : 0xFFA3311F;
            graphics.fill(left, y, left + ENERGY_BAR_WIDTH, y + 1, base);
            if (row % 2 == 1) {
                graphics.fill(left, y, left + 2, y + 1, 0xFFC24B32);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isRecallHovered((int) mouseX, (int) mouseY)) {
            PacketDistributor.sendToServer(new DroneStationRecallPacket(this.menu.stationPos()));
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 1.0F);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isRecallHovered(int mouseX, int mouseY) {
        return this.isHovering(RECALL_BUTTON_X, RECALL_BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY);
    }
}
