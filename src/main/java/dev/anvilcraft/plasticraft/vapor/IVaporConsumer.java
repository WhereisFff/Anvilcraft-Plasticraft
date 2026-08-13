package dev.anvilcraft.plasticraft.vapor;

/** 接在大型炼药锅排气口上的机器。 */
@FunctionalInterface
public interface IVaporConsumer {
    /**
     * 返回从 {@code vapor} 接受的数量。模拟阶段不得改变状态。服务器线程上紧随其后的执行
     * 会收到按模拟接受量裁好的堆栈，消费者必须整份收下。
     */
    int receiveVapor(VaporStack vapor, VaporAction action, VaporizationContext context);

    /**
     * 此消费者是否封闭排气口，因而在无法接受全部气体时产生背压。开放消费者返回 {@code false}：
     * 无法接受的气体逸散到大气中。此查询不得改变世界或消费者状态。
     */
    default boolean sealsOutlet(VaporizationContext context) {
        return false;
    }
}
