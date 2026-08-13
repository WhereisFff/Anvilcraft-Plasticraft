package dev.anvilcraft.plasticraft.drone;

/** 无人机统一飞行/降落状态机;顺序即同步字节值,进入网络后保持稳定。 */
public enum DroneFlightState {
    /** 落地静止,不扣悬浮 FE。 */
    LANDED,
    /** 起飞爬升到目标悬停高度。 */
    TAKING_OFF,
    /** 悬停保持;观察无人机无任务时悬停在地面上方四格。 */
    HOVERING,
    /** 主动下降直到触地。 */
    LANDING,
    /** 飞向无人机站顶部泊位准备入库;泊位忙时在站旁悬停等待。 */
    DOCKING;

    public static DroneFlightState byId(int id) {
        DroneFlightState[] values = values();
        return id >= 0 && id < values.length ? values[id] : LANDED;
    }

    /** 空中状态会驱动螺旋桨旋转;渲染器只读取该判断,不自行推断。 */
    public boolean isAirborne() {
        return this != LANDED;
    }
}
