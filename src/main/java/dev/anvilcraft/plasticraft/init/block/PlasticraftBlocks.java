package dev.anvilcraft.plasticraft.init.block;

import dev.anvilcraft.lib.v2.registrum.providers.generators.RegistrumBlockModelGenerator;
import dev.anvilcraft.lib.v2.registrum.providers.generators.RegistrumItemModelGenerator;
import dev.anvilcraft.lib.v2.registrum.providers.loot.RegistrumBlockLootTables;
import dev.anvilcraft.lib.v2.registrum.util.entry.BlockEntry;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.ClearPlasticBlock;
import dev.anvilcraft.plasticraft.block.ClearPlasticMeltCauldronBlock;
import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.block.ElasticPlasticBlock;
import dev.anvilcraft.plasticraft.block.EngineeringPlasticBlock;
import dev.anvilcraft.plasticraft.block.HardenedResinAnvilBlock;
import dev.anvilcraft.plasticraft.block.HardenedResinCauldronBlock;
import dev.anvilcraft.plasticraft.block.HeatResistantPlasticBlock;
import dev.anvilcraft.plasticraft.block.HighHeatFuelCauldronBlock;
import dev.anvilcraft.plasticraft.block.HighViscosityResinBlock;
import dev.anvilcraft.plasticraft.block.HighViscosityResinCauldronBlock;
import dev.anvilcraft.plasticraft.block.HighViscosityResinFluidBlock;
import dev.anvilcraft.plasticraft.block.Plastic3DPrintingComponentBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingChamberBlock;
import dev.anvilcraft.plasticraft.block.PlasticMoldingRegionBlock;
import dev.anvilcraft.plasticraft.block.PlasticOilCauldronBlock;
import dev.anvilcraft.plasticraft.block.ResinAnvilBlock;
import dev.anvilcraft.plasticraft.block.UniversalPlasticBlock;
import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltCauldronBlock;
import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltFluidBlock;
import dev.anvilcraft.plasticraft.block.UniversalPlasticShape;
import dev.anvilcraft.plasticraft.client.renderer.UniversalPlasticItemRenderer;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItemTags;
import dev.anvilcraft.plasticraft.item.ClearPlasticBlockItem;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.ElasticPlasticBlockItem;
import dev.anvilcraft.plasticraft.item.EngineeringPlasticBlockItem;
import dev.anvilcraft.plasticraft.item.HardenedResinAnvilItem;
import dev.anvilcraft.plasticraft.item.HardenedResinCauldronItem;
import dev.anvilcraft.plasticraft.item.HeatResistantPlasticBlockItem;
import dev.anvilcraft.plasticraft.item.HighViscosityResinBlockItem;
import dev.anvilcraft.plasticraft.item.Plastic3DPrintingComponentItem;
import dev.anvilcraft.plasticraft.item.PlasticMoldingChamberItem;
import dev.anvilcraft.plasticraft.item.ResinAnvilItem;
import dev.anvilcraft.plasticraft.item.UniversalPlasticBlockItem;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.dubhe.anvilcraft.block.cauldron.Layered4LevelCauldronBlock;
import dev.dubhe.anvilcraft.block.multipart.SimpleMultiPartBlock;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.init.block.ModBlockTags;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.item.block.SimpleMultiPartBlockItem;
import dev.dubhe.anvilcraft.util.registrater.DataGenUtil;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.item.properties.numeric.CustomModelDataProperty;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.model.generators.template.ElementBuilder;

