package dev.anvilcraft.plasticraft.molding.product;

import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.dubhe.anvilcraft.AnvilCraft;
import dev.dubhe.anvilcraft.api.fluid.LargeCauldronFluidHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 炼药锅两种形态的槽位与流体分层布局。
 *
 * <p>物品能力与实体运行时共用同一份布局，保证同一制品在实体形态与物品形态下槽号一致。
 * 槽序沿用硬化树脂炼药锅的「先输出后输入」拼接方式。大型锅的数值逐项对齐 AnvilCraft
 * 本体的 {@code LargeCauldronBlockEntity} / {@code LargeCauldronInputHandler} /
 * {@code LargeCauldronFluidHandler}。
 */
public enum PlasticCauldronLayout {
    /** 硬化树脂炼药锅同规格：输出 8 槽、输入 8 槽、单次落砧一次配方。 */
    NORMAL(8, 8, 1, 1, 1),
    /** 对齐本体大型炼药锅，输入堆叠倍率、分层容量与加工上限在运行时读取配置。 */
    LARGE(32, 8, 9, 8, 9);

    /** 物品未知时的槽位基准上限，与本体 {@code LargeCauldronInputHandler.getSlotLimit} 的空槽取值一致。 */
    private static final int DEFAULT_STACK_LIMIT = 64;

    private final int outputSlots;
    private final int inputSlots;
    private final int inputStackMultiplier;
    private final int fluidLayers;
    private final int recipePasses;

    PlasticCauldronLayout(
        int outputSlots,
        int inputSlots,
        int inputStackMultiplier,
        int fluidLayers,
        int recipePasses
    ) {
        this.outputSlots = outputSlots;
        this.inputSlots = inputSlots;
        this.inputStackMultiplier = inputStackMultiplier;
        this.fluidLayers = fluidLayers;
        this.recipePasses = recipePasses;
    }

    /** 非炼药锅类型返回 {@code null}，调用方据此区分储罐与锅。 */
    public static PlasticCauldronLayout of(ResourceLocation finalType) {
        if (MoldingProductTypes.isLargeCauldron(finalType)) return LARGE;
        return MoldingProductTypes.isCauldron(finalType) ? NORMAL : null;
    }

    public int outputSlots() {
        return this.outputSlots;
    }

    public int inputSlots() {
        return this.inputSlots;
    }

    public int totalSlots() {
        return this.outputSlots + this.inputSlots;
    }

    public boolean isOutputSlot(int slot) {
        return slot >= 0 && slot < this.outputSlots;
    }

    public int inputIndex(int slot) {
        return slot - this.outputSlots;
    }

    public int slotLimit(int slot) {
        return this.slotLimit(slot, ItemStack.EMPTY);
    }

    /** 输入槽按物品自身堆叠上限的整数倍放宽，输出槽只取物品堆叠上限，与本体大型锅一致。 */
    public int slotLimit(int slot, ItemStack stack) {
        int base = stack.isEmpty() ? DEFAULT_STACK_LIMIT : stack.getMaxStackSize();
        return this.isOutputSlot(slot) ? base : base * this.inputStackMultiplier();
    }

    public int fluidLayers() {
        return this.fluidLayers;
    }

    /** 普通锅由模型容量决定，大型锅与本体共用运行时配置，保留不足一桶的精确 mB 数量。 */
    public int fluidLayerCapacity(int capacity) {
        return this == LARGE ? LargeCauldronFluidHandler.tankCapacity() : Math.multiplyExact(capacity, 1000);
    }

    public int fluidCapacity(int capacity) {
        return Math.multiplyExact(this.fluidLayerCapacity(capacity), this.fluidLayers);
    }

    public int inputStackMultiplier() {
        return this == LARGE ? AnvilCraft.CONFIG.machines.largeCauldronInputStackMultiplier : this.inputStackMultiplier;
    }

    /** 单次落砧最多执行的加工组数，大型锅每组以一个普通堆叠为预算。 */
    public int recipePasses() {
        return this == LARGE ? AnvilCraft.CONFIG.machines.largeCauldronMaxProcessEfficiency : this.recipePasses;
    }
}
