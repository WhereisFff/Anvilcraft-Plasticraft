package dev.anvilcraft.plasticraft.molding.type;

/** 服务端功能类型验证结果，容量单位由对应类型定义。 */
public record MoldingTypeValidation(boolean valid, int capacity, String reason) {
    public MoldingTypeValidation {
        if (capacity < 0) throw new IllegalArgumentException("Molding type capacity must not be negative");
        reason = reason == null ? "" : reason;
    }

    public static MoldingTypeValidation valid(int capacity) {
        return new MoldingTypeValidation(true, capacity, "");
    }

    public static MoldingTypeValidation invalid(String reason) {
        return new MoldingTypeValidation(false, 0, reason);
    }
}
