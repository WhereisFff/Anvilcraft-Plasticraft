package dev.anvilcraft.plasticraft.init;

import dev.anvilcraft.plasticraft.blueprint.EntityBuildAdapter;
import dev.anvilcraft.plasticraft.blueprint.EntityBuildAdapters;
import dev.anvilcraft.plasticraft.blueprint.FluidBuildAdapter;
import dev.anvilcraft.plasticraft.blueprint.StructureSnapshot;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.item.AbstractPlasticEntityItem;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContents;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.dubhe.anvilcraft.api.fluid.IFluidHandlerHolder;
import dev.dubhe.anvilcraft.api.itemhandler.IItemHandlerHolder;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.item.property.component.SavedEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.List;

/** 施工实体适配注册:船/矿车/盔甲架/画/展示框、刷怪蛋、树脂捕获、塑料实体。 */
public final class PlasticraftEntityBuildAdapters {
    private PlasticraftEntityBuildAdapters() {
    }

    public static void register() {
        EntityBuildAdapters.register(new VehicleAdapter());
        EntityBuildAdapters.register(new ArmorStandAdapter());
        EntityBuildAdapters.register(new HangingAdapter());
        EntityBuildAdapters.register(new SpawnEggAdapter());
        EntityBuildAdapters.register(new ResinCaptureAdapter());
        EntityBuildAdapters.register(new PlasticEntityAdapter());
    }

