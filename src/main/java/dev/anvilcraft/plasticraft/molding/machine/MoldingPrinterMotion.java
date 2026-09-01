package dev.anvilcraft.plasticraft.molding.machine;

import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;

/** 打印机构使用的确定性点到点运动；服务端判定和客户端插值共用同一时间曲线。 */
public record MoldingPrinterMotion(
    MoldingVec3 from,
    MoldingVec3 to,
    long startGameTime,
    int durationTicks
) {
    public static final int DEFAULT_HORIZONTAL_COORDINATE = 16;
    public static final int DEFAULT_VERTICAL_COORDINATE = 32;
    public static final MoldingVec3 DEFAULT_POSITION = new MoldingVec3(
        DEFAULT_HORIZONTAL_COORDINATE,
        DEFAULT_VERTICAL_COORDINATE,
        DEFAULT_HORIZONTAL_COORDINATE
    );

    public MoldingPrinterMotion {
        if (durationTicks < 0) throw new IllegalArgumentException("Printer motion duration must not be negative");
    }

    public static MoldingPrinterMotion between(MoldingVec3 from, MoldingVec3 to, long startGameTime) {
        return new MoldingPrinterMotion(from, to, startGameTime, duration(from, to));
    }

    public static MoldingPrinterMotion stationary(MoldingVec3 position, long gameTime) {
        return new MoldingPrinterMotion(position, position, gameTime, 0);
    }

    public boolean complete(long gameTime) {
        return gameTime >= this.startGameTime + this.durationTicks;
    }

    public MoldingVec3 position(double gameTime) {
        if (this.durationTicks == 0) return this.to;
        double linear = Math.clamp((gameTime - this.startGameTime) / this.durationTicks, 0.0D, 1.0D);
        double eased = easeInOutQuadratic(linear);
        return this.from.add(this.to.subtract(this.from).scale(eased));
    }

    public static double easeInOutQuadratic(double progress) {
        double value = Math.clamp(progress, 0.0D, 1.0D);
        return value < 0.5D
            ? 2.0D * value * value
            : 1.0D - 2.0D * (1.0D - value) * (1.0D - value);
    }

    private static int duration(MoldingVec3 from, MoldingVec3 to) {
        MoldingVec3 distance = to.subtract(from);
        double longestAxis = Math.max(
            Math.max(Math.abs(distance.x()), Math.abs(distance.y())),
            Math.abs(distance.z())
        );
        return longestAxis == 0.0D ? 0 : Math.max(1, (int) Math.ceil(longestAxis));
    }
}
