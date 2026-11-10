package dev.anvilcraft.plasticraft.config;

import com.google.gson.annotations.SerializedName;
import dev.anvilcraft.lib.v2.config.BoundedDiscrete;
import dev.anvilcraft.lib.v2.config.Comment;
import dev.anvilcraft.lib.v2.config.Config;
import dev.anvilcraft.lib.v2.config.util.TranslatableEnum;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.neoforged.fml.config.ModConfig;

/** Plasticraft 客户端配置。 */
@Config(name = AnvilcraftPlasticraft.MOD_ID, type = ModConfig.Type.CLIENT)
public final class PlasticraftClientConfig {
    @Comment("Whether plastic sections start with 16-color items folded; click a banner to toggle a section")
    public boolean foldCreativeColorVariants = true;

    @Comment("Placement preview for Plasticraft multipart machines and plastic entities")
    public PlacementPreviewMode placementPreviewMode = PlacementPreviewMode.OUTLINE;

    @Comment("Opacity of the ghost placement preview")
    @BoundedDiscrete(min = 0, max = 1)
    public double placementPreviewGhostOpacity = 0.3;

    @Comment("Opacity of the outline placement preview")
    @BoundedDiscrete(min = 0, max = 1)
    public double placementPreviewOutlineOpacity = 0.5;

    /** 多方块与实体共用的放置预览模式。 */
    public enum PlacementPreviewMode implements TranslatableEnum {
        @SerializedName("Ghost")
        GHOST,
        @SerializedName("Outline")
        OUTLINE,
        @SerializedName("Off")
        OFF
    }
}
