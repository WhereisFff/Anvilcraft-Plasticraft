package dev.anvilcraft.plasticraft.integration.jade.provider;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.integration.jade.PlasticJadeLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.JadeUI;

/** 为方块化粘附物显示 Jade 状态。 */
public enum BondedBlockProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String BONDED = "bonded";

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        tag.putBoolean(
            BONDED,
            accessor.getBlockEntity() instanceof BondedEntityBlockEntity bonded && bonded.isInitialized()
                || accessor.getLevel() instanceof ServerLevel level
                    && BondedFallingBlocks.isBonded(level, accessor.getPosition())
        );
    }

    @Override
    public Identifier getUid() {
        return AnvilcraftPlasticraft.of("bonded_block");
    }

    public enum Client implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (accessor.getServerData().getBooleanOr(BONDED, false)) {
                tooltip.add(JadeUI.text(Component.literal("  ")
                    .append(Component.translatable("tooltip.anvilcraftplasticraft.bonded"))
                    .withStyle(ChatFormatting.GRAY)).tag(PlasticJadeLayout.BONDED));
            }
        }

        @Override
        public Identifier getUid() {
            return BondedBlockProvider.INSTANCE.getUid();
        }
    }
}
