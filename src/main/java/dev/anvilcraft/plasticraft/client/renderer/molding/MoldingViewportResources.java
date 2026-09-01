package dev.anvilcraft.plasticraft.client.renderer.molding;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class MoldingViewportResources implements ResourceManagerReloadListener {
    public static final MoldingViewportResources INSTANCE = new MoldingViewportResources();
    private final List<WeakReference<MoldingViewportBackend1211>> backends = new ArrayList<>();

    private MoldingViewportResources() {
    }

    synchronized void register(MoldingViewportBackend1211 backend) {
        purge();
        this.backends.add(new WeakReference<>(backend));
    }

    synchronized void unregister(MoldingViewportBackend1211 backend) {
        this.backends.removeIf(reference -> reference.get() == null || reference.get() == backend);
    }

    public synchronized void closeAll() {
        List<WeakReference<MoldingViewportBackend1211>> references = List.copyOf(this.backends);
        this.backends.clear();
        for (WeakReference<MoldingViewportBackend1211> reference : references) {
            MoldingViewportBackend1211 backend = reference.get();
            if (backend != null) backend.close();
        }
    }

    @Override
    public synchronized void onResourceManagerReload(ResourceManager resourceManager) {
        for (WeakReference<MoldingViewportBackend1211> reference : this.backends) {
            MoldingViewportBackend1211 backend = reference.get();
            if (backend != null) backend.reloadResources();
        }
        purge();
    }

    private void purge() {
        Iterator<WeakReference<MoldingViewportBackend1211>> iterator = this.backends.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().get() == null) iterator.remove();
        }
    }
}
