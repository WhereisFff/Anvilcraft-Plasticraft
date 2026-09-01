package dev.anvilcraft.plasticraft.block;

import net.minecraft.util.StringRepresentable;

/** 成型舱生产流程中需要持久化的阶段。 */
public enum PlasticMoldingMachineState implements StringRepresentable {
    EDITABLE("editable"),
    WAITING_TO_LOCK("waiting_to_lock"),
    MOLD_FILLING("mold_filling"),
    MOLD_READY("mold_ready"),
    PROCESS_READY("process_ready"),
    PROCESSING("processing"),
    WAITING_NEXT_CYCLE("waiting_next_cycle");

    private final String serializedName;

    PlasticMoldingMachineState(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }

    public static PlasticMoldingMachineState fromSerializedName(String name) {
        for (PlasticMoldingMachineState state : values()) {
            if (state.serializedName.equals(name)) return state;
        }
        return EDITABLE;
    }
}