import java.util.Optional;
import java.util.List;
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
        .loot(RegistrumBlockLootTables::dropSelf)
        .blockstate(() -> (ctx, gen) -> poweredMachineModels(gen, ctx.get(), "plastic_molding_chamber", "plastic_molding_chamber_off"))
        .item(PlasticMoldingChamberItem::new)
        .model(() -> (ctx, gen) -> gen.createWithExistingModel(ctx.get(), gen.modLoc("block/" + ctx.getName())))
        .build()
        .register();

    public static final BlockEntry<Plastic3DPrintingComponentBlock> PLASTIC_3D_PRINTING_COMPONENT =
        AnvilcraftPlasticraft.REGISTRUM
            .block("plastic_3d_printing_component", Plastic3DPrintingComponentBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(properties -> properties
                .noOcclusion()
                .strength(5.0F, 1200.0F)
                .sound(SoundType.METAL)
                .pushReaction(PushReaction.BLOCK))
            .lang("Plastic 3D Printing Component")
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .loot(RegistrumBlockLootTables::dropSelf)
            .blockstate(() -> (ctx, gen) -> poweredMachineModels(gen, ctx.get(), "print_component_body", "print_component_off"))
            .item(Plastic3DPrintingComponentItem::new)
            .model(() -> (ctx, gen) -> gen.createWithExistingModel(ctx.get(), gen.modLoc("block/print_component")))
            .build()
            .register();

    public static final BlockEntry<PlasticMoldingRegionBlock> PLASTIC_MOLDING_REGION = AnvilcraftPlasticraft.REGISTRUM
        .block("plastic_molding_region", PlasticMoldingRegionBlock::new)
        .initialProperties(() -> Blocks.BARRIER)
        .properties(properties -> properties
            .mapColor(MapColor.NONE)
            .noOcclusion()
            .strength(-1.0F, 3600000.0F)
            .pushReaction(PushReaction.BLOCK)
            .noLootTable())
        .lang("Plastic Molding Region")
        .blockstate(() -> (ctx, gen) -> gen.create(ctx.get(), Identifier.withDefaultNamespace("block/barrier")))
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
        .model(() -> (ctx, gen) -> gen.createWithExistingModel(ctx.get(), gen.modLoc("item/" + ctx.getName())))
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
        .blockstate(() -> (ctx, gen) -> gen.create(ctx.get(), gen.modLoc("block/high_viscosity_resin")))
        .item(HighViscosityResinBlockItem::new)
        .model(() -> (ctx, gen) -> gen.createWithExistingModel(ctx.get(), gen.modLoc("block/high_viscosity_resin")))
        .build()
        .tag(PlasticraftBlockTags.RESIN_SHOCK_COMPATIBLE)
        .register();

    public static final BlockEntry<HighViscosityResinCauldronBlock> LIQUID_HIGH_VISCOSITY_RESIN_CAULDRON = AnvilcraftPlasticraft.REGISTRUM
        .block("liquid_high_viscosity_resin_cauldron", HighViscosityResinCauldronBlock::new)
        .initialProperties(() -> Blocks.CAULDRON)
        .lang("Liquid High-Viscosity Resin Cauldron")
        .blockstate(DataGenUtil::noExtraModelOrState)
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
            .noCollision()
            .pushReaction(PushReaction.DESTROY)
            .noLootTable()
            .liquid()
            .sound(SoundType.EMPTY)
            .strength(100.0F))
        .lang("Liquid High-Viscosity Resin")
        .blockstate(() -> (ctx, gen) -> fluidModel(gen, ctx.get(), "fluid/liquid_high_viscosity_resin"))
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
        .blockstate(DataGenUtil::noExtraModelOrState)
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
        .blockstate(() -> (ctx, gen) -> layeredCauldronModels(gen, ctx.get(), ctx.getName(), "fluid/plastic_oil"))
        .loot((tables, block) -> tables.dropOther(block, Items.CAULDRON))
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.CAULDRONS)
        .onRegister(block -> Item.BY_BLOCK.put(block, Items.CAULDRON))
        .register();
    public static final BlockEntry<UniversalPlasticMeltFluidBlock> UNIVERSAL_PLASTIC_MELT = AnvilcraftPlasticraft.REGISTRUM
        .block(
            "universal_plastic_melt",
            properties -> new UniversalPlasticMeltFluidBlock(
                PlasticraftFluids.UNIVERSAL_PLASTIC_MELT,
                properties,
                PlasticMaterial.UNIVERSAL
            )
        )
        .properties(properties -> properties
            .mapColor(MapColor.SNOW)
            .replaceable()
            .noCollision()
            .pushReaction(PushReaction.DESTROY)
            .noLootTable()
            .liquid()
            .sound(SoundType.EMPTY)
            .strength(100.0F))
        .lang("Universal Plastic Melt")
        .blockstate(() -> (ctx, gen) -> fluidModel(gen, ctx.get(), "fluid/universal_plastic_melt"))
        .register();
    public static final BlockEntry<UniversalPlasticMeltCauldronBlock> UNIVERSAL_PLASTIC_MELT_CAULDRON = AnvilcraftPlasticraft.REGISTRUM
        .block(
            "universal_plastic_melt_cauldron",
            properties -> new UniversalPlasticMeltCauldronBlock(properties, PlasticMaterial.UNIVERSAL)
        )
        .initialProperties(() -> Blocks.CAULDRON)
        .lang("Universal Plastic Melt Cauldron")
        .blockstate(() -> (ctx, gen) -> layeredCauldronModels(gen, ctx.get(), ctx.getName(), "fluid/universal_plastic_melt"))
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
        .tag(
            BlockTags.MINEABLE_WITH_PICKAXE,
            PlasticraftBlockTags.PLASTIC_PRODUCTS,
            ModBlockTags.END_PORTAL_UNABLE_CHANGE
        )
        .loot(RegistrumBlockLootTables::dropSelf)
        .blockstate(() -> (context, provider) -> plasticBlockState(
            provider,
            context.get(),
            context.getName(),
            PlasticMaterial.UNIVERSAL
        ))
        .item((block, properties) -> new UniversalPlasticBlockItem(
            block,
            properties,
            PlasticraftEntities.UNIVERSAL_PLASTIC,
            block::defaultBlockState
        ))
        .lang("Universal Plastic Block")
