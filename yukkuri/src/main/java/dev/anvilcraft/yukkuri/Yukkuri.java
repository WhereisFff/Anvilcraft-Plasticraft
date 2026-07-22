package dev.anvilcraft.yukkuri;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Yukkuri.MOD_ID)
public final class Yukkuri {
    public static final String MOD_ID = "yukkuri";
    public static final String MOD_NAME = "Yukkuri";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Yukkuri() {
        LOGGER.info("Loading {}", MOD_NAME);
    }

    public static ResourceLocation of(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
