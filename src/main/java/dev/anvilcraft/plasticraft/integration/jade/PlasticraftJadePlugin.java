package dev.anvilcraft.plasticraft.integration.jade;

import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.block.HighHeatFuelCauldronBlock;
import dev.anvilcraft.plasticraft.block.HighViscosityResinCauldronBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.block.PlasticOilCauldronBlock;
import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltCauldronBlock;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.integration.jade.provider.BondedBlockProvider;
import dev.anvilcraft.plasticraft.integration.jade.provider.BondedEntityProvider;
import dev.anvilcraft.plasticraft.integration.jade.provider.PlasticEntityProvider;
import dev.anvilcraft.plasticraft.integration.jade.provider.PlasticFluidStorageProvider;
import dev.anvilcraft.plasticraft.integration.jade.provider.PlasticItemStorageProvider;
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
        registration.registerItemStorage(PlasticItemStorageProvider.INSTANCE, AbstractPlasticEntity.class);
        registration.registerItemStorage(PlasticItemStorageProvider.INSTANCE, BondedEntityBlockEntity.class);
        registration.registerFluidStorage(PlasticFluidStorageProvider.INSTANCE, AbstractPlasticEntity.class);
        registration.registerFluidStorage(PlasticFluidStorageProvider.INSTANCE, BondedEntityBlockEntity.class);
        registration.registerFluidStorage(PlasticFluidStorageProvider.INSTANCE, PlasticOilCauldronBlock.class);
        registration.registerFluidStorage(PlasticFluidStorageProvider.INSTANCE, UniversalPlasticMeltCauldronBlock.class);
        registration.registerFluidStorage(PlasticFluidStorageProvider.INSTANCE, HighViscosityResinCauldronBlock.class);
        registration.registerFluidStorage(PlasticFluidStorageProvider.INSTANCE, HighHeatFuelCauldronBlock.class);
        registration.registerFluidStorage(PlasticFluidStorageProvider.INSTANCE, PlasticMoldingChamberBlock.class);
        registration.registerFluidStorage(PlasticFluidStorageProvider.INSTANCE, CondenserTowerBlock.class);
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
        registration.registerItemStorageClient(PlasticItemStorageProvider.INSTANCE);
        registration.registerFluidStorageClient(PlasticFluidStorageProvider.INSTANCE);
        registration.addTooltipCollectedCallback(PlasticJadeLayout::finish);
        registration.registerBlockComponent(
            PlasticMoldingChamberProvider.INSTANCE,
            PlasticMoldingChamberBlock.class
        );
    }
}
