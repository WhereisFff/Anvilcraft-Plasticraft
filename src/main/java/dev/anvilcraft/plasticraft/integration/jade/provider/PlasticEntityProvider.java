package dev.anvilcraft.plasticraft.integration.jade.provider;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IDisplayHelper;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import java.util.ArrayList;
import java.util.List;

/** 为 Jade 指向的可移动 Plasticraft 实体添加简要状态信息。 */
public enum PlasticEntityProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    private static final String PUSHABLE_KEY = "pushable";
    private static final String MAGNETIZED_KEY = "magnetized";
    private static final ResourceLocation UID = AnvilcraftPlasticraft.of("hardend_resin_anvil");

    @Override
    public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
        Entity entity = accessor.getEntity();
        if (entity instanceof AbstractPlasticEntity anvil) {
            tag.putBoolean(PUSHABLE_KEY, anvil.isPushable());
            tag.putBoolean(MAGNETIZED_KEY, anvil.isMagnetized());
        }
    }

    @Override
    public IElement getIcon(
        EntityAccessor accessor,
        IPluginConfig config,
        IElement currentIcon
    ) {
        if (!(accessor.getEntity() instanceof AbstractPlasticEntity plastic)) return currentIcon;
        ItemStack picked = plastic.getPickResult();
        return picked.isEmpty() ? currentIcon : IElementHelper.get().item(picked);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof AbstractPlasticEntity)) {
            return;
        }

        CompoundTag data = accessor.getServerData();
        if (data.getBoolean(PUSHABLE_KEY)) {
            tooltip.add(Component.translatable("tooltip.anvilcraftplasticraft.jade.pushable"));
        }
        if (data.getBoolean(MAGNETIZED_KEY)) {
            tooltip.add(Component.translatable("tooltip.anvilcraftplasticraft.magnetized"));
        }

        if (accessor.getEntity() instanceof HardenedResinCauldronEntity pot) {
            IElementHelper helper = IElementHelper.get();
            List<ItemStack> items = pot.getSyncedItems();
            if (!items.isEmpty()) {
                for (ItemStack stack : items) {
                    List<IElement> itemElements = new ArrayList<>(2);
                    itemElements.add(helper.smallItem(stack));
                    itemElements.add(helper.text(Component.translatable(
                        "tooltip.anvilcraftplasticraft.jade.item_count",
                        IDisplayHelper.get().stripColor(stack.getHoverName()),
                        stack.getCount()
                    ).withStyle(ChatFormatting.GRAY)).message(null));
                    tooltip.add(itemElements);
                }
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
