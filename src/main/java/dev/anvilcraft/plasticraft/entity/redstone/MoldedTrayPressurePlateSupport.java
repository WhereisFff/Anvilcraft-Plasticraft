package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponentGeometry;
import dev.dubhe.anvilcraft.block.entity.plate.TimeCountedPressurePlateBlockEntity;
import dev.dubhe.anvilcraft.block.plate.PowerLevelPressurePlateBlock;
import dev.dubhe.anvilcraft.block.plate.TimeCountedPressurePlateBlock;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** 在支架的真实朝向与位置上复现压力板的实体检测算法。 */
final class MoldedTrayPressurePlateSupport {
    private static final double TOUCH_INSET = 1.0D / 16.0D;
    private static final double TOUCH_HEIGHT = 4.0D / 16.0D;
    private static final TagKey<EntityType<?>> UNDEAD = TagKey.create(
        Registries.ENTITY_TYPE,
        ResourceLocation.withDefaultNamespace("undead")
    );

    private MoldedTrayPressurePlateSupport() {
    }

    static boolean isPressurePlate(BlockState state) {
        return state.getBlock() instanceof BasePressurePlateBlock
            || state.is(BlockTags.PRESSURE_PLATES);
    }

    static int currentSignal(BlockState state) {
        if (state.hasProperty(BlockStateProperties.POWER)) {
            return state.getValue(BlockStateProperties.POWER);
        }
        return state.hasProperty(BlockStateProperties.POWERED)
            && state.getValue(BlockStateProperties.POWERED) ? 15 : 0;
    }

    static BlockState withSignal(BlockState state, int signal) {
        int clamped = Math.clamp(signal, 0, 15);
        BlockState result = state;
        if (result.hasProperty(BlockStateProperties.POWER)) {
            result = result.setValue(BlockStateProperties.POWER, clamped);
        }
        if (result.hasProperty(BlockStateProperties.POWERED)) {
            result = result.setValue(BlockStateProperties.POWERED, clamped > 0);
        }
        return result;
    }

    static int pressedTime(BlockState state) {
        if (state.getBlock() instanceof TimeCountedPressurePlateBlock) return 1;
        return state.getBlock() instanceof WeightedPressurePlateBlock ? 10 : 20;
    }

    static int expectedSignal(MoldedTrayRedstoneRuntime runtime, BlockState state, BlockEntity blockEntity) {
        UniversalPlasticEntity host = runtime.host();
        if (state.getBlock() instanceof TimeCountedPressurePlateBlock timePlate
            && blockEntity instanceof TimeCountedPressurePlateBlockEntity timeCounter) {
            return tickTimeCounted(runtime, timePlate, timeCounter);
        }
        AABB bounds = sensitiveBounds(runtime, false);
        Block block = state.getBlock();
        if (block == Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE) {
            return weightedSignal(entityCount(host, bounds, Entity.class, MoldedTrayPressurePlateSupport::canTrigger), 15);
        }
        if (block == Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE) {
            return weightedSignal(entityCount(host, bounds, Entity.class, MoldedTrayPressurePlateSupport::canTrigger), 150);
        }
        if (block instanceof PressurePlateBlock) {
            Class<? extends Entity> type = block == Blocks.STONE_PRESSURE_PLATE
                || block == Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE
                ? LivingEntity.class
                : Entity.class;
            return entityCount(host, bounds, type, MoldedTrayPressurePlateSupport::canTrigger) > 0 ? 15 : 0;
        }
        if (block instanceof PowerLevelPressurePlateBlock) {
            return anvilcraftSignal(host, state, bounds);
        }
        return entityCount(host, bounds, Entity.class, MoldedTrayPressurePlateSupport::canTrigger) > 0 ? 15 : 0;
    }

