package dev.anvilcraft.plasticraft.allay;

import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import org.jetbrains.annotations.Nullable;

public enum AllayLoungeStatus {
    IDLE((byte) 0),
    RUNNING((byte) 1),
    INTERRUPTED((byte) 2);

    private final byte id;

    AllayLoungeStatus(byte id) {
        this.id = id;
    }

    public byte id() {
        return this.id;
    }

    public static AllayLoungeStatus fromId(byte id) {
        return switch (id) {
            case 1 -> RUNNING;
            case 2 -> INTERRUPTED;
            default -> IDLE;
        };
    }

    public static AllayLoungeStatus forJob(@Nullable ConstructionJob job, boolean previouslyStarted) {
        if (job == null) return IDLE;
        return switch (job.state()) {
            case ConstructionJob.STATE_INACTIVE -> previouslyStarted ? INTERRUPTED : IDLE;
            case ConstructionJob.STATE_COMPLETED, ConstructionJob.STATE_COMPLETED_INCOMPLETE -> IDLE;
            case ConstructionJob.STATE_ACTIVE, ConstructionJob.STATE_PLANNING,
                ConstructionJob.STATE_SEALING_FLUID, ConstructionJob.STATE_DEMOLISHING,
                ConstructionJob.STATE_COLLECTING_DEBRIS, ConstructionJob.STATE_BUILDING,
                ConstructionJob.STATE_COMMITTING -> RUNNING;
            default -> INTERRUPTED;
        };
    }
}
