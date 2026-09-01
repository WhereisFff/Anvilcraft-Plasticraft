package dev.anvilcraft.plasticraft.molding.type;

import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.MoldingFunctionalAnalysis;
import dev.anvilcraft.plasticraft.molding.bake.MoldingVolumeMask;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import net.minecraft.resources.ResourceLocation;

/** 可扩展的成型制品功能类型策略。 */
public interface MoldingProductType {
    enum StorageKind {
        NONE,
        ITEMS,
        FLUIDS,
        TRAY
    }

    ResourceLocation id();

    String translationKey();

    /** 成品名称使用的类型后缀；除普通类型外默认复用类型选择名称。 */
    default String productNameSuffixKey() {
        return this.translationKey();
    }

    MoldingTypeValidation validate(MoldingFunctionalAnalysis analysis);

    default MoldingTypeValidation validate(EditableMoldingModel model, BakedMoldingModel baked) {
        return this.validate(baked.functionalAnalysis());
    }

    default boolean requiresPrinting() {
        return false;
    }

    /** 类型声明的无菜单存储能力；未定义类型默认不暴露猜测性能力。 */
    default StorageKind storageKind() {
        return StorageKind.NONE;
    }

    /** 每个容量单位所需的内腔体素数；非存储类型返回 0。 */
    default int unitsPerCapacity() {
        return 0;
    }

    /** 是否在类型选择面板中显示；大型炼药锅由普通炼药锅自动升级得到，不允许玩家直接选择。 */
    default boolean selectable() {
        return true;
    }

    /** 由内腔体素数派生容量；炼药锅使用 max(1, floor(v/1728)) 规则，在此重写。 */
    default int capacityFor(int cavityVolume) {
        int units = this.unitsPerCapacity();
        return units == 0 ? 0 : cavityVolume / units;
    }

    /** 校验已反序列化的容量值与内腔掩码是否自洽；写入后读回的值必须通过此检查。 */
    default boolean validateCapacity(int capacity, MoldingVolumeMask cavityMask) {
        return this.unitsPerCapacity() == 0 || capacity == this.capacityFor(cavityMask.volume());
    }
}
