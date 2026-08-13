package dev.anvilcraft.plasticraft.event;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.BlueprintJobSync;
import dev.anvilcraft.plasticraft.blueprint.BlueprintSource;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintData;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintException;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintService;
import dev.anvilcraft.plasticraft.blueprint.ScannerDiskImporter;
import dev.anvilcraft.plasticraft.client.blueprint.BlueprintClientActions;
import dev.anvilcraft.plasticraft.network.BlueprintImportResultPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Optional;

/**
 * 结构磁盘的世界交互入口:右击已保存的原版结构方块复制其模板,
 * 右击空气把扫描器磁盘就地转换为蓝图引用,潜行右击打开客户端文件导入界面;
 * 登录时全量同步任务索引。磁盘物品属于本体,不能覆写其物品类,统一经事件拦截。
 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID)
public final class ConstructionBlueprintEvents {
    private ConstructionBlueprintEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BlueprintJobSync.syncAllTo(player);
        }
    }

    /** 手持结构磁盘右击结构方块:读取其已保存模板,导入世界结构库并写盘。 */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!ConstructionBlueprintService.isStructureDisk(held)) return;
        // 会话进行中右击方块同样执行当前工具,阻止交互包发往服务端。
        if (event.getLevel().isClientSide && BlueprintClientActions.isSessionActive()) {
            event.setCanceled(true);
            BlueprintClientActions.executeSessionTool();
            return;
        }
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof StructureBlockEntity structureBlock)) {
            return;
        }
        event.setCanceled(true);
        if (!(event.getEntity() instanceof ServerPlayer player)
            || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        try {
            String structureName = structureBlock.getStructureName();
            ResourceLocation templateId = structureName.isEmpty()
                ? null
                : ResourceLocation.tryParse(structureName);
            if (templateId == null) {
                throw new ConstructionBlueprintException("template_missing", structureName);
            }
            Optional<StructureTemplate> template = level.getStructureManager().get(templateId);
            if (template.isEmpty()) {
                throw new ConstructionBlueprintException("template_missing", templateId.toString());
            }
            CompoundTag tag = template.get().save(new CompoundTag());
            ConstructionBlueprintService.ImportResult result = ConstructionBlueprintService.importIntoDisk(
                player.server,
                held,
                tag,
                templateId.getPath(),
                BlueprintSource.VANILLA_TEMPLATE
            );
            BlueprintImportResultPacket.sendSuccess(player, "imported", result.data().name(), result.warnings());
        } catch (ConstructionBlueprintException exception) {
            BlueprintImportResultPacket.sendFailure(player, exception);
        }
    }

    /**
     * 手持结构磁盘右击:潜行时在客户端打开文件导入界面;
     * 磁盘带扫描器数据且尚未导入时在服务端就地转换为蓝图引用。
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack held = event.getItemStack();
        if (!ConstructionBlueprintService.isStructureDisk(held)) return;

        if (event.getEntity().isShiftKeyDown()) {
            event.setCanceled(true);
            if (event.getLevel().isClientSide) {
                BlueprintClientActions.openImportScreen();
            }
            return;
        }

        boolean imported = ConstructionBlueprintData.get(held).isPresent();
        if (!imported && ScannerDiskImporter.hasScannerData(held)) {
            event.setCanceled(true);
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            try {
                ConstructionBlueprintService.ImportResult result = ConstructionBlueprintService.importSnapshot(
                    player.server,
                    held,
                    ScannerDiskImporter.read(player.server, held),
                    ScannerDiskImporter.scannerName(held).orElse("scanned_structure"),
                    BlueprintSource.SCANNER_DISK
                );
                BlueprintImportResultPacket.sendSuccess(
                    player,
                    "imported",
                    result.data().name(),
                    result.warnings()
                );
            } catch (ConstructionBlueprintException exception) {
                BlueprintImportResultPacket.sendFailure(player, exception);
            }
            return;
        }

        if (imported) {
            event.setCanceled(true);
            if (event.getLevel().isClientSide) {
                BlueprintClientActions.toggleDeploySession(event.getEntity(), event.getHand());
            }
        }
    }
}
