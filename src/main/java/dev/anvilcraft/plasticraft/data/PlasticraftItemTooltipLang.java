package dev.anvilcraft.plasticraft.data;

import dev.anvilcraft.lib.v2.registrum.providers.RegistrumLangProvider;
import dev.anvilcraft.plasticraft.api.tooltip.PlasticItemTooltipManager;

/** 生成运行时物品工具提示注册表所声明的翻译。 */
public final class PlasticraftItemTooltipLang {
    private PlasticraftItemTooltipLang() {
    }

    public static void init(RegistrumLangProvider provider) {
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
    }
}
