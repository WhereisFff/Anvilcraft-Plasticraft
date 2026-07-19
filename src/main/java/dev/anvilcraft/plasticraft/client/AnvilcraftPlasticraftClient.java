package dev.anvilcraft.plasticraft.client;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** 为今后注册支持调色板的材料而保留的客户端入口点。 */
@Mod(value = AnvilcraftPlasticraft.MOD_ID, dist = Dist.CLIENT)
public final class AnvilcraftPlasticraftClient {
    public AnvilcraftPlasticraftClient(IEventBus ignoredModBus, ModContainer ignoredContainer) {
        // 硬化树脂和树脂均为固定颜色材料，不注册方块或物品着色处理器。
        // 后续支持调色板的材料在此按需注册。
    }
}
