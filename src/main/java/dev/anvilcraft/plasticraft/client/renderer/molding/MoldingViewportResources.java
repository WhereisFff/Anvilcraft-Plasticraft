package dev.anvilcraft.plasticraft.client.renderer.molding;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class MoldingViewportResources implements ResourceManagerReloadListener {
    public static final MoldingViewportResources INSTANCE = new MoldingViewportResources();
    private final List<WeakReference<MoldingViewportRenderer>> backends = new ArrayList<>();

    private MoldingViewportResources() {
    }

    synchronized void register(MoldingViewportRenderer backend) {
        purge();
        this.backends.add(new WeakReference<>(backend));
    }

    synchronized void unregister(MoldingViewportRenderer backend) {
        this.backends.removeIf(reference -> reference.get() == null || reference.get() == backend);
    }

    public synchronized void closeAll() {
        List<WeakReference<MoldingViewportRenderer>> references = List.copyOf(this.backends);
        this.backends.clear();
        for (WeakReference<MoldingViewportRenderer> reference : references) {
            MoldingViewportRenderer backend = reference.get();
            if (backend != null) backend.close();
        }
    }

    @Override
    public synchronized void onResourceManagerReload(ResourceManager resourceManager) {
        for (WeakReference<MoldingViewportRenderer> reference : this.backends) {
            MoldingViewportRenderer backend = reference.get();
            if (backend != null) backend.reloadResources();
        }
        purge();
    }

    private void purge() {
        Iterator<WeakReference<MoldingViewportRenderer>> iterator = this.backends.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().get() == null) iterator.remove();
        }
    }
}
