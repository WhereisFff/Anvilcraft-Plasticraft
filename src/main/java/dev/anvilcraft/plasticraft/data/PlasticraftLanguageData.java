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

        // 塑料成型舱物品说明、容器标题和建模器界面语言文件生成。
        provider.add(
            "tooltip.anvilcraftplasticraft.plastic_molding_chamber",
            "Edits and projects a 48 x 48 x 48 model in a rear 3 x 3 x 3 collisionless workspace"
        );
        provider.add(
            "container.anvilcraftplasticraft.plastic_molding_chamber",
            "Plastic Molding Chamber"
        );
        provider.add("screen.anvilcraftplasticraft.molding.search_type", "Search type");
        provider.add("screen.anvilcraftplasticraft.molding.search_json", "Search model");
        provider.add("screen.anvilcraftplasticraft.molding.rename", "Rename");
        provider.add("screen.anvilcraftplasticraft.molding.numeric", "Numeric property");
        provider.add("screen.anvilcraftplasticraft.molding.numeric.tooltip", "%1$s %2$s");
        provider.add("screen.anvilcraftplasticraft.molding.numeric.position", "Position");
        provider.add("screen.anvilcraftplasticraft.molding.numeric.size", "Size");
        provider.add("screen.anvilcraftplasticraft.molding.numeric.pivot", "Pivot");
        provider.add("screen.anvilcraftplasticraft.molding.numeric.rotation", "Rotation");
        provider.add("screen.anvilcraftplasticraft.molding.no_writer", "Read-only");
        provider.add("screen.anvilcraftplasticraft.molding.writer", "Editing: %s");
        provider.add(
            "screen.anvilcraftplasticraft.molding.analysis",
            "%1$s px / %2$s mB / %3$s clay"
        );
        provider.add("screen.anvilcraftplasticraft.molding.out_of_bounds", "Outside workspace");
        provider.add("screen.anvilcraftplasticraft.molding.tool.move", "Move");
        provider.add("screen.anvilcraftplasticraft.molding.tool.scale", "Resize");
        provider.add("screen.anvilcraftplasticraft.molding.tool.rotate", "Rotate");
        provider.add("screen.anvilcraftplasticraft.molding.tool.pivot", "Pivot");
        provider.add("screen.anvilcraftplasticraft.molding.tool.mirror", "Mirror");
        provider.add("screen.anvilcraftplasticraft.molding.type", "Assign type");
        provider.add("screen.anvilcraftplasticraft.molding.type.normal", "Normal product");
        provider.add("screen.anvilcraftplasticraft.molding.undo", "Undo");
        provider.add("screen.anvilcraftplasticraft.molding.copy", "Copy");
        provider.add("screen.anvilcraftplasticraft.molding.cut", "Cut (also delete)");
        provider.add("screen.anvilcraftplasticraft.molding.paste", "Paste");
        provider.add("screen.anvilcraftplasticraft.molding.json", "JSON management");
        provider.add("screen.anvilcraftplasticraft.molding.takeover", "Take over editing");
        provider.add("screen.anvilcraftplasticraft.molding.element.tooltip", "%1$s - %2$s");
        provider.add("screen.anvilcraftplasticraft.molding.element.cube", "Cube");
        provider.add("screen.anvilcraftplasticraft.molding.element.group", "Group");
        provider.add("screen.anvilcraftplasticraft.molding.context.new_cube", "New cube");
        provider.add("screen.anvilcraftplasticraft.molding.context.rename", "Rename");
        provider.add("screen.anvilcraftplasticraft.molding.context.delete", "Delete");
        provider.add("screen.anvilcraftplasticraft.molding.context.hide", "Show or hide");
        provider.add("screen.anvilcraftplasticraft.molding.context.lock", "Lock or unlock");
        provider.add("screen.anvilcraftplasticraft.molding.context.group", "Group selection");
        provider.add("screen.anvilcraftplasticraft.molding.context.center_pivot", "Center pivot");
        provider.add("screen.anvilcraftplasticraft.molding.context.focus", "Focus selection");
        provider.add("screen.anvilcraftplasticraft.molding.context.reset_camera", "Frame workspace");

        // 塑料成型舱编辑会话与服务端命令拒绝原因语言文件生成。
        provider.add("message.anvilcraftplasticraft.molding.invalid_session", "Editing session expired");
        provider.add("message.anvilcraftplasticraft.molding.stale_revision", "Model changed; view refreshed");
        provider.add("message.anvilcraftplasticraft.molding.not_editable", "Chamber is not editable");
        provider.add("message.anvilcraftplasticraft.molding.nothing_to_undo", "Nothing to undo");
        provider.add("message.anvilcraftplasticraft.molding.nothing_to_redo", "Nothing to redo");
        provider.add("message.anvilcraftplasticraft.molding.invalid_command", "Invalid model edit");
        provider.add("message.anvilcraftplasticraft.molding.session_taken_over", "Editing control acquired");

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
