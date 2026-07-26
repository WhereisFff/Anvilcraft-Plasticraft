package dev.anvilcraft.plasticraft.init.block;

import dev.anvilcraft.lib.v2.registrum.util.entry.BlockEntry;
import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.block.CatalyticPressLidBlock;
import dev.anvilcraft.plasticraft.block.HighViscosityResinBlock;
import dev.anvilcraft.plasticraft.block.HighViscosityResinCauldronBlock;
import dev.anvilcraft.plasticraft.block.HighViscosityResinFluidBlock;
import dev.anvilcraft.plasticraft.block.HighHeatFuelCauldronBlock;
import dev.anvilcraft.plasticraft.block.HardenedResinAnvilBlock;
import dev.anvilcraft.plasticraft.block.HardenedResinCauldronBlock;
import dev.anvilcraft.plasticraft.block.PlasticOilCauldronBlock;
import dev.anvilcraft.plasticraft.block.ResinAnvilBlock;
import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltCauldronBlock;
import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltFluidBlock;
import dev.anvilcraft.plasticraft.init.entity.ModEntities;
import dev.anvilcraft.plasticraft.init.item.ModItemTags;
import dev.anvilcraft.plasticraft.item.HardenedResinAnvilItem;
import dev.anvilcraft.plasticraft.item.HardenedResinCauldronItem;
import dev.anvilcraft.plasticraft.item.CatalyticPressLidItem;
import dev.anvilcraft.plasticraft.item.HighViscosityResinBlockItem;
import dev.anvilcraft.plasticraft.item.ResinAnvilItem;
import dev.dubhe.anvilcraft.block.item.SimpleMultiPartBlockItem;
import dev.dubhe.anvilcraft.block.multipart.SimpleMultiPartBlock;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.util.DataGenUtil;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.ModelProvider;

import java.util.function.Supplier;

import static dev.anvilcraft.plasticraft.AnvilcraftPlasticraft.REGISTRUM;

/** Plasticraft 可移动制品的方块和物品注册。 */
public final class ModBlocks {
    public static final BlockEntry<CondenserTowerBlock> CONDENSER_TOWER = REGISTRUM
        .block("condenser_tower", CondenserTowerBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(properties -> properties
            .noOcclusion()
            .strength(5.0F, 1200.0F)
            .sound(SoundType.METAL))
        .lang("Condenser Tower")
        .loot(SimpleMultiPartBlock::loot)
        .blockstate(DataGenUtil::noExtraModelOrState)
        .item(SimpleMultiPartBlockItem<Cube3x3PartHalf>::new)
        .properties(properties -> properties.stacksTo(16))
        .model(DataGenUtil::noExtraModelOrState)
        .build()
        .tag(BlockTags.MINEABLE_WITH_PICKAXE)
        .register();

    public static final BlockEntry<HighViscosityResinBlock> HIGH_VISCOSITY_RESIN_BLOCK = REGISTRUM
        .block("high_viscosity_resin_block", HighViscosityResinBlock::new)
        .initialProperties(dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK::get)
        .properties(properties -> properties
            .mapColor(MapColor.COLOR_ORANGE)
            .noOcclusion()
            .sound(SoundType.HONEY_BLOCK))
        .lang("High-Viscosity Resin Block")
        .blockstate((context, provider) -> provider.simpleBlock(
            context.get(),
            provider.models().getExistingFile(provider.modLoc("block/high_viscosity_resin"))
        ))
        .item(HighViscosityResinBlockItem::new)
        .model((context, provider) -> provider.withExistingParent(
            context.getName(),
            provider.modLoc("block/high_viscosity_resin")
        ))
        .build()
        .tag(ModBlockTags.RESIN_SHOCK_COMPATIBLE)
        .register();

    public static final BlockEntry<HighViscosityResinCauldronBlock> LIQUID_HIGH_VISCOSITY_RESIN_CAULDRON = REGISTRUM
        .block("liquid_high_viscosity_resin_cauldron", HighViscosityResinCauldronBlock::new)
        .initialProperties(() -> Blocks.CAULDRON)
        .lang("Liquid High-Viscosity Resin Cauldron")
        .blockstate((context, provider) -> {
        })
        .loot((tables, block) -> tables.dropOther(block, Items.CAULDRON))
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.CAULDRONS)
        .onRegister(block -> Item.BY_BLOCK.put(block, Items.CAULDRON))
        .register();

