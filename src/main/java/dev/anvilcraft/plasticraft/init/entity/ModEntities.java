package dev.anvilcraft.plasticraft.init.entity;

import dev.anvilcraft.lib.v2.registrum.util.entry.EntityEntry;
import dev.anvilcraft.plasticraft.client.renderer.entity.CatalyticPressLidRenderer;
import dev.anvilcraft.plasticraft.client.renderer.entity.HardenedResinAnvilRenderer;
import dev.anvilcraft.plasticraft.client.renderer.entity.HardenedResinCauldronRenderer;
import dev.anvilcraft.plasticraft.client.renderer.entity.ResinAnvilRenderer;
import dev.anvilcraft.plasticraft.entity.CatalyticPressLidEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.ResinAnvilEntity;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import net.minecraft.world.entity.MobCategory;

import static dev.anvilcraft.plasticraft.AnvilcraftPlasticraft.REGISTRUM;

public final class ModEntities {
    public static final EntityEntry<CatalyticPressLidEntity> CATALYTIC_PRESS_LID = REGISTRUM
        .<CatalyticPressLidEntity>entity("catalytic_press_lid", CatalyticPressLidEntity::new, MobCategory.MISC)
        .properties(builder -> builder
            .sized(CatalyticPressLidEntity.WIDTH, CatalyticPressLidEntity.HEIGHT)
            .clientTrackingRange(10)
            .updateInterval(1))
        .lang("Catalytic Press Lid")
        .renderer(() -> CatalyticPressLidRenderer::new)
        .register();

    public static final EntityEntry<HardenedResinAnvilEntity> HARDEND_RESIN_ANVIL = REGISTRUM
        .<HardenedResinAnvilEntity>entity("hardend_resin_anvil", HardenedResinAnvilEntity::new, MobCategory.MISC)
        .properties(builder -> builder
            .sized(HardenedResinAnvilEntity.COLLISION_SIZE, HardenedResinAnvilEntity.COLLISION_SIZE)
            .clientTrackingRange(10)
            .updateInterval(1))
        .lang("Hardened Resin Anvil")
        .renderer(() -> HardenedResinAnvilRenderer::new)
        .register();

    public static final EntityEntry<HardenedResinCauldronEntity> HARDEND_RESIN_CAULDRON = REGISTRUM
        .<HardenedResinCauldronEntity>entity("hardend_resin_cauldron", HardenedResinCauldronEntity::new, MobCategory.MISC)
        .properties(builder -> builder
            .sized(HardenedResinCauldronEntity.COLLISION_SIZE, HardenedResinCauldronEntity.COLLISION_SIZE)
            .clientTrackingRange(10)
            .updateInterval(1))
        .lang("Hardened Resin Cauldron")
        .renderer(() -> HardenedResinCauldronRenderer::new)
        .register();

    public static final EntityEntry<ResinAnvilEntity> RESIN_ANVIL = REGISTRUM
        .<ResinAnvilEntity>entity("resin_anvil", ResinAnvilEntity::new, MobCategory.MISC)
        .properties(builder -> builder
            .sized(ResinAnvilEntity.COLLISION_SIZE, ResinAnvilEntity.COLLISION_SIZE)
            .clientTrackingRange(10)
            .updateInterval(1))
        .lang("Resin Anvil")
        .renderer(() -> ResinAnvilRenderer::new)
        .register();

    private ModEntities() {
    }

    public static void register() {
        CatalyticPressLidEntity.configureDefaultDrop(ModBlocks.CATALYTIC_PRESS_LID::asStack);
        HardenedResinAnvilEntity.configureDefaultDrop(ModBlocks.HARDEND_RESIN_ANVIL::asStack);
        HardenedResinCauldronEntity.configureDefaultDrop(ModBlocks.HARDEND_RESIN_CAULDRON::asStack);
        ResinAnvilEntity.configureDefaultDrop(ModBlocks.RESIN_ANVIL::asStack);
    }
}
