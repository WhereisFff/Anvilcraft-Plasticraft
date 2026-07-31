package dev.anvilcraft.plasticraft.data;

/** 汇总各类数据生成器的注册入口，不在此声明具体数据。 */
public final class PlasticraftDatagen {
    private PlasticraftDatagen() {
    }

    public static void init() {
        PlasticraftLanguageData.register();
        PlasticraftRecipeData.register();
        PlasticraftTagData.register();
    }
}
