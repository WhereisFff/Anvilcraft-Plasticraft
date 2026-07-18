package dev.anvilcraft.plasticraft.init;

import dev.anvilcraft.lib.v2.registrum.util.entry.EntityEntry;
import dev.anvilcraft.plasticraft.entity.PlasticAnvilEntity;
import net.minecraft.world.entity.MobCategory;

import static dev.anvilcraft.plasticraft.AnvilcraftPlasticraft.REGISTRUM;

public final class PlasticEntities {
    public static final EntityEntry<PlasticAnvilEntity> PLASTIC_ANVIL = REGISTRUM
        .<PlasticAnvilEntity>entity("plastic_anvil", PlasticAnvilEntity::new, MobCategory.MISC)
        .properties(builder -> builder
            .sized(1.0F, 1.0F)
            .clientTrackingRange(10)
            .updateInterval(1))
        .renderer(() -> dev.anvilcraft.plasticraft.client.renderer.entity.PlasticAnvilRenderer::new)
        .register();

    private PlasticEntities() {
    }

    public static void register() {
        PlasticAnvilEntity.configureDefaultDrop(PlasticBlocks.PLASTIC_ANVIL::asStack);
    }
}
