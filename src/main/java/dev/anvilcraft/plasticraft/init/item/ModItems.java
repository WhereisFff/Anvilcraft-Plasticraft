package dev.anvilcraft.plasticraft.init.item;

import dev.anvilcraft.lib.v2.registrum.util.entry.ItemEntry;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import dev.anvilcraft.plasticraft.item.HighViscosityResinBucketItem;
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

    public static final ItemEntry<HighViscosityResinBucketItem> LIQUID_HIGH_VISCOSITY_RESIN_BUCKET = REGISTRUM
        .item(
            "liquid_high_viscosity_resin_bucket",
            properties -> new HighViscosityResinBucketItem(ModFluids.LIQUID_HIGH_VISCOSITY_RESIN, properties)
        )
        .lang("Liquid High-Viscosity Resin Bucket")
        .initialProperties(() -> new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET))
        .tag(Tags.Items.BUCKETS)
        .model(ModelProviderUtil::bucket)
        .register();

    public static final ItemEntry<BucketItem> HIGH_HEAT_FUEL_BUCKET = fluidBucket(
        "high_heat_fuel_bucket",
        ModFluids.HIGH_HEAT_FUEL,
        "High-Heat Fuel Bucket"
    );
    public static final ItemEntry<BucketItem> PLASTIC_OIL_BUCKET = fluidBucket(
        "plastic_oil_bucket",
        ModFluids.PLASTIC_OIL,
        "Plastic Oil Bucket"
    );
    public static final ItemEntry<BucketItem> CRUDE_OIL_ACID_BUCKET = fluidBucket(
        "crude_oil_acid_bucket",
        ModFluids.CRUDE_OIL_ACID,
        "Crude Oil Essence Bucket"
    );

    private ModItems() {
    }

    private static ItemEntry<BucketItem> fluidBucket(
        String id,
        java.util.function.Supplier<? extends net.minecraft.world.level.material.Fluid> fluid,
        String name
    ) {
        return REGISTRUM.item(id, properties -> new BucketItem(fluid.get(), properties))
            .lang(name)
            .initialProperties(() -> new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET))
            .tag(Tags.Items.BUCKETS)
            .model(ModelProviderUtil::bucket)
            .register();
    }

    public static void register() {
        // 类加载时静态条目会挂接到 Registrum 事件总线。
    }
}
