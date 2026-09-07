package dev.anvilcraft.plasticraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

public final class AllayLoungeButton extends AbstractButton {
    private final ResourceLocation texture;
    @Nullable
    private final BooleanSupplier selected;
    private final Runnable action;
    private boolean pressed;

    public AllayLoungeButton(
        int x, int y, int size, ResourceLocation texture, Component message,
        @Nullable BooleanSupplier selected, Runnable action
    ) {
        super(x, y, size, size, message);
        this.texture = texture;
        this.selected = selected;
        this.action = action;
        this.setTooltip(Tooltip.create(message));
    }

    public boolean isPressed() {
        return this.pressed;
    }

    public void cancelPress() {
        this.pressed = false;
    }

    @Override
    public void onPress() {
        this.action.run();
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.pressed = true;
        if (this.selected == null) this.onPress();
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        if (!this.pressed) return;
        this.pressed = false;
        if (this.selected != null && this.clicked(mouseX, mouseY)) this.onPress();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean selected = this.selected != null && this.selected.getAsBoolean();
        int frame = this.pressed ? 2 : this.isMouseOver(mouseX, mouseY) ? 1 : selected ? 3 : 0;
        int frames = this.selected == null ? 3 : 4;
        graphics.blit(this.texture, this.getX(), this.getY(), 0, frame * this.height,
            this.width, this.height, this.width, this.height * frames);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
