package dev.anvilcraft.plasticraft.molding.blueprint;

/** 可安全回送给玩家的蓝图操作失败。 */
public class BlueprintException extends Exception {
    private final String reason;

    public BlueprintException(String reason, String detail) {
        super(detail);
        this.reason = reason;
    }

    public BlueprintException(String reason, String detail, Throwable cause) {
        super(detail, cause);
        this.reason = reason;
    }

    public String reason() {
        return this.reason;
    }
}