.tag(PlasticraftItemTags.PLASTIC_PRODUCTS, PlasticraftItemTags.BUOYANT_PLASTIC_ITEMS)
        .model(() -> (ctx, gen) -> plasticItemModel(gen, ctx.get(), PlasticMaterial.UNIVERSAL))
        .build()
        .register();
    public static final BlockEntry<UniversalPlasticMeltFluidBlock> ENGINEERING_PLASTIC_MELT =
        AnvilcraftPlasticraft.REGISTRUM
            .block(
                "engineering_plastic_melt",
                properties -> new UniversalPlasticMeltFluidBlock(
                    PlasticraftFluids.ENGINEERING_PLASTIC_MELT,
                    properties,
                    PlasticMaterial.ENGINEERING
                )
            )
            .properties(properties -> properties
                .mapColor(MapColor.COLOR_LIGHT_BLUE)
                .replaceable()
                .noCollision()
                .pushReaction(PushReaction.DESTROY)
                .noLootTable()
                .liquid()
                .sound(SoundType.EMPTY)
                .strength(100.0F))
            .lang("Engineering Plastic Melt")
            .blockstate(() -> (ctx, gen) -> fluidModel(gen, ctx.get(), "fluid/engineering_plastic_melt"))
            .register();
    public static final BlockEntry<UniversalPlasticMeltCauldronBlock> ENGINEERING_PLASTIC_MELT_CAULDRON =
        AnvilcraftPlasticraft.REGISTRUM
            .block(
                "engineering_plastic_melt_cauldron",
                properties -> new UniversalPlasticMeltCauldronBlock(properties, PlasticMaterial.ENGINEERING)
            )
            .initialProperties(() -> Blocks.CAULDRON)
            .lang("Engineering Plastic Melt Cauldron")
            .blockstate(() -> (ctx, gen) -> layeredCauldronModels(gen, ctx.get(), ctx.getName(), "fluid/engineering_plastic_melt"))
            .loot((tables, block) -> tables.dropOther(block, Items.CAULDRON))
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.CAULDRONS)
            .onRegister(block -> Item.BY_BLOCK.put(block, Items.CAULDRON))
            .register();
    public static final BlockEntry<EngineeringPlasticBlock> ENGINEERING_PLASTIC = AnvilcraftPlasticraft.REGISTRUM
        .block("engineering_plastic", EngineeringPlasticBlock::new)
        .initialProperties(() -> Blocks.LIGHT_BLUE_CONCRETE)
        .properties(properties -> properties
            .mapColor(MapColor.COLOR_LIGHT_BLUE)
            .noOcclusion()
            .strength(2.5F, 6.0F)
            .sound(SoundType.BONE_BLOCK)
            .pushReaction(PushReaction.NORMAL))
        .lang("Engineering Plastic Block")
        .tag(
            BlockTags.MINEABLE_WITH_PICKAXE,
            PlasticraftBlockTags.PLASTIC_PRODUCTS,
            ModBlockTags.END_PORTAL_UNABLE_CHANGE
        )
        .loot(RegistrumBlockLootTables::dropSelf)
        .blockstate(() -> (context, provider) -> plasticBlockState(
            provider,
            context.get(),
            context.getName(),
            PlasticMaterial.ENGINEERING
        ))
        .item((block, properties) -> new EngineeringPlasticBlockItem(
            block,
            properties,
            PlasticraftEntities.ENGINEERING_PLASTIC,
            block::defaultBlockState
        ))
        .lang("Engineering Plastic Block")
