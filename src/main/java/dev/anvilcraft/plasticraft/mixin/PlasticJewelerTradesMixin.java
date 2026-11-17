package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.anvilcraft.plasticraft.event.PlasticVillagerTrades;
import dev.dubhe.anvilcraft.init.entity.ModTradeSets;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;

@Mixin(AbstractVillager.class)
abstract class PlasticJewelerTradesMixin {
    @ModifyExpressionValue(
        method = "addOffersFromTradeSet",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/trading/TradeSet;getTrades()Lnet/minecraft/core/HolderSet;")
    )
    private HolderSet<VillagerTrade> addGranulePurchase(
        HolderSet<VillagerTrade> trades, @Local(argsOnly = true) ResourceKey<TradeSet> key, @Local LootContext context
    ) {
        if (!key.equals(ModTradeSets.JEWELER_LEVEL_1)) {
            return trades;
        }
        var offers = new ArrayList<>(trades.stream().toList());
        offers.add(Holder.direct(PlasticVillagerTrades.granulePurchase(context.getRandom())));
        return HolderSet.direct(offers);
    }
}
