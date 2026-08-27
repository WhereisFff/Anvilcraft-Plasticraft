package dev.anvilcraft.plasticraft.integration.jade.provider;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticCauldron;
import dev.anvilcraft.plasticraft.entity.PlasticCauldrons;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContentSummary;
import dev.anvilcraft.plasticraft.molding.product.PlasticCauldronLayout;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
    private static final String MOLDED_SUMMARY_KEY = "molded_summary";
    private static final ResourceLocation UID = AnvilcraftPlasticraft.of("hardend_resin_anvil");

    @Override
    public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
        Entity entity = accessor.getEntity();
        if (entity instanceof AbstractPlasticEntity anvil) {
            tag.putBoolean(PUSHABLE_KEY, anvil.isPushable());
            tag.putBoolean(MAGNETIZED_KEY, anvil.isMagnetized());
        }
        if (entity instanceof UniversalPlasticEntity plastic) {
            MoldedPlasticContentSummary summary = plastic.getMoldedContentSummary();
            if (MoldingProductTypes.isChest(summary.type()) || MoldingProductTypes.isCauldron(summary.type())) {
                tag.put(MOLDED_SUMMARY_KEY, summary.toTag(entity.registryAccess()));
            }
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

        boolean moldedSummary = accessor.getEntity() instanceof UniversalPlasticEntity
            && data.contains(MOLDED_SUMMARY_KEY, Tag.TAG_COMPOUND);
        if (moldedSummary) {
            appendMoldedSummary(
                tooltip,
                MoldedPlasticContentSummary.fromTag(
                    data.getCompound(MOLDED_SUMMARY_KEY),
                    accessor.getEntity().registryAccess()
                )
            );
        }

        // 成型制品的槽位内容已由摘要列出，只有硬化树脂锅需要实体同步列表兜底
        PlasticCauldron pot = moldedSummary ? null : PlasticCauldrons.of(accessor.getEntity());
        if (pot != null) {
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

    private static void appendMoldedSummary(ITooltip tooltip, MoldedPlasticContentSummary summary) {
        PlasticCauldronLayout layout = PlasticCauldronLayout.of(summary.type());
        if (layout == null && !MoldingProductTypes.isChest(summary.type())) return;
        if (layout != null) {
            appendItems(tooltip, summary);
            return;
        }
        appendItemSummary(tooltip, summary, summary.capacity());
    }

    private static void appendItemSummary(ITooltip tooltip, MoldedPlasticContentSummary summary, int slots) {
        tooltip.add(Component.translatable(
            "tooltip.anvilcraftplasticraft.molded_chest_contents",
            summary.occupiedSlots(),
            slots
        ).withStyle(ChatFormatting.GRAY));
        appendItems(tooltip, summary);
    }

    private static void appendItems(ITooltip tooltip, MoldedPlasticContentSummary summary) {
        IElementHelper helper = IElementHelper.get();
        for (MoldedPlasticContentSummary.ItemEntry item : summary.items()) {
            List<IElement> elements = new ArrayList<>(2);
            elements.add(helper.smallItem(item.stack()));
            elements.add(helper.text(Component.translatable(
                "tooltip.anvilcraftplasticraft.jade.item_count",
                IDisplayHelper.get().stripColor(item.stack().getHoverName()),
                item.count()
            ).withStyle(ChatFormatting.GRAY)).message(null));
            tooltip.add(elements);
        }
        appendOmitted(tooltip, summary.omittedItemTypes());
    }

    private static void appendOmitted(ITooltip tooltip, int count) {
        if (count > 0) {
            tooltip.add(Component.translatable(
                "tooltip.anvilcraftplasticraft.molded_more_contents",
                count
            ).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
