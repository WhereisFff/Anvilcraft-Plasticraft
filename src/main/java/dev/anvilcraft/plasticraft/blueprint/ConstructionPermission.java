package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.integration.ftbteams.FtbTeamsAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 施工资源与世界写入权限的唯一入口。团队关系每次调用实时查询，不把授权写入悦灵或任务 NBT。
 * 未安装 FTB Teams 时严格退回 UUID 所有者；世界权限 provider 为领地模组和 GameTest 留出窄替换边界。
 */
public final class ConstructionPermission {
    @FunctionalInterface
    public interface CollaboratorProvider {
        boolean areCollaborators(MinecraftServer server, UUID first, UUID second);
    }

    @FunctionalInterface
    public interface WorldPermissionProvider {
        boolean canModify(ServerLevel level, BlockPos pos, UUID owner);
    }

    private static final CollaboratorProvider DEFAULT_COLLABORATORS = FtbTeamsAdapter::arePlayersInSameTeam;
    private static final WorldPermissionProvider DEFAULT_WORLD_PERMISSION = ConstructionPermission::defaultCanModify;
    private static volatile CollaboratorProvider collaboratorProvider = DEFAULT_COLLABORATORS;
    private static volatile WorldPermissionProvider worldPermissionProvider = DEFAULT_WORLD_PERMISSION;

    private ConstructionPermission() {
    }

    /** 供 GameTest 或未来领地集成替换实时团队关系；传入 null 恢复默认适配器。 */
    public static void setCollaboratorProvider(@Nullable CollaboratorProvider provider) {
        collaboratorProvider = provider == null ? DEFAULT_COLLABORATORS : provider;
    }

    /** 供 GameTest 或未来领地集成替换世界写入判定；传入 null 恢复原版玩家判定。 */
    public static void setWorldPermissionProvider(@Nullable WorldPermissionProvider provider) {
        worldPermissionProvider = provider == null ? DEFAULT_WORLD_PERMISSION : provider;
    }

    public static boolean areCollaborators(MinecraftServer server, @Nullable UUID first, @Nullable UUID second) {
        if (first == null || second == null) return false;
        if (first.equals(second)) return true;
        try {
            return collaboratorProvider.areCollaborators(server, first, second);
        } catch (RuntimeException exception) {
            // 可选集成失败必须收紧为 owner-only，不能把异常当成授权。
            return false;
        }
    }

    public static boolean canManageJob(ServerPlayer actor, ConstructionJob job) {
        return areCollaborators(actor.server, actor.getUUID(), job.owner());
    }

    public static boolean canManageLounge(ServerPlayer actor, AllayLoungeBlockEntity lounge) {
        UUID owner = lounge.owner();
        return owner != null && areCollaborators(actor.server, actor.getUUID(), owner);
    }

    /** 玩家入口同时复核团队关系和协调站所在位置的当前世界权限。 */
    public static boolean canUseLounge(ServerPlayer actor, AllayLoungeBlockEntity lounge) {
        if (!canManageLounge(actor, lounge)
            || !(lounge.getLevel() instanceof ServerLevel level)
            || lounge.owner() == null) {
            return false;
        }
        return canPlayerModify(actor, lounge.getBlockPos())
            && canModify(level, lounge.getBlockPos(), lounge.owner());
    }

    /** 为自由收集卸货选择当前维度中仍与工人所有者协作的在线玩家。 */
    @Nullable
    public static ServerPlayer findOnlineCollaborator(ServerLevel level, UUID owner) {
        ServerPlayer exact = level.getServer().getPlayerList().getPlayer(owner);
        if (exact != null && exact.level().dimension().equals(level.dimension())) return exact;
        for (ServerPlayer player : level.players()) {
            if (areCollaborators(level.getServer(), owner, player.getUUID())) return player;
        }
        return null;
    }

    public static boolean canManageWorker(MinecraftServer server, UUID workerOwner, UUID resourceOwner) {
        return areCollaborators(server, workerOwner, resourceOwner);
    }

    public static boolean canModify(ServerLevel level, BlockPos pos, UUID owner) {
        if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        try {
            return worldPermissionProvider.canModify(level, pos, owner);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** 直接由玩家发起的世界交互检查，供休息室破坏等非任务路径使用。 */
    public static boolean canPlayerModify(ServerPlayer player, BlockPos pos) {
        return player.getAbilities().mayBuild
            && player.level() instanceof ServerLevel level
            && level.isInWorldBounds(pos)
            && level.getWorldBorder().isWithinBounds(pos)
            && level.mayInteract(player, pos)
            && canModify(level, pos, player.getUUID());
    }

    private static boolean defaultCanModify(ServerLevel level, BlockPos pos, UUID owner) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        // 原版权限无法按 UUID 判断离线或其他维度的玩家，领地集成可在 provider 中补充该能力。
        if (player == null || !player.level().dimension().equals(level.dimension())) return true;
        return player.getAbilities().mayBuild && level.mayInteract(player, pos);
    }
}
