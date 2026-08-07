package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayComponentLookup;
import dev.dubhe.anvilcraft.block.entity.ItemDetectorBlockEntity;
import dev.dubhe.anvilcraft.inventory.ItemDetectorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 让物品探测器菜单客户端找到移动支架里的虚拟方块实体。 */
@Mixin(ItemDetectorMenu.class)
abstract class ItemDetectorMenuMixin {
    @Redirect(
        method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;"
            + "Lnet/minecraft/network/FriendlyByteBuf;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)"
                + "Lnet/minecraft/world/level/block/entity/BlockEntity;"
        )
    )
    private static BlockEntity plasticraft$findTrayDetector(Level level, BlockPos pos) {
        BlockEntity original = level.getBlockEntity(pos);
        return original instanceof ItemDetectorBlockEntity
            ? original
            : MoldedTrayComponentLookup.find(
                level,
                pos,
                MoldedTrayComponentLookup.Kind.ITEM_DETECTOR
            );
    }
}
