package dev.anvilcraft.plasticraft.api.texture;

/**
 * 塑料贴图基础资源不满足稳定格式时抛出的明确错误。
 *
 * <p>客户端资源入口会记录此异常并生成安全占位图，因此坏资源包不会让客户端崩溃。</p>
 */
public class PlasticTextureResourceException extends IllegalArgumentException {
    public PlasticTextureResourceException(String message) {
        super(message);
    }

    public PlasticTextureResourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
