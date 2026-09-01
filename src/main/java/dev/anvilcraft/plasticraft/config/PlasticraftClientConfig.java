package dev.anvilcraft.plasticraft.config;

import dev.anvilcraft.lib.v2.config.Comment;
import dev.anvilcraft.lib.v2.config.Config;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.neoforged.fml.config.ModConfig;

/** Plasticraft 客户端配置。 */
@Config(name = AnvilcraftPlasticraft.MOD_ID, type = ModConfig.Type.CLIENT)
public final class PlasticraftClientConfig {
    @Comment("Whether plastic sections start with 16-color items folded; click a banner to toggle a section")
    public boolean foldCreativeColorVariants = true;
}
