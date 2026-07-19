package dev.anvilcraft.plasticraft.integration.jade.provider;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.PlasticAnvilBlock;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticAnvilEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Adds concise state information for a plastic anvil targeted by Jade. */
public enum PlasticAnvilProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    private static final String COLOR_KEY = "plastic_color";
    private static final String PUSHABLE_KEY = "pushable";
    private static final String MAGNETIZED_KEY = "magnetized";
    private static final ResourceLocation UID = AnvilcraftPlasticraft.of("plastic_anvil");

    @Override
    public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
        Entity entity = accessor.getEntity();
        if (entity instanceof AbstractPlasticAnvilEntity anvil) {
            var state = anvil.getDisplayState();
            DyeColor color = state.hasProperty(PlasticAnvilBlock.COLOR)
                ? state.getValue(PlasticAnvilBlock.COLOR)
                : DyeColor.WHITE;
            tag.putString(COLOR_KEY, color.getName());
            tag.putBoolean(PUSHABLE_KEY, anvil.isPushable());
            tag.putBoolean(MAGNETIZED_KEY, anvil.isMagnetized());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof AbstractPlasticAnvilEntity)) {
            return;
        }

        CompoundTag data = accessor.getServerData();
        String colorName = data.getString(COLOR_KEY);
        if (colorName.isEmpty()) {
            colorName = DyeColor.WHITE.getName();
        }
        Component color = Component.translatable("color.minecraft." + colorName);
        tooltip.add(Component.translatable("tooltip.anvilcraftplasticraft.jade.color", color));

        if (data.getBoolean(PUSHABLE_KEY)) {
            tooltip.add(Component.translatable("tooltip.anvilcraftplasticraft.jade.pushable"));
        }
        if (data.getBoolean(MAGNETIZED_KEY)) {
            tooltip.add(Component.translatable("tooltip.anvilcraftplasticraft.magnetized"));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