    private static int tickTimeCounted(
        MoldedTrayRedstoneRuntime runtime,
        TimeCountedPressurePlateBlock plate,
        TimeCountedPressurePlateBlockEntity counter
    ) {
        UniversalPlasticEntity host = runtime.host();
        AABB bounds = sensitiveBounds(runtime, true);
        boolean occupied = !host.level().getEntitiesOfClass(
            LivingEntity.class,
            bounds,
            entity -> true
        ).isEmpty();
        CompoundTag data = counter.saveWithoutMetadata(host.registryAccess());
        int ticks = data.getInt("tick");
        int maximum = Math.max(0, plate.needTick * 15);
        if (occupied && ticks < maximum) ticks++;
        else if (!occupied && ticks > 0) ticks--;
        data.putInt("tick", ticks);
        data.putInt("NeedTick", plate.needTick);
        counter.loadWithComponents(data, host.registryAccess());
        counter.setLevel(host.level());
        counter.setChanged();
        return Math.clamp(ticks / (plate.needTick == 0 ? 1 : plate.needTick), 0, 15);
    }

    private static int anvilcraftSignal(UniversalPlasticEntity host, BlockState state, AABB bounds) {
        Block block = state.getBlock();
        if (block == ModBlocks.TUNGSTEN_PRESSURE_PLATE.get()) {
            return Math.clamp(entityCount(
                host,
                bounds,
                LivingEntity.class,
                entity -> canTrigger(entity) && entity.fireImmune()
            ) + entityCount(
                host,
                bounds,
                ItemEntity.class,
                entity -> canTrigger(entity) && entity.fireImmune()
            ), 0, 15);
        }
        if (block == ModBlocks.TITANIUM_PRESSURE_PLATE.get()) {
            return durabilitySignal(host, bounds, false);
        }
        if (block == ModBlocks.ZINC_PRESSURE_PLATE.get()) {
            return healthSignal(host, bounds, false);
        }
        if (block == ModBlocks.TIN_PRESSURE_PLATE.get()) {
            return healthSignal(host, bounds, true);
        }
        if (block == ModBlocks.LEAD_PRESSURE_PLATE.get()) {
            Set<Class<?>> types = new HashSet<>();
            for (LivingEntity entity : entities(host, bounds, LivingEntity.class, MoldedTrayPressurePlateSupport::canTrigger)) {
                types.add(entity.getClass());
            }
            return Math.clamp(types.size(), 0, 15);
        }
        if (block == ModBlocks.SILVER_PRESSURE_PLATE.get()) {
            return Math.clamp(entityCount(
                host,
                bounds,
                Entity.class,
                entity -> entity.getType().is(UNDEAD)
            ), 0, 15);
        }
        if (block == ModBlocks.URANIUM_PRESSURE_PLATE.get()) {
            return durabilitySignal(host, bounds, true);
        }
        if (block == ModBlocks.PLUTONIUM_PRESSURE_PLATE.get()) {
            List<Player> players = entities(host, bounds, Player.class, MoldedTrayPressurePlateSupport::canTrigger);
            if (players.isEmpty()) return 0;
            ItemStack held = players.getFirst().getItemInHand(InteractionHand.MAIN_HAND);
            if (held.isEmpty()) return 0;
            if (!held.isDamageableItem()) return 15;
            int remaining = held.getMaxDamage() - held.getDamageValue();
            return Math.clamp(Math.max(1, remaining * 15 / held.getMaxDamage()), 0, 15);
        }
        if (block == ModBlocks.BRASS_PRESSURE_PLATE.get()) {
            float maximum = 0.0F;
            for (Player player : entities(host, bounds, Player.class, MoldedTrayPressurePlateSupport::canTrigger)) {
                Inventory inventory = player.getInventory();
                int occupied = 0;
                for (ItemStack stack : inventory.items) if (!stack.isEmpty()) occupied++;
                maximum = Math.max(maximum, occupied / (float) inventory.getContainerSize());
            }
            return Math.clamp((int) (maximum * 15.0F), 0, 15);
        }
        if (block == ModBlocks.BRONZE_PRESSURE_PLATE.get()) {
            float maximum = 0.0F;
            for (Player player : entities(host, bounds, Player.class, MoldedTrayPressurePlateSupport::canTrigger)) {
                maximum = Math.max(maximum, player.getFoodData().getFoodLevel() / 20.0F);
            }
            return Math.clamp((int) (maximum * 15.0F), 0, 15);
        }
        return entityCount(host, bounds, Entity.class, MoldedTrayPressurePlateSupport::canTrigger) > 0 ? 15 : 0;
    }