.tag(PlasticraftItemTags.PLASTIC_PRODUCTS, PlasticraftItemTags.BUOYANT_PLASTIC_ITEMS)
        .model(() -> (ctx, gen) -> plasticItemModel(gen, ctx.get(), PlasticMaterial.ENGINEERING))
        .build()
        .register();
    public static final BlockEntry<UniversalPlasticMeltFluidBlock> HEAT_RESISTANT_PLASTIC_MELT =
        AnvilcraftPlasticraft.REGISTRUM
            .block(
                "heat_resistant_plastic_melt",
                properties -> new UniversalPlasticMeltFluidBlock(
                    PlasticraftFluids.HEAT_RESISTANT_PLASTIC_MELT,
                    properties,
                    PlasticMaterial.HEAT_RESISTANT
                )
            )
            .properties(properties -> properties
                .mapColor(MapColor.COLOR_YELLOW)
                .replaceable()
                .noCollision()
                .pushReaction(PushReaction.DESTROY)
                .noLootTable()
                .liquid()
                .sound(SoundType.EMPTY)
                .strength(100.0F))
            .lang("Heat-Resistant Plastic Melt")
            .blockstate(() -> (ctx, gen) -> fluidModel(gen, ctx.get(), "fluid/heat_resistant_plastic_melt"))
            .register();
    public static final BlockEntry<UniversalPlasticMeltCauldronBlock> HEAT_RESISTANT_PLASTIC_MELT_CAULDRON =
        AnvilcraftPlasticraft.REGISTRUM
            .block(
                "heat_resistant_plastic_melt_cauldron",
                properties -> new UniversalPlasticMeltCauldronBlock(properties, PlasticMaterial.HEAT_RESISTANT)
            )
            .initialProperties(() -> Blocks.CAULDRON)
            .lang("Heat-Resistant Plastic Melt Cauldron")
            .blockstate(() -> (ctx, gen) -> layeredCauldronModels(gen, ctx.get(), ctx.getName(), "fluid/heat_resistant_plastic_melt"))
            .loot((tables, block) -> tables.dropOther(block, Items.CAULDRON))
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.CAULDRONS)
            .onRegister(block -> Item.BY_BLOCK.put(block, Items.CAULDRON))
            .register();
    public static final BlockEntry<HeatResistantPlasticBlock> HEAT_RESISTANT_PLASTIC =
        AnvilcraftPlasticraft.REGISTRUM
            .block("heat_resistant_plastic", HeatResistantPlasticBlock::new)
            .initialProperties(() -> Blocks.YELLOW_CONCRETE)
            .properties(properties -> properties
                .mapColor(MapColor.COLOR_YELLOW)
                .noOcclusion()
                .strength(3.5F, 10.0F)
                .sound(SoundType.BONE_BLOCK)
                .pushReaction(PushReaction.NORMAL))
            .lang("Heat-Resistant Plastic Block")
            .tag(
                BlockTags.MINEABLE_WITH_PICKAXE,
                PlasticraftBlockTags.PLASTIC_PRODUCTS,
                ModBlockTags.END_PORTAL_UNABLE_CHANGE
            )
            .loot(RegistrumBlockLootTables::dropSelf)
            .blockstate(() -> (context, provider) -> plasticBlockState(
                provider,
                context.get(),
                context.getName(),
                PlasticMaterial.HEAT_RESISTANT
            ))
            .item((block, properties) -> new HeatResistantPlasticBlockItem(
                block,
                properties,
                PlasticraftEntities.HEAT_RESISTANT_PLASTIC,
                block::defaultBlockState
            ))
            .properties(Item.Properties::fireResistant)
            .lang("Heat-Resistant Plastic Block")
