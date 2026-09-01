package dev.anvilcraft.plasticraft.integration.jade.provider;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

/** 把暂停原因合并为状态行；资源由 Jade 的通用能力组件绘制。 */
public enum PlasticMoldingChamberProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String STATE = "state";
    private static final String WAIT_REASON = "wait_reason";
    private static final String PARTIAL_DOWNGRADE = "partial_downgrade";
    private static final String FORMING_MODE = "forming_mode";
    private static final String PRINTING_PROGRESS = "printing_progress";
    private static final String PRINTING_TOTAL = "printing_total";

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof PlasticMoldingChamberBlockEntity chamber)) return;
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
        String state = tag.getString(STATE);
        String reason = tag.getString(WAIT_REASON);
        String statusKey = reason.isEmpty() || reason.equals("none") || reason.equals("process_ready")
            ? "screen.anvilcraftplasticraft.molding.state." + state
            : "screen.anvilcraftplasticraft.molding.wait." + reason;
        tooltip.add(Component.translatable(
            "tooltip.anvilcraftplasticraft.molding.state",
            Component.translatable(statusKey)
        ).withStyle(ChatFormatting.BLUE));
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

    @Override
    public ResourceLocation getUid() {
        return AnvilcraftPlasticraft.of("plastic_molding_chamber");
    }
}
