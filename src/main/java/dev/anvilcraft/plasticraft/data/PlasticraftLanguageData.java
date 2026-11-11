package dev.anvilcraft.plasticraft.data;

import dev.anvilcraft.lib.v2.config.ConfigData;
import dev.anvilcraft.lib.v2.registrum.providers.ProviderType;
import dev.anvilcraft.lib.v2.registrum.providers.RegistrumLangProvider;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.api.tooltip.PlasticItemTooltipManager;
import dev.anvilcraft.plasticraft.config.PlasticraftClientConfig;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItemGroups;

/** 生成物品名、界面文本、提示文本和可选集成所需的英文语言数据。 */
public final class PlasticraftLanguageData {
    private PlasticraftLanguageData() {
    }

    public static void register() {
        AnvilcraftPlasticraft.REGISTRUM.addDataGenerator(ProviderType.LANG, PlasticraftLanguageData::generate);
    }

    private static void generate(RegistrumLangProvider provider) {
        ConfigData.readConfigClass(provider, PlasticraftClientConfig.class);
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

        // 物品组、塑料材料名称及无法由 Registrum 条目自动生成的物品名称。
        provider.add(PlasticraftItemGroups.TITLE_KEY, "Anvilcraft: Plasticraft");
        provider.add(PlasticraftItemGroups.SECTION_TITLE_KEY_PREFIX + "universal_plastic", "Universal Plastic");
        provider.add(
            PlasticraftItemGroups.SECTION_TITLE_KEY_PREFIX + "universal_plastic.tooltip",
            "Universal plastic melt, granules and molded products; click the banner to expand or fold all 16 colors"
        );
        provider.add(PlasticraftItemGroups.SECTION_TITLE_KEY_PREFIX + "engineering_plastic", "Engineering Plastic");
        provider.add(
            PlasticraftItemGroups.SECTION_TITLE_KEY_PREFIX + "engineering_plastic.tooltip",
            "Engineering plastic melt, granules and molded products; click the banner to expand or fold all 16 colors"
        );
        provider.add(PlasticraftItemGroups.SECTION_TITLE_KEY_PREFIX + "clear_plastic", "Clear Plastic");
        provider.add(
            PlasticraftItemGroups.SECTION_TITLE_KEY_PREFIX + "clear_plastic.tooltip",
            "Clear plastic melt, granules and molded products; click the banner to expand or fold all 16 colors"
        );
        provider.add(
            PlasticraftItemGroups.SECTION_TITLE_KEY_PREFIX + "heat_resistant_plastic",
            "Heat-Resistant Plastic"
        );
        provider.add(
            PlasticraftItemGroups.SECTION_TITLE_KEY_PREFIX + "heat_resistant_plastic.tooltip",
            "Heat-resistant plastic melt, granules and molded products; click the banner to expand or fold all 16 colors"
        );
        provider.add("item.anvilcraftplasticraft.hardend_resin_anvil", "Hardened Resin Anvil");
        provider.add("item.anvilcraftplasticraft.hardend_resin_cauldron", "Hardened Resin Cauldron");
        provider.add("item.anvilcraftplasticraft.resin_anvil", "Resin Anvil");
        provider.add("item.anvilcraftplasticraft.universal_plastic", "Universal Plastic Block");
        provider.add("material.anvilcraftplasticraft.universal_plastic", "Universal Plastic");
        provider.add("item.anvilcraftplasticraft.engineering_plastic", "Engineering Plastic Block");
        provider.add("material.anvilcraftplasticraft.engineering_plastic", "Engineering Plastic");
        provider.add("item.anvilcraftplasticraft.heat_resistant_plastic", "Heat-Resistant Plastic Block");
        provider.add("material.anvilcraftplasticraft.heat_resistant_plastic", "Heat-Resistant Plastic");
        provider.add("item.anvilcraftplasticraft.clear_plastic", "Clear Plastic Block");
        provider.add("material.anvilcraftplasticraft.clear_plastic", "Clear Plastic");
        provider.add(PlasticraftItemGroups.SECTION_TITLE_KEY_PREFIX + "elastic_plastic", "Elastic Plastic");
        provider.add(
            PlasticraftItemGroups.SECTION_TITLE_KEY_PREFIX + "elastic_plastic.tooltip",
            "Elastic plastic melt, granules and bouncy molded products; click the banner to expand or fold all 16 colors"
        );
        provider.add("item.anvilcraftplasticraft.elastic_plastic", "Elastic Plastic Block");
        provider.add("material.anvilcraftplasticraft.elastic_plastic", "Elastic Plastic");
        provider.add("tooltip.anvilcraftplasticraft.molded_feature.elastic", ", elastic");
        provider.add(
            "jei.anvilcraftplasticraft.plastic_melt_catalysis.resin_priority",
            "Cold resin catalysts take priority for elastic melt; hardened resin cauldrons also act as catalysts"
        );
        provider.add("item.anvilcraftplasticraft.molded_product_name", "%1$s %2$s");
        provider.add("item.anvilcraftplasticraft.molded_product_suffix.block", "Block");

        // 塑料成型舱物品说明、容器标题和建模器界面语言文件生成。
        provider.add(
            "tooltip.anvilcraftplasticraft.plastic_molding_chamber",
            "Models and produces all kinds of plastic products"
        );
        provider.add(
            "container.anvilcraftplasticraft.plastic_molding_chamber",
            "Plastic Molding Chamber"
        );
        provider.add("screen.anvilcraftplasticraft.molding.search_type", "Search type");
        provider.add("screen.anvilcraftplasticraft.molding.search_json", "Search model");
        provider.add("screen.anvilcraftplasticraft.molding.rename", "Rename");
        provider.add("screen.anvilcraftplasticraft.molding.numeric", "Numeric property");
        provider.add(
            "screen.anvilcraftplasticraft.molding.numeric.tooltip",
            "%1$s %2$s\nExpressions: 8/2, +1, -=1, *2\nScroll: 1 px / 15° · Shift: 0.1 px / 1° · Ctrl: 10 px / 45°\nEnter: apply · Esc: cancel · Tab: next field"
        );
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
        provider.add("screen.anvilcraftplasticraft.molding.tool.move", "Move (V)");
        provider.add("screen.anvilcraftplasticraft.molding.tool.scale", "Resize (S)");
        provider.add("screen.anvilcraftplasticraft.molding.tool.rotate", "Rotate (R)");
        provider.add("screen.anvilcraftplasticraft.molding.tool.pivot", "Pivot (P)");
        provider.add("screen.anvilcraftplasticraft.molding.tool.mirror", "Mirror (M)");
        provider.add("screen.anvilcraftplasticraft.molding.type", "Assign type");
        provider.add("screen.anvilcraftplasticraft.molding.type.normal", "Normal");
        provider.add("screen.anvilcraftplasticraft.molding.type.crate", "Crate");
        provider.add("screen.anvilcraftplasticraft.molding.type.tank", "Tank");
        provider.add("screen.anvilcraftplasticraft.molding.type.anvil", "Anvil");
        provider.add("screen.anvilcraftplasticraft.molding.type.cauldron", "Cauldron");
        // 大型炼药锅不在类型面板中显示，该名称只用于成品后缀。
        provider.add("screen.anvilcraftplasticraft.molding.type.large_cauldron", "Large Cauldron");
        provider.add("screen.anvilcraftplasticraft.molding.type.tray", "Tray");
        provider.add("screen.anvilcraftplasticraft.molding.type_invalid", "This shape does not seem able to do that");
        provider.add("screen.anvilcraftplasticraft.molding.model_too_large", "Model is too large to process");
        provider.add("screen.anvilcraftplasticraft.molding.type_override_active", "Type limit bypass active");
        provider.add("screen.anvilcraftplasticraft.molding.creative_override_active", "Debug override active");
        provider.add("screen.anvilcraftplasticraft.molding.undo", "Undo (Ctrl+Z)");
        provider.add("screen.anvilcraftplasticraft.molding.copy", "Copy (Ctrl+C)");
        provider.add("screen.anvilcraftplasticraft.molding.cut", "Cut (Ctrl+X)");
        provider.add("screen.anvilcraftplasticraft.molding.paste", "Paste (Ctrl+V)");
        provider.add("screen.anvilcraftplasticraft.molding.json", "Model management");
        provider.add("screen.anvilcraftplasticraft.molding.takeover", "Take over editing");
        provider.add("screen.anvilcraftplasticraft.molding.element.tooltip", "%1$s - %2$s");
        provider.add("screen.anvilcraftplasticraft.molding.element.cube", "Cube");
        provider.add("screen.anvilcraftplasticraft.molding.element.group", "Group");
        provider.add("screen.anvilcraftplasticraft.molding.element.visible", "Visible");
        provider.add("screen.anvilcraftplasticraft.molding.element.hidden", "Hidden");
        provider.add("screen.anvilcraftplasticraft.molding.element.locked", "Locked");
        provider.add("screen.anvilcraftplasticraft.molding.element.unlocked", "Unlocked");
        provider.add("screen.anvilcraftplasticraft.molding.context.new_cube", "New cube");
        provider.add("screen.anvilcraftplasticraft.molding.context.rename", "Rename");
        provider.add("screen.anvilcraftplasticraft.molding.context.delete", "Delete");
        provider.add("screen.anvilcraftplasticraft.molding.context.delete_all", "Delete all cubes");
        provider.add("screen.anvilcraftplasticraft.molding.context.hide", "Show or hide");
        provider.add("screen.anvilcraftplasticraft.molding.context.lock", "Lock or unlock");
        provider.add("screen.anvilcraftplasticraft.molding.context.group", "Group selection");
        provider.add("screen.anvilcraftplasticraft.molding.context.center_pivot", "Center pivot");
        provider.add("screen.anvilcraftplasticraft.molding.context.focus", "Focus selection");
        provider.add("screen.anvilcraftplasticraft.molding.context.reset_camera", "Frame workspace");

        // 大建模工作区的工具栏、大纲、视角与操作反馈语言文件生成。
        provider.add("screen.anvilcraftplasticraft.molding.workspace.new_cube", "New");
        provider.add("screen.anvilcraftplasticraft.molding.workspace.new_cube.tooltip", "Create a Cube (N)");
        provider.add("screen.anvilcraftplasticraft.molding.workspace.duplicate", "Duplicate");
        provider.add("screen.anvilcraftplasticraft.molding.workspace.duplicate.tooltip", "Duplicate the selection (Ctrl+D)");
        provider.add("screen.anvilcraftplasticraft.molding.workspace.group", "Group");
        provider.add("screen.anvilcraftplasticraft.molding.workspace.group.tooltip", "Group the selection (Ctrl+G)");
        provider.add("screen.anvilcraftplasticraft.molding.workspace.undo", "Undo");
        provider.add("screen.anvilcraftplasticraft.molding.workspace.undo.tooltip", "Undo the last edit (Ctrl+Z)");
        provider.add("screen.anvilcraftplasticraft.molding.workspace.redo", "Redo");
        provider.add("screen.anvilcraftplasticraft.molding.workspace.redo.tooltip", "Redo the last undone edit (Ctrl+Shift+Z / Ctrl+Y)");
        provider.add("screen.anvilcraftplasticraft.molding.workspace.focus", "Focus");
        provider.add(
            "screen.anvilcraftplasticraft.molding.workspace.focus.tooltip",
            "Focus the selection (F) or frame the whole model (Home)"
        );
        provider.add("screen.anvilcraftplasticraft.molding.workspace.done", "Back");
        provider.add("screen.anvilcraftplasticraft.molding.workspace.done.tooltip", "Return to the machine panel (Ctrl+Space)");
        provider.add("screen.anvilcraftplasticraft.molding.workspace.search", "Find elements");
        provider.add("screen.anvilcraftplasticraft.molding.workspace.outliner", "Outliner");
        provider.add("screen.anvilcraftplasticraft.molding.workspace.status", "%s selected");
        provider.add("screen.anvilcraftplasticraft.molding.workspace.pending", "Applying edit…");
        provider.add(
            "screen.anvilcraftplasticraft.molding.workspace.read_only",
            "Read-only · Another player is editing or the chamber is locked"
        );
        provider.add("screen.anvilcraftplasticraft.molding.workspace.empty", "No matching elements");
        provider.add(
            "screen.anvilcraftplasticraft.molding.workspace.outliner_lock",
            "Toggle locking for this element or group (L for the selection)"
        );
        provider.add(
            "screen.anvilcraftplasticraft.molding.workspace.outliner_visibility",
            "Toggle visibility for this element or group (H for the selection)"
        );
        provider.add(
            "screen.anvilcraftplasticraft.molding.workspace.outliner_selection",
            "Click to select · Ctrl: add or remove · Shift: select a range · Right-click: actions"
        );
        provider.add(
            "screen.anvilcraftplasticraft.molding.workspace.navigation",
            "Left-drag: orbit · Right/middle-drag: pan · Scroll: zoom · Shift-drag: box select"
        );
        provider.add(
            "screen.anvilcraftplasticraft.molding.workspace.precision",
            "Snap: 1 px / 15° · Shift: 0.1 px / 1° · Ctrl: 10 px / 45° · Alt: free movement"
        );
        provider.add("screen.anvilcraftplasticraft.molding.workspace.perspective.tooltip", "Perspective view (5 / Numpad 5)");
        provider.add("screen.anvilcraftplasticraft.molding.workspace.front.tooltip", "Front view (1) · Ctrl+1 or right-click: back");
        provider.add("screen.anvilcraftplasticraft.molding.workspace.right.tooltip", "Right view (3) · Ctrl+3 or right-click: left");
        provider.add("screen.anvilcraftplasticraft.molding.workspace.top.tooltip", "Top view (7) · Ctrl+7 or right-click: bottom");
        provider.add(
            "screen.anvilcraftplasticraft.molding.workspace.expand.tooltip",
            "Expand or restore the modeling workspace (Ctrl+Space)"
        );
        provider.add(
            "screen.anvilcraftplasticraft.molding.workspace.numeric_invalid",
            "Invalid number or expression · Keep editing or press Esc to cancel"
        );
        provider.add(
            "screen.anvilcraftplasticraft.molding.workspace.transform_rejected",
            "This transform cannot be applied to the current selection"
        );

        provider.add(
            "screen.anvilcraftplasticraft.molding.workspace.rename_invalid",
            "The name cannot be applied · Keep editing or press Esc to cancel"
        );
        provider.add(
            "screen.anvilcraftplasticraft.molding.workspace.put_down_item",
            "Place the carried item in a slot before expanding the editor"
        );

        // 塑料成型舱磁盘控件和共享蓝图库叠加层语言文件生成。
        provider.add("screen.anvilcraftplasticraft.molding.disk.missing", "Place a Structure Disk in the disk slot");
        provider.add("screen.anvilcraftplasticraft.molding.disk.load", "Load the Structure Disk model for editing");
        provider.add("screen.anvilcraftplasticraft.molding.disk.invalid", "This Structure Disk has no valid molding model");
        provider.add("screen.anvilcraftplasticraft.molding.disk.store", "Store the current model on the Structure Disk and in the shared library");
        provider.add("screen.anvilcraftplasticraft.molding.disk.other_data", "Other Structure Disk data");
        provider.add("screen.anvilcraftplasticraft.molding.blueprint.owner", "Owner: %s");
        provider.add("screen.anvilcraftplasticraft.molding.blueprint.hash", "Hash: %s");
        provider.add("screen.anvilcraftplasticraft.molding.blueprint.pin", "Pin or unpin the selected model");
        provider.add("screen.anvilcraftplasticraft.molding.blueprint.copy", "Copy the selected model to your own shared blueprint");
        provider.add(
            "screen.anvilcraftplasticraft.molding.blueprint.open",
            "Open the model folder"
        );
        provider.add("screen.anvilcraftplasticraft.molding.blueprint.refresh", "Refresh models from the folder");
        provider.add("screen.anvilcraftplasticraft.molding.blueprint.delete", "Delete the selected model");
        provider.add(
            "screen.anvilcraftplasticraft.molding.blueprint.delete_confirm",
            "Hold Shift while clicking to delete the selected model"
        );

        // 塑料成型舱生产状态、资源条、模式和暂停原因语言文件生成。
        provider.add("screen.anvilcraftplasticraft.molding.state", "State: %s");
        provider.add("screen.anvilcraftplasticraft.molding.state.editable", "Editable");
        provider.add("screen.anvilcraftplasticraft.molding.state.waiting_to_lock", "Waiting to lock");
        provider.add("screen.anvilcraftplasticraft.molding.state.mold_filling", "Filling clay mold");
        provider.add("screen.anvilcraftplasticraft.molding.state.mold_ready", "Mold ready");
        provider.add("screen.anvilcraftplasticraft.molding.state.process_ready", "Ready to process");
        provider.add("screen.anvilcraftplasticraft.molding.state.processing", "Processing");
        provider.add("screen.anvilcraftplasticraft.molding.state.waiting_next_cycle", "Waiting for next cycle");
        provider.add("screen.anvilcraftplasticraft.molding.clay_status", "Clay: %1$s / %2$s / %3$s");
        provider.add("screen.anvilcraftplasticraft.molding.clay_lock_status", "Clay: %1$s / %2$s");
        provider.add("screen.anvilcraftplasticraft.molding.lock", "Lock and start");
        provider.add("screen.anvilcraftplasticraft.molding.lock_confirm", "Confirm and start processing");
        provider.add("screen.anvilcraftplasticraft.molding.unlock", "Unlock and return reserved clay");
        provider.add("screen.anvilcraftplasticraft.molding.mode.continuous", "Continuous processing");
        provider.add("screen.anvilcraftplasticraft.molding.mode.redstone", "Redstone control");
        provider.add("screen.anvilcraftplasticraft.molding.mode.single", "Single processing");
        provider.add("screen.anvilcraftplasticraft.molding.forming", "Forming method: %s");
        provider.add("screen.anvilcraftplasticraft.molding.forming.casting", "Casting");
        provider.add("screen.anvilcraftplasticraft.molding.forming.printing", "3D printing");
        provider.add("screen.anvilcraftplasticraft.molding.printing_progress", "Printing: %1$s / %2$s voxels");
        provider.add(
            "screen.anvilcraftplasticraft.molding.high_precision_requires_component",
            "High-precision types require a 3D Printing Component"
        );
        provider.add("screen.anvilcraftplasticraft.molding.fluid_staging", "Staging: %1$s / %2$s mB");
        provider.add("screen.anvilcraftplasticraft.molding.fluid_batch", "Batch: %1$s / %2$s mB");
        provider.add(
            "screen.anvilcraftplasticraft.molding.fluid_printing_component",
            "3D Printing Component: %1$s / %2$s mB"
        );
        provider.add("screen.anvilcraftplasticraft.molding.pump_rate", "Pump rate: %s mB/gt");
        provider.add(
            "screen.anvilcraftplasticraft.molding.printing_transfer_rate",
            "Chamber-to-component rate: %s mB/gt"
        );
        provider.add(
            "screen.anvilcraftplasticraft.molding.printing_consumption_rate",
            "Component printing consumption: %s mB/gt"
        );
        provider.add("screen.anvilcraftplasticraft.molding.partial_downgrade", "This result will downgrade to a normal product");
        provider.add("screen.anvilcraftplasticraft.molding.energy", "Energy: %1$s / %2$s FE");
        provider.add("screen.anvilcraftplasticraft.molding.rated_power", "Charging power: %s kW");
        provider.add("screen.anvilcraftplasticraft.molding.resource_slot", "Insert a capacitor to charge");
        provider.add("screen.anvilcraftplasticraft.molding.resource_slot.fluid", "Insert a bucket of plastic melt to fill");
        provider.add("screen.anvilcraftplasticraft.molding.resource_slot.type_override", "Insert Multiphase Transcendium to bypass type limits");
        provider.add("screen.anvilcraftplasticraft.molding.wait.none", "Standing by");
        provider.add("screen.anvilcraftplasticraft.molding.wait.invalid_model", "Invalid or empty model");
        provider.add("screen.anvilcraftplasticraft.molding.wait.structure_incomplete", "Chamber structure incomplete");
        provider.add("screen.anvilcraftplasticraft.molding.wait.region_blocked", "Forming region blocked");
        provider.add("screen.anvilcraftplasticraft.molding.wait.missing_clay", "Waiting for clay balls");
        provider.add(
            "screen.anvilcraftplasticraft.molding.wait.missing_printing_component",
            "Waiting for the 3D Printing Component"
        );
        provider.add("screen.anvilcraftplasticraft.molding.wait.missing_power", "Waiting for 256 kW power");
        provider.add("screen.anvilcraftplasticraft.molding.wait.mold_filling", "Filling clay mold");
        provider.add("screen.anvilcraftplasticraft.molding.wait.mold_ready", "Waiting for plastic melt");
        provider.add("screen.anvilcraftplasticraft.molding.wait.pumping", "Pumping at %s mB/gt");
        provider.add(
            "screen.anvilcraftplasticraft.molding.wait.pumping_printing_component",
            "Pumping into the 3D Printing Component at %s mB/gt"
        );
        provider.add("screen.anvilcraftplasticraft.molding.wait.batch_full", "Batch capacity reached");
        provider.add("screen.anvilcraftplasticraft.molding.wait.process_ready", "Ready to process");
        provider.add("screen.anvilcraftplasticraft.molding.wait.processing", "Processing transaction active");
        provider.add("screen.anvilcraftplasticraft.molding.wait.waiting_for_clear_region", "Waiting for region to clear");
        provider.add(
            "screen.anvilcraftplasticraft.molding.wait.printing_output_pending",
            "Waiting for the printed product to leave the discharge opening"
        );

        provider.add("tooltip.anvilcraftplasticraft.molding.state", "State: %s");
        provider.add("tooltip.anvilcraftplasticraft.molding.energy", "%1$s / %2$s FE, %3$s kW");
        provider.add("tooltip.anvilcraftplasticraft.molding.fluids", "Staging %1$s / %2$s, batch %3$s / %4$s");
        provider.add(
            "tooltip.anvilcraftplasticraft.molding.printing_fluids",
            "Staging %1$s / %2$s, 3D Printing Component %3$s / %4$s"
        );
        provider.add(
            "tooltip.anvilcraftplasticraft.molding.printing_rates",
            "Chamber transfer %1$s mB/gt, component consumption %2$s mB/gt"
        );
        provider.add("tooltip.anvilcraftplasticraft.molding.clay", "Clay slot %1$s, molded %2$s / %3$s");
        provider.add("tooltip.anvilcraftplasticraft.molding.partial_downgrade", "This result will downgrade to a normal product");
        provider.add("tooltip.anvilcraftplasticraft.molded_crate", "A plastic crate that stores %1$s items%2$s");
        provider.add(
            "tooltip.anvilcraftplasticraft.molded_tank",
            "A plastic tank that stores %1$s B of fluid%2$s"
        );
        // 成型炼药锅按「大型 / 普通」二选一，所有塑料锅都可装熔岩。
        provider.add(
            "tooltip.anvilcraftplasticraft.molded_cauldron",
            "A plastic cauldron that stores %1$s B of one fluid%2$s"
        );
        provider.add(
            "tooltip.anvilcraftplasticraft.molded_large_cauldron",
            "Stores layered fluids and processes ingredients in batches with anvil impacts"
        );
        provider.add("tooltip.anvilcraftplasticraft.molded_anvil", "Does not break when falling%1$s");
        provider.add(
            PlasticItemTooltipManager.DEMONSTRATION_TOOLTIP_KEY,
            "This is only a demonstration model\n"
                + "You can still model or import any model you want in the Plastic Molding Chamber"
        );
        provider.add(
            "tooltip.anvilcraftplasticraft.molded_giant_anvil",
            "Has giant anvil abilities%1$s"
        );
        provider.add(
            "tooltip.anvilcraftplasticraft.molded_tray",
            "A plastic tray that carries up to nine compatible redstone components with their native behavior and lighting"
        );
        provider.add("tooltip.anvilcraftplasticraft.molded_crate_contents", "Used capacity: %1$s/%2$s");
        provider.add("tooltip.anvilcraftplasticraft.molded_tank_contents", "Stored fluid: %1$s mB/%2$s B");
        provider.add("tooltip.anvilcraftplasticraft.molded_more_contents", "%s more content types");
        provider.add("tooltip.anvilcraftplasticraft.molded_feature.heat_resistant", ", fireproof");
        provider.add("tooltip.anvilcraftplasticraft.molded_feature.clear", ", transparent");
        provider.add("tooltip.anvilcraftplasticraft.molded_feature.engineering_anvil", ", Silk Touch");

        // 塑料成型舱编辑会话与服务端命令拒绝原因语言文件生成。
        provider.add("message.anvilcraftplasticraft.molding.invalid_session", "Editing session expired");
        provider.add("message.anvilcraftplasticraft.molding.stale_revision", "Model changed; view refreshed");
        provider.add("message.anvilcraftplasticraft.molding.not_editable", "Chamber is not editable");
        provider.add("message.anvilcraftplasticraft.molding.nothing_to_undo", "Nothing to undo");
        provider.add("message.anvilcraftplasticraft.molding.nothing_to_redo", "Nothing to redo");
        provider.add("message.anvilcraftplasticraft.molding.invalid_command", "Invalid model edit");
        provider.add("message.anvilcraftplasticraft.molding.session_taken_over", "Editing control acquired");
        provider.add("message.anvilcraftplasticraft.molding.already_locked", "Model is already locked");
        provider.add("message.anvilcraftplasticraft.molding.empty_model", "An empty model cannot be locked");
        provider.add("message.anvilcraftplasticraft.molding.type_cavity_too_small", "The selected type needs a larger sealed cavity");
        provider.add("message.anvilcraftplasticraft.molding.type_cavity_not_empty", "The selected type requires an empty cavity");
        provider.add("message.anvilcraftplasticraft.molding.type_cavity_ratio_too_small", "The selected type needs at least 50% cavity volume");
        provider.add("message.anvilcraftplasticraft.molding.type_anvil_empty", "An anvil model cannot be empty");
        provider.add("message.anvilcraftplasticraft.molding.type_anvil_height_too_short", "An anvil model needs at least 10 px of height");
        provider.add("message.anvilcraftplasticraft.molding.type_anvil_bottom_too_small", "The anvil bottom needs a flat 12 x 12 px area");
        provider.add("message.anvilcraftplasticraft.molding.type_anvil_bottom_too_thin", "The anvil bottom segment needs at least 3 px of thickness");
        provider.add("message.anvilcraftplasticraft.molding.type_anvil_middle_too_thin", "The anvil middle segment needs at least 3 px of thickness");
        provider.add("message.anvilcraftplasticraft.molding.type_anvil_neck_not_narrow", "The anvil middle segment must be strictly narrower on every side");
        provider.add(
            "message.anvilcraftplasticraft.molding.type_anvil_top_not_wide",
            "The anvil top projection may be at most 16 px squared smaller than its bottom projection"
        );
        provider.add("message.anvilcraftplasticraft.molding.type_anvil_segments_disconnected", "The three anvil segments must connect along Y");
        provider.add("message.anvilcraftplasticraft.molding.type_anvil_segments_invalid", "The anvil segments do not meet the thickness rules");
        provider.add("message.anvilcraftplasticraft.molding.type_anvil_shape_unavailable", "The anvil shape analysis is unavailable");
        provider.add("message.anvilcraftplasticraft.molding.type_cauldron_not_sealed", "A cauldron needs a closed bottom and side walls with an open top");
        provider.add("message.anvilcraftplasticraft.molding.type_cauldron_opening_too_small", "A cauldron needs at least 144 px squared of top opening");
        provider.add("message.anvilcraftplasticraft.molding.type_cauldron_too_shallow", "A cauldron needs at least 8 px of cavity depth");
        provider.add("message.anvilcraftplasticraft.molding.type_cauldron_not_large", "A large cauldron needs more than 1600 px squared of top opening");
        provider.add("message.anvilcraftplasticraft.molding.type_tray_empty", "A tray model needs a solid bottom and top");
        provider.add("message.anvilcraftplasticraft.molding.type_tray_outside_center", "A tray must fit inside the 48 x 48 px modeling area");
        provider.add("message.anvilcraftplasticraft.molding.type_tray_too_tall", "A tray may be at most 4 px tall");
        provider.add("message.anvilcraftplasticraft.molding.type_tray_surface_outside_bounds", "A tray surface may not extend below its bottom or above its top");
        provider.add("message.anvilcraftplasticraft.molding.type_tray_bottom_not_flat", "A tray needs a completely flat volumetric bottom face");
        provider.add("message.anvilcraftplasticraft.molding.type_tray_top_not_flat", "A tray needs a completely flat top face");
        provider.add("message.anvilcraftplasticraft.molding.type_tray_center_missing", "A tray needs a volumetric cube in its 3 x 3 modeling area");
        provider.add("message.anvilcraftplasticraft.molding.type_tray_shape_unavailable", "The tray shape analysis is unavailable");
        provider.add("message.anvilcraftplasticraft.molding.type_unknown_type", "The selected type is unavailable");
        provider.add("message.anvilcraftplasticraft.molding.missing_printing_component", "Install a 3D Printing Component directly above the chamber");
        provider.add("message.anvilcraftplasticraft.molding.not_locked", "Model is not locked");
        provider.add("message.anvilcraftplasticraft.molding.processing", "Processing transaction is active");
        provider.add("message.anvilcraftplasticraft.molding.drain_batch_first", "Drain the forming batch first");
        provider.add("message.anvilcraftplasticraft.molding.invalid_clay_limit", "Clay limit must be 1 to 256");
        provider.add("message.anvilcraftplasticraft.molding.fluid_interaction_failed", "Fluid container interaction failed");
        provider.add("message.anvilcraftplasticraft.molding.invalid_machine_action", "Invalid machine action");
        provider.add("message.anvilcraftplasticraft.molding.reserved_clay", "Return reserved clay first");
        provider.add("message.anvilcraftplasticraft.molding.invalid_model", "Invalid molding model");
        provider.add(
            "message.anvilcraftplasticraft.molding.model_too_complex",
            "The model surface or collision is too complex to manufacture safely"
        );

        // 磁盘事务、共享蓝图库和模型文件导入结果语言文件生成。
        provider.add("message.anvilcraftplasticraft.molding.blueprint_uploaded", "Model imported into the shared library");
        provider.add("message.anvilcraftplasticraft.molding.blueprint_pinned", "Model pinned");
        provider.add("message.anvilcraftplasticraft.molding.blueprint_unpinned", "Model unpinned");
        provider.add("message.anvilcraftplasticraft.molding.blueprint_copied", "Model copied");
        provider.add("message.anvilcraftplasticraft.molding.blueprint_deleted", "Model deleted");
        provider.add("message.anvilcraftplasticraft.molding.blueprint_renamed", "Model renamed");
        provider.add("message.anvilcraftplasticraft.molding.blueprint_name_taken", "A shared model already uses that filename");
        provider.add("message.anvilcraftplasticraft.molding.invalid_blueprint_name", "That model name cannot be used as a filename");
        provider.add("message.anvilcraftplasticraft.molding.blueprint_loaded", "Structure Disk model loaded for editing");
        provider.add("message.anvilcraftplasticraft.molding.blueprint_saved", "Model stored on the Structure Disk and in the shared library");
        provider.add("message.anvilcraftplasticraft.molding.blueprint_disk_written", "Shared model written to the Structure Disk");
        provider.add(
            "message.anvilcraftplasticraft.molding.confirm_blueprint_delete",
            "Hold Shift while clicking delete to confirm"
        );
        provider.add(
            "message.anvilcraftplasticraft.molding.confirm_blueprint_overwrite",
            "Replace %1$s [%2$s] with %3$s [%4$s]? Click the same control again to confirm"
        );
        provider.add("message.anvilcraftplasticraft.molding.open_folder_failed", "Unable to open the model folder; path copied");
        provider.add("message.anvilcraftplasticraft.molding.import_failed", "Unable to import %s");
        provider.add(
            "message.anvilcraftplasticraft.molding.delete_imported_source_failed",
            "Imported %s, but could not delete its source file"
        );
        provider.add(
            "message.anvilcraftplasticraft.molding.import_translation_prompt",
            "%1$s needs translation (%2$s, %3$s, %4$s); refresh again to confirm import"
        );
        provider.add("message.anvilcraftplasticraft.molding.missing_disk", "Place a Structure Disk in the disk slot");
        provider.add("message.anvilcraftplasticraft.molding.stale_disk", "The Structure Disk changed before the operation completed");
        provider.add("message.anvilcraftplasticraft.molding.invalid_disk_blueprint", "The Structure Disk has no valid molding model");
        provider.add(
            "message.anvilcraftplasticraft.molding.confirm_disk_overwrite",
            "Structure Disk contains data; click again to confirm storing"
        );
        provider.add(
            "message.anvilcraftplasticraft.molding.confirm_model_overwrite",
            "A model is being edited; click again to confirm loading"
        );
        provider.add("message.anvilcraftplasticraft.molding.model_too_large", "Model is too large to process");
        provider.add(
            "message.anvilcraftplasticraft.molding.printing_model_too_large",
            "3D printing is limited to x/z 8..40 px and y 1..33 px"
        );
        provider.add(
            "message.anvilcraftplasticraft.molding.printing_output_pending",
            "Waiting for the printed product to leave the discharge opening"
        );
        provider.add("message.anvilcraftplasticraft.molding.disk_write_failed", "Unable to update the Structure Disk");
        provider.add("message.anvilcraftplasticraft.molding.server_unavailable", "Server is unavailable");
        provider.add("message.anvilcraftplasticraft.molding.blueprint_missing", "The shared model no longer exists");
        provider.add("message.anvilcraftplasticraft.molding.stale_file_revision", "The shared model changed; refresh the list");
        provider.add("message.anvilcraftplasticraft.molding.permission_denied", "Only the owner or an administrator may change this model");
        provider.add("message.anvilcraftplasticraft.molding.library_full", "The shared model library is full");
        provider.add("message.anvilcraftplasticraft.molding.library_read_failed", "Unable to read the shared model library");
        provider.add("message.anvilcraftplasticraft.molding.library_directory_failed", "Unable to access the shared model directory");
        provider.add("message.anvilcraftplasticraft.molding.blueprint_read_failed", "Unable to read the shared model");
        provider.add("message.anvilcraftplasticraft.molding.blueprint_write_failed", "Unable to write the shared model");
        provider.add("message.anvilcraftplasticraft.molding.delete_failed", "Unable to delete the shared model");
        provider.add(
            "message.anvilcraftplasticraft.molding.atomic_write_unsupported",
            "The filesystem does not support atomic model writes"
        );
        provider.add("message.anvilcraftplasticraft.molding.preferences_read_failed", "Unable to read pinned-model preferences");
        provider.add("message.anvilcraftplasticraft.molding.preferences_too_large", "Pinned-model preferences exceed 64 KiB");
        provider.add("message.anvilcraftplasticraft.molding.unsafe_filename", "Unsafe model filename");
        provider.add("message.anvilcraftplasticraft.molding.unsafe_path", "Model path escapes its safe directory");
        provider.add("message.anvilcraftplasticraft.molding.unsafe_symlink", "Symbolic links are not allowed in model storage");
        provider.add("message.anvilcraftplasticraft.molding.unsupported_extension", "Only .json and .bbmodel files are supported");
        provider.add("message.anvilcraftplasticraft.molding.file_too_large", "Model file exceeds 1 MiB");
        provider.add("message.anvilcraftplasticraft.molding.json_too_deep", "Model JSON exceeds 64 nesting levels");
        provider.add("message.anvilcraftplasticraft.molding.invalid_json", "Invalid model JSON");
        provider.add("message.anvilcraftplasticraft.molding.invalid_blueprint", "Invalid Plasticraft blueprint");
        provider.add("message.anvilcraftplasticraft.molding.invalid_import", "Invalid model import");
        provider.add("message.anvilcraftplasticraft.molding.invalid_name", "Invalid model name");
        provider.add("message.anvilcraftplasticraft.molding.empty_import", "Imported file contains no supported geometry");
        provider.add("message.anvilcraftplasticraft.molding.unsupported_parent", "External parent model is not supported");
        provider.add("message.anvilcraftplasticraft.molding.unsupported_element", "Model contains an unsupported element");
        provider.add("message.anvilcraftplasticraft.molding.too_many_elements", "Model exceeds 256 elements");
        provider.add("message.anvilcraftplasticraft.molding.too_many_groups", "Model exceeds 128 groups");
        provider.add("message.anvilcraftplasticraft.molding.import_too_large", "Imported bounds exceed 48 units on an axis");
        provider.add("message.anvilcraftplasticraft.molding.import_translation_required", "Model translation requires confirmation");
        provider.add("message.anvilcraftplasticraft.molding.import_translation_invalid", "Confirmed model translation is invalid");

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
        provider.add("tooltip.anvilcraftplasticraft.size", "Size: %1$s x %2$s x %3$s blocks");
        provider.add(
            "tooltip.anvilcraftplasticraft.molding.structure_disk.fit_chamber",
            "✓ Can be processed by Plastic Molding Chamber"
        );
        provider.add(
            "tooltip.anvilcraftplasticraft.molding.structure_disk.too_large_for_chamber",
            "✗ Model is too large to process"
        );
        provider.add("tooltip.anvilcraftplasticraft.bonded", "Bonded in place");

        // Jade 插件配置项及其方块、实体状态文本。
        provider.add("tooltip.anvilcraftplasticraft.jade.color", "Material colour: %s");
        provider.add("tooltip.anvilcraftplasticraft.jade.pushable", "Can be pushed");
        provider.add("tooltip.anvilcraftplasticraft.jade.item_count", "%1$s x %2$s");
        provider.add("tooltip.anvilcraftplasticraft.jade.fluid_count", "%1$s: %2$s mB");
        provider.add("tooltip.anvilcraftplasticraft.jade.empty", "Empty");
        provider.add("tooltip.anvilcraftplasticraft.jade.gas", "%1$s %2$s / %3$s");
        provider.add("tooltip.anvilcraftplasticraft.jade.fluid", "%1$s %2$s / %3$s");
        provider.add("config.jade.plugin_anvilcraftplasticraft.bonded_entity", "Bonded entities");
        provider.add("config.jade.plugin_anvilcraftplasticraft.bonded_block", "Bonded blocks");
        provider.add("config.jade.plugin_anvilcraftplasticraft.hardend_resin_anvil", "Hardened Resin Anvil");
        provider.add("config.jade.plugin_anvilcraftplasticraft.fluid_storage", "Fluid Storage");
        provider.add(
            "config.jade.plugin_anvilcraftplasticraft.plastic_molding_chamber",
            "Plastic Molding Chamber"
        );
        provider.add("config.jade.plugin_anvilcraft.fluid_tank", "Fluid Tank");

        // JEI 配方分类、虚拟气体名称和蒸发速率说明。
        provider.add("gui.anvilcraftplasticraft.category.plasma_jet_blasting", "Plasma Jet Blasting");
        provider.add("gui.anvilcraftplasticraft.category.condenser", "Condensation");
        // JEI 开放催化页面：四种塑料熔体的催化剂、环境要求与非消耗说明。
        provider.add("gui.anvilcraftplasticraft.category.plastic_melt_catalysis", "Plastic Melt Catalysis");
        provider.add("jei.anvilcraftplasticraft.plastic_melt_catalysis.catalyst", "Catalyst efficiency:\n%s%%");
        provider.add("jei.anvilcraftplasticraft.plastic_melt_catalysis.not_consumed", "Catalysts are not consumed");
        provider.add("jei.anvilcraftplasticraft.plastic_melt_catalysis.heat", "Requires heat directly below");
        provider.add("jei.anvilcraftplasticraft.plastic_melt_catalysis.heat_power", "Heat: %s");
        provider.add("jei.anvilcraftplasticraft.plastic_melt_catalysis.cold", "Requires cold directly below");
        provider.add("jei.anvilcraftplasticraft.plastic_melt_catalysis.none", "No heat or cold required");
        provider.add("jei.anvilcraftplasticraft.plastic_melt_catalysis.details", "Open catalysis (hover for details)");
        provider.add(
            "jei.anvilcraftplasticraft.plastic_melt_catalysis.containers",
            "Drop catalysts into a fluid source, cauldron, fish tank, large cauldron or upward-facing plastic cauldron"
        );
        provider.add(
            "jei.anvilcraftplasticraft.plastic_melt_catalysis.preserved",
            "Converts the stored fluid or a whole large-cauldron layer, preserving amount and color; 1000 mB is an example"
        );
        provider.add(
            "jei.anvilcraftplasticraft.plastic_melt_catalysis.variety",
            "More distinct catalyst items increase speed with diminishing returns; larger stacks do not"
        );
        provider.add(
            "jei.anvilcraftplasticraft.plastic_melt_catalysis.glass_priority",
            "Royal or frost glass takes priority over metal catalysts, producing clear plastic melt"
        );
        provider.add(
            "jei.anvilcraftplasticraft.plastic_melt_catalysis.ember_priority",
            "Ember metal with heat takes priority over engineering plastic, producing heat-resistant plastic melt"
        );
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
