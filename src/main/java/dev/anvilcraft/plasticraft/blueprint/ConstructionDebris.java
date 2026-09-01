package dev.anvilcraft.plasticraft.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.plasticraft.init.PlasticraftDataComponents;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** 施工拆除掉落物上的任务来源标记;参与物品堆叠判定,避免与玩家掉落物合并。 */
public record ConstructionDebris(UUID jobId, int operationId) {
    public static final Codec<ConstructionDebris> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("job_id").forGetter(ConstructionDebris::jobId),
        Codec.INT.fieldOf("operation_id").forGetter(ConstructionDebris::operationId)
    ).apply(instance, ConstructionDebris::new));

    public static final StreamCodec<ByteBuf, ConstructionDebris> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC,
        ConstructionDebris::jobId,
        ByteBufCodecs.VAR_INT,
        ConstructionDebris::operationId,
        ConstructionDebris::new
    );

    public static void mark(ItemStack stack, UUID jobId, int operationId) {
        if (stack.isEmpty()) return;
        stack.set(PlasticraftDataComponents.CONSTRUCTION_DEBRIS.get(), new ConstructionDebris(jobId, operationId));
    }

    public static boolean isMarked(ItemStack stack) {
        return get(stack) != null;
    }

    @Nullable
    public static ConstructionDebris get(ItemStack stack) {
        if (stack.isEmpty()) return null;
        return stack.get(PlasticraftDataComponents.CONSTRUCTION_DEBRIS.get());
    }

    public static void clear(ItemStack stack) {
        if (stack.isEmpty()) return;
        stack.remove(PlasticraftDataComponents.CONSTRUCTION_DEBRIS.get());
    }
}
