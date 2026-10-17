package net.vulkanic.world;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.util.NativeLibraryLoader;

/** Rust owns ordered numeric membership and history. This adapter retains only
 * immutable payload references indexed by native slots. Caller holds the render
 * producer lock, including while traversing the read-only collection views. */
final class WorldAssetRegistry<K extends Number, V> extends AbstractMap<K, V> {
    private static MethodHandle bind(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_asset_registry_" + name, descriptor);
    }
    private static final MethodHandle CREATE = bind("create", FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = bind("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle FIND = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_asset_registry_find",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG), Linker.Option.critical(false));
    private static final MethodHandle PUT = bind("put", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle REMOVE = bind("remove", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle CLEAR = bind("clear", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle RESET = bind("reset", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle ADDED = bind("added", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle ADDED_DATA = bind("added_data", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle PROOF_CREATE = bind("proof_create", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle PROOF_RELEASE = bind("proof_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle PROOF_VALID = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_asset_registry_proof_valid",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS), Linker.Option.critical(false));
    private static final MethodHandle PROOF_REFRESH = bind("proof_refresh",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private final Class<K> keyType;
    private final MemorySegment owner;
    private MemorySegment slots = MemorySegment.NULL;
    private final ArrayList<V> payloads = new ArrayList<>();

    WorldAssetRegistry(Class<K> keyType) {
        if (keyType != Long.class && keyType != Integer.class) throw new IllegalArgumentException("asset keys must be long or int");
        this.keyType = keyType;
        try {
            var pointer = (MemorySegment)CREATE.invokeExact();
            try { owner = pointer.reinterpret(40, Arena.ofAuto(), WorldAssetRegistry::release); }
            catch (Throwable failure) { release(pointer); throw failure; }
        } catch (Throwable failure) { throw failed(failure); }
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); } catch (Throwable failure) { throw failed(failure); }
    }
    private static RuntimeException failed(Throwable failure) {
        if (failure instanceof RuntimeException runtime) return runtime;
        if (failure instanceof Error error) throw error;
        return new IllegalStateException("Native asset registry call failed", failure);
    }
    private void refreshSlots() {
        long pointer = owner.get(ValueLayout.JAVA_LONG, 32);
        long bytes = (long)owner.get(ValueLayout.JAVA_INT, 28) * 24;
        if (slots.address() != pointer || slots.byteSize() != bytes) slots = MemorySegment.ofAddress(pointer).reinterpret(bytes);
    }
    private int find(long key) {
        try { return (int)FIND.invokeExact(owner, key); }
        catch (Throwable failure) { throw failed(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    public V get(long key) { int slot = find(key); return slot < 0 ? null : payloads.get(slot); }
    @Override public V get(Object key) { return keyType.isInstance(key) ? get(((Number)key).longValue()) : null; }
    public boolean containsKey(long key) { return find(key) >= 0; }
    @Override public boolean containsKey(Object key) { return keyType.isInstance(key) && containsKey(((Number)key).longValue()); }
    @Override public int size() {
        try { return owner.get(ValueLayout.JAVA_INT, 12); }
        finally { Reference.reachabilityFence(this); }
    }
    public V put(long key, V value) {
        if (keyType == Integer.class && (int)key != key) throw new IllegalArgumentException("texture key outside int range");
        try {
            int previous = find(key);
            if (previous >= 0 && payloads.get(previous) == value) return value;
            // Reserve the projection before native membership changes.
            payloads.ensureCapacity(owner.get(ValueLayout.JAVA_INT, 28) + 1);
            int slot = (int)PUT.invokeExact(owner, key);
            refreshSlots();
            if (slot == payloads.size()) payloads.add(null);
            return payloads.set(slot, value);
        } catch (Throwable failure) { throw failed(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    @Override public V put(K key, V value) { return put(keyType.cast(Objects.requireNonNull(key)).longValue(), value); }
    public V remove(long key) {
        try {
            int slot = (int)REMOVE.invokeExact(owner, key);
            return slot < 0 ? null : payloads.set(slot, null);
        } catch (Throwable failure) { throw failed(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    @Override public V remove(Object key) { return keyType.isInstance(key) ? remove(((Number)key).longValue()) : null; }
    @Override public void clear() {
        try { CLEAR.invokeExact(owner); payloads.clear(); refreshSlots(); }
        catch (Throwable failure) { throw failed(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    MembershipJournalMark mark() {
        try { return new MembershipJournalMark(owner.get(ValueLayout.JAVA_LONG, 0), owner.get(ValueLayout.JAVA_INT, 8)); }
        finally { Reference.reachabilityFence(this); }
    }
    void resetJournal() {
        try { RESET.invokeExact(owner); }
        catch (Throwable failure) { throw failed(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    private K key(long key) {
        return keyType == Integer.class ? keyType.cast(Integer.valueOf((int)key)) : keyType.cast(Long.valueOf(key));
    }
    List<K> keysAddedSince(MembershipJournalMark mark) {
        try {
            if (mark.epoch() == owner.get(ValueLayout.JAVA_LONG, 0) && mark.position() < 0)
                throw new IndexOutOfBoundsException("negative membership journal position");
            int count = (int)ADDED.invokeExact(owner, mark.epoch(), mark.position());
            if (count < 0) throw new IllegalStateException("registry checkpoint predates the current membership journal");
            if (count == 0) return List.of();
            var data = ((MemorySegment)ADDED_DATA.invokeExact(owner)).reinterpret((long)count * 8);
            var result = new ArrayList<K>(count);
            for (int i = 0; i < count; i++) result.add(key(data.get(ValueLayout.JAVA_LONG, (long)i * 8)));
            return result;
        } catch (Throwable failure) { throw failed(failure); }
        finally { Reference.reachabilityFence(this); }
    }
    private abstract class OrderedIterator<T> implements Iterator<T> {
        private int next = owner.get(ValueLayout.JAVA_INT, 24);
        private final long version = owner.get(ValueLayout.JAVA_LONG, 16);
        @Override public boolean hasNext() { return next != -1; }
        @Override public T next() {
            try {
                if (version != owner.get(ValueLayout.JAVA_LONG, 16)) throw new ConcurrentModificationException();
                if (next == -1) throw new NoSuchElementException();
                int current = next; next = slots.get(ValueLayout.JAVA_INT, (long)current * 24 + 8);
                return project(current);
            } finally { Reference.reachabilityFence(WorldAssetRegistry.this); }
        }
        abstract T project(int slot);
    }
    private final Set<Entry<K, V>> entries = new AbstractSet<>() {
        @Override public int size() { return WorldAssetRegistry.this.size(); }
        @Override public Iterator<Entry<K, V>> iterator() {
            return new OrderedIterator<>() {
                @Override Entry<K, V> project(int slot) {
                    return new SimpleImmutableEntry<>(key(slots.get(ValueLayout.JAVA_LONG, (long)slot * 24)), payloads.get(slot));
                }
            };
        }
    };
    private final Collection<V> values = new AbstractCollection<>() {
        @Override public int size() { return WorldAssetRegistry.this.size(); }
        @Override public Iterator<V> iterator() {
            return new OrderedIterator<>() { @Override V project(int slot) { return payloads.get(slot); } };
        }
    };
    @Override public Set<Entry<K, V>> entrySet() { return entries; }
    @Override public Collection<V> values() { return values; }

    /** Dependency proof over primary slots. Replacements and slot reuse retire
     * it, while mutations of unrelated assets preserve reuse. No payload copy. */
    static final class Proof {
        private final WorldAssetRegistry<?, ?> meshes, textures;
        private final MemorySegment owner;
        private Proof(WorldAssetRegistry<?, ?> meshes, WorldAssetRegistry<?, ?> textures, MemorySegment pointer) {
            this.meshes = meshes; this.textures = textures;
            try { owner = pointer.reinterpret(0, Arena.ofAuto(), Proof::release); }
            catch (Throwable failure) { release(pointer); throw failed(failure); }
        }
        static Proof capture(WorldAssetRegistry<?, ?> meshes, long[] meshKeys,
                             WorldAssetRegistry<?, ?> textures, long[] textureKeys) {
            try (var arena = Arena.ofConfined()) {
                var keys = arena.allocate(Math.max(1L, (long)meshKeys.length + textureKeys.length) * 8, 8);
                MemorySegment.copy(meshKeys, 0, keys, ValueLayout.JAVA_LONG, 0, meshKeys.length);
                MemorySegment.copy(textureKeys, 0, keys, ValueLayout.JAVA_LONG, (long)meshKeys.length * 8, textureKeys.length);
                var pointer = (MemorySegment)PROOF_CREATE.invokeExact(meshes.owner, textures.owner, keys, meshKeys.length, textureKeys.length);
                return pointer.address() == 0 ? null : new Proof(meshes, textures, pointer);
            } catch (Throwable failure) { throw failed(failure); }
            finally { Reference.reachabilityFence(meshes); Reference.reachabilityFence(textures); }
        }
        boolean valid() {
            try { return (int)PROOF_VALID.invokeExact(owner, meshes.owner, textures.owner) != 0; }
            catch (Throwable failure) { throw failed(failure); }
            finally { Reference.reachabilityFence(this); }
        }
        boolean refreshAfterAdmission() {
            try { return (int)PROOF_REFRESH.invokeExact(owner, meshes.owner, textures.owner) != 0; }
            catch (Throwable failure) { throw failed(failure); }
            finally { Reference.reachabilityFence(this); }
        }
        private static void release(MemorySegment pointer) {
            try { PROOF_RELEASE.invokeExact(pointer); } catch (Throwable failure) { throw failed(failure); }
        }
    }
    private UnsupportedOperationException mutation() { return new UnsupportedOperationException("registry membership changes through put/remove/clear"); }
    @Override public V putIfAbsent(K key, V value) { throw mutation(); }
    @Override public boolean remove(Object key, Object value) { throw mutation(); }
    @Override public V computeIfAbsent(K key, Function<? super K, ? extends V> mapping) { throw mutation(); }
    @Override public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> mapping) { throw mutation(); }
    @Override public V compute(K key, BiFunction<? super K, ? super V, ? extends V> mapping) { throw mutation(); }
    @Override public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> mapping) { throw mutation(); }
}
