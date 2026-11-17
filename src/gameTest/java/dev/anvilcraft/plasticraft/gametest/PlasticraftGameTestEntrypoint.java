package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.testframework.Test;
import net.neoforged.testframework.conf.FrameworkConfiguration;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTestData;
import net.neoforged.testframework.gametest.StructureTemplateBuilder;
import net.neoforged.testframework.impl.MutableTestFramework;

import java.util.Objects;

/** 仅供测试使用的第二入口点，不包含在发布的主源码集中。 */
@Mod(PlasticraftGameTestEntrypoint.MOD_ID)
public final class PlasticraftGameTestEntrypoint {
    public static final String MOD_ID = AnvilcraftPlasticraft.MOD_ID + "_tests";

    public PlasticraftGameTestEntrypoint(IEventBus modEventBus, ModContainer container) {
        MutableTestFramework framework = FrameworkConfiguration.builder(AnvilcraftPlasticraft.of("tests"))
            .build()
            .create();
        framework.init(modEventBus, container);
        // 26.1 以结构起点为相对原点。既有场景约定地板在 y=1，站立面在 y=2。
        framework.tests().all().stream()
            .map(Test::asGameTest)
            .filter(Objects::nonNull)
            .map(GameTestData::structureName)
            .distinct()
            .map(Identifier::parse)
            .filter(id -> id.getNamespace().equals(AnvilcraftPlasticraft.MOD_ID)
                && id.getPath().startsWith("empty_") && id.getPath().endsWith("_floor"))
            .forEach(id -> {
                String path = id.getPath();
                EmptyTemplate.Size size = EmptyTemplate.Size.parse(path.substring("empty_".length(), path.length() - "_floor".length()));
                framework.dynamicStructures().register(id, () -> StructureTemplateBuilder.withSize(size.length(), size.height() + 2, size.width())
                    .fill(0, 1, 0, size.length() - 1, 1, size.width() - 1, Blocks.IRON_BLOCK.defaultBlockState())
                    .build());
            });
    }
}
