package dev.anvilcraft.plasticraft.integration.jade.provider;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** 为方块化粘附物显示 Jade 状态。 */
public enum BondedBlockProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
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
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getServerData().getBoolean(BONDED)) {
            tooltip.add(Component.translatable("tooltip.anvilcraftplasticraft.bonded"));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return AnvilcraftPlasticraft.of("bonded_block");
    }
}
