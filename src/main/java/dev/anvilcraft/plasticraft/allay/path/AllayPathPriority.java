package dev.anvilcraft.plasticraft.allay.path;

/** 悦灵寻路排队优先级,序越小越先跑。 */
public enum AllayPathPriority {
    ESCAPE,
    STUCK,
    DOCK_HEAD,
    DELIVER,
    PICKUP,
    LEAVE
}
