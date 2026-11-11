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
import snownee.jade.api.view.ViewGroup;

import java.util.List;

/** 实体与粘合板条箱共用有界仓储摘要，物品由 Jade 标准网格绘制数量角标。 */
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
        Entity entity = null;
        if (accessor instanceof EntityAccessor target) {
            entity = target.getEntity();
        } else if (accessor instanceof BlockAccessor block
            && block.getBlockEntity() instanceof BondedEntityBlockEntity bonded) {
            entity = bonded.getOrCreateRenderEntity();
        }
        if (!(entity instanceof UniversalPlasticEntity plastic)) {
            return null;
        }
        MoldedPlasticContentSummary summary = plastic.getMoldedContentSummary();
        if (!MoldingProductTypes.isCrate(summary.type())) {
            return null;
        }
        // Jade 的物品网格不绘制省略数量，完成回调将这行信息追加到同一分组内部。
        accessor.getServerData().putInt(OMITTED, summary.omittedItemTypes());
        accessor.getServerData().putInt(PREVIEW_COUNT, summary.items().size());
        ViewGroup<ItemStack> group = new ViewGroup<>(summary.items().stream()
            .map(entry -> entry.stack().copyWithCount(1)).toList());
        group.getExtraData().putIntArray(COUNTS, summary.items().stream().mapToInt(MoldedPlasticContentSummary.ItemEntry::count).toArray());
        group.getExtraData().putInt(USED, summary.occupiedSlots());
        group.getExtraData().putInt(CAPACITY, summary.capacity());
        return List.of(group);
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
