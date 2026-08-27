package dev.anvilcraft.plasticraft.molding.product;

import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
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
    /** 对齐本体大型炼药锅：输出 32 槽、输入 8 槽 × 9 倍堆叠、8 层 × 64 B、单次落砧最多 9 次配方。 */
    LARGE(32, 8, 9, 8, 9);

    /** 所有形态中最大的输入槽堆叠倍率，供不知道具体形态的通用校验取上界。 */
    public static final int MAX_STACK_MULTIPLIER = 9;
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
        return this.isOutputSlot(slot) ? base : base * this.inputStackMultiplier;
    }

    public int fluidLayers() {
        return this.fluidLayers;
    }

    /**
     * 单层流体上限，由制品总容量均分到各层。
     *
     * <p>普通锅只有一层，上限即整锅容量；大型锅固定 512 B / 8 层 = 64 B，与本体大型炼药锅一致。
     */
    public int fluidLayerCapacity(int capacity) {
        return Math.multiplyExact(capacity, 1000) / this.fluidLayers;
    }

    /** 单次落砧最多执行的配方次数。 */
    public int recipePasses() {
        return this.recipePasses;
    }
}
