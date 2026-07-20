package dev.anvilcraft.plasticraft.init.item;

import dev.anvilcraft.lib.v2.registrum.util.entry.ItemEntry;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import dev.anvilcraft.plasticraft.item.ResinAnvilHammerItem;
import dev.dubhe.anvilcraft.util.registrater.ModelProviderUtil;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;

import static dev.anvilcraft.plasticraft.AnvilcraftPlasticraft.REGISTRUM;

/** 不依附于方块条目的 Plasticraft 物品注册。 */
public final class ModItems {
    public static final ItemEntry<ResinAnvilHammerItem> RESIN_ANVIL_HAMMER = REGISTRUM
        .item("resin_anvil_hammer", ResinAnvilHammerItem::new)
        .lang("Resin Anvil Hammer")
        .properties(properties -> properties.durability(35))
        .tag(
            ItemTags.MACE_ENCHANTABLE,
            ItemTags.DURABILITY_ENCHANTABLE,
            dev.dubhe.anvilcraft.init.item.ModItemTags.ANVIL_HAMMER
        )
        .model((context, provider) -> {
        })
        .register();

    public static final ItemEntry<BucketItem> LIQUID_HIGH_VISCOSITY_RESIN_BUCKET = REGISTRUM
        .item(
            "liquid_high_viscosity_resin_bucket",
            properties -> new BucketItem(ModFluids.LIQUID_HIGH_VISCOSITY_RESIN.get(), properties)
        )
        .lang("Liquid High-Viscosity Resin Bucket")
        .initialProperties(() -> new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET))
        .tag(Tags.Items.BUCKETS)
        .model(ModelProviderUtil::bucket)
        .register();

    private ModItems() {
    }

    public static void register() {
        // 类加载时静态条目会挂接到 Registrum 事件总线。
    }
}
