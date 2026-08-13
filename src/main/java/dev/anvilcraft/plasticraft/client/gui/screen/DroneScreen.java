package dev.anvilcraft.plasticraft.client.gui.screen;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.drone.DroneEnergyModel;
import dev.anvilcraft.plasticraft.drone.DroneShortageStrategy;
import dev.anvilcraft.plasticraft.drone.tool.DroneCapability;
import dev.anvilcraft.plasticraft.drone.tool.DroneToolDefinition;
import dev.anvilcraft.plasticraft.drone.tool.DroneToolDefinitions;
import dev.anvilcraft.plasticraft.inventory.DroneMenu;
import dev.anvilcraft.plasticraft.network.DroneSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/** 无人机单机设置界面:工具状态、能量条、所有者、策略控件与收集库存展示。 */
public class DroneScreen extends AbstractContainerScreen<DroneMenu> {
    private static final ResourceLocation TEXTURE =
        AnvilcraftPlasticraft.of("textures/gui/background/drone.png");
    private static final ResourceLocation PAUSE_BUTTON =
        AnvilcraftPlasticraft.of("textures/gui/button/drone/pause.png");
    private static final ResourceLocation SKIP_BUTTON =
        AnvilcraftPlasticraft.of("textures/gui/button/drone/skip.png");
    private static final int ENERGY_FRAME_X = 10;
    private static final int ENERGY_FRAME_Y = 20;
    private static final int ENERGY_BAR_X = 13;
    private static final int ENERGY_BAR_Y = 24;
    private static final int ENERGY_BAR_WIDTH = 7;
    private static final int ENERGY_BAR_HEIGHT = 30;
    private static final int TEXT_X = 32;
    private static final int BUTTON_SIZE = 16;
    private static final int PAUSE_BUTTON_X = 32;
    private static final int SKIP_BUTTON_X = 54;
    private static final int BUTTON_Y = 96;

    public DroneScreen(DroneMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 150;
        this.inventoryLabelY = 10_000;
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
                    String.format(Locale.ROOT, "%,d", DroneEnergyModel.capacity())
                ),
                mouseX,
                mouseY
            );
        }
        if (this.showsStrategyControls()) {
            if (this.isButtonHovered(PAUSE_BUTTON_X, mouseX, mouseY)) {
                graphics.renderTooltip(
                    this.font,
                    Component.translatable("screen.anvilcraftplasticraft.drone.strategy.pause"),
                    mouseX,
                    mouseY
                );
            } else if (this.isButtonHovered(SKIP_BUTTON_X, mouseX, mouseY)) {
                graphics.renderTooltip(
                    this.font,
                    Component.translatable("screen.anvilcraftplasticraft.drone.strategy.skip"),
                    mouseX,
                    mouseY
                );
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        graphics.blit(TEXTURE, left, top, 0, 0, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
        this.renderEnergyBar(graphics);
        if (this.showsStrategyControls()) {
            DroneShortageStrategy strategy = this.menu.strategy();
            this.renderStrategyButton(
                graphics,
                PAUSE_BUTTON,
                PAUSE_BUTTON_X,
                strategy == DroneShortageStrategy.PAUSE,
                mouseX,
                mouseY
            );
            this.renderStrategyButton(
                graphics,
                SKIP_BUTTON,
                SKIP_BUTTON_X,
                strategy == DroneShortageStrategy.SKIP,
                mouseX,
                mouseY
            );
        }
    }

    /** 复用塑料成型舱的竖直分段能量条尺寸:7x30,奇数行左右渐变。 */
    private void renderEnergyBar(GuiGraphics graphics) {
        int left = this.leftPos + ENERGY_BAR_X;
        int top = this.topPos + ENERGY_BAR_Y;
        long capacity = DroneEnergyModel.capacity();
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

    private void renderStrategyButton(
        GuiGraphics graphics,
        ResourceLocation texture,
        int buttonX,
        boolean selected,
        int mouseX,
        int mouseY
    ) {
        // 四帧竖排图集:0 正常、1 悬停、2 按下(用于当前选中)、3 禁用。
        int frame = selected ? 2 : this.isButtonHovered(buttonX, mouseX, mouseY) ? 1 : 0;
        graphics.blit(
            texture,
            this.leftPos + buttonX,
            this.topPos + BUTTON_Y,
            0,
            frame * BUTTON_SIZE,
            BUTTON_SIZE,
            BUTTON_SIZE,
            BUTTON_SIZE,
            BUTTON_SIZE * 4
        );
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        DroneToolDefinition definition = this.menu.toolDefinition();
        Component toolName = DroneToolDefinitions.NONE.id().equals(definition.id())
            ? Component.translatable("screen.anvilcraftplasticraft.drone.tool.none")
            : new ItemStack(definition.toolItem().get()).getHoverName();
        graphics.drawString(
            this.font,
            Component.translatable("screen.anvilcraftplasticraft.drone.tool", toolName),
            TEXT_X,
            22,
            0x404040,
            false
        );
        String ownerName = this.menu.ownerName().isEmpty() ? "-" : this.menu.ownerName();
        graphics.drawString(
            this.font,
            Component.translatable("screen.anvilcraftplasticraft.drone.owner", ownerName),
            TEXT_X,
            34,
            0x404040,
            false
        );
        graphics.drawString(
            this.font,
            Component.translatable(
                "screen.anvilcraftplasticraft.drone.state."
                    + this.menu.flightState().name().toLowerCase(Locale.ROOT)
            ),
            TEXT_X,
            46,
            0x404040,
            false
        );
        if (definition.hasCapability(DroneCapability.CHUNK_LOADING)) {
            graphics.drawString(
                this.font,
                Component.translatable("screen.anvilcraftplasticraft.drone.coverage.inactive"),
                TEXT_X,
                58,
                0x404040,
                false
            );
        }
        if (this.showsStrategyControls()) {
            graphics.drawString(
                this.font,
                Component.translatable("screen.anvilcraftplasticraft.drone.strategy"),
                TEXT_X,
                84,
                0x404040,
                false
            );
        }
        if (definition.inventorySize() > 0) {
            graphics.drawString(
                this.font,
                Component.translatable("screen.anvilcraftplasticraft.drone.inventory"),
                DroneMenu.COLLECTION_SLOT_X - 1,
                DroneMenu.COLLECTION_SLOT_Y - 12,
                0x404040,
                false
            );
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.showsStrategyControls()) {
            if (this.isButtonHovered(PAUSE_BUTTON_X, (int) mouseX, (int) mouseY)) {
                this.sendStrategy(DroneShortageStrategy.PAUSE);
                return true;
            }
            if (this.isButtonHovered(SKIP_BUTTON_X, (int) mouseX, (int) mouseY)) {
                this.sendStrategy(DroneShortageStrategy.SKIP);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendStrategy(DroneShortageStrategy strategy) {
        if (strategy == this.menu.strategy() || this.minecraft == null) return;
        PacketDistributor.sendToServer(new DroneSettingsPacket(this.menu.containerId, strategy));
        if (this.minecraft.player != null) {
            this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 1.0F);
        }
    }

    /** 缺料与缺拆除能力策略只对建设与拆除工种显示。 */
    private boolean showsStrategyControls() {
        DroneToolDefinition definition = this.menu.toolDefinition();
        return definition.hasCapability(DroneCapability.PICK_UP_MATERIAL)
            || definition.hasCapability(DroneCapability.DEMOLISH);
    }

    private boolean isButtonHovered(int buttonX, int mouseX, int mouseY) {
        return this.isHovering(buttonX, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY);
    }
}
