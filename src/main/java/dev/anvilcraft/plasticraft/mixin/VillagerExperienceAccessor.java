package dev.anvilcraft.plasticraft.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 让气态经验沿用原版村民升级流程并补齐每一级交易。 */
@Mixin(Villager.class)
public interface VillagerExperienceAccessor {
    @Invoker("increaseMerchantCareer")
    void plasticraft$increaseMerchantCareer();
}
