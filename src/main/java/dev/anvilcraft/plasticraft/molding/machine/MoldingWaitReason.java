package dev.anvilcraft.plasticraft.molding.machine;

public enum MoldingWaitReason {
    NONE,
    INVALID_MODEL,
    STRUCTURE_INCOMPLETE,
    REGION_BLOCKED,
    MISSING_CLAY,
    MISSING_PRINTING_COMPONENT,
    MISSING_POWER,
    MOLD_FILLING,
    MOLD_READY,
    PUMPING,
    BATCH_FULL,
    PROCESS_READY,
    PROCESSING,
    WAITING_FOR_CLEAR_REGION
}
