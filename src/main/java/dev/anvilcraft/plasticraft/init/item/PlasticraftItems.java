package dev.anvilcraft.plasticraft.init.item;

import dev.anvilcraft.lib.v2.registrum.providers.DataGenContext;
import dev.anvilcraft.lib.v2.registrum.providers.generators.RegistrumItemModelGenerator;
import dev.anvilcraft.lib.v2.registrum.util.entry.ItemEntry;
import dev.anvilcraft.lib.v2.util.nullness.NonNullBiConsumer;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.renderer.PlasticPaletteItemTint;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.item.ClearPlasticGranuleItem;
import dev.anvilcraft.plasticraft.item.ClearPlasticMeltBucketItem;
import dev.anvilcraft.plasticraft.item.HighViscosityResinBucketItem;
import dev.anvilcraft.plasticraft.item.ResinAnvilHammerItem;
import dev.anvilcraft.plasticraft.item.UniversalPlasticGranuleItem;
import dev.anvilcraft.plasticraft.item.UniversalPlasticMeltBucketItem;
import dev.dubhe.anvilcraft.init.item.ModItemTags;
import dev.dubhe.anvilcraft.util.registrater.ModelProviderUtil;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.Tags;

import java.util.function.Supplier;

/** 不依附于方块条目的 Plasticraft 物品注册。 */
public final class PlasticraftItems {
    public static final ItemEntry<ResinAnvilHammerItem> RESIN_ANVIL_HAMMER = AnvilcraftPlasticraft.REGISTRUM
        .item("resin_anvil_hammer", ResinAnvilHammerItem::new)
        .lang("Resin Anvil Hammer")
        .properties(properties -> properties.durability(35))
        .tag(
            ItemTags.MACE_ENCHANTABLE,
            ItemTags.DURABILITY_ENCHANTABLE,
            ModItemTags.ANVIL_HAMMER
        )
        .model(() -> (ctx, gen) -> gen.createWithExistingModel(ctx.get(), gen.modLoc("item/" + ctx.getName())))
        .register();

