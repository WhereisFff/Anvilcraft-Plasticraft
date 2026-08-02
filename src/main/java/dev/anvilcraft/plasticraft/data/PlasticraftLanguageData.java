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

        // 物品组及无法由 Registrum 条目自动生成的物品名称。
        provider.add(PlasticraftItemGroups.TITLE_KEY, "Anvilcraft: Plasticraft");
        provider.add("item.anvilcraftplasticraft.hardend_resin_anvil", "Hardened Resin Anvil");
        provider.add("item.anvilcraftplasticraft.hardend_resin_cauldron", "Hardened Resin Cauldron");
        provider.add("item.anvilcraftplasticraft.resin_anvil", "Resin Anvil");
        provider.add("item.anvilcraftplasticraft.universal_plastic", "Universal Plastic Block");

        // 塑料成型舱物品说明、容器标题和建模器界面语言文件生成。
        provider.add(
            "tooltip.anvilcraftplasticraft.plastic_molding_chamber",
            "Forms blueprints with clay molds and plastic melt using power, pipes, logistics, and redstone automation"
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
        provider.add("screen.anvilcraftplasticraft.molding.cut", "Cut");
        provider.add("screen.anvilcraftplasticraft.molding.paste", "Paste");
        provider.add("screen.anvilcraftplasticraft.molding.json", "JSON management");
        provider.add("screen.anvilcraftplasticraft.molding.takeover", "Take over editing");
        provider.add("screen.anvilcraftplasticraft.molding.element.tooltip", "%1$s - %2$s");
        provider.add("screen.anvilcraftplasticraft.molding.element.cube", "Cube");
        provider.add("screen.anvilcraftplasticraft.molding.element.group", "Group");
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
        provider.add("screen.anvilcraftplasticraft.molding.disk.missing", "Place an AnvilCraft disk in the disk slot");
        provider.add("screen.anvilcraftplasticraft.molding.disk.load", "Load the disk blueprint into the editable chamber");
        provider.add("screen.anvilcraftplasticraft.molding.disk.invalid", "This disk has no valid molding blueprint");
        provider.add("screen.anvilcraftplasticraft.molding.disk.store", "Store the current model on disk and in the shared library");
        provider.add("screen.anvilcraftplasticraft.molding.disk.other_data", "Other disk data");
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
            "Hold Shift and click again to delete the selected model"
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
        provider.add("screen.anvilcraftplasticraft.molding.clay_lock_status", "Clay: in slot %1$s / required %2$s");
        provider.add("screen.anvilcraftplasticraft.molding.lock", "Lock and start");
        provider.add("screen.anvilcraftplasticraft.molding.lock_confirm", "Click again to confirm locking");
        provider.add("screen.anvilcraftplasticraft.molding.unlock", "Unlock and return reserved clay");
        provider.add("screen.anvilcraftplasticraft.molding.mode.continuous", "Continuous processing");
        provider.add("screen.anvilcraftplasticraft.molding.mode.redstone", "Redstone control");
        provider.add("screen.anvilcraftplasticraft.molding.mode.single", "Single processing");
        provider.add("screen.anvilcraftplasticraft.molding.fluid_staging", "Staging: %1$s / %2$s mB");
        provider.add("screen.anvilcraftplasticraft.molding.fluid_batch", "Batch: %1$s / %2$s mB");
        provider.add("screen.anvilcraftplasticraft.molding.pump_rate", "Pump rate: %s mB/gt");
        provider.add("screen.anvilcraftplasticraft.molding.energy", "Energy: %1$s / %2$s FE");
        provider.add("screen.anvilcraftplasticraft.molding.rated_power", "Working level: %s kW");
        provider.add("screen.anvilcraftplasticraft.molding.wait.none", "Standing by");
        provider.add("screen.anvilcraftplasticraft.molding.wait.invalid_model", "Invalid or empty model");
        provider.add("screen.anvilcraftplasticraft.molding.wait.structure_incomplete", "Chamber structure incomplete");
        provider.add("screen.anvilcraftplasticraft.molding.wait.region_blocked", "Forming region blocked");
        provider.add("screen.anvilcraftplasticraft.molding.wait.missing_clay", "Waiting for clay balls");
        provider.add("screen.anvilcraftplasticraft.molding.wait.missing_power", "Waiting for 256 kW power");
        provider.add("screen.anvilcraftplasticraft.molding.wait.mold_filling", "Filling clay mold");
        provider.add("screen.anvilcraftplasticraft.molding.wait.mold_ready", "Waiting for plastic melt");
        provider.add("screen.anvilcraftplasticraft.molding.wait.pumping", "Pumping at 2 B/gt");
        provider.add("screen.anvilcraftplasticraft.molding.wait.batch_full", "Batch capacity reached");
        provider.add("screen.anvilcraftplasticraft.molding.wait.process_ready", "Ready to process");
        provider.add("screen.anvilcraftplasticraft.molding.wait.processing", "Processing transaction active");
        provider.add("screen.anvilcraftplasticraft.molding.wait.waiting_for_clear_region", "Waiting for region to clear");

        provider.add("tooltip.anvilcraftplasticraft.molding.state", "State: %s");
        provider.add("tooltip.anvilcraftplasticraft.molding.energy", "%1$s / %2$s FE, %3$s kW");
        provider.add("tooltip.anvilcraftplasticraft.molding.fluids", "Staging %1$s / %2$s, batch %3$s / %4$s");
        provider.add("tooltip.anvilcraftplasticraft.molding.clay", "Clay slot %1$s, molded %2$s / %3$s");

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
        provider.add("message.anvilcraftplasticraft.molding.not_locked", "Model is not locked");
        provider.add("message.anvilcraftplasticraft.molding.processing", "Processing transaction is active");
        provider.add("message.anvilcraftplasticraft.molding.drain_batch_first", "Drain the forming batch first");
        provider.add("message.anvilcraftplasticraft.molding.invalid_clay_limit", "Clay limit must be 1 to 256");
        provider.add("message.anvilcraftplasticraft.molding.fluid_interaction_failed", "Fluid container interaction failed");
        provider.add("message.anvilcraftplasticraft.molding.invalid_machine_action", "Invalid machine action");
        provider.add("message.anvilcraftplasticraft.molding.reserved_clay", "Return reserved clay first");
        provider.add("message.anvilcraftplasticraft.molding.invalid_model", "Invalid molding model");

        // 磁盘事务、共享蓝图库和模型文件导入结果语言文件生成。
        provider.add("message.anvilcraftplasticraft.molding.blueprint_uploaded", "Model imported into the shared library");
        provider.add("message.anvilcraftplasticraft.molding.blueprint_pinned", "Model pinned");
        provider.add("message.anvilcraftplasticraft.molding.blueprint_unpinned", "Model unpinned");
        provider.add("message.anvilcraftplasticraft.molding.blueprint_copied", "Model copied");
        provider.add("message.anvilcraftplasticraft.molding.blueprint_deleted", "Model deleted");
        provider.add("message.anvilcraftplasticraft.molding.blueprint_loaded", "Disk blueprint loaded for editing");
        provider.add("message.anvilcraftplasticraft.molding.blueprint_saved", "Blueprint stored on disk and in the shared library");
        provider.add("message.anvilcraftplasticraft.molding.blueprint_disk_written", "Shared blueprint written to disk");
        provider.add(
            "message.anvilcraftplasticraft.molding.confirm_blueprint_delete",
            "Hold Shift and click delete again to confirm"
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
        provider.add("message.anvilcraftplasticraft.molding.missing_disk", "Place an AnvilCraft disk in the disk slot");
        provider.add("message.anvilcraftplasticraft.molding.stale_disk", "The disk changed before the operation completed");
        provider.add("message.anvilcraftplasticraft.molding.invalid_disk_blueprint", "The disk has no valid molding blueprint");
        provider.add(
            "message.anvilcraftplasticraft.molding.confirm_disk_overwrite",
            "Disk contains data; click again to confirm storing"
        );
        provider.add(
            "message.anvilcraftplasticraft.molding.confirm_model_overwrite",
            "A model is being edited; click again to confirm loading"
        );
        provider.add("message.anvilcraftplasticraft.molding.disk_write_failed", "Unable to update the disk");
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
