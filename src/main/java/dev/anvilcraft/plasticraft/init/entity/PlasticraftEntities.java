package dev.anvilcraft.plasticraft.init.entity;

import dev.anvilcraft.lib.v2.registrum.util.entry.EntityEntry;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.renderer.entity.CatalyticPressLidRenderer;
import dev.anvilcraft.plasticraft.client.renderer.entity.HardenedResinAnvilRenderer;
import dev.anvilcraft.plasticraft.client.renderer.entity.HardenedResinCauldronRenderer;
import dev.anvilcraft.plasticraft.client.renderer.entity.ResinAnvilRenderer;
import dev.anvilcraft.plasticraft.client.renderer.entity.UniversalPlasticEntityRenderer;
import dev.anvilcraft.plasticraft.entity.CatalyticPressLidEntity;
import dev.anvilcraft.plasticraft.entity.ClearPlasticEntity;
import dev.anvilcraft.plasticraft.entity.ElasticPlasticEntity;
import dev.anvilcraft.plasticraft.entity.EngineeringPlasticEntity;
import dev.anvilcraft.plasticraft.entity.HeatResistantPlasticEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.ResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import net.minecraft.world.entity.MobCategory;

public final class PlasticraftEntities {
    public static final EntityEntry<CatalyticPressLidEntity> CATALYTIC_PRESS_LID = AnvilcraftPlasticraft.REGISTRUM
        .<CatalyticPressLidEntity>entity("catalytic_press_lid", CatalyticPressLidEntity::new, MobCategory.MISC)
        .properties(builder -> builder
            .sized(CatalyticPressLidEntity.WIDTH, CatalyticPressLidEntity.HEIGHT)
            .clientTrackingRange(10)
            .updateInterval(1))
        .lang("Catalytic Press Lid")
        .renderer(() -> CatalyticPressLidRenderer::new)
        .register();

    public static final EntityEntry<HardenedResinAnvilEntity> HARDEND_RESIN_ANVIL = AnvilcraftPlasticraft.REGISTRUM
        .<HardenedResinAnvilEntity>entity("hardend_resin_anvil", HardenedResinAnvilEntity::new, MobCategory.MISC)
        .properties(builder -> builder
            .sized(HardenedResinAnvilEntity.COLLISION_SIZE, HardenedResinAnvilEntity.COLLISION_SIZE)
            .clientTrackingRange(10)
            .updateInterval(1))
        .lang("Hardened Resin Anvil")
        .renderer(() -> HardenedResinAnvilRenderer::new)
        .register();

    public static final EntityEntry<HardenedResinCauldronEntity> HARDEND_RESIN_CAULDRON = AnvilcraftPlasticraft.REGISTRUM
        .<HardenedResinCauldronEntity>entity("hardend_resin_cauldron", HardenedResinCauldronEntity::new, MobCategory.MISC)
        .properties(builder -> builder
            .sized(HardenedResinCauldronEntity.COLLISION_SIZE, HardenedResinCauldronEntity.COLLISION_SIZE)
            .clientTrackingRange(10)
            .updateInterval(1))
        .lang("Hardened Resin Cauldron")
        .renderer(() -> HardenedResinCauldronRenderer::new)
        .register();

    public static final EntityEntry<ResinAnvilEntity> RESIN_ANVIL = AnvilcraftPlasticraft.REGISTRUM
        .<ResinAnvilEntity>entity("resin_anvil", ResinAnvilEntity::new, MobCategory.MISC)
        .properties(builder -> builder
            .sized(ResinAnvilEntity.COLLISION_SIZE, ResinAnvilEntity.COLLISION_SIZE)
            .clientTrackingRange(10)
            .updateInterval(1))
        .lang("Resin Anvil")
        .renderer(() -> ResinAnvilRenderer::new)
        .register();

