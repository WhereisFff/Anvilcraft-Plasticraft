package dev.anvilcraft.plasticraft.entity.redstone;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** 支架红石元件行为的唯一分派入口。 */
final class MoldedTrayRedstoneBehaviors {
    private static final MoldedTrayRedstoneBehavior NONE = new MoldedTrayRedstoneBehavior() {
        @Override
        public boolean supports(BlockState state) {
            return false;
        }
    };
    private static final List<MoldedTrayRedstoneBehavior> BEHAVIORS = List.of(
        new MoldedTrayRepeaterBehavior(),
        new MoldedTrayComparatorBehavior(),
        new MoldedTrayAdvancedComparatorBehavior(),
        new MoldedTrayRedstoneTorchBehavior(),
        new MoldedTrayButtonBehavior(),
        new MoldedTrayLeverBehavior(),
        new MoldedTrayPressurePlateBehavior(),
        new MoldedTrayDaylightDetectorBehavior(),
        new MoldedTrayPulseGeneratorBehavior(),
        new MoldedTrayItemDetectorBehavior()
    );

    private MoldedTrayRedstoneBehaviors() {
    }

    static MoldedTrayRedstoneBehavior find(BlockState state) {
        for (MoldedTrayRedstoneBehavior behavior : BEHAVIORS) {
            if (behavior.supports(state)) return behavior;
        }
        return NONE;
    }
}
