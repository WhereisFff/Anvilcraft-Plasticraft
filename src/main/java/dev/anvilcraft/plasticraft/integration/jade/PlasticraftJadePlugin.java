package dev.anvilcraft.plasticraft.integration.jade;

import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.integration.jade.provider.BondedBlockProvider;
import dev.anvilcraft.plasticraft.integration.jade.provider.BondedEntityProvider;
import dev.anvilcraft.plasticraft.integration.jade.provider.CondenserTowerProvider;
import dev.anvilcraft.plasticraft.integration.jade.provider.PlasticEntityProvider;
import dev.anvilcraft.plasticraft.integration.jade.provider.PlasticMoldingChamberProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Plasticraft 实体、粘连方块与冷凝塔的 Jade 集成。 */
@WailaPlugin
public final class PlasticraftJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(PlasticEntityProvider.INSTANCE, AbstractPlasticEntity.class);
        registration.registerEntityDataProvider(BondedEntityProvider.INSTANCE, Entity.class);
        registration.registerBlockDataProvider(BondedBlockProvider.INSTANCE, Block.class);
        registration.registerBlockDataProvider(CondenserTowerProvider.INSTANCE, CondenserTowerBlock.class);
        registration.registerBlockDataProvider(
            PlasticMoldingChamberProvider.INSTANCE,
            PlasticMoldingChamberBlock.class
        );
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityIcon(PlasticEntityProvider.INSTANCE, AbstractPlasticEntity.class);
        registration.registerEntityComponent(PlasticEntityProvider.INSTANCE, AbstractPlasticEntity.class);
        registration.registerEntityComponent(BondedEntityProvider.INSTANCE, Entity.class);
        registration.registerBlockComponent(BondedBlockProvider.INSTANCE, Block.class);
        registration.registerBlockComponent(CondenserTowerProvider.INSTANCE, CondenserTowerBlock.class);
        registration.registerBlockComponent(
            PlasticMoldingChamberProvider.INSTANCE,
            PlasticMoldingChamberBlock.class
        );
    }
}
