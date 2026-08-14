package dev.anvilcraft.plasticraft.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * 施工世界权限窄接口。本阶段恒为允许;领地与 FTB Teams 复核留给后续 TODO,
 * 但拒绝时任务必须进入 WAITING_PERMISSION,不能被跳过策略绕过。
 */
public final class ConstructionPermission {
    private ConstructionPermission() {
    }

    public static boolean canModify(ServerLevel level, BlockPos pos, UUID owner) {
        return true;
    }
}