    public static final BlockEntry<HighViscosityResinFluidBlock> LIQUID_HIGH_VISCOSITY_RESIN = REGISTRUM
        .block(
            "liquid_high_viscosity_resin",
            properties -> new HighViscosityResinFluidBlock(ModFluids.LIQUID_HIGH_VISCOSITY_RESIN.get(), properties)
        )
        .properties(properties -> properties
            .mapColor(MapColor.COLOR_ORANGE)
            .replaceable()
            .noCollission()
            .pushReaction(PushReaction.DESTROY)
            .noLootTable()
            .liquid()
            .sound(SoundType.EMPTY)
            .strength(100.0F))
        .lang("Liquid High-Viscosity Resin")
        .blockstate((context, provider) -> provider.simpleBlock(
            context.get(),
            provider.models()
                .getBuilder(context.getName())
                .texture("particle", provider.modLoc("block/liquid_high_viscosity_resin"))
        ))
        .register();

    public static final BlockEntry<LiquidBlock> HIGH_HEAT_FUEL = fluidBlock(
        "high_heat_fuel",
        ModFluids.HIGH_HEAT_FUEL,
        "High-Heat Fuel"
    );
    public static final BlockEntry<HighHeatFuelCauldronBlock> HIGH_HEAT_FUEL_CAULDRON = REGISTRUM
        .block("high_heat_fuel_cauldron", HighHeatFuelCauldronBlock::new)
        .initialProperties(() -> Blocks.CAULDRON)
        .lang("High-Heat Fuel Cauldron")
        .blockstate((context, provider) -> {
        })
        .loot((tables, block) -> tables.dropOther(block, Items.CAULDRON))
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.CAULDRONS)
        .onRegister(block -> Item.BY_BLOCK.put(block, Items.CAULDRON))
        .register();
    public static final BlockEntry<LiquidBlock> PLASTIC_OIL = fluidBlock(
        "plastic_oil",
        ModFluids.PLASTIC_OIL,
        "Plastic Oil"
    );
    public static final BlockEntry<PlasticOilCauldronBlock> PLASTIC_OIL_CAULDRON = REGISTRUM
        .block("plastic_oil_cauldron", PlasticOilCauldronBlock::new)
        .initialProperties(() -> Blocks.CAULDRON)
        .lang("Plastic Oil Cauldron")
        .blockstate((context, provider) -> {
            ModelFile[] models = layeredCauldronModels(provider, context.getName(), "block/fluid_placeholder");
            provider.getVariantBuilder(context.get()).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(models[state.getValue(dev.dubhe.anvilcraft.block.Layered4LevelCauldronBlock.LEVEL) - 1])
                .build());
        })
        .loot((tables, block) -> tables.dropOther(block, Items.CAULDRON))
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.CAULDRONS)
        .onRegister(block -> Item.BY_BLOCK.put(block, Items.CAULDRON))
        .register();
    public static final BlockEntry<UniversalPlasticMeltFluidBlock> UNIVERSAL_PLASTIC_MELT = REGISTRUM
        .block(
            "universal_plastic_melt",
            properties -> new UniversalPlasticMeltFluidBlock(ModFluids.UNIVERSAL_PLASTIC_MELT, properties)
        )
        .properties(properties -> properties
            .mapColor(MapColor.SNOW)
            .replaceable()
            .noCollission()
            .pushReaction(PushReaction.DESTROY)
            .noLootTable()
            .liquid()
            .sound(SoundType.EMPTY)
            .strength(100.0F))
        .lang("Universal Plastic Melt")
        .blockstate((context, provider) -> {
            provider.models().existingFileHelper.trackGenerated(
                provider.modLoc("block/universal_plastic_melt"),
                ModelProvider.TEXTURE
            );
            provider.simpleBlock(
                context.get(),
                provider.models()
                    .getBuilder(context.getName())
                    .texture("particle", provider.modLoc("block/universal_plastic_melt"))
            );
        })
        .register();
    public static final BlockEntry<UniversalPlasticMeltCauldronBlock> UNIVERSAL_PLASTIC_MELT_CAULDRON = REGISTRUM
        .block("universal_plastic_melt_cauldron", UniversalPlasticMeltCauldronBlock::new)
        .initialProperties(() -> Blocks.CAULDRON)
        .lang("Universal Plastic Melt Cauldron")
        .blockstate((context, provider) -> {
            ModelFile[] models = layeredCauldronModels(
                provider,
                context.getName(),
                "block/universal_plastic_melt"
            );
            provider.getVariantBuilder(context.get()).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(models[state.getValue(dev.dubhe.anvilcraft.block.Layered4LevelCauldronBlock.LEVEL) - 1])
                .build());
        })
        .loot((tables, block) -> tables.dropOther(block, Items.CAULDRON))
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.CAULDRONS)
        .onRegister(block -> Item.BY_BLOCK.put(block, Items.CAULDRON))
        .register();
    public static final BlockEntry<LiquidBlock> CRUDE_OIL_ACID = fluidBlock(
        "crude_oil_acid",
        ModFluids.CRUDE_OIL_ACID,
        "Crude Oil Essence"
    );

    public static final BlockEntry<HardenedResinAnvilBlock> HARDEND_RESIN_ANVIL = REGISTRUM
        .block("hardend_resin_anvil", HardenedResinAnvilBlock::new)
        .initialProperties(() -> Blocks.ANVIL)
        .properties(properties -> properties
            .noOcclusion()
            .strength(5.0F, 1200.0F)
            .pushReaction(PushReaction.NORMAL))
        .lang("Hardened Resin Anvil")
        .tag(
            BlockTags.MINEABLE_WITH_PICKAXE,
            BlockTags.ANVIL,
            dev.dubhe.anvilcraft.init.block.ModBlockTags.NON_MAGNETIC
        )
        .blockstate((context, provider) -> {
        })
        .item((block, properties) -> new HardenedResinAnvilItem(
            block,
            properties,
            ModEntities.HARDEND_RESIN_ANVIL,
            block::defaultBlockState
        ))
        .tag(ModItemTags.PLASTIC_ANVILS, ModItemTags.BUOYANT_PLASTIC_ITEMS, ItemTags.ANVIL)
        .model((context, provider) -> {
        })
        .build()
        .register();

    public static final BlockEntry<HardenedResinCauldronBlock> HARDEND_RESIN_CAULDRON = REGISTRUM
        .block("hardend_resin_cauldron", HardenedResinCauldronBlock::new)
        .initialProperties(() -> Blocks.CAULDRON)
        .properties(properties -> properties
            .noOcclusion()
            .strength(2.0F, 20.0F)
            .pushReaction(PushReaction.NORMAL))
        .lang("Hardened Resin Cauldron")
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.CAULDRONS)
        .blockstate((context, provider) -> {
        })
        .item((block, properties) -> new HardenedResinCauldronItem(
            block,
            properties,
            ModEntities.HARDEND_RESIN_CAULDRON,
            block::defaultBlockState
        ))
        .tag(ModItemTags.PLASTIC_CAULDRONS, ModItemTags.BUOYANT_PLASTIC_ITEMS)
        .model((context, provider) -> {
        })
        .build()
        .register();

    public static final BlockEntry<CatalyticPressLidBlock> CATALYTIC_PRESS_LID = REGISTRUM
        .block("catalytic_press_lid", CatalyticPressLidBlock::new)
        .initialProperties(() -> Blocks.ANVIL)
        .properties(properties -> properties
            .noOcclusion()
            .strength(2.0F, 20.0F)
            .pushReaction(PushReaction.NORMAL))
        .lang("Catalytic Press Lid")
        .tag(BlockTags.MINEABLE_WITH_PICKAXE)
        .blockstate((context, provider) -> provider.simpleBlock(
            context.get(),
            provider.models().getExistingFile(provider.modLoc("block/catalytic_press_lid"))
        ))
        .item((block, properties) -> new CatalyticPressLidItem(
            block,
            properties,
            ModEntities.CATALYTIC_PRESS_LID,
            block::defaultBlockState
        ))
        .tag(ModItemTags.BUOYANT_PLASTIC_ITEMS)
        .model((context, provider) -> provider.withExistingParent(
            context.getName(),
            provider.modLoc("block/catalytic_press_lid")
        ))
        .build()
        .register();

    public static final BlockEntry<ResinAnvilBlock> RESIN_ANVIL = REGISTRUM
        .block("resin_anvil", ResinAnvilBlock::new)
        .initialProperties(() -> Blocks.ANVIL)
        .properties(properties -> properties
            .noOcclusion()
            .strength(4.0F, 80.0F)
            .pushReaction(PushReaction.NORMAL))
        .lang("Resin Anvil")
        .tag(
            BlockTags.MINEABLE_WITH_PICKAXE,
            BlockTags.ANVIL,
            dev.dubhe.anvilcraft.init.block.ModBlockTags.NON_MAGNETIC,
            ModBlockTags.RESIN_SHOCK_COMPATIBLE
        )
        .blockstate((context, provider) -> {
        })
        .item((block, properties) -> new ResinAnvilItem(
            block,
            properties.component(
                net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(0)
            ),
            ModEntities.RESIN_ANVIL,
            block::defaultBlockState
        ))
        .tag(ModItemTags.PLASTIC_ANVILS, ModItemTags.BUOYANT_PLASTIC_ITEMS)
        .model((context, provider) -> {
        })
        .build()
        .register();

    private ModBlocks() {
    }

    private static BlockEntry<LiquidBlock> fluidBlock(
        String id,
        Supplier<? extends FlowingFluid> source,
        String name
    ) {
        return REGISTRUM.block(id, properties -> new LiquidBlock(source.get(), properties))
            .initialProperties(() -> Blocks.WATER)
            .properties(properties -> properties
                .mapColor(MapColor.COLOR_PURPLE)
                .replaceable()
                .noCollission()
                .pushReaction(PushReaction.DESTROY)
                .noLootTable()
                .liquid()
                .sound(SoundType.EMPTY))
            .lang(name)
            .blockstate((context, provider) -> provider.simpleBlock(
                context.get(),
                provider.models()
                    .getBuilder(context.getName())
                    .texture("particle", provider.modLoc("block/fluid_placeholder"))
            ))
            .register();
    }

    private static ModelFile[] layeredCauldronModels(
        dev.anvilcraft.lib.v2.registrum.providers.RegistrumBlockstateProvider provider,
        String name,
        String contentTexture
    ) {
        if (contentTexture.equals("block/universal_plastic_melt")) {
            provider.models().existingFileHelper.trackGenerated(
                provider.modLoc(contentTexture),
                ModelProvider.TEXTURE
            );
        }
        ModelFile[] models = new ModelFile[4];
        for (int level = 1; level <= 4; level++) {
            String parent = level == 4
                ? "minecraft:block/template_cauldron_full"
                : "anvilcraft:block/template_cauldron_level" + level + "of4";
            models[level - 1] = provider.models()
                .getBuilder(name + "_level" + level)
                .parent(new ModelFile.UncheckedModelFile(parent))
                .texture("bottom", "minecraft:block/cauldron_bottom")
                .texture("content", "anvilcraftplasticraft:" + contentTexture)
                .texture("inside", "minecraft:block/cauldron_inner")
                .texture("particle", "minecraft:block/cauldron_side")
                .texture("side", "minecraft:block/cauldron_side")
                .texture("top", "minecraft:block/cauldron_top")
                .renderType("minecraft:cutout");
        }
        return models;
    }

    public static void register() {
        // 类加载时，静态条目会挂接到 Registrum 事件总线。
    }

    public static void registerDispenserBehavior(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            DispenserBlock.registerBehavior(RESIN_ANVIL.asItem(), ResinAnvilItem::dispense);
            DispenserBlock.registerBehavior(HIGH_VISCOSITY_RESIN_BLOCK.asItem(), HighViscosityResinBlockItem::dispense);
            HighViscosityResinCauldronBlock.registerInteractions();
            HighHeatFuelCauldronBlock.registerInteractions();
            PlasticOilCauldronBlock.registerInteractions();
            UniversalPlasticMeltCauldronBlock.registerInteractions();
        });
    }
}
