package dev.anvilcraft.plasticraft.api.texture;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 一次版本化塑料贴图生成的完整权威输入。
 *
 * <p>资源 ID 与资源内容哈希分开保存：前者说明所用材料，后者保证资源包重载后
 * 不会错误复用旧缓存。{@code paletteRow} 是有序色板中的材料颜色行，不绑定
 * {@code UniversalPlasticBlock} 或任何未来功能制品。</p>
 */
public record PlasticTextureInput(
    int generatorVersion,
    String shapeHash,
    List<PlasticSurface> surfaces,
    String baseTextureId,
    String baseTextureHash,
    String paletteId,
    String paletteHash,
    int paletteRow
) {
    public PlasticTextureInput {
        if (generatorVersion <= 0) {
            throw new IllegalArgumentException("Plastic texture generator version must be positive");
        }
        shapeHash = requireText(shapeHash, "shapeHash");
        surfaces = List.copyOf(Objects.requireNonNull(surfaces, "surfaces"));
        if (surfaces.isEmpty()) {
            throw new IllegalArgumentException("Plastic texture input must contain at least one surface");
        }
        String computedShapeHash = computeShapeHash(surfaces);
        if (!shapeHash.equals(computedShapeHash)) {
            throw new IllegalArgumentException("Plastic shape hash does not match its surface data");
        }
        baseTextureId = requireText(baseTextureId, "baseTextureId");
        baseTextureHash = requireText(baseTextureHash, "baseTextureHash");
        paletteId = requireText(paletteId, "paletteId");
        paletteHash = requireText(paletteHash, "paletteHash");
        if (paletteRow < 0) {
            throw new IllegalArgumentException("Plastic palette row must not be negative");
        }
    }

    /**
     * 对表面数据做规范化排序并计算 SHA-256，供建模器和服务端共同生成稳定形状哈希。
     */
    public static String computeShapeHash(List<PlasticSurface> surfaces) {
        Objects.requireNonNull(surfaces, "surfaces");
        MessageDigest digest = sha256Digest();

        surfaces.stream()
            .sorted(Comparator.comparing(PlasticSurface::id))
            .forEach(surface -> updateSurfaceHash(digest, surface));
        return HexFormat.of().formatHex(digest.digest());
    }

    /** 对原始资源字节计算稳定 SHA-256，避免资源重载后复用旧生成结果。 */
    public static String computeResourceHash(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return HexFormat.of().formatHex(sha256Digest().digest(bytes));
    }

    private static void updateSurfaceHash(MessageDigest digest, PlasticSurface surface) {
        updateText(digest, surface.id());
        for (PlasticSurface.Point point : surface.vertices()) {
            updatePoint(digest, point);
        }
        updatePoint(digest, surface.normal());
        updateInt(digest, surface.pixelWidth());
        updateInt(digest, surface.pixelHeight());
        updateInt(digest, surface.doubleSided() ? 1 : 0);
        updateInt(digest, surface.grooveShade());
        updateInt(digest, surface.ambientOcclusionShade());
    }

    private static void updateText(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updatePoint(MessageDigest digest, PlasticSurface.Point point) {
        updateDouble(digest, point.x());
        updateDouble(digest, point.y());
        updateDouble(digest, point.z());
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    /** 以 IEEE-754 位模式写入坐标，确保不同平台与区域设置得到同一形状哈希。 */
    private static void updateDouble(MessageDigest digest, double value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(Double.doubleToLongBits(value)).array());
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
