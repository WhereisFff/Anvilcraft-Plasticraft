package dev.anvilcraft.plasticraft.data;

import dev.anvilcraft.lib.v2.registrum.providers.ProviderType;
import dev.anvilcraft.lib.v2.registrum.providers.RegistrumLangProvider;
import dev.anvilcraft.plasticraft.api.tooltip.PlasticItemTooltipManager;
import dev.anvilcraft.plasticraft.init.item.ModItemGroups;

import static dev.anvilcraft.plasticraft.AnvilcraftPlasticraft.REGISTRUM;

/** 生成物品名、界面文本、提示文本和可选集成所需的英文语言数据。 */
public final class PlasticraftLanguageData {
    private PlasticraftLanguageData() {
    }

    public static void register() {
        REGISTRUM.addDataGenerator(ProviderType.LANG, PlasticraftLanguageData::generate);
    }

    private static void generate(RegistrumLangProvider provider) {
        // 静态物品提示的翻译键与运行时 manager 共用同一份声明，避免两边漏改。
        PlasticItemTooltipManager.getNormalMap().forEach(
            (itemId, description) -> provider.add(
                PlasticItemTooltipManager.getTranslationKey(itemId),
                description
            )
        );
        PlasticItemTooltipManager.getShiftMap().forEach(
            (itemId, description) -> provider.add(
                PlasticItemTooltipManager.getTranslationKeyShift(itemId),
                description
            )
        );

        // 物品组及无法由 Registrum 条目自动生成的物品名称。
        provider.add(ModItemGroups.TITLE_KEY, "Anvilcraft: Plasticraft");
        provider.add("item.anvilcraftplasticraft.hardend_resin_anvil", "Hardened Resin Anvil");
        provider.add("item.anvilcraftplasticraft.hardend_resin_cauldron", "Hardened Resin Cauldron");
        provider.add("item.anvilcraftplasticraft.resin_anvil", "Resin Anvil");
        provider.add("item.anvilcraftplasticraft.universal_plastic", "Universal Plastic Block");

        // 高黏度树脂粘接工具的操作结果消息。
        provider.add("message.anvilcraftplasticraft.adhesive.out_of_range", "Too far away");
        provider.add(
            "message.anvilcraftplasticraft.adhesive.too_far_disconnected",
            "Too far away; selection disconnected"
        );

        // 物品、实体和 HUD 共用的动态状态提示。
        provider.add("tooltip.anvilcraftplasticraft.magnetized", "Magnetized");
        provider.add("tooltip.anvilcraftplasticraft.resin_anvil.captured", "Contains: %s");
        provider.add("tooltip.anvilcraftplasticraft.color", "Color: %s");
        provider.add("tooltip.anvilcraftplasticraft.bonded", "Bonded in place");

        // Jade 插件配置项及其方块、实体状态文本。
        provider.add("tooltip.anvilcraftplasticraft.jade.color", "Material colour: %s");
        provider.add("tooltip.anvilcraftplasticraft.jade.pushable", "Can be pushed");
        provider.add("tooltip.anvilcraftplasticraft.jade.item_count", "%1$s x %2$s");
        provider.add("tooltip.anvilcraftplasticraft.jade.empty", "Empty");
        provider.add("tooltip.anvilcraftplasticraft.jade.gas", "%1$s %2$s / %3$s");
        provider.add("tooltip.anvilcraftplasticraft.jade.fluid", "%1$s %2$s / %3$s");
        provider.add("config.jade.plugin_anvilcraftplasticraft.bonded_entity", "Bonded entities");
        provider.add("config.jade.plugin_anvilcraftplasticraft.bonded_block", "Bonded blocks");
        provider.add("config.jade.plugin_anvilcraftplasticraft.hardend_resin_anvil", "Hardened Resin Anvil");
        provider.add("config.jade.plugin_anvilcraftplasticraft.condenser_tower", "Condenser Tower");
        provider.add("config.jade.plugin_anvilcraft.fluid_tank", "Fluid Tank");

        // JEI 配方分类、虚拟气体名称和蒸发速率说明。
        provider.add("gui.anvilcraftplasticraft.category.plasma_jet_blasting", "Plasma Jet Blasting");
        provider.add("gui.anvilcraftplasticraft.category.condenser", "Condensation");
        provider.add("jei.anvilcraftplasticraft.gas.gaseous_oil", "Gaseous oil");
        provider.add("jei.anvilcraftplasticraft.gas.gaseous_water", "Gaseous water");
        provider.add("jei.anvilcraftplasticraft.gas.gaseous_experience", "Gaseous experience");
        provider.add("jei.anvilcraftplasticraft.vaporization_rate", "Vaporization rate: %s mB/gt");

        // 树脂砧锤方向选择界面的六个方向名称。
        provider.add("screen.anvilcraftplasticraft.hammer_direction.up", "Up");
        provider.add("screen.anvilcraftplasticraft.hammer_direction.down", "Down");
        provider.add("screen.anvilcraftplasticraft.hammer_direction.north", "North");
        provider.add("screen.anvilcraftplasticraft.hammer_direction.east", "East");
        provider.add("screen.anvilcraftplasticraft.hammer_direction.south", "South");
        provider.add("screen.anvilcraftplasticraft.hammer_direction.west", "West");
    }
}
