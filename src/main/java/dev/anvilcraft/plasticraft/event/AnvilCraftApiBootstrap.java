package dev.anvilcraft.plasticraft.event;

import dev.anvilcraft.plasticraft.block.piston.SlidingAdhesionStructureExtension;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticCauldron;
import dev.anvilcraft.plasticraft.entity.PlasticCauldrons;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayComponentLookup;
import dev.anvilcraft.plasticraft.recipe.CauldronImpactRecipeProcessor;
import dev.anvilcraft.plasticraft.recipe.CondenserTowerProcess;
import dev.anvilcraft.plasticraft.recipe.EnhancedPlasmaJetBehavior;
import dev.anvilcraft.plasticraft.recipe.PlasmaJetAddonFuels;
import dev.dubhe.anvilcraft.api.menu.MenuBlockEntityLookup;
import dev.dubhe.anvilcraft.api.plasma.PlasmaJetHooks;
import dev.dubhe.anvilcraft.api.sliding.SlidingRailLoad;
import dev.dubhe.anvilcraft.api.sliding.SlidingStructureHooks;
import dev.dubhe.anvilcraft.block.entity.AdvancedComparatorBlockEntity;
import dev.dubhe.anvilcraft.block.entity.ItemDetectorBlockEntity;
import dev.dubhe.anvilcraft.block.entity.PulseGeneratorBlockEntity;
import dev.dubhe.anvilcraft.recipe.anvil.predicate.block.HasCauldron;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** 把塑料工艺接到铁砧工艺公开扩展点。不得在此初始化 JEI/Jade 等可选模组客户端 API。 */
public final class AnvilCraftApiBootstrap {
    private AnvilCraftApiBootstrap() {
    }

    public static void register() {
        PlasmaJetHooks.registerPassThrough(CondenserTowerProcess::isPlasmaPassThrough);
        PlasmaJetHooks.registerFuel(PlasmaJetAddonFuels.RESIN_CAULDRON);
        PlasmaJetHooks.registerFuel(PlasmaJetAddonFuels.HIGH_HEAT);
        PlasmaJetHooks.registerBehavior(EnhancedPlasmaJetBehavior.INSTANCE);

        SlidingRailLoad.registerOccupant((level, railPos, above) ->
            !level.getEntitiesOfClass(AbstractPlasticEntity.class, above).isEmpty());
        SlidingStructureHooks.register(SlidingAdhesionStructureExtension.INSTANCE);

        HasCauldron.registerEntityCauldronSelector((context, pos, current) -> {
            // 通用塑料实体无条件实现锅接口，成型类型不是锅时必须拒答，否则储罐等制品会被当成实体锅
            if (current instanceof PlasticCauldron cauldron && !cauldron.plasticraft$isCauldron()) return null;
            if (!CauldronImpactRecipeProcessor.hasTargetedRecipe()) return current;
            PlasticCauldron target = CauldronImpactRecipeProcessor.activeRecipeTarget();
            BlockPos targetCell = CauldronImpactRecipeProcessor.activeRecipeTargetCell();
            if (target != null
                && target.level() == context.getLevel()
                && !target.isRemoved()
                && targetCell != null
                && targetCell.equals(pos)) {
                return target;
            }
            if (PlasticCauldrons.isCauldron(current)) return null;
            return current;
        });

        MenuBlockEntityLookup.register(AnvilCraftApiBootstrap::findTrayMenuBlockEntity);
    }

    private static BlockEntity findTrayMenuBlockEntity(
        Level level,
        BlockPos pos,
        Class<? extends BlockEntity> type
    ) {
        MoldedTrayComponentLookup.Kind kind;
        if (ItemDetectorBlockEntity.class.isAssignableFrom(type)) {
            kind = MoldedTrayComponentLookup.Kind.ITEM_DETECTOR;
        } else if (AdvancedComparatorBlockEntity.class.isAssignableFrom(type)) {
            kind = MoldedTrayComponentLookup.Kind.ADVANCED_COMPARATOR;
        } else if (PulseGeneratorBlockEntity.class.isAssignableFrom(type)) {
            kind = MoldedTrayComponentLookup.Kind.PULSE_GENERATOR;
        } else {
            return null;
        }
        return MoldedTrayComponentLookup.find(level, pos, kind);
    }
}
