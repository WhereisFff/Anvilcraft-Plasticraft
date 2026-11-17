package dev.anvilcraft.plasticraft.gametest;

import net.minecraft.gametest.framework.GameTestException;
import net.minecraft.network.chat.Component;

/** 共享玩法断言通过游戏测试框架报告失败时保留原始描述。 */
final class PlasticraftTestFailure extends GameTestException {
    PlasticraftTestFailure(String message) {
        super(message);
    }

    @Override
    public Component getDescription() {
        return Component.literal(this.getMessage());
    }
}
