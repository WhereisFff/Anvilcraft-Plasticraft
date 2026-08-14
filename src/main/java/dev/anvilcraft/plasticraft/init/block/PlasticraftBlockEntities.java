package dev.anvilcraft.plasticraft.init.block;

import dev.anvilcraft.lib.v2.registrum.util.entry.BlockEntityEntry;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.CondenserTowerBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.HighHeatFuelCauldronBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.Plastic3DPrintingComponentBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.UniversalPlasticMeltBlockEntity;
import dev.anvilcraft.plasticraft.client.renderer.blockentity.BondedEntityBlockEntityRenderer;
import dev.anvilcraft.plasticraft.client.renderer.blockentity.CondenserTowerBlockEntityRenderer;
import dev.anvilcraft.plasticraft.client.renderer.blockentity.HighHeatFuelCauldronBlockEntityRenderer;
import dev.anvilcraft.plasticraft.client.renderer.blockentity.Plastic3DPrintingComponentRenderer;
import dev.anvilcraft.plasticraft.client.renderer.blockentity.PlasticMoldingChamberRenderer;

/** Plasticraft 方块实体注册。 */
public final class PlasticraftBlockEntities {
    public static final BlockEntityEntry<AllayLoungeBlockEntity> ALLAY_LOUNGE = AnvilcraftPlasticraft.REGISTRUM
        .<AllayLoungeBlockEntity>blockEntity("allay_lounge", AllayLoungeBlockEntity::new)
        .validBlock(PlasticraftBlocks.ALLAY_LOUNGE)
        .register();

    public static final BlockEntityEntry<Plastic3DPrintingComponentBlockEntity> PLASTIC_3D_PRINTING_COMPONENT =
        AnvilcraftPlasticraft.REGISTRUM
            .<Plastic3DPrintingComponentBlockEntity>blockEntity(
                "plastic_3d_printing_component",
                Plastic3DPrintingComponentBlockEntity::new
            )
            .validBlock(PlasticraftBlocks.PLASTIC_3D_PRINTING_COMPONENT)
            .renderer(() -> Plastic3DPrintingComponentRenderer::new)
            .register();

    public static final BlockEntityEntry<PlasticMoldingChamberBlockEntity> PLASTIC_MOLDING_CHAMBER = AnvilcraftPlasticraft.REGISTRUM
        .<PlasticMoldingChamberBlockEntity>blockEntity(
            "plastic_molding_chamber",
            PlasticMoldingChamberBlockEntity::new
        )
        .validBlock(PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER)
        .renderer(() -> PlasticMoldingChamberRenderer::new)
        .register();

    public static final BlockEntityEntry<BondedEntityBlockEntity> BONDED_ENTITY = AnvilcraftPlasticraft.REGISTRUM
        .blockEntity("bonded_entity", BondedEntityBlockEntity::new)
        .validBlocks(
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON,
            PlasticraftBlocks.CATALYTIC_PRESS_LID,
            PlasticraftBlocks.RESIN_ANVIL,
            PlasticraftBlocks.HARDEND_RESIN_ANVIL,
            PlasticraftBlocks.UNIVERSAL_PLASTIC
        )
        .renderer(() -> BondedEntityBlockEntityRenderer::new)
        .register();

    public static final BlockEntityEntry<CondenserTowerBlockEntity> CONDENSER_TOWER = AnvilcraftPlasticraft.REGISTRUM
        .blockEntity("condenser_tower", CondenserTowerBlockEntity::new)
        .validBlock(PlasticraftBlocks.CONDENSER_TOWER)
        .renderer(() ->
            CondenserTowerBlockEntityRenderer::new)
        .register();

    public static final BlockEntityEntry<HighHeatFuelCauldronBlockEntity> HIGH_HEAT_FUEL_CAULDRON = AnvilcraftPlasticraft.REGISTRUM
        .<HighHeatFuelCauldronBlockEntity>blockEntity(
            "high_heat_fuel_cauldron",
            HighHeatFuelCauldronBlockEntity::new
        )
        .validBlock(PlasticraftBlocks.HIGH_HEAT_FUEL_CAULDRON)
        .renderer(() ->
            HighHeatFuelCauldronBlockEntityRenderer::new)
        .register();

    public static final BlockEntityEntry<UniversalPlasticMeltBlockEntity> UNIVERSAL_PLASTIC_MELT = AnvilcraftPlasticraft.REGISTRUM
        .<UniversalPlasticMeltBlockEntity>blockEntity(
            "universal_plastic_melt",
            UniversalPlasticMeltBlockEntity::new
        )
        .validBlock(PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT)
        .register();

    private PlasticraftBlockEntities() {
    }

    public static void register() {
        // 类加载时静态条目会挂接到 Registrum 事件总线。
    }
}