.tag(PlasticraftItemTags.PLASTIC_PRODUCTS, PlasticraftItemTags.BUOYANT_PLASTIC_ITEMS)
            .model(() -> (ctx, gen) -> plasticItemModel(gen, ctx.get(), PlasticMaterial.HEAT_RESISTANT))
            .build()
            .register();
    public static final BlockEntry<UniversalPlasticMeltFluidBlock> CLEAR_PLASTIC_MELT =
        AnvilcraftPlasticraft.REGISTRUM
            .block(
                "clear_plastic_melt",
                properties -> new UniversalPlasticMeltFluidBlock(
                    PlasticraftFluids.CLEAR_PLASTIC_MELT,
                    properties,
                    PlasticMaterial.CLEAR
                )
            )
            .properties(properties -> properties
                .mapColor(MapColor.NONE)
                .replaceable()
                .noCollision()
                .pushReaction(PushReaction.DESTROY)
                .noLootTable()
                .liquid()
                .sound(SoundType.EMPTY)
                .strength(100.0F))
            .lang("Clear Plastic Melt")
            .blockstate(() -> (ctx, gen) -> fluidModel(gen, ctx.get(), "fluid/clear_plastic_melt"))
            .register();
    public static final BlockEntry<ClearPlasticMeltCauldronBlock> CLEAR_PLASTIC_MELT_CAULDRON =
        AnvilcraftPlasticraft.REGISTRUM
            .block("clear_plastic_melt_cauldron", ClearPlasticMeltCauldronBlock::new)
            .initialProperties(() -> Blocks.CAULDRON)
            .lang("Clear Plastic Melt Cauldron")
            .blockstate(() -> (ctx, gen) -> layeredCauldronModels(gen, ctx.get(), ctx.getName(), "fluid/clear_plastic_melt"))
            .loot((tables, block) -> tables.dropOther(block, Items.CAULDRON))
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.CAULDRONS)
            .onRegister(block -> Item.BY_BLOCK.put(block, Items.CAULDRON))
            .register();
    public static final BlockEntry<ClearPlasticBlock> CLEAR_PLASTIC = AnvilcraftPlasticraft.REGISTRUM
        .block("clear_plastic", ClearPlasticBlock::new)
        .initialProperties(() -> Blocks.GLASS)
        .properties(properties -> properties
            .mapColor(MapColor.NONE)
            .noOcclusion()
            .strength(1.5F, 3.0F)
            .sound(SoundType.BONE_BLOCK)
            .pushReaction(PushReaction.NORMAL))
        .lang("Clear Plastic Block")
        .tag(
            BlockTags.MINEABLE_WITH_PICKAXE,
            PlasticraftBlockTags.PLASTIC_PRODUCTS,
            ModBlockTags.END_PORTAL_UNABLE_CHANGE,
            ModBlockTags.SPECTRAL_CAN_THROUGH,
            ModBlockTags.LASER_CAN_PASS_THROUGH
        )
        .loot(RegistrumBlockLootTables::dropSelf)
        .blockstate(() -> (context, provider) -> plasticBlockState(
            provider,
            context.get(),
            context.getName(),
            PlasticMaterial.CLEAR
        ))
        .item((block, properties) -> new ClearPlasticBlockItem(
            block,
            properties,
            PlasticraftEntities.CLEAR_PLASTIC,
            block::defaultBlockState
        ))
        .lang("Clear Plastic Block")
