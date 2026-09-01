package dev.anvilcraft.plasticraft.allay;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 戴帽悦灵在世界实体与休息室托管记录之间往返的完整数据。
 * 工种不存盘,由主手物品在加载后解析。
 */
public record AllayWorkRecord(
    UUID entityId,
    ItemStack hardHat,
    ItemStack heldTool,
    Optional<UUID> owner,
    AllayShortageStrategy shortageStrategy,
    List<ItemStack> collectionInventory,
    Optional<UUID> assignedJobId,
    ItemStack hostedCarry,
    Optional<Component> customName,
    Optional<Long> originLounge,
    Optional<UUID> transitJob
) {
    public static final Codec<AllayWorkRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("entity_id").forGetter(AllayWorkRecord::entityId),
        ItemStack.OPTIONAL_CODEC.optionalFieldOf("hard_hat", ItemStack.EMPTY).forGetter(AllayWorkRecord::hardHat),
        ItemStack.OPTIONAL_CODEC.optionalFieldOf("held_tool", ItemStack.EMPTY).forGetter(AllayWorkRecord::heldTool),
        UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(AllayWorkRecord::owner),
        AllayShortageStrategy.CODEC.optionalFieldOf("shortage_strategy", AllayShortageStrategy.PAUSE)
            .forGetter(AllayWorkRecord::shortageStrategy),
        ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("collection_inventory", List.of())
            .forGetter(AllayWorkRecord::collectionInventory),
        UUIDUtil.CODEC.optionalFieldOf("assigned_job_id").forGetter(AllayWorkRecord::assignedJobId),
        ItemStack.OPTIONAL_CODEC.optionalFieldOf("hosted_carry", ItemStack.EMPTY)
            .forGetter(AllayWorkRecord::hostedCarry),
        ComponentSerialization.CODEC.optionalFieldOf("custom_name").forGetter(AllayWorkRecord::customName),
        Codec.LONG.optionalFieldOf("origin_lounge").forGetter(AllayWorkRecord::originLounge),
        UUIDUtil.CODEC.optionalFieldOf("transit_job").forGetter(AllayWorkRecord::transitJob)
    ).apply(instance, AllayWorkRecord::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AllayWorkRecord> STREAM_CODEC = StreamCodec.of(
        AllayWorkRecord::encode,
        AllayWorkRecord::decode
    );

    public AllayWorkRecord {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(hardHat, "hardHat");
        Objects.requireNonNull(heldTool, "heldTool");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(shortageStrategy, "shortageStrategy");
        collectionInventory = List.copyOf(collectionInventory);
        Objects.requireNonNull(assignedJobId, "assignedJobId");
        Objects.requireNonNull(hostedCarry, "hostedCarry");
        Objects.requireNonNull(customName, "customName");
        Objects.requireNonNull(originLounge, "originLounge");
        Objects.requireNonNull(transitJob, "transitJob");
    }

    /**
     * 非转运悦灵的便捷构造器，转运字段缺省为空。
     */
    public AllayWorkRecord(
        UUID entityId,
        ItemStack hardHat,
        ItemStack heldTool,
        Optional<UUID> owner,
        AllayShortageStrategy shortageStrategy,
        List<ItemStack> collectionInventory,
        Optional<UUID> assignedJobId,
        ItemStack hostedCarry,
        Optional<Component> customName
    ) {
        this(
            entityId,
            hardHat,
            heldTool,
            owner,
            shortageStrategy,
            collectionInventory,
            assignedJobId,
            hostedCarry,
            customName,
            Optional.empty(),
            Optional.empty()
        );
    }

    private static void encode(RegistryFriendlyByteBuf buffer, AllayWorkRecord data) {
        UUIDUtil.STREAM_CODEC.encode(buffer, data.entityId);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, data.hardHat);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, data.heldTool);
        ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).encode(buffer, data.owner);
        AllayShortageStrategy.STREAM_CODEC.encode(buffer, data.shortageStrategy);
        ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buffer, data.collectionInventory);
        ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).encode(buffer, data.assignedJobId);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, data.hostedCarry);
        ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC).encode(buffer, data.customName);
        ByteBufCodecs.optional(ByteBufCodecs.VAR_LONG).encode(buffer, data.originLounge);
        ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).encode(buffer, data.transitJob);
    }

    private static AllayWorkRecord decode(RegistryFriendlyByteBuf buffer) {
        return new AllayWorkRecord(
            UUIDUtil.STREAM_CODEC.decode(buffer),
            ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
            ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buffer),
            AllayShortageStrategy.STREAM_CODEC.decode(buffer),
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buffer),
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buffer),
            ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
            ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC).decode(buffer),
            ByteBufCodecs.optional(ByteBufCodecs.VAR_LONG).decode(buffer),
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buffer)
        );
    }
}
