package dev.anvilcraft.plasticraft.integration.jade;

import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.integration.jade.provider.PlasticEntityProvider;
import dev.anvilcraft.plasticraft.integration.jade.provider.CondenserTowerProvider;
import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** 可移动 Plasticraft 实体的 Jade 集成。 */
@WailaPlugin
public final class PlasticraftJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(PlasticEntityProvider.INSTANCE, AbstractPlasticEntity.class);
        registration.registerBlockDataProvider(CondenserTowerProvider.INSTANCE, CondenserTowerBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(PlasticEntityProvider.INSTANCE, AbstractPlasticEntity.class);
        registration.registerBlockComponent(CondenserTowerProvider.INSTANCE, CondenserTowerBlock.class);
    }
}
