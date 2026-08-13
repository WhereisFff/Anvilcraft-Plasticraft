package dev.anvilcraft.plasticraft.blueprint;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/** 蓝图内容的导入来源;序列化名一经写入磁盘组件即冻结,只能追加新项。 */
public enum BlueprintSource implements StringRepresentable {
    /** 原版结构方块保存并经结构模板管理器读取的模板。 */
    VANILLA_TEMPLATE("vanilla_template"),
    /** AnvilCraft 结构扫描器写出的结构磁盘世界文件。 */
    SCANNER_DISK("scanner_disk"),
    /** 客户端文件导入的原版结构 .nbt。 */
    VANILLA_FILE("vanilla_file"),
    /** 客户端文件导入的 Create 蓝图 .nbt。 */
    CREATE_FILE("create_file"),
    /** 客户端文件导入的 Litematica .litematic。 */
    LITEMATICA_FILE("litematica_file");

    public static final Codec<BlueprintSource> CODEC = StringRepresentable.fromEnum(BlueprintSource::values);
    public static final StreamCodec<ByteBuf, BlueprintSource> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(
        BlueprintSource::bySerializedName,
        BlueprintSource::getSerializedName
    );

    private final String serializedName;

    BlueprintSource(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }

    public static BlueprintSource bySerializedName(String name) {
        for (BlueprintSource source : values()) {
            if (source.serializedName.equals(name)) return source;
        }
        return VANILLA_TEMPLATE;
    }
}
