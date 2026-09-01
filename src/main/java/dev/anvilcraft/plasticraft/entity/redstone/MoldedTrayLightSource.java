package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponentPlacement;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** 将支架元件的原生方块发光等级映射到其当前世界位置。 */
public final class MoldedTrayLightSource {
    private static final Map<Level, Network> NETWORKS = new WeakHashMap<>();

    private MoldedTrayLightSource() {
    }

    public static void update(UniversalPlasticEntity host) {
        apply(host, collect(host));
    }

    public static void remove(UniversalPlasticEntity host) {
        apply(host, Map.of());
    }

    public static int lightAt(BlockGetter getter, BlockPos position) {
        if (!(getter instanceof Level level)) return 0;
        Network network;
        synchronized (NETWORKS) {
            network = NETWORKS.get(level);
        }
        return network == null ? 0 : network.lightLevels.getOrDefault(position, 0);
    }

    @SuppressWarnings("deprecation")
    public static int emission(BlockState state) {
        return Math.clamp(state.getLightEmission(), 0, 15);
    }

    private static Map<BlockPos, Integer> collect(UniversalPlasticEntity host) {
        MoldedPlasticData data = host.getMoldedData()
            .filter(value -> MoldingProductTypes.isTray(value.finalType()))
            .orElse(null);
        if (data == null) return Map.of();

        Map<BlockPos, Integer> lights = new HashMap<>();
        for (MoldedTrayComponentPlacement placement : data.contents().trayComponents()) {
            int light = emission(placement.component().state());
            if (light <= 0) continue;
            BlockPos position = MoldedTrayRedstoneNetwork.componentPosition(host, placement.cell()).immutable();
            lights.merge(position, light, Math::max);
        }
        return Map.copyOf(lights);
    }

    private static void apply(UniversalPlasticEntity host, Map<BlockPos, Integer> lights) {
        Level level = host.level();
        Set<BlockPos> affected = new HashSet<>();
        synchronized (NETWORKS) {
            Network network = NETWORKS.get(level);
            if (network == null) {
                if (lights.isEmpty()) return;
                network = new Network();
                NETWORKS.put(level, network);
            }
            network.removeStaleEntries(affected);

            UUID hostId = host.getUUID();
            Entry previous = network.entries.get(hostId);
            if (lights.isEmpty()) {
                if (previous != null && previous.belongsTo(host)) {
                    network.entries.remove(hostId);
                    affected.addAll(previous.lights.keySet());
                }
            } else if (previous == null || !previous.matches(host, lights)) {
                if (previous != null) affected.addAll(previous.lights.keySet());
                Entry replacement = new Entry(new WeakReference<>(host), lights);
                network.entries.put(hostId, replacement);
                affected.addAll(replacement.lights.keySet());
            }

            if (affected.isEmpty()) return;
            network.recalculate(affected);
            if (network.entries.isEmpty() && network.lightLevels.isEmpty()) NETWORKS.remove(level);
        }
        for (BlockPos position : affected) level.getLightEngine().checkBlock(position);
    }

    private static final class Network {
        private final Map<UUID, Entry> entries = new HashMap<>();
        private final Map<BlockPos, Integer> lightLevels = new ConcurrentHashMap<>();

        private void removeStaleEntries(Set<BlockPos> affected) {
            Iterator<Entry> iterator = this.entries.values().iterator();
            while (iterator.hasNext()) {
                Entry entry = iterator.next();
                if (entry.owner.get() != null) continue;
                affected.addAll(entry.lights.keySet());
                iterator.remove();
            }
        }

        private void recalculate(Set<BlockPos> affected) {
            for (BlockPos position : affected) {
                int light = 0;
                for (Entry entry : this.entries.values()) {
                    light = Math.max(light, entry.lights.getOrDefault(position, 0));
                }
                if (light > 0) this.lightLevels.put(position, light);
                else this.lightLevels.remove(position);
            }
        }
    }

    private record Entry(
        WeakReference<UniversalPlasticEntity> owner,
        Map<BlockPos, Integer> lights
    ) {
        private Entry {
            lights = Map.copyOf(lights);
        }

        private boolean belongsTo(UniversalPlasticEntity host) {
            return this.owner.get() == host;
        }

        private boolean matches(UniversalPlasticEntity host, Map<BlockPos, Integer> currentLights) {
            return this.belongsTo(host) && this.lights.equals(currentLights);
        }
    }
}