.tag(PlasticraftItemTags.PLASTIC_PRODUCTS, PlasticraftItemTags.BUOYANT_PLASTIC_ITEMS)
        .model(() -> (ctx, gen) -> plasticItemModel(gen, ctx.get(), PlasticMaterial.CLEAR))
        .build()
        .register();
    public static final BlockEntry<UniversalPlasticMeltFluidBlock> ELASTIC_PLASTIC_MELT =
        AnvilcraftPlasticraft.REGISTRUM
            .block(
                "elastic_plastic_melt",
                properties -> new UniversalPlasticMeltFluidBlock(
                    PlasticraftFluids.ELASTIC_PLASTIC_MELT,
                    properties,
                    PlasticMaterial.ELASTIC
                )
            )
            .properties(properties -> properties
                .mapColor(MapColor.NONE)
                .replaceable()
                .noCollision()
                .pushReaction(PushReaction.DESTROY)
                .noLootTable()
                .liquid()
                .sound(SoundType.EMPTY)
                .strength(100.0F))
            .lang("Elastic Plastic Melt")
            .blockstate(() -> (ctx, gen) -> fluidModel(gen, ctx.get(), "fluid/elastic_plastic_melt"))
            .register();
    public static final BlockEntry<UniversalPlasticMeltCauldronBlock> ELASTIC_PLASTIC_MELT_CAULDRON =
        AnvilcraftPlasticraft.REGISTRUM
            .block(
                "elastic_plastic_melt_cauldron",
                properties -> new UniversalPlasticMeltCauldronBlock(properties, PlasticMaterial.ELASTIC)
            )
            .initialProperties(() -> Blocks.CAULDRON)
            .lang("Elastic Plastic Melt Cauldron")
            .blockstate(() -> (ctx, gen) -> layeredCauldronModels(gen, ctx.get(), ctx.getName(), "fluid/elastic_plastic_melt"))
            .loot((tables, block) -> tables.dropOther(block, Items.CAULDRON))
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.CAULDRONS)
            .onRegister(block -> Item.BY_BLOCK.put(block, Items.CAULDRON))
            .register();
    public static final BlockEntry<ElasticPlasticBlock> ELASTIC_PLASTIC = AnvilcraftPlasticraft.REGISTRUM
        .block("elastic_plastic", ElasticPlasticBlock::new)
        .initialProperties(() -> ModBlocks.RESIN_BLOCK.get())
        .properties(properties -> properties
            .mapColor(MapColor.NONE)
            .noOcclusion()
            .friction(0.6F)
            .strength(1.5F, 3.0F)
            .sound(SoundType.SLIME_BLOCK)
            .pushReaction(PushReaction.NORMAL))
        .lang("Elastic Plastic Block")
        .tag(
            BlockTags.MINEABLE_WITH_PICKAXE,
            PlasticraftBlockTags.PLASTIC_PRODUCTS,
            ModBlockTags.END_PORTAL_UNABLE_CHANGE
        )
        .loot(RegistrumBlockLootTables::dropSelf)
        .blockstate(() -> (context, provider) -> plasticBlockState(
            provider,
            context.get(),
            context.getName(),
            PlasticMaterial.ELASTIC
        ))
        .item((block, properties) -> new ElasticPlasticBlockItem(
            block,
            properties,
            PlasticraftEntities.ELASTIC_PLASTIC,
            block::defaultBlockState
        ))
        .lang("Elastic Plastic Block")
