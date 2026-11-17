package dev.anvilcraft.plasticraft.event;

import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.TradeCost;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.List;
import java.util.Optional;

/** 向 AnvilCraft 珠宝商追加 Plasticraft 自己的收购项目。 */
public final class PlasticVillagerTrades {
    private static final int GRANULE_COST = 8;
    private static final int EMERALD_PAYMENT = 2;
    private static final int MAX_USES = 16;
    private static final int VILLAGER_XP = 2;
    private static final float PRICE_MULTIPLIER = 0.05F;
    private static final DyeColor[] GRANULE_COLORS = DyeColor.values();

    private PlasticVillagerTrades() {
    }

    /** 每名珠宝商生成报价时随机指定一种塑料粒颜色，并在该报价生命周期内保持不变。 */
    public static VillagerTrade granulePurchase(RandomSource random) {
        DyeColor color = GRANULE_COLORS[random.nextInt(GRANULE_COLORS.length)];
        return new VillagerTrade(granuleCost(color), Optional.empty(), new ItemStackTemplate(Items.EMERALD, EMERALD_PAYMENT),
            MAX_USES, VILLAGER_XP, PRICE_MULTIPLIER, Optional.empty(), List.of());
    }

    /**
     * 将颜色写入成本展示栈和组件谓词；包括白色在内都必须精确匹配，不能退化为空谓词。
     */
    private static TradeCost granuleCost(DyeColor color) {
        DataComponentExactPredicate components = DataComponentExactPredicate.builder()
            .expect(DataComponents.CUSTOM_DATA, PlasticMeltColor.explicitColorData(color))
            .build();
        return new TradeCost(
            PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE.get().builtInRegistryHolder(),
            ConstantValue.exactly(GRANULE_COST),
            components
        );
    }
}