    public static final ItemEntry<HighViscosityResinBucketItem> LIQUID_HIGH_VISCOSITY_RESIN_BUCKET = AnvilcraftPlasticraft.REGISTRUM
        .item(
            "liquid_high_viscosity_resin_bucket",
            properties -> new HighViscosityResinBucketItem(PlasticraftFluids.LIQUID_HIGH_VISCOSITY_RESIN, properties)
        )
        .lang("Liquid High-Viscosity Resin Bucket")
        .initialProperties(() -> new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET))
        .tag(Tags.Items.BUCKETS)
        .model(PlasticraftItems::bucketModel)
        .register();

    public static final ItemEntry<BucketItem> HIGH_HEAT_FUEL_BUCKET = fluidBucket(
        "high_heat_fuel_bucket",
        PlasticraftFluids.HIGH_HEAT_FUEL,
        "High-Heat Fuel Bucket"
    );
    public static final ItemEntry<BucketItem> CRUDE_OIL_ACID_BUCKET = fluidBucket(
        "crude_oil_acid_bucket",
        PlasticraftFluids.CRUDE_OIL_ACID,
        "Crude Oil Essence Bucket"
    );
    public static final ItemEntry<BucketItem> PLASTIC_OIL_BUCKET = fluidBucket(
        "plastic_oil_bucket",
        PlasticraftFluids.PLASTIC_OIL,
        "Plastic Oil Bucket"
    );
    public static final ItemEntry<UniversalPlasticMeltBucketItem> UNIVERSAL_PLASTIC_MELT_BUCKET = AnvilcraftPlasticraft.REGISTRUM
        .item(
            "universal_plastic_melt_bucket",
            properties -> new UniversalPlasticMeltBucketItem(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT, properties)
        )
        .lang("Universal Plastic Melt Bucket")
        .initialProperties(() -> new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET))
        .tag(Tags.Items.BUCKETS)
        .model(PlasticraftItems::bucketModel)
        .register();
    public static final ItemEntry<UniversalPlasticGranuleItem> UNIVERSAL_PLASTIC_GRANULE = AnvilcraftPlasticraft.REGISTRUM
        .item("universal_plastic_granule", UniversalPlasticGranuleItem::new)
        .lang("Universal Plastic Granule")
        .model(() -> granuleModel(false))
            .register();
    public static final ItemEntry<UniversalPlasticMeltBucketItem> ENGINEERING_PLASTIC_MELT_BUCKET =
        AnvilcraftPlasticraft.REGISTRUM
            .item(
                "engineering_plastic_melt_bucket",
                properties -> new UniversalPlasticMeltBucketItem(
                    PlasticraftFluids.ENGINEERING_PLASTIC_MELT,
                    properties
                )
            )
            .lang("Engineering Plastic Melt Bucket")
            .initialProperties(() -> new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET))
            .tag(Tags.Items.BUCKETS)
            .model(PlasticraftItems::bucketModel)
            .register();
    public static final ItemEntry<UniversalPlasticGranuleItem> ENGINEERING_PLASTIC_GRANULE =
        AnvilcraftPlasticraft.REGISTRUM
            .item("engineering_plastic_granule", UniversalPlasticGranuleItem::new)
            .lang("Engineering Plastic Granule")
            .model(() -> granuleModel(false))
            .register();
    public static final ItemEntry<UniversalPlasticMeltBucketItem> HEAT_RESISTANT_PLASTIC_MELT_BUCKET =
        AnvilcraftPlasticraft.REGISTRUM
            .item(
                "heat_resistant_plastic_melt_bucket",
                properties -> new UniversalPlasticMeltBucketItem(
                    PlasticraftFluids.HEAT_RESISTANT_PLASTIC_MELT,
                    properties
                )
            )
            .lang("Heat-Resistant Plastic Melt Bucket")
            .initialProperties(() -> new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET))
            .properties(Item.Properties::fireResistant)
            .tag(Tags.Items.BUCKETS)
            .model(PlasticraftItems::bucketModel)
            .register();
    public static final ItemEntry<UniversalPlasticGranuleItem> HEAT_RESISTANT_PLASTIC_GRANULE =
        AnvilcraftPlasticraft.REGISTRUM
            .item("heat_resistant_plastic_granule", UniversalPlasticGranuleItem::new)
            .lang("Heat-Resistant Plastic Granule")
            .properties(Item.Properties::fireResistant)
            .model(() -> granuleModel(false))
            .register();
    public static final ItemEntry<ClearPlasticMeltBucketItem> CLEAR_PLASTIC_MELT_BUCKET =
        AnvilcraftPlasticraft.REGISTRUM
            .item(
                "clear_plastic_melt_bucket",
                properties -> new ClearPlasticMeltBucketItem(PlasticraftFluids.CLEAR_PLASTIC_MELT, properties)
            )
            .lang("Clear Plastic Melt Bucket")
            .initialProperties(() -> new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET))
            .tag(Tags.Items.BUCKETS)
            .model(PlasticraftItems::bucketModel)
            .register();
    public static final ItemEntry<ClearPlasticGranuleItem> CLEAR_PLASTIC_GRANULE =
        AnvilcraftPlasticraft.REGISTRUM
            .item("clear_plastic_granule", ClearPlasticGranuleItem::new)
            .lang("Clear Plastic Granule")
            .model(() -> granuleModel(true))
            .register();

    public static final ItemEntry<UniversalPlasticMeltBucketItem> ELASTIC_PLASTIC_MELT_BUCKET =
        AnvilcraftPlasticraft.REGISTRUM
            .item(
                "elastic_plastic_melt_bucket",
                properties -> new UniversalPlasticMeltBucketItem(PlasticraftFluids.ELASTIC_PLASTIC_MELT, properties)
            )
            .lang("Elastic Plastic Melt Bucket")
            .initialProperties(() -> new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET))
            .tag(Tags.Items.BUCKETS)
            .model(PlasticraftItems::bucketModel)
            .register();
    public static final ItemEntry<UniversalPlasticGranuleItem> ELASTIC_PLASTIC_GRANULE =
        AnvilcraftPlasticraft.REGISTRUM
            .item("elastic_plastic_granule", UniversalPlasticGranuleItem::new)
            .lang("Elastic Plastic Granule")
            .model(() -> granuleModel(true))
            .register();

    private PlasticraftItems() {
    }

    private static <T extends Item> NonNullBiConsumer<DataGenContext<Item, T>, RegistrumItemModelGenerator> granuleModel(
        boolean translucent
    ) {
        return (ctx, generator) -> {
            var id = generator.modLoc("item/" + ctx.getName());
            var texture = new TextureMapping().put(TextureSlot.LAYER0, new Material(id, translucent));
            generator.generateTintedModel(ctx.get(), ModelTemplates.FLAT_ITEM.create(id, texture, generator.modelOutput),
                PlasticPaletteItemTint.INSTANCE);
        };
    }

    private static <T extends BucketItem> NonNullBiConsumer<DataGenContext<Item, T>, RegistrumItemModelGenerator> bucketModel() {
        return (ctx, gen) -> ModelProviderUtil.bucket().accept(
            new DataGenContext<>(ctx::get, ctx.getName(), ctx.getId()), gen
        );
    }

    private static ItemEntry<BucketItem> fluidBucket(
        String id,
        Supplier<? extends Fluid> fluid,
        String name
    ) {
        return AnvilcraftPlasticraft.REGISTRUM.item(id, properties -> new BucketItem(fluid.get(), properties))
            .lang(name)
            .initialProperties(() -> new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET))
            .tag(Tags.Items.BUCKETS)
            .model(PlasticraftItems::bucketModel)
            .register();
    }

    public static void register() {
        // 类加载时静态条目会挂接到 Registrum 事件总线。
    }
}
