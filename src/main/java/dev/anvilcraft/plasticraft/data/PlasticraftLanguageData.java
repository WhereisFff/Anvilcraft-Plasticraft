package dev.anvilcraft.plasticraft.data;

import dev.anvilcraft.lib.v2.registrum.providers.ProviderType;
import dev.anvilcraft.lib.v2.registrum.providers.RegistrumLangProvider;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.api.tooltip.PlasticItemTooltipManager;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItemGroups;

/** 生成物品名、界面文本、提示文本和可选集成所需的英文语言数据。 */
public final class PlasticraftLanguageData {
    private PlasticraftLanguageData() {
    }

    public static void register() {
        AnvilcraftPlasticraft.REGISTRUM.addDataGenerator(ProviderType.LANG, PlasticraftLanguageData::generate);
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

        // 物品组、塑料材料名称及无法由 Registrum 条目自动生成的物品名称。
        provider.add(PlasticraftItemGroups.TITLE_KEY, "Anvilcraft: Plasticraft");
        provider.add("item.anvilcraftplasticraft.hardend_resin_anvil", "Hardened Resin Anvil");
        provider.add("item.anvilcraftplasticraft.hardend_resin_cauldron", "Hardened Resin Cauldron");
        provider.add("item.anvilcraftplasticraft.resin_anvil", "Resin Anvil");
        provider.add("item.anvilcraftplasticraft.universal_plastic", "Universal Plastic Block");
        provider.add("material.anvilcraftplasticraft.universal_plastic", "Universal Plastic");
        provider.add("item.anvilcraftplasticraft.molded_product_name", "%1$s %2$s");
        provider.add("item.anvilcraftplasticraft.molded_product_suffix.block", "Block");

        // 塑料成型舱物品说明、容器标题和建模器界面语言文件生成。
        provider.add(
            "tooltip.anvilcraftplasticraft.plastic_molding_chamber",
            "Collects nearby clay balls and casts blueprint models with a Giant Anvil, or prints them with the component above"
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
        provider.add("screen.anvilcraftplasticraft.molding.type.normal", "Normal");
        provider.add("screen.anvilcraftplasticraft.molding.type.chest", "Chest");
        provider.add("screen.anvilcraftplasticraft.molding.type.tank", "Tank");
        provider.add("screen.anvilcraftplasticraft.molding.type.anvil", "Anvil");
        provider.add("screen.anvilcraftplasticraft.molding.type.tray", "Tray");
        provider.add("screen.anvilcraftplasticraft.molding.type.propeller", "Propeller");
        provider.add("screen.anvilcraftplasticraft.molding.type_invalid", "This shape does not seem able to do that");
        provider.add("screen.anvilcraftplasticraft.molding.model_too_large", "Model is too large to process");
        provider.add("screen.anvilcraftplasticraft.molding.type_override_active", "Type limit bypass active");
        provider.add("screen.anvilcraftplasticraft.molding.creative_override_active", "Debug override active");
        provider.add("screen.anvilcraftplasticraft.molding.undo", "Undo");
        provider.add("screen.anvilcraftplasticraft.molding.copy", "Copy");
        provider.add("screen.anvilcraftplasticraft.molding.cut", "Cut");
        provider.add("screen.anvilcraftplasticraft.molding.paste", "Paste");
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
        provider.add(
            "jei.anvilcraftplasticraft.plastic_molding_chamber.structure",
            "Casting uses a 3x3 platform directly above the forming region: nine Crafting Tables produce an "
                + "entity, while a center Space Overcompressor produces an item. Printing only needs a 3D "
                + "Printing Component directly above the chamber; it uses neither that platform nor a Giant Anvil."
        );
        provider.add(
            "jei.anvilcraftplasticraft.plastic_molding_chamber.forming",
            "Casting fills a clay mold and pumps at 250 mB/gt. For 3D printing, the chamber pumps the complete "
                + "model batch into the component above it at 250 mB/gt. Once full, the component automatically "
                + "consumes 1 mB/gt and reveals the exact model in Y-layer, Z-row, then -X to +X order."
        );
        provider.add(
            "jei.anvilcraftplasticraft.plastic_molding_chamber.outputs",
            "Only the forming batch is consumed. Casting returns every clay ball used by its mold. Printing "
                + "reserves no clay and directly creates a plastic entity in the forming region when complete."
        );
        provider.add(
            "jei.anvilcraftplasticraft.plastic_molding_chamber.modes",
            "Continuous starts another cycle after the region clears; Redstone starts one cycle per rising edge; "
                + "Single returns to editing after one cycle and ignores redstone. These production modes are "
                + "independent of the forming method: casting still needs a Giant Anvil strike, while printing "
                + "starts automatically when its batch is full."
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
        provider.add("tooltip.anvilcraftplasticraft.molded_chest", "A plastic chest that can store %s stacks of items");
        provider.add("tooltip.anvilcraftplasticraft.molded_tank", "A plastic tank that can store %s B of fluid");
        provider.add("tooltip.anvilcraftplasticraft.molded_anvil", "A plastic anvil that can process falling-anvil recipes");
        provider.add(
            "tooltip.anvilcraftplasticraft.molded_giant_anvil",
            "A giant plastic anvil for falling-anvil recipes, multiblock processing, ground shocks, and large cauldrons"
        );
        provider.add(
            "tooltip.anvilcraftplasticraft.molded_tray",
            "A plastic tray that carries up to nine compatible redstone components with their native behavior and lighting"
        );
        provider.add("tooltip.anvilcraftplasticraft.molded_chest_contents", "Occupied slots: %1$s/%2$s");
        provider.add("tooltip.anvilcraftplasticraft.molded_tank_contents", "Stored fluid: %1$s mB/%2$s B");
        provider.add("tooltip.anvilcraftplasticraft.molded_more_contents", "%s more content types");

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
        provider.add("message.anvilcraftplasticraft.molding.type_anvil_bottom_too_small", "The anvil bottom needs a flat 14 x 14 px area");
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
        provider.add("message.anvilcraftplasticraft.molding.type_tray_empty", "A tray model needs a solid bottom and top");
        provider.add("message.anvilcraftplasticraft.molding.type_tray_outside_center", "A tray must fit inside the 48 x 48 px modeling area");
        provider.add("message.anvilcraftplasticraft.molding.type_tray_too_tall", "A tray may be at most 4 px tall");
        provider.add("message.anvilcraftplasticraft.molding.type_tray_surface_outside_bounds", "A tray surface may not extend below its bottom or above its top");
        provider.add("message.anvilcraftplasticraft.molding.type_tray_bottom_not_flat", "A tray needs a completely flat volumetric bottom face");
        provider.add("message.anvilcraftplasticraft.molding.type_tray_top_not_flat", "A tray needs a completely flat top face");
        provider.add("message.anvilcraftplasticraft.molding.type_tray_center_missing", "A tray needs a volumetric cube in its 3 x 3 modeling area");
        provider.add("message.anvilcraftplasticraft.molding.type_tray_shape_unavailable", "The tray shape analysis is unavailable");
        provider.add("message.anvilcraftplasticraft.molding.type_propeller_model_required", "Propeller validation requires the complete editable model");
        provider.add("message.anvilcraftplasticraft.molding.type_propeller_empty", "A propeller model cannot be empty");
        provider.add("message.anvilcraftplasticraft.molding.type_propeller_too_small", "A propeller must span at least 8 px along one horizontal axis");
        provider.add("message.anvilcraftplasticraft.molding.type_propeller_outside_center", "A propeller must fit inside the central 16 x 16 px area");
        provider.add("message.anvilcraftplasticraft.molding.type_propeller_too_thick", "A propeller may be at most 2 px thick");
        provider.add("message.anvilcraftplasticraft.molding.type_propeller_center_missing", "A propeller needs a solid 2 x 2 px center");
        provider.add("message.anvilcraftplasticraft.molding.type_propeller_branches_missing", "A propeller needs at least two separated branches of 2 px or longer");
        provider.add("message.anvilcraftplasticraft.molding.type_propeller_branches_touching", "A propeller needs open gaps between its branches");
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
        provider.add("config.jade.plugin_anvilcraftplasticraft.condenser_tower", "Condenser Tower");
        provider.add(
            "config.jade.plugin_anvilcraftplasticraft.plastic_molding_chamber",
            "Plastic Molding Chamber"
        );
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