.tag(PlasticraftItemTags.PLASTIC_PRODUCTS, PlasticraftItemTags.BUOYANT_PLASTIC_ITEMS)
        .model(() -> (ctx, gen) -> plasticItemModel(gen, ctx.get(), PlasticMaterial.ELASTIC))
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
            ModBlockTags.NON_MAGNETIC,
            ModBlockTags.END_PORTAL_UNABLE_CHANGE
        )
        .blockstate(DataGenUtil::noExtraModelOrState)
        .item((block, properties) -> new HardenedResinAnvilItem(
            block,
            properties,
            PlasticraftEntities.HARDEND_RESIN_ANVIL,
            block::defaultBlockState
        ))
        .lang("Hardened Resin Anvil")
.tag(PlasticraftItemTags.PLASTIC_ANVILS, PlasticraftItemTags.BUOYANT_PLASTIC_ITEMS, ItemTags.ANVIL)
        .model(() -> (ctx, gen) -> magneticModel(gen, ctx.get(), ctx.getName()))
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
        .tag(
            BlockTags.MINEABLE_WITH_PICKAXE,
            BlockTags.CAULDRONS,
            ModBlockTags.END_PORTAL_UNABLE_CHANGE
        )
        .blockstate(DataGenUtil::noExtraModelOrState)
        .item((block, properties) -> new HardenedResinCauldronItem(
            block,
            properties,
            PlasticraftEntities.HARDEND_RESIN_CAULDRON,
            block::defaultBlockState
        ))
        .lang("Hardened Resin Cauldron")
.tag(PlasticraftItemTags.PLASTIC_CAULDRONS, PlasticraftItemTags.BUOYANT_PLASTIC_ITEMS)
        .model(() -> (ctx, gen) -> magneticModel(gen, ctx.get(), ctx.getName()))
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
            PlasticraftBlockTags.RESIN_SHOCK_COMPATIBLE,
            ModBlockTags.END_PORTAL_UNABLE_CHANGE
        )
        .blockstate(DataGenUtil::noExtraModelOrState)
        .item((block, properties) -> new ResinAnvilItem(
            block,
            properties.component(
                DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(0f), List.of(), List.of(), List.of())
            ),
            PlasticraftEntities.RESIN_ANVIL,
            block::defaultBlockState
        ))
        .lang("Resin Anvil")
