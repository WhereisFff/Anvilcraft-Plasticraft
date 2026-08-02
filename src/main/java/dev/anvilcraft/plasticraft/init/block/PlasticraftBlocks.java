package dev.anvilcraft.plasticraft.init.block;

import dev.anvilcraft.lib.v2.registrum.providers.RegistrumBlockstateProvider;
import dev.anvilcraft.lib.v2.registrum.util.entry.BlockEntry;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.CatalyticPressLidBlock;
import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.block.HardenedResinAnvilBlock;
import dev.anvilcraft.plasticraft.block.HardenedResinCauldronBlock;
import dev.anvilcraft.plasticraft.block.HighHeatFuelCauldronBlock;
import dev.anvilcraft.plasticraft.block.HighViscosityResinBlock;
import dev.anvilcraft.plasticraft.block.HighViscosityResinCauldronBlock;
import dev.anvilcraft.plasticraft.block.HighViscosityResinFluidBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingRegionBlock;
import dev.anvilcraft.plasticraft.block.PlasticOilCauldronBlock;
import dev.anvilcraft.plasticraft.block.ResinAnvilBlock;
import dev.anvilcraft.plasticraft.block.UniversalPlasticBlock;
import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltCauldronBlock;
import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltFluidBlock;
import dev.anvilcraft.plasticraft.block.UniversalPlasticShape;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItemTags;
import dev.anvilcraft.plasticraft.item.CatalyticPressLidItem;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.HardenedResinAnvilItem;
import dev.anvilcraft.plasticraft.item.HardenedResinCauldronItem;
import dev.anvilcraft.plasticraft.item.HighViscosityResinBlockItem;
import dev.anvilcraft.plasticraft.item.PlasticMoldingChamberItem;
import dev.anvilcraft.plasticraft.item.ResinAnvilItem;
import dev.anvilcraft.plasticraft.item.UniversalPlasticBlockItem;
import dev.dubhe.anvilcraft.block.Layered4LevelCauldronBlock;
import dev.dubhe.anvilcraft.block.item.SimpleMultiPartBlockItem;
import dev.dubhe.anvilcraft.block.multipart.SimpleMultiPartBlock;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.init.block.ModBlockTags;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.util.DataGenUtil;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelBuilder;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.ModelProvider;

import java.util.function.Supplier;

/** Plasticraft 可移动制品的方块和物品注册。 */
public final class PlasticraftBlocks {
    public static final BlockEntry<PlasticMoldingChamberBlock> PLASTIC_MOLDING_CHAMBER = AnvilcraftPlasticraft.REGISTRUM
        .block("plastic_molding_chamber", PlasticMoldingChamberBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(properties -> properties
            .noOcclusion()
            .strength(5.0F, 1200.0F)
            .sound(SoundType.METAL)
            .pushReaction(PushReaction.BLOCK))
        .lang("Plastic Molding Chamber")
        .tag(BlockTags.MINEABLE_WITH_PICKAXE)
        .loot((tables, block) -> tables.dropSelf(block))
        .blockstate((context, provider) -> provider.horizontalBlock(
            context.get(),
            provider.models().cubeAll(
                context.getName(),
                provider.modLoc("block/plastic_molding_chamber_placeholder")
            )
        ))
        .item(PlasticMoldingChamberItem::new)
        .model((context, provider) -> provider.withExistingParent(
            context.getName(),
            provider.modLoc("block/" + context.getName())
        ))
        .build()
        .register();

    public static final BlockEntry<PlasticMoldingRegionBlock> PLASTIC_MOLDING_REGION = AnvilcraftPlasticraft.REGISTRUM
        .block("plastic_molding_region", PlasticMoldingRegionBlock::new)
        .initialProperties(() -> Blocks.BARRIER)
        .properties(properties -> properties
            .mapColor(MapColor.NONE)
            .noOcclusion()
            .noCollission()
            .strength(-1.0F, 3600000.0F)
            .pushReaction(PushReaction.BLOCK)
            .noLootTable())
        .lang("Plastic Molding Region")
        .blockstate((context, provider) -> provider.simpleBlock(
            context.get(),
            provider.models().getBuilder(context.getName())
        ))
        .register();

    public static final BlockEntry<CondenserTowerBlock> CONDENSER_TOWER = AnvilcraftPlasticraft.REGISTRUM
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

    public static final BlockEntry<HighViscosityResinBlock> HIGH_VISCOSITY_RESIN_BLOCK = AnvilcraftPlasticraft.REGISTRUM
        .block("high_viscosity_resin_block", HighViscosityResinBlock::new)
        .initialProperties(ModBlocks.RESIN_BLOCK::get)
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
        .tag(PlasticraftBlockTags.RESIN_SHOCK_COMPATIBLE)
        .register();

    public static final BlockEntry<HighViscosityResinCauldronBlock> LIQUID_HIGH_VISCOSITY_RESIN_CAULDRON = AnvilcraftPlasticraft.REGISTRUM
        .block("liquid_high_viscosity_resin_cauldron", HighViscosityResinCauldronBlock::new)
        .initialProperties(() -> Blocks.CAULDRON)
        .lang("Liquid High-Viscosity Resin Cauldron")
        .blockstate((context, provider) -> {
        })
        .loot((tables, block) -> tables.dropOther(block, Items.CAULDRON))
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.CAULDRONS)
        .onRegister(block -> Item.BY_BLOCK.put(block, Items.CAULDRON))
        .register();

