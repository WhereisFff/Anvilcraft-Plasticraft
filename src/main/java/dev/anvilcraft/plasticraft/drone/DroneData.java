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
 * @param assignedJobId       当前任务租约;无任务时为空
 * @param hostedCarry         建设无人机的任务托管携带物,不算普通库存
 */
public record DroneData(
    ResourceLocation toolId,
    ItemStack leftPropeller,
    ItemStack rightPropeller,
    int energy,
    Optional<UUID> owner,
    DroneShortageStrategy shortageStrategy,
    List<ItemStack> collectionInventory,
    Optional<UUID> assignedJobId,
    ItemStack hostedCarry
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
            .forGetter(DroneData::collectionInventory),
        UUIDUtil.CODEC.optionalFieldOf("assigned_job_id").forGetter(DroneData::assignedJobId),
        ItemStack.OPTIONAL_CODEC.optionalFieldOf("hosted_carry", ItemStack.EMPTY)
            .forGetter(DroneData::hostedCarry)
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
        Objects.requireNonNull(assignedJobId, "assignedJobId");
        Objects.requireNonNull(hostedCarry, "hostedCarry");
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
            List.of(),
            Optional.empty(),
            ItemStack.EMPTY
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
            this.collectionInventory,
            this.assignedJobId,
            this.hostedCarry
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
            this.collectionInventory,
            this.assignedJobId,
            this.hostedCarry
        );
    }

    public DroneData withShortageStrategy(DroneShortageStrategy strategy) {
        return new DroneData(
            this.toolId,
            this.leftPropeller,
            this.rightPropeller,
            this.energy,
            this.owner,
            strategy,
            this.collectionInventory,
            this.assignedJobId,
            this.hostedCarry
        );
    }

    public DroneData withToolId(ResourceLocation newToolId) {
        return new DroneData(
            newToolId,
            this.leftPropeller,
            this.rightPropeller,
            this.energy,
            this.owner,
            this.shortageStrategy,
            this.collectionInventory,
            this.assignedJobId,
            this.hostedCarry
        );
    }

    public DroneData withCollectionInventory(List<ItemStack> inventory) {
        return new DroneData(
            this.toolId,
            this.leftPropeller,
            this.rightPropeller,
            this.energy,
            this.owner,
            this.shortageStrategy,
            inventory,
            this.assignedJobId,
            this.hostedCarry
        );
    }

    public DroneData withAssignment(Optional<UUID> jobId, ItemStack carry) {
        return new DroneData(
            this.toolId,
            this.leftPropeller,
            this.rightPropeller,
            this.energy,
            this.owner,
            this.shortageStrategy,
            this.collectionInventory,
            jobId,
            carry
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
        ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).encode(buffer, data.assignedJobId);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, data.hostedCarry);
    }

    private static DroneData decode(RegistryFriendlyByteBuf buffer) {
        ResourceLocation toolId = ResourceLocation.STREAM_CODEC.decode(buffer);
        ItemStack leftPropeller = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
        ItemStack rightPropeller = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
        int energy = buffer.readVarInt();
        Optional<UUID> owner = ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buffer);
        DroneShortageStrategy strategy = DroneShortageStrategy.STREAM_CODEC.decode(buffer);
        List<ItemStack> inventory = ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buffer);
        Optional<UUID> assignedJobId = ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buffer);
        ItemStack hostedCarry = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
        return new DroneData(
            toolId,
            leftPropeller,
            rightPropeller,
            energy,
            owner,
            strategy,
            inventory,
            assignedJobId,
            hostedCarry
        );
    }
}
