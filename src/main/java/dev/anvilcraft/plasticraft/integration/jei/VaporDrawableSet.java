package dev.anvilcraft.plasticraft.integration.jei;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.recipe.CondenserGas;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class VaporDrawableSet {
    private final IDrawable water;
    private final IDrawable oil;
    private final IDrawable experience;

    VaporDrawableSet(IGuiHelper helper) {
        this.water = create(helper, "steam.png");
        this.oil = create(helper, "oil_steam.png");
        this.experience = create(helper, "experience_steam.png");
    }

    void draw(GuiGraphics graphics, ResourceLocation gas, int x, int y) {
        ResourceLocation canonical = CondenserGas.canonicalize(gas);
        IDrawable drawable = this.water;
        if (CondenserGas.GASEOUS_OIL.equals(canonical)) {
            drawable = this.oil;
        } else if (CondenserGas.GASEOUS_EXPERIENCE.equals(canonical)) {
            drawable = this.experience;
        }
        drawable.draw(graphics, x, y);
    }

    private static IDrawable create(IGuiHelper helper, String texture) {
        return helper.drawableBuilder(
            AnvilcraftPlasticraft.of("textures/gui/jei/" + texture),
            0,
            0,
            16,
            16
        ).setTextureSize(16, 16).build();
    }
}
