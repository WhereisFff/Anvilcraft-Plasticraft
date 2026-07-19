package dev.anvilcraft.plasticraft.api.tooltip;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.ChatFormatting;
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
    private static boolean initialized;

    private PlasticItemTooltipManager() {
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        NORMAL.put(
            AnvilcraftPlasticraft.of("resin_anvil"),
            """
                Elastic and pushable; rebounds from blocks and entities
                Retains Resin Block capture and time-warp behavior
                Dry fast cooking hardens it
                Creative players can Shift-use a magnet to magnetize it"""
        );
        NORMAL.put(
            AnvilcraftPlasticraft.of("hardend_resin_anvil"),
            """
                Pushable anvil with the vanilla anvil workflow
                Renaming costs no experience and adds no prior-work penalty
                Creative players can Shift-use a magnet to magnetize it"""
        );
        NORMAL.put(
            AnvilcraftPlasticraft.of("hardend_resin_cauldron"),
            """
                Pushable cauldron for items and up to 1000 mB of fluid
                Connects to pipe heads, pumps, and control valves from any side
                Creative players can Shift-use a magnet to magnetize it"""
        );
    }

    public static void addTooltip(ItemStack stack, List<Component> tooltip) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!NORMAL.containsKey(itemId)) return;
        String[] lines = I18n.get(getTranslationKey(itemId)).split("\n");
        for (int index = lines.length - 1; index >= 0; index--) {
            tooltip.add(1, Component.literal(lines[index]).withStyle(ChatFormatting.GRAY));
        }
    }

    public static String getTranslationKey(ResourceLocation itemId) {
        return "tooltip.%s.item.%s".formatted(itemId.getNamespace(), itemId.getPath());
    }

    public static Map<ResourceLocation, String> getNormalMap() {
        return Collections.unmodifiableMap(NORMAL);
    }
}
