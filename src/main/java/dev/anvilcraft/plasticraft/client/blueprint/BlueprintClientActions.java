package dev.anvilcraft.plasticraft.client.blueprint;

import dev.anvilcraft.plasticraft.client.gui.screen.BlueprintImportScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/** 公共事件层进入客户端蓝图逻辑的唯一入口,保证服务端不加载客户端类。 */
public final class BlueprintClientActions {
    private BlueprintClientActions() {
    }

    public static void openImportScreen() {
        Minecraft.getInstance().setScreen(new BlueprintImportScreen());
    }

    /** 会话未激活时进入部署会话;已激活时执行当前选中的工具。 */
    public static void toggleDeploySession(Player player, InteractionHand hand) {
        if (BlueprintDeploySession.isActive()) {
            BlueprintDeploySession.executeSelectedTool();
        } else {
            BlueprintDeploySession.begin(player, hand);
        }
    }

    public static boolean isSessionActive() {
        return BlueprintDeploySession.isActive();
    }

    public static void executeSessionTool() {
        BlueprintDeploySession.executeSelectedTool();
    }
}
