package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayComponentLookup;
import dev.dubhe.anvilcraft.block.entity.PulseGeneratorBlockEntity;
import dev.dubhe.anvilcraft.inventory.PulseGeneratorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 让脉冲发生器菜单客户端找到移动支架里的虚拟方块实体。 */
@Mixin(PulseGeneratorMenu.class)
abstract class PulseGeneratorMenuMixin {
    @Redirect(
        method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;"
            + "Lnet/minecraft/network/FriendlyByteBuf;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)"
                + "Lnet/minecraft/world/level/block/entity/BlockEntity;"
        )
    )
    private static BlockEntity plasticraft$findTrayGenerator(Level level, BlockPos pos) {
        BlockEntity original = level.getBlockEntity(pos);
        return original instanceof PulseGeneratorBlockEntity
            ? original
            : MoldedTrayComponentLookup.find(
                level,
                pos,
                MoldedTrayComponentLookup.Kind.PULSE_GENERATOR
            );
    }
}
