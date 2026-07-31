package dev.anvilcraft.plasticraft.init.item;

import dev.anvilcraft.lib.v2.registrum.util.entry.ItemEntry;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import dev.anvilcraft.plasticraft.item.HighViscosityResinBucketItem;
import dev.anvilcraft.plasticraft.item.ResinAnvilHammerItem;
import dev.anvilcraft.plasticraft.item.UniversalPlasticGranuleItem;
import dev.anvilcraft.plasticraft.item.UniversalPlasticMeltBucketItem;
import dev.dubhe.anvilcraft.util.registrater.ModelProviderUtil;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.model.generators.ModelProvider;
import net.neoforged.neoforge.common.Tags;

import java.util.function.Supplier;

import static dev.anvilcraft.plasticraft.AnvilcraftPlasticraft.REGISTRUM;
import static dev.dubhe.anvilcraft.init.item.ModItemTags.ANVIL_HAMMER;

/** 不依附于方块条目的 Plasticraft 物品注册。 */
public final class ModItems {
    public static final ItemEntry<ResinAnvilHammerItem> RESIN_ANVIL_HAMMER = REGISTRUM
        .item("resin_anvil_hammer", ResinAnvilHammerItem::new)
        .lang("Resin Anvil Hammer")
        .properties(properties -> properties.durability(35))
        .tag(
            ItemTags.MACE_ENCHANTABLE,
            ItemTags.DURABILITY_ENCHANTABLE,
            ANVIL_HAMMER
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
    public static final ItemEntry<UniversalPlasticMeltBucketItem> UNIVERSAL_PLASTIC_MELT_BUCKET = REGISTRUM
        .item(
            "universal_plastic_melt_bucket",
            properties -> new UniversalPlasticMeltBucketItem(ModFluids.UNIVERSAL_PLASTIC_MELT, properties)
        )
        .lang("Universal Plastic Melt Bucket")
        .initialProperties(() -> new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET))
        .tag(Tags.Items.BUCKETS)
        .model(ModelProviderUtil::bucket)
        .register();
    public static final ItemEntry<UniversalPlasticGranuleItem> UNIVERSAL_PLASTIC_GRANULE = REGISTRUM
        .item("universal_plastic_granule", UniversalPlasticGranuleItem::new)
        .lang("Universal Plastic Granule")
        .model((context, provider) -> {
            provider.existingFileHelper.trackGenerated(
                provider.modLoc("item/universal_plastic_granule"),
                ModelProvider.TEXTURE
            );
            provider.generated(context, provider.modLoc("item/universal_plastic_granule"));
        })
        .register();

    private ModItems() {
    }

    private static ItemEntry<BucketItem> fluidBucket(
        String id,
        Supplier<? extends Fluid> fluid,
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
