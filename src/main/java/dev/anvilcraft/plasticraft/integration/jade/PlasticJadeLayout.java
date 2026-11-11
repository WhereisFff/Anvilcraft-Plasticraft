package dev.anvilcraft.plasticraft.integration.jade;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.integration.jade.provider.PlasticItemStorageProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.Accessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.api.ui.IBoxElement;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import java.util.List;

/** 在各提供器完成收集后排列名称状态和成型舱能源，避免默认组件覆盖或重复插入。 */
public final class PlasticJadeLayout {
    public static final ResourceLocation PUSHABLE = AnvilcraftPlasticraft.of("jade_pushable");
    public static final ResourceLocation BONDED = AnvilcraftPlasticraft.of("jade_bonded");
    public static final ResourceLocation MACHINE_STATE = AnvilcraftPlasticraft.of("jade_machine_state");
    public static final ResourceLocation CHAMBER_ENERGY = AnvilcraftPlasticraft.of("jade_chamber_energy");
    private static final ResourceLocation OMITTED = AnvilcraftPlasticraft.of("jade_omitted_items");
    private static final ResourceLocation GRID_ENERGY = ResourceLocation.fromNamespaceAndPath("anvilcraft", "power_provider");

    private PlasticJadeLayout() {
    }

    public static void finish(IBoxElement root, Accessor<?> accessor) {
        ITooltip tooltip = root.getTooltip();
        List<IElement> status = tooltip.get(MACHINE_STATE);
        if (status.isEmpty()) {
            status = tooltip.get(BONDED);
        }
        if (status.isEmpty()) {
            status = tooltip.get(PUSHABLE);
        }
        List<IElement> energy = tooltip.get(CHAMBER_ENERGY);
        int omitted = accessor.getServerData().getInt(PlasticItemStorageProvider.OMITTED);
        int previewCount = accessor.getServerData().getInt(PlasticItemStorageProvider.PREVIEW_COUNT);
        if (previewCount > 0) {
            int limit = IWailaConfig.get().getPlugin().getInt(accessor.showDetails()
                ? JadeIds.UNIVERSAL_ITEM_STORAGE_DETAILED_AMOUNT : JadeIds.UNIVERSAL_ITEM_STORAGE_NORMAL_AMOUNT);
            omitted += Math.max(0, previewCount - limit);
        }
        if (status.isEmpty() && energy.isEmpty() && omitted == 0) {
            return;
        }
        tooltip.remove(MACHINE_STATE);
        tooltip.remove(BONDED);
        tooltip.remove(PUSHABLE);
        int name = nameLine(tooltip);
        for (IElement element : status) {
            if (name >= 0) {
                tooltip.append(name, element.align(IElement.Align.RIGHT));
            } else {
                tooltip.add(0, element);
            }
        }

        if (!energy.isEmpty()) {
            tooltip.remove(GRID_ENERGY);
            tooltip.remove(JadeIds.UNIVERSAL_ENERGY_STORAGE);
            tooltip.remove(CHAMBER_ENERGY);
            int insertion = nameLine(tooltip) + 1;
            for (IElement element : energy) {
                tooltip.add(insertion++, element);
            }
        }
        if (omitted > 0) {
            int omittedCount = omitted;
            // 子框高度变化后，包含它的外层行也必须失效，否则后续模组名仍按旧高度绘制。
            tooltip.replace(JadeIds.UNIVERSAL_ITEM_STORAGE, lines -> {
                for (List<IElement> line : lines) {
                    for (IElement element : line) {
                        if (element instanceof IBoxElement box) {
                            box.getTooltip().remove(OMITTED);
                            box.getTooltip().add(IElementHelper.get().text(Component.translatable(
                                "tooltip.anvilcraftplasticraft.molded_more_contents", omittedCount
                            ).withStyle(ChatFormatting.GRAY)).tag(OMITTED));
                            box.size(null);
                        }
                    }
                }
                return lines;
            });
        }
        root.size(null);
    }

    private static int nameLine(ITooltip tooltip) {
        for (int line = 0; line < tooltip.size(); line++) {
            for (IElement.Align align : IElement.Align.values()) {
                if (tooltip.get(line, align).stream().anyMatch(element -> JadeIds.CORE_OBJECT_NAME.equals(element.getTag()))) {
                    return line;
                }
            }
        }
        return -1;
    }
}
