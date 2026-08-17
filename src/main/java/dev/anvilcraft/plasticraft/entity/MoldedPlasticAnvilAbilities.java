package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.dubhe.anvilcraft.api.IHasMultiBlock;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.block.multipart.AbstractMultiPartBlock;
import dev.dubhe.anvilcraft.event.giantanvil.GiantAnvilLandingEventListener;
import dev.dubhe.anvilcraft.event.giantanvil.shock.GiantAnvilShockEventListener;
import dev.dubhe.anvilcraft.util.AnvilUtil;
import dev.dubhe.anvilcraft.util.BlockMiningEffect;
import dev.dubhe.anvilcraft.util.BreakBlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** 将巨型塑料铁砧接入 AnvilCraft 的巨型落地能力。 */
public final class MoldedPlasticAnvilAbilities {
    private MoldedPlasticAnvilAbilities() {
    }

    /** 返回普通落砧事件是否已由大型炼药锅专用流程消费。 */
    public static boolean handleLanding(
        UniversalPlasticEntity entity,
        AnvilEvent.OnLand event
    ) {
        if (entity.isMoldedGiantAnvil() && handleGiantLanding(event)) return true;
        return handleStonecutterBreaking(entity, event);
    }

    private static boolean handleGiantLanding(AnvilEvent.OnLand event) {
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

    private static boolean handleStonecutterBreaking(
        UniversalPlasticEntity entity,
        AnvilEvent.OnLand event
    ) {
        if (!(event.getLevel() instanceof ServerLevel level) || !entity.isMoldedAnvil()) return false;
        BlockPos breakPos = mainPartOf(level, event.getPos().below());
        BlockState state = level.getBlockState(breakPos);
        if (!hasStonecutterBelow(level, breakPos, state)) return false;
        if (state.getBlock().getExplosionResistance() >= 1200.0F) event.setAnvilDamage(true);
        if (state.getDestroySpeed(level, breakPos) < 0.0F) return true;

        BlockMiningEffect effect = miningEffect(entity);
        ItemStack tool = BreakBlockUtil.createTool(level, state, effect);
        state.spawnAfterBreak(level, breakPos, tool, false);
        if (state.getBlock() instanceof IHasMultiBlock multiBlock) {
            multiBlock.onRemove(level, breakPos, state);
        }
        List<ItemStack> drops = BreakBlockUtil.drop(level, breakPos, effect);
        AnvilUtil.dropItems(drops, level, breakPos.getCenter());
        level.setBlockAndUpdate(breakPos, Blocks.AIR.defaultBlockState());
        return true;
    }

    public static BlockMiningEffect miningEffect(UniversalPlasticEntity entity) {
        return entity.getMoldedData()
            .flatMap(data -> PlasticMaterial.fromMelt(data.material()))
            .map(material -> switch (material) {
                case ENGINEERING -> BlockMiningEffect.SILK_TOUCH;
                case HEAT_RESISTANT -> BlockMiningEffect.SMELTING;
                case UNIVERSAL, CLEAR -> BlockMiningEffect.NORMAL;
            })
            .orElse(BlockMiningEffect.NORMAL);
    }

    private static BlockPos mainPartOf(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof AbstractMultiPartBlock<?> multiPartBlock) {
            BlockPos main = multiPartBlock.getMainPartPos(pos, state);
            if (level.getBlockState(main).is(multiPartBlock)) return main;
        }
        return pos;
    }

    private static boolean hasStonecutterBelow(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof AbstractMultiPartBlock<?> multiPartBlock) {
            return hasStonecutterBelowAnyPart(level, pos, state, multiPartBlock);
        }
        return level.getBlockState(pos.below()).is(Blocks.STONECUTTER);
    }

    private static <P extends Enum<P>> boolean hasStonecutterBelowAnyPart(
        ServerLevel level,
        BlockPos mainPos,
        BlockState mainState,
        AbstractMultiPartBlock<P> multiPartBlock
    ) {
        for (P part : multiPartBlock.getParts()) {
            BlockPos partPos = mainPos.offset(multiPartBlock.offsetFrom(mainState, part));
            if (level.getBlockState(partPos).is(multiPartBlock)
                && level.getBlockState(partPos.below()).is(Blocks.STONECUTTER)) {
                return true;
            }
        }
        return false;
    }
}