    public static final EntityEntry<UniversalPlasticEntity> UNIVERSAL_PLASTIC = AnvilcraftPlasticraft.REGISTRUM
        .<UniversalPlasticEntity>entity("universal_plastic", UniversalPlasticEntity::new, MobCategory.MISC)
        .properties(builder -> builder
            .sized(UniversalPlasticEntity.COLLISION_SIZE, UniversalPlasticEntity.COLLISION_SIZE)
            .clientTrackingRange(10)
            .updateInterval(1))
        .lang("Universal Plastic Block")
        .renderer(() -> UniversalPlasticEntityRenderer::new)
        .register();
    public static final EntityEntry<EngineeringPlasticEntity> ENGINEERING_PLASTIC = AnvilcraftPlasticraft.REGISTRUM
        .<EngineeringPlasticEntity>entity("engineering_plastic", EngineeringPlasticEntity::new, MobCategory.MISC)
        .properties(builder -> builder
            .sized(UniversalPlasticEntity.COLLISION_SIZE, UniversalPlasticEntity.COLLISION_SIZE)
            .clientTrackingRange(10)
            .updateInterval(1))
        .lang("Engineering Plastic Block")
        .renderer(() -> UniversalPlasticEntityRenderer::new)
        .register();
    public static final EntityEntry<HeatResistantPlasticEntity> HEAT_RESISTANT_PLASTIC =
        AnvilcraftPlasticraft.REGISTRUM
            .<HeatResistantPlasticEntity>entity(
                "heat_resistant_plastic",
                HeatResistantPlasticEntity::new,
                MobCategory.MISC
            )
            .properties(builder -> builder
                .sized(UniversalPlasticEntity.COLLISION_SIZE, UniversalPlasticEntity.COLLISION_SIZE)
                .clientTrackingRange(10)
                .updateInterval(1))
            .lang("Heat-Resistant Plastic Block")
            .renderer(() -> UniversalPlasticEntityRenderer::new)
            .register();
    public static final EntityEntry<ClearPlasticEntity> CLEAR_PLASTIC = AnvilcraftPlasticraft.REGISTRUM
        .<ClearPlasticEntity>entity("clear_plastic", ClearPlasticEntity::new, MobCategory.MISC)
        .properties(builder -> builder
            .sized(UniversalPlasticEntity.COLLISION_SIZE, UniversalPlasticEntity.COLLISION_SIZE)
            .clientTrackingRange(10)
            .updateInterval(1))
        .lang("Clear Plastic Block")
        .renderer(() -> UniversalPlasticEntityRenderer::new)
        .register();

    public static final EntityEntry<ElasticPlasticEntity> ELASTIC_PLASTIC = AnvilcraftPlasticraft.REGISTRUM
        .<ElasticPlasticEntity>entity("elastic_plastic", ElasticPlasticEntity::new, MobCategory.MISC)
        .properties(builder -> builder
            .sized(UniversalPlasticEntity.COLLISION_SIZE, UniversalPlasticEntity.COLLISION_SIZE)
            .clientTrackingRange(10)
            .updateInterval(1))
        .lang("Elastic Plastic Block")
        .renderer(() -> UniversalPlasticEntityRenderer::new)
        .register();

    private PlasticraftEntities() {
    }

    public static void register() {
        CatalyticPressLidEntity.configureDefaultDrop(PlasticraftBlocks.CATALYTIC_PRESS_LID::asStack);
        HardenedResinAnvilEntity.configureDefaultDrop(PlasticraftBlocks.HARDEND_RESIN_ANVIL::asStack);
        HardenedResinCauldronEntity.configureDefaultDrop(PlasticraftBlocks.HARDEND_RESIN_CAULDRON::asStack);
        ResinAnvilEntity.configureDefaultDrop(PlasticraftBlocks.RESIN_ANVIL::asStack);
        UniversalPlasticEntity.configureDefaultDrop(PlasticraftBlocks.UNIVERSAL_PLASTIC::asStack);
    }
}
