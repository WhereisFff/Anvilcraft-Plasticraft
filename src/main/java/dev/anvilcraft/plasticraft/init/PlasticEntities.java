package dev.anvilcraft.plasticraft.init;

import dev.anvilcraft.lib.v2.registrum.util.entry.EntityEntry;
import dev.anvilcraft.plasticraft.entity.PlasticAnvilEntity;
import dev.anvilcraft.plasticraft.entity.PlasticPotEntity;
import net.minecraft.world.entity.MobCategory;

import static dev.anvilcraft.plasticraft.AnvilcraftPlasticraft.REGISTRUM;

public final class PlasticEntities {
    public static final EntityEntry<PlasticAnvilEntity> PLASTIC_ANVIL = REGISTRUM
        .<PlasticAnvilEntity>entity("plastic_anvil", PlasticAnvilEntity::new, MobCategory.MISC)
        .properties(builder -> builder
            .sized(PlasticAnvilEntity.COLLISION_SIZE, PlasticAnvilEntity.COLLISION_SIZE)
            .clientTrackingRange(10)
            .updateInterval(1))
        .renderer(() -> dev.anvilcraft.plasticraft.client.renderer.entity.PlasticAnvilRenderer::new)
        .register();

    public static final EntityEntry<PlasticPotEntity> PLASTIC_POT = REGISTRUM
        .<PlasticPotEntity>entity("plastic_pot", PlasticPotEntity::new, MobCategory.MISC)
        .properties(builder -> builder
            .sized(PlasticPotEntity.COLLISION_SIZE, PlasticPotEntity.COLLISION_SIZE)
            .clientTrackingRange(10)
            .updateInterval(1))
        .renderer(() -> dev.anvilcraft.plasticraft.client.renderer.entity.PlasticPotRenderer::new)
        .register();

    private PlasticEntities() {
    }

    public static void register() {
        PlasticAnvilEntity.configureDefaultDrop(PlasticBlocks.PLASTIC_ANVIL::asStack);
        PlasticPotEntity.configureDefaultDrop(PlasticBlocks.PLASTIC_POT::asStack);
    }
}
