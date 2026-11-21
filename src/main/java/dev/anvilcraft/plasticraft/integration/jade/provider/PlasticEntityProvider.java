package dev.anvilcraft.plasticraft.integration.jade.provider;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticCauldron;
import dev.anvilcraft.plasticraft.entity.PlasticCauldrons;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.integration.jade.PlasticJadeLayout;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContentSummary;
import dev.anvilcraft.plasticraft.molding.product.PlasticCauldronLayout;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.Element;
import snownee.jade.api.ui.IDisplayHelper;
import snownee.jade.api.ui.JadeUI;

import java.util.ArrayList;
import java.util.List;

/** 为 Jade 指向的可移动 Plasticraft 实体添加简要状态信息。 */
public enum PlasticEntityProvider implements IServerDataProvider<EntityAccessor> {
    INSTANCE;

    private static final String PUSHABLE_KEY = "pushable";
    private static final String MAGNETIZED_KEY = "magnetized";
    private static final String MOLDED_SUMMARY_KEY = "molded_summary";
    private static final Identifier UID = AnvilcraftPlasticraft.of("hardend_resin_anvil");

    @Override
    public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
        Entity entity = accessor.getEntity();
        if (entity instanceof AbstractPlasticEntity anvil) {
            tag.putBoolean(PUSHABLE_KEY, anvil.isPushable());
            tag.putBoolean(MAGNETIZED_KEY, anvil.isMagnetized());
        }
        if (entity instanceof UniversalPlasticEntity plastic) {
            MoldedPlasticContentSummary summary = plastic.getMoldedContentSummary();
            if (MoldingProductTypes.isCauldron(summary.type())) {
                tag.put(MOLDED_SUMMARY_KEY, summary.toTag(entity.registryAccess()));
            }
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    public enum Client implements IEntityComponentProvider {
        INSTANCE;

        @Override
        public @Nullable Element getIcon(
            EntityAccessor accessor,
            IPluginConfig config,
            @Nullable Element currentIcon
        ) {
            if (!(accessor.getEntity() instanceof AbstractPlasticEntity plastic)) return currentIcon;
            ItemStack picked = plastic.getPickResult();
            return picked.isEmpty() ? currentIcon : JadeUI.item(picked);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (!(accessor.getEntity() instanceof AbstractPlasticEntity)) {
                return;
            }

            CompoundTag data = accessor.getServerData();
            if (data.getBooleanOr(PUSHABLE_KEY, false)) {
                tooltip.add(JadeUI.text(Component.literal("  ")
                    .append(Component.translatable("tooltip.anvilcraftplasticraft.jade.pushable"))
                    .withStyle(ChatFormatting.GRAY)).tag(PlasticJadeLayout.PUSHABLE));
            }
            if (data.getBooleanOr(MAGNETIZED_KEY, false)) {
                tooltip.add(Component.translatable("tooltip.anvilcraftplasticraft.magnetized"));
            }

            boolean moldedSummary = accessor.getEntity() instanceof UniversalPlasticEntity
                && data.getCompound(MOLDED_SUMMARY_KEY).isPresent();
            if (moldedSummary) {
                appendMoldedSummary(
                    tooltip,
                    MoldedPlasticContentSummary.fromTag(
                        data.getCompoundOrEmpty(MOLDED_SUMMARY_KEY),
                        accessor.getEntity().registryAccess()
                    )
                );
            }

            // 成型制品的槽位内容已由摘要列出，只有硬化树脂锅需要实体同步列表兜底
            PlasticCauldron pot = moldedSummary ? null : PlasticCauldrons.of(accessor.getEntity());
            if (pot != null) {

                List<ItemStack> items = pot.getSyncedItems();
                if (!items.isEmpty()) {
                    for (ItemStack stack : items) {
                        List<Element> itemElements = new ArrayList<>(2);
                        itemElements.add(JadeUI.smallItem(stack));
                        itemElements.add(JadeUI.text(Component.translatable(
                            "tooltip.anvilcraftplasticraft.jade.item_count",
                            IDisplayHelper.get().stripColor(stack.getHoverName()),
                            stack.getCount()
                        ).withStyle(ChatFormatting.GRAY)));
                        tooltip.add(itemElements);
                    }
                }
            }
        }

        private static void appendMoldedSummary(ITooltip tooltip, MoldedPlasticContentSummary summary) {
            if (PlasticCauldronLayout.of(summary.type()) != null) {
                appendItems(tooltip, summary);
            }
        }

        private static void appendItems(ITooltip tooltip, MoldedPlasticContentSummary summary) {

            for (MoldedPlasticContentSummary.ItemEntry item : summary.items()) {
                List<Element> elements = new ArrayList<>(2);
                elements.add(JadeUI.smallItem(item.stack()));
                elements.add(JadeUI.text(Component.translatable(
                    "tooltip.anvilcraftplasticraft.jade.item_count",
                    IDisplayHelper.get().stripColor(item.stack().getHoverName()),
                    item.count()
                ).withStyle(ChatFormatting.GRAY)));
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
        public Identifier getUid() {
            return PlasticEntityProvider.INSTANCE.getUid();
        }
    }
}
