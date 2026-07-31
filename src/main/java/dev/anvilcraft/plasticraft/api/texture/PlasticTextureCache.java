package dev.anvilcraft.plasticraft.api.texture;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * 仅缓存 CPU 生成结果的共享缓存；资源重载和退出世界时由客户端入口显式清空。
 */
public final class PlasticTextureCache {
    private static final ConcurrentMap<Key, GeneratedPlasticTexture> CACHE = new ConcurrentHashMap<>();

    private PlasticTextureCache() {
    }

    public static GeneratedPlasticTexture getOrGenerate(
        PlasticTextureInput input,
        Supplier<GeneratedPlasticTexture> generator
    ) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(generator, "generator");
        Key key = new Key(
            input.generatorVersion(),
            input.shapeHash(),
            input.baseTextureHash(),
            input.paletteId(),
            input.paletteHash(),
            input.paletteRow()
        );
        return CACHE.computeIfAbsent(key, ignored -> generator.get());
    }

    public static void clear() {
        CACHE.clear();
    }

    public static int size() {
        return CACHE.size();
    }

    private record Key(
        int generatorVersion,
        String shapeHash,
        String baseTextureHash,
        String paletteId,
        String paletteHash,
        int paletteRow
    ) {
    }
}
