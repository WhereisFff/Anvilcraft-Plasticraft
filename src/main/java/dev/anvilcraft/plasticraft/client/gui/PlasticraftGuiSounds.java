package dev.anvilcraft.plasticraft.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** 为自绘按钮提供与原版按钮一致的按下音效。 */
public final class PlasticraftGuiSounds {
    private PlasticraftGuiSounds() {
    }

    public static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }
}
