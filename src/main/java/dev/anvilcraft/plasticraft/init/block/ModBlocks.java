package dev.anvilcraft.plasticraft.init.block;

import dev.anvilcraft.lib.v2.registrum.util.entry.BlockEntry;
import dev.anvilcraft.plasticraft.block.HighViscosityResinBlock;
import dev.anvilcraft.plasticraft.block.HighViscosityResinCauldronBlock;
import dev.anvilcraft.plasticraft.block.HighViscosityResinFluidBlock;
import dev.anvilcraft.plasticraft.block.HardenedResinAnvilBlock;
import dev.anvilcraft.plasticraft.block.HardenedResinCauldronBlock;
import dev.anvilcraft.plasticraft.block.ResinAnvilBlock;
import dev.anvilcraft.plasticraft.init.entity.ModEntities;
import dev.anvilcraft.plasticraft.init.item.ModItemTags;
import dev.anvilcraft.plasticraft.item.HardenedResinAnvilItem;
import dev.anvilcraft.plasticraft.item.HardenedResinCauldronItem;
import dev.anvilcraft.plasticraft.item.HighViscosityResinBlockItem;
import dev.anvilcraft.plasticraft.item.ResinAnvilItem;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

import static dev.anvilcraft.plasticraft.AnvilcraftPlasticraft.REGISTRUM;

/** Plasticraft 可移动制品的方块和物品注册。 */
public final class ModBlocks {
    public static final BlockEntry<HighViscosityResinBlock> HIGH_VISCOSITY_RESIN_BLOCK = REGISTRUM
        .block("high_viscosity_resin_block", HighViscosityResinBlock::new)
        .initialProperties(() -> dev.dubhe.anvilcraft.init.block.ModBlocks.RESIN_BLOCK.get())
        .properties(properties -> properties
            .mapColor(MapColor.COLOR_ORANGE)
            .noOcclusion()
            .sound(SoundType.HONEY_BLOCK))
        .lang("High-Viscosity Resin Block")
        .blockstate((context, provider) -> {
            provider.simpleBlock(context.get());
            provider.models()
                .cubeAll(context.getName(), provider.modLoc("block/" + context.getName()))
                .renderType("translucent");
        })
        .item(HighViscosityResinBlockItem::new)
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
                .texture("particle", provider.modLoc("block/liquid_high_viscosity_resin_still"))
        ))
        .register();

    public static final BlockEntry<HardenedResinAnvilBlock> HARDEND_RESIN_ANVIL = REGISTRUM
        .block("hardend_resin_anvil", HardenedResinAnvilBlock::new)
        .initialProperties(() -> Blocks.ANVIL)
        .properties(properties -> properties.noOcclusion().strength(5.0F, 1200.0F))
        .lang("Hardened Resin Anvil")
        .tag(
            BlockTags.MINEABLE_WITH_PICKAXE,
            BlockTags.ANVIL,
            dev.dubhe.anvilcraft.init.block.ModBlockTags.NON_MAGNETIC,
            ModBlockTags.RESIN_SHOCK_COMPATIBLE
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
        .properties(properties -> properties.noOcclusion().strength(2.0F, 20.0F))
        .lang("Hardened Resin Cauldron")
        .tag(BlockTags.MINEABLE_WITH_PICKAXE)
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

    public static final BlockEntry<ResinAnvilBlock> RESIN_ANVIL = REGISTRUM
        .block("resin_anvil", ResinAnvilBlock::new)
        .initialProperties(() -> Blocks.ANVIL)
        .properties(properties -> properties.noOcclusion().strength(4.0F, 80.0F))
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

    public static void register() {
        // 类加载时，静态条目会挂接到 Registrum 事件总线。
    }

    public static void registerDispenserBehavior(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            DispenserBlock.registerBehavior(RESIN_ANVIL.asItem(), ResinAnvilItem::dispense);
            DispenserBlock.registerBehavior(HIGH_VISCOSITY_RESIN_BLOCK.asItem(), HighViscosityResinBlockItem::dispense);
            HighViscosityResinCauldronBlock.registerInteractions();
        });
    }
}
