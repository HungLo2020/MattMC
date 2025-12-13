package net.fabricmc.fabric.api.event;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/**
 * Fabric API stub for Event
 */
public class Event<T> {
    public static final ResourceLocation DEFAULT_PHASE = ResourceLocation.fromNamespaceAndPath("fabric", "default");
    
    private final Class<T> type;
    private final Function<T[], T> invokerFactory;
    private final List<T> listeners = new ArrayList<>();
    private T invoker;
    
    @SuppressWarnings("unchecked")
    private Event(Class<T> type, Function<T[], T> invokerFactory) {
        this.type = type;
        this.invokerFactory = invokerFactory;
        this.invoker = invokerFactory.apply((T[]) Array.newInstance(type, 0));
    }
    
    public static <T> Event<T> create(Class<T> type, Function<T[], T> invokerFactory) {
        return new Event<>(type, invokerFactory);
    }
    
    @SuppressWarnings("unchecked")
    public void register(T listener) {
        listeners.add(listener);
        invoker = invokerFactory.apply(listeners.toArray((T[]) Array.newInstance(type, 0)));
    }
    
    @SuppressWarnings("unchecked")
    public void register(ResourceLocation phase, T listener) {
        // Phase ordering is a no-op in this stub
        register(listener);
    }
    
    public void addPhaseOrdering(ResourceLocation first, ResourceLocation second) {
        // Phase ordering is a no-op in this stub
    }
    
    public T invoker() {
        return invoker;
    }
}
