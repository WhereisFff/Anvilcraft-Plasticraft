package dev.anvilcraft.plasticraft.integration.jade;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.integration.jade.provider.PlasticItemStorageProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.Accessor;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.api.ui.BoxElement;
import snownee.jade.api.ui.Element;
import snownee.jade.api.ui.JadeUI;
import snownee.jade.impl.Tooltip;

import java.util.List;

/** 在各提供器完成收集后排列名称状态和成型舱能源，避免默认组件覆盖或重复插入。 */
public final class PlasticJadeLayout {
    public static final Identifier PUSHABLE = AnvilcraftPlasticraft.of("jade_pushable");
    public static final Identifier BONDED = AnvilcraftPlasticraft.of("jade_bonded");
    public static final Identifier MACHINE_STATE = AnvilcraftPlasticraft.of("jade_machine_state");
    public static final Identifier CHAMBER_ENERGY = AnvilcraftPlasticraft.of("jade_chamber_energy");
    private static final Identifier OMITTED = AnvilcraftPlasticraft.of("jade_omitted_items");
    private static final Identifier GRID_ENERGY = Identifier.fromNamespaceAndPath("anvilcraft", "power_provider");

    private PlasticJadeLayout() {
    }

    public static void finish(BoxElement root, Accessor<?> accessor) {
        Tooltip tooltip = root.getTooltip();
        List<LayoutElement> status = tooltip.get(MACHINE_STATE);
        if (status.isEmpty()) {
            status = tooltip.get(BONDED);
        }
        if (status.isEmpty()) {
            status = tooltip.get(PUSHABLE);
        }
        List<LayoutElement> energy = tooltip.get(CHAMBER_ENERGY);
        int omitted = accessor.getServerData().getIntOr(PlasticItemStorageProvider.OMITTED, 0);
        int previewCount = accessor.getServerData().getIntOr(PlasticItemStorageProvider.PREVIEW_COUNT, 0);
        if (previewCount > 0) {
            int limit = IWailaConfig.get().plugin().getInt(accessor.showDetails()
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
        for (LayoutElement element : status) {
            if (name >= 0) {
                tooltip.append(name, JadeUI.spacer(0, 0).flexGrow(1));
                tooltip.append(name, element);
            } else {
                tooltip.add(0, element);
            }
        }

        if (!energy.isEmpty()) {
            tooltip.remove(GRID_ENERGY);
            tooltip.remove(JadeIds.UNIVERSAL_ENERGY_STORAGE);
            tooltip.remove(CHAMBER_ENERGY);
            int insertion = nameLine(tooltip) + 1;
            for (LayoutElement element : energy) {
                tooltip.add(insertion++, element);
            }
        }
        if (omitted > 0) {
            int omittedCount = omitted;
            // 子框高度变化后，包含它的外层行也必须失效，否则后续模组名仍按旧高度绘制。
            tooltip.replace(JadeIds.UNIVERSAL_ITEM_STORAGE, lines -> {
                for (List<LayoutElement> line : lines) {
                    for (LayoutElement element : line) {
                        if (element instanceof BoxElement box) {
                            box.getTooltip().remove(OMITTED);
                            box.getTooltip().add(JadeUI.text(Component.translatable(
                                "tooltip.anvilcraftplasticraft.molded_more_contents", omittedCount
                            ).withStyle(ChatFormatting.GRAY)).tag(OMITTED));
                            box.updateSize();
                        }
                    }
                }
                return lines;
            });
        }
        root.updateSize();
    }

    private static int nameLine(Tooltip tooltip) {
        for (int line = 0; line < tooltip.size(); line++) {
            if (tooltip.lines.get(line).elements().stream().anyMatch(element ->
                element instanceof Element jade && JadeIds.CORE_OBJECT_NAME.equals(jade.getTag()))) {
                return line;
            }
        }
        return -1;
    }
}
