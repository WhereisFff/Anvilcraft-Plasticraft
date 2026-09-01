package dev.anvilcraft.plasticraft.vapor.event;

import dev.anvilcraft.plasticraft.vapor.VaporizationContext;
import net.neoforged.bus.api.Event;

import java.util.Objects;

/** 围绕一次大型炼药锅气化事务触发。仅在服务器线程发布。 */
public final class LargeCauldronProcessEvent extends Event {
    private final VaporizationContext context;
    private final Phase phase;

    public LargeCauldronProcessEvent(VaporizationContext context, Phase phase) {
        this.context = Objects.requireNonNull(context, "context");
        this.phase = Objects.requireNonNull(phase, "phase");
    }

    public VaporizationContext context() {
        return this.context;
    }

    public Phase phase() {
        return this.phase;
    }

    public enum Phase {
        BEFORE_VAPORIZATION,
        AFTER_VAPORIZATION
    }
}
