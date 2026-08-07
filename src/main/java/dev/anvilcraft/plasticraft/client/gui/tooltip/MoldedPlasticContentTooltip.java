package dev.anvilcraft.plasticraft.client.gui.tooltip;

import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContentSummary;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class MoldedPlasticContentTooltip {
    private MoldedPlasticContentTooltip() {
    }

    public static List<Component> create(MoldedPlasticContentSummary summary) {
        List<Component> lines = new ArrayList<>();
        if (MoldingProductTypes.isChest(summary.type())) {
            lines.add(Component.translatable(
                "tooltip.anvilcraftplasticraft.molded_chest_contents",
                summary.occupiedSlots(),
                summary.capacity()
            ));
            for (MoldedPlasticContentSummary.ItemEntry item : summary.items()) {
                lines.add(Component.translatable(
                    "tooltip.anvilcraftplasticraft.jade.item_count",
                    item.stack().getHoverName().copy().withStyle(ChatFormatting.GRAY),
                    item.count()
                ).withStyle(ChatFormatting.GRAY));
            }
            appendOmitted(lines, summary.omittedItemTypes());
        } else if (MoldingProductTypes.isTank(summary.type())) {
            lines.add(Component.translatable(
                "tooltip.anvilcraftplasticraft.molded_tank_contents",
                summary.totalFluid(),
                summary.capacity()
            ));
            for (MoldedPlasticContentSummary.FluidEntry fluid : summary.fluids()) {
                lines.add(Component.translatable(
                    "tooltip.anvilcraftplasticraft.jade.fluid_count",
                    fluid.name(),
                    fluid.amount()
                ).withStyle(ChatFormatting.GRAY));
            }
            appendOmitted(lines, summary.omittedFluidTypes());
        }
        return List.copyOf(lines);
    }

    private static void appendOmitted(List<Component> lines, int count) {
        if (count > 0) {
            lines.add(Component.translatable(
                "tooltip.anvilcraftplasticraft.molded_more_contents",
                count
            ).withStyle(ChatFormatting.GRAY));
        }
    }
}
