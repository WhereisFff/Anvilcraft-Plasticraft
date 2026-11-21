package dev.anvilcraft.plasticraft.integration.jade.provider;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.integration.jade.PlasticJadeLayout;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPowerBridge;
import dev.dubhe.anvilcraft.api.power.PowerGrid;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.Element;
import snownee.jade.api.ui.IDisplayHelper;
import snownee.jade.api.ui.JadeUI;
import snownee.jade.api.view.ProgressView;

import java.util.Locale;

/** 名称右侧显示生产状态，电网供电与电池存量共用一条能源进度条。 */
public enum PlasticMoldingChamberProvider implements IServerDataProvider<BlockAccessor> {
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
    public Identifier getUid() {
        return AnvilcraftPlasticraft.of("plastic_molding_chamber");
    }

    public enum Client implements IBlockComponentProvider {
        INSTANCE;

        private static final Element ENERGY_FILL = JadeUI.horizontalTiledSprite(
            RenderPipelines.GUI_TEXTURED, JadeIds.JADE("energy_progress"), 16, 16
        );

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag tag = accessor.getServerData();
            if (!tag.contains(STATE)) {
                return;
            }
            String state = tag.getStringOr(STATE, "");
            String reason = tag.getStringOr(WAIT_REASON, "");
            String statusKey = reason.isEmpty() || reason.equals("none") || reason.equals("process_ready")
                ? "screen.anvilcraftplasticraft.molding.state." + state
                : "screen.anvilcraftplasticraft.molding.wait." + reason;

            tooltip.add(JadeUI.text(Component.literal("  ").append(Component.translatable(statusKey))
                .withStyle(ChatFormatting.GRAY)).tag(PlasticJadeLayout.MACHINE_STATE));
            int capacity = MoldingPowerBridge.capacity();
            int stored = tag.getIntOr(ENERGY, 0);
            Component energyText;
            if (tag.getBooleanOr(GRID_WORKING, false)) {
                energyText = Component.translatable(
                    "tooltip.anvilcraftplasticraft.jade.grid_power", tag.getIntOr(GRID_POWER, 0)
                ).withStyle(ChatFormatting.WHITE);
            } else {
                IDisplayHelper display = IDisplayHelper.get();
                energyText = Component.translatable("jade.fe",
                    ChatFormatting.WHITE + display.humanReadableNumber(stored, "FE", false),
                    display.humanReadableNumber(capacity, "FE", false));
            }
            tooltip.add(JadeUI.progress(new ProgressView(
                ProgressView.Part.of(Mth.clamp((float) stored / capacity, 0, 1), ENERGY_FILL),
                energyText, JadeUI.progressStyle(), BoxStyle.nestedBox()
            )).tag(PlasticJadeLayout.CHAMBER_ENERGY));
            String formingMode = tag.getStringOr(FORMING_MODE, "");
            if (!formingMode.isEmpty()) {
                tooltip.add(Component.translatable(
                    "screen.anvilcraftplasticraft.molding.forming",
                    Component.translatable("screen.anvilcraftplasticraft.molding.forming." + formingMode)
                ).withStyle(ChatFormatting.GRAY));
            }
            int printingTotal = tag.getIntOr(PRINTING_TOTAL, 0);
            if (formingMode.equals("printing") && printingTotal > 0) {
                tooltip.add(Component.translatable(
                    "screen.anvilcraftplasticraft.molding.printing_progress",
                    tag.getIntOr(PRINTING_PROGRESS, 0),
                    printingTotal
                ).withStyle(ChatFormatting.GRAY));
            }
            if (tag.getBooleanOr(PARTIAL_DOWNGRADE, false)) {
                tooltip.add(Component.translatable(
                    "tooltip.anvilcraftplasticraft.molding.partial_downgrade"
                ).withStyle(ChatFormatting.RED));
            }
        }

        @Override
        public Identifier getUid() {
            return PlasticMoldingChamberProvider.INSTANCE.getUid();
        }
    }
}
