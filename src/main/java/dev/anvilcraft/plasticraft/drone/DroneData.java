package dev.anvilcraft.plasticraft.drone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.plasticraft.init.PlasticraftDataComponents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 无人机在物品、实体与站内槽位之间往返的完整数据。字段顺序进入存档与网络后保持稳定,
 * 后续 TODO 需要的任务恢复字段只允许追加,不允许重排或删除。
 *
 * @param toolId              已安装工具定义 ID
 * @param leftPropeller       左螺旋桨的完整物品堆,保留玩家制作的模型、颜色与材质
 * @param rightPropeller      右螺旋桨的完整物品堆,不与左桨合并成平均外观
 * @param energy              内部 FE;容量与充电语义由 TODO 02 落实
 * @param owner               制造或放置玩家
 * @param shortageStrategy    缺料与缺拆除能力策略
 * @param collectionInventory 收集无人机的物品库存;其余工种保持空列表
 */
public record DroneData(
    ResourceLocation toolId,
    ItemStack leftPropeller,
    ItemStack rightPropeller,
    int energy,
    Optional<UUID> owner,
    DroneShortageStrategy shortageStrategy,
    List<ItemStack> collectionInventory
) {
    public static final Codec<DroneData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ResourceLocation.CODEC.fieldOf("tool_id").forGetter(DroneData::toolId),
        ItemStack.OPTIONAL_CODEC.optionalFieldOf("left_propeller", ItemStack.EMPTY)
            .forGetter(DroneData::leftPropeller),
        ItemStack.OPTIONAL_CODEC.optionalFieldOf("right_propeller", ItemStack.EMPTY)
            .forGetter(DroneData::rightPropeller),
        Codec.INT.optionalFieldOf("energy", 0).forGetter(DroneData::energy),
        UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(DroneData::owner),
        DroneShortageStrategy.CODEC.optionalFieldOf("shortage_strategy", DroneShortageStrategy.PAUSE)
            .forGetter(DroneData::shortageStrategy),
        ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("collection_inventory", List.of())
            .forGetter(DroneData::collectionInventory)
    ).apply(instance, DroneData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DroneData> STREAM_CODEC = StreamCodec.of(
        DroneData::encode,
        DroneData::decode
    );

    public DroneData {
        Objects.requireNonNull(toolId, "toolId");
        Objects.requireNonNull(leftPropeller, "leftPropeller");
        Objects.requireNonNull(rightPropeller, "rightPropeller");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(shortageStrategy, "shortageStrategy");
        collectionInventory = List.copyOf(collectionInventory);
    }

    /** 合成装配时的初始数据;所有者在放置时补写。 */
    public static DroneData assembled(ResourceLocation toolId, ItemStack leftPropeller, ItemStack rightPropeller) {
        return new DroneData(
            toolId,
            leftPropeller,
            rightPropeller,
            0,
            Optional.empty(),
            DroneShortageStrategy.PAUSE,
            List.of()
        );
    }

    public static Optional<DroneData> get(ItemStack stack) {
        return Optional.ofNullable(stack.get(PlasticraftDataComponents.DRONE_DATA.get()));
    }

    public static void set(ItemStack stack, DroneData data) {
        stack.set(PlasticraftDataComponents.DRONE_DATA.get(), data);
    }

    public DroneData withOwner(UUID ownerId) {
        return new DroneData(
            this.toolId,
            this.leftPropeller,
            this.rightPropeller,
            this.energy,
            Optional.of(ownerId),
            this.shortageStrategy,
            this.collectionInventory
        );
    }

    public DroneData withEnergy(int newEnergy) {
        return new DroneData(
            this.toolId,
            this.leftPropeller,
            this.rightPropeller,
            newEnergy,
            this.owner,
            this.shortageStrategy,
            this.collectionInventory
        );
    }

    private static void encode(RegistryFriendlyByteBuf buffer, DroneData data) {
        ResourceLocation.STREAM_CODEC.encode(buffer, data.toolId);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, data.leftPropeller);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, data.rightPropeller);
        buffer.writeVarInt(data.energy);
        ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).encode(buffer, data.owner);
        DroneShortageStrategy.STREAM_CODEC.encode(buffer, data.shortageStrategy);
        ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buffer, data.collectionInventory);
    }

    private static DroneData decode(RegistryFriendlyByteBuf buffer) {
        ResourceLocation toolId = ResourceLocation.STREAM_CODEC.decode(buffer);
        ItemStack leftPropeller = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
        ItemStack rightPropeller = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
        int energy = buffer.readVarInt();
        Optional<UUID> owner = ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buffer);
        DroneShortageStrategy strategy = DroneShortageStrategy.STREAM_CODEC.decode(buffer);
        List<ItemStack> inventory = ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buffer);
        return new DroneData(toolId, leftPropeller, rightPropeller, energy, owner, strategy, inventory);
    }
}