    private static int durabilitySignal(UniversalPlasticEntity host, AABB bounds, boolean useMinimum) {
        float selected = useMinimum ? Float.POSITIVE_INFINITY : 0.0F;
        boolean found = false;
        for (ItemEntity entity : entities(host, bounds, ItemEntity.class, MoldedTrayPressurePlateSupport::canTrigger)) {
            ItemStack stack = entity.getItem();
            float durability = stack.getMaxDamage() == 0
                ? 1.0F
                : (stack.getMaxDamage() - stack.getDamageValue()) / (float) stack.getMaxDamage();
            selected = useMinimum ? Math.min(selected, durability) : Math.max(selected, durability);
            found = true;
        }
        return found ? Math.clamp((int) (selected * 15.0F), 0, 15) : 0;
    }

    private static int healthSignal(UniversalPlasticEntity host, AABB bounds, boolean useMinimum) {
        float selected = useMinimum ? Float.POSITIVE_INFINITY : 0.0F;
        boolean found = false;
        for (LivingEntity entity : entities(host, bounds, LivingEntity.class, MoldedTrayPressurePlateSupport::canTrigger)) {
            float health = entity.getHealth() / entity.getMaxHealth();
            selected = useMinimum ? Math.min(selected, health) : Math.max(selected, health);
            found = true;
        }
        return found ? Math.clamp((int) (selected * 15.0F), 0, 15) : 0;
    }

    private static int weightedSignal(int count, int maximum) {
        if (count <= 0) return 0;
        return Math.clamp((int) Math.ceil(Math.min(count, maximum) * 15.0D / maximum), 0, 15);
    }

    private static boolean canTrigger(Entity entity) {
        return !entity.isIgnoringBlockTriggers();
    }

    private static <T extends Entity> int entityCount(
        UniversalPlasticEntity host,
        AABB bounds,
        Class<T> type,
        Predicate<? super T> predicate
    ) {
        return entities(host, bounds, type, predicate).size();
    }

    private static <T extends Entity> List<T> entities(
        UniversalPlasticEntity host,
        AABB bounds,
        Class<T> type,
        Predicate<? super T> predicate
    ) {
        return host.level().getEntitiesOfClass(
            type,
            bounds,
            entity -> entity != host && !entity.isSpectator() && predicate.test(entity)
        );
    }

    private static AABB sensitiveBounds(MoldedTrayRedstoneRuntime runtime, boolean fullCube) {
        UniversalPlasticEntity host = runtime.host();
        MoldedPlasticData data = host.getMoldedData().orElseThrow();
        AABB component = MoldedTrayComponentGeometry.localBounds(data, runtime.cell());
        AABB local = fullCube ? component : new AABB(
            component.minX + TOUCH_INSET,
            component.minY,
            component.minZ + TOUCH_INSET,
            component.maxX - TOUCH_INSET,
            component.minY + TOUCH_HEIGHT,
            component.maxZ - TOUCH_INSET
        );
        return worldBounds(host, local);
    }

    static AABB worldBounds(UniversalPlasticEntity host, AABB local) {
        Vec3 minimum = host.plasticraft$getGeometry().worldPointAt(
            host.position(),
            host.getOrientation(),
            new Vec3(local.minX, local.minY, local.minZ)
        );
        Vec3 maximum = host.plasticraft$getGeometry().worldPointAt(
            host.position(),
            host.getOrientation(),
            new Vec3(local.maxX, local.maxY, local.maxZ)
        );
        return new AABB(
            Math.min(minimum.x, maximum.x),
            Math.min(minimum.y, maximum.y),
            Math.min(minimum.z, maximum.z),
            Math.max(minimum.x, maximum.x),
            Math.max(minimum.y, maximum.y),
            Math.max(minimum.z, maximum.z)
        );
    }
}
