package dev.anvilcraft.plasticraft.init.block;

import dev.anvilcraft.lib.v2.registrum.util.entry.BlockEntityEntry;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.CondenserTowerBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.HighHeatFuelCauldronBlockEntity;
import dev.anvilcraft.plasticraft.block.entity.UniversalPlasticMeltBlockEntity;

import static dev.anvilcraft.plasticraft.AnvilcraftPlasticraft.REGISTRUM;

/** Plasticraft 方块实体注册。 */
public final class ModBlockEntities {
    public static final BlockEntityEntry<BondedEntityBlockEntity> BONDED_ENTITY = REGISTRUM
        .blockEntity("bonded_entity", BondedEntityBlockEntity::new)
        .validBlocks(
            ModBlocks.HARDEND_RESIN_CAULDRON,
            ModBlocks.CATALYTIC_PRESS_LID,
            ModBlocks.RESIN_ANVIL,
            ModBlocks.HARDEND_RESIN_ANVIL
        )
        .renderer(() -> dev.anvilcraft.plasticraft.client.renderer.blockentity.BondedEntityBlockEntityRenderer::new)
        .register();

    public static final BlockEntityEntry<CondenserTowerBlockEntity> CONDENSER_TOWER = REGISTRUM
        .blockEntity("condenser_tower", CondenserTowerBlockEntity::new)
        .validBlock(ModBlocks.CONDENSER_TOWER)
        .renderer(() ->
            dev.anvilcraft.plasticraft.client.renderer.blockentity.CondenserTowerBlockEntityRenderer::new)
        .register();

    public static final BlockEntityEntry<HighHeatFuelCauldronBlockEntity> HIGH_HEAT_FUEL_CAULDRON = REGISTRUM
        .<HighHeatFuelCauldronBlockEntity>blockEntity(
            "high_heat_fuel_cauldron",
            HighHeatFuelCauldronBlockEntity::new
        )
        .validBlock(ModBlocks.HIGH_HEAT_FUEL_CAULDRON)
        .renderer(() ->
            dev.anvilcraft.plasticraft.client.renderer.blockentity.HighHeatFuelCauldronBlockEntityRenderer::new)
        .register();

    public static final BlockEntityEntry<UniversalPlasticMeltBlockEntity> UNIVERSAL_PLASTIC_MELT = REGISTRUM
        .<UniversalPlasticMeltBlockEntity>blockEntity(
            "universal_plastic_melt",
            UniversalPlasticMeltBlockEntity::new
        )
        .validBlock(ModBlocks.UNIVERSAL_PLASTIC_MELT)
        .register();

    private ModBlockEntities() {
    }

    public static void register() {
        // 类加载时静态条目会挂接到 Registrum 事件总线。
    }
}
