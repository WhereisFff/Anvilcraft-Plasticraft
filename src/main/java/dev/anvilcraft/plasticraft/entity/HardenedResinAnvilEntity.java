package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.inventory.HardenedResinAnvilMenu;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.function.Supplier;

/** 具体的硬化树脂砧，共用移动逻辑位于抽象基类中。 */
public class HardenedResinAnvilEntity extends AbstractPlasticEntity {
    private static Supplier<ItemStack> defaultDropSupplier = () -> ItemStack.EMPTY;

    /** 配置旧实体或存档加载的实体没有明确物品堆时返回的物品。 */
    public static void configureDefaultDrop(Supplier<ItemStack> supplier) {
        defaultDropSupplier = Objects.requireNonNull(supplier, "supplier");
    }

    public HardenedResinAnvilEntity(EntityType<? extends HardenedResinAnvilEntity> entityType, Level level) {
        super(entityType, level);
        this.setDisplayState(ModBlocks.HARDEND_RESIN_ANVIL.get().defaultBlockState());
    }

    public HardenedResinAnvilEntity(
        EntityType<? extends HardenedResinAnvilEntity> entityType,
        Level level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    ) {
        super(entityType, level, position, displayState, dropStack, orientation);
    }

    @Override
    protected ItemStack createDefaultDropStack() {
        ItemStack fallback = defaultDropSupplier.get();
        if (fallback == null || fallback.isEmpty()) {
            return ItemStack.EMPTY;
        }
        fallback = fallback.copy();
        PlasticItemData.setMaterial(fallback, "hardened_resin");
        return fallback;
    }

    @Override
    protected void openAnvilMenu(ServerPlayer player) {
        HardenedResinAnvilMenu.open(player, this);
    }
}