    public static boolean isResinCapture(ItemStack stack) {
        return stack.has(ModComponents.SAVED_ENTITY)
            && (stack.is(PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
                || stack.is(ModBlocks.RESIN_BLOCK.asItem())
                || stack.is(PlasticraftBlocks.RESIN_ANVIL.asItem()));
    }

    /** 只有树脂块 / 高粘块释放后掉 1–3 树脂;树脂铁砧整砧消耗。 */
    public static boolean returnsResin(ItemStack stack) {
        return stack.has(ModComponents.SAVED_ENTITY)
            && (stack.is(PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asItem())
                || stack.is(ModBlocks.RESIN_BLOCK.asItem()));
    }

    public static ItemStack resinReturn(ServerLevel level) {
        return new ItemStack(ModItems.RESIN.get(), 1 + level.random.nextInt(3));
    }

    private static CompoundTag saveSanitized(Entity entity) {
        CompoundTag tag = new CompoundTag();
        entity.save(tag);
        tag.remove("UUID");
        return tag;
    }

    private static void extractContainer(Container container, List<EntityBuildAdapter.SlotStack> contents) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            contents.add(new EntityBuildAdapter.SlotStack(slot, stack.copy()));
            container.setItem(slot, ItemStack.EMPTY);
        }
    }

    private static void extractHandler(IItemHandler handler, List<EntityBuildAdapter.SlotStack> contents) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            contents.add(new EntityBuildAdapter.SlotStack(slot, stack.copy()));
            if (handler instanceof IItemHandlerModifiable modifiable) {
                modifiable.setStackInSlot(slot, ItemStack.EMPTY);
            } else {
                handler.extractItem(slot, stack.getCount(), false);
            }
        }
    }

    private static final class VehicleAdapter implements EntityBuildAdapter {
        @Override
        public boolean matches(EntityType<?> type, CompoundTag nbt) {
            if (type == EntityType.BOAT || type == EntityType.CHEST_BOAT) {
                return true;
            }
            Class<? extends Entity> base = type.getBaseClass();
            return Boat.class.isAssignableFrom(base) || AbstractMinecart.class.isAssignableFrom(base);
        }

        @Override
        public Planned plan(ServerLevel level, StructureSnapshot.EntityEntry entry, CompoundTag transformedNbt) {
            Entity entity = EntityType.create(transformedNbt, level).orElse(null);
            List<SlotStack> contents = new ArrayList<>();
            ItemStack material = ItemStack.EMPTY;
            if (entity instanceof Container container) {
                extractContainer(container, contents);
            }
            if (entity instanceof Boat boat) {
                Item drop = boat.getDropItem();
                material = drop == null || drop == Items.AIR ? new ItemStack(Items.OAK_BOAT) : new ItemStack(drop);
            } else if (entity instanceof AbstractMinecart cart) {
                material = cart.getPickResult();
                if (material == null || material.isEmpty()) {
                    material = new ItemStack(Items.MINECART);
                }
            } else if (EntityType.by(transformedNbt).orElse(null) == EntityType.BOAT) {
                material = new ItemStack(Items.OAK_BOAT);
            } else if (EntityType.by(transformedNbt).orElse(null) == EntityType.CHEST_BOAT) {
                material = new ItemStack(Items.OAK_CHEST_BOAT);
            }
            if (material.isEmpty()) {
                return Planned.skip();
            }
            CompoundTag sanitized = entity == null ? transformedNbt.copy() : saveSanitized(entity);
            sanitized.putString("id", transformedNbt.getString("id"));
            return new Planned(material, ItemStack.EMPTY, sanitized, List.copyOf(contents), List.of(), false);
        }
    }

    private static final class ArmorStandAdapter implements EntityBuildAdapter {
        @Override
        public boolean matches(EntityType<?> type, CompoundTag nbt) {
            return type == EntityType.ARMOR_STAND;
        }

        @Override
        public Planned plan(ServerLevel level, StructureSnapshot.EntityEntry entry, CompoundTag transformedNbt) {
            Entity entity = EntityType.create(transformedNbt, level).orElse(null);
            if (!(entity instanceof ArmorStand stand)) {
                return Planned.skip();
            }
            List<SlotStack> contents = new ArrayList<>();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = stand.getItemBySlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                contents.add(new SlotStack(slot.ordinal(), stack.copy()));
                stand.setItemSlot(slot, ItemStack.EMPTY);
            }
            return new Planned(
                new ItemStack(Items.ARMOR_STAND),
                ItemStack.EMPTY,
                saveSanitized(stand),
                List.copyOf(contents),
                List.of(),
                false
            );
        }

        @Override
        public void insertContents(Entity entity, List<SlotStack> contents, HolderLookup.Provider registries) {
            if (!(entity instanceof ArmorStand stand)) {
                return;
            }
            EquipmentSlot[] slots = EquipmentSlot.values();
            for (SlotStack content : contents) {
                if (content.slot() >= 0 && content.slot() < slots.length) {
                    stand.setItemSlot(slots[content.slot()], content.stack().copy());
                }
            }
        }
    }

    private static final class HangingAdapter implements EntityBuildAdapter {
        @Override
        public boolean matches(EntityType<?> type, CompoundTag nbt) {
            return type == EntityType.ITEM_FRAME
                || type == EntityType.GLOW_ITEM_FRAME
                || type == EntityType.PAINTING;
        }

        @Override
        public Planned plan(ServerLevel level, StructureSnapshot.EntityEntry entry, CompoundTag transformedNbt) {
            Entity entity = EntityType.create(transformedNbt, level).orElse(null);
            if (entity == null) {
                return Planned.skip();
            }
            List<SlotStack> contents = new ArrayList<>();
            ItemStack material;
            if (entity instanceof ItemFrame frame) {
                material = new ItemStack(frame.getType() == EntityType.GLOW_ITEM_FRAME
                    ? Items.GLOW_ITEM_FRAME
                    : Items.ITEM_FRAME);
                ItemStack displayed = frame.getItem();
                if (!displayed.isEmpty()) {
                    contents.add(new SlotStack(0, displayed.copy()));
                    frame.setItem(ItemStack.EMPTY);
                }
            } else if (entity instanceof Painting) {
                material = new ItemStack(Items.PAINTING);
            } else {
                return Planned.skip();
            }
            return new Planned(material, ItemStack.EMPTY, saveSanitized(entity), List.copyOf(contents), List.of(), false);
        }

        @Override
        public void insertContents(Entity entity, List<SlotStack> contents, HolderLookup.Provider registries) {
            if (!(entity instanceof ItemFrame frame) || contents.isEmpty()) {
                return;
            }
            frame.setItem(contents.getFirst().stack().copy(), false);
        }
    }

    private static final class SpawnEggAdapter implements EntityBuildAdapter {
        @Override
        public boolean matches(EntityType<?> type, CompoundTag nbt) {
            return SpawnEggItem.byId(type) != null;
        }

        @Override
        public Planned plan(ServerLevel level, StructureSnapshot.EntityEntry entry, CompoundTag transformedNbt) {
            EntityType<?> type = EntityType.by(transformedNbt).orElse(null);
            SpawnEggItem egg = type == null ? null : SpawnEggItem.byId(type);
            if (egg == null) {
                return Planned.skip();
            }
            CompoundTag sanitized = new CompoundTag();
            sanitized.putString("id", transformedNbt.getString("id"));
            if (transformedNbt.contains("Pos", Tag.TAG_LIST)) {
                sanitized.put("Pos", transformedNbt.getList("Pos", Tag.TAG_DOUBLE).copy());
            }
            if (transformedNbt.contains("Rotation", Tag.TAG_LIST)) {
                sanitized.put("Rotation", transformedNbt.getList("Rotation", Tag.TAG_FLOAT).copy());
            }
            return new Planned(
                new ItemStack(egg),
                ItemStack.EMPTY,
                sanitized,
                List.of(),
                List.of(),
                false
            );
        }
    }

    private static final class ResinCaptureAdapter implements EntityBuildAdapter {
        @Override
        public boolean matches(EntityType<?> type, CompoundTag nbt) {
            return Mob.class.isAssignableFrom(type.getBaseClass()) && SpawnEggItem.byId(type) == null;
        }

        @Override
        public Planned plan(ServerLevel level, StructureSnapshot.EntityEntry entry, CompoundTag transformedNbt) {
            Entity entity = EntityType.create(transformedNbt, level).orElse(null);
            if (!(entity instanceof Mob)) {
                return Planned.skip();
            }
            ItemStack resin = new ItemStack(PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK);
            CompoundTag saved = transformedNbt.copy();
            saved.remove("UUID");
            resin.set(ModComponents.SAVED_ENTITY, new SavedEntity(saved, entity instanceof Monster));
            CompoundTag sanitized = new CompoundTag();
            sanitized.putString("id", transformedNbt.getString("id"));
            if (transformedNbt.contains("Pos", Tag.TAG_LIST)) {
                sanitized.put("Pos", transformedNbt.getList("Pos", Tag.TAG_DOUBLE).copy());
            }
            if (transformedNbt.contains("Rotation", Tag.TAG_LIST)) {
                sanitized.put("Rotation", transformedNbt.getList("Rotation", Tag.TAG_FLOAT).copy());
            }
            return new Planned(resin, ItemStack.EMPTY, sanitized, List.of(), List.of(), false);
        }

    }

    private static final class PlasticEntityAdapter implements EntityBuildAdapter {
        @Override
        public boolean matches(EntityType<?> type, CompoundTag nbt) {
            return type == PlasticraftEntities.HARDEND_RESIN_CAULDRON.get()
                || type == PlasticraftEntities.HARDEND_RESIN_ANVIL.get()
                || type == PlasticraftEntities.RESIN_ANVIL.get()
                || type == PlasticraftEntities.UNIVERSAL_PLASTIC.get()
                || type == PlasticraftEntities.CATALYTIC_PRESS_LID.get()
                || AbstractPlasticEntity.class.isAssignableFrom(type.getBaseClass());
        }

        @Override
        public Planned plan(ServerLevel level, StructureSnapshot.EntityEntry entry, CompoundTag transformedNbt) {
            Entity entity = EntityType.create(transformedNbt, level).orElse(null);
            if (!(entity instanceof AbstractPlasticEntity plastic)) {
                return Planned.skip();
            }
            List<SlotStack> contents = new ArrayList<>();
            List<FluidBuildAdapter.TankFluid> fluids = new ArrayList<>();
            if (plastic instanceof IItemHandlerHolder holder) {
                extractHandler(holder.getItemHandler(), contents);
            }
            IFluidHandler handler = fluidHandlerOf(plastic);
            if (handler != null) {
                for (int tank = 0; tank < handler.getTanks(); tank++) {
                    FluidStack fluid = handler.getFluidInTank(tank);
                    if (fluid.isEmpty()) {
                        continue;
                    }
                    fluids.add(new FluidBuildAdapter.TankFluid(tank, fluid.copy()));
                    handler.drain(fluid.copy(), IFluidHandler.FluidAction.EXECUTE);
                }
            }
            ItemStack material = plastic.getDropStack().copyWithCount(1);
            MoldedPlasticData.get(material).ifPresent(data -> {
                MoldedPlasticContents stored = data.contents();
                int tank = 0;
                for (FluidStack fluid : stored.fluids()) {
                    if (!fluid.isEmpty()) {
                        fluids.add(new FluidBuildAdapter.TankFluid(tank++, fluid.copy()));
                    }
                }
                MoldedPlasticData.set(material, data.withContents(new MoldedPlasticContents(
                    List.of(),
                    List.of(),
                    stored.trayComponents()
                )));
                plastic.setDropStack(material.copy());
            });
            if (material.isEmpty() || !(material.getItem() instanceof AbstractPlasticEntityItem<?>)) {
                return Planned.skip();
            }
            return new Planned(
                material,
                ItemStack.EMPTY,
                saveSanitized(plastic),
                List.copyOf(contents),
                List.copyOf(fluids),
                false
            );
        }

        @Override
        public void insertContents(Entity entity, List<SlotStack> contents, HolderLookup.Provider registries) {
            if (!(entity instanceof IItemHandlerHolder holder)) {
                EntityBuildAdapter.super.insertContents(entity, contents, registries);
                return;
            }
            IItemHandler handler = holder.getItemHandler();
            for (SlotStack content : contents) {
                if (content.slot() < 0 || content.slot() >= handler.getSlots()) {
                    continue;
                }
                if (handler instanceof IItemHandlerModifiable modifiable) {
                    modifiable.setStackInSlot(content.slot(), content.stack().copy());
                } else {
                    handler.insertItem(content.slot(), content.stack().copy(), false);
                }
            }
        }
    }

    @Nullable
    private static IFluidHandler fluidHandlerOf(Entity entity) {
        if (entity instanceof IFluidHandlerHolder holder) {
            return holder.getFluidHandler();
        }
        return entity.getCapability(Capabilities.FluidHandler.ENTITY, null);
    }
}
