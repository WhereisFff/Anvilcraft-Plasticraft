package dev.anvilcraft.plasticraft.integration.jade.provider;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContentSummary;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.dubhe.anvilcraft.util.FormattingUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ItemViewUtils;
import snownee.jade.api.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/** 实体与粘合板条箱共用 Jade 库存收集与显示上限，支持详细模式展开物品网格。 */
public enum PlasticItemStorageProvider implements IServerExtensionProvider<ItemStack>,
    IClientExtensionProvider<ItemStack, ItemView> {
    INSTANCE;

    public static final String OMITTED = "plasticraft_omitted_item_types";
    public static final String PREVIEW_COUNT = "plasticraft_preview_item_types";
    private static final String COUNTS = "Counts";
    private static final String USED = "Used";
    private static final String CAPACITY = "Capacity";

    @Override
    public @Nullable List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
        UniversalPlasticEntity plastic = plastic(accessor);
        if (plastic == null) {
            return null;
        }
        MoldedPlasticContentSummary summary = plastic.getMoldedContentSummary();
        if (!MoldingProductTypes.isCrate(summary.type())) {
            return null;
        }
        // 实体同步摘要只有八类；Jade 使用自己的有界收集器，保证 Shift 能显示后续物品。
        List<ViewGroup<ItemStack>> collected = ItemViewUtils.groupOf(plastic.getMoldedItemHandler(), accessor, source -> {
            UniversalPlasticEntity target = plastic(source);
            return target == null ? null : target.getMoldedItemHandler();
        });
        if (collected == null) {
            return null;
        }
        List<ViewGroup<ItemStack>> groups = new ArrayList<>(collected.size());
        int previewCount = 0;
        for (ViewGroup<ItemStack> source : collected) {
            ViewGroup<ItemStack> group = new ViewGroup<>(source.views.stream().map(stack -> stack.copyWithCount(1)).toList());
            group.getExtraData().merge(source.getExtraData());
            group.getExtraData().putIntArray(COUNTS, source.views.stream().mapToInt(ItemStack::getCount).toArray());
            group.getExtraData().putInt(USED, summary.occupiedSlots());
            group.getExtraData().putInt(CAPACITY, summary.capacity());
            groups.add(group);
            previewCount += group.views.size();
        }
        accessor.getServerData().putInt(OMITTED, Math.max(0, summary.items().size() + summary.omittedItemTypes() - previewCount));
        accessor.getServerData().putInt(PREVIEW_COUNT, previewCount);
        return groups;
    }

    private static @Nullable UniversalPlasticEntity plastic(Accessor<?> accessor) {
        Entity entity = null;
        if (accessor instanceof EntityAccessor target) {
            entity = target.getEntity();
        } else if (accessor instanceof BlockAccessor block
            && block.getBlockEntity() instanceof BondedEntityBlockEntity bonded) {
            entity = bonded.getOrCreateRenderEntity();
        }
        return entity instanceof UniversalPlasticEntity plastic ? plastic : null;
    }

    @Override
    public List<ClientViewGroup<ItemView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<ItemStack>> groups) {
        return ClientViewGroup.map(groups, ItemView::new, (group, client) -> {
            CompoundTag data = group.getExtraData();
            int[] counts = data.getIntArray(COUNTS);
            for (int index = 0; index < client.views.size(); index++) {
                int count = counts[index];
                ItemView view = client.views.get(index);
                view.item = view.item.copyWithCount(count);
                view.amountText = accessor.showDetails() ? Integer.toString(count) : FormattingUtil.toAbbrNum(count);
            }
            client.title = Component.translatable(
                "tooltip.anvilcraftplasticraft.jade.storage", data.getInt(USED), data.getInt(CAPACITY)
            ).withStyle(ChatFormatting.GRAY);
        });
    }

    @Override
    public ResourceLocation getUid() {
        return AnvilcraftPlasticraft.of("item_storage");
    }
}
