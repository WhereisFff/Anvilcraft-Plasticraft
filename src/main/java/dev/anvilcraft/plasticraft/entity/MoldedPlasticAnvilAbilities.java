package dev.anvilcraft.plasticraft.entity;

import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.event.giantanvil.GiantAnvilLandingEventListener;
import dev.dubhe.anvilcraft.event.giantanvil.shock.GiantAnvilShockEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** 将巨型塑料铁砧接入 AnvilCraft 的巨型落地能力。 */
public final class MoldedPlasticAnvilAbilities {
    private MoldedPlasticAnvilAbilities() {
    }

    /** 返回普通落砧事件是否已由大型炼药锅专用流程消费。 */
    public static boolean handleLanding(
        UniversalPlasticEntity entity,
        AnvilEvent.OnLand event
    ) {
        if (!entity.isMoldedGiantAnvil()) return false;

        // 巨型事件把实体类型固定为 FallingGiantAnvilEntity，直接复用公开处理器可避免伪造代理实体。
        AnvilEvent.GiantOnLand giantEvent = new AnvilEvent.GiantOnLand(
            event.getLevel(),
            event.getPos().above(),
            null,
            event.getFallDistance()
        );
        GiantAnvilLandingEventListener.handleMultiblock(giantEvent);
        GiantAnvilShockEventListener.onLand(giantEvent);

        BlockPos hitPos = event.getPos().below();
        BlockState hitState = event.getLevel().getBlockState(hitPos);
        if (!(hitState.getBlock() instanceof LargeCauldronBlock)) return false;
        LargeCauldronBlockEntity cauldron = LargeCauldronBlockEntity.getMain(
            event.getLevel(),
            hitPos,
            hitState
        );
        return cauldron != null && cauldron.handleGiantAnvilImpact(event);
    }
}