.tag(PlasticraftItemTags.PLASTIC_ANVILS, PlasticraftItemTags.BUOYANT_PLASTIC_ITEMS)
        .model(() -> (ctx, gen) -> magneticModel(gen, ctx.get(), ctx.getName()))
        .build()
        .register();

    private PlasticraftBlocks() {
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
                .noCollision()
                .pushReaction(PushReaction.DESTROY)
                .noLootTable()
                .liquid()
                .sound(SoundType.EMPTY))
            .lang(name)
            .blockstate(() -> (ctx, gen) -> fluidModel(gen, ctx.get(), "fluid/" + textureName))
            .register();
    }

    private static void plasticItemModel(RegistrumItemModelGenerator generator, Item item, PlasticMaterial material) {
        var base = new ModelTemplate(Optional.of(generator.modLoc("item/plastic")), Optional.empty(), TextureSlot.PARTICLE);
        var model = base.create(ModelLocationUtils.getModelLocation(item),
            TextureMapping.particle(new Material(material.sprite(DyeColor.WHITE))), generator.modelOutput);
        generator.itemModelOutput.accept(item, ItemModelUtils.specialModel(model, UniversalPlasticItemRenderer.Unbaked.INSTANCE));
    }

    private static void magneticModel(RegistrumItemModelGenerator generator, Item item, String name) {
        var base = generator.modLoc("item/" + name);
        generator.itemModelOutput.accept(item, ItemModelUtils.rangeSelect(
            new CustomModelDataProperty(0),
            ItemModelUtils.plainModel(base),
            ItemModelUtils.override(ItemModelUtils.plainModel(base.withSuffix("_magnetic")), 1)
        ));
    }

    private static void poweredMachineModels(RegistrumBlockModelGenerator provider, Block block, String on, String off) {
        provider.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
            .with(PropertyDispatch.initial(PlasticMoldingChamberBlock.POWERED)
                .generate(powered -> BlockModelGenerators.plainVariant(provider.modLoc("block/" + (powered ? on : off)))))
            .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
    }

    private static void fluidModel(RegistrumBlockModelGenerator provider, Block block, String texture) {
        provider.create(block, provider.getBuilder().texture(TextureSlot.PARTICLE, provider.modLoc(texture)).build(block));
    }

    private static void layeredCauldronModels(
        RegistrumBlockModelGenerator provider, Block block, String name, String texture
    ) {
        boolean translucent = !texture.endsWith("_plastic_melt")
            || texture.endsWith("clear_plastic_melt") || texture.endsWith("elastic_plastic_melt");
        provider.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
            .with(PropertyDispatch.initial(Layered4LevelCauldronBlock.LEVEL).generate(level -> {
                Identifier parent = level == 4 ? provider.mcLoc("block/template_cauldron_full")
                    : Identifier.fromNamespaceAndPath("anvilcraft", "block/template_cauldron_level" + level + "of4");
                Identifier model = provider.getBuilder().parent(parent)
                    .texture(TextureSlot.BOTTOM, provider.mcLoc("block/cauldron_bottom"))
                    .texture(TextureSlot.CONTENT, provider.modLoc(texture), translucent)
                    .texture(TextureSlot.INSIDE, provider.mcLoc("block/cauldron_inner"))
                    .texture(TextureSlot.PARTICLE, provider.mcLoc("block/cauldron_side"))
                    .texture(TextureSlot.SIDE, provider.mcLoc("block/cauldron_side"))
                    .texture(TextureSlot.TOP, provider.mcLoc("block/cauldron_top"))
                    .build(provider.modLoc("block/" + name + "_level" + level));
                return BlockModelGenerators.plainVariant(model);
            })));
    }

    private static void plasticBlockState(RegistrumBlockModelGenerator provider, Block block, String name, PlasticMaterial material) {
        if (material.hasColorState()) {
            provider.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
                .with(PropertyDispatch.initial(DyeableMaterial.COLOR).generate(color ->
                    BlockModelGenerators.plainVariant(plasticBlockModel(provider, name, material, color)))));
        } else {
            provider.create(block, plasticBlockModel(provider, name, material, DyeColor.WHITE));
        }
    }

    private static Identifier plasticBlockModel(RegistrumBlockModelGenerator provider, String name, PlasticMaterial material, DyeColor color) {
        Identifier sprite = material.sprite(color);
        TextureSlot plastic = TextureSlot.create("plastic");
        return provider.getBuilder().texture(TextureSlot.PARTICLE, sprite, material.isTransparent())
            .texture(plastic, sprite, material.isTransparent())
            .transformTemplate(template -> {
                template.element(element -> plasticCube(element, plastic, 0, 16));
                if (material == PlasticMaterial.ELASTIC) {
                    template.element(element -> plasticCube(element, plastic, 2, 14));
                }
            })
            .build(provider.modLoc("block/" + name + "_" + color.getName()));
    }

    private static void plasticCube(ElementBuilder cube, TextureSlot plastic, int min, int max) {
        cube.from(min, min, min).to(max, max, max).allFaces((direction, face) -> {
            var region = UniversalPlasticShape.uv(direction);
            var layout = UniversalPlasticShape.TEXTURE_LAYOUT;
            face.texture(plastic).uvs(
                region.modelU0(layout.atlasWidth()), region.modelV0(layout.atlasHeight()),
                region.modelU1(layout.atlasWidth()), region.modelV1(layout.atlasHeight())
            );
        });
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
