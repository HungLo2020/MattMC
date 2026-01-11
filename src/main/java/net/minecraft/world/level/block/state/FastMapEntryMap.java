package net.minecraft.world.level.block.state;

import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * FerriteCore optimization #3: Property map implementation that computes values on-demand from FastMap.
 * Memory savings: ~170 MB by avoiding ImmutableMap storage for each blockstate
 */
public class FastMapEntryMap<S> implements Reference2ObjectMap<Property<?>, Comparable<?>> {
    private final StateHolder<?, S> viewedState;

    public FastMapEntryMap(StateHolder<?, S> viewedState) {
        this.viewedState = viewedState;
    }

    @Override
    public int size() {
        return viewedState.getFastMap() == null ? 0 : viewedState.getFastMap().numProperties();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    @Override
    public boolean containsValue(Object value) {
        for (Property<?> key : keySet()) {
            if (Objects.equals(value, get(key))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Comparable<?> get(@Nullable Object key) {
        if (viewedState.getFastMap() == null) {
            return null;
        }
        return viewedState.getFastMap().getValue(viewedState.getStateIndex(), key);
    }

    @NotNull
    @Override
    public ReferenceSet<Property<?>> keySet() {
        if (viewedState.getFastMap() == null) {
            return new ReferenceOpenHashSet<>();
        }
        return viewedState.getFastMap().getPropertySet();
    }

    @NotNull
    @Override
    public ObjectCollection<Comparable<?>> values() {
        ObjectList<Comparable<?>> values = new ObjectArrayList<>();
        for (Property<?> key : keySet()) {
            values.add(get(key));
        }
        return values;
    }

    @Override
    public void putAll(@NotNull Map<? extends Property<?>, ? extends Comparable<?>> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void defaultReturnValue(Comparable<?> comparable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Comparable<?> defaultReturnValue() {
        return null;
    }

    @Override
    public ObjectSet<Entry<Property<?>, Comparable<?>>> reference2ObjectEntrySet() {
        ObjectSet<Entry<Property<?>, Comparable<?>>> entries = new ObjectArraySet<>();
        for (Property<?> key : keySet()) {
            entries.add(new AbstractReference2ObjectMap.BasicEntry<>(key, get(key)));
        }
        return entries;
    }
}
