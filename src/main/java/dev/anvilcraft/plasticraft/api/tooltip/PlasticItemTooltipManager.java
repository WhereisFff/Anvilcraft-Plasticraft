package dev.anvilcraft.plasticraft.api.tooltip;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 供运行时工具提示和语言数据生成共用的物品描述注册表。 */
public final class PlasticItemTooltipManager {
    private static final Map<ResourceLocation, String> NORMAL = new LinkedHashMap<>();
    private static final Map<ResourceLocation, String> SHIFT = new LinkedHashMap<>();
    private static boolean initialized;

    private PlasticItemTooltipManager() {
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        NORMAL.put(
            AnvilcraftPlasticraft.of("liquid_high_viscosity_resin_bucket"),
            "Highly adhesive and non-volatile; it appears to need thousands of years to solidify"
        );
    }

    public static void register(
        ResourceLocation itemId,
        String normalDescription,
        String shiftDescription
    ) {
        NORMAL.put(itemId, normalDescription);
        SHIFT.put(itemId, shiftDescription);
    }

    public static void addTooltip(ItemStack stack, List<Component> tooltip) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (SHIFT.containsKey(itemId)) {
            if (Screen.hasShiftDown()) {
                addTranslatedTooltip(tooltip, getTranslationKeyShift(itemId));
            } else {
                if (NORMAL.containsKey(itemId)) addTranslatedTooltip(tooltip, getTranslationKey(itemId));
                tooltip.add(
                    1,
                    Component.translatable(
                        "tooltip.anvilcraft.press_key",
                        Component.literal("[Shift]").withStyle(ChatFormatting.WHITE)
                    ).withStyle(ChatFormatting.DARK_GRAY)
                );
            }
        } else if (NORMAL.containsKey(itemId)) {
            addTranslatedTooltip(tooltip, getTranslationKey(itemId));
        }
    }

    private static void addTranslatedTooltip(List<Component> tooltip, String key) {
        String[] lines = I18n.get(key).split("\n");
        for (int index = lines.length - 1; index >= 0; index--) {
            tooltip.add(1, Component.literal(lines[index]).withStyle(ChatFormatting.GRAY));
        }
    }

    public static String getTranslationKey(ResourceLocation itemId) {
        return "tooltip.%s.item.%s".formatted(itemId.getNamespace(), itemId.getPath());
    }

    public static String getTranslationKeyShift(ResourceLocation itemId) {
        return getTranslationKey(itemId) + ".shift";
    }

    public static Map<ResourceLocation, String> getNormalMap() {
        return Collections.unmodifiableMap(NORMAL);
    }

    public static Map<ResourceLocation, String> getShiftMap() {
        return Collections.unmodifiableMap(SHIFT);
    }
}
