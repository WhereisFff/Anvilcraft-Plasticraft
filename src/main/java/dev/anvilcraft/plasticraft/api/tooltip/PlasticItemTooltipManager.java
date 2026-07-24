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
        register(
            AnvilcraftPlasticraft.of("condenser_tower"),
            "A stackable 3x3x3 module that condenses vapor above a large cauldron",
            "Each module stores 64 buckets and exposes four output-only ports\n"
                + "Oil is separated into high-heat fuel, plastic oil, and crude-oil essence across the first three layers\n"
                + "Capping the top-center outlet makes a full module apply backpressure to the entire stack"
        );
        register(
            AnvilcraftPlasticraft.of("high_heat_fuel_bucket"),
            "Ignitable fuel for the blue enhanced plasma jet",
            "An enhanced jet vaporizes 50 mB each tick while consuming 10 mB of high-heat fuel\n"
                + "Each 250 mB layer of a full layered cauldron extends the enhanced jet by 50 game ticks"
        );
        NORMAL.put(
            AnvilcraftPlasticraft.of("plastic_oil_bucket"),
            "The second-layer condensate and primary feedstock for later plastic processing"
        );
        NORMAL.put(
            AnvilcraftPlasticraft.of("crude_oil_acid_bucket"),
            "The third-layer essence separated from gaseous crude oil"
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
