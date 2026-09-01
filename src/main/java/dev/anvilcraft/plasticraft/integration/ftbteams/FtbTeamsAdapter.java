package dev.anvilcraft.plasticraft.integration.ftbteams;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamManager;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

import java.util.UUID;

/**
 * FTB Teams 的可选运行时适配。只有嵌套实现会解析可选 API，未安装时保持严格所有者权限。
 */
public final class FtbTeamsAdapter {
    private static volatile boolean reportedFailure;

    private FtbTeamsAdapter() {
    }

    public static boolean arePlayersInSameTeam(MinecraftServer server, UUID first, UUID second) {
        if (first.equals(second) || !ModList.get().isLoaded("ftbteams")) return first.equals(second);
        try {
            return LoadedApi.arePlayersInSameTeam(server, first, second);
        } catch (LinkageError | RuntimeException exception) {
            if (!reportedFailure) {
                reportedFailure = true;
                AnvilcraftPlasticraft.LOGGER.debug("FTB Teams permission lookup failed", exception);
            }
            return false;
        }
    }

    /** 仅在 FTB Teams 已加载时解析嵌套类，主模组缺少可选依赖仍可正常启动。 */
    private static final class LoadedApi {
        private static boolean arePlayersInSameTeam(MinecraftServer server, UUID first, UUID second) {
            FTBTeamsAPI.API api = FTBTeamsAPI.api();
            if (api == null || !api.isManagerLoaded()) return false;
            TeamManager manager = api.getManager();
            return manager != null
                && manager.getServer() == server
                && manager.arePlayersInSameTeam(first, second);
        }
    }
}
