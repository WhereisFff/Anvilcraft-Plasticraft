package dev.anvilcraft.plasticraft.api.blockentity;

/** 为本体等离子喷流方块实体附加强化喷流状态。 */
public interface EnhancedPlasmaJetExtension {
    boolean plasticraft$isEnhanced();

    void plasticraft$setEnhanced(boolean enhanced);

    boolean plasticraft$usesLayeredFuel();

    void plasticraft$setUsesLayeredFuel(boolean layeredFuel);
}
