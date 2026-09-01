package dev.anvilcraft.plasticraft.integration.jade.provider;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.PlasticraftAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** 为所有被树脂固定的实体显示 Jade 状态。 */
public enum BondedEntityProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    private static final String BONDED = "bonded";

    @Override
    public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
        tag.putBoolean(
            BONDED,
            accessor.getEntity().hasData(PlasticraftAttachments.ENTITY_ADHESION)
                || accessor.getEntity().hasData(PlasticraftAttachments.ENTITY_BONDS)
        );
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (accessor.getServerData().getBoolean(BONDED)) {
            tooltip.add(Component.translatable("tooltip.anvilcraftplasticraft.bonded"));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return AnvilcraftPlasticraft.of("bonded_entity");
    }
}
