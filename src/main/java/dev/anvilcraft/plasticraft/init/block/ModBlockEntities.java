package dev.anvilcraft.plasticraft.init.block;

import dev.anvilcraft.lib.v2.registrum.util.entry.BlockEntityEntry;
import dev.anvilcraft.plasticraft.block.entity.CondenserTowerBlockEntity;

import static dev.anvilcraft.plasticraft.AnvilcraftPlasticraft.REGISTRUM;

/** Plasticraft 方块实体注册。 */
public final class ModBlockEntities {
    public static final BlockEntityEntry<CondenserTowerBlockEntity> CONDENSER_TOWER = REGISTRUM
        .blockEntity("condenser_tower", CondenserTowerBlockEntity::new)
        .validBlock(ModBlocks.CONDENSER_TOWER)
        .register();

    private ModBlockEntities() {
    }

    public static void register() {
        // 类加载时静态条目会挂接到 Registrum 事件总线。
    }
}
