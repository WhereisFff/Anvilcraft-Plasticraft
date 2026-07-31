package dev.anvilcraft.plasticraft.event;

import dev.anvilcraft.plasticraft.init.item.ModItems;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.dubhe.anvilcraft.init.entity.ModVillagers;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

/** 向 AnvilCraft 珠宝商追加 Plasticraft 自己的收购项目。 */
public final class PlasticVillagerTrades {
    private static final int NOVICE_LEVEL = 1;
    private static final int GRANULE_COST = 8;
    private static final int EMERALD_PAYMENT = 2;
    private static final int MAX_USES = 16;
    private static final int VILLAGER_XP = 2;
    private static final float PRICE_MULTIPLIER = 0.05F;
    private static final DyeColor[] GRANULE_COLORS = DyeColor.values();

    private PlasticVillagerTrades() {
    }

    public static void addTrades(VillagerTradesEvent event) {
        if (event.getType() != ModVillagers.JEWELER.get()) return;
        event.getTrades().get(NOVICE_LEVEL).add(granulePurchase());
    }

    /** 每名珠宝商生成报价时随机指定一种塑料粒颜色，并在该报价生命周期内保持不变。 */
    public static VillagerTrades.ItemListing granulePurchase() {
        return (entity, random) -> {
            DyeColor requestedColor = GRANULE_COLORS[random.nextInt(GRANULE_COLORS.length)];
            return new MerchantOffer(
                granuleCost(requestedColor),
                new ItemStack(Items.EMERALD, EMERALD_PAYMENT),
                MAX_USES,
                VILLAGER_XP,
                PRICE_MULTIPLIER
            );
        };
    }

    /**
     * 将颜色写入成本展示栈和组件谓词；包括白色在内都必须精确匹配，不能退化为空谓词。
     */
    private static ItemCost granuleCost(DyeColor color) {
        DataComponentPredicate components = DataComponentPredicate.builder()
            .expect(DataComponents.CUSTOM_DATA, PlasticMeltColor.explicitColorData(color))
            .build();
        return new ItemCost(
            ModItems.UNIVERSAL_PLASTIC_GRANULE.get().builtInRegistryHolder(),
            GRANULE_COST,
            components
        );
    }
}