    public static final BlockEntry<HighViscosityResinFluidBlock> LIQUID_HIGH_VISCOSITY_RESIN = AnvilcraftPlasticraft.REGISTRUM
        .block(
            "liquid_high_viscosity_resin",
            properties -> new HighViscosityResinFluidBlock(PlasticraftFluids.LIQUID_HIGH_VISCOSITY_RESIN.get(), properties)
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
        PlasticraftFluids.HIGH_HEAT_FUEL,
        "high_heat_fuel",
        MapColor.COLOR_YELLOW,
        "High-Heat Fuel"
    );
    public static final BlockEntry<HighHeatFuelCauldronBlock> HIGH_HEAT_FUEL_CAULDRON = AnvilcraftPlasticraft.REGISTRUM
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
        PlasticraftFluids.PLASTIC_OIL,
        "plastic_oil",
        MapColor.COLOR_LIGHT_BLUE,
        "Plastic Oil"
    );
    public static final BlockEntry<PlasticOilCauldronBlock> PLASTIC_OIL_CAULDRON = AnvilcraftPlasticraft.REGISTRUM
        .block("plastic_oil_cauldron", PlasticOilCauldronBlock::new)
        .initialProperties(() -> Blocks.CAULDRON)
        .lang("Plastic Oil Cauldron")
        .blockstate((context, provider) -> {
            ModelFile[] models = layeredCauldronModels(provider, context.getName(), "block/plastic_oil");
            provider.getVariantBuilder(context.get()).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(models[state.getValue(Layered4LevelCauldronBlock.LEVEL) - 1])
                .build());
        })
        .loot((tables, block) -> tables.dropOther(block, Items.CAULDRON))
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.CAULDRONS)
        .onRegister(block -> Item.BY_BLOCK.put(block, Items.CAULDRON))
        .register();
    public static final BlockEntry<UniversalPlasticMeltFluidBlock> UNIVERSAL_PLASTIC_MELT = AnvilcraftPlasticraft.REGISTRUM
        .block(
            "universal_plastic_melt",
            properties -> new UniversalPlasticMeltFluidBlock(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT, properties)
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
    public static final BlockEntry<UniversalPlasticMeltCauldronBlock> UNIVERSAL_PLASTIC_MELT_CAULDRON = AnvilcraftPlasticraft.REGISTRUM
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
                .modelFile(models[state.getValue(Layered4LevelCauldronBlock.LEVEL) - 1])
                .build());
        })
        .loot((tables, block) -> tables.dropOther(block, Items.CAULDRON))
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.CAULDRONS)
        .onRegister(block -> Item.BY_BLOCK.put(block, Items.CAULDRON))
        .register();
    public static final BlockEntry<UniversalPlasticBlock> UNIVERSAL_PLASTIC = AnvilcraftPlasticraft.REGISTRUM
        .block("universal_plastic", UniversalPlasticBlock::new)
        .initialProperties(() -> Blocks.WHITE_CONCRETE)
        .properties(properties -> properties
            .mapColor(MapColor.SNOW)
            .noOcclusion()
            .strength(1.5F, 3.0F)
            .sound(SoundType.BONE_BLOCK)
            .pushReaction(PushReaction.NORMAL))
        .lang("Universal Plastic Block")
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, PlasticraftBlockTags.PLASTIC_PRODUCTS)
        .loot((tables, block) -> tables.dropSelf(block))
        .blockstate((context, provider) -> {
            // 数据生成阶段为十六种熔体颜色分别烘焙模型；所有模型共用公共生成器给出的确定性 UV。
            ModelFile[] models = new ModelFile[DyeColor.values().length];
            for (DyeColor color : DyeColor.values()) {
                provider.models().existingFileHelper.trackGenerated(
                    UniversalPlasticShape.sprite(color),
                    ModelProvider.TEXTURE
                );
                var model = provider.models()
                    .getBuilder(context.getName() + "_" + color.getName())
                    .texture("particle", UniversalPlasticShape.sprite(color))
                    .texture("plastic", UniversalPlasticShape.sprite(color));
                var element = model.element().from(0.0F, 0.0F, 0.0F).to(16.0F, 14.0F, 16.0F);
                for (Direction direction : Direction.values()) {
                    var region = UniversalPlasticShape.uv(direction);
                    element.face(direction)
                        .uvs(
                            region.modelU0(UniversalPlasticShape.TEXTURE_LAYOUT.atlasWidth()),
                            region.modelV0(UniversalPlasticShape.TEXTURE_LAYOUT.atlasHeight()),
                            region.modelU1(UniversalPlasticShape.TEXTURE_LAYOUT.atlasWidth()),
                            region.modelV1(UniversalPlasticShape.TEXTURE_LAYOUT.atlasHeight())
                        )
                        .texture("#plastic")
                        .end();
                }
                element.end();
                models[color.getId()] = model;
            }
            provider.getVariantBuilder(context.get()).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(models[state.getValue(DyeableMaterial.COLOR).getId()])
                .build());
        })
        .item((block, properties) -> new UniversalPlasticBlockItem(
            block,
            properties,
            PlasticraftEntities.UNIVERSAL_PLASTIC,
            block::defaultBlockState
        ))
        .tag(PlasticraftItemTags.PLASTIC_PRODUCTS, PlasticraftItemTags.BUOYANT_PLASTIC_ITEMS)
        .model((context, provider) -> {
            // 物品模型继承同色方块几何，但独立提供缩小后的掉落、手持和三面 GUI 视角。
            var base = provider.getBuilder(context.getName()).parent(new ModelFile.UncheckedModelFile(
                provider.modLoc("block/" + context.getName() + "_white")
            ));
            addUniversalPlasticItemTransforms(base);
            for (DyeColor color : DyeColor.values()) {
                if (color == DyeColor.WHITE) continue;
                var variant = provider.getBuilder(context.getName() + "_" + color.getName())
                    .parent(new ModelFile.UncheckedModelFile(
                        provider.modLoc("block/" + context.getName() + "_" + color.getName())
                    ));
                addUniversalPlasticItemTransforms(variant);
                base.override()
                    .predicate(AnvilcraftPlasticraft.of("plastic_color"), color.getId())
                    .model(variant)
                    .end();
            }
        })
        .build()
        .register();
    public static final BlockEntry<LiquidBlock> CRUDE_OIL_ACID = fluidBlock(
        "crude_oil_acid",
        PlasticraftFluids.CRUDE_OIL_ACID,
        "oil_essence",
        MapColor.COLOR_BLACK,
        "Crude Oil Essence"
    );

    public static final BlockEntry<HardenedResinAnvilBlock> HARDEND_RESIN_ANVIL = AnvilcraftPlasticraft.REGISTRUM
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
            ModBlockTags.NON_MAGNETIC
        )
        .blockstate((context, provider) -> {
        })
        .item((block, properties) -> new HardenedResinAnvilItem(
            block,
            properties,
            PlasticraftEntities.HARDEND_RESIN_ANVIL,
            block::defaultBlockState
        ))
        .tag(PlasticraftItemTags.PLASTIC_ANVILS, PlasticraftItemTags.BUOYANT_PLASTIC_ITEMS, ItemTags.ANVIL)
        .model((context, provider) -> {
        })
        .build()
        .register();

    public static final BlockEntry<HardenedResinCauldronBlock> HARDEND_RESIN_CAULDRON = AnvilcraftPlasticraft.REGISTRUM
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
            PlasticraftEntities.HARDEND_RESIN_CAULDRON,
            block::defaultBlockState
        ))
        .tag(PlasticraftItemTags.PLASTIC_CAULDRONS, PlasticraftItemTags.BUOYANT_PLASTIC_ITEMS)
        .model((context, provider) -> {
        })
        .build()
        .register();

    public static final BlockEntry<CatalyticPressLidBlock> CATALYTIC_PRESS_LID = AnvilcraftPlasticraft.REGISTRUM
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
            PlasticraftEntities.CATALYTIC_PRESS_LID,
            block::defaultBlockState
        ))
        .tag(PlasticraftItemTags.BUOYANT_PLASTIC_ITEMS)
        .model((context, provider) -> provider.withExistingParent(
            context.getName(),
            provider.modLoc("block/catalytic_press_lid")
        ))
        .build()
        .register();

    public static final BlockEntry<ResinAnvilBlock> RESIN_ANVIL = AnvilcraftPlasticraft.REGISTRUM
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
            ModBlockTags.NON_MAGNETIC,
            PlasticraftBlockTags.RESIN_SHOCK_COMPATIBLE
        )
        .blockstate((context, provider) -> {
        })
        .item((block, properties) -> new ResinAnvilItem(
            block,
            properties.component(
                DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(0)
            ),
            PlasticraftEntities.RESIN_ANVIL,
            block::defaultBlockState
        ))
        .tag(PlasticraftItemTags.PLASTIC_ANVILS, PlasticraftItemTags.BUOYANT_PLASTIC_ITEMS)
        .model((context, provider) -> {
        })
        .build()
        .register();

    private PlasticraftBlocks() {
    }

    /** 为通用塑料物品固定标准显示变换，避免掉落物按一格实体原尺寸渲染。 */
    private static void addUniversalPlasticItemTransforms(ModelBuilder<?> model) {
        model.transforms()
            .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                .rotation(75.0F, 45.0F, 0.0F)
                .translation(0.0F, 2.5F, 0.0F)
                .scale(0.375F)
                .end()
            .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                .rotation(75.0F, 45.0F, 0.0F)
                .translation(0.0F, 2.5F, 0.0F)
                .scale(0.375F)
                .end()
            .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                .rotation(0.0F, 45.0F, 0.0F)
                .scale(0.4F)
                .end()
            .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                .rotation(0.0F, -135.0F, 0.0F)
                .scale(0.4F)
                .end()
            .transform(ItemDisplayContext.GROUND)
                .translation(0.0F, 3.0F, 0.0F)
                .scale(0.25F)
                .end()
            .transform(ItemDisplayContext.GUI)
                .rotation(30.0F, -135.0F, 0.0F)
                .scale(0.625F)
                .end()
            .transform(ItemDisplayContext.FIXED)
                .scale(0.5F)
                .end()
            .end();
    }

    private static BlockEntry<LiquidBlock> fluidBlock(
        String id,
        Supplier<? extends FlowingFluid> source,
        String textureName,
        MapColor mapColor,
        String name
    ) {
        return AnvilcraftPlasticraft.REGISTRUM.block(id, properties -> new LiquidBlock(source.get(), properties))
            .initialProperties(() -> Blocks.WATER)
            .properties(properties -> properties
                .mapColor(mapColor)
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
                    .texture("particle", provider.modLoc("block/" + textureName))
            ))
            .register();
    }

    private static ModelFile[] layeredCauldronModels(
        RegistrumBlockstateProvider provider,
        String name,
        String contentTexture
    ) {
        if (contentTexture.equals("block/universal_plastic_melt")) {
            provider.models().existingFileHelper.trackGenerated(
                provider.modLoc(contentTexture),
                ModelProvider.TEXTURE
            );
        }
        String renderType = contentTexture.equals("block/universal_plastic_melt")
            ? "minecraft:cutout"
            : "minecraft:translucent";
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
                .renderType(renderType);
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
