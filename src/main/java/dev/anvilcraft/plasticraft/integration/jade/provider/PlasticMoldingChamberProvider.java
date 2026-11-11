package dev.anvilcraft.plasticraft.integration.jade.provider;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.integration.jade.PlasticJadeLayout;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPowerBridge;
import dev.dubhe.anvilcraft.api.power.PowerGrid;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IDisplayHelper;
import snownee.jade.api.ui.IElementHelper;
import snownee.jade.impl.ui.SimpleProgressStyle;
import snownee.jade.overlay.DisplayHelper;

import java.util.Locale;

/** 名称右侧显示生产状态，电网供电与电池存量共用一条能源进度条。 */
public enum PlasticMoldingChamberProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String STATE = "state";
    private static final String ENERGY = "plasticraft_energy";
    private static final String GRID_WORKING = "plasticraft_grid_working";
    private static final String GRID_POWER = "plasticraft_grid_power";
    private static final String WAIT_REASON = "wait_reason";
    private static final String PARTIAL_DOWNGRADE = "partial_downgrade";
    private static final String FORMING_MODE = "forming_mode";
    private static final String PRINTING_PROGRESS = "printing_progress";
    private static final String PRINTING_TOTAL = "printing_total";

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof PlasticMoldingChamberBlockEntity chamber)) {
            return;
        }
        PowerGrid grid = chamber.getGrid();
        tag.putInt(ENERGY, chamber.energyStored());
        tag.putBoolean(GRID_WORKING, grid != null && grid.isWorking()
            && (grid.isHasInfinitePower() || grid.getGenerate() > 0));
        tag.putInt(GRID_POWER, chamber.getInputPower());
        tag.putString(STATE, chamber.machineState().getSerializedName());
        tag.putString(WAIT_REASON, chamber.waitReason().name().toLowerCase(Locale.ROOT));
        tag.putBoolean(PARTIAL_DOWNGRADE, chamber.willCurrentResultDowngrade());
        tag.putString(
            FORMING_MODE,
            (chamber.isLocked() ? chamber.cycleFormingMode() : chamber.formingMode()).getSerializedName()
        );
        tag.putInt(PRINTING_PROGRESS, chamber.printingProgress());
        tag.putInt(PRINTING_TOTAL, chamber.printingTotal());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        if (!tag.contains(STATE)) {
            return;
        }
        String state = tag.getString(STATE);
        String reason = tag.getString(WAIT_REASON);
        String statusKey = reason.isEmpty() || reason.equals("none") || reason.equals("process_ready")
            ? "screen.anvilcraftplasticraft.molding.state." + state
            : "screen.anvilcraftplasticraft.molding.wait." + reason;
        IElementHelper helper = IElementHelper.get();
        tooltip.add(helper.text(Component.literal("  ").append(Component.translatable(statusKey))
            .withStyle(ChatFormatting.GRAY)).tag(PlasticJadeLayout.MACHINE_STATE));
        int capacity = MoldingPowerBridge.capacity();
        int stored = tag.getInt(ENERGY);
        Component energyText;
        if (tag.getBoolean(GRID_WORKING)) {
            energyText = Component.translatable(
                "tooltip.anvilcraftplasticraft.jade.grid_power", tag.getInt(GRID_POWER)
            ).withStyle(ChatFormatting.WHITE);
        } else {
            IDisplayHelper display = IDisplayHelper.get();
            energyText = Component.literal(display.humanReadableNumber(stored, "FE", false))
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" / " + display.humanReadableNumber(capacity, "FE", false))
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(helper.progress(
            (float) stored / capacity, energyText, new CenteredEnergyStyle().color(0xFFAA0000, 0xFF660000),
            BoxStyle.getNestedBox(), true
        ).tag(PlasticJadeLayout.CHAMBER_ENERGY));
        String formingMode = tag.getString(FORMING_MODE);
        if (!formingMode.isEmpty()) {
            tooltip.add(Component.translatable(
                "screen.anvilcraftplasticraft.molding.forming",
                Component.translatable("screen.anvilcraftplasticraft.molding.forming." + formingMode)
            ).withStyle(ChatFormatting.GRAY));
        }
        int printingTotal = tag.getInt(PRINTING_TOTAL);
        if (formingMode.equals("printing") && printingTotal > 0) {
            tooltip.add(Component.translatable(
                "screen.anvilcraftplasticraft.molding.printing_progress",
                tag.getInt(PRINTING_PROGRESS),
                printingTotal
            ).withStyle(ChatFormatting.GRAY));
        }
        if (tag.getBoolean(PARTIAL_DOWNGRADE)) {
            tooltip.add(Component.translatable(
                "tooltip.anvilcraftplasticraft.molding.partial_downgrade"
            ).withStyle(ChatFormatting.RED));
        }
    }

    private static final class CenteredEnergyStyle extends SimpleProgressStyle {
        @Override
        public void render(GuiGraphics graphics, float x, float y, float width, float height,
                           float progress, @Nullable Component text) {
            super.render(graphics, x, y, width, height, progress, null);
            if (text != null) {
                Font font = DisplayHelper.font();
                IDisplayHelper.get().drawText(graphics, text,
                    x + (width - font.width(text)) / 2,
                    y + (height - font.lineHeight) / 2, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return AnvilcraftPlasticraft.of("plastic_molding_chamber");
    }
}
